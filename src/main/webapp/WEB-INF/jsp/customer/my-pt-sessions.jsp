<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="gms" uri="/WEB-INF/gms-functions.tld" %>
<%--
  MÀN "LỊCH TẬP CỦA TÔI" — /customer/pt-sessions  (CHỈ XEM)
  *** BẢN TẠM GĐ2: bảng thô để kiểm tra dữ liệu thời khóa biểu. Giao diện thật làm ở GĐ3. ***

  Servlet gửi sang:
    view       : "week" | "month"
    timetable  : (view=week)  .days (7 cột) + .bands (5 hàng) → band.cells[i] (danh sách buổi)
    month      : (view=month) .weeks → week (7 ô) → cell.sessions + các số đếm
    timetableError, dashboard
--%>
<c:set var="pageUrl" value="${pageContext.request.contextPath}/customer/pt-sessions" />

<!DOCTYPE html>
<html lang="vi">
<head>
    <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
    <title>Lịch tập của tôi · Super Gym</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/customer/dashboard.css?v=20261013c">
    <style>
        /* GĐ2 tạm: chỉ đủ để đọc dữ liệu; GĐ3 thay bằng CSS thật */
        .tkb-tmp table { width: 100%; border-collapse: collapse; table-layout: fixed; }
        .tkb-tmp th, .tkb-tmp td { border: 1px solid var(--border, #456); padding: 6px; vertical-align: top; text-align: left; }
        .tkb-tmp .ss { margin-bottom: 4px; padding: 4px 6px; border: 1px solid var(--border, #456); }
        .tkb-tmp .is-today { outline: 2px solid var(--primary, #FF4F2B); }
        .tkb-tmp nav { display: flex; gap: 12px; align-items: center; margin: 12px 0; flex-wrap: wrap; }
    </style>
</head>
<body class="dock my-pt-sessions-page ${themeClass}">
<div class="app">
    <%@ include file="/WEB-INF/jsp/common/layout-customer.jspf" %>
    <div class="main">
        <header class="topbar"><div class="tb-left"><h1 class="tb-title">Lịch tập của tôi</h1></div></header>

        <main class="content tkb-tmp" id="main-content" tabindex="-1">
            <c:if test="${not empty timetableError}"><p role="alert"><c:out value="${timetableError}" /></p></c:if>

            <%-- ===== XEM THEO TUẦN ===== --%>
            <c:if test="${view eq 'week' and not empty timetable}">
                <nav aria-label="Chọn tuần">
                    <a href="${pageUrl}?view=week&amp;date=${timetable.prevWeek}">‹ Tuần trước</a>
                    <b>${gms:formatDate(timetable.weekStart)} – ${gms:formatDate(timetable.weekEnd)}</b>
                    <a href="${pageUrl}?view=week&amp;date=${timetable.nextWeek}">Tuần sau ›</a>
                    <c:if test="${not timetable.currentWeek}"><a href="${pageUrl}?view=week">Tuần này</a></c:if>
                    <a href="${pageUrl}?view=month&amp;date=${timetable.weekStart}">Xem theo tháng</a>
                </nav>
                <table>
                    <thead>
                        <tr>
                            <th scope="col">Khung giờ</th>
                            <c:forEach var="d" items="${timetable.days}">
                                <th scope="col" class="${d.today ? 'is-today' : ''}"><c:out value="${d.name}" /><br>${gms:formatDate(d.date)}</th>
                            </c:forEach>
                        </tr>
                    </thead>
                    <tbody>
                        <c:forEach var="band" items="${timetable.bands}">
                            <tr>
                                <th scope="row"><c:out value="${band.name}" /><br><small><c:out value="${band.range}" /></small></th>
                                <c:forEach var="cell" items="${band.cells}">
                                    <td>
                                        <c:if test="${empty cell}">–</c:if>
                                        <c:forEach var="s" items="${cell}">
                                            <div class="ss">
                                                <b>${gms:formatTime(s.startTime)}–${gms:formatTime(s.endTime)}</b>
                                                [<c:out value="${s.statusKey}" />] <c:out value="${s.statusLabel}" /><br>
                                                PT <c:out value="${s.ptName}" /> · <c:out value="${s.typeLabel}" />
                                                <c:if test="${not s.singleSession}"> · buổi ${s.sessionNo}/${s.totalSessions}</c:if><br>
                                                <c:if test="${not empty s.partnerName}">Tập cùng: <c:out value="${s.partnerName}" /><br></c:if>
                                                <c:if test="${not empty s.explanation}"><small><c:out value="${s.explanation}" /></small><br></c:if>
                                                <c:if test="${not empty s.zaloUrl}"><a href="${fn:escapeXml(s.zaloUrl)}" target="_blank" rel="noopener">Zalo PT</a></c:if>
                                                <c:if test="${not empty s.calendarUrl}"> · <a href="${fn:escapeXml(s.calendarUrl)}" target="_blank" rel="noopener">Thêm vào Google Calendar</a></c:if>
                                            </div>
                                        </c:forEach>
                                    </td>
                                </c:forEach>
                            </tr>
                        </c:forEach>
                    </tbody>
                </table>
            </c:if>

            <%-- ===== XEM THEO THÁNG ===== --%>
            <c:if test="${view eq 'month' and not empty month}">
                <nav aria-label="Chọn tháng">
                    <a href="${pageUrl}?view=month&amp;date=${month.prevMonth}">‹ Tháng trước</a>
                    <b>Tháng ${month.month.monthValue}/${month.month.year}</b>
                    <a href="${pageUrl}?view=month&amp;date=${month.nextMonth}">Tháng sau ›</a>
                    <a href="${pageUrl}?view=week">Xem theo tuần</a>
                </nav>
                <p>Sắp tới ${month.upcomingCount} · Đã check-in ${month.checkedInCount} · Vắng ${month.missedCount}
                    · Đã hủy ${month.cancelledCount} · Đang cập nhật ${month.pendingCount}</p>
                <table>
                    <thead><tr><th>Thứ 2</th><th>Thứ 3</th><th>Thứ 4</th><th>Thứ 5</th><th>Thứ 6</th><th>Thứ 7</th><th>Chủ nhật</th></tr></thead>
                    <tbody>
                        <c:forEach var="week" items="${month.weeks}">
                            <tr>
                                <c:forEach var="cell" items="${week}">
                                    <td class="${cell.today ? 'is-today' : ''}" style="${cell.inMonth ? '' : 'opacity:.45'}">
                                        <a href="${pageUrl}?view=week&amp;date=${cell.date}">${cell.date.dayOfMonth}</a>
                                        <c:forEach var="s" items="${cell.sessions}">
                                            <div class="ss">${gms:formatTime(s.startTime)} <c:out value="${s.statusLabel}" /></div>
                                        </c:forEach>
                                    </td>
                                </c:forEach>
                            </tr>
                        </c:forEach>
                    </tbody>
                </table>
            </c:if>

        </main>
    </div>
</div>
</body>
</html>
