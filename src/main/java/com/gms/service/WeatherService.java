package com.gms.service;

import com.gms.dto.WeatherForecast;
import com.gms.dto.WeatherView;
import com.gms.integration.WeatherClient;
import com.gms.integration.WeatherUnavailableException;
import com.gms.util.AppConfig;
import com.gms.util.DateTimeUtil;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Lấy thời tiết ở vị trí phòng gym và chuẩn bị một thẻ ngắn cho PT Dashboard.
 * Dùng chung kết quả thành công 30 phút; nếu API lỗi thì 5 phút sau mới thử lại.
 */
public class WeatherService {

    private static final Logger LOGGER = Logger.getLogger(WeatherService.class.getName());
    private static final int SUCCESS_CACHE_MINUTES = 30;
    private static final int FAILURE_RETRY_MINUTES = 5;
    private static final int RAIN_CHANCE_THRESHOLD = 50;
    private static final int HOT_MAX_TEMPERATURE = 35;
    private static final int HOT_FEELS_LIKE_TEMPERATURE = 39;
    private static final int EXTREME_HOT_MAX_TEMPERATURE = 38;
    private static final int EXTREME_HOT_FEELS_LIKE_TEMPERATURE = 42;
    private static final int STRONG_WIND_KMH = 35;

    private final WeatherClient weatherClient;
    private final Supplier<LocalDateTime> currentTime;
    private final String cityName;
    private final String latitudeText;
    private final String longitudeText;

    // Một instance Service được Servlet dùng chung cho các request PT.
    // cachedView = null và cachedUntil còn hạn nghĩa là API vừa lỗi, chưa nên gọi lại.
    private WeatherView cachedView;
    private LocalDate cachedDate;
    private LocalDateTime cachedUntil;

    public WeatherService() {
        this(new WeatherClient(), DateTimeUtil::now,
                AppConfig.get("weather.city", "Hà Nội"),
                AppConfig.get("weather.latitude", "21.0285"),
                AppConfig.get("weather.longitude", "105.8542"));
    }

    /** Nhận client, thời gian và vị trí riêng để kiểm tra cache mà không gọi API thật. */
    WeatherService(WeatherClient weatherClient, Supplier<LocalDateTime> currentTime,
            String cityName, String latitudeText, String longitudeText) {
        this.weatherClient = weatherClient;
        this.currentTime = currentTime;
        this.cityName = cityName;
        this.latitudeText = latitudeText;
        this.longitudeText = longitudeText;
    }

    /**
     * Trả thẻ thời tiết hôm nay tại gym, hoặc null để JSP ẩn thẻ khi API không dùng được.
     * synchronized: nhiều PT mở trang cùng lúc chỉ một request gọi Open-Meteo khi cache hết hạn.
     */
    public synchronized WeatherView getTodayWeather() {
        LocalDateTime now = currentTime.get();

        // B1: dùng lại kết quả trong ngày; không giữ thời tiết hôm qua qua mốc nửa đêm.
        if (cachedDate != null && cachedDate.equals(now.toLocalDate())
                && cachedUntil != null && now.isBefore(cachedUntil)) {
            return cachedView;
        }

        try {
            // B2: vị trí lấy từ cấu hình máy; WeatherClient gửi GET và đọc JSON thành Forecast.
            double latitude = Double.parseDouble(latitudeText);
            double longitude = Double.parseDouble(longitudeText);
            WeatherForecast forecast = weatherClient.fetchToday(latitude, longitude);

            // B3: đổi dữ liệu API thành chữ/kiểu thẻ; giữ 30 phút cho các PT khác.
            cachedView = toView(forecast, cityName);
            cachedDate = now.toLocalDate();
            cachedUntil = now.plusMinutes(SUCCESS_CACHE_MINUTES);
        } catch (WeatherUnavailableException | NumberFormatException exception) {
            // B4: log nguyên nhân kỹ thuật; trả null và đợi 5 phút trước lần thử tiếp theo.
            LOGGER.log(Level.WARNING, "Không lấy được thời tiết cho PT Dashboard.", exception);
            cachedView = null;
            cachedDate = now.toLocalDate();
            cachedUntil = now.plusMinutes(FAILURE_RETRY_MINUTES);
        }
        return cachedView;
    }

