package com.gms.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * DTO 1 BUỔI PT của khách — DAO TẠO: MyPtSessionDAO (findSessionsOfPackage / findSingleSessions);
 * Service ĐIỀN thêm: MyPtSessionService.fillDisplay (trạng thái, lý do, Zalo…) và MyBookingService (moveNote, firstOfWeek, rebook).
 * Dùng chung 2 màn: Gói của tôi (my-bookings.jsp) và Lịch tập (my-pt-sessions.jsp).
 * Hàm tính sẵn: getDayName() "Thứ 2…Chủ nhật" · isCouple() · isSingleSession() · isEnded().
 */
public class MyPtSessionRow {
    public MyPtSessionRow(int sessionId, String status, String missedReason, String cancelType,
            String cancelReason, LocalDateTime cancelledAt, LocalDate slotDate,
            LocalTime startTime, LocalTime endTime, int subscriptionId, String type,
            LocalDate packageStartDate, LocalDate packageEndDate, int holderCustomerId,
            Integer partnerCustomerId, String packageName, int sessionNo, int totalSessions,
            int ptEmployeeId, String ptName, String ptPhone, String holderName,
            String partnerFullName) {
        this.sessionId = sessionId;
        this.status = status;
        this.missedReason = missedReason;
        this.cancelType = cancelType;
        this.cancelReason = cancelReason;
        this.cancelledAt = cancelledAt;
        this.slotDate = slotDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.subscriptionId = subscriptionId;
        this.type = type;
        this.packageStartDate = packageStartDate;
        this.packageEndDate = packageEndDate;
        this.holderCustomerId = holderCustomerId;
        this.partnerCustomerId = partnerCustomerId;
        this.packageName = packageName;
        this.sessionNo = sessionNo;
        this.totalSessions = totalSessions;
        this.ptEmployeeId = ptEmployeeId;
        this.ptName = ptName;
        this.ptPhone = ptPhone;
        this.holderName = holderName;
        this.partnerFullName = partnerFullName;
    }

    public MyPtSessionRow() {
    }

    // ===== IN RA MÀN — ngày giờ, số thứ tự =====
    // ← Sessions.SessionId → link "…&openSession=<id>" mở đúng buổi trên Lịch tập
    private int sessionId;
    // ← TimeSlots.SlotDate → ngày của buổi (dải chấm, "Buổi tới", danh sách)
    private LocalDate slotDate;
    // ← TimeSlots.StartTime → giờ bắt đầu
    private LocalTime startTime;
    // ← TimeSlots.EndTime → giờ kết thúc
    private LocalTime endTime;
    // ← DAO đánh số (ROW_NUMBER) → "Buổi 3/24" khi rê chuột vào chấm
    private int sessionNo;
    // ← tổng buổi của gói → "Buổi 3/24"
    private int totalSessions;

    // ===== IN RA MÀN — trạng thái (fillDisplay) =====
    // ← fillDisplay → applyStatus: checkedIn | missed | cancelled | pending | warn | upcoming → màu chấm / nhãn (data-status)
    private String statusKey;
    // ← fillDisplay: chữ trạng thái "Đã check-in", "Vắng"… → nhãn trạng thái
    private String statusLabel;
    // ← fillDisplay: lý do (VD "Chưa có gói thành viên ngày này") → chú thích khung "Cần để ý", title của "Buổi tới"
    private String explanation;
    // ← fillDisplay: buổi sắp tới mà ngày đó chưa có gói gym / vé ngày → vào "Cần để ý", chữ "Cần Membership/DayPass"
    private boolean noGymPass;

    // ===== IN RA MÀN — PT =====
    // ← Sessions.PtEmployeeId → link "Đặt lại với PT này" (?pt=<id>)
    private int ptEmployeeId;
    // ← tên PT → "PT …" ở dòng buổi lẻ, khung "PT buổi tới"
    private String ptName;
    // ← SĐT PT → nút gọi PT buổi tới
    private String ptPhone;
    // ← fillDisplay: link Zalo từ ptPhone → nút "Nhắn Zalo"
    private String zaloUrl;

    // ===== IN RA MÀN — chỉ màn Gói của tôi (MyBookingService điền) =====
    // ← fillPackageDetail → moveNote(MyBookingDAO.findMoves): "PT dời lịch · thay buổi T3 05/11" → cột ghi chú "Buổi khác giờ"
    private String moveNote;
    // ← markWeeks: buổi đầu tiên của tuần mới → JSP chèn vạch ngăn tuần trong dải chấm
    private boolean firstOfWeek;
    // ← orderSingles: buổi lẻ đã qua, mỗi PT lấy 1 buổi gần nhất → link "Đặt lại với PT này"
    private boolean rebook;

    // ===== IN RA MÀN — chỉ màn Lịch tập (my-pt-sessions.jsp) =====
    // ← fillDisplay: "Gói PT đơn / đôi / Buổi lẻ"
    private String typeLabel;
    // ← fillDisplay: tên người kia của gói đôi
    private String partnerName;
    // ← tên gói
    private String packageName;
    // ← Sessions.CancelReason → lý do hủy trong chi tiết buổi
    private String cancelReason;
    // ← fillDisplay: link "Thêm vào Google Calendar" (buổi Scheduled chưa kết thúc)
    private String calendarUrl;

