package com.gms.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

/** Kiểm tra buổi chờ chốt và điều kiện hoa hồng PT. */
class PtDashboardServiceTest {

    private static final LocalDate DAY = LocalDate.of(2026, 9, 29);
    private static final LocalTime END = LocalTime.of(19, 30);

    private boolean isOpenAt(LocalDateTime now) {
        return PtDashboardService.isAwaitingClosure(DAY, END, now);
    }

    @Test
    void notPendingBeforeSessionStarts() {
        assertFalse(isOpenAt(LocalDateTime.of(2026, 9, 29, 17, 59)));
    }

    @Test
    void notPendingDuringSession() {
        assertFalse(isOpenAt(LocalDateTime.of(2026, 9, 29, 18, 0)));
        assertFalse(isOpenAt(LocalDateTime.of(2026, 9, 29, 19, 0)));
    }

    @Test
    void pendingAfterEndAndNextDay() {
        assertTrue(isOpenAt(LocalDateTime.of(2026, 9, 30, 10, 0)));
        assertTrue(isOpenAt(LocalDateTime.of(2026, 9, 30, 19, 30)));
    }

    @Test
    void stillPendingUntilJobCloses() {
        assertTrue(isOpenAt(LocalDateTime.of(2026, 9, 30, 19, 31)));
    }

    @Test
    void exactEndStillExpected() {
        assertFalse(isOpenAt(DAY.atTime(END)));
        assertTrue(isOpenAt(DAY.atTime(END).plusSeconds(1)));
    }

    @Test
    void checkedInAndMissedAlwaysEarnCommission() {
        assertTrue(PtDashboardService.isCommissionable("CheckedIn", null));
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
