package com.gms.service;

import com.gms.constant.Messages;
import com.gms.dao.ReportDAO;
import com.gms.dto.ReportView;
import com.gms.dto.ServiceResult;
import com.gms.util.DBContext;
import com.gms.util.DateTimeUtil;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Nghiệp vụ Báo cáo vận hành & doanh thu của Manager (SRS 12.1.1, UC-46).
 *
 * Luồng getReport:
 *   B1: kiểm tra From/To (cả hai bắt buộc, From ≤ To, không giới hạn độ dài — MSG16)
 *   B2: mở 1 Connection, chạy các truy vấn của ReportDAO (cùng "ảnh chụp")
 *   B3: dựng biểu đồ: ≤ 62 ngày → cột theo ngày; dài hơn → gộp cột theo tháng (khoảng ngày không giới hạn)
 *   B4: trả ReportView; lỗi DB → MSG04
 *
 * Xuất CSV: buildCsv() dựng từ đúng ReportView đang hiển thị, không truy vấn lại.
 */
public class ReportService {

    /** Quá ngưỡng này thì gộp cột theo tháng cho biểu đồ đọc được. */
    private static final int MAX_DAILY_BARS = 62;
    /** Số nhãn tối đa hiện dưới trục X. */
    private static final int MAX_X_LABELS = 7;
    /** Số vạch tối đa của trục Y. */
    private static final int MAX_Y_INTERVALS = 5;

    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter FULL_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FULL_DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final String DAY_PASS = "DayPass";

    private final ReportDAO reportDAO = new ReportDAO();

