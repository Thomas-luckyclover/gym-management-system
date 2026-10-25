package com.gms.dto;

/**
 * MỘT LỰA CHỌN trong ô chọn của màn Bán gói PT: 1 gói, 1 PT, hoặc 1 giờ cố định.
 *
 *   value  giá trị gửi lên server (mã gói "9", mã PT "5", giờ "18:00")
 *   label  chữ chính ("Gói PT 2 Tháng", "Đinh Công Sơn", "18:00 – 19:30")
 *   note   chữ phụ ("16.500.000đ · 2 tháng", "Hoa hồng 70%", "PT trống 8/10 ngày đầu")
 */
public class PtSaleOption {

    private final String value;
    private final String label;
    private final String note;

    public PtSaleOption(String value, String label, String note) {
        this.value = value;
        this.label = label;
        this.note = note;
    }

    public String getValue() { return value; }

    public String getLabel() { return label; }

    public String getNote() { return note; }
}
