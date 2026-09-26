<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%--
  Quên mật khẩu (#3, UC-03). Dữ liệu: ForgotPasswordServlet
    recoveryStep       email | code | reset
    recoveryEmail      email đang khôi phục (bước code)
    codeSecondsLeft    số giây còn lại của mã (bước code)
    resendSecondsLeft  số giây phải chờ trước khi được Gửi lại mã (bước code)
    errorMessage, fieldErrors
  Layout nền tối dùng chung với trang đăng nhập (login.css).
--%>
<!DOCTYPE html>
<html lang="vi">
<head>
  <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
  <title>Quên mật khẩu — Super Gym</title>
  <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/auth/login.css">
  <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/auth/forgot-password.css?v=1">
  <script src="${pageContext.request.contextPath}/js/pages/auth/forgot-password.js?v=1" defer></script>
</head>
<body class="theme-guest-dark auth-dark">
  <%@ include file="/WEB-INF/jsp/common/flash-toast.jspf" %>
  <c:set var="ctx" value="${pageContext.request.contextPath}" />
  <c:set var="step" value="${empty recoveryStep ? 'email' : recoveryStep}" />

  <div class="auth-shell">
    <div class="auth-top">
      <a class="auth-brand" href="${ctx}/">Super Gym</a>
      <a class="auth-back" href="${ctx}/login">
        <i class="fa-solid fa-arrow-left"></i> Về đăng nhập
      </a>
    </div>

    <div class="auth-card">
      <%-- Tiến trình 3 bước --%>
      <ol class="recovery-steps" aria-label="Các bước khôi phục mật khẩu">
        <li class="${step eq 'email' ? 'is-current' : 'is-done'}"><span>1</span> Email</li>
        <li class="${step eq 'code' ? 'is-current' : (step eq 'reset' ? 'is-done' : '')}"><span>2</span> Mã xác thực</li>
        <li class="${step eq 'reset' ? 'is-current' : ''}"><span>3</span> Mật khẩu mới</li>
      </ol>

      <c:if test="${not empty errorMessage}">
        <div class="msg-banner error" role="alert">
          <i class="fa-solid fa-triangle-exclamation"></i>
          <span><c:out value="${errorMessage}" /></span>
        </div>
      </c:if>

      <c:choose>
        <%-- ================= Bước 1: nhập email ================= --%>
        <c:when test="${step eq 'email'}">
          <div class="auth-head">
            <h1>Quên mật khẩu</h1>
            <p>Nhập email đã đăng ký, chúng tôi sẽ gửi mã 6 số để bạn đặt lại mật khẩu.</p>
          </div>

          <form action="${ctx}/forgot-password" method="post" data-validate novalidate>
            <input type="hidden" name="action" value="send">
            <label class="field">
              <span class="field-label">Email</span>
              <input id="email" name="email" type="email"
                     placeholder="ban@email.com" autocomplete="email" maxlength="100"
                     value="<c:out value='${email}' />"
                     required data-label="Email" data-rule="email" autofocus>
              <span class="field-error" id="emailError"
                    <c:if test="${empty fieldErrors.email}">hidden</c:if>><c:out value="${fieldErrors.email}" /></span>
            </label>
            <p class="recovery-hint"><i class="fa-solid fa-circle-info"></i> Chỉ khôi phục qua email, không dùng số điện thoại.</p>
            <button class="btn btn-primary btn-block" type="submit">Gửi mã xác thực</button>
          </form>
        </c:when>

        <%-- ================= Bước 2: nhập mã ================= --%>
        <c:when test="${step eq 'code'}">
          <div class="auth-head">
            <h1>Nhập mã xác thực</h1>
            <p>Mã 6 số đã được gửi tới <b class="recovery-email"><c:out value="${recoveryEmail}" /></b>.</p>
          </div>

          <p class="recovery-timer" id="codeTimer" data-code-seconds="${codeSecondsLeft}">
            <i class="fa-regular fa-clock"></i>
            <span id="codeTimerText">
              <c:choose>
                <c:when test="${codeSecondsLeft > 0}">Mã hết hạn sau <b id="codeCountdown">--:--</b></c:when>
                <c:otherwise>Mã đã hết hạn. Bấm Gửi lại mã để nhận mã mới.</c:otherwise>
              </c:choose>
            </span>
          </p>

          <form action="${ctx}/forgot-password" method="post" data-validate novalidate>
            <input type="hidden" name="action" value="verify">
            <label class="field">
              <span class="field-label">Mã xác thực</span>
              <input id="code" name="code" type="text" class="otp-input"
                     inputmode="numeric" autocomplete="one-time-code" maxlength="6"
                     placeholder="••••••"
                     required data-label="Mã xác thực" data-rule="verificationCode" autofocus>
              <span class="field-error" id="codeError"
                    <c:if test="${empty fieldErrors.code}">hidden</c:if>><c:out value="${fieldErrors.code}" /></span>
            </label>
            <button class="btn btn-primary btn-block" type="submit">Xác nhận</button>
          </form>

          <div class="recovery-actions">
            <form action="${ctx}/forgot-password" method="post">
              <input type="hidden" name="action" value="resend">
              <button class="link-btn" type="submit" id="resendBtn" data-resend-seconds="${resendSecondsLeft}">
                <i class="fa-solid fa-rotate-right"></i> Gửi lại mã<span id="resendWait"></span>
              </button>
            </form>
            <a class="link-btn" href="${ctx}/forgot-password?restart=1">
              <i class="fa-solid fa-pen"></i> Dùng email khác
            </a>
          </div>
        </c:when>

        <%-- ================= Bước 3: đặt mật khẩu mới ================= --%>
        <c:otherwise>
          <div class="auth-head">
            <h1>Đặt mật khẩu mới</h1>
            <p>Mật khẩu 8–64 ký tự, gồm cả chữ và số. Sau khi đổi, mọi thiết bị đang đăng nhập sẽ bị đăng xuất.</p>
          </div>

          <form action="${ctx}/forgot-password" method="post" data-validate novalidate>
            <input type="hidden" name="action" value="reset">
            <label class="field">
              <span class="field-label">Mật khẩu mới</span>
              <span class="input-wrap">
                <input id="password" name="password" type="password" autocomplete="new-password"
                       maxlength="64" placeholder="Nhập mật khẩu mới"
                       required data-label="Mật khẩu mới" data-rule="password" autofocus>
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
                       maxlength="64" placeholder="Nhập lại mật khẩu mới"
                       required data-label="Nhập lại mật khẩu" data-match="password">
                <button type="button" class="pw-toggle" data-password-toggle="confirmPassword" aria-label="Hiện mật khẩu">
                  <i class="fa-solid fa-eye"></i>
                </button>
              </span>
              <span class="field-error" id="confirmPasswordError"
                    <c:if test="${empty fieldErrors.confirmPassword}">hidden</c:if>><c:out value="${fieldErrors.confirmPassword}" /></span>
            </label>

            <button class="btn btn-primary btn-block" type="submit">Đặt mật khẩu mới</button>
          </form>
        </c:otherwise>
      </c:choose>

      <p class="auth-switch">
        Nhớ ra mật khẩu? <a href="${ctx}/login">Đăng nhập</a>
      </p>
    </div>

    <p class="auth-foot-note">© 2026 Super Gym</p>
  </div>
</body>
</html>
