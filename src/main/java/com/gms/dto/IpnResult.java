package com.gms.dto;

/** Kết quả nghiệp vụ IPN; Servlet trả đúng hai key RspCode/Message cho VNPay. */
public class IpnResult {
    private final String code;
    private final String message;

    public IpnResult(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
