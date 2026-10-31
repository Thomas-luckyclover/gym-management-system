<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="gms" uri="/WEB-INF/gms-functions.tld" %>
<%--
================================================================================
 body-stats.jsp — MÀN CHỈ SỐ CƠ THỂ CỦA KHÁCH (/customer/body-stats). Chỉ khách thấy, PT không xem.

 AI GỬI DỮ LIỆU SANG?  BodyStatsServlet.doGet gọi BodyStatsService.getPage MỘT lần → "stats" (BodyStatsView).
   stats.tiles   5 ô số: mới nhất + thay đổi so với lần đầu (bảng BodyMeasurements, BMI Service tính)
   stats.chart*  biểu đồ đường của chỉ số đang chọn (?metric=), toạ độ SVG Service tính sẵn → không cần JS
   stats.rows    lịch sử đo, mới nhất trước
   dashboard     tên + mã khách góc phải (CustomerDashboardService)

 BỐ CỤC
   1. 5 ô số (bấm ô = vẽ biểu đồ chỉ số đó)
   2. Biểu đồ (trái, rộng)  |  Form "Ghi lần đo" (phải)
   3. Lịch sử đo (bảng — cũng là bản đọc được của biểu đồ)
================================================================================ --%>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<!DOCTYPE html>
<html lang="vi">
<head>
    <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
    <title>Chỉ số cơ thể · Super Gym</title>
    <link rel="stylesheet" href="${ctx}/css/pages/customer/body-stats.css?v=20261031a">
    <link rel="stylesheet" href="${ctx}/css/pages/auth/notification-bell.css">
    <script src="${ctx}/js/pages/auth/notification-bell.js" defer></script>
