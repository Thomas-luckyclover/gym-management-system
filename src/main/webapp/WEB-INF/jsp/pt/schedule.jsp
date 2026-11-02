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

 BỐ CỤC — mỗi khối lớn là 1 file riêng cùng thư mục pt/:
   1. Panel lịch: _schedule-toolbar.jspf (‹ tuần ›, Thêm khung, Tuần/Danh sách) → chú thích màu
                  → lưới 7 ngày, mỗi cột ngày = _schedule-day.jspf
   2. _schedule-pending.jspf         thanh "Chờ công bố" dính đáy: CSS tự hiện khi có nháp hoặc khung chờ gỡ
   3. _schedule-draft-template.jspf  <template> khung nháp: JS sao chép khi PT thêm khung
   4. _handle-dialog.jspf            hộp "Xử lý buổi": có khi URL có ?handle=<mã buổi>; biến "handle" ← PtSessionHandleService

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
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/pt/schedule.css?v=20261031shift">
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


                                <%@ include file="/WEB-INF/jsp/pt/_schedule-toolbar.jspf" %>


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
                                     --axis-hours = số giờ trên trục, VD 22 − 5 = 17 hàng giờ.
                                     --shift-split = số giờ từ đầu trục tới mốc ca chiều (week.shiftSplitHour = 13), VD 13 − 5 = 8 → CSS vẽ vạch "Ca chiều". --%>
                                <div class="pt-plan-scroll">
                                    <div class="pt-plan-grid" style="--axis-hours: ${week.axisEndHour - week.axisStartHour}; --shift-split: ${week.shiftSplitHour - week.axisStartHour}">

                                        <%-- Cột trục giờ bên trái: "05:00", "06:00", … (thêm số 0 trước giờ < 10). --%>
                                        <div class="pt-plan-axis-head" aria-hidden="true"></div>
                                        <div class="pt-plan-axis" aria-hidden="true">
                                            <c:forEach var="hour" begin="${week.axisStartHour}" end="${week.axisEndHour - 1}">
                                                <span><c:out value="${hour lt 10 ? '0' += hour : hour}" />:00</span>
                                            </c:forEach>
                                            <%-- Nhãn ca trên trục giờ (chỉ hiện ở lưới tuần): "Sáng" ở đầu trục, "Chiều" ngay dưới vạch 13:00. --%>
                                            <b class="pt-plan-shift is-am">Ca sáng</b>
                                            <b class="pt-plan-shift is-pm">Ca chiều</b>
                                        </div>

                                        <%-- Mỗi ngày 1 cột. --%>
                                        <c:forEach var="day" items="${week.days}">
                                            <c:set var="dayLabel" value="${day.dayName} ${fn:substring(gms:formatDate(day.date), 0, 5)}" />

                                            <%@ include file="/WEB-INF/jsp/pt/_schedule-day.jspf" %>

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

                            <%@ include file="/WEB-INF/jsp/pt/_schedule-pending.jspf" %>


                            <%@ include file="/WEB-INF/jsp/pt/_schedule-draft-template.jspf" %>


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
