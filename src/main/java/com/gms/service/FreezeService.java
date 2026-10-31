package com.gms.service;

import com.gms.constant.Messages;
import com.gms.constant.Role;
import com.gms.dao.AccountDAO;
import com.gms.dao.AuditLogDAO;
import com.gms.dao.CheckInDAO;
import com.gms.dao.CustomerDAO;
import com.gms.dao.EmployeeDAO;
import com.gms.dao.FreezeRecordDAO;
import com.gms.dto.CustomerMembershipRow;
import com.gms.dto.CustomerSearchItem;
import com.gms.dto.FreezeCustomerView;
import com.gms.dto.FreezeRecordItemView;
import com.gms.dto.FreezeSessionImpactView;
import com.gms.dto.PageResult;
import com.gms.dto.ServiceResult;
import com.gms.entity.Account;
import com.gms.entity.AuditLog;
import com.gms.entity.Employee;
import com.gms.entity.FreezeRecord;
import com.gms.entity.Subscription;
import com.gms.util.DBContext;
import com.gms.util.DateTimeUtil;
import com.gms.util.ValidationUtil;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service xử lý toàn bộ nghiệp vụ Bảo lưu & Tự động mở lại gói tập (BF-09, UC-18, JOB-01).
 *
 * <p>Quy tắc nghiệp vụ được thực thi đầy đủ:
 * <ul>
 *   <li><b>BR-20:</b> Khi bảo lưu gói tập của hội viên Active, hội viên chuyển sang Frozen.</li>
 *   <li><b>BR-21:</b> Chỉ gói tập Membership mới được bảo lưu; gói PT và gói ngày không được bảo lưu.</li>
 *   <li><b>BR-22:</b> Bảo lưu gói tập hoàn toàn miễn phí.</li>
 *   <li><b>BR-23:</b> Gói tập đã hết hạn không được bảo lưu.</li>
 *   <li><b>BR-24:</b> Gói tập đã dùng hết số lượt bảo lưu (P-01 snapshot) không được bảo lưu tiếp.</li>
 *   <li><b>BR-25:</b> Khi hết kỳ bảo lưu, hệ thống tự động mở lại gói tập và hội viên trở lại Active (JOB-01).</li>
 *   <li><b>BR-26, DBR-51:</b> Lễ tân có thể hủy bảo lưu sớm: gói mở lại ngay, hội viên về Active, hạn gói
 *       được cộng thêm đúng số ngày đã bảo lưu thực tế. Lượt bảo lưu vẫn bị tính đã dùng. Buổi PT đã thành
 *       Missed không phục hồi.</li>
 *   <li><b>CBR-08, DBR-13:</b> Ngày bắt đầu bảo lưu sớm nhất là ngày mai. Thời lượng tối đa không vượt quá P-02 tháng
 *       và không vượt quá số ngày còn lại của gói tập.</li>
 *   <li><b>CBR-62:</b> Bảo lưu không bị chặn bởi buổi PT; các buổi Scheduled trong kỳ chuyển thành Missed
 *       (lý do MembershipFrozen, trừ buổi, không hoàn tiền). Danh sách buổi được liệt kê trước để thông báo khách.</li>
 *   <li><b>CBR-67:</b> Buổi tập của gói đôi (PTCouple) chỉ bị Missed nếu người cùng tập cũng không có thẻ tập hợp lệ.</li>
 *   <li><b>CBR-68, DBR-36:</b> Các buổi chuyển thành Missed do bảo lưu nhả slot về Free để PT nhận khách khác.</li>
 *   <li><b>CBR-115:</b> Nhân viên không được tự thực hiện thao tác bảo lưu trên hồ sơ khách của chính mình.</li>
 * </ul>
 * </p>
 */
public class FreezeService {

    private static final Logger LOGGER = Logger.getLogger(FreezeService.class.getName());

    private final FreezeRecordDAO freezeRecordDAO;
    private final CheckInDAO checkInDAO;
    private final AccountDAO accountDAO;
    private final EmployeeDAO employeeDAO;
    private final CustomerDAO customerDAO;
    private final SystemConfigService systemConfigService;
    private final SelfServiceGuard selfServiceGuard;
    private final AuditLogDAO auditLogDAO;

    /**
     * Constructor mặc định dùng trong ứng dụng thực tế.
     */
    public FreezeService() {
        this(new FreezeRecordDAO(), new CheckInDAO(), new AccountDAO(), new EmployeeDAO(),
                new CustomerDAO(), new SystemConfigService(), new SelfServiceGuard(), new AuditLogDAO());
    }

