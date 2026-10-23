package com.gms.service;

import com.gms.constant.Messages;
import com.gms.dao.PtScheduleDAO;
import com.gms.dto.HourException;
import com.gms.dto.LeaveInfo;
import com.gms.dto.PtScheduleDay;
import com.gms.dto.PtScheduleItem;
import com.gms.dto.PtScheduleWeek;
import com.gms.dto.PtSlotDraft;
import com.gms.dto.ServiceResult;
import com.gms.entity.Session;
import com.gms.util.DBContext;
import com.gms.util.DateTimeUtil;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * SERVICE MÀN LỊCH DẠY PT (/pt/schedule).
 *
 * Servlet chỉ gọi HAI hàm chính:
 *   getWeek  — khi mở trang:   đọc DB → dựng 7 ngày → xếp từng thẻ vào đúng ngày.
 *   publish  — khi bấm Công bố: gỡ các khung đã chọn + thêm khung mới, trong MỘT transaction.
 *
 * MỤC LỤC
 *   PHẦN 1. MỞ TRANG          getWeek → buildEmptyWeek → buildDay, buildLeaveLabel, setAddRange
 *   PHẦN 2. XẾP THẺ VÀO NGÀY  merge, fillWeek → classify → sessionLabel, isOnLeave
 *   PHẦN 3. CÔNG BỐ           publish → publishInTransaction → parseRemoveIds, buildDrafts, validateDrafts
 *   PHẦN 4. HÀM DÙNG CHUNG    parseDate, weekStartOf, parseOpeningHours, đổi giờ ↔ phút
 *
 * Các hàm "static" không đọc DB nên test được trực tiếp (xem PtScheduleServiceTest, PtSchedulePublishTest).
 */
public class PtScheduleService {

    private final PtScheduleDAO scheduleDAO = new PtScheduleDAO();
    private final SystemConfigService configService = new SystemConfigService(); // đọc P-06, P-19


    // ================================================================================================================
    // PHẦN 1. MỞ TRANG — getWeek là hàm chính; các hàm phụ dựng tuần nằm ngay dưới
    // ================================================================================================================

