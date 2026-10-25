package com.gms.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * MỘT LẦN ĐO chỉ số cơ thể (bảng BodyMeasurements) + vài số Service tính thêm.
 *
 *   Ai tạo?   BodyStatsDAO.findByCustomer (đọc DB).
 *   Ai điền?  BodyStatsService: bmi (= cân ÷ chiều cao m²), weightChange (so với lần đo trước).
 *   Ai in?    body-stats.jsp bảng "Lịch sử đo".
 */
public class BodyStatRow {

    private int id;                    // BodyMeasurements.BodyMeasurementId — nút Xóa gửi lên
    private LocalDate measuredOn;
    private BigDecimal weightKg;       // 58.6
    private BigDecimal heightCm;       // 160.0
    private BigDecimal bodyFatPercent; // null = lần này không đo
    private BigDecimal musclePercent;  // tỷ lệ cơ; null = lần này không đo
    private BigDecimal waistCm;        // null = lần này không đo
    private String note;

    // ===== Service tính =====
    private BigDecimal bmi;            // 22.9
    private BigDecimal weightChange;   // -0.7 so với lần trước; null = lần đầu

    // ===== Chữ in sẵn cho bảng (dấu phẩy thập phân kiểu Việt Nam) =====

    /** "58,6 kg". */
    public String getWeightText() { return text(weightKg) + " kg"; }

    /** "22,9". */
    public String getBmiText() { return text(bmi); }

    /** "27,9 %" hoặc "—" khi lần này không đo. */
    public String getFatText() { return bodyFatPercent == null ? "—" : text(bodyFatPercent) + " %"; }

    /** "38,3 %" hoặc "—". */
    public String getMuscleText() { return musclePercent == null ? "—" : text(musclePercent) + " %"; }

    /** "73 cm" hoặc "—". */
    public String getWaistText() { return waistCm == null ? "—" : text(waistCm) + " cm"; }

    /** "−0,7" / "+0,4" / "±0" / "Lần đầu". */
    public String getChangeText() {
        if (weightChange == null) {
            return "Lần đầu";
        }
        int sign = weightChange.signum();
        return sign == 0 ? "±0" : (sign > 0 ? "+" : "−") + text(weightChange.abs());
    }

    /** "down" (giảm) | "up" (tăng) | "" — chỉ để tô màu nhẹ, không phán tốt / xấu. */
    public String getChangeTone() {
        if (weightChange == null || weightChange.signum() == 0) {
            return "";
        }
        return weightChange.signum() < 0 ? "down" : "up";
    }

    private static String text(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString().replace('.', ',');
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public LocalDate getMeasuredOn() { return measuredOn; }
    public void setMeasuredOn(LocalDate measuredOn) { this.measuredOn = measuredOn; }

    public BigDecimal getWeightKg() { return weightKg; }
    public void setWeightKg(BigDecimal weightKg) { this.weightKg = weightKg; }

    public BigDecimal getHeightCm() { return heightCm; }
    public void setHeightCm(BigDecimal heightCm) { this.heightCm = heightCm; }

    public BigDecimal getBodyFatPercent() { return bodyFatPercent; }
    public void setBodyFatPercent(BigDecimal bodyFatPercent) { this.bodyFatPercent = bodyFatPercent; }

    public BigDecimal getMusclePercent() { return musclePercent; }
    public void setMusclePercent(BigDecimal musclePercent) { this.musclePercent = musclePercent; }

    public BigDecimal getWaistCm() { return waistCm; }
    public void setWaistCm(BigDecimal waistCm) { this.waistCm = waistCm; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public BigDecimal getBmi() { return bmi; }
    public void setBmi(BigDecimal bmi) { this.bmi = bmi; }

    public BigDecimal getWeightChange() { return weightChange; }
    public void setWeightChange(BigDecimal weightChange) { this.weightChange = weightChange; }
}
