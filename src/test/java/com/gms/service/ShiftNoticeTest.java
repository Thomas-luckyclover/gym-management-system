package com.gms.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.gms.constant.Messages;
import com.gms.dao.ShiftDAO;
import com.gms.entity.Shift;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/**
 * Unit test cho CBR-123 / CBR-105 (commit de3c964, 3dcef0e):
 *   ShiftService.pastDayShiftMessage — chặn bán vào ca của ngày trước (MSG324).
 *   ShiftService.findShiftNotice     — thanh cảnh báo MSG323 (ca quá giờ) / MSG109 (chưa mở ca, chỉ màn bán).
 * Không cần SQL Server: ShiftDAO được thay bằng bản giả trả về ca dựng sẵn.
 */
class ShiftNoticeTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 2);

    private static Shift shift(LocalDate day, Shift.ShiftType type, Shift.Status status) {
        Shift s = new Shift(1);
        s.setWorkDate(day);
        s.setShiftType(type);
        s.setStatus(status);
        return s;
    }

    /** ShiftService với ShiftDAO giả: findCurrentOrActiveShift luôn trả về ca truyền vào. */
    private static ShiftService serviceReturning(Shift current) {
        ShiftDAO fake = new ShiftDAO() {
            @Override
            public Shift findCurrentOrActiveShift(LocalDate today) {
                return current;
            }
        };
        return new ShiftService(fake, null, null, null, null);
    }

    // ===== pastDayShiftMessage (MSG324) =====

    @Test
    void pastDay_openShiftOfYesterday_isRefusedWithMsg324() {
        Shift yesterday = shift(TODAY.minusDays(1), Shift.ShiftType.AFTERNOON, Shift.Status.OPEN);
        assertEquals(String.format(Messages.MSG324_SHIFT_PAST_DAY_NOT_CLOSED, "Ca chiều", "01/11/2026"),
                ShiftService.pastDayShiftMessage(yesterday, TODAY));
    }

    @Test
    void pastDay_openShiftOfToday_isAllowed() {
        Shift morning = shift(TODAY, Shift.ShiftType.MORNING, Shift.Status.OPEN);
        assertNull(ShiftService.pastDayShiftMessage(morning, TODAY));
    }

    @Test
    void pastDay_closedOrMissingShift_isNotThisRule() {
        assertNull(ShiftService.pastDayShiftMessage(null, TODAY));
        assertNull(ShiftService.pastDayShiftMessage(
                shift(TODAY.minusDays(1), Shift.ShiftType.AFTERNOON, Shift.Status.CLOSED), TODAY));
    }

    // ===== findShiftNotice (MSG323 / MSG109) =====

    @Test
    void notice_morningShiftStillOpenAt1300_showsMsg323OnEveryScreen() {
        ShiftService service = serviceReturning(shift(TODAY, Shift.ShiftType.MORNING, Shift.Status.OPEN));
        String expected = String.format(Messages.MSG323_SHIFT_OVERDUE_NOT_CLOSED, "Ca sáng", "02/11/2026");
        assertEquals(expected, service.findShiftNotice(TODAY.atTime(13, 0), false));
        assertEquals(expected, service.findShiftNotice(TODAY.atTime(15, 30), true));
    }

    @Test
    void notice_morningShiftOpenAt1259_showsNothing() {
        ShiftService service = serviceReturning(shift(TODAY, Shift.ShiftType.MORNING, Shift.Status.OPEN));
        assertNull(service.findShiftNotice(TODAY.atTime(12, 59), true));
    }

    @Test
    void notice_shiftOfYesterdayStillOpen_showsMsg323() {
        ShiftService service = serviceReturning(shift(TODAY.minusDays(1), Shift.ShiftType.MORNING, Shift.Status.OPEN));
        assertEquals(String.format(Messages.MSG323_SHIFT_OVERDUE_NOT_CLOSED, "Ca sáng", "01/11/2026"),
                service.findShiftNotice(LocalDateTime.of(TODAY, java.time.LocalTime.of(7, 0)), false));
    }

    @Test
    void notice_noOpenShift_showsMsg109OnlyOnSaleScreens() {
        ShiftService service = serviceReturning(shift(TODAY, Shift.ShiftType.MORNING, Shift.Status.NOT_OPENED));
        assertEquals(Messages.MSG109_SHIFT_NOT_OPEN_FOR_SALE, service.findShiftNotice(TODAY.atTime(8, 0), true));
        assertNull(service.findShiftNotice(TODAY.atTime(8, 0), false));
    }
}
