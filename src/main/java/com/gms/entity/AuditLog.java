package com.gms.entity;

import com.gms.constant.Role;
import java.time.LocalDateTime;

/**
 * Nhật ký thao tác nhạy cảm, ánh xạ bảng AuditLogs.
 */
public class AuditLog {

    private final int auditLogId;
    private final Integer accountId;
    private final Role actingRole;
    private final String action;
    private final String entityName;
    private final Integer entityId;
    private final String fieldChanged;
    private final String oldValue;
    private final String newValue;
    private final String reason;
    private final LocalDateTime createdAt;

    public AuditLog(
            int auditLogId,
            Integer accountId,
            Role actingRole,
            String action,
            String entityName,
            Integer entityId,
            String fieldChanged,
            String oldValue,
            String newValue,
            String reason,
            LocalDateTime createdAt
    ) {
        this.auditLogId = auditLogId;
        this.accountId = accountId;
        this.actingRole = actingRole;
        this.action = action;
        this.entityName = entityName;
        this.entityId = entityId;
        this.fieldChanged = fieldChanged;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.reason = reason;
        this.createdAt = createdAt;
    }

    public AuditLog(
            int auditLogId,
            Integer accountId,
            String action,
            String entityName,
            Integer entityId,
            String fieldChanged,
            String oldValue,
            String newValue,
            String reason,
            LocalDateTime createdAt
    ) {
        this(
                auditLogId,
                accountId,
                null,
                action,
                entityName,
                entityId,
                fieldChanged,
                oldValue,
                newValue,
                reason,
                createdAt
        );
    }

    public AuditLog(
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
        this(
                0,
                accountId,
                actingRole,
                action,
                entityName,
                entityId,
                fieldChanged,
                oldValue,
                newValue,
                reason,
                null
        );
    }

    public AuditLog(
            Integer accountId,
            String action,
            String entityName,
            Integer entityId,
            String fieldChanged,
            String oldValue,
            String newValue,
            String reason
    ) {
        this(
                0,
                accountId,
                null,
                action,
                entityName,
                entityId,
                fieldChanged,
                oldValue,
                newValue,
                reason,
                null
        );
    }

    public int getAuditLogId() {
        return auditLogId;
    }

    public Integer getAccountId() {
        return accountId;
    }

    public Role getActingRole() {
        return actingRole;
    }

    public String getAction() {
        return action;
    }

    public String getEntityName() {
        return entityName;
    }

    public Integer getEntityId() {
        return entityId;
    }

    public String getFieldChanged() {
        return fieldChanged;
    }

    public String getOldValue() {
        return oldValue;
    }

    public String getNewValue() {
        return newValue;
    }

    public String getReason() {
        return reason;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}