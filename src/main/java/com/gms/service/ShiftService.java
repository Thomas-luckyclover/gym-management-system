package com.gms.service;

import com.gms.constant.AppConstants;
import com.gms.constant.Messages;
import com.gms.constant.Role;
import com.gms.dao.AccountDAO;
import com.gms.dao.EmployeeDAO;
import com.gms.dao.ShiftDAO;
import com.gms.dto.PageResult;
import com.gms.dto.ServiceResult;
import com.gms.dto.ShiftDetailView;
import com.gms.dto.ShiftMetrics;
import com.gms.dto.ShiftTransactionItem;
import com.gms.entity.Account;
import com.gms.entity.Employee;
import com.gms.entity.Shift;
import com.gms.util.DBContext;
import com.gms.util.DateTimeUtil;
import com.gms.util.ValidationUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Lớp xử lý nghiệp vụ Ca làm việc và Đối soát tiền mặt tại quầy Lễ tân (Màn #39 - UC-25, Business Flow BF-03).
 *
 * <p>Triển khai đầy đủ các nhánh trong sơ đồ luồng nghiệp vụ BF-03 (SRS 2.3 & 7.3.1):</p>
 * <ul>
 *   <li><b>Trigger / Node 1:</b> Mở ca trực (openShift -> Shift: Open, UC-25).</li>
 *   <li><b>Node 2 / Main Flow:</b> Ghi nhận giao dịch tiền mặt vào ca trực (CBR-105).</li>
 *   <li><b>Node 2 / Alternative Flow A1:</b> Xác nhận tiền chuyển khoản đã về tài khoản quầy (confirmBankTransfer, CBR-77, CBR-105).</li>
 *   <li><b>Node 3 / Exception Flow E2:</b> Hủy đơn hàng tạo nhầm tại quầy trong ca mở, hoàn tiền, khôi phục voucher/slot (cancelOrder, CBR-11).</li>
 *   <li><b>Node 4 / Exception Flow E1:</b> Chặn chốt ca khi còn đơn quầy ở trạng thái Pending (CBR-78, MSG87).</li>
 *   <li><b>Node 5 / Main Flow:</b> Chốt ca khớp tiền 100% (closeShift, DBR-55).</li>
 *   <li><b>Node 5 / Alternative Flow A2:</b> Chốt ca lệch tiền kèm lý do giải trình bắt buộc (closeShift, BR-46).</li>
 * </ul>
 *
 * @author Antigravity
 */
public class ShiftService {

    private final ShiftDAO shiftDAO;
    private final AccountDAO accountDAO;
    private final EmployeeDAO employeeDAO;
    private final SelfServiceGuard selfServiceGuard;
    private final AuditLogService auditLogService;

    public ShiftService() {
        this.shiftDAO = new ShiftDAO();
        this.accountDAO = new AccountDAO();
        this.employeeDAO = new EmployeeDAO();
        this.selfServiceGuard = new SelfServiceGuard();
        this.auditLogService = new AuditLogService();
    }

    public ShiftService(ShiftDAO shiftDAO, AccountDAO accountDAO, EmployeeDAO employeeDAO,
                        SelfServiceGuard selfServiceGuard, AuditLogService auditLogService) {
        this.shiftDAO = shiftDAO;
        this.accountDAO = accountDAO;
        this.employeeDAO = employeeDAO;
        this.selfServiceGuard = selfServiceGuard;
        this.auditLogService = auditLogService;
    }

    protected Connection getConnection() throws SQLException {
        return DBContext.getConnection();
    }

    // ===== 1. Tải toàn bộ dữ liệu màn hình Ca làm việc =====

    /**
     * Lấy toàn bộ thông tin chi tiết của ca làm việc để hiển thị lên màn hình.
     *
     * @param accountId ID tài khoản Lễ tân từ session
     * @param requestedShiftId ID ca làm việc người dùng muốn xem (null nếu xem ca hiện tại)
     * @param search từ khóa tìm kiếm (mã đơn hoặc tên khách)
     * @param method bộ lọc hình thức thanh toán (all, cash, bank)
     * @param status bộ lọc trạng thái (all, paid, pending, cancelled)
     * @param page số trang hiện tại
     * @param pageSize số dòng mỗi trang
     * @return ServiceResult chứa ShiftDetailView đầy đủ dữ liệu
     */
    public ServiceResult<ShiftDetailView> getShiftDetail(int accountId, Integer requestedShiftId,
                                                        String search, String method, String status,
                                                        int page, int pageSize) {
        // B1: Xác thực quyền Lễ tân
        Employee employee = getActiveReceptionist(accountId);
        if (employee == null) {
            return ServiceResult.fail(Messages.MSG07_NO_PERMISSION);
        }

        try {
            LocalDate today = DateTimeUtil.today();

            // B2: Đảm bảo 2 ca hôm nay luôn tồn tại trong cơ sở dữ liệu nếu phòng tập mở cửa (CBR-104)
            shiftDAO.ensureShiftsForDate(today);

            // B3: Xác định ca cần hiển thị
            Shift shift = null;
            if (requestedShiftId != null && requestedShiftId > 0) {
                shift = shiftDAO.findById(requestedShiftId);
            }
            if (shift == null) {
                shift = shiftDAO.findCurrentOrActiveShift(today);
            }
            if (shift == null) {
                return ServiceResult.fail(String.format(Messages.MSG08_NOT_FOUND, "ca làm việc hôm nay"));
            }

            // B4: Lấy thông tin người mở ca và người chốt ca
            String openedByName = shiftDAO.findEmployeeFullName(shift.getOpenedByEmployeeId());
            String closedByName = shiftDAO.findEmployeeFullName(shift.getClosedByEmployeeId());

            // B5: Thống kê 4 thẻ KPI (DBR-55)
            ShiftMetrics metrics = shiftDAO.calculateShiftMetrics(shift.getShiftId());

            // B6: Lấy các đơn Pending trong ca để hiển thị Alert Banner
            List<ShiftTransactionItem> pendingOrders = shiftDAO.findPendingOrdersInShift(shift.getShiftId());

            // B7: Lấy danh sách các ca trong ngày để người dùng chuyển đổi
            List<Shift> todayShifts = shiftDAO.findTodayShifts(shift.getWorkDate());

            // B8: Lấy danh sách giao dịch có phân trang và bộ lọc
            int validPage = Math.max(page, 1);
            int validPageSize = pageSize > 0 ? pageSize : AppConstants.DEFAULT_PAGE_SIZE;

            int totalItems = shiftDAO.countTransactions(shift.getShiftId(), search, method, status);
            int offset = (validPage - 1) * validPageSize;

            List<ShiftTransactionItem> rawItems = shiftDAO.findTransactionsPage(
                    shift.getShiftId(), search, method, status, offset, validPageSize);

            // B9: Đánh giá quyền canCancel trên từng dòng giao dịch (CBR-11, CBR-115)
            List<ShiftTransactionItem> finalItems = new ArrayList<>();
            for (ShiftTransactionItem item : rawItems) {
                boolean canCancel = false;
                // Chỉ hủy được khi ca đang mở, đơn chưa hủy, và không phải đơn của chính Lễ tân
                if (shift.isOpen() && !item.isCancelled()) {
                    boolean isOwnProfile = isOrderOfStaff(accountId, item.getOrderId());
                    canCancel = !isOwnProfile;
                }

                boolean canConfirmTransfer = shift.isOpen() && item.isPending();

                finalItems.add(new ShiftTransactionItem(
                        item.getOrderId(), item.getOrderCode(), item.getCustomerName(),
                        item.getCustomerPhone(), item.getPackageName(), item.getCreatedAt(),
                        item.getPaymentMethod(), item.getAmount(), item.getOrderStatus(),
                        item.getCancelType(), item.getCancelReason(), item.getCancelledAt(),
                        canCancel, canConfirmTransfer
                ));
            }

            PageResult<ShiftTransactionItem> transactionsPage = new PageResult<>(
                    finalItems, validPage, validPageSize, totalItems);

            // B10: Đánh giá khả năng mở ca nếu ca đang ở trạng thái NotOpened
            boolean canOpen = false;
            LocalDateTime now = LocalDateTime.now(DateTimeUtil.GYM_ZONE);
            boolean unclosedPastShift = shift.isOpen() && shift.getWorkDate().isBefore(today);
            boolean overdue = shift.isOpen() && shift.getShiftType() == Shift.ShiftType.MORNING
                    && shift.getWorkDate().equals(today) && now.getHour() >= 13;

            String shiftNotOpenedReason = null;
            boolean pastShift = false;
            Shift blockingOpenShift = null;

            if (shift.isNotOpened()) {
                if (!today.equals(shift.getWorkDate())) {
                    canOpen = false;
                    if (shift.getWorkDate().isBefore(today)) {
                        shiftNotOpenedReason = "Ca làm việc của ngày đã qua trong quá khứ, không thể mở lại.";
                        pastShift = true;
                    } else {
                        shiftNotOpenedReason = "Ca làm việc thuộc ngày trong tương lai, chưa thể mở.";
                        pastShift = false;
                    }
                } else if (shiftDAO.isGymClosedOnDate(today)) {
                    canOpen = false;
                    shiftNotOpenedReason = "Hôm nay phòng tập đóng cửa cả ngày theo lịch nghỉ, không thể mở ca làm việc.";
                    pastShift = false;
                } else {
                    // Kiểm tra xem có ca nào khác đang Open không (kể cả hôm nay lẫn trong quá khứ)
                    Shift otherOpenShift = null;
                    try {
                        otherOpenShift = shiftDAO.findOtherOpenShift(shift.getShiftId());
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                    if (otherOpenShift == null) {
                        for (Shift s : todayShifts) {
                            if (s.getShiftId() != shift.getShiftId() && s.isOpen()) {
                                otherOpenShift = s;
                                break;
                            }
                        }
                    }

                    if (otherOpenShift != null) {
                        canOpen = false;
                        blockingOpenShift = otherOpenShift;
                        if (otherOpenShift.getWorkDate().isBefore(today)) {
                            shiftNotOpenedReason = "Ca làm việc ngày " + DateTimeUtil.formatDate(otherOpenShift.getWorkDate())
                                    + " (" + otherOpenShift.getShiftType().getDisplayName() + ") chưa được chốt sổ. Vui lòng chốt ca cũ trước.";
                        } else {
                            shiftNotOpenedReason = "Hiện đang có " + otherOpenShift.getShiftType().getDisplayName()
                                    + " đang mở. Vui lòng chốt ca trước.";
                        }
                        pastShift = false;
                    } else if (shift.getShiftType() == Shift.ShiftType.MORNING) {
                        // Ca sáng: kiểm tra xem Ca chiều đã từng được mở hoặc đã chốt chưa
                        Shift afternoonShift = null;
                        for (Shift s : todayShifts) {
                            if (s.getShiftType() == Shift.ShiftType.AFTERNOON) {
                                afternoonShift = s;
                                break;
                            }
                        }
                        if (afternoonShift != null && afternoonShift.isOpen()) {
                            canOpen = false;
                            blockingOpenShift = afternoonShift;
                            shiftNotOpenedReason = "Ca chiều đang mở. Ca sáng hôm nay đã kết thúc và không thể mở lại.";
                            pastShift = true;
                        } else if (afternoonShift != null && afternoonShift.isClosed()) {
                            canOpen = false;
                            shiftNotOpenedReason = "Ca chiều hôm nay đã chốt. Ca sáng đã kết thúc và không thể mở lại.";
                            pastShift = true;
                        } else if (now.getHour() >= 13) {
                            canOpen = false;
                            shiftNotOpenedReason = "Đã quá thời gian ca sáng (kết thúc lúc 13:00). Vui lòng mở Ca chiều.";
                            pastShift = true;
                        } else {
                            canOpen = true;
                            pastShift = false;
                        }
                    } else if (shift.getShiftType() == Shift.ShiftType.AFTERNOON) {
                        // Ca chiều: nếu Ca sáng đang mở thì phải chốt ca sáng trước
                        Shift morningShift = null;
                        for (Shift s : todayShifts) {
                            if (s.getShiftType() == Shift.ShiftType.MORNING) {
                                morningShift = s;
                                break;
                            }
                        }
                        if (morningShift != null && morningShift.isOpen()) {
                            canOpen = false;
                            blockingOpenShift = morningShift;
                            shiftNotOpenedReason = "Ca sáng đang mở. Vui lòng chốt ca sáng trước khi mở ca chiều.";
                            pastShift = false;
                        } else if (morningShift != null && morningShift.isNotOpened() && now.getHour() < 13) {
                            canOpen = false;
                            shiftNotOpenedReason = "Chưa đến giờ bắt đầu Ca chiều (từ 13:00). Vui lòng mở Ca sáng.";
                            pastShift = false;
                        } else {
                            canOpen = true;
                            pastShift = false;
                        }
                    } else {
                        canOpen = true;
                        pastShift = false;
                    }
                }
            }

            ShiftDetailView view = new ShiftDetailView(shift, openedByName, closedByName,
                    metrics, transactionsPage, pendingOrders, todayShifts,
                    search, method, status, canOpen, shiftNotOpenedReason, pastShift,
                    unclosedPastShift, overdue, blockingOpenShift);

            return ServiceResult.ok(view);

        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    // ===== 2. Nghiệp vụ Mở ca làm việc =====

    /**
     * Mở ca làm việc được chỉ định (BF-03: Trigger & Node 1 - Open shift -> Shift Open, CBR-104, CBR-105).
     *
     * <p>Quy tắc kiểm soát chặt chẽ:</p>
     * <ul>
     *   <li>Chỉ Lễ tân đang làm việc mới có quyền mở ca.</li>
     *   <li>Chỉ mở được ca của ngày hôm nay (không mở ca quá khứ hoặc tương lai).</li>
     *   <li>Không mở ca vào ngày phòng tập đóng cửa cả ngày theo OperatingHourExceptions.</li>
     *   <li>Mỗi thời điểm chỉ có tối đa 1 ca được mở trong ngày.</li>
     *   <li>Quy tắc tuần tự: Nếu Ca chiều đã mở hoặc đã chốt, Ca sáng KHÔNG THỂ mở lại.</li>
     *   <li>Ca sáng chỉ được mở trước 13:00. Ca chiều nếu Ca sáng chưa mở thì chỉ được mở từ 13:00.</li>
     * </ul>
     *
     * @param accountId ID tài khoản Lễ tân thực hiện
     * @param shiftId ID ca làm việc cần mở
     * @return ServiceResult thành công hoặc thất bại kèm thông báo
     */
    public ServiceResult<Void> openShift(int accountId, int shiftId) {
        // B1: Xác thực quyền Lễ tân
        Employee employee = getActiveReceptionist(accountId);
        if (employee == null) {
            return ServiceResult.fail(Messages.MSG07_NO_PERMISSION);
        }

        LocalDate today = DateTimeUtil.today();

        // B2: Kiểm tra trạng thái ca hiện tại
        Shift shift;
        try {
            shift = shiftDAO.findById(shiftId);
        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }

        if (shift == null) {
            return ServiceResult.fail(String.format(Messages.MSG08_NOT_FOUND, "ca làm việc"));
        }
        if (!shift.isNotOpened()) {
            return ServiceResult.fail("Ca làm việc này đã được mở hoặc đã chốt từ trước.");
        }

        // B3: Kiểm tra ngày làm việc — chỉ được mở ca của ngày hôm nay
        if (!today.equals(shift.getWorkDate())) {
            if (shift.getWorkDate().isBefore(today)) {
                return ServiceResult.fail("Không thể mở ca làm việc của ngày trong quá khứ.");
            } else {
                return ServiceResult.fail("Không thể mở trước ca làm việc của ngày trong tương lai.");
            }
        }

        // B4: Kiểm tra ngày nghỉ toàn gym
        try {
            if (shiftDAO.isGymClosedOnDate(shift.getWorkDate())) {
                return ServiceResult.fail("Hôm nay phòng tập đóng cửa cả ngày theo lịch nghỉ, không thể mở ca làm việc.");
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }

        // B5: Kiểm tra thứ tự ca và xung đột ca trong ngày
        List<Shift> todayShifts;
        try {
            todayShifts = shiftDAO.findTodayShifts(shift.getWorkDate());
        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }

        // Không được mở khi có bất kỳ ca nào khác đang Open (toàn hệ thống)
        Shift otherOpen = null;
        try {
            otherOpen = shiftDAO.findOtherOpenShift(shiftId);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        if (otherOpen == null) {
            for (Shift s : todayShifts) {
                if (s.getShiftId() != shiftId && s.isOpen()) {
                    otherOpen = s;
                    break;
                }
            }
        }
        if (otherOpen != null) {
            if (otherOpen.getWorkDate().isBefore(today)) {
                return ServiceResult.fail("Không thể mở ca mới vì " + otherOpen.getShiftType().getDisplayName()
                        + " ngày " + DateTimeUtil.formatDate(otherOpen.getWorkDate())
                        + " chưa được chốt sổ. Vui lòng chốt ca cũ trước.");
            } else {
                return ServiceResult.fail("Hiện đang có " + otherOpen.getShiftType().getDisplayName()
                        + " đang mở. Vui lòng chốt ca trước khi mở ca mới.");
            }
        }

        LocalDateTime now = LocalDateTime.now(DateTimeUtil.GYM_ZONE);

        // Quy tắc tuần tự theo thời gian:
        if (shift.getShiftType() == Shift.ShiftType.MORNING) {
            // Ca sáng: nếu Ca chiều đã mở hoặc đã chốt -> không thể mở lại Ca sáng
            for (Shift s : todayShifts) {
                if (s.getShiftType() == Shift.ShiftType.AFTERNOON) {
                    if (s.isClosed()) {
                        return ServiceResult.fail("Ca sáng không thể mở vì Ca chiều của ngày hôm nay đã chốt.");
                    }
                    if (s.isOpen()) {
                        return ServiceResult.fail("Ca sáng không thể mở vì Ca chiều của ngày hôm nay đang mở.");
                    }
                }
            }
            // Nếu đã quá 13:00 -> ca sáng đã kết thúc
            if (now.getHour() >= 13) {
                return ServiceResult.fail("Đã quá thời gian ca sáng (kết thúc lúc 13:00). Vui lòng mở Ca chiều.");
            }
        } else if (shift.getShiftType() == Shift.ShiftType.AFTERNOON) {
            // Ca chiều: nếu Ca sáng đang mở -> phải chốt Ca sáng trước
            for (Shift s : todayShifts) {
                if (s.getShiftType() == Shift.ShiftType.MORNING) {
                    if (s.isOpen()) {
                        return ServiceResult.fail("Hiện đang có Ca sáng đang mở. Vui lòng chốt Ca sáng trước khi mở Ca chiều.");
                    }
                    if (s.isNotOpened() && now.getHour() < 13) {
                        return ServiceResult.fail("Chưa đến giờ bắt đầu Ca chiều (từ 13:00). Vui lòng mở Ca sáng.");
                    }
                }
            }
        }

        // B6: Cập nhật trạng thái ca sang Open trong Transaction
        try (Connection conn = getConnection()) {
            if (conn != null) {
                conn.setAutoCommit(false);
            }

            try {
                int updated = shiftDAO.openShift(conn, shiftId, employee.getEmployeeId(), now);
                if (updated == 0) {
                    if (conn != null) conn.rollback();
                    return ServiceResult.fail(Messages.MSG22_CONCURRENT_EDIT);
                }

                // Ghi nhật ký thao tác nhạy cảm (GBR-07, CBR-112)
                auditLogService.logInTransaction(conn, accountId, Role.RECEPTIONIST,
                        "Mở ca làm việc", "Shifts", shiftId, "Status",
                        "NotOpened", "Open", "Mở " + shift.getShiftType().getDisplayName());

                if (conn != null) conn.commit();
                return ServiceResult.ok(null);

            } catch (SQLException ex) {
                if (conn != null) conn.rollback();
                throw ex;
            }

        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    // ===== 3. Nghiệp vụ Đối soát & Chốt ca làm việc =====

    /**
     * Đối soát tiền mặt và chốt ca làm việc (BF-03: Node 4, 5 & 6; UC-25, CBR-78, DBR-55, BR-46).
     *
     * <p>Xử lý các nhánh luồng chốt ca:</p>
     * <ul>
     *   <li><b>BF-03 Exception Flow E1:</b> Kiểm tra đơn Pending trong ca. Nếu còn -> Chặn chốt ca, trả về MSG87 (CBR-78).</li>
     *   <li><b>BF-03 Main Flow:</b> Tiền đếm được khớp tiền sổ sách (discrepancy == 0) -> Chốt ca thành công, lưu trạng thái Closed.</li>
     *   <li><b>BF-03 Alternative Flow A2:</b> Tiền đếm được bị chênh lệch -> Bắt buộc nhập lý do giải trình 5–500 ký tự (BR-46).</li>
     * </ul>
     *
     * @param accountId ID tài khoản Lễ tân thực hiện
     * @param shiftId ID ca làm việc cần chốt
     * @param countedCash số tiền mặt thực tế đếm được trong két
     * @param varianceReason lý do chênh lệch (bắt buộc ≥ 5 ký tự khi có chênh lệch)
     * @return ServiceResult thành công hoặc thất bại kèm thông báo
     */
    public ServiceResult<Void> closeShift(int accountId, int shiftId, long countedCash, String varianceReason) {
        // B1: Xác thực quyền Lễ tân
        Employee employee = getActiveReceptionist(accountId);
        if (employee == null) {
            return ServiceResult.fail(Messages.MSG07_NO_PERMISSION);
        }

        if (countedCash < 0) {
            return ServiceResult.fail(Messages.MSG17_POSITIVE_NUMBER);
        }

        // B2: Kiểm tra trạng thái ca
        Shift shift;
        try {
            shift = shiftDAO.findById(shiftId);
        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }

        if (shift == null) {
            return ServiceResult.fail(String.format(Messages.MSG08_NOT_FOUND, "ca làm việc"));
        }
        if (!shift.isOpen()) {
            return ServiceResult.fail("Chỉ có thể chốt ca đang ở trạng thái Đang mở.");
        }

        // B3: Kiểm tra các đơn Pending — CBR-78: Shift không thể chốt nếu còn đơn Pending
        List<ShiftTransactionItem> pendingOrders;
        try {
            pendingOrders = shiftDAO.findPendingOrdersInShift(shiftId);
        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }

        if (!pendingOrders.isEmpty()) {
            return ServiceResult.fail(String.format(Messages.MSG87_CLOSE_SHIFT_PENDING_ORDERS, pendingOrders.size()));
        }

        // B4: Tính tiền mặt kỳ vọng theo sổ sách (DBR-55)
        long expectedCash;
        try {
            ShiftMetrics metrics = shiftDAO.calculateShiftMetrics(shiftId);
            expectedCash = metrics.getCashTotal();
        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }

        // B5: Kiểm tra chênh lệch tiền mặt (BR-46)
        long discrepancy = countedCash - expectedCash;
        String trimmedReason = varianceReason != null ? varianceReason.trim() : null;

        if (discrepancy != 0) {
            if (ValidationUtil.isNullOrEmpty(trimmedReason) || trimmedReason.length() < AppConstants.REASON_MIN_LENGTH) {
                return ServiceResult.fail("Số tiền đếm được bị chênh lệch so với sổ sách. "
                        + String.format(Messages.MSG19_TOO_SHORT, AppConstants.REASON_MIN_LENGTH));
            }
            if (trimmedReason.length() > AppConstants.REASON_MAX_LENGTH) {
                return ServiceResult.fail(String.format(Messages.MSG10_MAX_LENGTH, AppConstants.REASON_MAX_LENGTH));
            }
        }

        // B6: Mở transaction cập nhật trạng thái ca sang Closed và ghi nhật ký
        try (Connection conn = getConnection()) {
            if (conn != null) {
                conn.setAutoCommit(false);
            }

            try {
                LocalDateTime now = LocalDateTime.now(DateTimeUtil.GYM_ZONE);
                int updated = shiftDAO.closeShift(conn, shiftId, employee.getEmployeeId(), now,
                        expectedCash, countedCash, discrepancy != 0 ? trimmedReason : null);

                if (updated == 0) {
                    if (conn != null) conn.rollback();
                    return ServiceResult.fail(Messages.MSG22_CONCURRENT_EDIT);
                }

                // B7: Ghi nhật ký chốt ca
                String auditNote = discrepancy == 0
                        ? "Tiền mặt khớp sổ (" + expectedCash + "đ)"
                        : "Lệch " + (discrepancy > 0 ? "thừa " : "thiếu ") + Math.abs(discrepancy) + "đ: " + trimmedReason;

                auditLogService.logInTransaction(conn, accountId, Role.RECEPTIONIST,
                        "Chốt ca làm việc", "Shifts", shiftId, "Status",
                        "Open", "Closed", auditNote);

                if (conn != null) conn.commit();
                return ServiceResult.ok(null);

            } catch (SQLException ex) {
                if (conn != null) conn.rollback();
                throw ex;
            }

        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    // ===== 4. Xác nhận Đơn chuyển khoản tại quầy =====

    /**
     * Xác nhận đơn chuyển khoản đã nhận được tiền (BF-03: Alternative Flow A1 - Confirm transfer once money has arrived, CBR-77, CBR-105).
     *
     * @param accountId ID tài khoản Lễ tân thực hiện
     * @param orderId ID đơn hàng
     * @return ServiceResult thành công hoặc thất bại kèm thông báo
     */
    public ServiceResult<Void> confirmBankTransfer(int accountId, int orderId) {
        // B1: Xác thực quyền Lễ tân
        Employee employee = getActiveReceptionist(accountId);
        if (employee == null) {
            return ServiceResult.fail(Messages.MSG07_NO_PERMISSION);
        }

        try (Connection conn = getConnection()) {
            if (conn != null) {
                conn.setAutoCommit(false);
            }

            try {
                // B2: Xác thực đơn hàng thuộc ca đang mở và đang ở trạng thái Pending
                Shift shift = getShiftOfOrder(orderId);
                if (shift == null || !shift.isOpen()) {
                    if (conn != null) conn.rollback();
                    return ServiceResult.fail("Đơn hàng không thuộc ca làm việc đang mở.");
                }

                LocalDateTime now = LocalDateTime.now(DateTimeUtil.GYM_ZONE);
                int updated = shiftDAO.confirmBankTransfer(conn, orderId, employee.getEmployeeId(), now);
                if (updated == 0) {
                    if (conn != null) conn.rollback();
                    return ServiceResult.fail("Đơn hàng này không còn ở trạng thái chờ xác nhận.");
                }

                // B3: Ghi nhật ký thao tác
                auditLogService.logInTransaction(conn, accountId, Role.RECEPTIONIST,
                        "Xác nhận chuyển khoản", "Orders", orderId, "Status",
                        "Pending", "Paid", "Lễ tân xác nhận đã nhận đủ tiền chuyển khoản");

                if (conn != null) conn.commit();
                return ServiceResult.ok(null);

            } catch (SQLException ex) {
                if (conn != null) conn.rollback();
                throw ex;
            }

        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    // ===== 5. Nghiệp vụ Hủy đơn hàng tại quầy =====

    /**
     * Hủy đơn hàng tại quầy trong ca mở (BF-03: Exception Flow E2 - Cancel wrongly sold order in shift, CBR-11, CBR-115, BR-44, DBR-36).
     *
     * @param accountId ID tài khoản Lễ tân thực hiện
     * @param orderId ID đơn hàng cần hủy
     * @param reason lý do hủy (tối thiểu 5 ký tự)
     * @return ServiceResult thành công hoặc thất bại kèm thông báo
     */
    public ServiceResult<Void> cancelOrder(int accountId, int orderId, String reason) {
        // B1: Xác thực quyền Lễ tân
        Employee employee = getActiveReceptionist(accountId);
        if (employee == null) {
            return ServiceResult.fail(Messages.MSG07_NO_PERMISSION);
        }

        // B2: Kiểm tra độ dài lý do hủy (GBR-09: 5–500 ký tự)
        String trimmedReason = reason != null ? reason.trim() : null;
        if (ValidationUtil.isNullOrEmpty(trimmedReason) || trimmedReason.length() < AppConstants.REASON_MIN_LENGTH) {
            return ServiceResult.fail(String.format(Messages.MSG19_TOO_SHORT, AppConstants.REASON_MIN_LENGTH));
        }
        if (trimmedReason.length() > AppConstants.REASON_MAX_LENGTH) {
            return ServiceResult.fail(String.format(Messages.MSG10_MAX_LENGTH, AppConstants.REASON_MAX_LENGTH));
        }

        // B3: Chặn Lễ tân hủy đơn của chính mình (CBR-115)
        if (isOrderOfStaff(accountId, orderId)) {
            return ServiceResult.fail(Messages.MSG76_STAFF_OWN_CUSTOMER_PROFILE);
        }

        try (Connection conn = getConnection()) {
            if (conn != null) {
                conn.setAutoCommit(false);
            }

            try {
                // B4: Kiểm tra ca của đơn hàng — CBR-11: chỉ được hủy trong ca mở đã tạo nó
                Shift shift = getShiftOfOrder(orderId);
                if (shift == null || !shift.isOpen()) {
                    if (conn != null) conn.rollback();
                    return ServiceResult.fail(Messages.MSG97_CANCEL_ORDER_OUTSIDE_SHIFT);
                }

                LocalDateTime now = LocalDateTime.now(DateTimeUtil.GYM_ZONE);
                int cancelled = shiftDAO.cancelCounterOrder(conn, orderId, trimmedReason, now);
                if (cancelled == 0) {
                    if (conn != null) conn.rollback();
                    return ServiceResult.fail("Đơn hàng này không còn ở trạng thái cho phép hủy.");
                }

                // B5: Ghi nhật ký thao tác nhạy cảm
                auditLogService.logInTransaction(conn, accountId, Role.RECEPTIONIST,
                        "Hủy đơn hàng tại quầy", "Orders", orderId, "Status",
                        "Paid/Pending", "Cancelled", trimmedReason);

                if (conn != null) conn.commit();
                return ServiceResult.ok(null);

            } catch (SQLException ex) {
                if (conn != null) conn.rollback();
                throw ex;
            }

        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    // ===== Hàm hỗ trợ nội bộ =====

    /**
     * Xác thực tài khoản Lễ tân đang hoạt động.
     */
    private Employee getActiveReceptionist(int accountId) {
        try {
            Account account = accountDAO.findById(accountId);
            if (account == null) {
                return null;
            }
            Employee employee = employeeDAO.findByPersonId(account.getPersonId());
            if (employee == null || employee.getPosition() != Role.RECEPTIONIST
                    || employee.getStatus() != Employee.Status.WORKING) {
                return null;
            }
            return employee;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Kiểm tra xem đơn hàng có thuộc hồ sơ khách của chính tài khoản nhân viên đang đăng nhập không (CBR-115).
     */
    protected boolean isOrderOfStaff(int staffAccountId, int orderId) {
        String sql = "SELECT HolderCustomerId FROM Orders WHERE OrderId = ?";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, orderId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int customerId = rs.getInt("HolderCustomerId");
                    return selfServiceGuard.isOwnCustomerProfile(staffAccountId, customerId);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Lấy thông tin ca làm việc gắn với đơn hàng.
     */
    protected Shift getShiftOfOrder(int orderId) {
        String sql = "SELECT ShiftId FROM Orders WHERE OrderId = ? AND Channel = 'Counter'";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, orderId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int shiftId = rs.getInt("ShiftId");
                    if (!rs.wasNull()) {
                        return shiftDAO.findById(shiftId);
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }
}
