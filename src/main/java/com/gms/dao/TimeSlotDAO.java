package com.gms.dao;

import com.gms.util.DBContext;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;

/**
 * Truy vấn bảng TimeSlots (khung giờ rảnh PT công bố) — phần đọc dùng cho PT Dashboard.
 *
 * @author Le Cong Minh
 */
public class TimeSlotDAO {

    /**
     * Đếm khung giờ còn trống (Free — chưa ai đặt, chưa ai giữ chỗ) của PT trong 1 khoảng ngày.
     *
     * VD PT 5, từ hôm nay tới Chủ nhật → số slot khách còn đặt được trong tuần này.
     *
     * @param ptEmployeeId EmployeeId của PT.
     * @param fromDate     từ ngày (tính cả).
     * @param toDate       tới ngày (tính cả).
     * @return số slot trống.
     * @throws SQLException lỗi kết nối hoặc lỗi câu SQL.
     */
    public int countFreeSlots(int ptEmployeeId, LocalDate fromDate, LocalDate toDate) throws SQLException {
        String sql = "SELECT COUNT(*) FROM TimeSlots "
                + "WHERE PtEmployeeId = ? AND Status = 'Free' AND SlotDate BETWEEN ? AND ?";

        try (Connection conn = DBContext.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, ptEmployeeId);
            stmt.setDate(2, Date.valueOf(fromDate));
            stmt.setDate(3, Date.valueOf(toDate));

            try (ResultSet rs = stmt.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }
}
