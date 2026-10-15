package com.gms.dao;

import com.gms.dto.MyPtHistorySessionView;
import com.gms.dto.MyPtPackageView;
import com.gms.dto.MyPtSessionFilter;
import com.gms.dto.MyPtUpcomingSessionView;
import com.gms.dto.PtSessionCancelInfo;
import com.gms.entity.Package;
import com.gms.entity.Session;
import com.gms.util.DBContext;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Đọc dữ liệu cho màn Lịch tập của tôi.
 * Thứ tự: gói PT của bạn, lịch sắp tới, lịch sử, đếm ngày phân trang và hàm dùng chung.
 * DAO không chốt buổi, đổi trạng thái gói hay tính lượt đổi còn lại.
 */
public class MyPtSessionDAO {

    // ===== 1. GÓI PT CỦA BẠN =====

    /**
     * Lấy gói PT khách đã mua có Subscriptions.Status = Active, gồm gói đứng tên và gói đôi đi cùng.
     * Không lọc Packages.Status: danh mục đã ngừng bán vẫn giữ gói khách đã mua.
     * Gói chưa tới StartDate vẫn được lấy; không lấy buổi lẻ hoặc đơn chưa Paid.
     * Trạng thái hết hạn của Subscription do job cập nhật; DAO chỉ đọc trạng thái đang lưu.
     *
     * @param customerId mã hồ sơ khách do Service xác định từ tài khoản đăng nhập,
     *                   không phải accountId hoặc mã khách nhập từ URL
     * @return danh sách gói theo hạn gần nhất; rỗng nếu không có gói phù hợp
     * @throws SQLException khi kết nối hoặc truy vấn lỗi; Service sẽ xử lý lỗi
     */
    public List<MyPtPackageView> findActivePtPackages(int customerId, LocalDateTime now) throws SQLException {
        return findPtPackages(customerId, true, now);
    }

    /** Các lần mua gói PT đã Paid của khách, gồm gói cũ phục vụ bộ lọc Lịch sử. */
    public List<MyPtPackageView> findPtPackageOptions(int customerId, LocalDateTime now) throws SQLException {
        return findPtPackages(customerId, false, now);
    }

