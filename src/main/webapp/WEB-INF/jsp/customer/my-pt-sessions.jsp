<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="gms" uri="/WEB-INF/gms-functions.tld" %>
<%-- Dữ liệu MyPtSessionServlet đặt vào request: view (week/month), viewDate/selectedDate (LocalDate),
     timetable (TimetableView) hoặc month (MonthView) do Service dựng; timetableError là lỗi tải lịch. --%>
<c:set var="pageUrl" value="${pageContext.request.contextPath}/customer/pt-sessions" />
<c:set var="calendarDate" value="${viewDate}" />
<!DOCTYPE html>
<html lang="vi">
    <head>
        <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
        <title>Lịch tập · Super Gym</title>
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/customer/dashboard.css?v=20261013c">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/customer/my-pt-sessions.css?v=20261019f">

        <script src="${pageContext.request.contextPath}/js/pages/customer/my-pt-sessions.js?v=20261019e" defer>
        </script>

        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/auth/notification-bell.css">
        <script src="${pageContext.request.contextPath}/js/pages/auth/notification-bell.js" defer></script>
    </head>
    <body class="dock ${themeClass}">
        <%@ include file="/WEB-INF/jsp/common/flash-toast.jspf" %>
        <div class="app">
            <%-- ===== Menu bên trái (activeSidebar: Servlet chọn mục Lịch tập đang mở) ===== --%>
            <%@ include file="/WEB-INF/jsp/common/layout-customer.jspf" %>
            <%-- ===== KẾT THÚC menu bên trái ===== --%>
            <div class="main">
                <%-- ===== Thanh đầu trang: tiêu đề, hint, nút Đặt buổi lẻ, theme, tài khoản (dashboard) ===== --%>
                <c:set var="scheduleTitle" value="Lịch tập" />
                <%@ include file="_schedule-header.jspf" %>
                <%-- ===== KẾT THÚC thanh đầu trang ===== --%>
                <%-- data-focus-session: buổi mở sẵn chi tiết khi đến từ "Gói của tôi" (initFocusSession). --%>
                <main class="content pt-schedule" id="main-content" tabindex="-1" data-focus-session="${focusSessionId}">
                    <%-- ===== Vùng nội dung: lỗi tải lịch khác với lịch hợp lệ nhưng không có buổi ===== --%>
                    <c:choose>
                        <c:when test="${not empty timetableError}">
                            <%-- Service thất bại → thông báo lỗi và link GET tải lại; không hiện lịch rỗng giả. --%>
                            <div class="schedule-empty" role="alert">
                                <h3>Chưa tải được lịch tập</h3>
                                <p>
                                    <c:out value="${timetableError}" />
                                </p>
                                <a class="btn btn-outline" href="${pageUrl}">Tải lại</a>
                            </div>
                            <%-- KẾT THÚC thông báo lỗi tải lịch. --%>
                        </c:when>
                        <c:otherwise>
                            <section class="schedule-board" aria-label="Lịch tập">
                                <%-- ===== Thanh công cụ lịch: điều hướng kỳ, chọn ngày, chuyển Tuần/Tháng ===== --%>
                                <div class="schedule-toolbar">
                                    <%-- ===== Điều hướng trái: kỳ trước/sau, tên kỳ, Hôm nay (timetable hoặc month) ===== --%>
                                    <nav class="schedule-period" aria-label="Đổi khoảng thời gian">
                                        <a class="btn btn-outline" aria-label="${view eq 'month' ? 'Tháng trước' : 'Tuần trước'}" href="${pageUrl}?view=${view}&amp;date=${view eq 'month' ? month.prevMonth : timetable.prevWeek}">
                                            <i class="fa-solid fa-chevron-left" aria-hidden="true">
                                            </i>
                                        </a>
                                        <h3>
                                            <c:choose>
                                                <c:when test="${view eq 'month'}">Tháng ${month.month.monthValue}/${month.month.year}</c:when>
                                                <c:otherwise>${gms:formatDate(timetable.weekStart)} – ${gms:formatDate(timetable.weekEnd)}</c:otherwise>
                                            </c:choose>
                                        </h3>
                                        <a class="btn btn-outline" aria-label="${view eq 'month' ? 'Tháng sau' : 'Tuần sau'}" href="${pageUrl}?view=${view}&amp;date=${view eq 'month' ? month.nextMonth : timetable.nextWeek}">
                                            <i class="fa-solid fa-chevron-right" aria-hidden="true">
                                            </i>
                                        </a>
                                        <a class="btn btn-ghost" href="${pageUrl}?view=${view}">Hôm nay</a>
                                    </nav>
                                    <%-- ===== KẾT THÚC điều hướng kỳ trước/sau và Hôm nay ===== --%>
                                    <div class="schedule-controls">
                                        <%-- ===== Chọn ngày: GET view + date về Servlet; #schedule-date được selectScheduleDay cập nhật trên mobile ===== --%>
                                        <details class="schedule-date-picker">
                                            <summary class="btn btn-outline"><i class="fa-regular fa-calendar" aria-hidden="true"></i> Chọn ngày</summary>
                                            <form method="get" action="${pageUrl}" class="schedule-date">
                                            <input type="hidden" name="view" value="${view}">
                                            <label for="schedule-date">Ngày muốn xem</label>
                                            <input id="schedule-date" name="date" type="date" value="${calendarDate}" min="1754-01-01" max="9998-12-31" required>
                                            <button class="btn btn-outline" type="submit">Xem</button>
                                            </form>
                                        </details>
                                        <%-- ===== KẾT THÚC khối Chọn ngày ===== --%>
                                        <%-- Chuyển Tuần/Tháng giữ đúng viewDate; selectScheduleDay cập nhật date trong hai link này. --%>
                                        <nav class="schedule-switch" aria-label="Cách xem lịch">
                                            <a href="${pageUrl}?view=week&amp;date=${calendarDate}" aria-current="${view eq 'week' ? 'page' : 'false'}">Tuần</a>
                                            <a href="${pageUrl}?view=month&amp;date=${calendarDate}" aria-current="${view eq 'month' ? 'page' : 'false'}">Tháng</a>
                                        </nav>
                                        <%-- KẾT THÚC nút chuyển Tuần/Tháng. --%>
                                    </div>
                                </div>
                                <%-- ===== KẾT THÚC thanh công cụ lịch ===== --%>
                                <%-- ===== Dãy nút ngày mobile (timetable.days): initScheduleDays khởi tạo, selectScheduleDay đổi cột, không truy vấn lại ===== --%>
                                <c:if test="${view eq 'week'}">
                                    <div class="schedule-mobile-days" role="group" aria-label="Chọn ngày trong tuần" data-selected-date="${selectedDate}" hidden>
                                        <c:forEach var="d" items="${timetable.days}" varStatus="dayIndex">
                                            <button type="button" data-day-index="${dayIndex.index}" data-day-date="${d.date}" data-today="${d.today}" class="${d.today ? 'is-today' : ''} ${d.date eq selectedDate ? 'is-selected' : ''}" aria-pressed="false">
                                                <span>${dayIndex.index eq 6 ? 'CN' : 'T'}<c:if test="${dayIndex.index lt 6}">${dayIndex.index + 2}</c:if>
                                                </span>
                                                <strong>${d.date.dayOfMonth}</strong>
                                            </button>
                                        </c:forEach>
                                    </div>
                                </c:if>
                                <%-- ===== KẾT THÚC dãy nút ngày mobile ===== --%>
                                <div class="schedule-scroll" tabindex="0" role="region" aria-label="Bảng lịch tập, có thể cuộn ngang">
                                    <c:choose>
                                        <c:when test="${view eq 'week'}">
                                            <%-- ===== Lịch tuần (TimetableView): 3 hàng Sáng/Chiều/Tối × 7 ngày; mỗi ô là List<MyPtSessionRow> ===== --%>
                                            <table class="schedule-grid schedule-week">
                                                <caption class="visually-hidden">Lịch từ ${gms:formatDate(timetable.weekStart)} đến ${gms:formatDate(timetable.weekEnd)} · Giờ Việt Nam</caption>
                                                <thead>
                                                    <%-- Tên thứ/ngày: today từ DTO tô hôm nay; selectedDate tô ngày URL chọn.
                                                         data-week-column nối nút mobile với cả tiêu đề và ô buổi bên dưới. --%>
                                                    <tr>
                                                        <th scope="col">Khung giờ</th>
                                                        <c:forEach var="d" items="${timetable.days}" varStatus="columnIndex">
                                                            <th scope="col" data-week-column="${columnIndex.index}" class="${d.today ? 'is-today' : ''} ${d.date eq selectedDate ? 'is-selected' : ''}" data-day-date="${d.date}">
                                                                <span>
                                                                    <c:out value="${d.name}" />
                                                                </span>
                                                                <strong>${d.date.dayOfMonth}/${d.date.monthValue}</strong>
                                                                <c:if test="${d.today}">
                                                                    <small class="day-today-label">Hôm nay</small>
                                                                </c:if>
                                                                <small class="day-selected-label">Đang chọn</small>
                                                            </th>
                                                        </c:forEach>
                                                    </tr>
                                                </thead>
                                                <tbody>
                                                    <%-- Service xếp buổi theo giờ bắt đầu vào band; JSP chỉ lặp khung và từng ngày. --%>
                                                    <c:forEach var="band" items="${timetable.bands}">
                                                        <tr>
                                                            <th scope="row">
                                                                <c:out value="${band.name}" />
                                                                <small>
                                                                    <c:out value="${band.range}" />
                                                                </small>
                                                            </th>
                                                            <c:forEach var="cell" items="${band.cells}" varStatus="column">
                                                                <td data-week-column="${column.index}" class="${timetable.days[column.index].today ? 'is-today' : ''} ${timetable.days[column.index].date eq selectedDate ? 'is-selected' : ''}">
                                                                    <c:if test="${empty cell}">
                                                                        <span class="schedule-vacant" aria-label="Không có buổi">—</span>
                                                                    </c:if>
                                                                    <%-- Mỗi buổi s trong ô → một thẻ bấm mở chi tiết; fragment đọc trực tiếp biến s. --%>
                                                                    <c:forEach var="s" items="${cell}">
                                                                        <%@ include file="_session-card.jspf" %>
                                                                    </c:forEach>
                                                                </td>
                                                            </c:forEach>
                                                        </tr>
                                                    </c:forEach>
                                                </tbody>
                                            </table>
                                            <%-- ===== KẾT THÚC bảng lịch tuần ===== --%>
                                        </c:when>
                                        <c:otherwise>
                                            <%-- ===== Lịch tháng (MonthView.weeks): từng tuần chứa 7 Cell; chỉ hiện ngày của đúng tháng ===== --%>
                                            <table class="schedule-grid schedule-month no-gms-table">
                                                <caption class="visually-hidden">Tháng ${month.month.monthValue}/${month.month.year} · Giờ Việt Nam</caption>
                                                <thead>
                                                    <tr>
                                                        <th scope="col">Thứ 2</th>
                                                        <th scope="col">Thứ 3</th>
                                                        <th scope="col">Thứ 4</th>
                                                        <th scope="col">Thứ 5</th>
                                                        <th scope="col">Thứ 6</th>
                                                        <th scope="col">Thứ 7</th>
                                                        <th scope="col">Chủ nhật</th>
                                                    </tr>
                                                </thead>
                                                <tbody>
                                                    <c:forEach var="week" items="${month.weeks}">
                                                        <tr>
                                                            <c:forEach var="cell" items="${week}">
                                                                <c:choose>
                                                                    <c:when test="${not cell.inMonth}">
                                                                        <%-- Ô thuộc tháng trước/sau chỉ để căn đúng thứ; không hiện ngày hoặc buổi. --%>
                                                                        <td class="is-month-padding" aria-hidden="true"></td>
                                                                    </c:when>
                                                                    <c:otherwise>
                                                                        <%-- Hai dấu độc lập: hôm nay và ngày chọn; link ngày gửi GET sang tuần chứa ngày đó. --%>
                                                                        <td data-day-date="${cell.date}" class="${cell.today ? 'is-today' : ''} ${cell.date eq selectedDate ? 'is-selected' : ''}">
                                                                            <a class="schedule-day" href="${pageUrl}?view=week&amp;date=${cell.date}" aria-label="Xem tuần chứa ngày ${gms:formatDate(cell.date)}">
                                                                                <span class="schedule-day-number">${cell.date.dayOfMonth}/${cell.date.monthValue}</span>
                                                                                <span class="schedule-day-labels">
                                                                                    <c:if test="${cell.today}"><small class="day-today-label">Hôm nay</small></c:if>
                                                                                    <c:if test="${cell.date eq selectedDate}"><small class="day-selected-label">Đang chọn</small></c:if>
                                                                                </span>
                                                                            </a>
                                                                            <%-- Thẻ gọn trong tháng chỉ hiện giờ/trạng thái; bấm sang lịch tuần để xem chi tiết. --%>
                                                                            <c:forEach var="s" items="${cell.sessions}">
                                                                                <a class="month-session ${s.ended ? 'is-ended' : ''}" href="${pageUrl}?view=week&amp;date=${cell.date}">
                                                                                    <strong>${gms:formatTime(s.startTime)}</strong>
                                                                                    <span class="session-status" data-status="${s.statusKey}">
                                                                                        <c:out value="${s.statusLabel}" />
                                                                                    </span>
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
                                            <%-- ===== KẾT THÚC bảng lịch tháng ===== --%>
                                        </c:otherwise>
                                    </c:choose>
                                </div>
                                <%-- ===== Chú thích cuối lịch: statusKey do Service chọn; hint dùng .pt-info của common.js ===== --%>
                                <div class="schedule-legend" aria-label="Chú thích trạng thái">
                                    <span class="session-status" data-status="upcoming">Sắp tới</span>
                                    <span class="session-status" data-status="warn">Cần chú ý</span>
                                    <span class="session-status" data-status="checkedIn">Đã check-in</span>
                                    <span class="session-status" data-status="missed">Vắng</span>
                                    <span class="session-status" data-status="cancelled">Đã hủy</span>
                                    <span class="pt-info">
                                        <button type="button" class="pt-info-trigger" aria-label="Giải thích trạng thái buổi" aria-describedby="session-status-help"><span aria-hidden="true">i</span></button>
                                        <span id="session-status-help" class="pt-info-popover" role="tooltip">
                                            <strong>Cần chú ý:</strong> phòng nghỉ hoặc quyền vào gym chưa hợp lệ.<br>
                                            <strong>Đã check-in:</strong> ghi nhận vào gym, không xác nhận đã tập xong với PT.<br>
                                            <strong>Đang cập nhật:</strong> buổi đã hết giờ, hệ thống đang chờ chốt kết quả cuối ngày.<br>
                                            Buổi đã kết thúc dùng nền trầm, vẫn bấm xem được.<br>
                                            Hôm nay có nền cam; ngày đang chọn có viền xanh. Hai dấu có thể cùng xuất hiện.
                                        </span>
                                    </span>
                                </div>
                                <%-- ===== KẾT THÚC chú thích trạng thái cuối lịch ===== --%>
                            </section>
                            <c:if test="${view eq 'month'}">
                                <%-- Tổng hợp của đúng tháng: countInto trong Service đã đếm, JSP chỉ xuất số. --%>
                                <p class="schedule-note">Trong tháng: ${month.upcomingCount} sắp tới · ${month.checkedInCount} đã check-in · ${month.missedCount} vắng · ${month.cancelledCount} đã hủy · ${month.pendingCount} đang cập nhật.</p>
                            </c:if>
                            <%-- KẾT THÚC tổng hợp số buổi trong tháng. --%>
                        </c:otherwise>
                    </c:choose>
                    <%-- ===== KẾT THÚC vùng nội dung lỗi hoặc lịch hợp lệ ===== --%>
                    <%-- ===== Một dialog dùng chung cho mọi thẻ: openSessionDetail chép nội dung buổi vào đây ===== --%>
                    <%@ include file="_session-drawer.jspf" %>
                    <%-- ===== KẾT THÚC khung chi tiết dùng chung ===== --%>
                </main>
            </div>
        </div>
    </body>
</html>
