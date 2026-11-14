package com.gms.servlet.receptionist;

import com.gms.constant.Messages;
import com.gms.constant.SessionKey;
import com.gms.dto.ServiceResult;
import com.gms.entity.EquipmentItem;
import com.gms.entity.Facility;
import com.gms.service.MaintenanceRequestService;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MaintenanceReportServletTest {

    @Mock
    private MaintenanceRequestService maintenanceRequestService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private HttpSession session;

    @Mock
    private RequestDispatcher requestDispatcher;

    private MaintenanceReportServlet servlet;

    @BeforeEach
    void setUp() {
        servlet = new MaintenanceReportServlet(maintenanceRequestService);
        lenient().when(request.getSession(false)).thenReturn(session);
        lenient().when(request.getSession()).thenReturn(session);
        lenient().when(session.getAttribute(SessionKey.ACCOUNT_ID)).thenReturn(2);
        lenient().when(session.getAttribute(SessionKey.CSRF_TOKEN)).thenReturn("csrf-ok");
    }

    @Test
    void doPost_multipartExceedsMaxFileSizeDuringParameterRead_setsToastAndRedirects() throws Exception {
        String expectedMessage = String.format(Messages.MSG20_INVALID_IMAGE, "5 MB");
        when(request.getParameter("csrfToken")).thenThrow(new IllegalStateException("SizeLimitExceededException"));
        when(request.getContentType()).thenReturn("multipart/form-data; boundary=---abc");
        when(request.getContextPath()).thenReturn("/gms");

        servlet.doPost(request, response);

        verify(session).setAttribute(
                eq(SessionKey.FLASH_TOAST),
                argThat(toast -> toast instanceof Map<?, ?> map
                        && "danger".equals(map.get("type"))
                        && expectedMessage.equals(map.get("message")))
        );
        verify(response).sendRedirect("/gms/receptionist/facilities");
    }

    @Test
    void doPost_imagePartExceedsMaxFileSize_forwardsStep2WithErrorAndPreservedFields() throws Exception {
        String expectedMessage = String.format(Messages.MSG20_INVALID_IMAGE, "5 MB");
        EquipmentItem item = new EquipmentItem(10, 3, "TM-01", EquipmentItem.Status.AVAILABLE);
        Facility facility = new Facility(3, "May chay bo", Facility.Category.CARDIO, "Tang 1", 1, Facility.Status.AVAILABLE);

        when(request.getParameter("csrfToken")).thenReturn("csrf-ok");
        lenient().when(request.getParameter("equipmentItemIds")).thenReturn("10");
        lenient().when(request.getParameterValues("equipmentItemIds")).thenReturn(new String[]{"10"});
        when(request.getParameter("incidentType")).thenReturn("Mechanical");
        when(request.getParameter("priority")).thenReturn("Medium");
        when(request.getParameter("description")).thenReturn("May chay keu to bat thuong");
        when(request.getParameter("affectedItemsNote")).thenReturn("Can kiem tra motor");
        when(request.getParameter("warningTagPlaced")).thenReturn("on");
        when(request.getPart("imageFile")).thenThrow(new IllegalStateException("FileSizeLimitExceededException"));

        when(maintenanceRequestService.getSelectableEquipmentItems()).thenReturn(ServiceResult.ok(List.of(item)));
        when(maintenanceRequestService.getFacilityById(3)).thenReturn(ServiceResult.ok(facility));
        when(request.getRequestDispatcher("/WEB-INF/jsp/receptionist/maintenance-report-step2.jsp"))
                .thenReturn(requestDispatcher);

        servlet.doPost(request, response);

        verify(request).setAttribute("formError", expectedMessage);
        verify(request).setAttribute("description", "May chay keu to bat thuong");
        verify(requestDispatcher).forward(request, response);
    }

    @Test
    void doPost_nonMultipartIllegalStateException_rethrows() {
        when(request.getParameter("csrfToken")).thenThrow(new IllegalStateException("unexpected state"));
        when(request.getContentType()).thenReturn("application/x-www-form-urlencoded");

        assertThrows(IllegalStateException.class, () -> servlet.doPost(request, response));
    }
}
