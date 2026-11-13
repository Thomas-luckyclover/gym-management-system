package com.gms.servlet.admin;

import com.gms.constant.Messages;
import com.gms.dto.AdminDashboardView;
import com.gms.dto.AdminDashboardView.PackageRevenueRow;
import com.gms.dto.ServiceResult;
import com.gms.service.SystemReportService;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AdminDashboardServletTest {

    private static final LocalDate FIXED_TODAY = LocalDate.of(2026, 11, 13);

    @Test
    @DisplayName("Mặc định không có param -> Tải báo cáo từ đầu tháng tới hôm nay")
    void doGet_NoParams_LoadsMonthToDate() throws Exception {
        SystemReportService service = mock(SystemReportService.class);
        AdminDashboardView view = emptyView(LocalDate.of(2026, 11, 1), FIXED_TODAY);
        when(service.getAdminDashboard(LocalDate.of(2026, 11, 1), FIXED_TODAY))
                .thenReturn(ServiceResult.ok(view));

        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        RequestDispatcher dispatcher = mock(RequestDispatcher.class);
        when(request.getRequestDispatcher("/WEB-INF/jsp/admin/dashboard.jsp")).thenReturn(dispatcher);

        TestableAdminDashboardServlet servlet = new TestableAdminDashboardServlet(service, FIXED_TODAY);
        servlet.doGet(request, response);

        verify(service).getAdminDashboard(LocalDate.of(2026, 11, 1), FIXED_TODAY);
        verify(request).setAttribute("fromDateValue", "2026-11-01");
        verify(request).setAttribute("toDateValue", "2026-11-13");
        verify(request).setAttribute("isInvalidRange", false);
        verify(request).setAttribute("emptyInRange", true);
        verify(request).setAttribute("report", view);
        verify(dispatcher).forward(request, response);
    }

    @Test
    @DisplayName("URL có giá trị ngày sai định dạng -> Bỏ qua giá trị lỗi, rơi về mặc định (GBR-20)")
    void doGet_MalformedUrlDates_FallsBackToDefaultRange() throws Exception {
        SystemReportService service = mock(SystemReportService.class);
        AdminDashboardView view = emptyView(LocalDate.of(2026, 11, 1), FIXED_TODAY);
        when(service.getAdminDashboard(LocalDate.of(2026, 11, 1), FIXED_TODAY))
                .thenReturn(ServiceResult.ok(view));

        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        RequestDispatcher dispatcher = mock(RequestDispatcher.class);
        when(request.getParameter("fromDate")).thenReturn("not-a-date");
        when(request.getParameter("toDate")).thenReturn("2026/99/99");
        when(request.getRequestDispatcher("/WEB-INF/jsp/admin/dashboard.jsp")).thenReturn(dispatcher);

        TestableAdminDashboardServlet servlet = new TestableAdminDashboardServlet(service, FIXED_TODAY);
        servlet.doGet(request, response);

        verify(service).getAdminDashboard(LocalDate.of(2026, 11, 1), FIXED_TODAY);
        verify(request).setAttribute("fromDateValue", "2026-11-01");
        verify(request).setAttribute("toDateValue", "2026-11-13");
        verify(request).setAttribute("isInvalidRange", false);
        verify(dispatcher).forward(request, response);
    }

    @Test
    @DisplayName("Gửi param rỗng -> Báo MSG09 và không gọi Service")
    void doGet_EmptyParam_SetsMsg09AndSkipsService() throws Exception {
        SystemReportService service = mock(SystemReportService.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        RequestDispatcher dispatcher = mock(RequestDispatcher.class);
        when(request.getParameter("fromDate")).thenReturn("");
        when(request.getParameter("toDate")).thenReturn("2026-11-13");
        when(request.getRequestDispatcher("/WEB-INF/jsp/admin/dashboard.jsp")).thenReturn(dispatcher);

        TestableAdminDashboardServlet servlet = new TestableAdminDashboardServlet(service, FIXED_TODAY);
        servlet.doGet(request, response);

        verifyNoInteractions(service);
        verify(request).setAttribute("fromError", String.format(Messages.MSG09_REQUIRED, "Từ ngày"));
        verify(request).setAttribute("isInvalidRange", true);
        verify(dispatcher).forward(request, response);
    }

    @Test
    @DisplayName("Từ ngày lớn hơn Đến ngày -> Báo MSG16 và không gọi Service")
    void doGet_ReversedRange_SetsMsg16AndSkipsService() throws Exception {
        SystemReportService service = mock(SystemReportService.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        RequestDispatcher dispatcher = mock(RequestDispatcher.class);
        when(request.getParameter("fromDate")).thenReturn("2026-11-20");
        when(request.getParameter("toDate")).thenReturn("2026-11-10");
        when(request.getRequestDispatcher("/WEB-INF/jsp/admin/dashboard.jsp")).thenReturn(dispatcher);

        TestableAdminDashboardServlet servlet = new TestableAdminDashboardServlet(service, FIXED_TODAY);
        servlet.doGet(request, response);

        verifyNoInteractions(service);
        verify(request).setAttribute("toError", Messages.MSG16_DATE_RANGE_REVERSED);
        verify(request).setAttribute("isInvalidRange", true);
        verify(dispatcher).forward(request, response);
    }

    @Test
    @DisplayName("export=csv với khoảng ngày hợp lệ -> Ghi UTF-8 BOM và nội dung CSV có đơn vị 'đ'")
    void doGet_ExportCsv_WritesUtf8BomAndMoneyContent() throws Exception {
        com.gms.dao.ReportDAO reportDAO = mock(com.gms.dao.ReportDAO.class);
        SystemReportService realService = new SystemReportService(reportDAO) {
            @Override
            public ServiceResult<AdminDashboardView> getAdminDashboard(LocalDate from, LocalDate to) {
                return ServiceResult.ok(new AdminDashboardView(
                        from, to, 2_190_000L, 1, 0, 0L,
                        List.of(new PackageRevenueRow("Gói 3 Tháng", 1, 2_190_000L, 199_091L)),
                        List.of(), 0, 0, 0, 0, 0, List.of(), List.of()
                ));
            }
        };

        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        when(response.getOutputStream()).thenReturn(new InMemoryServletOutputStream(captured));
        when(request.getParameter("fromDate")).thenReturn("2026-11-01");
        when(request.getParameter("toDate")).thenReturn("2026-11-13");
        when(request.getParameter("export")).thenReturn("csv");

        TestableAdminDashboardServlet servlet = new TestableAdminDashboardServlet(realService, FIXED_TODAY);
        servlet.doGet(request, response);

        verify(response).setContentType("text/csv; charset=UTF-8");
        verify(response).setHeader(
                eq("Content-Disposition"),
                eq("attachment; filename=\"bao-cao-admin_01-11-2026_den_13-11-2026.csv\"")
        );

        byte[] bytes = captured.toByteArray();
        assertTrue(bytes.length > 3);
        assertArrayEquals(new byte[] { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF },
                new byte[] { bytes[0], bytes[1], bytes[2] });
        String csvBody = new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        assertTrue(csvBody.contains("Tổng doanh thu,2.190.000đ"));
        assertTrue(csvBody.contains("Gói 3 Tháng,1,2.190.000đ,199.091đ"));
    }

    @Test
    @DisplayName("export=csv với khoảng ngày đảo ngược -> Trả 400 và chặn tải CSV")
    void doGet_ExportCsvWithReversedRange_Returns400WithoutCsv() throws Exception {
        SystemReportService service = mock(SystemReportService.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        RequestDispatcher dispatcher = mock(RequestDispatcher.class);
        when(request.getParameter("fromDate")).thenReturn("2026-11-20");
        when(request.getParameter("toDate")).thenReturn("2026-11-10");
        when(request.getParameter("export")).thenReturn("csv");
        when(request.getRequestDispatcher("/WEB-INF/jsp/admin/dashboard.jsp")).thenReturn(dispatcher);

        TestableAdminDashboardServlet servlet = new TestableAdminDashboardServlet(service, FIXED_TODAY);
        servlet.doGet(request, response);

        verifyNoInteractions(service);
        verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
        verify(dispatcher).forward(request, response);
    }

    private static AdminDashboardView emptyView(LocalDate from, LocalDate to) {
        return new AdminDashboardView(
                from, to, 0L, 0, 0, 0L,
                List.of(), List.of(), 0, 0, 0, 0, 0, List.of(), List.of()
        );
    }

    private static final class TestableAdminDashboardServlet extends AdminDashboardServlet {
        private final LocalDate fixedToday;

        private TestableAdminDashboardServlet(SystemReportService service, LocalDate fixedToday) {
            super(service);
            this.fixedToday = fixedToday;
        }

        @Override
        protected LocalDate currentDate() {
            return fixedToday;
        }
    }

    private static final class InMemoryServletOutputStream extends ServletOutputStream {
        private final ByteArrayOutputStream delegate;

        private InMemoryServletOutputStream(ByteArrayOutputStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setWriteListener(WriteListener writeListener) {
        }

        @Override
        public void write(int b) {
            delegate.write(b);
        }
    }
}

