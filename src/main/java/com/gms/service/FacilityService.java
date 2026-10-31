package com.gms.service;

import com.gms.constant.Messages;
import com.gms.dao.EquipmentItemDAO;
import com.gms.dao.FacilityDAO;
import com.gms.dto.ServiceResult;
import com.gms.entity.EquipmentItem;
import com.gms.entity.Facility;
import com.gms.util.ValidationUtil;

import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

/**
 * Service Layer: Quản lý nghiệp vụ danh mục cơ sở vật chất và cá thể máy (Facilities & EquipmentItems).
 */
public class FacilityService {

    private final FacilityDAO facilityDAO;
    private final EquipmentItemDAO equipmentItemDAO;

    public FacilityService() {
        this.facilityDAO = new FacilityDAO();
        this.equipmentItemDAO = new EquipmentItemDAO();
    }

    public FacilityService(FacilityDAO facilityDAO, EquipmentItemDAO equipmentItemDAO) {
        this.facilityDAO = facilityDAO;
        this.equipmentItemDAO = equipmentItemDAO;
    }

    /**
     * Lấy toàn bộ danh mục nhóm máy cho màn hình Quản lý Manager (kèm danh sách cá thể con).
     */
    public ServiceResult<List<Facility>> getAllFacilitiesForManagement() {
        try {
            List<Facility> list = facilityDAO.findAllForManagement();
            return ServiceResult.ok(list);
        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /**
     * Lấy thông tin 1 nhóm máy theo ID.
     */
    public ServiceResult<Facility> getFacilityById(int facilityId) {
        if (facilityId <= 0) {
            return ServiceResult.fail(Messages.MSG217_FACILITY_ID_INVALID);
        }
        try {
            Facility facility = facilityDAO.findById(facilityId);
            if (facility == null) {
                return ServiceResult.fail(String.format(Messages.MSG08_NOT_FOUND, "nhóm cơ sở vật chất"));
            }
            List<EquipmentItem> items = equipmentItemDAO.findByFacilityIdForManagement(facilityId);
            facility.setItems(items);
            return ServiceResult.ok(facility);
        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /**
     * Lấy danh sách các cá thể máy con của một nhóm máy.
     */
    public ServiceResult<List<EquipmentItem>> getItemsByFacilityId(int facilityId) {
        if (facilityId <= 0) {
            return ServiceResult.ok(Collections.emptyList());
        }
        try {
            List<EquipmentItem> items = equipmentItemDAO.findByFacilityIdForManagement(facilityId);
            return ServiceResult.ok(items);
        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /**
     * Lấy danh sách các vị trí PlacementNote không trùng lặp để gợi ý người dùng (dùng datalist).
     */
    public List<String> getPlacementSuggestions() {
        try {
            return equipmentItemDAO.findDistinctPlacementNotes();
        } catch (SQLException e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    /**
     * Thêm mới hoặc cập nhật một nhóm cơ sở vật chất.
     */
    public ServiceResult<Integer> saveFacility(Facility facility) {
        // B1: Validate dữ liệu đầu vào
        if (facility == null) {
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }

        String name = facility.getFacilityName();
        if (!ValidationUtil.isLengthValid(name, 3, 100)) {
            return ServiceResult.failField("facilityName", Messages.MSG218_FACILITY_NAME_LENGTH);
        }

        if (facility.getCategory() == null) {
            return ServiceResult.failField("category", Messages.MSG219_FACILITY_CATEGORY_REQUIRED);
        }

        String unitLabel = facility.getUnitLabel();
        if (unitLabel == null || unitLabel.isBlank()) {
            facility.setUnitLabel("máy");
        } else if (unitLabel.length() > 15) {
            return ServiceResult.failField("unitLabel", Messages.MSG220_FACILITY_UNIT_LENGTH);
        }

        // B2: Gọi DAO lưu dữ liệu
        try {
            if (facility.getFacilityId() > 0) {
                boolean updated = facilityDAO.update(facility);
                if (!updated) {
                    return ServiceResult.fail(Messages.MSG221_FACILITY_NOT_FOUND_FOR_UPDATE);
                }
                return ServiceResult.ok(facility.getFacilityId());
            } else {
                facility.setActive(true);
                int generatedId = facilityDAO.create(facility);
                if (generatedId <= 0) {
                    return ServiceResult.fail(Messages.MSG222_FACILITY_CREATE_FAILED);
                }
                return ServiceResult.ok(generatedId);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /**
     * Ẩn hoặc Hiện một nhóm cơ sở vật chất.
     */
    public ServiceResult<Void> toggleFacilityActive(int facilityId, boolean isActive) {
        if (facilityId <= 0) {
            return ServiceResult.fail(Messages.MSG223_FACILITY_GROUP_ID_INVALID);
        }
        try {
            boolean updated = facilityDAO.toggleActive(facilityId, isActive);
            if (!updated) {
                return ServiceResult.fail(String.format(Messages.MSG08_NOT_FOUND, "nhóm máy"));
            }
            return ServiceResult.ok(null);
        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /**
     * Thêm mới hoặc cập nhật một cá thể máy vật lý.
     */
    public ServiceResult<Integer> saveEquipmentItem(EquipmentItem item) {
        if (item == null) {
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }

        if (item.getFacilityId() <= 0) {
            return ServiceResult.failField("facilityId", Messages.MSG224_FACILITY_GROUP_REQUIRED);
        }

        String itemCode = item.getItemCode();
        if (itemCode == null || itemCode.trim().length() < 2 || itemCode.trim().length() > 20) {
            return ServiceResult.failField("itemCode", Messages.MSG225_ITEM_CODE_LENGTH);
        }
        item.setItemCode(itemCode.trim().toUpperCase());

        String itemLabel = item.getItemLabel();
        if (itemLabel == null || itemLabel.trim().isEmpty() || itemLabel.trim().length() > 50) {
            return ServiceResult.failField("itemLabel", Messages.MSG226_ITEM_LABEL_LENGTH);
        }
        item.setItemLabel(itemLabel.trim());

        String placement = item.getPlacementNote();
        if (placement != null && placement.trim().length() > 100) {
            return ServiceResult.failField("placementNote", Messages.MSG227_ITEM_LOCATION_LENGTH);
        }

        try {
            // Kiểm tra trùng ItemCode
            Integer excludeId = item.getEquipmentItemId() > 0 ? item.getEquipmentItemId() : null;
            if (equipmentItemDAO.isItemCodeExists(item.getItemCode(), excludeId)) {
                return ServiceResult.failField("itemCode", String.format(Messages.MSG228_ITEM_CODE_EXISTS, item.getItemCode()));
            }

            if (item.getEquipmentItemId() > 0) {
                boolean updated = equipmentItemDAO.update(item);
                if (!updated) {
                    return ServiceResult.fail(Messages.MSG229_ITEM_NOT_FOUND_FOR_UPDATE);
                }
                return ServiceResult.ok(item.getEquipmentItemId());
            } else {
                item.setStatus(EquipmentItem.Status.AVAILABLE);
                int generatedId = equipmentItemDAO.create(item);
                if (generatedId <= 0) {
                    return ServiceResult.fail(Messages.MSG230_ITEM_CREATE_FAILED);
                }
                return ServiceResult.ok(generatedId);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /**
     * Thanh lý một cá thể máy vật lý (chuyển sang 'Retired').
     */
    public ServiceResult<Void> retireEquipmentItem(int equipmentItemId) {
        if (equipmentItemId <= 0) {
            return ServiceResult.fail(Messages.MSG231_ITEM_ID_INVALID);
        }
        try {
            boolean updated = equipmentItemDAO.retire(equipmentItemId);
            if (!updated) {
                return ServiceResult.fail(String.format(Messages.MSG08_NOT_FOUND, "cá thể máy"));
            }
            return ServiceResult.ok(null);
        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }
}
