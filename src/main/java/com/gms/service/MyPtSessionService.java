package com.gms.service;

import com.gms.constant.Messages;
import com.gms.dao.AccountDAO;
import com.gms.dao.CustomerDAO;
import com.gms.dao.MyPtSessionDAO;
import com.gms.dto.MyPtHistorySessionView;
import com.gms.dto.MyPtPackageView;
import com.gms.dto.MyPtSessionFilter;
import com.gms.dto.MyPtSessionPage;
import com.gms.dto.MyPtUpcomingSessionView;
import com.gms.dto.PtSessionCancelInfo;
import com.gms.dto.ServiceResult;
import com.gms.entity.Account;
import com.gms.entity.Customer;
import com.gms.entity.Package;
import com.gms.entity.Session;
import com.gms.util.DateTimeUtil;
import com.gms.util.ValidationUtil;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Kiểm tra khách đăng nhập và chuẩn bị dữ liệu cho màn Lịch tập của tôi. */
public class MyPtSessionService {

    private static final int DAYS_PER_PAGE = 8; // Hai khối cùng tối đa 8 ngày có buổi/trang.

    private final AccountDAO accountDAO = new AccountDAO(); // Đọc tài khoản và PersonId.
    private final CustomerDAO customerDAO = new CustomerDAO(); // Tìm hồ sơ khách từ PersonId.
    private final MyPtSessionDAO sessionDAO = new MyPtSessionDAO(); // Đọc gói và buổi PT khách được xem.

    // =====================================================================
    // 1. GÓI PT CỦA BẠN: lấy gói và bổ sung dữ liệu hiển thị.
    // =====================================================================

    /**
     * Lấy gói PT khách đã mua có Subscriptions.Status = Active, gồm gói đứng tên và gói đôi đi cùng.
     * Không lọc Packages.Status: danh mục ngừng bán thì gói đã mua vẫn được hiển thị.
     * DAO tạo MyPtPackageView; Service điền nhãn loại gói và lượt đổi còn lại.
     *
     * @param accountId mã tài khoản từ session, không lấy customerId từ trình duyệt
     * @return danh sách gói (có thể rỗng), hoặc thông báo lỗi quyền/truy vấn
     */
    public ServiceResult<List<MyPtPackageView>> getPtPackages(int accountId) {
        return getPtPackages(accountId, true);
    }

    /** Gói đã thanh toán, kể cả gói cũ, để lọc lịch sử; quyền xem như danh sách đang dùng. */
    public ServiceResult<List<MyPtPackageView>> getPtPackageOptions(int accountId) {
        return getPtPackages(accountId, false);
    }

