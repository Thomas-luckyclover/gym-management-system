<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="gms" uri="/WEB-INF/gms-functions.tld" %>
<%--
  MÀN PT DASHBOARD — mở bởi PtDashboardServlet.doGet (GET /pt/dashboard).
  Dữ liệu (request.setAttribute, mỗi khối có thể có câu lỗi riêng …Error):
    scheduleDays      ← PtDashboardServlet.loadBlocks → PtDashboardService.getSchedule → PtDashboardDAO.findSessions
    attentionItems    ← … → getAttentionItems → PtDashboardDAO.findClosedDaySessions + findClientPackages
    assignedPackages  ← … → getAssignedPackages → PtDashboardDAO.findAssignedPackages
    commissionSummary ← … → getCommissionSummary → PtDashboardDAO.findCommissionSummary
    weather, weatherType ← PtDashboardServlet.loadWeather → WeatherService
  Màn chỉ xem: không có form POST; nút "Xử lý buổi" / "Dời buổi" là link GET sang /pt/schedule.
--%>

<!DOCTYPE html>
<html lang="vi">
<head>
    <%@ include file="/WEB-INF/jsp/common/head.jspf" %>

    <title><c:out value="${pageTitle}" /> · GMS</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/pt/header.css?v=20261106help">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/pt/dashboard.css?v=20261114a">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/dashboard-weather.css?v=20261014s">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/dashboard-comic.css?v=20261015h">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/dashboard-hero.css?v=20261025city">
    <link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Patrick+Hand&display=swap">
    <script src="${pageContext.request.contextPath}/js/pages/pt/dashboard.js?v=20261025b" defer></script>
</head>

