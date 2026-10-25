package com.gms.service;

import com.gms.constant.Messages;
import com.gms.dao.BodyStatsDAO;
import com.gms.dto.BodyStatRow;
import com.gms.dto.BodyStatTile;
import com.gms.dto.BodyStatsView;
import com.gms.dto.BodyStatsView.ChartPoint;
import com.gms.dto.BodyStatsView.ChartTick;
import com.gms.dto.ServiceResult;
import com.gms.util.DateTimeUtil;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * SERVICE MÀN CHỈ SỐ CƠ THỂ (/customer/body-stats). Chỉ khách xem dữ liệu của chính mình.
 *
 * Servlet gọi 3 hàm chính, mỗi lần 1 hàm:
 *   getPage  mở trang: đọc mọi lần đo → 5 ô số, biểu đồ 1 chỉ số, lịch sử
 *   save     ghi 1 lần đo (cùng ngày thì ghi đè)
 *   delete   xóa 1 lần đo
 *
 * MỤC LỤC
 *   PHẦN 1. HÀM CHÍNH       getPage, save, delete
 *   PHẦN 2. TÍNH (static)   bmi, bmiGroup, buildTiles, buildChart, parseNumber, checkInput
 */
public class BodyStatsService {

    // Giới hạn nhập, trùng CHECK trong bảng BodyMeasurements.
    static final BigDecimal WEIGHT_MIN = new BigDecimal("20");
    static final BigDecimal WEIGHT_MAX = new BigDecimal("300");
    static final BigDecimal HEIGHT_MIN = new BigDecimal("100");
    static final BigDecimal HEIGHT_MAX = new BigDecimal("250");
    static final BigDecimal FAT_MIN = new BigDecimal("3");
    static final BigDecimal FAT_MAX = new BigDecimal("70");
    static final BigDecimal MUSCLE_MIN = new BigDecimal("10");
    static final BigDecimal MUSCLE_MAX = new BigDecimal("70");
    static final BigDecimal WAIST_MIN = new BigDecimal("40");
    static final BigDecimal WAIST_MAX = new BigDecimal("200");

    // Khung biểu đồ (viewBox 0 0 640 240): chừa lề trái cho số trục, lề dưới cho ngày.
    static final double CHART_W = 640, CHART_H = 240, PAD_LEFT = 44, PAD_RIGHT = 16, PAD_TOP = 16, PAD_BOTTOM = 28;

    private final BodyStatsDAO statsDAO = new BodyStatsDAO();


    // ================================================================================================================
    // PHẦN 1. HÀM CHÍNH
    // ================================================================================================================

