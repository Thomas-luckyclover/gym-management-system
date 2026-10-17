package com.gms.service;

import com.gms.entity.LeaveRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kiểm thử đơn vị cho LeaveRequestService (BF-05, UC-14, UC-15, JOB-07).
 */
class LeaveRequestServiceTest {

    private final LeaveRequestService service = new LeaveRequestService();

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
}
