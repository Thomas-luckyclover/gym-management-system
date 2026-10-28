package com.gms.dto;

/**
 * Một dòng định biên của một ca cho một chức vụ trên màn hình duyệt đơn (màn #27, UC-15).
 */
public class ShiftStaffingRow {

    private final String shiftLabel;    // "Ca sáng" / "Ca chiều"
    private final int current;          // đang trực (chưa trừ đơn đang xét)
    private final int afterApproval;    // nếu duyệt đơn này
    private final int minRequired;      // P-13 / P-14

    public ShiftStaffingRow(
            String shiftLabel,
            int current,
            int afterApproval,
            int minRequired
    ) {
        this.shiftLabel = shiftLabel;
        this.current = current;
        this.afterApproval = afterApproval;
        this.minRequired = minRequired;
    }

    public String getShiftLabel() {
        return shiftLabel;
    }

    public int getCurrent() {
        return current;
    }

    public int getAfterApproval() {
        return afterApproval;
    }

    public int getMinRequired() {
        return minRequired;
    }

    public boolean isBelowMin() {
        return afterApproval < minRequired;
    }
}
