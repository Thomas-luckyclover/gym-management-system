package com.gms.integration;

/**
 * Không lấy được dự báo thời tiết (mất mạng, API chậm quá 3 giây, API đổi định dạng...).
 *
 * Thời tiết chỉ là phần "trang trí" trên PT Dashboard, nên người gọi bắt lỗi này rồi ẩn thẻ thời tiết,
 * KHÔNG được làm hỏng cả trang.
 *
 * @author Le Cong Minh
 */
public class WeatherUnavailableException extends Exception {

    /**
     * @param message mô tả ngắn cho dev, VD "Open-Meteo trả HTTP 503".
     * @param cause   lỗi gốc (IOException, lỗi đọc JSON...), có thể null.
     */
    public WeatherUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
