package com.gms.service;

import com.gms.dto.HourException;
import com.gms.dto.LeaveInfo;
import com.gms.dto.PtSaleCustomer;
import com.gms.dto.PtSaleDay;
import com.gms.dto.PtSalePlan;
import com.gms.dto.PtScheduleItem;
import com.gms.util.TimeRange;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Luật màn Bán gói PT (không đọc DB). Kỳ mẫu bắt đầu Thứ 2 26/10/2026, giờ 18:00, 90 phút. */
class PtSaleServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 25, 9, 0);
    private static final LocalDate MON = LocalDate.of(2026, 10, 26);
    private static final LocalTime SIX_PM = LocalTime.of(18, 0);

    private PtSaleCustomer customer() {
        PtSaleCustomer c = new PtSaleCustomer();
        c.setPersonId(50);
        c.setFullName("Nguyễn Văn An");
        c.setDateOfBirth(LocalDate.of(1995, 3, 12));
        c.setMembershipEnd(LocalDate.of(2027, 7, 16));
        return c;
    }

    private PtScheduleItem slot(int id, LocalDate date, String start, String kind) {
        PtScheduleItem s = new PtScheduleItem();
        s.setTimeSlotId(id);
        s.setSlotDate(date);
        s.setStartTime(LocalTime.parse(start));
        s.setEndTime(LocalTime.parse(start).plusMinutes(90));
        s.setKind(kind);
        return s;
    }

    @Test
    void customerRules() {
        assertNull(PtSaleService.whyCannotBuy(customer(), NOW.toLocalDate(), 10, 19));
        assertTrue(PtSaleService.whyCannotBuy(customer(), NOW.toLocalDate(), 10, 50).contains("chính mình"));

        PtSaleCustomer child = customer();
        child.setDateOfBirth(LocalDate.of(2017, 1, 1));
        assertTrue(PtSaleService.whyCannotBuy(child, NOW.toLocalDate(), 10, 19).contains("10 tuổi"));

        PtSaleCustomer noMembership = customer();
        noMembership.setMembershipEnd(null);
        assertTrue(PtSaleService.whyCannotBuy(noMembership, NOW.toLocalDate(), 10, 19).contains("gói thành viên"));

        PtSaleCustomer frozen = customer();
        frozen.setFrozen(true);
        assertTrue(PtSaleService.whyCannotBuy(frozen, NOW.toLocalDate(), 10, 19).contains("bảo lưu"));
    }

    @Test
    void candidateTimesComeFromPublishedFreeWeekdaySlotsInFirstTwoWeeks() {
        List<LocalTime> times = PtSaleService.candidateTimes(List.of(
                slot(1, MON, "18:00", "Free"),
                slot(2, MON.plusDays(1), "06:00", "Free"),
                slot(3, MON.plusDays(2), "08:00", "Booked"),       // đã có khách → không tính
                slot(4, MON.plusDays(5), "09:00", "Free"),         // Thứ 7 → không tính
                slot(5, MON.plusDays(20), "10:00", "Free")),       // ngoài 14 ngày đầu → không tính
                MON, 90, NOW);
        assertEquals(List.of(LocalTime.of(6, 0), SIX_PM), times);
    }

    @Test
    void unpublishedWeekdaysAreWeekdaysWithoutAnyFreeSlotInFirstTwoWeeks() {
        List<Integer> missing = PtSaleService.unpublishedWeekdays(List.of(
                slot(1, MON, "18:00", "Free"),                     // Thứ 2 có khung
                slot(2, MON.plusDays(2), "08:00", "Booked"),       // Thứ 4 chỉ có khung đã có khách → vẫn thiếu
                slot(3, MON.plusDays(11), "09:00", "Free"),        // Thứ 6 tuần 2 → có
                slot(4, MON.plusDays(15), "10:00", "Free")),       // Thứ 3 tuần 3, ngoài 14 ngày → không tính
                MON, NOW);
        assertEquals(List.of(2, 3, 4), missing);                   // Thứ 3, Thứ 4, Thứ 5
    }

    @Test
    void sessionsThatCreateANewSlotAreCounted() {
        List<PtSaleDay> days = List.of(
                new PtSaleDay(MON, "Thứ 2", SIX_PM, SIX_PM.plusMinutes(90), "book", null, null, null),
                new PtSaleDay(MON.plusDays(1), "Thứ 3", SIX_PM, SIX_PM.plusMinutes(90), "create", null, null, null),
                new PtSaleDay(MON.plusDays(8), "Thứ 3", SIX_PM, SIX_PM.plusMinutes(90), "create", null, null, null));
        assertEquals(2, PtSaleService.countNewSlots(days));        // 2 buổi "create" = PT chưa mở khung
        assertEquals(0, PtSaleService.countNewSlots(days.subList(0, 1)));
    }

    /** checkDay cho 1 ngày: tự lọc khung cùng ngày như buildPlan / checkRequested làm. */
    private PtSaleDay check(LocalDate date, LocalTime time, List<PtScheduleItem> slots, List<LeaveInfo> leaves,
            List<HourException> exceptions, List<TimeRange> busy, LocalDateTime now) {
        List<PtScheduleItem> sameDay = PtSaleService.slotsByDate(slots).getOrDefault(date, List.of());
        return PtSaleService.checkDay(date, time, 90, sameDay, leaves, exceptions, busy, now);
    }

    @Test
    void checkDayUsesFreeSlotCreatesMissingAndSkipsTakenTimes() {
        List<PtScheduleItem> slots = List.of(
                slot(10, MON, "18:00", "Free"),                    // T2: dùng khung đã công bố
                slot(11, MON.plusDays(1), "18:00", "Booked"),      // T3: PT đã có khách
                slot(12, MON.plusDays(2), "17:00", "Free"),        // T4: khung khác chồng giờ
                slot(13, MON.plusDays(3), "18:00", "Withdrawn"),   // T5: PT đã gỡ
                slot(14, MON.plusDays(4), "06:00", "Held"));       // T6 06:00: đơn khác đang giữ
        List<TimeRange> none = List.of();

        PtSaleDay monday = check(MON, SIX_PM, slots, List.of(), List.of(), none, NOW);
        assertEquals("book", monday.getAction());
        assertEquals(10, monday.getSlotId());
        assertEquals("PT đã có khách giờ này", check(MON.plusDays(1), SIX_PM, slots, List.of(), List.of(), none, NOW).getReason());
        assertEquals("PT có khung khác chồng giờ", check(MON.plusDays(2), SIX_PM, slots, List.of(), List.of(), none, NOW).getReason());
        assertEquals("PT đã gỡ khung giờ này", check(MON.plusDays(3), SIX_PM, slots, List.of(), List.of(), none, NOW).getReason());
        assertEquals("create", check(MON.plusDays(4), SIX_PM, slots, List.of(), List.of(), none, NOW).getAction());
        assertEquals("Đang được giữ cho đơn khác",
                check(MON.plusDays(4), LocalTime.of(6, 0), slots, List.of(), List.of(), none, NOW).getReason());
        assertEquals(SIX_PM, PtSaleService.mostCommonStart(List.of(slot(1, MON, "18:00", "Held"),
                slot(2, MON.plusDays(1), "06:00", "Held"), slot(3, MON.plusDays(3), "18:00", "Held"))));
    }

    @Test
    void checkDaySkipsCustomerClashAndPastTimeButWarnsClosedDayAndLeave() {
        List<TimeRange> busy = List.of(new TimeRange(MON.plusDays(1), LocalTime.of(18, 30), LocalTime.of(20, 0)));
        HourException closed = new HourException();
        closed.setApplyDate(MON.plusDays(2));
        closed.setClosedAllDay(true);
        LeaveInfo leave = new LeaveInfo();
        leave.setLeaveDate(MON);
        leave.setShiftType("Afternoon");
        leave.setStatus("Approved");
        List<LeaveInfo> leaves = List.of(leave);
        List<HourException> exceptions = List.of(closed);

        PtSaleDay monday = check(MON, SIX_PM, List.of(), leaves, exceptions, busy, NOW);
        assertTrue(monday.isSession());
        assertTrue(monday.getWarning().contains("đơn nghỉ"));
        assertEquals("Khách có buổi khác trùng giờ", check(MON.plusDays(1), SIX_PM, List.of(), leaves, exceptions, busy, NOW).getReason());
        assertTrue(check(MON.plusDays(2), SIX_PM, List.of(), leaves, exceptions, busy, NOW).getWarning().contains("Phòng nghỉ"));

        LocalDateTime evening = LocalDateTime.of(2026, 10, 26, 19, 0);
        assertEquals("Đã qua giờ", check(MON, SIX_PM, List.of(), List.of(), List.of(), List.of(), evening).getReason());
    }

    @Test
    void planListsEveryWeekdayWithPatternTimesAndOneOffSlots() {
        LocalDate end = MON.plusDays(11);   // 2 tuần: T2 26/10 → T6 06/11 = 10 ngày T2–T6
        List<PtScheduleItem> slots = List.of(
                slot(20, MON.plusDays(2), "18:00", "Booked"),      // T4 tuần 1: 18:00 đã có khách
                slot(21, MON.plusDays(3), "14:00", "Free"));       // T5 tuần 1: PT mở thêm riêng 14:00
        PtSalePlan plan = PtSaleService.buildPlan(MON, end, 90, 8_800_000, List.of(SIX_PM),
                slots, List.of(), List.of(), List.of(), NOW);

        assertEquals(10, plan.getDays().size());
        assertEquals(List.of("18:00"), plan.getTimes());
        PtSalePlan.Day wednesday = plan.getDays().get(2);
        assertEquals("2026-10-28", wednesday.getDate());
        assertEquals(3, wednesday.getWeekday());
        assertFalse(wednesday.getOptions().get(0).isOk());
        assertEquals("PT đã có khách giờ này", wednesday.getOptions().get(0).getReason());
        PtSalePlan.Day thursday = plan.getDays().get(3);
        assertEquals("14:00", thursday.getOptions().get(0).getTime());   // giờ riêng ngày đó, xếp trước 18:00
        assertEquals("19:30", thursday.getOptions().get(1).getEnd());
        assertTrue(thursday.getOptions().get(1).isOk());
        assertFalse(thursday.getOptions().get(0).isFresh());               // 14:00 PT đã mở sẵn
        assertTrue(thursday.getOptions().get(1).isFresh());                // 18:00 ngày đó chưa có khung → tự tạo
    }

    @Test
    void customTimesMergeOnlyValidTimesWithinOpeningHours() {
        LocalTime open = LocalTime.of(5, 30);
        LocalTime close = LocalTime.of(22, 0);
        // 17:00 hợp lệ (trong giờ mở cửa, đúng mốc 15 phút, 90 phút kết thúc trước 22:00) → được gộp.
        // null (bỏ qua), 04:00 (trước giờ mở), 21:00 và 20:45 (kết thúc quá 22:00) → bị loại.
        List<LocalTime> customTimes = Arrays.asList(null, LocalTime.of(17, 0), LocalTime.of(4, 0),
                LocalTime.of(21, 0), LocalTime.of(20, 45));
        assertEquals(List.of(LocalTime.of(17, 0), SIX_PM),
                PtSaleService.mergeCustomTimes(List.of(SIX_PM), customTimes, 90, open, close));

        // Giờ ngoài giờ mở cửa / sai mốc 15 phút / null → không hợp lệ.
        assertFalse(PtSaleService.isValidStartTime(LocalTime.of(4, 0), 90, open, close));
        assertFalse(PtSaleService.isValidStartTime(LocalTime.of(17, 10), 90, open, close));
        assertFalse(PtSaleService.isValidStartTime(null, 90, open, close));
        // LocalTime.plusMinutes tự quay về ngày mới; ca 23:00–00:30 vẫn phải bị chặn.
        assertFalse(PtSaleService.isValidStartTime(LocalTime.of(23, 0), 90, open, close));
        assertFalse(PtSaleService.isValidStartTime(LocalTime.of(17, 0), 0, open, close));
    }

    @Test
    void requestedSessionsAcceptCustomTimeAsCreateSession() {
        LocalDate end = MON.plusDays(11);
        // PT chỉ công bố 18:00; lễ tân tự nhập 17:00 (hợp lệ) → checkRequested chấp nhận, action = "create" (sẽ tạo khung mới).
        List<PtSaleDay> days = PtSaleService.checkRequested(
                PtSaleService.parseSessions("2026-10-26@17:00"), MON, end, 90,
                List.of(SIX_PM, LocalTime.of(17, 0)), List.of(), List.of(), List.of(), List.of(), NOW);
        assertEquals(1, days.size());
        assertEquals("create", days.get(0).getAction());
        assertTrue(days.get(0).isSession());
    }

    @Test
    void requestedSessionsAreRecheckedOneByOne() {
        LocalDate end = MON.plusDays(11);
        List<PtScheduleItem> slots = List.of(slot(20, MON.plusDays(2), "18:00", "Booked"));
        Map<LocalDate, LocalTime> ok = PtSaleService.parseSessions("2026-10-26@18:00, 2026-10-29@18:00");
        List<PtSaleDay> days = PtSaleService.checkRequested(ok, MON, end, 90, List.of(SIX_PM), slots,
                List.of(), List.of(), List.of(), NOW);
        assertEquals(2, days.size());
        assertEquals(MON, days.get(0).getDate());

        // Ngày đã có khách / giờ PT không mở / ngoài kỳ / không có buổi nào → từ chối kèm đúng ngày.
        IllegalArgumentException taken = assertThrows(IllegalArgumentException.class, () -> PtSaleService.checkRequested(
                PtSaleService.parseSessions("2026-10-28@18:00"), MON, end, 90, List.of(SIX_PM), slots,
                List.of(), List.of(), List.of(), NOW));
        assertTrue(taken.getMessage().contains("28/10") && taken.getMessage().contains("PT đã có khách"));
        assertThrows(IllegalArgumentException.class, () -> PtSaleService.checkRequested(
                PtSaleService.parseSessions("2026-10-27@07:00"), MON, end, 90, List.of(SIX_PM), slots,
                List.of(), List.of(), List.of(), NOW));
        assertThrows(IllegalArgumentException.class, () -> PtSaleService.checkRequested(
                PtSaleService.parseSessions("2026-10-31@18:00"), MON, end, 90, List.of(SIX_PM), slots,
                List.of(), List.of(), List.of(), NOW));
        assertThrows(IllegalArgumentException.class, () -> PtSaleService.checkRequested(
                PtSaleService.parseSessions(""), MON, end, 90, List.of(SIX_PM), slots, List.of(), List.of(), List.of(), NOW));
    }

    @Test
    void parseSessionsRejectsBadTextAndTwoSessionsOnOneDay() {
        assertEquals(2, PtSaleService.parseSessions("2026-10-26@18:00,2026-10-27@06:00").size());
        assertTrue(PtSaleService.parseSessions(null).isEmpty());
        assertNull(PtSaleService.parseSessions("2026-10-26"));
        assertNull(PtSaleService.parseSessions("2026-10-26@18:00,2026-10-26@06:00"));
    }

    @Test
    void moneyAndShift() {
        assertEquals(375000, PtSaleService.sessionValue(16_500_000, 44));
        assertEquals(1_500_000, PtSaleService.vatAmount(16_500_000, 10));
        assertEquals("Morning", PtSaleService.shiftTypeAt(LocalTime.of(12, 59)));
        assertEquals("Afternoon", PtSaleService.shiftTypeAt(LocalTime.of(13, 0)));
    }
}
