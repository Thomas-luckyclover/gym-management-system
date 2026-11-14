package com.gms.service;

import com.gms.constant.Messages;
import com.gms.dao.EquipmentItemDAO;
import com.gms.dao.MaintenanceRequestDAO;
import java.sql.Connection;
import com.gms.dao.FacilityDAO;
import com.gms.dao.MaintenanceRequestDAO;
import com.gms.dto.ServiceResult;
import com.gms.entity.EquipmentItem;
import com.gms.entity.Facility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit Test cho FacilityService (Quản lý Cơ sở vật chất & Thiết bị vật lý).
 */
public class FacilityServiceTest {

    private FakeFacilityDAO fakeFacilityDAO;
    private FakeEquipmentItemDAO fakeEquipmentItemDAO;
    private FakeMaintenanceRequestDAO fakeMaintenanceRequestDAO;
    private FacilityService facilityService;

    @BeforeEach
    void setUp() {
        fakeFacilityDAO = new FakeFacilityDAO();
        fakeEquipmentItemDAO = new FakeEquipmentItemDAO();
        fakeMaintenanceRequestDAO = new FakeMaintenanceRequestDAO();
        facilityService = new FacilityService(fakeFacilityDAO, fakeEquipmentItemDAO, fakeMaintenanceRequestDAO);

        // Seed 1 facility mẫu
        Facility f1 = new Facility(1, "Máy chạy bộ", Facility.Category.CARDIO, "Tầng 1", 2, Facility.Status.AVAILABLE, 0, 2);
        f1.setUnitLabel("máy");
        f1.setActive(true);
        fakeFacilityDAO.facilities.put(1, f1);

        EquipmentItem i1 = new EquipmentItem(1, 1, "TM-001", "Số 1", "Dãy cửa sổ", EquipmentItem.Status.AVAILABLE);
        EquipmentItem i2 = new EquipmentItem(2, 1, "TM-002", "Số 2", "Dãy cửa sổ", EquipmentItem.Status.AVAILABLE);
        fakeEquipmentItemDAO.items.put(1, i1);
        fakeEquipmentItemDAO.items.put(2, i2);
    }

    @Test
    @DisplayName("Lấy danh mục nhóm máy quản lý thành công")
    void getAllFacilitiesForManagement_success() {
        ServiceResult<List<Facility>> result = facilityService.getAllFacilitiesForManagement();
        assertTrue(result.isSuccess());
        assertEquals(1, result.getData().size());
    }

    @Test
    @DisplayName("Lấy chi tiết nhóm máy theo ID kèm danh sách con")
    void getFacilityById_success() {
        ServiceResult<Facility> result = facilityService.getFacilityById(1);
        assertTrue(result.isSuccess());
        assertNotNull(result.getData());
        assertEquals("Máy chạy bộ", result.getData().getFacilityName());
        assertEquals(2, result.getData().getItems().size());
    }

    @Test
    @DisplayName("Thêm nhóm máy: Báo lỗi khi tên nhóm máy dưới 3 ký tự")
    void saveFacility_shortName_fails() {
        Facility f = new Facility();
        f.setFacilityName("AB");
        f.setCategory(Facility.Category.MACHINES);

        ServiceResult<Integer> result = facilityService.saveFacility(f);
        assertFalse(result.isSuccess());
        assertTrue(result.getFieldErrors().containsKey("facilityName"));
    }

    @Test
    @DisplayName("Thêm nhóm máy: Thành công")
    void saveFacility_valid_success() {
        Facility f = new Facility();
        f.setFacilityName("Giàn tạ đa năng");
        f.setCategory(Facility.Category.MACHINES);
        f.setLocation("Tầng 2");
        f.setUnitLabel("máy");

        ServiceResult<Integer> result = facilityService.saveFacility(f);
        assertTrue(result.isSuccess());
        assertTrue(result.getData() > 0);
    }

    @Test
    @DisplayName("Ẩn / Hiện nhóm máy thành công")
    void toggleFacilityActive_success() {
        ServiceResult<Void> result = facilityService.toggleFacilityActive(1, false);
        assertTrue(result.isSuccess());
        assertFalse(fakeFacilityDAO.facilities.get(1).isActive());
    }

