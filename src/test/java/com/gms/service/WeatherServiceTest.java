package com.gms.service;

import com.gms.dto.WeatherForecast;
import com.gms.dto.WeatherView;
import com.gms.integration.WeatherClient;
import com.gms.integration.WeatherUnavailableException;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Kiểm tra thứ tự lời nhắc và cache mà không cần kết nối Open-Meteo. */
class WeatherServiceTest {

    private WeatherForecast forecast(int currentCode, int dailyCode, double maxTemperature,
            double feelsLikeTemperature, int rainChance, boolean isDay) {
        return new WeatherForecast(29.4, feelsLikeTemperature, 72, currentCode, isDay,
                dailyCode, maxTemperature, 25.4, rainChance);
    }

    @Test
    void choosesWeatherByBusinessPriority() {
        WeatherService service = new WeatherService(
                new FakeWeatherClient(forecast(0, 0, 31, 32, 0, true)),
                () -> LocalDateTime.of(2026, 10, 9, 9, 0), "Hà Nội", "21.0285", "105.8542");

        assertEquals(WeatherView.Mood.STORM,
                service.toView(forecast(3, 95, 36, 40, 90, true), "Hà Nội").getMood());
        assertEquals(WeatherView.Mood.RAIN,
                service.toView(forecast(3, 3, 36, 40, 80, true), "Hà Nội").getMood());
        assertEquals(WeatherView.Mood.HOT,
                service.toView(forecast(0, 0, 35, 34, 0, true), "Hà Nội").getMood());
        assertEquals(WeatherView.Mood.CLOUDY,
                service.toView(forecast(3, 3, 32, 34, 0, true), "Hà Nội").getMood());
        assertEquals(WeatherView.Mood.NIGHT,
                service.toView(forecast(0, 0, 32, 34, 0, false), "Hà Nội").getMood());
        assertEquals(WeatherView.Mood.SUNNY,
                service.toView(forecast(0, 0, 32, 34, 0, true), "Hà Nội").getMood());
        assertEquals(WeatherView.Mood.SUNNY,
                service.toView(forecast(1, 1, 32, 34, 0, true), "Hà Nội").getMood());
    }

    @Test
    void mapsSnowWindAndHeatWithoutTreatingWeatherCodeAsTemperature() {
        WeatherService service = new WeatherService(
                new FakeWeatherClient(forecast(0, 0, 31, 32, 0, true)),
                () -> LocalDateTime.of(2026, 10, 9, 9, 0), "Hà Nội", "21.0285", "105.8542");

        WeatherView snow = service.toView(forecast(71, 71, 0, 0, 0, true), "Hà Nội");
        assertEquals(WeatherView.Mood.SNOW, snow.getMood());
        assertEquals("Tuyết nhẹ", snow.getConditionLabel());

        WeatherForecast windy = new WeatherForecast(29, 32, 60, 0, true,
                40, 0, 31, 25, 0);
        assertEquals(WeatherView.Mood.WIND, service.toView(windy, "Hà Nội").getMood());

        WeatherView hot = service.toView(forecast(0, 0, 35, 34, 0, true), "Hà Nội");
        assertEquals(WeatherView.Mood.HOT, hot.getMood());
        assertEquals("hot", hot.getWeatherType());
        assertEquals("Nắng nóng gay gắt",
                service.toView(forecast(0, 0, 38, 42, 0, true), "Hà Nội").getTitle());
        assertEquals("Đêm nay nhiều mây",
                service.toView(forecast(3, 3, 32, 34, 0, false), "Hà Nội").getTitle());
    }

    @Test
    void rainEffectRequiresMoreThanFiftyPercent() {
        WeatherService service = new WeatherService(
                new FakeWeatherClient(forecast(0, 0, 31, 32, 0, true)),
                () -> LocalDateTime.of(2026, 10, 9, 9, 0), "Hà Nội", "21.0285", "105.8542");

        // Dự báo mưa đúng 50% chưa bật mưa; 51% bật. Đang mưa thực tế là trường hợp riêng.
        assertEquals("sunny", service.toView(forecast(0, 61, 32, 34, 50, true), "Hà Nội").getWeatherType());
        assertEquals("rain", service.toView(forecast(0, 0, 32, 34, 51, true), "Hà Nội").getWeatherType());
        assertEquals("rain", service.toView(forecast(61, 61, 32, 34, 20, true), "Hà Nội").getWeatherType());
    }

