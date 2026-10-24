<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="gms" uri="/WEB-INF/gms-functions.tld" %>
<%--
================================================================================
 my-bookings.jsp — MÀN "GÓI CỦA TÔI" (/customer/bookings?package=<mã gói | single>). CHỈ XEM.

 AI GỬI DỮ LIỆU SANG?  MyBookingServlet.doGet gọi 2 Service, mỗi Service 1 lần:
   1) MyBookingService.getMyBookings(accountId, packageKey) → booking (MyBookingView)  — lỗi thì bookingError
   2) CustomerDashboardService.getDashboard(...)     → dashboard (tên, mã khách, canUseGym)

   Biến JSP                Ai tạo                                  DAO đọc bảng nào
   ─────────────────────── ─────────────────────────────────────── ──────────────────────────────────────────────
   booking.activePackages  DAO tạo MyPtPackageView, Service chia    MyPtSessionDAO.findMyPackages:
   booking.endedPackages   "Đang tập" / "Đã kết thúc" + nhãn         Subscriptions + Orders (PackageNameSnapshot)
                                                                     + Employees/Persons (tên, SĐT PT)
   booking.sessions        DAO tạo MyPtSessionRow, Service gắn       MyPtSessionDAO.findSessionsOfPackage / findSingleSessions:
     (và focusSession,     nhãn trạng thái, lý do, link Zalo         Sessions + TimeSlots + Subscriptions + Orders + Persons
      offPattern, watchList)
   booking.weekTemplate    Service applyWeekTemplate (tự tính)       –
   s.moveNote              Service moveNote                          MyBookingDAO.findMoves: SessionMoveLogs
   booking.ctaKind, hotline, openingHours   Service chooseCta / fillCounterInfo (P-19, AppConstants)
   dashboard.canUseGym     CustomerDashboardService                  CustomerDashboardDAO.canUseGymToday:
                                                                     Subscriptions Membership/DayPass Active phủ hôm nay

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

<%-- ===== BIẾN DÙNG CHUNG CẢ TRANG =====
     sp  = booking.selectedPackage  gói đang xem ở cột phải (null khi xem "Buổi lẻ")
     f   = booking.focusSession     "Buổi tới": buổi Scheduled đầu tiên chưa kết thúc (Service chọn)
     cta = booking.ctaKind          buyMembership | renewAtCounter | rejoin | ptPackage | rỗng --%>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<c:set var="pageUrl" value="${ctx}/customer/bookings" />
<c:set var="sp" value="${booking.selectedPackage}" />
<c:set var="f" value="${booking.focusSession}" />
<c:set var="cta" value="${booking.ctaKind}" />

<%-- Link sang Lịch tập tuần: backPackage = gói đang xem (để Lịch tập có nút quay về đúng gói); các dòng bên dưới nối thêm date + openSession.
     VD /customer/pt-sessions?view=week&backPackage=82 --%>
<c:set var="backPackageKey" value="${booking.singleSelected ? 'single' : sp.subscriptionId}" />
<c:set var="weekLink" value="${ctx}/customer/pt-sessions?view=week&amp;backPackage=${backPackageKey}" />

