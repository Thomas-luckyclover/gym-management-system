package com.gms.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * DỮ LIỆU KHỐI "LỊCH TẬP" GỬI CHO JS — Service tạo: PtSaleService.loadPlan → buildPlan, rồi JsonUtil.toJson
 * → nhúng vào <script id="sale-plan-data"> (_pt-sale-plan.jspf) → pt-sale-plan.js đọc (biến data) và vẽ lịch mẫu + từng tuần.
 * Option = 1 giờ của 1 ngày (time, end, ok, reason, warning, fresh = PT chưa mở khung) · Day = 1 ngày T2–T6 trong kỳ.
 */
public class PtSalePlan {
    public static class Option {
        private final String time;
        private final String end;
        private final boolean ok;
        private final String reason;
        private final String warning;
        private final boolean fresh;

        public Option(String time, String end, boolean ok, String reason, String warning, boolean fresh) {
            this.time = time;
            this.end = end;
            this.ok = ok;
            this.reason = reason;
            this.warning = warning;
            this.fresh = fresh;
        }

        public String getTime() { return time; }
        public String getEnd() { return end; }
        public boolean isOk() { return ok; }
        public String getReason() { return reason; }
        public String getWarning() { return warning; }
        public boolean isFresh() { return fresh; }
    }

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

    // ===== JS ĐỌC ĐỂ VẼ =====
    // ← mỗi ngày T2–T6 trong kỳ × mỗi giờ chọn được → checkDay (ok / lý do) → data.days: ô ngày ở "Từng tuần", ô sửa riêng
    private List<Day> days = new ArrayList<>();
    // ← giờ PT đã công bố (14 ngày đầu) → các nút giờ của mỗi dòng "Lịch mẫu"
    private List<String> times = new ArrayList<>();
    // ← giờ lịch mẫu lễ tân tự thêm cho RIÊNG từng thứ (đã kiểm giờ mở cửa) → thêm nút giờ vào đúng dòng thứ đó
    private String[] custom = new String[5];
    // ← t1..t5 trên URL (giờ hợp lệ) → nút giờ được chọn sẵn của mỗi thứ
    private String[] pattern = new String[5];
    // ← sessions trên URL → khôi phục các buổi "sửa riêng" (F5, chọn khách, Giữ lịch bị từ chối)
    private List<String> requested = new ArrayList<>();
    // ← thứ PT chưa mở khung nào → nhãn "PT chưa công bố" trên dòng thứ đó
    private List<Integer> unpublished = new ArrayList<>();
    // ← giờ PT đã công bố → giờ KHÔNG nằm trong đây ghi "PT chưa mở"
    private List<String> published = new ArrayList<>();
    // ← giờ mở cửa P-17 → min của ô "+ Thêm giờ"
    private String open;
    // ← giờ bắt đầu muộn nhất (đóng cửa − thời lượng buổi) → max của ô "+ Thêm giờ"
    private String close;
    // ← P-06 thời lượng 1 buổi → giờ kết thúc
    private int minutes;
    // ← giờ tự thêm sai (ngoài giờ mở cửa / lệch mốc 15 phút) → khung đỏ đầu "Lịch tập"
    private String notice;
    // ← giá gói → "N buổi · x đ/buổi"
    private long price;
    // ← ngày bắt đầu kỳ
    private String start;
    // ← ngày hết kỳ
    private String end;

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
    public List<String> getPublished() { return published; }
    public void setPublished(List<String> published) { this.published = published; }
    public String getOpen() { return open; }
    public void setOpen(String open) { this.open = open; }
    public String getClose() { return close; }
    public void setClose(String close) { this.close = close; }
    public String[] getCustom() { return custom; }
    public void setCustom(String[] custom) { this.custom = custom; }
    public String getNotice() { return notice; }
    public void setNotice(String notice) { this.notice = notice; }
    public int getMinutes() { return minutes; }
    public void setMinutes(int minutes) { this.minutes = minutes; }
}
