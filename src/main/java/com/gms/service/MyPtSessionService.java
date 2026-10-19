package com.gms.service;

import com.gms.constant.Messages;
import com.gms.dao.AccountDAO;
import com.gms.dao.CustomerDAO;
import com.gms.dao.MyPtSessionDAO;
import com.gms.dto.MyPtPackageView;
import com.gms.dto.MyPtSessionPage;
import com.gms.dto.MyPtSessionRow;
import com.gms.dto.ServiceResult;
import com.gms.entity.Account;
import com.gms.entity.Customer;
import com.gms.util.DateTimeUtil;
import com.gms.util.ValidationUtil;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Service màn "Lịch tập của tôi" (UC-36 xem lịch). Màn CHỈ XEM.
 * [CHOT 19/10] Bỏ khách tự hủy (UC-39) / tự đổi buổi (UC-38): khách nhắn PT qua Zalo.
 *
 * TÌM NHANH (Ctrl+F gõ tag):
 *   [QUYEN]         tìm khách đang đăng nhập                → findCustomerId
 *   [GOI-PT]        thẻ "Gói PT của bạn"                     → getPtPackages
 *   [SAP-TOI]       1 trang lịch sắp tới                    → getUpcomingPage
 *   [LICH-SU]       1 trang lịch sử                         → getHistoryPage
 *   [PHAN-TRANG]    tính trang, cắt 8 ngày                  → parsePageNumber, countPages, clampPage, cutDaysOfPage
 *   [SDT-PT]        số điện thoại PT cho khách xem          → getUpcomingPage, fillPackageNames
 *   [CANH-BAO-GYM]  câu cảnh báo vàng                       → buildGymWarning
 */
public class MyPtSessionService {

    /** [PHAN-TRANG] Mỗi trang tối đa 8 NGÀY có buổi (notes 11/10). */
    private static final int DAYS_PER_PAGE = 8;

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
    // [GOI-PT] GÓI PT
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
                fillPackageNames(p, customerId);
                p.setTypeLabel(typeLabel(p.getType()));

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

    /** [GOI-PT] Điền tên PT và tên "người tập cùng" cho 1 gói. */
    private void fillPackageNames(MyPtPackageView p, int myCustomerId) throws SQLException {
        p.setPtName(sessionDAO.findEmployeeName(p.getPtEmployeeId()));
        p.setPtPhone(sessionDAO.findEmployeePhone(p.getPtEmployeeId()));   // [SDT-PT]

        Integer otherId = otherPersonId(p.isCouple(), p.getHolderCustomerId(), p.getPartnerCustomerId(), myCustomerId);
        if (otherId != null) {
            p.setPartnerName(sessionDAO.findCustomerName(otherId));
        }
    }


    // =====================================================================
    // [PHAN-TRANG] SỐ TRANG TRÊN URL
    // =====================================================================

    /** [PHAN-TRANG] Số trang trên URL: thiếu / sai / nhỏ hơn 1 → trang 1 (không lỗi 500). */
    public int parsePageNumber(String value) {
        Integer page = parsePositiveInt(value);
        return page == null ? 1 : page;
    }


    // =====================================================================
    // [SAP-TOI] LỊCH TẬP SẮP TỚI (1 trang)
    // =====================================================================

