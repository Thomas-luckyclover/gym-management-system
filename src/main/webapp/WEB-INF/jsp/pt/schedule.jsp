<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="gms" uri="/WEB-INF/gms-functions.tld" %>
<%--
================================================================================
 schedule.jsp — MÀN LỊCH DẠY PT (/pt/schedule)

 DỮ LIỆU SERVLET GỬI SANG
   week           PtScheduleWeek   tuần → week.days (7 PtScheduleDay) → day.items (các PtScheduleItem)
   scheduleError  String           có khi Service lỗi; lúc đó KHÔNG có week
   view           "week" | "list"  kiểu xem; viewDate = ngày đang xem; today = hôm nay
   csrfToken, draftAccountId       cho form Công bố và khóa lưu nháp

 BỐ CỤC
   1. Panel lịch: thanh công cụ (‹ tuần ›, Thêm khung, Tuần/Danh sách) → lưới 7 ngày → chú thích màu
   2. Thanh "Chờ công bố" dính đáy: CSS tự hiện khi có nháp hoặc khung chờ gỡ
   3. <template> khung nháp: JS sao chép khi PT thêm khung

 AI LÀM GÌ
   Gỡ khung   = ô checkbox trong thẻ khung trống. Không cần JS: CSS đổi kiểu thẻ khi được tích.
   Thêm khung = JS (schedule.js) sao chép <template>, thẻ nháp có 2 ô ẩn slotDate + startTime.
   Công bố    = JS gửi mọi ô thuộc form #pt-publish-form (kể cả ô nằm ngoài form nhờ form="pt-publish-form").
