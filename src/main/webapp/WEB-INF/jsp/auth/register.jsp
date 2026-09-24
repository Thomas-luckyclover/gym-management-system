<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!DOCTYPE html>
<html lang="vi">
<head>
  <%-- Include phần head chung (fonts, icons, tokens.css, common.css, common.js) --%>
  <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
  <title>Đăng ký tài khoản — Super Gym</title>
  <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/auth/register.css">
  <script src="${pageContext.request.contextPath}/js/pages/auth/register.js" defer></script>
</head>
<body class="theme-guest-dark auth-dark">
  <%-- Include toast thông báo nổi --%>
  <%@ include file="/WEB-INF/jsp/common/flash-toast.jspf" %>

  <div class="auth-shell">
    <%-- Thanh điều hướng phía trên của auth --%>
    <div class="auth-top">
      <a class="auth-brand" href="${pageContext.request.contextPath}/">Super Gym</a>
      <a class="auth-back" href="${pageContext.request.contextPath}/"><i class="fa-solid fa-arrow-left"></i> Về trang chủ</a>
    </div>

    <div class="auth-card">
      <div class="auth-head">
        <h1>Tạo tài khoản</h1>
        <p>Điền thông tin để tạo tài khoản hội viên Super Gym.</p>
      </div>

      <%-- Hiển thị thông báo lỗi từ server nếu có --%>
      <c:if test="${not empty errorMessage}">
        <div class="msg-banner error" role="alert">
          <i class="fa-solid fa-triangle-exclamation"></i>
          <span><c:out value="${errorMessage}" /></span>
        </div>
      </c:if>

      <form id="registerForm" action="${pageContext.request.contextPath}/register" method="post" novalidate>
        <%-- Ô nhập Họ và tên --%>
        <label class="field">
          <span class="field-label">Họ và tên <span class="required-mark">*</span></span>
          <input type="text" id="fullName" name="fullName" placeholder="Nguyễn Văn A" maxlength="50" required
                 value="<c:out value='${fullName}' />">
          <span class="field-error" id="fullNameError"></span>
        </label>

        <%-- Ô nhập Ngày sinh --%>
        <label class="field">
          <span class="field-label">Ngày sinh <span class="required-mark">*</span></span>
          <input type="date" id="dateOfBirth" name="dateOfBirth" required value="<c:out value='${dateOfBirth}' />">
          <span class="field-hint">
            <i class="fa-solid fa-circle-info"></i>
            <span>Lưu ý: Thành viên dưới 10 tuổi không thể mua gói PT. Nếu đăng ký cho trẻ em, vui lòng liên hệ quầy.</span>
          </span>
          <span class="field-error" id="dateOfBirthError"></span>
        </label>

        <%-- Ô nhập Số điện thoại --%>
        <label class="field">
          <span class="field-label">Số điện thoại <span class="required-mark">*</span></span>
          <input type="tel" id="phone" name="phone" placeholder="090 xxx xxxx" maxlength="10" inputmode="numeric" required
                 value="<c:out value='${phone}' />">
          <span class="field-error" id="phoneError"></span>
        </label>

        <%-- Ô nhập Email --%>
        <label class="field">
          <span class="field-label">Email <span class="required-mark">*</span></span>
          <input type="email" id="email" name="email" placeholder="ban@email.com" maxlength="100" required
                 value="<c:out value='${email}' />">
          <span class="field-error" id="emailError"></span>
        </label>

        <%-- Ô nhập Mật khẩu --%>
        <label class="field">
          <span class="field-label">Mật khẩu <span class="required-mark">*</span></span>
          <span class="input-wrap">
            <input type="password" id="password" name="password" placeholder="8–64 ký tự, có chữ và số" required>
            <button type="button" class="pw-toggle" data-toggle-target="password" aria-label="Hiện/ẩn mật khẩu">
              <i class="fa-solid fa-eye"></i>
            </button>
          </span>
          <span class="field-error" id="passwordError"></span>
        </label>

        <%-- Ô nhập lại Mật khẩu --%>
        <label class="field">
          <span class="field-label">Xác nhận mật khẩu <span class="required-mark">*</span></span>
          <span class="input-wrap">
            <input type="password" id="confirmPassword" name="confirmPassword" placeholder="Nhập lại mật khẩu" required>
            <button type="button" class="pw-toggle" data-toggle-target="confirmPassword" aria-label="Hiện/ẩn mật khẩu">
              <i class="fa-solid fa-eye"></i>
            </button>
          </span>
          <span class="field-error" id="confirmPasswordError"></span>
        </label>

        <button type="submit" id="btnSubmitRegister" class="btn btn-primary btn-block">
          Tạo tài khoản
        </button>
      </form>

      <p class="auth-switch">
        Đã có tài khoản? <a href="${pageContext.request.contextPath}/login">Đăng nhập</a>
      </p>
    </div>
  </div>
</body>
</html>