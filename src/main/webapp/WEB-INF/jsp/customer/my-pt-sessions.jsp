<%--
================================================================================
 my-pt-sessions.jsp — BẢN HỌC (comment kỹ). Code y hệt bản gốc, chỉ thêm comment.
 Đường dẫn gốc: src/main/webapp/WEB-INF/jsp/customer/my-pt-sessions.jsp

 LUỒNG: trình duyệt GET /customer/pt-sessions?view=...&date=...
        → MyPtSessionServlet.doGet → loadHeader + loadTimetable
        → MyPtSessionService.getWeek / getMonth → MyPtSessionDAO (SQL)
        → Servlet setAttribute → forward tới FILE NÀY → JSP in HTML.

 CÁC ATTRIBUTE SERVLET GỬI SANG (đọc bằng ${tên}):
   dashboard        CustomerDashboardView  tên, mã khách ở header       (loadHeader :57)
   view             String "week"|"month"  đang xem tuần hay tháng      (loadTimetable :78)
   viewDate         LocalDate              ngày đang xem (URL hoặc hôm nay) (:71)
   selectedDate     LocalDate | null       chỉ có khi URL có ?date      (:73)
   timetable        TimetableView          CHỈ khi view = week          (:85)
   month            MonthView              CHỈ khi view = month         (:85)
   timetableError   String                 lỗi MSG04/MSG07 nếu Service hỏng (:87)
   activeSidebar, themeClass               menu trái + class CSS         (:48-49)

 BIẾN KHÔNG DO SERVLET GỬI (JSP tự tạo):
   pageUrl, calendarDate      do c:set ngay dưới
   d, band, cell, week, s     do c:forEach var="..." tạo khi lặp

 QUY TẮC EL: ${a.b} = a.getB() ; boolean thì a.isB() ; ${list[i]} = list.get(i)
================================================================================ --%>

<%-- Khai báo trang: trả HTML, mã UTF-8 để in tiếng Việt. --%>
<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>

<%-- 3 thư viện thẻ:
     c   = JSTL core: c:if, c:choose, c:forEach, c:set, c:out
     fn  = JSTL functions: fn:escapeXml (chống XSS) — dùng trong file con
     gms = hàm tự viết của nhóm (DateTimeUtil, khai báo ở WEB-INF/gms-functions.tld):
           gms:formatDate(LocalDate) → "22/10/2026", gms:formatTime(LocalTime) → "15:00"
           (JSTL fmt không định dạng được LocalDate nên phải tự viết) --%>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="gms" uri="/WEB-INF/gms-functions.tld" %>
<%-- Dữ liệu MyPtSessionServlet đặt vào request: view (week/month), viewDate/selectedDate (LocalDate),
     timetable (TimetableView) hoặc month (MonthView) do Service dựng; timetableError là lỗi tải lịch. --%>

<%-- pageUrl = "/gym-management-system/customer/pt-sessions". Mọi link trên trang ghép thêm ?view=&date= vào đây.
     contextPath = tên app trên Tomcat, không ghi cứng để đổi tên app vẫn chạy. --%>
<c:set var="pageUrl" value="${pageContext.request.contextPath}/customer/pt-sessions" />

<%-- calendarDate = viewDate. Đặt tên riêng để dùng cho ô Chọn ngày và 2 nút Tuần/Tháng
     (giữ đúng ngày khi đổi cách xem). --%>
