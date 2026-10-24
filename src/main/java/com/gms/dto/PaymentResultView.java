package com.gms.dto;

import com.gms.entity.Order;

/**
 * Service tạo từ trạng thái database.
 * JSP dùng để hiển thị kết quả, không suy ra Paid từ URL VNPay.
 */
public class PaymentResultView {

    // OrderDAO đọc snapshot; JSP hiện mã đơn, tên gói và số tiền.
    private final Order order;

    // Service chọn nội dung theo trạng thái thực tế trong DB.
    private final String headline;
    private final String message;

    // Service tính để JSP chọn trạng thái và nút thao tác.
    private final boolean success;
    private final boolean waiting;
    private final boolean retryable;

    public PaymentResultView(
            Order order,
            String headline,
            String message,
            boolean success,
            boolean waiting,
            boolean retryable) {

        this.order = order;
        this.headline = headline;
        this.message = message;
        this.success = success;
        this.waiting = waiting;
        this.retryable = retryable;
    }

    public Order getOrder() {
        return order;
    }

    public String getHeadline() {
        return headline;
    }

    public String getMessage() {
        return message;
    }

    public boolean isSuccess() {
        return success;
    }

    public boolean isWaiting() {
        return waiting;
    }

    public boolean isRetryable() {
        return retryable;
    }
}