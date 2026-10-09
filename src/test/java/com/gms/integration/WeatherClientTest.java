package com.gms.integration;

import com.gms.dto.WeatherForecast;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Kiểm tra JSON current/daily từ API được đọc đúng trước khi chuyển cho Service. */
class WeatherClientTest {

    private final WeatherClient client = new WeatherClient();

    @Test
    void readsCurrentValuesAndFirstDailyValue() throws WeatherUnavailableException {
        String json = """
                {
                  "current": {
                    "temperature_2m": 29.4,
                    "apparent_temperature": 33.1,
                    "relative_humidity_2m": 72,
                    "wind_speed_10m": 12.5,
                    "weather_code": 3,
                    "is_day": 0
                  },
                  "daily": {
                    "weather_code": [61],
                    "temperature_2m_max": [32.2],
                    "temperature_2m_min": [25.4],
                    "precipitation_probability_max": [80]
                  }
                }
                """;

        WeatherForecast forecast = client.parseForecast(json);

        assertEquals(29.4, forecast.getCurrentTemperature());
        assertEquals(33.1, forecast.getFeelsLikeTemperature());
        assertEquals(72, forecast.getCurrentHumidityPercent());
        assertEquals(12.5, forecast.getCurrentWindSpeed());
        assertEquals(3, forecast.getCurrentWeatherCode());
        assertFalse(forecast.isDay());
        assertEquals(61, forecast.getDailyWeatherCode());
        assertEquals(32.2, forecast.getDailyMaxTemperature());
        assertEquals(25.4, forecast.getDailyMinTemperature());
        assertEquals(80, forecast.getDailyMaxRainProbabilityPercent());
    }

    @Test
    void missingDailyFieldIsReportedAsWeatherUnavailable() {
        String json = """
                {"current":{"temperature_2m":29,"apparent_temperature":32,
                  "relative_humidity_2m":70,"weather_code":0,"is_day":1,"wind_speed_10m":12},
                 "daily":{"weather_code":[0],"temperature_2m_max":[31],
                  "temperature_2m_min":[24]}}
                """;

        WeatherUnavailableException error = assertThrows(WeatherUnavailableException.class,
                () -> client.parseForecast(json));
        assertNotNull(error.getCause());
    }
}
