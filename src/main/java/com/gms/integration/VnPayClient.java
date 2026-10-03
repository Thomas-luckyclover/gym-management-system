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

/**
 * Cổng thanh toán VNPay (API 2.1.0).
 * Tạo URL thanh toán có chữ ký HMAC-SHA512, kiểm tra chữ ký kết quả VNPay trả về (IPN và return URL).
 * Chỉ IPN mới đánh dấu đơn đã thanh toán; return URL chỉ hiển thị kết quả.
 */
public class VnPayClient {

    // ===== Hằng số theo tài liệu VNPay 2.1.0 =====

    private static final String VNP_VERSION = "2.1.0";
    private static final String VNP_COMMAND_PAY = "pay";
    private static final String VNP_CURRENCY = "VND";
    private static final String VNP_LOCALE = "vn";
    private static final String VNP_ORDER_TYPE = "other";
    private static final String HMAC_ALGORITHM = "HmacSHA512";

    /** VNPay yêu cầu thời gian dạng yyyyMMddHHmmss theo giờ Việt Nam (GMT+7). */
    private static final DateTimeFormatter VNP_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /** Tên tham số chữ ký — phải loại ra trước khi tự tính lại chữ ký để so sánh. */
    public static final String PARAM_SECURE_HASH = "vnp_SecureHash";
    public static final String PARAM_SECURE_HASH_TYPE = "vnp_SecureHashType";

    /** "00" ở cả vnp_ResponseCode và vnp_TransactionStatus nghĩa là giao dịch thành công. */
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

        // B2: gom tham số — TreeMap tự sắp xếp theo tên (a→z), đúng yêu cầu ký của VNPay
        Map<String, String> params = new TreeMap<>();
        params.put("vnp_Version", VNP_VERSION);
        params.put("vnp_Command", VNP_COMMAND_PAY);
        params.put("vnp_TmnCode", tmnCode);
        // VNPay tính tiền theo đơn vị "xu": 100.000đ phải gửi 10000000
        params.put("vnp_Amount", String.valueOf(amountVnd * 100));
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
     * Kiểm tra chữ ký VNPay gửi về có hợp lệ không — chống giả mạo kết quả thanh toán.
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
     * Đọc số tiền VNPay báo về (đổi từ "xu" về VND) để so với Transaction.Amount.
     * Trả -1 nếu thiếu hoặc sai định dạng.
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
     * Tạo JSON trả lời IPN cho VNPay, VD {"RspCode":"00","Message":"Confirm Success"}.
     */
    public String buildIpnResponse(String rspCode, String message) {
        return "{\"RspCode\":\"" + rspCode + "\",\"Message\":\"" + message + "\"}";
    }

    // ===== Phần kỹ thuật ký số =====

    /**
     * Ghép tham số thành chuỗi "key1=value1&key2=value2..." theo thứ tự tên a→z, bỏ qua giá trị rỗng,
     * giá trị được mã hóa URL — đúng cách ký trong code mẫu Java chính thức của VNPay 2.1.0.
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
     * Bỏ dấu tiếng Việt và ký tự lạ: "Thanh toán gói 3 tháng" → "Thanh toan goi 3 thang".
     * VNPay yêu cầu vnp_OrderInfo chỉ gồm ký tự ASCII, có dấu sẽ lỗi chữ ký.
     */
    private String toAsciiText(String text) {
        String withoutD = text.replace('đ', 'd').replace('Đ', 'D');
        String decomposed = Normalizer.normalize(withoutD, Normalizer.Form.NFD);
        String withoutMarks = decomposed.replaceAll("\\p{M}", "");
        return withoutMarks.replaceAll("[^A-Za-z0-9 .,:_-]", "");
    }
}