    @Test
    @DisplayName("Thêm cá thể máy: Báo lỗi khi mã máy trống hoặc quá ngắn")
    void saveEquipmentItem_shortCode_fails() {
        EquipmentItem item = new EquipmentItem();
        item.setFacilityId(1);
        item.setItemCode("T");
        item.setItemLabel("Số 1");

        ServiceResult<Integer> result = facilityService.saveEquipmentItem(item);
        assertFalse(result.isSuccess());
        assertTrue(result.getFieldErrors().containsKey("itemCode"));
    }

    @Test
    @DisplayName("Thêm cá thể máy: Báo lỗi khi trùng ItemCode")
    void saveEquipmentItem_duplicateCode_fails() {
        EquipmentItem item = new EquipmentItem();
        item.setFacilityId(1);
        item.setItemCode("TM-001"); // đã có trong setUp
        item.setItemLabel("Số 3");

        ServiceResult<Integer> result = facilityService.saveEquipmentItem(item);
        assertFalse(result.isSuccess());
        assertTrue(result.getFieldErrors().containsKey("itemCode"));
    }

    @Test
    @DisplayName("Thêm cá thể máy: Báo lỗi rõ ràng khi trùng ItemLabel trong cùng nhóm máy")
    void saveEquipmentItem_duplicateLabelInSameFacility_fails() {
        EquipmentItem item = new EquipmentItem();
        item.setFacilityId(1);
        item.setItemCode("TM-003");
        item.setItemLabel("số 1"); // trùng với item 1 ("Số 1") trong facilityId = 1

        ServiceResult<Integer> result = facilityService.saveEquipmentItem(item);
        assertFalse(result.isSuccess());
        assertTrue(result.getFieldErrors().containsKey("itemLabel"));
        assertEquals("Tên hiển thị / Quy cách 'số 1' đã tồn tại trong nhóm máy này.", result.getMessage());
    }

    @Test
    @DisplayName("Thêm cá thể máy: Cho phép trùng ItemLabel ở nhóm máy khác")
    void saveEquipmentItem_sameLabelInDifferentFacility_success() {
        EquipmentItem item = new EquipmentItem();
        item.setFacilityId(2);
        item.setItemCode("BK-001");
        item.setItemLabel("Số 1");

        ServiceResult<Integer> result = facilityService.saveEquipmentItem(item);
        assertTrue(result.isSuccess());
        assertTrue(result.getData() > 0);
    }

    @Test
    @DisplayName("Cập nhật cá thể máy: Cho phép giữ nguyên ItemLabel của chính nó")
    void saveEquipmentItem_updateKeepingOwnLabel_success() {
        EquipmentItem item = new EquipmentItem();
        item.setEquipmentItemId(1);
        item.setFacilityId(1);
        item.setItemCode("TM-001");
        item.setItemLabel("Số 1");
        item.setPlacementNote("Gần cửa chính");

        ServiceResult<Integer> result = facilityService.saveEquipmentItem(item);
        assertTrue(result.isSuccess());
        assertEquals(1, result.getData());
    }

    @Test
    @DisplayName("Lưu cá thể máy: Lỗi SQL trùng UQ_EquipmentItems_FacilityLabel (2627) được map về lỗi itemLabel thay vì MSG04")
    void saveEquipmentItem_sqlDuplicateLabel2627_returnsClearFieldError() {
        fakeEquipmentItemDAO.sqlExceptionOnWrite = new SQLException(
                "Violation of UNIQUE KEY constraint 'UQ_EquipmentItems_FacilityLabel'. Cannot insert duplicate key in object 'dbo.EquipmentItems'.",
                "23000",
                2627);

        EquipmentItem item = new EquipmentItem();
        item.setFacilityId(1);
        item.setItemCode("TM-009");
        item.setItemLabel("Số 9");

        ServiceResult<Integer> result = facilityService.saveEquipmentItem(item);
        assertFalse(result.isSuccess());
        assertTrue(result.getFieldErrors().containsKey("itemLabel"));
        assertEquals("Tên hiển thị / Quy cách 'Số 9' đã tồn tại trong nhóm máy này.", result.getMessage());
    }

