package com.gms.servlet.guest;

import com.gms.dto.PageResult;
import com.gms.dto.ServiceResult;
import com.gms.entity.BlogPost;
import com.gms.service.BlogService;
import com.gms.util.PaginationUtil;
import com.gms.util.RequestUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;

/**
 * Controller xử lý màn hình Blog List (Màn SRS #6 / UC-06).
 *
 * <p>Tiếp nhận request GET /blog?q=...&category=...&page=..., gọi BlogService để truy vấn
 * danh sách bài viết đang đăng có phân trang (6 bài/trang) và lọc theo chuyên mục/từ khóa.</p>
 *
 * <p>Quy tắc nghiệp vụ & phân quyền (Phase 1 Rules / CBR-100 / Kịch bản B01-B07, B11):</p>
 * <ul>
 *   <li>Trang công khai cho Guest và mọi vai trò, không yêu cầu đăng nhập (GBR-10).</li>
 *   <li>Chỉ hiển thị bài viết đang hoạt động (IsActive = 1).</li>
 *   <li>6 bài viết mỗi trang, sắp xếp mới nhất lên trước.</li>
 *   <li>Tìm kiếm theo từ khóa trong tiêu đề hoặc tóm tắt (không phân biệt hoa thường và dấu).</li>
 *   <li>Lọc 1 chuyên mục: Tất cả / Tập luyện / Dinh dưỡng / Tin tức (kết hợp AND với từ khóa).</li>
 *   <li>Đổi từ khóa hoặc chuyên mục thì đặt lại về trang 1.</li>
 *   <li>Nếu không có kết quả phù hợp thì hiện thông báo MSG01.</li>
 * </ul>
 */
@WebServlet("/blog")
public class BlogListServlet extends HttpServlet {

    private final BlogService blogService;

    /**
     * Constructor mặc định khởi tạo BlogService.
     */
    public BlogListServlet() {
        this(new BlogService());
    }

    /**
     * Constructor phục vụ dependency injection khi kiểm thử.
     *
     * @param blogService đối tượng BlogService
     */
    public BlogListServlet(BlogService blogService) {
        this.blogService = blogService;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        // B1: đọc và chuẩn hóa tham số tìm kiếm, lọc chuyên mục và phân trang
        String rawKeyword = RequestUtil.getTrimmedParam(request, "q");
        if (rawKeyword == null) {
            rawKeyword = RequestUtil.getTrimmedParam(request, "keyword");
        }

        String rawCategory = RequestUtil.getTrimmedParam(request, "category");
        int page = PaginationUtil.getPageNumber(request);

        // B2: gọi BlogService lấy danh sách bài viết đang đăng theo trang (6 bài/trang)
        ServiceResult<PageResult<BlogPost>> result = blogService.getActiveBlogPage(
                rawKeyword,
                rawCategory,
                page,
                BlogService.BLOG_PAGE_SIZE
        );

        // B3: đóng gói dữ liệu phân trang và điều kiện lọc
        PageResult<BlogPost> pageResult;
        if (result.isSuccess() && result.getData() != null) {
            pageResult = result.getData();
        } else {
            pageResult = new PageResult<>(Collections.emptyList(), page, BlogService.BLOG_PAGE_SIZE, 0);
        }

        // B4: chuẩn hóa tên hiển thị của chuyên mục được chọn (Tất cả, Tập luyện, Dinh dưỡng, Tin tức)
        String selectedCategory = "Tất cả";
        if (rawCategory != null && !rawCategory.isEmpty() && !"Tất cả".equalsIgnoreCase(rawCategory) && !"All".equalsIgnoreCase(rawCategory)) {
            String norm = blogService.normalizeCategory(rawCategory);
            if (norm != null) {
                selectedCategory = blogService.getCategoryDisplayName(norm);
            }
        }

        // B5: gán thuộc tính vào request cho JSP hiển thị
        request.setAttribute("pageResult", pageResult);
        request.setAttribute("posts", pageResult.getItems());
        request.setAttribute("totalCount", pageResult.getTotalItems());
        request.setAttribute("currentPage", pageResult.getPageNumber());
        request.setAttribute("totalPages", pageResult.getTotalPages());
        request.setAttribute("keyword", rawKeyword != null ? rawKeyword : "");
        request.setAttribute("selectedCategory", selectedCategory);
        request.setAttribute("noResultMsg", result.getMessage());

        // B6: forward sang giao diện JSP danh sách blog
        RequestUtil.forward(request, response, "guest/blog-list");
    }
}
