<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="gms" uri="/WEB-INF/gms-functions.tld" %>
<!DOCTYPE html>
<html lang="vi">
    <head>
        <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
        <title>Báo cáo vận hành hệ thống · Super Gym</title>
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/admin/dashboard.css?v=20261114b">
        <script src="${pageContext.request.contextPath}/js/pages/admin/dashboard.js?v=20261113v3" defer></script>
    </head>
    <body class="dock ${themeClass}">
        <%@ include file="/WEB-INF/jsp/common/flash-toast.jspf" %>
        <div class="app">
            <c:set var="activeSidebar" value="dashboard" />
            <%@ include file="/WEB-INF/jsp/common/layout-admin.jspf" %>

            <div class="main">
                <header class="topbar">
                    <div class="tb-left title-hint-row">
                        <h1 class="tb-title">Báo cáo vận hành hệ thống</h1>
                        <span class="pt-info">
                            <button type="button"
                                    class="pt-info-trigger"
                                    aria-label="Quy tắc tổng hợp số liệu báo cáo"
                                    aria-describedby="adminReportHint">
                                <span aria-hidden="true">i</span>
                            </button>
                            <span id="adminReportHint" class="pt-info-popover" role="tooltip">
                                <strong>Tổng doanh thu:</strong> Đã gồm VAT theo giá trị lưu trên đơn hoàn tất.<br>
                                <strong>Hoa hồng PT:</strong> Tính trên buổi tập hoàn thành và không hoàn tiền.<br>
                                <strong>Tài khoản bị khóa:</strong> Gồm khóa tạm sai mật khẩu trong kỳ và Admin khóa.<br>
                                <strong>Thay đổi tài khoản &amp; Thao tác nhạy cảm:</strong> Chỉ tính thao tác nghiệp vụ, loại trừ sự kiện đăng nhập thường.
                            </span>
                        </span>
                    </div>

                    <div class="top-right">
                        <%@ include file="/WEB-INF/jsp/common/notification-bell.jspf" %>
                        <%@ include file="/WEB-INF/jsp/common/topbar-user.jspf" %>
                    </div>
                </header>

                <main class="content" id="main-content" tabindex="-1">
                    <%-- Thanh lọc khoảng ngày & xuất CSV (UC-44) --%>
                    <section class="card filter-card" aria-label="Bộ lọc thời gian báo cáo">
                        <form id="reportFilterForm"
                              class="filter-bar"
                              method="get"
                              action="${pageContext.request.contextPath}/admin/dashboard"
                              novalidate>
                            <div class="filter-fields">
                                <div class="field">
                                    <label for="fromDate">Từ ngày</label>
                                    <input type="date"
                                           id="fromDate"
                                           name="fromDate"
                                           value="${fn:escapeXml(fromDateValue)}"
                                           data-initial-value="${fn:escapeXml(fromDateValue)}"
                                           data-label="Từ ngày"
                                           class="${not empty fromError ? 'invalid' : ''}"
                                           <c:if test="${not empty fromError}">aria-invalid="true" aria-describedby="fromDateError"</c:if>
                                           required>
                                    <p class="field-error" id="fromDateError" <c:if test="${empty fromError}">hidden</c:if>>
                                        <c:if test="${not empty fromError}">
                                            <i class="fa-solid fa-circle-xmark" aria-hidden="true"></i>
                                            <span><c:out value="${fromError}" /></span>
                                        </c:if>
                                    </p>
                                </div>

                                <div class="field">
                                    <label for="toDate">Đến ngày</label>
                                    <input type="date"
                                           id="toDate"
                                           name="toDate"
                                           value="${fn:escapeXml(toDateValue)}"
                                           data-initial-value="${fn:escapeXml(toDateValue)}"
                                           data-label="Đến ngày"
                                           class="${not empty toError ? 'invalid' : ''}"
                                           <c:if test="${not empty toError}">aria-invalid="true" aria-describedby="toDateError"</c:if>
                                           required>
                                    <p class="field-error" id="toDateError" <c:if test="${empty toError}">hidden</c:if>>
                                        <c:if test="${not empty toError}">
                                            <i class="fa-solid fa-circle-xmark" aria-hidden="true"></i>
                                            <span><c:out value="${toError}" /></span>
                                        </c:if>
                                    </p>
                                </div>
                            </div>

                            <div class="filter-controls">
                                <div class="range-presets"
                                     data-range-presets
                                     data-from="#fromDate"
                                     data-to="#toDate"
                                     role="group"
                                     aria-label="Chọn nhanh khoảng thời gian">
                                    <button type="button" class="chip" data-range="7">7 ngày</button>
                                    <button type="button" class="chip" data-range="30">30 ngày</button>
                                    <button type="button" class="chip" data-range="month">Tháng này</button>
                                </div>

                                <div class="filter-actions">
                                    <button type="submit" class="btn btn-primary">Xem báo cáo</button>

                                    <c:url var="exportCsvUrl" value="/admin/dashboard">
                                        <c:param name="fromDate" value="${fromDateValue}" />
                                        <c:param name="toDate" value="${toDateValue}" />
                                        <c:param name="export" value="csv" />
                                    </c:url>

                                    <c:choose>
                                        <c:when test="${isInvalidRange or not empty errorMessage}">
                                            <a id="exportCsv"
                                               class="btn btn-ghost is-disabled"
                                               href="#"
                                               aria-disabled="true"
                                               tabindex="-1"><i class="fa-solid fa-download" aria-hidden="true"></i> Xuất CSV</a>
                                        </c:when>
                                        <c:otherwise>
                                            <a id="exportCsv"
                                               class="btn btn-ghost"
                                               href="${exportCsvUrl}"
                                               data-exported-message="${fn:escapeXml(exportedMessage)}"><i class="fa-solid fa-download" aria-hidden="true"></i> Xuất CSV</a>
                                        </c:otherwise>
                                    </c:choose>
                                </div>
                            </div>
                        </form>
                    </section>

                    <%-- Thông báo lỗi hệ thống hoặc khoảng ngày trống dữ liệu --%>
                    <c:if test="${not empty errorMessage}">
                        <div class="notice notice-error" role="alert">
                            <i class="fa-solid fa-circle-exclamation" aria-hidden="true"></i>
                            <span><c:out value="${errorMessage}" /></span>
                        </div>
                    </c:if>

                    <c:if test="${empty errorMessage and not isInvalidRange and report != null}">
                        <c:if test="${emptyInRange}">
                            <div class="notice notice-info" role="status">
                                <i class="fa-solid fa-circle-info" aria-hidden="true"></i>
                                <span><c:out value="${noDataMessage}" /></span>
                            </div>
                        </c:if>

                        <%-- Nhóm 1: Vận hành & doanh thu --%>
                        <section class="report-section" aria-labelledby="sectionOpsTitle">
                            <div class="section-head">
                                <h2 id="sectionOpsTitle" class="section-title">Vận hành &amp; doanh thu</h2>
                            </div>

                            <div class="kpi-grid-4">
                                <div class="metric-card metric-card-primary">
                                    <span class="metric-label">Tổng doanh thu</span>
                                    <strong class="metric-value"><c:out value="${gms:formatMoney(report.totalRevenue)}" /></strong>
                                </div>
                                <div class="metric-card">
                                    <span class="metric-label">Lượt check-in</span>
                                    <strong class="metric-value"><c:out value="${report.checkInCount}" /></strong>
                                </div>
                                <div class="metric-card">
                                    <span class="metric-label">Lượt vé ngày</span>
                                    <strong class="metric-value"><c:out value="${report.dayPassVisits}" /></strong>
                                </div>
                                <div class="metric-card">
                                    <span class="metric-label">Doanh thu vé ngày</span>
                                    <strong class="metric-value"><c:out value="${gms:formatMoney(report.dayPassRevenue)}" /></strong>
                                </div>
                            </div>

                            <article class="card table-card">
                                <h3 class="card-title">Doanh thu theo từng gói</h3>
                                <div class="table-wrap">
                                    <table class="data-table">
                                        <thead>
                                            <tr>
                                                <th scope="col">Tên gói</th>
                                                <th scope="col" class="num">Giao dịch</th>
                                                <th scope="col" class="num">Doanh thu</th>
                                                <th scope="col" class="num">Trong đó VAT</th>
                                            </tr>
                                        </thead>
                                        <tbody>
                                            <c:choose>
                                                <c:when test="${empty report.revenueByPackage}">
                                                    <tr>
                                                        <td colspan="4" class="table-empty"><c:out value="${noDataMessage}" /></td>
                                                    </tr>
                                                </c:when>
                                                <c:otherwise>
                                                    <c:forEach var="row" items="${report.revenueByPackage}">
                                                        <tr>
                                                            <td><c:out value="${row.packageName}" /></td>
                                                            <td class="num"><c:out value="${row.transactionCount}" /></td>
                                                            <td class="num"><c:out value="${gms:formatMoney(row.revenue)}" /></td>
                                                            <td class="num"><c:out value="${gms:formatMoney(row.vatAmount)}" /></td>
                                                        </tr>
                                                    </c:forEach>
                                                </c:otherwise>
                                            </c:choose>
                                        </tbody>
                                    </table>
                                </div>
                            </article>

                            <article class="card table-card">
                                <h3 class="card-title">Hoa hồng theo PT</h3>
                                <div class="table-wrap">
                                    <table class="data-table">
                                        <thead>
                                            <tr>
                                                <th scope="col">Mã PT</th>
                                                <th scope="col">Họ tên</th>
                                                <th scope="col" class="num">Buổi được tính</th>
                                                <th scope="col" class="num">Tổng hoa hồng</th>
                                            </tr>
                                        </thead>
                                        <tbody>
                                            <c:choose>
                                                <c:when test="${empty report.ptCommissions}">
                                                    <tr>
                                                        <td colspan="4" class="table-empty"><c:out value="${noDataMessage}" /></td>
                                                    </tr>
                                                </c:when>
                                                <c:otherwise>
                                                    <c:forEach var="row" items="${report.ptCommissions}">
                                                        <tr>
                                                            <td class="mono"><c:out value="${row.employeeCode}" /></td>
                                                            <td><c:out value="${row.fullName}" /></td>
                                                            <td class="num"><c:out value="${row.sessionCount}" /></td>
                                                            <td class="num"><c:out value="${gms:formatMoney(row.commissionAmount)}" /></td>
                                                        </tr>
                                                    </c:forEach>
                                                </c:otherwise>
                                            </c:choose>
                                        </tbody>
                                    </table>
                                </div>
                            </article>
                        </section>

                        <%-- Nhóm 2: Audit & bảo mật --%>
                        <section class="report-section report-section-audit" aria-labelledby="sectionAuditTitle">
                            <div class="section-head">
                                <h2 id="sectionAuditTitle" class="section-title">Audit &amp; bảo mật</h2>
                                <%-- Phạm vi B: giữ liên kết gốc để phản ánh đúng hạng mục chưa triển khai --%>
                                <a class="btn btn-outline" href="${pageContext.request.contextPath}/admin/audit-log">Mở Audit Log</a>
                            </div>

                            <div class="audit-grid">
                                <div class="metric-card audit-card-login">
                                    <span class="metric-label">Hoạt động đăng nhập</span>
                                    <ul class="audit-stat-list">
                                        <li>
                                            <span>Đăng nhập thành công</span>
                                            <strong class="stat-num"><c:out value="${report.signInCount}" /></strong>
                                        </li>
                                        <li>
                                            <span>Đăng nhập thất bại</span>
                                            <strong class="stat-num"><c:out value="${report.signInFailedCount}" /></strong>
                                        </li>
                                        <li>
                                            <span>Khóa tạm (sai mật khẩu)</span>
                                            <strong class="stat-num"><c:out value="${report.tempLockedCount}" /></strong>
                                        </li>
                                    </ul>
                                </div>

                                <div class="metric-card audit-highlight">
                                    <span class="metric-label">Tài khoản bị khóa</span>
                                    <strong class="metric-value"><c:out value="${report.lockedAccountCount}" /></strong>
                                </div>

                                <div class="metric-card audit-highlight">
                                    <span class="metric-label">Thay đổi tài khoản</span>
                                    <strong class="metric-value"><c:out value="${report.accountAuditChangeCount}" /></strong>
                                </div>
                            </div>

                            <div class="audit-tables-grid">
                                <article class="card table-card">
                                    <h3 class="card-title">Thao tác nhạy cảm theo loại</h3>
                                    <div class="table-wrap">
                                        <table class="data-table">
                                            <thead>
                                                <tr>
                                                    <th scope="col">Thao tác</th>
                                                    <th scope="col" class="num">Số lần</th>
                                                </tr>
                                            </thead>
                                            <tbody>
                                                <c:choose>
                                                    <c:when test="${empty report.sensitiveActions}">
                                                        <tr>
                                                            <td colspan="2" class="table-empty"><c:out value="${noDataMessage}" /></td>
                                                        </tr>
                                                    </c:when>
                                                    <c:otherwise>
                                                        <c:forEach var="row" items="${report.sensitiveActions}">
                                                            <tr>
                                                                <td><c:out value="${row.actionLabel}" /></td>
                                                                <td class="num"><c:out value="${row.total}" /></td>
                                                            </tr>
                                                        </c:forEach>
                                                    </c:otherwise>
                                                </c:choose>
                                            </tbody>
                                        </table>
                                    </div>
                                </article>

                                <article class="card table-card">
                                    <h3 class="card-title">Thao tác nhạy cảm theo người thực hiện</h3>
                                    <div class="table-wrap">
                                        <table class="data-table">
                                            <thead>
                                                <tr>
                                                    <th scope="col">Người thực hiện</th>
                                                    <th scope="col">Vai trò</th>
                                                    <th scope="col" class="num">Số lần</th>
                                                </tr>
                                            </thead>
                                            <tbody>
                                                <c:choose>
                                                    <c:when test="${empty report.sensitiveActors}">
                                                        <tr>
                                                            <td colspan="3" class="table-empty"><c:out value="${noDataMessage}" /></td>
                                                        </tr>
                                                    </c:when>
                                                    <c:otherwise>
                                                        <c:forEach var="row" items="${report.sensitiveActors}">
                                                            <tr>
                                                                <td><c:out value="${row.actorName}" /></td>
                                                                <td><c:out value="${row.actorRoleLabel}" /></td>
                                                                <td class="num"><c:out value="${row.total}" /></td>
                                                            </tr>
                                                        </c:forEach>
                                                    </c:otherwise>
                                                </c:choose>
                                            </tbody>
                                        </table>
                                    </div>
                                </article>
                            </div>
                        </section>
                    </c:if>
                </main>
            </div>
        </div>
    </body>
</html>
