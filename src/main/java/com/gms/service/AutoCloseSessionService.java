package com.gms.service;

import com.gms.dao.AutoCloseSessionDAO;
import com.gms.dto.SessionAutoCloseData;
import com.gms.entity.Package;
import com.gms.entity.Session;
import com.gms.util.DBContext;
import com.gms.util.DateTimeUtil;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

/**
 * JOB-04 — chốt các buổi PT đã qua ngày mà vẫn Scheduled.
 * Mỗi buổi chốt thành 1 trong 3 kết quả (evaluate):
 *   phòng đóng / buổi ngoài giờ mở cửa → Cancelled (GymClosed), hoa hồng PT = 0
 *   khách không có quyền vào gym       → Missed (MembershipFrozen | NoMembership)
 *   có check-in trong ngày tập         → CheckedIn · không check-in → Missed (NoCheckIn)
 * Mỗi buổi 1 transaction riêng: 1 buổi lỗi không làm hỏng các buổi khác.
 */
public class AutoCloseSessionService {
    private final AutoCloseSessionDAO sessionDAO;
    private final AuditLogService auditLogService;

    
    // Job dùng constructor này, constructor ko tham số, và constructor này tự tạo DAO thật
    public AutoCloseSessionService() {
        this(new AutoCloseSessionDAO(), new AuditLogService());
    }

    // Tets gọi constructor có tham số để truyền DAO giả vào, ko cần DB thật
    public AutoCloseSessionService(AutoCloseSessionDAO sessionDAO,
            AuditLogService auditLogService) {
        this.sessionDAO = sessionDAO;
        this.auditLogService = auditLogService;
    }

    // Chạy job với ngày hôm nay (BackgroundJobScheduler gọi).
    // trả về số buổi int
    public int runJob() throws SQLException {
        return runJob(DateTimeUtil.today());
    }

    // Chạy job với 1 ngày cho trước (test gọi thẳng). Trả về: số buổi đã chốt.
    public int runJob(LocalDate today) throws SQLException {
        // B1: lấy Id các buổi Scheduled có ngày tập < hôm nay (AutoCloseSessionDAO.findOverdueSessionIds)
        List<Integer> overdueSessionIds = findOverdueSessionIds(today);
        int closedCount = 0;

        // B2: chốt TỪNG buổi, mỗi buổi 1 transaction (closeOneSession); đếm số buổi chốt được
        for (Integer sessionId : overdueSessionIds) {
            if (closeOneSession(sessionId, today)) {
                closedCount++;
            }
        }
        return closedCount;
    }

    /**
     * HÀM PHỤ (runJob B1) — mở kết nối, đọc danh sách Id buổi quá hạn. Trả về: List<Integer> SessionId.
     */
    private List<Integer> findOverdueSessionIds(LocalDate today) throws SQLException {
        try (Connection connection = openConnection()) {
            return sessionDAO.findOverdueSessionIds(connection, today);
        }
    }

    /**
     * HÀM PHỤ (runJob B2) — chốt 1 buổi trong 1 transaction. Trả về: true = đã chốt, false = bỏ qua / lỗi.
     */
    private boolean closeOneSession(int sessionId, LocalDate today) {
        try (Connection connection = openConnection()) {
            // Mở transaction
            connection.setAutoCommit(false);

            try {
                // B1: AutoCloseSessionDAO.findDataForClosing: KHÓA dòng buổi (UPDLOCK) + đọc đủ dữ liệu để quyết định
                //     → SessionAutoCloseData. null = buổi đã được PT xử lý / không còn Scheduled → rollback, bỏ qua
                SessionAutoCloseData data = sessionDAO.findDataForClosing(
                        connection, sessionId, today);
                if (data == null) {
                    connection.rollback();
                    return false;
                }

                // B2: quyết định kết quả (evaluate) → Outcome(status, missedReason, cancelType)
                Outcome outcome = evaluate(data);

                // B3: ghi kết quả: Sessions: Scheduled → CheckedIn | Missed | Cancelled, IsAutoClosed = 1
                //     (không cập nhật được dòng nào = có người vừa xử lý → rollback)
                boolean updated = sessionDAO.closeScheduledSession(
                        connection,
                        sessionId,
                        today,
                        outcome.status(),
                        outcome.missedReason(),
                        outcome.cancelType());
                if (!updated) {
                    connection.rollback();
                    return false;
                }

                // B4: AuditLogs: thêm 1 dòng "JOB-04" (Status cũ → mới, lý do)
                auditLogService.logInTransaction(
                        connection,
                        null,
                        "JOB-04",
                        "Sessions",
                        sessionId,
                        "Status",
                        Session.Status.SCHEDULED.getDbValue(),
                        outcome.status().getDbValue(),
                        outcome.missedReason() == null
                                ? null
                                : outcome.missedReason().getDbValue());

                // B5: commit → xong 1 buổi
                connection.commit();
                return true;
            } catch (SQLException | RuntimeException exception) {
                // Lỗi giữa chừng → rollback buổi này
                connection.rollback();
                throw exception;
            }
        } catch (SQLException | RuntimeException exception) {
            // In lỗi của buổi này ra log, job vẫn chạy tiếp buổi sau.
            System.err.println("[JOB-04] SessionId=" + sessionId);
            exception.printStackTrace();
            return false;
        }
    }

