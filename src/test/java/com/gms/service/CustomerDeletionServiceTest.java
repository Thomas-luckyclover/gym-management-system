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
import com.gms.entity.Customer;
import com.gms.entity.CustomerDeletionRequest;
import com.gms.entity.Person;
import com.gms.dto.CustomerProfileRowView;
import com.gms.util.DBContext;
import java.sql.Connection;
import java.util.List;
import org.mockito.MockedStatic;
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

    // ===== Các nhánh ghi dữ liệu phải commit/rollback toàn bộ nghiệp vụ =====

    @Test
    void noAssetsDeletesImmediatelyAndKeepsHistoryTablesUntouched() throws Exception {
        mockActor(2, Role.RECEPTIONIST);
        Connection connection = mock(Connection.class);
        when(customerDAO.findById(connection, 15, true)).thenReturn(activeCustomer());
        when(customerDAO.softDeleteCustomer(connection, 15)).thenReturn(true);
        try (MockedStatic<DBContext> database = mockStatic(DBContext.class)) {
            database.when(DBContext::getConnection).thenReturn(connection);
            assertTrue(service.requestOrDeleteCustomer(2, 15, "Khách yêu cầu ngừng hồ sơ").isSuccess());
        }
        verify(connection).commit();
        verify(connection, never()).rollback();
        verify(customerDeletionRequestDAO, never()).insert(any(), any());
        verify(personDAO).releasePersonContactIfNoActiveRoles(connection, 400);
        verify(accountDAO).deactivateAccountIfNoActiveRoles(connection, 400);
        verify(auditLogService).logInTransaction(eq(connection), eq(2), eq(Role.RECEPTIONIST),
                anyString(), eq("Customers"), eq(15), eq("Status"), eq("Active"), eq("Inactive"), anyString());
    }

    @Test
    void remainingPackageCreatesPendingAndDoesNotDeleteCustomer() throws Exception {
        mockActor(2, Role.RECEPTIONIST);
        Connection connection = mock(Connection.class);
        when(customerDAO.findById(connection, 15, true)).thenReturn(activeCustomer());
        when(customerDAO.countActiveSubscriptions(connection, 15)).thenReturn(1);
        when(customerDeletionRequestDAO.insert(eq(connection), any())).thenReturn(88);
        try (MockedStatic<DBContext> database = mockStatic(DBContext.class)) {
            database.when(DBContext::getConnection).thenReturn(connection);
            assertTrue(service.requestOrDeleteCustomer(2, 15, "Khách muốn ngừng sử dụng dịch vụ").isSuccess());
        }
        verify(connection).commit();
        verify(customerDAO, never()).softDeleteCustomer(any(), anyInt());
        verify(notificationService).notifyRoleInTransaction(eq(connection), eq(Role.MANAGER), anyString(), anyString());
    }

    @Test
    void scheduledSessionAlsoRequiresManagerApproval() throws Exception {
        mockActor(2, Role.RECEPTIONIST);
        Connection connection = mock(Connection.class);
        when(customerDAO.findById(connection, 15, true)).thenReturn(activeCustomer());
        when(customerDAO.countScheduledSessions(connection, 15)).thenReturn(2);
        when(customerDeletionRequestDAO.insert(eq(connection), any())).thenReturn(89);
        try (MockedStatic<DBContext> database = mockStatic(DBContext.class)) {
            database.when(DBContext::getConnection).thenReturn(connection);
            assertTrue(service.requestOrDeleteCustomer(2, 15, "Khách yêu cầu xóa hồ sơ").isSuccess());
        }
        verify(customerDAO, never()).softDeleteCustomer(any(), anyInt());
        verify(connection).commit();
    }

    @Test
    void duplicateRequestRollsBackWithoutCreatingAnotherRequest() throws Exception {
        mockActor(2, Role.RECEPTIONIST);
        Connection connection = mock(Connection.class);
        when(customerDAO.findById(connection, 15, true)).thenReturn(activeCustomer());
        when(customerDeletionRequestDAO.hasPendingRequest(connection, 15)).thenReturn(true);
        try (MockedStatic<DBContext> database = mockStatic(DBContext.class)) {
            database.when(DBContext::getConnection).thenReturn(connection);
            assertFalse(service.requestOrDeleteCustomer(2, 15, "Khách yêu cầu xóa hồ sơ").isSuccess());
        }
        verify(connection).rollback();
        verify(connection, never()).commit();
        verify(customerDeletionRequestDAO, never()).insert(any(), any());
    }

    @Test
    void auditFailureRollsBackTheDeletion() throws Exception {
        mockActor(2, Role.RECEPTIONIST);
        Connection connection = mock(Connection.class);
        when(customerDAO.findById(connection, 15, true)).thenReturn(activeCustomer());
        when(customerDAO.softDeleteCustomer(connection, 15)).thenReturn(true);
        doThrow(new SQLException("Test rollback"))
                .when(auditLogService).logInTransaction(eq(connection), anyInt(), any(), anyString(),
                        anyString(), anyInt(), anyString(), any(), any(), anyString());
        try (MockedStatic<DBContext> database = mockStatic(DBContext.class)) {
            database.when(DBContext::getConnection).thenReturn(connection);
            assertFalse(service.requestOrDeleteCustomer(2, 15, "Khách yêu cầu xóa hồ sơ").isSuccess());
        }
        verify(connection).rollback();
        verify(connection, never()).commit();
    }

    @Test
    void managerApprovesPendingAndDeactivatesCustomerInOneTransaction() throws Exception {
        mockActor(1, Role.MANAGER);
        Connection connection = mock(Connection.class);
        when(customerDeletionRequestDAO.findById(connection, 7, true)).thenReturn(pendingRequest());
        when(customerDAO.findById(connection, 15, true)).thenReturn(activeCustomer());
        when(customerDeletionRequestDAO.approve(connection, 7, 1)).thenReturn(true);
        when(customerDAO.softDeleteCustomer(connection, 15)).thenReturn(true);
        try (MockedStatic<DBContext> database = mockStatic(DBContext.class)) {
            database.when(DBContext::getConnection).thenReturn(connection);
            assertTrue(service.approveDeletion(1, 7).isSuccess());
        }
        verify(connection).commit();
        verify(customerDAO).softDeleteCustomer(connection, 15);
        verify(notificationService).notifyEmployeeInTransaction(eq(connection), eq(3), anyString(), anyString());
    }

    @Test
    void processedRequestCannotBeApprovedTwice() throws Exception {
        mockActor(1, Role.MANAGER);
        Connection connection = mock(Connection.class);
        CustomerDeletionRequest request = pendingRequest();
        request.setStatus(CustomerDeletionRequest.Status.APPROVED);
        when(customerDeletionRequestDAO.findById(connection, 7, true)).thenReturn(request);
        try (MockedStatic<DBContext> database = mockStatic(DBContext.class)) {
            database.when(DBContext::getConnection).thenReturn(connection);
            assertFalse(service.approveDeletion(1, 7).isSuccess());
        }
        verify(connection).rollback();
        verify(customerDeletionRequestDAO, never()).approve(any(), anyInt(), anyInt());
    }

    @Test
    void ownProfileCannotBeApprovedOrRejected() throws Exception {
        mockActor(1, Role.MANAGER);
        Connection connection = mock(Connection.class);
        when(customerDeletionRequestDAO.findById(connection, 7, true)).thenReturn(pendingRequest());
        when(selfServiceGuard.isOwnCustomerProfile(1, 15)).thenReturn(true);
        try (MockedStatic<DBContext> database = mockStatic(DBContext.class)) {
            database.when(DBContext::getConnection).thenReturn(connection);
            assertFalse(service.approveDeletion(1, 7).isSuccess());
            assertFalse(service.rejectDeletion(1, 7, "Không tự xử lý hồ sơ của mình").isSuccess());
        }
        verify(connection, times(2)).rollback();
        verify(customerDeletionRequestDAO, never()).approve(any(), anyInt(), anyInt());
        verify(customerDeletionRequestDAO, never()).reject(any(), anyInt(), anyInt(), anyString());
    }

    @Test
    void rejectingRequestDoesNotDeactivateCustomer() throws Exception {
        mockActor(1, Role.MANAGER);
        Connection connection = mock(Connection.class);
        when(customerDeletionRequestDAO.findById(connection, 7, true)).thenReturn(pendingRequest());
        when(customerDeletionRequestDAO.reject(connection, 7, 1, "Khách vẫn tiếp tục sử dụng gói")).thenReturn(true);
        try (MockedStatic<DBContext> database = mockStatic(DBContext.class)) {
            database.when(DBContext::getConnection).thenReturn(connection);
            assertTrue(service.rejectDeletion(1, 7, "Khách vẫn tiếp tục sử dụng gói").isSuccess());
        }
        verify(connection).commit();
        verify(customerDAO, never()).softDeleteCustomer(any(), anyInt());
    }

    // ===== Trạng thái, phân trang và dữ liệu dùng trên giao diện =====

    @Test
    void receptionistListIncludesFrozenExpiredAndNewProfiles() throws Exception {
        when(customerDAO.countCustomerProfiles(null, "ALL", false)).thenReturn(1);
        CustomerProfileRowView row = new CustomerProfileRowView(15, "KH-0015", 400,
                "Hội viên đang bảo lưu", null, null, "Self", "Frozen", null, true, false, null, null, null);
        when(customerDAO.findCustomerProfiles(null, "ALL", false, 0, 10)).thenReturn(List.of(row));
        when(accountDAO.findById(2)).thenReturn(new Account(102, null, Account.Status.ACTIVE));
        var result = service.getProfilesPage(2, null, null, false, 1, 10);
        assertTrue(result.isSuccess());
        assertEquals("Đang bảo lưu", result.getData().getItems().get(0).getStatusLabel());
        assertFalse(row.isOwnProfile());
    }

    @Test
    void databaseFailureIsReportedAsErrorRatherThanEmptyList() throws Exception {
        when(customerDAO.countCustomerProfiles(null, "ALL", true)).thenThrow(new SQLException("Test list error"));
        var result = service.getProfilesPage(1, null, null, true, 1, 10);
        assertFalse(result.isSuccess());
        assertNull(result.getData());
        assertEquals(Messages.MSG04_SYSTEM_BUSY, result.getMessage());
    }

    @Test
    void excessivePageNumberLoadsLastPage() throws Exception {
        when(customerDeletionRequestDAO.countPending()).thenReturn(12);
        when(customerDeletionRequestDAO.findPendingViews(1, 10, 10)).thenReturn(List.of());
        var result = service.getPendingPage(1, 999, 10);
        assertTrue(result.isSuccess());
        assertEquals(2, result.getData().getPageNumber());
    }

    @Test
    void receptionistCannotReadInactiveProfileEvenWithDirectUrl() throws Exception {
        Connection connection = mock(Connection.class);
        Customer customer = activeCustomer();
        customer.setStatus("Inactive");
        when(customerDAO.findById(connection, 15, false)).thenReturn(customer);
        try (MockedStatic<DBContext> database = mockStatic(DBContext.class)) {
            database.when(DBContext::getConnection).thenReturn(connection);
            assertFalse(service.getProfileDetail(2, 15, false).isSuccess());
        }
        verifyNoInteractions(personDAO);
    }

    @Test
    void statusFilterCannotBeUsedToExposeInactiveProfilesToReceptionist() {
        assertEquals("ALL", service.normalizeStatus("Inactive", false));
        assertEquals("Inactive", service.normalizeStatus("Inactive", true));
        assertEquals("Frozen", service.normalizeStatus("Frozen", false));
        assertEquals("ALL", service.normalizeStatus("Unknown", true));
    }

    private void mockActor(int accountId, Role role) throws SQLException {
        int personId = 100 + accountId;
        when(accountDAO.findById(accountId)).thenReturn(
                new Account(accountId, personId, "hash", Account.Status.ACTIVE, 0, null, null, null));
        when(employeeDAO.findByPersonId(personId)).thenReturn(new Employee(
                accountId, personId, "NV-000" + accountId, role,
                null, null, null, null, Employee.Status.WORKING, null, null));
    }

    private Customer activeCustomer() {
        Customer customer = new Customer(400, "KH-0015", "Self", "Active");
        customer.setCustomerId(15);
        return customer;
    }

    private CustomerDeletionRequest pendingRequest() {
        CustomerDeletionRequest request = new CustomerDeletionRequest();
        request.setCustomerDeletionRequestId(7);
        request.setCustomerId(15);
        request.setRequestedByEmployeeId(3);
        request.setStatus(CustomerDeletionRequest.Status.PENDING);
        request.setReason("Khách yêu cầu xóa hồ sơ");
        return request;
    }
}
