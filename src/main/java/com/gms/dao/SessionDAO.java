package com.gms.dao;

import com.gms.dto.PtSessionView;
import com.gms.dto.SessionStatRow;
import com.gms.util.DBContext;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Truy vấn bảng Sessions (buổi PT) — phần đọc dùng cho PT Dashboard.
 *
 * Ngày của buổi nằm ở TimeSlots.SlotDate (buổi chiếm khung giờ nào), nên câu SQL JOIN TimeSlots.
 * DAO chỉ ĐỌC trạng thái đang lưu (GBR-14: đổi trạng thái là việc của điểm danh / JOB-04), không tự tính lại.
 *
 * @author Le Cong Minh
 */
public class SessionDAO {

    /**
     * Điều kiện "khách {customerColumn} được phép tập vào ngày t.SlotDate" (CBR-60, CBR-64, CBR-66):
     *   - có quyền dùng chưa bị hủy, ngày buổi nằm trong [StartDate, EndDate] (EndDate tính cả ngày đó — GBR-03)
     *   - loại quyền dùng được chấp nhận:
     *       · Buổi thuộc gói PT (PTSingle, PTCouple): CHỈ gói tập (Membership) — vé ngày không thay được (CBR-64)
     *       · Buổi lẻ (SingleSession): gói tập, HOẶC gói ngày (DayPass) của chính hôm đó — khách vãng lai
     *         mua gói ngày rồi mua buổi lẻ hôm nay tại quầy (CBR-60)
     *   - và ngày đó KHÔNG nằm trong khoảng bảo lưu của gói (bảo lưu = tạm ngừng dùng; gói ngày không bảo lưu được)
     */
    private static final String HAS_MEMBERSHIP_ON_SLOT_DATE =
            "EXISTS (SELECT 1 FROM Subscriptions m "
            + "WHERE m.CustomerId = %s AND m.Status <> 'Cancelled' "
            + "AND (m.SubscriptionType = 'Membership' "
            + "     OR (sub.SubscriptionType = 'SingleSession' AND m.SubscriptionType = 'DayPass')) "
            + "AND t.SlotDate BETWEEN m.StartDate AND m.EndDate "
            + "AND NOT EXISTS (SELECT 1 FROM FreezeRecords f WHERE f.SubscriptionId = m.SubscriptionId "
            + "AND t.SlotDate BETWEEN f.StartDate AND COALESCE(f.ActualEndDate, f.PlannedEndDate)))";