    /**
     * Kết quả chốt 1 buổi: status (Sessions.Status) + missedReason (khi Missed) + cancelType (khi Cancelled).
     */
    public record Outcome(Session.Status status, Session.MissedReason missedReason,
            Session.CancelType cancelType) {
    }

    /**
     * Luật chốt 1 buổi (không đọc DB — test gọi thẳng). Trả về: Outcome.
     */
    public static Outcome evaluate(SessionAutoCloseData data) {
        // B1: phòng nghỉ cả ngày / không đọc được giờ mở cửa / buổi nằm ngoài giờ mở cửa ngày đó
        //     → Cancelled + GymClosed (không phải lỗi của khách, PT không có hoa hồng)
        if (data.isGymClosedAllDay()
                || data.getGymOpenTime() == null
                || data.getGymCloseTime() == null
                || data.getSlotStartTime().isBefore(data.getGymOpenTime())
                || data.getSlotEndTime().isAfter(data.getGymCloseTime())) {
            return new Outcome(Session.Status.CANCELLED, null, Session.CancelType.GYM_CLOSED);
        }

        // B2: gói đôi thì xét cả người tập cùng
        boolean coupleSession = isCoupleSession(data);

        // B3: từng người có quyền vào gym ngày đó không: có membership hoặc vé ngày, và không bảo lưu
        boolean holderCanUseGym = canUseGym(
                data.hasHolderMembership(),
                data.hasHolderDayPass(),
                data.isHolderFrozen());
        boolean partnerCanUseGym = coupleSession && canUseGym(
                data.hasPartnerMembership(),
                data.hasPartnerDayPass(),
                data.isPartnerFrozen());

        // B4: KHÔNG AI vào gym được (đơn: người đứng tên; đôi: cả 2 người)
        boolean allCustomersCannotUseGym = !holderCanUseGym
                && (!coupleSession || !partnerCanUseGym);

        // B5: không ai vào được + có người đang bảo lưu → Missed (MembershipFrozen)
        if (allCustomersCannotUseGym && hasFrozenCustomer(data, coupleSession)) {
            return new Outcome(Session.Status.MISSED,
                    Session.MissedReason.MEMBERSHIP_FROZEN, null);
        }

        // B6: không ai vào được, không bảo lưu → Missed (NoMembership)
        if (allCustomersCannotUseGym) {
            return new Outcome(Session.Status.MISSED,
                    Session.MissedReason.NO_MEMBERSHIP, null);
        }

        // B7: có check-in (người đứng tên hoặc người tập cùng) trong ngày tập → CheckedIn
        if (data.hasCheckInOnSessionDate()) {
            return new Outcome(Session.Status.CHECKED_IN, null, null);
        }

        // B8: có quyền vào gym nhưng không đến → Missed (NoCheckIn)
        return new Outcome(Session.Status.MISSED, Session.MissedReason.NO_CHECK_IN, null);
    }

    /**
     * HÀM PHỤ (evaluate B2) — gói PT đôi (PTCouple)?
     */
    private static boolean isCoupleSession(SessionAutoCloseData data) {
        return data.getPtSubscriptionType() == Package.Type.PT_COUPLE;
    }

    /**
     * HÀM PHỤ (evaluate B3) — vào gym được = (có membership HOẶC vé ngày) VÀ không bảo lưu.
     */
    private static boolean canUseGym(boolean hasMembership, boolean hasDayPass,
            boolean isFrozen) {
        return (hasMembership || hasDayPass) && !isFrozen;
    }

    /**
     * HÀM PHỤ (evaluate B5) — người đứng tên (hoặc người tập cùng ở gói đôi) đang bảo lưu?
     */
    private static boolean hasFrozenCustomer(SessionAutoCloseData data,
            boolean coupleSession) {
        return data.isHolderFrozen()
                || (coupleSession && data.isPartnerFrozen());
    }

    /**
     * HÀM PHỤ — mở kết nối DB (test ghi đè để dùng kết nối giả).
     * protected nghĩa là class con được ovveride hàm này, mở cho test thay thế ok, bên ngoài ko gọi đc trừ các class cùng package
     */
    protected Connection openConnection() throws SQLException {
        return DBContext.getConnection(); // // bản thật: nối SQL Server
    }
}
