package com.gms.dao;

import com.gms.dto.SessionAutoCloseData;
import com.gms.entity.Package;
import com.gms.entity.Session;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO của JOB-04: (1) tìm mã buổi, (2) đọc dữ liệu một buổi, (3) ghi kết quả buổi đó.
 * Service gọi ba hàm theo thứ tự này và quản lý transaction; DAO chỉ đọc/ghi database.
 */
public class AutoCloseSessionDAO {

    /**
     * Hàm 1 — tìm các buổi cần xử lý; chỉ trả mã, chưa đọc điều kiện hay sửa buổi.
     * @param today ngày hiện tại theo giờ VN, do Service lấy một lần
     * @return List<Integer> gồm các SessionId; ví dụ [91, 105], hoặc [] nếu không có
     */
    public List<Integer> findOverdueSessionIds(Connection connection, LocalDate today) throws SQLException {
        // B1: chọn buổi Scheduled có ngày tập trước today, kể cả buổi sót từ nhiều ngày trước.
        String sql = """
                SELECT s.SessionId
                FROM Sessions s
                JOIN TimeSlots t ON t.TimeSlotId = s.TimeSlotId
                WHERE s.Status = 'Scheduled'
                  AND t.SlotDate < ?
                ORDER BY t.SlotDate, s.SessionId
                """;

        List<Integer> sessionIds = new ArrayList<>();

        // B2: mỗi dòng SQL trả một SessionId; add đưa mã đó vào danh sách Java.
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


    /**
     * Hàm 2 — Service đưa một SessionId từ hàm 1 vào; DAO tạo DTO của đúng buổi đó.
     * DTO mang dữ liệu gói, bảo lưu và check-in; Service mới quyết định kết quả.
     * Trả null khi buổi đã đổi trạng thái hoặc ngày tập chưa qua.
     */
    public SessionAutoCloseData findDataForClosing(Connection connection, int sessionId, LocalDate today)
            throws SQLException {
        // B1: đọc dữ kiện của buổi tại ngày tập. CASE/EXISTS trả 1 nếu có, 0 nếu không.
        // pt là gói PT tạo ra buổi; gym là Membership/DayPass dùng để vào gym.
        // Expired/Used vẫn có thể đã hợp lệ vào ngày tập vì JOB chạy sau ngày đó.
        String sql = """
                SELECT s.SessionId, t.SlotDate, pt.SubscriptionType, o.PartnerCustomerId,
                       -- Khách đứng tên: membership, DayPass và bảo lưu tại ngày tập.
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
                WHERE s.SessionId = ?
                  AND s.Status = 'Scheduled'
                  AND t.SlotDate < ?
                """;

        // B2: đọc đúng sessionId trong transaction của buổi; nếu không còn dòng thì Service bỏ qua.
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, sessionId);
            statement.setDate(2, Date.valueOf(today));

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }

                // B3: đổi 'PTCouple' từ DB thành Package.Type.PT_COUPLE; kiểm tra partner của gói đôi.
                Package.Type ptSubscriptionType = Package.Type.fromDbValue(
                        resultSet.getString("SubscriptionType"));
                if (ptSubscriptionType == null || !ptSubscriptionType.isPtService()) {
                    throw new SQLException("Loại gói không hợp lệ cho JOB-04, SessionId=" + sessionId);
                }

                int partnerCustomerId = resultSet.getInt("PartnerCustomerId");
                // getInt trả 0 cả khi cột NULL; wasNull() cho biết có thật sự thiếu partner hay không.
                boolean hasPartner = !resultSet.wasNull();
                if (ptSubscriptionType == Package.Type.PT_COUPLE && !hasPartner) {
                    throw new SQLException("Gói PT đôi thiếu PartnerCustomerId, SessionId=" + sessionId);
                }

                // B4: lấy các cột vừa đọc để tạo một DTO; Service sẽ xét DTO này sau.
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
                        resultSet.getBoolean("HasCheckInOnSessionDate"));
            }
        }
    }


    /**
     * Hàm 3 — Service truyền kết quả đã quyết định; DAO ghi vào đúng một buổi.
     * Trả true nếu UPDATE đổi được một buổi, false nếu buổi không còn Scheduled.
     * Service dùng kết quả này để chọn commit và ghi AuditLog trong cùng transaction.
     */
    public boolean closeScheduledSession(Connection connection, int sessionId, LocalDate today,
            Session.Status status, Session.MissedReason missedReason) throws SQLException {
        // B1: WHERE kiểm tra lại Scheduled, tránh ghi đè buổi đã được chốt/hủy.
        String sql = """
                UPDATE s
                SET Status = ?,
                    MissedReason = ?,
                    IsAutoClosed = 1,
                    AttendanceMarkedAt = SYSDATETIME()
                FROM Sessions s
                JOIN TimeSlots t ON t.TimeSlotId = s.TimeSlotId
                WHERE s.SessionId = ?
                  AND s.Status = 'Scheduled'
                  AND t.SlotDate < ?
                """;

        // B2: Status dùng giá trị DB của enum; CheckedIn có MissedReason = NULL.
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status.getDbValue());

            if (missedReason == null) {
                statement.setNull(2, Types.VARCHAR);
            } else {
                statement.setString(2, missedReason.getDbValue());
            }

            statement.setInt(3, sessionId);
            statement.setDate(4, Date.valueOf(today));

            // B3: trả số dòng đã đổi thành true/false; DAO chưa tự commit transaction.
            return statement.executeUpdate() == 1;
        }
    }
}
