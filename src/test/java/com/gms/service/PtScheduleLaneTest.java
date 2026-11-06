package com.gms.service;

import com.gms.dto.PtScheduleItem;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Kiểm tra xếp làn ô chồng giờ trên Lịch dạy (PtScheduleService.assignLanes); không dùng DB.
 * Lỗi cũ: 1 cặp ô chồng giờ làm MỌI ô trong ngày bị co. Mong đợi: chỉ cụm chồng giờ chia làn.
 */
class PtScheduleLaneTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 9);

    /** Khung trống (không có buổi) → blocking. */
    private static PtScheduleItem slot(String start, String end) {
        return new PtScheduleItem(1, DAY, LocalTime.parse(start), LocalTime.parse(end), "Free");
    }

    /** Buổi có khách với trạng thái cho trước (Cancelled = không blocking). */
    private static PtScheduleItem session(String start, String end, String status) {
        return new PtScheduleItem(10, status, 1, DAY, LocalTime.parse(start), LocalTime.parse(end),
                "Gói PT", 1, 12, "Khách", "0900000000");
    }

    /** Ô không chồng ai giữ 1 làn (rộng đủ), cụm 19:00 + 20:00 chia 2 làn. */
    @Test
    void onlyOverlappingClusterIsSplit() {
        PtScheduleItem morning = slot("07:00", "08:30");
        PtScheduleItem evening1 = session("19:00", "20:30", "Scheduled");
        PtScheduleItem evening2 = session("20:00", "21:30", "Scheduled");
        List<PtScheduleItem> items = new ArrayList<>(List.of(morning, evening1, evening2));

        PtScheduleService.assignLanes(items);

        assertEquals(1, morning.getLaneCount());
        assertEquals(0, morning.getLane());
        assertEquals(2, evening1.getLaneCount());
        assertEquals(2, evening2.getLaneCount());
        assertEquals(0, evening1.getLane());
        assertEquals(1, evening2.getLane());
    }

    /** Khung liền nhau (kết thúc 17:00 = bắt đầu 17:00) không tính là chồng. */
    @Test
    void touchingSlotsDoNotOverlap() {
        PtScheduleItem first = slot("15:30", "17:00");
        PtScheduleItem second = slot("17:00", "18:30");
        PtScheduleService.assignLanes(new ArrayList<>(List.of(first, second)));

        assertEquals(1, first.getLaneCount());
        assertEquals(1, second.getLaneCount());
    }

    /** Buổi đã hủy cùng giờ với khung mới → khung đang chiếm giờ ở làn 0, buổi đã hủy dạt sang làn 1. */
    @Test
    void cancelledSessionGoesToRightLane() {
        PtScheduleItem cancelled = session("19:30", "21:00", "Cancelled");
        PtScheduleItem freeSlot = slot("19:30", "21:00");
        PtScheduleService.assignLanes(new ArrayList<>(List.of(cancelled, freeSlot)));

        assertEquals(0, freeSlot.getLane());
        assertEquals(1, cancelled.getLane());
        assertEquals(2, cancelled.getLaneCount());
    }

    /** Làn đã trống được dùng lại: A 17:00–18:30, B 17:30–19:00, C 18:30–20:00 → C quay về làn 0, cụm 2 làn. */
    @Test
    void freedLaneIsReused() {
        PtScheduleItem a = slot("17:00", "18:30");
        PtScheduleItem b = slot("17:30", "19:00");
        PtScheduleItem c = slot("18:30", "20:00");
        PtScheduleService.assignLanes(new ArrayList<>(List.of(a, b, c)));

        assertEquals(0, a.getLane());
        assertEquals(1, b.getLane());
        assertEquals(0, c.getLane());
        assertEquals(2, c.getLaneCount());
    }

    /** Ngày không có ô → không lỗi. */
    @Test
    void emptyDayIsFine() {
        assertDoesNotThrow(() -> PtScheduleService.assignLanes(new ArrayList<>()));
    }
}
