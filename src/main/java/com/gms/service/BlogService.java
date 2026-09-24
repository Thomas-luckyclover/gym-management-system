package com.gms.service;

import com.gms.constant.Messages;
import com.gms.dao.BlogPostDAO;
import com.gms.dto.BlogPostDetailView;
import com.gms.dto.PageResult;
import com.gms.dto.ServiceResult;
import com.gms.entity.BlogPost;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

/**
 * Service xử lý toàn bộ nghiệp vụ liên quan đến Blog (SRS 2.2 / UC-06).
 *
 * <p>Nhiệm vụ chính:</p>
 * <ul>
 *   <li>Tìm kiếm, lọc danh mục và phân trang danh sách bài viết đang đăng (6 bài/trang).</li>
 *   <li>Lấy chi tiết bài viết kèm tính toán thời gian đọc và 3 bài viết gợi ý cùng chuyên mục.</li>
 *   <li>Tính thời gian đọc ước tính dựa trên độ dài nội dung (số từ ÷ 200, tối thiểu 1 phút).</li>
 *   <li>Tự động trích xuất tóm tắt (Excerpt) từ nội dung nếu bài viết chưa có tóm tắt (≤ 300 ký tự).</li>
 *   <li>Cung cấp ảnh bìa mặc định theo chuyên mục (Training / Nutrition / News) khi bài viết thiếu ảnh.</li>
 * </ul>
 */
public class BlogService {

    /** Số bài viết hiển thị mỗi trang trên trang danh sách blog công khai (SRS 2.2.1 / Phase 1 Rules). */
    public static final int BLOG_PAGE_SIZE = 6;

    /** Độ dài tối đa cho đoạn tóm tắt bài viết tự động sinh từ nội dung (Phase 1 Rules). */
    public static final int DEFAULT_EXCERPT_MAX_LENGTH = 300;

    /** Tốc độ đọc trung bình tính theo số từ mỗi phút (WPM - Words Per Minute theo SRS 2.2.2). */
    private static final double WORDS_PER_MINUTE = 200.0;

    private final BlogPostDAO blogPostDAO;

    /**
     * Constructor mặc định khởi tạo BlogPostDAO tương ứng.
     */
    public BlogService() {
        this(new BlogPostDAO());
    }

    /**
     * Constructor hỗ trợ dependency injection cho kiểm thử (Unit Test).
     *
     * @param blogPostDAO đối tượng DAO thao tác cơ sở dữ liệu
     */
    public BlogService(BlogPostDAO blogPostDAO) {
        this.blogPostDAO = blogPostDAO;
    }

    // ===== 1. Nghiệp vụ bài viết công khai (Guest) =====

