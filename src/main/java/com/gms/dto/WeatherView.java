package com.gms.dto;

import java.time.LocalDateTime;

/**
 * Một thẻ thời tiết đã có sẵn câu chữ để PT Dashboard hiển thị.
 * WeatherService sẽ tạo DTO này từ WeatherForecast; JSP chỉ đọc, không xét mã thời tiết.
 */
public class WeatherView {

    /** Kiểu thời tiết để JSP gắn class CSS; tên CSS chỉ lấy từ các giá trị cố định này. */
    public enum Mood {
        STORM("storm"),
        RAIN("rain"),
        SNOW("snow"),
        WIND("wind"),
        HOT("hot"),
        CLOUDY("cloudy"),
        SUNNY("sunny"),
        NIGHT("night");

        private final String cssName;

        Mood(String cssName) {
            this.cssName = cssName;
        }

        /** @return hậu tố class CSS, ví dụ RAIN thành weather--rain */
        public String getCssName() {
            return cssName;
        }
    }

    // WeatherService chọn từ mã thời tiết/nhiệt độ trong WeatherForecast; JSP dùng để chọn màu thẻ.
    private final Mood mood;

    // WeatherService đọc cấu hình địa điểm của gym; JSP in cạnh tình trạng trời, ví dụ "Hà Nội".
    private final String cityName;

    // WeatherService đổi WeatherForecast.currentWeatherCode thành chữ; JSP in "Nhiều mây".
    private final String conditionLabel;

    // WeatherService chọn thông điệp chính; JSP in "Hôm nay có thể mưa".
    private final String title;

    // WeatherService chọn lời nhắc phù hợp cho PT; JSP in thành một dòng dưới tiêu đề.
    private final String advice;

    // WeatherService chọn tên icon Font Awesome theo mood; JSP gắn vào biểu tượng thời tiết.
    private final String iconClass;

    // WeatherService làm tròn WeatherForecast.currentTemperature; JSP in lớn, ví dụ "29°".
    private final int temperature;

    // Các số từ WeatherForecast do Service làm tròn; null ở constructor cũ dùng bởi các màn/test khác.
    private final Integer feelsLikeTemperature;
    private final Integer humidityPercent;
    private final Integer windSpeedKmh;
    private final Integer minTemperature;
    private final Integer maxTemperature;
    private final Integer rainProbabilityPercent;

    // WeatherService ghi thời điểm lấy API thành công theo giờ gym. Stale chỉ bật khi lần thử mới thất bại.
    private final LocalDateTime updatedAt;
    private final boolean stale;

    /** Giữ constructor cũ cho nơi chỉ cần dòng thời tiết ngắn. */
    public WeatherView(Mood mood, String cityName, String conditionLabel, String title,
            String advice, String iconClass, int temperature) {
        this(mood, cityName, conditionLabel, title, advice, iconClass, temperature,
                null, null, null, null, null, null);
    }

    /** Service truyền thêm các số đo để PT Dashboard hiện bảng chi tiết. */
    public WeatherView(Mood mood, String cityName, String conditionLabel, String title,
            String advice, String iconClass, int temperature, Integer feelsLikeTemperature,
            Integer humidityPercent, Integer windSpeedKmh, Integer minTemperature,
            Integer maxTemperature, Integer rainProbabilityPercent) {
        this(mood, cityName, conditionLabel, title, advice, iconClass, temperature,
                feelsLikeTemperature, humidityPercent, windSpeedKmh, minTemperature,
                maxTemperature, rainProbabilityPercent, null, false);
    }

    private WeatherView(Mood mood, String cityName, String conditionLabel, String title,
            String advice, String iconClass, int temperature, Integer feelsLikeTemperature,
            Integer humidityPercent, Integer windSpeedKmh, Integer minTemperature,
            Integer maxTemperature, Integer rainProbabilityPercent,
            LocalDateTime updatedAt, boolean stale) {
        this.mood = mood;
        this.cityName = cityName;
        this.conditionLabel = conditionLabel;
        this.title = title;
        this.advice = advice;
        this.iconClass = iconClass;
        this.temperature = temperature;
        this.feelsLikeTemperature = feelsLikeTemperature;
        this.humidityPercent = humidityPercent;
        this.windSpeedKmh = windSpeedKmh;
        this.minTemperature = minTemperature;
        this.maxTemperature = maxTemperature;
        this.rainProbabilityPercent = rainProbabilityPercent;
        this.updatedAt = updatedAt;
        this.stale = stale;
    }

    /** Gắn thời điểm lần GET thành công; dữ liệu preview vẫn không có mốc này. */
    public WeatherView withUpdatedAt(LocalDateTime fetchedAt) {
        return new WeatherView(mood, cityName, conditionLabel, title, advice, iconClass,
                temperature, feelsLikeTemperature, humidityPercent, windSpeedKmh,
                minTemperature, maxTemperature, rainProbabilityPercent, fetchedAt, false);
    }

    /** Dùng lại đúng số đo và mốc lấy cũ sau lỗi mạng, để giao diện ghi rõ dữ liệu cũ. */
    public WeatherView asStale() {
        return new WeatherView(mood, cityName, conditionLabel, title, advice, iconClass,
                temperature, feelsLikeTemperature, humidityPercent, windSpeedKmh,
                minTemperature, maxTemperature, rainProbabilityPercent, updatedAt, true);
    }

    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public boolean isStale() { return stale; }

    /** @return mood để Service hoặc test kiểm tra kiểu thời tiết đã chọn */
    public Mood getMood() {
        return mood;
    }

    /** @return hậu tố CSS để JSP gắn class weather--{moodCss} */
    public String getMoodCss() {
        return mood.getCssName();
    }

    /** CSS dùng cùng mood mà Service chọn; nắng nóng có bảng màu cam riêng. */
    public String getWeatherType() {
        return mood.getCssName();
    }

    /** @return tên địa điểm JSP in cạnh tình trạng thời tiết */
    public String getCityName() {
        return cityName;
    }

    /** @return chữ mô tả thời tiết hiện tại */
    public String getConditionLabel() {
        return conditionLabel;
    }

    /** @return tiêu đề chính trên thẻ thời tiết */
    public String getTitle() {
        return title;
    }

    /** @return lời nhắc ngắn cho PT */
    public String getAdvice() {
        return advice;
    }

    /** @return tên icon Font Awesome do Service chọn, không lấy trực tiếp từ API */
    public String getIconClass() {
        return iconClass;
    }

    /** @return nhiệt độ hiện tại đã làm tròn để JSP in cùng ký hiệu độ */
    public int getTemperature() {
        return temperature;
    }

    /** @return current.apparent_temperature đã làm tròn, °C */
    public Integer getFeelsLikeTemperature() { return feelsLikeTemperature; }

    /** @return current.relative_humidity_2m, % */
    public Integer getHumidityPercent() { return humidityPercent; }

    /** @return current.wind_speed_10m đã làm tròn, km/h */
    public Integer getWindSpeedKmh() { return windSpeedKmh; }

    /** @return daily.temperature_2m_min[0] đã làm tròn, °C */
    public Integer getMinTemperature() { return minTemperature; }

    /** @return daily.temperature_2m_max[0] đã làm tròn, °C */
    public Integer getMaxTemperature() { return maxTemperature; }

    /** @return daily.precipitation_probability_max[0], % */
    public Integer getRainProbabilityPercent() { return rainProbabilityPercent; }
}
