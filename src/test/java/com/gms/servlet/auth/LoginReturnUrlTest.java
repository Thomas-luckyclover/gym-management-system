package com.gms.servlet.auth;

import com.gms.constant.Role;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Kiểm tra LoginServlet.canReturnTo — lỗi: hết phiên ở trang PT, đăng nhập lại bằng tài khoản KHÁC (Lễ tân)
 * → bị đẩy vào /pt/... → 403. Mong đợi: chỉ quay về returnUrl khi vai trò vừa đăng nhập vào được trang đó.
 */
class LoginReturnUrlTest {

    /** Trang khu vực vai trò khác → không quay về (đi trang chủ vai trò). */
    @Test
    void otherRoleAreaIsRejected() {
        assertFalse(LoginServlet.canReturnTo("/pt/schedule", Role.RECEPTIONIST));
        assertFalse(LoginServlet.canReturnTo("/admin/dashboard", Role.MANAGER));
        assertFalse(LoginServlet.canReturnTo("/customer/dashboard", Role.PT));
    }

    /** Trang đúng khu vực của vai trò → quay về, giữ cả query. */
    @Test
    void ownAreaIsAccepted() {
        assertTrue(LoginServlet.canReturnTo("/pt/schedule?date=2026-11-09&view=week", Role.PT));
        assertTrue(LoginServlet.canReturnTo("/receptionist/pt-sales", Role.RECEPTIONIST));
    }

    /** Trang dùng chung mọi vai trò → quay về. */
    @Test
    void sharedPageIsAccepted() {
        assertTrue(LoginServlet.canReturnTo("/account-settings", Role.CUSTOMER));
        assertTrue(LoginServlet.canReturnTo("/notifications", Role.ADMIN));
    }

    /** URL không an toàn / trống → không quay về. */
    @Test
    void unsafeUrlIsRejected() {
        assertFalse(LoginServlet.canReturnTo(null, Role.PT));
        assertFalse(LoginServlet.canReturnTo("", Role.PT));
        assertFalse(LoginServlet.canReturnTo("//evil.com/pt/", Role.PT));
        assertFalse(LoginServlet.canReturnTo("https://evil.com", Role.PT));
    }
}
