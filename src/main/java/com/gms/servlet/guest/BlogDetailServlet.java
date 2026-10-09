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
 * Servlet chi tiết bài viết Blog (URL /blog/detail?id=...) — trang công khai.
 *
 * <p>Hiển thị bài viết đang đăng kèm bài liên quan cùng chuyên mục; id lỗi/không tồn tại thì toast cảnh báo và về /blog.</p>
 */
@WebServlet("/blog/detail")
public class BlogDetailServlet extends HttpServlet {

    private final BlogService blogService;

    public BlogDetailServlet() {
        this(new BlogService());
    }

    public BlogDetailServlet(BlogService blogService) {
        this.blogService = blogService;
    }

    /**
     * GET /blog/detail — lấy chi tiết bài viết theo id; nếu không hợp lệ thì về trang danh sách kèm cảnh báo.
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
