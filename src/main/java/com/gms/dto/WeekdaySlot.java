package com.gms.dto;

import java.time.LocalTime;

/**
 * 1 ô của "Lịch cố định" (màn Gói của tôi): giờ tập cố định của 1 thứ trong tuần (T2 … T6).
 * MyBookingService.buildWeekTemplate tính từ các buổi thật của gói.
 */
public class WeekdaySlot {

    public static final String KIND_MAIN = "main";     // thứ này tập đúng giờ chính của gói
    public static final String KIND_ODD = "odd";       // thứ này cố định nhưng KHÁC giờ chính (VD T4 07:00 khi các thứ khác 18:00)
    public static final String KIND_MIXED = "mixed";   // có buổi nhưng giờ không cố định → các buổi nằm ở "Buổi khác giờ"
    public static final String KIND_NONE = "none";     // thứ này không có buổi

    private final String dayName;      // "T2" … "T6"
    private final String kind;         // main | odd | mixed | none
    private final LocalTime startTime; // giờ cố định (null nếu mixed / none)
    private final LocalTime endTime;
    private final int count;           // số buổi của gói rơi đúng giờ cố định ở thứ này

    public WeekdaySlot(String dayName, String kind, LocalTime startTime, LocalTime endTime, int count) {
        this.dayName = dayName;
        this.kind = kind;
        this.startTime = startTime;
        this.endTime = endTime;
        this.count = count;
    }

    public String getDayName() { return dayName; }
    public String getKind() { return kind; }
    public LocalTime getStartTime() { return startTime; }
    public LocalTime getEndTime() { return endTime; }
    public int getCount() { return count; }

    /** Thứ có giờ cố định (main hoặc odd)? */
    public boolean isFixed() {
        return KIND_MAIN.equals(kind) || KIND_ODD.equals(kind);
    }
}
