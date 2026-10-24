package com.gms.service;

import com.gms.constant.Messages;
import com.gms.constant.Role;
import com.gms.dao.OrderDAO;
import com.gms.dao.PackageDAO;
import com.gms.dao.PaymentDAO;
import com.gms.dao.VoucherDAO;
import com.gms.dto.PaymentCustomerView;
import com.gms.dto.CheckoutView;
import com.gms.dto.ServiceResult;
import com.gms.entity.Order;
import com.gms.entity.Package;
import com.gms.entity.Voucher;
import com.gms.util.DBContext;
import com.gms.util.DateTimeUtil;
import com.gms.util.MoneyUtil;
import com.gms.util.ValidationUtil;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.Duration;
import java.util.Locale;
import java.util.List;
import com.gms.constant.AppConstants;
import com.gms.dao.TransactionDAO;
import com.gms.entity.Transaction;
import com.gms.integration.VnPayClient;

/**
 * SEQ-1: tạo đơn online Pending; giao dịch và subscription được tạo ở sequence
 * sau.
 */
public class PaymentService {

    // DAO đọc khách, gói và đơn bằng cùng connection của Service.
    private final PaymentDAO paymentDAO;
    private final PackageDAO packageDAO;
    private final OrderDAO orderDAO;
    private final VoucherDAO voucherDAO;

    // Đọc P-09/P-10 và ghi nhật ký theo tiện ích chung của project.
    private final SystemConfigService configService;
    private final AuditLogService auditLogService;

    // Đọc và lưu các lần thử thanh toán.
    private final TransactionDAO transactionDAO = new TransactionDAO();

    // Tạo URL thanh toán có chữ ký theo quy định VNPay.
    private final VnPayClient vnPayClient = new VnPayClient();

    /**
     * Dùng DAO và tiện ích đã có của project.
     */
    public PaymentService() {
        this(new PaymentDAO(), new PackageDAO(), new OrderDAO(), new SystemConfigService(), new AuditLogService());
    }

    /**
     * Cho test thay DAO nhưng vẫn chạy cùng luồng transaction của Service.
     */
    public PaymentService(PaymentDAO paymentDAO, PackageDAO packageDAO, OrderDAO orderDAO,
            SystemConfigService configService, AuditLogService auditLogService) {
        this(paymentDAO, packageDAO, orderDAO, configService, auditLogService, new VoucherDAO());
    }

    /**
     * Constructor mở rộng cho kiểm thử SEQ-2, giữ constructor SEQ-1 để không
     * phá chỗ gọi cũ.
     */
    public PaymentService(PaymentDAO paymentDAO, PackageDAO packageDAO, OrderDAO orderDAO,
            SystemConfigService configService, AuditLogService auditLogService, VoucherDAO voucherDAO) {
        this.voucherDAO = voucherDAO;
        this.paymentDAO = paymentDAO;
        this.packageDAO = packageDAO;
        this.orderDAO = orderDAO;
        this.configService = configService;
        this.auditLogService = auditLogService;
    }

