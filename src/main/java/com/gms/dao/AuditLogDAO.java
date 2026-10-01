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
                        + "(AccountId, Action, EntityName, EntityId, "
                        + "FieldChanged, OldValue, NewValue, Reason) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement stmt =
                     conn.prepareStatement(sql)) {

            if (auditLog.getAccountId() == null) {
                stmt.setNull(1, Types.INTEGER);
            } else {
                stmt.setInt(1, auditLog.getAccountId());
            }

            stmt.setString(2, auditLog.getAction());
            stmt.setString(3, auditLog.getEntityName());

            if (auditLog.getEntityId() == null) {
                stmt.setNull(4, Types.INTEGER);
            } else {
                stmt.setInt(4, auditLog.getEntityId());
            }

            stmt.setString(5, auditLog.getFieldChanged());
            stmt.setString(6, auditLog.getOldValue());
            stmt.setString(7, auditLog.getNewValue());
            stmt.setString(8, auditLog.getReason());

            return stmt.executeUpdate() == 1;
        }
    }
}