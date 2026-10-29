package com.gms.integration;

import com.gms.util.AppConfig;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Enumeration;
import java.util.Map;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import com.gms.util.DateTimeUtil;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.UUID;

/**
 * Cổng thanh toán VNPay (API 2.1.0). Tạo URL thanh toán có chữ ký HMAC-SHA512,
 * kiểm tra chữ ký kết quả VNPay trả về (IPN và return URL). Chỉ IPN mới đánh
 * dấu đơn đã thanh toán; return URL chỉ hiển thị kết quả.
 */
public class VnPayClient {

    // ===== Hằng số theo tài liệu VNPay 2.1.0 =====
    private static final String VNP_VERSION = "2.1.0";
    private static final String VNP_COMMAND_PAY = "pay";
    private static final String VNP_CURRENCY = "VND";
    private static final String VNP_LOCALE = "vn";
    private static final String VNP_ORDER_TYPE = "other";
    private static final String HMAC_ALGORITHM = "HmacSHA512";

    /**
     * VNPay yêu cầu thời gian dạng yyyyMMddHHmmss theo giờ Việt Nam (GMT+7).
     */
    private static final DateTimeFormatter VNP_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /**
     * Tên tham số chữ ký — phải loại ra trước khi tự tính lại chữ ký để so
     * sánh.
     */
    public static final String PARAM_SECURE_HASH = "vnp_SecureHash";
    public static final String PARAM_SECURE_HASH_TYPE = "vnp_SecureHashType";

    /**
     * "00" ở cả vnp_ResponseCode và vnp_TransactionStatus nghĩa là giao dịch
     * thành công.
     */
    public static final String CODE_SUCCESS = "00";

    // ===== Mã phản hồi IPN (RspCode) mà GMS trả lại cho VNPay — theo tài liệu VNPay =====
    public static final String IPN_CONFIRM_SUCCESS = "00";
    public static final String IPN_ORDER_NOT_FOUND = "01";
    public static final String IPN_ORDER_ALREADY_CONFIRMED = "02";
    public static final String IPN_INVALID_AMOUNT = "04";
    public static final String IPN_INVALID_SIGNATURE = "97";
    public static final String IPN_UNKNOWN_ERROR = "99";

    // ===== API-01: tạo URL thanh toán =====
    /**
     * Tạo URL thanh toán đã ký HMAC-SHA512, dùng để redirect khách sang VNPay.
     * Mỗi lần thanh toán phải có txnRef mới (VNPay từ chối mã trùng).
     */
    public String buildPaymentUrl(String txnRef, long amountVnd, String orderInfo, String clientIp,
            LocalDateTime createdAt, LocalDateTime expireAt) {

        // B1: đọc cấu hình merchant
        String tmnCode = AppConfig.get("vnpay.tmn-code");
        String hashSecret = AppConfig.get("vnpay.hash-secret");
        String payUrl = AppConfig.get("vnpay.pay-url");
        String returnUrl = AppConfig.get("vnpay.return-url");
        if (tmnCode == null || tmnCode.isBlank()
                || hashSecret == null || hashSecret.isBlank()
                || payUrl == null || payUrl.isBlank()
                || returnUrl == null || returnUrl.isBlank()) {

            throw new IllegalStateException("Thiếu cấu hình merchant VNPay");
        }
        // B2: gom tham số — TreeMap tự sắp xếp theo tên (a→z), đúng yêu cầu ký của VNPay
        Map<String, String> params = new TreeMap<>();
        params.put("vnp_Version", VNP_VERSION);
        params.put("vnp_Command", VNP_COMMAND_PAY);
        params.put("vnp_TmnCode", tmnCode);
        // VNPay tính tiền theo đơn vị "xu": 100.000đ phải gửi 10000000
        params.put("vnp_Amount", String.valueOf(Math.multiplyExact(amountVnd, 100L)));
        params.put("vnp_CurrCode", VNP_CURRENCY);
        params.put("vnp_TxnRef", txnRef);
        params.put("vnp_OrderInfo", toAsciiText(orderInfo));
        params.put("vnp_OrderType", VNP_ORDER_TYPE);
        params.put("vnp_Locale", VNP_LOCALE);
        params.put("vnp_ReturnUrl", returnUrl);
        params.put("vnp_IpAddr", clientIp);
        params.put("vnp_CreateDate", createdAt.format(VNP_DATE_FORMATTER));
        params.put("vnp_ExpireDate", expireAt.format(VNP_DATE_FORMATTER));

        // B3: tạo chuỗi dữ liệu cần ký và chuỗi query (2 chuỗi giống nhau vì đều mã hóa URL từng giá trị)
        String hashData = buildHashData(params);
        String queryString = hashData;

        // B4: ký HMAC-SHA512 bằng hashSecret, gắn chữ ký vào cuối URL
        String secureHash = hmacSha512(hashSecret, hashData);
        return payUrl + "?" + queryString + "&" + PARAM_SECURE_HASH + "=" + secureHash;
    }

