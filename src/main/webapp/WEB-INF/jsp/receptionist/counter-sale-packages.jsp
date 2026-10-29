<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="gms" uri="/WEB-INF/gms-functions.tld" %>
<%--
================================================================================
 counter-sale-packages.jsp — BÁN GÓI TẠI QUẦY, BƯỚC 1: CHỌN GÓI (/receptionist/sales), SRS #37

 AI GỬI DỮ LIỆU SANG?  CounterSaleServlet.showPackages gọi CounterSaleService.getPackageView một lần.
 BIẾN     packageView (CounterPackageView: gymCards, ptCards, sessionCards), pageError (Service lỗi)
 LUỒNG    Chọn gói → Chọn khách → Voucher → Thanh toán
 BỐ CỤC   thanh công cụ (3 tab · ô tìm gói lọc ngay trên trình duyệt) · lưới thẻ 4 cột · thanh "Đang chọn" ở dưới
 JS       counter-sale.js: filterPackageCards (ô tìm), handlePackageChange (chọn thẻ), handleContinueClick (nút Tiếp tục)
================================================================================ --%>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<!DOCTYPE html>
<html lang="vi">
<head>
    <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
    <title>Bán hàng · Super Gym Lễ tân</title>
    <link rel="stylesheet" href="${ctx}/css/pages/receptionist/counter-sale.css?v=20261030a">
    <script src="${ctx}/js/pages/receptionist/counter-sale.js?v=20261029b" defer></script>
