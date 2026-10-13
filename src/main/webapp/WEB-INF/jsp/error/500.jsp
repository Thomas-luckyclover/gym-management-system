<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" isErrorPage="true" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%--
  Trang lỗi 500 — khai báo trong WEB-INF/web.xml. Không in chi tiết lỗi kỹ thuật ra màn hình (bảo mật).
--%>
<!DOCTYPE html>
<html lang="vi">
<head>
  <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
  <title>500 · Super Gym</title>
  <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/error/error.css?v=6">
</head>
<body class="theme-guest-dark err-body">
  <main class="err">
    <%@ include file="/WEB-INF/jsp/error/error-art.jspf" %>

    <section class="err-card">
      <a class="err-brand" href="${pageContext.request.contextPath}/">
        <img src="${pageContext.request.contextPath}/images/logo.png?v=20261013" alt="" width="44" height="34">
        Super Gym
      </a>

      <%-- Hai số 0 ghép thành một thanh tạ đang "gồng" --%>
      <p class="err-code" aria-hidden="true">
        <span>5</span>
        <svg class="err-barbell" viewBox="0 0 230 100">
          <rect class="bar" x="88" y="45" width="54" height="10" rx="5" />
          <circle class="rim" cx="48" cy="50" r="42" />
          <circle class="ring" cx="48" cy="50" r="28" />
          <circle class="hole" cx="48" cy="50" r="8" />
          <circle class="rim" cx="182" cy="50" r="42" />
          <circle class="ring" cx="182" cy="50" r="28" />
          <circle class="hole" cx="182" cy="50" r="8" />
        </svg>
      </p>

      <%-- Pumpy theo loại lỗi --%>
      <c:set var="pumpyMood" value="dizzy" />
      <%@ include file="/WEB-INF/jsp/error/pumpy.jspf" %>

      <p class="err-status">500 – Internal Server Error</p>
      <h1 class="err-title">Hệ thống đang bận</h1>
      <p class="err-message">Hệ thống đang bận. Vui lòng thử lại sau ít phút.</p>

      <div class="err-actions">
        <button class="btn btn-primary btn-lg" type="button" onclick="location.reload()">
          <i class="fa-solid fa-rotate-right"></i> Thử lại
        </button>
        <a class="btn btn-ghost btn-lg" href="${pageContext.request.contextPath}/">
          <i class="fa-solid fa-house"></i> Về trang chủ
        </a>
      </div>
    </section>
  </main>
</body>
</html>
