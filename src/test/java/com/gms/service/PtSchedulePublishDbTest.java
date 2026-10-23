package com.gms.service;

import com.gms.dao.PtScheduleDAO;
import com.gms.dto.ServiceResult;
import com.gms.util.DBContext;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;

/** Chạy main với target/classes đứng trước target/test-classes để dùng DB local; mọi dữ liệu thử đều rollback. */
@EnabledIfSystemProperty(named = "gms.schedule.dbtest", matches = "true")
class PtSchedulePublishDbTest {
    private final PtScheduleService service = new PtScheduleService();
    private final PtScheduleDAO dao = new PtScheduleDAO();
    private final int minutes = new SystemConfigService().getInt(SystemConfigService.SESSION_DURATION_MINUTES);
    private static final String DATE = "2150-01-03";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 23, 7, 0);

    PtSchedulePublishDbTest() throws SQLException { }

    /** Runner riêng để không thay application.properties giả của các unit test khác. */
    public static void main(String[] args) throws Exception {
        PtSchedulePublishDbTest tests = new PtSchedulePublishDbTest();
        tests.insertsWholeBatchRejectsReplayAndRollsBackFixture();
        tests.invalidLastDraftWritesNothingAndChangedConfigKeepsDrafts();
        tests.reopensWithdrawnWithoutChangingItsIdOrTimes();
        tests.secondTransactionCannotPublishPastTheFirstPtLock();
        System.out.println("4 SQL transaction tests passed; all fixture rows rolled back.");
    }

    @Test
    void insertsWholeBatchRejectsReplayAndRollsBackFixture() throws Exception {
        try (Connection connection = DBContext.getConnection()) {
            connection.setAutoCommit(false);
            try {
                int ptId = dao.lockPtForPublish(connection, 15);
                int before = count(connection, ptId);
                ServiceResult<Integer> result = service.publishInTransaction(connection, 15,
                        new String[]{DATE, DATE}, new String[]{"08:00", "10:00"}, minutes, NOW);
                assertTrue(result.isSuccess());
                assertEquals(2, result.getData());
                assertEquals(before + 2, count(connection, ptId));
                assertThrows(IllegalArgumentException.class, () -> service.publishInTransaction(connection, 15,
                        new String[]{DATE}, new String[]{"08:00"}, minutes, NOW));
                assertEquals(before + 2, count(connection, ptId));
            } finally { connection.rollback(); }
        }
    }

    @Test
    void invalidLastDraftWritesNothingAndChangedConfigKeepsDrafts() throws Exception {
        try (Connection connection = DBContext.getConnection()) {
            connection.setAutoCommit(false);
            try {
                int ptId = dao.lockPtForPublish(connection, 15);
                int before = count(connection, ptId);
                assertThrows(IllegalArgumentException.class, () -> service.publishInTransaction(connection, 15,
                        new String[]{DATE, DATE}, new String[]{"08:00", "08:15"}, minutes, NOW));
                assertEquals(before, count(connection, ptId));
                assertFalse(service.publishInTransaction(connection, 15,
                        new String[]{DATE}, new String[]{"08:00"}, minutes + 15, NOW).isSuccess());
                assertEquals(before, count(connection, ptId));
                assertFalse(service.publishInTransaction(connection, 1,
                        new String[]{DATE}, new String[]{"08:00"}, minutes, NOW).isSuccess());
            } finally { connection.rollback(); }
        }
    }

    @Test
    void reopensWithdrawnWithoutChangingItsIdOrTimes() throws Exception {
        try (Connection connection = DBContext.getConnection()) {
            connection.setAutoCommit(false);
            try {
                int ptId = dao.lockPtForPublish(connection, 15);
                int before = count(connection, ptId);
                String sql = "INSERT INTO TimeSlots (PtEmployeeId,SlotDate,StartTime,EndTime,Status) "
                        + "VALUES (?,?,'08:00',?,'Withdrawn')";
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setInt(1, ptId);
                    statement.setString(2, DATE);
                    statement.setTime(3, java.sql.Time.valueOf(java.time.LocalTime.of(8, 0).plusMinutes(minutes)));
                    statement.executeUpdate();
                }
                int oldId = slotId(connection, ptId);
                assertTrue(service.publishInTransaction(connection, 15,
                        new String[]{DATE}, new String[]{"08:00"}, minutes, NOW).isSuccess());
                assertEquals(before + 1, count(connection, ptId));
                assertEquals(oldId, slotId(connection, ptId));
                try (PreparedStatement statement = connection.prepareStatement("SELECT Status FROM TimeSlots WHERE TimeSlotId=?")) {
                    statement.setInt(1, oldId);
                    try (ResultSet rs = statement.executeQuery()) { assertTrue(rs.next()); assertEquals("Free", rs.getString(1)); }
                }
            } finally { connection.rollback(); }
        }
    }

    @Test
    void secondTransactionCannotPublishPastTheFirstPtLock() throws Exception {
        try (Connection first = DBContext.getConnection(); Connection second = DBContext.getConnection()) {
            first.setAutoCommit(false);
            second.setAutoCommit(false);
            try {
                assertNotNull(dao.lockPtForPublish(first, 15));
                try (Statement statement = second.createStatement()) { statement.execute("SET LOCK_TIMEOUT 1000"); }
                assertThrows(SQLException.class, () -> dao.lockPtForPublish(second, 15));
            } finally { second.rollback(); first.rollback(); }
        }
    }

    private static int count(Connection connection, int ptId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM TimeSlots WHERE PtEmployeeId=? AND SlotDate=?")) {
            statement.setInt(1, ptId);
            statement.setString(2, DATE);
            try (ResultSet rs = statement.executeQuery()) { rs.next(); return rs.getInt(1); }
        }
    }

    private static int slotId(Connection connection, int ptId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT TimeSlotId FROM TimeSlots WHERE PtEmployeeId=? AND SlotDate=? AND StartTime='08:00'")) {
            statement.setInt(1, ptId);
            statement.setString(2, DATE);
            try (ResultSet rs = statement.executeQuery()) { assertTrue(rs.next()); return rs.getInt(1); }
        }
    }
}
