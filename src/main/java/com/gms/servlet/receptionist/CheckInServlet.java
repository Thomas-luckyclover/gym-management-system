package com.gms.servlet.receptionist;

import com.gms.constant.Messages;
import com.gms.constant.SessionKey;
import com.gms.dto.CheckInLogItem;
import com.gms.dto.CheckInLookupResult;
import com.gms.dto.CustomerSearchItem;
import com.gms.dto.PageResult;
import com.gms.dto.ServiceResult;
import com.gms.service.CheckInService;
import com.gms.util.PaginationUtil;
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
 * Servlet màn hình Check-in hội viên tại quầy Lễ tân (URL
 * /receptionist/check-in).
 *
 * <p>
 * GET: tra cứu hội viên (mã/tên/SĐT) và xem danh sách lượt vào ca hôm nay.
 * </p>
 * <p>
 * POST: xác nhận cho hội viên vào tập hoặc lưu ảnh check-in chụp tại quầy.
 * </p>
 */
@WebServlet("/receptionist/check-in")
@MultipartConfig(maxFileSize = 5 * 1024 * 1024, // 5MB (DBR-08)
        maxRequestSize = 6 * 1024 * 1024)
public class CheckInServlet extends HttpServlet {

    private static final String VIEW_NAME = "receptionist/check-in";
    private static final String ACTION_CONFIRM_CHECK_IN = "confirmCheckIn";
    private static final String ACTION_SAVE_PHOTO = "savePhoto";

    private final CheckInService checkInService = new CheckInService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String query = RequestUtil.getTrimmedParam(request, "q");
        // lay gia tri cua tham so q tu request
        // gia tri q la cai ma minh nhap trong thanh tim kiem o giao dien
        int page = PaginationUtil.getPageNumber(request);
        // lay so trang hien tai tu request phuc phu cho viec phan trang
        int pageSize = 10; // Phân trang 10 khách mỗi trang để vừa vặn màn hình Check-in

        if (!ValidationUtil.isNullOrEmpty(query)) {// neu chuoi q khong rong
            ServiceResult<CheckInLookupResult> result = checkInService.lookupCustomer(query);// goi ham lookupCustomer
                                                                                             // de tim kiem thong tin
                                                                                             // khach hang
            if (result.isSuccess()) {// neu tim kiem thanh cong
                CheckInLookupResult lookupData = result.getData();
                //lấy dữ liệu kèm theo khi thực hiện tra cứu (CheckInLookupResult) từ ServiceResult

                // Nếu exact match không tìm thấy (Mã KH / SĐT) → tìm theo một phần tên có phân
                // trang
                if (lookupData.getDecision() == CheckInLookupResult.Decision.NOT_FOUND) {//dòng 73 service
                    PageResult<CustomerSearchItem> searchResultsPage = checkInService.searchCustomersPage(query, page,
                            pageSize);

                    if (!searchResultsPage.isEmpty()) {//nếu danh sách kết quả tìm kiếm theo 1 phần tên không rỗng thì không đóng gói lookupResult
                        //ở đây lookupResult sẽ là null, chỉ có searchResultsPage được set vào request để jsp hiển thị danh sách khách hàng
                        //
                        request.setAttribute("searchResultsPage", searchResultsPage);
                        // dong goi thong tin ket qua tim kiem va phan trang (jsp dung searchResultsPage.items)
                    } else {// neu danh sach ket qua tim kiem rong
                        // Không tìm thấy khách nào
                        request.setAttribute("lookupResult", lookupData);
                    }
                } else {// neu tim thay khach hang
                    request.setAttribute("lookupResult", lookupData);// set thong tin khach hang
                }
            } else {// neu tim kiem that bai
                request.setAttribute("searchError", result.getMessage());// set thong tin loi
            }
            request.setAttribute("query", query);// dong goi du lieu gui lai trang jsp
        }

        // Lấy danh sách lượt vào ca hôm nay
        List<CheckInLogItem> todayLogs = checkInService.getTodayLog(20);
        request.setAttribute("todayLogs", todayLogs);
        request.setAttribute("activeNav", "checkin");

