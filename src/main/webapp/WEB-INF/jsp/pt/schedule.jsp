<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="gms" uri="/WEB-INF/gms-functions.tld" %>
<%--
================================================================================
 schedule.jsp — MÀN LỊCH DẠY PT (/pt/schedule)

 AI GỬI DỮ LIỆU SANG?  PtScheduleServlet.doGet gọi PtScheduleService.getWeek(accountId, ngày) MỘT lần.

   Biến JSP        Ai tạo                                        DAO đọc bảng nào (PtScheduleDAO)
   ─────────────── ───────────────────────────────────────────── ─────────────────────────────────────────────
   week            Service getWeek: dựng tuần Thứ 2 → Chủ nhật,  –
                   trục giờ, P-06 = 90 phút
     └ week.days   Service buildDay: 7 PtScheduleDay             OperatingHourExceptions (đổi giờ / nghỉ cả ngày)
                   (giờ mở cửa P-19 = 05:30-22:00, nghỉ phép)    LeaveRequests (đơn nghỉ Pending / Approved)
       └ day.items Service fillWeek: xếp PtScheduleItem vào ngày findSessions:  Sessions + TimeSlots + Subscriptions
                   + chọn màu (kind) + chữ trạng thái                          + Orders (PackageNameSnapshot) + Persons
                                                                 findOpenSlots: TimeSlots Free / Held chưa có buổi
   scheduleError   câu lỗi khi Service lỗi; lúc đó KHÔNG có week
   view            "week" | "list" (kiểu xem); viewDate = ngày đang xem; today = hôm nay
   csrfToken, draftAccountId   cho form Công bố và khóa lưu nháp trong trình duyệt

 BỐ CỤC
   1. Panel lịch: thanh công cụ (‹ tuần ›, Thêm khung, Tuần/Danh sách) → lưới 7 ngày → chú thích màu
   2. Thanh "Chờ công bố" dính đáy: CSS tự hiện khi có nháp hoặc khung chờ gỡ
   3. <template> khung nháp: JS sao chép khi PT thêm khung
   4. Hộp "Xử lý buổi" (_handle-dialog.jspf): có khi URL có ?handle=<mã buổi>; biến "handle" ← PtSessionHandleService

 AI LÀM GÌ
   Gỡ khung   = ô checkbox trong thẻ khung trống. Không cần JS: CSS đổi kiểu thẻ khi được tích.
   Thêm khung = JS (schedule.js) sao chép <template>, thẻ nháp có 2 ô ẩn slotDate + startTime.
   Công bố    = JS gửi mọi ô thuộc form #pt-publish-form (kể cả ô nằm ngoài form nhờ form="pt-publish-form")
                → PtScheduleServlet.doPost → PtScheduleService.publish (1 transaction).
   Xử lý buổi = thẻ buổi là link ?handle= → hộp 3 phương án; mỗi phương án 1 form POST thường
                → doPost (action=reschedule|transfer|cancel) → PtSessionHandleService.
