package com.gms.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO màn GÓI CỦA TÔI (/customer/bookings) — Service TẠO + ĐIỀN: MyBookingService.getMyBookings.
 * Servlet: MyBookingServlet.loadBookings → request.setAttribute("booking", view) → my-bookings.jsp đọc ${booking.xxx}.
 * Hàm tính sẵn: isNoData() = không có gói nào và không có buổi lẻ → JSP hiện "Bạn chưa có gói PT hoặc buổi lẻ".
 */
public class MyBookingView {
    // ===== IN RA MÀN — cột trái "Gói của bạn" =====
    // ← getMyBookings B2: MyPtSessionDAO.findMyPackages → gói isActive() → tab "Đang tập" (mỗi gói 1 thẻ _booking-package.jspf)
    private final List<MyPtPackageView> activePackages = new ArrayList<>();
    // ← getMyBookings B2: gói đã kết thúc → tab "Đã kết thúc"
    private final List<MyPtPackageView> endedPackages = new ArrayList<>();
    // ← getMyBookings B3: MyPtSessionDAO.findSingleSessions().size() → số trên tab "Buổi lẻ", "N buổi · mỗi buổi đặt riêng"
    private int singleSessionCount;
    // ← getMyBookings B5: đang xem buổi lẻ (?package=single) → JSP mở tab "Buổi lẻ", vẽ danh sách buổi lẻ thay vì lịch gói
    private boolean singleSelected;

    // ===== IN RA MÀN — đầu cột phải (gói đang xem + buổi tới) =====
    // ← getMyBookings B4: pickPackage(?package=) → JSP biến "sp": tên gói, PT, ngày, vòng "còn N buổi"
    private MyPtPackageView selectedPackage;
    // ← countSessions: buổi Scheduled sớm nhất chưa kết thúc → JSP biến "f": khung "Buổi tới" (ngày, giờ, cảnh báo)
    private MyPtSessionRow focusSession;
    // ← countSessions: Id của focusSession → chấm buổi đó trong dải "Các buổi của gói" có viền (is-next)
    private Integer focusSessionId;
    // ← countSessions: số ngày tới buổi tới → chữ "Hôm nay" (0) / "Ngày mai" (1)
    private long daysUntilFocus;

    // ===== IN RA MÀN — khung gợi ý mua / gia hạn (CTA) =====
    // ← chooseCta: "buyMembership" | "renewAtCounter" | "rejoin" | "ptPackage" | null → chọn câu + nút của khung CTA
    private String ctaKind;
    // ← countRecentSingles: số buổi lẻ trong 30 ngày → "Bạn đã đặt N buổi lẻ trong 30 ngày" (CTA ptPackage)
    private int recentSingleCount;
    // ← fillPackageDetail: số ngày tới ngày hết gói → chooseCta (≤ 14 ngày → gợi ý gia hạn)
    private long daysLeft;
    // ← fillCounterInfo: AppConstants.GYM_HOTLINE → chữ số điện thoại trên nút "Gọi lễ tân"
    private String hotline;
    // ← fillCounterInfo: số dạng tel: → href="tel:…" của nút "Gọi lễ tân"
    private String hotlineTel;
    // ← fillCounterInfo: SystemConfigs P-19 "06:00 – 22:00" → giờ mở cửa trong câu gia hạn tại quầy
    private String openingHours;

    // ===== IN RA MÀN — "Lịch cố định" (chỉ khi xem 1 gói) =====
    // ← applyWeekTemplate: 5 ô T2–T6 (WeekdaySlot) → mỗi ô: giờ cố định / "~ không cố định" / "— không có buổi"
    private List<WeekdaySlot> weekTemplate = new ArrayList<>();
    // ← summarize: "T2 · T4 lúc 18:00" → aria-label của dải 5 ô
    private String templateSummary;
    // ← applyWeekTemplate: số buổi đúng giờ cố định → "x/y buổi theo lịch này"
    private int templateCount;
    // ← getMyBookings B1: thứ hôm nay (1 = T2) → tô ô "hôm nay" trong dải 5 ô
    private int todayWeekday;
    // ← applyWeekTemplate: buổi KHÁC giờ cố định (+ moveNote) → danh sách "Buổi khác giờ"
    private List<MyPtSessionRow> offPattern = new ArrayList<>();

    // ===== IN RA MÀN — "Các buổi của gói" / danh sách buổi lẻ =====
    // ← getMyBookings B6: MyPtSessionDAO.findSessionsOfPackage (hoặc buổi lẻ, orderSingles) + fillDisplay → mỗi buổi 1 chấm màu / 1 dòng
    private List<MyPtSessionRow> sessions = new ArrayList<>();
    // ← countSessions: tổng số buổi → "x/y buổi theo lịch này", "N buổi" (buổi lẻ)
    private int totalCount;
    // ← countSessions: đếm statusKey checkedIn → chú thích "Đã check-in N"
    private int checkedInCount;
    // ← countSessions: đếm missed → "Vắng N"
    private int missedCount;
    // ← countSessions: đếm cancelled → "Đã hủy N"
    private int cancelledCount;
    // ← countSessions: đếm pending → "Đang cập nhật N"
    private int pendingCount;
    // ← countSessions: còn lại (sắp tới) → "Sắp tới N", câu CTA "Gói còn N buổi"
    private int remainingCount;

