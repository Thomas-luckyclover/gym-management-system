<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="gms" uri="/WEB-INF/gms-functions.tld" %>
<%--
================================================================================
 dashboard.jsp — TRANG TỔNG QUAN PT (/pt/dashboard). Chỉ xem, không có form.

 DỮ LIỆU SERVLET GỬI SANG (mỗi khối: dữ liệu HOẶC lỗi, không bao giờ cả hai)
   scheduleDays      / scheduleError            7 PtScheduleDayView → day.sessions (PtSessionView)
   attentionItems    / attentionError           cảnh báo 14 ngày (đã gắn sẵn vào từng thẻ khách)
   assignedPackages  / assignedPackagesError    PtAssignedPackageView: tên, SĐT, link Zalo, cảnh báo
   commissionSummary / commissionSummaryError   tiền + số buổi tháng này
   weather, weatherType, weatherPreview         cho khối chào (common/dashboard-weather.jspf)

 BỐ CỤC (thứ tự trong HTML = thứ tự đọc trên điện thoại)
   1. Khối chào: lời chào + 3 con số nhanh + tranh phòng tập + thời tiết   (css/dashboard-hero.css)
   2. Lịch 7 ngày
   3. Khách phụ trách (gọi, Zalo, cảnh báo của từng khách)
   4. Kết quả tháng và hoa hồng
   5. Hộp "Tùy chọn lịch buổi tập" (dashboard.js mở khi bấm "Xử lý buổi" / "Dời buổi")
   Màn rộng: CSS xếp 2 cột — trái: Lịch + Kết quả tháng, phải: Khách phụ trách (dashboard.css mục 1).

 NHẮC KHÁCH: không có nút. JOB-08 tự gửi thông báo cho khách thiếu membership trước buổi 1 ngày.
================================================================================ --%>
<!DOCTYPE html>
<html lang="vi">
<head>
    <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
    <title><c:out value="${pageTitle}" /> · GMS</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/pt/header.css?v=20261024a">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/pt/dashboard.css?v=20261025a">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/dashboard-weather.css?v=20261014s">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/dashboard-comic.css?v=20261015h">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/dashboard-hero.css?v=20261025a">
    <link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Patrick+Hand&display=swap">
    <script src="${pageContext.request.contextPath}/js/pages/pt/dashboard.js?v=20261025a" defer></script>
