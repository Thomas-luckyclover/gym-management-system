package com.gms.dao;

import com.gms.dto.SessionAutoCloseData;
import com.gms.entity.Package;
import com.gms.entity.Session;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO của JOB-04 — đọc buổi quá hạn, đọc dữ liệu để chốt, ghi kết quả vào bảng Sessions.
 */
public class AutoCloseSessionDAO {
    // Đọc Sessions JOIN TimeSlots: buổi Scheduled có SlotDate < hôm nay, xếp theo ngày.
    // Trả về: List<Integer> SessionId (chưa khóa — Service khóa từng buổi lúc chốt).
    public List<Integer> findOverdueSessionIds(Connection connection, LocalDate today) throws SQLException {
        String sql = """
                SELECT s.SessionId
                FROM Sessions s
                JOIN TimeSlots t ON t.TimeSlotId = s.TimeSlotId
                WHERE s.Status = 'Scheduled'
                  AND t.SlotDate < ?
                ORDER BY t.SlotDate, s.SessionId
                """;

        List<Integer> sessionIds = new ArrayList<>();

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setDate(1, Date.valueOf(today));

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    sessionIds.add(resultSet.getInt("SessionId"));
                }
            }
        }

        return sessionIds;
    }

    // Đọc 1 buổi + mọi thứ cần để chốt, KHÓA dòng buổi tới hết transaction (UPDLOCK, HOLDLOCK).
    // Bảng: Sessions, TimeSlots, Subscriptions (gói PT + gói gym), Orders (người tập cùng), FreezeRecords, CheckIns,
    //       OperatingHourExceptions (ngày nghỉ / giờ riêng) + SystemConfigs P-17 (giờ mở cửa mặc định).
    // Trả về: SessionAutoCloseData; null = buổi không còn Scheduled / chưa qua ngày.
    public SessionAutoCloseData findDataForClosing(Connection connection, int sessionId, LocalDate today)
            throws SQLException {
        String sql = """
                SELECT s.SessionId, t.SlotDate, pt.SubscriptionType, o.PartnerCustomerId,
                       -- Khách đứng tên: membership, DayPass và bảo lưu tại ngày tập.
                       t.StartTime, t.EndTime,
                       CASE WHEN ex.IsClosedAllDay = 1 THEN 1 ELSE 0 END AS GymClosedAllDay,
                       COALESCE(ex.OpenTime, TRY_CONVERT(time(0), LEFT(cfg.ConfigValue, 5))) AS GymOpenTime,
                       COALESCE(ex.CloseTime, TRY_CONVERT(time(0), RIGHT(cfg.ConfigValue, 5))) AS GymCloseTime,
                       CASE WHEN EXISTS (
                           SELECT 1 FROM Subscriptions gym
                           WHERE gym.CustomerId = pt.CustomerId
                             AND gym.SubscriptionType = 'Membership'
                             AND gym.Status IN ('Active', 'Frozen', 'Expired')
                             AND t.SlotDate BETWEEN gym.StartDate AND gym.EndDate
                       ) THEN 1 ELSE 0 END AS HolderHasMembership,
                       CASE WHEN EXISTS (
                           SELECT 1 FROM Subscriptions gym
                           WHERE gym.CustomerId = pt.CustomerId
                             AND gym.SubscriptionType = 'DayPass'
                             AND gym.Status IN ('Active', 'Used', 'Expired')
                             AND t.SlotDate BETWEEN gym.StartDate AND gym.EndDate
                       ) THEN 1 ELSE 0 END AS HolderHasDayPass,
                       CASE WHEN EXISTS (
                           SELECT 1 FROM Subscriptions gym
                           JOIN FreezeRecords freeze ON freeze.SubscriptionId = gym.SubscriptionId
                           WHERE gym.CustomerId = pt.CustomerId
                             AND gym.SubscriptionType = 'Membership'
                             AND t.SlotDate BETWEEN freeze.StartDate
                                 AND COALESCE(freeze.ActualEndDate, freeze.PlannedEndDate)
                       ) THEN 1 ELSE 0 END AS HolderFrozen,
                       -- Người tập cùng: chỉ có ý nghĩa khi loại gói là PTCouple.
                       CASE WHEN EXISTS (
                           SELECT 1 FROM Subscriptions gym
                           WHERE gym.CustomerId = o.PartnerCustomerId
                             AND gym.SubscriptionType = 'Membership'
                             AND gym.Status IN ('Active', 'Frozen', 'Expired')
                             AND t.SlotDate BETWEEN gym.StartDate AND gym.EndDate
                       ) THEN 1 ELSE 0 END AS PartnerHasMembership,
                       CASE WHEN EXISTS (
                           SELECT 1 FROM Subscriptions gym
                           WHERE gym.CustomerId = o.PartnerCustomerId
                             AND gym.SubscriptionType = 'DayPass'
                             AND gym.Status IN ('Active', 'Used', 'Expired')
                             AND t.SlotDate BETWEEN gym.StartDate AND gym.EndDate
                       ) THEN 1 ELSE 0 END AS PartnerHasDayPass,
                       CASE WHEN EXISTS (
                           SELECT 1 FROM Subscriptions gym
                           JOIN FreezeRecords freeze ON freeze.SubscriptionId = gym.SubscriptionId
                           WHERE gym.CustomerId = o.PartnerCustomerId
                             AND gym.SubscriptionType = 'Membership'
                             AND t.SlotDate BETWEEN freeze.StartDate
                                 AND COALESCE(freeze.ActualEndDate, freeze.PlannedEndDate)
                       ) THEN 1 ELSE 0 END AS PartnerFrozen,
                       -- Lượt vào gym: holder hoặc partner, từ 00:00 ngày tập đến trước 00:00 hôm sau.
                       CASE WHEN EXISTS (
                           SELECT 1 FROM CheckIns ci
                           JOIN Subscriptions gym ON gym.SubscriptionId = ci.SubscriptionId
                           WHERE gym.SubscriptionType IN ('Membership', 'DayPass')
                             AND (gym.CustomerId = pt.CustomerId
                                  OR (pt.SubscriptionType = 'PTCouple'
                                      AND gym.CustomerId = o.PartnerCustomerId))
                             AND ci.CheckedInAt >= CAST(t.SlotDate AS DATETIME2)
                             AND ci.CheckedInAt < DATEADD(DAY, 1, CAST(t.SlotDate AS DATETIME2))
                       ) THEN 1 ELSE 0 END AS HasCheckInOnSessionDate
                FROM Sessions s WITH (UPDLOCK, HOLDLOCK) -- giữ buổi ổn định tới khi Service kết thúc transaction
                JOIN TimeSlots t ON t.TimeSlotId = s.TimeSlotId
                JOIN Subscriptions pt ON pt.SubscriptionId = s.SubscriptionId
                JOIN Orders o ON o.OrderId = pt.OrderId
                LEFT JOIN OperatingHourExceptions ex ON ex.ApplyDate = t.SlotDate
                LEFT JOIN SystemConfigs cfg ON cfg.ConfigKey = 'P-17'
                WHERE s.SessionId = ?
                  AND s.Status = 'Scheduled'
                  AND t.SlotDate < ?
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            // ? 1 = SessionId · ? 2 = hôm nay (chỉ buổi đã qua ngày)
            statement.setInt(1, sessionId);
            statement.setDate(2, Date.valueOf(today));

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }

                // Kiểm dữ liệu hỏng: gói không phải gói PT → lỗi; gói đôi mà thiếu người tập cùng → lỗi (Service rollback buổi này)
                Package.Type ptSubscriptionType = Package.Type.fromDbValue(
                        resultSet.getString("SubscriptionType"));
                if (ptSubscriptionType == null || !ptSubscriptionType.isPtService()) {
                    throw new SQLException("Loại gói không hợp lệ cho JOB-04, SessionId=" + sessionId);
                }

                int partnerCustomerId = resultSet.getInt("PartnerCustomerId");

                boolean hasPartner = !resultSet.wasNull();
                if (ptSubscriptionType == Package.Type.PT_COUPLE && !hasPartner) {
                    throw new SQLException("Gói PT đôi thiếu PartnerCustomerId, SessionId=" + sessionId);
                }

                // Dòng kết quả → SessionAutoCloseData (các cột 0/1 → boolean)
                return new SessionAutoCloseData(
                        resultSet.getInt("SessionId"),
                        resultSet.getDate("SlotDate").toLocalDate(),
                        ptSubscriptionType,
                        resultSet.getBoolean("HolderHasMembership"),
                        resultSet.getBoolean("HolderHasDayPass"),
                        resultSet.getBoolean("HolderFrozen"),
                        resultSet.getBoolean("PartnerHasMembership"),
                        resultSet.getBoolean("PartnerHasDayPass"),
                        resultSet.getBoolean("PartnerFrozen"),
                        resultSet.getBoolean("HasCheckInOnSessionDate"),
                        resultSet.getBoolean("GymClosedAllDay"),
                        resultSet.getTime("StartTime").toLocalTime(),
                        resultSet.getTime("EndTime").toLocalTime(),
                        toLocalTime(resultSet.getTime("GymOpenTime")),
                        toLocalTime(resultSet.getTime("GymCloseTime")));
            }
        }
    }

    /**
     * HÀM PHỤ — cột time có thể NULL (cấu hình P-17 lỗi) → null.
     */
    private static java.time.LocalTime toLocalTime(Time value) {
        return value == null ? null : value.toLocalTime();
    }

    // Ghi kết quả chốt vào bảng Sessions (chỉ khi buổi VẪN Scheduled và đã qua ngày).
    //   Status / MissedReason / CancelType = kết quả evaluate · IsAutoClosed = 1 · AttendanceMarkedAt = bây giờ
    //   GymClosed → CommissionAmount = 0 · có CancelType → CancelledAt = bây giờ
    // Trả về: true = cập nhật đúng 1 dòng.
    public boolean closeScheduledSession(Connection connection, int sessionId, LocalDate today,
            Session.Status status, Session.MissedReason missedReason,
            Session.CancelType cancelType) throws SQLException {
        String sql = """
                UPDATE s
                SET Status = ?,
                    MissedReason = ?,
                    CancelType = ?,
                    CommissionAmount = CASE WHEN ? = 'GymClosed' THEN 0 ELSE CommissionAmount END,
                    CancelledAt = CASE WHEN ? IS NULL THEN NULL ELSE SYSDATETIME() END,
                    IsAutoClosed = 1,
                    AttendanceMarkedAt = SYSDATETIME()
                FROM Sessions s
                JOIN TimeSlots t ON t.TimeSlotId = s.TimeSlotId
                WHERE s.SessionId = ?
                  AND s.Status = 'Scheduled'
                  AND t.SlotDate < ?
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            // ? 1 Status · ? 2 MissedReason · ? 3 CancelType · ? 4, ? 5 = CancelType (cho 2 câu CASE) · ? 6 SessionId · ? 7 hôm nay
            statement.setString(1, status.getDbValue());

            if (missedReason == null) {
                statement.setNull(2, Types.VARCHAR);
            } else {
                statement.setString(2, missedReason.getDbValue());
            }

            if (cancelType == null) {
                statement.setNull(3, Types.VARCHAR);
            } else {
                statement.setString(3, cancelType.getDbValue());
            }

            if (cancelType == null) {
                statement.setNull(4, Types.VARCHAR);
            } else {
                statement.setString(4, cancelType.getDbValue());
            }

            if (cancelType == null) {
                statement.setNull(5, Types.VARCHAR);
            } else {
                statement.setString(5, cancelType.getDbValue());
            }

            statement.setInt(6, sessionId);
            statement.setDate(7, Date.valueOf(today));

            return statement.executeUpdate() == 1;
        }
    }
}
