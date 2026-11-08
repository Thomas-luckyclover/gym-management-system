package com.gms.entity;

import java.time.LocalDateTime;

/**
 * Entity đại diện cho bảng BlogPosts trong SQL Server.
 * Ánh xạ 1-1 theo schema database/1_Init_Schema.sql:
 * BlogPostId, AuthorEmployeeId, Title, Category, Excerpt, Content, CoverImage, Status, PublishedAt.
 */
public class BlogPost {

    // Enum trạng thái bài viết
    public enum Status {
        // Bài viết đang đăng
        Active,
        // Bài viết đã gỡ hoặc nháp
        Inactive;

        // Chuyển chuỗi thành enum Status, mặc định Inactive nếu không khớp
        public static Status fromString(String value) {
            // null hoặc rỗng thì trả về Inactive
            if (value == null || value.trim().isEmpty()) {
                return Inactive;
            }
            // Duyệt các giá trị enum để tìm khớp
            for (Status item : values()) {
                if (item.name().equalsIgnoreCase(value.trim())) {
                    return item;
                }
            }
            return Inactive;
        }
    }

    /** Khóa chính tự tăng, bất biến (DBR-09) -> final, không có setter. */
    private final int blogPostId;

    private int authorEmployeeId;
    private String authorName;
    private String title;
    private String category;
    private String excerpt;
    private String content;
    private String coverImage;
    private Status status;
    private LocalDateTime publishedAt;

    // Constructor mặc định cho bài viết mới chưa có ID
    public BlogPost() {
        this.blogPostId = 0;
        this.status = Status.Inactive;
    }

    // Constructor tạo bài viết mới (chưa có BlogPostId)
    public BlogPost(int authorEmployeeId, String title, String category,
                    String excerpt, String content, String coverImage,
                    Status status, LocalDateTime publishedAt) {
        this(0, authorEmployeeId, null, title, category, excerpt, content, coverImage, status, publishedAt);
    }

    // Constructor đầy đủ khi đọc từ DB
    public BlogPost(int blogPostId, int authorEmployeeId, String title, String category,
                    String excerpt, String content, String coverImage,
                    Status status, LocalDateTime publishedAt) {
        this(blogPostId, authorEmployeeId, null, title, category, excerpt, content, coverImage, status, publishedAt);
    }

    // Constructor đầy đủ kèm tên tác giả (sau khi JOIN Employees/Persons)
    public BlogPost(int blogPostId, int authorEmployeeId, String authorName, String title,
                    String category, String excerpt, String content, String coverImage,
                    Status status, LocalDateTime publishedAt) {
        this.blogPostId = blogPostId;
        this.authorEmployeeId = authorEmployeeId;
        this.authorName = authorName;
        this.title = title;
        this.category = category;
        this.excerpt = excerpt;
        this.content = content;
        this.coverImage = coverImage;
        this.status = status != null ? status : Status.Inactive;
        this.publishedAt = publishedAt;
    }

    // ===== Getter & Setter (Setter chỉ gán thuần, không validate) =====

    // Lấy blogPostId (bất biến)
    public int getBlogPostId() {
        return blogPostId;
    }

    // Lấy authorEmployeeId
    public int getAuthorEmployeeId() {
        return authorEmployeeId;
    }

    // Gán authorEmployeeId
    public void setAuthorEmployeeId(int authorEmployeeId) {
        this.authorEmployeeId = authorEmployeeId;
    }

    // Lấy tên tác giả
    public String getAuthorName() {
        return authorName;
    }

    // Gán tên tác giả
    public void setAuthorName(String authorName) {
        this.authorName = authorName;
    }

    // Lấy tiêu đề
    public String getTitle() {
        return title;
    }

    // Gán tiêu đề
    public void setTitle(String title) {
        this.title = title;
    }

    // Lấy chuyên mục
    public String getCategory() {
        return category;
    }

    // Gán chuyên mục
    public void setCategory(String category) {
        this.category = category;
    }

    // Lấy tóm tắt ngắn
    public String getExcerpt() {
        return excerpt;
    }

    // Gán tóm tắt ngắn
    public void setExcerpt(String excerpt) {
        this.excerpt = excerpt;
    }

    // Lấy nội dung chi tiết
    public String getContent() {
        return content;
    }

    // Gán nội dung chi tiết
    public void setContent(String content) {
        this.content = content;
    }

    // Lấy đường dẫn ảnh đại diện
    public String getCoverImage() {
        return coverImage;
    }

    // Gán đường dẫn ảnh đại diện
    public void setCoverImage(String coverImage) {
        this.coverImage = coverImage;
    }

    // Lấy trạng thái bài viết
    public Status getStatus() {
        return status;
    }

    // Gán trạng thái bài viết
    public void setStatus(Status status) {
        this.status = status;
    }

    // Gán trạng thái từ chuỗi (chuyển qua Status.fromString)
    public void setStatus(String statusStr) {
        this.status = Status.fromString(statusStr);
    }

    // Kiểm tra bài viết đang Active hay không
    public boolean isActive() {
        return this.status == Status.Active;
    }

    // Lấy thời điểm đăng bài
    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }

    // Gán thời điểm đăng bài
    public void setPublishedAt(LocalDateTime publishedAt) {
        this.publishedAt = publishedAt;
    }

    // Chuỗi mô tả bài viết cho debug/log
    @Override
    public String toString() {
        return "BlogPost{"
                + "blogPostId=" + blogPostId
                + ", authorEmployeeId=" + authorEmployeeId
                + ", authorName='" + authorName + '\''
                + ", title='" + title + '\''
                + ", category='" + category + '\''
                + ", status=" + status
                + ", publishedAt=" + publishedAt
                + '}';
    }
}
