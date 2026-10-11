<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="gms" uri="/WEB-INF/gms-functions.tld" %>
<%-- Đang lọc theo filter Servlet THỰC SỰ áp dụng (không theo URL thô): khối đang bị lọc dùng câu rỗng "Không có buổi phù hợp". Bộ lọc sai → sessionFilter là none() → hai biến này false. --%>
<c:set var="upcomingFiltered" value="${sessionFilter.commonFilterActive}" />
<c:set var="historyFiltered" value="${sessionFilter.historyFilterActive}" />

<!DOCTYPE html>
<html lang="vi">
<head>
    <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
    <title>Lịch tập của tôi · Super Gym</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/customer/dashboard.css?v=7">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/customer/my-pt-sessions.css?v=12">
</head>
<body class="dock ${themeClass}">
<%@ include file="/WEB-INF/jsp/common/flash-toast.jspf" %>

<div class="app">
    <%@ include file="/WEB-INF/jsp/common/layout-customer.jspf" %>
    <div class="main">
        <%-- ===== Header (dashboard: CustomerDashboardView từ Service hiện có) ===== --%>
        <header class="topbar">
            <div class="tb-left">
                <h1 class="tb-title">Lịch tập của tôi</h1>
            </div>
            <c:if test="${not empty dashboard}">
                <div class="top-right">
                    <a class="tb-user" href="${pageContext.request.contextPath}/account" title="Cài đặt tài khoản">
                        <span class="avatar"><c:out value="${dashboard.initials}" /></span>
                        <span class="tb-user-text">
                            <b><c:out value="${dashboard.fullName}" /></b>
                            <span><c:out value="${dashboard.customerCode}" /></span>
                        </span>
                    </a>
                </div>
            </c:if>
        </header>

        <main class="content my-pt-sessions">
            <c:if test="${not empty dashboardError}">
                <div class="alert-banner danger" role="alert"><c:out value="${dashboardError}" /></div>
            </c:if>

            <%-- ===== Gói PT của bạn (ptPackages: List<MyPtPackageView>, ptPackagesError: lỗi từ Service) ===== --%>
            <section class="my-pt-panel" aria-labelledby="packages-title" data-reveal>
                <div class="my-pt-heading">
                    <div class="my-pt-title-with-hint">
                        <h2 id="packages-title">Gói PT của bạn</h2>
                        <%-- Giải thích chỉ hiện khi hover/focus nút i, cùng cách dùng với PT Dashboard. --%>
                        <span class="my-pt-info">
                            <button type="button" aria-label="Giải thích thông tin gói PT" aria-describedby="packages-help">i</button>
                            <span id="packages-help" role="tooltip">
                                <strong>Tổng số buổi:</strong> số buổi được tạo khi thanh toán gói.<br>
                                <strong>Buổi chưa chốt:</strong> gồm buổi sắp tới và buổi đang chờ kết quả.<br>
                                <strong>Lượt đổi còn lại:</strong> theo giới hạn lúc mua gói. Với gói đôi, hai thành viên dùng chung số buổi và lượt đổi.
                            </span>
                        </span>
                    </div>
                    <a class="btn btn-primary btn-sm" href="${pageContext.request.contextPath}/customer/book-session">Đặt buổi lẻ</a>
                </div>
                <c:choose>
                    <%-- 1. Kiểm tra lỗi trước: lỗi tải không có nghĩa là khách chưa có gói. --%>
                    <c:when test="${not empty ptPackagesError}">
                        <p class="my-pt-package-error" role="alert"><c:out value="${ptPackagesError}" /></p>
                    </c:when>
                    <%-- 2. Tải thành công nhưng rỗng: chỉ xét gói đã mua có Subscriptions.Status = Active. --%>
                    <c:when test="${empty ptPackages}">
                        <div class="my-pt-placeholder">
                            <p>Bạn chưa có gói PT đang hoạt động.</p>
                            <span>Bạn vẫn có thể đặt buổi PT lẻ.</span>
                        </div>
                    </c:when>
                    <%-- 3. Mỗi DTO do DAO tạo tương ứng một gói; Service đã điền nhãn và lượt đổi còn lại. --%>
                    <c:otherwise>
                        <div class="my-pt-package-list">
                            <c:forEach var="ptPackage" items="${ptPackages}">
                                <article class="my-pt-package-card">
                                    <div class="my-pt-package-heading">
                                        <h3><c:out value="${ptPackage.packageName}" /></h3>
                                        <span class="my-pt-package-type"><c:out value="${ptPackage.subscriptionTypeLabel}" /></span>
                                    </div>

                                    <%-- PT phụ trách chung của gói; partnerName là thành viên còn lại của gói đôi. --%>
                                    <dl class="my-pt-package-details ${not empty ptPackage.partnerName ? 'my-pt-package-details-couple' : ''}">
                                        <div>
                                            <dt>PT phụ trách</dt>
                                            <dd><c:out value="${ptPackage.ptName}" /></dd>
                                        </div>
                                        <c:if test="${not empty ptPackage.partnerName}">
                                            <div>
                                                <dt>Người tập cùng</dt>
                                                <dd><c:out value="${ptPackage.partnerName}" /></dd>
                                            </div>
                                        </c:if>
                                        <%-- LocalDate định dạng bằng hàm gms; ngày hết hạn vẫn là ngày còn hiệu lực. --%>
                                        <div>
                                            <dt>Bắt đầu</dt>
                                            <dd><c:out value="${gms:formatDate(ptPackage.startDate)}" /></dd>
                                        </div>
                                        <div>
                                            <dt>Hạn gói</dt>
                                            <dd><c:out value="${gms:formatDate(ptPackage.endDate)}" /></dd>
                                        </div>
                                    </dl>

                                    <%-- Tổng buổi sinh lúc Paid; chưa chốt là COUNT Scheduled, không phải tổng trừ đã tập. --%>
                                    <dl class="my-pt-package-stats">
                                        <div>
                                            <dt>Tổng số buổi</dt>
                                            <dd><c:out value="${ptPackage.generatedSessionCount}" /></dd>
                                        </div>
                                        <div>
                                            <dt>Buổi chưa chốt</dt>
                                            <dd><c:out value="${ptPackage.scheduledSessionCount}" /></dd>
                                        </div>
                                        <div>
                                            <dt>Lượt đổi còn lại</dt>
                                            <dd><c:out value="${ptPackage.remainingSwaps}" /></dd>
                                        </div>
                                    </dl>
                                </article>
                            </c:forEach>
                        </div>
                    </c:otherwise>
                </c:choose>
            </section>

            <%-- ===== Bộ lọc chung (sessionFilter: filter đang áp dụng; filterErrors: lỗi từng ô; filterNotApplied) =====
                 Chỉ gồm Loại buổi, Từ ngày, Đến ngày; áp dụng cho cả Sắp tới và Lịch sử.
                 Giá trị trong ô lấy từ ${param.*} (khách đã nhập, kể cả khi sai) để khách sửa tiếp. --%>
            <section class="my-pt-panel" aria-labelledby="filters-title" data-reveal>
                <div class="my-pt-heading">
                    <h2 id="filters-title">Lọc lịch tập</h2>
                    <span class="my-pt-caption">Áp dụng cho cả lịch sắp tới và lịch sử</span>
                </div>
                <%-- 1. Có ô sai: Servlet đã dùng danh sách mặc định, báo rõ để khách không tưởng đã lọc. --%>
                <c:if test="${filterNotApplied}">
                    <p class="my-pt-filter-alert" role="alert">Bộ lọc chưa được áp dụng. Vui lòng sửa ô được đánh dấu bên dưới.</p>
                </c:if>
                <form method="get" action="${pageContext.request.contextPath}/customer/pt-sessions">
                    <fieldset class="my-pt-filters">
                        <legend class="my-pt-visually-hidden">Điều kiện lọc lịch tập</legend>
                        <%-- 2. Loại buổi: value là mã DB, Service chỉ nhận 3 loại này. --%>
                        <label>Loại buổi
                            <select name="type" class="${not empty filterErrors.type ? 'invalid' : ''}"
                                    <c:if test="${not empty filterErrors.type}">aria-invalid="true" aria-describedby="typeError"</c:if>>
                                <option value="">Tất cả</option>
                                <option value="PTSingle" ${param.type eq 'PTSingle' ? 'selected' : ''}>PT cá nhân</option>
                                <option value="PTCouple" ${param.type eq 'PTCouple' ? 'selected' : ''}>PT đôi</option>
                                <option value="SingleSession" ${param.type eq 'SingleSession' ? 'selected' : ''}>Buổi lẻ</option>
                            </select>
                            <c:if test="${not empty filterErrors.type}">
                                <span id="typeError" class="field-error"><c:out value="${filterErrors.type}" /></span>
                            </c:if>
                        </label>
                        <%-- 3. Khoảng ngày theo ngày buổi tập, gồm cả hai đầu. --%>
                        <label>Từ ngày
                            <input type="date" name="dateFrom" value="${fn:escapeXml(param.dateFrom)}"
                                   class="${not empty filterErrors.dateFrom ? 'invalid' : ''}"
                                   <c:if test="${not empty filterErrors.dateFrom}">aria-invalid="true" aria-describedby="dateFromError"</c:if>>
                            <c:if test="${not empty filterErrors.dateFrom}">
                                <span id="dateFromError" class="field-error"><c:out value="${filterErrors.dateFrom}" /></span>
                            </c:if>
                        </label>
                        <label>Đến ngày
                            <input type="date" name="dateTo" value="${fn:escapeXml(param.dateTo)}"
                                   class="${not empty filterErrors.dateTo ? 'invalid' : ''}"
                                   <c:if test="${not empty filterErrors.dateTo}">aria-invalid="true" aria-describedby="dateToError"</c:if>>
                            <c:if test="${not empty filterErrors.dateTo}">
                                <span id="dateToError" class="field-error"><c:out value="${filterErrors.dateTo}" /></span>
                            </c:if>
                        </label>
                        <%-- 4. Giữ trạng thái lịch sử đang chọn khi đổi bộ lọc chung, chỉ khi giá trị đó hợp lệ. --%>
                        <c:if test="${not empty param.historyStatus and empty filterErrors.historyStatus}">
                            <input type="hidden" name="historyStatus" value="${fn:escapeXml(param.historyStatus)}">
                        </c:if>
                        <%-- 5. Xóa lọc là link về URL gốc: xóa cả bộ lọc chung lẫn trạng thái lịch sử.
                             Không dùng type="reset" vì reset chỉ đưa ô về giá trị lúc tải trang, không xóa URL. --%>
                        <div class="my-pt-filter-actions">
                            <button class="btn btn-primary btn-sm" type="submit">Áp dụng</button>
                            <a class="btn btn-outline btn-sm" href="${pageContext.request.contextPath}/customer/pt-sessions">Xóa lọc</a>
                        </div>
                    </fieldset>
                </form>
            </section>

            <%-- ===== Lịch tập sắp tới (upcomingDays: Map ngày → List<MyPtUpcomingSessionView>, upcomingSessionsError) ===== --%>
            <section class="my-pt-panel" aria-labelledby="pending-title" data-reveal>
                <div class="my-pt-heading">
                    <div class="my-pt-title-with-hint">
                        <h2 id="pending-title">Lịch tập sắp tới</h2>
                        <span class="my-pt-info">
                            <button type="button" aria-label="Giải thích lịch tập sắp tới" aria-describedby="pending-help">i</button>
                            <span id="pending-help" role="tooltip">Các buổi chưa kết thúc, xếp theo ngày giờ gần nhất. Membership hoặc DayPass hợp lệ cho ngày tập được công nhận là quyền vào gym. Cảnh báo bảo lưu không phải kết quả vắng.</span>
                        </span>
                    </div>
                </div>
                <c:choose>
                    <%-- 1. Lỗi truy vấn phải hiện lỗi, không báo khách không có lịch. --%>
                    <c:when test="${not empty upcomingSessionsError}">
                        <p class="my-pt-package-error" role="alert"><c:out value="${upcomingSessionsError}" /></p>
                    </c:when>
                    <%-- 2. Rỗng do bộ lọc khác với chưa có lịch thật (GBR-21). --%>
                    <c:when test="${empty upcomingDays and upcomingFiltered}">
                        <div class="my-pt-placeholder">
                            <p>Không có buổi phù hợp với bộ lọc.</p>
                            <span>Hãy đổi loại buổi, khoảng ngày hoặc bấm Xóa lọc.</span>
                        </div>
                    </c:when>
                    <c:when test="${empty upcomingDays}">
                        <div class="my-pt-placeholder">
                            <p>Bạn không có buổi PT sắp tới.</p>
                            <span>Các buổi đã qua hoặc đã hủy thuộc lịch sử buổi tập.</span>
                        </div>
                    </c:when>
                    <c:otherwise>
                        <%-- upcomingDays: Map ngày → buổi, ngày gần nhất trước; luôn hiện đầy đủ buổi mỗi ngày. --%>

                        <div class="my-pt-days">
                            <c:forEach var="day" items="${upcomingDays}">
                                <c:set var="isToday" value="${day.key eq todayDate}" />

                                <div class="my-pt-day ${isToday ? 'is-today' : ''}">
                                    <div class="my-pt-day-heading">
                                        <span class="my-pt-day-label"><c:if test="${isToday}">Hôm nay · </c:if><c:out value="${gms:formatDate(day.key)}" /></span>
                                        <span class="my-pt-day-count"><c:out value="${fn:length(day.value)}" /> buổi</span>
                                    </div>
                                    <div class="my-pt-day-sessions">
                                        <%-- 3. Service chỉ trả Scheduled chưa kết thúc; JSP không tự tính giờ/quyền vào gym. --%>
                                        <c:forEach var="ptSession" items="${day.value}">
                                            <article class="my-pt-session">
                                                <div class="my-pt-session-time">
                                                    <p class="my-pt-session-hours"><c:out value="${gms:formatTime(ptSession.startTime)}" />–<c:out value="${gms:formatTime(ptSession.endTime)}" /></p>
                                    </div>
                                    <div class="my-pt-session-content">
                                        <div class="my-pt-session-heading">
                                            <%-- Tên PT lấy từ Sessions.PtEmployeeId, có thể khác PT phụ trách gói. --%>
                                            <h3>PT <c:out value="${ptSession.ptName}" /></h3>
                                            <c:if test="${ptSession.inProgress}">
                                                <span class="my-pt-package-type">Đang diễn ra</span>
                                            </c:if>
                                        </div>
                                        <p class="my-pt-caption"><c:out value="${ptSession.packageName}" /> · <c:out value="${ptSession.subscriptionTypeLabel}" /></p>
                                        <c:if test="${not empty ptSession.partnerName}">
                                            <p class="my-pt-caption">Tập cùng: <c:out value="${ptSession.partnerName}" /></p>
                                        </c:if>
                                        <%-- 3. Cảnh báo do Service xét riêng cho người xem và người đi cùng, không đổi trạng thái. --%>
                                        <c:if test="${not empty ptSession.warning}">
                                            <p class="my-pt-session-warning"><c:out value="${ptSession.warning}" /></p>
                                        </c:if>
                                        <%-- Chưa nối backend đổi/hủy nên không hiện nút thao tác giả. --%>
                                    </div>
                                </article>
                            </c:forEach>
                                    </div>
                                </div>
                            </c:forEach>
                        </div>
                    </c:otherwise>
                </c:choose>
            </section>

            <%-- ===== Lịch sử buổi tập (historyDays: Map ngày → List<MyPtHistorySessionView>, historySessionsError) ===== --%>
            <section class="my-pt-panel" aria-labelledby="history-title" data-reveal>
                <div class="my-pt-heading">
                    <div class="my-pt-title-with-hint">
                        <h2 id="history-title">Lịch sử buổi tập</h2>
                        <%-- Hint chung cho khối: cách chia nhóm và ý nghĩa từng trạng thái. --%>
                        <span class="my-pt-info">
                            <button type="button" aria-label="Giải thích lịch sử buổi tập" aria-describedby="history-help">i</button>
                            <span id="history-help" role="tooltip">
                                Các buổi tập đã diễn ra và các buổi đã bị hủy.<br>
                                <strong>Kết quả đang cập nhật:</strong> buổi vừa kết thúc, kết quả sẽ có sau khi hết ngày tập.<br>
                                <strong>Đã check-in:</strong> bạn (hoặc người tập cùng, nếu là gói đôi) đã check-in vào gym hôm đó. Đây chỉ là ghi nhận vào cửa, không phải xác nhận đã tập xong với PT.<br>
                                <strong>Vắng / Đã hủy:</strong> lý do ghi ngay dưới từng buổi. Buổi bạn tự hủy vẫn bị trừ khỏi gói và không được hoàn tiền.
                            </span>
                        </span>
                    </div>
                </div>
                <%-- Trạng thái riêng của Lịch sử: mỗi lựa chọn là một link GET, bấm là lọc ngay, không cần JS.
                     Link giữ nguyên bộ lọc chung khách đang nhập (type, dateFrom, dateTo) và chỉ đổi historyStatus.
                     Lựa chọn sáng theo filter đang áp dụng; bộ lọc sai → none() → "Tất cả" sáng, đúng với danh sách. --%>
                <nav class="my-pt-status-tabs" aria-label="Lọc lịch sử theo trạng thái">
                    <c:forTokens var="option" delims=","
                                 items=":Tất cả,pending:Kết quả đang cập nhật,checkedIn:Đã check-in,missed:Vắng,cancelled:Đã hủy">
                        <c:set var="optionValue" value="${fn:substringBefore(option, ':')}" />
                        <c:set var="optionLabel" value="${fn:substringAfter(option, ':')}" />
                        <c:set var="isCurrent" value="${empty optionValue
                                ? empty sessionFilter.historyStatus
                                : sessionFilter.historyStatus.urlValue eq optionValue}" />
                        <c:url var="optionUrl" value="/customer/pt-sessions">
                            <c:if test="${not empty param.type}"><c:param name="type" value="${param.type}" /></c:if>
                            <c:if test="${not empty param.dateFrom}"><c:param name="dateFrom" value="${param.dateFrom}" /></c:if>
                            <c:if test="${not empty param.dateTo}"><c:param name="dateTo" value="${param.dateTo}" /></c:if>
                            <c:if test="${not empty optionValue}"><c:param name="historyStatus" value="${optionValue}" /></c:if>
                        </c:url>
                        <a class="my-pt-status-tab" href="${optionUrl}" ${isCurrent ? 'aria-current="true"' : ''}><c:out value="${optionLabel}" /></a>
                    </c:forTokens>
                </nav>
                <%-- historyStatus lạ (sửa URL tay): báo ngay dưới dải lựa chọn. --%>
                <c:if test="${not empty filterErrors.historyStatus}">
                    <p class="field-error my-pt-status-error"><c:out value="${filterErrors.historyStatus}" /></p>
                </c:if>
                <c:choose>
                    <%-- 1. Lỗi truy vấn phải hiện lỗi, không báo khách chưa có lịch sử. --%>
                    <c:when test="${not empty historySessionsError}">
                        <p class="my-pt-package-error" role="alert"><c:out value="${historySessionsError}" /></p>
                    </c:when>
                    <%-- 2a. Rỗng do bộ lọc chung hoặc trạng thái lịch sử (GBR-21). --%>
                    <c:when test="${empty historyDays and historyFiltered}">
                        <div class="my-pt-placeholder">
                            <p>Không có buổi phù hợp với bộ lọc.</p>
                            <span>Hãy chọn trạng thái khác, đổi bộ lọc phía trên hoặc bấm Xóa lọc.</span>
                        </div>
                    </c:when>
                    <%-- 2b. Tải thành công, không lọc mà vẫn rỗng: khách chưa có lịch sử. --%>
                    <c:when test="${empty historyDays}">
                        <div class="my-pt-placeholder">
                            <p>Bạn chưa có buổi tập nào trong lịch sử.</p>
                            <span>Buổi đã qua, đã vắng hoặc đã hủy sẽ xuất hiện tại đây.</span>
                        </div>
                    </c:when>
                    <%-- 3. historyDays: Map ngày → buổi, ngày mới nhất trước; luôn hiển thị đầy đủ các buổi. --%>
                    <c:otherwise>

                        <div class="my-pt-days">
                            <c:forEach var="day" items="${historyDays}">
                                <c:set var="isToday" value="${day.key eq todayDate}" />

                                <div class="my-pt-day ${isToday ? 'is-today' : ''}">
                                    <div class="my-pt-day-heading">
                                        <span class="my-pt-day-label"><c:if test="${isToday}">Hôm nay · </c:if><c:out value="${gms:formatDate(day.key)}" /></span>
                                        <span class="my-pt-day-count"><c:out value="${fn:length(day.value)}" /> buổi</span>
                                    </div>
                                    <div class="my-pt-day-sessions">
                                        <%-- Mỗi DTO là một buổi; Service đã điền nhãn trạng thái, giải thích và hint. --%>
                                        <c:forEach var="historySession" items="${day.value}">
                                            <article class="my-pt-session">
                                                <%-- Giờ thực tế của buổi (TimeSlots); ngày đã nằm ở tiêu đề nhóm. --%>
                                                <div class="my-pt-session-time">
                                                    <p class="my-pt-session-hours"><c:out value="${gms:formatTime(historySession.startTime)}" />–<c:out value="${gms:formatTime(historySession.endTime)}" /></p>
                                    </div>
                                    <div class="my-pt-session-content">
                                        <div class="my-pt-session-heading">
                                            <%-- PT lấy từ Sessions.PtEmployeeId, có thể khác PT phụ trách gói. --%>
                                            <h3>PT <c:out value="${historySession.ptName}" /></h3>
                                            <div class="my-pt-session-status">
                                                <%-- Badge dùng chung common.css như PT Dashboard; class do Service chọn. --%>
                                                <span class="badge ${historySession.statusBadgeClass}"><c:out value="${historySession.statusLabel}" /></span>
                                                <%-- Tạm không hiện hint từng buổi; ý nghĩa Đã check-in nằm trong hint chung của khối. --%>
                                            </div>
                                        </div>
                                        <p class="my-pt-caption"><c:out value="${historySession.packageName}" /> · <c:out value="${historySession.subscriptionTypeLabel}" /></p>
                                        <c:if test="${not empty historySession.partnerName}">
                                            <p class="my-pt-caption">Tập cùng: <c:out value="${historySession.partnerName}" /></p>
                                        </c:if>
                                        <%-- Giải thích Vắng/Hủy/chờ chốt do Service diễn giải từ kết quả đã lưu. --%>
                                        <c:if test="${not empty historySession.explanation}">
                                            <p class="my-pt-session-note"><c:out value="${historySession.explanation}" /></p>
                                        </c:if>
                                        <%-- Lý do hủy PT đã chọn, in nguyên văn khi có. --%>
                                        <c:if test="${not empty historySession.cancelReason}">
                                            <p class="my-pt-session-note">Lý do: <c:out value="${historySession.cancelReason}" /></p>
                                        </c:if>
                                    </div>
                                </article>
                            </c:forEach>
                                    </div>
                                </div>
                            </c:forEach>
                        </div>
                    </c:otherwise>
                </c:choose>
            </section>
        </main>
    </div>
</div>
</body>
</html>
