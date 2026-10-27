package com.gms.dto;

import com.gms.entity.Shift;

import java.util.Collections;
import java.util.List;

/**
 * View object tổng hợp toàn bộ dữ liệu cần thiết để hiển thị màn hình Ca làm việc (Màn #39 - UC-25).
 *
 * <p>Nơi tạo: {@link com.gms.service.ShiftService#getShiftDetail}.</p>
 * <p>Nơi dùng: Servlet gán vào request attribute {@code detail}, JSP truy xuất để render toàn trang.</p>
 */
public class ShiftDetailView {

    // Service: Đối tượng Shift đang được xem (chứa thông tin trạng thái, ngày, loại ca, thời điểm mở/chốt...).
    private final Shift shift;

    // DAO: Họ và tên nhân viên Lễ tân mở ca (Persons.FullName).
    private final String openedByEmployeeName;

    // DAO: Họ và tên nhân viên Lễ tân chốt ca (Persons.FullName).
    private final String closedByEmployeeName;

    // Service/DAO: Thống kê 4 thẻ KPI (Tiền mặt, Chuyển khoản, Tổng thu, Đơn chờ xử lý).
    private final ShiftMetrics metrics;

    // DAO: Danh sách giao dịch trong ca đã phân trang và áp bộ lọc.
    private final PageResult<ShiftTransactionItem> transactionsPage;

    // DAO: Danh sách các đơn đang ở trạng thái Pending trong ca để hiển thị ở Alert Banner.
    private final List<ShiftTransactionItem> pendingOrders;

    // DAO: Danh sách tất cả các ca trong ngày (Sáng và Chiều) để Lễ tân chuyển qua lại xem.
    private final List<Shift> todayShifts;

    // Request filter: Từ khóa tìm kiếm người dùng đã nhập.
    private final String searchKeyword;

    // Request filter: Phương thức thanh toán được chọn (All, Cash, Transfer).
    private final String methodFilter;

    // Request filter: Trạng thái đơn được chọn (All, Paid, Pending, Cancelled).
    private final String statusFilter;

    // Quy tắc nghiệp vụ: Ca có được phép mở tại thời điểm hiện tại hay không.
    private final boolean canOpen;

    // Lý do không cho phép mở ca (khi notOpened = true và canOpen = false).
    private final String shiftNotOpenedReason;

    // Đánh dấu ca thực sự đã qua trong quá khứ (không thể mở lại).
    private final boolean pastShift;

    public ShiftDetailView(Shift shift, String openedByEmployeeName, String closedByEmployeeName,
                           ShiftMetrics metrics, PageResult<ShiftTransactionItem> transactionsPage,
                           List<ShiftTransactionItem> pendingOrders, List<Shift> todayShifts,
                           String searchKeyword, String methodFilter, String statusFilter) {
        this(shift, openedByEmployeeName, closedByEmployeeName, metrics, transactionsPage,
                pendingOrders, todayShifts, searchKeyword, methodFilter, statusFilter, true, null, false);
    }

    public ShiftDetailView(Shift shift, String openedByEmployeeName, String closedByEmployeeName,
                           ShiftMetrics metrics, PageResult<ShiftTransactionItem> transactionsPage,
                           List<ShiftTransactionItem> pendingOrders, List<Shift> todayShifts,
                           String searchKeyword, String methodFilter, String statusFilter,
                           boolean canOpen, String shiftNotOpenedReason) {
        this(shift, openedByEmployeeName, closedByEmployeeName, metrics, transactionsPage,
                pendingOrders, todayShifts, searchKeyword, methodFilter, statusFilter, canOpen, shiftNotOpenedReason, false);
    }

    public ShiftDetailView(Shift shift, String openedByEmployeeName, String closedByEmployeeName,
                           ShiftMetrics metrics, PageResult<ShiftTransactionItem> transactionsPage,
                           List<ShiftTransactionItem> pendingOrders, List<Shift> todayShifts,
                           String searchKeyword, String methodFilter, String statusFilter,
                           boolean canOpen, String shiftNotOpenedReason, boolean pastShift) {
        this.shift = shift;
        this.openedByEmployeeName = openedByEmployeeName;
        this.closedByEmployeeName = closedByEmployeeName;
        this.metrics = metrics;
        this.transactionsPage = transactionsPage;
        this.pendingOrders = pendingOrders != null ? pendingOrders : Collections.emptyList();
        this.todayShifts = todayShifts != null ? todayShifts : Collections.emptyList();
        this.searchKeyword = searchKeyword;
        this.methodFilter = methodFilter;
        this.statusFilter = statusFilter;
        this.canOpen = canOpen;
        this.shiftNotOpenedReason = shiftNotOpenedReason;
        this.pastShift = pastShift;
    }

    public Shift getShift() {
        return shift;
    }

    public String getOpenedByEmployeeName() {
        return openedByEmployeeName;
    }

    public String getClosedByEmployeeName() {
        return closedByEmployeeName;
    }

    public ShiftMetrics getMetrics() {
        return metrics;
    }

    public PageResult<ShiftTransactionItem> getTransactionsPage() {
        return transactionsPage;
    }

    public List<ShiftTransactionItem> getPendingOrders() {
        return pendingOrders;
    }

    public List<Shift> getTodayShifts() {
        return todayShifts;
    }

    public String getSearchKeyword() {
        return searchKeyword;
    }

    public String getMethodFilter() {
        return methodFilter;
    }

    public String getStatusFilter() {
        return statusFilter;
    }

    public boolean hasPendingOrders() {
        return !pendingOrders.isEmpty();
    }

    public boolean isCanOpen() {
        return canOpen;
    }

    public String getShiftNotOpenedReason() {
        return shiftNotOpenedReason;
    }

    public boolean isPastShift() {
        return pastShift;
    }
}
