package com.gms.dto;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * [TKB] Thời khóa biểu 1 TUẦN của khách (màn "Lịch tập của tôi").
 *
 *   Hàng = 5 khung giờ cố định (Sáng sớm, Sáng, Trưa, Chiều, Tối)
 *   Cột  = 7 ngày, Thứ 2 → Chủ nhật
 *   Ô    = các buổi bắt đầu trong khung đó của ngày đó (thường 0 hoặc 1 buổi)
 *
 * JSP đọc: ${timetable.days} cho tiêu đề cột, ${timetable.bands} → band.cells[i] → danh sách buổi.
 */
public class TimetableView {

    private final LocalDate weekStart;   // Thứ 2
    private final LocalDate weekEnd;     // Chủ nhật
    private final boolean currentWeek;   // tuần đang xem có chứa hôm nay không (để hiện nút "Tuần này")
    private final List<Day> days = new ArrayList<>();
    private final List<Band> bands = new ArrayList<>();

    public TimetableView(LocalDate weekStart, LocalDate today) {
        this.weekStart = weekStart;
        this.weekEnd = weekStart.plusDays(6);
        this.currentWeek = !today.isBefore(weekStart) && !today.isAfter(weekEnd);
    }

    public LocalDate getWeekStart() { return weekStart; }
    public LocalDate getWeekEnd() { return weekEnd; }
    public LocalDate getPrevWeek() { return weekStart.minusWeeks(1); }
    public LocalDate getNextWeek() { return weekStart.plusWeeks(1); }
    public boolean isCurrentWeek() { return currentWeek; }
    public List<Day> getDays() { return days; }
    public List<Band> getBands() { return bands; }

    /** 1 cột ngày: "Thứ 2" + 19/10. */
    public static class Day {
        private final LocalDate date;
        private final String name;     // "Thứ 2" … "Chủ nhật"
        private final boolean today;

        public Day(LocalDate date, String name, boolean today) {
            this.date = date;
            this.name = name;
            this.today = today;
        }

        public LocalDate getDate() { return date; }
        public String getName() { return name; }
        public boolean isToday() { return today; }
    }

    /** 1 hàng khung giờ: tên + khoảng giờ + 7 ô (mỗi ô là danh sách buổi). */
    public static class Band {
        private final String name;     // "Sáng sớm"
        private final String range;    // "06:00 – 09:00"
        private final List<List<MyPtSessionRow>> cells = new ArrayList<>();

        public Band(String name, String range) {
            this.name = name;
            this.range = range;
            for (int i = 0; i < 7; i++) {
                cells.add(new ArrayList<>());
            }
        }

        public String getName() { return name; }
        public String getRange() { return range; }
        public List<List<MyPtSessionRow>> getCells() { return cells; }
    }
}
