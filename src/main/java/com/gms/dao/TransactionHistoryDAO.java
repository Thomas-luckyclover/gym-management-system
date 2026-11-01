package com.gms.dao;

import com.gms.dto.TransactionHistoryItem;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class TransactionHistoryDAO {

    public List<TransactionHistoryItem> findByAccount(
            Connection conn,
            int accountId,
            String status,
            int offset,
            int limit) throws SQLException {

        String sql = """
                SELECT
                    t.TransactionId,
                    o.OrderCode,
                    o.PackageNameSnapshot,
                    t.Amount,
                    t.PaymentMethod,
                    t.Status,
                    t.GatewayReference,
                    t.CreatedAt,
                    t.CompletedAt,
                    t.NeedsRefund,
                    t.RefundedAt,
                    s.SubscriptionId,
                    pk.PackageType,
                    o.UnitPriceSnapshot,
                    o.DiscountAmount,
                    v.VoucherCode,
                    o.VatRateSnapshot,
                    o.VatAmount
                FROM Transactions t
                JOIN Orders o
                    ON o.OrderId = t.OrderId
                JOIN Packages pk
                    ON pk.PackageId = o.PackageId
                LEFT JOIN Vouchers v
                    ON v.VoucherId = o.VoucherId
                JOIN Customers c
                    ON c.CustomerId = o.HolderCustomerId
                JOIN Accounts a
                    ON a.PersonId = c.PersonId
                LEFT JOIN Subscriptions s
                    ON s.OrderId = o.OrderId
                WHERE a.AccountId = ?
                  AND a.Status = 'Active'
                  AND c.Status <> 'Inactive'
                """;

        if (status != null) {
            sql += " AND t.Status = ? ";
        }

        sql += """
                ORDER BY t.CreatedAt DESC, t.TransactionId DESC
                OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
                """;

        List<TransactionHistoryItem> items = new ArrayList<>();

        try (PreparedStatement statement = conn.prepareStatement(sql)) {
            int index = 1;

            statement.setInt(index++, accountId);

            if (status != null) {
                statement.setString(index++, status);
            }

            statement.setInt(index++, offset);
            statement.setInt(index, limit);

            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    int subscriptionValue = rows.getInt("SubscriptionId");
                    Integer subscriptionId = rows.wasNull()
                            ? null : subscriptionValue;

                    items.add(new TransactionHistoryItem(
                            rows.getInt("TransactionId"),
                            rows.getString("OrderCode"),
                            rows.getString("PackageNameSnapshot"),
                            rows.getLong("Amount"),
                            rows.getString("PaymentMethod"),
                            rows.getString("Status"),
                            rows.getString("GatewayReference"),
                            toLocalDateTime(rows.getTimestamp("CreatedAt")),
                            toLocalDateTime(rows.getTimestamp("CompletedAt")),
                            rows.getBoolean("NeedsRefund"),
                            toLocalDateTime(rows.getTimestamp("RefundedAt")),
                            subscriptionId,
                            rows.getString("PackageType"),
                            rows.getLong("UnitPriceSnapshot"),
                            rows.getLong("DiscountAmount"),
                            rows.getString("VoucherCode"),
                            rows.getInt("VatRateSnapshot"),
                            rows.getLong("VatAmount")));
                }
            }
        }

        return items;
    }

    private LocalDateTime toLocalDateTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }
}