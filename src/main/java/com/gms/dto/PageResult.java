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
    public int getTotalPages() {
        long pages = (totalItems + pageSize - 1) / pageSize;
        if (pages < 1) {
            return 1;
        }
        return (int) pages;
    }

    /**
     * Vị trí bắt đầu của trang, dùng tính STT hiển thị (Mục 14 — STT theo vị trí sau khi sort, không lấy từ DB).
     * Trang 2, 10 dòng/trang → 10, nên dòng đầu trang 2 có STT = 10 + 0 + 1 = 11.
     * @return số dòng đứng trước trang hiện tại
     */
    public int getStartIndex() {
        return (pageNumber - 1) * pageSize;
    }

    public boolean isEmpty() {
        return items == null || items.isEmpty();
    }

    public boolean isHasPrevious() {
        return pageNumber > 1;
    }

    public boolean isHasNext() {
        return pageNumber < getTotalPages();
    }
}
