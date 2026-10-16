package com.gms.dao;

import com.gms.dto.PtSessionInfo;
import com.gms.dto.PtSwapSlotView;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * [DOI-BUOI] DAO ĐỔI BUỔI (UC-38). MỖI HÀM = 1 CÂU SQL NHỎ.
 * Mọi hàm nhận Connection từ Service, vì Service mở TRANSACTION (DAO KHÔNG commit).
 *
 * TÌM NHANH (Ctrl+F gõ tag):
 *   [DOI-BUOI-GET]   đọc để lọc slot hợp lệ  → findFreeSlots, isGymOpen, isPtBusy, isCustomerBusy, canEnterGym
 *   [DOI-BUOI-POST]  ghi khi xác nhận đổi    → lockCustomers, claimSlot, moveSession, increaseSwapsUsed,
 *                                              releaseOldSlot, insertMoveLog, insertAuditLog
 *   (Người nhận thông báo: MyPtSessionDAO.findRecipientAccounts — tag [THONG-BAO])
 */
public class PtSessionSwapDAO {

    // =====================================================================
    // [DOI-BUOI-GET] ĐỌC — dùng để lọc slot hợp lệ
    // =====================================================================

    /** [DOI-BUOI-GET] Các slot TRỐNG (Free) của 1 PT trong khoảng ngày [from, to]. */
    public List<PtSwapSlotView> findFreeSlots(Connection connection, int ptEmployeeId,
            LocalDate from, LocalDate to) throws SQLException {
        String sql = """
                SELECT TimeSlotId, SlotDate, StartTime, EndTime
                FROM TimeSlots
                WHERE PtEmployeeId = ?
                  AND Status = 'Free'
                  AND SlotDate BETWEEN ? AND ?
                ORDER BY SlotDate, StartTime, TimeSlotId
                """;
        List<PtSwapSlotView> slots = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, ptEmployeeId);
            statement.setDate(2, Date.valueOf(from));
            statement.setDate(3, Date.valueOf(to));
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    slots.add(new PtSwapSlotView(
                            rs.getInt("TimeSlotId"),
                            rs.getDate("SlotDate").toLocalDate(),
                            rs.getTime("StartTime").toLocalTime(),
                            rs.getTime("EndTime").toLocalTime()));
                }
            }
        }
        return slots;
    }

    /**
     * [DOI-BUOI-GET] Slot có nằm TRONG GIỜ MỞ CỬA của gym không?
     * Ngày có lịch ngoại lệ (OperatingHourExceptions) thì dùng giờ ngoại lệ; nghỉ cả ngày → false.
     * @param open / close giờ mở cửa mặc định (cấu hình P-19)
     */
    public boolean isGymOpen(Connection connection, PtSwapSlotView slot, LocalTime open, LocalTime close)
            throws SQLException {
        String sql = """
                SELECT IsClosedAllDay, OpenTime, CloseTime
                FROM OperatingHourExceptions
                WHERE ApplyDate = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setDate(1, Date.valueOf(slot.getSlotDate()));
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    if (rs.getBoolean("IsClosedAllDay")) {
                        return false;
                    }
                    open = rs.getTime("OpenTime").toLocalTime();
                    close = rs.getTime("CloseTime").toLocalTime();
                }
            }
        }
        return !slot.getStartTime().isBefore(open) && !slot.getEndTime().isAfter(close);
    }

    /**
     * [DOI-BUOI-GET] PT có đang BẬN buổi khác TRÙNG GIỜ với slot này không?
     * Trùng giờ: (giờ bắt đầu cũ < giờ kết thúc mới) VÀ (giờ kết thúc cũ > giờ bắt đầu mới).
     */
    public boolean isPtBusy(Connection connection, int ptEmployeeId, int exceptSessionId, PtSwapSlotView slot)
            throws SQLException {
        String sql = """
                SELECT COUNT(*)
                FROM Sessions s
                JOIN TimeSlots t ON t.TimeSlotId = s.TimeSlotId
                WHERE s.Status = 'Scheduled'
                  AND s.SessionId <> ?
                  AND s.PtEmployeeId = ?
                  AND t.SlotDate = ?
                  AND t.StartTime < CAST(? AS TIME)
                  AND t.EndTime   > CAST(? AS TIME)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, exceptSessionId);
            statement.setInt(2, ptEmployeeId);
            statement.setDate(3, Date.valueOf(slot.getSlotDate()));
            statement.setTime(4, Time.valueOf(slot.getEndTime()));
            statement.setTime(5, Time.valueOf(slot.getStartTime()));
            return countIsPositive(statement);
        }
    }

    /** [DOI-BUOI-GET] Khách có đang BẬN buổi PT khác (của bất kỳ gói nào) TRÙNG GIỜ với slot này không? */
    public boolean isCustomerBusy(Connection connection, int customerId, int exceptSessionId, PtSwapSlotView slot)
            throws SQLException {
        String sql = """
                SELECT COUNT(*)
                FROM Sessions s
                JOIN TimeSlots t       ON t.TimeSlotId = s.TimeSlotId
                JOIN Subscriptions sub ON sub.SubscriptionId = s.SubscriptionId
                JOIN Orders o          ON o.OrderId = sub.OrderId
                WHERE s.Status = 'Scheduled'
                  AND s.SessionId <> ?
                  AND (sub.CustomerId = ?
                       OR (sub.SubscriptionType = 'PTCouple' AND o.PartnerCustomerId = ?))
                  AND t.SlotDate = ?
                  AND t.StartTime < CAST(? AS TIME)
                  AND t.EndTime   > CAST(? AS TIME)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, exceptSessionId);
            statement.setInt(2, customerId);
            statement.setInt(3, customerId);
            statement.setDate(4, Date.valueOf(slot.getSlotDate()));
            statement.setTime(5, Time.valueOf(slot.getEndTime()));
            statement.setTime(6, Time.valueOf(slot.getStartTime()));
            return countIsPositive(statement);
        }
    }

    /**
     * [DOI-BUOI-GET] Khách có VÀO ĐƯỢC GYM ngày đó không?
     * = có Membership/DayPass (Active hoặc Frozen, đơn đã Paid) phủ ngày đó
     *   VÀ ngày đó không nằm trong kỳ bảo lưu.
     */
    public boolean canEnterGym(Connection connection, int customerId, LocalDate date) throws SQLException {
        String hasPassSql = """
                SELECT COUNT(*)
                FROM Subscriptions g
                JOIN Orders o ON o.OrderId = g.OrderId
                WHERE g.CustomerId = ?
                  AND g.SubscriptionType IN ('Membership', 'DayPass')
                  AND g.Status IN ('Active', 'Frozen')
                  AND o.Status = 'Paid'
                  AND ? BETWEEN g.StartDate AND g.EndDate
                """;
        String frozenSql = """
                SELECT COUNT(*)
                FROM FreezeRecords f
                JOIN Subscriptions g ON g.SubscriptionId = f.SubscriptionId
                WHERE g.CustomerId = ?
                  AND ? BETWEEN f.StartDate AND COALESCE(f.ActualEndDate, f.PlannedEndDate)
                """;
        boolean hasPass = countByCustomerAndDate(connection, hasPassSql, customerId, date);
        boolean frozen = countByCustomerAndDate(connection, frozenSql, customerId, date);
        return hasPass && !frozen;
    }

    // =====================================================================
    // [DOI-BUOI-POST] GHI — chạy trong transaction của Service
    // =====================================================================

    /**
     * [DOI-BUOI-POST] KHÓA hồ sơ các khách của gói (theo thứ tự CustomerId tăng dần).
     * Mục đích: 2 lần đổi buổi KHÁC gói nhưng cùng khách không chen nhau xếp trùng giờ.
     * Khóa theo thứ tự cố định để tránh deadlock.
     */
    public void lockCustomers(Connection connection, int customerId, Integer partnerCustomerId) throws SQLException {
        String sql = """
                SELECT CustomerId
                FROM Customers WITH (UPDLOCK, HOLDLOCK)
                WHERE CustomerId IN (?, ?)
                ORDER BY CustomerId
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, customerId);
            statement.setInt(2, partnerCustomerId == null ? customerId : partnerCustomerId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    // chỉ cần đọc qua để giữ khóa
                }
            }
        }
    }

    /** [DOI-BUOI-POST] Giữ slot mới: Free → Booked. "AND Status='Free'" = nếu người khác vừa lấy thì trả false. */
    public boolean claimSlot(Connection connection, int timeSlotId, int ptEmployeeId) throws SQLException {
        String sql = """
                UPDATE TimeSlots
                SET Status = 'Booked'
                WHERE TimeSlotId = ? AND PtEmployeeId = ? AND Status = 'Free'
                  AND HeldByOrderId IS NULL AND HoldExpiresAt IS NULL
                """;
        return executeUpdate(connection, sql, timeSlotId, ptEmployeeId) == 1;
    }

    /** [DOI-BUOI-POST] Chuyển buổi sang slot mới (chỉ khi buổi vẫn ở slot cũ và còn Scheduled). */
    public boolean moveSession(Connection connection, int sessionId, int oldSlotId, int newSlotId) throws SQLException {
        String sql = """
                UPDATE Sessions
                SET TimeSlotId = ?
                WHERE SessionId = ? AND TimeSlotId = ? AND Status = 'Scheduled'
                """;
        return executeUpdate(connection, sql, newSlotId, sessionId, oldSlotId) == 1;
    }

    /** [DOI-BUOI-POST] Trừ 1 lượt đổi của gói (SwapsUsed + 1), chỉ khi còn lượt. */
    public boolean increaseSwapsUsed(Connection connection, int subscriptionId) throws SQLException {
        String sql = """
                UPDATE Subscriptions
                SET SwapsUsed = SwapsUsed + 1
                WHERE SubscriptionId = ? AND Status = 'Active' AND SwapsUsed < MaxSwapsSnapshot
                """;
        return executeUpdate(connection, sql, subscriptionId) == 1;
    }

    /** [DOI-BUOI-POST] Trả slot cũ về Free (nếu không còn buổi Scheduled nào đang dùng slot đó). */
    public void releaseOldSlot(Connection connection, int timeSlotId) throws SQLException {
        String sql = """
                UPDATE TimeSlots
                SET Status = 'Free'
                WHERE TimeSlotId = ? AND Status = 'Booked'
                  AND NOT EXISTS (SELECT 1 FROM Sessions WHERE TimeSlotId = ? AND Status = 'Scheduled')
                """;
        executeUpdate(connection, sql, timeSlotId, timeSlotId);
    }

    /** [DOI-BUOI-POST][AUDIT] Ghi nhật ký dời buổi (SessionMoveLogs). */
    public void insertMoveLog(Connection connection, PtSessionInfo info, int newSlotId, int accountId)
            throws SQLException {
        String sql = """
                INSERT INTO SessionMoveLogs (SessionId, MoveType, OldTimeSlotId, NewTimeSlotId,
                                             OldPtEmployeeId, NewPtEmployeeId, RequestedByAccountId)
                VALUES (?, 'CustomerSwap', ?, ?, ?, ?, ?)
                """;
        executeUpdate(connection, sql, info.getSessionId(), info.getTimeSlotId(), newSlotId,
                info.getPtEmployeeId(), info.getPtEmployeeId(), accountId);
    }

    /** [DOI-BUOI-POST][AUDIT] Ghi nhật ký kiểm toán (AuditLogs): ai đổi TimeSlotId cũ → mới. */
    public void insertAuditLog(Connection connection, PtSessionInfo info, int newSlotId, int accountId)
            throws SQLException {
        String sql = """
                INSERT INTO AuditLogs (AccountId, ActingRole, Action, EntityName, EntityId,
                                       FieldChanged, OldValue, NewValue)
                VALUES (?, 'Customer', N'Đổi buổi PT', 'Sessions', ?, 'TimeSlotId',
                        CAST(? AS NVARCHAR(1000)), CAST(? AS NVARCHAR(1000)))
                """;
        executeUpdate(connection, sql, accountId, info.getSessionId(), info.getTimeSlotId(), newSlotId);
    }

    // =====================================================================
    // HÀM PHỤ
    // =====================================================================

    /** Chạy UPDATE/INSERT với các tham số số nguyên. Trả số dòng bị ảnh hưởng. */
    private int executeUpdate(Connection connection, String sql, int... values) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) {
                statement.setInt(i + 1, values[i]);
            }
            return statement.executeUpdate();
        }
    }

    /** Chạy câu SELECT COUNT(*) đã gán tham số → true nếu > 0. */
    private boolean countIsPositive(PreparedStatement statement) throws SQLException {
        try (ResultSet rs = statement.executeQuery()) {
            rs.next();
            return rs.getInt(1) > 0;
        }
    }

    /** Chạy câu COUNT có 2 dấu ?: customerId, date → true nếu > 0. */
    private boolean countByCustomerAndDate(Connection connection, String sql, int customerId, LocalDate date)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, customerId);
            statement.setDate(2, Date.valueOf(date));
            return countIsPositive(statement);
        }
    }
}