    /**
     * HÀM CHÍNH — BodyStatsServlet.doGet gọi.
     * metric = chỉ số vẽ biểu đồ (?metric=), sai hoặc thiếu → "weight".
     */
    public ServiceResult<BodyStatsView> getPage(int accountId, String metric, LocalDate today) {
        try {
            Integer customerId = statsDAO.findCustomerId(accountId);
            if (customerId == null) {
                return ServiceResult.fail(Messages.MSG07_NO_PERMISSION);
            }
            List<BodyStatRow> rows = statsDAO.findByCustomer(customerId);   // cũ trước

            // B1: BMI + chênh cân nặng so với lần trước cho từng dòng.
            BigDecimal previous = null;
            for (BodyStatRow row : rows) {
                row.setBmi(bmi(row.getWeightKg(), row.getHeightCm()));
                row.setWeightChange(previous == null ? null : row.getWeightKg().subtract(previous));
                previous = row.getWeightKg();
            }

            BodyStatsView view = new BodyStatsView();
            view.setToday(today);

            // B2: 5 ô số + biểu đồ (cần danh sách cũ trước).
            view.setTiles(buildTiles(rows));
            buildChart(view, rows, normalizeMetric(metric));

            // B3: chiều cao lần gần nhất để điền sẵn; bảng lịch sử in mới nhất trước.
            if (!rows.isEmpty()) {
                view.setLastHeight(rows.get(rows.size() - 1).getHeightCm());
            }
            List<BodyStatRow> newestFirst = new ArrayList<>(rows);
            Collections.reverse(newestFirst);
            view.setRows(newestFirst);
            return ServiceResult.ok(view);

        } catch (SQLException exception) {
            exception.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /**
     * HÀM CHÍNH — BodyStatsServlet.doPost (action=save) gọi. Chữ từ form, Service tự kiểm tra hết.
     * Số được nhập "58,6" hoặc "58.6".
     */
    public ServiceResult<Void> save(int accountId, String dateText, String weightText, String heightText,
            String fatText, String muscleText, String waistText, String noteText, LocalDate today) {
        // B1: đọc + kiểm tra chữ trên form.
        LocalDate day;
        try {
            day = LocalDate.parse(dateText == null ? "" : dateText.trim());
        } catch (DateTimeException exception) {
            return ServiceResult.fail("Chọn ngày đo.");
        }
        BigDecimal weight = parseNumber(weightText);
        BigDecimal height = parseNumber(heightText);
        BigDecimal fat = parseNumber(fatText);
        BigDecimal muscle = parseNumber(muscleText);
        BigDecimal waist = parseNumber(waistText);
        String note = noteText == null || noteText.isBlank() ? null : noteText.trim();
        String problem = checkInput(day, today, weight, height, fat, muscle, waist, note);
        if (problem != null) {
            return ServiceResult.fail(problem);
        }

        // B2: ghi.
        try {
            Integer customerId = statsDAO.findCustomerId(accountId);
            if (customerId == null) {
                return ServiceResult.fail(Messages.MSG07_NO_PERMISSION);
            }
            statsDAO.save(customerId, day, weight, height, fat, muscle, waist, note);
            return ServiceResult.ok(null, "Đã lưu lần đo ngày " + DateTimeUtil.formatDate(day)
                    + " · BMI " + format(bmi(weight, height)) + ".");
        } catch (SQLException exception) {
            exception.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /** HÀM CHÍNH — BodyStatsServlet.doPost (action=delete) gọi. Chỉ xóa được lần đo của chính mình. */
    public ServiceResult<Void> delete(int accountId, int measurementId) {
        try {
            Integer customerId = statsDAO.findCustomerId(accountId);
            if (customerId == null) {
                return ServiceResult.fail(Messages.MSG07_NO_PERMISSION);
            }
            if (!statsDAO.delete(customerId, measurementId)) {
                return ServiceResult.fail("Không tìm thấy lần đo này.");
            }
            return ServiceResult.ok(null, "Đã xóa lần đo.");
        } catch (SQLException exception) {
            exception.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }


    // ================================================================================================================
    // PHẦN 2. TÍNH — static, không đọc DB (BodyStatsServiceTest)
    // ================================================================================================================

    /** BMI = cân nặng (kg) ÷ (chiều cao m)², làm tròn 1 số lẻ. VD 58,6 kg, 160 cm → 22,9. */
    static BigDecimal bmi(BigDecimal weightKg, BigDecimal heightCm) {
        BigDecimal meters = heightCm.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);
        return weightKg.divide(meters.multiply(meters), 1, RoundingMode.HALF_UP);
    }

    /**
     * Nhóm BMI theo ngưỡng WHO cho người châu Á (Bộ Y tế VN dùng):
     *   < 18,5 Thiếu cân · 18,5–22,9 Bình thường · 23–24,9 Thừa cân · ≥ 25 Béo phì.
     * Trả {chữ, tone}; tone dùng tô màu: ok (xanh) | warn (vàng) | bad (đỏ).
     */
    static String[] bmiGroup(BigDecimal bmi) {
        double value = bmi.doubleValue();
        if (value < 18.5) {
            return new String[]{"Thiếu cân", "warn"};
        }
        if (value < 23) {
            return new String[]{"Bình thường", "ok"};
        }
        if (value < 25) {
            return new String[]{"Thừa cân", "warn"};
        }
        return new String[]{"Béo phì", "bad"};
    }

    /** 5 ô số: giá trị mới nhất có của từng chỉ số + thay đổi so với lần đầu có chỉ số đó. */
    static List<BodyStatTile> buildTiles(List<BodyStatRow> rows) {
        List<BodyStatTile> tiles = new ArrayList<>();
        tiles.add(tile("weight", "Cân nặng", "kg", rows, BodyStatRow::getWeightKg));
        tiles.add(tile("bmi", "BMI", "", rows, BodyStatRow::getBmi));
        tiles.add(tile("fat", "Tỷ lệ mỡ", "%", rows, BodyStatRow::getBodyFatPercent));
        tiles.add(tile("muscle", "Tỷ lệ cơ", "%", rows, BodyStatRow::getMusclePercent));
        tiles.add(tile("waist", "Vòng eo", "cm", rows, BodyStatRow::getWaistCm));
        return tiles;
    }

    private static BodyStatTile tile(String metric, String label, String unit, List<BodyStatRow> rows,
            Function<BodyStatRow, BigDecimal> getter) {
        BodyStatRow first = null;
        BodyStatRow last = null;
        for (BodyStatRow row : rows) {
            if (getter.apply(row) != null) {
                first = first == null ? row : first;
                last = row;
            }
        }
        if (last == null) {
            return new BodyStatTile(metric, label, "—", unit, null, null, null);
        }

        BigDecimal latest = getter.apply(last);
        String change = null;
        if (first != last) {
            BigDecimal diff = latest.subtract(getter.apply(first));
            change = signed(diff) + (unit.isEmpty() ? "" : " " + unit) + " so với "
                    + DateTimeUtil.formatDate(first.getMeasuredOn()).substring(0, 5);
        }
        String note = null;
        String tone = null;
        if ("bmi".equals(metric)) {
            String[] group = bmiGroup(latest);
            note = group[0];
            tone = group[1];
        }
        return new BodyStatTile(metric, label, format(latest), unit, change, note, tone);
    }

    /**
     * Biểu đồ đường 1 chỉ số. Trục ngang theo NGÀY THẬT (cách đều theo số ngày, không theo số lần đo),
     * trục dọc từ (nhỏ nhất − lề) tới (lớn nhất + lề) để thay đổi nhỏ vẫn nhìn thấy.
     * Ra: chuỗi points cho <polyline>, vùng tô cho <polygon>, các điểm, 3 vạch trục số, ngày đầu / cuối.
     */
    static void buildChart(BodyStatsView view, List<BodyStatRow> rows, String metric) {
        Function<BodyStatRow, BigDecimal> getter = switch (metric) {
            case "bmi" -> BodyStatRow::getBmi;
            case "fat" -> BodyStatRow::getBodyFatPercent;
            case "muscle" -> BodyStatRow::getMusclePercent;
            case "waist" -> BodyStatRow::getWaistCm;
            default -> BodyStatRow::getWeightKg;
        };
        String unit = switch (metric) {
            case "bmi" -> "";
            case "fat", "muscle" -> "%";
            case "waist" -> "cm";
            default -> "kg";
        };
        view.setMetric(metric);
        view.setMetricLabel(switch (metric) {
            case "bmi" -> "BMI";
            case "fat" -> "Tỷ lệ mỡ (%)";
            case "muscle" -> "Tỷ lệ cơ (%)";
            case "waist" -> "Vòng eo (cm)";
            default -> "Cân nặng (kg)";
        });

        // B1: chỉ lấy lần đo có chỉ số này.
        List<BodyStatRow> points = new ArrayList<>();
        for (BodyStatRow row : rows) {
            if (getter.apply(row) != null) {
                points.add(row);
            }
        }
        if (points.size() < 2) {
            return;
        }

        // B2: khoảng giá trị + lề 10% (ít nhất 1 đơn vị) để đường không dính mép.
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (BodyStatRow row : points) {
            double value = getter.apply(row).doubleValue();
            min = Math.min(min, value);
            max = Math.max(max, value);
        }
        double margin = Math.max((max - min) * 0.1, 1);
        double low = Math.floor(min - margin);
        double high = Math.ceil(max + margin);

        LocalDate firstDay = points.get(0).getMeasuredOn();
        long span = Math.max(1, ChronoUnit.DAYS.between(firstDay, points.get(points.size() - 1).getMeasuredOn()));
        double plotW = CHART_W - PAD_LEFT - PAD_RIGHT;
        double plotH = CHART_H - PAD_TOP - PAD_BOTTOM;

        // B3: toạ độ từng điểm.
        StringBuilder line = new StringBuilder();
        List<ChartPoint> chartPoints = new ArrayList<>();
        for (BodyStatRow row : points) {
            double value = getter.apply(row).doubleValue();
            double x = PAD_LEFT + plotW * ChronoUnit.DAYS.between(firstDay, row.getMeasuredOn()) / span;
            double y = PAD_TOP + plotH * (high - value) / (high - low);
            x = Math.round(x * 10) / 10.0;
            y = Math.round(y * 10) / 10.0;
            line.append(line.length() == 0 ? "" : " ").append(x).append(',').append(y);
            chartPoints.add(new ChartPoint(x, y, DateTimeUtil.formatDate(row.getMeasuredOn()).substring(0, 5)
                    + " · " + format(getter.apply(row)) + (unit.isEmpty() ? "" : " " + unit)));
        }
        double bottom = PAD_TOP + plotH;
        double firstX = chartPoints.get(0).getX();
        double lastX = chartPoints.get(chartPoints.size() - 1).getX();

        // B4: 3 vạch trục số: trên, giữa, dưới.
        List<ChartTick> ticks = new ArrayList<>();
        for (int i = 0; i <= 2; i++) {
            double value = high - (high - low) * i / 2;
            ticks.add(new ChartTick(PAD_TOP + plotH * i / 2, format(BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP))));
        }

        view.setChartLine(line.toString());
        view.setChartArea(firstX + "," + bottom + " " + line + " " + lastX + "," + bottom);
        view.setChartPoints(chartPoints);
        view.setChartTicks(ticks);
        view.setChartFrom(DateTimeUtil.formatDate(firstDay).substring(0, 5));
        view.setChartTo(DateTimeUtil.formatDate(points.get(points.size() - 1).getMeasuredOn()).substring(0, 5));
    }

    /** Chữ "58,6" / "58.6" / " 58 " → 58.6 (1 số lẻ). Trống → null. Không phải số → giá trị âm để checkInput báo sai. */
    static BigDecimal parseNumber(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(text.trim().replace(',', '.')).setScale(1, RoundingMode.HALF_UP);
        } catch (NumberFormatException exception) {
            return new BigDecimal("-1");
        }
    }

    /** Kiểm tra 1 lần đo. null = hợp lệ; còn lại là câu báo cho khách. */
    static String checkInput(LocalDate day, LocalDate today, BigDecimal weight, BigDecimal height, BigDecimal fat,
            BigDecimal muscle, BigDecimal waist, String note) {
        if (day.isAfter(today)) {
            return "Ngày đo không được ở tương lai.";
        }
        if (day.getYear() < 2000) {
            return "Ngày đo không hợp lệ.";
        }
        if (weight == null || outside(weight, WEIGHT_MIN, WEIGHT_MAX)) {
            return "Cân nặng phải từ 20 đến 300 kg.";
        }
        if (height == null || outside(height, HEIGHT_MIN, HEIGHT_MAX)) {
            return "Chiều cao phải từ 100 đến 250 cm.";
        }
        if (fat != null && outside(fat, FAT_MIN, FAT_MAX)) {
            return "Tỷ lệ mỡ phải từ 3 đến 70% (bỏ trống nếu không đo).";
        }
        if (muscle != null && outside(muscle, MUSCLE_MIN, MUSCLE_MAX)) {
            return "Tỷ lệ cơ phải từ 10 đến 70% (bỏ trống nếu không đo).";
        }
        if (waist != null && outside(waist, WAIST_MIN, WAIST_MAX)) {
            return "Vòng eo phải từ 40 đến 200 cm (bỏ trống nếu không đo).";
        }
        if (note != null && note.length() > 200) {
            return "Ghi chú tối đa 200 ký tự.";
        }
        return null;
    }

    private static boolean outside(BigDecimal value, BigDecimal min, BigDecimal max) {
        return value.compareTo(min) < 0 || value.compareTo(max) > 0;
    }

    /** 58.60 → "58,6"; 60.0 → "60". Dấu phẩy thập phân kiểu Việt Nam. */
    static String format(BigDecimal value) {
        BigDecimal clean = value.setScale(1, RoundingMode.HALF_UP).stripTrailingZeros();
        return clean.toPlainString().replace('.', ',');
    }

    /** -3.8 → "−3,8"; 1.2 → "+1,2"; 0 → "±0". */
    static String signed(BigDecimal diff) {
        int sign = diff.signum();
        if (sign == 0) {
            return "±0";
        }
        return (sign > 0 ? "+" : "−") + format(diff.abs());
    }

    private static String normalizeMetric(String metric) {
        return switch (metric == null ? "" : metric) {
            case "bmi", "fat", "muscle", "waist" -> metric;
            default -> "weight";
        };
    }
}
