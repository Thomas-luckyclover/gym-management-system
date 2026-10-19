<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="gms" uri="/WEB-INF/gms-functions.tld" %>
<%--
  MÀN "LỊCH TẬP CỦA TÔI" — /customer/pt-sessions  (CHỈ XEM)
  [CHOT 19/10] Khách không tự hủy / tự đổi buổi: muốn hủy hoặc đổi thì nhắn PT, PT cập nhật lịch giúp.
  Servlet gửi sang (đọc bằng ${tên}):
    dashboard                 : tên + mã khách ở header
    ptPackages                : thẻ "Gói PT của bạn" (gói Active)
    upcomingPage, historyPage : 1 trang của mỗi khối → .days (ngày) → .sessions (buổi)
    todayDate

  TÌM NHANH (Ctrl+F gõ tag): [HEADER] [TAB] [SAP-TOI] [LICH-SU] [PHAN-TRANG] [GOI-PT] [SDT-PT]
--%>

<%-- Biến dùng nhiều lần --%>
<c:set var="pageUrl" value="${pageContext.request.contextPath}/customer/pt-sessions" />
<c:set var="upcomingCurrent" value="${empty upcomingPage ? 1 : upcomingPage.currentPage}" />
<c:set var="historyCurrent" value="${empty historyPage ? 1 : historyPage.currentPage}" />

<%-- Tab mở sẵn: theo ?view=, mặc định "upcoming" --%>
<c:choose>
    <c:when test="${param.view eq 'packages' or param.view eq 'upcoming' or param.view eq 'history'}">
        <c:set var="activeView" value="${param.view}" />
    </c:when>
    <c:otherwise><c:set var="activeView" value="upcoming" /></c:otherwise>
</c:choose>

<!DOCTYPE html>
<html lang="vi">
<head>
    <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
    <title>Lịch tập của tôi · Super Gym</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/customer/dashboard.css?v=20261013c">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/customer/my-pt-sessions.css?v=20261019a">
    <script src="${pageContext.request.contextPath}/js/pages/customer/my-pt-sessions.js?v=20261019a" defer></script>
</head>
<body class="dock my-pt-sessions-page ${themeClass}">
<%@ include file="/WEB-INF/jsp/common/flash-toast.jspf" %>

