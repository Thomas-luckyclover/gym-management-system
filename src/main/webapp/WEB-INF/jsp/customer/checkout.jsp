<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="gms" uri="/WEB-INF/gms-functions.tld" %>
<!DOCTYPE html>
<html lang="vi">
    <head>
        <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
        <title>Thanh toán · Super Gym</title>
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/customer/dashboard.css?v=7">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/customer/checkout.css?v=1">
    </head>
    <body class="dock ${themeClass}">
        <%@ include file="/WEB-INF/jsp/common/flash-toast.jspf" %>
        <div class="app">
            <%@ include file="/WEB-INF/jsp/common/layout-customer.jspf" %>
            <div class="main">
                <header class="topbar">
                    <h1 class="tb-title">Đơn hàng của bạn</h1>
                </header>
                <main class="content">
                    <%-- ===== Tóm tắt: checkout do PaymentService tạo; order là snapshot do OrderDAO đọc ===== --%>
                    <c:choose>
                        <c:when test="${not empty checkoutError}">
                            <div class="alert-banner danger" role="alert"><c:out value="${checkoutError}" /></div>
                        </c:when>
                        <c:otherwise>
                            <section class="checkout-card">
                                <h2><c:out value="${order.packageNameSnapshot}" /></h2>
                                <p>Mã đơn: <c:out value="${order.orderCode}" /></p>
                                <c:choose>
                                    <c:when test="${order.pending}"><p>Trạng thái: Chờ thanh toán</p></c:when>
                                    <c:when test="${order.paid}"><p>Trạng thái: Đã thanh toán</p></c:when>
                                    <c:otherwise><p>Trạng thái: Đã hủy</p></c:otherwise>
                                </c:choose>
                                <dl class="checkout-summary">
                                    <div><dt>Giá gói</dt><dd><c:out value="${gms:formatMoney(order.unitPriceSnapshot)}" /></dd></div>
                                    <div><dt>Giảm giá voucher</dt><dd><c:out value="${gms:formatMoney(order.discountAmount)}" /></dd></div>
                                    <div class="checkout-total"><dt>Tổng thanh toán</dt><dd><c:out value="${gms:formatMoney(order.finalAmount)}" /></dd></div>
                                    <div><dt>Trong đó VAT <c:out value="${order.vatRateSnapshot}" />%</dt><dd><c:out value="${gms:formatMoney(order.vatAmount)}" /></dd></div>
                                </dl>
                                <p>Hạn thanh toán: <c:out value="${gms:formatDateTime(order.paymentDueAt)}" /></p>
                                <%-- ===== common.js bindCountdowns(): số giây do server tính, hết giờ reload để khóa form ===== --%>
                                <c:if test="${order.pending and checkout.secondsLeft gt 0}">
                                    <p class="checkout-countdown">Thời gian còn lại:
                                        <strong data-countdown-seconds="${checkout.secondsLeft}" data-countdown-reload="true">Đang cập nhật…</strong>
                                    </p>
                                </c:if>

                                <%-- ===== Voucher: giữ voucherInput khi POST lỗi; errors.voucherCode do Service trả ===== --%>
                                <form class="checkout-voucher" method="post" action="${pageContext.request.contextPath}/customer/checkout">
                                    <h3>Mã giảm giá</h3>
                                    <input type="hidden" name="order" value="${order.orderId}">
                                    <input type="hidden" name="csrfToken" value="<c:out value="${csrfToken}" />">
                                    <label for="voucherCode">Nhập mã voucher</label>
                                    <div class="checkout-voucher__controls">
                                        <input id="voucherCode" type="text" name="voucherCode" maxlength="20"
                                               value="<c:out value="${voucherInput}" />" placeholder="Ví dụ: WELCOME10"
                                               aria-describedby="voucherHint voucherError" ${checkout.voucherEditable ? '' : 'disabled'}>
                                        <button class="btn btn-primary" type="submit" name="action" value="apply-voucher"
                                                ${checkout.voucherEditable ? '' : 'disabled'}>Áp dụng</button>
                                        <c:if test="${not empty checkout.voucherCode}">
                                            <button class="btn btn-outline" type="submit" name="action" value="remove-voucher"
                                                    ${checkout.voucherEditable ? '' : 'disabled'}>Gỡ mã</button>
                                        </c:if>
                                    </div>
                                    <p id="voucherHint" class="checkout-voucher__hint">Mỗi đơn áp dụng một mã. Giá trên đã bao gồm VAT.</p>
                                    <c:if test="${not empty checkout.voucherCode}">
                                        <p>Đang áp dụng: <strong><c:out value="${checkout.voucherCode}" /></strong></p>
                                    </c:if>
                                    <div id="voucherError" class="checkout-voucher__error" role="alert">
                                        <c:choose>
                                            <c:when test="${not empty errors.voucherCode}"><c:out value="${errors.voucherCode}" /></c:when>
                                            <c:otherwise><c:out value="${voucherError}" /></c:otherwise>
                                        </c:choose>
                                    </div>
                                    <c:if test="${not checkout.voucherEditable}">
                                        <p><c:out value="${checkout.voucherLockReason}" /></p>
                                    </c:if>
                                </form>
                                <p class="checkout-voucher__hint">Thanh toán qua VNPay đang được hoàn thiện.</p>
                            </section>
                        </c:otherwise>
                    </c:choose>
                    <a class="btn btn-outline" href="${pageContext.request.contextPath}/customer/packages">Quay lại chọn gói</a>
                </main>
            </div>
        </div>
    </body>
</html>
