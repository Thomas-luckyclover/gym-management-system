<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>

<!DOCTYPE html>
<html lang="vi">
<head>
    <%@ include file="/WEB-INF/jsp/common/head.jspf" %>

    <title>Tạo tài khoản — Super Gym</title>

    <link rel="stylesheet"
          href="${pageContext.request.contextPath}/css/pages/auth/register.css">

    <script src="${pageContext.request.contextPath}/js/pages/auth/register.js"
            defer></script>
</head>

<%--
  Màn đăng ký dùng server-side rendering: Servlet quyết định step nào hiển thị,
  không có JS chuyển cảnh client-side. Mỗi POST submit form → Servlet xử lý →
  forward lại JSP với đúng step.

  Các step (attribute "step" từ Servlet):
    [Nhánh đăng ký mới]
    "phone"                  → Bước 1: nhập SĐT
    "form"                   → Bước 2: điền thông tin đầy đủ
    "otp"                    → Bước 3: nhập mã OTP
    [Nhánh Claim — kích hoạt hồ sơ quầy]
    "claim-email"            → Bước 2c: chỉ nhập email
    "claim-otp"              → Bước 3c: nhập mã OTP
    "claim-password"         → Bước 4c: nhập mật khẩu (sau OTP đúng)
    [Nhánh chặn]
    "blocked-account"        → SĐT đã có tài khoản (CBR-26)
    "blocked-email-mismatch" → SĐT có hồ sơ nhưng email nhập sai (CBR-25)
    "blocked-no-email"       → Hồ sơ cũ chưa có email (CBR-27)
    [Kết quả]
    "success"                → Đăng ký / kích hoạt hồ sơ thành công
--%>
<body class="theme-guest-dark auth-dark">
<%@ include file="/WEB-INF/jsp/common/flash-toast.jspf" %>

