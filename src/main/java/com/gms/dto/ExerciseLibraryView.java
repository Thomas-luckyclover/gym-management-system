package com.gms.dto;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * DỮ LIỆU MÀN THƯ VIỆN BÀI TẬP (/customer/exercises, /pt/exercises), biến JSP "library".
 *
 *   Ai tạo?   ExerciseLibraryService.getPage — 1 lần mỗi lần mở trang.
 *   Nguồn?    bảng Exercises (bộ đệm từ wger.de), Subscriptions + Packages (quyền mở khóa của khách).
 *   Ai in?    _exercise-library.jspf: màn khóa (khách chưa đủ quyền) hoặc bộ lọc + lưới bài + phân trang.
 */
public class ExerciseLibraryView {

    /** Trạng thái quyền xem. Khách: chỉ OPEN mới thấy thư viện. PT: luôn OPEN. */
    public enum Access {
        OPEN,          // đang có gói mở khóa còn hiệu lực (kể cả đang bảo lưu)
        NEVER,         // chưa từng có gói mở khóa
        EXPIRED,       // gói mở khóa gần nhất đã hết hạn ngày lockDate
        NOT_STARTED    // đã mua nhưng gói bắt đầu từ ngày lockDate
    }

    /**
     * 1 gói của khách có cờ UnlocksExerciseLibrary = 1. DAO tạo (findUnlockPeriods), Service dùng để quyết định Access.
     * Không in ra trang.
     */
    public static class Period {
        // DAO: Subscriptions.StartDate
        private final LocalDate startDate;
        // DAO: Subscriptions.EndDate (ngày cuối còn dùng)
        private final LocalDate endDate;
        // DAO: Subscriptions.Status — Active | Frozen | Expired (Cancelled đã lọc ở SQL)
        private final String status;

        public Period(LocalDate startDate, LocalDate endDate, String status) {
            this.startDate = startDate;
            this.endDate = endDate;
            this.status = status;
        }

        // Ngày bắt đầu gói
        public LocalDate getStartDate() {
            return startDate;
        }

        // Ngày cuối gói còn dùng
        public LocalDate getEndDate() {
            return endDate;
        }

        // Trạng thái gói
        public String getStatus() {
            return status;
        }
    }

    /**
     * 1 lựa chọn của bộ lọc (chip nhóm cơ, dòng trong ô Dụng cụ, ô Sắp xếp) hoặc 1 số trang.
     * Service tạo; JSP in label, đánh dấu selected, ghép value vào link/ô chọn.
     */
    public static class Option {
        // Giá trị gửi lên URL: mã nhóm cơ / mã dụng cụ / "name" / số trang
        private final String value;
        // Chữ hiện trên trang, ví dụ "Ngực"
        private final String label;
        // Số bài trong nhóm (chỉ chip nhóm cơ dùng; -1 = không in)
        private final int count;
        // true = đang chọn → JSP tô đậm / selected / aria-current
        private final boolean selected;

        public Option(String value, String label, int count, boolean selected) {
            this.value = value;
            this.label = label;
            this.count = count;
            this.selected = selected;
        }

        // Giá trị gửi lên URL
        public String getValue() {
            return value;
        }

        // Chữ hiện ra
        public String getLabel() {
            return label;
        }

        // Số bài (-1 = không in)
        public int getCount() {
            return count;
        }

        // Có đang chọn không
        public boolean isSelected() {
            return selected;
        }
    }

    // Service: PT → OPEN; khách → decideAccess(các gói mở khóa, hôm nay).
    private Access access = Access.OPEN;
    // Service: ngày hết hạn (EXPIRED) hoặc ngày bắt đầu (NOT_STARTED). JSP in dd/MM/yyyy trong câu khóa.
    private LocalDate lockDate;
    // Service: câu giải thích ở màn khóa, ví dụ "Gói của bạn hết hạn ngày 26/10/2026. Gia hạn để mở lại thư viện."
    private String lockMessage;
    // DAO findUnlockingPackages → Service ghép "Gói 1 Tháng · 799.000đ". JSP in danh sách ở màn khóa.
    private List<String> unlockingPackages = new ArrayList<>();
    // Servlet: true khi URL bắt đầu /pt/ → JSP đổi link và bỏ nút Mua gói.
    private boolean pt;

    // DAO findPage + Service điền nhãn tiếng Việt. Tối đa PAGE_SIZE bài.
    private List<ExerciseItem> items = new ArrayList<>();
    // DAO countFiltered: số bài khớp bộ lọc (mọi trang).
    private int total;
    // DAO countAll: tổng số bài trong thư viện (để in "812 bài").
    private int libraryTotal;
    // Service: trang đang xem (đã kẹp trong 1..totalPages) và tổng số trang.
    private int page = 1;
    private int totalPages = 1;
    // Service: STT bài đầu / cuối của trang, ví dụ "Hiện 13–24 / 78 bài".
    private int firstIndex;
    private int lastIndex;

    // Service: 8 chip nhóm cơ (kèm số bài) + 12 dụng cụ + 2 cách sắp xếp.
    private List<Option> categories = new ArrayList<>();
    private List<Option> equipments = new ArrayList<>();
    private List<Option> sorts = new ArrayList<>();
    // Service: giá trị bộ lọc đã làm sạch để điền lại vào form và ghép link (null = không lọc).
    private String query;
    private Integer categoryId;
    private Integer equipmentId;
    private String sort;

