package com.gms.dto;

/**
 * MỘT LỰA CHỌN trong ô chọn của màn Bán gói PT: 1 gói hoặc 1 PT.
 *
 *   Ai tạo?   PtSaleService (bước 2 của prepare: loadPackage / loadPt) — PtSaleDAO đọc Packages / Employees(+Persons).
 *   Ai in?    pt-sales.jsp hai ô chọn (Gói, PT).
 *
 *   value  giá trị gửi lên server (mã gói "9", mã PT "5")
 *   label  chữ chính ("Gói PT 2 Tháng", "Đinh Công Sơn")
 *   note   chữ phụ ("16.500.000đ · 2 tháng · 1 người", chữ cái đầu tên PT "ĐS")
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
