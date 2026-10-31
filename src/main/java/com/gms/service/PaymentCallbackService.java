package com.gms.service;

import com.gms.dao.NotificationDAO;
import com.gms.dao.OrderDAO;
import com.gms.dao.PackageDAO;
import com.gms.dao.PaymentDAO;
import com.gms.dao.SubscriptionDAO;
import com.gms.dao.TransactionDAO;
import com.gms.dto.IpnResult;
import com.gms.entity.Order;
import com.gms.entity.Package;
import com.gms.entity.Subscription;
import com.gms.entity.Transaction;
import com.gms.integration.VnPayClient;
import com.gms.util.DBContext;
import com.gms.util.DateTimeUtil;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import com.gms.dto.ServiceResult;
import com.gms.dto.PaymentResultView;
import com.gms.dto.StartDatePicker;
import com.gms.util.PackagePeriod;
import com.gms.util.AppConfig;

/**
 * Nhận kết quả thanh toán từ VNPay qua IPN. Không dùng session và không tin kết
 * quả chưa kiểm tra chữ ký.
 */
public class PaymentCallbackService {

    private final TransactionDAO transactionDAO;
    private final OrderDAO orderDAO;
    private final PaymentDAO paymentDAO;
    private final PackageDAO packageDAO;
    private final SubscriptionDAO subscriptionDAO;
    private final SystemConfigService configService;
    private final AuditLogService auditService;
    private final NotificationDAO notificationDAO;
    private final VnPayClient vnPayClient;
    // Chỉ đọc để trang kết quả hiện nút "Đổi ngày" sau khi đơn Paid.
    private final SubscriptionStartDateService startDateService = new SubscriptionStartDateService();

    public PaymentCallbackService() {
        this(
                new TransactionDAO(),
                new OrderDAO(),
                new PaymentDAO(),
                new PackageDAO(),
                new SubscriptionDAO(),
                new SystemConfigService(),
                new AuditLogService(),
                new NotificationDAO(),
                new VnPayClient());
    }

    /**
     * Cho phép thay dependency trong unit test. Các DAO vẫn sử dụng cùng
     * Connection do Service quản lý.
     */
    public PaymentCallbackService(
            TransactionDAO transactionDAO,
            OrderDAO orderDAO,
            PaymentDAO paymentDAO,
            PackageDAO packageDAO,
            SubscriptionDAO subscriptionDAO,
            SystemConfigService configService,
            AuditLogService auditService,
            NotificationDAO notificationDAO,
            VnPayClient vnPayClient) {

        this.transactionDAO = transactionDAO;
        this.orderDAO = orderDAO;
        this.paymentDAO = paymentDAO;
        this.packageDAO = packageDAO;
        this.subscriptionDAO = subscriptionDAO;
        this.configService = configService;
        this.auditService = auditService;
        this.notificationDAO = notificationDAO;
        this.vnPayClient = vnPayClient;
    }

    /**
     * Xác minh và xử lý một thông báo thanh toán.
     *
     * @param params các tham số vnp_* do Servlet đọc từ request
     * @return mã để Servlet trả JSON cho VNPay
     */
    public IpnResult handleIpn(Map<String, String> params) {
        String ref = params == null ? null : params.get("vnp_TxnRef");
        System.out.println("[IPN] nhận txnRef=" + shorten(ref));

        try {
            if (params == null || !vnPayClient.verifySignature(params)) {
                return reply("97", "Invalid signature");
            }

            String expectedMerchant = AppConfig.get("vnpay.tmn-code");

            if (expectedMerchant == null || expectedMerchant.isBlank()) {
                return reply("99", "Configuration error");
            }

            if (!expectedMerchant.equals(params.get("vnp_TmnCode"))) {
                return reply("97", "Invalid merchant");
            }

            return processVerifiedResult(params);

        } catch (RuntimeException e) {
            System.err.println(
                    "[IPN] Processing error: "
                    + e.getClass().getSimpleName());

            return reply("99", "Processing error");
        }
    }

    /**
     * Rút gọn mã giao dịch để ghi log, không lộ đầy đủ.
     */
    private String shorten(String ref) {
        if (ref == null) {
            return "null";
        }
        return ref.length() <= 8
                ? ref
                : ref.substring(0, 4) + "..." + ref.substring(ref.length() - 4);
    }

