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
import com.gms.dto.PtScheduleDayView;
import com.gms.dto.PtSessionView;
import com.gms.dto.ServiceResult;
import com.gms.entity.Account;
import com.gms.entity.Employee;
import com.gms.entity.Package;
import com.gms.entity.Session;
import com.gms.util.DateTimeUtil;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
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

                days.add(new PtScheduleDayView(
                        date,
                        buildDayLabel(date, today),
                        date.equals(today),
                        sessionsOfDay));
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

    // Khối "Cần chú ý" (14 ngày tới): buổi rơi lúc phòng đóng + khách không vào gym được ngày có buổi.
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

            // B2: PtDashboardDAO.findClosedDaySessions → buổi Scheduled rơi lúc phòng đóng (nghỉ cả ngày / ngoài giờ, cùng luật JOB-04) → việc "closedDay" (cần Dời buổi)
            List<PtAttentionView> items = new ArrayList<>();
            for (PtDashboardDAO.ClosedDaySession row : dashboardDAO.findClosedDaySessions(pt.getEmployeeId(), today, toDate)) {
                items.add(buildClosedDayItem(row));
            }

            // B3: PtDashboardDAO.findClientPackages → khách × gói × ngày có buổi + quyền vào gym → lọc dòng có vấn đề (buildAttentionItems)
            List<PtClientPackageView> clientPackages = dashboardDAO.findClientPackages(
                    pt.getEmployeeId(),
                    today,
                    toDate,
                    now);
            items.addAll(buildAttentionItems(clientPackages));

            return ServiceResult.ok(items);

        } catch (SQLException exception) {
            exception.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /**
     * HÀM PHỤ (getAttentionItems B2) — 1 buổi rơi lúc phòng đóng → việc "closedDay" (nút Dời buổi): tiêu đề + câu theo 3 trường hợp (buildClosedDayTitle, buildClosedDayMessage).
     */
    static PtAttentionView buildClosedDayItem(PtDashboardDAO.ClosedDaySession row) {
        return new PtAttentionView(
                "closedDay",
                row.sessionId(),
                row.slotDate(),
                buildClosedDayTitle(row),
                buildClosedDayMessage(row),
                row.subscriptionId(),
                row.customerName());
    }

    /** HÀM PHỤ (buildClosedDayItem) — tiêu đề: nghỉ cả ngày · chưa rõ giờ mở cửa · ngoài giờ mở cửa. */
    private static String buildClosedDayTitle(PtDashboardDAO.ClosedDaySession row) {
        if (row.closedAllDay()) {
            return "Ngày phòng nghỉ — cần dời";
        }
        if (row.openTime() == null || row.closeTime() == null) {
            return "Chưa rõ giờ mở cửa — cần kiểm tra";
        }
        return "Ngoài giờ mở cửa — cần dời";
    }

    /** HÀM PHỤ (buildClosedDayItem) — câu giải thích theo cùng 3 trường hợp với tiêu đề. */
    private static String buildClosedDayMessage(PtDashboardDAO.ClosedDaySession row) {
        String date = DateTimeUtil.formatDate(row.slotDate());
        String start = DateTimeUtil.formatTime(row.startTime());
        if (row.closedAllDay()) {
            return "Buổi " + date + " " + start + " rơi vào ngày phòng nghỉ. Dời buổi trước ngày này; nếu không, hệ thống sẽ hủy và không tính hoa hồng.";
        }
        if (row.openTime() == null || row.closeTime() == null) {
            return "Buổi " + date + " " + start + " chưa xác định được giờ mở cửa ngày này. Hệ thống sẽ hủy buổi nếu chưa xử lý.";
        }
        return "Buổi " + date + " " + start + "–" + DateTimeUtil.formatTime(row.endTime())
                + " nằm ngoài giờ mở cửa (" + DateTimeUtil.formatTime(row.openTime()) + "–" + DateTimeUtil.formatTime(row.closeTime())
                + "). Dời buổi trước ngày này; nếu không, hệ thống sẽ hủy và không tính hoa hồng.";
    }

    /**
     * HÀM PHỤ (getAttentionItems B3) — chọn dòng có vấn đề (selectAttentionPackages) → mỗi dòng 1 PtAttentionView.
     */
    List<PtAttentionView> buildAttentionItems(List<PtClientPackageView> clientPackages) throws SQLException {
        List<PtClientPackageView> selected = selectAttentionPackages(clientPackages);

        List<PtAttentionView> items = new ArrayList<>();
        for (PtClientPackageView row : selected) {
            items.add(buildAttentionItem(row, clientPackages));
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
    private PtAttentionView buildAttentionItem(PtClientPackageView row, List<PtClientPackageView> clientPackages) throws SQLException {
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

        // B3: tạo PtAttentionView kind = "frozen" | "membership"
        return new PtAttentionView(
                row.isFrozenOnSessionDate() ? "frozen" : "membership",
                null,
                row.getSessionDate(),
                title,
                message,
                row.getPtSubscriptionId(),
                row.getCustomerName());
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

    // Khối "Kết quả tháng và hoa hồng" của tháng hiện tại.
    public ServiceResult<PtCommissionSummaryView> getCommissionSummary(int accountId) {
        return getCommissionSummary(accountId, YearMonth.from(DateTimeUtil.today()));
    }

    // Khối "Kết quả tháng và hoa hồng" của tháng đang xem (?month=), tính theo ngày tập trong tháng đó.
    public ServiceResult<PtCommissionSummaryView> getCommissionSummary(int accountId, YearMonth month) {
        try {
            // B1: kiểm PT đang làm
            Employee pt = findWorkingPt(accountId);
            if (pt == null) {
                return ServiceResult.fail(Messages.MSG07_NO_PERMISSION);
            }

            LocalDateTime now = DateTimeUtil.now();
            LocalDate monthStart = month.atDay(1);

            // B2: PtDashboardDAO.findCommissionSummary(từ ngày 1 tới ngày 1 tháng sau) → số buổi + tiền theo từng kết quả
            PtCommissionSummaryView summary = dashboardDAO.findCommissionSummary(
                    pt.getEmployeeId(), monthStart, monthStart.plusMonths(1), now);

            // B3: tỷ lệ hoa hồng HIỆN TẠI của PT (không phải của tháng đang xem) + tính tổng buổi đã chốt và % từng loại (prepareCommissionSummary)
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



/*
 * ============================================================================================================
 * PHỤ LỤC — BẢN ĐỒ DTO MÀN PT DASHBOARD: FIELD NÀO · HÀM NÀO TẠO · ĐI QUA ĐÂU · HIỆN Ở ĐÂU
 * ============================================================================================================
 *
 * A. CÂY GỌI HÀM — GET /pt/dashboard (chỉ có doGet, chỉ ĐỌC DB)
 *   ?month=yyyy-MM → tháng đang xem (mặc định tháng hiện tại), chỉ khối hoa hồng đổi theo.
 *
 *   PtDashboardServlet.doGet
 *     B2 loadBlocks — mỗi khối 1 hàm Service, kết quả qua putBlock (ok → "xxx" · lỗi → "xxxError"):
 *        1. getSchedule(accountId)          → DAO.findSessions → prepareSessionDisplay → gom 7 ngày
 *                                                                               → "scheduleDays"      | "scheduleError"
 *        2. getAttentionItems(accountId)    → DAO.findClosedDaySessions (phòng đóng: nghỉ cả ngày / ngoài giờ) → buildClosedDayItem
 *                                           + DAO.findClientPackages → buildAttentionItems
 *                                               → selectAttentionPackages → buildAttentionItem
 *                                                                               → "attentionItems"    | "attentionError"
 *        3. getAssignedPackages(accountId, attentionItems ở bước 2)
 *                                           → DAO.findAssignedPackages → typeLabel → mergeClientAttention
 *                                                                               → "assignedPackages"  | "assignedPackagesError"
 *        4. getCommissionSummary(accountId) → DAO.findCommissionSummary → setCommissionRate → prepareCommissionSummary
 *                                                                               → "commissionSummary" | "commissionSummaryError"
 *     B3 loadWeather → WeatherService                                           → "weather" · "weatherType" · "weatherPreview"
 *     B4 setPageInfo                                                            → "activeNav" · "pageTitle" · "today"
 *
 *   Mọi hàm Service B1 đều gọi findWorkingPt: tài khoản Active + Employees.Position = PT + Status = Working; sai → MSG07.
 *   1 khối lỗi chỉ khối đó hiện chữ đỏ (putBlock), các khối khác vẫn hiện.
 *
 * ------------------------------------------------------------------------------------------------------------
 * B. PtScheduleDayView (phần tử "scheduleDays", biến JSP day) — khối "Lịch dạy 7 ngày"
 *    Tạo: getSchedule B4 — new PtScheduleDayView(...) cho 7 ngày (hôm nay → +6). Constructor, field final.
 * ------------------------------------------------------------------------------------------------------------
 *   slotDate       today.plusDays(i)                          → tab ngày "dd/MM"
 *   dayLabel       buildDayLabel: "Hôm nay" | "Thứ Hai"…      → tên tab
 *   today          date.equals(today)  (getter isToday)       → tab mở sẵn + is-today; JSP tìm todayCount / firstToday
 *   sessions       lọc List<PtSessionView> đúng ngày          → "N buổi" / "Trống" · danh sách buổi trong tab (mục C)
 *
 * ------------------------------------------------------------------------------------------------------------
 * C. PtSessionView (phần tử day.sessions, biến JSP s) — 1 buổi trong tab ngày
 *    Tạo: PtDashboardDAO.findSessions (constructor) · Điền thêm: getSchedule B3 → prepareSessionDisplay
 * ------------------------------------------------------------------------------------------------------------
 *   field                  ai làm                                               hiện ở
 *   sessionId              DAO ← Sessions.SessionId                             link Xử lý buổi ?handle= · so firstToday
 *   slotDate               DAO ← TimeSlots.SlotDate                             link ?date= · data-start/data-end
 *   startTime · endTime    DAO ← TimeSlots                                      "07:00 – 08:00" · dashboard.js tô buổi đang diễn ra
 *   customerName           DAO ← Persons.FullName người đứng tên                tên khách
 *   partnerName            DAO ← Persons của PartnerCustomerId (gói đôi)        " & tên người tập cùng"
 *   subscriptionType       DAO ← Subscriptions.SubscriptionType (enum)          (chỉ để tính nhãn)
 *   status                 DAO ← Sessions.Status (enum)                         JSP: khác SCHEDULED mới hiện huy hiệu
 *   missedReason           DAO ← Sessions.MissedReason (enum)                   (chỉ để tính nhãn)
 *   subscriptionTypeLabel  prepareSessionDisplay B2 = typeLabel(...)            "PT cá nhân" | "PT đôi" | "Buổi lẻ"
 *   statusLabel            prepareSessionDisplay B3                             chữ huy hiệu "Đã check-in"…
 *   statusBadgeClass       prepareSessionDisplay B3                             màu huy hiệu badge-success / danger…
 *   missedReasonLabel      prepareSessionDisplay B4 (chỉ Missed)                in cạnh huy hiệu Vắng
 *   canOpenActions         prepareSessionDisplay B5 = Scheduled + chưa tới giờ  nút "Xử lý buổi" · chip "Tiếp theo"
 *   Kiểm dữ liệu hỏng (B1, B2): buổi hôm nay/tương lai mà Missed, hoặc loại gói lạ → ném SQLException → cả khối báo lỗi.
 *
 * ------------------------------------------------------------------------------------------------------------
 * D. PtAttentionView ("attentionItems" và client.attentionItems, biến JSP item) — việc "Cần chú ý" 14 ngày tới
 *    Tạo bằng constructor ở 2 nơi, field final, không setter:
 *      kind = "closedDay"              ← buildClosedDayItem(row)  ← DAO.findClosedDaySessions (buổi rơi lúc phòng đóng: nghỉ cả ngày / ngoài giờ, cùng luật JOB-04)
 *      kind = "frozen" | "membership"  ← buildAttentionItem(row)  ← DAO.findClientPackages, đã lọc qua selectAttentionPackages
 *                                        (bỏ người vào gym bình thường; mỗi khách + gói + loại vấn đề chỉ giữ NGÀY SỚM NHẤT)
 * ------------------------------------------------------------------------------------------------------------
 *   field                    closedDay                       frozen / membership                     hiện ở
 *   kind                     "closedDay"                     frozen ? "frozen" : "membership"        màu ô (is-closed / is-frozen) · nút Dời buổi
 *   sessionId                row.sessionId                   null                                    link "Dời buổi" ?handle=
 *   sessionDate              row.slotDate                    row.sessionDate (ngày sớm nhất)          "buổi dd/MM" · link ?date=
 *   attentionTitle           "Ngày phòng nghỉ — cần dời" | "Ngoài giờ mở cửa — cần dời" | "Chưa rõ giờ mở cửa — cần kiểm tra"     "Membership đang bảo lưu" | "Cần membership"   chữ đậm
 *   attentionMessage         theo 3 trường hợp (buildClosedDayMessage)  "Buổi ngày … bảo lưu" / "Cần membership…"
 *                                                            + gói đôi: câu về người kia (findPartnerPackage)   ghi chú (closedDay hoặc gói đôi)
 *   ptSubscriptionId         row.subscriptionId              row.ptSubscriptionId                    mergeClientAttention ghép vào đúng khách
 *   customerName             row.customerName                row.customerName                        "· tên" (khi gói đôi)
 *   Số ô "Cần chú ý" ở 3 số nhanh = fn:length(attentionItems).
 *
 *   (Dữ liệu trung gian PtClientPackageView: DAO.findClientPackages tạo — 1 dòng = khách × gói × ngày có buổi,
 *    kèm hasMembershipOnSessionDate · frozenOnSessionDate. Không lên JSP.)
 *
 * ------------------------------------------------------------------------------------------------------------
 * E. PtAssignedPackageView (phần tử "assignedPackages", biến JSP client) — khối "Khách phụ trách"
 *    Tạo: PtDashboardDAO.findAssignedPackages (constructor + 3 setter). Lấy: gói PT còn hạn của PT + buổi lẻ sắp dạy
 *         + MỌI gói có trong attentionItems (dù không còn thuộc PT).
 *    Điền thêm: getAssignedPackages B3 (nhãn) · B4 mergeClientAttention (việc cần chú ý + sắp xếp).
 * ------------------------------------------------------------------------------------------------------------
 *   customerName · partnerName       DAO constructor ← Persons                      tên khách "& người tập cùng"
 *   ptSubscriptionType               DAO constructor (enum)                         (chỉ để tính nhãn)
 *   ptEndDate                        DAO ← Subscriptions.EndDate                    "đến dd/MM/yyyy"
 *   scheduledSessionCount            DAO ← COUNT buổi Scheduled chưa tới giờ        "còn N buổi"
 *   ptSubscriptionId                 DAO setter                                     khóa ghép việc cần chú ý
 *   customerPhone · partnerPhone     DAO setter ← Persons.Phone                     nút gọi điện
 *   ptSubscriptionTypeLabel          getAssignedPackages B3 = typeLabel(...)        "PT cá nhân · còn N buổi"
 *   attentionItems                   mergeClientAttention: PtAttentionView cùng ptSubscriptionId   các ô việc cần chú ý (mục D)
 *   (getter) customerZaloUrl · partnerZaloUrl = ZaloUtil.toUrl(phone)              nút "Zalo"
 *   (getter) attentionPriority: có closedDay = 0 · có việc khác = 1 · không có = 2 → mergeClientAttention sort: khách có việc lên đầu
 *
 * ------------------------------------------------------------------------------------------------------------
 * F. PtCommissionSummaryView ("commissionSummary", biến JSP cs) — khối "Kết quả tháng và hoa hồng"
 *    Tạo: PtDashboardDAO.findCommissionSummary (constructor, đếm buổi có NGÀY TẬP trong tháng này).
 *    Điền thêm: getCommissionSummary B3 setCommissionRate · prepareCommissionSummary → setResolvedStatistics.
 *    ?month=yyyy-MM → tháng đang xem (mặc định tháng hiện tại); commissionRate vẫn là tỷ lệ HIỆN TẠI.
 * ------------------------------------------------------------------------------------------------------------
 *   field                             DAO tính bằng (SUM CASE trong SQL)                         hiện ở
 *   finalizedCommissionAmount         hoa hồng buổi CheckedIn / Missed                          ô tiền "Đã chốt"
 *   pendingCommissionAmount           hoa hồng buổi Scheduled ĐÃ hết giờ (chờ JOB-04)           ô "Chờ hệ thống chốt"
 *   pendingSessionCount               số buổi đó                                                "N buổi vừa kết thúc"
 *   projectedCommissionAmount         hoa hồng buổi Scheduled CHƯA hết giờ                      ô "Dự kiến"
 *   projectedSessionCount             số buổi đó                                                "N buổi còn lại trong tháng"
 *   checkedInCount                    Status = CheckedIn                                        thanh tỷ lệ + "Có check-in N"
 *   noCheckInCount                    Missed + NoCheckIn                                        "Không check-in N"
 *   noMembershipCount                 Missed + NoMembership                                     "Thiếu quyền vào gym N"
 *   membershipFrozenCount             Missed + MembershipFrozen                                 "Đang bảo lưu N"
 *   cancelledCount                    Status = Cancelled                                        "Đã hủy N"
 *   commissionRate                    Service ← Employees.CommissionRate (tỷ lệ HIỆN TẠI)       "Tỷ lệ hoa hồng hiện tại: N%"
 *   resolvedSessionCount              prepareCommissionSummary = tổng 5 loại trên               "N buổi đã chốt" · 0 → "Chưa có buổi nào…"
 *   checkedInRatePercent … cancelledRatePercent
 *                                     prepareCommissionSummary = count × 100 / tổng (2 chữ số)  "%" cạnh chú thích
 *
 * ------------------------------------------------------------------------------------------------------------
 * G. Attribute KHÔNG phải DTO của PT
 * ------------------------------------------------------------------------------------------------------------
 *   weather           WeatherView (WeatherService, API thời tiết; ?weather= → bản xem thử)  → common/dashboard-weather.jspf
 *   weatherType       resolveWeatherType → class <body> "weather-sunny|rain|…" (đổi màu nền, tranh)
 *   weatherPreview    true = đang xem thử → nhãn "Xem trước"
 *   activeNav · pageTitle · today   setPageInfo → menu sáng "Tổng quan", tiêu đề, ngày trên thanh trên
 *   JSP tự tính: todayCount (số buổi hôm nay) · firstToday (buổi đầu hôm nay) từ scheduleDays → 3 số nhanh + chip "Tiếp theo"
 *
 * ============================================================================================================
 */