package com.gms.dto;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * [TKB] Lịch 1 THÁNG của khách: lưới 7 cột (Thứ 2 → Chủ nhật), mỗi hàng 1 tuần.
 * Ô của ngày ngoài tháng vẫn hiện (mờ) cho đủ tuần, nhưng KHÔNG tính vào số đếm.
 *
 * JSP đọc: ${month.weeks} → week (List<Cell>) → cell.sessions.
 */
public class MonthView {

    private final YearMonth month;
    private final boolean currentMonth;
    private final List<List<Cell>> weeks = new ArrayList<>();

    // Số đếm trong tháng (chú thích dưới lưới)
    private int upcomingCount;    // Sắp tới (gồm Hôm nay + Cần chú ý)
    private int checkedInCount;   // Đã check-in
    private int missedCount;      // Vắng
    private int cancelledCount;   // Đã hủy
    private int pendingCount;     // Đang cập nhật

    public MonthView(YearMonth month, LocalDate today) {
        this.month = month;
        this.currentMonth = YearMonth.from(today).equals(month);
    }

    public YearMonth getMonth() { return month; }
    public LocalDate getFirstDay() { return month.atDay(1); }
    public LocalDate getPrevMonth() { return month.minusMonths(1).atDay(1); }
    public LocalDate getNextMonth() { return month.plusMonths(1).atDay(1); }
    public boolean isCurrentMonth() { return currentMonth; }
    public List<List<Cell>> getWeeks() { return weeks; }

    public int getUpcomingCount() { return upcomingCount; }
    public void setUpcomingCount(int upcomingCount) { this.upcomingCount = upcomingCount; }
    public int getCheckedInCount() { return checkedInCount; }
    public void setCheckedInCount(int checkedInCount) { this.checkedInCount = checkedInCount; }
    public int getMissedCount() { return missedCount; }
    public void setMissedCount(int missedCount) { this.missedCount = missedCount; }
    public int getCancelledCount() { return cancelledCount; }
    public void setCancelledCount(int cancelledCount) { this.cancelledCount = cancelledCount; }
    public int getPendingCount() { return pendingCount; }
    public void setPendingCount(int pendingCount) { this.pendingCount = pendingCount; }

    /** 1 ô ngày trong lưới tháng. */
    public static class Cell {
        private final LocalDate date;
        private final boolean inMonth;
        private final boolean today;
        private final List<MyPtSessionRow> sessions = new ArrayList<>();

        public Cell(LocalDate date, boolean inMonth, boolean today) {
            this.date = date;
            this.inMonth = inMonth;
            this.today = today;
        }

        public LocalDate getDate() { return date; }
        public boolean isInMonth() { return inMonth; }
        public boolean isToday() { return today; }
        public List<MyPtSessionRow> getSessions() { return sessions; }
    }
}
