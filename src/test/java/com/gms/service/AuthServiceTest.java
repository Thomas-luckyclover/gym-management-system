package com.gms.service;

import com.gms.constant.Messages;
import com.gms.dao.AccountDAO;
import com.gms.dto.LoginResult;
import com.gms.dto.ServiceResult;
import com.gms.entity.Account;
import com.gms.util.PasswordUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthServiceTest {

    private FakeAccountDAO fakeAccountDAO;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        fakeAccountDAO = new FakeAccountDAO();
        authService = new TestableAuthService(fakeAccountDAO, new FakeSystemConfigService(3, 7));
    }

    @Test
    @DisplayName("B1: Trống identifier và password -> Trả lỗi cả 2 ô")
    void login_EmptyIdentifierAndPassword_ReturnsFieldErrors() {
        ServiceResult<LoginResult> result = authService.login("", "");

        assertFalse(result.isSuccess());
        assertTrue(result.hasFieldErrors());
        assertEquals(Messages.MSG39_IDENTIFIER_REQUIRED, result.getFieldErrors().get("identifier"));
        assertEquals(String.format(Messages.MSG09_REQUIRED, "Mật khẩu"), result.getFieldErrors().get("password"));
    }

    @Test
    @DisplayName("B1: Trống identifier -> Trả lỗi ô identifier")
    void login_EmptyIdentifier_ReturnsFieldError() {
        ServiceResult<LoginResult> result = authService.login("", "Password123");

        assertFalse(result.isSuccess());
        assertTrue(result.hasFieldErrors());
        assertEquals(Messages.MSG39_IDENTIFIER_REQUIRED, result.getFieldErrors().get("identifier"));
    }

    @Test
    @DisplayName("B1: Trống password -> Trả lỗi ô password")
    void login_EmptyPassword_ReturnsFieldError() {
        ServiceResult<LoginResult> result = authService.login("user@example.com", "");

        assertFalse(result.isSuccess());
        assertTrue(result.hasFieldErrors());
        assertEquals(String.format(Messages.MSG09_REQUIRED, "Mật khẩu"), result.getFieldErrors().get("password"));
    }

    @Test
    @DisplayName("B3: Không tìm thấy tài khoản -> Trả lỗi MSG34")
    void login_AccountNotFound_ReturnsWrongCredentials() {
        ServiceResult<LoginResult> result = authService.login("notfound@example.com", "Password123");

        assertFalse(result.isSuccess());
        assertEquals(Messages.MSG34_WRONG_CREDENTIALS_STAFF, result.getMessage());
    }

    @Test
    @DisplayName("B4: Tài khoản Inactive -> Trả lỗi MSG37")
    void login_InactiveAccount_ReturnsInactiveError() {
        Account inactiveAccount = new Account(1, 10, "hashed", Account.Status.INACTIVE, 0, null, null, LocalDateTime.now());
        fakeAccountDAO.setEmailAccount(inactiveAccount);

        ServiceResult<LoginResult> result = authService.login("inactive@example.com", "Password123");

        assertFalse(result.isSuccess());
        assertEquals(Messages.MSG37_ACCOUNT_INACTIVE, result.getMessage());
    }

    @Test
    @DisplayName("B7: Khách sai mật khẩu, còn lượt -> MSG33 theo P-17 trong SystemConfigs")
    void login_CustomerWrongPassword_UsesMaxAttemptsFromConfig() {
        Account customer = new Account(2, 20, PasswordUtil.hashPassword("Dung2026abc"), Account.Status.ACTIVE, 0, null, null, LocalDateTime.now());
        fakeAccountDAO.setEmailAccount(customer);

        ServiceResult<LoginResult> result = authService.login("khach@example.com", "Sai2026abc");

        assertFalse(result.isSuccess());
        assertEquals(String.format(Messages.MSG33_WRONG_CREDENTIALS_ATTEMPTS_LEFT, 2), result.getMessage());
    }

    @Test
    @DisplayName("B7: Khách sai đủ P-17 lần -> khóa tạm đúng P-18 phút")
    void login_CustomerReachesMaxAttempts_LocksForConfiguredMinutes() {
        Account customer = new Account(3, 30, PasswordUtil.hashPassword("Dung2026abc"), Account.Status.ACTIVE, 2, null, null, LocalDateTime.now());
        fakeAccountDAO.setEmailAccount(customer);

        ServiceResult<LoginResult> result = authService.login("khach@example.com", "Sai2026abc");

        assertFalse(result.isSuccess());
        assertEquals(String.format(Messages.MSG35_ACCOUNT_TEMP_LOCKED, 7, "07:00"), result.getMessage());
        assertTrue(fakeAccountDAO.isLocked());
    }

    private static class TestableAuthService extends AuthService {
        TestableAuthService(AccountDAO accountDAO, SystemConfigService systemConfigService) {
            super(accountDAO, systemConfigService);
        }

        @Override
        protected Connection getConnection() throws SQLException {
            return null; // Không cần kết nối SQL thật trong unit test
        }
    }

    /** Fake AccountDAO cho unit test */
    private static class FakeAccountDAO extends AccountDAO {
        private Account emailAccount;
        private boolean locked;

        void setEmailAccount(Account account) {
            this.emailAccount = account;
        }

        @Override
        public Account findByEmail(Connection conn, String email) throws SQLException {
            return emailAccount;
        }

        @Override
        public Account findByEmail(String email) throws SQLException {
            return emailAccount;
        }

        @Override
        public boolean increaseFailedLogin(Connection conn, int accountId) {
            return true;
        }

        @Override
        public boolean lockUntil(Connection conn, int accountId, LocalDateTime lockedUntil) {
            locked = lockedUntil != null;
            return true;
        }

        boolean isLocked() {
            return locked;
        }
    }

    /** Fake SystemConfigService: trả P-17, P-18 cố định, không cần DB. */
    private static class FakeSystemConfigService extends SystemConfigService {
        private final int maxFailedAttempts;
        private final int lockMinutes;

        FakeSystemConfigService(int maxFailedAttempts, int lockMinutes) {
            this.maxFailedAttempts = maxFailedAttempts;
            this.lockMinutes = lockMinutes;
        }

        @Override
        public int getInt(String configKey) {
            if (MAX_FAILED_ATTEMPTS.equals(configKey)) {
                return maxFailedAttempts;
            }
            return lockMinutes;
        }
    }
}
