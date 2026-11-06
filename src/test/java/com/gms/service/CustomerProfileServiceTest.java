package com.gms.service;

import com.gms.dao.CustomerDAO;
import com.gms.dao.PtSaleDAO;
import com.gms.dao.PersonDAO;
import com.gms.entity.Person;
import com.gms.dto.CounterCustomerCreateInput;
import com.gms.entity.Customer;
import com.gms.util.DBContext;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomerProfileServiceTest {
    private CounterCustomerCreateInput input() {
        return new CounterCustomerCreateInput("Nguyen Van An", "0901234567", LocalDate.of(2000, 1, 1),
                "Self", null, null, null, "test@example.com");
    }

    @Test
    void refusesUnauthorizedOperatorWithoutWriting() throws Exception {
        Connection conn = mock(Connection.class);
        try (var db = mockStatic(DBContext.class);
             var role = mockConstruction(PtSaleDAO.class);
             var customers = mockConstruction(CustomerDAO.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);
            assertFalse(new CustomerProfileService().update(1, 2, input(), null, null).isSuccess());
            verify(conn).rollback();
            verify(conn, never()).commit();
            verifyNoInteractions(customers.constructed().get(0));
        }
    }

    @Test
    void refusesInactiveProfileWithoutWriting() throws Exception {
        Connection conn = mock(Connection.class);
        Customer customer = new Customer();
        customer.setStatus("Inactive");
        try (var db = mockStatic(DBContext.class);
             var role = mockConstruction(PtSaleDAO.class, (m, c) -> when(m.findReceptionist(conn, 1)).thenReturn(new int[]{1, 1}));
             var guard = mockConstruction(SelfServiceGuard.class);
             var customers = mockConstruction(CustomerDAO.class, (m, c) -> when(m.findById(conn, 2, true)).thenReturn(customer))) {
            db.when(DBContext::getConnection).thenReturn(conn);
            assertFalse(new CustomerProfileService().update(1, 2, input(), null, null).isSuccess());
            verify(conn).rollback();
            verify(conn, never()).prepareStatement(anyString());
        }
    }

    @Test
    void rollsBackIfCustomerUpdateFails() throws Exception {
        Connection conn = mock(Connection.class);
        PreparedStatement stmt = mock(PreparedStatement.class);
        when(conn.prepareStatement(anyString())).thenReturn(stmt);
        when(stmt.executeUpdate()).thenReturn(1);
        Customer customer = new Customer();
        customer.setCustomerId(2);
        customer.setPersonId(3);
        customer.setCustomerType("Self");
        customer.setStatus("Active");
        try (var db = mockStatic(DBContext.class);
             var role = mockConstruction(PtSaleDAO.class, (m, c) -> when(m.findReceptionist(conn, 1)).thenReturn(new int[]{1, 1}));
             var guard = mockConstruction(SelfServiceGuard.class);
             var persons = mockConstruction(PersonDAO.class, (m, c) -> {
                 Person person = new Person();
                 person.setPhone("0901234567");
                 person.setEmail("test@example.com");
                 when(m.findForProfileUpdate(conn, 3)).thenReturn(person);
             });
             var customers = mockConstruction(CustomerDAO.class, (m, c) -> {
                 when(m.findById(conn, 2, true)).thenReturn(customer);
                 doThrow(new SQLException("failure")).when(m).updateProfile(conn, customer);
             })) {
            db.when(DBContext::getConnection).thenReturn(conn);
            assertFalse(new CustomerProfileService().update(1, 2, input(), null, null).isSuccess());
            verify(conn).rollback();
            verify(conn, never()).commit();
        }
    }
}
