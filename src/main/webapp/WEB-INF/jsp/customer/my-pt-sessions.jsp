<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="gms" uri="/WEB-INF/gms-functions.tld" %>
<%--
  MÀN "LỊCH TẬP CỦA TÔI" — /customer/pt-sessions
  Servlet gửi sang (đọc bằng ${tên}):
    dashboard                 : tên + mã khách ở header
    ptPackages                : thẻ "Gói PT của bạn" (gói Active)
    ptPackageOptions          : danh sách gói cho ô lọc "Gói PT"
    sessionFilter             : bộ lọc ĐÃ HỢP LỆ ; filterErrors : lỗi từng ô lọc
    upcomingPage, historyPage : 1 trang của mỗi khối → .days (ngày) → .sessions (buổi)
    filterQuery, pagingQuery  : chuỗi tham số để link giữ nguyên bộ lọc
    todayDate, csrfToken
  ${param.xxx} = giá trị thô trên URL, dùng để điền lại ô lọc.

  TÌM NHANH (Ctrl+F gõ tag): [HEADER] [TAB] [LOC] [SAP-TOI] [LICH-SU] [PHAN-TRANG] [GOI-PT]
                            [NUT] [HUY-BUOI] [DOI-BUOI]
--%>

<%-- Biến dùng nhiều lần --%>
<c:set var="pageUrl" value="${pageContext.request.contextPath}/customer/pt-sessions" />
<c:set var="upcomingCurrent" value="${empty upcomingPage ? 1 : upcomingPage.currentPage}" />
<c:set var="historyCurrent" value="${empty historyPage ? 1 : historyPage.currentPage}" />

<%-- Tab mở sẵn: theo ?view=, mặc định "upcoming"; có historyStatus thì mở "history" --%>
<c:choose>
    <c:when test="${param.view eq 'packages' or param.view eq 'upcoming' or param.view eq 'history'}">
        <c:set var="activeView" value="${param.view}" />
    </c:when>
    <c:when test="${not empty param.historyStatus}"><c:set var="activeView" value="history" /></c:when>
    <c:otherwise><c:set var="activeView" value="upcoming" /></c:otherwise>
</c:choose>

<!DOCTYPE html>
<html lang="vi">
<head>
    <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
    <title>Lịch tập của tôi · Super Gym</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/customer/dashboard.css?v=20261013c">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/customer/my-pt-sessions.css?v=20261017b">
    <script src="${pageContext.request.contextPath}/js/pages/customer/my-pt-sessions.js?v=20261017b" defer></script>
</head>
<body class="dock my-pt-sessions-page ${themeClass}">
<%@ include file="/WEB-INF/jsp/common/flash-toast.jspf" %>

