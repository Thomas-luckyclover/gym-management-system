package com.gms.dto;

import com.gms.util.DateTimeUtil;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

/**
 * Dữ liệu giao diện "Đổi ngày bắt đầu" của một gói Membership đã mua.
 *
 * <p>
 * Service tính sẵn mọi thứ (ngày được chọn, có đổi được không, ngày hết hạn).
 * JSP và JavaScript chỉ hiển thị, không tự suy luận luật.</p>
 */
public class StartDatePicker {

    private final int subscriptionId;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private final LocalDate today;
    private final List<LocalDate> options;
    private final String lockReason;

    /**
     * @param options các ngày khách bấm chọn được; rỗng khi đã bị khóa
     * @param lockReason null khi còn đổi được, ngược lại là lý do bị khóa
     */
    public StartDatePicker(int subscriptionId, LocalDate startDate, LocalDate endDate, LocalDate today,
            List<LocalDate> options, String lockReason) {
        this.subscriptionId = subscriptionId;
        this.startDate = startDate;
        this.endDate = endDate;
        this.today = today;
        this.options = options == null ? new ArrayList<>() : options;
        this.lockReason = lockReason;
    }

    public int getSubscriptionId() {
        return subscriptionId;
    }

    public String getStartText() {
        return DateTimeUtil.formatDate(startDate);
    }

    public String getEndText() {
        return DateTimeUtil.formatDate(endDate);
    }

    public String getStartIso() {
        return startDate.toString();
    }

    /** Còn đổi được: chưa khóa và có ít nhất một ngày khác để chọn. */
    public boolean isEditable() {
        return lockReason == null && options.size() > 1;
    }

    public String getLockReason() {
        return lockReason;
    }

    /** Hai hàng lịch (T2..CN) bắt đầu từ thứ Hai của tuần chứa hôm nay, đủ phủ hôm nay + 7 ngày. */
    public List<CalendarCell> getCalendarCells() {
        List<CalendarCell> cells = new ArrayList<>();
        int months = com.gms.util.PackagePeriod.monthsBetween(startDate, endDate);
        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        for (int i = 0; i < 14; i++) {
            LocalDate day = monday.plusDays(i);
            boolean enabled = options.contains(day);
            String endText = enabled && months > 0
                    ? DateTimeUtil.formatDate(com.gms.util.PackagePeriod.endDate(day, months)) : "";
            cells.add(new CalendarCell(day, enabled, day.equals(startDate), day.equals(today), endText));
        }
        return cells;
    }

    /**
     * Tiêu đề lịch: một tháng thì "Tháng 10/2026", hai tháng "Tháng 10 – 11/2026",
     * qua năm "Tháng 12/2026 – 1/2027". Tính theo khoảng hôm nay đến hôm nay + 7 ngày.
     */
    public String getCalendarTitle() {
        LocalDate first = today;
        LocalDate last = today.plusDays(com.gms.util.PackagePeriod.MAX_DAYS_AHEAD);
        if (first.getMonth() == last.getMonth() && first.getYear() == last.getYear()) {
            return "Tháng " + last.getMonthValue() + "/" + last.getYear();
        }
        if (first.getYear() == last.getYear()) {
            return "Tháng " + first.getMonthValue() + " – " + last.getMonthValue() + "/" + last.getYear();
        }
        return "Tháng " + first.getMonthValue() + "/" + first.getYear()
                + " – " + last.getMonthValue() + "/" + last.getYear();
    }

    /** Một ô trên lịch. */
    public static class CalendarCell {

        private final LocalDate date;
        private final boolean enabled;
        private final boolean selected;
        private final boolean today;
        private final String endText;

        CalendarCell(LocalDate date, boolean enabled, boolean selected, boolean today, String endText) {
            this.endText = endText;
            this.date = date;
            this.enabled = enabled;
            this.selected = selected;
            this.today = today;
        }

        /** Số ngày; ngày mùng 1 ghi dạng "1/11" để khách biết đã sang tháng mới. */
        public String getLabel() {
            return date.getDayOfMonth() == 1
                    ? "1/" + date.getMonthValue()
                    : String.valueOf(date.getDayOfMonth());
        }

        public String getIso() {
            return date.toString();
        }

        /** Ngày bắt đầu dạng dd/MM/yyyy để xem trước trên ô ngày. */
        public String getStartText() {
            return DateTimeUtil.formatDate(date);
        }

        /** Ngày hết hạn nếu chọn ngày này (server tính sẵn); rỗng với ô không chọn được. */
        public String getEndText() {
            return endText;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public boolean isSelected() {
            return selected;
        }

        public boolean isToday() {
            return today;
        }
    }
}
