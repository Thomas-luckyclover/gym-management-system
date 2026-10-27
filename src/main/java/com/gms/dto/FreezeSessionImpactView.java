package com.gms.dto;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * DTO mô tả ảnh hưởng của việc bảo lưu gói tập lên các buổi PT của hội viên (CBR-62, CBR-67).
 * Trước khi lưu phiếu bảo lưu, Lễ tân được xem danh sách mọi buổi Scheduled bị tác động để thông báo cho khách.
 */
public class FreezeSessionImpactView {

    // DAO: Sessions.SessionId, mã định danh buổi tập.
    // JSP: gắn vào thuộc tính dữ liệu hoặc in thứ tự buổi.
    private int sessionId;

    // DAO: TimeSlots.SlotDate, ngày diễn ra buổi tập.
    // JSP: định dạng dd/MM/yyyy.
    private LocalDate slotDate;

    // DAO: TimeSlots.StartTime, giờ bắt đầu buổi tập.
    // JSP: định dạng HH:mm.
    private LocalTime startTime;

    // DAO: TimeSlots.EndTime, giờ kết thúc buổi tập.
    // JSP: định dạng HH:mm.
    private LocalTime endTime;

    // DAO: Persons.FullName của PT phụ trách buổi tập.
    // JSP: in tên huấn luyện viên.
    private String ptName;

    // DAO: Subscriptions.SubscriptionType (PTSingle, PTCouple, SingleSession).
    // JSP: nhãn phân loại gói PT.
    private String packageType;

    // DAO: Họ tên người tập cùng nếu là gói PTCouple (từ Orders.PartnerCustomerId -> Persons.FullName).
    // JSP: hiển thị tên bạn tập nếu có.
    private String partnerName;

    // Service: xác định buổi có chuyển sang trạng thái Missed (MembershipFrozen) hay không.
    // Đối với gói đôi, nếu partner vẫn có membership hợp lệ thì buổi vẫn giữ Scheduled (CBR-67).
    private boolean willBeMissed;

    // Service: lý do/ghi chú giải thích vì sao bị Missed hoặc vẫn giữ Scheduled.
    // JSP: hiển thị tooltip hoặc nhãn giải thích cho Lễ tân.
    private String impactExplanation;

    public FreezeSessionImpactView() {
    }

    public FreezeSessionImpactView(int sessionId, LocalDate slotDate, LocalTime startTime, LocalTime endTime,
                                  String ptName, String packageType, String partnerName,
                                  boolean willBeMissed, String impactExplanation) {
        this.sessionId = sessionId;
        this.slotDate = slotDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.ptName = ptName;
        this.packageType = packageType;
        this.partnerName = partnerName;
        this.willBeMissed = willBeMissed;
        this.impactExplanation = impactExplanation;
    }

    public int getSessionId() {
        return sessionId;
    }

    public void setSessionId(int sessionId) {
        this.sessionId = sessionId;
    }

    public LocalDate getSlotDate() {
        return slotDate;
    }

    public void setSlotDate(LocalDate slotDate) {
        this.slotDate = slotDate;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalTime endTime) {
        this.endTime = endTime;
    }

    public String getPtName() {
        return ptName;
    }

    public void setPtName(String ptName) {
        this.ptName = ptName;
    }

    public String getPackageType() {
        return packageType;
    }

    public void setPackageType(String packageType) {
        this.packageType = packageType;
    }

    public String getPartnerName() {
        return partnerName;
    }

    public void setPartnerName(String partnerName) {
        this.partnerName = partnerName;
    }

    public boolean isWillBeMissed() {
        return willBeMissed;
    }

    public void setWillBeMissed(boolean willBeMissed) {
        this.willBeMissed = willBeMissed;
    }

    public String getImpactExplanation() {
        return impactExplanation;
    }

    public void setImpactExplanation(String impactExplanation) {
        this.impactExplanation = impactExplanation;
    }
}
