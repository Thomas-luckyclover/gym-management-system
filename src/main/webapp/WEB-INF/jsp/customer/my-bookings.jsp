<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="gms" uri="/WEB-INF/gms-functions.tld" %>
<%--
  MÀN GÓI CỦA TÔI — mở bởi MyBookingServlet.doGet (GET /customer/bookings[?package=…]).
  Dữ liệu (request.setAttribute):
    booking      ← MyBookingServlet.loadBookings → MyBookingService.getMyBookings → MyPtSessionDAO / MyBookingDAO  (MyBookingView)
    bookingError ← câu lỗi khi getMyBookings thất bại
    dashboard    ← MyBookingServlet.loadHeader → CustomerDashboardService.getDashboard (canUseGym, customerCode)
  Bố cục: cột trái (Gói của bạn · PT · Vào phòng tập) | cột phải (gói đang xem: buổi tới, CTA, lịch cố định, các buổi, cần để ý).
  Màn chỉ xem: không có form POST; mọi link là GET (?package=… hoặc sang /customer/pt-sessions).
--%>

<c:set var="ctx" value="${pageContext.request.contextPath}" />
<c:set var="pageUrl" value="${ctx}/customer/bookings" />
<%-- Biến tắt: sp = gói đang xem (MyPtPackageView) · f = buổi tới (MyPtSessionRow) · cta = loại khung gợi ý. --%>
<c:set var="sp" value="${booking.selectedPackage}" />
<c:set var="f" value="${booking.focusSession}" />
<c:set var="cta" value="${booking.ctaKind}" />

<%-- Link sang Lịch tập (tuần) mang theo backPackage để nút "Quay lại" về đúng gói. --%>
<c:set var="backPackageKey" value="${booking.singleSelected ? 'single' : sp.subscriptionId}" />
<c:set var="weekLink" value="${ctx}/customer/pt-sessions?view=week&amp;backPackage=${backPackageKey}" />

