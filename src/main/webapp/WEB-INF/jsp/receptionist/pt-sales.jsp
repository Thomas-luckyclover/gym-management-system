<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="gms" uri="/WEB-INF/gms-functions.tld" %>
<%--
================================================================================
 pt-sales.jsp — MÀN LỄ TÂN BÁN GÓI PT (/receptionist/pt-sales), UC-21 bản đơn giản hóa

 AI GỬI DỮ LIỆU SANG?  PtSaleServlet.showPage gọi PtSaleService.prepare MỘT lần → "sale" (PtSaleView).
 LUỒNG — lễ tân TƯ VẤN TRƯỚC, chọn khách lúc nào cũng được (góp ý của cô 26/10, kiểu máy POS: hàng trái, giỏ phải)
   Trái  1. Gói & PT     ô gói, ô PT, ngày bắt đầu (form GET, đổi là tự gửi) — chưa cần biết khách
         2. Lịch tập     pt-sale-plan.js: lịch mẫu hằng tuần → xem từng tuần → sửa riêng 1 buổi
   Phải  3. Khách        ô tìm gợi ý (pt-sales.js) → URL thêm customer=…, GIỮ NGUYÊN gói / PT / lịch đang chọn
         4. Thu tiền     "Giữ lịch 15 phút" (cần đủ khách + lịch) → ?order=… : đồng hồ, thu tiền / hủy giữ
 BỐ CỤC   thanh bước trên cùng · trái: tư vấn · phải: "Đơn bán" (khách + tóm tắt + nút) dính khi cuộn
 BIẾN     sale, pageError (Service lỗi), saleError (bấm Giữ lịch bị từ chối), csrfToken
================================================================================ --%>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<c:set var="base" value="${ctx}/receptionist/pt-sales" />
<!DOCTYPE html>
<html lang="vi">
<head>
    <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
    <title>Bán gói PT · Super Gym Lễ tân</title>
    <link rel="stylesheet" href="${ctx}/css/pages/receptionist/pt-sales.css?v=20261031b">
    <script src="${ctx}/js/pages/receptionist/pt-sales.js?v=20261026s" defer></script>
    <script src="${ctx}/js/pages/receptionist/pt-sale-plan.js?v=20261031a" defer></script>
