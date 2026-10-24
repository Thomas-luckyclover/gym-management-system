<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%--
================================================================================
 login.jsp — MÀN ĐĂNG NHẬP (/login, màn #1). 3 "bảng" trong 1 trang, chỉ 1 bảng hiện.

 AI GỬI DỮ LIỆU SANG?
   GET  /login → LoginServlet.doGet: chỉ mở trang (có thể kèm returnUrl).
   POST /login → LoginServlet.doPost gọi AuthService.login(identifier, password):
       đúng  → lưu session (accountId, vai trò, displayName = Persons.FullName) rồi chuyển trang, KHÔNG quay lại JSP này
       sai   → forward lại JSP này với các biến dưới đây.
   DAO: AccountDAO đọc Accounts JOIN Persons (Email hoặc Phone, PasswordHash, Status, FailedLoginCount, LockedUntil);
        sai mật khẩu thì ghi lại FailedLoginCount / LockedUntil (P-17 = 5 lần, P-18 = 5 phút).

 BIẾN JSP
   loginStep        "form" | "blocked-no-password" (MSG38) | "blocked-lock" (MSG35); GET mặc định là form
   errorMessage     lỗi chung → khung đỏ / vàng
   fieldErrors      lỗi gắn ô (identifier, password) → chữ đỏ dưới ô
   identifier       chữ khách đã gõ ở ô đầu (giữ lại khi báo lỗi; mật khẩu thì KHÔNG giữ)
   lockSecondsLeft  số giây còn khóa (Servlet tính) → đồng hồ đếm ngược
   returnUrl        trang khách định vào trước khi bị bắt đăng nhập → đăng nhập xong quay lại đó

 Kiểm tra ô + nút con mắt + khóa nút khi gửi: login.js + common.js.
================================================================================ --%>
<!DOCTYPE html>
<html lang="vi">
    <head>
        <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
        <title>Đăng nhập — Super Gym</title>
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/auth/login.css?v=20261014b">
        <script src="${pageContext.request.contextPath}/js/pages/auth/login.js?v=2" defer></script>
    </head>
    <%-- Theme tối + kính mờ: khớp prototype dang-nhap.html (body.dark.auth-dark) --%>
    <body class="theme-guest-dark auth-dark">
        <%@ include file="/WEB-INF/jsp/common/flash-toast.jspf" %>

        <%-- loginStep do LoginServlet đặt khi POST thất bại. GET (chưa có giá trị) → bảng "form". --%>
        <c:set var="loginStep" value="${empty loginStep ? 'form' : loginStep}" />

        <%-- ##### KHUNG TRANG: ảnh nền + logo + link "Về trang chủ" + thẻ ở giữa ##### --%>
        <div class="auth-shell">
            <%@ include file="/WEB-INF/jsp/common/auth-artwork.jspf" %>
            <div class="auth-top">
                <a class="auth-brand" href="${pageContext.request.contextPath}/" aria-label="Super Gym — Trang chủ"><img class="auth-brand__logo" src="${pageContext.request.contextPath}/images/brand/super-gym-wordmark.svg?v=20261014d" alt="Super Gym" width="170" height="44">
                    <%@ include file="/WEB-INF/jsp/common/auth-muse.jspf" %></a>
                <a class="auth-back" href="${pageContext.request.contextPath}/">
                    <i class="fa-solid fa-arrow-left"></i> Về trang chủ
                </a>
            </div>

            <div class="auth-card">

                <%-- ==========================================================
                     BẢNG 1. FORM ĐĂNG NHẬP — BẮT ĐẦU   (loginStep = "form")
                     ========================================================== --%>
                <div class="step-panel" data-step="form" <c:if test="${loginStep ne 'form'}">hidden</c:if>>
                    <div class="auth-head">
                        <h1>Đăng nhập</h1>
                    </div>

                    <%-- Lỗi chung (AuthService trả), VD "Email, số điện thoại hoặc mật khẩu không đúng. Bạn còn 4 lần thử." (MSG33) → khung đỏ có ⚠. --%>
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
                        <%-- returnUrl (nếu có) gửi kèm để đăng nhập xong quay lại đúng trang. --%>
                        <c:if test="${not empty returnUrl}">
                            <input type="hidden" name="returnUrl" value="<c:out value='${returnUrl}' />">
                        </c:if>

                        <%-- Ô 1: email HOẶC số điện thoại (AuthService tự nhận ra loại nào).
                             value = identifier đã gõ; lỗi gắn ô fieldErrors.identifier → chữ đỏ dưới ô. --%>
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

                        <%-- Ô 2: mật khẩu + nút con mắt (common.js đổi type password ↔ text). Lỗi: fieldErrors.password. --%>
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

                        <%-- Nút cam to "Đăng nhập" → POST /login. --%>
                        <button class="btn btn-primary btn-block" type="submit" id="loginSubmitBtn">
                            Đăng nhập
                        </button>
                    </form>

                    <p class="auth-switch">
                        Chưa có tài khoản?
                        <a href="${pageContext.request.contextPath}/register">Đăng ký</a>
                    </p>
                </div>
                <%-- BẢNG 1. FORM ĐĂNG NHẬP — KẾT THÚC --%>

                <%-- ==========================================================
                     BẢNG 2. TÀI KHOẢN CHƯA CÓ MẬT KHẨU — BẮT ĐẦU   (loginStep = "blocked-no-password", MSG38)
                     Tài khoản do lễ tân tạo tại quầy (Accounts.PasswordHash = NULL) → khung vàng + nút "Đặt mật khẩu"
                     dẫn sang Quên mật khẩu (đặt mật khẩu lần đầu qua email).
                     ========================================================== --%>
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
                        <%-- "Thử lại": login.js chuyển về bảng 1, không gọi server. --%>
                        <button class="btn btn-outline" type="button" data-back-form>Thử lại</button>
                    </div>
                </div>
                <%-- BẢNG 2 — KẾT THÚC --%>

                <%-- ==========================================================
                     BẢNG 3. TÀI KHOẢN TẠM KHÓA — BẮT ĐẦU   (loginStep = "blocked-lock", MSG35)
                     Sai mật khẩu 5 lần (P-17) → Accounts.LockedUntil = bây giờ + 5 phút (P-18).
                     ========================================================== --%>
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

                    <%-- Đồng hồ "Có thể thử lại sau: 04:59" tới khi tài khoản mở khóa (GBR-27).
                         lockSecondsLeft do LoginServlet tính (parse từ MSG35); common.js đếm lùi, về 0 thì tải lại trang. --%>
                    <c:if test="${not empty lockSecondsLeft and lockSecondsLeft > 0}">
                        <div class="lock-countdown">
                            <i class="fa-regular fa-clock"></i>
                            <span>Có thể thử lại sau:</span>
                            <b class="tabular countdown-badge"
                               data-countdown-seconds="${lockSecondsLeft}"
                               data-countdown-reload="true">00:00</b>
                        </div>
                    </c:if>

                    <%-- Đang đếm ngược thì nút "Quay lại" bị khóa; hết giờ thì bấm được (về bảng 1). --%>
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
                <%-- BẢNG 3 — KẾT THÚC --%>

            </div>
            <p class="auth-foot-note">© 2026 Super Gym</p>
        </div>
        <%-- ##### KHUNG TRANG — KẾT THÚC ##### --%>
    </body>
</html>
