package com.gms.service;

import com.gms.dto.WeatherForecast;
import com.gms.dto.WeatherView;
import com.gms.integration.WeatherClient;
import com.gms.integration.WeatherUnavailableException;
import com.gms.util.AppConfig;
import com.gms.util.DateTimeUtil;
import java.time.LocalDateTime;

/**
 * Thời tiết hôm nay cho PT Dashboard: lấy số liệu từ WeatherClient rồi đổi thành câu nhắc
 * ("Hôm nay có mưa, nhớ mang ô"...) và chọn kiểu hiệu ứng CSS.
 *
 * ===== CACHE =====
 * Thời tiết không đổi từng giây, và gọi API mất ~1–2 giây, nên kết quả được giữ trong RAM 30 phút,
 * dùng chung cho mọi PT. Gọi lỗi thì nhớ "đang lỗi" 5 phút để không bắt mỗi lần mở trang chờ 3 giây.
 *
 * ===== CHẾ ĐỘ DEMO =====
 * demoWeather(mood) trả thẻ thời tiết mẫu cho 1 kiểu trời, không gọi API — để xem đủ các hiệu ứng CSS
 * khi thuyết trình (PT Dashboard mở với ?weatherDemo=rain / storm / hot / cloudy / sunny / night).
 *
 * ===== ĐỊA ĐIỂM =====
 * Mặc định Hà Nội. Muốn đổi thì thêm vào application.properties (không bắt buộc):
 *   weather.city=Hà Nội · weather.latitude=21.0285 · weather.longitude=105.8542
 *
 * @author Le Cong Minh
 */
public class WeatherService {

    private static final String DEFAULT_CITY = "Hà Nội";
    private static final String DEFAULT_LATITUDE = "21.0285";
    private static final String DEFAULT_LONGITUDE = "105.8542";

    private static final int CACHE_MINUTES = 30;
    private static final int FAILURE_RETRY_MINUTES = 5;

    /** Ngưỡng "nắng nóng": cao nhất trong ngày ≥ 35°C hoặc cảm nhận lúc này ≥ 39°C. */
    private static final int HOT_MAX_TEMPERATURE = 35;
    private static final int HOT_FEELS_LIKE = 39;

    /** Khả năng mưa từ 60% trở lên thì coi như hôm nay có mưa, dù lúc này trời chưa mưa. */
    private static final int RAIN_CHANCE_THRESHOLD = 60;

    // Kết quả đã lấy lần trước, dùng chung cho mọi request (volatile để các luồng Tomcat thấy giá trị mới ngay)
    private static volatile WeatherView cachedView;
    private static volatile LocalDateTime cachedUntil;

    private final WeatherClient weatherClient = new WeatherClient();

    /**
     * Lấy thẻ thời tiết hôm nay.
     *
     * @return WeatherView có sẵn câu chữ; trả null khi không lấy được thời tiết
     *         → JSP ẩn thẻ thời tiết, phần còn lại của dashboard vẫn hiện bình thường.
     */
    public WeatherView getTodayWeather() {
        // B1: còn trong hạn cache (kể cả cache "đang lỗi") → trả luôn, không gọi API
        LocalDateTime now = DateTimeUtil.now();
        if (cachedUntil != null && now.isBefore(cachedUntil)) {
            return cachedView;
        }

        // B2: hết hạn → gọi API. synchronized để 10 PT mở trang cùng lúc thì chỉ 1 người phải chờ gọi API
        synchronized (WeatherService.class) {
            // Kiểm tra lại: có thể luồng khác vừa gọi xong trong lúc mình đứng chờ
            if (cachedUntil != null && now.isBefore(cachedUntil)) {
                return cachedView;
            }
            try {
                double latitude = Double.parseDouble(AppConfig.get("weather.latitude", DEFAULT_LATITUDE));
                double longitude = Double.parseDouble(AppConfig.get("weather.longitude", DEFAULT_LONGITUDE));
                String city = AppConfig.get("weather.city", DEFAULT_CITY);

                WeatherForecast forecast = weatherClient.fetchToday(latitude, longitude);
                cachedView = toView(forecast, city);
                cachedUntil = now.plusMinutes(CACHE_MINUTES);

            } catch (WeatherUnavailableException | NumberFormatException e) {
                // Lỗi → in ra console cho dev, nhớ "không có thời tiết" 5 phút rồi mới thử lại
                System.err.println("[WEATHER] " + e.getMessage());
                cachedView = null;
                cachedUntil = now.plusMinutes(FAILURE_RETRY_MINUTES);
            }
            return cachedView;
        }
    }

    /**
     * Thẻ thời tiết MẪU cho 1 kiểu trời — không gọi API, chỉ dùng để xem trước hiệu ứng.
     *
     * @param moodName tên kiểu trời không phân biệt hoa thường: "storm", "rain", "hot", "cloudy", "sunny", "night".
     * @return thẻ thời tiết mẫu; null nếu tên không khớp kiểu nào (người gọi dùng thời tiết thật).
     */
    public WeatherView demoWeather(String moodName) {
        if (moodName == null) {
            return null;
        }
        WeatherForecast sample;
        switch (moodName.trim().toLowerCase()) {
            case "storm":
                sample = new WeatherForecast(27, 31, 90, 95, 96, 30, 24, 95, true);
                break;
            case "rain":
                sample = new WeatherForecast(26, 29, 92, 63, 63, 29, 24, 90, true);
                break;
            case "hot":
                sample = new WeatherForecast(37, 43, 50, 0, 0, 38, 28, 0, true);
                break;
            case "cloudy":
                sample = new WeatherForecast(29, 31, 70, 3, 3, 31, 25, 20, true);
                break;
            case "sunny":
                sample = new WeatherForecast(30, 32, 60, 0, 0, 31, 24, 5, true);
                break;
            case "night":
                sample = new WeatherForecast(26, 27, 75, 0, 0, 31, 24, 5, false);
                break;
            default:
                return null;
        }
        return toView(sample, AppConfig.get("weather.city", DEFAULT_CITY) + " (demo)");
    }

