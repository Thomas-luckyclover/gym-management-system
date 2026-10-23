package com.gms.service;

import com.gms.dto.PtSessionView;
import com.gms.dto.PtAssignedPackageView;
import com.gms.dto.PtAttentionView;
import com.gms.entity.Package;
import com.gms.entity.Session;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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
        PtSessionView session = createSession(Package.Type.PT_COUPLE, Session.Status.MISSED, reason, NOW.minusDays(1));
        service.prepareSessionDisplay(session, NOW);
        assertEquals(label, session.getMissedReasonLabel());

        PtSessionView scheduled = createSession(Package.Type.PT_COUPLE, Session.Status.SCHEDULED, reason, NOW);
        service.prepareSessionDisplay(scheduled, NOW);
        assertNull(scheduled.getMissedReasonLabel());
    }

    @ParameterizedTest
    @CsvSource({"2026-10-07T08:00", "2026-10-08T08:00"})
    void rejectsMissedSessionBeforeItsDayHasPassed(LocalDateTime startsAt) {
        PtSessionView session = createSession(Package.Type.PT_SINGLE,
                Session.Status.MISSED, Session.MissedReason.NO_CHECK_IN, startsAt);

        assertThrows(SQLException.class, () -> service.prepareSessionDisplay(session, NOW));
        assertEquals(Session.Status.MISSED, session.getStatus());
    }

    @Test
    void rejectsNonPtSubscriptionRatherThanRenderingWrongLabel() {
        PtSessionView session = createSession(Package.Type.MEMBERSHIP, Session.Status.SCHEDULED, null, NOW);

        assertThrows(SQLException.class, () -> service.prepareSessionDisplay(session, NOW));
    }

    @Test
    void mergesByPackageIdAndKeepsBothPeopleOfCouple() {
        var first = clientPackage(11);
        var sameNameOtherPackage = clientPackage(12);
        var packages = new ArrayList<>(List.of(sameNameOtherPackage, first));
        var holder = attention(11, 6, "membership");
        var partner = attention(11, 7, "frozen");

        PtDashboardService.mergeClientAttention(packages, List.of(holder, partner));

        assertEquals(List.of(holder, partner), first.getAttentionItems());
        assertTrue(sameNameOtherPackage.getAttentionItems().isEmpty());
        assertSame(first, packages.get(0));
    }

    @Test
    void prioritizesClosedDayAndMergingAgainDoesNotDuplicateCards() {
        var normal = clientPackage(10);
        var membership = clientPackage(11);
        var closed = clientPackage(12);
        var packages = new ArrayList<>(List.of(normal, membership, closed));
        var items = List.of(attention(11, 6, "membership"), attention(12, 7, "closedDay"));

        PtDashboardService.mergeClientAttention(packages, items);
        PtDashboardService.mergeClientAttention(packages, items);

        assertEquals(List.of(closed, membership, normal), packages);
        assertEquals(1, closed.getAttentionItems().size());
        assertEquals(1, membership.getAttentionItems().size());
    }

    @Test
    void clientCardBuildsZaloLinksFromBothPhones() {
        var client = clientPackage(11);
        client.setCustomerPhone("0912 345 678");
        client.setPartnerPhone("+84 987 654 321");

        assertEquals("https://zalo.me/0912345678", client.getCustomerZaloUrl());
        assertEquals("https://zalo.me/0987654321", client.getPartnerZaloUrl());

        client.setPartnerPhone(null);   // không có SĐT → null, JSP ẩn nút Zalo
        assertNull(client.getPartnerZaloUrl());
    }

    /** Tạo hai gói cùng tên để test không vô tình ghép cảnh báo theo tên khách. */
    private static PtAssignedPackageView clientPackage(int subscriptionId) {
        var client = new PtAssignedPackageView("Khách trùng tên", null, Package.Type.PT_COUPLE, NOW.toLocalDate(), 5);
        client.setPtSubscriptionId(subscriptionId);
        return client;
    }

    /** Dữ liệu cảnh báo tối thiểu để kiểm tra ghép gói, không gọi database. */
    private static PtAttentionView attention(int subscriptionId, int customerId, String kind) {
        return new PtAttentionView(customerId, subscriptionId, "Khách trùng tên", NOW.toLocalDate(), 5,
                NOW.toLocalDate(), null, null, "PT đôi", null, "Cần chú ý", "Nội dung", kind,
                "closedDay".equals(kind) ? 274 : null);
    }

    /** Tạo một buổi mẫu; thời điểm kiểm tra cố định để test không phụ thuộc giờ chạy. */
    private PtSessionView createSession(Package.Type type, Session.Status status,
            Session.MissedReason reason, LocalDateTime startsAt) {
        return new PtSessionView(10, startsAt.toLocalDate(), startsAt.toLocalTime(),
                startsAt.plusMinutes(90).toLocalTime(), "Vũ Hoài Nam",
                type == Package.Type.PT_COUPLE ? "Đỗ Lan Chi" : null, type, status, reason);
    }
}