<div class="app">
    <%@ include file="/WEB-INF/jsp/common/layout-customer.jspf" %>
    <div class="main">

        <%-- ===================== [HEADER] ===================== --%>
        <header class="topbar">
            <div class="tb-left"><h1 class="tb-title">Lịch tập của tôi</h1></div>
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

        <main class="content my-pt-sessions" id="main-content" tabindex="-1" data-active-view="${activeView}">
            <c:if test="${not empty dashboardError}">
                <div class="alert-banner danger" role="alert"><c:out value="${dashboardError}" /></div>
            </c:if>

            <%-- ===================== [TAB] 3 tab (JS ẩn/hiện khối, không tải lại trang) ===================== --%>
            <div class="my-pt-toolbar">
                <nav class="my-pt-view-tabs" aria-label="Lịch và gói PT" hidden>
                    <button type="button" data-session-view="upcoming">Sắp tới</button>
                    <button type="button" data-session-view="history">Lịch sử</button>
                    <button type="button" data-session-view="packages">Gói PT của tôi</button>
                </nav>
                <a class="btn btn-primary" href="${pageContext.request.contextPath}/customer/book-session"><svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path d="M12 5v14M5 12h14"/></svg>Đặt buổi lẻ</a>
            </div>

            <%-- ===================== [SAP-TOI] KHỐI 1: LỊCH TẬP SẮP TỚI ===================== --%>
            <section class="my-pt-panel my-pt-schedule-panel" data-session-panel="upcoming" aria-labelledby="pending-title">
                <div class="my-pt-heading">
                    <div class="my-pt-title-with-hint">
                        <h2 id="pending-title">Lịch tập sắp tới</h2>
                        <span class="pt-info"><button type="button" class="pt-info-trigger" aria-label="Giải thích lịch tập sắp tới" aria-describedby="my-pt-help-1"><span aria-hidden="true">i</span></button><span id="my-pt-help-1" class="pt-info-popover" role="tooltip">Các buổi chưa kết thúc, xếp theo ngày giờ gần nhất. Muốn hủy hoặc đổi buổi, hãy liên hệ PT để PT cập nhật lịch giúp bạn.</span></span>
                    </div>
                </div>

                <c:choose>
                    <c:when test="${not empty upcomingSessionsError}">
                        <p class="my-pt-package-error" role="alert"><c:out value="${upcomingSessionsError}" /></p>
                    </c:when>
                    <c:when test="${empty upcomingPage.days}">
                        <div class="my-pt-placeholder">
                            <p>Bạn không có buổi PT sắp tới.</p>
                            <span>Các buổi đã qua hoặc đã hủy thuộc lịch sử buổi tập.</span>
                        </div>
                    </c:when>
                    <c:otherwise>
                        <div class="my-pt-days">
                            <%-- Vòng ngoài: từng NGÀY --%>
                            <c:forEach var="day" items="${upcomingPage.days}">
                                <div class="my-pt-day ${day.date eq todayDate ? 'is-today' : ''}">
                                    <div class="my-pt-day-heading">
                                        <span class="my-pt-day-label">${day.date eq todayDate ? 'Hôm nay · ' : ''}${gms:formatDate(day.date)}</span>
                                        <span class="my-pt-day-count">${fn:length(day.sessions)} buổi</span>
                                    </div>
                                    <div class="my-pt-day-sessions">
                                        <%-- Vòng trong: từng BUỔI của ngày đó --%>
                                        <c:forEach var="s" items="${day.sessions}">
                                            <article class="my-pt-session">
                                                <div class="my-pt-session-time">
                                                    <p class="my-pt-session-hours"><span>${gms:formatTime(s.startTime)}</span><span class="my-pt-time-separator" aria-hidden="true">–</span><span>${gms:formatTime(s.endTime)}</span></p>
                                                </div>

                                                <div class="my-pt-session-content">
                                                    <div class="my-pt-session-heading">
                                                        <h3>PT <c:out value="${s.ptName}" /></h3>
                                                        <%-- [SDT-PT] Bấm số để gọi (trên điện thoại) --%>
                                                        <c:if test="${not empty s.ptPhone}">
                                                            <a class="my-pt-pt-phone" href="tel:${fn:escapeXml(s.ptPhone)}"><i class="fa-solid fa-phone" aria-hidden="true"></i> <c:out value="${s.ptPhone}" /></a>
                                                        </c:if>
                                                        <c:if test="${s.inProgress}"><span class="my-pt-package-type">Đang diễn ra</span></c:if>
                                                    </div>
                                                    <p class="my-pt-caption"><c:out value="${s.packageName}" /> · <c:out value="${s.typeLabel}" /></p>
                                                    <c:if test="${not empty s.warning}">
                                                        <p class="my-pt-session-warning"><c:out value="${s.warning}" /></p>
                                                    </c:if>
                                                    <c:if test="${not empty s.packageStartDate or not empty s.partnerName}">
                                                        <details class="my-pt-session-details">
                                                            <summary>Chi tiết buổi tập<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path d="m6 9 6 6 6-6"/></svg></summary>
                                                            <dl class="my-pt-session-facts">
                                                                <c:if test="${not empty s.packageStartDate}">
                                                                    <div><dt>Thời hạn gói</dt><dd>${gms:formatDate(s.packageStartDate)} – ${gms:formatDate(s.packageEndDate)}</dd></div>
                                                                    <div><dt>Mã gói</dt><dd>#${s.subscriptionId}</dd></div>
                                                                </c:if>
                                                                <c:if test="${not empty s.partnerName}">
                                                                    <div><dt>Tập cùng</dt><dd><c:out value="${s.partnerName}" /></dd></div>
                                                                </c:if>
                                                            </dl>
                                                        </details>
                                                    </c:if>
                                                </div>
                                            </article>
                                        </c:forEach>
                                    </div>
                                </div>
                            </c:forEach>
                        </div>
                    </c:otherwise>
                </c:choose>

                <%-- [PHAN-TRANG][SAP-TOI] Phân trang Sắp tới: đổi upcomingPage ±1, giữ trang Lịch sử --%>
                <c:if test="${upcomingPage.totalPages gt 1}">
                    <nav class="my-pt-pagination" aria-label="Phân trang Lịch tập sắp tới">
                        <span>Trang ${upcomingCurrent} / ${upcomingPage.totalPages} · ${upcomingPage.totalDays} ngày có buổi</span>
                        <div class="my-pt-pagination-actions">
                            <c:choose>
                                <c:when test="${upcomingCurrent gt 1}">
                                    <a class="btn btn-outline btn-sm" href="${pageUrl}?view=upcoming&amp;upcomingPage=${upcomingCurrent - 1}&amp;historyPage=${historyCurrent}#pending-title">Trước</a>
                                </c:when>
                                <c:otherwise><span class="btn btn-outline btn-sm" aria-disabled="true">Trước</span></c:otherwise>
                            </c:choose>
                            <c:choose>
                                <c:when test="${upcomingCurrent lt upcomingPage.totalPages}">
                                    <a class="btn btn-outline btn-sm" href="${pageUrl}?view=upcoming&amp;upcomingPage=${upcomingCurrent + 1}&amp;historyPage=${historyCurrent}#pending-title">Sau</a>
                                </c:when>
                                <c:otherwise><span class="btn btn-outline btn-sm" aria-disabled="true">Sau</span></c:otherwise>
                            </c:choose>
                        </div>
                    </nav>
                </c:if>
            </section>

            <%-- ===================== [LICH-SU] KHỐI 2: LỊCH SỬ BUỔI TẬP ===================== --%>
            <section class="my-pt-panel my-pt-schedule-panel" data-session-panel="history" aria-labelledby="history-title">
                <div class="my-pt-heading">
                    <div class="my-pt-title-with-hint">
                        <h2 id="history-title">Lịch sử buổi tập</h2>
                        <span class="pt-info"><button type="button" class="pt-info-trigger" aria-label="Giải thích lịch sử buổi tập" aria-describedby="my-pt-help-2"><span aria-hidden="true">i</span></button><span id="my-pt-help-2" class="pt-info-popover" role="tooltip">
                                Các buổi tập đã diễn ra và các buổi đã bị hủy.<br>
                                <strong>Kết quả đang cập nhật:</strong> buổi vừa kết thúc, kết quả sẽ có sau khi hết ngày tập.<br>
                                <strong>Đã check-in:</strong> bạn (hoặc người tập cùng, nếu là gói đôi) đã check-in vào gym hôm đó. Đây chỉ là ghi nhận vào cửa, không phải xác nhận đã tập xong với PT.<br>
                                <strong>Vắng / Đã hủy:</strong> lý do ghi trong phần chi tiết của từng buổi.
                            </span></span>
                    </div>
                </div>

                <c:choose>
                    <c:when test="${not empty historySessionsError}">
                        <p class="my-pt-package-error" role="alert"><c:out value="${historySessionsError}" /></p>
                    </c:when>
                    <c:when test="${empty historyPage.days}">
                        <div class="my-pt-placeholder">
                            <p>Bạn chưa có buổi tập nào trong lịch sử.</p>
                            <span>Buổi đã qua, đã vắng hoặc đã hủy sẽ xuất hiện tại đây.</span>
                        </div>
                    </c:when>
                    <c:otherwise>
                        <div class="my-pt-days">
                            <c:forEach var="day" items="${historyPage.days}">
                                <div class="my-pt-day ${day.date eq todayDate ? 'is-today' : ''}">
                                    <div class="my-pt-day-heading">
                                        <span class="my-pt-day-label">${day.date eq todayDate ? 'Hôm nay · ' : ''}${gms:formatDate(day.date)}</span>
                                        <span class="my-pt-day-count">${fn:length(day.sessions)} buổi</span>
                                    </div>
                                    <div class="my-pt-day-sessions">
                                        <c:forEach var="s" items="${day.sessions}">
                                            <article class="my-pt-session">
                                                <div class="my-pt-session-time">
                                                    <p class="my-pt-session-hours"><span>${gms:formatTime(s.startTime)}</span><span class="my-pt-time-separator" aria-hidden="true">–</span><span>${gms:formatTime(s.endTime)}</span></p>
                                                </div>
                                                <div class="my-pt-session-content">
                                                    <div class="my-pt-session-heading">
                                                        <h3>PT <c:out value="${s.ptName}" /></h3>
                                                        <div class="my-pt-session-status">
                                                            <span class="badge ${s.badgeClass}"><c:out value="${s.statusLabel}" /></span>
                                                        </div>
                                                    </div>
                                                    <p class="my-pt-caption"><c:out value="${s.packageName}" /> · <c:out value="${s.typeLabel}" /></p>
                                                    <details class="my-pt-session-details">
                                                        <summary>Chi tiết buổi tập<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path d="m6 9 6 6 6-6"/></svg></summary>
                                                        <dl class="my-pt-session-facts">
                                                            <c:if test="${not empty s.packageStartDate}">
                                                                <div><dt>Thời hạn gói</dt><dd>${gms:formatDate(s.packageStartDate)} – ${gms:formatDate(s.packageEndDate)}</dd></div>
                                                                <div><dt>Mã gói</dt><dd>#${s.subscriptionId}</dd></div>
                                                            </c:if>
                                                            <c:if test="${not empty s.partnerName}">
                                                                <div><dt>Tập cùng</dt><dd><c:out value="${s.partnerName}" /></dd></div>
                                                            </c:if>
                                                            <c:if test="${not empty s.explanation}">
                                                                <div class="my-pt-session-fact-wide"><dt>Kết quả buổi tập</dt><dd><c:out value="${s.explanation}" /></dd></div>
                                                            </c:if>
                                                            <c:if test="${not empty s.cancelledAt}">
                                                                <div><dt>Thời điểm hủy</dt><dd>${gms:formatDateTime(s.cancelledAt)}</dd></div>
                                                            </c:if>
                                                            <c:if test="${not empty s.cancelReason}">
                                                                <div class="my-pt-session-fact-wide"><dt>Lý do đã ghi nhận</dt><dd><c:out value="${s.cancelReason}" /></dd></div>
                                                            </c:if>
                                                        </dl>
                                                    </details>
                                                </div>
                                            </article>
                                        </c:forEach>
                                    </div>
                                </div>
                            </c:forEach>
                        </div>
                    </c:otherwise>
                </c:choose>

                <%-- [PHAN-TRANG][LICH-SU] Phân trang Lịch sử: đổi historyPage ±1, giữ trang Sắp tới --%>
                <c:if test="${historyPage.totalPages gt 1}">
                    <nav class="my-pt-pagination" aria-label="Phân trang Lịch sử buổi tập">
                        <span>Trang ${historyCurrent} / ${historyPage.totalPages} · ${historyPage.totalDays} ngày có buổi</span>
                        <div class="my-pt-pagination-actions">
                            <c:choose>
                                <c:when test="${historyCurrent gt 1}">
                                    <a class="btn btn-outline btn-sm" href="${pageUrl}?view=history&amp;historyPage=${historyCurrent - 1}&amp;upcomingPage=${upcomingCurrent}#history-title">Trước</a>
                                </c:when>
                                <c:otherwise><span class="btn btn-outline btn-sm" aria-disabled="true">Trước</span></c:otherwise>
                            </c:choose>
                            <c:choose>
                                <c:when test="${historyCurrent lt historyPage.totalPages}">
                                    <a class="btn btn-outline btn-sm" href="${pageUrl}?view=history&amp;historyPage=${historyCurrent + 1}&amp;upcomingPage=${upcomingCurrent}#history-title">Sau</a>
                                </c:when>
                                <c:otherwise><span class="btn btn-outline btn-sm" aria-disabled="true">Sau</span></c:otherwise>
                            </c:choose>
                        </div>
                    </nav>
                </c:if>
            </section>

            <%-- ===================== [GOI-PT] KHỐI 3: GÓI PT CỦA BẠN ===================== --%>
            <section class="my-pt-panel my-pt-packages-panel" data-session-panel="packages" aria-labelledby="packages-title">
                <div class="my-pt-heading">
                    <div class="my-pt-title-with-hint">
                        <h2 id="packages-title">Gói PT của bạn</h2>
                        <span class="pt-info"><button type="button" class="pt-info-trigger" aria-label="Giải thích thông tin gói PT" aria-describedby="my-pt-help-3"><span aria-hidden="true">i</span></button><span id="my-pt-help-3" class="pt-info-popover" role="tooltip">
                                <strong>Tổng số buổi:</strong> số buổi được tạo khi thanh toán gói.<br>
                                <strong>Buổi sắp tới:</strong> buổi còn trong lịch và chưa bắt đầu; không gồm buổi đang diễn ra, chờ kết quả hoặc đã hủy.<br>
                                <strong>Lượt đổi còn lại:</strong> theo giới hạn lúc mua gói. Với gói đôi, hai thành viên dùng chung số buổi và lượt đổi.
                            </span></span>
                    </div>
                </div>

                <c:choose>
                    <c:when test="${not empty ptPackagesError}">
                        <p class="my-pt-package-error" role="alert"><c:out value="${ptPackagesError}" /></p>
                    </c:when>
                    <c:when test="${empty ptPackages}">
                        <div class="my-pt-placeholder">
                            <p>Bạn chưa có gói PT đang hoạt động.</p>
                            <span>Bạn vẫn có thể đặt buổi PT lẻ.</span>
                        </div>
                    </c:when>
                    <c:otherwise>
                        <div class="my-pt-package-list">
                            <c:forEach var="p" items="${ptPackages}">
                                <article class="my-pt-package-card">
                                    <div class="my-pt-package-heading">
                                        <h3><c:out value="${p.packageName}" /></h3>
                                        <span class="my-pt-package-type"><c:out value="${p.typeLabel}" /></span>
                                    </div>
                                    <dl class="my-pt-package-details ${not empty p.partnerName ? 'my-pt-package-details-couple' : ''}">
                                        <div>
                                            <dt>PT phụ trách</dt>
                                            <dd>
                                                <c:out value="${p.ptName}" />
                                                <%-- [SDT-PT] --%>
                                                <c:if test="${not empty p.ptPhone}">
                                                    <br><a class="my-pt-pt-phone" href="tel:${fn:escapeXml(p.ptPhone)}"><i class="fa-solid fa-phone" aria-hidden="true"></i> <c:out value="${p.ptPhone}" /></a>
                                                </c:if>
                                            </dd>
                                        </div>
                                        <c:if test="${not empty p.partnerName}">
                                            <div><dt>Người tập cùng</dt><dd><c:out value="${p.partnerName}" /></dd></div>
                                        </c:if>
                                        <div><dt>Bắt đầu</dt><dd>${gms:formatDate(p.startDate)}</dd></div>
                                        <div><dt>Hạn gói</dt><dd>${gms:formatDate(p.endDate)}</dd></div>
                                    </dl>
                                    <dl class="my-pt-package-stats">
                                        <div><dt>Tổng số buổi</dt><dd>${p.generatedSessionCount}</dd></div>
                                        <div><dt>Buổi sắp tới</dt><dd>${p.upcomingSessionCount}</dd></div>
                                        <div><dt>Lượt đổi còn lại</dt><dd>${p.remainingSwaps}</dd></div>
                                    </dl>
                                </article>
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
