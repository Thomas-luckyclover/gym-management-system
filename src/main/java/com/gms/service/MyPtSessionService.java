package com.gms.service;

import com.gms.constant.AppConstants;
import com.gms.constant.Messages;
import com.gms.dao.AccountDAO;
import com.gms.dao.CustomerDAO;
import com.gms.dao.MyPtSessionDAO;
import com.gms.dto.MonthView;
import com.gms.dto.MyPtSessionRow;
import com.gms.dto.ServiceResult;
import com.gms.dto.TimetableView;
import com.gms.entity.Account;
import com.gms.entity.Customer;
import com.gms.util.DateTimeUtil;
import com.gms.util.ZaloUtil;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * SERVICE MÀN LỊCH TẬP (khách) — /customer/pt-sessions: thời khóa biểu tuần / lịch tháng, CHỈ XEM.
 *
 * AI GỌI SERVICE NÀY?  MyPtSessionServlet (getWeek, getMonth, parseViewDate) · MyBookingService (fillDisplay, findCustomerId, typeLabel, zaloUrl).
 * SERVICE GỌI AI?      MyPtSessionDAO (buổi, gói, quyền vào gym, ngày phòng nghỉ) · AccountDAO / CustomerDAO (tài khoản → khách)
 *                      · ZaloUtil (link Zalo). Link Zalo và "Thêm vào Google Calendar" chỉ là URL ghép sẵn, không gọi API ngoài.
 *
 * BỐ CỤC FILE — mỗi HÀM CHÍNH đứng trước, các hàm phụ của nó nằm ngay bên dưới:
 *   HÀM CHÍNH 1  getWeek       thời khóa biểu tuần: 7 ngày × 3 hàng Sáng / Chiều / Tối   → bandOf
 *   HÀM CHÍNH 2  getMonth      lưới tháng (đủ tuần T2 → CN) + số đếm theo trạng thái   → countInto
 *   HÀM CHÍNH 3  fillDisplay   nhãn, trạng thái, lý do, cảnh báo cho mọi buổi (dùng chung với Gói của tôi)
 *                              → buildGymWarning, canUseGym, typeLabel, zaloUrl, applyStatus, set, missedExplanation,
 *                                cancelExplanation, isNotEnded, calendarUrl, encode
 *   DÙNG CHUNG                 findCustomerId, parseViewDate, weekStartOf
 *
 * THAM SỐ / LUẬT QUAN TRỌNG
 *   - 3 hàng thời khóa biểu xếp theo giờ BẮT ĐẦU (bandOf): trước 13:00 → "Sáng" · 13:00–17:59 → "Chiều" · từ 18:00 → "Tối".
 *   - Độ dài buổi KHÔNG đọc P-06 ở đây: giờ kết thúc lấy thẳng từ TimeSlots.EndTime.
 *   - Luật vào gym giống JOB-04 (CBR-117): Membership phủ ngày tập và không bảo lưu, HOẶC DayPass phủ ngày tập.
 *   - Chỉ ĐỌC trạng thái đã lưu: buổi đã hết giờ mà chưa chốt hiện "Chưa chốt", không chốt thay JOB-04.
 *
 * VẤN ĐÁP (mỗi câu kèm luồng file để mở theo khi trả lời):
 *   1) Màn Lịch tập hiển thị gì? → thời khóa biểu TUẦN (3 hàng) hoặc lịch THÁNG; bấm buổi → hộp chi tiết (trạng thái, PT, Zalo, Google Calendar).
 *      File: MyPtSessionServlet → MyPtSessionService → MyPtSessionDAO → WEB-INF/jsp/customer/my-pt-sessions.jsp.
 *   2) Khách tự hủy / đổi buổi được không? → KHÔNG (chốt 19/10): chỉ XEM; muốn đổi / hủy thì nhắn PT, PT xử lý ở Lịch dạy.
 *      File: MyPtSessionService → PtSessionHandleService (PT đổi ở Lịch dạy).
 *   3) 3 hàng Sáng / Chiều / Tối chia thế nào? → bandOf theo giờ BẮT ĐẦU.  File: MyPtSessionService (bandOf) → my-pt-sessions.jsp.
 *   4) "Vào được phòng tập" tính thế nào? → giống JOB-04; thiếu thì tô nhãn "Chưa có gói tập".
 *      File: MyPtSessionService (buildGymWarning, canUseGym) → AutoCloseSessionService (cùng luật JOB-04).
 */
