package com.gms.service;

import com.gms.constant.Messages;
import com.gms.constant.Role;
import com.gms.dao.AccountDAO;
import com.gms.dao.AuditLogDAO;
import com.gms.dao.CheckInDAO;
import com.gms.dao.CustomerDAO;
import com.gms.dao.EmployeeDAO;
import com.gms.dao.FreezeRecordDAO;
import com.gms.dto.FreezeCustomerView;
import com.gms.dto.FreezeRecordItemView;
import com.gms.dto.FreezeSessionImpactView;
import com.gms.dto.ServiceResult;
import com.gms.entity.Account;
import com.gms.entity.AuditLog;
import com.gms.entity.Customer;
import com.gms.entity.Employee;
import com.gms.entity.FreezeRecord;
import com.gms.entity.Subscription;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test kiểm thử toàn diện quy tắc nghiệp vụ của workflow Bảo lưu thẻ hội viên (BF-09, UC-18, JOB-01):
 * <ul>
 *   <li>BR-20: Gói bảo lưu -> hội viên chuyển sang Frozen.</li>
 *   <li>BR-21, BR-23: Từ chối bảo lưu gói không phải Membership hoặc đã hết hạn (MSG99).</li>
 *   <li>BR-24: Từ chối bảo lưu khi đã hết hạn mức bảo lưu (P-01).</li>
 *   <li>CBR-08, DBR-13: Kiểm soát ngày bắt đầu (>= ngày mai), ngày kết thúc (<= P-02 tháng và hạn gói) (MSG100).</li>
 *   <li>CBR-62, CBR-67, CBR-68: Đánh dấu buổi PT Scheduled thành Missed / MembershipFrozen, xử lý gói đôi.</li>
 *   <li>CBR-115: Chặn nhân viên tự bảo lưu hoặc hủy bảo lưu trên hồ sơ của chính mình.</li>
 *   <li>BR-26, DBR-51: Hủy bảo lưu sớm, mở lại gói, gia hạn thêm số ngày thực tế, không khôi phục buổi PT.</li>
 *   <li>JOB-01: Tự động mở lại gói tập khi hết kỳ bảo lưu.</li>
 * </ul>
 */
class FreezeServiceTest {

    private FakeFreezeRecordDAO fakeFreezeRecordDAO;
    private FakeCheckInDAO fakeCheckInDAO;
    private FakeAccountDAO fakeAccountDAO;
    private FakeEmployeeDAO fakeEmployeeDAO;
    private FakeCustomerDAO fakeCustomerDAO;
    private FakeSystemConfigService fakeSystemConfigService;
    private FakeSelfServiceGuard fakeSelfServiceGuard;
    private FakeAuditLogDAO fakeAuditLogDAO;
    private TestableFreezeService freezeService;

    private static final int STAFF_ACCOUNT_ID = 10;
    private static final int STAFF_PERSON_ID = 20;
    private static final int STAFF_EMPLOYEE_ID = 30;

    private static final int CUST_ID = 1;
    private static final int CUST_PERSON_ID = 101;
    private static final int SUB_ID = 1001;

    @BeforeEach
    void setUp() {
        fakeFreezeRecordDAO = new FakeFreezeRecordDAO();
        fakeCheckInDAO = new FakeCheckInDAO();
        fakeAccountDAO = new FakeAccountDAO();
        fakeEmployeeDAO = new FakeEmployeeDAO();
        fakeCustomerDAO = new FakeCustomerDAO();
        fakeSystemConfigService = new FakeSystemConfigService();
        fakeSelfServiceGuard = new FakeSelfServiceGuard();
        fakeAuditLogDAO = new FakeAuditLogDAO();

        freezeService = new TestableFreezeService(
                fakeFreezeRecordDAO, fakeCheckInDAO, fakeAccountDAO, fakeEmployeeDAO,
                fakeCustomerDAO, fakeSystemConfigService, fakeSelfServiceGuard, fakeAuditLogDAO);

        // Tạo tài khoản Lễ tân hợp lệ
        Account staffAcc = new Account(STAFF_ACCOUNT_ID, STAFF_PERSON_ID, "hash",
                Account.Status.ACTIVE, 0, null, null, LocalDateTime.now());
        fakeAccountDAO.addAccount(staffAcc);

        Employee staffEmp = new Employee(STAFF_EMPLOYEE_ID, STAFF_PERSON_ID, "NV-0030", Role.RECEPTIONIST,
                LocalDate.now().minusMonths(6), null, null, null, Employee.Status.WORKING, null, null);
        fakeEmployeeDAO.addEmployee(staffEmp);

        // Tạo hồ sơ khách hàng mẫu
        CheckInDAO.CustomerLookupInfo cust = new CheckInDAO.CustomerLookupInfo();
        cust.customerId = CUST_ID;
        cust.personId = CUST_PERSON_ID;
        cust.customerCode = "KH-1001";
        cust.fullName = "Nguyễn Văn An";
        cust.phone = "0901234501";
        cust.customerStatus = "Active";
        fakeCheckInDAO.addCustomer(cust);
    }

