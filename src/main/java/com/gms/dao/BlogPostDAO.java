//package com.gms.dao;
// 
//import com.gms.constant.AppConstants;
//import com.gms.entity.BlogPost;
//import com.gms.util.DBContext;
//import com.gms.util.SqlUtil;
//import java.sql.Connection;
//import java.sql.PreparedStatement;
//import java.sql.ResultSet;
//import java.sql.SQLException;
//import java.sql.Timestamp;
//import java.time.LocalDateTime;
//import java.util.ArrayList;
//import java.util.List;
///**
// * DAO truy vấn bảng BlogPosts trong SQL Server.
// *
// * <p>Tuân thủ nghiêm ngặt các quy tắc backend (codeImplementationBackend.md):</p>
// * <ul>
// * <li>Chỉ SQL thuần, không chứa nghiệp vụ, không gọi ValidationUtil.</li>
// * <li>Luôn dùng PreparedStatement, từ khóa SQL viết hoa, mỗi mệnh đề 1 dòng.</li>
// * <li>Không tự catch SQLException — ném lên cho tầng Service xử lý.</li>
// * <li>Phân trang bằng OFFSET ? ROWS FETCH NEXT ? ROWS ONLY.</li>
// * <li>Tìm kiếm bằng COLLATE Vietnamese_CI_AI + SqlUtil.toContainsPattern.</li>
// * <li>Ánh xạ ResultSet qua method private mapResultSetToBlogPost đặt cuối class.</li>
// * <li>Tham số category truyền vào phải là giá trị đã chuẩn hóa đúng như lưu
// *     trong cột BlogPosts.Category (Training / Nutrition / News) — việc ánh xạ
// *     nhãn hiển thị (VD: "Tập luyện" -> "Training") là nghiệp vụ, thuộc Service,
// *     không đặt trong DAO.</li>
// * </ul>
// */
//public class BlogPostDAO {
// 
//    // ===== 1. Tìm kiếm và phân trang bài viết đang đăng (Active) =====
// 
//    /**
//     * Tìm danh sách bài viết đang đăng (Active) theo từ khóa và chuyên mục, có phân trang.
//     * @param keyword từ khóa tìm trong tiêu đề bài viết (có thể null hoặc rỗng)
//     * @param category chuyên mục cần lọc, đúng giá trị lưu trong DB (Training / Nutrition / News); null hoặc rỗng để lấy hết
//     * @param page số trang hiện tại (bắt đầu từ 1)
//     * @param pageSize số dòng hiển thị mỗi trang
//     * @return danh sách BlogPost tương ứng với trang
//     * @throws SQLException khi có lỗi truy vấn cơ sở dữ liệu
//     */
//   
//  public List<BlogPost> findActivePage(String keyword, String category, int page, int pageSize) throws SQLException{
//      StringBuilder sql = new StringBuilder();
//      sql.append("\"SELECT b.BlogPostId, b.AuthorEmployeeId, p.FullName AS AuthorName, b.Title, b.Category, b.Excerpt, b.Content, b.CoverImage, b.Status, b.PublishedAt\\n\"");
//      sql.append("FROM BlogPosts b\n");
//      sql.append("LEFT JOIN Employees e ON b.AuthorEmployeeId = e.EmployeeId\\n ");
//      sql.append("LEFT JOIN Persons p ON e.PersonId = p.PersonId\n");
//      sql.append("WHERE b.Status = 'Active'\n");
//      
//      boolean hasKeyword = keyword != null && !keyword.trim().isEmpty();
//      if(hasKeyword){
//          sql.append("   AND b.Title COLLATE Vietnamese_CI_AI LIKE ? ESCAPE '\\\\'\\n");   
//      }
//      
//      boolean hasCategory = category != null && !category.trim().isEmpty();
//      if(hasCategory){
//          sql.append("  AND b.Categpry = ?\n");
//      }
//      
//      sql.append("ORDER BY b.PublishedAt DESC, b.BlogPostId DESC\n");
//      sql.append("OFFSET ? ROWS FETCH NEXT ? ROWS ONLY");
//      
//       // B2: gán giá trị tham số an toàn qua PreparedStatement
//        List<BlogPost> list = new ArrayList<>();
//        try (Connection conn = DBContext.getConnection();
//                PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
// 
//            int paramIndex = 1;
//            if (hasKeyword) {
//                String searchPattern = SqlUtil.toContainsPattern(keyword);
//                stmt.setString(paramIndex, searchPattern);
//                paramIndex++;
//            }
//            if (hasCategory) {
//                stmt.setString(paramIndex, category.trim());
//                paramIndex++;
//            }
// 
//            int safePage = Math.max(1, page);
//            int safePageSize = pageSize > 0 ? pageSize : AppConstants.DEFAULT_PAGE_SIZE;
//            int offset = (safePage - 1) * safePageSize;
// 
//            stmt.setInt(paramIndex, offset);
//            paramIndex++;
//            stmt.setInt(paramIndex, safePageSize);
// 
//            // B3: thực thi truy vấn và ánh xạ kết quả
//            try (ResultSet rs = stmt.executeQuery()) {
//                while (rs.next()) {
//                    BlogPost post = mapResultSetToBlogPost(rs);
//                    list.add(post);
//                }
//            }
//        }
// 
//        return list;
//    }
//  }
//}