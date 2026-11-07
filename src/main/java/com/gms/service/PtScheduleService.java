package com.gms.service;

import com.gms.constant.AppConstants;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * MÀN LỊCH DẠY — dựng lịch tuần (getWeek) và Công bố / gỡ khung rảnh (publish).
 * Hàm static dùng chung: parseOpeningHours (P-19), isOnLeave (khung trùng ca nghỉ), weekStartOf — PtSaleService, PtSessionHandle dùng lại.
 */
public class PtScheduleService {
    private final PtScheduleDAO scheduleDAO = new PtScheduleDAO();
    private final SystemConfigService configService = new SystemConfigService();

    // Dựng lịch 1 tuần (T2 → CN) chứa ngày anyDay. Trả về: ok(PtScheduleWeek) | fail(MSG07 không phải PT, MSG04 lỗi).
    public ServiceResult<PtScheduleWeek> getWeek(int accountId, LocalDate anyDay, LocalDateTime now) {
        try {
            // B1: tài khoản → EmployeeId của PT đang làm (PtScheduleDAO.findPtEmployeeId); không phải → MSG07
            Integer ptId = scheduleDAO.findPtEmployeeId(accountId);
            if (ptId == null) {
                return ServiceResult.fail(Messages.MSG07_NO_PERMISSION);
            }

            // B2: khoảng tuần thứ Hai → Chủ nhật; đọc P-06 (thời lượng buổi) + P-19 (giờ mở cửa)
            LocalDate monday = weekStartOf(anyDay);
            LocalDate sunday = monday.plusDays(6);

            int minutes = configService.getInt(SystemConfigService.SESSION_DURATION_MINUTES);
            LocalTime[] hours = parseOpeningHours(configService.getString(SystemConfigService.OPENING_HOURS));

            // B3: đọc DB (PtScheduleDAO): buổi có khách · khung Free/Held · đơn nghỉ · ngày giờ đặc biệt
            List<PtScheduleItem> sessions = scheduleDAO.findSessions(ptId, monday, sunday);
            List<PtScheduleItem> openSlots = scheduleDAO.findOpenSlots(ptId, monday, sunday);
            List<LeaveInfo> leaves = scheduleDAO.findLeaves(ptId, monday, sunday);
            List<HourException> exceptions = scheduleDAO.findHourExceptions(monday, sunday);

            // B4: 7 ngày rỗng (giờ mở cửa, phòng nghỉ, nhãn nghỉ phép, khoảng được thêm khung) — buildEmptyWeek
            PtScheduleWeek week = buildEmptyWeek(
                    monday,
                    now,
                    exceptions,
                    leaves,
                    minutes,
                    hours[0],
                    hours[1]);

            // B5: gộp buổi + khung, xếp theo giờ → phân loại từng ô và xếp vào đúng ngày (fillWeek)
            List<PtScheduleItem> allItems = merge(sessions, openSlots, now);

            fillWeek(
                    week,
                    allItems,
                    leaves,
                    now);

            return ServiceResult.ok(week);

        } catch (SQLException | IllegalStateException | DateTimeException exception) {
            exception.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /**
     * HÀM PHỤ (getWeek B4) — tuần chưa có ô: ngày đầu/cuối, tuần trước/sau, trục giờ, 7 PtScheduleDay.
     */
    static PtScheduleWeek buildEmptyWeek(LocalDate monday, LocalDateTime now,
            List<HourException> exceptions, List<LeaveInfo> leaves, int sessionMinutes,
            LocalTime open, LocalTime close) {
        // B1: cấu hình P-06 / P-19 sai → báo lỗi (Service trả MSG04)
        boolean badDuration = sessionMinutes <= 0;
        boolean badHours = !close.isAfter(open);
        if (badDuration || badHours) {
            throw new IllegalStateException("Cấu hình lịch dạy không hợp lệ");
        }

        // B2: thông tin tuần + trục giờ mặc định theo P-19
        PtScheduleWeek week = new PtScheduleWeek();
        week.setWeekStart(monday);
        week.setWeekEnd(monday.plusDays(6));
        week.setPrevWeek(monday.minusWeeks(1));
        week.setNextWeek(monday.plusWeeks(1));
        week.setCurrentWeek(monday.equals(weekStartOf(now.toLocalDate())));
        week.setSessionMinutes(sessionMinutes);
        week.setAxisStartHour(open.getHour());
        week.setAxisEndHour(endHour(close));

        // B3: 7 ngày: buildDay (giờ ngày đó) · nhãn + chi tiết nghỉ phép · khoảng được thêm khung (setAddRange); nới trục giờ theo từng ngày
        List<PtScheduleDay> days = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            PtScheduleDay day = buildDay(
                    monday.plusDays(i),
                    now.toLocalDate(),
                    open,
                    close,
                    exceptions);
            day.setLeaveLabel(buildLeaveLabel(day.getDate(), leaves));
            day.setLeaveDetails(buildLeaveDetails(day.getDate(), leaves));
            setAddRange(day, now, sessionMinutes);
            days.add(day);

            if (!day.isClosedAllDay()) {
                week.setAxisStartHour(Math.min(week.getAxisStartHour(), day.getOpenTime().getHour()));
                week.setAxisEndHour(Math.max(week.getAxisEndHour(), endHour(day.getCloseTime())));
            }
        }

        week.setDays(days);
        return week;
    }

    /**
     * HÀM PHỤ (buildEmptyWeek, validateDrafts) — 1 ngày: tên thứ, hôm nay / đã qua, giờ mở cửa theo P-19;
     * ngày có OperatingHourExceptions → đổi giờ hoặc "Phòng nghỉ" cả ngày. Giờ ngoại lệ sai → lỗi.
     */
    static PtScheduleDay buildDay(LocalDate date, LocalDate today, LocalTime open,
            LocalTime close, List<HourException> exceptions) {
        PtScheduleDay day = new PtScheduleDay();
        day.setDate(date);
        day.setDayName(dayName(date));
        day.setToday(date.equals(today));
        day.setPast(date.isBefore(today));
        day.setOpenTime(open);
        day.setCloseTime(close);
        day.setItems(new ArrayList<>());

        for (HourException exception : exceptions) {
            if (!date.equals(exception.getApplyDate())) {
                continue;
            }

            day.setClosedAllDay(exception.isClosedAllDay());
            day.setOpenTime(exception.getOpenTime());
            day.setCloseTime(exception.getCloseTime());

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
     * HÀM PHỤ (buildEmptyWeek) — "Nghỉ phép ca sáng · chờ duyệt; …" cho các đơn Pending/Approved của ngày. "" = không nghỉ.
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

        return String.join("; ", labels);
    }

    /**
     * HÀM PHỤ — đơn nghỉ còn hiệu lực = Pending hoặc Approved (đơn chờ duyệt cũng khóa khung).
     */
    private static boolean isActiveLeave(LeaveInfo leave) {
        return "Pending".equals(leave.getStatus()) || "Approved".equals(leave.getStatus());
    }

    /**
     * HÀM PHỤ (buildEmptyWeek) — nhãn + "Lý do: …" của từng đơn, hiện khi mở chi tiết nghỉ phép.
     */
    static String buildLeaveDetails(LocalDate date, List<LeaveInfo> leaves) {
        List<String> details = new ArrayList<>();
        for (LeaveInfo leave : leaves) {
            if (date.equals(leave.getLeaveDate()) && isActiveLeave(leave)) {
                String reason = leave.getReason();
                details.add(buildLeaveLabel(date, List.of(leave)) + "\nLý do: "
                        + (reason == null || reason.isBlank() ? "Không có thông tin." : reason));
            }
        }
        return String.join("\n\n", details);
    }

    /**
     * HÀM PHỤ (buildEmptyWeek) — phút sớm nhất / muộn nhất được bắt đầu khung mới trong ngày (mốc 15 phút):
     * từ giờ mở cửa (hôm nay: từ sau giờ hiện tại) tới giờ đóng cửa − thời lượng buổi. Ngày đã qua / phòng nghỉ → không thêm được.
     */
    static void setAddRange(PtScheduleDay day, LocalDateTime now, int sessionMinutes) {
        if (day.isPast() || day.isClosedAllDay()) {
            return;
        }

        int first = roundUpTo15(minuteOfDay(day.getOpenTime()));
        if (day.isToday()) {
            int afterNow = roundUpTo15(minuteOfDay(now.toLocalTime()) + 1);
            first = Math.max(first, afterNow);
        }

        int last = roundDownTo15(minuteOfDay(day.getCloseTime()) - sessionMinutes);

        if (first > last) {
            return;
        }
        day.setFirstStartMinute(first);
        day.setLastStartMinute(last);
    }

    /**
     * HÀM PHỤ (getWeek B5) — gộp buổi + khung trống thành 1 danh sách, xếp theo ngày rồi giờ.
     */
    static List<PtScheduleItem> merge(List<PtScheduleItem> sessions, List<PtScheduleItem> openSlots) {
        List<PtScheduleItem> items = new ArrayList<>(sessions);
        items.addAll(openSlots);
        items.sort(Comparator.comparing(PtScheduleItem::getSlotDate).thenComparing(PtScheduleItem::getStartTime));
        return items;
    }

    /**
     * HÀM PHỤ (getWeek B5) — như merge(sessions, openSlots) nhưng mỗi khung giờ chỉ còn MỘT ô.
     * Buổi bị hủy (PT hủy, gói hết hạn, phòng nghỉ…) trả khung về Free, nên cùng TimeSlotId có cả ô "Đã hủy"
     * lẫn ô "Còn trống" (hoặc ô buổi mới của khách khác) → lịch vẽ 2 ô chồng nhau.
     *   Khung đã có buổi khác chưa hủy          → bỏ ô "Đã hủy".
     *   Khung còn trống, chưa tới giờ bắt đầu    → bỏ ô "Đã hủy", giữ ô "Còn trống" (PT còn mở cho khách khác).
     *   Khung còn trống, đã qua giờ bắt đầu      → bỏ ô "Còn trống", giữ ô "Đã hủy" (để PT xem lại lịch sử).
     * Ví dụ: 05/11 15:00 buổi của Hương hủy vì gói hết hạn, khung về Free → xem ngày 07/11 chỉ còn ô "Đã hủy";
     *        16/11 15:00 cùng trường hợp → chỉ còn ô "Còn trống".
     */
    static List<PtScheduleItem> merge(List<PtScheduleItem> sessions, List<PtScheduleItem> openSlots, LocalDateTime now) {
        Set<Integer> liveSlots = new HashSet<>();
        Set<Integer> cancelledSlots = new HashSet<>();
        for (PtScheduleItem session : sessions) {
            if ("Cancelled".equals(session.getSessionStatus())) {
                cancelledSlots.add(session.getTimeSlotId());
            } else {
                liveSlots.add(session.getTimeSlotId());
            }
        }

        Set<Integer> futureOpen = new HashSet<>();
        List<PtScheduleItem> keptSlots = new ArrayList<>();
        for (PtScheduleItem slot : openSlots) {
            boolean started = !LocalDateTime.of(slot.getSlotDate(), slot.getStartTime()).isAfter(now);
            if (cancelledSlots.contains(slot.getTimeSlotId()) && started) {
                continue;
            }
            if (!started) {
                futureOpen.add(slot.getTimeSlotId());
            }
            keptSlots.add(slot);
        }

        List<PtScheduleItem> keptSessions = new ArrayList<>();
        for (PtScheduleItem session : sessions) {
            boolean cancelled = "Cancelled".equals(session.getSessionStatus());
            int slotId = session.getTimeSlotId();
            if (cancelled && (liveSlots.contains(slotId) || futureOpen.contains(slotId))) {
                continue;
            }
            keptSessions.add(session);
        }
        return merge(keptSessions, keptSlots);
    }

    /**
     * HÀM PHỤ (getWeek B5) — nới trục giờ theo ô sớm/muộn nhất; mỗi ô: classify → tính vị trí → bỏ vào đúng ngày;
     * buổi có khách trùng ca nghỉ → thêm vào day.leaveBookings.
     */
    static void fillWeek(
            PtScheduleWeek week,
            List<PtScheduleItem> items,
            List<LeaveInfo> leaves,
            LocalDateTime now) {
        for (PtScheduleItem item : items) {
            week.setAxisStartHour(Math.min(week.getAxisStartHour(), item.getStartTime().getHour()));
            week.setAxisEndHour(Math.max(week.getAxisEndHour(), endHour(item.getEndTime())));
        }

        int axisStartMinute = week.getAxisStartHour() * 60;
        for (PtScheduleItem item : items) {
            classify(item, leaves, now);

            item.setStartMinute(minuteOfDay(item.getStartTime()) - axisStartMinute);

            for (PtScheduleDay day : week.getDays()) {
                if (day.getDate().equals(item.getSlotDate())) {
                    day.getItems().add(item);
                    if ("Scheduled".equals(item.getSessionStatus()) && isOnLeave(item, leaves)) {
                        day.getLeaveBookings().add(item.getStartTime() + " – " + item.getEndTime()
                                + " · " + item.getCustomerNames() + " · " + item.getStatusLabel());
                    }
                    break;
                }
            }
        }

        // Ô chồng giờ (VD buổi đã hủy 19:30 nằm cùng giờ khung mới 19:15) → chia làn RIÊNG cho cụm đó,
        // các ô khác trong ngày vẫn rộng đủ cột.
        for (PtScheduleDay day : week.getDays()) {
            assignLanes(day.getItems());
        }
    }

    /**
     * HÀM PHỤ (fillWeek) — xếp làn cho các ô CHỒNG GIỜ trong 1 ngày, giống Google Calendar.
     * Input: ô của 1 ngày. Output: mỗi ô có lane (làn thứ mấy) + laneCount (cụm có mấy làn) → JSP in --lane / --lanes.
     *
     * Ví dụ 1 ngày: 07:00–08:30 · 19:00–20:30 · 20:00–21:30
     *   Cụm 1 = {07:00}            → 1 làn  → ô 07:00 rộng đủ cột
     *   Cụm 2 = {19:00, 20:00}     → 2 làn  → 19:00 ở làn 0 (nửa trái), 20:00 ở làn 1 (nửa phải)
     * Trước đây cột ngày là CSS grid tự thêm cột khi có ô chồng → MỌI ô trong ngày bị co (lỗi "1 cái co là tất cả co").
     *
     * B1: xếp ô theo giờ bắt đầu; cùng giờ thì ô đang chiếm giờ (khung trống / buổi Scheduled) đứng trước,
     *     buổi đã hủy / vắng / check-in (chỉ để xem) dạt sang phải.
     * B2: duyệt từng ô; ô bắt đầu khi cụm hiện tại đã kết thúc → chốt số làn cho cụm cũ, mở cụm mới.
     * B3: đặt ô vào làn đầu tiên đã trống (làn kết thúc trước giờ bắt đầu ô); hết làn trống → mở làn mới.
     * B4: chốt cụm cuối.
     */
    static void assignLanes(List<PtScheduleItem> items) {
        // B1
        List<PtScheduleItem> sorted = new ArrayList<>(items);
        sorted.sort(Comparator.comparing(PtScheduleItem::getStartTime)
                .thenComparing(item -> item.isBlocking() ? 0 : 1));

        List<PtScheduleItem> cluster = new ArrayList<>();   // các ô của cụm đang xét
        List<LocalTime> laneEnds = new ArrayList<>();       // giờ kết thúc ô cuối của từng làn trong cụm
        LocalTime clusterEnd = null;                        // giờ kết thúc muộn nhất của cụm

        for (PtScheduleItem item : sorted) {
            // B2
            boolean startsNewCluster = clusterEnd != null && !item.getStartTime().isBefore(clusterEnd);
            if (startsNewCluster) {
                closeCluster(cluster, laneEnds.size());
                cluster.clear();
                laneEnds.clear();
                clusterEnd = null;
            }

            // B3
            int lane = -1;
            for (int i = 0; i < laneEnds.size(); i++) {
                if (!laneEnds.get(i).isAfter(item.getStartTime())) {
                    lane = i;
                    break;
                }
            }
            if (lane < 0) {
                lane = laneEnds.size();
                laneEnds.add(item.getEndTime());
            } else {
                laneEnds.set(lane, item.getEndTime());
            }
            item.setLane(lane);
            cluster.add(item);
            clusterEnd = clusterEnd == null || item.getEndTime().isAfter(clusterEnd) ? item.getEndTime() : clusterEnd;
        }

        // B4
        closeCluster(cluster, laneEnds.size());
    }

    /** HÀM PHỤ (assignLanes) — ghi số làn của cụm vào mọi ô trong cụm (cụm rỗng thì thôi). */
    private static void closeCluster(List<PtScheduleItem> cluster, int laneCount) {
        for (PtScheduleItem item : cluster) {
            item.setLaneCount(Math.max(1, laneCount));
        }
    }

    /**
     * HÀM PHỤ (fillWeek) — quyết định 1 ô hiện thế nào:
     */
    static void classify(PtScheduleItem item, List<LeaveInfo> leaves, LocalDateTime now) {
        LocalDateTime start = LocalDateTime.of(item.getSlotDate(), item.getStartTime());
        LocalDateTime end = LocalDateTime.of(item.getSlotDate(), item.getEndTime());
        item.setPast(!end.isAfter(now));
        item.setCanRemove(false);
        item.setCanHandle(false);

        item.setStartMinute(minuteOfDay(item.getStartTime()) - 5 * 60);

        item.setLengthMinutes((int) Duration.between(item.getStartTime(), item.getEndTime()).toMinutes());

        boolean notStarted = start.isAfter(now);

        // B1: có buổi → "booked", nhãn theo trạng thái buổi; Scheduled chưa bắt đầu → được bấm Xử lý (canHandle)
        if (item.getSessionId() != null) {
            item.setKind("booked");
            item.setStatusLabel(sessionLabel(
                    item,
                    start,
                    end,
                    now));
            boolean scheduled = "Scheduled".equals(item.getSessionStatus());
            item.setCanHandle(scheduled && notStarted);
            return;
        }

        // B2: khung đang giữ cho đơn tại quầy (Bán gói PT) → "held", "Đang giữ · đơn chờ thanh toán"
        if ("Held".equalsIgnoreCase(item.getKind())) {
            item.setKind("held");
            item.setStatusLabel("Đang giữ · đơn chờ thanh toán");
            return;
        }

        // B3: khung trống trùng ca nghỉ → "locked", "Đã duyệt nghỉ" | "Chờ duyệt nghỉ"
        if (isOnLeave(item, leaves)) {
            item.setKind("locked");
            boolean approved = leaves.stream().anyMatch(leave -> "Approved".equals(leave.getStatus())
                    && isOnLeave(item, List.of(leave)));
            item.setStatusLabel(approved ? "Đã duyệt nghỉ" : "Chờ duyệt nghỉ");
            return;
        }

        // B4: còn lại → "free", "Còn trống"; chưa bắt đầu → được tích Gỡ (canRemove)
        item.setKind("free");
        item.setStatusLabel("Còn trống");
        item.setCanRemove(notStarted);
    }

    /**
     * HÀM PHỤ (classify B1) — Scheduled: "Sắp tới" | "Đang diễn ra" | "Chưa chốt" (đã hết giờ, chờ JOB-04);
     * còn lại: "Đã check-in" | "Vắng" | "Đã hủy".
     */
    private static String sessionLabel(PtScheduleItem item, LocalDateTime start,
            LocalDateTime end, LocalDateTime now) {
        Session.Status status = Session.Status.fromDbValue(item.getSessionStatus());
        if (status == null) {
            throw new IllegalStateException("Trạng thái buổi không hợp lệ: " + item.getSessionId());
        }

        if (status == Session.Status.SCHEDULED) {
            if (!end.isAfter(now)) {
                return "Chưa chốt";
            }
            if (!start.isAfter(now)) {
                return "Đang diễn ra";
            }
            return "Sắp tới";
        }

        return switch (status) {
            case CHECKED_IN -> "Đã check-in";
            case MISSED -> "Vắng";
            case CANCELLED -> "Đã hủy";
            default -> throw new IllegalStateException("Trạng thái buổi không hợp lệ");
        };
    }

    /**
     * HÀM PHỤ (dùng chung) — khung có trùng đơn nghỉ không: FullDay · Morning (bắt đầu trước 13:00) · Afternoon (từ 13:00).
     */
    static boolean isOnLeave(PtScheduleItem item, List<LeaveInfo> leaves) {
        boolean morning = item.getStartTime().isBefore(AppConstants.AFTERNOON_SHIFT_START);

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

    // Nút "Công bố": thêm khung nháp + gỡ khung đã tích, TẤT CẢ trong 1 transaction (lỗi 1 khung → không lưu gì).
    public ServiceResult<Integer> publish(int accountId, String[] dates, String[] starts, String[] removeIds,
            int expectedMinutes, LocalDateTime now) {
        // B1: không có gì để lưu / mảng ngày giờ lệch nhau → MSG320
        boolean hasDrafts = dates != null && dates.length > 0;
        boolean hasRemovals = removeIds != null && removeIds.length > 0;
        boolean pairsBroken = hasDrafts && (starts == null || dates.length != starts.length);
        if ((!hasDrafts && !hasRemovals) || pairsBroken) {
            return ServiceResult.fail(Messages.MSG320_PT_PUBLISH_NOTHING);
        }

        try (Connection connection = DBContext.getConnection()) {
            // B2: mở transaction → publishInTransaction; ok → commit, fail / lỗi → rollback
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

        // Lỗi do dữ liệu (khung trùng, đã qua giờ…) → trả đúng câu lỗi cho PT
        } catch (IllegalArgumentException exception) {
            return ServiceResult.fail(exception.getMessage());

        } catch (SQLException | IllegalStateException | DateTimeException exception) {
            exception.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /**
     * HÀM PHỤ (publish B2) — các bước ghi trong transaction.
     */
    ServiceResult<Integer> publishInTransaction(Connection connection, int accountId, String[] dates,
            String[] starts, String[] removeIds, int expectedMinutes, LocalDateTime now) throws SQLException {
        // B1: KHÓA dòng PT (Employees UPDLOCK) → 2 tab / 2 lễ tân cùng ghi lịch PT này phải xếp hàng; không phải PT → MSG07
        Integer ptId = scheduleDAO.lockPtForPublish(connection, accountId);
        if (ptId == null) {
            return ServiceResult.fail(Messages.MSG07_NO_PERMISSION);
        }

        // B2: khóa P-06, P-19 (không cho Admin đổi giữa chừng); P-06 khác lúc mở trang → MSG321 (tải lại trang)
        scheduleDAO.lockScheduleConfigs(connection);

        int minutes = configService.getInt(SystemConfigService.SESSION_DURATION_MINUTES);
        LocalTime[] hours = parseOpeningHours(configService.getString(SystemConfigService.OPENING_HOURS));
        if (minutes <= 0 || minutes >= 24 * 60) {
            throw new IllegalStateException("P-06 không hợp lệ");
        }
        if (minutes != expectedMinutes) {
            return ServiceResult.fail(Messages.MSG321_PT_DURATION_CHANGED);
        }

        // B3: gỡ khung đã tích: TimeSlots Free → Withdrawn (withdrawFreeSlot); 1 khung không gỡ được (vừa có khách giữ / đã tới giờ) → lỗi cả lượt
        List<Integer> slotIds = parseRemoveIds(removeIds);
        for (int slotId : slotIds) {
            boolean withdrawn = scheduleDAO.withdrawFreeSlot(
                    connection,
                    ptId,
                    slotId,
                    now);
            if (!withdrawn) {
                throw new IllegalArgumentException("Có khung không còn gỡ được (khách vừa giữ/đặt hoặc đã tới giờ). "
                        + "Tải lại lịch rồi thử lại.");
            }
        }

        // B4: chỉ gỡ → xong (MSG322 "Đã gỡ N khung")
        boolean onlyRemovals = dates == null || dates.length == 0;
        if (onlyRemovals) {
            return ServiceResult.ok(slotIds.size(), String.format(Messages.MSG322_PT_SLOTS_REMOVED, slotIds.size()));
        }

        // B5: đọc khung nháp (buildDrafts) → đọc ngày đặc biệt, đơn nghỉ, khung hiện có (khóa) → kiểm từng khung (validateDrafts)
        List<PtSlotDraft> drafts = buildDrafts(dates, starts, minutes);
        LocalDate fromDate = drafts.get(0).getSlotDate();
        LocalDate toDate = drafts.get(drafts.size() - 1).getSlotDate();
        List<HourException> exceptions = scheduleDAO.findHourExceptions(connection, fromDate, toDate);
        List<LeaveInfo> leaves = scheduleDAO.findLeaves(
                connection,
                ptId,
                fromDate,
                toDate);
        List<PtScheduleItem> existing = scheduleDAO.findSlotsForPublish(
                connection,
                ptId,
                fromDate,
                toDate);
        validateDrafts(
                drafts,
                existing,
                leaves,
                exceptions,
                hours,
                now);

        // B6: ghi từng khung: có khung Withdrawn đúng giờ → mở lại Free; không → INSERT TimeSlots Free (insertOrReopenSlot)
        for (PtSlotDraft draft : drafts) {
            scheduleDAO.insertOrReopenSlot(connection, ptId, draft);
        }

        // B7: câu thông báo "Đã công bố N khung giờ[, gỡ M khung giờ]."
        String message = "Đã công bố " + drafts.size() + " khung giờ";
        if (slotIds.isEmpty()) {
            message = message + ".";
        } else {
            message = message + ", gỡ " + slotIds.size() + " khung giờ.";
        }
        return ServiceResult.ok(drafts.size() + slotIds.size(), message);
    }

    /**
     * HÀM PHỤ (publishInTransaction B3) — removeSlotId[] → danh sách Id (bỏ trùng); giá trị sai → lỗi "Khung cần gỡ không hợp lệ".
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
     * HÀM PHỤ (publishInTransaction B5) — slotDate[] + startTime[] → PtSlotDraft (kết thúc = bắt đầu + P-06),
     * bắt mốc 15 phút, không qua ngày; xếp theo ngày giờ.
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

        drafts.sort(Comparator.comparing(PtSlotDraft::getSlotDate).thenComparing(PtSlotDraft::getStartTime));
        return drafts;
    }

    /**
     * HÀM PHỤ (publishInTransaction B5) — kiểm từng khung nháp, sai ở đâu báo đúng "dd/MM HH:mm: lý do":
     */
    static void validateDrafts(List<PtSlotDraft> drafts, List<PtScheduleItem> existing, List<LeaveInfo> leaves,
            List<HourException> exceptions, LocalTime[] hours, LocalDateTime now) {
        PtSlotDraft previous = null;

        for (PtSlotDraft draft : drafts) {
            String label = DateTimeUtil.formatDate(draft.getSlotDate()) + " " + DateTimeUtil.formatTime(draft.getStartTime());

            // B1: giờ bắt đầu đã qua
            boolean started = !draft.getSlotDate().atTime(draft.getStartTime()).isAfter(now);
            if (started) {
                throw new IllegalArgumentException(label + ": giờ bắt đầu đã qua.");
            }

            // B2: phòng nghỉ / ngoài giờ mở cửa ngày đó
            PtScheduleDay day = buildDay(
                    draft.getSlotDate(),
                    now.toLocalDate(),
                    hours[0],
                    hours[1],
                    exceptions);
            boolean outside = day.isClosedAllDay()
                    || draft.getStartTime().isBefore(day.getOpenTime())
                    || draft.getEndTime().isAfter(day.getCloseTime());
            if (outside) {
                throw new IllegalArgumentException(label + ": phòng nghỉ hoặc khung nằm ngoài giờ mở cửa.");
            }

            // B3: trùng ca nghỉ phép
            PtScheduleItem candidate = new PtScheduleItem();
            candidate.setSlotDate(draft.getSlotDate());
            candidate.setStartTime(draft.getStartTime());
            if (isOnLeave(candidate, leaves)) {
                throw new IllegalArgumentException(label + ": trùng ca nghỉ phép.");
            }

            // B4: chồng lên khung nháp trước đó (danh sách đã xếp)
            boolean sameDayAsPrevious = previous != null && previous.getSlotDate().equals(draft.getSlotDate());
            if (sameDayAsPrevious && previous.getEndTime().isAfter(draft.getStartTime())) {
                throw new IllegalArgumentException(label + ": trùng một khung khác trong danh sách nháp.");
            }

            // B5: chồng lên khung đã có (khung Withdrawn cùng giờ bắt đầu mà khác thời lượng → không mở lại được)
            for (PtScheduleItem slot : existing) {
                if (!slot.getSlotDate().equals(draft.getSlotDate())) {
                    continue;
                }

                if ("Withdrawn".equals(slot.getKind())) {
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

    /**
     * HÀM PHỤ — "Thứ 2" … "Chủ nhật".
     */
    private static String dayName(LocalDate date) {
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        if (dayOfWeek == DayOfWeek.SUNDAY) {
            return "Chủ nhật";
        }
        return "Thứ " + (dayOfWeek.getValue() + 1);
    }

    /**
     * HÀM PHỤ (Servlet loadWeek) — ?date= → LocalDate; trống / sai định dạng → hôm nay.
     * (2 dòng minusWeeks / plusDays bên dưới không dùng kết quả — thừa, không ảnh hưởng.)
     */
    public static LocalDate parseDate(String value, LocalDate today) {
        if (value == null || value.isBlank()) {
            return today;
        }

        try {
            LocalDate date = LocalDate.parse(value);

            LocalDate weekStart = weekStartOf(date);
            weekStart.minusWeeks(1);
            weekStart.plusDays(13);

            return date;
        } catch (DateTimeException exception) {
            return today;
        }
    }

    /**
     * HÀM PHỤ — thứ Hai của tuần chứa date.
     */
    public static LocalDate weekStartOf(LocalDate date) {
        return date.with(DayOfWeek.MONDAY);
    }

    /**
     * HÀM PHỤ (dùng chung) — P-19 "06:00-22:00" → [06:00, 22:00]; sai dạng / đóng ≤ mở → lỗi cấu hình.
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

    /**
     * HÀM PHỤ — giờ làm tròn lên (21:30 → 22) để trục giờ chứa trọn khung.
     */
    private static int endHour(LocalTime time) {
        boolean hasMinutes = time.getMinute() > 0 || time.getSecond() > 0;
        return hasMinutes ? time.getHour() + 1 : time.getHour();
    }

    /**
     * HÀM PHỤ — LocalTime → số phút từ 00:00.
     */
    private static int minuteOfDay(LocalTime time) {
        return time.toSecondOfDay() / 60;
    }

    /**
     * HÀM PHỤ — làm tròn LÊN mốc 15 phút.
     */
    private static int roundUpTo15(int minute) {
        return (minute + 14) / 15 * 15;
    }

    /**
     * HÀM PHỤ — làm tròn XUỐNG mốc 15 phút.
     */
    private static int roundDownTo15(int minute) {
        return Math.floorDiv(minute, 15) * 15;
    }
}