    @Test
    @DisplayName("Lưu cá thể máy: Lỗi SQL trùng UQ_EquipmentItems_ItemCode (2627) được map về lỗi itemCode thay vì MSG04")
    void saveEquipmentItem_sqlDuplicateCode2627_returnsClearFieldError() {
        fakeEquipmentItemDAO.sqlExceptionOnWrite = new SQLException(
                "Violation of UNIQUE KEY constraint 'UQ_EquipmentItems_ItemCode'.",
                "23000",
                2627);

        EquipmentItem item = new EquipmentItem();
        item.setFacilityId(1);
        item.setItemCode("TM-009");
        item.setItemLabel("Số 9");

        ServiceResult<Integer> result = facilityService.saveEquipmentItem(item);
        assertFalse(result.isSuccess());
        assertTrue(result.getFieldErrors().containsKey("itemCode"));
        assertEquals(String.format(Messages.MSG228_ITEM_CODE_EXISTS, "TM-009"), result.getMessage());
    }

    @Test
    @DisplayName("Lưu nhóm máy: Kiểm tra các biên độ dài, null và chuẩn hóa khoảng trắng")
    void saveFacility_boundaries_validatedAndNormalized() {
        assertFalse(facilityService.saveFacility(null).isSuccess());

        Facility minFacility = new Facility();
        minFacility.setFacilityName("  ABC  ");
        minFacility.setCategory(Facility.Category.MACHINES);
        minFacility.setLocation(null);
        minFacility.setUnitLabel("   ");
        ServiceResult<Integer> minRes = facilityService.saveFacility(minFacility);
        assertTrue(minRes.isSuccess());
        assertEquals("ABC", fakeFacilityDAO.facilities.get(minRes.getData()).getFacilityName());
        assertEquals("", fakeFacilityDAO.facilities.get(minRes.getData()).getLocation());
        assertEquals("máy", fakeFacilityDAO.facilities.get(minRes.getData()).getUnitLabel());

        Facility maxFacility = new Facility();
        maxFacility.setFacilityName(" " + "F".repeat(100) + " ");
        maxFacility.setCategory(Facility.Category.CARDIO);
        maxFacility.setLocation(" " + "L".repeat(100) + " ");
        maxFacility.setUnitLabel(" " + "U".repeat(15) + " ");
        ServiceResult<Integer> maxRes = facilityService.saveFacility(maxFacility);
        assertTrue(maxRes.isSuccess());
        assertEquals(100, fakeFacilityDAO.facilities.get(maxRes.getData()).getFacilityName().length());
        assertEquals(100, fakeFacilityDAO.facilities.get(maxRes.getData()).getLocation().length());
        assertEquals(15, fakeFacilityDAO.facilities.get(maxRes.getData()).getUnitLabel().length());

        Facility tooLongLocation = new Facility();
        tooLongLocation.setFacilityName("Tạ đòn");
        tooLongLocation.setCategory(Facility.Category.FREE_WEIGHTS);
        tooLongLocation.setLocation("L".repeat(101));
        ServiceResult<Integer> locFail = facilityService.saveFacility(tooLongLocation);
        assertFalse(locFail.isSuccess());
        assertTrue(locFail.getFieldErrors().containsKey("location"));

        Facility tooLongUnit = new Facility();
        tooLongUnit.setFacilityName("Tạ đơn");
        tooLongUnit.setCategory(Facility.Category.FREE_WEIGHTS);
        tooLongUnit.setUnitLabel("U".repeat(16));
        ServiceResult<Integer> unitFail = facilityService.saveFacility(tooLongUnit);
        assertFalse(unitFail.isSuccess());
        assertTrue(unitFail.getFieldErrors().containsKey("unitLabel"));
    }