<c:set var="calendarDate" value="${viewDate}" />
<!DOCTYPE html>
<html lang="vi">
    <head>
        <%-- Phần head dùng chung (meta, font, CSS chung). Include tĩnh = chép nguyên file vào đây lúc dịch JSP. --%>
        <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
        <title>Lịch tập · Super Gym</title>
        <%-- CSS riêng trang. ?v=... để trình duyệt tải bản mới khi file đổi (chống cache). --%>
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/customer/dashboard.css?v=20261013c">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/customer/my-pt-sessions.css?v=20261021a">

        <%-- JS riêng trang: mở dialog chi tiết, sao chép SĐT, chọn ngày trên mobile.
             defer = chạy SAU khi HTML tải xong (JS cần các thẻ đã có trên trang). --%>
        <script src="${pageContext.request.contextPath}/js/pages/customer/my-pt-sessions.js?v=20261019e" defer>
        </script>

        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/auth/notification-bell.css">
        <script src="${pageContext.request.contextPath}/js/pages/auth/notification-bell.js" defer></script>
    </head>
    <%-- themeClass do Servlet đặt (:49): class CSS chọn giao diện trang khách. --%>
    <body class="dock ${themeClass}">
        <%-- Thông báo nổi (toast) dùng chung toàn hệ thống. --%>
        <%@ include file="/WEB-INF/jsp/common/flash-toast.jspf" %>
        <div class="app">
            <%-- ===== Menu bên trái (activeSidebar: Servlet chọn mục Lịch tập đang mở) ===== --%>
            <%@ include file="/WEB-INF/jsp/common/layout-customer.jspf" %>
            <%-- ===== KẾT THÚC menu bên trái ===== --%>
            <div class="main">
                <%-- ===== Thanh đầu trang: tiêu đề, hint, nút Đặt buổi lẻ, theme, tài khoản (dashboard) ===== --%>
                <%-- scheduleTitle: biến truyền cho file con _schedule-header.jspf in tiêu đề "Lịch tập".
                     File con đó đọc ${dashboard.fullName}, ${dashboard.customerCode}, ${dashboard.initials}. --%>
                <c:set var="scheduleTitle" value="Lịch tập" />
                <%@ include file="_schedule-header.jspf" %>
                <%-- ===== KẾT THÚC thanh đầu trang ===== --%>
                <%-- data-focus-session: buổi mở sẵn chi tiết khi đến từ "Gói của tôi" (initFocusSession). --%>
                <main class="content pt-schedule" id="main-content" tabindex="-1" data-focus-session="${focusSessionId}">
                    <%-- ===== Vùng nội dung: lỗi tải lịch khác với lịch hợp lệ nhưng không có buổi ===== --%>
                    <%-- c:choose = if / else. Có lỗi → hộp lỗi; không lỗi → bảng lịch. --%>
                    <c:choose>
                        <c:when test="${not empty timetableError}">
                            <%-- NHÁNH LỖI: Service trả ServiceResult.fail (MSG07 không tìm thấy khách / MSG04 lỗi SQL).
                                 Không vẽ lịch rỗng để khách không hiểu nhầm là "không có buổi nào". --%>
                            <div class="schedule-empty" role="alert">
                                <h3>Chưa tải được lịch tập</h3>
                                <p>
                                    <%-- c:out tự escape ký tự HTML → chống XSS. --%>
                                    <c:out value="${timetableError}" />
                                </p>
                                <%-- Tải lại = GET trang không tham số → tuần hiện tại. --%>
                                <a class="btn btn-outline" href="${pageUrl}">Tải lại</a>
                            </div>
                            <%-- KẾT THÚC thông báo lỗi tải lịch. --%>
                        </c:when>
                        <c:otherwise>
                            <%-- NHÁNH THÀNH CÔNG: vẽ thanh công cụ + bảng lịch. --%>
                            <section class="schedule-board" aria-label="Lịch tập">
                                <%-- ===== Thanh công cụ lịch: điều hướng kỳ, chọn ngày, chuyển Tuần/Tháng ===== --%>
                                <div class="schedule-toolbar">
                                    <%-- ===== Điều hướng trái: kỳ trước/sau, tên kỳ, Hôm nay (timetable hoặc month) ===== --%>
                                    <nav class="schedule-period" aria-label="Đổi khoảng thời gian">
                                        <%-- NÚT ‹ (kỳ trước). Giữ nguyên view, chỉ đổi date:
                                             view=month → date = month.prevMonth  = MonthView.getPrevMonth()  (ngày 1 tháng trước)
                                             view=week  → date = timetable.prevWeek = TimetableView.getPrevWeek() (Thứ 2 tuần trước)
                                             Toán tử 3 ngôi ? : chọn theo view. &amp; trong HTML = dấu & của URL.
                                             aria-label: chữ ẩn cho trình đọc màn hình, không hiện ra. --%>
                                        <a class="btn btn-outline" aria-label="${view eq 'month' ? 'Tháng trước' : 'Tuần trước'}" href="${pageUrl}?view=${view}&amp;date=${view eq 'month' ? month.prevMonth : timetable.prevWeek}">
                                            <i class="fa-solid fa-chevron-left" aria-hidden="true">
                                            </i>
                                        </a>
                                        <%-- TIÊU ĐỀ KỲ:
                                             tháng: "Tháng 10/2026" — month.month là YearMonth, .monthValue/.year là getter của Java
                                             tuần : "19/10/2026 – 25/10/2026" — weekStart/weekEnd do getWeek tính --%>
                                        <h3>
                                            <c:choose>
                                                <c:when test="${view eq 'month'}">Tháng ${month.month.monthValue}/${month.month.year}</c:when>
                                                <c:otherwise>${gms:formatDate(timetable.weekStart)} – ${gms:formatDate(timetable.weekEnd)}</c:otherwise>
                                            </c:choose>
                                        </h3>
                                        <%-- NÚT › (kỳ sau): giống nút ‹, dùng nextMonth / nextWeek. --%>
                                        <a class="btn btn-outline" aria-label="${view eq 'month' ? 'Tháng sau' : 'Tuần sau'}" href="${pageUrl}?view=${view}&amp;date=${view eq 'month' ? month.nextMonth : timetable.nextWeek}">
                                            <i class="fa-solid fa-chevron-right" aria-hidden="true">
                                            </i>
                                        </a>
                                        <%-- NÚT HÔM NAY: chỉ gửi view, KHÔNG gửi date
                                             → Servlet parseViewDate(null) trả hôm nay → về kỳ hiện tại, vẫn giữ Tuần/Tháng.
                                             Không có date nên selectedDate = null → không cột nào có viền "Đang chọn". --%>
                                        <a class="btn btn-ghost" href="${pageUrl}?view=${view}">Hôm nay</a>
                                    </nav>
                                    <%-- ===== KẾT THÚC điều hướng kỳ trước/sau và Hôm nay ===== --%>
                                    <div class="schedule-controls">
                                        <%-- ===== Chọn ngày: GET view + date về Servlet; #schedule-date được selectScheduleDay cập nhật trên mobile ===== --%>
                                        <%-- details/summary: bấm "Chọn ngày" thì xổ form ra (HTML thuần, không cần JS). --%>
                                        <details class="schedule-date-picker">
                                            <summary class="btn btn-outline"><i class="fa-regular fa-calendar" aria-hidden="true"></i> Chọn ngày</summary>
                                            <%-- FORM GET: bấm Xem → URL ?view=...&date=yyyy-mm-dd (tên ô = tên tham số). --%>
                                            <form method="get" action="${pageUrl}" class="schedule-date">
                                            <%-- Ô ẨN mang view hiện tại → chọn ngày xong VẪN GIỮ Tuần hay Tháng. --%>
                                            <input type="hidden" name="view" value="${view}">
                                            <label for="schedule-date">Ngày muốn xem</label>
                                            <%-- Ô ngày: giá trị sẵn = ngày đang xem. min/max khớp parseViewDate (năm 1754–9998,
                                                 giới hạn kiểu DATETIME của SQL Server). required = bắt buộc chọn. --%>
                                            <input id="schedule-date" name="date" type="date" value="${calendarDate}" min="1754-01-01" max="9998-12-31" required>
                                            <button class="btn btn-outline" type="submit">Xem</button>
                                            </form>
                                        </details>
                                        <%-- ===== KẾT THÚC khối Chọn ngày ===== --%>
                                        <%-- Chuyển Tuần/Tháng giữ đúng viewDate; selectScheduleDay cập nhật date trong hai link này. --%>
                                        <%-- 2 NÚT TUẦN / THÁNG: đổi view, GIỮ date.
                                             aria-current="page" cho nút đang chọn → CSS (my-pt-sessions.css :53) gạch chân cam.
                                             Nút còn lại aria-current="false" → không tô. --%>
                                        <nav class="schedule-switch" aria-label="Cách xem lịch">
                                            <a href="${pageUrl}?view=week&amp;date=${calendarDate}" aria-current="${view eq 'week' ? 'page' : 'false'}">Tuần</a>
                                            <a href="${pageUrl}?view=month&amp;date=${calendarDate}" aria-current="${view eq 'month' ? 'page' : 'false'}">Tháng</a>
                                        </nav>
                                        <%-- KẾT THÚC nút chuyển Tuần/Tháng. --%>
                                    </div>
                                </div>
                                <%-- ===== KẾT THÚC thanh công cụ lịch ===== --%>

                                <%-- ===== Dãy nút ngày mobile (timetable.days): initScheduleDays khởi tạo, selectScheduleDay đổi cột, không truy vấn lại ===== --%>
                                <%-- CHỈ có ở lịch tuần. Mặc định hidden; JS bỏ hidden khi chạy (không có JS thì không hiện).
                                     Màn hẹp chỉ hiện 1 cột ngày, bấm nút → JS ẩn/hiện cột, KHÔNG gọi server.
                                     data-*: dữ liệu JSP gửi cho JS, JS đọc bằng button.dataset.dayDate ... --%>
                                <c:if test="${view eq 'week'}">
                                    <div class="schedule-mobile-days" role="group" aria-label="Chọn ngày trong tuần" data-selected-date="${selectedDate}" hidden>
                                        <%-- d = 1 TimetableView.Day (name, date, today). dayIndex.index = 0..6 (varStatus). --%>
                                        <c:forEach var="d" items="${timetable.days}" varStatus="dayIndex">
                                            <button type="button" data-day-index="${dayIndex.index}" data-day-date="${d.date}" data-today="${d.today}" class="${d.today ? 'is-today' : ''} ${d.date eq selectedDate ? 'is-selected' : ''}" aria-pressed="false">
                                                <%-- Nhãn ngắn: index 6 → "CN", còn lại "T" + (index+2) → T2..T7. --%>
                                                <span>${dayIndex.index eq 6 ? 'CN' : 'T'}<c:if test="${dayIndex.index lt 6}">${dayIndex.index + 2}</c:if>
                                                </span>
                                                <strong>${d.date.dayOfMonth}</strong>
                                            </button>
                                        </c:forEach>
                                    </div>
                                </c:if>
                                <%-- ===== KẾT THÚC dãy nút ngày mobile ===== --%>

                                <div class="schedule-scroll" tabindex="0" role="region" aria-label="Bảng lịch tập, có thể cuộn ngang">
                                    <%-- view quyết định vẽ bảng TUẦN hay bảng THÁNG (chỉ 1 trong 2 có dữ liệu). --%>
                                    <c:choose>
                                        <c:when test="${view eq 'week'}">
                                            <%-- ===== Lịch tuần (TimetableView): 3 hàng Sáng/Chiều/Tối × 7 ngày; mỗi ô là List<MyPtSessionRow> ===== --%>
                                            <%-- CẤU TRÚC DỮ LIỆU:
                                                 timetable.days  = List<Day>  7 ngày (tiêu đề cột)
                                                 timetable.bands = List<Band> 3 hàng Sáng/Chiều/Tối
                                                 band.cells      = List<List<MyPtSessionRow>> 7 ô, mỗi ô là list buổi
                                                 → Service getWeek đã xếp buổi vào [hàng][cột] = [bandOf(giờ)][thứ-1]. --%>
                                            <table class="schedule-grid schedule-week">
                                                <%-- caption ẩn: tên bảng cho trình đọc màn hình. --%>
                                                <caption class="visually-hidden">Lịch từ ${gms:formatDate(timetable.weekStart)} đến ${gms:formatDate(timetable.weekEnd)} · Giờ Việt Nam</caption>
                                                <thead>
                                                    <%-- Tên thứ/ngày: today từ DTO tô hôm nay; selectedDate tô ngày URL chọn.
                                                         data-week-column nối nút mobile với cả tiêu đề và ô buổi bên dưới. --%>
                                                    <tr>
                                                        <th scope="col">Khung giờ</th>
                                                        <%-- 7 ô tiêu đề cột. columnIndex.index = 0..6 = số cột. --%>
                                                        <c:forEach var="d" items="${timetable.days}" varStatus="columnIndex">
                                                            <%-- class is-today: d.today (Service so date với hôm nay) → nền cam.
                                                                 class is-selected: d.date == selectedDate (URL có ?date) → viền "Đang chọn". --%>
                                                            <th scope="col" data-week-column="${columnIndex.index}" class="${d.today ? 'is-today' : ''} ${d.date eq selectedDate ? 'is-selected' : ''}" data-day-date="${d.date}">
                                                                <span>
                                                                    <%-- d.name = "Thứ 2"... (mảng DAY_NAMES trong Service) --%>
                                                                    <c:out value="${d.name}" />
                                                                </span>
                                                                <%-- "20/10": getDayOfMonth() / getMonthValue() của LocalDate --%>
                                                                <strong>${d.date.dayOfMonth}/${d.date.monthValue}</strong>
                                                                <c:if test="${d.today}">
                                                                    <small class="day-today-label">Hôm nay</small>
                                                                </c:if>
                                                                <%-- Luôn in nhưng CSS ẩn; chỉ hiện khi th có class is-selected. --%>
                                                                <small class="day-selected-label">Đang chọn</small>
                                                            </th>
                                                        </c:forEach>
                                                    </tr>
                                                </thead>
                                                <tbody>
                                                    <%-- Service xếp buổi theo giờ bắt đầu vào band; JSP chỉ lặp khung và từng ngày. --%>
                                                    <%-- VÒNG 1: band = 1 hàng (Sáng/Chiều/Tối). --%>
                                                    <c:forEach var="band" items="${timetable.bands}">
                                                        <tr>
                                                            <%-- Ô đầu hàng: "Sáng" + "06:00 – 12:00" (hằng BANDS trong Service). --%>
                                                            <th scope="row">
                                                                <c:out value="${band.name}" />
                                                                <small>
                                                                    <c:out value="${band.range}" />
                                                                </small>
                                                            </th>
                                                            <%-- VÒNG 2: cell = 1 ô = List<MyPtSessionRow> (KHÔNG phải object Cell như lịch tháng).
                                                                 column.index = cột 0..6, dùng để lấy ngày tương ứng timetable.days[column.index]. --%>
                                                            <c:forEach var="cell" items="${band.cells}" varStatus="column">
                                                                <td data-week-column="${column.index}" class="${timetable.days[column.index].today ? 'is-today' : ''} ${timetable.days[column.index].date eq selectedDate ? 'is-selected' : ''}">
                                                                    <%-- Ô không có buổi → in dấu "—". --%>
                                                                    <c:if test="${empty cell}">
                                                                        <span class="schedule-vacant" aria-label="Không có buổi">—</span>
                                                                    </c:if>
                                                                    <%-- Mỗi buổi s trong ô → một thẻ bấm mở chi tiết; fragment đọc trực tiếp biến s. --%>
                                                                    <%-- VÒNG 3: s = 1 MyPtSessionRow (1 buổi tập).
                                                                         _session-card.jspf in thẻ (giờ, PT, loại gói, trạng thái)
                                                                         + chi tiết ẩn (_session-detail.jspf). Bấm thẻ → JS chép chi tiết vào dialog. --%>
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
                                            <%-- CẤU TRÚC DỮ LIỆU:
                                                 month.weeks   = List<List<Cell>>  mỗi phần tử = 1 hàng tuần gồm 7 Cell
                                                 Cell          = 1 ô ngày: date, inMonth, today, sessions (List<MyPtSessionRow>)
                                                 Lưới từ Thứ 2 tuần đầu → CN tuần cuối, nên có ô đệm của tháng trước/sau. --%>
                                            <table class="schedule-grid schedule-month no-gms-table">
                                                <caption class="visually-hidden">Tháng ${month.month.monthValue}/${month.month.year} · Giờ Việt Nam</caption>
                                                <thead>
                                                    <%-- Tiêu đề tháng ghi cứng Thứ 2 → CN (không cần dữ liệu). --%>
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
                                                    <%-- VÒNG 1: week = 1 hàng tuần (List<Cell> gồm 7 ô). --%>
                                                    <c:forEach var="week" items="${month.weeks}">
                                                        <tr>
                                                            <%-- VÒNG 2: cell = 1 object MonthView.Cell. --%>
                                                            <c:forEach var="cell" items="${week}">
                                                                <c:choose>
                                                                    <c:when test="${not cell.inMonth}">
                                                                        <%-- Ô thuộc tháng trước/sau chỉ để căn đúng thứ; không hiện ngày hoặc buổi. --%>
                                                                        <%-- inMonth = false (Service: YearMonth.from(date) khác tháng đang xem)
                                                                             → ô trống. Lớp chặn thứ 3 (SQL chỉ lấy ngày 1→cuối tháng, Service gắn inMonth). --%>
                                                                        <td class="is-month-padding" aria-hidden="true"></td>
                                                                    </c:when>
                                                                    <c:otherwise>
                                                                        <%-- Hai dấu độc lập: hôm nay và ngày chọn; link ngày gửi GET sang tuần chứa ngày đó. --%>
                                                                        <td data-day-date="${cell.date}" class="${cell.today ? 'is-today' : ''} ${cell.date eq selectedDate ? 'is-selected' : ''}">
                                                                            <%-- LINK SỐ NGÀY: bấm → ?view=week&date=ngày đó
                                                                                 → Servlet gọi getWeek → mở CẢ TUẦN chứa ngày đó (lùi về Thứ 2),
                                                                                   cột ngày đó có viền "Đang chọn" vì URL có date.
                                                                                 Không có JS, chỉ là thẻ a thường. aria-label ẩn, không hiện ra. --%>
                                                                            <a class="schedule-day" href="${pageUrl}?view=week&amp;date=${cell.date}" aria-label="Xem tuần chứa ngày ${gms:formatDate(cell.date)}">
                                                                                <%-- "22/10": cell.getDate().getDayOfMonth() / getMonthValue() --%>
                                                                                <span class="schedule-day-number">${cell.date.dayOfMonth}/${cell.date.monthValue}</span>
                                                                                <span class="schedule-day-labels">
                                                                                    <c:if test="${cell.today}"><small class="day-today-label">Hôm nay</small></c:if>
                                                                                    <c:if test="${cell.date eq selectedDate}"><small class="day-selected-label">Đang chọn</small></c:if>
                                                                                </span>
                                                                            </a>
                                                                            <%-- Thẻ gọn trong tháng chỉ hiện giờ/trạng thái; bấm sang lịch tuần để xem chi tiết. --%>
                                                                            <%-- VÒNG 3: s = 1 buổi trong ô (Service đã thả vào bằng cellByDate).
                                                                                 Lịch tháng KHÔNG có dialog chi tiết; bấm → sang tuần như link ngày. --%>
                                                                            <c:forEach var="s" items="${cell.sessions}">
                                                                                <%-- s.ended (applyStatus) → class is-ended → nền trầm. --%>
                                                                                <a class="month-session ${s.ended ? 'is-ended' : ''}" href="${pageUrl}?view=week&amp;date=${cell.date}">
                                                                                    <strong>${gms:formatTime(s.startTime)}</strong>
                                                                                    <%-- data-status = statusKey (upcoming/warn/today/checkedIn/missed/cancelled/pending)
                                                                                         → CSS chọn màu; statusLabel = chữ ("Sắp tới"...). Cả 2 do applyStatus gán. --%>
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
                                <%-- Legend ghi cứng 5 màu, không lấy dữ liệu. Cùng data-status nên cùng màu với thẻ buổi. --%>
                                <div class="schedule-legend" aria-label="Chú thích trạng thái">
                                    <span class="session-status" data-status="upcoming">Sắp tới</span>
                                    <span class="session-status" data-status="warn">Cần chú ý</span>
                                    <span class="session-status" data-status="checkedIn">Đã check-in</span>
                                    <span class="session-status" data-status="missed">Vắng</span>
                                    <span class="session-status" data-status="cancelled">Đã hủy</span>
                                    <%-- Nút "i": rê/bấm hiện popover giải thích (JS chung common.js). --%>
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
                                    <%-- SỐ BUỔI TRONG THÁNG: chỉ ở lịch tháng, nằm bên phải chú thích.
                                         countInto (Service) cộng theo statusKey: today/upcoming/warn → upcomingCount;
                                         checkedIn, missed, cancelled → đếm riêng; còn lại (pending) → pendingCount. Không dùng COUNT SQL. --%>
                                    <c:if test="${view eq 'month'}">
                                        <p class="schedule-month-stats">
                                            <span class="schedule-month-stats-title">Tháng ${month.month.monthValue}</span>
                                            <span data-status="upcoming"><b>${month.upcomingCount}</b> sắp tới</span>
                                            <span data-status="checkedIn"><b>${month.checkedInCount}</b> đã check-in</span>
                                            <span data-status="missed"><b>${month.missedCount}</b> vắng</span>
                                            <span data-status="cancelled"><b>${month.cancelledCount}</b> đã hủy</span>
                                            <c:if test="${month.pendingCount gt 0}">
                                                <span data-status="pending"><b>${month.pendingCount}</b> đang cập nhật</span>
                                            </c:if>
                                        </p>
                                    </c:if>
                                </div>
                                <%-- ===== KẾT THÚC chú thích trạng thái cuối lịch ===== --%>
                            </section>
                        </c:otherwise>
                    </c:choose>
                    <%-- ===== KẾT THÚC vùng nội dung lỗi hoặc lịch hợp lệ ===== --%>
                    <%-- ===== Một dialog dùng chung cho mọi thẻ: openSessionDetail chép nội dung buổi vào đây ===== --%>
                    <%-- _session-drawer.jspf = 1 thẻ dialog RỖNG duy nhất (#session-drawer-content).
                         Bấm thẻ buổi → JS cloneNode khối chi tiết ẩn trong thẻ → đặt vào đây → showModal().
                         Không AJAX, không tải lại trang. --%>
                    <%@ include file="_session-drawer.jspf" %>
                    <%-- ===== KẾT THÚC khung chi tiết dùng chung ===== --%>
                </main>
            </div>
        </div>
    </body>
</html>