    /**
     * Lấy các buổi của 1 PT trong 1 khoảng ngày, sớm nhất lên đầu.
     *
     * Tên khách lấy từ người đứng tên gói; gói couple lấy thêm người đi kèm (Orders.PartnerCustomerId).
     * Không lấy SĐT / email — dashboard chỉ cần tên.
     *
     * VD findByPt(7, 23/09, 23/09, false) → 1 buổi lẻ 10:00–11:30, khách có gói ngày 23/09 → missingMembership = false
     *    (CBR-60). Nếu đó là buổi của gói PT thì gói ngày không đủ → missingMembership = true (CBR-64).
     *
     * @param ptEmployeeId  EmployeeId của PT đang đăng nhập (chỉ lấy buổi PT này đang giữ — GBR-10).
     * @param fromDate      từ ngày (tính cả).
     * @param toDate        tới ngày (tính cả).
     * @param scheduledOnly true = chỉ lấy buổi Scheduled (dùng cho khối Buổi cần điểm danh); false = mọi trạng thái.
     * @return danh sách buổi; list rỗng nếu không có buổi nào.
     * @throws SQLException lỗi kết nối hoặc lỗi câu SQL.
     */
    public List<PtSessionView> findByPt(int ptEmployeeId, LocalDate fromDate, LocalDate toDate, boolean scheduledOnly)
            throws SQLException {
        // Gói couple: buổi "không diễn ra" chỉ khi CẢ HAI người đều không có gói tập (CBR-64).
        // Gói đơn: không có người đi kèm → chỉ xét người đứng tên.
        String sql = "SELECT s.SessionId, t.SlotDate, t.StartTime, t.EndTime, s.Status, s.MissedReason, "
                + "sub.SubscriptionType, p.FullName AS CustomerName, pp.FullName AS PartnerName, "
                + "CASE WHEN " + String.format(HAS_MEMBERSHIP_ON_SLOT_DATE, "sub.CustomerId") + " THEN 1 "
                + "     WHEN o.PartnerCustomerId IS NOT NULL AND "
                + String.format(HAS_MEMBERSHIP_ON_SLOT_DATE, "o.PartnerCustomerId") + " THEN 1 "
                + "     ELSE 0 END AS HasMembership "
                + "FROM Sessions s "
                + "JOIN TimeSlots t ON t.TimeSlotId = s.TimeSlotId "
                + "JOIN Subscriptions sub ON sub.SubscriptionId = s.SubscriptionId "
                + "JOIN Customers c ON c.CustomerId = sub.CustomerId "
                + "JOIN Persons p ON p.PersonId = c.PersonId "
                + "JOIN Orders o ON o.OrderId = sub.OrderId "
                + "LEFT JOIN Customers pc ON pc.CustomerId = o.PartnerCustomerId "
                + "LEFT JOIN Persons pp ON pp.PersonId = pc.PersonId "
                + "WHERE s.PtEmployeeId = ? AND t.SlotDate BETWEEN ? AND ? "
                + (scheduledOnly ? "AND s.Status = 'Scheduled' " : "")
                + "ORDER BY t.SlotDate, t.StartTime";

        List<PtSessionView> sessions = new ArrayList<>();
        try (Connection conn = DBContext.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, ptEmployeeId);
            stmt.setDate(2, Date.valueOf(fromDate));
            stmt.setDate(3, Date.valueOf(toDate));

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    sessions.add(new PtSessionView(
                            rs.getInt("SessionId"),
                            rs.getDate("SlotDate").toLocalDate(),
                            rs.getTime("StartTime").toLocalTime(),
                            rs.getTime("EndTime").toLocalTime(),
                            rs.getString("Status"),
                            rs.getString("SubscriptionType"),
                            rs.getString("CustomerName"),
                            rs.getString("PartnerName"),
                            rs.getString("MissedReason"),
                            rs.getInt("HasMembership") == 0));
                }
            }
        }
        return sessions;
    }

    /**
     * Lấy số liệu thô của mọi buổi PT trong 1 khoảng ngày — để Service tự cộng hoa hồng, vẽ biểu đồ, đếm tuần này.
     * DAO KHÔNG lọc "buổi nào được tính hoa hồng": luật đó (CBR-80…82, CBR-55, CBR-57) nằm ở PtDashboardService.
     *
     * VD findStatRows(5, 01/09, 30/09) → 6 dòng: 3 Completed, 1 Missed, 2 Scheduled (mỗi dòng 280.000đ hoa hồng).
     *
     * @param ptEmployeeId PT đang giữ buổi (chuyển PT thì hoa hồng theo PT mới — Sessions.PtEmployeeId).
     * @param fromDate     từ ngày (tính cả).
     * @param toDate       tới ngày (tính cả).
     * @return danh sách buổi; list rỗng nếu không có buổi nào.
     * @throws SQLException lỗi kết nối hoặc lỗi câu SQL.
     */
    public List<SessionStatRow> findStatRows(int ptEmployeeId, LocalDate fromDate, LocalDate toDate)
            throws SQLException {
        String sql = "SELECT t.SlotDate, s.Status, s.CancelType, sub.SubscriptionType, s.CommissionAmount "
                + "FROM Sessions s "
                + "JOIN TimeSlots t ON t.TimeSlotId = s.TimeSlotId "
                + "JOIN Subscriptions sub ON sub.SubscriptionId = s.SubscriptionId "
                + "WHERE s.PtEmployeeId = ? AND t.SlotDate BETWEEN ? AND ?";

        List<SessionStatRow> rows = new ArrayList<>();
        try (Connection conn = DBContext.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, ptEmployeeId);
            stmt.setDate(2, Date.valueOf(fromDate));
            stmt.setDate(3, Date.valueOf(toDate));

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    rows.add(new SessionStatRow(
                            rs.getDate("SlotDate").toLocalDate(),
                            rs.getString("Status"),
                            rs.getString("CancelType"),
                            rs.getString("SubscriptionType"),
                            rs.getLong("CommissionAmount")));
                }
            }
        }
        return rows;
    }
}
