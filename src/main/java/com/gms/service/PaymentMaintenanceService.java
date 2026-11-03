package com.gms.service;

import com.gms.dao.NotificationDAO;
import com.gms.dao.OrderDAO;
import com.gms.dao.PaymentDAO;
import com.gms.dao.TransactionDAO;
import com.gms.entity.Order;
import com.gms.entity.Transaction;
import com.gms.util.DBContext;
import com.gms.util.AppConfig;
import com.gms.util.DateTimeUtil;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import com.gms.integration.VnPayClient;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * SERVICE CỦA JOB-05 VÀ JOB-06 — dọn các thanh toán online quá hạn (SRS 13.5, 13.6).
 *
 * JOB-06 expireTimedOutTransactions  giao dịch Pending quá ExpiresAt -> Expired; đơn vẫn Pending
 *                                    để khách bấm "Thanh toán lại" (CBR-69).
 * JOB-05 cancelOverdueOnlineOrders   đơn online Pending quá PaymentDueAt và chưa có Success
 *                                    -> Cancelled/PaymentTimeout, đóng giao dịch Pending, nhả slot,
 *                                    trả voucher, thông báo khách (CBR-70, BR-44).
 *
 * Mỗi bản ghi một transaction riêng, một bản ghi lỗi không chặn các bản ghi khác. JOB-06 hỏi VNPay (CBR-125) trước khi đóng giao dịch; JOB-05 không gọi VNPay..
 */
public class PaymentMaintenanceService {

    private final TransactionDAO transactionDAO;
    private final OrderDAO orderDAO;
    private final PaymentDAO paymentDAO;
    private final NotificationDAO notificationDAO;
    private final AuditLogService auditService;
    private final VnPayClient vnPayClient;
    private final PaymentCallbackService callbackService;

    public PaymentMaintenanceService() {
        this(new TransactionDAO(), new OrderDAO(), new PaymentDAO(),
                new NotificationDAO(), new AuditLogService(),
                new VnPayClient(), new PaymentCallbackService());
    }

    public PaymentMaintenanceService(
            TransactionDAO transactionDAO,
            OrderDAO orderDAO,
            PaymentDAO paymentDAO,
            NotificationDAO notificationDAO,
            AuditLogService auditService) {
        this(transactionDAO, orderDAO, paymentDAO, notificationDAO, auditService,
                new VnPayClient(), new PaymentCallbackService());
    }

    public PaymentMaintenanceService(
            TransactionDAO transactionDAO,
            OrderDAO orderDAO,
            PaymentDAO paymentDAO,
            NotificationDAO notificationDAO,
            AuditLogService auditService,
            VnPayClient vnPayClient,
            PaymentCallbackService callbackService) {
        this.transactionDAO = transactionDAO;
        this.orderDAO = orderDAO;
        this.paymentDAO = paymentDAO;
        this.notificationDAO = notificationDAO;
        this.auditService = auditService;
        this.vnPayClient = vnPayClient;
        this.callbackService = callbackService;
    }

// ===================== JOB-06: ĐÓNG GIAO DỊCH QUÁ HẠN LINK =====================

    /** ExpireTimedOutTransactionsJob gọi mỗi phút. Trả số giao dịch đã Expired. */
    public int expireTimedOutTransactions() throws SQLException {
        LocalDateTime now = DateTimeUtil.now();
        List<Integer> ids;

        try (Connection conn = openConnection()) {
            ids = transactionDAO.findExpiredPendingIds(conn, now);
        }

        int count = 0;
        for (int transactionId : ids) {
            // MỚI (CBR-125): hỏi VNPay trước khi đóng.
            if (keepForGateway(transactionId)) {
                continue;
            }
            if (expireOneTransaction(transactionId)) {
                count++;
            }
        }

        if (count > 0) {
            System.out.println("[JOB-06] Đã đóng " + count + " giao dịch quá hạn link.");
        }
        return count;
    }

    /**
     * CBR-125: hỏi VNPay về giao dịch Pending quá hạn link.
     *
     * @return true nếu giao dịch cần GIỮ NGUYÊN, không đóng lúc này:
     *         - VNPay báo đã thu tiền và đã xử lý như IPN, hoặc
     *         - giao dịch đã có người xử lý, hoặc
     *         - chưa hỏi được VNPay mà đơn vẫn còn hạn (phút sau thử lại).
     *         false nếu nên đóng Expired như cũ.
     */
    private boolean keepForGateway(int transactionId) {
        // Công tắc: tắt thì chạy như cũ.
        if (!"true".equalsIgnoreCase(AppConfig.get("vnpay.query-enabled", "false"))) {
            return false;
        }

        Transaction tx;
        LocalDateTime dueAt;

        // Đọc dữ liệu, không giữ kết nối khi gọi API.
        try (Connection conn = openConnection()) {
            tx = transactionDAO.findById(conn, transactionId, false);
            if (tx == null || !tx.isPending()) {
                return true;
            }
            Order order = orderDAO.findForCallback(conn, tx.getOrderId(), false);
            dueAt = order == null ? null : order.getPaymentDueAt();
        } catch (SQLException e) {
            System.err.println("[JOB-06] Không đọc được TransactionId=" + transactionId);
            e.printStackTrace();
            return true;
        }

        try {
            Map<String, String> result = vnPayClient.queryTransaction(
                    tx.getGatewayReference(), tx.getCreatedAt());

            // true: VNPay báo đã thu tiền và đã ghi nhận như IPN, không đóng.
            return callbackService.handleQueryResult(result);

        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            boolean beforeDeadline = dueAt != null
                    && DateTimeUtil.now().isBefore(dueAt);

            System.err.println("[JOB-06] Chưa hỏi được VNPay, TransactionId="
                    + transactionId
                    + (beforeDeadline ? ": thử lại phút sau" : ": quá hạn đơn, đóng luôn"));

            return beforeDeadline;
        }
    }

