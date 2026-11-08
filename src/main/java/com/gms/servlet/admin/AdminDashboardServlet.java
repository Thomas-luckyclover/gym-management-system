package com.gms.servlet.admin;

import com.gms.dto.ServiceResult;
import com.gms.service.SystemReportService;
import com.gms.util.DateTimeUtil;
import com.gms.util.MoneyUtil;
import com.gms.util.RequestUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Servlet Dashboard Admin (URL /admin/dashboard) — báo cáo vận hành, doanh thu, hoa hồng PT và audit.
 *
 * <p>Theo khoảng ngày được chọn; có thể xuất CSV cùng bộ lọc đang xem.</p>
 */
@WebServlet("/admin/dashboard")
public class AdminDashboardServlet extends HttpServlet {

    private static final String VIEW_NAME = "admin/dashboard";
    private static final String ACTION_EXPORT_CSV = "exportCsv";

    private final SystemReportService systemReportService = new SystemReportService();

    /**
     * GET /admin/dashboard — hiện dashboard theo khoảng ngày; action=exportCsv thì xuất CSV.
     */
    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws ServletException, IOException {
        // B1: đọc khoảng ngày; lần đầu vào trang dùng ngày 1 tháng hiện tại tới hôm nay.
        String fromValue = RequestUtil.getTrimmedParam(request, "fromDate");
        String toValue = RequestUtil.getTrimmedParam(request, "toDate");
        String action = RequestUtil.getTrimmedParam(request, "action");

        LocalDate fromDate = DateTimeUtil.parseHtmlDate(fromValue);
        LocalDate toDate = DateTimeUtil.parseHtmlDate(toValue);

        boolean isFirstLoad = fromValue == null && toValue == null;
        if (isFirstLoad) {
            toDate = DateTimeUtil.today();
            fromDate = toDate.withDayOfMonth(1);
        }

        // B2: gọi Service tổng hợp báo cáo và validate lại khoảng ngày ở server.
        ServiceResult<Map<String, Object>> result = systemReportService.getAdminDashboard(
                fromDate,
                toDate
        );

        // B3: nếu người dùng yêu cầu CSV và dữ liệu hợp lệ thì xuất đúng bộ lọc hiện tại.
        if (ACTION_EXPORT_CSV.equals(action) && result.isSuccess()) {
            writeCsv(response, result.getData());
            return;
        }

        // B4: chuẩn bị dữ liệu cho View.
        request.setAttribute("activeSidebar", "dashboard");
        request.setAttribute("fromDate", fromDate);
        request.setAttribute("toDate", toDate);

        if (result.isSuccess()) {
            request.setAttribute("dashboard", result.getData());
        } else {
            request.setAttribute("pageError", result.getMessage());
        }

        RequestUtil.forward(request, response, VIEW_NAME);
    }

    private void writeCsv(
            HttpServletResponse response,
            Map<String, Object> dashboard
    ) throws IOException {
        LocalDate fromDate = (LocalDate) dashboard.get("fromDate");
        LocalDate toDate = (LocalDate) dashboard.get("toDate");

        response.setCharacterEncoding("UTF-8");
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader(
                "Content-Disposition",
                "attachment; filename=admin-report-" + fromDate + "-to-" + toDate + ".csv"
        );

        try (PrintWriter writer = response.getWriter()) {
            // BOM giúp Excel nhận đúng UTF-8 khi mở CSV tiếng Việt.
            writer.write('\ufeff');
            writer.println("Báo cáo vận hành hệ thống Admin");
            writer.println("Từ ngày," + escapeCsv(DateTimeUtil.formatDate(fromDate)));
            writer.println("Đến ngày," + escapeCsv(DateTimeUtil.formatDate(toDate)));
            writer.println();

            writer.println("TỔNG QUAN");
            writer.println("Chỉ số,Giá trị");
            writer.println("Tổng doanh thu," + escapeCsv(MoneyUtil.formatMoney((Long) dashboard.get("totalRevenue"))));
            writer.println("Lượt check-in," + dashboard.get("checkInCount"));

            Map<String, Object> dayPassSummary = castMap(dashboard.get("dayPassSummary"));
            writer.println("Lượt vé ngày," + dayPassSummary.get("count"));
            writer.println("Doanh thu vé ngày," + escapeCsv(MoneyUtil.formatMoney((Long) dayPassSummary.get("revenue"))));
            writer.println();

            writer.println("DOANH THU THEO GÓI");
            writer.println("Tên gói,Số giao dịch,Doanh thu,Trong đó VAT");
            for (Map<String, Object> row : castList(dashboard.get("revenueByPackage"))) {
                writer.println(
                        escapeCsv((String) row.get("packageName")) + ","
                                + row.get("transactionCount") + ","
                                + escapeCsv(MoneyUtil.formatMoney((Long) row.get("revenue"))) + ","
                                + escapeCsv(MoneyUtil.formatMoney((Long) row.get("vatAmount")))
                );
            }
            writer.println();

            writer.println("HOA HỒNG PT");
            writer.println("Mã PT,Họ tên,Số buổi được tính,Tổng hoa hồng");
            for (Map<String, Object> row : castList(dashboard.get("ptCommissions"))) {
                writer.println(
                        escapeCsv((String) row.get("employeeCode")) + ","
                                + escapeCsv((String) row.get("fullName")) + ","
                                + row.get("sessionCount") + ","
                                + escapeCsv(MoneyUtil.formatMoney((Long) row.get("commissionAmount")))
                );
            }
            writer.println();

            writer.println("AUDIT & BẢO MẬT");
            writer.println("Hoạt động đăng nhập,Số lần");
            Map<String, Integer> loginActions = castIntegerMap(dashboard.get("loginAuditActions"));
            writer.println("SignIn," + loginActions.get("SignIn"));
            writer.println("SignInFailed," + loginActions.get("SignInFailed"));
            writer.println("AccountLocked," + loginActions.get("AccountLocked"));
            writer.println("Tài khoản bị khóa," + dashboard.get("lockedAccountCount"));
            writer.println("Thay đổi tài khoản," + dashboard.get("accountAuditChangeCount"));
            writer.println();

            writer.println("THAO TÁC NHẠY CẢM THEO LOẠI");
            writer.println("Action,Số lần");
            for (Map.Entry<String, Integer> entry : castIntegerMap(dashboard.get("sensitiveAuditLogsByAction")).entrySet()) {
                writer.println(escapeCsv(entry.getKey()) + "," + entry.getValue());
            }
            writer.println();

            writer.println("THAO TÁC NHẠY CẢM THEO NGƯỜI THỰC HIỆN");
            writer.println("AccountId,Người thực hiện,Vai trò,Số lần");
            for (Map<String, Object> row : castList(dashboard.get("sensitiveAuditActors"))) {
                Object accountId = row.get("accountId");
                writer.println(
                        escapeCsv(accountId == null ? "System" : String.valueOf(accountId)) + ","
                                + escapeCsv((String) row.get("actorName")) + ","
                                + escapeCsv((String) row.get("actorRole")) + ","
                                + row.get("total")
                );
            }
        }
    }

    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }

        String escaped = value.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Integer> castIntegerMap(Object value) {
        return (Map<String, Integer>) value;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castList(Object value) {
        return (List<Map<String, Object>>) value;
    }
}
