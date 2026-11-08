package com.gms.dao;

import com.gms.entity.AuditLog;
import com.gms.util.DBContext;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;

/**
 * Truy vấn bảng AuditLogs.
 */
public class AuditLogDAO {

    /**
     * Ghi một bản ghi AuditLog bằng connection riêng.
     *
     * @param auditLog dữ liệu nhật ký cần ghi
     * @return true nếu ghi thành công
     * @throws SQLException khi truy vấn database lỗi
     */
    public boolean insert(AuditLog auditLog) throws SQLException {
        try (Connection conn = DBContext.getConnection()) {
            return insert(conn, auditLog);
        }
    }

    /**
     * Ghi AuditLog bằng connection có sẵn để dùng chung transaction.
     *
     * @param conn connection hiện tại
     * @param auditLog dữ liệu nhật ký cần ghi
     * @return true nếu ghi thành công
     * @throws SQLException khi truy vấn database lỗi
     */
    public boolean insert(
            Connection conn,
            AuditLog auditLog
    ) throws SQLException {

        String sql =
                "INSERT INTO AuditLogs "
                        + "(AccountId, ActingRole, Action, EntityName, EntityId, "
                        + "FieldChanged, OldValue, NewValue, Reason) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement stmt =
                     conn.prepareStatement(sql)) {

            if (auditLog.getAccountId() == null) {
                stmt.setNull(1, Types.INTEGER);
            } else {
                stmt.setInt(1, auditLog.getAccountId());
            }

            if (auditLog.getActingRole() == null) {
                stmt.setNull(2, Types.VARCHAR);
            } else {
                stmt.setString(2, auditLog.getActingRole().getDbValue());
            }

            stmt.setString(3, safeTruncate(auditLog.getAction(), 100));
            stmt.setString(4, safeTruncate(auditLog.getEntityName(), 50));

            if (auditLog.getEntityId() == null) {
                stmt.setNull(5, Types.INTEGER);
            } else {
                stmt.setInt(5, auditLog.getEntityId());
            }

            stmt.setString(6, safeTruncate(auditLog.getFieldChanged(), 50));
            stmt.setString(7, safeTruncate(auditLog.getOldValue(), 1000));
            stmt.setString(8, safeTruncate(auditLog.getNewValue(), 1000));
            stmt.setString(9, safeTruncate(auditLog.getReason(), 500));

            return stmt.executeUpdate() == 1;
        }
    }

    private static String safeTruncate(String val, int maxLen) {
        if (val == null || val.length() <= maxLen) {
            return val;
        }
        return val.substring(0, maxLen);
    }
}