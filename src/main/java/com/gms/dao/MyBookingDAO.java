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
 * DAO RIÊNG CỦA MÀN "GÓI CỦA TÔI" (/customer/bookings). Chỉ ĐỌC.
 *
 *   findMoves   lịch sử dời buổi của 1 gói → giải thích "Buổi khác giờ"
 *
 * Gói và buổi đọc chung MyPtSessionDAO (màn Lịch tập), ở đây chỉ thêm phần riêng.
 */
public class MyBookingDAO {

    /**
     * HÀM CHÍNH — MyBookingService.fillPackageDetail gọi ở B2 (chỉ khi gói có buổi lệch giờ).
     * Các buổi của 1 gói đã từng bị dời (bảng SessionMoveLogs), trả về theo mã buổi.
     * Service đã chắc chắn gói thuộc khách trước khi gọi → ở đây chỉ lọc theo mã gói.
     * Ví dụ: {812 → (CustomerSwap, giờ gốc T6 11/09 18:00)}
     */
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

        // B1: mở kết nối, gán mã gói
        try (Connection connection = DBContext.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, subscriptionId);

            // B2: lần dời đầu giữ giờ gốc; các lần sau chỉ cập nhật "ai dời" (lần cuối)
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    int sessionId = rs.getInt("SessionId");
                    SessionMoveInfo move = moves.get(sessionId);
                    if (move == null) {
                        moves.put(sessionId, new SessionMoveInfo(sessionId, rs.getString("MoveType"),
                                rs.getDate("SlotDate").toLocalDate(), rs.getTime("StartTime").toLocalTime()));
                    } else {
                        move.setMoveType(rs.getString("MoveType"));
                    }
                }
            }
        }
        return moves;
    }

}
