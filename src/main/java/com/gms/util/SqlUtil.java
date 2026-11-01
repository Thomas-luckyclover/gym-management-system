package com.gms.util;

/**
 * Hỗ trợ viết câu SQL tìm kiếm cho SQL Server (GBR-19: không phân biệt hoa thường, không phân biệt dấu).
 *
 * Mẫu câu tìm kiếm chuẩn cho cả nhóm:
 *
 *   WHERE c.FullName COLLATE Vietnamese_CI_AI LIKE ? ESCAPE '\'
 *
 *   stmt.setString(1, SqlUtil.toContainsPattern(keyword));
 *
 * - Vietnamese_CI_AI: CI = không phân biệt hoa/thường, AI = không phân biệt dấu → gõ "nguyen" khớp "Nguyễn".
 * - Luôn truyền tham số qua PreparedStatement, không nối chuỗi.
 */
public final class SqlUtil {

    private static final char ESCAPE_CHAR = '\\';

    private SqlUtil() {
    }

    /**
     * Đổi từ khóa người dùng gõ thành mẫu LIKE "chứa từ khóa": "%từ khóa%".
     * Ký tự đặc biệt của LIKE (% _ [) được escape để người dùng gõ "50%" không bị hiểu thành ký tự đại diện.
     * @param keyword từ khóa (có thể null)
     * @return mẫu LIKE; từ khóa rỗng thì trả "%" (khớp tất cả)
     */
    public static String toContainsPattern(String keyword) {
        if (ValidationUtil.isNullOrEmpty(keyword)) {
            return "%";
        }
        String trimmedKeyword = keyword.trim();
        StringBuilder pattern = new StringBuilder("%");

        for (int i = 0; i < trimmedKeyword.length(); i++) {
            char character = trimmedKeyword.charAt(i);
            boolean isSpecial = character == '%' || character == '_' || character == '[' || character == ESCAPE_CHAR;
            if (isSpecial) {
                pattern.append(ESCAPE_CHAR);
                //tự động chèn "//" để escape ký tự đặc biệt trong LIKE
            }
            pattern.append(character);
        }

        pattern.append('%');
        return pattern.toString();
    }
}
