package com.gms.servlet.admin;

import com.gms.constant.Messages;
import com.gms.dto.AdminDashboardView;
import com.gms.dto.ServiceResult;
import com.gms.service.SystemReportService;
import com.gms.util.DateTimeUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Báo cáo vận hành hệ thống cho Quản trị viên (#68 System Operational Report, UC-44).
 *
 * Hỗ trợ:
 * - GET /admin/dashboard: hiển thị báo cáo theo khoảng ngày (mặc định từ đầu tháng đến hôm nay).
 * - GET /admin/dashboard?fromDate=...&toDate=...&export=csv: xuất file CSV UTF-8 BOM (UC-44, GBR-33).
 */
@WebServlet("/admin/dashboard")
public class AdminDashboardServlet extends HttpServlet {

    private static final String VIEW_PATH = "/WEB-INF/jsp/admin/dashboard.jsp";
    private static final byte[] UTF8_BOM = new byte[] { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF };

    private final SystemReportService systemReportService;

    public AdminDashboardServlet() {
        this(new SystemReportService());
    }

    public AdminDashboardServlet(SystemReportService systemReportService) {
        this.systemReportService = Objects.requireNonNull(systemReportService);
    }

    /** Cho phép test ghi đè ngày hiện tại để cố định khoảng mặc định đầu tháng → hôm nay. */
    protected LocalDate currentDate() {
        return DateTimeUtil.today();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        LocalDate today = currentDate();
        LocalDate defaultFrom = today.withDayOfMonth(1);
        LocalDate defaultTo = today;

        DateParseOutcome fromOutcome = parseDateParam(
                request.getParameter("fromDate"), defaultFrom, "Từ ngày");
        DateParseOutcome toOutcome = parseDateParam(
                request.getParameter("toDate"), defaultTo, "Đến ngày");

        LocalDate fromDate = fromOutcome.date;
        LocalDate toDate = toOutcome.date;
        String fromError = fromOutcome.error;
        String toError = toOutcome.error;

        if (fromError == null && toError == null && fromDate.isAfter(toDate)) {
            toError = Messages.MSG16_DATE_RANGE_REVERSED;
        }

        boolean isInvalidRange = fromError != null || toError != null;
        boolean isExportCsv = "csv".equalsIgnoreCase(request.getParameter("export"));

        request.setAttribute("fromDateValue", fromOutcome.inputValue);
        request.setAttribute("toDateValue", toOutcome.inputValue);
        request.setAttribute("fromError", fromError);
        request.setAttribute("toError", toError);
        request.setAttribute("isInvalidRange", isInvalidRange);
        request.setAttribute("noDataMessage", Messages.MSG03_NO_DATA_IN_RANGE);

        if (isInvalidRange) {
            request.setAttribute("emptyInRange", false);
            request.setAttribute("exportedMessage", "");
            if (isExportCsv) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            }
            request.getRequestDispatcher(VIEW_PATH).forward(request, response);
            return;
        }

        ServiceResult<AdminDashboardView> result = systemReportService.getAdminDashboard(fromDate, toDate);
        if (!result.isSuccess()) {
            request.setAttribute("errorMessage", result.getMessage());
            request.setAttribute("emptyInRange", false);
            request.setAttribute("exportedMessage", "");
            if (isExportCsv) {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
            request.getRequestDispatcher(VIEW_PATH).forward(request, response);
            return;
        }

        AdminDashboardView view = result.getData();
        if (isExportCsv) {
            writeCsvResponse(response, fromDate, toDate, view);
            return;
        }

        String fileName = SystemReportService.buildCsvFileName(fromDate, toDate);
        request.setAttribute("report", view);
        request.setAttribute("emptyInRange", view.isEmptyInRange());
        request.setAttribute("exportedMessage", String.format(Messages.MSG32_EXPORTED, fileName));
        request.getRequestDispatcher(VIEW_PATH).forward(request, response);
    }

    /**
     * Phân tích tham số ngày từ URL:
     * - Thiếu tham số (null) -> dùng mặc định.
     * - Chuỗi rỗng ("") -> người dùng xóa trống khi gửi form -> báo MSG09.
     * - Sai định dạng URL (VD ?fromDate=abc) -> bỏ qua giá trị lỗi và rơi về mặc định (GBR-20).
     */
    private DateParseOutcome parseDateParam(String rawParam, LocalDate fallback, String fieldLabel) {
        if (rawParam == null) {
            return new DateParseOutcome(fallback, fallback.toString(), null);
        }
        String trimmed = rawParam.trim();
        if (trimmed.isEmpty()) {
            return new DateParseOutcome(null, "", String.format(Messages.MSG09_REQUIRED, fieldLabel));
        }
        LocalDate parsed = DateTimeUtil.parseHtmlDate(trimmed);
        if (parsed == null) {
            return new DateParseOutcome(fallback, fallback.toString(), null);
        }
        return new DateParseOutcome(parsed, parsed.toString(), null);
    }

    /** Xuất file CSV kèm UTF-8 BOM để mở đúng tiếng Việt có dấu trên Excel. */
    private void writeCsvResponse(
            HttpServletResponse response,
            LocalDate fromDate,
            LocalDate toDate,
            AdminDashboardView view
    ) throws IOException {
        String fileName = SystemReportService.buildCsvFileName(fromDate, toDate);
        String csvContent = systemReportService.buildCsv(view);

        response.setContentType("text/csv; charset=UTF-8");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");

        try (OutputStream out = response.getOutputStream()) {
            out.write(UTF8_BOM);
            out.write(csvContent.getBytes(StandardCharsets.UTF_8));
            out.flush();
        }
    }

    private static final class DateParseOutcome {
        private final LocalDate date;
        private final String inputValue;
        private final String error;

        private DateParseOutcome(LocalDate date, String inputValue, String error) {
            this.date = date;
            this.inputValue = inputValue;
            this.error = error;
        }
    }
}
