package com.gms.service;

import com.gms.dao.AccountDAO;
import com.gms.dao.CheckInDAO;
import com.gms.dao.EmployeeDAO;
import com.gms.dto.CheckInLogItem;
import com.gms.dto.CheckInLookupResult;
import com.gms.dto.CheckInLookupResult.Decision;
import com.gms.dto.ServiceResult;
import com.gms.entity.Account;
import com.gms.entity.CheckIn;
import com.gms.entity.Employee;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test kiểm thử toàn diện các rule nghiệp vụ màn hình Check-in Lễ tân (UC-19).
 * - CBR-37: Avatar không được dùng đối chiếu check-in
 * - CBR-38: Ảnh check-in thuộc hồ sơ hội viên do Lễ tân quản lý
 * - DBR-47, DBR-48, CBR-58: Gói PT không cho vào phòng tập tự do
 * - DBR-34: Trạng thái Customer (Active, Frozen, Expired, New)
 * - DBR-51, UC-18: Gói bảo lưu bị chặn vào tập
 * - UC-17: Khách đang chờ duyệt xoá bị chặn vào tập
 * - Vé ngày (DayPass) chỉ vào được 1 lượt duy nhất
 */
class CheckInServiceTest {

    private FakeCheckInDAO fakeCheckInDAO;
    private FakeAccountDAO fakeAccountDAO;
    private FakeEmployeeDAO fakeEmployeeDAO;
    private CheckInService checkInService;

    @BeforeEach
    void setUp() {
        fakeCheckInDAO = new FakeCheckInDAO();
        fakeAccountDAO = new FakeAccountDAO();
        fakeEmployeeDAO = new FakeEmployeeDAO();
        checkInService = new CheckInService(fakeCheckInDAO, fakeAccountDAO, fakeEmployeeDAO);

        setupTestData();
    }

