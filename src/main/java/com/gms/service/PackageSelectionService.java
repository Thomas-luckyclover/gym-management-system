package com.gms.service;

import com.gms.constant.Messages;
import com.gms.dao.PackageDAO;
import com.gms.dto.PackageCardView;
import com.gms.dto.PackageFacetOption;
import com.gms.dto.PackageOptionView;
import com.gms.dto.PackageSearchCriteria;
import com.gms.dto.PackageSelectionView;
import com.gms.dto.ServiceResult;
import com.gms.entity.Package;
import com.gms.util.MoneyUtil;
import java.sql.SQLException;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Service cho màn Mua gói của Customer (SRS 7.1.1, UC-22).
 *
 * <p>
 * Việc chính: lấy mọi gói đang bán, dựng thẻ giá (giá/tháng, giá gốc + số tiền
 * tiết kiệm, nhãn Tiết kiệm hơn / Mới ra mắt), cho biết gói nào khách tự mua
 * online được (Membership, DayPass) và gói nào chỉ mua tại quầy (PT), rồi lọc
 * theo ô tìm kiếm, loại gói, khoảng giá, thời hạn (tháng), hình thức mua và
 * sắp xếp.</p>
 *
 * <p>
 * Vì sao tải hết gói rồi mới lọc: % tiết kiệm và nhãn "Tiết kiệm hơn" phải so
 * với giá gói 1 tháng. Nếu lọc trước, khách gõ "6 tháng" sẽ làm mất gói 1 tháng
 * và nhãn tính sai.</p>
 *
 * <p>
 * Phần dựng thẻ giá (buildGymCards, assignBadges) là bản sao từ HomeService để
 * không sửa file của module khác. Khi nhóm đồng ý, tách chung một chỗ để
 * homepage và màn này cùng gọi.</p>
 *
 * @author ALOHA
 */
public class PackageSelectionService {

    // ===== Giá trị bộ lọc loại gói (khớp value của chip trong package-selection.jsp) =====
    public static final String TYPE_ALL = "all";
    public static final String TYPE_GYM = "gym";
    public static final String TYPE_DAY_PASS = "day-pass";
    public static final String TYPE_PT = "pt";

    // ===== Giá trị sắp xếp (khớp value của <select name="sort">) =====
    public static final String SORT_DEFAULT = "default";
    public static final String SORT_SAVING = "saving";
    public static final String SORT_PRICE_ASC = "price-asc";
    public static final String SORT_PRICE_DESC = "price-desc";

    // ===== Giá trị bộ lọc hình thức mua (khớp value của checkbox name="buy") =====
    public static final String BUY_ONLINE = "online";
    public static final String BUY_COUNTER = "counter";

    /**
     * SRS 7.1.1: ô tìm kiếm tối đa 100 ký tự.
     */
    private static final int KEYWORD_MAX_LENGTH = 100;

    /**
     * Số thẻ gói mỗi trang: 8 = 2 hàng × 4 cột trên màn rộng.
     */
    public static final int PAGE_SIZE = 8;

    /**
     * SRS 7.1.1: chỉ hiện % tiết kiệm với gói từ 3 tháng trở lên.
     */
    private static final int MIN_MONTHS_FOR_SAVING_PERCENT = 3;

    /**
     * Chỉ dùng để so thứ tự thời hạn khi sắp xếp (1 tháng ≈ 30 ngày), không
     * dùng để tính ngày hết hạn.
     */
    private static final int DAYS_PER_MONTH_FOR_SORTING = 30;

    /**
     * Nhãn của gói có tiết kiệm so với mua từng tháng (savingPercent khác null).
     */
    private static final String BADGE_SAVING = "Tiết kiệm hơn";
    private static final String BADGE_NEW = "Mới ra mắt";

    /**
     * Gói tạo trong vòng số ngày này được gắn nhãn "Mới ra mắt" (nếu chưa có
     * nhãn Tiết kiệm hơn).
     */
    private static final int NEW_PACKAGE_DAYS = 30;

    /**
     * Ảnh gói đặt trong webapp/images/packages/, tên file theo quy ước ở
     * resolveImagePath.
     */
    private static final String IMAGE_DIRECTORY_URL = "/images/packages/";
    private static final String DEFAULT_IMAGE_KEY = "default";
    private static final String[] IMAGE_EXTENSIONS = {"jpg", "png", "webp", "svg"};

    private final PackageDAO packageDAO = new PackageDAO();
    private final SystemConfigService systemConfigService = new SystemConfigService();

