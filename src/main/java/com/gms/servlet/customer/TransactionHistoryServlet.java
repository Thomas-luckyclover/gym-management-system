package com.gms.servlet.customer;

import com.gms.constant.SessionKey;
import com.gms.dto.ServiceResult;
import com.gms.dto.TransactionHistoryItem;
import com.gms.service.NotificationService;
import com.gms.service.TransactionHistoryService;
import com.gms.util.RequestUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.List;

@WebServlet("/customer/transactions")
public class TransactionHistoryServlet extends HttpServlet {

    private final TransactionHistoryService historyService =
            new TransactionHistoryService();

    private final NotificationService notificationService =
            new NotificationService();

    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response)
            throws ServletException, IOException {

        response.setHeader("Cache-Control", "no-store");

        HttpSession session = request.getSession(false);
        Object account = session == null
                ? null : session.getAttribute(SessionKey.ACCOUNT_ID);

        if (!(account instanceof Integer)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        int accountId = (Integer) account;

        // Tự động đánh dấu đã đọc các thông báo thanh toán, giao dịch khi khách vào trang
        notificationService.markTopicNotificationsRead(accountId, "Thanh toán", "Mua gói", "đơn hàng");
        int page = parsePage(request.getParameter("page"));

        String status = request.getParameter("status");
        status = status == null ? "" : status.trim();

        ServiceResult<List<TransactionHistoryItem>> result =
                historyService.getHistory(accountId, page, status);

        if (!result.isSuccess()) {
            request.setAttribute("historyError", result.getMessage());
        } else {
            List<TransactionHistoryItem> rows = result.getData();
            int pageSize = TransactionHistoryService.PAGE_SIZE;

            request.setAttribute("hasNext", rows.size() > pageSize);
            request.setAttribute(
                    "historyItems",
                    rows.subList(0, Math.min(rows.size(), pageSize)));
        }

        request.setAttribute("currentPage", page);
        request.setAttribute("selectedStatus", status);
        request.setAttribute("activeSidebar", "transactions");
        request.setAttribute("activeNav", "transactions");
        request.setAttribute(
                "themeClass", "theme-pt customer-dashboard-page");

        RequestUtil.forward(
                request, response, "customer/transaction-history");
    }

    private int parsePage(String value) {
        if (value == null || value.isBlank()) {
            return 1;
        }

        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}