package com.gms.service;

import com.gms.constant.Messages;
import com.gms.dao.AccountDAO;
import com.gms.dao.CustomerDAO;
import com.gms.dao.CustomerDashboardDAO;
import com.gms.dao.PersonDAO;
import com.gms.dto.CustomerDashboardView;
import com.gms.dto.CustomerDashboardView.AttendanceView;
import com.gms.dto.CustomerDashboardView.BannerItem;
import com.gms.dto.CustomerDashboardView.CalendarCell;
import com.gms.dto.CustomerDashboardView.NotificationRow;
import com.gms.dto.CustomerDashboardView.SubscriptionRow;
import com.gms.dto.CustomerDashboardView.UpcomingSessionRow;
import com.gms.dto.ServiceResult;
import com.gms.dto.StartDatePicker;
import com.gms.entity.Account;
import com.gms.entity.Customer;
import com.gms.entity.Person;
import com.gms.util.DateTimeUtil;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
 *
 * ❓ VẤN ĐÁP (mỗi câu kèm luồng file để mở theo khi trả lời):
 *   1) Dashboard khách hiển thị gì? → hồ sơ (tên/mã/avatar), "vào phòng tập được không" (canUseGym), gói tập đang dùng, 5 buổi PT sắp tới, banner cảnh báo.
 *      File: servlet/customer/CustomerDashboardServlet.java → service/CustomerDashboardService.java → dao/CustomerDashboardDAO.java → WEB-INF/jsp/customer/dashboard.jsp.
 *   2) canUseGym tính thế nào? → CustomerDashboardDAO.canUseGymToday: có Membership/DayPass Active phủ hôm nay và không bảo lưu.
 *      File: service/CustomerDashboardService.java → dao/CustomerDashboardDAO.java (canUseGymToday).
 *   3) Dashboard có tự đổi trạng thái gói không? → KHÔNG (GBR-14, chỉ đọc): hết hạn / bảo lưu / tự đóng buổi là việc của JOB-01…04.
 *      File: dao/CustomerDashboardDAO.java (chỉ SELECT) — việc đổi trạng thái nằm ở job/*.java (JOB-01…04).
 */
public class CustomerDashboardService {

    /** Mốc "sắp hết hạn" mặc định (ngày) khi P-21 chưa có trong SystemConfigs. */
    private static final int DEFAULT_EXPIRING_SOON_DAYS = 7;

    /** Mã tham số P-21: số ngày cảnh báo gói sắp hết hạn ([CHỐT-D1]). */
    private static final String EXPIRING_SOON_DAYS_KEY = "P-21";

    /** Chuỗi từ mức này trở lên thì được chào bằng màn "chuỗi tập" sau khi đăng nhập (mở rộng ngoài SRS). */
    private static final int STREAK_INTRO_MIN_DAYS = 3;

    /** Trạng thái thẻ gói tập trên dashboard (JSP chọn kiểu hiển thị theo giá trị này). */
    private static final String CARD_ACTIVE = "ACTIVE";
    private static final String CARD_EXPIRING = "EXPIRING";
    private static final String CARD_FROZEN = "FROZEN";
    private static final String CARD_EXPIRED = "EXPIRED";
    private static final String CARD_NONE = "NONE";

    /** Số thông báo gần nhất hiện trong chuông. */
    private static final int NOTIFICATION_LIMIT = 5;

    private final AccountDAO accountDAO = new AccountDAO();
    private final PersonDAO personDAO = new PersonDAO();
    private final CustomerDAO customerDAO = new CustomerDAO();
    private final CustomerDashboardDAO dashboardDAO = new CustomerDashboardDAO();
    private final SystemConfigService systemConfigService = new SystemConfigService();
    private final SubscriptionStartDateService startDateService = new SubscriptionStartDateService();

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

