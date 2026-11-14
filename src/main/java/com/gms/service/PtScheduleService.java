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
 * SERVICE MÀN LỊCH DẠY PT — /pt/schedule: dựng lịch tuần (getWeek) và Công bố / gỡ khung rảnh (publish).
 *
 * AI GỌI SERVICE NÀY?  PtScheduleServlet (getWeek, publish, parseDate). Hàm static dùng lại ở màn khác:
 *                      parseOpeningHours, isOnLeave, weekStartOf → PtSaleService, PtSessionHandleService.
 * SERVICE GỌI AI?      PtScheduleDAO (PT, buổi, khung, đơn nghỉ, ngày giờ đặc biệt, ghi TimeSlots) · SystemConfigService (P-06, P-17).
 *
 * BỐ CỤC FILE — mỗi HÀM CHÍNH đứng trước, các hàm phụ của nó nằm ngay bên dưới:
 *   HÀM CHÍNH 1  getWeek   dựng lịch 1 tuần (T2 → CN)        → buildEmptyWeek, buildDay, dayName, buildLeaveLabel, buildLeaveDetails,
 *                                                              isActiveLeave, setAddRange, merge, fillWeek, assignLanes, closeCluster,
 *                                                              classify, sessionLabel
 *   HÀM CHÍNH 2  publish   nút "Công bố": thêm + gỡ khung     → publishInTransaction, parseRemoveIds, buildDrafts, validateDrafts
 *   DÙNG CHUNG             isOnLeave, parseDate, weekStartOf, parseOpeningHours, endHour, minuteOfDay, roundUpTo15, roundDownTo15
 *
 * LUẬT QUAN TRỌNG
 *   - Đơn nghỉ CHỜ DUYỆT cũng khóa khung trống trùng ca (isActiveLeave); buổi đã có khách không tự hủy, chỉ báo "trùng ca nghỉ".
 *   - Công bố là 1 transaction: 1 khung lỗi → không lưu khung nào. Khóa dòng PT trước khi ghi (2 tab cùng ghi phải xếp hàng).
 *   - Khung đã gỡ (Withdrawn) đúng giờ được mở lại thay vì thêm dòng mới.
 *   - Các hàm static (buildEmptyWeek, classify, assignLanes, validateDrafts…) không đọc DB → test gọi thẳng.
 */
public class PtScheduleService {
    private final PtScheduleDAO scheduleDAO = new PtScheduleDAO();
    private final SystemConfigService configService = new SystemConfigService();



    // ============================================================================================================
    // HÀM CHÍNH 1. getWeek — dựng lịch 1 tuần (T2 → CN) chứa ngày đang xem
    //   Ai gọi: PtScheduleServlet.loadWeek  ·  Ra: PtScheduleWeek → JSP biến "week"
    //   Hàm phụ ngay dưới, theo thứ tự getWeek gọi:
    //     buildEmptyWeek → buildDay → dayName · buildLeaveLabel / buildLeaveDetails → isActiveLeave · setAddRange
    //     merge (2 bản) → fillWeek → classify → sessionLabel · assignLanes → closeCluster
    // ============================================================================================================

