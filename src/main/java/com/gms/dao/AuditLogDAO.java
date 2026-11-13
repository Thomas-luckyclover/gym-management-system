package com.gms.dao;

import com.gms.entity.AuditLog;
import com.gms.util.DBContext;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;

/**
 * DAO quản lý thao tác ghi dữ liệu vào bảng nhật ký kiểm toán ({@code AuditLogs}).
 *
 * <p><b>Cách hoạt động:</b> Hỗ trợ chèn bản ghi {@link AuditLog} cả bằng kết nối độc lập lẫn
 * dùng chung {@code Connection} transaction, đồng thời cắt ngắn chuỗi an toàn theo độ dài tối đa của cột CSDL.</p>
 */
public class AuditLogDAO {

    /**
     * Ghi một bản ghi {@code AuditLog} bằng kết nối riêng.
     *
     * <p><b>Cách hoạt động:</b> Tự mở kết nối qua {@code DBContext.getConnection()} rồi ủy quyền sang
     * {@link #insert(Connection, AuditLog)} để chèn bản ghi.</p>
     *
     * @param auditLog dữ liệu nhật ký cần ghi
     * @return {@code true} nếu ghi thành công 1 dòng
     * @throws SQLException khi truy vấn cơ sở dữ liệu lỗi
     */
    public boolean insert(AuditLog auditLog) throws SQLException {
        try (Connection conn = DBContext.getConnection()) {
            return insert(conn, auditLog);
        }
    }

    /**
     * Ghi bản ghi {@code AuditLog} bằng kết nối có sẵn để dùng chung transaction với thao tác chính.
     *
     * <p><b>Cách hoạt động:</b> Xử lý giá trị {@code null} cho {@code AccountId}, {@code ActingRole}, {@code EntityId};
     * gọi {@link #safeTruncate} cắt vừa độ dài cho phép các cột chuỗi rồi thực thi {@code INSERT}.</p>
     *
     * @param conn     kết nối CSDL của transaction hiện tại
     * @param auditLog dữ liệu nhật ký cần ghi
     * @return {@code true} nếu ghi thành công 1 dòng
     * @throws SQLException khi truy vấn cơ sở dữ liệu lỗi
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

    /**
     * Cắt ngắn chuỗi nếu vượt quá độ dài tối đa của cột trong CSDL.
     *
     * <p><b>Cách hoạt động:</b> Nếu chuỗi {@code null} hoặc độ dài {@code <= maxLen} thì giữ nguyên;
     * ngược lại cắt lấy {@code maxLen} ký tự đầu để tránh lỗi tràn độ dài cột khi {@code INSERT}.</p>
     */
    private static String safeTruncate(String val, int maxLen) {
        if (val == null || val.length() <= maxLen) {
            return val;
        }
        return val.substring(0, maxLen);
    }
}