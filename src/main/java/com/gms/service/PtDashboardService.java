package com.gms.service;

import com.gms.constant.Messages;
import com.gms.constant.Role;
import com.gms.dao.AccountDAO;
import com.gms.dao.EmployeeDAO;
import com.gms.dao.PtDashboardDAO;
import com.gms.dao.PtRemindDAO;
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
import java.util.List;
import java.util.Set;

/** Kiểm tra quyền và chuẩn bị dữ liệu các khối trên dashboard PT (UC-32). */
public class PtDashboardService {

    private static final int SCHEDULE_DAYS = 7; // Số ngày hiển thị đã chốt cho dashboard.
    private static final int ATTENTION_DAYS = 14; // Phạm vi cảnh báo membership/bảo lưu, tính cả hôm nay.

    private final AccountDAO accountDAO = new AccountDAO(); // Đọc tài khoản và PersonId.
    private final EmployeeDAO employeeDAO = new EmployeeDAO(); // Tìm hồ sơ PT từ PersonId.
    private final PtDashboardDAO dashboardDAO = new PtDashboardDAO(); // Đọc lịch và dữ liệu gói của khách.
    private final PtRemindDAO remindDAO = new PtRemindDAO(); // Nút "Nhắc khách": gửi thông báo + biết hôm nay đã nhắc chưa.
    private final SystemConfigService systemConfigService = new SystemConfigService(); // Đọc P-22 trước khi hiện điểm.