</head>
<body class="dock theme-staff counter-sale-page">
<%@ include file="/WEB-INF/jsp/common/flash-toast.jspf" %>
<div class="app">
    <%@ include file="/WEB-INF/jsp/common/layout-receptionist.jspf" %>
    <div class="main">
        <header class="topbar">
            <div class="tb-left">
                <h1 class="tb-title">Chọn gói cho khách</h1>
            </div>
            <div class="top-right">
                <a class="tb-user" href="${ctx}/account" title="Cài đặt tài khoản">
                    <span class="avatar">LT</span>
                    <span class="tb-user-text"><b><c:out value="${sessionScope.displayName}" default="Lễ tân" /></b><small>Lễ tân quầy</small></span>
                </a>
            </div>
        </header>

        <main class="content counter-sale" id="main-content" tabindex="-1">
        <c:choose>
            <%-- ===== Service lỗi: hiện thông báo thân thiện, không hiện danh sách rỗng giả ===== --%>
            <c:when test="${not empty pageError}">
                <section class="counter-block counter-alert" role="alert">
                    <p><c:out value="${pageError}" /></p>
                    <a class="btn btn-outline" href="${ctx}/receptionist/sales">Tải lại</a>
                </section>
            </c:when>

            <c:otherwise>
                <%-- ===== Thanh công cụ: 3 tab bên trái, ô tìm gói bên phải (màn hẹp thì ô tìm xuống dòng dưới) =====
                     Tab: common.js bindTabs() đổi tab theo data-tab / data-tab-panel.
                     data-tip: lời nhắc hiện khi rê chuột / focus vào tab (CSS ::after), thay cho khung ghi chú cũ.
                     Ô tìm: counter-sale.js hàm filterPackageCards() lọc thẻ theo tên, không phân biệt dấu. ===== --%>
                <div class="counter-toolbar">
                    <div class="counter-tabs" role="tablist" aria-label="Nhóm gói">
                        <button class="counter-tab" type="button" role="tab" data-tab="gym" aria-selected="true">
                            Gói tập <span class="counter-tab-count"><c:out value="${packageView.gymCards.size()}" /></span>
                        </button>
                        <button class="counter-tab" type="button" role="tab" data-tab="pt" aria-selected="false"
                                data-tip="Gói PT chỉ bán cho khách đang có gói tập còn hạn." aria-description="Gói PT chỉ bán cho khách đang có gói tập còn hạn.">
                            Gói PT <span class="counter-tab-count"><c:out value="${packageView.ptCards.size()}" /></span>
                            <i class="fa-solid fa-circle-info counter-tab-info" aria-hidden="true"></i>
                        </button>
                        <button class="counter-tab" type="button" role="tab" data-tab="session" aria-selected="false"
                                data-tip="Khách vãng lai: bán vé ngày hôm nay trước, rồi mới bán buổi PT lẻ." aria-description="Khách vãng lai: bán vé ngày hôm nay trước, rồi mới bán buổi PT lẻ.">
                            Buổi PT lẻ <span class="counter-tab-count"><c:out value="${packageView.sessionCards.size()}" /></span>
                            <i class="fa-solid fa-circle-info counter-tab-info" aria-hidden="true"></i>
                        </button>
                    </div>

                    <div class="counter-search">
                        <label class="visually-hidden" for="packageSearch">Tìm gói theo tên</label>
                        <i class="fa-solid fa-magnifying-glass" aria-hidden="true"></i>
                        <input id="packageSearch" type="search" placeholder="Tìm gói theo tên…" autocomplete="off" spellcheck="false">
                    </div>
                </div>

                <%-- ===== Tab Gói tập: gói tháng rồi vé ngày. Nút Tiếp tục đi sang bước Chọn khách của chính màn này. ===== --%>
                <section class="counter-panel" data-tab-panel="gym">
                    <c:set var="continueBase" value="${ctx}/receptionist/sales" />
                    <c:set var="continueLabel" value="Tiếp tục: tìm khách →" />
                    <c:choose>
                        <c:when test="${empty packageView.gymCards}">
                            <p class="counter-empty">Chưa có gói tập nào đang bán.</p>
                        </c:when>
                        <c:otherwise>
                            <div class="counter-grid">
                                <c:forEach var="card" items="${packageView.gymCards}">
                                    <%@ include file="/WEB-INF/jsp/receptionist/_counter-card.jspf" %>
                                </c:forEach>
                            </div>
                        </c:otherwise>
                    </c:choose>
                    <div class="counter-empty" data-filter-empty hidden></div>
                </section>

                <%-- ===== Tab Gói PT: bán ở màn PtSaleServlet đã có (/receptionist/pt-sales) ===== --%>
                <section class="counter-panel" data-tab-panel="pt" hidden>
                    <c:set var="continueBase" value="${ctx}/receptionist/pt-sales" />
                    <c:set var="continueLabel" value="Tiếp tục: bán gói PT →" />
                    <c:choose>
                        <c:when test="${empty packageView.ptCards}">
                            <p class="counter-empty">Chưa có gói PT nào đang bán.</p>
                        </c:when>
                        <c:otherwise>
                            <div class="counter-grid">
                                <c:forEach var="card" items="${packageView.ptCards}">
                                    <%@ include file="/WEB-INF/jsp/receptionist/_counter-card.jspf" %>
                                </c:forEach>
                            </div>
                        </c:otherwise>
                    </c:choose>
                    <div class="counter-empty" data-filter-empty hidden></div>
                </section>

                <%-- ===== Tab Buổi PT lẻ: chưa có màn bán tại quầy nên thẻ bị khóa (continueAllowed = false) ===== --%>
                <section class="counter-panel" data-tab-panel="session" hidden>
                    <c:set var="continueBase" value="${ctx}/receptionist/sales" />
                    <c:set var="continueLabel" value="Tiếp tục" />
                    <c:choose>
                        <c:when test="${empty packageView.sessionCards}">
                            <p class="counter-empty">Chưa có buổi PT lẻ nào đang bán.</p>
                        </c:when>
                        <c:otherwise>
                            <div class="counter-grid">
                                <c:forEach var="card" items="${packageView.sessionCards}">
                                    <%@ include file="/WEB-INF/jsp/receptionist/_counter-card.jspf" %>
                                </c:forEach>
                            </div>
                        </c:otherwise>
                    </c:choose>
                    <div class="counter-empty" data-filter-empty hidden></div>
                </section>

                <%-- ===== Thanh dưới: gói đang chọn + nút Tiếp tục (counter-sale.js cập nhật #selectedSummary, #continueButton) ===== --%>
                <div class="counter-bar" role="region" aria-label="Gói đang chọn">
                    <p class="counter-bar-text" id="selectedSummary" aria-live="polite">Chưa chọn gói nào</p>
                    <button class="btn btn-primary" type="button" id="continueButton" disabled>Tiếp tục: tìm khách →</button>
                </div>
            </c:otherwise>
        </c:choose>
        </main>
    </div>
</div>
</body>
</html>