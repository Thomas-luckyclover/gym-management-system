<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!DOCTYPE html>
<html lang="vi">
    <head>
        <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
        <title>Đăng nhập — Super Gym</title>
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/auth/login.css?v=8">
        <script src="${pageContext.request.contextPath}/js/pages/auth/login.js?v=2" defer></script>
    </head>
    <%-- Theme tối + kính mờ: khớp prototype dang-nhap.html (body.dark.auth-dark) --%>
    <body class="theme-guest-dark auth-dark">
        <%@ include file="/WEB-INF/jsp/common/flash-toast.jspf" %>

        <%--
          loginStep do LoginServlet set khi POST thất bại:
          form | blocked-no-password (MSG38) | blocked-lock (MSG35).
          GET mặc định là form.
        --%>
        <c:set var="loginStep" value="${empty loginStep ? 'form' : loginStep}" />

        <div class="auth-shell">
            <div class="auth-top">
                <a class="auth-brand" href="${pageContext.request.contextPath}/"><img src="${pageContext.request.contextPath}/images/logo.png?v=3" alt="Super Gym" width="52" height="52"></a>
                <a class="auth-back" href="${pageContext.request.contextPath}/">
                    <i class="fa-solid fa-arrow-left"></i> Về trang chủ
                </a>
            </div>

            <div class="auth-card">

                <%-- ---------- Form đăng nhập ---------- --%>
                <div class="step-panel" data-step="form" <c:if test="${loginStep ne 'form'}">hidden</c:if>>
                        <div class="auth-head">
                            <h1>Đăng nhập</h1>
                            <p>Nhập email hoặc số điện thoại và mật khẩu để tiếp tục.</p>
                        </div>

                    <c:if test="${not empty errorMessage and loginStep eq 'form'}">
                        <div class="msg-banner error" role="alert">
                            <i class="fa-solid fa-triangle-exclamation"></i>
                            <span><c:out value="${errorMessage}" /></span>
                        </div>
                    </c:if>

                    <form id="loginForm"
                          action="${pageContext.request.contextPath}/login"
                          method="post"
                          novalidate>
                        <c:if test="${not empty returnUrl}">
                            <input type="hidden" name="returnUrl" value="<c:out value='${returnUrl}' />">
                        </c:if>

                        <label class="field">
                            <span class="field-label">Email hoặc số điện thoại</span>
                            <input id="identifier"
                                   name="identifier"
                                   type="text"
                                   placeholder="ban@email.com hoặc 090xxxxxxx"
                                   autocomplete="username"
                                   value="<c:out value='${identifier}' />"
                                   required>
                            <span class="field-error"
                                  id="identifierError"
                                  <c:if test="${empty fieldErrors.identifier}">hidden</c:if>><c:out value="${fieldErrors.identifier}" /></span>
                            </label>

                            <label class="field">
                                <span class="field-label">Mật khẩu</span>
                                <span class="input-wrap">
                                    <input id="password"
                                           name="password"
                                           type="password"
                                           placeholder="Nhập mật khẩu"
                                           autocomplete="current-password"
                                           required>
                                    <button type="button"
                                            class="pw-toggle"
                                            data-password-toggle="password"
                                            aria-label="Hiện mật khẩu">
                                        <i class="fa-solid fa-eye"></i>
                                    </button>
                                </span>
                                <span class="field-error"
                                      id="passwordError"
                                <c:if test="${empty fieldErrors.password}">hidden</c:if>><c:out value="${fieldErrors.password}" /></span>
                            </label>

                            <p class="auth-switch auth-forgot">
                                <a href="${pageContext.request.contextPath}/forgot-password">Quên mật khẩu?</a>
                        </p>

                        <button class="btn btn-primary btn-block" type="submit" id="loginSubmitBtn">
                            Đăng nhập
                        </button>
                    </form>

                    <p class="auth-switch">
                        Chưa có tài khoản?
                        <a href="${pageContext.request.contextPath}/register">Đăng ký</a>
                    </p>
                </div>

                <%-- ---------- Tài khoản chưa có mật khẩu (MSG38) ---------- --%>
                <div class="step-panel" data-step="blocked-no-password" <c:if test="${loginStep ne 'blocked-no-password'}">hidden</c:if>>
                        <div class="auth-head">
                            <h1>Chưa thể đăng nhập</h1>
                        </div>
                        <div class="msg-banner warning" role="alert">
                            <i class="fa-solid fa-triangle-exclamation"></i>
                            <span>
                            <c:choose>
                                <c:when test="${not empty errorMessage}">
                                    <c:out value="${errorMessage}" />
                                </c:when>
                                <c:otherwise>
                                    Tài khoản chưa có mật khẩu. Hãy dùng Quên mật khẩu để đặt mật khẩu.
                                </c:otherwise>
                            </c:choose>
                        </span>
                    </div>
                    <div class="btn-row">
                        <a class="btn btn-primary" href="${pageContext.request.contextPath}/forgot-password">Đặt mật khẩu</a>
                        <button class="btn btn-outline" type="button" data-back-form>Thử lại</button>
                    </div>
                </div>

                <%-- ---------- Khoá đăng nhập sau quá số lần sai (MSG35) ---------- --%>
                <div class="step-panel" data-step="blocked-lock" <c:if test="${loginStep ne 'blocked-lock'}">hidden</c:if>>
                        <div class="auth-head">
                            <h1>Tài khoản tạm khoá</h1>
                        </div>
                        <div class="msg-banner warning" role="alert">
                            <i class="fa-solid fa-triangle-exclamation"></i>
                            <span>
                            <c:choose>
                                <c:when test="${not empty errorMessage}">
                                    <c:out value="${errorMessage}" />
                                </c:when>
                                <c:otherwise>
                                    Bạn đã nhập sai mật khẩu quá số lần cho phép. Vui lòng thử lại sau, hoặc dùng chức năng Quên mật khẩu.
                                </c:otherwise>
                            </c:choose>
                        </span>
                    </div>

                    <%-- Đồng hồ đếm ngược tới khi tài khoản được mở khoá (GBR-27).
                         lockSecondsLeft do LoginServlet tính (parse từ MSG35) và set khi forward về panel blocked-lock. --%>
                    <c:if test="${not empty lockSecondsLeft and lockSecondsLeft > 0}">
                        <div class="lock-countdown">
                            <i class="fa-regular fa-clock"></i>
                            <span>Có thể thử lại sau:</span>
                            <b class="tabular countdown-badge"
                               data-countdown-seconds="${lockSecondsLeft}"
                               data-countdown-reload="true">00:00</b>
                        </div>
                    </c:if>

                    <div class="btn-row">
                        <a class="btn btn-primary" href="${pageContext.request.contextPath}/forgot-password">Quên mật khẩu</a>
                        <c:choose>
                            <c:when test="${not empty lockSecondsLeft and lockSecondsLeft > 0}">
                                <button class="btn btn-outline" type="button" disabled title="Tài khoản đang tạm khóa, vui lòng chờ hết thời gian đếm ngược">Quay lại</button>
                            </c:when>
                            <c:otherwise>
                                <button class="btn btn-outline" type="button" data-back-form>Quay lại</button>
                            </c:otherwise>
                        </c:choose>
                    </div>
                </div>

            </div>
            <p class="auth-foot-note">© 2026 Super Gym</p>
        </div>
    </body>
</html>
