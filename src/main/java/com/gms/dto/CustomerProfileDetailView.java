package com.gms.dto;

import com.gms.dao.CustomerDAO.ActiveSubscriptionSummary;
import com.gms.dao.CustomerDAO.ScheduledSessionSummary;
import com.gms.entity.CustomerDeletionRequest;
import com.gms.entity.Customer;
import com.gms.entity.Person;
import java.util.List;

/**
 * CustomerDeletionService gom dữ liệu cho hai trang chi tiết BF-07.
 * profile dùng lại dòng hồ sơ; hai danh sách chỉ được tải khi mở chi tiết.
 */
public class CustomerProfileDetailView {

    // Service ghép Customers + Persons; JSP hiển thị danh tính và trạng thái.
    private final CustomerProfileRowView profile;

    // DAO đọc gói còn hạn, kể cả phần tham gia gói đôi; dùng để cảnh báo trước khi xóa.
    private final List<ActiveSubscriptionSummary> activePackages;

    // DAO đọc buổi Scheduled; JSP thu gọn trong mục có thể mở rộng.
    private final List<ScheduledSessionSummary> scheduledSessions;

    // DAO đọc yêu cầu Pending, nếu có; khóa gửi trùng và cho quản lý mở màn duyệt.
    private final CustomerDeletionRequest pendingRequest;

    // Service đọc Persons/Customers; trang chi tiết và form sửa dùng ngày sinh, ảnh, giám hộ.
    private final Person person;
    private final Customer customer;

    public CustomerProfileDetailView(CustomerProfileRowView profile,
            List<ActiveSubscriptionSummary> activePackages,
            List<ScheduledSessionSummary> scheduledSessions,
            CustomerDeletionRequest pendingRequest) {
        this(profile, activePackages, scheduledSessions, pendingRequest, null, null);
    }

    public CustomerProfileDetailView(CustomerProfileRowView profile,
            List<ActiveSubscriptionSummary> activePackages,
            List<ScheduledSessionSummary> scheduledSessions, CustomerDeletionRequest pendingRequest,
            Person person, Customer customer) {
        this.person = person;
        this.customer = customer;
        this.profile = profile;
        this.activePackages = activePackages;
        this.scheduledSessions = scheduledSessions;
        this.pendingRequest = pendingRequest;
    }

    /** Thông tin cá nhân để hiện ngày sinh và điền form sửa. */
    public Person getPerson() {
        return person;
    }

    /** Hồ sơ để hiện ảnh check-in và người giám hộ. */
    public Customer getCustomer() {
        return customer;
    }

    public CustomerProfileRowView getProfile() {
        return profile;
    }

    public List<ActiveSubscriptionSummary> getActivePackages() {
        return activePackages;
    }

    public List<ScheduledSessionSummary> getScheduledSessions() {
        return scheduledSessions;
    }

    public CustomerDeletionRequest getPendingRequest() {
        return pendingRequest;
    }

    public boolean isHasActiveAssets() {
        return !activePackages.isEmpty() || !scheduledSessions.isEmpty();
    }
}
