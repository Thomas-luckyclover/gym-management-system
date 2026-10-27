package com.gms.service;

import com.gms.constant.Role;
import com.gms.dao.AccountDAO;
import com.gms.dao.EmployeeDAO;
import com.gms.dao.LeaveRequestDAO;
import com.gms.dao.PersonDAO;
import com.gms.dto.LeaveBalance;
import com.gms.dto.LeaveRequestGroupView;
import com.gms.entity.Account;
import com.gms.entity.Employee;
import com.gms.entity.LeaveRequest;
import com.gms.util.DBContext;
import com.gms.util.DateTimeUtil;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import com.gms.dto.LeaveSubmissionPreview;
import com.gms.dto.ServiceResult;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Kiểm tra form, dự báo và gửi đơn: đủ phép, tách đơn có đồng ý, lịch PT và rollback. */
class StaffLeaveServiceTest {
    private final LeaveRequestDAO dao = mock(LeaveRequestDAO.class);
    private final AccountDAO accounts = mock(AccountDAO.class);
    private final EmployeeDAO employees = mock(EmployeeDAO.class);
    private final SystemConfigService configs = mock(SystemConfigService.class);
    private final AuditLogService audit = mock(AuditLogService.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final Connection connection = mock(Connection.class);
    private final Employee employee = mock(Employee.class);
    private StaffLeaveService service;
    private LocalDate first;

    @BeforeEach
    void setUp() throws Exception {
        service = new StaffLeaveService(dao, accounts, employees, mock(PersonDAO.class), configs, audit, notifications);
        first = LocalDate.of(DateTimeUtil.today().getYear() + 1, 2, 1);
        when(accounts.findById(10)).thenReturn(new Account(44, null, Account.Status.ACTIVE));
        when(employees.findByPersonId(44)).thenReturn(employee);
        when(employee.getEmployeeId()).thenReturn(50);
        when(employee.getPosition()).thenReturn(Role.RECEPTIONIST);
        when(employee.getStatus()).thenReturn(Employee.Status.WORKING);
        when(dao.findWorkingPosition(connection, 50)).thenAnswer(invocation -> employee.getPosition().getDbValue());
        when(configs.getString(SystemConfigService.LEAVE_SUBMIT_DEADLINE)).thenReturn("15:00");
        when(configs.getInt(SystemConfigService.LEAVE_ANNUAL_QUOTA)).thenReturn(14);
        setRemaining("5");
        when(dao.insert(eq(connection), any())).thenReturn(101, 102);
    }

    // ===== Dự báo nhân sự khi mở form và đổi lựa chọn =====
    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"PT", "RECEPTIONIST"})
    void formAndPreviewOnlyDeductTheApplicantsPosition(Role role) throws Exception {
        when(employee.getPosition()).thenReturn(role);
        when(configs.getInt(SystemConfigService.MIN_PT_PER_SHIFT)).thenReturn(2);
        when(configs.getInt(SystemConfigService.MIN_RECEPTIONIST_PER_SHIFT)).thenReturn(1);
        var tomorrow = DateTimeUtil.today().plusDays(1);
        when(dao.countOnDuty(connection, tomorrow, "FullDay", "PT", 0)).thenReturn(6);
        when(dao.countOnDuty(connection, tomorrow, "FullDay", "Receptionist", 0)).thenReturn(4);

        ServiceResult<com.gms.dto.StaffLeaveFormView> formResult;
        ServiceResult<LeaveSubmissionPreview> previewResult;
        try (MockedStatic<DBContext> database = mockStatic(DBContext.class)) {
            database.when(DBContext::getConnection).thenReturn(connection);
            formResult = service.getStaffLeaveForm(10);
            previewResult = service.previewSubmission(10, LeaveRequest.LeaveType.UNPAID,
                    tomorrow, tomorrow, LeaveRequest.ShiftType.FULL_DAY);
        }
        assertTrue(formResult.isSuccess());
        assertTrue(previewResult.isSuccess());
        var form = formResult.getData().getStaffing();
        var preview = previewResult.getData().getStaffing();
        assertEquals(form.getRemainingPt(), preview.getRemainingPt());
        assertEquals(form.getRemainingReceptionist(), preview.getRemainingReceptionist());
        assertEquals(role == Role.PT ? 5 : 6, preview.getRemainingPt());
        assertEquals(role == Role.RECEPTIONIST ? 3 : 4, preview.getRemainingReceptionist());
        assertEquals(2, preview.getMinPt());
        assertEquals(1, preview.getMinReceptionist());
    }

    private void setRemaining(String days) throws Exception {
        when(dao.getAnnualBalance(connection, 50, first.getYear(), 14))
                .thenReturn(new LeaveBalance(14, new BigDecimal("14").subtract(new BigDecimal(days)), BigDecimal.ZERO));
    }

    @Test
    void previewUsesTheLowestStaffingOnTheLastDayWithoutInventingStaff() throws Exception {
        when(configs.getInt(SystemConfigService.MIN_PT_PER_SHIFT)).thenReturn(2);
        when(configs.getInt(SystemConfigService.MIN_RECEPTIONIST_PER_SHIFT)).thenReturn(1);
        when(dao.countOnDuty(eq(connection), any(), eq("FullDay"), eq("PT"), eq(0))).thenReturn(4);
        when(dao.countOnDuty(eq(connection), any(), eq("FullDay"), eq("Receptionist"), eq(0))).thenReturn(3);
        when(dao.countOnDuty(connection, first.plusDays(2), "FullDay", "PT", 0)).thenReturn(0);
        when(dao.countOnDuty(connection, first.plusDays(2), "FullDay", "Receptionist", 0)).thenReturn(1);
        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(connection);
            var result = service.previewSubmission(10, LeaveRequest.LeaveType.UNPAID,
                    first, first.plusDays(2), LeaveRequest.ShiftType.FULL_DAY);
            assertTrue(result.isSuccess());
            assertEquals(0, result.getData().getStaffing().getRemainingPt());
            assertEquals(0, result.getData().getStaffing().getRemainingReceptionist());
        }
    }

    @Test
    void consentingToFiveAnnualAndFourUnpaidCreatesLinkedNonoverlappingRequests() throws Exception {
        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(connection);
            var result = service.submitLeaveRequest(10, LeaveRequest.LeaveType.ANNUAL, first, first.plusDays(8),
                    LeaveRequest.ShiftType.FULL_DAY, "Việc gia đình", true, 5, 4);
            assertTrue(result.isSuccess());
            var parts = savedParts(2);
            assertEquals(LeaveRequest.LeaveType.ANNUAL, parts.get(0).getLeaveType());
            assertEquals(first, parts.get(0).getFromDate());
            assertEquals(first.plusDays(4), parts.get(0).getToDate());
            assertEquals(new BigDecimal("5"), parts.get(0).getDays());
            assertEquals(LeaveRequest.LeaveType.UNPAID, parts.get(1).getLeaveType());
            assertEquals(first.plusDays(5), parts.get(1).getFromDate());
            assertEquals(first.plusDays(8), parts.get(1).getToDate());
            assertEquals(new BigDecimal("4"), parts.get(1).getDays());
            assertNotNull(UUID.fromString(parts.get(0).getLeaveGroupId()));
            assertEquals(parts.get(0).getLeaveGroupId(), parts.get(1).getLeaveGroupId());
            verify(audit, times(2)).logInTransaction(eq(connection), eq(10), eq(Role.RECEPTIONIST),
                    eq("Nộp đơn nghỉ phép"), eq("LeaveRequests"), anyInt(), eq("Status"), isNull(), eq("Pending"), eq("Việc gia đình"));
            verify(notifications).notifyRoleInTransaction(eq(connection), eq(Role.MANAGER), anyString(), contains("5 ngày phép năm và 4 ngày không lương"));
            verify(connection).commit();
        }
    }

    @Test
    void insufficientAnnualWithoutConsentDoesNotCreateAnything() throws Exception {
        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(connection);
            var result = service.submitLeaveRequest(10, LeaveRequest.LeaveType.ANNUAL, first, first.plusDays(8),
                    LeaveRequest.ShiftType.FULL_DAY, "Việc gia đình");
            assertFalse(result.isSuccess());
            assertTrue(result.getFieldErrors().containsKey("acceptUnpaid"));
            verify(dao, never()).insert(any(), any());
            verifyNoInteractions(audit, notifications);
            verify(connection).rollback();
        }
    }

    @Test
    void changedBalanceRequiresFreshConsentInsteadOfUsingOldFiveFourPlan() throws Exception {
        setRemaining("4");
        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(connection);
            var result = service.submitLeaveRequest(10, LeaveRequest.LeaveType.ANNUAL, first, first.plusDays(8),
                    LeaveRequest.ShiftType.FULL_DAY, "Việc gia đình", true, 5, 4);
            assertFalse(result.isSuccess());
            assertTrue(result.getMessage().contains("đã thay đổi"));
            verify(dao, never()).insert(any(), any());
        }
    }

    @Test
    void enoughAnnualSavesOneRequestWithNoGroup() throws Exception {
        setRemaining("14");
        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(connection);
            assertTrue(service.submitLeaveRequest(10, LeaveRequest.LeaveType.ANNUAL, first, first.plusDays(8),
                    LeaveRequest.ShiftType.FULL_DAY, "Việc gia đình").isSuccess());
            var request = savedParts(1).get(0);
            assertEquals(new BigDecimal("9"), request.getDays());
            assertEquals(LeaveRequest.LeaveType.ANNUAL, request.getLeaveType());
            assertNull(request.getLeaveGroupId());
        }
    }

    @Test
    void explicitlyUnpaidNeverUsesAnnualBalanceEvenWhenAvailable() throws Exception {
        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(connection);
            assertTrue(service.submitLeaveRequest(10, LeaveRequest.LeaveType.UNPAID, first, first.plusDays(8),
                    LeaveRequest.ShiftType.FULL_DAY, "Việc gia đình").isSuccess());
            var request = savedParts(1).get(0);
            assertEquals(LeaveRequest.LeaveType.UNPAID, request.getLeaveType());
            assertNull(request.getLeaveGroupId());
            verify(dao, never()).getAnnualBalance(any(), anyInt(), anyInt(), anyInt());
        }
    }

    @Test
    void zeroUsableAnnualDaysCreatesOnlyOneUnpaidRequestAfterConsent() throws Exception {
        setRemaining("0.5");
        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(connection);
            assertTrue(service.submitLeaveRequest(10, LeaveRequest.LeaveType.ANNUAL, first, first,
                    LeaveRequest.ShiftType.MORNING, "Việc gia đình", true, 0, 1).isSuccess());
            var request = savedParts(1).get(0);
            assertEquals(BigDecimal.ONE, request.getDays());
            assertEquals(LeaveRequest.LeaveType.UNPAID, request.getLeaveType());
            assertEquals(LeaveRequest.ShiftType.MORNING, request.getShiftType());
            assertNull(request.getLeaveGroupId());
        }
    }

    @Test
    void secondInsertFailureRollsBackBothPartsAndAudit() throws Exception {
        when(dao.insert(eq(connection), any())).thenReturn(101).thenThrow(new SQLException("second insert failed"));
        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(connection);
            assertFalse(service.submitLeaveRequest(10, LeaveRequest.LeaveType.ANNUAL, first, first.plusDays(8),
                    LeaveRequest.ShiftType.FULL_DAY, "Việc gia đình", true, 5, 4).isSuccess());
            verify(connection).rollback();
            verify(connection, never()).commit();
            verifyNoInteractions(notifications);
        }
    }

    @Test
    void newScheduledSessionOnLastDayBlocksBothParts() throws Exception {
        when(employee.getPosition()).thenReturn(Role.PT);
        when(dao.countScheduledSessions(connection, 50, first.plusDays(8), "FullDay")).thenReturn(1);
        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(connection);
            assertFalse(service.submitLeaveRequest(10, LeaveRequest.LeaveType.ANNUAL, first, first.plusDays(8),
                    LeaveRequest.ShiftType.FULL_DAY, "Việc gia đình", true, 5, 4).isSuccess());
            verify(dao, never()).insert(any(), any());
            verify(connection).rollback();
        }
    }

    @Test
    void overlappingRequestBlocksBeforeBalanceOrInsert() throws Exception {
        when(dao.hasConflictingLeave(connection, 50, first, first.plusDays(8), "FullDay")).thenReturn(true);
        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(connection);
            assertFalse(service.submitLeaveRequest(10, LeaveRequest.LeaveType.ANNUAL, first, first.plusDays(8),
                    LeaveRequest.ShiftType.FULL_DAY, "Việc gia đình", true, 5, 4).isSuccess());
            verify(dao, never()).insert(any(), any());
            verify(dao, never()).getAnnualBalance(any(), anyInt(), anyInt(), anyInt());
        }
    }

    @Test
    void previewReflectsPendingReservationsAndDoesNotWrite() throws Exception {
        when(dao.getAnnualBalance(connection, 50, first.getYear(), 14))
                .thenReturn(new LeaveBalance(14, new BigDecimal("6"), new BigDecimal("3")));
        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(connection);
            var result = service.previewSubmission(10, LeaveRequest.LeaveType.ANNUAL,
                    first, first.plusDays(8), LeaveRequest.ShiftType.FULL_DAY);
            assertTrue(result.isSuccess());
            assertEquals(5, result.getData().getAnnualDays());
            assertEquals(4, result.getData().getUnpaidDays());
            assertTrue(result.getData().isNeedsUnpaidConsent());
            verify(dao, never()).insert(any(), any());
            verify(connection, never()).commit();
        }
    }

    @Test
    void mixedDecisionsCountOnlyApprovedPartInGroup() {
        LeaveRequest annual = new LeaveRequest();
        annual.setLeaveGroupId(UUID.randomUUID().toString());
        annual.setDays(new BigDecimal("5"));
        annual.setStatus(LeaveRequest.Status.APPROVED);
        LeaveRequest unpaid = new LeaveRequest();
        unpaid.setLeaveGroupId(annual.getLeaveGroupId());
        unpaid.setDays(new BigDecimal("4"));
        unpaid.setStatus(LeaveRequest.Status.REJECTED);
        var group = new LeaveRequestGroupView(List.of(annual, unpaid));
        assertEquals(new BigDecimal("9"), group.getTotalDays());
        assertEquals(new BigDecimal("5"), group.getApprovedDays());
        assertTrue(group.isLinked());
    }

    private List<LeaveRequest> savedParts(int count) throws Exception {
        ArgumentCaptor<LeaveRequest> captured = ArgumentCaptor.forClass(LeaveRequest.class);
        verify(dao, times(count)).insert(eq(connection), captured.capture());
        return captured.getAllValues();
    }
}
