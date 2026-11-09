package com.gms.service;

import com.gms.dao.ExpireDayPassDAO;
import com.gms.entity.AuditLog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test cho JOB-03 — ExpireDayPassService.
 */
class ExpireDayPassServiceTest {

    private FakeExpireDayPassDAO fakeDAO;
    private FakeAuditLogService fakeAuditLogService;
    private ExpireDayPassService service;

    @BeforeEach
    void setUp() {
        fakeDAO = new FakeExpireDayPassDAO();
        fakeAuditLogService = new FakeAuditLogService();
        service = new ExpireDayPassService(fakeDAO, fakeAuditLogService) {
            @Override
            protected Connection openConnection() {
                return createDummyConnection();
            }
        };
    }

    @Test
    @DisplayName("JOB-03: Vé ngày quá hạn chưa dùng -> đổi sang Expired và ghi AuditLog")
    void runJob_expiresUnusedDayPass() throws SQLException {
        LocalDate today = LocalDate.of(2026, 11, 8);
        fakeDAO.addExpiredUnusedDayPass(100, "Active");

        int count = service.runJob(today);

        assertEquals(1, count);
        assertEquals("Expired", fakeDAO.getPassStatus(100));
        assertEquals(1, fakeAuditLogService.getLogs().size());

        AuditLog log = fakeAuditLogService.getLogs().get(0);
        assertNull(log.getAccountId());
        assertEquals("JOB-03", log.getAction());
        assertEquals("Subscriptions", log.getEntityName());
        assertEquals(100, log.getEntityId());
        assertEquals("Status", log.getFieldChanged());
        assertEquals("Active", log.getOldValue());
        assertEquals("Expired", log.getNewValue());
    }

    @Test
    @DisplayName("JOB-03: Một vé lỗi giữa chừng chỉ rollback vé đó, các vé sau vẫn xử lý bình thường")
    void runJob_continuesWhenOnePassFails() throws SQLException {
        LocalDate today = LocalDate.of(2026, 11, 8);
        fakeDAO.addExpiredUnusedDayPass(300, "Active");
        fakeDAO.failOnSubscription(300);

        fakeDAO.addExpiredUnusedDayPass(301, "Active");

        int count = service.runJob(today);

        assertEquals(1, count);
        assertEquals("Active", fakeDAO.getPassStatus(300));
        assertEquals("Expired", fakeDAO.getPassStatus(301));
    }

    private static Connection createDummyConnection() {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    if ("isClosed".equals(method.getName())) {
                        return false;
                    }
                    return null;
                });
    }

    private static class FakeExpireDayPassDAO extends ExpireDayPassDAO {
        private final List<Integer> ids = new ArrayList<>();
        private final Map<Integer, String> passStatuses = new HashMap<>();
        private int failSubId = -1;

        void addExpiredUnusedDayPass(int subscriptionId, String status) {
            ids.add(subscriptionId);
            passStatuses.put(subscriptionId, status);
        }

        void failOnSubscription(int subscriptionId) {
            this.failSubId = subscriptionId;
        }

        String getPassStatus(int subscriptionId) {
            return passStatuses.get(subscriptionId);
        }

        @Override
        public List<Integer> findExpiredUnusedDayPassIds(LocalDate today) {
            return ids;
        }

        @Override
        public boolean markDayPassExpired(Connection conn, int subscriptionId) throws SQLException {
            if (subscriptionId == failSubId) {
                throw new SQLException("Simulated DB error on subId=" + subscriptionId);
            }
            if ("Active".equals(passStatuses.get(subscriptionId))) {
                passStatuses.put(subscriptionId, "Expired");
                return true;
            }
            return false;
        }
    }

    private static class FakeAuditLogService extends AuditLogService {
        private final List<AuditLog> logs = new ArrayList<>();

        List<AuditLog> getLogs() {
            return logs;
        }

        @Override
        public void logInTransaction(Connection conn, Integer accountId, String action,
                String entityName, Integer entityId, String fieldChanged,
                String oldValue, String newValue, String reason) {
            logs.add(new AuditLog(accountId, action, entityName, entityId,
                    fieldChanged, oldValue, newValue, reason));
        }
    }
}