</head>
<body class="dock theme-pt body-stats-page">
<%@ include file="/WEB-INF/jsp/common/flash-toast.jspf" %>
<div class="app">
    <%@ include file="/WEB-INF/jsp/common/layout-customer.jspf" %>
    <div class="main">
        <header class="topbar">
            <div class="tb-left">
                <h1 class="tb-title">Chỉ số cơ thể</h1>
                <%-- Hint box chung (.pt-info, common.js điều khiển): bấm / di chuột vào nút i để đọc. --%>
                <span class="pt-info">
                    <button type="button" class="pt-info-trigger" aria-label="Hướng dẫn Chỉ số cơ thể" aria-describedby="stats-help"><span aria-hidden="true">i</span></button>
                    <span id="stats-help" class="pt-info-popover" role="tooltip">
                        <strong>Ghi lần đo:</strong> nhập cân nặng + chiều cao, BMI tự tính. Mỡ, cơ, vòng eo không bắt buộc (lấy từ máy InBody tại phòng). Lưu lại cùng ngày = sửa lần đo ngày đó.<br>
                        <strong>BMI (người châu Á):</strong> dưới 18,5 thiếu cân · 18,5–22,9 bình thường · 23–24,9 thừa cân · từ 25 béo phì.<br>
                        <strong>Biểu đồ:</strong> bấm một ô số để xem thay đổi của chỉ số đó. Chỉ bạn xem được trang này.
                    </span>
                </span>
            </div>
            <div class="top-right">
                <%@ include file="/WEB-INF/jsp/common/notification-bell.jspf" %>
                <%@ include file="/WEB-INF/jsp/common/topbar-user.jspf" %>
            </div>
        </header>

        <main class="content stats" id="main-content" tabindex="-1">
            <c:choose>
                <c:when test="${not empty statsError}">
                    <section class="panel stats-error" role="alert"><p><c:out value="${statsError}" /></p>
                        <a class="btn btn-outline" href="${ctx}/customer/body-stats">Tải lại</a></section>
                </c:when>
                <c:otherwise>
                    <%-- ===== 1. 5 Ô SỐ — mỗi ô là link ?metric= để vẽ biểu đồ chỉ số đó ===== --%>
                    <nav class="stats-tiles" aria-label="Chọn chỉ số để xem biểu đồ">
                        <c:forEach var="t" items="${stats.tiles}">
                            <a class="stats-tile ${t.metric eq stats.metric ? 'is-active' : ''}" href="${ctx}/customer/body-stats?metric=${t.metric}"
                               aria-current="${t.metric eq stats.metric ? 'true' : 'false'}">
                                <span class="stats-tile-label"><c:out value="${t.label}" /></span>
                                <span class="stats-tile-value"><c:out value="${t.value}" /><c:if test="${t.value ne '—' and not empty t.unit}"><small> <c:out value="${t.unit}" /></small></c:if></span>
                                <c:if test="${not empty t.note}"><span class="stats-chip is-${t.tone}"><c:out value="${t.note}" /></span></c:if>
                                <span class="stats-tile-change"><c:out value="${not empty t.change ? t.change : 'Cần ít nhất 2 lần đo'}" /></span>
                            </a>
                        </c:forEach>
                    </nav>

                    <div class="stats-main">
                        <%-- ===== 2a. BIỂU ĐỒ — SVG vẽ từ toạ độ Service tính (viewBox 640×240) ===== --%>
                        <section class="panel stats-chart" aria-labelledby="stats-chart-title">
                            <header class="stats-head">
                                <h2 id="stats-chart-title"><c:out value="${stats.metricLabel}" /></h2>
                                <c:if test="${stats.hasChart}"><p><c:out value="${stats.chartFrom}" /> – <c:out value="${stats.chartTo}" /> · <c:out value="${fn:length(stats.chartPoints)}" /> lần đo</p></c:if>
                            </header>
                            <c:choose>
                                <c:when test="${stats.hasChart}">
                                    <svg class="stats-svg" viewBox="0 0 640 240" role="img" aria-labelledby="stats-chart-title stats-chart-desc">
                                        <desc id="stats-chart-desc">Biểu đồ đường từ <c:out value="${stats.chartFrom}" /> đến <c:out value="${stats.chartTo}" />. Số liệu chi tiết ở bảng Lịch sử đo.</desc>
                                        <c:forEach var="tick" items="${stats.chartTicks}">
                                            <line class="grid" x1="44" x2="624" y1="${tick.y}" y2="${tick.y}" />
                                            <text class="axis" x="36" y="${tick.y + 4}" text-anchor="end"><c:out value="${tick.label}" /></text>
                                        </c:forEach>
                                        <polygon class="area" points="${stats.chartArea}" />
                                        <polyline class="line" points="${stats.chartLine}" />
                                        <c:forEach var="pt" items="${stats.chartPoints}" varStatus="loop">
                                            <g class="dot ${loop.last ? 'is-last' : ''}">
                                                <circle cx="${pt.x}" cy="${pt.y}" r="${loop.last ? 6 : 4}"><title><c:out value="${pt.label}" /></title></circle>
                                            </g>
                                        </c:forEach>
                                        <text class="axis" x="44" y="234"><c:out value="${stats.chartFrom}" /></text>
                                        <text class="axis" x="624" y="234" text-anchor="end"><c:out value="${stats.chartTo}" /></text>
                                    </svg>
                                </c:when>
                                <c:otherwise>
                                    <p class="stats-empty">Cần ít nhất 2 lần đo có chỉ số này để vẽ biểu đồ. Ghi lần đo ở khung bên cạnh.</p>
                                </c:otherwise>
                            </c:choose>
                        </section>

                        <%-- ===== 2b. FORM GHI LẦN ĐO → POST action=save → BodyStatsService.save. Cùng ngày = ghi đè. ===== --%>
                        <section class="panel stats-form" aria-labelledby="stats-form-title">
                            <header class="stats-head">
                                <h2 id="stats-form-title">Ghi lần đo</h2>
                                <p>Cân, đo tại phòng hoặc ở nhà. BMI tự tính.</p>
                            </header>
                            <form method="post" action="${ctx}/customer/body-stats" class="stats-fields">
                                <input type="hidden" name="csrfToken" value="<c:out value='${csrfToken}' />">
                                <input type="hidden" name="action" value="save">
                                <input type="hidden" name="metric" value="${stats.metric}">
                                <label class="stats-field is-wide"><span>Ngày đo</span>
                                    <input type="date" name="measuredOn" value="${stats.today}" max="${stats.today}" required>
                                </label>
                                <label class="stats-field"><span>Cân nặng <small>kg</small></span>
                                    <input type="text" name="weight" inputmode="decimal" autocomplete="off" placeholder="58,6" required>
                                </label>
                                <label class="stats-field"><span>Chiều cao <small>cm</small></span>
                                    <input type="text" name="height" inputmode="decimal" autocomplete="off" value="${stats.lastHeightText}" placeholder="160" required>
                                </label>
                                <label class="stats-field"><span>Tỷ lệ mỡ <small>% · không bắt buộc</small></span>
                                    <input type="text" name="fat" inputmode="decimal" autocomplete="off" placeholder="27,9">
                                </label>
                                <label class="stats-field"><span>Tỷ lệ cơ <small>% · không bắt buộc</small></span>
                                    <input type="text" name="muscle" inputmode="decimal" autocomplete="off" placeholder="38,3">
                                </label>
                                <label class="stats-field is-wide"><span>Vòng eo <small>cm · không bắt buộc</small></span>
                                    <input type="text" name="waist" inputmode="decimal" autocomplete="off" placeholder="73">
                                </label>
                                <label class="stats-field is-wide"><span>Ghi chú <small>không bắt buộc</small></span>
                                    <input type="text" name="note" maxlength="200" placeholder="VD: Đo InBody tại phòng">
                                </label>
                                <button class="btn btn-primary is-wide" type="submit">Lưu lần đo</button>
                            </form>
                        </section>
                    </div>

                    <%-- ===== 3. LỊCH SỬ ĐO (mới nhất trước) — xóa 1 dòng: POST action=delete (common.js hỏi xác nhận) ===== --%>
                    <section class="panel stats-history" aria-labelledby="stats-history-title">
                        <header class="stats-head"><h2 id="stats-history-title">Lịch sử đo</h2>
                            <p><c:out value="${fn:length(stats.rows)}" /> lần</p></header>
                        <c:choose>
                            <c:when test="${empty stats.rows}">
                                <p class="stats-empty">Chưa có lần đo nào. Ghi lần đầu để bắt đầu theo dõi thay đổi.</p>
                            </c:when>
                            <c:otherwise>
                                <div class="stats-table-wrap">
                                    <table class="stats-table">
                                        <%-- Cột số (class num) căn phải cả tiêu đề lẫn giá trị — quy ước bảng chung trong common.css. --%>
                                        <thead><tr><th scope="col">Ngày</th><th scope="col" class="num">Cân nặng</th><th scope="col" class="num">Thay đổi</th><th scope="col" class="num">BMI</th><th scope="col" class="num">Mỡ</th><th scope="col" class="num">Cơ</th><th scope="col" class="num">Vòng eo</th><th scope="col">Ghi chú</th><th scope="col"><span class="visually-hidden">Xóa</span></th></tr></thead>
                                        <tbody>
                                        <c:forEach var="r" items="${stats.rows}">
                                            <tr>
                                                <td class="date"><c:out value="${gms:formatDate(r.measuredOn)}" /></td>
                                                <td class="num"><c:out value="${r.weightText}" /></td>
                                                <td class="num is-${r.changeTone}"><c:out value="${r.changeText}" /></td>
                                                <td class="num" data-label="BMI"><c:out value="${r.bmiText}" /></td>
                                                <td class="num" data-label="Mỡ"><c:out value="${r.fatText}" /></td>
                                                <td class="num" data-label="Cơ"><c:out value="${r.muscleText}" /></td>
                                                <td class="num" data-label="Vòng eo"><c:out value="${r.waistText}" /></td>
                                                <td class="note"><c:out value="${r.note}" /></td>
                                                <td>
                                                    <form method="post" action="${ctx}/customer/body-stats">
                                                        <input type="hidden" name="csrfToken" value="<c:out value='${csrfToken}' />">
                                                        <input type="hidden" name="action" value="delete">
                                                        <input type="hidden" name="id" value="${r.id}">
                                                        <input type="hidden" name="metric" value="${stats.metric}">
                                                        <button class="stats-delete" type="submit" aria-label="Xóa lần đo ${gms:formatDate(r.measuredOn)}"
                                                                data-confirm-message="Xóa lần đo ngày ${gms:formatDate(r.measuredOn)}?" data-confirm-title="Xóa lần đo" data-confirm-text="Xóa">
                                                            <i class="fa-solid fa-trash-can" aria-hidden="true"></i></button>
                                                    </form>
                                                </td>
                                            </tr>
                                        </c:forEach>
                                        </tbody>
                                    </table>
                                </div>
                            </c:otherwise>
                        </c:choose>
                    </section>
                </c:otherwise>
            </c:choose>
        </main>
    </div>
</div>
</body>
</html>
