package com.gms.service;

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
        {"Sáng", "06:00 – 12:00"},
        {"Chiều", "12:00 – 18:00"},
        {"Tối", "18:00 – 22:00"}
    };

    private final AccountDAO accountDAO = new AccountDAO();
    private final CustomerDAO customerDAO = new CustomerDAO();
    private final MyPtSessionDAO sessionDAO = new MyPtSessionDAO();


    // =====================================================================
    // QUYỀN
    // =====================================================================

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


    // =====================================================================
    // XEM THEO TUẦN
    // =====================================================================

    /** Thời khóa biểu tuần chứa ngày anyDay: 7 ngày × 3 khung giờ. */
    public ServiceResult<TimetableView> getWeek(int accountId, LocalDate anyDay, LocalDateTime now) {
        try {
            // B1: tìm khách
            Integer customerId = findCustomerId(accountId);
            if (customerId == null) {
                return ServiceResult.fail(Messages.MSG07_NO_PERMISSION);
            }

            // B2: lấy buổi từ Thứ 2 → Chủ nhật (1 câu SQL), điền nhãn + trạng thái
            LocalDate monday = weekStartOf(anyDay);
            LocalDate sunday = monday.plusDays(6);
            List<MyPtSessionRow> rows = sessionDAO.findSessionsInRange(customerId, monday, sunday);
            fillDisplay(rows, customerId, now);

            // B3: dựng khung rỗng 7 ngày × 3 khung giờ
            LocalDate today = now.toLocalDate();
            TimetableView view = new TimetableView(monday, today);
            for (int i = 0; i < 7; i++) {
                LocalDate date = monday.plusDays(i);
                view.getDays().add(new TimetableView.Day(date, DAY_NAMES[i], date.equals(today)));
            }
            for (String[] band : BANDS) {
                view.getBands().add(new TimetableView.Band(band[0], band[1]));
            }

            // B4: thả từng buổi vào ô [khung giờ][thứ]
            for (MyPtSessionRow row : rows) {
                int dayIndex = row.getSlotDate().getDayOfWeek().getValue() - 1;   // Thứ 2 = 0 … CN = 6
                view.getBands().get(bandOf(row.getStartTime())).getCells().get(dayIndex).add(row);
            }
            return ServiceResult.ok(view);

        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);   // lỗi DB ≠ "không có buổi"
        }
    }


    // =====================================================================
    // XEM THEO THÁNG
    // =====================================================================

    /** Lưới tháng chứa ngày anyDay (đủ tuần Thứ 2 → CN) + số đếm theo trạng thái. */
    public ServiceResult<MonthView> getMonth(int accountId, LocalDate anyDay, LocalDateTime now) {
        try {
            // B1: tìm khách
            Integer customerId = findCustomerId(accountId);
            if (customerId == null) {
                return ServiceResult.fail(Messages.MSG07_NO_PERMISSION);
            }

            // B2: khoảng ngày của lưới = Thứ 2 tuần đầu → Chủ nhật tuần cuối
            YearMonth month = YearMonth.from(anyDay);
            LocalDate gridStart = weekStartOf(month.atDay(1));
            LocalDate gridEnd = weekStartOf(month.atEndOfMonth()).plusDays(6);

            // B3: chỉ lấy buổi trong tháng đang xem; ô đệm đầu/cuối tuần để trống.
            List<MyPtSessionRow> rows = sessionDAO.findSessionsInRange(customerId, month.atDay(1), month.atEndOfMonth());
            fillDisplay(rows, customerId, now);

            // B4: dựng lưới, nhớ ô theo ngày
            LocalDate today = now.toLocalDate();
            MonthView view = new MonthView(month, today);
            Map<LocalDate, MonthView.Cell> cellByDate = new HashMap<>();
            for (LocalDate weekStart = gridStart; !weekStart.isAfter(gridEnd); weekStart = weekStart.plusWeeks(1)) {
                List<MonthView.Cell> week = new ArrayList<>();
                for (int i = 0; i < 7; i++) {
                    LocalDate date = weekStart.plusDays(i);
                    MonthView.Cell cell = new MonthView.Cell(date, YearMonth.from(date).equals(month), date.equals(today));
                    week.add(cell);
                    cellByDate.put(date, cell);
                }
                view.getWeeks().add(week);
            }

            // B5: thả và đếm các buổi của đúng tháng đã chọn.
            for (MyPtSessionRow row : rows) {
                cellByDate.get(row.getSlotDate()).getSessions().add(row);
                countInto(view, row.getStatusKey());
            }
            return ServiceResult.ok(view);

        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
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


    // =====================================================================
    // ĐIỀN THÔNG TIN HIỂN THỊ (dùng chung với màn "Gói của tôi")
    // =====================================================================

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

    private static void set(MyPtSessionRow row, String key, String label, String explanation) {
        row.setStatusKey(key);
        row.setStatusLabel(label);
        row.setExplanation(explanation);
    }

    /** Buổi chưa kết thúc? (bây giờ < giờ kết thúc) */
    static boolean isNotEnded(MyPtSessionRow row, LocalDateTime now) {
        return now.isBefore(row.getSlotDate().atTime(row.getEndTime()));
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


    // =====================================================================
    // HÀM NHỎ — không đụng DB, có unit test (MyPtSessionServiceTest)
    // =====================================================================

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

    /** Xếp theo giờ bắt đầu: trước 12h là Sáng, trước 18h là Chiều, còn lại là Tối. */
    static int bandOf(LocalTime startTime) {
        if (startTime.getHour() < 12) {
            return 0;
        }
        return startTime.getHour() < 18 ? 1 : 2;
    }

    /** SĐT → link Zalo. "0912 345 678" / "+84912345678" → https://zalo.me/0912345678. Không có số → null. */
    static String zaloUrl(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        // B1: chỉ giữ chữ số
        String digits = phone.replaceAll("\\D", "");
        // B2: đầu 84 (mã nước) → đổi về 0
        if (digits.startsWith("84") && digits.length() == 11) {
            digits = "0" + digits.substring(2);
        }
        return digits.isEmpty() ? null : "https://zalo.me/" + digits;
    }

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

    /** Định dạng giờ Google Calendar cần: yyyyMMdd'T'HHmmss'Z' theo UTC. */
    private static final DateTimeFormatter CALENDAR_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    /** Mã hóa chữ để đặt vào URL (khoảng trắng → %20). */
    private static String encode(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /** Mã loại gói → chữ tiếng Việt. */
    static String typeLabel(String type) {
        return switch (type) {
            case "PTSingle" -> "PT cá nhân";
            case "PTCouple" -> "PT đôi";
            default -> "Buổi lẻ";
        };
    }
}
