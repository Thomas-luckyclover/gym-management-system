package com.gms.dto;

import com.gms.util.DateTimeUtil;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
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
    private final String fullName;
    private final String avatar;
    private final String customerCode;
    private final String customerStatus;

    private final boolean canUseGym;

    /** Danh sách gói tập/PT đang có hiệu lực hoặc chưa bắt đầu. */
    private final List<SubscriptionRow> subscriptions;

    // ===== Buổi PT sắp tới ([CHỐT-D3]: 5 buổi Scheduled gần nhất từ bây giờ) =====

    private final List<UpcomingSessionRow> upcomingSessions;

    // ===== Banners cảnh báo ([CHỐT-D1]) =====

    private final List<BannerItem> banners;

    // ===== Chuyên cần (mở rộng ngoài SRS, tính trong CustomerDashboardService) =====

    private final AttendanceView attendance;

    // ===== Thông báo cho chuông (Notifications + NotificationRecipients) =====

    private final List<NotificationRow> notifications;
    private final int unreadNotificationCount;

    // ===== Constructor =====

    public CustomerDashboardView(
            String fullName,
            String avatar,
            String customerCode,
            String customerStatus,
            boolean canUseGym,
            List<SubscriptionRow> subscriptions,
            List<UpcomingSessionRow> upcomingSessions,
            List<BannerItem> banners,
            AttendanceView attendance,
            List<NotificationRow> notifications,
            int unreadNotificationCount) {

        this.fullName = fullName;
        this.avatar = avatar;
        this.customerCode = customerCode;
        this.customerStatus = customerStatus;
        this.canUseGym = canUseGym;
        this.subscriptions = subscriptions;
        this.upcomingSessions = upcomingSessions;
        this.banners = banners;
        this.attendance = attendance;
        this.notifications = notifications;
        this.unreadNotificationCount = unreadNotificationCount;
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
    public AttendanceView getAttendance() { return attendance; }
    public List<NotificationRow> getNotifications() { return notifications; }
    public int getUnreadNotificationCount() { return unreadNotificationCount; }

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
     * Gói tập nối tiếp (DBR-52): Membership/DayPass có ngày bắt đầu sau hôm nay.
     * @return gói nối tiếp sớm nhất, hoặc null nếu chưa gia hạn.
     */
    public SubscriptionRow getNextMembership() {
        if (subscriptions == null) return null;
        for (SubscriptionRow sub : subscriptions) {
            if (!sub.isPtPackage() && sub.isPending()) {
                return sub;
            }
        }
        return null;
    }

    /** Gói PT đang dùng đầu tiên (Active), hoặc null. */
    public SubscriptionRow getActivePtPackage() {
        if (subscriptions == null) return null;
        for (SubscriptionRow sub : subscriptions) {
            if (sub.isPtPackage() && "Active".equals(sub.getStatus())) {
                return sub;
            }
        }
        return null;
    }

    /** Buổi PT sắp tới gần nhất, hoặc null. */
    public UpcomingSessionRow getNextSession() {
        return upcomingSessions == null || upcomingSessions.isEmpty() ? null : upcomingSessions.get(0);
    }

    /** Số hiện trên chuông: cảnh báo đang có + thông báo chưa đọc. */
    public int getBellCount() {
        int banner = banners == null ? 0 : banners.size();
        return banner + unreadNotificationCount;
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

        private final int subscriptionId;
        private final String subscriptionType;
        private final String packageName;
        private final LocalDate startDate;
        private final LocalDate endDate;
        private final String status;

        // --- Chỉ có với gói PT (DBR-53, [CHỐT-D2]) ---
        private final Integer totalSessions;
        private final Integer remainingSessions;
        private final Integer usedSessions;

        // --- Bảo lưu nếu Frozen (DBR-51) ---
        private final LocalDate freezeStart;
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

        /** Tổng số ngày của gói (tính cả ngày đầu và ngày cuối, GBR-03). */
        public long getTotalDays() {
            if (startDate == null || endDate == null) return 0;
            return ChronoUnit.DAYS.between(startDate, endDate) + 1;
        }

        /**
         * Phần trăm thời hạn đã dùng, 0..100, cho thanh tiến độ.
         * Gói chưa bắt đầu trả về 0; gói đã quá hạn trả về 100.
         */
        public int getElapsedPercent() {
            long total = getTotalDays();
            if (total <= 0) return 0;
            long used = ChronoUnit.DAYS.between(startDate, DateTimeUtil.today()) + 1;
            long clamped = Math.max(0, Math.min(total, used));
            return (int) Math.round(clamped * 100.0 / total);
        }

        /** Phần trăm buổi PT đã tập (đã dùng / tổng), 0..100. */
        public int getUsedPercent() {
            if (totalSessions == null || totalSessions <= 0 || usedSessions == null) return 0;
            return (int) Math.min(100, Math.round(usedSessions * 100.0 / totalSessions));
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

        private final int sessionId;
        private final LocalDate slotDate;
        private final LocalTime startTime;
        private final LocalTime endTime;
        private final String ptName;

        /** Tên gói (VD: "Gói PT cá nhân 10 buổi"). */
        private final String packageName;
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

        /** Thứ trong tuần, viết hoa: "THỨ TƯ", "CHỦ NHẬT". */
        public String getWeekdayLabel() {
            if (slotDate == null) return "";
            switch (slotDate.getDayOfWeek()) {
                case MONDAY:    return "THỨ HAI";
                case TUESDAY:   return "THỨ BA";
                case WEDNESDAY: return "THỨ TƯ";
                case THURSDAY:  return "THỨ NĂM";
                case FRIDAY:    return "THỨ SÁU";
                case SATURDAY:  return "THỨ BẢY";
                default:        return "CHỦ NHẬT";
            }
        }

        /** Ngày trong tháng, VD 14. */
        public int getDayOfMonth() {
            return slotDate == null ? 0 : slotDate.getDayOfMonth();
        }

        /** "Hôm nay", "Ngày mai" hoặc "Còn N ngày". */
        public String getRelativeLabel() {
            if (slotDate == null) return "";
            long days = ChronoUnit.DAYS.between(DateTimeUtil.today(), slotDate);
            if (days <= 0) return "Hôm nay";
            if (days == 1) return "Ngày mai";
            return "Còn " + days + " ngày";
        }
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

    /**
     * Chuyên cần của tháng hiện tại (mở rộng ngoài SRS).
     * Mọi con số do CustomerDashboardService tính sẵn; JSP chỉ in ra.
     */
    public static class AttendanceView {
        private final int streakDays;
        private final int recordStreak;
        private final int daysToBeatRecord;
        private final int thisMonthCount;
        private final int lastMonthCount;
        private final boolean checkedInToday;
        /** Service đặt true khi chuỗi đủ dài để chào bằng màn "chuỗi tập"; Servlet chỉ thêm điều kiện "một lần mỗi lần đăng nhập". */
        private final boolean streakIntroEligible;
        private final String monthLabel;
        private final int leadingBlanks;
        private final List<CalendarCell> cells;

        public AttendanceView(int streakDays, int recordStreak, int daysToBeatRecord,
                int thisMonthCount, int lastMonthCount, boolean checkedInToday,
                boolean streakIntroEligible, String monthLabel, int leadingBlanks, List<CalendarCell> cells) {
            this.streakDays = streakDays;
            this.recordStreak = recordStreak;
            this.daysToBeatRecord = daysToBeatRecord;
            this.thisMonthCount = thisMonthCount;
            this.lastMonthCount = lastMonthCount;
            this.checkedInToday = checkedInToday;
            this.streakIntroEligible = streakIntroEligible;
            this.monthLabel = monthLabel;
            this.leadingBlanks = leadingBlanks;
            this.cells = cells;
        }

        public int getStreakDays() { return streakDays; }
        public int getRecordStreak() { return recordStreak; }
        public int getDaysToBeatRecord() { return daysToBeatRecord; }
        public int getThisMonthCount() { return thisMonthCount; }
        public int getLastMonthCount() { return lastMonthCount; }
        public boolean isCheckedInToday() { return checkedInToday; }
        public boolean isStreakIntroEligible() { return streakIntroEligible; }
        public String getMonthLabel() { return monthLabel; }
        public int getLeadingBlanks() { return leadingBlanks; }
        public List<CalendarCell> getCells() { return cells; }

        /** Đang giữ kỷ lục: chuỗi hiện tại bằng kỷ lục và > 0. */
        public boolean isRecordHolder() { return streakDays > 0 && daysToBeatRecord == 0; }
    }

    /**
     * Một ô ngày trong lịch tháng.
     * state: checked (đã check-in) | closed (phòng đóng cửa / bảo lưu) | missed | future.
     */
    public static class CalendarCell {
        private final int day;
        private final String state;
        private final boolean today;

        public CalendarCell(int day, String state, boolean today) {
            this.day = day;
            this.state = state;
            this.today = today;
        }

        public int getDay() { return day; }
        public String getState() { return state; }
        public boolean isToday() { return today; }
    }

    /**
     * Một thông báo trong chuông (bảng Notifications, GBR-34: có trạng thái đã đọc).
     */
    public static class NotificationRow {
        private final int notificationId;
        private final String title;
        private final String content;
        private final LocalDateTime sentAt;
        private final boolean read;

        public NotificationRow(int notificationId, String title, String content,
                LocalDateTime sentAt, boolean read) {
            this.notificationId = notificationId;
            this.title = title;
            this.content = content;
            this.sentAt = sentAt;
            this.read = read;
        }

        public int getNotificationId() { return notificationId; }
        public String getTitle() { return title; }
        public String getContent() { return content; }
        public LocalDateTime getSentAt() { return sentAt; }
        public boolean isRead() { return read; }
    }
}
