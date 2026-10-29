<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="gms" uri="/WEB-INF/gms-functions.tld" %>
<!DOCTYPE html>
<html lang="vi">
    <head>
        <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
        <title>Thanh toán · Super Gym</title>
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/customer/dashboard.css?v=7">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/customer/checkout.css?v=7">
        <script src="${pageContext.request.contextPath}/js/pages/customer/checkout.js?v=5" defer></script>
    </head>
    <body class="dock checkout-page ${themeClass}">
        <%@ include file="/WEB-INF/jsp/common/flash-toast.jspf" %>
        <div class="app">
            <%@ include file="/WEB-INF/jsp/common/layout-customer.jspf" %>
            <div class="main">
                <header class="topbar">
                    <div class="tb-left"><h1 class="tb-title">Thanh toán</h1></div>
                    <div class="top-right"><span class="tb-user-text"><c:out value="${customerName}" /></span></div>
                </header>
                <main class="content" id="main-content">
                    <a class="checkout-back" href="${pageContext.request.contextPath}/customer/packages">← Quay lại chọn gói</a>
                    <div class="checkout-heading">
                        <div>
                            <h1>Xác nhận và thanh toán</h1>
                            <p>Kiểm tra gói tập và ưu đãi trước khi tiếp tục.</p>
                        </div>
                        <%-- common.js bindCountdowns() dùng số giây do PaymentService tính; hết giờ reload khóa form. --%>
                        <c:if test="${not empty checkout and order.pending and checkout.secondsLeft gt 0}">
                            <div class="checkout-clock"><i class="fa-regular fa-clock" aria-hidden="true"></i> Còn lại
                                <strong data-countdown-seconds="${checkout.secondsLeft}" data-countdown-reload="true">Đang cập nhật…</strong>
                            </div>
                        </c:if>
                    </div>
                    <c:if test="${not empty paymentError}"><div class="alert-banner danger" role="alert"><c:out value="${paymentError}" /></div></c:if>
                    <c:choose>
                        <c:when test="${not empty checkoutError}">
                            <div class="alert-banner danger" role="alert"><c:out value="${checkoutError}" /></div>
                        </c:when>
                        <c:otherwise>
                            <div class="checkout-grid">
                                <%-- Dữ liệu gói là snapshot trên Order, không gán tên/giá/quyền lợi minh họa từ mockup. --%>
                                <section class="checkout-card checkout-package">
                                    <h2>Gói tập của bạn</h2>
                                    <div class="checkout-banner">
                                        <div class="checkout-banner__brand"><i class="fa-solid fa-dumbbell" aria-hidden="true"></i> SUPER GYM</div>
                                        <h3><c:out value="${order.packageNameSnapshot}" /></h3>
                                    </div>
                                    <c:if test="${not empty order.packageDescriptionSnapshot}"><p class="checkout-muted"><c:out value="${order.packageDescriptionSnapshot}" /></p></c:if>
                                        <ul class="checkout-benefits">
                                        <c:forEach var="benefit" items="${checkout.benefitLines}">
                                            <li><i class="fa-regular fa-circle-check" aria-hidden="true"></i><span><c:out value="${benefit}" /></span></li>
                                                </c:forEach>
                                    </ul>
                                    <div class="checkout-details">
                                        <div><span>Khách hàng</span><strong><c:out value="${customerName}" /></strong></div>
                                        <div><span>Mã đơn</span><strong><c:out value="${order.orderCode}" /></strong></div>
                                        <c:choose>
                                            <c:when test="${order.pending}"><span class="badge badge-warning">Chờ thanh toán</span></c:when>
                                            <c:when test="${order.paid}"><span class="badge badge-success">Đã thanh toán</span></c:when>
                                            <c:otherwise><span class="badge badge-danger">Đã hủy</span></c:otherwise>
                                        </c:choose>
                                    </div>
                                </section>
                                <section class="checkout-card checkout-summary">
                                    <h2>Tóm tắt thanh toán</h2>
                                    <dl class="checkout-prices">
                                        <div><dt>Giá gói</dt><dd><c:out value="${checkout.priceText}" /></dd></div>
                                        <div id="discountRow" ${empty checkout.voucherCode ? 'hidden' : ''}>
                                            <dt>Giảm giá</dt>
                                            <dd class="checkout-discount">
                                                −<span id="discountAmount"><c:out value="${checkout.discountText}" /></span>
                                            </dd>
                                        </div>
                                    </dl>
                                    <%-- checkout.js tự POST khi blur/Enter, dưới ô chỉ hiện một trạng thái cụ thể. --%>
                                    <form id="voucherForm" class="checkout-voucher" method="post" action="${pageContext.request.contextPath}/customer/checkout"
                                          data-editable="${checkout.voucherEditable}" data-valid="${empty voucherError}"
                                          data-applied="<c:out value="${checkout.voucherCode}" />">
                                        <input type="hidden" name="order" value="${order.orderId}">
                                        <input type="hidden" name="csrfToken" value="<c:out value="${csrfToken}" />">
                                        <input type="hidden" name="action" value="apply-voucher">
                                        <label for="voucherCode">Mã giảm giá</label>
                                        <div class="checkout-voucher__input">
                                            <input id="voucherCode" type="text" name="voucherCode" maxlength="20" autocomplete="off"
                                                   value="<c:out value="${voucherInput}" />" placeholder="Nhập mã giảm giá"
                                                   aria-describedby="voucherStatus" ${checkout.voucherEditable ? '' : 'disabled'}>
                                            <i id="voucherValidIcon" class="fa-solid fa-circle-check" aria-hidden="true" ${empty checkout.voucherCode ? 'hidden' : ''}></i>
                                            <button id="voucherClear" type="button" aria-label="Gỡ mã giảm giá" title="Gỡ mã giảm giá"
                                                    ${checkout.voucherEditable ? '' : 'disabled'} ${empty checkout.voucherCode and empty voucherInput ? 'hidden' : ''}>×</button>
                                        </div>
                                        <p id="voucherStatus" class="checkout-voucher__status" data-state="${empty voucherError ? 'success' : 'error'}" role="status" aria-live="polite">
                                            <c:choose>
                                                <c:when test="${not empty voucherError}"><c:out value="${voucherError}" /></c:when>
                                                <c:otherwise><c:out value="${checkout.voucherMessage}" /></c:otherwise>
                                            </c:choose>
                                        </p>
                                        <c:if test="${not checkout.voucherEditable}"><p class="checkout-muted"><c:out value="${checkout.voucherLockReason}" /></p></c:if>
                                            <noscript><button class="btn btn-outline" type="submit">Kiểm tra mã</button></noscript>
                                        </form>
                                        <div class="checkout-total">
                                            <div><span>Tổng thanh toán</span><strong id="totalAmount"><c:out value="${checkout.totalText}" /></strong></div>
                                        <p>Đã bao gồm VAT <c:out value="${order.vatRateSnapshot}" />%: <span id="vatAmount"><c:out value="${checkout.vatText}" /></span></p>
                                            <%-- Tiền trước thuế từ snapshot đơn; checkout.js cập nhật preTaxAmount khi đổi voucher. --%>
                                        <p>Tiền trước thuế:
                                            <span id="preTaxAmount"><c:out value="${gms:formatMoney(order.finalAmount - order.vatAmount)}" /></span>
                                        </p>
                                    </div>
                                    <%-- Gói Membership mua lúc đang có gói còn hạn: báo trước ngày thật sự bắt đầu (DBR-52). --%>
                                    <c:if test="${checkout.chained}">
                                        <div class="checkout-chain">
                                            <span class="checkout-chain__title">Gói mới nối tiếp gói đang dùng</span>
                                            <p>Gói đang dùng của bạn còn hạn đến <b><c:out value="${checkout.chainCurrentEndText}" /></b>.
                                                Gói này sẽ bắt đầu từ <b><c:out value="${checkout.chainStartText}" /></b> và dùng đến <b><c:out value="${checkout.chainEndText}" /></b>.</p> 
                                        </div>
                                    </c:if>
                                    <form id="paymentForm"
                                          method="post"
                                          action="${pageContext.request.contextPath}/customer/checkout">

                                        <input type="hidden"
                                               name="order"
                                               value="${order.orderId}">

                                        <input type="hidden"
                                               name="action"
                                               value="pay">

                                        <input type="hidden"
                                               name="csrfToken"
                                               value="<c:out value='${csrfToken}' />">

                                        <button id="payButton"
                                                class="btn btn-primary checkout-pay"
                                                type="submit"
                                                ${checkout.voucherEditable ? '' : 'disabled'}>

                                            Tiến hành thanh toán
                                            <i class="fa-solid fa-arrow-right" aria-hidden="true"></i>
                                        </button>
                                    </form>
                                    <div class="checkout-provider">
                                        <img src="${pageContext.request.contextPath}/images/payment/vnpay.svg" alt="VNPay">
                                        <p><i class="fa-solid fa-lock" aria-hidden="true"></i> Thanh toán an toàn qua VNPay</p>
                                    </div>
                                </section>
                            </div>
                            <p id="paymentAvailability" class="checkout-footer">Kiểm tra thông tin trước khi tiến hành thanh toán.</p>
                        </c:otherwise>
                    </c:choose>
                </main>
            </div>
        </div>
    </body>
</html>
