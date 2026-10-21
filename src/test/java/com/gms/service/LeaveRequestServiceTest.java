package com.gms.service;

import com.gms.dao.AccountDAO;
import com.gms.dao.EmployeeDAO;
import com.gms.dao.LeaveRequestDAO;
import com.gms.dao.PersonDAO;
import com.gms.entity.LeaveRequest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Kiểm thử đơn vị cho LeaveRequestService (BF-05, UC-14, UC-15, JOB-07).
 */
@ExtendWith(MockitoExtension.class)
class LeaveRequestServiceTest {

    @Mock
    private LeaveRequestDAO leaveRequestDAO;

    @Mock
    private AccountDAO accountDAO;

    @Mock
    private EmployeeDAO employeeDAO;

    @Mock
    private PersonDAO personDAO;

    @Mock
    private SystemConfigService systemConfigService;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private NotificationService notificationService;

    private LeaveRequestService service;
    private StaffLeaveService staffService;

    @BeforeEach
    void setUp() {
        service = new LeaveRequestService(
                leaveRequestDAO, accountDAO, employeeDAO,
                systemConfigService, auditLogService, notificationService
        );
        staffService = new StaffLeaveService(
                leaveRequestDAO, accountDAO, employeeDAO, personDAO,
                systemConfigService, auditLogService, notificationService
        );
    }

    @Test
    void managerFilterUsesKnownTypesAndIgnoresMalformedDates() {
        var filter = service.createManagerFilter("  NV-0006  ", "Pending", "Manager", "Sick", "not-a-date", "2026-10-24");
        assertEquals("NV-0006", filter.getKeyword());
        assertNull(filter.getPosition());
        assertEquals(LeaveRequest.LeaveType.SICK, filter.getLeaveType());
        assertNull(filter.getFromDate());
        assertEquals(LocalDate.of(2026, 10, 24), filter.getToDate());
    }

    @Test
    void managerFilterKeepsAllStatusAndDateRange() {
        var filter = service.createManagerFilter("", "All", "Receptionist", "Unpaid", "2026-10-23", "2026-10-24");
        assertNull(filter.getStatus());
        assertEquals("Receptionist", filter.getPosition());
        assertEquals(LeaveRequest.LeaveType.UNPAID, filter.getLeaveType());
        assertEquals(LocalDate.of(2026, 10, 23), filter.getFromDate());
        assertEquals(LocalDate.of(2026, 10, 24), filter.getToDate());
    }

    @Test
    void parseStatusFilter_returnsPendingByDefault_whenNullOrBlank() {
        assertEquals(LeaveRequest.Status.PENDING, service.parseStatusFilter(null));
        assertEquals(LeaveRequest.Status.PENDING, service.parseStatusFilter(""));
        assertEquals(LeaveRequest.Status.PENDING, service.parseStatusFilter("   "));
    }

    @Test
    void parseStatusFilter_returnsNull_whenAll() {
        assertNull(service.parseStatusFilter("All"));
        assertNull(service.parseStatusFilter("all"));
        assertNull(service.parseStatusFilter(" ALL "));
    }

    @ParameterizedTest
    @CsvSource({
        "Pending, PENDING",
        "Approved, APPROVED",
        "Rejected, REJECTED"
    })
    void parseStatusFilter_mapsValidStatus(String param, LeaveRequest.Status expected) {
        assertEquals(expected, service.parseStatusFilter(param));
    }

    // ===== Kiểm thử validate submitLeaveRequest (UC-14) =====

    @Test
    void submitLeaveRequest_fails_whenLeaveTypeIsNull() {
        var result = staffService.submitLeaveRequest(
                1, null, java.time.LocalDate.now().plusDays(2),
                LeaveRequest.ShiftType.MORNING, "Lý do hợp lệ");

        assertFalse(result.isSuccess());
        assertTrue(result.getFieldErrors().containsKey("leaveType"));
    }

    @Test
    void submitLeaveRequest_fails_whenShiftTypeIsNull() {
        var result = staffService.submitLeaveRequest(
                1, LeaveRequest.LeaveType.ANNUAL, java.time.LocalDate.now().plusDays(2),
                null, "Lý do hợp lệ");

        assertFalse(result.isSuccess());
        assertTrue(result.getFieldErrors().containsKey("shiftType"));
    }

    @Test
    void submitLeaveRequest_fails_whenLeaveDateIsNull() {
        var result = staffService.submitLeaveRequest(
                1, LeaveRequest.LeaveType.ANNUAL, null,
                LeaveRequest.ShiftType.MORNING, "Lý do hợp lệ");

        assertFalse(result.isSuccess());
        assertTrue(result.getFieldErrors().containsKey("leaveDate"));
    }

