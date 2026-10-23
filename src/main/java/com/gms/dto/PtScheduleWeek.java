package com.gms.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * MỘT TUẦN trên Lịch dạy PT:  tuần → 7 ngày (days) → các thẻ trong ngày (items).
 *
 *   Ai tạo?   PtScheduleService.getWeek.
 *   Ai đọc?   PtScheduleServlet đặt vào request với tên "week"; schedule.jsp đọc ${week.days}.
 *
 * Ví dụ xem ngày 27/10/2026:
 *   weekStart=26/10 (Thứ 2), weekEnd=01/11 (Chủ nhật), axisStartHour=5, axisEndHour=22.
 */
public class PtScheduleWeek {

    // ===== Mốc tuần (dùng cho thanh ‹ tuần trước / tuần sau ›) =====
    private LocalDate weekStart;     // Thứ 2 của tuần đang xem
    private LocalDate weekEnd;       // Chủ nhật của tuần đang xem
    private LocalDate prevWeek;      // Thứ 2 tuần trước → link ‹
    private LocalDate nextWeek;      // Thứ 2 tuần sau  → link ›
    private boolean currentWeek;     // tuần đang xem có chứa hôm nay không → ẩn nút "Tuần này"

    // ===== Trục giờ của lưới (chung cho cả 7 cột) =====
    private int axisStartHour;       // giờ đầu trục, ví dụ 5 = 05:00
    private int axisEndHour;         // giờ cuối trục, ví dụ 22 = 22:00
    private int sessionMinutes;      // P-06: mỗi khung dài bao nhiêu phút, ví dụ 90

    // ===== 7 ngày, Thứ 2 → Chủ nhật =====
    private List<PtScheduleDay> days = List.of();


    // ===== Getter / Setter: chỉ đọc và gán, không có nghiệp vụ =====

    public LocalDate getWeekStart() { return weekStart; }
    public void setWeekStart(LocalDate weekStart) { this.weekStart = weekStart; }

    public LocalDate getWeekEnd() { return weekEnd; }
    public void setWeekEnd(LocalDate weekEnd) { this.weekEnd = weekEnd; }

    public LocalDate getPrevWeek() { return prevWeek; }
    public void setPrevWeek(LocalDate prevWeek) { this.prevWeek = prevWeek; }

    public LocalDate getNextWeek() { return nextWeek; }
    public void setNextWeek(LocalDate nextWeek) { this.nextWeek = nextWeek; }

    public boolean isCurrentWeek() { return currentWeek; }
    public void setCurrentWeek(boolean currentWeek) { this.currentWeek = currentWeek; }

    public int getAxisStartHour() { return axisStartHour; }
    public void setAxisStartHour(int axisStartHour) { this.axisStartHour = axisStartHour; }

    public int getAxisEndHour() { return axisEndHour; }
    public void setAxisEndHour(int axisEndHour) { this.axisEndHour = axisEndHour; }

    public int getSessionMinutes() { return sessionMinutes; }
    public void setSessionMinutes(int sessionMinutes) { this.sessionMinutes = sessionMinutes; }

    public List<PtScheduleDay> getDays() { return days; }
    public void setDays(List<PtScheduleDay> days) { this.days = days; }
}
