package com.gms.service;

import com.gms.constant.Role;
import com.gms.dao.OrderDAO;
import com.gms.dao.PackageDAO;
import com.gms.dao.PaymentDAO;
import com.gms.dao.SubscriptionDAO;
import com.gms.dto.PaymentCustomerView;
import com.gms.dto.ServiceResult;
import com.gms.dto.StartDatePicker;
import com.gms.entity.Order;
import com.gms.entity.Package;
import com.gms.entity.Subscription;
import com.gms.util.DBContext;
import com.gms.util.DateTimeUtil;
import com.gms.util.PackagePeriod;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

/**
 * Khách tự chọn ngày bắt đầu của gói Membership sau khi mua.
 *
 * <p>
 * Chỉ đổi bảng Subscriptions, không thêm cột. Gói được cấp với ngày mặc định theo DBR-52; khách được dời
 * ngày bắt đầu về sau một lần, trong hôm nay đến hôm nay + 7 ngày, miễn là gói chưa dùng. Ngày hết hạn luôn do
 * server tính lại theo thời hạn gói.</p>
 */
public class SubscriptionStartDateService {

    private final PaymentDAO paymentDAO;
    private final SubscriptionDAO subscriptionDAO;
    private final PackageDAO packageDAO;
    private final OrderDAO orderDAO;
    private final AuditLogService auditService;

    public SubscriptionStartDateService() {
        this(new PaymentDAO(), new SubscriptionDAO(), new PackageDAO(), new OrderDAO(), new AuditLogService());
    }

    /** Test dùng constructor này để thay DAO giả. */
    public SubscriptionStartDateService(PaymentDAO paymentDAO, SubscriptionDAO subscriptionDAO,
            PackageDAO packageDAO, OrderDAO orderDAO, AuditLogService auditService) {
        this.paymentDAO = paymentDAO;
        this.subscriptionDAO = subscriptionDAO;
        this.packageDAO = packageDAO;
        this.orderDAO = orderDAO;
        this.auditService = auditService;
    }

    /**
     * Giao diện chọn ngày cho một gói của chính khách đang đăng nhập (dùng ở Dashboard).
     *
     * @return picker; data null khi gói không phải Membership đang Active hoặc không thuộc khách
     */
    public ServiceResult<StartDatePicker> getPicker(int accountId, Integer subscriptionId) {
        if (accountId <= 0 || subscriptionId == null || subscriptionId <= 0) {
            return ServiceResult.fail("Mã gói không hợp lệ.");
        }
        try (Connection conn = openConnection()) {
            PaymentCustomerView customer = findCustomer(conn, accountId);
            Subscription subscription = subscriptionDAO.findById(conn, subscriptionId);
            if (customer == null || subscription == null || subscription.getCustomerId() != customer.getCustomerId()) {
                return ServiceResult.ok(null);
            }
            return ServiceResult.ok(buildPicker(conn, subscription, DateTimeUtil.today()));
        } catch (SQLException | RuntimeException e) {
            return ServiceResult.fail("Chưa đọc được thông tin gói. Vui lòng thử lại.");
        }
    }

    /**
     * Như getPicker nhưng dùng customerId đã biết, không khóa hồ sơ khách. Dashboard chỉ đọc nên không được
     * giữ khóa chung với luồng thanh toán.
     *
     * @return picker, hoặc null khi gói không thuộc khách hoặc không phải Membership đang Active
     */
    public ServiceResult<StartDatePicker> getPickerForCustomer(int customerId, Integer subscriptionId) {
        if (customerId <= 0 || subscriptionId == null || subscriptionId <= 0) {
            return ServiceResult.fail("Mã gói không hợp lệ.");
        }
        try (Connection conn = openConnection()) {
            Subscription subscription = subscriptionDAO.findById(conn, subscriptionId);
            if (subscription == null || subscription.getCustomerId() != customerId) {
                return ServiceResult.ok(null);
            }
            return ServiceResult.ok(buildPicker(conn, subscription, DateTimeUtil.today()));
        } catch (SQLException | RuntimeException e) {
            return ServiceResult.fail("Chưa đọc được thông tin gói. Vui lòng thử lại.");
        }
    }

    /**
     * Giao diện chọn ngày cho đơn vừa thanh toán (trang kết quả thanh toán). Chỉ gọi khi đơn là của khách
     * đang đăng nhập và đã Paid; nhận connection của người gọi để cùng đọc một lần.
     *
     * @return picker, hoặc null khi đơn không cấp gói Membership
     */
    public StartDatePicker pickerForPaidOrder(Connection conn, Order order) throws SQLException {
        if (order == null || !order.isPaid()) {
            return null;
        }
        Subscription subscription = subscriptionDAO.findByOrderId(conn, order.getOrderId());
        if (subscription == null || subscription.getCustomerId() != order.getHolderCustomerId()) {
            return null;
        }
        return buildPicker(conn, subscription, DateTimeUtil.today());
    }

