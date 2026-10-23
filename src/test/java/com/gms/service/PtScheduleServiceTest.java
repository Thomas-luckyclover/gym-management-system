package com.gms.service;

import com.gms.dto.HourException;
import com.gms.dto.LeaveInfo;
import com.gms.dto.PtScheduleDay;
import com.gms.dto.PtScheduleItem;
import com.gms.dto.PtScheduleWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** Kiểm tra chọn tuần, phân loại khung và dựng tuần Lịch dạy PT; không dùng DB. */
class PtScheduleServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 22);
    private static final LocalDateTime NOW = TODAY.atTime(9, 0);

    /** URL thiếu, sai ngày hoặc ngoài biên điều hướng đều quay về hôm nay. */
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "abc", "22/10/2026", "2026-02-30", "2026-13-01",
            "2026-10-22T10:00", "+999999999-12-31", "-999999999-01-01"})
    void invalidDateFallsBackToToday(String value) {
        assertEquals(TODAY, PtScheduleService.parseDate(value, TODAY));
    }

    /** Ngày hợp lệ giữ nguyên để Servlet xác định tuần, gồm ngày nhuận và qua năm. */
    @ParameterizedTest
    @ValueSource(strings = {"2026-10-22", "2026-10-25", "2026-11-01", "2028-02-29", "2027-01-01"})
    void validDateIsPreserved(String value) {
        assertEquals(LocalDate.parse(value), PtScheduleService.parseDate(value, TODAY));
    }

    /** Thứ 2, Thứ 4, Chủ nhật và ngày qua tháng/năm đều về đúng Thứ 2. */
    @ParameterizedTest
    @CsvSource({
        "2026-10-19, 2026-10-19",
        "2026-10-21, 2026-10-19",
        "2026-10-25, 2026-10-19",
        "2026-11-01, 2026-10-26",
        "2027-01-01, 2026-12-28"
    })
    void weekStartsOnMonday(LocalDate date, LocalDate expectedMonday) {
        assertEquals(expectedMonday, PtScheduleService.weekStartOf(date));
    }

    /** Ngày URL cực trị không làm tràn các phép tính tuần mà Servlet sử dụng. */
    @ParameterizedTest
    @ValueSource(strings = {"+999999999-12-31", "-999999999-01-01"})
    void extremeDateStillAllowsWeekNavigation(String value) {
        LocalDate date = PtScheduleService.parseDate(value, TODAY);
        LocalDate weekStart = PtScheduleService.weekStartOf(date);

        assertDoesNotThrow(() -> weekStart.minusWeeks(1));
        assertDoesNotThrow(() -> weekStart.plusWeeks(1));
        assertDoesNotThrow(() -> weekStart.plusDays(6));
    }

    /** Free tương lai được gỡ; chiều cao lấy từ giờ DB, không cố định 90 phút. */
    @Test
    void freeSlotUsesActualDurationAndCanBeRemoved() {
        PtScheduleItem item = slot(TODAY, "10:00", "11:15", "Free");

        PtScheduleService.classify(item, List.of(), NOW);

        assertEquals("free", item.getKind());
        assertEquals("Còn trống", item.getStatusLabel());
        assertEquals(300, item.getStartMinute());
        assertEquals(75, item.getLengthMinutes());
        assertTrue(item.isCanRemove());
        assertFalse(item.isCanHandle());
        assertFalse(item.isPast());
    }

    /** Đơn nghỉ khóa theo giờ bắt đầu; 12:00 thuộc ca chiều, kể cả khung trước đó vắt qua trưa. */
    @ParameterizedTest
    @CsvSource({"Morning,10:00,Pending,true", "Morning,11:45,Approved,true",
        "Morning,12:00,Pending,false", "Morning,14:00,Approved,false",
        "Afternoon,11:45,Pending,false", "Afternoon,12:00,Approved,true",
        "FullDay,10:00,Pending,true", "FullDay,15:00,Approved,true",
        "Morning,10:00,Rejected,false"})
    void leaveLocksOnlyItsShift(String shift, LocalTime start, String status, boolean locked) {
        PtScheduleItem item = slot(TODAY, start.toString(), start.plusMinutes(90).toString(), "Free");

        PtScheduleService.classify(item, List.of(leave(TODAY, shift, status)), NOW);

        assertEquals(locked ? "locked" : "free", item.getKind());
        assertEquals(!locked, item.isCanRemove());
        assertFalse(item.isCanHandle());
    }

    /** Đơn nghỉ ngày khác không khóa khung của hôm nay. */
    @Test
    void leaveOfAnotherDateDoesNotLockSlot() {
        PtScheduleItem item = slot(TODAY, "10:00", "11:30", "Free");
        PtScheduleService.classify(item, List.of(leave(TODAY.plusDays(1), "FullDay", "Pending")), NOW);

        assertEquals("free", item.getKind());
        assertTrue(item.isCanRemove());
    }

    /** Held giữ nguyên ưu tiên, kể cả có đơn nghỉ: không cho gỡ khung đang giữ. */
    @Test
    void heldSlotCannotBeRemovedOrHandled() {
        PtScheduleItem item = slot(TODAY, "10:00", "11:30", "Held");
        PtScheduleService.classify(item, List.of(leave(TODAY, "FullDay", "Pending")), NOW);

        assertEquals("held", item.getKind());
        assertFalse(item.isCanRemove());
        assertFalse(item.isCanHandle());
    }

    /** Tới giờ bắt đầu đã hết quyền gỡ; phải tới giờ kết thúc mới đánh dấu past. */
    @ParameterizedTest
    @CsvSource({"07:00,08:30,true", "07:30,09:00,true", "08:00,09:30,false", "09:00,10:30,false"})
    void startedFreeSlotCannotBeRemoved(LocalTime start, LocalTime end, boolean past) {
        PtScheduleItem item = slot(TODAY, start.toString(), end.toString(), "Free");
        PtScheduleService.classify(item, List.of(), NOW);

        assertEquals(past, item.isPast());
        assertFalse(item.isCanRemove());
    }

    /** Buổi có khách luôn là booked; trạng thái đã chốt không bị đổi thành khung Free. */
    @ParameterizedTest
    @CsvSource({"Scheduled,Hôm nay,true", "CheckedIn,Đã check-in,false", "Missed,Vắng,false", "Cancelled,Đã hủy,false"})
    void sessionKeepsItsResultAndActionPermission(String status, String label, boolean canHandle) {
        PtScheduleItem item = session(TODAY, "10:00", "11:30", status);

        PtScheduleService.classify(item, List.of(leave(TODAY, "FullDay", "Approved")), NOW);

        assertEquals("booked", item.getKind());
        assertEquals(label, item.getStatusLabel());
        assertEquals(canHandle, item.isCanHandle());
        assertFalse(item.isCanRemove());
    }

    /** Buổi chưa chốt đổi nhãn đúng ở giờ bắt đầu và giờ kết thúc. */
    @ParameterizedTest
    @CsvSource({"07:30,09:00,Đang cập nhật,true", "08:00,09:30,Đang diễn ra,false", "09:00,10:30,Đang diễn ra,false"})
    void scheduledSessionUsesTimeBoundaries(LocalTime start, LocalTime end, String label, boolean past) {
        PtScheduleItem item = session(TODAY, start.toString(), end.toString(), "Scheduled");

        PtScheduleService.classify(item, List.of(), NOW);

        assertEquals(label, item.getStatusLabel());
        assertEquals(past, item.isPast());
        assertFalse(item.isCanHandle());
    }

    /** Buổi tương lai khác hôm nay hiện Sắp tới và vẫn mở được xử lý. */
    @Test
    void futureSessionIsUpcoming() {
        PtScheduleItem item = session(TODAY.plusDays(1), "10:00", "11:30", "Scheduled");

        PtScheduleService.classify(item, List.of(), NOW);

        assertEquals("Sắp tới", item.getStatusLabel());
        assertTrue(item.isCanHandle());
    }

    /** Buổi có trạng thái lạ phải báo lỗi thay vì hiện nhầm Sắp tới. */
    @Test
    void invalidSessionStatusIsRejected() {
        PtScheduleItem item = session(TODAY, "10:00", "11:30", "Unknown");
        assertThrows(IllegalStateException.class, () -> PtScheduleService.classify(item, List.of(), NOW));
    }

    /** Tuần rỗng có đủ 7 ngày và list items có thể thêm; đọc P-06/P-19 từ tham số đầu vào. */
    @Test
    void emptyWeekHasSevenDaysAndIndependentItemLists() {
        PtScheduleWeek week = week(List.of(), List.of());

        assertEquals(LocalDate.of(2026, 10, 19), week.getWeekStart());
        assertEquals(LocalDate.of(2026, 10, 25), week.getWeekEnd());
        assertEquals(LocalDate.of(2026, 10, 12), week.getPrevWeek());
        assertEquals(LocalDate.of(2026, 10, 26), week.getNextWeek());
        assertEquals(7, week.getDays().size());
        assertTrue(week.isCurrentWeek());
        assertEquals(90, week.getSessionMinutes());
        assertEquals(5, week.getAxisStartHour());
        assertEquals(22, week.getAxisEndHour());
        assertEquals("Thứ 2", week.getDays().get(0).getDayName());
        assertEquals("Chủ nhật", week.getDays().get(6).getDayName());
        assertTrue(week.getDays().get(2).isPast());
        assertTrue(week.getDays().get(3).isToday());
        assertFalse(week.getDays().get(4).isPast());
        week.getDays().get(0).getItems().add(slot(TODAY, "10:00", "11:30", "Free"));
        assertTrue(week.getDays().get(1).getItems().isEmpty());
    }

    /** Khoảng giờ được thêm khung: ngày qua/nghỉ thì không có; hôm nay chỉ từ sau giờ hiện tại. */
    @Test
    void addRangeFollowsOpeningHoursNowAndClosedDays() {
        // NOW = Thứ 5 22/10 09:00; P-19 05:30–22:00; khung 90 phút.
        PtScheduleWeek week = week(List.of(hours(TODAY.plusDays(2), true, null, null),
                hours(TODAY.plusDays(1), false, "08:10", "12:00")), List.of());

        assertNull(week.getDays().get(2).getFirstStartMinute());          // Thứ 4 đã qua
        assertEquals(555, week.getDays().get(3).getFirstStartMinute());   // hôm nay: sau 09:00 → 09:15
        assertEquals(1230, week.getDays().get(3).getLastStartMinute());   // 22:00 − 90 phút = 20:30
        assertEquals(495, week.getDays().get(4).getFirstStartMinute());   // mở 08:10 → làm tròn lên 08:15
        assertEquals(630, week.getDays().get(4).getLastStartMinute());    // 12:00 − 90 phút = 10:30
        assertNull(week.getDays().get(5).getFirstStartMinute());          // Thứ 7 phòng nghỉ
        assertEquals(330, week.getDays().get(6).getFirstStartMinute());   // Chủ nhật: từ 05:30
    }

    /** Ngày ngoại lệ thay giờ P-19, đồng thời mở rộng trục nếu mở sớm/đóng muộn. */
    @Test
    void exceptionalHoursOverrideDefaultsAndExpandAxis() {
        HourException exception = hours(TODAY, false, "04:30", "22:30");
        PtScheduleWeek week = week(List.of(exception), List.of());

        assertEquals(LocalTime.of(4, 30), week.getDays().get(3).getOpenTime());
        assertEquals(LocalTime.of(22, 30), week.getDays().get(3).getCloseTime());
        assertEquals(LocalTime.of(5, 30), week.getDays().get(4).getOpenTime());
        assertEquals(4, week.getAxisStartHour());
        assertEquals(23, week.getAxisEndHour());
    }

    /** Ngày phòng nghỉ vẫn giữ buổi Scheduled đã sinh để PT biết cần dời. */
    @Test
    void closedDayStillContainsItsScheduledSession() {
        PtScheduleWeek week = week(List.of(hours(TODAY, true, null, null)), List.of());
        PtScheduleItem item = session(TODAY, "10:00", "11:30", "Scheduled");

        PtScheduleService.fillWeek(week, List.of(item), List.of(), NOW);

        PtScheduleDay day = week.getDays().get(3);
        assertTrue(day.isClosedAllDay());
        assertNull(day.getOpenTime());
        assertNull(day.getCloseTime());
        assertEquals(List.of(item), day.getItems());
        assertEquals("Scheduled", item.getSessionStatus());
        assertEquals("booked", item.getKind());
        assertTrue(item.isCanHandle());
    }

    /** Nhãn ca nghỉ giữ cả chờ duyệt/đã duyệt, bỏ đơn bị từ chối và đơn khác ngày. */
    @Test
    void leaveLabelContainsOnlyActiveLeavesOfTheDay() {
        List<LeaveInfo> leaves = List.of(leave(TODAY, "Morning", "Pending"),
                leave(TODAY, "Afternoon", "Approved"), leave(TODAY, "FullDay", "Rejected"),
                leave(TODAY.plusDays(1), "FullDay", "Approved"));

        PtScheduleWeek week = week(List.of(), leaves);

        assertEquals("Nghỉ phép ca sáng · chờ duyệt; Nghỉ phép ca chiều · đã duyệt", week.getDays().get(3).getLeaveLabel());
        assertEquals("", week.getDays().get(2).getLeaveLabel());
    }

    /** Ghép không mất lịch sử trên khung mở lại, sắp giờ tăng dần và không sửa hai list gốc. */
    @Test
    void mergeRetainsHistoryAndSortsRowsWithoutChangingInputLists() {
        PtScheduleItem cancelled = session(TODAY, "10:00", "11:30", "Cancelled");
        PtScheduleItem reopened = slot(TODAY, "10:00", "11:30", "Free");
        PtScheduleItem earlier = slot(TODAY.minusDays(1), "08:00", "09:30", "Held");
        List<PtScheduleItem> sessions = List.of(cancelled);
        List<PtScheduleItem> slots = List.of(reopened, earlier);

        List<PtScheduleItem> merged = PtScheduleService.merge(sessions, slots);

        assertEquals(List.of(earlier, cancelled, reopened), merged);
        assertEquals(1, sessions.size());
        assertEquals(List.of(reopened, earlier), slots);
    }

    /** Buổi ngoài giờ P-19 hiện tại vẫn nằm trong trục, không có top âm. */
    @Test
    void fillWeekPlacesItemsInCorrectDayAndUsesExpandedAxis() {
        PtScheduleWeek week = week(List.of(), List.of());
        PtScheduleItem early = session(TODAY, "04:30", "06:00", "Missed");
        PtScheduleItem later = slot(TODAY.plusDays(1), "08:30", "10:00", "Free");

        PtScheduleService.fillWeek(week, List.of(early, later), List.of(), NOW);

        assertEquals(4, week.getAxisStartHour());
        assertEquals(30, early.getStartMinute());
        assertEquals(270, later.getStartMinute());
        assertEquals(List.of(early), week.getDays().get(3).getItems());
        assertEquals(List.of(later), week.getDays().get(4).getItems());
        assertTrue(week.getDays().get(0).getItems().isEmpty());
    }

    /** Thiếu/sai cấu hình không được dùng mặc định để dựng một tuần trông như hợp lệ. */
    @ParameterizedTest
    @ValueSource(strings = {"bad", "22:00-05:30", "25:00-22:00", "05:30-22:00-extra"})
    void invalidOpeningHoursAreRejected(String value) {
        assertThrows(RuntimeException.class, () -> PtScheduleService.parseOpeningHours(value));
    }

    /** P-19 có khoảng trắng vẫn đọc được giờ thật. */
    @Test
    void openingHoursAreParsedFromConfiguration() {
        assertArrayEquals(new LocalTime[]{LocalTime.of(5, 30), LocalTime.of(22, 0)},
                PtScheduleService.parseOpeningHours(" 05:30 - 22:00 "));
    }

    /** Dữ liệu mẫu của test, không phải giờ mặc định được hard-code trong Service. */
    private static PtScheduleWeek week(List<HourException> exceptions, List<LeaveInfo> leaves) {
        return PtScheduleService.buildEmptyWeek(LocalDate.of(2026, 10, 19), NOW,
                exceptions, leaves, 90, LocalTime.of(5, 30), LocalTime.of(22, 0));
    }

    /** Tạo dữ liệu khung giống kết quả readSlot của DAO. */
    private static PtScheduleItem slot(LocalDate date, String start, String end, String kind) {
        PtScheduleItem item = new PtScheduleItem();
        item.setSlotDate(date);
        item.setStartTime(LocalTime.parse(start));
        item.setEndTime(LocalTime.parse(end));
        item.setKind(kind);
        return item;
    }

    /** Thêm thông tin buổi giống kết quả readSessionRow của DAO. */
    private static PtScheduleItem session(LocalDate date, String start, String end, String status) {
        PtScheduleItem item = slot(date, start, end, null);
        item.setSessionId(100);
        item.setSessionStatus(status);
        return item;
    }

    /** Tạo đơn nghỉ mẫu với ngày/ca/trạng thái riêng cho từng case. */
    private static LeaveInfo leave(LocalDate date, String shift, String status) {
        LeaveInfo leave = new LeaveInfo();
        leave.setLeaveDate(date);
        leave.setShiftType(shift);
        leave.setStatus(status);
        return leave;
    }

    /** Ngày nghỉ cả ngày nhận giờ null như dữ liệu OperatingHourExceptions. */
    private static HourException hours(LocalDate date, boolean closed, String open, String close) {
        HourException exception = new HourException();
        exception.setApplyDate(date);
        exception.setClosedAllDay(closed);
        exception.setOpenTime(open == null ? null : LocalTime.parse(open));
        exception.setCloseTime(close == null ? null : LocalTime.parse(close));
        return exception;
    }
}