    /** Dữ liệu cố định để thử ?weather=rain; không gọi API và không ghi vào cache thật. */
    public WeatherView getPreview(String weatherType) {
        if (weatherType == null) {
            return null;
        }
        switch (weatherType) {
            case "sunny":
                return new WeatherView(WeatherView.Mood.SUNNY, cityName, "Trời quang",
                        "Trời quang", "Một ngày đẹp để giữ nhịp tập luyện.", "fa-sun", 32,
                        35, 55, 9, 26, 33, 5);
            case "cloudy":
                return new WeatherView(WeatherView.Mood.CLOUDY, cityName, "Nhiều mây",
                        "Trời nhiều mây", "Chuẩn bị buổi tập như kế hoạch.", "fa-cloud-sun", 27,
                        29, 68, 12, 24, 30, 30);
            case "rain":
                return new WeatherView(WeatherView.Mood.RAIN, cityName, "Mưa vừa",
                        "Trời đang mưa", "Nhắc khách mang ô và chú ý sàn ướt.", "fa-cloud-showers-heavy", 26,
                        28, 88, 18, 24, 29, 85);
            case "storm":
                return new WeatherView(WeatherView.Mood.STORM, cityName, "Giông",
                        "Trời đang có giông", "Nhắc khách đi lại cẩn thận.", "fa-cloud-bolt", 25,
                        27, 91, 36, 23, 28, 95);
            case "snow":
                return new WeatherView(WeatherView.Mood.SNOW, cityName, "Tuyết nhẹ",
                        "Trời đang có tuyết", "Chú ý đường trơn trước giờ tập.", "fa-snowflake", 0,
                        -3, 80, 11, -4, 2, 70);
            case "wind":
                return new WeatherView(WeatherView.Mood.WIND, cityName, "Gió mạnh",
                        "Hôm nay gió mạnh", "Nhắc khách đi lại cẩn thận.", "fa-wind", 28,
                        29, 62, 38, 25, 31, 15);
            case "night":
                return new WeatherView(WeatherView.Mood.NIGHT, cityName, "Trời quang",
                        "Tối nay trời quang", "Chuẩn bị dụng cụ trước giờ hẹn.", "fa-moon", 24,
                        25, 73, 7, 22, 30, 10);
            case "hot":
                return new WeatherView(WeatherView.Mood.HOT, cityName, "Nắng nóng",
                        "Hôm nay nắng nóng", "Nhắc khách uống nước và nghỉ đủ giữa các hiệp.", "fa-temperature-high", 36,
                        40, 58, 9, 29, 37, 5);
            case "extreme_hot":
                return new WeatherView(WeatherView.Mood.HOT, cityName, "Nắng gắt",
                        "Nắng nóng gay gắt", "Nhắc khách uống đủ nước và giảm cường độ tập khi cần.", "fa-temperature-high", 40,
                        44, 55, 9, 31, 41, 5);
            default:
                return null;
        }
    }

