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
 * Service điều phối JOB-04.
 *
 * <p>Service giữ quy tắc nghiệp vụ; DAO chỉ đọc và ghi dữ liệu SQL.</p>
 */
public class AutoCloseSessionService {


// Service cần hai đối tượng:
// - sessionDAO: đọc và cập nhật bảng Sessions.
// - auditLogService: ghi nhật ký vào AuditLogs.
    private final AutoCloseSessionDAO sessionDAO;
    private final AuditLogService auditLogService;

    public AutoCloseSessionService() {
        this(new AutoCloseSessionDAO(), new AuditLogService());
    }

    /** Constructor nhận DAO để test Service mà không phải sửa database thật. */
    public AutoCloseSessionService(AutoCloseSessionDAO sessionDAO,
            AuditLogService auditLogService) {
        this.sessionDAO = sessionDAO;
        this.auditLogService = auditLogService;
    }



    /** Mở kết nối; mỗi buổi sẽ tự đóng kết nối sau khi commit hoặc rollback. */
    // có nhiệm vụ mở kết nối SQL Server.
    // Để protected nhằm cho class test kế thừa Service và thay cách mở kết nối nếu cần
    protected Connection openConnection() throws SQLException {
        return DBContext.getConnection();
    }

    /** Kết quả Service quyết định trước khi DAO ghi vào Sessions. */
    // Đây là một kiểu dữ liệu nhỏ để Service trả về kết quả quyết định
    // record là một kiểu dữ liệu đặc biệt trong Java, bản chất vẫn là một class nhưng Java tự sinh sẵn constructor, getter, equals, hashCode, toString.
    public record Outcome(Session.Status status, Session.MissedReason missedReason) {
    }

    /**
     * Chọn trạng thái cuối cùng theo thứ tự: Frozen → NoMembership → CheckedIn → NoCheckIn.
     *
     * <p>Membership hoặc DayPass đều được tính là có quyền vào gym. Check-in chỉ cho biết
     * khách đã vào gym trong ngày, không khẳng định khách đã tập cùng PT.</p>
     */
    public static Outcome evaluate(SessionAutoCloseData data) {
        boolean coupleSession = isCoupleSession(data);

        // B1: xác định từng người có thể dùng gym vào ngày của buổi hay không.
        boolean holderCanUseGym = canUseGym(
                data.hasHolderMembership(),
                data.hasHolderDayPass(),
                data.isHolderFrozen());
        boolean partnerCanUseGym = coupleSession && canUseGym(
                data.hasPartnerMembership(),
                data.hasPartnerDayPass(),
                data.isPartnerFrozen());

        // B2: PTSingle/SingleSession xét một người; PTCouple xét cả hai người.
        boolean allCustomersCannotUseGym = !holderCanUseGym
                && (!coupleSession || !partnerCanUseGym);

        // B3: nếu cả buổi bị chặn và có người đang bảo lưu, lý do là MembershipFrozen.
        if (allCustomersCannotUseGym && hasFrozenCustomer(data, coupleSession)) {
            return new Outcome(Session.Status.MISSED,
                    Session.MissedReason.MEMBERSHIP_FROZEN);
        }

        // B4: cả buổi bị chặn nhưng không ai bảo lưu, lý do là NoMembership.
        if (allCustomersCannotUseGym) {
            return new Outcome(Session.Status.MISSED,
                    Session.MissedReason.NO_MEMBERSHIP);
        }

        // B5: còn ít nhất một người dùng được gym, lúc này mới xét check-in trong ngày.
        if (data.hasCheckInOnSessionDate()) {
            return new Outcome(Session.Status.CHECKED_IN, null);
        }

        // Không có check-in nên buổi được chốt là Missed/NoCheckIn.
        return new Outcome(Session.Status.MISSED, Session.MissedReason.NO_CHECK_IN);
    }

    /** True khi buổi thuộc gói PTCouple; các loại còn lại chỉ có một khách. */
    private static boolean isCoupleSession(SessionAutoCloseData data) {
        return data.getPtSubscriptionType() == Package.Type.PT_COUPLE;
    }

    /** Một khách dùng được gym nếu có Membership/DayPass và không đang bảo lưu. */
    private static boolean canUseGym(boolean hasMembership, boolean hasDayPass,
            boolean isFrozen) {
        return (hasMembership || hasDayPass) && !isFrozen;
    }

    /** Kiểm tra trong nhóm khách của buổi có ai đang bị bảo lưu hay không. */
    private static boolean hasFrozenCustomer(SessionAutoCloseData data,
            boolean coupleSession) {
        return data.isHolderFrozen()
                || (coupleSession && data.isPartnerFrozen());
    }

    /** Chạy JOB-04 theo ngày hiện tại ở múi giờ Việt Nam. */
    public int runJob() throws SQLException {
        return runJob(DateTimeUtil.today());
    }

    /**
     * Chạy JOB-04 cho một ngày được truyền vào.
     *
     * @return số buổi đã cập nhật và commit thành công
     */
    public int runJob(LocalDate today) throws SQLException {
        List<Integer> overdueSessionIds = findOverdueSessionIds(today);
        int closedCount = 0;

        // Mỗi mã buổi có transaction riêng nên một buổi lỗi không làm dừng các buổi còn lại.
        for (Integer sessionId : overdueSessionIds) {
            if (closeOneSession(sessionId, today)) {
                closedCount++;
            }
        }
        return closedCount;
    }

    /** DAO lấy danh sách ID trước; nếu truy vấn lỗi thì dừng cả lượt để scheduler retry. */
    private List<Integer> findOverdueSessionIds(LocalDate today) throws SQLException {
        try (Connection connection = openConnection()) {
            return sessionDAO.findOverdueSessionIds(connection, today);
        }
    }

    /**
     * Chốt một buổi trong transaction riêng.
     *
     * <p>Thứ tự: đọc lại trạng thái → tính kết quả → cập nhật Sessions → ghi AuditLog → commit.</p>
     */
    private boolean closeOneSession(int sessionId, LocalDate today) {
        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);

            try {
                // B1: khóa và đọc lại. Null nghĩa là buổi đã được luồng khác xử lý hoặc chưa đủ điều kiện.
                SessionAutoCloseData data = sessionDAO.findDataForClosing(
                        connection, sessionId, today);
                if (data == null) {
                    connection.rollback();
                    return false;
                }

                // B2: Service quyết định Status và MissedReason theo quy tắc JOB-04.
                Outcome outcome = evaluate(data);

                // B3: chỉ cập nhật nếu trạng thái vẫn là Scheduled tại thời điểm ghi.
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

                // B4: ghi log trong cùng transaction; log lỗi thì cả cập nhật buổi cũng rollback.
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

                // B5: chỉ sau khi Sessions và AuditLog cùng thành công mới commit.
                connection.commit();
                return true;
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException | RuntimeException exception) {
            // Lỗi của buổi này được ghi lại; vòng lặp bên ngoài vẫn xử lý các buổi tiếp theo.
            System.err.println("[JOB-04] SessionId=" + sessionId);
            exception.printStackTrace();
            return false;
        }
    }
}
