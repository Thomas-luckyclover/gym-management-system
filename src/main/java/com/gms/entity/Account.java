package com.gms.entity;

import java.time.LocalDateTime;

/**
 * Entity ánh xạ 1-1 với bảng Accounts trong database SQL Server.
 * Quản lý thông tin tài khoản đăng nhập của người dùng (Person).
 * 
 * <p>
 * Quy tắc & Ràng buộc nghiệp vụ (DBR-02, CBR-17, CBR-18, BR-01, BR-02):
 * </p>
 * <ul>
 * <li>1 Account gắn đúng 1 Person (UQ_Accounts_PersonId).</li>
 * <li>PasswordHash có thể NULL nếu tài khoản do Lễ tân tạo (khách chưa đặt mật
 * khẩu).</li>
 * <li>Status bao gồm: Active, Locked, Inactive.</li>
 * <li>FailedLoginCount đếm số lần sai mật khẩu liên tiếp (tối đa 5 lần theo
 * BR-01).</li>
 * <li>LockedUntil lưu thời điểm khóa tạm thời do nhập sai mật khẩu.</li>
 * <li>accountId, personId, createdAt là bất biến (final), không có setter.</li>
 * </ul>
 */
public class Account {

    /**
     * Trạng thái tài khoản (khớp giá trị VARCHAR(10) trong DB).
     */
    public enum Status {
        // Trạng thái tài khoản: hoạt động
        ACTIVE("Active"),
        // Trạng thái tài khoản: bị khóa
        LOCKED("Locked"),
        // Trạng thái tài khoản: ngừng hoạt động
        INACTIVE("Inactive");

        // Giá trị tương ứng lưu trong DB
        private final String dbValue;

        // Gán giá trị DB cho enum
        Status(String dbValue) {
            this.dbValue = dbValue;
        }

        // Lấy giá trị chuỗi lưu trong DB
        public String getDbValue() {
            return dbValue;
        }

        /**
         * Chuyển chuỗi trong DB thành enum Status.
         * @param dbValue giá trị cột Status
         * @return Status khớp đúng từng ký tự, hoặc null nếu không khớp
         */
        public static Status fromDbValue(String dbValue) {
            for (Status status : values()) {
                if (status.dbValue.equals(dbValue)) {
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
     * Constructor tạo đối tượng mới chưa có trong DB (accountId = 0, createdAt =
     * LocalDateTime.now()).
     * 
     * @param personId     ID của Person sở hữu tài khoản
     * @param passwordHash mật khẩu đã băm (hoặc null nếu chưa đặt)
     * @param status       trạng thái tài khoản
     */
    // Constructor tạo mới account, accountId = 0, createdAt = thời điểm hiện tại
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
     * 
     * @param accountId        khóa chính AccountId
     * @param personId         khóa ngoại PersonId
     * @param passwordHash     mật khẩu đã băm
     * @param status           trạng thái tài khoản
     * @param failedLoginCount số lần đăng nhập sai
     * @param lockedUntil      thời điểm hết hạn khóa tạm
     * @param lastLoginAt      thời điểm đăng nhập gần nhất
     * @param createdAt        thời điểm tạo tài khoản
     */
    // Constructor đầy đủ dùng khi đọc từ DB
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

    // Lấy accountId (bất biến)
    public int getAccountId() {
        return accountId;
    }

    // Lấy personId (bất biến)
    public int getPersonId() {
        return personId;
    }

    // Lấy hash mật khẩu
    public String getPasswordHash() {
        return passwordHash;
    }

    // Gán hash mật khẩu
    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    // Lấy trạng thái tài khoản
    public Status getStatus() {
        return status;
    }

    // Gán trạng thái tài khoản
    public void setStatus(Status status) {
        this.status = status;
    }

    // Lấy số lần đăng nhập sai
    public int getFailedLoginCount() {
        return failedLoginCount;
    }

    // Gán số lần đăng nhập sai
    public void setFailedLoginCount(int failedLoginCount) {
        this.failedLoginCount = failedLoginCount;
    }

    // Lấy thời điểm hết hạn khóa tạm
    public LocalDateTime getLockedUntil() {
        return lockedUntil;
    }

    // Gán thời điểm hết hạn khóa tạm
    public void setLockedUntil(LocalDateTime lockedUntil) {
        this.lockedUntil = lockedUntil;
    }

    // Lấy thời điểm đăng nhập gần nhất
    public LocalDateTime getLastLoginAt() {
        return lastLoginAt;
    }

    // Gán thời điểm đăng nhập gần nhất
    public void setLastLoginAt(LocalDateTime lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }

    // Lấy thời điểm tạo tài khoản (bất biến)
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
