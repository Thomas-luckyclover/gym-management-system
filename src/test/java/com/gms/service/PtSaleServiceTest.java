package com.gms.service;

import com.gms.dto.HourException;
import com.gms.dto.LeaveInfo;
import com.gms.dto.PtSaleCustomer;
import com.gms.dto.PtSaleDay;
import com.gms.dto.PtSaleGridRow;
import com.gms.dto.PtScheduleItem;
import com.gms.util.TimeRange;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Luật màn Bán gói PT (không đọc DB). Kỳ mẫu: Thứ 2 26/10/2026 → 06/11/2026, giờ 18:00, 90 phút. */
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

    private static LocalTime[] all(LocalTime time) {
        return new LocalTime[] {time, time, time, time, time};
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
    void gridCountsUsableWeeksPerWeekdayAndMarksSelection() {
        LocalDate end = MON.plusDays(11);   // 2 tuần: T2 26/10 → T6 06/11
        List<PtScheduleItem> slots = List.of(slot(20, MON.plusDays(2), "18:00", "Booked"));   // T4 tuần 1 đã có khách
        LocalTime[] selected = {SIX_PM, null, null, null, null};
        List<PtSaleGridRow> grid = PtSaleService.buildGrid(List.of(SIX_PM), selected, MON, end, 90,
                slots, List.of(), List.of(), List.of(), NOW);

        PtSaleGridRow row = grid.get(0);
        assertEquals("18:00", row.getTime());
        assertEquals(2, row.getCells().get(0).getTotal());
        assertEquals("full", row.getCells().get(0).getLevel());
        assertTrue(row.getCells().get(0).isSelected());
        assertEquals(1, row.getCells().get(2).getUsable());    // T4: còn 1/2 tuần
        assertEquals("some", row.getCells().get(2).getLevel());
        assertTrue(row.isWholeWeekPossible());
        assertArrayEquals(new int[] {2, 2, 2, 2, 2}, PtSaleService.weekdayTotals(MON, end));
    }

    @Test
    void planUsesFreeSlotCreatesMissingAndSkipsTakenDays() {
        LocalDate end = MON.plusDays(4);   // T2 → T6 cùng tuần
        List<PtScheduleItem> slots = List.of(
                slot(10, MON, "18:00", "Free"),                    // T2: dùng khung đã công bố
                slot(11, MON.plusDays(1), "18:00", "Booked"),      // T3: PT đã có khách
                slot(12, MON.plusDays(2), "17:00", "Free"),        // T4: khung khác chồng giờ
                slot(13, MON.plusDays(3), "18:00", "Withdrawn"));  // T5: PT đã gỡ
        List<TimeRange> busy = List.of();
        List<PtSaleDay> days = PtSaleService.planDays(MON, end, all(SIX_PM), 90, slots, List.of(), List.of(), busy, NOW);

        assertEquals(5, days.size());
        assertEquals("book", days.get(0).getAction());
        assertEquals(10, days.get(0).getSlotId());
        assertEquals("PT đã có khách giờ này", days.get(1).getReason());
        assertEquals("PT có khung khác chồng giờ", days.get(2).getReason());
        assertEquals("PT đã gỡ khung giờ này", days.get(3).getReason());
        assertEquals("create", days.get(4).getAction());   // T6: chưa có khung → tạo mới

        // Mỗi thứ 1 giờ riêng; thứ chưa chọn giờ → không có buổi.
        LocalTime[] mixed = {SIX_PM, LocalTime.of(6, 0), null, SIX_PM, SIX_PM};
        List<PtSaleDay> week = PtSaleService.planDays(MON, end, mixed, 90, List.of(slot(30, MON.plusDays(1), "06:00", "Held")),
                List.of(), List.of(), busy, NOW);
        assertEquals("Đang được giữ cho đơn khác", week.get(1).getReason());
        assertEquals("Chưa chọn giờ", week.get(2).getReason());
        assertEquals(SIX_PM, PtSaleService.mostCommonStart(List.of(slot(1, MON, "18:00", "Held"),
                slot(2, MON.plusDays(1), "06:00", "Held"), slot(3, MON.plusDays(3), "18:00", "Held"))));
    }

    @Test
    void planSkipsCustomerClashAndPastTimeButWarnsClosedDayAndLeave() {
        LocalDate end = MON.plusDays(2);
        List<TimeRange> busy = List.of(new TimeRange(MON.plusDays(1), LocalTime.of(18, 30), LocalTime.of(20, 0)));

        HourException closed = new HourException();
        closed.setApplyDate(MON.plusDays(2));
        closed.setClosedAllDay(true);

        LeaveInfo leave = new LeaveInfo();
        leave.setLeaveDate(MON);
        leave.setShiftType("Afternoon");
        leave.setStatus("Approved");

        List<PtSaleDay> days = PtSaleService.planDays(MON, end, all(SIX_PM), 90, List.of(), List.of(leave),
                List.of(closed), busy, NOW);
        assertTrue(days.get(0).isSession());
        assertTrue(days.get(0).getWarning().contains("đơn nghỉ"));
        assertEquals("Khách có buổi khác trùng giờ", days.get(1).getReason());
        assertTrue(days.get(2).getWarning().contains("Phòng nghỉ"));

        // Bắt đầu hôm nay sau giờ học → ngày hôm nay bị bỏ.
        LocalDateTime evening = LocalDateTime.of(2026, 10, 26, 19, 0);
        List<PtSaleDay> today = PtSaleService.planDays(MON, MON, all(SIX_PM), 90, List.of(), List.of(), List.of(), List.of(), evening);
        assertEquals("Đã qua giờ", today.get(0).getReason());
    }

    @Test
    void moneyAndShift() {
        assertEquals(375000, PtSaleService.sessionValue(16_500_000, 44));
        assertEquals(1_500_000, PtSaleService.vatAmount(16_500_000, 10));
        assertEquals("Morning", PtSaleService.shiftTypeAt(LocalTime.of(13, 59)));
        assertEquals("Afternoon", PtSaleService.shiftTypeAt(LocalTime.of(14, 0)));
    }
}
