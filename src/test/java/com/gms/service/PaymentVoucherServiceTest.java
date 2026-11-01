package com.gms.service;

import com.gms.constant.Role;
import com.gms.dao.OrderDAO;
import com.gms.dao.PackageDAO;
import com.gms.dao.PaymentDAO;
import com.gms.dao.VoucherDAO;
import com.gms.dto.PaymentCustomerView;
import com.gms.dto.ServiceResult;
import com.gms.entity.Order;
import com.gms.entity.Package;
import com.gms.entity.Voucher;
import com.gms.util.DateTimeUtil;
import com.gms.util.MoneyUtil;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Test Service SEQ-2 bằng DAO giả; khóa/lượt dùng SQL Server thật cần kiểm thử local. */
class PaymentVoucherServiceTest {

    @Test
    void appliesNormalizedCodeUsingSnapshotAndRecalculatesInclusiveVat() {
        Fixture f = new Fixture();
        assertTrue(f.service.applyVoucher(7, 80, " welcome10 ").isSuccess());
        assertEquals("WELCOME10", f.receivedCode);
        assertEquals(9, f.order.getVoucherId());
        assertEquals(219000L, f.order.getDiscountAmount());
        assertEquals(1971000L, f.order.getFinalAmount());
        assertEquals(MoneyUtil.calculateVatAmount(1971000L, 10), f.order.getVatAmount());
        assertEquals(2190000L, f.order.getUnitPriceSnapshot());
        assertEquals(List.of("reserve:9", "update", "audit", "commit"), f.events);
    }

    @Test
    void amountDiscountIsCappedAtOriginalPrice() {
        Fixture f = new Fixture();
        f.voucher.setDiscountType(Voucher.DiscountType.AMOUNT);
        f.voucher.setDiscountValue(3000000);
        assertTrue(f.service.applyVoucher(7, 80, "WELCOME10").isSuccess());
        assertEquals(0, f.order.getFinalAmount());
        assertEquals(0, f.order.getVatAmount());
    }

    @Test
    void percentUsesSharedRoundingToWholeDong() {
        Fixture f = new Fixture();
        f.order.setUnitPriceSnapshot(105);
        f.voucher.setDiscountValue(10);
        assertTrue(f.service.applyVoucher(7, 80, "WELCOME10").isSuccess());
        assertEquals(11, f.order.getDiscountAmount());
    }

    @Test
    void sameCodeIsIdempotentEvenWhenNoUsesRemain() {
        Fixture f = new Fixture();
        f.order.setVoucherId(9);
        f.order.setDiscountAmount(219000);
        f.voucher.setUsedCount(f.voucher.getMaxUsage());
        assertTrue(f.service.applyVoucher(7, 80, "WELCOME10").isSuccess());
        assertEquals(List.of("commit"), f.events);
        assertEquals(219000L, f.order.getDiscountAmount());
    }

    @Test
    void replacementReturnsOldUseOnce() {
        Fixture f = new Fixture();
        f.order.setVoucherId(8);
        assertTrue(f.service.applyVoucher(7, 80, "WELCOME10").isSuccess());
        assertEquals(List.of("reserve:9", "return:8", "update", "audit", "commit"), f.events);
    }

    @Test
    void removeReturnsUseAndRestoresOriginalVat() {
        Fixture f = new Fixture();
        f.order.setVoucherId(8);
        f.order.setDiscountAmount(219000);
        assertTrue(f.service.removeVoucher(7, 80).isSuccess());
        assertNull(f.order.getVoucherId());
        assertEquals(0, f.order.getDiscountAmount());
        assertEquals(MoneyUtil.calculateVatAmount(2190000, 10), f.order.getVatAmount());
        assertEquals(List.of("return:8", "update", "audit", "commit"), f.events);
    }

    @Test
    void repeatedRemoveIsNoOp() {
        Fixture f = new Fixture();
        assertTrue(f.service.removeVoucher(7, 80).isSuccess());
        assertEquals(List.of("commit"), f.events);
    }

