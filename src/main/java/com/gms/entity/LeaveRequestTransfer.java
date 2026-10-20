package com.gms.entity;

import java.time.LocalDateTime;

/**
 * Bản ghi chuyển người duyệt đơn nghỉ phép (Bảng LeaveRequestTransfers, BF-05).
 */
public class LeaveRequestTransfer {

    private int transferId;
    private int leaveRequestId;
    private int fromEmployeeId;
    private String fromEmployeeName;
    private int toEmployeeId;
    private String toEmployeeName;
    private String note;
    private LocalDateTime createdAt;

    public LeaveRequestTransfer() {
    }

    public LeaveRequestTransfer(int leaveRequestId, int fromEmployeeId, int toEmployeeId, String note) {
        this.leaveRequestId = leaveRequestId;
        this.fromEmployeeId = fromEmployeeId;
        this.toEmployeeId = toEmployeeId;
        this.note = note;
        this.createdAt = LocalDateTime.now();
    }

    public LeaveRequestTransfer(int transferId, int leaveRequestId, int fromEmployeeId, String fromEmployeeName, int toEmployeeId, String toEmployeeName, String note, LocalDateTime createdAt) {
        this.transferId = transferId;
        this.leaveRequestId = leaveRequestId;
        this.fromEmployeeId = fromEmployeeId;
        this.fromEmployeeName = fromEmployeeName;
        this.toEmployeeId = toEmployeeId;
        this.toEmployeeName = toEmployeeName;
        this.note = note;
        this.createdAt = createdAt;
    }

    public int getTransferId() {
        return transferId;
    }

    public void setTransferId(int transferId) {
        this.transferId = transferId;
    }

    public int getLeaveRequestId() {
        return leaveRequestId;
    }

    public void setLeaveRequestId(int leaveRequestId) {
        this.leaveRequestId = leaveRequestId;
    }

    public int getFromEmployeeId() {
        return fromEmployeeId;
    }

    public void setFromEmployeeId(int fromEmployeeId) {
        this.fromEmployeeId = fromEmployeeId;
    }

    public String getFromEmployeeName() {
        return fromEmployeeName;
    }

    public void setFromEmployeeName(String fromEmployeeName) {
        this.fromEmployeeName = fromEmployeeName;
    }

    public int getToEmployeeId() {
        return toEmployeeId;
    }

    public void setToEmployeeId(int toEmployeeId) {
        this.toEmployeeId = toEmployeeId;
    }

    public String getToEmployeeName() {
        return toEmployeeName;
    }

    public void setToEmployeeName(String toEmployeeName) {
        this.toEmployeeName = toEmployeeName;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
