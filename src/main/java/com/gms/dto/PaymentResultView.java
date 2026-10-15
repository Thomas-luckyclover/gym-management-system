package com.gms.dto;

import com.gms.entity.Order;

/** CallbackService tạo từ trạng thái DB, không dùng ResponseCode trên return URL để báo Paid. */
public class PaymentResultView {
    private final Order order;
    private final String headline;
    private final String message;
    private final boolean success;
    private final boolean waiting;
    private final boolean retryable;

    public PaymentResultView(Order order, String headline, String message, boolean success,
            boolean waiting, boolean retryable) {
        this.order = order;
        this.headline = headline;
        this.message = message;
        this.success = success;
        this.waiting = waiting;
        this.retryable = retryable;
    }

    public Order getOrder() { return order; }
    public String getHeadline() { return headline; }
    public String getMessage() { return message; }
    public boolean isSuccess() { return success; }
    public boolean isWaiting() { return waiting; }
    public boolean isRetryable() { return retryable; }
}
