package com.gms.dto;

import java.util.List;

/**
 * Một trang dữ liệu của danh sách có phân trang (GBR-18, codeImplementationBackend.md Mục 14).
 *
 * Luồng dùng chuẩn:
 * B1: Servlet đọc page/pageSize từ request qua PaginationUtil
 * B2: DAO query 2 câu — COUNT(*) lấy totalItems, và SELECT ... OFFSET ? ROWS FETCH NEXT ? ROWS ONLY lấy items
 * B3: Service gói lại thành new PageResult<>(items, pageNumber, pageSize, totalItems)
 * B4: JSP dùng ${page.items}, ${page.totalPages}, và STT = ${page.startIndex + loop.index + 1}
 *
 * @param <T> kiểu phần tử trong trang
 */
public class PageResult<T> {

    private final List<T> items;
    private final int pageNumber;
    private final int pageSize;
    private final long totalItems;

    /**
     * @param items các dòng của trang hiện tại (đã được SQL cắt sẵn bằng OFFSET/FETCH)
     * @param pageNumber số trang hiện tại, bắt đầu từ 1
     * @param pageSize số dòng mỗi trang
     * @param totalItems tổng số dòng khớp bộ lọc (từ câu COUNT)
     */
    public PageResult(List<T> items, int pageNumber, int pageSize, long totalItems) {
        this.items = items;
        this.pageNumber = pageNumber;
        this.pageSize = pageSize;
        this.totalItems = totalItems;
    }

    public List<T> getItems() {
        return items;
    }

    public int getPageNumber() {
        return pageNumber;
    }

    public int getPageSize() {
        return pageSize;
    }

    public long getTotalItems() {
        return totalItems;
    }

    /**
     * Tổng số trang; luôn ít nhất 1 để thanh phân trang không bị "trang 1 / 0".
     * @return tổng số trang
     */
    // GETTER TỰ TÍNH — không có field/setter "totalPages"; = (totalItems + pageSize − 1) ÷ pageSize, ít nhất 1; JSP: manager/leave-request-list.jsp:218 …
    public int getTotalPages() {
        long pages = (totalItems + pageSize - 1) / pageSize;
        if (pages < 1) {
            return 1;
        }
        return (int) pages;
    }

    // GETTER TỰ TÍNH — không có field/setter "empty"; = items null hoặc rỗng; JSP: không dùng (chỉ Java/test)
    public boolean isEmpty() {
        return items == null || items.isEmpty();
    }

    // GETTER TỰ TÍNH — không có field/setter "hasPrevious"; = pageNumber > 1; JSP: manager/leave-request-list.jsp:221 …
    public boolean isHasPrevious() {
        return pageNumber > 1;
    }

    // GETTER TỰ TÍNH — không có field/setter "hasNext"; = pageNumber < getTotalPages(); JSP: manager/leave-request-list.jsp:238 …
    public boolean isHasNext() {
        return pageNumber < getTotalPages();
    }
}
