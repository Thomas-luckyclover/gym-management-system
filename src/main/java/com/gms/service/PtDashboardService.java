package com.gms.service;

import com.gms.constant.Messages;
import com.gms.constant.Role;
import com.gms.dao.AccountDAO;
import com.gms.dao.EmployeeDAO;
import com.gms.dao.PtDashboardDAO;
import com.gms.dto.PtAssignedPackageView;
import com.gms.dto.PtAttentionView;
import com.gms.dto.PtClientPackageView;
import com.gms.dto.PtCommissionSummaryView;
import com.gms.dto.PtRatingView;
import com.gms.dto.PtScheduleDayView;
import com.gms.dto.PtSessionView;
import com.gms.dto.ServiceResult;
import com.gms.entity.Account;
import com.gms.entity.Employee;
import com.gms.entity.Package;
import com.gms.entity.Session;
import com.gms.util.DateTimeUtil;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * MÀN PT DASHBOARD — mỗi khối 1 hàm public, Servlet gọi lần lượt. Mọi hàm đều kiểm "đúng là PT đang làm" (findWorkingPt).
 */
public class PtDashboardService {
    // Lịch dạy: hôm nay + 6 ngày · Cần chú ý: xét 14 ngày tới.
    private static final int SCHEDULE_DAYS = 7;
    private static final int ATTENTION_DAYS = 14;

    private final AccountDAO accountDAO = new AccountDAO();
    private final EmployeeDAO employeeDAO = new EmployeeDAO();
    private final PtDashboardDAO dashboardDAO = new PtDashboardDAO();
    private final SystemConfigService systemConfigService = new SystemConfigService();

