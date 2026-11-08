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
 * SERVICE MÀN CHỈ SỐ CƠ THỂ (khách) — /customer/body-stats. Chỉ khách xem / ghi dữ liệu của chính mình.
 *
 * AI GỌI SERVICE NÀY?  Chỉ BodyStatsServlet, mỗi lần 1 hàm chính. Servlet đưa chữ thô từ form; Service tự kiểm tra hết.
 * SERVICE GỌI AI?      BodyStatsDAO (bảng BodyMeasurements, tài khoản → mã khách).
 *
 * BỐ CỤC FILE — mỗi HÀM CHÍNH đứng trước, các hàm phụ của nó nằm ngay bên dưới:
 *   HÀM CHÍNH 1  getPage    mở trang: mọi lần đo → 5 ô số, biểu đồ 1 chỉ số, lịch sử   → buildTiles, tile, signed, bmiGroup, buildChart, normalizeMetric
 *   HÀM CHÍNH 2  save      ghi 1 lần đo (cùng ngày thì ghi đè)                         → parseNumber, checkInput, outside
 *   HÀM CHÍNH 3  delete    xóa 1 lần đo của chính mình
 *   DÙNG CHUNG             bmi, format
 *
 * LUẬT QUAN TRỌNG
 *   - Mỗi khách 1 lần đo / ngày: lưu lại cùng ngày → ghi đè (DAO dùng MERGE + HOLDLOCK).
 *   - Số nhập "58,6" hoặc "58.6" đều được; BMI không lưu DB, tính lúc đọc.
 *   - Các hàm static (bmi, bmiGroup, buildTiles, buildChart, parseNumber, checkInput) không đọc DB → BodyStatsServiceTest gọi thẳng.
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



    // ============================================================================================================
    // HÀM CHÍNH 1. getPage — mở trang: 5 ô số, biểu đồ 1 chỉ số, lịch sử
    //   Ai gọi: BodyStatsServlet.doGet  ·  Ra: BodyStatsView → JSP
    //   Hàm phụ ngay dưới, theo thứ tự getPage gọi: buildTiles → tile → signed · bmiGroup · buildChart · normalizeMetric
    // ============================================================================================================

    /**
     * HÀM CHÍNH 1 — dữ liệu cả màn: 5 ô số, biểu đồ 1 chỉ số, lịch sử.
     *   Ai gọi: BodyStatsServlet.doGet
     *   Trả về: ok(BodyStatsView) | lỗi (không phải khách / lỗi hệ thống)
     */
    public ServiceResult<BodyStatsView> getPage(int accountId, String metric, LocalDate today) {
        try {
            // B1: tài khoản phải là khách, đọc mọi lần đo (cũ trước)
            Integer customerId = statsDAO.findCustomerId(accountId);
            if (customerId == null) {
                return ServiceResult.fail(Messages.MSG07_NO_PERMISSION);
            }
            List<BodyStatRow> rows = statsDAO.findByCustomer(customerId);   // cũ trước

            // B2: tính BMI + chênh cân nặng so với lần trước
            BigDecimal previous = null;
            for (BodyStatRow row : rows) {
                row.setBmi(bmi(row.getWeightKg(), row.getHeightCm()));
                row.setWeightChange(previous == null ? null : row.getWeightKg().subtract(previous));
                previous = row.getWeightKg();
            }

            // Tạo DTO, gắn ngày hôm nay
            BodyStatsView view = new BodyStatsView();
            view.setToday(today);

            // B3: 5 ô số + biểu đồ
            // buildTiles trả về danh sách 5 ô (cân nặng, BMI, mỡ, cơ, vòng eo), mỗi ô có giá trị mới nhất + thay đổi so với lần đầu. Gán thẳng vào view.
            view.setTiles(buildTiles(rows));
            // normalizeMetric(metric): chữ ?metric= trên URL chỉ nhận bmi / fat / muscle / waist, còn lại (trống, chữ lạ) → weight. Kiểu whitelist như ô sắp xếp của thư viện bài tập.
            buildChart(view, rows, normalizeMetric(metric));

            // B4: điền sẵn chiều cao lần gần nhất; lịch sử in mới nhất trước
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

    /** HÀM PHỤ (getPage B2) — 5 ô số: giá trị mới nhất có của từng chỉ số + thay đổi so với lần đầu có chỉ số đó. */
    static List<BodyStatTile> buildTiles(List<BodyStatRow> rows) {
        List<BodyStatTile> tiles = new ArrayList<>();
        tiles.add(tile("weight", "Cân nặng", "kg", rows, BodyStatRow::getWeightKg));
        tiles.add(tile("bmi", "BMI", "", rows, BodyStatRow::getBmi));
        tiles.add(tile("fat", "Tỷ lệ mỡ", "%", rows, BodyStatRow::getBodyFatPercent));
        tiles.add(tile("muscle", "Tỷ lệ cơ", "%", rows, BodyStatRow::getMusclePercent));
        tiles.add(tile("waist", "Vòng eo", "cm", rows, BodyStatRow::getWaistCm));
        return tiles;
    }

    /** HÀM PHỤ (buildTiles) — 1 ô số: giá trị mới nhất có của chỉ số + thay đổi so với lần đầu (signed), nhóm BMI (bmiGroup) cho ô BMI. */
    private static BodyStatTile tile(
            String metric,
            String label,
            String unit,
            List<BodyStatRow> rows,
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
            return new BodyStatTile(
                    metric,
                    label,
                    "—",
                    unit,
                    null,
                    null,
                    null);
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
        return new BodyStatTile(
                metric,
                label,
                format(latest),
                unit,
                change,
                note,
                tone);
    }

    /** HÀM PHỤ (tile) — -3.8 → "−3,8"; 1.2 → "+1,2"; 0 → "±0". */
    static String signed(BigDecimal diff) {
        int sign = diff.signum();
        if (sign == 0) {
            return "±0";
        }
        return (sign > 0 ? "+" : "−") + format(diff.abs());
    }

    /**
     * HÀM PHỤ (tile) — nhóm BMI theo ngưỡng WHO cho người châu Á (Bộ Y tế VN dùng):
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

    /**
     * HÀM PHỤ (getPage B2) — biểu đồ đường 1 chỉ số. Trục ngang theo NGÀY THẬT (cách đều theo số ngày, không theo số lần đo),
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

        // B1:  // Chỉ giữ lần đo có chỉ số này (mỡ, cơ, vòng eo được bỏ trống). Dưới 2 điểm thì return, vì 1 điểm không thành đường. JSP thấy hasChart = false thì hiện chữ “cần ít nhất 2 lần đo”.
        List<BodyStatRow> points = new ArrayList<>();
        for (BodyStatRow row : rows) {
            if (getter.apply(row) != null) {
                points.add(row);
            }
        }

        if (points.size() < 2) {
            return;
        }

        // B2: Chọn khoảng chục dọc từ min đến max
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (BodyStatRow row : points) {
            double value = getter.apply(row).doubleValue();
            min = Math.min(min, value);
            max = Math.max(max, value);
        }
        double margin = Math.max((max - min) * 0.1, 1); // lề: đề điểm thấp nhất và cao nhất ko dính sát hay tràn ngoài khung
        double low = Math.floor(min - margin);
        double high = Math.ceil(max + margin);

        
        // 
        LocalDate firstDay = points.get(0).getMeasuredOn();
        long span = Math.max(1, ChronoUnit.DAYS.between(firstDay, points.get(points.size() - 1).getMeasuredOn()));
        double plotW = CHART_W - PAD_LEFT - PAD_RIGHT;
        double plotH = CHART_H - PAD_TOP - PAD_BOTTOM;

        // B3: tọa độ từng điểm
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

        // B4: 3 vạch trục số
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

    /** HÀM PHỤ (getPage B2) — ?metric= lạ / trống → "weight"; còn lại giữ nguyên (weight | bmi | fat | muscle | waist). */
    private static String normalizeMetric(String metric) {
        return switch (metric == null ? "" : metric) {
            case "bmi", "fat", "muscle", "waist" -> metric;
            default -> "weight";
        };
    }



    // ============================================================================================================
    // HÀM CHÍNH 2. save — form "Ghi lần đo"
    //   Ai gọi: BodyStatsServlet.doPost (action=save)  ·  Gọi xuống: BodyStatsDAO.save
    //   Hàm phụ ngay dưới: parseNumber · checkInput → outside
    // ============================================================================================================

    /**
     * HÀM CHÍNH 2 — ghi 1 lần đo (cùng ngày đã có → ghi đè).
     *   Ai gọi: BodyStatsServlet.doPost (action=save)
     *   Trả về: ok(câu báo đã lưu + BMI) | câu lỗi
     */
    public ServiceResult<Void> save(
            int accountId,
            String dateText,
            String weightText,
            String heightText,
            String fatText,
            String muscleText,
            String waistText,
            String noteText,
            LocalDate today) {
        // B1: đọc và kiểm tra chữ trên form
        LocalDate day;
        try {
            day = LocalDate.parse(dateText == null ? "" : dateText.trim());
        } catch (DateTimeException exception) {
            return ServiceResult.fail(Messages.MSG299_MEASUREMENT_DATE_REQUIRED);
        }
        BigDecimal weight = parseNumber(weightText);
        BigDecimal height = parseNumber(heightText);
        BigDecimal fat = parseNumber(fatText);
        BigDecimal muscle = parseNumber(muscleText);
        BigDecimal waist = parseNumber(waistText);
        String note = noteText == null || noteText.isBlank() ? null : noteText.trim();
        String problem = checkInput(
                day,
                today,
                weight,
                height,
                fat,
                muscle,
                waist,
                note);
        if (problem != null) {
            return ServiceResult.fail(problem);
        }

        // B2: tìm mã khách rồi lưu (cùng ngày → ghi đè)
        try {
            Integer customerId = statsDAO.findCustomerId(accountId);
            if (customerId == null) {
                return ServiceResult.fail(Messages.MSG07_NO_PERMISSION);
            }
            statsDAO.save(
                    customerId,
                    day,
                    weight,
                    height,
                    fat,
                    muscle,
                    waist,
                    note);
            return ServiceResult.ok(null, String.format(Messages.MSG300_MEASUREMENT_SAVED, DateTimeUtil.formatDate(day),
                    format(bmi(weight, height))));
        } catch (SQLException exception) {
            exception.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /** HÀM PHỤ (save B1) — chữ "58,6" / "58.6" / " 58 " → 58.6 (1 số lẻ). Trống → null. Không phải số → giá trị âm để checkInput báo sai. */
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

    /** HÀM PHỤ (save B1) — kiểm tra 1 lần đo (ngày, khoảng hợp lệ từng chỉ số, ghi chú). null = hợp lệ; còn lại là câu báo cho khách. */
    static String checkInput(
            LocalDate day,
            LocalDate today,
            BigDecimal weight,
            BigDecimal height,
            BigDecimal fat,
            BigDecimal muscle,
            BigDecimal waist,
            String note) {
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
            return String.format(Messages.MSG314_NOTE_MAX_LENGTH, 200);
        }
        return null;
    }

    /** HÀM PHỤ (checkInput) — value nằm ngoài [min, max] → true. */
    private static boolean outside(BigDecimal value, BigDecimal min, BigDecimal max) {
        return value.compareTo(min) < 0 || value.compareTo(max) > 0;
    }



    // ============================================================================================================
    // HÀM CHÍNH 3. delete — nút Xóa trong bảng lịch sử
    //   Ai gọi: BodyStatsServlet.doPost (action=delete)  ·  Gọi xuống: BodyStatsDAO.delete
    // ============================================================================================================

    /**
     * HÀM CHÍNH 3 — xóa 1 lần đo của chính mình.
     *   Ai gọi: BodyStatsServlet.doPost (action=delete)
     *   Trả về: ok | lỗi (không có lần đo đó)
     */
    public ServiceResult<Void> delete(int accountId, int measurementId) {
        try {
            // B1: tài khoản phải là khách
            Integer customerId = statsDAO.findCustomerId(accountId);
            if (customerId == null) {
                return ServiceResult.fail(Messages.MSG07_NO_PERMISSION);
            }

            // B2: xóa (không có lần đo đó của mình → báo lỗi)
            if (!statsDAO.delete(customerId, measurementId)) {
                return ServiceResult.fail(Messages.MSG298_MEASUREMENT_NOT_FOUND);
            }
            return ServiceResult.ok(null, Messages.MSG301_MEASUREMENT_DELETED);
        } catch (SQLException exception) {
            exception.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }



    // ============================================================================================================
    // DÙNG CHUNG — tính BMI, in số kiểu Việt Nam (static, không đọc DB)
    // ============================================================================================================

    /** HÀM PHỤ (dùng chung: getPage B1, save) — BMI = cân nặng (kg) ÷ (chiều cao m)², làm tròn 1 số lẻ. VD 58,6 kg, 160 cm → 22,9. */
    static BigDecimal bmi(BigDecimal weightKg, BigDecimal heightCm) {
        BigDecimal meters = heightCm.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);
        return weightKg.divide(meters.multiply(meters), 1, RoundingMode.HALF_UP);
    }

    /** HÀM PHỤ (dùng chung: save, tile, buildChart, signed) — 58.60 → "58,6"; 60.0 → "60". Dấu phẩy thập phân kiểu Việt Nam. */
    static String format(BigDecimal value) {
        BigDecimal clean = value.setScale(1, RoundingMode.HALF_UP).stripTrailingZeros();
        return clean.toPlainString().replace('.', ',');
    }
}