    @Test
    void previewUsesFixedTypesWithoutCallingApi() {
        FakeWeatherClient client = new FakeWeatherClient(forecast(0, 0, 31, 32, 0, true));
        WeatherService service = new WeatherService(client,
                () -> LocalDateTime.of(2026, 10, 9, 9, 0), "Hà Nội", "21.0285", "105.8542");

        for (String type : new String[] {"sunny", "cloudy", "rain", "storm", "snow", "wind", "night", "hot"}) {
            assertEquals(type, service.getPreview(type).getWeatherType());
        }
        assertEquals("hot", service.getPreview("extreme_hot").getWeatherType());
        assertNull(service.getPreview("unknown"));
        assertEquals(0, client.calls);
    }

    @Test
    void reusesSuccessForThirtyMinutesButNotAfterMidnight() {
        AtomicReference<LocalDateTime> now = new AtomicReference<>(LocalDateTime.of(2026, 10, 9, 23, 55));
        FakeWeatherClient client = new FakeWeatherClient(forecast(0, 0, 31, 32, 0, true));
        WeatherService service = new WeatherService(client, now::get, "Hà Nội", "21.0285", "105.8542");

        assertNotNull(service.getTodayWeather());
        now.set(LocalDateTime.of(2026, 10, 9, 23, 59));
        assertNotNull(service.getTodayWeather());
        assertEquals(1, client.calls);

        now.set(LocalDateTime.of(2026, 10, 10, 0, 1));
        assertNotNull(service.getTodayWeather());
        assertEquals(2, client.calls);
    }

    @Test
    void callsApiAgainWhenThirtyMinuteCacheExpires() {
        AtomicReference<LocalDateTime> now = new AtomicReference<>(LocalDateTime.of(2026, 10, 9, 9, 0));
        FakeWeatherClient client = new FakeWeatherClient(forecast(0, 0, 31, 32, 0, true));
        WeatherService service = new WeatherService(client, now::get, "Hà Nội", "21.0285", "105.8542");

        service.getTodayWeather();
        now.set(LocalDateTime.of(2026, 10, 9, 9, 29));
        service.getTodayWeather();
        assertEquals(1, client.calls);

        now.set(LocalDateTime.of(2026, 10, 9, 9, 30));
        service.getTodayWeather();
        assertEquals(2, client.calls);
    }

    @Test
    void retriesFiveMinutesAfterFailure() {
        AtomicReference<LocalDateTime> now = new AtomicReference<>(LocalDateTime.of(2026, 10, 9, 9, 0));
        FakeWeatherClient client = new FakeWeatherClient(forecast(0, 0, 31, 32, 0, true));
        client.failuresRemaining = 1;
        WeatherService service = new WeatherService(client, now::get, "Hà Nội", "21.0285", "105.8542");

        assertNull(service.getTodayWeather());
        now.set(LocalDateTime.of(2026, 10, 9, 9, 4));
        assertNull(service.getTodayWeather());
        assertEquals(1, client.calls);

        now.set(LocalDateTime.of(2026, 10, 9, 9, 5));
        assertNotNull(service.getTodayWeather());
        assertEquals(2, client.calls);
    }

    /** Thay API thật bằng dữ liệu cố định để test thời gian cache. */
    private static class FakeWeatherClient extends WeatherClient {
        private final WeatherForecast forecast;
        private int calls;
        private int failuresRemaining;

        FakeWeatherClient(WeatherForecast forecast) {
            this.forecast = forecast;
        }

        @Override
        public WeatherForecast fetchToday(double latitude, double longitude) throws WeatherUnavailableException {
            calls++;
            if (failuresRemaining > 0) {
                failuresRemaining--;
                throw new WeatherUnavailableException("API không phản hồi.", null);
            }
            return forecast;
        }
    }
}