            // B3b: không còn gói tập nào chạy thì lấy gói Expired gần nhất để thẻ gói báo "Đã hết hạn"
            boolean hasRunningMembership = false;
            for (SubscriptionRow sub : subscriptions) {
                boolean running = "Active".equals(sub.getStatus()) || "Frozen".equals(sub.getStatus());
                if (!sub.isPtPackage() && running) {
                    hasRunningMembership = true;
                    break;
                }
            }
            if (!hasRunningMembership) {
                SubscriptionRow expired = dashboardDAO.findLatestExpiredMembership(customerId);
                if (expired != null) {
                    subscriptions.add(expired);
                }
            }

            // Chỉ chuẩn bị nhãn giao diện theo ngày hiệu lực; JOB vẫn chịu trách nhiệm ghi trạng thái DB.
            prepareSubscriptionPresentation(subscriptions, now.toLocalDate());

            // B4: buổi PT sắp tới
            List<UpcomingSessionRow> upcomingSessions = dashboardDAO.findUpcomingSessions(customerId, now);

            // B5: banner cảnh báo
            List<BannerItem> banners = buildBanners(customer, subscriptions, customerId, now);

            // B6: chuyên cần (chuỗi theo tháng, kỷ lục, lịch tháng) — mở rộng ngoài SRS
            AttendanceView attendance = buildAttendance(customerId, now.toLocalDate());

            // B7: thông báo cho chuông — lỗi đọc thông báo không được làm hỏng dashboard
            List<NotificationRow> notifications = new ArrayList<>();
            int unreadCount = 0;
            try {
                notifications = dashboardDAO.findRecentNotifications(accountId, NOTIFICATION_LIMIT);
                unreadCount = dashboardDAO.countUnreadNotifications(accountId);
            } catch (SQLException e) {
                e.printStackTrace();
            }

            // B8: trạng thái thẻ gói tập
            String membershipCardState = buildMembershipCardState(subscriptions, now.toLocalDate());

