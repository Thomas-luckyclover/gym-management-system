package com.gms.service;

import com.gms.constant.Messages;
import com.gms.dao.NotificationDAO;
import com.gms.dao.PtSaleDAO;
import com.gms.dao.PtSaleDAO.HeldOrder;
import com.gms.dao.PtSaleDAO.PackageRow;
import com.gms.dao.PtSaleDAO.PtRow;
import com.gms.dao.PtScheduleDAO;
import com.gms.dto.HourException;
import com.gms.dto.LeaveInfo;
import com.gms.dto.PtSaleCustomer;
import com.gms.dto.PtSaleDay;
import com.gms.dto.PtSalePlan;
import com.gms.dto.PtSaleOption;
import com.gms.dto.PtSaleView;
import com.gms.dto.PtScheduleItem;
import com.gms.dto.ServiceResult;
import com.gms.util.DBContext;
import com.gms.util.DateTimeUtil;
import com.gms.util.JsonUtil;
import com.gms.util.TimeRange;
import java.sql.Connection;
import java.sql.SQLException;
import java.text.NumberFormat;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * SERVICE MÀN LỄ TÂN BÁN GÓI PT (/receptionist/pt-sales) — UC-21 bản đơn giản hóa (docs/notes.md 25/10/2026).
 *
 * Luồng giống mua vé có giữ chỗ:
 *   search   ô tìm kiếm gợi ý (AJAX): tên không dấu / SĐT / mã KH
 *   prepare  mỗi lần đổi lựa chọn (GET): Khách → Gói, PT, ngày bắt đầu → dữ liệu Lịch tập (JSON, trình duyệt ghép lịch)
 *   hold     "Giữ lịch 15 phút": tạo đơn Pending + khung Held (PT và lễ tân khác thấy "Đang giữ")
 *   pay      "Xác nhận đã thu tiền": đơn Paid, giao dịch, gói PT, các buổi (Held → Booked)
 *   release  "Hủy giữ chỗ": đơn Cancelled, khung về như cũ
 *   Giữ quá 15 phút chưa thu tiền → tự nhả ở lần mở màn kế tiếp (releaseExpired).
 *
 * MỤC LỤC
 *   PHẦN 1. HÀM CHÍNH       search, prepare, hold, pay, release
 *   PHẦN 2. 4 BƯỚC          build → loadCustomers, loadPackage, loadPt, loadPlan · loadHeld
 *   PHẦN 3. LUẬT (static)   whyCannotBuy, candidateTimes, checkDay, buildPlan, checkRequested, sessionValue, …
 */
public class PtSaleService {

    static final int START_DAYS_AHEAD = 30;                  // ngày bắt đầu: hôm nay → +30 ngày
    static final int TIME_WINDOW_DAYS = 14;                  // giờ trên bảng tuần: khung PT công bố trong 14 ngày đầu kỳ
    static final int HOLD_MINUTES = 15;                      // giữ chỗ bao lâu
    static final LocalTime AFTERNOON_FROM = LocalTime.of(14, 0);
    static final String EXPIRED_REASON = "Hết 15 phút giữ chỗ, chưa thu tiền";
    static final String RELEASE_REASON = "Lễ tân hủy giữ chỗ, chưa thu tiền";

    private final PtSaleDAO saleDAO = new PtSaleDAO();
    private final PtScheduleDAO scheduleDAO = new PtScheduleDAO();
    private final NotificationDAO notificationDAO = new NotificationDAO();
    private final SystemConfigService configService = new SystemConfigService();

    /** Lựa chọn trên URL / form (chữ thô, Service tự đọc + kiểm tra). */
    public static class Input {
        public String customer, partner, packageId, pt, start, query;
        public String[] times = new String[5];   // t1..t5 = lịch mẫu Thứ 2..Thứ 6 (trống = Nghỉ)
        public boolean confirmPt;                // ô tích "Đã xác nhận với PT" (bắt buộc khi có buổi PT chưa mở khung)
        public String sessions;                  // "2026-10-27@09:30,…" — các buổi đã chọn (chỉ khi Giữ lịch)
    }


    // ================================================================================================================
    // PHẦN 1. HÀM CHÍNH
    // ================================================================================================================

