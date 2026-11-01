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
 * Màn "Lịch tập" (/customer/pt-sessions) — thời khóa biểu tuần / tháng, CHỈ XEM.
 * Khách không tự hủy / đổi buổi: nhắn PT qua Zalo, PT cập nhật lịch (chốt 19/10).
 * Các hàm fillDisplay, findCustomerId, typeLabel, zaloUrl được màn "Gói của tôi" dùng lại.
 * Link Zalo và "Thêm vào Google Calendar" chỉ là URL ghép sẵn, không gọi API ngoài.
 *
 * THAM SỐ CẤU HÌNH (giá trị hiện tại)
 *   BANDS: 3 hàng thời khóa biểu, xếp theo giờ BẮT ĐẦU (bandOf):
 *     bắt đầu trước 13:00 → "Sáng" · 13:00–17:59 → "Chiều" · từ 18:00 → "Tối" (13:00 = mốc ca chung, AppConstants)
 *     (chữ hiện cạnh tên hàng: "05:30 – 13:00", "13:00 – 18:00", "18:00 – 22:00")
 *   Độ dài 1 buổi KHÔNG đọc P-06 ở đây: giờ kết thúc lấy thẳng từ TimeSlots.EndTime (hiện P-06 = 90 phút).
 *   Luật vào gym giống JOB-04: Membership hoặc DayPass phủ ngày tập, và không bảo lưu ngày đó.
 *
 * ❓ VẤN ĐÁP (mỗi câu kèm luồng file để mở theo khi trả lời):
 *   1) Màn Lịch tập hiển thị gì? → thời khóa biểu TUẦN (3 hàng Sáng/Chiều/Tối) hoặc lịch THÁNG; bấm buổi → hộp chi tiết (trạng thái, PT, link Zalo, Google Calendar).
 *      File: servlet/customer/MyPtSessionServlet.java → service/MyPtSessionService.java → dao/MyPtSessionDAO.java → WEB-INF/jsp/customer/my-pt-sessions.jsp.
 *   2) Khách tự hủy / đổi buổi được không? → KHÔNG (chốt 19/10): chỉ XEM; muốn đổi/hủy thì nhắn PT qua Zalo, PT xử lý ở Lịch dạy.
 *      File: service/MyPtSessionService.java → service/PtScheduleService.java (PT đổi ở Lịch dạy).
 *   3) 3 hàng Sáng/Chiều/Tối chia thế nào? → bandOf theo giờ BẮT ĐẦU: trước 13:00 = Sáng · 13:00–17:59 = Chiều · từ 18:00 = Tối.
 *      File: service/MyPtSessionService.java (bandOf) → WEB-INF/jsp/customer/my-pt-sessions.jsp.
 *   4) "Vào được phòng tập" tính thế nào? → giống JOB-04: có Membership/DayPass phủ ngày tập và không bảo lưu; thiếu thì tô nhãn "Chưa có gói tập".
 *      File: service/MyPtSessionService.java → service/AutoCloseSessionService.java (cùng luật vào gym JOB-04).
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

    // ================================================================================================================
    // 1. LỊCH TUẦN — getWeek gọi bandOf để xếp buổi vào Sáng / Chiều / Tối
    // ================================================================================================================

    /** Thời khóa biểu tuần chứa ngày anyDay: 7 ngày × 3 khung giờ. */
    public ServiceResult<TimetableView> getWeek(int accountId, LocalDate anyDay, LocalDateTime now) {
        try {
            // ===== B1: ĐỔI TÀI KHOẢN → MÃ KHÁCH =====
            // accountId lấy từ session (Servlet truyền vào). Hàm này kiểm tra:
            //   tài khoản còn ACTIVE + có hồ sơ khách chưa bị xóa → trả CustomerId (vd 5)
            //   không thỏa → trả null
            Integer customerId = findCustomerId(accountId);
            if (customerId == null) {
                // Không phải khách hợp lệ → báo lỗi, Servlet đặt "timetableError"
                return ServiceResult.fail(Messages.MSG07_NO_PERMISSION);
            }

            // ===== B2: LẤY CÁC BUỔI TRONG TUẦN TỪ DB =====
            // Tìm Thứ 2 của tuần chứa anyDay: 22/10 (T5) → 19/10 (T2)
            LocalDate monday = weekStartOf(anyDay);
            // Chủ nhật = Thứ 2 + 6 ngày: 19/10 → 25/10
            LocalDate sunday = monday.plusDays(6);

            // 1 câu SQL: mọi buổi của khách có SlotDate từ 19/10 đến 25/10,
            // đã xếp theo ngày + giờ. Mỗi phần tử = 1 buổi (MyPtSessionRow).
            List<MyPtSessionRow> rows = sessionDAO.findSessionsInRange(customerId, monday, sunday);

            // Điền thêm thông tin để hiển thị cho TỪNG buổi (sửa thẳng vào rows):
            //   nhãn loại gói, link Zalo PT, tên người tập cùng, cảnh báo,
            //   trạng thái (Sắp tới / Hôm nay / Vắng…), link Google Calendar
            fillDisplay(rows, customerId, now);

            // ===== B3: DỰNG KHUNG RỖNG 3 HÀNG × 7 CỘT =====
            LocalDate today = now.toLocalDate();            // 20/10 — để đánh dấu cột "Hôm nay"
            TimetableView view = new TimetableView(monday, today);   // vỏ rỗng, nhớ tuần 19/10–25/10

            // 7 cột ngày: i = 0..6 → 19/10 "Thứ 2", 20/10 "Thứ 3" (today = true), …, 25/10 "Chủ nhật"
            for (int i = 0; i < 7; i++) {
                LocalDate date = monday.plusDays(i);
                String dayName = DAY_NAMES[i];              // "Thứ 2" … "Chủ nhật"
                boolean isToday = date.equals(today);
                view.getDays().add(new TimetableView.Day(date, dayName, isToday));
            }

            // 3 hàng khung giờ: Sáng / Chiều / Tối.
            // Mỗi Band khi tạo đã tự có 7 ô rỗng (constructor của Band tạo 7 ArrayList).
            for (String[] band : BANDS) {
                String bandName = band[0];                  // "Sáng"
                String bandRange = band[1];                 // "05:30 – 13:00"
                view.getBands().add(new TimetableView.Band(bandName, bandRange));
            }
            // → Lưới 3 × 7 ô, ô nào cũng rỗng. Ngày không có buổi vẫn có ô (JSP hiện "—").

            // ===== B4: THẢ TỪNG BUỔI VÀO ĐÚNG Ô =====
            for (MyPtSessionRow row : rows) {
                // CỘT: Java đánh số MONDAY = 1 … SUNDAY = 7 → trừ 1 để thành index 0..6
                //      vd buổi 21/10 (Thứ 4): 3 − 1 = 2
                int dayIndex = row.getSlotDate().getDayOfWeek().getValue() - 1;

                // HÀNG: theo GIỜ BẮT ĐẦU: < 13h → 0 Sáng | < 18h → 1 Chiều | còn lại → 2 Tối
                //      vd 18:00 → 2
                int bandIndex = bandOf(row.getStartTime());

                // Lấy hàng, rồi lấy ô trong hàng (ô = List các buổi, chứa được nhiều buổi)
                TimetableView.Band band = view.getBands().get(bandIndex);     // hàng Tối
                List<MyPtSessionRow> cell = band.getCells().get(dayIndex);     // ô Tối – Thứ 4
                cell.add(row);                                                 // bỏ buổi vào ô
            }

            // Thành công → Servlet đặt attribute "timetable" = view
            return ServiceResult.ok(view);

        } catch (SQLException e) {
            // Lỗi DB (mất kết nối, sai SQL…) ≠ "tuần này không có buổi".
            // Trả lỗi để JSP hiện "Chưa tải được lịch tập" + nút Tải lại, KHÔNG hiện lưới rỗng giả.
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /** Xếp theo giờ bắt đầu: trước 13:00 (mốc ca chung) là Sáng, trước 18h là Chiều, còn lại là Tối. */
    static int bandOf(LocalTime startTime) {
        if (startTime.isBefore(AppConstants.AFTERNOON_SHIFT_START)) {
            return 0;
        }
        return startTime.getHour() < 18 ? 1 : 2;
    }

    // ================================================================================================================
    // 2. LỊCH THÁNG — getMonth gọi countInto để cộng các ô thống kê
    // ================================================================================================================

    /** Lưới tháng chứa ngày anyDay (đủ tuần Thứ 2 → CN) + số đếm theo trạng thái. */
    public ServiceResult<MonthView> getMonth(int accountId, LocalDate anyDay, LocalDateTime now) {
        try {
            // ===== B1: ĐỔI TÀI KHOẢN → MÃ KHÁCH (giống getWeek) =====
            Integer customerId = findCustomerId(accountId);
            if (customerId == null) {
                return ServiceResult.fail(Messages.MSG07_NO_PERMISSION);
            }

            // ===== B2: TÍNH KHUNG LƯỚI (đủ tuần T2 → CN) =====
            YearMonth month = YearMonth.from(anyDay);        // 22/10/2026 → tháng 10/2026
            LocalDate firstDay = month.atDay(1);             // 01/10 (Thứ 5)
            LocalDate lastDay = month.atEndOfMonth();        // 31/10 (Thứ 7) — tự biết tháng 28/29/30/31 ngày

            // Lưới phải bắt đầu từ THỨ 2 và kết thúc ở CHỦ NHẬT để mỗi hàng đủ 7 ô:
            LocalDate gridStart = weekStartOf(firstDay);             // T2 của tuần chứa 1/10 → 28/09
            LocalDate gridEnd = weekStartOf(lastDay).plusDays(6);    // T2 tuần chứa 31/10 = 26/10, +6 → 01/11
            // → Lưới 28/09 → 01/11 = 5 hàng × 7 = 35 ô.
            //   28/9–30/9 và 1/11 là "ô đệm" (không thuộc tháng 10).

            // ===== B3: LẤY BUỔI — CHỈ TRONG THÁNG, KHÔNG LẤY Ô ĐỆM =====
            // Truyền firstDay..lastDay (1/10 → 31/10), KHÔNG phải gridStart..gridEnd.
            List<MyPtSessionRow> rows = sessionDAO.findSessionsInRange(customerId, firstDay, lastDay);
            fillDisplay(rows, customerId, now);              // điền nhãn + trạng thái như getWeek

            // ===== B4: DỰNG LƯỚI RỖNG + "DANH BẠ" NGÀY → Ô =====
            LocalDate today = now.toLocalDate();             // 20/10
            MonthView view = new MonthView(month, today);    // vỏ rỗng, các ô đếm = 0

            // Map để sau này tra nhanh: đưa ngày 21/10 → trả ngay đúng ô 21/10
            Map<LocalDate, MonthView.Cell> cellByDate = new HashMap<>();

            // Vòng NGOÀI: mỗi lần 1 tuần. weekStart = 28/9, 5/10, 12/10, 19/10, 26/10 → dừng khi > 01/11
            for (LocalDate weekStart = gridStart; !weekStart.isAfter(gridEnd); weekStart = weekStart.plusWeeks(1)) {
                List<MonthView.Cell> week = new ArrayList<>();          // 1 hàng của lưới

                // Vòng TRONG: 7 ngày của tuần đó (T2 → CN)
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

            // ===== B5: THẢ BUỔI VÀO Ô + ĐẾM =====
            for (MyPtSessionRow row : rows) {
                // Tra danh bạ: ngày buổi tập → ô tương ứng. Thêm buổi vào ô đó.
                // An toàn vì rows chỉ có ngày trong tháng ⇒ ngày nào cũng có trong Map.
                MonthView.Cell cell = cellByDate.get(row.getSlotDate());
                cell.getSessions().add(row);

                // Cộng 1 vào ô đếm theo trạng thái (dòng tổng cuối trang):
                //   today / upcoming / warn → "sắp tới" | checkedIn | missed | cancelled | còn lại → "đang cập nhật"
                countInto(view, row.getStatusKey());
            }

            return ServiceResult.ok(view);     // Servlet đặt attribute "month" = view

        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);   // lỗi DB ≠ "tháng không có buổi"
        }
    }

    /** Cộng 1 vào ô đếm của tháng. Hôm nay / Sắp tới / Cần chú ý đều tính là "Sắp tới". */
    private void countInto(MonthView view, String statusKey) {
        switch (statusKey) {
            case KEY_TODAY, KEY_UPCOMING, KEY_WARN -> view.setUpcomingCount(view.getUpcomingCount() + 1);
            case KEY_CHECKED_IN -> view.setCheckedInCount(view.getCheckedInCount() + 1);
            case KEY_MISSED -> view.setMissedCount(view.getMissedCount() + 1);
            case KEY_CANCELLED -> view.setCancelledCount(view.getCancelledCount() + 1);
            default -> view.setPendingCount(view.getPendingCount() + 1);
        }
    }

    // ================================================================================================================
    // 3. HIỂN THỊ DÙNG CHUNG — fillDisplay được cả MyPTSession và MyBookings gọi
    // ================================================================================================================

    /** Điền nhãn loại gói, người tập cùng, link Zalo, trạng thái + lý do cho mọi buổi. */
    void fillDisplay(List<MyPtSessionRow> rows, int myCustomerId, LocalDateTime now) throws SQLException {
        if (rows.isEmpty()) {
            return;
        }

        // B1: ngày phòng nghỉ trong khoảng của danh sách (1 câu SQL cho cả danh sách)
        LocalDate first = rows.get(0).getSlotDate();
        LocalDate last = rows.get(rows.size() - 1).getSlotDate();
        Set<LocalDate> closedDays = sessionDAO.findClosedDays(first, last);

        for (MyPtSessionRow row : rows) {
            // B2: nhãn loại gói + link Zalo PT
            row.setTypeLabel(typeLabel(row.getType()));
            row.setZaloUrl(zaloUrl(row.getPtPhone()));

            // B3: gói đôi → "người tập cùng" là người còn lại
            if (row.isCouple() && row.getPartnerCustomerId() != null) {
                boolean iAmHolder = row.getHolderCustomerId() == myCustomerId;
                row.setPartnerName(iAmHolder ? row.getPartnerFullName() : row.getHolderName());
            }

            // B4: cảnh báo chỉ cho buổi CHƯA KẾT THÚC (ngày phòng nghỉ ưu tiên trước)
            // Xét riêng row.slotDate của mỗi buổi, không dùng cảnh báo ngày trước cho ngày sau.
            // Ví dụ seed: 20/10 phòng nghỉ → warn; 21/10 mở cửa và quyền vào gym hợp lệ → upcoming.
            String warning = null;
            if (isNotEnded(row, now)) {
                warning = closedDays.contains(row.getSlotDate())
                        ? CLOSED_DAY_WARNING
                        : buildGymWarning(row, myCustomerId);
            }

            // B5: mã + nhãn + lý do
            applyStatus(row, now, warning);

            // B6: buổi chưa diễn ra → link "Thêm vào Google Calendar"
            if ("Scheduled".equals(row.getStatus()) && isNotEnded(row, now)) {
                row.setCalendarUrl(calendarUrl(row));
            }
        }
    }

    /**
     * Cảnh báo quyền vào gym cho 1 buổi chưa diễn ra (giống JOB-04 — notes 10/10). Null = không cảnh báo.
     * "Vào được gym" = có Membership HOẶC DayPass phủ ngày đó VÀ không đang bảo lưu.
     * Gói đôi: chỉ cần 1 trong 2 người vào được; câu chữ nói theo góc nhìn người đang xem.
     */
    private String buildGymWarning(MyPtSessionRow row, int myCustomerId) throws SQLException {
        LocalDate day = row.getSlotDate();

        // B1: người đứng tên
        int holderId = row.getHolderCustomerId();
        boolean holderFrozen = sessionDAO.isFrozen(holderId, day);
        boolean holderOk = sessionDAO.hasGymPass(holderId, day) && !holderFrozen;

        // B2: gói cá nhân / buổi lẻ → chỉ xét người đứng tên
        if (!row.isCouple() || row.getPartnerCustomerId() == null) {
            if (holderOk) {
                return null;
            }
            row.setNoGymPass(!holderFrozen);   // thiếu gói (không phải bảo lưu) → màn Gói của tôi đưa vào "Cần để ý"
            return holderFrozen
                    ? "Buổi tập nằm trong kỳ bảo lưu. Hãy kiểm tra thời gian bảo lưu trước ngày tập."
                    : "Chưa có Membership hoặc DayPass cho ngày này. Mua trước ngày tập để không bị tính vắng.";
        }

        // B3: gói đôi → xét thêm người đi kèm
        int partnerId = row.getPartnerCustomerId();
        boolean partnerFrozen = sessionDAO.isFrozen(partnerId, day);
        boolean partnerOk = sessionDAO.hasGymPass(partnerId, day) && !partnerFrozen;

        // B4: đổi sang góc nhìn "tôi" / "người còn lại"
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

    /** Mã loại gói → chữ tiếng Việt. */
    static String typeLabel(String type) {
        return switch (type) {
            case "PTSingle" -> "PT cá nhân";
            case "PTCouple" -> "PT đôi";
            default -> "Buổi lẻ";
        };
    }

    /** SĐT → link Zalo. Logic nằm ở ZaloUtil (PT Dashboard cũng dùng); giữ tên cũ cho MyBookingService và test. */
    static String zaloUrl(String phone) {
        return ZaloUtil.toUrl(phone);
    }

    // ================================================================================================================
    // 4. TRẠNG THÁI BUỔI — applyStatus gọi các hàm gán nhãn và giải thích ngay dưới
    // ================================================================================================================

    /**
     * Gán mã + nhãn + lý do theo Sessions.Status (chỉ ĐỌC kết quả đã lưu, không chốt thay JOB-04).
     *   Scheduled chưa kết thúc: có cảnh báo → warn | hôm nay → today | ngày sau → upcoming
     *   Scheduled đã kết thúc → pending | CheckedIn / Missed / Cancelled → giữ nguyên + lý do
     */
    static void applyStatus(MyPtSessionRow row, LocalDateTime now, String warning) {
        row.setEnded(!isNotEnded(row, now));
        boolean couple = row.isCouple();

        switch (row.getStatus()) {
            case "Scheduled" -> {
                if (!isNotEnded(row, now)) {
                    set(row, KEY_PENDING, "Đang cập nhật",
                            "Buổi đã kết thúc. Hệ thống sẽ chốt kết quả sau khi hết ngày tập.");
                } else if (warning != null) {
                    set(row, KEY_WARN, "Cần chú ý", warning);
                } else if (row.getSlotDate().equals(now.toLocalDate())) {
                    boolean started = !now.isBefore(row.getSlotDate().atTime(row.getStartTime()));
                    set(row, KEY_TODAY, started ? "Đang diễn ra" : "Hôm nay", null);
                } else {
                    set(row, KEY_UPCOMING, "Sắp tới", null);
                }
            }
            case "CheckedIn" -> set(row, KEY_CHECKED_IN, "Đã check-in", couple
                    ? "Bạn hoặc người tập cùng đã check-in vào gym hôm đó. Đây là ghi nhận vào cửa, không phải xác nhận đã tập xong với PT."
                    : "Bạn đã check-in vào gym hôm đó. Đây là ghi nhận vào cửa, không phải xác nhận đã tập xong với PT.");
            case "Missed" -> set(row, KEY_MISSED, "Vắng", missedExplanation(row.getMissedReason(), couple));
            default -> set(row, KEY_CANCELLED, "Đã hủy", cancelExplanation(row.getCancelType(), couple));
        }
    }

    /** Gán cùng lúc mã trạng thái, nhãn và lời giải thích cho một buổi. */
    private static void set(MyPtSessionRow row, String key, String label, String explanation) {
        row.setStatusKey(key);
        row.setStatusLabel(label);
        row.setExplanation(explanation);
    }

    /** Vì sao vắng (Sessions.MissedReason). */
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

    /** Vì sao bị hủy (Sessions.CancelType). Dữ liệu cũ vẫn có thể là ByCustomer. */
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

    /** Buổi chưa kết thúc? (bây giờ < giờ kết thúc) */
    static boolean isNotEnded(MyPtSessionRow row, LocalDateTime now) {
        return now.isBefore(row.getSlotDate().atTime(row.getEndTime()));
    }

    // ================================================================================================================
    // 5. LINK GOOGLE CALENDAR — calendarUrl gọi encode để mã hóa nội dung URL
    // ================================================================================================================

    /**
     * Buổi → link "Thêm vào Google Calendar" (không cần API key, Google mở sẵn sự kiện để khách bấm Lưu).
     * Giờ trong DB là giờ VN → đổi sang UTC (đuôi Z) để lịch của khách hiện đúng giờ.
     * Vd 21/10/2026 18:00–19:30 → dates=20261021T110000Z/20261021T123000Z.
     */
    static String calendarUrl(MyPtSessionRow row) {
        // B1: giờ bắt đầu / kết thúc theo giờ VN → UTC
        String start = CALENDAR_TIME.format(row.getSlotDate().atTime(row.getStartTime()).atZone(DateTimeUtil.GYM_ZONE));
        String end = CALENDAR_TIME.format(row.getSlotDate().atTime(row.getEndTime()).atZone(DateTimeUtil.GYM_ZONE));

        // B2: tiêu đề + mô tả (buổi lẻ không có số thứ tự)
        String title = "Buổi PT với " + row.getPtName() + " · Super Gym";
        String details = row.getPackageName()
                + (row.isSingleSession() ? "" : " · buổi " + row.getSessionNo() + "/" + row.getTotalSessions());

        // B3: ghép link, mã hóa chữ tiếng Việt / khoảng trắng
        return "https://calendar.google.com/calendar/render?action=TEMPLATE"
                + "&text=" + encode(title)
                + "&dates=" + start + "/" + end
                + "&details=" + encode(details)
                + "&location=" + encode("Super Gym");
    }

    /** Mã hóa chữ để đặt vào URL (khoảng trắng → %20). */
    private static String encode(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8).replace("+", "%20");
    }

    // ================================================================================================================
    // 6. QUYỀN VÀ NGÀY DÙNG CHUNG — getWeek / getMonth / Servlet và MyBookings gọi
    // ================================================================================================================

    /**
     * Tài khoản đang đăng nhập → mã khách. Null = tài khoản khóa / không phải khách.
     * (accountId lấy trong HttpSession, KHÔNG tin URL.)
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

    /** Ngày URL sai hoặc ngoài khoảng an toàn cho SQL và điều hướng tuần/tháng → hôm nay. */
    public static LocalDate parseViewDate(String value, LocalDate today) {
        // B1: đọc định dạng yyyy-MM-dd; không để ngày cực trị làm tràn phép cộng tuần/tháng.
        LocalDate date = DateTimeUtil.parseHtmlDate(value);
        if (date == null || date.getYear() < 1754 || date.getYear() > 9998) {
            return today;
        }
        // B2: trả ngày hợp lệ; Service tự tìm tuần hoặc tháng chứa ngày này.
        return date;
    }

    /** Ngày bất kỳ → Thứ 2 của tuần đó. Vd 22/10/2026 → 19/10; Chủ nhật 25/10 → 19/10. */
    static LocalDate weekStartOf(LocalDate date) {
        return date.with(DayOfWeek.MONDAY);
    }

}
