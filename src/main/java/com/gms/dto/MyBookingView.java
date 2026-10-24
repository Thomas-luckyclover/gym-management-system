package com.gms.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Dữ liệu màn "Gói của tôi" (/customer/bookings).
 *   Cột trái : activePackages, endedPackages, singleSessionCount
 *   Cột phải : gói đang chọn (selectedPackage, hoặc buổi lẻ khi singleSelected)
 *              → CTA đúng lúc + buổi tới + lịch cố định + dải chấm các buổi + cần để ý
 */
public class MyBookingView {

    // ---------------- Cột trái ----------------
    private final List<MyPtPackageView> activePackages = new ArrayList<>();   // Đang tập
    private final List<MyPtPackageView> endedPackages = new ArrayList<>();    // Đã kết thúc
    private int singleSessionCount;                                           // số buổi lẻ

    // ---------------- Cột phải ----------------
    private MyPtPackageView selectedPackage;     // gói đang chọn (null nếu đang xem buổi lẻ / chưa có gì)
    private boolean singleSelected;              // true = đang xem nhóm "Các buổi lẻ"
    private List<MyPtSessionRow> sessions = new ArrayList<>();   // bảng buổi, theo thứ tự #1 → #N
    private Integer focusSessionId;              // "Buổi tới": buổi Scheduled đầu tiên chưa kết thúc (null nếu không có)
    private MyPtSessionRow focusSession;         // chính buổi đó → thẻ "Buổi tới" ở đầu cột phải
    private long daysUntilFocus;                 // còn mấy ngày tới buổi đó (0 = hôm nay, 1 = ngày mai)

    // 5 ô đếm (Tổng = Đã check-in + Vắng + Đã hủy + Còn lại + Đang cập nhật)
    private int totalCount;
    private int checkedInCount;
    private int missedCount;
    private int cancelledCount;
    private int remainingCount;                  // Hôm nay + Sắp tới + Cần chú ý
    private int pendingCount;                    // Đang cập nhật (đã kết thúc, chờ chốt) — JSP chỉ hiện khi > 0

    // ---------------- Lịch cố định (chỉ gói PT) ----------------
    private List<WeekdaySlot> weekTemplate = new ArrayList<>();      // 5 ô T2 → T6
    private String templateSummary;              // "T2 · T3 · T6 lúc 18:00 · T4 lúc 07:00"
    private int templateCount;                   // số buổi rơi đúng lịch cố định
    private List<MyPtSessionRow> offPattern = new ArrayList<>();     // "Buổi khác giờ" (kèm moveNote)

    // ---------------- Cần để ý ----------------
    private List<MyPtSessionRow> watchList = new ArrayList<>();      // "Cần để ý": buổi SẮP TỚI có cảnh báo (còn kịp xử lý)
    private List<SessionGroup> watchGroups = new ArrayList<>();      // Gom buổi cần chú ý theo cùng lý do.

    // ---------------- CTA: tối đa 1, Service chọn theo độ ưu tiên ----------------
    private String ctaKind;                      // buyMembership | renewAtCounter | rejoin | ptPackage | null
    private long daysLeft;                       // số ngày tới hết hạn gói (renewAtCounter)
    private int recentSingleCount;               // số buổi lẻ trong 30 ngày (ptPackage)
    private String openingHours;                 // "05:30 – 22:00" (P-19)
    private String hotline;                      // "1900 6868"
    private String hotlineTel;                   // "19006868" → href tel:
    private int todayWeekday;                    // thứ của hôm nay (1 = T2 … 7 = CN) → ô "hôm nay" trong lịch cố định

    /** Chưa có gói PT / buổi lẻ nào? (JSP: ${booking.noData}; không đặt tên isEmpty vì "empty" là từ khóa EL) */
    public boolean isNoData() {
        return activePackages.isEmpty() && endedPackages.isEmpty() && singleSessionCount == 0;
    }

    public List<MyPtPackageView> getActivePackages() { return activePackages; }
    public List<MyPtPackageView> getEndedPackages() { return endedPackages; }

