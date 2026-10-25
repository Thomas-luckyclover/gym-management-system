<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="gms" uri="/WEB-INF/gms-functions.tld" %>
<%--
================================================================================
 pt-sales.jsp — MÀN LỄ TÂN BÁN GÓI PT (/receptionist/pt-sales), UC-21 bản đơn giản hóa

 AI GỬI DỮ LIỆU SANG?  PtSaleServlet.showPage gọi PtSaleService.prepare MỘT lần → biến "sale" (PtSaleView).
   sale.holder / sale.partner   ← PtSaleDAO.findCustomer: Customers + Persons + gói thành viên hôm nay
   sale.packages / sale.pts     ← Packages (PTSingle, PTCouple đang bán) / Employees PT đang làm việc
   sale.times                   ← TimeSlots Free của PT trong 14 ngày đầu kỳ (Service gom theo giờ)
   sale.days                    ← Service planDays: mỗi ngày T2–T6 trong kỳ có buổi hay không
   pageError  câu lỗi khi Service lỗi (không có "sale") · saleError  câu lỗi khi bấm bán bị từ chối

 BỐ CỤC (màn rộng 2 cột; điện thoại 1 cột)
   Trái:  KHỐI 1 Khách · KHỐI 2 Gói, PT, ngày bắt đầu · KHỐI 3 Giờ cố định · KHỐI 3b Lịch dự kiến
          → cả 3 khối nằm trong MỘT form GET: bấm "Tiếp" (hoặc đổi lựa chọn, JS tự gửi) là Service tính lại.
   Phải:  KHỐI 4 Tóm tắt đơn + thu tiền → form POST riêng, mang lại 6 lựa chọn bằng ô ẩn.

 JS (pt-sales.js) chỉ: đổi radio / ô chọn thì tự gửi form GET. Tắt JS vẫn dùng nút "Tiếp".
================================================================================ --%>
<!DOCTYPE html>
<html lang="vi">
<head>
    <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
    <title>Bán gói PT · Super Gym Lễ tân</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/receptionist/pt-sales.css?v=20261025c">
    <script src="${pageContext.request.contextPath}/js/pages/receptionist/pt-sales.js?v=20261025a" defer></script>
