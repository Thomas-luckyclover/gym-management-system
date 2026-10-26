package com.gms.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * DỮ LIỆU BƯỚC 3 "LỊCH TẬP" của màn Bán gói PT — gửi xuống trình duyệt dạng JSON (pt-sale-plan.js đọc).
 *
 *   Ai tạo?   PtSaleService.buildPlan (không đọc DB, test được) từ khung của PT, đơn nghỉ, ngày phòng nghỉ,
 *             các buổi khác của khách trong kỳ.
 *   Ý tưởng:  server tính SẴN cho từng ngày T2–T6 trong kỳ: mỗi giờ có thể tập được hay không (+ lý do).
 *             Trình duyệt chỉ ghép "lịch mẫu" + "sửa từng buổi" trên dữ liệu này → bấm là thấy ngay, không tải lại.
 *             Lúc bấm "Giữ lịch", PtSaleService.hold vẫn KIỂM TRA LẠI từng buổi có khóa (không tin trình duyệt).
 *
 *   Ví dụ 1 ngày:  {date:"2026-10-28", weekday:3, label:"Thứ 4 · 28/10",
 *                   options:[{time:"09:30", end:"11:00", ok:true}, {time:"20:00", ok:false, reason:"PT đã có khách giờ này"}]}
 */
public class PtSalePlan {

    /** 1 giờ có thể chọn trong 1 ngày. ok = false → reason nói vì sao; ok = true có thể kèm warning (PT cần dời sau). */
    public static class Option {
        private final String time;
        private final String end;
        private final boolean ok;
        private final String reason;
        private final String warning;

        public Option(String time, String end, boolean ok, String reason, String warning) {
            this.time = time;
            this.end = end;
            this.ok = ok;
            this.reason = reason;
            this.warning = warning;
        }

        public String getTime() { return time; }
        public String getEnd() { return end; }
        public boolean isOk() { return ok; }
        public String getReason() { return reason; }
        public String getWarning() { return warning; }
    }

    /** 1 ngày T2–T6 trong kỳ. weekday: 1 = Thứ 2 … 5 = Thứ 6. */
    public static class Day {
        private final String date;
        private final int weekday;
        private final String label;
        private final List<Option> options = new ArrayList<>();

        public Day(String date, int weekday, String label) {
            this.date = date;
            this.weekday = weekday;
            this.label = label;
        }

        public String getDate() { return date; }
        public int getWeekday() { return weekday; }
        public String getLabel() { return label; }
        public List<Option> getOptions() { return options; }
    }

    private String start;                               // "2026-10-27"
    private String end;
    private long price;                                 // giá gói → giá 1 buổi = price ÷ số buổi
    private List<String> times = new ArrayList<>();      // giờ của lịch mẫu (PT công bố trong 14 ngày đầu kỳ)
    private String[] pattern = new String[5];            // lịch mẫu ban đầu (t1..t5 trên URL); null = Nghỉ
    private List<String> requested = new ArrayList<>();  // "2026-10-27@09:30" — buổi đã gửi lên khi Giữ lịch bị từ chối
    private List<Day> days = new ArrayList<>();
    private List<Integer> unpublished = new ArrayList<>(); // Thứ PT chưa công bố (1–5) → buổi vào Thứ đó cần "Đã xác nhận với PT"

    public String getStart() { return start; }
    public void setStart(String start) { this.start = start; }
    public String getEnd() { return end; }
    public void setEnd(String end) { this.end = end; }
    public long getPrice() { return price; }
    public void setPrice(long price) { this.price = price; }
    public List<String> getTimes() { return times; }
    public void setTimes(List<String> times) { this.times = times; }
    public String[] getPattern() { return pattern; }
    public void setPattern(String[] pattern) { this.pattern = pattern; }
    public List<String> getRequested() { return requested; }
    public void setRequested(List<String> requested) { this.requested = requested; }
    public List<Day> getDays() { return days; }
    public void setDays(List<Day> days) { this.days = days; }
    public List<Integer> getUnpublished() { return unpublished; }
    public void setUnpublished(List<Integer> unpublished) { this.unpublished = unpublished; }
}
