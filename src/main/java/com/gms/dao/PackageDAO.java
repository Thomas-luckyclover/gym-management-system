package com.gms.dao;

import com.gms.entity.Package;
import com.gms.util.DBContext;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO cho bảng Packages (gói tập, gói PT, buổi PT lẻ).
 *
 * DAO chỉ đọc/ghi DB, KHÔNG tính toán gì (giá mỗi tháng, % tiết kiệm, nhãn "Phổ biến nhất"...).
 * Những việc đó nằm ở Service.
 *
 * @author Le Cong Minh
 */
public class PackageDAO {

    /**
     * Lấy mọi gói ĐANG BÁN để hiện 2 bảng giá trên trang chủ (luật 2.6).
     *
     * Dùng khi: HomeServlet mở trang chủ. Service tự chia list này thành 2 bảng:
     *   - Bảng giá gói tập: Membership, DayPass
     *   - Bảng giá PT     : PTSingle, PTCouple, SingleSession
     *
     * Thứ tự: thời hạn ngắn → dài (luật 2.6), cùng thời hạn thì gói rẻ hơn đứng trước.
     * Ví dụ với seed (Gói 12 Tháng đã ngừng bán → không có):
     *   Gói tập: Gói Ngày → Gói 1 Tháng → Gói 3 Tháng → Gói 6 Tháng
     *   Gói PT : Buổi PT Lẻ (không thời hạn) → PT 1 Tháng → PT Couple
     *
     * @return danh sách gói đang bán; list rỗng (không phải null) nếu Manager tắt hết
     * @throws SQLException lỗi kết nối hoặc lỗi câu SQL
     */
    public List<Package> findActiveForHomepage() throws SQLException {
        // B1: lấy gói đang bán, sắp theo thời hạn rồi theo giá
        String sql = "SELECT PackageId, PackageName, Description, Benefits, PackageType, "
                + "DurationUnit, DurationValue, EstimatedSessionCount, Price, IsActive, UnlocksExerciseLibrary, CreatedAt "
                + "FROM Packages "
                + "WHERE IsActive = 1 "
                + "ORDER BY CASE DurationUnit WHEN 'Day' THEN 0 WHEN 'Month' THEN 1 ELSE -1 END, DurationValue, Price";

        List<Package> packages = new ArrayList<>();

        // B2: mở kết nối (tự đóng khi ra khỏi try)
        try (Connection conn = DBContext.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql);
                ResultSet rs = stmt.executeQuery()) {

            // B3: mỗi dòng → 1 object Package
            while (rs.next()) {
                packages.add(mapResultSetToPackage(rs));
            }
        }

        return packages;
    }

    /**
     * Tìm gói theo ID kèm RowVer để phục vụ kiểm soát đồng thời (optimistic concurrency - MSG22).
     *
     * @param packageId ID gói cần tìm
     * @return đối tượng Package đầy đủ hoặc null nếu không tìm thấy
     * @throws SQLException lỗi truy vấn cơ sở dữ liệu
     */
    public Package findById(int packageId) throws SQLException {
        try (Connection conn = DBContext.getConnection()) {
            return findById(conn, packageId);
        }
    }

    /**
     * Tìm gói theo ID trong một transaction hiện hành.
     *
     * @param conn kết nối cơ sở dữ liệu
     * @param packageId ID gói cần tìm
     * @return đối tượng Package đầy đủ kèm RowVer hoặc null nếu không tồn tại
     * @throws SQLException lỗi truy vấn cơ sở dữ liệu
     */
    public Package findById(Connection conn, int packageId) throws SQLException {
        String sql = "SELECT PackageId, PackageName, Description, Benefits, PackageType, "
                + "DurationUnit, DurationValue, EstimatedSessionCount, Price, IsActive, "
                + "UnlocksExerciseLibrary, CreatedAt, RowVer "
                + "FROM Packages "
                + "WHERE PackageId = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, packageId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Package pkg = mapResultSetToPackage(rs);
                    pkg.setRowVersion(rs.getBytes("RowVer"));
                    return pkg;
                }
            }
        }
        return null;
    }

    /**
     * Kiểm tra xem tên gói có bị trùng với một gói KHÁC đang bán (IsActive = 1) hay không.
     * Áp dụng chỉ mục unique có lọc UX_Packages_Name_Active (DBR-06).
     *
     * @param packageName tên gói cần kiểm tra
     * @param excludePackageId ID gói bỏ qua (khi chỉnh sửa chính nó), truyền null khi thêm mới
     * @return true nếu tên gói đã tồn tại ở trạng thái đang bán
     * @throws SQLException lỗi truy vấn
     */
    public boolean existsActiveByName(String packageName, Integer excludePackageId) throws SQLException {
        try (Connection conn = DBContext.getConnection()) {
            return existsActiveByName(conn, packageName, excludePackageId);
        }
    }

    /**
     * Kiểm tra tên gói trùng lặp trong một transaction hiện hành.
     */
    public boolean existsActiveByName(Connection conn, String packageName, Integer excludePackageId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM Packages "
                + "WHERE PackageName = ? AND IsActive = 1 AND (? IS NULL OR PackageId <> ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, packageName != null ? packageName.trim() : "");
            if (excludePackageId != null) {
                stmt.setInt(2, excludePackageId);
                stmt.setInt(3, excludePackageId);
            } else {
                stmt.setNull(2, Types.INTEGER);
                stmt.setNull(3, Types.INTEGER);
            }
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        }
        return false;
    }

    // ===== Hàm phụ =====

    /**
     * Chép 1 dòng kết quả SELECT thành object Package (dòng phải có đủ 12 cột như câu SELECT ở trên).
     */
    private Package mapResultSetToPackage(ResultSet rs) throws SQLException {
        // B1: Id là final → chỉ gán được qua constructor
        Package pkg = new Package(rs.getInt("PackageId"));

        // B2: các cột còn lại → setter, mỗi dòng 1 cột (đọc là biết cột nào vào trường nào)
        pkg.setPackageName(rs.getString("PackageName"));
        pkg.setDescription(rs.getString("Description"));
        pkg.setBenefits(rs.getString("Benefits"));
        pkg.setPackageType(Package.Type.fromDbValue(rs.getString("PackageType")));
        pkg.setDurationUnit(rs.getString("DurationUnit"));
        pkg.setDurationValue(getNullableInteger(rs, "DurationValue"));
        pkg.setEstimatedSessionCount(getNullableInteger(rs, "EstimatedSessionCount"));
        pkg.setPrice(rs.getInt("Price"));
        pkg.setActive(rs.getBoolean("IsActive"));
        pkg.setUnlocksExerciseLibrary(rs.getBoolean("UnlocksExerciseLibrary"));

        // B3: JDBC trả Timestamp, entity dùng LocalDateTime → đổi kiểu (NULL thì giữ null)
        Timestamp createdAt = rs.getTimestamp("CreatedAt");
        pkg.setCreatedAt(createdAt == null ? null : createdAt.toLocalDateTime());

        return pkg;
    }

    /**
     * Đọc cột số có thể NULL.
     * rs.getInt() gặp NULL sẽ trả 0 (không phải null) → phải hỏi thêm wasNull() mới biết là NULL thật hay số 0.
     * VD: Buổi PT Lẻ có DurationDays NULL → trả null, không phải 0 ngày.
     */
    private Integer getNullableInteger(ResultSet rs, String columnName) throws SQLException {
        int value = rs.getInt(columnName);
        if (rs.wasNull()) {
            return null;
        }
        return value;
    }
}