</head>
<body class="dock theme-staff pt-sale-page">
<%@ include file="/WEB-INF/jsp/common/flash-toast.jspf" %>
<div class="app">
    <%@ include file="/WEB-INF/jsp/common/layout-receptionist.jspf" %>
    <div class="main">
        <header class="topbar">
            <div class="tb-left">
                <h1 class="tb-title">Bán gói PT</h1>
                <%-- Hint box chung (.pt-info, common.js điều khiển): bấm / di chuột vào nút i để đọc. --%>
                <span class="pt-info">
                    <button type="button" class="pt-info-trigger" aria-label="Hướng dẫn Bán gói PT" aria-describedby="sale-help"><span aria-hidden="true">i</span></button>
                    <span id="sale-help" class="pt-info-popover" role="tooltip">
                        <strong>1. Gói &amp; PT:</strong> tư vấn trước, chưa cần biết khách. Ngày bắt đầu từ hôm nay tới 30 ngày sau.<br>
                        <strong>2. Lịch tập:</strong> mỗi thứ chọn Nghỉ hoặc 1 giờ; xem từng tuần, bấm 1 ngày để sửa riêng ngày đó.<br>
                        <strong>3. Khách:</strong> ô tìm ở cột Đơn bán — tên không dấu, SĐT hoặc mã KH; chọn lúc nào cũng được, lịch giữ nguyên. Gói đôi cần thêm người tập cùng.<br>
                        <strong>4. Giữ chỗ:</strong> bấm "Giữ lịch 15 phút" trước khi thu tiền để không ai lấy mất khung. Quá 15 phút chưa thu tiền thì lịch tự trả lại cho PT.
                    </span>
                </span>
            </div>
            <div class="top-right">
                <%@ include file="/WEB-INF/jsp/common/topbar-user.jspf" %>
            </div>
        </header>

        <main class="content sale" id="main-content" tabindex="-1">
        <c:choose>
        <c:when test="${not empty pageError}">
            <section class="sale-card sale-alert" role="alert">
                <p><c:out value="${pageError}" /></p>
                <a class="btn btn-outline" href="${base}">Bán gói mới</a>
            </section>
        </c:when>
        <c:otherwise>
            <%-- holderOk: có khách mua được · customerReady: đủ khách để giữ lịch (gói đôi cần cả người tập cùng)
                 done2: đã chọn đủ gói + PT + ngày bắt đầu (có kỳ) --%>
            <c:set var="holderOk" value="${not empty sale.holder and empty sale.holder.blockReason}" />
            <c:set var="customerReady" value="${holderOk and (not sale.couple or (not empty sale.partner and empty sale.partner.blockReason))}" />
            <c:set var="done2" value="${not empty sale.endDate}" />

            <%-- ===== ĐƠN ĐANG GIỮ CHỖ (lễ tân bấm Back / đăng xuất rồi quay lại vẫn tìm được đơn chưa thu tiền) ===== --%>
            <c:if test="${not empty sale.pendingHolds}">
                <section class="sale-holds" aria-label="Đơn đang giữ chỗ">
                    <p class="sale-holds-title"><i class="fa-solid fa-hourglass-half" aria-hidden="true"></i> Đơn đang giữ chỗ — tiếp tục thu tiền trước khi hết 15 phút:</p>
                    <ul class="sale-holds-list">
                        <c:forEach var="hold" items="${sale.pendingHolds}">
                            <li>
                                <span class="sale-holds-info">
                                    <span class="sale-holds-code"><c:out value="${hold.orderCode}" /></span>
                                    <span class="sale-holds-time">còn <b data-countdown-seconds="${hold.secondsLeft}">--:--</b></span>
                                </span>
                                <a class="btn btn-primary btn-sm" href="${base}?order=${hold.orderId}">Tiếp tục thu tiền</a>
                            </li>
                        </c:forEach>
                    </ul>
                </section>
            </c:if>

            <%-- ===== THANH BƯỚC ===== --%>
            <ol class="sale-stepper" aria-label="Các bước bán gói">
                <li class="${done2 or sale.held ? 'is-done' : 'is-current'}"><span>1</span> Gói &amp; PT</li>
                <li class="${sale.held ? 'is-done' : (done2 ? 'is-current' : '')}" data-step-plan><span>2</span> Lịch tập</li>
                <li class="${customerReady or sale.held ? 'is-done' : (done2 ? 'is-current' : '')}"><span>3</span> Khách</li>
                <li class="${sale.held ? 'is-current' : ''}"><span>4</span> Giữ chỗ &amp; thu tiền</li>
            </ol>

            <div class="sale-layout">
            <div class="sale-flow">

            <%-- =====================================================================
                 BƯỚC 1. GÓI, PT, NGÀY BẮT ĐẦU — form GET, đổi là pt-sales.js tự gửi. Chưa cần khách.
                 ===================================================================== --%>
            <c:if test="${not sale.held}">
            <form id="sale-form" class="sale-flow" method="get" action="${base}" data-auto-submit>
                <input type="hidden" name="customer" value="${sale.customerId}">
                <input type="hidden" name="partner" value="${sale.partnerId}">
                <section class="sale-card" aria-labelledby="sale-h2">
                    <header class="sale-card-head"><h2 id="sale-h2">Gói &amp; PT</h2>
                        <c:if test="${done2}"><p><c:out value="${gms:formatDate(sale.startDate)}" /> – <c:out value="${gms:formatDate(sale.endDate)}" /> · <c:out value="${sale.months}" /> tháng</p></c:if></header>

                    <fieldset class="sale-packages">
                        <legend class="sale-field-label">Gói</legend>
                        <div class="sale-tiles">
                        <c:forEach var="o" items="${sale.packages}">
                            <label class="sale-tile">
                                <input type="radio" name="package" value="${o.value}" ${o.value eq sale.packageId ? 'checked' : ''}>
                                <strong><c:out value="${o.label}" /></strong>
                                <small><c:out value="${o.note}" /></small>
                            </label>
                        </c:forEach>
                        </div>
                    </fieldset>

                    <c:if test="${not empty sale.pts}">
                        <fieldset>
                            <legend class="sale-field-label">PT khách chọn</legend>
                            <div class="sale-pts">
                            <c:forEach var="o" items="${sale.pts}">
                                <label class="sale-pt">
                                    <input type="radio" name="pt" value="${o.value}" ${o.value eq sale.ptId ? 'checked' : ''}>
                                    <span class="sale-avatar" aria-hidden="true"><c:out value="${o.note}" /></span>
                                    <span><c:out value="${o.label}" /></span>
                                </label>
                            </c:forEach>
                            </div>
                        </fieldset>
                        <label class="sale-field sale-start"><span class="sale-field-label">Ngày bắt đầu kỳ</span>
                            <input name="start" type="date" value="${sale.startDate}" min="${sale.minStart}" max="${sale.maxStart}"
                                   aria-describedby="sale-start-error" data-start-field>
                        </label>
                        <%-- pt-sales.js hiện câu này khi ngày gõ vào nằm ngoài min–max (form KHÔNG gửi đi). --%>
                        <p class="sale-msg is-error" id="sale-start-error" role="alert" hidden>Ngày bắt đầu phải từ <c:out value="${gms:formatDate(sale.minStart)}" /> đến <c:out value="${gms:formatDate(sale.maxStart)}" />.</p>
                    </c:if>
                    <c:if test="${not empty sale.choiceError}"><p class="sale-msg is-error" role="alert"><c:out value="${sale.choiceError}" /></p></c:if>
                    <noscript><button class="btn btn-outline" type="submit">Tiếp</button></noscript>
                </section>

            </form>
            </c:if>

            <%-- =====================================================================
                 BƯỚC 2. LỊCH TẬP — nằm NGOÀI form các bước (chọn giờ không tải lại trang).
                 Server gửi sale.planJson (PtSalePlan: mỗi ngày T2–T6 × mỗi giờ: tập được không + lý do);
                 pt-sale-plan.js dựng: Lịch mẫu hằng tuần → Từng tuần (tab) → sửa riêng 1 buổi.
                 Gson đã thoát < > & trong JSON nên in thẳng vào <script type="application/json"> an toàn.
                 ===================================================================== --%>
            <c:if test="${not sale.held and done2}">
            <section class="sale-card sale-plan" aria-labelledby="sale-h3">
                <header class="sale-card-head">
                    <h2 id="sale-h3">Lịch tập</h2>
                    <p class="plan-total" data-plan-total aria-live="polite"></p>
                </header>
                <c:choose>
                    <c:when test="${empty sale.planJson}">
                        <p class="sale-empty"><c:out value="${sale.planError}" /></p>
                    </c:when>
                    <c:otherwise>
                        <script type="application/json" id="sale-plan-data">${sale.planJson}</script>
                        <noscript><p class="sale-msg is-error">Cần bật JavaScript để chọn lịch tập.</p></noscript>
                        <%-- Nhắc: Thứ PT chưa công bố khung nào. Việc bắt tích "Đã xác nhận với PT" xét từng buổi (ô "PT chưa mở"). --%>
                        <c:if test="${not empty sale.unpublishedText}">
                            <div class="plan-alert" role="note">
                                <i class="fa-solid fa-triangle-exclamation" aria-hidden="true"></i>
                                <p><b><c:out value="${sale.ptName}" /> chưa công bố khung rảnh <c:out value="${sale.unpublishedText}" /></b> trong 2 tuần đầu.
                                    Nên hỏi PT trước khi thanh toán — buổi ghi <b>"PT chưa mở"</b> cần tích <b>"Đã xác nhận với PT"</b> mới giữ lịch được.</p>
                            </div>
                        </c:if>

                        <div class="plan-block">
                            <div class="plan-block-head">
                                <div class="sale-label-row">
                                    <h3 id="plan-pattern-title">Lịch mẫu hằng tuần</h3>
                                    <span class="pt-info"><button type="button" class="pt-info-trigger" aria-label="Cách chọn lịch mẫu" aria-describedby="plan-pattern-help"><span aria-hidden="true">i</span></button><span id="plan-pattern-help" class="pt-info-popover" role="tooltip">Mỗi thứ chọn <b>Nghỉ</b> hoặc 1 giờ (1–5 buổi/tuần). Giờ nào có ngày không xếp được sẽ ghi <b>"Còn x/9 buổi"</b> và lý do chính; rê chuột vào giờ để xem từng ngày.</span></span>
                                </div>
                            </div>
                            <div class="plan-pattern" data-pattern aria-labelledby="plan-pattern-title"></div>
                        </div>

                        <div class="plan-block">
                            <div class="plan-block-head">
                                <div class="sale-label-row">
                                    <h3 id="plan-week-title">Từng tuần</h3>
                                    <span class="pt-info"><button type="button" class="pt-info-trigger" aria-label="Cách xem và sửa từng tuần" aria-describedby="plan-week-help"><span aria-hidden="true">i</span></button><span id="plan-week-help" class="pt-info-popover" role="tooltip">Bấm số tuần hoặc ‹ › để xem. <b>Bấm 1 ô ngày</b> để đổi giờ hoặc bỏ buổi <b>chỉ ngày đó</b>; các tuần khác giữ lịch mẫu. Chấm đỏ trên số tuần = có ngày chưa xếp được.</span></span>
                                </div>
                                <div class="plan-week-nav">
                                    <button type="button" class="plan-nav" data-week-step="-1" aria-label="Tuần trước"><i class="fa-solid fa-chevron-left" aria-hidden="true"></i></button>
                                    <p class="plan-week-label" data-week-label aria-live="polite"></p>
                                    <button type="button" class="plan-nav" data-week-step="1" aria-label="Tuần sau"><i class="fa-solid fa-chevron-right" aria-hidden="true"></i></button>
                                </div>
                            </div>
                            <div class="plan-tabs" role="tablist" aria-label="Chọn tuần" data-week-tabs></div>
                            <div class="plan-week" role="tabpanel" aria-labelledby="plan-week-title" data-week></div>
                            <div class="plan-editor" data-editor hidden></div>
                            <ul class="plan-key" aria-label="Chú thích">
                                <li class="is-ok">Có buổi</li><li class="is-edit">Đã sửa riêng</li><li class="is-warn">PT cần dời sau</li><li class="is-fresh">PT chưa mở — cần hỏi PT</li><li class="is-bad">Chưa xếp được</li>
                            </ul>
                        </div>
                    </c:otherwise>
                </c:choose>
            </section>
            </c:if>

            <%-- =====================================================================
                 LỊCH ĐANG GIỮ (bước 4): lưới 5 cột T2–T6, mỗi ô 1 buổi đã giữ
                 ===================================================================== --%>
            <c:if test="${sale.held and not empty sale.days}">
                <section class="sale-card" aria-labelledby="sale-h-plan">
                    <header class="sale-card-head"><h2 id="sale-h-plan"><c:out value="${sale.sessionCount}" /> buổi tập
                        <c:if test="${sale.held}"><span class="sale-badge">Đang giữ</span></c:if></h2>
                        <c:if test="${sale.skipCount gt 0}"><p><c:out value="${sale.skipCount}" /> ngày không có buổi (lý do bên dưới)</p></c:if></header>
                    <div class="sale-cal" role="list">
                        <c:forEach var="n" begin="2" end="6"><span class="sale-cal-head" aria-hidden="true">T<c:out value="${n}" /></span></c:forEach>
                        <c:forEach var="d" items="${sale.days}" varStatus="loop">
                            <span role="listitem" <c:if test="${loop.first}">style="grid-column-start: ${d.date.dayOfWeek.value}"</c:if>
                                  class="sale-day ${d.session ? (empty d.warning ? 'is-on' : 'is-warn') : 'is-off'}"
                                  title="${fn:escapeXml(d.session ? (empty d.warning ? d.dayLabel : d.warning) : d.reason)}">
                                <b><c:out value="${fn:substring(gms:formatDate(d.date), 0, 5)}" /></b>
                                <small><c:out value="${d.session ? gms:formatTime(d.startTime) : 'Nghỉ'}" /></small>
                            </span>
                        </c:forEach>
                    </div>
                    <c:if test="${sale.noteCount gt 0}">
                        <ul class="sale-notes">
                            <c:forEach var="d" items="${sale.days}">
                                <c:if test="${not d.session}"><li class="is-off"><b><c:out value="${d.dayLabel}" /></b> <c:out value="${d.reason}" /></li></c:if>
                                <c:if test="${d.session and not empty d.warning}"><li class="is-warn"><b><c:out value="${d.dayLabel}" /></b> <c:out value="${d.warning}" /></li></c:if>
                            </c:forEach>
                        </ul>
                    </c:if>
                </section>
            </c:if>
            </div>

            <%-- =====================================================================
                 CỘT PHẢI. ĐƠN BÁN — tóm tắt + nút chính của bước hiện tại
                 ===================================================================== --%>
            <aside class="sale-card sale-summary" aria-labelledby="sale-h-sum">
                <header class="sale-card-head"><h2 id="sale-h-sum">Đơn bán</h2>
                    <c:if test="${sale.held}"><p class="sale-code"><c:out value="${sale.heldCode}" /></p></c:if></header>

                <c:if test="${sale.held}">
                    <%-- Đồng hồ giữ chỗ: common.js bindCountdowns đếm mm:ss, hết giờ tải lại (Service đã nhả khung). --%>
                    <p class="sale-timer">
                        <i class="fa-regular fa-clock" aria-hidden="true"></i>
                        <span>Giữ chỗ còn</span>
                        <b data-countdown-seconds="${sale.holdSeconds}" data-countdown-reload="true">--:--</b>
                    </p>
                </c:if>

                <%-- KHÁCH — chọn lúc nào cũng được. Chọn / đổi → URL đổi customer (partner), giữ nguyên gói, PT, ngày,
                     lịch mẫu (t1..t5) và các buổi sửa riêng (sessions) — pt-sales.js ghép URL. --%>
                <c:url var="keepUrl" value="/receptionist/pt-sales">
                    <c:if test="${not empty sale.packageId}"><c:param name="package" value="${sale.packageId}" /></c:if>
                    <c:if test="${not empty sale.ptId}"><c:param name="pt" value="${sale.ptId}" /></c:if>
                    <c:if test="${done2}"><c:param name="start" value="${sale.startDate}" /></c:if>
                </c:url>
                <section class="sale-who" aria-labelledby="sale-h1">
                    <div class="sale-label-row">
                        <h3 id="sale-h1" class="sale-field-label">Khách mua gói</h3>
                        <span class="pt-info"><button type="button" class="pt-info-trigger" aria-label="Cách tìm khách" aria-describedby="sale-q-help"><span aria-hidden="true">i</span></button><span id="sale-q-help" class="pt-info-popover" role="tooltip">Gõ tên <b>không cần dấu</b> ("bui thu ha"), số điện thoại ("0901…") hoặc mã khách ("KH-1001"). Tư vấn gói, PT, lịch trước rồi chọn khách sau cũng được — lịch đang xếp giữ nguyên.</span></span>
                    </div>
                    <c:choose>
                        <c:when test="${empty sale.holder}">
                            <%-- Gõ → gợi ý (JS). Tắt JS: Enter → ?q=… (ô ẩn giữ gói / PT / ngày) → danh sách bên dưới. --%>
                            <form class="sale-search is-small" method="get" action="${base}" role="search" data-combobox="customer">
                                <c:if test="${not empty sale.packageId}"><input type="hidden" name="package" value="${sale.packageId}"></c:if>
                                <c:if test="${not empty sale.ptId}"><input type="hidden" name="pt" value="${sale.ptId}"></c:if>
                                <c:if test="${done2}"><input type="hidden" name="start" value="${sale.startDate}"></c:if>
                                <div class="sale-search-box">
                                    <i class="fa-solid fa-magnifying-glass" aria-hidden="true"></i>
                                    <input id="sale-q" name="q" type="search" value="<c:out value='${sale.searchQuery}' />"
                                           placeholder="Tên, SĐT hoặc mã khách" autocomplete="off" spellcheck="false"
                                           role="combobox" aria-autocomplete="list" aria-expanded="false" aria-controls="sale-q-list"
                                           aria-describedby="sale-q-help">
                                    <ul class="sale-suggest" id="sale-q-list" role="listbox" aria-label="Khách gợi ý" hidden></ul>
                                </div>
                            </form>
                            <c:if test="${not empty sale.holderError}"><p class="sale-msg is-error" role="alert"><c:out value="${sale.holderError}" /></p></c:if>
                            <c:if test="${not empty sale.searchResults}">
                                <ul class="sale-results" aria-label="Kết quả tìm kiếm">
                                    <c:forEach var="r" items="${sale.searchResults}">
                                        <c:url var="pickUrl" value="/receptionist/pt-sales"><c:if test="${not empty sale.packageId}"><c:param name="package" value="${sale.packageId}" /></c:if><c:if test="${not empty sale.ptId}"><c:param name="pt" value="${sale.ptId}" /></c:if><c:if test="${done2}"><c:param name="start" value="${sale.startDate}" /></c:if><c:param name="customer" value="${r.customerId}" /></c:url>
                                        <li><a href="${pickUrl}" data-keep-plan>
                                            <span class="sale-avatar" aria-hidden="true"><c:out value="${fn:substring(r.fullName, 0, 1)}" /></span>
                                            <span class="sale-row-main"><strong><c:out value="${r.fullName}" /></strong><small><c:out value="${r.customerCode}" /> · <c:out value="${not empty r.phone ? r.phone : 'Hồ sơ phụ thuộc'}" /></small></span>
                                            <em class="${empty r.blockReason ? 'is-ok' : 'is-blocked'}"><c:out value="${empty r.blockReason ? 'Mua được' : r.blockReason}" /></em>
                                        </a></li>
                                    </c:forEach>
                                </ul>
                            </c:if>
                        </c:when>
                        <c:otherwise>
                            <c:set var="cust" value="${sale.holder}" />
                            <c:set var="custRole" value="Người đứng tên" />
                            <%@ include file="/WEB-INF/jsp/receptionist/_sale-customer.jspf" %>
                            <c:if test="${not sale.held}"><a class="btn btn-ghost btn-sm sale-inline" href="${keepUrl}" data-keep-plan>Đổi khách</a></c:if>
                        </c:otherwise>
                    </c:choose>

                    <%-- Gói đôi: người tập cùng (chọn sau người đứng tên hay trước đều được). --%>
                    <c:if test="${sale.couple}">
                        <c:choose>
                            <c:when test="${not empty sale.partner}">
                                <c:set var="cust" value="${sale.partner}" />
                                <c:set var="custRole" value="Người tập cùng" />
                                <%@ include file="/WEB-INF/jsp/receptionist/_sale-customer.jspf" %>
                                <c:if test="${not sale.held}">
                                    <c:url var="noPartnerUrl" value="/receptionist/pt-sales"><c:if test="${not empty sale.packageId}"><c:param name="package" value="${sale.packageId}" /></c:if><c:if test="${not empty sale.ptId}"><c:param name="pt" value="${sale.ptId}" /></c:if><c:if test="${done2}"><c:param name="start" value="${sale.startDate}" /></c:if><c:if test="${not empty sale.customerId}"><c:param name="customer" value="${sale.customerId}" /></c:if></c:url>
                                    <a class="btn btn-ghost btn-sm sale-inline" href="${noPartnerUrl}" data-keep-plan>Đổi người tập cùng</a>
                                </c:if>
                            </c:when>
                            <c:otherwise>
                                <div class="sale-search is-small" data-combobox="partner">
                                    <div class="sale-search-box">
                                        <i class="fa-solid fa-user-plus" aria-hidden="true"></i>
                                        <input id="sale-partner" type="search" placeholder="Người tập cùng: tên, SĐT, mã" autocomplete="off" spellcheck="false"
                                               role="combobox" aria-autocomplete="list" aria-expanded="false" aria-controls="sale-partner-list" aria-label="Tìm người tập cùng">
                                        <ul class="sale-suggest" id="sale-partner-list" role="listbox" aria-label="Người tập cùng gợi ý" hidden></ul>
                                    </div>
                                </div>
                                <c:if test="${not empty sale.partnerError}"><p class="sale-msg"><c:out value="${sale.partnerError}" /></p></c:if>
                            </c:otherwise>
                        </c:choose>
                    </c:if>
                </section>

                <dl class="sale-facts">
                    <div><dt>Gói</dt><dd><c:out value="${not empty sale.packageName ? sale.packageName : '—'}" /></dd></div>
                    <div><dt>PT</dt><dd><c:out value="${not empty sale.ptName ? sale.ptName : '—'}" /></dd></div>
                    <div><dt>Kỳ</dt><dd><c:choose><c:when test="${done2}"><c:out value="${gms:formatDate(sale.startDate)}" /> – <c:out value="${gms:formatDate(sale.endDate)}" /></c:when><c:otherwise>—</c:otherwise></c:choose></dd></div>
                    <div><dt>Buổi</dt><dd data-plan-count><c:choose><c:when test="${sale.sessionCount gt 0}"><c:out value="${sale.sessionCount}" /> buổi · <c:out value="${gms:formatMoney(sale.sessionValue)}" />/buổi</c:when><c:otherwise>—</c:otherwise></c:choose></dd></div>
                </dl>
                <p class="sale-total"><span>Phải thu</span><strong><c:choose><c:when test="${sale.price gt 0}"><c:out value="${gms:formatMoney(sale.price)}" /></c:when><c:otherwise>—</c:otherwise></c:choose></strong></p>

                <c:if test="${not empty saleError}"><p class="sale-msg is-error" role="alert"><c:out value="${saleError}" /></p></c:if>

                <c:choose>
                    <%-- Bước 4: đang giữ → thu tiền hoặc hủy giữ. --%>
                    <c:when test="${sale.held}">
                        <form class="sale-pay" method="post" action="${base}">
                            <input type="hidden" name="csrfToken" value="<c:out value='${csrfToken}' />">
                            <input type="hidden" name="action" value="pay">
                            <input type="hidden" name="order" value="${sale.heldOrderId}">
                            <input type="hidden" name="start" value="${sale.startDate}">
                            <fieldset class="sale-method">
                                <legend class="sale-field-label">Cách thu tiền</legend>
                                <label><input type="radio" name="method" value="Cash" checked> <i class="fa-solid fa-money-bill-wave" aria-hidden="true"></i> Tiền mặt</label>
                                <label><input type="radio" name="method" value="Transfer"> <i class="fa-solid fa-building-columns" aria-hidden="true"></i> Chuyển khoản</label>
                            </fieldset>
                            <label class="sale-field sale-ref"><span class="sale-field-label">Mã giao dịch <small>(không bắt buộc)</small></span>
                                <input name="reference" type="text" maxlength="50" autocomplete="off"></label>
                            <label class="sale-confirm"><input type="checkbox" required>
                                <span>Đã nhận đủ <b><c:out value="${gms:formatMoney(sale.price)}" /></b>.</span></label>
                            <button class="btn btn-primary sale-main" type="submit">Xác nhận đã thu tiền</button>
                        </form>
                        <form method="post" action="${base}">
                            <input type="hidden" name="csrfToken" value="<c:out value='${csrfToken}' />">
                            <input type="hidden" name="action" value="release">
                            <input type="hidden" name="order" value="${sale.heldOrderId}">
                            <button class="btn btn-ghost sale-release" type="submit" data-confirm-message="Hủy giữ chỗ ${sale.heldCode}? Các khung sẽ trả lại cho PT." data-confirm-title="Hủy giữ chỗ" data-confirm-text="Hủy giữ chỗ">Hủy giữ chỗ</button>
                        </form>
                    </c:when>
                    <%-- Bước 3: giữ lịch (khung chuyển "Đang giữ" ngay, người khác không lấy được).
                         pt-sale-plan.js điền t1..t5 (lịch mẫu) + sessions (các buổi), mở nút khi có ít nhất 1 buổi. --%>
                    <c:when test="${not empty sale.planJson}">
                        <form method="post" action="${base}" data-plan-form data-customer-ready="${customerReady}">
                            <input type="hidden" name="csrfToken" value="<c:out value='${csrfToken}' />">
                            <input type="hidden" name="action" value="hold">
                            <input type="hidden" name="customer" value="${sale.customerId}">
                            <input type="hidden" name="partner" value="${sale.partnerId}">
                            <input type="hidden" name="package" value="${sale.packageId}">
                            <input type="hidden" name="pt" value="${sale.ptId}">
                            <input type="hidden" name="start" value="${sale.startDate}">
                            <c:forEach var="n" begin="1" end="5"><input type="hidden" name="t${n}" value="" data-plan-t="${n}"></c:forEach>
                            <input type="hidden" name="sessions" value="" data-plan-sessions>
                            <%-- Chỉ hiện khi lịch có buổi "PT chưa mở" (pt-sale-plan.js bật / tắt; PtSaleService.hold kiểm tra lại). --%>
                            <label class="sale-confirm sale-pt-confirm" data-plan-confirm hidden>
                                <input type="checkbox" name="confirmPt" value="1">
                                <span><b>Đã xác nhận với PT</b>
                                    <small data-plan-confirm-text>PT đã đồng ý dạy các buổi chưa mở khung.</small></span>
                            </label>
                            <button class="btn btn-primary sale-main" type="submit" disabled data-plan-submit><i class="fa-solid fa-lock" aria-hidden="true"></i> Giữ lịch 15 phút</button>
                        </form>
                        <%-- Pumpy + bong bóng nhắc bước kế tiếp (pt-sale-plan.js renderSummary đổi chữ theo trạng thái).
                             Chữ không nói "bên trái / bên phải" vì iPad / điện thoại xếp 1 cột. --%>
                        <div class="sale-note">
                            <%@ include file="/WEB-INF/jsp/receptionist/_sale-pumpy.jspf" %>
                            <p class="sale-msg sale-bubble" data-plan-note aria-live="polite">Chọn giờ tập ở phần <b>Lịch tập</b>, rồi tìm khách ở ô <b>Khách mua gói</b> để giữ lịch.</p>
                        </div>
                    </c:when>
                    <c:otherwise>
                        <p class="sale-msg">Chọn gói, PT và ngày bắt đầu ở phần <b>Gói &amp; PT</b> để xếp lịch.</p>
                    </c:otherwise>
                </c:choose>
            </aside>
            </div>
        </c:otherwise>
        </c:choose>
        </main>
    </div>
</div>
</body>
</html>
