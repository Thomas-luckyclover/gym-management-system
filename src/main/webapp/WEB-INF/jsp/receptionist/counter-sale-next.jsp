<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="gms" uri="/WEB-INF/gms-functions.tld" %>
<%--
================================================================================
 counter-sale-next.jsp — BÁN GÓI TẠI QUẦY, BƯỚC 3: XÁC NHẬN & THU TIỀN
 (/receptionist/sales?package=…&customer=…)

 HỢP ĐỒNG SERVLET → JSP → JS (SRS #37):
 -------------------------------------------------------------------------------
 Attribute/field             | Nguồn                   | Cách hiển thị / sử dụng
 -------------------------------------------------------------------------------
 selectedCard                | Service tạo             | Thẻ chi tiết gói, giá niêm yết, VAT, tổng tiền
 selectedCustomer            | Service tạo             | Thông tin hội viên: Họ tên, mã KH, SĐT, trạng thái
 isChained                   | Servlet tính            | true: tự động nối tiếp gói cũ; false: cho chọn ngày
 todayText                   | Servlet định dạng       | dd/MM/yyyy ngày hiện tại
 defaultStartDateText        | Servlet định dạng       | Ngày bắt đầu mặc định (hôm nay hoặc ngày kế tiếp)
 defaultStartDateIso         | Servlet tạo (yyyy-MM-dd)| Giá trị khởi tạo cho input type="date"
 defaultEndDateText          | Servlet định dạng       | Ngày hết hạn dự kiến theo gói
 minStartDateIso             | Servlet tạo             | Giới hạn min của input date (hôm nay)
 maxStartDateIso             | Servlet tạo             | Giới hạn max của input date (hôm nay + 7 ngày)
 csrfToken                   | RequestUtil tạo         | Token CSRF bảo mật trong hidden field
 -------------------------------------------------------------------------------
 ĐIỀU HƯỚNG & GIAO DIỆN (THEME SÁNG / TỐI CHUẨN ARENA):
   - Nút quay lại: đặt ở thanh trên cùng toàn trang (cả topbar và thanh điều hướng).
   - Không đặt nút "Đổi khách" / "Đổi gói" rời rạc trong từng thẻ.
   - Cột trái: Thông tin hội viên, Gói & Hiệu lực kích hoạt, Phương thức thanh toán.
   - Cột phải: Hóa đơn thanh toán POS dùng token CSS --arena-* tự động đổi sáng/tối.
================================================================================ --%>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<!DOCTYPE html>
<html lang="vi">
<head>
    <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
    <title>Xác nhận thanh toán · Super Gym Lễ tân</title>
    <link rel="stylesheet" href="${ctx}/css/pages/receptionist/counter-sale.css?v=20261031_vTidy">
    <script src="${ctx}/js/pages/receptionist/counter-sale.js?v=20261030_vVoucherClean" defer></script>
</head>
<body class="dock theme-staff counter-sale-page">
<%@ include file="/WEB-INF/jsp/common/flash-toast.jspf" %>
<div class="app">
    <%@ include file="/WEB-INF/jsp/common/layout-receptionist.jspf" %>
    <div class="main">
        <header class="topbar">
            <div class="tb-left">
                <a class="btn btn-ghost btn-sm counter-topbar-back" href="${ctx}/receptionist/sales?package=<c:out value='${selectedCard.packageId}' />" title="Quay lại bước chọn khách">
                    <i class="fa-solid fa-arrow-left"></i> Quay lại
                </a>
                <h1 class="tb-title">Xác nhận thanh toán</h1>
            </div>
            <div class="top-right">
                <a class="tb-user" href="${ctx}/account" title="Cài đặt tài khoản">
                    <span class="avatar">LT</span>
                    <span class="tb-user-text"><b><c:out value="${sessionScope.displayName}" default="Lễ tân" /></b><small>Lễ tân quầy</small></span>
                </a>
            </div>
        </header>

        <main class="content counter-sale" id="main-content" tabindex="-1">
            <%-- Chỉ báo bước quy trình --%>
            <div class="counter-top-bar">
                <span class="counter-step-hint">Bước 3 / 3: Xác nhận đơn hàng &amp; Thu tiền</span>
            </div>

            <%-- Form thu tiền tại quầy: counter-sale.js hàm initCheckoutSubmit() gắn sự kiện submit chống bấm đúp --%>
            <form method="post" action="${ctx}/receptionist/sales" id="checkoutForm">
                <input type="hidden" name="csrfToken" value="<c:out value='${csrfToken}' />">
                <input type="hidden" name="packageId" value="<c:out value='${selectedCard.packageId}' />">
                <input type="hidden" name="customerId" value="<c:out value='${selectedCustomer.customerId}' />">

                <div class="counter-layout">

                    <%-- ===== CỘT TRÁI: THIẾT LẬP & XÁC NHẬN ĐƠN HÀNG ===== --%>
                    <section class="counter-block counter-checkout-block" id="checkoutSection">

                        <%-- ===== Khối 1: Thông tin hội viên (selectedCustomer: CounterCustomerRow) ===== --%>
                        <div class="counter-section">
                            <div class="counter-section-header">
                                <span class="counter-step-num">1</span>
                                <h2 class="counter-section-title">Thông tin hội viên</h2>
                            </div>
                            <div class="counter-customer-card">
                                <div class="counter-avatar-badge" aria-hidden="true">
                                    <i class="fa-solid fa-user-check"></i>
                                </div>
                                <div class="counter-customer-details">
                                    <div class="customer-name-row">
                                        <span class="customer-name"><c:out value="${selectedCustomer.fullName}" /></span>
                                        <span class="badge ${selectedCustomer.statusBadgeClass}"><c:out value="${selectedCustomer.status.displayName}" /></span>
                                        <c:if test="${selectedCustomer.dependent}">
                                            <span class="badge badge-info">Hồ sơ phụ thuộc</span>
                                        </c:if>
                                    </div>
                                    <div class="customer-meta-row">
                                        <span><i class="fa-solid fa-id-card"></i> Mã KH: <strong><c:out value="${selectedCustomer.customerCode}" /></strong></span>
                                        <span><i class="fa-solid fa-phone"></i> SĐT: <strong><c:out value="${selectedCustomer.phoneText}" /></strong></span>
                                    </div>
                                </div>
                            </div>
                        </div>

                        <%-- ===== Khối 2: Gói dịch vụ & Hiệu lực thẻ (selectedCard: CounterPackageCard, isChained: boolean) ===== --%>
                        <div class="counter-section">
                            <div class="counter-section-header">
                                <span class="counter-step-num">2</span>
                                <h2 class="counter-section-title">Gói dịch vụ &amp; Hiệu lực thẻ</h2>
                            </div>

                            <%-- Chi tiết mặt hàng --%>
                            <div class="counter-package-detail-card">
                                <div class="package-main-info">
                                    <span class="package-type-pill"><c:out value="${selectedCard.typeLabel}" /></span>
                                    <h3 class="package-title"><c:out value="${selectedCard.packageName}" /></h3>
                                    <p class="package-duration-info">
                                        Thời hạn sử dụng: <strong><c:out value="${selectedCard.durationText}" /></strong>
                                    </p>
                                    <c:if test="${not empty selectedCard.benefitLines}">
                                        <ul class="package-benefit-mini-list">
                                            <c:forEach var="benefit" items="${selectedCard.benefitLines}">
                                                <li><i class="fa-solid fa-check"></i> <c:out value="${benefit}" /></li>
                                            </c:forEach>
                                        </ul>
                                    </c:if>
                                </div>
                            </div>

                            <%-- Cấu hình thời gian kích hoạt --%>
                            <div class="counter-validity-config">
                                <span class="config-label">Thời gian kích hoạt &amp; hết hạn gói:</span>

                                <c:choose>
                                    <%-- TRƯỜNG HỢP A: KHÁCH ĐANG CÓ GÓI CÒN HẠN -> BẮT BUỘC NỐI TIẾP --%>
                                    <c:when test="${isChained}">
                                        <input type="hidden" name="startDateMode" value="chained">
                                        <div class="validity-chained-notice">
                                            <i class="fa-solid fa-link"></i>
                                            <div>
                                                <p class="notice-title">Tự động kích hoạt nối tiếp sau gói hiện tại</p>
                                                <p class="notice-sub">Hội viên đang có gói còn hạn đến ngày <strong><c:out value="${selectedCustomer.currentPackageEndText}" /></strong>. Gói mới sẽ tự động bắt đầu từ ngày hôm sau để không gián đoạn quyền lợi.</p>
                                            </div>
                                        </div>
                                        <div class="validity-dates-preview">
                                            <div class="date-chip">
                                                <span class="date-label">Ngày bắt đầu:</span>
                                                <span class="date-value"><c:out value="${defaultStartDateText}" /></span>
                                            </div>
                                            <div class="date-arrow"><i class="fa-solid fa-arrow-right"></i></div>
                                            <div class="date-chip highlight">
                                                <span class="date-label">Ngày hết hạn dự kiến:</span>
                                                <span class="date-value"><c:out value="${defaultEndDateText}" /></span>
                                            </div>
                                        </div>
                                    </c:when>

                                    <%-- TRƯỜNG HỢP B: KHÁCH MỚI HOẶC CHƯA CÓ GÓI -> CHO CHỌN HÔM NAY HOẶC TRONG 7 NGÀY --%>
                                    <c:otherwise>
                                        <%-- id="validityPickerOptions" được dùng trong counter-sale.js, hàm initValidityPicker() --%>
                                        <div class="validity-picker-options" id="validityPickerOptions"
                                             data-duration-months="<c:out value='${selectedCard.durationMonths}' />"
                                             data-today-iso="<c:out value='${defaultStartDateIso}' />"
                                             data-today-text="<c:out value='${defaultStartDateText}' />"
                                             data-default-end-text="<c:out value='${defaultEndDateText}' />">
                                            <label class="validity-option-radio">
                                                <input type="radio" name="startDateMode" id="modeToday" value="today" checked>
                                                <span class="radio-label">
                                                    <strong>Kích hoạt ngay hôm nay</strong> (<c:out value="${todayText}" />)
                                                </span>
                                            </label>
                                            <label class="validity-option-radio">
                                                <input type="radio" name="startDateMode" id="modeCustom" value="custom">
                                                <span class="radio-label">
                                                    <strong>Hẹn ngày kích hoạt khác</strong> (trong 7 ngày tới)
                                                </span>
                                            </label>
                                        </div>

                                        <%-- id="customDateWrap" được dùng trong counter-sale.js, hàm initValidityPicker() để toggle ẩn/hiện --%>
                                        <div class="validity-custom-input" id="customDateWrap" hidden>
                                            <label for="customStartDate" class="field-label">Chọn ngày bắt đầu:</label>
                                            <input type="date" id="customStartDate" name="customStartDate"
                                                   value="<c:out value='${defaultStartDateIso}' />"
                                                   min="<c:out value='${minStartDateIso}' />"
                                                   max="<c:out value='${maxStartDateIso}' />">
                                            <span class="field-hint">Chỉ được chọn từ hôm nay đến <c:out value="${maxStartDateIso}" /></span>
                                        </div>

                                        <div class="validity-dates-preview">
                                            <div class="date-chip">
                                                <span class="date-label">Ngày bắt đầu:</span>
                                                <span class="date-value" id="previewStartDate"><c:out value="${defaultStartDateText}" /></span>
                                            </div>
                                            <div class="date-arrow"><i class="fa-solid fa-arrow-right"></i></div>
                                            <div class="date-chip highlight">
                                                <span class="date-label">Ngày hết hạn dự kiến:</span>
                                                <span class="date-value" id="previewEndDate"><c:out value="${defaultEndDateText}" /></span>
                                            </div>
                                        </div>
                                    </c:otherwise>
                                </c:choose>
                            </div>
                        </div>

                        <%-- ===== Khối 3: Phương thức thu tiền (paymentMethod: Cash / Transfer) ===== --%>
                        <div class="counter-section">
                            <div class="counter-section-header">
                                <span class="counter-step-num">3</span>
                                <h2 class="counter-section-title">Phương thức thanh toán</h2>
                            </div>
                            <%-- id="paymentMethodsGroup" được dùng trong counter-sale.js, hàm initPaymentMethodToggle() --%>
                            <div class="counter-payment-radios" id="paymentMethodsGroup">
                                <label class="counter-payment-card active">
                                    <input type="radio" name="paymentMethod" value="Cash" checked>
                                    <div class="payment-card-content">
                                        <i class="fa-solid fa-money-bill-wave payment-icon is-cash"></i>
                                        <div>
                                            <span class="payment-name">Tiền mặt</span>
                                            <span class="payment-sub">Lễ tân đếm tiền mặt tại quầy</span>
                                        </div>
                                    </div>
                                </label>
                                <label class="counter-payment-card">
                                    <input type="radio" name="paymentMethod" value="Transfer">
                                    <div class="payment-card-content">
                                        <i class="fa-solid fa-building-columns payment-icon is-transfer"></i>
                                        <div>
                                            <span class="payment-name">Chuyển khoản / Quẹt thẻ POS</span>
                                            <span class="payment-sub">Quét VietQR hoặc máy POS quầy</span>
                                        </div>
                                    </div>
                                </label>
                            </div>
                        </div>

                    </section>

                    <%-- ===== CỘT PHẢI: TÓM TẮT THANH TOÁN (selectedCard: CounterPackageCard) ===== --%>
                    <aside class="counter-summary counter-receipt-aside" aria-label="Thông tin thanh toán">
                        <div class="counter-summary-header">
                            <p class="counter-summary-title"><i class="fa-solid fa-receipt"></i> Thông tin thanh toán</p>
                            <span class="counter-summary-subtitle">Xác nhận thu tiền tại quầy</span>
                        </div>

                        <div class="counter-summary-body">
                            <%-- Chi tiết mặt hàng trên bill --%>
                            <div class="receipt-item-line">
                                <div class="receipt-item-name">
                                    <strong><c:out value="${selectedCard.packageName}" /></strong>
                                    <span class="receipt-item-sub"><c:out value="${selectedCard.durationText}" /></span>
                                </div>
                                <div class="receipt-item-price">
                                    <c:out value="${gms:formatMoney(selectedCard.price)}" />
                                </div>
                            </div>

                            <%-- Voucher giảm giá --%>
                            <div class="counter-summary-voucher">
                                <label for="voucherCode" class="voucher-label">Mã ưu đãi / Voucher</label>
                                <div class="counter-voucher-input-wrap">
                                    <input type="text" id="voucherCode" name="voucher" placeholder="NHẬP MÃ NẾU CÓ..." autocomplete="off">
                                    <button type="button" id="voucherClearBtn" class="voucher-clear-btn" aria-label="Xóa mã" title="Xóa mã" hidden>&times;</button>
                                </div>
                            </div>

                            <div class="counter-summary-separator"></div>

                            <%-- Bảng tính chi tiết tiền --%>
                            <dl class="counter-summary-list">
                                <div>
                                    <dt>Giá niêm yết</dt>
                                    <dd><c:out value="${gms:formatMoney(selectedCard.price)}" /></dd>
                                </div>
                                <div>
                                    <dt>Trước thuế VAT</dt>
                                    <dd><c:out value="${gms:formatMoney(selectedCard.preTaxAmount)}" /></dd>
                                </div>
                                <div>
                                    <dt>Thuế VAT <c:out value="${selectedCard.vatRate}" />% (đã gồm)</dt>
                                    <dd><c:out value="${gms:formatMoney(selectedCard.vatAmount)}" /></dd>
                                </div>
                                <div class="is-total">
                                    <dt>Tổng phải thu</dt>
                                    <dd class="total-amount"><c:out value="${gms:formatMoney(selectedCard.price)}" /></dd>
                                </div>
                            </dl>
                        </div>

                        <div class="counter-summary-footer">
                            <%-- id="confirmPaymentBtn" được dùng trong counter-sale.js, hàm initCheckoutSubmit() để khóa nút và hiện spinner --%>
                            <button class="counter-summary-btn" type="submit" id="confirmPaymentBtn">
                                <i class="fa-solid fa-circle-check"></i> Xác nhận đã thu <c:out value="${gms:formatMoney(selectedCard.price)}" />
                            </button>
                        </div>
                    </aside>

                </div>
            </form>
        </main>
    </div>
</div>
</body>
</html>