    // ===== KHÔNG IN RA MÀN (chỉ dùng trong code) =====
    private String status;
    private String missedReason;
    private String cancelType;
    private LocalDateTime cancelledAt;
    private int subscriptionId;
    private String type;
    private LocalDate packageStartDate;
    private LocalDate packageEndDate;
    private int holderCustomerId;
    private Integer partnerCustomerId;
    private String holderName;
    private String partnerFullName;
    private boolean ended;

    public boolean isEnded() { return ended; }

    public void setEnded(boolean ended) { this.ended = ended; }

    // GETTER TỰ TÍNH — không có field/setter "dayName"; = thứ của slotDate: "Thứ 2…Thứ 7", "Chủ nhật"; JSP: customer/my-bookings.jsp:204 …
    public String getDayName() {
        int day = slotDate.getDayOfWeek().getValue();
        return day == 7 ? "Chủ nhật" : "Thứ " + (day + 1);
    }

    // GETTER TỰ TÍNH — không có field/setter "couple"; = type == "PTCouple"; JSP: không dùng (chỉ Java/test)
    public boolean isCouple() {
        return "PTCouple".equals(type);
    }

    // GETTER TỰ TÍNH — không có field/setter "singleSession"; = type == "SingleSession"; JSP: customer/_session-card.jspf:20 …
    public boolean isSingleSession() {
        return "SingleSession".equals(type);
    }

    public int getPtEmployeeId() { return ptEmployeeId; }
    public void setPtEmployeeId(int ptEmployeeId) { this.ptEmployeeId = ptEmployeeId; }

    public String getMoveNote() { return moveNote; }
    public void setMoveNote(String moveNote) { this.moveNote = moveNote; }

    public boolean isFirstOfWeek() { return firstOfWeek; }
    public void setFirstOfWeek(boolean firstOfWeek) { this.firstOfWeek = firstOfWeek; }

    public boolean isNoGymPass() { return noGymPass; }
    public void setNoGymPass(boolean noGymPass) { this.noGymPass = noGymPass; }

    public boolean isRebook() { return rebook; }
    public void setRebook(boolean rebook) { this.rebook = rebook; }

    public int getSessionId() { return sessionId; }
    public void setSessionId(int sessionId) { this.sessionId = sessionId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getMissedReason() { return missedReason; }
    public void setMissedReason(String missedReason) { this.missedReason = missedReason; }

    public String getCancelType() { return cancelType; }
    public void setCancelType(String cancelType) { this.cancelType = cancelType; }

    public String getCancelReason() { return cancelReason; }
    public void setCancelReason(String cancelReason) { this.cancelReason = cancelReason; }

    public LocalDateTime getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(LocalDateTime cancelledAt) { this.cancelledAt = cancelledAt; }

    public LocalDate getSlotDate() { return slotDate; }
    public void setSlotDate(LocalDate slotDate) { this.slotDate = slotDate; }

    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }

    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime endTime) { this.endTime = endTime; }

    public int getSubscriptionId() { return subscriptionId; }
    public void setSubscriptionId(int subscriptionId) { this.subscriptionId = subscriptionId; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public LocalDate getPackageStartDate() { return packageStartDate; }
    public void setPackageStartDate(LocalDate packageStartDate) { this.packageStartDate = packageStartDate; }

    public LocalDate getPackageEndDate() { return packageEndDate; }
    public void setPackageEndDate(LocalDate packageEndDate) { this.packageEndDate = packageEndDate; }

    public int getHolderCustomerId() { return holderCustomerId; }
    public void setHolderCustomerId(int holderCustomerId) { this.holderCustomerId = holderCustomerId; }

    public Integer getPartnerCustomerId() { return partnerCustomerId; }
    public void setPartnerCustomerId(Integer partnerCustomerId) { this.partnerCustomerId = partnerCustomerId; }

    public String getPackageName() { return packageName; }
    public void setPackageName(String packageName) { this.packageName = packageName; }

    public int getSessionNo() { return sessionNo; }
    public void setSessionNo(int sessionNo) { this.sessionNo = sessionNo; }

    public int getTotalSessions() { return totalSessions; }
    public void setTotalSessions(int totalSessions) { this.totalSessions = totalSessions; }

    public String getPtName() { return ptName; }
    public void setPtName(String ptName) { this.ptName = ptName; }

    public String getPtPhone() { return ptPhone; }
    public void setPtPhone(String ptPhone) { this.ptPhone = ptPhone; }

    public String getHolderName() { return holderName; }
    public void setHolderName(String holderName) { this.holderName = holderName; }

    public String getPartnerFullName() { return partnerFullName; }
    public void setPartnerFullName(String partnerFullName) { this.partnerFullName = partnerFullName; }

    public String getTypeLabel() { return typeLabel; }
    public void setTypeLabel(String typeLabel) { this.typeLabel = typeLabel; }

    public String getPartnerName() { return partnerName; }
    public void setPartnerName(String partnerName) { this.partnerName = partnerName; }

    public String getStatusKey() { return statusKey; }
    public void setStatusKey(String statusKey) { this.statusKey = statusKey; }

    public String getStatusLabel() { return statusLabel; }
    public void setStatusLabel(String statusLabel) { this.statusLabel = statusLabel; }

    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }

    public String getZaloUrl() { return zaloUrl; }
    public void setZaloUrl(String zaloUrl) { this.zaloUrl = zaloUrl; }

    public String getCalendarUrl() { return calendarUrl; }
    public void setCalendarUrl(String calendarUrl) { this.calendarUrl = calendarUrl; }
}