    @Test
    void submitLeaveRequest_fails_whenLeaveDateIsTodayOrInThePast() {
        java.time.LocalDate today = java.time.LocalDate.now();
        var resultToday = staffService.submitLeaveRequest(
                1, LeaveRequest.LeaveType.ANNUAL, today,
                LeaveRequest.ShiftType.MORNING, "Lý do hợp lệ");
        assertFalse(resultToday.isSuccess());
        assertTrue(resultToday.getFieldErrors().containsKey("leaveDate"));

        var resultPast = staffService.submitLeaveRequest(
                1, LeaveRequest.LeaveType.ANNUAL, today.minusDays(1),
                LeaveRequest.ShiftType.MORNING, "Lý do hợp lệ");
        assertFalse(resultPast.isSuccess());
        assertTrue(resultPast.getFieldErrors().containsKey("leaveDate"));
    }

    @Test
    void submitLeaveRequest_fails_whenReasonIsTooShort() {
        var result = staffService.submitLeaveRequest(
                1, LeaveRequest.LeaveType.ANNUAL, java.time.LocalDate.now().plusDays(2),
                LeaveRequest.ShiftType.MORNING, "abc"); // dưới 5 ký tự

        assertFalse(result.isSuccess());
        assertTrue(result.getFieldErrors().containsKey("reason"));
    }

    // ===== Kiểm thử validate rejectRequest (UC-15) =====

    @Test
    void rejectRequest_fails_whenRejectReasonIsTooShort() {
        var result = service.rejectRequest(1, 1, "ngắn"); // dưới 5 ký tự
        assertFalse(result.isSuccess());
        assertTrue(result.getFieldErrors().containsKey("rejectReason"));
    }

    @Test
    void rejectRequest_fails_whenRejectReasonIsNull() {
        var result = service.rejectRequest(1, 1, null);
        assertFalse(result.isSuccess());
        assertTrue(result.getFieldErrors().containsKey("rejectReason"));
    }

    @Test
    void rejectRequest_fails_whenRejectReasonIsTooLong() {
        String longReason = "a".repeat(501); // vượt quá 500 ký tự
        var result = service.rejectRequest(1, 1, longReason);
        assertFalse(result.isSuccess());
        assertTrue(result.getFieldErrors().containsKey("rejectReason"));
    }

    // ===== Kiểm thử getReviewDetail (UC-15) =====

    @Test
    void getReviewDetail_fails_whenIdIsZeroOrNegative() {
        var resultZero = service.getReviewDetail(0);
        assertFalse(resultZero.isSuccess());

        var resultNegative = service.getReviewDetail(-5);
        assertFalse(resultNegative.isSuccess());
    }

    // ===== Kiểm thử tính hạn duyệt P-20 (CBR-85) =====

    @Test
    void computeApprovalDeadline_nearDate_usesDayBeforeLeaveDate() {
        // Đơn nộp 19/10, xin nghỉ 21/10 -> Hạn duyệt chốt 20:00 ngày 20/10 (vì 20/10 < 22/10)
        LeaveRequest req = new LeaveRequest();
        req.setSubmittedAt(LocalDateTime.of(2026, 10, 19, 9, 30));
        req.setFromDate(LocalDate.of(2026, 10, 21));
        req.setToDate(req.getFromDate());

        LocalDateTime deadline = service.computeApprovalDeadline(req, LocalTime.of(20, 0));
        assertEquals(LocalDateTime.of(2026, 10, 20, 20, 0), deadline);
    }

    @Test
    void computeApprovalDeadline_farDate_capsAt3DaysSla() {
        // Đơn nộp 19/10, xin nghỉ xa 20/12 -> Hạn duyệt chốt sau 3 ngày lúc 20:00 ngày 22/10 (CBR-85)
        LeaveRequest req = new LeaveRequest();
        req.setSubmittedAt(LocalDateTime.of(2026, 10, 19, 9, 30));
        req.setFromDate(LocalDate.of(2026, 12, 20));
        req.setToDate(req.getFromDate());

        LocalDateTime deadline = service.computeApprovalDeadline(req, LocalTime.of(20, 0));
        assertEquals(LocalDateTime.of(2026, 10, 22, 20, 0), deadline);
    }

    // Các trường hợp duyệt/từ chối có transaction nằm ở LeaveApprovalSafetyTest.

    // ===== Kiểm thử rút đơn (withdrawRequest - UC-14) =====

    @Test
    void withdrawRequest_fails_whenIdIsInvalid() {
        var resultZero = staffService.withdrawRequest(1, 0);
        assertFalse(resultZero.isSuccess());

        var resultNegative = staffService.withdrawRequest(1, -1);
        assertFalse(resultNegative.isSuccess());
    }
}
