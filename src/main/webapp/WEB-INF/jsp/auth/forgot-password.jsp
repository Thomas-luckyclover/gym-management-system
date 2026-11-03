<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%--
  MÀN QUÊN MẬT KHẨU — mở bởi ForgotPasswordServlet.forwardStep. 1 trang, 3 bước, vẽ form theo "recoveryStep".
  Dữ liệu (request.setAttribute trong ForgotPasswordServlet):
    recoveryStep  ← resolveStep (theo session)                      → chọn form: email | code | reset
    recoveryEmail ← session RECOVERY_EMAIL                          → "Đã gửi tới …"
    errorMessage  ← ServiceResult lỗi chung (MSG37, MSG44, MSG61…)   → banner đỏ trên form
    fieldErrors   ← ServiceResult.getFieldErrors() (Map theo tên ô)  → chữ đỏ dưới từng ô
    codeSecondsLeft / resendSecondsLeft ← setCodeTimers             → đồng hồ + nút Gửi lại (forgot-password.js đếm)
    attemptHint / resendHint (+ …Warn)  ← setCodeTimers             → gợi ý số lần còn lại (Warn = chữ vàng)
  Mọi form: POST /forgot-password → ForgotPasswordServlet.doPost, phân nhánh theo ô ẩn action; csrfToken do common.js tự gắn.
--%>

<!DOCTYPE html>
<html lang="vi">
<head>
  <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
  <title>Quên mật khẩu — Super Gym</title>
  <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/auth/login.css?v=20261014b">
  <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/auth/forgot-password.css?v=20261025a">
  <script src="${pageContext.request.contextPath}/js/pages/auth/forgot-password.js?v=20261025a" defer></script>