<%-- Tab mở sẵn: đang xem buổi lẻ → "single" · gói đã kết thúc → "ended" · còn lại → "active". --%>
<c:set var="openTab" value="${booking.singleSelected ? 'single' : (not empty sp and not sp.active ? 'ended' : 'active')}" />
<!DOCTYPE html>
<html lang="vi">
    <head>
        <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
        <title>Gói của tôi · Super Gym</title>
        <link rel="stylesheet" href="${ctx}/css/pages/customer/dashboard.css?v=20261025city">
        <link rel="stylesheet" href="${ctx}/css/pages/customer/session-status.css?v=20261025a">
        <link rel="stylesheet" href="${ctx}/css/pages/customer/my-bookings.css?v=20261031a">
        <link rel="stylesheet" href="${ctx}/css/pages/auth/notification-bell.css">
        <script src="${ctx}/js/pages/auth/notification-bell.js" defer></script>
    </head>
    <body class="dock ${themeClass}">
        <%@ include file="/WEB-INF/jsp/common/flash-toast.jspf" %>
        <div class="app">

            <%@ include file="/WEB-INF/jsp/common/layout-customer.jspf" %>
            <div class="main">

                <%-- Thanh tiêu đề dùng chung (_schedule-header.jspf): tiêu đề + chuông + tài khoản. --%>
                <c:set var="scheduleTitle" value="Gói của tôi" />
                <%@ include file="_schedule-header.jspf" %>

                <main class="content booking-page" id="main-content" tabindex="-1">
                    <c:choose>

                        <%-- LỖI TẢI: booking không có (getMyBookings fail) → khung "Chưa tải được gói tập" + câu lỗi + nút Tải lại. --%>
                        <c:when test="${not empty bookingError}">
                            <div class="empty-state" role="alert">
                                <h2>Chưa tải được gói tập</h2>
                                <p><c:out value="${bookingError}" /></p>
                                <a class="btn btn-outline" href="${pageUrl}">Tải lại</a>
                            </div>
                        </c:when>

                        <%-- TRỐNG: booking.isNoData() = không gói, không buổi lẻ → mời "Đặt buổi lẻ". --%>
                        <c:when test="${booking.noData}">
                            <div class="empty-state">
                                <h2>Bạn chưa có gói PT hoặc buổi lẻ</h2>
                                <p>Đặt một buổi lẻ online, hoặc đăng ký gói PT tại quầy lễ tân.</p>
                                <a class="btn btn-primary" href="${ctx}/customer/book-session">Đặt buổi lẻ</a>
                            </div>
                        </c:when>

                        <c:otherwise>
                            <div class="booking-layout">

                                <%-- ===== CỘT TRÁI ===== --%>
                                <aside class="booking-rail" aria-label="Gói, PT và quyền vào phòng tập">

                                    <section class="panel booking-box" aria-labelledby="packages-title">
                                        <div class="booking-box-head">
                                            <h2 id="packages-title">Gói của bạn</h2>
                                            <span class="pt-info"><button type="button" class="pt-info-trigger" aria-label="Giải thích các nhóm gói" aria-describedby="hint-tabs"><span aria-hidden="true">i</span></button><span id="hint-tabs" class="pt-info-popover" role="tooltip"><strong>Đang tập:</strong> gói PT còn hạn.<br><strong>Đã kết thúc:</strong> gói hết hạn hoặc đã dùng hết buổi.<br><strong>Buổi lẻ:</strong> các buổi bạn đặt riêng từng buổi, không thuộc gói nào.</span></span>
                                        </div>

                                        <%--
                                          3 TAB (JS common tablist đổi tab, không tải lại trang):
                                            Đang tập    = số gói booking.activePackages · Đã kết thúc = booking.endedPackages · Buổi lẻ = booking.singleSessionCount
                                        --%>
                                        <div class="booking-tabs" role="tablist" aria-label="Nhóm gói">
                                            <button type="button" role="tab" id="tab-active" aria-controls="panel-active" data-tab="active" aria-selected="${openTab eq 'active'}">
                                                <span class="booking-tab-label">Đang tập</span><span class="booking-tab-count">${fn:length(booking.activePackages)}</span>
                                            </button>
                                            <button type="button" role="tab" id="tab-ended" aria-controls="panel-ended" data-tab="ended" aria-selected="${openTab eq 'ended'}">
                                                <span class="booking-tab-label">Đã kết thúc</span><span class="booking-tab-count">${fn:length(booking.endedPackages)}</span>
                                            </button>
                                            <button type="button" role="tab" id="tab-single" aria-controls="panel-single" data-tab="single" aria-selected="${openTab eq 'single'}">
                                                <span class="booking-tab-label">Buổi lẻ</span><span class="booking-tab-count">${booking.singleSessionCount}</span>
                                            </button>
                                        </div>

                                        <div class="booking-tab-panel" role="tabpanel" id="panel-active" aria-labelledby="tab-active" data-tab-panel="active" ${openTab eq 'active' ? '' : 'hidden'}>
                                            <%-- Mỗi gói đang tập = 1 thẻ (_booking-package.jspf, biến p) → bấm = ?package=<SubscriptionId>. --%>
                                            <c:forEach var="p" items="${booking.activePackages}">
                                                <%@ include file="_booking-package.jspf" %>
                                            </c:forEach>
                                            <c:if test="${empty booking.activePackages}"><p class="booking-muted">Bạn không có gói PT nào đang tập.</p></c:if>
                                        </div>

                                        <div class="booking-tab-panel" role="tabpanel" id="panel-ended" aria-labelledby="tab-ended" data-tab-panel="ended" ${openTab eq 'ended' ? '' : 'hidden'}>
                                            <%-- Mỗi gói đã kết thúc = 1 thẻ (cùng file _booking-package.jspf). --%>
                                            <c:forEach var="p" items="${booking.endedPackages}">
                                                <%@ include file="_booking-package.jspf" %>
                                            </c:forEach>
                                            <c:if test="${empty booking.endedPackages}"><p class="booking-muted">Chưa có gói nào kết thúc.</p></c:if>
                                        </div>

                                        <div class="booking-tab-panel" role="tabpanel" id="panel-single" aria-labelledby="tab-single" data-tab-panel="single" ${openTab eq 'single' ? '' : 'hidden'}>
                                            <c:choose>
                                                <%-- Có buổi lẻ → 1 thẻ "Tất cả buổi lẻ" → ?package=single (booking.singleSelected = true). --%>
                                                <c:when test="${booking.singleSessionCount gt 0}">
                                                    <a class="booking-package" href="${pageUrl}?package=single" aria-current="${booking.singleSelected ? 'page' : 'false'}">
                                                        <strong class="booking-package-name">Tất cả buổi lẻ</strong>
                                                        <span class="booking-package-meta">${booking.singleSessionCount} buổi · mỗi buổi đặt riêng</span>
                                                    </a>
                                                </c:when>
                                                <c:otherwise><p class="booking-muted">Bạn chưa đặt buổi lẻ nào.</p></c:otherwise>
                                            </c:choose>
                                            <a class="booking-add" href="${ctx}/customer/book-session"><i class="fa-solid fa-calendar-plus" aria-hidden="true"></i> Đặt buổi lẻ</a>
                                        </div>
                                    </section>

                                    <%-- KHUNG PT: có buổi tới → PT của buổi tới (f.ptName, f.ptPhone, f.zaloUrl); không → PT của gói (sp.…). --%>
                                    <c:set var="ptName" value="${not empty f ? f.ptName : sp.ptName}" />
                                    <c:set var="ptPhone" value="${not empty f ? f.ptPhone : sp.ptPhone}" />
                                    <c:set var="ptZalo" value="${not empty f ? f.zaloUrl : sp.zaloUrl}" />
                                    <c:if test="${not empty ptName}">
                                        <section class="panel booking-box" aria-labelledby="pt-title">
                                            <h2 id="pt-title" class="booking-box-label">${not empty f ? 'PT buổi tới' : 'PT của gói'}</h2>
                                            <p class="booking-pt-name"><c:out value="${ptName}" /></p>
                                            <div class="booking-contact">
                                                <c:if test="${not empty ptPhone}">
                                                    <a class="booking-contact-btn" href="tel:${fn:escapeXml(ptPhone)}"><i class="fa-solid fa-phone" aria-hidden="true"></i><c:out value="${ptPhone}" /></a>
                                                </c:if>
                                                <c:if test="${not empty ptZalo}">
                                                    <a class="booking-contact-btn is-zalo" href="${fn:escapeXml(ptZalo)}" target="_blank" rel="noopener noreferrer" aria-label="Nhắn Zalo PT ${fn:escapeXml(ptName)} (mở tab mới)">Nhắn Zalo<i class="fa-solid fa-arrow-up-right-from-square" aria-hidden="true"></i></a>
                                                </c:if>
                                            </div>
                                            <p class="booking-muted">Muốn đổi hoặc hủy buổi, hãy nhắn PT trước.</p>
                                        </section>
                                    </c:if>

                                    <%--
                                      KHUNG "VÀO PHÒNG TẬP" ← dashboard.canUseGym (CustomerDashboardService): xanh "Hôm nay vào được" / đỏ "chưa vào được".
                                      Nút "Gói thành viên & vé ngày" ẩn khi khung CTA đã là buyMembership (tránh 2 nút giống nhau).
                                    --%>
                                    <c:if test="${not empty dashboard}">
                                        <section class="panel booking-box" aria-labelledby="gym-title">
                                            <div class="booking-box-head">
                                                <h2 id="gym-title" class="booking-box-label">Vào phòng tập</h2>
                                                <span class="pt-info"><button type="button" class="pt-info-trigger" aria-label="Mua gói ở đâu" aria-describedby="hint-buy"><span aria-hidden="true">i</span></button><span id="hint-buy" class="pt-info-popover" role="tooltip"><strong>Online:</strong> gói thành viên, vé ngày và buổi PT lẻ.<br><strong>Tại quầy lễ tân:</strong> gói PT cá nhân và PT đôi (gia hạn cũng tại quầy).</span></span>
                                            </div>
                                            <p class="booking-gym-status ${dashboard.canUseGym ? 'is-ok' : 'is-no'}">
                                                <c:out value="${dashboard.canUseGym ? 'Hôm nay vào được' : 'Hôm nay chưa vào được'}" />
                                            </p>
                                            <c:if test="${not dashboard.canUseGym}"><p class="booking-muted">Gói PT không kèm quyền vào gym — cần gói thành viên hoặc vé ngày.</p></c:if>

                                            <c:if test="${cta ne 'buyMembership'}">
                                                <a class="booking-add" href="${ctx}/customer/packages/purchase"><i class="fa-solid fa-id-card" aria-hidden="true"></i> Gói thành viên &amp; vé ngày</a>
                                            </c:if>
                                        </section>
                                    </c:if>

                                </aside>

                                <%-- ===== CỘT PHẢI: GÓI ĐANG XEM ===== --%>
                                <section class="panel booking-detail" aria-labelledby="booking-heading">

                                    <header class="booking-overview">
                                        <div class="booking-overview-main">

                                            <%-- Tiêu đề: "Buổi lẻ" hoặc sp.packageName; dòng phụ: sp.typeLabel, "cùng" sp.partnerName, sp.startDate – sp.endDate. --%>
                                            <h2 id="booking-heading"><c:out value="${booking.singleSelected ? 'Buổi lẻ' : sp.packageName}" /></h2>

                                            <c:if test="${not booking.singleSelected}">
                                                <p class="booking-muted">
                                                    <c:out value="${sp.typeLabel}" /><c:if test="${not empty sp.partnerName}"> cùng <c:out value="${sp.partnerName}" /></c:if>
                                                    · <c:out value="${gms:formatDate(sp.startDate)}" /> – <c:out value="${gms:formatDate(sp.endDate)}" />
                                                </p>
                                            </c:if>

                                            <%--
                                              BUỔI TỚI ← booking.focusSession (MyBookingService.countSessions):
                                                "Hôm nay"/"Ngày mai" ← booking.daysUntilFocus · ngày giờ ← f.dayName, f.slotDate, f.startTime–f.endTime
                                                f.statusKey = 'warn' → nhãn vàng (thiếu gói gym / phòng nghỉ) · link "Xem trên lịch tập" → /customer/pt-sessions mở đúng buổi
                                            --%>
                                            <div class="booking-next">
                                                <c:choose>
                                                    <c:when test="${not empty f}">
                                                        <p class="booking-next-label">
                                                            Buổi tới<c:if test="${booking.daysUntilFocus eq 0}"> · <strong>Hôm nay</strong></c:if><c:if test="${booking.daysUntilFocus eq 1}"> · <strong>Ngày mai</strong></c:if>
                                                        </p>
                                                        <p class="booking-next-when">
                                                            <c:out value="${f.dayName}" />, <c:out value="${fn:substring(gms:formatDate(f.slotDate), 0, 5)}" /> · <c:out value="${gms:formatTime(f.startTime)}" /> – <c:out value="${gms:formatTime(f.endTime)}" />
                                                            <c:if test="${f.statusKey eq 'warn'}">
                                                                <span class="booking-next-warn" title="${fn:escapeXml(f.explanation)}"><c:out value="${f.noGymPass ? 'Cần Membership/DayPass' : (fn:contains(f.explanation, 'Phòng tập nghỉ') ? 'Ngày phòng nghỉ' : 'Cần chú ý')}" /></span>
                                                            </c:if>
                                                        </p>
                                                    </c:when>
                                                    <c:otherwise>
                                                        <p class="booking-next-when is-quiet">Không còn buổi sắp tới</p>
                                                    </c:otherwise>
                                                </c:choose>

                                                <c:url var="nextUrl" value="/customer/pt-sessions">
                                                    <c:param name="view" value="week" />
                                                    <c:param name="backPackage" value="${backPackageKey}" />
                                                    <c:if test="${not empty f}">
                                                        <c:param name="date" value="${f.slotDate}" />
                                                        <c:param name="openSession" value="${f.sessionId}" />
                                                    </c:if>
                                                </c:url>
                                                <a class="booking-next-link" href="${fn:escapeXml(nextUrl)}">Xem trên lịch tập <i class="fa-solid fa-chevron-right" aria-hidden="true"></i></a>
                                            </div>
                                        </div>

                                        <%-- VÒNG TIẾN ĐỘ: còn = sp.totalSessions − sp.pastSessions (≤ 3 → màu cảnh báo) · % = sp.progressPercent. --%>
                                        <c:if test="${not empty sp}">
                                            <c:set var="left" value="${sp.totalSessions - sp.pastSessions}" />
                                            <div class="booking-progress">
                                                <div class="booking-ring ${left le 3 ? 'is-ending' : ''}" style="--p: ${sp.progressPercent}" role="img" aria-label="Còn ${left} buổi, đã qua ${sp.pastSessions} trên ${sp.totalSessions} buổi">
                                                    <p class="booking-ring-text">còn <b><c:out value="${left}" /></b> buổi</p>
                                                </div>
                                                <p class="booking-muted"><c:out value="${sp.pastSessions}" />/<c:out value="${sp.totalSessions}" /> đã qua</p>
                                            </div>
                                        </c:if>
                                    </header>

                                    <%--
                                      KHUNG GỢI Ý ← booking.ctaKind (MyBookingService.chooseCta):
                                        buyMembership  → "Buổi sắp tới chưa vào được phòng tập" + nút Mua gói thành viên
                                        renewAtCounter → "Gói còn N buổi, hết hạn …" (booking.remainingCount, sp.endDate) + Gọi lễ tân
                                        rejoin         → "Muốn tập tiếp với PT …?" + Gọi lễ tân
                                        ptPackage      → "Bạn đã đặt N buổi lẻ trong 30 ngày" (booking.recentSingleCount) + Gọi lễ tân
                                        Mã khách ← dashboard.customerCode · số gọi ← booking.hotline / booking.hotlineTel · giờ mở cửa ← booking.openingHours
                                    --%>
                                    <c:if test="${not empty cta}">
                                        <c:set var="customerCode" value="${dashboard.customerCode}" />
                                        <section class="booking-cta ${cta eq 'ptPackage' ? 'is-soft' : ''}" aria-labelledby="cta-title">

                                            <span class="booking-cta-icon" aria-hidden="true">
                                                <c:choose>
                                                    <c:when test="${cta eq 'buyMembership'}"><i class="fa-solid fa-triangle-exclamation"></i></c:when>
                                                    <c:when test="${cta eq 'ptPackage'}"><i class="fa-solid fa-repeat"></i></c:when>
                                                    <c:otherwise><i class="fa-solid fa-rotate-right"></i></c:otherwise>
                                                </c:choose>
                                            </span>
                                            <div class="booking-cta-text">
                                                <c:choose>
                                                    <c:when test="${cta eq 'buyMembership'}">
                                                        <h3 id="cta-title">Buổi sắp tới chưa vào được phòng tập</h3>
                                                        <p>Gói PT không kèm quyền vào gym. Mua gói thành viên hoặc vé ngày để buổi không bị tính vắng.</p>
                                                    </c:when>
                                                    <c:when test="${cta eq 'renewAtCounter'}">

                                                        <h3 id="cta-title">
                                                            <c:choose>
                                                                <c:when test="${booking.remainingCount gt 0}">Gói còn ${booking.remainingCount} buổi, hết hạn ngày ${gms:formatDate(sp.endDate)}</c:when>
                                                                <c:otherwise>Gói hết hạn ngày ${gms:formatDate(sp.endDate)}</c:otherwise>
                                                            </c:choose>
                                                        </h3>
                                                        <p>Gia hạn tại <strong>quầy lễ tân</strong><c:if test="${not empty customerCode}">, báo mã <strong><c:out value="${customerCode}" /></strong></c:if> để giữ lịch cố định với PT <c:out value="${sp.ptName}" />.<c:if test="${not empty booking.openingHours}"> Mở cửa <c:out value="${booking.openingHours}" />.</c:if></p>
                                                    </c:when>
                                                    <c:when test="${cta eq 'rejoin'}">
                                                        <h3 id="cta-title">Muốn tập tiếp với PT <c:out value="${sp.ptName}" />?</h3>
                                                        <p>Đăng ký gói PT mới tại <strong>quầy lễ tân</strong><c:if test="${not empty customerCode}">, báo mã <strong><c:out value="${customerCode}" /></strong></c:if>.<c:if test="${not empty booking.openingHours}"> Mở cửa <c:out value="${booking.openingHours}" />.</c:if></p>
                                                    </c:when>
                                                    <c:otherwise>

                                                        <h3 id="cta-title">Tập đều hơn với gói PT</h3>
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

                                                        <a class="btn ${cta eq 'ptPackage' ? 'btn-outline' : 'btn-primary'} btn-sm" href="tel:${booking.hotlineTel}">
                                                            <i class="fa-solid fa-phone" aria-hidden="true"></i> Gọi lễ tân <span class="booking-cta-phone"><c:out value="${booking.hotline}" /></span>
                                                        </a>
                                                    </c:otherwise>
                                                </c:choose>
                                            </div>
                                        </section>
                                    </c:if>

                                    <c:choose>
                                        <%-- ===== XEM 1 GÓI ===== --%>
                                        <c:when test="${not booking.singleSelected}">

                                            <%--
                                              LỊCH CỐ ĐỊNH ← booking.weekTemplate (5 WeekdaySlot, MyBookingService.applyWeekTemplate):
                                                "x/y buổi theo lịch này" = booking.templateCount / booking.totalCount · ô hôm nay ← booking.todayWeekday
                                                mỗi ô: d.fixed → giờ + "N buổi" (odd: "giờ khác") · mixed → "~ không cố định" · none → "— không có buổi"
                                            --%>
                                            <section class="booking-section" aria-labelledby="pattern-title">
                                                <div class="booking-section-head">
                                                    <h3 id="pattern-title">Lịch cố định</h3>
                                                    <span class="pt-info"><button type="button" class="pt-info-trigger" aria-label="Cách đọc lịch cố định" aria-describedby="hint-pattern"><span aria-hidden="true">i</span></button><span id="hint-pattern" class="pt-info-popover" role="tooltip">Các ô là giờ tập lặp lại từ Thứ 2 đến Thứ 6.<br><strong>Viền đứt:</strong> giờ khác các thứ còn lại.<br><strong>Không cố định:</strong> giờ thay đổi theo tuần.<br><strong>Buổi khác giờ:</strong> do đổi lịch, PT dời/chuyển PT hoặc khung cố định đã kín.</span></span>

                                                    <span class="booking-count">${booking.templateCount}/${booking.totalCount} buổi theo lịch này</span>
                                                </div>
                                                <ul class="booking-week" aria-label="${fn:escapeXml(empty booking.templateSummary ? 'Chưa có giờ cố định' : booking.templateSummary)}">
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

                                                <%-- BUỔI KHÁC GIỜ ← booking.offPattern: ngày (link mở buổi) · giờ · s.moveNote (MyBookingDAO.findMoves) · trạng thái. --%>
                                                <c:if test="${not empty booking.offPattern}">
                                                    <h4 class="booking-subhead">Buổi khác giờ <span>${fn:length(booking.offPattern)}</span></h4>
                                                    <ul class="booking-rows">
                                                        <c:forEach var="s" items="${booking.offPattern}">
                                                            <li>
                                                                <a class="booking-rows-date" href="${weekLink}&amp;date=${s.slotDate}&amp;openSession=${s.sessionId}"><c:out value="${s.dayName}" />, ${gms:formatDate(s.slotDate)}</a>
                                                                <span class="booking-rows-time">${gms:formatTime(s.startTime)} – ${gms:formatTime(s.endTime)}</span>
                                                                <span class="booking-rows-note"><c:out value="${s.moveNote}" /></span>
                                                                <span class="session-status" data-status="${s.statusKey}"><c:out value="${s.statusLabel}" /></span>
                                                            </li>
                                                        </c:forEach>
                                                    </ul>
                                                </c:if>
                                            </section>

                                            <%--
                                              CÁC BUỔI CỦA GÓI: mỗi buổi 1 chấm màu theo s.statusKey; s.firstOfWeek → vạch ngăn tuần;
                                                buổi tới (booking.focusSessionId) có viền; bấm chấm → Lịch tập mở đúng buổi.
                                            --%>
                                            <c:if test="${booking.totalCount gt 0}">
                                                <section class="booking-section" aria-labelledby="dots-title">
                                                    <div class="booking-section-head">
                                                        <h3 id="dots-title">Các buổi của gói</h3>
                                                        <span class="pt-info"><button type="button" class="pt-info-trigger" aria-label="Cách đọc dải buổi" aria-describedby="hint-dots"><span aria-hidden="true">i</span></button><span id="hint-dots" class="pt-info-popover" role="tooltip">Mỗi ô là 1 buổi, xếp theo ngày tập. Vạch dọc: sang tuần mới. Bấm một ô để mở đúng buổi trên lịch tuần.<br><strong>Vắng:</strong> không check-in, buổi vẫn tính vào gói.<br><strong>Đang cập nhật:</strong> buổi vừa kết thúc, kết quả có sau khi hết ngày.</span></span>
                                                    </div>
                                                    <div class="booking-dots" role="group" aria-label="Các buổi trong gói">
                                                        <c:forEach var="s" items="${booking.sessions}">
                                                            <c:if test="${s.firstOfWeek}"><span class="booking-dot-week" aria-hidden="true"></span></c:if>
                                                            <c:url var="sessionUrl" value="/customer/pt-sessions">
                                                                <c:param name="view" value="week" />
                                                                <c:param name="backPackage" value="${backPackageKey}" />
                                                                <c:param name="date" value="${s.slotDate}" />
                                                                <c:param name="openSession" value="${s.sessionId}" />
                                                            </c:url>
                                                            <a class="booking-dot ${s.sessionId eq booking.focusSessionId ? 'is-next' : ''}"
                                                               data-status="${fn:escapeXml(s.statusKey)}" href="${fn:escapeXml(sessionUrl)}"
                                                               aria-label="Buổi ${s.sessionNo}/${s.totalSessions}, ${gms:formatDate(s.slotDate)}, ${gms:formatTime(s.startTime)} – ${gms:formatTime(s.endTime)}, ${fn:escapeXml(s.statusLabel)}"
                                                               title="Buổi ${s.sessionNo}/${s.totalSessions} · ${gms:formatDate(s.slotDate)} · ${fn:escapeXml(s.statusLabel)}"></a>
                                                        </c:forEach>
                                                    </div>

                                                    <%-- CHÚ THÍCH: chỉ hiện loại có số > 0 ← booking.checkedInCount / missedCount / cancelledCount / pendingCount / remainingCount. --%>
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

                                        <%--
                                          ===== XEM BUỔI LẺ ===== ← booking.sessions (orderSingles: sắp tới trước, đã qua sau)
                                            mỗi dòng: ngày · giờ · PT · trạng thái · s.rebook → link "Đặt lại với PT này" (?pt=<ptEmployeeId>)
                                        --%>
                                        <c:otherwise>

                                            <section class="booking-section" aria-labelledby="single-title">
                                                <div class="booking-section-head">
                                                    <h3 id="single-title">Các buổi lẻ của bạn</h3>
                                                    <span class="booking-count">${booking.totalCount} buổi</span>
                                                </div>
                                                <ul class="booking-rows is-single">
                                                    <c:forEach var="s" items="${booking.sessions}">
                                                        <li>
                                                            <a class="booking-rows-date" href="${weekLink}&amp;date=${s.slotDate}&amp;openSession=${s.sessionId}"><c:out value="${s.dayName}" />, ${gms:formatDate(s.slotDate)}</a>
                                                            <span class="booking-rows-time">${gms:formatTime(s.startTime)} – ${gms:formatTime(s.endTime)}</span>
                                                            <span class="booking-rows-note">PT <strong><c:out value="${s.ptName}" /></strong></span>
                                                            <span class="session-status" data-status="${s.statusKey}"><c:out value="${s.statusLabel}" /></span>
                                                            <span class="booking-rows-action">
                                                                <c:if test="${s.rebook}"><a href="${ctx}/customer/book-session?pt=${s.ptEmployeeId}">Đặt lại với PT này</a></c:if>
                                                            </span>
                                                        </li>
                                                    </c:forEach>
                                                </ul>
                                            </section>

                                        </c:otherwise>
                                    </c:choose>

                                    <%-- CẦN ĐỂ Ý ← booking.watchList (buổi thiếu gói gym / vé ngày) → nhóm theo lý do: booking.watchGroups → _booking-groups.jspf. --%>
                                    <c:if test="${not empty booking.watchList}">
                                        <details class="booking-section booking-watch">
                                            <summary class="booking-section-head">
                                                <span class="booking-watch-title">Cần để ý</span>
                                                <span class="booking-count">${fn:length(booking.watchList)} buổi</span>
                                            </summary>
                                            <p class="booking-muted">Buổi sắp tới rơi vào ngày bạn chưa có gói thành viên hoặc vé ngày hợp lệ. Mua trước ngày tập để buổi không bị tính vắng.</p>

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