    /**
     * Chọn một kiểu thời tiết và câu nhắc từ WeatherForecast.
     * Mã WMO và số đo chọn câu mô tả/lời nhắc.
     * Ưu tiên giông/tuyết, mưa, gió mạnh; sau đó xét đêm, nóng, mây và trời quang.
     * Dự báo mưa chỉ bật hiệu ứng khi trên 50%; đang mưa thực tế vẫn dùng hiệu ứng mưa.
     */
    WeatherView toView(WeatherForecast forecast, String city) {
        int temperature = (int) Math.round(forecast.getCurrentTemperature());
        int maxTemperature = (int) Math.round(forecast.getDailyMaxTemperature());
        int minTemperature = (int) Math.round(forecast.getDailyMinTemperature());
        int feelsLikeTemperature = (int) Math.round(forecast.getFeelsLikeTemperature());
        int windSpeedKmh = (int) Math.round(forecast.getCurrentWindSpeed());
        int currentCode = forecast.getCurrentWeatherCode();
        int dailyCode = forecast.getDailyWeatherCode();

        String title;
        String advice;
        String iconClass;
        WeatherView.Mood mood;

        if (isStormCode(currentCode) || isStormCode(dailyCode)) {
            mood = WeatherView.Mood.STORM;
            title = isStormCode(currentCode) ? "Trời đang có giông" : "Hôm nay có thể có giông";
            advice = "Nhắc khách đi lại cẩn thận trước giờ tập.";
            iconClass = "fa-cloud-bolt";
        } else if (isSnowCode(currentCode) || isSnowCode(dailyCode)) {
            mood = WeatherView.Mood.SNOW;
            title = isSnowCode(currentCode) ? "Trời đang có tuyết" : "Hôm nay có thể có tuyết";
            advice = "Nhắc khách chú ý đường trơn trước giờ tập.";
            iconClass = "fa-snowflake";
        } else if (isRainCode(currentCode)
                || forecast.getDailyMaxRainProbabilityPercent() > RAIN_CHANCE_THRESHOLD) {
            mood = WeatherView.Mood.RAIN;
            title = isRainCode(currentCode) ? "Trời đang mưa" : "Hôm nay có thể mưa";
            advice = "Nhắc khách mang ô và để ý sàn ướt khi vào phòng tập.";
            iconClass = "fa-cloud-showers-heavy";
        } else if (forecast.getCurrentWindSpeed() >= STRONG_WIND_KMH) {
            mood = WeatherView.Mood.WIND;
            title = "Hôm nay gió mạnh";
            advice = "Nhắc khách đi lại cẩn thận trên đường tới phòng tập.";
            iconClass = "fa-wind";
        } else if (!forecast.isDay()) {
            mood = WeatherView.Mood.NIGHT;
            title = isCloudyCode(currentCode) ? "Đêm nay nhiều mây" : "Tối nay trời quang";
            advice = "Chuẩn bị dụng cụ trước giờ hẹn để buổi tập bắt đầu đúng giờ.";
            iconClass = "fa-moon";
        } else if (maxTemperature >= HOT_MAX_TEMPERATURE
                || feelsLikeTemperature >= HOT_FEELS_LIKE_TEMPERATURE) {
            mood = WeatherView.Mood.HOT;
            boolean extremeHeat = maxTemperature >= EXTREME_HOT_MAX_TEMPERATURE
                    || feelsLikeTemperature >= EXTREME_HOT_FEELS_LIKE_TEMPERATURE;
            title = extremeHeat ? "Nắng nóng gay gắt" : "Hôm nay nắng nóng";
            advice = extremeHeat
                    ? "Nhắc khách uống đủ nước và giảm cường độ tập khi cần."
                    : "Nhắc khách uống nước và nghỉ đủ giữa các hiệp.";
            iconClass = "fa-temperature-high";
        } else if (isCloudyCode(currentCode)) {
            mood = WeatherView.Mood.CLOUDY;
            title = "Trời nhiều mây";
            advice = "Thời tiết dễ chịu, chuẩn bị buổi tập như kế hoạch.";
            iconClass = "fa-cloud-sun";
        } else {
            mood = WeatherView.Mood.SUNNY;
            title = "Trời quang";
            advice = "Duy trì lịch tập như kế hoạch hôm nay.";
            iconClass = "fa-sun";
        }

        return new WeatherView(mood, city, describeCode(currentCode), title,
                advice, iconClass, temperature, feelsLikeTemperature,
                forecast.getCurrentHumidityPercent(), windSpeedKmh, minTemperature,
                maxTemperature, forecast.getDailyMaxRainProbabilityPercent());
    }

    /** Mã 95–99 mô tả giông, có thể kèm mưa đá. */
    private boolean isStormCode(int code) {
        return code >= 95 && code <= 99;
    }

    /** Mã mưa phùn, mưa thường và mưa rào của Open-Meteo. */
    private boolean isRainCode(int code) {
        return (code >= 51 && code <= 67) || (code >= 80 && code <= 82);
    }

    /** Mã tuyết rơi, hạt tuyết hoặc tuyết rào trong bảng WMO của Open-Meteo. */
    private boolean isSnowCode(int code) {
        return (code >= 71 && code <= 77) || code == 85 || code == 86;
    }

    /** Mã ít/nhiều mây hoặc sương mù. */
    private boolean isCloudyCode(int code) {
        return (code >= 2 && code <= 3) || code == 45 || code == 48;
    }

    /** Đổi mã thời tiết hiện tại thành chữ ngắn để JSP in cạnh tên thành phố. */
    private String describeCode(int code) {
        switch (code) {
            case 0:
                return "Trời quang";
            case 1:
                return "Ít mây";
            case 2:
                return "Mây rải rác";
            case 3:
                return "Nhiều mây";
            case 45:
            case 48:
                return "Sương mù";
            case 51:
            case 53:
            case 55:
            case 56:
            case 57:
                return "Mưa phùn";
            case 61:
            case 66:
                return "Mưa nhỏ";
            case 63:
                return "Mưa vừa";
            case 65:
            case 67:
                return "Mưa to";
            case 80:
                return "Mưa rào nhẹ";
            case 81:
                return "Mưa rào vừa";
            case 82:
                return "Mưa rào mạnh";
            case 71:
                return "Tuyết nhẹ";
            case 73:
                return "Tuyết vừa";
            case 75:
                return "Tuyết dày";
            case 77:
                return "Hạt tuyết";
            case 85:
                return "Tuyết rào nhẹ";
            case 86:
                return "Tuyết rào mạnh";
            case 95:
            case 97:
                return "Giông";
            case 96:
            case 99:
                return "Giông kèm mưa đá";
            default:
                return "Thời tiết thay đổi";
        }
    }
}
