package com.gms.service;

import com.gms.constant.Messages;
import com.gms.dao.AccountDAO;
import com.gms.dto.LoginResult;
import com.gms.dto.ServiceResult;
import com.gms.entity.Account;
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
        authService = new TestableAuthService(fakeAccountDAO);
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

    private static class TestableAuthService extends AuthService {
        TestableAuthService(AccountDAO accountDAO) {
            super(accountDAO);
        }

        @Override
        protected Connection getConnection() throws SQLException {
            return null; // Không cần kết nối SQL thật trong unit test
        }
    }

    /** Fake AccountDAO cho unit test */
    private static class FakeAccountDAO extends AccountDAO {
        private Account emailAccount;

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
    }
}
