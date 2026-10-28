<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="gms" uri="/WEB-INF/gms-functions.tld" %>

<!DOCTYPE html>
<html lang="vi">
    <head>
        <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
        <title>Lịch sử giao dịch · Super Gym</title>

        <link rel="stylesheet"
              href="${pageContext.request.contextPath}/css/pages/customer/dashboard.css?v=7">
        <link rel="stylesheet"
              href="${pageContext.request.contextPath}/css/pages/customer/transaction-history.css?v=1">
        <script src="${pageContext.request.contextPath}/js/pages/customer/transaction-history.js?v=1" defer></script>
    </head>

    <body class="dock ${themeClass}">
        <div class="app">
            <%@ include file="/WEB-INF/jsp/common/layout-customer.jspf" %>

            <div class="main">
                <header class="topbar">
                    <h1 class="tb-title">Lịch sử giao dịch</h1>
                </header>

                <main class="content" id="main-content">
                    <section class="history-panel">

                        <%-- Bộ lọc: các nút chọn nhanh, mỗi nút là một link GET --%>
                        <nav class="history-chips" aria-label="Lọc theo trạng thái">
                            <c:url var="allUrl" value="/customer/transactions"/>
                            <a class="history-chip ${empty selectedStatus ? 'is-active' : ''}"
                               href="${allUrl}">Tất cả</a>

                            <c:url var="successUrl" value="/customer/transactions">
                                <c:param name="status" value="Success"/>
                            </c:url>
                            <a class="history-chip ${selectedStatus eq 'Success' ? 'is-active' : ''}"
                               href="${successUrl}">Thành công</a>

                            <c:url var="pendingUrl" value="/customer/transactions">
                                <c:param name="status" value="Pending"/>
                            </c:url>
                            <a class="history-chip ${selectedStatus eq 'Pending' ? 'is-active' : ''}"
                               href="${pendingUrl}">Đang chờ</a>

                            <c:url var="failedUrl" value="/customer/transactions">
                                <c:param name="status" value="Failed"/>
                            </c:url>
                            <a class="history-chip ${selectedStatus eq 'Failed' ? 'is-active' : ''}"
                               href="${failedUrl}">Thất bại</a>

                            <c:url var="expiredUrl" value="/customer/transactions">
                                <c:param name="status" value="Expired"/>
                            </c:url>
                            <a class="history-chip ${selectedStatus eq 'Expired' ? 'is-active' : ''}"
                               href="${expiredUrl}">Hết hạn</a>
                        </nav>

                        <c:choose>
                            <c:when test="${not empty historyError}">
                                <p class="history-error" role="alert">
                                    <c:out value="${historyError}"/>
                                </p>
                            </c:when>

                            <c:otherwise>
                                <c:choose>
                                    <c:when test="${empty historyItems}">
                                        <p class="history-empty">Chưa có giao dịch nào.</p>
                                    </c:when>

                                    <c:otherwise>
                                        <%-- Danh sách nhóm theo tháng: gặp tháng mới thì đóng <ul> cũ và mở nhóm mới --%>
                                        <c:set var="currentMonth" value=""/>
                                        <c:forEach var="item" items="${historyItems}">

                                            <c:if test="${item.monthLabel ne currentMonth}">
                                                <c:if test="${not empty currentMonth}"></ul></c:if>
                                                <p class="history-month" role="heading" aria-level="2">
                                                    <c:out value="${item.monthLabel}"/>
                                                </p>
                                                <ul class="history-list">
                                                <c:set var="currentMonth" value="${item.monthLabel}"/>
                                            </c:if>

                                            <li>
                                                <%-- Dòng: bấm để mở popup chi tiết (js/pages/customer/transaction-history.js) --%>
                                                <button type="button" class="th-row"
                                                        data-th-open="th-dialog-${item.transactionId}"
                                                        data-status="${item.displayStatus}"
                                                        data-tone="${item.tone}">
                                                    <span class="th-row__thumb">
                                                        <i class="th-badge" data-status="${item.displayStatus}" aria-hidden="true"></i>
                                                    </span>
                                                    <span>
                                                        <span class="th-row__title"><c:out value="${item.packageName}"/></span>
                                                        <span class="th-row__date">
                                                            <c:out value="${item.createdDateText}"/> · <c:out value="${item.paymentMethodLabel}"/>
                                                        </span>
                                                        <c:if test="${item.hasValidity}">
                                                            <span class="th-row__valid">Đến <c:out value="${item.validUntilText}"/></span>
                                                        </c:if>
                                                    </span>
                                                    <span class="th-row__side">
                                                        <strong class="th-row__amount"><c:out value="${gms:formatMoney(item.amount)}"/></strong>
                                                        <span class="th-status" data-status="${item.displayStatus}"><c:out value="${item.shortStatusLabel}"/></span>
                                                    </span>
                                                    <span class="th-row__go"><span>Chi tiết</span></span>
                                                </button>

                                                <%-- Popup chi tiết của giao dịch này --%>
                                                <dialog class="th-dialog" id="th-dialog-${item.transactionId}"
                                                        data-tone="${item.tone}" data-status="${item.displayStatus}"
                                                        aria-label="Chi tiết giao dịch">
                                                    <div class="th-dialog__banner">
                                                        <button type="button" class="th-dialog__close" data-th-close aria-label="Đóng">&#10005;</button>
                                                    </div>
                                                    <div class="th-dialog__body">
                                                        <div class="th-dialog__head">
                                                            <div>
                                                                <h2 class="th-dialog__title"><c:out value="${item.packageName}"/></h2>
                                                                <span class="th-status" data-status="${item.displayStatus}"><c:out value="${item.shortStatusLabel}"/></span>
                                                            </div>
                                                            <strong class="th-dialog__amount"><c:out value="${gms:formatMoney(item.amount)}"/></strong>
                                                        </div>

                                                        <dl class="th-dialog__list">
                                                            <c:if test="${item.hasValidity}">
                                                                <div><dt>Hiệu lực</dt><dd><c:out value="${item.validityText}"/></dd></div>
                                                            </c:if>
                                                            <div><dt>Thời gian</dt><dd><c:out value="${item.createdAtText}"/></dd></div>
                                                            <div><dt>Thanh toán</dt><dd><c:out value="${item.paymentMethodLabel}"/></dd></div>
                                                            <c:if test="${not empty item.refundRowText}">
                                                                <div><dt>Hoàn tiền</dt><dd><c:out value="${item.refundRowText}"/></dd></div>
                                                            </c:if>
                                                            <div><dt>Mã đơn</dt><dd><c:out value="${item.orderCode}"/></dd></div>
                                                            <c:if test="${not empty item.gatewayReference}">
                                                                <div><dt>Mã tham chiếu</dt><dd><c:out value="${item.gatewayReference}"/></dd></div>
                                                            </c:if>
                                                        </dl>

                                                        <div class="th-dialog__actions">
                                                            <button type="button" class="btn btn-outline" data-th-close>Đóng</button>

                                                            <c:choose>
                                                                <c:when test="${item.canViewPackage}">
                                                                    <c:url var="packageUrl" value="/customer/bookings">
                                                                        <c:param name="id" value="${item.subscriptionId}"/>
                                                                    </c:url>
                                                                    <a class="btn btn-outline" href="${packageUrl}">Xem gói</a>
                                                                </c:when>

                                                                <c:when test="${item.canBuyAgain}">
                                                                    <c:url var="buyAgainUrl" value="/customer/packages"/>
                                                                    <a class="btn btn-buy" href="${buyAgainUrl}">Mua lại</a>
                                                                </c:when>

                                                                <c:when test="${item.onlineGateway and item.status eq 'Pending'}">
                                                                    <c:url var="resultUrl" value="/customer/payment-result">
                                                                        <c:param name="transaction" value="${item.transactionId}"/>
                                                                    </c:url>
                                                                    <a class="btn btn-outline" href="${resultUrl}">Xem kết quả</a>
                                                                </c:when>
                                                            </c:choose>
                                                        </div>
                                                    </div>
                                                </dialog>
                                            </li>
                                        </c:forEach>
                                        </ul>
                                    </c:otherwise>
                                </c:choose>

                                <nav class="history-pagination"
                                     aria-label="Phân trang lịch sử giao dịch">

                                    <c:if test="${currentPage > 1}">
                                        <c:url var="previousUrl" value="/customer/transactions">
                                            <c:param name="page" value="${currentPage - 1}"/>
                                            <c:param name="status" value="${selectedStatus}"/>
                                        </c:url>
                                        <a href="${previousUrl}">Trang trước</a>
                                    </c:if>

                                    <span>Trang <c:out value="${currentPage}"/></span>

                                    <c:if test="${hasNext}">
                                        <c:url var="nextUrl" value="/customer/transactions">
                                            <c:param name="page" value="${currentPage + 1}"/>
                                            <c:param name="status" value="${selectedStatus}"/>
                                        </c:url>
                                        <a href="${nextUrl}">Trang sau</a>
                                    </c:if>
                                </nav>
                            </c:otherwise>
                        </c:choose>
                    </section>
                </main>
            </div>
        </div>
    </body>
</html>
