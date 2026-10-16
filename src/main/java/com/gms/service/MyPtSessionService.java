package com.gms.service;

import com.gms.constant.AppConstants;
import com.gms.constant.Messages;
import com.gms.dao.AccountDAO;
import com.gms.dao.CustomerDAO;
import com.gms.dao.MyPtSessionDAO;
import com.gms.dao.NotificationDAO;
import com.gms.dto.MyPtPackageView;
import com.gms.dto.MyPtSessionFilter;
import com.gms.dto.MyPtSessionPage;
import com.gms.dto.MyPtSessionRow;
import com.gms.dto.PtSessionInfo;
import com.gms.dto.ServiceResult;
import com.gms.entity.Account;
import com.gms.entity.Customer;
import com.gms.util.DBContext;
import com.gms.util.DateTimeUtil;
import com.gms.util.ValidationUtil;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Service màn "Lịch tập của tôi" (UC-36 xem lịch, UC-39 hủy buổi).
 *
 * TÌM NHANH (Ctrl+F gõ tag):
 *   [QUYEN]         tìm khách đang đăng nhập, kiểm tra buổi có phải của mình → findCustomerId, isMySession
 *   [GOI-PT]        thẻ "Gói PT của bạn" + ô lọc gói         → getPtPackages, getPtPackageOptions
 *   [LOC]           kiểm tra bộ lọc trên URL                → parseFilter, parsePageNumber
 *   [SAP-TOI]       1 trang lịch sắp tới                    → getUpcomingPage
 *   [LICH-SU]       1 trang lịch sử                         → getHistoryPage
 *   [PHAN-TRANG]    tính trang, cắt 8 ngày                  → countPages, clampPage, cutDaysOfPage
 *   [CANH-BAO-GYM]  câu cảnh báo vàng                       → buildGymWarning
 *   [NUT]           bật/tắt nút Hủy, Đổi trên từng buổi     → fillActionFlags
 *   [HUY-BUOI]      khách hủy buổi                          → cancelPtSession
 *   [THONG-BAO]     gửi thông báo khi hủy                   → notifyCancel
 */
public class MyPtSessionService {

    /** [PHAN-TRANG] Mỗi trang tối đa 8 NGÀY có buổi (notes 11/10). */
    private static final int DAYS_PER_PAGE = 8;

    private final AccountDAO accountDAO = new AccountDAO();
    private final CustomerDAO customerDAO = new CustomerDAO();
    private final MyPtSessionDAO sessionDAO = new MyPtSessionDAO();
    private final NotificationDAO notificationDAO = new NotificationDAO();


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

