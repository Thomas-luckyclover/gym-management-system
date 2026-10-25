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
 * SERVICE CỦA TRANG TỔNG QUAN PT — /pt/dashboard (UC-32).
 *
 * Mỗi khối trên trang có 1 HÀM CHÍNH riêng; PtDashboardServlet gọi lần lượt từng hàm.
 * Một khối lỗi không làm hỏng khối khác (mỗi hàm trả ServiceResult riêng).
 *
 *   0. MÃ NHÂN VIÊN    getPtEmployeeCode     "NV-0015" dưới tên PT ở đầu trang
 *   1. LỊCH 7 NGÀY     getSchedule           dải 7 ngày, từ hôm nay
 *   2. CẦN CHÚ Ý       getAttentionItems     buổi rơi vào ngày nghỉ, khách thiếu membership / đang bảo lưu
 *   3. KHÁCH PHỤ TRÁCH getAssignedPackages   danh sách gói PT + SĐT + Zalo, gắn cảnh báo ở khối 2
 *   4. KẾT QUẢ THÁNG   getCommissionSummary  3 ô tiền + thanh tỷ lệ kết quả
 *   5. ĐÁNH GIÁ        getRating             điểm tháng (chưa có màn gọi, xem ghi chú ở hàm)
 *   6. DÙNG CHUNG      findWorkingPt, typeLabel
 *
 * Quy tắc quyền (GBR-10): mọi hàm chính đều bắt đầu bằng findWorkingPt(accountId).
 * accountId luôn lấy từ session, không bao giờ lấy mã PT từ URL → PT chỉ xem được dữ liệu của mình.
 *
 * THAM SỐ CẤU HÌNH (giá trị hiện tại — đổi ở đâu)
 *   SCHEDULE_DAYS  = 7    dải lịch hiện 7 ngày: hôm nay → hôm nay + 6          (hằng số dưới đây)
 *   ATTENTION_DAYS = 14   "Cần chú ý" xét buổi trong 14 ngày tới               (hằng số dưới đây)
 *   P-22           = 3    cần ≥ 3 khách đánh giá mới hiện điểm trung bình PT  (bảng SystemConfigs)
 *   Tỷ lệ hoa hồng của PT đọc từ Employees.CommissionRate (VD Đinh Công Sơn = 70%); P-11 = 70% chỉ là mặc định cho PT mới.
 */
public class PtDashboardService {

    private static final int SCHEDULE_DAYS = 7;    // Lịch hiện 7 ngày: hôm nay → hôm nay + 6
    private static final int ATTENTION_DAYS = 14;  // Cảnh báo xét 14 ngày: hôm nay → hôm nay + 13

    private final AccountDAO accountDAO = new AccountDAO();
    private final EmployeeDAO employeeDAO = new EmployeeDAO();
    private final PtDashboardDAO dashboardDAO = new PtDashboardDAO();
    private final SystemConfigService systemConfigService = new SystemConfigService();


    // =================================================================================================
    // 0. MÃ NHÂN VIÊN
    // =================================================================================================

