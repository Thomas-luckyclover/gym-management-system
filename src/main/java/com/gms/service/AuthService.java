package com.gms.service;

import com.gms.constant.Messages;
import com.gms.constant.Role;
import com.gms.dao.AccountDAO;
import com.gms.dto.LoginResult;
import com.gms.dto.ServiceResult;
import com.gms.entity.Account;
import com.gms.util.DBContext;
import com.gms.util.PasswordUtil;
import com.gms.util.ValidationUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Service xử lý nghiệp vụ Xác thực & Tài khoản (Phase 1, SRS #1 Sign In).
 *
 * <p>
 * Quy tắc Đăng nhập (B1 – B8 theo quy tắc Phase 1 Mục 2.1):
 * </p>
 * <ul>
 * <li>B1 & B2: Kiểm tra dữ liệu nhập (identifier, password).</li>
 * <li>B3: Tìm tài khoản theo Email hoặc SĐT (Person JOIN Accounts).</li>
 * <li>B4: Kiểm tra trạng thái Inactive hoặc Admin khóa (Account.Status !=
 * ACTIVE).</li>
 * <li>B5: Kiểm tra thời gian khóa tạm thời (LockedUntil). Hết hạn -> tự reset
 * (BR-02).</li>
 * <li>B6: Kiểm tra tài khoản chưa có mật khẩu (PasswordHash IS NULL).</li>
 * <li>B7: Kiểm tra mật khẩu (Phân biệt Staff vs Customer cho đếm sai/khóa
 * tạm).</li>
 * <li>B8: Đăng nhập thành công -> Reset failed counter, update
 * LastLoginAt.</li>
 * </ul>
 *
 * <p>
 * LƯU Ý: Các thông số cấu hình P-17 (MAX_FAILED_ATTEMPTS = 5) và P-18
 * (LOCK_MINUTES = 5)
 * hiện đang dùng hằng số tạm thời, sẽ được nâng cấp đọc qua SystemConfigService
 * khi có API từ Minh LC.
 * </p>
 */
public class AuthService {

    /**
     * P-17: Số lần nhập sai tối đa trước khi khóa tạm. Tạm dùng hằng số, chờ
     * SystemConfigService từ Minh LC.
     */
    private static final int MAX_FAILED_ATTEMPTS = 5;

    /**
     * P-18: Thời gian khóa tạm (phút). Tạm dùng hằng số, chờ SystemConfigService từ
     * Minh LC.
     */
    private static final int LOCK_MINUTES = 5;

    /** Màn chọn vai trò (CBR-113). */
    public static final String SELECT_ROLE_PATH = "/select-role";

    private final AccountDAO accountDAO;

    public AuthService() {
        this(new AccountDAO());
    }

    public AuthService(AccountDAO accountDAO) {
        this.accountDAO = accountDAO;
    }

    protected Connection getConnection() throws SQLException {
        return DBContext.getConnection();
    }

    /**
     * Xử lý đăng nhập tài khoản.
     *
     * @param identifier Email hoặc Số điện thoại người dùng nhập
     * @param password   Mật khẩu người dùng nhập
     * @return ServiceResult chứa LoginResult khi thành công, hoặc lỗi chi tiết
     */
    public ServiceResult<LoginResult> login(String identifier, String password) {
        // B1 & B2: Kiểm tra tham số đầu vào
        boolean isIdentifierEmpty = ValidationUtil.isNullOrEmpty(identifier);
        boolean isPasswordEmpty = ValidationUtil.isNullOrEmpty(password);

        if (isIdentifierEmpty && isPasswordEmpty) {
            Map<String, String> errors = new LinkedHashMap<>();
            errors.put("identifier", Messages.MSG39_IDENTIFIER_REQUIRED);
            errors.put("password", String.format(Messages.MSG09_REQUIRED, "Mật khẩu"));
            return ServiceResult.failFields(errors);
        }
        if (isIdentifierEmpty) {
            return ServiceResult.failField("identifier", Messages.MSG39_IDENTIFIER_REQUIRED);
        }
        if (isPasswordEmpty) {
            return ServiceResult.failField("password", String.format(Messages.MSG09_REQUIRED, "Mật khẩu"));
        }

        String trimmedIdentifier = identifier.trim();

        try (Connection conn = getConnection()) {
            // B3: Tìm tài khoản theo Email hoặc SĐT
            Account account = null;
            if (ValidationUtil.isValidEmail(trimmedIdentifier)) {
                account = accountDAO.findByEmail(conn, trimmedIdentifier);
            } else if (ValidationUtil.isValidPhone(trimmedIdentifier)) {
                account = accountDAO.findByPhone(conn, trimmedIdentifier);
            }

            if (account == null) {
                return ServiceResult.fail(Messages.MSG34_WRONG_CREDENTIALS_STAFF);
            }

            // B4: Kiểm tra trạng thái tài khoản (Inactive hoặc Admin khóa)
            if (account.getStatus() == Account.Status.INACTIVE || account.getStatus() == Account.Status.LOCKED) {
                return ServiceResult.fail(Messages.MSG37_ACCOUNT_INACTIVE);
            }

            LocalDateTime now = LocalDateTime.now();

            // B5: Kiểm tra khóa tạm thời do nhập sai quá số lần (LockedUntil)
            if (account.getLockedUntil() != null) {
                if (now.isBefore(account.getLockedUntil())) {
                    long remainingSeconds = Duration.between(now, account.getLockedUntil()).getSeconds();
                    return ServiceResult.fail(String.format(Messages.MSG35_ACCOUNT_TEMP_LOCKED,
                            LOCK_MINUTES, formatMinutesSeconds(remainingSeconds)));
                } else {
                    // Hết hạn khóa tạm -> reset số lần sai (BR-02)
                    accountDAO.resetFailedLogin(conn, account.getAccountId());
                    account.setFailedLoginCount(0);
                    account.setLockedUntil(null);
                }
            }

            // B6: Kiểm tra tài khoản chưa có mật khẩu
            if (account.getPasswordHash() == null || account.getPasswordHash().trim().isEmpty()) {
                return ServiceResult.fail(Messages.MSG38_NO_PASSWORD_YET);
            }

            // B6a: lấy mọi vai trò còn hiệu lực của Person (DBR-65)
            PersonProfile profile = fetchPersonProfile(conn, account.getPersonId());

            // B6b: không còn vai trò nào → chặn như Inactive (CBR-86)
            if (profile.roles.isEmpty()) {
                return ServiceResult.fail(Messages.MSG37_ACCOUNT_INACTIVE);
            }

            // B6c: có vai trò nhân viên thì không tự khóa (BR-01)
            boolean isStaff = profile.hasStaffRole();

            // B7: Kiểm tra mật khẩu
            boolean isPasswordMatched = PasswordUtil.checkPassword(password, account.getPasswordHash());

            if (!isPasswordMatched) {
                if (isStaff) {
                    // Nhân viên sai mật khẩu -> báo thông báo chung, không đếm số lần còn lại
                    return ServiceResult.fail(Messages.MSG34_WRONG_CREDENTIALS_STAFF);
                } else {
                    // Khách hàng sai mật khẩu -> tăng counter và đếm số lần thử
                    accountDAO.increaseFailedLogin(conn, account.getAccountId());
                    int newFailedCount = account.getFailedLoginCount() + 1;
                    account.setFailedLoginCount(newFailedCount);

                    if (newFailedCount >= MAX_FAILED_ATTEMPTS) {
                        LocalDateTime lockTime = now.plusMinutes(LOCK_MINUTES);
                        accountDAO.lockUntil(conn, account.getAccountId(), lockTime);
                        account.setLockedUntil(lockTime);
                        return ServiceResult.fail(String.format(Messages.MSG35_ACCOUNT_TEMP_LOCKED, LOCK_MINUTES,
                                formatMinutesSeconds(LOCK_MINUTES * 60L)));
                    } else {
                        int attemptsLeft = MAX_FAILED_ATTEMPTS - newFailedCount;
                        return ServiceResult
                                .fail(String.format(Messages.MSG33_WRONG_CREDENTIALS_ATTEMPTS_LEFT, attemptsLeft));
                    }
                }
            }

            // B8: Đăng nhập thành công -> Reset failed counter và cập nhật LastLoginAt
            if (account.getFailedLoginCount() > 0 || account.getLockedUntil() != null) {
                accountDAO.resetFailedLogin(conn, account.getAccountId());
                account.setFailedLoginCount(0);
                account.setLockedUntil(null);
            }

            accountDAO.updateLastLogin(conn, account.getAccountId(), now);
            account.setLastLoginAt(now);

            // B9: 1 vai trò vào thẳng, ≥ 2 vai trò chọn vai trò (CBR-113)
            boolean isSingleRole = profile.roles.size() == 1;
            Role role = isSingleRole ? profile.roles.get(0) : null;
            String redirectUrl = isSingleRole ? role.getHomePath() : SELECT_ROLE_PATH;

            LoginResult loginResult = new LoginResult(
                    account.getAccountId(),
                    account.getPersonId(),
                    profile.fullName,
                    profile.email,
                    profile.phone,
                    profile.avatar,
                    role,
                    redirectUrl,
                    profile.roles);

            return ServiceResult.ok(loginResult);

        } catch (SQLException e) {
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /** MSG35 hiển thị thời gian còn lại dạng mm:ss (GBR-27), VD 272 giây → "04:32". */
    private static String formatMinutesSeconds(long totalSeconds) {
        return String.format("%02d:%02d", totalSeconds / 60, totalSeconds % 60);
    }

    // ===== Private Helper đọc thông tin Person & Role=====

    private static class PersonProfile {
        final String fullName;
        final String email;
        final String phone;
        final String avatar;
        final List<Role> roles;

        PersonProfile(String fullName, String email, String phone, String avatar, List<Role> roles) {
            this.fullName = fullName != null ? fullName : "";
            this.email = email != null ? email : "";
            this.phone = phone != null ? phone : "";
            this.avatar = avatar;
            this.roles = roles;
        }

        boolean hasStaffRole() {
            for (Role r : roles) {
                if (r != Role.CUSTOMER) {
                    return true;
                }
            }
            return false;
        }
    }

    /** Đọc Person và các vai trò còn hiệu lực (DBR-65). */
    private PersonProfile fetchPersonProfile(Connection conn, int personId) throws SQLException {
        if (conn == null) {
            return new PersonProfile("", "", "", null, List.of(Role.CUSTOMER));
        }
        String sql = """
                SELECT p.FullName, p.Email, p.Phone, p.Avatar,
                       (SELECT TOP 1 e.Position FROM Employees e
                         WHERE e.PersonId = p.PersonId AND e.Status = 'Working') AS StaffPosition,
                       (SELECT TOP 1 c.CustomerId FROM Customers c
                         WHERE c.PersonId = p.PersonId AND c.Status <> 'Inactive') AS ActiveCustomerId
                FROM Persons p
                WHERE p.PersonId = ?
                """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, personId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    List<Role> roles = new ArrayList<>();
                    Role staffRole = Role.fromDbValue(rs.getString("StaffPosition"));
                    if (staffRole != null && staffRole != Role.CUSTOMER) {
                        roles.add(staffRole);
                    }
                    if (rs.getObject("ActiveCustomerId") != null) {
                        roles.add(Role.CUSTOMER);
                    }
                    return new PersonProfile(rs.getString("FullName"), rs.getString("Email"),
                            rs.getString("Phone"), rs.getString("Avatar"), roles);
                }
            }
        }
        return new PersonProfile("", "", "", null, List.of());
    }

    /**
     * Lấy vai trò còn hiệu lực của tài khoản cho màn chọn vai trò (CBR-113).
     * @param accountId tài khoản đang đăng nhập
     * @return danh sách vai trò, rỗng nếu tài khoản không còn hoạt động
     */
    public List<Role> findActiveRoles(int accountId) {
        try (Connection conn = getConnection()) {
            Account account = accountDAO.findById(conn, accountId);
            if (account == null || account.getStatus() != Account.Status.ACTIVE) {
                return List.of();
            }
            return fetchPersonProfile(conn, account.getPersonId()).roles;
        } catch (SQLException e) {
            e.printStackTrace();
            return List.of();
        }
    }
}
