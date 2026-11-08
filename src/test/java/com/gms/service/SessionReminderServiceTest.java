package com.gms.service;

import com.gms.dao.SessionReminderDAO.Reminder;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** JOB-08: kiểm tra tiêu đề (dùng để chống gửi trùng) và câu nhắc theo từng lý do. Không cần DB. */
class SessionReminderServiceTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 27);

    @Test
    void titleContainsSessionDateSoEachDayIsRemindedOnce() {
        assertEquals("Nhắc buổi PT ngày 27/10/2026", SessionReminderService.buildTitle(DAY));
        assertNotEquals(SessionReminderService.buildTitle(DAY), SessionReminderService.buildTitle(DAY.plusDays(1)));
    }

    @Test
    void missingAccessMessageTellsTimePtAndWhatToBuy() {
        String text = SessionReminderService.buildContent(new Reminder(5, LocalTime.of(18, 0), "Đinh Công Sơn", false), DAY);
        assertTrue(text.startsWith("Ngày mai 27/10/2026 lúc 18:00 bạn có buổi PT với Đinh Công Sơn"));
        assertTrue(text.contains("chưa có membership hoặc vé ngày"));
        assertTrue(text.length() >= 10 && text.length() <= 500);   // DBR-15: nội dung 10–500 ký tự
    }

    @Test
    void frozenMessageExplainsFreeze() {
        String text = SessionReminderService.buildContent(new Reminder(5, LocalTime.of(7, 30), "Đinh Công Sơn", true), DAY);
        assertTrue(text.contains("lúc 07:30"));
        assertTrue(text.contains("đang bảo lưu"));
        assertFalse(text.contains("vé ngày"));   // đang bảo lưu thì vé ngày cũng không giúp vào được (JOB-04)
        assertTrue(text.contains("lễ tân"));
        assertTrue(text.length() <= 500);
    }
}