    /**
     * Xử lý kết quả đã được xác minh từ IPN hoặc QueryDR. Giữ nguyên phần khóa
     * DB, kiểm tiền, cập nhật đơn và cấp gói.
     */
    private IpnResult processVerifiedResult(
            Map<String, String> params) {

        String responseCode = params.get("vnp_ResponseCode");
        String transactionStatus = params.get("vnp_TransactionStatus");
        if (responseCode == null
                || transactionStatus == null
                || !responseCode.matches("[0-9]{2}")
                || !transactionStatus.matches("[0-9]{2}")) {

            return reply("99", "Missing result fields");
        }

        String txnRef = parseTxnRef(params.get("vnp_TxnRef"));

        if (txnRef == null) {
            return reply("01", "Transaction not found");
        }

        try (Connection conn = openConnection()) {
            conn.setAutoCommit(false);

            try {
                // B2: đọc tham chiếu để xác định chủ đơn.
                Transaction first
                        = transactionDAO.findByGatewayReference(conn, txnRef);

                if (first == null || !first.isOnlineGateway()) {
                    conn.rollback();
                    return reply("01", "Transaction not found");
                }

                int transactionId = first.getTransactionId();

                Order initialOrder = orderDAO.findForCallback(
                        conn, first.getOrderId(), false);

                if (initialOrder == null || !initialOrder.isOnline()) {
                    conn.rollback();
                    return reply("01", "Order not found");
                }

                // B3: khóa khách → đơn → giao dịch.
                paymentDAO.lockCustomer(
                        conn, initialOrder.getHolderCustomerId());

                Order order = orderDAO.findForCallback(
                        conn, first.getOrderId(), true);

                Transaction payment = transactionDAO.findById(
                        conn, transactionId, true);

                if (order == null || payment == null) {
                    conn.rollback();
                    return reply("01", "Transaction not found");
                }

                // B4: kết quả đã xử lý thì không ghi/cấp gói lần nữa.
                if (payment.isSuccess() || payment.isFailed()) {
                    conn.rollback();
                    return reply("02", "Order already confirmed");
                }

                LocalDateTime now = DateTimeUtil.now().withNano(0);
                String reference = payment.getGatewayReference();

                boolean success = vnPayClient.isPaymentSuccess(params);

                // B5: thông báo thất bại không cấp gói.
                if (!success) {
                    if (!matchesAmount(
                            params.get("vnp_Amount"), payment.getAmount())) {

                        conn.rollback();
                        return reply("04", "Invalid amount");
                    }

                    if (payment.isExpired()) {
                        conn.rollback();
                        return reply("02", "Order already confirmed");
                    }

                    requireOne(transactionDAO.recordResult(
                            conn,
                            transactionId,
                            Transaction.Status.FAILED,
                            reference,
                            false,
                            responseCode,
                            now));

                    auditService.logInTransaction(
                            conn, null, null,
                            "VNPay không thành công",
                            "Transactions",
                            transactionId,
                            "Status",
                            Transaction.Status.PENDING.getDbValue(),
                            Transaction.Status.FAILED.getDbValue(),
                            null);

                    notifyCustomer(
                            conn,
                            order,
                            "Thanh toán chưa thành công",
                            "Bạn có thể thanh toán lại đơn "
                            + order.getOrderCode()
                            + " khi đơn còn hạn.");

                    conn.commit();
                    return reply("00", "Confirm Success");
                }

                // B6: so số xu chính xác, không chia 100 rồi làm mất phần lẻ.
                boolean amountMatches
                        = matchesAmount(
                                params.get("vnp_Amount"),
                                payment.getAmount())
                        && payment.getAmount() == order.getFinalAmount();

                boolean needsRefund
                        = !order.isPending() || !amountMatches;

                // Schema hiện tại không lưu được Success thứ hai của cùng đơn.
                if (transactionDAO.hasOtherSuccess(
                        conn, order.getOrderId(), transactionId)) {

                    conn.rollback();

                    System.err.println(
                            "IPN cần đối soát: đơn đã có Success khác.");

                    return reply("99", "Manual reconciliation required");
                }

                requireOne(transactionDAO.recordResult(
                        conn,
                        transactionId,
                        Transaction.Status.SUCCESS,
                        reference,
                        needsRefund,
                        null,
                        now));

                // B7: có tiền nhưng đơn không hợp lệ thì không cấp gói.
                if (needsRefund) {
                    if (order.isPending()) {
                        requireOne(orderDAO.markCancelled(
                                conn,
                                order.getOrderId(),
                                Order.CancelType.INVALID_PAYMENT,
                                "VNPay thành công nhưng số tiền không khớp"));

                        if (order.getVoucherId() != null) {
                            requireOne(paymentDAO.returnVoucherUse(
                                    conn, order.getVoucherId()));
                        }
                    }

                    auditService.logInTransaction(
                            conn, null, null,
                            "VNPay cần hoàn tiền",
                            "Transactions",
                            transactionId,
                            "NeedsRefund",
                            "0",
                            "1",
                            null);

                    notifyCustomer(
                            conn,
                            order,
                            "Thanh toán cần đối soát",
                            "Đơn " + order.getOrderCode()
                            + " chưa được cấp gói. "
                            + "Giao dịch đã đưa vào danh sách cần hoàn tiền.");

                    conn.commit();

                    if (!amountMatches) {
                        return reply("04", "Invalid amount");
                    }

                    return reply("00", "Confirm Success");
                }

                // B8: ghi Paid và cấp quyền sử dụng trong cùng transaction.
                requireOne(orderDAO.markPaid(
                        conn, order.getOrderId(), now));

                // Ngày thanh toán theo giờ VNPay ghi nhận: IPN về sau nửa đêm không làm ngày bắt đầu nhảy sang hôm sau.
                Subscription subscription = buildSubscription(
                        conn, order, PackagePeriod.paymentDay(params.get("vnp_PayDate"), now));

                int subscriptionId
                        = subscriptionDAO.insertOnline(conn, subscription);

                paymentDAO.activateNewOrExpired(
                        conn, order.getHolderCustomerId());

                auditService.logInTransaction(
                        conn, null, null,
                        "VNPay xác nhận thanh toán",
                        "Orders",
                        order.getOrderId(),
                        "Status",
                        Order.Status.PENDING.getDbValue(),
                        Order.Status.PAID.getDbValue(),
                        null);

                auditService.logInTransaction(
                        conn, null, null,
                        "Cấp gói sau IPN",
                        "Subscriptions",
                        subscriptionId,
                        "Status",
                        null,
                        Subscription.Status.ACTIVE.getDbValue(),
                        null);

                notifyCustomer(
                        conn,
                        order,
                        "Mua gói thành công",
                        "Đơn " + order.getOrderCode()
                        + " đã thanh toán. Gói "
                        + order.getPackageNameSnapshot()
                        + " đã được cấp.");

                conn.commit();
                return reply("00", "Confirm Success");

            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            }

        } catch (SQLException | RuntimeException e) {
            // Không ghi query IPN hoặc dữ liệu chữ ký ra log.
            System.err.println(
                    "IPN chưa commit: " + e.getClass().getSimpleName());

            return reply("99", "Unknown error");
        }
    }

