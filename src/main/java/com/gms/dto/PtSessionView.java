package com.gms.dto;

import com.gms.entity.Package;
import com.gms.entity.Session;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Dữ liệu một buổi trong lịch 7 ngày của PT.
 * PtDashboardDAO sẽ tạo DTO từ Sessions, TimeSlots, Subscriptions, Orders và hồ sơ khách.
 * PtDashboardService sẽ dùng các DTO này để gom lịch theo ngày và chuẩn bị nhãn cho JSP.
 * DAO tạo 9 field dữ liệu gốc; Service bổ sung nhãn hiển thị và điều kiện mở hộp xử lý.
 */
public class PtSessionView {

    // ===== 1. FIELD =====

    // ----- 1.1. DAO đọc từ DB và gán qua constructor: dữ liệu gốc, không có setter -----

    // DAO: Sessions.SessionId, số nguyên định danh buổi.
    // Không in ra trang; gắn vào data-session-id của nút “Xử lý”.
    private final int sessionId;

    // DAO: TimeSlots.SlotDate, ngày diễn ra buổi.
    // JSP dùng gms:formatDate để hiện dd/MM/yyyy, ví dụ 06/10/2026.
    private final LocalDate slotDate;

    // DAO: TimeSlots.StartTime, giờ bắt đầu buổi.
    // JSP dùng gms:formatTime để hiện HH:mm, ví dụ 17:00.
    private final LocalTime startTime;

    // DAO: TimeSlots.EndTime, giờ kết thúc buổi.
    // JSP ghép với startTime thành khoảng giờ, ví dụ 17:00 – 18:30.
    private final LocalTime endTime;

    // DAO: Persons.FullName của Customers được Subscriptions.CustomerId trỏ tới.
    // JSP dùng c:out để hiện tên khách đứng tên gói.
    private final String customerName;

    // DAO: Persons.FullName của khách được Orders.PartnerCustomerId trỏ tới.
    // Gói đôi hiện “Tên khách & Tên người đi cùng”; gói đơn nhận null và không hiện phần này.
    private final String partnerName;

    // DAO: Subscriptions.SubscriptionType, chuyển bằng Package.Type.fromDbValue.
    // Service đổi thành nhãn “PT cá nhân”, “PT đôi” hoặc “Buổi lẻ” để JSP hiển thị.
    private final Package.Type subscriptionType;

    // DAO: Sessions.Status, chuyển bằng Session.Status.fromDbValue.
    // Service chuẩn bị nhãn và màu badge; JSP không in tên enum như CHECKED_IN.
    private final Session.Status status;

    // DAO: Sessions.MissedReason, chuyển bằng Session.MissedReason.fromDbValue.
    // Service đổi thành lý do tiếng Việt khi buổi Missed; trạng thái khác nhận null.
    private final Session.MissedReason missedReason;

    // ----- 1.2. Service tính từ dữ liệu gốc và gán qua setter: dữ liệu để JSP hiển thị -----

    // Service: đổi getSubscriptionType() thành nhãn tiếng Việt.
    // JSP hiện dưới tên khách, ví dụ “PT đôi”.
    private String subscriptionTypeLabel;

    // Service: đổi getStatus() thành nhãn tiếng Việt.
    // JSP hiện trong badge, ví dụ “Đã lên lịch” hoặc “Vắng”.
    private String statusLabel;

    // Service: chọn class badge từ getStatus(), dùng các màu có sẵn trong common.css.
    // Không in thành chữ; JSP gắn class này vào badge trạng thái.
    private String statusBadgeClass;

    // Service: đổi getMissedReason() thành lời giải thích khi getStatus() là MISSED.
    // JSP hiện dưới loại gói, ví dụ “Membership đang bảo lưu”; null thì không hiện.
    private String missedReasonLabel;

    // Service: true khi getStatus() là SCHEDULED và slotDate + startTime còn ở tương lai.
    // JSP dùng để hiện nút mở hộp “Xử lý”; không phải quyền thực hiện bốn thao tác trong hộp.
    private boolean canOpenActions;

    // ===== 2. CONSTRUCTOR: DAO truyền 9 field của nhóm 1.1 =====
    // Các field nhóm 1.2 được Service bổ sung sau khi DAO trả DTO.

