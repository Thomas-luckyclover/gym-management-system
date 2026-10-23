<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="gms" uri="/WEB-INF/gms-functions.tld" %>
<%--
  MÀN "GÓI CỦA TÔI" — /customer/bookings?id=<mã gói | single>  (CHỈ XEM)
  booking: MyBookingView do MyBookingService tạo; mã khách lấy từ session, không lấy từ URL.
  Cột trái : 3 tab Đang tập / Đã kết thúc / Buổi lẻ (bindTabs của common.js) + lối đi nhanh
  Cột phải: KHÔNG liệt kê hết các buổi (xem đủ → màn Lịch tập). Gồm:
            [DAU-GOI] tên gói + buổi tới + tiến độ · [CTA] tối đa 1 lời mời hành động
            [LICH-CO-DINH] tuần mẫu T2–T6 + buổi khác giờ · [CAC-BUOI] dải chấm · [CAN-DE-Y] cảnh báo buổi sắp tới
            Buổi lẻ: [BUOI-LE] danh sách ngắn + "Đặt lại với PT"
  Lời giải thích dài để trong nút (i) (.pt-info, common.js bindHintBoxes), không rải chữ phụ trên màn.
--%>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<c:set var="pageUrl" value="${ctx}/customer/bookings" />
<c:set var="sp" value="${booking.selectedPackage}" />
<c:set var="f" value="${booking.focusSession}" />
<c:set var="cta" value="${booking.ctaKind}" />
<%-- Tab mở sẵn = tab chứa thứ đang xem bên phải --%>
<%-- Link sang Lịch tập tuần của 1 buổi: tô ngày đó + mở sẵn chi tiết (?session=) + nút quay về đúng gói (?from=). Nối thêm date + session. --%>
<c:set var="fromKey" value="${booking.singleSelected ? 'single' : sp.subscriptionId}" />
<c:set var="weekLink" value="${ctx}/customer/pt-sessions?view=week&amp;from=${fromKey}" />
<c:set var="openTab" value="${booking.singleSelected ? 'single' : (not empty sp and not sp.active ? 'ended' : 'active')}" />
<!DOCTYPE html>
<html lang="vi">
    <head>
        <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
        <title>Gói của tôi · Super Gym</title>
        <link rel="stylesheet" href="${ctx}/css/pages/customer/dashboard.css?v=20261013c">
        <link rel="stylesheet" href="${ctx}/css/pages/customer/my-pt-sessions.css?v=20261019f">
        <link rel="stylesheet" href="${ctx}/css/pages/customer/my-bookings.css?v=20261023a">
        <link rel="stylesheet" href="${ctx}/css/pages/auth/notification-bell.css">
        <script src="${ctx}/js/pages/auth/notification-bell.js" defer></script>
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
                                <p>Đặt một buổi lẻ online, hoặc đăng ký gói PT tại quầy lễ tân.</p>
                                <a class="btn btn-primary" href="${ctx}/customer/book-session">Đặt buổi lẻ</a>
                            </div>
                        </c:when>
                        <c:otherwise>
                            <div class="booking-layout">

                                <%-- ===================== CỘT TRÁI: 3 tab + lối đi nhanh ===================== --%>
                                <aside class="booking-sidebar schedule-board" aria-label="Danh sách gói">
                                    <div class="booking-sidebar-head">
                                        <h2>Gói của bạn</h2>
                                        <span class="pt-info"><button type="button" class="pt-info-trigger" aria-label="Giải thích các nhóm gói" aria-describedby="hint-tabs"><span aria-hidden="true">i</span></button><span id="hint-tabs" class="pt-info-popover" role="tooltip"><strong>Đang tập:</strong> gói PT còn hạn.<br><strong>Đã kết thúc:</strong> gói hết hạn hoặc đã dùng hết buổi.<br><strong>Buổi lẻ:</strong> các buổi bạn đặt riêng từng buổi, không thuộc gói nào.</span></span>
                                    </div>
                                    <div class="booking-tabs" role="tablist" aria-label="Nhóm gói">
                                        <%-- Tách nhãn và số đếm: nhãn được ngắt chữ khi hẹp, badge luôn đứng cạnh nhãn. --%>
                                        <button type="button" role="tab" id="tab-active" aria-controls="panel-active" data-tab="active" aria-selected="${openTab eq 'active'}">
                                            <span class="booking-tab-label">Đang tập</span>
                                            <span class="booking-tab-count">${fn:length(booking.activePackages)}</span>
                                        </button>
                                        <button type="button" role="tab" id="tab-ended" aria-controls="panel-ended" data-tab="ended" aria-selected="${openTab eq 'ended'}">
                                            <span class="booking-tab-label">Đã kết thúc</span>
                                            <span class="booking-tab-count">${fn:length(booking.endedPackages)}</span>
                                        </button>
                                        <button type="button" role="tab" id="tab-single" aria-controls="panel-single" data-tab="single" aria-selected="${openTab eq 'single'}">
                                            <span class="booking-tab-label">Buổi lẻ</span>
                                            <span class="booking-tab-count">${booking.singleSessionCount}</span>
                                        </button>
                                    </div>

                                    <div class="booking-panel" role="tabpanel" id="panel-active" aria-labelledby="tab-active" data-tab-panel="active" ${openTab eq 'active' ? '' : 'hidden'}>
                                        <c:forEach var="p" items="${booking.activePackages}">
                                            <%@ include file="_booking-package.jspf" %>
                                        </c:forEach>
                                        <c:if test="${empty booking.activePackages}">
                                            <p class="schedule-note booking-panel-empty">Bạn không có gói PT nào đang tập.</p>
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
                                                    <span class="booking-package-meta">${booking.singleSessionCount} buổi · mỗi buổi đặt riêng</span>
                                                </a>
                                            </c:when>
                                            <c:otherwise>
                                                <p class="schedule-note booking-panel-empty">Bạn chưa đặt buổi lẻ nào.</p>
                                            </c:otherwise>
                                        </c:choose>
                                    </div>

                                    <%-- Lối đi nhanh: màn này không được là ngõ cụt. Gói PT KHÔNG bán online (CBR-48) → chỉ dẫn mua gói thành viên / vé ngày. --%>
                                    <nav class="booking-links" aria-labelledby="links-title">
                                        <p class="booking-links-title"><span id="links-title">Đi tới</span>
                                            <span class="pt-info"><button type="button" class="pt-info-trigger" aria-label="Mua gói ở đâu" aria-describedby="hint-buy"><span aria-hidden="true">i</span></button><span id="hint-buy" class="pt-info-popover" role="tooltip"><strong>Online:</strong> gói thành viên, vé ngày và buổi PT lẻ.<br><strong>Tại quầy lễ tân:</strong> gói PT cá nhân và PT đôi (gia hạn cũng tại quầy).</span></span>
                                        </p>
                                        <a class="booking-explore" href="${ctx}/customer/packages/purchase">
                                            <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="9"></circle><path d="m15.5 8.5-2 5-5 2 2-5z"></path></svg>
                                            <span>Gói thành viên &amp; vé ngày</span>
                                            <svg class="booking-explore-arrow" viewBox="0 0 24 24" aria-hidden="true"><path d="m9 6 6 6-6 6"></path></svg>
                                        </a>
                                        <a href="${ctx}/customer/pt-sessions">
                                            <svg viewBox="0 0 24 24" aria-hidden="true"><rect x="3" y="5" width="18" height="16" rx="2"></rect><path d="M3 10h18M8 3v4M16 3v4"></path></svg> Lịch tập của tôi
                                        </a>
                                        <a href="${ctx}/customer/book-session">
                                            <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 5v14M5 12h14"></path></svg> Đặt buổi lẻ
                                        </a>
                                    </nav>
                                </aside>

                                <section class="booking-content schedule-board" aria-labelledby="booking-heading">

                                    <%-- B1: đầu gói và buổi tới dùng chung một khối, chỉ giữ một link mở đúng buổi. --%>
                                    <header class="booking-overview">
                                        <div class="booking-title">
                                            <h3 id="booking-heading"><c:out value="${booking.singleSelected ? 'Buổi lẻ' : sp.packageName}" /></h3>
                                            <a class="btn btn-outline btn-sm" href="${weekLink}${not empty f ? '&amp;date=' += f.slotDate += '&amp;session=' += f.sessionId : ''}">Xem trên lịch tập</a>
                                        </div>
                                        <c:set var="ptName" value="${not empty f ? f.ptName : sp.ptName}" />
                                        <c:set var="ptZalo" value="${not empty f ? f.zaloUrl : sp.zaloUrl}" />
                                        <c:if test="${not empty ptName}">
                                            <p class="booking-sub">PT <c:out value="${ptName}" /><c:if test="${not booking.singleSelected}"> · đến <c:out value="${gms:formatDate(sp.endDate)}" /></c:if></p>
                                        </c:if>
                                        <div class="booking-hero ${empty f ? 'is-quiet' : ''} ${booking.singleSelected ? 'is-single' : ''}">
                                            <div class="booking-hero-main">
                                                <%-- B2: có buổi thì hiện ngày giờ và cảnh báo; hết buổi dùng một câu ngắn. --%>
                                                <c:choose>
                                                    <c:when test="${not empty f}">
                                                        <p class="booking-hero-label">Buổi tới · <c:choose><c:when test="${booking.daysUntilFocus eq 0}">Hôm nay</c:when><c:when test="${booking.daysUntilFocus eq 1}">Ngày mai</c:when><c:otherwise><c:out value="${f.dayName}" />, <c:out value="${fn:substring(gms:formatDate(f.slotDate), 0, 5)}" /></c:otherwise></c:choose></p>
                                                        <div class="booking-hero-time">
                                                            <p class="booking-hero-when"><c:out value="${f.dayName}" />, <c:out value="${fn:substring(gms:formatDate(f.slotDate), 0, 5)}" /> · <c:out value="${gms:formatTime(f.startTime)}" /> – <c:out value="${gms:formatTime(f.endTime)}" /></p>
                                                            <c:if test="${f.statusKey eq 'warn'}"><span class="booking-hero-warn" title="${fn:escapeXml(f.explanation)}" aria-label="${fn:escapeXml(f.explanation)}"><c:out value="${f.noGymPass ? 'Cần Membership/DayPass' : (fn:contains(f.explanation, 'Phòng tập nghỉ') ? 'Ngày phòng nghỉ' : 'Cần chú ý')}" /></span></c:if>
                                                        </div>
                                                    </c:when>
                                                    <c:otherwise><p class="booking-hero-when">Không còn buổi sắp tới</p></c:otherwise>
                                                </c:choose>
                                                <c:if test="${(booking.singleSelected or sp.active) and not empty ptZalo}">
                                                    <div class="booking-hero-actions"><a class="btn btn-primary btn-sm" href="${fn:escapeXml(ptZalo)}" target="_blank" rel="noopener noreferrer">Nhắn Zalo PT</a></div>
                                                </c:if>
                                            </div>
                                            <%-- B3: buổi đã qua tính theo ngày; buổi lẻ không có quota gói hay tiến độ. --%>
                                            <c:if test="${not empty sp}">
                                                <c:set var="left" value="${sp.totalSessions - sp.pastSessions}" />
                                                <div class="booking-progress">
                                                    <div class="booking-ring ${left le 3 ? 'is-ending' : ''}" style="--p: ${sp.progressPercent}" role="img" aria-label="Còn ${left} buổi, đã qua ${sp.pastSessions} trên ${sp.totalSessions} buổi">
                                                        <p class="booking-remaining">còn <b><c:out value="${left}" /></b> <span>buổi</span></p>
                                                    </div>
                                                    <p class="booking-sub"><c:out value="${sp.pastSessions}" />/<c:out value="${sp.totalSessions}" /> đã qua</p>
                                                </div>
                                            </c:if>
                                        </div>
                                    </header>

                                    <%-- ===================== [CTA] tối đa 1 — Service chọn theo độ ưu tiên (MyBookingService.chooseCta) ===================== --%>
                                    <c:if test="${not empty cta}">
                                        <c:set var="customerCode" value="${dashboard.customerCode}" />
                                        <section class="booking-cta ${cta eq 'ptPackage' ? 'is-soft' : ''}" aria-labelledby="cta-title">
                                            <span class="booking-cta-icon" aria-hidden="true">
                                                <c:choose>
                                                    <c:when test="${cta eq 'buyMembership'}"><svg viewBox="0 0 24 24"><path d="M12 9v4M12 17h.01"></path><path d="M10.3 3.9 2.6 17a2 2 0 0 0 1.7 3h15.4a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0z"></path></svg></c:when>
                                                    <c:when test="${cta eq 'ptPackage'}"><svg viewBox="0 0 24 24"><path d="M17 2l4 4-4 4"></path><path d="M3 11v-1a4 4 0 0 1 4-4h14"></path><path d="M7 22l-4-4 4-4"></path><path d="M21 13v1a4 4 0 0 1-4 4H3"></path></svg></c:when>
                                                    <c:otherwise><svg viewBox="0 0 24 24"><path d="M21 12a9 9 0 1 1-3-6.7L21 8"></path><path d="M21 3v5h-5"></path></svg></c:otherwise>
                                                </c:choose>
                                            </span>
                                            <div class="booking-cta-text">
                                                <c:choose>
                                                    <c:when test="${cta eq 'buyMembership'}">
                                                        <h4 id="cta-title">Buổi sắp tới chưa vào được phòng tập</h4>
                                                        <p>Gói PT không kèm quyền vào gym. Mua gói thành viên hoặc vé ngày để buổi không bị tính vắng.</p>
                                                    </c:when>
                                                    <c:when test="${cta eq 'renewAtCounter'}">
                                                        <h4 id="cta-title">
                                                            <c:choose>
                                                                <c:when test="${booking.remainingCount gt 0}">Gói còn ${booking.remainingCount} buổi, hết hạn ngày ${gms:formatDate(sp.endDate)}</c:when>
                                                                <c:otherwise>Gói hết hạn ngày ${gms:formatDate(sp.endDate)}</c:otherwise>
                                                            </c:choose>
                                                        </h4>
                                                        <p>Gia hạn tại <strong>quầy lễ tân</strong><c:if test="${not empty customerCode}">, báo mã <strong><c:out value="${customerCode}" /></strong></c:if> để giữ lịch cố định với PT <c:out value="${sp.ptName}" />.<c:if test="${not empty booking.openingHours}"> Mở cửa <c:out value="${booking.openingHours}" />.</c:if></p>
                                                    </c:when>
                                                    <c:when test="${cta eq 'rejoin'}">
                                                        <h4 id="cta-title">Muốn tập tiếp với PT <c:out value="${sp.ptName}" />?</h4>
                                                        <p>Đăng ký gói PT mới tại <strong>quầy lễ tân</strong><c:if test="${not empty customerCode}">, báo mã <strong><c:out value="${customerCode}" /></strong></c:if>.<c:if test="${not empty booking.openingHours}"> Mở cửa <c:out value="${booking.openingHours}" />.</c:if></p>
                                                    </c:when>
                                                    <c:otherwise>
                                                        <h4 id="cta-title">Tập đều hơn với gói PT</h4>
                                                        <p>Bạn đã đặt ${booking.recentSingleCount} buổi lẻ trong 30 ngày. Gói PT xếp sẵn lịch cố định từ Thứ 2 đến Thứ 6 với 1 PT, đăng ký tại quầy lễ tân.</p>
                                                    </c:otherwise>
                                                </c:choose>
                                            </div>
                                            <div class="booking-cta-actions">
                                                <c:choose>
                                                    <c:when test="${cta eq 'buyMembership'}">
                                                        <a class="btn btn-primary btn-sm" href="${ctx}/customer/packages/purchase">Mua gói thành viên</a>
                                                    </c:when>
                                                    <c:otherwise>
                                                        <c:if test="${cta ne 'ptPackage' and not empty sp.zaloUrl}">
                                                            <a class="btn btn-ghost btn-sm" href="${fn:escapeXml(sp.zaloUrl)}" target="_blank" rel="noopener noreferrer">Báo PT trước</a>
                                                        </c:if>
                                                        <a class="btn ${cta eq 'ptPackage' ? 'btn-outline' : 'btn-primary'} btn-sm" href="tel:${booking.hotlineTel}">
                                                            <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M5 4h4l2 5-2.5 1.5a11 11 0 0 0 5 5L15 13l5 2v4a2 2 0 0 1-2 2A16 16 0 0 1 3 6a2 2 0 0 1 2-2"></path></svg>
                                                            Gọi lễ tân <span class="booking-cta-phone"><c:out value="${booking.hotline}" /></span>
                                                        </a>
                                                    </c:otherwise>
                                                </c:choose>
                                            </div>
                                        </section>
                                    </c:if>

                                    <c:choose>
                                        <c:when test="${not booking.singleSelected}">

                                            <%-- ===================== [LICH-CO-DINH] tuần mẫu T2–T6 (MyBookingService.applyWeekTemplate) ===================== --%>
                                            <section class="booking-section" aria-labelledby="pattern-title">
                                                <div class="booking-section-head">
                                                    <h4 id="pattern-title">Lịch cố định</h4>
                                                    <span class="pt-info"><button type="button" class="pt-info-trigger" aria-label="Cách đọc lịch cố định" aria-describedby="hint-pattern"><span aria-hidden="true">i</span></button><span id="hint-pattern" class="pt-info-popover" role="tooltip">Các ô là giờ tập lặp lại từ Thứ 2 đến Thứ 6.<br><strong>Viền đứt:</strong> giờ khác các thứ còn lại.<br><strong>Không cố định:</strong> giờ thay đổi theo tuần.<br><strong>Buổi khác giờ:</strong> do đổi lịch, PT dời/chuyển PT hoặc khung cố định đã kín.</span></span>
                                                    <span class="booking-count">${booking.templateCount}/${booking.totalCount} buổi theo lịch này</span>
                                                </div>
                                                <ul class="booking-week">
                                                    <c:forEach var="d" items="${booking.weekTemplate}" varStatus="st">
                                                        <li class="booking-day is-${d.kind} ${sp.active and st.index + 1 eq booking.todayWeekday ? 'is-today' : ''}">
                                                            <span class="booking-day-name">${d.dayName}</span>
                                                            <c:choose>
                                                                <c:when test="${d.fixed}">
                                                                    <b>${gms:formatTime(d.startTime)}</b>
                                                                    <small>${d.kind eq 'odd' ? 'giờ khác · ' : ''}${d.count} buổi</small>
                                                                </c:when>
                                                                <c:when test="${d.kind eq 'mixed'}"><b aria-hidden="true">~</b><small>không cố định</small></c:when>
                                                                <c:otherwise><b aria-hidden="true">—</b><small>không có buổi</small></c:otherwise>
                                                            </c:choose>
                                                        </li>
                                                    </c:forEach>
                                                </ul>
                                                <c:if test="${not empty booking.templateSummary}">
                                                    <p class="booking-summary"><strong><c:out value="${booking.templateSummary}" /></strong> · ${gms:formatDate(sp.startDate)} – ${gms:formatDate(sp.endDate)}</p>
                                                </c:if>

                                                <c:if test="${not empty booking.offPattern}">
                                                    <div class="booking-subhead">
                                                        <h5>Buổi khác giờ <span>${fn:length(booking.offPattern)}</span></h5>
                                                    </div>
                                                    <ul class="booking-rows">
                                                        <c:forEach var="s" items="${booking.offPattern}">
                                                            <li>
                                                                <a class="booking-rows-date" href="${weekLink}&amp;date=${s.slotDate}&amp;session=${s.sessionId}"><c:out value="${s.dayName}" />, ${gms:formatDate(s.slotDate)}</a>
                                                                <span class="booking-rows-time">${gms:formatTime(s.startTime)} – ${gms:formatTime(s.endTime)}</span>
                                                                <span class="booking-rows-note"><c:out value="${s.moveNote}" /></span>
                                                                <span class="session-status" data-status="${s.statusKey}"><c:out value="${s.statusLabel}" /></span>
                                                            </li>
                                                        </c:forEach>
                                                    </ul>
                                                </c:if>
                                            </section>

                                            <%-- ===================== [CAC-BUOI] dải chấm: 1 ô = 1 buổi, vạch = sang tuần mới ===================== --%>
                                            <%-- Trạng thái không chỉ bằng màu: mỗi loại có hình riêng (đặc / dấu × / gạch chéo / viền) + chú thích có số. --%>
                                            <c:if test="${booking.totalCount gt 0}">
                                                <section class="booking-section" aria-labelledby="dots-title">
                                                    <div class="booking-section-head">
                                                        <h4 id="dots-title">Các buổi của gói</h4>
                                                        <span class="pt-info"><button type="button" class="pt-info-trigger" aria-label="Cách đọc dải buổi" aria-describedby="hint-dots"><span aria-hidden="true">i</span></button><span id="hint-dots" class="pt-info-popover" role="tooltip">Mỗi ô là 1 buổi, xếp theo ngày tập. Vạch dọc: sang tuần mới. Bấm một ô để mở đúng buổi trên lịch tuần.<br><strong>Vắng:</strong> không check-in, buổi vẫn tính vào gói.<br><strong>Đang cập nhật:</strong> buổi vừa kết thúc, kết quả có sau khi hết ngày.</span></span>
                                                    </div>
                                                    <%-- Mỗi ô là link sang lịch tuần; date chọn ngày, session mở đúng buổi, from giữ gói để quay lại. --%>
                                                    <div class="booking-dots" role="group" aria-label="Các buổi trong gói">
                                                        <c:forEach var="s" items="${booking.sessions}">
                                                            <c:if test="${s.firstOfWeek}"><span class="booking-dot-week" aria-hidden="true"></span></c:if>
                                                            <c:url var="sessionWeekUrl" value="/customer/pt-sessions">
                                                                <c:param name="view" value="week" />
                                                                <c:param name="from" value="${fromKey}" />
                                                                <c:param name="date" value="${s.slotDate}" />
                                                                <c:param name="session" value="${s.sessionId}" />
                                                            </c:url>
                                                            <a class="booking-dot ${s.sessionId eq booking.focusSessionId ? 'is-next' : ''}"
                                                               data-status="${fn:escapeXml(s.statusKey)}" href="${fn:escapeXml(sessionWeekUrl)}"
                                                               aria-label="Buổi ${s.sessionNo}/${s.totalSessions}, ${gms:formatDate(s.slotDate)}, ${gms:formatTime(s.startTime)} – ${gms:formatTime(s.endTime)}, ${fn:escapeXml(s.statusLabel)}"
                                                               title="Buổi ${s.sessionNo}/${s.totalSessions} · ${gms:formatDate(s.slotDate)} · ${fn:escapeXml(s.statusLabel)}"></a>
                                                        </c:forEach>
                                                    </div>
                                                    <ul class="booking-key">
                                                        <c:if test="${booking.checkedInCount gt 0}"><li><i class="booking-dot" data-status="checkedIn"></i>Đã check-in <b>${booking.checkedInCount}</b></li></c:if>
                                                        <c:if test="${booking.missedCount gt 0}"><li><i class="booking-dot" data-status="missed"></i>Vắng <b>${booking.missedCount}</b></li></c:if>
                                                        <c:if test="${booking.cancelledCount gt 0}"><li><i class="booking-dot" data-status="cancelled"></i>Đã hủy <b>${booking.cancelledCount}</b></li></c:if>
                                                        <c:if test="${booking.pendingCount gt 0}"><li><i class="booking-dot" data-status="pending"></i>Đang cập nhật <b>${booking.pendingCount}</b></li></c:if>
                                                        <c:if test="${booking.remainingCount gt 0}"><li><i class="booking-dot" data-status="upcoming"></i>Sắp tới <b>${booking.remainingCount}</b></li></c:if>
                                                    </ul>
                                                </section>
                                            </c:if>
                                        </c:when>

                                        <c:otherwise>
                                            <%-- ===================== [BUOI-LE] sắp tới trước, rồi đã qua (mới nhất trước) ===================== --%>
                                            <section class="booking-section" aria-labelledby="single-title">
                                                <div class="booking-section-head">
                                                    <h4 id="single-title">Các buổi lẻ của bạn</h4>
                                                    <span class="booking-count">${booking.totalCount} buổi</span>
                                                </div>
                                                <ul class="booking-rows is-single">
                                                    <c:forEach var="s" items="${booking.sessions}">
                                                        <li>
                                                            <a class="booking-rows-date" href="${weekLink}&amp;date=${s.slotDate}&amp;session=${s.sessionId}"><c:out value="${s.dayName}" />, ${gms:formatDate(s.slotDate)}</a>
                                                            <span class="booking-rows-time">${gms:formatTime(s.startTime)} – ${gms:formatTime(s.endTime)}</span>
                                                            <span class="booking-rows-note">PT <strong><c:out value="${s.ptName}" /></strong></span>
                                                            <span class="session-status" data-status="${s.statusKey}"><c:out value="${s.statusLabel}" /></span>
                                                            <span class="booking-rows-action">
                                                                <c:if test="${s.rebook}">
                                                                    <a href="${ctx}/customer/book-session?pt=${s.ptEmployeeId}">Đặt lại với PT này</a>
                                                                </c:if>
                                                            </span>
                                                        </li>
                                                    </c:forEach>
                                                </ul>
                                            </section>
                                        </c:otherwise>
                                    </c:choose>

                                    <%-- ===================== [CAN-DE-Y] chỉ buổi SẮP TỚI chưa có Membership/DayPass (còn kịp mua); không có thì ẩn cả mục ===================== --%>
                                    <c:if test="${not empty booking.watchList}">
                                        <%-- details/summary hỗ trợ mở, đóng bằng chuột và bàn phím; mặc định thu gọn. --%>
                                        <details class="booking-section booking-watch" aria-labelledby="watch-title">
                                            <summary class="booking-section-head">
                                                <span id="watch-title" class="booking-watch-title">Cần để ý</span>
                                                <span class="booking-count">${fn:length(booking.watchList)} buổi</span>
                                            </summary>
                                            <p class="booking-summary">Buổi sắp tới rơi vào ngày bạn chưa có gói thành viên hoặc vé ngày hợp lệ. Mua trước ngày tập để buổi không bị tính vắng.</p>
                                            <c:set var="groups" value="${booking.watchGroups}" />
                                            <%@ include file="_booking-groups.jspf" %>
                                        </details>
                                    </c:if>

                                </section>
                            </div>
                        </c:otherwise>
                    </c:choose>
                </main>
            </div>
        </div>
    </body>
</html>