    /**
     * [SAP-TOI] Buổi Scheduled CHƯA KẾT THÚC (đang diễn ra vẫn ở đây).
     *
     * @param now giờ VN Servlet lấy 1 LẦN, dùng chung với [LICH-SU] để mỗi buổi chỉ nằm ở 1 khối
     */
    public ServiceResult<MyPtSessionPage> getUpcomingPage(int accountId, LocalDateTime now, int requestedPage) {
        try {
            Integer customerId = findCustomerId(accountId);
            if (customerId == null) {
                return ServiceResult.fail(Messages.MSG07_NO_PERMISSION);
            }

            // B1 [PHAN-TRANG]: lấy TẤT CẢ các ngày có buổi (chỉ lấy ngày → rất nhẹ)
            List<LocalDate> allDays = sessionDAO.findUpcomingDays(customerId, now);

            // B2 [PHAN-TRANG]: tính trang, cắt tối đa 8 ngày của trang đang xem
            int totalPages = countPages(allDays.size());
            int currentPage = clampPage(requestedPage, totalPages);
            List<LocalDate> pageDays = cutDaysOfPage(allDays, currentPage);

            // B3: lấy các buổi của những ngày đó
            List<MyPtSessionRow> rows = new ArrayList<>();
            if (!pageDays.isEmpty()) {
                rows = sessionDAO.findUpcomingSessions(customerId, now,
                        Collections.min(pageDays), Collections.max(pageDays));
            }

            // B4: điền thông tin hiển thị cho từng buổi
            for (MyPtSessionRow row : rows) {
                fillCommonInfo(row, customerId);
                row.setPtPhone(sessionDAO.findEmployeePhone(row.getPtEmployeeId()));   // [SDT-PT] để khách liên hệ PT

                // Đang diễn ra = đã tới giờ bắt đầu (bây giờ >= giờ bắt đầu)
                LocalDateTime startsAt = row.getSlotDate().atTime(row.getStartTime());
                row.setInProgress(!now.isBefore(startsAt));

                row.setWarning(buildGymWarning(row, customerId));   // [CANH-BAO-GYM]
            }

            // B5: gom buổi theo ngày → 1 trang
            MyPtSessionPage page = new MyPtSessionPage(groupByDay(rows), currentPage, totalPages, allDays.size());
            return ServiceResult.ok(page);

        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);   // lỗi DB ≠ "không có buổi"
        }
    }


    // =====================================================================
    // [LICH-SU] LỊCH SỬ BUỔI TẬP (1 trang) — giống [SAP-TOI], chỉ khác hàm DAO và nhãn
    // =====================================================================

    /** [LICH-SU] CheckedIn / Missed / Cancelled + Scheduled đã kết thúc (chờ JOB-04). Mới nhất trước. */
    public ServiceResult<MyPtSessionPage> getHistoryPage(int accountId, LocalDateTime now, int requestedPage) {
        try {
            Integer customerId = findCustomerId(accountId);
            if (customerId == null) {
                return ServiceResult.fail(Messages.MSG07_NO_PERMISSION);
            }

            // B1 [PHAN-TRANG]
            List<LocalDate> allDays = sessionDAO.findHistoryDays(customerId, now);

            // B2 [PHAN-TRANG]
            int totalPages = countPages(allDays.size());
            int currentPage = clampPage(requestedPage, totalPages);
            List<LocalDate> pageDays = cutDaysOfPage(allDays, currentPage);

            // B3
            List<MyPtSessionRow> rows = new ArrayList<>();
            if (!pageDays.isEmpty()) {
                rows = sessionDAO.findHistorySessions(customerId, now,
                        Collections.min(pageDays), Collections.max(pageDays));
            }

            // B4: chỉ ĐỌC kết quả đã lưu, không tự chốt thay JOB-04
            for (MyPtSessionRow row : rows) {
                fillCommonInfo(row, customerId);
                fillHistoryStatus(row);
            }

            // B5
            MyPtSessionPage page = new MyPtSessionPage(groupByDay(rows), currentPage, totalPages, allDays.size());
            return ServiceResult.ok(page);

        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }


    // =====================================================================
    // ĐIỀN THÔNG TIN HIỂN THỊ CHO 1 BUỔI
    // =====================================================================

    /** Tên PT, tên người tập cùng, nhãn loại buổi (dùng cho cả [SAP-TOI] và [LICH-SU]). */
    private void fillCommonInfo(MyPtSessionRow row, int myCustomerId) throws SQLException {
        row.setPtName(sessionDAO.findEmployeeName(row.getPtEmployeeId()));
        row.setTypeLabel(typeLabel(row.getType()));

        Integer otherId = otherPersonId(row.isCouple(), row.getHolderCustomerId(), row.getPartnerCustomerId(), myCustomerId);
        if (otherId != null) {
            row.setPartnerName(sessionDAO.findCustomerName(otherId));
        }
    }

    /** [LICH-SU] Badge + câu giải thích theo Sessions.Status (đọc kết quả đã lưu, không tính lại). */
    private void fillHistoryStatus(MyPtSessionRow row) {
        boolean couple = row.isCouple();

        switch (row.getStatus()) {
            case "Scheduled" -> {   // đã kết thúc nhưng JOB-04 chưa chốt
                row.setStatusLabel("Kết quả đang cập nhật");
                row.setBadgeClass("badge-info");
                row.setExplanation("Buổi đã kết thúc. Hệ thống sẽ chốt kết quả sau khi ngày tập kết thúc.");
            }
            case "CheckedIn" -> {   // chỉ là có vào gym, KHÔNG xác nhận đã tập xong với PT
                row.setStatusLabel("Đã check-in");
                row.setBadgeClass("badge-success");
            }
            case "Missed" -> {
                row.setStatusLabel("Vắng");
                row.setBadgeClass("badge-danger");
                row.setExplanation(missedExplanation(row.getMissedReason(), couple));
            }
            default -> {            // "Cancelled"
                row.setStatusLabel("Đã hủy");
                row.setBadgeClass("badge-neutral");
                row.setExplanation(cancelExplanation(row.getCancelType(), couple));
            }
        }
    }

    /** [LICH-SU] Vì sao vắng (Sessions.MissedReason). */
    private String missedExplanation(String reason, boolean couple) {
        if (reason == null) {
            return "Buổi được ghi nhận là vắng.";
        }
        return switch (reason) {
            case "NoCheckIn" -> couple
                    ? "Cả hai không check-in vào gym trong ngày tập."
                    : "Bạn không check-in vào gym trong ngày tập.";
            case "NoMembership" -> couple
                    ? "Cả hai chưa có Membership hoặc DayPass hợp lệ cho ngày tập."
                    : "Bạn chưa có Membership hoặc DayPass hợp lệ cho ngày tập.";
            case "MembershipFrozen" -> couple
                    ? "Ngày tập nằm trong kỳ bảo lưu và cả hai chưa có quyền vào gym hợp lệ."
                    : "Ngày tập nằm trong kỳ bảo lưu Membership của bạn.";
            default -> "Buổi được ghi nhận là vắng.";
        };
    }

    /** [LICH-SU] Vì sao bị hủy (Sessions.CancelType). Dữ liệu cũ vẫn có thể là ByCustomer. */
    private String cancelExplanation(String cancelType, boolean couple) {
        if (cancelType == null) {
            return "Buổi đã bị hủy.";
        }
        return switch (cancelType) {
            case "ByPt" -> "PT đã hủy buổi này.";
            case "ByCustomer" -> couple
                    ? "Buổi được hủy theo yêu cầu của một thành viên. Gói vẫn bị trừ một buổi và không hoàn tiền."
                    : "Buổi được hủy theo yêu cầu của bạn. Gói vẫn bị trừ một buổi và không hoàn tiền.";
            case "GymClosed" -> "Phòng tập đóng cửa vào ngày này nên buổi bị hủy.";
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
    // HÀM NHỎ DÙNG CHUNG
    // =====================================================================

    /** Mã loại gói → chữ tiếng Việt. */
    private String typeLabel(String type) {
        return switch (type) {
            case "PTSingle" -> "PT cá nhân";
            case "PTCouple" -> "PT đôi";
            default -> "Buổi lẻ";   // SingleSession
        };
    }

    /**
     * Gói đôi: "người tập cùng" = NGƯỜI CÒN LẠI.
     *   Tôi đứng tên → người đi kèm ; tôi đi kèm → người đứng tên ; không phải gói đôi → null.
     */
    private Integer otherPersonId(boolean couple, int holderId, Integer partnerId, int myCustomerId) {
        if (!couple || partnerId == null) {
            return null;
        }
        return holderId == myCustomerId ? partnerId : holderId;
    }

    /** [PHAN-TRANG] Tổng số trang: 0 ngày → 1 trang ; 1..8 → 1 ; 9..16 → 2 ; ... */
    private int countPages(int totalDays) {
        if (totalDays == 0) {
            return 1;
        }
        return (totalDays + DAYS_PER_PAGE - 1) / DAYS_PER_PAGE;   // chia làm tròn LÊN
    }

    /** [PHAN-TRANG] Ép số trang vào khoảng [1, totalPages] (gõ ?upcomingPage=999 vẫn không lỗi). */
    private int clampPage(int page, int totalPages) {
        return Math.max(1, Math.min(page, totalPages));
    }

    /** [PHAN-TRANG] Cắt ra các ngày của 1 trang. Vd trang 2 → ngày thứ 9..16. */
    private List<LocalDate> cutDaysOfPage(List<LocalDate> allDays, int page) {
        int from = (page - 1) * DAYS_PER_PAGE;
        int to = Math.min(from + DAYS_PER_PAGE, allDays.size());
        if (from >= to) {
            return new ArrayList<>();
        }
        return allDays.subList(from, to);
    }

    /** [PHAN-TRANG] Gom các buổi (đã sắp theo ngày) thành từng nhóm ngày, giữ nguyên thứ tự. */
    private List<MyPtSessionPage.Day> groupByDay(List<MyPtSessionRow> rows) {
        List<MyPtSessionPage.Day> days = new ArrayList<>();
        MyPtSessionPage.Day currentDay = null;

        for (MyPtSessionRow row : rows) {
            // Sang ngày mới → mở nhóm mới
            if (currentDay == null || !currentDay.getDate().equals(row.getSlotDate())) {
                currentDay = new MyPtSessionPage.Day(row.getSlotDate());
                days.add(currentDay);
            }
            currentDay.getSessions().add(row);
        }
        return days;
    }

    /** "12" → 12 ; "abc", "-1", "0", rỗng → null. */
    private Integer parsePositiveInt(String value) {
        if (ValidationUtil.isNullOrEmpty(value)) {
            return null;
        }
        try {
            int number = Integer.parseInt(value.trim());
            return number > 0 ? number : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