    // ===== 1. KIỂM THỬ TRA CỨU & ĐÁNH GIÁ ĐIỀU KIỆN (Customer Lookup) =====

    @Test
    @DisplayName("Tra cứu: Báo lỗi khi từ khóa trống hoặc không tìm thấy khách")
    void testLookupCustomer_NotFound() {
        ServiceResult<FreezeCustomerView> res1 = freezeService.lookupCustomerForFreeze("", STAFF_ACCOUNT_ID);
        assertFalse(res1.isSuccess());

        ServiceResult<FreezeCustomerView> res2 = freezeService.lookupCustomerForFreeze("KH-9999", STAFF_ACCOUNT_ID);
        assertFalse(res2.isSuccess());
        assertTrue(res2.getMessage().contains("Không tìm thấy"));
    }

    @Test
    @DisplayName("CBR-16: Chặn bảo lưu khi hồ sơ đang có yêu cầu xóa chờ duyệt")
    void testLookupCustomer_PendingDeletion() {
        fakeCheckInDAO.setPendingDeletion(CUST_ID, true);

        ServiceResult<FreezeCustomerView> res = freezeService.lookupCustomerForFreeze("KH-1001", STAFF_ACCOUNT_ID);
        assertTrue(res.isSuccess());
        FreezeCustomerView view = res.getData();
        assertFalse(view.isEligibleToFreeze());
        assertEquals(Messages.MSG101_PROFILE_PENDING_DELETION, view.getIneligibilityReason());
    }

    @Test
    @DisplayName("CBR-115: Chặn nhân viên tự bảo lưu hồ sơ của chính mình")
    void testLookupCustomer_StaffOwnProfile() {
        fakeSelfServiceGuard.setOwnProfile(STAFF_ACCOUNT_ID, CUST_ID, true);

        // Gán gói tập hợp lệ
        Subscription sub = createSubscription(SUB_ID, CUST_ID, Subscription.Type.MEMBERSHIP,
                Subscription.Status.ACTIVE, LocalDate.now().minusMonths(1), LocalDate.now().plusMonths(5), 0, 1);
        fakeFreezeRecordDAO.setActiveOrFrozenSub(CUST_ID, sub);

        ServiceResult<FreezeCustomerView> res = freezeService.lookupCustomerForFreeze("KH-1001", STAFF_ACCOUNT_ID);
        assertTrue(res.isSuccess());
        FreezeCustomerView view = res.getData();
        assertTrue(view.isOwnProfile());
        assertFalse(view.isEligibleToFreeze());
        assertTrue(view.getIneligibilityReason().contains("CBR-115"));
    }

    @Test
    @DisplayName("BR-21, MSG99: Từ chối bảo lưu khi hội viên không có gói Membership nào")
    void testLookupCustomer_NoMembership() {
        // Không thêm Subscription nào
        ServiceResult<FreezeCustomerView> res = freezeService.lookupCustomerForFreeze("KH-1001", STAFF_ACCOUNT_ID);
        assertTrue(res.isSuccess());
        FreezeCustomerView view = res.getData();
        assertFalse(view.isHasMembership());
        assertFalse(view.isEligibleToFreeze());
        assertTrue(view.getIneligibilityReason().contains("Không thể bảo lưu gói này"));
    }

    @Test
    @DisplayName("BR-23, MSG99: Từ chối bảo lưu khi gói Membership đã hết hạn")
    void testLookupCustomer_ExpiredMembership() {
        Subscription sub = createSubscription(SUB_ID, CUST_ID, Subscription.Type.MEMBERSHIP,
                Subscription.Status.ACTIVE, LocalDate.now().minusMonths(6), LocalDate.now().minusDays(1), 0, 1);
        fakeFreezeRecordDAO.setActiveOrFrozenSub(CUST_ID, sub);

        ServiceResult<FreezeCustomerView> res = freezeService.lookupCustomerForFreeze("KH-1001", STAFF_ACCOUNT_ID);
        assertTrue(res.isSuccess());
        FreezeCustomerView view = res.getData();
        assertTrue(view.isHasMembership());
        assertFalse(view.isEligibleToFreeze());
        assertTrue(view.getIneligibilityReason().contains("hết hạn"));
    }

    @Test
    @DisplayName("BR-24, MSG99: Từ chối bảo lưu khi gói đã dùng hết hạn mức bảo lưu (FreezeUsed >= MaxFreezes)")
    void testLookupCustomer_FreezeAllowanceExhausted() {
        Subscription sub = createSubscription(SUB_ID, CUST_ID, Subscription.Type.MEMBERSHIP,
                Subscription.Status.ACTIVE, LocalDate.now().minusMonths(1), LocalDate.now().plusMonths(5), 1, 1);
        fakeFreezeRecordDAO.setActiveOrFrozenSub(CUST_ID, sub);

        ServiceResult<FreezeCustomerView> res = freezeService.lookupCustomerForFreeze("KH-1001", STAFF_ACCOUNT_ID);
        assertTrue(res.isSuccess());
        FreezeCustomerView view = res.getData();
        assertFalse(view.isEligibleToFreeze());
        assertTrue(view.getIneligibilityReason().contains("hết hạn mức bảo lưu"));
    }

