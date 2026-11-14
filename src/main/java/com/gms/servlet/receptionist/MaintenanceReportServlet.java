package com.gms.servlet.receptionist;

import com.gms.constant.Messages;
import com.gms.dto.ServiceResult;
import com.gms.entity.Account;
import com.gms.entity.EquipmentItem;
import com.gms.entity.Facility;
import com.gms.entity.MaintenanceRequest;
import com.gms.service.MaintenanceRequestService;
import com.gms.util.RequestUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.Part;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

/**
 * Servlet điều hướng và xử lý Bước 2: Mô tả sự cố báo hỏng thiết bị của Lễ tân.
 */
@WebServlet("/receptionist/facilities/report")
@MultipartConfig(
        fileSizeThreshold = 1024 * 1024,      // 1 MB
        maxFileSize = 5 * 1024 * 1024,        // 5 MB theo SRS DBR-08
        maxRequestSize = 25 * 1024 * 1024     // 25 MB
)
public class MaintenanceReportServlet extends HttpServlet {

    private static final String STEP2_VIEW = "receptionist/maintenance-report-step2";

    private final MaintenanceRequestService maintenanceRequestService;

    public MaintenanceReportServlet() {
        this(new MaintenanceRequestService());
    }

    MaintenanceReportServlet(MaintenanceRequestService maintenanceRequestService) {
        this.maintenanceRequestService = maintenanceRequestService;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        // B1: Đọc danh sách mã máy cá thể được chọn từ Bước 1
        String itemIdsParam = RequestUtil.getTrimmedParam(request, "equipmentItemIds");
        java.util.List<Integer> targetItemIds = new java.util.ArrayList<>();

        if (itemIdsParam != null && !itemIdsParam.isBlank()) {
            for (String part : itemIdsParam.split(",")) {
                try {
                    int id = Integer.parseInt(part.trim());
                    if (id > 0 && !targetItemIds.contains(id)) {
                        targetItemIds.add(id);
                    }
                } catch (NumberFormatException ignored) {}
            }
        }

        // Fallback kiểm tra tham số đơn lẻ equipmentItemId
        if (targetItemIds.isEmpty()) {
            int singleId = RequestUtil.getIntParam(request, "equipmentItemId", 0);
            if (singleId > 0) {
                targetItemIds.add(singleId);
            }
        }

        if (targetItemIds.isEmpty()) {
            RequestUtil.setFlashToast(request, "warning", Messages.MSG241_MAINTENANCE_DEVICE_REQUIRED);
            response.sendRedirect(request.getContextPath() + "/receptionist/facilities");
            return;
        }

        // B2: Tải thông tin các máy cá thể và dòng máy cha
        ServiceResult<List<EquipmentItem>> itemsResult = maintenanceRequestService.getSelectableEquipmentItems();
        java.util.List<EquipmentItem> selectedItems = new java.util.ArrayList<>();
        String blockedItemCode = null;

        if (itemsResult.isSuccess() && itemsResult.getData() != null) {
            for (EquipmentItem item : itemsResult.getData()) {
                if (targetItemIds.contains(item.getEquipmentItemId())) {
                    // Chỉ cho vào Bước 2 khi máy đang Khả dụng và chưa có đơn Open/InProgress (BR-105 & GBR-13)
                    if (item.isAvailable()) {
                        selectedItems.add(item);
                    } else if (!item.isRetired() && blockedItemCode == null) {
                        blockedItemCode = item.getItemCode();
                    }
                }
            }
        }

        if (selectedItems.isEmpty()) {
            String warningMsg = (blockedItemCode != null)
                    ? String.format(Messages.MSG344_MAINTENANCE_ITEM_BUSY, blockedItemCode)
                    : Messages.MSG242_MAINTENANCE_DEVICE_INACTIVE;
            RequestUtil.setFlashToast(request, "warning", warningMsg);
            response.sendRedirect(request.getContextPath() + "/receptionist/facilities");
            return;
        }

        EquipmentItem firstItem = selectedItems.get(0);
        Facility facility = null;
        ServiceResult<Facility> facResult = maintenanceRequestService.getFacilityById(firstItem.getFacilityId());
        if (facResult.isSuccess()) {
            facility = facResult.getData();
        }

        // Nối danh sách ID thành chuỗi lưu trữ
        java.util.List<String> idStrings = new java.util.ArrayList<>();
        for (EquipmentItem item : selectedItems) {
            idStrings.add(String.valueOf(item.getEquipmentItemId()));
        }
        String joinedItemIds = String.join(",", idStrings);

        request.setAttribute("selectedItems", selectedItems);
        request.setAttribute("selectedItem", firstItem);
        request.setAttribute("equipmentItemIds", joinedItemIds);
        request.setAttribute("facility", facility);
        request.setAttribute("incidentTypeOptions", MaintenanceRequest.IncidentType.values());
        request.setAttribute("priorityOptions", MaintenanceRequest.Priority.values());

        HttpSession session = request.getSession(true);
        String csrfToken = RequestUtil.getOrCreateCsrfToken(session);
        request.setAttribute("csrfToken", csrfToken);

        RequestUtil.forward(request, response, STEP2_VIEW);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute(com.gms.constant.SessionKey.ACCOUNT_ID) == null) {
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }
        int accountId = (Integer) session.getAttribute(com.gms.constant.SessionKey.ACCOUNT_ID);

