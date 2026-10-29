<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="gms" uri="/WEB-INF/gms-functions.tld" %>
<%--
================================================================================
 counter-sale-next.jsp — TRANG TẠM sau bước Chọn khách (/receptionist/sales?package=…&customer=…)

 Bước Voucher và Thanh toán chưa làm. Trang này chỉ cho thấy gói + khách đã chọn truyền đúng,
 và có nút quay lại. Khi làm bước Voucher: thay file này bằng counter-sale-voucher.jsp.
 BIẾN     selectedCard (CounterPackageCard), selectedCustomer (CounterCustomerRow)
================================================================================ --%>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<!DOCTYPE html>
<html lang="vi">
<head>
    <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
    <title>Bán hàng · Super Gym Lễ tân</title>
    <link rel="stylesheet" href="${ctx}/css/pages/receptionist/counter-sale.css?v=20261029a">
</head>
<body class="dock theme-staff counter-sale-page">
<%@ include file="/WEB-INF/jsp/common/flash-toast.jspf" %>
<div class="app">
    <%@ include file="/WEB-INF/jsp/common/layout-receptionist.jspf" %>
    <div class="main">
        <header class="topbar">
            <div class="tb-left">
                <h1 class="tb-title">Voucher</h1>
            </div>
        </header>

        <main class="content counter-sale" id="main-content" tabindex="-1">
            <div class="counter-layout">
                <section class="counter-block">
                    <p class="counter-empty">Bước Voucher và Thanh toán đang được làm.</p>
                    <div class="counter-actions">
                        <a class="btn btn-ghost" href="${ctx}/receptionist/sales?package=${selectedCard.packageId}">← Đổi khách</a>
                    </div>
                </section>

                <aside class="counter-summary" aria-label="Tóm tắt đơn">
                    <p class="counter-summary-title">Tóm tắt đơn</p>
                    <dl class="counter-summary-list">
                        <div>
                            <dt>Khách</dt>
                            <dd>
                                <c:out value="${selectedCustomer.fullName}" />
                                <span class="counter-customer-sub"><c:out value="${selectedCustomer.customerCode}" /> · <c:out value="${selectedCustomer.phoneText}" /></span>
                            </dd>
                        </div>
                        <div><dt>Gói</dt><dd><c:out value="${selectedCard.packageName}" /></dd></div>
                        <div><dt>Giá gói</dt><dd><c:out value="${gms:formatMoney(selectedCard.price)}" /></dd></div>
                        <div><dt>Trước VAT</dt><dd><c:out value="${gms:formatMoney(selectedCard.preTaxAmount)}" /></dd></div>
                        <div><dt>VAT <c:out value="${selectedCard.vatRate}" />% (đã gồm)</dt><dd><c:out value="${gms:formatMoney(selectedCard.vatAmount)}" /></dd></div>
                        <div class="is-total"><dt>Phải thu</dt><dd><c:out value="${gms:formatMoney(selectedCard.price)}" /></dd></div>
                    </dl>
                </aside>
            </div>
        </main>
    </div>
</div>
</body>
</html>