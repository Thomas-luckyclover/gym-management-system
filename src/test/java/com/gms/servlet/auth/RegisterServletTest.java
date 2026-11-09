package com.gms.servlet.auth;

import com.gms.constant.Messages;
import com.gms.service.RegistrationService;
import com.gms.service.VerificationCodeService;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class RegisterServletTest {

    private RegistrationService mockRegistrationService;
    private VerificationCodeService mockVerificationCodeService;
    private RegisterServlet servlet;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private RequestDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        mockRegistrationService = mock(RegistrationService.class);
        mockVerificationCodeService = mock(VerificationCodeService.class);
        servlet = new RegisterServlet(mockRegistrationService, mockVerificationCodeService);

        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        dispatcher = mock(RequestDispatcher.class);

        when(request.getRequestDispatcher(anyString())).thenReturn(dispatcher);
    }

    @Test
    @DisplayName("doGet: Không có tham số -> step là phone")
    void doGet_Default_SetsStepPhone() throws ServletException, IOException {
        servlet.doGet(request, response);

        verify(request).setAttribute("step", "phone");
        verify(dispatcher).forward(request, response);
    }

    @Test
    @DisplayName("doGet: step=2 và phone hợp lệ -> chuyển sang claim-email")
    void doGet_Step2WithPhone_SetsStepClaimEmail() throws ServletException, IOException {
        when(request.getParameter("step")).thenReturn("2");
        when(request.getParameter("phone")).thenReturn("0912345678");

        servlet.doGet(request, response);

        verify(request).setAttribute("step", "claim-email");
        verify(request).setAttribute("phone", "0912345678");
        verify(dispatcher).forward(request, response);
    }

    @Test
    @DisplayName("doPost: step phone nhưng SĐT không hợp lệ -> báo lỗi MSG13")
    void doPost_InvalidPhone_ShowsFieldError() throws ServletException, IOException {
        when(request.getParameter("step")).thenReturn("phone");
        when(request.getParameter("phone")).thenReturn("12345");

        servlet.doPost(request, response);

        verify(request).setAttribute("step", "phone");
        verify(dispatcher).forward(request, response);
    }

    @Test
    @DisplayName("doPost: step phone, số mới -> chuyển sang step form")
    void doPost_NewPhone_GoesToFormStep() throws ServletException, IOException {
        when(request.getParameter("step")).thenReturn("phone");
        when(request.getParameter("phone")).thenReturn("0912345678");
        when(mockRegistrationService.checkPhone("0912345678"))
                .thenReturn(new RegistrationService.PhoneCheckResult(RegistrationService.PhoneOutcome.NEW_PHONE, null));

        servlet.doPost(request, response);

        verify(request).setAttribute("step", "form");
        verify(request).setAttribute("phone", "0912345678");
        verify(request).setAttribute("registerMode", "new");
        verify(dispatcher).forward(request, response);
    }

    @Test
    @DisplayName("doPost: step phone, số đã có tài khoản -> chuyển sang blocked-account")
    void doPost_PhoneHasAccount_GoesToBlockedAccount() throws ServletException, IOException {
        when(request.getParameter("step")).thenReturn("phone");
        when(request.getParameter("phone")).thenReturn("0912345678");
        when(mockRegistrationService.checkPhone("0912345678"))
                .thenReturn(new RegistrationService.PhoneCheckResult(RegistrationService.PhoneOutcome.HAS_ACCOUNT, null));

        servlet.doPost(request, response);

        verify(request).setAttribute("step", "blocked-account");
        verify(request).setAttribute("blockedMessage", Messages.MSG51_PHONE_HAS_ACCOUNT);
        verify(dispatcher).forward(request, response);
    }
}

