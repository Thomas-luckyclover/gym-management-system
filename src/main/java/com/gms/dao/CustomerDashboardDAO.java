package com.gms.dao;

import com.gms.dto.CustomerDashboardView.SubscriptionRow;
import com.gms.dto.CustomerDashboardView.UpcomingSessionRow;
import com.gms.util.DBContext;
import com.gms.util.DateTimeUtil;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Truy vấn dữ liệu cho Customer Dashboard (UC-41, SRS 9.1.1).
 *
 * Nguyên tắc: DAO chỉ ĐỌC trạng thái đang lưu trong DB — không tự tính lại
 * trạng thái hết hạn hay bảo lưu; việc đó là của JOB-01..04 (GBR-14).
 *
 * @author Nguyen Tuan Minh
 */
public class CustomerDashboardDAO {

    // =========================================================
    // 1. Kiểm tra quyền vào phòng tập hôm nay
    // =========================================================

    /**
     * Khách có đang có Membership hoặc DayPass còn hiệu lực hôm nay không?
     * Gói PT (PTSingle / PTCouple / SingleSession) KHÔNG cho vào phòng tập (DBR-47,
     * DBR-48, CBR-58).
     * Gói Frozen (đang bảo lưu) không được tính là hiệu lực.
     *
     * @param customerId CustomerId của khách đang đăng nhập.
     * @return true nếu có thể vào phòng tập hôm nay.
     * @throws SQLException lỗi kết nối hoặc lỗi câu SQL.
     */
    public boolean canUseGymToday(int customerId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM Subscriptions "
                + "WHERE CustomerId = ? "
                + "AND Status = 'Active' "
                + "AND SubscriptionType IN ('Membership', 'DayPass') "
                + "AND StartDate <= ? AND EndDate >= ?";

        try (Connection conn = DBContext.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, customerId);
            Date today = Date.valueOf(DateTimeUtil.today());
            stmt.setDate(2, today);
            stmt.setDate(3, today);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    // =========================================================
    // 2. Danh sách gói tập đang dùng / chưa bắt đầu
    // =========================================================

    /**
     * Lấy danh sách gói của khách: đang Active, Frozen, hoặc chưa bắt đầu
     * (StartDate > hôm nay, DBR-52).
     * Không lấy gói Cancelled hoặc Expired (đã hết hạn hoàn toàn).
     * Với gói PT: kèm số buổi đã dùng và còn lại ([CHỐT-D2]).
     * Với gói Frozen: kèm khoảng bảo lưu hiện tại (DBR-51).
     * Sắp xếp: gói đang Active trước, gói chưa bắt đầu sau; trong mỗi nhóm xếp theo
     * EndDate tăng dần.
     *
     * Lưu ý gói đôi (PTCouple): người đứng tên lưu ở Subscriptions.CustomerId,
     * người đi kèm lưu ở Orders.PartnerCustomerId — câu SQL dùng UNION ALL để lấy
     * cả hai.
     *
     * @param customerId CustomerId của khách đang đăng nhập.
     * @return danh sách gói; list rỗng nếu không có gói nào.
     * @throws SQLException lỗi kết nối hoặc lỗi câu SQL.
     */
    public List<SubscriptionRow> findActiveSubscriptions(int customerId) throws SQLException {
        String sql = "SELECT subCombined.SubscriptionId, subCombined.SubscriptionType, subCombined.PackageName, "
                + "subCombined.StartDate, subCombined.EndDate, subCombined.Status, subCombined.GeneratedSessionCount, "
                + "subCombined.RemainingSessions, subCombined.UsedSessions, "
                + "subCombined.FreezeStart, subCombined.FreezeEnd "
                + "FROM ("
                + "  SELECT sub.SubscriptionId, sub.SubscriptionType, o.PackageNameSnapshot AS PackageName, "
                + "  sub.StartDate, sub.EndDate, sub.Status, sub.GeneratedSessionCount, "
                + "  ("
                + "    SELECT COUNT(*) FROM Sessions s2 "
                + "    WHERE s2.SubscriptionId = sub.SubscriptionId "
                + "    AND s2.Status = 'Scheduled'"
                + "  ) AS RemainingSessions, "
                + "  ("
                + "    SELECT COUNT(*) FROM Sessions s3 "
                + "    WHERE s3.SubscriptionId = sub.SubscriptionId "
                + "    AND s3.Status IN ('CheckedIn', 'Missed')"
                + "  ) AS UsedSessions, "
                + "  fr.StartDate AS FreezeStart, COALESCE(fr.ActualEndDate, fr.PlannedEndDate) AS FreezeEnd, "
                + "  0 AS SortPriority "
                + "  FROM Subscriptions sub "
                + "  JOIN Orders o ON o.OrderId = sub.OrderId "
                + "  LEFT JOIN FreezeRecords fr ON fr.SubscriptionId = sub.SubscriptionId "
                + "    AND fr.ActualEndDate IS NULL "
                + "  WHERE sub.CustomerId = ? "
                + "  AND sub.Status IN ('Active', 'Frozen') "
                + "  AND sub.StartDate <= ? "
                + "  UNION ALL "
                + "  SELECT sub2.SubscriptionId, sub2.SubscriptionType, o2.PackageNameSnapshot AS PackageName, "
                + "  sub2.StartDate, sub2.EndDate, 'Pending' AS Status, sub2.GeneratedSessionCount, "
                + "  ("
                + "    SELECT COUNT(*) FROM Sessions s4 "
                + "    WHERE s4.SubscriptionId = sub2.SubscriptionId "
                + "    AND s4.Status = 'Scheduled'"
                + "  ) AS RemainingSessions, "
                + "  0 AS UsedSessions, "
                + "  NULL AS FreezeStart, NULL AS FreezeEnd, "
                + "  1 AS SortPriority "
                + "  FROM Subscriptions sub2 "
                + "  JOIN Orders o2 ON o2.OrderId = sub2.OrderId "
                + "  WHERE sub2.CustomerId = ? "
                + "  AND sub2.Status = 'Active' "
                + "  AND sub2.StartDate > ? "
                + ") AS subCombined "
                + "ORDER BY subCombined.SortPriority, subCombined.EndDate";

        LocalDate today = DateTimeUtil.today();
        List<SubscriptionRow> rows = new ArrayList<>();

        try (Connection conn = DBContext.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, customerId);
            stmt.setDate(2, Date.valueOf(today));
            stmt.setInt(3, customerId);
            stmt.setDate(4, Date.valueOf(today));

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String subType = rs.getString("SubscriptionType");
                    boolean isPt = "PTSingle".equals(subType)
                            || "PTCouple".equals(subType)
                            || "SingleSession".equals(subType);

                    Integer totalSessions = isPt ? rs.getInt("GeneratedSessionCount") : null;
                    Integer remaining = isPt ? rs.getInt("RemainingSessions") : null;
                    Integer used = isPt ? rs.getInt("UsedSessions") : null;

                    Date fsDate = rs.getDate("FreezeStart");
                    Date feDate = rs.getDate("FreezeEnd");

                    rows.add(new SubscriptionRow(
                            rs.getInt("SubscriptionId"),
                            subType,
                            rs.getString("PackageName"),
                            rs.getDate("StartDate").toLocalDate(),
                            rs.getDate("EndDate").toLocalDate(),
                            rs.getString("Status"),
                            totalSessions,
                            remaining,
                            used,
                            fsDate != null ? fsDate.toLocalDate() : null,
                            feDate != null ? feDate.toLocalDate() : null));
                }
            }
        }
        return rows;
    }

    // =========================================================
    // 3. Gói đôi — người đi kèm (PartnerCustomerId)
    // =========================================================

    public List<SubscriptionRow> findPartnerSubscriptions(int customerId) throws SQLException {
        String sql = "SELECT sub.SubscriptionId, sub.SubscriptionType, o.PackageNameSnapshot AS PackageName, "
                + "sub.StartDate, sub.EndDate, sub.Status, sub.GeneratedSessionCount, "
                + "("
                + "  SELECT COUNT(*) FROM Sessions s2 "
                + "  WHERE s2.SubscriptionId = sub.SubscriptionId "
                + "  AND s2.Status = 'Scheduled'"
                + ") AS RemainingSessions, "
                + "("
                + "  SELECT COUNT(*) FROM Sessions s3 "
                + "  WHERE s3.SubscriptionId = sub.SubscriptionId "
                + "  AND s3.Status IN ('CheckedIn', 'Missed')"
                + ") AS UsedSessions "
                + "FROM Subscriptions sub "
                + "JOIN Orders o ON o.OrderId = sub.OrderId "
                + "WHERE o.PartnerCustomerId = ? "
                + "AND sub.Status IN ('Active', 'Frozen') "
                + "ORDER BY sub.EndDate";

        List<SubscriptionRow> rows = new ArrayList<>();
        try (Connection conn = DBContext.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, customerId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    rows.add(new SubscriptionRow(
                            rs.getInt("SubscriptionId"),
                            rs.getString("SubscriptionType"),
                            rs.getString("PackageName"),
                            rs.getDate("StartDate").toLocalDate(),
                            rs.getDate("EndDate").toLocalDate(),
                            rs.getString("Status"),
                            rs.getObject("GeneratedSessionCount") != null ? rs.getInt("GeneratedSessionCount") : null,
                            rs.getObject("RemainingSessions") != null ? rs.getInt("RemainingSessions") : null,
                            rs.getObject("UsedSessions") != null ? rs.getInt("UsedSessions") : null,
                            null, null));
                }
            }
        }
        return rows;
    }

    // =========================================================
    // 4. Buổi PT sắp tới ([CHỐT-D3]: 5 buổi Scheduled gần nhất)
    // =========================================================

    public List<UpcomingSessionRow> findUpcomingSessions(int customerId, LocalDateTime now)
            throws SQLException {

        // Điều kiện khách có Membership / DayPass (nếu SingleSession) ngày đó,
        // không trong khoảng bảo lưu.
        String hasMembership = "EXISTS (SELECT 1 FROM Subscriptions m "
                + "WHERE m.CustomerId = sub.CustomerId "
                + "AND m.Status <> 'Cancelled' "
                + "AND (m.SubscriptionType = 'Membership' "
                + "     OR (sub.SubscriptionType = 'SingleSession' AND m.SubscriptionType = 'DayPass')) "
                + "AND t.SlotDate BETWEEN m.StartDate AND m.EndDate "
                + "AND NOT EXISTS (SELECT 1 FROM FreezeRecords f "
                + "  WHERE f.SubscriptionId = m.SubscriptionId "
                + "  AND t.SlotDate BETWEEN f.StartDate AND COALESCE(f.ActualEndDate, f.PlannedEndDate)))";

        // Lấy buổi của người đứng tên gói UNION người đi kèm (gói đôi)
        String sql = "SELECT TOP 5 s.SessionId, t.SlotDate, t.StartTime, t.EndTime, "
                + "ep.FullName AS PtName, o.PackageNameSnapshot AS PackageName, sub.SubscriptionType, sub.CustomerId, "
                + "CASE WHEN " + hasMembership + " THEN 0 ELSE 1 END AS MissingMembership "
                + "FROM Sessions s "
                + "JOIN TimeSlots t ON t.TimeSlotId = s.TimeSlotId "
                + "JOIN Subscriptions sub ON sub.SubscriptionId = s.SubscriptionId "
                + "JOIN Orders o ON o.OrderId = sub.OrderId "
                + "JOIN Employees e ON e.EmployeeId = s.PtEmployeeId "
                + "JOIN Persons ep ON ep.PersonId = e.PersonId "
                + "WHERE s.Status = 'Scheduled' "
                + "AND (t.SlotDate > ? OR (t.SlotDate = ? AND t.StartTime > CAST(? AS TIME))) "
                + "AND ("
                + "  sub.CustomerId = ? " // người đứng tên
                + "  OR o.PartnerCustomerId = ? " // người đi kèm (gói đôi)
                + ") "
                + "ORDER BY t.SlotDate, t.StartTime";

        List<UpcomingSessionRow> rows = new ArrayList<>();
        Date today = Date.valueOf(now.toLocalDate());

        try (Connection conn = DBContext.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setDate(1, today);
            stmt.setDate(2, today);
            stmt.setObject(3, now.toLocalTime());
            stmt.setInt(4, customerId);
            stmt.setInt(5, customerId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    rows.add(new UpcomingSessionRow(
                            rs.getInt("SessionId"),
                            rs.getDate("SlotDate").toLocalDate(),
                            rs.getTime("StartTime").toLocalTime(),
                            rs.getTime("EndTime").toLocalTime(),
                            rs.getString("PtName"),
                            rs.getString("PackageName"),
                            rs.getInt("MissingMembership") == 1));
                }
            }
        }
        return rows;
    }

    // =========================================================
    // 5. Đếm buổi PT rơi vào ngày không có Membership ([CHỐT-D1])
    // =========================================================

    public int countSessionsMissingMembership(int customerId) throws SQLException {
        String hasMembership = "EXISTS (SELECT 1 FROM Subscriptions m "
                + "WHERE m.CustomerId = sub.CustomerId "
                + "AND m.Status <> 'Cancelled' "
                + "AND m.SubscriptionType = 'Membership' "
                + "AND t.SlotDate BETWEEN m.StartDate AND m.EndDate "
                + "AND NOT EXISTS (SELECT 1 FROM FreezeRecords f "
                + "  WHERE f.SubscriptionId = m.SubscriptionId "
                + "  AND t.SlotDate BETWEEN f.StartDate AND COALESCE(f.ActualEndDate, f.PlannedEndDate)))";

        String sql = "SELECT COUNT(*) FROM Sessions s "
                + "JOIN TimeSlots t ON t.TimeSlotId = s.TimeSlotId "
                + "JOIN Subscriptions sub ON sub.SubscriptionId = s.SubscriptionId "
                + "JOIN Orders o ON o.OrderId = sub.OrderId "
                + "WHERE s.Status = 'Scheduled' "
                + "AND t.SlotDate >= ? "
                + "AND (sub.CustomerId = ? OR o.PartnerCustomerId = ?) "
                + "AND NOT " + hasMembership;

        try (Connection conn = DBContext.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setDate(1, Date.valueOf(DateTimeUtil.today()));
            stmt.setInt(2, customerId);
            stmt.setInt(3, customerId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    // =========================================================
    // 6. Đơn chờ thanh toán (Pending order) ([CHỐT-D1])
    // =========================================================

    public Object[] findPendingOrder(int customerId) throws SQLException {
        String sql = "SELECT TOP 1 o.OrderId, o.PaymentDueAt "
                + "FROM Orders o "
                + "WHERE o.HolderCustomerId = ? "
                + "AND o.Status = 'Pending' "
                + "AND o.PaymentDueAt > ? "
                + "ORDER BY o.PaymentDueAt";

        try (Connection conn = DBContext.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, customerId);
            stmt.setObject(2, java.sql.Timestamp.valueOf(DateTimeUtil.now()));
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int orderId = rs.getInt("OrderId");
                    java.sql.Timestamp ts = rs.getTimestamp("PaymentDueAt");
                    LocalDateTime deadline = ts != null ? ts.toLocalDateTime() : null;
                    return new Object[] { orderId, deadline };
                }
            }
        }
        return null;
    }
}
