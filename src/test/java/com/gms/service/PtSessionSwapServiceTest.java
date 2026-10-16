package com.gms.service;

import com.gms.dto.PtSessionInfo;
import com.gms.dto.PtSwapSlotView;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PtSessionSwapServiceTest {

    /** Buổi gốc: Thứ 6 16/10/2026 18:00–19:30, gói 01/10–31/10, đã đổi 3/6 lượt. */
    private PtSessionInfo source() {
        PtSessionInfo info = new PtSessionInfo();
        info.setSubscriptionType("PTSingle");
        info.setSubscriptionStatus("Active");
        info.setSessionStatus("Scheduled");
        info.setSlotDate(LocalDate.of(2026, 10, 16));
        info.setStartTime(LocalTime.of(18, 0));
        info.setEndTime(LocalTime.of(19, 30));
        info.setTimeSlotId(10);
        info.setPackageStartDate(LocalDate.of(2026, 10, 1));
        info.setPackageEndDate(LocalDate.of(2026, 10, 31));
        info.setMaxSwaps(6);
        info.setSwapsUsed(3);
        return info;
    }

    @Test
    void usesPackageSnapshotRatherThanHardcodedThree() {
        assertNull(PtSessionSwapService.checkSourceSession(source(), LocalDateTime.of(2026, 10, 14, 10, 0)));
        PtSessionInfo info = source();
        info.setSwapsUsed(6);
        assertNotNull(PtSessionSwapService.checkSourceSession(info, LocalDateTime.of(2026, 10, 14, 10, 0)));
    }

    @Test
    void refusesSourceAtItsExactStartAndAfterStart() {
        PtSessionInfo info = source();
        assertNull(PtSessionSwapService.checkSourceSession(info, LocalDateTime.of(2026, 10, 16, 17, 59, 59)));
        assertNotNull(PtSessionSwapService.checkSourceSession(info, LocalDateTime.of(2026, 10, 16, 18, 0)));
        assertNotNull(PtSessionSwapService.checkSourceSession(info, LocalDateTime.of(2026, 10, 16, 18, 1)));
    }

    @Test
    void refusesSingleSessionAndFinalStatus() {
        PtSessionInfo info = source();
        info.setSubscriptionType("SingleSession");
        assertNotNull(PtSessionSwapService.checkSourceSession(info, LocalDateTime.of(2026, 10, 14, 10, 0)));
        info = source();
        info.setSessionStatus("Cancelled");
        assertNotNull(PtSessionSwapService.checkSourceSession(info, LocalDateTime.of(2026, 10, 14, 10, 0)));
    }

    /** [CHOT 17/10] Được đổi sang BẤT KỲ thứ 7 / CN nào còn trong hạn gói, không chỉ tuần này. */
    @Test
    void anyWeekendInsidePackageTermIsAllowed() {
        PtSessionInfo info = source();                                   // gói 01/10 – 31/10
        LocalDateTime now = LocalDateTime.of(2026, 10, 14, 10, 0);       // thứ 4

        assertTrue(PtSessionSwapService.isAllowedSlot(info, slot(18, 8, 90), now));   // CN tuần này
        assertTrue(PtSessionSwapService.isAllowedSlot(info, slot(25, 8, 90), now));   // CN tuần sau
        assertTrue(PtSessionSwapService.isAllowedSlot(info, slot(31, 8, 90), now));   // T7 cuối gói
        assertFalse(PtSessionSwapService.isAllowedSlot(info, slot(16, 8, 90), now));  // thứ 6 → không phải cuối tuần
        assertFalse(PtSessionSwapService.isAllowedSlot(info, slot(11, 8, 90), now));  // CN đã qua

        info.setPackageEndDate(LocalDate.of(2026, 10, 24));
        assertFalse(PtSessionSwapService.isAllowedSlot(info, slot(25, 8, 90), now));  // quá hạn gói
    }

    @Test
    void preservesSessionDurationAndExcludesAlreadyStartedTarget() {
        PtSessionInfo info = source();
        LocalDateTime now = LocalDateTime.of(2026, 10, 18, 8, 0);
        assertFalse(PtSessionSwapService.isAllowedSlot(info, slot(18, 8, 90), now));
        assertFalse(PtSessionSwapService.isAllowedSlot(info, slot(18, 9, 60), now));
        assertTrue(PtSessionSwapService.isAllowedSlot(info, slot(18, 9, 90), now));
    }

    private PtSwapSlotView slot(int day, int hour, int duration) {
        LocalTime start = LocalTime.of(hour, 0);
        return new PtSwapSlotView(11, LocalDate.of(2026, 10, day), start, start.plusMinutes(duration));
    }
}
