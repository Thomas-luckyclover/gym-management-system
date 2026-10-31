<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="gms" uri="/WEB-INF/gms-functions.tld" %>
<%--
================================================================================
 counter-sale-receipt.jsp — BIÊN LAI THU TIỀN TẠI QUẦY (/receptionist/sales?receipt=…)
 HIỂN THỊ SAU KHI LỄ TÂN XÁC NHẬN THU TIỀN THÀNH CÔNG:
   - Thông tin biên lai: Mã đơn, Khách hàng, Gói, Hiệu lực, Hình thức, Số tiền.
   - Nút: "In biên lai" (window.print) & "Bán đơn mới" (quay lại /receptionist/sales).
 BIẾN: receipt (CounterSaleReceipt)
================================================================================ --%>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<!DOCTYPE html>
<html lang="vi">
<head>
    <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
    <title>Phiếu thu dịch vụ #<c:out value="${receipt.orderCode}" /> · Super Gym Lễ tân</title>
    <link rel="stylesheet" href="${ctx}/css/pages/receptionist/counter-sale.css?v=20261031_vVoucherDone">
</head>
<body class="dock theme-staff counter-sale-page">
<%@ include file="/WEB-INF/jsp/common/flash-toast.jspf" %>
<div class="app">
    <%@ include file="/WEB-INF/jsp/common/layout-receptionist.jspf" %>
    <div class="main">
        <header class="topbar no-print">
            <div class="tb-left">
                <h1 class="tb-title">Phiếu thu dịch vụ</h1>
            </div>
            <div class="top-right">
                <a class="tb-user" href="${ctx}/account" title="Cài đặt tài khoản">
                    <span class="avatar">LT</span>
                    <span class="tb-user-text"><b><c:out value="${sessionScope.displayName}" default="Lễ tân" /></b><small>Lễ tân quầy</small></span>
                </a>
            </div>
        </header>

        <main class="content counter-sale" id="main-content" tabindex="-1">
            <div class="receipt-container">

                <%-- ===== Khối 1: Banner thông báo thành công (no-print) ===== --%>
                <div class="receipt-success-banner no-print">
                    <div class="receipt-success-icon"><i class="fa-solid fa-circle-check"></i></div>
                    <div class="receipt-success-text">
                        <h2>Giao dịch hoàn tất!</h2>
                        <p>Đã ghi nhận thanh toán và kích hoạt gói tập thành công trên hệ thống.</p>
                    </div>
                </div>

                <%-- ===== Khối 2: Thẻ phiếu thu / Biên lai in nhiệt (receipt: CounterSaleReceipt) ===== --%>
                <div class="pos-receipt-card" id="printableReceipt">
                    <div class="receipt-brand">
                        <i class="fa-solid fa-dumbbell receipt-brand-icon"></i>
                        <h3 class="receipt-brand-name">SUPER GYM FITNESS</h3>
                        <p class="receipt-brand-sub">Phiếu thu dịch vụ tại quầy</p>
                    </div>

                    <div class="receipt-dashed-line"></div>

                    <div class="receipt-info-grid">
                        <div class="receipt-row">
                            <span class="receipt-label">Mã đơn hàng:</span>
                            <strong class="receipt-val"><c:out value="${receipt.orderCode}" /></strong>
                        </div>
                        <div class="receipt-row">
                            <span class="receipt-label">Thời gian thu:</span>
                            <span class="receipt-val"><c:out value="${receipt.paidAtText}" /></span>
                        </div>
                        <div class="receipt-row">
                            <span class="receipt-label">Lễ tân thu tiền:</span>
                            <span class="receipt-val"><c:out value="${receipt.receptionistName}" /></span>
                        </div>
                        <div class="receipt-row">
                            <span class="receipt-label">Hình thức:</span>
                            <span class="receipt-val"><c:out value="${receipt.paymentMethodText}" /></span>
                        </div>
                    </div>

                    <div class="receipt-dashed-line"></div>

                    <div class="receipt-info-grid">
                        <div class="receipt-row">
                            <span class="receipt-label">Hội viên:</span>
                            <strong class="receipt-val"><c:out value="${receipt.customerName}" /></strong>
                        </div>
                        <div class="receipt-row">
                            <span class="receipt-label">Mã KH / SĐT:</span>
                            <span class="receipt-val"><c:out value="${receipt.customerCode}" /> · <c:out value="${receipt.phoneText}" /></span>
                        </div>
                        <div class="receipt-row">
                            <span class="receipt-label">Gói đăng ký:</span>
                            <strong class="receipt-val"><c:out value="${receipt.packageName}" /> (<c:out value="${receipt.durationText}" />)</strong>
                        </div>
                        <div class="receipt-row">
                            <span class="receipt-label">Hiệu lực gói:</span>
                            <span class="receipt-val"><c:out value="${receipt.startDateText}" /> → <c:out value="${receipt.endDateText}" /></span>
                        </div>
                        <c:if test="${receipt.discountAmount > 0}">
                            <div class="receipt-row">
                                <span class="receipt-label">Giảm giá (<c:out value="${receipt.voucherCode}" />):</span>
                                <span class="receipt-val receipt-val-discount">-<c:out value="${gms:formatMoney(receipt.discountAmount)}" /></span>
                            </div>
                        </c:if>
                    </div>

                    <div class="receipt-dashed-line"></div>

                    <div class="receipt-total-row">
                        <span class="receipt-total-label">TỔNG ĐÃ THU:</span>
                        <span class="receipt-total-val"><c:out value="${gms:formatMoney(receipt.finalAmount)}" /></span>
                    </div>

                    <div class="receipt-footer-note">
                        <p>Cảm ơn quý khách đã đồng hành cùng Super Gym!</p>
                        <p><small>Vui lòng giữ phiếu thu để đối chiếu khi cần thiết.</small></p>
                    </div>
                </div>

                <%-- ===== Khối 3: Nút thao tác In / Bán đơn mới (no-print) ===== --%>
                <div class="receipt-actions no-print">
                    <button type="button" class="btn btn-outline" onclick="window.print()">
                        <i class="fa-solid fa-print"></i> In phiếu thu
                    </button>
                    <a class="btn btn-primary" href="${ctx}/receptionist/sales">
                        <i class="fa-solid fa-plus"></i> Bán đơn mới
                    </a>
                </div>

            </div>
        </main>
    </div>
</div>
</body>
</html>
