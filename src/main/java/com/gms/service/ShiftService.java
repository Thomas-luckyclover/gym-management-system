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
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * Nghiệp vụ Ca làm việc và Đối soát tiền mặt tại quầy Lễ tân (Màn #39 - UC-25, BF-06).
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

    /**
     * CBR-123: Nếu ca đang mở thuộc ngày hôm trước (quên chốt) thì chặn bán tại quầy (trả về MSG324).
     */
    public static String pastDayShiftMessage(Shift shift, LocalDate today) {
        if (shift == null || !shift.isOpen() || !shift.getWorkDate().isBefore(today)) {
            return null;
        }
        return String.format(Messages.MSG324_SHIFT_PAST_DAY_NOT_CLOSED,
                shift.getShiftType().getDisplayName(), DateTimeUtil.formatDate(shift.getWorkDate()));
    }

    /**
     * Thanh cảnh báo ca trên các màn Lễ tân (được gọi bởi ReceptionistAreaFilter).
     */
    public String findShiftNotice(LocalDateTime now, boolean saleScreen) {
        try {
            LocalDate today = now.toLocalDate();
            Shift shift = shiftDAO.findCurrentOrActiveShift(today);
            if (shift == null || !shift.isOpen()) {
                return saleScreen ? Messages.MSG109_SHIFT_NOT_OPEN_FOR_SALE : null;
            }
            LocalTime end = shift.getShiftType() == Shift.ShiftType.MORNING
                    ? AppConstants.AFTERNOON_SHIFT_START
                    : PtScheduleService.parseOpeningHours(
                            new SystemConfigService().getString(SystemConfigService.OPENING_HOURS))[1];
            boolean overdue = shift.getWorkDate().isBefore(today) || !now.toLocalTime().isBefore(end);
            return overdue
                    ? String.format(Messages.MSG323_SHIFT_OVERDUE_NOT_CLOSED,
                            shift.getShiftType().getDisplayName(), DateTimeUtil.formatDate(shift.getWorkDate()))
                    : null;
        } catch (SQLException | RuntimeException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Tự động chọn ca phù hợp nhất để hiển thị hoặc làm việc (CBR-104):
     * 1. Ca ngày cũ quên chưa chốt -> 2. Ca đang Open hôm nay -> 3. Ca chiều nếu Ca sáng đã chốt -> 4. Xét theo mốc 13:00.
     */
    public Shift findCurrentOrActiveShift(LocalDate today) throws SQLException {
        Shift unclosedPast = shiftDAO.findUnclosedPastShift(today);
        if (unclosedPast != null) {
            return unclosedPast;
        }

        List<Shift> shifts = shiftDAO.findTodayShifts(today);//lấy 2 ca trong ngày hôm nay
        if (shifts == null || shifts.isEmpty()) {
            return null;
        }

        Shift morning = null;
        Shift afternoon = null;
        for (Shift s : shifts) {
            if (s.isOpen()) {//nếu có ca đang mở thì ưu tiên hiển thị ca đó
                return s;
            }
            if (s.getShiftType() == Shift.ShiftType.MORNING) {
                morning = s;
            } else {
                afternoon = s;
            }
        }

        if (afternoon != null && (afternoon.isClosed() || (morning != null && morning.isClosed()))) {
            //nếu ca chiều đã chốt hoặc ca sáng đã chốt thì ưu tiên hiển thị ca chiều
            return afternoon;
        }


        // Khi cả 2 ca đều chưa mở/chưa chốt: xét theo giờ thực tế
        // - Từ 13:00 trở đi: nhân viên vào quầy buổi chiều -> gợi ý Ca Chiều
        // - Trước 13:00: gợi ý Ca Sáng (nếu thiếu ca sáng thì lấy ca đầu tiên)
        boolean isAfternoonTime = !LocalDateTime.now(DateTimeUtil.GYM_ZONE)
                .toLocalTime().isBefore(AppConstants.AFTERNOON_SHIFT_START);
        if (isAfternoonTime && afternoon != null) {
            return afternoon;
        }
        return morning != null ? morning : shifts.get(0);
    }

    // ===== 1. Tải toàn bộ dữ liệu màn hình Ca làm việc =====

    public ServiceResult<ShiftDetailView> getShiftDetail(int accountId, Integer requestedShiftId,
                                                        String search, String method, String status,
                                                        int page, int pageSize) {
        // B1: Kiểm tra tài khoản Lễ tân đang làm việc
        Employee employee = getActiveReceptionist(accountId);
        if (employee == null) {
            return ServiceResult.fail(Messages.MSG07_NO_PERMISSION);
        }

        try {
            LocalDate today = DateTimeUtil.today();

            // B2: Đảm bảo 2 ca hôm nay luôn tồn tại trong DB nếu phòng tập mở cửa (CBR-104)
            shiftDAO.ensureShiftsForDate(today);

            // B3: Xác định ca cần hiển thị (theo shiftId trên URL hoặc ca hiện tại)
            //Lúc mới vào trang shiftId = null, khi bấm chuyển ca thì shiftId = id của ca được chọn
            Shift shift = (requestedShiftId != null && requestedShiftId > 0)
                    ? shiftDAO.findById(requestedShiftId)
                    : null;
            if (shift == null) {
                //tìm ca hiện tại hoặc ca đang mở trong ngày hôm nay (CBR-104)
                shift = findCurrentOrActiveShift(today);
            }
            if (shift == null) {
                return ServiceResult.fail(String.format(Messages.MSG08_NOT_FOUND, "ca làm việc hôm nay"));
            }

            // B4: Tải thông tin nhân viên, KPI, đơn chờ xử lý và danh sách ca trong ngày
            //lấy tên người mở ca (nếu có) để hiển thị trên màn hình chi tiết ca làm việc
            String openedByName = shiftDAO.findEmployeeFullName(shift.getOpenedByEmployeeId());
            //lấy tên người chốt ca (nếu có) để hiển thị trên màn hình chi tiết ca làm việc
            String closedByName = shiftDAO.findEmployeeFullName(shift.getClosedByEmployeeId());
            //hển thị 4 thẻ kpi: tổng tiền mặt, tổng chuyển khoản, tổng thu, tổng đơn chờ xử lý
            ShiftMetrics metrics = shiftDAO.calculateShiftMetrics(shift.getShiftId());
            //Lấy danh sách đơn Pending trong ca để hiển thị cảnh báo và chặn chốt ca
            List<ShiftTransactionItem> pendingOrders = shiftDAO.findPendingOrdersInShift(shift.getShiftId());
            //Lấy 2 ca trong ngày hôm nay thứ tự sáng > chiều để hiển thị danh sách ca làm việc trên màn hình chi tiết ca
            List<Shift> todayShifts = shiftDAO.findTodayShifts(shift.getWorkDate());

            // B5: Tải danh sách giao dịch có lọc và phân trang
            int validPage = Math.max(page, 1);//chuẩn hóa số trang hiện tại
            int validPageSize = pageSize > 0 ? pageSize : AppConstants.DEFAULT_PAGE_SIZE;//chuẩn hóa số dòng hiển thị mỗi trang
            int totalItems = shiftDAO.countTransactions(shift.getShiftId(), search, method, status);
            int offset = (validPage - 1) * validPageSize;//số dòng cần bỏ qua để phân trang

            List<ShiftTransactionItem> items = shiftDAO.findTransactionsPage(
                    shift.getShiftId(), search, method, status, offset, validPageSize);

            // B6: Gắn cờ quyền thao tác trên từng giao dịch (CBR-11, CBR-77, CBR-115)
            for (ShiftTransactionItem item : items) {
                item.setCanCancel(shift.isOpen() && !item.isCancelled() && !isOrderOfStaff(accountId, item.getOrderId()));
                item.setCanConfirmTransfer(shift.isOpen() && item.isPending());
            }//lễ tân chỉ được bấm hủy đơn khi đồng thời thỏa mãn các điều kiện
            // 1. Ca đang mở (Open) 2. Đơn chưa hủy (không phải Cancelled) 3. Đơn không phải do chính lễ tân này lập ra
            //nút xác nhận chuyển khoản chỉ hiển thị khi đồng thời thỏa mãn các điều kiện
            // 1. Ca đang mở (Open) 2. Đơn đang chờ xác nhận (Pending)
            PageResult<ShiftTransactionItem> transactionsPage = new PageResult<>(
                    items, validPage, validPageSize, totalItems);//một trang đối tượng có phân trang

            // B7: Đánh giá trạng thái cảnh báo và khả năng mở ca (nếu ca đang NotOpened)
            LocalDateTime now = LocalDateTime.now(DateTimeUtil.GYM_ZONE);
            boolean unclosedPastShift = shift.isOpen() && shift.getWorkDate().isBefore(today);//bắt lỗi ca hôm qua quên chưa chốt
            boolean overdue = shift.isOpen()//bắt lỗi ca sáng bị trễ giờ
                    && shift.getShiftType() == Shift.ShiftType.MORNING
                    && shift.getWorkDate().equals(today)
                    && !now.toLocalTime().isBefore(AppConstants.AFTERNOON_SHIFT_START);

            boolean canOpen = false;//cờ cho phép mở ca (true) hoặc không (false)
            boolean pastShift = false;//cờ đánh dấu ca làm việc thực sự đã qua trong quá khứ (không thể mở lại)
            String shiftNotOpenedReason = null;//lý do không cho phép mở ca (khi notOpened = true và canOpen = false)
            Shift blockingOpenShift = null;//lưu lại ca làm việc đang mở khiến ca này bị chặn

            //khối lệnh này chạy khi ca ở trạng thái chưa mở
            //kiểm tra xem ca này được phép mở hay không nếu không thì lý do cụ thể là gì
            if (shift.isNotOpened()) {
                if (!today.equals(shift.getWorkDate())) {//kiểm tra ngày của ca này là ca quá khư hoặc tương lai
                    if (shift.getWorkDate().isBefore(today)) {//kiểm tra ngày của ca này có trước hôm nay hay không
                        shiftNotOpenedReason = "Ca làm việc của ngày đã qua trong quá khứ, không thể mở lại.";
                        pastShift = true;
                    } else {
                        shiftNotOpenedReason = "Ca làm việc thuộc ngày trong tương lai, chưa thể mở.";
                    }
                } else if (shiftDAO.isGymClosedOnDate(today)) {//kiểm tra hôm nay có phải ngày nghỉ của phòng tập hay không
                    shiftNotOpenedReason = Messages.MSG258_SHIFT_GYM_CLOSED;
                } else {

                    Shift afternoonShift = findShiftByType(todayShifts, Shift.ShiftType.AFTERNOON);
                    boolean isMorning = shift.getShiftType() == Shift.ShiftType.MORNING;
                    boolean afternoonClosed = afternoonShift != null && afternoonShift.isClosed();
                    boolean afterMorningEnd = !now.toLocalTime().isBefore(AppConstants.AFTERNOON_SHIFT_START);//sau 13:00

                    Shift otherOpen = null;
                    if (isMorning && afternoonClosed) {//là ca sáng và ca chiều đã chốt
                        shiftNotOpenedReason = "Ca chiều hôm nay đã chốt. Ca sáng đã kết thúc và không thể mở lại.";
                        pastShift = true;
                    } else if (isMorning && afterMorningEnd) {//là ca sáng và sau 13:00
                        shiftNotOpenedReason = Messages.MSG264_SHIFT_MORNING_OVER;
                        pastShift = true;
                    } else if ((otherOpen = findAnyOtherOpenShift(shift.getShiftId())) != null) {//nếu có ca khác đang mở
                        blockingOpenShift = otherOpen;//lưu lại ca đang mở
                        shiftNotOpenedReason = otherOpen.getWorkDate().isBefore(today)//kiểm tra xem ca đang mở là ca của hôm qua hay hôm nay
                                ? "Ca làm việc ngày " + DateTimeUtil.formatDate(otherOpen.getWorkDate())
                                        + " (" + otherOpen.getShiftType().getDisplayName() + ") chưa được chốt sổ. Vui lòng chốt ca cũ trước."
                                : String.format(Messages.MSG261_SHIFT_OTHER_OPEN_SHORT, otherOpen.getShiftType().getDisplayName());
                    } else if (isMorning) {
                        canOpen = true;
                    } else {//trường hợp đang ở ca chiều
                        Shift morningShift = findShiftByType(todayShifts, Shift.ShiftType.MORNING);
                        LocalTime afternoonEnd = afternoonShiftEnd();//lấy giờ đóng cửa của ca chiều (22:00)
                        if (afternoonEnd != null && !now.toLocalTime().isBefore(afternoonEnd)) {
                            // Đã quá giờ đóng cửa: ca chiều hôm nay đã qua, không mở được nữa
                            shiftNotOpenedReason = afternoonShiftOverMessage(afternoonEnd);
                            pastShift = true;
                        } else if (morningShift != null && morningShift.isNotOpened()
                                && now.toLocalTime().isBefore(AppConstants.AFTERNOON_SHIFT_START)) {
                            //Ca sáng chưa mở và chưa quá 13:00: không được nhảy cóc mở ca chiều
                            shiftNotOpenedReason = Messages.MSG266_SHIFT_AFTERNOON_NOT_YET;
                        } else {//còn lại
                            canOpen = true;
                        }
                    }
                }
            }

            ShiftDetailView view = new ShiftDetailView(shift, openedByName, closedByName,
                    metrics, transactionsPage, pendingOrders, todayShifts,
                    search, method, status, canOpen, shiftNotOpenedReason, pastShift,
                    unclosedPastShift, overdue, blockingOpenShift);
            return ServiceResult.ok(view);//trả về kết quả khi thực hiện xong 1 hàm service kèm dữ liệu của nó

        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    // ===== 2. Nghiệp vụ Mở ca làm việc =====

    public ServiceResult<Void> openShift(int accountId, int shiftId) {
        Employee employee = getActiveReceptionist(accountId);
        if (employee == null) {
            return ServiceResult.fail(Messages.MSG07_NO_PERMISSION);
        }

        try {
            // B1: Kiểm tra trạng thái và ngày làm việc của ca
            Shift shift = shiftDAO.findById(shiftId);//tìm ca làm việc theo shiftId
            if (shift == null) {//kiểm tra xem shiftId gửi lên có tồn tại trong DB hay không, tránh NullPointerException cho đoạn bên dưới
                return ServiceResult.fail(String.format(Messages.MSG08_NOT_FOUND, "ca làm việc"));
            }
            if (!shift.isNotOpened()) {//kiểm tra xem ca này ở trạng thái chưa mở hay không, tránh 2 lễ tân cùng bấm mở ca dẫn đến lỗi chồng chéo
                return ServiceResult.fail(Messages.MSG255_SHIFT_ALREADY_OPENED_OR_CLOSED);
            }

            LocalDate today = DateTimeUtil.today();
            if (!today.equals(shift.getWorkDate())) {//chặn mở ca của ngày khác hôm nay (quá khứ hoặc tương lai)
                return ServiceResult.fail(shift.getWorkDate().isBefore(today)
                        ? Messages.MSG256_SHIFT_PAST_DAY
                        : Messages.MSG257_SHIFT_FUTURE_DAY);
            }
            if (shiftDAO.isGymClosedOnDate(today)) {//chặn mở ca vào ngày nghỉ của phòng tập
                return ServiceResult.fail(Messages.MSG258_SHIFT_GYM_CLOSED);
            }
            //Trong trường hợp lễ tân treo màn hình lúc đầu nút mở ca vẫn hiện nhưng treo quá giờ 13:00 thì bấm mở ca sẽ bị chặn
            //Không bao h tin tưởng dữ liệu từ fe, tránh dùng f12 hoặc postman chỉnh request

            // B2: Kiểm tra xung đột ca đang mở và thứ tự ca Sáng -> Chiều (CBR-104)
            List<Shift> todayShifts = shiftDAO.findTodayShifts(today);
            Shift otherOpen = findAnyOtherOpenShift(shiftId);//tìm xem có ca nào khác đang mở hay không (trừ ca hiện tại)
            if (otherOpen != null) {
                if (otherOpen.getWorkDate().isBefore(today)) {//nếu ca đang mở là ca của quá khứ
                    return ServiceResult.fail(String.format(Messages.MSG259_SHIFT_OLD_NOT_CLOSED,
                            otherOpen.getShiftType().getDisplayName(), DateTimeUtil.formatDate(otherOpen.getWorkDate())));
                }
                return ServiceResult.fail(String.format(Messages.MSG260_SHIFT_OTHER_OPEN,
                        otherOpen.getShiftType().getDisplayName()));//nếu ca đang mở là ca của hôm nay thì chặn mở ca khác
            }

            LocalDateTime now = LocalDateTime.now(DateTimeUtil.GYM_ZONE);
            if (shift.getShiftType() == Shift.ShiftType.MORNING) {
                Shift afternoonShift = findShiftByType(todayShifts, Shift.ShiftType.AFTERNOON);//lấy ca chiều
                if (afternoonShift != null && afternoonShift.isClosed()) {
                    return ServiceResult.fail(Messages.MSG262_SHIFT_MORNING_AFTER_AFTERNOON_CLOSED);
                    //Không thể mở ca sáng hôm nay nếu ca chiều hôm nay đã chốt
                }
                if (!now.toLocalTime().isBefore(AppConstants.AFTERNOON_SHIFT_START)) {
                    return ServiceResult.fail(Messages.MSG264_SHIFT_MORNING_OVER);
                    //Không thể mở ca sáng hôm nay nếu đã quá 13:00 (quá giờ quy định)
                }
            } else {
                LocalTime afternoonEnd = afternoonShiftEnd();//lấy giờ đóng cửa của ca chiều (22:00)
                if (afternoonEnd != null && !now.toLocalTime().isBefore(afternoonEnd)) {
                    return ServiceResult.fail(afternoonShiftOverMessage(afternoonEnd));
                    //Không thể mở ca chiều hôm nay nếu đã quá giờ đóng cửa
                }
                Shift morningShift = findShiftByType(todayShifts, Shift.ShiftType.MORNING);//lấy ca sáng
                if (morningShift != null && morningShift.isNotOpened()
                        && now.toLocalTime().isBefore(AppConstants.AFTERNOON_SHIFT_START)) {
                    return ServiceResult.fail(Messages.MSG266_SHIFT_AFTERNOON_NOT_YET);
                    //nếu bây giờ là trước 13:00 mà ca sáng hôm nay chưa mở thì không được nhảy cóc mở ca chiều
                }
            }

            // B3: Cập nhật trạng thái ca sang Open và ghi AuditLog trong cùng transaction
            //Dùng chung 1 connection để update ca và ghi log trong cùng transaction, nếu 1 trong 2 lỗi thì rollback đảm bảo tính toàn vẹn dữ liệu
            try (Connection conn = getConnection()) {
                beginTx(conn);//tắt auto-commit(tự động lưu vào db mà lưu vào bộ nhớ tạm) để bắt đầu transaction
                try {
                    int updated = shiftDAO.openShift(conn, shiftId, employee.getEmployeeId(), now);
                    if (updated == 0) {
                        rollbackTx(conn);//bỏ tất cả thay đổi trong transaction này như chưa có gì xảy ra
                        return ServiceResult.fail(Messages.MSG22_CONCURRENT_EDIT);
                    }
                    auditLogService.logInTransaction(conn, accountId, Role.RECEPTIONIST,
                            "Mở ca làm việc", "Shifts", shiftId, "Status",
                            "NotOpened", "Open", "Mở " + shift.getShiftType().getDisplayName());
                    commitTx(conn);//thực hiện lưu tất cả thay đổi trong transaction này vào db, nếu lỗi thì rollback
                    return ServiceResult.ok(null);
                } catch (SQLException ex) {
                    rollbackTx(conn);
                    throw ex;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    // ===== 3. Nghiệp vụ Đối soát & Chốt ca làm việc =====

    public ServiceResult<Void> closeShift(int accountId, int shiftId, long countedCash, String varianceReason) {
        Employee employee = getActiveReceptionist(accountId);
        if (employee == null) {
            return ServiceResult.fail(Messages.MSG07_NO_PERMISSION);
        }
        if (countedCash < 0) {
            return ServiceResult.fail(Messages.MSG17_POSITIVE_NUMBER);
        }

        try {
            // B1: Kiểm tra ca phải đang ở trạng thái Open và không còn đơn Pending (CBR-78)
            Shift shift = shiftDAO.findById(shiftId);
            if (shift == null) {
                return ServiceResult.fail(String.format(Messages.MSG08_NOT_FOUND, "ca làm việc"));
            }
            if (!shift.isOpen()) {//Chỉ cho chốt ca trạng thái open
                return ServiceResult.fail(Messages.MSG267_SHIFT_CLOSE_ONLY_OPEN);
            }

            List<ShiftTransactionItem> pendingOrders = shiftDAO.findPendingOrdersInShift(shiftId);
            //Kiểm tra xem ca này còn đơn Pending hay không, nếu còn thì chặn chốt ca và hiển thị cảnh báo
            if (!pendingOrders.isEmpty()) {
                return ServiceResult.fail(String.format(Messages.MSG87_CLOSE_SHIFT_PENDING_ORDERS, pendingOrders.size()));
            }

            // B2: Tính chênh lệch tiền mặt so với sổ sách (DBR-55, BR-46)
            long expectedCash = shiftDAO.calculateShiftMetrics(shiftId).getCashTotal();
            long discrepancy = countedCash - expectedCash;
            String trimmedReason = varianceReason != null ? varianceReason.trim() : null;//cắt chuỗi

            if (discrepancy != 0) {
                String reasonError = validateReasonLength(trimmedReason, "Số tiền đếm được bị chênh lệch so với sổ sách. ");
                //Kiểm tra độ dài lý do chênh lệch tiền mặt (GBR-09: 5–500 ký tự)
                if (reasonError != null) {
                    return ServiceResult.fail(reasonError);
                }
            }

            // B3: Chốt ca và ghi AuditLog trong cùng transaction

            try (Connection conn = getConnection()) {
                beginTx(conn);
                try {
                    LocalDateTime now = LocalDateTime.now(DateTimeUtil.GYM_ZONE);
                    int updated = shiftDAO.closeShift(conn, shiftId, employee.getEmployeeId(), now,
                            expectedCash, countedCash, discrepancy != 0 ? trimmedReason : null);
                    if (updated == 0) {
                        rollbackTx(conn);
                        return ServiceResult.fail(Messages.MSG22_CONCURRENT_EDIT);
                    }

                    String auditNote = discrepancy == 0
                            ? "Tiền mặt khớp sổ (" + expectedCash + "đ)"
                            : "Lệch " + (discrepancy > 0 ? "thừa " : "thiếu ") + Math.abs(discrepancy) + "đ: " + trimmedReason;
                    auditLogService.logInTransaction(conn, accountId, Role.RECEPTIONIST,
                            "Chốt ca làm việc", "Shifts", shiftId, "Status",
                            "Open", "Closed", auditNote);

                    commitTx(conn);
                    return ServiceResult.ok(null);
                } catch (SQLException ex) {
                    rollbackTx(conn);
                    throw ex;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    // ===== 4. Nghiệp vụ Hủy đơn hàng tại quầy =====

    public ServiceResult<Void> cancelOrder(int accountId, int orderId, String reason) {
        Employee employee = getActiveReceptionist(accountId);
        if (employee == null) {
            return ServiceResult.fail(Messages.MSG07_NO_PERMISSION);
        }

        // B1: Kiểm tra độ dài lý do hủy (GBR-09: 5–500 ký tự) và quyền tự hủy (CBR-115)
        String trimmedReason = reason != null ? reason.trim() : null;
        String reasonError = validateReasonLength(trimmedReason, "");
        if (reasonError != null) {
            return ServiceResult.fail(reasonError);
        }
        if (isOrderOfStaff(accountId, orderId)) {
            return ServiceResult.fail(Messages.MSG76_STAFF_OWN_CUSTOMER_PROFILE);
        }

        // B2: Đơn hàng chỉ được hủy trong ca mở đã tạo ra nó (CBR-11)
        Shift shift = getShiftOfOrder(orderId);
        if (shift == null || !shift.isOpen()) {
            return ServiceResult.fail(Messages.MSG97_CANCEL_ORDER_OUTSIDE_SHIFT);
        }

        // B3: Hủy đơn và ghi AuditLog trong cùng transaction
        try (Connection conn = getConnection()) {
            beginTx(conn);
            try {
                LocalDateTime now = LocalDateTime.now(DateTimeUtil.GYM_ZONE);
                int cancelled = shiftDAO.cancelCounterOrder(conn, orderId, trimmedReason, now, accountId);
                if (cancelled == 0) {
                    rollbackTx(conn);
                    return ServiceResult.fail(Messages.MSG270_ORDER_NOT_CANCELLABLE);
                }

                auditLogService.logInTransaction(conn, accountId, Role.RECEPTIONIST,
                        "Hủy đơn hàng tại quầy", "Orders", orderId, "Status",
                        "Paid/Pending", "Cancelled", trimmedReason);

                commitTx(conn);
                return ServiceResult.ok(null);
            } catch (SQLException ex) {
                rollbackTx(conn);
                throw ex;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    // ===== Hàm hỗ trợ nội bộ =====

    private void beginTx(Connection conn) throws SQLException {
        if (conn != null) {
            conn.setAutoCommit(false);
        }
    }

    private void commitTx(Connection conn) throws SQLException {
        if (conn != null) {
            conn.commit();
        }
    }

    private void rollbackTx(Connection conn) throws SQLException {
        if (conn != null) {
            conn.rollback();
        }
    }

    private String validateReasonLength(String trimmedReason, String shortMsgPrefix) {
        if (ValidationUtil.isNullOrEmpty(trimmedReason) || trimmedReason.length() < AppConstants.REASON_MIN_LENGTH) {
            return shortMsgPrefix + String.format(Messages.MSG19_TOO_SHORT, AppConstants.REASON_MIN_LENGTH);
        }
        if (trimmedReason.length() > AppConstants.REASON_MAX_LENGTH) {
            return String.format(Messages.MSG10_MAX_LENGTH, AppConstants.REASON_MAX_LENGTH);
        }
        return null;
    }

    /**
     * Giờ kết thúc ca chiều = giờ đóng cửa phòng tập (P-17, dạng HH:mm-HH:mm).
     * Trả về null nếu không đọc được cấu hình, khi đó không chặn theo giờ đóng cửa.
     */
    protected LocalTime afternoonShiftEnd() {
        try {
            return PtScheduleService.parseOpeningHours(
                    new SystemConfigService().getString(SystemConfigService.OPENING_HOURS))[1];//trả về 22:00
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static String afternoonShiftOverMessage(LocalTime afternoonEnd) {
        return "Đã quá thời gian ca chiều (kết thúc lúc " + afternoonEnd
                + "). Ca chiều hôm nay đã kết thúc và không thể mở lại.";
    }

    private Shift findAnyOtherOpenShift(int currentShiftId) {
        try {
            return shiftDAO.findOtherOpenShift(currentShiftId);
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    private Shift findShiftByType(List<Shift> shifts, Shift.ShiftType type) {
        for (Shift s : shifts) {
            if (s.getShiftType() == type) {
                return s;
            }
        }
        return null;
    }

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

    protected boolean isOrderOfStaff(int staffAccountId, int orderId) {
        try {
            Integer customerId = shiftDAO.findOrderHolderCustomerId(orderId);
            return customerId != null && selfServiceGuard.isOwnCustomerProfile(staffAccountId, customerId);
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    protected Shift getShiftOfOrder(int orderId) {
        try {
            return shiftDAO.findShiftOfCounterOrder(orderId);
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }
}