        try {
            handlePostRequest(request, response, accountId);
        } catch (IllegalStateException ex) {
            // Khi file vượt ngưỡng maxFileSize (5 MB), Tomcat ném IllegalStateException ngay khi đọc param/part
            if (RequestUtil.isMultipartRequest(request)) {
                RequestUtil.setFlashToast(request, "danger", String.format(Messages.MSG20_INVALID_IMAGE, "5 MB"));
                response.sendRedirect(request.getContextPath() + "/receptionist/facilities");
                return;
            }
            throw ex;
        }
    }

    private void handlePostRequest(HttpServletRequest request, HttpServletResponse response, int accountId)
            throws ServletException, IOException {
        // Kiểm tra CSRF token
        if (!RequestUtil.isValidCsrfToken(request)) {
            RequestUtil.setFlashToast(request, "danger", Messages.MSG214_FORM_SESSION_EXPIRED);
            response.sendRedirect(request.getContextPath() + "/receptionist/facilities");
            return;
        }

        // Đọc danh sách thiết bị cần tạo phiếu
        String itemIdsParam = RequestUtil.getTrimmedParam(request, "equipmentItemIds");
        java.util.List<Integer> itemIds = new java.util.ArrayList<>();

        if (itemIdsParam != null && !itemIdsParam.isBlank()) {
            for (String part : itemIdsParam.split(",")) {
                try {
                    int id = Integer.parseInt(part.trim());
                    if (id > 0 && !itemIds.contains(id)) {
                        itemIds.add(id);
                    }
                } catch (NumberFormatException ignored) {}
            }
        }

        if (itemIds.isEmpty()) {
            int singleId = RequestUtil.getIntParam(request, "equipmentItemId", 0);
            if (singleId > 0) {
                itemIds.add(singleId);
            }
        }

        if (itemIds.isEmpty()) {
            RequestUtil.setFlashToast(request, "warning", Messages.MSG241_MAINTENANCE_DEVICE_REQUIRED);
            response.sendRedirect(request.getContextPath() + "/receptionist/facilities");
            return;
        }

        String incidentType = RequestUtil.getTrimmedParam(request, "incidentType");
        String priority = RequestUtil.getTrimmedParam(request, "priority");
        String description = RequestUtil.getTrimmedParam(request, "description");
        String affectedItemsNote = RequestUtil.getTrimmedParam(request, "affectedItemsNote");
        boolean warningTagPlaced = "true".equalsIgnoreCase(request.getParameter("warningTagPlaced"))
                || "on".equalsIgnoreCase(request.getParameter("warningTagPlaced"))
                || "1".equals(request.getParameter("warningTagPlaced"));

        // Kiểm tra độ dài ghi chú thiết bị ảnh hưởng (tối đa 100 ký tự theo SRS BR-105, DBR-15)
        if (affectedItemsNote != null && affectedItemsNote.length() > 100) {
            forwardStep2WithError(request, response, String.format(Messages.MSG314_NOTE_MAX_LENGTH, 100), null,
                    itemIdsParam, incidentType, priority, description, affectedItemsNote, warningTagPlaced);
            return;
        }

        // Xử lý upload ảnh (nếu có - tối đa 5 MB theo SRS DBR-08)
        String attachedImages = null;
        Part filePart;
        try {
            filePart = request.getPart("imageFile");
        } catch (IllegalStateException ex) {
            forwardStep2WithError(request, response, String.format(Messages.MSG20_INVALID_IMAGE, "5 MB"), null,
                    itemIdsParam, incidentType, priority, description, affectedItemsNote, warningTagPlaced);
            return;
        } catch (ServletException ignored) {
            filePart = null;
        }

        if (filePart != null && filePart.getSize() > 0) {
            if (filePart.getSize() > com.gms.constant.AppConstants.IMAGE_MAX_BYTES) {
                forwardStep2WithError(request, response, String.format(Messages.MSG20_INVALID_IMAGE, "5 MB"), null,
                        itemIdsParam, incidentType, priority, description, affectedItemsNote, warningTagPlaced);
                return;
            }
            String submittedName = filePart.getSubmittedFileName() != null
                    ? Paths.get(filePart.getSubmittedFileName()).getFileName().toString()
                    : "";
            String ext = "";
            int dotIdx = submittedName.lastIndexOf('.');
            if (dotIdx >= 0) {
                ext = submittedName.substring(dotIdx).toLowerCase();
            }
            if (ext.equals(".jpg") || ext.equals(".jpeg") || ext.equals(".png")) {
                String uploadDir = getServletContext().getRealPath("/uploads/maintenance");
                File dir = new File(uploadDir);
                if (!dir.exists()) {
                    dir.mkdirs();
                }
                String newFilename = "mt_" + UUID.randomUUID().toString().substring(0, 8) + ext;
                filePart.write(uploadDir + File.separator + newFilename);
                attachedImages = "/uploads/maintenance/" + newFilename;
            } else {
                forwardStep2WithError(request, response, String.format(Messages.MSG20_INVALID_IMAGE, "5 MB"), null,
                        itemIdsParam, incidentType, priority, description, affectedItemsNote, warningTagPlaced);
                return;
            }
        }

        // Gọi Service tạo phiếu bảo trì (tạo đồng loạt nếu chọn nhiều máy)
        ServiceResult<List<String>> result = maintenanceRequestService.createRequests(
                accountId,
                itemIds,
                priority,
                incidentType,
                "Receptionist",
                description,
                warningTagPlaced,
                attachedImages,
                affectedItemsNote
        );

        if (result.isSuccess()) {
            List<String> createdCodes = result.getData();
            String codesSummary;
            if (createdCodes == null || createdCodes.isEmpty()) {
                codesSummary = "";
            } else if (createdCodes.size() <= 5) {
                codesSummary = String.join(", ", createdCodes);
            } else {
                codesSummary = createdCodes.size() + " phiếu (" + createdCodes.get(0) + " → " + createdCodes.get(createdCodes.size() - 1) + ")";
            }
            RequestUtil.setFlashToast(request, "success", String.format(Messages.MSG243_MAINTENANCE_SENT, codesSummary));
            response.sendRedirect(request.getContextPath() + "/receptionist/maintenance-requests");
        } else {
            forwardStep2WithError(request, response, result.getMessage(), result.getFieldErrors(),
                    itemIdsParam, incidentType, priority, description, affectedItemsNote, warningTagPlaced);
        }
    }

    private void forwardStep2WithError(
            HttpServletRequest request,
            HttpServletResponse response,
            String formError,
            java.util.Map<String, String> fieldErrors,
            String itemIdsParam,
            String incidentType,
            String priority,
            String description,
            String affectedItemsNote,
            boolean warningTagPlaced
    ) throws ServletException, IOException {
        request.setAttribute("formError", formError);
        if (fieldErrors != null) {
            request.setAttribute("fieldErrors", fieldErrors);
        }
        request.setAttribute("equipmentItemIds", itemIdsParam);
        request.setAttribute("incidentType", incidentType);
        request.setAttribute("priority", priority);
        request.setAttribute("description", description);
        request.setAttribute("affectedItemsNote", affectedItemsNote);
        request.setAttribute("warningTagPlaced", warningTagPlaced);
        doGet(request, response);
    }
}