    private void setupTestData() {
        // KH-1001: Active, có ảnh check-in, gói Membership 3 Tháng còn hạn
        CheckInDAO.CustomerLookupInfo c1001 = new CheckInDAO.CustomerLookupInfo();
        c1001.customerId = 1;
        c1001.personId = 1;
        c1001.customerCode = "KH-1001";
        c1001.fullName = "Trần Bảo Ngọc";
        c1001.phone = "0901234501";
        c1001.checkInPhoto = "/uploads/checkin/kh-1001.jpg";
        c1001.customerStatus = "Active";
        fakeCheckInDAO.addCustomer("KH-1001", c1001);
        fakeCheckInDAO.addCustomer("0901234501", c1001);

        CheckInDAO.CustomerSubscriptionInfo s1 = new CheckInDAO.CustomerSubscriptionInfo();
        s1.subscriptionId = 1;
        s1.customerId = 1;
        s1.subscriptionType = "Membership";
        s1.packageName = "Gói 3 Tháng";
        s1.status = "Active";
        s1.startDate = LocalDate.now().minusMonths(1);
        s1.endDate = LocalDate.now().plusMonths(2);
        fakeCheckInDAO.addSubscription(1, s1);

        // KH-1014: Mua online, Active, CHƯA CÓ ẢNH check-in (CBR-38)
        CheckInDAO.CustomerLookupInfo c1014 = new CheckInDAO.CustomerLookupInfo();
        c1014.customerId = 14;
        c1014.personId = 14;
        c1014.customerCode = "KH-1014";
        c1014.fullName = "Lê Thị Hoa";
        c1014.phone = "0901234514";
        c1014.avatar = "/uploads/avatar/hoa.jpg"; // Avatar có nhưng không được dùng check-in (CBR-37)
        c1014.checkInPhoto = null;                 // Thiếu ảnh check-in quầy
        c1014.customerStatus = "Active";
        fakeCheckInDAO.addCustomer("KH-1014", c1014);

        CheckInDAO.CustomerSubscriptionInfo s14 = new CheckInDAO.CustomerSubscriptionInfo();
        s14.subscriptionId = 14;
        s14.customerId = 14;
        s14.subscriptionType = "Membership";
        s14.packageName = "Gói 1 Tháng";
        s14.status = "Active";
        s14.startDate = LocalDate.now();
        s14.endDate = LocalDate.now().plusMonths(1);
        fakeCheckInDAO.addSubscription(14, s14);

        // KH-1004: Gói đang bảo lưu (Frozen)
        CheckInDAO.CustomerLookupInfo c1004 = new CheckInDAO.CustomerLookupInfo();
        c1004.customerId = 4;
        c1004.personId = 4;
        c1004.customerCode = "KH-1004";
        c1004.fullName = "Đỗ Văn Khang";
        c1004.phone = "0901234504";
        c1004.checkInPhoto = "/uploads/checkin/kh-1004.jpg";
        c1004.customerStatus = "Frozen";
        fakeCheckInDAO.addCustomer("KH-1004", c1004);

        CheckInDAO.CustomerSubscriptionInfo s4 = new CheckInDAO.CustomerSubscriptionInfo();
        s4.subscriptionId = 4;
        s4.customerId = 4;
        s4.subscriptionType = "Membership";
        s4.packageName = "Gói 6 Tháng";
        s4.status = "Frozen";
        s4.startDate = LocalDate.now().minusMonths(2);
        s4.endDate = LocalDate.now().plusMonths(4);
        fakeCheckInDAO.addSubscription(4, s4);
        fakeCheckInDAO.setFreezeEndDate(4, LocalDate.now().plusDays(15));

        // KH-1003: Gói đã hết hạn (Expired)
        CheckInDAO.CustomerLookupInfo c1003 = new CheckInDAO.CustomerLookupInfo();
        c1003.customerId = 3;
        c1003.personId = 3;
        c1003.customerCode = "KH-1003";
        c1003.fullName = "Phạm Thu Trang";
        c1003.phone = "0901234503";
        c1003.checkInPhoto = "/uploads/checkin/kh-1003.jpg";
        c1003.customerStatus = "Expired";
        fakeCheckInDAO.addCustomer("KH-1003", c1003);

        CheckInDAO.CustomerSubscriptionInfo s3 = new CheckInDAO.CustomerSubscriptionInfo();
        s3.subscriptionId = 3;
        s3.customerId = 3;
        s3.subscriptionType = "Membership";
        s3.packageName = "Gói 1 Tháng";
        s3.status = "Expired";
        s3.startDate = LocalDate.now().minusMonths(2);
        s3.endDate = LocalDate.now().minusDays(5);
        fakeCheckInDAO.addSubscription(3, s3);

        // KH-1013: Có yêu cầu xoá chờ Manager duyệt
        CheckInDAO.CustomerLookupInfo c1013 = new CheckInDAO.CustomerLookupInfo();
        c1013.customerId = 13;
        c1013.personId = 13;
        c1013.customerCode = "KH-1013";
        c1013.fullName = "Bùi Thanh Hà";
        c1013.phone = "0901234513";
        c1013.checkInPhoto = "/uploads/checkin/kh-1013.jpg";
        c1013.customerStatus = "Active";
        fakeCheckInDAO.addCustomer("KH-1013", c1013);
        fakeCheckInDAO.setPendingDeletion(13, true);

        // KH-1005: Khách chỉ mua gói PT, không có Membership/DayPass (CBR-58)
        CheckInDAO.CustomerLookupInfo c1005 = new CheckInDAO.CustomerLookupInfo();
        c1005.customerId = 5;
        c1005.personId = 5;
        c1005.customerCode = "KH-1005";
        c1005.fullName = "Vũ Hải Nam";
        c1005.phone = "0901234505";
        c1005.checkInPhoto = "/uploads/checkin/kh-1005.jpg";
        c1005.customerStatus = "Active";
        fakeCheckInDAO.addCustomer("KH-1005", c1005);

        CheckInDAO.CustomerSubscriptionInfo sPt = new CheckInDAO.CustomerSubscriptionInfo();
        sPt.subscriptionId = 5;
        sPt.customerId = 5;
        sPt.subscriptionType = "PTSingle";
        sPt.packageName = "PT 12 buổi";
        sPt.status = "Active";
        sPt.generatedSessionCount = 12;
        fakeCheckInDAO.addSubscription(5, sPt);

        // KH-1015: Khách New chưa mua gói nào
        CheckInDAO.CustomerLookupInfo c1015 = new CheckInDAO.CustomerLookupInfo();
        c1015.customerId = 15;
        c1015.personId = 15;
        c1015.customerCode = "KH-1015";
        c1015.fullName = "Hoàng Gia Huy";
        c1015.phone = "0901234515";
        c1015.customerStatus = "New";
        fakeCheckInDAO.addCustomer("KH-1015", c1015);

        // KH-1016: Vé ngày chưa dùng
        CheckInDAO.CustomerLookupInfo c1016 = new CheckInDAO.CustomerLookupInfo();
        c1016.customerId = 16;
        c1016.personId = 16;
        c1016.customerCode = "KH-1016";
        c1016.fullName = "Nguyễn Văn Tuấn";
        c1016.phone = "0901234516";
        c1016.customerStatus = "Active";
        fakeCheckInDAO.addCustomer("KH-1016", c1016);

        CheckInDAO.CustomerSubscriptionInfo sDayPass = new CheckInDAO.CustomerSubscriptionInfo();
        sDayPass.subscriptionId = 16;
        sDayPass.customerId = 16;
        sDayPass.subscriptionType = "DayPass";
        sDayPass.packageName = "Vé Ngày";
        sDayPass.status = "Active";
        sDayPass.startDate = LocalDate.now();
        sDayPass.endDate = LocalDate.now();
        fakeCheckInDAO.addSubscription(16, sDayPass);

        // Employee Lễ tân
        Account ltAccount = new Account(10, 20, "hash", Account.Status.ACTIVE, 0, null, null, LocalDateTime.now());
        fakeAccountDAO.addAccount(10, ltAccount);

        Employee ltEmployee = new Employee(3, 20, "NV-0003", com.gms.constant.Role.RECEPTIONIST,
                LocalDate.now(), null, null, null, Employee.Status.WORKING, null, null);
        fakeEmployeeDAO.addEmployee(20, ltEmployee);
    }

