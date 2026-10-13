<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" isErrorPage="true" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%--
  Trang lỗi 403 — khai báo trong WEB-INF/web.xml. Không in chi tiết lỗi kỹ thuật ra màn hình (bảo mật).
--%>
<!DOCTYPE html>
<html lang="vi">
<head>
  <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
  <title>403 · Super Gym</title>
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

      <%-- Số 0 là bánh tạ có ổ khóa ở giữa --%>
      <p class="err-code" aria-hidden="true">
        <span>4</span>
        <svg class="err-plate" viewBox="0 0 100 100">
          <circle class="rim" cx="50" cy="50" r="44" />
          <circle class="ring" cx="50" cy="50" r="30" />
          <path class="lock" d="M42 47 v-6 a8 8 0 0 1 16 0 v6" />
          <rect class="lock" x="37" y="47" width="26" height="19" rx="4" />
        </svg>
        <span>3</span>
      </p>

      <%-- Pumpy theo loại lỗi --%>
      <c:set var="pumpyMood" value="stop" />
      <%@ include file="/WEB-INF/jsp/error/pumpy.jspf" %>

      <p class="err-status">403 – Forbidden</p>
      <h1 class="err-title">Không có quyền truy cập</h1>
      <p class="err-message">Bạn không có quyền truy cập chức năng này. Hãy đăng nhập đúng tài khoản hoặc quay lại trang trước.</p>

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
