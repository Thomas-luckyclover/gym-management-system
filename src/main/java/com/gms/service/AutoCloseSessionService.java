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
 * SERVICE CỦA JOB-04 — TỰ CHỐT BUỔI PT ĐÃ QUA NGÀY MÀ CÒN "Scheduled".
 *
 * NGHIỆP VỤ
 *   PT không điểm danh bằng tay. Sau khi ngày tập kết thúc, hệ thống tự chốt mỗi buổi còn Scheduled thành:
 *     Missed / MembershipFrozen   cả buổi không ai vào được gym, và có người đang bảo lưu
 *     Missed / NoMembership       cả buổi không ai vào được gym, không ai bảo lưu
 *     CheckedIn                   có người vào được gym VÀ có check-in trong ngày tập
 *     Missed / NoCheckIn          có người vào được gym nhưng không ai check-in
 *   "Vào được gym" = có Membership HOẶC vé ngày (DayPass) phủ ngày tập, VÀ không bảo lưu ngày đó.
 *
 * AI GỌI?  BackgroundJobScheduler (lượt 00:05 mỗi ngày) → AutoCloseUnmarkedSessionsJob.run() → runJob().
 *
 * CÁC NHÓM HÀM
 *   1. CHẠY JOB         runJob(), runJob(today)
 *                         ├─ B1 findOverdueSessionIds   lấy danh sách mã buổi cần chốt
 *                         └─ B2 closeOneSession         chốt từng buổi, mỗi buổi 1 transaction
 *   2. QUYẾT ĐỊNH KẾT QUẢ evaluate(data)               luật chọn Status + MissedReason (không đụng DB)
 *   3. DÙNG CHUNG       openConnection()
 *
 * Service giữ quy tắc nghiệp vụ; AutoCloseSessionDAO chỉ đọc / ghi SQL.
 */
public class AutoCloseSessionService {

    // sessionDAO      đọc + cập nhật bảng Sessions (AutoCloseSessionDAO)
    // auditLogService ghi nhật ký mỗi buổi đã chốt vào AuditLogs
    private final AutoCloseSessionDAO sessionDAO;
    private final AuditLogService auditLogService;

    /** Job dùng constructor này: tự tạo DAO và AuditLogService thật. */
    public AutoCloseSessionService() {
        this(new AutoCloseSessionDAO(), new AuditLogService());
    }

    /** Test dùng constructor này: truyền DAO giả để không sửa database thật. */
    public AutoCloseSessionService(AutoCloseSessionDAO sessionDAO,
            AuditLogService auditLogService) {
        this.sessionDAO = sessionDAO;
        this.auditLogService = auditLogService;
    }


    // =================================================================================================
    // 1. CHẠY JOB
    // =================================================================================================

    /**
     * HÀM CHÍNH — AutoCloseUnmarkedSessionsJob.run() gọi mỗi ngày lúc 00:05.
     * Chạy JOB-04 với "hôm nay" theo giờ Việt Nam.
     */
    public int runJob() throws SQLException {
        return runJob(DateTimeUtil.today());
    }

    /**
     * HÀM CHÍNH — runJob() ở trên gọi; test có thể gọi thẳng với một ngày cố định.
     *
     * @param today ngày hiện tại. Buổi có ngày tập TRƯỚC ngày này mới bị chốt.
     *              VD today = 24/10 → chốt buổi 23/10 trở về trước (kể cả buổi sót từ nhiều ngày trước).
     * @return số buổi đã chốt và commit thành công
     */
    public int runJob(LocalDate today) throws SQLException {
        // B1: lấy danh sách mã buổi cần chốt, VD [91, 105]. Lỗi ở đây thì dừng cả lượt để scheduler thử lại.
        List<Integer> overdueSessionIds = findOverdueSessionIds(today);
        int closedCount = 0;

        // B2: chốt từng buổi. Mỗi buổi 1 transaction riêng → một buổi lỗi không làm dừng các buổi còn lại.
        for (Integer sessionId : overdueSessionIds) {
            if (closeOneSession(sessionId, today)) {
                closedCount++;
            }
        }
        return closedCount;
    }

    /**
     * HÀM PHỤ — gọi ở B1 của runJob(today).
     * Mở 1 kết nối, nhờ DAO đọc mã các buổi Scheduled đã qua ngày, rồi đóng kết nối.
     */
    private List<Integer> findOverdueSessionIds(LocalDate today) throws SQLException {
        try (Connection connection = openConnection()) {
            return sessionDAO.findOverdueSessionIds(connection, today);
        }
    }

    /**
     * HÀM PHỤ — gọi ở B2 của runJob(today), mỗi buổi một lần.
     * Chốt 1 buổi trong 1 transaction: đọc lại → quyết định → cập nhật Sessions → ghi AuditLog → commit.
     *
     * @return true = đã chốt; false = bỏ qua (buổi đã được xử lý nơi khác) hoặc lỗi (đã in log)
     */
    private boolean closeOneSession(int sessionId, LocalDate today) {
        try (Connection connection = openConnection()) {
            // Tắt tự lưu: các câu SQL dưới chỉ có hiệu lực khi commit().
            connection.setAutoCommit(false);

            try {
                // B1: khóa và đọc lại buổi. null = buổi đã được luồng khác xử lý hoặc chưa đủ điều kiện.
                SessionAutoCloseData data = sessionDAO.findDataForClosing(
                        connection, sessionId, today);
                if (data == null) {
                    connection.rollback();
                    return false;
                }

                // B2: Service quyết định Status + MissedReason theo luật JOB-04.
                Outcome outcome = evaluate(data);

                // B3: cập nhật; DAO kiểm tra lại "vẫn còn Scheduled" ngay lúc ghi.
                boolean updated = sessionDAO.closeScheduledSession(
                        connection,
                        sessionId,
                        today,
                        outcome.status(),
                        outcome.missedReason());
                if (!updated) {
                    connection.rollback();
                    return false;
                }

                // B4: ghi nhật ký trong CÙNG transaction; ghi log lỗi thì cập nhật buổi cũng bị hủy.
                //     Người thực hiện = null (hệ thống). VD: Sessions #91, Status: Scheduled → Missed, NoCheckIn.
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

                // B5: Sessions và AuditLog cùng thành công → lưu thật.
                connection.commit();
                return true;
            } catch (SQLException | RuntimeException exception) {
                // Lỗi giữa chừng → hủy mọi thay đổi của buổi này.
                connection.rollback();
                throw exception;
            }
        } catch (SQLException | RuntimeException exception) {
            // In lỗi của buổi này; vòng lặp ở runJob vẫn chốt các buổi tiếp theo.
            System.err.println("[JOB-04] SessionId=" + sessionId);
            exception.printStackTrace();
            return false;
        }
    }


