package com.gms.service;

import com.gms.constant.Messages;
import com.gms.dao.NotificationDAO;
import com.gms.dao.PtSaleDAO;
import com.gms.dao.PtSaleDAO.PackageRow;
import com.gms.dao.PtSaleDAO.PtRow;
import com.gms.dao.PtScheduleDAO;
import com.gms.dto.HourException;
import com.gms.dto.LeaveInfo;
import com.gms.dto.PtSaleCustomer;
import com.gms.dto.PtSaleDay;
import com.gms.dto.PtSaleOption;
import com.gms.dto.PtSaleView;
import com.gms.dto.PtScheduleItem;
import com.gms.dto.ServiceResult;
import com.gms.util.DBContext;
import com.gms.util.DateTimeUtil;
import com.gms.util.TimeRange;
import java.sql.Connection;
import java.sql.SQLException;
import java.text.NumberFormat;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * SERVICE MÀN LỄ TÂN BÁN GÓI PT (/receptionist/pt-sales) — UC-21, bản đơn giản hóa (docs/notes.md 25/10/2026).
 *
 * Servlet gọi 2 hàm chính:
 *   prepare — mỗi lần Lễ tân bấm "Tiếp" (GET): đi 4 bước Khách → Gói/PT/ngày → Giờ cố định → Lịch dự kiến.
 *   sell    — bấm "Xác nhận đã thu tiền" (POST): làm lại ĐÚNG 4 bước đó có khóa dòng, rồi ghi DB trong 1 transaction.
 *
 * Đơn giản hóa so với SRS (đã chốt):
 *   - Thu tiền ngay khi bán: đơn tạo ra là Paid luôn, không có đơn Pending / khung Held.
 *   - Ngày nào giờ cố định không dùng được → ngày đó không có buổi (không chọn giờ thay — BR-41 bản rút gọn).
 *   - Không có nhánh bán 1 buổi lẻ cho khách vãng lai.
 *   - Ca làm việc tự mở theo giờ hiện tại (trước 14:00 = ca sáng).
 *
 * MỤC LỤC
 *   PHẦN 1. HÀM CHÍNH      prepare, sell
 *   PHẦN 2. 4 BƯỚC (chung) build → loadHolder, loadPackage, loadPartner, loadPt, loadPlan
 *   PHẦN 3. GHI DB         writeSale, notifySale
 *   PHẦN 4. LUẬT (static)  whyCannotBuy, fixedTimeChoices, planDays, sessionValue, vatAmount, shiftTypeAt
 */
public class PtSaleService {

    static final int START_DAYS_AHEAD = 30;     // ngày bắt đầu: từ hôm nay tới 30 ngày sau
    static final int TIME_WINDOW_DAYS = 14;     // giờ cố định: tính theo khung PT công bố trong 14 ngày đầu kỳ
    static final LocalTime AFTERNOON_FROM = LocalTime.of(14, 0);   // từ 14:00 là ca chiều

    private final PtSaleDAO saleDAO = new PtSaleDAO();
    private final PtScheduleDAO scheduleDAO = new PtScheduleDAO();   // dùng lại: đơn nghỉ, ngày đổi giờ / nghỉ
    private final NotificationDAO notificationDAO = new NotificationDAO();
    private final SystemConfigService configService = new SystemConfigService();


    // ================================================================================================================
    // PHẦN 1. HÀM CHÍNH
    // ================================================================================================================

