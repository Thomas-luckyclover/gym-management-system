package com.gms.service;

import com.gms.dao.*;
import com.gms.dto.CounterCustomerCreateInput;
import com.gms.entity.*;
import com.gms.integration.EmailGateway;
import com.gms.listener.SessionRegistryListener;
import com.gms.util.DBContext;
import java.sql.Connection;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomerProfileContactTest {
    @Test
    void refusesContactChangeWithoutVerification() throws Exception {
        checkChange(false, false);
    }

    @Test
    void refusesDuplicateContact() throws Exception {
        checkChange(true, true);
    }

    @Test
    void savesContactsNotifiesOldEmailAndSignsOutCustomer() throws Exception {
        checkChange(true, false);
    }

    private void checkChange(boolean verified, boolean duplicate) throws Exception {
        Connection conn = mock(Connection.class);
        Customer customer = new Customer();
        customer.setCustomerId(2);
        customer.setPersonId(3);
        customer.setStatus("Active");
        customer.setCustomerType("Self");
        customer.setCheckInPhoto("/uploads/checkin/old.png");
        Person person = new Person();
        person.setPersonId(3);
        person.setFullName("Nguyen Van An");
        person.setDateOfBirth(LocalDate.of(2000, 1, 1));
        person.setPhone("0901234567");
        person.setEmail("old@example.com");
        Account account = mock(Account.class);
        when(account.getAccountId()).thenReturn(9);
        Person other = new Person();
        other.setPersonId(99);
        var input = new CounterCustomerCreateInput("Nguyen Van An", "0901234568", person.getDateOfBirth(),
                "Self", null, null, null, "new@example.com");
        try (var db = mockStatic(DBContext.class);
             var sessions = mockStatic(SessionRegistryListener.class);
             var people = mockConstruction(PersonDAO.class, (m, c) -> {
                 when(m.findForProfileUpdate(conn, 3)).thenReturn(person);
                 if (duplicate) when(m.findByPhone("0901234568")).thenReturn(other);
             });
             var customers = mockConstruction(CustomerDAO.class, (m, c) -> when(m.findById(conn, 2, true)).thenReturn(customer));
             var roles = mockConstruction(PtSaleDAO.class, (m, c) -> when(m.findReceptionist(conn, 1)).thenReturn(new int[]{1, 1}));
             var guard = mockConstruction(SelfServiceGuard.class);
             var accounts = mockConstruction(AccountDAO.class, (m, c) -> when(m.findByPersonId(conn, 3)).thenReturn(account));
             var audit = mockConstruction(AuditLogService.class);
             var email = mockConstruction(EmailGateway.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);
            var result = new CustomerProfileService().update(1, 2, input, null, null, verified);
            if (!verified || duplicate) {
                assertFalse(result.isSuccess());
                verify(conn).rollback();
                verify(conn, never()).commit();
                verify(people.constructed().get(0), never()).updateCustomerContacts(any(), anyInt(), any(), any());
                sessions.verifyNoInteractions();
                assertTrue(email.constructed().isEmpty());
            } else {
                assertTrue(result.isSuccess());
                verify(people.constructed().get(0)).updateCustomerContacts(conn, 3, "0901234568", "new@example.com");
                verify(conn).commit();
                sessions.verify(() -> SessionRegistryListener.invalidateSessionsOfAccount(9, null));
                verify(email.constructed().get(0)).sendNotice(eq("old@example.com"), anyString(), anyString());
            }
        }
    }
}