    /**
     * Nhận một dòng dữ liệu đã được DAO đọc và chuyển sang kiểu Java.
     * @param sessionId mã buổi dùng cho thao tác
     * @param slotDate ngày diễn ra buổi
     * @param startTime giờ bắt đầu
     * @param endTime giờ kết thúc
     * @param customerName tên khách đứng tên gói
     * @param partnerName tên người đi cùng, null nếu không có
     * @param subscriptionType loại gói PT hoặc buổi lẻ
     * @param status trạng thái buổi
     * @param missedReason lý do vắng, null nếu buổi không Missed
     */
    public PtSessionView(int sessionId, LocalDate slotDate, LocalTime startTime, LocalTime endTime,
            String customerName, String partnerName, Package.Type subscriptionType,
            Session.Status status, Session.MissedReason missedReason) {
        // B1: nhận mã buổi và khung thời gian từ DAO.
        this.sessionId = sessionId;
        this.slotDate = slotDate;
        this.startTime = startTime;
        this.endTime = endTime;

        // B2: nhận tên khách và loại gói của buổi.
        this.customerName = customerName;
        this.partnerName = partnerName;
        this.subscriptionType = subscriptionType;

        // B3: giữ nguyên kết quả chốt buổi trong DB.
        this.status = status;
        this.missedReason = missedReason;
    }

    // ===== 3. GETTER / SETTER: cùng thứ tự với các field ở phần 1 =====

    // ----- 3.1. Getter của nhóm DAO (1.1): đọc dữ liệu gốc, không có setter -----

    /** Mã buổi để gắn vào nút xử lý. */
    public int getSessionId() {
        return sessionId;
    }

    /** Ngày để Service gom nhóm và JSP định dạng. */
    public LocalDate getSlotDate() {
        return slotDate;
    }

    /** Giờ bắt đầu để JSP hiển thị khoảng giờ. */
    public LocalTime getStartTime() {
        return startTime;
    }

    /** Giờ kết thúc để Service xét buổi đã qua giờ. */
    public LocalTime getEndTime() {
        return endTime;
    }

    /** Tên khách đứng tên gói, JSP xuất bằng c:out. */
    public String getCustomerName() {
        return customerName;
    }

    /** Tên người đi cùng, có thể null với gói đơn. */
    public String getPartnerName() {
        return partnerName;
    }

    /** Loại gói để Service chọn nhãn hiển thị. */
    public Package.Type getSubscriptionType() {
        return subscriptionType;
    }

    /** Trạng thái để Service chọn nhãn và badge. */
    public Session.Status getStatus() {
        return status;
    }

    /** Lý do để giải thích buổi vắng trên lịch. */
    public Session.MissedReason getMissedReason() {
        return missedReason;
    }

    // ----- 3.2. Getter / setter của nhóm Service (1.2): mỗi cặp ứng với một field -----
    // Getter để JSP đọc; setter để Service gán. Setter không cập nhật database.

    // subscriptionTypeLabel: nhãn loại gói.

    /** Nhãn loại gói để JSP hiện dưới tên khách. */
    public String getSubscriptionTypeLabel() {
        return subscriptionTypeLabel;
    }

    /** Nhận nhãn loại gói do Service chọn. */
    public void setSubscriptionTypeLabel(String subscriptionTypeLabel) {
        this.subscriptionTypeLabel = subscriptionTypeLabel;
    }

    // statusLabel: nhãn trạng thái buổi.

    /** Chữ tiếng Việt trong badge trạng thái. */
    public String getStatusLabel() {
        return statusLabel;
    }

    /** Nhận nhãn trạng thái do Service chọn. */
    public void setStatusLabel(String statusLabel) {
        this.statusLabel = statusLabel;
    }

    // statusBadgeClass: class CSS của badge trạng thái.

    /** Class CSS dùng màu badge có sẵn của dự án. */
    public String getStatusBadgeClass() {
        return statusBadgeClass;
    }

    /** Nhận class badge do Service chọn. */
    public void setStatusBadgeClass(String statusBadgeClass) {
        this.statusBadgeClass = statusBadgeClass;
    }

    // missedReasonLabel: lời giải thích buổi vắng.

    /** Lý do vắng bằng tiếng Việt, null nếu không cần hiện. */
    public String getMissedReasonLabel() {
        return missedReasonLabel;
    }

    /** Nhận lý do hiển thị do Service chuẩn bị. */
    public void setMissedReasonLabel(String missedReasonLabel) {
        this.missedReasonLabel = missedReasonLabel;
    }

    // canOpenActions: điều kiện hiện nút mở hộp xử lý.

    /** JSP đọc session.canOpenActions để quyết định hiện nút mở hộp. */
    public boolean isCanOpenActions() {
        return canOpenActions;
    }

    /** Nhận điều kiện mở hộp do Service xét trạng thái và giờ bắt đầu. */
    public void setCanOpenActions(boolean canOpenActions) {
        this.canOpenActions = canOpenActions;
    }
}