</head>
<body class="dock theme-staff pt-sale-page">
<%@ include file="/WEB-INF/jsp/common/flash-toast.jspf" %>
<div class="app">
    <%@ include file="/WEB-INF/jsp/common/layout-receptionist.jspf" %>
    <div class="main">
        <header class="topbar">
            <div class="tb-left"><h1 class="tb-title">Bán gói PT</h1></div>
            <div class="top-right">
                <a class="tb-user" href="${pageContext.request.contextPath}/account" title="Cài đặt tài khoản">
                    <span class="avatar">LT</span>
                    <span class="tb-user-text"><b><c:out value="${sessionScope.displayName}" default="Lễ tân" /></b><small>Lễ tân quầy</small></span>
                </a>
            </div>
        </header>

        <main class="content sale" id="main-content" tabindex="-1">
            <c:choose>
                <c:when test="${not empty pageError}">
                    <section class="panel sale-error" role="alert">
                        <p><c:out value="${pageError}" /></p>
                        <a class="btn btn-outline" href="${pageContext.request.contextPath}/receptionist/pt-sales">Tải lại</a>
                    </section>
                </c:when>
                <c:otherwise>
                    <%-- Các bước đã xong (dùng để tô số bước và hiện khối kế tiếp). --%>
                    <c:set var="step1" value="${not empty sale.holder and empty sale.holder.blockReason}" />
                    <c:set var="step2" value="${step1 and not empty sale.endDate}" />
                    <c:set var="step3" value="${step2 and not empty sale.days}" />

                    <div class="sale-grid">
                        <%-- ======================================================================
                             TRÁI: 1 FORM GET cho 3 khối. Mọi nút "Tiếp" gửi lại cả 6 lựa chọn.
                             ====================================================================== --%>
                        <form class="sale-steps" method="get" action="${pageContext.request.contextPath}/receptionist/pt-sales" data-auto-submit>

                            <%-- ===== KHỐI 1. KHÁCH ===== --%>
                            <section class="panel sale-step ${step1 ? 'is-done' : 'is-current'}" aria-labelledby="sale-step-1">
                                <h2 id="sale-step-1"><span class="sale-step-no" aria-hidden="true">1</span> Khách mua gói</h2>
                                <div class="sale-search">
                                    <label class="visually-hidden" for="sale-q">Mã khách hoặc số điện thoại</label>
                                    <input id="sale-q" name="q" type="search" value="<c:out value='${sale.query}' />"
                                           placeholder="KH-0001 hoặc 0902345678" autocomplete="off" ${empty sale.query ? 'autofocus' : ''}>
                                    <button class="btn btn-primary" type="submit">Tìm</button>
                                </div>
                                <c:if test="${not empty sale.holderError}"><p class="sale-msg is-error" role="alert"><c:out value="${sale.holderError}" /></p></c:if>

                                <%-- Thẻ khách: tên, mã, SĐT, gói thành viên tới ngày nào; không bán được thì hiện lý do. --%>
                                <c:if test="${not empty sale.holder}">
                                    <c:set var="cust" value="${sale.holder}" />
                                    <c:set var="custRole" value="Người đứng tên" />
                                    <%@ include file="/WEB-INF/jsp/receptionist/_sale-customer.jspf" %>
                                </c:if>
                            </section>

                            <%-- ===== KHỐI 2. GÓI, PT, NGÀY BẮT ĐẦU (hiện khi khách mua được) ===== --%>
                            <c:if test="${step1}">
                                <section class="panel sale-step ${step2 ? 'is-done' : 'is-current'}" aria-labelledby="sale-step-2">
                                    <h2 id="sale-step-2"><span class="sale-step-no" aria-hidden="true">2</span> Gói, PT và ngày bắt đầu</h2>

                                    <%-- Gói: radio, mỗi gói 1 ô. option.note = "16.500.000đ · 2 tháng · 1 người". --%>
                                    <fieldset class="sale-choices">
                                        <legend>Gói PT</legend>
                                        <c:forEach var="option" items="${sale.packages}">
                                            <label class="sale-choice">
                                                <input type="radio" name="package" value="${option.value}" ${option.value eq sale.packageId ? 'checked' : ''}>
                                                <strong><c:out value="${option.label}" /></strong>
                                                <small><c:out value="${option.note}" /></small>
                                            </label>
                                        </c:forEach>
                                        <c:if test="${empty sale.packages}"><p class="sale-msg">Chưa có gói PT nào đang bán.</p></c:if>
                                    </fieldset>

                                    <%-- Gói đôi: thêm người tập cùng (BR-37: cũng phải có gói thành viên còn hạn). --%>
                                    <c:if test="${sale.couple}">
                                        <div class="sale-field">
                                            <label for="sale-partner">Người tập cùng <small>(mã khách hoặc SĐT)</small></label>
                                            <div class="sale-search">
                                                <input id="sale-partner" name="partner" type="search" value="<c:out value='${sale.partnerQuery}' />" placeholder="KH-0002" autocomplete="off">
                                                <button class="btn btn-outline" type="submit">Tìm</button>
                                            </div>
                                            <c:if test="${not empty sale.partnerError}"><p class="sale-msg is-error" role="alert"><c:out value="${sale.partnerError}" /></p></c:if>
                                            <c:if test="${not empty sale.partner}">
                                                <c:set var="cust" value="${sale.partner}" />
                                                <c:set var="custRole" value="Người tập cùng" />
                                                <%@ include file="/WEB-INF/jsp/receptionist/_sale-customer.jspf" %>
                                            </c:if>
                                        </div>
                                    </c:if>

                                    <div class="sale-row">
                                        <div class="sale-field">
                                            <label for="sale-pt">PT khách chọn</label>
                                            <select id="sale-pt" name="pt">
                                                <option value="">— Chọn PT —</option>
                                                <c:forEach var="option" items="${sale.pts}">
                                                    <option value="${option.value}" ${option.value eq sale.ptId ? 'selected' : ''}><c:out value="${option.label}" /></option>
                                                </c:forEach>
                                            </select>
                                        </div>
                                        <div class="sale-field">
                                            <label for="sale-start">Ngày bắt đầu</label>
                                            <input id="sale-start" name="start" type="date" value="${sale.startDate}" min="${sale.minStart}" max="${sale.maxStart}">
                                        </div>
                                    </div>
                                    <c:if test="${not empty sale.choiceError}"><p class="sale-msg is-error" role="alert"><c:out value="${sale.choiceError}" /></p></c:if>
                                    <c:if test="${not empty sale.endDate}">
                                        <p class="sale-msg">Kỳ gói: <strong><c:out value="${gms:formatDate(sale.startDate)}" /> – <c:out value="${gms:formatDate(sale.endDate)}" /></strong> (<c:out value="${sale.months}" /> tháng, tính cả ngày cuối).</p>
                                    </c:if>
                                    <div class="sale-next"><button class="btn btn-outline" type="submit">Tiếp</button></div>
                                </section>
                            </c:if>

                            <%-- ===== KHỐI 3. GIỜ CỐ ĐỊNH + LỊCH DỰ KIẾN (hiện khi đủ gói, PT, ngày) ===== --%>
                            <c:if test="${step2}">
                                <section class="panel sale-step ${step3 ? 'is-done' : 'is-current'}" aria-labelledby="sale-step-3">
                                    <h2 id="sale-step-3"><span class="sale-step-no" aria-hidden="true">3</span> Giờ cố định T2–T6</h2>
                                    <c:choose>
                                        <c:when test="${not empty sale.times}">
                                            <%-- Giờ = khung Free PT đã công bố trong 14 ngày đầu kỳ (CBR-52). note "PT trống 8/10 ngày đầu". --%>
                                            <fieldset class="sale-times">
                                                <legend class="visually-hidden">Giờ cố định</legend>
                                                <c:forEach var="option" items="${sale.times}">
                                                    <label class="sale-time">
                                                        <input type="radio" name="time" value="${option.value}" ${option.value eq sale.fixedTime ? 'checked' : ''}>
                                                        <strong><c:out value="${option.label}" /></strong>
                                                        <small><c:out value="${option.note}" /></small>
                                                    </label>
                                                </c:forEach>
                                            </fieldset>
                                            <div class="sale-next"><button class="btn btn-outline" type="submit">Xem lịch dự kiến</button></div>
                                        </c:when>
                                        <c:otherwise>
                                            <p class="sale-empty"><c:out value="${sale.ptName}" /> chưa công bố khung trống nào vào T2–T6 trong 14 ngày đầu kỳ.
                                                Nhờ PT công bố khung trên Lịch dạy, hoặc chọn ngày bắt đầu / PT khác.</p>
                                        </c:otherwise>
                                    </c:choose>
                                    <c:if test="${not empty sale.planError}"><p class="sale-msg is-error" role="alert"><c:out value="${sale.planError}" /></p></c:if>

                                    <%-- Lịch dự kiến: mỗi ngày T2–T6 trong kỳ là 1 ô. Xanh = có buổi, vàng = có buổi nhưng PT cần dời,
                                         xám gạch = không có buổi (rê chuột / đọc danh sách bên dưới để biết lý do). --%>
                                    <c:if test="${not empty sale.days}">
                                        <div class="sale-plan" aria-labelledby="sale-plan-title">
                                            <h3 id="sale-plan-title"><c:out value="${sale.sessionCount}" /> buổi
                                                <c:if test="${sale.skipCount gt 0}"><small>· <c:out value="${sale.skipCount}" /> ngày không có buổi</small></c:if></h3>
                                            <ol class="sale-days">
                                                <%-- Ô đầu tiên đứng đúng cột thứ của nó (kỳ bắt đầu Thứ 4 → cột 3), các ô sau tự xếp tiếp. --%>
                                                <c:forEach var="d" items="${sale.days}" varStatus="loop">
                                                    <li <c:if test="${loop.first}">style="grid-column-start: ${d.date.dayOfWeek.value}"</c:if> class="sale-day ${d.session ? (empty d.warning ? 'is-on' : 'is-warn') : 'is-off'}"
                                                        title="${fn:escapeXml(d.dayLabel)} · ${d.session ? (empty d.warning ? 'Có buổi' : fn:escapeXml(d.warning)) : fn:escapeXml(d.reason)}">
                                                        <span><c:out value="${fn:substringBefore(d.dayLabel, ' ·')}" /></span>
                                                        <b><c:out value="${fn:substring(gms:formatDate(d.date), 0, 5)}" /></b>
                                                    </li>
                                                </c:forEach>
                                            </ol>
                                            <ul class="sale-legend" aria-label="Chú thích">
                                                <li class="is-on">Có buổi</li><li class="is-warn">Có buổi, PT cần dời</li><li class="is-off">Không có buổi</li>
                                            </ul>
                                            <%-- Ngày không có buổi / cần dời: liệt kê lý do để Lễ tân báo khách. --%>
                                            <c:if test="${sale.noteCount gt 0}">
                                                <ul class="sale-notes">
                                                    <c:forEach var="d" items="${sale.days}">
                                                        <c:if test="${not d.session}"><li class="is-off"><b><c:out value="${d.dayLabel}" /></b> · <c:out value="${d.reason}" /></li></c:if>
                                                        <c:if test="${d.session and not empty d.warning}"><li class="is-warn"><b><c:out value="${d.dayLabel}" /></b> · <c:out value="${d.warning}" /></li></c:if>
                                                    </c:forEach>
                                                </ul>
                                            </c:if>
                                        </div>
                                    </c:if>
                                </section>
                            </c:if>
                        </form>

                        <%-- ======================================================================
                             PHẢI: KHỐI 4. TÓM TẮT + THU TIỀN (form POST riêng)
                             ====================================================================== --%>
                        <aside class="panel sale-summary" aria-labelledby="sale-summary-title">
                            <h2 id="sale-summary-title">Đơn bán</h2>
                            <dl class="sale-facts">
                                <div><dt>Khách</dt><dd><c:out value="${not empty sale.holder ? sale.holder.fullName : '—'}" /><c:if test="${not empty sale.partner}"> &amp; <c:out value="${sale.partner.fullName}" /></c:if></dd></div>
                                <div><dt>Gói</dt><dd><c:out value="${not empty sale.packageName ? sale.packageName : '—'}" /></dd></div>
                                <div><dt>PT</dt><dd><c:out value="${not empty sale.ptName ? sale.ptName : '—'}" /></dd></div>
                                <div><dt>Kỳ</dt><dd><c:choose><c:when test="${not empty sale.endDate}"><c:out value="${gms:formatDate(sale.startDate)}" /> – <c:out value="${gms:formatDate(sale.endDate)}" /></c:when><c:otherwise>—</c:otherwise></c:choose></dd></div>
                                <div><dt>Giờ</dt><dd><c:choose><c:when test="${step3}">T2–T6 · <c:out value="${gms:formatTime(sale.fixedTime)}" /></c:when><c:otherwise>—</c:otherwise></c:choose></dd></div>
                                <div><dt>Số buổi</dt><dd><c:choose><c:when test="${step3}"><c:out value="${sale.sessionCount}" /> buổi · <c:out value="${gms:formatMoney(sale.sessionValue)}" />/buổi</c:when><c:otherwise>—</c:otherwise></c:choose></dd></div>
                            </dl>
                            <p class="sale-total"><span>Phải thu</span><strong><c:choose><c:when test="${sale.price gt 0}"><c:out value="${gms:formatMoney(sale.price)}" /></c:when><c:otherwise>—</c:otherwise></c:choose></strong></p>

                            <c:choose>
                                <c:when test="${sale.ready}">
                                    <form class="sale-pay" method="post" action="${pageContext.request.contextPath}/receptionist/pt-sales">
                                        <input type="hidden" name="csrfToken" value="<c:out value='${csrfToken}' />">
                                        <input type="hidden" name="q" value="<c:out value='${sale.query}' />">
                                        <input type="hidden" name="partner" value="<c:out value='${sale.partnerQuery}' />">
                                        <input type="hidden" name="package" value="${sale.packageId}">
                                        <input type="hidden" name="pt" value="${sale.ptId}">
                                        <input type="hidden" name="start" value="${sale.startDate}">
                                        <input type="hidden" name="time" value="${sale.fixedTime}">
                                        <fieldset class="sale-method">
                                            <legend>Cách thu tiền</legend>
                                            <label><input type="radio" name="method" value="Cash" ${method ne 'Transfer' ? 'checked' : ''}> Tiền mặt</label>
                                            <label><input type="radio" name="method" value="Transfer" ${method eq 'Transfer' ? 'checked' : ''}> Chuyển khoản</label>
                                        </fieldset>
                                        <%-- Chỉ hiện khi chọn Chuyển khoản (CSS :has). --%>
                                        <label class="sale-field sale-ref">Mã giao dịch <small>(không bắt buộc)</small>
                                            <input name="reference" type="text" maxlength="50" autocomplete="off">
                                        </label>
                                        <label class="sale-confirm">
                                            <input type="checkbox" required>
                                            <span>Đã nhận đủ <b><c:out value="${gms:formatMoney(sale.price)}" /></b><span class="sale-ref-note"> (đã kiểm tra tiền vào tài khoản)</span>.</span>
                                        </label>
                                        <c:if test="${not empty saleError}"><p class="sale-msg is-error" role="alert"><c:out value="${saleError}" /></p></c:if>
                                        <button class="btn btn-primary sale-submit" type="submit">Xác nhận đã thu tiền</button>
                                        <p class="sale-msg">Bán xong: tạo đơn đã thanh toán, gói PT và <c:out value="${sale.sessionCount}" /> buổi; khách và PT nhận thông báo.</p>
                                    </form>
                                </c:when>
                                <c:otherwise>
                                    <%-- Bán bị từ chối mà lịch không còn dùng được: câu lỗi đã hiện ngay ở bước có vấn đề bên trái. --%>
                                    <p class="sale-msg">Làm đủ 3 bước bên trái để thu tiền.</p>
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