        RequestUtil.forward(request, response, VIEW_NAME);// chuyen tiep du lieu tu servlet sang jsp de hien thi cho
                                                          // nguoi dung
    }

    /**
     * POST /receptionist/check-in — xác nhận cho hội viên vào tập hoặc lưu ảnh
     * check-in (theo action).
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);// "false" có nghĩa là nếu không có session thì không tạo
                                                        // session mới
        if (session == null || session.getAttribute(SessionKey.ACCOUNT_ID) == null) {// nếu không có session hoặc không
                                                                                     // có id trong session
            // session == null xảy ra khi chưa đăng nhập, đang xuất hoặc phiên làm việc hết
            // hạn
            // session.getAttribute(SessionKey.ACCOUNT_ID) == null phân biệt giữa khách vãng
            // lai và khách đã đăng nhập
            RequestUtil.redirect(request, response, "/login");// chuyển hướng về trang login
            return;
        }

        int accountId = (Integer) session.getAttribute(SessionKey.ACCOUNT_ID);// lấy id của account từ session
        String action = RequestUtil.getTrimmedParam(request, "action");// đọc tham số action từ form bên jsp để biết ng
                                                                       // dùng muốn làm gì

        if (ACTION_CONFIRM_CHECK_IN.equals(action)) {// nếu action là confirm check in
            handleConfirmCheckIn(request, response, accountId);
            return;
        }

        if (ACTION_SAVE_PHOTO.equals(action)) {// nếu action là save photo
            handleSavePhoto(request, response);
            return;
        }

        RequestUtil.setFlashToast(request, "error", Messages.MSG04_SYSTEM_BUSY);
        // nếu action không phải là confirm check in hoặc save photo
        // người dùng cố tình sửa 1 action không hợp lệ trong http request
        // lỗi mạng hoặc form gửi thiếu trường dữ liệu
        RequestUtil.redirect(request, response, "/receptionist/check-in");// chuyển hướng về trang check in
    }

    /**
     * Xử lý xác nhận cho hội viên vào tập.
     */
    private void handleConfirmCheckIn(HttpServletRequest request, HttpServletResponse response, int accountId)
            throws IOException {

        int subscriptionId = RequestUtil.getIntParam(request, "subscriptionId", 0);
        String customerName = RequestUtil.getTrimmedParam(request, "customerName");
        boolean photoVerified = "true".equalsIgnoreCase(RequestUtil.getTrimmedParam(request, "photoVerified"));

        if (subscriptionId <= 0) { // Kiểm tra ID gói tập hợp lệ phòng ngừa can thiệp dữ liệu
            RequestUtil.setFlashToast(request, "error", "Thông tin gói tập không hợp lệ.");
            RequestUtil.redirect(request, response, "/receptionist/check-in");
            return;
        }

        ServiceResult<Void> result = checkInService.performCheckIn(subscriptionId, accountId, photoVerified);
        // thực hiện checkin, lưu thông tin id gói tập, id tk lễ tân đã checkin, ảnh
        // check-in xác thực đã hợp lệ
        if (result.isSuccess()) {// nếu checkin thành công
            String nameText = !ValidationUtil.isNullOrEmpty(customerName) ? customerName : "Hội viên";
            // nếu tên khách không rỗng thì sử dụng tên khách, nếu rỗng thì sử dụng "Hội viên"
            RequestUtil.setFlashToast(request, "success", nameText + " đã vào tập thành công.");// thông báo thành công
            RequestUtil.redirect(request, response, "/receptionist/check-in");
        } else {// nếu checkin thất bại
            // lễ tân k tồn tại trong csdl, khi đang checkin mà quản lý xóa
            // message trả về "không tìm thấy tài khoản nhân viên"
            // mất kết nối với database
            // message trả về "hệ thông đang bận. Vui lòng thử lại sau"
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

        if (customerId <= 0) { // Kiểm tra ID khách hàng hợp lệ phòng ngừa can thiệp dữ liệu
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
            // Ảnh mẫu dự phòng khi máy thực hành không có camera vật lý
            photoPath = "https://images.unsplash.com/photo-1524504388940-b1c1722653e1?w=240&h=300&fit=crop&q=80";
        }

        ServiceResult<Void> updateResult = checkInService.updateCheckInPhoto(customerId, photoPath);

        if (updateResult.isSuccess()) {
            RequestUtil.setFlashToast(request, "success", "Đã lưu ảnh check-in vào hồ sơ hội viên.");
            String redirectUrl = "/receptionist/check-in"
                    + (!ValidationUtil.isNullOrEmpty(customerCode) ? "?q=" + customerCode : "");
            RequestUtil.redirect(request, response, redirectUrl);
        } else {
            RequestUtil.setFlashToast(request, "error", updateResult.getMessage());
            RequestUtil.redirect(request, response, "/receptionist/check-in");
        }
    }
}
