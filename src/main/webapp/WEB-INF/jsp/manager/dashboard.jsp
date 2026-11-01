<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="gms" uri="/WEB-INF/gms-functions.tld" %>
<%--
  dashboard.jsp — Báo cáo vận hành & doanh thu của Manager (#66, UC-46, SRS 12.1.1).
  Khớp mockup gms_manager_mockup_v24.html (viewReport) + thẻ Lượt check-in (đã thống nhất thêm).
  Dữ liệu: report (ReportView), from, to, isInvalidRange, exportedMessage do ManagerDashboardServlet set.
--%>
<!DOCTYPE html>
<html lang="vi">
<head>
    <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
    <title>Báo cáo vận hành &amp; doanh thu - GMS</title>
    <%-- Khung dock/sidebar/topbar dùng chung ở common.css (mục 11) --%>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/manager/manager-dashboard.css?v=20261025city">

</head>
<body class="dock ${themeClass}">

<%@ include file="/WEB-INF/jsp/common/flash-toast.jspf" %>

<div class="app">
    <%@ include file="/WEB-INF/jsp/common/layout-manager.jspf" %>

    <div class="main">
        <header class="topbar">
            <div class="tb-left">
                <h1 class="tb-title">Báo cáo vận hành &amp; doanh thu</h1>
            </div>

            <%-- Khối người dùng: giống topbar của Account Settings --%>
            <%-- Chuông thông báo + tài khoản --%>
            <div class="top-right">

                <%@ include file="/WEB-INF/jsp/common/notification-bell.jspf" %>


                <%@ include file="/WEB-INF/jsp/common/topbar-user.jspf" %>

            </div>
        </header>

        <main class="content" id="main-content" tabindex="-1">

            <%-- Ghi chú bên trái, bộ lọc bên phải (giống mockup) --%>
            <div class="page-intro" data-reveal>
                <p>
                    <c:if test="${not empty report}">Số liệu cập nhật lúc ${gms:formatDateTime(report.generatedAt)}, không theo thời gian thực.</c:if>
                    <c:if test="${empty report}">Số liệu là ảnh chụp lúc xem, không theo thời gian thực.</c:if>
                </p>

                <form id="reportFilter" method="get"
                      action="${pageContext.request.contextPath}/manager/dashboard"
                      class="report-filter" novalidate>
                    <div class="field">
                        <label class="field-label" for="reportFrom">Từ ngày</label>
                        <input id="reportFrom" name="from" type="date" required value="${from}">
                    </div>
                    <div class="field">
                        <label class="field-label" for="reportTo">Đến ngày</label>
                        <input id="reportTo" name="to" type="date" required value="${to}">
                    </div>
                    <%-- Chọn nhanh khoảng ngày: điền 2 ô ngày rồi xem luôn (common.js "Chọn nhanh khoảng ngày") --%>
                    <div class="range-presets" data-range-presets data-from="#reportFrom" data-to="#reportTo" role="group" aria-label="Chọn nhanh khoảng ngày">
                        <button type="button" class="chip" data-range="7">7 ngày</button>
                        <button type="button" class="chip" data-range="30">30 ngày</button>
                        <button type="button" class="chip" data-range="month">Tháng này</button>
                    </div>
                    <button class="btn btn-primary" type="submit">Xem</button>
                    <%-- Xuất đúng báo cáo đang hiển thị (from/to của lần xem gần nhất), không phải ô ngày đang sửa dở --%>
                    <a id="exportCsv" class="btn btn-ghost"
                       href="${pageContext.request.contextPath}/manager/dashboard?action=export&from=${from}&to=${to}"
                       data-exported-message="<c:out value='${exportedMessage}' />"
                       <c:if test="${isInvalidRange or empty report}">aria-disabled="true"</c:if>>
                        <i class="fa-solid fa-download" aria-hidden="true"></i> Xuất CSV
                    </a>
                </form>
            </div>
            <span id="reportRangeError" class="field-error" role="alert"
                  <c:if test="${not isInvalidRange}">hidden</c:if>>
                Ngày bắt đầu phải trước hoặc bằng ngày kết thúc.
            </span>

            <c:if test="${not empty pageError}">
                <div class="report-card report-empty" role="alert"><c:out value="${pageError}" /></div>
            </c:if>

            <c:choose>
            <c:when test="${isInvalidRange}">
                <div class="report-card report-empty">Ngày bắt đầu phải trước hoặc bằng ngày kết thúc.</div>
            </c:when>
            <c:when test="${not empty report}">

            <%-- ===== 6 thẻ tổng quan ===== --%>
            <%-- Bảng điểm: ô đầu tô màu chủ đạo; data-flip: số lật kiểu bảng giờ tàu khi tải (common.js) --%>
            <section class="report-stats scoreboard" aria-label="Tổng quan" data-reveal>
                <div class="report-card stat is-lead"><div class="stat-label">Doanh thu</div><div class="stat-value is-flap" title="${gms:formatMoney(report.totalRevenue)}" data-flip><c:out value="${report.totalRevenueText}" /></div><div class="stat-note">Đã gồm VAT</div><c:set var="delta" value="${reportDeltas['revenue']}" /><%@ include file="/WEB-INF/jsp/manager/_stat-delta.jspf" %></div>
                <div class="report-card stat"><div class="stat-label">Hội viên đang hoạt động</div><div class="stat-value is-flap" data-flip>${report.activeMembers}</div><div class="stat-note">+${report.newMembers} hội viên mới</div></div>
                <div class="report-card stat"><div class="stat-label">Gói đang chạy</div><div class="stat-value is-flap" data-flip>${report.runningPackages}</div><div class="stat-note">Gói tập, không gồm vé ngày</div></div>
                <div class="report-card stat"><div class="stat-label">Hoa hồng PT</div><div class="stat-value is-flap" title="${gms:formatMoney(report.totalCommission)}" data-flip><c:out value="${report.totalCommissionText}" /></div><div class="stat-note">Theo % riêng từng PT</div><c:set var="delta" value="${reportDeltas['commission']}" /><%@ include file="/WEB-INF/jsp/manager/_stat-delta.jspf" %></div>
                <div class="report-card stat"><div class="stat-label">Vé 1 ngày</div><div class="stat-value is-flap" data-flip>${report.dayPassVisits}</div><div class="stat-note">lượt · <c:out value="${report.dayPassRevenueText}" /> doanh thu</div><c:set var="delta" value="${reportDeltas['dayPass']}" /><%@ include file="/WEB-INF/jsp/manager/_stat-delta.jspf" %></div>
                <div class="report-card stat"><div class="stat-label">Lượt check-in</div><div class="stat-value is-flap" data-flip>${report.checkInCount}</div><div class="stat-note">Gồm cả vé 1 ngày</div><c:set var="delta" value="${reportDeltas['checkIn']}" /><%@ include file="/WEB-INF/jsp/manager/_stat-delta.jspf" %></div>
            </section>

            <%-- Không có dữ liệu trong khoảng ngày → báo rõ (MSG03), vẫn giữ các thẻ ở trên --%>
            <c:if test="${report.emptyInRange}">
                <div class="report-card report-empty"><%= com.gms.constant.Messages.MSG03_NO_DATA_IN_RANGE %></div>
            </c:if>

            <c:if test="${not report.emptyInRange}">

            <%-- ===== Biểu đồ doanh thu: chiều cao cột = --percent (% so với trục Y) ===== --%>
            <section class="report-card report-pad" data-reveal>
                <h2>Doanh thu theo <c:out value="${report.monthlyChart ? 'tháng' : 'ngày'}" /></h2>
                <p class="report-sub">Triệu đồng, đã gồm VAT</p>
                <div class="chart" role="img" aria-label="Biểu đồ doanh thu">
                    <div class="chart-plot">
                        <c:forEach var="t" items="${report.ticks}">
                            <div class="grid-line" style="--percent:${t.percent}"><span><c:out value="${t.label}" /></span></div>
                        </c:forEach>
                        <div class="bars">
                            <c:forEach var="b" items="${report.buckets}">
                                <%-- tabindex + data-tooltip: xem số khi rê chuột hoặc Tab bằng bàn phím --%>
                                <div class="bar${b.last ? ' bar-last' : ''}" style="--percent:${b.percent}" tabindex="0"
                                     data-tooltip="<c:out value='${b.fullLabel}' />: ${gms:formatMoney(b.amount)}"
                                     aria-label="<c:out value='${b.fullLabel}' />: ${gms:formatMoney(b.amount)}"></div>
                            </c:forEach>
                        </div>
                    </div>
                    <div class="x-labels">
                        <c:forEach var="b" items="${report.buckets}"><span><c:if test="${b.labelVisible}"><c:out value="${b.label}" /></c:if></span></c:forEach>
                    </div>
                </div>
            </section>

            <div class="report-grid" data-reveal>

                <%-- ===== Doanh thu theo gói ===== --%>
                <section class="report-card">
                    <h2 class="report-card-title">Doanh thu theo gói</h2>
                    <div class="report-table-wrap">
                        <table class="report-table">
                            <thead>
                            <tr><th>Gói</th><th class="lane-col">Tỉ lệ</th><th class="num">Số đơn</th><th class="num">Doanh thu</th><th class="num">VAT</th></tr>
                            </thead>
                            <tbody>
                            <%-- Làn chạy: chấm dừng ở % doanh thu gói so với gói cao nhất (× 100 trước khi chia để BigDecimal không làm tròn về 0/1) --%>
                            <c:set var="maxPackageRevenue" value="${0}" />
                            <c:forEach var="row" items="${report.packages}">
                                <c:if test="${row.revenue > maxPackageRevenue}"><c:set var="maxPackageRevenue" value="${row.revenue}" /></c:if>
                            </c:forEach>
                            <c:forEach var="row" items="${report.packages}" varStatus="loop">
                                <tr>
                                    <td><span class="rank">${loop.index + 1}</span><c:out value="${row.packageName}" /></td>
                                    <td class="lane-col"><span class="lane"><span class="lane-runner" style="--pct:${maxPackageRevenue > 0 ? (row.revenue * 100) / maxPackageRevenue : 0}"></span></span></td>
                                    <td class="num">${row.orderCount}</td>
                                    <td class="num">${gms:formatMoney(row.revenue)}</td>
                                    <td class="num muted">${gms:formatMoney(row.vat)}</td>
                                </tr>
                            </c:forEach>
                            <c:if test="${empty report.packages}">
                                <tr><td colspan="5" class="muted">Không có đơn nào trong khoảng ngày.</td></tr>
                            </c:if>
                            </tbody>
                        </table>
                    </div>
                </section>

                <%-- ===== Hoa hồng PT ===== --%>
                <section class="report-card">
                    <h2 class="report-card-title">Hoa hồng PT</h2>
                    <div class="report-table-wrap">
                        <table class="report-table">
                            <thead>
                            <tr><th>PT</th><th class="num">Hoàn thành</th><th class="num">Vắng</th><th class="num">Hủy</th><th class="num">Hoa hồng</th></tr>
                            </thead>
                            <tbody>
                            <c:forEach var="row" items="${report.ptRows}">
                                <tr>
                                    <td><c:out value="${row.fullName}" /> <span class="muted">(${row.commissionRate}%)</span></td>
                                    <td class="num">${row.completed}</td>
                                    <td class="num">${row.missed}</td>
                                    <td class="num">${row.cancelled}</td>
                                    <td class="num"><b>${gms:formatMoney(row.commission)}</b></td>
                                </tr>
                            </c:forEach>
                            <c:if test="${empty report.ptRows}">
                                <tr><td colspan="5" class="muted">Chưa có buổi PT nào trong khoảng ngày.</td></tr>
                            </c:if>
                            </tbody>
                        </table>
                    </div>
                    <p class="report-foot">Hoa hồng vẫn tính cho buổi khách vắng, khách tự hủy và buổi PT hủy.</p>
                </section>
            </div>

            </c:if>
            </c:when>
            </c:choose>
        </main>
    </div>
</div>

<script src="${pageContext.request.contextPath}/js/pages/manager/manager-dashboard.js" defer></script>
</body>
</html>