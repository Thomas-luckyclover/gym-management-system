package com.gms.service;

import com.gms.dao.AutoCloseSessionDAO;
import com.gms.dao.AutoCloseSessionDAO.Candidate;
import com.gms.entity.Session;
import com.gms.util.DBContext;
import com.gms.util.DateTimeUtil;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

/** Chốt buổi theo Frozen, NoMembership, check-in rồi NoCheckIn (JOB-04). */
public class AutoCloseSessionService {
    private final AutoCloseSessionDAO dao;
    private final AuditLogService audit;

    public AutoCloseSessionService() {
        this(new AutoCloseSessionDAO(), new AuditLogService());
    }

    public AutoCloseSessionService(AutoCloseSessionDAO dao, AuditLogService audit) {
        this.dao = dao;
        this.audit = audit;
    }

    protected Connection openConnection() throws SQLException {
        return DBContext.getConnection();
    }

    public record Outcome(Session.Status status, Session.MissedReason reason) {
    }

    /** Xác định kết quả theo thứ tự JOB-04 đã chốt. */
    public static Outcome evaluate(Candidate c) {
        boolean holderUnavailable = c.holderFrozen() || !c.holderMembership();
        boolean partnerUnavailable = c.partnerFrozen() || !c.partnerMembership();
        boolean unavailable = holderUnavailable && (!c.couple() || partnerUnavailable);
        boolean frozen = c.holderFrozen() || (c.couple() && c.partnerFrozen());
        if (unavailable && frozen) {
            return new Outcome(Session.Status.MISSED, Session.MissedReason.MEMBERSHIP_FROZEN);
        }
        if (unavailable) {
            return new Outcome(Session.Status.MISSED, Session.MissedReason.NO_MEMBERSHIP);
        }
        if (c.checkedIn()) {
            return new Outcome(Session.Status.CHECKED_IN, null);
        }
        return new Outcome(Session.Status.MISSED, Session.MissedReason.NO_CHECK_IN);
    }

    /** Chốt từng buổi trong transaction riêng. */
    public int runJob() throws SQLException {
        return runJob(DateTimeUtil.today());
    }

    /** Chốt các buổi trước ngày VN được chỉ định. */
    public int runJob(LocalDate today) throws SQLException {
        List<Integer> ids;
        try (Connection conn = openConnection()) {
            ids = dao.findOverdueIds(conn, today);
        }
        int closed = 0;
        for (int id : ids) {
            try (Connection conn = openConnection()) {
                conn.setAutoCommit(false);
                try {
                    // B1: khóa và kiểm tra lại buổi Scheduled (JOB-04)
                    Candidate candidate = dao.findForClosing(conn, id, today);
                    if (candidate == null) {
                        conn.rollback();
                        continue;
                    }
                    Outcome result = evaluate(candidate);
                    // B2: ghi kết quả và nhả slot Missed (DBR-36)
                    if (dao.close(conn, candidate, result.status(), result.reason())) {
                        if (result.status() == Session.Status.MISSED) {
                            dao.releaseSlot(conn, candidate.timeSlotId());
                        }
                        audit.logInTransaction(conn, null, "JOB-04", "Sessions", id, "Status",
                                Session.Status.SCHEDULED.getDbValue(), result.status().getDbValue(),
                                result.reason() == null ? null : result.reason().getDbValue());
                        conn.commit();
                        closed++;
                    } else {
                        conn.rollback();
                    }
                } catch (SQLException | RuntimeException e) {
                    conn.rollback();
                    throw e;
                }
            } catch (SQLException | RuntimeException e) {
                System.err.println("[JOB-04] SessionId=" + id);
                e.printStackTrace();
            }
        }
        return closed;
    }
}
