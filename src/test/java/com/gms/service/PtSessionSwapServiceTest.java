package com.gms.service;

import com.gms.dto.PtSessionSwapInfo;
import com.gms.dto.PtSwapSlotView;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PtSessionSwapServiceTest {
    private PtSessionSwapInfo source() {
        PtSessionSwapInfo info = new PtSessionSwapInfo();
        info.setSubscriptionType("PTSingle");
        info.setSubscriptionStatus("Active");
        info.setSessionStatus("Scheduled");
        info.setSlotDate(LocalDate.of(2026,10,16));
        info.setStartTime(LocalTime.of(18,0));
        info.setEndTime(LocalTime.of(19,30));
        info.setTimeSlotId(10);
        info.setPackageStartDate(LocalDate.of(2026,10,1));
        info.setPackageEndDate(LocalDate.of(2026,10,31));
        info.setMaxSwaps(6);
        info.setSwapsUsed(3);
        return info;
    }

    @Test void usesPackageSnapshotRatherThanHardcodedThree() {
        assertNull(PtSessionSwapService.sourceError(source(),LocalDateTime.of(2026,10,14,10,0)));
        var info=source(); info.setSwapsUsed(6);
        assertNotNull(PtSessionSwapService.sourceError(info,LocalDateTime.of(2026,10,14,10,0)));
    }

    @Test void refusesSourceAtItsExactStartAndAfterStart() {
        var info=source();
        assertNull(PtSessionSwapService.sourceError(info,LocalDateTime.of(2026,10,16,17,59,59)));
        assertNotNull(PtSessionSwapService.sourceError(info,LocalDateTime.of(2026,10,16,18,0)));
        assertNotNull(PtSessionSwapService.sourceError(info,LocalDateTime.of(2026,10,16,18,1)));
    }

    @Test void refusesSingleSessionAndFinalStatus() {
        var info=source(); info.setSubscriptionType("SingleSession");
        assertNotNull(PtSessionSwapService.sourceError(info,LocalDateTime.of(2026,10,14,10,0)));
        info=source(); info.setSessionStatus("Cancelled");
        assertNotNull(PtSessionSwapService.sourceError(info,LocalDateTime.of(2026,10,14,10,0)));
    }

    @Test void opensFollowingWeekOnlyFromSaturdayIncludingSunday() {
        assertEquals(LocalDate.of(2026,10,18),PtSessionSwapService.lastSelectableDate(LocalDate.of(2026,10,16)));
        assertEquals(LocalDate.of(2026,10,25),PtSessionSwapService.lastSelectableDate(LocalDate.of(2026,10,17)));
        assertEquals(LocalDate.of(2026,10,25),PtSessionSwapService.lastSelectableDate(LocalDate.of(2026,10,18)));
        assertEquals(LocalDate.of(2026,10,25),PtSessionSwapService.lastSelectableDate(LocalDate.of(2026,10,19)));
    }

    @Test void weekendMustBeWithinTermAndCurrentBookingWindow() {
        var info=source(); var now=LocalDateTime.of(2026,10,14,10,0);
        assertTrue(PtSessionSwapService.isAllowedDate(info,slot(18,8,90),now));
        assertFalse(PtSessionSwapService.isAllowedDate(info,slot(16,8,90),now));
        assertFalse(PtSessionSwapService.isAllowedDate(info,slot(25,8,90),now));
        info.setPackageEndDate(LocalDate.of(2026,10,17));
        assertFalse(PtSessionSwapService.isAllowedDate(info,slot(18,8,90),now));
    }

    @Test void preservesSessionDurationAndExcludesAlreadyStartedTarget() {
        var info=source(); var now=LocalDateTime.of(2026,10,18,8,0);
        assertFalse(PtSessionSwapService.isAllowedDate(info,slot(18,8,90),now));
        assertFalse(PtSessionSwapService.isAllowedDate(info,slot(18,9,60),now));
        assertTrue(PtSessionSwapService.isAllowedDate(info,slot(18,9,90),now));
    }

    private PtSwapSlotView slot(int day,int hour,int duration) {
        LocalTime start=LocalTime.of(hour,0);
        return new PtSwapSlotView(11,LocalDate.of(2026,10,day),start,start.plusMinutes(duration));
    }
}