    @Test
    @DisplayName("Đánh giá gói đang bảo lưu: hiển thị thông tin đợt bảo lưu và dự tính hủy sớm")
    void testLookupCustomer_CurrentlyFrozen() {
        Subscription sub = createSubscription(SUB_ID, CUST_ID, Subscription.Type.MEMBERSHIP,
                Subscription.Status.FROZEN, LocalDate.now().minusMonths(2), LocalDate.now().plusMonths(4), 1, 1);
        fakeFreezeRecordDAO.setActiveOrFrozenSub(CUST_ID, sub);

        LocalDate freezeStart = LocalDate.now().minusDays(5);
        LocalDate freezePlannedEnd = LocalDate.now().plusDays(25);
        FreezeRecord freeze = new FreezeRecord(1, SUB_ID, STAFF_EMPLOYEE_ID, freezeStart, freezePlannedEnd,
                null, "Đi công tác nước ngoài", LocalDateTime.now().minusDays(6));
        fakeFreezeRecordDAO.setOpenFreeze(SUB_ID, freeze);

        ServiceResult<FreezeCustomerView> res = freezeService.lookupCustomerForFreeze("KH-1001", STAFF_ACCOUNT_ID);
        assertTrue(res.isSuccess());
        FreezeCustomerView view = res.getData();
        assertTrue(view.isCurrentlyFrozen());
        assertFalse(view.isEligibleToFreeze());
        assertEquals(freezeStart, view.getCurrentFreezeStartDate());
        assertEquals(freezePlannedEnd, view.getCurrentFreezePlannedEndDate());
        // Số ngày đã bảo lưu thực tế: từ freezeStart đến hôm nay = 5 + 1 = 6 ngày
        assertEquals(6, view.getDaysFrozenIfCancelledToday());
        assertEquals(sub.getEndDate().plusDays(6), view.getProjectedNewEndDateIfCancelledToday());
        assertTrue(view.isCanCancelEarly());
    }

    @Test
    @DisplayName("Đủ điều kiện bảo lưu: tính toán chính xác minStartDate và maxEndDate theo P-02")
    void testLookupCustomer_Eligible() {
        LocalDate today = LocalDate.now();
        Subscription sub = createSubscription(SUB_ID, CUST_ID, Subscription.Type.MEMBERSHIP,
                Subscription.Status.ACTIVE, today.minusMonths(1), today.plusMonths(6), 0, 1);
        fakeFreezeRecordDAO.setActiveOrFrozenSub(CUST_ID, sub);
        fakeSystemConfigService.setInt(SystemConfigService.MAX_FREEZE_MONTHS, 1);

        ServiceResult<FreezeCustomerView> res = freezeService.lookupCustomerForFreeze("KH-1001", STAFF_ACCOUNT_ID);
        assertTrue(res.isSuccess());
        FreezeCustomerView view = res.getData();
        assertTrue(view.isEligibleToFreeze());
        assertEquals(today.plusDays(1), view.getMinStartDate());

        // Ngày kết thúc tối đa: min(startDate + 1 tháng - 1 ngày, packageEndDate)
        LocalDate expectedMaxEnd = today.plusDays(1).plusMonths(1).minusDays(1);
        assertEquals(expectedMaxEnd, view.getMaxEndDate());
    }

    // ===== 2. KIỂM THỬ THỰC HIỆN TẠO MỚI BẢO LƯU (applyFreeze) =====

    @Test
    @DisplayName("applyFreeze: Từ chối khi lý do < 5 ký tự hoặc thiếu ngày")
    void testApplyFreeze_InvalidInput() {
        LocalDate start = LocalDate.now().plusDays(1);
        LocalDate end = LocalDate.now().plusDays(20);

        ServiceResult<Void> r1 = freezeService.applyFreeze(STAFF_ACCOUNT_ID, SUB_ID, start, end, "abc");
        assertFalse(r1.isSuccess());
        assertTrue(r1.getMessage().contains("5 ký tự"));

        ServiceResult<Void> r2 = freezeService.applyFreeze(STAFF_ACCOUNT_ID, SUB_ID, null, end, "Công tác xa");
        assertFalse(r2.isSuccess());
    }

