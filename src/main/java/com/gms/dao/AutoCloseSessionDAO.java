package com.gms.dao;

import com.gms.entity.Session;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Đọc và chốt buổi quá ngày cho JOB-04. */
public class AutoCloseSessionDAO {
    public record Candidate(int sessionId, int timeSlotId, LocalDate slotDate, boolean couple,
            boolean holderFrozen, boolean holderMembership, boolean partnerFrozen,
            boolean partnerMembership, boolean checkedIn) {
    }

    private static String frozen(String customer) {
        return "EXISTS (SELECT 1 FROM Subscriptions m JOIN FreezeRecords f "
                + "ON f.SubscriptionId=m.SubscriptionId WHERE m.CustomerId=" + customer
                + " AND m.SubscriptionType='Membership' AND t.SlotDate BETWEEN "
                + "f.StartDate AND COALESCE(f.ActualEndDate,f.PlannedEndDate))";
    }

    private static String membership(String customer) {
        return "EXISTS (SELECT 1 FROM Subscriptions m WHERE m.CustomerId=" + customer
                + " AND m.SubscriptionType='Membership' AND m.Status IN ('Active','Frozen') "
                + "AND t.SlotDate BETWEEN m.StartDate AND m.EndDate)";
    }

    /** Lấy mã buổi Scheduled đã qua ngày VN. */
    public List<Integer> findOverdueIds(Connection conn, LocalDate today) throws SQLException {
        String sql = "SELECT s.SessionId FROM Sessions s JOIN TimeSlots t "
                + "ON t.TimeSlotId=s.TimeSlotId WHERE s.Status='Scheduled' AND t.SlotDate<? ORDER BY s.SessionId";
        List<Integer> ids = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setDate(1, Date.valueOf(today));
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getInt(1));
                }
            }
        }
        return ids;
    }

    /** Khóa buổi và đọc điều kiện chốt trong transaction. */
    public Candidate findForClosing(Connection conn, int id, LocalDate today) throws SQLException {
        String sql = "SELECT s.SessionId,s.TimeSlotId,t.SlotDate,sub.SubscriptionType, "
                + "CASE WHEN " + frozen("sub.CustomerId") + " THEN 1 ELSE 0 END AS HolderFrozen, "
                + "CASE WHEN " + membership("sub.CustomerId") + " THEN 1 ELSE 0 END AS HolderMembership, "
                + "CASE WHEN " + frozen("o.PartnerCustomerId") + " THEN 1 ELSE 0 END AS PartnerFrozen, "
                + "CASE WHEN " + membership("o.PartnerCustomerId") + " THEN 1 ELSE 0 END AS PartnerMembership, "
                + "CASE WHEN EXISTS (SELECT 1 FROM CheckIns ci JOIN Subscriptions m "
                + "ON m.SubscriptionId=ci.SubscriptionId WHERE m.SubscriptionType IN ('Membership','DayPass') "
                + "AND (m.CustomerId=sub.CustomerId OR (sub.SubscriptionType='PTCouple' "
                + "AND m.CustomerId=o.PartnerCustomerId)) AND ci.CheckedInAt>=CAST(t.SlotDate AS DATETIME2) "
                + "AND ci.CheckedInAt<DATEADD(DAY,1,CAST(t.SlotDate AS DATETIME2))) THEN 1 ELSE 0 END AS CheckedIn "
                + "FROM Sessions s WITH (UPDLOCK,HOLDLOCK) JOIN TimeSlots t ON t.TimeSlotId=s.TimeSlotId "
                + "JOIN Subscriptions sub ON sub.SubscriptionId=s.SubscriptionId JOIN Orders o ON o.OrderId=sub.OrderId "
                + "WHERE s.SessionId=? AND s.Status='Scheduled' AND t.SlotDate<?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);
            stmt.setDate(2, Date.valueOf(today));
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new Candidate(id, rs.getInt("TimeSlotId"), rs.getDate("SlotDate").toLocalDate(),
                        "PTCouple".equals(rs.getString("SubscriptionType")), rs.getBoolean("HolderFrozen"),
                        rs.getBoolean("HolderMembership"), rs.getBoolean("PartnerFrozen"),
                        rs.getBoolean("PartnerMembership"), rs.getBoolean("CheckedIn"));
            }
        }
    }

    /** Ghi kết quả nếu buổi vẫn còn Scheduled. */
    public boolean close(Connection conn, Candidate c, Session.Status status, Session.MissedReason reason)
            throws SQLException {
        String sql = "UPDATE Sessions SET Status=?,MissedReason=?,IsAutoClosed=1, "
                + "AttendanceMarkedAt=SYSDATETIME() WHERE SessionId=? AND Status='Scheduled'";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, status.getDbValue());
            stmt.setString(2, reason == null ? null : reason.getDbValue());
            stmt.setInt(3, c.sessionId());
            return stmt.executeUpdate() == 1;
        }
    }

    /** Nhả slot khi không còn buổi giữ chỗ (DBR-36). */
    public void releaseSlot(Connection conn, int timeSlotId) throws SQLException {
        String sql = "UPDATE TimeSlots SET Status='Free',HeldByOrderId=NULL,HoldExpiresAt=NULL "
                + "WHERE TimeSlotId=? AND Status='Booked' AND NOT EXISTS "
                + "(SELECT 1 FROM Sessions WHERE TimeSlotId=? AND Status IN ('Scheduled','CheckedIn'))";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, timeSlotId);
            stmt.setInt(2, timeSlotId);
            stmt.executeUpdate();
        }
    }
}
