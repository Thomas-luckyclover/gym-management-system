<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="gms" uri="/WEB-INF/gms-functions.tld" %>
<%--
    PtDashboardServlet truyền từng khối riêng: scheduleDays, attentionItems,
    assignedPackages, commissionSummary và weather. Mỗi khối có
    attribute *Error riêng; JSP không coi lỗi tải dữ liệu là danh sách rỗng.
--%>
<!DOCTYPE html>
<html lang="vi">
<head>
    <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
    <title><c:out value="${pageTitle}" /> · GMS</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/auth/account-settings.css?v=2">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/pt/dashboard.css?v=20261013j">
    <script src="${pageContext.request.contextPath}/js/pages/pt/dashboard.js?v=9" defer></script>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/dashboard-weather.css?v=20261014s">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/dashboard-comic.css?v=20261015h">
    <link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Patrick+Hand&display=swap">
</head>
<%-- weatherType do Servlet kiểm tra: sunny/cloudy/rain/storm/snow/wind/night/hot. --%>
<body class="dock theme-pt pt-dashboard-page weather-${weatherType}">
    <%@ include file="/WEB-INF/jsp/common/flash-toast.jspf" %>

    <div class="app">
        <%@ include file="/WEB-INF/jsp/common/layout-pt.jspf" %>
        <div class="main">
            <%@ include file="/WEB-INF/jsp/pt/_header.jspf" %>

            <main class="content pt-dashboard pt-board" id="main-content" tabindex="-1">
                    <c:set var="todayCount" value="0" />
                    <c:set var="nextSession" value="${null}" />
                    <c:forEach var="statDay" items="${scheduleDays}">
                        <c:if test="${statDay.today}">
                            <c:set var="todayCount" value="${fn:length(statDay.sessions)}" />
                            <c:if test="${not empty statDay.sessions}"><c:set var="nextSession" value="${statDay.sessions[0]}" /></c:if>
                        </c:if>
                    </c:forEach>

                <%-- Khối chào + thời tiết (animation) giữ nguyên, đưa lên đầu trang. --%>
                <section class="pt-board__hero" aria-label="Phòng tập và thời tiết hôm nay">
                    <c:set var="weatherGreetingName" value="${sessionScope.displayName}" />
                    <c:set var="weatherAudience" value="pt" />
                    <%@ include file="/WEB-INF/jsp/common/dashboard-weather.jspf" %>
                </section>

                <%-- ===== Lịch 7 ngày (việc chính của PT) =====
                     scheduleDays: Service tạo đủ bảy ngày; day.sessions là buổi DAO đọc, Service bổ sung nhãn.
                     Dải ngày là tab: hôm nay = vạch cam + chữ HÔM NAY, ngày đang chọn = nền đen. Tắt JS vẫn thấy đủ 7 ngày. --%>
                <section class="pt-schedule pt-board__schedule" aria-labelledby="schedule-title" data-reveal>
                    <div class="pt-section-heading">
                        <div><h2 id="schedule-title">Lịch dạy của bạn</h2></div>
                        <span class="pt-info">
                            <button type="button" class="pt-info-trigger" aria-label="Giải thích lịch 7 ngày" aria-describedby="schedule-help"><span aria-hidden="true">i</span></button>
                            <span id="schedule-help" class="pt-info-popover" role="tooltip">Từ hôm nay đến 6 ngày tiếp theo. Chọn một ngày để xem các buổi; buổi chưa bắt đầu có thể xem các phương án đổi lịch.</span>
                        </span>
                    </div>
                    <dl class="pt-pulse">
                        <div><dt>Buổi hôm nay</dt><dd><c:out value="${empty scheduleError and scheduleDays != null ? todayCount : '—'}" /></dd></div>
                        <div><dt>Buổi đầu ngày</dt><dd><c:choose><c:when test="${not empty nextSession}"><c:out value="${gms:formatTime(nextSession.startTime)}" /></c:when><c:otherwise>—</c:otherwise></c:choose></dd></div>
                        <div><dt>Cần chú ý</dt><dd><c:out value="${empty attentionError and attentionItems != null ? fn:length(attentionItems) : '—'}" /></dd></div>
                    </dl>

                    <c:choose>
                        <c:when test="${not empty scheduleError}">
                            <p class="pt-block-error" role="alert"><c:out value="${scheduleError}" /></p>
                        </c:when>
                        <c:when test="${scheduleDays == null}">
                            <p class="empty-state">Chưa tải được lịch.</p>
                        </c:when>
                        <c:otherwise>
                            <div class="pt-days" role="tablist" aria-label="Chọn ngày trong tuần" data-pt-days>
                                <c:forEach var="day" items="${scheduleDays}" varStatus="dayStatus">
                                    <button type="button" role="tab" class="pt-day ${day.today ? 'is-today' : ''}"
                                            id="pt-day-tab-${dayStatus.index}" aria-controls="pt-day-panel-${dayStatus.index}"
                                            aria-selected="${day.today ? 'true' : 'false'}" tabindex="${day.today ? '0' : '-1'}">
                                        <span class="pt-day__name"><c:out value="${day.today ? 'Hôm nay' : day.dayLabel}" /></span>
                                        <span class="pt-day__date"><c:out value="${fn:substring(gms:formatDate(day.slotDate), 0, 5)}" /></span>
                                        <span class="pt-day__count">
                                            <c:choose>
                                                <c:when test="${empty day.sessions}">Trống</c:when>
                                                <c:otherwise><c:out value="${fn:length(day.sessions)}" /> buổi</c:otherwise>
                                            </c:choose>
                                        </span>
                                    </button>
                                </c:forEach>
                            </div>

                            <c:forEach var="day" items="${scheduleDays}" varStatus="dayStatus">
                                <div class="pt-day-panel" role="tabpanel" id="pt-day-panel-${dayStatus.index}" aria-labelledby="pt-day-tab-${dayStatus.index}" tabindex="0">
                                    <h3 class="pt-day-panel__title"><c:out value="${day.dayLabel}" /> · <c:out value="${gms:formatDate(day.slotDate)}" /></h3>
                                    <c:choose>
                                        <c:when test="${empty day.sessions}">
                                            <p class="empty-state">Không có buổi nào trong ngày này.</p>
                                        </c:when>
                                        <c:otherwise>
                                            <div class="pt-session-list">
                                                <c:forEach var="sessionView" items="${day.sessions}">
                                                    <article class="pt-schedule-session" data-start="${day.slotDate}T${sessionView.startTime}" data-end="${day.slotDate}T${sessionView.endTime}">
                                                        <time class="pt-session-time"><b><c:out value="${gms:formatTime(sessionView.startTime)}" /></b><span>– <c:out value="${gms:formatTime(sessionView.endTime)}" /></span></time>
                                                        <div class="pt-session-details">
                                                            <h4><c:out value="${sessionView.customerName}" /><c:if test="${not empty sessionView.partnerName}"> &amp; <c:out value="${sessionView.partnerName}" /></c:if></h4>
                                                            <p><c:out value="${sessionView.subscriptionTypeLabel}" /></p>
                                                        </div>
                                                        <div class="pt-session-controls">
                                                            <%-- Scheduled là trạng thái mặc định của lịch; chỉ hiện badge khi trạng thái khác. --%>
                                                            <%-- nextSession = buổi đầu tiên hôm nay (tính ở đầu trang); chưa bắt đầu → nhãn "Tiếp theo". --%>
                                                            <c:if test="${not empty nextSession and sessionView.sessionId eq nextSession.sessionId and sessionView.canOpenActions}">
                                                                <span class="pt-chip-next">Tiếp theo</span>
                                                            </c:if>
                                                            <c:if test="${sessionView.status ne 'SCHEDULED'}">
                                                                <span class="badge ${sessionView.statusBadgeClass}"><c:out value="${sessionView.statusLabel}" /></span>
                                                            </c:if>
                                                            <c:if test="${sessionView.canOpenActions}">
                                                                <%-- sessionId là Sessions.SessionId; JS dùng để mở đúng hộp buổi. --%>
                                                                <button type="button" class="btn pt-session-action" data-pt-session-action
                                                                        data-session-id="${sessionView.sessionId}"
                                                                        data-session-label="Buổi ${gms:formatTime(sessionView.startTime)} – ${gms:formatTime(sessionView.endTime)} · ${day.dayLabel}">
                                                                    <i class="fa-solid fa-screwdriver-wrench" aria-hidden="true"></i> Xử lý buổi
                                                                </button>
                                                            </c:if>
                                                        </div>
                                                    </article>
                                                </c:forEach>
                                            </div>
                                        </c:otherwise>
                                    </c:choose>
                                </div>
                            </c:forEach>
                        </c:otherwise>
                    </c:choose>
                </section>

                <%-- attentionItems: Service gom khách + gói + loại vấn đề, giữ buổi gần nhất. --%>
                <section class="pt-alerts pt-board__alerts" aria-labelledby="alerts-title" data-reveal>
                    <div class="pt-section-heading">
                        <div><h2 id="alerts-title">Cần chú ý</h2></div>
                        <span class="pt-info">
                            <button type="button" class="pt-info-trigger" aria-label="Giải thích mục Cần chú ý" aria-describedby="alerts-help"><span aria-hidden="true">i</span></button>
                            <span id="alerts-help" class="pt-info-popover" role="tooltip">Kiểm tra membership hoặc bảo lưu cho các buổi trong 14 ngày tới. Bấm <strong>Nhắc khách</strong> để gửi thông báo tới khách (mỗi khách 1 lần/ngày); khách xem ở chuông thông báo.</span>
                        </span>
                    </div>
                    <c:choose>
                        <c:when test="${not empty attentionError}">
                            <p class="pt-block-error" role="alert"><c:out value="${attentionError}" /></p>
                        </c:when>
                        <c:when test="${attentionItems == null}">
                            <p class="empty-state">Chưa tải được cảnh báo.</p>
                        </c:when>
                        <c:when test="${empty attentionItems}">
                            <p class="empty-state">Không có khách cần kiểm tra cho các buổi trong 14 ngày tới.</p>
                        </c:when>
                        <c:otherwise>
                            <div class="pt-attention-list">
                                <c:forEach var="item" items="${attentionItems}">
                                    <%-- 1 thẻ Cần chú ý: đầu thẻ (chữ cái tên + tên + nhãn) → 2 ô thông tin → nút Nhắc khách. --%>
                                    <c:set var="nameParts" value="${fn:split(item.customerName, ' ')}" />
                                    <article class="pt-attn ${not empty item.freezeEndDate ? 'is-frozen' : ''}">
                                        <header class="pt-attn__head">
                                            <span class="pt-attn__avatar" aria-hidden="true"><c:out value="${fn:substring(nameParts[fn:length(nameParts) - 1], 0, 1)}" /></span>
                                            <div class="pt-attn__who">
                                                <%-- Hàng trên: tên + ngày buổi gần nhất (luôn cùng hàng); hàng dưới: nhãn tình trạng. --%>
                                                <div class="pt-attn__top">
                                                    <h3><c:out value="${item.customerName}" /></h3>
                                                    <span class="pt-attn__when">buổi <strong><c:out value="${fn:substring(gms:formatDate(item.sessionDate), 0, 5)}" /></strong></span>
                                                </div>
                                                <p><span class="pt-attn__tag"><c:out value="${item.attentionTitle}" /></span></p>
                                            </div>
                                        </header>
                                        <dl class="pt-attn__facts">
                                            <div>
                                                <dt>Membership</dt>
                                                <dd class="is-bad"><c:out value="${item.membershipStatusLabel}" />
                                                    <c:if test="${not empty item.freezeEndDate}"> · đến <c:out value="${fn:substring(gms:formatDate(item.freezeEndDate), 0, 5)}" /></c:if>
                                                    <c:if test="${empty item.freezeEndDate and not empty item.membershipEndDate}"> · <c:out value="${fn:substring(gms:formatDate(item.membershipEndDate), 0, 5)}" /></c:if></dd>
                                            </div>
                                            <div>
                                                <dt>Gói PT</dt>
                                                <dd>Còn <c:out value="${item.scheduledSessionCount}" /> buổi · đến <c:out value="${fn:substring(gms:formatDate(item.ptEndDate), 0, 5)}" /></dd>
                                            </div>
                                        </dl>
                                        <%-- Gói đôi: lời nhắc của Service nói thêm người còn lại có vào được gym không. --%>
                                        <c:if test="${item.ptSubscriptionTypeLabel eq 'PT đôi'}"><p class="pt-attn__note"><c:out value="${item.attentionMessage}" /></p></c:if>
                                        <%-- Nút Nhắc khách: POST về PtDashboardServlet.doPost; Service kiểm tra lại khách + gói thuộc PT này. --%>
                                        <form class="pt-attn__act" method="post" action="${pageContext.request.contextPath}/pt/dashboard">
                                            <input type="hidden" name="csrfToken" value="${fn:escapeXml(csrfToken)}">
                                            <input type="hidden" name="customerId" value="${item.customerId}">
                                            <input type="hidden" name="subscriptionId" value="${item.ptSubscriptionId}">
                                            <c:choose>
                                                <c:when test="${item.reminded}">
                                                    <button type="button" class="btn btn-outline btn-sm is-done" disabled><i class="fa-solid fa-check" aria-hidden="true"></i> Đã nhắc hôm nay</button>
                                                </c:when>
                                                <c:otherwise>
                                                    <button type="submit" class="btn btn-primary btn-sm" aria-label="Nhắc ${fn:escapeXml(item.customerName)}"><i class="fa-solid fa-bell" aria-hidden="true"></i> Nhắc khách</button>
                                                </c:otherwise>
                                            </c:choose>
                                        </form>
                                    </article>
                                </c:forEach>
                            </div>
                        </c:otherwise>
                    </c:choose>
                </section>


                <%-- commissionSummary: DAO cộng số/tiền đã lưu, Service tính năm tỷ lệ. --%>
                <section class="pt-summary pt-board__summary" aria-labelledby="summary-title" data-reveal>
                    <div class="pt-section-heading">
                        <h2 id="summary-title">Kết quả tháng và hoa hồng</h2>
                        <span class="pt-info">
                            <button type="button" class="pt-info-trigger" aria-label="Giải thích kết quả tháng" aria-describedby="summary-help"><span aria-hidden="true">i</span></button>
                            <span id="summary-help" class="pt-info-popover" role="tooltip">Số liệu theo ngày tập trong tháng này. Tỷ lệ tính trên các buổi đã có kết quả, kể cả buổi hủy.</span>
                        </span>
                    </div>
                    <c:choose>
                        <c:when test="${not empty commissionSummaryError}">
                            <p class="pt-block-error" role="alert"><c:out value="${commissionSummaryError}" /></p>
                        </c:when>
                        <c:when test="${commissionSummary == null}">
                            <p class="empty-state">Chưa tải được kết quả tháng.</p>
                        </c:when>
                        <c:otherwise>
                            <c:set var="cs" value="${commissionSummary}" />
                            <c:if test="${cs.commissionRate != null}">
                                <p class="pt-commission-rate">Tỷ lệ hoa hồng hiện tại: <strong><c:out value="${cs.commissionRate}" />%</strong></p>
                            </c:if>
                            <%-- B1: 3 ô tiền — đã chốt (nổi bật) · chờ hệ thống chốt · dự kiến --%>
                            <div class="pt-money-tiles">
                                <div class="pt-money-tile is-main"><span>Đã chốt</span><strong><c:out value="${gms:formatMoney(cs.finalizedCommissionAmount)}" /></strong><small><c:out value="${cs.resolvedSessionCount}" /> buổi đã có kết quả</small></div>
                                <div class="pt-money-tile"><span>Chờ hệ thống chốt</span><strong><c:out value="${gms:formatMoney(cs.pendingCommissionAmount)}" /></strong><small><c:out value="${cs.pendingSessionCount}" /> buổi vừa kết thúc</small></div>
                                <div class="pt-money-tile"><span>Dự kiến</span><strong><c:out value="${gms:formatMoney(cs.projectedCommissionAmount)}" /></strong><small><c:out value="${cs.projectedSessionCount}" /> buổi còn lại trong tháng</small></div>
                            </div>
                            <%-- B2: 1 thanh xếp chồng thay 5 hộp % — độ dài mỗi đoạn = số buổi (flex), chú thích luôn có chữ + số --%>
                            <c:choose>
                                <c:when test="${cs.resolvedSessionCount == 0}">
                                    <p class="pt-summary-note">Chưa có buổi nào được chốt trong tháng này.</p>
                                </c:when>
                                <c:otherwise>
                                    <p class="pt-outcome-title"><c:out value="${cs.resolvedSessionCount}" /> buổi đã chốt <span>· tỷ lệ theo kết quả</span></p>
                                    <div class="pt-outcome-bar" role="img" aria-label="Có check-in ${cs.checkedInCount}, không check-in ${cs.noCheckInCount}, thiếu quyền vào gym ${cs.noMembershipCount}, đang bảo lưu ${cs.membershipFrozenCount}, đã hủy ${cs.cancelledCount}">
                                        <c:if test="${cs.checkedInCount gt 0}"><i class="is-ok" style="flex: ${cs.checkedInCount}"></i></c:if>
                                        <c:if test="${cs.noCheckInCount gt 0}"><i class="is-absent" style="flex: ${cs.noCheckInCount}"></i></c:if>
                                        <c:if test="${cs.noMembershipCount gt 0}"><i class="is-pass" style="flex: ${cs.noMembershipCount}"></i></c:if>
                                        <c:if test="${cs.membershipFrozenCount gt 0}"><i class="is-frozen" style="flex: ${cs.membershipFrozenCount}"></i></c:if>
                                        <c:if test="${cs.cancelledCount gt 0}"><i class="is-cancel" style="flex: ${cs.cancelledCount}"></i></c:if>
                                    </div>
                                    <ul class="pt-outcome-legend">
                                        <li><i class="is-ok"></i>Có check-in <strong><c:out value="${cs.checkedInCount}" /></strong> <fmt:formatNumber value="${cs.checkedInRatePercent}" maxFractionDigits="0" />%</li>
                                        <li><i class="is-absent"></i>Không check-in <strong><c:out value="${cs.noCheckInCount}" /></strong> <fmt:formatNumber value="${cs.noCheckInRatePercent}" maxFractionDigits="0" />%</li>
                                        <li><i class="is-pass"></i>Thiếu quyền vào gym <strong><c:out value="${cs.noMembershipCount}" /></strong> <fmt:formatNumber value="${cs.noMembershipRatePercent}" maxFractionDigits="0" />%</li>
                                        <li><i class="is-frozen"></i>Đang bảo lưu <strong><c:out value="${cs.membershipFrozenCount}" /></strong> <fmt:formatNumber value="${cs.membershipFrozenRatePercent}" maxFractionDigits="0" />%</li>
                                        <li><i class="is-cancel"></i>Đã hủy <strong><c:out value="${cs.cancelledCount}" /></strong> <fmt:formatNumber value="${cs.cancelledRatePercent}" maxFractionDigits="0" />%</li>
                                    </ul>
                                    <%-- B3: nhận xét chỉ hiện khi khách vắng chiếm từ 1/3 số buổi đã chốt --%>
                                    <c:if test="${cs.noCheckInRatePercent ge 33}">
                                        <p class="pt-outcome-insight"><i class="fa-solid fa-triangle-exclamation" aria-hidden="true"></i>
                                            <span><strong><c:out value="${cs.noCheckInCount}" />/<c:out value="${cs.resolvedSessionCount}" /> buổi</strong> khách không check-in. Hỏi thăm khách hay vắng để giữ buổi (và hoa hồng) của bạn.</span></p>
                                    </c:if>
                                </c:otherwise>
                            </c:choose>
                        </c:otherwise>
                                </c:choose>
                            </div>
                            <div class="pt-summary-group pt-summary-pending">
                                <div class="pt-summary-group-heading">
                                    <div><h3>Chờ hệ thống chốt</h3><p>Buổi đã kết thúc, đang chờ kết quả</p></div>
                                    <p class="pt-summary-count"><strong><c:out value="${commissionSummary.pendingSessionCount}" /></strong> buổi</p>
                                </div>
                                <p class="pt-summary-money-line">Hoa hồng đang chờ <strong><c:out value="${gms:formatMoney(commissionSummary.pendingCommissionAmount)}" /></strong></p>
                            </div>
                            <div class="pt-summary-group pt-summary-projected">
                                <div class="pt-summary-group-heading">
                                    <div><h3>Dự kiến</h3><p>Buổi chưa kết thúc trong tháng</p></div>
                                    <p class="pt-summary-count"><strong><c:out value="${commissionSummary.projectedSessionCount}" /></strong> buổi</p>
                                </div>
                                <p class="pt-summary-money-line">Hoa hồng dự kiến <strong><c:out value="${gms:formatMoney(commissionSummary.projectedCommissionAmount)}" /></strong></p>
                            </div>
                        </c:otherwise>
                    </c:choose>
                </section>

                <%-- assignedPackages: DAO trả từng gói/buổi lẻ, Service thêm nhãn loại. --%>
                <section class="pt-clients pt-board__clients" aria-labelledby="clients-title" data-reveal>
                    <div class="pt-section-heading">
                        <h2 id="clients-title">Khách phụ trách</h2>
                        <span class="pt-info">
                            <button type="button" class="pt-info-trigger" aria-label="Giải thích khách phụ trách" aria-describedby="clients-help"><span aria-hidden="true">i</span></button>
                            <span id="clients-help" class="pt-info-popover" role="tooltip">Gói PT còn hoạt động và buổi lẻ sắp dạy; một khách có nhiều gói có thể xuất hiện nhiều dòng.</span>
                        </span>
                    </div>
                    <c:choose>
                        <c:when test="${not empty assignedPackagesError}">
                            <p class="pt-block-error" role="alert"><c:out value="${assignedPackagesError}" /></p>
                        </c:when>
                        <c:when test="${assignedPackages == null}">
                            <p class="empty-state">Chưa tải được khách phụ trách.</p>
                        </c:when>
                        <c:when test="${empty assignedPackages}">
                            <p class="empty-state">Chưa có gói PT hoặc buổi lẻ sắp dạy.</p>
                        </c:when>
                        <c:otherwise>
                            <div class="pt-client-list">
                                <c:forEach var="assignedPackage" items="${assignedPackages}">
                                    <%-- Thanh = số buổi còn trong lịch / tổng buổi của gói (remainingPercent do DTO tính). --%>
                                    <article class="pt-client-item">
                                        <h3><c:out value="${assignedPackage.customerName}" /><c:if test="${not empty assignedPackage.partnerName}"> &amp; <c:out value="${assignedPackage.partnerName}" /></c:if></h3>
                                        <span class="pt-client-end">đến <c:out value="${gms:formatDate(assignedPackage.ptEndDate)}" /></span>
                                        <span class="pt-client-bar" aria-hidden="true"><i style="width: ${assignedPackage.remainingPercent}%"></i></span>
                                        <p class="pt-client-type"><c:out value="${assignedPackage.ptSubscriptionTypeLabel}" /> · còn <strong><c:out value="${assignedPackage.scheduledSessionCount}" /></strong><c:if test="${assignedPackage.totalSessionCount gt 0}">/<c:out value="${assignedPackage.totalSessionCount}" /></c:if> buổi trong lịch</p>
                                    </article>
                                </c:forEach>
                            </div>
                        </c:otherwise>
                    </c:choose>
                </section>
            </main>
        </div>
    </div>

    <%-- Hộp này chỉ giới thiệu bốn cách xử lý; nghiệp vụ chưa có Service nên nút bị khóa. --%>
    <dialog id="ptSessionDialog" class="pt-session-dialog" aria-labelledby="ptSessionDialogTitle" aria-describedby="ptSessionDialogHint">
        <h2 id="ptSessionDialogTitle">Tùy chọn lịch buổi tập</h2>
        <p id="ptSessionDialogHint">Các phương án dành cho buổi chưa bắt đầu.</p>
        <div class="pt-session-options">
            <button type="button" class="pt-session-option" data-pt-action="reschedule" disabled><i class="fa-solid fa-calendar-days" aria-hidden="true"></i><span><strong>Dời buổi</strong><span>Chuyển sang slot khác của bạn, không tính lượt đổi của khách.</span></span></button>
            <button type="button" class="pt-session-option" data-pt-action="transfer" disabled><i class="fa-solid fa-user-pen" aria-hidden="true"></i><span><strong>Chuyển PT</strong><span>Chọn PT khác có slot phù hợp để tiếp nhận buổi.</span></span></button>
            <button type="button" class="pt-session-option" data-pt-action="cancel" disabled><i class="fa-solid fa-ban" aria-hidden="true"></i><span><strong>Hủy buổi</strong><span>Xem điều kiện hủy trước khi xác nhận.</span></span></button>
            <button type="button" class="pt-session-option" data-pt-action="customer-swap" disabled><i class="fa-solid fa-user-shield" aria-hidden="true"></i><span><strong>Đổi lịch theo yêu cầu khách</strong><span>Dành cho khách phụ thuộc, có tính lượt đổi của gói.</span></span></button>
        </div>
        <p class="pt-session-availability">Các lựa chọn hiện chưa khả dụng.</p>
        <div class="pt-session-dialog-actions"><button type="button" class="btn btn-primary" id="ptSessionDialogClose" autofocus>Đóng</button></div>
    </dialog>
</body>
</html>