    // ===== Truy vấn màn Mua gói =====
    /**
     * Lấy dữ liệu màn Mua gói: thẻ gói đã lọc + sắp xếp + cắt trang, cùng dữ
     * liệu cột bộ lọc.
     *
     * @param criteria giá trị tìm kiếm / lọc / sắp xếp khách gửi lên (chưa kiểm
     * tra)
     * @param imageFileNames tên các file đang có trong images/packages (VD
     * "membership.svg"); null nếu không đọc được
     * @return dữ liệu màn hình, hoặc MSG04 khi lỗi DB / thiếu tham số cấu hình
     */
    public ServiceResult<PackageSelectionView> getPackageSelection(PackageSearchCriteria criteria,
            Set<String> imageFileNames) {
        try {
            // B1: gói đang bán (gồm cả PT để hiện thông tin), số phút giữ đơn P-09 và giờ mở cửa P-19
            List<Package> activePackages = packageDAO.findActiveForHomepage();
            int paymentDueMinutes = systemConfigService.getInt(SystemConfigService.PAYMENT_DUE_MINUTES);
            String openingHours = formatOpeningHours(systemConfigService.getString(SystemConfigService.OPENING_HOURS));

            // B2: dựng thẻ, lọc, sắp xếp
            PackageSelectionView view = buildSelection(activePackages, criteria, paymentDueMinutes,
                    openingHours, imageFileNames);
            return ServiceResult.ok(view);

        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        } catch (IllegalStateException e) {
            // Thiếu hoặc sai tham số P-09 / P-19 trong SystemConfigs: lỗi cấu hình, không phải lỗi khách
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /**
     * Gói này khách có tự mua online được không (UC-22: chỉ gói tập và vé ngày;
     * PT bán ở quầy). Hàm tạo đơn sau này phải gọi lại hàm này ở server, không
     * chỉ dựa vào việc ẩn nút trên giao diện.
     *
     * @param packageType loại gói (có thể null)
     * @return true nếu là Membership hoặc DayPass
     */
    public static boolean isOnlinePurchasable(Package.Type packageType) {
        boolean isMembership = packageType == Package.Type.MEMBERSHIP;
        boolean isDayPass = packageType == Package.Type.DAY_PASS;
        return isMembership || isDayPass;
    }

    // ===== Dựng danh sách thẻ (không đụng DB nên test được) =====
    /**
     * Dựng toàn bộ dữ liệu màn từ danh sách gói đã đọc sẵn. Tách riêng khỏi
     * getPackageSelection để kiểm thử không cần kết nối DB.
     *
     * <p>
     * Mọi giá trị khách gửi lên được chuẩn hóa chứ không báo lỗi (bộ lọc là URL
     * GET, ai cũng sửa tay được): từ khóa cắt 100 ký tự; loại gói / sắp xếp lạ
     * về mặc định; giá không phải số bị bỏ qua, nằm ngoài khoảng thật thì ép
     * vào khoảng, min lớn hơn max thì đổi chỗ; thời hạn không có trong dữ liệu
     * bị bỏ qua; trang sai ép về trang hợp lệ.</p>
     */
    PackageSelectionView buildSelection(List<Package> activePackages, PackageSearchCriteria criteria,
            int paymentDueMinutes, String openingHours, Set<String> imageFileNames) {
        // B1: chuẩn hóa đầu vào người dùng gửi lên
        String safeKeyword = normalizeKeyword(criteria.getKeyword());
        String safeTypeFilter = normalizeTypeFilter(criteria.getTypeFilter());
        String safeSortKey = normalizeSortKey(criteria.getSortKey());

        // B2: tách gói tập (Membership + DayPass) và gói PT, vì cách dựng thẻ khác nhau
        List<Package> gymPackages = new ArrayList<>();
        List<Package> ptPackages = new ArrayList<>();
        for (Package pkg : activePackages) {
            if (pkg.getPackageType().isPtService()) {
                ptPackages.add(pkg);
            } else {
                gymPackages.add(pkg);
            }
        }

        // B3: dựng thẻ giá, cất theo mã gói để ghép lại đúng thứ tự gốc ở B4
        Map<Integer, PackageCardView> cardsByPackageId = new LinkedHashMap<>();
        Map<Integer, Long> originalPriceByPackageId = new HashMap<>();
        for (PackageCardView card : buildGymCards(gymPackages, originalPriceByPackageId)) {
            cardsByPackageId.put(card.getPkg().getPackageId(), card);
        }
        for (Package pkg : ptPackages) {
            PackageCardView ptCard = new PackageCardView(pkg, pkg.getDurationMonths(), null, null);
            cardsByPackageId.put(pkg.getPackageId(), ptCard);
        }

        // B3b: gói tạo trong NEW_PACKAGE_DAYS ngày gần đây và chưa có nhãn Tiết kiệm hơn thì gắn "Mới ra mắt"
        LocalDateTime now = LocalDateTime.now();
        for (PackageCardView card : cardsByPackageId.values()) {
            if (card.getBadge() == null && isNewPackage(card.getPkg(), now)) {
                card.setBadge(BADGE_NEW);
            }
        }

        // B4: mỗi gói thành 1 thẻ có thông tin mua online / tại quầy
        List<PackageOptionView> allOptions = new ArrayList<>();
        for (Package pkg : activePackages) {
            PackageCardView card = cardsByPackageId.get(pkg.getPackageId());
            allOptions.add(buildOption(card, originalPriceByPackageId.get(pkg.getPackageId()), paymentDueMinutes,
                    openingHours, imageFileNames));
        }

        // B5: dữ liệu cột bộ lọc, tính trên TOÀN BỘ gói (query thời hạn, khoảng giá, số lượng)
        PackageSelectionView view = new PackageSelectionView();
        fillFilterPanel(view, allOptions, criteria, safeTypeFilter);
        Set<String> selectedDurationKeys = new HashSet<>();
        for (PackageFacetOption option : view.getDurationOptions()) {
            if (option.isSelected()) {
                selectedDurationKeys.add(option.getKey());
            }
        }

        // B6: lọc (giữ nguyên thứ tự)
        String normalizedKeyword = normalizeSearchText(safeKeyword);
        List<PackageOptionView> filteredOptions = new ArrayList<>();
        for (PackageOptionView option : allOptions) {
            Package pkg = option.getCard().getPkg();
            boolean matches = matchesTypeFilter(pkg, safeTypeFilter)
                    && matchesKeyword(pkg, normalizedKeyword)
                    && pkg.getPrice() >= view.getMinPrice()
                    && pkg.getPrice() <= view.getMaxPrice()
                    && matchesDuration(pkg, selectedDurationKeys)
                    && matchesBuyChannel(option, view.isBuyOnline(), view.isBuyCounterOnly());
            if (matches) {
                filteredOptions.add(option);
            }
        }

        // B7: sắp xếp
        sortOptions(filteredOptions, safeSortKey);

        // B8: cắt trang. Trang sai (nhỏ hơn 1 hoặc vượt tổng số trang) được ép về khoảng hợp lệ
        int filteredCount = filteredOptions.size();
        int totalPages = (filteredCount + PAGE_SIZE - 1) / PAGE_SIZE;
        if (totalPages < 1) {
            totalPages = 1;
        }
        int safePage = Math.max(1, Math.min(criteria.getPage(), totalPages));
        int fromIndex = (safePage - 1) * PAGE_SIZE;
        int toIndex = Math.min(fromIndex + PAGE_SIZE, filteredCount);
        List<PackageOptionView> pageOptions = new ArrayList<>(filteredOptions.subList(fromIndex, toIndex));

        view.setOptions(pageOptions);
        view.setTotalActiveCount(allOptions.size());
        view.setKeyword(safeKeyword);
        view.setTypeFilter(safeTypeFilter);
        view.setSortKey(safeSortKey);
        view.setOpeningHours(openingHours);
        view.setPage(safePage);
        view.setTotalPages(totalPages);
        view.setFilteredCount(filteredCount);
        return view;
    }

    // ===== Cột bộ lọc: query từ dữ liệu thật =====
    /**
     * Thời hạn của 1 lựa chọn lọc, gom từ các gói đang bán.
     */
    private static class DurationFacet {

        private final String key;
        private final String label;
        private final int sortDays;
        private int count;

        private DurationFacet(String key, String label, int sortDays) {
            this.key = key;
            this.label = label;
            this.sortDays = sortDays;
        }
    }

    /**
     * Điền dữ liệu cột bộ lọc (số lượng, thời hạn, khoảng giá, chip đang bật)
     * vào view.
     */
    private void fillFilterPanel(PackageSelectionView view, List<PackageOptionView> allOptions,
            PackageSearchCriteria criteria, String safeTypeFilter) {
        // B1: đếm theo loại, hình thức mua; tìm giá thấp nhất / cao nhất; gom thời hạn (chỉ gói tính theo tháng)
        int gymCount = 0;
        int dayPassCount = 0;
        int ptCount = 0;
        int onlineCount = 0;
        int minBound = Integer.MAX_VALUE;
        int maxBound = 0;
        Map<String, DurationFacet> facetsByKey = new LinkedHashMap<>();

        for (PackageOptionView option : allOptions) {
            Package pkg = option.getCard().getPkg();
            Package.Type packageType = pkg.getPackageType();
            if (packageType == Package.Type.MEMBERSHIP) {
                gymCount++;
            } else if (packageType == Package.Type.DAY_PASS) {
                dayPassCount++;
            } else {
                ptCount++;
            }
            if (option.isPurchasableOnline()) {
                onlineCount++;
            }
            minBound = Math.min(minBound, pkg.getPrice());
            maxBound = Math.max(maxBound, pkg.getPrice());

            // Vé ngày và buổi lẻ không có số tháng nên không vào bộ lọc Thời hạn (đã có ở bộ lọc Loại gói)
            if (isMonthBased(pkg)) {
                String durationKey = buildDurationKey(pkg);
                DurationFacet facet = facetsByKey.get(durationKey);
                if (facet == null) {
                    facet = new DurationFacet(durationKey, option.getDurationLabel(), getDurationDaysForSorting(pkg));
                    facetsByKey.put(durationKey, facet);
                }
                facet.count++;
            }
        }
        if (allOptions.isEmpty()) {
            minBound = 0;
        }
        int counterOnlyCount = allOptions.size() - onlineCount;

        // B2: thời hạn xếp ngắn → dài; chỉ giữ lựa chọn khách gửi lên nếu có thật trong dữ liệu
        List<DurationFacet> facets = new ArrayList<>(facetsByKey.values());
        facets.sort((first, second) -> Integer.compare(first.sortDays, second.sortDays));
        Set<String> requestedKeys = criteria.getDurationKeys();
        List<PackageFacetOption> durationOptions = new ArrayList<>();
        for (DurationFacet facet : facets) {
            boolean selected = requestedKeys.contains(facet.key);
            durationOptions.add(new PackageFacetOption("duration", facet.key, facet.label, facet.count, selected));
        }

        // B3: chuẩn hóa khoảng giá: thiếu → đầu mút; ngoài khoảng → ép vào; min > max → đổi chỗ
        int minPrice = clamp(criteria.getMinPrice() == null ? minBound : criteria.getMinPrice(), minBound, maxBound);
        int maxPrice = clamp(criteria.getMaxPrice() == null ? maxBound : criteria.getMaxPrice(), minBound, maxBound);
        if (minPrice > maxPrice) {
            int swap = minPrice;
            minPrice = maxPrice;
            maxPrice = swap;
        }

        // B4: hình thức mua: tick cả hai hoặc không tick gì = không lọc
        boolean buyOnline = criteria.isBuyOnline();
        boolean buyCounterOnly = criteria.isBuyCounterOnly();
        if (buyOnline == buyCounterOnly) {
            buyOnline = false;
            buyCounterOnly = false;
        }

        // B5: chip bộ lọc đang bật
        List<PackageFacetOption> chips = new ArrayList<>();
        if (!TYPE_ALL.equals(safeTypeFilter)) {
            chips.add(PackageFacetOption.chip("type", safeTypeFilter, buildTypeFilterLabel(safeTypeFilter)));
        }
        if (minPrice > minBound || maxPrice < maxBound) {
            String priceLabel = "Giá " + MoneyUtil.formatMoney(minPrice) + " – " + MoneyUtil.formatMoney(maxPrice);
            chips.add(PackageFacetOption.chip("price", "", priceLabel));
        }
        for (PackageFacetOption option : durationOptions) {
            if (option.isSelected()) {
                chips.add(PackageFacetOption.chip("duration", option.getKey(), option.getLabel()));
            }
        }
        if (buyOnline) {
            chips.add(PackageFacetOption.chip("buy", BUY_ONLINE, "Thanh toán trực tuyến"));
        }
        if (buyCounterOnly) {
            chips.add(PackageFacetOption.chip("buy", BUY_COUNTER, "Chỉ mua tại quầy"));
        }

        view.setAllCount(allOptions.size());
        view.setGymCount(gymCount);
        view.setDayPassCount(dayPassCount);
        view.setPtCount(ptCount);
        view.setDurationOptions(durationOptions);
        view.setMinBound(minBound);
        view.setMaxBound(maxBound);
        view.setMinPrice(minPrice);
        view.setMaxPrice(maxPrice);
        view.setOnlineCount(onlineCount);
        view.setCounterOnlyCount(counterOnlyCount);
        view.setBuyOnline(buyOnline);
        view.setBuyCounterOnly(buyCounterOnly);
        view.setChips(chips);
    }

    /**
     * Ép số vào khoảng [low, high].
     *
     * @param value giá trị cần ép
     * @param low cận dưới (tính cả biên)
     * @param high cận trên (tính cả biên)
     * @return value nếu nằm trong khoảng, ngược lại là biên gần nhất
     */
    private static int clamp(int value, int low, int high) {
        return Math.max(low, Math.min(value, high));
    }

    /**
     * Gói có thời hạn tính theo tháng không: gói tập và gói PT có, vé ngày và
     * buổi PT lẻ không.
     *
     * @param pkg gói cần kiểm tra
     * @return true nếu là Membership, PT_SINGLE hoặc PT_COUPLE
     */
    private boolean isMonthBased(Package pkg) {
        Package.Type packageType = pkg.getPackageType();
        boolean isMembership = packageType == Package.Type.MEMBERSHIP;
        boolean isPtByMonth = packageType == Package.Type.PT_SINGLE || packageType == Package.Type.PT_COUPLE;
        return isMembership || isPtByMonth;
    }

    /**
     * Mã thời hạn gửi lên URL, chỉ dùng cho gói tính theo tháng.
     *
     * @param pkg gói tính theo tháng (isMonthBased = true)
     * @return "month-" + số tháng, ví dụ Gói 3 Tháng → "month-3"
     */
    private String buildDurationKey(Package pkg) {
        return "month-" + pkg.getDurationMonths();
    }

    /**
     * Tên hiển thị của loại gói đang lọc, dùng cho chip "đang lọc".
     *
     * @param typeFilter đã chuẩn hóa, khác "all"
     * @return "Gói tập", "Vé ngày" hoặc "Gói PT"
     */
    private String buildTypeFilterLabel(String typeFilter) {
        if (TYPE_GYM.equals(typeFilter)) {
            return "Gói tập";
        }
        if (TYPE_DAY_PASS.equals(typeFilter)) {
            return "Vé ngày";
        }
        return "Gói PT";
    }

    // ===== Chuẩn hóa đầu vào =====
    /**
     * Từ khóa: null → rỗng, bỏ khoảng trắng đầu cuối, cắt tối đa 100 ký tự (SRS
     * 7.1.1).
     */
    private String normalizeKeyword(String keyword) {
        if (keyword == null) {
            return "";
        }
        String trimmedKeyword = keyword.trim();
        if (trimmedKeyword.length() > KEYWORD_MAX_LENGTH) {
            return trimmedKeyword.substring(0, KEYWORD_MAX_LENGTH).trim();
        }
        return trimmedKeyword;
    }

    /**
     * Loại gói: giá trị không nằm trong 4 giá trị hợp lệ thì về mặc định "all".
     */
    private String normalizeTypeFilter(String typeFilter) {
        boolean isGym = TYPE_GYM.equals(typeFilter);
        boolean isDayPass = TYPE_DAY_PASS.equals(typeFilter);
        boolean isPt = TYPE_PT.equals(typeFilter);
        if (isGym || isDayPass || isPt) {
            return typeFilter;
        }
        return TYPE_ALL;
    }

    /**
     * Kiểu sắp xếp: giá trị lạ thì về mặc định "default" (Mặc định).
     */
    private String normalizeSortKey(String sortKey) {
        boolean isSaving = SORT_SAVING.equals(sortKey);
        boolean isPriceAscending = SORT_PRICE_ASC.equals(sortKey);
        boolean isPriceDescending = SORT_PRICE_DESC.equals(sortKey);
        if (isSaving || isPriceAscending || isPriceDescending) {
            return sortKey;
        }
        return SORT_DEFAULT;
    }

    /**
     * Chuyển chữ thành dạng so sánh không phân biệt hoa thường và dấu tiếng
     * Việt (GBR-19): "Gói 3 Tháng" và "goi 3 thang" cho cùng kết quả "goi 3
     * thang". Chữ "đ" không tách dấu được bằng Normalizer nên đổi riêng thành
     * "d".
     */
    static String normalizeSearchText(String text) {
        if (text == null) {
            return "";
        }
        String lowerText = text.toLowerCase(Locale.ROOT);
        String decomposedText = Normalizer.normalize(lowerText, Normalizer.Form.NFD);
        String withoutMarks = decomposedText.replaceAll("\\p{M}+", "");
        String withoutDStroke = withoutMarks.replace('đ', 'd');
        return withoutDStroke.trim();
    }

    // ===== Lọc =====
    /**
     * Gói có thuộc loại đang lọc không: gym = Membership, day-pass = DayPass,
     * pt = mọi gói PT và buổi lẻ.
     */
    private boolean matchesTypeFilter(Package pkg, String typeFilter) {
        Package.Type packageType = pkg.getPackageType();
        if (TYPE_GYM.equals(typeFilter)) {
            return packageType == Package.Type.MEMBERSHIP;
        }
        if (TYPE_DAY_PASS.equals(typeFilter)) {
            return packageType == Package.Type.DAY_PASS;
        }
        if (TYPE_PT.equals(typeFilter)) {
            return packageType.isPtService();
        }
        return true;
    }

    /**
     * Tên gói có chứa từ khóa (đã chuẩn hóa) không; từ khóa rỗng thì khớp tất
     * cả.
     */
    private boolean matchesKeyword(Package pkg, String normalizedKeyword) {
        if (normalizedKeyword.isEmpty()) {
            return true;
        }
        String normalizedName = normalizeSearchText(pkg.getPackageName());
        return normalizedName.contains(normalizedKeyword);
    }

    /**
     * Gói có thuộc 1 trong các thời hạn đang tick không; chưa tick gì thì khớp
     * tất cả. Khi đã tick, vé ngày và buổi lẻ bị loại vì không có số tháng.
     */
    private boolean matchesDuration(Package pkg, Set<String> selectedDurationKeys) {
        if (selectedDurationKeys.isEmpty()) {
            return true;
        }
        if (!isMonthBased(pkg)) {
            return false;
        }
        return selectedDurationKeys.contains(buildDurationKey(pkg));
    }

    /**
     * Hình thức mua: chỉ tick "online" → gói online được; chỉ tick "tại quầy" →
     * gói không mua online được.
     */
    private boolean matchesBuyChannel(PackageOptionView option, boolean buyOnline, boolean buyCounterOnly) {
        if (buyOnline) {
            return option.isPurchasableOnline();
        }
        if (buyCounterOnly) {
            return !option.isPurchasableOnline();
        }
        return true;
    }

    // ===== Sắp xếp =====
    /**
     * Sắp xếp tại chỗ theo kiểu khách chọn; sortKey đã chuẩn hóa nên nhánh
     * cuối là Mặc định.
     *
     * @param options danh sách thẻ đã lọc
     * @param sortKey SORT_PRICE_ASC, SORT_PRICE_DESC, SORT_SAVING hoặc SORT_DEFAULT
     */
    private void sortOptions(List<PackageOptionView> options, String sortKey) {
        if (SORT_PRICE_ASC.equals(sortKey)) {
            options.sort(PackageSelectionService::compareByPriceAscending);
        } else if (SORT_PRICE_DESC.equals(sortKey)) {
            options.sort(PackageSelectionService::compareByPriceDescending);
        } else if (SORT_SAVING.equals(sortKey)) {
            options.sort(PackageSelectionService::compareBySaving);
        } else {
            options.sort(PackageSelectionService::compareByDefaultOrder);
        }
    }

    /**
     * Mặc định: gói mua được online trước, gói chỉ mua tại quầy (PT) sau. Trong
     * nhóm online: "Tiết kiệm hơn" → "Mới ra mắt" → gói tập → vé ngày. Trong
     * nhóm PT: "Mới ra mắt" trước. Cùng hạng thì thời hạn ngắn → dài, bằng nhau
     * thì gói rẻ trước.
     */
    private static int compareByDefaultOrder(PackageOptionView first, PackageOptionView second) {
        int firstRank = getDefaultRank(first);
        int secondRank = getDefaultRank(second);
        if (firstRank != secondRank) {
            return Integer.compare(firstRank, secondRank);
        }
        return compareByDuration(first, second);
    }

    /**
     * Hạng của 1 thẻ trong thứ tự Mặc định, số nhỏ đứng trước.
     *
     * @param option thẻ cần xếp hạng
     * @return 0 Tiết kiệm hơn, 1 online mới ra mắt, 2 gói tập, 3 vé ngày,
     * 4 PT mới ra mắt, 5 PT còn lại
     */
    private static int getDefaultRank(PackageOptionView option) {
        String badge = option.getCard().getBadge();
        boolean isNew = BADGE_NEW.equals(badge);
        if (option.isPurchasableOnline()) {
            if (BADGE_SAVING.equals(badge)) {
                return 0;
            }
            if (isNew) {
                return 1;
            }
            Package.Type packageType = option.getCard().getPkg().getPackageType();
            return packageType == Package.Type.MEMBERSHIP ? 2 : 3;
        }
        return isNew ? 4 : 5;
    }

    /**
     * % tiết kiệm cao → thấp; gói chưa có % xuống cuối; bằng nhau thì theo thời
     * hạn.
     */
    private static int compareBySaving(PackageOptionView first, PackageOptionView second) {
        int firstSaving = first.getCard().getSavingPercent() == null ? -1 : first.getCard().getSavingPercent();
        int secondSaving = second.getCard().getSavingPercent() == null ? -1 : second.getCard().getSavingPercent();
        if (firstSaving != secondSaving) {
            return Integer.compare(secondSaving, firstSaving);
        }
        return compareByDuration(first, second);
    }

    /**
     * Thời hạn ngắn → dài; cùng thời hạn thì gói rẻ đứng trước (cùng luật thứ
     * tự với trang chủ, luật 2.6).
     */
    private static int compareByDuration(PackageOptionView first, PackageOptionView second) {
        int firstDays = getDurationDaysForSorting(first.getCard().getPkg());
        int secondDays = getDurationDaysForSorting(second.getCard().getPkg());
        if (firstDays != secondDays) {
            return Integer.compare(firstDays, secondDays);
        }
        return Integer.compare(first.getCard().getPkg().getPrice(), second.getCard().getPkg().getPrice());
    }

    /**
     * Giá thấp → cao; bằng giá thì theo thời hạn.
     */
    private static int compareByPriceAscending(PackageOptionView first, PackageOptionView second) {
        int firstPrice = first.getCard().getPkg().getPrice();
        int secondPrice = second.getCard().getPkg().getPrice();
        if (firstPrice != secondPrice) {
            return Integer.compare(firstPrice, secondPrice);
        }
        return compareByDuration(first, second);
    }

    /**
     * Giá cao → thấp; bằng giá thì theo thời hạn.
     */
    private static int compareByPriceDescending(PackageOptionView first, PackageOptionView second) {
        int firstPrice = first.getCard().getPkg().getPrice();
        int secondPrice = second.getCard().getPkg().getPrice();
        if (firstPrice != secondPrice) {
            return Integer.compare(secondPrice, firstPrice);
        }
        return compareByDuration(first, second);
    }

    /**
     * Quy thời hạn ra số ngày chỉ để so thứ tự: buổi lẻ (không thời hạn) = 0
     * nên đứng đầu, như PackageDAO.
     */
    private static int getDurationDaysForSorting(Package pkg) {
        Integer durationValue = pkg.getDurationValue();
        if (durationValue == null) {
            return 0;
        }
        if ("Month".equals(pkg.getDurationUnit())) {
            return durationValue * DAYS_PER_MONTH_FOR_SORTING;
        }
        return durationValue;
    }

    // ===== Dựng thẻ giá gói tập (sao chép từ HomeService.buildGymCards) =====
    /**
     * Tạo thẻ cho gói tập: giá/tháng, % tiết kiệm so với gói 1 tháng, rồi gắn
     * nhãn. Khác HomeService ở một điểm: % tiết kiệm chỉ tính cho gói từ 3
     * tháng (SRS 7.1.1).
     *
     * Ví dụ seed (gói 1 tháng = 799.000): Gói 3 Tháng 2.190.000 →
     * 730.000/tháng, tiết kiệm 9%; Gói 6 Tháng 3.990.000 → 665.000/tháng, tiết
     * kiệm 17%.
     */
    private List<PackageCardView> buildGymCards(List<Package> gymPackages, Map<Integer, Long> originalPrices) {
        // B1: tìm giá gói 1 tháng làm mốc; không có gói 1 tháng thì không tính % tiết kiệm
        Long oneMonthPrice = null;
        for (Package pkg : gymPackages) {
            boolean isMembership = pkg.getPackageType() == Package.Type.MEMBERSHIP;
            boolean isOneMonth = pkg.getDurationMonths() == 1;
            if (isMembership && isOneMonth) {
                oneMonthPrice = (long) pkg.getPrice();
                break;
            }
        }

        // B2: tính cho từng gói
        List<PackageCardView> cards = new ArrayList<>();
        List<PackageCardView> membershipCards = new ArrayList<>();
        for (Package pkg : gymPackages) {
            int months = pkg.getDurationMonths();
            Long monthlyPrice = null;
            Integer savingPercent = null;

            if (months > 1) {
                monthlyPrice = Math.round((double) pkg.getPrice() / months);
            }

            boolean canShowSaving = months >= MIN_MONTHS_FOR_SAVING_PERCENT && oneMonthPrice != null;
            if (canShowSaving) {
                long priceIfBuyMonthly = oneMonthPrice * months;
                long saved = priceIfBuyMonthly - pkg.getPrice();
                if (saved > 0) {
                    savingPercent = (int) Math.round(saved * 100.0 / priceIfBuyMonthly);
                    originalPrices.put(pkg.getPackageId(), priceIfBuyMonthly);
                }
            }

            PackageCardView card = new PackageCardView(pkg, months, monthlyPrice, savingPercent);
            cards.add(card);
            if (pkg.getPackageType() == Package.Type.MEMBERSHIP) {
                membershipCards.add(card);
            }
        }

        // B3: gắn nhãn "Tiết kiệm hơn" cho mọi gói hội viên có tiết kiệm
        assignBadges(membershipCards);
        return cards;
    }

    /**
     * Gắn nhãn "Tiết kiệm hơn" cho gói hội viên có savingPercent khác null
     * (rẻ hơn mua từng tháng). Gói 1 tháng và gói không tiết kiệm giữ nhãn
     * null.
     *
     * @param membershipCards thẻ các gói Membership, đã tính savingPercent
     */
    private void assignBadges(List<PackageCardView> membershipCards) {
        for (PackageCardView card : membershipCards) {
            boolean hasSaving = card.getSavingPercent() != null;
            if (hasSaving) {
                card.setBadge(BADGE_SAVING);
            }
        }
    }

    /**
     * Gói tạo trong vòng NEW_PACKAGE_DAYS ngày gần đây (chưa có ngày tạo thì
     * không tính là mới).
     */
    private boolean isNewPackage(Package pkg, LocalDateTime now) {
        LocalDateTime createdAt = pkg.getCreatedAt();
        if (createdAt == null) {
            return false;
        }
        return !createdAt.isBefore(now.minusDays(NEW_PACKAGE_DAYS));
    }

    // ===== Thông tin mua online / tại quầy của 1 thẻ =====
    /**
     * Gói thẻ giá thành 1 thẻ hiển thị: kênh mua, nhãn chữ, câu hướng dẫn,
     * ảnh, giá gốc và số tiền tiết kiệm.
     *
     * @param card thẻ giá đã có badge
     * @param originalPrice giá nếu mua từng tháng; null nếu gói không tiết kiệm
     * @param paymentDueMinutes số phút giữ đơn, tham số P-09
     * @param openingHours giờ mở cửa đã định dạng, null nếu chưa cấu hình
     * @param imageFileNames tên file có trong images/packages, null nếu không đọc được
     * @return thẻ để JSP in ra; savedAmount = originalPrice - giá bán, null khi originalPrice null
     */
    private PackageOptionView buildOption(PackageCardView card, Long originalPrice, int paymentDueMinutes,
            String openingHours, Set<String> imageFileNames) {
        Package pkg = card.getPkg();
        Package.Type packageType = pkg.getPackageType();

        boolean purchasableOnline = isOnlinePurchasable(packageType);
        String typeLabel = buildTypeLabel(packageType);
        String durationLabel = buildDurationLabel(pkg);
        String channelLabel = buildChannelLabel(packageType);
        String purchaseHint = buildPurchaseHint(packageType, paymentDueMinutes, openingHours);
        String imagePath = resolveImagePath(pkg, imageFileNames);

        Long savedAmount = originalPrice == null ? null : originalPrice - pkg.getPrice();

        return new PackageOptionView(card, purchasableOnline, originalPrice, savedAmount, typeLabel,
                durationLabel, channelLabel, purchaseHint, imagePath);
    }

    /**
     * Tên loại gói in trên thẻ.
     *
     * @param packageType loại gói
     * @return "Gói tập", "Vé ngày", "Gói PT", "Gói PT đôi" hoặc "Buổi PT lẻ"
     */
    private String buildTypeLabel(Package.Type packageType) {
        switch (packageType) {
            case MEMBERSHIP:
                return "Gói tập";
            case DAY_PASS:
                return "Vé ngày";
            case PT_SINGLE:
                return "Gói PT";
            case PT_COUPLE:
                return "Gói PT đôi";
            case SINGLE_SESSION:
                return "Buổi PT lẻ";
            default:
                return "";
        }
    }

    /**
     * Thời hạn in trên thẻ, ví dụ Gói 3 Tháng → "3 tháng", Gói Ngày → "1 ngày".
     *
     * @param pkg gói cần in thời hạn
     * @return "n tháng", "1 ngày" hoặc "1 buổi"
     */
    private String buildDurationLabel(Package pkg) {
        switch (pkg.getPackageType()) {
            case MEMBERSHIP:
            case PT_SINGLE:
            case PT_COUPLE:
                return pkg.getDurationMonths() + " tháng";
            case DAY_PASS:
                return "1 ngày";
            case SINGLE_SESSION:
                return "1 buổi";
            default:
                return "";
        }
    }

    /**
     * Câu ngắn cố định trên thẻ: điện thoại không có hover nên thông tin kênh
     * mua không chỉ nằm trong tooltip.
     */
    private String buildChannelLabel(Package.Type packageType) {
        if (isOnlinePurchasable(packageType)) {
            return "Thanh toán trực tuyến hoặc tại quầy";
        }
        if (packageType == Package.Type.SINGLE_SESSION) {
            return "Chọn PT và khung giờ để đặt buổi";
        }
        return "Chỉ mua tại quầy";
    }

    /**
     * Câu giải thích đầy đủ cách mua (tooltip của nút và đoạn hướng dẫn trong
     * popup). paymentDueMinutes lấy từ tham số P-09, openingHours từ P-19;
     * không viết cứng số trong chữ.
     */
    private String buildPurchaseHint(Package.Type packageType, int paymentDueMinutes, String openingHours) {
        if (isOnlinePurchasable(packageType)) {
            return "Thanh toán VNPay. Hoặc mua tại quầy.";
        }

        if (packageType == Package.Type.SINGLE_SESSION) {
            return "Bấm Chọn buổi để chọn PT, ngày và khung giờ. Sau khi xác nhận lịch, bạn sẽ được chuyển sang chi tiết thanh toán.";
        }

        String counterHint = "Đăng ký tại quầy lễ tân.";
        if (openingHours != null) {
            counterHint = counterHint + " Mở cửa " + openingHours + ".";
        }
        return counterHint;
    }

    // ===== Ảnh gói theo quy ước tên file (không cần cột ảnh trong database) =====
    /**
     * Chọn ảnh cho gói theo quy ước tên file trong images/packages/, thử từ cụ
     * thể đến chung: Gói 3 tháng → membership-3m → membership → default PT đôi
     * 1 tháng → pt-couple-1m → pt-couple → default Vé ngày → day-pass →
     * default; buổi PT lẻ → single-session → default Mỗi tên thử lần lượt các
     * đuôi jpg, png, webp, svg. Thêm file đúng tên là gói đó có ảnh riêng,
     * không phải sửa code.
     *
     * @param pkg gói cần chọn ảnh
     * @param imageFileNames tên file đang có trong thư mục ảnh (null nếu không
     * đọc được)
     * @return đường dẫn ảnh tính từ context path, hoặc null nếu không có file
     * nào phù hợp
     */
    private String resolveImagePath(Package pkg, Set<String> imageFileNames) {
        if (imageFileNames == null) {
            return null;
        }

        // B1: lập danh sách tên (không đuôi) cần thử, từ cụ thể đến chung
        List<String> candidateKeys = new ArrayList<>();
        String typeKey = buildImageTypeKey(pkg.getPackageType());
        boolean hasMonths = pkg.getDurationMonths() > 0;
        if (hasMonths) {
            candidateKeys.add(typeKey + "-" + pkg.getDurationMonths() + "m");
        }
        candidateKeys.add(typeKey);
        candidateKeys.add(DEFAULT_IMAGE_KEY);

        // B2: tên nào có file thật trong thư mục thì dùng
        for (String candidateKey : candidateKeys) {
            for (String extension : IMAGE_EXTENSIONS) {
                String fileName = candidateKey + "." + extension;
                if (imageFileNames.contains(fileName)) {
                    return IMAGE_DIRECTORY_URL + fileName;
                }
            }
        }
        return null;
    }

    /**
     * Tiền tố tên file ảnh theo loại gói, ví dụ MEMBERSHIP → "membership".
     *
     * @param packageType loại gói
     * @return tiền tố tên file, hoặc "default" khi không nhận ra loại
     */
    private String buildImageTypeKey(Package.Type packageType) {
        switch (packageType) {
            case MEMBERSHIP:
                return "membership";
            case DAY_PASS:
                return "day-pass";
            case PT_SINGLE:
                return "pt-single";
            case PT_COUPLE:
                return "pt-couple";
            case SINGLE_SESSION:
                return "single-session";
            default:
                return DEFAULT_IMAGE_KEY;
        }
    }

    // ===== Hỗ trợ =====
    /**
     * Giờ mở cửa "05:30-22:00" → "05:30 – 22:00" (cùng cách hiển thị với trang
     * chủ); null giữ nguyên.
     */
    private String formatOpeningHours(String openingHours) {
        if (openingHours == null) {
            return null;
        }
        return openingHours.replace("-", " – ");
    }
}