    /** Lấy Employees.EmployeeCode của PT đang đăng nhập để hiện cạnh tên ở đầu trang. */
    public ServiceResult<String> getPtEmployeeCode(int accountId) {
        try {
            Account account = accountDAO.findById(accountId);
            if (account == null || account.getStatus() != Account.Status.ACTIVE) {
                return ServiceResult.fail(Messages.MSG07_NO_PERMISSION);
            }

            Employee employee = employeeDAO.findByPersonId(account.getPersonId());
            if (employee == null || employee.getPosition() != Role.PT
                    || employee.getStatus() != Employee.Status.WORKING) {
                return ServiceResult.fail(Messages.MSG07_NO_PERMISSION);
            }

            return ServiceResult.ok(employee.getEmployeeCode());
        } catch (SQLException exception) {
            exception.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    // =====================================================================
    // 1. LỊCH 7 NGÀY: hàm lấy lịch và các hàm phụ của lịch nằm cùng nhóm.
    // =====================================================================

    /**
     * Lấy lịch của PT đang đăng nhập và tạo đủ 7 nhóm ngày, bắt đầu từ hôm nay.
     * @param accountId mã tài khoản từ session, không lấy từ tham số trình duyệt
     * @return các PtScheduleDayView do Service tạo, hoặc thông báo lỗi
     */
    public ServiceResult<List<PtScheduleDayView>> getSchedule(int accountId) {
        try {
            // B1: kiểm tra tài khoản còn hoạt động và có tồn tại (GBR-10).
            // truyền vào accountId lấy từ session sau bước login
            Account account = accountDAO.findById(accountId);
            if (account == null || account.getStatus() != Account.Status.ACTIVE) {
                return ServiceResult.fail(Messages.MSG07_NO_PERMISSION);
            }

            // B2: tìm đúng hồ sơ PT đang làm việc (GBR-10).
            // PT chỉ xem được Dashboard của mình, và trạng thái = đang làm việc
            Employee employee = employeeDAO.findByPersonId(account.getPersonId());
            if (employee == null || employee.getPosition() != Role.PT
                    || employee.getStatus() != Employee.Status.WORKING) {
                return ServiceResult.fail(Messages.MSG07_NO_PERMISSION);
            }

            // B3: lấy lịch từ hôm nay đến hôm nay + 6 ngày.
            LocalDateTime now = DateTimeUtil.now();
            LocalDate today = now.toLocalDate();
            LocalDate toDate = today.plusDays(SCHEDULE_DAYS - 1);
            List<PtSessionView> sessions = dashboardDAO.findSessions(
                    employee.getEmployeeId(), today, toDate);

            // B3.1: bổ sung chữ tiếng Việt và điều kiện mở hộp cho từng DTO do DAO tạo.
            // Lấy now một lần để các buổi được xét cùng thời điểm, kể cả lúc qua nửa đêm.
            for (PtSessionView session : sessions) {
                prepareSessionDisplay(session, now);
            }

            // B4: tạo từng nhóm ngày và chọn các buổi của ngày đó.
            List<PtScheduleDayView> scheduleDays = new ArrayList<>();
            for (int i = 0; i < SCHEDULE_DAYS; i++) { // Vòng lặp ngoài: hôm nay (i = 0) → 6 ngày sau, đúng thứ tự đọc trái → phải
                // B4.1: xác định ngày, nhãn và dấu hiệu hôm nay.
                LocalDate slotDate = today.plusDays(i); // ngày tháng năm
                String dayLabel = buildDayLabel(slotDate, today); // thứ/ Hôm nay
                boolean isToday = slotDate.equals(today);

                // B4.2: chọn các DTO buổi có ngày trùng slotDate.
                // Tạo các buổi trong ngày = slotDate - Vòng lặp trong
                List<PtSessionView> sessionsOfDay = new ArrayList<>();
                for (PtSessionView session : sessions) {
                    if (session.getSlotDate().equals(slotDate)) {
                        sessionsOfDay.add(session);
                    }
                }

                // B4.3: tạo DTO ngày, kể cả khi danh sách buổi rỗng.
                PtScheduleDayView scheduleDay = new PtScheduleDayView(
                        slotDate, dayLabel, isToday, sessionsOfDay);
                scheduleDays.add(scheduleDay);
            }

            // B5: trả lịch đã gom để Servlet chuyển sang JSP.
            return ServiceResult.ok(scheduleDays);
        } catch (SQLException exception) {
            // B6: ghi lỗi kỹ thuật, trả thông báo thân thiện.
            exception.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /**
     * Bổ sung phần hiển thị vào PtSessionView do DAO tạo, không thay đổi dữ liệu gốc.
     * @param session một buổi DAO đọc từ DB
     * @param now thời điểm hiện tại theo giờ VN, lấy một lần trong getSchedule
     */
    void prepareSessionDisplay(PtSessionView session, LocalDateTime now) throws SQLException {
        // Lịch từ hôm nay trở đi không được có kết quả vắng: JOB-04 chỉ chốt sau ngày tập.
        // Báo dữ liệu bất thường, không đổi trạng thái DB hoặc âm thầm ẩn buổi.
        if (session.getStatus() == Session.Status.MISSED
                && !session.getSlotDate().isBefore(now.toLocalDate())) {
            throw new SQLException("Buổi hôm nay hoặc tương lai đã bị chốt vắng, SessionId="
                    + session.getSessionId());
        }

        // B1: lấy loại gói gốc để chọn chữ hiện dưới tên khách.
        switch (session.getSubscriptionType()) {
            case PT_SINGLE:
                session.setSubscriptionTypeLabel("PT cá nhân");
                break;
            case PT_COUPLE:
                session.setSubscriptionTypeLabel("PT đôi");
                break;
            case SINGLE_SESSION:
                session.setSubscriptionTypeLabel("Buổi lẻ");
                break;
            default:
                throw new SQLException("Loại gói không hợp lệ cho buổi PT, SessionId=" + session.getSessionId());
        }

        // B2: lấy trạng thái gốc để chọn chữ và màu badge; không ghi lại Sessions.Status.
        switch (session.getStatus()) {
            case SCHEDULED:
                session.setStatusLabel("Đã lên lịch");
                session.setStatusBadgeClass("badge-warning");
                break;
            case CHECKED_IN:
                session.setStatusLabel("Đã check-in");
                session.setStatusBadgeClass("badge-success");
                break;
            case MISSED:
                session.setStatusLabel("Vắng");
                session.setStatusBadgeClass("badge-danger");
                break;
            case CANCELLED:
                session.setStatusLabel("Đã hủy");
                session.setStatusBadgeClass("badge-neutral");
                break;
        }

        // B3: chỉ hiện lý do trên buổi vắng; buổi khác không có dòng lý do.
        session.setMissedReasonLabel(null);
        if (session.getStatus() == Session.Status.MISSED && session.getMissedReason() != null) {
            switch (session.getMissedReason()) {
                case NO_CHECK_IN:
                    session.setMissedReasonLabel("Khách không check-in");
                    break;
                case NO_MEMBERSHIP:
                    session.setMissedReasonLabel("Không có membership hợp lệ");
                    break;
                case MEMBERSHIP_FROZEN:
                    session.setMissedReasonLabel("Membership đang bảo lưu");
                    break;
            }
        }

        // B4: chỉ mở hộp với buổi còn Scheduled và chưa đến giờ bắt đầu (BR-69).
        // Các nút trong hộp vẫn khóa cho đến khi có Service xử lý từng nghiệp vụ.
        LocalDateTime startsAt = LocalDateTime.of(session.getSlotDate(), session.getStartTime());
        boolean canOpenActions = session.getStatus() == Session.Status.SCHEDULED
                && startsAt.isAfter(now); // Giờ bắt đầu nằm sau thời điểm hiện tại → buổi chưa bắt đầu.
        session.setCanOpenActions(canOpenActions);
    }

    /** Chọn nhãn ngày để JSP hiện “Hôm nay” hoặc tên thứ tiếng Việt. */
    private String buildDayLabel(LocalDate slotDate, LocalDate today) {
        // B1: ưu tiên nhãn hôm nay cho ngày đầu lịch.
        if (slotDate.equals(today)) {
            return "Hôm nay";
        }

        // B2: đổi thứ của slotDate thành nhãn tiếng Việt.
        switch (slotDate.getDayOfWeek()) {
            case MONDAY:
                return "Thứ Hai";
            case TUESDAY:
                return "Thứ Ba";
            case WEDNESDAY:
                return "Thứ Tư";
            case THURSDAY:
                return "Thứ Năm";
            case FRIDAY:
                return "Thứ Sáu";
            case SATURDAY:
                return "Thứ Bảy";
            default:
                return "Chủ Nhật";
        }
    }


    // =====================================================================
    // 2. CẦN CHÚ Ý: hàm lấy cảnh báo và các hàm phụ nằm ngay bên dưới.
    // =====================================================================
    // Luồng gọi: getAttentionItems -> buildAttentionItems -> selectAttentionPackages.
    // Sau khi chọn dữ liệu: buildAttentionItems gọi buildAttentionItem cho từng dòng.
    // Riêng gói đôi: buildAttentionItem gọi findPartnerPackage để kiểm tra người còn lại.

    /**
     * Hàm chính, Servlet gọi: kiểm tra quyền PT, gọi DAO lấy dữ liệu 14 ngày,
     * rồi gọi buildAttentionItems và bọc danh sách cảnh báo trong ServiceResult.
     * Không tạo từng DTO ở đây; phần tạo một DTO nằm trong buildAttentionItem.
     * Chỉ xử lý membership và bảo lưu; ngưỡng cảnh báo gói PT sắp hết chưa được chốt.
     * @param accountId mã tài khoản từ session, không lấy mã PT từ trình duyệt
     * @return danh sách PtAttentionView do Service tạo; rỗng nếu không có vấn đề
     */
    public ServiceResult<List<PtAttentionView>> getAttentionItems(int accountId) {
        try {
            // B1: kiểm tra tài khoản còn hoạt động.
            Account account = accountDAO.findById(accountId);
            if (account == null || account.getStatus() != Account.Status.ACTIVE) {
                return ServiceResult.fail(Messages.MSG07_NO_PERMISSION);
            }

            // B2: tìm hồ sơ PT đang làm việc của chính tài khoản này.
            Employee employee = employeeDAO.findByPersonId(account.getPersonId());
            if (employee == null || employee.getPosition() != Role.PT
                    || employee.getStatus() != Employee.Status.WORKING) {
                return ServiceResult.fail(Messages.MSG07_NO_PERMISSION);
            }

            // B3: lấy một thời điểm giờ VN và đọc dữ liệu từ hôm nay đến hôm nay + 13 ngày.
            LocalDateTime now = DateTimeUtil.now();
            LocalDate today = now.toLocalDate();
            LocalDate toDate = today.plusDays(ATTENTION_DAYS - 1);
            // Lấy cả dòng hợp lệ + dòng không hợp lệ
            List<PtClientPackageView> clientPackages = dashboardDAO.findClientPackages(
                    employee.getEmployeeId(), today, toDate, now);

            // B4: gom các ngày cùng vấn đề, rồi tạo DTO có field hiển thị trực tiếp.
            List<PtAttentionView> attentionItems = buildAttentionItems(clientPackages, today);

            // B4.1: khách đã được nhắc hôm nay → JSP đổi nút thành "Đã nhắc hôm nay".
            Set<Integer> remindedToday = remindDAO.findRemindedToday(employee.getEmployeeId());
            for (PtAttentionView item : attentionItems) {
                item.setReminded(remindedToday.contains(item.getCustomerId()));
            }
            return ServiceResult.ok(attentionItems);
        } catch (SQLException exception) {
            // B5: lỗi đọc dữ liệu được báo riêng, không trả danh sách rỗng giả là không có cảnh báo.
            exception.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /**
     * Nút "Nhắc khách" ở khối Cần chú ý: gửi 1 thông báo tới tài khoản của khách.
     * Không tin form: tính lại danh sách Cần chú ý của CHÍNH PT này rồi tìm đúng khách + gói.
     * Mỗi khách chỉ nhắc 1 lần/ngày để không thành spam.
     */
    public ServiceResult<Void> remindCustomer(int accountId, int customerId, int ptSubscriptionId) {
        try {
            // B1: danh sách Cần chú ý hiện tại (đã kiểm tra tài khoản là PT đang làm việc)
            ServiceResult<List<PtAttentionView>> items = getAttentionItems(accountId);
            if (!items.isSuccess()) {
                return ServiceResult.fail(items.getMessage());
            }

            // B2: khách + gói phải còn trong danh sách của PT này
            PtAttentionView target = null;
            for (PtAttentionView item : items.getData()) {
                if (item.getCustomerId() == customerId && item.getPtSubscriptionId() == ptSubscriptionId) {
                    target = item;
                    break;
                }
            }
            if (target == null) {
                return ServiceResult.fail("Khách này không còn cần nhắc (đã có gói hợp lệ hoặc buổi đã qua).");
            }
            if (target.isReminded()) {
                return ServiceResult.fail("Bạn đã nhắc " + target.getCustomerName() + " hôm nay rồi.");
            }

            // B3: khách phải có tài khoản để nhận thông báo
            Integer customerAccountId = remindDAO.findActiveAccountId(customerId);
            if (customerAccountId == null) {
                return ServiceResult.fail(target.getCustomerName() + " chưa có tài khoản để nhận thông báo. Hãy nhắn Zalo cho khách.");
            }

            // B4: ghi thông báo (PT gửi) cho đúng 1 người
            Account account = accountDAO.findById(accountId);
            Employee pt = employeeDAO.findByPersonId(account.getPersonId());
            remindDAO.insertReminder(pt.getEmployeeId(), customerAccountId, remindContent(target));
            return ServiceResult.ok(null, "Đã nhắc " + target.getCustomerName() + ".");

        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /** Lời nhắc khách đọc ở chuông thông báo (10–500 ký tự, DBR-15). */
    static String remindContent(PtAttentionView item) {
        String day = DateTimeUtil.formatDate(item.getSessionDate());
        if (item.getFreezeEndDate() != null) {
            return "Buổi PT ngày " + day + " nằm trong kỳ bảo lưu gói thành viên của bạn. "
                    + "Liên hệ lễ tân nếu muốn kết thúc bảo lưu sớm để tập buổi này.";
        }
        return "Buổi PT ngày " + day + " của bạn chưa có gói thành viên hợp lệ nên chưa vào được phòng tập. "
                + "Mở Gói của tôi để mua gói thành viên hoặc vé ngày trước buổi tập.";
    }

    /**
     * getAttentionItems gọi hàm này để chuyển list dữ liệu DAO thành list cảnh báo Service.
     * Gọi selectAttentionPackages để bỏ khách không có vấn đề và gom ngày trùng vấn đề.
     * Sau đó gọi buildAttentionItem cho từng dòng đã chọn, thêm DTO vào attentionItems.
     * @param clientPackages các DTO dữ liệu gốc do PtDashboardDAO.findClientPackages tạo
     * @param today ngày hiện tại theo giờ VN để phân biệt đã hết hạn với thiếu gói cho ngày tập
     * @return danh sách cảnh báo, không chứa dòng cho khách dùng được gym vào ngày tập
     * @throws SQLException nếu thiếu dữ liệu người còn lại của gói đôi
     */
    List<PtAttentionView> buildAttentionItems(List<PtClientPackageView> clientPackages,
            LocalDate today) throws SQLException {
        // B1: chọn một DTO gốc cho mỗi vấn đề, giữ buổi gần nhất thay vì tạo dòng cho mọi buổi.
        // attentionPackages = Bỏ các khách dùng được gym + gom các khách cùng 1 loại vấn đề + giữ dòng có ngày tập sắp tới sớm nhất
        List<PtClientPackageView> attentionPackages = selectAttentionPackages(clientPackages);

        // B2: chuyển từng DTO được chọn thành một dòng cảnh báo.
        List<PtAttentionView> attentionItems = new ArrayList<>();
        for (PtClientPackageView clientPackage : attentionPackages) {
            // buildAttentionItem = Nhận một DTO gốc đã được chọn => Copy các field cần hiển thị + Tạo nhãn và lời nhắc, xét thêm người còn lại nếu là PT đôi
            PtAttentionView attentionItem = buildAttentionItem(clientPackage, clientPackages, today);
            attentionItems.add(attentionItem);
        }
        return attentionItems;
    }

    /**
     * buildAttentionItems gọi hàm này để chọn dữ liệu cần cảnh báo, chưa tạo PtAttentionView.
     * Bỏ khách đủ membership và không bảo lưu; giữ ngày gần nhất theo khách + gói + loại vấn đề.
     * Ví dụ 5 ngày thiếu membership cùng khách/gói chỉ giữ 1 PtClientPackageView của ngày gần nhất.
     * @param clientPackages toàn bộ dữ liệu DAO, gồm cả khách không có vấn đề
     * @return list DTO gốc đã chọn; rỗng nếu không cần cảnh báo
     */
    private List<PtClientPackageView> selectAttentionPackages(List<PtClientPackageView> clientPackages) {
        List<PtClientPackageView> attentionPackages = new ArrayList<>();
        for (PtClientPackageView clientPackage : clientPackages) {
            // B1: đủ membership và không bảo lưu thì không cần cảnh báo.
            // Bỏ qua cảnh báo, ko quan tâm nhóm này
            if (clientPackage.isHasMembershipOnSessionDate() && !clientPackage.isFrozenOnSessionDate()) {
                continue;
            }


            // Nhóm cần hiển thị lên
            // B2: tìm vấn đề đã chọn của cùng khách và cùng gói. => gom nhóm
            // frozen = true là cảnh báo bảo lưu; false ở đây là cảnh báo thiếu membership (đã bỏ khách đủ gói ở B1).
            int existingIndex = -1;
            for (int i = 0; i < attentionPackages.size(); i++) {
                PtClientPackageView existingPackage = attentionPackages.get(i);
                if (existingPackage.getCustomerId() == clientPackage.getCustomerId()
                        && existingPackage.getPtSubscriptionId() == clientPackage.getPtSubscriptionId()
                        && existingPackage.isFrozenOnSessionDate() == clientPackage.isFrozenOnSessionDate()) {
                    existingIndex = i;
                    break;
                }
            }

            // B3: mỗi khách + gói PT + loại vấn đề chỉ giữ một dòng cảnh báo.
            // Khác khách, khác gói hoặc khác loại vấn đề thì vẫn thêm một dòng riêng.
            // existingIndex là vị trí dòng đã tìm được ở B2; -1 nghĩa là chưa tìm thấy.
            if (existingIndex == -1) {
                // B3.1: chưa có dòng cùng cả ba thông tin trên, thêm DTO đang xét vào danh sách.
                attentionPackages.add(clientPackage);
            } else {
                // B3.2: đã có dòng trùng, lấy DTO đang giữ tại vị trí existingIndex để so ngày tập.
                PtClientPackageView existingPackage = attentionPackages.get(existingIndex);
                // DTO đang xét có ngày tập sớm hơn thì dùng nó làm cảnh báo cần xử lý trước.
                // Ví dụ đang giữ 15/10, gặp 12/10 thì thay; gặp 18/10 thì giữ nguyên 15/10.
                if (clientPackage.getSessionDate().isBefore(existingPackage.getSessionDate())) {
                    // set thay DTO tại vị trí cũ, không thêm dòng; số dòng cảnh báo vẫn giữ nguyên.
                    attentionPackages.set(existingIndex, clientPackage);
                }
            }
        }
        return attentionPackages;
    }

    /**
     * buildAttentionItems gọi hàm này để tạo đúng một PtAttentionView từ một dòng dữ liệu đã chọn.
     * Chuẩn bị tiêu đề, lời nhắc, nhãn loại gói và tình trạng membership; chép các giá trị cần hiện.
     * Nếu là gói đôi, gọi findPartnerPackage trên list DAO đầy đủ để giải thích người còn lại.
     * @param clientPackage dữ liệu của khách/gói/ngày được chọn cho cảnh báo
     * @param clientPackages list DAO đầy đủ, giữ cả người có membership hợp lệ để xét gói đôi
     * @param today ngày hiện tại giờ VN để chọn nhãn đã hết hạn hay không bao phủ ngày tập
     * @return một DTO có field trực tiếp để JSP hiển thị
     * @throws SQLException nếu thiếu dữ liệu người còn lại của gói đôi
     */
    private PtAttentionView buildAttentionItem(PtClientPackageView clientPackage,
            List<PtClientPackageView> clientPackages, LocalDate today) throws SQLException {
        // B1: chọn tiêu đề/lời nhắc. Ngày tập lấy từ getSessionDate(), in dd/MM/yyyy.
        String sessionDateText = DateTimeUtil.formatDate(clientPackage.getSessionDate());
        String attentionTitle;
        String attentionMessage;
        if (clientPackage.isFrozenOnSessionDate()) {
            attentionTitle = "Membership đang bảo lưu";
            attentionMessage = "Buổi ngày " + sessionDateText + " nằm trong kỳ bảo lưu của khách.";
        } else {
            attentionTitle = "Cần membership";
            attentionMessage = "Cần membership hợp lệ cho buổi ngày " + sessionDateText + ".";
        }

        // B2: chọn nhãn loại gói; gói đôi phải đọc người còn lại tại đúng ngày tập này.
        String ptSubscriptionTypeLabel = "PT cá nhân";
        if (clientPackage.getPtSubscriptionType() == Package.Type.PT_COUPLE) {
            ptSubscriptionTypeLabel = "PT đôi";
            PtClientPackageView partner = findPartnerPackage(clientPackage, clientPackages);
            if (partner == null) {
                throw new SQLException("Thiếu dữ liệu người còn lại của gói đôi, SubscriptionId="
                        + clientPackage.getPtSubscriptionId());
            }

            boolean partnerCanUseGym = partner.isHasMembershipOnSessionDate()
                    && !partner.isFrozenOnSessionDate();
            if (partnerCanUseGym) {
                attentionMessage += " " + partner.getCustomerName()
                        + " vẫn dùng được gym vào ngày này.";
            } else {
                attentionMessage += " " + partner.getCustomerName()
                        + " cũng không dùng được gym vào ngày này; cả hai cần được kiểm tra trước buổi tập.";
            }
        }

        // B3: ưu tiên bảo lưu; nếu thiếu gói thì dùng hạn membership và today để giải thích.
        String membershipStatusLabel;
        if (clientPackage.isFrozenOnSessionDate()) {
            membershipStatusLabel = "Đang bảo lưu";
        } else if (clientPackage.getMembershipEndDate() == null) {
            membershipStatusLabel = "Chưa có membership phù hợp";
        } else if (clientPackage.getMembershipEndDate().isBefore(today)) {
            membershipStatusLabel = "Đã hết hạn";
        } else {
            membershipStatusLabel = "Không bao phủ ngày tập";
        }

        // B4: chép các giá trị cần hiện; không chuyển mã khách/gói hoặc hai boolean sang DTO hiển thị.
        // JSP sẽ đọc trực tiếp item.customerName, item.ptEndDate, item.attentionTitle...
        return new PtAttentionView(clientPackage.getCustomerId(), clientPackage.getPtSubscriptionId(),
                clientPackage.getCustomerName(), clientPackage.getPtEndDate(),
                clientPackage.getScheduledSessionCount(), clientPackage.getSessionDate(),
                clientPackage.getMembershipEndDate(), clientPackage.getFreezeEndDate(),
                ptSubscriptionTypeLabel, membershipStatusLabel, attentionTitle, attentionMessage);
    }

    /**
     * buildAttentionItem chỉ gọi hàm này khi đang tạo cảnh báo cho gói đôi.
     * Tìm dữ liệu người còn lại theo cùng mã gói PT + ngày tập, khác customerId; không tìm theo tên.
     * Không tạo DTO cảnh báo; trả DTO gốc để hàm gọi xét người còn lại có dùng được gym không.
     * @param clientPackage dữ liệu của người đang được cảnh báo
     * @param clientPackages list DAO đầy đủ, chưa bỏ người có membership hợp lệ
     * @return dữ liệu người còn lại; null nếu thiếu, hàm gọi sẽ báo lỗi thay vì tự đoán
     */
    private PtClientPackageView findPartnerPackage(PtClientPackageView clientPackage,
            List<PtClientPackageView> clientPackages) {
        for (PtClientPackageView otherCustomer : clientPackages) {
            if (otherCustomer.getPtSubscriptionId() == clientPackage.getPtSubscriptionId()
                    && otherCustomer.getSessionDate().equals(clientPackage.getSessionDate())
                    && otherCustomer.getCustomerId() != clientPackage.getCustomerId()) {
                return otherCustomer;
            }
        }
        return null;
    }


    // =====================================================================
    // 3. KHÁCH PHỤ TRÁCH: một DTO cho mỗi gói PT/buổi lẻ, chỉ bổ sung nhãn.
    // =====================================================================

    /**
     * Servlet gọi hàm này để lấy đầy đủ danh sách gói PT/buổi lẻ của PT đang đăng nhập.
     * DAO đã chọn gói phù hợp và tạo PtAssignedPackageView; Service bổ sung nhãn vào chính DTO đó.
     * Không gom theo ngày, không gom nhiều gói của cùng khách thành một dòng, không tạo DTO thứ hai.
     * @param accountId mã tài khoản lấy từ session đăng nhập, không phải mã nhân viên PT
     * @return danh sách DTO đã có nhãn; rỗng nếu không có gói phù hợp, hoặc kết quả lỗi
     */
    public ServiceResult<List<PtAssignedPackageView>> getAssignedPackages(int accountId) {
        try {
            // B1: kiểm tra tài khoản tồn tại và còn hoạt động.
            Account account = accountDAO.findById(accountId);
            if (account == null || account.getStatus() != Account.Status.ACTIVE) {
                return ServiceResult.fail(Messages.MSG07_NO_PERMISSION);
            }

            // B2: từ PersonId của tài khoản, tìm hồ sơ PT đang làm việc.
            // EmployeeId tìm được ở đây mới là mã PT dùng để lọc dữ liệu trong DAO.
            Employee employee = employeeDAO.findByPersonId(account.getPersonId());
            if (employee == null || employee.getPosition() != Role.PT
                    || employee.getStatus() != Employee.Status.WORKING) {
                return ServiceResult.fail(Messages.MSG07_NO_PERMISSION);
            }

            // B3: lấy thời điểm giờ VN một lần, gọi DAO lấy các DTO đã điền năm field gốc.
            // Gói cá nhân/đôi theo PT trên gói; buổi lẻ theo PT thực tế của buổi chưa bắt đầu.
            LocalDateTime now = DateTimeUtil.now();
            List<PtAssignedPackageView> assignedPackages = dashboardDAO.findAssignedPackages(
                    employee.getEmployeeId(), now);

            // B4: lặp từng DTO DAO đã tạo, đổi loại gói thành nhãn rồi gán vào chính DTO đó.
            // Chỉ bổ sung ptSubscriptionTypeLabel; tên, hạn gói và số buổi vẫn giữ nguyên dữ liệu DAO.
            for (PtAssignedPackageView assignedPackage : assignedPackages) {
                switch (assignedPackage.getPtSubscriptionType()) {
                    case PT_SINGLE:
                        assignedPackage.setPtSubscriptionTypeLabel("PT cá nhân");
                        break;
                    case PT_COUPLE:
                        assignedPackage.setPtSubscriptionTypeLabel("PT đôi");
                        break;
                    case SINGLE_SESSION:
                        assignedPackage.setPtSubscriptionTypeLabel("Buổi lẻ");
                        break;
                    default:
                        throw new SQLException("Loại gói không hợp lệ trong Khách phụ trách");
                }
            }

            // B5: trả danh sách đã có nhãn để Servlet truyền sang JSP bằng assignedPackages.
            // Nếu DAO trả list rỗng, vẫn là thành công; JSP sẽ hiện thông báo không có gói phù hợp.
            return ServiceResult.ok(assignedPackages);
        } catch (SQLException exception) {
            // B6: ghi lỗi kỹ thuật; Servlet nhận thông báo an toàn cho assignedPackagesError.
            exception.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }


    // =====================================================================
    // 4. KẾT QUẢ THÁNG VÀ HOA HỒNG: DAO lấy số/tiền, Service tính tỷ lệ.
    // =====================================================================

    /**
     * Servlet gọi hàm này để lấy kết quả tháng của PT đang đăng nhập.
     * @param accountId mã tài khoản từ session; không dùng làm PtEmployeeId trong SQL
     * @return một PtCommissionSummaryView đã có số buổi, tiền và tỷ lệ; hoặc thông báo lỗi
     */
    public ServiceResult<PtCommissionSummaryView> getCommissionSummary(int accountId) {
        try {
            // B1: tài khoản phải còn hoạt động.
            Account account = accountDAO.findById(accountId);
            if (account == null || account.getStatus() != Account.Status.ACTIVE) {
                return ServiceResult.fail(Messages.MSG07_NO_PERMISSION);
            }

            // B2: tìm EmployeeId của chính PT đang làm việc để DAO chỉ lấy buổi của PT này.
            Employee employee = employeeDAO.findByPersonId(account.getPersonId());
            if (employee == null || employee.getPosition() != Role.PT
                    || employee.getStatus() != Employee.Status.WORKING) {
                return ServiceResult.fail(Messages.MSG07_NO_PERMISSION);
            }

            // B3: dùng cùng một now giờ VN để chọn tháng và chia buổi chờ chốt/dự kiến.
            LocalDateTime now = DateTimeUtil.now();
            LocalDate monthStart = now.toLocalDate().withDayOfMonth(1);
            LocalDate nextMonthStart = monthStart.plusMonths(1);

            // B4: DAO đếm buổi, cộng Sessions.CommissionAmount và tạo DTO có 10 số gốc.
            PtCommissionSummaryView summary = dashboardDAO.findCommissionSummary(
                    employee.getEmployeeId(), monthStart, nextMonthStart, now);

            // B5: thêm tỷ lệ hoa hồng hiện tại từ Employees; không tính lại tiền từng buổi.
            summary.setCommissionRate(employee.getCommissionRate());

            // B6: Service điền mẫu số và tỷ lệ buổi; Servlet truyền DTO sang JSP.
            prepareCommissionSummary(summary);
            return ServiceResult.ok(summary);
        } catch (SQLException exception) {
            exception.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /** Tính năm tỷ lệ từ số buổi DAO trả về; Cancelled cũng thuộc mẫu số. */
    void prepareCommissionSummary(PtCommissionSummaryView summary) {
        int resolvedSessionCount = summary.getCheckedInCount()
                + summary.getNoCheckInCount()
                + summary.getNoMembershipCount()
                + summary.getMembershipFrozenCount()
                + summary.getCancelledCount();

        // Không có buổi đã chốt: mẫu số là 0, các tỷ lệ hiển thị đều là 0%.
        if (resolvedSessionCount == 0) {
            summary.setResolvedStatistics(0, 0, 0, 0, 0, 0);
            return;
        }

        // Mỗi loại chia cho cùng ResolvedTotal, làm tròn đến hai chữ số thập phân.
        summary.setResolvedStatistics(
                resolvedSessionCount,
                calculateRatePercent(summary.getCheckedInCount(), resolvedSessionCount),
                calculateRatePercent(summary.getNoCheckInCount(), resolvedSessionCount),
                calculateRatePercent(summary.getNoMembershipCount(), resolvedSessionCount),
                calculateRatePercent(summary.getMembershipFrozenCount(), resolvedSessionCount),
                calculateRatePercent(summary.getCancelledCount(), resolvedSessionCount));
    }

    /** Ví dụ 8/14 buổi: 8 * 100 / 14 = 57,142...%, làm tròn thành 57,14%. */
    private double calculateRatePercent(int sessionCount, int resolvedSessionCount) {
        double ratePercent = sessionCount * 100.0 / resolvedSessionCount;
        return Math.round(ratePercent * 100.0) / 100.0;
    }

    // =====================================================================
    // 5. ĐÁNH GIÁ PT: tổng hợp tháng hiện tại, chỉ hiện điểm khi đủ khách P-22.
    // =====================================================================

    /** Servlet gọi để lấy điểm tháng này của đúng PT đang đăng nhập. */
    public ServiceResult<PtRatingView> getRating(int accountId) {
        try {
            // B1: không lấy PtEmployeeId từ URL; tìm qua tài khoản và hồ sơ PT còn làm việc.
            Account account = accountDAO.findById(accountId);
            if (account == null || account.getStatus() != Account.Status.ACTIVE) {
                return ServiceResult.fail(Messages.MSG07_NO_PERMISSION);
            }
            Employee employee = employeeDAO.findByPersonId(account.getPersonId());
            if (employee == null || employee.getPosition() != Role.PT
                    || employee.getStatus() != Employee.Status.WORKING) {
                return ServiceResult.fail(Messages.MSG07_NO_PERMISSION);
            }

            // B2: RatingMonth là ngày 1 của tháng hiện tại theo giờ VN.
            LocalDate ratingMonth = DateTimeUtil.today().withDayOfMonth(1);
            PtRatingView rating = dashboardDAO.findRatingForMonth(employee.getEmployeeId(), ratingMonth);

            // B3: P-22 là số khách tối thiểu. Service ẩn điểm nếu chưa đủ người chấm.
            int minimumCustomers = systemConfigService.getInt(SystemConfigService.MIN_PT_RATING_CUSTOMERS);
            prepareRating(rating, minimumCustomers);
            return ServiceResult.ok(rating);
        } catch (SQLException | IllegalStateException exception) {
            exception.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /**
     * Đổi hai số DAO trả thành điểm và lời nhắn cho JSP.
     * Điểm chỉ được tính sau khi số khách đạt P-22; không trả tên hay nội dung phiếu cho PT.
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

        // Điểm hiện = tổng sao / số khách; cắt ở hai chữ số để 4,999 không hiện thành 5,00.
        BigDecimal average = BigDecimal.valueOf(rating.getRatingTotal())
                .divide(BigDecimal.valueOf(customerCount), 2, RoundingMode.DOWN);
        rating.setDisplay(true, average,
                buildRatingMessage(average, rating.getRatingTotal() == 5L * customerCount));
    }

    /** Chọn một trong sáu câu theo điểm hiện; 5 tuyệt đối nghĩa là mọi khách đều chấm 5. */
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
}
