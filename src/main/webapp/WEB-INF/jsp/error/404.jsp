<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" isErrorPage="true" %>
<%--
  Trang lỗi 404 — khai báo trong WEB-INF/web.xml. Không in chi tiết lỗi kỹ thuật ra màn hình (bảo mật).
--%>
<!DOCTYPE html>
<html lang="vi">
<head>
  <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
  <title>404 · Super Gym</title>
</head>
<body class="theme-guest">
  <!-- Khung thông báo lỗi căn giữa màn hình — style ở common.css (.error-page) -->
  <main class="error-page">
    <p class="error-page__code">404</p>
    <h1 class="error-page__title">Không tìm thấy trang</h1>
    <p class="error-page__message">Trang bạn tìm không tồn tại hoặc đã bị gỡ.</p>
    <a class="btn btn-primary" href="${pageContext.request.contextPath}/">Về trang chủ</a>
  </main>
</body>
</html>
