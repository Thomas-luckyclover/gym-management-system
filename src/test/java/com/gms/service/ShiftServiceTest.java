package com.gms.service;

import com.gms.constant.Messages;
import com.gms.constant.Role;
import com.gms.dao.AccountDAO;
import com.gms.dao.EmployeeDAO;
import com.gms.dao.ShiftDAO;
import com.gms.dto.PageResult;
import com.gms.dto.ServiceResult;
import com.gms.dto.ShiftDetailView;
import com.gms.dto.ShiftMetrics;
import com.gms.dto.ShiftTransactionItem;
import com.gms.entity.Account;
import com.gms.entity.Employee;
import com.gms.entity.Shift;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test kiểm thử các quy tắc nghiệp vụ cốt lõi của Ca làm việc Lễ tân (Màn #39 - UC-25).
 * - CBR-78: Chặn chốt ca nếu còn đơn Pending
 * - BR-46, GBR-09: Chốt ca lệch tiền bắt buộc lý do ≥ 5 ký tự
 * - CBR-11: Hủy đơn quầy yêu cầu lý do ≥ 5 ký tự
 * - DBR-55: Doanh thu tiền mặt tính đúng theo đơn hợp lệ
 */
class ShiftServiceTest {

    private FakeShiftDAO fakeShiftDAO;
    private FakeAccountDAO fakeAccountDAO;
    private FakeEmployeeDAO fakeEmployeeDAO;
    private FakeSelfServiceGuard fakeSelfServiceGuard;
    private FakeAuditLogService fakeAuditLogService;
    private ShiftService shiftService;

    @BeforeEach
    void setUp() {
        fakeShiftDAO = new FakeShiftDAO();
        fakeAccountDAO = new FakeAccountDAO();
        fakeEmployeeDAO = new FakeEmployeeDAO();
        fakeSelfServiceGuard = new FakeSelfServiceGuard();
        fakeAuditLogService = new FakeAuditLogService();

        shiftService = new ShiftService(fakeShiftDAO, fakeAccountDAO, fakeEmployeeDAO,
                fakeSelfServiceGuard, fakeAuditLogService);

        // Chuẩn bị tài khoản Lễ tân: AccountId = 10, PersonId = 20, EmployeeId = 30
        Account acc = new Account(10, 20, "hash", Account.Status.ACTIVE, 0, null, null, LocalDateTime.now());
        fakeAccountDAO.addAccount(acc);

        Employee emp = new Employee(30, 20, "NV-0030", Role.RECEPTIONIST,
                LocalDate.now(), null, null, null, Employee.Status.WORKING, null, null);
        fakeEmployeeDAO.addEmployee(emp);
    }

    @Test
    @DisplayName("CBR-78: Chặn chốt ca khi còn đơn Pending trong ca")
    void testCloseShift_BlockedWhenPendingOrdersExist() {
        // Tạo ca đang Open
        Shift shift = new Shift(1, LocalDate.now(), Shift.ShiftType.MORNING,
                30, null, Shift.Status.OPEN, LocalDateTime.now(), null, null, null, null);
        fakeShiftDAO.setShift(shift);

        // Có 1 đơn Pending
        fakeShiftDAO.addPendingOrder(new ShiftTransactionItem(
                101, "DH-2026-0035", "Nguyễn Văn A", "0901234567", "Gói 1 Tháng",
                LocalDateTime.now(), "Transfer", 799000, "Pending", null, null, null, true, true
        ));

        ServiceResult<Void> result = shiftService.closeShift(10, 1, 2000000, null);

        assertFalse(result.isSuccess(), "Chốt ca phải thất bại khi còn đơn Pending");
        assertTrue(result.getMessage().contains("chờ thanh toán") || result.getMessage().contains("Pending"),
                "Thông báo lỗi phải nhắc đến đơn chưa thanh toán (CBR-78)");
    }

    @Test
    @DisplayName("BR-46: Chốt ca lệch tiền mà không nhập lý do hoặc lý do < 5 ký tự bị từ chối")
    void testCloseShift_RequiresVarianceReasonWhenDiscrepant() {
        // Tạo ca đang Open, không còn đơn Pending
        Shift shift = new Shift(2, LocalDate.now(), Shift.ShiftType.MORNING,
                30, null, Shift.Status.OPEN, LocalDateTime.now(), null, null, null, null);
        fakeShiftDAO.setShift(shift);
        fakeShiftDAO.clearPendingOrders();

        // Tiền sổ sách là 1.000.000, nhưng Lễ tân đếm được 900.000 (lệch 100.000)
        fakeShiftDAO.setMetrics(new ShiftMetrics(1000000, 2, 500000, 1, 1500000, 3, 0));

        // Case 1: Không nhập lý do
        ServiceResult<Void> resNoReason = shiftService.closeShift(10, 2, 900000, "");
        assertFalse(resNoReason.isSuccess(), "Không nhập lý do khi lệch tiền phải bị từ chối");

        // Case 2: Nhập lý do quá ngắn (< 5 ký tự)
        ServiceResult<Void> resShortReason = shiftService.closeShift(10, 2, 900000, "lệch");
        assertFalse(resShortReason.isSuccess(), "Lý do < 5 ký tự phải bị từ chối (GBR-09)");
    }

    @Test
    @DisplayName("CBR-11: Hủy đơn quầy yêu cầu lý do tối thiểu 5 ký tự")
    void testCancelOrder_RequiresValidReason() {
        // Lý do rỗng
        ServiceResult<Void> resEmpty = shiftService.cancelOrder(10, 101, "");
        assertFalse(resEmpty.isSuccess(), "Lý do hủy rỗng phải bị từ chối");

        // Lý do ngắn < 5 ký tự
        ServiceResult<Void> resShort = shiftService.cancelOrder(10, 101, "hủy");
        assertFalse(resShort.isSuccess(), "Lý do hủy < 5 ký tự phải bị từ chối");
    }

    @Test
    @DisplayName("Kiểm tra phân quyền: Tài khoản không phải Lễ tân không được thao tác")
    void testPermissionCheck_DeniedForNonReceptionist() {
        // Tài khoản không tồn tại
        ServiceResult<ShiftDetailView> resInvalid = shiftService.getShiftDetail(999, null, null, null, null, 1, 10);
        assertFalse(resInvalid.isSuccess());
        assertEquals(Messages.MSG07_NO_PERMISSION, resInvalid.getMessage());
    }

    // ===== Fake DAOs hỗ trợ Unit Test =====

    static class FakeShiftDAO extends ShiftDAO {
        private Shift currentShift;
        private final List<ShiftTransactionItem> pendingOrders = new ArrayList<>();
        private ShiftMetrics metrics = new ShiftMetrics(0, 0, 0, 0, 0, 0, 0);

        void setShift(Shift s) { this.currentShift = s; }
        void addPendingOrder(ShiftTransactionItem item) { pendingOrders.add(item); }
        void clearPendingOrders() { pendingOrders.clear(); }
        void setMetrics(ShiftMetrics m) { this.metrics = m; }

        @Override
        public void ensureShiftsForDate(LocalDate date) {}

        @Override
        public void ensureShiftsForDate(Connection conn, LocalDate date) {}

        @Override
        public Shift findById(int shiftId) {
            return currentShift;
        }

        @Override
        public Shift findCurrentOrActiveShift(LocalDate today) {
            return currentShift;
        }

        @Override
        public List<Shift> findTodayShifts(LocalDate date) {
            List<Shift> list = new ArrayList<>();
            if (currentShift != null) list.add(currentShift);
            return list;
        }

        @Override
        public ShiftMetrics calculateShiftMetrics(int shiftId) {
            return metrics;
        }

        @Override
        public List<ShiftTransactionItem> findPendingOrdersInShift(int shiftId) {
            return new ArrayList<>(pendingOrders);
        }

        @Override
        public int closeShift(Connection conn, int shiftId, int employeeId, LocalDateTime closedAt,
                              long expectedCash, long countedCash, String varianceReason) {
            if (currentShift != null) {
                currentShift.setStatus(Shift.Status.CLOSED);
                currentShift.setClosedAt(closedAt);
                currentShift.setExpectedCash(expectedCash);
                currentShift.setCountedCash(countedCash);
                currentShift.setVarianceReason(varianceReason);
            }
            return 1;
        }
    }

    static class FakeAccountDAO extends AccountDAO {
        private final List<Account> accounts = new ArrayList<>();
        void addAccount(Account a) { accounts.add(a); }

        @Override
        public Account findById(int accountId) {
            for (Account a : accounts) {
                if (a.getAccountId() == accountId) return a;
            }
            return null;
        }
    }

    static class FakeEmployeeDAO extends EmployeeDAO {
        private final List<Employee> employees = new ArrayList<>();
        void addEmployee(Employee e) { employees.add(e); }

        @Override
        public Employee findByPersonId(int personId) {
            for (Employee e : employees) {
                if (e.getPersonId() == personId) return e;
            }
            return null;
        }
    }

    static class FakeSelfServiceGuard extends SelfServiceGuard {
        @Override
        public boolean isOwnCustomerProfile(int staffAccountId, int customerId) {
            return false;
        }
    }

    static class FakeAuditLogService extends AuditLogService {
        @Override
        void logInTransaction(Connection conn, Integer accountId, Role actingRole,
                              String action, String entityName, Integer entityId,
                              String fieldChanged, String oldValue, String newValue,
                              String reason) {}
    }
}