public class MyPtSessionService {
    // Mã trạng thái hiển thị → JSP chọn màu theo mã
    static final String KEY_TODAY = "today";            // hôm nay, chưa kết thúc
    static final String KEY_UPCOMING = "upcoming";      // các ngày sau
    static final String KEY_WARN = "warn";              // sắp tới nhưng có cảnh báo
    static final String KEY_PENDING = "pending";        // đã kết thúc, chờ JOB-04 chốt
    static final String KEY_CHECKED_IN = "checkedIn";
    static final String KEY_MISSED = "missed";
    static final String KEY_CANCELLED = "cancelled";

    /** Cảnh báo "phòng nghỉ" — màn Gói của tôi so câu này để phân biệt với cảnh báo thiếu quyền vào gym. */
    static final String CLOSED_DAY_WARNING =
            "Phòng tập nghỉ ngày này. PT sẽ liên hệ để dời buổi sang ngày khác, buổi không bị trừ.";

    private static final String[] DAY_NAMES = {"Thứ 2", "Thứ 3", "Thứ 4", "Thứ 5", "Thứ 6", "Thứ 7", "Chủ nhật"};

    /** 3 khung giờ = 3 hàng của thời khóa biểu (xếp theo giờ BẮT ĐẦU, xem bandOf). */
    private static final String[][] BANDS = {
        {"Sáng", "05:30 – 13:00"},
        {"Chiều", "13:00 – 18:00"},
        {"Tối", "18:00 – 22:00"}
    };

    private final AccountDAO accountDAO = new AccountDAO();
    private final CustomerDAO customerDAO = new CustomerDAO();
    private final MyPtSessionDAO sessionDAO = new MyPtSessionDAO();

