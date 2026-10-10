package com.gms.service;

import com.gms.dto.PtSessionView;
import com.gms.entity.Package;
import com.gms.entity.Session;
import java.sql.SQLException;
import java.time.LocalDateTime;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Kiểm tra điều kiện mở hộp và nhãn buổi trước khi Servlet chuyển lịch sang JSP. */
class PtDashboardServiceTest {

    private final PtDashboardService service = new PtDashboardService();
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 7, 17, 0);

    @ParameterizedTest
    @CsvSource({
        "SCHEDULED, 2026-10-07T17:01, true",
        "SCHEDULED, 2026-10-08T08:00, true",
        "SCHEDULED, 2026-10-07T17:00, false",
        "SCHEDULED, 2026-10-07T16:59, false",
        "SCHEDULED, 2026-10-06T18:00, false",
        "CHECKED_IN, 2026-10-08T08:00, false",
        "MISSED, 2026-10-08T08:00, false",
        "CANCELLED, 2026-10-08T08:00, false"
    })
    void opensDialogOnlyForScheduledSessionBeforeStart(Session.Status status,
            LocalDateTime startsAt, boolean expected) throws SQLException {
        PtSessionView session = createSession(Package.Type.PT_SINGLE, status, null, startsAt);

        service.prepareSessionDisplay(session, NOW);

        assertEquals(expected, session.isCanOpenActions());
        assertEquals(status, session.getStatus(), "Chuẩn bị màn hình không được đổi trạng thái gốc");
    }

    @ParameterizedTest
    @CsvSource({
        "SCHEDULED, Đã lên lịch, badge-warning",
        "CHECKED_IN, Đã check-in, badge-success",
        "MISSED, Vắng, badge-danger",
        "CANCELLED, Đã hủy, badge-neutral"
    })
    void displaysVietnameseStatusWithExistingBadge(Session.Status status,
            String label, String badgeClass) throws SQLException {
        PtSessionView session = createSession(Package.Type.PT_SINGLE, status, null, NOW);

        service.prepareSessionDisplay(session, NOW);

        assertEquals(label, session.getStatusLabel());
        assertEquals(badgeClass, session.getStatusBadgeClass());
    }

    @ParameterizedTest
    @CsvSource({
        "PT_SINGLE, PT cá nhân",
        "PT_COUPLE, PT đôi",
        "SINGLE_SESSION, Buổi lẻ"
    })
    void displaysSubscriptionType(Package.Type type, String label) throws SQLException {
        PtSessionView session = createSession(type, Session.Status.SCHEDULED, null, NOW);

        service.prepareSessionDisplay(session, NOW);

        assertEquals(label, session.getSubscriptionTypeLabel());
    }

    @ParameterizedTest
    @CsvSource({
        "NO_CHECK_IN, Khách không check-in",
        "NO_MEMBERSHIP, Không có membership hợp lệ",
        "MEMBERSHIP_FROZEN, Membership đang bảo lưu"
    })
    void displaysMissedReasonOnlyOnMissedSession(Session.MissedReason reason,
            String label) throws SQLException {
        PtSessionView session = createSession(Package.Type.PT_COUPLE, Session.Status.MISSED, reason, NOW);
        service.prepareSessionDisplay(session, NOW);
        assertEquals(label, session.getMissedReasonLabel());

        PtSessionView scheduled = createSession(Package.Type.PT_COUPLE, Session.Status.SCHEDULED, reason, NOW);
        service.prepareSessionDisplay(scheduled, NOW);
        assertNull(scheduled.getMissedReasonLabel());
    }

    @Test
    void rejectsNonPtSubscriptionRatherThanRenderingWrongLabel() {
        PtSessionView session = createSession(Package.Type.MEMBERSHIP, Session.Status.SCHEDULED, null, NOW);

        assertThrows(SQLException.class, () -> service.prepareSessionDisplay(session, NOW));
    }

    /** Tạo một buổi mẫu; thời điểm kiểm tra cố định để test không phụ thuộc giờ chạy. */
    private PtSessionView createSession(Package.Type type, Session.Status status,
            Session.MissedReason reason, LocalDateTime startsAt) {
        return new PtSessionView(10, startsAt.toLocalDate(), startsAt.toLocalTime(),
                startsAt.plusMinutes(90).toLocalTime(), "Vũ Hoài Nam",
                type == Package.Type.PT_COUPLE ? "Đỗ Lan Chi" : null, type, status, reason);
    }
}
