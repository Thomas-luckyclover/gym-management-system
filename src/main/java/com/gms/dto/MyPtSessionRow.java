package com.gms.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 1 BUỔI PT của khách. Dùng ở màn "Lịch tập" (ô thời khóa biểu) và "Gói của tôi" (dòng bảng buổi).
 *   Phần 1: DAO đọc từ DB (MyPtSessionDAO.readSessionRow).
 *   Phần 2: Service tính thêm để hiển thị (MyPtSessionService.fillDisplay).
 */
public class MyPtSessionRow {

    // ---------------- Phần 1: DAO đọc từ DB (SESSIONS_SQL → readSessionRow) ----------------
    // Bảng gốc Sessions: một dòng là một buổi; các field dưới đây lấy trực tiếp, không ghép chữ từ bảng khác.
    private int sessionId;               // Sessions.SessionId
    private String status;               // Sessions.Status: Scheduled | CheckedIn | Missed | Cancelled
    private String missedReason;         // Sessions.MissedReason (khi Missed)
    private String cancelType;           // Sessions.CancelType (khi Cancelled)
    private String cancelReason;         // Sessions.CancelReason
    private LocalDateTime cancelledAt;   // Sessions.CancelledAt

    // JOIN Sessions.TimeSlotId = TimeSlots.TimeSlotId để lấy ngày/giờ của buổi này.
    private LocalDate slotDate;          // TimeSlots.SlotDate → ngày trên ô lịch và phần chi tiết
    private LocalTime startTime;         // TimeSlots.StartTime → giờ bắt đầu; Service dùng để xếp Sáng/Chiều/Tối
    private LocalTime endTime;           // TimeSlots.EndTime → giờ kết thúc; Service dùng để tính ended

    // JOIN Sessions.SubscriptionId = Subscriptions.SubscriptionId để lấy gói chứa buổi.
    private int subscriptionId;          // Subscriptions.SubscriptionId → link Xem toàn bộ gói
    private String type;                 // Subscriptions.SubscriptionType: PTSingle | PTCouple | SingleSession
    private LocalDate packageStartDate;  // Subscriptions.StartDate → đầu hạn gói; readSessionRow để null nếu buổi lẻ
    private LocalDate packageEndDate;    // Subscriptions.EndDate → cuối hạn gói; readSessionRow để null nếu buổi lẻ
    private int holderCustomerId;        // Subscriptions.CustomerId → người đứng tên, không nhất thiết là người đang xem

    // Từ gói JOIN Subscriptions.OrderId = Orders.OrderId để đọc thông tin đơn lúc mua.
    private Integer partnerCustomerId;   // Orders.PartnerCustomerId → người đi kèm; DAO chỉ gán cho PTCouple
    private String packageName;          // Orders.PackageNameSnapshot → tên gói lúc mua, không lấy tên hiện tại trong Packages

    // Không có cột SessionNo trong DB: SQL tự tính ROW_NUMBER() theo Sessions.SubscriptionId,
    // sắp TimeSlots.SlotDate, TimeSlots.StartTime, Sessions.SessionId; JOIN Sessions ↔ TimeSlots như trên.
    // Đánh số cả gói trước khi lọc ngày để đổi tuần vẫn giữ số buổi. Đây là tử số 31 trong “31/45”.
    private int sessionNo;
    // Subscriptions.GeneratedSessionCount, qua Sessions.SubscriptionId = Subscriptions.SubscriptionId.
    private int totalSessions;           // Mẫu số 45 trong “31/45”, không phải COUNT các buổi của tuần đang xem

    // PT của BUỔI: JOIN Sessions.PtEmployeeId = Employees.EmployeeId,
    // rồi Employees.PersonId = Persons.PersonId (SQL đặt alias ptPerson).
    private int ptEmployeeId;            // Sessions.PtEmployeeId → link "Đặt lại với PT này" (màn Gói của tôi, buổi lẻ)
    private String ptName;               // ptPerson.FullName = Persons.FullName → tên PT trên thẻ và chi tiết
    private String ptPhone;              // ptPerson.Phone = Persons.Phone → SĐT liên hệ, copy và tạo link Zalo

    // Người đứng tên: JOIN Subscriptions.CustomerId = Customers.CustomerId (alias holder),
    // rồi holder.PersonId = Persons.PersonId (alias holderPerson).
    private String holderName;           // holderPerson.FullName = Persons.FullName → tên người đứng tên gói

    // Người đi kèm: LEFT JOIN Orders.PartnerCustomerId = Customers.CustomerId (alias partner),
    // rồi LEFT JOIN partner.PersonId = Persons.PersonId (alias partnerPerson); không có người thì tên null.
    private String partnerFullName;      // partnerPerson.FullName = Persons.FullName → tên người đi kèm gói đôi
    // ---------------- Kết thúc phần 1: dữ liệu DAO đọc hoặc SQL tính ----------------

