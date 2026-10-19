<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="gms" uri="/WEB-INF/gms-functions.tld" %>
<c:set var="pageUrl" value="${pageContext.request.contextPath}/customer/bookings" />
<!DOCTYPE html>
<html lang="vi">
    <head>
        <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
        <title>Gói của tôi · Super Gym</title>
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/customer/dashboard.css?v=20261013c">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/customer/my-pt-sessions.css?v=20261019f">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/customer/my-bookings.css?v=20261019d">
        <script src="${pageContext.request.contextPath}/js/pages/customer/my-pt-sessions.js?v=20261019e" defer>
        </script>
        <script src="${pageContext.request.contextPath}/js/pages/customer/my-bookings.js?v=20261019c" defer>
        </script>
    </head>
    <body class="dock ${themeClass}">
        <%@ include file="/WEB-INF/jsp/common/flash-toast.jspf" %>
        <div class="app">
            <%@ include file="/WEB-INF/jsp/common/layout-customer.jspf" %>
            <div class="main">
                <c:set var="scheduleTitle" value="Gói của tôi" />
                <%@ include file="_schedule-header.jspf" %>
                <main class="content pt-schedule" id="main-content" tabindex="-1">
                    <%-- booking: MyBookingView do MyBookingService tạo; không lấy mã khách từ URL. --%>
                    <c:choose>
                        <c:when test="${not empty bookingError}">
                            <div class="schedule-empty" role="alert">
                                <h3>Chưa tải được gói tập</h3>
                                <p>
                                    <c:out value="${bookingError}" />
                                </p>
                                <a class="btn btn-outline" href="${pageUrl}">Tải lại</a>
                            </div>
                        </c:when>
                        <c:when test="${booking.noData}">
                            <div class="schedule-empty">
                                <h3>Bạn chưa có gói PT hoặc buổi lẻ</h3>
                                <p>Liên hệ lễ tân để mua gói PT hoặc chọn một buổi tập lẻ.</p>
                                <a class="btn btn-primary" href="${pageContext.request.contextPath}/customer/book-session">Đặt buổi lẻ</a>
                            </div>
                        </c:when>
                        <c:otherwise>
                            <div class="booking-layout">
                                <aside class="booking-sidebar schedule-board" aria-label="Danh sách gói">
                                    <h3>Gói PT &amp; buổi lẻ</h3>
                                    <div class="booking-package-filters" hidden data-package-filters>
                                        <label>Tình trạng<select id="package-state">
                                                <option value="all">Tất cả</option>
                                                <option value="active">Đang tập</option>
                                                <option value="ended">Đã kết thúc</option>
                                                <option value="single">Buổi lẻ</option>
                                            </select>
                                        </label>
                                        <label>Loại gói<select id="package-type">
                                                <option value="all">Tất cả</option>
                                                <option value="personal">PT cá nhân</option>
                                                <option value="couple">PT đôi</option>
                                                <option value="single">Buổi lẻ</option>
                                            </select>
                                        </label>
                                    </div>
                                    <div data-package-group>
                                        <h4>Đang tập <span>${fn:length(booking.activePackages)}</span>
                                        </h4>
                                        <c:forEach var="p" items="${booking.activePackages}">
                                            <%@ include file="_booking-package.jspf" %>
                                        </c:forEach>
                                    </div>
                                    <div data-package-group>
                                        <h4>Đã kết thúc <span>${fn:length(booking.endedPackages)}</span>
                                        </h4>
                                        <c:forEach var="p" items="${booking.endedPackages}">
                                            <%@ include file="_booking-package.jspf" %>
                                        </c:forEach>
                                    </div>
                                    <c:if test="${booking.singleSessionCount gt 0}">
                                        <div data-package-group>
                                            <h4>Buổi lẻ</h4>
                                            <a class="booking-package" data-package-item data-package-state="single" data-package-type="single" href="${pageUrl}?id=single" aria-current="${booking.singleSelected ? 'page' : 'false'}">
                                                <strong>Các buổi lẻ</strong>
                                                <span>${booking.singleSessionCount} buổi đã đặt</span>
                                            </a>
                                        </div>
                                    </c:if>
                                    <p class="schedule-note" id="package-filter-empty" hidden>Không có gói phù hợp. Hãy chọn lại bộ lọc.</p>
                                </aside>
                                <section class="booking-content schedule-board" aria-label="Chi tiết gói và danh sách buổi">
                                    <div class="booking-overview">
                                        <c:choose>
                                            <c:when test="${booking.singleSelected}">
                                                <h3>Các buổi lẻ</h3>
                                                <p>Mỗi buổi có PT và thời gian riêng. Chọn Chi tiết để xem thông tin.</p>
                                            </c:when>
                                            <c:otherwise>
                                                <c:set var="sp" value="${booking.selectedPackage}" />
                                                <div class="booking-title">
                                                    <h3>
                                                        <c:out value="${sp.packageName}" />
                                                    </h3>
                                                    <span class="session-status" data-status="${sp.active ? 'upcoming' : 'cancelled'}">${sp.active ? 'Đang tập' : 'Đã kết thúc'}</span>
                                                </div>
                                                <p>
                                                    <c:out value="${sp.typeLabel}" /> · ${gms:formatDate(sp.startDate)} – ${gms:formatDate(sp.endDate)} · ${sp.totalSessions} buổi</p>
                                                <p>PT <strong>
                                                        <c:out value="${sp.ptName}" />
                                                    </strong>
                                                    <c:if test="${not empty sp.partnerName}"> · Tập cùng <c:out value="${sp.partnerName}" />
                                                    </c:if>
                                                </p>
                                                <c:if test="${not empty sp.ptPhone}">
                                                    <div class="session-actions">
                                                        <span>
                                                            <c:out value="${sp.ptPhone}" />
                                                        </span>
                                                        <c:if test="${not empty sp.zaloUrl}">
                                                            <a class="btn btn-outline btn-sm" href="${fn:escapeXml(sp.zaloUrl)}" target="_blank" rel="noopener noreferrer">Nhắn Zalo</a>
                                                        </c:if>
                                                        <button class="btn btn-outline btn-sm" type="button" data-copy-phone="${fn:escapeXml(sp.ptPhone)}">Sao chép số</button>
                                                    </div>
                                                </c:if>
                                            </c:otherwise>
                                        </c:choose>
                                    </div>
                                    <%-- Các ô đếm do Service tính; JS filterSessions chỉ ẩn/hiện các hàng đã tải. --%>
                                    <div class="booking-counts" role="group" aria-label="Lọc trạng thái buổi" hidden data-session-filters>
                                        <button type="button" data-filter="all" aria-pressed="true">
                                            <span>Tất cả</span>
                                            <strong>${booking.totalCount}</strong>
                                        </button>
                                        <button type="button" data-filter="checkedIn" aria-pressed="false">
                                            <span>Đã check-in</span>
                                            <strong>${booking.checkedInCount}</strong>
                                        </button>
                                        <button type="button" data-filter="missed" aria-pressed="false">
                                            <span>Vắng</span>
                                            <strong>${booking.missedCount}</strong>
                                        </button>
                                        <button type="button" data-filter="cancelled" aria-pressed="false">
                                            <span>Đã hủy</span>
                                            <strong>${booking.cancelledCount}</strong>
                                        </button>
                                        <button type="button" data-filter="remaining" aria-pressed="false">
                                            <span>Còn lại</span>
                                            <strong>${booking.remainingCount}</strong>
                                        </button>
                                        <c:if test="${booking.pendingCount gt 0}">
                                            <button type="button" data-filter="pending" aria-pressed="false">
                                                <span>Đang cập nhật</span>
                                                <strong>${booking.pendingCount}</strong>
                                            </button>
                                        </c:if>
                                    </div>
                                    <div class="booking-table-scroll" tabindex="0" role="region" aria-label="Danh sách buổi, có thể cuộn">
                                        <table class="booking-table" id="booking-sessions" data-focus-session="${booking.focusSessionId}">
                                            <caption class="visually-hidden">Các buổi theo thứ tự ngày giờ · Giờ Việt Nam</caption>
                                            <thead>
                                                <tr>
                                                    <th scope="col">Buổi</th>
                                                    <th scope="col">Ngày / giờ</th>
                                                    <th scope="col">Trạng thái</th>
                                                    <th scope="col">Thông tin</th>
                                                </tr>
                                            </thead>
                                            <tbody>
                                                <c:forEach var="s" items="${booking.sessions}" varStatus="number">
                                                    <tr data-session-row data-session-id="${s.sessionId}" data-status="${s.statusKey}" class="${s.sessionId eq booking.focusSessionId ? 'is-focus' : ''}">
                                                        <td>
                                                            <strong>#${s.singleSession ? number.count : s.sessionNo}</strong>
                                                        </td>
                                                        <td>
                                                            <strong>${gms:formatDate(s.slotDate)}</strong>
                                                            <span>${gms:formatTime(s.startTime)} – ${gms:formatTime(s.endTime)}</span>
                                                        </td>
                                                        <td>
                                                            <span class="session-status" data-status="${s.statusKey}">
                                                                <c:out value="${s.statusLabel}" />
                                                            </span>
                                                        </td>
                                                        <td>
                                                            <span>PT <c:out value="${s.ptName}" />
                                                            </span>
                                                            <c:if test="${not empty s.explanation}">
                                                                <small>
                                                                    <c:out value="${s.explanation}" />
                                                                </small>
                                                            </c:if>
                                                            <details class="booking-session-detail" data-session-detail>
                                                                <summary>Chi tiết buổi tập</summary>
                                                                <div data-session-content>
                                                                    <%@ include file="_session-detail.jspf" %>
                                                                </div>
                                                            </details>
                                                        </td>
                                                    </tr>
                                                </c:forEach>
                                                <tr id="session-filter-empty" hidden>
                                                    <td colspan="4">Không có buổi phù hợp với trạng thái đã chọn.</td>
                                                </tr>
                                                <c:if test="${empty booking.sessions}">
                                                    <tr>
                                                        <td colspan="4">Gói này chưa có buổi tập.</td>
                                                    </tr>
                                                </c:if>
                                            </tbody>
                                        </table>
                                    </div>
                                    <p class="schedule-note booking-result" id="booking-result" role="status" aria-live="polite">${booking.totalCount} buổi</p>
                                </section>
                            </div>
                        </c:otherwise>
                    </c:choose>
                    <%@ include file="_session-drawer.jspf" %>
                </main>
            </div>
        </div>
    </body>
</html>