    /**
     * HÀM CHÍNH — PtDashboardServlet và PtScheduleServlet gọi khi mở trang, để hiện "NV-0015" dưới tên PT.
     */
    public ServiceResult<String> getPtEmployeeCode(int accountId) {
        try {
            // B1: tìm hồ sơ PT đang làm việc của tài khoản này.
            Employee pt = findWorkingPt(accountId);
            if (pt == null) {
                return ServiceResult.fail(Messages.MSG07_NO_PERMISSION);
            }

            // B2: trả mã nhân viên.
            return ServiceResult.ok(pt.getEmployeeCode());

        } catch (SQLException exception) {
            exception.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }


    // =================================================================================================
    // 1. LỊCH 7 NGÀY
    // =================================================================================================

    /**
     * HÀM CHÍNH — PtDashboardServlet.loadBlocks gọi ở B1 khi PT mở trang.
     * Trả đúng 7 ngày (kể cả ngày không có buổi), mỗi ngày chứa các buổi của ngày đó.
     */
    public ServiceResult<List<PtScheduleDayView>> getSchedule(int accountId) {
        try {
            // B1: kiểm tra quyền.
            Employee pt = findWorkingPt(accountId);
            if (pt == null) {
                return ServiceResult.fail(Messages.MSG07_NO_PERMISSION);
            }

            // B2: DAO đọc các buổi từ hôm nay đến hôm nay + 6, rồi gắn chữ hiển thị cho từng buổi.
            //     Lấy "now" 1 lần để mọi buổi được so cùng một thời điểm.
            LocalDateTime now = DateTimeUtil.now();
            LocalDate today = now.toLocalDate();
            List<PtSessionView> sessions = dashboardDAO.findSessions(
                    pt.getEmployeeId(), today, today.plusDays(SCHEDULE_DAYS - 1));

            for (PtSessionView session : sessions) {
                prepareSessionDisplay(session, now);
            }

            // B3: tạo 7 ngày. Mỗi ngày nhặt ra các buổi có cùng ngày.
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
     * HÀM PHỤ — gọi ở B2 của getSchedule, mỗi buổi 1 lần.
     * Gắn chữ tiếng Việt lên buổi DAO đã đọc (loại gói, trạng thái, lý do vắng) và quyết định nút "Xử lý buổi".
     * Không ghi gì xuống DB.
     */
    void prepareSessionDisplay(PtSessionView session, LocalDateTime now) throws SQLException {
        // B1: chặn dữ liệu sai — buổi hôm nay / tương lai không thể đã "Vắng" (JOB-04 chỉ chốt sau ngày tập).
        if (session.getStatus() == Session.Status.MISSED
                && !session.getSlotDate().isBefore(now.toLocalDate())) {
            throw new SQLException("Buổi hôm nay hoặc tương lai đã bị chốt vắng, SessionId=" + session.getSessionId());
        }

        // B2: loại gói → "PT cá nhân" / "PT đôi" / "Buổi lẻ". Không phải gói PT → dữ liệu sai, báo lỗi.
        String typeLabel = typeLabel(session.getSubscriptionType());
        if (typeLabel == null) {
            throw new SQLException("Loại gói không hợp lệ cho buổi PT, SessionId=" + session.getSessionId());
        }
        session.setSubscriptionTypeLabel(typeLabel);

        // B3: trạng thái → chữ + màu badge.
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

        // B4: chỉ buổi Vắng mới có dòng lý do.
        session.setMissedReasonLabel(null);
        if (session.getStatus() == Session.Status.MISSED && session.getMissedReason() != null) {
            session.setMissedReasonLabel(switch (session.getMissedReason()) {
                case NO_CHECK_IN -> "Khách không check-in";
                case NO_MEMBERSHIP -> "Không có membership hợp lệ";
                case MEMBERSHIP_FROZEN -> "Membership đang bảo lưu";
            });
        }

        // B5: nút "Xử lý buổi" chỉ bật khi buổi còn Scheduled và chưa tới giờ bắt đầu (BR-69).
        LocalDateTime startsAt = LocalDateTime.of(session.getSlotDate(), session.getStartTime());
        session.setCanOpenActions(session.getStatus() == Session.Status.SCHEDULED && startsAt.isAfter(now));
    }

    /**
     * HÀM PHỤ — gọi ở B3 của getSchedule. Ngày đầu là "Hôm nay", các ngày sau là "Thứ Hai"… "Chủ Nhật".
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


    // =================================================================================================
    // 2. CẦN CHÚ Ý — cảnh báo trong 14 ngày tới
    // =================================================================================================
    //   Thứ tự gọi:  getAttentionItems
    //                  ├─ B3  buildClosedDayItem         (mỗi buổi rơi vào ngày phòng nghỉ → 1 thẻ "Dời buổi")
    //                  └─ B4  buildAttentionItems
    //                           ├─ B1  selectAttentionPackages   (bỏ khách ổn, gom ngày trùng vấn đề)
    //                           └─ B2  buildAttentionItem        (tạo 1 thẻ cảnh báo)
    //                                    └─ B2  findPartnerPackage   (gói đôi: xem người còn lại)
    //
    //   Khách thiếu membership KHÔNG cần PT bấm gì: JOB-08 tự nhắc khách trước buổi 1 ngày.

    /**
     * HÀM CHÍNH — PtDashboardServlet.loadBlocks gọi ở B2 khi PT mở trang.
     * Trả danh sách cảnh báo: thẻ ngày nghỉ đứng trước, thẻ membership / bảo lưu đứng sau. Rỗng = không có gì cần chú ý.
     */
    public ServiceResult<List<PtAttentionView>> getAttentionItems(int accountId) {
        try {
            // B1: kiểm tra quyền.
            Employee pt = findWorkingPt(accountId);
            if (pt == null) {
                return ServiceResult.fail(Messages.MSG07_NO_PERMISSION);
            }

            // B2: khoảng xét = hôm nay → hôm nay + 13.
            LocalDateTime now = DateTimeUtil.now();
            LocalDate today = now.toLocalDate();
            LocalDate toDate = today.plusDays(ATTENTION_DAYS - 1);

            // B3: buổi rơi vào ngày phòng nghỉ cả ngày → mỗi buổi 1 thẻ (PT phải dời).
            List<PtAttentionView> items = new ArrayList<>();
            for (PtDashboardDAO.ClosedDaySession row : dashboardDAO.findClosedDaySessions(pt.getEmployeeId(), today, toDate)) {
                items.add(buildClosedDayItem(row));
            }

            // B4: khách thiếu membership / đang bảo lưu vào ngày tập → thêm sau thẻ ngày nghỉ.
            List<PtClientPackageView> clientPackages = dashboardDAO.findClientPackages(pt.getEmployeeId(), today, toDate, now);
            items.addAll(buildAttentionItems(clientPackages, today));

            return ServiceResult.ok(items);

        } catch (SQLException exception) {
            // Lỗi đọc dữ liệu được báo riêng — không giả vờ "không có cảnh báo".
            exception.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /**
     * HÀM PHỤ — gọi ở B3 của getAttentionItems, mỗi buổi ngày nghỉ 1 lần.
     * Ví dụ: "Buổi 30/10/2026 18:00 rơi vào ngày phòng nghỉ. Dời buổi trước ngày này; …"
     * static + không đụng DB → test gọi thẳng được (PtAttentionServiceTest).
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
     * HÀM PHỤ — gọi ở B4 của getAttentionItems.
     * Biến dữ liệu thô của DAO (mỗi khách × mỗi ngày tập 1 dòng) thành các thẻ cảnh báo.
     */
    List<PtAttentionView> buildAttentionItems(List<PtClientPackageView> clientPackages, LocalDate today) throws SQLException {
        // B1: chỉ giữ dòng có vấn đề; cùng khách + cùng gói + cùng vấn đề thì giữ ngày gần nhất.
        List<PtClientPackageView> selected = selectAttentionPackages(clientPackages);

        // B2: mỗi dòng được giữ → 1 thẻ cảnh báo.
        List<PtAttentionView> items = new ArrayList<>();
        for (PtClientPackageView row : selected) {
            items.add(buildAttentionItem(row, clientPackages, today));
        }
        return items;
    }

    /**
     * HÀM PHỤ — gọi ở B1 của buildAttentionItems.
     * Ví dụ: khách A thiếu membership 5 ngày (12, 14, 16, 18, 20/10) → chỉ giữ 1 dòng ngày 12/10.
     */
    private List<PtClientPackageView> selectAttentionPackages(List<PtClientPackageView> clientPackages) {
        List<PtClientPackageView> selected = new ArrayList<>();

        for (PtClientPackageView row : clientPackages) {
            // B1: có membership và không bảo lưu → khách ổn, bỏ qua.
            if (row.isHasMembershipOnSessionDate() && !row.isFrozenOnSessionDate()) {
                continue;
            }

            // B2: tìm dòng đã giữ có cùng khách + cùng gói + cùng loại vấn đề (bảo lưu hay thiếu membership).
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

            // B3: chưa có → thêm. Đã có → giữ ngày tập sớm hơn (việc cần xử lý trước).
            if (sameIndex == -1) {
                selected.add(row);
            } else if (row.getSessionDate().isBefore(selected.get(sameIndex).getSessionDate())) {
                selected.set(sameIndex, row);
            }
        }
        return selected;
    }

    /**
     * HÀM PHỤ — gọi ở B2 của buildAttentionItems, mỗi dòng 1 lần.
     * Tạo 1 thẻ: tiêu đề, lời giải thích, nhãn gói, tình trạng membership.
     * clientPackages là list ĐẦY ĐỦ (cả người ổn) — cần để xem người còn lại của gói đôi.
     */
    private PtAttentionView buildAttentionItem(PtClientPackageView row, List<PtClientPackageView> clientPackages,
            LocalDate today) throws SQLException {
        // B1: tiêu đề + câu giải thích theo vấn đề.
        String dateText = DateTimeUtil.formatDate(row.getSessionDate());
        String title;
        String message;
        if (row.isFrozenOnSessionDate()) {
            title = "Membership đang bảo lưu";
            message = "Buổi ngày " + dateText + " nằm trong kỳ bảo lưu của khách.";
        } else {
            title = "Cần membership";
            message = "Cần membership hợp lệ cho buổi ngày " + dateText + ".";
        }

        // B2: gói đôi → nói thêm người còn lại có dùng được gym ngày đó không.
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

        // B3: tình trạng membership (ưu tiên bảo lưu → chưa có → hết hạn → không phủ ngày tập).
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

        // B4: đóng gói thành DTO cho JSP.
        return new PtAttentionView(row.getCustomerId(), row.getPtSubscriptionId(),
                row.getCustomerName(), row.getPtEndDate(),
                row.getScheduledSessionCount(), row.getSessionDate(),
                row.getMembershipEndDate(), row.getFreezeEndDate(),
                typeLabel(row.getPtSubscriptionType()), membershipLabel, title, message,
                row.isFrozenOnSessionDate() ? "frozen" : "membership", null);
    }

    /**
     * HÀM PHỤ — gọi ở B2 của buildAttentionItem (chỉ với gói đôi).
     * Người còn lại = cùng mã gói + cùng ngày tập + khác mã khách. Không tìm theo tên (tên có thể trùng).
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


    // =================================================================================================
    // 3. KHÁCH PHỤ TRÁCH
    // =================================================================================================

    /**
     * HÀM CHÍNH — PtDashboardServlet.loadBlocks gọi ở B3, SAU getAttentionItems (cần attentionItems để gắn vào từng khách).
     * Mỗi gói PT / buổi lẻ = 1 thẻ khách. SĐT và link Zalo do DTO tự tính từ SĐT (PtAssignedPackageView).
     */
    public ServiceResult<List<PtAssignedPackageView>> getAssignedPackages(int accountId, List<PtAttentionView> attentionItems) {
        try {
            // B1: kiểm tra quyền.
            Employee pt = findWorkingPt(accountId);
            if (pt == null) {
                return ServiceResult.fail(Messages.MSG07_NO_PERMISSION);
            }

            // B2: DAO đọc gói. Truyền thêm mã gói đang có cảnh báo để không sót khách của buổi PT dạy thay.
            List<Integer> attentionPackageIds = attentionItems.stream()
                    .map(PtAttentionView::getPtSubscriptionId)
                    .distinct()
                    .toList();
            List<PtAssignedPackageView> packages = dashboardDAO.findAssignedPackages(
                    pt.getEmployeeId(), DateTimeUtil.now(), attentionPackageIds);

            // B3: gắn nhãn loại gói.
            for (PtAssignedPackageView item : packages) {
                String label = typeLabel(item.getPtSubscriptionType());
                if (label == null) {
                    throw new SQLException("Loại gói không hợp lệ trong Khách phụ trách");
                }
                item.setPtSubscriptionTypeLabel(label);
            }

            // B4: gắn cảnh báo vào đúng thẻ khách, đưa khách cần xử lý lên đầu.
            mergeClientAttention(packages, attentionItems);
            return ServiceResult.ok(packages);

        } catch (SQLException exception) {
            exception.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /**
     * HÀM PHỤ — gọi ở B4 của getAssignedPackages.
     * Ghép theo MÃ GÓI (không theo tên). Thứ tự: thẻ có buổi ngày nghỉ → thẻ có cảnh báo khác → khách bình thường.
     */
    static void mergeClientAttention(List<PtAssignedPackageView> packages, List<PtAttentionView> items) {
        // B1: mỗi thẻ khách nhận các cảnh báo cùng SubscriptionId.
        for (PtAssignedPackageView client : packages) {
            client.getAttentionItems().clear();
            for (PtAttentionView item : items) {
                if (client.getPtSubscriptionId() == item.getPtSubscriptionId()) {
                    client.getAttentionItems().add(item);
                }
            }
        }

        // B2: sắp xếp theo độ ưu tiên (0 = ngày nghỉ, 1 = cảnh báo khác, 2 = bình thường).
        packages.sort(Comparator.comparingInt(PtAssignedPackageView::getAttentionPriority));
    }


    // =================================================================================================
    // 4. KẾT QUẢ THÁNG VÀ HOA HỒNG
    // =================================================================================================

    /**
     * HÀM CHÍNH — PtDashboardServlet.loadBlocks gọi ở B4 khi PT mở trang.
     * DAO đếm buổi + cộng tiền trong tháng; Service thêm tỷ lệ hoa hồng và tính % từng loại kết quả.
     */
    public ServiceResult<PtCommissionSummaryView> getCommissionSummary(int accountId) {
        try {
            // B1: kiểm tra quyền.
            Employee pt = findWorkingPt(accountId);
            if (pt == null) {
                return ServiceResult.fail(Messages.MSG07_NO_PERMISSION);
            }

            // B2: tháng hiện tại = từ ngày 1 tháng này đến trước ngày 1 tháng sau.
            LocalDateTime now = DateTimeUtil.now();
            LocalDate monthStart = now.toLocalDate().withDayOfMonth(1);

            // B3: DAO đếm buổi và cộng Sessions.CommissionAmount.
            PtCommissionSummaryView summary = dashboardDAO.findCommissionSummary(
                    pt.getEmployeeId(), monthStart, monthStart.plusMonths(1), now);

            // B4: thêm % hoa hồng hiện tại của PT và tính tỷ lệ từng loại kết quả.
            summary.setCommissionRate(pt.getCommissionRate());
            prepareCommissionSummary(summary);
            return ServiceResult.ok(summary);

        } catch (SQLException exception) {
            exception.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /**
     * HÀM PHỤ — gọi ở B4 của getCommissionSummary.
     * Mẫu số = tổng buổi đã có kết quả (kể cả buổi Hủy). Chưa có buổi nào → mọi tỷ lệ = 0.
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
     * HÀM PHỤ — gọi trong prepareCommissionSummary. Ví dụ 8/14 buổi → 57,142…% → làm tròn 57,14%.
     */
    private double percent(int count, int total) {
        return Math.round(count * 100.0 / total * 100.0) / 100.0;
    }


    // =================================================================================================
    // 5. ĐÁNH GIÁ PT
    // =================================================================================================

    /**
     * HÀM CHÍNH — dành cho khối "Đánh giá tháng". HIỆN CHƯA CÓ Servlet nào gọi (khối này chưa lên giao diện);
     * phần tính điểm prepareRating đã có test PtRatingServiceTest.
     */
    public ServiceResult<PtRatingView> getRating(int accountId) {
        try {
            // B1: kiểm tra quyền.
            Employee pt = findWorkingPt(accountId);
            if (pt == null) {
                return ServiceResult.fail(Messages.MSG07_NO_PERMISSION);
            }

            // B2: DAO đọc tổng sao + số khách chấm trong tháng này.
            LocalDate ratingMonth = DateTimeUtil.today().withDayOfMonth(1);
            PtRatingView rating = dashboardDAO.findRatingForMonth(pt.getEmployeeId(), ratingMonth);

            // B3: P-22 = số khách tối thiểu; chưa đủ thì ẩn điểm.
            int minimumCustomers = systemConfigService.getInt(SystemConfigService.MIN_PT_RATING_CUSTOMERS);
            prepareRating(rating, minimumCustomers);
            return ServiceResult.ok(rating);

        } catch (SQLException | IllegalStateException exception) {
            exception.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /**
     * HÀM PHỤ — gọi ở B3 của getRating. Điểm = tổng sao / số khách, cắt 2 số lẻ (4,999 hiện 4,99, không thành 5,00).
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
     * HÀM PHỤ — gọi trong prepareRating. Chọn 1 trong 6 câu động viên theo điểm.
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


    // =================================================================================================
    // 6. DÙNG CHUNG
    // =================================================================================================

    /**
     * HÀM PHỤ DÙNG CHUNG — gọi ở B1 của MỌI hàm chính:
     * getPtEmployeeCode, getSchedule, getAttentionItems, getAssignedPackages, getCommissionSummary, getRating.
     *
     * accountId (từ session) → Accounts → PersonId → Employees.
     * Trả hồ sơ PT nếu: tài khoản Active  VÀ  nhân viên là PT  VÀ  đang làm việc (GBR-10).
     * Trả null nếu không đạt → hàm chính trả MSG07 "không có quyền".
     */
    private Employee findWorkingPt(int accountId) throws SQLException {
        // B1: tài khoản phải tồn tại và đang hoạt động.
        Account account = accountDAO.findById(accountId);
        if (account == null || account.getStatus() != Account.Status.ACTIVE) {
            return null;
        }

        // B2: người của tài khoản đó phải là PT đang làm việc.
        Employee employee = employeeDAO.findByPersonId(account.getPersonId());
        if (employee == null
                || employee.getPosition() != Role.PT
                || employee.getStatus() != Employee.Status.WORKING) {
            return null;
        }
        return employee;
    }

    /**
     * HÀM PHỤ DÙNG CHUNG — loại gói → chữ tiếng Việt. Không phải gói PT → null (hàm gọi tự quyết báo lỗi).
     * Gọi ở: B2 prepareSessionDisplay, buildClosedDayItem, B4 buildAttentionItem, B3 getAssignedPackages.
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
