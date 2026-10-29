package com.gms.dto;

import com.gms.util.DateTimeUtil;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Dữ liệu hiển thị trên Customer Dashboard (UC-41, SRS 9.1.1), biến JSP "dashboard".
 *
 *   Ai tạo?   CustomerDashboardService.getDashboard (CustomerDashboardServlet gọi) — DAO đọc, Service gom 1 lần.
 *   Ai in?    customer/dashboard.jsp (+ _schedule-header.jspf hiện tên/mã khách ở topbar).
 *   Bảng đọc: Customers + Persons (hồ sơ), Subscriptions (+ tên gói, FreezeRecords khi bảo lưu),
 *             Sessions + TimeSlots + Persons (5 buổi PT sắp tới). Banner do Service tính từ các dữ liệu trên.
 *
 * Thiết kế bất biến (immutable): tất cả field lấy từ DB qua Service, không có setter.
 * DAO chỉ đọc trạng thái đang lưu; Dashboard không tự tính lại trạng thái (GBR-14).
 *
 * @author Nguyen Tuan Minh
 */
public class CustomerDashboardView {

    // ===== Thông tin hồ sơ (DAO: Customers + Persons) =====
    private final String fullName;         // Persons.FullName
    private final String avatar;           // Persons.Avatar; null thì JSP hiện initials
    private final String customerCode;     // Customers.CustomerCode
    private final String customerStatus;   // Customers.Status (New | Active | Frozen | Expired)

    // DAO: CustomerDashboardDAO.canUseGymToday — có Subscriptions Membership/DayPass Active phủ hôm nay, không bảo lưu.
    private final boolean canUseGym;

    // DAO: CustomerDashboardDAO — Subscriptions (+ tên gói); mỗi dòng là 1 gói đang dùng / chưa bắt đầu.
    private final List<SubscriptionRow> subscriptions;

    // ===== Buổi PT sắp tới (DAO: Sessions + TimeSlots + Persons tên PT) =====
    // [CHỐT-D3]: 5 buổi Scheduled gần nhất từ bây giờ.
    private final List<UpcomingSessionRow> upcomingSessions;

    // ===== Banners cảnh báo (Service tính — [CHỐT-D1], không đọc bảng riêng) =====
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

    // ===== Helper methods cho JSP =====

