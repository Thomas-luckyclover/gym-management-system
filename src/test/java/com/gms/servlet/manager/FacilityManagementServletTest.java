package com.gms.servlet.manager;

import com.gms.constant.Messages;
import com.gms.constant.SessionKey;
import com.gms.service.FacilityService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FacilityManagementServletTest {

    @Mock
    private FacilityService facilityService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private HttpSession session;

    private FacilityManagementServlet servlet;

    @BeforeEach
    void setUp() {
        servlet = new FacilityManagementServlet(facilityService);
        lenient().when(request.getSession(false)).thenReturn(session);
        lenient().when(request.getSession()).thenReturn(session);
        lenient().when(session.getAttribute(SessionKey.ACCOUNT_ID)).thenReturn(1);
        lenient().when(session.getAttribute(SessionKey.CSRF_TOKEN)).thenReturn("csrf-ok");
    }

    @Test
    void doPost_multipartExceedsMaxFileSizeDuringParameterRead_setsToastAndRedirects() throws Exception {
        String expectedMessage = String.format(Messages.MSG20_INVALID_IMAGE, "5 MB");
        when(request.getParameter("csrfToken")).thenThrow(new IllegalStateException("FileSizeLimitExceededException"));
        when(request.getContentType()).thenReturn("multipart/form-data; boundary=---123");
        when(request.getContextPath()).thenReturn("/gms");

        servlet.doPost(request, response);

        verify(session).setAttribute(
                eq(SessionKey.FLASH_TOAST),
                argThat(toast -> toast instanceof Map<?, ?> map
                        && "danger".equals(map.get("type"))
                        && expectedMessage.equals(map.get("message")))
        );
        verify(response).sendRedirect("/gms/manager/facilities");
    }

    @Test
    void doPost_saveFacilityPartExceedsMaxFileSize_preservesSelectedIdAndRedirects() throws Exception {
        String expectedMessage = String.format(Messages.MSG20_INVALID_IMAGE, "5 MB");
        when(request.getParameter("csrfToken")).thenReturn("csrf-ok");
        when(request.getParameter("action")).thenReturn("saveFacility");
        when(request.getParameter("facilityId")).thenReturn("5");
        when(request.getPart("imageFile")).thenThrow(new IllegalStateException("FileSizeLimitExceededException"));
        when(request.getContextPath()).thenReturn("/gms");

        servlet.doPost(request, response);

        verify(session).setAttribute(
                eq(SessionKey.FLASH_TOAST),
                argThat(toast -> toast instanceof Map<?, ?> map
                        && "danger".equals(map.get("type"))
                        && expectedMessage.equals(map.get("message")))
        );
        verify(response).sendRedirect("/gms/manager/facilities?selectedId=5");
    }

    @Test
    void doPost_nonMultipartIllegalStateException_rethrows() {
        when(request.getParameter("csrfToken")).thenThrow(new IllegalStateException("unexpected state"));
        when(request.getContentType()).thenReturn("application/x-www-form-urlencoded");

        assertThrows(IllegalStateException.class, () -> servlet.doPost(request, response));
    }
}

