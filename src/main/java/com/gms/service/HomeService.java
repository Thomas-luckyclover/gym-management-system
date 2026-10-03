package com.gms.service;

import com.gms.constant.Messages;
import com.gms.dao.EmployeeDAO;
import com.gms.dao.PackageDAO;
import com.gms.dto.HomePageView;
import com.gms.dto.PackageCardView;
import com.gms.dto.ServiceResult;
import com.gms.entity.BlogPost;
import com.gms.entity.Package;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Gom dữ liệu cho trang chủ (luật 2.6, kịch bản test H01–H07).
 *
 * Trang chủ có 5 khối lấy từ DB:
 *   Số PT + danh sách PT → EmployeeDAO.findWorkingPts()
 *   Bảng giá gói tập     → PackageDAO + tính giá/tháng, % tiết kiệm, nhãn
 *   Bảng giá PT          → PackageDAO
 *   Giờ mở cửa           → tham số P-19
 *   3 bài viết mới       → BlogService.getLatestPosts(3)
 *
 * @author Le Cong Minh
 */
public class HomeService {

    private static final String BADGE_POPULAR = "Phổ biến nhất";
    private static final String BADGE_BEST_SAVING = "Tiết kiệm nhất";
    private static final int LATEST_POST_LIMIT = 3;

    private final PackageDAO packageDAO = new PackageDAO();
    private final EmployeeDAO employeeDAO = new EmployeeDAO();
    private final SystemConfigService systemConfigService = new SystemConfigService();
    private final BlogService blogService = new BlogService();

    /**
     * Lấy toàn bộ dữ liệu trang chủ trong 1 lần gọi.
     *
     * @return thành công kèm HomePageView; thất bại (lỗi DB) kèm MSG04
     */
    public ServiceResult<HomePageView> getHomePage() {
        try {
            HomePageView home = new HomePageView();

            // B1: lấy gói đang bán, chia 2 nhóm: gói tập / gói PT
            List<Package> gymPackages = new ArrayList<>();
            List<Package> ptPackages = new ArrayList<>();
            for (Package pkg : packageDAO.findActiveForHomepage()) {
                if (pkg.getPackageType().isPtService()) {
                    ptPackages.add(pkg);
                } else {
                    gymPackages.add(pkg);
                }
            }

            // B2: gói tập cần tính thêm giá/tháng, % tiết kiệm, nhãn; gói PT chỉ cần số tháng
            home.setGymPackages(buildGymCards(gymPackages));
            List<PackageCardView> ptCards = new ArrayList<>();
            for (Package pkg : ptPackages) {
                ptCards.add(new PackageCardView(pkg, toMonths(pkg), null, null));
            }
            home.setPtPackages(ptCards);

            // B3: PT đang làm
            home.setPts(employeeDAO.findWorkingPts());

            // B4: giờ mở cửa, VD "05:30-22:00" → "05:30 – 22:00"
            String openingHours = systemConfigService.getString(SystemConfigService.OPENING_HOURS);
            if (openingHours != null) {
                home.setOpeningHours(openingHours.replace("-", " – "));
            }

            // B5: 3 bài mới nhất — lỗi thì chỉ ẩn khối blog, không làm hỏng cả trang chủ
            ServiceResult<List<BlogPost>> postResult = blogService.getLatestPosts(LATEST_POST_LIMIT);
            if (postResult.isSuccess()) {
                home.setLatestPosts(postResult.getData());
            }

            return ServiceResult.ok(home);

        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /**
     * Tạo thẻ giá cho gói tập: tính giá/tháng, % tiết kiệm so với gói 1 tháng, rồi gắn nhãn.
     *
     * Ví dụ seed (gói 1 tháng = 799.000):
     *   Gói Ngày     → không tính gì
     *   Gói 1 Tháng  → không tính gì (là mốc so sánh)
     *   Gói 3 Tháng  → 2.190.000 / 3 = 730.000/tháng; mua lẻ 3 × 799.000 = 2.397.000 → tiết kiệm 9%  → "Phổ biến nhất"
     *   Gói 6 Tháng  → 3.990.000 / 6 = 665.000/tháng; mua lẻ 6 × 799.000 = 4.794.000 → tiết kiệm 17% → "Tiết kiệm nhất"
     */
    private List<PackageCardView> buildGymCards(List<Package> gymPackages) {
        // B1: tìm giá gói 1 tháng làm mốc; không có gói 1 tháng → không tính % tiết kiệm
        Long oneMonthPrice = null;
        for (Package pkg : gymPackages) {
            if (pkg.getPackageType() == Package.Type.MEMBERSHIP && toMonths(pkg) == 1) {
                oneMonthPrice = (long) pkg.getPrice();
                break;
            }
        }

        // B2: tính cho từng gói
        List<PackageCardView> cards = new ArrayList<>();
        List<PackageCardView> membershipCards = new ArrayList<>();
        for (Package pkg : gymPackages) {
            int months = toMonths(pkg);
            Long monthlyPrice = null;
            Integer savingPercent = null;

            if (months > 1) {
                monthlyPrice = Math.round((double) pkg.getPrice() / months);
                if (oneMonthPrice != null) {
                    long priceIfBuyMonthly = oneMonthPrice * months;
                    long saved = priceIfBuyMonthly - pkg.getPrice();
                    if (saved > 0) {
                        savingPercent = (int) Math.round(saved * 100.0 / priceIfBuyMonthly);
                    }
                }
            }

            PackageCardView card = new PackageCardView(pkg, months, monthlyPrice, savingPercent);
            cards.add(card);
            if (pkg.getPackageType() == Package.Type.MEMBERSHIP) {
                membershipCards.add(card);
            }
        }

        // B3: gắn nhãn — mỗi nhãn đúng 1 gói
        assignBadges(membershipCards);
        return cards;
    }

    /**
     * "Tiết kiệm nhất" = gói có % tiết kiệm cao nhất.
     * "Phổ biến nhất"  = gói tháng dài thứ 2 (list đã sắp ngắn → dài); trùng gói "Tiết kiệm nhất" thì bỏ.
     * Nhóm muốn đổi tiêu chí (VD đếm gói bán chạy) → chỉ sửa hàm này.
     */
    private void assignBadges(List<PackageCardView> membershipCards) {
        // B1: tìm gói tiết kiệm nhất
        PackageCardView bestSaving = null;
        for (PackageCardView card : membershipCards) {
            Integer saving = card.getSavingPercent();
            if (saving != null && (bestSaving == null || saving > bestSaving.getSavingPercent())) {
                bestSaving = card;
            }
        }
        if (bestSaving != null) {
            bestSaving.setBadge(BADGE_BEST_SAVING);
        }

        // B2: gói dài thứ 2 là "Phổ biến nhất" (cần ít nhất 2 gói tháng)
        if (membershipCards.size() >= 2) {
            PackageCardView popular = membershipCards.get(membershipCards.size() - 2);
            if (popular != bestSaving) {
                popular.setBadge(BADGE_POPULAR);
            }
        }
    }

    /**
     * Số tháng của gói (DBR-19); gói ngày và buổi lẻ → 0.
     */
    private int toMonths(Package pkg) {
        return pkg.getDurationMonths();
    }
}
