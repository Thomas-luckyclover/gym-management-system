package com.gms.service;

import com.gms.dao.*;
import com.gms.dto.CounterCustomerCreateInput;
import com.gms.entity.Customer;
import com.gms.util.DBContext;
import java.sql.Connection;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomerProfileCreateTest {
    @Test
    void selfCreatesAccountAndSavesPhotoInTransaction() throws Exception {
        checkCreate(false);
    }

    @Test
    void dependentSavesGuardianWithoutCreatingAccount() throws Exception {
        checkCreate(true);
    }

    private void checkCreate(boolean dependent) throws Exception {
        Connection conn = mock(Connection.class);
        var input = new CounterCustomerCreateInput("Nguyen Van An", "0901234567", LocalDate.of(2010, 1, 1),
                dependent ? "Dependent" : "Self", "Nguyen Van Binh", "Bo", "0901234568", "test@example.com");
        try (var db = mockStatic(DBContext.class);
             var sales = mockConstruction(CounterSaleDAO.class);
             var people = mockConstruction(PersonDAO.class, (m, c) -> when(m.insert(eq(conn), any())).thenReturn(10));
             var customers = mockConstruction(CustomerDAO.class, (m, c) -> {
                 when(m.generateNextCustomerCode(conn)).thenReturn("KH-0011");
                 when(m.insert(eq(conn), any())).thenReturn(11);
             });
             var role = mockConstruction(PtSaleDAO.class, (m, c) -> when(m.findReceptionist(conn, 1)).thenReturn(new int[]{1, 2}));
             var audit = mockConstruction(AuditLogService.class);
             var accounts = mockConstruction(CounterAccountService.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);
            var result = new CounterSaleService().quickCreateCustomer(1, input, dependent ? "0901234569" : null, "/uploads/checkin/test.png");
            assertTrue(result.isSuccess(), result.getMessage());
            var captor = org.mockito.ArgumentCaptor.forClass(Customer.class);
            verify(customers.constructed().get(0)).insert(eq(conn), captor.capture());
            assertEquals("/uploads/checkin/test.png", captor.getValue().getCheckInPhoto());
            assertEquals(dependent ? "0901234569" : null, captor.getValue().getGuardianPhone2());
            if (dependent) {
                verifyNoInteractions(accounts.constructed().get(0));
            } else {
                verify(accounts.constructed().get(0)).createWithTempPassword(conn, 10, "test@example.com", "Nguyen Van An", "KH-0011");
            }
            verify(conn).commit();
            verify(conn, never()).rollback();
        }
    }
}