</head>
<%-- weatherType đã được Servlet lọc (resolveWeatherType): chỉ là 1 trong 8 tên cố định. --%>
<body class="dock theme-pt pt-dashboard-page weather-${weatherType}">
    <%@ include file="/WEB-INF/jsp/common/flash-toast.jspf" %>

    <div class="app">
        <%@ include file="/WEB-INF/jsp/common/layout-pt.jspf" %>
        <div class="main">
            <%@ include file="/WEB-INF/jsp/pt/_header.jspf" %>

            <main class="content pt-board" id="main-content" tabindex="-1">

                <%-- ===== 1. KHỐI CHÀO =====
                     Tìm số buổi hôm nay + buổi đầu tiên hôm nay để hiện 3 con số nhanh. --%>
                <c:set var="todayCount" value="0" />
                <c:set var="firstToday" value="${null}" />
                <c:forEach var="statDay" items="${scheduleDays}">
                    <c:if test="${statDay.today}">
                        <c:set var="todayCount" value="${fn:length(statDay.sessions)}" />
                        <c:if test="${not empty statDay.sessions}"><c:set var="firstToday" value="${statDay.sessions[0]}" /></c:if>
                    </c:if>
                </c:forEach>

                <section class="pt-board__hero" aria-label="Lời chào và thời tiết hôm nay">
                    <c:set var="weatherGreetingName" value="${sessionScope.displayName}" />
                    <c:set var="weatherAudience" value="pt" />
                    <%@ include file="/WEB-INF/jsp/common/dashboard-weather.jspf" %>

                    <%-- Lỗi tải dữ liệu → hiện "—", không hiện số 0 giả. --%>
                    <dl class="pt-pulse">
                        <div><dt>Buổi hôm nay</dt><dd><c:out value="${empty scheduleError and scheduleDays != null ? todayCount : '—'}" /></dd></div>
                        <div><dt>Buổi đầu ngày</dt><dd><c:out value="${empty firstToday ? '—' : gms:formatTime(firstToday.startTime)}" /></dd></div>
                        <div><dt>Cần chú ý</dt><dd><c:out value="${empty attentionError and attentionItems != null ? fn:length(attentionItems) : '—'}" /></dd></div>
                    </dl>
                </section>


                <%-- ===== 2. LỊCH 7 NGÀY =====
                     Dải ngày là tab (role="tab"): JSP vẽ đủ 7 bảng, dashboard.js chỉ ẩn/hiện.
                     Tắt JS vẫn đọc được cả 7 ngày. Hôm nay = viền cam; ngày đang chọn = nền xanh. --%>
                <section class="panel pt-board__schedule" aria-labelledby="schedule-title" data-reveal>
                    <div class="pt-section-heading">
                        <h2 id="schedule-title">Lịch dạy của bạn</h2>
                        <span class="pt-info">
                            <button type="button" class="pt-info-trigger" aria-label="Giải thích lịch 7 ngày" aria-describedby="schedule-help"><span aria-hidden="true">i</span></button>
                            <span id="schedule-help" class="pt-info-popover" role="tooltip">Từ hôm nay đến 6 ngày tiếp theo. Chọn một ngày để xem các buổi; buổi chưa bắt đầu có thể xem các phương án đổi lịch.</span>
                        </span>
                    </div>

                    <c:choose>
                        <c:when test="${not empty scheduleError}">
                            <p class="pt-block-error" role="alert"><c:out value="${scheduleError}" /></p>
                        </c:when>
                        <c:when test="${scheduleDays == null}">
                            <p class="empty-state">Chưa tải được lịch.</p>
                        </c:when>
                        <c:otherwise>
                            <div class="pt-days" role="tablist" aria-label="Chọn ngày" data-pt-days>
                                <c:forEach var="day" items="${scheduleDays}" varStatus="st">
                                    <button type="button" role="tab" class="pt-day ${day.today ? 'is-today' : ''}"
                                            id="pt-day-tab-${st.index}" aria-controls="pt-day-panel-${st.index}"
                                            aria-selected="${day.today}" tabindex="${day.today ? 0 : -1}">
                                        <span class="pt-day__name"><c:out value="${day.dayLabel}" /></span>
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

                            <c:forEach var="day" items="${scheduleDays}" varStatus="st">
                                <div class="pt-day-panel" role="tabpanel" id="pt-day-panel-${st.index}" aria-labelledby="pt-day-tab-${st.index}" tabindex="0">
                                    <c:choose>
                                        <c:when test="${empty day.sessions}">
                                            <p class="empty-state">Không có buổi nào trong ngày này.</p>
                                        </c:when>
                                        <c:otherwise>
                                            <%-- data-start / data-end: common.js đọc để gắn "Đang diễn ra" / "Còn 20 phút". --%>
                                            <c:forEach var="s" items="${day.sessions}">
                                                <article class="pt-schedule-session" data-start="${day.slotDate}T${s.startTime}" data-end="${day.slotDate}T${s.endTime}">
                                                    <time class="pt-session-time"><b><c:out value="${gms:formatTime(s.startTime)}" /></b><span>– <c:out value="${gms:formatTime(s.endTime)}" /></span></time>

                                                    <div class="pt-session-details">
                                                        <h3><c:out value="${s.customerName}" /><c:if test="${not empty s.partnerName}"> &amp; <c:out value="${s.partnerName}" /></c:if></h3>
                                                        <p><c:out value="${s.subscriptionTypeLabel}" /></p>
                                                    </div>

                                                    <div class="pt-session-controls">
                                                        <%-- Buổi đầu tiên hôm nay, chưa bắt đầu → nhãn "Tiếp theo". --%>
                                                        <c:if test="${not empty firstToday and s.sessionId eq firstToday.sessionId and s.canOpenActions}">
                                                            <span class="pt-chip-next">Tiếp theo</span>
                                                        </c:if>
                                                        <%-- "Đã lên lịch" là mặc định → không cần badge; chỉ hiện Check-in / Hủy. --%>
                                                        <c:if test="${s.status ne 'SCHEDULED'}">
                                                            <span class="badge ${s.statusBadgeClass}"><c:out value="${s.statusLabel}" /></span>
                                                        </c:if>
                                                        <c:if test="${s.canOpenActions}">
                                                            <button type="button" class="btn pt-session-action" data-pt-session-action
                                                                    data-session-id="${s.sessionId}"
                                                                    data-session-label="Buổi ${gms:formatTime(s.startTime)} – ${gms:formatTime(s.endTime)} · ${day.dayLabel}">
                                                                <i class="fa-solid fa-screwdriver-wrench" aria-hidden="true"></i> Xử lý buổi
                                                            </button>
                                                        </c:if>
                                                    </div>
                                                </article>
                                            </c:forEach>
                                        </c:otherwise>
                                    </c:choose>
                                </div>
                            </c:forEach>
                        </c:otherwise>
                    </c:choose>
                </section>


                <%-- ===== 3. KHÁCH PHỤ TRÁCH =====
                     Mỗi thẻ = 1 gói PT / buổi lẻ. Service đã sắp: thẻ có buổi ngày nghỉ → thẻ có cảnh báo → bình thường.
                     Liên hệ: số gọi được (tel:) + nút Zalo (client.customerZaloUrl do DTO tự tính từ SĐT). --%>
                <section class="panel pt-board__clients" aria-labelledby="clients-title" data-reveal>
                    <div class="pt-section-heading"><h2 id="clients-title">Khách phụ trách</h2></div>

                    <c:if test="${not empty attentionError}"><p class="pt-block-error" role="alert"><c:out value="${attentionError}" /></p></c:if>

                    <c:choose>
                        <c:when test="${not empty assignedPackagesError}"><p class="pt-block-error" role="alert"><c:out value="${assignedPackagesError}" /></p></c:when>
                        <c:when test="${assignedPackages == null}"><p class="empty-state">Chưa tải được khách phụ trách.</p></c:when>
                        <c:when test="${empty assignedPackages}"><p class="empty-state">Chưa có gói PT hoặc buổi lẻ sắp dạy.</p></c:when>
                        <c:otherwise>
                            <ul class="pt-client-list">
                                <c:forEach var="client" items="${assignedPackages}">
                                    <li class="pt-client-item">
                                        <h3><c:out value="${client.customerName}" /><c:if test="${not empty client.partnerName}"> &amp; <c:out value="${client.partnerName}" /></c:if></h3>
                                        <p class="pt-client-type"><c:out value="${client.ptSubscriptionTypeLabel}" /> · còn <strong><c:out value="${client.scheduledSessionCount}" /></strong> buổi · đến <c:out value="${gms:formatDate(client.ptEndDate)}" /></p>

                                        <%-- Liên hệ: gói đôi thì mỗi người 1 dòng, có tên để PT biết đang gọi ai. --%>
                                        <c:if test="${not empty client.customerPhone or not empty client.partnerPhone}">
                                            <ul class="pt-contacts">
                                                <c:if test="${not empty client.customerPhone}">
                                                    <li>
                                                        <c:if test="${not empty client.partnerName}"><span class="pt-contacts__who"><c:out value="${client.customerName}" /></span></c:if>
                                                        <a class="pt-contact-btn" href="tel:${fn:escapeXml(client.customerPhone)}"><i class="fa-solid fa-phone" aria-hidden="true"></i><c:out value="${client.customerPhone}" /></a>
                                                        <c:if test="${not empty client.customerZaloUrl}">
                                                            <a class="pt-contact-btn is-zalo" href="${fn:escapeXml(client.customerZaloUrl)}" target="_blank" rel="noopener noreferrer" aria-label="Nhắn Zalo cho ${fn:escapeXml(client.customerName)} (mở tab mới)">Zalo<i class="fa-solid fa-arrow-up-right-from-square" aria-hidden="true"></i></a>
                                                        </c:if>
                                                    </li>
                                                </c:if>
                                                <c:if test="${not empty client.partnerPhone}">
                                                    <li>
                                                        <span class="pt-contacts__who"><c:out value="${client.partnerName}" /></span>
                                                        <a class="pt-contact-btn" href="tel:${fn:escapeXml(client.partnerPhone)}"><i class="fa-solid fa-phone" aria-hidden="true"></i><c:out value="${client.partnerPhone}" /></a>
                                                        <c:if test="${not empty client.partnerZaloUrl}">
                                                            <a class="pt-contact-btn is-zalo" href="${fn:escapeXml(client.partnerZaloUrl)}" target="_blank" rel="noopener noreferrer" aria-label="Nhắn Zalo cho ${fn:escapeXml(client.partnerName)} (mở tab mới)">Zalo<i class="fa-solid fa-arrow-up-right-from-square" aria-hidden="true"></i></a>
                                                        </c:if>
                                                    </li>
                                                </c:if>
                                            </ul>
                                        </c:if>

                                        <%-- Cảnh báo của đúng gói này (Service ghép theo mã gói, không theo tên). --%>
                                        <c:forEach var="item" items="${client.attentionItems}">
                                            <div class="pt-attn ${item.kind eq 'frozen' ? 'is-frozen' : ''} ${item.kind eq 'closedDay' ? 'is-closed' : ''}">
                                                <p><strong><c:out value="${item.attentionTitle}" /></strong> · buổi <c:out value="${fn:substring(gms:formatDate(item.sessionDate), 0, 5)}" /><c:if test="${not empty client.partnerName}"> · <c:out value="${item.customerName}" /></c:if></p>
                                                <c:if test="${item.kind eq 'closedDay' or not empty client.partnerName}"><p class="pt-attn__note"><c:out value="${item.attentionMessage}" /></p></c:if>
                                                <c:choose>
                                                    <c:when test="${item.kind eq 'closedDay'}">
                                                        <button type="button" class="btn btn-primary btn-sm" data-pt-session-action data-session-id="${item.sessionId}" data-session-label="Dời buổi ${gms:formatDate(item.sessionDate)} · ${fn:escapeXml(item.customerName)}">Dời buổi</button>
                                                    </c:when>
                                                    <c:otherwise><p class="pt-attn__auto"><i class="fa-solid fa-bell" aria-hidden="true"></i> Hệ thống tự nhắc khách trước buổi 1 ngày.</p></c:otherwise>
                                                </c:choose>
                                            </div>
                                        </c:forEach>
                                    </li>
                                </c:forEach>
                            </ul>
                        </c:otherwise>
                    </c:choose>
                </section>


                <%-- ===== 4. KẾT QUẢ THÁNG VÀ HOA HỒNG =====
                     DAO đếm buổi + cộng tiền; Service tính % (PtDashboardService.prepareCommissionSummary). --%>
                <section class="panel pt-board__summary" aria-labelledby="summary-title" data-reveal>
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

                            <%-- 3 ô tiền: Đã chốt (nổi bật) · Chờ hệ thống chốt · Dự kiến --%>
                            <div class="pt-money-tiles">
                                <div class="pt-money-tile is-main"><span>Đã chốt</span><strong><c:out value="${gms:formatMoney(cs.finalizedCommissionAmount)}" /></strong><small><c:out value="${cs.resolvedSessionCount}" /> buổi đã có kết quả</small></div>
                                <div class="pt-money-tile"><span>Chờ hệ thống chốt</span><strong><c:out value="${gms:formatMoney(cs.pendingCommissionAmount)}" /></strong><small><c:out value="${cs.pendingSessionCount}" /> buổi vừa kết thúc</small></div>
                                <div class="pt-money-tile"><span>Dự kiến</span><strong><c:out value="${gms:formatMoney(cs.projectedCommissionAmount)}" /></strong><small><c:out value="${cs.projectedSessionCount}" /> buổi còn lại trong tháng</small></div>
                            </div>

                            <%-- Thanh xếp chồng: mỗi đoạn dài theo số buổi (flex: số buổi). Chú thích luôn có chữ + số + %. --%>
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
                                </c:otherwise>
                            </c:choose>
                        </c:otherwise>
                    </c:choose>
                </section>
            </main>
        </div>
    </div>

    <%-- ===== 5. HỘP TÙY CHỌN BUỔI =====
         Chỉ giới thiệu 4 cách xử lý; nghiệp vụ chưa có Service nên các nút bị khóa (disabled). --%>
    <dialog id="ptSessionDialog" class="pt-session-dialog" aria-labelledby="ptSessionDialogTitle" aria-describedby="ptSessionDialogHint">
        <h2 id="ptSessionDialogTitle">Tùy chọn lịch buổi tập</h2>
        <p id="ptSessionDialogHint">Các phương án dành cho buổi chưa bắt đầu.</p>
        <div class="pt-session-options">
            <button type="button" class="pt-session-option" disabled><i class="fa-solid fa-calendar-days" aria-hidden="true"></i><span><strong>Dời buổi</strong><span>Chuyển sang slot khác của bạn, không tính lượt đổi của khách.</span></span></button>
            <button type="button" class="pt-session-option" disabled><i class="fa-solid fa-user-pen" aria-hidden="true"></i><span><strong>Chuyển PT</strong><span>Chọn PT khác có slot phù hợp để tiếp nhận buổi.</span></span></button>
            <button type="button" class="pt-session-option" disabled><i class="fa-solid fa-ban" aria-hidden="true"></i><span><strong>Hủy buổi</strong><span>Xem điều kiện hủy trước khi xác nhận.</span></span></button>
            <button type="button" class="pt-session-option" disabled><i class="fa-solid fa-user-shield" aria-hidden="true"></i><span><strong>Đổi lịch theo yêu cầu khách</strong><span>Dành cho khách phụ thuộc, có tính lượt đổi của gói.</span></span></button>
        </div>
        <p class="pt-session-availability">Các lựa chọn hiện chưa khả dụng.</p>
        <div class="pt-session-dialog-actions"><button type="button" class="btn btn-primary" id="ptSessionDialogClose" autofocus>Đóng</button></div>
    </dialog>
</body>
</html>