    /**
     * Đổi số liệu thô thành thẻ thời tiết có câu nhắc. Xét theo thứ tự ưu tiên:
     * giông → mưa → nắng nóng → nhiều mây → đêm trời quang → nắng đẹp.
     *
     * @param forecast số liệu thô hôm nay.
     * @param city     tên nơi xem thời tiết, VD "Hà Nội".
     * @return thẻ thời tiết.
     */
    WeatherView toView(WeatherForecast forecast, String city) {
        int temperature = (int) Math.round(forecast.getTemperature());
        int feelsLike = (int) Math.round(forecast.getFeelsLike());
        int maxTemperature = (int) Math.round(forecast.getMaxTemperature());
        int minTemperature = (int) Math.round(forecast.getMinTemperature());
        int rainChance = forecast.getRainChance();
        String conditionLabel = describeCode(forecast.getCurrentCode());

        WeatherView.Mood mood;
        String title;
        String advice;
        String icon;

        // B1: có giông lúc này hoặc trong ngày (mã 95–99) → nguy hiểm nhất, xét trước
        if (isStormCode(forecast.getCurrentCode()) || isStormCode(forecast.getDailyCode())) {
            mood = WeatherView.Mood.STORM;
            title = "Hôm nay có mưa giông";
            advice = "Nhớ mang ô và áo mưa. Khách có thể đến trễ — nhắn xác nhận trước buổi tập nhé.";
            icon = "fa-cloud-bolt";

        // B2: đang mưa, cả ngày có mưa, hoặc khả năng mưa cao
        } else if (isRainCode(forecast.getCurrentCode()) || isRainCode(forecast.getDailyCode())
                || rainChance >= RAIN_CHANCE_THRESHOLD) {
            mood = WeatherView.Mood.RAIN;
            title = isRainCode(forecast.getCurrentCode())
                    ? "Trời đang mưa"
                    : "Hôm nay có thể mưa (" + rainChance + "%)";
            advice = "Nhớ mang ô! Sàn gần cửa dễ trơn — nhắc khách lau giày trước khi vào khu tập.";
            icon = "fa-cloud-showers-heavy";

        // B3: nắng nóng
        } else if (maxTemperature >= HOT_MAX_TEMPERATURE || feelsLike >= HOT_FEELS_LIKE) {
            mood = WeatherView.Mood.HOT;
            title = "Nắng nóng " + maxTemperature + "°C";
            advice = "Uống đủ nước, nhắc khách mang bình nước và cho nghỉ giữa hiệp lâu hơn một chút.";
            icon = "fa-temperature-high";

        // B4: nhiều mây / sương mù
        } else if (isCloudyCode(forecast.getCurrentCode())) {
            mood = WeatherView.Mood.CLOUDY;
            title = "Trời nhiều mây, dễ chịu";
            advice = "Thời tiết mát mẻ — hợp cho các buổi tập cường độ cao.";
            icon = "fa-cloud-sun";

        // B5: trời quang ban đêm
        } else if (!forecast.isDay()) {
            mood = WeatherView.Mood.NIGHT;
            title = "Tối nay trời quang";
            advice = "Giờ cao điểm buổi tối — chuẩn bị dụng cụ sớm để buổi tập không bị trễ.";
            icon = "fa-moon";

        // B6: còn lại = nắng đẹp
        } else {
            mood = WeatherView.Mood.SUNNY;
            title = "Trời nắng đẹp";
            advice = "Ngày đẹp để tập. Ra ngoài giữa trưa thì nhớ kem chống nắng nhé.";
            icon = "fa-sun";
        }

        return new WeatherView(mood, city, conditionLabel, title, advice, icon,
                temperature, feelsLike, maxTemperature, minTemperature, rainChance, forecast.getHumidity());
    }

    // ===== Hàm phụ: đọc mã thời tiết WMO (Open-Meteo dùng bảng mã chuẩn của Tổ chức Khí tượng Thế giới) =====

    /** Mã 95, 96, 99 = dông (có thể kèm mưa đá). */
    private boolean isStormCode(int code) {
        return code >= 95;
    }

    /** Mã 51–67 = mưa phùn / mưa, 80–82 = mưa rào. */
    private boolean isRainCode(int code) {
        return (code >= 51 && code <= 67) || (code >= 80 && code <= 82);
    }

    /** Mã 1–3 = ít mây tới nhiều mây, 45 và 48 = sương mù. */
    private boolean isCloudyCode(int code) {
        return (code >= 1 && code <= 3) || code == 45 || code == 48;
    }

    /**
     * Đổi mã WMO thành chữ tiếng Việt.
     *
     * @param code mã thời tiết, VD 61.
     * @return VD "Mưa nhỏ".
     */
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
                return "Mưa rào";
            case 82:
                return "Mưa rào rất to";
            case 95:
                return "Dông";
            case 96:
            case 99:
                return "Dông kèm mưa đá";
            default:
                // 71–77, 85–86 là tuyết — Việt Nam gần như không gặp
                return (code >= 71 && code <= 86) ? "Có tuyết" : "Thời tiết thay đổi";
        }
    }
}
