package com.gms.service;

import com.gms.dao.AccountDAO;
import com.gms.dao.EmployeeDAO;
import com.gms.dao.LeaveRequestDAO;
import com.gms.dto.LeaveEmployeeView;
import com.gms.dto.LeaveRequestFilter;
import com.gms.dto.LeaveRequestRow;
import com.gms.entity.LeaveRequest;
import com.gms.util.DateTimeUtil;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Gom đơn theo đúng nhân viên, giữ người chưa có đơn và phân trang theo hàng cha. */
class LeaveEmployeePageTest {

    private final LeaveRequestDAO dao = mock(LeaveRequestDAO.class);
    private LeaveRequestService service;
    private LeaveRequestFilter filter;

    @BeforeEach
    void setUp() {
        service = new LeaveRequestService(dao, mock(AccountDAO.class), mock(EmployeeDAO.class),
                mock(SystemConfigService.class), mock(AuditLogService.class), mock(NotificationService.class));
        filter = service.createManagerFilter("", "All", null, null, null, null);
    }

    @Test
    void employeesWithoutRequestsStayVisibleAndRequestsBelongToTheRightPerson() throws Exception {
        LeaveEmployeeView first = employee(10, 2);
        LeaveEmployeeView empty = employee(11, 0);
        when(dao.countEmployees(filter)).thenReturn(2);
        when(dao.findEmployeePage(filter, 0, 20)).thenReturn(List.of(first, empty));
        when(dao.findEmployeeRequests(filter, List.of(10, 11), null)).thenReturn(List.of(request(101, 10), request(102, 10)));

        var result = service.getEmployeePage(filter, null, 1, 20);
        assertTrue(result.isSuccess());
        assertEquals(2, result.getData().getTotalItems());
        assertEquals(2, result.getData().getItems().size());
        assertEquals(101, first.getRequests().get(0).getLeaveRequestId());
        assertEquals(102, first.getRequests().get(1).getLeaveRequestId());
        assertTrue(empty.getRequests().isEmpty());
        assertEquals(0, empty.getTotalRequestCount());
    }

    @Test
    void oneEmployeePerPageStillIncludesAllOfThatPersonsRequests() throws Exception {
        LeaveEmployeeView employee = employee(10, 2);
        when(dao.countEmployees(filter)).thenReturn(2);
        when(dao.findEmployeePage(filter, 0, 1)).thenReturn(List.of(employee));
        when(dao.findEmployeeRequests(filter, List.of(10), null)).thenReturn(List.of(request(101, 10), request(102, 10)));
        var page = service.getEmployeePage(filter, null, 1, 1).getData();
        assertEquals(1, page.getItems().size());
        assertEquals(2, page.getItems().get(0).getRequests().size());
        assertEquals(2, page.getTotalPages());
        assertTrue(page.isHasNext());
    }

    @Test
    void requestFiltersDoNotEraseTheEmployeesOverallPendingCount() throws Exception {
        filter = service.createManagerFilter("", "Approved", null, "Sick", null, null);
        LeaveEmployeeView employee = employee(10, 3);
        employee.setPendingCount(2);
        employee.setNextPendingDate(DateTimeUtil.today().plusDays(1));
        when(dao.countEmployees(filter)).thenReturn(1);
        when(dao.findEmployeePage(filter, 0, 20)).thenReturn(List.of(employee));
        when(dao.findEmployeeRequests(filter, List.of(10), null)).thenReturn(List.of());
        var result = service.getEmployeePage(filter, null, 1, 20);
        assertTrue(result.isSuccess());
        assertEquals(1, result.getData().getItems().size());
        assertEquals(2, employee.getPendingCount());
        assertTrue(employee.isNearLeaveDate());
        assertTrue(employee.getRequests().isEmpty());
    }