    @Test
    @DisplayName("GBR-19 & MSG09: Tra cứu với từ khóa rỗng/khoảng trắng bị từ chối")
    void testLookupWithEmptyQuery() {
        ServiceResult<CheckInLookupResult> resultEmpty = checkInService.lookupCustomer("");
        assertFalse(resultEmpty.isSuccess());

        ServiceResult<CheckInLookupResult> resultNull = checkInService.lookupCustomer(null);
        assertFalse(resultNull.isSuccess());

        ServiceResult<CheckInLookupResult> resultWhitespace = checkInService.lookupCustomer("   ");
        assertFalse(resultWhitespace.isSuccess());
    }

    @Test
    @DisplayName("MSG08: Khách không tồn tại trả về thông báo không tìm thấy hồ sơ")
    void testLookupNotFound() {
        ServiceResult<CheckInLookupResult> result = checkInService.lookupCustomer("KH-9999");
        assertTrue(result.isSuccess());
        assertNotNull(result.getData());
        assertEquals(Decision.NOT_FOUND, result.getData().getDecision());
        assertFalse(result.getData().isCanCheckIn());
    }

    @Test
    @DisplayName("DBR-47 & CBR-38: KH-1001 có gói Active và ảnh check-in -> Đủ điều kiện vào tập (ELIGIBLE)")
    void testLookupEligibleCustomer() {
        ServiceResult<CheckInLookupResult> result = checkInService.lookupCustomer("KH-1001");
        assertTrue(result.isSuccess());
        CheckInLookupResult data = result.getData();
        assertNotNull(data);
        assertEquals("KH-1001", data.getCustomerCode());
        assertEquals(Decision.ELIGIBLE, data.getDecision());
        assertTrue(data.isCanCheckIn());
        assertNotNull(data.getCheckInPhoto());
    }

    @Test
    @DisplayName("CBR-37 & CBR-38: KH-1014 có Avatar nhưng thiếu ảnh check-in -> Bắt buộc chụp tại quầy")
    void testLookupMissingPhotoCustomer() {
        ServiceResult<CheckInLookupResult> result = checkInService.lookupCustomer("KH-1014");
        assertTrue(result.isSuccess());
        CheckInLookupResult data = result.getData();
        assertNotNull(data);
        assertEquals("KH-1014", data.getCustomerCode());
        assertEquals(Decision.WARN_MISSING_PHOTO, data.getDecision());
        assertFalse(data.isCanCheckIn(), "Chưa có ảnh check-in thì không được vào tập");
        assertTrue(data.isRequiresPhoto(), "Bắt buộc chụp ảnh quầy");
    }

