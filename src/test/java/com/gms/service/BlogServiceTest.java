package com.gms.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gms.entity.BlogPost;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit test cho các hàm nghiệp vụ BlogService theo yêu cầu Phase 1:
 * - Tính thời gian đọc (calculateReadingTime, formatReadingTime)
 * - Lấy tóm tắt từ nội dung (extractExcerpt)
 * - Ảnh bìa mặc định theo danh mục (getDefaultCoverImage, enrichPost)
 * - Chuẩn hóa danh mục (normalizeCategory, getCategoryDisplayName)
 */
class BlogServiceTest {

    private BlogService blogService;

    @BeforeEach
    void setUp() {
        blogService = new BlogService();
    }

    // ===== 1. Tính thời gian đọc (Reading Time) =====

    @Test
    @DisplayName("Nội dung null hoặc rỗng trả về tối thiểu 1 phút đọc")
    void readingTimeForNullOrEmptyContentReturnsOneMinute() {
        assertEquals(1, blogService.calculateReadingTime(null));
        assertEquals(1, blogService.calculateReadingTime(""));
        assertEquals(1, blogService.calculateReadingTime("   "));
        assertEquals("1 phút đọc", blogService.formatReadingTime(1));
    }

    @Test
    @DisplayName("Nội dung ngắn (< 200 từ) trả về 1 phút đọc")
    void readingTimeForShortContentReturnsOneMinute() {
        String content = "Mỗi buổi tập nên bắt đầu bằng 5-10 phút làm nóng toàn thân.";
        assertEquals(1, blogService.calculateReadingTime(content));
        assertEquals("1 phút đọc", blogService.formatReadingTime(blogService.calculateReadingTime(content)));
    }

    @Test
    @DisplayName("Nội dung đúng 200 từ trả về 1 phút đọc, 201 từ làm tròn lên 2 phút")
    void readingTimeWordCountRounding() {
        StringBuilder sb200 = new StringBuilder();
        for (int i = 1; i <= 200; i++) {
            sb200.append("từ").append(i).append(" ");
        }
        assertEquals(1, blogService.calculateReadingTime(sb200.toString()));

        StringBuilder sb201 = new StringBuilder(sb200);
        sb201.append("từ201");
        assertEquals(2, blogService.calculateReadingTime(sb201.toString()));
        assertEquals("2 phút đọc", blogService.formatReadingTime(2));

        StringBuilder sb405 = new StringBuilder();
        for (int i = 1; i <= 405; i++) {
            sb405.append("từ").append(i).append(" ");
        }
        assertEquals(3, blogService.calculateReadingTime(sb405.toString()));
        assertEquals("3 phút đọc", blogService.formatReadingTime(3));
    }

    @Test
    @DisplayName("Tính thời gian đọc phải bóc tách thẻ HTML trước khi đếm từ")
    void readingTimeStripsHtmlBeforeCountingWords() {
        String htmlContent = "<h2>Tiêu đề bài viết</h2>"
                + "<p>Đoạn văn 1 có <strong>chữ in đậm</strong> và <a href='#'>đường link</a>.</p>"
                + "<ul>"
                + "  <li>Mục 1</li>"
                + "  <li>Mục 2</li>"
                + "  <li>Mục 3</li>"
                + "</ul>";
        // Tổng số từ thực tế sau khi bỏ thẻ HTML vẫn < 200 từ -> 1 phút
        int minutes = blogService.calculateReadingTime(htmlContent);
        assertEquals(1, minutes);
    }

    // ===== 2. Lấy tóm tắt từ nội dung (Extract Excerpt) =====

    @Test
    @DisplayName("Nội dung null hoặc rỗng trả về chuỗi rỗng")
    void excerptForNullOrEmptyContentReturnsEmptyString() {
        assertEquals("", blogService.extractExcerpt(null));
        assertEquals("", blogService.extractExcerpt(""));
        assertEquals("", blogService.extractExcerpt("   "));
    }

    @Test
    @DisplayName("Nội dung ngắn hơn giới hạn giữ nguyên văn bản thuần và không thêm dấu ...")
    void excerptForShortContentReturnsPlainTextWithoutEllipsis() {
        String htmlContent = "<p>Bài tập khởi động giúp giảm chấn thương khớp vai.</p>";
        String excerpt = blogService.extractExcerpt(htmlContent);
        assertEquals("Bài tập khởi động giúp giảm chấn thương khớp vai.", excerpt);
    }

