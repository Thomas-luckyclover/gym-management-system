<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="gms" uri="/WEB-INF/gms-functions.tld" %>
<%--
  MÀN "GÓI CỦA TÔI" — /customer/bookings?id=<mã gói | single>  (CHỈ XEM)
  *** BẢN TẠM: bảng thô để kiểm tra dữ liệu. Giao diện thật làm ở GĐ3. ***

  Servlet gửi sang: booking (MyBookingView), bookingError, dashboard
--%>
<c:set var="pageUrl" value="${pageContext.request.contextPath}/customer/bookings" />

<!DOCTYPE html>
<html lang="vi">
<head>
    <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
    <title>Gói của tôi · Super Gym</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/customer/dashboard.css?v=20261013c">
    <style>
        /* tạm: chỉ đủ để đọc dữ liệu */
        .bk-tmp { display: flex; gap: 24px; flex-wrap: wrap; align-items: flex-start; }
        .bk-tmp aside { flex: 0 1 320px; }
        .bk-tmp section { flex: 1 1 560px; }
        .bk-tmp table { width: 100%; border-collapse: collapse; }
        .bk-tmp th, .bk-tmp td { border: 1px solid var(--border, #456); padding: 6px; text-align: left; }
        .bk-tmp .is-focus { outline: 2px solid var(--primary, #FF4F2B); }
        .bk-tmp a[aria-current="true"] { font-weight: 700; }
    </style>
</head>
<body class="dock my-bookings-page ${themeClass}">
<div class="app">
    <%@ include file="/WEB-INF/jsp/common/layout-customer.jspf" %>
    <div class="main">
        <header class="topbar"><div class="tb-left"><h1 class="tb-title">Gói của tôi</h1></div></header>

        <main class="content" id="main-content" tabindex="-1">
            <c:if test="${not empty bookingError}"><p role="alert"><c:out value="${bookingError}" /></p></c:if>
            <c:if test="${not empty booking and booking.noData}"><p>Bạn chưa có gói PT hoặc buổi lẻ nào.</p></c:if>

            <c:if test="${not empty booking and not booking.noData}">
                <div class="bk-tmp">
                    <%-- ===== Cột trái: danh sách gói ===== --%>
                    <aside aria-label="Danh sách gói">
                        <h2>Đang tập (${fn:length(booking.activePackages)})</h2>
                        <c:forEach var="p" items="${booking.activePackages}">
                            <p><a href="${pageUrl}?id=${p.subscriptionId}" aria-current="${booking.selectedPackage.subscriptionId eq p.subscriptionId}">
                                <c:out value="${p.packageName}" /></a><br>
                                <c:out value="${p.typeLabel}" /> · PT <c:out value="${p.ptName}" /><br>
                                ${gms:formatDate(p.startDate)} – ${gms:formatDate(p.endDate)} · đã qua ${p.pastSessions}/${p.totalSessions} (${p.progressPercent}%)</p>
                        </c:forEach>

                        <h2>Đã kết thúc (${fn:length(booking.endedPackages)})</h2>
                        <c:forEach var="p" items="${booking.endedPackages}">
                            <p><a href="${pageUrl}?id=${p.subscriptionId}" aria-current="${booking.selectedPackage.subscriptionId eq p.subscriptionId}">
                                <c:out value="${p.packageName}" /></a> · <c:out value="${p.typeLabel}" /></p>
                        </c:forEach>

                        <h2>Buổi lẻ (${booking.singleSessionCount})</h2>
                        <c:if test="${booking.singleSessionCount gt 0}">
                            <p><a href="${pageUrl}?id=single" aria-current="${booking.singleSelected}">Các buổi lẻ</a></p>
                        </c:if>
                    </aside>

                    <%-- ===== Cột phải: gói đang chọn ===== --%>
                    <section aria-label="Buổi của gói">
                        <c:choose>
                            <c:when test="${booking.singleSelected}"><h2>Các buổi lẻ</h2></c:when>
                            <c:otherwise>
                                <c:set var="sp" value="${booking.selectedPackage}" />
                                <h2><c:out value="${sp.packageName}" /> (${sp.active ? 'Đang tập' : 'Đã kết thúc'})</h2>
                                <p>PT <c:out value="${sp.ptName}" /> · <c:out value="${sp.ptPhone}" />
                                    <c:if test="${not empty sp.zaloUrl}"> · <a href="${fn:escapeXml(sp.zaloUrl)}" target="_blank" rel="noopener">Nhắn Zalo</a></c:if>
                                    <c:if test="${not empty sp.partnerName}"> · Tập cùng <c:out value="${sp.partnerName}" /></c:if></p>
                            </c:otherwise>
                        </c:choose>

                        <p>Tất cả ${booking.totalCount} · Đã check-in ${booking.checkedInCount} · Vắng ${booking.missedCount}
                            · Đã hủy ${booking.cancelledCount} · Còn lại ${booking.remainingCount}
                            <c:if test="${booking.pendingCount gt 0}"> · Đang cập nhật ${booking.pendingCount}</c:if></p>

                        <table>
                            <thead><tr><th>Buổi</th><th>Ngày</th><th>Giờ</th><th>Trạng thái</th><th>Ghi chú</th></tr></thead>
                            <tbody>
                                <c:forEach var="s" items="${booking.sessions}">
                                    <tr class="${s.sessionId eq booking.focusSessionId ? 'is-focus' : ''}" data-status="${s.statusKey}">
                                        <td><c:if test="${not s.singleSession}">#${s.sessionNo}</c:if></td>
                                        <td>${gms:formatDate(s.slotDate)}</td>
                                        <td>${gms:formatTime(s.startTime)}–${gms:formatTime(s.endTime)}</td>
                                        <td>[<c:out value="${s.statusKey}" />] <c:out value="${s.statusLabel}" /></td>
                                        <td><c:out value="${s.explanation}" />
                                            <c:if test="${not empty s.calendarUrl}"><a href="${fn:escapeXml(s.calendarUrl)}" target="_blank" rel="noopener">Thêm vào Google Calendar</a></c:if></td>
                                    </tr>
                                </c:forEach>
                            </tbody>
                        </table>
                    </section>
                </div>
            </c:if>
        </main>
    </div>
</div>
</body>
</html>
