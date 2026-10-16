package com.gms.dto;

import java.time.LocalDate;

/**
 * BỘ LỌC ĐÃ KIỂM TRA HỢP LỆ của màn "Lịch tập của tôi".
 * Chỉ Service tạo (hàm parseFilter). Field nào null = không lọc theo điều kiện đó.
 *
 *   Ô trên form        | Tham số URL     | Field
 *   -------------------|-----------------|------------------
 *   Gói PT             | subscriptionId  | subscriptionId
 *   Loại buổi          | type            | type
 *   Buổi trong ngày    | timeOfDay       | timeOfDay
 *   Từ ngày / Đến ngày | dateFrom/dateTo | dateFrom/dateTo
 *   Tab trạng thái     | historyStatus   | historyStatus  (chỉ khối Lịch sử)
 */
public class MyPtSessionFilter {

    private Integer subscriptionId;   // mã gói
    private String type;              // PTSingle | PTCouple | SingleSession
    private String timeOfDay;         // morning | afternoon | evening
    private LocalDate dateFrom;       // từ ngày (gồm cả ngày này)
    private LocalDate dateTo;         // đến ngày (gồm cả ngày này)
    private String historyStatus;     // pending | checkedIn | missed | cancelled

    /** Bộ lọc rỗng = không lọc gì (dùng khi URL trống hoặc khi khách nhập sai). */
    public static MyPtSessionFilter none() {
        return new MyPtSessionFilter();
    }

    /** Có đang lọc khối Sắp tới không? (JSP dùng để chọn câu "Không có buổi phù hợp bộ lọc") */
    public boolean isCommonFilterActive() {
        return subscriptionId != null || type != null || timeOfDay != null
                || dateFrom != null || dateTo != null;
    }

    /** Có đang lọc khối Lịch sử không? (bộ lọc chung HOẶC tab trạng thái) */
    public boolean isHistoryFilterActive() {
        return isCommonFilterActive() || historyStatus != null;
    }

    // ===== getter / setter =====
    public Integer getSubscriptionId() { return subscriptionId; }
    public void setSubscriptionId(Integer subscriptionId) { this.subscriptionId = subscriptionId; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getTimeOfDay() { return timeOfDay; }
    public void setTimeOfDay(String timeOfDay) { this.timeOfDay = timeOfDay; }
    public LocalDate getDateFrom() { return dateFrom; }
    public void setDateFrom(LocalDate dateFrom) { this.dateFrom = dateFrom; }
    public LocalDate getDateTo() { return dateTo; }
    public void setDateTo(LocalDate dateTo) { this.dateTo = dateTo; }
    public String getHistoryStatus() { return historyStatus; }
    public void setHistoryStatus(String historyStatus) { this.historyStatus = historyStatus; }
}
