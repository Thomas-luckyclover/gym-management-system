package com.gms.service;

import com.gms.constant.Messages;
import com.gms.dao.AccountDAO;
import com.gms.dao.CustomerDAO;
import com.gms.dao.MyPtSessionDAO;
import com.gms.dto.MonthView;
import com.gms.dto.MyPtPackageView;
import com.gms.dto.MyPtSessionRow;
import com.gms.dto.ServiceResult;
import com.gms.dto.TimetableView;
import com.gms.entity.Account;
import com.gms.entity.Customer;
import com.gms.util.DateTimeUtil;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Service màn "Lịch tập của tôi" (UC-36 xem lịch) — THỜI KHÓA BIỂU, CHỈ XEM.
 * [CHOT 19/10] Khách không tự hủy / tự đổi buổi: nhắn PT qua Zalo, PT cập nhật lịch giúp (UC-35).
 *
 * TÌM NHANH (Ctrl+F gõ tag):
 *   [QUYEN]         tìm khách đang đăng nhập                 → findCustomerId
 *   [TKB-TUAN]      thời khóa biểu 1 tuần (5 khung × 7 ngày) → getWeek
 *   [TKB-THANG]     lưới 1 tháng + số đếm                    → getMonth
 *   [TRANG-THAI]    nhãn + màu + lý do của 1 buổi            → fillDisplay, applyStatus
 *   [CANH-BAO-GYM]  câu cảnh báo vàng                        → buildGymWarning
 *   [GOI-PT]        thẻ "Gói PT của bạn"                     → getPtPackages
 *   [HAM-NHO]       hàm tính toán không đụng DB (có unit test) → weekStartOf, bandOf, zaloUrl, applyStatus
 */
public class MyPtSessionService {

    // ---- Mã trạng thái hiển thị (JSP chọn màu theo mã này) ----
    static final String KEY_TODAY = "today";            // buổi hôm nay, chưa kết thúc
    static final String KEY_UPCOMING = "upcoming";      // buổi các ngày sau
    static final String KEY_WARN = "warn";              // sắp tới nhưng có cảnh báo (phòng nghỉ, chưa có quyền vào gym)
    static final String KEY_PENDING = "pending";        // đã kết thúc, chờ JOB-04 chốt
    static final String KEY_CHECKED_IN = "checkedIn";
    static final String KEY_MISSED = "missed";
    static final String KEY_CANCELLED = "cancelled";

    /** Tên 7 ngày, theo thứ tự cột (Thứ 2 → Chủ nhật). */
    private static final String[] DAY_NAMES = {"Thứ 2", "Thứ 3", "Thứ 4", "Thứ 5", "Thứ 6", "Thứ 7", "Chủ nhật"};

    /** 5 khung giờ cố định = 5 hàng của thời khóa biểu. Xếp buổi theo GIỜ BẮT ĐẦU (xem bandOf). */
    private static final String[][] BANDS = {
        {"Sáng sớm", "06:00 – 09:00"},
        {"Sáng", "09:00 – 12:00"},
        {"Trưa", "12:00 – 15:00"},
        {"Chiều", "15:00 – 18:00"},
        {"Tối", "18:00 – 22:00"}
    };

    private final AccountDAO accountDAO = new AccountDAO();
    private final CustomerDAO customerDAO = new CustomerDAO();
    private final MyPtSessionDAO sessionDAO = new MyPtSessionDAO();


    // =====================================================================
    // [QUYEN] TÌM KHÁCH ĐANG ĐĂNG NHẬP
    // =====================================================================

    /**
     * [QUYEN] accountId (lấy trong HttpSession, KHÔNG tin URL) → customerId.
     *
     * @return customerId, hoặc null nếu tài khoản bị khóa / không phải khách hàng
     */
    Integer findCustomerId(int accountId) throws SQLException {
        Account account = accountDAO.findById(accountId);
        if (account == null || account.getStatus() != Account.Status.ACTIVE) {
            return null;
        }

        Customer customer = customerDAO.findByPersonId(account.getPersonId());
        if (customer == null || customer.getDeletedAt() != null) {
            return null;
        }

        return customer.getCustomerId();
    }


