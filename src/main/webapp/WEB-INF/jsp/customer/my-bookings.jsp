<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="gms" uri="/WEB-INF/gms-functions.tld" %>
<%--
================================================================================
 my-bookings.jsp — MÀN "GÓI CỦA TÔI" (/customer/bookings?id=<mã gói | single>). CHỈ XEM.

 DỮ LIỆU SERVLET GỬI SANG
   booking       MyBookingView (MyBookingService.getMyBookings) — hoặc bookingError khi lỗi
   dashboard     CustomerDashboardView — tên/mã khách (topbar) + canUseGym (thẻ "Vào phòng tập")

 BỐ CỤC
   Cột trái (dính khi cuộn) = những gì của TÔI:
     1. Gói của bạn     3 tab Đang tập / Đã kết thúc / Buổi lẻ (common.js bindTabs)
     2. PT của bạn      tên, gọi, Zalo
     3. Vào phòng tập   hôm nay vào được không + link mua gói thành viên / vé ngày
   Cột phải = gói đang chọn:
     4. Đầu gói         tên gói, thời hạn, buổi tới (+ link DUY NHẤT sang Lịch tập), vòng "còn N buổi"
     5. Lời mời         tối đa 1 (MyBookingService.chooseCta)
     6. Lịch cố định    5 ô T2–T6 + buổi khác giờ         | Buổi lẻ: danh sách buổi lẻ
     7. Các buổi        1 ô = 1 buổi, bấm ô → mở đúng buổi trên Lịch tập
     8. Cần để ý        buổi sắp tới chưa có gói thành viên (thu gọn)
================================================================================ --%>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<c:set var="pageUrl" value="${ctx}/customer/bookings" />
<c:set var="sp" value="${booking.selectedPackage}" />
<c:set var="f" value="${booking.focusSession}" />
<c:set var="cta" value="${booking.ctaKind}" />

<%-- Link sang Lịch tập tuần: from = gói đang xem (để có nút quay về đúng gói); các dòng bên dưới nối thêm date + session. --%>
<c:set var="fromKey" value="${booking.singleSelected ? 'single' : sp.subscriptionId}" />
<c:set var="weekLink" value="${ctx}/customer/pt-sessions?view=week&amp;from=${fromKey}" />

