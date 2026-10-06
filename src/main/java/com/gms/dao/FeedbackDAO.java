package com.gms.dao;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;

/** Lưu góp ý và đánh giá PT theo tháng (DBR-66, DBR-67). */
public class FeedbackDAO {
    /** Kiểm tra khách có buổi đã chốt với PT trong tháng (CBR-116). */
    public boolean canRate(Connection conn, int customerId, int ptId, LocalDate month) throws SQLException {
        String sql = "SELECT TOP (1) 1 FROM Sessions s JOIN TimeSlots t ON t.TimeSlotId=s.TimeSlotId "
                + "JOIN Subscriptions sub ON sub.SubscriptionId=s.SubscriptionId JOIN Orders o ON o.OrderId=sub.OrderId "
                + "JOIN Employees e ON e.EmployeeId=s.PtEmployeeId WHERE s.PtEmployeeId=? AND e.Position='PT' "
                + "AND (sub.CustomerId=? OR (sub.SubscriptionType='PTCouple' AND o.PartnerCustomerId=?)) "
                + "AND s.Status IN ('CheckedIn','Missed') AND t.SlotDate>=? AND t.SlotDate<?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, ptId);
            stmt.setInt(2, customerId);
            stmt.setInt(3, customerId);
            stmt.setDate(4, Date.valueOf(month));
            stmt.setDate(5, Date.valueOf(month.plusMonths(1)));
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** Sửa phiếu tháng và đưa về Unread (DBR-67). */
    public boolean updateRating(Connection conn, int customerId, int ptId, int rating, LocalDate month,
            String content) throws SQLException {
        String sql = "UPDATE Feedbacks SET Rating=?,Content=?,UpdatedAt=SYSDATETIME(), "
                + "IsRead=0,ReadAt=NULL,ReadByEmployeeId=NULL WHERE CustomerId=? AND RatedPtEmployeeId=? AND RatingMonth=?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, rating);
            stmt.setNString(2, content);
            stmt.setInt(3, customerId);
            stmt.setInt(4, ptId);
            stmt.setDate(5, Date.valueOf(month));
            return stmt.executeUpdate() == 1;
        }
    }

    /** Thêm góp ý thường hoặc đánh giá mới. */
    public void insert(Connection conn, int customerId, Integer ptId, Integer rating, LocalDate month,
            String content) throws SQLException {
        String sql = "INSERT INTO Feedbacks(CustomerId,RatedPtEmployeeId,Rating,RatingMonth,Content) VALUES(?,?,?,?,?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, customerId);
            if (ptId == null) {
                stmt.setNull(2, Types.INTEGER);
                stmt.setNull(3, Types.TINYINT);
                stmt.setNull(4, Types.DATE);
            } else {
                stmt.setInt(2, ptId);
                stmt.setInt(3, rating);
                stmt.setDate(4, Date.valueOf(month));
            }
            stmt.setNString(5, content);
            stmt.executeUpdate();
        }
    }
}