================================================================================ --%>
<!DOCTYPE html>
<html lang="vi">
    <head>
        <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
        <title>Lịch dạy · Super Gym</title>
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/pt/header.css?v=20261025city">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/pt/schedule.css?v=20261029handle">
        <%-- Chỉ nạp JS khi có lịch (lỗi tải thì trang không cần JS). --%>
        <c:if test="${not empty week}"><script src="${pageContext.request.contextPath}/js/pages/pt/schedule.js?v=20261026t" defer></script></c:if>
    </head>
    <body class="dock theme-pt pt-plan-page">
        <%@ include file="/WEB-INF/jsp/common/flash-toast.jspf" %>
        <div class="app">
            <%-- Thanh menu trái của PT. --%>
            <%@ include file="/WEB-INF/jsp/common/layout-pt.jspf" %>
            <div class="main">
                <%-- Topbar: pageTitle "Lịch dạy" + nút (i) pageHelp + tên PT + mã NV. --%>
                <%@ include file="/WEB-INF/jsp/pt/_header.jspf" %>

                <c:choose>
                    <%-- ==========================================================================
                         TRƯỜNG HỢP LỖI TẢI — BẮT ĐẦU
                         scheduleError (Service trả khi DAO lỗi), VD "Hệ thống đang bận…"
                         → khung đỏ + nút "Tải lại". Báo rõ, không giả làm lịch trống.
                         ========================================================================== --%>
                    <c:when test="${not empty scheduleError}">
                        <main class="content pt-plan" id="main-content" tabindex="-1">
                            <section class="panel pt-plan-error" role="alert">
                                <p><c:out value="${scheduleError}" /></p>
                                <a class="btn btn-outline" href="${pageContext.request.contextPath}/pt/schedule">Tải lại</a>
                            </section>
                        </main>
                    </c:when>
                    <%-- TRƯỜNG HỢP LỖI TẢI — KẾT THÚC --%>

                    <c:otherwise>
                        <%-- ----- Chuẩn bị link -----
                             Link đổi tuần giữ kiểu xem; link đổi kiểu xem giữ ngày.
                             week.prevWeek / nextWeek (Service: Thứ 2 tuần trước / sau) → VD /pt/schedule?date=2026-10-19&view=week --%>
                        <c:url var="prevWeekUrl" value="/pt/schedule"><c:param name="date" value="${week.prevWeek}" /><c:param name="view" value="${view}" /></c:url>
                        <c:url var="nextWeekUrl" value="/pt/schedule"><c:param name="date" value="${week.nextWeek}" /><c:param name="view" value="${view}" /></c:url>
                        <c:url var="thisWeekUrl" value="/pt/schedule"><c:param name="view" value="${view}" /></c:url>
                        <c:url var="weekViewUrl" value="/pt/schedule"><c:param name="date" value="${viewDate}" /><c:param name="view" value="week" /></c:url>
                        <c:url var="listViewUrl" value="/pt/schedule"><c:param name="date" value="${viewDate}" /><c:param name="view" value="list" /></c:url>

                        <%-- canAddAn  hy: tuần này còn ngày nào thêm được khung không?
                             (Service để day.firstStartMinute = null cho ngày đã qua / nghỉ / hết giờ.) --%>
                        <c:set var="canAddAny" value="false" />
                        <%-- canRemoveAny: tuần này có khung trống gỡ được không (item.canRemove ← Service) → nút "Gỡ hết khung trống". --%>
                        <c:set var="canRemoveAny" value="false" />
                        <c:forEach var="day" items="${week.days}">
                            <c:if test="${not empty day.firstStartMinute}"><c:set var="canAddAny" value="true" /></c:if>
                            <c:forEach var="item" items="${day.items}">
                                <c:if test="${item.canRemove}"><c:set var="canRemoveAny" value="true" /></c:if>
                            </c:forEach>
                        </c:forEach>

                        <%-- data-… cho schedule.js: duration = P-06 (90), trục giờ tính bằng phút (VD 300 = 05:00),
                             draft-key = "mãTàiKhoản:ngàyThứ2" để nháp của tuần nào lưu riêng tuần đó. --%>
                        <main class="content pt-plan ${view eq 'list' ? 'is-list' : ''}" id="main-content" tabindex="-1"
                              data-duration="${week.sessionMinutes}"
                              data-axis-start="${week.axisStartHour * 60}" data-axis-end="${week.axisEndHour * 60}"
                              data-draft-key="${draftAccountId}:${week.weekStart}"
                              data-next-key="${draftAccountId}:${week.nextWeek}" data-next-url="${nextWeekUrl}">

                            <%-- ==========================================================================
                                 KHỐI 1. PANEL LỊCH — BẮT ĐẦU
                                 ========================================================================== --%>
                            <section class="panel pt-plan-board" aria-labelledby="pt-plan-range">

                                <%-- ----- 1.1 Thanh công cụ (cùng kiểu với màn Lịch tập của khách) ----- --%>
                                <header class="pt-plan-toolbar">
                                    <nav class="pt-plan-period" aria-label="Chọn tuần">
                                        <a class="btn btn-outline pt-plan-step" href="${prevWeekUrl}" aria-label="Tuần trước"><i class="fa-solid fa-chevron-left" aria-hidden="true"></i></a>
                                        <%-- Khoảng tuần: week.weekStart cắt "dd/MM" + " – " + week.weekEnd đủ "dd/MM/yyyy" → "26/10 – 01/11/2026". --%>
                                        <h2 id="pt-plan-range"><c:out value="${fn:substring(gms:formatDate(week.weekStart), 0, 5)}" /> – <c:out value="${gms:formatDate(week.weekEnd)}" /></h2>
                                        <a class="btn btn-outline pt-plan-step" href="${nextWeekUrl}" aria-label="Tuần sau"><i class="fa-solid fa-chevron-right" aria-hidden="true"></i></a>
                                        <%-- Nhảy thẳng tới ngày bất kỳ (khỏi bấm ‹ › nhiều lần): chọn ngày → schedule.js gửi form GET
                                             ?date=…&view=… → Service dựng tuần chứa ngày đó. Tắt JS: nút "Xem". --%>
                                        <form class="pt-plan-jump" method="get" action="${pageContext.request.contextPath}/pt/schedule">
                                            <input type="hidden" name="view" value="${view}">
                                            <label class="pt-plan-jump-field" title="Chọn ngày để xem tuần đó">
                                                <i class="fa-regular fa-calendar" aria-hidden="true"></i>
                                                <span class="visually-hidden">Xem tuần của ngày</span>
                                                <input type="date" name="date" value="${viewDate}" required data-jump-date>
                                            </label>
                                            <noscript><button class="btn btn-outline" type="submit">Xem</button></noscript>
                                        </form>
                                        <%-- week.currentWeek = false (đang xem tuần khác) → hiện nút "Tuần này". --%>
                                        <c:if test="${not week.currentWeek}"><a class="btn btn-ghost" href="${thisWeekUrl}">Tuần này</a></c:if>
                                    </nav>

                                    <div class="pt-plan-actions">
                                        <c:choose>
                                            <c:when test="${canAddAny}">
                                                <%-- Nút "Thêm khung" mở form nhỏ: cách chính trên điện thoại, cách bàn phím trên máy tính. --%>
                                                <details class="pt-plan-add">
                                                    <summary class="btn btn-outline"><i class="fa-solid fa-calendar-plus" aria-hidden="true"></i> Thêm khung</summary>
                                                    <form id="pt-add-form" class="pt-plan-add-form">
                                                        <%-- Ô chọn ngày: chỉ ngày còn thêm được; mỗi lựa chọn "Thứ 3 · 27/10" (day.dayName · day.date). --%>
                                                        <label>Ngày
                                                            <select name="date">
                                                                <c:forEach var="day" items="${week.days}">
                                                                    <c:if test="${not empty day.firstStartMinute}">
                                                                        <option value="${day.date}" ${day.today ? 'selected' : ''}><c:out value="${day.dayName}" /> · <c:out value="${fn:substring(gms:formatDate(day.date), 0, 5)}" /></option>
                                                                    </c:if>
                                                                </c:forEach>
                                                            </select>
                                                        </label>
                                                        <%-- Giờ bắt đầu: step 900 giây = mốc 15 phút. --%>
                                                        <label>Giờ bắt đầu
                                                            <input type="time" name="start" step="900" required>
                                                        </label>
                                                        <%-- "Kết thúc 19:30 · 90 phút": JS điền giờ kết thúc; week.sessionMinutes = P-06. --%>
                                                        <p class="pt-plan-add-end">Kết thúc <output name="end">--:--</output> · <c:out value="${week.sessionMinutes}" /> phút</p>
                                                        <button class="btn btn-primary" type="submit">Thêm vào nháp</button>
                                                    </form>
                                                </details>
                                            </c:when>
                                            <c:otherwise>
                                                <%-- Tuần đã qua / không còn ngày thêm được → chỉ một dòng chữ. --%>
                                                <p class="pt-plan-readonly">Tuần này chỉ xem: không còn ngày nào thêm được khung.</p>
                                            </c:otherwise>
                                        </c:choose>

                                        <%-- Thao tác nhanh — chỉ tạo NHÁP (thanh Chờ công bố), PT xem lại rồi mới Công bố:
                                               Gỡ hết khung trống  tích mọi ô gỡ (chỉ khung Còn trống, không khách, không bị giữ) + bỏ nháp
                                               Chép sang tuần sau  khung của tuần này → nháp tuần sau (cùng thứ, cùng giờ), rồi mở tuần sau --%>
                                        <div class="pt-plan-tools" role="group" aria-label="Thao tác nhanh">
                                            <c:if test="${canRemoveAny}">
                                                <button type="button" class="btn btn-ghost" data-plan-action="clear-free"><i class="fa-solid fa-calendar-xmark" aria-hidden="true"></i> Gỡ hết khung trống</button>
                                            </c:if>
                                            <button type="button" class="btn btn-ghost" data-plan-action="copy-next"><i class="fa-regular fa-copy" aria-hidden="true"></i> Chép sang tuần sau</button>
                                        </div>

                                        <%-- Công tắc kiểu xem: link đang chọn có aria-current="page" → CSS tô nền. --%>
                                        <nav class="pt-plan-switch" aria-label="Kiểu xem">
                                            <a href="${weekViewUrl}" aria-current="${view eq 'week' ? 'page' : 'false'}">Tuần</a>
                                            <a href="${listViewUrl}" aria-current="${view eq 'list' ? 'page' : 'false'}">Danh sách</a>
                                        </nav>
                                    </div>
                                </header>

                                <%-- ----- 1.1b Buổi trên ?handle= không xử lý được (không phải buổi của bạn, đã bắt đầu…)
                                     handleError ← PtScheduleServlet.loadHandle; handleFormError ← Lưu bị từ chối mà hộp không mở lại được. ----- --%>
                                <c:if test="${not empty handleError}">
                                    <p class="pt-plan-alert" role="alert"><i class="fa-solid fa-circle-exclamation" aria-hidden="true"></i>
                                        <c:out value="${not empty handleFormError ? handleFormError : handleError}" /></p>
                                </c:if>
                                <%-- Chú thích đặt trước lịch; cùng màu và kiểu viền với các khung ở cả hai giao diện. --%>
                                <div class="pt-plan-legend" aria-label="Màu trạng thái">
                                    <span data-kind="free">Còn trống</span>
                                    <span data-kind="booked">Có khách</span>
                                    <span data-kind="held">Đang giữ</span>
                                    <span data-kind="locked">Tạm khóa</span>
                                    <span data-kind="draft">Nháp mới</span>
                                    <span data-kind="removing">Chờ gỡ</span>
                                </div>
                                <div class="pt-list-columns" aria-hidden="true">
                                    <span>Giờ tập</span><span>Buổi / khung giờ</span><span>Trạng thái</span><span>Thao tác</span>
                                </div>

                                <%-- ----- 1.2 Thông báo cho trình đọc màn hình (ẩn khỏi mắt). Lỗi thì JS hiện thêm toast chung. ----- --%>
                                <p class="visually-hidden" id="pt-plan-status" role="status"></p>

                                <%-- ----- 1.3 Lưới 7 ngày -----
                                     Màn rộng + kiểu Tuần: lưới giờ (thẻ đặt theo phút). Còn lại: danh sách theo ngày (CSS quyết định).
                                     --axis-hours = số giờ trên trục, VD 22 − 5 = 17 hàng giờ. --%>
                                <div class="pt-plan-scroll">
                                    <div class="pt-plan-grid" style="--axis-hours: ${week.axisEndHour - week.axisStartHour}">

                                        <%-- Cột trục giờ bên trái: "05:00", "06:00", … (thêm số 0 trước giờ < 10). --%>
                                        <div class="pt-plan-axis-head" aria-hidden="true"></div>
                                        <div class="pt-plan-axis" aria-hidden="true">
                                            <c:forEach var="hour" begin="${week.axisStartHour}" end="${week.axisEndHour - 1}">
                                                <span><c:out value="${hour lt 10 ? '0' += hour : hour}" />:00</span>
                                            </c:forEach>
                                        </div>

                                        <%-- Mỗi ngày 1 cột. --%>
                                        <c:forEach var="day" items="${week.days}">
                                            <c:set var="dayLabel" value="${day.dayName} ${fn:substring(gms:formatDate(day.date), 0, 5)}" />
                                            <section class="pt-day ${day.today ? 'is-today' : ''} ${day.past ? 'is-past' : ''} ${day.closedAllDay ? 'is-closed' : ''}" aria-label="${dayLabel}">

                                                <%-- Đầu cột: "Thứ 3" + "27/10" + nhãn nhỏ:
                                                       "Hôm nay" (day.today), "Phòng nghỉ" (day.closedAllDay ← OperatingHourExceptions),
                                                       day.leaveLabel ← LeaveRequests, VD "Nghỉ phép ca sáng · chờ duyệt". --%>
                                                <div class="pt-day-head">
                                                    <h3 class="pt-day-title">
                                                    <span class="pt-day-name"><c:out value="${day.dayName}" /></span>
                                                    <span class="pt-day-date"><c:out value="${fn:substring(gms:formatDate(day.date), 0, 5)}" /></span>
                                                    </h3>
                                                    <c:if test="${day.today}"><small class="pt-day-note is-today">Hôm nay</small></c:if>
                                                    <c:if test="${day.closedAllDay}"><small class="pt-day-note is-closed">Phòng nghỉ</small></c:if>
                                                    <c:if test="${not empty day.leaveLabel}">
                                                        <details class="pt-day-leave">
                                                            <summary><c:out value="${day.leaveLabel}" /></summary>
                                                            <div class="pt-day-leave-details">
                                                                <p class="pt-day-leave-reason"><c:out value="${day.leaveDetails}" /></p>
                                                                <p>Đơn chờ duyệt cũng tạm khóa khung trống trong ca nghỉ. Buổi đã có khách không tự hủy.</p>
                                                                <c:if test="${not empty day.leaveBookings}">
                                                                    <p>Buổi có khách trùng ca nghỉ:</p>
                                                                    <ul><c:forEach var="booking" items="${day.leaveBookings}"><li><c:out value="${booking}" /></li></c:forEach></ul>
                                                                </c:if>
                                                                <p>“Chưa chốt” nghĩa là buổi đã kết thúc nhưng chưa có kết quả, không có nghĩa là đã hủy hoặc khách vắng.</p>
                                                            </div>
                                                        </details>
                                                        <c:if test="${not empty day.leaveBookings}"><small class="pt-day-note is-conflict"><c:out value="${fn:length(day.leaveBookings)}" /> buổi trùng ca nghỉ</small></c:if>
                                                    </c:if>
                                                </div>

                                                <%-- Vùng thả khung. data-first / data-last: khoảng giờ được thêm khung (phút trong ngày,
                                                     VD 330 = 05:30, 1230 = 20:30); thiếu 2 thuộc tính này = ngày không thêm được. --%>
                                                <div class="pt-day-track" data-date="${day.date}" data-label="${fn:escapeXml(dayLabel)}"
                                                     <c:if test="${not empty day.firstStartMinute}">data-first="${day.firstStartMinute}" data-last="${day.lastStartMinute}"</c:if>>

                                                    <c:forEach var="item" items="${day.items}">
                                                        <%-- timeText "18:00 – 19:30" ← item.startTime / endTime (DAO TimeSlots.StartTime / EndTime). --%>
                                                        <c:set var="timeText" value="${gms:formatTime(item.startTime)} – ${gms:formatTime(item.endTime)}" />
                                                        <%-- Thẻ khung. Màu theo item.kind (Service chọn): free = còn trống, booked = có khách,
                                                             held = đang giữ cho đơn online, locked = tạm khóa vì nghỉ phép.
                                                             --start / --length: vị trí + chiều cao thẻ trên lưới (Service tính bằng phút).
                                                             data-start / data-end chỉ có ở thẻ CHIẾM giờ: JS dùng để báo trùng khi thêm khung.
                                                             title (rê chuột): "18:00 – 19:30 · Đỗ Thị Mai · Sắp tới". --%>
                                                        <%-- id="session-120": sau khi dời / hủy, Servlet redirect về #session-120 → trình duyệt cuộn tới, CSS :target tô viền. --%>
                                                        <article class="pt-slot is-${item.kind} ${item.past ? 'is-past' : ''} ${item.canHandle ? 'is-handleable' : ''}"
                                                                 <c:if test="${item.booked}">id="session-${item.sessionId}"</c:if>
                                                                 data-status="<c:out value='${item.sessionStatus}' />"
                                                                 style="--start: ${item.startMinute}; --length: ${item.lengthMinutes}"
                                                                 <c:if test="${item.blocking}">data-start="${item.startOfDay}" data-end="${item.endOfDay}"</c:if>
                                                                 title="${fn:escapeXml(timeText)}${item.booked ? ' · ' : ''}${fn:escapeXml(item.customerNames)} · ${fn:escapeXml(item.statusLabel)}">
                                                            <c:choose>
                                                                <%-- Khung trống gỡ được (item.canRemove, Service xét): cả thẻ là <label>, bấm thẻ = tích ô "gỡ".
                                                                     Ô tích gửi removeSlotId = item.timeSlotId (TimeSlots.TimeSlotId) khi Công bố.
                                                                     Tích ô chỉ đánh dấu Chờ gỡ; Giữ lại bỏ lựa chọn, Lưu thay đổi mới ghi DB. --%>
                                                                <c:when test="${item.canRemove}">
                                                                    <label class="pt-slot-body pt-slot-toggle">
                                                                        <input type="checkbox" class="pt-remove" name="removeSlotId" value="${item.timeSlotId}" form="pt-publish-form"
                                                                               aria-label="Gỡ khung ${timeText}, ${dayLabel}">
                                                                        <span class="pt-slot-time"><c:out value="${timeText}" /></span>
                                                                        <span class="pt-slot-info"><strong>Khung rảnh</strong></span>
                                                                        <span class="pt-slot-state"><c:out value="${item.statusLabel}" /></span>
                                                                        <span class="pt-slot-flag">Chờ gỡ</span>
                                                                        <span class="pt-slot-hint" aria-hidden="true">
                                                                            <span class="when-off">Gỡ khung</span>
                                                                            <span class="when-on">Giữ lại</span>
                                                                        </span>
                                                                    </label>
                                                                </c:when>

                                                                <%-- Các thẻ khác chỉ để xem. Khung có khách (item.booked) hiện thêm:
                                                                       item.customerNames ← Persons.FullName (gói đôi "A & B"), in đậm
                                                                       item.packageName   ← Orders.PackageNameSnapshot (tên gói lúc mua)
                                                                       "Buổi 20/45"       ← sessionNo (ROW_NUMBER trong SQL) / totalSessions (Subscriptions.GeneratedSessionCount)
                                                                     item.statusLabel (Service): "Còn trống", "Sắp tới", "Đã check-in", "Vắng"… --%>
                                                                <%-- Buổi có khách còn xử lý được (item.canHandle: Scheduled + chưa tới giờ) → cả thẻ là LINK
                                                                     ?date=…&view=…&handle=<mã buổi> → doGet mở hộp Xử lý buổi (_handle-dialog.jspf). Không cần JS. --%>
                                                                <c:when test="${item.canHandle}">
                                                                    <c:url var="handleUrl" value="/pt/schedule"><c:param name="date" value="${day.date}" /><c:param name="view" value="${view}" /><c:param name="handle" value="${item.sessionId}" /></c:url>
                                                                    <a class="pt-slot-body pt-slot-link" href="${handleUrl}" aria-label="Xử lý buổi ${timeText}, ${dayLabel}, ${fn:escapeXml(item.customerNames)}">
                                                                        <span class="pt-slot-time"><c:out value="${timeText}" /></span>
                                                                        <span class="pt-slot-info">
                                                                            <strong class="pt-slot-person"><c:out value="${item.customerNames}" /></strong>
                                                                            <span class="pt-slot-package"><c:out value="${item.packageName}" /> · Buổi <c:out value="${item.sessionNo}" />/<c:out value="${item.totalSessions}" /></span>
                                                                        </span>
                                                                        <span class="pt-slot-state"><c:out value="${item.statusLabel}" /></span>
                                                                        <span class="pt-slot-hint" aria-hidden="true">Bấm để xử lý</span>
                                                                    </a>
                                                                </c:when>

                                                                <c:otherwise>
                                                                    <div class="pt-slot-body">
                                                                        <span class="pt-slot-time"><c:out value="${timeText}" /></span>
                                                                        <span class="pt-slot-info">
                                                                            <c:choose>
                                                                                <c:when test="${item.booked}">
                                                                                    <strong class="pt-slot-person"><c:out value="${item.customerNames}" /></strong>
                                                                                    <span class="pt-slot-package"><c:out value="${item.packageName}" /> · Buổi <c:out value="${item.sessionNo}" />/<c:out value="${item.totalSessions}" /></span>
                                                                                </c:when>
                                                                                <c:when test="${item.kind eq 'held'}"><strong>Đơn chờ thanh toán</strong></c:when>
                                                                                <c:when test="${item.kind eq 'locked'}"><strong>Nghỉ phép</strong></c:when>
                                                                                <c:otherwise><strong>Khung rảnh</strong></c:otherwise>
                                                                            </c:choose>
                                                                        </span>
                                                                        <span class="pt-slot-state">
                                                                            <c:choose>
                                                                                <c:when test="${item.kind eq 'held'}">Đang giữ</c:when>
                                                                                <c:otherwise><c:out value="${item.statusLabel}" /></c:otherwise>
                                                                            </c:choose>
                                                                        </span>
                                                                    </div>
                                                                </c:otherwise>
                                                            </c:choose>
                                                        </article>
                                                    </c:forEach>

                                                    <%-- Chữ hiện khi cột không có thẻ nào (CSS ẩn nếu có thẻ). --%>
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

                                <%-- ----- 1.5 Bóng khung đi theo chuột trên Lịch tuần; JS chuyển nó vào cột đang trỏ. ----- --%>
                                <div class="pt-ghost" id="pt-ghost" aria-hidden="true" hidden>
                                    <span class="pt-slot-time"></span>
                                    <span class="pt-ghost-hint"></span>
                                </div>
                            </section>
                            <%-- KHỐI 1. PANEL LỊCH — KẾT THÚC --%>

                            <%-- ==========================================================================
                                 KHỐI 2. THANH CHỜ CÔNG BỐ (dính đáy) — BẮT ĐẦU
                                 Số "2 khung mới / 1 khung gỡ" do CSS tự đếm (counter), JS không phải vẽ danh sách.
                                 Gửi lên: csrfToken, duration (P-06 trang đang dùng), removeSlotId…, slotDate + startTime của nháp.
                                 ========================================================================== --%>
                            <form id="pt-publish-form" class="pt-plan-pending" aria-label="Thay đổi chờ lưu">
                                <input type="hidden" name="csrfToken" value="<c:out value='${csrfToken}' />">
                                <input type="hidden" name="duration" value="${week.sessionMinutes}">
                                <div class="pt-pending-text">
                                    <p class="pt-pending-count">
                                        <span class="pt-pending-add"><b class="pt-count-add"></b> khung mới</span>
                                        <span class="pt-pending-remove"><b class="pt-count-remove"></b> khung gỡ</span>
                                    </p>
                                    <p class="pt-pending-note">Thay đổi chưa áp dụng vào lịch của khách.</p>
                                    <%-- Lỗi do server trả về khi Công bố (VD trùng giờ) — JS điền chữ vào đây. --%>
                                    <p class="pt-pending-error" id="pt-publish-error" role="alert"></p>
                                </div>
                                <div class="pt-pending-actions">
                                    <button class="btn btn-ghost" type="button" id="pt-clear">Bỏ tất cả</button>
                                    <button class="btn btn-primary" type="submit" id="pt-publish">Công bố</button>
                                </div>
                            </form>
                            <%-- KHỐI 2. THANH CHỜ CÔNG BỐ — KẾT THÚC --%>

                            <%-- ==========================================================================
                                 KHỐI 3. MẪU KHUNG NHÁP (JS sao chép) — BẮT ĐẦU
                                 Không hiện cho tới khi PT thêm khung; JS điền giờ vào .pt-slot-time và 2 ô ẩn.
                                 ========================================================================== --%>
                            <template id="pt-draft-template">
                                <article class="pt-slot pt-draft">
                                    <input type="hidden" name="slotDate" form="pt-publish-form">
                                    <input type="hidden" name="startTime" form="pt-publish-form">
                                    <div class="pt-slot-body">
                                        <span class="pt-slot-time"></span>
                                        <span class="pt-slot-info"><strong>Khung rảnh mới</strong></span>
                                        <span class="pt-slot-state">Nháp mới</span>
                                    </div>
                                    <button type="button" class="pt-draft-remove" aria-label="Bỏ khung nháp"><i class="fa-solid fa-xmark" aria-hidden="true"></i></button>
                                </article>
                            </template>
                            <%-- KHỐI 3. MẪU KHUNG NHÁP — KẾT THÚC --%>

                            <%-- KHỐI 4. HỘP XỬ LÝ BUỔI — chỉ có khi URL có ?handle= và Service trả "handle". --%>
                            <c:if test="${not empty handle}">
                                <%@ include file="/WEB-INF/jsp/pt/_handle-dialog.jspf" %>
                            </c:if>
                        </main>
                    </c:otherwise>
                </c:choose>
            </div>
        </div>
    </body>
</html>