    /**
     * Tạo đơn từ giá DB; hủy đơn cũ và trả voucher trong cùng transaction.
     *
     * @param accountId tài khoản từ session, không phải customerId
     * @param packageId gói khách chọn; chỉ Membership/DayPass được mua tại đây
     * @return orderId mới để Servlet redirect tới checkout, hoặc thông báo lỗi
     */
    public ServiceResult<Integer> createOnlineOrder(int accountId, Integer packageId) {
        // B1: kiểm đầu vào trước khi mở kết nối; không nhận giá/customerId từ form.
        if (accountId <= 0 || packageId == null || packageId <= 0) {
            return ServiceResult.fail("Gói tập không hợp lệ. Vui lòng chọn lại.");
        }

        try (Connection conn = openConnection()) {
            conn.setAutoCommit(false);
            try {
                // B2: khóa hồ sơ theo tài khoản; Frozen không được tạo/hủy đơn.
                PaymentCustomerView customer = paymentDAO.findCustomerForPurchase(conn, accountId);
                if (customer == null) {
                    conn.rollback();
                    return ServiceResult.fail("Không tìm thấy hồ sơ khách hàng còn hiệu lực.");
                }
                if (customer.isFrozen()) {
                    conn.rollback();
                    return ServiceResult.fail("Hồ sơ đang đóng băng. Bạn chưa thể mua gói tập.");
                }

                // B3: chỉ mua gói còn bán; PT/buổi lẻ có luồng đặt riêng.
                Package pkg = packageDAO.findById(conn, packageId);
                boolean isOnlineType = pkg != null && (pkg.getPackageType() == Package.Type.MEMBERSHIP
                        || pkg.getPackageType() == Package.Type.DAY_PASS);
                if (pkg == null || !pkg.isActive() || !isOnlineType || pkg.getPrice() <= 0) {
                    conn.rollback();
                    return ServiceResult.fail("Gói này không còn bán hoặc không hỗ trợ mua online.");
                }
                int dueMinutes = configService.getInt(SystemConfigService.PAYMENT_DUE_MINUTES);
                int vatRate = configService.getInt(SystemConfigService.VAT_PERCENT);
                if (dueMinutes <= 0 || vatRate < 0 || vatRate > 20) {
                    throw new SQLException("Cấu hình P-09/P-10 không hợp lệ");
                }

                // B4: chỉ hủy Pending online cùng khách + gói, không chạm Paid/tại quầy.
                List<Order> oldOrders = orderDAO.findPendingOnlineByHolderAndPackage(conn,
                        customer.getCustomerId(), packageId);
                for (Order oldOrder : oldOrders) {
                    int cancelledRows = orderDAO.markCancelled(conn, oldOrder.getOrderId(),
                            Order.CancelType.REPLACED_BY_NEW_ORDER, "Khách tạo đơn online mới cùng gói");
                    if (cancelledRows != 1) {
                        throw new SQLException("Đơn cũ đã thay đổi trạng thái");
                    }
                    if (oldOrder.getVoucherId() != null) {
                        int voucherRows = paymentDAO.returnVoucherUse(conn, oldOrder.getVoucherId());
                        if (voucherRows != 1) {
                            throw new SQLException("Không thể trả lượt voucher của đơn cũ");
                        }
                    }
                    auditLogService.logInTransaction(conn, accountId, Role.CUSTOMER,
                            "Thay thế đơn online", "Orders", oldOrder.getOrderId(), "Status",
                            Order.Status.PENDING.getDbValue(), Order.Status.CANCELLED.getDbValue(),
                            "Khách tạo đơn online mới cùng gói");
                }

                // B5: snapshot giá đã gồm VAT, lấy cùng một mốc giờ VN cho hạn đơn.
                LocalDateTime createdAt = DateTimeUtil.now().withNano(0);
                Order order = new Order(0);
                order.setOrderCode(orderDAO.generateNextOrderCode(conn));
                order.setHolderCustomerId(customer.getCustomerId());
                order.setPackageId(packageId);
                order.setChannel(Order.Channel.ONLINE);
                order.setPackageNameSnapshot(pkg.getPackageName());
                order.setPackageDescriptionSnapshot(pkg.getDescription());
                order.setPackageBenefitsSnapshot(pkg.getBenefits());
                order.setUnitPriceSnapshot(pkg.getPrice());
                order.setVatRateSnapshot(vatRate);
                order.setVatAmount(MoneyUtil.calculateVatAmount(pkg.getPrice(), vatRate));
                order.setStatus(Order.Status.PENDING);
                order.setCreatedAt(createdAt);
                order.setPaymentDueAt(createdAt.plusMinutes(dueMinutes));
                int orderId = orderDAO.insert(conn, order);

                // B6: nhật ký và đơn cùng commit; lỗi giữa chừng trả toàn bộ thay đổi.
                auditLogService.logInTransaction(conn, accountId, Role.CUSTOMER,
                        "Tạo đơn online", "Orders", orderId, "Status", null,
                        Order.Status.PENDING.getDbValue(), null);
                conn.commit();
                return ServiceResult.ok(orderId);
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException | RuntimeException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /**
     * Đọc đơn thật cho trang bàn giao; chưa áp voucher/khởi tạo thanh toán.
     */
    public ServiceResult<Order> getOwnedOnlineOrder(int accountId, Integer orderId) {
        if (accountId <= 0 || orderId == null || orderId <= 0) {
            return ServiceResult.fail("Mã đơn không hợp lệ.");
        }
        try (Connection conn = openConnection()) {
            Order order = orderDAO.findOnlineByIdAndAccount(conn, accountId, orderId);
            if (order == null) {
                return ServiceResult.fail("Không tìm thấy đơn hàng của bạn.");
            }
            return ServiceResult.ok(order);
        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /**
     * Đọc dữ liệu thật cho checkout; giá/VAT giữ nguyên snapshot của đơn.
     */
    public ServiceResult<CheckoutView> getCheckoutView(int accountId, Integer orderId) {
        if (accountId <= 0 || orderId == null || orderId <= 0) {
            return ServiceResult.fail("Mã đơn không hợp lệ.");
        }
        try (Connection conn = openConnection()) {
            Order order = orderDAO.findOnlineByIdAndAccount(conn, accountId, orderId);
            if (order == null) {
                return ServiceResult.fail("Không tìm thấy đơn hàng của bạn.");
            }
            String code = voucherDAO.findCodeById(conn, order.getVoucherId());
            LocalDateTime now = DateTimeUtil.now();
            long secondsLeft = order.getPaymentDueAt() == null ? 0
                    : (Math.max(0, Duration.between(now, order.getPaymentDueAt()).toMillis()) + 999) / 1000;
            boolean hasPendingPayment = paymentDAO.hasPendingTransaction(conn, orderId);
            String lockReason = null;
            if (!order.isPending()) {
                lockReason = "Chỉ được đổi voucher trên đơn chờ thanh toán.";
            } else if (order.getPaymentDueAt() == null || !now.isBefore(order.getPaymentDueAt())) {
                lockReason = "Đơn đã hết hạn thanh toán. Vui lòng quay lại chọn gói để tạo đơn mới.";
            } else if (hasPendingPayment) {
                lockReason = Messages.MSG86_VOUCHER_PAYMENT_PENDING;
            }
            return ServiceResult.ok(new CheckoutView(order, code, secondsLeft, lockReason == null, lockReason));
        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /**
     * Nhận mã từ form, chuẩn hóa và áp/thay voucher; không nhận discount từ
     * trình duyệt.
     */
    public ServiceResult<Void> applyVoucher(int accountId, Integer orderId, String rawCode) {
        String code = rawCode == null ? "" : rawCode.trim().toUpperCase(Locale.ROOT);
        if (!ValidationUtil.isValidVoucherCode(code)) {
            return ServiceResult.failField("voucherCode", "Mã voucher phải có 4–20 ký tự chữ A–Z hoặc số.");
        }
        return changeVoucher(accountId, orderId, code, false);
    }

    /**
     * Gỡ mã khỏi đơn Pending và hoàn đúng một lượt cũ (BR-45).
     */
    public ServiceResult<Void> removeVoucher(int accountId, Integer orderId) {
        return changeVoucher(accountId, orderId, null, true);
    }

    /**
     * Một transaction cho kiểm điều kiện, giữ/hoàn lượt voucher, giảm giá, VAT
     * và AuditLog.
     */
    private ServiceResult<Void> changeVoucher(int accountId, Integer orderId, String code, boolean remove) {
        // B1: kiểm ID trước kết nối; danh tính không lấy từ hidden input.
        if (accountId <= 0 || orderId == null || orderId <= 0) {
            return ServiceResult.fail("Mã đơn không hợp lệ.");
        }
        try (Connection conn = openConnection()) {
            conn.setAutoCommit(false);
            try {
                // B2: khóa theo thứ tự hồ sơ -> đơn -> voucher, khớp với tạo đơn SEQ-1.
                PaymentCustomerView customer = paymentDAO.findCustomerForPurchase(conn, accountId);
                if (customer == null || customer.isFrozen()) {
                    conn.rollback();
                    return ServiceResult.fail("Hồ sơ không còn hiệu lực hoặc đang đóng băng.");
                }
                Order order = orderDAO.findOnlineForUpdate(conn, accountId, orderId);
                LocalDateTime now = DateTimeUtil.now();
                if (order == null || !order.isPending()) {
                    conn.rollback();
                    return ServiceResult.fail("Không tìm thấy đơn chờ thanh toán của bạn.");
                }
                if (order.getPaymentDueAt() == null || !now.isBefore(order.getPaymentDueAt())) {
                    conn.rollback();
                    return ServiceResult.fail("Đơn đã hết hạn thanh toán. Vui lòng tạo đơn mới.");
                }
                if (paymentDAO.hasPendingTransaction(conn, orderId)) {
                    conn.rollback();
                    return ServiceResult.fail(Messages.MSG86_VOUCHER_PAYMENT_PENDING);
                }

                // B3: kiểm mã mới trước khi hoàn lượt cũ; mã sai không xóa giảm giá đang có.
                Integer oldVoucherId = order.getVoucherId();
                List<Voucher> vouchers = voucherDAO.findForChange(conn, code, oldVoucherId);
                Voucher selected = null;
                for (Voucher voucher : vouchers) {
                    if (code != null && code.equals(voucher.getVoucherCode())) {
                        selected = voucher;
                    }
                }
                if (!remove) {
                    if (selected != null && Integer.valueOf(selected.getVoucherId()).equals(oldVoucherId)) {
                        conn.commit();
                        return ServiceResult.ok(null, "Voucher này đã được áp dụng; không tăng thêm lượt dùng.");
                    }
                    Package pkg = packageDAO.findById(conn, order.getPackageId());
                    String invalidReason = validateVoucher(conn, selected, pkg, order, now.toLocalDate());
                    if (invalidReason != null) {
                        conn.rollback();
                        return ServiceResult.failField("voucherCode",
                                String.format(Messages.MSG85_VOUCHER_INVALID, invalidReason));
                    }
                } else if (oldVoucherId == null) {
                    conn.commit();
                    return ServiceResult.ok(null, "Đơn chưa áp dụng voucher.");
                }

                // B4: giữ lượt mới và trả lượt cũ; rowsAffected sai thì rollback tất cả.
                long discountAmount = 0;
                if (!remove) {
                    if (voucherDAO.reserveUse(conn, selected.getVoucherId()) != 1) {
                        throw new SQLException("Không thể giữ lượt voucher mới");
                    }
                    if (selected.getDiscountType() == Voucher.DiscountType.PERCENT) {
                        discountAmount = MoneyUtil.calculatePercentDiscount(order.getUnitPriceSnapshot(),
                                selected.getDiscountValue());
                    } else {
                        discountAmount = Math.min(order.getUnitPriceSnapshot(), selected.getDiscountValue());
                    }
                }
                if (oldVoucherId != null && paymentDAO.returnVoucherUse(conn, oldVoucherId) != 1) {
                    throw new SQLException("Không thể trả lượt voucher cũ");
                }

                // B5: VAT tách từ giá sau giảm theo thuế suất snapshot, không cộng thêm VAT.
                order.setVoucherId(remove ? null : selected.getVoucherId());
                order.setDiscountAmount(discountAmount);
                order.setVatAmount(MoneyUtil.calculateVatAmount(order.getFinalAmount(), order.getVatRateSnapshot()));
                if (orderDAO.updateVoucher(conn, order, DateTimeUtil.now()) != 1) {
                    throw new SQLException("Đơn đã thay đổi hoặc hết hạn lúc ghi voucher");
                }
                auditLogService.logInTransaction(conn, accountId, Role.CUSTOMER,
                        remove ? "Gỡ voucher" : "Áp dụng voucher", "Orders", orderId, "VoucherId",
                        oldVoucherId == null ? null : oldVoucherId.toString(),
                        order.getVoucherId() == null ? null : order.getVoucherId().toString(), null);
                conn.commit();
                return ServiceResult.ok(null, remove ? "Đã gỡ voucher." : "Đã áp dụng voucher.");
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException | RuntimeException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /**
     * Trả lý do CBR-73/74/76; null nếu hợp lệ. Lượt dùng tính cả Pending/Paid
     * khác đơn hiện tại.
     */
    private String validateVoucher(Connection conn, Voucher voucher, Package pkg, Order order, LocalDate today)
            throws SQLException {
        if (voucher == null) {
            return "mã không tồn tại";
        }
        if (!voucher.isActive()) {
            return "mã đã ngừng hoạt động";
        }
        if (!voucher.isValidOnDate(today)) {
            return "mã chưa đến ngày áp dụng hoặc đã hết hạn";
        }
        if (!voucher.hasRemainingUsage()) {
            return "mã đã hết lượt sử dụng";
        }
        boolean isMembership = pkg != null && pkg.getPackageType() == Package.Type.MEMBERSHIP;
        boolean isDayPass = pkg != null && pkg.getPackageType() == Package.Type.DAY_PASS;
        if (!isMembership && !isDayPass) {
            return "mã chỉ áp dụng cho gói tập hoặc vé ngày";
        }
        if (voucher.getApplicableScope() == Voucher.ApplicableScope.MEMBERSHIP_ONLY && !isMembership) {
            return "mã chỉ áp dụng cho gói hội viên";
        }
        if (voucher.getApplicableScope() == null || voucher.getDiscountType() == null) {
            throw new SQLException("Voucher thiếu loại hoặc phạm vi áp dụng");
        }
        if (order.getUnitPriceSnapshot() < voucher.getMinOrderValue()) {
            return "giá gói trước giảm phải từ " + MoneyUtil.formatMoney(voucher.getMinOrderValue());
        }
        if (voucher.getMaxUsagePerCustomer() != null) {
            int usedByCustomer = voucherDAO.countCustomerUses(conn, voucher.getVoucherId(),
                    order.getHolderCustomerId(), order.getOrderId());
            if (usedByCustomer >= voucher.getMaxUsagePerCustomer()) {
                return "bạn đã dùng hết số lượt cho phép của mã này";
            }
        }
        return null;
    }

    /**
     * Tạo lần thử thanh toán Pending và URL VNPay. Chưa chuyển Order sang Paid,
     * chưa cấp Subscription.
     *
     * @param accountId tài khoản từ session
     * @param orderId đơn cần thanh toán
     * @param clientIp IP do Servlet lấy từ request
     * @return URL thanh toán hoặc thông báo lỗi
     */
    public ServiceResult<String> startPayment(
            int accountId, Integer orderId, String clientIp) {

        // B1: kiểm tra ID trước khi mở kết nối.
        if (accountId <= 0 || orderId == null || orderId <= 0) {
            return ServiceResult.fail("Mã đơn không hợp lệ.");
        }

        try (Connection conn = openConnection()) {
            conn.setAutoCommit(false);

            try {
                // B2: khóa hồ sơ trước, cùng thứ tự với thao tác voucher.
                PaymentCustomerView customer
                        = paymentDAO.findCustomerForPurchase(conn, accountId);

                if (customer == null || customer.isFrozen()) {
                    conn.rollback();
                    return ServiceResult.fail(
                            "Hồ sơ không còn hiệu lực hoặc đang đóng băng.");
                }

                // B3: đọc và khóa đơn thuộc tài khoản hiện tại.
                Order order
                        = orderDAO.findOnlineForUpdate(conn, accountId, orderId);

                LocalDateTime now = DateTimeUtil.now().withNano(0);

                if (order == null || !order.isPending()) {
                    conn.rollback();
                    return ServiceResult.fail(
                            "Không tìm thấy đơn chờ thanh toán của bạn.");
                }

                if (order.getPaymentDueAt() == null
                        || !now.isBefore(order.getPaymentDueAt())) {
                    conn.rollback();
                    return ServiceResult.fail(
                            "Đơn đã hết hạn thanh toán. Vui lòng tạo đơn mới.");
                }

                if (transactionDAO.hasPending(conn, orderId)) {
                    conn.rollback();
                    return ServiceResult.fail(
                            "Đơn đang có giao dịch thanh toán xử lý. "
                            + "Không tạo thêm giao dịch.");
                }

                long amount = order.getFinalAmount();

                if (amount <= 0) {
                    conn.rollback();
                    return ServiceResult.fail(
                            "VNPay yêu cầu số tiền lớn hơn 0đ. "
                            + "Vui lòng gỡ hoặc đổi mã giảm giá.");
                }

                // B4: hạn link không vượt quá hạn thanh toán của đơn.
                LocalDateTime expiresAt = now.plusMinutes(
                        AppConstants.PAYMENT_LINK_VALID_MINUTES);

                if (expiresAt.isAfter(order.getPaymentDueAt())) {
                    expiresAt = order.getPaymentDueAt();
                }

                // B5: tạo mã lần thanh toán theo SRS.
                int attempt = transactionDAO.nextAttempt(conn, orderId);

                String txnRef = order.getOrderCode().replace("-", "")
                        + "A" + attempt;

                // B6: snapshot số tiền và hạn của lần thanh toán.
                Transaction payment = new Transaction(0);
                payment.setOrderId(orderId);
                payment.setAmount(amount);
                payment.setPaymentMethod(
                        Transaction.PaymentMethod.ONLINE_GATEWAY);
                payment.setStatus(Transaction.Status.PENDING);
                payment.setGatewayReference(txnRef);
                payment.setCreatedAt(now);
                payment.setExpiresAt(expiresAt);

                int transactionId
                        = transactionDAO.insertPending(conn, payment);

                // B7: tạo URL chứa thông tin thanh toán và chữ ký.
                String paymentUrl = vnPayClient.buildPaymentUrl(
                        txnRef,
                        amount,
                        "Thanh toan " + order.getOrderCode(),
                        clientIp,
                        now,
                        expiresAt);

                // B8: nhật ký và giao dịch được lưu cùng transaction.
                auditLogService.logInTransaction(
                        conn,
                        accountId,
                        Role.CUSTOMER,
                        "Khởi tạo thanh toán VNPay",
                        "Transactions",
                        transactionId,
                        "Status",
                        null,
                        Transaction.Status.PENDING.getDbValue(),
                        null);

                conn.commit();
                return ServiceResult.ok(paymentUrl);

            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            }

        } catch (SQLException | RuntimeException e) {
            // Không ghi secret hoặc URL đã ký ra log.
            System.err.println("Không khởi tạo được thanh toán VNPay: "
                    + e.getClass().getSimpleName());

            return ServiceResult.fail(
                    "Chưa mở được VNPay. Vui lòng kiểm tra cấu hình "
                    + "merchant và kết nối hệ thống.");
        }
    }

    /**
     * Mở kết nối thực; test thay bằng kết nối theo dõi commit/rollback.
     */
    protected Connection openConnection() throws SQLException {
        return DBContext.getConnection();
    }
}
