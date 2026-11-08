package com.gms.service;

import com.gms.dto.MyBookingView;
import com.gms.dto.MyPtPackageView;
import com.gms.dto.MyPtSessionRow;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Kiểm tra phần tính toán màn "Gói của tôi" (không cần DB). */
class MyBookingServiceTest {

    /** "Bây giờ" cố định: Thứ 2, 19/10/2026 lúc 14:20. */
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 19, 14, 20);

    // ---------------------------------------------------------------- countSessions

    @Test
    void countsAddUpToTotalAndFocusFirstRemaining() {
        List<MyPtSessionRow> sessions = new ArrayList<>();
        sessions.add(session(1, "CheckedIn", LocalDate.of(2026, 10, 12)));
        sessions.add(session(2, "Missed", LocalDate.of(2026, 10, 13)));
        sessions.add(session(3, "Cancelled", LocalDate.of(2026, 10, 14)));
        sessions.add(session(4, "Scheduled", LocalDate.of(2026, 10, 16)));   // đã qua, chờ chốt → pending
        sessions.add(session(5, "Scheduled", LocalDate.of(2026, 10, 19)));   // hôm nay 18:00 → còn lại, buổi để cuộn tới
        sessions.add(session(6, "Scheduled", LocalDate.of(2026, 10, 21)));   // sắp tới → còn lại
        for (MyPtSessionRow s : sessions) {
            MyPtSessionService.applyStatus(s, NOW, null);
        }

        MyBookingView view = new MyBookingView();
        MyBookingService.countSessions(view, sessions, NOW);

        assertEquals(6, view.getTotalCount());
        assertEquals(1, view.getCheckedInCount());
        assertEquals(1, view.getMissedCount());
        assertEquals(1, view.getCancelledCount());
        assertEquals(1, view.getPendingCount());
        assertEquals(2, view.getRemainingCount());
        assertEquals(view.getTotalCount(), view.getCheckedInCount() + view.getMissedCount()
                + view.getCancelledCount() + view.getRemainingCount() + view.getPendingCount());
        assertEquals(Integer.valueOf(5), view.getFocusSessionId());
        assertEquals(5, view.getFocusSession().getSessionId());
        assertEquals(0, view.getDaysUntilFocus());   // buổi tới là hôm nay
    }

    @Test
    void daysUntilFocusCountsFromToday() {
        List<MyPtSessionRow> sessions = new ArrayList<>();
        sessions.add(session(1, "Scheduled", LocalDate.of(2026, 10, 22)));   // Thứ 5, còn 3 ngày
        MyPtSessionService.applyStatus(sessions.get(0), NOW, null);

        MyBookingView view = new MyBookingView();
        MyBookingService.countSessions(view, sessions, NOW);

        assertEquals(3, view.getDaysUntilFocus());
    }

    @Test
    void noFocusWhenEverySessionIsOver() {
        List<MyPtSessionRow> sessions = new ArrayList<>();
        sessions.add(session(1, "CheckedIn", LocalDate.of(2026, 10, 12)));
        MyPtSessionService.applyStatus(sessions.get(0), NOW, null);

        MyBookingView view = new MyBookingView();
        MyBookingService.countSessions(view, sessions, NOW);

        assertNull(view.getFocusSession());
    }

    // ---------------------------------------------------------------- pickPackage

    @Test
    void picksPackageFromUrlOnlyIfItBelongsToCustomer() {
        MyBookingView view = viewWith(pkg(10, "Active"), pkg(20, "Expired"));

        assertEquals(20, MyBookingService.pickPackage(view, "20").getSubscriptionId());
        assertEquals(10, MyBookingService.pickPackage(view, "999").getSubscriptionId());   // gói người khác → mặc định
        assertEquals(10, MyBookingService.pickPackage(view, "abc").getSubscriptionId());
        assertEquals(10, MyBookingService.pickPackage(view, null).getSubscriptionId());
    }

    @Test
    void singleKeyShowsSingleSessionsOnlyWhenThereAreAny() {
        MyBookingView view = viewWith(pkg(10, "Active"));
        assertEquals(10, MyBookingService.pickPackage(view, "single").getSubscriptionId());   // chưa có buổi lẻ

        view.setSingleSessionCount(2);
        assertNull(MyBookingService.pickPackage(view, "single"));                            // null = xem buổi lẻ
    }

    @Test
    void defaultsToEndedPackageWhenNoActiveOne() {
        MyBookingView view = viewWith(pkg(20, "Expired"));
        assertEquals(20, MyBookingService.pickPackage(view, null).getSubscriptionId());
    }

    // ---------------------------------------------------------------- applyWeekTemplate

    @Test
    void standardPackageIsMondayToFridayAtOneTime() {
        List<MyPtSessionRow> sessions = weeks(2, new int[]{1, 2, 3, 4, 5}, new int[]{18, 18, 18, 18, 18});
        MyBookingView view = new MyBookingView();
        MyBookingService.applyWeekTemplate(view, sessions);

        assertEquals("T2 – T6 lúc 18:00", view.getTemplateSummary());
        assertEquals(10, view.getTemplateCount());
        assertTrue(view.getOffPattern().isEmpty());
        for (var slot : view.getWeekTemplate()) {
            assertEquals("main", slot.getKind());
            assertEquals(LocalTime.of(19, 30), slot.getEndTime());
        }
    }

    @Test
    void wednesdayAtOtherFixedTimeIsOddNotException() {
        // T2, T3, T6 lúc 18:00 · T4 lúc 07:00 · T5 không có buổi
        List<MyPtSessionRow> sessions = weeks(3, new int[]{1, 2, 3, 5}, new int[]{18, 18, 7, 18});
        MyBookingView view = new MyBookingView();
        MyBookingService.applyWeekTemplate(view, sessions);

        assertEquals("T2 · T3 · T6 lúc 18:00, T4 lúc 07:00", view.getTemplateSummary());
        assertEquals("main", view.getWeekTemplate().get(0).getKind());
        assertEquals("odd", view.getWeekTemplate().get(2).getKind());
        assertEquals(LocalTime.of(7, 0), view.getWeekTemplate().get(2).getStartTime());
        assertEquals("none", view.getWeekTemplate().get(3).getKind());
        assertEquals(12, view.getTemplateCount());
        assertTrue(view.getOffPattern().isEmpty());
    }

    @Test
    void movedSessionsGoToOffPatternIncludingWeekend() {
        List<MyPtSessionRow> sessions = weeks(3, new int[]{1, 3, 5}, new int[]{18, 18, 18});
        sessions.get(1).setStartTime(LocalTime.of(9, 0));                 // 1 buổi T4 dời giờ
        MyPtSessionRow saturday = session(99, "CheckedIn", LocalDate.of(2026, 10, 10));   // T7 do đổi buổi
        saturday.setStartTime(LocalTime.of(8, 15));
        sessions.add(saturday);
        MyBookingView view = new MyBookingView();
        MyBookingService.applyWeekTemplate(view, sessions);

        assertEquals("T2 · T4 · T6 lúc 18:00", view.getTemplateSummary());
        assertEquals(8, view.getTemplateCount());
        assertEquals(2, view.getOffPattern().size());
    }

    @Test
    void dayWithoutMajorityIsMixed() {
        // T2 có 4 buổi ở 4 giờ khác nhau → không cố định
        List<MyPtSessionRow> sessions = new ArrayList<>();
        int[] hours = {6, 9, 15, 18};
        for (int i = 0; i < 4; i++) {
            MyPtSessionRow s = session(i + 1, "CheckedIn", LocalDate.of(2026, 9, 7).plusWeeks(i));
            s.setStartTime(LocalTime.of(hours[i], 0));
            sessions.add(s);
        }
        MyBookingView view = new MyBookingView();
        MyBookingService.applyWeekTemplate(view, sessions);

        assertEquals("mixed", view.getWeekTemplate().get(0).getKind());
        assertNull(view.getTemplateSummary());
        assertEquals(4, view.getOffPattern().size());
    }

    @Test
    void tieKeepsEarlierTime() {
        // T2: 2 buổi 07:00 + 2 buổi 18:00 → hòa 50/50, chọn 07:00 để kết quả ổn định
        List<MyPtSessionRow> sessions = new ArrayList<>();
        int[] hours = {18, 7, 18, 7};
        for (int i = 0; i < 4; i++) {
            MyPtSessionRow s = session(i + 1, "CheckedIn", LocalDate.of(2026, 9, 7).plusWeeks(i));
            s.setStartTime(LocalTime.of(hours[i], 0));
            sessions.add(s);
        }
        MyBookingView view = new MyBookingView();
        MyBookingService.applyWeekTemplate(view, sessions);

        assertEquals(LocalTime.of(7, 0), view.getWeekTemplate().get(0).getStartTime());
        assertEquals(2, view.getOffPattern().size());
    }

    // ---------------------------------------------------------------- moveNote / markWeeks / watch list

    @Test
    void moveNoteSaysWhoMovedAndWhichSessionItReplaced() {
        var move = new com.gms.dto.SessionMoveInfo(1, "PtReschedule", LocalDate.of(2026, 10, 6), LocalTime.of(18, 0));
        assertEquals("PT dời lịch · thay buổi T3 06/10", MyBookingService.moveNote(move));
        assertEquals("Xếp giờ khác từ đầu vì khung cố định ngày đó đã kín", MyBookingService.moveNote(null));
    }

    @Test
    void firstSessionOfEachWeekExceptTheFirstWeekIsMarked() {
        List<MyPtSessionRow> sessions = weeks(2, new int[]{1, 3}, new int[]{18, 18});
        MyBookingService.markWeeks(sessions);

        assertFalse(sessions.get(0).isFirstOfWeek());
        assertFalse(sessions.get(1).isFirstOfWeek());
        assertTrue(sessions.get(2).isFirstOfWeek());
        assertFalse(sessions.get(3).isFirstOfWeek());
    }

    @Test
    void watchListOnlyUpcomingMissingGymPass() {
        List<MyPtSessionRow> sessions = new ArrayList<>();
        sessions.add(session(1, "Missed", LocalDate.of(2026, 10, 5)));
        sessions.add(session(2, "CheckedIn", LocalDate.of(2026, 10, 7)));
        sessions.add(session(3, "Cancelled", LocalDate.of(2026, 10, 9)));
        sessions.add(session(4, "Scheduled", LocalDate.of(2026, 10, 21)));
        for (MyPtSessionRow s : sessions) {
            MyPtSessionService.applyStatus(s, NOW, null);
        }
        MyPtSessionService.applyStatus(sessions.get(3), NOW, "Bạn chưa có Membership hoặc DayPass hợp lệ cho ngày tập này.");
        sessions.get(3).setNoGymPass(true);
        MyPtSessionRow frozen = session(5, "Scheduled", LocalDate.of(2026, 10, 23));   // bảo lưu → không vào Cần để ý
        MyPtSessionService.applyStatus(frozen, NOW, "Buổi tập nằm trong kỳ bảo lưu.");
        sessions.add(frozen);

        List<MyPtSessionRow> watch = MyBookingService.buildWatchList(sessions);
        assertEquals(1, watch.size());                    // chỉ buổi sắp tới có cảnh báo
        assertEquals(4, watch.get(0).getSessionId());

    }

    @Test
    void groupByReasonMergesSameStatusAndReason() {
        List<MyPtSessionRow> rows = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            MyPtSessionRow s = session(i, "Missed", LocalDate.of(2026, 10, i + 4));
            MyPtSessionService.applyStatus(s, NOW, null);
            s.setMissedReason("NoMembership");
            rows.add(s);
        }
        rows.forEach(s -> MyPtSessionService.applyStatus(s, NOW, null));
        MyPtSessionRow cancelled = session(9, "Cancelled", LocalDate.of(2026, 10, 9));
        cancelled.setCancelType("GymClosed");
        MyPtSessionService.applyStatus(cancelled, NOW, null);
        rows.add(cancelled);

        var groups = MyBookingService.groupByReason(rows);
        assertEquals(2, groups.size());                       // 3 buổi vắng cùng lý do → 1 nhóm; hủy phòng nghỉ → nhóm riêng
        assertEquals(3, groups.get(0).getSessions().size());
        assertEquals("cancelled", groups.get(1).getStatusKey());
    }

    // ---------------------------------------------------------------- buổi lẻ

    @Test
    void singlesUpcomingFirstThenNewestPastWithOneRebookPerPt() {
        List<MyPtSessionRow> singles = new ArrayList<>();
        singles.add(single(1, "CheckedIn", LocalDate.of(2026, 9, 21), 7));
        singles.add(single(2, "CheckedIn", LocalDate.of(2026, 10, 8), 7));
        singles.add(single(3, "Cancelled", LocalDate.of(2026, 10, 10), 8));
        singles.add(single(4, "Scheduled", LocalDate.of(2026, 10, 24), 9));
        for (MyPtSessionRow s : singles) {
            MyPtSessionService.applyStatus(s, NOW, null);
        }

        List<MyPtSessionRow> ordered = MyBookingService.orderSingles(singles, NOW);
        assertEquals(4, ordered.get(0).getSessionId());
        assertEquals(3, ordered.get(1).getSessionId());
        assertEquals(2, ordered.get(2).getSessionId());
        assertFalse(ordered.get(1).isRebook());   // buổi bị hủy → không gợi ý đặt lại
        assertTrue(ordered.get(2).isRebook());    // buổi gần nhất với PT 7
        assertFalse(ordered.get(3).isRebook());   // PT 7 đã có nút ở buổi mới hơn
    }

    @Test
    void recentSinglesCountLast30DaysWithoutCancelled() {
        List<MyPtSessionRow> singles = new ArrayList<>();
        singles.add(single(1, "CheckedIn", LocalDate.of(2026, 9, 1), 7));    // quá 30 ngày
        singles.add(single(2, "CheckedIn", LocalDate.of(2026, 10, 1), 7));
        singles.add(single(3, "Cancelled", LocalDate.of(2026, 10, 10), 7));  // hủy → không tính
        singles.add(single(4, "Missed", LocalDate.of(2026, 10, 15), 7));
        assertEquals(2, MyBookingService.countRecentSingles(singles, NOW.toLocalDate()));
    }

    // ---------------------------------------------------------------- chooseCta

    @Test
    void ctaRenewAtCounterWhenFewSessionsLeft() {
        MyBookingView view = viewWith(pkg(10, "Active"));
        view.setSelectedPackage(view.getActivePackages().get(0));
        view.setRemainingCount(2);
        view.setDaysLeft(40);
        assertEquals("renewAtCounter", MyBookingService.chooseCta(view, new ArrayList<>()));

        view.setRemainingCount(10);
        assertNull(MyBookingService.chooseCta(view, new ArrayList<>()));
        view.setDaysLeft(14);
        assertEquals("renewAtCounter", MyBookingService.chooseCta(view, new ArrayList<>()));
    }

    @Test
    void ctaRejoinForEndedPackage() {
        MyBookingView view = viewWith(pkg(20, "Expired"));
        view.setSelectedPackage(view.getEndedPackages().get(0));
        assertEquals("rejoin", MyBookingService.chooseCta(view, new ArrayList<>()));
    }

    @Test
    void ctaBuyMembershipOnlyWhenNoGymPass() {
        MyBookingView view = viewWith(pkg(10, "Active"));
        view.setSelectedPackage(view.getActivePackages().get(0));
        view.setRemainingCount(1);
        MyPtSessionRow s = session(1, "Scheduled", LocalDate.of(2026, 10, 21));
        List<MyPtSessionRow> sessions = List.of(s);

        MyPtSessionService.applyStatus(s, NOW, MyPtSessionService.CLOSED_DAY_WARNING);
        assertEquals("renewAtCounter", MyBookingService.chooseCta(view, sessions));

        MyPtSessionService.applyStatus(s, NOW, "Buổi tập nằm trong kỳ bảo lưu.");   // bảo lưu → vẫn gia hạn
        assertEquals("renewAtCounter", MyBookingService.chooseCta(view, sessions));

        MyPtSessionService.applyStatus(s, NOW, "Bạn chưa có Membership hoặc DayPass hợp lệ cho ngày tập này.");
        s.setNoGymPass(true);
        assertEquals("buyMembership", MyBookingService.chooseCta(view, sessions));
    }

    @Test
    void ctaPtPackageOnlyForFrequentSingles() {
        MyBookingView view = new MyBookingView();
        view.setSingleSelected(true);
        view.setRecentSingleCount(2);
        assertNull(MyBookingService.chooseCta(view, new ArrayList<>()));
        view.setRecentSingleCount(3);
        assertEquals("ptPackage", MyBookingService.chooseCta(view, new ArrayList<>()));
    }

    // ---------------------------------------------------------------- dữ liệu mẫu

    /** Các buổi trong n tuần kể từ T2 07/09/2026; days[i] = thứ (1 = T2), hours[i] = giờ bắt đầu. */
    private static List<MyPtSessionRow> weeks(int n, int[] days, int[] hours) {
        List<MyPtSessionRow> list = new ArrayList<>();
        int id = 1;
        for (int w = 0; w < n; w++) {
            for (int i = 0; i < days.length; i++) {
                LocalDate date = LocalDate.of(2026, 9, 7).plusWeeks(w).plusDays(days[i] - 1);
                MyPtSessionRow s = session(id++, "CheckedIn", date);
                s.setStartTime(LocalTime.of(hours[i], 0));
                s.setEndTime(LocalTime.of(hours[i], 0).plusMinutes(90));
                list.add(s);
            }
        }
        return list;
    }

    private static MyPtSessionRow single(int id, String status, LocalDate date, int ptId) {
        MyPtSessionRow row = session(id, status, date);
        row.setType("SingleSession");
        row.setPtEmployeeId(ptId);
        return row;
    }

    private static MyPtSessionRow session(int id, String status, LocalDate date) {
        MyPtSessionRow row = new MyPtSessionRow();
        row.setSessionId(id);
        row.setStatus(status);
        row.setType("PTSingle");
        row.setSlotDate(date);
        row.setStartTime(LocalTime.of(18, 0));
        row.setEndTime(LocalTime.of(19, 30));
        return row;
    }

    private static MyPtPackageView pkg(int id, String status) {
        MyPtPackageView p = new MyPtPackageView();
        p.setSubscriptionId(id);
        p.setStatus(status);
        return p;
    }

    private static MyBookingView viewWith(MyPtPackageView... packages) {
        MyBookingView view = new MyBookingView();
        for (MyPtPackageView p : packages) {
            if (p.isActive()) {
                view.getActivePackages().add(p);
            } else {
                view.getEndedPackages().add(p);
            }
        }
        return view;
    }
}
