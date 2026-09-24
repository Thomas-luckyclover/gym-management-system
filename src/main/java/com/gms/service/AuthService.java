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
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service xử lý nghiệp vụ Xác thực & Tài khoản (Phase 1, SRS #1 Sign In).
 *
 * <p>
 * Thứ tự kiểm tra (khớp 01_Phase1_Rules.md mục 2.1):
 * </p>
 * <ul>
 * <li>B1: Kiểm tra dữ liệu nhập (identifier trống/sai dạng, password
 * trống).</li>
 * <li>B2 & B3: Tìm tài khoản theo Email hoặc SĐT (Person IsReleased = 0 JOIN
 * Accounts).</li>
 * <li>B4: Account.Status khác Active (Inactive: nhân viên nghỉ, Locked: Admin
 * khóa) → MSG37.</li>
 * <li>B5: KHÁCH đang bị khóa tạm (LockedUntil) → MSG35; hết khóa → reset
 * (BR-02).</li>
 * <li>B6: Chưa có mật khẩu → MSG38.</li>
 * <li>B7: Sai mật khẩu — khách: đếm + khóa tạm (BR-01); nhân viên: MSG34, không
 * đếm, không khóa.</li>
 * <li>B8: Thành công → reset bộ đếm, cập nhật LastLoginAt.</li>
 * </ul>
 *
 * <p>
 * Việc tạo session (đổi session id, accountId/role/displayName, timeout 7200s)
 * do LoginServlet làm.
 * </p>
 */
public class AuthService {

    private static final Logger LOGGER = Logger.getLogger(AuthService.class.getName());

    /**
     * GBR-01: múi giờ UTC+7. Nếu dự án có DateTimeUtil.now() thì đổi sang dùng hàm
     * đó.
     */
    private static final ZoneId ZONE_VN = ZoneId.of("Asia/Ho_Chi_Minh");

    /**
     * P-17: số lần sai trước khi khóa tạm.
     * TODO: đọc qua SystemConfigService (agents.md Phần 13) khi có API — hiện chỉ
     * là giá trị seed.
     */
    private static final int MAX_FAILED_ATTEMPTS = 5;

    /**
     * P-18: thời gian khóa tạm (phút). TODO: đọc qua SystemConfigService như P-17.
     */
    private static final int LOCK_MINUTES = 5;

    /**
     * UC-01: SĐT dùng chung giữa khách và nhân viên → chỉ đăng nhập bằng email.
     * TODO: chuyển thành Messages.EXTRA_PHONE_SHARED_USE_EMAIL (và js/common.js)
     * khi được phép sửa Messages.
     */
    private static final String MSG_PHONE_SHARED_USE_EMAIL = "Số điện thoại này dùng chung cho nhiều tài khoản. Vui lòng đăng nhập bằng email.";

    /**
     * Hash giả để tài khoản không tồn tại cũng tốn thời gian BCrypt như tài khoản
     * có thật.
     */
    private static final String DUMMY_PASSWORD_HASH = PasswordUtil.hashPassword("GMS-dummy-password-1");

    private final AccountDAO accountDAO;

    public AuthService() {
        this(new AccountDAO());
    }

    public AuthService(AccountDAO accountDAO) {
        this.accountDAO = accountDAO;
    }

    /**
     * Xử lý đăng nhập tài khoản.
     *
     * @param identifier Email hoặc Số điện thoại người dùng nhập
     * @param password   Mật khẩu người dùng nhập (không trim — giữ nguyên từng ký
     *                   tự)
     * @return ServiceResult chứa LoginResult khi thành công, hoặc lỗi chi tiết
     */
    public ServiceResult<LoginResult> login(String identifier, String password) {
        // B1: kiểm tra dữ liệu nhập, gom lỗi cả form (GBR-17)
        String trimmedIdentifier = identifier == null ? "" : identifier.trim();
        boolean isEmail = trimmedIdentifier.contains("@");

        Map<String, String> errors = new LinkedHashMap<>();
        if (trimmedIdentifier.isEmpty()) {
            errors.put("identifier", Messages.MSG39_IDENTIFIER_REQUIRED);
        } else if (isEmail) {
            if (!ValidationUtil.isValidEmail(trimmedIdentifier)) {
                errors.put("identifier", Messages.MSG11_INVALID_EMAIL);
            }
        } else if (!ValidationUtil.isValidPhone(trimmedIdentifier)) {
            errors.put("identifier", Messages.MSG13_INVALID_PHONE);
        }
        // Mật khẩu KHÔNG trim: chỉ coi là trống khi null hoặc dài 0
        if (password == null || password.isEmpty()) {
            errors.put("password", String.format(Messages.MSG09_REQUIRED, "Mật khẩu"));
        }
        if (!errors.isEmpty()) {
            return buildFieldFailure(errors);
        }

        // Dùng 1 connection cho cả luồng đăng nhập
        try (Connection conn = DBContext.getConnection()) {
            return authenticate(conn, trimmedIdentifier, isEmail, password);
        } catch (SQLException e) {
            // Không log identifier/password
            LOGGER.log(Level.SEVERE, "Lỗi SQL khi đăng nhập", e);
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    private ServiceResult<LoginResult> authenticate(Connection conn, String identifier,
            boolean isEmail, String password) throws SQLException {
        // B2 & B3: tìm tài khoản
        Account account;
        if (isEmail) {
            account = accountDAO.findByEmail(conn, identifier);
        } else {
            List<Account> matched = accountDAO.findAllByPhone(conn, identifier);
            if (matched.size() > 1) {
                return ServiceResult.failField("identifier", MSG_PHONE_SHARED_USE_EMAIL);
            }
            account = matched.isEmpty() ? null : matched.get(0);
        }

        if (account == null) {
            // Cân bằng thời gian phản hồi, tránh lộ tài khoản có tồn tại hay không
            PasswordUtil.checkPassword(password, DUMMY_PASSWORD_HASH);
            return ServiceResult.fail(Messages.MSG34_WRONG_CREDENTIALS_STAFF);
        }

        // B4: chỉ Active mới được đi tiếp (Inactive = nhân viên nghỉ, Locked = Admin
        // khóa)
        if (account.getStatus() != Account.Status.ACTIVE) {
            return ServiceResult.fail(Messages.MSG37_ACCOUNT_INACTIVE);
        }

        // Xác định vai trò TRƯỚC B5 vì khóa tạm chỉ áp cho khách.
        // Không còn vai trò hoạt động nào → coi như tài khoản ngừng hoạt động.
        // TODO (mục 7 dòng 4, chờ chốt): Person có cả 2 hồ sơ thì hỏi chọn vai trò.
        AccountDAO.LoginProfile profile = accountDAO.findLoginProfile(conn, account.getAccountId());
        if (profile == null) {
            // Account trỏ tới Person không tồn tại — dữ liệu lệch, không cho đăng nhập tiếp
            LOGGER.log(Level.SEVERE, "Account {0} không có Person hợp lệ", account.getAccountId());
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
        Role role = profile.getRole();
        if (role == null) {
            return ServiceResult.fail(Messages.MSG37_ACCOUNT_INACTIVE);
        }
        boolean isStaff = role != Role.CUSTOMER;

        LocalDateTime now = LocalDateTime.now(ZONE_VN);

        // B5: khóa tạm do sai mật khẩu — chỉ áp cho KHÁCH (BR-01)
        if (!isStaff && account.getLockedUntil() != null) {
            if (now.isBefore(account.getLockedUntil())) {
                long remainingSeconds = Duration.between(now, account.getLockedUntil()).getSeconds();
                return ServiceResult.fail(String.format(
                        Messages.MSG35_ACCOUNT_TEMP_LOCKED, LOCK_MINUTES, formatMinutesSeconds(remainingSeconds)));
            }
            // Hết khóa → tự mở, bộ đếm về 0 (BR-02)
            accountDAO.resetFailedLogin(conn, account.getAccountId());
            account.setFailedLoginCount(0);
            account.setLockedUntil(null);
        }

        // B6: tài khoản chưa có mật khẩu (Lễ tân tạo, khách chưa đặt)
        if (account.getPasswordHash() == null || account.getPasswordHash().trim().isEmpty()) {
            return ServiceResult.fail(Messages.MSG38_NO_PASSWORD_YET);
        }

        // B7: kiểm tra mật khẩu
        boolean isPasswordMatched = PasswordUtil.checkPassword(password, account.getPasswordHash());
        if (!isPasswordMatched) {
            if (isStaff) {
                // Nhân viên: báo chung, không đếm, không bao giờ tự khóa
                return ServiceResult.fail(Messages.MSG34_WRONG_CREDENTIALS_STAFF);
            }
            // Khách: tăng đếm + khóa tạm trong 1 câu lệnh (không dùng số đếm đã đọc từ đầu
            // request)
            int failedCount = accountDAO.increaseFailedLoginAndLock(
                    conn, account.getAccountId(), MAX_FAILED_ATTEMPTS, now.plusMinutes(LOCK_MINUTES));
            if (failedCount < 0) {
                // Tài khoản vừa bị xóa giữa chừng
                return ServiceResult.fail(Messages.MSG34_WRONG_CREDENTIALS_STAFF);
            }
            if (failedCount >= MAX_FAILED_ATTEMPTS) {
                return ServiceResult.fail(String.format(
                        Messages.MSG35_ACCOUNT_TEMP_LOCKED, LOCK_MINUTES,
                        formatMinutesSeconds(Duration.ofMinutes(LOCK_MINUTES).getSeconds())));
            }
            return ServiceResult.fail(String.format(
                    Messages.MSG33_WRONG_CREDENTIALS_ATTEMPTS_LEFT, MAX_FAILED_ATTEMPTS - failedCount));
        }

        // B8: đăng nhập thành công
        if (account.getFailedLoginCount() > 0 || account.getLockedUntil() != null) {
            accountDAO.resetFailedLogin(conn, account.getAccountId());
            account.setFailedLoginCount(0);
            account.setLockedUntil(null);
        }
        accountDAO.updateLastLogin(conn, account.getAccountId(), now);

        LoginResult loginResult = new LoginResult(
                account.getAccountId(),
                account.getPersonId(),
                profile.getFullName() != null ? profile.getFullName() : "",
                profile.getEmail() != null ? profile.getEmail() : "",
                profile.getPhone() != null ? profile.getPhone() : "",
                profile.getAvatar(),
                role,
                role.getHomePath());
        return ServiceResult.ok(loginResult);
    }

    /** GBR-27: thời gian còn lại dạng mm:ss. */
    private static String formatMinutesSeconds(long totalSeconds) {
        long seconds = Math.max(totalSeconds, 0);
        return String.format("%02d:%02d", seconds / 60, seconds % 60);
    }

    /** 1 ô lỗi → failField (có message); nhiều ô → failFields. */
    private static ServiceResult<LoginResult> buildFieldFailure(Map<String, String> errors) {
        if (errors.size() == 1) {
            Map.Entry<String, String> only = errors.entrySet().iterator().next();
            return ServiceResult.failField(only.getKey(), only.getValue());
        }
        return ServiceResult.failFields(errors);
    }
}