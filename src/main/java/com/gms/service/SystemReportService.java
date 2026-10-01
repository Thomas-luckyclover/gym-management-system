package com.gms.service;

import com.gms.constant.Messages;
import com.gms.dao.ReportDAO;
import com.gms.dto.ServiceResult;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Nghiệp vụ tổng hợp báo cáo vận hành, doanh thu và audit metrics dành cho Admin (UC-47).
 */
public class SystemReportService {

    private final ReportDAO reportDAO = new ReportDAO();

    /**
     * Tải toàn bộ dữ liệu Admin Dashboard trong khoảng ngày được chọn.
     *
     * @param fromDate ngày bắt đầu, tính cả ngày này
     * @param toDate ngày kết thúc, tính cả ngày này
     * @return dữ liệu dashboard hoặc lỗi thân thiện
     */
    public ServiceResult<Map<String, Object>> getAdminDashboard(
            LocalDate fromDate,
            LocalDate toDate
    ) {
        // B1: kiểm tra khoảng ngày ở tầng Service theo GBR-13 và D7.
        if (fromDate == null || toDate == null) {
            return ServiceResult.fail(String.format(Messages.MSG09_REQUIRED, "Khoảng ngày"));
        }

        if (fromDate.isAfter(toDate)) {
            return ServiceResult.fail(Messages.MSG16_DATE_RANGE_REVERSED);
        }

        try {
            // B2: lấy phần vận hành và doanh thu giống báo cáo Manager.
            int checkInCount = reportDAO.countCheckIns(fromDate, toDate);
            long totalRevenue = reportDAO.sumRevenue(fromDate, toDate);
            List<Map<String, Object>> revenueByPackage = reportDAO.findRevenueByPackage(
                    fromDate,
                    toDate
            );
            Map<String, Object> dayPassSummary = reportDAO.getDayPassSummary(fromDate, toDate);
            List<Map<String, Object>> ptCommissions = reportDAO.findPtCommissionSummary(
                    fromDate,
                    toDate
            );

            // B3: lấy audit metrics chỉ Admin được xem.
            Map<String, Integer> loginAuditActions = reportDAO.countLoginAuditActions(
                    fromDate,
                    toDate
            );
            int lockedAccountCount = reportDAO.countLockedAccounts(fromDate, toDate);
            int accountAuditChangeCount = reportDAO.countAccountAuditChanges(fromDate, toDate);
            Map<String, Integer> sensitiveAuditLogsByAction = reportDAO.countSensitiveAuditLogsByAction(
                    fromDate,
                    toDate
            );
            List<Map<String, Object>> sensitiveAuditActors = reportDAO.findSensitiveAuditActors(fromDate, toDate);

            // B4: đóng gói dữ liệu cho Servlet/JSP. Không tính thêm chỉ số ngoài rule.
            Map<String, Object> dashboard = new LinkedHashMap<>();
            dashboard.put("fromDate", fromDate);
            dashboard.put("toDate", toDate);
            dashboard.put("checkInCount", checkInCount);
            dashboard.put("totalRevenue", totalRevenue);
            dashboard.put("revenueByPackage", revenueByPackage);
            dashboard.put("dayPassSummary", dayPassSummary);
            dashboard.put("ptCommissions", ptCommissions);
            dashboard.put("loginAuditActions", loginAuditActions);
            dashboard.put("lockedAccountCount", lockedAccountCount);
            dashboard.put("accountAuditChangeCount", accountAuditChangeCount);
            dashboard.put("sensitiveAuditLogsByAction", sensitiveAuditLogsByAction);
            dashboard.put("sensitiveAuditActors", sensitiveAuditActors);

            int dayPassCount = (Integer) dayPassSummary.get("count");
            boolean hasOperationalData = checkInCount > 0
                    || totalRevenue > 0
                    || dayPassCount > 0
                    || !ptCommissions.isEmpty();
            boolean hasLoginAuditData = loginAuditActions.values().stream().anyMatch(value -> value > 0);
            boolean hasAuditData = hasLoginAuditData
                    || lockedAccountCount > 0
                    || accountAuditChangeCount > 0
                    || !sensitiveAuditLogsByAction.isEmpty();
            dashboard.put("hasOperationalData", hasOperationalData);
            dashboard.put("hasAuditData", hasAuditData);
            dashboard.put("hasAnyData", hasOperationalData || hasAuditData);

            return ServiceResult.ok(dashboard);
        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }
}
