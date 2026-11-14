package com.gms.service;

import com.gms.constant.Messages;
import com.gms.dao.ReportDAO;
import com.gms.dto.AdminDashboardView;
import com.gms.dto.AdminDashboardView.PackageRevenueRow;
import com.gms.dto.AdminDashboardView.PtCommissionRow;
import com.gms.dto.AdminDashboardView.SensitiveActionRow;
import com.gms.dto.AdminDashboardView.SensitiveActorRow;
import com.gms.dto.ServiceResult;
import com.gms.util.DateTimeUtil;
import com.gms.util.MoneyUtil;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Tầng Service tổng hợp báo cáo vận hành, doanh thu và chỉ số audit dành cho Admin (UC-44, màn #68).
 *
 * <p><b>Cách hoạt động:</b> Kiểm tra hợp lệ khoảng ngày truy vấn ({@code fromDate <= toDate}), gọi
 * {@link ReportDAO} tải toàn bộ số liệu trên cùng một phiên CSDL và dựng CSV chuẩn UTF-8 giữ định dạng
 * tiền {@code "đ"} theo đúng số liệu đang hiển thị (GBR-33).</p>
 */
public class SystemReportService {

    private static final Logger LOGGER = Logger.getLogger(SystemReportService.class.getName());
    private static final DateTimeFormatter FILE_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private final ReportDAO reportDAO;

    /**
     * Khởi tạo Service cùng {@link ReportDAO} mặc định.
     */
    public SystemReportService() {
        this(new ReportDAO());
    }

    /**
     * Khởi tạo Service với {@link ReportDAO} truyền vào để thuận tiện kiểm thử đơn vị.
     */
    public SystemReportService(ReportDAO reportDAO) {
        this.reportDAO = reportDAO;
    }

    /**
     * Tải toàn bộ dữ liệu Admin Dashboard trong khoảng ngày được chọn.
     *
     * <p><b>Cách hoạt động:</b> Validate {@code fromDate} và {@code toDate} bắt buộc, đảm bảo
     * {@code fromDate <= toDate} (MSG16), sau đó truy vấn {@link ReportDAO#findAdminDashboard}
     * và trả về {@link AdminDashboardView}.</p>
     *
     * @param fromDate ngày bắt đầu, tính cả ngày này
     * @param toDate   ngày kết thúc, tính cả ngày này
     * @return {@link ServiceResult} chứa dữ liệu báo cáo hoặc thông báo lỗi validate
     */
    public ServiceResult<AdminDashboardView> getAdminDashboard(LocalDate fromDate, LocalDate toDate) {
        if (fromDate == null) {
            return ServiceResult.fail(String.format(Messages.MSG09_REQUIRED, "Từ ngày"));
        }
        if (toDate == null) {
            return ServiceResult.fail(String.format(Messages.MSG09_REQUIRED, "Đến ngày"));
        }

        if (fromDate.isAfter(toDate)) {
            return ServiceResult.fail(Messages.MSG16_DATE_RANGE_REVERSED);
        }

        try {
            AdminDashboardView dashboard = reportDAO.findAdminDashboard(fromDate, toDate);
            return ServiceResult.ok(dashboard);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Lỗi tải báo cáo Admin từ " + fromDate + " đến " + toDate, e);
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /**
     * Tạo tên file CSV xuất báo cáo theo khoảng ngày đang xem (bao-cao-admin_dd-MM-yyyy_den_dd-MM-yyyy.csv).
     */
    public static String buildCsvFileName(LocalDate fromDate, LocalDate toDate) {
        return "bao-cao-admin_" + fromDate.format(FILE_DATE_FORMATTER)
                + "_den_" + toDate.format(FILE_DATE_FORMATTER) + ".csv";
    }

    /**
     * Dựng nội dung CSV theo đúng dữ liệu đang hiển thị trên Admin Dashboard (GBR-33),
     * giữ nguyên định dạng tiền {@code "đ"} (VD {@code "55.255.000đ"}).
     *
     * @param view dữ liệu báo cáo đã tải hợp lệ
     * @return chuỗi nội dung CSV (chưa gồm 3 byte BOM UTF-8 do Servlet ghi ở đầu stream)
     */
    public String buildCsv(AdminDashboardView view) {
        StringBuilder csv = new StringBuilder();
        String rangeText = DateTimeUtil.formatDate(view.getFromDate())
                + " - " + DateTimeUtil.formatDate(view.getToDate());

        appendCsvRow(csv, "Báo cáo vận hành hệ thống Admin");
        appendCsvRow(csv, "Khoảng ngày", rangeText);
        appendCsvRow(csv);

        appendCsvRow(csv, "TỔNG QUAN VẬN HÀNH");
        appendCsvRow(csv, "Chỉ số", "Giá trị");
        appendCsvRow(csv, "Tổng doanh thu", MoneyUtil.formatMoney(view.getTotalRevenue()));
        appendCsvRow(csv, "Lượt check-in", view.getCheckInCount());
        appendCsvRow(csv, "Lượt vé ngày", view.getDayPassVisits());
        appendCsvRow(csv, "Doanh thu vé ngày", MoneyUtil.formatMoney(view.getDayPassRevenue()));
        appendCsvRow(csv);

        appendCsvRow(csv, "DOANH THU THEO TỪNG GÓI");
        appendCsvRow(csv, "Tên gói", "Giao dịch", "Doanh thu", "Trong đó VAT");
        for (PackageRevenueRow row : view.getRevenueByPackage()) {
            appendCsvRow(
                    csv,
                    row.getPackageName(),
                    row.getTransactionCount(),
                    MoneyUtil.formatMoney(row.getRevenue()),
                    MoneyUtil.formatMoney(row.getVatAmount())
            );
        }
        appendCsvRow(csv);

        appendCsvRow(csv, "HOA HỒNG THEO PT");
        appendCsvRow(csv, "Mã PT", "Họ tên", "Buổi được tính", "Tổng hoa hồng");
        for (PtCommissionRow row : view.getPtCommissions()) {
            appendCsvRow(
                    csv,
                    row.getEmployeeCode(),
                    row.getFullName(),
                    row.getSessionCount(),
                    MoneyUtil.formatMoney(row.getCommissionAmount())
            );
        }
        appendCsvRow(csv);

        appendCsvRow(csv, "AUDIT & BẢO MẬT");
        appendCsvRow(csv, "Chỉ số", "Số lần");
        appendCsvRow(csv, "Đăng nhập thành công", view.getSignInCount());
        appendCsvRow(csv, "Đăng nhập thất bại", view.getSignInFailedCount());
        appendCsvRow(csv, "Khóa tạm do đăng nhập sai", view.getTempLockedCount());
        appendCsvRow(csv, "Tài khoản bị khóa", view.getLockedAccountCount());
        appendCsvRow(csv, "Thay đổi tài khoản", view.getAccountAuditChangeCount());
        appendCsvRow(csv);

        appendCsvRow(csv, "THAO TÁC NHẠY CẢM THEO LOẠI");
        appendCsvRow(csv, "Thao tác", "Số lần");
        for (SensitiveActionRow row : view.getSensitiveActions()) {
            appendCsvRow(csv, row.getActionLabel(), row.getTotal());
        }
        appendCsvRow(csv);

        appendCsvRow(csv, "THAO TÁC NHẠY CẢM THEO NGƯỜI THỰC HIỆN");
        appendCsvRow(csv, "Người thực hiện", "Vai trò", "Số lần");
        for (SensitiveActorRow row : view.getSensitiveActors()) {
            appendCsvRow(csv, row.getActorName(), row.getActorRoleLabel(), row.getTotal());
        }
        return csv.toString();
    }

    private static void appendCsvRow(StringBuilder csv, Object... cells) {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                csv.append(',');
            }
            Object cell = cells[i];
            csv.append(cell instanceof Number ? cell.toString() : escapeCsv(String.valueOf(cell)));
        }
        csv.append("\r\n");
    }

    /** Chống lỗi tách cột và CSV formula injection khi mở bằng Excel. */
    static String escapeCsv(String text) {
        if (text == null) {
            return "";
        }
        String value = text;
        if (!value.isEmpty() && "=+-@\t\r".indexOf(value.charAt(0)) >= 0) {
            value = "'" + value;
        }
        boolean needsQuotes = value.indexOf(',') >= 0
                || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0
                || value.indexOf('\r') >= 0;
        if (needsQuotes) {
            value = "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
