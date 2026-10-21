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
        assertEquals(4, view.getEndedCount());       // 1, 2, 3 và buổi 4 đã qua giờ chờ chốt
    }

    @Test
    void daysUntilFocusCountsFromToday() {
        List<MyPtSessionRow> sessions = new ArrayList<>();
        sessions.add(session(1, "Scheduled", LocalDate.of(2026, 10, 22)));   // Thứ 5, còn 3 ngày
        MyPtSessionService.applyStatus(sessions.get(0), NOW, null);

        MyBookingView view = new MyBookingView();
        MyBookingService.countSessions(view, sessions, NOW);

        assertEquals(3, view.getDaysUntilFocus());
        assertEquals(0, view.getEndedCount());
    }

    @Test
    void noFocusWhenEverySessionIsOver() {
        List<MyPtSessionRow> sessions = new ArrayList<>();
        sessions.add(session(1, "CheckedIn", LocalDate.of(2026, 10, 12)));
        MyPtSessionService.applyStatus(sessions.get(0), NOW, null);

        MyBookingView view = new MyBookingView();
        MyBookingService.countSessions(view, sessions, NOW);

        assertNull(view.getFocusSession());
        assertEquals(1, view.getEndedCount());
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

    // ---------------------------------------------------------------- dữ liệu mẫu

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