    /**
     * HÀM CHÍNH — PtScheduleServlet.doGet gọi (qua loadWeek) mỗi lần mở trang.
     *
     * Vào:  accountId (lấy từ session), anyDay (ngày bất kỳ trong tuần muốn xem), now (giờ Việt Nam).
     * Ra:   ServiceResult chứa PtScheduleWeek; lỗi thì chứa câu báo lỗi.
     */
    public ServiceResult<PtScheduleWeek> getWeek(int accountId, LocalDate anyDay, LocalDateTime now) {
        try {
            // B1: tài khoản đăng nhập → mã PT. Không phải PT đang làm việc thì dừng.
            Integer ptId = scheduleDAO.findPtEmployeeId(accountId);
            if (ptId == null) {
                return ServiceResult.fail(Messages.MSG07_NO_PERMISSION);
            }

            // B2: tuần cần xem là Thứ 2 → Chủ nhật chứa anyDay.
            LocalDate monday = weekStartOf(anyDay);
            LocalDate sunday = monday.plusDays(6);

            // B3: đọc cấu hình: P-06 = số phút mỗi khung, P-19 = giờ mở/đóng cửa.
            int minutes = configService.getInt(SystemConfigService.SESSION_DURATION_MINUTES);
            LocalTime[] hours = parseOpeningHours(configService.getString(SystemConfigService.OPENING_HOURS));

            // B4: đọc 4 nhóm dữ liệu của CẢ TUẦN, mỗi nhóm đúng 1 lần.
            List<PtScheduleItem> sessions = scheduleDAO.findSessions(ptId, monday, sunday);
            List<PtScheduleItem> openSlots = scheduleDAO.findOpenSlots(ptId, monday, sunday);
            List<LeaveInfo> leaves = scheduleDAO.findLeaves(ptId, monday, sunday);
            List<HourException> exceptions = scheduleDAO.findHourExceptions(monday, sunday);

            // B5: dựng 7 ngày trống, rồi xếp từng thẻ vào đúng ngày.
            PtScheduleWeek week = buildEmptyWeek(monday, now, exceptions, leaves, minutes, hours[0], hours[1]);
            List<PtScheduleItem> allItems = merge(sessions, openSlots);
            fillWeek(week, allItems, leaves, now);

            return ServiceResult.ok(week);

        } catch (SQLException | IllegalStateException | DateTimeException exception) {
            // Lỗi DB hoặc cấu hình sai → báo lỗi tải.
            // KHÔNG trả tuần trống: PT sẽ tưởng nhầm là mình không có lịch.
            exception.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /**
     * HÀM PHỤ — gọi ở B5 của getWeek.
     * Dựng tuần có đủ 7 ngày (chưa có thẻ nào). Ngày nào có giờ ngoại lệ thì dùng giờ ngoại lệ.
     */
    static PtScheduleWeek buildEmptyWeek(LocalDate monday, LocalDateTime now,
            List<HourException> exceptions, List<LeaveInfo> leaves, int sessionMinutes,
            LocalTime open, LocalTime close) {

        // B1: chặn cấu hình sai trước khi dựng.
        boolean badDuration = sessionMinutes <= 0;
        boolean badHours = !close.isAfter(open);
        if (badDuration || badHours) {
            throw new IllegalStateException("Cấu hình lịch dạy không hợp lệ");
        }

        // B2: các mốc tuần + trục giờ ban đầu theo P-19.
        PtScheduleWeek week = new PtScheduleWeek();
        week.setWeekStart(monday);
        week.setWeekEnd(monday.plusDays(6));
        week.setPrevWeek(monday.minusWeeks(1));
        week.setNextWeek(monday.plusWeeks(1));
        week.setCurrentWeek(monday.equals(weekStartOf(now.toLocalDate())));
        week.setSessionMinutes(sessionMinutes);
        week.setAxisStartHour(open.getHour());       // mở 05:30 → trục bắt đầu 05:00
        week.setAxisEndHour(endHour(close));          // đóng 22:30 → trục kết thúc 23:00

        // B3: dựng từng ngày, i = 0 là Thứ 2 ... i = 6 là Chủ nhật.
        List<PtScheduleDay> days = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            PtScheduleDay day = buildDay(monday.plusDays(i), now.toLocalDate(), open, close, exceptions);
            day.setLeaveLabel(buildLeaveLabel(day.getDate(), leaves));
            setAddRange(day, now, sessionMinutes);
            days.add(day);

            // Ngày mở sớm hơn / đóng muộn hơn P-19 thì nới trục chung để không cắt mất khung.
            if (!day.isClosedAllDay()) {
                week.setAxisStartHour(Math.min(week.getAxisStartHour(), day.getOpenTime().getHour()));
                week.setAxisEndHour(Math.max(week.getAxisEndHour(), endHour(day.getCloseTime())));
            }
        }

        week.setDays(days);
        return week;
    }

    /**
     * HÀM PHỤ — gọi ở B3 của buildEmptyWeek; validateDrafts cũng gọi để biết giờ mở cửa của một ngày.
     * Một ngày dùng giờ P-19, trừ khi bảng ngoại lệ có giờ riêng hoặc báo nghỉ cả ngày.
     */
    static PtScheduleDay buildDay(LocalDate date, LocalDate today, LocalTime open,
            LocalTime close, List<HourException> exceptions) {

        // B1: thông tin ngày + giờ mặc định P-19.
        PtScheduleDay day = new PtScheduleDay();
        day.setDate(date);
        day.setDayName(dayName(date));
        day.setToday(date.equals(today));
        day.setPast(date.isBefore(today));
        day.setOpenTime(open);
        day.setCloseTime(close);
        day.setItems(new ArrayList<>());   // mỗi ngày một list riêng để thêm thẻ

        // B2: tìm ngoại lệ của đúng ngày này. Mỗi ngày có tối đa 1 bản ghi.
        for (HourException exception : exceptions) {
            if (!date.equals(exception.getApplyDate())) {
                continue;
            }

            day.setClosedAllDay(exception.isClosedAllDay());
            day.setOpenTime(exception.getOpenTime());
            day.setCloseTime(exception.getCloseTime());

            // Ngày vẫn mở cửa thì phải có đủ giờ mở < giờ đóng.
            boolean missingTime = day.getOpenTime() == null || day.getCloseTime() == null;
            boolean wrongOrder = !missingTime && !day.getCloseTime().isAfter(day.getOpenTime());
            if (!day.isClosedAllDay() && (missingTime || wrongOrder)) {
                throw new IllegalStateException("Giờ ngoại lệ không hợp lệ: " + date);
            }
            break;
        }

        return day;
    }

    /**
     * HÀM PHỤ — gọi ở B3 của buildEmptyWeek.
     * Tính khoảng giờ PT được THÊM khung mới trong ngày (đơn vị: phút trong ngày).
     * JSP in ra data-first / data-last; JS chỉ việc so sánh, không phải tự tính giờ mở cửa.
     *
     * Ví dụ: mở 05:30–22:00, khung 90 phút → first = 330 (05:30), last = 1170 (19:30).
     */
    static void setAddRange(PtScheduleDay day, LocalDateTime now, int sessionMinutes) {
        // B1: ngày đã qua hoặc phòng nghỉ → không thêm được (để null).
        if (day.isPast() || day.isClosedAllDay()) {
            return;
        }

        // B2: sớm nhất = giờ mở cửa, làm tròn LÊN mốc 15 phút.
        //     Hôm nay thì còn phải SAU giờ hiện tại (Service không nhận khung đã bắt đầu).
        int first = roundUpTo15(minuteOfDay(day.getOpenTime()));
        if (day.isToday()) {
            int afterNow = roundUpTo15(minuteOfDay(now.toLocalTime()) + 1);
            first = Math.max(first, afterNow);
        }

        // B3: muộn nhất = giờ đóng cửa − độ dài khung, làm tròn XUỐNG mốc 15 phút.
        int last = roundDownTo15(minuteOfDay(day.getCloseTime()) - sessionMinutes);

        // B4: không còn chỗ cho dù chỉ một khung → để null.
        if (first > last) {
            return;
        }
        day.setFirstStartMinute(first);
        day.setLastStartMinute(last);
    }

    /**
     * HÀM PHỤ — gọi ở B3 của buildEmptyWeek.
     * Ghép nhãn nghỉ phép của một ngày, ví dụ "Nghỉ phép ca sáng · chờ duyệt".
     * Không có đơn Pending/Approved nào → chuỗi rỗng.
     */
    static String buildLeaveLabel(LocalDate date, List<LeaveInfo> leaves) {
        List<String> labels = new ArrayList<>();

        for (LeaveInfo leave : leaves) {
            boolean sameDay = date.equals(leave.getLeaveDate());
            if (!sameDay || !isActiveLeave(leave)) {
                continue;
            }

            String shift = switch (leave.getShiftType()) {
                case "Morning" -> "ca sáng";
                case "Afternoon" -> "ca chiều";
                case "FullDay" -> "cả ngày";
                default -> throw new IllegalStateException("Ca nghỉ không hợp lệ");
            };
            String status = "Pending".equals(leave.getStatus()) ? "chờ duyệt" : "đã duyệt";
            labels.add("Nghỉ phép " + shift + " · " + status);
        }

        // Một ngày có thể nghỉ cả ca sáng lẫn ca chiều → nối bằng "; ".
        return String.join("; ", labels);
    }

    /** HÀM PHỤ — gọi ở B1 của buildDay. Thứ 2 ... Thứ 7, Chủ nhật (Java đánh số Thứ 2 = 1). */
    private static String dayName(LocalDate date) {
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        if (dayOfWeek == DayOfWeek.SUNDAY) {
            return "Chủ nhật";
        }
        return "Thứ " + (dayOfWeek.getValue() + 1);
    }


    // ================================================================================================================
    // PHẦN 2. XẾP THẺ VÀO NGÀY — fillWeek gọi classify cho từng thẻ
    // ================================================================================================================

    /**
     * HÀM PHỤ — gọi ở B5 của getWeek.
     * Ghép buổi có khách + khung trống thành một danh sách, xếp theo ngày rồi giờ.
     * Tạo list mới, không sửa hai list DAO trả về.
     */
    static List<PtScheduleItem> merge(List<PtScheduleItem> sessions, List<PtScheduleItem> openSlots) {
        List<PtScheduleItem> items = new ArrayList<>(sessions);
        items.addAll(openSlots);
        items.sort(Comparator.comparing(PtScheduleItem::getSlotDate).thenComparing(PtScheduleItem::getStartTime));
        return items;
    }

    /**
     * HÀM PHỤ — gọi ở B5 của getWeek.
     * Phân loại từng thẻ (classify) rồi đặt vào đúng ngày. Không đổi gì trong DB.
     */
    static void fillWeek(PtScheduleWeek week, List<PtScheduleItem> items, List<LeaveInfo> leaves, LocalDateTime now) {

        // B1: buổi cũ có thể nằm ngoài giờ mở cửa hiện tại → nới trục giờ trước khi tính vị trí.
        for (PtScheduleItem item : items) {
            week.setAxisStartHour(Math.min(week.getAxisStartHour(), item.getStartTime().getHour()));
            week.setAxisEndHour(Math.max(week.getAxisEndHour(), endHour(item.getEndTime())));
        }

        // B2: phân loại, tính vị trí theo trục cuối cùng, rồi bỏ vào ngày có cùng ngày.
        int axisStartMinute = week.getAxisStartHour() * 60;
        for (PtScheduleItem item : items) {
            classify(item, leaves, now);

            // Ví dụ trục bắt đầu 05:00, khung 08:30 → startMinute = 510 − 300 = 210.
            item.setStartMinute(minuteOfDay(item.getStartTime()) - axisStartMinute);

            for (PtScheduleDay day : week.getDays()) {
                if (day.getDate().equals(item.getSlotDate())) {
                    day.getItems().add(item);
                    break;
                }
            }
        }
    }

    /**
     * HÀM PHỤ — gọi ở B2 của fillWeek.
     * Quyết định một thẻ là loại gì, ghi chữ gì, có được gỡ / xử lý không. Không đọc DB.
     *
     *   Có khách (sessionId != null)  → booked  : nhãn theo trạng thái buổi
     *   Khách đang giữ để thanh toán  → held    : không gỡ được
     *   Trùng ca nghỉ phép            → locked  : không gỡ được
     *   Còn lại                       → free    : gỡ được nếu chưa tới giờ bắt đầu
     */
    static void classify(PtScheduleItem item, List<LeaveInfo> leaves, LocalDateTime now) {

        // B1: mốc thời gian, độ dài, và đặt lại hai quyền về false.
        LocalDateTime start = LocalDateTime.of(item.getSlotDate(), item.getStartTime());
        LocalDateTime end = LocalDateTime.of(item.getSlotDate(), item.getEndTime());
        item.setPast(!end.isAfter(now));          // hết giờ mới tính là đã qua
        item.setCanRemove(false);
        item.setCanHandle(false);
        // Vị trí tạm tính từ 05:00; fillWeek sẽ tính lại theo trục thật của tuần.
        item.setStartMinute(minuteOfDay(item.getStartTime()) - 5 * 60);
        // Độ dài lấy theo giờ trong DB (khung cũ có thể khác P-06 hiện tại).
        item.setLengthMinutes((int) Duration.between(item.getStartTime(), item.getEndTime()).toMinutes());

        boolean notStarted = start.isAfter(now);

        // B2: có khách → booked. Đơn nghỉ KHÔNG đè lên buổi đã có khách.
        if (item.getSessionId() != null) {
            item.setKind("booked");
            item.setStatusLabel(sessionLabel(item, start, end, now));
            boolean scheduled = "Scheduled".equals(item.getSessionStatus());
            item.setCanHandle(scheduled && notStarted);
            return;
        }

        // B3: khách đang giữ khung chờ thanh toán → held.
        if ("Held".equalsIgnoreCase(item.getKind())) {
            item.setKind("held");
            item.setStatusLabel("Đang giữ · đơn chờ thanh toán");
            return;
        }

        // B4: trùng ca nghỉ phép → locked (chỉ hiển thị, không sửa DB).
        if (isOnLeave(item, leaves)) {
            item.setKind("locked");
            item.setStatusLabel("Tạm khóa · đơn nghỉ");
            return;
        }

        // B5: khung trống bình thường. Chỉ gỡ được khi chưa tới giờ bắt đầu.
        item.setKind("free");
        item.setStatusLabel("Còn trống");
        item.setCanRemove(notStarted);
    }

    /**
     * HÀM PHỤ — gọi ở B2 của classify.
     * Chữ trạng thái của buổi có khách. Buổi đã có kết quả giữ nguyên kết quả;
     * buổi còn Scheduled thì đổi chữ theo giờ: Sắp tới → Hôm nay → Đang diễn ra → Đang cập nhật.
     */
    private static String sessionLabel(PtScheduleItem item, LocalDateTime start,
            LocalDateTime end, LocalDateTime now) {

        Session.Status status = Session.Status.fromDbValue(item.getSessionStatus());
        if (status == null) {
            throw new IllegalStateException("Trạng thái buổi không hợp lệ: " + item.getSessionId());
        }

        if (status == Session.Status.SCHEDULED) {
            if (!end.isAfter(now)) {
                return "Đang cập nhật";      // đã hết giờ nhưng hệ thống chưa chốt kết quả
            }
            if (!start.isAfter(now)) {
                return "Đang diễn ra";
            }
            boolean isToday = item.getSlotDate().equals(now.toLocalDate());
            return isToday ? "Hôm nay" : "Sắp tới";
        }

        return switch (status) {
            case CHECKED_IN -> "Đã check-in";
            case MISSED -> "Vắng";
            case CANCELLED -> "Đã hủy";
            default -> throw new IllegalStateException("Trạng thái buổi không hợp lệ");
        };
    }

    /**
     * HÀM PHỤ — gọi ở B4 của classify; validateDrafts cũng gọi để chặn khung mới trùng ca nghỉ.
     * Ca sáng = khung BẮT ĐẦU trước 12:00, ca chiều = bắt đầu từ 12:00. Chỉ đơn Pending/Approved mới khóa.
     */
    static boolean isOnLeave(PtScheduleItem item, List<LeaveInfo> leaves) {
        boolean morning = item.getStartTime().isBefore(LocalTime.NOON);

        for (LeaveInfo leave : leaves) {
            boolean sameDay = item.getSlotDate().equals(leave.getLeaveDate());
            if (!sameDay || !isActiveLeave(leave)) {
                continue;
            }

            boolean fullDay = "FullDay".equals(leave.getShiftType());
            boolean morningLeave = "Morning".equals(leave.getShiftType()) && morning;
            boolean afternoonLeave = "Afternoon".equals(leave.getShiftType()) && !morning;
            if (fullDay || morningLeave || afternoonLeave) {
                return true;
            }
        }
        return false;
    }

    /** HÀM PHỤ — đơn chờ duyệt và đã duyệt đều khóa khung; đơn bị từ chối thì không. */
    private static boolean isActiveLeave(LeaveInfo leave) {
        return "Pending".equals(leave.getStatus()) || "Approved".equals(leave.getStatus());
    }


    // ================================================================================================================
    // PHẦN 3. CÔNG BỐ — publish là hàm chính; mọi kiểm tra và ghi nằm trong publishInTransaction
    // ================================================================================================================

    /**
     * HÀM CHÍNH — PtScheduleServlet.doPost gọi khi PT bấm Công bố.
     *
     * Vào:  dates + starts  = các khung MỚI (cặp ngày, giờ bắt đầu) trình duyệt gửi lên
     *       removeIds       = mã các khung muốn GỠ, ví dụ {"773"}
     *       expectedMinutes = P-06 mà trang đang dùng (để phát hiện cấu hình vừa bị đổi)
     * Ra:   số khung đã thay đổi + câu thông báo; lỗi thì không lưu gì cả (rollback).
     */
    public ServiceResult<Integer> publish(int accountId, String[] dates, String[] starts, String[] removeIds,
            int expectedMinutes, LocalDateTime now) {

        // B1: phải có ít nhất một thay đổi; ngày và giờ của khung mới phải đi thành cặp.
        boolean hasDrafts = dates != null && dates.length > 0;
        boolean hasRemovals = removeIds != null && removeIds.length > 0;
        boolean pairsBroken = hasDrafts && (starts == null || dates.length != starts.length);
        if ((!hasDrafts && !hasRemovals) || pairsBroken) {
            return ServiceResult.fail("Chọn ít nhất một khung nháp hoặc khung cần gỡ trước khi công bố.");
        }

        try (Connection connection = DBContext.getConnection()) {

            // B2: mở transaction: hoặc lưu TẤT CẢ, hoặc không lưu gì.
            connection.setAutoCommit(false);
            try {
                ServiceResult<Integer> result = publishInTransaction(connection, accountId, dates, starts,
                        removeIds, expectedMinutes, now);
                if (result.isSuccess()) {
                    connection.commit();
                } else {
                    connection.rollback();
                }
                return result;

            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }

        } catch (IllegalArgumentException exception) {
            // B3: dữ liệu nháp sai (trùng giờ, ngoài giờ mở cửa...) → báo đúng câu lỗi để PT sửa.
            return ServiceResult.fail(exception.getMessage());

        } catch (SQLException | IllegalStateException | DateTimeException exception) {
            // Lỗi hệ thống → câu chung, không lộ chi tiết SQL.
            exception.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /**
     * HÀM PHỤ — gọi ở B2 của publish (test DB gọi thẳng hàm này để rollback được).
     * Connection đã tắt autoCommit; publish quyết định commit hay rollback.
     *
     * Thứ tự: khóa → GỠ trước → THÊM sau.
     * Gỡ trước để PT có thể gỡ khung 09:30 rồi đặt khung mới vào đúng chỗ đó trong cùng một lần.
     */
    ServiceResult<Integer> publishInTransaction(Connection connection, int accountId, String[] dates,
            String[] starts, String[] removeIds, int expectedMinutes, LocalDateTime now) throws SQLException {

        // B1: khóa hồ sơ PT + cấu hình: hai lần Công bố cùng lúc phải xếp hàng, không chen nhau.
        Integer ptId = scheduleDAO.lockPtForPublish(connection, accountId);
        if (ptId == null) {
            return ServiceResult.fail(Messages.MSG07_NO_PERMISSION);
        }
        scheduleDAO.lockScheduleConfigs(connection);

        int minutes = configService.getInt(SystemConfigService.SESSION_DURATION_MINUTES);
        LocalTime[] hours = parseOpeningHours(configService.getString(SystemConfigService.OPENING_HOURS));
        if (minutes <= 0 || minutes >= 24 * 60) {
            throw new IllegalStateException("P-06 không hợp lệ");
        }
        if (minutes != expectedMinutes) {
            return ServiceResult.fail("Thời lượng buổi đã thay đổi. Tải lại lịch và kiểm tra nháp trước khi công bố.");
        }

        // B2: GỠ khung (DBR-36: chỉ Free → Withdrawn, giữ dòng làm lịch sử).
        //     Một khung không còn gỡ được → báo lỗi, publish sẽ rollback cả lần gửi.
        List<Integer> slotIds = parseRemoveIds(removeIds);
        for (int slotId : slotIds) {
            boolean withdrawn = scheduleDAO.withdrawFreeSlot(connection, ptId, slotId, now);
            if (!withdrawn) {
                throw new IllegalArgumentException("Có khung không còn gỡ được (khách vừa giữ/đặt hoặc đã tới giờ). "
                        + "Tải lại lịch rồi thử lại.");
            }
        }

        boolean onlyRemovals = dates == null || dates.length == 0;
        if (onlyRemovals) {
            return ServiceResult.ok(slotIds.size(), "Đã gỡ " + slotIds.size() + " khung giờ.");
        }

        // B3: THÊM khung: Service tự tính giờ kết thúc, rồi đọc dữ liệu cần để kiểm tra (một lần).
        List<PtSlotDraft> drafts = buildDrafts(dates, starts, minutes);
        LocalDate fromDate = drafts.get(0).getSlotDate();
        LocalDate toDate = drafts.get(drafts.size() - 1).getSlotDate();
        List<HourException> exceptions = scheduleDAO.findHourExceptions(connection, fromDate, toDate);
        List<LeaveInfo> leaves = scheduleDAO.findLeaves(connection, ptId, fromDate, toDate);
        List<PtScheduleItem> existing = scheduleDAO.findSlotsForPublish(connection, ptId, fromDate, toDate);
        validateDrafts(drafts, existing, leaves, exceptions, hours, now);

        // B4: mọi khung đều hợp lệ mới ghi. Khung trùng giờ một khung Withdrawn cũ thì mở lại khung cũ.
        for (PtSlotDraft draft : drafts) {
            scheduleDAO.insertOrReopenSlot(connection, ptId, draft);
        }

        String message = "Đã công bố " + drafts.size() + " khung giờ";
        if (slotIds.isEmpty()) {
            message = message + ".";
        } else {
            message = message + ", gỡ " + slotIds.size() + " khung giờ.";
        }
        return ServiceResult.ok(drafts.size() + slotIds.size(), message);
    }

    /**
     * HÀM PHỤ — gọi ở B2 của publishInTransaction.
     * Mã khung từ trình duyệt ("57") → số 57. Bỏ mã trùng. Mã không phải số dương → báo lỗi.
     */
    static List<Integer> parseRemoveIds(String[] removeIds) {
        List<Integer> ids = new ArrayList<>();
        if (removeIds == null) {
            return ids;
        }

        for (String value : removeIds) {
            try {
                String text = value == null ? "" : value.trim();
                int id = Integer.parseInt(text);
                if (id <= 0) {
                    throw new NumberFormatException();
                }
                if (!ids.contains(id)) {
                    ids.add(id);
                }
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Khung cần gỡ không hợp lệ. Tải lại lịch rồi chọn lại.");
            }
        }
        return ids;
    }

    /**
     * HÀM PHỤ — gọi ở B3 của publishInTransaction.
     * Cặp (ngày, giờ bắt đầu) dạng chữ → danh sách PtSlotDraft, xếp theo ngày rồi giờ.
     * Giờ kết thúc = giờ bắt đầu + P-06, KHÔNG nhận từ trình duyệt.
     */
    static List<PtSlotDraft> buildDrafts(String[] dates, String[] starts, int minutes) {
        boolean missing = dates == null || starts == null || dates.length == 0;
        if (missing || dates.length != starts.length) {
            throw new IllegalArgumentException("Danh sách ngày/giờ nháp không hợp lệ.");
        }

        List<PtSlotDraft> drafts = new ArrayList<>();
        for (int i = 0; i < dates.length; i++) {
            try {
                LocalDate date = LocalDate.parse(dates[i] == null ? "" : dates[i]);
                LocalTime start = LocalTime.parse(starts[i] == null ? "" : starts[i]);
                LocalTime end = start.plusMinutes(minutes);

                // Năm phải nằm trong giới hạn SQL DATE; giờ đúng mốc 15 phút, không giây lẻ, không qua ngày.
                boolean badYear = date.getYear() < 1 || date.getYear() > 9999;
                boolean offGrid = start.getMinute() % 15 != 0 || start.getSecond() != 0 || start.getNano() != 0;
                boolean overnight = !end.isAfter(start);
                if (badYear || offGrid || overnight) {
                    throw new IllegalArgumentException("Ngày/giờ nháp không hợp lệ; chọn mốc 15 phút, không qua ngày.");
                }

                drafts.add(new PtSlotDraft(date, start, end));
            } catch (DateTimeException exception) {
                throw new IllegalArgumentException("Ngày/giờ nháp không đúng định dạng.");
            }
        }

        // Xếp theo giờ để kiểm tra trùng chỉ cần so hai khung đứng liền nhau.
        drafts.sort(Comparator.comparing(PtSlotDraft::getSlotDate).thenComparing(PtSlotDraft::getStartTime));
        return drafts;
    }

    /**
     * HÀM PHỤ — gọi ở B3 của publishInTransaction.
     * Kiểm tra từng khung mới. Sai ở đâu → ném IllegalArgumentException kèm ngày giờ của khung sai.
     *
     *   1. Chưa bắt đầu (so với giờ hiện tại).
     *   2. Nằm trong giờ mở cửa của ngày đó, ngày đó không nghỉ.
     *   3. Không trùng ca nghỉ phép.
     *   4. Không chồng lên khung mới khác (được chạm mép: 08:00–09:30 rồi 09:30–11:00).
     *   5. Không chồng lên khung đã có trong DB (khung Withdrawn cũ thì được dùng lại nếu cùng giờ).
     */
    static void validateDrafts(List<PtSlotDraft> drafts, List<PtScheduleItem> existing, List<LeaveInfo> leaves,
            List<HourException> exceptions, LocalTime[] hours, LocalDateTime now) {

        PtSlotDraft previous = null;

        for (PtSlotDraft draft : drafts) {
            String label = DateTimeUtil.formatDate(draft.getSlotDate()) + " " + DateTimeUtil.formatTime(draft.getStartTime());

            // 1. Chưa bắt đầu.
            boolean started = !draft.getSlotDate().atTime(draft.getStartTime()).isAfter(now);
            if (started) {
                throw new IllegalArgumentException(label + ": giờ bắt đầu đã qua.");
            }

            // 2. Trong giờ mở cửa của ngày đó.
            PtScheduleDay day = buildDay(draft.getSlotDate(), now.toLocalDate(), hours[0], hours[1], exceptions);
            boolean outside = day.isClosedAllDay()
                    || draft.getStartTime().isBefore(day.getOpenTime())
                    || draft.getEndTime().isAfter(day.getCloseTime());
            if (outside) {
                throw new IllegalArgumentException(label + ": phòng nghỉ hoặc khung nằm ngoài giờ mở cửa.");
            }

            // 3. Không trùng ca nghỉ phép.
            PtScheduleItem candidate = new PtScheduleItem();
            candidate.setSlotDate(draft.getSlotDate());
            candidate.setStartTime(draft.getStartTime());
            if (isOnLeave(candidate, leaves)) {
                throw new IllegalArgumentException(label + ": trùng ca nghỉ phép.");
            }

            // 4. Không chồng lên khung mới đứng ngay trước (danh sách đã xếp theo giờ).
            boolean sameDayAsPrevious = previous != null && previous.getSlotDate().equals(draft.getSlotDate());
            if (sameDayAsPrevious && previous.getEndTime().isAfter(draft.getStartTime())) {
                throw new IllegalArgumentException(label + ": trùng một khung khác trong danh sách nháp.");
            }

            // 5. Không chồng lên khung đã có trong DB.
            for (PtScheduleItem slot : existing) {
                if (!slot.getSlotDate().equals(draft.getSlotDate())) {
                    continue;
                }

                if ("Withdrawn".equals(slot.getKind())) {
                    // Khung đã gỡ cùng giờ bắt đầu sẽ được mở lại, nên giờ kết thúc phải khớp (giữ lịch sử).
                    boolean sameStart = slot.getStartTime().equals(draft.getStartTime());
                    boolean differentEnd = !slot.getEndTime().equals(draft.getEndTime());
                    if (sameStart && differentEnd) {
                        throw new IllegalArgumentException(label + ": khung cũ có thời lượng khác. Chọn giờ bắt đầu khác.");
                    }
                    continue;
                }

                boolean overlap = draft.getStartTime().isBefore(slot.getEndTime())
                        && slot.getStartTime().isBefore(draft.getEndTime());
                if (overlap) {
                    throw new IllegalArgumentException(label + ": trùng khung đã công bố "
                            + DateTimeUtil.formatTime(slot.getStartTime()) + "–" + DateTimeUtil.formatTime(slot.getEndTime()) + ".");
                }
            }

            previous = draft;
        }
    }


    // ================================================================================================================
    // PHẦN 4. HÀM DÙNG CHUNG — ngày trên URL, tuần, giờ mở cửa, đổi giờ ↔ phút
    // ================================================================================================================

    /**
     * HÀM PHỤ — PtScheduleServlet.loadWeek gọi.
     * Đọc ngày trên URL dạng yyyy-MM-dd. Thiếu, sai, hoặc quá xa (không lùi/tiến tuần được) → trả today.
     */
    public static LocalDate parseDate(String value, LocalDate today) {
        if (value == null || value.isBlank()) {
            return today;
        }

        try {
            LocalDate date = LocalDate.parse(value);   // "abc", "2026-02-30" → lỗi → vào catch

            // Thử lùi 1 tuần và tiến 13 ngày: vượt giới hạn năm của Java thì cũng vào catch.
            // Nhờ vậy nút ‹ › không bao giờ gây lỗi 500.
            LocalDate weekStart = weekStartOf(date);
            weekStart.minusWeeks(1);
            weekStart.plusDays(13);

            return date;
        } catch (DateTimeException exception) {
            return today;
        }
    }

    /** HÀM PHỤ — ngày bất kỳ → Thứ 2 cùng tuần. Ví dụ 22/10/2026 và 25/10/2026 đều → 19/10/2026. */
    public static LocalDate weekStartOf(LocalDate date) {
        return date.with(DayOfWeek.MONDAY);
    }

    /**
     * HÀM PHỤ — gọi ở B3 của getWeek và B1 của publishInTransaction.
     * P-19 dạng "05:30-22:00" → {05:30, 22:00}. Sai dạng hoặc đóng trước giờ mở → báo lỗi cấu hình.
     */
    public static LocalTime[] parseOpeningHours(String value) {
        String[] parts = value.split("-", -1);
        if (parts.length != 2) {
            throw new IllegalStateException("P-19 không đúng dạng HH:mm-HH:mm");
        }

        LocalTime open = LocalTime.parse(parts[0].trim());
        LocalTime close = LocalTime.parse(parts[1].trim());
        if (!close.isAfter(open)) {
            throw new IllegalStateException("P-19 có giờ đóng cửa không sau giờ mở cửa");
        }
        return new LocalTime[]{open, close};
    }

    /** Giờ cuối trục làm tròn LÊN giờ chẵn: 22:30 → 23, 22:00 → 22. */
    private static int endHour(LocalTime time) {
        boolean hasMinutes = time.getMinute() > 0 || time.getSecond() > 0;
        return hasMinutes ? time.getHour() + 1 : time.getHour();
    }

    /** Giờ → số phút từ 00:00. Ví dụ 09:30 → 570. */
    private static int minuteOfDay(LocalTime time) {
        return time.toSecondOfDay() / 60;
    }

    /** Làm tròn LÊN mốc 15 phút: 331 → 345, 330 → 330. */
    private static int roundUpTo15(int minute) {
        return (minute + 14) / 15 * 15;
    }

    /** Làm tròn XUỐNG mốc 15 phút: 1179 → 1170. */
    private static int roundDownTo15(int minute) {
        return Math.floorDiv(minute, 15) * 15;
    }
}
