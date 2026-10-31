<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="gms" uri="/WEB-INF/gms-functions.tld" %>
<%--
================================================================================
 dashboard.jsp — TRANG CÁ NHÂN CỦA KHÁCH (/customer/dashboard, UC-41, SRS 9.1.1).

 AI GỬI DỮ LIỆU SANG?  CustomerDashboardServlet.doGet gọi CustomerDashboardService.getDashboard(accountId)
 → biến JSP "dashboard" (CustomerDashboardView). Lỗi thì dashboardError.

 Biến JSP                 Ai tạo / DAO đọc bảng nào
 ──────────────────────── ───────────────────────────────────────────────────────────────
 dashboard.fullName       DAO Persons.FullName
 dashboard.customerCode   DAO Customers.CustomerCode
 dashboard.customerStatus DAO Customers.Status (New | Active | Frozen | Expired)
 dashboard.canUseGym      CustomerDashboardDAO.canUseGymToday:
                          Subscriptions Membership/DayPass Active phủ hôm nay (không bảo lưu)
 dashboard.subscriptions  DAO Subscriptions (+ tên gói; FreezeRecords khi bảo lưu)
 dashboard.upcomingSessions  DAO Sessions + TimeSlots + Persons(tên PT) — 5 buổi PT gần nhất
 dashboard.banners        Service tính 5 loại cảnh báo ([CHỐT-D1]), không đọc bảng riêng

 BỐ CỤC
   1. Hero card: chào + ảnh đại diện + tên/mã khách + trạng thái + "vào được phòng tập không"
   2. Gói đang dùng + buổi PT sắp tới
   3. Banner cảnh báo (đỏ/vàng)
   4. Thời tiết (trang trí) + chuông thông báo (dùng chung common/*.jspf)
================================================================================
--%>
<!DOCTYPE html>
<html lang="vi">

    <head>
        <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
        <title>Trang cá nhân · Super Gym</title>
        <link rel="stylesheet"
              href="${pageContext.request.contextPath}/css/pages/customer/dashboard.css?v=20261031-compact?v=20261025city">
        <link rel="stylesheet"
              href="${pageContext.request.contextPath}/css/dashboard-weather.css?v=20261014s">
        <link rel="stylesheet"
              href="${pageContext.request.contextPath}/css/dashboard-comic.css?v=20261015h">
        <%-- Khối chào + thời tiết dùng chung với PT Dashboard (.pt-board__hero) --%>
        <link rel="stylesheet"
              href="${pageContext.request.contextPath}/css/dashboard-hero.css?v=20261025city">
        <link rel="stylesheet"
              href="https://fonts.googleapis.com/css2?family=Patrick+Hand&display=swap">
        <link rel="stylesheet"
              href="${pageContext.request.contextPath}/css/pages/customer/start-date-picker.css?v=4">
        <script src="${pageContext.request.contextPath}/js/pages/customer/start-date-picker.js?v=1" defer></script>
    </head>

    <body class="dock theme-pt customer-dashboard-page customer-weather-page weather-${weatherType}">

        <%@ include file="/WEB-INF/jsp/common/flash-toast.jspf" %>

        <div class="app">

            <%@ include file="/WEB-INF/jsp/common/layout-customer.jspf" %>

            <div class="main">

        <header class="topbar">
            <div class="tb-left">
                <h1 class="tb-title">Trang cá nhân</h1>
            </div>
            <div class="top-right">
                <%@ include file="/WEB-INF/jsp/common/notification-bell.jspf" %>
                <%@ include file="/WEB-INF/jsp/common/topbar-user.jspf" %>
            </div>
        </header>

                <main class="content" id="main-content" tabindex="-1">

                    <c:if test="${not empty dashboardError}">
                        <div class="alert-banner danger" role="alert">
                            <i class="fa-solid fa-circle-exclamation"></i>
                            <span>
                                <c:out value="${dashboardError}" />
                            </span>
                        </div>
                    </c:if>

                    <c:if test="${not empty dashboard}">
                        <%--=====Lời chào và thời tiết (cùng fragment với PT Dashboard)=====--%>
                        <c:set var="weatherGreetingName" value="${dashboard.fullName}" />
                        <c:set var="weatherAudience" value="customer" />
                        <section class="pt-board__hero cust-hero"
                                 aria-label="Lời chào và thời tiết hôm nay">
                            <%@ include file="/WEB-INF/jsp/common/dashboard-weather.jspf" %>
                            <%-- Nhịp của bạn: lấy từ dữ liệu dashboard sẵn có (gói hiệu
                                lực, buổi sắp tới) --%>
                            <c:set var="pulseNext"
                                   value="${empty dashboard.upcomingSessions ? null : dashboard.upcomingSessions[0]}" />
                            <dl class="pt-pulse">
                                <div>
                                    <dt>Ngày tập còn lại</dt>
                                    <dd>
                                        <c:out
                                            value="${empty dashboard.activeMembership ? 0 : dashboard.activeMembership.daysRemaining}" />
                                    </dd>
                                </div>
                                <div>
                                    <dt>Buổi PT kế tiếp</dt>
                                    <dd>
                                        <c:choose>
                                            <c:when test="${not empty pulseNext}">
                                                <c:out
                                                    value="${gms:formatTime(pulseNext.startTime)}" />
                                            </c:when>
                                            <c:otherwise>—</c:otherwise>
                                        </c:choose>
                                    </dd>
                                </div>
                                <div>
                                    <dt>Buổi sắp tới</dt>
                                    <dd>
                                        <c:out
                                            value="${empty dashboard.upcomingSessions ? 0 : fn:length(dashboard.upcomingSessions)}" />
                                    </dd>
                                </div>
                            </dl>
                        </section>

                        <c:set var="am" value="${dashboard.activeMembership}" />
                        <div class="split">
                            <div class="col-main">

                                <%-- Alert Banners --%>
                                <c:forEach var="b" items="${dashboard.banners}">
                                    <div class="alert-banner ${b.level eq 'danger' ? 'danger' : 'warn'}"
                                         role="alert">
                                        <i
                                            class="fa-solid ${b.level eq 'danger' ? 'fa-circle-exclamation' : 'fa-triangle-exclamation'}"></i>
                                        <span>
                                            <c:out value="${b.message}" />
                                        </span>
                                        <c:choose>
                                            <c:when test="${b.type eq 'EXPIRED'}">
                                                <a class="btn btn-primary btn-sm"
                                                   href="${pageContext.request.contextPath}/customer/packages/purchase">Mua
                                                    gói</a>
                                                </c:when>
                                                <c:when
                                                    test="${b.type eq 'EXPIRING' or b.type eq 'MISSING_MEMBERSHIP'}">
                                                <a class="btn btn-outline btn-sm"
                                                   href="${pageContext.request.contextPath}/customer/packages/purchase">Gia
                                                    hạn</a>
                                                </c:when>
                                                <c:when test="${b.type eq 'PENDING_ORDER'}">
                                                <a class="btn btn-primary btn-sm"
                                                   href="${pageContext.request.contextPath}/customer/transactions">Thanh
                                                    toán</a>
                                                </c:when>
                                            </c:choose>
                                    </div>
                                </c:forEach>

                                <%-- Lịch 7 ngày: Service tạo dashboard.scheduleDays; selectScheduleDay trong dashboard.js đổi ngày. --%>
                                <section class="panel customer-schedule" aria-label="Lịch PT 7 ngày">
                                    <div class="panel-head">
                                        <h2>Lịch tập của bạn</h2>
                                        <a class="btn btn-outline btn-sm" href="${pageContext.request.contextPath}/customer/pt-sessions">Tất cả lịch</a>
                                    </div>
                                    <div class="customer-week" aria-label="Chọn ngày tập">
                                        <c:forEach var="scheduleDay" items="${dashboard.scheduleDays}" varStatus="dayLoop">
                                            <button type="button" class="customer-day ${scheduleDay.today ? 'is-active' : ''}"
                                                    data-schedule-day="${dayLoop.index}" aria-controls="customer-day-${dayLoop.index}"
                                                    aria-pressed="${scheduleDay.today}">
                                                <span><c:out value="${scheduleDay.dayLabel}" /></span>
                                                <b><c:out value="${scheduleDay.dateLabel}" /></b>
                                                <small>${empty scheduleDay.sessions ? 'Trống' : fn:length(scheduleDay.sessions)}<c:if test="${not empty scheduleDay.sessions}"> buổi</c:if></small>
                                            </button>
                                        </c:forEach>
                                    </div>
                                    <c:forEach var="scheduleDay" items="${dashboard.scheduleDays}" varStatus="dayLoop">
                                        <div id="customer-day-${dayLoop.index}" class="customer-day-sessions" ${scheduleDay.today ? '' : 'hidden'}>
                                            <c:choose>
                                                <c:when test="${empty scheduleDay.sessions}">
                                                    <div class="customer-schedule-empty">
                                                        <p class="empty-state empty-state--relax">Không có buổi PT nào trong ngày này.</p>

                                                        <small>Chọn ngày để xem buổi tập đã xếp.</small>
                                                    </div>
                                                </c:when>
                                                <c:otherwise>
                                                    <c:forEach var="trainingSession" items="${scheduleDay.sessions}">
                                                        <div class="data-row">
                                                            <div class="row-main">
                                                                <b>${gms:formatTime(trainingSession.startTime)}–${gms:formatTime(trainingSession.endTime)}</b>
                                                                <p><c:out value="${trainingSession.ptName}" /> · <c:out value="${trainingSession.packageName}" /></p>
                                                            </div>
                                                            <span class="badge ${trainingSession.missingMembership ? 'badge-warning' : 'badge-neutral'}">${trainingSession.missingMembership ? 'Chưa có gói tập' : 'Đã xếp'}</span>
                                                        </div>
                                                    </c:forEach>
                                                </c:otherwise>
                                            </c:choose>
                                        </div>
                                    </c:forEach>
                                </section>
                                <%-- Gói tập: dữ liệu thật từ dashboard.subscriptions, giữ chức năng đổi ngày khi được phép. --%>
                                <section class="panel customer-packages" aria-label="Gói tập của bạn">
                                    <div class="panel-head"><h2>Gói tập của bạn</h2></div>
                                    <div class="customer-package-grid">
                                        <c:forEach var="subscription" items="${dashboard.subscriptions}">
                                            <c:set var="picker" value="${not subscription.ptPackage and subscription.subscriptionId == dashboard.activeMembership.subscriptionId ? dashboard.startPicker : null}" />
                                            <article class="customer-package-card" <c:if test="${picker.editable}">data-sdp data-sdp-url="/customer/subscription/start-date" data-sdp-sub="${picker.subscriptionId}" data-sdp-csrf="<c:out value='${csrfToken}' />"</c:if>>
                                                <small><c:out value="${subscription.ptPackage ? 'Gói tập cùng PT' : 'Gói tập gym'}" /> · <c:out value="${subscription.statusLabel}" /></small>
                                                <h3><c:out value="${subscription.packageName}" /></h3>
                                                <p>Hết hạn <strong data-sdp-end><c:out value="${gms:formatDate(subscription.endDate)}" /></strong></p>
                                                <c:choose>
                                                    <c:when test="${subscription.ptPackage}">
                                                        <b class="customer-package-balance">Còn ${subscription.remainingSessions == null ? 0 : subscription.remainingSessions}/${subscription.totalSessions == null ? 0 : subscription.totalSessions} buổi</b>
                                                    </c:when>
                                                    <c:otherwise>
                                                        <b class="customer-package-balance">Còn ${subscription.daysRemaining} ngày</b>
                                                    </c:otherwise>
                                                </c:choose>
                                                <c:if test="${picker.editable}">
                                                    <div class="customer-package-start">Bắt đầu <strong data-sdp-start><c:out value="${gms:formatDate(subscription.startDate)}" /></strong></div>
                                                    <button type="button" class="sdp-btn" data-sdp-toggle aria-expanded="false">Đổi ngày bắt đầu</button>
                                                    <%@ include file="/WEB-INF/jsp/customer/_start-date-picker.jspf" %>
                                                </c:if>
                                            </article>
                                        </c:forEach>
                                        <c:if test="${empty dashboard.subscriptions}"><p>Bạn chưa có gói tập.</p></c:if>
                                    </div>
                                    <a class="btn btn-outline btn-sm" href="${pageContext.request.contextPath}/customer/packages/purchase">Xem gói tập</a>
                                </section>


                            </div>

                            <%-- Cột phụ (col-side) --%>
                            <aside class="col-side">
                                <%-- PT của bạn: DAO chỉ đọc gói thuộc hội viên; ZaloUtil tạo liên kết từ số điện thoại. --%>
                                <section class="side-card customer-trainers">
                                    <h2>PT của bạn</h2>
                                    <c:forEach var="trainer" items="${dashboard.trainers}">
                                        <article class="customer-trainer">
                                            <h3><c:out value="${trainer.fullName}" /></h3>
                                            <c:forEach var="trainerPackage" items="${trainer.packages}">
                                                <p class="customer-trainer-package"><c:out value="${trainerPackage.packageName}" /> · còn <b>${trainerPackage.remainingSessions == null ? 0 : trainerPackage.remainingSessions} buổi</b></p>
                                            </c:forEach>
                                            <div class="customer-trainer-actions">
                                                <c:choose>
                                                    <c:when test="${not empty trainer.phoneUrl}">
                                                        <a class="btn btn-outline btn-sm" href="<c:out value='${trainer.phoneUrl}' />"><i class="fa-solid fa-phone" aria-hidden="true"></i> <c:out value="${trainer.phone}" /></a>
                                                        <c:if test="${not empty trainer.zaloUrl}">
                                                            <a class="btn btn-outline btn-sm customer-zalo" href="<c:out value='${trainer.zaloUrl}' />" target="_blank" rel="noopener noreferrer">Zalo ↗</a>
                                                        </c:if>
                                                    </c:when>
                                                    <c:otherwise><p>Chưa có số điện thoại liên hệ.</p></c:otherwise>
                                                </c:choose>
                                            </div>
                                        </article>
                                    </c:forEach>
                                    <c:if test="${empty dashboard.trainers}"><p>Bạn chưa có PT phụ trách gói tập.</p></c:if>
                                </section>
                                <%-- Lối tắt / Tiện ích --%>
                                <div class="side-card">
                                    <span class="section-label">Lối tắt</span>
                                    <div class="stack-3"
                                         style="margin-top: var(--space-3);">
                                        <a class="btn btn-outline btn-sm btn-block"
                                           href="${pageContext.request.contextPath}/customer/training-plans">
                                            <i
                                                class="fa-solid fa-clipboard-list"></i>
                                            Xem giáo án của tôi
                                        </a>
                                        <a class="btn btn-ghost btn-sm btn-block"
                                           href="${pageContext.request.contextPath}/customer/feedback">
                                            <i class="fa-solid fa-inbox"></i>
                                            Gửi phản ánh góp ý
                                        </a>
                                    </div>
                                </div>
                            </aside>
                        </div>
                    </c:if>

                </main>
            </div>
        </div>

        <script src="${pageContext.request.contextPath}/js/pages/customer/dashboard.js?v=20261031-compact" defer></script>
    </body>

</html>