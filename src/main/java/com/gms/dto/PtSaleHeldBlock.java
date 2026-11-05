package com.gms.dto;

/**
 * KHỐI "ĐƠN ĐANG GIỮ" (trang ?order=<id>): mã đơn + đồng hồ "Giữ chỗ còn mm:ss" + form thu tiền / hủy giữ.
 * Service điền: PtSaleService.loadHeld (đọc PtSaleDAO.findHeldOrder). Chưa giữ đơn nào → mọi field null / 0.
 * JSP đọc: pt-sales.jsp (đồng hồ) và _pt-sale-actions.jspf (ô ẩn "order" của form Thu tiền / Hủy giữ chỗ).
 */
public class PtSaleHeldBlock {

    // ← Orders.OrderId của đơn Pending đang giữ → ô ẩn "order"; khác null = trang đang ở chế độ "đang giữ"
    private Integer orderId;

    // ← Orders.OrderCode "DH-2026-0181" → tiêu đề đơn, câu hỏi "Hủy giữ chỗ DH-…?"
    private String orderCode;

    // ← HoldExpiresAt (sớm nhất) − giờ server → data-countdown-seconds, common.js đếm ngược, về 0 thì tải lại trang
    private long holdSeconds;


    public Integer getOrderId() { return orderId; }
    public void setOrderId(Integer orderId) { this.orderId = orderId; }

    public String getOrderCode() { return orderCode; }
    public void setOrderCode(String orderCode) { this.orderCode = orderCode; }

    public long getHoldSeconds() { return holdSeconds; }
    public void setHoldSeconds(long holdSeconds) { this.holdSeconds = holdSeconds; }
}
