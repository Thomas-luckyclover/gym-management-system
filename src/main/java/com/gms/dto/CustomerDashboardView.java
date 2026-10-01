package com.gms.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * Dữ liệu hiển thị trên Customer Dashboard (UC-41, SRS 9.1.1).
 *
 * Thiết kế bất biến (immutable): tất cả field lấy từ DB qua Service, không có setter.
 * DAO chỉ đọc trạng thái đang lưu; Dashboard không tự tính lại trạng thái (GBR-14).
 *
 * @author Nguyen Tuan Minh
 */
public class CustomerDashboardView {

    // ===== Thông tin hồ sơ =====

    /** Tên đầy đủ của khách. */
    private final String fullName;

    /** Avatar path; null = hiển thị chữ cái đầu. */
    private final String avatar;

    /** Mã hội viên (VD: KH-1001). */
    private final String customerCode;

    /** Trạng thái hồ sơ: New / Active / Frozen / Expired (DBR-34, DBR-35). */
    private final String customerStatus;

    // ===== Gói tập & quyền vào gym =====

    /**
     * Khách có thể vào phòng tập hôm nay không.
     * canUseGym = đang có Membership hoặc DayPass còn hiệu lực (DBR-47, DBR-48, CBR-58).
     * Gói PT không cho vào phòng tập.
     */
    private final boolean canUseGym;

    /** Danh sách gói tập/PT đang có hiệu lực hoặc chưa bắt đầu. */
    private final List<SubscriptionRow> subscriptions;

    // ===== Buổi PT sắp tới ([CHỐT-D3]: 5 buổi Scheduled gần nhất từ bây giờ) =====

    private final List<UpcomingSessionRow> upcomingSessions;

    // ===== Banners cảnh báo ([CHỐT-D1]) =====

    private final List<BannerItem> banners;

    // ===== Constructor =====

    public CustomerDashboardView(
            String fullName,
            String avatar,
            String customerCode,
            String customerStatus,
            boolean canUseGym,
            List<SubscriptionRow> subscriptions,
            List<UpcomingSessionRow> upcomingSessions,
            List<BannerItem> banners) {

        this.fullName = fullName;
        this.avatar = avatar;
        this.customerCode = customerCode;
        this.customerStatus = customerStatus;
        this.canUseGym = canUseGym;
        this.subscriptions = subscriptions;
        this.upcomingSessions = upcomingSessions;
        this.banners = banners;
    }

    // ===== Getters =====

    public String getFullName() { return fullName; }
    public String getAvatar() { return avatar; }
    public String getCustomerCode() { return customerCode; }
    public String getCustomerStatus() { return customerStatus; }
    public boolean isCanUseGym() { return canUseGym; }
    public List<SubscriptionRow> getSubscriptions() { return subscriptions; }
    public List<UpcomingSessionRow> getUpcomingSessions() { return upcomingSessions; }
    public List<BannerItem> getBanners() { return banners; }

    // =========================================================
    // Inner classes
    // =========================================================

    /**
     * Một dòng gói đang dùng / gói nối tiếp chưa bắt đầu (DBR-52).
     */
    public static class SubscriptionRow {

        private final int subscriptionId;

        /** Membership / PTSingle / PTCouple / DayPass / SingleSession */
        private final String subscriptionType;

        /** Tên gói (VD: "Gói 3 tháng"). */
        private final String packageName;

        /** Ngày bắt đầu hiệu lực. */
        private final LocalDate startDate;

        /** Ngày cuối còn dùng được (tính cả ngày đó — GBR-03). */
        private final LocalDate endDate;

        /** Active / Frozen / Expired / Pending (chưa bắt đầu) */
        private final String status;

        // --- Chỉ có với gói PT (DBR-53, [CHỐT-D2]) ---

        /** Tổng buổi được sinh lúc đơn Paid. Null nếu không phải gói PT. */
        private final Integer totalSessions;

        /** Số buổi Scheduled còn lại. Null nếu không phải gói PT. */
        private final Integer remainingSessions;

        /** Số buổi đã dùng (Completed + Missed). Null nếu không phải gói PT. */
        private final Integer usedSessions;

        // --- Bảo lưu nếu Frozen (DBR-51) ---

