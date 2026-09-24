package com.gms.service;

import com.gms.constant.Messages;
import com.gms.constant.Role;
import com.gms.dao.AccountDAO;
import com.gms.dao.PersonDAO;
import com.gms.dto.LoginResult;
import com.gms.dto.ServiceResult;
import com.gms.entity.Account;
import com.gms.entity.Person;
import com.gms.util.PasswordUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AccountDAO accountDAO;

    @Mock
    private PersonDAO personDAO;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(accountDAO, personDAO);
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
    @DisplayName("B2: Trống password -> Trả lỗi ô password")
    void login_EmptyPassword_ReturnsFieldError() {
        ServiceResult<LoginResult> result = authService.login("user@example.com", "");

        assertFalse(result.isSuccess());
        assertTrue(result.hasFieldErrors());
        assertEquals(String.format(Messages.MSG09_REQUIRED, "Mật khẩu"), result.getFieldErrors().get("password"));
    }

    @Test
    @DisplayName("B3: Không tìm thấy tài khoản theo email -> Báo lỗi sai thông tin đăng nhập")
    void login_AccountNotFound_ReturnsWrongCredentials() throws SQLException {
        when(accountDAO.findByEmail("notfound@example.com")).thenReturn(null);

        ServiceResult<LoginResult> result = authService.login("notfound@example.com", "Password123");

        assertFalse(result.isSuccess());
        assertEquals(Messages.MSG34_WRONG_CREDENTIALS_STAFF, result.getMessage());
    }

    @Test
    @DisplayName("B4: Tài khoản Inactive -> Báo lỗi MSG37")
    void login_InactiveAccount_ReturnsInactiveError() throws SQLException {
        Account inactiveAccount = new Account(1, 10, "hashed", Account.Status.INACTIVE, 0, null, null, LocalDateTime.now());
        when(accountDAO.findByEmail("inactive@example.com")).thenReturn(inactiveAccount);

        ServiceResult<LoginResult> result = authService.login("inactive@example.com", "Password123");

        assertFalse(result.isSuccess());
        assertEquals(Messages.MSG37_ACCOUNT_INACTIVE, result.getMessage());
    }

    @Test
    @DisplayName("B5: Tài khoản đang bị khóa tạm thời -> Báo lỗi MSG35")
    void login_TempLockedAccount_ReturnsTempLockedError() throws SQLException {
        LocalDateTime lockedUntil = LocalDateTime.now().plusMinutes(4);
        Account tempLockedAccount = new Account(1, 10, "hashed", Account.Status.ACTIVE, 5, lockedUntil, null, LocalDateTime.now());
        when(accountDAO.findByEmail("locked@example.com")).thenReturn(tempLockedAccount);

        ServiceResult<LoginResult> result = authService.login("locked@example.com", "Password123");

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Tài khoản bị khóa 5 phút"));
    }

    @Test
    @DisplayName("B6: Tài khoản chưa đặt mật khẩu (PasswordHash null) -> Báo lỗi MSG38")
    void login_NoPasswordSet_ReturnsNoPasswordError() throws SQLException {
        Account noPasswordAccount = new Account(1, 10, null, Account.Status.ACTIVE, 0, null, null, LocalDateTime.now());
        when(accountDAO.findByEmail("nopass@example.com")).thenReturn(noPasswordAccount);

        ServiceResult<LoginResult> result = authService.login("nopass@example.com", "Password123");

        assertFalse(result.isSuccess());
        assertEquals(Messages.MSG38_NO_PASSWORD_YET, result.getMessage());
    }

    @Test
    @DisplayName("B7: Nhân viên sai mật khẩu -> Trả lỗi chung MSG34, không đếm số lần sai")
    void login_StaffWrongPassword_ReturnsStaffWrongCredentials() throws SQLException {
        Account staffAccount = new Account(1, 10, "hashed_pw", Account.Status.ACTIVE, 0, null, null, LocalDateTime.now());
        when(accountDAO.findByEmail("staff@example.com")).thenReturn(staffAccount);
        when(personDAO.findRoleByPersonId(10)).thenReturn(Role.RECEPTIONIST);

        try (MockedStatic<PasswordUtil> mockedPasswordUtil = mockStatic(PasswordUtil.class)) {
            mockedPasswordUtil.when(() -> PasswordUtil.checkPassword("WrongPass", "hashed_pw")).thenReturn(false);

            ServiceResult<LoginResult> result = authService.login("staff@example.com", "WrongPass");

            assertFalse(result.isSuccess());
            assertEquals(Messages.MSG34_WRONG_CREDENTIALS_STAFF, result.getMessage());
            verify(accountDAO, never()).increaseFailedLogin(anyInt());
        }
    }

    @Test
    @DisplayName("B7: Khách hàng sai mật khẩu lần 1 -> Tăng counter, báo còn 4 lần thử MSG33")
    void login_CustomerWrongPassword_IncreasesFailedCount() throws SQLException {
        Account customerAccount = new Account(1, 10, "hashed_pw", Account.Status.ACTIVE, 0, null, null, LocalDateTime.now());
        when(accountDAO.findByEmail("customer@example.com")).thenReturn(customerAccount);
        when(personDAO.findRoleByPersonId(10)).thenReturn(Role.CUSTOMER);
        when(accountDAO.increaseFailedLogin(1)).thenReturn(true);

        try (MockedStatic<PasswordUtil> mockedPasswordUtil = mockStatic(PasswordUtil.class)) {
            mockedPasswordUtil.when(() -> PasswordUtil.checkPassword("WrongPass", "hashed_pw")).thenReturn(false);

            ServiceResult<LoginResult> result = authService.login("customer@example.com", "WrongPass");

            assertFalse(result.isSuccess());
            assertEquals(String.format(Messages.MSG33_WRONG_CREDENTIALS_ATTEMPTS_LEFT, 4), result.getMessage());
            verify(accountDAO).increaseFailedLogin(1);
        }
    }

    @Test
    @DisplayName("B7: Khách hàng sai mật khẩu lần thứ 5 -> Khóa tạm 5 phút MSG35")
    void login_CustomerWrongPassword5Times_LocksAccount() throws SQLException {
        Account customerAccount = new Account(1, 10, "hashed_pw", Account.Status.ACTIVE, 4, null, null, LocalDateTime.now());
        when(accountDAO.findByEmail("customer@example.com")).thenReturn(customerAccount);
        when(personDAO.findRoleByPersonId(10)).thenReturn(Role.CUSTOMER);
        when(accountDAO.increaseFailedLogin(1)).thenReturn(true);
        when(accountDAO.lockUntil(eq(1), any(LocalDateTime.class))).thenReturn(true);

        try (MockedStatic<PasswordUtil> mockedPasswordUtil = mockStatic(PasswordUtil.class)) {
            mockedPasswordUtil.when(() -> PasswordUtil.checkPassword("WrongPass", "hashed_pw")).thenReturn(false);

            ServiceResult<LoginResult> result = authService.login("customer@example.com", "WrongPass");

            assertFalse(result.isSuccess());
            assertTrue(result.getMessage().contains("Tài khoản bị khóa 5 phút"));
            verify(accountDAO).lockUntil(eq(1), any(LocalDateTime.class));
        }
    }

    @Test
    @DisplayName("B8: Đăng nhập thành công -> Cập nhật LastLoginAt, trả LoginResult ok")
    void login_Success_ReturnsLoginResult() throws SQLException {
        Account account = new Account(1, 10, "hashed_pw", Account.Status.ACTIVE, 0, null, null, LocalDateTime.now());
        Person person = new Person(10, "Nguyễn Văn A", LocalDate.of(1995, 1, 1), "0912345678", "user@example.com", null, false, LocalDateTime.now());

        when(accountDAO.findByEmail("user@example.com")).thenReturn(account);
        when(personDAO.findRoleByPersonId(10)).thenReturn(Role.CUSTOMER);
        when(personDAO.findPersonByPersonId(10)).thenReturn(person);
        when(accountDAO.updateLastLogin(eq(1), any(LocalDateTime.class))).thenReturn(true);

        try (MockedStatic<PasswordUtil> mockedPasswordUtil = mockStatic(PasswordUtil.class)) {
            mockedPasswordUtil.when(() -> PasswordUtil.checkPassword("CorrectPass123", "hashed_pw")).thenReturn(true);

            ServiceResult<LoginResult> result = authService.login("user@example.com", "CorrectPass123");

            assertTrue(result.isSuccess());
            assertNotNull(result.getData());
            assertEquals("Nguyễn Văn A", result.getData().getFullName());
            assertEquals(Role.CUSTOMER, result.getData().getRole());
            assertEquals("/customer/dashboard", result.getData().getRedirectUrl());
            verify(accountDAO).updateLastLogin(eq(1), any(LocalDateTime.class));
        }
    }
}
