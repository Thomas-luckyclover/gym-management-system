package com.gms.dto;

/**
 * DTO chứa 4 chỉ số KPI tổng hợp của ca làm việc (Màn #39 - UC-25).
 *
 * <p>Nơi tạo: {@link com.gms.dao.ShiftDAO#calculateShiftMetrics}.</p>
 * <p>Nơi dùng: Render 4 thẻ KPI đầu trang tại {@code receptionist/shift.jsp}.</p>
 */
public class ShiftMetrics {

    // DAO: Tổng tiền các giao dịch tiền mặt thành công (Cash & Success) của các đơn chưa hủy (DBR-55).
    // JSP hiển thị ở thẻ KPI Tiền mặt (dạng tiền tệ: gms:formatMoney).
    private final long cashTotal;

    // DAO: Số lượng hóa đơn tiền mặt hợp lệ.
    // JSP hiển thị số lượng hóa đơn dưới thẻ Tiền mặt.
    private final int cashCount;

    // DAO: Tổng tiền các giao dịch chuyển khoản thành công (Transfer & Success) của các đơn chưa hủy.
    // JSP hiển thị ở thẻ KPI Chuyển khoản (dạng tiền tệ: gms:formatMoney).
    private final long bankTotal;

    // DAO: Số lượng hóa đơn chuyển khoản hợp lệ.
    // JSP hiển thị số lượng hóa đơn dưới thẻ Chuyển khoản.
    private final int bankCount;

    // DAO: Tổng doanh thu hợp lệ = cashTotal + bankTotal.
    // JSP hiển thị ở thẻ KPI Tổng thu (dạng tiền tệ: gms:formatMoney).
    private final long totalRevenue;

    // DAO: Tổng số lượng hóa đơn hợp lệ = cashCount + bankCount.
    // JSP hiển thị số hóa đơn hợp lệ dưới thẻ Tổng thu.
    private final int totalValidCount;

    // DAO: Số lượng đơn hàng tại quầy trong ca đang ở trạng thái Pending.
    // JSP hiển thị số lượng đơn chờ xử lý; nếu > 0 sẽ cảnh báo chặn chốt ca (CBR-78).
    private final int pendingCount;

    public ShiftMetrics(long cashTotal, int cashCount, long bankTotal, int bankCount,
                        long totalRevenue, int totalValidCount, int pendingCount) {
        this.cashTotal = cashTotal;
        this.cashCount = cashCount;
        this.bankTotal = bankTotal;
        this.bankCount = bankCount;
        this.totalRevenue = totalRevenue;
        this.totalValidCount = totalValidCount;
        this.pendingCount = pendingCount;
    }

    public long getCashTotal() {
        return cashTotal;
    }

    public int getCashCount() {
        return cashCount;
    }

    public long getBankTotal() {
        return bankTotal;
    }

    public int getBankCount() {
        return bankCount;
    }

    public long getTotalRevenue() {
        return totalRevenue;
    }

    public int getTotalValidCount() {
        return totalValidCount;
    }

    public int getPendingCount() {
        return pendingCount;
    }

    /**
     * Kiểm tra ca làm việc có đơn hàng chờ thanh toán hay không (CBR-78).
     *
     * @return true nếu còn ít nhất 1 đơn Pending, ngược lại false
     */
    public boolean hasPendingOrders() {
        return pendingCount > 0;
    }

    /**
     * Kiểm tra ca làm việc đã phát sinh giao dịch nào chưa (bao gồm cả hợp lệ và chờ xử lý).
     *
     * @return true nếu có giao dịch, ngược lại false
     */
    public boolean hasTransactions() {
        return totalValidCount > 0 || pendingCount > 0;
    }

    /**
     * Tỷ lệ phần trăm doanh thu tiền mặt trên tổng thu (0.0 đến 100.0).
     *
     * @return tỷ lệ phần trăm tiền mặt
     */
    public double getCashPercentage() {
        return totalRevenue > 0 ? ((double) cashTotal / totalRevenue) * 100.0 : 0.0;
    }

    /**
     * Tỷ lệ phần trăm doanh thu chuyển khoản trên tổng thu (0.0 đến 100.0).
     *
     * @return tỷ lệ phần trăm chuyển khoản
     */
    public double getBankPercentage() {
        return totalRevenue > 0 ? ((double) bankTotal / totalRevenue) * 100.0 : 0.0;
    }
}