    private List<MyPtPackageView> findPtPackages(int customerId, boolean activeOnly,
            LocalDateTime now) throws SQLException {
        // B1: chọn mỗi gói một dòng; tên gói lấy từ đơn lúc mua, PT lấy từ người phụ trách gói.
        String sql = """
                SELECT sub.SubscriptionId,
                       o.PackageNameSnapshot AS PackageName,
                       sub.SubscriptionType,
                       ptPerson.FullName AS PtName,
                       CASE
                           WHEN sub.SubscriptionType <> 'PTCouple' THEN NULL
                           WHEN sub.CustomerId = ? THEN partnerPerson.FullName
                           ELSE holderPerson.FullName
                       END AS PartnerName,
                       sub.StartDate, sub.EndDate,
                       sub.GeneratedSessionCount,
                       sessionCounts.ScheduledSessionCount,
                       sessionCounts.UpcomingSessionCount,
                       sub.MaxSwapsSnapshot, sub.SwapsUsed
                FROM Subscriptions sub
                JOIN Orders o ON o.OrderId = sub.OrderId
                JOIN Employees pt ON pt.EmployeeId = sub.PtEmployeeId
                JOIN Persons ptPerson ON ptPerson.PersonId = pt.PersonId
                JOIN Customers holder ON holder.CustomerId = sub.CustomerId
                JOIN Persons holderPerson ON holderPerson.PersonId = holder.PersonId
                LEFT JOIN Customers partner ON partner.CustomerId = o.PartnerCustomerId
                    AND sub.SubscriptionType = 'PTCouple'
                LEFT JOIN Persons partnerPerson ON partnerPerson.PersonId = partner.PersonId
                -- Đếm riêng buổi của từng gói để không lặp thẻ theo số buổi.
                OUTER APPLY (
                    SELECT COUNT(*) AS ScheduledSessionCount,
                           COUNT(CASE WHEN t.SlotDate > ?
                                           OR (t.SlotDate = ? AND t.StartTime > CAST(? AS TIME))
                                      THEN 1 END) AS UpcomingSessionCount
                    FROM Sessions s
                    JOIN TimeSlots t ON t.TimeSlotId = s.TimeSlotId
                    WHERE s.SubscriptionId = sub.SubscriptionId
                      AND s.Status = 'Scheduled'
                ) sessionCounts
                WHERE sub.SubscriptionType IN ('PTSingle', 'PTCouple')
                  AND (? = 0 OR sub.Status = 'Active')
                  AND o.Status = 'Paid'
                  AND (
                      sub.CustomerId = ?
                      OR (sub.SubscriptionType = 'PTCouple' AND o.PartnerCustomerId = ?)
                  )
                ORDER BY sub.EndDate, sub.StartDate, sub.SubscriptionId
                """;

        List<MyPtPackageView> ptPackages = new ArrayList<>();

        // B2: truyền customerId và cờ lọc Active qua PreparedStatement, không nối dữ liệu vào SQL.
        try (Connection connection = DBContext.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            // Vị trí 1 chọn tên người còn lại: khách đăng nhập không thấy tên mình là người đi cùng.
            statement.setInt(1, customerId);
            // 2–4 đếm lịch chưa bắt đầu; 5 chọn Active; 6–7 giới hạn người đứng tên/đi cùng.
            statement.setDate(2, Date.valueOf(now.toLocalDate()));
            statement.setDate(3, Date.valueOf(now.toLocalDate()));
            statement.setTime(4, Time.valueOf(now.toLocalTime()));
            statement.setInt(5, activeOnly ? 1 : 0);
            statement.setInt(6, customerId);
            statement.setInt(7, customerId);

            // B3: mỗi dòng SQL tạo một DTO, rồi thêm vào danh sách gói trả về.
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    int subscriptionId = resultSet.getInt("SubscriptionId");
                    String packageName = resultSet.getString("PackageName");
                    Package.Type subscriptionType = Package.Type.fromDbValue(
                            resultSet.getString("SubscriptionType"));
                    String ptName = resultSet.getString("PtName");
                    String partnerName = resultSet.getString("PartnerName");

                    // SQL đếm mọi Scheduled, kể cả buổi đã kết thúc nhưng chờ JOB-04.
                    // Không lấy tổng ban đầu trừ buổi đã dùng để tránh nhầm các loại hủy.
                    int generatedSessionCount = resultSet.getInt("GeneratedSessionCount");
                    int scheduledSessionCount = resultSet.getInt("ScheduledSessionCount");
                    int maxSwapsSnapshot = resultSet.getInt("MaxSwapsSnapshot");
                    int swapsUsed = resultSet.getInt("SwapsUsed");

                    MyPtPackageView ptPackage = new MyPtPackageView(
                            subscriptionId, packageName, subscriptionType, ptName, partnerName,
                            resultSet.getDate("StartDate").toLocalDate(),
                            resultSet.getDate("EndDate").toLocalDate(),
                            generatedSessionCount, scheduledSessionCount, maxSwapsSnapshot, swapsUsed);
                    ptPackage.setUpcomingSessionCount(resultSet.getInt("UpcomingSessionCount"));
                    ptPackages.add(ptPackage);
                }
            }
        }

        // B4: trả dữ liệu gốc; Service sẽ điền nhãn loại gói và lượt đổi còn lại.
        return ptPackages;
    }

    // ===== 2. LỊCH TẬP SẮP TỚI =====

    /**
     * Đọc các buổi Scheduled từ hôm nay; Service loại buổi đã kết thúc trong hôm nay.
     * Lấy cả buổi lẻ và gói đôi đi cùng; không lọc trạng thái danh mục Packages.
     * @param customerId hồ sơ khách do Service xác định từ tài khoản đăng nhập
     * @param today ngày hiện tại theo giờ VN, Service lấy cùng thời điểm xét nhãn
     * @param filter bộ lọc chung đã hợp lệ (loại buổi, từ ngày, đến ngày); none() là không lọc.
     *               Trạng thái lịch sử không áp dụng cho khối này.
     * @return mỗi dòng một DTO buổi, theo ngày giờ gần nhất; có thể rỗng
     */
    public List<MyPtUpcomingSessionView> findUpcomingSessions(int customerId, LocalDate today,
            MyPtSessionFilter filter) throws SQLException {
        // Luồng chưa phân trang vẫn dùng truy vấn hiện tại; Service loại buổi đã kết thúc hôm nay.
        return findUpcomingSessions(customerId, today, null, filter, 0, 0);
    }

    /**
     * Lấy các buổi sắp tới thuộc một trang ngày, sau bộ lọc chung.
     * SQL chọn ngày trước, rồi lấy đủ buổi của các ngày đó; không tải toàn bộ lịch vào Java.
     * @param customerId hồ sơ khách đăng nhập do Service xác định
     * @param now cùng giờ VN với countUpcomingSessionDays, để hai truy vấn xét cùng nhóm buổi
     * @param filter bộ lọc đã hợp lệ, không áp dụng trạng thái lịch sử
     * @param dayOffset số ngày bỏ qua, Service tính (trang hợp lệ - 1) * 8
     * @param daysPerPage số ngày mỗi trang, Service sẽ truyền 8
     * @return danh sách buổi theo ngày/giờ tăng dần; không tách buổi cùng ngày; có thể rỗng
     * @throws SQLException khi truy vấn lỗi
     */
    public List<MyPtUpcomingSessionView> findUpcomingSessionsPage(int customerId, LocalDateTime now,
            MyPtSessionFilter filter, int dayOffset, int daysPerPage) throws SQLException {
        validateDayPagination(dayOffset, daysPerPage);
        return findUpcomingSessions(customerId, now.toLocalDate(), now, filter, dayOffset, daysPerPage);
    }

    /**
     * Truy vấn và ánh xạ DTO dùng chung cho luồng sắp tới cũ và luồng phân trang.
     * now null / daysPerPage = 0 chỉ dùng nội bộ cho luồng cũ; luồng trang luôn nhận now và cỡ trang hợp lệ.
     */
    private List<MyPtUpcomingSessionView> findUpcomingSessions(int customerId, LocalDate today,
            LocalDateTime now, MyPtSessionFilter filter, int dayOffset, int daysPerPage) throws SQLException {
        // B1: JOIN lịch/PT thực tế từ Sessions; EXISTS không nhân dòng khi có nhiều gói gym.
        // Dữ kiện Membership, DayPass, bảo lưu dùng cùng điều kiện ngày với DAO của JOB-04.
        // ORDER BY tách riêng ở B2 để chèn điều kiện lọc vào trước nó.
        String baseSql = """
                SELECT s.SessionId, sub.SubscriptionId, sub.CustomerId AS HolderCustomerId,
                       o.PackageNameSnapshot AS PackageName, sub.SubscriptionType,
                       sub.StartDate AS PackageStartDate, sub.EndDate AS PackageEndDate,
                       ptPerson.FullName AS PtName,
                       CASE WHEN sub.SubscriptionType <> 'PTCouple' THEN NULL
                            WHEN sub.CustomerId = ? THEN partnerPerson.FullName
                            ELSE holderPerson.FullName END AS PartnerName,
                       t.SlotDate, t.StartTime, t.EndTime,
                       CASE WHEN EXISTS (
                           SELECT 1 FROM Subscriptions gym
                           WHERE gym.CustomerId = sub.CustomerId
                             AND gym.SubscriptionType = 'Membership'
                             AND gym.Status IN ('Active', 'Frozen', 'Expired')
                             AND t.SlotDate BETWEEN gym.StartDate AND gym.EndDate
                       ) THEN 1 ELSE 0 END AS HolderHasMembership,
                       CASE WHEN EXISTS (
                           SELECT 1 FROM Subscriptions gym
                           WHERE gym.CustomerId = sub.CustomerId
                             AND gym.SubscriptionType = 'DayPass'
                             AND gym.Status IN ('Active', 'Used', 'Expired')
                             AND t.SlotDate BETWEEN gym.StartDate AND gym.EndDate
                       ) THEN 1 ELSE 0 END AS HolderHasDayPass,
                       CASE WHEN EXISTS (
                           SELECT 1 FROM Subscriptions gym
                           JOIN FreezeRecords f ON f.SubscriptionId = gym.SubscriptionId
                           WHERE gym.CustomerId = sub.CustomerId
                             AND gym.SubscriptionType = 'Membership'
                             AND t.SlotDate BETWEEN f.StartDate
                                 AND COALESCE(f.ActualEndDate, f.PlannedEndDate)
                       ) THEN 1 ELSE 0 END AS HolderFrozen,
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
                           JOIN FreezeRecords f ON f.SubscriptionId = gym.SubscriptionId
                           WHERE gym.CustomerId = o.PartnerCustomerId
                             AND gym.SubscriptionType = 'Membership'
                             AND t.SlotDate BETWEEN f.StartDate
                                 AND COALESCE(f.ActualEndDate, f.PlannedEndDate)
                       ) THEN 1 ELSE 0 END AS PartnerFrozen
                FROM Sessions s
                JOIN TimeSlots t ON t.TimeSlotId = s.TimeSlotId
                JOIN Subscriptions sub ON sub.SubscriptionId = s.SubscriptionId
                JOIN Orders o ON o.OrderId = sub.OrderId
                JOIN Employees pt ON pt.EmployeeId = s.PtEmployeeId
                JOIN Persons ptPerson ON ptPerson.PersonId = pt.PersonId
                JOIN Customers holder ON holder.CustomerId = sub.CustomerId
                JOIN Persons holderPerson ON holderPerson.PersonId = holder.PersonId
                LEFT JOIN Customers partner ON partner.CustomerId = o.PartnerCustomerId
                    AND sub.SubscriptionType = 'PTCouple'
                LEFT JOIN Persons partnerPerson ON partnerPerson.PersonId = partner.PersonId
                WHERE s.Status = 'Scheduled' AND o.Status = 'Paid'
                  AND sub.SubscriptionType IN ('PTSingle', 'PTCouple', 'SingleSession')
                  AND t.SlotDate >= ?
                  AND (sub.CustomerId = ?
                       OR (sub.SubscriptionType = 'PTCouple' AND o.PartnerCustomerId = ?))
                """;
        // Giá trị cho từng dấu ? theo đúng thứ tự xuất hiện trong SQL:
        // vị trí 1 chọn người còn lại; 2 là hôm nay; 3/4 giới hạn gói đứng tên hoặc đi cùng (GBR-10).
        List<Object> parameters = new ArrayList<>(List.of(customerId, today, customerId, customerId));

        // B2: ghép điều kiện bộ lọc chung rồi mới sắp xếp; filter none() thì không thêm gì.
        StringBuilder sql = new StringBuilder(baseSql);
        appendCommonFilter(sql, parameters, filter);
        if (daysPerPage > 0) {
            // Luồng trang loại buổi đã kết thúc ngay trong SQL, trước khi chọn các ngày.
            // Khớp countUpcomingSessionDays; không để ngày chỉ có buổi đã qua chiếm chỗ trong trang.
            sql.append(" AND (t.SlotDate > ? OR (t.SlotDate = ? AND t.EndTime > CAST(? AS TIME)))");
            parameters.add(now.toLocalDate());
            parameters.add(now.toLocalDate());
            parameters.add(now.toLocalTime());
            sql = buildDayPageSql(sql, parameters, dayOffset, daysPerPage, false);
        } else {
            sql.append(" ORDER BY t.SlotDate, t.StartTime, s.SessionId");
        }
        List<MyPtUpcomingSessionView> sessions = new ArrayList<>();

        try (Connection connection = DBContext.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            bindParameters(statement, parameters);

            // B3: chỉ đọc, không khóa buổi hoặc cập nhật trạng thái thay JOB-04.
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    MyPtUpcomingSessionView session = new MyPtUpcomingSessionView(
                            resultSet.getInt("SessionId"), resultSet.getInt("SubscriptionId"),
                            resultSet.getInt("HolderCustomerId"), resultSet.getString("PackageName"),
                            Package.Type.fromDbValue(resultSet.getString("SubscriptionType")),
                            resultSet.getString("PtName"), resultSet.getString("PartnerName"),
                            resultSet.getDate("SlotDate").toLocalDate(),
                            resultSet.getTime("StartTime").toLocalTime(),
                            resultSet.getTime("EndTime").toLocalTime(),
                            resultSet.getBoolean("HolderHasMembership"),
                            resultSet.getBoolean("HolderHasDayPass"), resultSet.getBoolean("HolderFrozen"),
                            resultSet.getBoolean("PartnerHasMembership"),
                            resultSet.getBoolean("PartnerHasDayPass"), resultSet.getBoolean("PartnerFrozen"));
                    if (session.getSubscriptionType() != Package.Type.SINGLE_SESSION) {
                        session.setPackageTerm(resultSet.getDate("PackageStartDate").toLocalDate(),
                                resultSet.getDate("PackageEndDate").toLocalDate());
                    }
                    sessions.add(session);
                }
            }
        }
        return sessions;
    }

    // ===== 3. LỊCH SỬ BUỔI TẬP =====

    /**
     * Đọc buổi đã có kết quả cuối cùng hoặc đã qua giờ kết thúc: CheckedIn, Missed, Cancelled
     * (kể cả buổi tương lai bị hủy) và Scheduled đã kết thúc nhưng chờ JOB-04 chốt.
     * Là phần bù của findUpcomingSessions: cùng một thời điểm now thì mỗi buổi chỉ thuộc một khối.
     * Chỉ đọc kết quả lưu trong Sessions; không lọc Subscriptions.Status hay Packages.Status
     * để buổi của gói đã hết hạn hoặc ngừng bán (CBR-55) vẫn còn trong lịch sử.
     * @param customerId hồ sơ khách do Service xác định từ tài khoản đăng nhập
     * @param now thời điểm xét giờ VN, Service lấy một lần và dùng chung với khối sắp tới
     * @param filter bộ lọc chung + trạng thái lịch sử đã hợp lệ; none() là không lọc
     * @return mỗi dòng một DTO buổi, mới nhất trước; có thể rỗng
     */
    public List<MyPtHistorySessionView> findHistorySessions(int customerId, LocalDateTime now,
            MyPtSessionFilter filter) throws SQLException {
        return findHistorySessions(customerId, now, filter, 0, 0);
    }

    /**
     * Lấy một trang lịch sử theo ngày sau bộ lọc chung và trạng thái lịch sử.
     * @param customerId hồ sơ khách đăng nhập do Service xác định, gồm quyền đi cùng gói đôi
     * @param now cùng thời điểm với countHistorySessionDays và khối sắp tới
     * @param filter bộ lọc đã hợp lệ; lọc trước khi chọn ngày để trang không thiếu buổi phù hợp
     * @param dayOffset số ngày bỏ qua, Service tính (trang hợp lệ - 1) * 8
     * @param daysPerPage số ngày mỗi trang, Service sẽ truyền 8
     * @return đủ buổi thuộc các ngày của trang, ngày/giờ mới nhất trước; có thể rỗng
     * @throws SQLException khi truy vấn lỗi
     */
    public List<MyPtHistorySessionView> findHistorySessionsPage(int customerId, LocalDateTime now,
            MyPtSessionFilter filter, int dayOffset, int daysPerPage) throws SQLException {
        validateDayPagination(dayOffset, daysPerPage);
        return findHistorySessions(customerId, now, filter, dayOffset, daysPerPage);
    }

    /** Truy vấn và ánh xạ DTO lịch sử dùng chung; daysPerPage = 0 là luồng cũ chưa phân trang. */
    private List<MyPtHistorySessionView> findHistorySessions(int customerId, LocalDateTime now,
            MyPtSessionFilter filter, int dayOffset, int daysPerPage) throws SQLException {
        // B1: JOIN lịch/PT thực tế từ Sessions, gói và người tập cùng như khối sắp tới.
        // Không cần EXISTS Membership/DayPass: lý do vắng đã được job lưu trong MissedReason.
        // ORDER BY tách riêng ở B2 để chèn điều kiện lọc vào trước nó.
        String baseSql = """
                SELECT s.SessionId, sub.SubscriptionId, sub.CustomerId AS HolderCustomerId,
                       o.PackageNameSnapshot AS PackageName, sub.SubscriptionType,
                       sub.StartDate AS PackageStartDate, sub.EndDate AS PackageEndDate,
                       ptPerson.FullName AS PtName,
                       CASE WHEN sub.SubscriptionType <> 'PTCouple' THEN NULL
                            WHEN sub.CustomerId = ? THEN partnerPerson.FullName
                            ELSE holderPerson.FullName END AS PartnerName,
                       t.SlotDate, t.StartTime, t.EndTime,
                       s.Status, s.MissedReason, s.CancelType, s.CancelReason, s.CancelledAt
                FROM Sessions s
                JOIN TimeSlots t ON t.TimeSlotId = s.TimeSlotId
                JOIN Subscriptions sub ON sub.SubscriptionId = s.SubscriptionId
                JOIN Orders o ON o.OrderId = sub.OrderId
                JOIN Employees pt ON pt.EmployeeId = s.PtEmployeeId
                JOIN Persons ptPerson ON ptPerson.PersonId = pt.PersonId
                JOIN Customers holder ON holder.CustomerId = sub.CustomerId
                JOIN Persons holderPerson ON holderPerson.PersonId = holder.PersonId
                LEFT JOIN Customers partner ON partner.CustomerId = o.PartnerCustomerId
                    AND sub.SubscriptionType = 'PTCouple'
                LEFT JOIN Persons partnerPerson ON partnerPerson.PersonId = partner.PersonId
                WHERE o.Status = 'Paid'
                  AND sub.SubscriptionType IN ('PTSingle', 'PTCouple', 'SingleSession')
                  AND (s.Status IN ('CheckedIn', 'Missed', 'Cancelled')
                       OR (s.Status = 'Scheduled'
                           AND (t.SlotDate < ? OR (t.SlotDate = ? AND t.EndTime <= CAST(? AS TIME)))))
                  AND (sub.CustomerId = ?
                       OR (sub.SubscriptionType = 'PTCouple' AND o.PartnerCustomerId = ?))
                """;
        // Giá trị cho từng dấu ? theo đúng thứ tự xuất hiện trong SQL:
        // vị trí 1 chọn người còn lại; 2-4 xác định Scheduled đã kết thúc; 5-6 giới hạn gói được xem.
        // Giờ đi kèm CAST(? AS TIME) vì driver có thể gửi java.sql.Time thành DATETIME.
        List<Object> parameters = new ArrayList<>(List.of(customerId,
                now.toLocalDate(), now.toLocalDate(), now.toLocalTime(), customerId, customerId));

        // B2: bộ lọc chung giống hệt khối Sắp tới, rồi thêm trạng thái riêng của Lịch sử, sau cùng mới sắp xếp.
        StringBuilder sql = new StringBuilder(baseSql);
        appendCommonFilter(sql, parameters, filter);
        appendHistoryStatusFilter(sql, parameters, filter);
        if (daysPerPage > 0) {
            sql = buildDayPageSql(sql, parameters, dayOffset, daysPerPage, true);
        } else {
            sql.append(" ORDER BY t.SlotDate DESC, t.StartTime DESC, s.SessionId DESC");
        }
        List<MyPtHistorySessionView> sessions = new ArrayList<>();

        try (Connection connection = DBContext.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            bindParameters(statement, parameters);

            // B3: chỉ đọc; mỗi dòng SQL tạo một DTO, nhãn và giải thích do Service bổ sung.
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    Timestamp cancelledAt = resultSet.getTimestamp("CancelledAt");
                    MyPtHistorySessionView session = new MyPtHistorySessionView(
                            resultSet.getInt("SessionId"), resultSet.getInt("SubscriptionId"),
                            resultSet.getInt("HolderCustomerId"), resultSet.getString("PackageName"),
                            Package.Type.fromDbValue(resultSet.getString("SubscriptionType")),
                            resultSet.getString("PtName"), resultSet.getString("PartnerName"),
                            resultSet.getDate("SlotDate").toLocalDate(),
                            resultSet.getTime("StartTime").toLocalTime(),
                            resultSet.getTime("EndTime").toLocalTime(),
                            Session.Status.fromDbValue(resultSet.getString("Status")),
                            // MissedReason và CancelType NULL khi buổi không thuộc trạng thái tương ứng.
                            Session.MissedReason.fromDbValue(resultSet.getString("MissedReason")),
                            Session.CancelType.fromDbValue(resultSet.getString("CancelType")),
                            resultSet.getString("CancelReason"),
                            cancelledAt == null ? null : cancelledAt.toLocalDateTime());
                    if (session.getSubscriptionType() != Package.Type.SINGLE_SESSION) {
                        session.setPackageTerm(resultSet.getDate("PackageStartDate").toLocalDate(),
                                resultSet.getDate("PackageEndDate").toLocalDate());
                    }
                    sessions.add(session);
                }
            }
        }
        return sessions;
    }

    // ===== 4. ĐẾM NGÀY ĐỂ PHÂN TRANG =====

    /**
     * Đếm số ngày có Scheduled chưa kết thúc, sau bộ lọc chung; không đếm số buổi.
     * Một ngày có nhiều buổi vẫn tính một ngày để không chia buổi cùng ngày sang hai trang.
     * @param customerId hồ sơ khách đăng nhập do Service xác định, gồm quyền đi cùng gói đôi
     * @param now giờ VN dùng chung khi đếm và lấy dữ liệu trang; kết thúc đúng now thuộc lịch sử
     * @param filter bộ lọc đã hợp lệ; trạng thái lịch sử không áp dụng cho khối sắp tới
     * @return số ngày phù hợp trên toàn bộ kết quả, bằng 0 nếu không có buổi
     * @throws SQLException khi truy vấn lỗi; không trả 0 thay cho lỗi kết nối
     */
    public int countUpcomingSessionDays(int customerId, LocalDateTime now,
            MyPtSessionFilter filter) throws SQLException {
        // B1: giữ cùng JOIN và quyền xem như findUpcomingSessions (GBR-10, DBR-54).
        // Xét cả giờ kết thúc hôm nay để khớp phần Service loại buổi đã kết thúc.
        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(DISTINCT t.SlotDate) AS TotalDays
                FROM Sessions s
                JOIN TimeSlots t ON t.TimeSlotId = s.TimeSlotId
                JOIN Subscriptions sub ON sub.SubscriptionId = s.SubscriptionId
                JOIN Orders o ON o.OrderId = sub.OrderId
                JOIN Employees pt ON pt.EmployeeId = s.PtEmployeeId
                JOIN Persons ptPerson ON ptPerson.PersonId = pt.PersonId
                JOIN Customers holder ON holder.CustomerId = sub.CustomerId
                JOIN Persons holderPerson ON holderPerson.PersonId = holder.PersonId
                LEFT JOIN Customers partner ON partner.CustomerId = o.PartnerCustomerId
                    AND sub.SubscriptionType = 'PTCouple'
                LEFT JOIN Persons partnerPerson ON partnerPerson.PersonId = partner.PersonId
                WHERE s.Status = 'Scheduled' AND o.Status = 'Paid'
                  AND sub.SubscriptionType IN ('PTSingle', 'PTCouple', 'SingleSession')
                  AND (t.SlotDate > ? OR (t.SlotDate = ? AND t.EndTime > CAST(? AS TIME)))
                  AND (sub.CustomerId = ?
                       OR (sub.SubscriptionType = 'PTCouple' AND o.PartnerCustomerId = ?))
                """);
        // B2: ngày/giờ cho điều kiện chưa kết thúc, rồi mã khách cho hai nhánh quyền xem.
        List<Object> parameters = new ArrayList<>(List.of(
                now.toLocalDate(), now.toLocalDate(), now.toLocalTime(), customerId, customerId));
        appendCommonFilter(sql, parameters, filter);

        // B3: SQL chỉ trả một số đếm; chưa lấy danh sách buổi hoặc cắt trang.
        return countSessionDays(sql, parameters);
    }

    /**
     * Đếm ngày có buổi lịch sử sau bộ lọc chung và trạng thái riêng của lịch sử.
     * Điều kiện nhóm giống findHistorySessions, kể cả buổi tương lai đã Cancelled.
     * Không lọc trạng thái gói khách đã mua hoặc danh mục, để giữ lịch sử gói cũ.
     * @param customerId hồ sơ khách đăng nhập do Service xác định
     * @param now cùng thời điểm xét với khối sắp tới; Scheduled kết thúc <= now thuộc lịch sử
     * @param filter bộ lọc đã hợp lệ; none() là không lọc
     * @return số ngày khác nhau trên toàn bộ kết quả sau lọc; rỗng trả 0
     * @throws SQLException khi truy vấn lỗi; Service xử lý thông báo lỗi
     */
    public int countHistorySessionDays(int customerId, LocalDateTime now,
            MyPtSessionFilter filter) throws SQLException {
        // B1: giữ cùng JOIN, quyền xem và điều kiện nhóm như findHistorySessions.
        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(DISTINCT t.SlotDate) AS TotalDays
                FROM Sessions s
                JOIN TimeSlots t ON t.TimeSlotId = s.TimeSlotId
                JOIN Subscriptions sub ON sub.SubscriptionId = s.SubscriptionId
                JOIN Orders o ON o.OrderId = sub.OrderId
                JOIN Employees pt ON pt.EmployeeId = s.PtEmployeeId
                JOIN Persons ptPerson ON ptPerson.PersonId = pt.PersonId
                JOIN Customers holder ON holder.CustomerId = sub.CustomerId
                JOIN Persons holderPerson ON holderPerson.PersonId = holder.PersonId
                LEFT JOIN Customers partner ON partner.CustomerId = o.PartnerCustomerId
                    AND sub.SubscriptionType = 'PTCouple'
                LEFT JOIN Persons partnerPerson ON partnerPerson.PersonId = partner.PersonId
                WHERE o.Status = 'Paid'
                  AND sub.SubscriptionType IN ('PTSingle', 'PTCouple', 'SingleSession')
                  AND (s.Status IN ('CheckedIn', 'Missed', 'Cancelled')
                       OR (s.Status = 'Scheduled'
                           AND (t.SlotDate < ? OR (t.SlotDate = ? AND t.EndTime <= CAST(? AS TIME)))))
                  AND (sub.CustomerId = ?
                       OR (sub.SubscriptionType = 'PTCouple' AND o.PartnerCustomerId = ?))
                """);
        // B2: đếm sau khi áp dụng đầy đủ bộ lọc, dùng lại hai hàm của truy vấn lịch sử.
        List<Object> parameters = new ArrayList<>(List.of(
                now.toLocalDate(), now.toLocalDate(), now.toLocalTime(), customerId, customerId));
        appendCommonFilter(sql, parameters, filter);
        appendHistoryStatusFilter(sql, parameters, filter);

        // B3: trả tổng ngày cho Service tính tổng trang ở bước tiếp theo.
        return countSessionDays(sql, parameters);
    }

    // ===== 5. HÀM DÙNG CHUNG =====

    /** Kiểm tra tham số nội bộ Service truyền xuống, không âm thầm chạy lấy toàn bộ khi cỡ trang sai. */
    private void validateDayPagination(int dayOffset, int daysPerPage) {
        if (dayOffset < 0 || daysPerPage <= 0) {
            throw new IllegalArgumentException("Số ngày bỏ qua phải >= 0 và số ngày mỗi trang phải > 0.");
        }
    }

    /**
     * Bọc truy vấn đã lọc thành hai CTE để phân trang theo ngày trong SQL Server.
     * FilteredSessions là tập buổi đúng quyền xem/nhóm/bộ lọc; PageDays chỉ chọn ngày của trang.
     * CTE là phần SQL, không phải danh sách toàn bộ buổi được tải vào bộ nhớ Java.
     * @param filteredSql truy vấn SELECT đã lọc, chưa có ORDER BY, có SlotDate/StartTime/SessionId
     * @param parameters tham số truy vấn gốc; hàm thêm dayOffset và daysPerPage vào cuối
     * @param dayOffset số ngày bỏ qua, không phải số buổi
     * @param daysPerPage số ngày tối đa của trang
     * @param descending true cho lịch sử mới nhất trước, false cho sắp tới gần nhất trước
     * @return SQL lấy đủ buổi của các ngày đã chọn, cùng thứ tự ổn định như danh sách cũ
     */
    private StringBuilder buildDayPageSql(StringBuilder filteredSql, List<Object> parameters,
            int dayOffset, int daysPerPage, boolean descending) {
        // B1: chỉ hướng sắp xếp cố định được nối chuỗi; giá trị phân trang vẫn dùng dấu ?.
        String direction = descending ? " DESC" : " ASC";
        StringBuilder sql = new StringBuilder("WITH FilteredSessions AS (\n");
        sql.append(filteredSql);
        // B2: OFFSET/FETCH áp trên các ngày khác nhau, không áp trực tiếp trên các buổi.
        sql.append("""
                ), PageDays AS (
                    SELECT DISTINCT SlotDate
                    FROM FilteredSessions
                    ORDER BY SlotDate
                """);
        sql.append(direction).append(" OFFSET ? ROWS FETCH NEXT ? ROWS ONLY\n)");
        parameters.add(dayOffset);
        parameters.add(daysPerPage);
        // B3: lấy đủ buổi đã lọc của những ngày được chọn; ngày cuối trang không bị cắt đôi.
        sql.append("""

                SELECT filtered.*
                FROM FilteredSessions filtered
                JOIN PageDays page ON page.SlotDate = filtered.SlotDate
                ORDER BY filtered.SlotDate
                """);
        sql.append(direction).append(", filtered.StartTime").append(direction)
                .append(", filtered.SessionId").append(direction);
        return sql;
    }

    /**
     * Ghép điều kiện bộ lọc chung (loại buổi, từ ngày, đến ngày) vào cuối mệnh đề WHERE.
     * Dùng cho cả khối Sắp tới và khối Lịch sử để hai khối lọc giống nhau.
     * Chỉ nối đoạn SQL cố định; giá trị người dùng chọn luôn đi qua dấu ? (không nối vào chuỗi).
     * @param sql câu SQL đang ghép, phải kết thúc ở mệnh đề WHERE (chưa có ORDER BY)
     * @param parameters danh sách giá trị cho dấu ?, được thêm theo đúng thứ tự đoạn SQL
     * @param filter bộ lọc đã hợp lệ; field null thì bỏ qua điều kiện đó
     */
    private void appendCommonFilter(StringBuilder sql, List<Object> parameters, MyPtSessionFilter filter) {
        // Lọc theo lần mua, đồng thời giữ nguyên điều kiện sở hữu trong truy vấn gốc.
        if (filter.getSubscriptionId() != null) {
            sql.append(" AND sub.SubscriptionId = ?");
            parameters.add(filter.getSubscriptionId());
        }
        // B1: loại buổi; so mã DB (PTSingle/PTCouple/SingleSession), không so nhãn tiếng Việt.
        if (filter.getSubscriptionType() != null) {
            sql.append(" AND sub.SubscriptionType = ?");
            parameters.add(filter.getSubscriptionType().getDbValue());
        }
        // B2: khoảng ngày gồm cả hai đầu, so trên ngày buổi tập thực tế (TimeSlots.SlotDate).
        if (filter.getDateFrom() != null) {
            sql.append(" AND t.SlotDate >= ?");
            parameters.add(filter.getDateFrom());
        }
        if (filter.getDateTo() != null) {
            sql.append(" AND t.SlotDate <= ?");
            parameters.add(filter.getDateTo());
        }
    }

    /**
     * Gán lần lượt các giá trị vào dấu ? của PreparedStatement theo đúng kiểu dữ liệu.
     * Dùng chung cho các truy vấn có điều kiện ghép động của màn này.
     * @param statement câu lệnh đã prepare
     * @param parameters giá trị theo thứ tự dấu ?: Integer, String, LocalDate hoặc LocalTime
     * @throws SQLException khi gặp kiểu chưa hỗ trợ, để lỗi lộ ra ngay thay vì gán sai
     */
    private void bindParameters(PreparedStatement statement, List<Object> parameters) throws SQLException {
        for (int index = 0; index < parameters.size(); index++) {
            int position = index + 1; // JDBC đánh số dấu ? từ 1.
            Object value = parameters.get(index);
            if (value instanceof Integer) {
                statement.setInt(position, (Integer) value);
            } else if (value instanceof String) {
                statement.setString(position, (String) value);
            } else if (value instanceof LocalDate) {
                statement.setDate(position, Date.valueOf((LocalDate) value));
            } else if (value instanceof LocalTime) {
                statement.setTime(position, Time.valueOf((LocalTime) value));
            } else {
                throw new SQLException("Kiểu tham số chưa hỗ trợ ở vị trí " + position + ": " + value);
            }
        }
    }

    /**
     * Ghép điều kiện trạng thái riêng của khối Lịch sử; không dùng cho khối Sắp tới.
     * SQL gốc đã giới hạn Scheduled ở buổi đã qua giờ kết thúc, nên chọn "Kết quả đang cập nhật"
     * chỉ cần lọc Status = Scheduled là ra đúng nhóm này.
     * @param sql câu SQL lịch sử đang ghép, chưa có ORDER BY
     * @param parameters danh sách giá trị cho dấu ?, thêm đúng một giá trị khi có lọc
     * @param filter bộ lọc đã hợp lệ; historyStatus null là Tất cả, không thêm điều kiện
     */
    private void appendHistoryStatusFilter(StringBuilder sql, List<Object> parameters,
            MyPtSessionFilter filter) {
        MyPtSessionFilter.HistoryStatus historyStatus = filter.getHistoryStatus();
        if (historyStatus == null) {
            return;
        }
        // Đổi nhóm hiển thị sang mã Sessions.Status đang lưu trong DB.
        Session.Status status = switch (historyStatus) {
            case PENDING_RESULT -> Session.Status.SCHEDULED;
            case CHECKED_IN -> Session.Status.CHECKED_IN;
            case MISSED -> Session.Status.MISSED;
            case CANCELLED -> Session.Status.CANCELLED;
        };
        sql.append(" AND s.Status = ?");
        parameters.add(status.getDbValue());
    }

    /**
     * Thực thi truy vấn COUNT của hai khối, dùng chung cách gán tham số và đóng kết nối.
     * @param sql SQL đếm ngày với bí danh TotalDays, chỉ ghép các đoạn SQL cố định
     * @param parameters giá trị các dấu ? theo thứ tự trong SQL
     * @return số ngày SQL đếm được, không tải các buổi vào bộ nhớ Java
     * @throws SQLException khi truy vấn lỗi hoặc không trả dòng COUNT như dự kiến
     */
    private int countSessionDays(StringBuilder sql, List<Object> parameters) throws SQLException {
        try (Connection connection = DBContext.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            bindParameters(statement, parameters);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SQLException("Truy vấn đếm ngày buổi PT không trả kết quả.");
                }
                return resultSet.getInt("TotalDays");
            }
        }
    }

    // ===== 7. HỦY BUỔI TẬP PT (CBR-66, BR-79) =====

    /**
     * Lấy thông tin tối thiểu cần thiết để Service quyết định có cho phép khách hủy buổi này không.
     * Gồm: người đứng tên, partner (nếu đôi), ngày/giờ buổi, trạng thái buổi, trạng thái gói.
     * Không trả về null khi buổi không tồn tại — Service phân biệt "không thấy" bằng cách check kết quả null.
     *
     * @param sessionId mã buổi cần truy vấn
     * @return PtSessionCancelInfo hoặc null nếu không tìm thấy buổi
     * @throws SQLException khi truy vấn lỗi
     */
    public PtSessionCancelInfo findSessionForCancel(int sessionId) throws SQLException {
        // SQL: SELECT tối thiểu cần cho quyết định hủy (customer, partner, slot, status).
        // Dùng OUTER JOIN Orders vì SingleSession có thể không có PartnerCustomerId.
        String sql = """
                SELECT s.SessionId,
                       sub.CustomerId,
                       o.PartnerCustomerId,
                       t.SlotDate, t.StartTime, t.EndTime,
                       s.Status AS SessionStatus,
                       sub.Status AS SubscriptionStatus
                FROM Sessions s
                JOIN Subscriptions sub ON sub.SubscriptionId = s.SubscriptionId
                JOIN TimeSlots t       ON t.TimeSlotId = s.TimeSlotId
                JOIN Orders o          ON o.OrderId = sub.OrderId
                WHERE s.SessionId = ?
                """;
        try (Connection connection = DBContext.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, sessionId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                PtSessionCancelInfo info = new PtSessionCancelInfo();
                info.setSessionId(rs.getInt("SessionId"));
                info.setCustomerId(rs.getInt("CustomerId"));
                int partner = rs.getInt("PartnerCustomerId");
                info.setPartnerCustomerId(rs.wasNull() ? null : partner);
                info.setSlotDate(rs.getDate("SlotDate").toLocalDate());
                info.setStartTime(rs.getTime("StartTime").toLocalTime());
                info.setEndTime(rs.getTime("EndTime").toLocalTime());
                info.setSessionStatus(rs.getString("SessionStatus"));
                info.setSubscriptionStatus(rs.getString("SubscriptionStatus"));
                return info;
            }
        }
    }

    /**
     * Hủy buổi tập: cập nhật trạng thái Sessions thành Cancelled với CancelType = ByCustomer.
     * <p>Quy tắc DB (CK_Sessions_CustomerCancel): CancelledByAccountId bắt buộc, RefundAgreed = 0.
     * Trước khi gọi, Service phải đã xác nhận: đúng chủ, Status = Scheduled, buổi chưa kết thúc.
     *
     * @param sessionId          mã buổi cần hủy
     * @param cancelledByAccountId tài khoản bấm hủy (lưu vào Sessions.CancelledByAccountId)
     * @return true nếu cập nhật đúng 1 dòng, false nếu buổi không tồn tại hoặc đã đổi trạng thái
     * @throws SQLException khi UPDATE lỗi
     */
    public boolean cancelSessionByCustomer(int sessionId, int cancelledByAccountId) throws SQLException {
        // SQL: chỉ UPDATE khi Status = 'Scheduled' để chống double-submit / người khác vừa đổi.
        // RefundAgreed = 0 vì BR-79: khách tự hủy không hoàn buổi.
        // CancelledAt = SYSDATETIME() vì chính là lúc khách bấm.
        String sql = """
                UPDATE s SET s.Status = 'Cancelled',
                             s.CancelType = 'ByCustomer',
                             s.CancelledAt = SYSDATETIME(),
                             s.CancelledByAccountId = ?,
                             s.RefundAgreed = 0
                FROM Sessions s
                WHERE s.SessionId = ?
                  AND s.Status = 'Scheduled'
                """;
        try (Connection connection = DBContext.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, cancelledByAccountId);
            statement.setInt(2, sessionId);
            return statement.executeUpdate() == 1;
        }
    }
}