    // =================================================================================================
    // 2. QUYẾT ĐỊNH KẾT QUẢ (không đụng database → test được bằng dữ liệu giả)
    // =================================================================================================

    /**
     * Kết quả Service chọn cho 1 buổi, trước khi DAO ghi vào Sessions.
     * record = lớp chỉ chứa dữ liệu; Java tự tạo constructor + getter: outcome.status(), outcome.missedReason().
     * VD: (MISSED, NO_CHECK_IN) hoặc (CHECKED_IN, null).
     */
    public record Outcome(Session.Status status, Session.MissedReason missedReason) {
    }

    /**
     * HÀM CHÍNH — closeOneSession gọi ở B2.
     * Xét lần lượt: MembershipFrozen → NoMembership → CheckedIn → NoCheckIn; gặp điều kiện nào trước thì trả luôn.
     *
     * Membership hoặc DayPass đều tính là có quyền vào gym. Check-in chỉ cho biết khách đã vào gym trong ngày,
     * không khẳng định khách đã tập cùng PT.
     *
     * VD gói đôi: người đứng tên thiếu membership, người tập cùng có membership và có check-in → CheckedIn.
     */
    public static Outcome evaluate(SessionAutoCloseData data) {
        boolean coupleSession = isCoupleSession(data);

        // B1: từng người có dùng được gym vào ngày tập không.
        boolean holderCanUseGym = canUseGym(
                data.hasHolderMembership(),
                data.hasHolderDayPass(),
                data.isHolderFrozen());
        boolean partnerCanUseGym = coupleSession && canUseGym(
                data.hasPartnerMembership(),
                data.hasPartnerDayPass(),
                data.isPartnerFrozen());

        // B2: gói 1 người (PTSingle / SingleSession) xét 1 người; gói đôi (PTCouple) chỉ "bị chặn" khi CẢ HAI bị chặn.
        boolean allCustomersCannotUseGym = !holderCanUseGym
                && (!coupleSession || !partnerCanUseGym);

        // B3: cả buổi bị chặn và có người đang bảo lưu → Missed / MembershipFrozen.
        if (allCustomersCannotUseGym && hasFrozenCustomer(data, coupleSession)) {
            return new Outcome(Session.Status.MISSED,
                    Session.MissedReason.MEMBERSHIP_FROZEN);
        }

        // B4: cả buổi bị chặn, không ai bảo lưu → Missed / NoMembership.
        if (allCustomersCannotUseGym) {
            return new Outcome(Session.Status.MISSED,
                    Session.MissedReason.NO_MEMBERSHIP);
        }

        // B5: còn ít nhất 1 người vào được gym → giờ mới xét check-in trong ngày.
        if (data.hasCheckInOnSessionDate()) {
            return new Outcome(Session.Status.CHECKED_IN, null);
        }

        // B6: không ai check-in → Missed / NoCheckIn.
        return new Outcome(Session.Status.MISSED, Session.MissedReason.NO_CHECK_IN);
    }

    /** HÀM PHỤ — gọi ở đầu evaluate. true khi buổi thuộc gói PT đôi; loại khác chỉ có 1 khách. */
    private static boolean isCoupleSession(SessionAutoCloseData data) {
        return data.getPtSubscriptionType() == Package.Type.PT_COUPLE;
    }

    /**
     * HÀM PHỤ — gọi ở B1 của evaluate (1 lần cho người đứng tên, 1 lần cho người tập cùng).
     * Một khách dùng được gym = có Membership hoặc DayPass, VÀ không đang bảo lưu.
     * VD có DayPass nhưng đang bảo lưu → false (DayPass không gỡ được bảo lưu).
     * SessionReminderDAO (JOB-08) dùng đúng luật này trong SQL để nhắc khách trước 1 ngày.
     */
    private static boolean canUseGym(boolean hasMembership, boolean hasDayPass,
            boolean isFrozen) {
        return (hasMembership || hasDayPass) && !isFrozen;
    }

    /** HÀM PHỤ — gọi ở B3 của evaluate. Trong nhóm khách của buổi có ai đang bảo lưu không. */
    private static boolean hasFrozenCustomer(SessionAutoCloseData data,
            boolean coupleSession) {
        return data.isHolderFrozen()
                || (coupleSession && data.isPartnerFrozen());
    }


    // =================================================================================================
    // 3. DÙNG CHUNG
    // =================================================================================================

    /**
     * HÀM PHỤ DÙNG CHUNG — gọi ở findOverdueSessionIds và closeOneSession.
     * Mở 1 kết nối SQL Server; try-with-resources ở nơi gọi tự đóng kết nối.
     * Để protected: class test có thể kế thừa và thay cách mở kết nối.
     */
    protected Connection openConnection() throws SQLException {
        return DBContext.getConnection();
    }
}
