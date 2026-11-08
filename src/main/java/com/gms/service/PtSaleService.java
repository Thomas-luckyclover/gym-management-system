package com.gms.service;

import com.gms.constant.Messages;
import com.gms.dao.NotificationDAO;
import com.gms.dao.PtSaleDAO;
import com.gms.dao.PtSaleDAO.HeldOrder;
import com.gms.dao.PtSaleDAO.PackageRow;
import com.gms.dao.PtSaleDAO.PtRow;
import com.gms.dao.PtScheduleDAO;
import com.gms.dao.ShiftDAO;
import com.gms.dto.HourException;
import com.gms.dto.LeaveInfo;
import com.gms.dto.PtSaleChoiceBlock;
import com.gms.dto.PtSaleBuyerBlock;
import com.gms.dto.PtSalePlanBlock;
import com.gms.dto.PtSaleHeldBlock;
import com.gms.dto.PtSaleCustomer;
import com.gms.dto.PtSaleDay;
import com.gms.dto.PtSalePlan;
import com.gms.dto.PtSaleOption;
import com.gms.dto.PtSalePendingHold;
import com.gms.dto.PtSaleView;
import com.gms.dto.PtScheduleItem;
import com.gms.dto.ServiceResult;
import com.gms.entity.Order;
import com.gms.entity.Shift;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * SERVICE MÀN LỄ TÂN BÁN GÓI PT — /receptionist/pt-sales (UC-21 bản đơn giản hóa, docs/notes.md 25/10/2026).
 *
 * AI GỌI SERVICE NÀY?  Chỉ PtSaleServlet. Servlet đưa chữ thô (Input) + accountId; Service tự kiểm tra hết.
 * SERVICE GỌI AI?      PtSaleDAO (đọc / ghi đơn, khung, buổi) · PtScheduleDAO (ngày nghỉ PT, giờ mở cửa ngoại lệ)
 *                      · ShiftDAO (ca đang mở) · NotificationDAO (thông báo sau thu tiền) · SystemConfigService (P-05, P-06, P-10, P-19).
 *
 * BỐ CỤC FILE — mỗi HÀM CHÍNH đứng trước, các hàm phụ của nó nằm ngay bên dưới:
 *   HÀM CHÍNH 1  search    ô gợi ý khách (AJAX)                         → whyCannotBuy
 *   HÀM CHÍNH 2  prepare   mở màn / đổi lựa chọn / mở đơn đang giữ      → newView, build, load…, buildPlan, checkDay, loadHeld …
 *   HÀM CHÍNH 3  hold      "Giữ lịch 15 phút": đơn Pending + khung Held → firstProblem, countNewSlots, checkRequested, openShiftId
 *   HÀM CHÍNH 4  pay       "Xác nhận đã thu tiền": Paid + gói + buổi    → whyCannotPay, mostCommonStart, notifySale
 *   HÀM CHÍNH 5  release   "Hủy giữ chỗ": đơn Cancelled, trả khung      → (dùng releaseHold của DAO)
 *   DÙNG CHUNG             releaseExpired, inTransaction, requireReceptionist, tiền / ngày giờ
 *
 * LUẬT QUAN TRỌNG
 *   - prepare (xem) và hold (giữ) đi CÙNG hàm build → luật lúc xem và lúc giữ là một.
 *   - hold khóa dòng PT (PtSaleDAO.lockPt) rồi mới đọc lại khung → 2 lễ tân giữ cùng khung: người sau bị từ chối.
 *   - Giữ / thu tiền bắt buộc có ca Open hôm nay (openShiftId): chưa mở ca → MSG109 / MSG110, ca ngày trước → MSG324.
 *     Hệ thống KHÔNG tự mở ca.
 *   - Quá 15 phút: không có JOB. releaseExpired chạy đầu prepare và hold ("nhả lười").
 *   - Các hàm static (whyCannotBuy, checkDay, buildPlan, checkRequested, …) không đọc DB → PtSaleServiceTest gọi thẳng.
 */
public class PtSaleService {

    static final int START_DAYS_AHEAD = 30;                  // ngày bắt đầu: hôm nay → +30 ngày
    static final int TIME_WINDOW_DAYS = 14;                  // giờ trên bảng tuần: khung PT công bố trong 14 ngày đầu kỳ
    static final int HOLD_MINUTES = 15;                      // giữ chỗ bao lâu
    static final String EXPIRED_REASON = "Hết 15 phút giữ chỗ, chưa thu tiền";
    static final String RELEASE_REASON = "Lễ tân hủy giữ chỗ, chưa thu tiền";
    static final String CANCEL_BY_RECEPTIONIST = Order.CancelType.CANCELLED_BY_RECEPTIONIST.getDbValue(); // lễ tân bấm Hủy
    static final String CANCEL_TIMEOUT = Order.CancelType.PAYMENT_TIMEOUT.getDbValue();                   // hết 15 phút (BR-93)

    // Lớp dưới mà Service này gọi (xem "SERVICE GỌI AI?" ở đầu file)
    private final PtSaleDAO saleDAO = new PtSaleDAO();
    private final PtScheduleDAO scheduleDAO = new PtScheduleDAO();
    private final ShiftDAO shiftDAO = new ShiftDAO();
    private final NotificationDAO notificationDAO = new NotificationDAO();
    private final SystemConfigService configService = new SystemConfigService();

    /** Lựa chọn trên URL / form (chữ thô, Service tự đọc + kiểm tra). */
    public static class Input {
        public String customer, partner, packageId, pt, start, query;
        public String[] times = new String[5];   // t1..t5 = lịch mẫu Thứ 2..Thứ 6 (trống = Nghỉ)
        public boolean confirmPt;                // ô tích "Đã xác nhận với PT" (bắt buộc khi có buổi PT chưa mở khung)
        public String sessions;                  // "2026-10-27@09:30,…" — các buổi đã chọn (chỉ khi Giữ lịch)
    }


    // ============================================================================================================
    // HÀM CHÍNH 1. search — ô "Tên, SĐT hoặc mã khách" (AJAX)
    //   Ai gọi: PtSaleServlet.writeSuggestions  ·  Gọi xuống: PtSaleDAO.findReceptionist, searchCustomers
    //   Hàm phụ ngay dưới: whyCannotBuy
    // ============================================================================================================