</head>
<body class="theme-guest-dark auth-dark auth-page-forgot">
  <%@ include file="/WEB-INF/jsp/common/flash-toast.jspf" %>
  <c:set var="ctx" value="${pageContext.request.contextPath}" />
  <%-- step = recoveryStep (trống = "email"). --%>
  <c:set var="step" value="${empty recoveryStep ? 'email' : recoveryStep}" />

  <div class="auth-shell">
    <%@ include file="/WEB-INF/jsp/common/auth-artwork.jspf" %>
    <div class="auth-top">
      <a class="auth-brand" href="${ctx}/" aria-label="Super Gym — Trang chủ"><img class="auth-brand__logo" src="${ctx}/images/brand/super-gym-wordmark.svg?v=20261014d" alt="Super Gym" width="170" height="44">
        <%@ include file="/WEB-INF/jsp/common/auth-muse.jspf" %></a>
      <a class="auth-back" href="${ctx}/login"><i class="fa-solid fa-arrow-left" aria-hidden="true"></i> Về đăng nhập</a>
    </div>

    <div class="auth-card">

      <%-- THANH 3 BƯỚC: bước hiện tại tô đậm (is-current), bước đã qua có dấu xong (is-done). --%>
      <ol class="recovery-steps" aria-label="Các bước khôi phục mật khẩu">
        <li class="${step eq 'email' ? 'is-current' : 'is-done'}" ${step eq 'email' ? 'aria-current="step"' : ''}><span>1</span> Email</li>
        <li class="${step eq 'code' ? 'is-current' : (step eq 'reset' ? 'is-done' : '')}" ${step eq 'code' ? 'aria-current="step"' : ''}><span>2</span> Mã xác thực</li>
        <li class="${step eq 'reset' ? 'is-current' : ''}" ${step eq 'reset' ? 'aria-current="step"' : ''}><span>3</span> Mật khẩu mới</li>
      </ol>

      <%-- BANNER LỖI CHUNG ← errorMessage (VD tài khoản bị khóa, mã hết hạn, gửi quá nhiều lần). --%>
      <c:if test="${not empty errorMessage}">
        <div class="msg-banner error" role="alert">
          <i class="fa-solid fa-triangle-exclamation" aria-hidden="true"></i>
          <span><c:out value="${errorMessage}" /></span>
        </div>
      </c:if>

      <c:choose>

        <%--
          ===== BƯỚC 1: EMAIL =====
          FORM: action=send → handleSend → PasswordRecoveryService.requestCode
            email = ô nhập (giữ lại chữ đã gõ khi lỗi: attribute "email") · lỗi ô ← fieldErrors.email
        --%>
        <c:when test="${step eq 'email'}">
          <div class="auth-head"><h1>Quên mật khẩu</h1></div>

          <form action="${ctx}/forgot-password" method="post" data-validate novalidate>
            <input type="hidden" name="action" value="send">
            <label class="field">
              <span class="field-label">Email</span>
              <input id="email" name="email" type="email" placeholder="ban@email.com" autocomplete="email" maxlength="100"
                     value="<c:out value='${email}' />" aria-describedby="emailHint"
                     required data-label="Email" data-rule="email" autofocus>
              <span class="field-hint" id="emailHint"><i class="fa-solid fa-circle-info" aria-hidden="true"></i> Mã 6 số sẽ được gửi tới email này. Chỉ khôi phục qua email, không dùng số điện thoại.</span>
              <span class="field-error" id="emailError" <c:if test="${empty fieldErrors.email}">hidden</c:if>><c:out value="${fieldErrors.email}" /></span>
            </label>
            <button class="btn btn-primary btn-block" type="submit">Gửi mã xác thực</button>
          </form>
        </c:when>

        <%-- ===== BƯỚC 2: MÃ XÁC THỰC ===== --%>
        <c:when test="${step eq 'code'}">
          <div class="auth-head"><h1>Nhập mã xác thực</h1></div>

          <%-- ĐỒNG HỒ HẾT HẠN MÃ: data-code-seconds = codeSecondsLeft → forgot-password.js đếm ngược "mm:ss"; 0 → "Mã đã hết hạn". --%>
          <p class="recovery-timer" id="codeTimer" data-code-seconds="${codeSecondsLeft}">
            <i class="fa-regular fa-clock" aria-hidden="true"></i>
            <span id="codeTimerText">
              <c:choose>
                <c:when test="${codeSecondsLeft > 0}">Mã hết hạn sau <b id="codeCountdown">--:--</b></c:when>
                <c:otherwise>Mã đã hết hạn. Bấm Gửi lại mã để nhận mã mới.</c:otherwise>
              </c:choose>
            </span>
          </p>

          <form action="${ctx}/forgot-password" method="post" data-validate novalidate>

            <%--
              FORM: action=verify → handleVerify → PasswordRecoveryService.verifyCode
                code = 6 số · lỗi ô ← fieldErrors.code · attemptHint = "Còn x lần nhập" (vàng khi còn 1 lần)
            --%>
            <input type="hidden" name="action" value="verify">
            <label class="field">
              <span class="field-label">Mã xác thực</span>
              <input id="code" name="code" type="text" class="otp-input" inputmode="numeric" autocomplete="one-time-code"
                     maxlength="6" placeholder="••••••" aria-describedby="codeHint"
                     required data-label="Mã xác thực" data-rule="verificationCode" autofocus>
              <span class="field-hint" id="codeHint">Đã gửi tới <b class="recovery-email"><c:out value="${recoveryEmail}" /></b></span>
              <span class="field-error" id="codeError" <c:if test="${empty fieldErrors.code}">hidden</c:if>><c:out value="${fieldErrors.code}" /></span>

              <c:if test="${not empty attemptHint}">
                <span class="field-hint ${attemptHintWarn ? 'field-hint--warn' : ''}"><c:out value="${attemptHint}" /></span>
              </c:if>
            </label>
            <button class="btn btn-primary btn-block" type="submit">Xác nhận</button>
          </form>

          <%--
            NÚT PHỤ:
              "Gửi lại mã" = FORM action=resend → handleResend; data-resend-seconds = resendSecondsLeft → JS khóa nút tới khi hết đếm
              resendHint = "Còn x lần gửi lại" (vàng khi hết lượt)
              "Dùng email khác" = GET ?restart=1 → doGet xóa session khôi phục, về bước 1
          --%>
          <div class="recovery-actions">
            <form action="${ctx}/forgot-password" method="post">
              <input type="hidden" name="action" value="resend">

              <button class="link-btn" type="submit" id="resendBtn" data-resend-seconds="${resendSecondsLeft}">
                <i class="fa-solid fa-rotate-right" aria-hidden="true"></i> Gửi lại mã<span id="resendWait"></span>
              </button>

              <c:if test="${not empty resendHint}">
                <span class="field-hint ${resendHintWarn ? 'field-hint--warn' : ''}"><c:out value="${resendHint}" /></span>
              </c:if>
            </form>
            <a class="link-btn" href="${ctx}/forgot-password?restart=1"><i class="fa-solid fa-pen" aria-hidden="true"></i> Dùng email khác</a>
          </div>

          <%-- Pumpy nhắc kiểm tra hộp thư rác. --%>
          <%@ include file="/WEB-INF/jsp/common/pumpy-mail-hint.jspf" %>
        </c:when>

        <%--
          ===== BƯỚC 3: MẬT KHẨU MỚI =====
          FORM: action=reset → handleReset → PasswordRecoveryService.resetPassword
            password, confirmPassword (nút mắt = hiện/ẩn) · lỗi ô ← fieldErrors.password / fieldErrors.confirmPassword
            Thành công → toast MSG95, về /login, mọi thiết bị khác bị đăng xuất.
        --%>
        <c:otherwise>
          <div class="auth-head"><h1>Đặt mật khẩu mới</h1></div>

          <form action="${ctx}/forgot-password" method="post" data-validate novalidate>
            <input type="hidden" name="action" value="reset">
            <label class="field">
              <span class="field-label">Mật khẩu mới</span>
              <span class="input-wrap">
                <input id="password" name="password" type="password" autocomplete="new-password" maxlength="64"
                       placeholder="Nhập mật khẩu mới" aria-describedby="passwordHint"
                       required data-label="Mật khẩu mới" data-rule="password" autofocus>
                <button type="button" class="pw-toggle" data-password-toggle="password" aria-label="Hiện mật khẩu"><i class="fa-solid fa-eye" aria-hidden="true"></i></button>
              </span>
              <span class="field-hint" id="passwordHint">8–64 ký tự, gồm cả chữ và số.</span>
              <span class="field-error" id="passwordError" <c:if test="${empty fieldErrors.password}">hidden</c:if>><c:out value="${fieldErrors.password}" /></span>
            </label>

            <label class="field">
              <span class="field-label">Nhập lại mật khẩu</span>
              <span class="input-wrap">
                <input id="confirmPassword" name="confirmPassword" type="password" autocomplete="new-password" maxlength="64"
                       placeholder="Nhập lại mật khẩu mới" required data-label="Nhập lại mật khẩu" data-match="password">
                <button type="button" class="pw-toggle" data-password-toggle="confirmPassword" aria-label="Hiện mật khẩu"><i class="fa-solid fa-eye" aria-hidden="true"></i></button>
              </span>
              <span class="field-error" id="confirmPasswordError" <c:if test="${empty fieldErrors.confirmPassword}">hidden</c:if>><c:out value="${fieldErrors.confirmPassword}" /></span>
            </label>

            <button class="btn btn-primary btn-block" type="submit">Đặt mật khẩu mới</button>

            <p class="recovery-note"><i class="fa-solid fa-circle-info" aria-hidden="true"></i> Đổi xong, mọi thiết bị đang đăng nhập tài khoản này sẽ bị đăng xuất.</p>
          </form>
        </c:otherwise>

      </c:choose>

      <p class="auth-switch">Nhớ ra mật khẩu? <a href="${ctx}/login">Đăng nhập</a></p>
    </div>

    <p class="auth-foot-note">© 2026 Super Gym</p>
  </div>

</body>
</html>
