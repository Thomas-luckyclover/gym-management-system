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
 * Servlet danh sách Blog công khai (URL /blog).
 *
 * <p>Hỗ trợ ?q= từ khóa, ?category= chuyên mục, ?page số trang; 6 bài/trang, chỉ bài đang đăng.</p>
 */
@WebServlet("/blog")
public class BlogListServlet extends HttpServlet {

    private final BlogService blogService;

    public BlogListServlet() {
        this(new BlogService());
    }

    public BlogListServlet(BlogService blogService) {
        this.blogService = blogService;
    }

    /**
     * GET /blog — hiện danh sách bài viết theo từ khóa/chuyên mục/trang; mặc định trang 1, sắp xếp mới nhất.
     */
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
