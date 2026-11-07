package com.gms.dao;

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
 * DAO CỦA JOB-03 — TỰ ĐỘNG HẾT HẠN VÉ NGÀY CHƯA SỬ DỤNG.
 *
 * Chỉ gồm 2 hàm:
 *   1. findExpiredUnusedDayPassIds   lấy danh sách mã vé ngày quá hạn chưa check-in
 *   2. markDayPassExpired            đổi trạng thái vé ngày sang Expired
 */
public class ExpireDayPassDAO {

    /**
     * BƯỚC 1 — Tìm các vé ngày (DayPass) đang Active nhưng đã qua ngày hiệu lực (EndDate < today)
     * và chưa có lượt check-in nào.
     */
    public List<Integer> findExpiredUnusedDayPassIds(LocalDate today) throws SQLException {
        String sql = "SELECT s.SubscriptionId "
                + "FROM Subscriptions s "
                + "WHERE s.SubscriptionType = 'DayPass' "
                + "  AND s.Status = 'Active' "
                + "  AND s.EndDate < ? "
                + "  AND NOT EXISTS (SELECT 1 FROM CheckIns ci WHERE ci.SubscriptionId = s.SubscriptionId)";

        List<Integer> list = new ArrayList<>();
        try (Connection conn = DBContext.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setDate(1, Date.valueOf(today));
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(rs.getInt("SubscriptionId"));
                }
            }
        }
        return list;
    }

    /**
     * BƯỚC 2 — Đổi trạng thái vé ngày từ Active sang Expired.
     */
    public boolean markDayPassExpired(Connection conn, int subscriptionId) throws SQLException {
        String sql = "UPDATE Subscriptions "
                + "SET Status = 'Expired' "
                + "WHERE SubscriptionId = ? AND SubscriptionType = 'DayPass' AND Status = 'Active'";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, subscriptionId);
            return stmt.executeUpdate() > 0;
        }
    }
}
