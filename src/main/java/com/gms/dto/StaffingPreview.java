package com.gms.dto;

/**
 * Dự báo quân số còn lại của một ca nếu đơn đang soạn được duyệt (SRS 5.2.1).
 * Service tạo, Servlet trả JSON cho form nộp đơn (màn #25).
 */
public class StaffingPreview {

    private final int remainingPt;
    private final int remainingReceptionist;
    private final int minPt;
    private final int minReceptionist;

    public StaffingPreview(int remainingPt, int remainingReceptionist, int minPt, int minReceptionist) {
        this.remainingPt = remainingPt;
        this.remainingReceptionist = remainingReceptionist;
        this.minPt = minPt;
        this.minReceptionist = minReceptionist;
    }

    public int getRemainingPt() {
        return remainingPt;
    }

    public int getRemainingReceptionist() {
        return remainingReceptionist;
    }

    public int getMinPt() {
        return minPt;
    }

    public int getMinReceptionist() {
        return minReceptionist;
    }
}