<div class="auth-shell">
    <div class="auth-top">
        <a class="auth-brand"
           href="${pageContext.request.contextPath}/">Super Gym</a>

        <a class="auth-back"
           href="${pageContext.request.contextPath}/">
            <i class="fa-solid fa-arrow-left"></i>
            Về trang chủ
        </a>
    </div>

    <div class="auth-card">

        <%-- ==================== BƯỚC 1: Nhập SĐT ==================== --%>
        <c:if test="${step eq 'phone' or empty step}">
            <div class="auth-head">
                <h1>Tạo tài khoản</h1>
                <p>Nhập số điện thoại để hệ thống kiểm tra hồ sơ sẵn có tại phòng gym.</p>
            </div>

            <%-- Lỗi server trả về khi SĐT không hợp lệ --%>
            <c:if test="${not empty fieldErrors.phone}">
                <div class="msg-banner error" role="alert">
                    <i class="fa-solid fa-triangle-exclamation"></i>
                    <span><c:out value="${fieldErrors.phone}" /></span>
                </div>
            </c:if>

            <form id="registerPhoneForm"
                  action="${pageContext.request.contextPath}/register"
                  method="post"
                  novalidate>

                <input type="hidden" name="step" value="phone">

                <label class="field">
                    <span class="field-label">
                        Số điện thoại
                        <span class="required-mark">*</span>
                    </span>

                    <input type="tel"
                           id="phone"
                           name="phone"
                           maxlength="10"
                           inputmode="numeric"
                           autocomplete="tel"
                           placeholder="0912345678"
                           required
                           value="<c:out value='${phone}' />">

                    <span class="field-error" id="phoneError"></span>
                </label>

                <button type="submit"
                        id="btnContinueRegister"
                        class="btn btn-primary btn-block">
                    Tiếp tục
                </button>
            </form>

            <p class="auth-switch">
                Đã có tài khoản?
                <a href="${pageContext.request.contextPath}/login">Đăng nhập</a>
            </p>
        </c:if>

        <%-- ==================== BƯỚC 2: Điền thông tin (ĐĂNG KÝ MỚI) ==================== --%>
        <c:if test="${step eq 'form'}">
            <%-- Chip hiện SĐT đã nhập kèm nút "Đổi số" --%>
            <span class="phone-chip register-phone-chip">
                <i class="fa-solid fa-phone"></i>
                <span><c:out value="${phone}" /></span>
                <a href="${pageContext.request.contextPath}/register">Đổi số</a>
            </span>

            <div class="auth-head">
                <h1>Tạo tài khoản</h1>
                <p>Điền thông tin để tạo tài khoản hội viên Super Gym.</p>
            </div>

            <%-- Lỗi server (validate thất bại hoặc email trùng...) --%>
            <c:if test="${not empty errorMessage}">
                <div class="msg-banner error" role="alert">
                    <i class="fa-solid fa-triangle-exclamation"></i>
                    <span><c:out value="${errorMessage}" /></span>
                </div>
            </c:if>

            <form id="registerForm"
                  action="${pageContext.request.contextPath}/register"
                  method="post"
                  data-unsaved-warning
                  novalidate>

                <input type="hidden" name="step" value="form">
                <input type="hidden"
                       name="phone"
                       value="<c:out value='${phone}' />">
                <input type="hidden" name="registerMode" value="new">

                <label class="field">
                    <span class="field-label">
                        Email
                        <span class="required-mark">*</span>
                    </span>

                    <input type="email"
                           id="email"
                           name="email"
                           maxlength="100"
                           autocomplete="email"
                           placeholder="ban@email.com"
                           data-rule="email"
                           required
                           value="<c:out value='${email}' />">

                    <span class="field-error"
                          id="emailError"
                          ${empty fieldErrors.email ? 'hidden' : ''}>
                        <c:out value="${fieldErrors.email}" />
                    </span>
                </label>

                <label class="field">
                    <span class="field-label">
                        Mật khẩu
                        <span class="required-mark">*</span>
                    </span>

                    <span class="input-wrap">
                        <input type="password"
                               id="password"
                               name="password"
                               autocomplete="new-password"
                               placeholder="8–64 ký tự, có chữ và số"
                               data-rule="password"
                               required>

                        <button type="button"
                                class="pw-toggle"
                                data-password-toggle="password"
                                aria-label="Hiện hoặc ẩn mật khẩu">
                            <i class="fa-solid fa-eye"></i>
                        </button>
                    </span>

                    <span class="field-error" id="passwordError"></span>
                </label>

                <label class="field">
                    <span class="field-label">
                        Xác nhận mật khẩu
                        <span class="required-mark">*</span>
                    </span>

                    <span class="input-wrap">
                        <input type="password"
                               id="confirmPassword"
                               name="confirmPassword"
                               autocomplete="new-password"
                               placeholder="Nhập lại mật khẩu"
                               data-match="password"
                               required>

                        <button type="button"
                                class="pw-toggle"
                                data-password-toggle="confirmPassword"
                                aria-label="Hiện hoặc ẩn mật khẩu">
                            <i class="fa-solid fa-eye"></i>
                        </button>
                    </span>

                    <span class="field-error" id="confirmPasswordError"></span>
                </label>

                <label class="field">
                    <span class="field-label">
                        Họ và tên
                        <span class="required-mark">*</span>
                    </span>

                    <input type="text"
                           id="fullName"
                           name="fullName"
                           maxlength="50"
                           autocomplete="name"
                           placeholder="Nguyễn Văn A"
                           data-rule="fullName"
                           required
                           value="<c:out value='${fullName}' />">

                    <span class="field-error"
                          id="fullNameError"
                          ${empty fieldErrors.fullName ? 'hidden' : ''}>
                        <c:out value="${fieldErrors.fullName}" />
                    </span>
                </label>

                <label class="field">
                    <span class="field-label">
                        Ngày sinh
                        <span class="required-mark">*</span>
                    </span>

                    <input type="date"
                           id="dateOfBirth"
                           name="dateOfBirth"
                           data-rule="dateOfBirth"
                           required
                           value="<c:out value='${dateOfBirth}' />">

                    <span class="field-hint">
                        <i class="fa-solid fa-circle-info"></i>
                        <span>
                            Lưu ý: Thành viên dưới 10 tuổi không thể mua gói PT.
                            Nếu đăng ký cho trẻ em, vui lòng liên hệ quầy.
                        </span>
                    </span>

                    <span class="field-error"
                          id="dateOfBirthError"
                          ${empty fieldErrors.dateOfBirth ? 'hidden' : ''}>
                        <c:out value="${fieldErrors.dateOfBirth}" />
                    </span>
                </label>

                <button type="submit"
                        id="btnSubmitRegister"
                        class="btn btn-primary btn-block">
                    Tạo tài khoản
                </button>
            </form>

            <p class="auth-switch">
                Đã có tài khoản?
                <a href="${pageContext.request.contextPath}/login">Đăng nhập</a>
            </p>
        </c:if>

        <%-- ==================== BƯỚC 3: Nhập mã OTP (ĐĂNG KÝ MỚI) ==================== --%>
        <c:if test="${step eq 'otp'}">
            <div class="auth-head">
                <h1>Nhập mã xác thực</h1>
            </div>

            <div class="msg-banner info" role="status">
                <i class="fa-solid fa-circle-info"></i>
                <span>
                    Nhập mã xác thực vừa gửi tới email
                    <strong><c:out value="${maskedEmail}" /></strong>
                    và SĐT <strong><c:out value="${phone}" /></strong>
                    để hoàn tất đăng ký.
                    Mã có hiệu lực trong
                    <strong id="otpCountdown" data-expires="${otpExpiresAt}"></strong>.
                </span>
            </div>

            <c:if test="${not empty successMessage}">
                <div class="msg-banner success" role="status">
                    <i class="fa-solid fa-circle-check"></i>
                    <span><c:out value="${successMessage}" /></span>
                </div>
            </c:if>

            <%-- Lỗi server khi mã sai, hết hạn hoặc bị khóa --%>
            <c:if test="${not empty errorMessage}">
                <div class="msg-banner error" role="alert">
                    <i class="fa-solid fa-triangle-exclamation"></i>
                    <span><c:out value="${errorMessage}" /></span>
                </div>
            </c:if>

            <form id="otpForm"
                  action="${pageContext.request.contextPath}/register"
                  method="post"
                  novalidate>

                <input type="hidden" name="step" value="otp">
                <%-- Giữ lại context để Servlet biết phải làm gì sau khi OTP đúng --%>
                <input type="hidden" name="phone" value="<c:out value='${phone}' />">
                <input type="hidden" name="email" value="<c:out value='${email}' />">
                <input type="hidden" name="fullName" value="<c:out value='${fullName}' />">
                <input type="hidden" name="dateOfBirth" value="<c:out value='${dateOfBirth}' />">
                <input type="hidden" name="registerMode" value="<c:out value='${registerMode}' />">
                <input type="hidden" name="password" value="<c:out value='${password}' />">

                <label class="field">
                    <span class="field-label">
                        Mã xác thực (6 số)
                        <span class="required-mark">*</span>
                    </span>

                    <input type="text"
                           id="otpCode"
                           name="code"
                           class="otp-input"
                           inputmode="numeric"
                           maxlength="6"
                           placeholder="––––––"
                           autocomplete="one-time-code"
                           required>

                    <span class="field-hint">
                        <i class="fa-solid fa-circle-info"></i>
                        <span>Không nhận được mã? Kiểm tra hộp thư rác hoặc bấm Gửi lại mã.</span>
                    </span>

                    <span class="field-error"
                          id="otpCodeError"
                          ${empty fieldErrors.code ? 'hidden' : ''}>
                        <c:out value="${fieldErrors.code}" />
                    </span>
                </label>

                <button type="submit"
                        id="btnVerifyOtp"
                        class="btn btn-primary btn-block">
                    Xác thực
                </button>
            </form>

            <%-- Nút gửi lại mã: POST riêng sang bước resend, Servlet kiểm tra 60s + 5 lần/giờ --%>
            <div class="center-row">
                <form action="${pageContext.request.contextPath}/register"
                      method="post"
                      id="resendForm">
                    <input type="hidden" name="step" value="resend">
                    <input type="hidden" name="phone" value="<c:out value='${phone}' />">
                    <input type="hidden" name="email" value="<c:out value='${email}' />">
                    <input type="hidden" name="fullName" value="<c:out value='${fullName}' />">
                    <input type="hidden" name="dateOfBirth" value="<c:out value='${dateOfBirth}' />">
                    <input type="hidden" name="registerMode" value="<c:out value='${registerMode}' />">
                    <input type="hidden" name="password" value="<c:out value='${password}' />">
                    <button type="submit"
                            class="btn-link"
                            id="resendBtn"
                            data-resend-unlock="${resendUnlockAt}">
                        Gửi lại mã
                    </button>
                </form>
            </div>

            <p class="auth-switch">
                <a href="${pageContext.request.contextPath}/register">
                    <i class="fa-solid fa-arrow-left"></i> Dùng số khác
                </a>
            </p>
        </c:if>

        <%-- ==================== CLAIM: BƯỚC 2c — Nhập email (chỉ 1 field) ==================== --%>
        <c:if test="${step eq 'claim-email'}">
            <span class="phone-chip register-phone-chip">
                <i class="fa-solid fa-phone"></i>
                <span><c:out value="${phone}" /></span>
                <a href="${pageContext.request.contextPath}/register">Đổi số</a>
            </span>

            <div class="auth-head">
                <h1>Kích hoạt hồ sơ</h1>
                <p>Hệ thống tìm thấy hồ sơ khớp với số điện thoại của bạn. Nhập email đã đăng ký tại quầy để xác thực.</p>
            </div>

            <c:if test="${not empty errorMessage}">
                <div class="msg-banner error" role="alert">
                    <i class="fa-solid fa-triangle-exclamation"></i>
                    <span><c:out value="${errorMessage}" /></span>
                </div>
            </c:if>

            <form id="claimEmailForm"
                  action="${pageContext.request.contextPath}/register"
                  method="post"
                  novalidate>

                <input type="hidden" name="step" value="claim-email">
                <input type="hidden" name="phone" value="<c:out value='${phone}' />">

                <label class="field">
                    <span class="field-label">
                        Email đã đăng ký tại quầy
                        <span class="required-mark">*</span>
                    </span>

                    <input type="email"
                           id="claimEmail"
                           name="email"
                           maxlength="100"
                           autocomplete="email"
                           placeholder="ban@email.com"
                           data-rule="email"
                           required
                           value="<c:out value='${email}' />">

                    <span class="field-error"
                          id="claimEmailError"
                          ${empty fieldErrors.email ? 'hidden' : ''}>
                        <c:out value="${fieldErrors.email}" />
                    </span>
                </label>

                <button type="submit"
                        id="btnClaimEmail"
                        class="btn btn-primary btn-block">
                    Tiếp tục
                </button>
            </form>

            <p class="auth-switch">
                <a href="${pageContext.request.contextPath}/register">
                    <i class="fa-solid fa-arrow-left"></i> Dùng số khác
                </a>
            </p>
        </c:if>

        <%-- ==================== CLAIM: BƯỚC 3c — Nhập mã OTP ==================== --%>
        <c:if test="${step eq 'claim-otp'}">
            <div class="auth-head">
                <h1>Nhập mã xác thực</h1>
            </div>

            <div class="msg-banner info" role="status">
                <i class="fa-solid fa-circle-info"></i>
                <span>
                    Mã xác thực đã gửi tới email
                    <strong><c:out value="${maskedEmail}" /></strong>.
                    Mã có hiệu lực trong
                    <strong id="otpCountdown" data-expires="${otpExpiresAt}"></strong>.
                </span>
            </div>

            <c:if test="${not empty successMessage}">
                <div class="msg-banner success" role="status">
                    <i class="fa-solid fa-circle-check"></i>
                    <span><c:out value="${successMessage}" /></span>
                </div>
            </c:if>

            <c:if test="${not empty errorMessage}">
                <div class="msg-banner error" role="alert">
                    <i class="fa-solid fa-triangle-exclamation"></i>
                    <span><c:out value="${errorMessage}" /></span>
                </div>
            </c:if>

            <form id="claimOtpForm"
                  action="${pageContext.request.contextPath}/register"
                  method="post"
                  novalidate>

                <input type="hidden" name="step" value="claim-otp">
                <input type="hidden" name="phone" value="<c:out value='${phone}' />">
                <input type="hidden" name="email" value="<c:out value='${email}' />">

                <label class="field">
                    <span class="field-label">
                        Mã xác thực (6 số)
                        <span class="required-mark">*</span>
                    </span>

                    <input type="text"
                           id="claimOtpCode"
                           name="code"
                           class="otp-input"
                           inputmode="numeric"
                           maxlength="6"
                           placeholder="––––––"
                           autocomplete="one-time-code"
                           required>

                    <span class="field-hint">
                        <i class="fa-solid fa-circle-info"></i>
                        <span>Không nhận được mã? Kiểm tra hộp thư rác hoặc bấm Gửi lại mã.</span>
                    </span>

                    <span class="field-error"
                          id="claimOtpCodeError"
                          ${empty fieldErrors.code ? 'hidden' : ''}>
                        <c:out value="${fieldErrors.code}" />
                    </span>
                </label>

                <button type="submit"
                        id="btnClaimVerifyOtp"
                        class="btn btn-primary btn-block">
                    Xác thực
                </button>
            </form>

            <div class="center-row">
                <form action="${pageContext.request.contextPath}/register"
                      method="post"
                      id="claimResendForm">
                    <input type="hidden" name="step" value="claim-resend">
                    <input type="hidden" name="phone" value="<c:out value='${phone}' />">
                    <input type="hidden" name="email" value="<c:out value='${email}' />">
                    <button type="submit"
                            class="btn-link"
                            id="claimResendBtn"
                            data-resend-unlock="${resendUnlockAt}">
                        Gửi lại mã
                    </button>
                </form>
            </div>

            <p class="auth-switch">
                <a href="${pageContext.request.contextPath}/register">
                    <i class="fa-solid fa-arrow-left"></i> Dùng số khác
                </a>
            </p>
        </c:if>

        <%-- ==================== CLAIM: BƯỚC 4c — Đặt mật khẩu ==================== --%>
        <c:if test="${step eq 'claim-password'}">
            <div class="auth-head">
                <h1>Đặt mật khẩu</h1>
                <p>Xác thực thành công! Đặt mật khẩu để hoàn tất kích hoạt tài khoản.</p>
            </div>

            <c:if test="${not empty errorMessage}">
                <div class="msg-banner error" role="alert">
                    <i class="fa-solid fa-triangle-exclamation"></i>
                    <span><c:out value="${errorMessage}" /></span>
                </div>
            </c:if>

            <form id="claimPasswordForm"
                  action="${pageContext.request.contextPath}/register"
                  method="post"
                  novalidate>

                <input type="hidden" name="step" value="claim-password">
                <input type="hidden" name="phone" value="<c:out value='${phone}' />">
                <input type="hidden" name="email" value="<c:out value='${email}' />">

                <label class="field">
                    <span class="field-label">
                        Mật khẩu mới
                        <span class="required-mark">*</span>
                    </span>

                    <span class="input-wrap">
                        <input type="password"
                               id="claimPassword"
                               name="password"
                               autocomplete="new-password"
                               placeholder="8–64 ký tự, có chữ và số"
                               data-rule="password"
                               required>

                        <button type="button"
                                class="pw-toggle"
                                data-password-toggle="claimPassword"
                                aria-label="Hiện hoặc ẩn mật khẩu">
                            <i class="fa-solid fa-eye"></i>
                        </button>
                    </span>

                    <span class="field-error"
                          id="claimPasswordError"
                          ${empty fieldErrors.password ? 'hidden' : ''}>
                        <c:out value="${fieldErrors.password}" />
                    </span>
                </label>

                <label class="field">
                    <span class="field-label">
                        Xác nhận mật khẩu
                        <span class="required-mark">*</span>
                    </span>

                    <span class="input-wrap">
                        <input type="password"
                               id="claimConfirmPassword"
                               name="confirmPassword"
                               autocomplete="new-password"
                               placeholder="Nhập lại mật khẩu"
                               data-match="claimPassword"
                               required>

                        <button type="button"
                                class="pw-toggle"
                                data-password-toggle="claimConfirmPassword"
                                aria-label="Hiện hoặc ẩn mật khẩu">
                            <i class="fa-solid fa-eye"></i>
                        </button>
                    </span>

                    <span class="field-error"
                          id="claimConfirmPasswordError"
                          ${empty fieldErrors.confirmPassword ? 'hidden' : ''}>
                        <c:out value="${fieldErrors.confirmPassword}" />
                    </span>
                </label>

                <button type="submit"
                        id="btnClaimSetPassword"
                        class="btn btn-primary btn-block">
                    Hoàn tất kích hoạt
                </button>
            </form>

            <p class="auth-switch">
                <a href="${pageContext.request.contextPath}/register">
                    <i class="fa-solid fa-arrow-left"></i> Dùng số khác
                </a>
            </p>
        </c:if>

        <%-- ==================== NHÁNH: SĐT ĐÃ CÓ TÀI KHOẢN (CBR-26) ==================== --%>
        <c:if test="${step eq 'blocked-account'}">
            <span class="phone-chip register-phone-chip">
                <i class="fa-solid fa-phone"></i>
                <span><c:out value="${phone}" /></span>
                <a href="${pageContext.request.contextPath}/register">Đổi số</a>
            </span>

            <div class="auth-head">
                <h1>Không tạo được tài khoản</h1>
            </div>

            <div class="msg-banner warning" role="alert">
                <i class="fa-solid fa-triangle-exclamation"></i>
                <span>Số điện thoại này đã có tài khoản. Vui lòng đăng nhập hoặc dùng chức năng Quên mật khẩu.</span>
            </div>

            <div class="btn-row">
                <a class="btn btn-primary"
                   href="${pageContext.request.contextPath}/login">Đăng nhập</a>
                <a class="btn btn-outline"
                   href="${pageContext.request.contextPath}/forgot-password">Quên mật khẩu</a>
            </div>

            <p class="auth-switch">
                <a href="${pageContext.request.contextPath}/register">
                    <i class="fa-solid fa-arrow-left"></i> Dùng số khác
                </a>
            </p>
        </c:if>

        <%-- ==================== NHÁNH: EMAIL KHÔNG KHỚP HỒ SƠ (CBR-25) ==================== --%>
        <c:if test="${step eq 'blocked-email-mismatch'}">
            <div class="auth-head">
                <h1>Email không khớp hồ sơ</h1>
            </div>

            <div class="msg-banner warning" role="alert">
                <i class="fa-solid fa-triangle-exclamation"></i>
                <span>
                    Số điện thoại này đã đăng ký với một email khác
                    (<strong><c:out value="${maskedEmail}" /></strong>).
                    Dùng đúng email đó, hoặc ra quầy để được hỗ trợ.
                </span>
            </div>

            <div class="counter-box">
                <i class="fa-solid fa-location-dot"></i>
                <span>
                    <strong>Quầy Lễ tân:</strong> 12 Nguyễn Huệ, Q.1, TP.HCM — mở cửa 06:00 – 22:00 mỗi ngày. Mang theo giấy tờ tuỳ thân để Lễ tân đối chiếu hồ sơ.
                </span>
            </div>

            <div class="btn-row">
                <%-- Quay lại bước claim-email để thử email khác (giữ lại số điện thoại) --%>
                <form action="${pageContext.request.contextPath}/register" method="get" style="display:contents">
                    <input type="hidden" name="step" value="2">
                    <input type="hidden" name="phone" value="<c:out value='${phone}' />">
                    <button type="submit" class="btn btn-primary" id="retryEmailBtn">Thử email khác</button>
                </form>
                <a class="btn btn-outline"
                   href="${pageContext.request.contextPath}/register">Dùng số khác</a>
            </div>
        </c:if>

        <%-- ==================== NHÁNH: HỒ SƠ CHƯA CÓ EMAIL (CBR-27) ==================== --%>
        <c:if test="${step eq 'blocked-no-email'}">
            <div class="auth-head">
                <h1>Cần bổ sung thông tin tại quầy</h1>
            </div>

            <div class="msg-banner warning" role="alert">
                <i class="fa-solid fa-triangle-exclamation"></i>
                <span>Hồ sơ của số điện thoại này chưa có email. Vui lòng ra quầy gặp Lễ tân để bổ sung trước khi tạo tài khoản.</span>
            </div>

            <div class="counter-box">
                <i class="fa-solid fa-location-dot"></i>
                <span>
                    <strong>Quầy Lễ tân:</strong> 12 Nguyễn Huệ, Q.1, TP.HCM — mở cửa 06:00 – 22:00 mỗi ngày. Mang theo giấy tờ tuỳ thân để Lễ tân đối chiếu hồ sơ.
                </span>
            </div>

            <a class="btn btn-outline btn-block"
               href="${pageContext.request.contextPath}/register">
                <i class="fa-solid fa-arrow-left"></i> Dùng số khác
            </a>
        </c:if>

        <%-- ==================== THÀNH CÔNG ==================== --%>
        <c:if test="${step eq 'success'}">
            <div class="success-panel">
                <div class="success-icon">
                    <i class="fa-solid fa-circle-check"></i>
                </div>

                <h1>Tạo tài khoản thành công</h1>

                <%-- registerMode: "new" = tạo mới, "linked" = kích hoạt hồ sơ cũ (UC-Claim) --%>
                <c:choose>
                    <c:when test="${registerMode eq 'linked'}">
                        <p>Hồ sơ cũ của bạn tại phòng gym đã được gắn vào tài khoản mới — toàn bộ lịch sử gói tập được giữ nguyên.</p>
                    </c:when>
                    <c:otherwise>
                        <p>Tài khoản đã sẵn sàng. Từ giờ bạn là hội viên và dùng được các chức năng dành cho khách hàng.</p>
                    </c:otherwise>
                </c:choose>

                <div class="next-box">
                    <h4>Bước tiếp theo</h4>
                    <ul>
                        <li>Chọn và thanh toán gói tập ngay.</li>
                        <li>Ra quầy chụp ảnh check-in (bắt buộc với mọi gói, trừ gói ngày).</li>
                        <li>Muốn tập với PT thì đăng ký gói PT tại quầy sau khi đã có gói tập.</li>
                    </ul>
                </div>

                <a class="btn btn-primary btn-block"
                   href="${pageContext.request.contextPath}/login">
                    Đăng nhập ngay
                </a>
            </div>
        </c:if>

    </div><%-- .auth-card --%>

    <p class="auth-foot-note">© 2026 Super Gym</p>
</div><%-- .auth-shell --%>

</body>
</html>