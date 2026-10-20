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

    @BeforeEach
    void setUp() {
        service = new LeaveRequestService(
                leaveRequestDAO, accountDAO, employeeDAO, personDAO,
                systemConfigService, auditLogService, notificationService
        );
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
        var result = service.submitLeaveRequest(
                1, null, java.time.LocalDate.now().plusDays(2),
                LeaveRequest.ShiftType.MORNING, "Lý do hợp lệ");

        assertFalse(result.isSuccess());
        assertTrue(result.getFieldErrors().containsKey("leaveType"));
    }

    @Test
    void submitLeaveRequest_fails_whenShiftTypeIsNull() {
        var result = service.submitLeaveRequest(
                1, LeaveRequest.LeaveType.ANNUAL, java.time.LocalDate.now().plusDays(2),
                null, "Lý do hợp lệ");

        assertFalse(result.isSuccess());
        assertTrue(result.getFieldErrors().containsKey("shiftType"));
    }

    @Test
    void submitLeaveRequest_fails_whenLeaveDateIsNull() {
        var result = service.submitLeaveRequest(
                1, LeaveRequest.LeaveType.ANNUAL, null,
                LeaveRequest.ShiftType.MORNING, "Lý do hợp lệ");

        assertFalse(result.isSuccess());
        assertTrue(result.getFieldErrors().containsKey("leaveDate"));
    }

    @Test
    void submitLeaveRequest_fails_whenLeaveDateIsTodayOrInThePast() {
        java.time.LocalDate today = java.time.LocalDate.now();
        var resultToday = service.submitLeaveRequest(
                1, LeaveRequest.LeaveType.ANNUAL, today,
                LeaveRequest.ShiftType.MORNING, "Lý do hợp lệ");
        assertFalse(resultToday.isSuccess());
        assertTrue(resultToday.getFieldErrors().containsKey("leaveDate"));

        var resultPast = service.submitLeaveRequest(
                1, LeaveRequest.LeaveType.ANNUAL, today.minusDays(1),
                LeaveRequest.ShiftType.MORNING, "Lý do hợp lệ");
        assertFalse(resultPast.isSuccess());
        assertTrue(resultPast.getFieldErrors().containsKey("leaveDate"));
    }

    @Test
    void submitLeaveRequest_fails_whenReasonIsTooShort() {
        var result = service.submitLeaveRequest(
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
        req.setLeaveDate(LocalDate.of(2026, 10, 21));

        LocalDateTime deadline = service.computeApprovalDeadline(req, LocalTime.of(20, 0));
        assertEquals(LocalDateTime.of(2026, 10, 20, 20, 0), deadline);
    }

    @Test
    void computeApprovalDeadline_farDate_capsAt3DaysSla() {
        // Đơn nộp 19/10, xin nghỉ xa 20/12 -> Hạn duyệt chốt sau 3 ngày lúc 20:00 ngày 22/10 (CBR-85)
        LeaveRequest req = new LeaveRequest();
        req.setSubmittedAt(LocalDateTime.of(2026, 10, 19, 9, 30));
        req.setLeaveDate(LocalDate.of(2026, 12, 20));

        LocalDateTime deadline = service.computeApprovalDeadline(req, LocalTime.of(20, 0));
        assertEquals(LocalDateTime.of(2026, 10, 22, 20, 0), deadline);
    }

    // ===== Kiểm thử duyệt đơn (approveRequest - UC-15, CBR-85) =====

    @Test
    void approveRequest_fails_whenLeaveDatePassed() throws Exception {
        LeaveRequest req = new LeaveRequest();
        req.setLeaveRequestId(101);
        req.setStatus(LeaveRequest.Status.PENDING);
        req.setLeaveDate(LocalDate.now().minusDays(1)); // Ngày nghỉ đã qua
        req.setSubmittedAt(LocalDateTime.now().minusDays(5));
        req.setShiftType(LeaveRequest.ShiftType.MORNING);

        LeaveRequestDAO.LeaveReviewDetail detail = new LeaveRequestDAO.LeaveReviewDetail(
                req, "NV001", "Nguyễn Văn A", "PT", null, null
        );

        when(leaveRequestDAO.findReviewDetail(101)).thenReturn(detail);

        var result = service.approveRequest(1, 101);

        assertFalse(result.isSuccess());
        assertEquals("Không thể duyệt đơn nghỉ phép của ngày đã qua.", result.getMessage());
        verify(leaveRequestDAO, never()).approve(any(), anyInt(), anyInt());
    }

    @Test
    void approveRequest_notBlockedByDeadline_whenOverdue() throws Exception {
        // Đơn quá hạn duyệt nhưng ngày nghỉ trong tương lai: Manager vẫn có quyền xem xét xử lý, không bị hard block
        LeaveRequest req = new LeaveRequest();
        req.setLeaveRequestId(101);
        req.setStatus(LeaveRequest.Status.PENDING);
        req.setLeaveDate(LocalDate.now().plusDays(2));
        req.setSubmittedAt(LocalDateTime.now().minusDays(5));
        req.setShiftType(LeaveRequest.ShiftType.MORNING);

        LeaveRequestDAO.LeaveReviewDetail detail = new LeaveRequestDAO.LeaveReviewDetail(
                req, "NV001", "Nguyễn Văn A", "PT", null, null
        );

        when(leaveRequestDAO.findReviewDetail(101)).thenReturn(detail);
        when(accountDAO.findById(1)).thenReturn(null);

        var result = service.approveRequest(1, 101);

        assertFalse(result.isSuccess());
        assertFalse(result.getMessage().contains("quá hạn duyệt"));
        assertNotEquals("Không thể duyệt đơn nghỉ phép của ngày đã qua.", result.getMessage());
    }

    @Test
    void approveRequest_fails_whenRequestNotFound() throws Exception {
        when(leaveRequestDAO.findReviewDetail(999)).thenReturn(null);

        var result = service.approveRequest(1, 999);

        assertFalse(result.isSuccess());
        verify(leaveRequestDAO, never()).approve(any(), anyInt(), anyInt());
    }

    @Test
    void approveRequest_fails_whenNotPending() throws Exception {
        LeaveRequest req = new LeaveRequest();
        req.setLeaveRequestId(102);
        req.setStatus(LeaveRequest.Status.APPROVED);
        req.setLeaveDate(LocalDate.now().plusDays(5));
        req.setShiftType(LeaveRequest.ShiftType.MORNING);

        LeaveRequestDAO.LeaveReviewDetail detail = new LeaveRequestDAO.LeaveReviewDetail(
                req, "NV001", "Nguyễn Văn A", "PT", null, "Manager B"
        );

        when(leaveRequestDAO.findReviewDetail(102)).thenReturn(detail);

        var result = service.approveRequest(1, 102);

        assertFalse(result.isSuccess());
        verify(leaveRequestDAO, never()).approve(any(), anyInt(), anyInt());
    }

    // ===== Kiểm thử từ chối đơn (rejectRequest - UC-15, CBR-85) =====

    @Test
    void rejectRequest_notBlockedByDeadline_whenOverdue() throws Exception {
        // Đơn quá hạn duyệt nhưng Manager vẫn có quyền từ chối, không bị hard block
        LeaveRequest req = new LeaveRequest();
        req.setLeaveRequestId(103);
        req.setStatus(LeaveRequest.Status.PENDING);
        req.setLeaveDate(LocalDate.now().minusDays(1));
        req.setSubmittedAt(LocalDateTime.now().minusDays(5));
        req.setShiftType(LeaveRequest.ShiftType.AFTERNOON);

        LeaveRequestDAO.LeaveReviewDetail detail = new LeaveRequestDAO.LeaveReviewDetail(
                req, "NV002", "Trần Thị B", "Receptionist", null, null
        );

        when(leaveRequestDAO.findReviewDetail(103)).thenReturn(detail);
        when(accountDAO.findById(1)).thenReturn(null);

        var result = service.rejectRequest(1, 103, "Lý do từ chối hợp lệ");

        assertFalse(result.isSuccess());
        assertFalse(result.getMessage().contains("quá hạn duyệt"));
    }

    // ===== Kiểm thử rút đơn (withdrawRequest - UC-14) =====

    @Test
    void withdrawRequest_fails_whenIdIsInvalid() {
        var resultZero = service.withdrawRequest(1, 0);
        assertFalse(resultZero.isSuccess());

        var resultNegative = service.withdrawRequest(1, -1);
        assertFalse(resultNegative.isSuccess());
    }
}
