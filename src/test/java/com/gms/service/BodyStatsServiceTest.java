package com.gms.service;

import com.gms.dto.BodyStatRow;
import com.gms.dto.BodyStatTile;
import com.gms.dto.BodyStatsView;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Luật màn Chỉ số cơ thể (không đọc DB). */
class BodyStatsServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 25);

    private BodyStatRow row(String day, String weight, String fat) {
        BodyStatRow row = new BodyStatRow();
        row.setMeasuredOn(LocalDate.parse(day));
        row.setWeightKg(new BigDecimal(weight));
        row.setHeightCm(new BigDecimal("160"));
        row.setBodyFatPercent(fat == null ? null : new BigDecimal(fat));
        row.setBmi(BodyStatsService.bmi(row.getWeightKg(), row.getHeightCm()));
        return row;
    }

    @Test
    void bmiAndAsianGroups() {
        assertEquals(new BigDecimal("22.9"), BodyStatsService.bmi(new BigDecimal("58.6"), new BigDecimal("160")));
        assertEquals("Thiếu cân", BodyStatsService.bmiGroup(new BigDecimal("18.4"))[0]);
        assertEquals("Bình thường", BodyStatsService.bmiGroup(new BigDecimal("22.9"))[0]);
        assertEquals("Thừa cân", BodyStatsService.bmiGroup(new BigDecimal("23.0"))[0]);
        assertEquals("Béo phì", BodyStatsService.bmiGroup(new BigDecimal("25.0"))[0]);
    }

    @Test
    void parsesCommaOrDotAndValidatesRanges() {
        assertEquals(new BigDecimal("58.6"), BodyStatsService.parseNumber(" 58,6 "));
        assertEquals(new BigDecimal("58.6"), BodyStatsService.parseNumber("58.6"));
        assertNull(BodyStatsService.parseNumber(""));
        BigDecimal w = new BigDecimal("58.6");
        BigDecimal h = new BigDecimal("160");
        assertNull(BodyStatsService.checkInput(TODAY, TODAY, w, h, null, null, "", "", null));
        assertNotNull(BodyStatsService.checkInput(TODAY.plusDays(1), TODAY, w, h, null, null, "", "", null));
        assertNotNull(BodyStatsService.checkInput(TODAY, TODAY, new BigDecimal("400"), h, null, null, "", "", null));
        assertNotNull(BodyStatsService.checkInput(TODAY, TODAY, w, new BigDecimal("90"), null, null, "", "", null));
        assertNotNull(BodyStatsService.checkInput(TODAY, TODAY, w, h, new BigDecimal("-1"), null, "abc", "", null));
    }

    @Test
    void tilesShowLatestAndChangeSinceFirst() {
        List<BodyStatTile> tiles = BodyStatsService.buildTiles(List.of(
                row("2026-09-05", "62.4", "31"), row("2026-10-10", "60.1", null), row("2026-10-24", "58.6", "27.9")));
        assertEquals("58,6", tiles.get(0).getValue());
        assertEquals("−3,8 kg so với 05/09", tiles.get(0).getChange());
        assertEquals("Bình thường", tiles.get(1).getNote());
        assertEquals("27,9", tiles.get(2).getValue());
        assertEquals("—", tiles.get(3).getValue());   // chưa đo vòng eo lần nào
    }

    @Test
    void chartPlacesPointsByRealDatesAndSkipsMissingValues() {
        BodyStatsView view = new BodyStatsView();
        BodyStatsService.buildChart(view, List.of(row("2026-09-05", "62.4", "31"), row("2026-10-10", "60.1", null),
                row("2026-10-24", "58.6", "27.9")), "fat");
        assertEquals(2, view.getChartPoints().size());          // lần 10/10 không đo mỡ → bỏ
        assertEquals(44.0, view.getChartPoints().get(0).getX());  // điểm đầu ở mép trái vùng vẽ
        assertEquals(624.0, view.getChartPoints().get(1).getX()); // điểm cuối ở mép phải
        assertTrue(view.getChartPoints().get(0).getY() < view.getChartPoints().get(1).getY()); // mỡ giảm → điểm sau thấp hơn

        BodyStatsView one = new BodyStatsView();
        BodyStatsService.buildChart(one, List.of(row("2026-09-05", "62.4", null)), "weight");
        assertFalse(one.isHasChart());
    }
}