    @Test
    @DisplayName("DBR-51 & UC-18: KH-1004 có gói đang bảo lưu -> Từ chối vào tập (DENIED_FROZEN)")
    void testLookupFrozenCustomer() {
        ServiceResult<CheckInLookupResult> result = checkInService.lookupCustomer("KH-1004");
        assertTrue(result.isSuccess());
        CheckInLookupResult data = result.getData();
        assertNotNull(data);
        assertEquals("KH-1004", data.getCustomerCode());
        assertEquals(Decision.DENIED_FROZEN, data.getDecision());
        assertFalse(data.isCanCheckIn());
    }

    @Test
    @DisplayName("DBR-34: KH-1003 gói đã hết hạn -> Từ chối vào tập (DENIED_EXPIRED)")
    void testLookupExpiredCustomer() {
        ServiceResult<CheckInLookupResult> result = checkInService.lookupCustomer("KH-1003");
        assertTrue(result.isSuccess());
        CheckInLookupResult data = result.getData();
        assertNotNull(data);
        assertEquals("KH-1003", data.getCustomerCode());
        assertEquals(Decision.DENIED_EXPIRED, data.getDecision());
        assertFalse(data.isCanCheckIn());
    }

    @Test
    @DisplayName("UC-17: KH-1013 có yêu cầu xóa chờ duyệt -> Từ chối vào tập (DENIED_DELETION_PENDING)")
    void testLookupPendingDeletionCustomer() {
        ServiceResult<CheckInLookupResult> result = checkInService.lookupCustomer("KH-1013");
        assertTrue(result.isSuccess());
        CheckInLookupResult data = result.getData();
        assertNotNull(data);
        assertEquals("KH-1013", data.getCustomerCode());
        assertEquals(Decision.DENIED_DELETION_PENDING, data.getDecision());
        assertFalse(data.isCanCheckIn());
    }

    @Test
    @DisplayName("CBR-58: KH-1005 chỉ mua gói PT -> Từ chối vào phòng tập tự do (DENIED_PT_ONLY)")
    void testLookupPtOnlyCustomer() {
        ServiceResult<CheckInLookupResult> result = checkInService.lookupCustomer("KH-1005");
        assertTrue(result.isSuccess());
        CheckInLookupResult data = result.getData();
        assertNotNull(data);
        assertEquals("KH-1005", data.getCustomerCode());
        assertEquals(Decision.DENIED_PT_ONLY, data.getDecision());
        assertFalse(data.isCanCheckIn(), "Gói PT không cho vào tập tự do");
    }

    @Test
    @DisplayName("DBR-34: KH-1015 chưa đăng ký gói nào -> Từ chối vào tập (DENIED_NO_PACKAGE)")
    void testLookupNoPackageCustomer() {
        ServiceResult<CheckInLookupResult> result = checkInService.lookupCustomer("KH-1015");
        assertTrue(result.isSuccess());
        CheckInLookupResult data = result.getData();
        assertNotNull(data);
        assertEquals("KH-1015", data.getCustomerCode());
        assertEquals(Decision.DENIED_NO_PACKAGE, data.getDecision());
        assertFalse(data.isCanCheckIn());
    }

    @Test
    @DisplayName("DBR-47: KH-1016 vé ngày chưa dùng -> Đủ điều kiện vào tập (ELIGIBLE_DAYPASS)")
    void testLookupDayPassCustomer() {
        ServiceResult<CheckInLookupResult> result = checkInService.lookupCustomer("KH-1016");
        assertTrue(result.isSuccess());
        CheckInLookupResult data = result.getData();
        assertNotNull(data);
        assertEquals("KH-1016", data.getCustomerCode());
        assertEquals(Decision.ELIGIBLE_DAYPASS, data.getDecision());
        assertTrue(data.isCanCheckIn());
    }

    @Test
    @DisplayName("SRS 6.3.1: Tra cứu bằng số điện thoại của hội viên")
    void testLookupByPhone() {
        ServiceResult<CheckInLookupResult> result = checkInService.lookupCustomer("0901234501");
        assertTrue(result.isSuccess());
        CheckInLookupResult data = result.getData();
        assertNotNull(data);
        assertEquals("KH-1001", data.getCustomerCode());
        assertEquals("0901234501", data.getPhone());
    }

