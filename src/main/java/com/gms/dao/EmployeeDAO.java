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

/** Truy vấn nhân viên (hồ sơ nhân viên, danh sách PT ở trang chủ). */
public class EmployeeDAO {

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
                //1 nhân viên có 2 trạng thái Working và Resigned → ưu tiên lấy dòng Working, nếu không có thì lấy dòng Resigned (mới nhất)
                //desc để lấy hồ sơ gần đây mới nhất

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
     *   5 | Đinh Công Sơn         | Strength & Conditioning  | 8 năm
     *   6 | Nguyễn Thị Kiều Trang | Giảm mỡ · Thể hình nữ    | 6 năm
     *   7 | David Miller          | Tăng cơ · Powerlifting   | 9 năm
     *   (EmployeeId 8 Hồ Văn Nam đã Resigned → không có trong danh sách)
     *
     * Chỉ lấy họ tên, chuyên môn, kinh nghiệm — KHÔNG lấy SĐT/email vì trang chủ là trang công khai.
     *
     * @return danh sách PT đang làm; list rỗng (không phải null) nếu không có PT nào
     * @throws SQLException lỗi kết nối hoặc lỗi câu SQL
     */
    public List<PTCardView> findWorkingPts() throws SQLException {
        // B1: câu SQL nối 2 bảng
        //   - Employees  : lọc PT đang làm, lấy chuyên môn + kinh nghiệm
        //   - Persons    : lấy họ tên (Employees không có cột họ tên)
        String sql = "SELECT e.EmployeeId, p.FullName, e.Specialty, e.YearsOfExperience "
                + "FROM Employees e "
                + "JOIN Persons p ON p.PersonId = e.PersonId "
                + "WHERE e.Position = ? AND e.Status = ? "
                + "ORDER BY e.EmployeeId";

        List<PTCardView> pts = new ArrayList<>();

        // B2: mở kết nối (tự đóng khi ra khỏi try)
        try (Connection connection = DBContext.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {

            // B3: điền 2 dấu ? — lấy giá trị từ enum, không gõ tay chuỗi "PT", "Working"
            statement.setString(1, Role.PT.getDbValue());
            statement.setString(2, Employee.Status.WORKING.getDbValue());

            // B4: mỗi dòng kết quả → 1 thẻ PT
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    PTCardView pt = new PTCardView(
                            resultSet.getInt("EmployeeId"),
                            resultSet.getString("FullName"),
                            resultSet.getString("Specialty"),
                            getNullableInteger(resultSet, "YearsOfExperience")
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
