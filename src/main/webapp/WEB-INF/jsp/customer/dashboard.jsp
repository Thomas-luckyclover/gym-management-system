<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="gms" uri="/WEB-INF/gms-functions.tld" %>

<!DOCTYPE html>
<html lang="vi">

<head>
    <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
    <title>Trang cá nhân · Super Gym</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/base.css?v=4">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/awning.css?v=2">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/weather.css?v=2">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/customer/dashboard.css?v=4">
</head>

<body class="dock ${themeClass} weather-${weatherType}">

<%@ include file="/WEB-INF/jsp/common/flash-toast.jspf" %>

<div class="app">

    <%@ include file="/WEB-INF/jsp/common/layout-customer.jspf" %>

    <div class="main">

        <header class="topbar">
            <div class="tb-left">
                <h1 class="tb-title">Trang cá nhân</h1>
            </div>
            <c:if test="${not empty dashboard}">
                <div class="top-right">
                    <a class="tb-user" href="${pageContext.request.contextPath}/account" title="Cài đặt tài khoản">
                        <c:choose>
                            <c:when test="${not empty dashboard.avatar}">
                                <img class="avatar" src="${pageContext.request.contextPath}${dashboard.avatar}" alt="Ảnh đại diện">
                            </c:when>
                            <c:otherwise>
                                <span class="avatar"><c:out value="${dashboard.initials}" /></span>
                            </c:otherwise>
                        </c:choose>
                        <span class="tb-user-text">
                            <b><c:out value="${dashboard.fullName}" /></b>
                            <span><c:out value="${dashboard.customerCode}" /></span>
                        </span>
                    </a>
                </div>
            </c:if>
        </header>

        <main class="content">

            <c:if test="${not empty dashboardError}">
                <div class="alert-banner danger" role="alert">
                    <i class="fa-solid fa-circle-exclamation"></i>
                    <span><c:out value="${dashboardError}" /></span>
                </div>
            </c:if>

            <c:if test="${not empty dashboard}">
                <%-- Khối thời tiết dùng cùng WeatherService/API với PT Dashboard; các khối Customer bên dưới giữ nguyên. --%>
                <c:if test="${not empty weather}">
                    <section class="customer-weather" aria-label="Thời tiết hôm nay tại phòng gym">
                        <div class="customer-weather__copy">
                            <div class="customer-weather__eyebrow">
                                <span><c:out value="${weather.cityName}" /> · <c:out value="${weather.title}" /></span>
                                <c:if test="${weatherPreview}"><span class="customer-weather__preview">Xem trước</span></c:if>
                            </div>
                            <p class="customer-weather__condition">Hiện tại: <c:out value="${weather.conditionLabel}" /></p>
                            <p class="customer-weather__advice"><c:out value="${weather.advice}" /></p>
                        </div>
                        <strong class="customer-weather__temperature"><c:out value="${weather.temperature}" />°</strong>
                        <dl class="customer-weather__details">
                            <div><dt>Cảm giác</dt><dd><c:out value="${weather.feelsLikeTemperature}" />°</dd></div>
                            <div><dt>Thấp / cao</dt><dd><c:out value="${weather.minTemperature}" />° / <c:out value="${weather.maxTemperature}" />°</dd></div>
                            <div><dt>Độ ẩm</dt><dd><c:out value="${weather.humidityPercent}" />%</dd></div>
                            <div><dt>Gió</dt><dd><c:out value="${weather.windSpeedKmh}" /> km/h</dd></div>
                            <div><dt>Khả năng mưa</dt><dd><c:out value="${weather.rainProbabilityPercent}" />%</dd></div>
                        </dl>
                    </section>
                </c:if>
                <div class="split">
                    <div class="col-main">

                        <%-- Status Hero (Khớp prototype 10.1.1) --%>
                        <c:set var="am" value="${dashboard.activeMembership}" />
                        <section class="status-hero">
                            <div>
                                <c:choose>
                                    <c:when test="${not empty am}">
                                        <div class="section-label"><c:out value="${am.packageName}" /></div>
                                        <h2>Còn ${am.daysRemaining} ngày tập</h2>
                                        <div class="status-meta">
                                            <div>
                                                <b class="tabular"><c:out value="${gms:formatDate(am.endDate)}" /></b>
                                                <span>Hết hạn</span>
                                            </div>
                                            <c:set var="ptSub" value="${null}" />
                                            <c:forEach var="s" items="${dashboard.subscriptions}">
                                                <c:if test="${empty ptSub and s.ptPackage and s.status eq 'Active'}">
                                                    <c:set var="ptSub" value="${s}" />
                                                </c:if>
                                            </c:forEach>
                                            <c:if test="${not empty ptSub and not empty ptSub.totalSessions}">
                                                <div>
                                                    <b class="tabular">${ptSub.remainingSessions} / ${ptSub.totalSessions}</b>
                                                    <span>Buổi PT còn lại</span>
                                                </div>
                                            </c:if>
                                            <div>
                                                <b class="tabular"><c:out value="${dashboard.customerCode}" /></b>
                                                <span>Mã hội viên</span>
                                            </div>
                                        </div>
                                    </c:when>
                                    <c:otherwise>
                                        <div class="section-label">Tài khoản hội viên</div>
                                        <h2>Chưa có gói tập hiệu lực</h2>
                                        <div class="status-meta">
                                            <div>
                                                <b class="tabular"><c:out value="${dashboard.customerStatusLabel}" /></b>
                                                <span>Trạng thái</span>
                                            </div>
                                            <div>
                                                <b class="tabular"><c:out value="${dashboard.customerCode}" /></b>
                                                <span>Mã hội viên</span>
                                            </div>
                                        </div>
                                    </c:otherwise>
                                </c:choose>
                            </div>
                            <c:choose>
                                <c:when test="${not empty am}">
                                    <a class="btn btn-primary" href="${pageContext.request.contextPath}/customer/packages/purchase">Gia hạn <i class="fa-solid fa-arrow-right"></i></a>
                                </c:when>
                                <c:otherwise>
                                    <a class="btn btn-primary" href="${pageContext.request.contextPath}/customer/packages/purchase">Mua gói ngay <i class="fa-solid fa-arrow-right"></i></a>
                                </c:otherwise>
                            </c:choose>
                        </section>

                        <%-- Alert Banners --%>
                        <c:forEach var="b" items="${dashboard.banners}">
                            <div class="alert-banner ${b.level eq 'danger' ? 'danger' : 'warn'}" role="alert">
                                <i class="fa-solid ${b.level eq 'danger' ? 'fa-circle-exclamation' : 'fa-triangle-exclamation'}"></i>
                                <span><c:out value="${b.message}" /></span>
                                <c:choose>
                                    <c:when test="${b.type eq 'EXPIRED'}">
                                        <a class="btn btn-primary btn-sm" href="${pageContext.request.contextPath}/customer/packages/purchase">Mua gói</a>
                                    </c:when>
                                    <c:when test="${b.type eq 'EXPIRING' or b.type eq 'MISSING_MEMBERSHIP'}">
                                        <a class="btn btn-outline btn-sm" href="${pageContext.request.contextPath}/customer/packages/purchase">Gia hạn</a>
                                    </c:when>
                                    <c:when test="${b.type eq 'PENDING_ORDER'}">
                                        <a class="btn btn-primary btn-sm" href="${pageContext.request.contextPath}/customer/transactions">Thanh toán</a>
                                    </c:when>
                                </c:choose>
                            </div>
                        </c:forEach>

                        <%-- Panel: Buổi tập sắp tới --%>
                        <section class="panel">
                            <div class="panel-head">
                                <h2>Buổi tập sắp tới</h2>
                                <a class="btn btn-ghost btn-sm" href="${pageContext.request.contextPath}/customer/schedule">Tất cả lịch <i class="fa-solid fa-arrow-right"></i></a>
                            </div>
                            <c:choose>
                                <c:when test="${empty dashboard.upcomingSessions}">
                                    <div class="empty-state" style="padding: var(--space-8) var(--space-4); text-align: center; color: var(--color-text-soft);">
                                        <i class="fa-regular fa-calendar-xmark" style="font-size: 2rem; margin-bottom: var(--space-3); opacity: 0.6; display: block;"></i>
                                        <p>Chưa có buổi PT nào sắp tới.</p>
                                    </div>
                                </c:when>
                                <c:otherwise>
                                    <div class="data-list">
                                        <c:forEach var="u" items="${dashboard.upcomingSessions}">
                                            <div class="data-row">
                                                <div class="row-main">
                                                    <div class="row-title">
                                                        <c:out value="${gms:formatDate(u.slotDate)}" /> · <c:out value="${gms:formatTime(u.startTime)}" /> – <c:out value="${gms:formatTime(u.endTime)}" />
                                                    </div>
                                                    <div class="row-meta">
                                                        <span><i class="fa-solid fa-user-check"></i> <c:out value="${u.ptName}" /></span>
                                                        <span><i class="fa-solid fa-layer-group"></i> <c:out value="${u.packageName}" /></span>
                                                    </div>
                                                </div>
                                                <c:choose>
                                                    <c:when test="${u.missingMembership}">
                                                        <span class="badge badge-danger">Chưa có gói tập</span>
                                                    </c:when>
                                                    <c:otherwise>
                                                        <span class="badge badge-info">Đã xếp</span>
                                                    </c:otherwise>
                                                </c:choose>
                                            </div>
                                        </c:forEach>
                                    </div>
                                </c:otherwise>
                            </c:choose>
                        </section>

                    </div>

                    <%-- Cột phụ (col-side) --%>
                    <aside class="col-side">
                        <%-- Thẻ Gói tập --%>
                        <c:choose>
                            <c:when test="${not empty am}">
                                <div class="side-card">
                                    <span class="section-label">Gói tập</span>
                                    <h3><c:out value="${am.packageName}" /></h3>
                                    <div class="kpi-bar">
                                        <i style="width: 100%"></i>
                                    </div>
                                    <div class="side-row"><span>Bắt đầu</span><b><c:out value="${gms:formatDate(am.startDate)}" /></b></div>
                                    <div class="side-row"><span>Hết hạn</span><b><c:out value="${gms:formatDate(am.endDate)}" /></b></div>
                                    <div class="side-row"><span>Còn lại</span><b>${am.daysRemaining} ngày</b></div>
                                </div>
                            </c:when>
                            <c:otherwise>
                                <div class="side-card">
                                    <span class="section-label">Gói tập</span>
                                    <h3>Chưa có gói tập</h3>
                                    <p class="muted" style="margin-top: var(--space-2);">Bạn cần mua gói tập để vào phòng tập.</p>
                                    <a class="btn btn-primary btn-sm btn-block" style="margin-top: var(--space-4);" href="${pageContext.request.contextPath}/customer/packages/purchase">Mua gói</a>
                                </div>
                            </c:otherwise>
                        </c:choose>

                        <%-- Thẻ Gói PT (nếu có) --%>
                        <c:forEach var="s" items="${dashboard.subscriptions}">
                            <c:if test="${s.ptPackage}">
                                <div class="side-card">
                                    <span class="section-label"><c:out value="${s.packageName}" /></span>
                                    <h3>Còn ${s.remainingSessions != null ? s.remainingSessions : 0} buổi</h3>
                                    <div class="kpi-bar muted">
                                        <i style="width: ${s.totalSessions != null && s.totalSessions > 0 ? (s.remainingSessions * 100 / s.totalSessions) : 0}%"></i>
                                    </div>
                                    <div class="side-row"><span>Tổng số buổi</span><b>${s.totalSessions != null ? s.totalSessions : 0} buổi</b></div>
                                    <div class="side-row"><span>Đã dùng</span><b>${s.usedSessions != null ? s.usedSessions : 0} buổi</b></div>
                                    <div class="side-row"><span>Hạn dùng</span><b><c:out value="${gms:formatDate(s.endDate)}" /></b></div>
                                </div>
                            </c:if>
                        </c:forEach>

                        <%-- Lối tắt / Tiện ích --%>
                        <div class="side-card">
                            <span class="section-label">Lối tắt</span>
                            <div class="stack-3" style="margin-top: var(--space-3);">
                                <a class="btn btn-outline btn-sm btn-block" href="${pageContext.request.contextPath}/customer/training-plans">
                                    <i class="fa-solid fa-clipboard-list"></i> Xem giáo án của tôi
                                </a>
                                <a class="btn btn-ghost btn-sm btn-block" href="${pageContext.request.contextPath}/customer/feedback">
                                    <i class="fa-solid fa-inbox"></i> Gửi phản ánh góp ý
                                </a>
                            </div>
                        </div>
                    </aside>
                </div>
            </c:if>

        </main>
    </div>
</div>

</body>
</html>
