package com.gms.service;

import com.gms.constant.Messages;
import com.gms.constant.Role;
import com.gms.dao.AccountDAO;
import com.gms.dao.EmployeeDAO;
import com.gms.dao.ShiftDAO;
import com.gms.dao.TransactionDAO;
import com.gms.dto.ClosedShiftHistoryItem;
import com.gms.dto.ManagerOrderDetailView;
import com.gms.dto.ManagerTransactionItem;
import com.gms.dto.PageResult;
import com.gms.dto.ServiceResult;
import com.gms.entity.Account;
import com.gms.entity.Employee;
import com.gms.util.DBContext;
import com.gms.util.DateTimeUtil;
import com.gms.util.ValidationUtil;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.OutputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

/**
 * Service xử lý các nghiệp vụ của màn hình Giao dịch toàn hệ thống cho Quản lý (Màn #41 - UC-26, SRS 7.4.2).
 *
 * <p>Quy tắc kỹ thuật & nghiệp vụ:</p>
 * <ul>
 *   <li>Tab 1: Tra cứu đơn hàng & giao dịch toàn hệ thống, lọc hoàn tiền (CBR-72), xuất Excel (.xlsx).</li>
 *   <li>Tab 2: Lịch sử các ca làm việc đã đóng của Lễ tân, đối soát tiền mặt & chênh lệch (BR-46, DBR-55).</li>
 *   <li>Xác nhận hoàn tiền ngoài hệ thống (BR-47): ghi nhận thời điểm, người duyệt và ghi chú, đồng thời ghi AuditLog.</li>
 * </ul>
 *
 * @author Le Cong Minh
 */
public class ManagerTransactionService {

    private final TransactionDAO transactionDAO;
    private final ShiftDAO shiftDAO;
    private final AccountDAO accountDAO;
    private final EmployeeDAO employeeDAO;
    private final AuditLogService auditLogService;

    public ManagerTransactionService() {
        this.transactionDAO = new TransactionDAO();
        this.shiftDAO = new ShiftDAO();
        this.accountDAO = new AccountDAO();
        this.employeeDAO = new EmployeeDAO();
        this.auditLogService = new AuditLogService();
    }

    /**
     * Constructor phục vụ unit test dependency injection.
     */
    public ManagerTransactionService(TransactionDAO transactionDAO, ShiftDAO shiftDAO,
                                    AccountDAO accountDAO, EmployeeDAO employeeDAO,
                                    AuditLogService auditLogService) {
        this.transactionDAO = transactionDAO;
        this.shiftDAO = shiftDAO;
        this.accountDAO = accountDAO;
        this.employeeDAO = employeeDAO;
        this.auditLogService = auditLogService;
    }

    /**
     * Lấy danh sách đơn hàng & giao dịch có phân trang phục vụ Tab 1 của Manager (SRS 7.4.2).
     *
     * @param search          từ khóa tìm kiếm
     * @param method          phương thức thanh toán
     * @param status          trạng thái đơn
     * @param needsRefundOnly true nếu chỉ lọc giao dịch cần hoàn tiền
     * @param fromDate        từ ngày
     * @param toDate          đến ngày
     * @param page            trang hiện tại (>= 1)
     * @param pageSize        kích thước trang
     * @return ServiceResult chứa PageResult của ManagerTransactionItem
     */
    public ServiceResult<PageResult<ManagerTransactionItem>> getTransactionPage(
            String search, String method, String status, Boolean needsRefundOnly,
            LocalDate fromDate, LocalDate toDate, int page, int pageSize) {

        // B1: Kiểm tra khoảng ngày hợp lệ (fromDate <= toDate)
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            return ServiceResult.fail(Messages.MSG169_DATE_RANGE_REVERSED);
        }

