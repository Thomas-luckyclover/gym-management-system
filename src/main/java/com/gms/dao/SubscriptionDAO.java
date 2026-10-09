package com.gms.dao;

import com.gms.dto.PtClientView;
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
 * Truy vấn bảng Subscriptions (quyền dùng sinh ra từ đơn đã thanh toán) — phần đọc dùng cho PT Dashboard.
 *
 * @author Le Cong Minh
 */
public class SubscriptionDAO {

    /**
     * Lấy danh sách khách ĐƯỢC GIAO cho 1 PT (SRS 9.1.2 "Khách của tôi", GBR-10).
     *
     * "Được giao vào ngày X" = có gói PT với PT này (Subscriptions.PtEmployeeId), chưa bị hủy, và X nằm trong
     * [StartDate, EndDate] của gói (EndDate tính cả ngày đó — GBR-03).
     * Xét theo ngày X (ngày PT đang lọc trên dashboard) để khối này khớp với khối "Buổi trong ngày":
     * xem lại ngày cũ thì thấy khách của ngày đó, dù hôm nay gói đã hết hạn (trạng thái lúc này có thể là Expired).
     * Tính cả BUỔI LẺ khách tự đặt (SingleSession) — nhóm chốt 30/09/2026. Buổi lẻ chỉ có hạn đúng ngày của buổi,
     * nên khách buổi lẻ chỉ hiện ở đúng ngày đó.
     * Gói couple có 2 người: người đứng tên (Subscriptions.CustomerId) + người đi kèm (Orders.PartnerCustomerId),
     * nên câu SQL gộp 2 phần bằng UNION ALL. Gói hết hạn sớm nhất lên đầu để PT biết ai sắp cần gia hạn.
     *
     * VD PT 5 (Sơn), ngày 30/09 → 1 khách: KH-1006, Gói PT cá nhân, hết hạn 07/10/2026.
     *     PT 7 (David), ngày 23/09 → 1 khách: Nguyễn Thị Hạnh, Buổi lẻ (hết hạn đúng 23/09); ngày 30/09 → 0 khách.
     *
     * @param ptEmployeeId EmployeeId của PT đang đăng nhập.
     * @param onDate       ngày muốn xem (ngày đang lọc trên dashboard, mặc định hôm nay).
     * @return danh sách khách; list rỗng nếu PT chưa được giao khách nào.
     * @throws SQLException lỗi kết nối hoặc lỗi câu SQL.
     */
    public List<PtClientView> findClientsOfPt(int ptEmployeeId, LocalDate onDate) throws SQLException {
        String sql = "SELECT p.FullName, c.CustomerCode, sub.SubscriptionType, sub.EndDate, sub.Status, 0 AS IsPartner "
                + "FROM Subscriptions sub "
                + "JOIN Customers c ON c.CustomerId = sub.CustomerId "
                + "JOIN Persons p ON p.PersonId = c.PersonId "
                + "WHERE sub.PtEmployeeId = ? AND sub.Status <> 'Cancelled' AND ? BETWEEN sub.StartDate AND sub.EndDate "
                + "UNION ALL "
                + "SELECT pp.FullName, pc.CustomerCode, sub.SubscriptionType, sub.EndDate, sub.Status, 1 AS IsPartner "
                + "FROM Subscriptions sub "
                + "JOIN Orders o ON o.OrderId = sub.OrderId "
                + "JOIN Customers pc ON pc.CustomerId = o.PartnerCustomerId "
                + "JOIN Persons pp ON pp.PersonId = pc.PersonId "
                + "WHERE sub.PtEmployeeId = ? AND sub.Status <> 'Cancelled' AND ? BETWEEN sub.StartDate AND sub.EndDate "
                + "ORDER BY EndDate, FullName";

        List<PtClientView> clients = new ArrayList<>();
        try (Connection conn = DBContext.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, ptEmployeeId);
            stmt.setDate(2, Date.valueOf(onDate));
            stmt.setInt(3, ptEmployeeId);
            stmt.setDate(4, Date.valueOf(onDate));

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    clients.add(new PtClientView(
                            rs.getString("FullName"),
                            rs.getString("CustomerCode"),
                            rs.getString("SubscriptionType"),
                            rs.getDate("EndDate").toLocalDate(),
                            rs.getString("Status"),
                            rs.getInt("IsPartner") == 1));
                }
            }
        }
        return clients;
    }
}
