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
 * Tầng Service xử lý nghiệp vụ ghi nhật ký kiểm toán ({@code AuditLogs}) cho các thao tác nhạy cảm.
 *
 * <p><b>Cách hoạt động:</b> Chuẩn hóa dữ liệu nhật ký trước khi lưu qua {@link AuditLogDAO}, tự động ẩn giá trị
 * các trường nhạy cảm (mật khẩu, mã, token) và hỗ trợ cả chế độ ghi độc lập lẫn ghi chung trong transaction.</p>
 */
public class AuditLogService {

    private final AuditLogDAO auditLogDAO;

    /**
     * Khởi tạo Service cùng {@link AuditLogDAO} mặc định.
     */
    public AuditLogService() {
        auditLogDAO = new AuditLogDAO();
    }

    /**
     * Ghi một bản ghi nhật ký thao tác nhạy cảm vào bảng {@code AuditLogs} bằng kết nối riêng.
     *
     * <p><b>Cách hoạt động:</b> Tạo entity {@link AuditLog} thông qua {@link #createAuditLog} (tự động che các
     * trường nhạy cảm), gọi {@code auditLogDAO.insert(auditLog)} và trả về {@link ServiceResult} thành công/thất bại.</p>
     *
     * @param accountId    ID tài khoản thực hiện (có thể {@code null} với tác vụ ẩn danh)
     * @param action       mô tả hành động thực hiện
     * @param entityName   tên bảng/đối tượng bị tác động
     * @param entityId     ID bản ghi bị tác động
     * @param fieldChanged tên trường dữ liệu thay đổi
     * @param oldValue     giá trị trước khi đổi
     * @param newValue     giá trị sau khi đổi
     * @param reason       lý do hoặc ghi chú bổ sung
     * @return {@link ServiceResult} báo kết quả ghi nhật ký
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
     * Ghi bản ghi {@code AuditLog} kèm vai trò đang thao tác ({@code ActingRole}) ngay trong transaction hiện tại.
     *
     * <p><b>Cách hoạt động:</b> Tạo entity {@link AuditLog} có {@code actingRole} và tự che thông tin nhạy cảm,
     * rồi gọi {@code auditLogDAO.insert(conn, auditLog)}; ném {@code SQLException} nếu không chèn được để caller rollback.</p>
     *
     * @param conn         kết nối CSDL của transaction đang mở
     * @param accountId    ID tài khoản thực hiện
     * @param actingRole   vai trò hiện hành lúc thao tác (CBR-112)
     * @param action       mô tả thao tác
     * @param entityName   tên đối tượng
     * @param entityId     ID đối tượng
     * @param fieldChanged trường bị thay đổi
     * @param oldValue     giá trị cũ
     * @param newValue     giá trị mới
     * @param reason       lý do nếu có
     * @throws SQLException khi không ghi được bản ghi {@code AuditLog}
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
     * Ghi bản ghi {@code AuditLog} (không chỉ định {@code actingRole}) trong transaction đang mở.
     *
     * <p><b>Cách hoạt động:</b> Tạo đối tượng {@link AuditLog} đã che dữ liệu nhạy cảm rồi ghi qua
     * {@code auditLogDAO.insert(conn, auditLog)}; ném {@code SQLException} nếu thất bại.</p>
     *
     * @param conn         kết nối CSDL của transaction hiện tại
     * @param accountId    ID tài khoản thực hiện
     * @param action       mô tả thao tác
     * @param entityName   tên đối tượng
     * @param entityId     ID đối tượng
     * @param fieldChanged trường bị thay đổi
     * @param oldValue     giá trị cũ
     * @param newValue     giá trị mới
     * @param reason       lý do nếu có
     * @throws SQLException khi không ghi được bản ghi {@code AuditLog}
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

    /**
     * Tạo đối tượng {@link AuditLog} mặc định không gắn {@code actingRole} (ủy quyền về hàm 9 tham số).
     */
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
     * Khởi tạo entity {@link AuditLog} kèm {@code actingRole} và tự động ẩn giá trị các trường nhạy cảm.
     *
     * <p><b>Cách hoạt động:</b> Gọi {@link #isSensitiveField} để kiểm tra tên trường thay đổi; nếu là trường nhạy cảm
     * thì đặt {@code oldValue} và {@code newValue} về {@code null} trước khi trả về entity.</p>
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

    /**
     * Kiểm tra tên trường thay đổi có chứa dữ liệu nhạy cảm không được phép ghi nguyên văn vào log hay không.
     *
     * <p><b>Cách hoạt động:</b> Chuẩn hóa tên trường về chữ thường và trả về {@code true} nếu chứa
     * {@code password}, {@code code} hoặc {@code token}.</p>
     */
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