    @Test
    @DisplayName("Lưu cá thể máy: Kiểm tra biên độ dài và khoảng trắng cho mã, nhãn, vị trí")
    void saveEquipmentItem_lengthAndWhitespaceBoundaries() {
        assertFalse(facilityService.saveEquipmentItem(null).isSuccess());

        EquipmentItem badFacility = new EquipmentItem();
        badFacility.setFacilityId(0);
        badFacility.setItemCode("AB");
        badFacility.setItemLabel("1");
        assertTrue(facilityService.saveEquipmentItem(badFacility).getFieldErrors().containsKey("facilityId"));

        // Biên tối thiểu: mã 2 ký tự (chuẩn hóa uppercase), nhãn 1 ký tự, placement khoảng trắng -> null
        EquipmentItem minItem = new EquipmentItem();
        minItem.setFacilityId(1);
        minItem.setItemCode("  ab  ");
        minItem.setItemLabel(" X ");
        minItem.setPlacementNote("   ");
        ServiceResult<Integer> minRes = facilityService.saveEquipmentItem(minItem);
        assertTrue(minRes.isSuccess());
        assertEquals("AB", fakeEquipmentItemDAO.items.get(minRes.getData()).getItemCode());
        assertEquals("X", fakeEquipmentItemDAO.items.get(minRes.getData()).getItemLabel());
        assertEquals(null, fakeEquipmentItemDAO.items.get(minRes.getData()).getPlacementNote());

        // Biên tối đa: mã 20, nhãn 50, vị trí 100
        EquipmentItem maxItem = new EquipmentItem();
        maxItem.setFacilityId(1);
        maxItem.setItemCode("C".repeat(20));
        maxItem.setItemLabel("L".repeat(50));
        maxItem.setPlacementNote(" " + "P".repeat(100) + " ");
        ServiceResult<Integer> maxRes = facilityService.saveEquipmentItem(maxItem);
        assertTrue(maxRes.isSuccess());
        assertEquals(100, fakeEquipmentItemDAO.items.get(maxRes.getData()).getPlacementNote().length());

        // Vượt biên tối đa: mã 21, nhãn 51, vị trí 101, và trùng nhãn khi thêm khoảng trắng
        EquipmentItem overCode = new EquipmentItem();
        overCode.setFacilityId(1);
        overCode.setItemCode("C".repeat(21));
        overCode.setItemLabel("OK");
        assertTrue(facilityService.saveEquipmentItem(overCode).getFieldErrors().containsKey("itemCode"));

        EquipmentItem overLabel = new EquipmentItem();
        overLabel.setFacilityId(1);
        overLabel.setItemCode("OK-01");
        overLabel.setItemLabel("L".repeat(51));
        assertTrue(facilityService.saveEquipmentItem(overLabel).getFieldErrors().containsKey("itemLabel"));

        EquipmentItem overPlacement = new EquipmentItem();
        overPlacement.setFacilityId(1);
        overPlacement.setItemCode("OK-02");
        overPlacement.setItemLabel("OK 2");
        overPlacement.setPlacementNote("P".repeat(101));
        assertTrue(facilityService.saveEquipmentItem(overPlacement).getFieldErrors().containsKey("placementNote"));

        EquipmentItem paddedDuplicateLabel = new EquipmentItem();
        paddedDuplicateLabel.setFacilityId(1);
        paddedDuplicateLabel.setItemCode("TM-999");
        paddedDuplicateLabel.setItemLabel("  Số 1  ");
        assertTrue(facilityService.saveEquipmentItem(paddedDuplicateLabel).getFieldErrors().containsKey("itemLabel"));
    }

    @Test
    @DisplayName("Lưu cá thể máy: Fallback lỗi SQL 2601 không kèm tên constraint và lỗi SQL khác 50000")
    void saveEquipmentItem_sqlDuplicate2601FallbackAndUnexpectedSqlError() {
        fakeEquipmentItemDAO.simulateLabelExistsAfterSqlError = true;
        fakeEquipmentItemDAO.sqlExceptionOnWrite = new SQLException("Duplicate key row", "23000", 2601);

        EquipmentItem dupItem = new EquipmentItem();
        dupItem.setFacilityId(1);
        dupItem.setItemCode("TM-888");
        dupItem.setItemLabel("Nhãn mới");
        ServiceResult<Integer> dupRes = facilityService.saveEquipmentItem(dupItem);
        assertFalse(dupRes.isSuccess());
        assertTrue(dupRes.getFieldErrors().containsKey("itemLabel"));

        fakeEquipmentItemDAO.simulateLabelExistsAfterSqlError = false;
        fakeEquipmentItemDAO.sqlExceptionOnWrite = new SQLException("Unexpected DB timeout", "HY000", 50000);
        ServiceResult<Integer> busyRes = facilityService.saveEquipmentItem(dupItem);
        assertFalse(busyRes.isSuccess());
        assertEquals(Messages.MSG04_SYSTEM_BUSY, busyRes.getMessage());
    }

    @Test
    @DisplayName("Thêm cá thể máy: Thành công")
    void saveEquipmentItem_valid_success() {
        EquipmentItem item = new EquipmentItem();
        item.setFacilityId(1);
        item.setItemCode("TM-003");
        item.setItemLabel("Số 3");
        item.setPlacementNote("Dãy cửa sổ");

        ServiceResult<Integer> result = facilityService.saveEquipmentItem(item);
        assertTrue(result.isSuccess());
        assertTrue(result.getData() > 0);
    }

