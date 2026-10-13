package com.gms.dto;

import com.gms.entity.Package;
import java.time.LocalDate;
import java.time.LocalTime;

/** Một buổi Scheduled do DAO tạo; Service bổ sung nhãn thời gian và cảnh báo cho khách. */
public class MyPtUpcomingSessionView {

    // Sessions.SessionId: nhận diện buổi để nối thao tác sau này, không in mã ra màn hình.
    private final int sessionId;
    // Subscriptions.SubscriptionId: nhận diện gói cho bộ lọc sau này, không in mã.
    private final int subscriptionId;
    // Subscriptions.CustomerId: Service phân biệt người xem đứng tên hay đi cùng gói đôi.
    private final int holderCustomerId;
    // Orders.PackageNameSnapshot: JSP in tên gói lúc mua.
    private final String packageName;
    // Subscriptions.SubscriptionType: Service chọn nhãn cá nhân/đôi/buổi lẻ.
    private final Package.Type subscriptionType;
    // Sessions.PtEmployeeId → Employees → Persons.FullName: JSP in PT thực tế dạy buổi.
    private final String ptName;
    // Orders.PartnerCustomerId và khách đứng tên: JSP in tên thành viên còn lại, gói cá nhân null.
    private final String partnerName;
    // TimeSlots.SlotDate/StartTime/EndTime: Service xét thời gian, JSP in ngày và khung giờ.
    private final LocalDate slotDate;
    private final LocalTime startTime;
    private final LocalTime endTime;
    // DAO dùng EXISTS theo ngày tập: Service xét Membership/DayPass/bảo lưu như JOB-04.
    // Các cờ không in trực tiếp; Service chuyển thành cảnh báo dễ hiểu.
    private final boolean holderHasMembership;
    private final boolean holderHasDayPass;
    private final boolean holderFrozen;
    private final boolean partnerHasMembership;
    private final boolean partnerHasDayPass;
    private final boolean partnerFrozen;

    // Service chọn từ subscriptionType: JSP in loại buổi.
    private String subscriptionTypeLabel;
    // Service so ngày với hôm nay: JSP làm nổi ngày hôm nay, không in boolean.
    private boolean today;
    // Service so giờ bắt đầu với thời điểm xét: JSP chỉ hiện nhãn khi buổi đang diễn ra.
    private boolean inProgress;
    // Service xét quyền vào gym từng thành viên: JSP in cảnh báo; null thì không hiện.
    private String warning;

    /** Nhận dữ liệu một buổi từ SQL; không tính quyền vào gym hoặc thay đổi trạng thái. */
    public MyPtUpcomingSessionView(int sessionId, int subscriptionId, int holderCustomerId,
            String packageName, Package.Type subscriptionType, String ptName, String partnerName,
            LocalDate slotDate, LocalTime startTime, LocalTime endTime,
            boolean holderHasMembership, boolean holderHasDayPass, boolean holderFrozen,
            boolean partnerHasMembership, boolean partnerHasDayPass, boolean partnerFrozen) {
        // B1: nhận định danh, gói và người liên quan đến buổi.
        this.sessionId = sessionId;
        this.subscriptionId = subscriptionId;
        this.holderCustomerId = holderCustomerId;
        this.packageName = packageName;
        this.subscriptionType = subscriptionType;
        this.ptName = ptName;
        this.partnerName = partnerName;
        // B2: nhận lịch thực tế, không dùng giờ cố định hoặc PT mặc định của gói.
        this.slotDate = slotDate;
        this.startTime = startTime;
        this.endTime = endTime;
        // B3: nhận dữ kiện quyền vào gym để Service xét, không tự kết luận Vắng.
        this.holderHasMembership = holderHasMembership;
        this.holderHasDayPass = holderHasDayPass;
        this.holderFrozen = holderFrozen;
        this.partnerHasMembership = partnerHasMembership;
        this.partnerHasDayPass = partnerHasDayPass;
        this.partnerFrozen = partnerFrozen;
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

    /** Thành viên còn lại của gói đôi. */
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

    /** Membership người đứng tên bao phủ ngày tập. */
    public boolean isHolderHasMembership() {
        return holderHasMembership;
    }

    /** Người đứng tên có DayPass cho ngày tập. */
    public boolean isHolderHasDayPass() {
        return holderHasDayPass;
    }

    /** Người đứng tên nằm trong kỳ bảo lưu tại ngày tập. */
    public boolean isHolderFrozen() {
        return holderFrozen;
    }

    /** Membership người đi cùng bao phủ ngày tập. */
    public boolean isPartnerHasMembership() {
        return partnerHasMembership;
    }

    /** Người đi cùng có DayPass cho ngày tập. */
    public boolean isPartnerHasDayPass() {
        return partnerHasDayPass;
    }

    /** Người đi cùng nằm trong kỳ bảo lưu tại ngày tập. */
    public boolean isPartnerFrozen() {
        return partnerFrozen;
    }

    /** Nhãn loại buổi do Service chuẩn bị. */
    public String getSubscriptionTypeLabel() {
        return subscriptionTypeLabel;
    }

    /** Nhận nhãn loại buổi; không xử lý nghiệp vụ trong setter. */
    public void setSubscriptionTypeLabel(String subscriptionTypeLabel) {
        this.subscriptionTypeLabel = subscriptionTypeLabel;
    }

    /** JSP đọc session.today để nhấn ngày hôm nay. */
    public boolean isToday() {
        return today;
    }

    /** Nhận kết quả so ngày từ Service. */
    public void setToday(boolean today) {
        this.today = today;
    }

    /** JSP đọc session.inProgress để hiện Đang diễn ra. */
    public boolean isInProgress() {
        return inProgress;
    }

    /** Nhận kết quả so thời gian từ Service. */
    public void setInProgress(boolean inProgress) {
        this.inProgress = inProgress;
    }

    /** Cảnh báo cho người xem, null nếu không cần cảnh báo. */
    public String getWarning() {
        return warning;
    }

    /** Nhận nội dung cảnh báo do Service chuẩn bị. */
    public void setWarning(String warning) {
        this.warning = warning;
    }

}
