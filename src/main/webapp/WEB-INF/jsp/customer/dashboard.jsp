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
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/customer/dashboard-home.css?v=2">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/dashboard-weather.css?v=9">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/weather-scene.css?v=4">
    <script src="${pageContext.request.contextPath}/js/weather-scene.js?v=4" defer></script>
    <script src="${pageContext.request.contextPath}/js/pages/customer/dashboard-home.js?v=2" defer></script>
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
                <c:set var="cm" value="${dashboard.currentMembership}" />
                <c:set var="nextMem" value="${dashboard.nextMembership}" />
                <c:set var="base" value="${not empty cm ? cm : dashboard.expiredMembership}" />
                <c:set var="cs" value="${dashboard.membershipCardState}" />
                <c:set var="hasDetail" value="${not empty dashboard.planDetails}" />

                <%-- Có gói PT hoặc buổi PT: 2 cột (hàng 1: gói tập | gói PT, hàng 2: chuyên cần | buổi PT). Không có: dàn 1 cột. --%>
                <div class="cd-grid${dashboard.showPtArea ? '' : ' cd-grid--solo'}">

                    <%-- ===== 1. Gói tập: nhiều đen, ít vàng. Bấm vào thẻ hoặc "Chi tiết gói" để mở chi tiết ===== --%>
                    <section class="cd-plan cd-plan--${cs}" data-reveal data-plan-card="${hasDetail ? 'open' : ''}">
                        <div class="cd-plan__lab">
                            <span><c:out value="${not empty base ? base.packageName : 'Tài khoản hội viên'}" /></span>
                            <c:choose>
                                <c:when test="${cs eq 'EXPIRING'}"><em class="cd-pill">Sắp hết hạn</em></c:when>
                                <c:when test="${cs eq 'FROZEN'}"><em class="cd-pill">Đang bảo lưu</em></c:when>
                                <c:when test="${cs eq 'EXPIRED'}"><em class="cd-pill">Đã hết hạn</em></c:when>
                                <c:when test="${cs eq 'ACTIVE'}"><em class="cd-pill">${cm.statusLabel}</em></c:when>
                                <c:otherwise><em class="cd-pill"><c:out value="${dashboard.customerStatusLabel}" /></em></c:otherwise>
                            </c:choose>
                            <c:if test="${hasDetail}">
                                <button type="button" class="cd-plan__go" data-plan-open="cur">Chi tiết gói ›</button>
                            </c:if>
                        </div>

                        <c:choose>
                            <c:when test="${cs eq 'ACTIVE' or cs eq 'EXPIRING'}">
                                <div class="cd-plan__big"><b>${cm.daysRemaining}</b><span>ngày tập còn lại</span></div>
                                <p class="cd-plan__sub">Hết hạn <b><c:out value="${cm.endWeekdayLabel}" /> <c:out value="${gms:formatDate(cm.endDate)}" /></b></p>
                                <div class="cd-bar" style="--p: ${cm.elapsedPercent}%"><i></i></div>
                                <div class="cd-plan__row">
                                    <div class="cd-plan__next">
                                        <c:choose>
                                            <c:when test="${not empty nextMem}">
                                                <button type="button" class="cd-plan__link" data-plan-open="nxt">Kế tiếp: <b><c:out value="${nextMem.packageName}" /></b> · từ <c:out value="${gms:formatDate(nextMem.startDate)}" /> ›</button>
                                            </c:when>
                                            <c:when test="${cs eq 'EXPIRING'}">Chưa có gói kế tiếp. <b>Gia hạn trước để không mất ngày nào.</b></c:when>
                                            <c:otherwise>Chưa có gói kế tiếp.</c:otherwise>
                                        </c:choose>
                                    </div>
                                    <a class="cd-btn-gold cd-btn-gold--sm" href="${pageContext.request.contextPath}/customer/packages/purchase">Gia hạn thêm</a>
                                </div>
                            </c:when>
                            <c:when test="${cs eq 'FROZEN'}">
                                <div class="cd-plan__big cd-plan__big--text"><b>Bảo lưu</b><span>đến <c:out value="${gms:formatDate(cm.freezeEnd)}" /></span></div>
                                <p class="cd-plan__sub">Gói hết hạn <b><c:out value="${cm.endWeekdayLabel}" /> <c:out value="${gms:formatDate(cm.endDate)}" /></b>, sẽ cộng thêm số ngày bảo lưu · chưa vào phòng tập được</p>
                                <div class="cd-plan__row">
                                    <div class="cd-plan__next">Gói tự chạy lại sau khi hết bảo lưu.</div>
                                    <a class="cd-btn-gold cd-btn-gold--sm" href="${pageContext.request.contextPath}/customer/packages/purchase">Gia hạn thêm</a>
                                </div>
                            </c:when>
                            <c:when test="${cs eq 'EXPIRED'}">
                                <div class="cd-plan__big cd-plan__big--text"><b>Hết hạn</b><span>${dashboard.expiredMembership.daysSinceEnd} ngày trước</span></div>
                                <p class="cd-plan__sub">Hết hạn <b><c:out value="${base.endWeekdayLabel}" /> <c:out value="${gms:formatDate(base.endDate)}" /></b> · chưa vào phòng tập được</p>
                                <div class="cd-plan__row">
                                    <div class="cd-plan__next">Mua gói mới để tiếp tục tập.</div>
                                    <a class="cd-btn-gold cd-btn-gold--sm" href="${pageContext.request.contextPath}/customer/packages/purchase">Mua gói ngay</a>
                                </div>
                            </c:when>
                            <c:otherwise>
                                <div class="cd-plan__big cd-plan__big--text"><b>Chưa có gói</b></div>
                                <p class="cd-plan__sub">Bạn cần có gói tập để vào phòng tập.</p>
                                <div class="cd-plan__row">
                                    <div class="cd-plan__next"></div>
                                    <a class="cd-btn-gold cd-btn-gold--sm" href="${pageContext.request.contextPath}/customer/packages/purchase">Mua gói ngay</a>
                                </div>
                            </c:otherwise>
                        </c:choose>
                    </section>

                    <%-- ===== 2. Gói PT: nhiều vàng, có đen. Chỉ hiện khi có gói PT ===== --%>
                    <c:if test="${dashboard.showPtArea}">
                        <div class="cd-pt-stack">
                            <c:forEach var="s" items="${dashboard.subscriptions}">
                                <c:if test="${s.ptPackage}">
                                    <section class="cd-pt" data-reveal>
                                        <div class="cd-pt__lab">
                                            <span><c:out value="${s.packageName}" /></span>
                                            <em class="cd-pill">${s.pending ? 'Sắp bắt đầu' : s.statusLabel}</em>
                                        </div>
                                        <c:choose>
                                            <c:when test="${s.pending}">
                                                <div class="cd-pt__big"><b><c:out value="${s.startDayMonthLabel}" /></b><span>bắt đầu</span></div>
                                                <p class="cd-pt__sub">Còn ${s.daysUntilStart} ngày nữa · ${s.totalSessions != null ? s.totalSessions : 0} buổi<c:if test="${not empty s.ptName}"> · PT <b><c:out value="${s.ptName}" /></b></c:if></p>
                                            </c:when>
                                            <c:otherwise>
                                                <div class="cd-pt__big"><b>${s.remainingSessions != null ? s.remainingSessions : 0}</b><span>buổi còn lại</span></div>
                                                <p class="cd-pt__sub">Đã tập ${s.usedSessions != null ? s.usedSessions : 0}/${s.totalSessions != null ? s.totalSessions : 0} buổi<c:if test="${not empty s.ptName}"> · PT <b><c:out value="${s.ptName}" /></b></c:if></p>
                                                <div class="cd-bar cd-bar--pt" style="--p: ${s.usedPercent}%"><i></i></div>
                                            </c:otherwise>
                                        </c:choose>
                                        <div class="cd-pt__row">
                                            <div class="cd-pt__next">
                                                <c:if test="${not s.pending and s.subscriptionId eq dashboard.activePtPackage.subscriptionId and not empty dashboard.nextSession}">
                                                    Buổi kế tiếp <b><c:out value="${dashboard.nextSession.relativeLabel}" />, <c:out value="${gms:formatTime(dashboard.nextSession.startTime)}" /></b> ·
                                                </c:if>
                                                Hạn <b><c:out value="${gms:formatDate(s.endDate)}" /></b>
                                            </div>
                                            <a class="cd-pt__link" href="${pageContext.request.contextPath}/customer/pt-sessions">Xem lịch tập của tôi</a>
                                        </div>
                                    </section>
                                </c:if>
                            </c:forEach>
                        </div>
                    </c:if>

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

                    <%-- ===== 4. Buổi PT sắp tới + lối tắt (chỉ khi có gói PT) ===== --%>
                    <c:if test="${dashboard.showPtArea}">
                        <section class="cd-card cd-sess" data-reveal>
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
                            <div class="cd-shortrow">
                                <a class="cd-shortcut" href="${pageContext.request.contextPath}/customer/training-plans">
                                    <span class="cd-shortcut__ic"><i class="fa-solid fa-clipboard-list"></i></span>
                                    <span class="cd-shortcut__tx"><b>Giáo án của tôi</b><small>Kế hoạch tập PT giao cho bạn</small></span>
                                </a>
                                <a class="cd-shortcut" href="${pageContext.request.contextPath}/customer/feedback">
                                    <span class="cd-shortcut__ic"><i class="fa-solid fa-inbox"></i></span>
                                    <span class="cd-shortcut__tx"><b>Gửi phản ánh góp ý</b><small>Phòng tập sẽ phản hồi cho bạn</small></span>
                                </a>
                            </div>
                        </section>
                    </c:if>
                </div>

                <%-- ===== Khung chi tiết gói: mỗi gói một tab (đang dùng / kế tiếp). Dữ liệu là bản chép lúc mua (DBR-50) ===== --%>
                <c:if test="${hasDetail}">
                    <div class="cd-modal" data-plan-modal hidden>
                        <div class="cd-modal__box" role="dialog" aria-modal="true" aria-label="Chi tiết gói" tabindex="-1">
                            <div class="cd-modal__top">
                                <b>Chi tiết gói</b>
                                <button type="button" class="cd-modal__x" data-plan-close aria-label="Đóng">×</button>
                            </div>
                            <c:if test="${not empty base and not empty nextMem}">
                                <div class="cd-modal__tabs" role="tablist">
                                    <c:forEach var="pd" items="${dashboard.planDetails}">
                                        <button type="button" role="tab" class="cd-modal__tab" data-plan-tab="${pd.pending ? 'nxt' : 'cur'}">${pd.pending ? 'Gói kế tiếp' : (pd.status eq 'Expired' ? 'Gói đã hết hạn' : 'Gói đang dùng')}</button>
                                    </c:forEach>
                                </div>
                            </c:if>
                            <c:forEach var="pd" items="${dashboard.planDetails}">
                                <div class="cd-modal__panel" data-plan-panel="${pd.pending ? 'nxt' : 'cur'}">
                                    <div class="cd-modal__title">
                                        <div><b><c:out value="${pd.packageName}" /></b><span>Mã đơn <c:out value="${pd.orderCode}" /></span></div>
                                        <em class="cd-pill">${pd.pending ? 'Gói kế tiếp' : pd.statusLabel}</em>
                                    </div>
                                    <section class="cd-modal__sec">
                                        <h4>Thời hạn</h4>
                                        <dl>
                                            <dt>Bắt đầu</dt><dd><c:out value="${gms:formatDate(pd.startDate)}" /></dd>
                                            <dt>Hết hạn</dt><dd><c:out value="${gms:formatDate(pd.endDate)}" /></dd>
                                            <dt>Tổng thời hạn</dt><dd>${pd.totalDays} ngày</dd>
                                            <c:if test="${not pd.pending and pd.status ne 'Expired'}">
                                                <dt>Đã dùng</dt><dd>${pd.daysUsed} ngày</dd>
                                            </c:if>
                                            <c:if test="${not pd.pending and pd.freezeUsed != null}">
                                                <dt>Bảo lưu đã dùng</dt>
                                                <dd>${pd.freezeUsed}<c:if test="${pd.maxFreezes != null}"> / ${pd.maxFreezes}</c:if> lần</dd>
                                            </c:if>
                                        </dl>
                                    </section>
                                    <c:if test="${not empty pd.packageDescription or not empty pd.benefitLines}">
                                        <section class="cd-modal__sec">
                                            <h4>Mô tả gói (lúc bạn mua)</h4>
                                            <c:if test="${not empty pd.packageDescription}"><p><c:out value="${pd.packageDescription}" /></p></c:if>
                                            <c:if test="${not empty pd.benefitLines}">
                                                <ul>
                                                    <c:forEach var="line" items="${pd.benefitLines}"><li><c:out value="${line}" /></li></c:forEach>
                                                </ul>
                                            </c:if>
                                        </section>
                                    </c:if>
                                    <c:if test="${not pd.pending}">
                                        <section class="cd-modal__sec">
                                            <h4>Vào phòng tập</h4>
                                            <div class="cd-code">
                                                <b><c:out value="${dashboard.customerCode}" /></b>
                                                <span>Đọc mã này cho lễ tân khi vào tập</span>
                                            </div>
                                        </section>
                                    </c:if>
                                </div>
                            </c:forEach>
                            <div class="cd-modal__foot">
                                <button type="button" class="cd-modal__close" data-plan-close>Đóng</button>
                                <a class="cd-btn-gold cd-btn-gold--sm" href="${pageContext.request.contextPath}/customer/packages/purchase">${cs eq 'EXPIRED' or cs eq 'NONE' ? 'Mua gói ngay' : 'Gia hạn thêm'}</a>
                            </div>
                        </div>
                    </div>
                </c:if>
            </c:if>

        </main>
    </div>
</div>

</body>
</html>
