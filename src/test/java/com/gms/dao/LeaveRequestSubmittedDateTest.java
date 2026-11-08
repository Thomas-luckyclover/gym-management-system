package com.gms.dao;

import com.gms.dto.LeaveRequestFilter;
import com.gms.util.DBContext;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Ngày lọc luôn là ngày gửi đơn, gồm đủ ngày cuối, trên cả bảng và số đếm. */
class LeaveRequestSubmittedDateTest {
    @ParameterizedTest
    @ValueSource(strings = {"requestCount", "requestPage", "statusCounts", "employeeCount", "employeePage", "employeeRequests"})
    void allManagerQueriesUseSubmissionDates(String query) throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(mock(ResultSet.class));
        LocalDate date = LocalDate.of(2026, 10, 22);
        var filter = new LeaveRequestFilter(null, "", null, null, date, date);
        var dao = new LeaveRequestDAO();

        try (var db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(connection);
            switch (query) {
                case "requestCount" -> dao.countRequests(filter);
                case "requestPage" -> dao.findRequestPage(filter, 0, 10, null);
                case "statusCounts" -> dao.getStatusCounts(filter);
                case "employeeCount" -> dao.countEmployees(filter);
                case "employeePage" -> dao.findEmployeePage(filter, 0, 10);
                case "employeeRequests" -> dao.findEmployeeRequests(filter, java.util.List.of(1), null);
                default -> fail("Unknown query");
            }
        }

        var sql = ArgumentCaptor.forClass(String.class);
        verify(connection).prepareStatement(sql.capture());
        assertTrue(sql.getValue().contains("lr.SubmittedAt >= ?"));
        assertTrue(sql.getValue().contains("lr.SubmittedAt < DATEADD(DAY, 1, CAST(? AS date))"));
        assertFalse(sql.getValue().contains("lr.ToDate >= ?"));
        assertFalse(sql.getValue().contains("lr.FromDate <= ?"));
        int firstDateIndex = "employeePage".equals(query) ? 2 : 1;
        verify(statement).setDate(firstDateIndex, java.sql.Date.valueOf(date));
        verify(statement).setDate(firstDateIndex + 1, java.sql.Date.valueOf(date));
    }
}
