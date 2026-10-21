package com.gms.service;

import com.gms.constant.Role;
import com.gms.dao.AccountDAO;
import com.gms.dao.EmployeeDAO;
import com.gms.dao.LeaveRequestDAO;
import com.gms.dao.PersonDAO;
import com.gms.dto.LeaveBalance;
import com.gms.entity.Account;
import com.gms.entity.Employee;
import com.gms.entity.LeaveRequest;
import com.gms.util.DBContext;
import com.gms.util.DateTimeUtil;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Kiểm tra điều kiện thật khi POST, gồm ngày thứ hai, quỹ phép và phân công đã thay đổi. */
class LeaveApprovalSafetyTest {
    private final LeaveRequestDAO dao = mock(LeaveRequestDAO.class);
    private final AccountDAO accounts = mock(AccountDAO.class);
    private final EmployeeDAO employees = mock(EmployeeDAO.class);
    private final SystemConfigService configs = mock(SystemConfigService.class);
    private final AuditLogService audit = mock(AuditLogService.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final Connection connection = mock(Connection.class);
    private LeaveRequestService service;
    private LeaveRequest request;
    private LocalDate firstDay;

    @BeforeEach
    void setUp() throws Exception {
        service = new LeaveRequestService(dao, accounts, employees, mock(PersonDAO.class), configs, audit, notifications);
        firstDay = DateTimeUtil.today().plusDays(10);
        request = new LeaveRequest();
        request.setLeaveRequestId(12);
        request.setEmployeeId(50);
        request.setFromDate(firstDay);
        request.setToDate(firstDay.plusDays(1));
        request.setDays(new BigDecimal("2.0"));
        request.setLeaveType(LeaveRequest.LeaveType.UNPAID);
        request.setShiftType(LeaveRequest.ShiftType.FULL_DAY);
        request.setStatus(LeaveRequest.Status.PENDING);
        when(configs.getInt(SystemConfigService.MIN_PT_PER_SHIFT)).thenReturn(2);
        when(configs.getInt(SystemConfigService.MIN_RECEPTIONIST_PER_SHIFT)).thenReturn(1);
        when(dao.countOnDutyAfterApproval(any(Connection.class), any(LocalDate.class), anyString(), anyString(), anyInt()))
                .thenReturn(3);
    }

    @Test
    void checksStaffingOnTheSecondDay() throws Exception {
        when(dao.countOnDutyAfterApproval(connection, firstDay.plusDays(1), "FullDay", "PT", 12)).thenReturn(1);
        var result = service.validateApproval(connection, request, "PT");
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains(firstDay.plusDays(1).toString()));
    }

