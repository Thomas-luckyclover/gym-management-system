package com.gms.dto;

import com.gms.entity.Package;
import java.time.LocalDate;

/**
 * Dữ liệu một thẻ trong khối Gói PT của bạn.
 * DAO điền dữ liệu gốc; Service bổ sung nhãn loại gói và lượt đổi còn lại.
 * Servlet sẽ truyền List<MyPtPackageView> bằng attribute ptPackages cho JSP.
 */
public class MyPtPackageView {

    // ===== 1. FIELD: DAO đọc từ database =====

    // Subscriptions.SubscriptionId: nhận diện gói khi lọc lịch, không in mã số này.
    private final int subscriptionId;

    // Orders.PackageNameSnapshot: tên lúc mua, JSP in tiêu đề thẻ.
    private final String packageName;

    // Subscriptions.SubscriptionType: Service chọn nhãn PT cá nhân hoặc PT đôi.
    private final Package.Type subscriptionType;

    // Subscriptions.PtEmployeeId → Employees → Persons.FullName: JSP in PT phụ trách gói.
    // PT thực tế của từng buổi sẽ được lấy riêng từ Sessions ở khối lịch.
    private final String ptName;

    // DAO chọn tên thành viên còn lại của gói đôi qua Customers → Persons.FullName.
    // Khách đứng tên thấy người đi cùng; khách đi cùng thấy người đứng tên. Gói cá nhân: null.
    private final String partnerName;

    // Subscriptions.StartDate: JSP in ngày bắt đầu bằng gms:formatDate.
    private final LocalDate startDate;

    // Subscriptions.EndDate: JSP in hạn gói; ngày này vẫn còn hiệu lực (GBR-03).
    private final LocalDate endDate;

    // Subscriptions.GeneratedSessionCount: tổng buổi thực sinh lúc Paid (BR-40, DBR-53).
    // JSP in tổng ban đầu, không dùng số ước tính của danh mục Packages.
    private final int generatedSessionCount;

    // COUNT Sessions cùng SubscriptionId còn Scheduled: JSP in “Buổi chưa chốt”.
    // Gồm cả buổi đã kết thúc nhưng chờ JOB-04, không chỉ buổi tương lai.
    private final int scheduledSessionCount;
    // COUNT Scheduled có SlotDate + StartTime sau thời điểm xem.
    // JSP in Buổi sắp tới; không gồm buổi đang diễn ra hoặc đang chờ JOB-04.
    private int upcomingSessionCount;

    // Subscriptions.MaxSwapsSnapshot: Service dùng giới hạn đã lưu lúc bán (GBR-04).
    private final int maxSwapsSnapshot;

    // Subscriptions.SwapsUsed: Service dùng số lượt đổi đã sử dụng của cả gói (BR-73).
    private final int swapsUsed;

    // ===== 2. FIELD: Service bổ sung để JSP hiển thị =====

    // Service đọc subscriptionType: JSP in “PT cá nhân” hoặc “PT đôi”.
    private String subscriptionTypeLabel;

    // Service tính maxSwapsSnapshot - swapsUsed: JSP in số lượt đổi còn lại.
    private int remainingSwaps;

    // ===== 3. CONSTRUCTOR: nhận một dòng gói mà DAO truy vấn =====

    /** Nhận dữ liệu một gói cá nhân/đôi; partnerName có thể null với gói cá nhân. */
    public MyPtPackageView(int subscriptionId, String packageName,
            Package.Type subscriptionType, String ptName, String partnerName,
            LocalDate startDate, LocalDate endDate, int generatedSessionCount,
            int scheduledSessionCount, int maxSwapsSnapshot, int swapsUsed) {
        // B1: nhận thông tin định danh, tên và người liên quan đến gói.
        this.subscriptionId = subscriptionId;
        this.packageName = packageName;
        this.subscriptionType = subscriptionType;
        this.ptName = ptName;
        this.partnerName = partnerName;

        // B2: nhận thời hạn và số buổi; DTO không tự tính hay sửa dữ liệu DB.
        this.startDate = startDate;
        this.endDate = endDate;
        this.generatedSessionCount = generatedSessionCount;
        this.scheduledSessionCount = scheduledSessionCount;

        // B3: giữ dữ liệu lượt đổi để Service tính phần còn lại.
        this.maxSwapsSnapshot = maxSwapsSnapshot;
        this.swapsUsed = swapsUsed;
    }

    // ===== 4. GETTER: dữ liệu gốc do DAO điền =====

    /** Mã gói dùng cho bộ lọc lịch. */
    public int getSubscriptionId() {
        return subscriptionId;
    }

    /** Tên gói đã lưu lúc mua, dùng làm tiêu đề thẻ. */
    public String getPackageName() {
        return packageName;
    }

    /** Loại gói để Service chọn nhãn hiển thị. */
    public Package.Type getSubscriptionType() {
        return subscriptionType;
    }

    /** Tên PT phụ trách chung của gói. */
    public String getPtName() {
        return ptName;
    }

    /** Tên người đi cùng trong đơn gói đôi; null thì JSP không hiện. */
    public String getPartnerName() {
        return partnerName;
    }

    /** Ngày bắt đầu gói, có thể là ngày tương lai. */
    public LocalDate getStartDate() {
        return startDate;
    }

    /** Ngày cuối gói còn hiệu lực. */
    public LocalDate getEndDate() {
        return endDate;
    }

    /** Tổng số buổi thực sinh khi thanh toán, không phải số còn lại. */
    public int getGeneratedSessionCount() {
        return generatedSessionCount;
    }

    /** Số buổi Scheduled chưa có kết quả cuối cùng. */
    public int getScheduledSessionCount() {
        return scheduledSessionCount;
    }

    /** Số buổi còn Scheduled và chưa bắt đầu trong từng lần mua gói. */
    public int getUpcomingSessionCount() {
        return upcomingSessionCount;
    }

    /** DAO gán số đếm từ lịch thực tế, không lấy tổng ban đầu trừ số buổi đã dùng. */
    public void setUpcomingSessionCount(int upcomingSessionCount) {
        this.upcomingSessionCount = upcomingSessionCount;
    }

    /** Giới hạn đổi của gói lúc bán, không lấy cấu hình mới thay thế. */
    public int getMaxSwapsSnapshot() {
        return maxSwapsSnapshot;
    }

    /** Số lượt đổi đã dùng của cả gói; gói đôi dùng chung. */
    public int getSwapsUsed() {
        return swapsUsed;
    }

    // ===== 5. GETTER / SETTER: Service điền trước khi trả DTO cho Servlet =====

    /** Nhãn loại gói để JSP in trực tiếp. */
    public String getSubscriptionTypeLabel() {
        return subscriptionTypeLabel;
    }

    /** Gán nhãn do Service chọn; không cập nhật database. */
    public void setSubscriptionTypeLabel(String subscriptionTypeLabel) {
        this.subscriptionTypeLabel = subscriptionTypeLabel;
    }

    /** Số lượt đổi còn lại do Service tính. */
    public int getRemainingSwaps() {
        return remainingSwaps;
    }

    /** Gán kết quả tính của Service; setter chỉ nhận giá trị. */
    public void setRemainingSwaps(int remainingSwaps) {
        this.remainingSwaps = remainingSwaps;
    }
}
