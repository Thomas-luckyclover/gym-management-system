<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%--
================================================================================
 forgot-password.jsp — QUÊN MẬT KHẨU (/forgot-password, màn #3). 3 bước trong 1 trang.

 DỮ LIỆU SERVLET GỬI SANG (ForgotPasswordServlet.forwardStep)
   recoveryStep        "email" | "code" | "reset"   — Servlet suy ra từ SESSION, không từ URL
   recoveryEmail       email đang khôi phục (bước 2)
   codeSecondsLeft     số giây tới khi mã hết hạn      (bước 2, JS đếm lùi)
   resendSecondsLeft   số giây phải chờ để "Gửi lại mã" (bước 2, JS đếm lùi)
   attemptHint / resendHint (+ …Warn)  gợi ý số lượt còn lại MSG62–MSG65 (bước 2)
   errorMessage        lỗi chung → khung đỏ trên cùng
   fieldErrors         lỗi gắn ô → chữ đỏ dưới đúng ô
   email               chữ khách đã gõ (giữ lại khi bước 1 báo lỗi)

 Mỗi form có ô ẩn "action" (send / verify / resend / reset) → doPost biết khách bấm nút nào.
 Kiểm tra ô ở trình duyệt + nút con mắt + khóa nút khi gửi: common.js (form[data-validate]).
 Đồng hồ + ô mã chỉ nhận số: forgot-password.js.
================================================================================ --%>
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
  <c:set var="step" value="${empty recoveryStep ? 'email' : recoveryStep}" />

  <div class="auth-shell">
    <%@ include file="/WEB-INF/jsp/common/auth-artwork.jspf" %>
    <div class="auth-top">
      <a class="auth-brand" href="${ctx}/" aria-label="Super Gym — Trang chủ"><img class="auth-brand__logo" src="${ctx}/images/brand/super-gym-wordmark.svg?v=20261014d" alt="Super Gym" width="170" height="44">
        <%@ include file="/WEB-INF/jsp/common/auth-muse.jspf" %></a>
      <a class="auth-back" href="${ctx}/login"><i class="fa-solid fa-arrow-left" aria-hidden="true"></i> Về đăng nhập</a>
    </div>

    <div class="auth-card">
      <%-- Tiến trình 3 bước: bước đang làm = is-current, bước đã qua = is-done. --%>
      <ol class="recovery-steps" aria-label="Các bước khôi phục mật khẩu">
        <li class="${step eq 'email' ? 'is-current' : 'is-done'}" ${step eq 'email' ? 'aria-current="step"' : ''}><span>1</span> Email</li>
        <li class="${step eq 'code' ? 'is-current' : (step eq 'reset' ? 'is-done' : '')}" ${step eq 'code' ? 'aria-current="step"' : ''}><span>2</span> Mã xác thực</li>
        <li class="${step eq 'reset' ? 'is-current' : ''}" ${step eq 'reset' ? 'aria-current="step"' : ''}><span>3</span> Mật khẩu mới</li>
      </ol>

      <c:if test="${not empty errorMessage}">
        <div class="msg-banner error" role="alert">
          <i class="fa-solid fa-triangle-exclamation" aria-hidden="true"></i>
          <span><c:out value="${errorMessage}" /></span>
        </div>
      </c:if>

      <c:choose>
        <%-- ================= BƯỚC 1: NHẬP EMAIL ================= --%>
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

        <%-- ================= BƯỚC 2: NHẬP MÃ ================= --%>
        <c:when test="${step eq 'code'}">
          <div class="auth-head"><h1>Nhập mã xác thực</h1></div>

          <%-- Đồng hồ: số giây do server tính (codeSecondsLeft); JS đếm lùi, về 0 thì đổi thành câu MSG44. --%>
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
            <input type="hidden" name="action" value="verify">
            <label class="field">
              <span class="field-label">Mã xác thực</span>
              <input id="code" name="code" type="text" class="otp-input" inputmode="numeric" autocomplete="one-time-code"
                     maxlength="6" placeholder="••••••" aria-describedby="codeHint"
                     required data-label="Mã xác thực" data-rule="verificationCode" autofocus>
              <span class="field-hint" id="codeHint">Đã gửi tới <b class="recovery-email"><c:out value="${recoveryEmail}" /></b></span>
              <span class="field-error" id="codeError" <c:if test="${empty fieldErrors.code}">hidden</c:if>><c:out value="${fieldErrors.code}" /></span>
              <%-- MSG62 / MSG63: số lần còn nhập mã (ẩn khi đang bị khóa). --%>
              <c:if test="${not empty attemptHint}">
                <span class="field-hint ${attemptHintWarn ? 'field-hint--warn' : ''}"><c:out value="${attemptHint}" /></span>
              </c:if>
            </label>
            <button class="btn btn-primary btn-block" type="submit">Xác nhận</button>
          </form>

          <div class="recovery-actions">
            <form action="${ctx}/forgot-password" method="post">
              <input type="hidden" name="action" value="resend">
              <%-- JS khóa nút tới khi hết resendSecondsLeft, hiện "(0:59)" cạnh chữ. --%>
              <button class="link-btn" type="submit" id="resendBtn" data-resend-seconds="${resendSecondsLeft}">
                <i class="fa-solid fa-rotate-right" aria-hidden="true"></i> Gửi lại mã<span id="resendWait"></span>
              </button>
              <%-- MSG64 / MSG65: số lần còn gửi lại trong 60 phút. --%>
              <c:if test="${not empty resendHint}">
                <span class="field-hint ${resendHintWarn ? 'field-hint--warn' : ''}"><c:out value="${resendHint}" /></span>
              </c:if>
            </form>
            <a class="link-btn" href="${ctx}/forgot-password?restart=1"><i class="fa-solid fa-pen" aria-hidden="true"></i> Dùng email khác</a>
          </div>

          <%-- Gợi ý khi chưa nhận được mail (Pumpy). --%>
          <%@ include file="/WEB-INF/jsp/common/pumpy-mail-hint.jspf" %>
        </c:when>

        <%-- ================= BƯỚC 3: ĐẶT MẬT KHẨU MỚI ================= --%>
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
            <%-- Hệ quả quan trọng → nói ngay dưới nút (CBR-93). --%>
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
