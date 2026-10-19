package com.gms.service;

import com.gms.dto.MyPtSessionRow;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

/** Kiểm tra các hàm tính toán của thời khóa biểu "Lịch tập của tôi" (không cần DB). */
class MyPtSessionServiceTest {

    /** "Bây giờ" cố định: Thứ 2, 19/10/2026 lúc 14:20. */
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 19, 14, 20);

    // ---------------------------------------------------------------- weekStartOf

    @ParameterizedTest
    @CsvSource({
        "2026-10-19, 2026-10-19",   // Thứ 2 → chính nó
        "2026-10-22, 2026-10-19",   // Thứ 5
        "2026-10-25, 2026-10-19",   // Chủ nhật vẫn thuộc tuần bắt đầu Thứ 2 trước đó
        "2026-11-01, 2026-10-26"    // qua tháng
    })
    void weekStartIsMondayOfSameWeek(LocalDate anyDay, LocalDate expectedMonday) {
        assertEquals(expectedMonday, MyPtSessionService.weekStartOf(anyDay));
    }

    // ---------------------------------------------------------------- bandOf

    @ParameterizedTest
    @CsvSource({
        "06:00, 0", "08:59, 0",     // Sáng sớm
        "09:00, 1", "11:45, 1",     // Sáng
        "12:00, 2", "14:59, 2",     // Trưa
        "15:00, 3", "17:59, 3",     // Chiều
        "18:00, 4", "20:30, 4"      // Tối
    })
    void startTimeFallsIntoCorrectBand(LocalTime startTime, int expectedBand) {
        assertEquals(expectedBand, MyPtSessionService.bandOf(startTime));
    }

    // ---------------------------------------------------------------- zaloUrl

    @ParameterizedTest
    @CsvSource({
        "0912345678,     https://zalo.me/0912345678",
        "0912 345 678,   https://zalo.me/0912345678",
        "+84912345678,   https://zalo.me/0912345678",
        "84912345678,    https://zalo.me/0912345678"
    })
    void phoneBecomesZaloLink(String phone, String expected) {
        assertEquals(expected, MyPtSessionService.zaloUrl(phone));
    }

    @Test
    void noPhoneMeansNoZaloLink() {
        assertNull(MyPtSessionService.zaloUrl(null));
        assertNull(MyPtSessionService.zaloUrl("  "));
    }

    // ---------------------------------------------------------------- applyStatus

    @Test
    void scheduledTodayNotStartedIsToday() {
        MyPtSessionRow row = scheduled(LocalDate.of(2026, 10, 19), 18, 0);
        MyPtSessionService.applyStatus(row, NOW, null);
        assertEquals("today", row.getStatusKey());
        assertEquals("Hôm nay", row.getStatusLabel());
    }

    @Test
    void scheduledTodayAlreadyStartedIsInProgress() {
        MyPtSessionRow row = scheduled(LocalDate.of(2026, 10, 19), 14, 0);   // 14:00–15:30, bây giờ 14:20
        MyPtSessionService.applyStatus(row, NOW, null);
        assertEquals("today", row.getStatusKey());
        assertEquals("Đang diễn ra", row.getStatusLabel());
    }

    @Test
    void scheduledLaterDayIsUpcoming() {
        MyPtSessionRow row = scheduled(LocalDate.of(2026, 10, 21), 18, 0);
        MyPtSessionService.applyStatus(row, NOW, null);
        assertEquals("upcoming", row.getStatusKey());
    }

    @Test
    void scheduledWithWarningIsWarnAndKeepsReason() {
        MyPtSessionRow row = scheduled(LocalDate.of(2026, 10, 20), 18, 0);
        MyPtSessionService.applyStatus(row, NOW, "Phòng tập nghỉ ngày này.");
        assertEquals("warn", row.getStatusKey());
        assertEquals("Phòng tập nghỉ ngày này.", row.getExplanation());
    }

    @Test
    void scheduledAlreadyEndedIsPendingAndIgnoresWarning() {
        MyPtSessionRow row = scheduled(LocalDate.of(2026, 10, 19), 9, 0);   // 09:00–10:30 đã qua
        MyPtSessionService.applyStatus(row, NOW, "bỏ qua");
        assertEquals("pending", row.getStatusKey());
    }

    @Test
    void storedResultsAreReadAsIs() {
        MyPtSessionRow checkedIn = scheduled(LocalDate.of(2026, 10, 16), 18, 0);
        checkedIn.setStatus("CheckedIn");
        MyPtSessionService.applyStatus(checkedIn, NOW, null);
        assertEquals("checkedIn", checkedIn.getStatusKey());

        MyPtSessionRow missed = scheduled(LocalDate.of(2026, 10, 13), 18, 0);
        missed.setStatus("Missed");
        missed.setMissedReason("NoCheckIn");
        MyPtSessionService.applyStatus(missed, NOW, null);
        assertEquals("missed", missed.getStatusKey());
        assertTrue(missed.getExplanation().contains("không check-in"));

        MyPtSessionRow cancelled = scheduled(LocalDate.of(2026, 10, 9), 18, 0);
        cancelled.setStatus("Cancelled");
        cancelled.setCancelType("ByPt");
        MyPtSessionService.applyStatus(cancelled, NOW, null);
        assertEquals("cancelled", cancelled.getStatusKey());
        assertEquals("PT đã hủy buổi này.", cancelled.getExplanation());
    }

    /** Buổi PT cá nhân 90 phút, trạng thái Scheduled. */
    private static MyPtSessionRow scheduled(LocalDate date, int hour, int minute) {
        MyPtSessionRow row = new MyPtSessionRow();
        row.setStatus("Scheduled");
        row.setType("PTSingle");
        row.setSlotDate(date);
        row.setStartTime(LocalTime.of(hour, minute));
        row.setEndTime(LocalTime.of(hour, minute).plusMinutes(90));
        return row;
    }
}
