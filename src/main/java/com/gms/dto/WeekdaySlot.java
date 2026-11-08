package com.gms.dto;

import java.time.LocalTime;

/**
 * DTO 1 ô THỨ (T2…T6) trong "Lịch cố định" — Service TẠO: MyBookingService.applyWeekTemplate.
 * JSP đọc: my-bookings.jsp (vòng lặp ${booking.weekTemplate}, biến "d"). isFixed() = có giờ cố định (main / odd).
 */
public class WeekdaySlot {
    public static final String KIND_MAIN = "main";
    public static final String KIND_ODD = "odd";
    public static final String KIND_MIXED = "mixed";
    public static final String KIND_NONE = "none";

    // ===== IN RA MÀN =====
    // ← "T2"…"T6" → chữ trên ô
    private final String dayName;
    // ← main (giờ cố định chính) | odd (giờ cố định khác giờ chính) | mixed (có buổi nhưng không cố định) | none → class màu ô is-<kind>
    private final String kind;
    // ← giờ xuất hiện nhiều nhất của thứ đó → số giờ in đậm
    private final LocalTime startTime;
    // ← số buổi đúng giờ đó → "N buổi"
    private final int count;

    // ===== KHÔNG IN RA MÀN =====
    // ← giờ kết thúc tương ứng (để dành, JSP chưa dùng)
    private final LocalTime endTime;

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

    // GETTER TỰ TÍNH — không có field/setter "fixed"; = kind là KIND_MAIN hoặc KIND_ODD; JSP: customer/my-bookings.jsp:324
    public boolean isFixed() {
        return KIND_MAIN.equals(kind) || KIND_ODD.equals(kind);
    }
}