    private boolean expireOneTransaction(int transactionId) {
        try (Connection conn = openConnection()) {
            conn.setAutoCommit(false);

            try {
                // WHERE Status='Pending' trong DAO: IPN hoặc JOB-05 xử lý trước thì rows = 0.
                int rows = transactionDAO.markExpired(conn, transactionId);
                if (rows != 1) {
                    conn.rollback();
                    return false;
                }

                auditService.logInTransaction(
                        conn, null, "JOB-06",
                        "Transactions", transactionId,
                        "Status",
                        Transaction.Status.PENDING.getDbValue(),
                        Transaction.Status.EXPIRED.getDbValue(),
                        null);

                conn.commit();
                return true;

            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException | RuntimeException e) {
            System.err.println("[JOB-06] Lỗi TransactionId=" + transactionId);
            e.printStackTrace();
            return false;
        }
    }

    // ===================== JOB-05: HỦY ĐƠN ONLINE QUÁ HẠN =====================

    /** CancelOverdueOnlineOrdersJob gọi mỗi phút. Trả số đơn đã hủy. */
    public int cancelOverdueOnlineOrders() throws SQLException {
        LocalDateTime now = DateTimeUtil.now();
        List<Integer> ids;

        try (Connection conn = openConnection()) {
            ids = orderDAO.findOverdueOnlineIds(conn, now);
        }

        int count = 0;
        for (int orderId : ids) {
            if (cancelOneOrder(orderId)) {
                count++;
            }
        }

        if (count > 0) {
            System.out.println("[JOB-05] Đã hủy " + count + " đơn online quá hạn.");
        }
        return count;
    }

    /** Hủy một đơn trong MỘT transaction: voucher lỗi thì cả việc hủy cũng rollback. */
    private boolean cancelOneOrder(int orderId) {
        try (Connection conn = openConnection()) {
            conn.setAutoCommit(false);

            try {
                // B1: đọc đơn (chưa khóa) để biết chủ đơn, rồi khóa khách -> đơn.
                Order peek = orderDAO.findForCallback(conn, orderId, false);
                if (peek == null || !peek.isOnline()) {
                    conn.rollback();
                    return false;
                }

                paymentDAO.lockCustomer(conn, peek.getHolderCustomerId());
                Order order = orderDAO.findForCallback(conn, orderId, true);

                // B2: kiểm tra lại khi đã khóa — IPN có thể vừa chen vào.
                LocalDateTime now = DateTimeUtil.now().withNano(0);
                boolean stillOverdue = order != null
                        && order.isPending()
                        && order.getPaymentDueAt() != null
                        && order.getPaymentDueAt().isBefore(now);

                if (!stillOverdue || transactionDAO.hasSuccess(conn, orderId)) {
                    conn.rollback();
                    return false;
                }

                // B3: hủy đơn (WHERE Status='Pending' trong DAO).
                int cancelled = orderDAO.markCancelled(
                        conn, orderId, Order.CancelType.PAYMENT_TIMEOUT, null);
                if (cancelled != 1) {
                    conn.rollback();
                    return false;
                }

                // B4: đóng giao dịch Pending, nhả slot, trả voucher (BR-44).
                transactionDAO.expirePendingByOrder(conn, orderId);
                paymentDAO.releaseHeldSlots(conn, orderId);

                if (order.getVoucherId() != null
                        && paymentDAO.returnVoucherUse(conn, order.getVoucherId()) != 1) {
                    throw new SQLException("Không trả được lượt voucher của đơn " + orderId);
                }

                // B5: nhật ký + thông báo trong CÙNG transaction.
                auditService.logInTransaction(
                        conn, null, "JOB-05",
                        "Orders", orderId,
                        "Status",
                        Order.Status.PENDING.getDbValue(),
                        Order.Status.CANCELLED.getDbValue(),
                        Order.CancelType.PAYMENT_TIMEOUT.getDbValue());

                notifyCustomer(conn, order);

                conn.commit();
                return true;

            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException | RuntimeException e) {
            System.err.println("[JOB-05] Lỗi OrderId=" + orderId);
            e.printStackTrace();
            return false;
        }
    }

    private void notifyCustomer(Connection conn, Order order) throws SQLException {
        Integer accountId = paymentDAO.findAccountForCustomer(conn, order.getHolderCustomerId());
        if (accountId == null) {
            return;
        }

        int notificationId = notificationDAO.insertSystemNotification(
                conn,
                "Đơn hàng đã hết hạn thanh toán",
                "Đơn " + order.getOrderCode() + " đã bị hủy do quá hạn thanh toán. "
                + "Bạn có thể chọn lại gói để tạo đơn mới.");

        notificationDAO.insertRecipient(conn, notificationId, accountId);
    }

    /** protected để class test kế thừa và thay cách mở kết nối. */
    protected Connection openConnection() throws SQLException {
        return DBContext.getConnection();
    }
}