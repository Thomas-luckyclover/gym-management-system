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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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
    // Dùng chung giữa servlet Return và job trong cùng ứng dụng Tomcat.
    private static final Map<Integer, Long> lastQueryAt
            = new ConcurrentHashMap<>();

// Không cho hai luồng cùng gọi QueryDR cho một giao dịch.
    private static final Set<Integer> querying
            = ConcurrentHashMap.newKeySet();

    /**
     * Scheduler gọi một lượt; không chạy chồng trên cùng instance.
     */
    public synchronized void runOnce() throws SQLException {
        if (!isQueryEnabled()) {
            return;
        }

        List<Integer> ids;

        try (Connection conn = DBContext.getConnection()) {
            ids = transactionDAO.findQueryCandidates(
                    conn,
                    DateTimeUtil.now()
                            .minusMinutes(FIRST_QUERY_DELAY_MINUTES));
        }

        // Dọn thông tin giới hạn tần suất đã cũ.
        // Giữ nguyên thông tin của giao dịch đang được truy vấn.
        long cleanupBefore
                = System.currentTimeMillis() - 24 * 60 * 60 * 1000L;

        lastQueryAt.forEach((id, timestamp) -> {
            if (timestamp < cleanupBefore && !querying.contains(id)) {
                lastQueryAt.remove(id, timestamp);
            }
        });

        // Chụp thời điểm để thứ tự sắp xếp không đổi
        // khi servlet Return đồng thời cập nhật lastQueryAt.
        Map<Integer, Long> queryTimes = new HashMap<>(lastQueryAt);

        ids.sort(Comparator
                .comparingLong((Integer id)
                        -> queryTimes.getOrDefault(id, 0L))
                .thenComparingInt(Integer::intValue));

        int queried = 0;

        for (Integer transactionId : ids) {
            if (Thread.currentThread().isInterrupted()) {
                return;
            }

            if (queried >= MAX_QUERIES_PER_RUN) {
                break;
            }

            if (tryReconcile(transactionId)) {
                queried++;
            }
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

    /**
     * Gọi từ servlet Return sau khi đã xác minh chữ ký và kiểm tra giao dịch
     * thuộc tài khoản đăng nhập.
     *
     * Không áp dụng điều kiện phải tạo được 1 phút như job. Vẫn chống gọi đồng
     * thời và giới hạn tần suất.
     */
    public void reconcileAfterReturn(int verifiedTransactionId) {
        if (verifiedTransactionId <= 0) {
            return;
        }

        if (!isQueryEnabled()) {
            System.err.println(
                    "[QueryDR] Bỏ qua Return vì query-enabled=false");
            return;
        }

        tryReconcile(verifiedTransactionId);
    }

    private boolean isQueryEnabled() {
        return Boolean.parseBoolean(
                AppConfig.get("vnpay.query-enabled", "false"));
    }

    /**
     * Trả true nếu đã nhận lượt xử lý. Không giữ khóa toàn bộ service trong lúc
     * chờ VNPay.
     */
    private boolean tryReconcile(int transactionId) {
        if (!querying.add(transactionId)) {
            return false;
        }

        try {
            long now = System.currentTimeMillis();
            Long previous = lastQueryAt.get(transactionId);

            if (previous != null
                    && now - previous < QUERY_INTERVAL_MILLIS) {
                return false;
            }

            // Ghi trước khi gọi để lỗi mạng cũng không bị gọi liên tục.
            lastQueryAt.put(transactionId, now);

            // Hàm này đọc lại trạng thái DB:
            // đã Success/Failed thì không gọi API nữa.
            reconcileOne(transactionId);

            return true;

        } finally {
            querying.remove(transactionId);
        }
    }
}
