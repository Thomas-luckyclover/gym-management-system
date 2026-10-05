<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>

<!DOCTYPE html>
<html lang="vi">

<head>
    <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
    <title>Trang cá nhân - GMS</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/customer/dashboard.css">
</head>

<body class="dock ${themeClass}">

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
                            <small>Hội viên</small>
                        </span>
                    </a>
                </div>
            </c:if>
        </header>

        <main class="content">

            <c:if test="${not empty dashboardError}">
                <div class="dash-banner dash-banner--danger" role="alert">
                    <i class="fa-solid fa-circle-exclamation"></i>
                    <span><c:out value="${dashboardError}" /></span>
                </div>
            </c:if>

            <c:if test="${not empty dashboard}">

                <%-- ===== Banner cảnh báo [CHỐT-D1]: đỏ trước, vàng sau (Service đã sắp xếp) ===== --%>
                <c:forEach var="b" items="${dashboard.banners}">
                    <div class="dash-banner dash-banner--${b.level}" role="alert">
                        <i class="fa-solid ${b.level eq 'danger' ? 'fa-circle-exclamation' : 'fa-triangle-exclamation'}"></i>
                        <span><c:out value="${b.message}" /></span>
                        <c:choose>
                            <c:when test="${b.type eq 'EXPIRED'}">
                                <a class="btn btn-primary" href="${pageContext.request.contextPath}/customer/packages/purchase">Mua gói</a>
                            </c:when>
                            <c:when test="${b.type eq 'EXPIRING' or b.type eq 'MISSING_MEMBERSHIP'}">
                                <a class="btn btn-primary" href="${pageContext.request.contextPath}/customer/packages/purchase">Gia hạn</a>
                            </c:when>
                            <c:when test="${b.type eq 'PENDING_ORDER'}">
                                <a class="btn btn-primary" href="${pageContext.request.contextPath}/customer/transactions">Thanh toán</a>
                            </c:when>
                        </c:choose>
                    </div>
                </c:forEach>

                <%-- ===== Hero: hồ sơ + quyền vào phòng tập ===== --%>
                <section class="dash-hero ${dashboard.canUseGym ? 'dash-hero--ok' : 'dash-hero--no'}">
                    <div class="dash-hero-info">
                        <span class="dash-hero-code">Mã hội viên: <b><c:out value="${dashboard.customerCode}" /></b></span>
                        <h2><c:out value="${dashboard.fullName}" /></h2>
                        <span class="dash-chip dash-chip--${dashboard.customerStatus eq 'Active' ? 'ok' : (dashboard.customerStatus eq 'Frozen' ? 'warn' : (dashboard.customerStatus eq 'Expired' ? 'bad' : 'muted'))}">
                            <c:out value="${dashboard.customerStatusLabel}" />
                        </span>
                    </div>
                    <div class="dash-hero-gym">
                        <c:choose>
                            <c:when test="${dashboard.canUseGym}">
                                <i class="fa-solid fa-circle-check"></i>
                                <b>Được vào phòng tập hôm nay</b>
                                <c:if test="${not empty dashboard.activeMembership}">
                                    <c:set var="am" value="${dashboard.activeMembership}" />
                                    <small>Gói hiệu lực đến ${am.endDate.dayOfMonth < 10 ? '0' : ''}${am.endDate.dayOfMonth}/${am.endDate.monthValue < 10 ? '0' : ''}${am.endDate.monthValue}/${am.endDate.year}</small>
                                </c:if>
                            </c:when>
                            <c:otherwise>
                                <i class="fa-solid fa-circle-xmark"></i>
                                <b>Chưa được vào phòng tập</b>
                                <small>Cần có gói tập hoặc vé ngày còn hiệu lực</small>
                            </c:otherwise>
                        </c:choose>
                    </div>
                </section>

                <div class="dash-grid">

                    <%-- ===== Gói tập [CHỐT-D2] ===== --%>
                    <section class="dash-card">
                        <h3>Gói tập của tôi</h3>
                        <c:choose>
                            <c:when test="${empty dashboard.subscriptions}">
                                <p class="dash-empty">Bạn chưa có gói tập nào.
                                    <a href="${pageContext.request.contextPath}/customer/packages/purchase">Mua gói ngay</a></p>
                            </c:when>
                            <c:otherwise>
                                <ul class="dash-list">
                                    <c:forEach var="s" items="${dashboard.subscriptions}">
                                        <li class="dash-item">
                                            <div class="dash-item-head">
                                                <b><c:out value="${s.packageName}" /></b>
                                                <span class="dash-chip dash-chip--${s.status eq 'Active' ? 'ok' : (s.status eq 'Frozen' or s.status eq 'Pending' ? 'warn' : 'muted')}">
                                                    <c:out value="${s.statusLabel}" />
                                                </span>
                                            </div>
                                            <small><c:out value="${s.subscriptionTypeLabel}" /> ·
                                                ${s.startDate.dayOfMonth < 10 ? '0' : ''}${s.startDate.dayOfMonth}/${s.startDate.monthValue < 10 ? '0' : ''}${s.startDate.monthValue}/${s.startDate.year}
                                                –
                                                ${s.endDate.dayOfMonth < 10 ? '0' : ''}${s.endDate.dayOfMonth}/${s.endDate.monthValue < 10 ? '0' : ''}${s.endDate.monthValue}/${s.endDate.year}
                                            </small>
                                            <c:if test="${s.ptPackage and not empty s.totalSessions}">
                                                <div class="dash-quota">
                                                    Đã dùng ${s.usedSessions} / ${s.totalSessions} buổi
                                                    <div class="dash-bar"><span style="width: ${s.totalSessions > 0 ? (s.usedSessions * 100 / s.totalSessions) : 0}%"></span></div>
                                                </div>
                                            </c:if>
                                        </li>
                                    </c:forEach>
                                </ul>
                            </c:otherwise>
                        </c:choose>
                    </section>

                    <%-- ===== Buổi PT sắp tới [CHỐT-D3] ===== --%>
                    <section class="dash-card">
                        <h3>Buổi PT sắp tới</h3>
                        <c:choose>
                            <c:when test="${empty dashboard.upcomingSessions}">
                                <p class="dash-empty">Chưa có buổi PT nào sắp tới.</p>
                            </c:when>
                            <c:otherwise>
                                <ul class="dash-list">
                                    <c:forEach var="u" items="${dashboard.upcomingSessions}">
                                        <li class="dash-item">
                                            <div class="dash-item-head">
                                                <b>${u.slotDate.dayOfMonth < 10 ? '0' : ''}${u.slotDate.dayOfMonth}/${u.slotDate.monthValue < 10 ? '0' : ''}${u.slotDate.monthValue}/${u.slotDate.year}
                                                    · ${u.startTime.hour < 10 ? '0' : ''}${u.startTime.hour}:${u.startTime.minute < 10 ? '0' : ''}${u.startTime.minute}
                                                    – ${u.endTime.hour < 10 ? '0' : ''}${u.endTime.hour}:${u.endTime.minute < 10 ? '0' : ''}${u.endTime.minute}</b>
                                                <c:if test="${u.missingMembership}">
                                                    <span class="dash-chip dash-chip--bad">Chưa có gói tập</span>
                                                </c:if>
                                            </div>
                                            <small>PT <c:out value="${u.ptName}" /> · <c:out value="${u.packageName}" /></small>
                                        </li>
                                    </c:forEach>
                                </ul>
                            </c:otherwise>
                        </c:choose>
                    </section>

                </div>
            </c:if>

        </main>
    </div>
</div>

</body>
</html>