    @Test
    void blocksSessionsOnTheLastDayOfTheRange() throws Exception {
        when(dao.countScheduledSessions(connection, 50, firstDay.plusDays(1), "FullDay")).thenReturn(2);
        var result = service.validateApproval(connection, request, "PT");
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("2"));
        assertTrue(result.getMessage().contains(DateTimeUtil.formatDate(firstDay.plusDays(1))));
    }

    @Test
    void rejectsAnnualLeaveBeyondBalanceAtApproval() throws Exception {
        request.setLeaveType(LeaveRequest.LeaveType.ANNUAL);
        when(configs.getInt(SystemConfigService.LEAVE_ANNUAL_QUOTA)).thenReturn(14);
        when(dao.getAnnualBalance(connection, 50, firstDay.getYear(), 14, 12))
                .thenReturn(new LeaveBalance(14, new BigDecimal("13"), BigDecimal.ZERO));
        assertFalse(service.validateApproval(connection, request, "PT").isSuccess());
        verify(dao, never()).countScheduledSessions(any(), anyInt(), any(), anyString());
    }

    @Test
    void exactBalanceCanApproveWithoutReservingTheCurrentRequestTwice() throws Exception {
        request.setLeaveType(LeaveRequest.LeaveType.ANNUAL);
        request.setToDate(firstDay);
        request.setShiftType(LeaveRequest.ShiftType.MORNING);
        request.setDays(new BigDecimal("0.5"));
        when(configs.getInt(SystemConfigService.LEAVE_ANNUAL_QUOTA)).thenReturn(14);
        when(dao.getAnnualBalance(connection, 50, firstDay.getYear(), 14, 12))
                .thenReturn(new LeaveBalance(14, new BigDecimal("13.5"), BigDecimal.ZERO));
        assertTrue(service.validateApproval(connection, request, "PT").isSuccess());
    }

    @Test
    void missingQuotaBlocksAnnualLeaveWithAnActionableMessage() throws Exception {
        request.setLeaveType(LeaveRequest.LeaveType.ANNUAL);
        when(configs.getInt(SystemConfigService.LEAVE_ANNUAL_QUOTA)).thenThrow(new IllegalStateException("missing"));
        var result = service.validateApproval(connection, request, "PT");
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("P-23"));
    }

    @Test
    void sickLeaveDoesNotConsumeAnnualQuota() throws Exception {
        request.setLeaveType(LeaveRequest.LeaveType.SICK);
        assertTrue(service.validateApproval(connection, request, "Receptionist").isSuccess());
        verify(dao, never()).getAnnualBalance(any(), anyInt(), anyInt(), anyInt(), anyInt());
    }

    @Test
    void invalidStoredDaysCannotBypassQuota() throws Exception {
        request.setDays(new BigDecimal("0.5"));
        assertFalse(service.validateApproval(connection, request, "PT").isSuccess());
    }

    @Test
    void multiDayHalfShiftIsRejected() throws Exception {
        request.setShiftType(LeaveRequest.ShiftType.MORNING);
        assertFalse(service.validateApproval(connection, request, "PT").isSuccess());
    }

    private void stubManagerAndCurrentRequest() throws Exception {
        when(accounts.findById(10)).thenReturn(new Account(500, null, Account.Status.ACTIVE));
        Employee manager = mock(Employee.class);
        when(manager.getEmployeeId()).thenReturn(7);
        when(manager.getPosition()).thenReturn(Role.MANAGER);
        when(manager.getStatus()).thenReturn(Employee.Status.WORKING);
        when(employees.findByPersonId(500)).thenReturn(manager);
        when(dao.findWorkingPosition(connection, 7)).thenReturn("Manager");
        when(dao.findWorkingPosition(connection, 50)).thenReturn("PT");
        when(dao.findReviewDetail(12)).thenReturn(new LeaveRequestDAO.LeaveReviewDetail(
                request, "NV-0050", "PT test", "PT", null, null));
        when(dao.findById(connection, 12)).thenReturn(request);
    }

    @Test
    void changedAssigneeIsRejectedAfterTransactionLock() throws Exception {
        stubManagerAndCurrentRequest();
        request.setAssignedToEmployeeId(9);
        try (MockedStatic<DBContext> database = mockStatic(DBContext.class)) {
            database.when(DBContext::getConnection).thenReturn(connection);
            var result = service.approveRequest(10, 12);
            assertFalse(result.isSuccess());
            verify(connection).rollback();
            verify(dao, never()).approve(any(), anyInt(), anyInt());
            var order = inOrder(dao);
            order.verify(dao).lockDecisionScope(connection);
            order.verify(dao).findById(connection, 12);
        }
    }

    @Test
    void staleTabCannotRejectAnAlreadyApprovedRequest() throws Exception {
        stubManagerAndCurrentRequest();
        LeaveRequest approved = new LeaveRequest();
        approved.setStatus(LeaveRequest.Status.APPROVED);
        when(dao.findById(connection, 12)).thenReturn(approved);
        try (MockedStatic<DBContext> database = mockStatic(DBContext.class)) {
            database.when(DBContext::getConnection).thenReturn(connection);
            assertFalse(service.rejectRequest(10, 12, "Lý do hợp lệ").isSuccess());
            verify(connection).rollback();
            verify(dao, never()).reject(any(), anyInt(), anyInt(), anyString());
        }
    }

    @Test
    void approvalAndNotificationCommitExactlyOnce() throws Exception {
        stubManagerAndCurrentRequest();
        when(dao.approve(connection, 12, 7)).thenReturn(true);
        try (MockedStatic<DBContext> database = mockStatic(DBContext.class)) {
            database.when(DBContext::getConnection).thenReturn(connection);
            assertTrue(service.approveRequest(10, 12).isSuccess());
            verify(connection).commit();
            verify(notifications).notifyEmployeeInTransaction(eq(connection), eq(50), anyString(), anyString());
            request.setStatus(LeaveRequest.Status.APPROVED);
            assertFalse(service.approveRequest(10, 12).isSuccess());
            verify(dao, times(1)).approve(connection, 12, 7);
            verify(connection, times(1)).commit();
        }
    }

    @Test
    void notificationFailureRollsBackApproval() throws Exception {
        stubManagerAndCurrentRequest();
        when(dao.approve(connection, 12, 7)).thenReturn(true);
        doThrow(new SQLException("notification failed")).when(notifications)
                .notifyEmployeeInTransaction(eq(connection), eq(50), anyString(), anyString());
        try (MockedStatic<DBContext> database = mockStatic(DBContext.class)) {
            database.when(DBContext::getConnection).thenReturn(connection);
            assertFalse(service.approveRequest(10, 12).isSuccess());
            verify(connection).rollback();
            verify(connection, never()).commit();
        }
    }

    @Test
    void manualApprovalPostCannotSkipAnnualBalanceCheck() throws Exception {
        stubManagerAndCurrentRequest();
        request.setLeaveType(LeaveRequest.LeaveType.ANNUAL);
        when(configs.getInt(SystemConfigService.LEAVE_ANNUAL_QUOTA)).thenReturn(14);
        when(dao.getAnnualBalance(connection, 50, firstDay.getYear(), 14, 12))
                .thenReturn(new LeaveBalance(14, new BigDecimal("14"), BigDecimal.ZERO));
        try (MockedStatic<DBContext> database = mockStatic(DBContext.class)) {
            database.when(DBContext::getConnection).thenReturn(connection);
            assertFalse(service.approveRequest(10, 12).isSuccess());
            verify(dao, never()).approve(any(), anyInt(), anyInt());
            verify(connection).rollback();
            verifyNoInteractions(audit, notifications);
        }
    }

    @Test
    void submissionRejectsReversedDatesBeforeDatabaseAccess() {
        var result = service.submitLeaveRequest(10, LeaveRequest.LeaveType.ANNUAL,
                firstDay, firstDay.minusDays(1), LeaveRequest.ShiftType.FULL_DAY, "Lý do hợp lệ");
        assertFalse(result.isSuccess());
        assertTrue(result.getFieldErrors().containsKey("toDate"));
        verifyNoInteractions(accounts);
    }

    @Test
    void submissionRejectsHalfShiftAcrossMultipleDays() {
        var result = service.submitLeaveRequest(10, LeaveRequest.LeaveType.ANNUAL,
                firstDay, firstDay.plusDays(1), LeaveRequest.ShiftType.MORNING, "Lý do hợp lệ");
        assertFalse(result.isSuccess());
        assertTrue(result.getFieldErrors().containsKey("shiftType"));
        verifyNoInteractions(accounts);
    }

    @Test
    void submissionRejectsCrossYearLeave() {
        LocalDate december = LocalDate.of(DateTimeUtil.today().getYear() + 1, 12, 31);
        var result = service.submitLeaveRequest(10, LeaveRequest.LeaveType.ANNUAL,
                december, december.plusDays(1), LeaveRequest.ShiftType.FULL_DAY, "Lý do hợp lệ");
        assertFalse(result.isSuccess());
        assertTrue(result.getFieldErrors().containsKey("toDate"));
    }

    @Test
    void submissionRejectsMoreThanThirtyOneDays() {
        LocalDate january = LocalDate.of(DateTimeUtil.today().getYear() + 1, 1, 1);
        var result = service.submitLeaveRequest(10, LeaveRequest.LeaveType.ANNUAL,
                january, january.plusDays(31), LeaveRequest.ShiftType.FULL_DAY, "Lý do hợp lệ");
        assertFalse(result.isSuccess());
        assertTrue(result.getFieldErrors().containsKey("toDate"));
    }

    @Test
    void confirmationRequiresEveryApplicableCheckToPass() {
        var detail = new LeaveRequestDAO.LeaveReviewDetail(request, "NV-0050", "PT test", "PT", null, null);
        detail.setCanAct(true);
        var quota = new com.gms.dto.ReviewCheck("ANNUAL_QUOTA", "Quỹ phép", true, true, "Đạt", null);
        var sessions = new com.gms.dto.ReviewCheck("SESSIONS", "Lịch PT", true, false, "Trùng lịch", null);
        detail.setChecks(java.util.List.of(quota, sessions));
        assertFalse(detail.isCanApprove());
        sessions.setOk(true);
        assertTrue(detail.isCanApprove());
        detail.setCanAct(false);
        assertFalse(detail.isCanApprove());
    }

    @Test
    void listDoesNotMarkAResignedEmployeeAsPassing() throws Exception {
        when(configs.getString(SystemConfigService.LEAVE_APPROVED_DEADLINE)).thenReturn("20:00");
        when(dao.countByFilter(any())).thenReturn(1);
        when(dao.findManagerPage(any(), eq(0), eq(20), isNull())).thenReturn(java.util.List.of(
                new LeaveRequestDAO.LeaveRequestRow(request, "NV-0050", "PT test", "PT", null, null)));
        when(dao.findWorkingPosition(connection, 50)).thenReturn(null);
        try (MockedStatic<DBContext> database = mockStatic(DBContext.class)) {
            database.when(DBContext::getConnection).thenReturn(connection);
            var result = service.getManagerPage("", "Pending", 1, 20);
            assertTrue(result.isSuccess());
            var item = result.getData().getItems().get(0);
            assertEquals("Cần xử lý", item.getCheckSummary());
            assertTrue(item.getCheckMessage().contains("không còn"));
        }
    }

    @Test
    void reviewBlocksConfirmationWhenTheEmployeeHasResigned() throws Exception {
        stubManagerAndCurrentRequest();
        when(configs.getString(SystemConfigService.LEAVE_APPROVED_DEADLINE)).thenReturn("20:00");
        when(dao.findReviewDetail(12, 7)).thenReturn(new LeaveRequestDAO.LeaveReviewDetail(
                request, "NV-0050", "PT test", "PT", null, null));
        when(dao.findWorkingPosition(connection, 50)).thenReturn(null);
        try (MockedStatic<DBContext> database = mockStatic(DBContext.class)) {
            database.when(DBContext::getConnection).thenReturn(connection);
            var result = service.getReviewDetail(12, 10);
            assertTrue(result.isSuccess());
            var staffing = result.getData().getChecks().get(1);
            assertFalse(staffing.isOk());
            assertTrue(staffing.getMessage().contains("đã nghỉ việc"));
            assertFalse(result.getData().isCanApprove());
        }
    }
}
