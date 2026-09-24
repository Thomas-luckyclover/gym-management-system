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
 * Data Access Object (DAO) cho bảng Accounts.
 * Thao tác trực tiếp với cơ sở dữ liệu SQL Server, không chứa logic nghiệp vụ.
 *
 * <p>Quy tắc:</p>
 * <ul>
 *   <li>Sử dụng PreparedStatement, không nối chuỗi SQL.</li>
 *   <li>Khai báo throws SQLException để tầng Service xử lý.</li>
 *   <li>Hỗ trợ phương thức nhận Connection từ ngoài để phục vụ Transaction.</li>
 * </ul>
 */
public class AccountDAO {

    // ===== 1. TÌM KIẾM =====

    /**
     * Tìm tài khoản theo Email của người dùng (JOIN với bảng Persons).
     * @param email Email đăng ký của tài khoản
     * @return Account tương ứng hoặc null nếu không tìm thấy
     * @throws SQLException khi có lỗi kết nối hoặc truy vấn SQL
     */
    public Account findByEmail(String email) throws SQLException {
        try (Connection conn = DBContext.getConnection()) {
            return findByEmail(conn, email);
        }
    }

    /**
     * Tìm tài khoản theo Email dùng Connection có sẵn (dùng trong Transaction).
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
     * Tìm tài khoản theo Số điện thoại của người dùng (JOIN với bảng Persons).
     * @param phone Số điện thoại đăng ký
     * @return Account tương ứng hoặc null nếu không tìm thấy
     * @throws SQLException khi có lỗi kết nối hoặc truy vấn SQL
     */
    public Account findByPhone(String phone) throws SQLException {
        try (Connection conn = DBContext.getConnection()) {
            return findByPhone(conn, phone);
        }
    }

    /**
     * Tìm tài khoản theo Số điện thoại dùng Connection có sẵn (dùng trong Transaction).
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
     * Tìm tài khoản theo AccountId (khóa chính).
     * @param accountId ID của tài khoản
     * @return Account tương ứng hoặc null nếu không tìm thấy
     * @throws SQLException khi có lỗi kết nối hoặc truy vấn SQL
     */
    public Account findById(int accountId) throws SQLException {
        try (Connection conn = DBContext.getConnection()) {
            return findById(conn, accountId);
        }
    }

    /**
     * Tìm tài khoản theo AccountId dùng Connection có sẵn (dùng trong Transaction).
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

    // ===== 2. CẬP NHẬT MẬT KHẨU & ĐĂNG NHẬP =====

    /**
     * Cập nhật mật khẩu mới (đã hash BCrypt) cho tài khoản.
     * @param accountId ID của tài khoản cần đổi mật khẩu
     * @param newPasswordHash Chuỗi hash mật khẩu mới
     * @return true nếu cập nhật thành công, false nếu không tìm thấy accountId
     * @throws SQLException khi có lỗi SQL
     */
    public boolean updatePasswordHash(int accountId, String newPasswordHash) throws SQLException {
        try (Connection conn = DBContext.getConnection()) {
            return updatePasswordHash(conn, accountId, newPasswordHash);
        }
    }

    /**
     * Cập nhật mật khẩu mới dùng Connection có sẵn.
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
     * Tăng số lần đăng nhập sai liên tiếp lên 1 (phục vụ BR-01).
     * @param accountId ID tài khoản nhập sai mật khẩu
     * @return true nếu cập nhật thành công
     * @throws SQLException khi có lỗi SQL
     */
    public boolean increaseFailedLogin(int accountId) throws SQLException {
        try (Connection conn = DBContext.getConnection()) {
            return increaseFailedLogin(conn, accountId);
        }
    }

    /**
     * Tăng số lần đăng nhập sai liên tiếp dùng Connection có sẵn.
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
     * Đặt lại số lần đăng nhập sai về 0 và xóa thời điểm khóa tạm (khi đăng nhập thành công hoặc hết hạn khóa theo BR-02).
     * @param accountId ID tài khoản cần reset
     * @return true nếu cập nhật thành công
     * @throws SQLException khi có lỗi SQL
     */
    public boolean resetFailedLogin(int accountId) throws SQLException {
        try (Connection conn = DBContext.getConnection()) {
            return resetFailedLogin(conn, accountId);
        }
    }

    /**
     * Đặt lại số lần đăng nhập sai dùng Connection có sẵn.
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
     * Đặt thời gian khóa tạm thời cho tài khoản do nhập sai quá số lần quy định (BR-01, P-17, P-18).
     * Lưu ý: Không thay đổi cột Status (LockedUntil chỉ dùng cho khóa tạm).
     * @param accountId ID tài khoản bị khóa
     * @param lockedUntil Thời điểm kết thúc khóa tạm
     * @return true nếu cập nhật thành công
     * @throws SQLException khi có lỗi SQL
     */
    public boolean lockUntil(int accountId, LocalDateTime lockedUntil) throws SQLException {
        try (Connection conn = DBContext.getConnection()) {
            return lockUntil(conn, accountId, lockedUntil);
        }
    }

    /**
     * Đặt thời gian khóa tạm thời dùng Connection có sẵn.
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
     * Cập nhật thời điểm đăng nhập gần nhất (LastLoginAt) khi người dùng đăng nhập thành công.
     * @param accountId ID tài khoản đăng nhập
     * @param lastLoginAt Thời điểm đăng nhập
     * @return true nếu cập nhật thành công
     * @throws SQLException khi có lỗi SQL
     */
    public boolean updateLastLogin(int accountId, LocalDateTime lastLoginAt) throws SQLException {
        try (Connection conn = DBContext.getConnection()) {
            return updateLastLogin(conn, accountId, lastLoginAt);
        }
    }

    /**
     * Cập nhật thời điểm đăng nhập gần nhất dùng Connection có sẵn.
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

    // ===== 3. MAP RESULTSET =====

    /**
     * Map dữ liệu từ 1 dòng của ResultSet sang đối tượng Account.
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

        return new Account(accountId, personId, passwordHash, status, failedLoginCount, lockedUntil, lastLoginAt, createdAt);
    }
}
