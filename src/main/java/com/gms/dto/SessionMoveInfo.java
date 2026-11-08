package com.gms.dto;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * DTO lần DỜI BUỔI gần nhất của 1 buổi — DAO TẠO: MyBookingDAO.findMoves (bảng SessionMoveLogs).
 * Không in thẳng: MyBookingService.moveNote đổi thành câu "PT dời lịch · thay buổi T3 05/11" (MyPtSessionRow.moveNote).
 */
public class SessionMoveInfo {
    // ===== DÙNG ĐỂ TẠO CÂU moveNote =====
    // ← SessionMoveLogs.MoveType (lần dời MỚI NHẤT) → "Bạn đổi buổi" / "PT đổi giúp bạn" / "PT dời lịch" / "Chuyển PT dạy thay"
    private String moveType;
    // ← TimeSlots.SlotDate của khung CŨ (lần dời đầu tiên) → "thay buổi T3 05/11"
    private final LocalDate originalDate;
    // ← TimeSlots.StartTime của khung cũ
    private final LocalTime originalStart;

    // ===== KHÔNG IN RA MÀN (chỉ dùng trong code) =====
    private final int sessionId;

    public SessionMoveInfo(int sessionId, String moveType, LocalDate originalDate, LocalTime originalStart) {
        this.sessionId = sessionId;
        this.moveType = moveType;
        this.originalDate = originalDate;
        this.originalStart = originalStart;
    }

    public int getSessionId() { return sessionId; }
    public String getMoveType() { return moveType; }
    public void setMoveType(String moveType) { this.moveType = moveType; }
    public LocalDate getOriginalDate() { return originalDate; }
    public LocalTime getOriginalStart() { return originalStart; }
}
