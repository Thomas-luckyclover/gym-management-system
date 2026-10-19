package com.gms.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Dữ liệu màn "Gói của tôi" (/customer/bookings).
 *   Cột trái : activePackages, endedPackages, singleSessionCount
 *   Cột phải : gói đang chọn (selectedPackage, hoặc buổi lẻ khi singleSelected) + 5 ô đếm + bảng buổi
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
    private Integer focusSessionId;              // buổi để tự cuộn tới: buổi đầu tiên chưa kết thúc

    // 5 ô đếm (Tổng = Đã check-in + Vắng + Đã hủy + Còn lại + Đang cập nhật)
    private int totalCount;
    private int checkedInCount;
    private int missedCount;
    private int cancelledCount;
    private int remainingCount;                  // Hôm nay + Sắp tới + Cần chú ý
    private int pendingCount;                    // Đang cập nhật (đã kết thúc, chờ chốt) — JSP chỉ hiện khi > 0

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
    public void setPendingCount(int pendingCount) { this.pendingCount = pendingCount; }
}
