package com.gms.dto;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Buổi đã bị dời/đổi: lần dời ĐẦU TIÊN cho biết giờ gốc, lần dời CUỐI cho biết ai dời.
 * Màn Gói của tôi dùng để ghi lý do cho "Buổi khác giờ".
 *
 *   Ai tạo?   MyBookingDAO.findMoves — đọc bảng SessionMoveLogs (không phải Service tự tính).
 *   Ai dùng?  MyBookingService.moveNote ghép lý do "đổi từ giờ X sang giờ Y".
 */
public class SessionMoveInfo {

    private final int sessionId;
    private String moveType;               // CustomerSwap | PtOnBehalfSwap | PtReschedule | PtTransfer (lần dời cuối)
    private final LocalDate originalDate;  // ngày của khung GỐC (OldTimeSlotId của lần dời đầu)
    private final LocalTime originalStart;

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