    public int getSingleSessionCount() { return singleSessionCount; }
    public void setSingleSessionCount(int singleSessionCount) { this.singleSessionCount = singleSessionCount; }

    public MyPtPackageView getSelectedPackage() { return selectedPackage; }
    public void setSelectedPackage(MyPtPackageView selectedPackage) { this.selectedPackage = selectedPackage; }

    public boolean isSingleSelected() { return singleSelected; }
    public void setSingleSelected(boolean singleSelected) { this.singleSelected = singleSelected; }

    public List<MyPtSessionRow> getSessions() { return sessions; }
    public void setSessions(List<MyPtSessionRow> sessions) { this.sessions = sessions; }

    public Integer getFocusSessionId() { return focusSessionId; }
    public void setFocusSessionId(Integer focusSessionId) { this.focusSessionId = focusSessionId; }

    public MyPtSessionRow getFocusSession() { return focusSession; }
    public void setFocusSession(MyPtSessionRow focusSession) { this.focusSession = focusSession; }

    public long getDaysUntilFocus() { return daysUntilFocus; }
    public void setDaysUntilFocus(long daysUntilFocus) { this.daysUntilFocus = daysUntilFocus; }


    public int getTotalCount() { return totalCount; }
    public void setTotalCount(int totalCount) { this.totalCount = totalCount; }

    public int getCheckedInCount() { return checkedInCount; }
    public void setCheckedInCount(int checkedInCount) { this.checkedInCount = checkedInCount; }

    public int getMissedCount() { return missedCount; }
    public void setMissedCount(int missedCount) { this.missedCount = missedCount; }

    public int getCancelledCount() { return cancelledCount; }
    public void setCancelledCount(int cancelledCount) { this.cancelledCount = cancelledCount; }

    public int getRemainingCount() { return remainingCount; }
    public void setRemainingCount(int remainingCount) { this.remainingCount = remainingCount; }

    public int getPendingCount() { return pendingCount; }

    public List<WeekdaySlot> getWeekTemplate() { return weekTemplate; }
    public void setWeekTemplate(List<WeekdaySlot> weekTemplate) { this.weekTemplate = weekTemplate; }

    public String getTemplateSummary() { return templateSummary; }
    public void setTemplateSummary(String templateSummary) { this.templateSummary = templateSummary; }

    public int getTemplateCount() { return templateCount; }
    public void setTemplateCount(int templateCount) { this.templateCount = templateCount; }

    public List<MyPtSessionRow> getOffPattern() { return offPattern; }
    public void setOffPattern(List<MyPtSessionRow> offPattern) { this.offPattern = offPattern; }



    public List<MyPtSessionRow> getWatchList() { return watchList; }
    public void setWatchList(List<MyPtSessionRow> watchList) { this.watchList = watchList; }

    public List<SessionGroup> getWatchGroups() { return watchGroups; }
    public void setWatchGroups(List<SessionGroup> watchGroups) { this.watchGroups = watchGroups; }

    public String getCtaKind() { return ctaKind; }
    public void setCtaKind(String ctaKind) { this.ctaKind = ctaKind; }

    public long getDaysLeft() { return daysLeft; }
    public void setDaysLeft(long daysLeft) { this.daysLeft = daysLeft; }

    public int getRecentSingleCount() { return recentSingleCount; }
    public void setRecentSingleCount(int recentSingleCount) { this.recentSingleCount = recentSingleCount; }

    public String getOpeningHours() { return openingHours; }
    public void setOpeningHours(String openingHours) { this.openingHours = openingHours; }

    public String getHotline() { return hotline; }
    public void setHotline(String hotline) { this.hotline = hotline; }

    public int getTodayWeekday() { return todayWeekday; }
    public void setTodayWeekday(int todayWeekday) { this.todayWeekday = todayWeekday; }

    public String getHotlineTel() { return hotlineTel; }
    public void setHotlineTel(String hotlineTel) { this.hotlineTel = hotlineTel; }
    public void setPendingCount(int pendingCount) { this.pendingCount = pendingCount; }
}
