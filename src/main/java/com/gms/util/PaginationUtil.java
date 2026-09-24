package com.gms.util;

import com.gms.constant.AppConstants;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Đọc và chuẩn hóa tham số phân trang từ request (GBR-18).
 * URL chuẩn của mọi màn danh sách: ...?page=2&pageSize=20&keyword=...&status=...
 */
public final class PaginationUtil {

    public static final String PARAM_PAGE = "page";
    public static final String PARAM_PAGE_SIZE = "pageSize";

    private PaginationUtil() {
    }

    /**
     * Đọc số trang, nhỏ hơn 1 hoặc sai định dạng thì về trang 1.
     * @param request request hiện tại
     * @return số trang ≥ 1
     */
    public static int getPageNumber(HttpServletRequest request) {
        int pageNumber = RequestUtil.getIntParam(request, PARAM_PAGE, 1);
        if (pageNumber < 1) {
            return 1;
        }
        return pageNumber;
    }

    /**
     * Đọc số dòng mỗi trang; chỉ chấp nhận 10/20/50, giá trị khác về mặc định 10
     * (chặn người dùng sửa URL thành pageSize=100000 làm nặng DB).
     * @param request request hiện tại
     * @return 10, 20 hoặc 50
     */
    public static int getPageSize(HttpServletRequest request) {
        int pageSize = RequestUtil.getIntParam(request, PARAM_PAGE_SIZE, AppConstants.DEFAULT_PAGE_SIZE);
        boolean isAllowed = AppConstants.PAGE_SIZE_OPTIONS.contains(pageSize);
        if (!isAllowed) {
            return AppConstants.DEFAULT_PAGE_SIZE;
        }
        return pageSize;
    }

    /**
     * Số dòng bỏ qua, truyền vào "OFFSET ? ROWS" của câu SQL.
     * @param pageNumber số trang (từ 1)
     * @param pageSize số dòng mỗi trang
     * @return offset
     */
    public static int calculateOffset(int pageNumber, int pageSize) {
        return (pageNumber - 1) * pageSize;
    }
}