    @Test
    void invalidFormatDoesNotOpenConnection() {
        Fixture f = new Fixture();
        assertFalse(f.service.applyVoucher(7, 80, "<script>").isSuccess());
        assertFalse(f.service.applyVoucher(7, 80, "").isSuccess());
        assertEquals(0, f.opens);
    }

    @Test
    void unknownCodeKeepsOldVoucherAndDiscount() {
        Fixture f = new Fixture();
        f.order.setVoucherId(8);
        f.order.setDiscountAmount(50000);
        f.voucher = null;
        ServiceResult<Void> result = f.service.applyVoucher(7, 80, "UNKNOWN");
        assertFalse(result.isSuccess());
        assertTrue(result.getFieldErrors().containsKey("voucherCode"));
        assertEquals(8, f.order.getVoucherId());
        assertEquals(50000L, f.order.getDiscountAmount());
        assertEquals(List.of("rollback"), f.events);
    }

    @Test
    void disabledExpiredFutureAndExhaustedVouchersAreRejected() {
        Fixture inactive = new Fixture();
        inactive.voucher.setActive(false);
        assertFalse(inactive.service.applyVoucher(7, 80, "WELCOME10").isSuccess());
        Fixture expired = new Fixture();
        expired.voucher.setEndDate(DateTimeUtil.today().minusDays(1));
        assertFalse(expired.service.applyVoucher(7, 80, "WELCOME10").isSuccess());
        Fixture future = new Fixture();
        future.voucher.setStartDate(DateTimeUtil.today().plusDays(1));
        assertFalse(future.service.applyVoucher(7, 80, "WELCOME10").isSuccess());
        Fixture exhausted = new Fixture();
        exhausted.voucher.setUsedCount(100);
        assertFalse(exhausted.service.applyVoucher(7, 80, "WELCOME10").isSuccess());
    }

    @Test
    void lastValidDayAndMinimumPriceEqualityAreAllowed() {
        Fixture f = new Fixture();
        f.voucher.setStartDate(DateTimeUtil.today());
        f.voucher.setEndDate(DateTimeUtil.today());
        f.voucher.setMinOrderValue(f.order.getUnitPriceSnapshot());
        assertTrue(f.service.applyVoucher(7, 80, "WELCOME10").isSuccess());
    }

    @Test
    void minimumOriginalPriceScopeAndCustomerLimitAreChecked() {
        Fixture min = new Fixture();
        min.voucher.setMinOrderValue(3000000);
        assertFalse(min.service.applyVoucher(7, 80, "WELCOME10").isSuccess());
        Fixture scope = new Fixture();
        scope.pkg.setPackageType(Package.Type.DAY_PASS);
        scope.voucher.setApplicableScope(Voucher.ApplicableScope.MEMBERSHIP_ONLY);
        assertFalse(scope.service.applyVoucher(7, 80, "WELCOME10").isSuccess());
        Fixture pt = new Fixture();
        pt.pkg.setPackageType(Package.Type.PT_SINGLE);
        assertFalse(pt.service.applyVoucher(7, 80, "WELCOME10").isSuccess());
        Fixture limit = new Fixture();
        limit.customerUses = 1;
        assertFalse(limit.service.applyVoucher(7, 80, "WELCOME10").isSuccess());
    }

    @Test
    void unlimitedCustomerUsesAndDayPassScopeAreAllowed() {
        Fixture f = new Fixture();
        f.voucher.setMaxUsagePerCustomer(null);
        f.customerUses = 100;
        f.pkg.setPackageType(Package.Type.DAY_PASS);
        assertTrue(f.service.applyVoucher(7, 80, "WELCOME10").isSuccess());
    }

    @Test
    void pendingTransactionBlocksBothApplyAndRemove() {
        Fixture f = new Fixture();
        f.pendingPayment = true;
        assertFalse(f.service.applyVoucher(7, 80, "WELCOME10").isSuccess());
        assertFalse(f.service.removeVoucher(7, 80).isSuccess());
        assertEquals(0, f.voucherReads);
    }

