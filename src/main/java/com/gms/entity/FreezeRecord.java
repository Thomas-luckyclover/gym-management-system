package com.gms.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Entity ánh xạ bảng FreezeRecords trong cơ sở dữ liệu.
 * Đại diện cho một lần bảo lưu (tạm dừng) gói tập Membership của hội viên (UC-18, BF-09).
 *
 * <p>Tuân thủ ràng buộc schema:
 * <ul>
 *   <li>PK: FreezeRecordId (tự tăng)</li>
 *   <li>FK: SubscriptionId tham chiếu Subscriptions</li>
 *   <li>FK: HandledByEmployeeId tham chiếu Employees</li>
 *   <li>StartDate & PlannedEndDate: khoảng thời gian dự kiến bảo lưu (StartDate sớm nhất ngày mai - DBR-13)</li>
 *   <li>ActualEndDate: NULL nếu đang bảo lưu; có ngày nếu hủy sớm (BR-26) hoặc đã hoàn tất (JOB-01)</li>
 *   <li>Reason: lý do bảo lưu, tối thiểu 5 ký tự (CK_FreezeRecords_Reason)</li>
 *   <li>CreatedAt: thời điểm lập phiếu bảo lưu</li>
 * </ul>
 * </p>
 */
public class FreezeRecord {

    private int freezeRecordId;
    private int subscriptionId;
    private int handledByEmployeeId;
    private LocalDate startDate;
    private LocalDate plannedEndDate;
    private LocalDate actualEndDate;
    private String reason;
    private LocalDateTime createdAt;

    /**
     * Constructor mặc định.
     */
    public FreezeRecord() {
    }

    /**
     * Constructor tạo mới một lượt bảo lưu (chưa có ngày kết thúc thực tế).
     *
     * @param subscriptionId mã quyền dùng dịch vụ
     * @param handledByEmployeeId mã nhân viên lễ tân xử lý
     * @param startDate ngày bắt đầu bảo lưu
     * @param plannedEndDate ngày kết thúc dự kiến
     * @param reason lý do bảo lưu
     */
    public FreezeRecord(int subscriptionId, int handledByEmployeeId,
                        LocalDate startDate, LocalDate plannedEndDate, String reason) {
        this.subscriptionId = subscriptionId;
        this.handledByEmployeeId = handledByEmployeeId;
        this.startDate = startDate;
        this.plannedEndDate = plannedEndDate;
        this.actualEndDate = null;
        this.reason = reason;
        this.createdAt = LocalDateTime.now();
    }

    /**
     * Constructor đầy đủ khi đọc từ database.
     */
    public FreezeRecord(int freezeRecordId, int subscriptionId, int handledByEmployeeId,
                        LocalDate startDate, LocalDate plannedEndDate, LocalDate actualEndDate,
                        String reason, LocalDateTime createdAt) {
        this.freezeRecordId = freezeRecordId;
        this.subscriptionId = subscriptionId;
        this.handledByEmployeeId = handledByEmployeeId;
        this.startDate = startDate;
        this.plannedEndDate = plannedEndDate;
        this.actualEndDate = actualEndDate;
        this.reason = reason;
        this.createdAt = createdAt;
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

    public int getHandledByEmployeeId() {
        return handledByEmployeeId;
    }

    public void setHandledByEmployeeId(int handledByEmployeeId) {
        this.handledByEmployeeId = handledByEmployeeId;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * Kiểm tra bản ghi bảo lưu có đang mở (đang trong kỳ bảo lưu chưa kết thúc) hay không.
     *
     * @return true nếu ActualEndDate là null
     */
    public boolean isOpen() {
        return actualEndDate == null;
    }
}
