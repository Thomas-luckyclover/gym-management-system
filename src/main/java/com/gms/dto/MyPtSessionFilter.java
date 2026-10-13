package com.gms.dto;

import com.gms.entity.Package;
import java.time.LocalDate;

/**
 * Điều kiện lọc ĐÃ HỢP LỆ cho màn Lịch tập của tôi; chỉ Service tạo sau khi kiểm tra tham số URL.
 * Dữ liệu thô (chuỗi người dùng nhập) không nằm ở đây: JSP giữ lại bằng ${param.*},
 * lỗi từng ô trả qua ServiceResult.failFields. Mọi field null nghĩa là không lọc theo điều kiện đó.
 */
public class MyPtSessionFilter {

    /** Trạng thái riêng của khối Lịch sử; giá trị URL cố định, không phải mã Sessions.Status. */
    public enum HistoryStatus {
        // Scheduled đã qua giờ kết thúc, chờ JOB-04 chốt.
        PENDING_RESULT("pending"),
        CHECKED_IN("checkedIn"),
        MISSED("missed"),
        CANCELLED("cancelled");

        private final String urlValue;

        HistoryStatus(String urlValue) {
            this.urlValue = urlValue;
        }

        /** Giá trị trên URL và trong option của JSP. */
        public String getUrlValue() {
            return urlValue;
        }

        /** Đổi giá trị URL thành enum; trả null nếu không khớp để Service báo lỗi ô. */
        public static HistoryStatus fromUrlValue(String value) {
            for (HistoryStatus item : values()) {
                if (item.urlValue.equals(value)) {
                    return item;
                }
            }
            return null;
        }
    }

    // ===== Bộ lọc chung: áp dụng cho cả Sắp tới và Lịch sử =====
    // Subscriptions.SubscriptionType: PTSingle, PTCouple hoặc SingleSession; null = mọi loại.
    private final Package.Type subscriptionType;
    // TimeSlots.SlotDate từ ngày này (gồm cả ngày đó); null = không giới hạn đầu.
    private final LocalDate dateFrom;
    // TimeSlots.SlotDate đến ngày này (gồm cả ngày đó); null = không giới hạn cuối.
    private final LocalDate dateTo;

    // ===== Chỉ khối Lịch sử: thu hẹp kết quả lịch sử, không ảnh hưởng Sắp tới =====
    // null = Tất cả, gồm cả Scheduled đã kết thúc.
    private final HistoryStatus historyStatus;
    // Subscriptions.SubscriptionId: phân biệt chính xác từng lần mua; null = mọi gói.
    private final Integer subscriptionId;

    /** Service gọi sau khi đã kiểm tra hợp lệ; không tự validate trong constructor. */
    public MyPtSessionFilter(Package.Type subscriptionType, LocalDate dateFrom, LocalDate dateTo,
            HistoryStatus historyStatus) {
        this(subscriptionType, dateFrom, dateTo, historyStatus, null);
    }

    public MyPtSessionFilter(Package.Type subscriptionType, LocalDate dateFrom, LocalDate dateTo,
            HistoryStatus historyStatus, Integer subscriptionId) {
        this.subscriptionType = subscriptionType;
        this.dateFrom = dateFrom;
        this.dateTo = dateTo;
        this.historyStatus = historyStatus;
        this.subscriptionId = subscriptionId;
    }

    /** Không lọc gì: dùng khi URL trống hoặc khi bộ lọc người dùng nhập không hợp lệ. */
    public static MyPtSessionFilter none() {
        return new MyPtSessionFilter(null, null, null, null);
    }

    /** Loại buổi cần lọc, null nếu không lọc. */
    public Package.Type getSubscriptionType() {
        return subscriptionType;
    }

    /** Ngày bắt đầu khoảng lọc, null nếu không giới hạn. */
    public LocalDate getDateFrom() {
        return dateFrom;
    }

    /** Ngày kết thúc khoảng lọc, null nếu không giới hạn. */
    public LocalDate getDateTo() {
        return dateTo;
    }

    /** Trạng thái lịch sử cần lọc, null là Tất cả. */
    public HistoryStatus getHistoryStatus() {
        return historyStatus;
    }

    /** Có điều kiện bộ lọc chung: JSP dùng câu rỗng "Không có buổi phù hợp". */
    public boolean isCommonFilterActive() {
        return subscriptionType != null || dateFrom != null || dateTo != null || subscriptionId != null;
    }

    public Integer getSubscriptionId() {
        return subscriptionId;
    }

    /** Khối Lịch sử đang bị thu hẹp (bộ lọc chung hoặc trạng thái lịch sử). */
    public boolean isHistoryFilterActive() {
        return isCommonFilterActive() || historyStatus != null;
    }
}
