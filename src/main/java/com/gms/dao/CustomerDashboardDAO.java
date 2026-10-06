package com.gms.dao;

import com.gms.dto.CustomerDashboardView.SubscriptionRow;
import com.gms.dto.CustomerDashboardView.UpcomingSessionRow;
import com.gms.util.DBContext;
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
     * Gói PT (PTSingle / PTCouple / SingleSession) KHÔNG cho vào phòng tập (DBR-47, DBR-48, CBR-58).
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
                + "AND ? BETWEEN StartDate AND EndDate";

        try (Connection conn = DBContext.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, customerId);
            stmt.setDate(2, Date.valueOf(LocalDate.now()));
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    // =========================================================
    // 2. Danh sách gói tập đang dùng / chưa bắt đầu
    // =========================================================

    /**
     * Lấy danh sách gói của khách: đang Active, Frozen, hoặc chưa bắt đầu (StartDate > hôm nay, DBR-52).
     * Không lấy gói Cancelled hoặc Expired (đã hết hạn hoàn toàn).
     * Với gói PT: kèm số buổi đã dùng và còn lại ([CHỐT-D2]).
     * Với gói Frozen: kèm khoảng bảo lưu hiện tại (DBR-51).
     * Sắp xếp: gói đang Active trước, gói chưa bắt đầu sau; trong mỗi nhóm xếp theo EndDate tăng dần.
     *
     * Lưu ý gói đôi (PTCouple): người đứng tên lưu ở Subscriptions.CustomerId,
     * người đi kèm lưu ở Orders.PartnerCustomerId — câu SQL dùng UNION ALL để lấy cả hai.
     *
     * @param customerId CustomerId của khách đang đăng nhập.
     * @return danh sách gói; list rỗng nếu không có gói nào.
     * @throws SQLException lỗi kết nối hoặc lỗi câu SQL.
     */
    public List<SubscriptionRow> findActiveSubscriptions(int customerId) throws SQLException {
        // Câu SQL chính: người đứng tên gói
        String sql = "SELECT sub.SubscriptionId, sub.SubscriptionType, pkg.PackageName, "
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
                + ") AS UsedSessions, "
                + "fr.StartDate AS FreezeStart, COALESCE(fr.ActualEndDate, fr.PlannedEndDate) AS FreezeEnd "
                + "FROM Subscriptions sub "
                + "JOIN Packages pkg ON pkg.PackageId = sub.PackageId "
                + "LEFT JOIN FreezeRecords fr ON fr.SubscriptionId = sub.SubscriptionId "
                + "  AND fr.ActualEndDate IS NULL "
                + "WHERE sub.CustomerId = ? "
                + "AND sub.Status IN ('Active', 'Frozen') "
                + "AND sub.StartDate <= ? "          // đã bắt đầu
                + "UNION ALL "
                // Gói nối tiếp chưa bắt đầu (DBR-52): StartDate > hôm nay
                + "SELECT sub2.SubscriptionId, sub2.SubscriptionType, pkg2.PackageName, "
                + "sub2.StartDate, sub2.EndDate, 'Pending' AS Status, sub2.GeneratedSessionCount, "
                + "("
                + "  SELECT COUNT(*) FROM Sessions s4 "
                + "  WHERE s4.SubscriptionId = sub2.SubscriptionId "
                + "  AND s4.Status = 'Scheduled'"
                + ") AS RemainingSessions, "
                + "0 AS UsedSessions, "
                + "NULL AS FreezeStart, NULL AS FreezeEnd "
                + "FROM Subscriptions sub2 "
                + "JOIN Packages pkg2 ON pkg2.PackageId = sub2.PackageId "
                + "WHERE sub2.CustomerId = ? "
                + "AND sub2.Status = 'Active' "
                + "AND sub2.StartDate > ? "          // chưa bắt đầu
                + "ORDER BY "
                + "CASE WHEN Status = 'Pending' THEN 1 ELSE 0 END, "
                + "EndDate";

        LocalDate today = LocalDate.now();
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
                    Integer remaining     = isPt ? rs.getInt("RemainingSessions")     : null;
                    Integer used          = isPt ? rs.getInt("UsedSessions")          : null;

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

    /**
     * Lấy gói PT mà khách là NGƯỜI ĐI KÈM (Orders.PartnerCustomerId).
     * Gói đôi dùng chung quota buổi với người đứng tên (DBR-54, [CHỐT-D2]).
     * Chỉ lấy gói đang Active / Frozen và chưa Cancelled.
     *
     * @param customerId CustomerId của khách đang đăng nhập.
     * @return danh sách gói đôi mà khách là partner; list rỗng nếu không có.
     * @throws SQLException lỗi kết nối hoặc lỗi câu SQL.
     */
    public List<SubscriptionRow> findPartnerSubscriptions(int customerId) throws SQLException {
        String sql = "SELECT sub.SubscriptionId, sub.SubscriptionType, pkg.PackageName, "
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
                + "JOIN Packages pkg ON pkg.PackageId = sub.PackageId "
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
                            rs.getInt("GeneratedSessionCount"),
                            rs.getInt("RemainingSessions"),
                            rs.getInt("UsedSessions"),
                            null, null));
                }
            }
        }
        return rows;
    }

    // =========================================================
    // 4. Buổi PT sắp tới ([CHỐT-D3]: 5 buổi Scheduled gần nhất)
    // =========================================================

    /**
     * Lấy tối đa 5 buổi PT ở trạng thái Scheduled gần nhất từ thời điểm hiện tại.
     * Bao gồm cả buổi của gói đôi mà khách là thành viên (DBR-37).
     * Với mỗi buổi: kiểm tra khách có Membership còn hiệu lực ngày đó không (CBR-66).
     *
     * @param customerId   CustomerId của khách đang đăng nhập.
     * @param now          Thời điểm hiện tại (so sánh với SlotDate + StartTime).
     * @return danh sách buổi sắp tới; list rỗng nếu không có.
     * @throws SQLException lỗi kết nối hoặc lỗi câu SQL.
     */
    public List<UpcomingSessionRow> findUpcomingSessions(int customerId, LocalDateTime now)
            throws SQLException {

        // Điều kiện khách có Membership / DayPass (nếu SingleSession) ngày đó,
        // không trong khoảng bảo lưu — tái dụng logic từ SessionDAO.HAS_MEMBERSHIP_ON_SLOT_DATE
        String hasMembership =
                "EXISTS (SELECT 1 FROM Subscriptions m "
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
                + "ep.FullName AS PtName, pkg.PackageName, sub.SubscriptionType, sub.CustomerId, "
                + "CASE WHEN " + hasMembership + " THEN 0 ELSE 1 END AS MissingMembership "
                + "FROM Sessions s "
                + "JOIN TimeSlots t ON t.TimeSlotId = s.TimeSlotId "
                + "JOIN Subscriptions sub ON sub.SubscriptionId = s.SubscriptionId "
                + "JOIN Packages pkg ON pkg.PackageId = sub.PackageId "
                + "JOIN Employees e ON e.EmployeeId = s.PtEmployeeId "
                + "JOIN Persons ep ON ep.PersonId = e.PersonId "
                + "JOIN Orders o ON o.OrderId = sub.OrderId "
                + "WHERE s.Status = 'Scheduled' "
                + "AND (t.SlotDate > ? OR (t.SlotDate = ? AND t.StartTime > ?)) "
                + "AND ("
                + "  sub.CustomerId = ? "                           // người đứng tên
                + "  OR o.PartnerCustomerId = ? "                   // người đi kèm (gói đôi)
                + ") "
                + "ORDER BY t.SlotDate, t.StartTime";

        List<UpcomingSessionRow> rows = new ArrayList<>();
        Date today = Date.valueOf(now.toLocalDate());
        java.sql.Time nowTime = java.sql.Time.valueOf(now.toLocalTime());

        try (Connection conn = DBContext.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setDate(1, today);
            stmt.setDate(2, today);
            stmt.setTime(3, nowTime);
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

    /**
     * Đếm số buổi PT Scheduled của khách rơi vào ngày không có Membership còn hiệu lực (CBR-66).
     * Dùng để quyết định có hiện banner "Có X buổi PT rơi vào ngày không có gói tập" không.
     *
     * @param customerId CustomerId của khách đang đăng nhập.
     * @return số buổi bị thiếu Membership; 0 nếu không có.
     * @throws SQLException lỗi kết nối hoặc lỗi câu SQL.
     */
    public int countSessionsMissingMembership(int customerId) throws SQLException {
        String hasMembership =
                "EXISTS (SELECT 1 FROM Subscriptions m "
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
            stmt.setDate(1, Date.valueOf(LocalDate.now()));
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

    /**
     * Tìm đơn hàng đầu tiên ở trạng thái Pending của khách còn trong hạn thanh toán.
     * Trả về mảng [orderId, expiresAt] hoặc null nếu không có đơn Pending nào.
     *
     * @param customerId CustomerId của khách đang đăng nhập.
     * @return Object[] {orderId (Integer), expiresAt (LocalDateTime)} hoặc null.
     * @throws SQLException lỗi kết nối hoặc lỗi câu SQL.
     */
    public Object[] findPendingOrder(int customerId) throws SQLException {
        String sql = "SELECT TOP 1 o.OrderId, o.PaymentDeadline "
                + "FROM Orders o "
                + "WHERE o.CustomerId = ? "
                + "AND o.Status = 'Pending' "
                + "AND o.PaymentDeadline > ? "
                + "ORDER BY o.PaymentDeadline";

        try (Connection conn = DBContext.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, customerId);
            stmt.setObject(2, java.sql.Timestamp.valueOf(LocalDateTime.now()));
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int orderId = rs.getInt("OrderId");
                    java.sql.Timestamp ts = rs.getTimestamp("PaymentDeadline");
                    LocalDateTime deadline = ts != null ? ts.toLocalDateTime() : null;
                    return new Object[]{orderId, deadline};
                }
            }
        }
        return null;
    }
}