    @Test
    @DisplayName("Thanh lý cá thể máy thành công chuyển sang Retired")
    void retireEquipmentItem_success() {
        ServiceResult<Void> result = facilityService.retireEquipmentItem(1);
        assertTrue(result.isSuccess());
        assertEquals(EquipmentItem.Status.RETIRED, fakeEquipmentItemDAO.items.get(1).getStatus());
    }

    @Test
    @DisplayName("Không thanh lý được cá thể máy đang có yêu cầu bảo trì chưa hoàn thành")
    void retireEquipmentItem_withActiveRequest_fails() {
        fakeMaintenanceRequestDAO.hasActive = true;
        EquipmentItem.Status before = fakeEquipmentItemDAO.items.get(1).getStatus();

        ServiceResult<Void> result = facilityService.retireEquipmentItem(1);

        assertFalse(result.isSuccess());
        assertEquals(before, fakeEquipmentItemDAO.items.get(1).getStatus());
    }

    // --- Fake DAO Classes ---
    private static class FakeMaintenanceRequestDAO extends MaintenanceRequestDAO {
        boolean hasActive = false;

        @Override
        public boolean hasActiveRequest(int equipmentItemId) {
            return hasActive;
        }

        @Override
        public boolean hasActiveRequest(Connection connection, int equipmentItemId) {
            return hasActive;
        }
    }

    private static class FakeFacilityDAO extends FacilityDAO {
        final Map<Integer, Facility> facilities = new HashMap<>();
        int nextId = 100;

        @Override
        public List<Facility> findAllForManagement() {
            return new ArrayList<>(facilities.values());
        }

        @Override
        public Facility findById(int id) {
            return facilities.get(id);
        }

        @Override
        public int create(Facility f) {
            int id = nextId++;
            f.setFacilityId(id);
            facilities.put(id, f);
            return id;
        }

        @Override
        public boolean update(Facility f) {
            facilities.put(f.getFacilityId(), f);
            return true;
        }

        @Override
        public boolean toggleActive(int id, boolean active) {
            Facility f = facilities.get(id);
            if (f != null) {
                f.setActive(active);
                return true;
            }
            return false;
        }
    }

    private static class FakeEquipmentItemDAO extends EquipmentItemDAO {
        final Map<Integer, EquipmentItem> items = new HashMap<>();
        int nextId = 200;
        SQLException sqlExceptionOnWrite;
        boolean simulateLabelExistsAfterSqlError = false;
        private int labelCheckCount = 0;

        @Override
        public List<EquipmentItem> findByFacilityIdForManagement(int facilityId) {
            List<EquipmentItem> list = new ArrayList<>();
            for (EquipmentItem i : items.values()) {
                if (i.getFacilityId() == facilityId) list.add(i);
            }
            return list;
        }

        @Override
        public boolean isItemCodeExists(String code, Integer excludeId) {
            for (EquipmentItem i : items.values()) {
                if (excludeId != null && i.getEquipmentItemId() == excludeId) continue;
                if (i.getItemCode().equalsIgnoreCase(code)) return true;
            }
            return false;
        }

        @Override
        public boolean isItemLabelExists(int facilityId, String itemLabel, Integer excludeId) {
            labelCheckCount++;
            if (simulateLabelExistsAfterSqlError && labelCheckCount > 1) {
                return true;
            }
            for (EquipmentItem i : items.values()) {
                if (excludeId != null && i.getEquipmentItemId() == excludeId) continue;
                if (i.getFacilityId() == facilityId && i.getItemLabel() != null
                        && i.getItemLabel().equalsIgnoreCase(itemLabel)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public int create(EquipmentItem item) throws SQLException {
            if (sqlExceptionOnWrite != null) {
                throw sqlExceptionOnWrite;
            }
            int id = nextId++;
            item.setEquipmentItemId(id);
            items.put(id, item);
            return id;
        }

        @Override
        public boolean update(EquipmentItem item) throws SQLException {
            if (sqlExceptionOnWrite != null) {
                throw sqlExceptionOnWrite;
            }
            items.put(item.getEquipmentItemId(), item);
            return true;
        }

        @Override
        public boolean retire(int id) {
            EquipmentItem i = items.get(id);
            if (i != null) {
                i.setStatus(EquipmentItem.Status.RETIRED);
                return true;
            }
            return false;
        }
    }
}