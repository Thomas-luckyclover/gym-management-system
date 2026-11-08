package com.gms.service;

import com.gms.constant.Messages;
import com.gms.dto.PtHandleDay;
import com.gms.dto.PtHandleSlot;
import com.gms.dto.PtHandleView;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Luật của hộp Xử lý buổi (không đọc DB). Buổi mẫu: Thứ 3 27/10/2026 18:00–19:30, gói 07/09 → 07/11. */
class PtSessionHandleServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 25, 9, 0);

    private PtHandleView session() {
        PtHandleView session = new PtHandleView();
        session.setSessionId(120);
        session.setSessionStatus("Scheduled");
        session.setTimeSlotId(500);
        session.setSlotDate(LocalDate.of(2026, 10, 27));
        session.setStartTime(LocalTime.of(18, 0));
        session.setEndTime(LocalTime.of(19, 30));
        session.setTermStart(LocalDate.of(2026, 9, 7));
        session.setTermEnd(LocalDate.of(2026, 11, 7));
        return session;
    }

    private PtHandleSlot slot(int id, String date, String start) {
        PtHandleSlot slot = new PtHandleSlot();
        slot.setTimeSlotId(id);
        slot.setSlotDate(LocalDate.parse(date));
        slot.setStartTime(LocalTime.parse(start));
        slot.setEndTime(LocalTime.parse(start).plusMinutes(90));
        slot.setHasGymAccess(true);
        return slot;
    }

    @Test
    void validSlotHasNoReason() {
        assertNull(PtSessionHandleService.whyNotAllowed(session(), slot(812, "2026-10-29", "06:00"), NOW));
    }

    @Test
    void rejectsCurrentSlotPastSlotAndOutsideTerm() {
        assertNotNull(PtSessionHandleService.whyNotAllowed(session(), slot(500, "2026-10-29", "06:00"), NOW));
        assertTrue(PtSessionHandleService.whyNotAllowed(session(), slot(1, "2026-10-25", "08:30"), NOW).contains("đã qua"));
        assertTrue(PtSessionHandleService.whyNotAllowed(session(), slot(2, "2026-11-08", "08:00"), NOW).contains("hạn gói"));
    }

    @Test
    void rejectsEachFlagFromSqlExceptMissingMembership() {
        PtHandleSlot closed = slot(3, "2026-10-29", "06:00");
        closed.setGymClosed(true);
        PtHandleSlot leave = slot(4, "2026-10-29", "06:00");
        leave.setPtOnLeave(true);
        PtHandleSlot busy = slot(5, "2026-10-29", "06:00");
        busy.setCustomerBusy(true);
        PtHandleSlot noAccess = slot(6, "2026-10-29", "06:00");
        noAccess.setHasGymAccess(false);

        assertTrue(PtSessionHandleService.whyNotAllowed(session(), closed, NOW).contains("phòng nghỉ"));
        assertTrue(PtSessionHandleService.whyNotAllowed(session(), leave, NOW).contains("nghỉ"));
        assertTrue(PtSessionHandleService.whyNotAllowed(session(), busy, NOW).contains("trùng giờ"));
        assertNull(PtSessionHandleService.whyNotAllowed(session(), noAccess, NOW));   // chỉ cảnh báo vàng, không chặn
    }

    @Test
    void sessionMustBeMineScheduledAndNotStarted() {
        assertNotNull(PtSessionHandleService.whySessionLocked(null, NOW));
        assertNull(PtSessionHandleService.whySessionLocked(session(), NOW));

        PtHandleView checkedIn = session();
        checkedIn.setSessionStatus("CheckedIn");
        assertEquals(Messages.MSG91_SESSION_CANNOT_CHANGE, PtSessionHandleService.whySessionLocked(checkedIn, NOW));

        LocalDateTime atStart = LocalDateTime.of(2026, 10, 27, 18, 0);
        assertEquals(Messages.MSG91_SESSION_CANNOT_CHANGE, PtSessionHandleService.whySessionLocked(session(), atStart));
    }

    @Test
    void cancelNeedsReasonAndRefundTick() {
        assertNotNull(PtSessionHandleService.checkCancel(null, true));
        assertNotNull(PtSessionHandleService.checkCancel("ốm", true));
        assertNotNull(PtSessionHandleService.checkCancel("x".repeat(501), true));
        assertNotNull(PtSessionHandleService.checkCancel("PT bị ốm đột xuất", false));
        assertNull(PtSessionHandleService.checkCancel("PT bị ốm đột xuất", true));
    }

    @Test
    void parsesSlotIdAndRoundsCommission() {
        assertEquals(812, PtSessionHandleService.parseSlotId(" 812 "));
        assertNull(PtSessionHandleService.parseSlotId("abc"));
        assertNull(PtSessionHandleService.parseSlotId("0"));
        assertNull(PtSessionHandleService.parseSlotId(null));
        assertEquals(156100, PtSessionHandleService.transferCommission(223000, 70));
        assertEquals(1, PtSessionHandleService.transferCommission(3, 33));   // 0.99 → 1
    }

    @Test
    void groupsSlotsByDayInOrder() {
        List<PtHandleDay> days = PtSessionHandleService.groupByDay(List.of(
                slot(1, "2026-10-29", "06:00"), slot(2, "2026-10-29", "18:00"), slot(3, "2026-11-01", "08:00")));
        assertEquals(2, days.size());
        assertEquals("Thứ 5 · 29/10", days.get(0).getLabel());
        assertEquals(2, days.get(0).getSlots().size());
        assertEquals("Chủ nhật · 01/11", days.get(1).getLabel());
    }
}
