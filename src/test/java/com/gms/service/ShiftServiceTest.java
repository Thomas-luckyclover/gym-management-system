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
import com.gms.util.DateTimeUtil;
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
 * - CBR-104 & CBR-105: Thứ tự ca và kiểm soát mở ca (chặn mở ca sáng khi ca chiều đã mở/chốt)
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

        shiftService = new TestableShiftService(fakeShiftDAO, fakeAccountDAO, fakeEmployeeDAO,
                fakeSelfServiceGuard, fakeAuditLogService);

        // Chuẩn bị tài khoản Lễ tân: AccountId = 10, PersonId = 20, EmployeeId = 30
        Account acc = new Account(10, 20, "hash", Account.Status.ACTIVE, 0, null, null, LocalDateTime.now());
        fakeAccountDAO.addAccount(acc);

        Employee emp = new Employee(30, 20, "NV-0030", Role.RECEPTIONIST,
                LocalDate.now(), null, null, null, Employee.Status.WORKING, null, null);
        fakeEmployeeDAO.addEmployee(emp);
    }

    // ===== 1. Kiểm thử nghiệp vụ MỞ CA (openShift & getShiftDetail) =====

    @Test
    @DisplayName("CBR-104: Chặn mở ca sáng khi ca chiều của ngày hôm nay đã chốt")
    void testOpenShift_BlockedWhenAfternoonShiftAlreadyClosed() {
        LocalDate today = LocalDate.now();

        // Ca sáng chưa mở
        Shift morning = new Shift(1, today, Shift.ShiftType.MORNING,
                null, null, Shift.Status.NOT_OPENED, null, null, null, null, null);

        // Ca chiều đã chốt
        Shift afternoon = new Shift(2, today, Shift.ShiftType.AFTERNOON,
                30, 30, Shift.Status.CLOSED,
                today.atTime(13, 0), today.atTime(21, 30), 1000000L, 1000000L, null);

        fakeShiftDAO.setTodayShifts(List.of(morning, afternoon));
        fakeShiftDAO.setShift(morning);

        ServiceResult<Void> result = shiftService.openShift(10, 1);

        assertFalse(result.isSuccess(), "Mở ca sáng phải thất bại khi ca chiều đã chốt");
        assertTrue(result.getMessage().contains("Ca chiều") && result.getMessage().contains("chốt"),
                "Thông báo phải nêu rõ ca chiều đã chốt: " + result.getMessage());
    }

    @Test
    @DisplayName("CBR-104: Chặn mở ca sáng khi ca chiều đang mở")
    void testOpenShift_BlockedWhenAfternoonShiftAlreadyOpen() {
        LocalDate today = LocalDate.now();

        Shift morning = new Shift(1, today, Shift.ShiftType.MORNING,
                null, null, Shift.Status.NOT_OPENED, null, null, null, null, null);

        Shift afternoon = new Shift(2, today, Shift.ShiftType.AFTERNOON,
                30, null, Shift.Status.OPEN, today.atTime(13, 0), null, null, null, null);

        fakeShiftDAO.setTodayShifts(List.of(morning, afternoon));
        fakeShiftDAO.setShift(morning);

        ServiceResult<Void> result = shiftService.openShift(10, 1);

        assertFalse(result.isSuccess(), "Mở ca sáng phải thất bại khi ca chiều đang mở");
        assertTrue(result.getMessage().contains("đang mở"),
                "Thông báo phải nêu rõ ca chiều đang mở: " + result.getMessage());
    }

    @Test
    @DisplayName("CBR-104: Chặn mở ca làm việc của ngày trong quá khứ")
    void testOpenShift_BlockedForPastDate() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        Shift pastShift = new Shift(99, yesterday, Shift.ShiftType.MORNING,
                null, null, Shift.Status.NOT_OPENED, null, null, null, null, null);
        fakeShiftDAO.setShift(pastShift);

        ServiceResult<Void> result = shiftService.openShift(10, 99);

        assertFalse(result.isSuccess(), "Không được mở ca của ngày quá khứ");
        assertTrue(result.getMessage().contains("quá khứ"),
                "Thông báo phải nhắc đến ngày quá khứ: " + result.getMessage());
    }

    @Test
    @DisplayName("CBR-104: Chặn mở ca làm việc của ngày trong tương lai")
    void testOpenShift_BlockedForFutureDate() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        Shift futureShift = new Shift(100, tomorrow, Shift.ShiftType.MORNING,
                null, null, Shift.Status.NOT_OPENED, null, null, null, null, null);
        fakeShiftDAO.setShift(futureShift);

        ServiceResult<Void> result = shiftService.openShift(10, 100);

        assertFalse(result.isSuccess(), "Không được mở trước ca của ngày tương lai");
        assertTrue(result.getMessage().contains("tương lai"),
                "Thông báo phải nhắc đến ngày tương lai: " + result.getMessage());
    }

    @Test
    @DisplayName("CBR-104: Chặn mở ca làm việc vào ngày phòng tập đóng cửa cả ngày")
    void testOpenShift_BlockedWhenGymClosedAllDay() {
        LocalDate today = LocalDate.now();
        Shift shift = new Shift(1, today, Shift.ShiftType.MORNING,
                null, null, Shift.Status.NOT_OPENED, null, null, null, null, null);
        fakeShiftDAO.setShift(shift);
        fakeShiftDAO.setGymClosed(true);

        ServiceResult<Void> result = shiftService.openShift(10, 1);

        assertFalse(result.isSuccess(), "Không được mở ca khi phòng tập đóng cửa cả ngày");
        assertTrue(result.getMessage().contains("đóng cửa cả ngày"),
                "Thông báo phải nhắc đến lịch nghỉ: " + result.getMessage());
    }

    @Test
    @DisplayName("CBR-104: Chặn mở lại ca đã Closed hoặc đang Open")
    void testOpenShift_BlockedWhenAlreadyClosedOrOpen() {
        LocalDate today = LocalDate.now();
        Shift closedShift = new Shift(1, today, Shift.ShiftType.MORNING,
                30, 30, Shift.Status.CLOSED, today.atTime(5, 30), today.atTime(13, 0), 0L, 0L, null);
        fakeShiftDAO.setShift(closedShift);

        ServiceResult<Void> result = shiftService.openShift(10, 1);

        assertFalse(result.isSuccess(), "Không được mở lại ca đã chốt");
        assertTrue(result.getMessage().contains("đã được mở hoặc đã chốt"),
                "Thông báo phải báo ca đã mở/chốt: " + result.getMessage());
    }

    @Test
    @DisplayName("CBR-104: Chặn mở ca chiều khi ca sáng vẫn đang mở")
    void testOpenShift_AfternoonBlockedWhenMorningShiftOpen() {
        LocalDate today = LocalDate.now();

        Shift morning = new Shift(1, today, Shift.ShiftType.MORNING,
                30, null, Shift.Status.OPEN, today.atTime(5, 30), null, null, null, null);
        Shift afternoon = new Shift(2, today, Shift.ShiftType.AFTERNOON,
                null, null, Shift.Status.NOT_OPENED, null, null, null, null, null);

        fakeShiftDAO.setTodayShifts(List.of(morning, afternoon));
        fakeShiftDAO.setShift(afternoon);

        ServiceResult<Void> result = shiftService.openShift(10, 2);

        assertFalse(result.isSuccess(), "Không được mở ca chiều khi ca sáng vẫn đang mở");
        assertTrue(result.getMessage().contains("Ca sáng đang mở"),
                "Thông báo phải nhắc ca sáng đang mở: " + result.getMessage());
    }

    @Test
    @DisplayName("CBR-104: Mở ca chiều thành công khi ca sáng đã chốt xong")
    void testOpenShift_AfternoonSuccessWhenMorningClosed() {
        LocalDate today = LocalDate.now();

        Shift morning = new Shift(1, today, Shift.ShiftType.MORNING,
                30, 30, Shift.Status.CLOSED, today.atTime(5, 30), today.atTime(13, 0), 500000L, 500000L, null);
        Shift afternoon = new Shift(2, today, Shift.ShiftType.AFTERNOON,
                null, null, Shift.Status.NOT_OPENED, null, null, null, null, null);

        fakeShiftDAO.setTodayShifts(List.of(morning, afternoon));
        fakeShiftDAO.setShift(afternoon);

        ServiceResult<Void> result = shiftService.openShift(10, 2);

        assertTrue(result.isSuccess(), "Mở ca chiều phải thành công khi ca sáng đã chốt");
        assertEquals(Shift.Status.OPEN, afternoon.getStatus());
    }

    @Test
    @DisplayName("Giao diện: canOpen = false và lý do giải trình khi ca chiều đã chốt nhưng ca sáng chưa mở")
    void testGetShiftDetail_CanOpenFalseWhenAfternoonClosed() {
        LocalDate today = LocalDate.now();

        Shift morning = new Shift(1, today, Shift.ShiftType.MORNING,
                null, null, Shift.Status.NOT_OPENED, null, null, null, null, null);
        Shift afternoon = new Shift(2, today, Shift.ShiftType.AFTERNOON,
                30, 30, Shift.Status.CLOSED, today.atTime(13, 0), today.atTime(21, 30), 0L, 0L, null);

        fakeShiftDAO.setTodayShifts(List.of(morning, afternoon));
        fakeShiftDAO.setShift(morning);

        ServiceResult<ShiftDetailView> res = shiftService.getShiftDetail(10, 1, null, null, null, 1, 10);

        assertTrue(res.isSuccess());
        ShiftDetailView view = res.getData();
        assertFalse(view.isCanOpen(), "Ca sáng không được phép mở khi ca chiều đã chốt");
        assertNotNull(view.getShiftNotOpenedReason());
        assertTrue(view.getShiftNotOpenedReason().contains("Ca chiều hôm nay đã chốt"));
    }

    @Test
    @DisplayName("CBR-104: findCurrentOrActiveShift ưu tiên chính xác ca cần làm việc")
    void testFindCurrentOrActiveShift_Prioritization() throws SQLException {
        LocalDate today = LocalDate.now();

        Shift morningClosed = new Shift(1, today, Shift.ShiftType.MORNING,
                30, 30, Shift.Status.CLOSED, today.atTime(5, 30), today.atTime(13, 0), 0L, 0L, null);
        Shift afternoonNotOpened = new Shift(2, today, Shift.ShiftType.AFTERNOON,
                null, null, Shift.Status.NOT_OPENED, null, null, null, null, null);

        // Case 1: Ca sáng đã chốt, Ca chiều chưa mở -> ưu tiên Ca chiều
        ShiftDAO dao = new ShiftDAO() {
            @Override
            public List<Shift> findTodayShifts(LocalDate date) {
                return List.of(morningClosed, afternoonNotOpened);
            }
        };
        Shift active1 = dao.findCurrentOrActiveShift(today);
        assertNotNull(active1);
        assertEquals(Shift.ShiftType.AFTERNOON, active1.getShiftType(), "Phải ưu tiên Ca chiều khi Ca sáng đã chốt");

        // Case 2: Ca chiều đã chốt -> hiển thị Ca chiều
        Shift afternoonClosed = new Shift(2, today, Shift.ShiftType.AFTERNOON,
                30, 30, Shift.Status.CLOSED, today.atTime(13, 0), today.atTime(21, 30), 0L, 0L, null);
        ShiftDAO dao2 = new ShiftDAO() {
            @Override
            public List<Shift> findTodayShifts(LocalDate date) {
                return List.of(morningClosed, afternoonClosed);
            }
        };
        Shift active2 = dao2.findCurrentOrActiveShift(today);
        assertNotNull(active2);
        assertEquals(Shift.ShiftType.AFTERNOON, active2.getShiftType(), "Phải hiển thị Ca chiều khi ca chiều đã chốt");

        // Case 3: Ca sáng đang Open -> phải trả về Ca sáng
        Shift morningOpen = new Shift(1, today, Shift.ShiftType.MORNING,
                30, null, Shift.Status.OPEN, today.atTime(5, 30), null, null, null, null);
        ShiftDAO dao3 = new ShiftDAO() {
            @Override
            public List<Shift> findTodayShifts(LocalDate date) {
                return List.of(morningOpen, afternoonNotOpened);
            }
        };
        Shift active3 = dao3.findCurrentOrActiveShift(today);
        assertNotNull(active3);
        assertEquals(Shift.ShiftType.MORNING, active3.getShiftType(), "Phải trả về ca đang Open");

        // Case 4: Có ca hôm qua vẫn đang Open -> phải ưu tiên ca hôm qua
        Shift yesterdayMorningOpen = new Shift(99, today.minusDays(1), Shift.ShiftType.MORNING,
                30, null, Shift.Status.OPEN, today.minusDays(1).atTime(5, 30), null, null, null, null);
        ShiftDAO dao4 = new ShiftDAO() {
            @Override
            public Shift findUnclosedPastShift(LocalDate d) {
                return yesterdayMorningOpen;
            }

            @Override
            public List<Shift> findTodayShifts(LocalDate date) {
                return List.of(morningClosed, afternoonNotOpened);
            }
        };
        Shift active4 = dao4.findCurrentOrActiveShift(today);
        assertNotNull(active4);
        assertEquals(99, active4.getShiftId(), "Phải ưu tiên ca hôm qua chưa chốt");
    }

    @Test
    @DisplayName("Chặn mở ca ngày mới khi có ca hôm trước chưa chốt sổ")
    void testOpenShift_BlockedWhenPastShiftStillOpen() {
        LocalDate today = DateTimeUtil.today();
        Shift yesterdayShift = new Shift(10, today.minusDays(1), Shift.ShiftType.MORNING,
                30, null, Shift.Status.OPEN, today.minusDays(1).atTime(5, 30), null, null, null, null);
        Shift todayShift = new Shift(11, today, Shift.ShiftType.MORNING,
                null, null, Shift.Status.NOT_OPENED, null, null, null, null, null);

        fakeShiftDAO.setTodayShifts(List.of(yesterdayShift, todayShift));

        ServiceResult<Void> result = shiftService.openShift(10, 11);
        assertFalse(result.isSuccess(), "Không được mở ca mới khi ca hôm trước chưa chốt");
        assertTrue(result.getMessage().contains("chưa được chốt sổ"), "Thông báo phải nhắc nhở ca cũ chưa chốt sổ");
    }

    // ===== 2. Kiểm thử nghiệp vụ CHỐT CA (closeShift) =====

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

    // ===== 3. Kiểm thử nghiệp vụ HỦY ĐƠN (cancelOrder) & PHÂN QUYỀN =====

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

    @Test
    @DisplayName("BF-03 Main Flow: Chốt ca thành công khi tiền mặt thực tế khớp 100% với sổ sách (discrepancy = 0)")
    void testCloseShift_SuccessWhenDiscrepancyZero() {
        Shift shift = new Shift(3, LocalDate.now(), Shift.ShiftType.MORNING,
                30, null, Shift.Status.OPEN, LocalDateTime.now(), null, null, null, null);
        fakeShiftDAO.setShift(shift);
        fakeShiftDAO.clearPendingOrders();
        fakeShiftDAO.setMetrics(new ShiftMetrics(2500000, 3, 1000000, 2, 3500000, 5, 0));

        // Nhập tiền thực tế đúng 2.500.000, không cần lý do
        ServiceResult<Void> result = shiftService.closeShift(10, 3, 2500000, null);

        assertTrue(result.isSuccess(), "Chốt ca khớp tiền phải thành công");
        assertEquals(Shift.Status.CLOSED, shift.getStatus());
        assertEquals(2500000L, shift.getExpectedCash());
        assertEquals(2500000L, shift.getCountedCash());
        assertNull(shift.getVarianceReason());
    }

    @Test
    @DisplayName("BF-03 A2 Flow: Chốt ca thành công khi có chênh lệch tiền nhưng có lý do hợp lệ >= 5 ký tự (BR-46)")
    void testCloseShift_SuccessWhenVarianceReasonValid() {
        Shift shift = new Shift(4, LocalDate.now(), Shift.ShiftType.MORNING,
                30, null, Shift.Status.OPEN, LocalDateTime.now(), null, null, null, null);
        fakeShiftDAO.setShift(shift);
        fakeShiftDAO.clearPendingOrders();
        fakeShiftDAO.setMetrics(new ShiftMetrics(1500000, 2, 500000, 1, 2000000, 3, 0));

        // Nhập tiền đếm được 1.450.000 (thiếu 50.000) và lý do >= 5 ký tự
        ServiceResult<Void> result = shiftService.closeShift(10, 4, 1450000, "Khách thanh toán thiếu tiền lẻ đã ghi nợ");

        assertTrue(result.isSuccess(), "Chốt ca lệch tiền có lý do hợp lệ phải thành công");
        assertEquals(Shift.Status.CLOSED, shift.getStatus());
        assertEquals(1500000L, shift.getExpectedCash());
        assertEquals(1450000L, shift.getCountedCash());
        assertNotNull(shift.getVarianceReason());
        assertTrue(shift.getVarianceReason().contains("thiếu tiền lẻ"));
    }

    @Test
    @DisplayName("BR-46: Chốt ca thất bại khi số tiền đếm được là số âm")
    void testCloseShift_BlockedWhenNegativeCountedCash() {
        Shift shift = new Shift(5, LocalDate.now(), Shift.ShiftType.MORNING,
                30, null, Shift.Status.OPEN, LocalDateTime.now(), null, null, null, null);
        fakeShiftDAO.setShift(shift);

        ServiceResult<Void> result = shiftService.closeShift(10, 5, -50000, null);

        assertFalse(result.isSuccess(), "Tiền đếm được là số âm phải bị từ chối");
        assertEquals(Messages.MSG17_POSITIVE_NUMBER, result.getMessage());
    }

    @Test
    @DisplayName("BF-03 A1 Flow & CBR-77: Xác nhận chuyển khoản ngân hàng thành công trong ca đang mở")
    void testConfirmBankTransfer_Success() {
        Shift shift = new Shift(6, LocalDate.now(), Shift.ShiftType.MORNING,
                30, null, Shift.Status.OPEN, LocalDateTime.now(), null, null, null, null);
        ((TestableShiftService) shiftService).setOrderShift(shift);

        ServiceResult<Void> result = shiftService.confirmBankTransfer(10, 201);

        assertTrue(result.isSuccess(), "Xác nhận chuyển khoản cho đơn trong ca mở phải thành công");
    }

    @Test
    @DisplayName("CBR-77: Chặn xác nhận chuyển khoản khi ca đã chốt hoặc không ở trạng thái Open")
    void testConfirmBankTransfer_BlockedWhenShiftNotOpen() {
        Shift closedShift = new Shift(7, LocalDate.now(), Shift.ShiftType.MORNING,
                30, 30, Shift.Status.CLOSED, LocalDateTime.now().minusHours(4), LocalDateTime.now(), 1000000L, 1000000L, null);
        ((TestableShiftService) shiftService).setOrderShift(closedShift);

        ServiceResult<Void> result = shiftService.confirmBankTransfer(10, 202);

        assertFalse(result.isSuccess(), "Không thể xác nhận chuyển khoản cho ca đã chốt");
        assertTrue(result.getMessage().contains("đang mở"));
    }

    @Test
    @DisplayName("CBR-115: Chặn Lễ tân tự hủy đơn hàng thuộc hồ sơ khách của chính mình")
    void testCancelOrder_BlockedForStaffOwnProfile() {
        ((TestableShiftService) shiftService).setOwnCustomerProfile(true);

        ServiceResult<Void> result = shiftService.cancelOrder(10, 301, "Khách hàng muốn hủy đơn quầy");

        assertFalse(result.isSuccess(), "Không được phép tự hủy đơn của chính mình");
        assertEquals(Messages.MSG76_STAFF_OWN_CUSTOMER_PROFILE, result.getMessage());
    }

    // ===== Lớp Testable & Fake DAOs hỗ trợ Unit Test =====

    static class TestableShiftService extends ShiftService {
        private Shift orderShift;
        private boolean isOwnCustomerProfile = false;

        TestableShiftService(ShiftDAO shiftDAO, AccountDAO accountDAO, EmployeeDAO employeeDAO,
                             SelfServiceGuard selfServiceGuard, AuditLogService auditLogService) {
            super(shiftDAO, accountDAO, employeeDAO, selfServiceGuard, auditLogService);
        }

        void setOrderShift(Shift s) { this.orderShift = s; }
        void setOwnCustomerProfile(boolean own) { this.isOwnCustomerProfile = own; }

        @Override
        protected Connection getConnection() throws SQLException {
            return null; // Không cần kết nối SQL thật trong unit test
        }

        @Override
        protected Shift getShiftOfOrder(int orderId) {
            return orderShift != null ? orderShift : super.getShiftOfOrder(orderId);
        }

        @Override
        protected boolean isOrderOfStaff(int staffAccountId, int orderId) {
            return isOwnCustomerProfile;
        }
    }

    static class FakeShiftDAO extends ShiftDAO {
        private Shift currentShift;
        private final List<Shift> shiftsList = new ArrayList<>();
        private final List<ShiftTransactionItem> pendingOrders = new ArrayList<>();
        private ShiftMetrics metrics = new ShiftMetrics(0, 0, 0, 0, 0, 0, 0);
        private boolean gymClosed = false;

        void setShift(Shift s) {
            this.currentShift = s;
            if (s != null) {
                boolean found = false;
                for (int i = 0; i < shiftsList.size(); i++) {
                    if (shiftsList.get(i).getShiftId() == s.getShiftId()) {
                        shiftsList.set(i, s);
                        found = true;
                        break;
                    }
                }
                if (!found && shiftsList.isEmpty()) {
                    shiftsList.add(s);
                }
            }
        }

        void setTodayShifts(List<Shift> list) {
            this.shiftsList.clear();
            if (list != null) {
                this.shiftsList.addAll(list);
            }
        }

        void setGymClosed(boolean closed) {
            this.gymClosed = closed;
        }

        void addPendingOrder(ShiftTransactionItem item) { pendingOrders.add(item); }
        void clearPendingOrders() { pendingOrders.clear(); }
        void setMetrics(ShiftMetrics m) { this.metrics = m; }

        @Override
        public void ensureShiftsForDate(LocalDate date) {}

        @Override
        public void ensureShiftsForDate(Connection conn, LocalDate date) {}

        @Override
        public boolean isGymClosedOnDate(LocalDate date) {
            return gymClosed;
        }

        @Override
        public boolean isGymClosedOnDate(Connection conn, LocalDate date) {
            return gymClosed;
        }

        @Override
        public String findEmployeeFullName(Integer employeeId) {
            if (employeeId == null || employeeId <= 0) return null;
            return "Nhân viên " + employeeId;
        }

        @Override
        public int countTransactions(int shiftId, String search, String method, String status) {
            return 0;
        }

        @Override
        public List<ShiftTransactionItem> findTransactionsPage(int shiftId, String search, String method,
                                                               String status, int offset, int limit) {
            return new ArrayList<>();
        }

        @Override
        public Shift findById(int shiftId) {
            for (Shift s : shiftsList) {
                if (s.getShiftId() == shiftId) return s;
            }
            if (currentShift != null && currentShift.getShiftId() == shiftId) {
                return currentShift;
            }
            return currentShift;
        }

        @Override
        public Shift findCurrentOrActiveShift(LocalDate today) {
            return currentShift;
        }

        @Override
        public List<Shift> findTodayShifts(LocalDate date) {
            if (!shiftsList.isEmpty()) {
                return new ArrayList<>(shiftsList);
            }
            List<Shift> list = new ArrayList<>();
            if (currentShift != null) list.add(currentShift);
            return list;
        }

        @Override
        public Shift findOtherOpenShift(int exceptShiftId) {
            for (Shift s : shiftsList) {
                if (s.getShiftId() != exceptShiftId && s.isOpen()) return s;
            }
            if (currentShift != null && currentShift.getShiftId() != exceptShiftId && currentShift.isOpen()) {
                return currentShift;
            }
            return null;
        }

        @Override
        public Shift findUnclosedPastShift(LocalDate today) {
            for (Shift s : shiftsList) {
                if (s.isOpen() && s.getWorkDate().isBefore(today)) return s;
            }
            if (currentShift != null && currentShift.isOpen() && currentShift.getWorkDate().isBefore(today)) {
                return currentShift;
            }
            return null;
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
        public int openShift(Connection conn, int shiftId, int employeeId, LocalDateTime openedAt) {
            for (Shift s : shiftsList) {
                if (s.getShiftId() == shiftId) {
                    s.setStatus(Shift.Status.OPEN);
                    s.setOpenedAt(openedAt);
                    s.setOpenedByEmployeeId(employeeId);
                    return 1;
                }
            }
            if (currentShift != null && currentShift.getShiftId() == shiftId) {
                currentShift.setStatus(Shift.Status.OPEN);
                currentShift.setOpenedAt(openedAt);
                currentShift.setOpenedByEmployeeId(employeeId);
                return 1;
            }
            return 1;
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

        @Override
        public int confirmBankTransfer(Connection conn, int orderId, int employeeId, LocalDateTime completedAt) {
            return 1;
        }

        @Override
        public int cancelCounterOrder(Connection conn, int orderId, String reason, LocalDateTime now) {
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
