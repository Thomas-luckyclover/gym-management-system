package com.gms.dto;

import com.gms.dao.CustomerDAO.ActiveSubscriptionSummary;
import com.gms.dao.CustomerDAO.ScheduledSessionSummary;
import com.gms.entity.CustomerDeletionRequest;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * DTO dữ liệu chi tiết cho màn hình Quản lý duyệt yêu cầu xóa hồ sơ (Màn #32, UC-17).
 * Cung cấp thông tin khách hàng, lý do xóa, danh sách gói tập còn hạn và kiểm tra tự duyệt.
 */
public class CustomerDeletionReviewView {

    // DAO: Bản ghi yêu cầu xóa từ CustomerDeletionRequests.
    private final CustomerDeletionRequest deletionRequest;

    // DAO: Customers.CustomerId, mã khách hàng cần xóa.
    private final int customerId;

    // DAO: Customers.CustomerCode, mã hiển thị khách hàng.
    private final String customerCode;

    // DAO: Persons.FullName, họ tên khách hàng.
    private final String customerFullName;

    // DAO: Persons.Phone, số điện thoại khách hàng.
    private final String customerPhone;

    // DAO: Persons.Email, email khách hàng.
    private final String customerEmail;

    // DAO: Customers.CustomerType (Self | Dependent).
    private final String customerType;

    // DAO: Customers.JoinedAt, thời điểm tham gia.
    private final LocalDateTime customerJoinedAt;

    // DAO: Persons.FullName của Lễ tân gửi yêu cầu.
    private final String requesterName;

    // DAO: Persons.FullName của Quản lý đã duyệt/từ chối (nếu đã xử lý).
    private final String reviewerName;

    // Service: danh sách các gói tập đang chạy (Active/Frozen, EndDate >= hôm nay).
    private List<ActiveSubscriptionSummary> activePackages;

    // Service: danh sách các buổi PT đang lên lịch (Scheduled).
    private List<ScheduledSessionSummary> scheduledSessions;

    // DAO: true nếu khách hàng trùng PersonId với Quản lý đang đăng nhập (CBR-115).
    // Nếu true, Quản lý không được tự duyệt xóa hồ sơ của chính mình (báo MSG76).
    private final boolean ownProfile;

    // DTO: true nếu yêu cầu đang ở trạng thái Pending và không phải ownProfile.
    private final boolean canAct;

    public CustomerDeletionReviewView(
            CustomerDeletionRequest deletionRequest,
            int customerId,
            String customerCode,
            String customerFullName,
            String customerPhone,
            String customerEmail,
            String customerType,
            LocalDateTime customerJoinedAt,
            String requesterName,
            String reviewerName,
            List<ActiveSubscriptionSummary> activePackages,
            List<ScheduledSessionSummary> scheduledSessions,
            boolean ownProfile
    ) {
        this.deletionRequest = deletionRequest;
        this.customerId = customerId;
        this.customerCode = customerCode;
        this.customerFullName = customerFullName;
        this.customerPhone = customerPhone;
        this.customerEmail = customerEmail;
        this.customerType = customerType;
        this.customerJoinedAt = customerJoinedAt;
        this.requesterName = requesterName;
        this.reviewerName = reviewerName;
        this.activePackages = activePackages != null ? Collections.unmodifiableList(activePackages) : Collections.emptyList();
        this.scheduledSessions = scheduledSessions != null ? Collections.unmodifiableList(scheduledSessions) : Collections.emptyList();
        this.ownProfile = ownProfile;
        this.canAct = deletionRequest != null && deletionRequest.isPending() && !ownProfile;
    }

    public CustomerDeletionRequest getDeletionRequest() {
        return deletionRequest;
    }

    public int getCustomerId() {
        return customerId;
    }

    public String getCustomerCode() {
        return customerCode;
    }

    public String getCustomerFullName() {
        return customerFullName;
    }

    public String getCustomerPhone() {
        return customerPhone;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public String getCustomerType() {
        return customerType;
    }

    public LocalDateTime getCustomerJoinedAt() {
        return customerJoinedAt;
    }

    public String getRequesterName() {
        return requesterName;
    }

    public String getReviewerName() {
        return reviewerName;
    }

    public List<ActiveSubscriptionSummary> getActivePackages() {
        return activePackages;
    }

    public List<ScheduledSessionSummary> getScheduledSessions() {
        return scheduledSessions;
    }

    public boolean isOwnProfile() {
        return ownProfile;
    }

    public boolean isCanAct() {
        return canAct;
    }


    public boolean hasActiveAssets() {
        return !activePackages.isEmpty() || !scheduledSessions.isEmpty();
    }

    /** Chỉ Service tải phần gói/buổi khi mở màn duyệt, danh sách không tải phần này. */
    public void setActivePackages(List<ActiveSubscriptionSummary> activePackages) {
        this.activePackages = List.copyOf(activePackages);
    }

    public void setScheduledSessions(List<ScheduledSessionSummary> scheduledSessions) {
        this.scheduledSessions = List.copyOf(scheduledSessions);
    }

    public String getCustomerTypeLabel() {
        if ("Dependent".equals(customerType)) {
            return "Phụ thuộc";
        }
        return "Cá nhân";
    }
}
