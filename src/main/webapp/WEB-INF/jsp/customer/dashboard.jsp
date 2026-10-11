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
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/base.css?v=8">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/awning.css?v=4">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/customer/dashboard.css?v=7">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/customer/dashboard-home.css?v=1">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/dashboard-weather.css?v=9">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/weather-scene.css?v=4">
    <script src="${pageContext.request.contextPath}/js/weather-scene.js?v=4" defer></script>
    <script src="${pageContext.request.contextPath}/js/pages/customer/dashboard-home.js?v=1" defer></script>
</head>

<body class="dock theme-pt customer-dashboard-page customer-weather-page weather-${weatherType}${showStreakIntro ? ' cd-intro-active' : ''}">

<%-- Màn chào chuỗi: chỉ hiện một lần sau khi đăng nhập, khi chuỗi >= 3 ngày (Servlet quyết định) --%>
<c:if test="${showStreakIntro and not empty dashboard}">
    <div class="cd-intro" role="dialog" aria-modal="true" aria-label="Chuỗi tập của bạn" data-intro tabindex="-1">
        <div class="cd-intro__glow" aria-hidden="true"></div>
        <div class="cd-intro__stage">
            <div class="cd-intro__flame" aria-hidden="true">
                <i class="cd-intro__ring"></i><i class="cd-intro__ring cd-intro__ring--2"></i>
                <svg viewBox="0 0 32 40">
                    <path d="M16 1c1 6 8 10 8 20a8 8 0 0 1-16 0c0-4 2-6 3-9 1 3 2 4 4 4-1-6 0-11 1-15z" fill="#FFB020"/>
                    <path d="M16 22c1 3 4 4 4 8a4 4 0 0 1-8 0c0-3 3-4 4-8z" fill="#F0592A"/>
                </svg>
                <span class="cd-intro__sparks" data-sparks></span>
            </div>
            <p class="cd-intro__kicker">Chuỗi tập của bạn</p>
            <p class="cd-intro__num"><b data-intro-count="${dashboard.attendance.streakDays}">${dashboard.attendance.streakDays}</b></p>
            <p class="cd-intro__unit">ngày liên tiếp</p>
            <p class="cd-intro__msg">
                <c:choose>
                    <c:when test="${dashboard.attendance.recordHolder}">Đây là kỷ lục của bạn. Giữ vững nhé!</c:when>
                    <c:otherwise>Giữ lửa nhé, mai gặp lại ở phòng tập.</c:otherwise>
                </c:choose>
            </p>
        </div>
        <div class="cd-intro__timer" aria-hidden="true"><i></i></div>
    </div>
</c:if>

<%@ include file="/WEB-INF/jsp/common/flash-toast.jspf" %>
<%@ include file="/WEB-INF/jsp/common/weather-scene.jspf" %>
<%@ include file="/WEB-INF/jsp/pt/_awning.jspf" %>