<%-- Tab mở sẵn = tab chứa thứ đang xem ở cột phải. --%>
<c:set var="openTab" value="${booking.singleSelected ? 'single' : (not empty sp and not sp.active ? 'ended' : 'active')}" />
<!DOCTYPE html>
<html lang="vi">
    <head>
        <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
        <title>Gói của tôi · Super Gym</title>
        <link rel="stylesheet" href="${ctx}/css/pages/customer/dashboard.css?v=20261013c">
        <link rel="stylesheet" href="${ctx}/css/pages/customer/session-status.css?v=20261025a">
        <link rel="stylesheet" href="${ctx}/css/pages/customer/my-bookings.css?v=20261025a">
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

                <main class="content booking-page" id="main-content" tabindex="-1">
                    <c:choose>
                        <c:when test="${not empty bookingError}">
                            <div class="empty-state" role="alert">
                                <h2>Chưa tải được gói tập</h2>
                                <p><c:out value="${bookingError}" /></p>
                                <a class="btn btn-outline" href="${pageUrl}">Tải lại</a>
                            </div>
                        </c:when>

                        <c:when test="${booking.noData}">
                            <div class="empty-state">
                                <h2>Bạn chưa có gói PT hoặc buổi lẻ</h2>
                                <p>Đặt một buổi lẻ online, hoặc đăng ký gói PT tại quầy lễ tân.</p>
                                <a class="btn btn-primary" href="${ctx}/customer/book-session">Đặt buổi lẻ</a>
                            </div>
                        </c:when>

                        <c:otherwise>
                            <div class="booking-layout">

                                <%-- ======================= CỘT TRÁI ======================= --%>
                                <aside class="booking-rail" aria-label="Gói, PT và quyền vào phòng tập">

                                    <%-- ===== 1. GÓI CỦA BẠN ===== --%>
                                    <section class="panel booking-box" aria-labelledby="packages-title">
                                        <div class="booking-box-head">
                                            <h2 id="packages-title">Gói của bạn</h2>
                                            <span class="pt-info"><button type="button" class="pt-info-trigger" aria-label="Giải thích các nhóm gói" aria-describedby="hint-tabs"><span aria-hidden="true">i</span></button><span id="hint-tabs" class="pt-info-popover" role="tooltip"><strong>Đang tập:</strong> gói PT còn hạn.<br><strong>Đã kết thúc:</strong> gói hết hạn hoặc đã dùng hết buổi.<br><strong>Buổi lẻ:</strong> các buổi bạn đặt riêng từng buổi, không thuộc gói nào.</span></span>
                                        </div>

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
                                            <c:forEach var="p" items="${booking.activePackages}">
                                                <%@ include file="_booking-package.jspf" %>
                                            </c:forEach>
                                            <c:if test="${empty booking.activePackages}"><p class="booking-muted">Bạn không có gói PT nào đang tập.</p></c:if>
                                        </div>

                                        <div class="booking-tab-panel" role="tabpanel" id="panel-ended" aria-labelledby="tab-ended" data-tab-panel="ended" ${openTab eq 'ended' ? '' : 'hidden'}>
                                            <c:forEach var="p" items="${booking.endedPackages}">
                                                <%@ include file="_booking-package.jspf" %>
                                            </c:forEach>
                                            <c:if test="${empty booking.endedPackages}"><p class="booking-muted">Chưa có gói nào kết thúc.</p></c:if>
                                        </div>

                                        <div class="booking-tab-panel" role="tabpanel" id="panel-single" aria-labelledby="tab-single" data-tab-panel="single" ${openTab eq 'single' ? '' : 'hidden'}>
                                            <c:choose>
                                                <c:when test="${booking.singleSessionCount gt 0}">
                                                    <a class="booking-package" href="${pageUrl}?id=single" aria-current="${booking.singleSelected ? 'page' : 'false'}">
                                                        <strong class="booking-package-name">Tất cả buổi lẻ</strong>
                                                        <span class="booking-package-meta">${booking.singleSessionCount} buổi · mỗi buổi đặt riêng</span>
                                                    </a>
                                                </c:when>
                                                <c:otherwise><p class="booking-muted">Bạn chưa đặt buổi lẻ nào.</p></c:otherwise>
                                            </c:choose>
                                            <a class="booking-add" href="${ctx}/customer/book-session"><i class="fa-solid fa-calendar-plus" aria-hidden="true"></i> Đặt buổi lẻ</a>
                                        </div>
                                    </section>

                                    <%-- ===== 2. PT CỦA BẠN =====
                                         Có buổi tới → PT của buổi đó (có thể là PT dạy thay); không có → PT của gói. --%>
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

                                    <%-- ===== 3. VÀO PHÒNG TẬP =====
                                         Gói PT KHÔNG kèm quyền vào gym: cần gói thành viên hoặc vé ngày (JOB-04 tính vắng nếu thiếu).
                                         canUseGym = hôm nay vào được không (CustomerDashboardService). --%>
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
                                            <%-- Lời mời bên phải đã có nút "Mua gói thành viên" thì ở đây không lặp lại. --%>
                                            <c:if test="${cta ne 'buyMembership'}">
                                                <a class="booking-add" href="${ctx}/customer/packages/purchase"><i class="fa-solid fa-id-card" aria-hidden="true"></i> Gói thành viên &amp; vé ngày</a>
                                            </c:if>
                                        </section>
                                    </c:if>
                                </aside>


                                <%-- ======================= CỘT PHẢI ======================= --%>
                                <section class="panel booking-detail" aria-labelledby="booking-heading">

                                    <%-- ===== 4. ĐẦU GÓI ===== --%>
                                    <header class="booking-overview">
                                        <div class="booking-overview-main">
                                            <h2 id="booking-heading"><c:out value="${booking.singleSelected ? 'Buổi lẻ' : sp.packageName}" /></h2>
                                            <c:if test="${not booking.singleSelected}">
                                                <p class="booking-muted">
                                                    <c:out value="${sp.typeLabel}" /><c:if test="${not empty sp.partnerName}"> cùng <c:out value="${sp.partnerName}" /></c:if>
                                                    · <c:out value="${gms:formatDate(sp.startDate)}" /> – <c:out value="${gms:formatDate(sp.endDate)}" />
                                                </p>
                                            </c:if>

                                            <%-- Buổi tới: 1 nhãn + 1 dòng ngày giờ (không lặp thứ/ngày). Hôm nay / ngày mai thì nói thẳng. --%>
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
                                                <%-- Link DUY NHẤT sang Lịch tập: mở đúng tuần + đúng buổi tới. --%>
                                                <c:url var="nextUrl" value="/customer/pt-sessions">
                                                    <c:param name="view" value="week" />
                                                    <c:param name="from" value="${fromKey}" />
                                                    <c:if test="${not empty f}">
                                                        <c:param name="date" value="${f.slotDate}" />
                                                        <c:param name="session" value="${f.sessionId}" />
                                                    </c:if>
                                                </c:url>
                                                <a class="booking-next-link" href="${fn:escapeXml(nextUrl)}">Xem trên lịch tập <i class="fa-solid fa-chevron-right" aria-hidden="true"></i></a>
                                            </div>
                                        </div>

                                        <%-- Vòng tiến độ: chỉ gói PT (buổi lẻ không có số buổi của gói). --%>
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


                                    <%-- ===== 5. LỜI MỜI HÀNH ĐỘNG (tối đa 1) ===== --%>
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
                                        <c:when test="${not booking.singleSelected}">

                                            <%-- ===== 6. LỊCH CỐ ĐỊNH (MyBookingService.applyWeekTemplate) =====
                                                 5 ô đã nói đủ thứ + giờ → câu tóm tắt "T2 – T6 lúc 18:00" chỉ làm nhãn cho trình đọc màn hình. --%>
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

                                                <c:if test="${not empty booking.offPattern}">
                                                    <h4 class="booking-subhead">Buổi khác giờ <span>${fn:length(booking.offPattern)}</span></h4>
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

                                            <%-- ===== 7. CÁC BUỔI CỦA GÓI: 1 ô = 1 buổi, vạch dọc = sang tuần mới =====
                                                 Không chỉ dựa vào màu: mỗi trạng thái 1 hình (đặc / dấu × / gạch chéo / viền đứt / viền) + chú thích có số. --%>
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
                                                                <c:param name="from" value="${fromKey}" />
                                                                <c:param name="date" value="${s.slotDate}" />
                                                                <c:param name="session" value="${s.sessionId}" />
                                                            </c:url>
                                                            <a class="booking-dot ${s.sessionId eq booking.focusSessionId ? 'is-next' : ''}"
                                                               data-status="${fn:escapeXml(s.statusKey)}" href="${fn:escapeXml(sessionUrl)}"
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
                                            <%-- ===== 6'. BUỔI LẺ: sắp tới trước, rồi đã qua (mới nhất trước) ===== --%>
                                            <section class="booking-section" aria-labelledby="single-title">
                                                <div class="booking-section-head">
                                                    <h3 id="single-title">Các buổi lẻ của bạn</h3>
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
                                                                <c:if test="${s.rebook}"><a href="${ctx}/customer/book-session?pt=${s.ptEmployeeId}">Đặt lại với PT này</a></c:if>
                                                            </span>
                                                        </li>
                                                    </c:forEach>
                                                </ul>
                                            </section>
                                        </c:otherwise>
                                    </c:choose>


                                    <%-- ===== 8. CẦN ĐỂ Ý: buổi SẮP TỚI chưa có Membership/DayPass (còn kịp mua). Không có → ẩn cả mục.
                                         <details> tự mở / đóng bằng chuột và bàn phím, không cần JS. ===== --%>
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
