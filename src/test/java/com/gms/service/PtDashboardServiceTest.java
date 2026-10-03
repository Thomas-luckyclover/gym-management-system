package com.gms.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

/**
 * Kiểm tra 2 luật thuần của PT Dashboard — không cần DB:
 *   - Cửa sổ điểm danh (BR-54, P-07 = 24 giờ). Buổi mẫu 29/09 18:00–19:30 → điểm danh được tới 30/09 19:30.
 *   - Buổi nào được tính hoa hồng ("05_Dashboard_Rules.md" mục 3.2, CBR-80/81/82, CBR-55/57).
 */
class PtDashboardServiceTest {

    private static final LocalDate DAY = LocalDate.of(2026, 9, 29);
    private static final LocalTime START = LocalTime.of(18, 0);
    private static final LocalTime END = LocalTime.of(19, 30);
    private static final int WINDOW_HOURS = 24;

    private boolean isOpenAt(LocalDateTime now) {
        return PtDashboardService.isAttendanceOpen(DAY, START, END, now, WINDOW_HOURS);
    }

    @Test
    void notOpenBeforeSessionStarts() {
        assertFalse(isOpenAt(LocalDateTime.of(2026, 9, 29, 17, 59)));
    }

    @Test
    void openFromStartTimeInclusive() {
        assertTrue(isOpenAt(LocalDateTime.of(2026, 9, 29, 18, 0)));
        assertTrue(isOpenAt(LocalDateTime.of(2026, 9, 29, 19, 0)));
    }

    @Test
    void stillOpenUntilWindowHoursAfterEnd() {
        assertTrue(isOpenAt(LocalDateTime.of(2026, 9, 30, 10, 0)));
        assertTrue(isOpenAt(LocalDateTime.of(2026, 9, 30, 19, 30)));
    }

    @Test
    void closedAfterWindow() {
        assertFalse(isOpenAt(LocalDateTime.of(2026, 9, 30, 19, 31)));
    }

    @Test
    void completedAndMissedAlwaysEarnCommission() {
        assertTrue(PtDashboardService.isCommissionable("Completed", null));
        assertTrue(PtDashboardService.isCommissionable("Missed", null));
    }

    @Test
    void cancelledEarnsOnlyWhenPtCancelledWithRefundOrCustomerCancelled() {
        assertTrue(PtDashboardService.isCommissionable("Cancelled", "ByPt"));
        assertTrue(PtDashboardService.isCommissionable("Cancelled", "ByCustomer"));
        // Phòng đóng cửa (CBR-57) / gói PT hết hạn (CBR-55): CancelType GymClosed / PackageExpired → không tính
        assertFalse(PtDashboardService.isCommissionable("Cancelled", "GymClosed"));
        assertFalse(PtDashboardService.isCommissionable("Cancelled", "PackageExpired"));
    }

    @Test
    void scheduledNotYetCounted() {
        assertFalse(PtDashboardService.isCommissionable("Scheduled", null));
    }
}
