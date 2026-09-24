package com.gms.dao;

import com.gms.entity.Account;
import com.gms.util.DBContext;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Truy vấn bảng Accounts.
 */
public class AccountDAO {

    public Account findById(int accountId) throws SQLException {
        String sql
                = "SELECT AccountId, PersonId, PasswordHash, Status, "
                + "FailedLoginCount, LockedUntil, LastLoginAt, CreatedAt "
                + "FROM Accounts "
                + "WHERE AccountId = ?";

        try (Connection connection = DBContext.getConnection();
                PreparedStatement statement
                = connection.prepareStatement(sql)) {

            statement.setInt(1, accountId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return mapResultSetToAccount(resultSet);
                }
            }
        }

        return null;
    }

    public boolean updatePasswordHash(
            int accountId,
            String passwordHash
    ) throws SQLException {

        String sql
                = "UPDATE Accounts "
                + "SET PasswordHash = ? "
                + "WHERE AccountId = ?";

        try (Connection connection = DBContext.getConnection();
                PreparedStatement statement
                = connection.prepareStatement(sql)) {

            statement.setString(1, passwordHash);
            statement.setInt(2, accountId);

            int affectedRows = statement.executeUpdate();

            return affectedRows == 1;
        }
    }

    private Account mapResultSetToAccount(ResultSet resultSet)
            throws SQLException {

        return new Account(
                resultSet.getInt("AccountId"),
                resultSet.getInt("PersonId"),
                resultSet.getString("PasswordHash"),
                Account.Status.fromDbValue(
                        resultSet.getString("Status")
                ),
                resultSet.getInt("FailedLoginCount"),
                resultSet.getObject(
                        "LockedUntil",
                        java.time.LocalDateTime.class
                ),
                resultSet.getObject(
                        "LastLoginAt",
                        java.time.LocalDateTime.class
                ),
                resultSet.getObject(
                        "CreatedAt",
                        java.time.LocalDateTime.class
                )
        );
    }
}