    /**
     * HÀM CHÍNH — PtSaleServlet.doGet gọi. Chỉ ĐỌC, không giữ chỗ.
     * Vào: chữ Lễ tân nhập trên form (chưa kiểm tra). Ra: PtSaleView đi tới bước xa nhất có thể.
     */
    public ServiceResult<PtSaleView> prepare(int accountId, String query, String partnerQuery, String packageText,
            String ptText, String startText, String timeText, LocalDateTime now) {
        try (Connection connection = DBContext.getConnection()) {
            int[] receptionist = saleDAO.findReceptionist(connection, accountId);
            if (receptionist == null) {
                return ServiceResult.fail(Messages.MSG07_NO_PERMISSION);
            }
            PtSaleView view = newView(query, partnerQuery, packageText, ptText, startText, timeText, now);
            build(connection, view, receptionist[1], now, false);
            return ServiceResult.ok(view);

        } catch (SQLException | IllegalStateException | DateTimeException exception) {
            exception.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /**
     * HÀM CHÍNH — PtSaleServlet.doPost gọi khi bấm "Xác nhận đã thu tiền".
     *
     * B1 khóa PT → B2 làm lại 4 bước với dữ liệu MỚI NHẤT, có khóa khung của PT → B3 ghi đơn, giao dịch, gói, buổi.
     * Ra: thành công → câu toast có mã đơn; thất bại → câu lỗi, KHÔNG ghi gì (Servlet gọi lại prepare để dựng trang).
     */
    public ServiceResult<PtSaleView> sell(int accountId, String query, String partnerQuery, String packageText,
            String ptText, String startText, String timeText, String method, String reference, LocalDateTime now) {

        // B0: cách thu tiền: chỉ tiền mặt / chuyển khoản (CBR-49). Mã chuyển khoản không bắt buộc, tối đa 50 ký tự.
        if (!"Cash".equals(method) && !"Transfer".equals(method)) {
            return ServiceResult.fail("Chọn cách thu tiền: tiền mặt hoặc chuyển khoản.");
        }
        String ref = reference == null || reference.isBlank() || "Cash".equals(method) ? null : reference.trim();
        if (ref != null && ref.length() > 50) {
            return ServiceResult.fail("Mã giao dịch chuyển khoản tối đa 50 ký tự.");
        }

        try (Connection connection = DBContext.getConnection()) {
            connection.setAutoCommit(false);
            try {
                int[] receptionist = saleDAO.findReceptionist(connection, accountId);
                if (receptionist == null) {
                    connection.rollback();
                    return ServiceResult.fail(Messages.MSG07_NO_PERMISSION);
                }

                // B1: khóa PT trước khi đọc lịch của PT (xếp hàng với lễ tân khác và với PT đang Công bố).
                PtSaleView view = newView(query, partnerQuery, packageText, ptText, startText, timeText, now);
                if (view.getPtId() != null) {
                    saleDAO.lockPt(connection, view.getPtId());
                }

                // B2: làm lại 4 bước, đọc khung PT có khóa.
                build(connection, view, receptionist[1], now, true);
                String problem = firstProblem(view);
                if (problem != null) {
                    connection.rollback();
                    return ServiceResult.fail(problem);
                }

                // B3: ghi.
                String orderCode = writeSale(connection, view, receptionist[0], method, ref, now);
                connection.commit();

                String message = "Đã bán " + view.getPackageName() + " cho " + view.getHolder().getFullName()
                        + " · mã đơn " + orderCode + " · " + view.getSessionCount() + " buổi.";
                return ServiceResult.ok(view, message);

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


    // ================================================================================================================
    // PHẦN 2. 4 BƯỚC — prepare và sell dùng CHUNG, nên xem trước và lúc bán kiểm tra đúng một luật
    // ================================================================================================================

    /** HÀM PHỤ — đọc chữ trên form thành giá trị; chữ sai thì để null (bước đó coi như chưa chọn). */
    private PtSaleView newView(String query, String partnerQuery, String packageText, String ptText,
            String startText, String timeText, LocalDateTime now) {
        PtSaleView view = new PtSaleView();
        view.setQuery(trim(query));
        view.setPartnerQuery(trim(partnerQuery));
        view.setPackageId(parseId(packageText));
        view.setPtId(parseId(ptText));
        view.setStartDate(parseDate(startText));
        view.setFixedTime(parseTime(timeText));
        view.setMinStart(now.toLocalDate());
        view.setMaxStart(now.toLocalDate().plusDays(START_DAYS_AHEAD));
        return view;
    }

    /**
     * HÀM PHỤ — đi lần lượt 4 bước; bước nào thiếu hoặc sai thì DỪNG ở đó (bước sau để trống).
     * lock = true chỉ khi bán (đọc khung PT có khóa dòng).
     */
    private void build(Connection connection, PtSaleView view, int receptionistPersonId, LocalDateTime now,
            boolean lock) throws SQLException {
        LocalDate today = now.toLocalDate();
        int minAge = configService.getInt(SystemConfigService.MIN_AGE_FOR_PT);

        // Bước 1: người đứng tên.
        if (!loadHolder(connection, view, today, minAge, receptionistPersonId)) {
            return;
        }
        // Bước 2a: gói (danh sách luôn hiện khi đã có khách).
        if (!loadPackage(connection, view)) {
            return;
        }
        // Bước 2b: gói đôi cần người tập cùng.
        if (view.isCouple() && !loadPartner(connection, view, today, minAge, receptionistPersonId)) {
            return;
        }
        // Bước 2c: PT + ngày bắt đầu.
        if (!loadPt(connection, view)) {
            return;
        }
        // Bước 3 + 4: giờ cố định, lịch từng ngày.
        loadPlan(connection, view, now, lock);
    }

    /** Bước 1. Tìm người đứng tên; không tìm thấy hoặc không bán được → dừng. */
    private boolean loadHolder(Connection connection, PtSaleView view, LocalDate today, int minAge,
            int receptionistPersonId) throws SQLException {
        if (view.getQuery() == null) {
            return false;
        }
        PtSaleCustomer holder = saleDAO.findCustomer(connection, view.getQuery(), today);
        if (holder == null) {
            view.setHolderError("Không tìm thấy khách có mã hoặc số điện thoại \"" + view.getQuery() + "\".");
            return false;
        }
        holder.setBlockReason(whyCannotBuy(holder, today, minAge, receptionistPersonId));
        view.setHolder(holder);
        return holder.getBlockReason() == null;
    }

    /** Bước 2a. Danh sách gói PT đang bán + gói đang chọn (giá, số tháng, gói đôi hay không). */
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

    /** Bước 2b. Người tập cùng (chỉ gói đôi): phải tìm thấy, khác người đứng tên, và cũng mua được (BR-37). */
    private boolean loadPartner(Connection connection, PtSaleView view, LocalDate today, int minAge,
            int receptionistPersonId) throws SQLException {
        if (view.getPartnerQuery() == null) {
            view.setPartnerError("Gói đôi: nhập mã hoặc số điện thoại người tập cùng.");
            return false;
        }
        PtSaleCustomer partner = saleDAO.findCustomer(connection, view.getPartnerQuery(), today);
        if (partner == null) {
            view.setPartnerError("Không tìm thấy người tập cùng \"" + view.getPartnerQuery() + "\".");
            return false;
        }
        if (partner.getCustomerId() == view.getHolder().getCustomerId()) {
            view.setPartnerError("Người tập cùng phải là một khách khác.");
            return false;
        }
        partner.setBlockReason(whyCannotBuy(partner, today, minAge, receptionistPersonId));
        view.setPartner(partner);
        return partner.getBlockReason() == null;
    }

    /** Bước 2c. PT (không phải chính khách) + ngày bắt đầu trong [hôm nay, +30 ngày] → ngày kết thúc. */
    private boolean loadPt(Connection connection, PtSaleView view) throws SQLException {
        // Danh sách PT: bỏ PT là chính khách / người tập cùng (nhân viên tập như khách phải chọn PT khác).
        List<PtSaleOption> options = new ArrayList<>();
        for (PtRow row : saleDAO.findWorkingPts(connection)) {
            if (!isCustomerPerson(view, row.personId)) {
                options.add(new PtSaleOption(String.valueOf(row.employeeId), row.fullName,
                        "Hoa hồng " + row.commissionRate + "%"));
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
        boolean outOfRange = view.getStartDate().isBefore(view.getMinStart())
                || view.getStartDate().isAfter(view.getMaxStart());
        if (outOfRange) {
            view.setChoiceError("Ngày bắt đầu phải từ " + DateTimeUtil.formatDate(view.getMinStart())
                    + " đến " + DateTimeUtil.formatDate(view.getMaxStart()) + ".");
            return false;
        }
        view.setPtName(pt.fullName);
        view.setCommissionRate(pt.commissionRate);
        view.setEndDate(DateTimeUtil.calculateEndDateByMonths(view.getStartDate(), view.getMonths()));
        return true;
    }

    /** Bước 3 + 4. Giờ cố định (từ khung PT đã công bố) rồi lịch từng ngày T2–T6 trong kỳ. */
    private void loadPlan(Connection connection, PtSaleView view, LocalDateTime now, boolean lock) throws SQLException {
        int minutes = configService.getInt(SystemConfigService.SESSION_DURATION_MINUTES);
        LocalDate start = view.getStartDate();
        LocalDate end = view.getEndDate();

        // Bước 3: các giờ PT đã công bố khung trống trong 14 ngày đầu kỳ.
        List<PtScheduleItem> slots = saleDAO.findPtSlots(connection, view.getPtId(), start, end, lock);
        view.setTimes(fixedTimeChoices(slots, start, minutes, now));
        if (view.getFixedTime() == null) {
            return;
        }
        boolean published = false;
        for (PtSaleOption option : view.getTimes()) {
            published |= option.getValue().equals(view.getFixedTime().toString());
        }
        if (!published) {
            view.setPlanError("Giờ " + DateTimeUtil.formatTime(view.getFixedTime())
                    + " PT chưa công bố khung trống. Chọn một giờ trong danh sách (CBR-52).");
            return;
        }

        // Bước 4: lịch từng ngày + giá trị buổi.
        int partnerId = view.getPartner() == null ? view.getHolder().getCustomerId() : view.getPartner().getCustomerId();
        List<TimeRange> busy = saleDAO.findCustomerBusy(connection, view.getHolder().getCustomerId(), partnerId, start, end);
        List<LeaveInfo> leaves = scheduleDAO.findLeaves(connection, view.getPtId(), start, end);
        List<HourException> exceptions = scheduleDAO.findHourExceptions(connection, start, end);

        List<PtSaleDay> days = planDays(start, end, view.getFixedTime(), minutes, slots, leaves, exceptions, busy, now);
        int count = 0;
        for (PtSaleDay day : days) {
            if (day.isSession()) {
                count++;
            }
        }
        view.setDays(days);
        view.setSessionCount(count);
        view.setSkipCount(days.size() - count);
        if (count == 0) {
            view.setPlanError("Không ngày nào trong kỳ dùng được giờ này. Chọn giờ hoặc ngày bắt đầu khác.");
            return;
        }
        if (count > 255) {
            throw new IllegalStateException("Số buổi vượt giới hạn TINYINT");
        }
        view.setSessionValue(sessionValue(view.getPrice(), count));
    }

    /** Khách (đứng tên hoặc tập cùng) có phải chính người này không — so theo PersonId. */
    private boolean isCustomerPerson(PtSaleView view, int personId) {
        boolean holder = view.getHolder() != null && view.getHolder().getPersonId() == personId;
        boolean partner = view.getPartner() != null && view.getPartner().getPersonId() == personId;
        return holder || partner;
    }

    /** Câu lỗi đầu tiên chặn việc bán (theo thứ tự bước); null = bán được. */
    static String firstProblem(PtSaleView view) {
        if (view.getHolder() == null) {
            return view.getHolderError() != null ? view.getHolderError() : "Tìm khách trước khi bán.";
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
        if (view.getEndDate() == null || view.getFixedTime() == null) {
            return "Chọn đủ gói, PT, ngày bắt đầu và giờ cố định.";
        }
        if (view.getPlanError() != null) {
            return view.getPlanError();
        }
        return view.isReady() ? null : "Chưa có lịch dự kiến.";
    }


    // ================================================================================================================
    // PHẦN 3. GHI DB — trong transaction của sell
    // ================================================================================================================

    /**
     * HÀM PHỤ — B3 của sell. Thứ tự: ca → mã đơn → đơn (Paid) → giao dịch (Success) → gói → từng buổi → thông báo.
     * Trả mã đơn để in lên toast.
     */
    private String writeSale(Connection connection, PtSaleView view, int receptionistId, String method, String ref,
            LocalDateTime now) throws SQLException {

        // B1: ca hiện tại (tự mở nếu chưa mở). Ca đã chốt → không ghi được tiền vào ca đó.
        String shiftType = shiftTypeAt(now.toLocalTime());
        Integer shiftId = saleDAO.findOrOpenShift(connection, now.toLocalDate(), shiftType, receptionistId, now);
        if (shiftId == null) {
            throw new IllegalArgumentException("Ca " + ("Morning".equals(shiftType) ? "sáng" : "chiều")
                    + " hôm nay đã chốt két, không ghi thêm tiền vào ca này được.");
        }

        // B2: đơn + giao dịch. Giá đã gồm VAT; tiền VAT = giá × VAT ÷ (100 + VAT) (DBR-57).
        PackageRow pkg = saleDAO.findPackage(connection, view.getPackageId());
        int vatRate = configService.getInt(SystemConfigService.VAT_PERCENT);
        String orderCode = saleDAO.nextOrderCode(connection, now.getYear());
        Integer partnerId = view.getPartner() == null ? null : view.getPartner().getCustomerId();
        int orderId = saleDAO.insertOrder(connection, orderCode, view.getHolder().getCustomerId(), partnerId, pkg,
                receptionistId, shiftId, vatRate, vatAmount(pkg.price, vatRate), now);
        saleDAO.insertTransaction(connection, orderId, pkg.price, method, ref, receptionistId, now);

        // B3: gói PT. Trần đổi buổi = P-08 × số tháng, chốt lúc bán (GBR-04).
        int maxSwaps = configService.getInt(SystemConfigService.MAX_SWAP_COUNT) * view.getMonths();
        int subscriptionId = saleDAO.insertSubscription(connection, orderId, view.getHolder().getCustomerId(), pkg.type,
                view.getPtId(), view.getStartDate(), view.getEndDate(), view.getFixedTime(), view.getSessionCount(), maxSwaps);

        // B4: từng buổi. "book" = khung PT đã công bố → Booked; "create" = tạo khung Booked mới.
        long value = view.getSessionValue();
        long commission = Math.round(value * view.getCommissionRate() / 100.0);
        for (PtSaleDay day : view.getDays()) {
            if (!day.isSession()) {
                continue;
            }
            int slotId;
            if ("book".equals(day.getAction())) {
                if (!saleDAO.bookFreeSlot(connection, day.getSlotId(), view.getPtId())) {
                    throw new IllegalArgumentException(day.getDayLabel() + ": " + Messages.MSG103_SLOT_ALREADY_TAKEN);
                }
                slotId = day.getSlotId();
            } else {
                slotId = saleDAO.insertBookedSlot(connection, view.getPtId(), day.getDate(), day.getStartTime(), day.getEndTime());
            }
            saleDAO.insertSession(connection, subscriptionId, slotId, view.getPtId(), value, commission);
        }

        // B5: thông báo khách (+ người tập cùng) và PT.
        notifySale(connection, view, orderCode);
        return orderCode;
    }

    /** HÀM PHỤ — B5 của writeSale. Hồ sơ không có tài khoản thì không nhận (giống JOB-08). */
    private void notifySale(Connection connection, PtSaleView view, String orderCode) throws SQLException {
        String term = DateTimeUtil.formatDate(view.getStartDate()) + " – " + DateTimeUtil.formatDate(view.getEndDate());
        String time = DateTimeUtil.formatTime(view.getFixedTime());

        int holderId = view.getHolder().getCustomerId();
        int partnerId = view.getPartner() == null ? holderId : view.getPartner().getCustomerId();
        notify(connection, saleDAO.findCustomerAccounts(connection, holderId, partnerId), "Gói PT mới đã kích hoạt",
                view.getPackageName() + " với " + view.getPtName() + ": " + view.getSessionCount() + " buổi, T2–T6 lúc "
                        + time + ", từ " + term + ". Mã đơn " + orderCode + ".");

        Integer ptAccount = notificationDAO.findActiveAccountIdByEmployeeId(connection, view.getPtId());
        if (ptAccount != null) {
            notify(connection, List.of(ptAccount), "Bạn có khách gói PT mới",
                    view.getHolder().getFullName() + (view.getPartner() == null ? "" : " & " + view.getPartner().getFullName())
                            + " mua " + view.getPackageName() + ": " + view.getSessionCount() + " buổi, T2–T6 lúc "
                            + time + ", từ " + term + ".");
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
    // PHẦN 4. LUẬT — static, không đọc DB (PtSaleServiceTest)
    // ================================================================================================================

    /**
     * Khách có mua được gói PT không? null = được.
     *   lễ tân không bán cho hồ sơ khách của chính mình · đủ tuổi tối thiểu (P-05) ·
     *   có gói thành viên phủ hôm nay (vé ngày không tính) · hôm nay không bảo lưu (CBR-59, BR-37).
     */
    static String whyCannotBuy(PtSaleCustomer customer, LocalDate today, int minAge, int receptionistPersonId) {
        if (customer.getPersonId() == receptionistPersonId) {
            return "Lễ tân không tự bán gói cho hồ sơ khách của chính mình. Nhờ lễ tân khác.";
        }
        if (DateTimeUtil.calculateAge(customer.getDateOfBirth(), today) < minAge) {
            return customer.getFullName() + " chưa đủ " + minAge + " tuổi, chưa mua được gói PT.";
        }
        if (customer.getMembershipEnd() == null) {
            return customer.getFullName() + " chưa có gói thành viên còn hạn hôm nay (vé ngày không tính). "
                    + "Bán gói thành viên trước.";
        }
        if (customer.isFrozen()) {
            return customer.getFullName() + " đang bảo lưu gói thành viên, chưa mua được gói PT.";
        }
        return null;
    }

    /**
     * Bước 3. Các giờ cố định được chọn = giờ bắt đầu của khung Free (đúng P-06 phút, chưa qua giờ)
     * PT đã công bố vào T2–T6 trong 14 ngày đầu kỳ. note cho biết PT trống mấy ngày trong 14 ngày đó.
     * Ví dụ PT công bố 18:00 vào 8 ngày T2–T6 đầu kỳ → {"18:00", "18:00 – 19:30", "PT trống 8/10 ngày đầu"}.
     */
    static List<PtSaleOption> fixedTimeChoices(List<PtScheduleItem> slots, LocalDate start, int minutes,
            LocalDateTime now) {
        LocalDate windowEnd = start.plusDays(TIME_WINDOW_DAYS - 1);
        int weekdays = 0;
        for (LocalDate d = start; !d.isAfter(windowEnd); d = d.plusDays(1)) {
            weekdays += isWeekday(d) ? 1 : 0;
        }

        Map<LocalTime, Integer> counts = new TreeMap<>();
        for (PtScheduleItem slot : slots) {
            boolean inWindow = !slot.getSlotDate().isAfter(windowEnd) && isWeekday(slot.getSlotDate());
            boolean free = "Free".equals(slot.getKind());
            boolean rightLength = slot.getStartTime().plusMinutes(minutes).equals(slot.getEndTime());
            boolean future = slot.getSlotDate().atTime(slot.getStartTime()).isAfter(now);
            if (inWindow && free && rightLength && future) {
                counts.merge(slot.getStartTime(), 1, Integer::sum);
            }
        }

        List<PtSaleOption> options = new ArrayList<>();
        for (Map.Entry<LocalTime, Integer> entry : counts.entrySet()) {
            LocalTime time = entry.getKey();
            options.add(new PtSaleOption(time.toString(),
                    DateTimeUtil.formatTime(time) + " – " + DateTimeUtil.formatTime(time.plusMinutes(minutes)),
                    "PT trống " + entry.getValue() + "/" + weekdays + " ngày đầu"));
        }
        return options;
    }

    /**
     * Bước 4. Lịch từng ngày T2–T6 từ start tới end (tính cả 2 đầu). Mỗi ngày 1 trong 3 kết quả:
     *   skip    đã qua giờ · khách có buổi khác trùng giờ · PT đã có khách / đã gỡ khung đúng giờ này
     *           · PT có khung khác chồng giờ · khung cũ khác thời lượng
     *   book    PT có khung Free đúng giờ → dùng khung đó
     *   create  PT chưa có khung giờ này → tạo khung mới
     * Có buổi nhưng cần PT để ý (warning): ngày phòng nghỉ, PT có đơn nghỉ trùng ca → PT dời sau ở Lịch dạy.
     */
    static List<PtSaleDay> planDays(LocalDate start, LocalDate end, LocalTime time, int minutes,
            List<PtScheduleItem> slots, List<LeaveInfo> leaves, List<HourException> exceptions,
            List<TimeRange> busy, LocalDateTime now) {
        List<PtSaleDay> days = new ArrayList<>();
        LocalTime endTime = time.plusMinutes(minutes);

        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            if (!isWeekday(date)) {
                continue;
            }
            String label = PtSessionHandleService.dayLabel(date);
            TimeRange range = new TimeRange(date, time, endTime);

            String skip = whySkip(date, time, endTime, range, slots, busy, now);
            if (skip != null) {
                days.add(new PtSaleDay(date, label, time, endTime, "skip", null, skip, null));
                continue;
            }

            PtScheduleItem exact = findExactFree(date, time, endTime, slots);
            String warning = warningFor(date, time, leaves, exceptions);
            if (exact != null) {
                days.add(new PtSaleDay(date, label, time, endTime, "book", exact.getTimeSlotId(), null, warning));
            } else {
                days.add(new PtSaleDay(date, label, time, endTime, "create", null, null, warning));
            }
        }
        return days;
    }

    /** HÀM PHỤ — planDays gọi. Lý do ngày này KHÔNG có buổi; null = có buổi được. */
    private static String whySkip(LocalDate date, LocalTime time, LocalTime endTime, TimeRange range,
            List<PtScheduleItem> slots, List<TimeRange> busy, LocalDateTime now) {
        if (!date.atTime(time).isAfter(now)) {
            return "Đã qua giờ";
        }
        for (TimeRange other : busy) {
            if (other.overlaps(range)) {
                return "Khách có buổi khác trùng giờ";
            }
        }
        for (PtScheduleItem slot : slots) {
            if (!slot.getSlotDate().equals(date)) {
                continue;
            }
            boolean sameStart = slot.getStartTime().equals(time);
            String kind = slot.getKind();
            if (sameStart && ("Booked".equals(kind) || "Held".equals(kind))) {
                return "PT đã có khách giờ này";
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

    /** HÀM PHỤ — planDays gọi. Khung Free đúng giờ bắt đầu + kết thúc của ngày này; không có → null. */
    private static PtScheduleItem findExactFree(LocalDate date, LocalTime time, LocalTime endTime,
            List<PtScheduleItem> slots) {
        for (PtScheduleItem slot : slots) {
            boolean same = slot.getSlotDate().equals(date) && slot.getStartTime().equals(time)
                    && slot.getEndTime().equals(endTime);
            if (same && "Free".equals(slot.getKind())) {
                return slot;
            }
        }
        return null;
    }

    /** HÀM PHỤ — planDays gọi. Có buổi nhưng PT phải dời sau: ngày phòng nghỉ / PT có đơn nghỉ trùng ca. */
    private static String warningFor(LocalDate date, LocalTime time, List<LeaveInfo> leaves,
            List<HourException> exceptions) {
        for (HourException exception : exceptions) {
            if (date.equals(exception.getApplyDate()) && exception.isClosedAllDay()) {
                return "Phòng nghỉ ngày này — PT cần dời buổi";
            }
        }
        PtScheduleItem candidate = new PtScheduleItem();
        candidate.setSlotDate(date);
        candidate.setStartTime(time);
        if (PtScheduleService.isOnLeave(candidate, leaves)) {
            return "PT có đơn nghỉ — PT cần dời buổi";
        }
        return null;
    }

    /** Giá trị 1 buổi = giá gói ÷ số buổi thật sự sinh ra, làm tròn đồng (DBR-59). VD 16.500.000 ÷ 44 = 375.000. */
    static long sessionValue(long price, int sessionCount) {
        return Math.round((double) price / sessionCount);
    }

    /** Tiền VAT nằm trong giá = giá × VAT ÷ (100 + VAT), làm tròn đồng (DBR-57). VD 16.500.000, 10% → 1.500.000. */
    static long vatAmount(long price, int vatRate) {
        return Math.round(price * (double) vatRate / (100 + vatRate));
    }

    /** Ca theo giờ: trước 14:00 là ca sáng (Morning), từ 14:00 là ca chiều (Afternoon). */
    static String shiftTypeAt(LocalTime time) {
        return time.isBefore(AFTERNOON_FROM) ? "Morning" : "Afternoon";
    }

    private static boolean isWeekday(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY;
    }

    private static String money(long amount) {
        return NumberFormat.getInstance(Locale.forLanguageTag("vi-VN")).format(amount) + "đ";
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
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