    // =====================================================================
    // [TKB-TUAN] THỜI KHÓA BIỂU 1 TUẦN
    // =====================================================================

    /**
     * [TKB-TUAN] Thời khóa biểu của tuần chứa ngày anyDay.
     *
     * @param anyDay ngày bất kỳ trong tuần muốn xem (Servlet đọc ?date=, sai/thiếu → hôm nay)
     * @param now    giờ VN Servlet lấy 1 LẦN cho cả request
     */
    public ServiceResult<TimetableView> getWeek(int accountId, LocalDate anyDay, LocalDateTime now) {
        try {
            Integer customerId = findCustomerId(accountId);
            if (customerId == null) {
                return ServiceResult.fail(Messages.MSG07_NO_PERMISSION);
            }

            // B1: khoảng ngày Thứ 2 → Chủ nhật
            LocalDate monday = weekStartOf(anyDay);
            LocalDate sunday = monday.plusDays(6);

            // B2: 1 câu SQL lấy hết buổi trong tuần + ngày phòng nghỉ
            List<MyPtSessionRow> rows = sessionDAO.findSessionsInRange(customerId, monday, sunday);
            Set<LocalDate> closedDays = sessionDAO.findClosedDays(monday, sunday);
            fillDisplay(rows, customerId, now, closedDays);

            // B3: dựng khung 7 ngày × 5 khung giờ
            LocalDate today = now.toLocalDate();
            TimetableView view = new TimetableView(monday, today);
            for (int i = 0; i < 7; i++) {
                LocalDate date = monday.plusDays(i);
                view.getDays().add(new TimetableView.Day(date, DAY_NAMES[i], date.equals(today)));
            }
            for (String[] band : BANDS) {
                view.getBands().add(new TimetableView.Band(band[0], band[1]));
            }

            // B4: thả từng buổi vào đúng ô (hàng = khung giờ, cột = thứ trong tuần)
            for (MyPtSessionRow row : rows) {
                int dayIndex = row.getSlotDate().getDayOfWeek().getValue() - 1;   // Thứ 2 = 0 … Chủ nhật = 6
                int bandIndex = bandOf(row.getStartTime());
                view.getBands().get(bandIndex).getCells().get(dayIndex).add(row);
            }
            return ServiceResult.ok(view);

        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);   // lỗi DB ≠ "không có buổi"
        }
    }


    // =====================================================================
    // [TKB-THANG] LỊCH 1 THÁNG
    // =====================================================================

    /**
     * [TKB-THANG] Lưới tháng chứa ngày anyDay. Lưới luôn đủ tuần (bắt đầu Thứ 2, kết thúc Chủ nhật),
     * ngày ngoài tháng vẫn có buổi để hiện mờ, nhưng KHÔNG tính vào số đếm.
     */
    public ServiceResult<MonthView> getMonth(int accountId, LocalDate anyDay, LocalDateTime now) {
        try {
            Integer customerId = findCustomerId(accountId);
            if (customerId == null) {
                return ServiceResult.fail(Messages.MSG07_NO_PERMISSION);
            }

            // B1: khoảng ngày của lưới = Thứ 2 tuần chứa ngày 1 → Chủ nhật tuần chứa ngày cuối tháng
            YearMonth month = YearMonth.from(anyDay);
            LocalDate gridStart = weekStartOf(month.atDay(1));
            LocalDate gridEnd = weekStartOf(month.atEndOfMonth()).plusDays(6);

            // B2: cùng 1 hàm DAO với xem tuần, chỉ khác khoảng ngày
            List<MyPtSessionRow> rows = sessionDAO.findSessionsInRange(customerId, gridStart, gridEnd);
            Set<LocalDate> closedDays = sessionDAO.findClosedDays(gridStart, gridEnd);
            fillDisplay(rows, customerId, now, closedDays);

            // B3: dựng lưới tuần × 7 ô, nhớ ô theo ngày để thả buổi vào
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

            // B4: thả buổi vào ô + đếm (chỉ buổi trong tháng)
            for (MyPtSessionRow row : rows) {
                cellByDate.get(row.getSlotDate()).getSessions().add(row);
                if (YearMonth.from(row.getSlotDate()).equals(month)) {
                    countInto(view, row.getStatusKey());
                }
            }
            return ServiceResult.ok(view);

        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /** [TKB-THANG] Cộng 1 vào ô đếm tương ứng. Hôm nay + Sắp tới + Cần chú ý đều là "Sắp tới". */
    private void countInto(MonthView view, String statusKey) {
        switch (statusKey) {
            case KEY_TODAY, KEY_UPCOMING, KEY_WARN -> view.setUpcomingCount(view.getUpcomingCount() + 1);
            case KEY_CHECKED_IN -> view.setCheckedInCount(view.getCheckedInCount() + 1);
            case KEY_MISSED -> view.setMissedCount(view.getMissedCount() + 1);
            case KEY_CANCELLED -> view.setCancelledCount(view.getCancelledCount() + 1);
            default -> view.setPendingCount(view.getPendingCount() + 1);   // KEY_PENDING
        }
    }


    // =====================================================================
    // [TRANG-THAI] ĐIỀN THÔNG TIN HIỂN THỊ CHO TỪNG BUỔI
    // =====================================================================

    /** [TRANG-THAI] Điền nhãn loại gói, người tập cùng, link Zalo, trạng thái cho mọi buổi. */
    private void fillDisplay(List<MyPtSessionRow> rows, int myCustomerId, LocalDateTime now,
            Set<LocalDate> closedDays) throws SQLException {
        for (MyPtSessionRow row : rows) {
            row.setTypeLabel(typeLabel(row.getType()));
            row.setZaloUrl(zaloUrl(row.getPtPhone()));

            // Gói đôi: "người tập cùng" = NGƯỜI CÒN LẠI so với người đang xem
            if (row.isCouple() && row.getPartnerCustomerId() != null) {
                boolean iAmHolder = row.getHolderCustomerId() == myCustomerId;
                row.setPartnerName(iAmHolder ? row.getPartnerFullName() : row.getHolderName());
            }

            // Cảnh báo chỉ cần cho buổi CHƯA KẾT THÚC (buổi đã qua thì đọc kết quả đã lưu)
            String warning = null;
            if (isNotEnded(row, now)) {
                warning = closedDays.contains(row.getSlotDate())
                        ? "Phòng tập nghỉ ngày này. PT sẽ liên hệ để dời buổi sang ngày khác, buổi không bị trừ."
                        : buildGymWarning(row, myCustomerId);
            }
            applyStatus(row, now, warning);
        }
    }

    /**
     * [TRANG-THAI][HAM-NHO] Gán mã + nhãn + lý do theo Sessions.Status (ĐỌC kết quả đã lưu, không tự chốt thay JOB-04).
     *
     *   Scheduled, chưa kết thúc, có cảnh báo  → warn      "Cần chú ý"
     *   Scheduled, chưa kết thúc, hôm nay      → today     "Hôm nay" / "Đang diễn ra"
     *   Scheduled, chưa kết thúc, ngày sau     → upcoming  "Sắp tới"
     *   Scheduled, đã kết thúc                 → pending   "Đang cập nhật"
     *   CheckedIn / Missed / Cancelled         → checkedIn / missed / cancelled
     *
     * @param warning câu cảnh báo (null = không cảnh báo); chỉ dùng cho buổi chưa kết thúc
     */
    static void applyStatus(MyPtSessionRow row, LocalDateTime now, String warning) {
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
    private static boolean isNotEnded(MyPtSessionRow row, LocalDateTime now) {
        return now.isBefore(row.getSlotDate().atTime(row.getEndTime()));
    }

    /** [TRANG-THAI] Vì sao vắng (Sessions.MissedReason). */
    private static String missedExplanation(String reason, boolean couple) {
        if (reason == null) {
            return "Buổi được ghi nhận là vắng. Buổi vẫn tính vào gói.";
        }
        return switch (reason) {
            case "NoCheckIn" -> couple
                    ? "Cả hai không check-in vào gym trong ngày tập. Buổi vẫn tính vào gói."
                    : "Bạn không check-in vào gym trong ngày tập. Buổi vẫn tính vào gói.";
            case "NoMembership" -> couple
                    ? "Cả hai chưa có Membership hoặc DayPass hợp lệ cho ngày tập."
                    : "Bạn chưa có Membership hoặc DayPass hợp lệ cho ngày tập.";
            case "MembershipFrozen" -> couple
                    ? "Ngày tập nằm trong kỳ bảo lưu và cả hai chưa có quyền vào gym hợp lệ."
                    : "Ngày tập nằm trong kỳ bảo lưu Membership của bạn.";
            default -> "Buổi được ghi nhận là vắng. Buổi vẫn tính vào gói.";
        };
    }

    /** [TRANG-THAI] Vì sao bị hủy (Sessions.CancelType). Dữ liệu cũ vẫn có thể là ByCustomer. */
    private static String cancelExplanation(String cancelType, boolean couple) {
        if (cancelType == null) {
            return "Buổi đã bị hủy.";
        }
        return switch (cancelType) {
            case "ByPt" -> "PT đã hủy buổi này.";
            case "ByCustomer" -> couple
                    ? "Buổi được hủy theo yêu cầu của một thành viên. Gói vẫn bị trừ một buổi và không hoàn tiền."
                    : "Buổi được hủy theo yêu cầu của bạn. Gói vẫn bị trừ một buổi và không hoàn tiền.";
            case "GymClosed" -> "Phòng tập đóng cửa vào ngày này nên buổi bị hủy, không trừ buổi.";
            case "PackageExpired" -> "Gói PT đã hết hạn nên buổi chưa tập bị hủy.";
            default -> "Buổi đã bị hủy.";
        };
    }

    /**
     * [CANH-BAO-GYM] Ngày tập có quyền vào gym không? (giống JOB-04 — notes 10/10)
     *   "Vào được gym" = có Membership HOẶC DayPass phủ ngày đó  VÀ  không đang bảo lưu.
     *   Chỉ là CẢNH BÁO, KHÔNG đổi trạng thái buổi. Trả null = không cần cảnh báo.
     */
    private String buildGymWarning(MyPtSessionRow row, int myCustomerId) throws SQLException {
        LocalDate day = row.getSlotDate();

        int holderId = row.getHolderCustomerId();
        boolean holderFrozen = sessionDAO.isFrozen(holderId, day);
        boolean holderOk = sessionDAO.hasGymPass(holderId, day) && !holderFrozen;

        // --- Gói cá nhân / buổi lẻ: chỉ xét người đứng tên ---
        if (!row.isCouple() || row.getPartnerCustomerId() == null) {
            if (holderOk) {
                return null;
            }
            if (holderFrozen) {
                return "Buổi tập nằm trong kỳ bảo lưu. Hãy kiểm tra thời gian bảo lưu trước ngày tập.";
            }
            return "Bạn chưa có Membership hoặc DayPass hợp lệ cho ngày tập này.";
        }

        // --- Gói đôi: xét cả 2 người, nói theo góc nhìn NGƯỜI ĐANG XEM ---
        int partnerId = row.getPartnerCustomerId();
        boolean partnerFrozen = sessionDAO.isFrozen(partnerId, day);
        boolean partnerOk = sessionDAO.hasGymPass(partnerId, day) && !partnerFrozen;

        boolean iAmHolder = holderId == myCustomerId;
        boolean meOk = iAmHolder ? holderOk : partnerOk;
        boolean otherOk = iAmHolder ? partnerOk : holderOk;
        boolean meFrozen = iAmHolder ? holderFrozen : partnerFrozen;

        if (meOk && otherOk) {
            return null;
        }
        if (meOk) {
            return "Người tập cùng chưa có quyền vào gym hợp lệ cho ngày này. Bạn vẫn có thể tham gia buổi tập.";
        }
        if (otherOk) {
            return meFrozen
                    ? "Bạn đang trong kỳ bảo lưu vào ngày tập. Người tập cùng vẫn có thể tham gia buổi tập."
                    : "Bạn chưa có quyền vào gym cho ngày này. Người tập cùng vẫn có thể tham gia buổi tập.";
        }
        if (holderFrozen || partnerFrozen) {
            return "Buổi tập trùng kỳ bảo lưu và cả hai chưa có quyền vào gym hợp lệ. Hãy kiểm tra Membership hoặc DayPass trước ngày tập.";
        }
        return "Cả hai chưa có Membership hoặc DayPass hợp lệ cho ngày tập này.";
    }


    // =====================================================================
    // [GOI-PT] GÓI PT (thẻ "Gói PT của bạn" — chuyển sang màn "Gói của tôi" ở GĐ4)
    // =====================================================================

    /** [GOI-PT] Thẻ "Gói PT của bạn": chỉ gói Active, kèm số liệu. */
    public ServiceResult<List<MyPtPackageView>> getPtPackages(int accountId) {
        try {
            Integer customerId = findCustomerId(accountId);
            if (customerId == null) {
                return ServiceResult.fail(Messages.MSG07_NO_PERMISSION);
            }

            LocalDateTime now = DateTimeUtil.now();
            List<MyPtPackageView> packages = sessionDAO.findPtPackages(customerId, true);

            for (MyPtPackageView p : packages) {
                p.setPtName(sessionDAO.findEmployeeName(p.getPtEmployeeId()));
                p.setPtPhone(sessionDAO.findEmployeePhone(p.getPtEmployeeId()));
                p.setTypeLabel(typeLabel(p.getType()));

                // Gói đôi: "người tập cùng" = người còn lại
                if (p.isCouple() && p.getPartnerCustomerId() != null) {
                    int otherId = p.getHolderCustomerId() == customerId ? p.getPartnerCustomerId() : p.getHolderCustomerId();
                    p.setPartnerName(sessionDAO.findCustomerName(otherId));
                }

                // Lượt đổi còn lại = giới hạn LÚC MUA − đã dùng (GBR-04, BR-73). Không để âm.
                p.setRemainingSwaps(Math.max(0, p.getMaxSwaps() - p.getSwapsUsed()));
                p.setUpcomingSessionCount(sessionDAO.countUpcomingSessionsOfPackage(p.getSubscriptionId(), now));
            }
            return ServiceResult.ok(packages);

        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }


    // =====================================================================
    // [HAM-NHO] HÀM TÍNH TOÁN KHÔNG ĐỤNG DB (unit test: MyPtSessionServiceTest)
    // =====================================================================

    /** [HAM-NHO] Ngày bất kỳ → Thứ 2 của tuần đó. Vd 22/10/2026 (Thứ 5) → 19/10; 25/10 (Chủ nhật) → 19/10. */
    static LocalDate weekStartOf(LocalDate date) {
        return date.with(DayOfWeek.MONDAY);
    }

    /**
     * [HAM-NHO] Giờ bắt đầu → số thứ tự khung giờ (hàng của thời khóa biểu).
     *   trước 09:00 → 0 Sáng sớm | 09:00–11:59 → 1 Sáng | 12:00–14:59 → 2 Trưa
     *   15:00–17:59 → 3 Chiều    | từ 18:00   → 4 Tối
     */
    static int bandOf(LocalTime startTime) {
        int hour = startTime.getHour();
        if (hour < 9) {
            return 0;
        }
        if (hour < 12) {
            return 1;
        }
        if (hour < 15) {
            return 2;
        }
        if (hour < 18) {
            return 3;
        }
        return 4;
    }

    /**
     * [HAM-NHO] Số điện thoại PT → link Zalo. Bỏ ký tự không phải số; "+84…" đổi về "0…".
     * Vd "0912 345 678" và "+84912345678" → https://zalo.me/0912345678. Không có số → null.
     */
    static String zaloUrl(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        String digits = phone.replaceAll("\\D", "");
        if (digits.startsWith("84") && digits.length() == 11) {
            digits = "0" + digits.substring(2);
        }
        return digits.isEmpty() ? null : "https://zalo.me/" + digits;
    }

    /** Mã loại gói → chữ tiếng Việt. */
    private static String typeLabel(String type) {
        return switch (type) {
            case "PTSingle" -> "PT cá nhân";
            case "PTCouple" -> "PT đôi";
            default -> "Buổi lẻ";   // SingleSession
        };
    }
}
