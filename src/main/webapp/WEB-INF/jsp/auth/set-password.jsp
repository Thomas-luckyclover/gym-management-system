<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%--
  Đặt mật khẩu lần đầu qua link email (CBR-24, CBR-31). Dữ liệu: SetPasswordServlet
    token        link còn hiệu lực → hiện form
    linkError    link sai / hết hạn / đã dùng → hiện thông báo + nút Quên mật khẩu
    fieldErrors  lỗi dưới ô mật khẩu
--%>
<!DOCTYPE html>
<html lang="vi">
<head>
  <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
  <title>Đặt mật khẩu — Super Gym</title>
  <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/auth/login.css">
  <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/auth/forgot-password.css?v=1">
</head>
<body class="theme-guest-dark auth-dark">
  <%@ include file="/WEB-INF/jsp/common/flash-toast.jspf" %>
  <c:set var="ctx" value="${pageContext.request.contextPath}" />

  <div class="auth-shell">
    <div class="auth-top">
      <a class="auth-brand" href="${ctx}/">Super Gym</a>
      <a class="auth-back" href="${ctx}/login">
        <i class="fa-solid fa-arrow-left"></i> Về đăng nhập
      </a>
    </div>

    <div class="auth-card">
      <c:choose>
        <%-- ---------- Link không còn hiệu lực ---------- --%>
        <c:when test="${not empty linkError}">
          <div class="auth-head">
            <h1>Link không còn hiệu lực</h1>
          </div>
          <div class="msg-banner warning" role="alert">
            <i class="fa-solid fa-triangle-exclamation"></i>
            <span><c:out value="${linkError}" /></span>
          </div>
          <div class="btn-row">
            <a class="btn btn-primary" href="${ctx}/forgot-password">Quên mật khẩu</a>
            <a class="btn btn-outline" href="${ctx}/login">Đăng nhập</a>
          </div>
        </c:when>

        <%-- ---------- Form đặt mật khẩu ---------- --%>
        <c:otherwise>
          <div class="auth-head">
            <h1>Đặt mật khẩu</h1>
            <p>Tạo mật khẩu để bắt đầu đăng nhập Super Gym. Mật khẩu 8–64 ký tự, gồm cả chữ và số.</p>
          </div>

          <form action="${ctx}/set-password" method="post" data-validate novalidate>
            <input type="hidden" name="token" value="<c:out value='${token}' />">

            <label class="field">
              <span class="field-label">Mật khẩu</span>
              <span class="input-wrap">
                <input id="password" name="password" type="password" autocomplete="new-password"
                       maxlength="64" placeholder="Nhập mật khẩu"
                       required data-label="Mật khẩu" data-rule="password" autofocus>
                <button type="button" class="pw-toggle" data-password-toggle="password" aria-label="Hiện mật khẩu">
                  <i class="fa-solid fa-eye"></i>
                </button>
              </span>
              <span class="field-error" id="passwordError"
                    <c:if test="${empty fieldErrors.password}">hidden</c:if>><c:out value="${fieldErrors.password}" /></span>
            </label>

            <label class="field">
              <span class="field-label">Nhập lại mật khẩu</span>
              <span class="input-wrap">
                <input id="confirmPassword" name="confirmPassword" type="password" autocomplete="new-password"
                       maxlength="64" placeholder="Nhập lại mật khẩu"
                       required data-label="Nhập lại mật khẩu" data-match="password">
                <button type="button" class="pw-toggle" data-password-toggle="confirmPassword" aria-label="Hiện mật khẩu">
                  <i class="fa-solid fa-eye"></i>
                </button>
              </span>
              <span class="field-error" id="confirmPasswordError"
                    <c:if test="${empty fieldErrors.confirmPassword}">hidden</c:if>><c:out value="${fieldErrors.confirmPassword}" /></span>
            </label>

            <button class="btn btn-primary btn-block" type="submit">Đặt mật khẩu</button>
          </form>
        </c:otherwise>
      </c:choose>
    </div>

    <p class="auth-foot-note">© 2026 Super Gym</p>
  </div>
</body>
</html>