    /**
     * Lấy danh sách bài viết đang đăng (Active) theo từ khóa và chuyên mục có phân trang.
     * Tự động chuẩn hóa chuyên mục, bổ sung tóm tắt và ảnh bìa mặc định nếu thiếu.
     *
     * @param keyword từ khóa tìm kiếm trong tiêu đề hoặc tóm tắt (có thể null)
     * @param category chuyên mục cần lọc (Training / Nutrition / News hoặc tiếng Việt tương ứng; null để lấy hết)
     * @param page số trang hiện tại (bắt đầu từ 1)
     * @param pageSize số bài mỗi trang (mặc định BLOG_PAGE_SIZE = 6)
     * @return ServiceResult chứa PageResult bài viết BlogPost
     */
    public ServiceResult<PageResult<BlogPost>> getActiveBlogPage(String keyword, String category, int page, int pageSize) {
        // B1: chuẩn hóa tham số đầu vào
        int safePage = Math.max(1, page);
        int safePageSize = pageSize > 0 ? pageSize : BLOG_PAGE_SIZE;
        String normalizedCategory = normalizeCategory(category);
        String trimmedKeyword = (keyword != null && !keyword.trim().isEmpty()) ? keyword.trim() : null;

        try {
            // B2: đếm tổng số bài viết thỏa mãn điều kiện lọc
            int totalItems = blogPostDAO.countActive(trimmedKeyword, normalizedCategory);

            // B3: truy vấn danh sách bài viết theo trang
            List<BlogPost> items;
            if (totalItems > 0) {
                items = blogPostDAO.findActivePage(trimmedKeyword, normalizedCategory, safePage, safePageSize);
                for (BlogPost post : items) {
                    enrichPost(post);
                }
            } else {
                items = Collections.emptyList();
            }

            // B4: đóng gói kết quả phân trang, gán MSG01 nếu không có bài viết khớp bộ lọc
            PageResult<BlogPost> pageResult = new PageResult<>(items, safePage, safePageSize, totalItems);
            if (totalItems == 0) {
                return ServiceResult.ok(pageResult, Messages.MSG01_NO_RESULT);
            }
            return ServiceResult.ok(pageResult);

        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /**
     * Lấy danh sách bài viết đang đăng với kích thước trang chuẩn của Blog (6 bài/trang).
     *
     * @param keyword từ khóa tìm kiếm
     * @param category chuyên mục cần lọc
     * @param page số trang hiện tại
     * @return ServiceResult chứa PageResult bài viết
     */
    public ServiceResult<PageResult<BlogPost>> getActiveBlogPage(String keyword, String category, int page) {
        return getActiveBlogPage(keyword, category, page, BLOG_PAGE_SIZE);
    }

    /**
     * Lấy chi tiết bài viết đang đăng theo ID, bao gồm thời gian đọc và tối đa 3 bài viết gợi ý.
     *
     * @param blogPostId ID bài viết cần xem
     * @return ServiceResult chứa BlogPostDetailView nếu tìm thấy; báo lỗi nếu bài viết không tồn tại hoặc đã gỡ
     */
    public ServiceResult<BlogPostDetailView> getActiveBlogDetail(int blogPostId) {
        // B1: kiểm tra ID hợp lệ
        if (blogPostId <= 0) {
            return ServiceResult.fail("Bài viết này không còn hiển thị.");
        }

        try {
            // B2: truy vấn bài viết đang đăng từ cơ sở dữ liệu
            BlogPost post = blogPostDAO.findActiveById(blogPostId);
            if (post == null) {
                return ServiceResult.fail("Bài viết này không còn hiển thị.");
            }

            // B3: bổ sung tóm tắt, ảnh bìa mặc định và tính thời gian đọc
            enrichPost(post);
            int readingTimeMinutes = calculateReadingTime(post.getContent());
            String readingTimeFormatted = formatReadingTime(readingTimeMinutes);

            // B4: lấy tối đa 3 bài gợi ý cùng chuyên mục (trừ bài đang xem)
            List<BlogPost> relatedPosts = blogPostDAO.findRelated(post.getCategory(), post.getBlogPostId(), 3);
            for (BlogPost related : relatedPosts) {
                enrichPost(related);
            }

            // B5: trả về view object hoàn chỉnh
            BlogPostDetailView detailView = new BlogPostDetailView(
                    post,
                    readingTimeMinutes,
                    readingTimeFormatted,
                    relatedPosts
            );
            return ServiceResult.ok(detailView);

        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /**
     * Lấy đối tượng BlogPost đang đăng theo ID.
     *
     * @param blogPostId ID bài viết
     * @return ServiceResult chứa BlogPost
     */
    public ServiceResult<BlogPost> getActiveBlogPostById(int blogPostId) {
        if (blogPostId <= 0) {
            return ServiceResult.fail("Bài viết này không còn hiển thị.");
        }

        try {
            BlogPost post = blogPostDAO.findActiveById(blogPostId);
            if (post == null) {
                return ServiceResult.fail("Bài viết này không còn hiển thị.");
            }
            enrichPost(post);
            return ServiceResult.ok(post);

        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /**
     * Lấy danh sách các bài viết gợi ý liên quan theo chuyên mục.
     *
     * @param category chuyên mục bài viết
     * @param excludeId ID bài viết cần loại trừ
     * @param limit số lượng bài tối đa
     * @return ServiceResult chứa danh sách bài viết
     */
    public ServiceResult<List<BlogPost>> getRelatedPosts(String category, int excludeId, int limit) {
        String normalizedCategory = normalizeCategory(category);
        int safeLimit = limit > 0 ? limit : 3;

        try {
            List<BlogPost> related = blogPostDAO.findRelated(normalizedCategory, excludeId, safeLimit);
            for (BlogPost post : related) {
                enrichPost(post);
            }
            return ServiceResult.ok(related);
        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /**
     * Lấy danh sách các bài viết mới nhất đang hiển thị.
     *
     * @param limit số lượng bài viết mới nhất cần lấy
     * @return ServiceResult chứa danh sách bài viết
     */
    public ServiceResult<List<BlogPost>> getLatestPosts(int limit) {
        int safeLimit = limit > 0 ? limit : 3;

        try {
            List<BlogPost> latest = blogPostDAO.findLatest(safeLimit);
            for (BlogPost post : latest) {
                enrichPost(post);
            }
            return ServiceResult.ok(latest);
        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    // ===== 2. Xử lý nội dung (Thời gian đọc, Tóm tắt, Ảnh bìa) =====

    /**
     * Tính thời gian đọc ước tính dựa trên nội dung bài viết.
     * Quy tắc (SRS 2.2.2 / Phase 1 Rules): loại bỏ thẻ HTML, đếm số từ ÷ 200, làm tròn lên, tối thiểu 1 phút.
     *
     * @param content nội dung bài viết (chứa mã HTML do Manager soạn)
     * @return số phút đọc ước tính (tối thiểu 1 phút)
     */
    public int calculateReadingTime(String content) {
        if (content == null || content.trim().isEmpty()) {
            return 1;
        }

        // B1: loại bỏ toàn bộ thẻ HTML và ký tự khoảng trắng dư thừa
        String plainText = stripHtml(content);
        if (plainText.isEmpty()) {
            return 1;
        }

        // B2: đếm số từ theo khoảng trắng
        String[] words = plainText.split("\\s+");
        int wordCount = words.length;

        // B3: tính số phút đọc = ceil(wordCount / 200), tối thiểu 1 phút
        int minutes = (int) Math.ceil((double) wordCount / WORDS_PER_MINUTE);
        return Math.max(1, minutes);
    }

    /**
     * Định dạng thời gian đọc thành chuỗi hiển thị thân thiện trên giao diện tiếng Việt (GBR-01).
     *
     * @param minutes số phút đọc
     * @return chuỗi hiển thị, VD: "1 phút đọc", "4 phút đọc"
     */
    public String formatReadingTime(int minutes) {
        int safeMinutes = Math.max(1, minutes);
        return safeMinutes + " phút đọc";
    }

    /**
     * Tạo tóm tắt bài viết từ nội dung khi bài viết không có sẵn trường Excerpt.
     * Lọc bỏ thẻ HTML, chuẩn hóa khoảng trắng và lấy tối đa maxLength ký tự,
     * ưu tiên ngắt tại khoảng trắng gần nhất để không cụt từ.
     *
     * @param content nội dung gốc chứa mã HTML
     * @param maxLength độ dài tối đa cho phép (mặc định ≤ 300 ký tự)
     * @return chuỗi tóm tắt văn bản thuần kèm dấu "..." nếu bị cắt ngắn
     */
    public String extractExcerpt(String content, int maxLength) {
        if (content == null || content.trim().isEmpty()) {
            return "";
        }

        // B1: chuyển nội dung HTML sang văn bản thuần (ưu tiên bỏ tiêu đề h1-h6 để lấy đoạn văn)
        String contentWithoutHeadings = content.replaceAll("(?is)<h[1-6][^>]*>.*?</h[1-6]>", " ");
        String plainText = stripHtml(contentWithoutHeadings);
        if (plainText.isEmpty()) {
            plainText = stripHtml(content);
        }
        if (plainText.isEmpty()) {
            return "";
        }

        int limit = maxLength > 0 ? maxLength : DEFAULT_EXCERPT_MAX_LENGTH;
        if (plainText.length() <= limit) {
            return plainText;
        }

        // B2: trừ 3 ký tự cho dấu "..." để đảm bảo tổng độ dài không vượt quá limit
        int cutLimit = limit > 3 ? (limit - 3) : limit;
        String truncated = plainText.substring(0, cutLimit);
        int lastSpaceIndex = truncated.lastIndexOf(' ');
        if (lastSpaceIndex > cutLimit / 2) {
            truncated = truncated.substring(0, lastSpaceIndex);
        }

        return truncated.trim() + "...";
    }

    /**
     * Tạo tóm tắt từ nội dung với độ dài mặc định (300 ký tự theo quy định thẻ bài viết).
     *
     * @param content nội dung bài viết
     * @return đoạn tóm tắt ngắn
     */
    public String extractExcerpt(String content) {
        return extractExcerpt(content, DEFAULT_EXCERPT_MAX_LENGTH);
    }

    /**
     * Lấy đường dẫn ảnh bìa mặc định theo chuyên mục khi bài viết không có CoverImage.
     *
     * @param category chuyên mục bài viết (Training, Nutrition, News hoặc tiếng Việt tương ứng)
     * @return đường dẫn ảnh tĩnh trong thư mục webapp/images/
     */
    public String getDefaultCoverImage(String category) {
        String normalized = normalizeCategory(category);
        if (normalized == null) {
            return "/images/blog/default-blog.jpg";
        }

        switch (normalized) {
            case "Training":
                return "/images/blog/default-training.jpg";
            case "Nutrition":
                return "/images/blog/default-nutrition.jpg";
            case "News":
                return "/images/blog/default-news.jpg";
            default:
                return "/images/blog/default-blog.jpg";
        }
    }

    /**
     * Bổ sung tóm tắt và ảnh bìa mặc định cho bài viết nếu các trường này chưa có giá trị.
     *
     * @param post bài viết BlogPost cần bổ sung
     */
    public void enrichPost(BlogPost post) {
        if (post == null) {
            return;
        }

        // Bổ sung tóm tắt từ nội dung nếu chưa có
        if (post.getExcerpt() == null || post.getExcerpt().trim().isEmpty()) {
            post.setExcerpt(extractExcerpt(post.getContent()));
        }

        // Bổ sung ảnh bìa mặc định theo chuyên mục nếu chưa có
        if (post.getCoverImage() == null || post.getCoverImage().trim().isEmpty()) {
            post.setCoverImage(getDefaultCoverImage(post.getCategory()));
        }
    }

    // ===== 3. Chuẩn hóa & Hỗ trợ hiển thị =====

    /**
     * Chuẩn hóa tên chuyên mục từ người dùng hoặc giao diện thành giá trị chuẩn lưu trong DB:
     * Training | Nutrition | News. Trả về null nếu là "Tất cả", "All", null hoặc chuỗi rỗng.
     *
     * @param category giá trị chuyên mục từ giao diện
     * @return giá trị chuẩn trong DB (Training, Nutrition, News), hoặc null nếu lấy tất cả
     */
    public String normalizeCategory(String category) {
        if (category == null || category.trim().isEmpty()) {
            return null;
        }
        String trimmed = category.trim();
        if ("Tất cả".equalsIgnoreCase(trimmed) || "All".equalsIgnoreCase(trimmed)) {
            return null;
        }
        if ("Tập luyện".equalsIgnoreCase(trimmed) || "Tap luyen".equalsIgnoreCase(trimmed) || "Training".equalsIgnoreCase(trimmed)) {
            return "Training";
        }
        if ("Dinh dưỡng".equalsIgnoreCase(trimmed) || "Dinh duong".equalsIgnoreCase(trimmed) || "Nutrition".equalsIgnoreCase(trimmed)) {
            return "Nutrition";
        }
        if ("Tin tức".equalsIgnoreCase(trimmed) || "Tin tuc".equalsIgnoreCase(trimmed) || "News".equalsIgnoreCase(trimmed)) {
            return "News";
        }
        return trimmed;
    }

    /**
     * Chuyển mã chuyên mục tiếng Anh trong DB thành nhãn hiển thị tiếng Việt trên giao diện (GBR-01).
     *
     * @param category mã chuyên mục (Training / Nutrition / News)
     * @return nhãn tiếng Việt tương ứng (Tập luyện / Dinh dưỡng / Tin tức)
     */
    public String getCategoryDisplayName(String category) {
        if (category == null) {
            return "";
        }
        switch (category.trim()) {
            case "Training":
                return "Tập luyện";
            case "Nutrition":
                return "Dinh dưỡng";
            case "News":
                return "Tin tức";
            default:
                return category.trim();
        }
    }

    /**
     * Loại bỏ các thẻ HTML và giải mã các thực thể HTML phổ biến.
     *
     * @param html chuỗi chứa mã HTML
     * @return văn bản thuần
     */
    private String stripHtml(String html) {
        if (html == null || html.trim().isEmpty()) {
            return "";
        }
        // Thay thẻ mở/đóng của khối hoặc ngắt dòng bằng khoảng trắng để tránh dính chữ
        String text = html.replaceAll("(?i)</?(br|p|div|h[1-6]|li)[^>]*>", " ");
        // Xóa toàn bộ thẻ HTML còn lại
        text = text.replaceAll("<[^>]+>", "");
        // Giải mã các thực thể HTML thông dụng
        text = text.replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'");
        // Chuẩn hóa khoảng trắng liên tiếp
        return text.replaceAll("\\s+", " ").trim();
    }
}