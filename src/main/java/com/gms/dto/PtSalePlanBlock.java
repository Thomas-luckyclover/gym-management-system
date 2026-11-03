package com.gms.dto;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * KHỐI "LỊCH TẬP" (bước 2) — và lưới "N buổi tập · Đang giữ" khi mở đơn đang giữ.
 * Service điền: PtSaleService.newView (giờ t1..t5) → loadPlan (xem: planJson · giữ lịch: days) / loadHeld (days).
 * JSP đọc: _pt-sale-plan.jspf (planJson → pt-sale-plan.js vẽ), _pt-sale-held.jspf (days), pt-sales.jsp (ô "Buổi").
 */
public class PtSalePlanBlock {

    // ===== LỊCH ĐANG CHỌN =====

    // ← ?t1..t5 (giờ lịch mẫu Thứ 2..Thứ 6, null = Nghỉ) → loadPlan tính lịch; timeOf / getWeekTimeTexts đọc
    private LocalTime[] weekTimes = new LocalTime[5];


    // ===== XEM LỊCH (trước khi giữ) =====

    // ← loadPlan → buildPlan → JSON (PtSalePlan) → <script id="sale-plan-data">, pt-sale-plan.js vẽ lịch mẫu + từng tuần
    private String planJson;

    // ← loadPlan / checkRequested: không tính được lịch / buổi gửi lên sai → dòng xám thay lịch, chặn Giữ lịch
    private String planError;

    // ← unpublishedWeekdays: thứ PT chưa mở khung nào trong 14 ngày đầu → hộp nhắc vàng "PT chưa công bố khung rảnh Thứ 3, Thứ 5"
    private List<Integer> unpublishedWeekdays = new ArrayList<>();


    // ===== CÁC BUỔI (khi Giữ lịch / khi mở đơn đang giữ) =====

    // ← loadPlan(lock) → checkRequested · hoặc loadHeld (khung đang giữ) → lưới ô ngày "Đang giữ"
    private List<PtSaleDay> days = new ArrayList<>();

    // ← số buổi xếp được → "N buổi tập", hold() dùng để biết có buổi để giữ không
    private int sessionCount;

    // ← số ngày không có buổi → "N ngày không có buổi (lý do bên dưới)"
    private int skipCount;

    // ← giá ÷ số buổi → (không in thẳng) giá trị 1 buổi
    private long sessionValue;


    /** Giờ lịch mẫu của 1 thứ (1 = Thứ 2) dạng "HH:mm"; "" = Nghỉ. */
    public String timeOf(int weekday) {
        LocalTime time = weekTimes[weekday - 1];
        return time == null ? "" : time.toString();
    }

    /** 5 giờ lịch mẫu dạng chữ (Thứ 2..Thứ 6). */
    public List<String> getWeekTimeTexts() {
        List<String> texts = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            texts.add(timeOf(i));
        }
        return texts;
    }

    /** Số dòng ghi chú dưới lưới đang giữ = ngày không có buổi + ngày có cảnh báo vàng. */
    public int getNoteCount() {
        int count = 0;
        for (PtSaleDay day : days) {
            if (!day.isSession() || day.getWarning() != null) {
                count++;
            }
        }
        return count;
    }

    /** "Thứ 3, Thứ 5" — chèn vào hộp nhắc PT chưa công bố. */
    public String getUnpublishedText() {
        StringBuilder text = new StringBuilder();
        for (int weekday : unpublishedWeekdays) {
            text.append(text.length() == 0 ? "" : ", ").append("Thứ ").append(weekday + 1);
        }
        return text.toString();
    }


    public LocalTime[] getWeekTimes() { return weekTimes; }
    public void setWeekTimes(LocalTime[] weekTimes) { this.weekTimes = weekTimes; }

    public String getPlanJson() { return planJson; }
    public void setPlanJson(String planJson) { this.planJson = planJson; }

    public String getPlanError() { return planError; }
    public void setPlanError(String planError) { this.planError = planError; }

    public List<Integer> getUnpublishedWeekdays() { return unpublishedWeekdays; }
    public void setUnpublishedWeekdays(List<Integer> unpublishedWeekdays) { this.unpublishedWeekdays = unpublishedWeekdays; }

    public List<PtSaleDay> getDays() { return days; }
    public void setDays(List<PtSaleDay> days) { this.days = days; }

    public int getSessionCount() { return sessionCount; }
    public void setSessionCount(int sessionCount) { this.sessionCount = sessionCount; }

    public int getSkipCount() { return skipCount; }
    public void setSkipCount(int skipCount) { this.skipCount = skipCount; }

    public long getSessionValue() { return sessionValue; }
    public void setSessionValue(long sessionValue) { this.sessionValue = sessionValue; }
}
