package com.gms.servlet.manager;

import com.gms.constant.SessionKey;
import com.gms.dto.ServiceResult;
import com.gms.entity.EquipmentItem;
import com.gms.entity.Facility;
import com.gms.service.FacilityService;
import com.gms.util.RequestUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.util.List;

/**
 * Controller: Quản lý danh mục cơ sở vật chất & cá thể thiết bị vật lý (Dành cho Quản lý phòng gym).
 * Hỗ trợ Master - Detail: Nhóm máy bên trái, danh sách cá thể máy bên phải.
 */
@WebServlet(urlPatterns = {"/manager/facilities", "/facilities"})
public class FacilityManagementServlet extends HttpServlet {

    private final FacilityService facilityService = new FacilityService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        // B1: Xác thực session (phân quyền khu /manager/* do RoleAreaFilter kiểm tra)
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute(SessionKey.ACCOUNT_ID) == null) {
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }

        // B2: Lấy toàn bộ danh sách nhóm máy
        ServiceResult<List<Facility>> facilitiesResult = facilityService.getAllFacilitiesForManagement();
        List<Facility> facilities = facilitiesResult.getData();

        // B3: Xác định nhóm máy đang được chọn xem chi tiết (mặc định lấy phần tử đầu tiên)
        int selectedId = RequestUtil.getIntParam(request, "selectedId", 0);
        Facility selectedFacility = null;

        if (facilities != null && !facilities.isEmpty()) {
            if (selectedId > 0) {
                for (Facility f : facilities) {
                    if (f.getFacilityId() == selectedId) {
                        selectedFacility = f;
                        break;
                    }
                }
            }
            if (selectedFacility == null) {
                selectedFacility = facilities.get(0);
                selectedId = selectedFacility.getFacilityId();
            }
        }

        // B4: Lấy danh sách gợi ý vị trí PlacementNote cho thẻ <datalist>
        List<String> placementSuggestions = facilityService.getPlacementSuggestions();

        // B5: Thiết lập attributes hiển thị
        String csrfToken = RequestUtil.getOrCreateCsrfToken(session);
        request.setAttribute("csrfToken", csrfToken);
        request.setAttribute("facilities", facilities);
        request.setAttribute("selectedFacility", selectedFacility);
        request.setAttribute("selectedId", selectedId);
        request.setAttribute("placementSuggestions", placementSuggestions);
        request.setAttribute("categories", Facility.Category.values());
        request.setAttribute("activeNav", "facilities");
        request.setAttribute("pageTitle", "Quản lý cơ sở vật chất");

        request.getRequestDispatcher("/WEB-INF/jsp/manager/facilities.jsp")
                .forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        // B1: Xác thực session
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute(SessionKey.ACCOUNT_ID) == null) {
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }

        // B2: Kiểm tra CSRF token
        if (!RequestUtil.isValidCsrfToken(request)) {
            RequestUtil.setFlashToast(request, "danger", "Phiên làm việc đã hết hạn hoặc không hợp lệ.");
            response.sendRedirect(request.getContextPath() + "/manager/facilities");
            return;
        }

        String action = RequestUtil.getTrimmedParam(request, "action");
        if (action == null) {
            action = "";
        }

        switch (action) {
            case "saveFacility" -> handleSaveFacility(request, response);
            case "toggleFacility" -> handleToggleFacility(request, response);
            case "saveItem" -> handleSaveEquipmentItem(request, response);
            case "retireItem" -> handleRetireEquipmentItem(request, response);
            default -> {
                RequestUtil.setFlashToast(request, "warning", "Thao tác không được hỗ trợ.");
                response.sendRedirect(request.getContextPath() + "/manager/facilities");
            }
        }
    }

    private void handleSaveFacility(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        int facilityId = RequestUtil.getIntParam(request, "facilityId", 0);
        String name = RequestUtil.getTrimmedParam(request, "facilityName");
        String categoryStr = RequestUtil.getTrimmedParam(request, "category");
        String location = RequestUtil.getTrimmedParam(request, "location");
        String unitLabel = RequestUtil.getTrimmedParam(request, "unitLabel");
        String imageUrl = RequestUtil.getTrimmedParam(request, "imageUrl");

        Facility f = new Facility();
        f.setFacilityId(facilityId);
        f.setFacilityName(name);
        f.setCategory(Facility.Category.fromDbValue(categoryStr));
        f.setLocation(location);
        f.setUnitLabel(unitLabel != null && !unitLabel.isBlank() ? unitLabel : "máy");
        f.setImageUrl(imageUrl);
        f.setActive(true);

        ServiceResult<Integer> result = facilityService.saveFacility(f);
        if (result.isSuccess()) {
            int savedId = result.getData();
            RequestUtil.setFlashToast(request, "success",
                    facilityId > 0 ? "Cập nhật nhóm máy thành công!" : "Thêm mới nhóm máy thành công!");
            response.sendRedirect(request.getContextPath() + "/manager/facilities?selectedId=" + savedId);
        } else {
            RequestUtil.setFlashToast(request, "danger", result.getMessage());
            response.sendRedirect(request.getContextPath() + "/manager/facilities" + (facilityId > 0 ? "?selectedId=" + facilityId : ""));
        }
    }

    private void handleToggleFacility(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        int facilityId = RequestUtil.getIntParam(request, "facilityId", 0);
        boolean isActive = "true".equalsIgnoreCase(request.getParameter("isActive"))
                || "1".equals(request.getParameter("isActive"));

        ServiceResult<Void> result = facilityService.toggleFacilityActive(facilityId, isActive);
        if (result.isSuccess()) {
            RequestUtil.setFlashToast(request, "success",
                    isActive ? "Đã hiển thị nhóm máy vào danh mục vận hành." : "Đã tạm dừng / ẩn nhóm máy.");
        } else {
            RequestUtil.setFlashToast(request, "danger", result.getMessage());
        }
        response.sendRedirect(request.getContextPath() + "/manager/facilities?selectedId=" + facilityId);
    }

    private void handleSaveEquipmentItem(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        int itemId = RequestUtil.getIntParam(request, "equipmentItemId", 0);
        int facilityId = RequestUtil.getIntParam(request, "facilityId", 0);
        String itemCode = RequestUtil.getTrimmedParam(request, "itemCode");
        String itemLabel = RequestUtil.getTrimmedParam(request, "itemLabel");
        String placementNote = RequestUtil.getTrimmedParam(request, "placementNote");

        EquipmentItem item = new EquipmentItem();
        item.setEquipmentItemId(itemId);
        item.setFacilityId(facilityId);
        item.setItemCode(itemCode);
        item.setItemLabel(itemLabel);
        item.setPlacementNote(placementNote);

        ServiceResult<Integer> result = facilityService.saveEquipmentItem(item);
        if (result.isSuccess()) {
            RequestUtil.setFlashToast(request, "success",
                    itemId > 0 ? "Cập nhật cá thể máy " + itemCode + " thành công!" : "Thêm cá thể máy " + itemCode + " thành công!");
        } else {
            RequestUtil.setFlashToast(request, "danger", result.getMessage());
        }
        response.sendRedirect(request.getContextPath() + "/manager/facilities?selectedId=" + facilityId);
    }

    private void handleRetireEquipmentItem(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        int itemId = RequestUtil.getIntParam(request, "equipmentItemId", 0);
        int facilityId = RequestUtil.getIntParam(request, "facilityId", 0);

        ServiceResult<Void> result = facilityService.retireEquipmentItem(itemId);
        if (result.isSuccess()) {
            RequestUtil.setFlashToast(request, "success", "Đã thanh lý cá thể máy.");
        } else {
            RequestUtil.setFlashToast(request, "danger", result.getMessage());
        }
        response.sendRedirect(request.getContextPath() + "/manager/facilities?selectedId=" + facilityId);
    }
}
