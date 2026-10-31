package com.gms.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * DTO hiển thị một dòng trong bảng danh sách bảo lưu (Màn hình Bảo lưu Lễ tân #34, UC-18).
 * Bao gồm cả thông tin hồ sơ khách, gói tập liên kết, thời hạn bảo lưu và nhân viên thực hiện.
 */
public class FreezeRecordItemView {

    // DAO: FreezeRecords.FreezeRecordId, mã định danh bản ghi bảo lưu.
    private int freezeRecordId;

    // DAO: Subscriptions.SubscriptionId, mã quyền sử dụng gói tập.
    private int subscriptionId;

    // DAO: Customers.CustomerId, mã định danh khách hàng.
    private int customerId;

    // DAO: Customers.CustomerCode, mã hiển thị KH-xxxx.
    private String customerCode;

    // DAO: Persons.FullName, họ tên hội viên.
    private String customerName;

    // DAO: Persons.Phone, số điện thoại liên hệ.
    private String customerPhone;

    // DAO: Packages.PackageName, tên gói tập Membership.
    private String packageName;

    // DAO: FreezeRecords.StartDate, ngày bắt đầu bảo lưu.
    private LocalDate startDate;

    // DAO: FreezeRecords.PlannedEndDate, ngày dự kiến kết thúc bảo lưu.
    private LocalDate plannedEndDate;

    // DAO: FreezeRecords.ActualEndDate, ngày kết thúc thực tế (NULL nếu đang bảo lưu).
    private LocalDate actualEndDate;

    // DAO: FreezeRecords.Reason, lý do khách yêu cầu bảo lưu.
    private String reason;

    // DAO: Employees.EmployeeCode + Persons.FullName của Lễ tân tiếp nhận.
    private String handledByEmployeeName;

    // DAO: FreezeRecords.CreatedAt, thời điểm lập phiếu bảo lưu.
    private LocalDateTime createdAt;

    // Service: tính số ngày đã bảo lưu thực tế hoặc dự kiến.
    private int daysFrozen;

    // Service: nhãn trạng thái ("Đang bảo lưu", "Hủy sớm", "Đã hoàn thành").
    private String statusLabel;

    // Service: mã CSS badge (badge-warning, badge-info, badge-ok).
    private String statusBadgeClass;

    // Service: cờ cho phép Lễ tân bấm "Hủy bảo lưu sớm" (chỉ khi ActualEndDate is null).
    private boolean canCancelEarly;

    public FreezeRecordItemView() {
    }

    public int getFreezeRecordId() {
        return freezeRecordId;
    }

    public void setFreezeRecordId(int freezeRecordId) {
        this.freezeRecordId = freezeRecordId;
    }

    public int getSubscriptionId() {
        return subscriptionId;
    }

    public void setSubscriptionId(int subscriptionId) {
        this.subscriptionId = subscriptionId;
    }

    public int getCustomerId() {
        return customerId;
    }

    public void setCustomerId(int customerId) {
        this.customerId = customerId;
    }

    public String getCustomerCode() {
        return customerCode;
    }

    public void setCustomerCode(String customerCode) {
        this.customerCode = customerCode;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getCustomerPhone() {
        return customerPhone;
    }

    public void setCustomerPhone(String customerPhone) {
        this.customerPhone = customerPhone;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getPlannedEndDate() {
        return plannedEndDate;
    }

    public void setPlannedEndDate(LocalDate plannedEndDate) {
        this.plannedEndDate = plannedEndDate;
    }

    public LocalDate getActualEndDate() {
        return actualEndDate;
    }

    public void setActualEndDate(LocalDate actualEndDate) {
        this.actualEndDate = actualEndDate;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getHandledByEmployeeName() {
        return handledByEmployeeName;
    }

    public void setHandledByEmployeeName(String handledByEmployeeName) {
        this.handledByEmployeeName = handledByEmployeeName;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public int getDaysFrozen() {
        return daysFrozen;
    }

    public void setDaysFrozen(int daysFrozen) {
        this.daysFrozen = daysFrozen;
    }

    public String getStatusLabel() {
        return statusLabel;
    }

    public void setStatusLabel(String statusLabel) {
        this.statusLabel = statusLabel;
    }

    public String getStatusBadgeClass() {
        return statusBadgeClass;
    }

    public void setStatusBadgeClass(String statusBadgeClass) {
        this.statusBadgeClass = statusBadgeClass;
    }

    public boolean isCanCancelEarly() {
        return canCancelEarly;
    }

    public void setCanCancelEarly(boolean canCancelEarly) {
        this.canCancelEarly = canCancelEarly;
    }
}
