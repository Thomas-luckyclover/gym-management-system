package com.gms.service;

import com.gms.dao.AccountDAO;
import com.gms.dao.EmployeeDAO;
import com.gms.dao.LeaveRequestDAO;
import com.gms.dto.ServiceResult;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Duyệt nhiều dùng lại kiểm tra từng đơn theo đúng thứ tự, không bỏ qua lỗi hoặc duyệt trùng. */
class LeaveBulkApprovalTest {
    private final LeaveRequestService service = spy(new LeaveRequestService(
            mock(LeaveRequestDAO.class), mock(AccountDAO.class), mock(EmployeeDAO.class),
            mock(SystemConfigService.class), mock(AuditLogService.class), mock(NotificationService.class)));

    @Test
    void checksEachRequestInOrderAndReportsPartialFailures() {
        doReturn(ServiceResult.ok(null)).when(service).approveRequest(10, 12);
        doReturn(ServiceResult.fail("Không đủ nhân sự sau khi duyệt đơn trước.")).when(service).approveRequest(10, 13);
        doReturn(ServiceResult.ok(null)).when(service).approveRequest(10, 14);

        var result = service.approveRequests(10, List.of(12, 13, 14, 12));

        assertTrue(result.isSuccess());
        assertEquals(2, result.getData().getApprovedCount());
        assertEquals("Không đủ nhân sự sau khi duyệt đơn trước.", result.getData().getFailedRequests().get(13));
        var order = inOrder(service);
        order.verify(service).approveRequest(10, 12);
        order.verify(service).approveRequest(10, 13);
        order.verify(service).approveRequest(10, 14);
        verify(service, times(1)).approveRequest(10, 12);
    }

    @Test
    void alreadyProcessedOrForbiddenRequestsRemainFailures() {
        doReturn(ServiceResult.fail("Đơn đã được xử lý.")).when(service).approveRequest(10, 12);
        doReturn(ServiceResult.fail("Bạn không có quyền xử lý đơn này.")).when(service).approveRequest(10, 13);
        var result = service.approveRequests(10, List.of(12, 13)).getData();
        assertEquals(0, result.getApprovedCount());
        assertEquals(2, result.getFailedRequests().size());
    }

    @Test
    void invalidIdAnywhereRejectsTheWholeInputBeforeAnyDecision() {
        assertFalse(service.approveRequests(10, List.of(12, -1)).isSuccess());
        assertFalse(service.approveRequests(10, Arrays.asList(12, null)).isSuccess());
        verify(service, never()).approveRequest(anyInt(), anyInt());
    }

    @Test
    void missingOrOversizedSelectionCannotWrite() {
        assertFalse(service.approveRequests(10, null).isSuccess());
        assertFalse(service.approveRequests(10, List.of()).isSuccess());
        assertFalse(service.approveRequests(10, Collections.nCopies(51, 12)).isSuccess());
        verify(service, never()).approveRequest(anyInt(), anyInt());
    }
}
