<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" isErrorPage="true" %>
<%--
  Trang lỗi 403 — khai báo trong WEB-INF/web.xml. Không in chi tiết lỗi kỹ thuật ra màn hình (bảo mật).
--%>
<!DOCTYPE html>
<html lang="vi">
<head>
  <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
  <title>403 · Super Gym</title>
</head>
<body class="theme-guest">
  <!-- Khung thông báo lỗi căn giữa màn hình — style ở common.css (.error-page) -->
  <main class="error-page">
    <p class="error-page__code">403</p>
    <h1 class="error-page__title">Không có quyền truy cập</h1>
    <p class="error-page__message">Bạn không có quyền truy cập chức năng này.</p>
    <a class="btn btn-primary" href="${pageContext.request.contextPath}/">Về trang chủ</a>
  </main>
</body>
</html>