<div class="app">
    <%@ include file="/WEB-INF/jsp/common/layout-customer.jspf" %>
    <div class="main">

        <%-- ===================== [HEADER] ===================== --%>
        <header class="topbar">
            <div class="tb-left"><h1 class="tb-title">Lịch tập của tôi</h1></div>
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

        <main class="content my-pt-sessions" id="main-content" tabindex="-1" data-active-view="${activeView}">
            <c:if test="${not empty dashboardError}">
                <div class="alert-banner danger" role="alert"><c:out value="${dashboardError}" /></div>
            </c:if>

            <%-- ===================== [TAB] 3 tab (JS ẩn/hiện khối, không tải lại trang) ===================== --%>
            <div class="my-pt-toolbar">
                <nav class="my-pt-view-tabs" aria-label="Lịch và gói PT" hidden>
                    <button type="button" data-session-view="upcoming">Sắp tới</button>
                    <button type="button" data-session-view="history">Lịch sử</button>
                    <button type="button" data-session-view="packages">Gói PT của tôi</button>
                </nav>
                <a class="btn btn-primary" href="${pageContext.request.contextPath}/customer/book-session"><svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path d="M12 5v14M5 12h14"/></svg>Đặt buổi lẻ</a>
            </div>

            <%-- ===================== [LOC] BỘ LỌC (form GET → Servlet.loadFilter → Service.parseFilter) ===================== --%>
            <section class="my-pt-panel my-pt-filter-panel" aria-labelledby="filters-title">
                <div class="my-pt-heading"><h2 id="filters-title">Lọc lịch tập</h2></div>

                <c:if test="${filterNotApplied}">
                    <p class="my-pt-filter-alert" role="alert">Bộ lọc chưa được áp dụng. Vui lòng sửa ô được đánh dấu bên dưới.</p>
                </c:if>

                <form method="get" action="${pageUrl}" id="sessionFilterForm">
                    <input type="hidden" name="view" value="${activeView}">
                    <fieldset class="my-pt-filters">
                        <legend class="my-pt-visually-hidden">Điều kiện lọc lịch tập</legend>

                        <%-- [LOC] Ô 1: Gói PT --%>
                        <label class="my-pt-package-filter">Gói PT
                            <select name="subscriptionId" class="${not empty filterErrors.subscriptionId ? 'invalid' : ''}" ${not empty ptPackageOptionsError ? 'disabled' : ''}>
                                <option value="">Tất cả gói</option>
                                <c:forEach var="option" items="${ptPackageOptions}">
                                    <option value="${option.subscriptionId}" ${sessionFilter.subscriptionId eq option.subscriptionId ? 'selected' : ''}>
                                        <c:out value="${option.packageName}" /> · <c:out value="${option.ptName}" /> · ${gms:formatDate(option.startDate)}–${gms:formatDate(option.endDate)} · Gói #${option.subscriptionId}
                                    </option>
                                </c:forEach>
                            </select>
                            <c:if test="${not empty filterErrors.subscriptionId}"><span class="field-error"><c:out value="${filterErrors.subscriptionId}" /></span></c:if>
                            <c:if test="${not empty ptPackageOptionsError}"><span class="field-error"><c:out value="${ptPackageOptionsError}" /></span></c:if>
                        </label>

                        <%-- [LOC] Ô 2: Loại buổi --%>
                        <label>Loại buổi
                            <select name="type" class="${not empty filterErrors.type ? 'invalid' : ''}">
                                <option value="">Tất cả</option>
                                <option value="PTSingle"      ${param.type eq 'PTSingle' ? 'selected' : ''}>PT cá nhân</option>
                                <option value="PTCouple"      ${param.type eq 'PTCouple' ? 'selected' : ''}>PT đôi</option>
                                <option value="SingleSession" ${param.type eq 'SingleSession' ? 'selected' : ''}>Buổi lẻ</option>
                            </select>
                            <c:if test="${not empty filterErrors.type}"><span class="field-error"><c:out value="${filterErrors.type}" /></span></c:if>
                        </label>

                        <%-- [LOC] Ô 3: Buổi trong ngày --%>
                        <label>Buổi trong ngày
                            <select name="timeOfDay" class="${not empty filterErrors.timeOfDay ? 'invalid' : ''}">
                                <option value="">Tất cả</option>
                                <option value="morning"   ${param.timeOfDay eq 'morning' ? 'selected' : ''}>Sáng (trước 12:00)</option>
                                <option value="afternoon" ${param.timeOfDay eq 'afternoon' ? 'selected' : ''}>Chiều (12:00–18:00)</option>
                                <option value="evening"   ${param.timeOfDay eq 'evening' ? 'selected' : ''}>Tối (từ 18:00)</option>
                            </select>
                            <c:if test="${not empty filterErrors.timeOfDay}"><span class="field-error"><c:out value="${filterErrors.timeOfDay}" /></span></c:if>
                        </label>

                        <%-- [LOC] Ô 4, 5: Khoảng ngày --%>
                        <label>Từ ngày
                            <input type="date" name="dateFrom" value="${fn:escapeXml(param.dateFrom)}"
                                   class="${not empty filterErrors.dateFrom ? 'invalid' : ''}">
                            <c:if test="${not empty filterErrors.dateFrom}"><span class="field-error"><c:out value="${filterErrors.dateFrom}" /></span></c:if>
                        </label>
                        <label>Đến ngày
                            <input type="date" name="dateTo" value="${fn:escapeXml(param.dateTo)}"
                                   class="${not empty filterErrors.dateTo ? 'invalid' : ''}">
                            <c:if test="${not empty filterErrors.dateTo}"><span class="field-error"><c:out value="${filterErrors.dateTo}" /></span></c:if>
                        </label>

                        <%-- Giữ tab trạng thái Lịch sử đang chọn khi bấm Lọc --%>
                        <c:if test="${not empty sessionFilter.historyStatus}">
                            <input type="hidden" name="historyStatus" value="${sessionFilter.historyStatus}">
                        </c:if>

                        <div class="my-pt-filter-actions">
                            <button class="btn btn-primary btn-sm" type="submit">Lọc lịch tập</button>
                            <a class="btn btn-outline btn-sm" href="${pageUrl}?view=${activeView}" data-clear-session-filters>Xóa lọc</a>
                        </div>
                    </fieldset>
                </form>
            </section>

            <%-- ===================== [SAP-TOI] KHỐI 1: LỊCH TẬP SẮP TỚI ===================== --%>
            <section class="my-pt-panel my-pt-schedule-panel" data-session-panel="upcoming" aria-labelledby="pending-title">
                <div class="my-pt-heading">
                    <div class="my-pt-title-with-hint">
                        <h2 id="pending-title">Lịch tập sắp tới</h2>
                        <span class="pt-info"><button type="button" class="pt-info-trigger" aria-label="Giải thích lịch tập sắp tới" aria-describedby="my-pt-help-1"><span aria-hidden="true">i</span></button><span id="my-pt-help-1" class="pt-info-popover" role="tooltip">Các buổi chưa kết thúc, xếp theo ngày giờ gần nhất. Membership hoặc DayPass hợp lệ cho ngày tập được công nhận là quyền vào gym. Cảnh báo bảo lưu không phải kết quả vắng.</span></span>
                    </div>
                </div>

                <c:choose>
                    <c:when test="${not empty upcomingSessionsError}">
                        <p class="my-pt-package-error" role="alert"><c:out value="${upcomingSessionsError}" /></p>
                    </c:when>
                    <c:when test="${empty upcomingPage.days and sessionFilter.commonFilterActive}">
                        <div class="my-pt-placeholder">
                            <p>Không có buổi phù hợp với bộ lọc.</p>
                            <span>Hãy đổi loại buổi, khoảng ngày hoặc bấm Xóa lọc.</span>
                        </div>
                    </c:when>
                    <c:when test="${empty upcomingPage.days}">
                        <div class="my-pt-placeholder">
                            <p>Bạn không có buổi PT sắp tới.</p>
                            <span>Các buổi đã qua hoặc đã hủy thuộc lịch sử buổi tập.</span>
                        </div>
                    </c:when>
                    <c:otherwise>
                        <div class="my-pt-days">
                            <%-- Vòng ngoài: từng NGÀY --%>
                            <c:forEach var="day" items="${upcomingPage.days}">
                                <div class="my-pt-day ${day.date eq todayDate ? 'is-today' : ''}">
                                    <div class="my-pt-day-heading">
                                        <span class="my-pt-day-label">${day.date eq todayDate ? 'Hôm nay · ' : ''}${gms:formatDate(day.date)}</span>
                                        <span class="my-pt-day-count">${fn:length(day.sessions)} buổi</span>
                                    </div>
                                    <div class="my-pt-day-sessions">
                                        <%-- Vòng trong: từng BUỔI của ngày đó --%>
                                        <c:forEach var="s" items="${day.sessions}">
                                            <%-- Chữ hiện trong hộp thoại Hủy / Đổi --%>
                                            <c:set var="sessionText" value="${gms:formatDate(day.date)} · ${gms:formatTime(s.startTime)}–${gms:formatTime(s.endTime)} · PT ${s.ptName}" />
                                            <article class="my-pt-session">
                                                <div class="my-pt-session-time">
                                                    <p class="my-pt-session-hours"><span>${gms:formatTime(s.startTime)}</span><span class="my-pt-time-separator" aria-hidden="true">–</span><span>${gms:formatTime(s.endTime)}</span></p>

                                                    <%-- [NUT][DOI-BUOI] Nút Đổi buổi: không hiện với buổi lẻ / đang diễn ra.
                                                         Còn lượt → bấm được ; hết lượt / gói hết hiệu lực → KHÓA + ghi lý do (Service.fillActionFlags) --%>
                                                    <c:if test="${not s.inProgress and not s.singleSession}">
                                                        <c:choose>
                                                            <c:when test="${s.canSwap}">
                                                                <button type="button" class="btn btn-outline my-pt-swap-button"
                                                                        data-swap-session-id="${s.sessionId}" data-session-text="${fn:escapeXml(sessionText)}">Đổi buổi</button>
                                                            </c:when>
                                                            <c:otherwise>
                                                                <button type="button" class="btn btn-outline my-pt-swap-button" disabled
                                                                        title="${s.swapNote}">${s.swapNote}</button>
                                                            </c:otherwise>
                                                        </c:choose>
                                                    </c:if>

                                                    <%-- [NUT][HUY-BUOI] Nút Hủy buổi: chỉ khi gói còn Active --%>
                                                    <c:if test="${s.canCancel}">
                                                        <button type="button" class="my-pt-cancel-button"
                                                                data-cancel-session-id="${s.sessionId}" data-session-text="${fn:escapeXml(sessionText)}">
                                                            <i class="fa-solid fa-xmark" aria-hidden="true"></i> Hủy buổi
                                                        </button>
                                                    </c:if>
                                                </div>

                                                <div class="my-pt-session-content">
                                                    <div class="my-pt-session-heading">
                                                        <h3>PT <c:out value="${s.ptName}" /></h3>
                                                        <c:if test="${s.inProgress}"><span class="my-pt-package-type">Đang diễn ra</span></c:if>
                                                    </div>
                                                    <p class="my-pt-caption"><c:out value="${s.packageName}" /> · ${s.typeLabel}</p>
                                                    <c:if test="${not empty s.warning}">
                                                        <p class="my-pt-session-warning"><c:out value="${s.warning}" /></p>
                                                    </c:if>
                                                    <c:if test="${not empty s.packageStartDate or not empty s.partnerName}">
                                                        <details class="my-pt-session-details">
                                                            <summary>Chi tiết buổi tập<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path d="m6 9 6 6 6-6"/></svg></summary>
                                                            <dl class="my-pt-session-facts">
                                                                <c:if test="${not empty s.packageStartDate}">
                                                                    <div><dt>Thời hạn gói</dt><dd>${gms:formatDate(s.packageStartDate)} – ${gms:formatDate(s.packageEndDate)}</dd></div>
                                                                    <div><dt>Mã gói</dt><dd>#${s.subscriptionId}</dd></div>
                                                                </c:if>
                                                                <c:if test="${not empty s.partnerName}">
                                                                    <div><dt>Tập cùng</dt><dd><c:out value="${s.partnerName}" /></dd></div>
                                                                </c:if>
                                                            </dl>
                                                        </details>
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

                <%-- [PHAN-TRANG][SAP-TOI] Phân trang Sắp tới: đổi upcomingPage ±1, giữ bộ lọc (pagingQuery) và trang Lịch sử --%>
                <c:if test="${upcomingPage.totalPages gt 1}">
                    <nav class="my-pt-pagination" aria-label="Phân trang Lịch tập sắp tới">
                        <span>Trang ${upcomingCurrent} / ${upcomingPage.totalPages} · ${upcomingPage.totalDays} ngày có buổi</span>
                        <div class="my-pt-pagination-actions">
                            <c:choose>
                                <c:when test="${upcomingCurrent gt 1}">
                                    <a class="btn btn-outline btn-sm" href="${pageUrl}?${fn:escapeXml(pagingQuery)}upcomingPage=${upcomingCurrent - 1}&amp;historyPage=${historyCurrent}#pending-title">Trước</a>
                                </c:when>
                                <c:otherwise><span class="btn btn-outline btn-sm" aria-disabled="true">Trước</span></c:otherwise>
                            </c:choose>
                            <c:choose>
                                <c:when test="${upcomingCurrent lt upcomingPage.totalPages}">
                                    <a class="btn btn-outline btn-sm" href="${pageUrl}?${fn:escapeXml(pagingQuery)}upcomingPage=${upcomingCurrent + 1}&amp;historyPage=${historyCurrent}#pending-title">Sau</a>
                                </c:when>
                                <c:otherwise><span class="btn btn-outline btn-sm" aria-disabled="true">Sau</span></c:otherwise>
                            </c:choose>
                        </div>
                    </nav>
                </c:if>
            </section>

            <%-- ===================== [LICH-SU] KHỐI 2: LỊCH SỬ BUỔI TẬP ===================== --%>
            <section class="my-pt-panel my-pt-schedule-panel" data-session-panel="history" aria-labelledby="history-title">
                <div class="my-pt-heading">
                    <div class="my-pt-title-with-hint">
                        <h2 id="history-title">Lịch sử buổi tập</h2>
                        <span class="pt-info"><button type="button" class="pt-info-trigger" aria-label="Giải thích lịch sử buổi tập" aria-describedby="my-pt-help-2"><span aria-hidden="true">i</span></button><span id="my-pt-help-2" class="pt-info-popover" role="tooltip">
                                Các buổi tập đã diễn ra và các buổi đã bị hủy.<br>
                                <strong>Kết quả đang cập nhật:</strong> buổi vừa kết thúc, kết quả sẽ có sau khi hết ngày tập.<br>
                                <strong>Đã check-in:</strong> bạn (hoặc người tập cùng, nếu là gói đôi) đã check-in vào gym hôm đó. Đây chỉ là ghi nhận vào cửa, không phải xác nhận đã tập xong với PT.<br>
                                <strong>Vắng / Đã hủy:</strong> lý do ghi ngay dưới từng buổi. Buổi bạn tự hủy vẫn bị trừ khỏi gói và không được hoàn tiền.
                            </span></span>
                    </div>
                </div>

                <%-- [LOC][LICH-SU] 5 tab trạng thái: link GET, giữ bộ lọc chung (filterQuery) + trang Sắp tới --%>
                <c:set var="tabUrl" value="${pageUrl}?${filterQuery}view=history&upcomingPage=${upcomingCurrent}" />
                <c:set var="hs" value="${sessionFilter.historyStatus}" />
                <nav class="my-pt-status-tabs" aria-label="Lọc lịch sử theo trạng thái">
                    <a class="my-pt-status-tab" href="${fn:escapeXml(tabUrl)}"                          ${empty hs ? 'aria-current="true"' : ''}>Tất cả</a>
                    <a class="my-pt-status-tab" href="${fn:escapeXml(tabUrl)}&amp;historyStatus=pending"   ${hs eq 'pending' ? 'aria-current="true"' : ''}>Kết quả đang cập nhật</a>
                    <a class="my-pt-status-tab" href="${fn:escapeXml(tabUrl)}&amp;historyStatus=checkedIn" ${hs eq 'checkedIn' ? 'aria-current="true"' : ''}>Đã check-in</a>
                    <a class="my-pt-status-tab" href="${fn:escapeXml(tabUrl)}&amp;historyStatus=missed"    ${hs eq 'missed' ? 'aria-current="true"' : ''}>Vắng</a>
                    <a class="my-pt-status-tab" href="${fn:escapeXml(tabUrl)}&amp;historyStatus=cancelled" ${hs eq 'cancelled' ? 'aria-current="true"' : ''}>Đã hủy</a>
                </nav>
                <c:if test="${not empty filterErrors.historyStatus}">
                    <p class="field-error my-pt-status-error"><c:out value="${filterErrors.historyStatus}" /></p>
                </c:if>

                <c:choose>
                    <c:when test="${not empty historySessionsError}">
                        <p class="my-pt-package-error" role="alert"><c:out value="${historySessionsError}" /></p>
                    </c:when>
                    <c:when test="${empty historyPage.days and sessionFilter.historyFilterActive}">
                        <div class="my-pt-placeholder">
                            <p>Không có buổi phù hợp với bộ lọc.</p>
                            <span>Hãy chọn trạng thái khác, đổi bộ lọc phía trên hoặc bấm Xóa lọc.</span>
                        </div>
                    </c:when>
                    <c:when test="${empty historyPage.days}">
                        <div class="my-pt-placeholder">
                            <p>Bạn chưa có buổi tập nào trong lịch sử.</p>
                            <span>Buổi đã qua, đã vắng hoặc đã hủy sẽ xuất hiện tại đây.</span>
                        </div>
                    </c:when>
                    <c:otherwise>
                        <div class="my-pt-days">
                            <c:forEach var="day" items="${historyPage.days}">
                                <div class="my-pt-day ${day.date eq todayDate ? 'is-today' : ''}">
                                    <div class="my-pt-day-heading">
                                        <span class="my-pt-day-label">${day.date eq todayDate ? 'Hôm nay · ' : ''}${gms:formatDate(day.date)}</span>
                                        <span class="my-pt-day-count">${fn:length(day.sessions)} buổi</span>
                                    </div>
                                    <div class="my-pt-day-sessions">
                                        <c:forEach var="s" items="${day.sessions}">
                                            <article class="my-pt-session">
                                                <div class="my-pt-session-time">
                                                    <p class="my-pt-session-hours"><span>${gms:formatTime(s.startTime)}</span><span class="my-pt-time-separator" aria-hidden="true">–</span><span>${gms:formatTime(s.endTime)}</span></p>
                                                </div>
                                                <div class="my-pt-session-content">
                                                    <div class="my-pt-session-heading">
                                                        <h3>PT <c:out value="${s.ptName}" /></h3>
                                                        <div class="my-pt-session-status">
                                                            <span class="badge ${s.badgeClass}">${s.statusLabel}</span>
                                                        </div>
                                                    </div>
                                                    <p class="my-pt-caption"><c:out value="${s.packageName}" /> · ${s.typeLabel}</p>
                                                    <details class="my-pt-session-details">
                                                        <summary>Chi tiết buổi tập<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path d="m6 9 6 6 6-6"/></svg></summary>
                                                        <dl class="my-pt-session-facts">
                                                            <c:if test="${not empty s.packageStartDate}">
                                                                <div><dt>Thời hạn gói</dt><dd>${gms:formatDate(s.packageStartDate)} – ${gms:formatDate(s.packageEndDate)}</dd></div>
                                                                <div><dt>Mã gói</dt><dd>#${s.subscriptionId}</dd></div>
                                                            </c:if>
                                                            <c:if test="${not empty s.partnerName}">
                                                                <div><dt>Tập cùng</dt><dd><c:out value="${s.partnerName}" /></dd></div>
                                                            </c:if>
                                                            <c:if test="${not empty s.explanation}">
                                                                <div class="my-pt-session-fact-wide"><dt>Kết quả buổi tập</dt><dd>${s.explanation}</dd></div>
                                                            </c:if>
                                                            <c:if test="${not empty s.cancelledAt}">
                                                                <div><dt>Thời điểm hủy</dt><dd>${gms:formatDateTime(s.cancelledAt)}</dd></div>
                                                            </c:if>
                                                            <c:if test="${not empty s.cancelReason}">
                                                                <div class="my-pt-session-fact-wide"><dt>Lý do đã ghi nhận</dt><dd><c:out value="${s.cancelReason}" /></dd></div>
                                                            </c:if>
                                                        </dl>
                                                    </details>
                                                </div>
                                            </article>
                                        </c:forEach>
                                    </div>
                                </div>
                            </c:forEach>
                        </div>
                    </c:otherwise>
                </c:choose>

                <%-- [PHAN-TRANG][LICH-SU] Phân trang Lịch sử: đổi historyPage ±1, giữ bộ lọc và trang Sắp tới --%>
                <c:if test="${historyPage.totalPages gt 1}">
                    <nav class="my-pt-pagination" aria-label="Phân trang Lịch sử buổi tập">
                        <span>Trang ${historyCurrent} / ${historyPage.totalPages} · ${historyPage.totalDays} ngày có buổi</span>
                        <div class="my-pt-pagination-actions">
                            <c:choose>
                                <c:when test="${historyCurrent gt 1}">
                                    <a class="btn btn-outline btn-sm" href="${pageUrl}?${fn:escapeXml(pagingQuery)}historyPage=${historyCurrent - 1}&amp;upcomingPage=${upcomingCurrent}#history-title">Trước</a>
                                </c:when>
                                <c:otherwise><span class="btn btn-outline btn-sm" aria-disabled="true">Trước</span></c:otherwise>
                            </c:choose>
                            <c:choose>
                                <c:when test="${historyCurrent lt historyPage.totalPages}">
                                    <a class="btn btn-outline btn-sm" href="${pageUrl}?${fn:escapeXml(pagingQuery)}historyPage=${historyCurrent + 1}&amp;upcomingPage=${upcomingCurrent}#history-title">Sau</a>
                                </c:when>
                                <c:otherwise><span class="btn btn-outline btn-sm" aria-disabled="true">Sau</span></c:otherwise>
                            </c:choose>
                        </div>
                    </nav>
                </c:if>
            </section>

            <%-- ===================== [GOI-PT] KHỐI 3: GÓI PT CỦA BẠN ===================== --%>
            <section class="my-pt-panel my-pt-packages-panel" data-session-panel="packages" aria-labelledby="packages-title">
                <div class="my-pt-heading">
                    <div class="my-pt-title-with-hint">
                        <h2 id="packages-title">Gói PT của bạn</h2>
                        <span class="pt-info"><button type="button" class="pt-info-trigger" aria-label="Giải thích thông tin gói PT" aria-describedby="my-pt-help-3"><span aria-hidden="true">i</span></button><span id="my-pt-help-3" class="pt-info-popover" role="tooltip">
                                <strong>Tổng số buổi:</strong> số buổi được tạo khi thanh toán gói.<br>
                                <strong>Buổi sắp tới:</strong> buổi còn trong lịch và chưa bắt đầu; không gồm buổi đang diễn ra, chờ kết quả hoặc đã hủy.<br>
                                <strong>Lượt đổi còn lại:</strong> theo giới hạn lúc mua gói. Với gói đôi, hai thành viên dùng chung số buổi và lượt đổi.
                            </span></span>
                    </div>
                </div>

                <c:choose>
                    <c:when test="${not empty ptPackagesError}">
                        <p class="my-pt-package-error" role="alert"><c:out value="${ptPackagesError}" /></p>
                    </c:when>
                    <c:when test="${empty ptPackages}">
                        <div class="my-pt-placeholder">
                            <p>Bạn chưa có gói PT đang hoạt động.</p>
                            <span>Bạn vẫn có thể đặt buổi PT lẻ.</span>
                        </div>
                    </c:when>
                    <c:otherwise>
                        <div class="my-pt-package-list">
                            <c:forEach var="p" items="${ptPackages}">
                                <article class="my-pt-package-card">
                                    <div class="my-pt-package-heading">
                                        <h3><c:out value="${p.packageName}" /></h3>
                                        <span class="my-pt-package-type">${p.typeLabel}</span>
                                    </div>
                                    <dl class="my-pt-package-details ${not empty p.partnerName ? 'my-pt-package-details-couple' : ''}">
                                        <div><dt>PT phụ trách</dt><dd><c:out value="${p.ptName}" /></dd></div>
                                        <c:if test="${not empty p.partnerName}">
                                            <div><dt>Người tập cùng</dt><dd><c:out value="${p.partnerName}" /></dd></div>
                                        </c:if>
                                        <div><dt>Bắt đầu</dt><dd>${gms:formatDate(p.startDate)}</dd></div>
                                        <div><dt>Hạn gói</dt><dd>${gms:formatDate(p.endDate)}</dd></div>
                                    </dl>
                                    <dl class="my-pt-package-stats">
                                        <div><dt>Tổng số buổi</dt><dd>${p.generatedSessionCount}</dd></div>
                                        <div><dt>Buổi sắp tới</dt><dd>${p.upcomingSessionCount}</dd></div>
                                        <div><dt>Lượt đổi còn lại</dt><dd>${p.remainingSwaps}</dd></div>
                                    </dl>
                                </article>
                            </c:forEach>
                        </div>
                    </c:otherwise>
                </c:choose>
            </section>
        </main>
    </div>