    @Test
    @DisplayName("UC-19: Thực hiện check-in cho hội viên và ghi nhận ID Lễ tân xác nhận")
    void testPerformCheckIn() {
        ServiceResult<Void> result = checkInService.performCheckIn(1, 10, true);
        assertTrue(result.isSuccess());
        assertEquals(1, fakeCheckInDAO.getInsertedCheckIns().size());
        assertEquals(1, fakeCheckInDAO.getInsertedCheckIns().get(0).getSubscriptionId());
        assertEquals(3, fakeCheckInDAO.getInsertedCheckIns().get(0).getVerifiedByEmployeeId());
    }

    @Test
    @DisplayName("CBR-38: Lễ tân chụp và cập nhật ảnh check-in tại quầy cho hội viên")
    void testUpdateCheckInPhoto() {
        ServiceResult<Void> result = checkInService.updateCheckInPhoto(14, "/uploads/checkin/kh-14-new.jpg");
        assertTrue(result.isSuccess());
        assertEquals("/uploads/checkin/kh-14-new.jpg", fakeCheckInDAO.getPhoto(14));
    }

    // ===== FAKE DAOs =====

    private static class FakeCheckInDAO extends CheckInDAO {
        private final Map<String, CustomerLookupInfo> customers = new HashMap<>();
        private final Map<Integer, List<CustomerSubscriptionInfo>> subscriptions = new HashMap<>();
        private final Map<Integer, Boolean> pendingDeletions = new HashMap<>();
        private final Map<Integer, LocalDate> freezeDates = new HashMap<>();
        private final Map<Integer, String> photos = new HashMap<>();
        private final List<CheckIn> insertedCheckIns = new ArrayList<>();

        void addCustomer(String key, CustomerLookupInfo info) {
            customers.put(key.toUpperCase(), info);
        }

        void addSubscription(int customerId, CustomerSubscriptionInfo sub) {
            subscriptions.computeIfAbsent(customerId, k -> new ArrayList<>()).add(sub);
        }

        void setPendingDeletion(int customerId, boolean pending) {
            pendingDeletions.put(customerId, pending);
        }

        void setFreezeEndDate(int subscriptionId, LocalDate date) {
            freezeDates.put(subscriptionId, date);
        }

        String getPhoto(int customerId) {
            return photos.get(customerId);
        }

        List<CheckIn> getInsertedCheckIns() {
            return insertedCheckIns;
        }

        @Override
        public CustomerLookupInfo findCustomerByKeyword(String keyword) {
            return customers.get(keyword.trim().toUpperCase());
        }

        @Override
        public boolean hasPendingDeletionRequest(int customerId) {
            return pendingDeletions.getOrDefault(customerId, false);
        }

        @Override
        public List<CustomerSubscriptionInfo> findSubscriptionsByCustomerId(int customerId) {
            return subscriptions.getOrDefault(customerId, List.of());
        }

        @Override
        public LocalDate getPlannedFreezeEndDate(int subscriptionId) {
            return freezeDates.get(subscriptionId);
        }

        @Override
        public boolean isDayPassUsedToday(int subscriptionId) {
            return false;
        }

        @Override
        public int insertCheckIn(CheckIn checkIn) {
            insertedCheckIns.add(checkIn);
            return insertedCheckIns.size();
        }

        @Override
        public int insertCheckIn(Connection conn, CheckIn checkIn) {
            insertedCheckIns.add(checkIn);
            return insertedCheckIns.size();
        }

        @Override
        public boolean updateCheckInPhoto(int customerId, String photoPath) {
            photos.put(customerId, photoPath);
            return true;
        }

        @Override
        public List<CheckInLogItem> findTodayCheckIns(int limit) {
            return List.of(new CheckInLogItem(1, "Trần Bảo Ngọc", "KH-1001", LocalDateTime.now(), "Gói 3 Tháng", true));
        }
    }

    private static class FakeAccountDAO extends AccountDAO {
        private final Map<Integer, Account> accounts = new HashMap<>();

        void addAccount(int accountId, Account account) {
            accounts.put(accountId, account);
        }

        @Override
        public Account findById(int accountId) {
            return accounts.get(accountId);
        }
    }

    private static class FakeEmployeeDAO extends EmployeeDAO {
        private final Map<Integer, Employee> employees = new HashMap<>();

        void addEmployee(int personId, Employee employee) {
            employees.put(personId, employee);
        }

        @Override
        public Employee findByPersonId(int personId) {
            return employees.get(personId);
        }
    }
}
