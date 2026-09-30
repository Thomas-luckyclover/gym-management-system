package com.gms.dto;

/**
 * Số liệu thời tiết THÔ của hôm nay, đọc từ API Open-Meteo — chưa có câu chữ gì.
 *
 * Ai tạo: WeatherClient.fetchToday.
 * Ai dùng: WeatherService đổi các con số này thành câu nhắc cho PT (WeatherView).
 *
 * VD Hà Nội trưa 30/09: temperature 33.9, feelsLike 40.9, humidity 56, currentCode 0 (trời quang),
 *     dailyCode 0, maxTemperature 34.5, minTemperature 26.9, rainChance 1, day = true
 *
 * @author Le Cong Minh
 */
public class WeatherForecast {

    private final double temperature;
    private final double feelsLike;
    private final int humidity;
    private final int currentCode;
    private final int dailyCode;
    private final double maxTemperature;
    private final double minTemperature;
    private final int rainChance;
    private final boolean day;

    /**
     * @param temperature    nhiệt độ hiện tại (°C), VD 33.9.
     * @param feelsLike      nhiệt độ cảm nhận (°C) — tính cả độ ẩm, VD 40.9.
     * @param humidity       độ ẩm hiện tại (%), VD 56.
     * @param currentCode    mã thời tiết WMO lúc này, VD 0 = trời quang, 61 = mưa, 95 = giông.
     * @param dailyCode      mã thời tiết WMO "xấu nhất" trong cả ngày hôm nay.
     * @param maxTemperature nhiệt độ cao nhất hôm nay (°C).
     * @param minTemperature nhiệt độ thấp nhất hôm nay (°C).
     * @param rainChance     khả năng mưa cao nhất trong ngày (%), VD 99.
     * @param day            true nếu đang là ban ngày ở địa điểm đó.
     */
    public WeatherForecast(double temperature, double feelsLike, int humidity, int currentCode, int dailyCode,
            double maxTemperature, double minTemperature, int rainChance, boolean day) {
        this.temperature = temperature;
        this.feelsLike = feelsLike;
        this.humidity = humidity;
        this.currentCode = currentCode;
        this.dailyCode = dailyCode;
        this.maxTemperature = maxTemperature;
        this.minTemperature = minTemperature;
        this.rainChance = rainChance;
        this.day = day;
    }

    public double getTemperature() {
        return temperature;
    }

    public double getFeelsLike() {
        return feelsLike;
    }

    public int getHumidity() {
        return humidity;
    }

    public int getCurrentCode() {
        return currentCode;
    }

    public int getDailyCode() {
        return dailyCode;
    }

    public double getMaxTemperature() {
        return maxTemperature;
    }

    public double getMinTemperature() {
        return minTemperature;
    }

    public int getRainChance() {
        return rainChance;
    }

    public boolean isDay() {
        return day;
    }
}
