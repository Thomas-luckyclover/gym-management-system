package com.gms.servlet.manager;

import com.gms.constant.Messages;
import com.gms.constant.Role;
import com.gms.constant.SessionKey;
import com.gms.dto.AccountSettingsView;
import com.gms.dto.ReportView;
import com.gms.dto.ServiceResult;
import com.gms.service.AccountSettingsService;
import com.gms.service.ReportService;
import com.gms.util.DateTimeUtil;
import com.gms.util.RequestUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

/**
 * Báo cáo vận hành & doanh thu (#66, UC-46, SRS 12.1.1). Chỉ Manager (ManagerAreaFilter đã chặn).
 *
 * GET /manager/dashboard?from=yyyy-MM-dd&to=yyyy-MM-dd          → hiện báo cáo
 * GET /manager/dashboard?action=export&from=..&to=..            → tải file CSV của báo cáo đó
 *
 * Báo cáo là "ảnh chụp" lúc xem: ReportView vừa hiện được giữ trong session để nút Xuất CSV xuất đúng
 * số đang trên màn hình (không truy vấn lại). Nếu session không còn ảnh chụp khớp khoảng ngày thì dựng lại.
 */
@WebServlet("/manager/dashboard")
public class ManagerDashboardServlet extends HttpServlet {

    private static final String ACTION_EXPORT = "export";
    private static final String SESSION_SNAPSHOT = "managerReportSnapshot";

    private final ReportService reportService = new ReportService();
    private final AccountSettingsService accountSettingsService = new AccountSettingsService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        // B1: đọc khoảng ngày; mặc định = từ ngày 1 của tháng đến hôm nay (giờ Việt Nam)
        LocalDate today = DateTimeUtil.today();
        LocalDate from = DateTimeUtil.parseHtmlDate(request.getParameter("from"));
        LocalDate to = DateTimeUtil.parseHtmlDate(request.getParameter("to"));
        if (from == null) {
            from = today.withDayOfMonth(1);
        }
        if (to == null) {
            to = today;
        }

        // B2: xuất CSV hay hiện trang
        String action = RequestUtil.getTrimmedParam(request, "action");
        if (ACTION_EXPORT.equals(action)) {
            handleExport(request, response, from, to);
            return;
        }
        renderPage(request, response, from, to);
    }

    /** Hiện trang báo cáo. From lớn hơn To thì không truy vấn, JSP hiện trạng thái lỗi (MSG16). */
    private void renderPage(HttpServletRequest request, HttpServletResponse response,
                            LocalDate from, LocalDate to) throws ServletException, IOException {

        HttpSession session = request.getSession(false);

        // B1: tên + ảnh người dùng cho topbar (giống trang Account Settings)
        Integer accountId = (Integer) session.getAttribute(SessionKey.ACCOUNT_ID);
        ServiceResult<AccountSettingsView> me =
                accountSettingsService.getAccountSettings(accountId, Role.MANAGER);
        if (me.isSuccess()) {
            request.setAttribute("accountSettings", me.getData());
            request.setAttribute("currentRole", Role.MANAGER);
        }

        // B2: dựng báo cáo (bỏ qua nếu khoảng ngày sai)
        boolean isInvalidRange = from.isAfter(to);
        if (!isInvalidRange) {
            ServiceResult<ReportView> result = reportService.getReport(from, to);
            if (result.isSuccess()) {
                ReportView report = result.getData();
                request.setAttribute("report", report);
                session.setAttribute(SESSION_SNAPSHOT, report);

                // Toast MSG32 hiện phía trình duyệt khi bấm Xuất (file tải về không làm tải lại trang)
                String fileName = ReportService.buildFileName(from, to);
                request.setAttribute("exportedMessage", String.format(Messages.MSG32_EXPORTED, fileName));
            } else {
                request.setAttribute("pageError", result.getMessage());
            }
        }

        // B3: dữ liệu chung cho JSP
        request.setAttribute("from", from);
        request.setAttribute("to", to);
        request.setAttribute("isInvalidRange", isInvalidRange);
        request.setAttribute("activeNav", "report");
        request.setAttribute("themeClass", "theme-manager");
        RequestUtil.forward(request, response, "manager/dashboard");
    }

    /** Trả file CSV: UTF-8 có BOM để Excel đọc đúng tiếng Việt. */
    private void handleExport(HttpServletRequest request, HttpServletResponse response,
                              LocalDate from, LocalDate to) throws IOException {

        // B1: khoảng ngày sai → không xuất, quay về trang
        if (from.isAfter(to)) {
            RequestUtil.redirect(request, response, "/manager/dashboard");
            return;
        }

        // B2: ưu tiên ảnh chụp đang hiển thị; không khớp thì dựng lại
        HttpSession session = request.getSession(false);
        ReportView report = null;
        Object snapshot = session.getAttribute(SESSION_SNAPSHOT);
        if (snapshot instanceof ReportView) {
            ReportView cached = (ReportView) snapshot;
            if (cached.getFrom().equals(from) && cached.getTo().equals(to)) {
                report = cached;
            }
        }
        if (report == null) {
            ServiceResult<ReportView> result = reportService.getReport(from, to);
            if (!result.isSuccess()) {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                return;
            }
            report = result.getData();
        }

        // B3: ghi file
        String fileName = ReportService.buildFileName(from, to);
        byte[] body = reportService.buildCsv(report).getBytes(StandardCharsets.UTF_8);
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
        response.setContentLength(body.length + 3);
        try (OutputStream out = response.getOutputStream()) {
            out.write(new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});   // BOM UTF-8
            out.write(body);
        }
    }
}