    // Service: "27/10/2026 08:15" — lần tải từ wger gần nhất (MAX(SyncedAt)). null = thư viện trống.
    private String syncedAtText;
    // Service: true = lần tải mới nhất bị lỗi, đang hiện bản cũ → JSP in dòng vàng.
    private boolean stale;

    // Quyền xem
    public Access getAccess() {
        return access;
    }

    // Gán quyền xem (Service)
    public void setAccess(Access access) {
        this.access = access;
    }

    // JSP: true = in lưới thư viện, false = in màn khóa
    public boolean isOpen() {
        return access == Access.OPEN;
    }

    // Ngày dùng trong câu khóa
    public LocalDate getLockDate() {
        return lockDate;
    }

    // Gán ngày khóa (Service)
    public void setLockDate(LocalDate lockDate) {
        this.lockDate = lockDate;
    }

    // Câu giải thích ở màn khóa
    public String getLockMessage() {
        return lockMessage;
    }

    // Gán câu giải thích (Service)
    public void setLockMessage(String lockMessage) {
        this.lockMessage = lockMessage;
    }

    // Các gói đang bán có đặc quyền (màn khóa)
    public List<String> getUnlockingPackages() {
        return unlockingPackages;
    }

    // Gán danh sách gói (Service)
    public void setUnlockingPackages(List<String> unlockingPackages) {
        this.unlockingPackages = unlockingPackages;
    }

    // Đang xem với vai trò PT
    public boolean isPt() {
        return pt;
    }

    // Gán vai trò PT (Service, theo Servlet)
    public void setPt(boolean pt) {
        this.pt = pt;
    }

    // Bài của trang đang xem
    public List<ExerciseItem> getItems() {
        return items;
    }

    // Gán bài (Service)
    public void setItems(List<ExerciseItem> items) {
        this.items = items;
    }

    // Số bài khớp bộ lọc
    public int getTotal() {
        return total;
    }

    // Gán số bài khớp (Service)
    public void setTotal(int total) {
        this.total = total;
    }

    // Tổng số bài trong thư viện
    public int getLibraryTotal() {
        return libraryTotal;
    }

    // Gán tổng số bài (Service)
    public void setLibraryTotal(int libraryTotal) {
        this.libraryTotal = libraryTotal;
    }

    // Trang đang xem
    public int getPage() {
        return page;
    }

    // Gán trang (Service)
    public void setPage(int page) {
        this.page = page;
    }

    // Tổng số trang
    public int getTotalPages() {
        return totalPages;
    }

    // Gán tổng số trang (Service)
    public void setTotalPages(int totalPages) {
        this.totalPages = totalPages;
    }

    // STT bài đầu trang
    public int getFirstIndex() {
        return firstIndex;
    }

    // Gán STT đầu (Service)
    public void setFirstIndex(int firstIndex) {
        this.firstIndex = firstIndex;
    }

    // STT bài cuối trang
    public int getLastIndex() {
        return lastIndex;
    }

    // Gán STT cuối (Service)
    public void setLastIndex(int lastIndex) {
        this.lastIndex = lastIndex;
    }

    // Chip nhóm cơ
    public List<Option> getCategories() {
        return categories;
    }

    // Gán chip nhóm cơ (Service)
    public void setCategories(List<Option> categories) {
        this.categories = categories;
    }

    // Dụng cụ trong ô chọn
    public List<Option> getEquipments() {
        return equipments;
    }

    // Gán dụng cụ (Service)
    public void setEquipments(List<Option> equipments) {
        this.equipments = equipments;
    }

    // Cách sắp xếp trong ô chọn
    public List<Option> getSorts() {
        return sorts;
    }

    // Gán cách sắp xếp (Service)
    public void setSorts(List<Option> sorts) {
        this.sorts = sorts;
    }

    // Từ khóa tìm tên bài đã làm sạch
    public String getQuery() {
        return query;
    }

    // Gán từ khóa (Service)
    public void setQuery(String query) {
        this.query = query;
    }

    // Nhóm cơ đang lọc (null = tất cả)
    public Integer getCategoryId() {
        return categoryId;
    }

    // Gán nhóm cơ đang lọc (Service)
    public void setCategoryId(Integer categoryId) {
        this.categoryId = categoryId;
    }

    // Dụng cụ đang lọc (null = tất cả)
    public Integer getEquipmentId() {
        return equipmentId;
    }

    // Gán dụng cụ đang lọc (Service)
    public void setEquipmentId(Integer equipmentId) {
        this.equipmentId = equipmentId;
    }

    // Cách sắp xếp đang dùng ("name" | "image")
    public String getSort() {
        return sort;
    }

    // Gán cách sắp xếp (Service)
    public void setSort(String sort) {
        this.sort = sort;
    }

    // Giờ tải từ wger gần nhất
    public String getSyncedAtText() {
        return syncedAtText;
    }

    // Gán giờ tải (Service)
    public void setSyncedAtText(String syncedAtText) {
        this.syncedAtText = syncedAtText;
    }

    // Đang hiện bản cũ vì tải mới bị lỗi
    public boolean isStale() {
        return stale;
    }

    // Gán cờ bản cũ (Service)
    public void setStale(boolean stale) {
        this.stale = stale;
    }

    // JSP: true khi có lọc → hiện link "Xóa lọc"
    public boolean isFiltered() {
        return query != null || categoryId != null || equipmentId != null;
    }
}
