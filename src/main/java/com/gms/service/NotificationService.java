package com.gms.service;

import com.gms.constant.Messages;
import com.gms.constant.Role;
import com.gms.dao.NotificationDAO;
import com.gms.dto.ServiceResult;
import com.gms.entity.Notification;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Nghiệp vụ gửi và đọc thông báo trong ứng dụng.
 */
public class NotificationService {

    private static final int BELL_LIMIT = 10;

    /**
     * Chỉ notification hệ thống có title bắt đầu bằng chuỗi này
     * mới được suy ra đường dẫn BF-06.
     */
    private static final String MAINTENANCE_TITLE_PREFIX =
            "Bảo trì ·";

    private static final Pattern
            MAINTENANCE_CODE_PATTERN =
            Pattern.compile(
                    "\\bBT-\\d{4}\\b",
                    Pattern.CASE_INSENSITIVE
            );

    private final NotificationDAO notificationDAO;

    public NotificationService() {
        notificationDAO =
                new NotificationDAO();
    }

    // ===== Gửi notification từ hệ thống =====

    public void notifyRoleInTransaction(
            Connection connection,
            Role role,
            String title,
            String content
    ) throws SQLException {

        List<Integer> accountIds =
                notificationDAO
                        .findActiveAccountIdsByRole(
                                connection,
                                role
                        );

        if (accountIds.isEmpty()) {
            return;
        }

        int notificationId =
                notificationDAO
                        .insertSystemNotification(
                                connection,
                                title,
                                content
                        );

        for (Integer accountId : accountIds) {

            notificationDAO.insertRecipient(
                    connection,
                    notificationId,
                    accountId
            );
        }
    }

    public void notifyEmployeeInTransaction(
            Connection connection,
            int employeeId,
            String title,
            String content
    ) throws SQLException {

        Integer accountId =
                notificationDAO
                        .findActiveAccountIdByEmployeeId(
                                connection,
                                employeeId
                        );

        if (accountId == null) {
            return;
        }

        int notificationId =
                notificationDAO
                        .insertSystemNotification(
                                connection,
                                title,
                                content
                        );

        notificationDAO.insertRecipient(
                connection,
                notificationId,
                accountId
        );
    }

    // ===== Dữ liệu chuông =====

    public ServiceResult<Map<String, Object>>
    getBellData(
            int accountId,
            Role currentRole
    ) {

        try {
            int unreadCount =
                    notificationDAO.countUnread(
                            accountId
                    );

            List<NotificationDAO.NotificationBellRow>
                    rows =
                    notificationDAO
                            .findLatestForAccount(
                                    accountId,
                                    BELL_LIMIT
                            );

            List<BellItem> items =
                    new ArrayList<>();

            for (NotificationDAO.NotificationBellRow
                    row : rows) {

                Notification notification =
                        row.getNotification();

                items.add(
                        new BellItem(
                                notification
                                        .getNotificationId(),
                                notification
                                        .getTitle(),
                                notification
                                        .getContent(),
                                notification
                                        .getSentAt(),
                                row.isRead(),
                                notification
                                        .isSystemGenerated(),
                                buildActionUrl(
                                        notification,
                                        currentRole
                                )
                        )
                );
            }

            Map<String, Object> data =
                    new LinkedHashMap<>();

            data.put(
                    "unreadCount",
                    unreadCount
            );

            data.put(
                    "items",
                    items
            );

            return ServiceResult.ok(data);

        } catch (SQLException e) {
            e.printStackTrace();

            return ServiceResult.fail(
                    Messages.MSG04_SYSTEM_BUSY
            );
        }
    }

    // ===== Đánh dấu đã đọc =====

    public ServiceResult<Void> markRead(
            int accountId,
            int notificationId
    ) {

        if (notificationId <= 0) {
            return ServiceResult.fail(
                    String.format(
                            Messages.MSG08_NOT_FOUND,
                            "thông báo"
                    )
            );
        }

        try {
            boolean updated =
                    notificationDAO.markRead(
                            accountId,
                            notificationId
                    );

            if (!updated) {
                return ServiceResult.fail(
                        String.format(
                                Messages.MSG08_NOT_FOUND,
                                "thông báo"
                        )
                );
            }

            return ServiceResult.ok(null);

        } catch (SQLException e) {
            e.printStackTrace();

            return ServiceResult.fail(
                    Messages.MSG04_SYSTEM_BUSY
            );
        }
    }

    public ServiceResult<Void> markAllRead(
            int accountId
    ) {

        try {
            notificationDAO.markAllRead(
                    accountId
            );

            return ServiceResult.ok(null);

        } catch (SQLException e) {
            e.printStackTrace();

            return ServiceResult.fail(
                    Messages.MSG04_SYSTEM_BUSY
            );
        }
    }

    // ===== Điều hướng notification =====

    private String buildActionUrl(
            Notification notification,
            Role currentRole
    ) {

        /*
         * Chỉ tự suy ra URL cho notification bảo trì
         * do HỆ THỐNG tạo.
         *
         * Manager tự gửi một notification có chữ
         * "Bảo trì" cũng không bị điều hướng nhầm.
         */
        if (notification == null
                || currentRole == null
                || !notification.isSystemGenerated()
                || notification.getTitle() == null
                || !notification.getTitle()
                .startsWith(
                        MAINTENANCE_TITLE_PREFIX
                )) {

            return null;
        }

        String requestCode =
                extractMaintenanceRequestCode(
                        notification.getContent()
                );

        if (requestCode == null) {
            return null;
        }

        if (currentRole == Role.MANAGER) {
            return "/manager/maintenance-requests"
                    + "?keyword="
                    + requestCode;
        }

        if (currentRole
                == Role.RECEPTIONIST) {

            return "/receptionist/maintenance-requests"
                    + "?keyword="
                    + requestCode;
        }

        return null;
    }

    private String extractMaintenanceRequestCode(
            String content
    ) {

        if (content == null) {
            return null;
        }

        Matcher matcher =
                MAINTENANCE_CODE_PATTERN
                        .matcher(content);

        if (!matcher.find()) {
            return null;
        }

        return matcher.group()
                .toUpperCase();
    }

    // ===== Dữ liệu trả cho JS =====

    public static class BellItem {

        private final int notificationId;
        private final String title;
        private final String content;
        private final LocalDateTime sentAt;
        private final boolean read;
        private final boolean systemGenerated;
        private final String actionUrl;

        public BellItem(
                int notificationId,
                String title,
                String content,
                LocalDateTime sentAt,
                boolean read,
                boolean systemGenerated,
                String actionUrl
        ) {

            this.notificationId =
                    notificationId;

            this.title = title;
            this.content = content;
            this.sentAt = sentAt;
            this.read = read;

            this.systemGenerated =
                    systemGenerated;

            this.actionUrl =
                    actionUrl;
        }

        public int getNotificationId() {
            return notificationId;
        }

        public String getTitle() {
            return title;
        }

        public String getContent() {
            return content;
        }

        public LocalDateTime getSentAt() {
            return sentAt;
        }

        public boolean isRead() {
            return read;
        }

        public boolean isSystemGenerated() {
            return systemGenerated;
        }

        public String getActionUrl() {
            return actionUrl;
        }
    }
}