    @Test
    void pageBeyondTheEndFallsBackToTheLastEmployeePage() throws Exception {
        when(dao.countEmployees(filter)).thenReturn(2);
        when(dao.findEmployeePage(filter, 1, 1)).thenReturn(List.of(employee(11, 0)));
        var page = service.getEmployeePage(filter, null, 99, 1).getData();
        assertEquals(2, page.getPageNumber());
        verify(dao).findEmployeePage(filter, 1, 1);
    }

    @Test
    void emptyEmployeePageDoesNotQueryRequests() throws Exception {
        when(dao.findEmployeePage(filter, 0, 20)).thenReturn(List.of());
        var result = service.getEmployeePage(filter, null, 1, 20);
        assertTrue(result.isSuccess());
        assertTrue(result.getData().isEmpty());
        verify(dao, never()).findEmployeeRequests(any(), anyList(), any());
    }

    @Test
    void databaseFailureIsAnErrorRatherThanAnEmptyEmployeeList() throws Exception {
        when(dao.countEmployees(filter)).thenThrow(new SQLException("database unavailable"));
        assertFalse(service.getEmployeePage(filter, null, 1, 20).isSuccess());
    }

    @Test
    void reversedDatesShowAnErrorWithoutQueryingTheDatabase() {
        filter = service.createManagerFilter("", "All", null, null, "2026-10-25", "2026-10-23");
        var pageResult = service.getEmployeePage(filter, null, 1, 20);
        var countResult = service.getStatusCounts(filter);
        assertFalse(pageResult.isSuccess());
        assertEquals("Từ ngày phải trước hoặc trùng Đến ngày.", pageResult.getMessage());
        assertFalse(countResult.isSuccess());
        verifyNoInteractions(dao);
    }

    @Test
    void sameDaySubmissionRangeCanShowFutureLeave() throws Exception {
        filter = service.createManagerFilter("", "All", null, null, "2026-10-24", "2026-10-24");
        LeaveEmployeeView employee = employee(10, 1);
        LeaveRequestRow row = request(101, 10);
        row.getLeaveRequest().setSubmittedAt(LocalDate.of(2026, 10, 24).atTime(10, 30));
        row.getLeaveRequest().setFromDate(LocalDate.of(2026, 11, 1));
        row.getLeaveRequest().setToDate(LocalDate.of(2026, 11, 2));
        row.getLeaveRequest().setShiftType(LeaveRequest.ShiftType.FULL_DAY);
        row.getLeaveRequest().setDays(new BigDecimal("2"));
        when(dao.countEmployees(filter)).thenReturn(1);
        when(dao.findEmployeePage(filter, 0, 20)).thenReturn(List.of(employee));
        when(dao.findEmployeeRequests(filter, List.of(10), null)).thenReturn(List.of(row));

        var result = service.getEmployeePage(filter, null, 1, 20);
        assertTrue(result.isSuccess());
        assertEquals(101, result.getData().getItems().get(0).getRequests().get(0).getLeaveRequestId());
    }

    private LeaveEmployeeView employee(int employeeId, int totalRequests) {
        LeaveEmployeeView employee = new LeaveEmployeeView(employeeId, "NV-" + employeeId,
                "Nhân viên " + employeeId, "PT", null, "Working");
        employee.setTotalRequestCount(totalRequests);
        return employee;
    }

    private LeaveRequestRow request(int requestId, int employeeId) {
        LeaveRequest request = new LeaveRequest();
        request.setLeaveRequestId(requestId);
        request.setEmployeeId(employeeId);
        request.setStatus(LeaveRequest.Status.APPROVED);
        request.setLeaveType(LeaveRequest.LeaveType.SICK);
        request.setShiftType(LeaveRequest.ShiftType.MORNING);
        request.setFromDate(DateTimeUtil.today().plusDays(2));
        request.setToDate(request.getFromDate());
        request.setDays(new BigDecimal("0.5"));
        return new LeaveRequestRow(request, "NV-" + employeeId, "Nhân viên " + employeeId, "PT", null, "Quản lý");
    }
}
