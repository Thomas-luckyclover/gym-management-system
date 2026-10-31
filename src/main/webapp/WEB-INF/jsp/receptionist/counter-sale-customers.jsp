<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="gms" uri="/WEB-INF/gms-functions.tld" %>
<%--
================================================================================
 counter-sale-customers.jsp — BÁN GÓI TẠI QUẦY, BƯỚC 2: CHỌN KHÁCH (/receptionist/sales?package=…), SRS #37

 AI GỬI DỮ LIỆU SANG?  CounterSaleServlet.showCustomers
 BIẾN     selectedCard (CounterPackageCard, gói đã chọn ở bước 1)
 TÌM KHÁCH  AJAX: counter-sale.js gọi GET /receptionist/sales/customers?q=… (CounterSaleServlet.writeCustomers) rồi tự vẽ bảng
 QUY TẮC  Gói tập và vé ngày bán cho mọi hồ sơ chưa xóa (Mới, Đang hoạt động, Bảo lưu, Hết hạn): nút "Chọn" luôn bấm được.
          Hồ sơ chưa có tài khoản vẫn mua được, chỉ gắn nhãn để lễ tân biết.
 BỐ CỤC   trái: ô tìm + bảng khách + nút Đổi gói / Tạo hồ sơ · phải: "Tóm tắt đơn"
================================================================================ --%>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<c:set var="base" value="${ctx}/receptionist/sales" />
<!DOCTYPE html>
<html lang="vi">
<head>
    <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
    <title>Chọn khách · Super Gym Lễ tân</title>
    <link rel="stylesheet" href="${ctx}/css/pages/receptionist/counter-sale.css?v=20261031_vContrast">
    <script src="${ctx}/js/pages/receptionist/counter-sale.js?v=20261030_vFixSearch" defer></script>
</head>
<body class="dock theme-staff counter-sale-page">
<%@ include file="/WEB-INF/jsp/common/flash-toast.jspf" %>
<div class="app">
    <%@ include file="/WEB-INF/jsp/common/layout-receptionist.jspf" %>
    <div class="main">
        <header class="topbar">
            <div class="tb-left">
                <h1 class="tb-title">Chọn khách</h1>
            </div>
            <div class="top-right">
                <a class="tb-user" href="${ctx}/account" title="Cài đặt tài khoản">
                    <span class="avatar">LT</span>
                    <span class="tb-user-text"><b><c:out value="${sessionScope.displayName}" default="Lễ tân" /></b><small>Lễ tân quầy</small></span>
                </a>
            </div>
        </header>

        <main class="content counter-sale" id="main-content" tabindex="-1">
            <div class="counter-layout">

                <%-- ===== Cột trái: ô tìm khách (AJAX) + bảng kết quả ===== --%>
                <section class="counter-block">
                    <%-- #customerSearch: counter-sale.js hàm handleCustomerInput() đọc chữ gõ rồi gọi loadCustomers().
                         data-package-id gắn gói đã chọn vào nút "Chọn" của từng dòng. Không có form, không có nút Tìm. --%>
                    <div class="counter-search" role="search">
                        <label class="visually-hidden" for="customerSearch">Tìm khách</label>
                        <i class="fa-solid fa-magnifying-glass" aria-hidden="true"></i>
                        <input id="customerSearch" type="search" data-package-id="${selectedCard.packageId}"
                               placeholder="Tên, số điện thoại hoặc mã KH (KH-0102)" autocomplete="off" spellcheck="false" autofocus>
                    </div>

                    <%-- #customerState: chữ hướng dẫn / đang tìm / không thấy / lỗi. counter-sale.js hàm showCustomerState() ghi vào đây. --%>
                    <p class="counter-empty" id="customerState" aria-live="polite">Gõ tên, số điện thoại hoặc mã KH để tìm khách.
                        <span class="counter-hint">Tên không cần dấu: "nguyen van an", "0901", "KH-0102" đều được.</span></p>

                    <%-- #customerTableWrap hiện khi có kết quả; #customerTableBody do counter-sale.js hàm renderCustomerRows() đổ dòng vào --%>
                    <div class="counter-table-wrap" id="customerTableWrap" hidden>
                        <table class="counter-table no-gms-table">
                            <thead>
                                <tr>
                                    <th scope="col">Khách hàng</th>
                                    <th scope="col">Trạng thái</th>
                                    <th scope="col">Gói tập</th>
                                    <th scope="col"><span class="visually-hidden">Chọn</span></th>
                                </tr>
                            </thead>
                            <tbody id="customerTableBody"></tbody>
                        </table>
                    </div>

                    <%-- Hàng nút dưới bảng: Đổi gói (trái) · Tạo hồ sơ mới (phải, mở màn hồ sơ ở tab mới) --%>
                    <div class="counter-actions">
                        <a class="btn btn-ghost" href="${base}">← Đổi gói</a>
                        <a class="btn btn-outline" href="${ctx}/receptionist/customers/form" target="_blank" rel="noopener">+ Tạo hồ sơ</a>
                    </div>
                </section>

                <%-- ===== Cột phải: Tóm tắt đơn (selectedCard). Dòng Giảm giá thêm ở bước Voucher. ===== --%>
                <aside class="counter-summary" aria-label="Tóm tắt đơn">
                    <p class="counter-summary-title">Tóm tắt đơn</p>
                    <dl class="counter-summary-list">
                        <div><dt>Khách</dt><dd class="counter-muted">Chưa chọn</dd></div>
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