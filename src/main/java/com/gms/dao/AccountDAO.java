package com.gms.dao;

import com.gms.entity.Account;
import com.gms.util.DBContext;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;

/**
 * Truy vấn bảng Accounts (đăng nhập, quên mật khẩu, đổi mật khẩu).
 */
public class AccountDAO {

    // ===== Tìm tài khoản =====

    /**
     * Tìm tài khoản theo email của Person còn hoạt động.
     * @param email email đăng nhập
     * @return Account, hoặc null nếu không có
     */
    public Account findByEmail(String email) throws SQLException {
        try (Connection conn = DBContext.getConnection()) {
            return findByEmail(conn, email);
        }
    }

    /**
     * Tìm tài khoản theo email, dùng Connection có sẵn.
     */
    public Account findByEmail(Connection conn, String email) throws SQLException {
        String sql = """
                SELECT a.AccountId, a.PersonId, a.PasswordHash, a.Status, a.FailedLoginCount, a.LockedUntil, a.LastLoginAt, a.CreatedAt
                FROM Accounts a
                JOIN Persons p ON a.PersonId = p.PersonId
                WHERE p.Email = ? AND p.IsReleased = 0
                """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, email);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToAccount(rs);
                }
            }
        }
        return null;
    }

    /**
     * Tìm tài khoản theo SĐT của Person còn hoạt động.
     * @param phone số điện thoại đăng nhập
     * @return Account, hoặc null nếu không có
     */
    public Account findByPhone(String phone) throws SQLException {
        try (Connection conn = DBContext.getConnection()) {
            return findByPhone(conn, phone);
        }
    }

    /**
     * Tìm tài khoản theo SĐT, dùng Connection có sẵn.
     */
    public Account findByPhone(Connection conn, String phone) throws SQLException {
        String sql = """
                SELECT a.AccountId, a.PersonId, a.PasswordHash, a.Status, a.FailedLoginCount, a.LockedUntil, a.LastLoginAt, a.CreatedAt
                FROM Accounts a
                JOIN Persons p ON a.PersonId = p.PersonId
                WHERE p.Phone = ? AND p.IsReleased = 0
                """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, phone);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToAccount(rs);
                }
            }
        }
        return null;
    }

    /**
     * Tìm tài khoản theo khóa chính.
     * @param accountId id tài khoản
     * @return Account, hoặc null nếu không có
     */
    public Account findById(int accountId) throws SQLException {
        try (Connection conn = DBContext.getConnection()) {
            return findById(conn, accountId);
        }
    }

    /**
     * Tìm tài khoản theo khóa chính, dùng Connection có sẵn.
     */
    public Account findById(Connection conn, int accountId) throws SQLException {
        String sql = """
                SELECT AccountId, PersonId, PasswordHash, Status, FailedLoginCount, LockedUntil, LastLoginAt, CreatedAt
                FROM Accounts
                WHERE AccountId = ?
                """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, accountId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToAccount(rs);
                }
            }
        }
        return null;
    }

    // ===== Mật khẩu và đăng nhập =====

    /**
     * Ghi mật khẩu mới (đã băm BCrypt).
     * @param accountId id tài khoản
     * @param newPasswordHash chuỗi BCrypt
     * @return true nếu có dòng được cập nhật
     */
    public boolean updatePasswordHash(int accountId, String newPasswordHash) throws SQLException {
        try (Connection conn = DBContext.getConnection()) {
            return updatePasswordHash(conn, accountId, newPasswordHash);
        }
    }

    /**
     * Ghi mật khẩu mới, dùng Connection có sẵn.
     */
    public boolean updatePasswordHash(Connection conn, int accountId, String newPasswordHash) throws SQLException {
        String sql = """
                UPDATE Accounts
                SET PasswordHash = ?
                WHERE AccountId = ?
                """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, newPasswordHash);
            stmt.setInt(2, accountId);
            return stmt.executeUpdate() > 0;
        }
    }

    /**
     * Ghi mật khẩu mới kèm kiểm tra hash cũ để chống ghi đè đồng thời (Optimistic Concurrency Control - TC-17C).
     * @param accountId id tài khoản
     * @param newPasswordHash chuỗi hash mới
     * @param oldPasswordHash chuỗi hash cũ đã kiểm tra
     * @return true nếu có dòng được cập nhật
     */
    public boolean updatePasswordHash(int accountId, String newPasswordHash, String oldPasswordHash) throws SQLException {
        try (Connection conn = DBContext.getConnection()) {
            return updatePasswordHash(conn, accountId, newPasswordHash, oldPasswordHash);
        }
    }

    /**
     * Ghi mật khẩu mới kèm kiểm tra hash cũ, dùng Connection có sẵn trong transaction (TC-17C).
     */
    public boolean updatePasswordHash(Connection conn, int accountId, String newPasswordHash, String oldPasswordHash) throws SQLException {
        String sql = """
                UPDATE Accounts
                SET PasswordHash = ?
                WHERE AccountId = ? AND PasswordHash = ?
                """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, newPasswordHash);
            stmt.setInt(2, accountId);
            stmt.setString(3, oldPasswordHash);
            return stmt.executeUpdate() > 0;
        }
    }

    /**
     * Tăng số lần đăng nhập sai thêm 1 (BR-01).
     * @param accountId id tài khoản
     * @return true nếu có dòng được cập nhật
     */
    public boolean increaseFailedLogin(int accountId) throws SQLException {
        try (Connection conn = DBContext.getConnection()) {
            return increaseFailedLogin(conn, accountId);
        }
    }

    /**
     * Tăng số lần đăng nhập sai, dùng Connection có sẵn.
     */
    public boolean increaseFailedLogin(Connection conn, int accountId) throws SQLException {
        String sql = """
                UPDATE Accounts
                SET FailedLoginCount = FailedLoginCount + 1
                WHERE AccountId = ?
                """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, accountId);
            return stmt.executeUpdate() > 0;
        }
    }

    /**
     * Đưa số lần sai về 0 và bỏ khóa tạm (BR-02).
     * @param accountId id tài khoản
     * @return true nếu có dòng được cập nhật
     */
    public boolean resetFailedLogin(int accountId) throws SQLException {
        try (Connection conn = DBContext.getConnection()) {
            return resetFailedLogin(conn, accountId);
        }
    }

    /**
     * Đưa số lần sai về 0, dùng Connection có sẵn.
     */
    public boolean resetFailedLogin(Connection conn, int accountId) throws SQLException {
        String sql = """
                UPDATE Accounts
                SET FailedLoginCount = 0,
                    LockedUntil = NULL
                WHERE AccountId = ?
                """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, accountId);
            return stmt.executeUpdate() > 0;
        }
    }

    /**
     * Khóa tạm tài khoản tới một thời điểm, không đổi Status (BR-01).
     * @param accountId id tài khoản
     * @param lockedUntil thời điểm hết khóa
     * @return true nếu có dòng được cập nhật
     */
    public boolean lockUntil(int accountId, LocalDateTime lockedUntil) throws SQLException {
        try (Connection conn = DBContext.getConnection()) {
            return lockUntil(conn, accountId, lockedUntil);
        }
    }

    /**
     * Khóa tạm tài khoản, dùng Connection có sẵn.
     */
    public boolean lockUntil(Connection conn, int accountId, LocalDateTime lockedUntil) throws SQLException {
        String sql = """
                UPDATE Accounts
                SET LockedUntil = ?
                WHERE AccountId = ?
                """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setTimestamp(1, lockedUntil != null ? Timestamp.valueOf(lockedUntil) : null);
            stmt.setInt(2, accountId);
            return stmt.executeUpdate() > 0;
        }
    }

    /**
     * Ghi thời điểm đăng nhập thành công gần nhất.
     * @param accountId id tài khoản
     * @param lastLoginAt thời điểm đăng nhập
     * @return true nếu có dòng được cập nhật
     */
    public boolean updateLastLogin(int accountId, LocalDateTime lastLoginAt) throws SQLException {
        try (Connection conn = DBContext.getConnection()) {
            return updateLastLogin(conn, accountId, lastLoginAt);
        }
    }

    /**
     * Ghi thời điểm đăng nhập, dùng Connection có sẵn.
     */
    public boolean updateLastLogin(Connection conn, int accountId, LocalDateTime lastLoginAt) throws SQLException {
        String sql = """
                UPDATE Accounts
                SET LastLoginAt = ?
                WHERE AccountId = ?
                """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setTimestamp(1, lastLoginAt != null ? Timestamp.valueOf(lastLoginAt) : null);
            stmt.setInt(2, accountId);
            return stmt.executeUpdate() > 0;
        }
    }

    /**
     * Tìm tài khoản theo PersonId trong transaction.
     */
    public Account findByPersonId(Connection conn, int personId) throws SQLException {
        String sql = """
                SELECT a.AccountId, a.PersonId, a.PasswordHash, a.Status, a.FailedLoginCount, a.LockedUntil, a.LastLoginAt, a.CreatedAt
                FROM Accounts a
                WHERE a.PersonId = ?
                """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, personId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToAccount(rs);
                }
            }
        }
        return null;
    }

    /**
     * Chuyển trạng thái tài khoản thành Inactive chỉ khi Person không còn bất kỳ vai trò nào hoạt động
     * (không còn khách hàng chưa Inactive, không còn nhân viên Working) - CBR-86, DBR-28.
     *
     * @param conn     Connection tham gia transaction
     * @param personId mã Person
     * @return true nếu tài khoản được chuyển sang Inactive
     * @throws SQLException khi truy vấn gặp lỗi
     */
    public boolean deactivateAccountIfNoActiveRoles(Connection conn, int personId) throws SQLException {
        String sql = """
                UPDATE Accounts SET Status = 'Inactive'
                WHERE PersonId = ? AND Status <> 'Inactive'
                  AND NOT EXISTS (SELECT 1 FROM Customers WHERE PersonId = Accounts.PersonId AND Status <> 'Inactive')
                  AND NOT EXISTS (SELECT 1 FROM Employees WHERE PersonId = Accounts.PersonId AND Status = 'Working')
                """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, personId);
            return stmt.executeUpdate() > 0;
        }
    }

    // ===== Map ResultSet =====

    /**
     * Chuyển 1 dòng ResultSet thành Account.
     */
    private Account mapResultSetToAccount(ResultSet rs) throws SQLException {
        int accountId = rs.getInt("AccountId");
        int personId = rs.getInt("PersonId");
        String passwordHash = rs.getString("PasswordHash");
        Account.Status status = Account.Status.fromDbValue(rs.getString("Status"));
        int failedLoginCount = rs.getInt("FailedLoginCount");

        Timestamp lockedUntilTs = rs.getTimestamp("LockedUntil");
        LocalDateTime lockedUntil = lockedUntilTs != null ? lockedUntilTs.toLocalDateTime() : null;

        Timestamp lastLoginAtTs = rs.getTimestamp("LastLoginAt");
        LocalDateTime lastLoginAt = lastLoginAtTs != null ? lastLoginAtTs.toLocalDateTime() : null;

        Timestamp createdAtTs = rs.getTimestamp("CreatedAt");
        LocalDateTime createdAt = createdAtTs != null ? createdAtTs.toLocalDateTime() : null;

        return new Account(accountId, personId, passwordHash, status, failedLoginCount, lockedUntil, lastLoginAt,
                createdAt);
    }
}