    // ===== API-02 + trang return: kiểm tra dữ liệu VNPay gửi về =====
    /**
     * Lấy các tham số vnp_* từ request (IPN hoặc return URL) thành map.
     */
    public Map<String, String> extractVnpParams(HttpServletRequest request) {
        Map<String, String> params = new TreeMap<>();
        Enumeration<String> parameterNames = request.getParameterNames();

        while (parameterNames.hasMoreElements()) {
            String name = parameterNames.nextElement();
            boolean isVnpParam = name.startsWith("vnp_");
            if (isVnpParam) {
                params.put(name, request.getParameter(name));
            }
        }
        return params;
    }

    /**
     * Kiểm tra chữ ký VNPay gửi về có hợp lệ không — chống giả mạo kết quả
     * thanh toán.
     */
    public boolean verifySignature(Map<String, String> vnpParams) {
        // B1: lấy chữ ký VNPay gửi kèm
        String receivedHash = vnpParams.get(PARAM_SECURE_HASH);
        if (receivedHash == null) {
            return false;
        }

        // B2: bỏ 2 tham số chữ ký ra, phần còn lại chính là dữ liệu VNPay đã ký
        Map<String, String> dataParams = new TreeMap<>(vnpParams);
        dataParams.remove(PARAM_SECURE_HASH);
        dataParams.remove(PARAM_SECURE_HASH_TYPE);

        // B3: tự ký lại bằng hashSecret của mình theo đúng cách VNPay ký
        String hashSecret = AppConfig.get("vnpay.hash-secret");
        String hashData = buildHashData(dataParams);
        String expectedHash = hmacSha512(hashSecret, hashData);

        // B4: so sánh kiểu "thời gian hằng" (MessageDigest.isEqual) để không lộ thông tin qua thời gian phản hồi
        byte[] expectedBytes = expectedHash.toLowerCase().getBytes(StandardCharsets.US_ASCII);
        byte[] receivedBytes = receivedHash.toLowerCase().getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expectedBytes, receivedBytes);
    }

    /**
     * Giao dịch thành công không — chỉ gọi sau khi verifySignature trả true.
     * Thành công khi cả vnp_ResponseCode và vnp_TransactionStatus đều = "00".
     */
    public boolean isPaymentSuccess(Map<String, String> vnpParams) {
        String responseCode = vnpParams.get("vnp_ResponseCode");
        String transactionStatus = vnpParams.get("vnp_TransactionStatus");
        boolean isResponseOk = CODE_SUCCESS.equals(responseCode);
        boolean isStatusOk = CODE_SUCCESS.equals(transactionStatus);
        return isResponseOk && isStatusOk;
    }

    /**
     * Đọc số tiền VNPay báo về (đổi từ "xu" về VND) để so với
     * Transaction.Amount. Trả -1 nếu thiếu hoặc sai định dạng.
     */
    public long getAmountVnd(Map<String, String> vnpParams) {
        String amountValue = vnpParams.get("vnp_Amount");
        try {
            long amountInXu = Long.parseLong(amountValue);
            return amountInXu / 100;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Tạo JSON trả lời IPN cho VNPay, VD {"RspCode":"00","Message":"Confirm
     * Success"}.
     */
    public String buildIpnResponse(String rspCode, String message) {
        return "{\"RspCode\":\"" + rspCode + "\",\"Message\":\"" + message + "\"}";
    }

    // ===== Phần kỹ thuật ký số =====
    /**
     * Ghép tham số thành chuỗi "key1=value1&key2=value2..." theo thứ tự tên
     * a→z, bỏ qua giá trị rỗng, giá trị được mã hóa URL — đúng cách ký trong
     * code mẫu Java chính thức của VNPay 2.1.0.
     */
    private String buildHashData(Map<String, String> sortedParams) {
        StringBuilder hashData = new StringBuilder();

        for (Map.Entry<String, String> entry : sortedParams.entrySet()) {
            String value = entry.getValue();
            boolean isEmptyValue = value == null || value.isEmpty();
            if (isEmptyValue) {
                continue;
            }
            if (hashData.length() > 0) {
                hashData.append('&');
            }
            hashData.append(URLEncoder.encode(entry.getKey(), StandardCharsets.US_ASCII));
            hashData.append('=');
            hashData.append(URLEncoder.encode(value, StandardCharsets.US_ASCII));
        }
        return hashData.toString();
    }

    /**
     * Tính HMAC-SHA512, trả về chuỗi hex chữ thường (128 ký tự).
     */
    private String hmacSha512(String secretKey, String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, HMAC_ALGORITHM);
            mac.init(keySpec);
            byte[] hashBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));

            StringBuilder hex = new StringBuilder();
            for (byte hashByte : hashBytes) {
                hex.append(String.format("%02x", hashByte));
            }
            return hex.toString();

        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            // Chỉ xảy ra khi JDK hỏng hoặc hashSecret rỗng — lỗi cấu hình, không phải lỗi người dùng
            throw new IllegalStateException("Không ký được dữ liệu VNPay, kiểm tra vnpay.hash-secret", e);
        }
    }

    /**
     * Bỏ dấu tiếng Việt và ký tự lạ: "Thanh toán gói 3 tháng" → "Thanh toan goi
     * 3 thang". VNPay yêu cầu vnp_OrderInfo chỉ gồm ký tự ASCII, có dấu sẽ lỗi
     * chữ ký.
     */
    private String toAsciiText(String text) {
        String withoutD = text.replace('đ', 'd').replace('Đ', 'D');
        String decomposed = Normalizer.normalize(withoutD, Normalizer.Form.NFD);
        String withoutMarks = decomposed.replaceAll("\\p{M}", "");
        return withoutMarks.replaceAll("[^A-Za-z0-9 .,:_-]", "");
    }
    // ===== QueryDR: GMS chủ động hỏi kết quả thanh toán =====

    private static final HttpClient QUERY_HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * Truy vấn một lần thanh toán đã tạo.
     *
     * txnRef: Transactions.GatewayReference. paymentCreatedAt: thời điểm đã gửi
     * trong vnp_CreateDate lúc tạo URL pay.
     *
     * Không mở transaction DB trong khi gọi API.
     */
    public Map<String, String> queryTransaction(
            String txnRef,
            LocalDateTime paymentCreatedAt)
            throws IOException, InterruptedException {

        if (txnRef == null || txnRef.isBlank()
                || paymentCreatedAt == null) {
            throw new IllegalArgumentException(
                    "Thiếu thông tin lần thanh toán.");
        }

        // B1: chuẩn bị dữ liệu QueryDR.
        Map<String, String> body = new LinkedHashMap<>();

        body.put("vnp_RequestId",
                UUID.randomUUID().toString().replace("-", ""));
        body.put("vnp_Version", "2.1.0");
        body.put("vnp_Command", "querydr");
        body.put("vnp_TmnCode", AppConfig.get("vnpay.tmn-code"));
        body.put("vnp_TxnRef", txnRef);

        // Phải dùng thời gian giao dịch gốc, không dùng now().
        body.put("vnp_TransactionDate",
                paymentCreatedAt.format(VNP_DATE_FORMATTER));

        body.put("vnp_CreateDate",
                DateTimeUtil.now().format(VNP_DATE_FORMATTER));
        body.put("vnp_IpAddr", AppConfig.get("vnpay.server-ip"));
        body.put("vnp_OrderInfo", "Query payment " + txnRef);

        // B2: QueryDR ký chuỗi có thứ tự cố định, phân cách bằng |.
        String signData = joinQueryFields(body,
                "vnp_RequestId",
                "vnp_Version",
                "vnp_Command",
                "vnp_TmnCode",
                "vnp_TxnRef",
                "vnp_TransactionDate",
                "vnp_CreateDate",
                "vnp_IpAddr",
                "vnp_OrderInfo");

        body.put(PARAM_SECURE_HASH,
                hmacSha512(
                        AppConfig.get("vnpay.hash-secret"),
                        signData));

        JsonObject jsonBody = new JsonObject();
        body.forEach(jsonBody::addProperty);

        // B3: gửi POST JSON tới VNPay.
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(AppConfig.get("vnpay.query-url")))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(
                        jsonBody.toString(),
                        StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = QUERY_HTTP.send(
                request,
                HttpResponse.BodyHandlers.ofString(
                        StandardCharsets.UTF_8));

        if (response.statusCode() != 200) {
            throw new IOException(
                    "VNPay chưa trả được kết quả QueryDR.");
        }

        // B4: đọc JSON thành Map.
        Map<String, String> result = new LinkedHashMap<>();

        try {
            JsonObject json = JsonParser.parseString(response.body())
                    .getAsJsonObject();

            for (Map.Entry<String, JsonElement> entry
                    : json.entrySet()) {

                JsonElement value = entry.getValue();

                result.put(
                        entry.getKey(),
                        value.isJsonNull() ? "" : value.getAsString());
            }
        } catch (RuntimeException e) {
            throw new IOException(
                    "Phản hồi QueryDR không đúng định dạng.", e);
        }

        // B5: chỉ trả dữ liệu đã kiểm chữ ký và đúng giao dịch yêu cầu.
        if (!verifyQuerySignature(result)) {
            throw new IOException(
                    "Chữ ký QueryDR không hợp lệ.");
        }

        if (!"querydr".equals(result.get("vnp_Command"))
                || !AppConfig.get("vnpay.tmn-code")
                        .equals(result.get("vnp_TmnCode"))
                || !txnRef.equals(result.get("vnp_TxnRef"))) {
            throw new IOException(
                    "Phản hồi QueryDR không khớp yêu cầu.");
        }

        return result;
    }

    /**
     * Kiểm chữ ký phản hồi QueryDR. Không dùng verifySignature() của IPN vì
     * cách ghép chuỗi khác nhau.
     */
    public boolean verifyQuerySignature(Map<String, String> data) {
        String received = data.get(PARAM_SECURE_HASH);

        if (received == null
                || !received.matches("[0-9a-fA-F]{128}")) {
            return false;
        }

        String signData = joinQueryFields(data,
                "vnp_ResponseId",
                "vnp_Command",
                "vnp_ResponseCode",
                "vnp_Message",
                "vnp_TmnCode",
                "vnp_TxnRef",
                "vnp_Amount",
                "vnp_BankCode",
                "vnp_PayDate",
                "vnp_TransactionNo",
                "vnp_TransactionType",
                "vnp_TransactionStatus",
                "vnp_OrderInfo",
                "vnp_PromotionCode",
                "vnp_PromotionAmount");

        String expected = hmacSha512(
                AppConfig.get("vnpay.hash-secret"),
                signData);

        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII),
                received.toLowerCase(Locale.ROOT)
                        .getBytes(StandardCharsets.US_ASCII));
    }

    /**
     * Giữ đúng thứ tự các trường khi ký. Trường không có giá trị vẫn giữ vị trí
     * rỗng.
     */
    private String joinQueryFields(
            Map<String, String> data,
            String... fields) {

        String[] values = new String[fields.length];

        for (int i = 0; i < fields.length; i++) {
            String value = data.get(fields[i]);
            values[i] = value == null ? "" : value;
        }

        return String.join("|", values);
    }
}
