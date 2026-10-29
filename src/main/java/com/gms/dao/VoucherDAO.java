package com.gms.dao;

import com.gms.entity.Voucher;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/** Đọc voucher và giữ lượt dùng trong transaction do PaymentService quản lý. */
public class VoucherDAO {

    /**
     * Khóa voucher cũ và mã đang chọn trong cùng transaction; thứ tự khóa thực tế do SQL Server quyết định.
     * @param conn transaction đổi voucher
     * @param code mã đã trim và chuyển in hoa; null khi chỉ gỡ mã
     * @param currentVoucherId mã đang gắn trên đơn hoặc null
     * @return voucher khớp mã mới và/hoặc voucher cũ; Service kiểm điều kiện áp dụng
     */
    public List<Voucher> findForChange(Connection conn, String code, Integer currentVoucherId) throws SQLException {
        String sql = "SELECT VoucherId, VoucherCode, DiscountType, DiscountValue, StartDate, EndDate, "
                + "MaxUsage, MaxUsagePerCustomer, UsedCount, MinOrderValue, ApplicableScope, IsActive "
                + "FROM Vouchers WITH (UPDLOCK, HOLDLOCK) "
                + "WHERE VoucherCode = ? OR VoucherId = ? ORDER BY VoucherId";
        List<Voucher> vouchers = new ArrayList<>();
        try (PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setString(1, code);
            if (currentVoucherId == null) {
                statement.setNull(2, Types.INTEGER);
            } else {
                statement.setInt(2, currentVoucherId);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    Voucher voucher = new Voucher(resultSet.getInt("VoucherId"));
                    voucher.setVoucherCode(resultSet.getString("VoucherCode"));
                    voucher.setDiscountType(Voucher.DiscountType.fromDbValue(resultSet.getString("DiscountType")));
                    voucher.setDiscountValue(resultSet.getInt("DiscountValue"));
                    voucher.setStartDate(resultSet.getDate("StartDate").toLocalDate());
                    voucher.setEndDate(resultSet.getDate("EndDate").toLocalDate());
                    voucher.setMaxUsage(resultSet.getInt("MaxUsage"));
                    int perCustomer = resultSet.getInt("MaxUsagePerCustomer");
                    voucher.setMaxUsagePerCustomer(resultSet.wasNull() ? null : perCustomer);
                    voucher.setUsedCount(resultSet.getInt("UsedCount"));
                    voucher.setMinOrderValue(resultSet.getLong("MinOrderValue"));
                    voucher.setApplicableScope(Voucher.ApplicableScope.fromDbValue(resultSet.getString("ApplicableScope")));
                    voucher.setActive(resultSet.getBoolean("IsActive"));
                    if (voucher.getDiscountType() == null || voucher.getApplicableScope() == null) {
                        throw new SQLException("Voucher có enum không hợp lệ");
                    }
                    vouchers.add(voucher);
                }
            }
        }
        return vouchers;
    }

    /** Đếm cả Pending/Paid của holder, bỏ Cancelled và đơn hiện tại (CBR-74). */
    public int countCustomerUses(Connection conn, int voucherId, int customerId, int excludedOrderId)
            throws SQLException {
        String sql = "SELECT COUNT(*) FROM Orders WHERE VoucherId = ? AND HolderCustomerId = ? "
                + "AND Status <> 'Cancelled' AND OrderId <> ?";
        try (PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setInt(1, voucherId);
            statement.setInt(2, customerId);
            statement.setInt(3, excludedOrderId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }

    /** Tăng một lượt có điều kiện; 0 nghĩa là voucher hết lượt/ngừng hoạt động. */
    public int reserveUse(Connection conn, int voucherId) throws SQLException {
        String sql = "UPDATE Vouchers SET UsedCount = UsedCount + 1 "
                + "WHERE VoucherId = ? AND IsActive = 1 AND UsedCount < MaxUsage";
        try (PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setInt(1, voucherId);
            return statement.executeUpdate();
        }
    }

    /** Đọc mã đã gắn trên đơn để hiện trong checkout; null nếu chưa áp mã/không tìm thấy. */
    public String findCodeById(Connection conn, Integer voucherId) throws SQLException {
        if (voucherId == null) {
            return null;
        }
        try (PreparedStatement statement = conn.prepareStatement("SELECT VoucherCode FROM Vouchers WHERE VoucherId = ?")) {
            statement.setInt(1, voucherId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getString("VoucherCode") : null;
            }
        }
    }
}
