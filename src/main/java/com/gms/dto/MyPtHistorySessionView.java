package com.gms.dto;

import com.gms.entity.Package;
import com.gms.entity.Session;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Một dòng Lịch sử buổi tập do DAO tạo; Service bổ sung nhãn, giải thích và hint cho khách.
 * Gồm CheckedIn, Missed, Cancelled và Scheduled đã qua giờ kết thúc (chờ JOB-04 chốt).
 * Chỉ mang kết quả đã lưu trong Sessions, không tính lại từ CheckIns hoặc membership hiện tại.
 */
public class MyPtHistorySessionView {

    // Sessions.SessionId: nhận diện buổi cho bộ lọc/thao tác sau này, không in mã ra màn hình.
    private final int sessionId;
    // Subscriptions.SubscriptionId: nhận diện gói cho bộ lọc sau này, không in mã.
    private final int subscriptionId;
    // Subscriptions.CustomerId: Service phân biệt người xem đứng tên hay đi cùng gói đôi.
    private final int holderCustomerId;
    // Orders.PackageNameSnapshot: JSP in tên gói lúc mua, không đọc Packages hiện tại.
    private final String packageName;
    // Subscriptions.SubscriptionType: Service chọn nhãn cá nhân/đôi/buổi lẻ và hint gói đôi.
    private final Package.Type subscriptionType;
    // Sessions.PtEmployeeId → Employees → Persons.FullName: JSP in PT thực tế dạy buổi.
    private final String ptName;
    // Orders.PartnerCustomerId và khách đứng tên: JSP in thành viên còn lại, gói cá nhân null.
    private final String partnerName;
    // TimeSlots.SlotDate/StartTime/EndTime: Service xét "kết quả đang cập nhật", JSP in ngày giờ.
    private final LocalDate slotDate;
    private final LocalTime startTime;
    private final LocalTime endTime;
    // Sessions.Status: trạng thái lưu trong DB; Service đổi thành nhãn, không in mã enum.
    private final Session.Status status;
    // Sessions.MissedReason: chỉ có khi Status = Missed, các trường hợp khác null.
    private final Session.MissedReason missedReason;
    // Sessions.CancelType (ByPt/ByCustomer/GymClosed/PackageExpired): chỉ có khi Cancelled, còn lại null.
    private final Session.CancelType cancelType;
    // Sessions.CancelReason: lý do hủy PT chọn trong dropdown, có thể null; JSP in nguyên văn khi có.
    private final String cancelReason;

    // Service chọn từ subscriptionType: JSP in loại buổi.
    private String subscriptionTypeLabel;
    // Service đổi từ status và thời gian: JSP in badge (Đã check-in, Vắng, Đã hủy, Kết quả đang cập nhật).
    private String statusLabel;
    // Service chọn class badge dùng chung trong common.css (badge-info/success/danger/neutral).
    private String statusBadgeClass;
    // Service giải thích vì sao Vắng/Hủy hoặc đang chờ chốt; null nếu không cần.
    private String explanation;
    // Service chuẩn bị hint cho nhãn Đã check-in (cá nhân hoặc gói đôi); null với trạng thái khác.
    private String hint;

    /** Nhận dữ liệu một buổi từ SQL; không đổi trạng thái hoặc tính lại kết quả. */
    public MyPtHistorySessionView(int sessionId, int subscriptionId, int holderCustomerId,
            String packageName, Package.Type subscriptionType, String ptName, String partnerName,
            LocalDate slotDate, LocalTime startTime, LocalTime endTime,
            Session.Status status, Session.MissedReason missedReason,
            Session.CancelType cancelType, String cancelReason) {
        // B1: nhận định danh, gói và người liên quan đến buổi.
        this.sessionId = sessionId;
        this.subscriptionId = subscriptionId;
        this.holderCustomerId = holderCustomerId;
        this.packageName = packageName;
        this.subscriptionType = subscriptionType;
        this.ptName = ptName;
        this.partnerName = partnerName;
        // B2: nhận lịch thực tế của buổi, không dùng giờ cố định hoặc PT mặc định của gói.
        this.slotDate = slotDate;
        this.startTime = startTime;
        this.endTime = endTime;
        // B3: nhận kết quả đã chốt; Service chỉ diễn giải, không kết luận lại.
        this.status = status;
        this.missedReason = missedReason;
        this.cancelType = cancelType;
        this.cancelReason = cancelReason;
    }

    /** Mã buổi để nối thao tác sau này. */
    public int getSessionId() {
        return sessionId;
    }

    /** Mã gói để nối bộ lọc sau này. */
    public int getSubscriptionId() {
        return subscriptionId;
    }

    /** Khách đứng tên, dùng xác định người đang xem. */
    public int getHolderCustomerId() {
        return holderCustomerId;
    }

    /** Tên gói đã mua. */
    public String getPackageName() {
        return packageName;
    }

    /** Loại buổi gốc từ DB. */
    public Package.Type getSubscriptionType() {
        return subscriptionType;
    }

    /** PT thực tế dạy buổi này. */
    public String getPtName() {
        return ptName;
    }

    /** Thành viên còn lại của gói đôi, null với gói cá nhân và buổi lẻ. */
    public String getPartnerName() {
        return partnerName;
    }

    /** Ngày buổi tập thực tế. */
    public LocalDate getSlotDate() {
        return slotDate;
    }

    /** Giờ bắt đầu thực tế. */
    public LocalTime getStartTime() {
        return startTime;
    }

    /** Giờ kết thúc thực tế. */
    public LocalTime getEndTime() {
        return endTime;
    }

    /** Trạng thái buổi lưu trong DB. */
    public Session.Status getStatus() {
        return status;
    }

    /** Lý do vắng, chỉ có khi buổi Missed. */
    public Session.MissedReason getMissedReason() {
        return missedReason;
    }

    /** Loại hủy, chỉ có khi buổi Cancelled. */
    public Session.CancelType getCancelType() {
        return cancelType;
    }

    /** Lý do hủy PT đã chọn, có thể null; hiển thị nguyên văn. */
    public String getCancelReason() {
        return cancelReason;
    }

    /** Nhãn loại buổi do Service chuẩn bị. */
    public String getSubscriptionTypeLabel() {
        return subscriptionTypeLabel;
    }

    /** Nhận nhãn loại buổi; không xử lý nghiệp vụ trong setter. */
    public void setSubscriptionTypeLabel(String subscriptionTypeLabel) {
        this.subscriptionTypeLabel = subscriptionTypeLabel;
    }

    /** JSP in nhãn trạng thái trong badge. */
    public String getStatusLabel() {
        return statusLabel;
    }

    /** Nhận nhãn trạng thái từ Service. */
    public void setStatusLabel(String statusLabel) {
        this.statusLabel = statusLabel;
    }

    /** Class badge chung để JSP chọn màu, giống PT Dashboard. */
    public String getStatusBadgeClass() {
        return statusBadgeClass;
    }

    /** Nhận class badge từ Service. */
    public void setStatusBadgeClass(String statusBadgeClass) {
        this.statusBadgeClass = statusBadgeClass;
    }

    /** Giải thích cho khách, null nếu không cần. */
    public String getExplanation() {
        return explanation;
    }

    /** Nhận nội dung giải thích do Service chuẩn bị. */
    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    /** Hint cho nhãn Đã check-in, null với trạng thái khác. */
    public String getHint() {
        return hint;
    }

    /** Nhận nội dung hint do Service chuẩn bị. */
    public void setHint(String hint) {
        this.hint = hint;
    }

}