            // B9: gom view
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
                    banners,
                    attendance,
                    notifications,
                    unreadCount,
                    membershipCardState);

            // Lịch 7 ngày độc lập với TOP 5 buổi sắp tới; mỗi ngày có danh sách riêng, kể cả ngày trống.
            List<UpcomingSessionRow> weekSessions = dashboardDAO.findWeekSessions(customerId, now);
            view.setScheduleDays(buildScheduleDays(now.toLocalDate(), weekSessions));
            view.setTrainers(groupTrainerPackages(dashboardDAO.findTrainers(customerId), subscriptions));

            // Thẻ Gói tập: hiện ô "Đổi ngày" nếu gói còn đổi được. Lỗi ở đây chỉ ẩn ô, không hỏng Dashboard.
            SubscriptionRow activeMembership = view.getActiveMembership();
            if (activeMembership != null && "Membership".equals(activeMembership.getSubscriptionType())) {
                ServiceResult<StartDatePicker> picker
                        = startDateService.getPicker(accountId, activeMembership.getSubscriptionId());
                if (picker.isSuccess()) {
                    view.setStartPicker(picker.getData());
                }
            }

            return ServiceResult.ok(view);

        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        } catch (Exception e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /** Gom một thẻ cho mỗi PT; gắn đúng gói theo ID, không ghép theo tên người có thể trùng. */
    List<CustomerDashboardView.TrainerContact> groupTrainerPackages(
            List<CustomerDashboardView.TrainerContact> trainerRows, List<SubscriptionRow> subscriptions) {
        List<CustomerDashboardView.TrainerContact> trainers = new ArrayList<>();
        for (CustomerDashboardView.TrainerContact trainerRow : trainerRows) {
            CustomerDashboardView.TrainerContact selectedTrainer = null;
            for (CustomerDashboardView.TrainerContact trainer : trainers) {
                if (trainer.getEmployeeId() == trainerRow.getEmployeeId()) {
                    selectedTrainer = trainer;
                    break;
                }
            }
            if (selectedTrainer == null) {
                selectedTrainer = trainerRow;
                trainers.add(selectedTrainer);
            }
            for (SubscriptionRow subscription : subscriptions) {
                if (subscription.getSubscriptionId() == trainerRow.getSubscriptionId()
                        && !subscription.isExpiredForDisplay()) {
                    selectedTrainer.getPackages().add(subscription);
                    break;
                }
            }
        }
        // Không còn gói hiệu lực thì không hiện PT như đang phụ trách hội viên.
        List<CustomerDashboardView.TrainerContact> activeTrainers = new ArrayList<>();
        for (CustomerDashboardView.TrainerContact trainer : trainers) {
            if (!trainer.getPackages().isEmpty()) {
                activeTrainers.add(trainer);
            }
        }
        return activeTrainers;
    }

    /** Giữ nguyên trạng thái DB, tách nhãn hiển thị nếu ngày thực tế đã qua hoặc chưa bắt đầu. */
    void prepareSubscriptionPresentation(List<SubscriptionRow> subscriptions, LocalDate today) {
        for (SubscriptionRow subscription : subscriptions) {
            String displayStatus = subscription.getStatus();
            boolean isRunning = "Active".equals(displayStatus) || "Frozen".equals(displayStatus)
                    || "Pending".equals(displayStatus);
            if (isRunning && subscription.getEndDate() != null && subscription.getEndDate().isBefore(today)) {
                displayStatus = "Expired";
            } else if (isRunning && subscription.getStartDate() != null && subscription.getStartDate().isAfter(today)) {
                displayStatus = "Pending";
            }
            subscription.setDisplayStatus(displayStatus);
        }
    }

    /** Service tạo 7 nhóm ngày; JSP chỉ đọc, không tự lọc buổi hoặc gọi database. */
    List<CustomerDashboardView.ScheduleDay> buildScheduleDays(LocalDate today, List<UpcomingSessionRow> weekSessions) {
        List<CustomerDashboardView.ScheduleDay> scheduleDays = new ArrayList<>();
        for (int dayIndex = 0; dayIndex < 7; dayIndex++) {
            LocalDate date = today.plusDays(dayIndex);
            List<UpcomingSessionRow> sessionsOfDay = new ArrayList<>();
            for (UpcomingSessionRow session : weekSessions) {
                if (date.equals(session.getSlotDate())) {
                    sessionsOfDay.add(session);
                }
            }
            scheduleDays.add(new CustomerDashboardView.ScheduleDay(date, dayIndex == 0, sessionsOfDay));
        }
        return scheduleDays;
    }

    // ===== Trạng thái thẻ gói tập =====

    /**
     * Quyết định thẻ gói tập hiện kiểu nào. Dashboard chỉ đọc trạng thái đã lưu (GBR-14).
     *
     * - FROZEN: gói tập đang bảo lưu.
     * - EXPIRING: gói Active còn không quá P-21 ngày và chưa có gói nối tiếp (DBR-52), nút Gia hạn nổi.
     * - ACTIVE: gói Active còn dài hạn, hoặc đã có gói nối tiếp.
     * - EXPIRED: không còn gói chạy nhưng từng có gói đã hết hạn.
     * - NONE: chưa từng có gói tập.
     *
     * @param subscriptions danh sách gói của khách (gồm cả gói nối tiếp và gói Expired gần nhất).
     * @param today         ngày hôm nay.
     * @return một trong các hằng CARD_*.
     */
    private String buildMembershipCardState(List<SubscriptionRow> subscriptions, LocalDate today) {
        boolean hasNext = false;
        boolean hasExpired = false;
        SubscriptionRow current = null;

        for (SubscriptionRow sub : subscriptions) {
            if (sub.isPtPackage()) {
                continue;
            }
            if (sub.isPending()) {
                hasNext = true;
            } else if ("Active".equals(sub.getDisplayStatus()) || "Frozen".equals(sub.getDisplayStatus())) {
                current = sub;
            } else if ("Expired".equals(sub.getDisplayStatus())) {
                hasExpired = true;
            }
        }

        if (current == null) {
            return hasExpired ? CARD_EXPIRED : CARD_NONE;
        }
        if ("Frozen".equals(current.getStatus())) {
            return CARD_FROZEN;
        }

        long daysLeft = ChronoUnit.DAYS.between(today, current.getEndDate());
        boolean expiringSoon = daysLeft <= readExpiringSoonDays();
        return expiringSoon && !hasNext ? CARD_EXPIRING : CARD_ACTIVE;
    }

    /** Đọc P-21 (số ngày coi là "sắp hết hạn"); chưa có hoặc lỗi đọc thì dùng mặc định 7 ngày. */
    private int readExpiringSoonDays() {
        try {
            return systemConfigService.getInt(EXPIRING_SOON_DAYS_KEY);
        } catch (Exception e) {
            return DEFAULT_EXPIRING_SOON_DAYS;
        }
    }

    // ===== Chuyên cần (mở rộng ngoài SRS) =====

    /**
     * Gom dữ liệu thô từ DB rồi tính chuyên cần cho dashboard.
     *
     * Quy tắc: một "ngày tập" là ngày có ít nhất một check-in (UC-19, lễ tân check-in thủ công).
     * Ngày phòng đóng cửa hoặc khách đang bảo lưu là "ngày trung tính": không làm đứt chuỗi
     * và cũng không được cộng vào chuỗi.
     *
     * @param customerId CustomerId của khách đang đăng nhập.
     * @param today      ngày hôm nay, lấy một lần ở getDashboard.
     * @return AttendanceView đã tính xong chuỗi, kỷ lục, số buổi và lịch tháng.
     * @throws SQLException lỗi đọc DB.
     */
    private AttendanceView buildAttendance(int customerId, LocalDate today) throws SQLException {
        // B1: mọi ngày khách từng check-in
        Set<LocalDate> checkInDays = dashboardDAO.findCheckInDays(customerId);

        // B2: lấy ngày đóng cửa / bảo lưu từ ngày check-in đầu tiên (hoặc đầu tháng) đến hôm nay
        LocalDate from = today.withDayOfMonth(1);
        for (LocalDate day : checkInDays) {
            if (day.isBefore(from)) {
                from = day;
            }
        }
        Set<LocalDate> neutralDays = new HashSet<>(dashboardDAO.findClosedDays(from, today));
        neutralDays.addAll(dashboardDAO.findFrozenDays(customerId, from, today));

        // B3: tính
        return calculateAttendance(today, checkInDays, neutralDays);
    }

    /**
     * Tính chuyên cần từ dữ liệu thô, không đụng DB nên test được trực tiếp
     * (khai báo package-private để test cùng package gọi được).
     *
     * - Chuỗi hiện tại tính trong tháng này: đếm lùi từ hôm nay, dừng ở ngày 1.
     *   Hôm nay chưa tập thì đếm từ hôm qua, chuỗi chưa đứt.
     * - Kỷ lục tính từ trước đến giờ (xuyên tháng), luôn không nhỏ hơn chuỗi hiện tại.
     *
     * @param today       ngày hôm nay.
     * @param checkInDays mọi ngày khách có check-in.
     * @param neutralDays ngày phòng đóng cửa hoặc khách đang bảo lưu.
     * @return AttendanceView cho JSP in ra.
     */
    AttendanceView calculateAttendance(LocalDate today, Set<LocalDate> checkInDays,
            Set<LocalDate> neutralDays) {

        YearMonth thisMonth = YearMonth.from(today);
        LocalDate firstOfMonth = thisMonth.atDay(1);

        // B1: chuỗi hiện tại trong tháng
        int streak = countStreakInMonth(today, firstOfMonth, checkInDays, neutralDays);

        // B2: kỷ lục toàn thời gian, không nhỏ hơn chuỗi đang chạy
        int record = Math.max(countLongestStreak(checkInDays, neutralDays), streak);

        // B3: còn bao nhiêu ngày để PHÁ kỷ lục (cần vượt, nên +1); đang giữ kỷ lục thì 0
        int daysToBeatRecord = streak < record ? record - streak + 1 : 0;

        // B4: số buổi tháng này và tháng trước
        int thisMonthCount = countCheckInsInMonth(checkInDays, thisMonth, today);
        int lastMonthCount = countCheckInsInMonth(checkInDays, thisMonth.minusMonths(1), today);

        // B5: lịch tháng. Thứ Hai là cột đầu nên số ô trống = thứ của ngày 1 trừ 1
        int leadingBlanks = firstOfMonth.getDayOfWeek().getValue() - 1;
        String monthLabel = "Tháng " + String.format("%02d/%d", thisMonth.getMonthValue(), thisMonth.getYear());

        return new AttendanceView(
                streak,
                record,
                daysToBeatRecord,
                thisMonthCount,
                lastMonthCount,
                checkInDays.contains(today),
                streak >= STREAK_INTRO_MIN_DAYS,
                monthLabel,
                leadingBlanks,
                buildCalendarCells(thisMonth, today, checkInDays, neutralDays));
    }

    /** Đếm lùi từ hôm nay (hoặc hôm qua nếu hôm nay chưa tập) đến hết ngày 1 của tháng. */
    private int countStreakInMonth(LocalDate today, LocalDate firstOfMonth,
            Set<LocalDate> checkInDays, Set<LocalDate> neutralDays) {

        LocalDate day = checkInDays.contains(today) ? today : today.minusDays(1);
        int streak = 0;
        while (!day.isBefore(firstOfMonth)) {
            if (checkInDays.contains(day)) {
                streak++;
            } else if (!neutralDays.contains(day)) {
                break; // ngày thường mà không tập: chuỗi đứt
            }
            day = day.minusDays(1);
        }
        return streak;
    }

    /** Chuỗi dài nhất từ trước đến giờ: hai lần tập liền nhau chỉ cách nhau bởi ngày trung tính. */
    private int countLongestStreak(Set<LocalDate> checkInDays, Set<LocalDate> neutralDays) {
        if (checkInDays.isEmpty()) {
            return 0;
        }
        List<LocalDate> sorted = new ArrayList<>(checkInDays);
        Collections.sort(sorted);

        int best = 1;
        int run = 1;
        for (int i = 1; i < sorted.size(); i++) {
            // Kiểm mọi ngày nằm giữa hai lần tập liền kề có đều là ngày trung tính không
            boolean onlyNeutralBetween = true;
            LocalDate between = sorted.get(i - 1).plusDays(1);
            while (between.isBefore(sorted.get(i))) {
                if (!neutralDays.contains(between)) {
                    onlyNeutralBetween = false;
                    break;
                }
                between = between.plusDays(1);
            }
            run = onlyNeutralBetween ? run + 1 : 1;
            best = Math.max(best, run);
        }
        return best;
    }

    /** Số ngày có check-in trong tháng (không tính ngày sau hôm nay). */
    private int countCheckInsInMonth(Set<LocalDate> checkInDays, YearMonth month, LocalDate today) {
        int count = 0;
        for (LocalDate day : checkInDays) {
            if (YearMonth.from(day).equals(month) && !day.isAfter(today)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Dựng các ô ngày của tháng. state: checked (đã check-in) | closed (đóng cửa/bảo lưu)
     * | future (chưa tới) | missed (đã qua mà không tập).
     */
    private List<CalendarCell> buildCalendarCells(YearMonth month, LocalDate today,
            Set<LocalDate> checkInDays, Set<LocalDate> neutralDays) {

        List<CalendarCell> cells = new ArrayList<>();
        for (int dayNumber = 1; dayNumber <= month.lengthOfMonth(); dayNumber++) {
            LocalDate date = month.atDay(dayNumber);
            String state;
            if (checkInDays.contains(date)) {
                state = "checked";
            } else if (neutralDays.contains(date)) {
                state = "closed";
            } else if (date.isAfter(today)) {
                state = "future";
            } else {
                state = "missed";
            }
            cells.add(new CalendarCell(dayNumber, state, date.equals(today)));
        }
        return cells;
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

        // B1: đọc P-21, nếu chưa seed hoặc lỗi đọc thì dùng mặc định 7 ngày
        int expiringSoonDays = readExpiringSoonDays();

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