================================================================================ --%>
<!DOCTYPE html>
<html lang="vi">
    <head>
        <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
        <title>Lịch dạy · Super Gym</title>
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/pt/header.css?v=20261024a">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/pt/schedule.css?v=20261024b">
        <c:if test="${not empty week}"><script src="${pageContext.request.contextPath}/js/pages/pt/schedule.js?v=20261024b" defer></script></c:if>
    </head>
    <body class="dock theme-pt pt-plan-page">
        <%@ include file="/WEB-INF/jsp/common/flash-toast.jspf" %>
        <div class="app">
            <%@ include file="/WEB-INF/jsp/common/layout-pt.jspf" %>
            <div class="main">
                <%@ include file="/WEB-INF/jsp/pt/_header.jspf" %>

                <c:choose>
                    <%-- ==================== LỖI TẢI: báo rõ, không giả làm lịch trống ==================== --%>
                    <c:when test="${not empty scheduleError}">
                        <main class="content pt-plan" id="main-content" tabindex="-1">
                            <section class="panel pt-plan-error" role="alert">
                                <p><c:out value="${scheduleError}" /></p>
                                <a class="btn btn-outline" href="${pageContext.request.contextPath}/pt/schedule">Tải lại</a>
                            </section>
                        </main>
                    </c:when>

                    <c:otherwise>
                        <%-- Link đổi tuần giữ kiểu xem; link đổi kiểu xem giữ ngày. --%>
                        <c:url var="prevWeekUrl" value="/pt/schedule"><c:param name="date" value="${week.prevWeek}" /><c:param name="view" value="${view}" /></c:url>
                        <c:url var="nextWeekUrl" value="/pt/schedule"><c:param name="date" value="${week.nextWeek}" /><c:param name="view" value="${view}" /></c:url>
                        <c:url var="thisWeekUrl" value="/pt/schedule"><c:param name="view" value="${view}" /></c:url>
                        <c:url var="weekViewUrl" value="/pt/schedule"><c:param name="date" value="${viewDate}" /><c:param name="view" value="week" /></c:url>
                        <c:url var="listViewUrl" value="/pt/schedule"><c:param name="date" value="${viewDate}" /><c:param name="view" value="list" /></c:url>

                        <%-- Tuần này còn ngày nào thêm được khung không? (Service để firstStartMinute = null nếu không) --%>
                        <c:set var="canAddAny" value="false" />
                        <c:forEach var="day" items="${week.days}">
                            <c:if test="${not empty day.firstStartMinute}"><c:set var="canAddAny" value="true" /></c:if>
                        </c:forEach>

                        <main class="content pt-plan ${view eq 'list' ? 'is-list' : ''}" id="main-content" tabindex="-1"
                              data-duration="${week.sessionMinutes}"
                              data-axis-start="${week.axisStartHour * 60}" data-axis-end="${week.axisEndHour * 60}"
                              data-draft-key="${draftAccountId}:${week.weekStart}">

                            <%-- ==================== 1. PANEL LỊCH ==================== --%>
                            <section class="panel pt-plan-board" aria-labelledby="pt-plan-range">

                                <%-- 1.1 Thanh công cụ: cùng kiểu với màn Lịch tập của khách --%>
                                <header class="pt-plan-toolbar">
                                    <nav class="pt-plan-period" aria-label="Chọn tuần">
                                        <a class="btn btn-outline pt-plan-step" href="${prevWeekUrl}" aria-label="Tuần trước"><i class="fa-solid fa-chevron-left" aria-hidden="true"></i></a>
                                        <h2 id="pt-plan-range"><c:out value="${fn:substring(gms:formatDate(week.weekStart), 0, 5)}" /> – <c:out value="${gms:formatDate(week.weekEnd)}" /></h2>
                                        <a class="btn btn-outline pt-plan-step" href="${nextWeekUrl}" aria-label="Tuần sau"><i class="fa-solid fa-chevron-right" aria-hidden="true"></i></a>
                                        <c:if test="${not week.currentWeek}"><a class="btn btn-ghost" href="${thisWeekUrl}">Tuần này</a></c:if>
                                    </nav>

                                    <div class="pt-plan-actions">
                                        <c:choose>
                                            <c:when test="${canAddAny}">
                                                <%-- Thêm khung bằng form: cách chính trên điện thoại, cách bàn phím trên máy tính. --%>
                                                <details class="pt-plan-add">
                                                    <summary class="btn btn-outline"><i class="fa-solid fa-calendar-plus" aria-hidden="true"></i> Thêm khung</summary>
                                                    <form id="pt-add-form" class="pt-plan-add-form">
                                                        <label>Ngày
                                                            <select name="date">
                                                                <c:forEach var="day" items="${week.days}">
                                                                    <c:if test="${not empty day.firstStartMinute}">
                                                                        <option value="${day.date}" ${day.today ? 'selected' : ''}><c:out value="${day.dayName}" /> · <c:out value="${fn:substring(gms:formatDate(day.date), 0, 5)}" /></option>
                                                                    </c:if>
                                                                </c:forEach>
                                                            </select>
                                                        </label>
                                                        <label>Giờ bắt đầu
                                                            <input type="time" name="start" step="900" required>
                                                        </label>
                                                        <p class="pt-plan-add-end">Kết thúc <output name="end">--:--</output> · <c:out value="${week.sessionMinutes}" /> phút</p>
                                                        <button class="btn btn-primary" type="submit">Thêm vào nháp</button>
                                                    </form>
                                                </details>
                                            </c:when>
                                            <c:otherwise>
                                                <p class="pt-plan-readonly">Tuần này chỉ xem: không còn ngày nào thêm được khung.</p>
                                            </c:otherwise>
                                        </c:choose>

                                        <nav class="pt-plan-switch" aria-label="Kiểu xem">
                                            <a href="${weekViewUrl}" aria-current="${view eq 'week' ? 'page' : 'false'}">Tuần</a>
                                            <a href="${listViewUrl}" aria-current="${view eq 'list' ? 'page' : 'false'}">Danh sách</a>
                                        </nav>
                                    </div>
                                </header>

                                <%-- 1.2 Thông báo cho trình đọc màn hình (ẩn khỏi mắt). Lỗi thì JS hiện thêm toast chung của app. --%>
                                <p class="visually-hidden" id="pt-plan-status" role="status"></p>

                                <%-- 1.3 Lưới 7 ngày. Màn rộng + kiểu Tuần: lưới giờ. Còn lại: danh sách theo ngày (CSS quyết định). --%>
                                <div class="pt-plan-scroll">
                                    <div class="pt-plan-grid" style="--axis-hours: ${week.axisEndHour - week.axisStartHour}">

                                        <div class="pt-plan-axis-head" aria-hidden="true"></div>
                                        <div class="pt-plan-axis" aria-hidden="true">
                                            <c:forEach var="hour" begin="${week.axisStartHour}" end="${week.axisEndHour - 1}">
                                                <span><c:out value="${hour lt 10 ? '0' += hour : hour}" />:00</span>
                                            </c:forEach>
                                        </div>

                                        <c:forEach var="day" items="${week.days}">
                                            <c:set var="dayLabel" value="${day.dayName} ${fn:substring(gms:formatDate(day.date), 0, 5)}" />
                                            <section class="pt-day ${day.today ? 'is-today' : ''} ${day.past ? 'is-past' : ''} ${day.closedAllDay ? 'is-closed' : ''}" aria-label="${dayLabel}">

                                                <h3 class="pt-day-head">
                                                    <span class="pt-day-name"><c:out value="${day.dayName}" /></span>
                                                    <span class="pt-day-date"><c:out value="${fn:substring(gms:formatDate(day.date), 0, 5)}" /></span>
                                                    <c:if test="${day.today}"><small class="pt-day-note is-today">Hôm nay</small></c:if>
                                                    <c:if test="${day.closedAllDay}"><small class="pt-day-note is-closed">Phòng nghỉ</small></c:if>
                                                    <c:if test="${not empty day.leaveLabel}"><small class="pt-day-note is-leave" title="${fn:escapeXml(day.leaveLabel)}"><c:out value="${day.leaveLabel}" /></small></c:if>
                                                </h3>

                                                <%-- data-first / data-last: khoảng giờ được thêm khung (phút trong ngày); thiếu = không thêm được. --%>
                                                <div class="pt-day-track" data-date="${day.date}" data-label="${fn:escapeXml(dayLabel)}"
                                                     <c:if test="${not empty day.firstStartMinute}">data-first="${day.firstStartMinute}" data-last="${day.lastStartMinute}"</c:if>>

                                                    <c:forEach var="item" items="${day.items}">
                                                        <c:set var="timeText" value="${gms:formatTime(item.startTime)} – ${gms:formatTime(item.endTime)}" />
                                                        <%-- data-start/data-end chỉ có ở thẻ CHIẾM giờ: JS dùng để báo trùng khi thêm khung. --%>
                                                        <article class="pt-slot is-${item.kind} ${item.past ? 'is-past' : ''}"
                                                                 style="--start: ${item.startMinute}; --length: ${item.lengthMinutes}"
                                                                 <c:if test="${item.blocking}">data-start="${item.startOfDay}" data-end="${item.endOfDay}"</c:if>
                                                                 title="${fn:escapeXml(timeText)}${item.booked ? ' · ' : ''}${fn:escapeXml(item.customerNames)} · ${fn:escapeXml(item.statusLabel)}">
                                                            <c:choose>
                                                                <%-- Khung trống gỡ được: cả thẻ là <label>, bấm thẻ = tích ô "gỡ". --%>
                                                                <c:when test="${item.canRemove}">
                                                                    <label class="pt-slot-body pt-slot-toggle">
                                                                        <input type="checkbox" class="pt-remove" name="removeSlotId" value="${item.timeSlotId}" form="pt-publish-form"
                                                                               aria-label="Gỡ khung ${timeText}, ${dayLabel}">
                                                                        <span class="pt-slot-time"><c:out value="${timeText}" /></span>
                                                                        <span class="pt-slot-state"><c:out value="${item.statusLabel}" /></span>
                                                                        <span class="pt-slot-flag">Chờ gỡ</span>
                                                                        <span class="pt-slot-hint" aria-hidden="true">
                                                                            <span class="when-off">Bấm để gỡ</span>
                                                                            <span class="when-on">Bấm để giữ lại</span>
                                                                        </span>
                                                                    </label>
                                                                </c:when>

                                                                <%-- Các thẻ khác chỉ để xem. --%>
                                                                <c:otherwise>
                                                                    <div class="pt-slot-body">
                                                                        <span class="pt-slot-time"><c:out value="${timeText}" /></span>
                                                                        <c:if test="${item.booked}">
                                                                            <strong class="pt-slot-person"><c:out value="${item.customerNames}" /></strong>
                                                                            <span class="pt-slot-package"><c:out value="${item.packageName}" /> · Buổi <c:out value="${item.sessionNo}" />/<c:out value="${item.totalSessions}" /></span>
                                                                        </c:if>
                                                                        <span class="pt-slot-state"><c:out value="${item.statusLabel}" /></span>
                                                                    </div>
                                                                </c:otherwise>
                                                            </c:choose>
                                                        </article>
                                                    </c:forEach>

                                                    <p class="pt-day-empty">
                                                        <c:choose>
                                                            <c:when test="${day.closedAllDay}">Phòng nghỉ cả ngày</c:when>
                                                            <c:otherwise>Chưa có khung</c:otherwise>
                                                        </c:choose>
                                                    </p>
                                                </div>
                                            </section>
                                        </c:forEach>
                                    </div>
                                </div>

                                <%-- 1.4 Chú thích màu --%>
                                <footer class="pt-plan-legend" aria-label="Chú thích màu">
                                    <span data-kind="free">Còn trống</span>
                                    <span data-kind="booked">Có khách</span>
                                    <span data-kind="held">Đang giữ</span>
                                    <span data-kind="locked">Tạm khóa (nghỉ phép)</span>
                                    <span data-kind="draft">Nháp mới</span>
                                    <span data-kind="removing">Chờ gỡ</span>
                                </footer>

                                <%-- Bóng khung đi theo chuột trên Lịch tuần; JS chuyển nó vào cột đang trỏ. --%>
                                <div class="pt-ghost" id="pt-ghost" aria-hidden="true" hidden>
                                    <span class="pt-slot-time"></span>
                                    <span class="pt-ghost-hint"></span>
                                </div>
                            </section>

                            <%-- ==================== 2. THANH CHỜ CÔNG BỐ (dính đáy) ====================
                                 Số "khung mới / khung gỡ" do CSS tự đếm (counter), JS không phải vẽ danh sách. --%>
                            <form id="pt-publish-form" class="pt-plan-pending" aria-label="Thay đổi chờ công bố">
                                <input type="hidden" name="csrfToken" value="<c:out value='${csrfToken}' />">
                                <input type="hidden" name="duration" value="${week.sessionMinutes}">
                                <div class="pt-pending-text">
                                    <p class="pt-pending-count">
                                        <span class="pt-pending-add"><b class="pt-count-add"></b> khung mới</span>
                                        <span class="pt-pending-remove"><b class="pt-count-remove"></b> khung gỡ</span>
                                    </p>
                                    <p class="pt-pending-note">Khách chưa thấy thay đổi cho tới khi bạn công bố.</p>
                                    <p class="pt-pending-error" id="pt-publish-error" role="alert"></p>
                                </div>
                                <div class="pt-pending-actions">
                                    <button class="btn btn-ghost" type="button" id="pt-clear">Bỏ tất cả</button>
                                    <button class="btn btn-primary" type="submit" id="pt-publish">Công bố</button>
                                </div>
                            </form>

                            <%-- ==================== 3. MẪU KHUNG NHÁP (JS sao chép) ==================== --%>
                            <template id="pt-draft-template">
                                <article class="pt-slot pt-draft">
                                    <input type="hidden" name="slotDate" form="pt-publish-form">
                                    <input type="hidden" name="startTime" form="pt-publish-form">
                                    <div class="pt-slot-body">
                                        <span class="pt-slot-time"></span>
                                        <span class="pt-slot-state">Nháp mới</span>
                                    </div>
                                    <button type="button" class="pt-draft-remove" aria-label="Bỏ khung nháp"><i class="fa-solid fa-xmark" aria-hidden="true"></i></button>
                                </article>
                            </template>
                        </main>
                    </c:otherwise>
                </c:choose>
            </div>
        </div>
    </body>
</html>
