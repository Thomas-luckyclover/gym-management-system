package com.gms.service;

import com.gms.util.DBContext;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Chặn nhân viên thao tác trên hồ sơ khách của chính mình (CBR-115). */
public class SelfServiceGuard {

    /**
     * Tài khoản nhân viên và hồ sơ khách có cùng Person không.
     * @param staffAccountId tài khoản đang thao tác
     * @param customerId hồ sơ khách bị tác động
     * @return true nếu là chính mình
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
     * PT và khách có cùng Person không (CBR-59, CBR-115).
     * @param employeeId PT
     * @param customerId khách
     * @return true nếu là cùng một người
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