<%-- weather-<weatherType> đổi màu nền theo thời tiết. --%>
<body class="dock theme-pt pt-dashboard-page weather-${weatherType}">
    <%@ include file="/WEB-INF/jsp/common/flash-toast.jspf" %>

    <div class="app">

        <%@ include file="/WEB-INF/jsp/common/layout-pt.jspf" %>
        <div class="main">

            <%-- Thanh tiêu đề PT: pageTitle + ngày hôm nay + chuông + tài khoản. --%>
            <%@ include file="/WEB-INF/jsp/pt/_header.jspf" %>

            <main class="content pt-board" id="main-content" tabindex="-1">

                <%-- Tìm ngày hôm nay trong scheduleDays: todayCount = số buổi hôm nay · firstToday = buổi đầu tiên hôm nay. --%>
                <c:set var="todayCount" value="0" />
                <c:set var="firstToday" value="${null}" />
                <c:forEach var="statDay" items="${scheduleDays}">
                    <%-- statDay.today = getter tự tính isToday() · dto/PtScheduleDayView.java (statDay = PtScheduleDayView) --%>
                    <c:if test="${statDay.today}">
                        <c:set var="todayCount" value="${fn:length(statDay.sessions)}" />
                        <c:if test="${not empty statDay.sessions}"><c:set var="firstToday" value="${statDay.sessions[0]}" /></c:if>
                    </c:if>
                </c:forEach>
                
                
                
                
                

                <%-- ===== KHỐI CHÀO + THỜI TIẾT ===== (common/dashboard-weather.jspf đọc weather) --%>
                <section class="pt-board__hero" aria-label="Lời chào và thời tiết hôm nay">

                    <c:set var="weatherGreetingName" value="${sessionScope.displayName}" />
                    <c:set var="weatherAudience" value="pt" />
                    <%@ include file="/WEB-INF/jsp/common/dashboard-weather.jspf" %>

                    <%--
                      3 SỐ NHANH: Buổi hôm nay ← todayCount · Buổi đầu ngày ← firstToday.startTime · Cần chú ý ← số phần tử attentionItems
                      (khối lỗi → "—").
                    --%>
                    <dl class="pt-pulse">

                        <div><dt>Buổi hôm nay</dt><dd><c:out value="${empty scheduleError and scheduleDays != null ? todayCount : '—'}" /></dd></div>

                        <div><dt>Buổi đầu ngày</dt><dd><c:out value="${empty firstToday ? '—' : gms:formatTime(firstToday.startTime)}" /></dd></div>

                        <div><dt>Cần chú ý</dt><dd><c:out value="${empty attentionError and attentionItems != null ? fn:length(attentionItems) : '—'}" /></dd></div>
                    </dl>
                </section>

                    
                    
                    
                    
                <%-- ===== LỊCH DẠY 7 NGÀY ===== ← scheduleDays (List<PtScheduleDayView>) --%>
                <section class="panel pt-board__schedule" aria-labelledby="schedule-title" data-reveal>
                    <div class="pt-section-heading">
                        <h2 id="schedule-title">Lịch dạy của bạn</h2>
                        <span class="pt-info">
                            <button type="button" class="pt-info-trigger" aria-label="Giải thích lịch 7 ngày" aria-describedby="schedule-help"><span aria-hidden="true">i</span></button>
                            <span id="schedule-help" class="pt-info-popover" role="tooltip">Từ hôm nay đến 6 ngày tiếp theo. Chọn một ngày để xem các buổi; buổi chưa bắt đầu có thể xem các phương án đổi lịch.</span>
                        </span>
                    </div>

                    <c:choose>       
                        <%-- Lỗi khối (scheduleError) → câu đỏ; null → "Chưa tải được lịch". --%>
                        <c:when test="${not empty scheduleError}">
                            <p class="pt-block-error" role="alert"><c:out value="${scheduleError}" /></p>
                        </c:when>
                        <c:when test="${scheduleDays == null}">
                            <p class="empty-state">Chưa tải được lịch.</p>
                        </c:when>
                        <c:otherwise>

                            
                            
                            
                            
                            <%-- 7 TAB NGÀY (dashboard.js đổi tab): day.dayLabel · dd/MM ← day.slotDate · "N buổi"/"Trống" ← day.sessions; tab hôm nay mở sẵn (day.today). --%>
                            <div class="pt-days" role="tablist" aria-label="Chọn ngày" data-pt-days>
                                <%-- MỖI TAB 1 BẢNG BUỔI (biến s = PtSessionView): --%>
                                <c:forEach var="day" items="${scheduleDays}" varStatus="st">
                                    <%-- day.today = getter tự tính isToday() · dto/PtScheduleDayView.java (day = PtScheduleDayView) --%>
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

                                            <c:forEach var="s" items="${day.sessions}">

                                                <%--
                                                  1 buổi: giờ ← s.startTime–s.endTime · tên ← s.customerName (& s.partnerName) · loại gói ← s.subscriptionTypeLabel
                                                  data-start / data-end → dashboard.js tô buổi đang diễn ra.
                                                --%>
                                                <article class="pt-schedule-session" data-start="${day.slotDate}T${s.startTime}" data-end="${day.slotDate}T${s.endTime}">

                                                    <time class="pt-session-time"><b><c:out value="${gms:formatTime(s.startTime)}" /></b><span>– <c:out value="${gms:formatTime(s.endTime)}" /></span></time>

                                                    <div class="pt-session-details">

                                                        <h3><c:out value="${s.customerName}" /><c:if test="${not empty s.partnerName}"> &amp; <c:out value="${s.partnerName}" /></c:if></h3>

                                                        <p><c:out value="${s.subscriptionTypeLabel}" /></p>
                                                    </div>

                                                    <div class="pt-session-controls">

                                                        <%-- Chip "Tiếp theo": buổi đầu tiên hôm nay và chưa bắt đầu. --%>
                                                        <c:if test="${not empty firstToday and s.sessionId eq firstToday.sessionId and s.canOpenActions}">
                                                            <span class="pt-chip-next">Tiếp theo</span>
                                                        </c:if>

                                                        <%-- Huy hiệu trạng thái (chỉ buổi đã có kết quả): s.statusLabel + màu s.statusBadgeClass. --%>
                                                        <c:if test="${s.status ne 'SCHEDULED'}">
                                                            <span class="badge ${s.statusBadgeClass}"><c:out value="${s.statusLabel}" /></span>
                                                        </c:if>

                                                        <%-- Lý do vắng cạnh huy hiệu "Vắng": s.missedReasonLabel ← prepareSessionDisplay B4. --%>
                                                        <c:if test="${s.status eq 'MISSED' and not empty s.missedReasonLabel}">
                                                            <small class="pt-session-reason"><c:out value="${s.missedReasonLabel}" /></small>
                                                        </c:if>

                                                        <%-- Nút "Xử lý buổi" (s.canOpenActions = Scheduled, chưa tới giờ) → GET /pt/schedule?date=…&handle=<sessionId> (mở hộp Xử lý buổi). --%>
                                                        <c:if test="${s.canOpenActions}">
                                                            <c:url var="handleUrl" value="/pt/schedule"><c:param name="date" value="${s.slotDate}" /><c:param name="handle" value="${s.sessionId}" /></c:url>
                                                            <a class="btn pt-session-action" href="${handleUrl}">
                                                                <i class="fa-solid fa-screwdriver-wrench" aria-hidden="true"></i> Xử lý buổi
                                                            </a>
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

                
                
                
                
                
                
                <%-- ===== KHÁCH PHỤ TRÁCH ===== ← assignedPackages (List<PtAssignedPackageView>), đã xếp khách có việc lên đầu. --%>
                <section class="panel pt-board__clients" aria-labelledby="clients-title" data-reveal>
                    <div class="pt-section-heading"><h2 id="clients-title">Khách phụ trách</h2></div>

                    <c:if test="${not empty attentionError}"><p class="pt-block-error" role="alert"><c:out value="${attentionError}" /></p></c:if>

                    <c:choose>
                        <c:when test="${not empty assignedPackagesError}"><p class="pt-block-error" role="alert"><c:out value="${assignedPackagesError}" /></p></c:when>
                        <c:when test="${assignedPackages == null}"><p class="empty-state">Chưa tải được khách phụ trách.</p></c:when>
                        <c:when test="${empty assignedPackages}"><p class="empty-state">Chưa có gói PT hoặc buổi lẻ sắp dạy.</p></c:when>
                        <c:otherwise>

                            <%--
                              1 khách (biến client): tên (& người tập cùng) · client.ptSubscriptionTypeLabel · "còn N buổi" ← client.scheduledSessionCount
                              · "đến" ← client.ptEndDate.
                            --%>
                            <ul class="pt-client-list">
                                <c:forEach var="client" items="${assignedPackages}">
                                    <li class="pt-client-item">

                                        <h3><c:out value="${client.customerName}" /><c:if test="${not empty client.partnerName}"> &amp; <c:out value="${client.partnerName}" /></c:if></h3>

                                        <p class="pt-client-type"><c:out value="${client.ptSubscriptionTypeLabel}" /> · còn <strong><c:out value="${client.scheduledSessionCount}" /></strong> buổi · đến <c:out value="${gms:formatDate(client.ptEndDate)}" /></p>

                                        <%-- LIÊN HỆ: SĐT + Zalo từng người (client.customerPhone / customerZaloUrl, partnerPhone / partnerZaloUrl). --%>
                                        <c:if test="${not empty client.customerPhone or not empty client.partnerPhone}">
                                            <ul class="pt-contacts">
                                                <c:if test="${not empty client.customerPhone}">
                                                    <li>
                                                        <c:if test="${not empty client.partnerName}"><span class="pt-contacts__who"><c:out value="${client.customerName}" /></span></c:if>

                                                        <a class="pt-contact-btn" href="tel:${fn:escapeXml(client.customerPhone)}"><i class="fa-solid fa-phone" aria-hidden="true"></i><c:out value="${client.customerPhone}" /></a>

                                                        <%-- client.customerZaloUrl = getter tự tính getCustomerZaloUrl() · dto/PtAssignedPackageView.java (client = PtAssignedPackageView) --%>
                                                        <c:if test="${not empty client.customerZaloUrl}">
                                                            <a class="pt-contact-btn is-zalo" href="${fn:escapeXml(client.customerZaloUrl)}" target="_blank" rel="noopener noreferrer" aria-label="Nhắn Zalo cho ${fn:escapeXml(client.customerName)} (mở tab mới)">Zalo<i class="fa-solid fa-arrow-up-right-from-square" aria-hidden="true"></i></a>
                                                        </c:if>
                                                    </li>
                                                </c:if>

                                                <c:if test="${not empty client.partnerPhone}">
                                                    <li>
                                                        <span class="pt-contacts__who"><c:out value="${client.partnerName}" /></span>
                                                        <a class="pt-contact-btn" href="tel:${fn:escapeXml(client.partnerPhone)}"><i class="fa-solid fa-phone" aria-hidden="true"></i><c:out value="${client.partnerPhone}" /></a>
                                                        <%-- client.partnerZaloUrl = getter tự tính getPartnerZaloUrl() · dto/PtAssignedPackageView.java (client = PtAssignedPackageView) --%>
                                                        <c:if test="${not empty client.partnerZaloUrl}">
                                                            <a class="pt-contact-btn is-zalo" href="${fn:escapeXml(client.partnerZaloUrl)}" target="_blank" rel="noopener noreferrer" aria-label="Nhắn Zalo cho ${fn:escapeXml(client.partnerName)} (mở tab mới)">Zalo<i class="fa-solid fa-arrow-up-right-from-square" aria-hidden="true"></i></a>
                                                        </c:if>
                                                    </li>
                                                </c:if>
                                            </ul>
                                        </c:if>

                                        <%--
                                          VIỆC CẦN CHÚ Ý của khách (biến item = PtAttentionView): màu theo item.kind (frozen | closedDay | membership)
                                            tiêu đề ← item.attentionTitle · "buổi dd/MM" ← item.sessionDate · ghi chú ← item.attentionMessage
                                        --%>
                                        <c:forEach var="item" items="${client.attentionItems}">
                                            <div class="pt-attn ${item.kind eq 'frozen' ? 'is-frozen' : ''} ${item.kind eq 'closedDay' ? 'is-closed' : ''}">

                                                <p><strong><c:out value="${item.attentionTitle}" /></strong> · buổi <c:out value="${fn:substring(gms:formatDate(item.sessionDate), 0, 5)}" /><c:if test="${not empty client.partnerName}"> · <c:out value="${item.customerName}" /></c:if></p>

                                                <c:if test="${item.kind eq 'closedDay' or not empty client.partnerName}"><p class="pt-attn__note"><c:out value="${item.attentionMessage}" /></p></c:if>
                                                <c:choose>

                                                    <%-- Buổi rơi lúc phòng đóng (nghỉ cả ngày / ngoài giờ) → nút "Dời buổi" → /pt/schedule?date=…&handle=<item.sessionId>. --%>
                                                    <c:when test="${item.kind eq 'closedDay'}">
                                                        <c:url var="moveUrl" value="/pt/schedule"><c:param name="date" value="${item.sessionDate}" /><c:param name="handle" value="${item.sessionId}" /></c:url>
                                                        <a class="btn btn-primary btn-sm" href="${moveUrl}">Dời buổi</a>
                                                    </c:when>

                                                    <%-- Thiếu gói gym / bảo lưu → chỉ báo: JOB-08 tự nhắc khách trước buổi 1 ngày. --%>
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

                    
                    
                    
                    
                    
                    
                <%-- ===== KẾT QUẢ THÁNG VÀ HOA HỒNG ===== ← commissionSummary (biến cs = PtCommissionSummaryView) --%>
                <%-- Chữ tháng đang xem: "tháng này" hoặc "tháng 10/2026" (selectedMonthText). --%>
                <c:set var="viewedMonthText" value="${isCurrentMonth ? 'tháng này' : 'tháng '.concat(selectedMonthText)}" />
                <section class="panel pt-board__summary" aria-labelledby="summary-title" data-reveal>
                    <div class="pt-section-heading">
                        <h2 id="summary-title">Kết quả tháng và hoa hồng</h2>
                        <span class="pt-info">
                            <button type="button" class="pt-info-trigger" aria-label="Giải thích kết quả tháng" aria-describedby="summary-help"><span aria-hidden="true">i</span></button>
                            <span id="summary-help" class="pt-info-popover" role="tooltip">Số liệu theo ngày tập trong <c:out value="${viewedMonthText}" />. Tỷ lệ tính trên các buổi đã có kết quả, kể cả buổi hủy.</span>
                        </span>
                        <%-- Ô chọn tháng: select ← monthOptions / selectedMonth (Servlet loadMonthFilter), gửi GET ?month=. data-auto-submit: common.js tự gửi; tắt JS có nút Xem. --%>
                        <form method="get" action="${pageContext.request.contextPath}/pt/dashboard" class="pt-month-filter">
                            <c:if test="${weatherPreview}"><input type="hidden" name="weather" value="${fn:escapeXml(param.weather)}"></c:if>
                            <label for="commissionMonth" class="visually-hidden">Chọn tháng</label>
                            <select id="commissionMonth" name="month" data-auto-submit>
                                <c:forEach var="o" items="${monthOptions}">
                                    <option value="${o.value()}" ${o.value() eq selectedMonth ? 'selected' : ''}><c:out value="${o.label()}" /></option>
                                </c:forEach>
                            </select>
                            <noscript><button type="submit" class="btn btn-sm">Xem</button></noscript>
                        </form>
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

                            <%-- Tỷ lệ hoa hồng ← cs.commissionRate (Employees.CommissionRate). --%>
                            <c:if test="${cs.commissionRate != null}">
                                <p class="pt-commission-rate">Tỷ lệ hoa hồng hiện tại: <strong><c:out value="${cs.commissionRate}" />%</strong></p>
                            </c:if>

                            <%--
                              3 Ô TIỀN: Đã chốt ← cs.finalizedCommissionAmount · Chờ hệ thống chốt ← cs.pendingCommissionAmount (cs.pendingSessionCount buổi)
                              · Dự kiến ← cs.projectedCommissionAmount (cs.projectedSessionCount buổi).
                            --%>
                            <div class="pt-money-tiles">
                                <div class="pt-money-tile is-main"><span>Đã chốt</span><strong><c:out value="${gms:formatMoney(cs.finalizedCommissionAmount)}" /></strong><small><c:out value="${cs.resolvedSessionCount}" /> buổi đã có kết quả</small></div>
                                <div class="pt-money-tile"><span>Chờ hệ thống chốt</span><strong><c:out value="${gms:formatMoney(cs.pendingCommissionAmount)}" /></strong><small><c:out value="${cs.pendingSessionCount}" /> buổi vừa kết thúc</small></div>
                                <div class="pt-money-tile"><span>Dự kiến</span><strong><c:out value="${gms:formatMoney(cs.projectedCommissionAmount)}" /></strong><small><c:choose><c:when test="${isCurrentMonth}"><c:out value="${cs.projectedSessionCount}" /> buổi còn lại trong tháng</c:when><c:otherwise>không còn buổi</c:otherwise></c:choose></small></div>
                            </div>

                            <c:choose>
                                <%-- Chưa có buổi nào có kết quả → 1 câu; có → thanh màu tỷ lệ + chú thích. --%>
                                <c:when test="${cs.resolvedSessionCount == 0}">
                                    <p class="pt-summary-note">Chưa có buổi nào được chốt trong <c:out value="${viewedMonthText}" />.</p>
                                </c:when>
                                <c:otherwise>

                                    <p class="pt-outcome-title"><c:out value="${cs.resolvedSessionCount}" /> buổi đã chốt <span>· tỷ lệ theo kết quả</span></p>
                                    <%-- THANH TỶ LỆ: mỗi đoạn dài theo số buổi (flex) ← cs.checkedInCount / noCheckInCount / noMembershipCount / membershipFrozenCount / cancelledCount. --%>
                                    <div class="pt-outcome-bar" role="img" aria-label="Có check-in ${cs.checkedInCount}, không check-in ${cs.noCheckInCount}, thiếu quyền vào gym ${cs.noMembershipCount}, đang bảo lưu ${cs.membershipFrozenCount}, đã hủy ${cs.cancelledCount}">
                                        <c:if test="${cs.checkedInCount gt 0}"><i class="is-ok" style="flex: ${cs.checkedInCount}"></i></c:if>
                                        <c:if test="${cs.noCheckInCount gt 0}"><i class="is-absent" style="flex: ${cs.noCheckInCount}"></i></c:if>
                                        <c:if test="${cs.noMembershipCount gt 0}"><i class="is-pass" style="flex: ${cs.noMembershipCount}"></i></c:if>
                                        <c:if test="${cs.membershipFrozenCount gt 0}"><i class="is-frozen" style="flex: ${cs.membershipFrozenCount}"></i></c:if>
                                        <c:if test="${cs.cancelledCount gt 0}"><i class="is-cancel" style="flex: ${cs.cancelledCount}"></i></c:if>
                                    </div>

                                    <%-- CHÚ THÍCH: số buổi + % (cs.…RatePercent ← PtDashboardService.prepareCommissionSummary). --%>
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

</body>
</html>
