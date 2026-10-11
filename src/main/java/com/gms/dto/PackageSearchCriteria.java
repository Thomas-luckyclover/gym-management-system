package com.gms.dto;

import java.util.HashSet;
import java.util.Set;

/**
 * Các giá trị tìm kiếm / lọc / sắp xếp thô mà khách gửi lên từ màn Mua gói (query string).
 * Servlet chỉ gói lại, KHÔNG kiểm tra; PackageSelectionService chuẩn hóa mọi giá trị sai
 * (xem bảng xử lý ở javadoc của Service).
 *
 * Ví dụ ?keyword=goi&type=gym&minPrice=500000&maxPrice=2000000&duration=month-3&buy=online&sort=saving
 * → keyword = "goi", typeFilter = "gym", minPrice = 500000, durationKeys = {"month-3"}, buyOnline = true.
 *
 * @author ALOHA
 */
public class PackageSearchCriteria {

    // Servlet: tham số keyword. Có thể null.
    private final String keyword;

    // Servlet: tham số type (all | gym | day-pass | pt). Có thể null hoặc giá trị lạ.
    private final String typeFilter;

    // Servlet: tham số sort. Có thể null hoặc giá trị lạ.
    private final String sortKey;

    // Servlet: tham số page, đã đổi sang số (không phải số thì là 1).
    private final int page;

    // Servlet: tham số minPrice / maxPrice, đã đổi sang số; null nếu thiếu hoặc không phải số.
    private final Integer minPrice;
    private final Integer maxPrice;

    // Servlet: các tham số duration (nhiều giá trị), VD "month-3", "day-1". Không bao giờ null.
    private final Set<String> durationKeys;

    // Servlet: buy=online / buy=counter có được tick không.
    private final boolean buyOnline;
    private final boolean buyCounterOnly;

    public PackageSearchCriteria(String keyword, String typeFilter, String sortKey, int page,
            Integer minPrice, Integer maxPrice, Set<String> durationKeys,
            boolean buyOnline, boolean buyCounterOnly) {
        this.keyword = keyword;
        this.typeFilter = typeFilter;
        this.sortKey = sortKey;
        this.page = page;
        this.minPrice = minPrice;
        this.maxPrice = maxPrice;
        this.durationKeys = durationKeys == null ? new HashSet<String>() : new HashSet<String>(durationKeys);
        this.buyOnline = buyOnline;
        this.buyCounterOnly = buyCounterOnly;
    }

    /** Tiện cho test: chỉ có từ khóa, loại, sắp xếp, trang; các bộ lọc còn lại để trống. */
    public static PackageSearchCriteria basic(String keyword, String typeFilter, String sortKey, int page) {
        return new PackageSearchCriteria(keyword, typeFilter, sortKey, page, null, null, null, false, false);
    }

    public String getKeyword() {
        return keyword;
    }

    public String getTypeFilter() {
        return typeFilter;
    }

    public String getSortKey() {
        return sortKey;
    }

    public int getPage() {
        return page;
    }

    public Integer getMinPrice() {
        return minPrice;
    }

    public Integer getMaxPrice() {
        return maxPrice;
    }

    public Set<String> getDurationKeys() {
        return new HashSet<String>(durationKeys);
    }

    public boolean isBuyOnline() {
        return buyOnline;
    }

    public boolean isBuyCounterOnly() {
        return buyCounterOnly;
    }
}
