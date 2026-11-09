package com.gms.dto;

/**
 * 1 DÒNG trong khung "ĐƠN ĐANG GIỮ CHỖ" (đầu màn, khi chưa mở đơn nào).
 * Service tạo: PtSaleService.loadPendingHolds ← PtSaleDAO.findActiveHoldOrders.
 * JSP đọc: _pt-sale-holds.jspf (biến hold).
 */
public class PtSalePendingHold {

    // ← Orders.OrderId → link "Tiếp tục thu tiền" (?order=<id>)
    private final int orderId;

    // ← Orders.OrderCode → mã đơn đầu dòng
    private final String orderCode;

    // ← HoldExpiresAt − bây giờ → "còn mm:ss"; pt-sales.js gỡ dòng khi về 0
    private final long secondsLeft;


    public PtSalePendingHold(int orderId, String orderCode, long secondsLeft) {
        this.orderId = orderId;
        this.orderCode = orderCode;
        this.secondsLeft = secondsLeft;
    }

    public int getOrderId() { return orderId; }
    public String getOrderCode() { return orderCode; }
    public long getSecondsLeft() { return secondsLeft; }
}
