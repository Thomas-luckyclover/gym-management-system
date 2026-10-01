package com.gms.servlet.receptionist;

import com.gms.constant.Messages;
import com.gms.constant.SessionKey;
import com.gms.dto.CheckInLogItem;
import com.gms.dto.CheckInLookupResult;
import com.gms.dto.ServiceResult;
import com.gms.service.CheckInService;
import com.gms.util.RequestUtil;
import com.gms.util.ValidationUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.Part;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;

/**
 * Controller màn hình Check-in hội viên tại quầy Lễ tân (#33, UC-19).
 * URL: /receptionist/check-in
 */
@WebServlet("/receptionist/check-in")
@MultipartConfig(
        maxFileSize = 5 * 1024 * 1024,      // 5MB (DBR-08)
        maxRequestSize = 6 * 1024 * 1024
)
public class CheckInServlet extends HttpServlet {

    private static final String VIEW_NAME = "receptionist/check-in";
    private static final String ACTION_CONFIRM_CHECK_IN = "confirmCheckIn";
    private static final String ACTION_SAVE_PHOTO = "savePhoto";

    private final CheckInService checkInService = new CheckInService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String query = RequestUtil.getTrimmedParam(request, "q");

        if (!ValidationUtil.isNullOrEmpty(query)) {
            ServiceResult<CheckInLookupResult> result = checkInService.lookupCustomer(query);
            if (result.isSuccess()) {
                request.setAttribute("lookupResult", result.getData());
            } else {
                request.setAttribute("searchError", result.getMessage());
            }
            request.setAttribute("query", query);
        }

        // Lấy danh sách lượt vào ca hôm nay
        List<CheckInLogItem> todayLogs = checkInService.getTodayLog(20);
        request.setAttribute("todayLogs", todayLogs);
        request.setAttribute("activeNav", "checkin");

        RequestUtil.forward(request, response, VIEW_NAME);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute(SessionKey.ACCOUNT_ID) == null) {
            RequestUtil.redirect(request, response, "/login");
            return;
        }

        int accountId = (Integer) session.getAttribute(SessionKey.ACCOUNT_ID);
        String action = RequestUtil.getTrimmedParam(request, "action");

        if (ACTION_CONFIRM_CHECK_IN.equals(action)) {
            handleConfirmCheckIn(request, response, accountId);
            return;
        }

        if (ACTION_SAVE_PHOTO.equals(action)) {
            handleSavePhoto(request, response);
            return;
        }

        RequestUtil.setFlashToast(request, "error", Messages.MSG04_SYSTEM_BUSY);
        RequestUtil.redirect(request, response, "/receptionist/check-in");
    }

    /**
     * Xử lý xác nhận cho hội viên vào tập.
     */
    private void handleConfirmCheckIn(HttpServletRequest request, HttpServletResponse response, int accountId)
            throws IOException {

        int subscriptionId = RequestUtil.getIntParam(request, "subscriptionId", 0);
        String customerName = RequestUtil.getTrimmedParam(request, "customerName");
        boolean photoVerified = "true".equalsIgnoreCase(RequestUtil.getTrimmedParam(request, "photoVerified"));

        if (subscriptionId <= 0) {
            RequestUtil.setFlashToast(request, "error", "Thông tin gói tập không hợp lệ.");
            RequestUtil.redirect(request, response, "/receptionist/check-in");
            return;
        }

        ServiceResult<Void> result = checkInService.performCheckIn(subscriptionId, accountId, photoVerified);

        if (result.isSuccess()) {
            String nameText = !ValidationUtil.isNullOrEmpty(customerName) ? customerName : "Hội viên";
            RequestUtil.setFlashToast(request, "success", nameText + " đã vào tập thành công.");
            RequestUtil.redirect(request, response, "/receptionist/check-in");
        } else {
            RequestUtil.setFlashToast(request, "error", result.getMessage());
            RequestUtil.redirect(request, response, "/receptionist/check-in");
        }
    }

    /**
     * Xử lý lưu ảnh check-in chụp tại quầy cho hội viên (CBR-38).
     */
    private void handleSavePhoto(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        int customerId = RequestUtil.getIntParam(request, "customerId", 0);
        String customerCode = RequestUtil.getTrimmedParam(request, "customerCode");

        if (customerId <= 0) {
            RequestUtil.setFlashToast(request, "error", "Không tìm thấy hồ sơ khách cần cập nhật ảnh.");
            RequestUtil.redirect(request, response, "/receptionist/check-in");
            return;
        }

        Part photoPart = request.getPart("photoFile");
        String photoPath;

        if (photoPart != null && photoPart.getSize() > 0) {
            String uploadDirectoryValue = getServletContext().getRealPath("/uploads/checkin");
            if (uploadDirectoryValue == null) {
                uploadDirectoryValue = System.getProperty("java.io.tmpdir") + "/uploads/checkin";
            }

            Path uploadDirectory = Path.of(uploadDirectoryValue);
            Files.createDirectories(uploadDirectory);

            String fileName = "checkin-" + customerId + "-" + UUID.randomUUID() + ".jpg";
            Path newFilePath = uploadDirectory.resolve(fileName).normalize();

            try (InputStream is = photoPart.getInputStream()) {
                Files.copy(is, newFilePath, StandardCopyOption.REPLACE_EXISTING);
            }

            photoPath = "/uploads/checkin/" + fileName;
        } else {
            // Trường hợp chụp nhanh từ webcam giả lập
            photoPath = "https://images.unsplash.com/photo-1524504388940-b1c1722653e1?w=240&h=300&fit=crop&q=80";
        }

        ServiceResult<Void> updateResult = checkInService.updateCheckInPhoto(customerId, photoPath);

        if (updateResult.isSuccess()) {
            RequestUtil.setFlashToast(request, "success", "Đã lưu ảnh check-in vào hồ sơ hội viên.");
            String redirectUrl = "/receptionist/check-in" + (!ValidationUtil.isNullOrEmpty(customerCode) ? "?q=" + customerCode : "");
            RequestUtil.redirect(request, response, redirectUrl);
        } else {
            RequestUtil.setFlashToast(request, "error", updateResult.getMessage());
            RequestUtil.redirect(request, response, "/receptionist/check-in");
        }
    }
}
