<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="gms" uri="/WEB-INF/gms-functions.tld" %>
<%--
================================================================================
 my-pt-sessions.jsp — MÀN "LỊCH TẬP" CỦA KHÁCH (/customer/pt-sessions?view=week|month&date=yyyy-MM-dd)

 LUỒNG: GET → MyPtSessionServlet.doGet → MyPtSessionService.getWeek / getMonth → MyPtSessionDAO → JSP này.

 AI GỬI DỮ LIỆU SANG?  MyPtSessionServlet.doGet gọi MyPtSessionService MỘT lần:
   view = week  → getWeek(accountId, ngày)  → timetable
   view = month → getMonth(accountId, ngày) → month
 Service gọi DAO (MyPtSessionDAO) như sau:
   findSessionsInRange   Sessions + TimeSlots + Subscriptions + Orders + Employees/Persons (tên, SĐT PT, tên khách)
   findClosedDays        OperatingHourExceptions (ngày phòng nghỉ cả ngày)
   hasGymPass / isFrozen Subscriptions (Membership / DayPass) + FreezeRecords — mỗi buổi sắp tới, để gắn "Cần chú ý"
 Service tự tính: Sáng/Chiều/Tối (bandOf), chữ trạng thái + màu (statusKey), lời giải thích, link Zalo / Google Calendar.

 DỮ LIỆU SERVLET GỬI SANG
   view            "week" | "month"
   viewDate        ngày đang xem (từ ?date, không có thì hôm nay)
   selectedDate    CHỈ có khi URL có ?date → cột/ô đó có viền "Đang chọn"
   timetable       TimetableView (khi view = week): 7 ngày × 3 khung Sáng/Chiều/Tối
   month           MonthView (khi view = month): các tuần × 7 ô ngày + số đếm cả tháng
   timetableError  lỗi tải lịch (khi đó KHÔNG có timetable / month)
   openSessionId   ?openSession= (đến từ "Gói của tôi" / lịch tháng) → JS mở sẵn chi tiết buổi đó
   backPackageKey  ?backPackage= → link "Quay lại Gói của tôi" phía trên lịch
   dashboard       tên + mã khách ở topbar

 BỐ CỤC
   1. Thanh công cụ: ‹ kỳ › · Hôm nay · Chọn ngày · Tuần/Tháng      (toàn bộ là link / form GET, không JS)
   2. Điện thoại + lịch tuần: dãy 7 ngày là LINK ?date=… → chỉ hiện cột của ngày đó (CSS)
   3. Bảng tuần (thẻ buổi bấm được) hoặc bảng tháng (bấm ngày → sang tuần chứa ngày đó)
   4. Chú thích trạng thái (+ số buổi cả tháng ở lịch tháng)
   5. Hộp "Chi tiết buổi tập" dùng chung (my-pt-sessions.js chép chi tiết của thẻ vừa bấm vào)
