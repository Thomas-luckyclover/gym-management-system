package com.gms.entity;

import java.time.LocalDateTime;

/**
 * Thông báo trong hệ thống.
 * Có thể do Manager gửi hoặc hệ thống tự tạo.
 */
public class Notification {

    private int notificationId;
    private Integer sentByEmployeeId;
    private String title;
    private String content;
    private Audience audience;
    private LocalDateTime scheduledAt;
    private LocalDateTime sentAt;
    private LocalDateTime createdAt;

    public Notification() {
    }

    public int getNotificationId() {
        return notificationId;
    }

    public void setNotificationId(int notificationId) {
        this.notificationId = notificationId;
    }

    public Integer getSentByEmployeeId() {
        return sentByEmployeeId;
    }

    public void setSentByEmployeeId(Integer sentByEmployeeId) {
        this.sentByEmployeeId = sentByEmployeeId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Audience getAudience() {
        return audience;
    }

    public void setAudience(Audience audience) {
        this.audience = audience;
    }

    public LocalDateTime getScheduledAt() {
        return scheduledAt;
    }

    public void setScheduledAt(LocalDateTime scheduledAt) {
        this.scheduledAt = scheduledAt;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public void setSentAt(LocalDateTime sentAt) {
        this.sentAt = sentAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isSystemGenerated() {
        return sentByEmployeeId == null;
    }

    public enum Audience {

        ALL_CUSTOMERS("AllCustomers"),
        ACTIVE_MEMBERS("ActiveMembers"),
        PTS("PTs"),
        RECEPTIONISTS("Receptionists"),
        ALL_STAFF("AllStaff"),
        INDIVIDUAL("Individual");

        private final String dbValue;

        Audience(String dbValue) {
            this.dbValue = dbValue;
        }

        public String getDbValue() {
            return dbValue;
        }

        public static Audience fromDbValue(String dbValue) {
            if (dbValue == null) {
                return null;
            }

            for (Audience audience : values()) {
                if (audience.dbValue.equalsIgnoreCase(dbValue)) {
                    return audience;
                }
            }

            return null;
        }
    }
}