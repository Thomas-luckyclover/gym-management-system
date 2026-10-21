<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="gms" uri="/WEB-INF/gms-functions.tld" %>
<%--
  MÀN "GÓI CỦA TÔI" — /customer/bookings?id=<mã gói | single>  (CHỈ XEM)
  booking: MyBookingView do MyBookingService tạo; mã khách lấy từ session, không lấy từ URL.
  Cột trái : 3 tab Đang tập / Đã kết thúc / Buổi lẻ (bindTabs của common.js đổi tab, không tải lại) → bấm thẻ gói = tải lại với ?id=
  Cột phải: thông tin gói + thẻ "Buổi tới" + chip lọc (JS ẩn/hiện dòng) + bảng buổi #1 → N, buổi đã qua gập lại
  TÌM NHANH: [TAB] [THONG-TIN-GOI] [BUOI-TOI] [CHIP-LOC] [BANG]
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
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/customer/my-bookings.css?v=20261021e">
        <script src="${pageContext.request.contextPath}/js/pages/customer/my-pt-sessions.js?v=20261019e" defer></script>
        <script src="${pageContext.request.contextPath}/js/pages/customer/my-bookings.js?v=20261021e" defer></script>
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
                                    <div class="booking-sidebar-head">
                                        <h2>Gói của bạn</h2>
                                        <span class="pt-info"><button type="button" class="pt-info-trigger" aria-label="Giải thích các nhóm gói" aria-describedby="hint-tabs"><span aria-hidden="true">i</span></button><span id="hint-tabs" class="pt-info-popover" role="tooltip"><strong>Đang tập:</strong> gói PT còn hạn.<br><strong>Đã kết thúc:</strong> gói hết hạn hoặc đã dùng hết buổi.<br><strong>Buổi lẻ:</strong> các buổi bạn đặt riêng từng buổi, không thuộc gói nào.</span></span>
                                    </div>
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
                                    <%-- ===================== [THONG-TIN-GOI] tên gói + trạng thái gói ===================== --%>
                                    <div class="booking-overview">
                                        <c:choose>
                                            <c:when test="${booking.singleSelected}">
                                                <h3 id="booking-heading">Buổi lẻ</h3>
                                                <p class="booking-sub">Mỗi buổi đặt riêng và có thể với PT khác nhau.</p>
                                            </c:when>
                                            <c:otherwise>
                                                <div class="booking-title">
                                                    <h3 id="booking-heading"><c:out value="${sp.packageName}" /></h3>
                                                    <span class="booking-state ${sp.active ? 'is-active' : 'is-ended'}">${sp.active ? 'Đang tập' : 'Đã kết thúc'}</span>
                                                </div>
                                                <p class="booking-sub">
                                                    <c:out value="${sp.typeLabel}" /> · ${gms:formatDate(sp.startDate)} – ${gms:formatDate(sp.endDate)} · PT <strong><c:out value="${sp.ptName}" /></strong>
                                                    <c:if test="${not empty sp.partnerName}"> · Tập cùng <strong><c:out value="${sp.partnerName}" /></strong></c:if>
                                                </p>
                                            </c:otherwise>
                                        </c:choose>
                                    </div>

                                    <%-- ===================== [BUOI-TOI] thẻ nổi bật: buổi gần nhất + liên hệ PT + vòng tiến độ ===================== --%>
                                    <%-- f = buổi Scheduled đầu tiên chưa kết thúc (MyBookingService.countSessions); null → hiện tóm tắt thay thế. --%>
                                    <c:set var="f" value="${booking.focusSession}" />
                                    <div class="booking-hero ${empty f ? 'is-quiet' : ''} ${not empty f and f.statusKey eq 'warn' ? 'is-warn' : ''}">
                                        <div class="booking-hero-main">
                                            <c:choose>
                                                <c:when test="${not empty f}">
                                                    <p class="booking-hero-label">Buổi tới ·
                                                        <c:choose>
                                                            <c:when test="${booking.daysUntilFocus eq 0}">hôm nay</c:when>
                                                            <c:when test="${booking.daysUntilFocus eq 1}">ngày mai</c:when>
                                                            <c:otherwise>còn ${booking.daysUntilFocus} ngày</c:otherwise>
                                                        </c:choose>
                                                    </p>
                                                    <p class="booking-hero-when"><c:out value="${f.dayName}" />, ${gms:formatDate(f.slotDate)} · ${gms:formatTime(f.startTime)} – ${gms:formatTime(f.endTime)}</p>
                                                    <p class="booking-hero-sub">PT <strong><c:out value="${f.ptName}" /></strong><c:if test="${not empty f.ptPhone}"> · <c:out value="${f.ptPhone}" /></c:if>
                                                        · <c:choose><c:when test="${f.singleSession}">Buổi lẻ</c:when><c:otherwise>Buổi ${f.sessionNo}/${f.totalSessions}</c:otherwise></c:choose></p>
                                                    <%-- Cần chú ý (phòng nghỉ / chưa có quyền vào gym): nói lý do ngay trên thẻ. --%>
                                                    <c:if test="${f.statusKey eq 'warn' and not empty f.explanation}">
                                                        <p class="booking-hero-warn"><c:out value="${f.explanation}" /></p>
                                                    </c:if>
                                                    <div class="booking-hero-actions">
                                                        <c:if test="${not empty f.zaloUrl}"><a class="btn btn-primary btn-sm" href="${fn:escapeXml(f.zaloUrl)}" target="_blank" rel="noopener noreferrer">Nhắn Zalo PT</a></c:if>
                                                        <c:if test="${not empty f.ptPhone}"><button class="btn btn-outline btn-sm" type="button" data-copy-phone="${fn:escapeXml(f.ptPhone)}">Sao chép số</button></c:if>
                                                        <c:if test="${not empty f.calendarUrl}"><a class="btn btn-outline btn-sm" href="${fn:escapeXml(f.calendarUrl)}" target="_blank" rel="noopener noreferrer">+ Google Calendar</a></c:if>
                                                        <a class="btn btn-ghost btn-sm" href="${pageContext.request.contextPath}/customer/pt-sessions?view=week&amp;date=${f.slotDate}">Xem trên lịch tập ›</a>
                                                    </div>
                                                </c:when>
                                                <c:when test="${booking.singleSelected}">
                                                    <p class="booking-hero-label">Buổi tới</p>
                                                    <p class="booking-hero-when">Chưa có buổi lẻ sắp tới</p>
                                                    <div class="booking-hero-actions"><a class="btn btn-primary btn-sm" href="${pageContext.request.contextPath}/customer/book-session">Đặt buổi lẻ</a></div>
                                                </c:when>
                                                <c:otherwise>
                                                    <p class="booking-hero-label">${sp.active ? 'Buổi tới' : 'Gói đã kết thúc'}</p>
                                                    <p class="booking-hero-when"><c:choose><c:when test="${sp.active}">Không còn buổi nào sắp tới</c:when><c:otherwise>Kết thúc ngày ${gms:formatDate(sp.endDate)}</c:otherwise></c:choose></p>
                                                    <p class="booking-hero-sub">Đã tập cùng PT <strong><c:out value="${sp.ptName}" /></strong> · ${booking.checkedInCount} buổi check-in, ${booking.missedCount} vắng, ${booking.cancelledCount} hủy.</p>
                                                </c:otherwise>
                                            </c:choose>
                                        </div>
                                        <%-- Vòng tiến độ (chỉ gói PT): --p = % đã qua, vẽ bằng conic-gradient trong CSS. --%>
                                        <c:if test="${not empty sp}">
                                            <div class="booking-ring" style="--p: ${sp.progressPercent}" role="img" aria-label="Đã qua ${sp.pastSessions} trên ${sp.totalSessions} buổi">
                                                <div>
                                                    <b>${sp.pastSessions}/${sp.totalSessions}</b>
                                                    <span>buổi đã qua</span>
                                                    <c:if test="${sp.active}"><span class="booking-ring-rest">còn ${sp.totalSessions - sp.pastSessions}</span></c:if>
                                                </div>
                                            </div>
                                            <span class="pt-info booking-ring-hint"><button type="button" class="pt-info-trigger" aria-label="Cách tính tiến độ" aria-describedby="hint-progress"><span aria-hidden="true">i</span></button><span id="hint-progress" class="pt-info-popover" role="tooltip"><strong>Đã qua:</strong> số buổi có ngày tập trước hôm nay, tính cả buổi vắng và buổi bị hủy.<br><strong>Còn:</strong> số buổi từ hôm nay trở đi.</span></span>
                                        </c:if>
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
                                    <p class="schedule-note booking-result"><span id="booking-result" role="status" aria-live="polite">${booking.totalCount} buổi · xếp theo ngày tập</span>
                                        <span class="pt-info"><button type="button" class="pt-info-trigger" aria-label="Cách đọc bảng buổi" aria-describedby="hint-table"><span aria-hidden="true">i</span></button><span id="hint-table" class="pt-info-popover" role="tooltip"><strong>#:</strong> thứ tự buổi trong gói theo ngày tập.<br><strong>Buổi đã qua:</strong> được gập lại ở đầu bảng, bấm để mở xem lịch sử.<br><strong>Buổi tới:</strong> buổi gần nhất bạn cần đi tập.<br><strong>Chi tiết:</strong> xem PT, lý do và thêm vào Google Calendar.</span></span></p>
                                    <div class="booking-table-scroll" tabindex="0" role="region" aria-label="Danh sách buổi, có thể cuộn">
                                        <table class="booking-table" id="booking-sessions">
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
                                                <%-- Dòng gập "▸ N buổi đã qua": JS gập các dòng is-ended và điền tóm tắt; không có JS thì dòng này ẩn, bảng hiện đủ. --%>
                                                <c:if test="${booking.endedCount gt 0}">
                                                    <tr class="booking-fold" data-past-fold hidden>
                                                        <td colspan="${colCount}">
                                                            <button type="button" data-toggle-past aria-expanded="false" aria-controls="booking-sessions">
                                                                <span class="booking-fold-icon" aria-hidden="true">▸</span>
                                                                <b>${booking.endedCount} buổi đã qua</b>
                                                                <span class="booking-fold-summary" data-past-summary></span>
                                                                <span class="booking-fold-action" data-past-action>Hiện</span>
                                                            </button>
                                                        </td>
                                                    </tr>
                                                </c:if>
                                                <c:forEach var="s" items="${booking.sessions}">
                                                    <c:set var="isFocus" value="${s.sessionId eq booking.focusSessionId}" />
                                                    <tr data-session-row data-status="${s.statusKey}" data-status-label="${fn:escapeXml(s.statusLabel)}" class="${isFocus ? 'is-focus' : ''} ${s.ended ? 'is-ended' : ''}">
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
