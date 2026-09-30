package com.gms.dto;

/**
 * Thẻ thời tiết trên PT Dashboard — đã có sẵn câu chữ, JSP chỉ việc in ra.
 *
 * Ai tạo: WeatherService.getTodayWeather (từ WeatherForecast thô).
 * Ai dùng: pt/dashboard.jsp. Trường mood quyết định hiệu ứng CSS (mưa rơi, nắng xoay, sét lóe...).
 *
 * VD Hà Nội trưa nắng: mood "hot", conditionLabel "Trời quang", title "Nắng nóng 35°C",
 *     advice "Uống đủ nước và nhắc khách mang bình nước...", temperature 34, feelsLike 41, rainChance 1
 *
 * @author Le Cong Minh
 */
public class WeatherView {

    /** Kiểu hiệu ứng, cũng là hậu tố class CSS "weather--{mood}". */
    public enum Mood {
        STORM("storm"),
        RAIN("rain"),
        HOT("hot"),
        CLOUDY("cloudy"),
        SUNNY("sunny"),
        NIGHT("night");

        private final String cssName;

        Mood(String cssName) {
            this.cssName = cssName;
        }

        public String getCssName() {
            return cssName;
        }
    }

    private final Mood mood;
    private final String cityName;
    private final String conditionLabel;
    private final String title;
    private final String advice;
    private final String iconClass;
    private final int temperature;
    private final int feelsLike;
    private final int maxTemperature;
    private final int minTemperature;
    private final int rainChance;
    private final int humidity;

    /**
     * @param mood           kiểu hiệu ứng (STORM / RAIN / HOT / CLOUDY / SUNNY / NIGHT).
     * @param cityName       tên nơi xem thời tiết, VD "Hà Nội".
     * @param conditionLabel tình trạng lúc này bằng chữ, VD "Mưa giông".
     * @param title          câu to trên thẻ, VD "Hôm nay có mưa giông".
     * @param advice         lời nhắc cho PT, VD "Nhớ mang ô và áo mưa...".
     * @param iconClass      icon Font Awesome, VD "fa-cloud-bolt".
     * @param temperature    nhiệt độ lúc này, đã làm tròn (°C).
     * @param feelsLike      nhiệt độ cảm nhận, đã làm tròn (°C).
     * @param maxTemperature cao nhất hôm nay (°C).
     * @param minTemperature thấp nhất hôm nay (°C).
     * @param rainChance     khả năng mưa cao nhất hôm nay (%).
     * @param humidity       độ ẩm lúc này (%).
     */
    public WeatherView(Mood mood, String cityName, String conditionLabel, String title, String advice,
            String iconClass, int temperature, int feelsLike, int maxTemperature, int minTemperature,
            int rainChance, int humidity) {
        this.mood = mood;
        this.cityName = cityName;
        this.conditionLabel = conditionLabel;
        this.title = title;
        this.advice = advice;
        this.iconClass = iconClass;
        this.temperature = temperature;
        this.feelsLike = feelsLike;
        this.maxTemperature = maxTemperature;
        this.minTemperature = minTemperature;
        this.rainChance = rainChance;
        this.humidity = humidity;
    }

    public Mood getMood() {
        return mood;
    }

    /** @return tên class CSS của hiệu ứng, VD "rain" → JSP ghép thành "weather--rain". */
    public String getMoodCss() {
        return mood.getCssName();
    }

    public String getCityName() {
        return cityName;
    }

    public String getConditionLabel() {
        return conditionLabel;
    }

    public String getTitle() {
        return title;
    }

    public String getAdvice() {
        return advice;
    }

    public String getIconClass() {
        return iconClass;
    }

    public int getTemperature() {
        return temperature;
    }

    public int getFeelsLike() {
        return feelsLike;
    }

    public int getMaxTemperature() {
        return maxTemperature;
    }

    public int getMinTemperature() {
        return minTemperature;
    }

    public int getRainChance() {
        return rainChance;
    }

    public int getHumidity() {
        return humidity;
    }
}
