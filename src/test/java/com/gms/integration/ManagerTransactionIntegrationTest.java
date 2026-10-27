package com.gms.integration;

import com.gms.dao.ShiftDAO;
import com.gms.dao.TransactionDAO;
import com.gms.dto.ClosedShiftHistoryItem;
import com.gms.dto.ManagerOrderDetailView;
import com.gms.dto.ManagerTransactionItem;
import com.gms.dto.PageResult;
import com.gms.dto.ServiceResult;
import com.gms.service.ManagerTransactionService;
import com.gms.util.DBContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration Test kiểm thử liên kết dữ liệu giữa Màn hình Giao dịch toàn hệ thống (Manager - UC-26)
 * với các màn hình và phân hệ khác:
 * 1. Phân hệ Ca làm việc Lễ tân (Receptionist Shifts - UC-25).
 * 2. Phân hệ Bán hàng & Đơn hàng (Sales Orders & Counter Transactions - UC-20, UC-21).
 * 3. Phân hệ Cổng thanh toán & Lần thanh toán (Payment Attempts & OnlineGateway - UC-PAY, API-02).
 * 4. Quy trình Hoàn tiền Offline và Nhật ký kiểm toán (Offline Refund & AuditLog - BR-47, CBR-72).
 * 5. Xuất báo cáo Excel đối soát (.xlsx POI Export).
 */
class ManagerTransactionIntegrationTest {

    private static boolean dbAvailable = false;
    private final TransactionDAO transactionDAO = new TransactionDAO();
    private final ShiftDAO shiftDAO = new ShiftDAO();
    private final ManagerTransactionService managerTransactionService = new ManagerTransactionService();

    @BeforeAll
    static void checkDatabaseConnection() {
        try (Connection conn = DBContext.getConnection()) {
            dbAvailable = (conn != null && !conn.isClosed());
        } catch (Exception e) {
            System.out.println("[WARN] Database not reachable for integration test: " + e.getMessage());
            dbAvailable = false;
        }
    }

    @Test
    @DisplayName("IT-01: Kiểm tra liên kết dữ liệu với Ca làm việc Lễ tân (Receptionist Shifts)")
    void testIntegrationWithReceptionistShifts() throws SQLException {
        if (!dbAvailable) {
            System.out.println("Skipping test: DB not available");
            return;
        }

        int totalClosedShifts = shiftDAO.countClosedShiftsHistory(null, null);
        assertTrue(totalClosedShifts >= 0, "Tổng số ca đã đóng phải >= 0");

        ServiceResult<PageResult<ClosedShiftHistoryItem>> shiftResult =
                managerTransactionService.getClosedShiftsPage(null, null, 1, 10);
        assertTrue(shiftResult.isSuccess(), "Lấy danh sách ca đã đóng phải thành công");
        PageResult<ClosedShiftHistoryItem> shiftPage = shiftResult.getData();
        assertNotNull(shiftPage, "Kết quả phân trang ca làm việc không được null");
        assertNotNull(shiftPage.getItems(), "Danh sách ca làm việc không được null");

        // Nếu có ca đã đóng, kiểm tra tính toàn vẹn của các trường đối soát (BR-46, DBR-55)
        for (ClosedShiftHistoryItem item : shiftPage.getItems()) {
            assertNotNull(item.getWorkDate(), "Ngày làm việc không được null");
            assertNotNull(item.getShiftType(), "Loại ca không được null");
            assertNotNull(item.getOpenedByName(), "Người mở ca không được null");
            assertTrue(item.getCountedCash() >= 0, "Tiền thực kiểm phải >= 0");
            assertTrue(item.getExpectedCash() >= 0, "Tiền kỳ vọng phải >= 0");
            assertEquals(item.getCountedCash() - item.getExpectedCash(), item.getVariance(),
                    "Chênh lệch tiền mặt phải bằng CountedCash - ExpectedCash");
        }
    }

