<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!DOCTYPE html>
<html lang="vi">
<head>
    <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
    <title>Đăng nhập · Super Gym</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/auth/login.css">
    <script src="${pageContext.request.contextPath}/js/pages/auth/login.js" defer></script>
</head>
<body class="auth-page">
    <%@ include file="/WEB-INF/jsp/common/flash-toast.jspf" %>

    <main class="auth-container">
        <div class="auth-card">
            <!-- Header/Logo section -->
            <div class="auth-header">
                <a href="${pageContext.request.contextPath}/" class="auth-logo">
                    <i class="fa-solid fa-dumbbell logo-icon"></i>
                    <span class="logo-text">SUPER <strong>GYM</strong></span>
                </a>
                <h1 class="auth-title">Đăng nhập tài khoản</h1>
                <p class="auth-subtitle">Chào mừng bạn quay trở lại với Super Gym</p>
            </div>

            <!-- Banner báo lỗi chung nếu có -->
            <div id="generalErrorBanner" class="auth-alert error-alert <c:if test='${empty errorMessage}'>hidden</c:if>">
                <i class="fa-solid fa-circle-exclamation alert-icon"></i>
                <span id="generalErrorText"><c:out value="${errorMessage}" /></span>
            </div>

            <!-- Form đăng nhập -->
            <form id="loginForm" action="${pageContext.request.contextPath}/login" method="post" class="auth-form" novalidate>
                <c:if test="${not empty returnUrl}">
                    <input type="hidden" name="returnUrl" value="<c:out value='${returnUrl}' />">
                </c:if>

                <!-- Identifier: Email hoặc Số điện thoại -->
                <div class="form-group <c:if test='${not empty fieldErrors.identifier}'>has-error</c:if>">
                    <label for="identifierInput" class="form-label">Email hoặc Số điện thoại <span class="required-star">*</span></label>
                    <div class="input-wrapper">
                        <i class="fa-solid fa-user input-icon"></i>
                        <input type="text"
                               id="identifierInput"
                               name="identifier"
                               class="form-control"
                               placeholder="Nhập email hoặc số điện thoại đăng ký"
                               value="<c:out value='${identifier}' />"
                               autocomplete="username"
                               required>
                    </div>
                    <span id="identifierError" class="field-error">
                        <c:out value="${fieldErrors.identifier}" />
                    </span>
                </div>

                <!-- Password -->
                <div class="form-group <c:if test='${not empty fieldErrors.password}'>has-error</c:if>">
                    <div class="label-row">
                        <label for="passwordInput" class="form-label">Mật khẩu <span class="required-star">*</span></label>
                        <a href="${pageContext.request.contextPath}/forgot-password" class="forgot-link">Quên mật khẩu?</a>
                    </div>
                    <div class="input-wrapper">
                        <i class="fa-solid fa-lock input-icon"></i>
                        <input type="password"
                               id="passwordInput"
                               name="password"
                               class="form-control"
                               placeholder="Nhập mật khẩu của bạn"
                               autocomplete="current-password"
                               required>
                        <button type="button" id="togglePasswordBtn" class="toggle-password-btn" aria-label="Hiện/Ẩn mật khẩu">
                            <i class="fa-solid fa-eye eye-icon"></i>
                        </button>
                    </div>
                    <span id="passwordError" class="field-error">
                        <c:out value="${fieldErrors.password}" />
                    </span>
                </div>

                <!-- Submit Button -->
                <button type="submit" id="submitBtn" class="btn btn-primary btn-block btn-lg">
                    <span class="btn-text">Đăng nhập</span>
                    <i class="fa-solid fa-right-to-bracket btn-icon"></i>
                </button>
            </form>

            <!-- Footer links -->
            <div class="auth-footer">
                <p>Chưa có tài khoản? <a href="${pageContext.request.contextPath}/register" class="register-link">Đăng ký ngay</a></p>
                <a href="${pageContext.request.contextPath}/" class="back-home-link">
                    <i class="fa-solid fa-arrow-left"></i> Quay lại trang chủ
                </a>
            </div>
        </div>
    </main>
</body>
</html>
