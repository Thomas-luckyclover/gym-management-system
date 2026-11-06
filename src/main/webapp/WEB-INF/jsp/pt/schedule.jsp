<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="gms" uri="/WEB-INF/gms-functions.tld" %>
<%--
  MÀN LỊCH DẠY PT — mở bởi PtScheduleServlet.showPage (GET /pt/schedule, hoặc khi Xử lý buổi bị từ chối).
  Dữ liệu (request.setAttribute):
    week          ← PtScheduleServlet.loadWeek → PtScheduleService.getWeek → PtScheduleDAO (PtScheduleWeek)
    scheduleError ← getWeek thất bại → khung lỗi + nút Tải lại
    view          ← loadView: "week" | "list" (class is-list đổi cách vẽ bằng CSS)
    viewDate, today ← loadWeek (ngày đang xem, hôm nay)
    handle / handleError ← loadHandle → PtSessionHandleService.getHandleForm (hộp Xử lý buổi)
    handleFormError, handleAction, handleReason ← handleSession khi Dời / Chuyển / Hủy bị từ chối
    csrfToken, draftAccountId, pageTitle, pageHelp ← setPageInfo
  JS: schedule.js — kéo / bấm chỗ trống thêm khung nháp, tích gỡ khung, Công bố (fetch POST), mở / đóng hộp xử lý.
--%>

