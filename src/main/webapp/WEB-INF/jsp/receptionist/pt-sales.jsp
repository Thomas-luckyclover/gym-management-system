<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="gms" uri="/WEB-INF/gms-functions.tld" %>
<%--
================================================================================
 pt-sales.jsp — MÀN LỄ TÂN BÁN GÓI PT (/receptionist/pt-sales), UC-21 bản đơn giản hóa

 AI GỬI DỮ LIỆU SANG?  PtSaleServlet.showPage gọi PtSaleService.prepare MỘT lần → "sale" (PtSaleView).
 LUỒNG (giống mua vé có giữ chỗ)
   1. Khách        ô tìm kiếm gợi ý (pt-sales.js gọi /receptionist/pt-sales/customers) → ?customer=12
   2. Gói & PT     ô gói, ô PT, ngày bắt đầu (form GET, đổi là tự gửi)
   3. Lịch tập     bảng T2–T6 × giờ PT rảnh, mỗi ô "8/9 tuần" → chọn giờ cho từng thứ (t1..t5)
   4. Thu tiền     "Giữ lịch 15 phút" (POST hold) → ?order=… : đồng hồ đếm ngược, thu tiền / hủy giữ
 BỐ CỤC   thanh bước trên cùng · trái: các bước · phải: "Đơn bán" dính khi cuộn
 BIẾN     sale, pageError (Service lỗi), saleError (bấm Giữ lịch bị từ chối), csrfToken
================================================================================ --%>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<c:set var="base" value="${ctx}/receptionist/pt-sales" />
<!DOCTYPE html>
<html lang="vi">
<head>
    <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
    <title>Bán gói PT · Super Gym Lễ tân</title>
    <link rel="stylesheet" href="${ctx}/css/pages/receptionist/pt-sales.css?v=20261025v2">
    <script src="${ctx}/js/pages/receptionist/pt-sales.js?v=20261025v2" defer></script>
