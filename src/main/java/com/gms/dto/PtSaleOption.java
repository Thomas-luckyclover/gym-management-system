package com.gms.dto;

/**
 * 1 LỰA CHỌN (1 ô gói hoặc 1 nút PT) ở khối "Gói & PT" — Service tạo: loadPackage / loadPt. JSP: _pt-sale-step1.jspf (biến o).
 */
public class PtSaleOption {
    // ===== IN RA MÀN =====
    // ← PackageId / EmployeeId → value của radio (gửi lên ?package= / ?pt=)
    private final String value;
    // ← tên gói / tên PT → chữ đậm
    private final String label;
    // ← gói: "8.800.000đ · 1 tháng · 1 người" · PT: chữ cái viết tắt avatar
    private final String note;
    // ← chỉ PT: "Boxing – Cardio · 4 năm kinh nghiệm" (ptDetail) → chữ nhỏ dưới tên · gói: null
    private final String detail;

    public PtSaleOption(String value, String label, String note) {
        this(value, label, note, null);
    }

    public PtSaleOption(
            String value,
            String label,
            String note,
            String detail) {
        this.value = value;
        this.label = label;
        this.note = note;
        this.detail = detail;
    }

    public String getValue() { return value; }

    public String getLabel() { return label; }

    public String getNote() { return note; }

    public String getDetail() { return detail; }
}
