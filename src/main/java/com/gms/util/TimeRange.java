package com.gms.util;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;

/**
 * Một khoảng giờ trong 1 ngày, dạng NỬA MỞ [start, end): tính phút đầu, không tính phút cuối.
 * Dùng chung cho mọi kiểm tra trùng lịch (slot PT, buổi PT, giờ mở cửa).
 *
 *   Trùng  ⇔  cùng ngày  VÀ  a.start < b.end  VÀ  b.start < a.end
 *   → 18:00–19:30 và 19:30–21:00 KHÔNG trùng (dạy nối tiếp được — DBR-33)
 *   → 18:00–19:30 và 18:15–19:45 TRÙNG (chồng một phần)
 *
 * Câu SQL kiểm tra trùng dùng đúng điều kiện này: t.StartTime < @end AND t.EndTime > @start.
 */
public final class TimeRange {

    private final LocalDate date;
    private final LocalTime start;
    private final LocalTime end;

    /** Tạo khoảng [start, end) của ngày date; end phải sau start (gym đóng trước 0h nên không vắt qua ngày). */
    public TimeRange(LocalDate date, LocalTime start, LocalTime end) {
        if (date == null || start == null || end == null || !end.isAfter(start)) {
            throw new IllegalArgumentException("Khoảng giờ không hợp lệ: " + date + " " + start + "–" + end);
        }
        this.date = date;
        this.start = start;
        this.end = end;
    }

    /** Khoảng bắt đầu lúc start, dài minutes phút. */
    public static TimeRange of(LocalDate date, LocalTime start, int minutes) {
        return new TimeRange(date, start, start.plusMinutes(minutes));
    }

    /** Hai khoảng có chồng lên nhau không (công thức nửa mở ở đầu lớp). */
    public boolean overlaps(TimeRange other) {
        return date.equals(other.date) && start.isBefore(other.end) && other.start.isBefore(end);
    }

    /** Khoảng này có nằm trọn trong [open, close] của cùng ngày không (VD giờ mở cửa). */
    public boolean isWithin(LocalTime open, LocalTime close) {
        return !start.isBefore(open) && !end.isAfter(close);
    }

    /** Số phút của khoảng. */
    public int minutes() {
        return (int) java.time.Duration.between(start, end).toMinutes();
    }

    public LocalDate getDate() { return date; }
    public LocalTime getStart() { return start; }
    public LocalTime getEnd() { return end; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TimeRange other)) return false;
        return date.equals(other.date) && start.equals(other.start) && end.equals(other.end);
    }

    @Override
    public int hashCode() {
        return Objects.hash(date, start, end);
    }

    @Override
    public String toString() {
        return date + " " + start + "–" + end;
    }
}
