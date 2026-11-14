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
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/pt/schedule.css?v=20261107shift">

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
                              <%-- data-next-key và data-next-url: nút "Chép sang tuần sau" --%>
                              data-next-key="${draftAccountId}:${week.nextWeek}" data-next-url="${nextWeekUrl}">

                            <section class="panel pt-plan-board" aria-labelledby="pt-plan-range">

                                <%-- THANH CÔNG CỤ: chọn tuần, Thêm khung, Gỡ hết, Chép sang tuần sau, Tuần / Danh sách. --%>
                                <%@ include file="/WEB-INF/jsp/pt/_schedule-toolbar.jspf" %>

                                <%-- Mở ?handle= mà buổi không còn xử lý được → câu đỏ (handleFormError ưu tiên nếu có). --%>
                                <c:if test="${not empty handleError}">
                                    <p class="pt-plan-alert" role="alert"><i class="fa-solid fa-circle-exclamation" aria-hidden="true"></i>
                                        <c:out value="${not empty handleFormError ? handleFormError : handleError}" /></p>
                                </c:if>

                                <%-- CHÚ THÍCH MÀU + tiêu đề cột kiểu Danh sách. --%>
                                <%@ include file="/WEB-INF/jsp/pt/_schedule-legend.jspf" %>

                                <p class="visually-hidden" id="pt-plan-status" role="status"></p>

                                <%-- LƯỚI TUẦN: trục giờ + 7 cột ngày + ô bóng khi kéo. --%>
                                <%@ include file="/WEB-INF/jsp/pt/_schedule-grid.jspf" %>
                            </section>

                            <%-- THANH "THAY ĐỔI CHỜ LƯU" + nút Công bố (form pt-publish-form). --%>
                            <%@ include file="/WEB-INF/jsp/pt/_schedule-pending.jspf" %>

                            <%-- MẪU 1 khung nháp (schedule.js nhân bản). --%>
                            <%@ include file="/WEB-INF/jsp/pt/_schedule-draft-template.jspf" %>

                            <%-- HỘP XỬ LÝ BUỔI (chỉ khi có handle). --%>
                            <c:if test="${not empty handle}">
                                <%@ include file="/WEB-INF/jsp/pt/_schedule-handle.jspf" %>
                            </c:if>
                        </main>
                    </c:otherwise>
                </c:choose>
            </div>
        </div>
    </body>
</html>
