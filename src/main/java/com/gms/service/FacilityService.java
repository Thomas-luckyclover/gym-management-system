package com.gms.service;

import com.gms.constant.Messages;
import com.gms.dao.EquipmentItemDAO;
import com.gms.dao.FacilityDAO;
import com.gms.dao.MaintenanceRequestDAO;
import com.gms.dto.ServiceResult;
import com.gms.entity.EquipmentItem;
import com.gms.entity.Facility;
import com.gms.util.ValidationUtil;

import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

/**
 * Service Layer: Xử lý nghiệp vụ quản lý danh mục dòng máy và cá thể thiết bị vật lý (Facilities & EquipmentItems).
 */
public class FacilityService {

    private final FacilityDAO facilityDAO;
    private final EquipmentItemDAO equipmentItemDAO;
    private final MaintenanceRequestDAO maintenanceRequestDAO;

    /**
     * Khởi tạo Service mặc định cùng các DAO cần thiết cho nghiệp vụ quản lý cơ sở vật chất.
     */
    public FacilityService() {
        this.facilityDAO = new FacilityDAO();
        this.equipmentItemDAO = new EquipmentItemDAO();
        this.maintenanceRequestDAO = new MaintenanceRequestDAO();
    }

    /**
     * Khởi tạo Service cho phép tiêm DAO giả lập (dùng chủ yếu trong unit test).
     */
    public FacilityService(FacilityDAO facilityDAO, EquipmentItemDAO equipmentItemDAO, MaintenanceRequestDAO maintenanceRequestDAO) {
        this.facilityDAO = facilityDAO;
        this.equipmentItemDAO = equipmentItemDAO;
        this.maintenanceRequestDAO = maintenanceRequestDAO;
    }

