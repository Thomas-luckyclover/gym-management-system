package com.gms.service;

import com.gms.constant.Messages;
import com.gms.dao.ReportDAO;
import com.gms.dto.AdminDashboardView;
import com.gms.dto.AdminDashboardView.PackageRevenueRow;
import com.gms.dto.AdminDashboardView.PtCommissionRow;
import com.gms.dto.AdminDashboardView.SensitiveActionRow;
import com.gms.dto.AdminDashboardView.SensitiveActorRow;
import com.gms.dto.ServiceResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SystemReportServiceTest {

    @Test
    @DisplayName("Thiếu Từ ngày -> Trả lỗi MSG09 và không gọi DAO")
    void getAdminDashboard_MissingFromDate_ReturnsMsg09() {
        ReportDAO reportDAO = mock(ReportDAO.class);
        SystemReportService service = new SystemReportService(reportDAO);

        ServiceResult<AdminDashboardView> result = service.getAdminDashboard(null, LocalDate.of(2026, 11, 13));

        assertFalse(result.isSuccess());
        assertEquals(String.format(Messages.MSG09_REQUIRED, "Từ ngày"), result.getMessage());
        verifyNoInteractions(reportDAO);
    }

    @Test
    @DisplayName("Thiếu Đến ngày -> Trả lỗi MSG09 và không gọi DAO")
    void getAdminDashboard_MissingToDate_ReturnsMsg09() {
        ReportDAO reportDAO = mock(ReportDAO.class);
        SystemReportService service = new SystemReportService(reportDAO);

        ServiceResult<AdminDashboardView> result = service.getAdminDashboard(LocalDate.of(2026, 11, 1), null);

        assertFalse(result.isSuccess());
        assertEquals(String.format(Messages.MSG09_REQUIRED, "Đến ngày"), result.getMessage());
        verifyNoInteractions(reportDAO);
    }

    @Test
    @DisplayName("Từ ngày sau Đến ngày -> Trả lỗi MSG16 và không gọi DAO")
    void getAdminDashboard_ReversedRange_ReturnsMsg16() {
        ReportDAO reportDAO = mock(ReportDAO.class);
        SystemReportService service = new SystemReportService(reportDAO);

        ServiceResult<AdminDashboardView> result = service.getAdminDashboard(
                LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 10));

        assertFalse(result.isSuccess());
        assertEquals(Messages.MSG16_DATE_RANGE_REVERSED, result.getMessage());
        verifyNoInteractions(reportDAO);
    }

    @Test
    @DisplayName("Lỗi CSDL -> Trả lỗi MSG04 hệ thống bận")
    void getAdminDashboard_SqlException_ReturnsMsg04() throws SQLException {
        ReportDAO reportDAO = mock(ReportDAO.class);
        LocalDate from = LocalDate.of(2026, 11, 1);
        LocalDate to = LocalDate.of(2026, 11, 13);
        when(reportDAO.findAdminDashboard(from, to)).thenThrow(new SQLException("DB error"));
        SystemReportService service = new SystemReportService(reportDAO);

        ServiceResult<AdminDashboardView> result = service.getAdminDashboard(from, to);

        assertFalse(result.isSuccess());
        assertEquals(Messages.MSG04_SYSTEM_BUSY, result.getMessage());
    }

    @Test
    @DisplayName("Khoảng ngày hợp lệ -> Trả về AdminDashboardView từ DAO")
    void getAdminDashboard_ValidRange_ReturnsDashboardView() throws SQLException {
        ReportDAO reportDAO = mock(ReportDAO.class);
        LocalDate from = LocalDate.of(2026, 11, 1);
        LocalDate to = LocalDate.of(2026, 11, 13);
        AdminDashboardView expected = sampleView(from, to);
        when(reportDAO.findAdminDashboard(from, to)).thenReturn(expected);
        SystemReportService service = new SystemReportService(reportDAO);

        ServiceResult<AdminDashboardView> result = service.getAdminDashboard(from, to);

        assertTrue(result.isSuccess());
        assertSame(expected, result.getData());
    }

    @Test
    @DisplayName("Tên file CSV đúng định dạng bao-cao-admin_dd-MM-yyyy_den_dd-MM-yyyy.csv")
    void buildCsvFileName_FormatsDatesCorrectly() {
        String fileName = SystemReportService.buildCsvFileName(
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 13));

        assertEquals("bao-cao-admin_01-11-2026_den_13-11-2026.csv", fileName);
    }

    @Test
    @DisplayName("buildCsv giữ định dạng tiền có 'đ' và thoát CSV Formula Injection")
    void buildCsv_KeepsMoneySuffixAndEscapesFormulas() {
        SystemReportService service = new SystemReportService(mock(ReportDAO.class));
        AdminDashboardView view = new AdminDashboardView(
                LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 13),
                55_255_000L,
                3,
                2,
                220_000L,
                List.of(new PackageRevenueRow("=SUM(A1:A2)", 4, 35_200_000L, 3_200_000L)),
                List.of(new PtCommissionRow("NV-0005", "Đinh Công Sơn", 25, 6_596_820L)),
                10,
                2,
                1,
                1,
                4,
                List.of(new SensitiveActionRow("Duyệt yêu cầu bảo trì", 15)),
                List.of(new SensitiveActorRow("Trần Quốc Việt", "Quản lý", 15))
        );

        String csv = service.buildCsv(view);

        assertTrue(csv.contains("Tổng doanh thu,55.255.000đ"));
        assertTrue(csv.contains("Doanh thu vé ngày,220.000đ"));
        assertTrue(csv.contains("'=SUM(A1:A2),4,35.200.000đ,3.200.000đ"));
        assertTrue(csv.contains("NV-0005,Đinh Công Sơn,25,6.596.820đ"));
        assertTrue(csv.contains("Trần Quốc Việt,Quản lý,15"));
    }

    private static AdminDashboardView sampleView(LocalDate from, LocalDate to) {
        return new AdminDashboardView(
                from, to, 1_000_000L, 5, 1, 110_000L,
                List.of(), List.of(), 2, 0, 0, 0, 0, List.of(), List.of()
        );
    }
}

