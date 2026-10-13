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

/** Đọc dữ liệu riêng cho trang tổng quan PT (UC-32). */
public class PtDashboardDAO {

    /**
     * Lấy các buổi của PT trong khoảng ngày và tạo một PtSessionView cho mỗi buổi.
     * Service truyền EmployeeId của PT đã xác minh từ tài khoản đăng nhập.
     * @param ptEmployeeId PT đang phụ trách buổi, theo Sessions.PtEmployeeId
     * @param fromDate ngày đầu, tính cả ngày này
     * @param toDate ngày cuối, tính cả ngày này
     * @return danh sách theo ngày và giờ; rỗng nếu không có buổi
     * @throws SQLException khi truy vấn lỗi hoặc giá trị enum không hợp lệ
     */
    public List<PtSessionView> findSessions(int ptEmployeeId, LocalDate fromDate, LocalDate toDate)
            throws SQLException {
        // B1: lấy lịch, tên khách và trạng thái từ DB.
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

        // B2: truyền PT và khoảng ngày bằng PreparedStatement.
        try (Connection connection = DBContext.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, ptEmployeeId);
            statement.setDate(2, Date.valueOf(fromDate));
            statement.setDate(3, Date.valueOf(toDate));

            // B3: đọc từng dòng để tạo DTO cho lịch.
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    // B3.1: đọc mã buổi và thời gian hiển thị.
                    int sessionId = resultSet.getInt("SessionId");
                    LocalDate slotDate = resultSet.getDate("SlotDate").toLocalDate();
                    LocalTime startTime = resultSet.getTime("StartTime").toLocalTime();
                    LocalTime endTime = resultSet.getTime("EndTime").toLocalTime();

                    // B3.2: đọc tên khách; gói đơn không có partnerName.
                    String customerName = resultSet.getString("CustomerName");
                    String partnerName = resultSet.getString("PartnerName");

                    // B3.3: chuyển loại gói và trạng thái sang enum.
                    String subscriptionTypeValue = resultSet.getString("SubscriptionType");
                    String statusValue = resultSet.getString("Status");
                    String missedReasonValue = resultSet.getString("MissedReason");
                    Package.Type subscriptionType = Package.Type.fromDbValue(subscriptionTypeValue);
                    Session.Status status = Session.Status.fromDbValue(statusValue);
                    Session.MissedReason missedReason = Session.MissedReason.fromDbValue(missedReasonValue);



                    // Không nhận diện được loại gói
                    // Không nhận diện được trạng thái buổi
                    // DB có ghi lý do vắng, nhưng enum không nhận diện được lý do đó
                    if (subscriptionType == null || status == null
                            || (missedReasonValue != null && missedReason == null)) {
                        throw new SQLException("Giá trị loại gói hoặc trạng thái không hợp lệ, SessionId=" + sessionId);
                    }

                    // B3.4: tạo DTO từ dòng vừa đọc và thêm vào danh sách.
                    PtSessionView session = new PtSessionView(
                            sessionId, slotDate, startTime, endTime,
                            customerName, partnerName, subscriptionType, status, missedReason);
                    sessions.add(session);
                }
            }
        }

        // B4: trả danh sách cho Service gom theo ngày.
        return sessions;
    }

    /**
     * Đọc dữ liệu khách/gói/ngày tập để Service chuẩn bị khối “Cần chú ý”.
     * Mỗi dòng ứng với một khách + một gói PT + một ngày, chưa phải một cảnh báo.
     * Gói đôi trả dòng riêng cho holder và partner. Membership được xét theo ngày tập,
     * kể cả gói nối tiếp; số buổi còn trong lịch được đếm trên toàn bộ tương lai của gói.
     * @param ptEmployeeId PT phụ trách buổi, theo Sessions.PtEmployeeId, đã được Service xác minh
     * @param fromDate ngày đầu phạm vi cảnh báo, Service truyền hôm nay theo giờ VN
     * @param toDate ngày cuối, Service truyền hôm nay + 13 ngày; tính cả hai ngày biên
     * @param now thời điểm giờ VN do Service lấy một lần, dùng lọc buổi chưa bắt đầu
     * @return dữ liệu theo ngày, gói và khách; danh sách rỗng nếu không có buổi phù hợp
     * @throws SQLException khi truy vấn lỗi hoặc loại gói không đúng với gói PT
     */
    public List<PtClientPackageView> findClientPackages(int ptEmployeeId,
            LocalDate fromDate, LocalDate toDate, LocalDateTime now) throws SQLException {
        // B1: chuẩn bị một truy vấn đọc dữ liệu, không thay đổi trạng thái buổi/gói.
        // WITH đặt tên cho từng phần kết quả để phần SELECT bên dưới dùng lại; không tạo bảng trong DB.
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

        // B2: truyền bốn tham số theo đúng thứ tự dấu ? trong SQL.
        try (Connection connection = DBContext.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, Timestamp.valueOf(now));
            statement.setInt(2, ptEmployeeId);
            statement.setDate(3, Date.valueOf(fromDate));
            statement.setDate(4, Date.valueOf(toDate));

            // B3: đọc từng dòng rõ tên cột, rồi truyền dữ liệu vào constructor DTO.
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    // B3.1: đọc khách và gói PT.
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

                    // B3.2: đọc ngày tập và các kết quả kiểm tra membership/bảo lưu.
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

                    // B3.3: DAO tạo dữ liệu gốc; Service sẽ chọn cảnh báo ở bước tiếp theo.
                    PtClientPackageView clientPackage = new PtClientPackageView(
                            customerId, customerName, ptSubscriptionId, ptSubscriptionType,
                            ptEndDate, scheduledSessionCount, sessionDate, membershipEndDate,
                            hasMembershipOnSessionDate, frozenOnSessionDate, freezeEndDate);
                    clientPackages.add(clientPackage);
                }
            }
        }

        // B4: trả danh sách dữ liệu, kể cả khách không có vấn đề; Service quyết định có cảnh báo hay không.
        return clientPackages;
    }

    /**
     * Lấy đầy đủ gói PT Active và buổi lẻ sắp dạy cho khối Khách phụ trách.
     * Gói cá nhân/đôi thuộc PT trên Subscriptions.PtEmployeeId, kể cả có buổi được chuyển cho PT khác.
     * Buổi lẻ thuộc PT thực tế trên Sessions.PtEmployeeId và phải còn Scheduled, chưa bắt đầu.
     * Mỗi dòng tạo một PtAssignedPackageView; gói đôi chứa hai tên trong cùng DTO.
     * @param ptEmployeeId mã nhân viên PT mà Service đã xác minh, không phải mã tài khoản
     * @param now thời điểm giờ VN do Service truyền vào; buổi bắt đầu đúng thời điểm này không được đếm
     * @return danh sách theo tên khách, hạn gói và mã gói; rỗng nếu không có dữ liệu phù hợp
     * @throws SQLException khi đọc DB lỗi hoặc loại gói không hợp lệ
     */
    public List<PtAssignedPackageView> findAssignedPackages(int ptEmployeeId, LocalDateTime now)
            throws SQLException {
        // B1: chọn gói và tên khách; đếm số buổi tương lai riêng để không nhân số dòng của gói đôi.
        // QueryTime giữ thời điểm Service truyền vào, dùng chung cho lọc buổi lẻ và đếm số buổi.
        String sql = """
                WITH QueryTime AS (
                    SELECT CAST(? AS DATETIME2) AS Now
                )
                SELECT sub.SubscriptionId, p.FullName AS CustomerName,
                       pp.FullName AS PartnerName,
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
                WHERE sub.Status = 'Active'
                  AND o.Status = 'Paid'
                  AND (
                      -- B1.2: gói PT lọc theo người phụ trách gói, không theo PT dạy từng buổi.
                      (sub.SubscriptionType IN ('PTSingle', 'PTCouple')
                       AND sub.PtEmployeeId = ?)
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
                ORDER BY p.FullName, sub.EndDate, sub.SubscriptionId
                """;

        List<PtAssignedPackageView> assignedPackages = new ArrayList<>();

        // B2: truyền thời điểm xét và mã PT; cùng một PT dùng cho hai nhánh gói PT/buổi lẻ.
        try (Connection connection = DBContext.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, Timestamp.valueOf(now));
            statement.setInt(2, ptEmployeeId);
            statement.setInt(3, ptEmployeeId);

            // B3: mỗi dòng SQL tạo một DTO; không gom khách theo tên vì một khách có thể có nhiều gói.
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    // B3.1: đọc tên để JSP hiện; partnerName có thể null với gói cá nhân/buổi lẻ.
                    String customerName = resultSet.getString("CustomerName");
                    String partnerName = resultSet.getString("PartnerName");

                    // B3.2: chuyển loại gói sang enum cho Service chọn nhãn.
                    Package.Type ptSubscriptionType = Package.Type.fromDbValue(
                            resultSet.getString("PtSubscriptionType"));
                    if (ptSubscriptionType != Package.Type.PT_SINGLE
                            && ptSubscriptionType != Package.Type.PT_COUPLE
                            && ptSubscriptionType != Package.Type.SINGLE_SESSION) {
                        int subscriptionId = resultSet.getInt("SubscriptionId");
                        throw new SQLException("Loại gói không hợp lệ trong Khách phụ trách, SubscriptionId="
                                + subscriptionId);
                    }

                    // B3.3: đọc hạn gói và số buổi Scheduled chưa bắt đầu mà SQL đã đếm.
                    LocalDate ptEndDate = resultSet.getDate("PtEndDate").toLocalDate();
                    int scheduledSessionCount = resultSet.getInt("ScheduledSessionCount");

                    // B3.4: điền năm field gốc; ptSubscriptionTypeLabel để Service bổ sung sau.
                    PtAssignedPackageView assignedPackage = new PtAssignedPackageView(
                            customerName, partnerName, ptSubscriptionType, ptEndDate, scheduledSessionCount);
                    assignedPackages.add(assignedPackage);
                }
            }
        }

        // B4: trả đầy đủ danh sách cho Service, không giới hạn số gói và không trả null khi rỗng.
        return assignedPackages;
    }

    /**
     * Tổng hợp số buổi và hoa hồng của PT trong một tháng, trả đúng một DTO.
     * @param ptEmployeeId mã PT dạy buổi, lọc theo Sessions.PtEmployeeId
     * @param monthStart ngày đầu tháng, có tính ngày này
     * @param nextMonthStart ngày đầu tháng sau, không tính ngày này
     * @param now thời điểm hiện tại theo giờ VN; buổi kết thúc đúng lúc này thuộc nhóm chờ chốt
     * @return các số đếm và tổng tiền gốc; Service sẽ bổ sung mẫu số và tỷ lệ
     * @throws SQLException nếu không đọc được dữ liệu
     */
    public PtCommissionSummaryView findCommissionSummary(int ptEmployeeId,
            LocalDate monthStart, LocalDate nextMonthStart, LocalDateTime now)
            throws SQLException {
        // B1: mỗi Session chỉ nối một TimeSlot. SQL tính số buổi/tiền trên toàn tháng.
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

        // B2: Service truyền cùng một now cho cả ba phép so sánh ngày/giờ.
        try (Connection connection = DBContext.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, Timestamp.valueOf(now));
            statement.setTimestamp(2, Timestamp.valueOf(now));
            statement.setTimestamp(3, Timestamp.valueOf(now));
            statement.setInt(4, ptEmployeeId);
            statement.setDate(5, Date.valueOf(monthStart));
            statement.setDate(6, Date.valueOf(nextMonthStart));

            // B3: SUM luôn trả một dòng; COALESCE biến tháng không có buổi thành các số 0.
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

                // B4: DAO tạo DTO từ đúng 10 cột; tỷ lệ phần trăm để Service tính sau.
                return new PtCommissionSummaryView(
                        checkedInCount, noCheckInCount, noMembershipCount,
                        membershipFrozenCount, cancelledCount, finalizedCommissionAmount,
                        pendingSessionCount, pendingCommissionAmount,
                        projectedSessionCount, projectedCommissionAmount);
            }
        }
    }

    /**
     * Đếm khách đã chấm và cộng số sao của PT trong đúng một tháng.
     * Mỗi khách có tối đa một phiếu cho PT/tháng nhờ unique index trong Feedbacks.
     * @param ptEmployeeId mã PT đã được Service xác minh
     * @param ratingMonth ngày đầu tháng hiện tại theo giờ VN
     * @return hai số gốc; Service sẽ áp ngưỡng P-22 trước khi JSP được hiện điểm
     * @throws SQLException khi không đọc được dữ liệu
     */
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
                // Không có phiếu: cả hai số bằng 0; Service chọn thông báo phù hợp.
                return new PtRatingView(resultSet.getInt("RatingCustomerCount"),
                        resultSet.getLong("RatingTotal"));
            }
        }
    }
}