    @Test
    @DisplayName("IT-02: Kiểm tra liên kết dữ liệu với Đơn hàng & Giao dịch thanh toán")
    void testIntegrationWithOrdersAndTransactions() throws SQLException {
        if (!dbAvailable) {
            System.out.println("Skipping test: DB not available");
            return;
        }

        ServiceResult<PageResult<ManagerTransactionItem>> result = managerTransactionService.getTransactionPage(
                null, null, null, null, null, null, 1, 10);

        assertTrue(result.isSuccess(), "Lấy danh sách giao dịch phải thành công");
        PageResult<ManagerTransactionItem> orderPage = result.getData();
        assertNotNull(orderPage);
        assertNotNull(orderPage.getItems());

        int pendingRefunds = transactionDAO.countPendingRefunds();
        assertTrue(pendingRefunds >= 0, "Số lượng giao dịch chờ hoàn tiền phải >= 0");

        // Kiểm tra tính toàn vẹn của dữ liệu đơn hàng
        for (ManagerTransactionItem item : orderPage.getItems()) {
            assertTrue(item.getOrderId() > 0, "Mã đơn hàng phải > 0");
            assertNotNull(item.getOrderCode(), "Mã hiển thị đơn hàng không được null");
            assertNotNull(item.getChannel(), "Kênh bán hàng (Counter/Online) không được null");
            assertNotNull(item.getOrderStatus(), "Trạng thái đơn hàng không được null");
            assertTrue(item.getTotalAmount() >= 0, "Tổng tiền phải >= 0");
        }
    }

    @Test
    @DisplayName("IT-03: Kiểm tra liên kết Modal Chi tiết đơn hàng và các lần thử thanh toán (Attempts)")
    void testIntegrationWithOrderDetailAndPaymentAttempts() {
        if (!dbAvailable) {
            System.out.println("Skipping test: DB not available");
            return;
        }

        // Lấy 1 đơn hàng bất kỳ để xem chi tiết
        ServiceResult<PageResult<ManagerTransactionItem>> pageResult = managerTransactionService.getTransactionPage(
                null, null, null, null, null, null, 1, 1);

        assertTrue(pageResult.isSuccess());
        PageResult<ManagerTransactionItem> page = pageResult.getData();

        if (!page.getItems().isEmpty()) {
            int orderId = page.getItems().get(0).getOrderId();
            ServiceResult<ManagerOrderDetailView> result = managerTransactionService.getOrderDetail(orderId);

            assertTrue(result.isSuccess(), "Lấy chi tiết đơn hàng phải thành công");
            ManagerOrderDetailView detail = result.getData();
            assertNotNull(detail, "Dữ liệu chi tiết đơn hàng không được null");
            assertEquals(orderId, detail.getOrderId(), "Mã đơn hàng phải khớp");
            assertNotNull(detail.getAttempts(), "Danh sách lần thử thanh toán không được null");

            // Kiểm tra phân rã giá trị đơn hàng
            assertTrue(detail.getUnitPrice() >= 0);
            assertTrue(detail.getTotalPayable() >= 0);
        }
    }

    @Test
    @DisplayName("IT-04: Kiểm tra luồng Xuất Excel đối soát giao dịch (.xlsx)")
    void testIntegrationWithExcelExport() throws Exception {
        if (!dbAvailable) {
            System.out.println("Skipping test: DB not available");
            return;
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        managerTransactionService.exportToExcel(null, null, null, null, null, null, out);

        byte[] excelBytes = out.toByteArray();
        assertTrue(excelBytes.length > 0, "File Excel xuất ra phải có nội dung");

        // Kiểm tra magic header của file .xlsx (PK zip file: 0x50, 0x4B, 0x03, 0x04)
        assertTrue(excelBytes.length >= 4);
        assertEquals(0x50, excelBytes[0], "Header byte 0 phải là 'P'");
        assertEquals(0x4B, excelBytes[1], "Header byte 1 phải là 'K'");
    }

    @Test
    @DisplayName("IT-05: Kiểm tra xác nhận hoàn tiền offline với transaction không hợp lệ")
    void testOfflineRefundValidation() {
        // Tài khoản không tồn tại hoặc transactionId không hợp lệ
        ServiceResult<Void> result = managerTransactionService.recordRefund(999999, -1, "Test refund reason valid");
        assertFalse(result.isSuccess(), "Transaction không hợp lệ phải trả về lỗi");
    }
}
