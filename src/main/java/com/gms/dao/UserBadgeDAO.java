package com.gms.dao;

import com.gms.util.DBContext;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * DAO THẺ TÀI KHOẢN góc phải mọi màn — đọc mã gốc của tài khoản theo vai trò đang dùng.
 *   Customer → Customers.CustomerCode (KH-1006) · vai trò nhân viên → Employees.EmployeeCode (NV-0005) đúng Position.
 * Chỉ đọc; UserBadgeService đổi mã gốc sang mã hiển thị.
 */
public class UserBadgeDAO {

    /** Mã gốc của tài khoản ở vai trò role ("Customer", "PT", "Receptionist", "Manager", "Admin"); không có → null. */
    public String findCode(int accountId, String role) throws SQLException {
        boolean customer = "Customer".equals(role);
        String sql = customer
                ? """
                  SELECT c.CustomerCode AS Code
                  FROM Accounts a
                  JOIN Customers c ON c.PersonId = a.PersonId AND c.DeletedAt IS NULL
                  WHERE a.AccountId = ?
                  """
                : """
                  SELECT e.EmployeeCode AS Code
                  FROM Accounts a
                  JOIN Employees e ON e.PersonId = a.PersonId AND e.Status = 'Working' AND e.Position = ?
                  WHERE a.AccountId = ?
                  """;
        try (Connection connection = DBContext.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            if (customer) {
                statement.setInt(1, accountId);
            } else {
                statement.setString(1, role);
                statement.setInt(2, accountId);
            }
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getString("Code") : null;
            }
        }
    }
}
