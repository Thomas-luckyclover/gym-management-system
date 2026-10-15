package com.gms.dao;

import com.gms.dto.PtSessionSwapInfo;
import com.gms.dto.PtSwapSlotView;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Date;
import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/** SQL cho UC-38. Connection và transaction do Service quản lý, không commit trong DAO. */
public class PtSessionSwapDAO {
    /** Chỉ đọc buổi khách được xem; khi ghi khóa buổi/gói để quota không bị tăng đồng thời. */
    public PtSessionSwapInfo findOwnedSession(Connection connection, int accountId, int sessionId,
            boolean lock) throws SQLException {
        String hint = lock ? " WITH (UPDLOCK, HOLDLOCK)" : "";
        String sql = """
                SELECT s.SessionId, s.TimeSlotId, s.PtEmployeeId, s.Status,
                       sub.SubscriptionId, sub.CustomerId, sub.SubscriptionType,
                       sub.Status AS SubscriptionStatus, sub.StartDate, sub.EndDate,
                       sub.SwapsUsed, sub.MaxSwapsSnapshot,
                       CASE WHEN sub.SubscriptionType='PTCouple' THEN o.PartnerCustomerId END AS PartnerId,
                       t.SlotDate, t.StartTime, t.EndTime, p.FullName AS PtName
                FROM Sessions s%s
                JOIN Subscriptions sub%s ON sub.SubscriptionId=s.SubscriptionId
                JOIN Orders o ON o.OrderId=sub.OrderId
                JOIN TimeSlots t ON t.TimeSlotId=s.TimeSlotId
                JOIN Employees e ON e.EmployeeId=s.PtEmployeeId
                JOIN Persons p ON p.PersonId=e.PersonId
                JOIN Accounts a ON a.AccountId=? AND a.Status='Active'
                JOIN Customers me ON me.PersonId=a.PersonId AND me.DeletedAt IS NULL
                WHERE s.SessionId=? AND o.Status='Paid'
                  AND (sub.CustomerId=me.CustomerId
                       OR (sub.SubscriptionType='PTCouple' AND o.PartnerCustomerId=me.CustomerId))
                  AND a.PersonId<>e.PersonId AND e.Status='Working'
                """.formatted(hint, hint);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, accountId);
            statement.setInt(2, sessionId);
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) return null;
                PtSessionSwapInfo info = new PtSessionSwapInfo();
                info.setSessionId(row.getInt("SessionId"));
                info.setTimeSlotId(row.getInt("TimeSlotId"));
                info.setPtEmployeeId(row.getInt("PtEmployeeId"));
                info.setPtName(row.getString("PtName"));
                info.setSubscriptionId(row.getInt("SubscriptionId"));
                info.setCustomerId(row.getInt("CustomerId"));
                int partner = row.getInt("PartnerId");
                info.setPartnerCustomerId(row.wasNull() ? null : partner);
                info.setSubscriptionType(row.getString("SubscriptionType"));
                info.setSubscriptionStatus(row.getString("SubscriptionStatus"));
                info.setSessionStatus(row.getString("Status"));
                info.setPackageStartDate(row.getDate("StartDate")==null ? null : row.getDate("StartDate").toLocalDate());
                info.setPackageEndDate(row.getDate("EndDate")==null ? null : row.getDate("EndDate").toLocalDate());
                info.setSlotDate(row.getDate("SlotDate").toLocalDate());
                info.setStartTime(row.getTime("StartTime").toLocalTime());
                info.setEndTime(row.getTime("EndTime").toLocalTime());
                info.setSwapsUsed(row.getInt("SwapsUsed"));
                info.setMaxSwaps(row.getInt("MaxSwapsSnapshot"));
                return info;
            }
        }
    }

    /** Slot Free của đúng PT, trong cửa sổ ngày; Service kiểm tra đầy đủ trước khi hiển thị/ghi. */
    public List<PtSwapSlotView> findFreeSlots(Connection connection, int ptId, LocalDate from,
            LocalDate to) throws SQLException {
        String sql = """
                SELECT TimeSlotId, SlotDate, StartTime, EndTime FROM TimeSlots
                WHERE PtEmployeeId=? AND Status='Free' AND SlotDate BETWEEN ? AND ?
                ORDER BY SlotDate, StartTime, TimeSlotId
                """;
        List<PtSwapSlotView> slots = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, ptId);
            statement.setDate(2, Date.valueOf(from));
            statement.setDate(3, Date.valueOf(to));
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) slots.add(new PtSwapSlotView(rows.getInt(1),
                        rows.getDate(2).toLocalDate(), rows.getTime(3).toLocalTime(), rows.getTime(4).toLocalTime()));
            }
        }
        return slots;
    }

    /** Quyền vào gym theo notes: Membership hoặc DayPass theo ngày, nhưng không trong kỳ bảo lưu. */
    public boolean canUseGym(Connection connection, int customerId, LocalDate date) throws SQLException {
        String sql = """
                SELECT CASE WHEN EXISTS (
                    SELECT 1 FROM Subscriptions g JOIN Orders o ON o.OrderId=g.OrderId
                    WHERE g.CustomerId=? AND g.SubscriptionType IN ('Membership','DayPass')
                      AND g.Status IN ('Active','Frozen') AND o.Status='Paid'
                      AND ? BETWEEN g.StartDate AND g.EndDate
                ) AND NOT EXISTS (
                    SELECT 1 FROM FreezeRecords f JOIN Subscriptions g ON g.SubscriptionId=f.SubscriptionId
                    WHERE g.CustomerId=? AND ? BETWEEN f.StartDate
                      AND COALESCE(f.ActualEndDate,f.PlannedEndDate)
                ) THEN 1 ELSE 0 END
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, customerId);
            statement.setDate(2, Date.valueOf(date));
            statement.setInt(3, customerId);
            statement.setDate(4, Date.valueOf(date));
            try (ResultSet row = statement.executeQuery()) { row.next(); return row.getBoolean(1); }
        }
    }

    /** Kiểm tra mọi gói của cả hai khách và lịch PT, không chỉ gói nguồn. */
    public boolean hasOverlap(Connection connection, PtSessionSwapInfo info, PtSwapSlotView slot)
            throws SQLException {
        String sql = """
                SELECT TOP 1 s.SessionId FROM Sessions s
                JOIN TimeSlots t ON t.TimeSlotId=s.TimeSlotId
                JOIN Subscriptions sub ON sub.SubscriptionId=s.SubscriptionId
                JOIN Orders o ON o.OrderId=sub.OrderId
                WHERE s.Status='Scheduled' AND s.SessionId<>? AND t.SlotDate=?
                  AND t.StartTime<CAST(? AS TIME) AND t.EndTime>CAST(? AS TIME)
                  AND (s.PtEmployeeId=? OR sub.CustomerId IN (?,?)
                       OR (sub.SubscriptionType='PTCouple' AND o.PartnerCustomerId IN (?,?)))
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, info.getSessionId());
            statement.setDate(2, Date.valueOf(slot.getSlotDate()));
            statement.setTime(3, Time.valueOf(slot.getEndTime()));
            statement.setTime(4, Time.valueOf(slot.getStartTime()));
            statement.setInt(5, info.getPtEmployeeId());
            int partner = info.getPartnerCustomerId() == null ? info.getCustomerId() : info.getPartnerCustomerId();
            statement.setInt(6, info.getCustomerId()); statement.setInt(7, partner);
            statement.setInt(8, info.getCustomerId()); statement.setInt(9, partner);
            try (ResultSet rows = statement.executeQuery()) { return rows.next(); }
        }
    }

    /** Lịch nghỉ/giờ ngoại lệ ưu tiên P-19. */
    public boolean isOpen(Connection connection, PtSwapSlotView slot, LocalTime open, LocalTime close)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT IsClosedAllDay, OpenTime, CloseTime FROM OperatingHourExceptions WHERE ApplyDate=?")) {
            statement.setDate(1, Date.valueOf(slot.getSlotDate()));
            try (ResultSet row = statement.executeQuery()) {
                if (row.next()) {
                    if (row.getBoolean(1)) return false;
                    open = row.getTime(2).toLocalTime(); close = row.getTime(3).toLocalTime();
                }
            }
        }
        return !slot.getStartTime().isBefore(open) && !slot.getEndTime().isAfter(close);
    }

    /** Gán tham số số nguyên cho SQL cố định; trả số dòng ghi để Service phát hiện cạnh tranh. */
    private int update(Connection connection, String sql, int... values) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index=0; index<values.length; index++) statement.setInt(index+1, values[index]);
            return statement.executeUpdate();
        }
    }

    /** Khóa các khách theo thứ tự cố định để hai swap khác gói không xếp trùng khách. */
    public void lockParticipants(Connection connection, PtSessionSwapInfo info) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT CustomerId FROM Customers WITH (UPDLOCK,HOLDLOCK)
                WHERE CustomerId IN (?,?) ORDER BY CustomerId
                """)) {
            statement.setInt(1, info.getCustomerId());
            statement.setInt(2, info.getPartnerCustomerId()==null ? info.getCustomerId() : info.getPartnerCustomerId());
            try (ResultSet rows=statement.executeQuery()) { while (rows.next()) rows.getInt(1); }
        }
    }

    public boolean claimSlot(Connection connection, int slotId, int ptId) throws SQLException {
        return update(connection, """
                UPDATE TimeSlots SET Status='Booked'
                WHERE TimeSlotId=? AND PtEmployeeId=? AND Status='Free'
                  AND HeldByOrderId IS NULL AND HoldExpiresAt IS NULL
                """, slotId, ptId)==1;
    }

    public boolean moveSession(Connection connection, PtSessionSwapInfo info, int targetId) throws SQLException {
        return update(connection, """
                UPDATE Sessions SET TimeSlotId=? WHERE SessionId=? AND TimeSlotId=? AND Status='Scheduled'
                """, targetId, info.getSessionId(), info.getTimeSlotId())==1;
    }

    public boolean increaseSwaps(Connection connection, int subscriptionId) throws SQLException {
        return update(connection, """
                UPDATE Subscriptions SET SwapsUsed=SwapsUsed+1
                WHERE SubscriptionId=? AND Status='Active' AND SwapsUsed<MaxSwapsSnapshot
                """, subscriptionId)==1;
    }

    public void releaseOldSlot(Connection connection, int slotId) throws SQLException {
        update(connection, """
                UPDATE TimeSlots SET Status='Free' WHERE TimeSlotId=? AND Status='Booked'
                  AND NOT EXISTS (SELECT 1 FROM Sessions WHERE TimeSlotId=? AND Status='Scheduled')
                """, slotId, slotId);
    }

    public void insertMoveLog(Connection connection, PtSessionSwapInfo info, int targetId, int accountId)
            throws SQLException {
        update(connection, """
                INSERT INTO SessionMoveLogs
                    (SessionId,MoveType,OldTimeSlotId,NewTimeSlotId,OldPtEmployeeId,NewPtEmployeeId,RequestedByAccountId)
                VALUES (?,'CustomerSwap',?,?,?,?,?)
                """, info.getSessionId(), info.getTimeSlotId(), targetId,
                info.getPtEmployeeId(), info.getPtEmployeeId(), accountId);
        update(connection, """
                INSERT INTO AuditLogs (AccountId,ActingRole,Action,EntityName,EntityId,FieldChanged,OldValue,NewValue)
                VALUES (?,'Customer',N'Đổi buổi PT','Sessions',?,'TimeSlotId',CAST(? AS NVARCHAR(1000)),CAST(? AS NVARCHAR(1000)))
                """, accountId, info.getSessionId(), info.getTimeSlotId(), targetId);
    }

    /** Người nhận gồm PT và các thành viên có tài khoản Active; không gửi ra kênh ngoài. */
    public List<Integer> findRecipients(Connection connection, PtSessionSwapInfo info) throws SQLException {
        String sql = """
                SELECT DISTINCT a.AccountId FROM Accounts a WHERE a.Status='Active' AND (
                  a.PersonId IN (SELECT PersonId FROM Customers WHERE CustomerId IN (?,?))
                  OR a.PersonId IN (SELECT PersonId FROM Employees WHERE EmployeeId=?))
                """;
        List<Integer> ids = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, info.getCustomerId());
            statement.setInt(2, info.getPartnerCustomerId()==null ? info.getCustomerId() : info.getPartnerCustomerId());
            statement.setInt(3, info.getPtEmployeeId());
            try (ResultSet rows=statement.executeQuery()) { while (rows.next()) ids.add(rows.getInt(1)); }
        }
        return ids;
    }
}
