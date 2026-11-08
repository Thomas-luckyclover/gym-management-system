package com.gms.dao;

import com.gms.dto.SessionMoveInfo;
import com.gms.util.DBContext;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * DAO MÀN GÓI CỦA TÔI (khách) — chỉ 1 câu: lịch sử dời buổi của 1 gói.
 *
 * AI GỌI DAO NÀY?  MyBookingService.fillPackageDetail (B2: ghi chú "PT dời lịch · thay buổi …" cho buổi khác giờ).
 * Gói và buổi đọc qua MyPtSessionDAO (dùng chung với màn Lịch tập).
 */
public class MyBookingDAO {
    // ============================================================================================================
    // PHỤC VỤ HÀM CHÍNH getMyBookings (bước fillPackageDetail) — ĐỌC SessionMoveLogs, không khóa
    // ============================================================================================================

    // Đọc SessionMoveLogs JOIN Sessions JOIN TimeSlots (khung CŨ) của mọi buổi trong gói, xếp theo thời điểm dời.
    // Trả về: Map SessionId → SessionMoveInfo (ngày giờ khung GỐC = lần dời đầu, loại dời = lần dời MỚI NHẤT).
    public Map<Integer, SessionMoveInfo> findMoves(int subscriptionId) throws SQLException {
        String sql = """
                SELECT m.SessionId, m.MoveType, oldSlot.SlotDate, oldSlot.StartTime
                FROM SessionMoveLogs m
                JOIN Sessions s        ON s.SessionId = m.SessionId
                JOIN TimeSlots oldSlot ON oldSlot.TimeSlotId = m.OldTimeSlotId
                WHERE s.SubscriptionId = ?
                ORDER BY m.MovedAt, m.SessionMoveLogId
                """;

        Map<Integer, SessionMoveInfo> moves = new LinkedHashMap<>();

        try (Connection connection = DBContext.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, subscriptionId);

            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    int sessionId = rs.getInt("SessionId");
                    SessionMoveInfo move = moves.get(sessionId);
                    // Lần dời đầu tiên của buổi → giữ ngày giờ khung gốc
                    if (move == null) {
                        moves.put(sessionId, new SessionMoveInfo(
                                sessionId,
                                rs.getString("MoveType"),
                                rs.getDate("SlotDate").toLocalDate(),
                                rs.getTime("StartTime").toLocalTime()));
                    } else {
                        // Lần dời sau → chỉ cập nhật loại dời (lấy lần mới nhất)
                        move.setMoveType(rs.getString("MoveType"));
                    }
                }
            }
        }
        return moves;
    }
}
