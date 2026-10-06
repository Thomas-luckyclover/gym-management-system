package com.gms.service;

import com.gms.dao.AutoCloseSessionDAO;
import com.gms.dao.AutoCloseSessionDAO.Candidate;
import com.gms.entity.Session;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Kiểm tra thứ tự chốt và vòng đời transaction của JOB-04. */
class AutoCloseSessionServiceTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 6);

    private Candidate candidate(boolean couple, boolean hf, boolean hm, boolean pf, boolean pm, boolean ci) {
        return new Candidate(1, 1, TODAY.minusDays(1), couple, hf, hm, pf, pm, ci);
    }

    private void assertOutcome(Candidate c, Session.Status status, Session.MissedReason reason) {
        assertEquals(new AutoCloseSessionService.Outcome(status, reason), AutoCloseSessionService.evaluate(c));
    }

    @Test void singleFrozen() {
        assertOutcome(candidate(false,true,true,false,false,true), Session.Status.MISSED, Session.MissedReason.MEMBERSHIP_FROZEN);
    }

    @Test void singleExpired() {
        assertOutcome(candidate(false,false,false,false,false,true), Session.Status.MISSED, Session.MissedReason.NO_MEMBERSHIP);
    }

    @Test void singleCheckedIn() {
        assertOutcome(candidate(false,false,true,false,false,true), Session.Status.CHECKED_IN, null);
    }

    @Test void singleNoCheckIn() {
        assertOutcome(candidate(false,false,true,false,false,false), Session.Status.MISSED, Session.MissedReason.NO_CHECK_IN);
    }

    @Test void coupleFrozenAndValidWithCheckIn() {
        assertOutcome(candidate(true,true,true,false,true,true), Session.Status.CHECKED_IN, null);
    }

    @Test void coupleFrozenAndValidWithoutCheckIn() {
        assertOutcome(candidate(true,true,true,false,true,false), Session.Status.MISSED, Session.MissedReason.NO_CHECK_IN);
    }

    @Test void coupleBothFrozen() {
        assertOutcome(candidate(true,true,true,true,true,true), Session.Status.MISSED, Session.MissedReason.MEMBERSHIP_FROZEN);
    }

    @Test void coupleBothExpired() {
        assertOutcome(candidate(true,false,false,false,false,true), Session.Status.MISSED, Session.MissedReason.NO_MEMBERSHIP);
    }

    @Test void coupleFrozenAndExpired() {
        assertOutcome(candidate(true,true,true,false,false,true), Session.Status.MISSED, Session.MissedReason.MEMBERSHIP_FROZEN);
    }

    private static class FakeDAO extends AutoCloseSessionDAO {
        List<Candidate> rows = new ArrayList<>();
        Set<Integer> closed = new HashSet<>();
        int releases;
        int failId = -1;
        boolean lostRace;

        @Override public List<Integer> findOverdueIds(Connection conn, LocalDate today) {
            return rows.stream().filter(c -> c.slotDate().isBefore(today) && !closed.contains(c.sessionId()))
                    .map(Candidate::sessionId).toList();
        }

        @Override public Candidate findForClosing(Connection conn, int id, LocalDate today) {
            return rows.stream().filter(c -> c.sessionId()==id && !closed.contains(id)).findFirst().orElse(null);
        }

        @Override public boolean close(Connection conn, Candidate c, Session.Status status, Session.MissedReason reason)
                throws SQLException {
            if (c.sessionId()==failId) {
                throw new SQLException("test row failure");
            }
            if (lostRace) {
                return false;
            }
            closed.add(c.sessionId());
            return true;
        }

        @Override public void releaseSlot(Connection conn, int slot) {
            releases++;
        }
    }

    private AutoCloseSessionService service(FakeDAO dao, List<String> transactions) {
        AuditLogService audit = new AuditLogService() {
            @Override void logInTransaction(Connection c, Integer a, String action, String entity,
                    Integer id, String field, String oldValue, String newValue, String reason) {
                transactions.add("audit:"+id);
            }
        };
        return new AutoCloseSessionService(dao,audit) {
            @Override protected Connection openConnection() {
                return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{Connection.class},
                        (p,m,a) -> {
                            transactions.add(m.getName());
                            if (m.getReturnType()==boolean.class) {
                                return false;
                            }
                            return null;
                        });
            }
        };
    }

    @Test void todayAndFutureNotClosed() throws SQLException {
        FakeDAO dao = new FakeDAO();
        dao.rows.add(new Candidate(1,1,TODAY,false,false,true,false,false,true));
        dao.rows.add(new Candidate(2,2,TODAY.plusDays(1),false,false,true,false,false,true));
        assertEquals(0,service(dao,new ArrayList<>()).runJob(TODAY));
        assertTrue(dao.closed.isEmpty());
    }

    @Test void idempotentAndMissedSlotReleased() throws SQLException {
        FakeDAO dao = new FakeDAO();
        dao.rows.add(candidate(false,false,true,false,false,false));
        List<String> tx = new ArrayList<>();
        AutoCloseSessionService service = service(dao,tx);
        assertEquals(1,service.runJob(TODAY));
        assertEquals(0,service.runJob(TODAY));
        assertEquals(1,dao.releases);
        assertEquals(1,tx.stream().filter("commit"::equals).count());
        assertTrue(tx.contains("audit:1"));
    }

    @Test void failedRowDoesNotBlockNextRow() throws SQLException {
        FakeDAO dao = new FakeDAO();
        dao.rows.add(candidate(false,false,true,false,false,true));
        dao.rows.add(new Candidate(2,2,TODAY.minusDays(1),false,false,true,false,false,true));
        dao.failId=1;
        List<String> tx = new ArrayList<>();
        assertEquals(1,service(dao,tx).runJob(TODAY));
        assertEquals(Set.of(2),dao.closed);
        assertTrue(tx.contains("rollback"));
        assertEquals(0,dao.releases);
    }

    @Test void conditionalUpdateLosingRaceDoesNotAuditOrRelease() throws SQLException {
        FakeDAO dao = new FakeDAO();
        dao.rows.add(candidate(false,false,true,false,false,false));
        dao.lostRace=true;
        List<String> tx = new ArrayList<>();
        assertEquals(0,service(dao,tx).runJob(TODAY));
        assertEquals(0,dao.releases);
        assertFalse(tx.contains("commit"));
        assertTrue(tx.contains("rollback"));
    }
}
