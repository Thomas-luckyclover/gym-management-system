<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="gms" uri="/WEB-INF/gms-functions.tld" %>
<!DOCTYPE html>
<html lang="vi">
    <head>
        <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
        <title>Kết quả thanh toán · Super Gym</title>

        <link rel="stylesheet"
              href="${pageContext.request.contextPath}/css/pages/customer/dashboard.css?v=7">

        <link rel="stylesheet"
              href="${pageContext.request.contextPath}/css/pages/payment/payment-result.css?v=2">

<link rel="stylesheet"
href="${pageContext.request.contextPath}/css/pages/customer/start-date-picker.css?v=4">
<script src="${pageContext.request.contextPath}/js/pages/customer/start-date-picker.js?v=1" defer></script>
    </head>
<body class="dock ${themeClass}">
    <%@ include file="/WEB-INF/jsp/common/flash-toast.jspf" %>

    <div class="app">
        <%@ include file="/WEB-INF/jsp/common/layout-customer.jspf" %>

        <div class="main">
            <header class="topbar">
                <h1 class="tb-title">Kết quả thanh toán</h1>
            </header>

            <main class="content" id="main-content">
                <section class="payment-result">
                    <img class="payment-result__logo"
                         src="${pageContext.request.contextPath}/images/payment/vnpay.svg"
                         alt="VNPay">

                    <c:choose>
                        <c:when test="${not empty resultError}">
                            <h2>Chưa xác minh được kết quả</h2>
                            <p role="alert">
                                <c:out value="${resultError}" />
                            </p>
                        </c:when>

                        <c:otherwise>
                            <h2>
                                <c:out value="${result.headline}" />
                            </h2>

                            <p role="status">
                                <c:out value="${result.message}" />
                            </p>

                            <dl>
                                <div>
                                    <dt>Mã đơn</dt>
                                    <dd>
                                        <c:out value="${result.order.orderCode}" />
                                    </dd>
                                </div>

                                <div>
                                    <dt>Gói tập</dt>
                                    <dd>
                                        <c:out value="${result.order.packageNameSnapshot}" />
                                    </dd>
                                </div>

                                <div>
                                    <dt>Số tiền của đơn</dt>
                                    <dd>
                                        <c:out value="${gms:formatMoney(result.order.finalAmount)}" />
                                    </dd>
                                </div>
                            </dl>

                            <%-- Gói Membership đã cấp: hiện ngày bắt đầu/hết hạn, còn đổi được thì có nút "Đổi ngày". --%>
                            <c:if test="${result.success and not empty result.startPicker}">
                                <c:set var="picker" value="${result.startPicker}" />
                                <div class="sdp-anchor" data-sdp data-sdp-url="/customer/subscription/start-date"
                                     data-sdp-sub="${picker.subscriptionId}" data-sdp-csrf="<c:out value='${csrfToken}' />">
                                    <div class="validity-line">
                                        <span class="validity-line__label">Hiệu lực<c:if test="${not picker.editable and not empty picker.lockReason}"><span class="hint-tip" tabindex="0" role="note" aria-label="<c:out value='${picker.lockReason}' />" data-tip="<c:out value='${picker.lockReason}' />">i</span></c:if></span>
                                        <b class="validity-line__range"><span data-sdp-start><c:out value="${picker.startText}" /></span> – <span data-sdp-end><c:out value="${picker.endText}" /></span></b>
                                        <c:if test="${picker.editable}"><button type="button" class="sdp-btn" data-sdp-toggle aria-expanded="false">Đổi ngày</button></c:if>
                                    </div>
                                    <c:if test="${picker.editable}">
                                        <%@ include file="/WEB-INF/jsp/customer/_start-date-picker.jspf" %>
                                    </c:if>
                                </div>
                            </c:if>

                            <c:if test="${result.retryable}">
                                <a class="btn btn-outline"
                                   href="${pageContext.request.contextPath}/customer/checkout?order=${result.order.orderId}">
                                    Quay lại thanh toán
                                </a>
                            </c:if>
                        </c:otherwise>
                    </c:choose>

                    <div class="btn-row">
                        <a class="btn btn-outline"
                           href="${pageContext.request.contextPath}/customer/packages">
                            Chọn gói
                        </a>

                        <a class="btn btn-outline"
                           href="${pageContext.request.contextPath}/customer/dashboard">
                            Trang cá nhân
                        </a>
                        <a class="btn btn-outline"
                           href="${pageContext.request.contextPath}/customer/bookings">
                            Gói của tôi
                        </a>

                        <a class="btn btn-outline"
                           href="${pageContext.request.contextPath}/customer/transactions">
                            Lịch sử giao dịch
                        </a>
                    </div>
                </section>
            </main>
        </div>
    </div>
    <%-- Chỉ đọc lại kết quả từ DB; không gọi QueryDR tại JSP. --%>
    <c:if test="${empty resultError and not empty result and result.waiting}">
        <script>
            window.setTimeout(function () {
                window.location.reload();
            }, 15000);
        </script>
    </c:if>
</body>
</html>