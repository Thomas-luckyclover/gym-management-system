<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>

<!DOCTYPE html>
<html lang="vi">
<head>
    <%@ include file="/WEB-INF/jsp/common/head.jspf" %>

    <title>Tạo tài khoản — Super Gym</title>

    <link rel="stylesheet"
          href="${pageContext.request.contextPath}/css/pages/auth/register.css">

    <script src="${pageContext.request.contextPath}/js/pages/auth/register.js?v=2"
            defer></script>
</head>

<body class="theme-guest-dark auth-dark">
<%@ include file="/WEB-INF/jsp/common/flash-toast.jspf" %>

<div class="auth-shell">
    <div class="auth-top">
        <a class="auth-brand"
           href="${pageContext.request.contextPath}/">
            Super Gym
        </a>

        <a class="auth-back"
           href="${pageContext.request.contextPath}/">
            <i class="fa-solid fa-arrow-left"></i>
            Về trang chủ
        </a>
    </div>

    <div class="auth-card">
        <c:if test="${not empty errorMessage}">
            <div class="msg-banner error" role="alert">
                <i class="fa-solid fa-triangle-exclamation"></i>
                <span>
                    <c:out value="${errorMessage}" />
                </span>
            </div>
        </c:if>

        <c:choose>
            <%-- ==================== BƯỚC 1 ==================== --%>
            <c:when test="${step ne '2'}">
                <div class="auth-head">
                    <h1>Tạo tài khoản</h1>
                    <p>
                        Nhập số điện thoại để hệ thống kiểm tra hồ sơ sẵn có
                        tại phòng gym.
                    </p>
                </div>

                <form id="registerPhoneForm"
                      action="${pageContext.request.contextPath}/register"
                      method="post"
                      novalidate>

                    <input type="hidden" name="step" value="1">

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

                        <span class="field-error"
                              id="phoneError"></span>
                    </label>

                    <button type="submit"
                            id="btnContinueRegister"
                            class="btn btn-primary btn-block">
                        Tiếp tục
                    </button>
                </form>
            </c:when>

            <%-- ==================== BƯỚC 2 ==================== --%>
            <c:otherwise>
                <div class="register-phone-chip">
                    <i class="fa-solid fa-phone"></i>

                    <span>
                        <c:out value="${phone}" />
                    </span>

                    <a href="${pageContext.request.contextPath}/register">
                        Đổi số
                    </a>
                </div>

                <div class="auth-head">
                    <h1>Tạo tài khoản</h1>
                    <p>
                        Điền thông tin để tạo tài khoản hội viên Super Gym.
                    </p>
                </div>

                <form id="registerForm"
                      action="${pageContext.request.contextPath}/register"
                      method="post"
                      novalidate>

                    <input type="hidden" name="step" value="2">

                    <input type="hidden"
                           name="phone"
                           value="<c:out value='${phone}' />">

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
                               required
                               value="<c:out value='${email}' />">

                        <span class="field-error"
                              id="emailError"></span>
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
                                   required>

                            <button type="button"
                                    class="pw-toggle"
                                    data-password-toggle="password"
                                    aria-label="Hiện hoặc ẩn mật khẩu">
                                <i class="fa-solid fa-eye"></i>
                            </button>
                        </span>

                        <span class="field-error"
                              id="passwordError"></span>
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
                                   required>

                            <button type="button"
                                    class="pw-toggle"
                                    data-password-toggle="confirmPassword"
                                    aria-label="Hiện hoặc ẩn mật khẩu">
                                <i class="fa-solid fa-eye"></i>
                            </button>
                        </span>

                        <span class="field-error"
                              id="confirmPasswordError"></span>
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
                               required
                               value="<c:out value='${fullName}' />">

                        <span class="field-error"
                              id="fullNameError"></span>
                    </label>

                    <label class="field">
                        <span class="field-label">
                            Ngày sinh
                            <span class="required-mark">*</span>
                        </span>

                        <input type="date"
                               id="dateOfBirth"
                               name="dateOfBirth"
                               required
                               value="<c:out value='${dateOfBirth}' />">

                        <span class="field-hint">
                            <i class="fa-solid fa-circle-info"></i>
                            <span>
                                Thành viên dưới 10 tuổi không thể mua gói PT.
                                Nếu đăng ký cho trẻ em, vui lòng liên hệ quầy.
                            </span>
                        </span>

                        <span class="field-error"
                              id="dateOfBirthError"></span>
                    </label>

                    <button type="submit"
                            id="btnSubmitRegister"
                            class="btn btn-primary btn-block">
                        Tạo tài khoản
                    </button>
                </form>
            </c:otherwise>
        </c:choose>

        <p class="auth-switch">
            Đã có tài khoản?
            <a href="${pageContext.request.contextPath}/login">
                Đăng nhập
            </a>
        </p>
    </div>
</div>
</body>
</html>