<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="gms" uri="/WEB-INF/gms-functions.tld" %>
<%--
================================================================================
 pt-sales.jsp — MÀN LỄ TÂN BÁN GÓI PT (/receptionist/pt-sales), UC-21 bản đơn giản hóa

 AI GỬI DỮ LIỆU SANG?
   PtSaleServlet.showPage gọi PtSaleService.prepare MỘT lần rồi đặt request attribute:
     sale        PtSaleView — mọi khối dưới đây đọc từ biến này (xem comment từng field trong PtSaleView.java)
     pageError   prepare lỗi → chỉ hiện khung đỏ, không vẽ gì khác
     saleError   bấm "Giữ lịch" bị từ chối → chữ đỏ ở cột phải
     csrfToken   ô ẩn của 3 form POST
     shiftNotice (do ReceptionistAreaFilter đặt) → thanh cảnh báo ca trong layout-receptionist.jspf

 BỐ CỤC (kiểu máy POS: trái tư vấn, phải "Đơn bán") — mỗi khối là 1 file _pt-sale-*.jspf cùng thư mục:
   _pt-sale-holds.jspf          Đơn đang giữ chỗ (đầu màn)
   _pt-sale-stepper.jspf        Thanh 4 bước
   ┌ CỘT TRÁI (sale-flow) ─────────────────────┐  ┌ CỘT PHẢI "Đơn bán" (sale-summary) ───────┐
   │ _pt-sale-step1.jspf   1. Gói & PT         │  │ mã đơn + đồng hồ (khi đang giữ)           │
   │ _pt-sale-plan.jspf    2. Lịch tập (JS vẽ) │  │ _pt-sale-customer-box.jspf  3. Khách      │
   │ _pt-sale-held.jspf    lưới buổi đang giữ  │  │ Gói / PT / Kỳ / Buổi / Phải thu           │
   └───────────────────────────────────────────┘  │ _pt-sale-actions.jspf  nút Giữ / Thu / Hủy│
                                                  └───────────────────────────────────────────┘
 JS: pt-sales.js (ô gợi ý khách, form bước 1 tự gửi) · pt-sale-plan.js (vẽ Lịch tập, điền ô ẩn form Giữ lịch)
================================================================================ --%>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<c:set var="base" value="${ctx}/receptionist/pt-sales" />
<!DOCTYPE html>
<html lang="vi">
<head>
    <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
    <title>Bán gói PT · Super Gym Lễ tân</title>
    <link rel="stylesheet" href="${ctx}/css/pages/receptionist/pt-sales.css?v=20261103c">
    <script src="${ctx}/js/pages/receptionist/pt-sales.js?v=20261103b" defer></script>
    <script src="${ctx}/js/pages/receptionist/pt-sale-plan.js?v=20261103c" defer></script>
