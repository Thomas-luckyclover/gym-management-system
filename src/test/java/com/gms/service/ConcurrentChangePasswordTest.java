package com.gms.service;

import com.gms.constant.Messages;
import com.gms.constant.Role;
import com.gms.dao.AccountDAO;
import com.gms.dto.ServiceResult;
import com.gms.entity.Account;
import com.gms.listener.SessionRegistryListener;
import com.gms.util.PasswordUtil;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TC-17C: Concurrent Password Change for Admin in GMS.
 *
 * Verifies optimistic concurrency control (OCC) when two concurrent requests
 * validate the same original password hash before attempting to update.
 * Exactly one update must succeed and the other must be rejected with MSG22_CONCURRENT_EDIT.
 */
class ConcurrentChangePasswordTest {

    private static final int ADMIN_ACCOUNT_ID = 999;
    private static final String INITIAL_PASSWORD = "Admin@2026Initial";
    private static final String NEW_PASSWORD_A = "Admin@2026NewA";
    private static final String NEW_PASSWORD_B = "Admin@2026NewB";

    private static List<Driver> savedDrivers;
    private static MockDriver mockDriver;

    private InstrumentedAccountDAO instrumentedAccountDAO;
    private TestAuditLogService testAuditLogService;
    private AccountSettingsService accountSettingsService;

    // =========================================================================
    // Mock JDBC Driver setup (Zero network / zero real DB access)
    // =========================================================================

    @BeforeAll
    static void setUpMockJdbcDriver() throws Exception {
        // Pre-load SQL Server driver class so it doesn't trigger static init later
        try {
            Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        } catch (Throwable ignored) {
        }

        // Set in-memory db properties in AppConfig so AppConfig.get("db.url") succeeds
        // without touching or modifying any files on disk
        try {
            Field propsField = com.gms.util.AppConfig.class.getDeclaredField("PROPERTIES");
            propsField.setAccessible(true);
            Properties props = (Properties) propsField.get(null);
            props.setProperty("db.url", "jdbc:mock:gms_test");
            props.setProperty("db.username", "test_sa");
            props.setProperty("db.password", "test_sa");
        } catch (Throwable t) {
            t.printStackTrace();
        }

        // Backup existing drivers and deregister them
        savedDrivers = new ArrayList<>();
        Enumeration<Driver> drivers = DriverManager.getDrivers();
        while (drivers.hasMoreElements()) {
            Driver d = drivers.nextElement();
            savedDrivers.add(d);
            DriverManager.deregisterDriver(d);
        }

        mockDriver = new MockDriver();
        DriverManager.registerDriver(mockDriver);
    }