    /** HÀM CHÍNH — PtSaleServlet (GET …/pt-sales/customers?q=) gọi khi Lễ tân gõ. Dưới 2 ký tự → danh sách rỗng. */
    public ServiceResult<List<PtSaleCustomer>> search(int accountId, String query, LocalDateTime now) {
        String text = query == null ? "" : query.trim();
        if (text.length() < 2) {
            return ServiceResult.ok(new ArrayList<>());
        }
        try (Connection connection = DBContext.getConnection()) {
            int[] receptionist = saleDAO.findReceptionist(connection, accountId);
            if (receptionist == null) {
                return ServiceResult.fail(Messages.MSG07_NO_PERMISSION);
            }
            String digits = text.replaceAll("\\D", "");
            List<PtSaleCustomer> rows = saleDAO.searchCustomers(connection, text, digits, now.toLocalDate());
            int minAge = configService.getInt(SystemConfigService.MIN_AGE_FOR_PT);
            for (PtSaleCustomer row : rows) {
                row.setBlockReason(whyCannotBuy(row, now.toLocalDate(), minAge, receptionist[1]));
            }
            return ServiceResult.ok(rows);
        } catch (SQLException exception) {
            exception.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /**
     * HÀM CHÍNH — PtSaleServlet.doGet gọi. orderId != null → đang giữ chỗ (bước 4), đọc từ đơn.
     * Trước khi đọc: nhả mọi lần giữ đã quá 15 phút (để bảng tuần thấy đúng khung trống).
     */
    public ServiceResult<PtSaleView> prepare(int accountId, Input input, Integer orderId, LocalDateTime now) {
        try {
            releaseExpired(now);
            try (Connection connection = DBContext.getConnection()) {
                int[] receptionist = saleDAO.findReceptionist(connection, accountId);
                if (receptionist == null) {
                    return ServiceResult.fail(Messages.MSG07_NO_PERMISSION);
                }
                PtSaleView view = newView(input, now);
                if (orderId != null) {
                    String problem = loadHeld(connection, view, orderId, now);
                    return problem == null ? ServiceResult.ok(view) : ServiceResult.fail(problem);
                }
                build(connection, view, input, receptionist[1], now, false);
                return ServiceResult.ok(view);
            }
        } catch (SQLException | IllegalStateException | DateTimeException exception) {
            exception.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /**
     * HÀM CHÍNH — "Giữ lịch 15 phút". Làm lại 4 bước có khóa dòng PT + khung, rồi trong 1 transaction:
     * ca (tự mở) → mã đơn → đơn Pending → mỗi ngày có buổi: khung Free → Held, hoặc tạo khung Held mới.
     * Ra: mã đơn (Servlet chuyển sang ?order=…) ; lỗi → câu lỗi, không ghi gì.
     */
    public ServiceResult<Integer> hold(int accountId, Input input, LocalDateTime now) {
        try {
            releaseExpired(now);
        } catch (SQLException exception) {
            exception.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
        return inTransaction(connection -> {
            int[] receptionist = requireReceptionist(connection, accountId);
            PtSaleView view = newView(input, now);
            if (view.getPtId() != null) {
                saleDAO.lockPt(connection, view.getPtId());      // xếp hàng với lễ tân khác / PT đang Công bố
            }
            build(connection, view, input, receptionist[1], now, true);
            String problem = firstProblem(view);
            if (problem != null) {
                throw new IllegalArgumentException(problem);
            }
            // Buổi PT chưa mở khung (giữ lịch sẽ tự tạo khung mới) → lễ tân phải hỏi PT rồi tích xác nhận.
            int unconfirmed = countNewSlots(view.getDays());
            if (unconfirmed > 0 && !input.confirmPt) {
                throw new IllegalArgumentException("Có " + unconfirmed + " buổi PT chưa mở khung giờ đó. "
                        + "Hỏi PT rồi tích \"Đã xác nhận với PT\" trước khi giữ lịch.");
            }

            // Ca hiện tại (đơn tại quầy bắt buộc thuộc 1 ca — tự mở nếu chưa mở).
            int shiftId = currentShift(connection, receptionist[0], now);
            PackageRow pkg = saleDAO.findPackage(connection, view.getPackageId());
            int vatRate = configService.getInt(SystemConfigService.VAT_PERCENT);
            String code = saleDAO.nextOrderCode(connection, now.getYear());
            Integer partnerId = view.getPartner() == null ? null : view.getPartner().getCustomerId();
            int orderId = saleDAO.insertPendingOrder(connection, code, view.getHolder().getCustomerId(), partnerId, pkg,
                    receptionist[0], shiftId, vatRate, vatAmount(pkg.price, vatRate), now);

            LocalDateTime expires = now.plusMinutes(HOLD_MINUTES);
            for (PtSaleDay day : view.getDays()) {
                if (!day.isSession()) {
                    continue;
                }
                if ("book".equals(day.getAction())) {
                    if (!saleDAO.holdFreeSlot(connection, day.getSlotId(), view.getPtId(), orderId, expires)) {
                        throw new IllegalArgumentException(day.getDayLabel() + ": " + Messages.MSG103_SLOT_ALREADY_TAKEN);
                    }
                } else {
                    saleDAO.insertHeldSlot(connection, view.getPtId(), day.getDate(), day.getStartTime(), day.getEndTime(),
                            orderId, expires, now);
                }
            }
            return ServiceResult.ok(orderId, "Đã giữ " + view.getSessionCount() + " buổi trong " + HOLD_MINUTES
                    + " phút · đơn " + code + ". Thu tiền để chốt.");
        });
    }

    /**
     * HÀM CHÍNH — "Xác nhận đã thu tiền" của đơn đang giữ.
     * startText = ngày bắt đầu kỳ (form mang theo). Đơn phải còn Pending, khung còn giữ và chưa quá hạn.
     */
    public ServiceResult<Integer> pay(int accountId, int orderId, String startText, String method, String reference,
            LocalDateTime now) {
        if (!"Cash".equals(method) && !"Transfer".equals(method)) {
            return ServiceResult.fail("Chọn cách thu tiền: tiền mặt hoặc chuyển khoản.");
        }
        String ref = reference == null || reference.isBlank() || "Cash".equals(method) ? null : reference.trim();
        if (ref != null && ref.length() > 50) {
            return ServiceResult.fail("Mã giao dịch chuyển khoản tối đa 50 ký tự.");
        }
        LocalDate start = parseDate(startText);

        return inTransaction(connection -> {
            int[] receptionist = requireReceptionist(connection, accountId);
            HeldOrder held = saleDAO.findHeldOrder(connection, orderId, true);
            String problem = whyCannotPay(held, start, now);
            if (problem != null) {
                throw new IllegalArgumentException(problem);
            }

            // B1: đơn Paid + giao dịch Success (người nhận = lễ tân đang bấm).
            PackageRow pkg = saleDAO.findPackage(connection, held.packageId);
            if (pkg == null) {
                throw new IllegalArgumentException("Gói này không còn bán. Hủy giữ chỗ rồi chọn gói khác.");
            }
            if (!saleDAO.markOrderPaid(connection, orderId, now)) {
                throw new IllegalArgumentException("Đơn này không còn chờ thu tiền.");
            }
            saleDAO.insertTransaction(connection, orderId, held.price, method, ref, receptionist[0], now);

            // B2: gói PT. Số buổi = số khung đang giữ; giá trị buổi = giá ÷ số buổi (DBR-59).
            LocalDate end = DateTimeUtil.calculateEndDateByMonths(start, pkg.months);
            int count = held.slots.size();
            int maxSwaps = configService.getInt(SystemConfigService.MAX_SWAP_COUNT) * pkg.months;
            int subscriptionId = saleDAO.insertSubscription(connection, orderId, held.holderId, pkg.type, held.ptId,
                    start, end, mostCommonStart(held.slots), count, maxSwaps);

            // B3: từng buổi: khung Held → Booked + buổi Scheduled.
            PtRow pt = saleDAO.findPt(connection, held.ptId);
            int rate = pt == null ? 0 : pt.commissionRate;
            long value = sessionValue(held.price, count);
            long commission = Math.round(value * rate / 100.0);
            for (PtScheduleItem slot : held.slots) {
                if (!saleDAO.bookHeldSlot(connection, slot.getTimeSlotId(), orderId)) {
                    throw new IllegalArgumentException(Messages.MSG103_SLOT_ALREADY_TAKEN);
                }
                saleDAO.insertSession(connection, subscriptionId, slot.getTimeSlotId(), held.ptId, value, commission);
            }

            // B4: thông báo khách (+ người tập cùng) và PT.
            notifySale(connection, held, pkg, pt == null ? "PT" : pt.fullName, start, end, count, now);
            return ServiceResult.ok(orderId, "Đã thu " + money(held.price) + " · đơn " + held.orderCode + " · "
                    + count + " buổi đã vào lịch PT.");
        });
    }

    /** HÀM CHÍNH — "Hủy giữ chỗ": đơn Cancelled (lễ tân hủy), khung về như trước khi giữ. */
    public ServiceResult<Integer> release(int accountId, int orderId, LocalDateTime now) {
        return inTransaction(connection -> {
            requireReceptionist(connection, accountId);
            HeldOrder held = saleDAO.findHeldOrder(connection, orderId, true);
            if (held == null || !"Pending".equals(held.status)) {
                throw new IllegalArgumentException("Đơn này không còn giữ chỗ.");
            }
            saleDAO.releaseHold(connection, orderId, held.createdAt, RELEASE_REASON, now);
            return ServiceResult.ok(orderId, "Đã hủy giữ chỗ đơn " + held.orderCode + ", các khung đã trả lại cho PT.");
        });
    }

    /** Nhả mọi đơn giữ chỗ đã quá hạn (mỗi đơn 1 transaction nhỏ). */
    void releaseExpired(LocalDateTime now) throws SQLException {
        List<Integer> expired;
        try (Connection connection = DBContext.getConnection()) {
            expired = saleDAO.findExpiredHoldOrders(connection, now);
        }
        for (int orderId : expired) {
            inTransaction(connection -> {
                HeldOrder held = saleDAO.findHeldOrder(connection, orderId, true);
                if (held != null && "Pending".equals(held.status)) {
                    saleDAO.releaseHold(connection, orderId, held.createdAt, EXPIRED_REASON, now);
                }
                return ServiceResult.ok(orderId);
            });
        }
    }

    @FunctionalInterface
    interface Work {
        ServiceResult<Integer> run(Connection connection) throws SQLException;
    }

    /** Chạy work trong 1 transaction; IllegalArgumentException = từ chối có lý do (rollback, trả câu lỗi). */
    private ServiceResult<Integer> inTransaction(Work work) {
        try (Connection connection = DBContext.getConnection()) {
            connection.setAutoCommit(false);
            try {
                ServiceResult<Integer> result = work.run(connection);
                connection.commit();
                return result;
            } catch (IllegalArgumentException exception) {
                connection.rollback();
                return ServiceResult.fail(exception.getMessage());
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException | IllegalStateException | DateTimeException exception) {
            exception.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    private int[] requireReceptionist(Connection connection, int accountId) throws SQLException {
        int[] receptionist = saleDAO.findReceptionist(connection, accountId);
        if (receptionist == null) {
            throw new IllegalArgumentException(Messages.MSG07_NO_PERMISSION);
        }
        return receptionist;
    }

    /** Ca hiện tại theo giờ (trước 14:00 = sáng). Chưa có / chưa mở → mở bằng lễ tân này; đã chốt → từ chối. */
    private int currentShift(Connection connection, int receptionistId, LocalDateTime now) throws SQLException {
        String type = shiftTypeAt(now.toLocalTime());
        Integer shiftId = saleDAO.findOrOpenShift(connection, now.toLocalDate(), type, receptionistId, now);
        if (shiftId == null) {
            throw new IllegalArgumentException("Ca " + ("Morning".equals(type) ? "sáng" : "chiều")
                    + " hôm nay đã chốt két, không ghi thêm đơn vào ca này được.");
        }
        return shiftId;
    }

    /** B4 của pay: 1 thông báo cho khách (+ người tập cùng), 1 cho PT. Hồ sơ không có tài khoản → bỏ qua. */
    private void notifySale(Connection connection, HeldOrder held, PackageRow pkg, String ptName, LocalDate start,
            LocalDate end, int count, LocalDateTime now) throws SQLException {
        String term = DateTimeUtil.formatDate(start) + " – " + DateTimeUtil.formatDate(end);
        int partnerId = held.partnerId == null ? held.holderId : held.partnerId;
        notify(connection, saleDAO.findCustomerAccounts(connection, held.holderId, partnerId), "Gói PT mới đã kích hoạt",
                pkg.name + " với " + ptName + ": " + count + " buổi, " + term + ". Mã đơn " + held.orderCode + ".");
        Integer ptAccount = notificationDAO.findActiveAccountIdByEmployeeId(connection, held.ptId);
        if (ptAccount != null) {
            PtSaleCustomer holder = saleDAO.findCustomer(connection, held.holderId, now.toLocalDate());
            notify(connection, List.of(ptAccount), "Bạn có khách gói PT mới",
                    (holder == null ? "Khách" : holder.getFullName()) + " mua " + pkg.name + ": " + count
                            + " buổi, " + term + ". Xem trên Lịch dạy.");
        }
    }

    private void notify(Connection connection, List<Integer> accountIds, String title, String content) throws SQLException {
        if (accountIds.isEmpty()) {
            return;
        }
        String text = content.length() > 500 ? content.substring(0, 497) + "..." : content;
        int notificationId = notificationDAO.insertSystemNotification(connection, title, text);
        for (int accountId : accountIds) {
            notificationDAO.insertRecipient(connection, notificationId, accountId);
        }
    }


    // ================================================================================================================
    // PHẦN 2. 4 BƯỚC — prepare (xem) và hold (giữ) dùng CHUNG nên kiểm tra đúng một luật
    // ================================================================================================================

    private PtSaleView newView(Input input, LocalDateTime now) {
        PtSaleView view = new PtSaleView();
        view.setCustomerId(parseId(input.customer));
        view.setPartnerId(parseId(input.partner));
        view.setPackageId(parseId(input.packageId));
        view.setPtId(parseId(input.pt));
        LocalDate start = parseDate(input.start);
        view.setStartDate(start != null ? start : now.toLocalDate().plusDays(1));   // chưa chọn → mặc định ngày mai
        LocalTime[] times = new LocalTime[5];
        for (int i = 0; i < 5; i++) {
            times[i] = parseTime(input.times[i]);
        }
        view.setWeekTimes(times);
        view.setSearchQuery(input.query == null || input.query.isBlank() ? null : input.query.trim());
        view.setMinStart(now.toLocalDate());
        view.setMaxStart(now.toLocalDate().plusDays(START_DAYS_AHEAD));
        return view;
    }

    /** Đi lần lượt 4 bước; bước nào thiếu / sai thì dừng (bước sau để trống). lock = true khi giữ chỗ. */
    private void build(Connection connection, PtSaleView view, Input input, int receptionistPersonId,
            LocalDateTime now, boolean lock) throws SQLException {
        LocalDate today = now.toLocalDate();
        int minAge = configService.getInt(SystemConfigService.MIN_AGE_FOR_PT);

        // Lễ tân TƯ VẤN TRƯỚC: gói, PT, ngày bắt đầu, lịch tập chọn được khi chưa biết khách (cô góp ý 26/10).
        // Khách (+ người tập cùng của gói đôi) chọn lúc nào cũng được ở cột "Đơn bán"; có khách thì lịch
        // tính thêm giờ trùng buổi khác của khách. Giữ lịch bắt buộc đủ cả hai (firstProblem).
        loadHolder(connection, view, today, minAge, receptionistPersonId);
        boolean packageOk = loadPackage(connection, view);
        if (packageOk && view.isCouple()) {
            loadPartner(connection, view, today, minAge, receptionistPersonId);
        }
        if (!packageOk || !loadPt(connection, view)) {
            return;
        }
        loadPlan(connection, view, input, now, lock);
    }

    /** Người đứng tên: đã chọn (?customer=) → hồ sơ + lý do không bán được; chưa chọn mà có chữ tìm (tắt JS) → danh sách. */
    private void loadHolder(Connection connection, PtSaleView view, LocalDate today, int minAge, int receptionistPersonId)
            throws SQLException {
        if (view.getCustomerId() == null) {
            if (view.getSearchQuery() != null) {
                String digits = view.getSearchQuery().replaceAll("\\D", "");
                view.setSearchResults(saleDAO.searchCustomers(connection, view.getSearchQuery(), digits, today));
                for (PtSaleCustomer row : view.getSearchResults()) {
                    row.setBlockReason(whyCannotBuy(row, today, minAge, receptionistPersonId));
                }
                if (view.getSearchResults().isEmpty()) {
                    view.setHolderError("Không tìm thấy khách nào khớp \"" + view.getSearchQuery() + "\".");
                }
            }
            return;
        }
        PtSaleCustomer holder = saleDAO.findCustomer(connection, view.getCustomerId(), today);
        if (holder == null) {
            view.setHolderError("Không tìm thấy khách này. Tìm lại.");
            return;
        }
        holder.setBlockReason(whyCannotBuy(holder, today, minAge, receptionistPersonId));
        view.setHolder(holder);
    }

    private boolean loadPackage(Connection connection, PtSaleView view) throws SQLException {
        List<PtSaleOption> options = new ArrayList<>();
        for (PackageRow row : saleDAO.findPtPackages(connection)) {
            String kind = "PTCouple".equals(row.type) ? "Gói đôi" : "1 người";
            options.add(new PtSaleOption(String.valueOf(row.packageId), row.name,
                    money(row.price) + " · " + row.months + " tháng · " + kind));
        }
        view.setPackages(options);
        if (view.getPackageId() == null) {
            return false;
        }
        PackageRow pkg = saleDAO.findPackage(connection, view.getPackageId());
        if (pkg == null) {
            view.setChoiceError("Gói này không còn bán. Chọn gói khác.");
            return false;
        }
        view.setPackageName(pkg.name);
        view.setPrice(pkg.price);
        view.setMonths(pkg.months);
        view.setCouple("PTCouple".equals(pkg.type));
        return true;
    }

    private boolean loadPartner(Connection connection, PtSaleView view, LocalDate today, int minAge,
            int receptionistPersonId) throws SQLException {
        if (view.getPartnerId() == null) {
            view.setPartnerError("Gói đôi: tìm và chọn người tập cùng.");
            return false;
        }
        PtSaleCustomer partner = saleDAO.findCustomer(connection, view.getPartnerId(), today);
        boolean sameAsHolder = partner != null && view.getHolder() != null
                && partner.getCustomerId() == view.getHolder().getCustomerId();
        if (partner == null || sameAsHolder) {
            view.setPartnerError("Người tập cùng phải là một khách khác.");
            return false;
        }
        partner.setBlockReason(whyCannotBuy(partner, today, minAge, receptionistPersonId));
        view.setPartner(partner);
        return partner.getBlockReason() == null;
    }

    private boolean loadPt(Connection connection, PtSaleView view) throws SQLException {
        List<PtSaleOption> options = new ArrayList<>();
        for (PtRow row : saleDAO.findWorkingPts(connection)) {
            if (!isCustomerPerson(view, row.personId)) {
                options.add(new PtSaleOption(String.valueOf(row.employeeId), row.fullName, initials(row.fullName)));
            }
        }
        view.setPts(options);
        if (view.getPtId() == null || view.getStartDate() == null) {
            return false;
        }
        PtRow pt = saleDAO.findPt(connection, view.getPtId());
        if (pt == null || isCustomerPerson(view, pt.personId)) {
            view.setChoiceError("PT này không nhận được gói. Chọn PT khác.");
            return false;
        }
        boolean outOfRange = view.getStartDate().isBefore(view.getMinStart()) || view.getStartDate().isAfter(view.getMaxStart());
        if (outOfRange) {
            view.setChoiceError("Ngày bắt đầu phải từ " + DateTimeUtil.formatDate(view.getMinStart()) + " đến "
                    + DateTimeUtil.formatDate(view.getMaxStart()) + ".");
            return false;
        }
        view.setPtName(pt.fullName);
        view.setCommissionRate(pt.commissionRate);
        view.setEndDate(DateTimeUtil.calculateEndDateByMonths(view.getStartDate(), view.getMonths()));
        return true;
    }

    /**
     * Bước 3. Xem (prepare): dựng PtSalePlan → JSON cho trình duyệt (pt-sale-plan.js).
     * Giữ (hold, lock = true): kiểm tra lại các buổi lễ tân gửi lên (input.sessions) → view.days.
     */
    private void loadPlan(Connection connection, PtSaleView view, Input input, LocalDateTime now, boolean lock)
            throws SQLException {
        int minutes = configService.getInt(SystemConfigService.SESSION_DURATION_MINUTES);
        LocalDate start = view.getStartDate();
        LocalDate end = view.getEndDate();
        List<PtScheduleItem> slots = saleDAO.findPtSlots(connection, view.getPtId(), start, end, lock);
        // Giờ bận của khách chỉ tính khi đã chọn khách (tư vấn trước → chưa có khách → chỉ xét lịch PT).
        List<TimeRange> busy = new ArrayList<>();
        PtSaleCustomer holder = view.getHolder();
        if (holder != null) {
            int partnerId = view.getPartner() == null ? holder.getCustomerId() : view.getPartner().getCustomerId();
            busy = saleDAO.findCustomerBusy(connection, holder.getCustomerId(), partnerId, start, end);
        }
        List<LeaveInfo> leaves = scheduleDAO.findLeaves(connection, view.getPtId(), start, end);
        List<HourException> exceptions = scheduleDAO.findHourExceptions(connection, start, end);
        List<LocalTime> candidates = candidateTimes(slots, start, minutes, now);
        Map<LocalDate, LocalTime> requested = parseSessions(input.sessions);
        // Thứ PT chưa công bố khung nào → hộp nhắc vàng trên JSP.
        List<Integer> unpublished = unpublishedWeekdays(slots, start, now);
        view.setUnpublishedWeekdays(unpublished);

        if (!lock) {
            if (candidates.isEmpty()) {
                view.setPlanError(view.getPtName() + " chưa công bố khung trống nào vào T2–T6 trong 2 tuần đầu kỳ. "
                        + "Nhờ PT công bố trên Lịch dạy, hoặc đổi ngày bắt đầu / PT.");
                return;
            }
            PtSalePlan plan = buildPlan(start, end, minutes, view.getPrice(), candidates, slots, leaves, exceptions, busy, now);
            plan.setUnpublished(unpublished);
            for (int i = 0; i < 5; i++) {
                LocalTime time = view.getWeekTimes()[i];
                plan.getPattern()[i] = time != null && candidates.contains(time) ? DateTimeUtil.formatTime(time) : null;
            }
            if (requested != null) {
                requested.forEach((date, time) -> plan.getRequested().add(date + "@" + DateTimeUtil.formatTime(time)));
            }
            view.setPlanJson(JsonUtil.toJson(plan));
            return;
        }

        if (requested == null) {
            view.setPlanError("Không đọc được lịch vừa chọn. Chọn lại lịch rồi bấm Giữ lịch.");
            return;
        }
        List<PtSaleDay> days;
        try {
            days = checkRequested(requested, start, end, minutes, candidates, slots, leaves, exceptions, busy, now);
        } catch (IllegalArgumentException problem) {
            view.setPlanError(problem.getMessage());
            return;
        }
        if (days.size() > 255) {
            throw new IllegalStateException("Số buổi vượt giới hạn TINYINT");
        }
        view.setDays(days);
        view.setSessionCount(days.size());
        view.setSessionValue(sessionValue(view.getPrice(), days.size()));
    }

    /** Bước 4: dựng màn từ đơn đang giữ (không cần URL đủ 6 lựa chọn). Đơn hết giữ → câu lỗi. */
    private String loadHeld(Connection connection, PtSaleView view, int orderId, LocalDateTime now) throws SQLException {
        HeldOrder held = saleDAO.findHeldOrder(connection, orderId, false);
        if (held == null || !"Pending".equals(held.status) || held.slots.isEmpty() || held.expiresAt == null) {
            return "Đơn này không còn giữ chỗ (đã thu tiền, đã hủy hoặc quá " + HOLD_MINUTES + " phút). Bán lại từ đầu.";
        }
        LocalDate today = now.toLocalDate();
        PackageRow pkg = saleDAO.findPackage(connection, held.packageId);
        PtRow pt = saleDAO.findPt(connection, held.ptId);
        view.setHeldOrderId(orderId);
        view.setHeldCode(held.orderCode);
        view.setHoldSeconds(Math.max(0, Duration.between(now, held.expiresAt).getSeconds()));
        view.setHolder(saleDAO.findCustomer(connection, held.holderId, today));
        view.setCustomerId(held.holderId);
        if (held.partnerId != null) {
            view.setPartner(saleDAO.findCustomer(connection, held.partnerId, today));
        }
        view.setPackageId(held.packageId);
        view.setPackageName(pkg == null ? "Gói PT" : pkg.name);
        view.setMonths(pkg == null ? 0 : pkg.months);
        view.setPrice(held.price);
        view.setPtId(held.ptId);
        view.setPtName(pt == null ? "PT" : pt.fullName);
        if (view.getStartDate() == null) {
            view.setStartDate(held.slots.get(0).getSlotDate());
        }
        view.setEndDate(DateTimeUtil.calculateEndDateByMonths(view.getStartDate(), view.getMonths()));

        List<PtSaleDay> days = new ArrayList<>();
        LocalTime[] times = new LocalTime[5];
        for (PtScheduleItem slot : held.slots) {
            days.add(new PtSaleDay(slot.getSlotDate(), PtSessionHandleService.dayLabel(slot.getSlotDate()),
                    slot.getStartTime(), slot.getEndTime(), "book", slot.getTimeSlotId(), null, null));
            int index = slot.getSlotDate().getDayOfWeek().getValue() - 1;
            if (index < 5 && times[index] == null) {
                times[index] = slot.getStartTime();
            }
        }
        view.setWeekTimes(times);
        view.setDays(days);
        view.setSessionCount(days.size());
        view.setSessionValue(sessionValue(held.price, days.size()));
        return null;
    }

    private boolean isCustomerPerson(PtSaleView view, int personId) {
        boolean holder = view.getHolder() != null && view.getHolder().getPersonId() == personId;
        boolean partner = view.getPartner() != null && view.getPartner().getPersonId() == personId;
        return holder || partner;
    }

    /** Câu lỗi đầu tiên chặn việc giữ chỗ (theo thứ tự bước); null = giữ được. */
    static String firstProblem(PtSaleView view) {
        if (view.getHolder() == null) {
            return view.getHolderError() != null ? view.getHolderError() : "Chọn khách trước.";
        }
        if (view.getHolder().getBlockReason() != null) {
            return view.getHolder().getBlockReason();
        }
        if (view.getPartnerError() != null) {
            return view.getPartnerError();
        }
        if (view.getPartner() != null && view.getPartner().getBlockReason() != null) {
            return view.getPartner().getBlockReason();
        }
        if (view.getChoiceError() != null) {
            return view.getChoiceError();
        }
        if (view.getEndDate() == null) {
            return "Chọn đủ gói, PT và ngày bắt đầu.";
        }
        if (view.getPlanError() != null) {
            return view.getPlanError();
        }
        return view.isReady() ? null : "Chọn ít nhất 1 buổi tập.";
    }


    // ================================================================================================================
    // PHẦN 3. LUẬT — static, không đọc DB (PtSaleServiceTest)
    // ================================================================================================================

    /**
     * Khách có mua được gói PT không? null = được.
     *   không phải hồ sơ khách của chính lễ tân · đủ tuổi (P-05) · có gói thành viên phủ hôm nay (vé ngày không tính)
     *   · hôm nay không bảo lưu (CBR-59, BR-37).
     */
    static String whyCannotBuy(PtSaleCustomer customer, LocalDate today, int minAge, int receptionistPersonId) {
        if (customer.getPersonId() == receptionistPersonId) {
            return "Lễ tân không tự bán gói cho hồ sơ khách của chính mình.";
        }
        if (DateTimeUtil.calculateAge(customer.getDateOfBirth(), today) < minAge) {
            return "Chưa đủ " + minAge + " tuổi để mua gói PT.";
        }
        if (customer.getMembershipEnd() == null) {
            return "Chưa có gói thành viên còn hạn hôm nay. Bán gói thành viên trước.";
        }
        if (customer.isFrozen()) {
            return "Đang bảo lưu gói thành viên.";
        }
        return null;
    }

    /** Lý do không thu tiền được đơn đang giữ; null = thu được. */
    static String whyCannotPay(HeldOrder held, LocalDate start, LocalDateTime now) {
        if (held == null || !"Pending".equals(held.status)) {
            return "Đơn này không còn chờ thu tiền.";
        }
        if (held.slots.isEmpty() || held.expiresAt == null || !held.expiresAt.isAfter(now)) {
            return "Đã quá " + HOLD_MINUTES + " phút giữ chỗ. Giữ lại lịch rồi thu tiền.";
        }
        LocalDate first = held.slots.get(0).getSlotDate();
        if (start == null || start.isAfter(first) || start.plusDays(TIME_WINDOW_DAYS).isBefore(first)) {
            return "Ngày bắt đầu không khớp lịch đang giữ. Hủy giữ chỗ rồi làm lại.";
        }
        return null;
    }

    /**
     * Giờ của "lịch mẫu" = giờ bắt đầu của các khung Free (đúng P-06 phút, chưa qua giờ) PT đã công bố
     * vào T2–T6 trong 14 ngày đầu kỳ (CBR-52: lịch phải nằm trong giờ PT đã công bố). Xếp tăng dần.
     */
    static List<LocalTime> candidateTimes(List<PtScheduleItem> slots, LocalDate start, int minutes, LocalDateTime now) {
        LocalDate windowEnd = start.plusDays(TIME_WINDOW_DAYS - 1);
        TreeMap<LocalTime, Boolean> times = new TreeMap<>();
        for (PtScheduleItem slot : slots) {
            boolean inWindow = !slot.getSlotDate().isAfter(windowEnd) && isWeekday(slot.getSlotDate());
            boolean free = "Free".equals(slot.getKind());
            boolean rightLength = slot.getStartTime().plusMinutes(minutes).equals(slot.getEndTime());
            boolean future = slot.getSlotDate().atTime(slot.getStartTime()).isAfter(now);
            if (inWindow && free && rightLength && future) {
                times.put(slot.getStartTime(), true);
            }
        }
        return new ArrayList<>(times.keySet());
    }

    /**
     * Thứ (1 = Thứ 2 … 5 = Thứ 6) mà PT CHƯA công bố khung Free nào (chưa qua giờ) trong 14 ngày đầu kỳ.
     * Ví dụ PT chỉ mở Thứ 2, 4, 6 → [2, 4]. Chỉ để in hộp nhắc; việc bắt tích xác nhận xét theo TỪNG BUỔI (countNewSlots).
     */
    static List<Integer> unpublishedWeekdays(List<PtScheduleItem> slots, LocalDate start, LocalDateTime now) {
        LocalDate windowEnd = start.plusDays(TIME_WINDOW_DAYS - 1);
        List<Integer> missing = new ArrayList<>(List.of(1, 2, 3, 4, 5));
        for (PtScheduleItem slot : slots) {
            boolean inWindow = !slot.getSlotDate().isAfter(windowEnd);
            boolean free = "Free".equals(slot.getKind());
            boolean future = slot.getSlotDate().atTime(slot.getStartTime()).isAfter(now);
            if (inWindow && free && future) {
                missing.remove(Integer.valueOf(slot.getSlotDate().getDayOfWeek().getValue()));
            }
        }
        return missing;
    }

    /**
     * Đếm số buổi "create" = ngày đó PT CHƯA có khung đúng giờ này → giữ lịch sẽ tự tạo khung mới.
     * Ví dụ PT Thứ 5 chỉ mở 09:00, lễ tân chọn Thứ 5 18:00 → mỗi Thứ 5 là 1 buổi "create". hold() bắt tích "Đã xác nhận với PT".
     */
    static int countNewSlots(List<PtSaleDay> days) {
        int count = 0;
        for (PtSaleDay day : days) {
            if ("create".equals(day.getAction())) {
                count++;
            }
        }
        return count;
    }

    /** Gom khung của PT theo ngày (buildPlan + checkRequested dùng). */
    static Map<LocalDate, List<PtScheduleItem>> slotsByDate(List<PtScheduleItem> slots) {
        Map<LocalDate, List<PtScheduleItem>> byDate = new HashMap<>();
        for (PtScheduleItem slot : slots) {
            byDate.computeIfAbsent(slot.getSlotDate(), k -> new ArrayList<>()).add(slot);
        }
        return byDate;
    }

    /**
     * 1 ngày + 1 giờ → có buổi được không (luật DUY NHẤT, dùng cả lúc xem lẫn lúc giữ chỗ).
     *   skip    đã qua giờ · khách có buổi khác trùng giờ · PT đã có khách / đang giữ / đã gỡ khung đúng giờ này
     *           · PT có khung khác chồng giờ · khung cũ khác thời lượng
     *   book    PT có khung Free đúng giờ → giữ khung đó
     *   create  PT chưa có khung giờ này → tạo khung mới
     * warning: có buổi nhưng PT cần dời sau (ngày phòng nghỉ, PT có đơn nghỉ trùng ca).
     */
    static PtSaleDay checkDay(LocalDate date, LocalTime time, int minutes, List<PtScheduleItem> sameDay,
            List<LeaveInfo> leaves, List<HourException> exceptions, List<TimeRange> busy, LocalDateTime now) {
        String label = PtSessionHandleService.dayLabel(date);
        LocalTime endTime = time.plusMinutes(minutes);
        String skip = whySkip(date, time, endTime, sameDay, busy, now);
        if (skip != null) {
            return new PtSaleDay(date, label, time, endTime, "skip", null, skip, null);
        }
        PtScheduleItem exact = null;
        for (PtScheduleItem slot : sameDay) {
            if (slot.getStartTime().equals(time) && slot.getEndTime().equals(endTime) && "Free".equals(slot.getKind())) {
                exact = slot;
            }
        }
        String warning = warningFor(date, time, leaves, exceptions);
        return new PtSaleDay(date, label, time, endTime, exact != null ? "book" : "create",
                exact == null ? null : exact.getTimeSlotId(), null, warning);
    }

    /**
     * Giờ chọn được trong 1 ngày = giờ của lịch mẫu (candidates) + khung Free đúng thời lượng PT công bố riêng ngày đó.
     * Ví dụ lịch mẫu {09:30, 20:00}, riêng 29/10 PT mở thêm 14:00 → 29/10 chọn được {09:30, 14:00, 20:00}.
     */
    static List<LocalTime> dayTimes(List<LocalTime> candidates, List<PtScheduleItem> sameDay, int minutes) {
        TreeSet<LocalTime> times = new TreeSet<>(candidates);
        for (PtScheduleItem slot : sameDay) {
            boolean rightLength = slot.getStartTime().plusMinutes(minutes).equals(slot.getEndTime());
            if ("Free".equals(slot.getKind()) && rightLength) {
                times.add(slot.getStartTime());
            }
        }
        return new ArrayList<>(times);
    }

    /**
     * Dữ liệu bước 3 cho trình duyệt: mỗi ngày T2–T6 trong kỳ × mỗi giờ chọn được → có buổi được không (+ lý do).
     * Trình duyệt ghép "lịch mẫu" + "sửa từng buổi" trên dữ liệu này; hold() vẫn kiểm tra lại bằng checkRequested.
     */
    static PtSalePlan buildPlan(LocalDate start, LocalDate end, int minutes, long price, List<LocalTime> candidates,
            List<PtScheduleItem> slots, List<LeaveInfo> leaves, List<HourException> exceptions,
            List<TimeRange> busy, LocalDateTime now) {
        PtSalePlan plan = new PtSalePlan();
        plan.setStart(start.toString());
        plan.setEnd(end.toString());
        plan.setPrice(price);
        for (LocalTime time : candidates) {
            plan.getTimes().add(DateTimeUtil.formatTime(time));
        }
        Map<LocalDate, List<PtScheduleItem>> byDate = slotsByDate(slots);
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            if (!isWeekday(date)) {
                continue;
            }
            List<PtScheduleItem> sameDay = byDate.getOrDefault(date, List.of());
            PtSalePlan.Day day = new PtSalePlan.Day(date.toString(), date.getDayOfWeek().getValue(),
                    PtSessionHandleService.dayLabel(date));
            for (LocalTime time : dayTimes(candidates, sameDay, minutes)) {
                PtSaleDay check = checkDay(date, time, minutes, sameDay, leaves, exceptions, busy, now);
                boolean fresh = "create".equals(check.getAction());   // PT chưa có khung giờ này ngày đó → sẽ tạo mới
                day.getOptions().add(new PtSalePlan.Option(DateTimeUtil.formatTime(time),
                        DateTimeUtil.formatTime(check.getEndTime()), check.isSession(), check.getReason(), check.getWarning(),
                        fresh));
            }
            plan.getDays().add(day);
        }
        return plan;
    }

    /**
     * "Giữ lịch": kiểm tra lại TỪNG buổi lễ tân gửi lên bằng đúng luật checkDay, trên dữ liệu đã khóa.
     * Ra: các buổi (đều có buổi được). Sai ở đâu → IllegalArgumentException với câu lỗi chỉ đúng ngày đó.
     */
    static List<PtSaleDay> checkRequested(Map<LocalDate, LocalTime> requested, LocalDate start, LocalDate end, int minutes,
            List<LocalTime> candidates, List<PtScheduleItem> slots, List<LeaveInfo> leaves,
            List<HourException> exceptions, List<TimeRange> busy, LocalDateTime now) {
        if (requested.isEmpty()) {
            throw new IllegalArgumentException("Chọn ít nhất 1 buổi tập.");
        }
        Map<LocalDate, List<PtScheduleItem>> byDate = slotsByDate(slots);
        List<PtSaleDay> days = new ArrayList<>();
        for (Map.Entry<LocalDate, LocalTime> entry : requested.entrySet()) {
            LocalDate date = entry.getKey();
            LocalTime time = entry.getValue();
            String label = PtSessionHandleService.dayLabel(date);
            if (date.isBefore(start) || date.isAfter(end) || !isWeekday(date)) {
                throw new IllegalArgumentException(label + " nằm ngoài các ngày T2–T6 của kỳ gói. Chọn lại lịch.");
            }
            List<PtScheduleItem> sameDay = byDate.getOrDefault(date, List.of());
            if (!dayTimes(candidates, sameDay, minutes).contains(time)) {
                throw new IllegalArgumentException(label + ": PT không có khung " + DateTimeUtil.formatTime(time)
                        + ". Chọn lại buổi này.");
            }
            PtSaleDay day = checkDay(date, time, minutes, sameDay, leaves, exceptions, busy, now);
            if (!day.isSession()) {
                throw new IllegalArgumentException(label + " lúc " + DateTimeUtil.formatTime(time) + ": "
                        + day.getReason() + ". Chọn lại buổi này.");
            }
            days.add(day);
        }
        return days;
    }

    /**
     * Ô ẩn "sessions" → các buổi. "2026-10-27@09:30,2026-10-29@18:00" → {27/10 → 09:30, 29/10 → 18:00}, xếp theo ngày.
     * Trống → rỗng. Sai định dạng hoặc 1 ngày 2 buổi → null.
     */
    static Map<LocalDate, LocalTime> parseSessions(String text) {
        Map<LocalDate, LocalTime> sessions = new TreeMap<>();
        if (text == null || text.isBlank()) {
            return sessions;
        }
        for (String part : text.split(",")) {
            String[] pieces = part.trim().split("@");
            LocalDate date = pieces.length == 2 ? parseDate(pieces[0]) : null;
            LocalTime time = pieces.length == 2 ? parseTime(pieces[1]) : null;
            if (date == null || time == null || sessions.put(date, time) != null) {
                return null;
            }
        }
        return sessions;
    }

    private static String whySkip(LocalDate date, LocalTime time, LocalTime endTime, List<PtScheduleItem> sameDay,
            List<TimeRange> busy, LocalDateTime now) {
        if (!date.atTime(time).isAfter(now)) {
            return "Đã qua giờ";
        }
        TimeRange range = new TimeRange(date, time, endTime);
        for (TimeRange other : busy) {
            if (other.overlaps(range)) {
                return "Khách có buổi khác trùng giờ";
            }
        }
        for (PtScheduleItem slot : sameDay) {
            boolean sameStart = slot.getStartTime().equals(time);
            String kind = slot.getKind();
            if (sameStart && "Booked".equals(kind)) {
                return "PT đã có khách giờ này";
            }
            if (sameStart && "Held".equals(kind)) {
                return "Đang được giữ cho đơn khác";
            }
            if (sameStart && "Withdrawn".equals(kind)) {
                return "PT đã gỡ khung giờ này";
            }
            if (sameStart && !slot.getEndTime().equals(endTime)) {
                return "Khung cũ khác thời lượng";
            }
            boolean overlap = new TimeRange(date, slot.getStartTime(), slot.getEndTime()).overlaps(range);
            if (!sameStart && overlap && !"Withdrawn".equals(kind)) {
                return "PT có khung khác chồng giờ";
            }
        }
        return null;
    }

    private static String warningFor(LocalDate date, LocalTime time, List<LeaveInfo> leaves, List<HourException> exceptions) {
        for (HourException exception : exceptions) {
            if (date.equals(exception.getApplyDate()) && exception.isClosedAllDay()) {
                return "Phòng nghỉ ngày này — PT cần dời buổi";
            }
        }
        PtScheduleItem candidate = new PtScheduleItem();
        candidate.setSlotDate(date);
        candidate.setStartTime(time);
        return PtScheduleService.isOnLeave(candidate, leaves) ? "PT có đơn nghỉ — PT cần dời buổi" : null;
    }

    /** Giờ bắt đầu xuất hiện nhiều nhất → Subscriptions.FixedStartTime (giờ "chính" của gói). */
    static LocalTime mostCommonStart(List<PtScheduleItem> slots) {
        Map<LocalTime, Integer> counts = new TreeMap<>();
        for (PtScheduleItem slot : slots) {
            counts.merge(slot.getStartTime(), 1, Integer::sum);
        }
        LocalTime best = null;
        int bestCount = 0;
        for (Map.Entry<LocalTime, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > bestCount) {
                best = entry.getKey();
                bestCount = entry.getValue();
            }
        }
        return best;
    }

    /** Giá trị 1 buổi = giá gói ÷ số buổi thật sự sinh ra, làm tròn đồng (DBR-59). */
    static long sessionValue(long price, int sessionCount) {
        return Math.round((double) price / sessionCount);
    }

    /** Tiền VAT nằm trong giá = giá × VAT ÷ (100 + VAT), làm tròn đồng (DBR-57). */
    static long vatAmount(long price, int vatRate) {
        return Math.round(price * (double) vatRate / (100 + vatRate));
    }

    /** Ca theo giờ: trước 14:00 là ca sáng (Morning), từ 14:00 là ca chiều (Afternoon). */
    static String shiftTypeAt(LocalTime time) {
        return time.isBefore(AFTERNOON_FROM) ? "Morning" : "Afternoon";
    }

    /** "Đinh Công Sơn" → "ĐS" (chữ đầu họ + chữ đầu tên). */
    static String initials(String fullName) {
        String[] parts = fullName.trim().split("\\s+");
        String first = parts[0].substring(0, 1);
        return parts.length == 1 ? first : first + parts[parts.length - 1].substring(0, 1);
    }

    private static boolean isWeekday(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY;
    }

    static String money(long amount) {
        return NumberFormat.getInstance(Locale.forLanguageTag("vi-VN")).format(amount) + "đ";
    }

    private static Integer parseId(String text) {
        try {
            int id = Integer.parseInt(text == null ? "" : text.trim());
            return id > 0 ? id : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static LocalDate parseDate(String text) {
        try {
            return text == null || text.isBlank() ? null : LocalDate.parse(text.trim());
        } catch (DateTimeException exception) {
            return null;
        }
    }

    private static LocalTime parseTime(String text) {
        try {
            return text == null || text.isBlank() ? null : LocalTime.parse(text.trim());
        } catch (DateTimeException exception) {
            return null;
        }
    }
}
