package com.gms.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Toàn bộ dữ liệu màn Mua gói (Customer), gồm kết quả (thẻ gói, phân trang) và cột bộ lọc bên trái.
 * PackageSelectionService gom 1 lần bằng các setter; PackageSelectionServlet đặt vào request với tên "packageSelection".
 * Trong JSP đọc: ${packageSelection.options}, ${packageSelection.keyword}, ${packageSelection.minBound}...
 *
 * Danh sách mặc định là list rỗng (không bao giờ null) → JSP chỉ cần kiểm tra ${empty packageSelection.options}.
 * Dùng setter thay vì hàm tạo dài vì có nhiều trường: Service gọi từng setter theo tên, không phải nhớ thứ tự tham số.
 *
 * @author ALOHA
 */
public class PackageSelectionView {

    // Service: các thẻ gói SAU khi đã lọc, sắp xếp và cắt trang. JSP lặp từng thẻ.
    private List<PackageOptionView> options = new ArrayList<>();

    // Service: số gói đang bán TRƯỚC khi lọc. 0 nghĩa là cửa hàng chưa có gói nào (khác với "lọc không ra gói").
    private int totalActiveCount = 0;

    // Service chuẩn hóa từ request (cắt tối đa 100 ký tự). JSP in lại vào ô tìm kiếm.
    private String keyword = "";

    // Giá trị lọc loại gói đã chuẩn hóa: all | gym | day-pass | pt.
    private String typeFilter = "all";

    // Giá trị sắp xếp đã chuẩn hóa: default | saving | price-asc | price-desc.
    private String sortKey = "default";

    // Service: giờ mở cửa dạng "05:30 – 22:00" (tham số P-19), dùng trong hướng dẫn mua tại quầy. Có thể null.
    private String openingHours = null;

    // Service: trang đang xem (từ 1), đã ép vào khoảng 1..totalPages.
    private int page = 1;

    // Service: tổng số trang sau khi lọc; tối thiểu 1.
    private int totalPages = 1;

    // Service: số gói còn lại sau khi lọc (trước khi cắt trang).
    private int filteredCount = 0;

    // ===== Cột bộ lọc bên trái: số lượng và khoảng giá tính trên TOÀN BỘ gói đang bán (không theo kết quả đã lọc) để khách luôn thấy đủ lựa chọn. =====

    // Service: số gói theo loại, hiện cạnh từng lựa chọn Loại gói.
    private int allCount = 0;

    private int gymCount = 0;

    private int dayPassCount = 0;

    private int ptCount = 0;
    // Service: thời hạn query từ gói đang bán, đã bỏ trùng và xếp ngắn → dài.
    private List<PackageFacetOption> durationOptions = new ArrayList<>();

    // Service: giá thấp nhất / cao nhất trong các gói đang bán; là hai đầu thanh kéo giá.
    private int minBound = 0;

    private int maxBound = 0;

    // Service: khoảng giá khách đang chọn sau chuẩn hóa (bằng bound nếu không lọc giá).
    private int minPrice = 0;

    private int maxPrice = 0;

    // Service: số gói thanh toán trực tuyến được / chỉ mua tại quầy.
    private int onlineCount = 0;

    private int counterOnlyCount = 0;

    // Service: trạng thái đang tick của hai ô hình thức mua.
    private boolean buyOnline = false;

    private boolean buyCounterOnly = false;

    // Service: các chip "bộ lọc đang bật" (PackageFacetOption.chip).
    private List<PackageFacetOption> chips = new ArrayList<>();


    public List<PackageOptionView> getOptions() {
        return options;
    }

    public void setOptions(List<PackageOptionView> options) {
        this.options = new ArrayList<>(options);
    }

    public int getTotalActiveCount() {
        return totalActiveCount;
    }

    public void setTotalActiveCount(int totalActiveCount) {
        this.totalActiveCount = totalActiveCount;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public String getTypeFilter() {
        return typeFilter;
    }

    public void setTypeFilter(String typeFilter) {
        this.typeFilter = typeFilter;
    }

    public String getSortKey() {
        return sortKey;
    }

    public void setSortKey(String sortKey) {
        this.sortKey = sortKey;
    }

    public String getOpeningHours() {
        return openingHours;
    }

    public void setOpeningHours(String openingHours) {
        this.openingHours = openingHours;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public void setTotalPages(int totalPages) {
        this.totalPages = totalPages;
    }

    public int getFilteredCount() {
        return filteredCount;
    }

    public void setFilteredCount(int filteredCount) {
        this.filteredCount = filteredCount;
    }

    public int getAllCount() {
        return allCount;
    }

    public void setAllCount(int allCount) {
        this.allCount = allCount;
    }

    public int getGymCount() {
        return gymCount;
    }

    public void setGymCount(int gymCount) {
        this.gymCount = gymCount;
    }

    public int getDayPassCount() {
        return dayPassCount;
    }

    public void setDayPassCount(int dayPassCount) {
        this.dayPassCount = dayPassCount;
    }

    public int getPtCount() {
        return ptCount;
    }

    public void setPtCount(int ptCount) {
        this.ptCount = ptCount;
    }

    public List<PackageFacetOption> getDurationOptions() {
        return durationOptions;
    }

    public void setDurationOptions(List<PackageFacetOption> durationOptions) {
        this.durationOptions = new ArrayList<>(durationOptions);
    }

    public int getMinBound() {
        return minBound;
    }

    public void setMinBound(int minBound) {
        this.minBound = minBound;
    }

    public int getMaxBound() {
        return maxBound;
    }

    public void setMaxBound(int maxBound) {
        this.maxBound = maxBound;
    }

    public int getMinPrice() {
        return minPrice;
    }

    public void setMinPrice(int minPrice) {
        this.minPrice = minPrice;
    }

    public int getMaxPrice() {
        return maxPrice;
    }

    public void setMaxPrice(int maxPrice) {
        this.maxPrice = maxPrice;
    }

    public int getOnlineCount() {
        return onlineCount;
    }

    public void setOnlineCount(int onlineCount) {
        this.onlineCount = onlineCount;
    }

    public int getCounterOnlyCount() {
        return counterOnlyCount;
    }

    public void setCounterOnlyCount(int counterOnlyCount) {
        this.counterOnlyCount = counterOnlyCount;
    }

    public boolean isBuyOnline() {
        return buyOnline;
    }

    public void setBuyOnline(boolean buyOnline) {
        this.buyOnline = buyOnline;
    }

    public boolean isBuyCounterOnly() {
        return buyCounterOnly;
    }

    public void setBuyCounterOnly(boolean buyCounterOnly) {
        this.buyCounterOnly = buyCounterOnly;
    }

    public List<PackageFacetOption> getChips() {
        return chips;
    }

    public void setChips(List<PackageFacetOption> chips) {
        this.chips = new ArrayList<>(chips);
    }

    /**
     * Có đang lọc hoặc tìm kiếm không — JSP dùng để hiện nút "Xóa bộ lọc" trong trạng thái rỗng.
     * Tính: từ khóa khác rỗng, hoặc có chip bộ lọc đang bật. Kiểu sắp xếp không tính là lọc.
     */
    public boolean isFiltering() {
        return !keyword.isEmpty() || !chips.isEmpty();
    }
}