    /**
     * Chuẩn bị quyền sử dụng cho Membership/DayPass. Membership nối tiếp gói
     * còn Active/Frozen; DayPass dùng trong ngày xử lý.
     */
    private Subscription buildSubscription(
            Connection conn, Order order, LocalDate today)
            throws SQLException {

        Package pkg = packageDAO.findById(conn, order.getPackageId());

        if (pkg == null) {
            throw new SQLException("Không tìm thấy gói để cấp quyền");
        }

        Subscription subscription = new Subscription(0);
        subscription.setOrderId(order.getOrderId());
        subscription.setCustomerId(order.getHolderCustomerId());

        if (pkg.getPackageType() == Package.Type.MEMBERSHIP) {
            LocalDate startDate = today;

            LocalDate oldEnd = subscriptionDAO.findLastMembershipEnd(
                    conn, order.getHolderCustomerId(), today);

            if (oldEnd != null) {
                startDate = oldEnd.plusDays(1);
            }

            int months = pkg.getDurationMonths();

            int maxFreezes = configService.getInt(
                    SystemConfigService.MAX_FREEZE_COUNT);

            if (months <= 0 || maxFreezes < 0 || maxFreezes > 255) {
                throw new SQLException(
                        "Thời hạn hoặc giới hạn bảo lưu không hợp lệ");
            }

            subscription.setSubscriptionType(
                    Subscription.Type.MEMBERSHIP);

            subscription.setStartDate(startDate);
            subscription.setEndDate(startDate.plusMonths(months));
            subscription.setMaxFreezesSnapshot(maxFreezes);

        } else if (pkg.getPackageType() == Package.Type.DAY_PASS) {
            subscription.setSubscriptionType(
                    Subscription.Type.DAY_PASS);

            subscription.setStartDate(today);
            subscription.setEndDate(today);

        } else {
            throw new SQLException(
                    "IPN hiện chỉ cấp Membership và DayPass");
        }

        return subscription;
    }