    @AfterAll
    static void restoreJdbcDrivers() throws Exception {
        try {
            Field propsField = com.gms.util.AppConfig.class.getDeclaredField("PROPERTIES");
            propsField.setAccessible(true);
            Properties props = (Properties) propsField.get(null);
            props.remove("db.url");
            props.remove("db.username");
            props.remove("db.password");
        } catch (Throwable ignored) {
        }

        if (mockDriver != null) {
            DriverManager.deregisterDriver(mockDriver);
        }
        if (savedDrivers != null) {
            for (Driver d : savedDrivers) {
                try {
                    DriverManager.registerDriver(d);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        instrumentedAccountDAO = new InstrumentedAccountDAO();
        testAuditLogService = new TestAuditLogService();
        accountSettingsService = new AccountSettingsService();

        // Inject instrumented DAO and recording AuditLogService
        setField(accountSettingsService, "accountDAO", instrumentedAccountDAO);
        setField(accountSettingsService, "auditLogService", testAuditLogService);

        // Seed dedicated Admin test account
        String initialHash = PasswordUtil.hashPassword(INITIAL_PASSWORD);
        Account adminAccount = new Account(
                ADMIN_ACCOUNT_ID,
                100, // personId
                initialHash,
                Account.Status.ACTIVE,
                0, // failedLoginCount
                null, // lockedUntil
                null, // lastLoginAt
                LocalDateTime.now()
        );
        instrumentedAccountDAO.setAccount(adminAccount);

        // Clear session registry
        clearSessionRegistry();
    }

    // =========================================================================
    // TC-17C Tests
    // =========================================================================

    @Test
    @DisplayName("TC-17C: Deterministic concurrent password change - Both validate original hash, exactly one UPDATE succeeds")
    void testTC17C_DeterministicConcurrentPasswordChange_OneSucceeds_OneRejected() throws Exception {
        // Prepare 3 test sessions for this Admin account
        MockSessionHolder sessionHolderA = new MockSessionHolder("SESSION_ADMIN_A");
        MockSessionHolder sessionHolderB = new MockSessionHolder("SESSION_ADMIN_B");
        MockSessionHolder sessionHolderC = new MockSessionHolder("SESSION_ADMIN_C");

        registerSessionForAccount(ADMIN_ACCOUNT_ID, sessionHolderA.proxy);
        registerSessionForAccount(ADMIN_ACCOUNT_ID, sessionHolderB.proxy);
        registerSessionForAccount(ADMIN_ACCOUNT_ID, sessionHolderC.proxy);

        // Coordination primitives:
        // 1. readBarrier: ensures BOTH Thread A and Thread B have read the initial account
        //    state (with H0) before either thread proceeds to password validation / hashing / update.
        CyclicBarrier readBarrier = new CyclicBarrier(2);
        instrumentedAccountDAO.setReadBarrier(readBarrier);

        // 2. Latches to guarantee deterministic ordering at the update phase:
        //    Thread A updates first -> releases Thread B.
        CountDownLatch threadAUpdatedLatch = new CountDownLatch(1);
        instrumentedAccountDAO.setThreadAUpdatedLatch(threadAUpdatedLatch);
        instrumentedAccountDAO.setThreadBWaitLatch(threadAUpdatedLatch);

        // Thread A: Request A
        CompletableFuture<ServiceResult<Void>> futureA = CompletableFuture.supplyAsync(() ->
                accountSettingsService.changePassword(
                        ADMIN_ACCOUNT_ID,
                        INITIAL_PASSWORD,
                        NEW_PASSWORD_A,
                        NEW_PASSWORD_A,
                        "SESSION_ADMIN_A",
                        Role.ADMIN
                )
        );

        // Thread B: Request B
        CompletableFuture<ServiceResult<Void>> futureB = CompletableFuture.supplyAsync(() ->
                accountSettingsService.changePassword(
                        ADMIN_ACCOUNT_ID,
                        INITIAL_PASSWORD,
                        NEW_PASSWORD_B,
                        NEW_PASSWORD_B,
                        "SESSION_ADMIN_B",
                        Role.ADMIN
                )
        );

        // Await both results
        ServiceResult<Void> resultA = futureA.get(10, TimeUnit.SECONDS);
        ServiceResult<Void> resultB = futureB.get(10, TimeUnit.SECONDS);

        // ---------------------------------------------------------------------
        // Requirement 3: Coordinate so both validate same original hash
        // ---------------------------------------------------------------------
        assertEquals(2, instrumentedAccountDAO.getFindByIdCalls().get(),
                "Both threads must have fetched the account record");
        assertEquals(2, instrumentedAccountDAO.getUpdatePasswordCalls().get(),
                "Both threads must have reached updatePasswordHash");

        // ---------------------------------------------------------------------
        // Requirement 4: Exactly one update succeeds and the other is rejected
        // ---------------------------------------------------------------------
        assertTrue(resultA.isSuccess(), "Request A should succeed");
        assertNull(resultA.getMessage(), "Request A should have no error message");

        assertFalse(resultB.isSuccess(), "Request B must be rejected due to optimistic concurrency conflict");
        assertEquals(Messages.MSG22_CONCURRENT_EDIT, resultB.getMessage(),
                "Request B must return MSG22_CONCURRENT_EDIT");

        // ---------------------------------------------------------------------
        // Requirement 5: Final BCrypt hash matches successful request's new password
        // ---------------------------------------------------------------------
        Account storedAccount = instrumentedAccountDAO.getStoredAccount(ADMIN_ACCOUNT_ID);
        assertNotNull(storedAccount, "Admin account must exist");
        String finalHash = storedAccount.getPasswordHash();

        assertTrue(PasswordUtil.checkPassword(NEW_PASSWORD_A, finalHash),
                "Stored hash must match successful Request A's new password");
        assertFalse(PasswordUtil.checkPassword(NEW_PASSWORD_B, finalHash),
                "Stored hash must NOT match rejected Request B's new password");
        assertFalse(PasswordUtil.checkPassword(INITIAL_PASSWORD, finalHash),
                "Stored hash must NOT match the initial old password");

        // ---------------------------------------------------------------------
        // Requirement 6: Only the successful request writes a successful Audit Log
        // ---------------------------------------------------------------------
        List<RecordedAuditLog> logs = testAuditLogService.getLogs();
        assertEquals(1, logs.size(), "Exactly one audit log entry must be recorded");

        RecordedAuditLog auditLog = logs.get(0);
        assertEquals(ADMIN_ACCOUNT_ID, auditLog.accountId, "Audit log accountId must match Admin");
        assertEquals(Role.ADMIN, auditLog.actingRole, "Audit log actingRole must be ADMIN");
        assertEquals("Account", auditLog.entityName, "Audit log entityName must be Account");
        assertEquals(ADMIN_ACCOUNT_ID, auditLog.entityId, "Audit log entityId must be Admin ID");
        assertEquals("PasswordHash", auditLog.fieldChanged, "Audit log fieldChanged must be PasswordHash");
        assertNull(auditLog.oldValue, "Sensitive field oldValue must be null (masked)");
        assertNull(auditLog.newValue, "Sensitive field newValue must be null (masked)");

        // ---------------------------------------------------------------------
        // Requirement 7: No incorrect session invalidation for rejected request
        // ---------------------------------------------------------------------
        // Request A passed "SESSION_ADMIN_A" -> session A is preserved
        assertFalse(sessionHolderA.isInvalidated(),
                "Current session of successful request (SESSION_ADMIN_A) must remain valid");

        // Session C was an old session belonging to the Admin -> invalidated by Request A
        assertTrue(sessionHolderC.isInvalidated(),
                "Other existing session (SESSION_ADMIN_C) must be invalidated by successful Request A");

        // Request B was rejected -> Verify that Request B did not execute session invalidation
        // to invalidate sessionHolderA! (sessionHolderA is still alive as checked above).
    }

    @Test
    @DisplayName("TC-17C: Racing threads without artificial delay - Atomic CAS ensures exactly one winner")
    void testTC17C_RacingThreads_AtomicOccEnsuresSingleSuccess() throws Exception {
        // Read barrier to align both threads at the starting line after reading H0
        CyclicBarrier startBarrier = new CyclicBarrier(2);
        instrumentedAccountDAO.setReadBarrier(startBarrier);
        instrumentedAccountDAO.setThreadAUpdatedLatch(null);
        instrumentedAccountDAO.setThreadBWaitLatch(null);

        CompletableFuture<ServiceResult<Void>> future1 = CompletableFuture.supplyAsync(() ->
                accountSettingsService.changePassword(
                        ADMIN_ACCOUNT_ID,
                        INITIAL_PASSWORD,
                        NEW_PASSWORD_A,
                        NEW_PASSWORD_A,
                        "SESSION_1",
                        Role.ADMIN
                )
        );

        CompletableFuture<ServiceResult<Void>> future2 = CompletableFuture.supplyAsync(() ->
                accountSettingsService.changePassword(
                        ADMIN_ACCOUNT_ID,
                        INITIAL_PASSWORD,
                        NEW_PASSWORD_B,
                        NEW_PASSWORD_B,
                        "SESSION_2",
                        Role.ADMIN
                )
        );

        ServiceResult<Void> r1 = future1.get(10, TimeUnit.SECONDS);
        ServiceResult<Void> r2 = future2.get(10, TimeUnit.SECONDS);

        int successCount = (r1.isSuccess() ? 1 : 0) + (r2.isSuccess() ? 1 : 0);
        int failCount = (!r1.isSuccess() ? 1 : 0) + (!r2.isSuccess() ? 1 : 0);

        assertEquals(1, successCount, "Exactly one thread must succeed in a race condition");
        assertEquals(1, failCount, "Exactly one thread must fail in a race condition");

        ServiceResult<Void> failedResult = r1.isSuccess() ? r2 : r1;
        assertEquals(Messages.MSG22_CONCURRENT_EDIT, failedResult.getMessage(),
                "Rejected thread must receive MSG22_CONCURRENT_EDIT");

        // Verify final stored password hash matches winning thread
        Account finalAccount = instrumentedAccountDAO.getStoredAccount(ADMIN_ACCOUNT_ID);
        String finalHash = finalAccount.getPasswordHash();

        if (r1.isSuccess()) {
            assertTrue(PasswordUtil.checkPassword(NEW_PASSWORD_A, finalHash));
            assertFalse(PasswordUtil.checkPassword(NEW_PASSWORD_B, finalHash));
        } else {
            assertTrue(PasswordUtil.checkPassword(NEW_PASSWORD_B, finalHash));
            assertFalse(PasswordUtil.checkPassword(NEW_PASSWORD_A, finalHash));
        }

        // Exactly 1 audit log created
        assertEquals(1, testAuditLogService.getLogs().size());
    }

    @Test
    @DisplayName("TC-17C: Rejected request path - Zero audit logs and zero session invalidations")
    void testTC17C_RejectedRequest_ZeroAuditLogs_ZeroSessionInvalidations() throws Exception {
        // Simulate a scenario where account password hash was updated in the DB by another user/process
        // right after this request started
        MockSessionHolder callerSession = new MockSessionHolder("SESSION_CALLER");
        MockSessionHolder otherSession = new MockSessionHolder("SESSION_OTHER");

        registerSessionForAccount(ADMIN_ACCOUNT_ID, callerSession.proxy);
        registerSessionForAccount(ADMIN_ACCOUNT_ID, otherSession.proxy);

        // Pre-alter the stored hash to simulate external concurrent modification
        Account stored = instrumentedAccountDAO.getStoredAccount(ADMIN_ACCOUNT_ID);
        stored.setPasswordHash(PasswordUtil.hashPassword("ConcurrentModifiedPassword"));

        // Caller attempts to change password with old hash (H0)
        ServiceResult<Void> result = accountSettingsService.changePassword(
                ADMIN_ACCOUNT_ID,
                INITIAL_PASSWORD, // current password matches original H0, but DB hash is already changed
                NEW_PASSWORD_A,
                NEW_PASSWORD_A,
                "SESSION_CALLER",
                Role.ADMIN
        );

        // The request must fail (either at validation or update phase)
        assertFalse(result.isSuccess(), "Request must fail");

        // Verify zero audit logs were created
        assertEquals(0, testAuditLogService.getLogs().size(), "No audit log should be written for rejected request");

        // Verify no session was invalidated
        assertFalse(callerSession.isInvalidated(), "Caller session must not be invalidated");
        assertFalse(otherSession.isInvalidated(), "Other session must not be invalidated by a rejected request");
    }

    // =========================================================================
    // Reflection & Test Utilities
    // =========================================================================

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            sun.misc.Unsafe unsafe = (sun.misc.Unsafe) f.get(null);
            Field field = target.getClass().getDeclaredField(fieldName);
            long offset = unsafe.objectFieldOffset(field);
            unsafe.putObject(target, offset, value);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<Integer, Set<HttpSession>> getSessionRegistryMap() throws Exception {
        Field field = SessionRegistryListener.class.getDeclaredField("SESSIONS_BY_ACCOUNT");
        field.setAccessible(true);
        return (Map<Integer, Set<HttpSession>>) field.get(null);
    }

    private static void registerSessionForAccount(int accountId, HttpSession session) throws Exception {
        Map<Integer, Set<HttpSession>> map = getSessionRegistryMap();
        Set<HttpSession> set = map.computeIfAbsent(accountId, k -> ConcurrentHashMap.newKeySet());
        set.add(session);
    }

    private static void clearSessionRegistry() throws Exception {
        Map<Integer, Set<HttpSession>> map = getSessionRegistryMap();
        map.clear();
    }

    // =========================================================================
    // Inner Test Helpers: Instrumented DAO, Test AuditLogService, Mock Driver
    // =========================================================================

    static class InstrumentedAccountDAO extends AccountDAO {
        private final ConcurrentHashMap<Integer, Account> store = new ConcurrentHashMap<>();
        private final AtomicInteger findByIdCalls = new AtomicInteger(0);
        private final AtomicInteger updatePasswordCalls = new AtomicInteger(0);

        private CyclicBarrier readBarrier;
        private CountDownLatch threadAUpdatedLatch;
        private CountDownLatch threadBWaitLatch;

        void setAccount(Account account) {
            store.put(account.getAccountId(), account);
        }

        void setReadBarrier(CyclicBarrier barrier) {
            this.readBarrier = barrier;
        }

        void setThreadAUpdatedLatch(CountDownLatch latch) {
            this.threadAUpdatedLatch = latch;
        }

        void setThreadBWaitLatch(CountDownLatch latch) {
            this.threadBWaitLatch = latch;
        }

        AtomicInteger getFindByIdCalls() {
            return findByIdCalls;
        }

        AtomicInteger getUpdatePasswordCalls() {
            return updatePasswordCalls;
        }

        @Override
        public Account findById(int accountId) throws SQLException {
            Account acc = store.get(accountId);
            if (acc == null) {
                return null;
            }
            findByIdCalls.incrementAndGet();

            // Return a detached snapshot of Account so caller has local state
            Account copy = new Account(
                    acc.getAccountId(),
                    acc.getPersonId(),
                    acc.getPasswordHash(),
                    acc.getStatus(),
                    acc.getFailedLoginCount(),
                    acc.getLockedUntil(),
                    acc.getLastLoginAt(),
                    acc.getCreatedAt()
            );

            // Synchronize at the barrier so both concurrent threads complete findById
            // and observe the exact same initial password hash H0 before proceeding.
            if (readBarrier != null) {
                try {
                    readBarrier.await(5, TimeUnit.SECONDS);
                } catch (Exception e) {
                    throw new RuntimeException("Read barrier await failed", e);
                }
            }
            return copy;
        }

        @Override
        public boolean updatePasswordHash(
                Connection conn,
                int accountId,
                String newPasswordHash,
                String oldPasswordHash) throws SQLException {

            updatePasswordCalls.incrementAndGet();

            // If threadBWaitLatch is set and this is Thread B (attempting to set NEW_PASSWORD_B),
            // wait until Thread A has finished its update before attempting:
            if (threadBWaitLatch != null && PasswordUtil.checkPassword(NEW_PASSWORD_B, newPasswordHash)) {
                try {
                    threadBWaitLatch.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            synchronized (this) {
                Account current = store.get(accountId);
                if (current == null) {
                    return false;
                }

                // Exact optimistic concurrency control SQL semantics:
                // UPDATE [Account] SET [PasswordHash] = ? WHERE [AccountId] = ? AND [PasswordHash] = ?
                if (current.getPasswordHash().equals(oldPasswordHash)) {
                    current.setPasswordHash(newPasswordHash);
                    if (threadAUpdatedLatch != null && PasswordUtil.checkPassword(NEW_PASSWORD_A, newPasswordHash)) {
                        threadAUpdatedLatch.countDown();
                    }
                    return true; // 1 row updated
                }

                // Mismatch: 0 rows updated
                return false;
            }
        }

        Account getStoredAccount(int accountId) {
            return store.get(accountId);
        }
    }

    static class RecordedAuditLog {
        final Integer accountId;
        final Role actingRole;
        final String action;
        final String entityName;
        final Integer entityId;
        final String fieldChanged;
        final String oldValue;
        final String newValue;
        final String reason;

        RecordedAuditLog(Integer accountId, Role actingRole, String action, String entityName,
                         Integer entityId, String fieldChanged, String oldValue, String newValue, String reason) {
            this.accountId = accountId;
            this.actingRole = actingRole;
            this.action = action;
            this.entityName = entityName;
            this.entityId = entityId;
            this.fieldChanged = fieldChanged;
            this.oldValue = oldValue;
            this.newValue = newValue;
            this.reason = reason;
        }
    }

    static class TestAuditLogService extends AuditLogService {
        private final List<RecordedAuditLog> logs = new CopyOnWriteArrayList<>();

        @Override
        void logInTransaction(
                Connection conn,
                Integer accountId,
                Role actingRole,
                String action,
                String entityName,
                Integer entityId,
                String fieldChanged,
                String oldValue,
                String newValue,
                String reason
        ) throws SQLException {
            logs.add(new RecordedAuditLog(
                    accountId, actingRole, action, entityName, entityId,
                    fieldChanged, oldValue, newValue, reason
            ));
        }

        List<RecordedAuditLog> getLogs() {
            return logs;
        }
    }

    static class MockSessionHolder {
        final String id;
        volatile boolean invalidated = false;
        final Map<String, Object> attributes = new ConcurrentHashMap<>();
        final HttpSession proxy;

        MockSessionHolder(String id) {
            this.id = id;
            this.proxy = (HttpSession) Proxy.newProxyInstance(
                    HttpSession.class.getClassLoader(),
                    new Class<?>[]{HttpSession.class},
                    new InvocationHandler() {
                        @Override
                        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                            String name = method.getName();
                            if ("getId".equals(name)) {
                                return id;
                            } else if ("invalidate".equals(name)) {
                                if (invalidated) {
                                    throw new IllegalStateException("Session already invalidated: " + id);
                                }
                                invalidated = true;
                                return null;
                            } else if ("getAttribute".equals(name)) {
                                return attributes.get((String) args[0]);
                            } else if ("setAttribute".equals(name)) {
                                attributes.put((String) args[0], args[1]);
                                return null;
                            } else if ("removeAttribute".equals(name)) {
                                attributes.remove((String) args[0]);
                                return null;
                            } else if ("hashCode".equals(name)) {
                                return id.hashCode();
                            } else if ("equals".equals(name)) {
                                return proxy == args[0];
                            } else if ("toString".equals(name)) {
                                return "MockSession[" + id + "]";
                            }
                            return null;
                        }
                    }
            );
        }

        boolean isInvalidated() {
            return invalidated;
        }
    }

    static class MockDriver implements Driver {
        @Override
        public Connection connect(String url, Properties info) {
            return (Connection) Proxy.newProxyInstance(
                    MockDriver.class.getClassLoader(),
                    new Class<?>[]{Connection.class},
                    (proxy, method, args) -> {
                        String name = method.getName();
                        if ("setAutoCommit".equals(name) || "commit".equals(name)
                                || "rollback".equals(name) || "close".equals(name)) {
                            return null;
                        } else if ("isClosed".equals(name)) {
                            return false;
                        } else if ("hashCode".equals(name)) {
                            return System.identityHashCode(proxy);
                        } else if ("equals".equals(name)) {
                            return proxy == args[0];
                        } else if ("toString".equals(name)) {
                            return "MockConnectionProxy";
                        }
                        return null;
                    }
            );
        }

        @Override
        public boolean acceptsURL(String url) {
            return true;
        }

        @Override
        public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) {
            return new DriverPropertyInfo[0];
        }

        @Override
        public int getMajorVersion() {
            return 1;
        }

        @Override
        public int getMinorVersion() {
            return 0;
        }

        @Override
        public boolean jdbcCompliant() {
            return true;
        }

        @Override
        public Logger getParentLogger() {
            return Logger.getGlobal();
        }
    }
}
