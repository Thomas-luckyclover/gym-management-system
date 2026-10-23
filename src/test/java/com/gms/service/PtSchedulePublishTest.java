package com.gms.service;

import com.gms.dto.HourException;
import com.gms.dto.LeaveInfo;
import com.gms.dto.PtScheduleItem;
import com.gms.dto.PtSlotDraft;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** Kiểm tra thuần nghiệp vụ trước công bố, không cần DB. */
class PtSchedulePublishTest {
    private static final LocalDate DATE = LocalDate.of(2026, 10, 23);
    private static final LocalDateTime NOW = DATE.atTime(7, 0);
    private static final LocalTime[] HOURS = {LocalTime.of(5, 30), LocalTime.of(22, 0)};

    @Test
    void rejectsMissingOrUnpairedInputs() {
        assertThrows(IllegalArgumentException.class, () -> PtScheduleService.buildDrafts(null, null, 90));
        assertThrows(IllegalArgumentException.class, () -> PtScheduleService.buildDrafts(new String[]{"2026-10-23"}, new String[0], 90));
        assertFalse(new PtScheduleService().publish(15, null, null, null, 90, NOW).isSuccess());
        assertFalse(new PtScheduleService().publish(15, new String[0], null, new String[0], 90, NOW).isSuccess());
    }

    @Test
    void parsesRemoveIdsSkippingDuplicatesAndRejectsGarbage() {
        assertEquals(List.of(), PtScheduleService.parseRemoveIds(null));
        assertEquals(List.of(57, 63), PtScheduleService.parseRemoveIds(new String[]{"57", "63", "57"}));
        assertThrows(IllegalArgumentException.class, () -> PtScheduleService.parseRemoveIds(new String[]{"abc"}));
        assertThrows(IllegalArgumentException.class, () -> PtScheduleService.parseRemoveIds(new String[]{"0"}));
        assertThrows(IllegalArgumentException.class, () -> PtScheduleService.parseRemoveIds(new String[]{null}));
    }

    @ParameterizedTest
    @ValueSource(strings = {"08:10", "08:00:01", "23:00", "abc", "08:00:00.001"})
    void rejectsOffGridOrOvernightStart(String start) {
        assertThrows(IllegalArgumentException.class, () -> drafts(start));
    }

    @Test
    void computesDurationAndSortsWithoutClientEndTime() {
        List<PtSlotDraft> rows = PtScheduleService.buildDrafts(new String[]{"2026-10-24", "2026-10-23"},
                new String[]{"10:00", "08:00"}, 75);
        assertEquals(DATE, rows.get(0).getSlotDate());
        assertEquals(LocalTime.of(9, 15), rows.get(0).getEndTime());
        assertThrows(IllegalArgumentException.class, () -> PtScheduleService.buildDrafts(
                new String[]{"+10000-01-01"}, new String[]{"08:00"}, 90));
    }

    @Test
    void acceptsTouchingEdgesButRejectsOverlappingDrafts() {
        assertDoesNotThrow(() -> validate(drafts("08:00", "09:30"), List.of(), List.of(), List.of(), NOW));
        assertThrows(IllegalArgumentException.class, () -> validate(drafts("08:00", "09:15"), List.of(), List.of(), List.of(), NOW));
        assertThrows(IllegalArgumentException.class, () -> validate(drafts("08:00", "08:00"), List.of(), List.of(), List.of(), NOW));
    }

    @Test
    void rejectsStartedTimeButAllowsTodayInFuture() {
        assertThrows(IllegalArgumentException.class, () -> validate(drafts("08:00"), List.of(), List.of(), List.of(), DATE.atTime(8, 0)));
        assertDoesNotThrow(() -> validate(drafts("08:15"), List.of(), List.of(), List.of(), DATE.atTime(8, 0)));
    }

    @Test
    void observesExceptionalHoursAndClosingBoundary() {
        HourException exception = new HourException();
        exception.setApplyDate(DATE);
        exception.setOpenTime(LocalTime.of(8, 0));
        exception.setCloseTime(LocalTime.of(12, 0));
        assertDoesNotThrow(() -> validate(drafts("10:30"), List.of(), List.of(), List.of(exception), NOW));
        assertThrows(IllegalArgumentException.class, () -> validate(drafts("10:45"), List.of(), List.of(), List.of(exception), NOW));
        exception.setClosedAllDay(true);
        exception.setOpenTime(null);
        exception.setCloseTime(null);
        assertThrows(IllegalArgumentException.class, () -> validate(drafts("08:00"), List.of(), List.of(), List.of(exception), NOW));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Pending", "Approved"})
    void pendingAndApprovedMorningLeaveBlockMorningOnly(String status) {
        LeaveInfo leave = new LeaveInfo();
        leave.setLeaveDate(DATE);
        leave.setShiftType("Morning");
        leave.setStatus(status);
        assertThrows(IllegalArgumentException.class, () -> validate(drafts("08:00"), List.of(), List.of(leave), List.of(), NOW));
        assertDoesNotThrow(() -> validate(drafts("12:00"), List.of(), List.of(leave), List.of(), NOW));
        leave.setStatus("Rejected");
        assertDoesNotThrow(() -> validate(drafts("08:00"), List.of(), List.of(leave), List.of(), NOW));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Free", "Held", "Booked"})
    void rejectsAnyOverlapWithPublishedSlots(String kind) {
        PtScheduleItem existing = existing(kind);
        assertThrows(IllegalArgumentException.class, () -> validate(drafts("08:15"), List.of(existing), List.of(), List.of(), NOW));
        assertDoesNotThrow(() -> validate(drafts("09:30"), List.of(existing), List.of(), List.of(), NOW));
    }

    @Test
    void reopensWithdrawnOnlyWithoutChangingHistoricalTimes() {
        PtScheduleItem existing = existing("Withdrawn");
        assertDoesNotThrow(() -> validate(drafts("08:00"), List.of(existing), List.of(), List.of(), NOW));
        existing.setEndTime(LocalTime.of(9, 0));
        assertThrows(IllegalArgumentException.class, () -> validate(drafts("08:00"), List.of(existing), List.of(), List.of(), NOW));
    }

    private static List<PtSlotDraft> drafts(String... starts) {
        String[] dates = new String[starts.length];
        java.util.Arrays.fill(dates, DATE.toString());
        return PtScheduleService.buildDrafts(dates, starts, 90);
    }

    private static PtScheduleItem existing(String kind) {
        PtScheduleItem row = new PtScheduleItem();
        row.setSlotDate(DATE);
        row.setStartTime(LocalTime.of(8, 0));
        row.setEndTime(LocalTime.of(9, 30));
        row.setKind(kind);
        return row;
    }

    private static void validate(List<PtSlotDraft> drafts, List<PtScheduleItem> existing, List<LeaveInfo> leaves,
            List<HourException> exceptions, LocalDateTime now) {
        PtScheduleService.validateDrafts(drafts, existing, leaves, exceptions, HOURS, now);
    }
}
