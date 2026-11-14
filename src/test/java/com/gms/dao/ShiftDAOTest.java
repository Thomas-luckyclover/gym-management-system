package com.gms.dao;

import com.gms.entity.Shift;
import com.gms.util.DBContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class ShiftDAOTest {

    @Test
    @DisplayName("findOtherOpenShift: SQL không còn ORDER BY và kiểm tra đúng điều kiện ShiftId <> ?")
    void testFindOtherOpenShift_WithoutOrderBy() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);

        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false);

        ShiftDAO shiftDAO = new ShiftDAO();
        int currentShiftId = 10;

        try (MockedStatic<DBContext> dbMock = mockStatic(DBContext.class)) {
            dbMock.when(DBContext::getConnection).thenReturn(connection);

            Shift result = shiftDAO.findOtherOpenShift(currentShiftId);
            assertNull(result);
        }

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(connection).prepareStatement(sqlCaptor.capture());

        String executedSql = sqlCaptor.getValue();
        // Kiểm tra câu lệnh SQL đúng cấu trúc và đã bỏ ORDER BY
        assertTrue(executedSql.contains("SELECT TOP 1"), "Phải có SELECT TOP 1");
        assertTrue(executedSql.contains("FROM Shifts"), "Phải truy vấn bảng Shifts");
        assertTrue(executedSql.contains("WHERE Status = 'Open' AND ShiftId <> ?"), "Phải có điều kiện lọc");
        assertFalse(executedSql.contains("ORDER BY"), "Không được chứa mệnh đề ORDER BY nữa");

        // Kiểm tra tham số được truyền đúng
        verify(statement).setObject(1, currentShiftId);
    }

    @Test
    @DisplayName("findUnclosedPastShift: SQL không còn ORDER BY và lọc đúng WorkDate < ?")
    void testFindUnclosedPastShift_WithoutOrderBy() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);

        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false);

        ShiftDAO shiftDAO = new ShiftDAO();
        LocalDate today = LocalDate.of(2026, 11, 12);

        try (MockedStatic<DBContext> dbMock = mockStatic(DBContext.class)) {
            dbMock.when(DBContext::getConnection).thenReturn(connection);

            Shift result = shiftDAO.findUnclosedPastShift(today);
            assertNull(result);
        }

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(connection).prepareStatement(sqlCaptor.capture());

        String executedSql = sqlCaptor.getValue();
        assertTrue(executedSql.contains("WHERE Status = 'Open' AND WorkDate < ?"));
        assertFalse(executedSql.contains("ORDER BY"), "Không được chứa mệnh đề ORDER BY nữa");
        verify(statement).setObject(1, Date.valueOf(today));
    }
}

