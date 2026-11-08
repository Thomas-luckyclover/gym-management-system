package com.gms.dto;

import com.gms.entity.BlogPost;
import java.util.Collections;
import java.util.List;

/**
 * DTO đóng gói dữ liệu chi tiết bài viết Blog hiển thị cho màn hình Blog Details (SRS #7 / UC-06).
 *
 * <p>Bao gồm:</p>
 * <ul>
 *   <li>Bài viết chính (post) đã được bổ sung tóm tắt và ảnh bìa mặc định nếu thiếu.</li>
 *   <li>Thời gian đọc ước tính (readingTimeMinutes) tính theo số từ ÷ 200, tối thiểu 1 phút.</li>
 *   <li>Chuỗi hiển thị thời gian đọc thân thiện bằng tiếng Việt (readingTimeFormatted, VD: "3 phút đọc").</li>
 *   <li>Danh sách tối đa 3 bài viết gợi ý liên quan cùng chuyên mục (relatedPosts).</li>
 * </ul>
 */
public class BlogPostDetailView {

    private final BlogPost post;
    private final int readingTimeMinutes;
    private final String readingTimeFormatted;
    private final List<BlogPost> relatedPosts;

    /**
     * Khởi tạo đối tượng hiển thị chi tiết bài viết Blog.
     *
     * @param post bài viết chính
     * @param readingTimeMinutes số phút đọc ước tính
     * @param readingTimeFormatted chuỗi hiển thị số phút đọc
     * @param relatedPosts danh sách bài viết liên quan cùng chuyên mục
     */
    public BlogPostDetailView(BlogPost post, int readingTimeMinutes, String readingTimeFormatted, List<BlogPost> relatedPosts) {
        this.post = post;
        this.readingTimeMinutes = readingTimeMinutes;
        this.readingTimeFormatted = readingTimeFormatted;
        this.relatedPosts = relatedPosts != null ? Collections.unmodifiableList(relatedPosts) : Collections.emptyList();
    }

    public BlogPost getPost() {
        return post;
    }

    public int getReadingTimeMinutes() {
        return readingTimeMinutes;
    }

    public String getReadingTimeFormatted() {
        return readingTimeFormatted;
    }

    public List<BlogPost> getRelatedPosts() {
        return relatedPosts;
    }

    @Override
    public String toString() {
        return "BlogPostDetailView{"
                + "postId=" + (post != null ? post.getBlogPostId() : 0)
                + ", readingTime=" + readingTimeFormatted
                + ", relatedCount=" + relatedPosts.size()
                + '}';
    }
}