    @Test
    void paidCancelledExpiredForeignAndFrozenOrdersAreBlocked() {
        Fixture paid = new Fixture();
        paid.order.setStatus(Order.Status.PAID);
        assertFalse(paid.service.applyVoucher(7, 80, "WELCOME10").isSuccess());
        Fixture cancelled = new Fixture();
        cancelled.order.setStatus(Order.Status.CANCELLED);
        assertFalse(cancelled.service.removeVoucher(7, 80).isSuccess());
        Fixture expired = new Fixture();
        expired.order.setPaymentDueAt(DateTimeUtil.now().minusSeconds(1));
        assertFalse(expired.service.applyVoucher(7, 80, "WELCOME10").isSuccess());
        Fixture foreign = new Fixture();
        foreign.order = null;
        assertFalse(foreign.service.applyVoucher(7, 80, "WELCOME10").isSuccess());
        Fixture frozen = new Fixture();
        frozen.customer = new PaymentCustomerView(42, true);
        assertFalse(frozen.service.applyVoucher(7, 80, "WELCOME10").isSuccess());
    }

    @Test
    void failedReservationReturnUpdateAndAuditEachRollbackWithoutCommit() {
        Fixture reserve = new Fixture();
        reserve.reserveRows = 0;
        assertFalse(reserve.service.applyVoucher(7, 80, "WELCOME10").isSuccess());
        assertFalse(reserve.events.contains("commit"));
        Fixture returned = new Fixture();
        returned.order.setVoucherId(8);
        returned.returnRows = 0;
        assertFalse(returned.service.applyVoucher(7, 80, "WELCOME10").isSuccess());
        assertFalse(returned.events.contains("commit"));
        Fixture update = new Fixture();
        update.updateRows = 0;
        assertFalse(update.service.applyVoucher(7, 80, "WELCOME10").isSuccess());
        assertFalse(update.events.contains("commit"));
        Fixture audit = new Fixture();
        audit.failAudit = true;
        assertFalse(audit.service.applyVoucher(7, 80, "WELCOME10").isSuccess());
        assertFalse(audit.events.contains("commit"));
        for (Fixture f : List.of(reserve, returned, update, audit)) {
            assertEquals("rollback", f.events.get(f.events.size() - 1));
        }
    }

    @Test
    void checkoutShowsRealVoucherAndLocksWhenPaymentIsPending() {
        Fixture f = new Fixture();
        f.order.setVoucherId(9);
        var view = f.service.getCheckoutView(7, 80);
        assertTrue(view.isSuccess());
        assertEquals("WELCOME10", view.getData().getVoucherCode());
        assertTrue(view.getData().isVoucherEditable());
        assertTrue(view.getData().getSecondsLeft() > 0);
        f.pendingPayment = true;
        assertFalse(f.service.getCheckoutView(7, 80).getData().isVoucherEditable());
        f.pendingPayment = false;
        f.order.setPaymentDueAt(DateTimeUtil.now().minusSeconds(1));
        assertEquals(0, f.service.getCheckoutView(7, 80).getData().getSecondsLeft());
        assertFalse(f.service.getCheckoutView(7, 80).getData().isVoucherEditable());
    }

    /** Các DAO giả xác nhận cùng Connection và ghi thứ tự giữ/hoàn lượt rồi commit/rollback. */
    private static class Fixture {
        Order order = new Order(80);
        Package pkg = new Package(3);
        Voucher voucher = new Voucher(9);
        PaymentCustomerView customer = new PaymentCustomerView(42, false);
        List<String> events = new ArrayList<>();
        boolean pendingPayment;
        boolean failAudit;
        int reserveRows = 1;
        int returnRows = 1;
        int updateRows = 1;
        int customerUses;
        int opens;
        int voucherReads;
        String receivedCode;
        Connection connection;
        PaymentService service;