    private ServiceResult<List<MyPtPackageView>> getPtPackages(int accountId, boolean activeOnly) {
        try {
            // B1: kiểm tra tài khoản tồn tại và còn hoạt động (GBR-10).
            Account account = accountDAO.findById(accountId);
            if (account == null || account.getStatus() != Account.Status.ACTIVE) {
                return ServiceResult.fail(Messages.MSG07_NO_PERMISSION);
            }

            // B2: xác định customerId qua PersonId của tài khoản đăng nhập.
            // Không chặn xem khi membership Frozen/Expired: quyền xem khác quyền vào gym.
            Customer customer = customerDAO.findByPersonId(account.getPersonId());
            if (customer == null || customer.getDeletedAt() != null) {
                return ServiceResult.fail(Messages.MSG07_NO_PERMISSION);
            }
            int customerId = customer.getCustomerId();

            // B3: lấy gói đã mua có Subscriptions.Status = Active mà khách đứng tên/đi cùng (DBR-54).
            // Không xét Packages.Status: ngừng bán danh mục không làm mất gói khách đã mua.
            // Đọc trạng thái Subscription đang lưu; không tự hết hạn gói hay chốt buổi thay job.
            LocalDateTime now = DateTimeUtil.now();
            List<MyPtPackageView> ptPackages = activeOnly
                    ? sessionDAO.findActivePtPackages(customerId, now)
                    : sessionDAO.findPtPackageOptions(customerId, now);

            // B4: điền dữ liệu hiển thị vào từng DTO do DAO tạo.
            for (MyPtPackageView ptPackage : ptPackages) {
                // B4.1: đổi loại gói thành nhãn; DAO chỉ lấy PTSingle và PTCouple.
                String subscriptionTypeLabel;
                if (ptPackage.getSubscriptionType() == Package.Type.PT_COUPLE) {
                    subscriptionTypeLabel = "PT đôi";
                } else {
                    subscriptionTypeLabel = "PT cá nhân";
                }
                ptPackage.setSubscriptionTypeLabel(subscriptionTypeLabel);

                // B4.2: dùng giới hạn lúc bán, không đọc P-08 hiện tại (GBR-04, BR-73).
                // Gói đôi dùng chung lượt đổi; chặn số âm khi hiển thị dữ liệu bất thường.
                int remainingSwaps = Math.max(0,
                        ptPackage.getMaxSwapsSnapshot() - ptPackage.getSwapsUsed());
                ptPackage.setRemainingSwaps(remainingSwaps);
            }

            // B5: không có gói vẫn là thành công; Servlet sẽ phân biệt rỗng với lỗi tải.
            return ServiceResult.ok(ptPackages);
        } catch (SQLException exception) {
            // Lỗi DB không biến thành danh sách rỗng khiến khách tưởng không có gói.
            exception.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }



    // =====================================================================
    // 2. LỊCH TẬP SẮP TỚI: Scheduled chưa kết thúc, không chốt trạng thái DB.
    // =====================================================================

    /**
     * Lấy buổi Scheduled chưa kết thúc của khách, gồm cá nhân, đôi và buổi lẻ.
     * @param accountId tài khoản từ session, không nhận customerId từ trình duyệt
     * @param filter bộ lọc đã hợp lệ từ parseFilter, hoặc none(); khối này chỉ dùng bộ lọc chung
     * @param now giờ VN Servlet lấy một lần, dùng chung với khối Lịch sử để không lệch nhóm
     * @return danh sách MyPtUpcomingSessionView do DAO tạo và Service bổ sung, hoặc lỗi
     */
    public ServiceResult<List<MyPtUpcomingSessionView>> getUpcomingSessions(int accountId,
            MyPtSessionFilter filter, LocalDateTime now) {
        try {
            // B1: kiểm tra tài khoản còn hoạt động và hồ sơ khách chưa bị xóa (GBR-10).
            Account account = accountDAO.findById(accountId);
            if (account == null || account.getStatus() != Account.Status.ACTIVE) {
                return ServiceResult.fail(Messages.MSG07_NO_PERMISSION);
            }
            Customer customer = customerDAO.findByPersonId(account.getPersonId());
            if (customer == null || customer.getDeletedAt() != null) {
                return ServiceResult.fail(Messages.MSG07_NO_PERMISSION);
            }
            int customerId = customer.getCustomerId();

            // B2: dùng now Servlet truyền vào; DAO và việc xét nhãn cùng dùng thời điểm này (GBR-01).
            // DAO chỉ ghép bộ lọc chung; trạng thái lịch sử không làm khối này rỗng.
            List<MyPtUpcomingSessionView> sessions =
                    sessionDAO.findUpcomingSessions(customerId, now.toLocalDate(), filter);
            List<MyPtUpcomingSessionView> upcomingSessions = new ArrayList<>();

            // B3: buổi đã kết thúc không thuộc khối sắp tới, dù DB vẫn còn Scheduled.
            // Không UPDATE trạng thái: JOB-04 xử lý riêng; lịch sử sẽ đọc buổi đã qua (GBR-14).
            for (MyPtUpcomingSessionView session : sessions) {
                LocalDateTime endsAt = session.getSlotDate().atTime(session.getEndTime());
                if (!endsAt.isAfter(now)) {
                    continue;
                }

                // B4: bổ sung nhãn thời gian và loại buổi, không in mã enum cho khách.
                applyUpcomingLabels(session, customerId, now);

                // B5: giữ buổi đã có nhãn và cảnh báo, không kết luận Vắng.
                upcomingSessions.add(session);
            }

            // B6: giữ thứ tự ngày giờ DAO đã sắp; rỗng khác với lỗi truy vấn.
            return ServiceResult.ok(upcomingSessions);
        } catch (SQLException exception) {
            exception.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /** Điền cùng nhãn/cảnh báo cho luồng cũ và luồng phân trang, chỉ trên những buổi DAO trả về. */
    private void applyUpcomingLabels(MyPtUpcomingSessionView session, int customerId,
            LocalDateTime now) throws SQLException {
        // B1: nhãn Hôm nay/Đang diễn ra dùng cùng thời điểm với điều kiện truy vấn.
        LocalDateTime startsAt = session.getSlotDate().atTime(session.getStartTime());
        session.setToday(session.getSlotDate().equals(now.toLocalDate()));
        session.setInProgress(!startsAt.isAfter(now)); // so giờ bắt đầu với giờ hiện tại để xem buổi tập đã diễn ra chưa
        String subscriptionTypeLabel = switch (session.getSubscriptionType()) {
            case PT_SINGLE -> "PT cá nhân";
            case PT_COUPLE -> "PT đôi";
            case SINGLE_SESSION -> "Buổi lẻ";
            default -> throw new SQLException("Loại buổi không hợp lệ, SessionId=" + session.getSessionId());
        };
        session.setSubscriptionTypeLabel(subscriptionTypeLabel);
        // B2: cảnh báo theo quyền vào gym như JOB-04; không tự chốt buổi.
        session.setWarning(buildGymAccessWarning(session, customerId));
    }

    /** Xét theo ngày buổi đúng JOB-04: có Membership hoặc DayPass và không trong kỳ bảo lưu. */
    private String buildGymAccessWarning(MyPtUpcomingSessionView session, int customerId) {
        // B1: cùng công thức AutoCloseSessionService.canUseGym, xét tại ngày buổi tập.
        boolean holderCanUseGym = (session.isHolderHasMembership() || session.isHolderHasDayPass())
                && !session.isHolderFrozen();
        boolean partnerCanUseGym = (session.isPartnerHasMembership() || session.isPartnerHasDayPass())
                && !session.isPartnerFrozen();
        boolean isCouple = session.getSubscriptionType() == Package.Type.PT_COUPLE;

        // B2: gói cá nhân và buổi lẻ xét khách đứng tên, không chặn xem lịch vì thiếu quyền vào gym.
        if (!isCouple) {
            if (holderCanUseGym) {
                return null;
            }
            if (session.isHolderFrozen()) {
                return "Buổi tập nằm trong kỳ bảo lưu. Hãy kiểm tra thời gian bảo lưu trước ngày tập.";
            }
            return "Bạn chưa có Membership hoặc DayPass hợp lệ cho ngày tập này.";
        }

        // B3: gói đôi phân biệt người xem để không nhầm tình trạng của mình với người tập cùng.
        boolean viewerIsHolder = session.getHolderCustomerId() == customerId;
        boolean viewerCanUseGym = viewerIsHolder ? holderCanUseGym : partnerCanUseGym;
        boolean otherCanUseGym = viewerIsHolder ? partnerCanUseGym : holderCanUseGym;
        boolean viewerFrozen = viewerIsHolder ? session.isHolderFrozen() : session.isPartnerFrozen();
        if (viewerCanUseGym && otherCanUseGym) {
            return null;
        }
        if (viewerCanUseGym) {
            return "Người tập cùng chưa có quyền vào gym hợp lệ cho ngày này. Bạn vẫn có thể tham gia buổi tập.";
        }
        if (otherCanUseGym) {
            if (viewerFrozen) {
                return "Bạn đang trong kỳ bảo lưu vào ngày tập. Người tập cùng vẫn có thể tham gia buổi tập.";
            }
            return "Bạn chưa có quyền vào gym cho ngày này. Người tập cùng vẫn có thể tham gia buổi tập.";
        }

        // B4: cả hai chưa đủ quyền; đây chỉ là cảnh báo theo dữ liệu hiện tại, không chốt kết quả buổi.
        if (session.isHolderFrozen() || session.isPartnerFrozen()) {
            return "Buổi tập trùng kỳ bảo lưu và cả hai chưa có quyền vào gym hợp lệ. Hãy kiểm tra Membership hoặc DayPass trước ngày tập.";
        }
        return "Cả hai chưa có Membership hoặc DayPass hợp lệ cho ngày tập này.";
    }



    // =====================================================================
    // 3. LỊCH SỬ BUỔI TẬP: đọc kết quả đã lưu, không tính lại, không đổi trạng thái DB.
    // =====================================================================

    /**
     * Lấy buổi đã có kết quả cuối cùng hoặc đã qua giờ kết thúc của khách, gồm cá nhân, đôi và buổi lẻ.
     * Buổi Scheduled đã kết thúc hiện là "Kết quả đang cập nhật" cho đến khi JOB-04 chốt (GBR-14).
     * @param accountId tài khoản từ session, không nhận customerId từ trình duyệt
     * @param filter bộ lọc đã hợp lệ từ parseFilter, hoặc none(); gồm bộ lọc chung và trạng thái lịch sử
     * @param now giờ VN Servlet lấy một lần, dùng chung với khối Sắp tới để không lệch nhóm
     * @return danh sách MyPtHistorySessionView do DAO tạo và Service bổ sung, hoặc lỗi
     */
    public ServiceResult<List<MyPtHistorySessionView>> getHistorySessions(int accountId,
            MyPtSessionFilter filter, LocalDateTime now) {
        try {
            // B1: kiểm tra tài khoản còn hoạt động và hồ sơ khách chưa bị xóa (GBR-10).
            Account account = accountDAO.findById(accountId);
            if (account == null || account.getStatus() != Account.Status.ACTIVE) {
                return ServiceResult.fail(Messages.MSG07_NO_PERMISSION);
            }
            Customer customer = customerDAO.findByPersonId(account.getPersonId());
            if (customer == null || customer.getDeletedAt() != null) {
                return ServiceResult.fail(Messages.MSG07_NO_PERMISSION);
            }
            int customerId = customer.getCustomerId();

            // B2: dùng cùng now với khối Sắp tới để mỗi buổi chỉ thuộc một khối (GBR-01).
            // DAO ghép bộ lọc chung và trạng thái lịch sử bằng AND.
            List<MyPtHistorySessionView> sessions =
                    sessionDAO.findHistorySessions(customerId, now, filter);

            // B3: bổ sung nhãn, giải thích, hint cho từng dòng; không UPDATE trạng thái.
            for (MyPtHistorySessionView session : sessions) {
                applyHistoryLabels(session);
            }

            // B4: giữ thứ tự mới nhất trước từ DAO; rỗng khác với lỗi truy vấn (GBR-21).
            return ServiceResult.ok(sessions);
        } catch (SQLException exception) {
            exception.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /** Điền nhãn hiển thị theo kết quả đã lưu; tách riêng để kiểm thử không cần DB. */
    static void applyHistoryLabels(MyPtHistorySessionView session) throws SQLException {
        // B1: đổi loại buổi thành nhãn, không in mã enum cho khách.
        boolean isCouple = session.getSubscriptionType() == Package.Type.PT_COUPLE;
        String subscriptionTypeLabel = switch (session.getSubscriptionType()) {
            case PT_SINGLE -> "PT cá nhân";
            case PT_COUPLE -> "PT đôi";
            case SINGLE_SESSION -> "Buổi lẻ";
            default -> throw new SQLException("Loại buổi không hợp lệ, SessionId=" + session.getSessionId());
        };
        session.setSubscriptionTypeLabel(subscriptionTypeLabel);

        // B2: chọn badge và lời giải thích theo Sessions.Status; DAO chỉ trả 4 trạng thái này.
        if (session.getStatus() == null) {
            throw new SQLException("Trạng thái buổi không hợp lệ, SessionId=" + session.getSessionId());
        }
        switch (session.getStatus()) {
            case SCHEDULED -> {
                // Đã qua giờ kết thúc nhưng JOB-04 chưa chốt: chưa được nói Vắng hay Đã check-in.
                session.setStatusLabel("Kết quả đang cập nhật");
                session.setStatusBadgeClass("badge-info");
                session.setExplanation("Buổi đã kết thúc. Hệ thống sẽ chốt kết quả sau khi ngày tập kết thúc.");
            }
            case CHECKED_IN -> {
                // Chỉ là check-in vào gym trong ngày, không xác nhận PT đã hoàn thành.
                session.setStatusLabel("Đã check-in");
                session.setStatusBadgeClass("badge-success");
                session.setHint(isCouple
                        ? "Bạn hoặc người tập cùng đã check-in vào gym trong ngày có lịch PT. "
                                + "Trạng thái này không xác nhận buổi PT đã hoàn thành."
                        : "Bạn đã check-in vào gym trong ngày có lịch PT. "
                                + "Trạng thái này không xác nhận buổi PT đã hoàn thành.");
            }
            case MISSED -> {
                session.setStatusLabel("Vắng");
                session.setStatusBadgeClass("badge-danger");
                session.setExplanation(buildMissedExplanation(session.getMissedReason(), isCouple));
            }
            case CANCELLED -> {
                session.setStatusLabel("Đã hủy");
                session.setStatusBadgeClass("badge-neutral");
                session.setExplanation(buildCancelExplanation(session.getCancelType(), isCouple));
            }
        }
    }

    /** Diễn giải MissedReason đã lưu; gói đôi nói "cả hai" vì job chỉ chốt Vắng khi cả hai không đủ điều kiện. */
    private static String buildMissedExplanation(Session.MissedReason reason, boolean isCouple) {
        if (reason == null) {
            return "Buổi được ghi nhận là vắng.";
        }
        return switch (reason) {
            case NO_CHECK_IN -> isCouple
                    ? "Cả hai không check-in vào gym trong ngày tập."
                    : "Bạn không check-in vào gym trong ngày tập.";
            case NO_MEMBERSHIP -> isCouple
                    ? "Cả hai chưa có Membership hoặc DayPass hợp lệ cho ngày tập."
                    : "Bạn chưa có Membership hoặc DayPass hợp lệ cho ngày tập.";
            case MEMBERSHIP_FROZEN -> isCouple
                    ? "Ngày tập nằm trong kỳ bảo lưu và cả hai chưa có quyền vào gym hợp lệ."
                    : "Ngày tập nằm trong kỳ bảo lưu Membership của bạn.";
        };
    }

    /** Diễn giải CancelType đã lưu; BR-79: khách tự hủy vẫn tiêu buổi, CBR-55: gói hết hạn vẫn giữ lịch sử. */
    private static String buildCancelExplanation(Session.CancelType cancelType, boolean isCouple) {
        if (cancelType == null) {
            return "Buổi đã bị hủy.";
        }
        return switch (cancelType) {
            case BY_PT -> "PT đã hủy buổi này.";
            case BY_CUSTOMER -> isCouple
                    ? "Buổi được hủy theo yêu cầu của một thành viên. Gói vẫn bị trừ một buổi và không hoàn tiền."
                    : "Buổi được hủy theo yêu cầu của bạn. Gói vẫn bị trừ một buổi và không hoàn tiền.";
            case GYM_CLOSED -> "Phòng tập đóng cửa vào ngày này nên buổi bị hủy.";
            case PACKAGE_EXPIRED -> "Gói PT đã hết hạn nên buổi chưa tập bị hủy.";
        };
    }



    // =====================================================================
    // 4. GOM THEO NGÀY: để JSP hiển thị đầy đủ các buổi trong từng nhóm ngày.
    // =====================================================================

    /**
     * Gom danh sách đã sắp theo ngày thành Map ngày → buổi, giữ nguyên thứ tự DAO trả về.
     * Chỉ đổi cách trình bày, không lọc hay sắp lại dữ liệu.
     * @param sessions danh sách buổi đã sắp theo ngày (tăng với sắp tới, giảm với lịch sử)
     * @param slotDateOf hàm lấy ngày tập của một buổi
     * @return LinkedHashMap giữ thứ tự ngày xuất hiện; rỗng nếu danh sách rỗng
     */
    public static <T> Map<LocalDate, List<T>> groupBySlotDate(List<T> sessions,
            Function<T, LocalDate> slotDateOf) {
        Map<LocalDate, List<T>> sessionsByDate = new LinkedHashMap<>();
        for (T session : sessions) {
            sessionsByDate.computeIfAbsent(slotDateOf.apply(session), date -> new ArrayList<>())
                    .add(session);
        }
        return sessionsByDate;
    }



    // =====================================================================
    // 5. BỘ LỌC: đổi tham số URL thô thành MyPtSessionFilter đã hợp lệ.
    // =====================================================================

    /**
     * Kiểm tra tham số lọc người dùng gửi lên; không đọc DB.
     * Ô trống là không lọc theo ô đó. Có ô sai thì trả lỗi theo từng ô và không trả filter nào,
     * để Servlet dùng MyPtSessionFilter.none() thay vì áp dụng một phần.
     * @param type tham số "type": PTSingle, PTCouple, SingleSession hoặc trống
     * @param dateFrom tham số "dateFrom" dạng yyyy-MM-dd từ input type="date", hoặc trống
     * @param dateTo tham số "dateTo" dạng yyyy-MM-dd, hoặc trống
     * @param historyStatus tham số "historyStatus": pending, checkedIn, missed, cancelled hoặc trống
     * @return ok(filter) khi mọi ô hợp lệ; failFields(tên ô → lỗi) khi có ô sai
     */
    public ServiceResult<MyPtSessionFilter> parseFilter(String type, String dateFrom, String dateTo,
            String historyStatus) {
        return parseFilter(type, dateFrom, dateTo, historyStatus, null, null);
    }

    /** Khi đã tải lựa chọn, từ chối mã không thuộc danh sách gói khách được xem. */
    public ServiceResult<MyPtSessionFilter> parseFilter(String type, String dateFrom, String dateTo,
            String historyStatus, String subscriptionIdValue, List<MyPtPackageView> packageOptions) {
        // LinkedHashMap giữ thứ tự ô trên form khi hiện lỗi.
        Map<String, String> fieldErrors = new LinkedHashMap<>();

        // B1: loại buổi chỉ nhận 3 loại của màn này, không nhận Membership/DayPass.
        Package.Type subscriptionType = null;
        if (!ValidationUtil.isNullOrEmpty(type)) {
            subscriptionType = Package.Type.fromDbValue(type.trim());
            boolean isSessionType = subscriptionType == Package.Type.PT_SINGLE
                    || subscriptionType == Package.Type.PT_COUPLE
                    || subscriptionType == Package.Type.SINGLE_SESSION;
            if (!isSessionType) {
                fieldErrors.put("type", Messages.MSG108_INVALID_OPTION);
            }
        }

        // B2: ngày từ input type="date"; parseHtmlDate trả null khi sai nên phải tách với ô trống.
        LocalDate fromDate = DateTimeUtil.parseHtmlDate(dateFrom);
        if (!ValidationUtil.isNullOrEmpty(dateFrom) && fromDate == null) {
            fieldErrors.put("dateFrom", Messages.MSG109_INVALID_DATE);
        }
        LocalDate toDate = DateTimeUtil.parseHtmlDate(dateTo);
        if (!ValidationUtil.isNullOrEmpty(dateTo) && toDate == null) {
            fieldErrors.put("dateTo", Messages.MSG109_INVALID_DATE);
        }

        // B3: chỉ so khoảng ngày khi cả hai ngày đều đọc được (DBR-13); báo lỗi tại ô Đến ngày.
        if (fromDate != null && toDate != null && !ValidationUtil.isValidDateRange(fromDate, toDate)) {
            fieldErrors.put("dateTo", Messages.MSG16_DATE_RANGE_REVERSED);
        }

        // B4: trạng thái riêng của khối Lịch sử; trống là Tất cả.
        MyPtSessionFilter.HistoryStatus status = null;
        if (!ValidationUtil.isNullOrEmpty(historyStatus)) {
            status = MyPtSessionFilter.HistoryStatus.fromUrlValue(historyStatus.trim());
            if (status == null) {
                // nếu nhập url = abc (null) => báo lỗi, nếu rỗng (null) không báo lỗi => lấy tất cả, nếu status = MISSED => chỉ lấy buổi vắng 
                fieldErrors.put("historyStatus", Messages.MSG108_INVALID_OPTION);
            }
        }

        // B5: mã gói chỉ nhận số nguyên dương, không ghép chuỗi người dùng vào SQL.
        Integer subscriptionId = null;
        if (!ValidationUtil.isNullOrEmpty(subscriptionIdValue)) {
            try {
                subscriptionId = Integer.valueOf(subscriptionIdValue.trim());
                if (subscriptionId <= 0) {
                    fieldErrors.put("subscriptionId", Messages.MSG108_INVALID_OPTION);
                } else if (packageOptions != null) {
                    boolean isOwnedPackage = false;
                    for (MyPtPackageView option : packageOptions) {
                        if (option.getSubscriptionId() == subscriptionId) {
                            isOwnedPackage = true;
                            break;
                        }
                    }
                    if (!isOwnedPackage) {
                        fieldErrors.put("subscriptionId", Messages.MSG108_INVALID_OPTION);
                    }
                }
            } catch (NumberFormatException exception) {
                fieldErrors.put("subscriptionId", Messages.MSG108_INVALID_OPTION);
            }
        }

        // B6: có lỗi thì không tạo filter, tránh áp dụng một phần điều kiện.
        if (!fieldErrors.isEmpty()) {
            return ServiceResult.failFields(fieldErrors);
        }
        return ServiceResult.ok(new MyPtSessionFilter(subscriptionType, fromDate, toDate, status,
                subscriptionId));
    }

    // =====================================================================
    // 6. PHÂN TRANG THEO NGÀY: đếm → chuẩn hóa trang → lấy buổi → tạo DTO trang.
    // =====================================================================

    /**
     * Chuẩn bị một trang lịch sắp tới: DAO tạo DTO buổi, Service bổ sung nhãn và tạo DTO trang.
     * Không gọi getUpcomingSessions để lấy toàn bộ lịch rồi cắt trong Java.
     * @param accountId mã tài khoản từ session; Service xác định customerId và quyền xem (GBR-10)
     * @param filter bộ lọc đã hợp lệ, chỉ phần chung áp dụng cho khối này
     * @param now giờ VN Servlet lấy một lần, dùng chung khi đếm/lấy trang và xét nhãn
     * @param requestedPage trang yêu cầu, thông thường đã qua parsePageNumber; vẫn chặn hai biên
     * @return trang đã chuẩn hóa và nhóm ngày, hoặc lỗi; không có buổi là trang 1/1 với map rỗng
     */
    public ServiceResult<MyPtSessionPage<MyPtUpcomingSessionView>> getUpcomingSessionsPage(
            int accountId, MyPtSessionFilter filter, LocalDateTime now, int requestedPage) {
        try {
            // B1: kiểm tra tài khoản/hồ sơ như luồng cũ; không nhận customerId từ URL.
            Account account = accountDAO.findById(accountId);
            if (account == null || account.getStatus() != Account.Status.ACTIVE) {
                return ServiceResult.fail(Messages.MSG07_NO_PERMISSION);
            }
            Customer customer = customerDAO.findByPersonId(account.getPersonId());
            if (customer == null || customer.getDeletedAt() != null) {
                return ServiceResult.fail(Messages.MSG07_NO_PERMISSION);
            }
            int customerId = customer.getCustomerId();

            // B2: đếm ngày SAU lọc, tính tổng trang làm tròn lên; rỗng vẫn có trang 1/1.
            // Công thức tránh cộng 7 vào totalDays để không tràn int khi dữ liệu lớn.
            int totalDays = sessionDAO.countUpcomingSessionDays(customerId, now, filter);
            int totalPages = totalDays == 0 ? 1 : 1 + (totalDays - 1) / DAYS_PER_PAGE;
            int currentPage = Math.max(1, Math.min(requestedPage, totalPages));
            int dayOffset = (currentPage - 1) * DAYS_PER_PAGE;

            // B3: chỉ lấy buổi của các ngày trong trang; không có ngày thì bỏ truy vấn lấy buổi.
            List<MyPtUpcomingSessionView> sessions = totalDays == 0
                    ? new ArrayList<>()
                    : sessionDAO.findUpcomingSessionsPage(customerId, now, filter, dayOffset, DAYS_PER_PAGE);

            // B4: dùng lại nhãn và cảnh báo đang có; SQL đã loại Scheduled kết thúc <= now.
            for (MyPtUpcomingSessionView session : sessions) {
                applyUpcomingLabels(session, customerId, now);
            }

            // B5: gom đúng dữ liệu trang thành nhóm ngày; không cắt hoặc đổi thứ tự thêm trong Java.
            Map<LocalDate, List<MyPtUpcomingSessionView>> days =
                    groupBySlotDate(sessions, MyPtUpcomingSessionView::getSlotDate);
            return ServiceResult.ok(new MyPtSessionPage<>(days, currentPage, totalDays, totalPages));
        } catch (SQLException exception) {
            // Lỗi truy vấn không biến thành trang rỗng (GBR-21).
            exception.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /**
     * Chuẩn bị một trang lịch sử, dùng bộ lọc chung và trạng thái lịch sử đã hợp lệ.
     * @param accountId mã tài khoản đăng nhập, không phải mã khách từ URL
     * @param filter bộ lọc dùng giống nhau khi đếm và lấy buổi của trang
     * @param now cùng giờ VN với khối sắp tới, không tự lấy giờ lại giữa hai truy vấn
     * @param requestedPage số trang yêu cầu, được đưa về khoảng 1..totalPages
     * @return DTO trang lịch sử, ngày mới nhất trước; rỗng là trang 1/1, lỗi trả fail
     */
    public ServiceResult<MyPtSessionPage<MyPtHistorySessionView>> getHistorySessionsPage(
            int accountId, MyPtSessionFilter filter, LocalDateTime now, int requestedPage) {
        try {
            // B1: xác định đúng hồ sơ khách; membership hết hạn/bảo lưu không chặn xem lịch sử.
            Account account = accountDAO.findById(accountId);
            if (account == null || account.getStatus() != Account.Status.ACTIVE) {
                return ServiceResult.fail(Messages.MSG07_NO_PERMISSION);
            }
            Customer customer = customerDAO.findByPersonId(account.getPersonId());
            if (customer == null || customer.getDeletedAt() != null) {
                return ServiceResult.fail(Messages.MSG07_NO_PERMISSION);
            }
            int customerId = customer.getCustomerId();

            // B2: đếm sau cả hai bộ lọc rồi giới hạn số trang; không dùng tổng của khối sắp tới.
            int totalDays = sessionDAO.countHistorySessionDays(customerId, now, filter);
            int totalPages = totalDays == 0 ? 1 : 1 + (totalDays - 1) / DAYS_PER_PAGE;
            int currentPage = Math.max(1, Math.min(requestedPage, totalPages));
            int dayOffset = (currentPage - 1) * DAYS_PER_PAGE;

            // B3: SQL chọn tối đa 8 ngày, lấy đủ buổi khớp bộ lọc trong những ngày đó.
            List<MyPtHistorySessionView> sessions = totalDays == 0
                    ? new ArrayList<>()
                    : sessionDAO.findHistorySessionsPage(customerId, now, filter, dayOffset, DAYS_PER_PAGE);

            // B4: dùng lại cách diễn giải kết quả đã lưu, không chốt trạng thái thay JOB-04.
            for (MyPtHistorySessionView session : sessions) {
                applyHistoryLabels(session);
            }

            // B5: Service tạo DTO trang; Servlet/JSP sẽ nhận DTO này ở bước nối tiếp theo.
            Map<LocalDate, List<MyPtHistorySessionView>> days =
                    groupBySlotDate(sessions, MyPtHistorySessionView::getSlotDate);
            return ServiceResult.ok(new MyPtSessionPage<>(days, currentPage, totalDays, totalPages));
        } catch (SQLException exception) {
            exception.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /**
     * Đổi số trang thô từ URL thành số nguyên dương, dùng riêng cho từng khối.
     * Chỉ đọc số trang; getUpcomingSessionsPage/getHistorySessionsPage giới hạn theo tổng trang sau lọc.
     *
     * @param pageValue chuỗi upcomingPage hoặc historyPage do Servlet đọc; có thể null/trống
     * @return số trang từ 1; thiếu, sai định dạng, vượt kiểu int hoặc nhỏ hơn 1 thì trả 1
     */
    public int parsePageNumber(String pageValue) {
        // B1: URL không có số trang hoặc ô trống thì xem trang đầu tiên.
        if (ValidationUtil.isNullOrEmpty(pageValue)) {
            return 1;
        }

        // B2: chỉ nhận số nguyên dương; URL nhập sai không gây lỗi 500.
        try {
            int pageNumber = Integer.parseInt(pageValue.trim());
            if (pageNumber < 1) {
                return 1;
            }
            return pageNumber;
        } catch (NumberFormatException exception) {
            return 1;
        }
    }

    /**
     * Hủy buổi tập PT do khách đăng nhập yêu cầu (BR-79: khách tự hủy vẫn tiêu buổi, không hoàn).
     * <p>Quy trình:
     * <ol>
     *   <li>Kiểm tra tài khoản còn hoạt động, khách có hồ sơ (GBR-10).</li>
     *   <li>Đọc buổi + slot + gói qua DAO; phân biệt 404 (không thấy) với 403 (không phải của tôi).</li>
     *   <li>Chỉ cho hủy khi Status = Scheduled và buổi chưa kết thúc (MSG91).</li>
     *   <li>Gói PT phải còn Active; nếu Expired/Cancelled thì JOB-02 xử lý (MSG105).</li>
     *   <li>Gọi DAO UPDATE; nếu không update được 1 dòng → có người vừa đổi → MSG22.</li>
     * </ol>
     * Mọi thông báo lỗi dùng Messages, không để lộ chi tiết kỹ thuật.
     *
     * @param accountId tài khoản từ session, dùng để tìm customerId
     * @param sessionId buổi cần hủy
     * @return ServiceResult thành công (data = null) hoặc thất bại có message
     */
    public ServiceResult<Void> cancelPtSession(int accountId, int sessionId) {
        try {
            // B1: xác thực tài khoản + tìm customerId.
            Account account = accountDAO.findById(accountId);
            if (account == null || account.getStatus() != Account.Status.ACTIVE) {
                return ServiceResult.fail(Messages.MSG07_NO_PERMISSION);
            }
            Customer customer = customerDAO.findByPersonId(account.getPersonId());
            if (customer == null || customer.getDeletedAt() != null) {
                return ServiceResult.fail(Messages.MSG07_NO_PERMISSION);
            }
            int customerId = customer.getCustomerId();

            // B2: đọc buổi + slot + gói để xác định quyền, thời điểm, trạng thái.
            PtSessionCancelInfo info = sessionDAO.findSessionForCancel(sessionId);
            if (info == null) {
                // Không lộ sự tồn tại của buổi lạ: cùng message với "buổi không phải của tôi".
                return ServiceResult.fail(Messages.MSG07_NO_PERMISSION);
            }
            // B2.1: buổi thuộc khách đăng nhập?
            //    Gói đôi: người đứng tên hoặc người đi cùng đều được hủy buổi của mình.
            //    Gói cá nhân / buổi lẻ: chỉ người đứng tên.
            boolean isHolder = info.getCustomerId() == customerId;
            boolean isPartner = info.getPartnerCustomerId() != null
                    && info.getPartnerCustomerId() == customerId;
            if (!isHolder && !isPartner) {
                return ServiceResult.fail(Messages.MSG07_NO_PERMISSION);
            }

            // B3: chỉ cho hủy khi buổi đang Scheduled. CheckedIn/Missed/Cancelled thì báo lỗi chung.
            if (!"Scheduled".equals(info.getSessionStatus())) {
                return ServiceResult.fail(Messages.MSG91_SESSION_CANNOT_CHANGE);
            }

            // B4: gói phải còn Active. Nếu Expired/Cancelled thì không cho khách hủy;
            //     buổi sẽ do JOB-02 chốt với CancelType = PackageExpired (CBR-55).
            if (!"Active".equals(info.getSubscriptionStatus())) {
                return ServiceResult.fail(Messages.MSG105_ACTION_OUTSIDE_WINDOW);
            }

            // B5: khách được hủy trước khi buổi kết thúc, kể cả khi buổi đang diễn ra.
            LocalDateTime endsAt = info.getSlotDate().atTime(info.getEndTime());
            LocalDateTime now = DateTimeUtil.now(); // Quan Trọng: lấy giờ thực tế, từ chối trường hợp biên
            if (!now.isBefore(endsAt)) {
                return ServiceResult.fail(Messages.MSG91_SESSION_CANNOT_CHANGE);
            }

            // B6: UPDATE buổi với điều kiện Status = 'Scheduled' (chống double-submit).
            boolean updated = sessionDAO.cancelSessionByCustomer(sessionId, accountId);
            if (!updated) {
                // Có người vừa đổi trạng thái giữa lúc ta đọc và ghi.
                return ServiceResult.fail(Messages.MSG22_CONCURRENT_EDIT);
            }
            return ServiceResult.ok(null, Messages.MSG110_SESSION_CANCELLED);
        } catch (SQLException exception) {
            exception.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }
}