    // Mã nhân viên của PT (VD PT-0005) cho thẻ tài khoản. Không phải PT đang làm → MSG07.
    public ServiceResult<String> getPtEmployeeCode(int accountId) {
        try {
            Employee pt = findWorkingPt(accountId);
            if (pt == null) {
                return ServiceResult.fail(Messages.MSG07_NO_PERMISSION);
            }

            return ServiceResult.ok(pt.getEmployeeCode());

        } catch (SQLException exception) {
            exception.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    // Khối "Lịch dạy của bạn": 7 ngày, mỗi ngày các buổi của PT.
    public ServiceResult<List<PtScheduleDayView>> getSchedule(int accountId) {
        try {
            // B1: tài khoản → PT đang làm (findWorkingPt); không phải → MSG07
            Employee pt = findWorkingPt(accountId);
            if (pt == null) {
                return ServiceResult.fail(Messages.MSG07_NO_PERMISSION);
            }

            LocalDateTime now = DateTimeUtil.now();
            LocalDate today = now.toLocalDate();
            // B2: PtDashboardDAO.findSessions: mọi buổi của PT từ hôm nay tới 6 ngày sau → List<PtSessionView>
            List<PtSessionView> sessions = dashboardDAO.findSessions(
                    pt.getEmployeeId(), today, today.plusDays(SCHEDULE_DAYS - 1));

            // B3: mỗi buổi điền nhãn loại gói, nhãn trạng thái, có nút "Xử lý buổi" không (prepareSessionDisplay)
            for (PtSessionView session : sessions) {
                prepareSessionDisplay(session, now);
            }

            // B4: chia buổi vào 7 ngày → 7 PtScheduleDayView (ngày, "Hôm nay"/"Thứ…", buổi của ngày)
            List<PtScheduleDayView> days = new ArrayList<>();

            for (int i = 0; i < SCHEDULE_DAYS; i++) {
                LocalDate date = today.plusDays(i);

                List<PtSessionView> sessionsOfDay = new ArrayList<>();
                for (PtSessionView session : sessions) {
                    if (session.getSlotDate().equals(date)) {
                        sessionsOfDay.add(session);
                    }
                }

                days.add(new PtScheduleDayView(date, buildDayLabel(date, today), date.equals(today), sessionsOfDay));
            }

            return ServiceResult.ok(days);

        } catch (SQLException exception) {
            exception.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /**
     * HÀM PHỤ (getSchedule B3) — điền phần hiển thị cho 1 buổi.
     */
    void prepareSessionDisplay(PtSessionView session, LocalDateTime now) throws SQLException {
        // B1: dữ liệu sai (buổi hôm nay / tương lai mà đã Vắng) → báo lỗi khối
        if (session.getStatus() == Session.Status.MISSED
                && !session.getSlotDate().isBefore(now.toLocalDate())) {
            throw new SQLException("Buổi hôm nay hoặc tương lai đã bị chốt vắng, SessionId=" + session.getSessionId());
        }

        // B2: nhãn loại gói "PT cá nhân" | "PT đôi" | "Buổi lẻ"
        String typeLabel = typeLabel(session.getSubscriptionType());
        if (typeLabel == null) {
            throw new SQLException("Loại gói không hợp lệ cho buổi PT, SessionId=" + session.getSessionId());
        }
        session.setSubscriptionTypeLabel(typeLabel);

        // B3: nhãn + màu huy hiệu trạng thái
        switch (session.getStatus()) {
            case SCHEDULED -> {
                session.setStatusLabel("Đã lên lịch");
                session.setStatusBadgeClass("badge-warning");
            }
            case CHECKED_IN -> {
                session.setStatusLabel("Đã check-in");
                session.setStatusBadgeClass("badge-success");
            }
            case MISSED -> {
                session.setStatusLabel("Vắng");
                session.setStatusBadgeClass("badge-danger");
            }
            case CANCELLED -> {
                session.setStatusLabel("Đã hủy");
                session.setStatusBadgeClass("badge-neutral");
            }
        }

        // B4: lý do vắng (chỉ khi Missed)
        session.setMissedReasonLabel(null);
        if (session.getStatus() == Session.Status.MISSED && session.getMissedReason() != null) {
            session.setMissedReasonLabel(switch (session.getMissedReason()) {
                case NO_CHECK_IN -> "Khách không check-in";
                case NO_MEMBERSHIP -> "Không có membership hợp lệ";
                case MEMBERSHIP_FROZEN -> "Membership đang bảo lưu";
            });
        }

        // B5: canOpenActions = buổi Scheduled chưa tới giờ → hiện nút "Xử lý buổi"
        LocalDateTime startsAt = LocalDateTime.of(session.getSlotDate(), session.getStartTime());
        session.setCanOpenActions(session.getStatus() == Session.Status.SCHEDULED && startsAt.isAfter(now));
    }

    /**
     * HÀM PHỤ (getSchedule B4) — "Hôm nay" hoặc "Thứ Hai" … "Chủ Nhật".
     */
    private String buildDayLabel(LocalDate date, LocalDate today) {
        if (date.equals(today)) {
            return "Hôm nay";
        }
        return switch (date.getDayOfWeek()) {
            case MONDAY -> "Thứ Hai";
            case TUESDAY -> "Thứ Ba";
            case WEDNESDAY -> "Thứ Tư";
            case THURSDAY -> "Thứ Năm";
            case FRIDAY -> "Thứ Sáu";
            case SATURDAY -> "Thứ Bảy";
            case SUNDAY -> "Chủ Nhật";
        };
    }

    // Khối "Cần chú ý" (14 ngày tới): buổi rơi ngày phòng nghỉ + khách không vào gym được ngày có buổi.
    public ServiceResult<List<PtAttentionView>> getAttentionItems(int accountId) {
        try {
            // B1: kiểm PT đang làm
            Employee pt = findWorkingPt(accountId);
            if (pt == null) {
                return ServiceResult.fail(Messages.MSG07_NO_PERMISSION);
            }

            LocalDateTime now = DateTimeUtil.now();
            LocalDate today = now.toLocalDate();
            LocalDate toDate = today.plusDays(ATTENTION_DAYS - 1);

            // B2: PtDashboardDAO.findClosedDaySessions → buổi Scheduled rơi ngày phòng nghỉ cả ngày → việc "closedDay" (cần Dời buổi)
            List<PtAttentionView> items = new ArrayList<>();
            for (PtDashboardDAO.ClosedDaySession row : dashboardDAO.findClosedDaySessions(pt.getEmployeeId(), today, toDate)) {
                items.add(buildClosedDayItem(row));
            }

            // B3: PtDashboardDAO.findClientPackages → khách × gói × ngày có buổi + quyền vào gym → lọc dòng có vấn đề (buildAttentionItems)
            List<PtClientPackageView> clientPackages = dashboardDAO.findClientPackages(pt.getEmployeeId(), today, toDate, now);
            items.addAll(buildAttentionItems(clientPackages, today));

            return ServiceResult.ok(items);

        } catch (SQLException exception) {
            exception.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /**
     * HÀM PHỤ (getAttentionItems B2) — 1 buổi ngày phòng nghỉ → việc "closedDay": tiêu đề "Ngày phòng nghỉ — cần dời" + câu giải thích.
     */
    static PtAttentionView buildClosedDayItem(PtDashboardDAO.ClosedDaySession row) {
        String when = DateTimeUtil.formatDate(row.slotDate()) + " " + DateTimeUtil.formatTime(row.startTime());

        return new PtAttentionView(row.customerId(), row.subscriptionId(), row.customerName(),
                row.ptEndDate(), 0, row.slotDate(), null, null, typeLabel(row.subscriptionType()), null,
                "Ngày phòng nghỉ — cần dời",
                "Buổi " + when + " rơi vào ngày phòng nghỉ. Dời buổi trước ngày này; nếu không, hệ thống sẽ hủy và không tính hoa hồng.",
                "closedDay", row.sessionId());
    }

    /**
     * HÀM PHỤ (getAttentionItems B3) — chọn dòng có vấn đề (selectAttentionPackages) → mỗi dòng 1 PtAttentionView.
     */
    List<PtAttentionView> buildAttentionItems(List<PtClientPackageView> clientPackages, LocalDate today) throws SQLException {
        List<PtClientPackageView> selected = selectAttentionPackages(clientPackages);

        List<PtAttentionView> items = new ArrayList<>();
        for (PtClientPackageView row : selected) {
            items.add(buildAttentionItem(row, clientPackages, today));
        }
        return items;
    }

    /**
     * HÀM PHỤ (buildAttentionItems) — bỏ dòng vào gym bình thường; mỗi khách + gói + loại vấn đề chỉ giữ NGÀY SỚM NHẤT.
     */
    private List<PtClientPackageView> selectAttentionPackages(List<PtClientPackageView> clientPackages) {
        List<PtClientPackageView> selected = new ArrayList<>();

        for (PtClientPackageView row : clientPackages) {
            if (row.isHasMembershipOnSessionDate() && !row.isFrozenOnSessionDate()) {
                continue;
            }

            int sameIndex = -1;
            for (int i = 0; i < selected.size(); i++) {
                PtClientPackageView kept = selected.get(i);
                if (kept.getCustomerId() == row.getCustomerId()
                        && kept.getPtSubscriptionId() == row.getPtSubscriptionId()
                        && kept.isFrozenOnSessionDate() == row.isFrozenOnSessionDate()) {
                    sameIndex = i;
                    break;
                }
            }

            if (sameIndex == -1) {
                selected.add(row);
            } else if (row.getSessionDate().isBefore(selected.get(sameIndex).getSessionDate())) {
                selected.set(sameIndex, row);
            }
        }
        return selected;
    }

    /**
     * HÀM PHỤ (buildAttentionItems) — 1 dòng có vấn đề → PtAttentionView.
     */
    private PtAttentionView buildAttentionItem(PtClientPackageView row, List<PtClientPackageView> clientPackages,
            LocalDate today) throws SQLException {
        String dateText = DateTimeUtil.formatDate(row.getSessionDate());
        String title;
        String message;
        // B1: tiêu đề + câu: đang bảo lưu → "Membership đang bảo lưu" · không thì → "Cần membership"
        if (row.isFrozenOnSessionDate()) {
            title = "Membership đang bảo lưu";
            message = "Buổi ngày " + dateText + " nằm trong kỳ bảo lưu của khách.";
        } else {
            title = "Cần membership";
            message = "Cần membership hợp lệ cho buổi ngày " + dateText + ".";
        }

        // B2: gói đôi → nói thêm người kia có vào gym được ngày đó không (findPartnerPackage)
        if (row.getPtSubscriptionType() == Package.Type.PT_COUPLE) {
            PtClientPackageView partner = findPartnerPackage(row, clientPackages);
            if (partner == null) {
                throw new SQLException("Thiếu dữ liệu người còn lại của gói đôi, SubscriptionId=" + row.getPtSubscriptionId());
            }

            boolean partnerOk = partner.isHasMembershipOnSessionDate() && !partner.isFrozenOnSessionDate();
            message += partnerOk
                    ? " " + partner.getCustomerName() + " vẫn dùng được gym vào ngày này."
                    : " " + partner.getCustomerName() + " cũng không dùng được gym vào ngày này; cả hai cần được kiểm tra trước buổi tập.";
        }

        // B3: nhãn tình trạng membership: Đang bảo lưu / Chưa có / Đã hết hạn / Không bao phủ ngày tập
        String membershipLabel;
        if (row.isFrozenOnSessionDate()) {
            membershipLabel = "Đang bảo lưu";
        } else if (row.getMembershipEndDate() == null) {
            membershipLabel = "Chưa có membership phù hợp";
        } else if (row.getMembershipEndDate().isBefore(today)) {
            membershipLabel = "Đã hết hạn";
        } else {
            membershipLabel = "Không bao phủ ngày tập";
        }

        // B4: tạo PtAttentionView kind = "frozen" | "membership"
        return new PtAttentionView(row.getCustomerId(), row.getPtSubscriptionId(),
                row.getCustomerName(), row.getPtEndDate(),
                row.getScheduledSessionCount(), row.getSessionDate(),
                row.getMembershipEndDate(), row.getFreezeEndDate(),
                typeLabel(row.getPtSubscriptionType()), membershipLabel, title, message,
                row.isFrozenOnSessionDate() ? "frozen" : "membership", null);
    }

    /**
     * HÀM PHỤ (buildAttentionItem B2) — dòng của NGƯỜI KIA cùng gói đôi, cùng ngày. null = thiếu dữ liệu.
     */
    private PtClientPackageView findPartnerPackage(PtClientPackageView row, List<PtClientPackageView> clientPackages) {
        for (PtClientPackageView other : clientPackages) {
            if (other.getPtSubscriptionId() == row.getPtSubscriptionId()
                    && other.getSessionDate().equals(row.getSessionDate())
                    && other.getCustomerId() != row.getCustomerId()) {
                return other;
            }
        }
        return null;
    }

    // Khối "Khách phụ trách": gói PT PT đang phụ trách + buổi lẻ sắp dạy + mọi gói đang có việc cần chú ý.
    public ServiceResult<List<PtAssignedPackageView>> getAssignedPackages(int accountId, List<PtAttentionView> attentionItems) {
        try {
            // B1: kiểm PT đang làm
            Employee pt = findWorkingPt(accountId);
            if (pt == null) {
                return ServiceResult.fail(Messages.MSG07_NO_PERMISSION);
            }

            // B2: Id các gói đang có việc cần chú ý (lấy cả gói đó dù không còn thuộc PT)
            List<Integer> attentionPackageIds = attentionItems.stream()
                    .map(PtAttentionView::getPtSubscriptionId)
                    .distinct()
                    .toList();
            // B3: PtDashboardDAO.findAssignedPackages → List<PtAssignedPackageView>, điền nhãn loại gói
            List<PtAssignedPackageView> packages = dashboardDAO.findAssignedPackages(
                    pt.getEmployeeId(), DateTimeUtil.now(), attentionPackageIds);

            for (PtAssignedPackageView item : packages) {
                String label = typeLabel(item.getPtSubscriptionType());
                if (label == null) {
                    throw new SQLException("Loại gói không hợp lệ trong Khách phụ trách");
                }
                item.setPtSubscriptionTypeLabel(label);
            }

            // B4: gắn việc cần chú ý vào đúng khách + xếp khách có việc lên đầu (mergeClientAttention)
            mergeClientAttention(packages, attentionItems);
            return ServiceResult.ok(packages);

        } catch (SQLException exception) {
            exception.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /**
     * HÀM PHỤ (getAssignedPackages B4) — mỗi khách nhận các việc cùng gói; xếp: phòng nghỉ (0) → có việc (1) → bình thường (2).
     */
    static void mergeClientAttention(List<PtAssignedPackageView> packages, List<PtAttentionView> items) {
        for (PtAssignedPackageView client : packages) {
            client.getAttentionItems().clear();
            for (PtAttentionView item : items) {
                if (client.getPtSubscriptionId() == item.getPtSubscriptionId()) {
                    client.getAttentionItems().add(item);
                }
            }
        }

        packages.sort(Comparator.comparingInt(PtAssignedPackageView::getAttentionPriority));
    }

    // Khối "Kết quả tháng và hoa hồng" (theo ngày tập trong tháng này).
    public ServiceResult<PtCommissionSummaryView> getCommissionSummary(int accountId) {
        try {
            // B1: kiểm PT đang làm
            Employee pt = findWorkingPt(accountId);
            if (pt == null) {
                return ServiceResult.fail(Messages.MSG07_NO_PERMISSION);
            }

            LocalDateTime now = DateTimeUtil.now();
            LocalDate monthStart = now.toLocalDate().withDayOfMonth(1);

            // B2: PtDashboardDAO.findCommissionSummary(từ ngày 1 tới ngày 1 tháng sau) → số buổi + tiền theo từng kết quả
            PtCommissionSummaryView summary = dashboardDAO.findCommissionSummary(
                    pt.getEmployeeId(), monthStart, monthStart.plusMonths(1), now);

            // B3: tỷ lệ hoa hồng hiện tại của PT + tính tổng buổi đã chốt và % từng loại (prepareCommissionSummary)
            summary.setCommissionRate(pt.getCommissionRate());
            prepareCommissionSummary(summary);
            return ServiceResult.ok(summary);

        } catch (SQLException exception) {
            exception.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /**
     * HÀM PHỤ (getCommissionSummary B3) — tổng = check-in + không check-in + thiếu gym + bảo lưu + hủy; % từng loại (0 buổi → mọi số = 0).
     */
    void prepareCommissionSummary(PtCommissionSummaryView summary) {
        int total = summary.getCheckedInCount()
                + summary.getNoCheckInCount()
                + summary.getNoMembershipCount()
                + summary.getMembershipFrozenCount()
                + summary.getCancelledCount();

        if (total == 0) {
            summary.setResolvedStatistics(0, 0, 0, 0, 0, 0);
            return;
        }

        summary.setResolvedStatistics(total,
                percent(summary.getCheckedInCount(), total),
                percent(summary.getNoCheckInCount(), total),
                percent(summary.getNoMembershipCount(), total),
                percent(summary.getMembershipFrozenCount(), total),
                percent(summary.getCancelledCount(), total));
    }

    /**
     * HÀM PHỤ — count / total × 100, làm tròn 2 chữ số.
     */
    private double percent(int count, int total) {
        return Math.round(count * 100.0 / total * 100.0) / 100.0;
    }

    // Điểm đánh giá tháng của PT (Feedbacks) — hiện Servlet chưa gọi, màn chưa hiện khối này.
    public ServiceResult<PtRatingView> getRating(int accountId) {
        try {
            Employee pt = findWorkingPt(accountId);
            if (pt == null) {
                return ServiceResult.fail(Messages.MSG07_NO_PERMISSION);
            }

            LocalDate ratingMonth = DateTimeUtil.today().withDayOfMonth(1);
            PtRatingView rating = dashboardDAO.findRatingForMonth(pt.getEmployeeId(), ratingMonth);

            int minimumCustomers = systemConfigService.getInt(SystemConfigService.MIN_PT_RATING_CUSTOMERS);
            prepareRating(rating, minimumCustomers);
            return ServiceResult.ok(rating);

        } catch (SQLException | IllegalStateException exception) {
            exception.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /**
     * HÀM PHỤ (getRating) — đủ số khách đánh giá (P-22) mới hiện điểm trung bình + câu động viên.
     */
    void prepareRating(PtRatingView rating, int minimumCustomers) {
        if (minimumCustomers < 1) {
            throw new IllegalStateException("Tham số P-22 phải lớn hơn 0.");
        }

        int customerCount = rating.getRatingCustomerCount();
        if (customerCount == 0) {
            rating.setDisplay(false, null, "Chưa có đánh giá trong tháng này.");
            return;
        }
        if (customerCount < minimumCustomers) {
            rating.setDisplay(false, null, "Chưa đủ đánh giá để hiện điểm tháng này.");
            return;
        }

        BigDecimal average = BigDecimal.valueOf(rating.getRatingTotal())
                .divide(BigDecimal.valueOf(customerCount), 2, RoundingMode.DOWN);
        boolean perfectFive = rating.getRatingTotal() == 5L * customerCount;
        rating.setDisplay(true, average, buildRatingMessage(average, perfectFive));
    }

    /**
     * HÀM PHỤ (prepareRating) — câu động viên theo điểm trung bình.
     */
    private String buildRatingMessage(BigDecimal average, boolean perfectFive) {
        if (perfectFive) {
            return "5 sao trọn vẹn! Vui một nhịp thôi, rồi tiếp tục giữ phong độ nhé.";
        }
        if (average.compareTo(BigDecimal.valueOf(4)) > 0) {
            return "Điểm rất tốt! Cứ giữ sự chỉn chu; đừng để sự chủ quan vào tập thay mình.";
        }
        if (average.compareTo(BigDecimal.valueOf(3)) > 0) {
            return "Đang vào guồng rồi. Chăm thêm những chi tiết nhỏ, khách sẽ cảm nhận được.";
        }
        if (average.compareTo(BigDecimal.valueOf(2)) > 0) {
            return "Đường tiến bộ đâu phải lúc nào cũng thẳng. Lắng nghe, chỉnh từng buổi rồi đi tiếp nhé.";
        }
        if (average.compareTo(BigDecimal.ONE) > 0) {
            return "Hơi hụt nhịp, nhưng chưa hết set. Chỉnh cách hướng dẫn từ góp ý của khách nhé.";
        }
        return "Điểm chưa như mong muốn. Đừng nản; mỗi buổi là một cơ hội lấy lại niềm tin.";
    }

    /**
     * HÀM PHỤ (mọi hàm public B1) — tài khoản Active → Employee có Position = PT và Status = Working; không đúng → null.
     */
    private Employee findWorkingPt(int accountId) throws SQLException {
        Account account = accountDAO.findById(accountId);
        if (account == null || account.getStatus() != Account.Status.ACTIVE) {
            return null;
        }

        Employee employee = employeeDAO.findByPersonId(account.getPersonId());
        if (employee == null
                || employee.getPosition() != Role.PT
                || employee.getStatus() != Employee.Status.WORKING) {
            return null;
        }
        return employee;
    }

    /**
     * HÀM PHỤ — PTSingle → "PT cá nhân" · PTCouple → "PT đôi" · SingleSession → "Buổi lẻ" · khác → null.
     */
    static String typeLabel(Package.Type type) {
        if (type == null) {
            return null;
        }
        return switch (type) {
            case PT_SINGLE -> "PT cá nhân";
            case PT_COUPLE -> "PT đôi";
            case SINGLE_SESSION -> "Buổi lẻ";
            default -> null;
        };
    }
}
