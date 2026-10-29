package com.gms.dao;

import com.gms.dto.ExerciseItem;
import com.gms.dto.ExerciseLibraryView.Period;
import com.gms.entity.Package;
import com.gms.util.DBContext;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DAO MÀN THƯ VIỆN BÀI TẬP — bảng Exercises (bộ đệm từ wger.de) + đọc quyền mở khóa của khách.
 *
 *   findUnlockPeriods      các gói CÓ đặc quyền của khách đang đăng nhập (kể cả gói đôi người đi kèm)
 *   findUnlockingPackages  gói đang bán có đặc quyền (in ở màn khóa)
 *   findLastSyncedAt       lần tải từ wger gần nhất; null = bảng trống
 *   countAll / countByCategory / countFiltered / findPage   đọc thư viện theo bộ lọc, phân trang ở SQL
 *   deleteAll + insertAll  ghi lại cả bảng khi đồng bộ (Service mở transaction, DAO nhận Connection)
 */
public class ExerciseLibraryDAO {

    /**
     * Gói của khách có Packages.UnlocksExerciseLibrary = 1, bỏ gói Cancelled.
     * Khách là người đứng tên (Subscriptions.CustomerId) hoặc người đi kèm gói đôi (Orders.PartnerCustomerId).
     *
     * @param accountId mã tài khoản từ session (không lấy từ URL)
     */
    public List<Period> findUnlockPeriods(int accountId) throws SQLException {
        String sql = """
                SELECT s.StartDate, s.EndDate, s.Status
                FROM Accounts a
                JOIN Customers c     ON c.PersonId = a.PersonId AND c.DeletedAt IS NULL
                JOIN Orders o        ON o.HolderCustomerId = c.CustomerId OR o.PartnerCustomerId = c.CustomerId
                JOIN Subscriptions s ON s.OrderId = o.OrderId
                JOIN Packages p      ON p.PackageId = o.PackageId
                WHERE a.AccountId = ?
                  AND p.UnlocksExerciseLibrary = 1
                  AND s.Status IN ('Active', 'Frozen', 'Expired')
                ORDER BY s.EndDate DESC
                """;
        List<Period> periods = new ArrayList<>();
        try (Connection connection = DBContext.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, accountId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    // Mỗi dòng: ngày bắt đầu, ngày cuối, trạng thái gói
                    periods.add(new Period(rs.getDate("StartDate").toLocalDate(),
                            rs.getDate("EndDate").toLocalDate(), rs.getString("Status")));
                }
            }
        }
        return periods;
    }

    /** Gói đang bán có đặc quyền, gói tập trước gói PT, rẻ trước. Chỉ đọc tên + giá. */
    public List<Package> findUnlockingPackages() throws SQLException {
        String sql = """
                SELECT PackageId, PackageName, Price
                FROM Packages
                WHERE IsActive = 1 AND UnlocksExerciseLibrary = 1
                ORDER BY CASE PackageType WHEN 'Membership' THEN 0 ELSE 1 END, Price
                """;
        List<Package> packages = new ArrayList<>();
        try (Connection connection = DBContext.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                Package pkg = new Package(rs.getInt("PackageId"));
                pkg.setPackageName(rs.getString("PackageName"));
                pkg.setPrice(rs.getInt("Price"));
                packages.add(pkg);
            }
        }
        return packages;
    }

    /** MAX(SyncedAt) của bảng; bảng trống → null. */
    public LocalDateTime findLastSyncedAt() throws SQLException {
        String sql = "SELECT MAX(SyncedAt) AS LastSync FROM Exercises";
        try (Connection connection = DBContext.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet rs = statement.executeQuery()) {
            Timestamp last = rs.next() ? rs.getTimestamp("LastSync") : null;
            return last == null ? null : last.toLocalDateTime();
        }
    }

    /** Tổng số bài trong thư viện. */
    public int countAll() throws SQLException {
        try (Connection connection = DBContext.getConnection();
                PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM Exercises");
                ResultSet rs = statement.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    /** Số bài mỗi nhóm cơ: {11 → 78, 12 → 105, …} cho các chip. */
    public Map<Integer, Integer> countByCategory() throws SQLException {
        String sql = """
                SELECT CategoryId, COUNT(*) AS Total
                FROM Exercises
                GROUP BY CategoryId
                """;
        Map<Integer, Integer> counts = new LinkedHashMap<>();
        try (Connection connection = DBContext.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                counts.put(rs.getInt("CategoryId"), rs.getInt("Total"));
            }
        }
        return counts;
    }

    /**
     * Đếm bài khớp bộ lọc. Tham số null = không lọc theo điều kiện đó.
     *
     * @param namePattern mẫu LIKE đã escape (SqlUtil.toContainsPattern), ví dụ "%bench%"
     */
    public int countFiltered(String namePattern, Integer categoryId, Integer equipmentId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM Exercises" + buildWhere(namePattern, categoryId, equipmentId);
        try (Connection connection = DBContext.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            bindWhere(statement, namePattern, categoryId, equipmentId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /**
     * Một trang bài theo bộ lọc, phân trang ở SQL (OFFSET … FETCH).
     *
     * @param orderBy mệnh đề ORDER BY lấy từ hằng số của Service (ExerciseLibraryService.orderByOf),
     *                KHÔNG BAO GIỜ là chữ người dùng gõ trên URL
     */
    public List<ExerciseItem> findPage(String namePattern, Integer categoryId, Integer equipmentId,
            String orderBy, int offset, int size) throws SQLException {
        String sql = "SELECT ExerciseId, Name, CategoryId, MuscleIds, EquipmentIds, ImageUrl, Description FROM Exercises"
                + buildWhere(namePattern, categoryId, equipmentId)
                + " ORDER BY " + orderBy
                + " OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";
        List<ExerciseItem> items = new ArrayList<>();
        try (Connection connection = DBContext.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            int next = bindWhere(statement, namePattern, categoryId, equipmentId);
            statement.setInt(next, offset);
            statement.setInt(next + 1, size);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    ExerciseItem item = new ExerciseItem();
                    item.setExerciseId(rs.getInt("ExerciseId"));
                    item.setName(rs.getString("Name"));
                    item.setCategoryId(rs.getInt("CategoryId"));
                    item.setMuscleIds(splitIds(rs.getString("MuscleIds")));
                    item.setEquipmentIds(splitIds(rs.getString("EquipmentIds")));
                    item.setImageUrl(rs.getString("ImageUrl"));
                    item.setDescription(rs.getString("Description"));
                    items.add(item);
                }
            }
        }
        return items;
    }

    /** Xóa cả bảng (bước 1 của đồng bộ, cùng transaction với insertAll). */
    public void deleteAll(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM Exercises");
        }
    }

    /** Ghi lại toàn bộ bài vừa tải (batch, bước 2 của đồng bộ). */
    public void insertAll(Connection connection, List<ExerciseItem> items, LocalDateTime syncedAt) throws SQLException {
        String sql = """
                INSERT INTO Exercises (ExerciseId, Name, CategoryId, MuscleIds, EquipmentIds, ImageUrl, Description, SyncedAt)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (ExerciseItem item : items) {
                statement.setInt(1, item.getExerciseId());
                statement.setString(2, item.getName());
                statement.setInt(3, item.getCategoryId());
                statement.setString(4, joinIds(item.getMuscleIds()));
                statement.setString(5, joinIds(item.getEquipmentIds()));
                if (item.getImageUrl() == null) {
                    statement.setNull(6, Types.NVARCHAR);
                } else {
                    statement.setString(6, item.getImageUrl());
                }
                if (item.getDescription() == null) {
                    statement.setNull(7, Types.NVARCHAR);
                } else {
                    statement.setString(7, item.getDescription());
                }
                statement.setTimestamp(8, Timestamp.valueOf(syncedAt));
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    // ===== Hàm phụ =====

    /** Ghép WHERE theo bộ lọc có giá trị. Chỉ ghép chữ cố định, giá trị luôn đi qua dấu ?. */
    private String buildWhere(String namePattern, Integer categoryId, Integer equipmentId) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        if (namePattern != null) {
            where.append(" AND Name LIKE ? ESCAPE '\\'");
        }
        if (categoryId != null) {
            where.append(" AND CategoryId = ?");
        }
        if (equipmentId != null) {
            // EquipmentIds lưu dạng ",1,8," → tìm ",8," không nhầm 8 với 18
            where.append(" AND EquipmentIds LIKE ?");
        }
        return where.toString();
    }

    /** Gán giá trị cho các dấu ? của buildWhere theo đúng thứ tự; trả vị trí ? kế tiếp. */
    private int bindWhere(PreparedStatement statement, String namePattern, Integer categoryId, Integer equipmentId)
            throws SQLException {
        int index = 1;
        if (namePattern != null) {
            statement.setString(index++, namePattern);
        }
        if (categoryId != null) {
            statement.setInt(index++, categoryId);
        }
        if (equipmentId != null) {
            statement.setString(index++, "%," + equipmentId + ",%");
        }
        return index;
    }

    /** [1, 8] → ",1,8,"; rỗng → ",". */
    static String joinIds(List<Integer> ids) {
        StringBuilder text = new StringBuilder(",");
        for (Integer id : ids) {
            text.append(id).append(',');
        }
        String joined = text.toString();
        return joined.length() > 100 ? "," : joined;
    }

    /** ",1,8," → [1, 8]; null hoặc "," → rỗng. */
    static List<Integer> splitIds(String text) {
        List<Integer> ids = new ArrayList<>();
        if (text == null) {
            return ids;
        }
        for (String part : text.split(",")) {
            if (!part.isBlank()) {
                ids.add(Integer.parseInt(part.trim()));
            }
        }
        return ids;
    }
}