    @Test
    @DisplayName("Nội dung dài hơn 300 ký tự được cắt tỉa an toàn, không vượt quá 300 ký tự và có dấu ...")
    void excerptForLongContentTruncatesAtWordBoundary() {
        StringBuilder longText = new StringBuilder("<p>");
        for (int i = 0; i < 60; i++) {
            longText.append("luyện tập thể hình ");
        }
        longText.append("</p>");

        String excerpt = blogService.extractExcerpt(longText.toString());
        assertNotNull(excerpt);
        assertTrue(excerpt.length() <= 300, "Độ dài tóm tắt không được vượt quá 300 ký tự");
        assertTrue(excerpt.endsWith("..."), "Tóm tắt bị cắt ngắn phải kết thúc bằng ...");
        assertTrue(excerpt.startsWith("luyện tập thể hình"));
    }

    @Test
    @DisplayName("Hỗ trợ cắt tóm tắt với độ dài tùy chỉnh")
    void excerptWithCustomMaxLength() {
        String content = "Chế độ dinh dưỡng cân bằng là yếu tố quyết định 70% thành công.";
        String excerpt = blogService.extractExcerpt(content, 30);
        assertTrue(excerpt.length() <= 30);
        assertTrue(excerpt.endsWith("..."));
    }

    // ===== 3. Ảnh bìa mặc định theo danh mục (Default Cover Image) =====

    @Test
    @DisplayName("Lấy đúng đường dẫn ảnh mặc định theo từng danh mục")
    void defaultCoverImageByCategory() {
        assertEquals("/images/blog/default-training.jpg", blogService.getDefaultCoverImage("Training"));
        assertEquals("/images/blog/default-training.jpg", blogService.getDefaultCoverImage("Tập luyện"));

        assertEquals("/images/blog/default-nutrition.jpg", blogService.getDefaultCoverImage("Nutrition"));
        assertEquals("/images/blog/default-nutrition.jpg", blogService.getDefaultCoverImage("Dinh dưỡng"));

        assertEquals("/images/blog/default-news.jpg", blogService.getDefaultCoverImage("News"));
        assertEquals("/images/blog/default-news.jpg", blogService.getDefaultCoverImage("Tin tức"));

        assertEquals("/images/blog/default-blog.jpg", blogService.getDefaultCoverImage(null));
        assertEquals("/images/blog/default-blog.jpg", blogService.getDefaultCoverImage("Khác"));
    }

    @Test
    @DisplayName("enrichPost bổ sung tóm tắt và ảnh mặc định nếu bài viết bị thiếu")
    void enrichPostFillsMissingFields() {
        BlogPost post = new BlogPost();
        post.setCategory("Training");
        post.setContent("<h2>Khởi động</h2><p>5 phút xoay khớp trước khi nâng tạ.</p>");
        post.setExcerpt(null);
        post.setCoverImage(null);

        blogService.enrichPost(post);

        assertEquals("5 phút xoay khớp trước khi nâng tạ.", post.getExcerpt());
        assertEquals("/images/blog/default-training.jpg", post.getCoverImage());
    }

    @Test
    @DisplayName("enrichPost không ghi đè nếu bài viết đã có sẵn tóm tắt và ảnh bìa")
    void enrichPostPreservesExistingFields() {
        BlogPost post = new BlogPost();
        post.setCategory("Nutrition");
        post.setContent("<p>Nội dung bài viết...</p>");
        post.setExcerpt("Tóm tắt có sẵn");
        post.setCoverImage("/uploads/custom-cover.jpg");

        blogService.enrichPost(post);

        assertEquals("Tóm tắt có sẵn", post.getExcerpt());
        assertEquals("/uploads/custom-cover.jpg", post.getCoverImage());
    }

    // ===== 4. Chuẩn hóa & Hiển thị danh mục =====

    @Test
    @DisplayName("normalizeCategory chuẩn hóa tên tiếng Việt hoặc tiếng Anh về đúng chuẩn DB")
    void normalizeCategoryHandlesVariations() {
        assertEquals("Training", blogService.normalizeCategory("Training"));
        assertEquals("Training", blogService.normalizeCategory("Tập luyện"));
        assertEquals("Training", blogService.normalizeCategory("tap luyen"));

        assertEquals("Nutrition", blogService.normalizeCategory("Nutrition"));
        assertEquals("Nutrition", blogService.normalizeCategory("Dinh dưỡng"));

        assertEquals("News", blogService.normalizeCategory("News"));
        assertEquals("News", blogService.normalizeCategory("Tin tức"));

        assertEquals(null, blogService.normalizeCategory("Tất cả"));
        assertEquals(null, blogService.normalizeCategory("All"));
        assertEquals(null, blogService.normalizeCategory(null));
    }

    @Test
    @DisplayName("getCategoryDisplayName chuyển mã DB sang nhãn hiển thị tiếng Việt")
    void categoryDisplayNameInVietnamese() {
        assertEquals("Tập luyện", blogService.getCategoryDisplayName("Training"));
        assertEquals("Dinh dưỡng", blogService.getCategoryDisplayName("Nutrition"));
        assertEquals("Tin tức", blogService.getCategoryDisplayName("News"));
        assertEquals("", blogService.getCategoryDisplayName(null));
    }
}
