package com.gms.servlet.guest;

import com.gms.constant.Messages;
import com.gms.dto.BlogPostDetailView;
import com.gms.dto.ServiceResult;
import com.gms.service.BlogService;
import com.gms.util.RequestUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Controller xử lý màn hình Blog Details (Màn SRS #7 / UC-06).
 *
 * <p>Tiếp nhận request GET /blog/detail?id=..., gọi BlogService để truy vấn chi tiết bài viết
 * kèm thời gian đọc và danh sách bài viết liên quan cùng chuyên mục.</p>
 *
 * <p>Quy tắc bảo mật & phân quyền (Phase 1 Rules / CBR-100 / Kịch bản B10-B11):</p>
 * <ul>
 *   <li>Trang công khai cho Guest và mọi vai trò, không yêu cầu đăng nhập (GBR-10).</li>
 *   <li>Chỉ hiển thị bài viết đang hoạt động (IsActive = 1).</li>
 *   <li>Nếu bài viết không tồn tại, đã gỡ hoặc id không hợp lệ: không để lộ nội dung,
 *       gắn Flash Toast cảnh báo và chuyển hướng an toàn về trang danh sách /blog.</li>
 * </ul>
 */
@WebServlet("/blog/detail")
public class BlogDetailServlet extends HttpServlet {

    private final BlogService blogService;

    /**
     * Constructor mặc định khởi tạo BlogService.
     */
    public BlogDetailServlet() {
        this(new BlogService());
    }

    /**
     * Constructor phục vụ dependency injection khi kiểm thử.
     *
     * @param blogService đối tượng BlogService
     */
    public BlogDetailServlet(BlogService blogService) {
        this.blogService = blogService;
    }

    // ===== Điều phối hiển thị chi tiết bài viết Blog =====

    /**
     * Xử lý request GET xem chi tiết bài viết Blog.
     *
     * @param request HttpServletRequest
     * @param response HttpServletResponse
     * @throws ServletException nếu có lỗi điều hướng servlet
     * @throws IOException nếu có lỗi I/O
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        // B1: đọc tham số id bài viết từ request (sử dụng RequestUtil)
        int blogPostId = RequestUtil.getIntParam(request, "id", 0);
        if (blogPostId <= 0) {
            RequestUtil.setFlashToast(request, "warning", Messages.MSG73_POST_UNAVAILABLE);
            RequestUtil.redirect(request, response, "/blog");
            return;
        }

        // B2: gọi BlogService lấy chi tiết bài viết đang đăng
        ServiceResult<BlogPostDetailView> result = blogService.getActiveBlogDetail(blogPostId);

        // B3: kiểm tra kết quả truy vấn bài viết
        if (!result.isSuccess()) {
            RequestUtil.setFlashToast(request, "warning", result.getMessage());
            RequestUtil.redirect(request, response, "/blog");
            return;
        }

        // B4: đóng gói dữ liệu vào request và chuyển tiếp tới View JSP
        BlogPostDetailView detailView = result.getData();
        request.setAttribute("detailView", detailView);
        request.setAttribute("post", detailView.getPost());
        request.setAttribute("categoryDisplayName", blogService.getCategoryDisplayName(detailView.getPost().getCategory()));

        RequestUtil.forward(request, response, "guest/blog-detail");
    }
}