    /**
     * Lấy toàn bộ danh mục dòng máy phục vụ màn hình Quản lý cơ sở vật chất của Manager.
     * Hàm gọi FacilityDAO tải cả dòng máy đang hiển thị lẫn đang ẩn kèm các cá thể con, rồi bọc trong ServiceResult.
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
     * Lấy thông tin chi tiết một dòng máy theo ID kèm toàn bộ danh sách cá thể máy con thuộc dòng đó.
     * Hàm kiểm tra tính hợp lệ của ID, truy vấn Facility từ FacilityDAO rồi nạp thêm danh sách EquipmentItem của dòng máy.
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
     * Lấy danh sách các ghi chú vị trí đặt máy không trùng lặp để gợi ý tự động hoàn thành (datalist) khi nhập liệu.
     * Hàm ủy quyền cho EquipmentItemDAO truy vấn các PlacementNote duy nhất và trả về danh sách rỗng nếu có lỗi SQL.
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
     * Thêm mới hoặc cập nhật thông tin một dòng máy (nhóm cơ sở vật chất).
     * Hàm kiểm tra độ dài tên, danh mục, đơn vị tính hợp lệ rồi gọi update (khi FacilityId > 0) hoặc create (bật mặc định IsActive = true).
     */
    public ServiceResult<Integer> saveFacility(Facility facility) {
        // B1: Kiểm tra dữ liệu đầu vào
        if (facility == null) {
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }

        String name = facility.getFacilityName();
        if (!ValidationUtil.isLengthValid(name, 3, 100)) {
            return ServiceResult.failField("facilityName", Messages.MSG218_FACILITY_NAME_LENGTH);
        }
        facility.setFacilityName(name.trim());

        if (facility.getCategory() == null) {
            return ServiceResult.failField("category", Messages.MSG219_FACILITY_CATEGORY_REQUIRED);
        }

        String location = facility.getLocation() != null ? facility.getLocation().trim() : "";
        if (location.length() > 100) {
            return ServiceResult.failField("location", Messages.MSG341_FACILITY_LOCATION_LENGTH);
        }
        facility.setLocation(location);

        String unitLabel = facility.getUnitLabel() != null ? facility.getUnitLabel().trim() : "";
        if (unitLabel.isEmpty()) {
            facility.setUnitLabel("máy");
        } else if (unitLabel.length() > 15) {
            return ServiceResult.failField("unitLabel", Messages.MSG220_FACILITY_UNIT_LENGTH);
        } else {
            facility.setUnitLabel(unitLabel);
        }

        // B2: Gọi DAO để cập nhật hoặc tạo mới trong CSDL
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
     * Bật hiển thị hoặc ẩn một dòng máy khỏi danh mục chọn của Lễ tân.
     * Hàm kiểm tra facilityId > 0 rồi gọi FacilityDAO.toggleActive để cập nhật cột IsActive.
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
     * Thêm mới hoặc cập nhật thông tin một cá thể máy vật lý.
     * Hàm kiểm tra mã máy (tự chuẩn hóa chữ in hoa), nhãn máy, ghi chú vị trí và kiểm tra trùng ItemCode, ItemLabel trong nhóm trước khi gọi create hoặc update qua EquipmentItemDAO.
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
        item.setPlacementNote(placement != null && !placement.isBlank() ? placement.trim() : null);

        Integer excludeId = item.getEquipmentItemId() > 0 ? item.getEquipmentItemId() : null;
        try {
            // Kiểm tra trùng mã cá thể máy trong CSDL
            if (equipmentItemDAO.isItemCodeExists(item.getItemCode(), excludeId)) {
                return duplicateEquipmentField("itemCode", Messages.MSG228_ITEM_CODE_EXISTS, item.getItemCode());
            }
            // Kiểm tra trùng nhãn hiển thị trong cùng nhóm máy (khớp UQ_EquipmentItems_FacilityLabel)
            if (equipmentItemDAO.isItemLabelExists(item.getFacilityId(), item.getItemLabel(), excludeId)) {
                return duplicateEquipmentField("itemLabel", Messages.MSG342_ITEM_LABEL_EXISTS, item.getItemLabel());
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
            ServiceResult<Integer> duplicateError = resolveEquipmentDuplicateError(e, item, excludeId);
            if (duplicateError != null) {
                return duplicateError;
            }
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /**
     * Chuyển đổi lỗi trùng khóa duy nhất SQL Server (2601/2627) trên bảng EquipmentItems
     * thành thông báo lỗi cụ thể theo trường dữ liệu thay vì trả thông báo chung MSG04.
     */
    private ServiceResult<Integer> resolveEquipmentDuplicateError(
            SQLException e, EquipmentItem item, Integer excludeId) {
        if (e.getErrorCode() != 2601 && e.getErrorCode() != 2627) {
            return null;
        }
        String message = e.getMessage() != null ? e.getMessage() : "";
        if (message.contains("UQ_EquipmentItems_FacilityLabel")) {
            return duplicateEquipmentField("itemLabel", Messages.MSG342_ITEM_LABEL_EXISTS, item.getItemLabel());
        }
        if (message.contains("UQ_EquipmentItems_ItemCode")) {
            return duplicateEquipmentField("itemCode", Messages.MSG228_ITEM_CODE_EXISTS, item.getItemCode());
        }
        try {
            if (equipmentItemDAO.isItemLabelExists(item.getFacilityId(), item.getItemLabel(), excludeId)) {
                return duplicateEquipmentField("itemLabel", Messages.MSG342_ITEM_LABEL_EXISTS, item.getItemLabel());
            }
            if (equipmentItemDAO.isItemCodeExists(item.getItemCode(), excludeId)) {
                return duplicateEquipmentField("itemCode", Messages.MSG228_ITEM_CODE_EXISTS, item.getItemCode());
            }
        } catch (SQLException ignored) {
            // Bỏ qua để fallback về thông báo hệ thống chung phía caller
        }
        return null;
    }

    private ServiceResult<Integer> duplicateEquipmentField(String fieldName, String messageTemplate, String value) {
        return ServiceResult.failField(fieldName, String.format(messageTemplate, value));
    }

    /**
     * Thanh lý một cá thể máy vật lý (chuyển sang trạng thái 'Retired').
     * Hàm kiểm tra an toàn trước khi thanh lý: từ chối nếu máy vẫn còn phiếu bảo trì đang mở hoặc đang xử lý, ngược lại gọi EquipmentItemDAO.retire.
     */
    public ServiceResult<Void> retireEquipmentItem(int equipmentItemId) {
        if (equipmentItemId <= 0) {
            return ServiceResult.fail(Messages.MSG231_ITEM_ID_INVALID);
        }
        try {
            if (maintenanceRequestDAO.hasActiveRequest(equipmentItemId)) {
                return ServiceResult.fail(Messages.MSG343_ITEM_RETIRE_HAS_MAINTENANCE);
            }
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