    /** Định dạng giờ Google Calendar cần: yyyyMMdd'T'HHmmss'Z' theo UTC. */
    private static final DateTimeFormatter CALENDAR_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);



    // ============================================================================================================
    // HÀM CHÍNH 1. getWeek — thời khóa biểu tuần chứa ngày đang xem (view=week)
    //   Ai gọi: MyPtSessionServlet.loadTimetable  ·  Ra: TimetableView → JSP biến "timetable"
    //   Hàm phụ ngay dưới: bandOf
    // ============================================================================================================

    /**
     * HÀM CHÍNH 1 — thời khóa biểu tuần chứa ngày đang xem: 7 ngày × 3 hàng Sáng / Chiều / Tối.
     *   Ai gọi: MyPtSessionServlet.loadTimetable (view=week)
     *   Trả về: ok(TimetableView) | lỗi (không phải khách / lỗi hệ thống)
     */
    public ServiceResult<TimetableView> getWeek(int accountId, LocalDate anyDay, LocalDateTime now) {
        try {
            // B1: tài khoản phải là khách
            Integer customerId = findCustomerId(accountId);
            if (customerId == null) {
                return ServiceResult.fail(Messages.MSG07_NO_PERMISSION);
            }

            // B2: lấy các buổi trong tuần (thứ Hai → Chủ nhật)
            LocalDate monday = weekStartOf(anyDay);
            LocalDate sunday = monday.plusDays(6);

            List<MyPtSessionRow> rows = sessionDAO.findSessionsInRange(customerId, monday, sunday);

            // Điền trạng thái, nhãn, link cho từng buổi
            fillDisplay(rows, customerId, now);

            // B3: dựng khung rỗng 3 hàng × 7 cột
            LocalDate today = now.toLocalDate();            // 20/10 — để đánh dấu cột "Hôm nay"
            TimetableView view = new TimetableView(monday, today);   // vỏ rỗng, nhớ tuần 19/10–25/10

            for (int i = 0; i < 7; i++) {
                LocalDate date = monday.plusDays(i);
                String dayName = DAY_NAMES[i];              // "Thứ 2" … "Chủ nhật"
                boolean isToday = date.equals(today);
                view.getDays().add(new TimetableView.Day(date, dayName, isToday));
            }

            for (String[] band : BANDS) {
                String bandName = band[0];                  // "Sáng"
                String bandRange = band[1];                 // "05:30 – 13:00"
                view.getBands().add(new TimetableView.Band(bandName, bandRange));
            }

            // B4: thả từng buổi vào đúng ô
            for (MyPtSessionRow row : rows) {
                // Cột = thứ trong tuần (T2 = 0)
                int dayIndex = row.getSlotDate().getDayOfWeek().getValue() - 1;

                // Hàng = Sáng / Chiều / Tối theo giờ bắt đầu
                int bandIndex = bandOf(row.getStartTime());

                TimetableView.Band band = view.getBands().get(bandIndex);     // hàng Tối
                List<MyPtSessionRow> cell = band.getCells().get(dayIndex);     // ô Tối – Thứ 4
                cell.add(row);                                                 // bỏ buổi vào ô
            }

            return ServiceResult.ok(view);

        } catch (SQLException e) {
            // Lỗi DB → báo lỗi, không hiện lưới rỗng giả
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /** HÀM PHỤ (getWeek B4) — xếp theo giờ bắt đầu: trước 13:00 (mốc ca chung) là Sáng, trước 18h là Chiều, còn lại là Tối. */
    static int bandOf(LocalTime startTime) {
        if (startTime.isBefore(AppConstants.AFTERNOON_SHIFT_START)) {
            return 0;
        }
        return startTime.getHour() < 18 ? 1 : 2;
    }



    // ============================================================================================================
    // HÀM CHÍNH 2. getMonth — lưới tháng chứa ngày đang xem + số đếm theo trạng thái (view=month)
    //   Ai gọi: MyPtSessionServlet.loadTimetable  ·  Ra: MonthView → JSP biến "month"
    //   Hàm phụ ngay dưới: countInto
    // ============================================================================================================

    /**
     * HÀM CHÍNH 2 — lưới tháng chứa ngày đang xem (đủ tuần T2 → CN) + số buổi theo trạng thái.
     *   Ai gọi: MyPtSessionServlet.loadTimetable (view=month)
     *   Trả về: ok(MonthView) | lỗi
     */
    public ServiceResult<MonthView> getMonth(int accountId, LocalDate anyDay, LocalDateTime now) {
        try {
            // B1: tài khoản phải là khách
            Integer customerId = findCustomerId(accountId);
            if (customerId == null) {
                return ServiceResult.fail(Messages.MSG07_NO_PERMISSION);
            }

            // B2: khung lưới đủ tuần (T2 đầu tháng → CN cuối tháng)
            YearMonth month = YearMonth.from(anyDay);        // 22/10/2026 → tháng 10/2026
            LocalDate firstDay = month.atDay(1);             // 01/10 (Thứ 5)
            LocalDate lastDay = month.atEndOfMonth();        // 31/10 (Thứ 7) — tự biết tháng 28/29/30/31 ngày

            LocalDate gridStart = weekStartOf(firstDay);             // T2 của tuần chứa 1/10 → 28/09
            LocalDate gridEnd = weekStartOf(lastDay).plusDays(6);    // T2 tuần chứa 31/10 = 26/10, +6 → 01/11
            // → Lưới 28/09 → 01/11 = 5 hàng × 7 = 35 ô.
            //   28/9–30/9 và 1/11 là "ô đệm" (không thuộc tháng 10).

            // B3: lấy buổi trong tháng (không lấy ô đệm)
            List<MyPtSessionRow> rows = sessionDAO.findSessionsInRange(customerId, firstDay, lastDay);
            fillDisplay(rows, customerId, now);              // điền nhãn + trạng thái như getWeek

            // B4: dựng lưới rỗng + bảng tra ngày → ô
            LocalDate today = now.toLocalDate();             // 20/10
            MonthView view = new MonthView(month);    // vỏ rỗng, các ô đếm = 0

            Map<LocalDate, MonthView.Cell> cellByDate = new HashMap<>();

            for (LocalDate weekStart = gridStart; !weekStart.isAfter(gridEnd); weekStart = weekStart.plusWeeks(1)) {
                List<MonthView.Cell> week = new ArrayList<>();          // 1 hàng của lưới

                for (int i = 0; i < 7; i++) {
                    LocalDate date = weekStart.plusDays(i);
                    boolean inMonth = YearMonth.from(date).equals(month);   // 28/9 → false (ô đệm), 5/10 → true
                    boolean isToday = date.equals(today);                   // 20/10 → true

                    MonthView.Cell cell = new MonthView.Cell(date, inMonth, isToday);   // ô rỗng, chưa có buổi
                    week.add(cell);              // thêm vào hàng
                    cellByDate.put(date, cell);  // ghi vào danh bạ (CÙNG 1 object, không phải bản sao)
                }
                view.getWeeks().add(week);       // thêm hàng vào lưới
            }

            // B5: thả buổi vào ô + đếm theo trạng thái
            for (MyPtSessionRow row : rows) {
                MonthView.Cell cell = cellByDate.get(row.getSlotDate());
                cell.getSessions().add(row);

                countInto(view, row.getStatusKey());
            }

            return ServiceResult.ok(view);     // Servlet đặt attribute "month" = view

        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);   // lỗi DB ≠ "tháng không có buổi"
        }
    }

    /** HÀM PHỤ (getMonth B5) — cộng 1 vào ô đếm của tháng. Hôm nay / Sắp tới / Cần chú ý đều tính là "Sắp tới". */
    private void countInto(MonthView view, String statusKey) {
        switch (statusKey) {
            case KEY_TODAY, KEY_UPCOMING, KEY_WARN -> view.setUpcomingCount(view.getUpcomingCount() + 1);
            case KEY_CHECKED_IN -> view.setCheckedInCount(view.getCheckedInCount() + 1);
            case KEY_MISSED -> view.setMissedCount(view.getMissedCount() + 1);
            case KEY_CANCELLED -> view.setCancelledCount(view.getCancelledCount() + 1);
            default -> view.setPendingCount(view.getPendingCount() + 1);
        }
    }



    // ============================================================================================================
    // HÀM CHÍNH 3. fillDisplay — điền chữ hiển thị cho mọi buổi (dùng chung: getWeek, getMonth, MyBookingService.getMyBookings)
    //   Hàm phụ ngay dưới, theo thứ tự fillDisplay gọi:
    //     buildGymWarning → canUseGym · typeLabel · zaloUrl · applyStatus → set · missedExplanation · cancelExplanation · isNotEnded
    //     calendarUrl → encode
    // ============================================================================================================

    /**
     * HÀM CHÍNH 3 — điền chữ hiển thị cho mọi buổi: loại gói, người tập cùng, Zalo, trạng thái, cảnh báo, Google Calendar.
     *   Ai gọi: getWeek, getMonth · MyBookingService.getMyBookings
     */
    void fillDisplay(List<MyPtSessionRow> rows, int myCustomerId, LocalDateTime now) throws SQLException {
        if (rows.isEmpty()) {
            return;
        }

        // B1: ngày phòng nghỉ trong khoảng đang xem (1 câu SQL)
        LocalDate first = rows.get(0).getSlotDate();
        LocalDate last = rows.get(rows.size() - 1).getSlotDate();
        Set<LocalDate> closedDays = sessionDAO.findClosedDays(first, last);

        for (MyPtSessionRow row : rows) {
            // B2: nhãn loại gói + link Zalo PT
            row.setTypeLabel(typeLabel(row.getType()));
            row.setZaloUrl(zaloUrl(row.getPtPhone()));

            // B3: gói đôi → tên người tập cùng
            if (row.isCouple() && row.getPartnerCustomerId() != null) {
                boolean iAmHolder = row.getHolderCustomerId() == myCustomerId;
                row.setPartnerName(iAmHolder ? row.getPartnerFullName() : row.getHolderName());
            }

            // B4: cảnh báo cho buổi chưa kết thúc (phòng nghỉ, chưa có gói gym)
            String warning = null;
            if (isNotEnded(row, now)) {
                warning = closedDays.contains(row.getSlotDate())
                        ? CLOSED_DAY_WARNING
                        : buildGymWarning(row, myCustomerId);
            }

            // B5: trạng thái + lý do
            applyStatus(row, now, warning);

            // B6: buổi sắp tới → link Google Calendar
            if ("Scheduled".equals(row.getStatus()) && isNotEnded(row, now)) {
                row.setCalendarUrl(calendarUrl(row));
            }
        }
    }

    /**
     * HÀM PHỤ (fillDisplay B4) — cảnh báo quyền vào gym cho 1 buổi chưa diễn ra (giống JOB-04). Null = không cảnh báo.
     * "Vào được gym" (CBR-117) = (có Membership phủ ngày đó VÀ không bảo lưu) HOẶC có DayPass phủ ngày đó.
     * Bảo lưu chỉ chặn gói thành viên: khách bảo lưu mua vé ngày vẫn vào tập được (quyết định 06/11/2026).
     * Gói đôi: chỉ cần 1 trong 2 người vào được; câu chữ nói theo góc nhìn người đang xem.
     */
    private String buildGymWarning(MyPtSessionRow row, int myCustomerId) throws SQLException {
        LocalDate day = row.getSlotDate();

        // B1: xét người đứng tên
        int holderId = row.getHolderCustomerId();
        boolean holderFrozen = sessionDAO.isFrozen(holderId, day);
        boolean holderOk = canUseGym(holderId, day, holderFrozen);

        // B2: gói cá nhân / buổi lẻ → chỉ người đứng tên
        if (!row.isCouple() || row.getPartnerCustomerId() == null) {
            if (holderOk) {
                return null;
            }
            row.setNoGymPass(!holderFrozen);   // thiếu gói (không phải bảo lưu) → màn Gói của tôi đưa vào "Cần để ý"
            return holderFrozen
                    ? "Buổi tập nằm trong kỳ bảo lưu. Hãy kiểm tra thời gian bảo lưu trước ngày tập."
                    : "Chưa có Membership hoặc DayPass cho ngày này. Mua trước ngày tập để không bị tính vắng.";
        }

        // B3: gói đôi → xét thêm người tập cùng
        int partnerId = row.getPartnerCustomerId();
        boolean partnerFrozen = sessionDAO.isFrozen(partnerId, day);
        boolean partnerOk = canUseGym(partnerId, day, partnerFrozen);

        // B4: đổi sang góc nhìn người đang xem
        boolean iAmHolder = holderId == myCustomerId;
        boolean meOk = iAmHolder ? holderOk : partnerOk;
        boolean otherOk = iAmHolder ? partnerOk : holderOk;
        boolean meFrozen = iAmHolder ? holderFrozen : partnerFrozen;

        // B5: chọn câu cảnh báo
        if (meOk && otherOk) {
            return null;
        }
        if (meOk) {
            return "Người tập cùng chưa có quyền vào gym hợp lệ cho ngày này. Bạn vẫn có thể tham gia buổi tập.";
        }
        if (otherOk) {
            row.setNoGymPass(!meFrozen);
            return meFrozen
                    ? "Bạn đang trong kỳ bảo lưu vào ngày tập. Người tập cùng vẫn có thể tham gia buổi tập."
                    : "Bạn chưa có quyền vào gym cho ngày này. Người tập cùng vẫn có thể tham gia buổi tập.";
        }
        row.setNoGymPass(!holderFrozen && !partnerFrozen);
        if (holderFrozen || partnerFrozen) {
            return "Buổi tập trùng kỳ bảo lưu và cả hai chưa có quyền vào gym hợp lệ. Hãy kiểm tra Membership hoặc DayPass trước ngày tập.";
        }
        return "Cả hai chưa có Membership hoặc DayPass cho ngày này. Mua trước ngày tập để không bị tính vắng.";
    }

    /**
     * HÀM PHỤ (buildGymWarning) — 1 khách vào gym được ngày đó không (CBR-117).
     * frozen = gói thành viên bảo lưu ngày đó (đã đọc sẵn để chọn câu cảnh báo).
     *   không bảo lưu → có Membership HOẶC DayPass là được (hasGymPass)
     *   đang bảo lưu → chỉ vé ngày mới cho vào (hasDayPass)
     */
    private boolean canUseGym(int customerId, LocalDate day, boolean frozen) throws SQLException {
        return frozen ? sessionDAO.hasDayPass(customerId, day) : sessionDAO.hasGymPass(customerId, day);
    }

    /** HÀM PHỤ (fillDisplay B2; MyBookingService.fillPackage) — mã loại gói → chữ tiếng Việt. */
    static String typeLabel(String type) {
        return switch (type) {
            case "PTSingle" -> "PT cá nhân";
            case "PTCouple" -> "PT đôi";
            default -> "Buổi lẻ";
        };
    }

    /**
     * HÀM PHỤ (fillDisplay B2; MyBookingService.fillPackage) — SĐT → link Zalo. Logic nằm ở ZaloUtil (PT Dashboard cũng dùng); giữ tên cũ cho MyBookingService và test.
     */
    static String zaloUrl(String phone) {
        return ZaloUtil.toUrl(phone);
    }

    /**
     * HÀM PHỤ (fillDisplay B5) — gán mã + nhãn + lý do theo Sessions.Status (chỉ ĐỌC kết quả đã lưu, không chốt thay JOB-04).
     *   Scheduled chưa kết thúc: có cảnh báo → warn | hôm nay → today | ngày sau → upcoming
     *   Scheduled đã kết thúc → pending | CheckedIn / Missed / Cancelled → giữ nguyên + lý do
     */
    static void applyStatus(MyPtSessionRow row, LocalDateTime now, String warning) {
        row.setEnded(!isNotEnded(row, now));
        boolean couple = row.isCouple();

        switch (row.getStatus()) {
            case "Scheduled" -> {
                if (!isNotEnded(row, now)) {
                    set(
                            row,
                            KEY_PENDING,
                            "Đang cập nhật",
                            "Buổi đã kết thúc. Hệ thống sẽ chốt kết quả sau khi hết ngày tập.");
                } else if (warning != null) {
                    set(
                            row,
                            KEY_WARN,
                            "Cần chú ý",
                            warning);
                } else if (row.getSlotDate().equals(now.toLocalDate())) {
                    boolean started = !now.isBefore(row.getSlotDate().atTime(row.getStartTime()));
                    set(
                            row,
                            KEY_TODAY,
                            started ? "Đang diễn ra" : "Hôm nay",
                            null);
                } else {
                    set(
                            row,
                            KEY_UPCOMING,
                            "Sắp tới",
                            null);
                }
            }
            case "CheckedIn" -> set(
                    row,
                    KEY_CHECKED_IN,
                    "Đã check-in",
                    couple ? "Bạn hoặc người tập cùng đã check-in vào gym hôm đó. Đây là ghi nhận vào cửa, không phải xác nhận đã tập xong với PT." : "Bạn đã check-in vào gym hôm đó. Đây là ghi nhận vào cửa, không phải xác nhận đã tập xong với PT.");
            case "Missed" -> set(
                    row,
                    KEY_MISSED,
                    "Vắng",
                    missedExplanation(row.getMissedReason(), couple));
            default -> set(
                    row,
                    KEY_CANCELLED,
                    "Đã hủy",
                    cancelExplanation(row.getCancelType(), couple));
        }
    }

    /** HÀM PHỤ (applyStatus) — gán cùng lúc mã trạng thái, nhãn và lời giải thích cho một buổi. */
    private static void set(
            MyPtSessionRow row,
            String key,
            String label,
            String explanation) {
        row.setStatusKey(key);
        row.setStatusLabel(label);
        row.setExplanation(explanation);
    }

    /** HÀM PHỤ (applyStatus) — vì sao vắng (Sessions.MissedReason). */
    private static String missedExplanation(String reason, boolean couple) {
        if (reason == null) {
            return "Buổi được ghi nhận là vắng. Buổi vẫn tính vào gói.";
        }
        return switch (reason) {
            case "NoCheckIn" -> couple
                    ? "Cả hai không check-in vào gym trong ngày tập. Buổi vẫn tính vào gói."
                    : "Bạn không check-in vào gym trong ngày tập. Buổi vẫn tính vào gói.";
            case "NoMembership" -> couple
                    ? "Hôm đó cả hai chưa có Membership hoặc DayPass nên buổi bị tính vắng."
                    : "Hôm đó bạn chưa có Membership hoặc DayPass nên buổi bị tính vắng.";
            case "MembershipFrozen" -> couple
                    ? "Ngày tập nằm trong kỳ bảo lưu và cả hai chưa có quyền vào gym hợp lệ."
                    : "Ngày tập nằm trong kỳ bảo lưu Membership của bạn.";
            default -> "Buổi được ghi nhận là vắng. Buổi vẫn tính vào gói.";
        };
    }

    /** HÀM PHỤ (applyStatus) — vì sao bị hủy (Sessions.CancelType). Dữ liệu cũ vẫn có thể là ByCustomer. */
    private static String cancelExplanation(String cancelType, boolean couple) {
        if (cancelType == null) {
            return "Buổi đã bị hủy.";
        }
        return switch (cancelType) {
            case "ByPt" -> "PT đã hủy buổi này.";
            case "ByCustomer" -> couple
                    ? "Hủy theo yêu cầu của một thành viên (dữ liệu trước khi bỏ chức năng khách tự hủy). Buổi vẫn bị trừ."
                    : "Hủy theo yêu cầu của bạn (dữ liệu trước khi bỏ chức năng khách tự hủy). Buổi vẫn bị trừ.";
            case "GymClosed" -> "Phòng tập nghỉ ngày này và PT chưa dời buổi nên hệ thống hủy. Buổi không được dạy bù, hãy liên hệ PT hoặc lễ tân.";
            case "PackageExpired" -> "Gói PT đã hết hạn nên buổi chưa tập bị hủy.";
            default -> "Buổi đã bị hủy.";
        };
    }

    /** HÀM PHỤ (fillDisplay, applyStatus) — buổi chưa kết thúc? (bây giờ < giờ kết thúc) */
    static boolean isNotEnded(MyPtSessionRow row, LocalDateTime now) {
        return now.isBefore(row.getSlotDate().atTime(row.getEndTime()));
    }

    /**
     * HÀM PHỤ (fillDisplay B6) — buổi → link "Thêm vào Google Calendar" (không cần API key, Google mở sẵn sự kiện để khách bấm Lưu).
     * Giờ trong DB là giờ VN → đổi sang UTC (đuôi Z) để lịch của khách hiện đúng giờ.
     * Vd 21/10/2026 18:00–19:30 → dates=20261021T110000Z/20261021T123000Z.
     */
    static String calendarUrl(MyPtSessionRow row) {
        // B1: đổi giờ VN sang giờ UTC
        String start = CALENDAR_TIME.format(row.getSlotDate().atTime(row.getStartTime()).atZone(DateTimeUtil.GYM_ZONE));
        String end = CALENDAR_TIME.format(row.getSlotDate().atTime(row.getEndTime()).atZone(DateTimeUtil.GYM_ZONE));

        // B2: tiêu đề + mô tả
        String title = "Buổi PT với " + row.getPtName() + " · Super Gym";
        String details = row.getPackageName()
                + (row.isSingleSession() ? "" : " · buổi " + row.getSessionNo() + "/" + row.getTotalSessions());

        // B3: ghép link
        return "https://calendar.google.com/calendar/render?action=TEMPLATE"
                + "&text=" + encode(title)
                + "&dates=" + start + "/" + end
                + "&details=" + encode(details)
                + "&location=" + encode("Super Gym");
    }

    /** HÀM PHỤ (calendarUrl) — mã hóa chữ để đặt vào URL (khoảng trắng → %20). */
    private static String encode(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8).replace("+", "%20");
    }



    // ============================================================================================================
    // DÙNG CHUNG — tài khoản → mã khách, đọc ngày trên URL, thứ Hai của tuần (getWeek, getMonth, Servlet, MyBookingService)
    // ============================================================================================================

    /**
     * HÀM PHỤ (dùng chung: getWeek B1, getMonth B1, MyBookingService B1) — tài khoản đang đăng nhập → mã khách.
     * Null = tài khoản khóa / không phải khách. (accountId lấy trong HttpSession, KHÔNG tin URL.)
     */
    Integer findCustomerId(int accountId) throws SQLException {
        // B1: tài khoản phải còn hoạt động
        Account account = accountDAO.findById(accountId);
        if (account == null || account.getStatus() != Account.Status.ACTIVE) {
            return null;
        }

        // B2: phải có hồ sơ khách chưa bị xóa
        Customer customer = customerDAO.findByPersonId(account.getPersonId());
        if (customer == null || customer.getDeletedAt() != null) {
            return null;
        }
        return customer.getCustomerId();
    }

    /** HÀM PHỤ (MyPtSessionServlet.readViewDate) — ngày URL sai hoặc ngoài khoảng an toàn cho SQL và điều hướng tuần / tháng → hôm nay. */
    public static LocalDate parseViewDate(String value, LocalDate today) {
        // B1: đọc ngày yyyy-MM-dd, ngày quá xa → hôm nay
        LocalDate date = DateTimeUtil.parseHtmlDate(value);
        if (date == null || date.getYear() < 1754 || date.getYear() > 9998) {
            return today;
        }
        return date;
    }

    /** HÀM PHỤ (getWeek, getMonth) — ngày bất kỳ → Thứ 2 của tuần đó. Vd 22/10/2026 → 19/10; Chủ nhật 25/10 → 19/10. */
    static LocalDate weekStartOf(LocalDate date) {
        return date.with(DayOfWeek.MONDAY);
    }
}
