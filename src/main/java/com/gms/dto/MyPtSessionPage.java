package com.gms.dto;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * MỘT TRANG của khối Sắp tới hoặc Lịch sử. Mỗi trang tối đa 8 NGÀY có buổi.
 *
 * Cấu trúc (JSP lặp 2 vòng):
 *   page.days                 → danh sách ngày của trang
 *     day.date                → ngày tập (vd 17/10/2026)
 *     day.sessions            → các buổi trong ngày đó
 *   page.currentPage / totalPages / totalDays → để in "Trang 1 / 3 · 20 ngày có buổi"
 */
public class MyPtSessionPage {

    /** Một ngày trong trang: ngày + các buổi của ngày đó. */
    public static class Day {
        private final LocalDate date;
        private final List<MyPtSessionRow> sessions = new ArrayList<>();

        public Day(LocalDate date) { this.date = date; }
        public LocalDate getDate() { return date; }
        public List<MyPtSessionRow> getSessions() { return sessions; }
    }

    private final List<Day> days;     // các ngày của trang này (đúng thứ tự hiển thị)
    private final int currentPage;    // trang đang xem, bắt đầu từ 1
    private final int totalPages;     // tổng số trang, tối thiểu 1
    private final int totalDays;      // tổng số ngày có buổi (mọi trang)

    public MyPtSessionPage(List<Day> days, int currentPage, int totalPages, int totalDays) {
        this.days = days;
        this.currentPage = currentPage;
        this.totalPages = totalPages;
        this.totalDays = totalDays;
    }

    public List<Day> getDays() { return days; }
    public int getCurrentPage() { return currentPage; }
    public int getTotalPages() { return totalPages; }
    public int getTotalDays() { return totalDays; }
}