    /** [QUYEN] Buổi này là của khách? = khách ĐỨNG TÊN gói, hoặc là NGƯỜI ĐI KÈM gói đôi (BR-77). */
    static boolean isMySession(PtSessionInfo info, int customerId) {
        boolean isHolder = info.getCustomerId() == customerId;
        boolean isPartner = info.getPartnerCustomerId() != null && info.getPartnerCustomerId() == customerId;
        return isHolder || isPartner;
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

    /** [GOI-PT][LOC] Ô lọc "Gói PT": mọi gói đã mua (kể cả gói cũ) để tra lịch sử. */
    public ServiceResult<List<MyPtPackageView>> getPtPackageOptions(int accountId) {
        try {
            Integer customerId = findCustomerId(accountId);
            if (customerId == null) {
                return ServiceResult.fail(Messages.MSG07_NO_PERMISSION);
            }

            List<MyPtPackageView> packages = sessionDAO.findPtPackages(customerId, false);
            for (MyPtPackageView p : packages) {
                p.setPtName(sessionDAO.findEmployeeName(p.getPtEmployeeId()));
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

        Integer otherId = otherPersonId(p.isCouple(), p.getHolderCustomerId(), p.getPartnerCustomerId(), myCustomerId);
        if (otherId != null) {
            p.setPartnerName(sessionDAO.findCustomerName(otherId));
        }
    }


    // =====================================================================
    // [LOC] KIỂM TRA THAM SỐ TRÊN URL
    // =====================================================================

    /**
     * [LOC] Kiểm tra từng ô lọc. Ô trống = không lọc ô đó.
     * Có ô sai → trả lỗi theo từng ô; Servlet sẽ dùng bộ lọc rỗng none() (không lọc nửa vời).
     */
    public ServiceResult<MyPtSessionFilter> parseFilter(String type, String dateFrom, String dateTo,
            String historyStatus, String subscriptionId, String timeOfDay,
            List<MyPtPackageView> myPackages) {

        Map<String, String> errors = new LinkedHashMap<>();   // tên ô → câu lỗi
        MyPtSessionFilter filter = new MyPtSessionFilter();

        // [LOC] Ô "Gói PT": phải là số VÀ là gói của chính khách (chống xem lén gói người khác)
        if (!ValidationUtil.isNullOrEmpty(subscriptionId)) {
            Integer id = parsePositiveInt(subscriptionId);
            if (id == null || !isMyPackage(id, myPackages)) {
                errors.put("subscriptionId", Messages.MSG108_INVALID_OPTION);
            } else {
                filter.setSubscriptionId(id);
            }
        }

        // [LOC] Ô "Loại buổi": chỉ 3 giá trị
        if (!ValidationUtil.isNullOrEmpty(type)) {
            if (isOneOf(type.trim(), "PTSingle", "PTCouple", "SingleSession")) {
                filter.setType(type.trim());
            } else {
                errors.put("type", Messages.MSG108_INVALID_OPTION);
            }
        }

        // [LOC] Ô "Buổi trong ngày": chỉ 3 giá trị
        if (!ValidationUtil.isNullOrEmpty(timeOfDay)) {
            if (isOneOf(timeOfDay.trim(), "morning", "afternoon", "evening")) {
                filter.setTimeOfDay(timeOfDay.trim());
            } else {
                errors.put("timeOfDay", Messages.MSG108_INVALID_OPTION);
            }
        }

        // [LOC] Ô "Từ ngày" / "Đến ngày": đúng dạng yyyy-MM-dd
        LocalDate from = DateTimeUtil.parseHtmlDate(dateFrom);
        if (!ValidationUtil.isNullOrEmpty(dateFrom) && from == null) {
            errors.put("dateFrom", Messages.MSG109_INVALID_DATE);
        }

        LocalDate to = DateTimeUtil.parseHtmlDate(dateTo);
        if (!ValidationUtil.isNullOrEmpty(dateTo) && to == null) {
            errors.put("dateTo", Messages.MSG109_INVALID_DATE);
        }

        // [LOC] Từ ngày phải <= Đến ngày (DBR-13), báo lỗi ở ô Đến ngày
        if (from != null && to != null && from.isAfter(to)) {
            errors.put("dateTo", Messages.MSG16_DATE_RANGE_REVERSED);
        }
        filter.setDateFrom(from);
        filter.setDateTo(to);

        // [LOC][LICH-SU] Tab trạng thái: chỉ 4 giá trị
        if (!ValidationUtil.isNullOrEmpty(historyStatus)) {
            if (isOneOf(historyStatus.trim(), "pending", "checkedIn", "missed", "cancelled")) {
                filter.setHistoryStatus(historyStatus.trim());
            } else {
                errors.put("historyStatus", Messages.MSG108_INVALID_OPTION);
            }
        }

        if (!errors.isEmpty()) {
            return ServiceResult.failFields(errors);
        }
        return ServiceResult.ok(filter);
    }

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
    public ServiceResult<MyPtSessionPage> getUpcomingPage(int accountId, MyPtSessionFilter filter,
            LocalDateTime now, int requestedPage) {
        try {
            Integer customerId = findCustomerId(accountId);
            if (customerId == null) {
                return ServiceResult.fail(Messages.MSG07_NO_PERMISSION);
            }

            // B1 [PHAN-TRANG]: lấy TẤT CẢ các ngày có buổi (chỉ lấy ngày → rất nhẹ)
            List<LocalDate> allDays = sessionDAO.findUpcomingDays(customerId, now, filter);

            // B2 [PHAN-TRANG]: tính trang, cắt tối đa 8 ngày của trang đang xem
            int totalPages = countPages(allDays.size());
            int currentPage = clampPage(requestedPage, totalPages);
            List<LocalDate> pageDays = cutDaysOfPage(allDays, currentPage);

            // B3: lấy các buổi của những ngày đó
            List<MyPtSessionRow> rows = new ArrayList<>();
            if (!pageDays.isEmpty()) {
                rows = sessionDAO.findUpcomingSessions(customerId, now, filter,
                        Collections.min(pageDays), Collections.max(pageDays));
            }

            // B4: điền thông tin hiển thị cho từng buổi
            for (MyPtSessionRow row : rows) {
                fillCommonInfo(row, customerId);

                // Đang diễn ra = đã tới giờ bắt đầu (bây giờ >= giờ bắt đầu)
                LocalDateTime startsAt = row.getSlotDate().atTime(row.getStartTime());
                row.setInProgress(!now.isBefore(startsAt));

                row.setWarning(buildGymWarning(row, customerId));   // [CANH-BAO-GYM]
                fillActionFlags(row);                                // [NUT]
            }

            // B5: gom buổi theo ngày → 1 trang
            MyPtSessionPage page = new MyPtSessionPage(groupByDay(rows), currentPage, totalPages, allDays.size());
            return ServiceResult.ok(page);

        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);   // lỗi DB ≠ "không có buổi"
        }
    }

    /**
     * [NUT] Bật / tắt nút trên từng buổi SẮP TỚI.
     * Nút trên giao diện chỉ để khách thấy; server vẫn kiểm tra lại khi bấm ([HUY-BUOI], [DOI-BUOI]).
     *
     *   Hủy buổi : gói còn Active (buổi ở khối Sắp tới = còn Scheduled, chưa kết thúc)
     *   Đổi buổi : không phải buổi lẻ (BR-74), chưa bắt đầu (BR-76), gói Active, còn lượt đổi (BR-73)
     *              Hết lượt → nút bị KHÓA với chữ "Hết lượt đổi" (SRS 9.2.4: "the form is locked")
     */
    private void fillActionFlags(MyPtSessionRow row) {
        boolean packageActive = "Active".equals(row.getSubscriptionStatus());

        // --- [HUY-BUOI] ---
        row.setCanCancel(packageActive);

        // --- [DOI-BUOI] ---
        row.setCanSwap(false);
        if (row.isSingleSession() || row.isInProgress()) {
            return;                                   // JSP không hiện nút Đổi
        }
        if (!packageActive) {
            row.setSwapNote("Gói đã hết hiệu lực");
        } else if (row.getSwapsUsed() >= row.getMaxSwaps()) {
            row.setSwapNote("Hết lượt đổi");
        } else {
            row.setCanSwap(true);
        }
    }


    // =====================================================================
    // [LICH-SU] LỊCH SỬ BUỔI TẬP (1 trang) — giống [SAP-TOI], chỉ khác hàm DAO và nhãn
    // =====================================================================

    /** [LICH-SU] CheckedIn / Missed / Cancelled + Scheduled đã kết thúc (chờ JOB-04). Mới nhất trước. */
    public ServiceResult<MyPtSessionPage> getHistoryPage(int accountId, MyPtSessionFilter filter,
            LocalDateTime now, int requestedPage) {
        try {
            Integer customerId = findCustomerId(accountId);
            if (customerId == null) {
                return ServiceResult.fail(Messages.MSG07_NO_PERMISSION);
            }

            // B1 [PHAN-TRANG]
            List<LocalDate> allDays = sessionDAO.findHistoryDays(customerId, now, filter);

            // B2 [PHAN-TRANG]
            int totalPages = countPages(allDays.size());
            int currentPage = clampPage(requestedPage, totalPages);
            List<LocalDate> pageDays = cutDaysOfPage(allDays, currentPage);

            // B3
            List<MyPtSessionRow> rows = new ArrayList<>();
            if (!pageDays.isEmpty()) {
                rows = sessionDAO.findHistorySessions(customerId, now, filter,
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
    // [HUY-BUOI] KHÁCH HỦY BUỔI (UC-39)
    // =====================================================================

    /**
     * [HUY-BUOI] Quy tắc:
     *   - LÝ DO KHÔNG BẮT BUỘC (SRS 9.2.1: hủy không có ô nhập); nếu có thì tối đa 500 ký tự.
     *     [CHOT 17/10] Khách tự hủy không cần giải thích — GBR-09 chỉ áp cho nhân viên hủy / từ chối.
     *   - Chỉ hủy buổi CỦA MÌNH (đứng tên / đi kèm gói đôi), còn Scheduled, gói còn Active.
     *   - Được hủy tới TRƯỚC GIỜ KẾT THÚC, kể cả khi đang diễn ra (notes 13/10).
     *   - Vẫn TRỪ BUỔI, KHÔNG hoàn tiền (BR-79).
     *   - Cập nhật buổi + ghi AuditLog + thông báo PT và người tập cùng trong 1 TRANSACTION (GBR-07, CBR-46).
     */
    public ServiceResult<Void> cancelPtSession(int accountId, int sessionId, String reason) {

        // B1 [HUY-BUOI]: lý do KHÔNG bắt buộc. Bỏ trống → lưu NULL. Có nhập thì tối đa 500 ký tự.
        String cleanReason = (reason == null || reason.isBlank()) ? null : reason.trim();
        if (cleanReason != null && cleanReason.length() > AppConstants.REASON_MAX_LENGTH) {
            return ServiceResult.fail(Messages.MSG10_MAX_LENGTH.formatted(AppConstants.REASON_MAX_LENGTH));
        }

        try {
            // B2 [QUYEN]: khách đang đăng nhập
            Integer customerId = findCustomerId(accountId);
            if (customerId == null) {
                return ServiceResult.fail(Messages.MSG07_NO_PERMISSION);
            }

            // B3 [QUYEN]: buổi có tồn tại và là của mình?
            PtSessionInfo info = sessionDAO.findSessionInfo(sessionId);
            if (info == null || !isMySession(info, customerId)) {
                return ServiceResult.fail(Messages.MSG07_NO_PERMISSION);   // không lộ "buổi có tồn tại"
            }

            // B4: chỉ hủy được buổi đang Scheduled
            if (!"Scheduled".equals(info.getSessionStatus())) {
                return ServiceResult.fail(Messages.MSG91_SESSION_CANNOT_CHANGE);
            }

            // B5: gói phải còn Active (gói hết hạn do JOB-02 hủy — CBR-55)
            if (!"Active".equals(info.getSubscriptionStatus())) {
                return ServiceResult.fail(Messages.MSG105_ACTION_OUTSIDE_WINDOW);
            }

            // B6: buổi chưa KẾT THÚC
            LocalDateTime endsAt = info.getSlotDate().atTime(info.getEndTime());
            if (!DateTimeUtil.now().isBefore(endsAt)) {
                return ServiceResult.fail(Messages.MSG91_SESSION_CANNOT_CHANGE);
            }

            // B7: ghi DB trong 1 transaction
            return cancelInTransaction(info, accountId, cleanReason);

        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /**
     * [HUY-BUOI] 3 việc ghi DB, ĐỦ CẢ 3 hoặc KHÔNG GÌ CẢ:
     *   1. UPDATE buổi → Cancelled
     *   2. INSERT AuditLog (kèm lý do nếu khách có nhập)
     *   3. INSERT thông báo cho khách, người tập cùng, PT
     */
    private ServiceResult<Void> cancelInTransaction(PtSessionInfo info, int accountId, String reason)
            throws SQLException {

        try (Connection connection = DBContext.getConnection()) {
            connection.setAutoCommit(false);

            try {
                // 1. UPDATE. Không được dòng nào = có người vừa đổi trạng thái trước mình
                if (!sessionDAO.cancelSession(connection, info.getSessionId(), accountId, reason)) {
                    connection.rollback();
                    return ServiceResult.fail(Messages.MSG22_CONCURRENT_EDIT);
                }

                // 2. [AUDIT]
                sessionDAO.insertCancelAuditLog(connection, accountId, info.getSessionId(), reason);

                // 3. [THONG-BAO]
                notifyCancel(connection, info);

                connection.commit();
                return ServiceResult.ok(null, Messages.MSG110_SESSION_CANCELLED);

            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        }
    }

    /** [THONG-BAO][HUY-BUOI] Báo ngay cho PT (để không phải chờ) và các thành viên của gói (CBR-46). */
    private void notifyCancel(Connection connection, PtSessionInfo info) throws SQLException {
        String content = "Buổi PT " + DateTimeUtil.formatDate(info.getSlotDate()) + " "
                + DateTimeUtil.formatTime(info.getStartTime()) + "–" + DateTimeUtil.formatTime(info.getEndTime())
                + " với PT " + info.getPtName() + " đã được hủy theo yêu cầu của khách."
                + " Buổi vẫn được tính vào gói và không hoàn tiền.";

        int notificationId = notificationDAO.insertSystemNotification(connection, "Buổi PT đã bị hủy", content);

        List<Integer> recipients = sessionDAO.findRecipientAccounts(connection,
                info.getCustomerId(), info.getPartnerCustomerId(), info.getPtEmployeeId());
        for (int recipientAccountId : recipients) {
            notificationDAO.insertRecipient(connection, notificationId, recipientAccountId);
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

    /** [LICH-SU] Vì sao bị hủy (Sessions.CancelType). */
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

    private boolean isOneOf(String value, String... allowed) {
        for (String item : allowed) {
            if (item.equals(value)) {
                return true;
            }
        }
        return false;
    }

    /** [LOC][QUYEN] Mã gói có thuộc danh sách gói của khách không? (null = chưa tải được → bỏ qua) */
    private boolean isMyPackage(int subscriptionId, List<MyPtPackageView> myPackages) {
        if (myPackages == null) {
            return true;
        }
        for (MyPtPackageView p : myPackages) {
            if (p.getSubscriptionId() == subscriptionId) {
                return true;
            }
        }
        return false;
    }
}