    /**
     * HÀM CHÍNH 1 — dựng lịch 1 tuần (T2 → CN) chứa ngày đang xem.
     *   Ai gọi: PtScheduleServlet.loadWeek
     *   Trả về: ok(PtScheduleWeek) | lỗi (không phải PT / lỗi hệ thống)
     */
    public ServiceResult<PtScheduleWeek> getWeek(int accountId, LocalDate anyDay, LocalDateTime now) {
        try {
            // B1: tài khoản phải là PT đang làm, JOIN ACCOUNT, PERSON, EMPLOYEES
            Integer ptId = scheduleDAO.findPtEmployeeId(accountId);
            if (ptId == null) {
                return ServiceResult.fail(Messages.MSG07_NO_PERMISSION);
            }

            // B2: tìm thứ Hai → Chủ nhật, đọc thời lượng buổi + giờ mở cửa
            LocalDate monday = weekStartOf(anyDay); // monday = ngày đầu tuần của tuần của ngày được truyền vào
            LocalDate sunday = monday.plusDays(6); // sunday = monday + 6

            
            // Đọc P-06 từ SystemConfigs
            int minutes = configService.getInt(SystemConfigService.SESSION_DURATION_MINUTES);
            // Đọc Giờ đóng mở cửa mặc định
            LocalTime[] hours = parseOpeningHours(configService.getString(
                    SystemConfigService.OPENING_HOURS));

            // B3: đọc buổi có khách, khung trống, đơn nghỉ, ngày giờ đặc biệt
            
            // 3.1: Lấy thông tin buổi Scheduled - buổi có khách của PT (cả chưa bắt đầu và đã kết thúc trong khoảng ngày)
            List<PtScheduleItem> sessions = scheduleDAO.findSessions(ptId, monday, sunday); 
            
            // 3.2 Lấy những khung đang Free/Held, ko lấy khung Scheduled - nghĩa là chưa có sessionId, chưa thành buổi của khách
            List<PtScheduleItem> openSlots = scheduleDAO.findOpenSlots(ptId, monday, sunday);
            
            
            // 3.3 Đọc đơn PENDING or APPROVED, đơn REJECTED thì bỏ qua
            List<LeaveInfo> leaves = scheduleDAO.findLeaves(ptId, monday, sunday);
            
            // 3.4
            // câu SQL dùng HOLDLOCK giữ khóa trên những gì vừa đọc cho đến khi transaction kết thúc, thay vì nhả khóa ngay khi câu SELECT chạy xong.
            // getWeek ko cần nma có HOLDLOCK vx vô hại do sẽ autocommit bth
            // Đọc giờ đóng mở cửa ngoại lệ trong range từ t2 đến chủ nhật của tuần đang xem
            List<HourException> exceptions = scheduleDAO.findHourExceptions(monday, sunday);

            
            // B4: dựng 7 ngày rỗng
            PtScheduleWeek week = buildEmptyWeek(
                    monday,
                    now,
                    exceptions,
                    leaves,
                    minutes,
                    hours[0],
                    hours[1]);

            // B5: gộp buổi + khung rồi xếp vào từng ngày
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
    static PtScheduleWeek buildEmptyWeek(
            LocalDate monday,
            LocalDateTime now,
            List<HourException> exceptions,
            List<LeaveInfo> leaves,
            int sessionMinutes,
            LocalTime open,
            LocalTime close) {
        // B1: cấu hình giờ sai → báo lỗi
        boolean badDuration = sessionMinutes <= 0;
        boolean badHours = !close.isAfter(open);
        if (badDuration || badHours) {
            throw new IllegalStateException("Cấu hình lịch dạy không hợp lệ");
        }

        // B2: thông tin tuần + trục giờ mặc định
        PtScheduleWeek week = new PtScheduleWeek();
        week.setWeekStart(monday);
        week.setWeekEnd(monday.plusDays(6));
        week.setPrevWeek(monday.minusWeeks(1));
        week.setNextWeek(monday.plusWeeks(1));
        week.setCurrentWeek(monday.equals(weekStartOf(now.toLocalDate())));
        week.setSessionMinutes(sessionMinutes);
        week.setAxisStartHour(open.getHour());
        week.setAxisEndHour(endHour(close));

        // B3: dựng từng ngày: giờ mở cửa, nghỉ phép, khoảng được thêm khung
        List<PtScheduleDay> days = new ArrayList<>();
        // duyệt qua từng ngày Thứ hai đến chủ nhật, 
        for (int i = 0; i < 7; i++) {
            PtScheduleDay day = buildDay(
                    monday.plusDays(i),
                    now.toLocalDate(),
                    open,
                    close,
                    exceptions);
            // ngày nghỉ: Chờ duyệt/Đã duyệt kèm thông tin ca sáng/ca chiều/cả ngày
            day.setLeaveLabel(buildLeaveLabel(day.getDate(), leaves));
            // Chi tiết nội dung nghỉ phép
            day.setLeaveDetails(buildLeaveDetails(day.getDate(), leaves));
            
            // setAddRange tính khoảng giờ mà PT được phép đặt giờ bắt đầu cho khung mới trong một ngày. Nó trả lời câu hỏi: “Ngày này, khung mới sớm nhất bắt đầu lúc mấy giờ, muộn nhất lúc mấy giờ?”
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
     * HÀM PHỤ (buildEmptyWeek, validateDrafts) — 1 ngày: tên thứ, hôm nay / đã qua, giờ mở cửa theo P-17;
     * ngày có OperatingHourExceptions → đổi giờ hoặc "Phòng nghỉ" cả ngày. Giờ ngoại lệ sai → lỗi.
     */
    static PtScheduleDay buildDay(
            LocalDate date,
            LocalDate today,
            LocalTime open,
            LocalTime close,
            List<HourException> exceptions) {
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

    /** HÀM PHỤ (buildDay) — "Thứ 2" … "Chủ nhật". */
    private static String dayName(LocalDate date) {
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        if (dayOfWeek == DayOfWeek.SUNDAY) {
            return "Chủ nhật";
        }
        return "Thứ " + (dayOfWeek.getValue() + 1);
    }

    /**
     * HÀM PHỤ (buildEmptyWeek) — "Nghỉ phép ca sáng · chờ duyệt; …" cho các đơn Pending/Approved của ngày. "" = không nghỉ.
     */
    
    // 
    static String buildLeaveLabel(LocalDate date, List<LeaveInfo> leaves) {
        List<String> labels = new ArrayList<>();

        for (LeaveInfo leave : leaves) {
            
            // Lọc: bỏ qua đơn không phải ngày này, và đơn không còn hiệu lực. isActiveLeave chỉ nhận Pending và Approved. SQL đã loại Rejected rồi, đây là lớp kiểm tra thứ hai.
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
     * HÀM PHỤ (buildEmptyWeek) — nhãn + "Lý do: …" của từng đơn, hiện khi mở chi tiết nghỉ phép.
     */
    // Lọc giống hệt buildLeaveLabel: đúng ngày, và đơn đang Pending hoặc Approved. Chỉ khác là viết gộp vào một if thay vì dùng continue.
    // Lý do đơn nghỉ: reason lấy từ database ghép với buildLeaveLabel
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

    /** HÀM PHỤ (buildLeaveLabel, buildLeaveDetails, isOnLeave) — đơn nghỉ còn hiệu lực = Pending hoặc Approved (đơn chờ duyệt cũng khóa khung). */
    private static boolean isActiveLeave(LeaveInfo leave) {
        return "Pending".equals(leave.getStatus()) || "Approved".equals(leave.getStatus());
    }

    /**
     * HÀM PHỤ (buildEmptyWeek) — phút sớm nhất / muộn nhất được bắt đầu khung mới trong ngày (mốc 15 phút):
     * từ giờ mở cửa (hôm nay: từ sau giờ hiện tại) tới giờ đóng cửa − thời lượng buổi. Ngày đã qua / phòng nghỉ → không thêm được.
     */
    static void setAddRange(PtScheduleDay day, LocalDateTime now, int sessionMinutes) {
        // B1: ngày đã qua / phòng nghỉ cả ngày (OperatingHourExceptions.IsClosedAllDay) → không thêm được
        if (day.isPast() || day.isClosedAllDay()) {
            return;
        }
        // B2: sớm nhất = giờ mở cửa của ngày (P-17 hoặc giờ ngoại lệ, đã tính trong buildDay), làm tròn LÊN mốc 15'
        int first = roundUpTo15(minuteOfDay(day.getOpenTime()));
        if (day.isToday()) {
            int afterNow = roundUpTo15(minuteOfDay(now.toLocalTime()) + 1);
            first = Math.max(first, afterNow);
        }
        // B4: muộn nhất = giờ đóng cửa − thời lượng buổi (P-06), làm tròn XUỐNG mốc 15'
        int last = roundDownTo15(minuteOfDay(day.getCloseTime()) - sessionMinutes);
        // B5: sớm nhất > muộn nhất → không còn chỗ (ngày mở quá ngắn / hôm nay đã quá muộn) → không thêm được
        if (first > last) {
            return;
        }
        day.setFirstStartMinute(first);
        day.setLastStartMinute(last);
    }

    
    // overloading: 2 hàm cùng tên nma danh sách tham số truyền vào khác nhau: Bản đơn giản và bản lọc (bên dưới)
    /**
     * HÀM PHỤ (getWeek B5) — gộp buổi + khung trống thành 1 danh sách, xếp theo ngày rồi giờ.
     */
    static List<PtScheduleItem> merge(List<PtScheduleItem> sessions, List<PtScheduleItem> openSlots) {
        List<PtScheduleItem> items = new ArrayList<>(sessions); // chép danh sách buổi 
        items.addAll(openSlots); // nối thêm khung trống
        items.sort(Comparator.comparing(PtScheduleItem::getSlotDate).thenComparing(PtScheduleItem::getStartTime)); // sắp xếp theo ngày bắt đầu rồi the giờ bắt đầu
        return items;
    }

    /**
     * HÀM PHỤ (getWeek B5) — như merge(sessions, openSlots) nhưng mỗi khung giờ chỉ còn MỘT ô.
     TH C: opensessions: Buổi đã hủy + buổi sống có khách, timelots trống => lấy buổi sống
     TH D: openSessions: Buổi đã hủy + timeslots trống => nếu đã qua giờ thì lấy lấy ô hủy, nếu chưa qua giờ thì lấy ô trống để bán tiếp
     */
    static List<PtScheduleItem> merge(List<PtScheduleItem> sessions, List<PtScheduleItem> openSlots, LocalDateTime now) {
        Set<Integer> liveSlots = new HashSet<>(); // sổ A: khung nào có buổi SỐNG
        Set<Integer> cancelledSlots = new HashSet<>(); // sổ B: Khung nào có buổi đã hủy
        //Duyệt qua từng buổi trong list sessions, xếp vào 1 trong 2 cuốn sổ
        for (PtScheduleItem session : sessions) {
            if ("Cancelled".equals(session.getSessionStatus())) {
                cancelledSlots.add(session.getTimeSlotId());
            } else {
                liveSlots.add(session.getTimeSlotId());
            }
        }

        
        // Câu 1: Ô này đã qua giờ chưa?
        // Câu 2: Khung này có buổi bị hủy không?
        Set<Integer> futureOpen = new HashSet<>();
        List<PtScheduleItem> keptSlots = new ArrayList<>(); // 
        for (PtScheduleItem slot : openSlots) {
            
            // CÂU 1: đã qua giờ chưa?
            boolean started = !LocalDateTime.of(slot.getSlotDate(), slot.getStartTime()).isAfter(now);
            // Nếu có buổi hủy và đã qua giờ
            if (cancelledSlots.contains(slot.getTimeSlotId()) && started) {
                continue; // bỏ qua
            }
            // chưa tới giờ => ghi vào sổ C: futureOpen
            if (!started) {
                futureOpen.add(slot.getTimeSlotId());
            }
            keptSlots.add(slot); // giữ
        }
        // => Sổ C: Khung 50 đang mở trống ở tương lai. Lát nữa nếu gặp buổi hủy ở khung 50 thì bỏ buổi hủy đó, để ô ‘Còn trống’ hiện ra.
        

        List<PtScheduleItem> keptSessions = new ArrayList<>(); // // giỏ đựng buổi được giữ


        for (PtScheduleItem session : sessions) {
            // CÂU 1: bị hủy?
            boolean cancelled = "Cancelled".equals(session.getSessionStatus());
            int slotId = session.getTimeSlotId();
            // Nếu có bị hủy và khung có buổi sống? (sổ A) hoặc có ô trống tương lai (Số C)
            if (cancelled && (liveSlots.contains(slotId) || futureOpen.contains(slotId))) {
                continue; // bỏ qua
            }
            // Giữ
            keptSessions.add(session);
        }
        return merge(keptSessions, keptSlots);
    }

    /**
     * HÀM PHỤ (getWeek B5) — nới trục giờ theo ô sớm/muộn nhất; mỗi ô: classify → tính vị trí → bỏ vào đúng ngày;
     * Hàm có 3 vòng lặp, mỗi vòng một việc, và phải chạy đúng thứ tự.
     */
    static void fillWeek(
            PtScheduleWeek week,
            List<PtScheduleItem> items,
            List<LeaveInfo> leaves,
            LocalDateTime now) {
        
        // Vòng lặp 1: Nối trực giờ theo ô sớm nhất/muộn nhất
        // Phải chạy trước vòng 2, vì vòng 2 tính vị trí ô so với đầu trục.
        for (PtScheduleItem item : items) {
            week.setAxisStartHour(Math.min(week.getAxisStartHour(), item.getStartTime().getHour()));
            week.setAxisEndHour(Math.max(week.getAxisEndHour(), endHour(item.getEndTime())));
        }

        
        // Vòng lặp 2: Xử lý từng buổi rồi cho vào day.getItems 
        // 5h → 300
        int axisStartMinute = week.getAxisStartHour() * 60; 
        for (PtScheduleItem item : items) {
            classify(item, leaves, now); // (a) kind, statusLabel, past, canRemove, canHandle, lengthMinutes

            item.setStartMinute(minuteOfDay(item.getStartTime()) - axisStartMinute); // (b) 07:00 = 420 − 300 = 120 → --start

            // Ô chồng giờ → chỉ cụm đó chia làn, ô khác vẫn rộng đủ cột
            for (PtScheduleDay day : week.getDays()) {
                if (day.getDate().equals(item.getSlotDate())) { // (c) tìm đúng cột

                    day.getItems().add(item); //     bỏ ô vào

                    // (d) nhắc PT dời  buổi đã Scheduled và trùng ca nghỉ
                    if ("Scheduled".equals(item.getSessionStatus()) && isOnLeave(item, leaves)) {
                        day.getLeaveBookings().add(item.getStartTime() + " – " + item.getEndTime()
                                + " · " + item.getCustomerNames() + " · " + item.getStatusLabel());
                    }
                    break;
                }
            }
        }

        // Ô chồng giờ (VD buổi đã hủy 19:30 nằm cùng giờ khung mới 19:15) → chia làn RIÊNG cho cụm đó,
        // Vòng lặp 3: chia làn
        // Phải chạy sau vòng 2, vì cần đủ hết ô của một ngày mới biết ô nào chồng ô nào.
        for (PtScheduleDay day : week.getDays()) {
            assignLanes(day.getItems());
        }
    }

     /**
     * HÀM PHỤ (fillWeek) — quyết định 1 ô hiện thế nào: kind (màu) · statusLabel (chữ) · past · canRemove · canHandle.
     * 4 nhánh, gặp nhánh đúng thì return — THỨ TỰ CÓ Ý NGHĨA: B1 trước B3 → buổi trùng ca nghỉ vẫn hiện là buổi (CBR-83).
     *   B1 có SessionId            → booked (+ sessionLabel)          · canHandle = Scheduled && chưa bắt đầu (BR-70)
     *   B2 TimeSlots.Status 'Held' → held   "Đang giữ · …"
     *   B3 trùng ca nghỉ phép       → locked "Đã duyệt nghỉ" / "Chờ duyệt nghỉ" (LeaveRequests Pending / Approved)
     *   B4 còn lại                  → free   "Còn trống"                 · canRemove = chưa bắt đầu (BR-95)
     */
    static void classify(PtScheduleItem item, List<LeaveInfo> leaves, LocalDateTime now) {
        // B0: phần chung cho mọi ô
        LocalDateTime start = LocalDateTime.of(item.getSlotDate(), item.getStartTime());   // ngày + giờ bắt đầu
        LocalDateTime end = LocalDateTime.of(item.getSlotDate(), item.getEndTime());       // ngày + giờ kết thúc
        item.setPast(!end.isAfter(now));          // đã KẾT THÚC → ô mờ
        item.setCanRemove(false);                 // mặc định không gỡ / không xử lý; nhánh B1, B4 bật lại nếu được
        item.setCanHandle(false);

        item.setStartMinute(minuteOfDay(item.getStartTime()) - 5 * 60);   // tạm (trục 5h); fillWeek ghi đè theo trục thật

        item.setLengthMinutes((int) Duration.between(item.getStartTime(), item.getEndTime()).toMinutes());   // VD 90 → chiều cao ô

        boolean notStarted = start.isAfter(now);  // CHƯA tới giờ bắt đầu → điều kiện gỡ khung / xử lý buổi

        // B1: có buổi → ô "có khách"; buổi Scheduled chưa bắt đầu thì bấm được để Dời / Chuyển / Hủy
        if (item.getSessionId() != null) {
            item.setKind("booked");
            item.setStatusLabel(sessionLabel(item, start, end, now));
            boolean scheduled = "Scheduled".equals(item.getSessionStatus());
            item.setCanHandle(scheduled && notStarted);
            return;
        }

        // B2: khung đang giữ cho đơn chờ thanh toán (DAO gán kind = TimeSlots.Status "Held") → ô "đang giữ"
        if ("Held".equalsIgnoreCase(item.getKind())) {
            item.setKind("held");
            item.setStatusLabel("Đang giữ · đơn chờ thanh toán");
            return;
        }

        // B3: khung trống trùng ca nghỉ (đơn Pending/Approved) → ô "tạm khóa"; có đơn Approved trùng ca → ưu tiên "Đã duyệt"
        if (isOnLeave(item, leaves)) {
            item.setKind("locked");
            boolean approved = leaves.stream().anyMatch(leave -> "Approved".equals(leave.getStatus())
                    && isOnLeave(item, List.of(leave)));       // hỏi lại isOnLeave với TỪNG đơn Approved
            item.setStatusLabel(approved ? "Đã duyệt nghỉ" : "Chờ duyệt nghỉ");
            return;
        }

        // B4: còn lại → ô "còn trống"; chưa tới giờ thì PT gỡ được
        item.setKind("free");
        item.setStatusLabel("Còn trống");
        item.setCanRemove(notStarted);
    }

    /**
     * HÀM PHỤ (classify B1) — Scheduled: "Sắp tới" | "Đang diễn ra" | "Chưa chốt" (đã hết giờ, chờ JOB-04);
     * còn lại: "Đã check-in" | "Vắng" | "Đã hủy".
     */
    private static String sessionLabel(
            PtScheduleItem item,
            LocalDateTime start,
            LocalDateTime end,
            LocalDateTime now) {
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
    
    // assignLanes xử lý trường hợp có 2 ô trùng giờ trong cùng một ngày: hai ô được xếp nằm cạnh nhau, mỗi ô rộng nửa cột, để không đè lên nhau. Những ô không trùng với ô nào vẫn rộng đủ cột.
    static void assignLanes(List<PtScheduleItem> items) {
        // B1: xếp ô theo giờ bắt đầu
        List<PtScheduleItem> sorted = new ArrayList<>(items);
        sorted.sort(Comparator.comparing(PtScheduleItem::getStartTime)
                .thenComparing(item -> item.isBlocking() ? 0 : 1));

        List<PtScheduleItem> cluster = new ArrayList<>();   // các ô của cụm đang xét
        List<LocalTime> laneEnds = new ArrayList<>();       // giờ kết thúc ô cuối của từng làn trong cụm
        LocalTime clusterEnd = null;                        // giờ kết thúc muộn nhất của cụm

        for (PtScheduleItem item : sorted) {
            // B2: ô không chồng cụm cũ → chốt cụm cũ, mở cụm mới
            boolean startsNewCluster = clusterEnd != null && !item.getStartTime().isBefore(clusterEnd);
            if (startsNewCluster) {
                closeCluster(cluster, laneEnds.size());
                cluster.clear();
                laneEnds.clear();
                clusterEnd = null;
            }

            // B3: đặt ô vào làn trống đầu tiên
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

        // B4: chốt cụm cuối cùng
        closeCluster(cluster, laneEnds.size());
    }

    /** HÀM PHỤ (assignLanes) — ghi số làn của cụm vào mọi ô trong cụm (cụm rỗng thì thôi). */
    private static void closeCluster(List<PtScheduleItem> cluster, int laneCount) {
        for (PtScheduleItem item : cluster) {
            item.setLaneCount(Math.max(1, laneCount));
        }
    }



    // ============================================================================================================
    // HÀM CHÍNH 2. publish — nút "Công bố": thêm khung nháp + gỡ khung đã tích, TẤT CẢ trong 1 transaction
    //   Ai gọi: PtScheduleServlet.doPost (fetch từ schedule.js)  ·  Ra: số khung đã đổi + câu thông báo
    //   Hàm phụ ngay dưới: publishInTransaction → parseRemoveIds · buildDrafts · validateDrafts
    // ============================================================================================================

    /**
     * HÀM CHÍNH 2 — nút "Công bố": thêm khung nháp + gỡ khung đã tích, tất cả trong 1 transaction.
     *   Ai gọi: PtScheduleServlet.doPost
     *   Trả về: ok(số khung đã đổi) | câu lỗi (1 khung sai → không lưu gì)
     */
    public ServiceResult<Integer> publish(
            int accountId,
            String[] dates,
            String[] starts,
            String[] removeIds,
            int expectedMinutes,
            LocalDateTime now) {
        // B1: không có gì để lưu → báo lỗi
        boolean hasDrafts = dates != null && dates.length > 0;
        boolean hasRemovals = removeIds != null && removeIds.length > 0;
        boolean pairsBroken = hasDrafts && (starts == null || dates.length != starts.length);
        if ((!hasDrafts && !hasRemovals) || pairsBroken) {
            return ServiceResult.fail(Messages.MSG320_PT_PUBLISH_NOTHING);
        }

        try (Connection connection = DBContext.getConnection()) {
            // B2: mở transaction → ghi hết; lỗi → hủy hết
            connection.setAutoCommit(false);
            try {
                ServiceResult<Integer> result = publishInTransaction(
                        connection,
                        accountId,
                        dates,
                        starts,
                        removeIds,
                        expectedMinutes,
                        now);
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

        // Khung sai (trùng, đã qua giờ…) → trả đúng câu lỗi cho PT
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
    ServiceResult<Integer> publishInTransaction(
            Connection connection,
            int accountId,
            String[] dates,
            String[] starts,
            String[] removeIds,
            int expectedMinutes,
            LocalDateTime now) throws SQLException {
        // B1: khóa dòng PT (2 tab cùng ghi phải chờ nhau)
        Integer ptId = scheduleDAO.lockPtForPublish(connection, accountId);
        if (ptId == null) {
            return ServiceResult.fail(Messages.MSG07_NO_PERMISSION);
        }

        // B2: khóa cấu hình giờ; thời lượng buổi đổi giữa chừng → bảo tải lại
        scheduleDAO.lockScheduleConfigs(connection);

        int minutes = configService.getInt(SystemConfigService.SESSION_DURATION_MINUTES);
        LocalTime[] hours = parseOpeningHours(configService.getString(SystemConfigService.OPENING_HOURS));
        if (minutes <= 0 || minutes >= 24 * 60) {
            throw new IllegalStateException("P-06 không hợp lệ");
        }
        if (minutes != expectedMinutes) {
            return ServiceResult.fail(Messages.MSG321_PT_DURATION_CHANGED);
        }

        // B3: gỡ khung đã tích (1 khung không gỡ được → hủy cả lượt)
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

        // B4: chỉ gỡ → xong
        boolean onlyRemovals = dates == null || dates.length == 0;
        if (onlyRemovals) {
            return ServiceResult.ok(slotIds.size(), String.format(Messages.MSG322_PT_SLOTS_REMOVED, slotIds.size()));
        }

        // B5: đọc khung nháp rồi kiểm từng khung
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

        // B6: ghi từng khung (có khung đã gỡ đúng giờ → mở lại)
        for (PtSlotDraft draft : drafts) {
            scheduleDAO.insertOrReopenSlot(connection, ptId, draft);
        }

        // B7: câu báo kết quả
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
    static void validateDrafts(
            List<PtSlotDraft> drafts,
            List<PtScheduleItem> existing,
            List<LeaveInfo> leaves,
            List<HourException> exceptions,
            LocalTime[] hours,
            LocalDateTime now) {
        PtSlotDraft previous = null;

        for (PtSlotDraft draft : drafts) {
            String label = DateTimeUtil.formatDate(draft.getSlotDate()) + " " + DateTimeUtil.formatTime(draft.getStartTime());

            // B1: đã qua giờ
            boolean started = !draft.getSlotDate().atTime(draft.getStartTime()).isAfter(now);
            if (started) {
                throw new IllegalArgumentException(label + ": giờ bắt đầu đã qua.");
            }

            // B2: phòng nghỉ / ngoài giờ mở cửa
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

            // B4: chồng khung nháp trước đó, khung đầu thì chưa có gì check nên thôi, từ khung t2 check trùng vs khung trước đó
            // buildDrafts đã sxep theo giờ tăng dần nên luôn có: previous.start ≤ draft.start < draft.end, chỉ cần check nửa còn lại
            boolean sameDayAsPrevious = previous != null && previous.getSlotDate().equals(draft.getSlotDate());
            if (sameDayAsPrevious && previous.getEndTime().isAfter(draft.getStartTime())) {
                throw new IllegalArgumentException(label + ": trùng một khung khác trong danh sách nháp.");
            }

            // B5: chồng khung đã có
            for (PtScheduleItem slot : existing) {
                //khác ngày thì bỏ qua
                if (!slot.getSlotDate().equals(draft.getSlotDate())) {
                    continue;
                }

                // khung đã gỡ có cùng giờ bắt đầu  và khác giờ kết thúc => loại
                if ("Withdrawn".equals(slot.getKind())) {
                    boolean sameStart = slot.getStartTime().equals(draft.getStartTime());
                    boolean differentEnd = !slot.getEndTime().equals(draft.getEndTime());
                    if (sameStart && differentEnd) {
                        throw new IllegalArgumentException(label + ": khung cũ có thời lượng khác. Chọn giờ bắt đầu khác.");
                    }
                    continue;
                }
                
                
                // CHồng GIỜ, overlap
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



    // ============================================================================================================
    // DÙNG CHUNG — ca nghỉ, ngày tuần, giờ mở cửa P-17, đổi giờ ↔ phút (một số hàm public cho Servlet / Service khác)
    // ============================================================================================================

    /**
     * HÀM PHỤ (dùng chung: fillWeek, classify, validateDrafts, PtSaleService, PtSessionHandleService) — khung có trùng đơn nghỉ không: FullDay · Morning (bắt đầu trước 13:00) · Afternoon (từ 13:00).
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

    /**
     * HÀM PHỤ (PtScheduleServlet.loadWeek) — ?date= → LocalDate; trống / sai định dạng → hôm nay.
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

    /** HÀM PHỤ (dùng chung: getWeek, buildEmptyWeek, parseDate, PtSaleService) — thứ Hai của tuần chứa date. */
    public static LocalDate weekStartOf(LocalDate date) {
        return date.with(DayOfWeek.MONDAY);
    }

    /**
     * HÀM PHỤ (dùng chung) — P-17 "05:30-22:00" → [05:30, 22:00]; sai dạng / đóng ≤ mở → lỗi cấu hình.
     */
    public static LocalTime[] parseOpeningHours(String value) {
        String[] parts = value.split("-", -1);
        if (parts.length != 2) {
            throw new IllegalStateException("P-17 không đúng dạng HH:mm-HH:mm");
        }

        LocalTime open = LocalTime.parse(parts[0].trim());
        LocalTime close = LocalTime.parse(parts[1].trim());
        if (!close.isAfter(open)) {
            throw new IllegalStateException("P-17 có giờ đóng cửa không sau giờ mở cửa");
        }
        return new LocalTime[]{open, close};
    }

    /** HÀM PHỤ (buildEmptyWeek, fillWeek) — giờ làm tròn lên (21:30 → 22) để trục giờ chứa trọn khung. */
    private static int endHour(LocalTime time) {
        boolean hasMinutes = time.getMinute() > 0 || time.getSecond() > 0;
        return hasMinutes ? time.getHour() + 1 : time.getHour();
    }

    /** HÀM PHỤ (setAddRange, fillWeek, classify) — LocalTime → số phút từ 00:00. */
    private static int minuteOfDay(LocalTime time) {
        return time.toSecondOfDay() / 60;
    }

    /** HÀM PHỤ (setAddRange) — làm tròn LÊN mốc 15 phút. */
    private static int roundUpTo15(int minute) {
        return (minute + 14) / 15 * 15;
    }

    /** HÀM PHỤ (setAddRange) — làm tròn XUỐNG mốc 15 phút. */
    private static int roundDownTo15(int minute) {
        return Math.floorDiv(minute, 15) * 15;
    }
}
