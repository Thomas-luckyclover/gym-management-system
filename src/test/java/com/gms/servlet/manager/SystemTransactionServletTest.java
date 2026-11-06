package com.gms.servlet.manager;

import com.gms.constant.Messages;
import com.gms.constant.SessionKey;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

/**
 * Unit Test kiểm thử Servlet Màn hình Giao dịch toàn hệ thống cho Quản lý (Màn #41 - UC-26).
 */
class SystemTransactionServletTest {

    private SystemTransactionServlet servlet;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private HttpSession session;
    private StringWriter responseWriter;

    @BeforeEach
    void setUp() throws Exception {
        servlet = new SystemTransactionServlet();
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        session = mock(HttpSession.class);

        when(request.getContextPath()).thenReturn("");
        when(request.getSession()).thenReturn(session);
        when(request.getSession(false)).thenReturn(session);
        when(request.getSession(true)).thenReturn(session);

        responseWriter = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(responseWriter));
    }

    @Test
    @DisplayName("GET: Chuyển hướng về /login khi chưa đăng nhập hoặc session hết hạn")
    void testDoGet_RedirectWhenNotLoggedIn() throws Exception {
        when(request.getSession(false)).thenReturn(null);
        when(request.getSession()).thenReturn(null);

        servlet.doGet(request, response);

        verify(response).sendRedirect("/login");
    }

    @Test
    @DisplayName("GET ?action=detail: Trả về lỗi 400 khi orderId không hợp lệ (<= 0)")
    void testDoGet_DetailInvalidOrderId_ReturnsBadRequest() throws Exception {
        when(session.getAttribute(SessionKey.ACCOUNT_ID)).thenReturn(12);
        when(request.getParameter("action")).thenReturn("detail");
        when(request.getParameter("orderId")).thenReturn("0");

        servlet.doGet(request, response);

        verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
        String json = responseWriter.toString();
        assertTrue(json.contains(Messages.MSG170_ORDER_CODE_INVALID_FULL),
                "Phải trả về thông báo mã đơn hàng không hợp lệ: " + json);
    }

    @Test
    @DisplayName("POST: Chuyển hướng về /login khi chưa đăng nhập")
    void testDoPost_RedirectWhenNotLoggedIn() throws Exception {
        when(request.getSession(false)).thenReturn(null);
        when(request.getSession()).thenReturn(null);

        servlet.doPost(request, response);

        verify(response).sendRedirect("/login");
    }

    @Test
    @DisplayName("POST: Chặn và từ chối khi CSRF token không khớp")
    void testDoPost_InvalidCsrfToken_Blocked() throws Exception {
        when(session.getAttribute(SessionKey.ACCOUNT_ID)).thenReturn(12);
        when(session.getAttribute(SessionKey.CSRF_TOKEN)).thenReturn("valid-token");
        when(request.getParameter("csrfToken")).thenReturn("wrong-token");

        servlet.doPost(request, response);

        verify(response).sendRedirect("/manager/transactions");
        verify(session).setAttribute(eq("flashToast"), any());
    }

    @Test
    @DisplayName("POST: Chuyển hướng về /manager/transactions khi action không hợp lệ")
    void testDoPost_InvalidAction_RedirectsToTransactions() throws Exception {
        when(session.getAttribute(SessionKey.ACCOUNT_ID)).thenReturn(12);
        when(session.getAttribute(SessionKey.CSRF_TOKEN)).thenReturn("valid-token");
        when(request.getParameter("csrfToken")).thenReturn("valid-token");
        when(request.getParameter("action")).thenReturn("unknownAction");

        servlet.doPost(request, response);

        verify(response).sendRedirect("/manager/transactions");
    }
}
