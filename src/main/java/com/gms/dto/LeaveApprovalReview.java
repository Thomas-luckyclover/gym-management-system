package com.gms.dto;

import java.util.ArrayList;
import java.util.List;

/** Kết quả kiểm tra dùng chung cho danh sách, chi tiết và lần duyệt thật. */
public class LeaveApprovalReview {

    private final List<ShiftStaffingRow> staffingRows = new ArrayList<>();
    private final List<ReviewCheck> checks = new ArrayList<>();

    /** null nghĩa là mọi điều kiện đạt; giao diện và POST đọc cùng kết quả này. */
    public String getBlockingMessage() {
        for (ReviewCheck check : checks) {
            if (check.isApplicable() && !check.isOk()) {
                return check.getMessage();
            }
        }
        return null;
    }

    public List<ShiftStaffingRow> getStaffingRows() {
        return staffingRows;
    }

    public List<ReviewCheck> getChecks() {
        return checks;
    }

}
