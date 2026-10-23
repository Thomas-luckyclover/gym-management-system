<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="gms" uri="/WEB-INF/gms-functions.tld" %>
<%--
    Servlet gửi week (PtScheduleWeek), today, viewDate, view hoặc scheduleError.
    week.days chứa 7 PtScheduleDay; day.items chứa PtScheduleItem do DAO/Service chuẩn bị.
    Lịch tuần / Danh sách dùng cùng HTML; class is-list đổi cách xếp bằng CSS Grid.
--%>
<!DOCTYPE html>
<html lang="vi">
    <head>
        <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
        <title>Lịch dạy · Super Gym</title>
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/pt/schedule.css?v=20261023f">
        <c:if test="${not empty week}"><script src="${pageContext.request.contextPath}/js/pages/pt/schedule.js?v=20261023f" defer></script></c:if>
    </head>
    <body class="dock theme-pt pt-schedule-page">
        <%@ include file="/WEB-INF/jsp/common/flash-toast.jspf" %>
        <div class="app">
            <%@ include file="/WEB-INF/jsp/common/layout-pt.jspf" %>
            <div class="main">
                <%@ include file="/WEB-INF/jsp/pt/_header.jspf" %>
                <main class="content pt-schedule-frame" id="main-content" tabindex="-1">
                    <%-- ==================== B1: URL giữ view khi đổi tuần, giữ date khi đổi view ==================== --%>
                    <c:url var="prevWeekUrl" value="/pt/schedule">
                        <c:param name="date" value="${week.prevWeek}" />
                        <c:param name="view" value="${view}" />
                    </c:url>
                    <c:url var="nextWeekUrl" value="/pt/schedule">
                        <c:param name="date" value="${week.nextWeek}" />
                        <c:param name="view" value="${view}" />
                    </c:url>
                    <c:url var="currentWeekUrl" value="/pt/schedule">
                        <c:param name="view" value="${view}" />
                    </c:url>
                    <c:url var="weekViewUrl" value="/pt/schedule">
                        <c:param name="date" value="${viewDate}" />
                        <c:param name="view" value="week" />
                    </c:url>
                    <c:url var="listViewUrl" value="/pt/schedule">
                        <c:param name="date" value="${viewDate}" />
                        <c:param name="view" value="list" />
                    </c:url>

                    <%-- ==================== B2: tải lỗi hiện riêng; chỉ render lịch/form khi có week ==================== --%>
                    <c:choose>
                        <c:when test="${not empty scheduleError}">
                            <section class="pt-frame-notice" role="alert">
                                <p><c:out value="${scheduleError}" /></p>
                                <a class="btn btn-ghost btn-sm" href="<c:out value='${currentWeekUrl}' />">Tải lại tuần này</a>
                            </section>
                        </c:when>
                        <c:otherwise>
                            <section class="pt-schedule-toolbar" aria-label="Điều hướng lịch dạy">
                                <nav class="pt-week-nav" aria-label="Chọn tuần">
                                    <a class="btn btn-ghost" href="<c:out value='${prevWeekUrl}' />" aria-label="Xem tuần trước">‹</a>
                                    <h2><c:out value="${fn:substring(gms:formatDate(week.weekStart), 0, 5)}" /> – <c:out value="${gms:formatDate(week.weekEnd)}" /></h2>
                                    <a class="btn btn-ghost" href="<c:out value='${nextWeekUrl}' />" aria-label="Xem tuần sau">›</a>
                                    <c:if test="${not week.currentWeek}">
                                        <a class="btn btn-ghost btn-sm" href="<c:out value='${currentWeekUrl}' />">Tuần này</a>
                                    </c:if>
                                </nav>
                                <nav class="pt-view-nav" aria-label="Kiểu xem lịch">
                                    <a href="<c:out value='${weekViewUrl}' />" aria-current="${view eq 'week' ? 'page' : 'false'}">Lịch tuần</a>
                                    <a href="<c:out value='${listViewUrl}' />" aria-current="${view eq 'list' ? 'page' : 'false'}">Danh sách</a>
                                </nav>
                            </section>
                            <p class="pt-frame-notice">Kéo hoặc nhập giờ để thêm nháp. Chỉ sau khi Công bố thành công khách mới thấy các khung này.</p>

                            <%-- ==================== B3: cùng khung 7 ngày; CSS đổi lưới tuần thành danh sách ==================== --%>
                            <div class="pt-schedule-workspace">
                                <%-- Nhập tay là cách thay thế kéo thả; details native không cần JS mở/đóng. --%>
                                <details class="pt-schedule-tools">
                                    <summary>Nhập ngày và giờ <span>Thay cho kéo chọn trên lịch</span></summary>
                                    <form id="pt-preview-form">
                                        <fieldset disabled>
                                            <legend>Thêm khung vào danh sách nháp</legend>
                                            <label>Ngày<input type="date" name="slotDate" value="<c:out value='${viewDate}' />" min="<c:out value='${week.weekStart}' />" max="<c:out value='${week.weekEnd}' />" required></label>
                                            <div class="pt-time-fields">
                                                <label>Bắt đầu<input type="time" name="startTime" step="900" required></label>
                                                <label>Kết thúc<input type="time" name="endTime" readonly></label>
                                            </div>
                                            <button class="btn btn-outline" type="submit">Thêm vào nháp</button>
                                            <small><c:out value="${week.sessionMinutes}" /> phút mỗi khung · Giờ bắt đầu theo mốc 15 phút, trong giờ mở cửa.</small>
                                        </fieldset>
                                    </form>
                                </details>
                                <%-- Trục chung từ Service; data-open/close ở từng ngày giữ đúng giờ ngoại lệ. --%>
                                <section id="pt-schedule" class="pt-schedule-board ${view eq 'list' ? 'is-list' : ''}" aria-labelledby="schedule-view-title"
                                         data-csrf="<c:out value='${csrfToken}' />" data-draft-key="${draftAccountId}:${week.weekStart}"
                                         data-duration="<c:out value='${week.sessionMinutes}' />"
                                         data-axis-start="${week.axisStartHour * 60}" data-axis-end="${week.axisEndHour * 60}" data-today="<c:out value='${today}' />"
                                         style="--pt-axis-minutes: ${60 * (week.axisEndHour - week.axisStartHour)}">
                                    <div class="pt-board-heading">
                                        <h2 id="schedule-view-title"><c:out value="${view eq 'list' ? 'Danh sách theo ngày' : 'Lịch tuần'}" /></h2>
                                        <ul class="pt-schedule-legend" aria-label="Các loại khối trên lịch">
                                            <li class="is-free">Trống</li><li class="is-held">Đang giữ</li><li class="is-booked">Có khách</li><li class="is-locked">Tạm khóa</li>
                                        </ul>
                                    </div>
                                    <p class="pt-week-help" id="pt-preview-status" role="status"><c:out value="${view eq 'list' ? 'Mở Nhập ngày và giờ để thêm nháp; kiểm tra danh sách trước khi công bố.' : 'Kéo trên vùng trống, thả để thêm nháp. Escape để hủy kéo.'}" /></p>
                                    <div class="pt-calendar-scroll" tabindex="0" aria-label="Khung lịch dạy theo ngày">
                                        <div class="pt-calendar">
                                            <%-- Trục giờ bao hết giờ mở cửa và các buổi lịch sử của tuần. --%>
                                            <div class="pt-time-axis" aria-hidden="true">
                                                <span>Giờ</span>
                                                <c:forEach var="hour" begin="${week.axisStartHour}" end="${week.axisEndHour - 1}">
                                                    <span><c:out value="${hour lt 10 ? '0' += hour : hour}" />:00</span>
                                                </c:forEach>
                                            </div>
                                            <c:forEach var="day" items="${week.days}">
                                                <section class="pt-calendar-day ${day.today ? 'is-today' : ''} ${day.closedAllDay ? 'is-closed' : ''}">
                                                    <h3 class="pt-day-heading">
                                                        <span><c:out value="${day.dayName}" /> · <c:out value="${fn:substring(gms:formatDate(day.date), 0, 5)}" /></span>
                                                        <c:choose>
                                                            <c:when test="${day.closedAllDay}"><small class="pt-day-closed">Phòng nghỉ cả ngày</small></c:when>
                                                            <c:when test="${day.today}"><small>Hôm nay</small></c:when>
                                                        </c:choose>
                                                    </h3>
                                                    <div class="pt-day-track" data-date="<c:out value='${day.date}' />" data-closed="${day.closedAllDay}"
                                                         data-open="${day.closedAllDay ? '' : day.openTime.toSecondOfDay() / 60}"
                                                         data-close="${day.closedAllDay ? '' : day.closeTime.toSecondOfDay() / 60}">
                                                        <div class="pt-hour-lines" aria-hidden="true">
                                                            <c:forEach begin="${week.axisStartHour}" end="${week.axisEndHour - 1}"><span></span></c:forEach>
                                                        </div>
                                                        <%-- Lớp riêng để preview không sinh cột Grid phụ hoặc làm co thẻ thật. --%>
                                                        <div class="pt-preview-layer"></div>
                                                        <%-- Nhãn đơn nghỉ; nhãn phòng nghỉ đã có dòng riêng dưới ngày. --%>
                                                        <div class="pt-day-notes">
                                                            <c:if test="${not empty day.leaveLabel}"><p><c:out value="${day.leaveLabel}" /></p></c:if>
                                                        </div>
                                                        <c:if test="${empty day.items}"><p class="pt-day-placeholder">Chưa có buổi hoặc khung giờ</p></c:if>
                                                        <%-- B3.1: Service đã tính kind, vị trí và độ dài; JSP chỉ in từng khối. --%>
                                                        <c:forEach var="item" items="${day.items}">
                                                            <article class="pt-slot-frame is-<c:out value='${item.kind}' /> ${item.past ? 'is-past' : ''}"
                                                                     data-session-id="<c:out value='${item.sessionId}' />" data-slot-id="${item.timeSlotId}"
                                                                     title="<c:out value='${item.customerNames}' /> · <c:out value='${item.packageName}' /> · <c:out value='${item.statusLabel}' />"
                                                                     style="--pt-slot-row: ${item.startMinute + 1}; --pt-slot-duration: ${item.lengthMinutes}">
                                                                <p class="pt-slot-time"><c:out value="${gms:formatTime(item.startTime)}" /> – <c:out value="${gms:formatTime(item.endTime)}" /></p>
                                                                <c:if test="${item.booked}">
                                                                    <div class="pt-slot-person">
                                                                        <strong><c:out value="${item.customerNames}" /></strong>
                                                                        <small class="pt-slot-detail"><c:out value="${item.packageName}" /> · Buổi <c:out value="${item.sessionNo}" />/<c:out value="${item.totalSessions}" /></small>
                                                                    </div>
                                                                </c:if>
                                                                <span class="pt-slot-state"><c:out value="${item.statusLabel}" /></span>
                                                                <%-- B3.2: quyền đã có nhưng thao tác ghi chưa nối; không cho bấm nút giả. --%>
                                                                <c:if test="${item.canHandle}"><button type="button" class="btn btn-outline btn-sm pt-slot-action" disabled title="Xử lý buổi sẽ được nối ở bước sau">Xử lý buổi</button></c:if>
                                                                <c:if test="${item.canRemove}"><button type="button" class="btn btn-ghost btn-sm pt-slot-action" disabled title="Gỡ khung sẽ được nối ở bước sau">Gỡ khung</button></c:if>
                                                            </article>
                                                        </c:forEach>
                                                    </div>
                                                </section>
                                            </c:forEach>
                                        </div>
                                    </div>
                                    <%-- JS chỉ sao chép khung này; giờ được gán bằng textContent, không dùng innerHTML. --%>
                                    <template id="pt-preview-template">
                                        <article class="pt-slot-preview"><strong data-preview-time></strong><span>Nháp · chưa công bố</span></article>
                                    </template>
                                </section>

                            </div>

                            <%-- ==================== B5: JS vẽ nháp bằng textContent; một POST công bố cả danh sách ==================== --%>
                            <section class="pt-pending-frame" aria-label="Khung thay đổi chờ công bố">
                                <div><h2>Chờ công bố</h2><p id="pt-preview-summary">Chưa có nháp. Kéo trên lịch hoặc mở Nhập ngày và giờ.</p></div>
                                <div class="pt-pending-actions">
                                    <button class="btn btn-ghost" type="button" id="pt-clear-preview" disabled>Bỏ tất cả nháp</button>
                                    <button class="btn btn-primary" type="button" id="pt-publish" disabled>Công bố</button>
                                </div>
                                <ol id="pt-draft-list" class="pt-draft-list" aria-label="Các khung nháp"></ol>
                                <p id="pt-publish-error" class="pt-publish-error" role="alert"></p>
                            </section>
                        </c:otherwise>
                    </c:choose>
                </main>
            </div>
        </div>
    </body>
</html>
