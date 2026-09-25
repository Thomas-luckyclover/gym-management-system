package com.gms.dao;

import com.gms.constant.AppConstants;
import com.gms.entity.BlogPost;
import com.gms.util.DBContext;
import com.gms.util.SqlUtil;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO truy vấn bảng BlogPosts trong SQL Server.
 *
 * <p>Tuân thủ nghiêm ngặt các quy tắc backend (codeImplementationBackend.md):</p>
 * <ul>
 * <li>Chỉ SQL thuần, không chứa nghiệp vụ, không gọi ValidationUtil.</li>
 * <li>Luôn dùng PreparedStatement, từ khóa SQL viết hoa, mỗi mệnh đề 1 dòng.</li>
 * <li>Không tự catch SQLException — ném lên cho tầng Service xử lý.</li>
 * <li>Phân trang bằng OFFSET ? ROWS FETCH NEXT ? ROWS ONLY.</li>
 * <li>Tìm kiếm bằng COLLATE Vietnamese_CI_AI + SqlUtil.toContainsPattern, trên cả Title và Excerpt (SRS 2.2.1).</li>
 * <li>Ánh xạ ResultSet qua method private mapResultSetToBlogPost đặt cuối class.</li>
 * <li>Tham số category truyền vào phải là giá trị đã chuẩn hóa đúng như lưu
 *     trong cột BlogPosts.Category (Training / Nutrition / News) — việc ánh xạ
 *     nhãn hiển thị (VD: "Tập luyện" -> "Training") là nghiệp vụ, thuộc Service,
 *     không đặt trong DAO.</li>
 * </ul>
 */
public class BlogPostDAO {

    // ===== 1. Tìm kiếm và phân trang bài viết đang đăng (Active) =====

