package com.gms.service;

import com.gms.util.TimeRange;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Tính GIỜ TRỐNG và TRÙNG LỊCH cho slot PT — Java thuần, không đọc DB (unit test được).
 * Dùng chung cho: PT dời / chuyển / đổi giúp buổi (UC-35), PT đăng slot trống (UC-30), bán gói PT (UC-21).
 *
 * Người gọi (Service) đọc DB 1 lần lấy các khoảng bận rồi đưa vào đây; KHÔNG gọi DB cho từng ô giờ.
 * Buổi / slot đang được xử lý phải được loại khỏi danh sách bận TRƯỚC khi gọi (không tự trùng với chính nó).
 */
public final class SlotPlanner {

    /** Lưới giờ bắt đầu của slot: phút 00 / 15 / 30 / 45 (DBR-12, CHECK CK_TimeSlots_Grid). */
    public static final int GRID_MINUTES = 15;

    private SlotPlanner() {
    }

    /** 1 khoảng bận + lý do để hiện cho người dùng (VD "Bạn có buổi với Hoàng Nam"). */
    public static final class Busy {
        private final TimeRange range;
        private final String reason;

        public Busy(TimeRange range, String reason) {
            this.range = range;
            this.reason = reason;
        }

        public TimeRange getRange() { return range; }
        public String getReason() { return reason; }
    }

    /** Giờ bắt đầu có đúng lưới 15 phút không. */
    public static boolean onGrid(LocalTime time) {
        return time.getSecond() == 0 && time.getNano() == 0 && time.getMinute() % GRID_MINUTES == 0;
    }

    /** Các khoảng bận trùng với khoảng định chọn (rỗng = không trùng). */
    public static List<Busy> conflicts(TimeRange candidate, List<Busy> busy) {
        List<Busy> result = new ArrayList<>();
        for (Busy b : busy) {
            if (b.getRange().overlaps(candidate)) {
                result.add(b);
            }
        }
        return result;
    }

    /**
     * Các giờ bắt đầu còn trống trong 1 ngày.
     * B1: chạy từ giờ mở cửa, mỗi bước stepMinutes.
     * B2: bỏ giờ mà buổi (dài durationMinutes) vượt giờ đóng cửa.
     * B3: bỏ giờ đã qua (bắt đầu không sau now).
     * B4: bỏ giờ trùng bất kỳ khoảng bận nào.
     *
     * @param open / close giờ mở – đóng cửa của ngày đó (đã xét ngày ngoại lệ); null = nghỉ cả ngày
     */
    public static List<LocalTime> freeStarts(LocalDate date, LocalTime open, LocalTime close,
            int durationMinutes, int stepMinutes, List<Busy> busy, LocalDateTime now) {
        List<LocalTime> result = new ArrayList<>();
        if (open == null || close == null) {
            return result;
        }
        // B1: giờ đầu tiên làm tròn LÊN theo lưới (VD mở 05:20 → 05:30)
        LocalTime start = roundUpToGrid(open, stepMinutes);
        while (start != null) {
            TimeRange candidate = TimeRange.of(date, start, durationMinutes);
            // B2: vượt giờ đóng cửa (hoặc qua nửa đêm) thì dừng
            if (candidate.getEnd().isBefore(start) || !candidate.isWithin(open, close)) {
                break;
            }
            // B3 + B4
            boolean future = date.atTime(start).isAfter(now);
            if (future && conflicts(candidate, busy).isEmpty()) {
                result.add(start);
            }
            LocalTime next = start.plusMinutes(stepMinutes);
            start = next.isAfter(start) ? next : null;   // tránh vòng lặp khi cộng qua 0h
        }
        return result;
    }

    /** Làm tròn lên theo bước phút, tính từ 00:00 (VD 05:20, bước 30 → 05:30). */
    static LocalTime roundUpToGrid(LocalTime time, int stepMinutes) {
        int minutes = time.getHour() * 60 + time.getMinute() + (time.getSecond() > 0 ? 1 : 0);
        int rounded = ((minutes + stepMinutes - 1) / stepMinutes) * stepMinutes;
        return rounded >= 24 * 60 ? null : LocalTime.of(rounded / 60, rounded % 60);
    }
}