</div>

<%-- ===================== [HUY-BUOI] HỘP THOẠI HỦY BUỔI (form POST action=cancel) =====================
     JS chỉ điền sessionId + dòng mô tả rồi mở hộp thoại. Bấm "Xác nhận hủy" = submit form. --%>
<dialog id="cancelDialog" class="my-pt-cancel-dialog" aria-labelledby="cancelDialogTitle">
    <form method="post" action="${pageUrl}" class="my-pt-cancel-dialog-content">
        <input type="hidden" name="action" value="cancel">
        <input type="hidden" name="csrfToken" value="${fn:escapeXml(csrfToken)}">
        <input type="hidden" name="sessionId">
        <h2 id="cancelDialogTitle">Hủy buổi tập này?</h2>
        <p class="my-pt-cancel-dialog-intro" data-session-text></p>
        <p class="my-pt-cancel-dialog-warning"><strong>Vẫn trừ một buổi trong gói.</strong> Không hoàn tiền và không thể hoàn tác.</p>

        <%-- [HUY-BUOI] Lý do hủy KHÔNG bắt buộc (tối đa 500 ký tự). Có ghi thì PT xem được trong lịch sử. --%>
        <label class="my-pt-swap-slot-label">Lý do hủy (không bắt buộc)
            <textarea name="cancelReason" rows="2" maxlength="500"
                      placeholder="Ví dụ: Em bị ốm, không thể đến tập"></textarea>
        </label>

        <div class="my-pt-dialog-actions">
            <button type="button" class="btn" data-cancel-choice="keep" data-close-dialog>Giữ buổi</button>
            <button type="submit" class="btn" data-cancel-choice="confirm">Xác nhận hủy</button>
        </div>
    </form>
