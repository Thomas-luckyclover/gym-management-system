package com.gms.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/**
 * Kiểm tra phần ký/kiểm chữ ký VNPay chạy đúng mà KHÔNG cần mạng hay tài khoản sandbox.
 * Dùng khóa giả trong src/test/resources/application.properties.
 */
class VnPayClientTest {

    private final VnPayClient client = new VnPayClient();

    /** Tách query của URL thanh toán thành map (giống cách Tomcat giải mã tham số khi VNPay gọi về). */
    private Map<String, String> parseQuery(String url) {
        String query = url.substring(url.indexOf('?') + 1);
        Map<String, String> params = new TreeMap<>();
        for (String pair : query.split("&")) {
            String[] parts = pair.split("=", 2);
            String key = URLDecoder.decode(parts[0], StandardCharsets.US_ASCII);
            String value = URLDecoder.decode(parts[1], StandardCharsets.US_ASCII);
            params.put(key, value);
        }
        return params;
    }

    private String buildSampleUrl() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 9, 24, 14, 30, 0);
        return client.buildPaymentUrl("DH20260001A1", 3_990_000, "Thanh toán gói 3 tháng — đơn DH-2026-0001",
                "127.0.0.1", createdAt, createdAt.plusMinutes(5));
    }

    @Test
    void paymentUrlHasRequiredParams() {
        Map<String, String> params = parseQuery(buildSampleUrl());
        assertEquals("399000000", params.get("vnp_Amount"));
        assertEquals("20260924143000", params.get("vnp_CreateDate"));
        assertEquals("20260924143500", params.get("vnp_ExpireDate"));
        assertEquals("Thanh toan goi 3 thang  don DH-2026-0001", params.get("vnp_OrderInfo"));
        assertEquals(128, params.get("vnp_SecureHash").length());
    }

    @Test
    void signatureRoundTripIsValid() {
        Map<String, String> params = parseQuery(buildSampleUrl());
        assertTrue(client.verifySignature(params));
    }

    @Test
    void tamperedAmountIsRejected() {
        Map<String, String> params = parseQuery(buildSampleUrl());
        params.put("vnp_Amount", "100");
        assertFalse(client.verifySignature(params));
    }

    @Test
    void missingSignatureIsRejected() {
        Map<String, String> params = parseQuery(buildSampleUrl());
        params.remove(VnPayClient.PARAM_SECURE_HASH);
        assertFalse(client.verifySignature(params));
    }

    @Test
    void successAndAmountHelpers() {
        Map<String, String> params = new TreeMap<>();
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TransactionStatus", "00");
        params.put("vnp_Amount", "399000000");
        assertTrue(client.isPaymentSuccess(params));
        assertEquals(3_990_000, client.getAmountVnd(params));
        assertEquals("{\"RspCode\":\"97\",\"Message\":\"Invalid Checksum\"}",
                client.buildIpnResponse(VnPayClient.IPN_INVALID_SIGNATURE, "Invalid Checksum"));
    }
}