    /**
     * Tạo thông báo trong ứng dụng, cùng transaction thanh toán.
     */
    private void notifyCustomer(
            Connection conn, Order order, String title, String content)
            throws SQLException {

        Integer accountId = paymentDAO.findAccountForCustomer(
                conn, order.getHolderCustomerId());

        if (accountId != null) {
            int notificationId
                    = notificationDAO.insertSystemNotification(
                            conn, title, content);

            notificationDAO.insertRecipient(
                    conn, notificationId, accountId);
        }
    }

    /**
     * Một cập nhật quan trọng phải tác động đúng một dòng.
     */
    private void requireOne(int rows) throws SQLException {
        if (rows != 1) {
            throw new SQLException(
                    "Dữ liệu đã thay đổi trong lúc xử lý IPN");
        }
    }

    private IpnResult reply(String code, String message) {
        System.out.println("[IPN] trả " + code + " - " + message);
        return new IpnResult(code, message);
    }

    /**
     * Merchant txnRef của GMS: mã đơn bỏ dấu gạch + A + số lần thử.
     */
    private String parseTxnRef(String ref) {
        if (ref == null
                || ref.length() > 50
                || !ref.matches("[A-Za-z0-9]+A[1-9][0-9]*")) {
            return null;
        }

        return ref;
    }

    /**
     * So vnp_Amount với số tiền VND nhân 100. Giá trị lỗi hoặc tràn số đều
     * không hợp lệ.
     */
    private boolean matchesAmount(String amountInXu, long expectedVnd) {
        try {
            return amountInXu != null
                    && amountInXu.matches("[0-9]+")
                    && Long.parseLong(amountInXu)
                    == Math.multiplyExact(expectedVnd, 100L);

        } catch (NumberFormatException | ArithmeticException e) {
            return false;
        }
    }

    protected Connection openConnection() throws SQLException {
        return DBContext.getConnection();
    }

    /**
     * Kiểm chữ ký Return và quyền sở hữu đơn. Chỉ trả TransactionId để chuyển
     * sang URL kết quả gọn. Không cập nhật trạng thái thanh toán.
     */
    public ServiceResult<Integer> resolveReturnTransaction(
            int accountId, Map<String, String> params) {

        if (accountId <= 0) {
            return ServiceResult.fail("Phiên đăng nhập không hợp lệ.");
        }

        try {
            // B1: không tin dữ liệu chưa được kiểm chữ ký.
            if (!vnPayClient.verifySignature(params)) {
                return ServiceResult.fail(
                        "Không xác minh được dữ liệu trả về từ VNPay.");
            }

            String txnRef = parseTxnRef(params.get("vnp_TxnRef"));

            if (txnRef == null) {
                return ServiceResult.fail(
                        "Mã lần thanh toán không hợp lệ.");
            }

            try (Connection conn = openConnection()) {
                // B2: tìm lần thanh toán theo mã đã gửi sang VNPay.
                Transaction payment
                        = transactionDAO.findByGatewayReference(conn, txnRef);

                if (payment == null || !payment.isOnlineGateway()) {
                    return ServiceResult.fail(
                            "Không tìm thấy giao dịch.");
                }

                // B3: kiểm đơn thuộc khách đang đăng nhập.
                Order order = orderDAO.findOnlineByIdAndAccount(
                        conn, accountId, payment.getOrderId());

                if (order == null) {
                    return ServiceResult.fail(
                            "Không tìm thấy đơn hàng của bạn.");
                }

                return ServiceResult.ok(payment.getTransactionId());
            }

        } catch (SQLException | RuntimeException e) {
            return ServiceResult.fail(
                    "Chưa đọc được kết quả thanh toán. Vui lòng thử lại.");
        }
    }

