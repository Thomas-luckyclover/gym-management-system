package com.gms.servlet.manager;

import com.gms.constant.SessionKey;
import com.gms.dto.LeaveEmployeeView;
import com.gms.dto.LeaveRequestFilter;
import com.gms.dto.LeaveRequestItemView;
import com.gms.dto.PageResult;
import com.gms.dto.ServiceResult;
import com.gms.service.LeaveRequestService;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Mở danh sách không tự lọc hôm nay; ngày do người dùng chọn vẫn được áp dụng. */
class LeaveRequestListServletTest {
    private final LeaveRequestService service = mock(LeaveRequestService.class, CALLS_REAL_METHODS);
    private final HttpServletRequest request = mock(HttpServletRequest.class);
    private final HttpServletResponse response = mock(HttpServletResponse.class);
    private LeaveRequestListServlet servlet;

    @BeforeEach
    void setUp() {
        servlet = new LeaveRequestListServlet(service);
        HttpSession session = mock(HttpSession.class);
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute(SessionKey.ACCOUNT_ID)).thenReturn(10);
        when(session.getAttribute(SessionKey.CSRF_TOKEN)).thenReturn("test-csrf-token");
        doReturn(ServiceResult.ok(new PageResult<LeaveRequestItemView>(List.of(), 1, 10, 0)))
                .when(service).getRequestPage(any(), eq(10), eq(1), eq(10));
        doReturn(ServiceResult.ok(new PageResult<LeaveEmployeeView>(List.of(), 1, 10, 0)))
                .when(service).getEmployeePage(any(), eq(10), eq(1), eq(10));
        doReturn(ServiceResult.ok(Map.of())).when(service).getStatusCounts(any());
        RequestDispatcher dispatcher = mock(RequestDispatcher.class);
        when(request.getRequestDispatcher("/WEB-INF/jsp/manager/leave-request-list.jsp"))
                .thenReturn(dispatcher);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"Pending", "Approved", "Rejected", "All"})
    void openingAnyTabDoesNotExcludeEarlierSubmissionDays(String status) throws Exception {
        when(request.getParameter("status")).thenReturn(status);
        servlet.doGet(request, response);
        ArgumentCaptor<LeaveRequestFilter> captor = ArgumentCaptor.forClass(LeaveRequestFilter.class);
        verify(service).getStatusCounts(captor.capture());
        assertNull(captor.getValue().getFromDate());
        assertNull(captor.getValue().getToDate());
        verify(request).setAttribute("fromDateFilter", null);
        verify(request).setAttribute("toDateFilter", null);
        if (status == null || status.isBlank()) {
            verify(request).setAttribute("statusFilter", "Pending");
        }
    }

    @Test
    void explicitlySelectedSubmissionRangeIsKept() throws Exception {
        when(request.getParameter("fromDate")).thenReturn("2026-10-22");
        when(request.getParameter("toDate")).thenReturn("2026-10-22");
        servlet.doGet(request, response);
        ArgumentCaptor<LeaveRequestFilter> captor = ArgumentCaptor.forClass(LeaveRequestFilter.class);
        verify(service).getRequestPage(captor.capture(), eq(10), eq(1), eq(10));
        assertEquals(LocalDate.of(2026, 10, 22), captor.getValue().getFromDate());
        assertEquals(LocalDate.of(2026, 10, 22), captor.getValue().getToDate());
    }

    @Test
    void clearingBothDatesRestoresAllSubmissionDays() throws Exception {
        when(request.getParameter("fromDate")).thenReturn("");
        when(request.getParameter("toDate")).thenReturn("");
        servlet.doGet(request, response);
        ArgumentCaptor<LeaveRequestFilter> captor = ArgumentCaptor.forClass(LeaveRequestFilter.class);
        verify(service).getRequestPage(captor.capture(), eq(10), eq(1), eq(10));
        assertNull(captor.getValue().getFromDate());
        assertNull(captor.getValue().getToDate());
    }
}
