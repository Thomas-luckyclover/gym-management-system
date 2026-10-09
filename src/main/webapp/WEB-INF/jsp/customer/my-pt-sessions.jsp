<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>

<!DOCTYPE html>
<html lang="vi">
<head>
    <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
    <title>Buổi PT của tôi · Super Gym</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/base.css?v=4">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/awning.css?v=2">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/weather.css?v=2">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/customer/dashboard.css?v=4">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/customer/my-pt-sessions.css?v=2">
</head>

<body class="dock ${themeClass}">
<%@ include file="/WEB-INF/jsp/common/flash-toast.jspf" %>

<div class="app">
    <%@ include file="/WEB-INF/jsp/common/layout-customer.jspf" %>

    <div class="main">
        <header class="topbar">
            <div class="tb-left">
                <h1 class="tb-title">Buổi PT của tôi</h1>
                <p class="tb-subtitle">Lịch tập và lịch sử các buổi đã đăng ký.</p>
            </div>

            <c:if test="${not empty dashboard}">
                <div class="top-right">
                    <a class="tb-user" href="${pageContext.request.contextPath}/account" title="Cài đặt tài khoản">
                        <span class="avatar"><c:out value="${dashboard.initials}" /></span>
                        <span class="tb-user-text">
                            <b><c:out value="${dashboard.fullName}" /></b>
                            <span><c:out value="${dashboard.customerCode}" /></span>
                        </span>
                    </a>
                </div>
            </c:if>
        </header>

        <main class="content my-pt-sessions">
            <c:if test="${not empty dashboardError}">
                <div class="alert-banner danger" role="alert">
                    <c:out value="${dashboardError}" />
                </div>
            </c:if>

            <section class="my-pt-sessions__intro" aria-labelledby="my-pt-title">
                <div class="my-pt-sessions__intro-copy">
                    <span class="section-label">LỊCH TẬP CỦA BẠN</span>
                    <h2 id="my-pt-title">Tất cả buổi PT</h2>
                    <p>Những buổi sắp tới và lịch sử tập được gom tại đây.</p>
                </div>
                <a class="btn btn-primary" href="${pageContext.request.contextPath}/customer/book-session">
                    Đặt buổi lẻ <i class="fa-solid fa-arrow-right"></i>
                </a>
            </section>

            <section class="my-pt-sessions__panel" aria-labelledby="session-list-title">
                <div class="panel-head">
                    <div>
                        <span class="section-label">LỊCH BUỔI PT</span>
                        <h2 id="session-list-title">Lịch tập của bạn</h2>
                    </div>
                    <span class="pt-info">
                        <button type="button" class="pt-info-trigger" aria-label="Thông tin về trạng thái buổi PT" aria-describedby="session-status-help"><span aria-hidden="true">i</span></button>
                        <span id="session-status-help" class="pt-info-popover" role="tooltip">Buổi sắp tới, đã check-in, vắng và đã hủy sẽ được phân nhóm rõ ràng.</span>
                    </span>
                </div>

                <c:choose>
                    <c:when test="${empty ptSessions}">
                        <div class="my-pt-sessions__empty">
                            <span class="my-pt-sessions__empty-icon"><i class="fa-regular fa-calendar"></i></span>
                            <h3>Bạn chưa có buổi PT nào</h3>
                            <p>Đặt một buổi PT để bắt đầu lịch tập của bạn.</p>
                            <a class="btn btn-outline btn-sm" href="${pageContext.request.contextPath}/customer/book-session">Xem lịch PT</a>
                        </div>
                    </c:when>
                    <c:otherwise>
                        <div class="my-pt-sessions__list">
                            <c:forEach var="session" items="${ptSessions}">
                                <article class="my-pt-session-card"><c:out value="${session}" /></article>
                            </c:forEach>
                        </div>
                    </c:otherwise>
                </c:choose>
            </section>
        </main>
    </div>
</div>
</body>
</html>
