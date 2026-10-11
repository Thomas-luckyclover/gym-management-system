<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="gms" uri="/WEB-INF/gms-functions.tld" %>
<%--
    PtDashboardServlet truyền từng khối riêng: scheduleDays, attentionItems,
    assignedPackages, commissionSummary, ptRating và weather. Mỗi khối có
    attribute *Error riêng; JSP không coi lỗi tải dữ liệu là danh sách rỗng.
--%>
<!DOCTYPE html>
<html lang="vi">
<head>
    <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
    <title><c:out value="${pageTitle}" /> · GMS</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/auth/account-settings.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/base.css?v=4">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/awning.css?v=2">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/weather.css?v=2">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/pt/dashboard.css?v=26">
    <script src="${pageContext.request.contextPath}/js/pages/pt/dashboard.js?v=6" defer></script>
</head>
<%-- weatherType do Servlet kiểm tra: sunny/cloudy/rain/storm/snow/wind/night/hot. --%>
<body class="dock theme-pt pt-dashboard-page weather-${weatherType}">
    <%@ include file="/WEB-INF/jsp/common/flash-toast.jspf" %>
    <%@ include file="/WEB-INF/jsp/pt/_awning.jspf" %>

    <div class="app">
        <%@ include file="/WEB-INF/jsp/common/layout-pt.jspf" %>
        <div class="main">
            <%@ include file="/WEB-INF/jsp/pt/_header.jspf" %>

            <main class="content pt-dashboard">
                <%-- Lời chào lấy tên từ session; weather do WeatherService tạo. API lỗi thì ẩn thẻ. --%>
                <section class="pt-welcome ${empty weather ? 'is-weather-unavailable' : ''}" aria-labelledby="welcome-title">
                    <span class="pt-welcome__ambient" aria-hidden="true"></span>
                    <div class="pt-welcome__copy">
                        <h2 id="welcome-title">Chào, <span class="pt-welcome__name"><c:out value="${sessionScope.displayName}" default="bạn" /></span></h2>
                    </div>
                    <c:if test="${not empty weather}">
                        <div class="pt-weather" aria-label="Thời tiết hôm nay tại phòng gym">
                            <div class="pt-weather__content">
                                <div class="pt-weather__eyebrow">
                                    <span><c:out value="${weather.cityName}" /> · <c:out value="${weather.title}" /></span>
                                    <c:if test="${weatherPreview}"><span class="pt-weather__preview">Xem trước</span></c:if>
                                </div>
                                <p class="pt-weather__condition">Hiện tại: <c:out value="${weather.conditionLabel}" /></p>
                            </div>
                            <%-- Giá trị thật giữ trong HTML; dashboard.js chỉ tạo hiệu ứng đếm, tắt JS vẫn thấy nhiệt độ. --%>
                            <strong class="pt-weather__temperature" data-weather-temperature="${weather.temperature}"><c:out value="${weather.temperature}" />°</strong>
                            <%-- Các số dưới đây đi từ WeatherForecast qua WeatherService sang WeatherView; mã WMO chỉ dùng để chọn câu chữ. --%>
                            <c:if test="${weather.feelsLikeTemperature != null}">
                                <dl class="pt-weather__details">
                                    <div><dt>Cảm giác</dt><dd><c:out value="${weather.feelsLikeTemperature}" />°</dd></div>
                                    <div><dt>Thấp / cao</dt><dd><c:out value="${weather.minTemperature}" />° / <c:out value="${weather.maxTemperature}" />°</dd></div>
                                    <div><dt>Độ ẩm</dt><dd><c:out value="${weather.humidityPercent}" />%</dd></div>
                                    <div><dt>Gió</dt><dd><c:out value="${weather.windSpeedKmh}" /> km/h</dd></div>
                                    <div><dt>Khả năng mưa</dt><dd><c:out value="${weather.rainProbabilityPercent}" />%</dd></div>
                                </dl>
                            </c:if>
                            <p class="pt-weather__advice"><c:out value="${weather.advice}" /></p>
                        </div>
                    </c:if>
                </section>

                <%-- attentionItems: Service gom khách + gói + loại vấn đề, giữ buổi gần nhất. --%>
                <section class="pt-alerts" aria-labelledby="alerts-title">
                    <div class="pt-section-heading">
                        <h2 id="alerts-title">Cần chú ý</h2>
                        <span class="pt-info">
                            <button type="button" class="pt-info-trigger" aria-label="Giải thích mục Cần chú ý" aria-describedby="alerts-help"><span aria-hidden="true">i</span></button>
                            <span id="alerts-help" class="pt-info-popover" role="tooltip">Kiểm tra membership hoặc bảo lưu cho các buổi trong 14 ngày tới.</span>
                        </span>
                    </div>
                    <c:choose>
                        <c:when test="${not empty attentionError}">
                            <p class="pt-block-error" role="alert"><c:out value="${attentionError}" /></p>
                        </c:when>
                        <c:when test="${attentionItems == null}">
                            <p class="empty-state">Chưa tải được cảnh báo.</p>
                        </c:when>
                        <c:when test="${empty attentionItems}">
                            <p class="empty-state">Không có khách cần kiểm tra cho các buổi trong 14 ngày tới.</p>
                        </c:when>
                        <c:otherwise>
                            <div class="pt-attention-list">
                                <c:forEach var="item" items="${attentionItems}">
                                    <article class="pt-attention-item">
                                        <div class="pt-attention-heading">
                                            <%-- item.customerName copy từ DAO; attentionTitle do Service chọn. --%>
                                            <span class="pt-person-avatar" aria-hidden="true"><c:out value="${fn:substring(item.customerName, 0, 1)}" /></span>
                                            <h3><c:out value="${item.customerName}" /></h3>
                                            <span class="badge badge-warning"><c:out value="${item.attentionTitle}" /></span>
                                        </div>
                                        <p class="pt-attention-message"><c:out value="${item.attentionMessage}" /></p>
                                        <div class="pt-attention-package">
                                            <p class="pt-attention-group-title">Gói PT · <strong><c:out value="${item.ptSubscriptionTypeLabel}" /></strong></p>
                                            <p>Gói PT đến <c:out value="${gms:formatDate(item.ptEndDate)}" /></p>
                                            <p><c:out value="${item.scheduledSessionCount}" /> buổi đã xếp lịch</p>
                                        </div>
                                        <div class="pt-attention-membership">
                                            <p class="pt-attention-group-title">Membership · <strong><c:out value="${item.membershipStatusLabel}" /></strong></p>
                                            <c:if test="${not empty item.membershipEndDate}">
                                                <p>Hạn membership: <c:out value="${gms:formatDate(item.membershipEndDate)}" /></p>
                                            </c:if>
                                            <c:if test="${not empty item.freezeEndDate}">
                                                <p>Bảo lưu đến <c:out value="${gms:formatDate(item.freezeEndDate)}" /></p>
                                            </c:if>
                                        </div>
                                    </article>
                                </c:forEach>
                            </div>
                        </c:otherwise>
                    </c:choose>
                </section>

                <%-- commissionSummary: DAO cộng số/tiền đã lưu, Service tính năm tỷ lệ. --%>
                <section class="pt-summary" aria-labelledby="summary-title">
                    <div class="pt-section-heading">
                        <h2 id="summary-title">Kết quả tháng và hoa hồng</h2>
                        <span class="pt-info">
                            <button type="button" class="pt-info-trigger" aria-label="Giải thích kết quả tháng" aria-describedby="summary-help"><span aria-hidden="true">i</span></button>
                            <span id="summary-help" class="pt-info-popover" role="tooltip">Số liệu theo ngày tập trong tháng này. Tỷ lệ tính trên các buổi đã có kết quả, kể cả buổi hủy.</span>
                        </span>
                    </div>
                    <c:choose>
                        <c:when test="${not empty commissionSummaryError}">
                            <p class="pt-block-error" role="alert"><c:out value="${commissionSummaryError}" /></p>
                        </c:when>
                        <c:when test="${commissionSummary == null}">
                            <p class="empty-state">Chưa tải được kết quả tháng.</p>
                        </c:when>
                        <c:otherwise>
                            <c:if test="${commissionSummary.commissionRate != null}">
                                <p class="pt-commission-rate">Tỷ lệ hoa hồng hiện tại: <strong><c:out value="${commissionSummary.commissionRate}" />%</strong></p>
                            </c:if>
                            <div class="pt-summary-group pt-summary-finalized">
                                <div class="pt-summary-group-heading">
                                    <div><h3>Đã chốt</h3><p><c:out value="${commissionSummary.resolvedSessionCount}" /> buổi đã có kết quả</p></div>
                                    <div class="pt-summary-money"><span>Hoa hồng đã chốt</span><strong><c:out value="${gms:formatMoney(commissionSummary.finalizedCommissionAmount)}" /></strong></div>
                                </div>
                                <c:choose>
                                    <c:when test="${commissionSummary.resolvedSessionCount == 0}">
                                        <p class="pt-summary-note">Chưa có buổi nào được chốt trong tháng này.</p>
                                    </c:when>
                                    <c:otherwise>
                                        <%-- Mỗi tỷ lệ có cùng mẫu số resolvedSessionCount, gồm cả buổi hủy. --%>
                                        <dl class="pt-summary-stats">
                                            <div class="pt-summary-stat"><dt>Có check-in</dt><dd><strong><c:out value="${commissionSummary.checkedInCount}" /> buổi</strong><span><fmt:formatNumber value="${commissionSummary.checkedInRatePercent}" maxFractionDigits="2" />%</span></dd></div>
                                            <div class="pt-summary-stat"><dt>Không check-in</dt><dd><strong><c:out value="${commissionSummary.noCheckInCount}" /> buổi</strong><span><fmt:formatNumber value="${commissionSummary.noCheckInRatePercent}" maxFractionDigits="2" />%</span></dd></div>
                                            <div class="pt-summary-stat"><dt>Thiếu quyền vào gym</dt><dd><strong><c:out value="${commissionSummary.noMembershipCount}" /> buổi</strong><span><fmt:formatNumber value="${commissionSummary.noMembershipRatePercent}" maxFractionDigits="2" />%</span></dd></div>
                                            <div class="pt-summary-stat"><dt>Đang bảo lưu</dt><dd><strong><c:out value="${commissionSummary.membershipFrozenCount}" /> buổi</strong><span><fmt:formatNumber value="${commissionSummary.membershipFrozenRatePercent}" maxFractionDigits="2" />%</span></dd></div>
                                            <div class="pt-summary-stat"><dt>Đã hủy</dt><dd><strong><c:out value="${commissionSummary.cancelledCount}" /> buổi</strong><span><fmt:formatNumber value="${commissionSummary.cancelledRatePercent}" maxFractionDigits="2" />%</span></dd></div>
                                        </dl>
                                    </c:otherwise>
                                </c:choose>
                            </div>
                            <div class="pt-summary-group pt-summary-pending">
                                <div class="pt-summary-group-heading">
                                    <div><h3>Chờ hệ thống chốt</h3><p>Buổi đã kết thúc, đang chờ kết quả</p></div>
                                    <p class="pt-summary-count"><strong><c:out value="${commissionSummary.pendingSessionCount}" /></strong> buổi</p>
                                </div>
                                <p class="pt-summary-money-line">Hoa hồng đang chờ <strong><c:out value="${gms:formatMoney(commissionSummary.pendingCommissionAmount)}" /></strong></p>
                            </div>
                            <div class="pt-summary-group pt-summary-projected">
                                <div class="pt-summary-group-heading">
                                    <div><h3>Dự kiến</h3><p>Buổi chưa kết thúc trong tháng</p></div>
                                    <p class="pt-summary-count"><strong><c:out value="${commissionSummary.projectedSessionCount}" /></strong> buổi</p>
                                </div>
                                <p class="pt-summary-money-line">Hoa hồng dự kiến <strong><c:out value="${gms:formatMoney(commissionSummary.projectedCommissionAmount)}" /></strong></p>
                            </div>
                        </c:otherwise>
                    </c:choose>
                </section>
                <div class="pt-dashboard-columns">
                    <%-- scheduleDays: Service tạo đủ bảy ngày; day.sessions là buổi DAO đọc, Service bổ sung nhãn. --%>
                    <section class="pt-schedule" aria-labelledby="schedule-title">
                        <div class="pt-section-heading">
                            <h2 id="schedule-title">Lịch 7 ngày</h2>
                            <span class="pt-info">
                                <button type="button" class="pt-info-trigger" aria-label="Giải thích lịch 7 ngày" aria-describedby="schedule-help"><span aria-hidden="true">i</span></button>
                                <span id="schedule-help" class="pt-info-popover" role="tooltip">Từ hôm nay đến 6 ngày tiếp theo. Buổi chưa bắt đầu có thể xem các phương án đổi lịch; thao tác hiện chưa khả dụng.</span>
                            </span>
                        </div>
                        <c:choose>
                            <c:when test="${not empty scheduleError}">
                                <p class="pt-block-error" role="alert"><c:out value="${scheduleError}" /></p>
                            </c:when>
                            <c:when test="${scheduleDays == null}">
                                <p class="empty-state">Chưa tải được lịch.</p>
                            </c:when>
                            <c:otherwise>
                                <%-- Hai nút dùng JS để đổi trạng thái open; tắt JS vẫn mở từng ngày được. --%>
                                <div class="pt-schedule-toolbar" data-schedule-controls hidden>
                                    <button type="button" data-schedule-expand>Mở tất cả</button>
                                    <button type="button" data-schedule-reset>Thu gọn</button>
                                </div>
                                <div class="pt-schedule-days">
                                    <c:forEach var="day" items="${scheduleDays}">
                                            <c:choose>
                                                <c:when test="${empty day.sessions}">
                                                    <div class="pt-schedule-day is-empty ${day.today ? 'is-today' : ''}">
                                                        <h3><c:out value="${day.dayLabel}" /> · <c:out value="${gms:formatDate(day.slotDate)}" /></h3>
                                                        <p class="pt-day-empty">Không có lịch</p>
                                                    </div>
                                                </c:when>
                                                <c:otherwise>
                                                    <%-- Hôm nay mở sẵn; các ngày khác thu gọn. fn:length đếm buổi trong day.sessions. --%>
                                                    <details class="pt-schedule-day ${day.today ? 'is-today' : ''}"
                                                             ${day.today ? 'open' : ''}>
                                                        <summary>
                                                            <span class="pt-day-label"><c:out value="${day.dayLabel}" /> · <c:out value="${gms:formatDate(day.slotDate)}" /></span>
                                                            <span class="pt-day-count"><c:out value="${fn:length(day.sessions)}" /> buổi</span>
                                                        </summary>
                                                    <div class="pt-session-list">
                                                        <c:forEach var="sessionView" items="${day.sessions}">
                                                            <article class="pt-schedule-session">
                                                                <div class="pt-session-main">
                                                                    <time class="pt-session-time"><c:out value="${gms:formatTime(sessionView.startTime)}" /> – <c:out value="${gms:formatTime(sessionView.endTime)}" /></time>
                                                                    <div class="pt-session-details">
                                                                        <h4><c:out value="${sessionView.customerName}" /><c:if test="${not empty sessionView.partnerName}"> &amp; <c:out value="${sessionView.partnerName}" /></c:if></h4>
                                                                        <p><c:out value="${sessionView.subscriptionTypeLabel}" /></p>
                                                                        <c:if test="${not empty sessionView.missedReasonLabel}">
                                                                            <p class="pt-missed-reason"><c:out value="${sessionView.missedReasonLabel}" /></p>
                                                                        </c:if>
                                                                    </div>
                                                                </div>
                                                                <div class="pt-session-controls">
                                                                    <%-- Scheduled là trạng thái mặc định của lịch; chỉ hiện badge khi trạng thái khác. --%>
                                                                    <c:if test="${sessionView.status ne 'SCHEDULED'}">
                                                                        <span class="badge ${sessionView.statusBadgeClass}"><c:out value="${sessionView.statusLabel}" /></span>
                                                                    </c:if>
                                                                    <c:if test="${sessionView.canOpenActions}">
                                                                        <%-- sessionId là Sessions.SessionId; JS dùng để mở đúng hộp buổi. --%>
                                                                        <button type="button" class="btn pt-session-action" data-pt-session-action
                                                                                data-session-id="${sessionView.sessionId}"
                                                                                data-session-label="Buổi ${gms:formatTime(sessionView.startTime)} – ${gms:formatTime(sessionView.endTime)} · ${day.dayLabel}">
                                                                            Xem tùy chọn lịch
                                                                        </button>
                                                                    </c:if>
                                                                </div>
                                                            </article>
                                                        </c:forEach>
                                                    </div>
                                                    </details>
                                                </c:otherwise>
                                            </c:choose>
                                    </c:forEach>
                                </div>
                            </c:otherwise>
                        </c:choose>
                    </section>

                    <aside class="pt-dashboard-side" aria-label="Khách phụ trách và đánh giá">
                        <%-- assignedPackages: DAO trả từng gói/buổi lẻ, Service thêm nhãn loại. --%>
                        <section class="pt-clients" aria-labelledby="clients-title">
                            <div class="pt-section-heading">
                                <h2 id="clients-title">Khách phụ trách</h2>
                                <span class="pt-info">
                                    <button type="button" class="pt-info-trigger" aria-label="Giải thích khách phụ trách" aria-describedby="clients-help"><span aria-hidden="true">i</span></button>
                                    <span id="clients-help" class="pt-info-popover" role="tooltip">Gói PT còn hoạt động và buổi lẻ sắp dạy; một khách có nhiều gói có thể xuất hiện nhiều dòng.</span>
                                </span>
                            </div>
                            <c:choose>
                                <c:when test="${not empty assignedPackagesError}">
                                    <p class="pt-block-error" role="alert"><c:out value="${assignedPackagesError}" /></p>
                                </c:when>
                                <c:when test="${assignedPackages == null}">
                                    <p class="empty-state">Chưa tải được khách phụ trách.</p>
                                </c:when>
                                <c:when test="${empty assignedPackages}">
                                    <p class="empty-state">Chưa có gói PT hoặc buổi lẻ sắp dạy.</p>
                                </c:when>
                                <c:otherwise>
                                    <div class="pt-client-list">
                                        <c:forEach var="assignedPackage" items="${assignedPackages}">
                                            <article class="pt-client-item">
                                                <span class="pt-person-avatar" aria-hidden="true"><c:out value="${fn:substring(assignedPackage.customerName, 0, 1)}" /></span>
                                                <h3><c:out value="${assignedPackage.customerName}" /><c:if test="${not empty assignedPackage.partnerName}"> &amp; <c:out value="${assignedPackage.partnerName}" /></c:if></h3>
                                                <p class="pt-client-type"><c:out value="${assignedPackage.ptSubscriptionTypeLabel}" /></p>
                                                <div class="pt-client-meta">
                                                    <span>Hạn sử dụng: <strong><c:out value="${gms:formatDate(assignedPackage.ptEndDate)}" /></strong></span>
                                                    <span>Đã xếp lịch: <strong><c:out value="${assignedPackage.scheduledSessionCount}" /> buổi</strong></span>
                                                </div>
                                            </article>
                                        </c:forEach>
                                    </div>
                                </c:otherwise>
                            </c:choose>
                        </section>

                        <%-- ptRating: Service chỉ hiện điểm khi đủ số khách theo P-22. --%>
                        <section class="pt-rating" aria-labelledby="rating-title">
                            <div class="pt-section-heading">
                                <h2 id="rating-title">Đánh giá PT</h2>
                                <span class="pt-info">
                                    <button type="button" class="pt-info-trigger" aria-label="Giải thích đánh giá PT" aria-describedby="rating-help"><span aria-hidden="true">i</span></button>
                                    <span id="rating-help" class="pt-info-popover" role="tooltip">Điểm tháng này chỉ hiện khi đủ số khách đánh giá; không hiển thị người chấm.</span>
                                </span>
                            </div>
                            <c:choose>
                                <c:when test="${not empty ptRatingError}">
                                    <p class="pt-block-error" role="alert"><c:out value="${ptRatingError}" /></p>
                                </c:when>
                                <c:when test="${ptRating == null}">
                                    <p class="empty-state">Chưa tải được đánh giá.</p>
                                </c:when>
                                <c:when test="${ptRating.ratingVisible}">
                                    <div class="pt-rating-score"><i class="fa-solid fa-star" aria-hidden="true"></i>
                                        <strong><fmt:formatNumber value="${ptRating.averageRating}" maxFractionDigits="2" minFractionDigits="0" />/5</strong>
                                    </div>
                                    <p><c:out value="${ptRating.ratingCustomerCount}" /> khách đã đánh giá trong tháng</p>
                                    <p class="pt-rating-message"><c:out value="${ptRating.message}" /></p>
                                </c:when>
                                <c:otherwise>
                                    <p class="empty-state"><c:out value="${ptRating.message}" /></p>
                                </c:otherwise>
                            </c:choose>
                        </section>
                        <%-- Đặt ngay dưới Đánh giá PT, trong cột phải của dashboard. --%>
                        <div class="pt-dashboard-doodles">
                            <img class="pt-dashboard-doodles__kid"
                                 src="${pageContext.request.contextPath}/images/pt/doodle-kid-umbrella.svg"
                                 alt="Cậu bé cầm ô" loading="lazy" decoding="async" />
                            <div class="pt-dashboard-doodles__trainer">
                                <p class="pt-dashboard-doodles__speech">Chúc bạn một ngày thật năng suất!</p>
                                <img src="${pageContext.request.contextPath}/images/pt/doodle-gym-guy.svg"
                                     alt="Thanh niên đang tập tạ" loading="lazy" decoding="async" />
                            </div>
                        </div>
                    </aside>
                </div>

            </main>
        </div>
    </div>

    <%-- Hộp này chỉ giới thiệu bốn cách xử lý; nghiệp vụ chưa có Service nên nút bị khóa. --%>
    <dialog id="ptSessionDialog" class="pt-session-dialog" aria-labelledby="ptSessionDialogTitle" aria-describedby="ptSessionDialogHint">
        <h2 id="ptSessionDialogTitle">Tùy chọn lịch buổi tập</h2>
        <p id="ptSessionDialogHint">Các phương án dành cho buổi chưa bắt đầu.</p>
        <div class="pt-session-options">
            <button type="button" class="pt-session-option" data-pt-action="reschedule" disabled><i class="fa-solid fa-calendar-days" aria-hidden="true"></i><span><strong>Dời buổi</strong><span>Chuyển sang slot khác của bạn, không tính lượt đổi của khách.</span></span></button>
            <button type="button" class="pt-session-option" data-pt-action="transfer" disabled><i class="fa-solid fa-user-pen" aria-hidden="true"></i><span><strong>Chuyển PT</strong><span>Chọn PT khác có slot phù hợp để tiếp nhận buổi.</span></span></button>
            <button type="button" class="pt-session-option" data-pt-action="cancel" disabled><i class="fa-solid fa-ban" aria-hidden="true"></i><span><strong>Hủy buổi</strong><span>Xem điều kiện hủy trước khi xác nhận.</span></span></button>
            <button type="button" class="pt-session-option" data-pt-action="customer-swap" disabled><i class="fa-solid fa-user-shield" aria-hidden="true"></i><span><strong>Đổi lịch theo yêu cầu khách</strong><span>Dành cho khách phụ thuộc, có tính lượt đổi của gói.</span></span></button>
        </div>
        <p class="pt-session-availability">Các lựa chọn hiện chưa khả dụng.</p>
        <div class="pt-session-dialog-actions"><button type="button" class="btn btn-primary" id="ptSessionDialogClose" autofocus>Đóng</button></div>
    </dialog>
</body>
</html>