    /**
     * Lấy 2 ký tự đầu để hiện avatar khi không có ảnh.
     * Tên Việt: lấy chữ cái đầu của họ (phần đầu) và tên (phần cuối).
     * VD: "Nguyễn Minh An" → "NA".
     */
    public String getInitials() {
        if (fullName == null || fullName.isBlank()) return "?";
        String[] parts = fullName.trim().split("\\s+");
        if (parts.length == 1) {
            return parts[0].substring(0, 1).toUpperCase();
        }
        return (parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1)).toUpperCase();
    }

    /**
     * Trạng thái hồ sơ hiển thị tiếng Việt (GBR-01: trạng thái lưu tiếng Anh, hiển thị tiếng Việt).
     */
    public String getCustomerStatusLabel() {
        if (customerStatus == null) return "";
        switch (customerStatus) {
            case "New":     return "Mới";
            case "Active":  return "Đang hoạt động";
            case "Frozen":  return "Đang bảo lưu";
            case "Expired": return "Hết hạn";
            default:        return customerStatus;
        }
    }

    /**
     * Tìm gói Membership (hoặc DayPass) đang Active để hiện trên Hero card.
     * @return gói tập chính đang active, hoặc null nếu không có.
     */
    public SubscriptionRow getActiveMembership() {
        if (subscriptions == null) return null;
        for (SubscriptionRow sub : subscriptions) {
            if (!sub.isPtPackage() && "Active".equals(sub.getStatus())) {
                return sub;
            }
        }
        return null;
    }

    /**
     * Kiểm tra khách có banner nào loại "danger" (đỏ) không — dùng để tô viền hero card.
     */
    public boolean hasAnyDangerBanner() {
        if (banners == null) return false;
        for (BannerItem b : banners) {
            if ("danger".equals(b.getLevel())) return true;
        }
        return false;
    }


    public static class SubscriptionRow {

        // DAO đọc bảng Subscriptions (+ Packages.PackageName / Orders.PackageNameSnapshot cho tên gói).
        private final int subscriptionId;          // Subscriptions.SubscriptionId
        private final String subscriptionType;     // Subscriptions.SubscriptionType: Membership | DayPass | PTSingle | PTCouple | SingleSession
        private final String packageName;          // tên gói lúc mua
        private final LocalDate startDate;         // Subscriptions.StartDate
        private final LocalDate endDate;           // Subscriptions.EndDate (ngày cuối còn dùng, GBR-03)
        private final String status;               // Subscriptions.Status: Active | Frozen | Pending | ...

        // --- Chỉ có với gói PT (DBR-53, [CHỐT-D2]) ---
        private final Integer totalSessions;       // tổng số buổi của gói (chỉ gói PT)
        private final Integer remainingSessions;   // số buổi còn lại
        private final Integer usedSessions;        // số buổi đã qua

        // --- Bảo lưu nếu Frozen (DBR-51) ---
        private final LocalDate freezeStart;       // FreezeRecords.StartDate — ngày bắt đầu bảo lưu
        private final LocalDate freezeEnd;         // FreezeRecords.EndDate — ngày hết bảo lưu

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
            return startDate != null && startDate.isAfter(DateTimeUtil.today());
        }

        /**
         * Số ngày còn lại tính từ hôm nay đến ngày kết thúc (GBR-03: endDate là ngày cuối còn dùng).
         * Trả về 0 nếu đã hết hạn hoặc thông tin thiếu.
         */
        public long getDaysRemaining() {
            if (endDate == null) return 0;
            long days = ChronoUnit.DAYS.between(DateTimeUtil.today(), endDate);
            return Math.max(days, 0);
        }

        /**
         * Nhãn loại gói tiếng Việt (GBR-01).
         */
        public String getSubscriptionTypeLabel() {
            if (subscriptionType == null) return "";
            switch (subscriptionType) {
                case "Membership":    return "Gói tập";
                case "DayPass":       return "Vé ngày";
                case "PTSingle":      return "Gói PT cá nhân";
                case "PTCouple":      return "Gói PT đôi";
                case "SingleSession": return "Buổi lẻ";
                default:              return subscriptionType;
            }
        }

        /**
         * Nhãn trạng thái gói tiếng Việt (GBR-01).
         */
        public String getStatusLabel() {
            if (status == null) return "";
            switch (status) {
                case "Active":    return "Đang dùng";
                case "Frozen":    return "Đang bảo lưu";
                case "Pending":   return "Chưa bắt đầu";
                case "Expired":   return "Hết hạn";
                case "Cancelled": return "Đã hủy";
                case "Used":      return "Đã dùng";
                case "Completed": return "Hoàn thành";
                default:          return status;
            }
        }
    }

    /**
     * Một buổi PT sắp tới ([CHỐT-D3]).
     */
    public static class UpcomingSessionRow {

        // DAO đọc: Sessions + TimeSlots (ngày/giờ) + Persons (tên PT) + tên gói.
        private final int sessionId;               // Sessions.SessionId
        private final LocalDate slotDate;          // TimeSlots.SlotDate
        private final LocalTime startTime;         // TimeSlots.StartTime
        private final LocalTime endTime;           // TimeSlots.EndTime
        private final String ptName;               // Persons.FullName của PT dạy buổi này

        /** Tên gói (VD: "Gói PT cá nhân 10 buổi"). */
        private final String packageName;
        // Service tính: buổi này khách chưa có Membership/DayPass phủ ngày → tô nhãn "Chưa có gói tập".
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
        private final String level;
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
