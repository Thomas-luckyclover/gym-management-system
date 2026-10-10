package com.gms.integration;

import com.gms.dto.WeatherForecast;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** Gửi yêu cầu API-04 tới Open-Meteo cho khối thời tiết trên PT Dashboard. */
public class WeatherClient {

    private static final String FORECAST_URL = "https://api.open-meteo.com/v1/forecast";
    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    /** Gửi GET rồi đổi JSON thành các số liệu thô mà WeatherService cần. */
    public WeatherForecast fetchToday(double latitude, double longitude) throws WeatherUnavailableException {
        String json = fetchTodayJson(latitude, longitude);
        return parseForecast(json);
    }

    /**
     * Gửi GET lấy thời tiết hôm nay tại tọa độ phòng gym.
     *
     * @param latitude vĩ độ của phòng gym, ví dụ Hà Nội là 21.0285
     * @param longitude kinh độ của phòng gym, ví dụ Hà Nội là 105.8542
     * @return JSON gốc trong HTTP response body; bước sau mới đọc các field current/daily
     * @throws WeatherUnavailableException khi tọa độ sai, API lỗi hoặc quá thời gian chờ
     */
    public String fetchTodayJson(double latitude, double longitude) throws WeatherUnavailableException {
        if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90
                || !Double.isFinite(longitude) || longitude < -180 || longitude > 180) {
            throw new WeatherUnavailableException("Tọa độ phòng gym không hợp lệ.", null);
        }

        // B1: chọn đúng vị trí, các field API-04 cần và chỉ lấy hôm nay theo giờ Việt Nam.
        String url = FORECAST_URL
                + "?latitude=" + latitude
                + "&longitude=" + longitude
                + "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,is_day,wind_speed_10m"
                + "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max"
                + "&timezone=Asia%2FHo_Chi_Minh&forecast_days=1";
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(TIMEOUT)
                .GET()
                .build();

        try {
            // B2: chỉ tạo HttpClient khi thật sự gọi API; Service cache nên rất ít lần gửi GET.
            HttpClient httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            // B3: chỉ mã 200 có JSON thời tiết; mã khác sẽ để Service ẩn thẻ.
            if (response.statusCode() != 200) {
                throw new WeatherUnavailableException(
                        "Open-Meteo trả HTTP " + response.statusCode() + ".", null);
            }
            return response.body();
        } catch (IOException exception) {
            throw new WeatherUnavailableException("Không gửi hoặc đọc được phản hồi từ Open-Meteo.", exception);
        } catch (UncheckedIOException exception) {
            throw new WeatherUnavailableException("Không tạo được kết nối HTTP tới Open-Meteo.", exception);
        } catch (InterruptedException exception) {
            //InterruptedException nghĩa là luồng Java đang chờ API bị yêu cầu dừng, ví dụ server đang tắt. Thread.currentThread().interrupt() giữ lại dấu hiệu “luồng đã bị ngắt”, rồi code đổi thành WeatherUnavailableException để Service xử lý thống nhất.
            Thread.currentThread().interrupt();
            throw new WeatherUnavailableException("Yêu cầu thời tiết bị ngắt.", exception);
        }
    }

    /** Đọc hai nhóm current và daily[0]; JSON thiếu ô hoặc sai kiểu được báo là API lỗi. */
    WeatherForecast parseForecast(String json) throws WeatherUnavailableException {
        try {
            // B1: current chứa một giá trị cho thời điểm hiện tại.
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonObject current = root.getAsJsonObject("current");
            double currentTemperature = current.get("temperature_2m").getAsDouble();
            double feelsLikeTemperature = current.get("apparent_temperature").getAsDouble();
            int currentHumidityPercent = current.get("relative_humidity_2m").getAsInt();
            int currentWeatherCode = current.get("weather_code").getAsInt();
            int isDayValue = current.get("is_day").getAsInt();
            if (isDayValue != 0 && isDayValue != 1) {
                throw new IllegalStateException("current.is_day phải là 0 hoặc 1");
            }
            boolean isDay = isDayValue == 1;
            double currentWindSpeed = current.get("wind_speed_10m").getAsDouble();

            // B2: forecast_days=1 nên phần tử [0] của mỗi mảng daily là hôm nay.
            JsonObject daily = root.getAsJsonObject("daily");
            int dailyWeatherCode = daily.getAsJsonArray("weather_code").get(0).getAsInt();
            double dailyMaxTemperature = daily.getAsJsonArray("temperature_2m_max").get(0).getAsDouble();
            double dailyMinTemperature = daily.getAsJsonArray("temperature_2m_min").get(0).getAsDouble();
            int dailyMaxRainProbabilityPercent = daily.getAsJsonArray("precipitation_probability_max")
                    .get(0).getAsInt();

            if (!Double.isFinite(currentTemperature) || !Double.isFinite(feelsLikeTemperature)
                    || !Double.isFinite(currentWindSpeed) || currentWindSpeed < 0
                    || !Double.isFinite(dailyMaxTemperature) || !Double.isFinite(dailyMinTemperature)
                    || currentHumidityPercent < 0 || currentHumidityPercent > 100
                    || dailyMaxRainProbabilityPercent < 0 || dailyMaxRainProbabilityPercent > 100) {
                throw new IllegalStateException("Số liệu nhiệt độ hoặc phần trăm không hợp lệ");
            }

            // B3: giữ số liệu thô; Service mới chọn chữ và màu cho thẻ thời tiết.
            return new WeatherForecast(currentTemperature, feelsLikeTemperature,
                    currentHumidityPercent, currentWeatherCode, isDay, currentWindSpeed,
                    dailyWeatherCode, dailyMaxTemperature, dailyMinTemperature,
                    dailyMaxRainProbabilityPercent);
        } catch (RuntimeException exception) {
            // Các ô JSON do API bên ngoài cung cấp có thể thiếu, null hoặc sai kiểu.
            throw new WeatherUnavailableException("Open-Meteo trả JSON thời tiết không hợp lệ.", exception);
        }
    }
}
