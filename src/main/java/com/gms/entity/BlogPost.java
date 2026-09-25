package com.gms.entity;

import java.time.LocalDateTime;

/**
 * Entity đại diện cho bảng BlogPosts trong SQL Server.
 * Ánh xạ 1-1 theo schema database/1_Init_Schema.sql:
 * BlogPostId, AuthorEmployeeId, Title, Category, Excerpt, Content, CoverImage, Status, PublishedAt.
 */
public class BlogPost {

    /**
     * Trạng thái bài viết theo constraint CK_BlogPosts_Status: Active (đang đăng) | Inactive (gỡ xuống/nháp).
     */
    public enum Status {
        Active,
        Inactive;

        /**
         * Chuyển chuỗi từ database hoặc request thành enum Status.
         * @param value giá trị chuỗi (Active / Inactive)
         * @return enum Status tương ứng, mặc định Inactive nếu null hoặc không khớp
         */
        public static Status fromString(String value) {
            if (value == null || value.trim().isEmpty()) {
                return Inactive;
            }
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

    /**
     * Constructor mặc định khi khởi tạo bài viết mới chưa có ID.
     */
    public BlogPost() {
        this.blogPostId = 0;
        this.status = Status.Inactive;
    }

    /**
     * Constructor tạo bài viết mới để thêm vào database (chưa có BlogPostId).
     * @param authorEmployeeId ID nhân viên tác giả (Manager)
     * @param title tiêu đề bài viết
     * @param category chuyên mục (Training, Nutrition, News)
     * @param excerpt tóm tắt ngắn
     * @param content nội dung chi tiết
     * @param coverImage đường dẫn ảnh đại diện
     * @param status trạng thái (Active / Inactive)
     * @param publishedAt thời điểm đăng bài
     */
    public BlogPost(int authorEmployeeId, String title, String category,
                    String excerpt, String content, String coverImage,
                    Status status, LocalDateTime publishedAt) {
        this(0, authorEmployeeId, null, title, category, excerpt, content, coverImage, status, publishedAt);
    }

    /**
     * Constructor đầy đủ khi đọc từ database.
     * @param blogPostId ID bài viết
     * @param authorEmployeeId ID nhân viên tác giả
     * @param title tiêu đề bài viết
     * @param category chuyên mục
     * @param excerpt tóm tắt ngắn
     * @param content nội dung chi tiết
     * @param coverImage ảnh đại diện
     * @param status trạng thái
     * @param publishedAt thời điểm đăng bài
     */
    public BlogPost(int blogPostId, int authorEmployeeId, String title, String category,
                    String excerpt, String content, String coverImage,
                    Status status, LocalDateTime publishedAt) {
        this(blogPostId, authorEmployeeId, null, title, category, excerpt, content, coverImage, status, publishedAt);
    }

    /**
     * Constructor đầy đủ kèm tên tác giả khi JOIN bảng Employees và Persons.
     * @param blogPostId ID bài viết
     * @param authorEmployeeId ID nhân viên tác giả
     * @param authorName họ tên tác giả hiển thị
     * @param title tiêu đề bài viết
     * @param category chuyên mục
     * @param excerpt tóm tắt ngắn
     * @param content nội dung chi tiết
     * @param coverImage ảnh đại diện
     * @param status trạng thái
     * @param publishedAt thời điểm đăng bài
     */
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

    public int getBlogPostId() {
        return blogPostId;
    }

    public int getAuthorEmployeeId() {
        return authorEmployeeId;
    }

    public void setAuthorEmployeeId(int authorEmployeeId) {
        this.authorEmployeeId = authorEmployeeId;
    }

    public String getAuthorName() {
        return authorName;
    }

    public void setAuthorName(String authorName) {
        this.authorName = authorName;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getExcerpt() {
        return excerpt;
    }

    public void setExcerpt(String excerpt) {
        this.excerpt = excerpt;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getCoverImage() {
        return coverImage;
    }

    public void setCoverImage(String coverImage) {
        this.coverImage = coverImage;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public void setStatus(String statusStr) {
        this.status = Status.fromString(statusStr);
    }

    public boolean isActive() {
        return this.status == Status.Active;
    }

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(LocalDateTime publishedAt) {
        this.publishedAt = publishedAt;
    }

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
