package com.gms.dao;

import com.gms.dto.PtSessionView;
import com.gms.dto.SessionStatRow;
import com.gms.util.DBContext;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Đọc buổi PT và số liệu theo ngày cho dashboard. */
public class SessionDAO {

    /** Membership hợp lệ và không có kỳ bảo lưu bao phủ ngày buổi. */
    private static final String HAS_MEMBERSHIP_ON_SLOT_DATE =
            "EXISTS (SELECT 1 FROM Subscriptions m "
            + "WHERE m.CustomerId = %s AND m.Status IN ('Active','Frozen') "
            + "AND m.SubscriptionType = 'Membership' "
            + "AND t.SlotDate BETWEEN m.StartDate AND m.EndDate "
            + "AND NOT EXISTS (SELECT 1 FROM FreezeRecords f WHERE f.SubscriptionId = m.SubscriptionId "
            + "AND t.SlotDate BETWEEN f.StartDate AND COALESCE(f.ActualEndDate, f.PlannedEndDate)))";

    /** Đọc các buổi PT trong khoảng ngày và tình trạng membership. */
    public List<PtSessionView> findByPt(int ptEmployeeId, LocalDate fromDate, LocalDate toDate, boolean scheduledOnly)
            throws SQLException {
        // Gói couple: buổi "không diễn ra" chỉ khi CẢ HAI người đều không có gói tập (CBR-64).
        // Gói đơn: không có người đi kèm → chỉ xét người đứng tên.
        String sql = "SELECT s.SessionId, t.SlotDate, t.StartTime, t.EndTime, s.Status, s.MissedReason, "
                + "sub.SubscriptionType, p.FullName AS CustomerName, pp.FullName AS PartnerName, "
                + "CASE WHEN " + String.format(HAS_MEMBERSHIP_ON_SLOT_DATE, "sub.CustomerId") + " THEN 1 "
                + "     WHEN o.PartnerCustomerId IS NOT NULL AND "
                + String.format(HAS_MEMBERSHIP_ON_SLOT_DATE, "o.PartnerCustomerId") + " THEN 1 "
                + "     ELSE 0 END AS HasMembership "
                + "FROM Sessions s "
                + "JOIN TimeSlots t ON t.TimeSlotId = s.TimeSlotId "
                + "JOIN Subscriptions sub ON sub.SubscriptionId = s.SubscriptionId "
                + "JOIN Customers c ON c.CustomerId = sub.CustomerId "
                + "JOIN Persons p ON p.PersonId = c.PersonId "
                + "JOIN Orders o ON o.OrderId = sub.OrderId "
                + "LEFT JOIN Customers pc ON pc.CustomerId = o.PartnerCustomerId "
                + "LEFT JOIN Persons pp ON pp.PersonId = pc.PersonId "
                + "WHERE s.PtEmployeeId = ? AND t.SlotDate BETWEEN ? AND ? "
                + (scheduledOnly ? "AND s.Status = 'Scheduled' " : "")
                + "ORDER BY t.SlotDate, t.StartTime";

        List<PtSessionView> sessions = new ArrayList<>();
        try (Connection conn = DBContext.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, ptEmployeeId);
            stmt.setDate(2, Date.valueOf(fromDate));
            stmt.setDate(3, Date.valueOf(toDate));

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    sessions.add(new PtSessionView(
                            rs.getInt("SessionId"),
                            rs.getDate("SlotDate").toLocalDate(),
                            rs.getTime("StartTime").toLocalTime(),
                            rs.getTime("EndTime").toLocalTime(),
                            rs.getString("Status"),
                            rs.getString("SubscriptionType"),
                            rs.getString("CustomerName"),
                            rs.getString("PartnerName"),
                            rs.getString("MissedReason"),
                            rs.getInt("HasMembership") == 0));
                }
            }
        }
        return sessions;
    }

    /** Đọc trạng thái và hoa hồng buổi PT trong khoảng ngày. */
    public List<SessionStatRow> findStatRows(int ptEmployeeId, LocalDate fromDate, LocalDate toDate)
            throws SQLException {
        String sql = "SELECT t.SlotDate, t.EndTime, s.Status, s.CancelType, sub.SubscriptionType, s.CommissionAmount "
                + "FROM Sessions s "
                + "JOIN TimeSlots t ON t.TimeSlotId = s.TimeSlotId "
                + "JOIN Subscriptions sub ON sub.SubscriptionId = s.SubscriptionId "
                + "WHERE s.PtEmployeeId = ? AND t.SlotDate BETWEEN ? AND ?";

        List<SessionStatRow> rows = new ArrayList<>();
        try (Connection conn = DBContext.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, ptEmployeeId);
            stmt.setDate(2, Date.valueOf(fromDate));
            stmt.setDate(3, Date.valueOf(toDate));

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    rows.add(new SessionStatRow(
                            rs.getDate("SlotDate").toLocalDate(),
                            rs.getTime("EndTime").toLocalTime(),
                            rs.getString("Status"),
                            rs.getString("CancelType"),
                            rs.getString("SubscriptionType"),
                            rs.getLong("CommissionAmount")));
                }
            }
        }
        return rows;
    }
}
