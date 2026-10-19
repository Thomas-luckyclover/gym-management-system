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

    @Test
    void invalidAndExtremeViewDatesFallBackToToday() {
        LocalDate today = NOW.toLocalDate();
        for (String input : new String[]{null, "", "abc", "2026-02-30", "0001-01-01", "+999999999-12-31", "9999-12-31"}) {
            assertEquals(today, MyPtSessionService.parseViewDate(input, today));
        }
        assertEquals(LocalDate.of(2026, 12, 31), MyPtSessionService.parseViewDate("2026-12-31", today));
    }

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
        "06:00, 0", "08:59, 0", "09:00, 0", "11:59, 0",
        "12:00, 1", "14:59, 1", "15:00, 1", "17:59, 1",
        "18:00, 2", "20:30, 2", "22:00, 2"
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

    // ---------------------------------------------------------------- calendarUrl

    @Test
    void calendarLinkUsesUtcTimeAndEncodedText() {
        MyPtSessionRow row = scheduled(LocalDate.of(2026, 10, 21), 18, 0);   // 18:00–19:30 giờ VN
        row.setPtName("Trần Quốc Bảo");
        row.setPackageName("PT cá nhân 2 tháng");
        row.setSessionNo(33);
        row.setTotalSessions(45);

        String url = MyPtSessionService.calendarUrl(row);

        assertTrue(url.startsWith("https://calendar.google.com/calendar/render?action=TEMPLATE"));
        assertTrue(url.contains("&dates=20261021T110000Z/20261021T123000Z"));   // VN = UTC+7
        assertTrue(url.contains("&text=Bu%E1%BB%95i%20PT%20v%E1%BB%9Bi%20Tr%E1%BA%A7n"));
        assertTrue(url.contains("bu%E1%BB%95i%2033%2F45"));                       // "buổi 33/45"
    }

    // ---------------------------------------------------------------- applyStatus

    @Test
    void endedFlagUsesExactEndTimeAndKeepsStoredResult() {
        MyPtSessionRow row = scheduled(NOW.toLocalDate(), 12, 50); // Kết thúc đúng 14:20.
        row.setStatus("CheckedIn");
        MyPtSessionService.applyStatus(row, NOW.minusNanos(1), null);
        assertFalse(row.isEnded());
        MyPtSessionService.applyStatus(row, NOW, null);
        assertTrue(row.isEnded());
        assertEquals("checkedIn", row.getStatusKey());
        assertEquals("Đã check-in", row.getStatusLabel());
    }

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
