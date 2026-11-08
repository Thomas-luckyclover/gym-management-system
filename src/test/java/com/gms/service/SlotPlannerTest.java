package com.gms.service;

import com.gms.service.SlotPlanner.Busy;
import com.gms.util.TimeRange;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Kiểm tra thuật toán trùng giờ + giờ trống (không cần DB). */
class SlotPlannerTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 26);            // Thứ 2
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 21, 17, 0);

    private static TimeRange r(String start, String end) {
        return new TimeRange(DAY, LocalTime.parse(start), LocalTime.parse(end));
    }

    // ---------------------------------------------------------------- TimeRange.overlaps

    @Test
    void sameRangeOverlaps() {
        assertTrue(r("18:00", "19:30").overlaps(r("18:00", "19:30")));
    }

    @Test
    void partialOverlapAtEitherEdge() {
        assertTrue(r("18:00", "19:30").overlaps(r("18:15", "19:45")));
        assertTrue(r("18:00", "19:30").overlaps(r("17:00", "18:30")));
        assertTrue(r("18:00", "19:30").overlaps(r("18:30", "19:00")));   // nằm trọn bên trong
    }

    @Test
    void backToBackDoesNotOverlap() {
        assertFalse(r("18:00", "19:30").overlaps(r("19:30", "21:00")));
        assertFalse(r("18:00", "19:30").overlaps(r("16:30", "18:00")));
    }

    @Test
    void differentDaysNeverOverlap() {
        TimeRange other = new TimeRange(DAY.plusDays(1), LocalTime.of(18, 0), LocalTime.of(19, 30));
        assertFalse(r("18:00", "19:30").overlaps(other));
    }

    @Test
    void mixedDurationsStillDetected() {
        // slot cũ 90 phút, slot mới 60 phút (P-06 đổi) — chỉ so giờ bắt đầu sẽ bỏ sót
        assertTrue(r("18:00", "19:30").overlaps(r("19:00", "20:00")));
    }

    // ---------------------------------------------------------------- grid

    @Test
    void gridAccepts15MinuteMarksOnly() {
        assertTrue(SlotPlanner.onGrid(LocalTime.of(18, 45)));
        assertFalse(SlotPlanner.onGrid(LocalTime.of(18, 10)));
        assertFalse(SlotPlanner.onGrid(LocalTime.of(18, 0, 30)));
    }

    @Test
    void roundUpToStep() {
        assertEquals(LocalTime.of(5, 30), SlotPlanner.roundUpToGrid(LocalTime.of(5, 20), 30));
        assertEquals(LocalTime.of(5, 30), SlotPlanner.roundUpToGrid(LocalTime.of(5, 30), 30));
    }

    // ---------------------------------------------------------------- freeStarts

    @Test
    void freeStartsRespectOpeningHoursAndDuration() {
        List<LocalTime> starts = SlotPlanner.freeStarts(DAY, LocalTime.of(20, 0), LocalTime.of(22, 0),
                90, 30, List.of(), NOW);
        assertEquals(List.of(LocalTime.of(20, 0), LocalTime.of(20, 30)), starts);   // 21:00 + 90' > 22:00
    }

    @Test
    void freeStartsSkipBusyRanges() {
        List<Busy> busy = List.of(new Busy(r("18:15", "19:45"), "Bạn đã có slot"));
        List<LocalTime> starts = SlotPlanner.freeStarts(DAY, LocalTime.of(16, 0), LocalTime.of(22, 0),
                90, 30, busy, NOW);
        // 17:00–18:30, 17:30–19:00, 18:00–19:30, 18:30–20:00, 19:00–20:30 đều chồng 18:15–19:45
        assertEquals(List.of(LocalTime.of(16, 0), LocalTime.of(16, 30), LocalTime.of(20, 0), LocalTime.of(20, 30)),
                starts);
    }

    @Test
    void closedDayHasNoFreeStart() {
        assertTrue(SlotPlanner.freeStarts(DAY, null, null, 90, 30, List.of(), NOW).isEmpty());
    }

    @Test
    void pastTimesAreSkipped() {
        LocalDateTime noon = DAY.atTime(12, 10);
        List<LocalTime> starts = SlotPlanner.freeStarts(DAY, LocalTime.of(11, 0), LocalTime.of(15, 0),
                90, 30, List.of(), noon);
        assertEquals(LocalTime.of(12, 30), starts.get(0));
    }

    @Test
    void conflictsReturnReasons() {
        List<Busy> busy = List.of(new Busy(r("17:30", "19:00"), "Khách có buổi PT đôi"),
                new Busy(r("20:00", "21:30"), "Bạn có buổi"));
        List<Busy> hit = SlotPlanner.conflicts(r("18:00", "19:30"), busy);
        assertEquals(1, hit.size());
        assertEquals("Khách có buổi PT đôi", hit.get(0).getReason());
    }
}