    /**
     * Khách chọn ngày bắt đầu mới. Một transaction: khóa khách, khóa gói, kiểm luật, ghi ngày, ghi AuditLog.
     *
     * @return picker mới để JavaScript cập nhật ô ngày
     */
    public ServiceResult<StartDatePicker> changeStartDate(int accountId, Integer subscriptionId, LocalDate chosen) {
        if (accountId <= 0 || subscriptionId == null || subscriptionId <= 0) {
            return ServiceResult.fail("Mã gói không hợp lệ.");
        }
        if (chosen == null) {
            return ServiceResult.failField("startDate", "Vui lòng chọn ngày bắt đầu.");
        }
        try (Connection conn = openConnection()) {
            conn.setAutoCommit(false);
            try {
                // B1: khóa theo thứ tự khách -> gói, cùng thứ tự với luồng thanh toán.
                PaymentCustomerView customer = findCustomer(conn, accountId);
                if (customer == null) {
                    conn.rollback();
                    return ServiceResult.fail("Hồ sơ không còn hiệu lực.");
                }
                Subscription subscription = subscriptionDAO.findForUpdate(conn, subscriptionId);
                if (subscription == null || subscription.getCustomerId() != customer.getCustomerId()
                        || subscription.getSubscriptionType() != Subscription.Type.MEMBERSHIP
                        || !subscription.isActive()) {
                    conn.rollback();
                    return ServiceResult.fail("Không tìm thấy gói tập của bạn.");
                }

                // B2: kiểm luật trên dữ liệu vừa khóa, không tin dữ liệu trình duyệt.
                LocalDate today = DateTimeUtil.today();
                StartDatePicker current = buildPicker(conn, subscription, today);
                if (current.getLockReason() != null) {
                    conn.rollback();
                    return ServiceResult.fail(current.getLockReason());
                }
                if (chosen.equals(subscription.getStartDate())) {
                    conn.rollback();
                    return ServiceResult.ok(current, "Ngày bắt đầu không thay đổi.");
                }
                LocalDate otherEnd = subscriptionDAO.findLastMembershipEndExcluding(
                        conn, subscription.getCustomerId(), today, subscription.getSubscriptionId());
                List<LocalDate> options = PackagePeriod.optionsForPurchased(
                        today, subscription.getStartDate(), otherEnd);
                if (!options.contains(chosen)) {
                    conn.rollback();
                    return ServiceResult.failField("startDate",
                            "Chỉ được chọn ngày bắt đầu từ " + DateTimeUtil.formatDate(options.get(0))
                            + " đến " + DateTimeUtil.formatDate(PackagePeriod.latestStart(today)) + ".");
                }

                // B3: ngày hết hạn tính lại theo thời hạn gói đã bán.
                Order order = orderDAO.findForCallback(conn, subscription.getOrderId(), false);
                Package pkg = order == null ? null : packageDAO.findById(conn, order.getPackageId());
                if (pkg == null || pkg.getDurationMonths() <= 0) {
                    throw new SQLException("Không đọc được thời hạn gói");
                }
                LocalDate newEnd = PackagePeriod.endDate(chosen, pkg.getDurationMonths());

                // B4: UPDATE có điều kiện trong WHERE; khác 1 dòng thì hủy toàn bộ.
                if (subscriptionDAO.updateStartDate(conn, subscription.getSubscriptionId(),
                        subscription.getStartDate(), chosen, newEnd) != 1) {
                    conn.rollback();
                    return ServiceResult.fail("Gói vừa thay đổi, vui lòng tải lại trang và thử lại.");
                }
                auditService.logInTransaction(conn, accountId, Role.CUSTOMER, SubscriptionDAO.ACTION_CHANGE_START,
                        "Subscriptions", subscription.getSubscriptionId(), "StartDate",
                        subscription.getStartDate().toString(), chosen.toString(),
                        "Hết hạn mới " + newEnd);
                conn.commit();

                subscription.setStartDate(chosen);
                subscription.setEndDate(newEnd);
                return ServiceResult.ok(buildPicker(conn, subscription, today), "Đã lưu ngày bắt đầu.");
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException | RuntimeException e) {
            return ServiceResult.fail("Chưa lưu được ngày bắt đầu. Vui lòng thử lại.");
        }
    }

    /** Dựng picker từ trạng thái hiện tại của gói; null nếu không phải Membership đang Active. */
    private StartDatePicker buildPicker(Connection conn, Subscription subscription, LocalDate today)
            throws SQLException {
        if (subscription.getSubscriptionType() != Subscription.Type.MEMBERSHIP || !subscription.isActive()) {
            return null;
        }
        int id = subscription.getSubscriptionId();
        String reason = PackagePeriod.lockReason(
                today,
                subscription.getStartDate(),
                subscriptionDAO.hasCheckIn(conn, id),
                subscriptionDAO.countStartDateChanges(conn, id),
                subscriptionDAO.existsLaterMembership(
                        conn, subscription.getCustomerId(), id, subscription.getStartDate()));
        List<LocalDate> options = List.of();
        if (reason == null) {
            LocalDate otherEnd = subscriptionDAO.findLastMembershipEndExcluding(
                    conn, subscription.getCustomerId(), today, id);
            options = PackagePeriod.optionsForPurchased(today, subscription.getStartDate(), otherEnd);
            if (options.size() <= 1) {
                // Gói này nối sau gói khác còn hạn lâu hơn 7 ngày: không còn ngày nào khác để chọn.
                reason = "Gói này tự nối sau gói đang dùng nên không đổi được ngày bắt đầu.";
                options = List.of();
            }
        }
        return new StartDatePicker(id, subscription.getStartDate(), subscription.getEndDate(), today, options, reason);
    }

    private PaymentCustomerView findCustomer(Connection conn, int accountId) throws SQLException {
        return paymentDAO.findCustomerForPurchase(conn, accountId);
    }

    /** Test ghi đè để không cần SQL Server. */
    protected Connection openConnection() throws SQLException {
        return DBContext.getConnection();
    }
}
