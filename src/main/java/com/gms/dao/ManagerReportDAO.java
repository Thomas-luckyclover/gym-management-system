package com.gms.dao;

import com.gms.dto.ReportView;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Truy vấn báo cáo doanh thu và hoa hồng cho quản lý. */
public class ManagerReportDAO {

    // Điều kiện chung của "giao dịch tính vào doanh thu"
    private static final String REVENUE_FROM_WHERE =
            " FROM Transactions t JOIN Orders o ON o.OrderId = t.OrderId "
            + "JOIN Packages p ON p.PackageId = o.PackageId "
            + "WHERE t.Status = 'Success' AND o.Status <> 'Cancelled' "
            + "AND t.CompletedAt >= ? AND t.CompletedAt < ? ";

    /**
     * Tổng doanh thu và tổng VAT trong khoảng (chỉ tính giao dịch Success của đơn không Cancelled).
     */
    public long[] sumRevenueAndVat(Connection conn, LocalDateTime start, LocalDateTime endExclusive)
            throws SQLException {
        String sql = "SELECT COALESCE(SUM(CAST(t.Amount AS BIGINT)), 0), COALESCE(SUM(CAST(o.VatAmount AS BIGINT)), 0)"
                + REVENUE_FROM_WHERE;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setTimestamp(1, Timestamp.valueOf(start));
            stmt.setTimestamp(2, Timestamp.valueOf(endExclusive));
            try (ResultSet rs = stmt.executeQuery()) {
                rs.next();
                return new long[] {rs.getLong(1), rs.getLong(2)};
            }
        }
    }

    /** Doanh thu, số đơn và VAT theo từng gói (mọi đơn đều thuộc một gói, kể cả vé 1 ngày). */
    public List<ReportView.PackageRow> findRevenueByPackage(Connection conn, LocalDateTime start,
            LocalDateTime endExclusive) throws SQLException {
        String sql = "SELECT p.PackageId, p.PackageName, p.PackageType, COUNT(*), "
                + "SUM(CAST(t.Amount AS BIGINT)), SUM(CAST(o.VatAmount AS BIGINT))"
                + REVENUE_FROM_WHERE
                + "GROUP BY p.PackageId, p.PackageName, p.PackageType "
                + "ORDER BY SUM(CAST(t.Amount AS BIGINT)) DESC";
        List<ReportView.PackageRow> rows = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setTimestamp(1, Timestamp.valueOf(start));
            stmt.setTimestamp(2, Timestamp.valueOf(endExclusive));
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    rows.add(new ReportView.PackageRow(rs.getInt(1), rs.getString(2), rs.getString(3),
                            rs.getLong(4), rs.getLong(5), rs.getLong(6)));
                }
            }
        }
        return rows;
    }

    /** Doanh thu từng ngày (chỉ những ngày có phát sinh; ngày trống Service tự điền 0). */
    public Map<LocalDate, Long> findDailyRevenue(Connection conn, LocalDateTime start, LocalDateTime endExclusive)
            throws SQLException {
        String sql = "SELECT CAST(t.CompletedAt AS DATE), SUM(CAST(t.Amount AS BIGINT))"
                + REVENUE_FROM_WHERE
                + "GROUP BY CAST(t.CompletedAt AS DATE)";
        Map<LocalDate, Long> daily = new LinkedHashMap<>();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setTimestamp(1, Timestamp.valueOf(start));
            stmt.setTimestamp(2, Timestamp.valueOf(endExclusive));
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    daily.put(rs.getDate(1).toLocalDate(), rs.getLong(2));
                }
            }
        }
        return daily;
    }

    /**
     * Số lượt check-in trong khoảng.
     *
     * @return mảng {tổng lượt, lượt bằng vé 1 ngày}
     */
    public long[] countCheckIns(Connection conn, LocalDateTime start, LocalDateTime endExclusive)
            throws SQLException {
        String sql = "SELECT COUNT(*), COALESCE(SUM(CASE WHEN s.SubscriptionType = 'DayPass' THEN 1 ELSE 0 END), 0) "
                + "FROM CheckIns c JOIN Subscriptions s ON s.SubscriptionId = c.SubscriptionId "
                + "WHERE c.CheckedInAt >= ? AND c.CheckedInAt < ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setTimestamp(1, Timestamp.valueOf(start));
            stmt.setTimestamp(2, Timestamp.valueOf(endExclusive));
            try (ResultSet rs = stmt.executeQuery()) {
                rs.next();
                return new long[] {rs.getLong(1), rs.getLong(2)};
            }
        }
    }

    /**
     * Hoa hồng từng PT theo NGÀY DIỄN RA BUỔI (CHỐT-D4). Tính buổi CheckedIn, Missed, và Cancelled do
     * PT hủy / khách hủy (CBR-80…82). Buổi hủy do phòng đóng cửa hoặc gói PT hết hạn (CancelType GymClosed / PackageExpired)
     * KHÔNG tính. Hoa hồng tính cho PT đang giữ buổi (Sessions.PtEmployeeId).
     */
    public List<ReportView.PtRow> findPtCommission(Connection conn, LocalDate from, LocalDate to)
            throws SQLException {
        String sql = "SELECT e.EmployeeId, pe.FullName, e.CommissionRate, "
                + "SUM(CASE WHEN se.Status = 'CheckedIn' THEN 1 ELSE 0 END), "
                + "SUM(CASE WHEN se.Status = 'Missed' THEN 1 ELSE 0 END), "
                + "SUM(CASE WHEN se.Status = 'Cancelled' THEN 1 ELSE 0 END), "
                + "SUM(CAST(se.CommissionAmount AS BIGINT)) "
                + "FROM Sessions se "
                + "JOIN TimeSlots ts ON ts.TimeSlotId = se.TimeSlotId "
                + "JOIN Employees e ON e.EmployeeId = se.PtEmployeeId "
                + "JOIN Persons pe ON pe.PersonId = e.PersonId "
                + "WHERE (se.Status IN ('CheckedIn', 'Missed') "
                + "       OR (se.Status = 'Cancelled' AND se.CancelType IN ('ByPt', 'ByCustomer'))) "
                + "AND ts.SlotDate >= ? AND ts.SlotDate <= ? "
                + "GROUP BY e.EmployeeId, pe.FullName, e.CommissionRate "
                + "ORDER BY SUM(CAST(se.CommissionAmount AS BIGINT)) DESC";
        List<ReportView.PtRow> rows = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setDate(1, Date.valueOf(from));
            stmt.setDate(2, Date.valueOf(to));
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    rows.add(new ReportView.PtRow(rs.getInt(1), rs.getString(2), rs.getInt(3),
                            rs.getLong(4), rs.getLong(5), rs.getLong(6), rs.getLong(7)));
                }
            }
        }
        return rows;
    }

    /** Số khách đang Active (ảnh chụp lúc xem, không phụ thuộc khoảng ngày). */
    public int countActiveMembers(Connection conn) throws SQLException {
        return queryCount(conn, "SELECT COUNT(*) FROM Customers WHERE Status = 'Active' AND DeletedAt IS NULL");
    }

    /** Số gói tập (Membership) đang Active — không gồm vé 1 ngày và gói PT. */
    public int countRunningPackages(Connection conn) throws SQLException {
        return queryCount(conn,
                "SELECT COUNT(*) FROM Subscriptions WHERE SubscriptionType = 'Membership' AND Status = 'Active'");
    }

    /** Số khách mới trong khoảng: đã rời trạng thái New (đã có gói) và JoinedAt nằm trong khoảng. */
    public int countNewMembers(Connection conn, LocalDateTime start, LocalDateTime endExclusive)
            throws SQLException {
        String sql = "SELECT COUNT(*) FROM Customers WHERE Status <> 'New' AND JoinedAt >= ? AND JoinedAt < ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setTimestamp(1, Timestamp.valueOf(start));
            stmt.setTimestamp(2, Timestamp.valueOf(endExclusive));
            try (ResultSet rs = stmt.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private int queryCount(Connection conn, String sql) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(sql);
                ResultSet rs = stmt.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
