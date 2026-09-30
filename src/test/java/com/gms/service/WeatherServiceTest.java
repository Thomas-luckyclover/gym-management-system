package com.gms.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gms.dto.WeatherForecast;
import com.gms.dto.WeatherView;
import org.junit.jupiter.api.Test;

/**
 * Kiểm tra WeatherService.toView chọn đúng kiểu hiệu ứng + câu nhắc — không gọi API, không cần mạng.
 * Thứ tự ưu tiên: giông → mưa → nắng nóng → nhiều mây → đêm quang → nắng đẹp.
 */
class WeatherServiceTest {

    private final WeatherService weatherService = new WeatherService();

    /** Tạo số liệu thô nhanh: chỉ quan tâm mã thời tiết, nhiệt độ, khả năng mưa, ngày/đêm. */
    private WeatherForecast forecast(int currentCode, int dailyCode, double max, double feelsLike,
            int rainChance, boolean day) {
        return new WeatherForecast(30, feelsLike, 70, currentCode, dailyCode, max, 25, rainChance, day);
    }

    @Test
    void stormInDailyForecastWinsOverEverythingElse() {
        // Lúc này trời quang nhưng trong ngày có giông (96) → vẫn phải nhắc mang ô
        WeatherView view = weatherService.toView(forecast(0, 96, 36, 41, 99, true), "Hà Nội");
        assertEquals(WeatherView.Mood.STORM, view.getMood());
        assertTrue(view.getAdvice().contains("mang ô"));
    }

    @Test
    void highRainChanceMeansRainEvenIfSkyIsClearNow() {
        WeatherView view = weatherService.toView(forecast(1, 3, 31, 34, 80, true), "Hà Nội");
        assertEquals(WeatherView.Mood.RAIN, view.getMood());
        assertEquals("Hôm nay có thể mưa (80%)", view.getTitle());
    }

    @Test
    void rainingNowShowsRainingTitle() {
        WeatherView view = weatherService.toView(forecast(61, 61, 29, 32, 90, true), "Hà Nội");
        assertEquals(WeatherView.Mood.RAIN, view.getMood());
        assertEquals("Trời đang mưa", view.getTitle());
        assertEquals("Mưa nhỏ", view.getConditionLabel());
    }

    @Test
    void hotWhenMaxAtLeast35OrFeelsLikeAtLeast39() {
        assertEquals(WeatherView.Mood.HOT, weatherService.toView(forecast(0, 0, 35, 36, 1, true), "HN").getMood());
        assertEquals(WeatherView.Mood.HOT, weatherService.toView(forecast(0, 0, 33, 39, 1, true), "HN").getMood());
    }

    @Test
    void cloudySunnyAndNight() {
        assertEquals(WeatherView.Mood.CLOUDY, weatherService.toView(forecast(2, 2, 30, 32, 10, true), "HN").getMood());
        assertEquals(WeatherView.Mood.SUNNY, weatherService.toView(forecast(0, 0, 30, 32, 10, true), "HN").getMood());
        assertEquals(WeatherView.Mood.NIGHT, weatherService.toView(forecast(0, 0, 30, 32, 10, false), "HN").getMood());
    }

    @Test
    void numbersAreRoundedForDisplay() {
        WeatherView view = weatherService.toView(new WeatherForecast(33.6, 40.5, 56, 0, 0, 34.4, 26.9, 1, true), "HN");
        assertEquals(34, view.getTemperature());
        assertEquals(41, view.getFeelsLike());
        assertEquals("hot", view.getMoodCss());
    }
}