        /** Ngày bắt đầu bảo lưu; null nếu không phải Frozen. */
        private final LocalDate freezeStart;

        /** Ngày kết thúc bảo lưu (dự kiến); null nếu không phải Frozen. */
        private final LocalDate freezeEnd;

        public SubscriptionRow(
                int subscriptionId,
                String subscriptionType,
                String packageName,
                LocalDate startDate,
                LocalDate endDate,
                String status,
                Integer totalSessions,
                Integer remainingSessions,
                Integer usedSessions,
                LocalDate freezeStart,
                LocalDate freezeEnd) {

            this.subscriptionId = subscriptionId;
            this.subscriptionType = subscriptionType;
            this.packageName = packageName;
            this.startDate = startDate;
            this.endDate = endDate;
            this.status = status;
            this.totalSessions = totalSessions;
            this.remainingSessions = remainingSessions;
            this.usedSessions = usedSessions;
            this.freezeStart = freezeStart;
            this.freezeEnd = freezeEnd;
        }

        public int getSubscriptionId() { return subscriptionId; }
        public String getSubscriptionType() { return subscriptionType; }
        public String getPackageName() { return packageName; }
        public LocalDate getStartDate() { return startDate; }
        public LocalDate getEndDate() { return endDate; }
        public String getStatus() { return status; }
        public Integer getTotalSessions() { return totalSessions; }
        public Integer getRemainingSessions() { return remainingSessions; }
        public Integer getUsedSessions() { return usedSessions; }
        public LocalDate getFreezeStart() { return freezeStart; }
        public LocalDate getFreezeEnd() { return freezeEnd; }

        /** Gói PT (PTSingle / PTCouple / SingleSession). */
        public boolean isPtPackage() {
            return "PTSingle".equals(subscriptionType)
                    || "PTCouple".equals(subscriptionType)
                    || "SingleSession".equals(subscriptionType);
        }

        /** Gói chưa bắt đầu (nối tiếp, DBR-52): startDate > today. */
        public boolean isPending() {
            return startDate != null && startDate.isAfter(LocalDate.now());
        }
    }

    /**
     * Một buổi PT sắp tới ([CHỐT-D3]).
     */
    public static class UpcomingSessionRow {

        private final int sessionId;
        private final LocalDate slotDate;
        private final LocalTime startTime;
        private final LocalTime endTime;
        private final String ptName;

        /** Tên gói (VD: "Gói PT cá nhân 10 buổi"). */
        private final String packageName;

        /**
         * Buổi rơi vào ngày không có Membership còn hiệu lực (CBR-66).
         * Nếu true → hiện nhãn cảnh báo + nút gia hạn.
         */
        private final boolean missingMembership;

        public UpcomingSessionRow(
                int sessionId,
                LocalDate slotDate,
                LocalTime startTime,
                LocalTime endTime,
                String ptName,
                String packageName,
                boolean missingMembership) {

            this.sessionId = sessionId;
            this.slotDate = slotDate;
            this.startTime = startTime;
            this.endTime = endTime;
            this.ptName = ptName;
            this.packageName = packageName;
            this.missingMembership = missingMembership;
        }

        public int getSessionId() { return sessionId; }
        public LocalDate getSlotDate() { return slotDate; }
        public LocalTime getStartTime() { return startTime; }
        public LocalTime getEndTime() { return endTime; }
        public String getPtName() { return ptName; }
        public String getPackageName() { return packageName; }
        public boolean isMissingMembership() { return missingMembership; }
    }

    /**
     * Một banner cảnh báo ([CHỐT-D1]).
     * Màu: "danger" (đỏ) hoặc "warning" (vàng) theo GBR-28.
     */
    public static class BannerItem {

        /** "danger" hoặc "warning". */
        private final String level;

        /** Nội dung hiển thị trên banner. */
        private final String message;

        /** Loại banner: FROZEN / EXPIRED / EXPIRING / MISSING_MEMBERSHIP / PENDING_ORDER. */
        private final String type;

        public BannerItem(String level, String message, String type) {
            this.level = level;
            this.message = message;
            this.type = type;
        }

        public String getLevel() { return level; }
        public String getMessage() { return message; }
        public String getType() { return type; }
    }
}
