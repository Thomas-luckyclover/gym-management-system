package com.gms.dto;

/**
 * Số liệu thô của hôm nay từ Open-Meteo, chưa có câu chữ để hiện trên PT Dashboard.
 * WeatherClient sẽ tạo DTO này khi đọc JSON; WeatherService dùng nó để tạo WeatherView.
 */
public class WeatherForecast {

    // ===== current: tình trạng ở thời điểm API cập nhật =====

    // current.temperature_2m (°C); Service làm tròn để hiện nhiệt độ hiện tại.
    private final double currentTemperature;

    // current.apparent_temperature (°C); Service dùng cho dòng "Cảm giác như ...".
    private final double feelsLikeTemperature;

    // current.relative_humidity_2m (%); Service đưa vào thông tin độ ẩm nếu cần hiện.
    private final int currentHumidityPercent;

    // current.weather_code; Service đổi mã WMO thành chữ, ví dụ 3 là "Nhiều mây".
    private final int currentWeatherCode;

    // current.is_day: API trả 1 ban ngày, 0 ban đêm; Service chọn giao diện phù hợp.
    private final boolean isDay;

    // current.wind_speed_10m (km/h); Service dùng để nhận biết ngày gió mạnh.
    private final double currentWindSpeed;

    // ===== daily[0]: dự báo cho ngày hôm nay theo giờ Việt Nam =====

    // daily.weather_code[0]; Service dùng để nhận biết mưa/giông trong ngày.
    private final int dailyWeatherCode;

    // daily.temperature_2m_max[0] (°C); Service làm tròn để hiện nhiệt độ cao nhất.
    private final double dailyMaxTemperature;

    // daily.temperature_2m_min[0] (°C); Service làm tròn để hiện nhiệt độ thấp nhất.
    private final double dailyMinTemperature;

    // daily.precipitation_probability_max[0] (%); Service dùng cho lời nhắc khả năng mưa.
    private final int dailyMaxRainProbabilityPercent;

    /**
     * Giữ đúng các số đọc từ JSON; DTO không gọi API và không tự chọn câu hiển thị.
     */
    public WeatherForecast(double currentTemperature, double feelsLikeTemperature,
            int currentHumidityPercent, int currentWeatherCode, boolean isDay,
            int dailyWeatherCode, double dailyMaxTemperature, double dailyMinTemperature,
            int dailyMaxRainProbabilityPercent) {
        this(currentTemperature, feelsLikeTemperature, currentHumidityPercent, currentWeatherCode,
                isDay, 0, dailyWeatherCode, dailyMaxTemperature, dailyMinTemperature,
                dailyMaxRainProbabilityPercent);
    }

    public WeatherForecast(double currentTemperature, double feelsLikeTemperature,
            int currentHumidityPercent, int currentWeatherCode, boolean isDay,
            double currentWindSpeed, int dailyWeatherCode, double dailyMaxTemperature,
            double dailyMinTemperature, int dailyMaxRainProbabilityPercent) {
        this.currentTemperature = currentTemperature;
        this.feelsLikeTemperature = feelsLikeTemperature;
        this.currentHumidityPercent = currentHumidityPercent;
        this.currentWeatherCode = currentWeatherCode;
        this.isDay = isDay;
        this.currentWindSpeed = currentWindSpeed;
        this.dailyWeatherCode = dailyWeatherCode;
        this.dailyMaxTemperature = dailyMaxTemperature;
        this.dailyMinTemperature = dailyMinTemperature;
        this.dailyMaxRainProbabilityPercent = dailyMaxRainProbabilityPercent;
    }

    /** @return nhiệt độ hiện tại để Service làm tròn cho thẻ thời tiết */
    public double getCurrentTemperature() {
        return currentTemperature;
    }

    /** @return nhiệt độ cảm nhận hiện tại để Service chọn dòng mô tả */
    public double getFeelsLikeTemperature() {
        return feelsLikeTemperature;
    }

    /** @return độ ẩm hiện tại, tính bằng phần trăm */
    public int getCurrentHumidityPercent() {
        return currentHumidityPercent;
    }

    /** @return mã WMO lúc này để Service đổi thành chữ tiếng Việt */
    public int getCurrentWeatherCode() {
        return currentWeatherCode;
    }

    /** @return true khi API báo đang là ban ngày tại phòng gym */
    public boolean isDay() {
        return isDay;
    }

    /** @return tốc độ gió hiện tại, km/h */
    public double getCurrentWindSpeed() {
        return currentWindSpeed;
    }

    /** @return mã WMO của cả ngày để Service nhận biết thời tiết sắp tới */
    public int getDailyWeatherCode() {
        return dailyWeatherCode;
    }

    /** @return nhiệt độ cao nhất hôm nay, tính bằng °C */
    public double getDailyMaxTemperature() {
        return dailyMaxTemperature;
    }

    /** @return nhiệt độ thấp nhất hôm nay, tính bằng °C */
    public double getDailyMinTemperature() {
        return dailyMinTemperature;
    }

    /** @return xác suất mưa cao nhất được dự báo trong ngày, tính bằng phần trăm */
    public int getDailyMaxRainProbabilityPercent() {
        return dailyMaxRainProbabilityPercent;
    }
}