    @Test
    @DisplayName("DBR-13: Chặn ngày bắt đầu bảo lưu trong quá khứ hoặc hôm nay")
    void testApplyFreeze_StartDateTooEarly() {
        LocalDate today = LocalDate.now();
        LocalDate end = today.plusDays(20);

        ServiceResult<Void> r1 = freezeService.applyFreeze(STAFF_ACCOUNT_ID, SUB_ID, today, end, "Khách có việc đột xuất");
        assertFalse(r1.isSuccess());
        assertTrue(r1.getMessage().contains("sớm nhất từ ngày mai"));

        ServiceResult<Void> r2 = freezeService.applyFreeze(STAFF_ACCOUNT_ID, SUB_ID, today.minusDays(1), end, "Khách có việc đột xuất");
        assertFalse(r2.isSuccess());
    }

    @Test
    @DisplayName("DBR-13: Chặn ngày kết thúc trước ngày bắt đầu")
    void testApplyFreeze_EndDateBeforeStartDate() {
        LocalDate start = LocalDate.now().plusDays(5);
        LocalDate end = LocalDate.now().plusDays(4);

        ServiceResult<Void> res = freezeService.applyFreeze(STAFF_ACCOUNT_ID, SUB_ID, start, end, "Khách có việc đột xuất");
        assertFalse(res.isSuccess());
        assertTrue(res.getMessage().contains("không được trước"));
    }

    @Test
    @DisplayName("CBR-115: Chặn nhân viên tự bảo lưu gói tập của chính mình")
    void testApplyFreeze_StaffOwnProfile() {
        Subscription sub = createSubscription(SUB_ID, CUST_ID, Subscription.Type.MEMBERSHIP,
                Subscription.Status.ACTIVE, LocalDate.now().minusMonths(1), LocalDate.now().plusMonths(6), 0, 1);
        fakeFreezeRecordDAO.setSubscription(sub);
        fakeSelfServiceGuard.setOwnProfile(STAFF_ACCOUNT_ID, CUST_ID, true);

        LocalDate start = LocalDate.now().plusDays(1);
        LocalDate end = LocalDate.now().plusDays(20);

        ServiceResult<Void> res = freezeService.applyFreeze(STAFF_ACCOUNT_ID, SUB_ID, start, end, "Công tác nước ngoài");
        assertFalse(res.isSuccess());
        assertTrue(res.getMessage().contains("CBR-115"));
    }

    @Test
    @DisplayName("CBR-08, MSG100: Từ chối bảo lưu vượt quá thời lượng P-02 hoặc quá hạn gói")
    void testApplyFreeze_DurationExceeded() {
        LocalDate today = LocalDate.now();
        // Gói chỉ còn 15 ngày
        Subscription sub = createSubscription(SUB_ID, CUST_ID, Subscription.Type.MEMBERSHIP,
                Subscription.Status.ACTIVE, today.minusMonths(2), today.plusDays(15), 0, 1);
        fakeFreezeRecordDAO.setSubscription(sub);
        fakeSystemConfigService.setInt(SystemConfigService.MAX_FREEZE_MONTHS, 1);

        LocalDate start = today.plusDays(1);
        LocalDate end = today.plusDays(20); // Vượt quá hạn gói 15 ngày

        ServiceResult<Void> res = freezeService.applyFreeze(STAFF_ACCOUNT_ID, SUB_ID, start, end, "Khách bị chấn thương");
        assertFalse(res.isSuccess());
        assertTrue(res.getMessage().contains("Bảo lưu tối đa"));
    }