        try {
            // B2: Đếm tổng số bản ghi khớp bộ lọc
            int totalItems = transactionDAO.countManagerTransactions(
                    search, method, status, needsRefundOnly, fromDate, toDate
            );

            // B3: Tính offset và lấy danh sách bản ghi trang hiện tại
            int offset = (page - 1) * pageSize;
            List<ManagerTransactionItem> items = transactionDAO.findManagerTransactionsPage(
                    search, method, status, needsRefundOnly, fromDate, toDate, offset, pageSize
            );

            // B4: Gói kết quả phân trang trả về cho Servlet
            PageResult<ManagerTransactionItem> pageResult =
                    new PageResult<>(items, page, pageSize, totalItems);

            return ServiceResult.ok(pageResult);

        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /**
     * Đếm số lượng giao dịch cần hoàn tiền ngoài hệ thống đang chờ xử lý (badge counter).
     *
     * @return số giao dịch cần hoàn tiền
     */
    public int countPendingRefunds() {
        try {
            return transactionDAO.countPendingRefunds();
        } catch (SQLException e) {
            e.printStackTrace();
            return 0;
        }
    }

    /**
     * Lấy danh sách lịch sử chốt ca làm việc có phân trang phục vụ Tab 2 của Manager (SRS 7.4.2).
     *
     * @param fromDate từ ngày
     * @param toDate   đến ngày
     * @param page     trang hiện tại
     * @param pageSize kích thước trang
     * @return ServiceResult chứa PageResult của ClosedShiftHistoryItem
     */
    public ServiceResult<PageResult<ClosedShiftHistoryItem>> getClosedShiftsPage(
            LocalDate fromDate, LocalDate toDate, int page, int pageSize) {

        // B1: Kiểm tra khoảng ngày
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            return ServiceResult.fail(Messages.MSG169_DATE_RANGE_REVERSED);
        }

        try {
            // B2: Đếm tổng số ca chốt
            int totalItems = shiftDAO.countClosedShiftsHistory(fromDate, toDate);

            // B3: Lấy dữ liệu trang
            int offset = (page - 1) * pageSize;
            List<ClosedShiftHistoryItem> items =
                    shiftDAO.findClosedShiftsHistoryPage(fromDate, toDate, offset, pageSize);

            return ServiceResult.ok(new PageResult<>(items, page, pageSize, totalItems));

        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /**
     * Lấy thông tin chi tiết đầy đủ của một đơn hàng kèm các lần thử thanh toán cho modal (UC-26).
     *
     * @param orderId mã đơn hàng
     * @return ServiceResult chứa ManagerOrderDetailView
     */
    public ServiceResult<ManagerOrderDetailView> getOrderDetail(int orderId) {
        if (orderId <= 0) {
            return ServiceResult.fail(Messages.MSG170_ORDER_CODE_INVALID_FULL);
        }

        try {
            ManagerOrderDetailView detail = transactionDAO.findOrderDetailForManager(orderId);
            if (detail == null) {
                return ServiceResult.fail(String.format(Messages.MSG08_NOT_FOUND, "đơn hàng"));
            }
            return ServiceResult.ok(detail);

        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /**
     * Manager xác nhận đã thực hiện hoàn tiền ngoài hệ thống cho giao dịch theo BR-47 và CBR-72.
     *
     * @param accountId     tài khoản Manager thực hiện
     * @param transactionId mã giao dịch được hoàn tiền
     * @param refundNote    ghi chú đối soát hoàn tiền (bắt buộc từ 5 - 500 ký tự)
     * @return ServiceResult trạng thái xử lý
     */
    public ServiceResult<Void> recordRefund(int accountId, int transactionId, String refundNote) {
        // B1: Kiểm tra ghi chú hợp lệ (ít nhất 5 ký tự)
        if (!ValidationUtil.isValidReason(refundNote)) {
            return ServiceResult.fail(Messages.MSG171_REFUND_NOTE_LENGTH);
        }

        if (transactionId <= 0) {
            return ServiceResult.fail(Messages.MSG158_TRANSACTION_ID_INVALID);
        }

        try (Connection conn = DBContext.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // B2: Xác thực tài khoản là Manager đang làm việc
                Employee manager = findEmployeeForAccount(conn, accountId, Role.MANAGER);
                if (manager == null) {
                    conn.rollback();
                    return ServiceResult.fail(Messages.MSG172_REFUND_NO_PERMISSION);
                }

                // B3: Đánh dấu giao dịch đã hoàn tiền
                int rowsUpdated = transactionDAO.markRefunded(conn, transactionId, manager.getEmployeeId(), refundNote.trim());
                if (rowsUpdated == 0) {
                    conn.rollback();
                    return ServiceResult.fail(Messages.MSG173_REFUND_NOT_APPLICABLE);
                }

                // B4: Ghi nhật ký AuditLog
                auditLogService.logInTransaction(
                        conn,
                        accountId,
                        "Xác nhận hoàn tiền ngoài hệ thống",
                        "Transactions",
                        transactionId,
                        "RefundedAt",
                        null,
                        "Đã hoàn tiền",
                        refundNote.trim()
                );

                conn.commit();
                return ServiceResult.ok(null);

            } catch (SQLException e) {
                conn.rollback();
                e.printStackTrace();
                return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
            } finally {
                conn.setAutoCommit(true);
            }

        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /**
     * Xuất toàn bộ danh sách giao dịch đang lọc ra định dạng Excel .xlsx bằng Apache POI (SRS 7.4.2).
     *
     * @param search          từ khóa tìm kiếm
     * @param method          phương thức thanh toán
     * @param status          trạng thái đơn
     * @param needsRefundOnly lọc đơn cần hoàn
     * @param fromDate        từ ngày
     * @param toDate          đến ngày
     * @param out             OutputStream để ghi file excel trả về client
     * @throws IOException  khi xuất file lỗi
     * @throws SQLException khi truy vấn CSDL lỗi
     */
    public void exportToExcel(String search, String method, String status, Boolean needsRefundOnly,
                              LocalDate fromDate, LocalDate toDate, OutputStream out)
            throws IOException, SQLException {

        // B1: Lấy toàn bộ danh sách thỏa mãn điều kiện lọc
        List<ManagerTransactionItem> list = transactionDAO.findAllManagerTransactionsForExport(
                search, method, status, needsRefundOnly, fromDate, toDate
        );

        // B2: Khởi tạo Workbook và Sheet
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Giao dịch");

            // B3: Tạo Style tiêu đề
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);

            // B4: Dòng tiêu đề các cột
            String[] headers = {
                    "STT", "Mã đơn hàng", "Kênh bán", "Thời điểm tạo",
                    "Khách hàng", "Số điện thoại", "Gói tập", "Mã voucher",
                    "Đơn giá (VND)", "Giảm giá (VND)", "Tiền trước thuế (VND)",
                    "Thuế VAT (VND)", "Tổng tiền (VND)", "Phương thức",
                    "Trạng thái đơn", "Cần hoàn tiền?", "Thời điểm hoàn",
                    "Người duyệt hoàn", "Ghi chú hoàn tiền"
            };

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // B5: Style cho các ô dữ liệu
            CellStyle textStyle = workbook.createCellStyle();
            textStyle.setBorderBottom(BorderStyle.THIN);
            textStyle.setBorderTop(BorderStyle.THIN);
            textStyle.setBorderLeft(BorderStyle.THIN);
            textStyle.setBorderRight(BorderStyle.THIN);

            CellStyle numberStyle = workbook.createCellStyle();
            numberStyle.cloneStyleFrom(textStyle);
            numberStyle.setAlignment(HorizontalAlignment.RIGHT);
            numberStyle.setDataFormat(workbook.createDataFormat().getFormat("#,##0"));

            CellStyle centerStyle = workbook.createCellStyle();
            centerStyle.cloneStyleFrom(textStyle);
            centerStyle.setAlignment(HorizontalAlignment.CENTER);

            // B6: Điền từng dòng dữ liệu
            int rowIdx = 1;
            for (ManagerTransactionItem item : list) {
                Row row = sheet.createRow(rowIdx);

                // STT
                Cell c0 = row.createCell(0);
                c0.setCellValue(rowIdx);
                c0.setCellStyle(centerStyle);

                // Mã đơn
                Cell c1 = row.createCell(1);
                c1.setCellValue(item.getOrderCode());
                c1.setCellStyle(centerStyle);

                // Kênh bán
                Cell c2 = row.createCell(2);
                c2.setCellValue(item.isOnline() ? "Trực tuyến" : "Tại quầy");
                c2.setCellStyle(centerStyle);

                // Thời điểm tạo
                Cell c3 = row.createCell(3);
                c3.setCellValue(item.getCreatedAt() != null ? DateTimeUtil.formatDateTime(item.getCreatedAt()) : "");
                c3.setCellStyle(centerStyle);

                // Khách hàng
                Cell c4 = row.createCell(4);
                c4.setCellValue(item.getCustomerName() != null ? item.getCustomerName() : "");
                c4.setCellStyle(textStyle);

                // Số điện thoại
                Cell c5 = row.createCell(5);
                c5.setCellValue(item.getCustomerPhone() != null ? item.getCustomerPhone() : "");
                c5.setCellStyle(centerStyle);

                // Gói tập
                Cell c6 = row.createCell(6);
                c6.setCellValue(item.getPackageName() != null ? item.getPackageName() : "");
                c6.setCellStyle(textStyle);

                // Mã voucher
                Cell c7 = row.createCell(7);
                c7.setCellValue(item.getVoucherCode() != null ? item.getVoucherCode() : "");
                c7.setCellStyle(centerStyle);

                // Đơn giá
                Cell c8 = row.createCell(8);
                c8.setCellValue(item.getUnitPrice());
                c8.setCellStyle(numberStyle);

                // Giảm giá
                Cell c9 = row.createCell(9);
                c9.setCellValue(item.getDiscountAmount());
                c9.setCellStyle(numberStyle);

                // Tiền trước thuế
                long preTax = item.getTotalAmount() - item.getVatAmount();
                Cell c10 = row.createCell(10);
                c10.setCellValue(preTax);
                c10.setCellStyle(numberStyle);

                // Thuế VAT
                Cell c11 = row.createCell(11);
                c11.setCellValue(item.getVatAmount());
                c11.setCellStyle(numberStyle);

                // Tổng tiền
                Cell c12 = row.createCell(12);
                c12.setCellValue(item.getTotalAmount());
                c12.setCellStyle(numberStyle);

                // Phương thức thanh toán
                Cell c13 = row.createCell(13);
                String methodLabel = "Tiền mặt";
                if ("Transfer".equalsIgnoreCase(item.getPaymentMethod())) {
                    methodLabel = "Chuyển khoản";
                } else if ("OnlineGateway".equalsIgnoreCase(item.getPaymentMethod())) {
                    methodLabel = "Cổng VNPay";
                }
                c13.setCellValue(methodLabel);
                c13.setCellStyle(centerStyle);

                // Trạng thái đơn
                Cell c14 = row.createCell(14);
                String statusLabel = "Đã thanh toán";
                if (item.isPending()) {
                    statusLabel = "Chờ thanh toán";
                } else if (item.isCancelled()) {
                    statusLabel = "Đã hủy";
                }
                c14.setCellValue(statusLabel);
                c14.setCellStyle(centerStyle);

                // Cần hoàn tiền?
                Cell c15 = row.createCell(15);
                String refundBadge = "Không";
                if (item.isRefundPending()) {
                    refundBadge = "Cần hoàn tiền";
                } else if (item.isRefundCompleted()) {
                    refundBadge = "Đã hoàn tiền";
                }
                c15.setCellValue(refundBadge);
                c15.setCellStyle(centerStyle);

                // Thời điểm hoàn
                Cell c16 = row.createCell(16);
                c16.setCellValue(item.getRefundedAt() != null ? DateTimeUtil.formatDateTime(item.getRefundedAt()) : "");
                c16.setCellStyle(centerStyle);

                // Người duyệt hoàn
                Cell c17 = row.createCell(17);
                c17.setCellValue(item.getRefundedByName() != null ? item.getRefundedByName() : "");
                c17.setCellStyle(textStyle);

                // Ghi chú hoàn
                Cell c18 = row.createCell(18);
                c18.setCellValue(item.getRefundNote() != null ? item.getRefundNote() : "");
                c18.setCellStyle(textStyle);

                rowIdx++;
            }

            // B7: Tự động căn chỉnh độ rộng cột
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            // B8: Ghi ra output stream
            workbook.write(out);
        }
    }

    /**
     * Xác định thực thể Employee từ Account ID và vai trò mong muốn.
     */
    private Employee findEmployeeForAccount(Connection conn, int accountId, Role expectedRole) throws SQLException {
        Account account = accountDAO.findById(accountId);
        if (account == null) {
            return null;
        }

        Employee employee = employeeDAO.findByPersonId(account.getPersonId());
        if (employee == null
                || employee.getPosition() != expectedRole
                || employee.getStatus() != Employee.Status.WORKING) {
            return null;
        }

        return employee;
    }
}
