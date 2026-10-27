package com.gms.service;

import com.gms.dao.EquipmentItemDAO;
import com.gms.dao.FacilityDAO;
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
    private FacilityService facilityService;

    @BeforeEach
    void setUp() {
        fakeFacilityDAO = new FakeFacilityDAO();
        fakeEquipmentItemDAO = new FakeEquipmentItemDAO();
        facilityService = new FacilityService(fakeFacilityDAO, fakeEquipmentItemDAO);

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

    // --- Fake DAO Classes ---
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
        public int create(EquipmentItem item) {
            int id = nextId++;
            item.setEquipmentItemId(id);
            items.put(id, item);
            return id;
        }

        @Override
        public boolean update(EquipmentItem item) {
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
