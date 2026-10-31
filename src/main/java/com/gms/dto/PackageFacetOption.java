package com.gms.dto;

/**
 * 1 lựa chọn lọc của màn Mua gói. Dùng cho hai việc cùng một hình dạng:
 * (1) ô tick thời hạn trong cột lọc, VD "3 tháng (2)": field = "duration", key = "month-3", count = 2;
 * (2) chip "bộ lọc đang bật" phía trên lưới thẻ, bấm ✕ để gỡ: tạo bằng {@link #chip}, count không dùng.
 *
 * JS (package-selection.js) gỡ chip bằng cách tìm ô nhập có name = field (và value = key) trong form lọc,
 * bỏ chọn / xóa rồi gửi lại form. Với field "price" thì xóa cả hai ô giá.
 *
 * Ví dụ ô tick: field = "duration", key = "month-3", label = "3 tháng", count = 2, selected = true.
 * Ví dụ chip: field = "buy", key = "online", label = "Thanh toán trực tuyến".
 *
 */
public class PackageFacetOption {

    // Service: name của ô nhập trong form lọc: type | price | duration | buy.
    private final String field;

    // Service: mã gửi lên URL (value của ô nhập), VD "month-3". Rỗng với field "price".
    private final String key;

    // Service: chữ hiển thị, VD "3 tháng".
    private final String label;

    // Service: số gói đang bán thuộc lựa chọn này (tính trên toàn bộ gói, không theo kết quả đã lọc).
    private final int count;

    // Service: true nếu khách đang tick lựa chọn này.
    private final boolean selected;

    public PackageFacetOption(String field, String key, String label, int count, boolean selected) {
        this.field = field;
        this.key = key;
        this.label = label;
        this.count = count;
        this.selected = selected;
    }

    /** Tạo chip "bộ lọc đang bật": luôn selected, không có số lượng. */
    public static PackageFacetOption chip(String field, String key, String label) {
        return new PackageFacetOption(field, key, label, 0, true);
    }

    public String getField() {
        return field;
    }

    public String getKey() {
        return key;
    }

    public String getLabel() {
        return label;
    }

    public int getCount() {
        return count;
    }

    public boolean isSelected() {
        return selected;
    }
}