    // ===== IN RA MÀN — khung "Cần để ý" =====
    // ← buildWatchList: buổi sắp tới mà ngày đó chưa có gói gym / vé ngày → số "N buổi", có thì hiện khung
    private List<MyPtSessionRow> watchList = new ArrayList<>();
    // ← groupByReason(watchList): gom theo lý do (SessionGroup) → _booking-groups.jspf
    private List<SessionGroup> watchGroups = new ArrayList<>();

    public boolean isNoData() {
        return activePackages.isEmpty() && endedPackages.isEmpty() && singleSessionCount == 0;
    }

    public List<MyPtPackageView> getActivePackages() { return activePackages; }
    public List<MyPtPackageView> getEndedPackages() { return endedPackages; }

    public MyPtPackageView getSelectedPackage() { return selectedPackage; }
    public void setSelectedPackage(MyPtPackageView selectedPackage) { this.selectedPackage = selectedPackage; }

    public List<MyPtSessionRow> getSessions() { return sessions; }
    public void setSessions(List<MyPtSessionRow> sessions) { this.sessions = sessions; }

    public String getOpeningHours() { return openingHours; }
    public void setOpeningHours(String openingHours) { this.openingHours = openingHours; }

    public long getDaysLeft() { return daysLeft; }
    public void setDaysLeft(long daysLeft) { this.daysLeft = daysLeft; }

    public String getTemplateSummary() { return templateSummary; }
    public void setTemplateSummary(String templateSummary) { this.templateSummary = templateSummary; }

    public int getSingleSessionCount() { return singleSessionCount; }
    public void setSingleSessionCount(int singleSessionCount) { this.singleSessionCount = singleSessionCount; }

    public boolean isSingleSelected() { return singleSelected; }
    public void setSingleSelected(boolean singleSelected) { this.singleSelected = singleSelected; }

    public MyPtSessionRow getFocusSession() { return focusSession; }
    public void setFocusSession(MyPtSessionRow focusSession) { this.focusSession = focusSession; }

    public Integer getFocusSessionId() { return focusSessionId; }
    public void setFocusSessionId(Integer focusSessionId) { this.focusSessionId = focusSessionId; }

    public long getDaysUntilFocus() { return daysUntilFocus; }
    public void setDaysUntilFocus(long daysUntilFocus) { this.daysUntilFocus = daysUntilFocus; }

    public String getCtaKind() { return ctaKind; }
    public void setCtaKind(String ctaKind) { this.ctaKind = ctaKind; }

    public int getRecentSingleCount() { return recentSingleCount; }
    public void setRecentSingleCount(int recentSingleCount) { this.recentSingleCount = recentSingleCount; }

    public String getHotline() { return hotline; }
    public void setHotline(String hotline) { this.hotline = hotline; }

    public String getHotlineTel() { return hotlineTel; }
    public void setHotlineTel(String hotlineTel) { this.hotlineTel = hotlineTel; }

    public List<WeekdaySlot> getWeekTemplate() { return weekTemplate; }
    public void setWeekTemplate(List<WeekdaySlot> weekTemplate) { this.weekTemplate = weekTemplate; }

    public int getTemplateCount() { return templateCount; }
    public void setTemplateCount(int templateCount) { this.templateCount = templateCount; }

    public int getTodayWeekday() { return todayWeekday; }
    public void setTodayWeekday(int todayWeekday) { this.todayWeekday = todayWeekday; }

    public List<MyPtSessionRow> getOffPattern() { return offPattern; }
    public void setOffPattern(List<MyPtSessionRow> offPattern) { this.offPattern = offPattern; }

    public int getTotalCount() { return totalCount; }
    public void setTotalCount(int totalCount) { this.totalCount = totalCount; }

    public int getCheckedInCount() { return checkedInCount; }
    public void setCheckedInCount(int checkedInCount) { this.checkedInCount = checkedInCount; }

    public int getMissedCount() { return missedCount; }
    public void setMissedCount(int missedCount) { this.missedCount = missedCount; }

    public int getCancelledCount() { return cancelledCount; }
    public void setCancelledCount(int cancelledCount) { this.cancelledCount = cancelledCount; }

    public int getPendingCount() { return pendingCount; }
    public void setPendingCount(int pendingCount) { this.pendingCount = pendingCount; }

    public int getRemainingCount() { return remainingCount; }
    public void setRemainingCount(int remainingCount) { this.remainingCount = remainingCount; }

    public List<MyPtSessionRow> getWatchList() { return watchList; }
    public void setWatchList(List<MyPtSessionRow> watchList) { this.watchList = watchList; }

    public List<SessionGroup> getWatchGroups() { return watchGroups; }
    public void setWatchGroups(List<SessionGroup> watchGroups) { this.watchGroups = watchGroups; }
}