</head>
<body class="dock theme-staff pt-sale-page">
<%@ include file="/WEB-INF/jsp/common/flash-toast.jspf" %>
<div class="app">
    <%@ include file="/WEB-INF/jsp/common/layout-receptionist.jspf" %>
    <div class="main">
        <header class="topbar">
            <div class="tb-left"><h1 class="tb-title">Bán gói PT</h1></div>
            <div class="top-right">
                <a class="tb-user" href="${ctx}/account" title="Cài đặt tài khoản">
                    <span class="avatar">LT</span>
                    <span class="tb-user-text"><b><c:out value="${sessionScope.displayName}" default="Lễ tân" /></b><small>Lễ tân quầy</small></span>
                </a>
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
            <%-- Bước đã xong → tô thanh bước. holderOk: đã có khách mua được. --%>
            <c:set var="holderOk" value="${not empty sale.holder and empty sale.holder.blockReason}" />
            <c:set var="done2" value="${not empty sale.endDate}" />

            <%-- ===== THANH BƯỚC ===== --%>
            <ol class="sale-stepper" aria-label="Các bước bán gói">
                <li class="${holderOk ? 'is-done' : 'is-current'}"><span>1</span> Khách</li>
                <li class="${done2 ? 'is-done' : (holderOk ? 'is-current' : '')}"><span>2</span> Gói &amp; PT</li>
                <li class="${sale.held ? 'is-done' : (done2 ? 'is-current' : '')}"><span>3</span> Lịch tập</li>
                <li class="${sale.held ? 'is-current' : ''}"><span>4</span> Giữ chỗ &amp; thu tiền</li>
            </ol>

            <div class="sale-layout">
            <div class="sale-flow">

            <%-- =====================================================================
                 BƯỚC 1. KHÁCH
                 ===================================================================== --%>
            <section class="sale-card" aria-labelledby="sale-h1">
                <h2 id="sale-h1" class="visually-hidden">Khách mua gói</h2>
                <c:choose>
                    <c:when test="${empty sale.holder}">
                        <%-- Ô tìm kiếm kiểu Google: gõ → gợi ý (JS). Tắt JS: Enter → ?q=… → danh sách bên dưới. --%>
                        <form class="sale-search" method="get" action="${base}" role="search" data-combobox="customer">
                            <label class="sale-search-label" for="sale-q">Khách mua gói</label>
                            <div class="sale-search-box">
                                <i class="fa-solid fa-magnifying-glass" aria-hidden="true"></i>
                                <input id="sale-q" name="q" type="search" value="<c:out value='${sale.searchQuery}' />"
                                       placeholder="Tên, số điện thoại hoặc mã khách" autocomplete="off" spellcheck="false" autofocus
                                       role="combobox" aria-autocomplete="list" aria-expanded="false" aria-controls="sale-q-list"
                                       aria-describedby="sale-q-hint">
                                <kbd aria-hidden="true">Enter</kbd>
                                <ul class="sale-suggest" id="sale-q-list" role="listbox" aria-label="Khách gợi ý" hidden></ul>
                            </div>
                            <p class="sale-hint" id="sale-q-hint">Gõ từ 2 ký tự. Tên không cần dấu: "nguyen van an", "0901", "KH-1001" đều được.</p>
                        </form>
                        <c:if test="${not empty sale.holderError}"><p class="sale-msg is-error" role="alert"><c:out value="${sale.holderError}" /></p></c:if>
                        <c:if test="${not empty sale.searchResults}">
                            <ul class="sale-results" aria-label="Kết quả tìm kiếm">
                                <c:forEach var="r" items="${sale.searchResults}">
                                    <li><a href="${base}?customer=${r.customerId}">
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
                        <c:if test="${not sale.held}"><a class="btn btn-ghost btn-sm sale-inline" href="${base}">Đổi khách</a></c:if>
                    </c:otherwise>
                </c:choose>
            </section>

            <%-- =====================================================================
                 BƯỚC 2 + 3 dùng chung 1 form GET. Đổi gói / PT / ngày / ô giờ → pt-sales.js tự gửi.
                 ===================================================================== --%>
            <c:if test="${not sale.held and holderOk}">
            <form id="sale-form" class="sale-flow" method="get" action="${base}" data-auto-submit>
                <input type="hidden" name="customer" value="${sale.customerId}">
                <input type="hidden" name="partner" value="${sale.partnerId}" data-partner-field>
                <%-- Bảng tuần chưa hiện → giờ đã chọn (t1..t5) đi theo ô ẩn; bảng hiện thì radio trong bảng mang giá trị. --%>
                <c:if test="${empty sale.grid}">
                    <c:forEach var="t" items="${sale.weekTimeTexts}" varStatus="loop"><input type="hidden" name="t${loop.index + 1}" value="${t}"></c:forEach>
                </c:if>

                <%-- ===== BƯỚC 2. GÓI, PT, NGÀY BẮT ĐẦU ===== --%>
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

                    <c:if test="${sale.couple}">
                        <div class="sale-field">
                            <span class="sale-field-label">Người tập cùng</span>
                            <c:choose>
                                <c:when test="${not empty sale.partner}">
                                    <c:set var="cust" value="${sale.partner}" />
                                    <c:set var="custRole" value="Người tập cùng" />
                                    <%@ include file="/WEB-INF/jsp/receptionist/_sale-customer.jspf" %>
                                    <button class="btn btn-ghost btn-sm sale-inline" type="button" data-clear-partner>Đổi người tập cùng</button>
                                </c:when>
                                <c:otherwise>
                                    <div class="sale-search is-small" data-combobox="partner">
                                        <div class="sale-search-box">
                                            <i class="fa-solid fa-user-plus" aria-hidden="true"></i>
                                            <input id="sale-partner" type="search" placeholder="Tên, SĐT hoặc mã người tập cùng" autocomplete="off" spellcheck="false"
                                                   role="combobox" aria-autocomplete="list" aria-expanded="false" aria-controls="sale-partner-list" aria-label="Tìm người tập cùng">
                                            <ul class="sale-suggest" id="sale-partner-list" role="listbox" aria-label="Người tập cùng gợi ý" hidden></ul>
                                        </div>
                                    </div>
                                    <c:if test="${not empty sale.partnerError}"><p class="sale-msg is-error"><c:out value="${sale.partnerError}" /></p></c:if>
                                </c:otherwise>
                            </c:choose>
                        </div>
                    </c:if>

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
                            <input name="start" type="date" value="${sale.startDate}" min="${sale.minStart}" max="${sale.maxStart}">
                        </label>
                    </c:if>
                    <c:if test="${not empty sale.choiceError}"><p class="sale-msg is-error" role="alert"><c:out value="${sale.choiceError}" /></p></c:if>
                    <noscript><button class="btn btn-outline" type="submit">Tiếp</button></noscript>
                </section>

                <%-- ===== BƯỚC 3. LỊCH TẬP HẰNG TUẦN ===== --%>
                <c:if test="${done2}">
                <section class="sale-card" aria-labelledby="sale-h3">
                    <header class="sale-card-head"><h2 id="sale-h3">Lịch tập hằng tuần</h2>
                        <p>Mỗi ô = số tuần trong kỳ còn trống giờ đó. Chọn 1 giờ cho mỗi thứ, hoặc bấm "Cả tuần".</p></header>
                    <c:choose>
                        <c:when test="${empty sale.grid}">
                            <p class="sale-empty"><c:out value="${sale.ptName}" /> chưa công bố khung trống nào vào T2–T6 trong 2 tuần đầu kỳ.
                                Nhờ PT công bố trên Lịch dạy, hoặc đổi ngày bắt đầu / PT.</p>
                        </c:when>
                        <c:otherwise>
                            <div class="sale-week-wrap">
                            <table class="sale-week">
                                <caption class="visually-hidden">Số tuần còn trống theo giờ và thứ</caption>
                                <thead><tr><th scope="col">Giờ</th>
                                    <c:forEach var="n" begin="0" end="4"><th scope="col">Thứ <c:out value="${n + 2}" /><small><c:out value="${sale.weekdayTotals[n]}" /> tuần</small></th></c:forEach>
                                </tr></thead>
                                <tbody>
                                <c:forEach var="row" items="${sale.grid}">
                                    <tr>
                                        <th scope="row"><span><c:out value="${row.label}" /></span>
                                            <c:if test="${row.wholeWeekPossible}">
                                                <c:url var="weekUrl" value="/receptionist/pt-sales"><c:param name="customer" value="${sale.customerId}" /><c:param name="partner" value="${sale.partnerId}" /><c:param name="package" value="${sale.packageId}" /><c:param name="pt" value="${sale.ptId}" /><c:param name="start" value="${sale.startDate}" /><c:param name="t1" value="${row.time}" /><c:param name="t2" value="${row.time}" /><c:param name="t3" value="${row.time}" /><c:param name="t4" value="${row.time}" /><c:param name="t5" value="${row.time}" /></c:url>
                                                <a class="sale-whole" href="${weekUrl}">Cả tuần</a>
                                            </c:if>
                                        </th>
                                        <c:forEach var="cell" items="${row.cells}">
                                            <td class="is-${cell.level}">
                                                <label class="sale-cell">
                                                    <input type="radio" name="t${cell.weekday}" value="${row.time}" ${cell.selected ? 'checked' : ''} ${cell.level eq 'none' ? 'disabled' : ''}
                                                           aria-label="Thứ ${cell.weekday + 1} lúc ${row.time}: trống ${cell.usable}/${cell.total} tuần">
                                                    <span aria-hidden="true"><b><c:out value="${cell.usable}" /></b>/<c:out value="${cell.total}" /></span>
                                                </label>
                                            </td>
                                        </c:forEach>
                                    </tr>
                                </c:forEach>
                                </tbody>
                            </table>
                            </div>
                            <ul class="sale-key" aria-label="Chú thích">
                                <li class="is-full">Trống mọi tuần</li><li class="is-most">Trống ≥ 3/4 số tuần</li><li class="is-some">Trống ít</li><li class="is-pick">Đã chọn</li>
                            </ul>
                        </c:otherwise>
                    </c:choose>
                    <c:if test="${not empty sale.planError}"><p class="sale-msg is-error" role="alert"><c:out value="${sale.planError}" /></p></c:if>
                    <noscript><button class="btn btn-outline" type="submit">Xem lịch</button></noscript>
                </section>
                </c:if>
            </form>
            </c:if>

            <%-- =====================================================================
                 LỊCH DỰ KIẾN (đã chọn đủ 5 thứ, hoặc đang giữ chỗ): lưới 5 cột T2–T6, mỗi ô 1 ngày
                 ===================================================================== --%>
            <c:if test="${not empty sale.days and (sale.weekComplete or sale.held)}">
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

                <dl class="sale-facts">
                    <div><dt>Khách</dt><dd><c:out value="${not empty sale.holder ? sale.holder.fullName : '—'}" /><c:if test="${not empty sale.partner}"> &amp; <c:out value="${sale.partner.fullName}" /></c:if></dd></div>
                    <div><dt>Gói</dt><dd><c:out value="${not empty sale.packageName ? sale.packageName : '—'}" /></dd></div>
                    <div><dt>PT</dt><dd><c:out value="${not empty sale.ptName ? sale.ptName : '—'}" /></dd></div>
                    <div><dt>Kỳ</dt><dd><c:choose><c:when test="${done2}"><c:out value="${gms:formatDate(sale.startDate)}" /> – <c:out value="${gms:formatDate(sale.endDate)}" /></c:when><c:otherwise>—</c:otherwise></c:choose></dd></div>
                    <div><dt>Buổi</dt><dd><c:choose><c:when test="${sale.sessionCount gt 0}"><c:out value="${sale.sessionCount}" /> buổi · <c:out value="${gms:formatMoney(sale.sessionValue)}" />/buổi</c:when><c:otherwise>—</c:otherwise></c:choose></dd></div>
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
                    <%-- Bước 3 xong: giữ lịch (khung chuyển "Đang giữ" ngay, người khác không lấy được). --%>
                    <c:when test="${sale.ready}">
                        <form method="post" action="${base}">
                            <input type="hidden" name="csrfToken" value="<c:out value='${csrfToken}' />">
                            <input type="hidden" name="action" value="hold">
                            <input type="hidden" name="customer" value="${sale.customerId}">
                            <input type="hidden" name="partner" value="${sale.partnerId}">
                            <input type="hidden" name="package" value="${sale.packageId}">
                            <input type="hidden" name="pt" value="${sale.ptId}">
                            <input type="hidden" name="start" value="${sale.startDate}">
                            <c:forEach var="t" items="${sale.weekTimeTexts}" varStatus="loop"><input type="hidden" name="t${loop.index + 1}" value="${t}"></c:forEach>
                            <button class="btn btn-primary sale-main" type="submit"><i class="fa-solid fa-lock" aria-hidden="true"></i> Giữ lịch 15 phút</button>
                        </form>
                        <p class="sale-msg">Giữ trước khi thu tiền: <c:out value="${sale.sessionCount}" /> khung chuyển "Đang giữ", lễ tân khác và PT không lấy được.</p>
                    </c:when>
                    <c:otherwise>
                        <p class="sale-msg">Làm xong các bước bên trái để giữ lịch.</p>
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
