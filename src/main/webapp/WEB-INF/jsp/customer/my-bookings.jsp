<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="gms" uri="/WEB-INF/gms-functions.tld" %>
<%--
  MÀN "GÓI CỦA TÔI" — /customer/bookings?id=<mã gói | single>  (CHỈ XEM)
  booking: MyBookingView do MyBookingService tạo; mã khách lấy từ session, không lấy từ URL.
  Cột trái : 3 tab Đang tập / Đã kết thúc / Buổi lẻ (bindTabs của common.js đổi tab, không tải lại) → bấm thẻ gói = tải lại với ?id=
  Cột phải: thông tin gói + chip lọc trạng thái (JS ẩn/hiện dòng) + bảng buổi #1 → N
  TÌM NHANH: [TAB] [THONG-TIN-GOI] [CHIP-LOC] [BANG]
--%>
<c:set var="pageUrl" value="${pageContext.request.contextPath}/customer/bookings" />
<c:set var="sp" value="${booking.selectedPackage}" />
<%-- Tab mở sẵn = tab chứa thứ đang xem bên phải --%>
<c:set var="openTab" value="${booking.singleSelected ? 'single' : (not empty sp and not sp.active ? 'ended' : 'active')}" />
<!DOCTYPE html>
<html lang="vi">
    <head>
        <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
        <title>Gói của tôi · Super Gym</title>
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/customer/dashboard.css?v=20261013c">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/customer/my-pt-sessions.css?v=20261019f">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/customer/my-bookings.css?v=20261021c">
        <script src="${pageContext.request.contextPath}/js/pages/customer/my-pt-sessions.js?v=20261019e" defer></script>
        <script src="${pageContext.request.contextPath}/js/pages/customer/my-bookings.js?v=20261021c" defer></script>
    </head>
    <body class="dock ${themeClass}">
        <%@ include file="/WEB-INF/jsp/common/flash-toast.jspf" %>
        <div class="app">
            <%@ include file="/WEB-INF/jsp/common/layout-customer.jspf" %>
            <div class="main">
                <c:set var="scheduleTitle" value="Gói của tôi" />
                <%@ include file="_schedule-header.jspf" %>
                <main class="content pt-schedule" id="main-content" tabindex="-1">
                    <c:choose>
                        <c:when test="${not empty bookingError}">
                            <div class="schedule-empty" role="alert">
                                <h3>Chưa tải được gói tập</h3>
                                <p><c:out value="${bookingError}" /></p>
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

                                <%-- ===================== [TAB] cột trái: 3 tab, mỗi tab 1 danh sách ===================== --%>
                                <aside class="booking-sidebar schedule-board" aria-label="Danh sách gói">
                                    <div class="booking-tabs" role="tablist" aria-label="Nhóm gói">
                                        <button type="button" role="tab" id="tab-active" aria-controls="panel-active" data-tab="active" aria-selected="${openTab eq 'active'}">
                                            Đang tập <span>${fn:length(booking.activePackages)}</span>
                                        </button>
                                        <button type="button" role="tab" id="tab-ended" aria-controls="panel-ended" data-tab="ended" aria-selected="${openTab eq 'ended'}">
                                            Đã kết thúc <span>${fn:length(booking.endedPackages)}</span>
                                        </button>
                                        <button type="button" role="tab" id="tab-single" aria-controls="panel-single" data-tab="single" aria-selected="${openTab eq 'single'}">
                                            Buổi lẻ <span>${booking.singleSessionCount}</span>
                                        </button>
                                    </div>

                                    <div class="booking-panel" role="tabpanel" id="panel-active" aria-labelledby="tab-active" data-tab-panel="active" ${openTab eq 'active' ? '' : 'hidden'}>
                                        <c:forEach var="p" items="${booking.activePackages}">
                                            <%@ include file="_booking-package.jspf" %>
                                        </c:forEach>
                                        <c:if test="${empty booking.activePackages}">
                                            <p class="schedule-note booking-panel-empty">Bạn không có gói PT nào đang tập. Mua gói mới ở quầy lễ tân.</p>
                                        </c:if>
                                    </div>

                                    <div class="booking-panel" role="tabpanel" id="panel-ended" aria-labelledby="tab-ended" data-tab-panel="ended" ${openTab eq 'ended' ? '' : 'hidden'}>
                                        <c:forEach var="p" items="${booking.endedPackages}">
                                            <%@ include file="_booking-package.jspf" %>
                                        </c:forEach>
                                        <c:if test="${empty booking.endedPackages}">
                                            <p class="schedule-note booking-panel-empty">Chưa có gói nào kết thúc.</p>
                                        </c:if>
                                    </div>

                                    <div class="booking-panel" role="tabpanel" id="panel-single" aria-labelledby="tab-single" data-tab-panel="single" ${openTab eq 'single' ? '' : 'hidden'}>
                                        <c:choose>
                                            <c:when test="${booking.singleSessionCount gt 0}">
                                                <a class="booking-package" href="${pageUrl}?id=single" aria-current="${booking.singleSelected ? 'page' : 'false'}">
                                                    <strong class="booking-package-name">Tất cả buổi lẻ</strong>
                                                    <span class="booking-package-meta">${booking.singleSessionCount} buổi · mỗi buổi đặt riêng, có thể khác PT</span>
                                                </a>
                                            </c:when>
                                            <c:otherwise>
                                                <p class="schedule-note booking-panel-empty">Bạn chưa đặt buổi lẻ nào.</p>
                                            </c:otherwise>
                                        </c:choose>
                                    </div>
                                </aside>

                                <section class="booking-content schedule-board" aria-labelledby="booking-heading">
                                    <%-- ===================== [THONG-TIN-GOI] ===================== --%>
                                    <div class="booking-overview">
                                        <c:choose>
                                            <c:when test="${booking.singleSelected}">
                                                <h3 id="booking-heading">Buổi lẻ</h3>
                                                <p class="booking-sub">Mỗi buổi đặt riêng và có thể với PT khác nhau. Bấm "Chi tiết" ở từng dòng để liên hệ PT.</p>
                                            </c:when>
                                            <c:otherwise>
                                                <div class="booking-title">
                                                    <h3 id="booking-heading"><c:out value="${sp.packageName}" /></h3>
                                                    <span class="booking-state ${sp.active ? 'is-active' : 'is-ended'}">${sp.active ? 'Đang tập' : 'Đã kết thúc'}</span>
                                                </div>
                                                <p class="booking-sub">
                                                    <c:out value="${sp.typeLabel}" /> · ${gms:formatDate(sp.startDate)} – ${gms:formatDate(sp.endDate)}
                                                    <c:if test="${not empty sp.partnerName}"> · Tập cùng <strong><c:out value="${sp.partnerName}" /></strong></c:if>
                                                </p>
                                                <div class="booking-pt">
                                                    <span>PT <strong><c:out value="${sp.ptName}" /></strong><c:if test="${not empty sp.ptPhone}"> · <c:out value="${sp.ptPhone}" /></c:if></span>
                                                    <c:if test="${not empty sp.zaloUrl}"><a class="btn btn-primary btn-sm" href="${fn:escapeXml(sp.zaloUrl)}" target="_blank" rel="noopener noreferrer">Nhắn Zalo</a></c:if>
                                                    <c:if test="${not empty sp.ptPhone}"><button class="btn btn-outline btn-sm" type="button" data-copy-phone="${fn:escapeXml(sp.ptPhone)}">Sao chép số</button></c:if>
                                                </div>
                                                <div class="booking-progress">
                                                    <progress value="${sp.pastSessions}" max="${sp.totalSessions gt 0 ? sp.totalSessions : 1}" aria-label="Tiến độ gói"></progress>
                                                    <span>${sp.pastSessions}/${sp.totalSessions} buổi đã qua<c:if test="${sp.active}"> · <strong>còn ${sp.totalSessions - sp.pastSessions} buổi</strong></c:if></span>
                                                </div>
                                            </c:otherwise>
                                        </c:choose>
                                    </div>

                                    <%-- ===================== [CHIP-LOC] số do Service đếm; JS chỉ ẩn/hiện dòng ===================== --%>
                                    <%-- Chip = 0 thì khóa (bấm chỉ ra bảng rỗng). "Sắp tới" = Hôm nay + Sắp tới + Cần chú ý. --%>
                                    <div class="booking-chips" role="group" aria-label="Lọc buổi theo trạng thái" hidden data-session-filters>
                                        <button type="button" data-filter="all" aria-pressed="true">Tất cả <b>${booking.totalCount}</b></button>
                                        <button type="button" data-filter="remaining" data-tone="upcoming" aria-pressed="false" ${booking.remainingCount eq 0 ? 'disabled' : ''}>Sắp tới <b>${booking.remainingCount}</b></button>
                                        <button type="button" data-filter="checkedIn" data-tone="checkedIn" aria-pressed="false" ${booking.checkedInCount eq 0 ? 'disabled' : ''}>Đã check-in <b>${booking.checkedInCount}</b></button>
                                        <button type="button" data-filter="missed" data-tone="missed" aria-pressed="false" ${booking.missedCount eq 0 ? 'disabled' : ''}>Vắng <b>${booking.missedCount}</b></button>
                                        <button type="button" data-filter="cancelled" data-tone="cancelled" aria-pressed="false" ${booking.cancelledCount eq 0 ? 'disabled' : ''}>Đã hủy <b>${booking.cancelledCount}</b></button>
                                        <c:if test="${booking.pendingCount gt 0}">
                                            <button type="button" data-filter="pending" data-tone="pending" aria-pressed="false">Đang cập nhật <b>${booking.pendingCount}</b></button>
                                        </c:if>
                                        <span class="pt-info"><button type="button" class="pt-info-trigger" aria-label="Giải thích các trạng thái" aria-describedby="hint-chips"><span aria-hidden="true">i</span></button><span id="hint-chips" class="pt-info-popover" role="tooltip">
                                            <strong>Sắp tới:</strong> buổi chưa diễn ra, gồm cả buổi Cần chú ý (phòng nghỉ hoặc chưa có quyền vào gym).<br>
                                            <strong>Đã check-in:</strong> bạn đã vào gym ngày đó.<br>
                                            <strong>Vắng:</strong> không check-in, buổi vẫn tính vào gói.<br>
                                            <strong>Đang cập nhật:</strong> buổi vừa kết thúc, kết quả có sau khi hết ngày.
                                        </span></span>
                                    </div>

                                    <%-- ===================== [BANG] buổi #1 → N (buổi lẻ: không có #, thêm cột PT) ===================== --%>
                                    <c:set var="isSingle" value="${booking.singleSelected}" />
                                    <%-- Cả 2 kiểu bảng đều 5 cột: gói (# Ngày Giờ TrạngThái ChiTiết), buổi lẻ (Ngày Giờ PT TrạngThái ChiTiết). --%>
                                    <c:set var="colCount" value="5" />
                                    <div class="booking-result-row">
                                        <p class="schedule-note booking-result" id="booking-result" role="status" aria-live="polite">${booking.totalCount} buổi · xếp theo ngày tập</p>
                                        <%-- Bảng tự cuộn tới "Buổi tới" → buổi đã qua bị khuất phía trên; nút này báo và cuộn lên. JS điền số. --%>
                                        <button type="button" class="booking-jump" data-jump-past hidden></button>
                                    </div>
                                    <div class="booking-table-scroll" tabindex="0" role="region" aria-label="Danh sách buổi, có thể cuộn">
                                        <table class="booking-table" id="booking-sessions" data-focus-session="${booking.focusSessionId}">
                                            <caption class="visually-hidden">Các buổi theo thứ tự ngày giờ · Giờ Việt Nam</caption>
                                            <thead>
                                                <tr>
                                                    <c:if test="${not isSingle}"><th scope="col" class="booking-col-no">#</th></c:if>
                                                    <th scope="col">Ngày</th>
                                                    <th scope="col">Giờ</th>
                                                    <c:if test="${isSingle}"><th scope="col">PT</th></c:if>
                                                    <th scope="col">Trạng thái</th>
                                                    <th scope="col"><span class="visually-hidden">Chi tiết</span></th>
                                                </tr>
                                            </thead>
                                            <tbody>
                                                <c:forEach var="s" items="${booking.sessions}">
                                                    <c:set var="isFocus" value="${s.sessionId eq booking.focusSessionId}" />
                                                    <%-- Vạch ngăn "Hôm nay": trước buổi đầu tiên chưa kết thúc, nếu phía trên đã có buổi đã qua. --%>
                                                    <c:if test="${not s.ended and not dividerShown}">
                                                        <c:if test="${hasEnded}">
                                                            <tr class="booking-divider" data-divider><td colspan="${colCount}"><span>Hôm nay · các buổi sắp tới</span></td></tr>
                                                        </c:if>
                                                        <c:set var="dividerShown" value="${true}" />
                                                    </c:if>
                                                    <c:if test="${s.ended}"><c:set var="hasEnded" value="${true}" /></c:if>
                                                    <tr data-session-row data-session-id="${s.sessionId}" data-status="${s.statusKey}" class="${isFocus ? 'is-focus' : ''} ${s.ended ? 'is-ended' : ''}">
                                                        <c:if test="${not isSingle}"><td class="booking-col-no">${s.sessionNo}</td></c:if>
                                                        <td>
                                                            <strong><c:out value="${s.dayName}" /></strong>, ${gms:formatDate(s.slotDate)}
                                                            <c:if test="${isFocus}"><span class="booking-next">Buổi tới</span></c:if>
                                                        </td>
                                                        <td class="booking-col-time">${gms:formatTime(s.startTime)} – ${gms:formatTime(s.endTime)}</td>
                                                        <c:if test="${isSingle}"><td><c:out value="${s.ptName}" /></td></c:if>
                                                        <td>
                                                            <span class="session-status" data-status="${s.statusKey}"><c:out value="${s.statusLabel}" /></span>
                                                            <%-- Lý do chỉ hiện khi buổi có vấn đề; câu chung của các trạng thái khác nằm trong Chi tiết. --%>
                                                            <c:if test="${not empty s.explanation and (s.statusKey eq 'warn' or s.statusKey eq 'missed' or s.statusKey eq 'cancelled')}">
                                                                <small class="booking-reason"><c:out value="${s.explanation}" /></small>
                                                            </c:if>
                                                        </td>
                                                        <td class="booking-col-detail">
                                                            <%-- initSessionDetails (my-pt-sessions.js) nối summary → mở dialog chi tiết dùng chung với Lịch tập. --%>
                                                            <details class="booking-session-detail" data-session-detail>
                                                                <summary>Chi tiết</summary>
                                                                <div data-session-content>
                                                                    <%@ include file="_session-detail.jspf" %>
                                                                </div>
                                                            </details>
                                                        </td>
                                                    </tr>
                                                </c:forEach>
                                                <tr id="session-filter-empty" hidden>
                                                    <td colspan="${colCount}">Không có buổi nào ở trạng thái này.</td>
                                                </tr>
                                                <c:if test="${empty booking.sessions}">
                                                    <tr><td colspan="${colCount}">Gói này chưa có buổi tập.</td></tr>
                                                </c:if>
                                            </tbody>
                                        </table>
                                    </div>
                                    <p class="schedule-note booking-foot">Cần hủy hoặc đổi buổi? Nhắn PT qua Zalo, PT sẽ cập nhật lịch giúp bạn.</p>
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