    /**
     * HÀM CHÍNH 1 — gợi ý khách khi lễ tân gõ vào ô "Tên, SĐT hoặc mã khách".
     *   AI GỌI   PtSaleServlet.writeSuggestions (GET /receptionist/pt-sales/customers?q=…)
     *   VÀO      accountId (tài khoản đăng nhập), query (chữ thô vừa gõ), now
     *   RA       mọi PtSaleCustomer khớp, mỗi khách có blockReason (null = bán được) → Servlet đổi sang JSON
     *   BƯỚC     1. chưa gõ gì → danh sách rỗng (không hỏi DB)
     *            2. findReceptionist: tài khoản phải là lễ tân đang làm → không thì MSG07
     *            3. searchCustomers: tên không dấu / SĐT / mã KH
     *            4. mỗi khách: whyCannotBuy → blockReason (chữ đỏ trên gợi ý)
     *   GHI DB   không
     */
    public ServiceResult<List<PtSaleCustomer>> search(int accountId, String query, LocalDateTime now) {
        String text = query == null ? "" : query.trim();
        if (text.isEmpty()) {
            return ServiceResult.ok(new ArrayList<>());
        }
        try (Connection connection = DBContext.getConnection()) {
            int[] receptionist = saleDAO.findReceptionist(connection, accountId);
            if (receptionist == null) {
                return ServiceResult.fail(Messages.MSG07_NO_PERMISSION);
            }
            
            // digits => số, \\D: chỉ nhận số, bỏ hết khoảng cách, còn nếu là text thì ok
            String digits = text.replaceAll("\\D", "");
            List<PtSaleCustomer> rows = saleDAO.searchCustomers(
                    connection,
                    text,
                    digits,
                    now.toLocalDate());
            
            // min age = 10 tuổi (Luật nghiệp vụ)
            int minAge = configService.getInt(SystemConfigService.MIN_AGE_FOR_PT);
            
            // Check lý do khách không thể mua gói PT: minage, lễ tân ko thể tự bán cho chính mình, chưa có gói thành viên hôm nay, đang bảo lưu hôm nay
            for (PtSaleCustomer row : rows) {
                row.setBlockReason(whyCannotBuy(
                        row,
                        now.toLocalDate(),
                        minAge,
                        receptionist[1]));
            }
            return ServiceResult.ok(rows);
        } catch (SQLException exception) {
            exception.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }


    /**
     * HÀM PHỤ (search, loadHolder, loadPartner) — khách có mua được gói PT không? null = được.
     * Chữ trả về in thẳng ra màn: dòng gợi ý (pt-sales.js) và thẻ khách (_sale-customer.jspf).
     *   không phải hồ sơ khách của chính lễ tân · đủ tuổi (P-05) · có gói thành viên phủ hôm nay (vé ngày không tính)
     *   · hôm nay không bảo lưu (CBR-59, BR-37).
     */
    static String whyCannotBuy(
            PtSaleCustomer customer,
            LocalDate today,
            int minAge,
            int receptionistPersonId) {
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


    // ============================================================================================================
    // HÀM CHÍNH 2. prepare — mở màn / đổi gói, PT, ngày / mở đơn đang giữ (?order=)
    //   Ai gọi: PtSaleServlet.showPage  ·  Ra: PtSaleView → JSP biến "sale"
    //   Hàm phụ ngay dưới, theo thứ tự prepare gọi:
    //     newView → build → loadHolder · loadPackage · loadPartner · loadPt → loadPlan → buildPlan / checkDay …
    //     loadPendingHolds (danh sách "Đơn đang giữ chỗ") · loadHeld (trang ?order=)
    // ============================================================================================================

    /**
     * HÀM CHÍNH 2 — dựng dữ liệu cho cả màn (mỗi lần mở trang hoặc đổi gói / PT / ngày / khách).
     *   AI GỌI   PtSaleServlet.showPage (GET /receptionist/pt-sales, và khi Giữ lịch bị từ chối)
     *   VÀO      accountId · input = chữ thô trên URL (customer, package, pt, start, t1..t5, sessions)
     *            · orderId = ?order= (null nếu không mở đơn đang giữ)
     *   RA       PtSaleView → Servlet đặt request attribute "sale" → pt-sales.jsp
     *   BƯỚC     1. releaseExpired: nhả đơn giữ quá 15 phút (để bảng tuần thấy đúng khung trống)
     *            2. findReceptionist → không phải lễ tân: MSG07
     *            3a. có orderId → loadHeld (trang "đang giữ": đồng hồ + thu tiền). URL thiếu start → lấy ngày khung giữ đầu tiên
     *            3b. không có → build (4 bước, lock = false) + loadPendingHolds (danh sách "Đơn đang giữ chỗ")
     *   GHI DB   chỉ releaseExpired (nếu có đơn quá hạn); còn lại chỉ đọc, không khóa
     */
    public ServiceResult<PtSaleView> prepare(
            int accountId,
            Input input,
            Integer orderId,
            LocalDateTime now) {
        try {
            
            //B1: Nhả các đơn giữ quá 15'
            releaseExpired(now);
            try (Connection connection = DBContext.getConnection()) {
                
                // B2: Từ accountId đọc từ session => [EmployeeId + PersonId] - lễ tân đanng làm
                int[] receptionist = saleDAO.findReceptionist(connection, accountId); 
                if (receptionist == null) {
                    return ServiceResult.fail(Messages.MSG07_NO_PERMISSION);
                }
                
                // Đọc mã gói + mã PT + mã khách + mã gói,.....
                PtSaleView view = newView(input, now); 
                
                
                // Sau khi đọc và tạo dữ liệu, check nếu orderId khác null thì nghĩa là có OrderId
                // Có orderId nghĩa là lễ tân đang mở một đơn đã giữ chỗ. Khi đó Service không dựng trang chọn nữa mà đọc đơn từ DB rồi dựng trang “đang giữ” (đồng hồ đếm ngược + form thu tiền).
                if (orderId != null) {
                    
                    
                    // 1. xử lý ngày bắt đầu, newView đã tự đặt ngày bắt đầu là ngày mai nếu URL không có start. Nhưng với đơn đang giữ, mặc định “ngày mai” là sai, vì lịch đã giữ có thể bắt đầu ngày khác.
                    if (parseDate(input.start) == null) {
                        view.getChoice().setStartDate(null); // nếu URL có start => giữ nguyên ngày đó, nếu URL ko có start, xóa mặc định là ngày mai để bước sau lấy mặc định start = buổi đầu tiên
                    }
                    
                    // 2. gọi loadHeld dựng trang từ đơn
                    String problem = loadHeld(
                            connection,
                            view,
                            orderId,
                            now);
                    // 3. problem == null: Servlet nhận ok(view) và vẽ trang đang giữ.
                    return problem == null ? ServiceResult.ok(view) : ServiceResult.fail(problem);
                }
                
                
                
                // build lịch planJson 
                build(
                        connection,
                        view,
                        input,
                        receptionist[1],
                        now,
                        false);
                view.setPendingHolds(loadPendingHolds(connection, now));
                return ServiceResult.ok(view);
            }
        } catch (SQLException | IllegalStateException | DateTimeException exception) {
            exception.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }


    /** HÀM PHỤ (prepare, hold) — chữ thô trên URL → PtSaleView rỗng: đọc mã, ngày (trống = ngày mai), giờ t1..t5, min / max ngày bắt đầu. */
    private PtSaleView newView(Input input, LocalDateTime now) {
        PtSaleView view = new PtSaleView();
        PtSaleChoiceBlock choice = view.getChoice(); // choice - DTO lựa chọn mà Lễ Tân chọn - DTO này là 1 filed con của PtSaleView
        PtSaleBuyerBlock buyer = view.getBuyer(); // buyer - DTO của người đứng gói - DTO này là 1 field con của PtSaleView

        buyer.setCustomerId(parseId(input.customer)); // CustomerID
        buyer.setPartnerId(parseId(input.partner)); // PartnerID
        choice.setPackageId(parseId(input.packageId)); // PackageID
        choice.setPtId(parseId(input.pt)); // PT ID
        LocalDate start = parseDate(input.start); // ngày bắt đầu, nếu trống thì rơi vào ngày mai
        choice.setStartDate(start != null ? start : now.toLocalDate().plusDays(1));   // chưa chọn → mặc định ngày mai
        LocalTime[] times = new LocalTime[5];
        for (int i = 0; i < 5; i++) {
            times[i] = parseTime(input.times[i]);
        }
        view.getPlan().setWeekTimes(times); // Lịch tuần
        buyer.setSearchQuery(input.query == null || input.query.isBlank() ? null : input.query.trim()); // câu query
        choice.setMinStart(now.toLocalDate()); // ngày min - hôm nay
        choice.setMaxStart(now.toLocalDate().plusDays(START_DAYS_AHEAD)); // ngày max = ngày min + 30
        return view; // trả về đối tượng view
    }

    /**
     * HÀM PHỤ (prepare, hold) — đi lần lượt các bước; bước nào thiếu / sai thì dừng, bước sau để trống.
     *   loadHolder (khách) → loadPackage (gói) → loadPartner (người tập cùng, chỉ gói đôi) → loadPt (PT + ngày) → loadPlan (lịch)
     *   lock = false khi xem (prepare) · lock = true khi giữ (hold): đọc khung có UPDLOCK và kiểm lại từng buổi.
     */
    // Hàm build ko trả lỗi nhưng có ghi câu lỗi vào view.set...
    // lỗi khách: holderError;
    // lỗi người tập cùng: partnerError;
    // lỗi gói hoặc PT: choiceError.
    private void build(Connection connection, PtSaleView view, Input input, int receptionistPersonId,
            LocalDateTime now, boolean lock) throws SQLException {
        LocalDate today = now.toLocalDate();
        int minAge = configService.getInt(SystemConfigService.MIN_AGE_FOR_PT);

        // Lễ tân TƯ VẤN TRƯỚC: gói, PT, ngày bắt đầu, lịch tập chọn được khi chưa biết khách (cô góp ý 26/10).
        // Khách (+ người tập cùng của gói đôi) chọn lúc nào cũng được ở cột "Đơn bán"; có khách thì lịch
        // tính thêm giờ trùng buổi khác của khách. Giữ lịch bắt buộc đủ cả hai (firstProblem).
        loadHolder(
                connection,
                view,
                today,
                minAge,
                receptionistPersonId);
        boolean packageOk = loadPackage(connection, view);
        if (packageOk && view.getChoice().isCouple()) {
            loadPartner(
                    connection,
                    view,
                    today,
                    minAge,
                    receptionistPersonId);
        }
        if (!packageOk || !loadPt(connection, view)) {
            return;
        }
        loadPlan(
                connection,
                view,
                input,
                now,
                lock);
    }

    /** HÀM PHỤ (build) — người đứng tên: đã chọn (?customer=) → hồ sơ + lý do không bán được; chưa chọn mà có chữ tìm (tắt JS) → danh sách. */
    private void loadHolder(Connection connection, PtSaleView view, LocalDate today, int minAge, int receptionistPersonId)
            throws SQLException {
        PtSaleBuyerBlock buyer = view.getBuyer();

        if (buyer.getCustomerId() == null) {
            if (buyer.getSearchQuery() != null) {
                String digits = buyer.getSearchQuery().replaceAll("\\D", "");
                buyer.setSearchResults(saleDAO.searchCustomers(
                        connection,
                        buyer.getSearchQuery(),
                        digits,
                        today));
                for (PtSaleCustomer row : buyer.getSearchResults()) {
                    row.setBlockReason(whyCannotBuy(
                            row,
                            today,
                            minAge,
                            receptionistPersonId));
                }
                if (buyer.getSearchResults().isEmpty()) {
                    buyer.setHolderError("Không tìm thấy khách nào khớp \"" + buyer.getSearchQuery() + "\".");
                }
            }
            return;
        }
        PtSaleCustomer holder = saleDAO.findCustomer(connection, buyer.getCustomerId(), today);
        if (holder == null) {
            buyer.setHolderError("Không tìm thấy khách này. Tìm lại.");
            return;
        }
        holder.setBlockReason(whyCannotBuy(
                holder,
                today,
                minAge,
                receptionistPersonId));
        buyer.setHolder(holder);
    }

    /** HÀM PHỤ (build) — danh sách gói (ô chọn gói) + gói đang chọn (tên, giá, số tháng, gói đôi?). false = chưa chọn / gói ngừng bán. */
    private boolean loadPackage(Connection connection, PtSaleView view) throws SQLException {
        PtSaleChoiceBlock choice = view.getChoice();

        List<PtSaleOption> options = new ArrayList<>();
        for (PackageRow row : saleDAO.findPtPackages(connection)) {
            String kind = "PTCouple".equals(row.type) ? "Gói đôi" : "1 người";
            options.add(new PtSaleOption(String.valueOf(row.packageId), row.name,
                    money(row.price) + " · " + row.months + " tháng · " + kind));
        }
        choice.setPackages(options);
        if (choice.getPackageId() == null) {
            return false;
        }
        PackageRow pkg = saleDAO.findPackage(connection, choice.getPackageId());
        if (pkg == null) {
            choice.setChoiceError("Gói này không còn bán. Chọn gói khác.");
            return false;
        }
        choice.setPackageName(pkg.name);
        choice.setPrice(pkg.price);
        choice.setMonths(pkg.months);
        choice.setCouple("PTCouple".equals(pkg.type));
        fillVatPreview(choice, pkg.price);
        return true;
    }

    /**
     * HÀM PHỤ (loadPackage) — tách thuế tạm cho trang chọn theo P-10 hiện tại (đơn chưa tồn tại), cùng công thức lúc hold.
     * Đọc P-10 lỗi → để 3 field null: chỉ ẩn 2 dòng thuế, không chặn trang (lúc giữ lịch vẫn báo lỗi như cũ).
     */
    private void fillVatPreview(PtSaleChoiceBlock choice, long price) {
        if (price <= 0) {
            return;
        }
        try {
            int vatRate = configService.getInt(SystemConfigService.VAT_PERCENT);
            long vat = vatAmount(price, vatRate);
            choice.setVatRate(vatRate);
            choice.setVatAmount(vat);
            choice.setPreTaxAmount(price - vat);
        } catch (SQLException | IllegalStateException exception) {
            exception.printStackTrace();
        }
    }

    /** HÀM PHỤ (build, chỉ gói đôi) — người tập cùng: phải chọn, phải khác người đứng tên, phải mua được. */
    private boolean loadPartner(Connection connection, PtSaleView view, LocalDate today, int minAge,
            int receptionistPersonId) throws SQLException {
        PtSaleBuyerBlock buyer = view.getBuyer();

        if (buyer.getPartnerId() == null) {
            buyer.setPartnerError("Gói đôi: tìm và chọn người tập cùng.");
            return false;
        }
        PtSaleCustomer partner = saleDAO.findCustomer(connection, buyer.getPartnerId(), today);
        boolean sameAsHolder = partner != null && buyer.getHolder() != null
                && partner.getCustomerId() == buyer.getHolder().getCustomerId();
        if (partner == null || sameAsHolder) {
            buyer.setPartnerError("Người tập cùng phải là một khách khác.");
            return false;
        }
        partner.setBlockReason(whyCannotBuy(
                partner,
                today,
                minAge,
                receptionistPersonId));
        buyer.setPartner(partner);
        return partner.getBlockReason() == null;
    }

    /** HÀM PHỤ (build) — danh sách PT (bỏ PT là chính khách) + PT đang chọn + kiểm ngày bắt đầu → tính ngày kết thúc kỳ. */
    private boolean loadPt(Connection connection, PtSaleView view) throws SQLException {
        PtSaleChoiceBlock choice = view.getChoice();

        List<PtSaleOption> options = new ArrayList<>();
        for (PtRow row : saleDAO.findWorkingPts(connection)) {
            if (!isCustomerPerson(view, row.personId)) {
                options.add(new PtSaleOption(
                        String.valueOf(row.employeeId),
                        row.fullName,
                        initials(row.fullName),
                        ptDetail(row)));
            }
        }
        choice.setPts(options);
        if (choice.getPtId() == null || choice.getStartDate() == null) {
            return false;
        }
        PtRow pt = saleDAO.findPt(connection, choice.getPtId());
        if (pt == null || isCustomerPerson(view, pt.personId)) {
            choice.setChoiceError("PT này không nhận được gói. Chọn PT khác.");
            return false;
        }
        boolean outOfRange = choice.getStartDate().isBefore(choice.getMinStart()) || choice.getStartDate().isAfter(choice.getMaxStart());
        if (outOfRange) {
            choice.setChoiceError("Ngày bắt đầu phải từ " + DateTimeUtil.formatDate(choice.getMinStart()) + " đến "
                    + DateTimeUtil.formatDate(choice.getMaxStart()) + ".");
            return false;
        }
        choice.setPtName(pt.fullName);
        choice.setCommissionRate(pt.commissionRate);
        choice.setEndDate(DateTimeUtil.calculateEndDateByMonths(choice.getStartDate(), choice.getMonths()));
        return true;
    }

    /** HÀM PHỤ (loadPt) — chữ nhỏ dưới tên PT: "Boxing – Cardio · 4 năm kinh nghiệm". Thiếu phần nào thì bỏ phần đó. */
    private String ptDetail(PtRow row) {
        String years = row.yearsOfExperience > 0 ? row.yearsOfExperience + " năm kinh nghiệm" : null;
        if (row.specialty == null || row.specialty.isBlank()) {
            return years;
        }
        return years == null ? row.specialty : row.specialty + " · " + years;
    }

    /** HÀM PHỤ (loadPt) — personId này có phải khách đứng tên / người tập cùng không (PT không tự bán cho mình). */
    private boolean isCustomerPerson(PtSaleView view, int personId) {
        PtSaleBuyerBlock buyer = view.getBuyer();

        boolean holder = buyer.getHolder() != null && buyer.getHolder().getPersonId() == personId;
        boolean partner = buyer.getPartner() != null && buyer.getPartner().getPersonId() == personId;
        return holder || partner;
    }

    /** HÀM PHỤ (loadPt) — "Đinh Công Sơn" → "ĐS" (chữ đầu họ + chữ đầu tên). */
    static String initials(String fullName) {
        String[] parts = fullName.trim().split("\\s+");
        String first = parts[0].substring(0, 1);
        return parts.length == 1 ? first : first + parts[parts.length - 1].substring(0, 1);
    }

    /**
     * HÀM PHỤ (build) — lịch tập.
     * Xem (prepare): dựng PtSalePlan → JSON cho trình duyệt (pt-sale-plan.js).
     * Giữ (hold, lock = true): kiểm tra lại các buổi lễ tân gửi lên (input.sessions) → view.days.
     */
        /**
     * HÀM PHỤ (build) — bước cuối của build: LỊCH TẬP.
     *   AI GỌI   build ← prepare (lock = false: chỉ xem) · build ← hold (lock = true: bấm "Giữ lịch 15 phút")
     *   VÀO      view (đã có gói, PT, ngày bắt đầu / kết thúc; có khách nếu lễ tân đã chọn)
     *            · input.sessions (các buổi lễ tân chọn, dạng "2026-11-03@09:30,…") · now · lock
     *   RA       lock = false → planBlock.planJson: JSON để pt-sale-plan.js vẽ Lịch mẫu / Từng tuần / Sửa riêng
     *            lock = true  → planBlock.days (các buổi đã kiểm lại) hoặc planBlock.planError (câu lỗi)
     *   BƯỚC     B1. Đọc dữ liệu trong kỳ: khung giờ PT, giờ bận của khách, PT nghỉ phép, ngày đổi giờ mở cửa
     *            B2. Chuẩn bị: giờ PT đã công bố, giờ mở cửa, các buổi lễ tân gửi lên, thứ PT chưa công bố
     *            ── XEM (lock = false) ──
     *            B3. Lọc giờ lễ tân tự nhập: hợp lệ → thêm vào lịch, sai → gom lại để báo
     *            B4. buildPlan: mỗi ngày T2–T6 trong kỳ × mỗi giờ → xếp được không + lý do
     *            B5. Ghi thêm dữ liệu cho JS, đổi sang JSON → return
     *            ── GIỮ LỊCH (lock = true) ──
     *            B6. Gom giờ chọn được = giờ PT công bố + giờ tự nhập hợp lệ
     *            B7. Kiểm lại TỪNG buổi gửi lên (checkRequested) → sai thì planError, dừng
     *            B8. Ghi danh sách buổi, số buổi, giá mỗi buổi vào view
     *   GHI DB   không — chỉ đọc (lock = true thì đọc khung giờ PT có khóa UPDLOCK)
     */
    private void loadPlan(Connection connection, PtSaleView view, Input input, LocalDateTime now, boolean lock)
            throws SQLException {
        PtSaleChoiceBlock choice = view.getChoice();
        PtSaleBuyerBlock buyer = view.getBuyer();
        PtSalePlanBlock planBlock = view.getPlan();

        // ===== B1: ĐỌC DỮ LIỆU TRONG KỲ (từ ngày bắt đầu đến ngày kết thúc) =====
        int minutes = configService.getInt(SystemConfigService.SESSION_DURATION_MINUTES);   // thời lượng 1 buổi
        LocalDate start = choice.getStartDate();
        LocalDate end = choice.getEndDate();

        // B1a: các khung giờ của PT trong kỳ (lock = true → khóa để 2 lễ tân không giữ trùng)
        List<PtScheduleItem> slots = saleDAO.findPtSlots(
                connection,
                choice.getPtId(),
                start,
                end,
                lock);

        // B1b: giờ bận của khách — chỉ tính khi đã chọn khách (tư vấn trước → chưa có khách → chỉ xét lịch PT)
        List<TimeRange> busy = new ArrayList<>();
        PtSaleCustomer holder = buyer.getHolder();
        if (holder != null) {
            int partnerId = buyer.getPartner() == null ? holder.getCustomerId() : buyer.getPartner().getCustomerId();
            busy = saleDAO.findCustomerBusy(
                    connection,
                    holder.getCustomerId(),
                    partnerId,
                    start,
                    end);
        }

        // B1c: ngày PT nghỉ phép + ngày gym đổi giờ mở cửa
        List<LeaveInfo> leaves = scheduleDAO.findLeaves(
                connection,
                choice.getPtId(),
                start,
                end);
        List<HourException> exceptions = scheduleDAO.findHourExceptions(connection, start, end);

        // ===== B2: CHUẨN BỊ GIỜ CHỌN ĐƯỢC =====
        // Giờ chọn được = giờ PT đã công bố (14 ngày đầu kỳ) + giờ LỄ TÂN TỰ NHẬP (lịch mẫu t1..t5 + sửa riêng trong sessions).
        // Giờ tự nhập sẽ thành buổi "create" (PT chưa mở khung) → lúc Giữ lịch, hold() bắt tích "Đã xác nhận với PT".
        List<LocalTime> published = candidateTimes(
                slots,
                start,
                minutes,
                now);
        LocalTime[] hours = PtScheduleService.parseOpeningHours(configService.getString(SystemConfigService.OPENING_HOURS)); // [mở cửa, đóng cửa]
        Map<LocalDate, LocalTime> requested = parseSessions(input.sessions);                    // ngày → giờ lễ tân chọn
        // Thứ PT chưa công bố khung nào → chỉ là gợi ý (nhãn "PT chưa công bố"), KHÔNG chặn: lễ tân vẫn tự nhập giờ được.
        List<Integer> unpublished = unpublishedWeekdays(slots, start, now);
        planBlock.setUnpublishedWeekdays(unpublished);

        // ============================================================================================
        // NHÁNH XEM (lock = false) — mở trang / đổi gói, PT, ngày, khách → dựng JSON cho JS vẽ lịch
        // ============================================================================================
        if (!lock) { // !lock => true => câu lệnh bên dưới chạy 
            // ===== B3: LỌC GIỜ LỄ TÂN TỰ NHẬP =====
            // Giờ tự nhập CHỈ áp đúng chỗ lễ tân thêm: giờ lịch mẫu Thứ X → các ngày Thứ X; giờ sửa riêng → đúng ngày đó.
            Set<String> invalid = new LinkedHashSet<>();          // giờ sai → báo ở B5

            // B3a: giờ lịch mẫu (Thứ 2..Thứ 6) không nằm trong giờ PT công bố
            Map<Integer, LocalTime> weekdayExtra = new HashMap<>();
            LocalTime[] weekTimes = planBlock.getWeekTimes();
            for (int i = 0; i < 5; i++) {
                LocalTime time = weekTimes[i];
                if (time == null || published.contains(time)) {
                    continue;                                     // Nghỉ hoặc giờ PT đã mở → không cần kiểm
                }
                if (isValidStartTime(time, minutes, hours[0], hours[1])) {
                    weekdayExtra.put(i + 1, time);                // hợp lệ → thêm cho đúng thứ đó
                } else {
                    invalid.add(DateTimeUtil.formatTime(time));
                }
            }

            // B3b: giờ sửa riêng từng ngày
            Map<LocalDate, LocalTime> dateExtra = new HashMap<>();
            if (requested != null) {
                requested.forEach((date, time) -> {
                    if (isValidStartTime(time, minutes, hours[0], hours[1])) {
                        dateExtra.put(date, time);                // hợp lệ → thêm cho đúng ngày đó
                    } else {
                        invalid.add(DateTimeUtil.formatTime(time));
                    }
                });
            }

            // ===== B4: DỰNG LỊCH — mỗi ngày T2–T6 × mỗi giờ → checkDay: xếp được không + lý do =====
            // (khách trùng giờ, PT có khách / chồng giờ, đơn khác đang giữ, đã qua giờ, PT nghỉ…) — cùng luật lúc Giữ lịch.
            PtSalePlan plan = buildPlan(start, end, minutes, choice.getPrice(), published, weekdayExtra, dateExtra,
                    slots, leaves, exceptions, busy, now);

            // ===== B5: GHI THÊM DỮ LIỆU CHO JS, ĐỔI SANG JSON =====
            plan.setUnpublished(unpublished);
            plan.setPublished(formatTimes(published));                       // JS: giờ ngoài danh sách này = "PT chưa mở"
            plan.setOpen(DateTimeUtil.formatTime(hours[0]));                 // min cho ô nhập giờ tự do
            plan.setClose(DateTimeUtil.formatTime(hours[1].minusMinutes(minutes))); // max = giờ bắt đầu muộn nhất hợp lệ (đóng cửa − thời lượng buổi)

            // B5a: lịch mẫu chọn sẵn cho 5 thứ (giờ dùng được mới chọn sẵn) + giờ tự nhập của từng thứ
            for (int i = 0; i < 5; i++) {
                LocalTime time = weekTimes[i];
                boolean usable = time != null && (published.contains(time) || weekdayExtra.containsKey(i + 1));
                plan.getPattern()[i] = usable ? DateTimeUtil.formatTime(time) : null;
                LocalTime extra = weekdayExtra.get(i + 1);
                plan.getCustom()[i] = extra == null ? null : DateTimeUtil.formatTime(extra);
            }

            // B5b: khôi phục các buổi sửa riêng (F5, chọn khách, Giữ lịch bị từ chối)
            if (requested != null) {
                requested.forEach((date, time) -> plan.getRequested().add(date + "@" + DateTimeUtil.formatTime(time)));
            }

            // B5c: có giờ tự nhập sai → câu nhắc trên màn
            if (!invalid.isEmpty()) {
                plan.setNotice("Giờ " + String.join(", ", invalid) + " không dùng được: giờ bắt đầu phải từ "
                        + plan.getOpen() + " đến " + plan.getClose() + " (buổi " + minutes
                        + " phút kết thúc trước giờ đóng cửa) và theo mốc 15 phút (:00, :15, :30, :45).");
            }

            // B5d: đổi sang JSON → JSP in vào <script id="sale-plan-data"> → pt-sale-plan.js vẽ lịch
            planBlock.setPlanJson(JsonUtil.toJson(plan));
            return;
        }

        // ============================================================================================
        // NHÁNH GIỮ LỊCH (lock = true) — bấm "Giữ lịch 15 phút" → kiểm lại từng buổi trên server
        // ============================================================================================

        // ===== B6: GOM GIỜ CHỌN ĐƯỢC = giờ PT công bố + giờ tự nhập hợp lệ =====
        List<LocalTime> custom = new ArrayList<>();
        for (LocalTime time : planBlock.getWeekTimes()) {
            custom.add(time);                       // giờ lịch mẫu (t1..t5)
        }
        if (requested != null) {
            custom.addAll(requested.values());      // giờ sửa riêng từng buổi (sessions)
        }
        List<LocalTime> candidates = mergeCustomTimes(
                published,
                custom,
                minutes,
                hours[0],
                hours[1]);

        // ===== B7: KIỂM LẠI TỪNG BUỔI LỄ TÂN GỬI LÊN (JS chỉ để xem nhanh, server mới quyết định) =====
        // B7a: không đọc được danh sách buổi → báo lỗi, dừng
        if (requested == null) {
            planBlock.setPlanError("Không đọc được lịch vừa chọn. Chọn lại lịch rồi bấm Giữ lịch.");
            return;
        }
        // B7b: checkRequested kiểm từng buổi bằng cùng luật checkDay; buổi nào sai → ném lỗi kèm câu báo
        List<PtSaleDay> days;
        try {
            days = checkRequested(
                    requested,
                    start,
                    end,
                    minutes,
                    candidates,
                    slots,
                    leaves,
                    exceptions,
                    busy,
                    now);
        } catch (IllegalArgumentException problem) {
            planBlock.setPlanError(problem.getMessage());   // → chữ đỏ ở khung Lịch tập
            return;
        }
        // B7c: số buổi lưu ở cột TINYINT (tối đa 255) → vượt là lỗi hệ thống
        if (days.size() > 255) {
            throw new IllegalStateException("Số buổi vượt giới hạn TINYINT");
        }

        // ===== B8: GHI KẾT QUẢ VÀO VIEW → hold() dùng để ghi TimeSlots =====
        planBlock.setDays(days);
        planBlock.setSessionCount(days.size());
        planBlock.setSessionValue(sessionValue(choice.getPrice(), days.size()));   // giá mỗi buổi = giá gói / số buổi
    }

    /**
     * HÀM PHỤ (loadPlan) — giờ của "lịch mẫu" = giờ bắt đầu của các khung Free (đúng P-06 phút, chưa qua giờ) PT đã công bố
     * vào T2–T6 trong 14 ngày đầu kỳ (CBR-52: lịch phải nằm trong giờ PT đã công bố). Xếp tăng dần.
     */
    static List<LocalTime> candidateTimes(
            List<PtScheduleItem> slots,
            LocalDate start,
            int minutes,
            LocalDateTime now) {
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
     * HÀM PHỤ (loadPlan) — thứ (1 = Thứ 2 … 5 = Thứ 6) mà PT CHƯA công bố khung Free nào (chưa qua giờ) trong 14 ngày đầu kỳ.
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
     * HÀM PHỤ (loadPlan) — gộp giờ lễ tân tự chọn (customTimes = giờ từ lịch mẫu t1..t5 + giờ sửa riêng từng buổi trong sessions)
     * vào danh sách giờ chọn được. Chỉ giữ giờ HỢP LỆ (isValidStartTime) → giờ này sẽ thành buổi "create"
     * (cần tick "Đã xác nhận với PT"). Nhờ vậy lễ tân bán được gói ở giờ PT chưa kịp công bố khung.
     */
    static List<LocalTime> mergeCustomTimes(List<LocalTime> published, List<LocalTime> customTimes, int minutes,
            LocalTime open, LocalTime close) {
        TreeSet<LocalTime> merged = new TreeSet<>(published);
        if (customTimes != null) {
            for (LocalTime time : customTimes) {
                if (isValidStartTime(time, minutes, open, close)) {
                    merged.add(time);
                }
            }
        }
        return new ArrayList<>(merged);
    }

    /**
     * HÀM PHỤ (mergeCustomTimes) — giờ bắt đầu hợp lệ cho 1 buổi PT do LỄ TÂN TỰ NHẬP (không cần PT đã công bố khung):
     *   trong giờ mở cửa (P-19), đúng mốc 15 phút, và khung (giờ → giờ + minutes) vẫn kết thúc trước giờ đóng cửa.
     * open/close null (P-19 lỗi cấu hình) → không giờ nào hợp lệ.
     */
    static boolean isValidStartTime(
            LocalTime time,
            int minutes,
            LocalTime open,
            LocalTime close) {
        if (time == null || open == null || close == null) {
            return false;
        }
        if (minutes <= 0 || minutes >= 24 * 60) {
            return false;
        }
        LocalTime end = time.plusMinutes(minutes);
        if (time.isBefore(open) || !end.isAfter(time) || end.isAfter(close)) {
            return false;
        }
        return time.toSecondOfDay() % (15 * 60) == 0;   // cùng mốc 15 phút với khung PT công bố
    }

    /** HÀM PHỤ (loadPlan) — [18:00, 06:00] → ["06:00", "18:00"] (xếp tăng dần). Cho PtSalePlan.published — JS tô xanh giờ PT đã mở. */
    static List<String> formatTimes(List<LocalTime> times) {
        List<String> texts = new ArrayList<>();
        for (LocalTime time : times) {
            texts.add(DateTimeUtil.formatTime(time));
        }
        return texts;
    }

    /**
     * HÀM PHỤ (loadPlan khi xem) — dữ liệu Lịch tập cho trình duyệt: mỗi ngày T2–T6 trong kỳ × mỗi giờ chọn được → có buổi được không (+ lý do).
     * Trình duyệt ghép "lịch mẫu" + "sửa từng buổi" trên dữ liệu này; hold() vẫn kiểm tra lại bằng checkRequested.
     */
    static PtSalePlan buildPlan(LocalDate start, LocalDate end, int minutes, long price, List<LocalTime> candidates,
            List<PtScheduleItem> slots, List<LeaveInfo> leaves, List<HourException> exceptions,
            List<TimeRange> busy, LocalDateTime now) {
        return buildPlan(
                start,
                end,
                minutes,
                price,
                candidates,
                Map.of(),
                Map.of(),
                slots,
                leaves,
                exceptions,
                busy,
                now);
    }

    /**
     * Như trên, thêm giờ LỄ TÂN TỰ NHẬP đúng chỗ: weekdayExtra (1 = Thứ 2 … → giờ lịch mẫu tự nhập của thứ đó)
     * và dateExtra (ngày → giờ sửa riêng tự nhập). Giờ tự nhập KHÔNG lan sang thứ / ngày khác.
     */
    static PtSalePlan buildPlan(LocalDate start, LocalDate end, int minutes, long price, List<LocalTime> candidates,
            Map<Integer, LocalTime> weekdayExtra, Map<LocalDate, LocalTime> dateExtra,
            List<PtScheduleItem> slots, List<LeaveInfo> leaves, List<HourException> exceptions,
            List<TimeRange> busy, LocalDateTime now) {
        PtSalePlan plan = new PtSalePlan();
        plan.setStart(start.toString());
        plan.setEnd(end.toString());
        plan.setPrice(price);
        plan.setMinutes(minutes);
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
            TreeSet<LocalTime> times = new TreeSet<>(dayTimes(candidates, sameDay, minutes));
            LocalTime weekdayTime = weekdayExtra.get(date.getDayOfWeek().getValue());
            if (weekdayTime != null) {
                times.add(weekdayTime);
            }
            LocalTime dateTime = dateExtra.get(date);
            if (dateTime != null) {
                times.add(dateTime);
            }
            for (LocalTime time : times) {
                PtSaleDay check = checkDay(
                        date,
                        time,
                        minutes,
                        sameDay,
                        leaves,
                        exceptions,
                        busy,
                        now);
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
     * HÀM PHỤ (buildPlan, checkRequested) — giờ chọn được trong 1 ngày = giờ của lịch mẫu (candidates) + khung Free đúng thời lượng PT công bố riêng ngày đó.
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
     * HÀM PHỤ (buildPlan, checkRequested) — 1 ngày + 1 giờ → có buổi được không (luật DUY NHẤT, dùng cả lúc xem lẫn lúc giữ chỗ).
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
        String skip = whySkip(
                date,
                time,
                endTime,
                sameDay,
                busy,
                now);
        if (skip != null) {
            return new PtSaleDay(
                    date,
                    label,
                    time,
                    endTime,
                    "skip",
                    null,
                    skip,
                    null);
        }
        PtScheduleItem exact = null;
        for (PtScheduleItem slot : sameDay) {
            if (slot.getStartTime().equals(time) && slot.getEndTime().equals(endTime) && "Free".equals(slot.getKind())) {
                exact = slot;
            }
        }
        String warning = warningFor(
                date,
                time,
                endTime,
                leaves,
                exceptions);
        return new PtSaleDay(date, label, time, endTime, exact != null ? "book" : "create",
                exact == null ? null : exact.getTimeSlotId(), null, warning);
    }

    /** HÀM PHỤ (checkDay) — lý do ngày + giờ này KHÔNG xếp buổi được; null = được. Chữ này in ra ô lịch ("Chưa xếp được"). */
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

    /** HÀM PHỤ (checkDay) — có buổi nhưng cảnh báo vàng: phòng nghỉ cả ngày / PT có đơn nghỉ trùng giờ. */
    /**
     * HÀM PHỤ (checkDay) — nhãn vàng cho buổi VẪN ĐƯỢC TẠO nhưng PT cần dời (BR-40, CBR-56, CBR-124); null = không cảnh báo.
     *   phòng đóng cả ngày · buổi nằm ngoài giờ mở cửa riêng của ngày đó (VD lễ chỉ mở 08:00–17:00, buổi 18:00)
     *   · PT có đơn nghỉ ca đó.
     * Bản cũ chỉ xét "đóng cả ngày" → buổi ngoài giờ riêng không được đánh dấu, JOB-04 hủy GymClosed mà lễ tân không biết.
     */
    private static String warningFor(
            LocalDate date,
            LocalTime time,
            LocalTime endTime,
            List<LeaveInfo> leaves,
            List<HourException> exceptions) {
        for (HourException exception : exceptions) {
            if (!date.equals(exception.getApplyDate())) {
                continue;
            }
            if (exception.isClosedAllDay()) {
                return "Phòng nghỉ ngày này — PT cần dời buổi";
            }
            boolean outsideHours = exception.getOpenTime() != null && exception.getCloseTime() != null
                    && (time.isBefore(exception.getOpenTime()) || endTime.isAfter(exception.getCloseTime()));
            if (outsideHours) {
                return "Ngày này phòng chỉ mở " + DateTimeUtil.formatTime(exception.getOpenTime()) + "–"
                        + DateTimeUtil.formatTime(exception.getCloseTime()) + " — PT cần dời buổi";
            }
        }
        PtScheduleItem candidate = new PtScheduleItem();
        candidate.setSlotDate(date);
        candidate.setStartTime(time);
        return PtScheduleService.isOnLeave(candidate, leaves) ? "PT có đơn nghỉ — PT cần dời buổi" : null;
    }

    /** HÀM PHỤ — gom khung của PT theo ngày (buildPlan + checkRequested dùng). */
    static Map<LocalDate, List<PtScheduleItem>> slotsByDate(List<PtScheduleItem> slots) {
        Map<LocalDate, List<PtScheduleItem>> byDate = new HashMap<>();
        for (PtScheduleItem slot : slots) {
            byDate.computeIfAbsent(slot.getSlotDate(), k -> new ArrayList<>()).add(slot);
        }
        return byDate;
    }

    /**
     * HÀM PHỤ (prepare) — gọi ở cuối prepare (khi KHÔNG mở đơn cụ thể ?order=).
     * Liệt kê đơn tại quầy đang giữ chỗ chưa hết hạn → JSP hiện danh sách "Đơn đang giữ" để lễ tân tìm lại
     * sau khi bấm Back / đăng xuất mà mất URL ?order= (không mất dữ liệu, chỉ mất đường dẫn).
     */
    private List<PtSalePendingHold> loadPendingHolds(Connection connection, LocalDateTime now) throws SQLException {
        List<PtSaleDAO.HeldOrder> active = saleDAO.findActiveHoldOrders(connection, now);
        List<PtSalePendingHold> holds = new ArrayList<>();
        for (PtSaleDAO.HeldOrder order : active) {
            long secondsLeft = Math.max(0, Duration.between(now, order.expiresAt).getSeconds());
            holds.add(new PtSalePendingHold(order.orderId, order.orderCode, secondsLeft));
        }
        return holds;
    }

    /** HÀM PHỤ (prepare có ?order=) — dựng màn từ đơn đang giữ (không cần URL đủ 6 lựa chọn). Đơn hết giữ → câu lỗi. */
    private String loadHeld(
            Connection connection,
            PtSaleView view,
            int orderId,
            LocalDateTime now) throws SQLException {
        PtSaleChoiceBlock choice = view.getChoice();
        PtSaleBuyerBlock buyer = view.getBuyer();
        PtSalePlanBlock planBlock = view.getPlan();
        PtSaleHeldBlock heldBlock = view.getHeldOrder();

        
        // B1: Đọc đơn từ DB bằng saleDAO.findHeldOrder(..., false): lock = false = chỉ xem ko khóa dòng
        HeldOrder held = saleDAO.findHeldOrder(connection, orderId, false);
        
        // Ktra đơn còn giữ, đơn còn hạn, còn tồn tại:
        if (held == null || !"Pending".equals(held.status) || held.slots.isEmpty() || held.expiresAt == null) {
            return String.format(Messages.MSG108_HOLD_NO_LONGER_VALID, HOLD_MINUTES);
        }
        
        // Đổ thông tin đơn vào view: mã đơn, số giây còn lại cho đồng hồ, khách, người tập cùng (nếu là gói đôi), tên gói, giá, PT.
        LocalDate today = now.toLocalDate();
        PackageRow pkg = saleDAO.findPackage(connection, held.packageId);
        PtRow pt = saleDAO.findPt(connection, held.ptId);
        heldBlock.setOrderId(orderId);
        // mã đơn
        heldBlock.setOrderCode(held.orderCode);
        // số giây còn lại
        heldBlock.setHoldSeconds(Math.max(0, Duration.between(now, held.expiresAt).getSeconds()));
        // Tách thuế lấy từ SNAPSHOT trên đơn, không tính lại theo P-10 hiện tại (CBR-09, DBR-57).
        heldBlock.setVatRate(held.vatRate);
        heldBlock.setVatAmount(held.vatAmount);
        heldBlock.setPreTaxAmount(held.price - held.vatAmount);
        //người đứng tên
        buyer.setHolder(saleDAO.findCustomer(connection, held.holderId, today));
        buyer.setCustomerId(held.holderId);
        // người tập cùng
        if (held.partnerId != null) {
            buyer.setPartner(saleDAO.findCustomer(connection, held.partnerId, today));
        }
        choice.setPackageId(held.packageId);
        
        // Tên gói 
        choice.setPackageName(pkg == null ? "Gói PT" : pkg.name);
        // Số tháng
        choice.setMonths(pkg == null ? 0 : pkg.months);
        // giá gói
        choice.setPrice(held.price);
        // PT được chọn
        choice.setPtId(held.ptId);
        // Tên đầy đủ của PT được chọn
        choice.setPtName(pt == null ? "PT" : pt.fullName);
        
        
        // ngày bắt đầu
        if (choice.getStartDate() == null) {
            choice.setStartDate(held.slots.get(0).getSlotDate());
        }
        //ngày kết thúc được tính từ ngày bắt đầu
        choice.setEndDate(DateTimeUtil.calculateEndDateByMonths(choice.getStartDate(), choice.getMonths()));

        
        
        // Dựng lịch: mỗi khung đang giữ thành 1 dòng buổi tập, giờ xếp theo thứ trong tuần.
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
        planBlock.setWeekTimes(times);
        planBlock.setDays(days);
        planBlock.setSessionCount(days.size());
        planBlock.setSessionValue(sessionValue(held.price, days.size()));
        return null; // null = ko có lỗi
    }


    // ============================================================================================================
    // HÀM CHÍNH 3. hold — nút "Giữ lịch 15 phút" (POST action=hold)
    //   Ai gọi: PtSaleServlet.doPost  ·  Ra: mã đơn (Servlet redirect ?order=…) hoặc câu lỗi
    //   Dùng lại build/loadPlan của prepare (lock = true). Hàm phụ riêng ngay dưới:
    //     firstProblem · countNewSlots · checkRequested · parseSessions · openShiftId
    // ============================================================================================================

    /**
     * HÀM CHÍNH 3 — nút "Giữ lịch 15 phút".
     *   AI GỌI   PtSaleServlet.doPost (action=hold)
     *   VÀO      accountId · input (giống prepare + sessions = "2026-11-03@09:30,…" + confirmPt) · now
     *   RA       ok(mã đơn, MSG307) → Servlet redirect ?order=… · fail(câu lỗi) → Servlet hiện chữ đỏ "saleError"
     *   BƯỚC (tất cả trong 1 transaction — lỗi ở đâu cũng rollback, không ghi gì)
     *            1. releaseExpired (ngoài transaction)
     *            2. requireReceptionist → lockPt: KHÓA dòng PT, lễ tân khác / PT đang Công bố phải chờ
     *            3. build(lock = true): làm lại các bước + checkRequested kiểm lại TỪNG buổi gửi lên
     *            4. firstProblem ≠ null → từ chối · có buổi "PT chưa mở" mà không tích xác nhận → từ chối
     *            5. openShiftId: chưa mở ca → MSG109, ca ngày trước → MSG324
     *            6. nextOrderCode + insertPendingOrder        → Orders: thêm 1 dòng Pending
     *            7. mỗi buổi: "book" → holdFreeSlot           → TimeSlots: Free → Held (+ HoldExpiresAt = now + 15')
     *                         "create" → insertHeldSlot       → TimeSlots: thêm 1 dòng Held
     */
    public ServiceResult<Integer> hold(int accountId, Input input, LocalDateTime now) {
        try {
            
            // B1: Nhả slot đã hết hạn
            releaseExpired(now);
        } catch (SQLException exception) {
            exception.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
        
        
        // Mở Transaction
        return inTransaction(connection -> {
            
            
            // B2: Tìm lễ tân
            int[] receptionist = requireReceptionist(connection, accountId);
            
            // B3: Tạo đối tượng view trước
            PtSaleView view = newView(input, now);
            PtSaleChoiceBlock choice = view.getChoice();
            PtSaleBuyerBlock buyer = view.getBuyer();
            PtSalePlanBlock planBlock = view.getPlan();

            
            
            // B4: Nếu đã chọn PT => Khóa PT đó để xử lý, các lễ tân khác + PT đang công bố sẽ phải xếp hàng do UPDATE LOCK mechanism xử lý race condition
            // lockPt chạy SELECT dòng PT trong Employees với UPDLOCK, HOLDLOCK để khóa tới hết transaction. Lễ tân khác giữ lịch cùng PT, hoặc PT đang công bố lịch, phải chờ. Người sau đọc lại khung thấy đã Held nên bị từ chối, không trùng lịch. Khóa dòng PT vì buổi create chưa có khung để khóa
            // UPDLOCK = treo biển “Đang có người dùng” lên cửa. Người khác vẫn được nhìn (đọc thường), nhưng không ai treo biển thứ hai được. Ai cũng muốn vào sửa thì đứng ngoài chờ.
            // HOLDLOCK = giữ biển tới khi làm xong hết việc (commit hoặc rollback). Không có HOLDLOCK thì biển bị gỡ ngay sau câu SELECT, chưa kịp làm gì đã mất khóa.
            if (choice.getPtId() != null) {
                saleDAO.lockPt(connection, choice.getPtId());      // xếp hàng với lễ tân khác / PT đang Công bố
            }
            
            //B5: build lịch để vẽ màn hình hold buổi, lokc=true: Thêm UPLOCK, HOLDLOCK
            build(
                    connection,
                    view,
                    input,
                    receptionist[1],
                    now,
                    true);
            
            
            // B6: Trả về message lỗi đầu tiên
            String problem = firstProblem(view);
            if (problem != null) {
                throw new IllegalArgumentException(problem);
            }
            
            
            // B6: Buổi PT chưa mở khung (giữ lịch sẽ tự tạo khung mới) → lễ tân phải hỏi PT rồi tích xác nhận.
            int unconfirmed = countNewSlots(planBlock.getDays()); // Đếm các buổi nhập thêm 
            if (unconfirmed > 0 && !input.confirmPt) {
                throw new IllegalArgumentException("Có " + unconfirmed + " buổi PT chưa mở khung giờ đó. "
                        + "Hỏi PT rồi tích \"Đã xác nhận với PT\" trước khi giữ lịch.");
            }

            // Ca đang mở (đơn tại quầy bắt buộc thuộc 1 ca). Chưa mở ca → MSG109, giống Bán hàng tại quầy.
            int shiftId = openShiftId(Messages.MSG109_SHIFT_NOT_OPEN_FOR_SALE);
            PackageRow pkg = saleDAO.findPackage(connection, choice.getPackageId());
            int vatRate = configService.getInt(SystemConfigService.VAT_PERCENT);
            String code = saleDAO.nextOrderCode(connection, now.getYear());
            Integer partnerId = buyer.getPartner() == null ? null : buyer.getPartner().getCustomerId();
            int orderId = saleDAO.insertPendingOrder(connection, code, buyer.getHolder().getCustomerId(), partnerId, pkg,
                    receptionist[0], shiftId, vatRate, vatAmount(pkg.price, vatRate), now);

            LocalDateTime expires = now.plusMinutes(HOLD_MINUTES);
            
            
            // Duyệt qua từng khung ngày + giờ
            for (PtSaleDay day : planBlock.getDays()) {
                if (!day.isSession()) { // ktra lại cho chắc
                    continue;
                }
                
                
                // nếu action khung đó = book thì đổi dòng TimeSlots từ Free thành Held, nếu ko thì sẽ thêm mới status = Held thẳng vào database
                if ("book".equals(day.getAction())) {
                    if (!saleDAO.holdFreeSlot(connection, day.getSlotId(), choice.getPtId(), orderId, expires)) {
                        throw new IllegalArgumentException(day.getDayLabel() + ": " + Messages.MSG103_SLOT_ALREADY_TAKEN);
                    }
                } else {
                    saleDAO.insertHeldSlot(connection, choice.getPtId(), day.getDate(), day.getStartTime(), day.getEndTime(),
                            orderId, expires, now);
                }
            }
            return ServiceResult.ok(orderId, String.format(
                    Messages.MSG307_PT_SALE_HELD,
                    planBlock.getSessionCount(),
                    HOLD_MINUTES,
                    code));
        });
    }

    /** HÀM PHỤ (hold) — câu lỗi đầu tiên chặn việc giữ chỗ (theo thứ tự bước); null = giữ được. */
    static String firstProblem(PtSaleView view) {
        PtSaleChoiceBlock choice = view.getChoice();
        PtSaleBuyerBlock buyer = view.getBuyer();
        PtSalePlanBlock planBlock = view.getPlan();

        if (buyer.getHolder() == null) {
            return buyer.getHolderError() != null ? buyer.getHolderError() : "Chọn khách trước.";
        }
        if (buyer.getHolder().getBlockReason() != null) {
            return buyer.getHolder().getBlockReason();
        }
        if (buyer.getPartnerError() != null) {
            return buyer.getPartnerError();
        }
        if (buyer.getPartner() != null && buyer.getPartner().getBlockReason() != null) {
            return buyer.getPartner().getBlockReason();
        }
        if (choice.getChoiceError() != null) {
            return choice.getChoiceError();
        }
        if (choice.getEndDate() == null) {
            return "Chọn đủ gói, PT và ngày bắt đầu.";
        }
        if (planBlock.getPlanError() != null) {
            return planBlock.getPlanError();
        }
        return view.isReady() ? null : "Chọn ít nhất 1 buổi tập.";
    }

    /**
     * HÀM PHỤ (hold) — đếm số buổi "create" = ngày đó PT CHƯA có khung đúng giờ này → giữ lịch sẽ tự tạo khung mới.
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

    /**
     * HÀM PHỤ (loadPlan khi lock = true) — kiểm tra lại TỪNG buổi lễ tân gửi lên bằng đúng luật checkDay, trên dữ liệu đã khóa.
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
            PtSaleDay day = checkDay(
                    date,
                    time,
                    minutes,
                    sameDay,
                    leaves,
                    exceptions,
                    busy,
                    now);
            if (!day.isSession()) {
                throw new IllegalArgumentException(label + " lúc " + DateTimeUtil.formatTime(time) + ": "
                        + day.getReason() + ". Chọn lại buổi này.");
            }
            days.add(day);
        }
        return days;
    }

    /**
     * HÀM PHỤ (loadPlan) — ô ẩn "sessions" → các buổi. "2026-10-27@09:30,2026-10-29@18:00" → {27/10 → 09:30, 29/10 → 18:00}, xếp theo ngày.
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

    /**
     * HÀM PHỤ (hold, pay) — ca đang mở để ghi đơn — cùng cách với CounterSaleService: lấy ca hiện tại của hôm nay,
     * chưa có ca hoặc ca chưa Open / đã chốt → từ chối bằng câu lỗi truyền vào (MSG109 khi giữ lịch, MSG110 khi thu tiền);
     * ca đang mở là ca ngày trước → MSG324 (CBR-123).
     * Hệ thống KHÔNG tự mở ca hộ lễ tân (CBR-105, BR-93): lễ tân mở ca ở màn Ca làm việc.
     */
    private int openShiftId(String notOpenMessage) throws SQLException {
        Shift shift = shiftDAO.findCurrentOrActiveShift(DateTimeUtil.today());
        if (shift == null || !shift.isOpen()) {
            throw new IllegalArgumentException(notOpenMessage);
        }
        // CBR-123: ca đang mở là ca ngày trước (quên chốt) → không ghi đơn hôm nay vào ca đó.
        String pastDayShift = ShiftService.pastDayShiftMessage(shift, DateTimeUtil.today());
        if (pastDayShift != null) {
            throw new IllegalArgumentException(pastDayShift);
        }
        return shift.getShiftId();
    }


        /**
     * HÀM CHÍNH 4 — nút "Xác nhận đã thu tiền" ở trang đơn đang giữ (?order=).
     *   AI GỌI   PtSaleServlet.doPost (action=pay)
     *   VÀO      accountId  : tài khoản lễ tân đang đăng nhập (lấy từ session)
     *            orderId    : ô ẩn "order"  = mã đơn đang giữ
     *            startText  : ô ẩn "start"  = ngày bắt đầu kỳ (bảng Orders không lưu ngày này nên phải gửi lên)
     *            method     : "Cash" | "Transfer"
     *            reference  : mã giao dịch chuyển khoản (không bắt buộc)
     *            now        : giờ hiện tại
     *   RA       ok(MSG310)  → Servlet: toast xanh, về trang bán gói trống
     *            fail(lỗi)   → Servlet: toast đỏ, quay lại ?order=… để thử lại
     *
     *   BƯỚC (ngoài transaction — chỉ kiểm dữ liệu gửi lên, chưa đụng DB)
     *     B0  method sai → MSG308 · mã giao dịch > 50 ký tự → MSG309 · đổi startText thành LocalDate
     *   BƯỚC (trong 1 transaction — 1 bước lỗi thì rollback tất cả)
     *     B1  requireReceptionist   lấy EmployeeId lễ tân (người nhận tiền)
     *     B2  findHeldOrder(lock)   đọc + KHÓA đơn và các khung đang giữ
     *     B3  whyCannotPay          đơn còn Pending? còn hạn 15'? ngày bắt đầu khớp lịch giữ?
     *     B4  openShiftId           phải có ca đang mở hôm nay
     *     B5  findPackage           gói còn bán không
     *     B6  markOrderPaid + insertTransaction    Orders: Pending → Paid · Transactions: +1 dòng Success
     *     B7  insertSubscription                   Subscriptions: +1 gói Active
     *     B8  mỗi khung: bookHeldSlot + insertSession   TimeSlots: Held → Booked · Sessions: +1 buổi Scheduled
     *     B9  notifySale                           thông báo cho khách (+ người tập cùng) và PT
     */
    public ServiceResult<Integer> pay(int accountId, int orderId, String startText, String method, String reference,
            LocalDateTime now) {
        return pay(accountId, orderId, startText, method, reference, true, now);
    }

    /** Như trên, kèm receivedInFull = ô "Đã nhận đủ" đã tích (CBR-77: bắt buộc khi chuyển khoản → MSG326). */
    public ServiceResult<Integer> pay(int accountId, int orderId, String startText, String method, String reference,
            boolean receivedInFull, LocalDateTime now) {

        // ===== B0. KIỂM DỮ LIỆU GỬI LÊN (chưa mở transaction, chưa đụng DB) =====

        // B0a. Cách trả chỉ được là "Cash" hoặc "Transfer". Sửa DevTools gửi giá trị khác → MSG308.
        if (!"Cash".equals(method) && !"Transfer".equals(method)) {
            return ServiceResult.fail(Messages.MSG308_PT_SALE_METHOD_REQUIRED);
        }
        // B0a'. Chuyển khoản: lễ tân phải tích "Đã nhận đủ" sau khi kiểm tiền đã vào tài khoản (CBR-77) → MSG326.
        //       Trước đây chỉ trình duyệt kiểm ô này; gửi thẳng request (bỏ JS / DevTools) vẫn thu tiền được.
        if ("Transfer".equals(method) && !receivedInFull) {
            return ServiceResult.fail(Messages.MSG326_TRANSFER_RECEIVED_REQUIRED);
        }

        // B0b. Mã giao dịch: để trống hoặc trả tiền mặt → bỏ (null). Có nhập thì cắt khoảng trắng 2 đầu.
        String ref = reference == null || reference.isBlank() || "Cash".equals(method) ? null : reference.trim();
        //      Cột GatewayReference chỉ chứa 50 ký tự → dài hơn thì báo MSG309.
        if (ref != null && ref.length() > 50) {
            return ServiceResult.fail(Messages.MSG309_PT_SALE_REFERENCE_LENGTH);
        }

        // B0c. "2026-11-10" → LocalDate. Trống / sai định dạng → null (không báo lỗi ở đây, để B3 báo).
        LocalDate start = parseDate(startText);

        // ===== MỞ TRANSACTION: từ đây mọi lệnh ghi DB dùng chung 1 connection =====
        // Lỗi nghiệp vụ → throw IllegalArgumentException → inTransaction rollback + trả fail(câu lỗi).
        return inTransaction(connection -> {

            // B1. Tài khoản phải là lễ tân đang làm việc. receptionist[0] = EmployeeId → ghi là người nhận tiền.
            int[] receptionist = requireReceptionist(connection, accountId);

            // B2. Đọc đơn + danh sách khung đang giữ, KHÓA lại (UPDLOCK, HOLDLOCK) tới hết transaction.
            //     2 lễ tân bấm cùng lúc: người sau phải chờ, người trước xong thì người sau thấy đơn đã Paid.
            HeldOrder held = saleDAO.findHeldOrder(connection, orderId, true);

            // B3. Kiểm đơn còn thu tiền được không (null = được):
            //     - đơn không tồn tại / không còn Pending (đã thu, đã hủy)
            //     - hết 15 phút giữ chỗ
            //     - ngày bắt đầu null, hoặc lệch buổi đầu tiên (sau buổi đầu / trước buổi đầu quá 14 ngày)
            String problem = whyCannotPay(held, start, now);
            if (problem != null) {
                throw new IllegalArgumentException(problem);
            }

            // B4. Phải có ca đang mở mới được thu tiền (CBR-105, BR-93) — giống Bán hàng tại quầy.
            //     Chưa mở ca → MSG110 · ca của ngày trước chưa đóng → MSG324.
            openShiftId(Messages.MSG110_SHIFT_NOT_OPEN_FOR_PAYMENT);

            // B5. Gói vẫn còn bán (cần số tháng, loại gói để tạo gói PT ở B7).
            PackageRow pkg = saleDAO.findPackage(connection, held.packageId);
            if (pkg == null) {
                throw new IllegalArgumentException("Gói này không còn bán. Hủy giữ chỗ rồi chọn gói khác.");
            }

            // ===== B6. GHI TIỀN =====
            // B6a. Đơn Pending → Paid. Câu UPDATE có "WHERE Status = 'Pending'" = chặn lần 2 ngay lúc ghi.
            //      Trả false = đơn đã đổi trạng thái → dừng.
            if (!saleDAO.markOrderPaid(connection, orderId, now)) {
                throw new IllegalArgumentException("Đơn này không còn chờ thu tiền.");
            }
            // B6b. Thêm 1 giao dịch Success. Số tiền = held.price (giá chốt lúc giữ lịch, không lấy giá gói hiện tại).
            saleDAO.insertTransaction(
                    connection,
                    orderId,
                    held.price,        // số tiền
                    method,            // Cash / Transfer
                    ref,               // mã giao dịch (null nếu tiền mặt)
                    receptionist[0],   // lễ tân nhận tiền
                    now);

            // ===== B7. TẠO GÓI PT (Subscriptions) =====
            // B7a. Ngày hết gói = ngày bắt đầu + số tháng của gói.
            LocalDate end = DateTimeUtil.calculateEndDateByMonths(start, pkg.months);
            // B7b. Buổi cuối phải nằm trong kỳ. Nếu ngày bắt đầu gửi lên sớm hơn lúc giữ lịch
            //      → kỳ kết thúc sớm hơn → buổi cuối rơi ra ngoài gói → từ chối.
            LocalDate last = held.slots.get(held.slots.size() - 1).getSlotDate();
            if (last.isAfter(end)) {
                throw new IllegalArgumentException("Ngày bắt đầu không khớp lịch đang giữ: buổi "
                        + DateTimeUtil.formatDate(last) + " nằm sau ngày hết gói " + DateTimeUtil.formatDate(end)
                        + ". Hủy giữ chỗ rồi làm lại.");
            }
            // B7c. Số buổi = số khung đang giữ.
            int count = held.slots.size();
            // B7d. Thêm 1 gói Active. FixedStartTime = giờ bắt đầu xuất hiện nhiều nhất trong các buổi.
            int subscriptionId = saleDAO.insertSubscription(connection, orderId, held.holderId, pkg.type, held.ptId,
                    start, end, mostCommonStart(held.slots), count);

            // ===== B8. TẠO TỪNG BUỔI TẬP =====
            // B8a. Tỷ lệ hoa hồng của PT (không tìm thấy PT → 0%).
            PtRow pt = saleDAO.findPt(connection, held.ptId);
            int rate = pt == null ? 0 : pt.commissionRate;
            // B8b. Giá trị 1 buổi = giá gói ÷ số buổi (DBR-59). Hoa hồng 1 buổi = giá trị buổi × tỷ lệ %.
            //      Hoa hồng ghi vào từng buổi ngay lúc bán → PT đổi tỷ lệ sau này cũng không ảnh hưởng buổi đã bán.
            long value = sessionValue(held.price, count);
            long commission = Math.round(value * rate / 100.0);
            // B8c. Mỗi khung đang giữ:
            for (PtScheduleItem slot : held.slots) {
                // Khung Held → Booked (chỉ đổi nếu khung vẫn đang giữ cho đúng đơn này).
                // false = khung đã bị lấy mất → MSG103, rollback cả đơn.
                if (!saleDAO.bookHeldSlot(connection, slot.getTimeSlotId(), orderId)) {
                    throw new IllegalArgumentException(Messages.MSG103_SLOT_ALREADY_TAKEN);
                }
                // Thêm 1 buổi Scheduled gắn với gói vừa tạo và khung này.
                saleDAO.insertSession(
                        connection,
                        subscriptionId,       // gói PT vừa tạo ở B7
                        slot.getTimeSlotId(), // khung giờ
                        held.ptId,            // PT dạy
                        value,                // giá trị buổi
                        commission);          // hoa hồng PT
            }

            // ===== B9. THÔNG BÁO =====
            // 1 thông báo cho khách (+ người tập cùng nếu gói đôi), 1 thông báo cho PT. Ai không có tài khoản thì bỏ qua.
            notifySale(
                    connection,
                    held,
                    pkg,
                    pt == null ? "PT" : pt.fullName,
                    start,
                    end,
                    count,
                    now);

            // Xong hết → inTransaction commit → trả MSG310 "Đã thu … · đơn … · N buổi".
            return ServiceResult.ok(orderId, String.format(
                    Messages.MSG310_PT_SALE_PAID,
                    money(held.price),
                    held.orderCode,
                    count));
        });
    }
    

    /** HÀM PHỤ (pay) — lý do không thu tiền được đơn đang giữ; null = thu được. */
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

    /** HÀM PHỤ (pay) — giờ bắt đầu xuất hiện nhiều nhất → Subscriptions.FixedStartTime (giờ "chính" của gói). */
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

    /** HÀM PHỤ (pay, bước 7) — 1 thông báo cho khách (+ người tập cùng), 1 cho PT. Hồ sơ không có tài khoản → bỏ qua. */
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

    /** HÀM PHỤ (notifySale) — 1 thông báo hệ thống + 1 người nhận cho mỗi tài khoản. Nội dung quá 500 ký tự thì cắt. */
    private void notify(
            Connection connection,
            List<Integer> accountIds,
            String title,
            String content) throws SQLException {
        if (accountIds.isEmpty()) {
            return;
        }
        String text = content.length() > 500 ? content.substring(0, 497) + "..." : content;
        int notificationId = notificationDAO.insertSystemNotification(connection, title, text);
        for (int accountId : accountIds) {
            notificationDAO.insertRecipient(connection, notificationId, accountId);
        }
    }


    // ============================================================================================================
    // HÀM CHÍNH 5. release — nút "Hủy giữ chỗ" (POST action=release)
    //   Ai gọi: PtSaleServlet.doPost  ·  Gọi xuống: PtSaleDAO.findHeldOrder, releaseHold
    // ============================================================================================================

    /**
     * HÀM CHÍNH 5 — nút "Hủy giữ chỗ" (có hộp xác nhận ở JSP).
     *   AI GỌI   PtSaleServlet.doPost (action=release)
     *   RA       ok(MSG311) / fail("Đơn này không còn giữ chỗ.") → Servlet toast, về trang trống
     *   GHI DB   1 transaction: Orders Pending → Cancelled · TimeSlots do lần giữ tạo → xóa · khung có sẵn → Free
     */
    public ServiceResult<Integer> release(int accountId, int orderId, LocalDateTime now) {
        
        // B1: Mở transaction
        return inTransaction(connection -> {
            requireReceptionist(connection, accountId);
            HeldOrder held = saleDAO.findHeldOrder(connection, orderId, true);
            if (held == null || !"Pending".equals(held.status)) {
                throw new IllegalArgumentException("Đơn này không còn giữ chỗ.");
            }
            // Lễ tân chủ động hủy → CancelledByReceptionist.
            saleDAO.releaseHold(
                    connection,
                    orderId,
                    held.createdAt,
                    CANCEL_BY_RECEPTIONIST,
                    RELEASE_REASON,
                    now);
            return ServiceResult.ok(orderId, String.format(Messages.MSG311_PT_SALE_RELEASED, held.orderCode));
        });
    }


    // ============================================================================================================
    // DÙNG CHUNG CHO CÁC HÀM CHÍNH
    //   releaseExpired  nhả đơn giữ quá 15 phút (prepare + hold gọi đầu tiên)
    //   inTransaction   chạy 1 khối ghi trong 1 transaction (hold, pay, release, releaseExpired)
    //   requireReceptionist · sessionValue · vatAmount · money · isWeekday · parseId · parseDate · parseTime
    // ============================================================================================================

    /**
     * DÙNG CHUNG (prepare, hold) — nhả mọi đơn giữ chỗ đã quá 15 phút, mỗi đơn 1 transaction nhỏ.
     * Không có JOB nền: đơn quá hạn chỉ được nhả khi có người mở màn Bán gói PT hoặc bấm Giữ lịch.
     */
    void releaseExpired(LocalDateTime now) throws SQLException {
        List<Integer> expired;
        try (Connection connection = DBContext.getConnection()) {
            expired = saleDAO.findExpiredHoldOrders(connection, now);
        }
        for (int orderId : expired) {
            inTransaction(connection -> {
                HeldOrder held = saleDAO.findHeldOrder(connection, orderId, true);
                if (held != null && "Pending".equals(held.status)) {
                    // Hết 15 phút chưa thu tiền → PaymentTimeout (BR-93), không phải "lễ tân hủy".
                    saleDAO.releaseHold(
                            connection,
                            orderId,
                            held.createdAt,
                            CANCEL_TIMEOUT,
                            EXPIRED_REASON,
                            now);
                }
                return ServiceResult.ok(orderId);
            });
        }
    }

    
    // interface Work 
    @FunctionalInterface
    interface Work {
        ServiceResult<Integer> run(Connection connection) throws SQLException;
    }

    /**
     * DÙNG CHUNG (hold, pay, release, releaseExpired) — chạy work trong 1 transaction.
     *   work chạy xong → commit · ném IllegalArgumentException → rollback + trả câu lỗi cho lễ tân
     *   · SQLException / lỗi khác → rollback + MSG04 "Hệ thống đang bận".
     */
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

    /** DÙNG CHUNG — tài khoản → [EmployeeId, PersonId] của lễ tân; không phải lễ tân → IllegalArgumentException(MSG07) → rollback. */
    private int[] requireReceptionist(Connection connection, int accountId) throws SQLException {
        int[] receptionist = saleDAO.findReceptionist(connection, accountId);
        if (receptionist == null) {
            throw new IllegalArgumentException(Messages.MSG07_NO_PERMISSION);
        }
        return receptionist;
    }

    /** Giá trị 1 buổi = giá gói ÷ số buổi thật sự sinh ra, làm tròn đồng (DBR-59). */
    static long sessionValue(long price, int sessionCount) {
        return Math.round((double) price / sessionCount);
    }

    /** Tiền VAT nằm trong giá = giá × VAT ÷ (100 + VAT), làm tròn đồng (DBR-57). */
    static long vatAmount(long price, int vatRate) {
        return Math.round(price * (double) vatRate / (100 + vatRate));
    }

    /** 1500000 → "1.500.000đ" (in trong ô gói và câu MSG310). */
    static String money(long amount) {
        return NumberFormat.getInstance(Locale.forLanguageTag("vi-VN")).format(amount) + "đ";
    }

    /** Thứ 2 → Thứ 6 (gói PT chỉ xếp buổi T2–T6). */
    private static boolean isWeekday(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY;
    }

    /** "12" → 12; trống / chữ / ≤ 0 → null (URL bị sửa tay không làm lỗi trang). */
    private static Integer parseId(String text) {
        try {
            int id = Integer.parseInt(text == null ? "" : text.trim());
            return id > 0 ? id : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    /** "2026-11-03" → LocalDate; sai định dạng → null. */
    private static LocalDate parseDate(String text) {
        try {
            return text == null || text.isBlank() ? null : LocalDate.parse(text.trim());
        } catch (DateTimeException exception) {
            return null;
        }
    }

    /** "09:30" → LocalTime; trống / sai → null (= Nghỉ ở lịch mẫu). */
    private static LocalTime parseTime(String text) {
        try {
            return text == null || text.isBlank() ? null : LocalTime.parse(text.trim());
        } catch (DateTimeException exception) {
            return null;
        }
    }
}