    /**
     * Đọc kết quả của đúng tài khoản. Không đánh Paid theo ResponseCode do
     * trình duyệt gửi.
     */
    public ServiceResult<PaymentResultView> getPaymentResult(
            int accountId, Integer transactionId) {

        if (accountId <= 0
                || transactionId == null
                || transactionId <= 0) {

            return ServiceResult.fail("Mã giao dịch không hợp lệ.");
        }

        try (Connection conn = openConnection()) {
            // B1: đọc giao dịch và kiểm chủ đơn.
            Transaction payment
                    = transactionDAO.findById(conn, transactionId, false);

            if (payment == null || !payment.isOnlineGateway()) {
                return ServiceResult.fail("Không tìm thấy giao dịch.");
            }

            Order order = orderDAO.findOnlineByIdAndAccount(
                    conn, accountId, payment.getOrderId());

            if (order == null) {
                return ServiceResult.fail(
                        "Không tìm thấy đơn hàng của bạn.");
            }

            // B2: hiển thị theo dữ liệu đã được ghi trong DB.
            if (payment.isNeedsRefund()) {
                return ServiceResult.ok(new PaymentResultView(
                        order,
                        "Giao dịch cần đối soát",
                        "Giao dịch đã được đánh dấu cần hoàn tiền. "
                        + "Vui lòng liên hệ lễ tân.",
                        false,
                        false,
                        false));
            }

            if (order.isPaid()) {
                PaymentResultView paid = new PaymentResultView(
                        order,
                        "Thanh toán thành công",
                        "GMS đã xác nhận thanh toán và cấp gói cho bạn.",
                        true,
                        false,
                        false);
                paid.setStartPicker(readStartPicker(conn, order));
                return ServiceResult.ok(paid);
            }

            if (order.isCancelled()) {
                return ServiceResult.ok(new PaymentResultView(
                        order,
                        "Đơn hàng đã hủy",
                        "Bạn có thể quay lại chọn gói để tạo đơn mới.",
                        false,
                        false,
                        false));
            }

            if (payment.isFailed() || payment.isExpired()) {
                boolean retryable = order.isPending()
                        && order.getPaymentDueAt() != null
                        && DateTimeUtil.now().isBefore(order.getPaymentDueAt())
                        && !paymentDAO.hasPendingTransaction(
                                conn, order.getOrderId());

                return ServiceResult.ok(new PaymentResultView(
                        order,
                        "Thanh toán chưa thành công",
                        retryable
                                ? "Bạn có thể thanh toán lại khi đơn còn hạn."
                                : "Đơn hiện không được thanh toán lại.",
                        false,
                        false,
                        retryable));
            }

            // Return có thể đến trước IPN; chưa có xác nhận thì chưa báo Paid.
            return ServiceResult.ok(new PaymentResultView(
                    order,
                    "Đang chờ xác nhận thanh toán",
                    "Hệ thống đang tự xác minh kết quả với VNPay. "
                    + "Trang sẽ tự cập nhật.",
                    false,
                    true,
                    false));

        } catch (SQLException | RuntimeException e) {
            return ServiceResult.fail(
                    "Chưa đọc được kết quả thanh toán. Vui lòng thử lại.");
        }
    }

    /** Lỗi đọc ngày bắt đầu không được làm hỏng trang kết quả; chỉ ẩn ô chọn ngày. */
    private StartDatePicker readStartPicker(Connection conn, Order order) {
        try {
            return startDateService.pickerForPaidOrder(conn, order);
        } catch (SQLException | RuntimeException e) {
            return null;
        }
    }

    /**
     * Nhận kết quả QueryDR từ job. Chỉ chuyển kết quả thanh toán thành công đã
     * xác minh sang phần xử lý DB dùng chung.
     */
    public IpnResult handleQueryResult(Map<String, String> params) {
        try {
            if (!vnPayClient.verifyQuerySignature(params)) {
                return reply("97", "Invalid query signature");
            }

            if (!"querydr".equals(params.get("vnp_Command"))
                    || !AppConfig.get("vnpay.tmn-code")
                            .equals(params.get("vnp_TmnCode"))) {
                return reply("99", "Invalid query response");
            }

            if (!"00".equals(params.get("vnp_ResponseCode"))
                    || !"00".equals(params.get("vnp_TransactionStatus"))
                    || !"01".equals(params.get("vnp_TransactionType"))) {
                return reply("99", "Payment not confirmed");
            }

            return processVerifiedResult(params);

        } catch (RuntimeException e) {
            return reply("99", "Query processing error");
        }
    }
}
