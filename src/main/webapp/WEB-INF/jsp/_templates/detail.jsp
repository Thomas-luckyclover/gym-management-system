<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="gms" uri="/WEB-INF/gms-functions.tld" %>
<%--
  TEMPLATE · MÀN CHI TIẾT (thông tin + hành động). Servlet đặt: item (DTO), itemError.
  Hành động nguy hiểm (hủy, xóa) dùng hộp xác nhận của common.js; nút bị khóa phải nói lý do.
--%>
<!DOCTYPE html>
<html lang="vi">
<head>
  <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
  <title>Chi tiết · Super Gym</title>
</head>
<body class="dock theme-manager">
<%@ include file="/WEB-INF/jsp/common/flash-toast.jspf" %>
<div class="app">
  <%@ include file="/WEB-INF/jsp/common/layout-manager.jspf" %>
  <div class="main">
    <header class="topbar">
      <div class="tb-left"><h1 class="tb-title">Chi tiết</h1></div>
      <div class="top-right"><a class="btn btn-secondary" href="javascript:history.back()">Quay lại</a></div>
    </header>

    <main class="content gms-page">
      <c:choose>
        <c:when test="${not empty itemError}">
          <p class="empty-state" role="alert"><c:out value="${itemError}" /></p>
        </c:when>
        <c:otherwise>
          <section class="gms-panel" data-reveal>
            <div class="gms-panel-head">
              <h2><c:out value="${item.name}" /></h2>
              <span class="badge badge-success"><c:out value="${item.statusLabel}" /></span>
            </div>
            <div class="gms-panel-body">
              <dl class="gms-detail">
                <div><dt>Mã</dt><dd><c:out value="${item.code}" /></dd></div>
                <div><dt>Ngày tạo</dt><dd>${gms:formatDate(item.createdAt)}</dd></div>
                <div><dt>Số tiền</dt><dd class="num">${gms:formatMoney(item.amount)}</dd></div>
                <div><dt>Ghi chú</dt><dd><c:out value="${item.note}" default="—" /></dd></div>
              </dl>
            </div>
          </section>
        </c:otherwise>
      </c:choose>
    </main>
  </div>
</div>
</body>
</html>
