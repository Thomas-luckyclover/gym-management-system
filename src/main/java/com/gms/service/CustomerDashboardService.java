package com.gms.service;

import com.gms.constant.Messages;
import com.gms.dao.AccountDAO;
import com.gms.dao.CustomerDAO;
import com.gms.dao.CustomerDashboardDAO;
import com.gms.dao.PersonDAO;
import com.gms.dto.CustomerDashboardView;
import com.gms.dto.CustomerDashboardView.BannerItem;
import com.gms.dto.CustomerDashboardView.SubscriptionRow;
import com.gms.dto.CustomerDashboardView.UpcomingSessionRow;
import com.gms.dto.ServiceResult;
import com.gms.entity.Account;
import com.gms.entity.Customer;
import com.gms.entity.Person;
import com.gms.util.DateTimeUtil;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Gom dữ liệu cho Customer Dashboard (#48, UC-41, SRS 9.1.1).
 *
 * Dashboard CHỈ ĐỌC trạng thái trong DB (GBR-14): trạng thái hết hạn, bảo lưu,
 * tự đóng buổi là việc của JOB-01…04, không tự tính lại ở đây.
 *
 * Bố cục theo "05_Dashboard_Rules.md" mục 1:
 * - Thông tin hồ sơ (tên, avatar, mã khách, trạng thái)
 * - Được vào phòng tập không (canUseGym — DBR-47, DBR-48, CBR-58)
 * - Gói tập đang dùng / chưa bắt đầu (kèm số buổi PT nếu có — [CHỐT-D2])
 * - Buổi PT sắp tới (5 buổi Scheduled gần nhất — [CHỐT-D3])
 * - Banner cảnh báo (5 loại — [CHỐT-D1])
 *
 * @author Nguyen Tuan Minh
 */
public class CustomerDashboardService {

    /** Mốc "sắp hết hạn" mặc định (ngày) khi P-21 chưa có trong SystemConfigs. */
    private static final int DEFAULT_EXPIRING_SOON_DAYS = 7;

    /** Mã tham số P-21: số ngày cảnh báo gói sắp hết hạn ([CHỐT-D1]). */
    private static final String EXPIRING_SOON_DAYS_KEY = "P-21";

    private final AccountDAO accountDAO = new AccountDAO();
    private final PersonDAO personDAO = new PersonDAO();
    private final CustomerDAO customerDAO = new CustomerDAO();
    private final CustomerDashboardDAO dashboardDAO = new CustomerDashboardDAO();
    private final SystemConfigService systemConfigService = new SystemConfigService();

    public ServiceResult<CustomerDashboardView> getDashboard(int accountId) {
        try {
            // B1: xác thực tài khoản là Customer đang hoạt động (GBR-10)
            Account account = accountDAO.findById(accountId);
            if (account == null) {
                return ServiceResult.fail(Messages.MSG07_NO_PERMISSION);
            }

            Person person = personDAO.findById(account.getPersonId());
            Customer customer = customerDAO.findByPersonId(account.getPersonId());
            if (customer == null) {
                return ServiceResult.fail(Messages.MSG07_NO_PERMISSION);
            }

            int customerId = customer.getCustomerId();
            LocalDateTime now = DateTimeUtil.now();

            // B2: kiểm tra quyền vào phòng tập hôm nay
            boolean canUseGym = dashboardDAO.canUseGymToday(customerId);

            // B3: danh sách gói tập đang dùng / chưa bắt đầu + gói đôi là partner
            List<SubscriptionRow> subscriptions = dashboardDAO.findActiveSubscriptions(customerId);
            List<SubscriptionRow> partnerSubs = dashboardDAO.findPartnerSubscriptions(customerId);
            subscriptions.addAll(partnerSubs);

            // B4: buổi PT sắp tới
            List<UpcomingSessionRow> upcomingSessions = dashboardDAO.findUpcomingSessions(customerId, now);

            // B5: banner cảnh báo
            List<BannerItem> banners = buildBanners(customer, subscriptions, customerId, now);

            // B6: gom view
            String fullName = person != null ? person.getFullName() : "";
            String avatar = person != null ? person.getAvatar() : null;

            CustomerDashboardView view = new CustomerDashboardView(
                    fullName,
                    avatar,
                    customer.getCustomerCode(),
                    customer.getStatus(),
                    canUseGym,
                    subscriptions,
                    upcomingSessions,
                    banners);

            return ServiceResult.ok(view);

        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    // ===== Banner cảnh báo ([CHỐT-D1]) =====

    private List<BannerItem> buildBanners(Customer customer, List<SubscriptionRow> subscriptions,
            int customerId, LocalDateTime now) throws SQLException {

        List<BannerItem> dangerBanners = new ArrayList<>();
        List<BannerItem> warningBanners = new ArrayList<>();

        String status = customer.getStatus();

        // Banner 1 (đỏ): hồ sơ Expired
        if ("Expired".equals(status)) {
            dangerBanners.add(new BannerItem("danger", "Gói tập đã hết hạn", "EXPIRED"));
        }

        // Banner 2 (vàng): hồ sơ Frozen — hiện khoảng bảo lưu
        if ("Frozen".equals(status)) {
            String freezeMessage = buildFreezeMessage(subscriptions);
            warningBanners.add(new BannerItem("warning", freezeMessage, "FROZEN"));
        }

        // Banner 3 (đỏ): có buổi PT rơi vào ngày không có gói tập (CBR-66)
        int missingCount = dashboardDAO.countSessionsMissingMembership(customerId);
        if (missingCount > 0) {
            String message = "Có " + missingCount + " buổi PT rơi vào ngày không có gói tập";
            dangerBanners.add(new BannerItem("danger", message, "MISSING_MEMBERSHIP"));
        }

        // Banner 4 (vàng): gói tập sắp hết hạn (≤ P-21 ngày), không hiện nếu đã có gói
        // nối tiếp (DBR-52)
        buildExpiringSoonBanner(subscriptions, now.toLocalDate(), warningBanners);

        // Banner 5 (vàng): đơn Pending chờ thanh toán (GBR-27)
        Object[] pendingOrder = dashboardDAO.findPendingOrder(customerId);
        if (pendingOrder != null) {
            int orderId = (int) pendingOrder[0];
            String message = "Đơn #" + orderId + " đang chờ thanh toán";
            warningBanners.add(new BannerItem("warning", message, "PENDING_ORDER"));
        }

        // Gộp: đỏ trước, vàng sau
        List<BannerItem> banners = new ArrayList<>(dangerBanners);
        banners.addAll(warningBanners);
        return banners;
    }

    private String buildFreezeMessage(List<SubscriptionRow> subscriptions) {
        for (SubscriptionRow sub : subscriptions) {
            boolean hasFreezeInfo = "Frozen".equals(sub.getStatus()) && sub.getFreezeEnd() != null;
            if (hasFreezeInfo) {
                return "Gói tập đang bảo lưu đến " + DateTimeUtil.formatDate(sub.getFreezeEnd());
            }
        }
        return "Gói tập đang bảo lưu";
    }

    private void buildExpiringSoonBanner(List<SubscriptionRow> subscriptions, LocalDate today,
            List<BannerItem> warningBanners) throws SQLException {

        // B1: đọc P-21, nếu chưa seed thì dùng mặc định 7 ngày
        int expiringSoonDays = DEFAULT_EXPIRING_SOON_DAYS;
        try {
            expiringSoonDays = systemConfigService.getInt(EXPIRING_SOON_DAYS_KEY);
        } catch (IllegalStateException e) {
            // P-21 chưa có trong SystemConfigs — dùng mặc định
        }

        // B2: kiểm tra có gói nối tiếp (chưa bắt đầu) không
        boolean hasQueuedSub = false;
        for (SubscriptionRow sub : subscriptions) {
            if (sub.isPending()) {
                hasQueuedSub = true;
                break;
            }
        }
        if (hasQueuedSub) {
            return;
        }

        // B3: tìm gói Membership/DayPass Active sắp hết hạn
        for (SubscriptionRow sub : subscriptions) {
            boolean isActiveGymSub = "Active".equals(sub.getStatus())
                    && !sub.isPtPackage();
            if (!isActiveGymSub) {
                continue;
            }
            long daysLeft = ChronoUnit.DAYS.between(today, sub.getEndDate());
            boolean isExpiringSoon = daysLeft >= 0 && daysLeft <= expiringSoonDays;
            if (isExpiringSoon) {
                String message = "Gói tập hết hạn sau " + daysLeft + " ngày ("
                        + DateTimeUtil.formatDate(sub.getEndDate()) + ")";
                warningBanners.add(new BannerItem("warning", message, "EXPIRING"));
                break;
            }
        }
    }
}
