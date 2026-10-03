package com.gms.service;

import com.gms.util.DBContext;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * CBR-115 — nhân viên KHÔNG được tự xử lý hồ sơ khách của chính mình.
 *
 * <p>Ví dụ: Kiều Trang (Person 22) là PT và cũng là khách. Khi đang ở vai trò PT,
 * cô không được điểm danh, ghi chú, lập giáo án cho chính hồ sơ khách của mình; khi là Lễ tân
 * (giả sử) thì không được bán gói, áp voucher, bảo lưu, sửa/xóa hồ sơ của chính mình.
 * Người khác có quyền làm thay.</p>
 *
 * <p>Cách dùng ở các UC sau (UC-16, 17, 18, 20, 21, 23, 31, 33, 34, 35, 37) — gọi ở Service, TRƯỚC khi ghi DB:</p>
 * <pre>
 *   if (selfServiceGuard.isOwnCustomerProfile(staffAccountId, customerId)) {
 *       return ServiceResult.fail(Messages.MSG07_NO_PERMISSION);
 *   }
 * </pre>
 * Với buổi PT: so customerId của buổi với hồ sơ khách của PT phụ trách bằng
 * {@link #isSamePerson(int, int)} (employeeId, customerId).
 */
public class SelfServiceGuard {

    /**
     * Tài khoản nhân viên đang thao tác và hồ sơ khách có cùng 1 Person không.
     *
     * @param staffAccountId AccountId trong session (đang ở vai trò nhân viên)
     * @param customerId     hồ sơ khách bị tác động
     * @return true nếu là chính mình → phải chặn
     */
    public boolean isOwnCustomerProfile(int staffAccountId, int customerId) throws SQLException {
        String sql = """
                SELECT 1
                FROM Accounts a
                JOIN Customers c ON c.PersonId = a.PersonId
                WHERE a.AccountId = ? AND c.CustomerId = ?
                """;
        return exists(sql, staffAccountId, customerId);
    }

    /**
     * PT (employeeId) và khách (customerId) có phải cùng 1 Person không — dùng cho đặt buổi,
     * điểm danh, ghi chú, giáo án (CBR-115, CBR-59).
     */
    public boolean isSamePerson(int employeeId, int customerId) throws SQLException {
        String sql = """
                SELECT 1
                FROM Employees e
                JOIN Customers c ON c.PersonId = e.PersonId
                WHERE e.EmployeeId = ? AND c.CustomerId = ?
                """;
        return exists(sql, employeeId, customerId);
    }

    private boolean exists(String sql, int first, int second) throws SQLException {
        try (Connection conn = DBContext.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, first);
            stmt.setInt(2, second);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }
}