    // ---------------- Phần 2: Service tính thêm; các field này không có cột DB riêng ----------------
    // typeLabel(type): đổi Subscriptions.SubscriptionType thành chữ trên thẻ/tiêu đề chi tiết.
    private String typeLabel;            // PTSingle → PT cá nhân; PTCouple → PT đôi; còn lại → Buổi lẻ

    // fillDisplay: so holderCustomerId với mã khách đang xem; chọn partnerFullName hoặc holderName.
    // Hai tên đều từ Persons.FullName qua hai đường JOIN người đứng tên/người đi kèm đã ghi ở phần 1.
    private String partnerName;          // “Tập cùng”: luôn là người còn lại so với người đang xem

    // applyStatus: dùng Sessions.Status + ngày/giờ TimeSlots + now của request + warning do fillDisplay tính.
    // Warning có thể đọc OperatingHourExceptions (ngày nghỉ), Subscriptions/FreezeRecords (quyền vào gym/bảo lưu).
    private String statusKey;            // today | upcoming | warn | pending | checkedIn | missed | cancelled → JSP chọn màu
    private String statusLabel;          // applyStatus chọn chữ badge, ví dụ “Đang diễn ra”; không đọc một cột tên trạng thái

    // applyStatus: ghép lời giải thích từ Sessions.MissedReason/CancelType, loại gói và warning ở trên.
    private String explanation;          // Nội dung cảnh báo/lý do trong chi tiết; khác cancelReason là chữ đọc từ DB

    // zaloUrl(ptPhone): chuẩn hóa Persons.Phone của PT rồi ghép https://zalo.me/<số>; thiếu số → null.
    private String zaloUrl;

    // calendarUrl(row): ghép ngày/giờ TimeSlots (đổi UTC), ptName từ Persons.FullName,
    // packageName từ Orders.PackageNameSnapshot, sessionNo do SQL tính, totalSessions từ Subscriptions.
    // Chỉ tạo nếu Sessions.Status=Scheduled và now < giờ kết thúc; mở form Google để khách tự lưu.
    private String calendarUrl;

    // applyStatus: ended = now >= TimeSlots.SlotDate + TimeSlots.EndTime (qua JOIN Sessions ↔ TimeSlots).
    // JSP dùng nền trầm cho buổi đã hết giờ; vẫn cho bấm chi tiết và giữ nhãn kết quả đã lưu trong Sessions.Status.
    private boolean ended;

    // Chỉ màn "Gói của tôi" (MyBookingService) điền 3 field dưới:
    private String moveNote;             // vì sao buổi lệch lịch cố định: "PT dời lịch · thay buổi T3 06/10" (SessionMoveLogs)
    private boolean firstOfWeek;         // buổi đầu tiên của 1 tuần trong gói → dải chấm vẽ vạch ngăn tuần
    private boolean rebook;              // buổi lẻ gần nhất với PT này → hiện "Đặt lại với PT"
    // ---------------- Kết thúc phần 2: dữ liệu Service tính thêm ----------------

    /** Buổi đã hết giờ; chỉ điều khiển cách hiển thị, không thay trạng thái trong DB. */
    public boolean isEnded() { return ended; }

    /** Service điền từ giờ kết thúc của buổi. */
    public void setEnded(boolean ended) { this.ended = ended; }

    /** Thứ của ngày tập: "Thứ 2" … "Chủ nhật" (JSP: ${s.dayName}); tính từ TimeSlots.SlotDate. */
    public String getDayName() {
        int day = slotDate.getDayOfWeek().getValue();   // Thứ 2 = 1 … Chủ nhật = 7
        return day == 7 ? "Chủ nhật" : "Thứ " + (day + 1);
    }

    /** Gói đôi? */
    public boolean isCouple() {
        return "PTCouple".equals(type);
    }

    /** Buổi lẻ? */
    public boolean isSingleSession() {
        return "SingleSession".equals(type);
    }

    // ---------------- Getter / Setter ----------------
    public int getPtEmployeeId() { return ptEmployeeId; }
    public void setPtEmployeeId(int ptEmployeeId) { this.ptEmployeeId = ptEmployeeId; }

    public String getMoveNote() { return moveNote; }
    public void setMoveNote(String moveNote) { this.moveNote = moveNote; }

    public boolean isFirstOfWeek() { return firstOfWeek; }
    public void setFirstOfWeek(boolean firstOfWeek) { this.firstOfWeek = firstOfWeek; }

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
