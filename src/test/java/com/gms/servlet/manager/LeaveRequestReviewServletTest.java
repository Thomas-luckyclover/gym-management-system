package com.gms.servlet.manager;

import com.gms.constant.SessionKey;
import com.gms.dto.LeaveReviewDetail;
import com.gms.dto.LeaveBulkApprovalResult;
import com.gms.dto.ServiceResult;
import com.gms.entity.LeaveRequest;
import com.gms.service.LeaveRequestService;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.mockito.Mockito.*;

/** Nút trong danh sách vẫn đi qua kiểm tra session/CSRF và Service của màn chi tiết. */
class LeaveRequestReviewServletTest {
    private final LeaveRequestService service = mock(LeaveRequestService.class);
    private final HttpServletRequest request = mock(HttpServletRequest.class);
    private final HttpServletResponse response = mock(HttpServletResponse.class);
    private final HttpSession session = mock(HttpSession.class);
    private LeaveRequestReviewServlet servlet;

    @BeforeEach
    void setUp() {
        servlet = new LeaveRequestReviewServlet(service);
        when(request.getSession(false)).thenReturn(session);
        when(request.getSession()).thenReturn(session);
        when(request.getSession(true)).thenReturn(session);
        when(session.getAttribute(SessionKey.ACCOUNT_ID)).thenReturn(10);
        when(session.getAttribute(SessionKey.CSRF_TOKEN)).thenReturn("test-csrf-token");
        when(request.getParameter("csrfToken")).thenReturn("test-csrf-token");
        when(request.getParameter("leaveRequestId")).thenReturn("12");
        when(request.getParameter("action")).thenReturn("approve");
        when(request.getContextPath()).thenReturn("/gms");
    }

    @Test
    void approvalReturnsToTheCurrentListFiltersAndPage() throws Exception {
        String returnUrl = "/manager/leave-requests?status=Pending&leaveType=Sick&page=2";
        when(request.getParameter("returnUrl")).thenReturn(returnUrl);
        when(service.approveRequest(10, 12)).thenReturn(ServiceResult.ok(null));
        servlet.doPost(request, response);
        verify(service).approveRequest(10, 12);
        verify(response).sendRedirect("/gms" + returnUrl);
    }

    @Test
    void rejectionSendsTheEnteredReasonToTheExistingService() throws Exception {
        when(request.getParameter("action")).thenReturn("reject");
        when(request.getParameter("rejectReason")).thenReturn("  Không đủ nhân sự trực ca  ");
        when(service.rejectRequest(10, 12, "Không đủ nhân sự trực ca")).thenReturn(ServiceResult.ok(null));
        servlet.doPost(request, response);
        verify(service).rejectRequest(10, 12, "Không đủ nhân sự trực ca");
        verify(response).sendRedirect("/gms/manager/leave-requests");
    }

    @Test
    void invalidCsrfCannotReachTheDecisionService() throws Exception {
        when(request.getParameter("csrfToken")).thenReturn("wrong-token");
        servlet.doPost(request, response);
        verifyNoInteractions(service);
        verify(response).sendRedirect("/gms/manager/leave-requests");
    }

    @Test
    void missingLoginCannotReachTheDecisionService() throws Exception {
        when(session.getAttribute(SessionKey.ACCOUNT_ID)).thenReturn(null);
        servlet.doPost(request, response);
        verifyNoInteractions(service);
        verify(response).sendRedirect("/gms/login");
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://example.com", "//example.com", "/manager/leave-requests/../other"})
    void returnUrlCannotRedirectOutsideTheList(String returnUrl) throws Exception {
        when(request.getParameter("returnUrl")).thenReturn(returnUrl);
        when(service.approveRequest(10, 12)).thenReturn(ServiceResult.ok(null));
        servlet.doPost(request, response);
        verify(response).sendRedirect("/gms/manager/leave-requests");
    }

    @Test
    void failedDecisionShowsTheDetailAndKeepsTheError() throws Exception {
        when(service.approveRequest(10, 12)).thenReturn(ServiceResult.fail("Ca trực không đủ người."));
        LeaveReviewDetail detail = new LeaveReviewDetail(new LeaveRequest(), "NV-0050", "Nhân viên test", "PT", null, null);
        when(service.getReviewDetail(12, 10)).thenReturn(ServiceResult.ok(detail));
        when(service.getEligibleReviewers(10)).thenReturn(ServiceResult.ok(List.of()));
        RequestDispatcher dispatcher = mock(RequestDispatcher.class);
        when(request.getRequestDispatcher("/WEB-INF/jsp/manager/leave-request-review.jsp")).thenReturn(dispatcher);
        servlet.doPost(request, response);
        verify(request).setAttribute("formMessage", "Ca trực không đủ người.");
        verify(dispatcher).forward(request, response);
        verify(response, never()).sendRedirect(anyString());
    }

    @Test
    void bulkApprovalKeepsResultAcrossRedirectAndPreservesFilters() throws Exception {
        when(request.getParameter("action")).thenReturn("approveMany");
        when(request.getParameterValues("leaveRequestIds")).thenReturn(new String[]{"12", "13"});
        String returnUrl = "/manager/leave-requests?status=Pending&position=PT&page=2";
        when(request.getParameter("returnUrl")).thenReturn(returnUrl);
        var result = new LeaveBulkApprovalResult(1, Map.of(13, "Không đủ nhân sự."));
        when(service.approveRequests(10, List.of(12, 13))).thenReturn(ServiceResult.ok(result));
        servlet.doPost(request, response);
        verify(session).setAttribute(SessionKey.LEAVE_BULK_RESULT, result);
        verify(response).sendRedirect("/gms" + returnUrl);
        verify(service, never()).approveRequest(anyInt(), anyInt());
    }

    @Test
    void malformedBulkIdStopsBeforeTheService() throws Exception {
        when(request.getParameter("action")).thenReturn("approveMany");
        when(request.getParameterValues("leaveRequestIds")).thenReturn(new String[]{"12", "wrong"});
        servlet.doPost(request, response);
        verifyNoInteractions(service);
        verify(response).sendRedirect("/gms/manager/leave-requests");
    }

    @Test
    void bulkApprovalRequiresCsrfAndLogin() throws Exception {
        when(request.getParameter("action")).thenReturn("approveMany");
        when(request.getParameter("csrfToken")).thenReturn("wrong-token");
        servlet.doPost(request, response);
        when(session.getAttribute(SessionKey.ACCOUNT_ID)).thenReturn(null);
        servlet.doPost(request, response);
        verifyNoInteractions(service);
    }
}
