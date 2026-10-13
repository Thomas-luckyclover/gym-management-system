<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" isErrorPage="true" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%--
  Trang lỗi 404 — khai báo trong WEB-INF/web.xml. Không in chi tiết lỗi kỹ thuật ra màn hình (bảo mật).
--%>
<!DOCTYPE html>
<html lang="vi">
<head>
  <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
  <title>404 · Super Gym</title>
  <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/error/error.css?v=6">
</head>
<body class="theme-guest-dark err-body">
  <main class="err">
    <%@ include file="/WEB-INF/jsp/error/error-art.jspf" %>

    <section class="err-card">
      <a class="err-brand" href="${pageContext.request.contextPath}/">
        <img src="${pageContext.request.contextPath}/images/logo-mark.png?v=3" alt="Super Gym" width="40" height="40">
        Super Gym
      </a>

      <p class="err-code" aria-hidden="true">
        <span>4</span>
        <svg class="err-plate" viewBox="0 0 100 100">
          <circle class="rim" cx="50" cy="50" r="44" />
          <circle class="ring" cx="50" cy="50" r="30" />
          <circle class="hole" cx="50" cy="50" r="9" />
          <text class="label" x="50" y="22">20KG</text>
        </svg>
        <span>4</span>
      </p>

      <%-- Pumpy theo loại lỗi --%>
      <c:set var="pumpyMood" value="lost" />
      <%@ include file="/WEB-INF/jsp/error/pumpy.jspf" %>

      <p class="err-status">404 – Not Found</p>
      <h1 class="err-title">Không tìm thấy trang</h1>
      <p class="err-message">Trang bạn tìm không tồn tại hoặc đã bị gỡ. Có thể đường dẫn bị gõ sai.</p>

      <div class="err-actions">
        <a class="btn btn-primary btn-lg" href="${pageContext.request.contextPath}/">
          <i class="fa-solid fa-house"></i> Về trang chủ
        </a>
        <button class="btn btn-ghost btn-lg" type="button" onclick="history.back()">
          <i class="fa-solid fa-arrow-left"></i> Quay lại
        </button>
      </div>
    </section>
  </main>
</body>
</html>
