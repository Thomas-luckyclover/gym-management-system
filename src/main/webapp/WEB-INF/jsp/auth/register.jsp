<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%--
  register.jsp — Màn hình Đăng ký tài khoản & Kích hoạt hồ sơ quầy (/register, màn #2, UC-05, UC-Claim).
  Server-side render theo ô ẩn "step" do RegisterServlet điều phối:
  - Đăng ký mới: phone -> form -> otp -> success
  - Kích hoạt hồ sơ (Claim): claim-email -> claim-otp -> claim-password -> success
  - Bị chặn: blocked-account | blocked-no-email | blocked-email-mismatch
--%>
<!DOCTYPE html>
<html lang="vi">
<head>
  <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
  <title>Tạo tài khoản — Super Gym</title>
  <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/auth/register.css?v=20261014b">
  <script src="${pageContext.request.contextPath}/js/pages/auth/register.js?v=20261109merge" defer></script>
</head>

<body class="theme-guest-dark auth-dark auth-page-register">
  <%@ include file="/WEB-INF/jsp/common/flash-toast.jspf" %>

  <div class="auth-shell">
    <%@ include file="/WEB-INF/jsp/common/auth-artwork.jspf" %>
    <div class="auth-top">
      <a class="auth-brand" href="${pageContext.request.contextPath}/" aria-label="Super Gym — Trang chủ">
        <img class="auth-brand__logo" src="${pageContext.request.contextPath}/images/brand/super-gym-wordmark.svg?v=20261014d" alt="Super Gym" width="170" height="44">
        <%@ include file="/WEB-INF/jsp/common/auth-muse.jspf" %>
      </a>
      <a class="auth-back" href="${pageContext.request.contextPath}/">
        <i class="fa-solid fa-arrow-left"></i> Về trang chủ
      </a>
    </div>

    <div class="auth-card">

      <%-- ===== BƯỚC 1: NHẬP SỐ ĐIỆN THOẠI ===== --%>
      <c:if test="${step eq 'phone' or empty step}">
        <div class="auth-head"><h1>Tạo tài khoản</h1></div>

        <c:if test="${not empty fieldErrors.phone}">
          <div class="msg-banner error" role="alert">
            <i class="fa-solid fa-triangle-exclamation"></i>
            <span><c:out value="${fieldErrors.phone}"/></span>
          </div>
        </c:if>

        <form id="registerPhoneForm" action="${pageContext.request.contextPath}/register" method="post" data-validate novalidate>
          <input type="hidden" name="step" value="phone">
          <label class="field">
            <span class="field-label">Số điện thoại <span class="required-mark">*</span></span>
            <input type="tel" id="phone" name="phone" maxlength="10" inputmode="numeric" autocomplete="tel"
                   placeholder="0912345678" aria-describedby="phoneHint" required data-rule="phone"
                   data-label="Số điện thoại" value="<c:out value='${phone}'/>" autofocus>
            <span class="field-hint" id="phoneHint">Đã có hồ sơ tại quầy? Hệ thống tự tìm theo số này.</span>
            <span class="field-error" id="phoneError" <c:if test="${empty fieldErrors.phone}">hidden</c:if>><c:out value="${fieldErrors.phone}"/></span>
          </label>
          <button type="submit" id="btnContinueRegister" class="btn btn-primary btn-block">Tiếp tục</button>
        </form>

        <p class="auth-switch">Đã có tài khoản? <a href="${pageContext.request.contextPath}/login">Đăng nhập</a></p>
      </c:if>

      <%-- ===== BƯỚC 2: ĐIỀN THÔNG TIN ĐĂNG KÝ MỚI ===== --%>
      <c:if test="${step eq 'form'}">
        <span class="phone-chip register-phone-chip">
          <i class="fa-solid fa-phone"></i>
          <span><c:out value="${phone}"/></span>
          <a href="${pageContext.request.contextPath}/register">Đổi số</a>
        </span>
        <div class="auth-head"><h1>Tạo tài khoản</h1></div>

        <c:if test="${not empty errorMessage}">
          <div class="msg-banner error" role="alert">
            <i class="fa-solid fa-triangle-exclamation"></i>
            <span><c:out value="${errorMessage}"/></span>
          </div>
        </c:if>

        <form id="registerForm" action="${pageContext.request.contextPath}/register" method="post" data-validate data-unsaved-warning novalidate>
          <input type="hidden" name="step" value="form">
          <input type="hidden" name="phone" value="<c:out value='${phone}'/>">
          <input type="hidden" name="registerMode" value="new">

          <label class="field">
            <span class="field-label">Email <span class="required-mark">*</span></span>
            <input type="email" id="email" name="email" maxlength="100" autocomplete="email" placeholder="ban@email.com"
                   required data-rule="email" data-label="Email" value="<c:out value='${email}'/>" autofocus>
            <span class="field-error" id="emailError" <c:if test="${empty fieldErrors.email}">hidden</c:if>><c:out value="${fieldErrors.email}"/></span>
          </label>

          <label class="field">
            <span class="field-label">Mật khẩu <span class="required-mark">*</span></span>
            <span class="input-wrap">
              <input type="password" id="password" name="password" autocomplete="new-password" placeholder="8–64 ký tự, có chữ và số"
                     required data-rule="password" data-label="Mật khẩu">
              <button type="button" class="pw-toggle" data-password-toggle="password" aria-label="Hiện hoặc ẩn mật khẩu"><i class="fa-solid fa-eye"></i></button>
            </span>
            <span class="field-error" id="passwordError" <c:if test="${empty fieldErrors.password}">hidden</c:if>><c:out value="${fieldErrors.password}"/></span>
          </label>

          <label class="field">
            <span class="field-label">Xác nhận mật khẩu <span class="required-mark">*</span></span>
            <span class="input-wrap">
              <input type="password" id="confirmPassword" name="confirmPassword" autocomplete="new-password" placeholder="Nhập lại mật khẩu"
                     required data-match="password" data-label="Xác nhận mật khẩu">
              <button type="button" class="pw-toggle" data-password-toggle="confirmPassword" aria-label="Hiện hoặc ẩn mật khẩu"><i class="fa-solid fa-eye"></i></button>
            </span>
            <span class="field-error" id="confirmPasswordError" <c:if test="${empty fieldErrors.confirmPassword}">hidden</c:if>><c:out value="${fieldErrors.confirmPassword}"/></span>
          </label>

          <label class="field">
            <span class="field-label">Họ và tên <span class="required-mark">*</span></span>
            <input type="text" id="fullName" name="fullName" maxlength="50" autocomplete="name" placeholder="Nguyễn Văn A"
                   required data-rule="fullName" data-label="Họ và tên" value="<c:out value='${fullName}'/>">
            <span class="field-error" id="fullNameError" <c:if test="${empty fieldErrors.fullName}">hidden</c:if>><c:out value="${fieldErrors.fullName}"/></span>
          </label>

          <label class="field">
            <span class="field-label">Ngày sinh <span class="required-mark">*</span></span>
            <input type="date" id="dateOfBirth" name="dateOfBirth" required data-rule="dateOfBirth" data-label="Ngày sinh" value="<c:out value='${dateOfBirth}'/>">
            <span class="field-hint"><i class="fa-solid fa-circle-info"></i> Lưu ý: Thành viên dưới 10 tuổi không thể mua gói PT.</span>
            <span class="field-error" id="dateOfBirthError" <c:if test="${empty fieldErrors.dateOfBirth}">hidden</c:if>><c:out value="${fieldErrors.dateOfBirth}"/></span>
          </label>

          <button type="submit" id="btnSubmitRegister" class="btn btn-primary btn-block">Tạo tài khoản</button>
        </form>

        <p class="auth-switch">Đã có tài khoản? <a href="${pageContext.request.contextPath}/login">Đăng nhập</a></p>
      </c:if>

      <%-- ===== BƯỚC 3: NHẬP MÃ OTP (ĐĂNG KÝ MỚI) ===== --%>
      <c:if test="${step eq 'otp'}">
        <div class="auth-head"><h1>Nhập mã xác thực</h1></div>

        <div class="msg-banner info" role="status">
          <i class="fa-solid fa-circle-info"></i>
          <span>
            Nhập mã xác thực vừa gửi tới email <strong><c:out value="${maskedEmail}"/></strong>
            và SĐT <strong><c:out value="${phone}"/></strong>.
            Mã có hiệu lực trong <strong id="otpCountdown" data-expires="${otpExpiresAt}"></strong>.
          </span>
        </div>

        <c:if test="${not empty successMessage}">
          <div class="msg-banner success" role="status">
            <i class="fa-solid fa-circle-check"></i>
            <span><c:out value="${successMessage}"/></span>
          </div>
        </c:if>

        <c:if test="${not empty errorMessage}">
          <div class="msg-banner error" role="alert">
            <i class="fa-solid fa-triangle-exclamation"></i>
            <span><c:out value="${errorMessage}"/></span>
          </div>
        </c:if>

        <form id="otpForm" action="${pageContext.request.contextPath}/register" method="post" data-validate novalidate>
          <input type="hidden" name="step" value="otp">
          <input type="hidden" name="phone" value="<c:out value='${phone}'/>">
          <input type="hidden" name="email" value="<c:out value='${email}'/>">
          <input type="hidden" name="fullName" value="<c:out value='${fullName}'/>">
          <input type="hidden" name="dateOfBirth" value="<c:out value='${dateOfBirth}'/>">
          <input type="hidden" name="registerMode" value="<c:out value='${registerMode}'/>">
          <input type="hidden" name="password" value="<c:out value='${password}'/>">

          <label class="field">
            <span class="field-label">Mã xác thực (6 số) <span class="required-mark">*</span></span>
            <input type="text" id="otpCode" name="code" class="otp-input" inputmode="numeric" maxlength="6"
                   placeholder="––––––" autocomplete="one-time-code" required data-rule="verificationCode" data-label="Mã xác thực" autofocus>
            <span class="field-hint"><i class="fa-solid fa-circle-info"></i> Không nhận được mã? Kiểm tra thư rác hoặc bấm Gửi lại mã.</span>
            <span class="field-error" id="otpCodeError" <c:if test="${empty fieldErrors.code}">hidden</c:if>><c:out value="${fieldErrors.code}"/></span>
          </label>

          <button type="submit" id="btnVerifyOtp" class="btn btn-primary btn-block">Xác thực</button>
        </form>

        <div class="center-row">
          <form action="${pageContext.request.contextPath}/register" method="post" id="resendForm">
            <input type="hidden" name="step" value="resend">
            <input type="hidden" name="phone" value="<c:out value='${phone}'/>">
            <input type="hidden" name="email" value="<c:out value='${email}'/>">
            <input type="hidden" name="fullName" value="<c:out value='${fullName}'/>">
            <input type="hidden" name="dateOfBirth" value="<c:out value='${dateOfBirth}'/>">
            <input type="hidden" name="registerMode" value="<c:out value='${registerMode}'/>">
            <input type="hidden" name="password" value="<c:out value='${password}'/>">
            <button type="submit" class="btn-link" id="resendBtn" data-resend-unlock="${resendUnlockAt}">Gửi lại mã</button>
          </form>
        </div>

        <p class="auth-switch"><a href="${pageContext.request.contextPath}/register"><i class="fa-solid fa-arrow-left"></i> Dùng số khác</a></p>
        <%@ include file="/WEB-INF/jsp/common/pumpy-mail-hint.jspf" %>
      </c:if>

      <%-- ===== CLAIM BƯỚC 2c: NHẬP EMAIL ĐÃ ĐĂNG KÝ TẠI QUẦY ===== --%>
      <c:if test="${step eq 'claim-email'}">
        <span class="phone-chip register-phone-chip">
          <i class="fa-solid fa-phone"></i>
          <span><c:out value="${phone}"/></span>
          <a href="${pageContext.request.contextPath}/register">Đổi số</a>
        </span>
        <div class="auth-head"><h1>Kích hoạt hồ sơ</h1></div>

        <c:if test="${not empty errorMessage}">
          <div class="msg-banner error" role="alert">
            <i class="fa-solid fa-triangle-exclamation"></i>
            <span><c:out value="${errorMessage}"/></span>
          </div>
        </c:if>

        <form id="claimEmailForm" action="${pageContext.request.contextPath}/register" method="post" data-validate novalidate>
          <input type="hidden" name="step" value="claim-email">
          <input type="hidden" name="phone" value="<c:out value='${phone}'/>">

          <label class="field">
            <span class="field-label">Email đã đăng ký tại quầy <span class="required-mark">*</span></span>
            <input type="email" id="claimEmail" name="email" maxlength="100" autocomplete="email" placeholder="ban@email.com"
                   aria-describedby="claimEmailHint" required data-rule="email" data-label="Email" value="<c:out value='${email}'/>" autofocus>
            <span class="field-hint" id="claimEmailHint">Đã tìm thấy hồ sơ tại quầy khớp số <c:out value="${phone}"/>. Nhập đúng email để xác thực.</span>
            <span class="field-error" id="claimEmailError" <c:if test="${empty fieldErrors.email}">hidden</c:if>><c:out value="${fieldErrors.email}"/></span>
          </label>

          <button type="submit" id="btnClaimEmail" class="btn btn-primary btn-block">Tiếp tục</button>
        </form>

        <p class="auth-switch"><a href="${pageContext.request.contextPath}/register"><i class="fa-solid fa-arrow-left"></i> Dùng số khác</a></p>
      </c:if>

      <%-- ===== CLAIM BƯỚC 3c: NHẬP MÃ OTP ===== --%>
      <c:if test="${step eq 'claim-otp'}">
        <div class="auth-head"><h1>Nhập mã xác thực</h1></div>

        <div class="msg-banner info" role="status">
          <i class="fa-solid fa-circle-info"></i>
          <span>
            Mã xác thực đã gửi tới email <strong><c:out value="${maskedEmail}"/></strong>.
            Mã có hiệu lực trong <strong id="otpCountdown" data-expires="${otpExpiresAt}"></strong>.
          </span>
        </div>

        <c:if test="${not empty successMessage}">
          <div class="msg-banner success" role="status">
            <i class="fa-solid fa-circle-check"></i>
            <span><c:out value="${successMessage}"/></span>
          </div>
        </c:if>

        <c:if test="${not empty errorMessage}">
          <div class="msg-banner error" role="alert">
            <i class="fa-solid fa-triangle-exclamation"></i>
            <span><c:out value="${errorMessage}"/></span>
          </div>
        </c:if>

        <form id="claimOtpForm" action="${pageContext.request.contextPath}/register" method="post" data-validate novalidate>
          <input type="hidden" name="step" value="claim-otp">
          <input type="hidden" name="phone" value="<c:out value='${phone}'/>">
          <input type="hidden" name="email" value="<c:out value='${email}'/>">

          <label class="field">
            <span class="field-label">Mã xác thực (6 số) <span class="required-mark">*</span></span>
            <input type="text" id="claimOtpCode" name="code" class="otp-input" inputmode="numeric" maxlength="6"
                   placeholder="––––––" autocomplete="one-time-code" required data-rule="verificationCode" data-label="Mã xác thực" autofocus>
            <span class="field-hint"><i class="fa-solid fa-circle-info"></i> Không nhận được mã? Kiểm tra thư rác hoặc bấm Gửi lại mã.</span>
            <span class="field-error" id="claimOtpCodeError" <c:if test="${empty fieldErrors.code}">hidden</c:if>><c:out value="${fieldErrors.code}"/></span>
          </label>

          <button type="submit" id="btnClaimVerifyOtp" class="btn btn-primary btn-block">Xác thực</button>
        </form>

        <div class="center-row">
          <form action="${pageContext.request.contextPath}/register" method="post" id="claimResendForm">
            <input type="hidden" name="step" value="claim-resend">
            <input type="hidden" name="phone" value="<c:out value='${phone}'/>">
            <input type="hidden" name="email" value="<c:out value='${email}'/>">
            <button type="submit" class="btn-link" id="claimResendBtn" data-resend-unlock="${resendUnlockAt}">Gửi lại mã</button>
          </form>
        </div>

        <p class="auth-switch"><a href="${pageContext.request.contextPath}/register"><i class="fa-solid fa-arrow-left"></i> Dùng số khác</a></p>
        <%@ include file="/WEB-INF/jsp/common/pumpy-mail-hint.jspf" %>
      </c:if>

      <%-- ===== CLAIM BƯỚC 4c: ĐẶT MẬT KHẨU HOÀN TẤT KÍCH HOẠT ===== --%>
      <c:if test="${step eq 'claim-password'}">
        <div class="auth-head"><h1>Đặt mật khẩu</h1></div>

        <div class="msg-banner success" role="status">
          <i class="fa-solid fa-circle-check"></i>
          <span>Xác thực thành công — đặt mật khẩu để hoàn tất kích hoạt.</span>
        </div>

        <c:if test="${not empty errorMessage}">
          <div class="msg-banner error" role="alert">
            <i class="fa-solid fa-triangle-exclamation"></i>
            <span><c:out value="${errorMessage}"/></span>
          </div>
        </c:if>

        <form id="claimPasswordForm" action="${pageContext.request.contextPath}/register" method="post" data-validate novalidate>
          <input type="hidden" name="step" value="claim-password">
          <input type="hidden" name="phone" value="<c:out value='${phone}'/>">
          <input type="hidden" name="email" value="<c:out value='${email}'/>">

          <label class="field">
            <span class="field-label">Mật khẩu mới <span class="required-mark">*</span></span>
            <span class="input-wrap">
              <input type="password" id="claimPassword" name="password" autocomplete="new-password" placeholder="8–64 ký tự, có chữ và số"
                     required data-rule="password" data-label="Mật khẩu" autofocus>
              <button type="button" class="pw-toggle" data-password-toggle="claimPassword" aria-label="Hiện hoặc ẩn mật khẩu"><i class="fa-solid fa-eye"></i></button>
            </span>
            <span class="field-error" id="claimPasswordError" <c:if test="${empty fieldErrors.password}">hidden</c:if>><c:out value="${fieldErrors.password}"/></span>
          </label>

          <label class="field">
            <span class="field-label">Xác nhận mật khẩu <span class="required-mark">*</span></span>
            <span class="input-wrap">
              <input type="password" id="claimConfirmPassword" name="confirmPassword" autocomplete="new-password" placeholder="Nhập lại mật khẩu"
                     required data-match="claimPassword" data-label="Xác nhận mật khẩu">
              <button type="button" class="pw-toggle" data-password-toggle="claimConfirmPassword" aria-label="Hiện hoặc ẩn mật khẩu"><i class="fa-solid fa-eye"></i></button>
            </span>
            <span class="field-error" id="claimConfirmPasswordError" <c:if test="${empty fieldErrors.confirmPassword}">hidden</c:if>><c:out value="${fieldErrors.confirmPassword}"/></span>
          </label>

          <button type="submit" id="btnClaimSetPassword" class="btn btn-primary btn-block">Hoàn tất kích hoạt</button>
        </form>

        <p class="auth-switch"><a href="${pageContext.request.contextPath}/register"><i class="fa-solid fa-arrow-left"></i> Dùng số khác</a></p>
      </c:if>

      <%-- ===== CÁC MÀN HÌNH BỊ CHẶN ===== --%>
      <c:if test="${step eq 'blocked-account'}">
        <span class="phone-chip register-phone-chip">
          <i class="fa-solid fa-phone"></i>
          <span><c:out value="${phone}"/></span>
          <a href="${pageContext.request.contextPath}/register">Đổi số</a>
        </span>
        <div class="auth-head"><h1>Không tạo được tài khoản</h1></div>
        <div class="msg-banner warning" role="alert">
          <i class="fa-solid fa-triangle-exclamation"></i>
          <span><c:out value="${blockedMessage}"/></span>
        </div>
        <div class="btn-row">
          <a class="btn btn-primary" href="${pageContext.request.contextPath}/login">Đăng nhập</a>
          <a class="btn btn-outline" href="${pageContext.request.contextPath}/forgot-password">Quên mật khẩu</a>
        </div>
        <p class="auth-switch"><a href="${pageContext.request.contextPath}/register"><i class="fa-solid fa-arrow-left"></i> Dùng số khác</a></p>
      </c:if>

      <c:if test="${step eq 'blocked-email-mismatch'}">
        <div class="auth-head"><h1>Email không khớp hồ sơ</h1></div>
        <div class="msg-banner warning" role="alert">
          <i class="fa-solid fa-triangle-exclamation"></i>
          <span><c:out value="${blockedMessage}"/></span>
        </div>
        <div class="counter-box">
          <i class="fa-solid fa-location-dot"></i>
          <span><strong>Quầy Lễ tân:</strong> 106/36 Cầu Giấy, Nghĩa Đô, Cầu Giấy, Hà Nội — mở cửa 06:00 – 22:00 mỗi ngày.</span>
        </div>
        <div class="btn-row">
          <form action="${pageContext.request.contextPath}/register" method="get" style="display:contents">
            <input type="hidden" name="step" value="2">
            <input type="hidden" name="phone" value="<c:out value='${phone}'/>">
            <button type="submit" class="btn btn-primary" id="retryEmailBtn">Thử email khác</button>
          </form>
          <a class="btn btn-outline" href="${pageContext.request.contextPath}/register">Dùng số khác</a>
        </div>
      </c:if>

      <c:if test="${step eq 'blocked-no-email'}">
        <div class="auth-head"><h1>Cần bổ sung thông tin tại quầy</h1></div>
        <div class="msg-banner warning" role="alert">
          <i class="fa-solid fa-triangle-exclamation"></i>
          <span><c:out value="${blockedMessage}"/></span>
        </div>
        <div class="counter-box">
          <i class="fa-solid fa-location-dot"></i>
          <span><strong>Quầy Lễ tân:</strong> 106/36 Cầu Giấy, Nghĩa Đô, Cầu Giấy, Hà Nội — mở cửa 06:00 – 22:00 mỗi ngày.</span>
        </div>
        <a class="btn btn-outline btn-block" href="${pageContext.request.contextPath}/register"><i class="fa-solid fa-arrow-left"></i> Dùng số khác</a>
      </c:if>

      <%-- ===== MÀN HÌNH THÀNH CÔNG ===== --%>
      <c:if test="${step eq 'success'}">
        <div class="success-panel">
          <div class="success-icon"><i class="fa-solid fa-circle-check"></i></div>
          <h1>Tạo tài khoản thành công</h1>
          <p>
            <c:choose>
              <c:when test="${registerMode eq 'linked'}">
                Hồ sơ cũ của bạn tại phòng gym đã được gắn vào tài khoản mới — toàn bộ lịch sử gói tập được giữ nguyên.
              </c:when>
              <c:otherwise>
                Tài khoản đã sẵn sàng. Từ giờ bạn là hội viên và dùng được các chức năng dành cho khách hàng.
              </c:otherwise>
            </c:choose>
          </p>
          <div class="next-box">
            <h4>Bước tiếp theo</h4>
            <ul>
              <li>Chọn và thanh toán gói tập ngay.</li>
              <li>Ra quầy chụp ảnh check-in (bắt buộc với mọi gói, trừ gói ngày).</li>
              <li>Muốn tập với PT thì đăng ký gói PT tại quầy sau khi đã có gói tập.</li>
            </ul>
          </div>
          <a class="btn btn-primary btn-block" href="${pageContext.request.contextPath}/login">Đăng nhập ngay</a>
        </div>
      </c:if>

    </div>
    <p class="auth-foot-note">© 2026 Super Gym</p>
  </div>
</body>
</html>