    /**
     * Tìm danh sách bài viết đang đăng (Active) theo từ khóa và chuyên mục, có phân trang.
     * Từ khóa được tìm trên cả tiêu đề và tóm tắt (SRS 2.2.1).
     * @param keyword từ khóa tìm trong tiêu đề hoặc tóm tắt bài viết (có thể null hoặc rỗng)
     * @param category chuyên mục cần lọc, đúng giá trị lưu trong DB (Training / Nutrition / News); null hoặc rỗng để lấy hết
     * @param page số trang hiện tại (bắt đầu từ 1)
     * @param pageSize số dòng hiển thị mỗi trang
     * @return danh sách BlogPost tương ứng với trang
     * @throws SQLException khi có lỗi truy vấn cơ sở dữ liệu
     */
    public List<BlogPost> findActivePage(String keyword, String category, int page, int pageSize) throws SQLException {
        // B1: chuẩn bị câu SQL với các mệnh đề viết hoa riêng từng dòng
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT b.BlogPostId, b.AuthorEmployeeId, p.FullName AS AuthorName, b.Title, b.Category, b.Excerpt, b.Content, b.CoverImage, b.Status, b.PublishedAt\n");
        sql.append("FROM BlogPosts b\n");
        sql.append("LEFT JOIN Employees e ON b.AuthorEmployeeId = e.EmployeeId\n");
        sql.append("LEFT JOIN Persons p ON e.PersonId = p.PersonId\n");
        sql.append("WHERE b.Status = 'Active'\n");

        boolean hasKeyword = keyword != null && !keyword.trim().isEmpty();
        if (hasKeyword) {
            sql.append("    AND (b.Title COLLATE Vietnamese_CI_AI LIKE ? ESCAPE '\\'\n");
            sql.append("         OR b.Excerpt COLLATE Vietnamese_CI_AI LIKE ? ESCAPE '\\')\n");
        }

        boolean hasCategory = category != null && !category.trim().isEmpty();
        if (hasCategory) {
            sql.append("    AND b.Category = ?\n");
        }

        sql.append("ORDER BY b.PublishedAt DESC, b.BlogPostId DESC\n");
        sql.append("OFFSET ? ROWS FETCH NEXT ? ROWS ONLY");

        // B2: gán giá trị tham số an toàn qua PreparedStatement
        List<BlogPost> list = new ArrayList<>();
        try (Connection conn = DBContext.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql.toString())) {

            int paramIndex = 1;
            if (hasKeyword) {
                String searchPattern = SqlUtil.toContainsPattern(keyword);
                stmt.setString(paramIndex, searchPattern);
                paramIndex++;
                stmt.setString(paramIndex, searchPattern);
                paramIndex++;
            }
            if (hasCategory) {
                stmt.setString(paramIndex, category.trim());
                paramIndex++;
            }

            int safePage = Math.max(1, page);
            int safePageSize = pageSize > 0 ? pageSize : AppConstants.DEFAULT_PAGE_SIZE;
            int offset = (safePage - 1) * safePageSize;

            stmt.setInt(paramIndex, offset);
            paramIndex++;
            stmt.setInt(paramIndex, safePageSize);

            // B3: thực thi truy vấn và ánh xạ kết quả
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    BlogPost post = mapResultSetToBlogPost(rs);
                    list.add(post);
                }
            }
        }

        return list;
    }

    /**
     * Tìm danh sách bài viết đang đăng (Active) theo từ khóa và chuyên mục với số dòng mặc định của hệ thống.
     * @param keyword từ khóa tìm kiếm
     * @param category chuyên mục cần lọc, đúng giá trị lưu trong DB (Training / Nutrition / News)
     * @param page số trang hiện tại (bắt đầu từ 1)
     * @return danh sách BlogPost theo trang
     * @throws SQLException khi có lỗi truy vấn cơ sở dữ liệu
     */
    public List<BlogPost> findActivePage(String keyword, String category, int page) throws SQLException {
        return findActivePage(keyword, category, page, AppConstants.DEFAULT_PAGE_SIZE);
    }

    /**
     * Đếm tổng số bài viết đang đăng (Active) khớp với từ khóa và chuyên mục lọc.
     * Từ khóa được tìm trên cả tiêu đề và tóm tắt (SRS 2.2.1), đồng bộ với findActivePage.
     * @param keyword từ khóa tìm trong tiêu đề hoặc tóm tắt
     * @param category chuyên mục cần lọc, đúng giá trị lưu trong DB (Training / Nutrition / News)
     * @return tổng số bài viết thỏa mãn điều kiện
     * @throws SQLException khi có lỗi truy vấn cơ sở dữ liệu
     */
    public int countActive(String keyword, String category) throws SQLException {
        // B1: chuẩn bị câu SQL đếm số dòng
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT COUNT(*)\n");
        sql.append("FROM BlogPosts b\n");
        sql.append("WHERE b.Status = 'Active'\n");

        boolean hasKeyword = keyword != null && !keyword.trim().isEmpty();
        if (hasKeyword) {
            sql.append("    AND (b.Title COLLATE Vietnamese_CI_AI LIKE ? ESCAPE '\\'\n");
            sql.append("         OR b.Excerpt COLLATE Vietnamese_CI_AI LIKE ? ESCAPE '\\')\n");
        }

        boolean hasCategory = category != null && !category.trim().isEmpty();
        if (hasCategory) {
            sql.append("    AND b.Category = ?\n");
        }

        // B2: gán tham số và thực thi
        try (Connection conn = DBContext.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql.toString())) {

            int paramIndex = 1;
            if (hasKeyword) {
                String searchPattern = SqlUtil.toContainsPattern(keyword);
                stmt.setString(paramIndex, searchPattern);
                paramIndex++;
                stmt.setString(paramIndex, searchPattern);
                paramIndex++;
            }
            if (hasCategory) {
                stmt.setString(paramIndex, category.trim());
                paramIndex++;
            }

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }

        return 0;
    }

    // ===== 2. Chi tiết bài viết và bài liên quan =====

    /**
     * Tìm bài viết đang đăng (Active) theo ID bài viết.
     * @param blogPostId ID bài viết cần tìm
     * @return đối tượng BlogPost nếu tìm thấy, null nếu không tồn tại hoặc bài viết đã bị gỡ
     * @throws SQLException khi có lỗi truy vấn cơ sở dữ liệu
     */
    public BlogPost findActiveById(int blogPostId) throws SQLException {
        // B1: chuẩn bị câu SQL lấy chi tiết bài viết
        String sql = "SELECT b.BlogPostId, b.AuthorEmployeeId, p.FullName AS AuthorName, b.Title, b.Category, b.Excerpt, b.Content, b.CoverImage, b.Status, b.PublishedAt\n"
                + "FROM BlogPosts b\n"
                + "LEFT JOIN Employees e ON b.AuthorEmployeeId = e.EmployeeId\n"
                + "LEFT JOIN Persons p ON e.PersonId = p.PersonId\n"
                + "WHERE b.BlogPostId = ?\n"
                + "    AND b.Status = 'Active'";

        // B2: gán tham số và thực thi truy vấn
        try (Connection conn = DBContext.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, blogPostId);

            // B3: ánh xạ bản ghi tìm được
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToBlogPost(rs);
                }
            }
        }

        return null;
    }

    /**
     * Tìm danh sách các bài viết liên quan cùng chuyên mục đang đăng, loại trừ bài viết hiện tại.
     * @param category chuyên mục bài viết, đúng giá trị lưu trong DB (Training / Nutrition / News)
     * @param excludeId ID bài viết cần loại trừ (bài đang xem)
     * @param limit số lượng bài viết tối đa cần lấy
     * @return danh sách các bài viết liên quan
     * @throws SQLException khi có lỗi truy vấn cơ sở dữ liệu
     */
    public List<BlogPost> findRelated(String category, int excludeId, int limit) throws SQLException {
        // B1: chuẩn bị câu SQL lấy bài viết liên quan
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT b.BlogPostId, b.AuthorEmployeeId, p.FullName AS AuthorName, b.Title, b.Category, b.Excerpt, b.Content, b.CoverImage, b.Status, b.PublishedAt\n");
        sql.append("FROM BlogPosts b\n");
        sql.append("LEFT JOIN Employees e ON b.AuthorEmployeeId = e.EmployeeId\n");
        sql.append("LEFT JOIN Persons p ON e.PersonId = p.PersonId\n");
        sql.append("WHERE b.Status = 'Active'\n");
        sql.append("    AND b.BlogPostId <> ?\n");

        boolean hasCategory = category != null && !category.trim().isEmpty();
        if (hasCategory) {
            sql.append("    AND b.Category = ?\n");
        }

        sql.append("ORDER BY b.PublishedAt DESC, b.BlogPostId DESC\n");
        sql.append("OFFSET 0 ROWS FETCH NEXT ? ROWS ONLY");

        // B2: gán tham số và thực thi
        List<BlogPost> list = new ArrayList<>();
        try (Connection conn = DBContext.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql.toString())) {

            int paramIndex = 1;
            stmt.setInt(paramIndex, excludeId);
            paramIndex++;

            if (hasCategory) {
                stmt.setString(paramIndex, category.trim());
                paramIndex++;
            }

            int safeLimit = limit > 0 ? limit : 3;
            stmt.setInt(paramIndex, safeLimit);

            // B3: ánh xạ kết quả
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    BlogPost post = mapResultSetToBlogPost(rs);
                    list.add(post);
                }
            }
        }

        return list;
    }

    /**
     * Tìm 3 bài viết liên quan cùng chuyên mục đang đăng, loại trừ bài viết hiện tại.
     * @param category chuyên mục bài viết, đúng giá trị lưu trong DB (Training / Nutrition / News)
     * @param excludeId ID bài viết cần loại trừ
     * @return danh sách tối đa 3 bài viết liên quan
     * @throws SQLException khi có lỗi truy vấn cơ sở dữ liệu
     */
    public List<BlogPost> findRelated(String category, int excludeId) throws SQLException {
        return findRelated(category, excludeId, 3);
    }

    /**
     * Lấy danh sách các bài viết đang đăng mới nhất.
     * @param limit số bài viết mới nhất cần lấy
     * @return danh sách các bài viết mới nhất
     * @throws SQLException khi có lỗi truy vấn cơ sở dữ liệu
     */
    public List<BlogPost> findLatest(int limit) throws SQLException {
        // B1: chuẩn bị câu SQL sắp xếp bài đăng mới nhất lên đầu
        String sql = "SELECT b.BlogPostId, b.AuthorEmployeeId, p.FullName AS AuthorName, b.Title, b.Category, b.Excerpt, b.Content, b.CoverImage, b.Status, b.PublishedAt\n"
                + "FROM BlogPosts b\n"
                + "LEFT JOIN Employees e ON b.AuthorEmployeeId = e.EmployeeId\n"
                + "LEFT JOIN Persons p ON e.PersonId = p.PersonId\n"
                + "WHERE b.Status = 'Active'\n"
                + "ORDER BY b.PublishedAt DESC, b.BlogPostId DESC\n"
                + "OFFSET 0 ROWS FETCH NEXT ? ROWS ONLY";

        // B2: gán tham số và thực thi
        List<BlogPost> list = new ArrayList<>();
        try (Connection conn = DBContext.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            int safeLimit = limit > 0 ? limit : 3;
            stmt.setInt(1, safeLimit);

            // B3: ánh xạ danh sách bài viết
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    BlogPost post = mapResultSetToBlogPost(rs);
                    list.add(post);
                }
            }
        }

        return list;
    }

    /**
     * Lấy 3 bài viết đang đăng mới nhất.
     * @return danh sách 3 bài viết mới nhất
     * @throws SQLException khi có lỗi truy vấn cơ sở dữ liệu
     */
    public List<BlogPost> findLatest() throws SQLException {
        return findLatest(3);
    }

    // ===== 3. Ánh xạ dữ liệu (Helper Method) =====

    /**
     * Ánh xạ một dòng từ ResultSet sang đối tượng BlogPost.
     * @param rs ResultSet đang trỏ tới dòng hiện tại
     * @return đối tượng BlogPost đã nạp dữ liệu
     * @throws SQLException khi đọc dữ liệu từ ResultSet gặp lỗi
     */
    private BlogPost mapResultSetToBlogPost(ResultSet rs) throws SQLException {
        int blogPostId = rs.getInt("BlogPostId");
        int authorEmployeeId = rs.getInt("AuthorEmployeeId");
        String authorName = rs.getString("AuthorName");
        String title = rs.getString("Title");
        String category = rs.getString("Category");
        String excerpt = rs.getString("Excerpt");
        String content = rs.getString("Content");
        String coverImage = rs.getString("CoverImage");
        String statusStr = rs.getString("Status");
        BlogPost.Status status = BlogPost.Status.fromString(statusStr);

        Timestamp timestamp = rs.getTimestamp("PublishedAt");
        LocalDateTime publishedAt = timestamp != null ? timestamp.toLocalDateTime() : null;

        return new BlogPost(
                blogPostId,
                authorEmployeeId,
                authorName,
                title,
                category,
                excerpt,
                content,
                coverImage,
                status,
                publishedAt
        );
    }
}