    /**
     * Constructor nhận dependencies để hỗ trợ kiểm thử đơn vị độc lập.
     */
    public FreezeService(FreezeRecordDAO freezeRecordDAO,
                         CheckInDAO checkInDAO,
                         AccountDAO accountDAO,
                         EmployeeDAO employeeDAO,
                         CustomerDAO customerDAO,
                         SystemConfigService systemConfigService,
                         SelfServiceGuard selfServiceGuard,
                         AuditLogDAO auditLogDAO) {
        this.freezeRecordDAO = freezeRecordDAO;
        this.checkInDAO = checkInDAO;
        this.accountDAO = accountDAO;
        this.employeeDAO = employeeDAO;
        this.customerDAO = customerDAO;
        this.systemConfigService = systemConfigService;
        this.selfServiceGuard = selfServiceGuard;
        this.auditLogDAO = auditLogDAO;
    }

    /**
     * Mở kết nối cơ sở dữ liệu để thực hiện transaction (có thể ghi đè trong test).
     */
    protected Connection openConnection() throws SQLException {
        return DBContext.getConnection();
    }

    // =========================================================================
    // 1. TRA CỨU HỘI VIÊN & KIỂM TRA ĐIỀU KIỆN BẢO LƯU (Customer Lookup)
    // =========================================================================