    @Test
    @DisplayName("Tạo bảo lưu thành công: đổi trạng thái gói & khách sang Frozen, chốt Missed buổi PT (CBR-62)")
    void testApplyFreeze_Success() {
        LocalDate today = LocalDate.now();
        Subscription sub = createSubscription(SUB_ID, CUST_ID, Subscription.Type.MEMBERSHIP,
                Subscription.Status.ACTIVE, today.minusMonths(1), today.plusMonths(5), 0, 1);
        fakeFreezeRecordDAO.setSubscription(sub);
        fakeSystemConfigService.setInt(SystemConfigService.MAX_FREEZE_MONTHS, 1);

        LocalDate start = today.plusDays(1);
        LocalDate end = start.plusDays(20);

        // Tạo 2 buổi PT của khách trong kỳ bảo lưu: 1 buổi đơn, 1 buổi đôi có partner còn thẻ
        FreezeSessionImpactView sessionSingle = new FreezeSessionImpactView(
                501, start.plusDays(2), LocalTime.of(8, 0), LocalTime.of(9, 30),
                "David", "PTSingle", null, true, "Sẽ bị Missed");
        FreezeSessionImpactView sessionCouple = new FreezeSessionImpactView(
                502, start.plusDays(4), LocalTime.of(10, 0), LocalTime.of(11, 30),
                "David", "PTCouple", "Trần Thị Bích", false, "Giữ lịch vì partner có thẻ");

        fakeFreezeRecordDAO.setScheduledSessions(List.of(sessionSingle, sessionCouple));

        ServiceResult<Void> res = freezeService.applyFreeze(
                STAFF_ACCOUNT_ID, SUB_ID, start, end, "Khách bị chấn thương thể thao");

        assertTrue(res.isSuccess());
        assertTrue(res.getMessage().contains("thành công"));

        // Kiểm tra Subscription -> Status = Frozen, FreezeUsed = 1
        Subscription updatedSub = fakeFreezeRecordDAO.findSubscription(SUB_ID);
        assertEquals(Subscription.Status.FROZEN, updatedSub.getStatus());
        assertEquals(1, updatedSub.getFreezeUsed());

        // Kiểm tra Customers -> Status = Frozen (BR-20)
        assertEquals("Frozen", fakeFreezeRecordDAO.getCustomerStatus(CUST_ID));

        // Kiểm tra FreezeRecord đã được chèn vào DB
        FreezeRecord record = fakeFreezeRecordDAO.getLastInsertedRecord();
        assertNotNull(record);
        assertEquals(SUB_ID, record.getSubscriptionId());
        assertEquals(STAFF_EMPLOYEE_ID, record.getHandledByEmployeeId());
        assertEquals(start, record.getStartDate());
        assertEquals(end, record.getPlannedEndDate());
        assertNull(record.getActualEndDate());

        // Kiểm tra CBR-62: Đúng buổi single bị chốt Missed
        List<Integer> missedIds = fakeFreezeRecordDAO.getMissedSessionIds();
        assertEquals(1, missedIds.size());
        assertEquals(501, missedIds.get(0));

        // Kiểm tra AuditLog được ghi lại
        AuditLog log = fakeAuditLogDAO.getLastLog();
        assertNotNull(log);
        assertEquals("FREEZE_MEMBERSHIP", log.getAction());
        assertEquals("Frozen", log.getNewValue());
    }

    @Test
    @DisplayName("Transaction: applyFreeze sử dụng kết nối transactional khi truy vấn buổi PT chịu ảnh hưởng")
    void testApplyFreeze_TransactionalSessionLookup() {
        LocalDate today = LocalDate.now();
        Subscription sub = createSubscription(SUB_ID, CUST_ID, Subscription.Type.MEMBERSHIP,
                Subscription.Status.ACTIVE, today.minusMonths(1), today.plusMonths(6), 0, 1);
        fakeFreezeRecordDAO.setSubscription(sub);

        LocalDate start = today.plusDays(1);
        LocalDate end = today.plusDays(10);

        FreezeSessionImpactView session1 = new FreezeSessionImpactView();
        session1.setSessionId(701);
        session1.setWillBeMissed(true);

        FreezeSessionImpactView session2 = new FreezeSessionImpactView();
        session2.setSessionId(702);
        session2.setWillBeMissed(true);

        fakeFreezeRecordDAO.setScheduledSessions(List.of(session1, session2));

        ServiceResult<Void> res = freezeService.applyFreeze(
                STAFF_ACCOUNT_ID, SUB_ID, start, end, "Bảo lưu dài ngày vì phẫu thuật");

        assertTrue(res.isSuccess());
        assertTrue(res.getMessage().contains("2 buổi PT"));
        assertEquals(List.of(701, 702), fakeFreezeRecordDAO.getMissedSessionIds());
    }

    @Test
    @DisplayName("applyFreeze: Lý do dài tới 500 ký tự được xử lý an toàn, không làm tràn cột AuditLogs.Reason")
    void testApplyFreeze_LongReasonCapsSafely() {
        LocalDate today = LocalDate.now();
        Subscription sub = createSubscription(SUB_ID, CUST_ID, Subscription.Type.MEMBERSHIP,
                Subscription.Status.ACTIVE, today.minusMonths(1), today.plusMonths(5), 0, 1);
        fakeFreezeRecordDAO.setSubscription(sub);
        fakeSystemConfigService.setInt(SystemConfigService.MAX_FREEZE_MONTHS, 1);

        LocalDate start = today.plusDays(1);
        LocalDate end = start.plusDays(20);

        String longReason = "A".repeat(550); // 550 ký tự

        ServiceResult<Void> res = freezeService.applyFreeze(
                STAFF_ACCOUNT_ID, SUB_ID, start, end, longReason);

        assertTrue(res.isSuccess(), "Lỗi applyFreeze: " + res.getMessage());
        FreezeRecord record = fakeFreezeRecordDAO.getLastInsertedRecord();
        assertNotNull(record);
        assertEquals(500, record.getReason().length(), "Lý do trong FreezeRecords bị giới hạn tối đa 500 ký tự");

        AuditLog log = fakeAuditLogDAO.getLastLog();
        assertNotNull(log);
        assertTrue(log.getReason().length() <= 500, "Reason trong AuditLog không được vượt quá 500 ký tự");
    }

    // ===== 3. KIỂM THỬ HỦY BẢO LƯU SỚM (cancelFreezeEarly) =====

