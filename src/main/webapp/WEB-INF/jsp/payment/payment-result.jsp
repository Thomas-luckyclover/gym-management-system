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
              href="${pageContext.request.contextPath}/css/pages/payment/payment-result.css?v=1">
    </head>
    <c:if test="${cleanResultUrl}">
        <script>
            // Rút gọn thanh địa chỉ, không gửi thêm request hoặc đổi dữ liệu đơn.
            window.history.replaceState(
                    null,
                    "",
                    "${pageContext.request.contextPath}/customer/payment-result?transaction=${transactionId}"
                                );
        </script>
    </c:if>
    <c:if test="${empty resultError and result.waiting}">
        <script>
            window.setTimeout(function () {
                window.location.reload();
            }, 15000);
        </script>
    </c:if>
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
    </body>
</html>