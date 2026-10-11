package com.gms.dto;

import java.math.BigDecimal;

/** Điểm đánh giá của PT trong tháng hiện tại trên dashboard. */
public class PtRatingView {

    // PtDashboardDAO: COUNT(DISTINCT Feedbacks.CustomerId) của PT trong RatingMonth.
    // JSP chỉ in số khách khi đã đủ ngưỡng P-22.
    private final int ratingCustomerCount;

    // PtDashboardDAO: SUM(Feedbacks.Rating) của cùng các phiếu; không in riêng lên màn hình.
    private final long ratingTotal;

    // PtDashboardService: ratingTotal / ratingCustomerCount, giữ tối đa hai số thập phân.
    // JSP in dạng "4,25/5" khi đủ ngưỡng P-22; null nếu chưa đủ khách.
    private BigDecimal averageRating;

    // PtDashboardService: so ratingCustomerCount với SystemConfigs.P-22.
    private boolean ratingVisible;

    // PtDashboardService: lời nhắc ngắn theo điểm, hoặc thông báo chưa có/chưa đủ đánh giá.
    private String message;

    /** DAO tạo DTO từ số khách và tổng sao; không đưa tên hoặc nội dung feedback vào đây. */
    public PtRatingView(int ratingCustomerCount, long ratingTotal) {
        this.ratingCustomerCount = ratingCustomerCount;
        this.ratingTotal = ratingTotal;
    }

    public int getRatingCustomerCount() {
        return ratingCustomerCount;
    }

    public BigDecimal getAverageRating() {
        return averageRating;
    }

    public long getRatingTotal() {
        return ratingTotal;
    }

    /** Service gọi sau khi đọc ngưỡng P-22 và chọn câu hiển thị. */
    public void setDisplay(boolean ratingVisible, BigDecimal averageRating, String message) {
        this.ratingVisible = ratingVisible;
        this.averageRating = averageRating;
        this.message = message;
    }

    public boolean isRatingVisible() {
        return ratingVisible;
    }

    public String getMessage() {
        return message;
    }
}
