package com.gms.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * DỮ LIỆU MÀN CHỈ SỐ CƠ THỂ (/customer/body-stats), biến JSP "stats".
 *
 *   Ai tạo?   BodyStatsService.getPage — đọc mọi lần đo của khách rồi tính:
 *     tiles      4 ô số (cân nặng, BMI, % mỡ, vòng eo) + thay đổi so với lần đầu
 *     chart*     biểu đồ đường của 1 chỉ số (chọn bằng ?metric=), toạ độ SVG Service tính sẵn
 *     rows       lịch sử đo, mới nhất trước
 *     form*      giá trị điền sẵn cho form "Ghi lần đo" (hôm nay, chiều cao lần trước)
 */
public class BodyStatsView {

    /** 1 điểm trên biểu đồ: toạ độ trong viewBox 0 0 640 240 + chữ khi rê chuột. */
    public static class ChartPoint {
        private final double x;
        private final double y;
        private final String label;   // "24/10 · 58,6 kg"

        public ChartPoint(double x, double y, String label) {
            this.x = x;
            this.y = y;
            this.label = label;
        }

        public double getX() { return x; }
        public double getY() { return y; }
        public String getLabel() { return label; }
    }

    /** 1 vạch ngang của trục số: toạ độ y + chữ ("60"). */
    public static class ChartTick {
        private final double y;
        private final String label;

        public ChartTick(double y, String label) {
            this.y = y;
            this.label = label;
        }

        public double getY() { return y; }
        public String getLabel() { return label; }
    }

    private List<BodyStatTile> tiles = new ArrayList<>();
    private List<BodyStatRow> rows = new ArrayList<>();

    // ===== Biểu đồ =====
    private String metric;                 // weight | bmi | fat | waist
    private String metricLabel;            // "Cân nặng (kg)"
    private String chartLine;              // "40,180 120,150 …" cho <polyline points>
    private String chartArea;              // vùng tô dưới đường cho <polygon points>
    private List<ChartPoint> chartPoints = new ArrayList<>();
    private List<ChartTick> chartTicks = new ArrayList<>();
    private String chartFrom;              // "05/09"
    private String chartTo;                // "24/10"

    // ===== Form ghi lần đo =====
    private LocalDate today;
    private BigDecimal lastHeight;         // chiều cao lần đo gần nhất, điền sẵn

    public List<BodyStatTile> getTiles() { return tiles; }
    public void setTiles(List<BodyStatTile> tiles) { this.tiles = tiles; }

    public List<BodyStatRow> getRows() { return rows; }
    public void setRows(List<BodyStatRow> rows) { this.rows = rows; }

    public String getMetric() { return metric; }
    public void setMetric(String metric) { this.metric = metric; }

    public String getMetricLabel() { return metricLabel; }
    public void setMetricLabel(String metricLabel) { this.metricLabel = metricLabel; }

    public String getChartLine() { return chartLine; }
    public void setChartLine(String chartLine) { this.chartLine = chartLine; }

    public String getChartArea() { return chartArea; }
    public void setChartArea(String chartArea) { this.chartArea = chartArea; }

    public List<ChartPoint> getChartPoints() { return chartPoints; }
    public void setChartPoints(List<ChartPoint> chartPoints) { this.chartPoints = chartPoints; }

    public List<ChartTick> getChartTicks() { return chartTicks; }
    public void setChartTicks(List<ChartTick> chartTicks) { this.chartTicks = chartTicks; }

    public String getChartFrom() { return chartFrom; }
    public void setChartFrom(String chartFrom) { this.chartFrom = chartFrom; }

    public String getChartTo() { return chartTo; }
    public void setChartTo(String chartTo) { this.chartTo = chartTo; }

    public LocalDate getToday() { return today; }
    public void setToday(LocalDate today) { this.today = today; }

    public BigDecimal getLastHeight() { return lastHeight; }
    public void setLastHeight(BigDecimal lastHeight) { this.lastHeight = lastHeight; }

    /** Chiều cao điền sẵn vào ô (dấu chấm cho ô nhập): 160.0 → "160"; chưa đo lần nào → "". */
    public String getLastHeightText() {
        return lastHeight == null ? "" : lastHeight.stripTrailingZeros().toPlainString();
    }

    /** JSP: biểu đồ cần ít nhất 2 lần đo có chỉ số đang chọn. */
    public boolean isHasChart() { return chartPoints.size() >= 2; }
}