</head>
<body class="dock theme-staff pt-sale-page">
<%@ include file="/WEB-INF/jsp/common/flash-toast.jspf" %>
<div class="app">
    <%@ include file="/WEB-INF/jsp/common/layout-receptionist.jspf" %>
    <div class="main">
        <header class="topbar">
            <div class="tb-left">
                <h1 class="tb-title">Bán gói PT</h1>
                <%-- Hint box chung (.pt-info, common.js điều khiển): bấm / di chuột vào nút i để đọc. --%>
                <span class="pt-info">
                    <button type="button" class="pt-info-trigger" aria-label="Hướng dẫn Bán gói PT" aria-describedby="sale-help"><span aria-hidden="true">i</span></button>
                    <span id="sale-help" class="pt-info-popover" role="tooltip">
                        <strong>1. Gói &amp; PT:</strong> tư vấn trước, chưa cần biết khách. Ngày bắt đầu từ hôm nay tới 30 ngày sau.<br>
                        <strong>2. Lịch tập:</strong> mỗi thứ chọn Nghỉ hoặc 1 giờ; xem từng tuần, bấm 1 ngày để sửa riêng ngày đó.<br>
                        <strong>3. Khách:</strong> ô tìm ở cột Đơn bán — tên không dấu, SĐT hoặc mã KH; chọn lúc nào cũng được, lịch giữ nguyên. Gói đôi cần thêm người tập cùng.<br>
                        <strong>4. Giữ chỗ:</strong> bấm "Giữ lịch 15 phút" trước khi thu tiền để không ai lấy mất khung. Quá 15 phút chưa thu tiền thì lịch tự trả lại cho PT.
                    </span>
                </span>
            </div>
            <div class="top-right">
                <%@ include file="/WEB-INF/jsp/common/notification-bell.jspf" %>
                <%@ include file="/WEB-INF/jsp/common/topbar-user.jspf" %>
            </div>
        </header>

        <main class="content sale" id="main-content" tabindex="-1">

        <%-- ===== Prepare lỗi → chỉ khung đỏ. Không lỗi → cả màn bán. ===== --%>
        <c:choose>
        <c:when test="${not empty pageError}">
            <section class="sale-card sale-alert" role="alert">
                <p><c:out value="${pageError}" /></p>
                <a class="btn btn-outline" href="${base}">Bán gói mới</a>
            </section>
        </c:when>
        <c:otherwise>

            <%-- Biến dùng chung cho các khối bên dưới:
                 holderOk       có khách đứng tên và khách đó mua được
                 customerReady  đủ khách để giữ lịch (gói đôi cần cả người tập cùng mua được)
                 done2          đã chọn đủ gói + PT + ngày bắt đầu (Service tính được ngày kết thúc kỳ) --%>
            <c:set var="holderOk" value="${not empty sale.holder and empty sale.holder.blockReason}" />
            <c:set var="customerReady" value="${holderOk and (not sale.couple or (not empty sale.partner and empty sale.partner.blockReason))}" />
            <c:set var="done2" value="${not empty sale.endDate}" />


            <%@ include file="/WEB-INF/jsp/receptionist/_pt-sale-holds.jspf" %>


            <%@ include file="/WEB-INF/jsp/receptionist/_pt-sale-stepper.jspf" %>


            <div class="sale-layout">

            <%-- ===================== BẮT ĐẦU: CỘT TRÁI — TƯ VẤN ===================== --%>
            <div class="sale-flow">

                <%@ include file="/WEB-INF/jsp/receptionist/_pt-sale-step1.jspf" %>

                <%@ include file="/WEB-INF/jsp/receptionist/_pt-sale-plan.jspf" %>

                <%@ include file="/WEB-INF/jsp/receptionist/_pt-sale-held.jspf" %>

            </div>
            <%-- ===================== KẾT THÚC: CỘT TRÁI ===================== --%>


            <%-- ===================== BẮT ĐẦU: CỘT PHẢI — "ĐƠN BÁN" ===================== --%>
            <aside class="sale-card sale-summary" aria-labelledby="sale-h-sum">
                <header class="sale-card-head"><h2 id="sale-h-sum">Đơn bán</h2>
                    <c:if test="${sale.held}"><p class="sale-code"><c:out value="${sale.heldCode}" /></p></c:if></header>

                <c:if test="${sale.held}">
                    <%-- Đồng hồ giữ chỗ: common.js bindCountdowns đếm mm:ss, hết giờ tải lại (Service đã nhả khung). --%>
                    <p class="sale-timer">
                        <i class="fa-regular fa-clock" aria-hidden="true"></i>
                        <span>Giữ chỗ còn</span>
                        <b data-countdown-seconds="${sale.holdSeconds}" data-countdown-reload="true">--:--</b>
                    </p>
                </c:if>


                <%@ include file="/WEB-INF/jsp/receptionist/_pt-sale-customer-box.jspf" %>


                <%-- Tóm tắt đơn: gói, PT, kỳ, số buổi + giá 1 buổi (pt-sale-plan.js cập nhật ô [data-plan-count] khi chọn giờ). --%>
                <dl class="sale-facts">
                    <div><dt>Gói</dt><dd><c:out value="${not empty sale.packageName ? sale.packageName : '—'}" /></dd></div>
                    <div><dt>PT</dt><dd><c:out value="${not empty sale.ptName ? sale.ptName : '—'}" /></dd></div>
                    <div><dt>Kỳ</dt><dd><c:choose><c:when test="${done2}"><c:out value="${gms:formatDate(sale.startDate)}" /> – <c:out value="${gms:formatDate(sale.endDate)}" /></c:when><c:otherwise>—</c:otherwise></c:choose></dd></div>
                    <div><dt>Buổi</dt><dd data-plan-count><c:choose><c:when test="${sale.sessionCount gt 0}"><c:out value="${sale.sessionCount}" /> buổi · <c:out value="${gms:formatMoney(sale.sessionValue)}" />/buổi</c:when><c:otherwise>—</c:otherwise></c:choose></dd></div>
                </dl>
                <p class="sale-total"><span>Phải thu</span><strong><c:choose><c:when test="${sale.price gt 0}"><c:out value="${gms:formatMoney(sale.price)}" /></c:when><c:otherwise>—</c:otherwise></c:choose></strong></p>

                <%-- Bấm "Giữ lịch" bị từ chối → PtSaleServlet đặt saleError, vẽ lại trang với câu lỗi ở đây. --%>
                <c:if test="${not empty saleError}"><p class="sale-msg is-error" role="alert"><c:out value="${saleError}" /></p></c:if>


                <%@ include file="/WEB-INF/jsp/receptionist/_pt-sale-actions.jspf" %>

            </aside>
            <%-- ===================== KẾT THÚC: CỘT PHẢI ===================== --%>

            </div>
        </c:otherwise>
        </c:choose>
        </main>
    </div>
</div>
</body>
</html>