    /**
     * Dựng báo cáo cho khoảng ngày [from, to] (tính cả hai đầu).
     *
     * @return thành công kèm ReportView; From &gt; To → lỗi MSG16; lỗi DB → MSG04
     */
    public ServiceResult<ReportView> getReport(LocalDate from, LocalDate to) {
        // B1
        if (from == null || to == null) {
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
        if (from.isAfter(to)) {
            return ServiceResult.failField("from", Messages.MSG16_DATE_RANGE_REVERSED);
        }

        LocalDateTime start = from.atStartOfDay();
        LocalDateTime endExclusive = to.plusDays(1).atStartOfDay();

        // B2
        try (Connection conn = DBContext.getConnection()) {
            ReportView view = new ReportView(from, to, DateTimeUtil.now());

            long[] revenueAndVat = reportDAO.sumRevenueAndVat(conn, start, endExclusive);
            view.setTotalRevenue(revenueAndVat[0]);
            view.setTotalVat(revenueAndVat[1]);

            List<ReportView.PackageRow> packages = reportDAO.findRevenueByPackage(conn, start, endExclusive);
            view.setPackages(packages);
            long dayPassRevenue = 0;
            for (ReportView.PackageRow row : packages) {
                if (DAY_PASS.equals(row.getPackageType())) {
                    dayPassRevenue += row.getRevenue();
                }
            }
            view.setDayPassRevenue(dayPassRevenue);

            long[] checkIns = reportDAO.countCheckIns(conn, start, endExclusive);
            view.setCheckInCount(checkIns[0]);
            view.setDayPassVisits(checkIns[1]);

            List<ReportView.PtRow> ptRows = reportDAO.findPtCommission(conn, from, to);
            view.setPtRows(ptRows);
            long totalCommission = 0;
            for (ReportView.PtRow row : ptRows) {
                totalCommission += row.getCommission();
            }
            view.setTotalCommission(totalCommission);

            view.setActiveMembers(reportDAO.countActiveMembers(conn));
            view.setRunningPackages(reportDAO.countRunningPackages(conn));
            view.setNewMembers(reportDAO.countNewMembers(conn, start, endExclusive));

            // B3
            buildChart(view, reportDAO.findDailyRevenue(conn, start, endExclusive));

            // B4
            return ServiceResult.ok(view);
        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    // ===== Biểu đồ =====

    /** Dựng các cột (theo ngày hoặc theo tháng), trục Y và vạch kẻ. */
    private void buildChart(ReportView view, Map<LocalDate, Long> daily) {
        LocalDate from = view.getFrom();
        LocalDate to = view.getTo();
        long dayCount = ChronoUnit.DAYS.between(from, to) + 1;

        List<ReportView.Bucket> buckets = new ArrayList<>();
        boolean isMonthly = dayCount > MAX_DAILY_BARS;

        if (!isMonthly) {
            for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
                buckets.add(new ReportView.Bucket(
                        day.getDayOfMonth() + "/" + day.getMonthValue(),
                        day.format(FULL_DATE),
                        daily.getOrDefault(day, 0L)));
            }
        } else {
            Map<YearMonth, Long> byMonth = new TreeMap<>();
            for (YearMonth ym = YearMonth.from(from); !ym.isAfter(YearMonth.from(to)); ym = ym.plusMonths(1)) {
                byMonth.put(ym, 0L);
            }
            for (Map.Entry<LocalDate, Long> entry : daily.entrySet()) {
                byMonth.merge(YearMonth.from(entry.getKey()), entry.getValue(), Long::sum);
            }
            for (Map.Entry<YearMonth, Long> entry : byMonth.entrySet()) {
                YearMonth ym = entry.getKey();
                String label = String.format("%02d/%d", ym.getMonthValue(), ym.getYear());
                buckets.add(new ReportView.Bucket(label, "Tháng " + label, entry.getValue()));
            }
        }

        // Trục Y: chọn bước "đẹp" để có tối đa MAX_Y_INTERVALS khoảng
        long max = 0;
        for (ReportView.Bucket bucket : buckets) {
            max = Math.max(max, bucket.getAmount());
        }
        long step = calculateNiceStep(max);
        long axisMax = Math.max(step, ((max + step - 1) / step) * step);

        int labelEvery = (int) Math.ceil(buckets.size() / (double) MAX_X_LABELS);
        for (int i = 0; i < buckets.size(); i++) {
            ReportView.Bucket bucket = buckets.get(i);
            bucket.setPercent(roundToTwoDecimals(bucket.getAmount() * 100.0 / axisMax));
            bucket.setLabelVisible(i % labelEvery == 0);
            bucket.setLast(i == buckets.size() - 1);
        }

        List<ReportView.Tick> ticks = new ArrayList<>();
        for (long value = 0; value <= axisMax; value += step) {
            ticks.add(new ReportView.Tick(formatTickLabel(value), roundToTwoDecimals(value * 100.0 / axisMax)));
        }

        view.setBuckets(buckets);
        view.setTicks(ticks);
        view.setMonthlyChart(isMonthly);
    }

    /** Bước nhỏ nhất trong dãy 100k, 200k, 500k, 1tr, 2tr, 5tr, 10tr... để số khoảng ≤ MAX_Y_INTERVALS. */
    private static long calculateNiceStep(long max) {
        long base = 100_000L;
        long[] multipliers = {1, 2, 5};
        while (true) {
            for (long multiplier : multipliers) {
                long step = base * multiplier;
                if (max <= step * MAX_Y_INTERVALS) {
                    return step;
                }
            }
            base *= 10;
        }
    }

    private static String formatTickLabel(long value) {
        if (value == 0) {
            return "0";
        }
        if (value % 1_000_000L == 0) {
            return (value / 1_000_000L) + "tr";
        }
        return (value / 1_000L) + "k";
    }

    private static double roundToTwoDecimals(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    // ===== Xuất CSV =====

    /** Tên file theo mockup: bao-cao_dd-mm-yyyy_den_dd-mm-yyyy.csv */
    public static String buildFileName(LocalDate from, LocalDate to) {
        return "bao-cao_" + from.format(FILE_DATE) + "_den_" + to.format(FILE_DATE) + ".csv";
    }

    /**
     * Dựng nội dung CSV từ đúng ReportView đang hiển thị (SRS: xuất số liệu đang hiện trên màn hình).
     * Tiền là số nguyên đồng, không có dấu phân cách nghìn. Servlet chịu trách nhiệm thêm BOM UTF-8.
     */
    public String buildCsv(ReportView view) {
        StringBuilder csv = new StringBuilder();
        String range = view.getFrom().format(FILE_DATE) + " đến " + view.getTo().format(FILE_DATE);

        appendRow(csv, "Báo cáo " + range);
        appendRow(csv, "Số liệu lúc", view.getGeneratedAt().format(FULL_DATE_TIME));
        appendRow(csv);

        appendRow(csv, "TỔNG QUAN");
        appendRow(csv, "Chỉ số", "Giá trị");
        appendRow(csv, "Doanh thu (đã gồm VAT)", view.getTotalRevenue());
        appendRow(csv, "Trong đó VAT", view.getTotalVat());
        appendRow(csv, "Hội viên đang hoạt động", view.getActiveMembers());
        appendRow(csv, "Hội viên mới trong kỳ", view.getNewMembers());
        appendRow(csv, "Gói đang chạy (không gồm vé 1 ngày)", view.getRunningPackages());
        appendRow(csv, "Hoa hồng PT", view.getTotalCommission());
        appendRow(csv, "Vé 1 ngày (lượt)", view.getDayPassVisits());
        appendRow(csv, "Vé 1 ngày (doanh thu)", view.getDayPassRevenue());
        appendRow(csv, "Lượt check-in (gồm vé 1 ngày)", view.getCheckInCount());
        appendRow(csv);

        appendRow(csv, "DOANH THU THEO GÓI");
        appendRow(csv, "Gói", "Số đơn", "Doanh thu (đã gồm VAT)", "VAT");
        long orderTotal = 0;
        for (ReportView.PackageRow row : view.getPackages()) {
            appendRow(csv, row.getPackageName(), row.getOrderCount(), row.getRevenue(), row.getVat());
            orderTotal += row.getOrderCount();
        }
        appendRow(csv, "Tổng", orderTotal, view.getTotalRevenue(), view.getTotalVat());
        appendRow(csv);

        appendRow(csv, "HOA HỒNG PT");
        appendRow(csv, "PT", "% hoa hồng", "Hoàn thành", "Vắng", "Hủy", "Hoa hồng");
        long completed = 0;
        long missed = 0;
        long cancelled = 0;
        for (ReportView.PtRow row : view.getPtRows()) {
            appendRow(csv, row.getFullName(), row.getCommissionRate(), row.getCompleted(),
                    row.getMissed(), row.getCancelled(), row.getCommission());
            completed += row.getCompleted();
            missed += row.getMissed();
            cancelled += row.getCancelled();
        }
        appendRow(csv, "Tổng", "", completed, missed, cancelled, view.getTotalCommission());
        appendRow(csv);

        appendRow(csv, view.isMonthlyChart() ? "DOANH THU THEO THÁNG" : "DOANH THU THEO NGÀY");
        appendRow(csv, view.isMonthlyChart() ? "Tháng" : "Ngày", "Doanh thu (đã gồm VAT)");
        for (ReportView.Bucket bucket : view.getBuckets()) {
            appendRow(csv, bucket.getFullLabel(), bucket.getAmount());
        }
        return csv.toString();
    }

    /** Ghi 1 dòng CSV: chữ được escape, số ghi nguyên. */
    private static void appendRow(StringBuilder csv, Object... cells) {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                csv.append(',');
            }
            Object cell = cells[i];
            csv.append(cell instanceof Number ? cell.toString() : escapeCsv(String.valueOf(cell)));
        }
        csv.append("\r\n");
    }

    /**
     * Escape 1 ô chữ: bọc "..." nếu có dấu phẩy/nháy/xuống dòng; chèn ' đầu ô nếu bắt đầu bằng = + - @
     * để Excel không hiểu nhầm là công thức (tên gói, tên PT do người dùng nhập).
     */
    private static String escapeCsv(String text) {
        String value = text;
        if (!value.isEmpty() && "=+-@\t\r".indexOf(value.charAt(0)) >= 0) {
            value = "'" + value;
        }
        boolean isQuoteNeeded = value.indexOf(',') >= 0 || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0;
        if (isQuoteNeeded) {
            value = "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
