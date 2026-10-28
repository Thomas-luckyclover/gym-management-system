package com.gms.service;

import com.gms.dao.AccountDAO;
import com.gms.dao.EmployeeDAO;
import com.gms.dao.LeaveRequestDAO;
import com.gms.dto.LeaveRequestFilter;
import com.gms.dto.LeaveReviewDetail;
import com.gms.dto.LeaveRequestGroupView;
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

    @Test
    void groupedRowIncludesBothPartsEvenWhenOtherPartIsOutsideFilter() throws Exception {
        LeaveRequestRow first = request(101);
        LeaveRequestRow second = request(102);
        String groupId = "11111111-1111-1111-1111-111111111111";
        first.getLeaveRequest().setLeaveGroupId(groupId);
        second.getLeaveRequest().setLeaveGroupId(groupId);
        second.getLeaveRequest().setFromDate(DateTimeUtil.today().plusDays(1));
        second.getLeaveRequest().setToDate(DateTimeUtil.today().plusDays(3));
        when(dao.countRequests(filter)).thenReturn(1);
        when(dao.findRequestPage(filter, 0, 1, null)).thenReturn(List.of(first));
        when(dao.findByLeaveGroup(groupId, 50)).thenReturn(
                List.of(second.getLeaveRequest(), first.getLeaveRequest()));

        var firstDetail = new LeaveReviewDetail(first.getLeaveRequest(), "NV-0050", "Test", "PT", null, null);
        var secondDetail = new LeaveReviewDetail(second.getLeaveRequest(), "NV-0050", "Test", "PT", null, null);
        service = spy(service);
        doReturn(com.gms.dto.ServiceResult.ok(List.of(firstDetail, secondDetail)))
                .when(service).getReviewParts(101, null);
        var page = service.getRequestPage(filter, null, 1, 1).getData();
        assertEquals(1, page.getTotalItems());
        assertEquals(1, page.getItems().size());
        var group = page.getItems().get(0).getLeaveGroup();
        assertEquals(2, group.getRequests().size());
        assertEquals(DateTimeUtil.today(), group.getFromDate());
        assertEquals(DateTimeUtil.today().plusDays(3), group.getToDate());
        assertEquals(new BigDecimal("1.0"), group.getTotalDays());
        assertEquals(List.of(firstDetail, secondDetail), group.getReviewParts());
        assertTrue(group.containsRequest(102));
        assertFalse(group.containsRequest(999));
    }

    @Test
    void expandedGroupReadsBothPartsAndPropagatesReadFailure() {
        var first = request(101).getLeaveRequest();
        var second = request(102).getLeaveRequest();
        first.setLeaveGroupId("11111111-1111-1111-1111-111111111111");
        var firstDetail = new LeaveReviewDetail(first, "NV-0050", "Test", "PT", null, null);
        var secondDetail = new LeaveReviewDetail(second, "NV-0050", "Test", "PT", null, null);
        firstDetail.setLeaveGroup(new LeaveRequestGroupView(List.of(first, second)));
        var reader = spy(service);
        doReturn(com.gms.dto.ServiceResult.ok(firstDetail)).when(reader).getReviewDetail(101, 10);
        doReturn(com.gms.dto.ServiceResult.ok(secondDetail)).when(reader).getReviewDetail(102, 10);
        assertEquals(List.of(firstDetail, secondDetail), reader.getReviewParts(101, 10).getData());
        doReturn(com.gms.dto.ServiceResult.fail("Không đọc được phần còn lại.")).when(reader).getReviewDetail(102, 10);
        assertFalse(reader.getReviewParts(101, 10).isSuccess());
    }

    @Test
    void failedPreloadShowsErrorAndDoesNotExposeUncheckedDecisionButtons() throws Exception {
        var first = request(101);
        var second = request(102);
        String groupId = "11111111-1111-1111-1111-111111111111";
        first.getLeaveRequest().setLeaveGroupId(groupId);
        when(dao.findRequestPage(filter, 0, 10, null)).thenReturn(List.of(first));
        when(dao.findByLeaveGroup(groupId, 50)).thenReturn(List.of(first.getLeaveRequest(), second.getLeaveRequest()));
        service = spy(service);
        doReturn(com.gms.dto.ServiceResult.fail("Không thể kiểm tra đơn."))
                .when(service).getReviewParts(101, null);
        var result = service.getRequestPage(filter, null, 1, 10);
        assertTrue(result.isSuccess());
        var group = result.getData().getItems().get(0).getLeaveGroup();
        assertEquals("Không thể kiểm tra đơn.", group.getReviewError());
        assertTrue(group.getReviewParts().isEmpty());
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