<!DOCTYPE html>
<html lang="vi">
    <head>
        <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
        <title>Lịch dạy · Super Gym</title>
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/pt/header.css?v=20261106help">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/pt/schedule.css?v=20261106lane">

        <%-- schedule.js chỉ nạp khi có lịch (week). --%>
        <c:if test="${not empty week}"><script src="${pageContext.request.contextPath}/js/pages/pt/schedule.js?v=20261106toast" defer></script></c:if>
    </head>
    <body class="dock theme-pt pt-plan-page">
        <%@ include file="/WEB-INF/jsp/common/flash-toast.jspf" %>
        <div class="app">

            <%@ include file="/WEB-INF/jsp/common/layout-pt.jspf" %>
            <div class="main">

                <%-- Thanh tiêu đề PT: pageTitle "Lịch dạy" + nút "i" (pageHelp) + ngày hôm nay. --%>
                <%@ include file="/WEB-INF/jsp/pt/_header.jspf" %>

                <c:choose>

                    <%-- LỖI TẢI LỊCH (không phải PT / lỗi DB) → câu lỗi + Tải lại. --%>
                    <c:when test="${not empty scheduleError}">
                        <main class="content pt-plan" id="main-content" tabindex="-1">
                            <section class="panel pt-plan-error" role="alert">
                                <p><c:out value="${scheduleError}" /></p>
                                <a class="btn btn-outline" href="${pageContext.request.contextPath}/pt/schedule">Tải lại</a>
                            </section>
                        </main>
                    </c:when>

                    <c:otherwise>

                        <%-- Các link GET giữ kiểu xem: tuần trước / tuần sau / tuần này / chuyển Tuần ↔ Danh sách. --%>
                        <c:url var="prevWeekUrl" value="/pt/schedule"><c:param name="date" value="${week.prevWeek}" /><c:param name="view" value="${view}" /></c:url>
                        <c:url var="nextWeekUrl" value="/pt/schedule"><c:param name="date" value="${week.nextWeek}" /><c:param name="view" value="${view}" /></c:url>
                        <c:url var="thisWeekUrl" value="/pt/schedule"><c:param name="view" value="${view}" /></c:url>
                        <c:url var="weekViewUrl" value="/pt/schedule"><c:param name="date" value="${viewDate}" /><c:param name="view" value="week" /></c:url>
                        <c:url var="listViewUrl" value="/pt/schedule"><c:param name="date" value="${viewDate}" /><c:param name="view" value="list" /></c:url>

                        <%--
                          canAddAny = có ngày nào còn thêm được khung (day.firstStartMinute) → nút "Thêm khung";
                          canRemoveAny = có khung nào gỡ được (item.canRemove) → nút "Gỡ hết khung trống".
                        --%>
                        <c:set var="canAddAny" value="false" />

                        <c:set var="canRemoveAny" value="false" />
                        <c:forEach var="day" items="${week.days}">
                            <c:if test="${not empty day.firstStartMinute}"><c:set var="canAddAny" value="true" /></c:if>
                            <c:forEach var="item" items="${day.items}">
                                <c:if test="${item.canRemove}"><c:set var="canRemoveAny" value="true" /></c:if>
                            </c:forEach>
                        </c:forEach>

                        <%-- data-* cho schedule.js: thời lượng buổi (week.sessionMinutes), trục giờ (phút), khóa lưu nháp theo PT + tuần, URL tuần sau. --%>
                        <main class="content pt-plan ${view eq 'list' ? 'is-list' : ''}" id="main-content" tabindex="-1"
                              data-duration="${week.sessionMinutes}"
                              data-axis-start="${week.axisStartHour * 60}" data-axis-end="${week.axisEndHour * 60}"
                              data-draft-key="${draftAccountId}:${week.weekStart}"
                              data-next-key="${draftAccountId}:${week.nextWeek}" data-next-url="${nextWeekUrl}">

                            <section class="panel pt-plan-board" aria-labelledby="pt-plan-range">

                                <%-- THANH CÔNG CỤ: chọn tuần, Thêm khung, Gỡ hết, Chép sang tuần sau, Tuần / Danh sách. --%>
                                <%@ include file="/WEB-INF/jsp/pt/_schedule-toolbar.jspf" %>

                                <%-- Mở ?handle= mà buổi không còn xử lý được → câu đỏ (handleFormError ưu tiên nếu có). --%>
                                <c:if test="${not empty handleError}">
                                    <p class="pt-plan-alert" role="alert"><i class="fa-solid fa-circle-exclamation" aria-hidden="true"></i>
                                        <c:out value="${not empty handleFormError ? handleFormError : handleError}" /></p>
                                </c:if>

                                <%-- CHÚ THÍCH MÀU: free · booked · held · locked · draft (nháp JS) · removing (đã tích gỡ). --%>
                                <div class="pt-plan-legend" aria-label="Màu trạng thái">
                                    <%-- title = giải thích khi rê chuột (bản đầy đủ nằm trong nút "i" cạnh tiêu đề). --%>
                                    <span data-kind="free" title="Khung đã công bố, chưa ai đặt. Bấm để đánh dấu gỡ.">Còn trống</span>
                                    <span data-kind="booked" title="Đã có buổi với khách. Bấm buổi sắp tới để Dời / Chuyển PT / Hủy.">Có khách</span>
                                    <span data-kind="held" title="Một đơn mua gói PT đang giữ khung này chờ thanh toán. Trả tiền → Có khách; hết hạn / hủy đơn → Còn trống.">Đang giữ</span>
                                    <span data-kind="locked" title="Khung trùng ca bạn xin nghỉ (chờ duyệt hoặc đã duyệt). Khách không đặt được.">Tạm khóa</span>
                                    <span data-kind="draft" title="Khung vừa thêm, chưa lưu. Bấm Công bố để khách thấy, bấm × để bỏ.">Nháp mới</span>
                                    <span data-kind="removing" title="Khung trống bạn đã đánh dấu gỡ. Bấm Công bố để gỡ thật.">Chờ gỡ</span>
                                </div>
                                <div class="pt-list-columns" aria-hidden="true">
                                    <span>Giờ tập</span><span>Buổi / khung giờ</span><span>Trạng thái</span><span>Thao tác</span>
                                </div>

                                <p class="visually-hidden" id="pt-plan-status" role="status"></p>

                                <div class="pt-plan-scroll">

                                    <%-- LƯỚI TUẦN: --axis-hours = số hàng giờ · --shift-split = vị trí vạch chia Ca sáng / Ca chiều (13:00). --%>
                                    <%-- week.shiftSplitHour = getter tự tính getShiftSplitHour() · dto/PtScheduleWeek.java --%>
                                    <div class="pt-plan-grid" style="--axis-hours: ${week.axisEndHour - week.axisStartHour}; --shift-split: ${week.shiftSplitHour - week.axisStartHour}">

                                        <div class="pt-plan-axis-head" aria-hidden="true"></div>

                                        <%-- TRỤC GIỜ: week.axisStartHour → week.axisEndHour − 1. --%>
                                        <div class="pt-plan-axis" aria-hidden="true">
                                            <c:forEach var="hour" begin="${week.axisStartHour}" end="${week.axisEndHour - 1}">
                                                <span><c:out value="${hour lt 10 ? '0' += hour : hour}" />:00</span>
                                            </c:forEach>

                                            <b class="pt-plan-shift is-am">Ca sáng</b>
                                            <b class="pt-plan-shift is-pm">Ca chiều</b>
                                        </div>

                                        <%-- 7 CỘT NGÀY: mỗi PtScheduleDay → _schedule-day.jspf (biến day, dayLabel = "Thứ 2 03/11"). --%>
                                        <c:forEach var="day" items="${week.days}">
                                            <c:set var="dayLabel" value="${day.dayName} ${fn:substring(gms:formatDate(day.date), 0, 5)}" />

                                            <%@ include file="/WEB-INF/jsp/pt/_schedule-day.jspf" %>

                                        </c:forEach>
                                    </div>
                                </div>

                                <%-- Ô "bóng" schedule.js dùng khi rê chuột / kéo để thêm khung. --%>
                                <div class="pt-ghost" id="pt-ghost" aria-hidden="true" hidden>
                                    <span class="pt-slot-time"></span>
                                    <span class="pt-ghost-hint"></span>
                                </div>
                            </section>

                            <%-- THANH "THAY ĐỔI CHỜ LƯU" + nút Công bố (form pt-publish-form). --%>
                            <%@ include file="/WEB-INF/jsp/pt/_schedule-pending.jspf" %>

                            <%-- MẪU 1 khung nháp (schedule.js nhân bản). --%>
                            <%@ include file="/WEB-INF/jsp/pt/_schedule-draft-template.jspf" %>

                            <%-- HỘP XỬ LÝ BUỔI (chỉ khi có handle). --%>
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
