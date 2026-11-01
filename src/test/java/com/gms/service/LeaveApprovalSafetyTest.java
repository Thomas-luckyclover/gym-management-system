package com.gms.service;

import com.gms.dto.LeaveRequestRow;
import com.gms.dto.LeaveEmployeeView;
import com.gms.dto.LeaveReviewDetailView;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
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
    private StaffLeaveService staffService;
    private LeaveRequest request;
    private LocalDate firstDay;

    @BeforeEach
    void setUp() throws Exception {
        service = new LeaveRequestService(dao, accounts, employees, configs, audit, notifications);
        staffService = new StaffLeaveService(dao, accounts, employees, mock(PersonDAO.class), configs, audit, notifications);
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
        when(dao.countOnDutyAfterApproval(connection, firstDay.plusDays(1), "Afternoon", "PT", 12)).thenReturn(1);
        var result = service.validateApproval(connection, request, "PT");
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains(DateTimeUtil.formatDate(firstDay.plusDays(1))));
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
    }

    @Test
    void exactBalanceCanApproveWithoutReservingTheCurrentRequestTwice() throws Exception {
        request.setLeaveType(LeaveRequest.LeaveType.ANNUAL);
        request.setToDate(firstDay);
        request.setShiftType(LeaveRequest.ShiftType.MORNING);
        request.setDays(BigDecimal.ONE);
        when(configs.getInt(SystemConfigService.LEAVE_ANNUAL_QUOTA)).thenReturn(14);
        when(dao.getAnnualBalance(connection, 50, firstDay.getYear(), 14, 12))
                .thenReturn(new LeaveBalance(14, new BigDecimal("13"), BigDecimal.ZERO));
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

    @ParameterizedTest
    @EnumSource(LeaveRequest.ShiftType.class)
    void everySingleDayShiftUsesOneDayAtApproval(LeaveRequest.ShiftType shift) throws Exception {
        request.setToDate(firstDay);
        request.setShiftType(shift);
        request.setDays(BigDecimal.ONE);
        request.setLeaveType(LeaveRequest.LeaveType.ANNUAL);
        when(configs.getInt(SystemConfigService.LEAVE_ANNUAL_QUOTA)).thenReturn(14);
        when(dao.getAnnualBalance(connection, 50, firstDay.getYear(), 14, 12))
                .thenReturn(new LeaveBalance(14, new BigDecimal("13"), BigDecimal.ZERO));

        assertTrue(service.validateApproval(connection, request, "Receptionist").isSuccess());

        when(dao.getAnnualBalance(connection, 50, firstDay.getYear(), 14, 12))
                .thenReturn(new LeaveBalance(14, new BigDecimal("13.5"), BigDecimal.ZERO));
        assertFalse(service.validateApproval(connection, request, "Receptionist").isSuccess());
    }

    @ParameterizedTest
    @EnumSource(LeaveRequest.ShiftType.class)
    void submissionStoresOneDayForEverySingleDayShift(LeaveRequest.ShiftType shift) throws Exception {
        when(accounts.findById(10)).thenReturn(new Account(500, null, Account.Status.ACTIVE));
        Employee employee = mock(Employee.class);
        when(employee.getEmployeeId()).thenReturn(50);
        when(employee.getPosition()).thenReturn(Role.RECEPTIONIST);
        when(employee.getStatus()).thenReturn(Employee.Status.WORKING);
        when(dao.findWorkingPosition(connection, 50)).thenReturn("Receptionist");
        when(employees.findByPersonId(500)).thenReturn(employee);
        when(configs.getString(SystemConfigService.LEAVE_SUBMIT_DEADLINE)).thenReturn("15:00");
        when(configs.getInt(SystemConfigService.LEAVE_ANNUAL_QUOTA)).thenReturn(14);
        when(dao.getAnnualBalance(connection, 50, firstDay.getYear(), 14))
                .thenReturn(new LeaveBalance(14, BigDecimal.ZERO, BigDecimal.ZERO));
        when(dao.insert(eq(connection), any(LeaveRequest.class))).thenReturn(101);

        try (MockedStatic<DBContext> database = mockStatic(DBContext.class)) {
            database.when(DBContext::getConnection).thenReturn(connection);
            var result = staffService.submitLeaveRequest(10, LeaveRequest.LeaveType.ANNUAL,
                    firstDay, firstDay, shift, "Lý do hợp lệ");

            assertTrue(result.isSuccess());
            ArgumentCaptor<LeaveRequest> savedRequest = ArgumentCaptor.forClass(LeaveRequest.class);
            verify(dao).insert(eq(connection), savedRequest.capture());
            assertEquals(0, BigDecimal.ONE.compareTo(savedRequest.getValue().getDays()));
            assertEquals(shift, savedRequest.getValue().getShiftType());
            verify(connection).commit();
        }
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
        when(dao.findById(connection, 12)).thenReturn(request);
    }

    @Test
    void workingManagerApprovesAfterReadingTheLockedPendingRequest() throws Exception {
        stubManagerAndCurrentRequest();
        when(dao.approve(connection, 12, 7)).thenReturn(true);
        try (MockedStatic<DBContext> database = mockStatic(DBContext.class)) {
            database.when(DBContext::getConnection).thenReturn(connection);
            var result = service.approveRequest(10, 12);
            assertTrue(result.isSuccess());
            verify(connection).commit();
            verify(dao).approve(connection, 12, 7);
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
        var result = staffService.submitLeaveRequest(10, LeaveRequest.LeaveType.ANNUAL,
                firstDay, firstDay.minusDays(1), LeaveRequest.ShiftType.FULL_DAY, "Lý do hợp lệ");
        assertFalse(result.isSuccess());
        assertTrue(result.getFieldErrors().containsKey("toDate"));
        verifyNoInteractions(accounts);
    }

    @Test
    void submissionRejectsHalfShiftAcrossMultipleDays() {
        var result = staffService.submitLeaveRequest(10, LeaveRequest.LeaveType.ANNUAL,
                firstDay, firstDay.plusDays(1), LeaveRequest.ShiftType.MORNING, "Lý do hợp lệ");
        assertFalse(result.isSuccess());
        assertTrue(result.getFieldErrors().containsKey("shiftType"));
        verifyNoInteractions(accounts);
    }

    @Test
    void submissionRejectsCrossYearLeave() {
        LocalDate december = LocalDate.of(DateTimeUtil.today().getYear() + 1, 12, 31);
        var result = staffService.submitLeaveRequest(10, LeaveRequest.LeaveType.ANNUAL,
                december, december.plusDays(1), LeaveRequest.ShiftType.FULL_DAY, "Lý do hợp lệ");
        assertFalse(result.isSuccess());
        assertTrue(result.getFieldErrors().containsKey("toDate"));
    }

    @Test
    void submissionRejectsMoreThanThirtyOneDays() {
        LocalDate january = LocalDate.of(DateTimeUtil.today().getYear() + 1, 1, 1);
        var result = staffService.submitLeaveRequest(10, LeaveRequest.LeaveType.ANNUAL,
                january, january.plusDays(31), LeaveRequest.ShiftType.FULL_DAY, "Lý do hợp lệ");
        assertFalse(result.isSuccess());
        assertTrue(result.getFieldErrors().containsKey("toDate"));
    }

    @Test
    void confirmationRequiresEveryApplicableCheckToPass() {
        var detail = new LeaveReviewDetailView(request, "NV-0050", "PT test", "PT", null, null);
        detail.setCanAct(true);
        var quota = new com.gms.dto.ReviewCheck("ANNUAL_QUOTA", "Quỹ phép", true, true, "Đạt");
        var sessions = new com.gms.dto.ReviewCheck("SESSIONS", "Lịch PT", true, false, "Trùng lịch");
        detail.setChecks(java.util.List.of(quota, sessions));
        assertFalse(detail.isCanApprove());
        sessions.setOk(true);
        assertTrue(detail.isCanApprove());
        detail.setCanAct(false);
        assertFalse(detail.isCanApprove());
    }

    @Test
    void listDoesNotMarkAResignedEmployeeAsPassing() throws Exception {
        when(dao.countEmployees(any())).thenReturn(1);
        when(dao.findEmployeePage(any(), eq(0), eq(20))).thenReturn(java.util.List.of(
                new LeaveEmployeeView(50, "NV-0050", "PT test", "PT", null, "Working")));
        when(dao.findEmployeeRequests(any(), eq(java.util.List.of(50)), isNull())).thenReturn(java.util.List.of(
                new LeaveRequestRow(request, "NV-0050", "PT test", "PT", null)));
        when(dao.findWorkingPosition(connection, 50)).thenReturn(null);
        when(dao.countRequests(any())).thenReturn(1);
        when(dao.findRequestPage(any(), eq(0), eq(20), isNull())).thenReturn(java.util.List.of(
                new LeaveRequestRow(request, "NV-0050", "PT test", "PT", null)));
        try (MockedStatic<DBContext> database = mockStatic(DBContext.class)) {
            database.when(DBContext::getConnection).thenReturn(connection);
            var result = service.getEmployeePage(service.createManagerFilter("", "Pending", null, null, null, null), null, 1, 20);
            assertTrue(result.isSuccess());
            var item = result.getData().getItems().get(0).getRequests().get(0);
            assertEquals("Cần xử lý", item.getCheckSummary());
            assertTrue(item.getCheckMessage().contains("không còn"));
            var flatResult = service.getRequestPage(service.createManagerFilter("", "Pending", null, null, null, null), null, 1, 20);
            assertTrue(flatResult.isSuccess());
            assertEquals(item.getCheckMessage(), flatResult.getData().getItems().get(0).getCheckMessage());
        }
    }

    @Test
    void reviewBlocksConfirmationWhenTheEmployeeHasResigned() throws Exception {
        stubManagerAndCurrentRequest();
        when(dao.findReviewDetail(12, 7)).thenReturn(new LeaveReviewDetailView(
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

    @ParameterizedTest
    @EnumSource(value = LeaveRequest.Status.class, names = {"APPROVED", "REJECTED"})
    void processedHistoryDoesNotShowNewApprovalWarnings(LeaveRequest.Status status) throws Exception {
        stubManagerAndCurrentRequest();
        request.setStatus(status);
        request.setFromDate(DateTimeUtil.today().minusDays(2));
        request.setToDate(request.getFromDate());
        request.setDays(new BigDecimal("0.5"));
        when(dao.findReviewDetail(12, 7)).thenReturn(new LeaveReviewDetailView(
                request, "NV-0050", "PT test", "PT", null, "Quản lý test"));

        var result = service.getReviewDetail(12, 10);

        assertTrue(result.isSuccess());
        assertTrue(result.getData().getChecks().isEmpty());
        assertNull(result.getData().getBanner());
        assertFalse(result.getData().isCanApprove());
        assertEquals(new BigDecimal("0.5"), result.getData().getLeaveRequest().getDays());
        assertEquals("Quản lý test", result.getData().getReviewerName());
        verify(dao, never()).findWorkingPosition(any(Connection.class), anyInt());
    }

    @Test
    void approvalReadsMissingRequestInsideTransaction() throws Exception {
        stubManagerAndCurrentRequest();
        when(dao.findById(connection, 12)).thenReturn(null);
        try (MockedStatic<DBContext> database = mockStatic(DBContext.class)) {
            database.when(DBContext::getConnection).thenReturn(connection);
            assertFalse(service.approveRequest(10, 12).isSuccess());
            verify(connection).rollback();
            verify(dao, never()).approve(any(), anyInt(), anyInt());
        }
    }

    @Test
    void approvalRequiresLateConfirmationInsideTransaction() throws Exception {
        stubManagerAndCurrentRequest();
        request.setFromDate(DateTimeUtil.today().minusDays(1));
        request.setToDate(DateTimeUtil.today());
        try (MockedStatic<DBContext> database = mockStatic(DBContext.class)) {
            database.when(DBContext::getConnection).thenReturn(connection);
            var result = service.approveRequest(10, 12);
            assertFalse(result.isSuccess());
            assertTrue(result.getFieldErrors().containsKey("lateApprovalReason"));
            verify(connection).rollback();
            verify(dao, never()).approve(any(), anyInt(), anyInt());
        }
    }

    @Test
    void approvalRejectsAnAlreadyProcessedRequestInsideTransaction() throws Exception {
        stubManagerAndCurrentRequest();
        request.setStatus(LeaveRequest.Status.APPROVED);
        try (MockedStatic<DBContext> database = mockStatic(DBContext.class)) {
            database.when(DBContext::getConnection).thenReturn(connection);
            assertFalse(service.approveRequest(10, 12).isSuccess());
            verify(dao, never()).approve(any(), anyInt(), anyInt());
            verify(connection).rollback();
        }
    }

    @Test
    void earlySubmissionDoesNotImposeAnApprovalDeadline() throws Exception {
        request.setSubmittedAt(DateTimeUtil.now().minusDays(5));
        assertReadinessAcrossScreens(true, null);
    }

    @Test
    void managerCanRejectOverdueLeaveWithAValidReason() throws Exception {
        stubManagerAndCurrentRequest();
        request.setFromDate(DateTimeUtil.today().minusDays(2));
        request.setToDate(DateTimeUtil.today().minusDays(1));
        when(dao.reject(connection, 12, 7, "Lý do hợp lệ")).thenReturn(true);
        try (MockedStatic<DBContext> database = mockStatic(DBContext.class)) {
            database.when(DBContext::getConnection).thenReturn(connection);
            assertTrue(service.rejectRequest(10, 12, "Lý do hợp lệ").isSuccess());
            verify(connection).commit();
        }
    }

    @Test
    void listDetailAndPostAgreeOnLastAfternoonStaffingShortage() throws Exception {
        when(dao.countOnDutyAfterApproval(connection, firstDay.plusDays(1), "Afternoon", "PT", 12)).thenReturn(1);
        assertReadinessAcrossScreens(false, "Ca chiều");
    }

    @Test
    void listDetailAndPostAgreeOnPendingAnnualReservations() throws Exception {
        request.setLeaveType(LeaveRequest.LeaveType.ANNUAL);
        when(configs.getInt(SystemConfigService.LEAVE_ANNUAL_QUOTA)).thenReturn(14);
        when(dao.getAnnualBalance(connection, 50, firstDay.getYear(), 14, 12))
                .thenReturn(new LeaveBalance(14, new BigDecimal("12"), new BigDecimal("1")));
        assertReadinessAcrossScreens(false, "Vượt quá");
    }

    @Test
    void listDetailAndPostAgreeOnLastDaySessionConflict() throws Exception {
        when(dao.countScheduledSessions(connection, 50, firstDay.plusDays(1), "FullDay")).thenReturn(2);
        assertReadinessAcrossScreens(false, "trùng 2");
    }

    @Test
    void listDetailAndPostAllowAnEligibleRequest() throws Exception {
        assertReadinessAcrossScreens(true, null);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"    ", "abcd"})
    void lateApprovalRejectsMissingOrShortReason(String reason) throws Exception {
        stubManagerAndCurrentRequest();
        request.setFromDate(DateTimeUtil.today().minusDays(2));
        request.setToDate(DateTimeUtil.today().minusDays(1));
        try (MockedStatic<DBContext> database = mockStatic(DBContext.class)) {
            database.when(DBContext::getConnection).thenReturn(connection);
            var result = service.approveRequest(10, 12, reason);
            assertFalse(result.isSuccess());
            assertTrue(result.getFieldErrors().containsKey("lateApprovalReason"));
            verify(dao, never()).approve(any(), anyInt(), anyInt());
            verifyNoInteractions(audit, notifications);
            verify(connection).rollback();
        }
    }

    @Test
    void lateApprovalRejectsOverlongReason() throws Exception {
        stubManagerAndCurrentRequest();
        request.setFromDate(DateTimeUtil.today().minusDays(2));
        request.setToDate(DateTimeUtil.today().minusDays(1));
        try (MockedStatic<DBContext> database = mockStatic(DBContext.class)) {
            database.when(DBContext::getConnection).thenReturn(connection);
            assertFalse(service.approveRequest(10, 12, "a".repeat(501)).isSuccess());
            verify(dao, never()).approve(any(), anyInt(), anyInt());
        }
    }

    @Test
    void lateAnnualApprovalUsesLeaveYearAndDoesNotReserveItTwice() throws Exception {
        stubManagerAndCurrentRequest();
        int leaveYear = DateTimeUtil.today().getYear() - 1;
        request.setFromDate(LocalDate.of(leaveYear, 12, 30));
        request.setToDate(LocalDate.of(leaveYear, 12, 31));
        request.setLeaveType(LeaveRequest.LeaveType.ANNUAL);
        when(configs.getInt(SystemConfigService.LEAVE_ANNUAL_QUOTA)).thenReturn(14);
        when(dao.getAnnualBalance(connection, 50, leaveYear, 14, 12))
                .thenReturn(new LeaveBalance(14, new BigDecimal("12"), BigDecimal.ZERO));
        when(dao.approve(connection, 12, 7)).thenReturn(true);
        try (MockedStatic<DBContext> database = mockStatic(DBContext.class)) {
            database.when(DBContext::getConnection).thenReturn(connection);
            var result = service.approveRequest(10, 12, "  Đã xác nhận nhân viên nghỉ đúng đơn  ");
            assertTrue(result.isSuccess());
            verify(dao).getAnnualBalance(connection, 50, leaveYear, 14, 12);
            verify(dao).approve(connection, 12, 7);
            verify(audit).logInTransaction(connection, 10, Role.MANAGER, "Duyệt muộn đơn nghỉ phép",
                    "LeaveRequests", 12, "Status", "Pending", "Approved", "Đã xác nhận nhân viên nghỉ đúng đơn");
            verify(dao, never()).countOnDutyAfterApproval(any(), any(), anyString(), anyString(), anyInt());
            verify(dao, never()).countScheduledSessions(any(Connection.class), anyInt(), any(), anyString());
            verify(connection).commit();
            // Sau lần đầu, trạng thái đã xử lý chặn lần gửi lại và không ghi phép lần hai.
            request.setStatus(LeaveRequest.Status.APPROVED);
            assertFalse(service.approveRequest(10, 12, "Xác nhận lại cùng đơn").isSuccess());
            verify(dao, times(1)).approve(connection, 12, 7);
        }
    }

    @Test
    void lateAnnualApprovalStillBlocksInsufficientBalance() throws Exception {
        stubManagerAndCurrentRequest();
        request.setFromDate(DateTimeUtil.today().minusDays(2));
        request.setToDate(DateTimeUtil.today().minusDays(1));
        request.setLeaveType(LeaveRequest.LeaveType.ANNUAL);
        when(configs.getInt(SystemConfigService.LEAVE_ANNUAL_QUOTA)).thenReturn(14);
        when(dao.getAnnualBalance(connection, 50, request.getFromDate().getYear(), 14, 12))
                .thenReturn(new LeaveBalance(14, new BigDecimal("13"), BigDecimal.ZERO));
        try (MockedStatic<DBContext> database = mockStatic(DBContext.class)) {
            database.when(DBContext::getConnection).thenReturn(connection);
            var result = service.approveRequest(10, 12, "Đã xác nhận nghỉ thực tế");
            assertFalse(result.isSuccess());
            assertTrue(result.getMessage().contains("Vượt quá"));
            verify(dao, never()).approve(any(), anyInt(), anyInt());
            verify(connection).rollback();
        }
    }

    @Test
    void lateUnpaidApprovalDoesNotUseAnnualQuota() throws Exception {
        stubManagerAndCurrentRequest();
        request.setFromDate(DateTimeUtil.today().minusDays(2));
        request.setToDate(DateTimeUtil.today().minusDays(1));
        when(dao.approve(connection, 12, 7)).thenReturn(true);
        try (MockedStatic<DBContext> database = mockStatic(DBContext.class)) {
            database.when(DBContext::getConnection).thenReturn(connection);
            assertTrue(service.approveRequest(10, 12, "Đã trao đổi với nhân viên").isSuccess());
            verify(dao, never()).getAnnualBalance(any(), anyInt(), anyInt(), anyInt(), anyInt());
            verify(connection).commit();
        }
    }

    @Test
    void ongoingLeaveChecksTodayAndFutureButNotPastStaffingOrSessions() throws Exception {
        LocalDate today = DateTimeUtil.today();
        // Không phụ thuộc tháng/năm: khoảng hôm qua đến hôm nay luôn hợp lệ nếu cùng năm.
        LocalDate fromDate = today.getDayOfYear() == 1 ? today : today.minusDays(1);
        request.setFromDate(fromDate);
        request.setToDate(today);
        request.setDays(BigDecimal.valueOf(java.time.temporal.ChronoUnit.DAYS.between(fromDate, today) + 1));
        when(dao.countScheduledSessions(connection, 50, today, "FullDay")).thenReturn(1);
        var result = service.validateApproval(connection, request, "PT");
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("trùng 1"));
        verify(dao, never()).countOnDutyAfterApproval(eq(connection), eq(today.minusDays(1)), anyString(), anyString(), anyInt());
        verify(dao, never()).countScheduledSessions(connection, 50, today.minusDays(1), "FullDay");
        verify(dao).countOnDutyAfterApproval(connection, today, "Morning", "PT", 12);
        verify(dao).countOnDutyAfterApproval(connection, today, "Afternoon", "PT", 12);
    }

    @Test
    void lateApprovalCannotBeBypassedThroughBulkAction() throws Exception {
        stubManagerAndCurrentRequest();
        request.setFromDate(DateTimeUtil.today().minusDays(2));
        request.setToDate(DateTimeUtil.today().minusDays(1));
        try (MockedStatic<DBContext> database = mockStatic(DBContext.class)) {
            database.when(DBContext::getConnection).thenReturn(connection);
            var result = service.approveRequests(10, java.util.List.of(12));
            assertTrue(result.isSuccess());
            assertEquals(0, result.getData().getApprovedCount());
            assertTrue(result.getData().getFailedRequests().get(12).contains("Duyệt muộn"));
            verify(dao, never()).approve(any(), anyInt(), anyInt());
        }
    }

    @Test
    void latePendingListAndDetailShowConfirmationInsteadOfDateBlock() throws Exception {
        stubManagerAndCurrentRequest();
        request.setFromDate(DateTimeUtil.today().minusDays(2));
        request.setToDate(DateTimeUtil.today().minusDays(1));
        when(dao.countRequests(any())).thenReturn(1);
        when(dao.findRequestPage(any(), eq(0), eq(20), isNull())).thenReturn(java.util.List.of(
                new LeaveRequestRow(request, "NV-0050", "PT test", "PT", null)));
        when(dao.findReviewDetail(12, 7)).thenReturn(new LeaveReviewDetailView(
                request, "NV-0050", "PT test", "PT", null, null));
        try (MockedStatic<DBContext> database = mockStatic(DBContext.class)) {
            database.when(DBContext::getConnection).thenReturn(connection);
            var item = service.getRequestPage(service.createManagerFilter("", "Pending", null, null, null, null),
                    null, 1, 20).getData().getItems().get(0);
            assertTrue(item.isLateApproval());
            assertFalse(item.isNearLeaveDate());
            assertEquals("Cần xác nhận", item.getCheckSummary());
            assertTrue(item.isCanApprove());
            var detail = service.getReviewDetail(12, 10).getData();
            assertTrue(detail.isCanApprove());
            assertTrue(detail.getBanner().contains("xác nhận"));
            assertTrue(detail.getStaffingRows().isEmpty());
        }
    }

    /** Cùng dữ liệu giả đi qua cả ba luồng để bắt việc giao diện lệch điều kiện POST. */
    private void assertReadinessAcrossScreens(boolean expectedAllowed, String expectedMessage) throws Exception {
        stubManagerAndCurrentRequest();
        when(dao.countRequests(any())).thenReturn(1);
        when(dao.findRequestPage(any(), eq(0), eq(20), isNull())).thenReturn(java.util.List.of(
                new LeaveRequestRow(request, "NV-0050", "PT test", "PT", null)));
        when(dao.countEmployees(any())).thenReturn(1);
        when(dao.findEmployeePage(any(), eq(0), eq(20))).thenReturn(java.util.List.of(
                new LeaveEmployeeView(50, "NV-0050", "PT test", "PT", null, "Working")));
        when(dao.findEmployeeRequests(any(), eq(java.util.List.of(50)), isNull())).thenReturn(java.util.List.of(
                new LeaveRequestRow(request, "NV-0050", "PT test", "PT", null)));
        when(dao.findReviewDetail(12, 7)).thenReturn(new LeaveReviewDetailView(
                request, "NV-0050", "PT test", "PT", null, null));
        when(dao.approve(connection, 12, 7)).thenReturn(true);
        try (MockedStatic<DBContext> database = mockStatic(DBContext.class)) {
            database.when(DBContext::getConnection).thenReturn(connection);
            var pageResult = service.getEmployeePage(service.createManagerFilter("", "Pending", null, null, null, null), null, 1, 20);
            var flatResult = service.getRequestPage(service.createManagerFilter("", "Pending", null, null, null, null), null, 1, 20);
            var detailResult = service.getReviewDetail(12, 10);
            var decisionResult = service.approveRequest(10, 12);
            assertTrue(pageResult.isSuccess());
            assertTrue(flatResult.isSuccess());
            assertTrue(detailResult.isSuccess());
            var item = pageResult.getData().getItems().get(0).getRequests().get(0);
            assertEquals(expectedAllowed ? "Đạt" : "Cần xử lý", item.getCheckSummary());
            assertEquals(item.getCheckSummary(), flatResult.getData().getItems().get(0).getCheckSummary());
            assertEquals(item.getCheckMessage(), flatResult.getData().getItems().get(0).getCheckMessage());
            assertEquals(expectedAllowed, detailResult.getData().isCanApprove());
            assertEquals(expectedAllowed, decisionResult.isSuccess());
            if (!expectedAllowed) {
                assertTrue(item.getCheckMessage().contains(expectedMessage));
                assertEquals(item.getCheckMessage(), decisionResult.getMessage());
                verify(dao, never()).approve(any(), anyInt(), anyInt());
            }
        }
    }
}
