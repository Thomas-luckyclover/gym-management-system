package com.gms.entity;

import java.time.LocalDateTime;

/**
 * Thông tin đăng nhập của một Person, ánh xạ bảng Accounts.
 */
public class Account {

    private final int accountId;
    private final int personId;
    private String passwordHash;
    private Status status;
    private int failedLoginCount;
    private LocalDateTime lockedUntil;
    private LocalDateTime lastLoginAt;
    private final LocalDateTime createdAt;

    public Account(int accountId, int personId, String passwordHash,
            Status status, int failedLoginCount,
            LocalDateTime lockedUntil, LocalDateTime lastLoginAt,
            LocalDateTime createdAt) {

        this.accountId = accountId;
        this.personId = personId;
        this.passwordHash = passwordHash;
        this.status = status;
        this.failedLoginCount = failedLoginCount;
        this.lockedUntil = lockedUntil;
        this.lastLoginAt = lastLoginAt;
        this.createdAt = createdAt;
    }

    public int getAccountId() {
        return accountId;
    }

    public int getPersonId() {
        return personId;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public int getFailedLoginCount() {
        return failedLoginCount;
    }

    public void setFailedLoginCount(int failedLoginCount) {
        this.failedLoginCount = failedLoginCount;
    }

    public LocalDateTime getLockedUntil() {
        return lockedUntil;
    }

    public void setLockedUntil(LocalDateTime lockedUntil) {
        this.lockedUntil = lockedUntil;
    }

    public LocalDateTime getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(LocalDateTime lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public enum Status {

        ACTIVE("Active"),
        LOCKED("Locked"),
        INACTIVE("Inactive");

        private final String dbValue;

        Status(String dbValue) {
            this.dbValue = dbValue;
        }

        public String getDbValue() {
            return dbValue;
        }

        public static Status fromDbValue(String dbValue) {
            if (dbValue == null) {
                return null;
            }

            for (Status status : values()) {
                if (status.dbValue.equalsIgnoreCase(dbValue)) {
                    return status;
                }
            }

            return null;
        }
    }
}