    @Test
    @DisplayName("cancelFreezeEarly: Hủy sớm thành công, mở lại gói, gia hạn đúng ngày thực tế (BR-26, DBR-51)")
    void testCancelFreezeEarly_Success() {
        LocalDate today = LocalDate.now();
        LocalDate originalEnd = today.plusMonths(3);

        Subscription sub = createSubscription(SUB_ID, CUST_ID, Subscription.Type.MEMBERSHIP,
                Subscription.Status.FROZEN, today.minusMonths(2), originalEnd, 1, 1);
        fakeFreezeRecordDAO.setSubscription(sub);

        // Đợt bảo lưu đã bắt đầu từ 10 ngày trước
        LocalDate freezeStart = today.minusDays(9); // Tính cả hôm nay là 10 ngày
        LocalDate freezePlannedEnd = today.plusDays(20);
        FreezeRecord freeze = new FreezeRecord(1, SUB_ID, STAFF_EMPLOYEE_ID, freezeStart, freezePlannedEnd,
                null, "Đi công tác", LocalDateTime.now().minusDays(10));
        fakeFreezeRecordDAO.setFreezeRecord(freeze);

        ServiceResult<Void> res = freezeService.cancelFreezeEarly(STAFF_ACCOUNT_ID, 1);
        assertTrue(res.isSuccess());
        assertTrue(res.getMessage().contains("hủy bảo lưu sớm thành công"));

        // Kiểm tra FreezeRecord -> ActualEndDate = today
        FreezeRecord updatedFreeze = fakeFreezeRecordDAO.findFreezeRecord(1);
        assertEquals(today, updatedFreeze.getActualEndDate());

        // Kiểm tra Subscription -> Status = Active, EndDate được gia hạn thêm 10 ngày (DBR-51)
        Subscription updatedSub = fakeFreezeRecordDAO.findSubscription(SUB_ID);
        assertEquals(Subscription.Status.ACTIVE, updatedSub.getStatus());
        assertEquals(originalEnd.plusDays(10), updatedSub.getEndDate());
        assertEquals(1, updatedSub.getFreezeUsed(), "Lượt bảo lưu vẫn bị tính đã dùng (BR-26)");

        // Kiểm tra Customer -> Status = Active (BR-26)
        assertEquals("Active", fakeFreezeRecordDAO.getCustomerStatus(CUST_ID));

        // Kiểm tra AuditLog
        AuditLog log = fakeAuditLogDAO.getLastLog();
        assertNotNull(log);
        assertEquals("CANCEL_FREEZE_EARLY", log.getAction());
    }

    // ===== 4. KIỂM THỬ TỰ ĐỘNG MỞ LẠI HẾT HẠN (JOB-01 Auto-Resume) =====

    @Test
    @DisplayName("JOB-01: Tự động mở lại gói tập đã hết hạn bảo lưu và cộng số ngày dự kiến")
    void testResumeExpiredFreezes_JOB01() {
        LocalDate today = LocalDate.now();
        LocalDate originalEnd = today.plusMonths(2);

        Subscription sub = createSubscription(SUB_ID, CUST_ID, Subscription.Type.MEMBERSHIP,
                Subscription.Status.FROZEN, today.minusMonths(3), originalEnd, 1, 1);
        fakeFreezeRecordDAO.setSubscription(sub);

        // Đợt bảo lưu từ 30 ngày trước đến hôm qua (đã hết hạn)
        LocalDate freezeStart = today.minusDays(30);
        LocalDate freezePlannedEnd = today.minusDays(1); // 30 ngày bảo lưu
        FreezeRecord freeze = new FreezeRecord(1, SUB_ID, STAFF_EMPLOYEE_ID, freezeStart, freezePlannedEnd,
                null, "Nghỉ dưỡng", LocalDateTime.now().minusDays(31));
        fakeFreezeRecordDAO.addExpiredFreeze(freeze);

        int count = freezeService.resumeExpiredFreezes(today);
        assertEquals(1, count);

        // Kiểm tra gói đã được mở lại và gia hạn 30 ngày
        Subscription updatedSub = fakeFreezeRecordDAO.findSubscription(SUB_ID);
        assertEquals(Subscription.Status.ACTIVE, updatedSub.getStatus());
        assertEquals(originalEnd.plusDays(30), updatedSub.getEndDate());

        // Kiểm tra khách về Active
        assertEquals("Active", fakeFreezeRecordDAO.getCustomerStatus(CUST_ID));

        // Kiểm tra ActualEndDate = PlannedEndDate
        assertEquals(freezePlannedEnd, freeze.getActualEndDate());
    }

    // ===== Tiện ích tạo đối tượng mẫu =====

    private Subscription createSubscription(int id, int customerId, Subscription.Type type,
                                            Subscription.Status status, LocalDate start, LocalDate end,
                                            int freezeUsed, int maxFreezes) {
        return new Subscription(id, 100, customerId, type, null, start, end,
                null, null, 0, null, freezeUsed, maxFreezes, status);
    }

