package com.gms.integration;

/** Báo WeatherService rằng không lấy được thời tiết; dashboard vẫn phải tải bình thường. */
public class WeatherUnavailableException extends Exception {

    private static final long serialVersionUID = 1L;

    public WeatherUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
