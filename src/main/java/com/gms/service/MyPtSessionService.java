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
 * Màn "Lịch tập" (/customer/pt-sessions) — thời khóa biểu tuần / tháng, CHỈ XEM.
 * Khách không tự hủy / đổi buổi: nhắn PT qua Zalo, PT cập nhật lịch (chốt 19/10).
 * Các hàm fillDisplay, findCustomerId, typeLabel, zaloUrl được màn "Gói của tôi" dùng lại.
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

    private static final String[] DAY_NAMES = {"Thứ 2", "Thứ 3", "Thứ 4", "Thứ 5", "Thứ 6", "Thứ 7", "Chủ nhật"};

    /** 5 khung giờ = 5 hàng của thời khóa biểu (xếp theo giờ BẮT ĐẦU, xem bandOf). */
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

    /** Thời khóa biểu tuần chứa ngày anyDay: 7 ngày × 5 khung giờ. */
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

            // B3: dựng khung rỗng 7 ngày × 5 khung giờ
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

            // B3: lấy buổi (cùng hàm DAO với xem tuần), điền nhãn + trạng thái
            List<MyPtSessionRow> rows = sessionDAO.findSessionsInRange(customerId, gridStart, gridEnd);
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

            // B5: thả buổi vào ô; chỉ đếm buổi thuộc tháng đang xem
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
            String warning = null;
            if (isNotEnded(row, now)) {
                warning = closedDays.contains(row.getSlotDate())
                        ? "Phòng tập nghỉ ngày này. PT sẽ liên hệ để dời buổi sang ngày khác, buổi không bị trừ."
                        : buildGymWarning(row, myCustomerId);
            }

            // B5: mã + nhãn + lý do
            applyStatus(row, now, warning);
        }
    }

    /**
     * Gán mã + nhãn + lý do theo Sessions.Status (chỉ ĐỌC kết quả đã lưu, không chốt thay JOB-04).
     *   Scheduled chưa kết thúc: có cảnh báo → warn | hôm nay → today | ngày sau → upcoming
     *   Scheduled đã kết thúc → pending | CheckedIn / Missed / Cancelled → giữ nguyên + lý do
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
                    ? "Cả hai chưa có Membership hoặc DayPass hợp lệ cho ngày tập."
                    : "Bạn chưa có Membership hoặc DayPass hợp lệ cho ngày tập.";
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
                    ? "Buổi được hủy theo yêu cầu của một thành viên. Gói vẫn bị trừ một buổi và không hoàn tiền."
                    : "Buổi được hủy theo yêu cầu của bạn. Gói vẫn bị trừ một buổi và không hoàn tiền.";
            case "GymClosed" -> "Phòng tập đóng cửa vào ngày này nên buổi bị hủy, không trừ buổi.";
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
            return holderFrozen
                    ? "Buổi tập nằm trong kỳ bảo lưu. Hãy kiểm tra thời gian bảo lưu trước ngày tập."
                    : "Bạn chưa có Membership hoặc DayPass hợp lệ cho ngày tập này.";
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
    // HÀM NHỎ — không đụng DB, có unit test (MyPtSessionServiceTest)
    // =====================================================================

    /** Ngày bất kỳ → Thứ 2 của tuần đó. Vd 22/10/2026 → 19/10; Chủ nhật 25/10 → 19/10. */
    static LocalDate weekStartOf(LocalDate date) {
        return date.with(DayOfWeek.MONDAY);
    }

    /** Giờ bắt đầu → khung giờ: <9h Sáng sớm(0) | <12h Sáng(1) | <15h Trưa(2) | <18h Chiều(3) | còn lại Tối(4). */
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

    /** Mã loại gói → chữ tiếng Việt. */
    static String typeLabel(String type) {
        return switch (type) {
            case "PTSingle" -> "PT cá nhân";
            case "PTCouple" -> "PT đôi";
            default -> "Buổi lẻ";
        };
    }
}
