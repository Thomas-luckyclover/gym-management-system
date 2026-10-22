package com.gms.service;

import com.gms.constant.Messages;
import com.gms.constant.Role;
import com.gms.dao.AccountDAO;
import com.gms.dao.CustomerDAO;
import com.gms.dao.CustomerDeletionRequestDAO;
import com.gms.dao.EmployeeDAO;
import com.gms.dao.PersonDAO;
import com.gms.dto.ServiceResult;
import com.gms.entity.Account;
import com.gms.entity.Employee;
import java.sql.SQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Kiểm thử đơn vị cho CustomerDeletionService (BF-07, UC-16, UC-17).
 * Kiểm tra các luật: BR-15, CBR-16, CBR-115 (SelfServiceGuard), GBR-09 (Độ dài lý do).
 */
@ExtendWith(MockitoExtension.class)
class CustomerDeletionServiceTest {

    @Mock
    private CustomerDeletionRequestDAO customerDeletionRequestDAO;

    @Mock
    private CustomerDAO customerDAO;

    @Mock
    private PersonDAO personDAO;

    @Mock
    private AccountDAO accountDAO;

    @Mock
    private EmployeeDAO employeeDAO;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private SelfServiceGuard selfServiceGuard;

    private CustomerDeletionService service;

    @BeforeEach
    void setUp() {
        service = new CustomerDeletionService(
                customerDeletionRequestDAO,
                customerDAO,
                personDAO,
                accountDAO,
                employeeDAO,
                auditLogService,
                notificationService,
                selfServiceGuard
        );
    }

    // ===== Kiểm tra validate lý do xóa của Lễ tân (GBR-09) =====

    @Test
    @DisplayName("Lễ tân xóa: Thất bại khi lý do null hoặc dưới 5 ký tự (MSG19)")
    void requestOrDeleteCustomer_fails_whenReasonTooShort() {
        ServiceResult<String> resultNull = service.requestOrDeleteCustomer(1, 10, null);
        assertFalse(resultNull.isSuccess());
        assertTrue(resultNull.getFieldErrors().containsKey("reason"));

        ServiceResult<String> resultShort = service.requestOrDeleteCustomer(1, 10, "1234");
        assertFalse(resultShort.isSuccess());
        assertTrue(resultShort.getFieldErrors().containsKey("reason"));
        assertEquals(String.format(Messages.MSG19_TOO_SHORT, 5), resultShort.getFieldErrors().get("reason"));
    }

    @Test
    @DisplayName("Lễ tân xóa: Thất bại khi lý do vượt quá 500 ký tự (MSG10)")
    void requestOrDeleteCustomer_fails_whenReasonTooLong() {
        String longReason = "a".repeat(501);
        ServiceResult<String> result = service.requestOrDeleteCustomer(1, 10, longReason);
        assertFalse(result.isSuccess());
        assertTrue(result.getFieldErrors().containsKey("reason"));
        assertEquals(String.format(Messages.MSG10_MAX_LENGTH, 500), result.getFieldErrors().get("reason"));
    }

    @Test
    @DisplayName("Lễ tân xóa: Thất bại khi thao tác trên hồ sơ của chính mình (CBR-115, MSG76)")
    void requestOrDeleteCustomer_fails_whenOwnCustomerProfile() throws SQLException {
        int receptionistAccountId = 2;
        int customerId = 15;
        String reason = "Khách hàng muốn xóa tài khoản cá nhân";

        Account account = new Account(receptionistAccountId, 100, "hash", Account.Status.ACTIVE, 0, null, null, null);

        Employee employee = new Employee(
                5, 100, "NV-0005", Role.RECEPTIONIST,
                null, null, null, null, Employee.Status.WORKING, null, null
        );

        when(accountDAO.findById(receptionistAccountId)).thenReturn(account);
        when(employeeDAO.findByPersonId(100)).thenReturn(employee);
        when(selfServiceGuard.isOwnCustomerProfile(receptionistAccountId, customerId)).thenReturn(true);

        ServiceResult<String> result = service.requestOrDeleteCustomer(receptionistAccountId, customerId, reason);

        assertFalse(result.isSuccess());
        assertEquals(Messages.MSG76_STAFF_OWN_CUSTOMER_PROFILE, result.getMessage());
    }

    // ===== Kiểm tra validate lý do từ chối của Quản lý (GBR-09) =====

    @Test
    @DisplayName("Quản lý từ chối: Thất bại khi lý do từ chối dưới 5 ký tự (MSG19)")
    void rejectDeletion_fails_whenRejectReasonTooShort() {
        ServiceResult<String> resultNull = service.rejectDeletion(1, 10, null);
        assertFalse(resultNull.isSuccess());
        assertTrue(resultNull.getFieldErrors().containsKey("rejectReason"));

        ServiceResult<String> resultShort = service.rejectDeletion(1, 10, "abcd");
        assertFalse(resultShort.isSuccess());
        assertTrue(resultShort.getFieldErrors().containsKey("rejectReason"));
        assertEquals(String.format(Messages.MSG19_TOO_SHORT, 5), resultShort.getFieldErrors().get("rejectReason"));
    }

    @Test
    @DisplayName("Quản lý từ chối: Thất bại khi lý do từ chối vượt quá 500 ký tự (MSG10)")
    void rejectDeletion_fails_whenRejectReasonTooLong() {
        String longReason = "r".repeat(501);
        ServiceResult<String> result = service.rejectDeletion(1, 10, longReason);
        assertFalse(result.isSuccess());
        assertTrue(result.getFieldErrors().containsKey("rejectReason"));
        assertEquals(String.format(Messages.MSG10_MAX_LENGTH, 500), result.getFieldErrors().get("rejectReason"));
    }

    @Test
    @DisplayName("Quản lý duyệt: Thất bại khi Quản lý không có quyền (không phải Manager/Admin)")
    void approveDeletion_fails_whenNotManager() throws SQLException {
        int accountId = 8;
        int requestId = 1;

        Account account = new Account(accountId, 80, "hash", Account.Status.ACTIVE, 0, null, null, null);

        Employee employee = new Employee(
                8, 80, "NV-0008", Role.RECEPTIONIST, // Lễ tân không thể duyệt
                null, null, null, null, Employee.Status.WORKING, null, null
        );

        when(accountDAO.findById(accountId)).thenReturn(account);
        when(employeeDAO.findByPersonId(80)).thenReturn(employee);

        ServiceResult<String> result = service.approveDeletion(accountId, requestId);

        assertFalse(result.isSuccess());
        assertEquals(Messages.MSG07_NO_PERMISSION, result.getMessage());
    }

    @Test
    @DisplayName("Quản lý từ chối: Thất bại khi Quản lý không có quyền (không phải Manager/Admin)")
    void rejectDeletion_fails_whenNotManager() throws SQLException {
        int accountId = 9;
        int requestId = 2;
        String rejectReason = "Không đủ điều kiện xóa";

        Account account = new Account(accountId, 90, "hash", Account.Status.ACTIVE, 0, null, null, null);

        Employee employee = new Employee(
                9, 90, "NV-0009", Role.PT, // PT không thể duyệt/từ chối
                null, null, null, null, Employee.Status.WORKING, null, null
        );

        when(accountDAO.findById(accountId)).thenReturn(account);
        when(employeeDAO.findByPersonId(90)).thenReturn(employee);

        ServiceResult<String> result = service.rejectDeletion(accountId, requestId, rejectReason);

        assertFalse(result.isSuccess());
        assertEquals(Messages.MSG07_NO_PERMISSION, result.getMessage());
    }
}
