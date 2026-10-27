package com.gms.service;

import com.gms.dto.ExerciseItem;
import com.gms.dto.ExerciseLibraryView.Access;
import com.gms.dto.ExerciseLibraryView.Option;
import com.gms.dto.ExerciseLibraryView.Period;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Thư viện bài tập: quyền mở khóa, đọc bộ lọc trên URL, sắp xếp whitelist, phân trang, nhãn tiếng Việt, lịch đồng bộ. */
class ExerciseLibraryServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 27);

    private static Period period(String start, String end, String status) {
        return new Period(LocalDate.parse(start), LocalDate.parse(end), status);
    }

    private static Access accessFor(List<Period> periods) {
        return ExerciseLibraryService.accessOf(ExerciseLibraryService.decideAccess(periods, TODAY), periods, TODAY);
    }

    // ===== Quyền mở khóa =====

    @Test
    void activePackageCoveringTodayOpensIncludingBothEdgeDays() {
        assertEquals(Access.OPEN, accessFor(List.of(period("2026-10-01", "2026-11-01", "Active"))));
        assertEquals(Access.OPEN, accessFor(List.of(period("2026-10-27", "2026-11-27", "Active"))));
        assertEquals(Access.OPEN, accessFor(List.of(period("2026-09-27", "2026-10-27", "Active"))));
    }

    @Test
    void frozenMembershipStillOpens() {
        assertEquals(Access.OPEN, accessFor(List.of(period("2026-09-01", "2026-12-01", "Frozen"))));
    }

    @Test
    void neverBoughtIsNever() {
        assertEquals(Access.NEVER, accessFor(List.of()));
        assertEquals("Thư viện bài tập là đặc quyền của hội viên. Mua gói tập hoặc gói PT để mở khóa.",
                ExerciseLibraryService.lockMessage(Access.NEVER, null));
    }

    @Test
    void endedYesterdayIsExpiredWithLatestEndDate() {
        List<Period> periods = List.of(period("2026-09-26", "2026-10-26", "Active"),
                period("2026-01-01", "2026-03-01", "Expired"));

        assertEquals(Access.EXPIRED, accessFor(periods));
        LocalDate lockDate = ExerciseLibraryService.lockDateOf(Access.EXPIRED, periods, TODAY);
        assertEquals(LocalDate.of(2026, 10, 26), lockDate);
        assertEquals("Gói của bạn đã hết hạn ngày 26/10/2026. Gia hạn gói tập hoặc gói PT để mở lại thư viện.",
                ExerciseLibraryService.lockMessage(Access.EXPIRED, lockDate));
    }

    @Test
    void packageStartingNextWeekIsNotStarted() {
        List<Period> periods = List.of(period("2026-11-03", "2026-12-03", "Active"));

        assertEquals(Access.NOT_STARTED, accessFor(periods));
        LocalDate lockDate = ExerciseLibraryService.lockDateOf(Access.NOT_STARTED, periods, TODAY);
        assertEquals(LocalDate.of(2026, 11, 3), lockDate);
        assertEquals("Gói của bạn bắt đầu từ ngày 03/11/2026. Thư viện sẽ mở từ ngày đó.",
                ExerciseLibraryService.lockMessage(Access.NOT_STARTED, lockDate));
    }

    @Test
    void expiredStatusDoesNotOpenEvenIfDatesCoverToday() {
        assertEquals(Access.EXPIRED, accessFor(List.of(period("2026-10-01", "2026-11-01", "Expired"))));
    }

    // ===== Bộ lọc trên URL =====

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void blankQueryMeansNoFilter(String raw) {
        assertNull(ExerciseLibraryService.cleanQuery(raw));
    }

    @Test
    void queryIsTrimmedCollapsedAndCutAt50() {
        assertEquals("bench press", ExerciseLibraryService.cleanQuery("  bench    press "));
        assertEquals(50, ExerciseLibraryService.cleanQuery("x".repeat(80)).length());
    }

    @ParameterizedTest
    @CsvSource({"11, 11", "8, 8", "15, 15", "16, ", "7, ", "abc, ", "-1, ", "99999999999, "})
    void onlyKnownCategoriesAreAccepted(String raw, Integer expected) {
        assertEquals(expected, ExerciseLibraryService.parseCategory(raw));
    }

    @ParameterizedTest
    @CsvSource({"1, 1", "12, 12", "13, ", "0, ", "x, "})
    void onlyKnownEquipmentIsAccepted(String raw, Integer expected) {
        assertEquals(expected, ExerciseLibraryService.parseEquipment(raw));
    }

    @Test
    void sortIsWhitelistedSoUrlTextNeverReachesSql() {
        assertEquals("image", ExerciseLibraryService.parseSort("image"));
        assertEquals("name", ExerciseLibraryService.parseSort("name"));
        assertEquals("name", ExerciseLibraryService.parseSort(null));
        assertEquals("name", ExerciseLibraryService.parseSort("Name; DROP TABLE Exercises--"));
        assertEquals("Name ASC, ExerciseId ASC", ExerciseLibraryService.orderByOf("name"));
        assertTrue(ExerciseLibraryService.orderByOf("image").startsWith("CASE WHEN ImageUrl IS NULL"));
    }

    @ParameterizedTest
    @CsvSource({"3, 3", "1, 1", "0, 1", "-4, 1", "abc, 1"})
    void pageNumberBelowOneOrTextFallsBackToFirst(String raw, int expected) {
        assertEquals(expected, ExerciseLibraryService.parsePage(raw));
    }

    // ===== Phân trang =====

    @ParameterizedTest
    @CsvSource({"0, 1", "1, 1", "12, 1", "13, 2", "78, 7", "812, 68"})
    void totalPagesRoundsUpAndEmptyIsOnePage(int total, int expected) {
        assertEquals(expected, ExerciseLibraryService.totalPages(total, 12));
    }

    @Test
    void pageLinksShowFiveAroundCurrentAndStayInsideRange() {
        assertEquals(List.of("4", "5", "6", "7", "8"), values(ExerciseLibraryService.pageLinks(6, 20)));
        assertEquals(List.of("1", "2", "3", "4", "5"), values(ExerciseLibraryService.pageLinks(1, 20)));
        assertEquals(List.of("16", "17", "18", "19", "20"), values(ExerciseLibraryService.pageLinks(20, 20)));
        assertEquals(List.of("1", "2"), values(ExerciseLibraryService.pageLinks(2, 2)));
        assertTrue(ExerciseLibraryService.pageLinks(6, 20).get(2).isSelected());
    }

    private static List<String> values(List<Option> options) {
        return options.stream().map(Option::getValue).toList();
    }

    // ===== Nhãn tiếng Việt =====

    @Test
    void labelsAreTranslatedAndUnknownIdsSkipped() {
        ExerciseItem item = new ExerciseItem();
        item.setCategoryId(11);
        item.setEquipmentIds(List.of(1, 8, 99));
        item.setMuscleIds(List.of(4, 5, 77));

        ExerciseLibraryService.fillLabels(item);

        assertEquals("Ngực", item.getCategoryLabel());
        assertEquals("Thanh đòn, Ghế tập", item.getEquipmentText());
        assertEquals("Ngực, Tay sau", item.getMuscleText());
    }

    @Test
    void noEquipmentReadsBodyweightAndUnknownCategoryIsOther() {
        ExerciseItem item = new ExerciseItem();
        item.setCategoryId(42);

        ExerciseLibraryService.fillLabels(item);

        assertEquals("Khác", item.getCategoryLabel());
        assertEquals("Không dụng cụ", item.getEquipmentText());
        assertEquals("", item.getMuscleText());
    }

    @Test
    void categoryChipsKeepFixedOrderAndShowZeroCounts() {
        List<Option> chips = ExerciseLibraryService.categoryOptions(Map.of(11, 78, 12, 105), 12);

        assertEquals(8, chips.size());
        assertEquals("Ngực", chips.get(0).getLabel());
        assertEquals(78, chips.get(0).getCount());
        assertTrue(chips.get(1).isSelected());
        assertEquals(0, chips.get(7).getCount());
        assertFalse(chips.get(0).isSelected());
    }

    // ===== Lịch đồng bộ với wger =====

    @Test
    void syncWhenEmptyOrOneDayOld() {
        LocalDateTime synced = LocalDateTime.of(2026, 10, 26, 8, 15);

        assertTrue(ExerciseLibraryService.needsSync(null, synced));
        assertFalse(ExerciseLibraryService.needsSync(synced, synced.plusHours(23).plusMinutes(59)));
        assertTrue(ExerciseLibraryService.needsSync(synced, synced.plusDays(1)));
    }

    @Test
    void retryOnlyFiveMinutesAfterAFailure() {
        LocalDateTime failed = LocalDateTime.of(2026, 10, 27, 9, 0);

        assertTrue(ExerciseLibraryService.canRetry(null, failed));
        assertFalse(ExerciseLibraryService.canRetry(failed, failed.plusMinutes(4)));
        assertTrue(ExerciseLibraryService.canRetry(failed, failed.plusMinutes(5)));
    }
}
