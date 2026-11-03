package com.gms.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * DTO 1 NGÀY trong "Lịch dạy của bạn" (7 ngày) — Service TẠO: PtDashboardService.getSchedule.
 * Servlet: PtDashboardServlet.loadBlocks → "scheduleDays" (List 7 ngày) → pt/dashboard.jsp (biến day / statDay).
 */
public class PtScheduleDayView {
    // ===== IN RA MÀN =====
    // ← hôm nay + i (i = 0…6) → "dd/MM" trên tab ngày
    private final LocalDate slotDate;
    // ← buildDayLabel: "Hôm nay" | "Thứ Hai"… → tên tab ngày
    private final String dayLabel;
    // ← date == hôm nay → tab mở sẵn, ô "Buổi hôm nay" đếm từ ngày này
    private final boolean isToday;
    // ← các PtSessionView cùng ngày (PtDashboardDAO.findSessions) → "N buổi" / "Trống" + danh sách buổi của tab
    private final List<PtSessionView> sessions;

    public PtScheduleDayView(LocalDate slotDate, String dayLabel, boolean isToday,
            List<PtSessionView> sessions) {
        this.slotDate = slotDate;
        this.dayLabel = dayLabel;
        this.isToday = isToday;

        this.sessions = List.copyOf(sessions);
    }

    public LocalDate getSlotDate() {
        return slotDate;
    }

    public String getDayLabel() {
        return dayLabel;
    }

    public boolean isToday() {
        return isToday;
    }

    public List<PtSessionView> getSessions() {
        return sessions;
    }
}
