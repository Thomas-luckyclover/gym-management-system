package com.gms.dao;

import com.gms.dto.PtAssignedPackageView;
import com.gms.dto.PtClientPackageView;
import com.gms.dto.PtCommissionSummaryView;
import com.gms.dto.PtRatingView;
import com.gms.dto.PtSessionView;
import com.gms.entity.Package;
import com.gms.entity.Session;
import com.gms.util.DBContext;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO màn PT DASHBOARD — chỉ đọc (SELECT), không ghi gì.
 */
public class PtDashboardDAO {
    // 1 buổi Scheduled rơi vào ngày phòng nghỉ (kết quả findClosedDaySessions).
    public record ClosedDaySession(int sessionId, int customerId, String customerName,
            int subscriptionId, Package.Type subscriptionType, LocalDate ptEndDate,
            LocalDate slotDate, LocalTime startTime) { }

    // Lịch dạy: Sessions JOIN TimeSlots, Subscriptions, Orders, khách (+ người tập cùng nếu PTCouple)
    // của PT trong [fromDate, toDate]. Trả về: List<PtSessionView> (Service chia theo ngày).
    public List<PtSessionView> findSessions(int ptEmployeeId, LocalDate fromDate, LocalDate toDate)
            throws SQLException {
        String sql = """
                SELECT s.SessionId, t.SlotDate, t.StartTime, t.EndTime,
                       p.FullName AS CustomerName, pp.FullName AS PartnerName,
                       sub.SubscriptionType, s.Status, s.MissedReason
                FROM Sessions s
                JOIN TimeSlots t ON t.TimeSlotId = s.TimeSlotId
                JOIN Subscriptions sub ON sub.SubscriptionId = s.SubscriptionId
                JOIN Orders o ON o.OrderId = sub.OrderId
                JOIN Customers c ON c.CustomerId = sub.CustomerId
                JOIN Persons p ON p.PersonId = c.PersonId
                LEFT JOIN Customers pc ON pc.CustomerId = o.PartnerCustomerId
                    AND sub.SubscriptionType = 'PTCouple'
                LEFT JOIN Persons pp ON pp.PersonId = pc.PersonId
                WHERE s.PtEmployeeId = ?
                  AND t.SlotDate BETWEEN ? AND ?
                ORDER BY t.SlotDate DESC, t.StartTime, s.SessionId DESC
                """;

        List<PtSessionView> sessions = new ArrayList<>();

        try (Connection connection = DBContext.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            // ? 1 = PT · ? 2, ? 3 = từ ngày, tới ngày
            statement.setInt(1, ptEmployeeId);
            statement.setDate(2, Date.valueOf(fromDate));
            statement.setDate(3, Date.valueOf(toDate));

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    int sessionId = resultSet.getInt("SessionId");
                    LocalDate slotDate = resultSet.getDate("SlotDate").toLocalDate();
                    LocalTime startTime = resultSet.getTime("StartTime").toLocalTime();
                    LocalTime endTime = resultSet.getTime("EndTime").toLocalTime();

                    String customerName = resultSet.getString("CustomerName");
                    String partnerName = resultSet.getString("PartnerName");

                    String subscriptionTypeValue = resultSet.getString("SubscriptionType");
                    String statusValue = resultSet.getString("Status");
                    String missedReasonValue = resultSet.getString("MissedReason");
                    Package.Type subscriptionType = Package.Type.fromDbValue(subscriptionTypeValue);
                    Session.Status status = Session.Status.fromDbValue(statusValue);
                    Session.MissedReason missedReason = Session.MissedReason.fromDbValue(missedReasonValue);

                    if (subscriptionType == null || status == null
                            || (missedReasonValue != null && missedReason == null)) {
                        throw new SQLException("Giá trị loại gói hoặc trạng thái không hợp lệ, SessionId=" + sessionId);
                    }

                    PtSessionView session = new PtSessionView(
                            sessionId, slotDate, startTime, endTime,
                            customerName, partnerName, subscriptionType, status, missedReason);
                    sessions.add(session);
                }
            }
        }

        return sessions;
    }

    // Buổi Scheduled của PT trong khoảng ngày mà OperatingHourExceptions đánh dấu nghỉ cả ngày.
    // Trả về: List<ClosedDaySession> → Service tạo việc "closedDay".
    public List<ClosedDaySession> findClosedDaySessions(int ptEmployeeId,
            LocalDate fromDate, LocalDate toDate) throws SQLException {
        String sql = """
                SELECT s.SessionId, sub.CustomerId, p.FullName AS CustomerName,
                       sub.SubscriptionId, sub.SubscriptionType, sub.EndDate,
                       t.SlotDate, t.StartTime
                FROM Sessions s
                JOIN TimeSlots t ON t.TimeSlotId = s.TimeSlotId
                JOIN Subscriptions sub ON sub.SubscriptionId = s.SubscriptionId
                JOIN Customers c ON c.CustomerId = sub.CustomerId
                JOIN Persons p ON p.PersonId = c.PersonId
                WHERE s.Status = 'Scheduled' AND s.PtEmployeeId = ?
                  AND t.SlotDate BETWEEN ? AND ?
                  AND EXISTS (
                      SELECT 1 FROM OperatingHourExceptions e
                      WHERE e.ApplyDate = t.SlotDate AND e.IsClosedAllDay = 1
                  )
                ORDER BY t.SlotDate, t.StartTime, s.SessionId
                """;
        List<ClosedDaySession> rows = new ArrayList<>();

        try (Connection connection = DBContext.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, ptEmployeeId);
            statement.setDate(2, Date.valueOf(fromDate));
            statement.setDate(3, Date.valueOf(toDate));

            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    Package.Type type = Package.Type.fromDbValue(rs.getString("SubscriptionType"));
                    if (type == null) {
                        throw new SQLException("Loại gói không hợp lệ, SessionId=" + rs.getInt("SessionId"));
                    }
                    rows.add(new ClosedDaySession(rs.getInt("SessionId"), rs.getInt("CustomerId"),
                            rs.getString("CustomerName"), rs.getInt("SubscriptionId"), type,
                            rs.getDate("EndDate").toLocalDate(), rs.getDate("SlotDate").toLocalDate(),
                            rs.getTime("StartTime").toLocalTime()));
                }
            }
        }
        return rows;
    }

    // Khách × gói PT × ngày có buổi (14 ngày tới, buổi chưa bắt đầu, gói Active, đơn Paid):
    //   WITH SessionDays (ngày có buổi của từng gói) → ClientPackages (tách người đứng tên + người tập cùng)
    //   mỗi dòng kèm: có membership phủ ngày đó? đang bảo lưu? hạn membership gần nhất, hạn bảo lưu, số buổi còn lại.
    // Trả về: List<PtClientPackageView> (Service lọc ra dòng có vấn đề).
    public List<PtClientPackageView> findClientPackages(int ptEmployeeId,
            LocalDate fromDate, LocalDate toDate, LocalDateTime now) throws SQLException {
        String sql = """
                WITH QueryTime AS (
                    SELECT CAST(? AS DATETIME2) AS Now
                ),
                -- B1.1: chọn gói và ngày có buổi của đúng PT trong phạm vi cảnh báo.
                -- DISTINCT giữ một dòng gói/ngày nếu gói có nhiều buổi cùng ngày.
                SessionDays AS (
                    SELECT DISTINCT sub.SubscriptionId AS PtSubscriptionId,
                           sub.CustomerId AS HolderCustomerId, o.PartnerCustomerId,
                           sub.SubscriptionType AS PtSubscriptionType,
                           sub.EndDate AS PtEndDate, t.SlotDate AS SessionDate
                    FROM Sessions s
                    JOIN TimeSlots t ON t.TimeSlotId = s.TimeSlotId
                    JOIN Subscriptions sub ON sub.SubscriptionId = s.SubscriptionId
                    JOIN Orders o ON o.OrderId = sub.OrderId
                    CROSS JOIN QueryTime q
                    WHERE s.PtEmployeeId = ?
                      AND sub.SubscriptionType IN ('PTSingle', 'PTCouple')
                      AND sub.Status = 'Active'
                      AND o.Status = 'Paid'
                      AND t.SlotDate BETWEEN ? AND ?
                      AND (t.SlotDate > CAST(q.Now AS DATE)
                           OR (t.SlotDate = CAST(q.Now AS DATE)
                               AND t.StartTime > CAST(q.Now AS TIME)))
                      AND (s.Status = 'Scheduled'
                           OR (s.Status = 'Missed' AND s.MissedReason = 'MembershipFrozen'))
                ),
                -- B1.2: holder luôn có một dòng; gói đôi thêm một dòng cho partner.
                ClientPackages AS (
                    SELECT PtSubscriptionId, HolderCustomerId AS CustomerId,
                           PtSubscriptionType, PtEndDate, SessionDate
                    FROM SessionDays
                    UNION ALL
                    SELECT PtSubscriptionId, PartnerCustomerId AS CustomerId,
                           PtSubscriptionType, PtEndDate, SessionDate
                    FROM SessionDays
                    WHERE PtSubscriptionType = 'PTCouple' AND PartnerCustomerId IS NOT NULL
                )
                SELECT cp.CustomerId, p.FullName AS CustomerName,
                       cp.PtSubscriptionId, cp.PtSubscriptionType, cp.PtEndDate,
                       futureSessions.ScheduledSessionCount, cp.SessionDate,
                       selectedMembership.EndDate AS MembershipEndDate,
                       -- B1.3: chỉ hỏi có membership bao phủ ngày tập, chưa xét bảo lưu.
                       CAST(CASE WHEN EXISTS (
                           SELECT 1 FROM Subscriptions m
                           WHERE m.CustomerId = cp.CustomerId
                             AND m.SubscriptionType = 'Membership'
                             AND m.Status IN ('Active', 'Frozen')
                             AND m.StartDate <= cp.SessionDate AND m.EndDate >= cp.SessionDate
                       ) THEN 1 ELSE 0 END AS BIT) AS HasMembershipOnSessionDate,
                       CAST(CASE WHEN freezePeriod.FreezeEndDate IS NOT NULL
                           THEN 1 ELSE 0 END AS BIT) AS FrozenOnSessionDate,
                       freezePeriod.FreezeEndDate
                FROM ClientPackages cp
                JOIN Customers c ON c.CustomerId = cp.CustomerId
                JOIN Persons p ON p.PersonId = c.PersonId
                CROSS JOIN QueryTime q
                -- B1.4: COUNT theo gói, không giới hạn 14 ngày và không JOIN hai người vào số đếm.
                OUTER APPLY (
                    SELECT COUNT(*) AS ScheduledSessionCount
                    FROM Sessions futureSession
                    JOIN TimeSlots futureSlot ON futureSlot.TimeSlotId = futureSession.TimeSlotId
                    WHERE futureSession.SubscriptionId = cp.PtSubscriptionId
                      AND futureSession.Status = 'Scheduled'
                      AND (futureSlot.SlotDate > CAST(q.Now AS DATE)
                           OR (futureSlot.SlotDate = CAST(q.Now AS DATE)
                               AND futureSlot.StartTime > CAST(q.Now AS TIME)))
                ) futureSessions
                -- B1.5: ưu tiên gói bao phủ ngày tập; thiếu thì lấy hạn gần nhất đã qua để giải thích.
                -- OUTER APPLY vẫn giữ dòng khách nếu không tìm thấy membership (EndDate sẽ null).
                OUTER APPLY (
                    SELECT TOP (1) m.EndDate
                    FROM Subscriptions m
                    WHERE m.CustomerId = cp.CustomerId
                      AND m.SubscriptionType = 'Membership'
                      AND ((m.Status IN ('Active', 'Frozen')
                            AND m.StartDate <= cp.SessionDate AND m.EndDate >= cp.SessionDate)
                           OR (m.Status IN ('Active', 'Frozen', 'Expired')
                               AND m.EndDate < cp.SessionDate))
                    ORDER BY CASE WHEN m.Status IN ('Active', 'Frozen')
                                       AND m.StartDate <= cp.SessionDate AND m.EndDate >= cp.SessionDate
                                  THEN 0 ELSE 1 END,
                             m.EndDate DESC, m.SubscriptionId DESC
                ) selectedMembership
                -- B1.6: kỳ bảo lưu được xét riêng trên tất cả membership của chính khách.
                OUTER APPLY (
                    SELECT MAX(COALESCE(f.ActualEndDate, f.PlannedEndDate)) AS FreezeEndDate
                    FROM FreezeRecords f
                    JOIN Subscriptions m ON m.SubscriptionId = f.SubscriptionId
                    WHERE m.CustomerId = cp.CustomerId
                      AND m.SubscriptionType = 'Membership'
                      AND f.StartDate <= cp.SessionDate
                      AND COALESCE(f.ActualEndDate, f.PlannedEndDate) >= cp.SessionDate
                ) freezePeriod
                ORDER BY cp.SessionDate, cp.PtSubscriptionId, cp.CustomerId
                """;

        List<PtClientPackageView> clientPackages = new ArrayList<>();

        try (Connection connection = DBContext.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            // ? 1 = bây giờ · ? 2 = PT · ? 3, ? 4 = từ ngày, tới ngày
            statement.setTimestamp(1, Timestamp.valueOf(now));
            statement.setInt(2, ptEmployeeId);
            statement.setDate(3, Date.valueOf(fromDate));
            statement.setDate(4, Date.valueOf(toDate));

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    int customerId = resultSet.getInt("CustomerId");
                    String customerName = resultSet.getString("CustomerName");
                    int ptSubscriptionId = resultSet.getInt("PtSubscriptionId");
                    Package.Type ptSubscriptionType = Package.Type.fromDbValue(
                            resultSet.getString("PtSubscriptionType"));
                    if (ptSubscriptionType != Package.Type.PT_SINGLE
                            && ptSubscriptionType != Package.Type.PT_COUPLE) {
                        throw new SQLException("Loại gói PT không hợp lệ, SubscriptionId=" + ptSubscriptionId);
                    }
                    LocalDate ptEndDate = resultSet.getDate("PtEndDate").toLocalDate();
                    int scheduledSessionCount = resultSet.getInt("ScheduledSessionCount");

                    LocalDate sessionDate = resultSet.getDate("SessionDate").toLocalDate();
                    Date membershipEndDateValue = resultSet.getDate("MembershipEndDate");
                    LocalDate membershipEndDate = null;
                    if (membershipEndDateValue != null) {
                        membershipEndDate = membershipEndDateValue.toLocalDate();
                    }
                    boolean hasMembershipOnSessionDate = resultSet.getBoolean("HasMembershipOnSessionDate");
                    boolean frozenOnSessionDate = resultSet.getBoolean("FrozenOnSessionDate");
                    Date freezeEndDateValue = resultSet.getDate("FreezeEndDate");
                    LocalDate freezeEndDate = null;
                    if (freezeEndDateValue != null) {
                        freezeEndDate = freezeEndDateValue.toLocalDate();
                    }

                    PtClientPackageView clientPackage = new PtClientPackageView(
                            customerId, customerName, ptSubscriptionId, ptSubscriptionType,
                            ptEndDate, scheduledSessionCount, sessionDate, membershipEndDate,
                            hasMembershipOnSessionDate, frozenOnSessionDate, freezeEndDate);
                    clientPackages.add(clientPackage);
                }
            }
        }

        return clientPackages;
    }

    // Khách phụ trách: Subscriptions (đơn Paid) JOIN khách (+ người tập cùng):
    //   gói PT Active do PT này phụ trách, chưa quá hạn · HOẶC buổi lẻ có buổi Scheduled sắp dạy với PT này
    //   · HOẶC gói nằm trong attentionPackageIds (IN (?, ?, …) ghép theo số phần tử).
    // Mỗi dòng đếm thêm số buổi Scheduled chưa bắt đầu. Trả về: List<PtAssignedPackageView>.
    public List<PtAssignedPackageView> findAssignedPackages(int ptEmployeeId, LocalDateTime now, List<Integer> attentionPackageIds)
            throws SQLException {
        String sql = """
                WITH QueryTime AS (
                    SELECT CAST(? AS DATETIME2) AS Now
                )
                SELECT sub.SubscriptionId, p.FullName AS CustomerName, p.Phone AS CustomerPhone,
                       pp.FullName AS PartnerName, pp.Phone AS PartnerPhone,
                       sub.SubscriptionType AS PtSubscriptionType,
                       sub.EndDate AS PtEndDate,
                       futureSessions.ScheduledSessionCount
                FROM Subscriptions sub
                JOIN Orders o ON o.OrderId = sub.OrderId
                JOIN Customers c ON c.CustomerId = sub.CustomerId
                JOIN Persons p ON p.PersonId = c.PersonId
                LEFT JOIN Customers pc ON pc.CustomerId = o.PartnerCustomerId
                    AND sub.SubscriptionType = 'PTCouple'
                LEFT JOIN Persons pp ON pp.PersonId = pc.PersonId
                CROSS JOIN QueryTime q
                -- B1.1: đếm trên toàn gói, bao gồm buổi đã giao cho PT khác; không giới hạn 7/14 ngày.
                OUTER APPLY (
                    SELECT COUNT(*) AS ScheduledSessionCount
                    FROM Sessions s
                    JOIN TimeSlots t ON t.TimeSlotId = s.TimeSlotId
                    WHERE s.SubscriptionId = sub.SubscriptionId
                      AND s.Status = 'Scheduled'
                      AND (t.SlotDate > CAST(q.Now AS DATE)
                           OR (t.SlotDate = CAST(q.Now AS DATE)
                               AND t.StartTime > CAST(q.Now AS TIME)))
                ) futureSessions
                WHERE o.Status = 'Paid'
                  AND ((sub.Status = 'Active' AND (
                      -- B1.2: gói PT lọc theo người phụ trách gói, không theo PT dạy từng buổi.
                      (sub.SubscriptionType IN ('PTSingle', 'PTCouple')
                       AND sub.PtEmployeeId = ?
                       AND sub.EndDate >= CAST(q.Now AS DATE))   -- gói đã quá hạn (chưa chạy job) không còn là khách phụ trách
                      OR
                      -- B1.3: buổi lẻ theo PT đang được giao buổi Scheduled chưa bắt đầu.
                      -- EXISTS chỉ kiểm tra có buổi phù hợp; không tạo thêm dòng cho gói.
                      (sub.SubscriptionType = 'SingleSession' AND EXISTS (
                          SELECT 1
                          FROM Sessions singleSession
                          JOIN TimeSlots singleSlot ON singleSlot.TimeSlotId = singleSession.TimeSlotId
                          WHERE singleSession.SubscriptionId = sub.SubscriptionId
                            AND singleSession.PtEmployeeId = ?
                            AND singleSession.Status = 'Scheduled'
                            AND (singleSlot.SlotDate > CAST(q.Now AS DATE)
                                 OR (singleSlot.SlotDate = CAST(q.Now AS DATE)
                                     AND singleSlot.StartTime > CAST(q.Now AS TIME)))
                      ))
                  )
                  ) OR sub.SubscriptionId IN (%s))
                ORDER BY p.FullName, sub.EndDate, sub.SubscriptionId
                """.formatted(attentionPackageIds.isEmpty() ? "NULL" : String.join(",", java.util.Collections.nCopies(attentionPackageIds.size(), "?")));

        List<PtAssignedPackageView> assignedPackages = new ArrayList<>();

        try (Connection connection = DBContext.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            // ? 1 = bây giờ · ? 2 = PT (gói PT) · ? 3 = PT (buổi lẻ) · ? 4… = Id gói cần chú ý
            statement.setTimestamp(1, Timestamp.valueOf(now));
            statement.setInt(2, ptEmployeeId);
            statement.setInt(3, ptEmployeeId);

            for (int i = 0; i < attentionPackageIds.size(); i++) {
                statement.setInt(i + 4, attentionPackageIds.get(i));
            }

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String customerName = resultSet.getString("CustomerName");
                    String partnerName = resultSet.getString("PartnerName");

                    Package.Type ptSubscriptionType = Package.Type.fromDbValue(
                            resultSet.getString("PtSubscriptionType"));
                    if (ptSubscriptionType != Package.Type.PT_SINGLE
                            && ptSubscriptionType != Package.Type.PT_COUPLE
                            && ptSubscriptionType != Package.Type.SINGLE_SESSION) {
                        int subscriptionId = resultSet.getInt("SubscriptionId");
                        throw new SQLException("Loại gói không hợp lệ trong Khách phụ trách, SubscriptionId="
                                + subscriptionId);
                    }

                    LocalDate ptEndDate = resultSet.getDate("PtEndDate").toLocalDate();
                    int scheduledSessionCount = resultSet.getInt("ScheduledSessionCount");

                    PtAssignedPackageView assignedPackage = new PtAssignedPackageView(
                            customerName, partnerName, ptSubscriptionType, ptEndDate, scheduledSessionCount);
                    assignedPackage.setPtSubscriptionId(resultSet.getInt("SubscriptionId"));
                    assignedPackage.setCustomerPhone(resultSet.getString("CustomerPhone"));
                    assignedPackage.setPartnerPhone(resultSet.getString("PartnerPhone"));
                    assignedPackages.add(assignedPackage);
                }
            }
        }

        return assignedPackages;
    }

    // Kết quả tháng: các buổi của PT có ngày tập trong [monthStart, nextMonthStart):
    //   đếm theo kết quả (CheckedIn, Missed theo lý do, Cancelled) + cộng hoa hồng 3 nhóm:
    //   đã chốt · đã qua giờ nhưng còn Scheduled (chờ JOB-04) · chưa tới giờ (dự kiến).
    // Trả về: PtCommissionSummaryView (Service tính thêm tổng + %).
    public PtCommissionSummaryView findCommissionSummary(int ptEmployeeId,
            LocalDate monthStart, LocalDate nextMonthStart, LocalDateTime now)
            throws SQLException {
        String sql = """
                WITH MonthlySessions AS (
                    SELECT s.Status, s.MissedReason, s.CancelType,
                           CAST(s.CommissionAmount AS BIGINT) AS CommissionAmount,
                           CASE WHEN t.SlotDate < CAST(? AS DATE)
                                     OR (t.SlotDate = CAST(? AS DATE)
                                         AND t.EndTime <= CAST(? AS TIME))
                                THEN 1 ELSE 0 END AS HasEnded
                    FROM Sessions s
                    JOIN TimeSlots t ON t.TimeSlotId = s.TimeSlotId
                    WHERE s.PtEmployeeId = ?
                      AND t.SlotDate >= ?
                      AND t.SlotDate < ?
                )
                SELECT
                    COALESCE(SUM(CASE WHEN Status = 'CheckedIn' THEN 1 ELSE 0 END), 0) AS CheckedInCount,
                    COALESCE(SUM(CASE WHEN Status = 'Missed' AND MissedReason = 'NoCheckIn'
                                      THEN 1 ELSE 0 END), 0) AS NoCheckInCount,
                    COALESCE(SUM(CASE WHEN Status = 'Missed' AND MissedReason = 'NoMembership'
                                      THEN 1 ELSE 0 END), 0) AS NoMembershipCount,
                    COALESCE(SUM(CASE WHEN Status = 'Missed' AND MissedReason = 'MembershipFrozen'
                                      THEN 1 ELSE 0 END), 0) AS MembershipFrozenCount,
                    COALESCE(SUM(CASE WHEN Status = 'Cancelled' THEN 1 ELSE 0 END), 0) AS CancelledCount,
                    COALESCE(SUM(CASE WHEN Status IN ('CheckedIn', 'Missed')
                                           OR (Status = 'Cancelled' AND CancelType IN ('ByPt', 'ByCustomer'))
                                      THEN CommissionAmount ELSE CAST(0 AS BIGINT) END), 0)
                        AS FinalizedCommissionAmount,
                    COALESCE(SUM(CASE WHEN Status = 'Scheduled' AND HasEnded = 1
                                      THEN 1 ELSE 0 END), 0) AS PendingSessionCount,
                    COALESCE(SUM(CASE WHEN Status = 'Scheduled' AND HasEnded = 1
                                      THEN CommissionAmount ELSE CAST(0 AS BIGINT) END), 0)
                        AS PendingCommissionAmount,
                    COALESCE(SUM(CASE WHEN Status = 'Scheduled' AND HasEnded = 0
                                      THEN 1 ELSE 0 END), 0) AS ProjectedSessionCount,
                    COALESCE(SUM(CASE WHEN Status = 'Scheduled' AND HasEnded = 0
                                      THEN CommissionAmount ELSE CAST(0 AS BIGINT) END), 0)
                        AS ProjectedCommissionAmount
                FROM MonthlySessions
                """;

        try (Connection connection = DBContext.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            // ? 1–3 = bây giờ (tính buổi đã kết thúc chưa) · ? 4 = PT · ? 5, ? 6 = đầu tháng, đầu tháng sau
            statement.setTimestamp(1, Timestamp.valueOf(now));
            statement.setTimestamp(2, Timestamp.valueOf(now));
            statement.setTimestamp(3, Timestamp.valueOf(now));
            statement.setInt(4, ptEmployeeId);
            statement.setDate(5, Date.valueOf(monthStart));
            statement.setDate(6, Date.valueOf(nextMonthStart));

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SQLException("Không đọc được tổng hợp buổi PT trong tháng.");
                }

                int checkedInCount = resultSet.getInt("CheckedInCount");
                int noCheckInCount = resultSet.getInt("NoCheckInCount");
                int noMembershipCount = resultSet.getInt("NoMembershipCount");
                int membershipFrozenCount = resultSet.getInt("MembershipFrozenCount");
                int cancelledCount = resultSet.getInt("CancelledCount");
                long finalizedCommissionAmount = resultSet.getLong("FinalizedCommissionAmount");

                int pendingSessionCount = resultSet.getInt("PendingSessionCount");
                long pendingCommissionAmount = resultSet.getLong("PendingCommissionAmount");
                int projectedSessionCount = resultSet.getInt("ProjectedSessionCount");
                long projectedCommissionAmount = resultSet.getLong("ProjectedCommissionAmount");

                return new PtCommissionSummaryView(
                        checkedInCount, noCheckInCount, noMembershipCount,
                        membershipFrozenCount, cancelledCount, finalizedCommissionAmount,
                        pendingSessionCount, pendingCommissionAmount,
                        projectedSessionCount, projectedCommissionAmount);
            }
        }
    }

    // Feedbacks của PT trong tháng: số khách đánh giá + tổng điểm. Trả về: PtRatingView.
    public PtRatingView findRatingForMonth(int ptEmployeeId, LocalDate ratingMonth)
            throws SQLException {
        String sql = """
                SELECT COUNT(DISTINCT CustomerId) AS RatingCustomerCount,
                       COALESCE(SUM(CAST(Rating AS BIGINT)), 0) AS RatingTotal
                FROM Feedbacks
                WHERE RatedPtEmployeeId = ? AND RatingMonth = ?
                """;

        try (Connection connection = DBContext.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, ptEmployeeId);
            statement.setDate(2, Date.valueOf(ratingMonth));

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SQLException("Không đọc được điểm PT trong tháng.");
                }

                return new PtRatingView(resultSet.getInt("RatingCustomerCount"),
                        resultSet.getLong("RatingTotal"));
            }
        }
    }
}
