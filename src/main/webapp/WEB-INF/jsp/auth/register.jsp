<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%--
================================================================================
 register.jsp — MÀN ĐĂNG KÝ (/register, màn #2). Nhiều bước trong 1 trang, mỗi lần chỉ 1 bước hiện.

 CÁCH CHẠY: server-side rendering, không có JS chuyển cảnh. Mỗi nút "Tiếp tục" POST form →
 RegisterServlet.doPost đọc ô ẩn "step" → gọi Service → forward lại JSP này với step mới.

 AI LÀM GÌ, DAO ĐỌC/GHI BẢNG NÀO
   RegistrationService.checkPhone          PersonDAO.findByPhone (Persons.Phone) + có Accounts chưa?
                                           → quyết định nhánh: đăng ký mới / kích hoạt hồ sơ quầy / bị chặn
   RegistrationService.validateRegistrationForm  kiểm tra ô; PersonDAO.existsByEmail (Persons.Email trùng?)
   VerificationCodeService.sendCode / verifyCode  VerificationCodes (mã 6 số, P-15 = 15 phút, P-17 = 5 lần sai)
   RegistrationService.register            1 transaction: PersonDAO.insert → CustomerDAO.insert (mã KH-xxxx) → thêm Accounts
   RegistrationService.claimAccount        chỉ thêm Accounts gắn vào Persons cũ (hồ sơ lễ tân tạo tại quầy)

 GIÁ TRỊ "step" (Servlet đặt)
   Đăng ký mới:   "phone" (Bước 1 nhập SĐT) → "form" (Bước 2 điền thông tin) → "otp" (Bước 3 nhập mã)
   Kích hoạt hồ sơ quầy (UC-Claim): "claim-email" (2c) → "claim-otp" (3c) → "claim-password" (4c)
   Bị chặn:       "blocked-account" (SĐT đã có tài khoản, CBR-26) · "blocked-email-mismatch" (email sai, CBR-25)
                  · "blocked-no-email" (hồ sơ cũ chưa có email, CBR-27)
   Kết quả:       "success"

 BIẾN KHÁC
   phone, email, fullName, dateOfBirth, password, registerMode   giữ lại chữ khách đã gõ / chuyển giữa các bước (ô ẩn)
   maskedEmail      Servlet che bớt email, VD "ma***@gmail.com"
   otpExpiresAt     Servlet: bây giờ + 15 phút → register.js đếm lùi "14:59"
   resendUnlockAt   Servlet: bây giờ + 60 giây → nút "Gửi lại mã" khóa tới mốc này
   errorMessage / successMessage / fieldErrors   khung đỏ / khung xanh / chữ đỏ dưới ô
================================================================================ --%>
<!DOCTYPE html>
<html lang="vi">

<head>
    <%@ include file="/WEB-INF/jsp/common/head.jspf" %>

    <title>Tạo tài khoản — Super Gym</title>

    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/auth/register.css?v=20261014b">

    <script src="${pageContext.request.contextPath}/js/pages/auth/register.js?v=3" defer></script>
</head>

<body class="theme-guest-dark auth-dark auth-page-register">
    <%@ include file="/WEB-INF/jsp/common/flash-toast.jspf" %>

    <%-- ##### KHUNG TRANG: ảnh nền + logo + link "Về trang chủ" + thẻ ở giữa ##### --%>
    <div class="auth-shell">
        <%@ include file="/WEB-INF/jsp/common/auth-artwork.jspf" %>
        <div class="auth-top">
            <a class="auth-brand" href="${pageContext.request.contextPath}/" aria-label="Super Gym — Trang chủ"><img class="auth-brand__logo" src="${pageContext.request.contextPath}/images/brand/super-gym-wordmark.svg?v=20261014d" alt="Super Gym" width="170" height="44">
                <%@ include file="/WEB-INF/jsp/common/auth-muse.jspf" %></a>

            <a class="auth-back" href="${pageContext.request.contextPath}/">
                <i class="fa-solid fa-arrow-left"></i>
                Về trang chủ
            </a>
        </div>

        <div class="auth-card">

            <%-- ==========================================================
                 BƯỚC 1. NHẬP SỐ ĐIỆN THOẠI — BẮT ĐẦU   (step = "phone" hoặc chưa có step)
                 Gửi lên: step=phone, phone. Servlet gọi checkPhone để chọn nhánh tiếp theo.
                 Lỗi SĐT (fieldErrors.phone, VD "Số điện thoại phải gồm 10 chữ số…") → khung đỏ trên form.
                 ========================================================== --%>
            <c:if test="${step eq 'phone' or empty step}">
                <div class="auth-head">
                    <h1>Tạo tài khoản</h1>
                </div>

                <c:if test="${not empty fieldErrors.phone}">
                    <div class="msg-banner error" role="alert">
                        <i class="fa-solid fa-triangle-exclamation"></i>
                        <span>
                            <c:out value="${fieldErrors.phone}" />
                        </span>
                    </div>
                </c:if>

                <form id="registerPhoneForm"
                    action="${pageContext.request.contextPath}/register" method="post"
                    novalidate>

                    <input type="hidden" name="step" value="phone">

                    <%-- Ô SĐT: giữ lại phone đã gõ; gợi ý bên dưới thay cho dòng sub-title cũ. --%>
                    <label class="field">
                        <span class="field-label">
                            Số điện thoại
                            <span class="required-mark">*</span>
                        </span>

                        <input type="tel" id="phone" name="phone" maxlength="10"
                            inputmode="numeric" autocomplete="tel" placeholder="0912345678"
                            aria-describedby="phoneHint"
                            required value="<c:out value='${phone}' />">
                        <span class="field-hint" id="phoneHint">Đã có hồ sơ tại quầy? Hệ thống tự tìm theo số này.</span>

                        <span class="field-error" id="phoneError"></span>
                    </label>

                    <button type="submit" id="btnContinueRegister"
                        class="btn btn-primary btn-block">
                        Tiếp tục
                    </button>
                </form>

                <p class="auth-switch">
                    Đã có tài khoản?
                    <a href="${pageContext.request.contextPath}/login">Đăng nhập</a>
                </p>
            </c:if>
            <%-- BƯỚC 1 — KẾT THÚC --%>

            <%-- ==========================================================
                 BƯỚC 2. ĐIỀN THÔNG TIN (ĐĂNG KÝ MỚI) — BẮT ĐẦU   (step = "form")
                 SĐT chưa có hồ sơ nào. Chip trên cùng "📞 0912345678  Đổi số".
                 4 ô: Email, Mật khẩu (+ xác nhận), Họ và tên, Ngày sinh. Giữ lại chữ đã gõ khi báo lỗi (trừ mật khẩu).
                 Lỗi gắn ô: fieldErrors.email (VD email đã dùng), fullName (2–50 ký tự), dateOfBirth.
                 Đúng hết → Servlet gửi mã OTP tới email rồi sang bước 3.
                 ========================================================== --%>
            <c:if test="${step eq 'form'}">
                <span class="phone-chip register-phone-chip">
                    <i class="fa-solid fa-phone"></i>
                    <span>
                        <c:out value="${phone}" />
                    </span>
                    <a href="${pageContext.request.contextPath}/register">Đổi số</a>
                </span>

                <div class="auth-head">
                    <h1>Tạo tài khoản</h1>
                </div>

                <%-- Lỗi chung (validate thất bại, gửi mã lỗi…) → khung đỏ. --%>
                <c:if test="${not empty errorMessage}">
                    <div class="msg-banner error" role="alert">
                        <i class="fa-solid fa-triangle-exclamation"></i>
                        <span>
                            <c:out value="${errorMessage}" />
                        </span>
                    </div>
                </c:if>

                <form id="registerForm"
                    action="${pageContext.request.contextPath}/register" method="post"
                    data-unsaved-warning novalidate>

                    <input type="hidden" name="step" value="form">
                    <input type="hidden" name="phone"
                        value="<c:out value='${phone}' />">
                    <input type="hidden" name="registerMode" value="new">

                    <%-- Email → sau này lưu Persons.Email. --%>
                    <label class="field">
                        <span class="field-label">
                            Email
                            <span class="required-mark">*</span>
                        </span>

                        <input type="email" id="email" name="email" maxlength="100"
                            autocomplete="email" placeholder="ban@email.com"
                            data-rule="email" required
                            value="<c:out value='${email}' />">

                        <span class="field-error" id="emailError" ${empty fieldErrors.email ? 'hidden' : '' }>
                            <c:out value="${fieldErrors.email}" />
                        </span>
                    </label>

                    <%-- Mật khẩu 8–64 ký tự có chữ và số → lưu băm BCrypt vào Accounts.PasswordHash. --%>
                    <label class="field">
                        <span class="field-label">
                            Mật khẩu
                            <span class="required-mark">*</span>
                        </span>

                        <span class="input-wrap">
                            <input type="password" id="password" name="password"
                                autocomplete="new-password"
                                placeholder="8–64 ký tự, có chữ và số"
                                data-rule="password" required>

                            <button type="button" class="pw-toggle"
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
                            <input type="password" id="confirmPassword"
                                name="confirmPassword" autocomplete="new-password"
                                placeholder="Nhập lại mật khẩu" data-match="password"
                                required>

                            <button type="button" class="pw-toggle"
                                data-password-toggle="confirmPassword"
                                aria-label="Hiện hoặc ẩn mật khẩu">
                                <i class="fa-solid fa-eye"></i>
                            </button>
                        </span>

                        <span class="field-error" id="confirmPasswordError"></span>
                    </label>

                    <%-- Họ và tên → Persons.FullName. --%>
                    <label class="field">
                        <span class="field-label">
                            Họ và tên
                            <span class="required-mark">*</span>
                        </span>

                        <input type="text" id="fullName" name="fullName" maxlength="50"
                            autocomplete="name" placeholder="Nguyễn Văn A"
                            data-rule="fullName" required
                            value="<c:out value='${fullName}' />">

                        <span class="field-error" id="fullNameError" ${empty fieldErrors.fullName ? 'hidden' : '' }>
                            <c:out value="${fieldErrors.fullName}" />
                        </span>
                    </label>

                    <%-- Ngày sinh → Persons.DateOfBirth. Gợi ý ⓘ về tuổi tối thiểu dùng PT (P-05 = 10 tuổi). --%>
                    <label class="field">
                        <span class="field-label">
                            Ngày sinh
                            <span class="required-mark">*</span>
                        </span>

                        <input type="date" id="dateOfBirth" name="dateOfBirth"
                            data-rule="dateOfBirth" required
                            value="<c:out value='${dateOfBirth}' />">

                        <span class="field-hint">
                            <i class="fa-solid fa-circle-info"></i>
                            <span>
                                Lưu ý: Thành viên dưới 10 tuổi không thể mua gói PT.
                                Nếu đăng ký cho trẻ em, vui lòng liên hệ quầy.
                            </span>
                        </span>

                        <span class="field-error" id="dateOfBirthError" ${empty fieldErrors.dateOfBirth ? 'hidden' : '' }>
                            <c:out value="${fieldErrors.dateOfBirth}" />
                        </span>
                    </label>

                    <button type="submit" id="btnSubmitRegister"
                        class="btn btn-primary btn-block">
                        Tạo tài khoản
                    </button>
                </form>

                <p class="auth-switch">
                    Đã có tài khoản?
                    <a href="${pageContext.request.contextPath}/login">Đăng nhập</a>
                </p>
            </c:if>
            <%-- BƯỚC 2 — KẾT THÚC --%>

            <%-- ==========================================================
                 BƯỚC 3. NHẬP MÃ OTP (ĐĂNG KÝ MỚI) — BẮT ĐẦU   (step = "otp")
                 Khung xanh: "Nhập mã xác thực vừa gửi tới email ma***@gmail.com và SĐT 0912345678 … Mã có hiệu lực trong 14:59."
                 Mã đúng → RegistrationService.register tạo Persons + Customers + Accounts → step = "success".
                 Mọi thông tin bước 2 đi theo ô ẩn (form chính và form "Gửi lại mã").
                 ========================================================== --%>
            <c:if test="${step eq 'otp'}">
                <div class="auth-head">
                    <h1>Nhập mã xác thực</h1>
                </div>

                <div class="msg-banner info" role="status">
                    <i class="fa-solid fa-circle-info"></i>
                    <span>
                        Nhập mã xác thực vừa gửi tới email
                        <strong>
                            <c:out value="${maskedEmail}" />
                        </strong>
                        và SĐT <strong>
                            <c:out value="${phone}" />
                        </strong>
                        để hoàn tất đăng ký.
                        Mã có hiệu lực trong
                        <strong id="otpCountdown" data-expires="${otpExpiresAt}"></strong>.
                    </span>
                </div>

                <%-- successMessage: sau khi gửi lại mã, VD MSG45 "Đã gửi lại mã…" → khung xanh lá. --%>
                <c:if test="${not empty successMessage}">
                    <div class="msg-banner success" role="status">
                        <i class="fa-solid fa-circle-check"></i>
                        <span>
                            <c:out value="${successMessage}" />
                        </span>
                    </div>
                </c:if>

                <%-- Lỗi khi mã sai, hết hạn hoặc bị khóa (VerificationCodeService) → khung đỏ. --%>
                <c:if test="${not empty errorMessage}">
                    <div class="msg-banner error" role="alert">
                        <i class="fa-solid fa-triangle-exclamation"></i>
                        <span>
                            <c:out value="${errorMessage}" />
                        </span>
                    </div>
                </c:if>

                <form id="otpForm" action="${pageContext.request.contextPath}/register"
                    method="post" novalidate>

                    <input type="hidden" name="step" value="otp">
                    <%-- Giữ lại thông tin bước 2 để Servlet tạo tài khoản sau khi OTP đúng. --%>
                    <input type="hidden" name="phone"
                        value="<c:out value='${phone}' />">
                    <input type="hidden" name="email"
                        value="<c:out value='${email}' />">
                    <input type="hidden" name="fullName"
                        value="<c:out value='${fullName}' />">
                    <input type="hidden" name="dateOfBirth"
                        value="<c:out value='${dateOfBirth}' />">
                    <input type="hidden" name="registerMode"
                        value="<c:out value='${registerMode}' />">
                    <input type="hidden" name="password"
                        value="<c:out value='${password}' />">

                    <label class="field">
                        <span class="field-label">
                            Mã xác thực (6 số)
                            <span class="required-mark">*</span>
                        </span>

                        <input type="text" id="otpCode" name="code"
                            class="otp-input" inputmode="numeric" maxlength="6"
                            placeholder="––––––" autocomplete="one-time-code"
                            required>

                        <span class="field-hint">
                            <i class="fa-solid fa-circle-info"></i>
                            <span>Không nhận được mã? Kiểm tra hộp thư rác hoặc bấm
                                Gửi lại mã.</span>
                        </span>

                        <span class="field-error" id="otpCodeError" ${empty fieldErrors.code ? 'hidden' : '' }>
                            <c:out value="${fieldErrors.code}" />
                        </span>
                    </label>

                    <button type="submit" id="btnVerifyOtp"
                        class="btn btn-primary btn-block">
                        Xác thực
                    </button>
                </form>

                <%-- Nút "Gửi lại mã": POST riêng step=resend; VerificationCodeService kiểm tra cách 60 giây + 5 lần / giờ.
                     data-resend-unlock = resendUnlockAt → register.js khóa nút tới mốc đó. --%>
                <div class="center-row">
                    <form action="${pageContext.request.contextPath}/register"
                        method="post" id="resendForm">
                        <input type="hidden" name="step" value="resend">
                        <input type="hidden" name="phone"
                            value="<c:out value='${phone}' />">
                        <input type="hidden" name="email"
                            value="<c:out value='${email}' />">
                        <input type="hidden" name="fullName"
                            value="<c:out value='${fullName}' />">
                        <input type="hidden" name="dateOfBirth"
                            value="<c:out value='${dateOfBirth}' />">
                        <input type="hidden" name="registerMode"
                            value="<c:out value='${registerMode}' />">
                        <input type="hidden" name="password"
                            value="<c:out value='${password}' />">
                        <button type="submit" class="btn-link" id="resendBtn"
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
                <%-- Hộp gợi ý khi chưa nhận được mail (Pumpy). --%>
                <%@ include file="/WEB-INF/jsp/common/pumpy-mail-hint.jspf" %>
            </c:if>
            <%-- BƯỚC 3 — KẾT THÚC --%>

            <%-- ==========================================================
                 BƯỚC 2c. KÍCH HOẠT HỒ SƠ QUẦY: NHẬP EMAIL — BẮT ĐẦU   (step = "claim-email")
                 SĐT đã có hồ sơ do lễ tân tạo (Persons) nhưng chưa có Accounts. Chỉ 1 ô: email đã khai tại quầy.
                 Email khớp Persons.Email → gửi mã → bước 3c; không khớp → nhánh "blocked-email-mismatch".
                 ========================================================== --%>
            <c:if test="${step eq 'claim-email'}">
                <span class="phone-chip register-phone-chip">
                    <i class="fa-solid fa-phone"></i>
                    <span>
                        <c:out value="${phone}" />
                    </span>
                    <a href="${pageContext.request.contextPath}/register">Đổi số</a>
                </span>

                <div class="auth-head">
                    <h1>Kích hoạt hồ sơ</h1>
                </div>

                <c:if test="${not empty errorMessage}">
                    <div class="msg-banner error" role="alert">
                        <i class="fa-solid fa-triangle-exclamation"></i>
                        <span>
                            <c:out value="${errorMessage}" />
                        </span>
                    </div>
                </c:if>

                <form id="claimEmailForm"
                    action="${pageContext.request.contextPath}/register" method="post"
                    novalidate>

                    <input type="hidden" name="step" value="claim-email">
                    <input type="hidden" name="phone"
                        value="<c:out value='${phone}' />">

                    <label class="field">
                        <span class="field-label">
                            Email đã đăng ký tại quầy
                            <span class="required-mark">*</span>
                        </span>

                        <input type="email" id="claimEmail" name="email" maxlength="100"
                            autocomplete="email" placeholder="ban@email.com"
                            aria-describedby="claimEmailHint"
                            data-rule="email" required
                            value="<c:out value='${email}' />">
                        <%-- Gợi ý thay cho dòng sub-title cũ: báo đã tìm thấy hồ sơ + cần làm gì. --%>
                        <span class="field-hint" id="claimEmailHint">Đã tìm thấy hồ sơ tại quầy khớp số <c:out value="${phone}" />. Nhập đúng email đã đăng ký để xác thực.</span>

                        <span class="field-error" id="claimEmailError" ${empty fieldErrors.email ? 'hidden' : '' }>
                            <c:out value="${fieldErrors.email}" />
                        </span>
                    </label>

                    <button type="submit" id="btnClaimEmail"
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
            <%-- BƯỚC 2c — KẾT THÚC --%>

            <%-- ==========================================================
                 BƯỚC 3c. KÍCH HOẠT HỒ SƠ: NHẬP MÃ OTP — BẮT ĐẦU   (step = "claim-otp")
                 Giống bước 3 nhưng chỉ cần phone + email (hồ sơ đã có sẵn). Mã đúng → bước 4c.
                 ========================================================== --%>
            <c:if test="${step eq 'claim-otp'}">
                <div class="auth-head">
                    <h1>Nhập mã xác thực</h1>
                </div>

                <div class="msg-banner info" role="status">
                    <i class="fa-solid fa-circle-info"></i>
                    <span>
                        Mã xác thực đã gửi tới email
                        <strong>
                            <c:out value="${maskedEmail}" />
                        </strong>.
                        Mã có hiệu lực trong
                        <strong id="otpCountdown"
                            data-expires="${otpExpiresAt}"></strong>.
                    </span>
                </div>

                <c:if test="${not empty successMessage}">
                    <div class="msg-banner success" role="status">
                        <i class="fa-solid fa-circle-check"></i>
                        <span>
                            <c:out value="${successMessage}" />
                        </span>
                    </div>
                </c:if>

                <c:if test="${not empty errorMessage}">
                    <div class="msg-banner error" role="alert">
                        <i class="fa-solid fa-triangle-exclamation"></i>
                        <span>
                            <c:out value="${errorMessage}" />
                        </span>
                    </div>
                </c:if>

                <form id="claimOtpForm"
                    action="${pageContext.request.contextPath}/register"
                    method="post" novalidate>

                    <input type="hidden" name="step" value="claim-otp">
                    <input type="hidden" name="phone"
                        value="<c:out value='${phone}' />">
                    <input type="hidden" name="email"
                        value="<c:out value='${email}' />">

                    <label class="field">
                        <span class="field-label">
                            Mã xác thực (6 số)
                            <span class="required-mark">*</span>
                        </span>

                        <input type="text" id="claimOtpCode" name="code"
                            class="otp-input" inputmode="numeric" maxlength="6"
                            placeholder="––––––" autocomplete="one-time-code"
                            required>

                        <span class="field-hint">
                            <i class="fa-solid fa-circle-info"></i>
                            <span>Không nhận được mã? Kiểm tra hộp thư rác hoặc bấm
                                Gửi lại mã.</span>
                        </span>

                        <span class="field-error" id="claimOtpCodeError" ${empty fieldErrors.code ? 'hidden' : '' }>
                            <c:out value="${fieldErrors.code}" />
                        </span>
                    </label>

                    <button type="submit" id="btnClaimVerifyOtp"
                        class="btn btn-primary btn-block">
                        Xác thực
                    </button>
                </form>

                <%-- "Gửi lại mã" của nhánh kích hoạt: step=claim-resend. --%>
                <div class="center-row">
                    <form action="${pageContext.request.contextPath}/register"
                        method="post" id="claimResendForm">
                        <input type="hidden" name="step" value="claim-resend">
                        <input type="hidden" name="phone"
                            value="<c:out value='${phone}' />">
                        <input type="hidden" name="email"
                            value="<c:out value='${email}' />">
                        <button type="submit" class="btn-link" id="claimResendBtn"
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
                <%-- Hộp gợi ý khi chưa nhận được mail (Pumpy). --%>
                <%@ include file="/WEB-INF/jsp/common/pumpy-mail-hint.jspf" %>
            </c:if>
            <%-- BƯỚC 3c — KẾT THÚC --%>

            <%-- ==========================================================
                 BƯỚC 4c. KÍCH HOẠT HỒ SƠ: ĐẶT MẬT KHẨU — BẮT ĐẦU   (step = "claim-password")
                 Khung xanh lá "Xác thực thành công…" thay cho sub-title cũ. 2 ô mật khẩu.
                 Lỗi gắn ô: fieldErrors.password / confirmPassword. Xong → claimAccount thêm Accounts → step = "success".
                 ========================================================== --%>
            <c:if test="${step eq 'claim-password'}">
                <div class="auth-head">
                    <h1>Đặt mật khẩu</h1>
                </div>

                <div class="msg-banner success" role="status">
                    <i class="fa-solid fa-circle-check" aria-hidden="true"></i>
                    <span>Xác thực thành công — đặt mật khẩu để hoàn tất kích hoạt.</span>
                </div>

                <c:if test="${not empty errorMessage}">
                    <div class="msg-banner error" role="alert">
                        <i class="fa-solid fa-triangle-exclamation"></i>
                        <span>
                            <c:out value="${errorMessage}" />
                        </span>
                    </div>
                </c:if>

                <form id="claimPasswordForm"
                    action="${pageContext.request.contextPath}/register"
                    method="post" novalidate>

                    <input type="hidden" name="step" value="claim-password">
                    <input type="hidden" name="phone"
                        value="<c:out value='${phone}' />">
                    <input type="hidden" name="email"
                        value="<c:out value='${email}' />">

                    <label class="field">
                        <span class="field-label">
                            Mật khẩu mới
                            <span class="required-mark">*</span>
                        </span>

                        <span class="input-wrap">
                            <input type="password" id="claimPassword"
                                name="password" autocomplete="new-password"
                                placeholder="8–64 ký tự, có chữ và số"
                                data-rule="password" required>

                            <button type="button" class="pw-toggle"
                                data-password-toggle="claimPassword"
                                aria-label="Hiện hoặc ẩn mật khẩu">
                                <i class="fa-solid fa-eye"></i>
                            </button>
                        </span>

                        <span class="field-error" id="claimPasswordError"
                            ${empty fieldErrors.password ? 'hidden' : '' }>
                            <c:out value="${fieldErrors.password}" />
                        </span>
                    </label>

                    <label class="field">
                        <span class="field-label">
                            Xác nhận mật khẩu
                            <span class="required-mark">*</span>
                        </span>

                        <span class="input-wrap">
                            <input type="password" id="claimConfirmPassword"
                                name="confirmPassword"
                                autocomplete="new-password"
                                placeholder="Nhập lại mật khẩu"
                                data-match="claimPassword" required>

                            <button type="button" class="pw-toggle"
                                data-password-toggle="claimConfirmPassword"
                                aria-label="Hiện hoặc ẩn mật khẩu">
                                <i class="fa-solid fa-eye"></i>
                            </button>
                        </span>

                        <span class="field-error" id="claimConfirmPasswordError"
                            ${empty fieldErrors.confirmPassword ? 'hidden' : '' }>
                            <c:out value="${fieldErrors.confirmPassword}" />
                        </span>
                    </label>

                    <button type="submit" id="btnClaimSetPassword"
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
            <%-- BƯỚC 4c — KẾT THÚC --%>

            <%-- ==========================================================
                 NHÁNH CHẶN: SĐT ĐÃ CÓ TÀI KHOẢN (CBR-26) — BẮT ĐẦU   (step = "blocked-account")
                 checkPhone thấy Persons.Phone này đã gắn Accounts → khung vàng + nút "Đăng nhập" / "Quên mật khẩu".
                 ========================================================== --%>
            <c:if test="${step eq 'blocked-account'}">
                <span class="phone-chip register-phone-chip">
                    <i class="fa-solid fa-phone"></i>
                    <span>
                        <c:out value="${phone}" />
                    </span>
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
            <%-- NHÁNH SĐT ĐÃ CÓ TÀI KHOẢN — KẾT THÚC --%>

            <%-- ==========================================================
                 NHÁNH CHẶN: EMAIL KHÔNG KHỚP HỒ SƠ (CBR-25) — BẮT ĐẦU   (step = "blocked-email-mismatch")
                 maskedEmail = email thật của hồ sơ đã che bớt, VD "(ma***@gmail.com)" để khách nhớ ra.
                 Hộp địa chỉ quầy lễ tân là chữ cố định.
                 ========================================================== --%>
            <c:if test="${step eq 'blocked-email-mismatch'}">
                <div class="auth-head">
                    <h1>Email không khớp hồ sơ</h1>
                </div>

                <div class="msg-banner warning" role="alert">
                    <i class="fa-solid fa-triangle-exclamation"></i>
                    <span>
                        Số điện thoại này đã đăng ký với một email khác
                        (<strong>
                            <c:out value="${maskedEmail}" />
                        </strong>).
                        Dùng đúng email đó, hoặc ra quầy để được hỗ trợ.
                    </span>
                </div>

                <div class="counter-box">
                    <i class="fa-solid fa-location-dot"></i>
                    <span>
                        <strong>Quầy Lễ tân:</strong> 106/36 Cầu Giấy,
                        Tổ Dân Phố Số 12, Nghĩa Đô, Hà Nội — mở cửa 06:00 – 22:00 mỗi ngày.
                        Mang theo giấy tờ tuỳ thân để Lễ tân đối chiếu
                        hồ sơ.
                    </span>
                </div>

                <div class="btn-row">
                    <%-- "Thử email khác": GET quay lại bước claim-email, giữ lại số điện thoại. --%>
                    <form
                        action="${pageContext.request.contextPath}/register"
                        method="get" style="display:contents">
                        <input type="hidden" name="step" value="2">
                        <input type="hidden" name="phone"
                            value="<c:out value='${phone}' />">
                        <button type="submit"
                            class="btn btn-primary"
                            id="retryEmailBtn">Thử email khác</button>
                    </form>
                    <a class="btn btn-outline"
                        href="${pageContext.request.contextPath}/register">Dùng số khác</a>
                </div>
            </c:if>
            <%-- NHÁNH EMAIL KHÔNG KHỚP — KẾT THÚC --%>

            <%-- ==========================================================
                 NHÁNH CHẶN: HỒ SƠ CHƯA CÓ EMAIL (CBR-27) — BẮT ĐẦU   (step = "blocked-no-email")
                 Persons.Email của hồ sơ quầy đang trống → không gửi được mã → phải ra quầy bổ sung.
                 ========================================================== --%>
            <c:if test="${step eq 'blocked-no-email'}">
                <div class="auth-head">
                    <h1>Cần bổ sung thông tin tại quầy</h1>
                </div>

                <div class="msg-banner warning" role="alert">
                    <i class="fa-solid fa-triangle-exclamation"></i>
                    <span>Hồ sơ của số điện thoại này chưa có email.
                        Vui lòng ra quầy gặp Lễ tân để bổ sung trước
                        khi tạo tài khoản.</span>
                </div>

                <div class="counter-box">
                    <i class="fa-solid fa-location-dot"></i>
                    <span>
                        <strong>Quầy Lễ tân:</strong> 106/36 Cầu Giấy,
                        Tổ Dân Phố Số 12, Nghĩa Đô, Hà Nội — mở cửa 06:00 – 22:00 mỗi ngày.
                        Mang theo giấy tờ tuỳ thân để Lễ tân đối
                        chiếu hồ sơ.
                    </span>
                </div>

                <a class="btn btn-outline btn-block"
                    href="${pageContext.request.contextPath}/register">
                    <i class="fa-solid fa-arrow-left"></i> Dùng số khác
                </a>
            </c:if>
            <%-- NHÁNH HỒ SƠ CHƯA CÓ EMAIL — KẾT THÚC --%>

            <%-- ==========================================================
                 THÀNH CÔNG — BẮT ĐẦU   (step = "success")
                 Dấu ✓ to + "Tạo tài khoản thành công" + câu theo registerMode:
                   "new"    = vừa tạo mới Persons + Customers + Accounts
                   "linked" = kích hoạt hồ sơ cũ tại quầy (UC-Claim), lịch sử gói giữ nguyên
                 Hộp "Bước tiếp theo" là chữ cố định. Nút "Đăng nhập ngay" → /login.
                 ========================================================== --%>
            <c:if test="${step eq 'success'}">
                <div class="success-panel">
                    <div class="success-icon">
                        <i class="fa-solid fa-circle-check"></i>
                    </div>

                    <h1>Tạo tài khoản thành công</h1>

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
            <%-- THÀNH CÔNG — KẾT THÚC --%>

        </div><%-- .auth-card --%>

        <p class="auth-foot-note">© 2026 Super Gym</p>
    </div><%-- .auth-shell --%>
    <%-- ##### KHUNG TRANG — KẾT THÚC ##### --%>

</body>

</html>
