package com.gms.dto;

import java.util.ArrayList;
import java.util.List;

/** Kết quả kiểm tra dùng chung cho danh sách, chi tiết và lần duyệt thật. */
public class LeaveApprovalReview {

    private String inputError;
    private LeaveBalance balance;
    private int minStaffRequired;
    private int remainingStaff;
    private List<ShiftStaffingRow> staffingRows = new ArrayList<>();
    private List<ReviewCheck> checks = new ArrayList<>();

    /** null nghĩa là mọi điều kiện đạt; giao diện và POST đọc cùng kết quả này. */
    public String getBlockingMessage() {
        if (inputError != null) {
            return inputError;
        }
        for (ReviewCheck check : checks) {
            if (check.isApplicable() && !check.isOk()) {
                return check.getMessage();
            }
        }
        return null;
    }

    public String getInputError() {
        return inputError;
    }

    public void setInputError(String inputError) {
        this.inputError = inputError;
    }

    public LeaveBalance getBalance() {
        return balance;
    }

    public void setBalance(LeaveBalance balance) {
        this.balance = balance;
    }

    public int getMinStaffRequired() {
        return minStaffRequired;
    }

    public void setMinStaffRequired(int minStaffRequired) {
        this.minStaffRequired = minStaffRequired;
    }

    public int getRemainingStaff() {
        return remainingStaff;
    }

    public void setRemainingStaff(int remainingStaff) {
        this.remainingStaff = remainingStaff;
    }

    public List<ShiftStaffingRow> getStaffingRows() {
        return staffingRows;
    }

    public void setStaffingRows(List<ShiftStaffingRow> staffingRows) {
        this.staffingRows = staffingRows;
    }

    public List<ReviewCheck> getChecks() {
        return checks;
    }

    public void setChecks(List<ReviewCheck> checks) {
        this.checks = checks;
    }
}
