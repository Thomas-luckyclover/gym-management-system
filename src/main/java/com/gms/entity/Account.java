package com.gms.entity;

import java.time.LocalDateTime;

/**
 * Entity ánh xạ 1-1 với bảng Accounts trong database SQL Server.
 * Quản lý thông tin tài khoản đăng nhập của người dùng (Person).
 * 
 * <p>Quy tắc & Ràng buộc nghiệp vụ (DBR-02, CBR-17, CBR-18, BR-01, BR-02):</p>
 * <ul>
 *   <li>1 Account gắn đúng 1 Person (UQ_Accounts_PersonId).</li>
 *   <li>PasswordHash có thể NULL nếu tài khoản do Lễ tân tạo (khách chưa đặt mật khẩu).</li>
 *   <li>Status bao gồm: Active, Locked, Inactive.</li>
 *   <li>FailedLoginCount đếm số lần sai mật khẩu liên tiếp (tối đa 5 lần theo BR-01).</li>
 *   <li>LockedUntil lưu thời điểm khóa tạm thời do nhập sai mật khẩu.</li>
 *   <li>accountId, personId, createdAt là bất biến (final), không có setter.</li>
 * </ul>
 */
public class Account {

    /**
     * Trạng thái tài khoản (khớp giá trị VARCHAR(10) trong DB).
     */
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

        /**
         * Chuyển giá trị chuỗi từ DB thành enum Status.
         * @param dbValue giá trị trong DB (VD "Active")
         * @return Status tương ứng, hoặc null nếu không khớp
         */
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

    // Khóa chính và tham chiếu (bất biến)
    private final int accountId;
    private final int personId;

    // Dữ liệu tài khoản
    private String passwordHash;
    private Status status;
    private int failedLoginCount;
    private LocalDateTime lockedUntil;
    private LocalDateTime lastLoginAt;

    // Thời điểm khởi tạo (bất biến)
    private final LocalDateTime createdAt;

    /**
     * Constructor tạo đối tượng mới chưa có trong DB (accountId = 0, createdAt = LocalDateTime.now()).
     * @param personId ID của Person sở hữu tài khoản
     * @param passwordHash mật khẩu đã băm (hoặc null nếu chưa đặt)
     * @param status trạng thái tài khoản
     */
    public Account(int personId, String passwordHash, Status status) {
        this.accountId = 0;
        this.personId = personId;
        this.passwordHash = passwordHash;
        this.status = status != null ? status : Status.ACTIVE;
        this.failedLoginCount = 0;
        this.lockedUntil = null;
        this.lastLoginAt = null;
        this.createdAt = LocalDateTime.now();
    }

    /**
     * Constructor đầy đủ dùng cho DAO khi đọc từ database.
     * @param accountId khóa chính AccountId
     * @param personId khóa ngoại PersonId
     * @param passwordHash mật khẩu đã băm
     * @param status trạng thái tài khoản
     * @param failedLoginCount số lần đăng nhập sai
     * @param lockedUntil thời điểm hết hạn khóa tạm
     * @param lastLoginAt thời điểm đăng nhập gần nhất
     * @param createdAt thời điểm tạo tài khoản
     */
    public Account(int accountId, int personId, String passwordHash, Status status,
                   int failedLoginCount, LocalDateTime lockedUntil, LocalDateTime lastLoginAt,
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

    // ===== Getters & Setters =====

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
}
