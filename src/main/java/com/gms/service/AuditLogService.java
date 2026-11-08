package com.gms.service;

import com.gms.constant.Messages;
import com.gms.constant.Role;
import com.gms.dao.AuditLogDAO;
import com.gms.dto.ServiceResult;
import com.gms.entity.AuditLog;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;

/**
 * Nghiệp vụ ghi nhật ký cho các thao tác nhạy cảm.
 */
public class AuditLogService {

    private final AuditLogDAO auditLogDAO;

    public AuditLogService() {
        auditLogDAO = new AuditLogDAO();
    }

    /**
     * Ghi một thao tác nhạy cảm vào bảng AuditLog (mở transaction riêng).
     * Các trường password / code / token sẽ tự được che, không lưu giá trị thật.
     */
    public ServiceResult<Void> log(
            Integer accountId,
            String action,
            String entityName,
            Integer entityId,
            String fieldChanged,
            String oldValue,
            String newValue,
            String reason
    ) {

        try {
            AuditLog auditLog =
                    createAuditLog(
                            accountId,
                            action,
                            entityName,
                            entityId,
                            fieldChanged,
                            oldValue,
                            newValue,
                            reason
                    );

            boolean isInserted =
                    auditLogDAO.insert(auditLog);

            if (!isInserted) {
                return ServiceResult.fail(
                        Messages.MSG04_SYSTEM_BUSY
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

    /**
     * Ghi một thao tác nhạy cảm kèm vai trò đang dùng (CBR-112).
     *
     * @param accountId tài khoản thực hiện
     * @param actingRole vai trò đang dùng khi thao tác
     * @param action mô tả thao tác dạng câu đọc được
     * @param entityName tên đối tượng bị tác động
     * @param entityId id đối tượng bị tác động
     * @param fieldChanged trường bị thay đổi
     * @param oldValue giá trị cũ
     * @param newValue giá trị mới
     * @param reason lý do nếu có
     * @return kết quả ghi nhật ký
     */
    public ServiceResult<Void> log(
            Integer accountId,
            Role actingRole,
            String action,
            String entityName,
            Integer entityId,
            String fieldChanged,
            String oldValue,
            String newValue,
            String reason
    ) {

        try {
            AuditLog auditLog =
                    createAuditLog(
                            accountId,
                            actingRole,
                            action,
                            entityName,
                            entityId,
                            fieldChanged,
                            oldValue,
                            newValue,
                            reason
                    );

            boolean isInserted =
                    auditLogDAO.insert(auditLog);

            if (!isInserted) {
                return ServiceResult.fail(
                        Messages.MSG04_SYSTEM_BUSY
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

    /**
     * Ghi AuditLog kèm vai trò đang dùng trong transaction đang có.
     *
     * @param conn connection của transaction hiện tại
     * @param accountId tài khoản thực hiện
     * @param actingRole vai trò đang dùng khi thao tác
     * @param action mô tả thao tác
     * @param entityName tên đối tượng
     * @param entityId id đối tượng
     * @param fieldChanged trường bị thay đổi
     * @param oldValue giá trị cũ
     * @param newValue giá trị mới
     * @param reason lý do nếu có
     * @throws SQLException khi không ghi được AuditLog
     */
    void logInTransaction(
            Connection conn,
            Integer accountId,
            Role actingRole,
            String action,
            String entityName,
            Integer entityId,
            String fieldChanged,
            String oldValue,
            String newValue,
            String reason
    ) throws SQLException {

        AuditLog auditLog =
                createAuditLog(
                        accountId,
                        actingRole,
                        action,
                        entityName,
                        entityId,
                        fieldChanged,
                        oldValue,
                        newValue,
                        reason
                );

        boolean isInserted =
                auditLogDAO.insert(
                        conn,
                        auditLog
                );

        if (!isInserted) {
            throw new SQLException(
                    "Không thể ghi AuditLog."
            );
        }
    }

    /**
     * Ghi AuditLog trong transaction đang có.
     *
     * @param conn connection của transaction hiện tại
     * @param accountId tài khoản thực hiện
     * @param action mô tả thao tác
     * @param entityName tên đối tượng
     * @param entityId id đối tượng
     * @param fieldChanged trường bị thay đổi
     * @param oldValue giá trị cũ
     * @param newValue giá trị mới
     * @param reason lý do nếu có
     * @throws SQLException khi không ghi được AuditLog
     */
    void logInTransaction(
            Connection conn,
            Integer accountId,
            String action,
            String entityName,
            Integer entityId,
            String fieldChanged,
            String oldValue,
            String newValue,
            String reason
    ) throws SQLException {

        AuditLog auditLog =
                createAuditLog(
                        accountId,
                        action,
                        entityName,
                        entityId,
                        fieldChanged,
                        oldValue,
                        newValue,
                        reason
                );

        boolean isInserted =
                auditLogDAO.insert(
                        conn,
                        auditLog
                );

        if (!isInserted) {
            throw new SQLException(
                    "Không thể ghi AuditLog."
            );
        }
    }

    private AuditLog createAuditLog(
            Integer accountId,
            String action,
            String entityName,
            Integer entityId,
            String fieldChanged,
            String oldValue,
            String newValue,
            String reason
    ) {

        return createAuditLog(
                accountId,
                null,
                action,
                entityName,
                entityId,
                fieldChanged,
                oldValue,
                newValue,
                reason
        );
    }

    /**
     * Tạo AuditLog entity kèm actingRole, ẩn giá trị nhạy cảm.
     */
    private AuditLog createAuditLog(
            Integer accountId,
            Role actingRole,
            String action,
            String entityName,
            Integer entityId,
            String fieldChanged,
            String oldValue,
            String newValue,
            String reason
    ) {

        String safeOldValue = oldValue;
        String safeNewValue = newValue;

        if (isSensitiveField(fieldChanged)) {
            safeOldValue = null;
            safeNewValue = null;
        }

        return new AuditLog(
                accountId,
                actingRole,
                action,
                entityName,
                entityId,
                fieldChanged,
                safeOldValue,
                safeNewValue,
                reason
        );
    }

    private boolean isSensitiveField(
            String fieldChanged
    ) {

        if (fieldChanged == null) {
            return false;
        }

        String normalizedField =
                fieldChanged.toLowerCase(
                        Locale.ROOT
                );

        return normalizedField.contains("password")
                || normalizedField.contains("code")
                || normalizedField.contains("token");
    }
}