    // =========================================================================
    // LỚP TESTABLE & FAKE DAOs
    // =========================================================================

    static class TestableFreezeService extends FreezeService {
        TestableFreezeService(FreezeRecordDAO freezeRecordDAO, CheckInDAO checkInDAO,
                              AccountDAO accountDAO, EmployeeDAO employeeDAO,
                              CustomerDAO customerDAO, SystemConfigService systemConfigService,
                              SelfServiceGuard selfServiceGuard, AuditLogDAO auditLogDAO) {
            super(freezeRecordDAO, checkInDAO, accountDAO, employeeDAO,
                    customerDAO, systemConfigService, selfServiceGuard, auditLogDAO);
        }

        @Override
        protected Connection openConnection() {
            // Dùng Dynamic Proxy tạo FakeConnection cho các thao tác transaction trong Unit Test
            return (Connection) java.lang.reflect.Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class},
                    (proxy, method, args) -> {
                        String name = method.getName();
                        if ("isClosed".equals(name)) return false;
                        if ("setAutoCommit".equals(name) || "commit".equals(name)
                                || "rollback".equals(name) || "close".equals(name)) {
                            return null;
                        }
                        return null;
                    });
        }
    }

    static class FakeFreezeRecordDAO extends FreezeRecordDAO {
        private final Map<Integer, Subscription> subs = new HashMap<>();
        private final Map<Integer, FreezeRecord> freezeRecords = new HashMap<>();
        private final Map<Integer, String> customerStatuses = new HashMap<>();
        private final List<FreezeRecord> expiredFreezes = new ArrayList<>();
        private final List<Integer> missedSessionIds = new ArrayList<>();
        private List<FreezeSessionImpactView> scheduledSessions = new ArrayList<>();
        private FreezeRecord lastInserted;

        @Override
        public String findPackageNameByOrderId(int orderId) {
            return "Gói Silver 6 Tháng";
        }

        @Override
        public String findEmployeeDisplay(int employeeId) {
            return "NV-0030 - Lê Lễ Tân";
        }

        void setSubscription(Subscription s) {
            subs.put(s.getSubscriptionId(), s);
        }

        void setActiveOrFrozenSub(int customerId, Subscription s) {
            subs.put(s.getSubscriptionId(), s);
        }

        void setOpenFreeze(int subId, FreezeRecord f) {
            freezeRecords.put(f.getFreezeRecordId(), f);
        }

        void setFreezeRecord(FreezeRecord f) {
            freezeRecords.put(f.getFreezeRecordId(), f);
        }

        void addExpiredFreeze(FreezeRecord f) {
            expiredFreezes.add(f);
            freezeRecords.put(f.getFreezeRecordId(), f);
        }

        void setScheduledSessions(List<FreezeSessionImpactView> list) {
            this.scheduledSessions = list;
        }

        Subscription findSubscription(int id) {
            return subs.get(id);
        }

        FreezeRecord findFreezeRecord(int id) {
            return freezeRecords.get(id);
        }

        FreezeRecord getLastInsertedRecord() {
            return lastInserted;
        }

        String getCustomerStatus(int customerId) {
            return customerStatuses.get(customerId);
        }

        List<Integer> getMissedSessionIds() {
            return missedSessionIds;
        }

        @Override
        public int insert(Connection conn, FreezeRecord record) {
            record.setFreezeRecordId(999);
            this.lastInserted = record;
            freezeRecords.put(999, record);
            return 999;
        }

        @Override
        public FreezeRecord findById(Connection conn, int freezeRecordId) {
            return freezeRecords.get(freezeRecordId);
        }

        @Override
        public FreezeRecord findById(int freezeRecordId) {
            return freezeRecords.get(freezeRecordId);
        }

        @Override
        public FreezeRecord findOpenFreezeBySubscriptionId(Connection conn, int subscriptionId) {
            for (FreezeRecord r : freezeRecords.values()) {
                if (r.getSubscriptionId() == subscriptionId && r.getActualEndDate() == null) {
                    return r;
                }
            }
            return null;
        }

        @Override
        public FreezeRecord findOpenFreezeBySubscriptionId(int subscriptionId) {
            return findOpenFreezeBySubscriptionId((Connection) null, subscriptionId);
        }

        @Override
        public boolean updateActualEndDate(Connection conn, int freezeRecordId, LocalDate actualEndDate) {
            FreezeRecord r = freezeRecords.get(freezeRecordId);
            if (r != null) {
                r.setActualEndDate(actualEndDate);
                return true;
            }
            return false;
        }

        @Override
        public List<FreezeRecord> findExpiredOpenFreezes(LocalDate today) {
            return expiredFreezes;
        }

        @Override
        public Subscription findMembershipSubscription(Connection conn, int subscriptionId) {
            return subs.get(subscriptionId);
        }

        @Override
        public Subscription findActiveOrFrozenMembershipByCustomerId(Connection conn, int customerId) {
            for (Subscription s : subs.values()) {
                if (s.getCustomerId() == customerId && s.getSubscriptionType() == Subscription.Type.MEMBERSHIP) {
                    return s;
                }
            }
            return null;
        }

        @Override
        public Subscription findActiveOrFrozenMembershipByCustomerId(int customerId) {
            return findActiveOrFrozenMembershipByCustomerId((Connection) null, customerId);
        }

        @Override
        public boolean updateSubscriptionStatusAndFreezeUsed(Connection conn, int subscriptionId, String status, int newFreezeUsed) {
            Subscription s = subs.get(subscriptionId);
            if (s != null) {
                s.setStatus(Subscription.Status.fromDbValue(status));
                s.setFreezeUsed(newFreezeUsed);
                return true;
            }
            return false;
        }

        @Override
        public boolean updateSubscriptionResume(Connection conn, int subscriptionId, String status, LocalDate newEndDate) {
            Subscription s = subs.get(subscriptionId);
            if (s != null) {
                s.setStatus(Subscription.Status.fromDbValue(status));
                s.setEndDate(newEndDate);
                return true;
            }
            return false;
        }

        @Override
        public boolean updateCustomerStatus(Connection conn, int customerId, String status) {
            customerStatuses.put(customerId, status);
            return true;
        }

        @Override
        public List<FreezeSessionImpactView> findScheduledSessionsInPeriod(Connection conn, int customerId, LocalDate startDate, LocalDate plannedEndDate) {
            return scheduledSessions;
        }

        @Override
        public List<FreezeSessionImpactView> findScheduledSessionsInPeriod(int customerId, LocalDate startDate, LocalDate plannedEndDate) {
            return scheduledSessions;
        }

        @Override
        public void markSessionsMissedDueToFreeze(Connection conn, List<Integer> sessionIds) {
            missedSessionIds.addAll(sessionIds);
        }

        @Override
        public List<FreezeRecordItemView> findRecentFreezes(String keyword, int limit) {
            return List.of();
        }

        @Override
        public List<FreezeRecordItemView> findRecentFreezes(int limit) {
            return List.of();
        }

        @Override
        public List<FreezeRecordItemView> findFreezesByCustomerId(int customerId) {
            return List.of();
        }
    }

    static class FakeCheckInDAO extends CheckInDAO {
        private final Map<String, CustomerLookupInfo> customers = new HashMap<>();
        private final Map<Integer, Boolean> pendingDeletions = new HashMap<>();

        void addCustomer(CustomerLookupInfo c) {
            customers.put(c.customerCode.toUpperCase(), c);
            customers.put(c.phone, c);
        }

        void setPendingDeletion(int customerId, boolean pending) {
            pendingDeletions.put(customerId, pending);
        }

        @Override
        public CustomerLookupInfo findCustomerByKeyword(String keyword) {
            return customers.get(keyword.trim().toUpperCase());
        }

        @Override
        public boolean hasPendingDeletionRequest(int customerId) {
            return Boolean.TRUE.equals(pendingDeletions.get(customerId));
        }
    }

    static class FakeAccountDAO extends AccountDAO {
        private final Map<Integer, Account> accounts = new HashMap<>();

        void addAccount(Account a) {
            accounts.put(a.getAccountId(), a);
        }

        @Override
        public Account findById(int accountId) {
            return accounts.get(accountId);
        }
    }

    static class FakeEmployeeDAO extends EmployeeDAO {
        private final Map<Integer, Employee> employees = new HashMap<>();

        void addEmployee(Employee e) {
            employees.put(e.getPersonId(), e);
        }

        @Override
        public Employee findByPersonId(int personId) {
            return employees.get(personId);
        }
    }

    static class FakeCustomerDAO extends CustomerDAO {
    }

    static class FakeSystemConfigService extends SystemConfigService {
        private final Map<String, Integer> intConfigs = new HashMap<>();

        void setInt(String key, int val) {
            intConfigs.put(key, val);
        }

        @Override
        public int getInt(String configKey) {
            return intConfigs.getOrDefault(configKey, 1);
        }
    }

    static class FakeSelfServiceGuard extends SelfServiceGuard {
        private final Map<String, Boolean> ownProfiles = new HashMap<>();

        void setOwnProfile(int accountId, int customerId, boolean own) {
            ownProfiles.put(accountId + "_" + customerId, own);
        }

        @Override
        public boolean isOwnCustomerProfile(int staffAccountId, int customerId) {
            return Boolean.TRUE.equals(ownProfiles.get(staffAccountId + "_" + customerId));
        }
    }

    static class FakeAuditLogDAO extends AuditLogDAO {
        private AuditLog lastLog;

        AuditLog getLastLog() {
            return lastLog;
        }

        @Override
        public boolean insert(Connection conn, AuditLog auditLog) {
            this.lastLog = auditLog;
            return true;
        }
    }
}
