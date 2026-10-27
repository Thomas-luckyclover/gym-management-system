package com.gms.service;

import com.gms.dao.AccountDAO;
import com.gms.dao.EmployeeDAO;
import com.gms.dao.LeaveRequestDAO;
import com.gms.dto.LeaveRequestFilter;
import com.gms.dto.LeaveRequestRow;
import com.gms.entity.LeaveRequest;
import com.gms.util.DateTimeUtil;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Các tab trạng thái phân trang theo đơn, không phụ thuộc số nhân viên hoặc trang nhóm. */
class LeaveRequestPageTest {
    private final LeaveRequestDAO dao = mock(LeaveRequestDAO.class);
    private LeaveRequestService service;
    private LeaveRequestFilter filter;

    @BeforeEach
    void setUp() {
        service = new LeaveRequestService(dao, mock(AccountDAO.class), mock(EmployeeDAO.class),
                mock(SystemConfigService.class), mock(AuditLogService.class), mock(NotificationService.class));
        filter = service.createManagerFilter("", "Approved", null, null, null, null);
    }

    @Test
    void requestsOfTheSameEmployeeCanOccupyDifferentPages() throws Exception {
        when(dao.countRequests(filter)).thenReturn(2);
        when(dao.findRequestPage(filter, 1, 1, null)).thenReturn(List.of(request(102)));
        var result = service.getRequestPage(filter, null, 2, 1);
        assertTrue(result.isSuccess());
        var page = result.getData();
        assertEquals(2, page.getTotalItems());
        assertEquals(2, page.getTotalPages());
        assertEquals(102, page.getItems().get(0).getLeaveRequestId());
        assertTrue(page.isHasPrevious());
        verify(dao, never()).findEmployeePage(any(), anyInt(), anyInt());
    }

    @Test
    void pageBeyondTheEndUsesTheLastRequestPage() throws Exception {
        when(dao.countRequests(filter)).thenReturn(3);
        when(dao.findRequestPage(filter, 2, 2, null)).thenReturn(List.of(request(103)));
        var page = service.getRequestPage(filter, null, 99, 2).getData();
        assertEquals(2, page.getPageNumber());
        assertEquals(103, page.getItems().get(0).getLeaveRequestId());
    }

    @Test
    void processedRequestsDoNotRunPendingApprovalChecks() throws Exception {
        when(dao.countRequests(filter)).thenReturn(1);
        when(dao.findRequestPage(filter, 0, 10, null)).thenReturn(List.of(request(101)));
        var page = service.getRequestPage(filter, null, 1, 10).getData();
        assertNull(page.getItems().get(0).getCheckSummary());
        assertFalse(page.getItems().get(0).isNearLeaveDate());
        verify(dao, never()).findWorkingPosition(any(), anyInt());
    }

    @Test
    void noMatchingRequestsReturnsAnEmptyPage() throws Exception {
        when(dao.findRequestPage(filter, 0, 10, null)).thenReturn(List.of());
        var result = service.getRequestPage(filter, null, 1, 10);
        assertTrue(result.isSuccess());
        assertTrue(result.getData().isEmpty());
        assertEquals(0, result.getData().getTotalItems());
    }

    @Test
    void reversedDateRangeDoesNotQueryRequests() {
        filter = service.createManagerFilter("", "Pending", null, null, "2026-10-25", "2026-10-23");
        assertFalse(service.getRequestPage(filter, null, 1, 10).isSuccess());
        verifyNoInteractions(dao);
    }

    @Test
    void databaseFailureIsReportedAsAnError() throws Exception {
        when(dao.countRequests(filter)).thenThrow(new SQLException("request query failed"));
        assertFalse(service.getRequestPage(filter, null, 1, 10).isSuccess());
    }

    private LeaveRequestRow request(int requestId) {
        LeaveRequest request = new LeaveRequest();
        request.setLeaveRequestId(requestId);
        request.setEmployeeId(50);
        request.setStatus(LeaveRequest.Status.APPROVED);
        request.setLeaveType(LeaveRequest.LeaveType.SICK);
        request.setShiftType(LeaveRequest.ShiftType.MORNING);
        request.setFromDate(DateTimeUtil.today());
        request.setToDate(request.getFromDate());
        request.setDays(new BigDecimal("0.5"));
        return new LeaveRequestRow(request, "NV-0050", "Nhân viên test", "PT", null);
    }
}