<%-- Tab mở sẵn = tab chứa thứ đang xem ở cột phải: 'single' | 'ended' (sp.active = false) | 'active'. --%>
<c:set var="openTab" value="${booking.singleSelected ? 'single' : (not empty sp and not sp.active ? 'ended' : 'active')}" />
<!DOCTYPE html>
<html lang="vi">
    <head>
        <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
        <title>Gói của tôi · Super Gym</title>
        <link rel="stylesheet" href="${ctx}/css/pages/customer/dashboard.css?v=20261024flat">
        <link rel="stylesheet" href="${ctx}/css/pages/customer/session-status.css?v=20261025a">
        <link rel="stylesheet" href="${ctx}/css/pages/customer/my-bookings.css?v=20261025a">
        <link rel="stylesheet" href="${ctx}/css/pages/auth/notification-bell.css">
        <script src="${ctx}/js/pages/auth/notification-bell.js" defer></script>
    </head>
    <body class="dock ${themeClass}">
        <%@ include file="/WEB-INF/jsp/common/flash-toast.jspf" %>
        <div class="app">
            <%-- Thanh menu trái của khách. --%>
            <%@ include file="/WEB-INF/jsp/common/layout-customer.jspf" %>
            <div class="main">
                <%-- Topbar dùng chung với Lịch tập: tiêu đề "Gói của tôi" + nút (i) + chuông + tên/mã khách. --%>
                <c:set var="scheduleTitle" value="Gói của tôi" />
                <%@ include file="_schedule-header.jspf" %>

                <main class="content booking-page" id="main-content" tabindex="-1">
                    <c:choose>
                        <%-- ===== TRƯỜNG HỢP LỖI: bookingError (Service trả khi DAO lỗi) → hộp "Chưa tải được gói tập" + nút Tải lại ===== --%>
                        <c:when test="${not empty bookingError}">
                            <div class="empty-state" role="alert">
                                <h2>Chưa tải được gói tập</h2>
                                <p><c:out value="${bookingError}" /></p>
                                <a class="btn btn-outline" href="${pageUrl}">Tải lại</a>
                            </div>
                        </c:when>

                        <%-- ===== TRƯỜNG HỢP CHƯA CÓ GÌ: booking.noData (không gói PT, không buổi lẻ) → nút cam "Đặt buổi lẻ" ===== --%>
                        <c:when test="${booking.noData}">
                            <div class="empty-state">
                                <h2>Bạn chưa có gói PT hoặc buổi lẻ</h2>
                                <p>Đặt một buổi lẻ online, hoặc đăng ký gói PT tại quầy lễ tân.</p>
                                <a class="btn btn-primary" href="${ctx}/customer/book-session">Đặt buổi lẻ</a>
                            </div>
                        </c:when>

                        <c:otherwise>
                            <div class="booking-layout">

                                <%-- ##################################################################
                                     CỘT TRÁI — BẮT ĐẦU
                                     ################################################################## --%>
                                <aside class="booking-rail" aria-label="Gói, PT và quyền vào phòng tập">

                                    <%-- ==========================================================
                                         KHỐI 1. GÓI CỦA BẠN — BẮT ĐẦU
                                         ========================================================== --%>
                                    <section class="panel booking-box" aria-labelledby="packages-title">
                                        <div class="booking-box-head">
                                            <h2 id="packages-title">Gói của bạn</h2>
                                            <span class="pt-info"><button type="button" class="pt-info-trigger" aria-label="Giải thích các nhóm gói" aria-describedby="hint-tabs"><span aria-hidden="true">i</span></button><span id="hint-tabs" class="pt-info-popover" role="tooltip"><strong>Đang tập:</strong> gói PT còn hạn.<br><strong>Đã kết thúc:</strong> gói hết hạn hoặc đã dùng hết buổi.<br><strong>Buổi lẻ:</strong> các buổi bạn đặt riêng từng buổi, không thuộc gói nào.</span></span>
                                        </div>

                                        <%-- 3 tab, mỗi tab có số đếm trong vòng tròn:
                                               "Đang tập 1"    = số phần tử booking.activePackages
                                               "Đã kết thúc 0" = số phần tử booking.endedPackages
                                               "Buổi lẻ 0"     = booking.singleSessionCount (Service đếm) --%>
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

                                        <%-- Tab "Đang tập": mỗi gói 1 thẻ (_booking-package.jspf), VD "Gói PT 2 Tháng / PT Sơn · còn 10 buổi". --%>
                                        <div class="booking-tab-panel" role="tabpanel" id="panel-active" aria-labelledby="tab-active" data-tab-panel="active" ${openTab eq 'active' ? '' : 'hidden'}>
                                            <c:forEach var="p" items="${booking.activePackages}">
                                                <%@ include file="_booking-package.jspf" %>
                                            </c:forEach>
                                            <c:if test="${empty booking.activePackages}"><p class="booking-muted">Bạn không có gói PT nào đang tập.</p></c:if>
                                        </div>

                                        <%-- Tab "Đã kết thúc": cùng kiểu thẻ. --%>
                                        <div class="booking-tab-panel" role="tabpanel" id="panel-ended" aria-labelledby="tab-ended" data-tab-panel="ended" ${openTab eq 'ended' ? '' : 'hidden'}>
                                            <c:forEach var="p" items="${booking.endedPackages}">
                                                <%@ include file="_booking-package.jspf" %>
                                            </c:forEach>
                                            <c:if test="${empty booking.endedPackages}"><p class="booking-muted">Chưa có gói nào kết thúc.</p></c:if>
                                        </div>

                                        <%-- Tab "Buổi lẻ": 1 thẻ "Tất cả buổi lẻ / 3 buổi · mỗi buổi đặt riêng" + link "Đặt buổi lẻ". --%>
                                        <div class="booking-tab-panel" role="tabpanel" id="panel-single" aria-labelledby="tab-single" data-tab-panel="single" ${openTab eq 'single' ? '' : 'hidden'}>
                                            <c:choose>
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
                                    <%-- KHỐI 1. GÓI CỦA BẠN — KẾT THÚC --%>

                                    <%-- ==========================================================
                                         KHỐI 2. PT CỦA BẠN — BẮT ĐẦU
                                         Có buổi tới → PT của buổi đó (có thể là PT dạy thay); không có → PT của gói.
                                           ptName  ← f.ptName / sp.ptName   (DAO: Persons.FullName của Sessions.PtEmployeeId)
                                           ptPhone ← f.ptPhone / sp.ptPhone (DAO: Persons.Phone)
                                           ptZalo  ← f.zaloUrl / sp.zaloUrl (Service: ZaloUtil.toUrl(SĐT PT))
                                         Hiện: nhãn "PT BUỔI TỚI" + "Đinh Công Sơn" + nút "📞 0912000005" + nút xanh "Nhắn Zalo ↗".
                                         ========================================================== --%>
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
                                    <%-- KHỐI 2. PT CỦA BẠN — KẾT THÚC --%>

                                    <%-- ==========================================================
                                         KHỐI 3. VÀO PHÒNG TẬP — BẮT ĐẦU
                                         Gói PT KHÔNG kèm quyền vào gym: cần gói thành viên hoặc vé ngày (JOB-04 tính vắng nếu thiếu).
                                         dashboard.canUseGym ← CustomerDashboardDAO.canUseGymToday (Subscriptions Membership/DayPass
                                         Active phủ hôm nay) → chấm xanh "Hôm nay vào được" hoặc chấm vàng "Hôm nay chưa vào được".
                                         ========================================================== --%>
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
                                            <%-- Lời mời bên phải đã có nút "Mua gói thành viên" thì ở đây không lặp lại link. --%>
                                            <c:if test="${cta ne 'buyMembership'}">
                                                <a class="booking-add" href="${ctx}/customer/packages/purchase"><i class="fa-solid fa-id-card" aria-hidden="true"></i> Gói thành viên &amp; vé ngày</a>
                                            </c:if>
                                        </section>
                                    </c:if>
                                    <%-- KHỐI 3. VÀO PHÒNG TẬP — KẾT THÚC --%>
                                </aside>
                                <%-- CỘT TRÁI — KẾT THÚC --%>


                                <%-- ##################################################################
                                     CỘT PHẢI — BẮT ĐẦU
                                     ################################################################## --%>
                                <section class="panel booking-detail" aria-labelledby="booking-heading">

                                    <%-- ==========================================================
                                         KHỐI 4. ĐẦU GÓI — BẮT ĐẦU
                                         ========================================================== --%>
                                    <header class="booking-overview">
                                        <div class="booking-overview-main">
                                            <%-- Tên gói: sp.packageName ← Orders.PackageNameSnapshot (tên lúc mua), VD "Gói PT 2 Tháng"; xem buổi lẻ → "Buổi lẻ". --%>
                                            <h2 id="booking-heading"><c:out value="${booking.singleSelected ? 'Buổi lẻ' : sp.packageName}" /></h2>
                                            <%-- Dòng nhạt: "PT cá nhân · 07/09/2026 – 07/11/2026"
                                                   sp.typeLabel   Service đổi từ Subscriptions.SubscriptionType
                                                   sp.partnerName gói đôi: " cùng <tên người còn lại>"
                                                   sp.startDate / endDate ← Subscriptions.StartDate / EndDate --%>
                                            <c:if test="${not booking.singleSelected}">
                                                <p class="booking-muted">
                                                    <c:out value="${sp.typeLabel}" /><c:if test="${not empty sp.partnerName}"> cùng <c:out value="${sp.partnerName}" /></c:if>
                                                    · <c:out value="${gms:formatDate(sp.startDate)}" /> – <c:out value="${gms:formatDate(sp.endDate)}" />
                                                </p>
                                            </c:if>

                                            <%-- ----- 4a. Buổi tới -----
                                                 1 nhãn + 1 dòng ngày giờ (không lặp thứ/ngày). Hôm nay / ngày mai thì nói thẳng.
                                                   booking.daysUntilFocus (Service tính) 0 → " · Hôm nay", 1 → " · Ngày mai"
                                                   f.dayName + f.slotDate + f.startTime/endTime ← TimeSlots → chữ to "Thứ 2, 26/10 · 18:00 – 19:30"
                                                   f.statusKey = 'warn' → nhãn vàng: "Cần Membership/DayPass" (f.noGymPass) / "Ngày phòng nghỉ" / "Cần chú ý";
                                                   rê chuột vào nhãn thấy câu đầy đủ f.explanation (Service viết). --%>
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
                                                <%-- Link DUY NHẤT sang Lịch tập "Xem trên lịch tập ›": mở đúng tuần + đúng buổi tới.
                                                     VD /customer/pt-sessions?view=week&backPackage=82&date=2026-10-26&openSession=120 --%>
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

                                        <%-- ----- 4b. Vòng tiến độ (chỉ gói PT; buổi lẻ không có số buổi của gói) -----
                                               left = sp.totalSessions (Subscriptions.GeneratedSessionCount) − sp.pastSessions (DAO đếm buổi trước hôm nay)
                                               sp.progressPercent → độ dài vòng cam; còn ≤ 3 buổi → vòng đổi màu (is-ending)
                                             Hiện: vòng tròn "còn 10 buổi" + dòng dưới "35/45 đã qua". --%>
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
                                    <%-- KHỐI 4. ĐẦU GÓI — KẾT THÚC --%>


                                    <%-- ==========================================================
                                         KHỐI 5. LỜI MỜI HÀNH ĐỘNG (tối đa 1) — BẮT ĐẦU
                                         cta (Service chooseCta) chọn 1 trong 4 nội dung:
                                           buyMembership  buổi sắp tới chưa vào được gym → nút cam "Mua gói thành viên"
                                           renewAtCounter gói còn ≤ 3 buổi hoặc ≤ 14 ngày → "Gói còn N buổi, hết hạn ngày …" + nút "Gọi lễ tân 1900 6868"
                                           rejoin         gói đã kết thúc → "Muốn tập tiếp với PT …?"
                                           ptPackage      ≥ 3 buổi lẻ / 30 ngày → "Tập đều hơn với gói PT" (nút viền)
                                         customerCode ← dashboard.customerCode (Customers, VD "KH-1006"); openingHours ← P-19.
                                         ========================================================== --%>
                                    <c:if test="${not empty cta}">
                                        <c:set var="customerCode" value="${dashboard.customerCode}" />
                                        <section class="booking-cta ${cta eq 'ptPackage' ? 'is-soft' : ''}" aria-labelledby="cta-title">
                                            <%-- Biểu tượng trái: ⚠ (mua gói) / ⟳ lặp (gói PT) / ↻ (gia hạn, đăng ký lại). --%>
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
                                                        <%-- booking.remainingCount (Service đếm buổi chưa diễn ra); sp.endDate ← Subscriptions.EndDate. --%>
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
                                                        <%-- booking.recentSingleCount (Service đếm buổi lẻ 30 ngày gần nhất). --%>
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
                                                        <%-- booking.hotlineTel "19006868" cho link tel:, booking.hotline "1900 6868" để đọc (AppConstants). --%>
                                                        <a class="btn ${cta eq 'ptPackage' ? 'btn-outline' : 'btn-primary'} btn-sm" href="tel:${booking.hotlineTel}">
                                                            <i class="fa-solid fa-phone" aria-hidden="true"></i> Gọi lễ tân <span class="booking-cta-phone"><c:out value="${booking.hotline}" /></span>
                                                        </a>
                                                    </c:otherwise>
                                                </c:choose>
                                            </div>
                                        </section>
                                    </c:if>
                                    <%-- KHỐI 5. LỜI MỜI — KẾT THÚC --%>


                                    <c:choose>
                                        <c:when test="${not booking.singleSelected}">

                                            <%-- ==========================================================
                                                 KHỐI 6. LỊCH CỐ ĐỊNH — BẮT ĐẦU   (đang xem 1 gói PT)
                                                 booking.weekTemplate = 5 WeekdaySlot T2 → T6, Service applyWeekTemplate tự tính từ booking.sessions:
                                                 mỗi thứ lấy giờ có nhiều buổi nhất. d.kind: main (giờ chính) | odd (viền đứt, giờ khác) |
                                                 mixed ("~ không cố định") | none ("— không có buổi").
                                                 Hiện mỗi ô: "T2" / "18:00" to / "9 buổi"; ô của hôm nay có gạch cam dưới.
                                                 Câu tóm tắt "T2 – T6 lúc 18:00" (templateSummary) chỉ làm nhãn cho trình đọc màn hình.
                                                 ========================================================== --%>
                                            <section class="booking-section" aria-labelledby="pattern-title">
                                                <div class="booking-section-head">
                                                    <h3 id="pattern-title">Lịch cố định</h3>
                                                    <span class="pt-info"><button type="button" class="pt-info-trigger" aria-label="Cách đọc lịch cố định" aria-describedby="hint-pattern"><span aria-hidden="true">i</span></button><span id="hint-pattern" class="pt-info-popover" role="tooltip">Các ô là giờ tập lặp lại từ Thứ 2 đến Thứ 6.<br><strong>Viền đứt:</strong> giờ khác các thứ còn lại.<br><strong>Không cố định:</strong> giờ thay đổi theo tuần.<br><strong>Buổi khác giờ:</strong> do đổi lịch, PT dời/chuyển PT hoặc khung cố định đã kín.</span></span>
                                                    <%-- "44/45 buổi theo lịch này" = templateCount / totalCount (Service đếm). --%>
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

                                                <%-- ----- 6b. Buổi khác giờ -----
                                                     booking.offPattern = buổi lệch lịch cố định. Mỗi dòng:
                                                       "Thứ 7, 12/09/2026" (link sang đúng buổi) · "09:00 – 10:30" ·
                                                       s.moveNote ← MyBookingDAO.findMoves (SessionMoveLogs), VD "Bạn đổi buổi · thay buổi T6 11/09" ·
                                                       nhãn trạng thái s.statusLabel (màu theo s.statusKey, session-status.css), VD "● Đã check-in". --%>
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
                                            <%-- KHỐI 6. LỊCH CỐ ĐỊNH — KẾT THÚC --%>

                                            <%-- ==========================================================
                                                 KHỐI 7. CÁC BUỔI CỦA GÓI — BẮT ĐẦU
                                                 booking.sessions (DAO: Sessions + TimeSlots, theo thứ tự buổi 1 → N).
                                                 1 ô = 1 buổi, vạch dọc = sang tuần mới (s.firstOfWeek, Service đánh dấu).
                                                 Màu + hình theo s.statusKey (session-status.css): đặc xanh = check-in, × đỏ = vắng,
                                                 gạch chéo = hủy, viền đứt = đang cập nhật, viền = sắp tới; buổi tới có viền đậm (is-next).
                                                 Rê chuột: "Buổi 12/45 · 26/10/2026 · Sắp tới". Bấm ô → Lịch tập đúng buổi.
                                                 ========================================================== --%>
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
                                                    <%-- Chú thích có số (Service đếm), chỉ hiện loại có > 0 buổi:
                                                         "■ Đã check-in 13  × Vắng 6  ⊘ Đã hủy 1  ⬚ Đang cập nhật 15  □ Sắp tới 10". --%>
                                                    <ul class="booking-key">
                                                        <c:if test="${booking.checkedInCount gt 0}"><li><i class="booking-dot" data-status="checkedIn"></i>Đã check-in <b>${booking.checkedInCount}</b></li></c:if>
                                                        <c:if test="${booking.missedCount gt 0}"><li><i class="booking-dot" data-status="missed"></i>Vắng <b>${booking.missedCount}</b></li></c:if>
                                                        <c:if test="${booking.cancelledCount gt 0}"><li><i class="booking-dot" data-status="cancelled"></i>Đã hủy <b>${booking.cancelledCount}</b></li></c:if>
                                                        <c:if test="${booking.pendingCount gt 0}"><li><i class="booking-dot" data-status="pending"></i>Đang cập nhật <b>${booking.pendingCount}</b></li></c:if>
                                                        <c:if test="${booking.remainingCount gt 0}"><li><i class="booking-dot" data-status="upcoming"></i>Sắp tới <b>${booking.remainingCount}</b></li></c:if>
                                                    </ul>
                                                </section>
                                            </c:if>
                                            <%-- KHỐI 7. CÁC BUỔI CỦA GÓI — KẾT THÚC --%>
                                        </c:when>

                                        <c:otherwise>
                                            <%-- ==========================================================
                                                 KHỐI 6'. BUỔI LẺ — BẮT ĐẦU   (đang xem nhóm "Buổi lẻ")
                                                 booking.sessions ← MyPtSessionDAO.findSingleSessions; Service xếp sắp tới trước, rồi đã qua (mới nhất trước).
                                                 Mỗi dòng: ngày (link) · giờ · "PT Đinh Công Sơn" · nhãn trạng thái ·
                                                 "Đặt lại với PT này" (s.rebook, Service chọn buổi lẻ gần nhất của mỗi PT; s.ptEmployeeId ← Sessions.PtEmployeeId).
                                                 ========================================================== --%>
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
                                            <%-- KHỐI 6'. BUỔI LẺ — KẾT THÚC --%>
                                        </c:otherwise>
                                    </c:choose>


                                    <%-- ==========================================================
                                         KHỐI 8. CẦN ĐỂ Ý — BẮT ĐẦU
                                         booking.watchList: buổi SẮP TỚI chưa có Membership/DayPass (còn kịp mua) — Service buildWatchList.
                                         Không có → ẩn cả mục. Thu gọn: "▸ CẦN ĐỂ Ý ……… 10 buổi"; bấm mở ra các nhóm (_booking-groups.jspf).
                                         <details> tự mở / đóng bằng chuột và bàn phím, không cần JS.
                                         ========================================================== --%>
                                    <c:if test="${not empty booking.watchList}">
                                        <details class="booking-section booking-watch">
                                            <summary class="booking-section-head">
                                                <span class="booking-watch-title">Cần để ý</span>
                                                <span class="booking-count">${fn:length(booking.watchList)} buổi</span>
                                            </summary>
                                            <p class="booking-muted">Buổi sắp tới rơi vào ngày bạn chưa có gói thành viên hoặc vé ngày hợp lệ. Mua trước ngày tập để buổi không bị tính vắng.</p>
                                            <%-- groups = booking.watchGroups: Service gom watchList theo lý do (groupByReason). --%>
                                            <c:set var="groups" value="${booking.watchGroups}" />
                                            <%@ include file="_booking-groups.jspf" %>
                                        </details>
                                    </c:if>
                                    <%-- KHỐI 8. CẦN ĐỂ Ý — KẾT THÚC --%>
                                </section>
                                <%-- CỘT PHẢI — KẾT THÚC --%>
                            </div>
                        </c:otherwise>
                    </c:choose>
                </main>
            </div>
        </div>
    </body>
</html>