================================================================================ --%>
<%-- pageUrl = "/gms/customer/pt-sessions" — gốc của mọi link đổi tuần / tháng / ngày bên dưới. --%>
<c:set var="pageUrl" value="${pageContext.request.contextPath}/customer/pt-sessions" />
<!DOCTYPE html>
<html lang="vi">
    <head>
        <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
        <title>Lịch tập · Super Gym</title>
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/customer/dashboard.css?v=20261025city">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/customer/session-status.css?v=20261025a">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/customer/my-pt-sessions.css?v=20261031a">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/auth/notification-bell.css">
        <script src="${pageContext.request.contextPath}/js/pages/customer/my-pt-sessions.js?v=20261025a" defer></script>
        <script src="${pageContext.request.contextPath}/js/pages/auth/notification-bell.js" defer></script>
    </head>
    <body class="dock ${themeClass}">
        <%@ include file="/WEB-INF/jsp/common/flash-toast.jspf" %>
        <div class="app">
            <%-- Thanh menu trái của khách. --%>
            <%@ include file="/WEB-INF/jsp/common/layout-customer.jspf" %>
            <div class="main">
                <%-- Topbar dùng chung: "Lịch tập" + (i) + nút "Đặt buổi lẻ" + chuông + tên/mã khách. --%>
                <c:set var="scheduleTitle" value="Lịch tập" />
                <%@ include file="_schedule-header.jspf" %>

                <%-- data-open-session = openSessionId (?openSession= từ Gói của tôi / lịch tháng) → JS mở sẵn hộp chi tiết của buổi đó. --%>
                <main class="content my-pt-page" id="main-content" tabindex="-1" data-open-session="${openSessionId}">
                    <c:choose>
                        <%-- ===== TRƯỜNG HỢP LỖI: timetableError → hộp "Chưa tải được lịch tập" + nút Tải lại.
                             KHÔNG vẽ lịch rỗng (khách sẽ tưởng "không có buổi nào"). ===== --%>
                        <c:when test="${not empty timetableError}">
                            <div class="empty-state" role="alert">
                                <h2>Chưa tải được lịch tập</h2>
                                <p><c:out value="${timetableError}" /></p>
                                <a class="btn btn-outline" href="${pageUrl}">Tải lại</a>
                            </div>
                        </c:when>

                        <c:otherwise>
                            <%-- Đến từ "Gói của tôi" (?backPackage=…) → link quay về ĐÚNG gói đó (Servlet đã kiểm tra backPackage là số / "single"). --%>
                            <c:if test="${not empty backPackageKey}">
                                <a class="schedule-back" href="${pageContext.request.contextPath}/customer/bookings?package=${backPackageKey}"><i class="fa-solid fa-chevron-left" aria-hidden="true"></i> Quay lại Gói của tôi</a>
                            </c:if>

                            <%-- ##################################################################
                                 BẢNG LỊCH — BẮT ĐẦU
                                 ################################################################## --%>
                            <section class="panel schedule-board" aria-label="Lịch tập">

                                <%-- ==========================================================
                                     KHỐI 1. THANH CÔNG CỤ — BẮT ĐẦU
                                     ========================================================== --%>
                                <div class="schedule-toolbar">
                                    <%-- ‹ › giữ cách xem, chỉ đổi date. Service đã tính sẵn ngày kỳ trước / kỳ sau
                                         (month.prevMonth / nextMonth hoặc timetable.prevWeek / nextWeek).
                                         Tiêu đề giữa: "Tháng 10/2026" hoặc "26/10/2026 – 01/11/2026" (timetable.weekStart / weekEnd). --%>
                                    <nav class="schedule-period" aria-label="Đổi khoảng thời gian">
                                        <a class="btn btn-outline schedule-step" aria-label="${view eq 'month' ? 'Tháng trước' : 'Tuần trước'}"
                                           href="${pageUrl}?view=${view}&amp;date=${view eq 'month' ? month.prevMonth : timetable.prevWeek}"><i class="fa-solid fa-chevron-left" aria-hidden="true"></i></a>
                                        <h2>
                                            <c:choose>
                                                <c:when test="${view eq 'month'}">Tháng ${month.month.monthValue}/${month.month.year}</c:when>
                                                <c:otherwise>${gms:formatDate(timetable.weekStart)} – ${gms:formatDate(timetable.weekEnd)}</c:otherwise>
                                            </c:choose>
                                        </h2>
                                        <a class="btn btn-outline schedule-step" aria-label="${view eq 'month' ? 'Tháng sau' : 'Tuần sau'}"
                                           href="${pageUrl}?view=${view}&amp;date=${view eq 'month' ? month.nextMonth : timetable.nextWeek}"><i class="fa-solid fa-chevron-right" aria-hidden="true"></i></a>
                                        <%-- Không gửi date → Servlet lấy hôm nay. --%>
                                        <a class="btn btn-ghost" href="${pageUrl}?view=${view}">Hôm nay</a>
                                    </nav>

                                    <div class="schedule-controls">
                                        <%-- <details> tự mở / đóng, không cần JS. Ô ẩn view giữ nguyên Tuần hay Tháng. --%>
                                        <details class="schedule-date-picker">
                                            <summary class="btn btn-outline"><i class="fa-regular fa-calendar" aria-hidden="true"></i> Chọn ngày</summary>
                                            <form method="get" action="${pageUrl}" class="schedule-date">
                                                <input type="hidden" name="view" value="${view}">
                                                <label for="schedule-date">Ngày muốn xem</label>
                                                <%-- min/max khớp giới hạn ngày Servlet chấp nhận (kiểu DATETIME của SQL Server). --%>
                                                <input id="schedule-date" name="date" type="date" value="${viewDate}" min="1754-01-01" max="9998-12-31" required>
                                                <button class="btn btn-outline" type="submit">Xem</button>
                                            </form>
                                        </details>

                                        <%-- Công tắc Tuần / Tháng: giữ ngày đang xem (viewDate); link đang chọn có aria-current → nền cam. --%>
                                        <nav class="schedule-switch" aria-label="Cách xem lịch">
                                            <a href="${pageUrl}?view=week&amp;date=${viewDate}" aria-current="${view eq 'week' ? 'page' : 'false'}">Tuần</a>
                                            <a href="${pageUrl}?view=month&amp;date=${viewDate}" aria-current="${view eq 'month' ? 'page' : 'false'}">Tháng</a>
                                        </nav>
                                    </div>
                                </div>
                                <%-- KHỐI 1. THANH CÔNG CỤ — KẾT THÚC --%>

                                <c:choose>
                                    <c:when test="${view eq 'week'}">

                                        <%-- ==========================================================
                                             KHỐI 2. DÃY 7 NGÀY TRÊN ĐIỆN THOẠI — BẮT ĐẦU
                                             mobileDay = số cột 0..6 được hiện trên điện thoại.
                                             Ưu tiên: ngày trên URL (?date) → hôm nay → Thứ 2. Màn rộng không dùng tới. --%>
                                        <c:set var="mobileDay" value="${0}" />
                                        <c:forEach var="d" items="${timetable.days}" varStatus="st">
                                            <c:if test="${d.today}"><c:set var="mobileDay" value="${st.index}" /></c:if>
                                        </c:forEach>
                                        <c:forEach var="d" items="${timetable.days}" varStatus="st">
                                            <c:if test="${d.date eq selectedDate}"><c:set var="mobileDay" value="${st.index}" /></c:if>
                                        </c:forEach>

                                        <%-- Mỗi nút là 1 LINK ?date=… (giống link ngày ở lịch tháng) → Servlet đặt selectedDate → cột đó hiện.
                                             Hiện: "T2" nhỏ + số ngày to "26"; ngày đang hiện có nền cam (is-shown). --%>
                                        <nav class="schedule-mobile-days" aria-label="Chọn ngày trong tuần">
                                            <c:forEach var="d" items="${timetable.days}" varStatus="st">
                                                <a href="${pageUrl}?view=week&amp;date=${d.date}"
                                                   class="${d.today ? 'is-today' : ''} ${st.index eq mobileDay ? 'is-shown' : ''}"
                                                   aria-current="${st.index eq mobileDay ? 'date' : 'false'}"
                                                   aria-label="${d.name}, ${gms:formatDate(d.date)}">
                                                    <span>${st.index eq 6 ? 'CN' : 'T'}${st.index lt 6 ? st.index + 2 : ''}</span>
                                                    <strong>${d.date.dayOfMonth}</strong>
                                                </a>
                                            </c:forEach>
                                        </nav>
                                        <%-- KHỐI 2. DÃY 7 NGÀY — KẾT THÚC --%>

                                        <%-- ==========================================================
                                             KHỐI 3a. BẢNG TUẦN — BẮT ĐẦU
                                             3 hàng Sáng/Chiều/Tối × 7 cột ngày.
                                             timetable.days[cột]  : d.name "Thứ 2", d.date, d.today (Service dựng 7 ngày)
                                             timetable.bands[hàng]: band.name "Sáng" + band.range "06:00 – 12:00" (hằng số BANDS của Service)
                                             band.cells[cột]      : các MyPtSessionRow của ô đó (DAO đọc, Service xếp theo giờ bắt đầu)
                                             Ô trống → "—". data-week-column + is-mobile-day: trên điện thoại chỉ hiện cột mobileDay.
                                             ========================================================== --%>
                                        <div class="schedule-scroll" tabindex="0" role="region" aria-label="Bảng lịch tuần, cuộn ngang được">
                                            <table class="schedule-grid schedule-week">
                                                <caption class="visually-hidden">Lịch từ ${gms:formatDate(timetable.weekStart)} đến ${gms:formatDate(timetable.weekEnd)} · Giờ Việt Nam</caption>
                                                <thead>
                                                    <tr>
                                                        <th scope="col">Khung giờ</th>
                                                        <c:forEach var="d" items="${timetable.days}" varStatus="col">
                                                            <th scope="col" data-week-column="${col.index}"
                                                                class="${d.today ? 'is-today' : ''} ${d.date eq selectedDate ? 'is-selected' : ''} ${col.index eq mobileDay ? 'is-mobile-day' : ''}">
                                                                <%-- Đầu cột: "Thứ 2" / "26/10" đậm / nhãn "Hôm nay" (nền cam) / "Đang chọn" (viền xanh). --%>
                                                                <span><c:out value="${d.name}" /></span>
                                                                <strong>${d.date.dayOfMonth}/${d.date.monthValue}</strong>
                                                                <c:if test="${d.today}"><small class="day-mark is-today-mark">Hôm nay</small></c:if>
                                                                <c:if test="${d.date eq selectedDate}"><small class="day-mark is-selected-mark">Đang chọn</small></c:if>
                                                            </th>
                                                        </c:forEach>
                                                    </tr>
                                                </thead>
                                                <tbody>
                                                    <c:forEach var="band" items="${timetable.bands}">
                                                        <tr>
                                                            <th scope="row"><c:out value="${band.name}" /><small><c:out value="${band.range}" /></small></th>
                                                            <c:forEach var="cell" items="${band.cells}" varStatus="col">
                                                                <c:set var="day" value="${timetable.days[col.index]}" />
                                                                <td data-week-column="${col.index}"
                                                                    class="${day.today ? 'is-today' : ''} ${day.date eq selectedDate ? 'is-selected' : ''} ${col.index eq mobileDay ? 'is-mobile-day' : ''}">
                                                                    <c:if test="${empty cell}"><span class="schedule-vacant" aria-label="Không có buổi">—</span></c:if>
                                                                    <%-- Mỗi buổi 1 thẻ bấm được (_session-card.jspf). --%>
                                                                    <c:forEach var="s" items="${cell}">
                                                                        <%@ include file="_session-card.jspf" %>
                                                                    </c:forEach>
                                                                </td>
                                                            </c:forEach>
                                                        </tr>
                                                    </c:forEach>
                                                </tbody>
                                            </table>
                                        </div>
                                        <%-- KHỐI 3a. BẢNG TUẦN — KẾT THÚC --%>
                                    </c:when>

                                    <c:otherwise>
                                        <%-- ==========================================================
                                             KHỐI 3b. BẢNG THÁNG — BẮT ĐẦU
                                             month.weeks = các hàng tuần, mỗi hàng 7 ô (Cell, Service dựng).
                                             Ô ngoài tháng (cell.inMonth = false) để trống. Bấm ngày / bấm buổi → sang lịch tuần chứa ngày đó.
                                             Mỗi ô: "26/10" + các dòng buổi "18:00  ● Sắp tới" (s.startTime ← TimeSlots, s.statusLabel ← Service).
                                             ========================================================== --%>
                                        <div class="schedule-scroll" tabindex="0" role="region" aria-label="Bảng lịch tháng, cuộn ngang được">
                                            <table class="schedule-grid schedule-month no-gms-table">
                                                <caption class="visually-hidden">Tháng ${month.month.monthValue}/${month.month.year} · Giờ Việt Nam</caption>
                                                <thead>
                                                    <tr>
                                                        <th scope="col">Thứ 2</th><th scope="col">Thứ 3</th><th scope="col">Thứ 4</th><th scope="col">Thứ 5</th>
                                                        <th scope="col">Thứ 6</th><th scope="col">Thứ 7</th><th scope="col">Chủ nhật</th>
                                                    </tr>
                                                </thead>
                                                <tbody>
                                                    <c:forEach var="week" items="${month.weeks}">
                                                        <tr>
                                                            <c:forEach var="cell" items="${week}">
                                                                <c:choose>
                                                                    <c:when test="${not cell.inMonth}"><td class="is-month-padding" aria-hidden="true"></td></c:when>
                                                                    <c:otherwise>
                                                                        <td class="${cell.today ? 'is-today' : ''} ${cell.date eq selectedDate ? 'is-selected' : ''}">
                                                                            <a class="schedule-day" href="${pageUrl}?view=week&amp;date=${cell.date}" aria-label="Xem tuần chứa ngày ${gms:formatDate(cell.date)}">
                                                                                <span class="schedule-day-number">${cell.date.dayOfMonth}/${cell.date.monthValue}</span>
                                                                                <c:if test="${cell.today}"><small class="day-mark is-today-mark">Hôm nay</small></c:if>
                                                                                <c:if test="${cell.date eq selectedDate}"><small class="day-mark is-selected-mark">Đang chọn</small></c:if>
                                                                            </a>
                                                                            <c:forEach var="s" items="${cell.sessions}">
                                                                                <a class="month-session ${s.ended ? 'is-ended' : ''}" href="${pageUrl}?view=week&amp;date=${cell.date}&amp;openSession=${s.sessionId}">
                                                                                    <strong>${gms:formatTime(s.startTime)}</strong>
                                                                                    <span class="session-status" data-status="${s.statusKey}"><c:out value="${s.statusLabel}" /></span>
                                                                                </a>
                                                                            </c:forEach>
                                                                        </td>
                                                                    </c:otherwise>
                                                                </c:choose>
                                                            </c:forEach>
                                                        </tr>
                                                    </c:forEach>
                                                </tbody>
                                            </table>
                                        </div>
                                        <%-- KHỐI 3b. BẢNG THÁNG — KẾT THÚC --%>
                                    </c:otherwise>
                                </c:choose>

                                <%-- ==========================================================
                                     KHỐI 4. CHÚ THÍCH — BẮT ĐẦU
                                     Chữ cố định, cùng data-status với thẻ buổi → cùng màu (session-status.css).
                                     ========================================================== --%>
                                <div class="schedule-legend">
                                    <span class="session-status" data-status="upcoming">Sắp tới</span>
                                    <span class="session-status" data-status="warn">Cần chú ý</span>
                                    <span class="session-status" data-status="checkedIn">Đã check-in</span>
                                    <span class="session-status" data-status="missed">Vắng</span>
                                    <span class="session-status" data-status="cancelled">Đã hủy</span>
                                    <span class="pt-info">
                                        <button type="button" class="pt-info-trigger" aria-label="Giải thích trạng thái buổi" aria-describedby="session-status-help"><span aria-hidden="true">i</span></button>
                                        <span id="session-status-help" class="pt-info-popover" role="tooltip">
                                            <strong>Cần chú ý:</strong> phòng nghỉ hoặc quyền vào gym chưa hợp lệ.<br>
                                            <strong>Đã check-in:</strong> bạn đã vào gym ngày đó. Hệ thống chỉ biết bạn đã vào cửa, không biết bạn có tập cùng PT hay không.<br>
                                            <strong>Đang cập nhật:</strong> buổi đã hết giờ, hệ thống đang chờ chốt kết quả cuối ngày.<br>
                                            Buổi đã kết thúc dùng nền trầm, vẫn bấm xem được.<br>
                                            Hôm nay có nền cam; ngày đang chọn có viền xanh. Hai dấu có thể cùng xuất hiện.
                                        </span>
                                    </span>

                                    <%-- Lịch tháng: số buổi cả tháng theo trạng thái (Service đếm sẵn: month.upcomingCount…),
                                         VD "Tháng 10  3 sắp tới  12 đã check-in  2 vắng  0 đã hủy". --%>
                                    <c:if test="${view eq 'month'}">
                                        <p class="schedule-month-stats">
                                            <span class="schedule-month-stats-title">Tháng ${month.month.monthValue}</span>
                                            <span data-status="upcoming"><b>${month.upcomingCount}</b> sắp tới</span>
                                            <span data-status="checkedIn"><b>${month.checkedInCount}</b> đã check-in</span>
                                            <span data-status="missed"><b>${month.missedCount}</b> vắng</span>
                                            <span data-status="cancelled"><b>${month.cancelledCount}</b> đã hủy</span>
                                            <c:if test="${month.pendingCount gt 0}"><span data-status="pending"><b>${month.pendingCount}</b> đang cập nhật</span></c:if>
                                        </p>
                                    </c:if>
                                </div>
                                <%-- KHỐI 4. CHÚ THÍCH — KẾT THÚC --%>
                            </section>
                            <%-- BẢNG LỊCH — KẾT THÚC --%>
                        </c:otherwise>
                    </c:choose>

                    <%-- ===== KHỐI 5. HỘP CHI TIẾT BUỔI (1 hộp dùng chung cho mọi thẻ, _session-drawer.jspf) ===== --%>
                    <%@ include file="_session-drawer.jspf" %>
                </main>
            </div>
        </div>
    </body>
</html>
