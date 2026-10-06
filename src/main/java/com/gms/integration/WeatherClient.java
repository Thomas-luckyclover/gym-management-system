package com.gms.integration;

import com.gms.dto.WeatherForecast;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;

/**
 * Cổng gọi Open-Meteo (miễn phí, không cần API key) để lấy thời tiết hôm nay.
 * Timeout 3 giây; WeatherService cache 30 phút nên phần lớn lần mở trang không phải gọi API.
 */
public class WeatherClient {

    private static final String FORECAST_URL = "https://api.open-meteo.com/v1/forecast";

    /** Chờ kết nối và chờ trả lời tối đa 3 giây mỗi bước. */
    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    /** Dùng chung 1 HttpClient cho mọi lần gọi (HttpClient an toàn khi nhiều luồng dùng cùng lúc). */
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .build();

    /**
     * Lấy thời tiết hôm nay tại 1 toạ độ.
     * Lỗi (mất mạng, quá 3 giây, JSON sai) ném WeatherUnavailableException.
     */
    public WeatherForecast fetchToday(double latitude, double longitude) throws WeatherUnavailableException {
        // B1: ghép URL — Locale.ROOT để số thập phân luôn dùng dấu chấm (21.0285, không phải 21,0285)
        String url = String.format(Locale.ROOT,
                "%s?latitude=%.4f&longitude=%.4f"
                + "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,is_day"
                + "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max"
                + "&timezone=Asia%%2FHo_Chi_Minh&forecast_days=1",
                FORECAST_URL, latitude, longitude);

        try {
            // B2: gửi GET, chờ tối đa 3 giây
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            // B3: API trả mã lỗi (VD 429 gọi quá nhiều, 503 bảo trì) → coi như không có thời tiết
            if (response.statusCode() != 200) {
                throw new WeatherUnavailableException("Open-Meteo trả HTTP " + response.statusCode(), null);
            }

            // B4: đọc JSON, lấy đúng các ô cần dùng
            return parseForecast(response.body());

        } catch (IOException | JsonParseException | IllegalStateException | NullPointerException e) {
            // Mất mạng / quá giờ / JSON thiếu ô → gói chung thành 1 loại lỗi cho Service xử lý
            throw new WeatherUnavailableException("Không đọc được thời tiết: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            // Tomcat đang tắt luồng này → giữ lại cờ "bị ngắt" cho luồng, rồi báo lỗi như thường
            Thread.currentThread().interrupt();
            throw new WeatherUnavailableException("Bị ngắt khi chờ thời tiết", e);
        }
    }

    /**
     * Đọc chuỗi JSON của Open-Meteo thành WeatherForecast.
     *
     * @param json nội dung trả về, dạng {"current":{...},"daily":{"weather_code":[96],...}}.
     * @return số liệu thô của hôm nay.
     */
    private WeatherForecast parseForecast(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonObject current = root.getAsJsonObject("current");
        JsonObject daily = root.getAsJsonObject("daily");

        return new WeatherForecast(
                current.get("temperature_2m").getAsDouble(),
                current.get("apparent_temperature").getAsDouble(),
                current.get("relative_humidity_2m").getAsInt(),
                current.get("weather_code").getAsInt(),
                firstInt(daily, "weather_code"),
                firstDouble(daily, "temperature_2m_max"),
                firstDouble(daily, "temperature_2m_min"),
                firstInt(daily, "precipitation_probability_max"),
                current.get("is_day").getAsInt() == 1);
    }

    /** Lấy phần tử đầu (= hôm nay) của 1 mảng số nguyên trong khối "daily". */
    private int firstInt(JsonObject daily, String key) {
        JsonArray values = daily.getAsJsonArray(key);
        return values.get(0).isJsonNull() ? 0 : values.get(0).getAsInt();
    }

    /** Lấy phần tử đầu (= hôm nay) của 1 mảng số thực trong khối "daily". */
    private double firstDouble(JsonObject daily, String key) {
        return daily.getAsJsonArray(key).get(0).getAsDouble();
    }
}