<div class="app">

    <%@ include file="/WEB-INF/jsp/common/layout-customer.jspf" %>

    <div class="main">

        <header class="topbar">
            <div class="tb-left">
                <h1 class="tb-title">Trang cá nhân</h1>
            </div>
            <c:if test="${not empty dashboard}">
                <div class="top-right">
                    <%-- Chuông thông báo: cảnh báo hệ thống tính từ DB + thông báo của Manager --%>
                    <div class="cd-bell" data-bell>
                        <button type="button" class="cd-bell__btn" aria-label="Thông báo" aria-haspopup="true" aria-expanded="false" data-bell-btn>
                            <i class="fa-regular fa-bell"></i>
                            <c:if test="${dashboard.bellCount > 0}">
                                <em class="cd-bell__count">${dashboard.bellCount}</em>
                            </c:if>
                        </button>
                        <div class="cd-bell__panel" role="region" aria-label="Danh sách thông báo" hidden data-bell-panel>
                            <div class="cd-bell__head"><b>Thông báo</b></div>
                            <c:if test="${empty dashboard.banners and empty dashboard.notifications}">
                                <div class="cd-bell__empty">Chưa có thông báo nào.</div>
                            </c:if>
                            <c:forEach var="b" items="${dashboard.banners}">
                                <div class="cd-bell__item ${b.level eq 'danger' ? 'is-danger' : 'is-warn'}">
                                    <i></i>
                                    <div>
                                        <b><c:out value="${b.message}" /></b>
                                        <c:choose>
                                            <c:when test="${b.type eq 'EXPIRED'}">
                                                <a href="${pageContext.request.contextPath}/customer/packages/purchase">Mua gói</a>
                                            </c:when>
                                            <c:when test="${b.type eq 'EXPIRING' or b.type eq 'MISSING_MEMBERSHIP'}">
                                                <a href="${pageContext.request.contextPath}/customer/packages/purchase">Gia hạn gói tập</a>
                                            </c:when>
                                            <c:when test="${b.type eq 'PENDING_ORDER'}">
                                                <a href="${pageContext.request.contextPath}/customer/transactions">Thanh toán</a>
                                            </c:when>
                                        </c:choose>
                                    </div>
                                </div>
                            </c:forEach>
                            <c:forEach var="n" items="${dashboard.notifications}">
                                <div class="cd-bell__item ${n.read ? 'is-read' : 'is-info'}">
                                    <i></i>
                                    <div>
                                        <b><c:out value="${n.title}" /></b>
                                        <span><c:out value="${n.content}" /></span>
                                        <small><c:out value="${gms:formatDateTime(n.sentAt)}" /></small>
                                    </div>
                                </div>
                            </c:forEach>
                        </div>
                    </div>
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
                <%-- ===== Lời chào và thời tiết (cùng fragment với PT Dashboard) ===== --%>
                <c:set var="weatherGreetingName" value="${dashboard.fullName}" />
                <c:set var="weatherAudience" value="customer" />
                <%@ include file="/WEB-INF/jsp/common/dashboard-weather.jspf" %>
                <div class="cd-grid">

                    <%-- ===== CỘT TRÁI: gói tập + chuyên cần (mọi thứ liên quan phòng tập) ===== --%>
                    <div class="cd-col">

                        <%-- 1. Gói tập --%>
                        <c:set var="am" value="${dashboard.activeMembership}" />
                        <c:set var="nextMem" value="${dashboard.nextMembership}" />
                        <section class="cd-plan" data-reveal>
                            <c:choose>
                                <c:when test="${not empty am}">
                                    <div class="cd-plan__label">
                                        <span><c:out value="${am.packageName}" /></span>
                                        <em class="cd-pill">${am.statusLabel}</em>
                                    </div>
                                    <div class="cd-plan__big"><b>${am.daysRemaining}</b><span>ngày tập còn lại</span></div>
                                    <p class="cd-plan__note">
                                        Dùng được đến <b><c:out value="${gms:formatDate(am.endDate)}" /></b>
                                        · đã dùng ${am.elapsedPercent}% thời hạn
                                    </p>
                                    <div class="cd-bar" style="--p: ${am.elapsedPercent}%"><i></i><u></u></div>
                                    <div class="cd-bar__dates">
                                        <span><c:out value="${gms:formatDate(am.startDate)}" /></span>
                                        <span><c:out value="${gms:formatDate(am.endDate)}" /></span>
                                    </div>
                                    <c:if test="${not empty nextMem}">
                                        <div class="cd-queued">
                                            <b>Đã gia hạn</b>
                                            <span><c:out value="${nextMem.packageName}" /> bắt đầu <c:out value="${gms:formatDate(nextMem.startDate)}" /></span>
                                        </div>
                                    </c:if>
                                    <div class="cd-code">
                                        <div><small>Mã khách hàng</small><b><c:out value="${dashboard.customerCode}" /></b></div>
                                        <span>Đọc mã này cho lễ tân khi vào tập</span>
                                    </div>
                                    <div class="cd-plan__actions">
                                        <a class="cd-btn-gold" href="${pageContext.request.contextPath}/customer/packages/purchase">
                                            <c:out value="${not empty nextMem ? 'Gia hạn thêm' : 'Gia hạn ngay'}" />
                                        </a>
                                        <c:if test="${not empty nextMem}">
                                            <span class="cd-plan__hint">Gói mới nối tiếp, không mất ngày nào.</span>
                                        </c:if>
                                    </div>
                                </c:when>
                                <c:otherwise>
                                    <div class="cd-plan__label"><span>Tài khoản hội viên</span><em class="cd-pill"><c:out value="${dashboard.customerStatusLabel}" /></em></div>
                                    <div class="cd-plan__big cd-plan__big--text"><span>Chưa có gói tập hiệu lực</span></div>
                                    <p class="cd-plan__note">Bạn cần có gói tập để vào phòng tập.</p>
                                    <c:if test="${not empty nextMem}">
                                        <div class="cd-queued">
                                            <b>Đã mua</b>
                                            <span><c:out value="${nextMem.packageName}" /> bắt đầu <c:out value="${gms:formatDate(nextMem.startDate)}" /></span>
                                        </div>
                                    </c:if>
                                    <div class="cd-code">
                                        <div><small>Mã khách hàng</small><b><c:out value="${dashboard.customerCode}" /></b></div>
                                        <span>Đọc mã này cho lễ tân khi vào tập</span>
                                    </div>
                                    <div class="cd-plan__actions">
                                        <a class="cd-btn-gold" href="${pageContext.request.contextPath}/customer/packages/purchase">Mua gói ngay</a>
                                    </div>
                                </c:otherwise>
                            </c:choose>
                        </section>

                        <%-- 3. Chuyên cần tháng này --%>
                        <c:set var="att" value="${dashboard.attendance}" />
                        <section class="cd-card cd-att" data-reveal>
                            <div class="cd-card__label">Chuyên cần tháng này</div>
                            <div class="cd-att__top">
                                <div class="cd-streak ${att.streakDays > 0 ? 'is-lit' : 'is-unlit'}">
                                    <div class="cd-flame" aria-hidden="true">
                                        <svg viewBox="0 0 32 40">
                                            <path d="M16 1c1 6 8 10 8 20a8 8 0 0 1-16 0c0-4 2-6 3-9 1 3 2 4 4 4-1-6 0-11 1-15z" fill="#FFB020"/>
                                            <path d="M16 22c1 3 4 4 4 8a4 4 0 0 1-8 0c0-3 3-4 4-8z" fill="#F0592A"/>
                                        </svg>
                                        <c:if test="${att.streakDays > 0}">
                                            <i class="cd-spark" style="--dx:-34px;--dy:-58px;--d:.30s"></i>
                                            <i class="cd-spark" style="--dx:30px;--dy:-66px;--d:.38s"></i>
                                            <i class="cd-spark" style="--dx:-14px;--dy:-80px;--d:.46s"></i>
                                            <i class="cd-spark" style="--dx:44px;--dy:-40px;--d:.34s"></i>
                                            <i class="cd-spark" style="--dx:-46px;--dy:-30px;--d:.42s"></i>
                                            <i class="cd-spark" style="--dx:10px;--dy:-92px;--d:.50s"></i>
                                        </c:if>
                                    </div>
                                    <div class="cd-streak__num"><b data-countup="${att.streakDays}">${att.streakDays}</b></div>
                                    <div class="cd-streak__text">
                                        <b>ngày liên tiếp</b>
                                        <span>trong tháng này</span>
                                    </div>
                                </div>
                                <div class="cd-facts">
                                    <div><b>${att.thisMonthCount}</b><span>buổi tháng này</span></div>
                                    <div><b>${att.lastMonthCount}</b><span>buổi tháng trước</span></div>
                                    <div><b>${att.recordStreak}</b><span>ngày kỷ lục</span></div>
                                    <c:choose>
                                        <c:when test="${att.recordHolder}">
                                            <div><b><i class="fa-solid fa-trophy"></i></b><span>đang giữ kỷ lục</span></div>
                                        </c:when>
                                        <c:otherwise>
                                            <div><b>${att.daysToBeatRecord}</b><span>ngày nữa phá kỷ lục</span></div>
                                        </c:otherwise>
                                    </c:choose>
                                </div>
                            </div>
                            <p class="cd-att__msg">
                                <c:choose>
                                    <c:when test="${att.checkedInToday}">Hôm nay bạn đã tập. Hẹn gặp lại ngày mai.</c:when>
                                    <c:when test="${att.streakDays > 0}">Hôm nay chưa tập · tập để giữ chuỗi.</c:when>
                                    <c:otherwise>Check-in hôm nay để bắt đầu chuỗi mới.</c:otherwise>
                                </c:choose>
                            </p>

                            <div class="cd-cal">
                                <div class="cd-cal__head">
                                    <b><c:out value="${att.monthLabel}" /></b>
                                </div>
                                <div class="cd-cal__week"><em>T2</em><em>T3</em><em>T4</em><em>T5</em><em>T6</em><em>T7</em><em>CN</em></div>
                                <div class="cd-cal__grid">
                                    <c:if test="${att.leadingBlanks > 0}">
                                        <c:forEach begin="1" end="${att.leadingBlanks}"><span class="cd-day is-blank"></span></c:forEach>
                                    </c:if>
                                    <c:forEach var="cell" items="${att.cells}">
                                        <span class="cd-day is-${cell.state} ${cell.today ? 'is-today' : ''}"
                                              <c:if test="${cell.today}">aria-current="date"</c:if>>${cell.day}</span>
                                    </c:forEach>
                                </div>
                                <div class="cd-cal__legend">
                                    <span><i class="is-checked"></i>Đã check-in</span>
                                    <span><i class="is-closed"></i>Phòng đóng cửa hoặc bảo lưu</span>
                                    <span><i class="is-future"></i>Chưa tới</span>
                                </div>
                            </div>
                        </section>
                    </div>

                    <%-- ===== CỘT PHẢI: chỉ liên quan PT ===== --%>
                    <div class="cd-col cd-col--pt">

                        <%-- Gói PT --%>
                        <c:forEach var="s" items="${dashboard.subscriptions}">
                            <c:if test="${s.ptPackage}">
                                <section class="cd-pt">
                                    <div class="cd-pt__label"><c:out value="${s.packageName}" /></div>
                                    <div class="cd-pt__big">
                                        <b>${s.remainingSessions != null ? s.remainingSessions : 0}</b>
                                        <span>buổi còn lại</span>
                                    </div>
                                    <div class="cd-bar cd-bar--pt" style="--p: ${s.usedPercent}%"><i></i></div>
                                    <div class="cd-bar__dates">
                                        <span>Đã tập ${s.usedSessions != null ? s.usedSessions : 0}</span>
                                        <span>Tổng ${s.totalSessions != null ? s.totalSessions : 0} buổi</span>
                                    </div>
                                    <div class="cd-pt__row"><span>Hạn dùng</span><b><c:out value="${gms:formatDate(s.endDate)}" /></b></div>
                                    <c:if test="${s.status eq 'Pending'}">
                                        <div class="cd-pt__row"><span>Trạng thái</span><b>Chưa bắt đầu</b></div>
                                    </c:if>
                                    <a class="cd-pt__link" href="${pageContext.request.contextPath}/customer/pt-sessions">Xem lịch tập của tôi</a>
                                </section>
                            </c:if>
                        </c:forEach>

                        <%-- 2. Buổi PT sắp tới --%>
                        <section class="cd-card cd-next" data-reveal>
                            <div class="cd-card__head">
                                <h2>Buổi PT sắp tới</h2>
                                <a class="cd-link" href="${pageContext.request.contextPath}/customer/pt-sessions">Xem lịch tập của tôi</a>
                            </div>
                            <c:choose>
                                <c:when test="${empty dashboard.upcomingSessions}">
                                    <div class="cd-empty">
                                        <i class="fa-regular fa-calendar-xmark"></i>
                                        <p>Chưa có buổi PT nào sắp tới.</p>
                                    </div>
                                </c:when>
                                <c:otherwise>
                                    <c:forEach var="u" items="${dashboard.upcomingSessions}" varStatus="st">
                                        <div class="cd-session ${st.first ? 'is-first' : ''} ${u.missingMembership ? 'is-warn' : ''}">
                                            <div class="cd-session__date">
                                                <small><c:out value="${u.weekdayLabel}" /></small>
                                                <b>${u.dayOfMonth}</b>
                                            </div>
                                            <div class="cd-session__info">
                                                <c:if test="${st.first}"><em><c:out value="${u.relativeLabel}" /></em></c:if>
                                                <b><c:out value="${gms:formatTime(u.startTime)}" /> – <c:out value="${gms:formatTime(u.endTime)}" /></b>
                                                <span>
                                                    <c:out value="${u.ptName}" /> · <c:out value="${u.packageName}" />
                                                    <c:if test="${u.missingMembership}">
                                                        · <a href="${pageContext.request.contextPath}/customer/packages/purchase">Chưa có gói tập · Gia hạn</a>
                                                    </c:if>
                                                </span>
                                            </div>
                                        </div>
                                    </c:forEach>
                                </c:otherwise>
                            </c:choose>
                            <div class="cd-next__foot">
                                <a class="cd-btn-gold cd-btn-gold--sm" href="${pageContext.request.contextPath}/customer/packages/purchase">Đặt buổi lẻ</a>
                                <span>Buổi lẻ thanh toán online, PT sẽ xác nhận lịch.</span>
                            </div>
                        </section>

                        <%-- Lối tắt --%>
                        <a class="cd-shortcut" href="${pageContext.request.contextPath}/customer/training-plans">
                            <span class="cd-shortcut__ic"><i class="fa-solid fa-clipboard-list"></i></span>
                            <span class="cd-shortcut__tx"><b>Giáo án của tôi</b><small>Kế hoạch tập PT giao cho bạn</small></span>
                            <i class="fa-solid fa-arrow-right cd-shortcut__go"></i>
                        </a>
                        <a class="cd-shortcut" href="${pageContext.request.contextPath}/customer/feedback">
                            <span class="cd-shortcut__ic"><i class="fa-solid fa-inbox"></i></span>
                            <span class="cd-shortcut__tx"><b>Gửi phản ánh góp ý</b><small>Phòng tập sẽ phản hồi cho bạn</small></span>
                            <i class="fa-solid fa-arrow-right cd-shortcut__go"></i>
                        </a>
                    </div>
                </div>
            </c:if>

        </main>
    </div>
</div>

</body>
</html>
