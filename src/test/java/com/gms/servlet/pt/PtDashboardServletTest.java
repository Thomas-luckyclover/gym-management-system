package com.gms.servlet.pt;

import com.gms.dto.WeatherView;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PtDashboardServletTest {

    @Test
    void weatherCssClassIsAlwaysFromAllowedList() {
        WeatherView actual = new WeatherView(WeatherView.Mood.HOT, "Hà Nội",
                "Trời quang", "Nắng nóng", "Uống nước", "fa-sun", 36);

        assertEquals("sunny", PtDashboardServlet.resolveWeatherType(null, null));
        assertEquals("hot", PtDashboardServlet.resolveWeatherType(null, actual));
        assertEquals("rain", PtDashboardServlet.resolveWeatherType("rain", actual));
        assertEquals("hot", PtDashboardServlet.resolveWeatherType("anything-else", actual));
        assertEquals("hot", PtDashboardServlet.resolveWeatherType("extreme_hot", actual));
    }
}
