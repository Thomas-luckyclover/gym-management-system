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
 * Cổng lấy dự báo thời tiết — API Open-Meteo (https://open-meteo.com), miễn phí, KHÔNG cần API key.
 *
 * ===== TỔNG QUAN LUỒNG =====
 * GMS ──(HTTPS GET)──> api.open-meteo.com/v1/forecast?latitude=..&longitude=.. ──> JSON
 * Chỉ đọc 2 khối trong JSON trả về:
 *   "current" : nhiệt độ, cảm nhận, độ ẩm, mã thời tiết lúc này, ngày/đêm
 *   "daily"   : mã thời tiết cả ngày, nhiệt độ cao/thấp nhất, khả năng mưa cao nhất (mảng 1 phần tử = hôm nay)
 *
 * ===== VÌ SAO TIMEOUT NGẮN =====
 * Gọi API mất ~1–2 giây. Thời tiết chỉ là phần phụ của PT Dashboard, nên chờ tối đa 3 giây rồi bỏ;
 * WeatherService còn cache kết quả 30 phút để phần lớn lần mở trang không phải gọi API.
 *
 * Được gọi từ Service (WeatherService), không gọi thẳng từ Servlet (agents.md 6.2).
 *
 * @author Le Cong Minh
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
     * Lấy thời tiết hôm nay tại 1 địa điểm.
     *
     * @param latitude  vĩ độ, VD 21.0285 (Hà Nội).
     * @param longitude kinh độ, VD 105.8542 (Hà Nội).
     * @return số liệu thô của hôm nay (xem WeatherForecast).
     * @throws WeatherUnavailableException khi mất mạng, quá 3 giây, API trả mã lỗi hoặc JSON sai dạng.
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