</dialog>

<%-- ===================== [DOI-BUOI] HỘP THOẠI ĐỔI BUỔI (form POST action=swap) =====================
     JS: điền sessionId → gọi AJAX lấy slot → đổ vào <select>. Bấm "Xác nhận đổi" = submit form. --%>
<dialog id="swapDialog" class="my-pt-cancel-dialog my-pt-swap-dialog" aria-labelledby="swapDialogTitle">
    <form method="post" action="${pageUrl}" class="my-pt-cancel-dialog-content">
        <input type="hidden" name="action" value="swap">
        <input type="hidden" name="csrfToken" value="${fn:escapeXml(csrfToken)}">
        <input type="hidden" name="sessionId">
        <input type="hidden" name="originalTimeSlotId">
        <h2 id="swapDialogTitle">Đổi buổi tập</h2>
        <p data-session-text></p>
        <p class="my-pt-swap-policy">Chọn slot thứ 7 hoặc Chủ nhật của cùng PT, bất kỳ tuần nào còn trong hạn gói. Mỗi lần xác nhận sử dụng một lượt đổi.</p>
        <p data-swap-status role="status" aria-live="polite"></p>
        <%-- [DOI-BUOI] Lọc khung giờ (SRS 9.2.4: Time band). Chỉ lọc ở trình duyệt, không gửi lên server. --%>
        <label class="my-pt-swap-slot-label" data-swap-band-label hidden>Buổi trong ngày
            <select data-swap-band>
                <option value="">Tất cả</option>
                <option value="morning">Sáng (trước 12:00)</option>
                <option value="afternoon">Chiều (12:00–18:00)</option>
                <option value="evening">Tối (từ 18:00)</option>
            </select>
        </label>

        <label class="my-pt-swap-slot-label" data-swap-slot-label hidden>Khung giờ mới
            <select name="targetTimeSlotId" required><option value="">Chọn khung giờ</option></select>
        </label>
        <div class="my-pt-dialog-actions">
            <button type="button" class="btn btn-outline" data-close-dialog>Giữ lịch cũ</button>
            <button type="submit" class="btn btn-primary" data-swap-confirm disabled>Xác nhận đổi</button>
        </div>
    </form>
</dialog>
</body>
</html>
