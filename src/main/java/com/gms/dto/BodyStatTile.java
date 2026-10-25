package com.gms.dto;

/**
 * MỘT Ô SỐ ở đầu màn Chỉ số cơ thể: giá trị mới nhất + thay đổi so với lần đầu.
 * Ví dụ: label "Cân nặng", value "58,6", unit "kg", change "−3,8 kg so với 05/09", note null.
 * BMI có thêm note = nhóm BMI ("Bình thường"), tone = ok | warn | bad để tô màu nhãn.
 */
public class BodyStatTile {

    private final String metric;   // weight | bmi | fat | waist — trùng ?metric= của biểu đồ
    private final String label;
    private final String value;    // "—" khi chưa có số
    private final String unit;
    private final String change;   // null khi chỉ có 1 lần đo
    private final String note;
    private final String tone;

    public BodyStatTile(String metric, String label, String value, String unit, String change, String note, String tone) {
        this.metric = metric;
        this.label = label;
        this.value = value;
        this.unit = unit;
        this.change = change;
        this.note = note;
        this.tone = tone;
    }

    public String getMetric() { return metric; }
    public String getLabel() { return label; }
    public String getValue() { return value; }
    public String getUnit() { return unit; }
    public String getChange() { return change; }
    public String getNote() { return note; }
    public String getTone() { return tone; }
}
