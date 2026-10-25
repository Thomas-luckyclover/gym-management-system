package com.gms.service;

import com.gms.dao.TransactionDAO;
import com.gms.dto.IpnResult;
import com.gms.entity.Transaction;
import com.gms.integration.VnPayClient;
import com.gms.util.AppConfig;
import com.gms.util.DBContext;
import com.gms.util.DateTimeUtil;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * Tự hỏi lại VNPay về giao dịch chưa có kết quả chắc chắn. Không cần session và
 * không giữ khóa DB khi chờ API.
 */
public class PaymentReconciliationService {

    // Cấu hình vận hành bản thử nghiệm của GMS.
    private static final int FIRST_QUERY_DELAY_MINUTES = 1;
    // Mỗi giao dịch chờ ít nhất 1 phút trước khi truy vấn lại.
    private static final long QUERY_INTERVAL_MILLIS = 60 * 1000L;
    private static final int MAX_QUERIES_PER_RUN = 20;

    private final TransactionDAO transactionDAO
            = new TransactionDAO();

    private final VnPayClient vnPayClient
            = new VnPayClient();

    private final PaymentCallbackService callbackService
            = new PaymentCallbackService();

    // Thời điểm truy vấn gần nhất, giữ trong bộ nhớ của Tomcat.
    private final Map<Integer, Long> lastQueryAt = new HashMap<>();

    /**
     * Scheduler gọi một lượt; không chạy chồng trên cùng instance.
     */
    public synchronized void runOnce() throws SQLException {
        if (!Boolean.parseBoolean(
                AppConfig.get("vnpay.query-enabled", "false"))) {
            return;
        }

        // B1: đọc danh sách rồi đóng connection.
        List<Integer> ids;

        try (Connection conn = DBContext.getConnection()) {
            ids = transactionDAO.findQueryCandidates(
                    conn,
                    DateTimeUtil.now()
                            .minusMinutes(FIRST_QUERY_DELAY_MINUTES));
        }

        // B2: xóa thông tin theo dõi của giao dịch đã xử lý xong.
        lastQueryAt.keySet().retainAll(new HashSet<>(ids));

        // Ưu tiên giao dịch chưa hỏi hoặc lâu chưa hỏi.
        ids.sort(Comparator
                .comparingLong((Integer id)
                        -> lastQueryAt.getOrDefault(id, 0L))
                .thenComparingInt(Integer::intValue));

        int queried = 0;

        for (Integer transactionId : ids) {
            if (Thread.currentThread().isInterrupted()) {
                return;
            }

            if (queried >= MAX_QUERIES_PER_RUN) {
                break;
            }

            long nowMillis = System.currentTimeMillis();
            Long previous = lastQueryAt.get(transactionId);

            if (previous != null
                    && nowMillis - previous < QUERY_INTERVAL_MILLIS) {
                continue;
            }

            // Lỗi mạng cũng phải chờ trước khi thử lại.
            lastQueryAt.put(transactionId, nowMillis);
            queried++;

            reconcileOne(transactionId);
        }
    }

    /**
     * Lỗi của một giao dịch không chặn những giao dịch còn lại.
     */
    private void reconcileOne(int transactionId) {
        Transaction payment;

        // B1: đọc lại vì IPN có thể vừa xử lý giao dịch.
        try (Connection conn = DBContext.getConnection()) {
            payment = transactionDAO.findById(
                    conn, transactionId, false);

            if (payment == null || !payment.isOnlineGateway()) {
                return;
            }

            boolean unresolved
                    = payment.getStatus() == Transaction.Status.PENDING
                    || payment.getStatus() == Transaction.Status.EXPIRED;

            if (!unresolved) {
                return;
            }
        } catch (SQLException e) {
            System.err.println(
                    "[QueryDR] Không đọc được transaction "
                    + transactionId);
            return;
        }

        try {
            // B2: gọi API sau khi connection DB đã đóng.
            // Client kiểm chữ ký, merchant và mã tham chiếu.
            Map<String, String> data = vnPayClient.queryTransaction(
                    payment.getGatewayReference(),
                    payment.getCreatedAt());

            String responseCode = data.get("vnp_ResponseCode");

            if (!"00".equals(responseCode)) {
                System.err.println(
                        "[QueryDR] Transaction " + transactionId
                        + ": query code=" + responseCode);
                return;
            }

            // Chỉ tiếp nhận loại giao dịch thanh toán.
            if (!"01".equals(data.get("vnp_TransactionType"))) {
                System.err.println(
                        "[QueryDR] Transaction " + transactionId
                        + ": cần đối soát loại giao dịch.");
                return;
            }

            String status = data.get("vnp_TransactionStatus");

            // Chưa xác nhận thành công thì chưa thay đổi DB.
            if (!"00".equals(status)) {
                System.out.println(
                        "[QueryDR] Transaction " + transactionId
                        + ": chưa xác nhận thành công, status=" + status);
                return;
            }

            // B3: dùng phần xử lý chung với IPN.
            // Phần này khóa DB, kiểm tiền và chống cấp gói hai lần.
            IpnResult result = callbackService.handleQueryResult(data);

            if ("00".equals(result.getCode())
                    || "02".equals(result.getCode())) {
                System.out.println(
                        "[QueryDR] Đã xử lý transaction "
                        + transactionId);
            } else {
                System.err.println(
                        "[QueryDR] Transaction " + transactionId
                        + ": cần kiểm tra, result=" + result.getCode());
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

        } catch (IOException | RuntimeException e) {
            System.err.println(
                    "[QueryDR] Chưa xác minh được transaction "
                    + transactionId + ": "
                    + e.getClass().getSimpleName());
        }
    }
}
