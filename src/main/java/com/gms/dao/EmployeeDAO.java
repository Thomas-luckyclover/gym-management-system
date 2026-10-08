package com.gms.dao;

import com.gms.constant.Role;
import com.gms.dto.PTCardView;
import com.gms.entity.Employee;
import com.gms.util.DBContext;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Truy vấn nhân viên và thống kê buổi PT. */
public class EmployeeDAO {

    /** Giá trị cột Sessions.Status của buổi đã dạy xong (chưa có entity Session nên tạm để hằng ở đây). */
    private static final String SESSION_STATUS_COMPLETED = "CheckedIn";

    /**
     * Tìm Employee theo PersonId, ưu tiên dòng đang Working.
     */
    public Employee findByPersonId(int personId)
            throws SQLException {

        String sql
                = "SELECT EmployeeId, PersonId, EmployeeCode, Position, "
                + "HireDate, CommissionRate, Specialty, "
                + "YearsOfExperience, Status, DeletedAt, "
                + "ResignationReason "
                + "FROM Employees "
                + "WHERE PersonId = ? "
                // ưu tiên dòng đang Working, mới nhất (DBR-65)
                + "ORDER BY CASE WHEN Status = 'Working' THEN 0 ELSE 1 END, EmployeeId DESC";

        try (Connection connection = DBContext.getConnection();
                PreparedStatement statement
                = connection.prepareStatement(sql)) {

            statement.setInt(1, personId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return mapResultSetToEmployee(resultSet);
                }
            }
        }

        return null;
    }

    /**
     * Lấy danh sách PT ĐANG LÀM để hiện ở trang chủ (luật 2.6 — khối "Danh sách PT" và "Số PT").
     *
     * Dùng khi: HomeServlet mở trang chủ. Số PT hiển thị = size() của list này (đếm thật, không hard-code).
     * Ví dụ với seed: trả về 3 dòng theo thứ tự EmployeeId
     *   5 | Đinh Công Sơn         | Strength & Conditioning  | 8 năm | x buổi
     *   6 | Nguyễn Thị Kiều Trang | Giảm mỡ · Thể hình nữ    | 6 năm | x buổi
     *   7 | David Miller          | Tăng cơ · Powerlifting   | 9 năm | x buổi
     *   (EmployeeId 8 Hồ Văn Nam đã Resigned → không có trong danh sách)
     *
     * Chỉ lấy họ tên, chuyên môn, kinh nghiệm, số buổi — KHÔNG lấy SĐT/email vì trang chủ là trang công khai.
     *
     * @return danh sách PT đang làm; list rỗng (không phải null) nếu không có PT nào
     * @throws SQLException lỗi kết nối hoặc lỗi câu SQL
     */
    public List<PTCardView> findWorkingPts() throws SQLException {
        // B1: câu SQL nối 3 bảng
        //   - Employees  : lọc PT đang làm, lấy chuyên môn + kinh nghiệm
        //   - Persons    : lấy họ tên (Employees không có cột họ tên)
        //   - Sessions   : đếm số buổi CheckedIn của từng PT
        // Dùng LEFT JOIN cho Sessions: PT mới chưa dạy buổi nào vẫn phải hiện (đếm ra 0).
        //   Nếu dùng JOIN thường, PT chưa có buổi nào sẽ BIẾN MẤT khỏi kết quả.
        // Điều kiện s.Status = 'CheckedIn' đặt ở ON, KHÔNG đặt ở WHERE:
        //   đặt ở WHERE thì dòng PT không có buổi (s.Status = NULL) bị loại → LEFT JOIN thành JOIN thường.
        String sql = "SELECT e.EmployeeId, p.FullName, e.Specialty, e.YearsOfExperience, "
                + "COUNT(s.SessionId) AS CompletedSessionCount "
                + "FROM Employees e "
                + "JOIN Persons p ON p.PersonId = e.PersonId "
                + "LEFT JOIN Sessions s ON s.PtEmployeeId = e.EmployeeId AND s.Status = ? "
                + "WHERE e.Position = ? AND e.Status = ? "
                + "GROUP BY e.EmployeeId, p.FullName, e.Specialty, e.YearsOfExperience "
                + "ORDER BY e.EmployeeId";

        List<PTCardView> pts = new ArrayList<>();

        // B2: mở kết nối (tự đóng khi ra khỏi try)
        try (Connection connection = DBContext.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {

            // B3: điền 3 dấu ? — lấy giá trị từ enum, không gõ tay chuỗi "PT", "Working"
            statement.setString(1, SESSION_STATUS_COMPLETED);
            statement.setString(2, Role.PT.getDbValue());
            statement.setString(3, Employee.Status.WORKING.getDbValue());

            // B4: mỗi dòng kết quả → 1 thẻ PT
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    PTCardView pt = new PTCardView(
                            resultSet.getInt("EmployeeId"),
                            resultSet.getString("FullName"),
                            resultSet.getString("Specialty"),
                            getNullableInteger(resultSet, "YearsOfExperience"),
                            resultSet.getInt("CompletedSessionCount")
                    );
                    pts.add(pt);
                }
            }
        }

        return pts;
    }

    private Employee mapResultSetToEmployee(ResultSet resultSet)
            throws SQLException {

        Integer commissionRate
                = getNullableInteger(resultSet, "CommissionRate");

        Integer yearsOfExperience
                = getNullableInteger(
                        resultSet,
                        "YearsOfExperience"
                );

        return new Employee(
                resultSet.getInt("EmployeeId"),
                resultSet.getInt("PersonId"),
                resultSet.getString("EmployeeCode"),
                Role.fromDbValue(
                        resultSet.getString("Position")
                ),
                resultSet.getObject(
                        "HireDate",
                        java.time.LocalDate.class
                ),
                commissionRate,
                resultSet.getString("Specialty"),
                yearsOfExperience,
                Employee.Status.fromDbValue(
                        resultSet.getString("Status")
                ),
                resultSet.getObject(
                        "DeletedAt",
                        java.time.LocalDateTime.class
                ),
                resultSet.getString("ResignationReason")
        );
    }

    private Integer getNullableInteger(
            ResultSet resultSet,
            String columnName
    ) throws SQLException {

        int value = resultSet.getInt(columnName);

        if (resultSet.wasNull()) {
            return null;
        }

        return value;
    }
}