    /**
     * Tra cứu thông tin hội viên theo từ khóa (Mã KH, SĐT hoặc Tên) và đánh giá toàn diện
     * khả năng bảo lưu gói tập Membership hiện tại của hội viên đó.
     *
     * @param query từ khóa tra cứu (mã KH KH-xxxx, SĐT hoặc tên)
     * @param staffAccountId mã tài khoản nhân viên đang thao tác (để kiểm tra CBR-115)
     * @return kết quả tra cứu đóng gói trong {@link FreezeCustomerView}
     */
    public ServiceResult<FreezeCustomerView> lookupCustomerForFreeze(String query, Integer staffAccountId) {
        if (ValidationUtil.isNullOrEmpty(query)) {
            return ServiceResult.fail(String.format(Messages.MSG08_NOT_FOUND, "hội viên"));
        }

        try {
            // B1: Tra cứu hội viên theo từ khóa chính xác (Mã KH hoặc SĐT)
            CheckInDAO.CustomerLookupInfo cust = checkInDAO.findCustomerByKeyword(query.trim());
            if (cust == null) {
                return ServiceResult.fail(String.format(Messages.MSG08_NOT_FOUND, "hội viên"));
            }

            // B2: Khởi tạo view kết quả
            FreezeCustomerView view = new FreezeCustomerView();
            view.setCustomerId(cust.customerId);
            view.setPersonId(cust.personId);
            view.setCustomerCode(cust.customerCode);
            view.setFullName(cust.fullName);
            view.setPhone(cust.phone);
            view.setCustomerStatus(cust.customerStatus);
            view.setCheckInPhoto(cust.checkInPhoto);
            view.setAvatar(cust.avatar);

            LocalDate today = DateTimeUtil.today();

            // B3: Kiểm tra yêu cầu xóa hồ sơ đang chờ duyệt (CBR-16)
            if (checkInDAO.hasPendingDeletionRequest(cust.customerId)) {
                view.setEligibleToFreeze(false);
                view.setIneligibilityReason(Messages.MSG101_PROFILE_PENDING_DELETION);
            }

            // B4: Kiểm tra tự phục vụ (CBR-115)
            boolean isOwnProfile = false;
            if (staffAccountId != null && staffAccountId > 0) {
                isOwnProfile = selfServiceGuard.isOwnCustomerProfile(staffAccountId, cust.customerId);
            }
            view.setOwnProfile(isOwnProfile);

            // B5: Tìm gói Membership đang Active hoặc Frozen của hội viên
            Subscription activeOrFrozenSub = freezeRecordDAO.findActiveOrFrozenMembershipByCustomerId(cust.customerId);

            if (activeOrFrozenSub == null) {
                // Không có gói tập Membership nào đang chạy
                view.setHasMembership(false);
                view.setEligibleToFreeze(false);
                if (view.getIneligibilityReason() == null) {
                    view.setIneligibilityReason(String.format(Messages.MSG99_FREEZE_REFUSED,
                            "khách hàng không có gói tập Membership nào đang hoạt động hoặc bảo lưu"));
                }
            } else {
                view.setHasMembership(true);
                view.setSubscriptionId(activeOrFrozenSub.getSubscriptionId());
                view.setOrderId(activeOrFrozenSub.getOrderId());
                view.setPackageStartDate(activeOrFrozenSub.getStartDate());
                view.setPackageEndDate(activeOrFrozenSub.getEndDate());
                view.setSubscriptionStatus(activeOrFrozenSub.getStatus() != null ? activeOrFrozenSub.getStatus().getDbValue() : "");
                view.setFreezeUsed(activeOrFrozenSub.getFreezeUsed());
                int maxSnapshot = activeOrFrozenSub.getMaxFreezesSnapshot() != null ? activeOrFrozenSub.getMaxFreezesSnapshot() : 1;
                view.setMaxFreezesSnapshot(maxSnapshot);

                // Lấy tên gói tập từ danh mục gói qua OrderId
                String pkgName = freezeRecordDAO.findPackageNameByOrderId(activeOrFrozenSub.getOrderId());
                view.setPackageName(pkgName);

                // Đọc tham số P-02 (Thời gian bảo lưu tối đa, mặc định 1 tháng)
                int maxMonths = 1;
                try {
                    maxMonths = systemConfigService.getInt(SystemConfigService.MAX_FREEZE_MONTHS);
                } catch (Exception ex) {
                    LOGGER.log(Level.WARNING, "Không đọc được cấu hình P-02, dùng mặc định 1 tháng", ex);
                }
                view.setMaxFreezeMonths(maxMonths);

                // B6: Xử lý theo trạng thái: ĐANG BẢO LƯU (Frozen) vs ĐANG HOẠT ĐỘNG (Active)
                if (activeOrFrozenSub.getStatus() == Subscription.Status.FROZEN) {
                    // Gói đang trong kỳ bảo lưu
                    view.setCurrentlyFrozen(true);
                    view.setEligibleToFreeze(false);
                    if (view.getIneligibilityReason() == null) {
                        view.setIneligibilityReason("Gói tập hiện đang trong kỳ bảo lưu.");
                    }

                    // Đọc đợt bảo lưu đang mở từ FreezeRecords
                    FreezeRecord openRecord = freezeRecordDAO.findOpenFreezeBySubscriptionId(activeOrFrozenSub.getSubscriptionId());
                    if (openRecord != null) {
                        view.setOpenFreezeRecordId(openRecord.getFreezeRecordId());
                        view.setCurrentFreezeStartDate(openRecord.getStartDate());
                        view.setCurrentFreezePlannedEndDate(openRecord.getPlannedEndDate());
                        view.setCurrentFreezeReason(openRecord.getReason());
                        view.setCurrentFreezeCreatedAt(openRecord.getCreatedAt());

                        // Đọc tên nhân viên đã tiếp nhận bảo lưu
                        String empName = freezeRecordDAO.findEmployeeDisplay(openRecord.getHandledByEmployeeId());
                        view.setCurrentFreezeHandledBy(empName);

                        // Dự tính số ngày đã bảo lưu nếu hủy sớm hôm nay (DBR-51)
                        int daysSoFar = 0;
                        if (!today.isBefore(openRecord.getStartDate())) {
                            daysSoFar = (int) ChronoUnit.DAYS.between(openRecord.getStartDate(), today) + 1;
                        }
                        view.setDaysFrozenIfCancelledToday(daysSoFar);
                        view.setProjectedNewEndDateIfCancelledToday(activeOrFrozenSub.getEndDate().plusDays(daysSoFar));
                        view.setCanCancelEarly(!isOwnProfile);
                    }

                } else if (activeOrFrozenSub.getStatus() == Subscription.Status.ACTIVE) {
                    // Gói đang hoạt động: kiểm tra điều kiện bảo lưu
                    view.setCurrentlyFrozen(false);

                    // Kiểm tra BR-23: gói hết hạn
                    if (activeOrFrozenSub.getEndDate().isBefore(today)) {
                        view.setEligibleToFreeze(false);
                        view.setIneligibilityReason(String.format(Messages.MSG99_FREEZE_REFUSED, "gói tập đã hết hạn"));
                    }
                    // Kiểm tra BR-24: hết lượt bảo lưu
                    else if (activeOrFrozenSub.getFreezeUsed() >= maxSnapshot) {
                        view.setEligibleToFreeze(false);
                        view.setIneligibilityReason(String.format(Messages.MSG99_FREEZE_REFUSED,
                                "đã dùng hết hạn mức bảo lưu (" + activeOrFrozenSub.getFreezeUsed() + "/" + maxSnapshot + " lần)"));
                    }
                    // Kiểm tra CBR-115: nhân viên tự thao tác
                    else if (isOwnProfile) {
                        view.setEligibleToFreeze(false);
                        view.setIneligibilityReason(Messages.MSG282_STAFF_OWN_FREEZE);
                    }
                    // Đạt đủ điều kiện bảo lưu
                    else {
                        view.setEligibleToFreeze(true);
                        LocalDate minStart = today.plusDays(1); // Ngày mai (DBR-13)
                        view.setMinStartDate(minStart);

                        // Ngày kết thúc tối đa = min(minStart + P-02 tháng - 1 ngày, packageEndDate) (GBR-03, SRS 6.3)
                        LocalDate maxEndByDuration = minStart.plusMonths(maxMonths).minusDays(1);
                        LocalDate maxAllowedEnd = maxEndByDuration.isBefore(activeOrFrozenSub.getEndDate())
                                ? maxEndByDuration
                                : activeOrFrozenSub.getEndDate();

                        // Nếu ngày mai đã vượt quá hạn gói tập thì không thể bảo lưu
                        if (minStart.isAfter(activeOrFrozenSub.getEndDate())) {
                            view.setEligibleToFreeze(false);
                            view.setIneligibilityReason(String.format(Messages.MSG99_FREEZE_REFUSED,
                                    "gói tập kết thúc vào hôm nay, không còn ngày để bảo lưu"));
                        } else {
                            view.setMaxEndDate(maxAllowedEnd);
                        }
                    }
                }
            }

            // B7: Đọc lịch sử các lần bảo lưu trước đây của khách hàng
            List<FreezeRecordItemView> history = freezeRecordDAO.findFreezesByCustomerId(cust.customerId);
            view.setFreezeHistory(history);

            // B8: Nếu có thể bảo lưu, lấy danh sách xem trước các buổi PT bị ảnh hưởng (mặc định thử ngày mai -> maxEndDate)
            if (view.isEligibleToFreeze() && view.getMinStartDate() != null && view.getMaxEndDate() != null) {
                List<FreezeSessionImpactView> affected = freezeRecordDAO.findScheduledSessionsInPeriod(
                        cust.customerId, view.getMinStartDate(), view.getMaxEndDate());
                view.setAffectedSessions(affected);
            }

            return ServiceResult.ok(view);

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Lỗi khi tra cứu hội viên để bảo lưu", e);
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /**
     * Tra cứu danh sách phân trang khách hàng khi tìm theo một phần tên.
     */
    public PageResult<CustomerSearchItem> searchCustomersPage(String keyword, int pageNumber, int pageSize) {
        if (ValidationUtil.isNullOrEmpty(keyword)) {
            return new PageResult<>(List.of(), pageNumber, pageSize, 0);
        }
        try {
            long total = checkInDAO.countCustomersByKeyword(keyword.trim());
            if (total == 0) {
                return new PageResult<>(List.of(), pageNumber, pageSize, 0);
            }
            List<CustomerSearchItem> items = checkInDAO.searchCustomersByKeyword(keyword.trim(), pageNumber, pageSize);
            return new PageResult<>(items, pageNumber, pageSize, total);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Lỗi phân trang tìm kiếm khách hàng", e);
            return new PageResult<>(List.of(), pageNumber, pageSize, 0);
        }
    }

    // =========================================================================
    // 2. XEM TRƯỚC CÁC BUỔI PT BỊ ẢNH HƯỞNG (CBR-62 Preview)
    // =========================================================================

    /**
     * Lấy danh sách các buổi Scheduled của hội viên trong khoảng thời gian bảo lưu để Lễ tân
     * thông báo trước cho khách hàng (CBR-62, CBR-67).
     *
     * @param customerId mã hội viên
     * @param startDate ngày bắt đầu bảo lưu
     * @param plannedEndDate ngày kết thúc bảo lưu
     * @return danh sách các buổi PT chịu tác động
     */
    public List<FreezeSessionImpactView> previewAffectedSessions(int customerId, LocalDate startDate, LocalDate plannedEndDate) {
        if (customerId <= 0 || startDate == null || plannedEndDate == null || plannedEndDate.isBefore(startDate)) {
            return List.of();
        }
        try {
            return freezeRecordDAO.findScheduledSessionsInPeriod(customerId, startDate, plannedEndDate);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Lỗi khi xem trước các buổi PT bị ảnh hưởng bởi bảo lưu", e);
            return List.of();
        }
    }

    // =========================================================================
    // 3. THỰC HIỆN BẢO LƯU GÓI TẬP (Apply Freeze - Main Flow)
    // =========================================================================

    /**
     * Tạo một lượt bảo lưu gói tập mới cho hội viên trong một transaction an toàn toàn vẹn (BF-09).
     *
     * @param staffAccountId tài khoản Lễ tân đang đăng nhập
     * @param subscriptionId mã quyền dùng gói Membership cần bảo lưu
     * @param startDate ngày bắt đầu bảo lưu (sớm nhất là ngày mai - DBR-13)
     * @param plannedEndDate ngày kết thúc dự kiến (không quá P-02 tháng và không quá hạn gói)
     * @param reason lý do bảo lưu (tối thiểu 5 ký tự)
     * @return kết quả thực hiện thành công kèm thông báo hoặc thông báo lỗi cụ thể
     */
    public ServiceResult<Void> applyFreeze(int staffAccountId, int subscriptionId,
                                          LocalDate startDate, LocalDate plannedEndDate, String reason) {

        // B1: Kiểm tra tính hợp lệ cơ bản của dữ liệu đầu vào
        if (staffAccountId <= 0 || subscriptionId <= 0) {
            return ServiceResult.fail(String.format(Messages.MSG08_NOT_FOUND, "thông tin yêu cầu"));
        }

        if (ValidationUtil.isNullOrEmpty(reason) || reason.trim().length() < 5) {
            return ServiceResult.fail(Messages.MSG278_FREEZE_REASON_MIN);
        }

        if (startDate == null || plannedEndDate == null) {
            return ServiceResult.fail(Messages.MSG279_FREEZE_DATES_REQUIRED);
        }

        LocalDate today = DateTimeUtil.today();
        LocalDate minStart = today.plusDays(1);

        // DBR-13: Bảo lưu sớm nhất từ ngày mai
        if (startDate.isBefore(minStart)) {
            return ServiceResult.fail(String.format(Messages.MSG280_FREEZE_START_FROM_TOMORROW, DateTimeUtil.formatDate(minStart)));
        }

        // DBR-13: From <= To
        if (plannedEndDate.isBefore(startDate)) {
            return ServiceResult.fail(Messages.MSG281_FREEZE_END_BEFORE_START);
        }

        // B2: Mở transaction để thực hiện chuỗi cập nhật nguyên tử
        try (Connection conn = openConnection()) {
            conn.setAutoCommit(false);

            try {
                // Đọc thông tin nhân viên Lễ tân thao tác
                Account account = accountDAO.findById(staffAccountId);
                if (account == null) {
                    conn.rollback();
                    return ServiceResult.fail(String.format(Messages.MSG08_NOT_FOUND, "tài khoản nhân viên"));
                }

                Employee employee = employeeDAO.findByPersonId(account.getPersonId());
                if (employee == null) {
                    conn.rollback();
                    return ServiceResult.fail(String.format(Messages.MSG08_NOT_FOUND, "hồ sơ nhân viên"));
                }

                // Khóa và đọc Subscription bằng UPDLOCK
                Subscription sub = freezeRecordDAO.findMembershipSubscription(conn, subscriptionId);
                if (sub == null) {
                    conn.rollback();
                    return ServiceResult.fail(String.format(Messages.MSG08_NOT_FOUND, "gói tập"));
                }

                // Kiểm tra CBR-115: Nhân viên không tự bảo lưu gói tập của chính mình
                if (selfServiceGuard.isOwnCustomerProfile(staffAccountId, sub.getCustomerId())) {
                    conn.rollback();
                    return ServiceResult.fail(Messages.MSG282_STAFF_OWN_FREEZE);
                }

                // Kiểm tra BR-21: Chỉ bảo lưu Membership
                if (sub.getSubscriptionType() != Subscription.Type.MEMBERSHIP) {
                    conn.rollback();
                    return ServiceResult.fail(String.format(Messages.MSG99_FREEZE_REFUSED, "chỉ có thể bảo lưu gói tập Membership"));
                }

                // Kiểm tra BR-23: Gói tập phải đang Active và chưa hết hạn
                if (sub.getStatus() != Subscription.Status.ACTIVE || sub.getEndDate().isBefore(today)) {
                    conn.rollback();
                    return ServiceResult.fail(String.format(Messages.MSG99_FREEZE_REFUSED, "gói tập không ở trạng thái hoạt động"));
                }

                // Kiểm tra BR-24: Hạn mức số lần bảo lưu
                int maxSnapshot = sub.getMaxFreezesSnapshot() != null ? sub.getMaxFreezesSnapshot() : 1;
                if (sub.getFreezeUsed() >= maxSnapshot) {
                    conn.rollback();
                    return ServiceResult.fail(String.format(Messages.MSG99_FREEZE_REFUSED,
                            "gói tập đã dùng hết hạn mức bảo lưu (" + sub.getFreezeUsed() + "/" + maxSnapshot + " lần)"));
                }

                // Kiểm tra không được có đợt bảo lưu nào đang mở trên gói này
                FreezeRecord openFreeze = freezeRecordDAO.findOpenFreezeBySubscriptionId(conn, subscriptionId);
                if (openFreeze != null) {
                    conn.rollback();
                    return ServiceResult.fail(String.format(Messages.MSG99_FREEZE_REFUSED, "gói tập đang có đợt bảo lưu chưa hoàn tất"));
                }

                // Kiểm tra giới hạn thời lượng P-02 (CBR-08, DBR-13)
                int maxMonths = 1;
                try {
                    maxMonths = systemConfigService.getInt(SystemConfigService.MAX_FREEZE_MONTHS);
                } catch (Exception ex) {
                    LOGGER.log(Level.WARNING, "Không đọc được cấu hình P-02", ex);
                }

                LocalDate maxAllowedEnd = startDate.plusMonths(maxMonths).minusDays(1);
                if (maxAllowedEnd.isAfter(sub.getEndDate())) {
                    maxAllowedEnd = sub.getEndDate();
                }

                if (plannedEndDate.isAfter(maxAllowedEnd)) {
                    conn.rollback();
                    return ServiceResult.fail(String.format(Messages.MSG100_FREEZE_LIMIT,
                            maxMonths, DateTimeUtil.formatDate(maxAllowedEnd)));
                }

                // B3: Thêm bản ghi mới vào FreezeRecords (giới hạn tối đa 500 ký tự)
                String cleanReason = reason != null ? reason.trim() : "";
                if (cleanReason.length() > 500) {
                    cleanReason = cleanReason.substring(0, 500);
                }

                FreezeRecord freezeRecord = new FreezeRecord(
                        subscriptionId,
                        employee.getEmployeeId(),
                        startDate,
                        plannedEndDate,
                        cleanReason
                );
                int freezeRecordId = freezeRecordDAO.insert(conn, freezeRecord);

                // B4: Cập nhật Subscription -> Status = 'Frozen', FreezeUsed = FreezeUsed + 1
                freezeRecordDAO.updateSubscriptionStatusAndFreezeUsed(
                        conn, subscriptionId, "Frozen", sub.getFreezeUsed() + 1);

                // B5: Cập nhật Customers -> Status = 'Frozen' (BR-20)
                freezeRecordDAO.updateCustomerStatus(conn, sub.getCustomerId(), "Frozen");

                // B6: Chuyển các buổi PT Scheduled trong kỳ thành Missed (MembershipFrozen) (CBR-62, CBR-67, CBR-68)
                List<FreezeSessionImpactView> impactedSessions = freezeRecordDAO.findScheduledSessionsInPeriod(
                        sub.getCustomerId(), startDate, plannedEndDate);

                List<Integer> sessionIdsToMiss = impactedSessions.stream()
                        .filter(FreezeSessionImpactView::isWillBeMissed)
                        .map(FreezeSessionImpactView::getSessionId)
                        .toList();

                if (!sessionIdsToMiss.isEmpty()) {
                    freezeRecordDAO.markSessionsMissedDueToFreeze(conn, sessionIdsToMiss);
                }

                // B7: Ghi AuditLog (đảm bảo Reason không vượt quá 500 ký tự cột DB)
                String auditReason = "Lập phiếu bảo lưu #" + freezeRecordId + " (" + DateTimeUtil.formatDate(startDate)
                        + " - " + DateTimeUtil.formatDate(plannedEndDate) + "): " + cleanReason;
                if (auditReason.length() > 500) {
                    auditReason = auditReason.substring(0, 497) + "...";
                }

                AuditLog log = new AuditLog(
                        staffAccountId,
                        Role.RECEPTIONIST,
                        "FREEZE_MEMBERSHIP",
                        "Subscription",
                        subscriptionId,
                        "Status",
                        "Active",
                        "Frozen",
                        auditReason
                );
                auditLogDAO.insert(conn, log);

                // B8: Commit transaction thành công
                conn.commit();

                String msg = "Bảo lưu gói tập thành công từ ngày " + DateTimeUtil.formatDate(startDate)
                        + " đến ngày " + DateTimeUtil.formatDate(plannedEndDate) + ".";
                if (!sessionIdsToMiss.isEmpty()) {
                    msg += " Có " + sessionIdsToMiss.size() + " buổi PT trong kỳ đã được chốt Vắng theo quy định.";
                }
                return ServiceResult.ok(null, msg);

            } catch (SQLException e) {
                conn.rollback();
                LOGGER.log(Level.SEVERE, "Lỗi SQL trong transaction tạo bảo lưu gói tập", e);
                return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
            }

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Lỗi kết nối CSDL khi tạo bảo lưu", e);
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    // =========================================================================
    // 4. HỦY BẢO LƯU SỚM (Cancel Freeze Early - BR-26, DBR-51)
    // =========================================================================

    /**
     * Hủy bảo lưu sớm theo yêu cầu của hội viên tại quầy Lễ tân (BR-26).
     *
     * <p>Xử lý:
     * <ul>
     *   <li>Gói tập mở lại ngay lập tức (Frozen -> Active).</li>
     *   <li>Hội viên trở lại trạng thái Active.</li>
     *   <li>Hạn sử dụng gói tập được gia hạn thêm đúng số ngày đã bảo lưu thực tế:
     *       <code>daysFrozen = ActualEndDate - StartDate + 1</code> (DBR-51).</li>
     *   <li>Lượt bảo lưu vẫn bị tính đã sử dụng (BR-24, P-01).</li>
     *   <li>Các buổi PT đã chuyển thành Missed không phục hồi lại (CBR-68).</li>
     * </ul>
     * </p>
     *
     * @param staffAccountId mã tài khoản nhân viên thao tác
     * @param freezeRecordId mã bản ghi bảo lưu cần hủy
     * @return kết quả thực hiện kèm thông tin số ngày đã gia hạn
     */
    public ServiceResult<Void> cancelFreezeEarly(int staffAccountId, int freezeRecordId) {
        if (staffAccountId <= 0 || freezeRecordId <= 0) {
            return ServiceResult.fail(String.format(Messages.MSG08_NOT_FOUND, "thông tin đợt bảo lưu"));
        }

        try (Connection conn = openConnection()) {
            conn.setAutoCommit(false);

            try {
                // B1: Đọc và khóa bản ghi FreezeRecord
                FreezeRecord freeze = freezeRecordDAO.findById(conn, freezeRecordId);
                if (freeze == null) {
                    conn.rollback();
                    return ServiceResult.fail(String.format(Messages.MSG08_NOT_FOUND, "phiếu bảo lưu"));
                }

                if (freeze.getActualEndDate() != null) {
                    conn.rollback();
                    return ServiceResult.fail(Messages.MSG283_FREEZE_ALREADY_ENDED);
                }

                // B2: Đọc và khóa gói Subscription
                Subscription sub = freezeRecordDAO.findMembershipSubscription(conn, freeze.getSubscriptionId());
                if (sub == null) {
                    conn.rollback();
                    return ServiceResult.fail(String.format(Messages.MSG08_NOT_FOUND, "gói tập"));
                }

                // B3: Kiểm tra CBR-115
                if (selfServiceGuard.isOwnCustomerProfile(staffAccountId, sub.getCustomerId())) {
                    conn.rollback();
                    return ServiceResult.fail(Messages.MSG284_STAFF_OWN_PROFILE);
                }

                LocalDate today = DateTimeUtil.today();
                LocalDate actualEnd;
                int daysFrozen;

                // B4: Tính ngày kết thúc thực tế và số ngày đã bảo lưu (DBR-51)
                if (today.isBefore(freeze.getStartDate())) {
                    // Hủy trước khi đợt bảo lưu bắt đầu có hiệu lực
                    // Gán ActualEndDate = StartDate để thỏa mãn CK_FreezeRecords_Actual (ActualEndDate >= StartDate)
                    actualEnd = freeze.getStartDate();
                    daysFrozen = 0;
                } else if (today.isAfter(freeze.getPlannedEndDate())) {
                    actualEnd = freeze.getPlannedEndDate();
                    daysFrozen = (int) ChronoUnit.DAYS.between(freeze.getStartDate(), actualEnd) + 1;
                } else {
                    actualEnd = today;
                    daysFrozen = (int) ChronoUnit.DAYS.between(freeze.getStartDate(), actualEnd) + 1;
                }

                // B5: Cập nhật FreezeRecords.ActualEndDate
                freezeRecordDAO.updateActualEndDate(conn, freezeRecordId, actualEnd);

                // B6: Mở lại gói tập và cộng số ngày thực tế vào EndDate
                LocalDate newEndDate = sub.getEndDate().plusDays(daysFrozen);
                freezeRecordDAO.updateSubscriptionResume(conn, sub.getSubscriptionId(), "Active", newEndDate);

                // B7: Cập nhật Customers.Status = 'Active' (BR-26)
                freezeRecordDAO.updateCustomerStatus(conn, sub.getCustomerId(), "Active");

                // B8: Ghi AuditLog
                AuditLog log = new AuditLog(
                        staffAccountId,
                        Role.RECEPTIONIST,
                        "CANCEL_FREEZE_EARLY",
                        "FreezeRecord",
                        freezeRecordId,
                        "ActualEndDate",
                        "NULL (Planned=" + DateTimeUtil.formatDate(freeze.getPlannedEndDate()) + ")",
                        DateTimeUtil.formatDate(actualEnd) + " (Gia hạn +" + daysFrozen + " ngày)",
                        "Hủy bảo lưu sớm tại quầy Lễ tân (BR-26)"
                );
                auditLogDAO.insert(conn, log);

                // B9: Commit transaction
                conn.commit();

                String msg = "Đã hủy bảo lưu sớm thành công. Gói tập đã được mở lại ngay; ";
                if (daysFrozen > 0) {
                    msg += "hạn gói được gia hạn thêm " + daysFrozen + " ngày (hạn mới: "
                            + DateTimeUtil.formatDate(newEndDate) + ").";
                } else {
                    msg += "thời gian bảo lưu chưa bắt đầu nên hạn gói giữ nguyên ("
                            + DateTimeUtil.formatDate(newEndDate) + ").";
                }
                return ServiceResult.ok(null, msg);

            } catch (SQLException e) {
                conn.rollback();
                LOGGER.log(Level.SEVERE, "Lỗi SQL khi hủy bảo lưu sớm", e);
                return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
            }

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Lỗi kết nối CSDL khi hủy bảo lưu sớm", e);
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    // =========================================================================
    // 5. TỰ ĐỘNG MỞ LẠI KHI HẾT HẠN BẢO LƯU (JOB-01 Auto-Resume)
    // =========================================================================

    /**
     * Tự động mở lại các gói tập có đợt bảo lưu đã quá hạn dự kiến (PlannedEndDate < today) (JOB-01).
     *
     * <p>Chạy định kỳ vào 00:05 hàng ngày. Mỗi gói được xử lý trong một transaction độc lập để
     * đảm bảo một gói lỗi không làm gián đoạn các gói khác (SRS 13.1).</p>
     *
     * @param today ngày hệ thống chạy job
     * @return số lượng gói tập đã được tự động mở lại thành công
     */
    public int resumeExpiredFreezes(LocalDate today) {
        if (today == null) {
            today = DateTimeUtil.today();
        }

        List<FreezeRecord> expiredFreezes;
        try {
            expiredFreezes = freezeRecordDAO.findExpiredOpenFreezes(today);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Lỗi khi quét danh sách bảo lưu hết hạn cho JOB-01", e);
            return 0;
        }

        if (expiredFreezes.isEmpty()) {
            LOGGER.info("JOB-01: Không có đợt bảo lưu nào đến hạn mở lại.");
            return 0;
        }

        int successCount = 0;

        for (FreezeRecord freeze : expiredFreezes) {
            try (Connection conn = openConnection()) {
                conn.setAutoCommit(false);

                try {
                    Subscription sub = freezeRecordDAO.findMembershipSubscription(conn, freeze.getSubscriptionId());
                    if (sub != null && sub.getStatus() == Subscription.Status.FROZEN) {
                        LocalDate actualEnd = freeze.getPlannedEndDate();
                        int daysFrozen = (int) ChronoUnit.DAYS.between(freeze.getStartDate(), actualEnd) + 1;
                        LocalDate newEndDate = sub.getEndDate().plusDays(daysFrozen);

                        // Cập nhật FreezeRecords.ActualEndDate = PlannedEndDate
                        freezeRecordDAO.updateActualEndDate(conn, freeze.getFreezeRecordId(), actualEnd);

                        // Mở lại gói tập
                        freezeRecordDAO.updateSubscriptionResume(conn, sub.getSubscriptionId(), "Active", newEndDate);

                        // Mở lại trạng thái hội viên
                        freezeRecordDAO.updateCustomerStatus(conn, sub.getCustomerId(), "Active");

                        // Ghi AuditLog hệ thống
                        AuditLog log = new AuditLog(
                                null,
                                (Role) null,
                                "JOB_RESUME_FREEZE",
                                "Subscription",
                                sub.getSubscriptionId(),
                                "Status",
                                "Frozen",
                                "Active",
                                "JOB-01 tự động mở lại sau khi hết kỳ bảo lưu #" + freeze.getFreezeRecordId()
                                        + " (+ " + daysFrozen + " ngày, hạn mới: " + DateTimeUtil.formatDate(newEndDate) + ")"
                        );
                        auditLogDAO.insert(conn, log);

                        conn.commit();
                        successCount++;
                    } else {
                        // Gói không còn ở trạng thái Frozen, đóng bản ghi bảo lưu
                        freezeRecordDAO.updateActualEndDate(conn, freeze.getFreezeRecordId(), freeze.getPlannedEndDate());
                        conn.commit();
                    }

                } catch (SQLException e) {
                    conn.rollback();
                    LOGGER.log(Level.WARNING, "Lỗi khi tự động mở lại bảo lưu id=" + freeze.getFreezeRecordId(), e);
                }

            } catch (SQLException e) {
                LOGGER.log(Level.SEVERE, "Lỗi kết nối khi chạy JOB-01 cho bảo lưu id=" + freeze.getFreezeRecordId(), e);
            }
        }

        LOGGER.info("JOB-01: Hoàn tất mở lại " + successCount + "/" + expiredFreezes.size() + " gói bảo lưu.");
        return successCount;
    }

    // =========================================================================
    // 6. DANH SÁCH BẢO LƯU GẦN ĐÂY
    // =========================================================================

    /**
     * Lấy danh sách các đợt bảo lưu gần đây phục vụ hiển thị trên bảng dữ liệu quầy Lễ tân.
     */
    public List<FreezeRecordItemView> getRecentFreezes(int limit) {
        try {
            return freezeRecordDAO.findRecentFreezes(limit);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Lỗi khi lấy danh sách bảo lưu gần đây", e);
            return List.of();
        }
    }

    /**
     * Lấy danh sách hội viên kèm gói tập Membership phục vụ hiển thị lưới dữ liệu tương tự GymMaster.
     */
    public PageResult<CustomerMembershipRow> getCustomerMembershipsPage(String keyword, int pageNumber, int pageSize) {
        try {
            long total = freezeRecordDAO.countCustomerMemberships(keyword);
            if (total == 0) {
                return new PageResult<>(List.of(), pageNumber, pageSize, 0);
            }
            List<CustomerMembershipRow> items = freezeRecordDAO.findCustomerMemberships(keyword, pageNumber, pageSize);
            return new PageResult<>(items, pageNumber, pageSize, total);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Lỗi khi lấy danh sách hội viên và gói tập", e);
            return new PageResult<>(List.of(), pageNumber, pageSize, 0);
        }
    }
}