        Fixture() {
            order.setHolderCustomerId(42);
            order.setPackageId(3);
            order.setChannel(Order.Channel.ONLINE);
            order.setStatus(Order.Status.PENDING);
            order.setUnitPriceSnapshot(2190000);
            order.setVatRateSnapshot(10);
            order.setPaymentDueAt(DateTimeUtil.now().plusMinutes(20));
            pkg.setPackageType(Package.Type.MEMBERSHIP);
            pkg.setPrice(9999999); // Giá catalogue khác snapshot: không được dùng để tính discount.
            voucher.setVoucherCode("WELCOME10");
            voucher.setDiscountType(Voucher.DiscountType.PERCENT);
            voucher.setDiscountValue(10);
            voucher.setStartDate(DateTimeUtil.today().minusDays(1));
            voucher.setEndDate(DateTimeUtil.today().plusDays(1));
            voucher.setMaxUsage(100);
            voucher.setMaxUsagePerCustomer(1);
            connection = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                        if (method.getName().equals("commit") || method.getName().equals("rollback")) {
                            events.add(method.getName());
                        }
                        return null;
                    });
            PaymentDAO paymentDAO = new PaymentDAO() {
                @Override
                public PaymentCustomerView findCustomerForPurchase(Connection conn, int accountId) {
                    assertSame(connection, conn);
                    assertEquals(7, accountId);
                    return customer;
                }

                @Override
                public boolean hasPendingTransaction(Connection conn, int orderId) {
                    assertSame(connection, conn);
                    return pendingPayment;
                }

                @Override
                public int returnVoucherUse(Connection conn, int voucherId) {
                    assertSame(connection, conn);
                    events.add("return:" + voucherId);
                    return returnRows;
                }
            };
            OrderDAO orderDAO = new OrderDAO() {
                @Override
                public Order findOnlineForUpdate(Connection conn, int accountId, int orderId) {
                    assertSame(connection, conn);
                    assertEquals(7, accountId);
                    assertEquals(80, orderId);
                    return order;
                }

                @Override
                public Order findOnlineByIdAndAccount(Connection conn, int accountId, int orderId) {
                    assertEquals(7, accountId);
                    return order;
                }

                @Override
                public int updateVoucher(Connection conn, Order changed, java.time.LocalDateTime now) {
                    assertSame(connection, conn);
                    events.add("update");
                    return updateRows;
                }
            };
            PackageDAO packageDAO = new PackageDAO() {
                @Override
                public Package findById(Connection conn, int packageId) {
                    assertSame(connection, conn);
                    return pkg;
                }
            };
            VoucherDAO voucherDAO = new VoucherDAO() {
                @Override
                public List<Voucher> findForChange(Connection conn, String code, Integer currentVoucherId) {
                    assertSame(connection, conn);
                    receivedCode = code;
                    voucherReads++;
                    return voucher == null ? List.of() : List.of(voucher);
                }

                @Override
                public int countCustomerUses(Connection conn, int voucherId, int customerId, int excludedOrderId) {
                    assertSame(connection, conn);
                    assertEquals(42, customerId);
                    assertEquals(80, excludedOrderId);
                    return customerUses;
                }

                @Override
                public int reserveUse(Connection conn, int voucherId) {
                    assertSame(connection, conn);
                    events.add("reserve:" + voucherId);
                    return reserveRows;
                }

                @Override
                public String findCodeById(Connection conn, Integer voucherId) {
                    return voucherId == null ? null : "WELCOME10";
                }
            };
            AuditLogService audit = new AuditLogService() {
                @Override
                void logInTransaction(Connection conn, Integer accountId, Role role, String action, String entity,
                        Integer entityId, String field, String oldValue, String newValue, String reason) throws SQLException {
                    assertSame(connection, conn);
                    events.add("audit");
                    if (failAudit) {
                        throw new SQLException("Expected audit failure");
                    }
                }
            };
            service = new PaymentService(paymentDAO, packageDAO, orderDAO, new SystemConfigService(), audit, voucherDAO) {
                @Override
                protected Connection openConnection() {
                    opens++;
                    return connection;
                }
            };
        }
    }
}
