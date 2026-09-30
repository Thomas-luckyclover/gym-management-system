<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>

<!DOCTYPE html>
<html lang="vi">

<head>

    <%@ include file="/WEB-INF/jsp/common/head.jspf" %>

    <title>Cài đặt tài khoản - GMS</title>

    <link
        rel="stylesheet"
        href="${pageContext.request.contextPath}/css/pages/auth/account-settings.css"
    >

</head>

<body class="dock ${themeClass}">

<%@ include file="/WEB-INF/jsp/common/flash-toast.jspf" %>

<div class="app">

    <%-- =========================================================
         Layout theo role

         Hiện tại branch của Vũ mới có layout-admin.jspf.
         Khi các layout còn lại được merge thì thêm static include
         vào từng c:when tương ứng.
         ========================================================= --%>

    <c:choose>

        <c:when test="${currentRole.dbValue eq 'Admin'}">

            <%@ include file="/WEB-INF/jsp/common/layout-admin.jspf" %>

        </c:when>

        <c:when test="${currentRole.dbValue eq 'Manager'}">

            <%@ include file="/WEB-INF/jsp/common/layout-manager.jspf" %>

        </c:when>

        <c:when test="${currentRole.dbValue eq 'PT'}">

            <c:set var="activeNav" value="account" />
            <%@ include file="/WEB-INF/jsp/common/layout-pt.jspf" %>

        </c:when>

        <c:otherwise>

            <%-- Layout role khác đang chờ merge từ thành viên phụ trách. --%>

        </c:otherwise>

    </c:choose>


    <div class="main">

        <header class="topbar">

            <div class="tb-left">

                <h1 class="tb-title">
                    Cài đặt tài khoản
                </h1>

            </div>


            <c:if test="${not empty accountSettings}">

                <div class="top-right">

                    <a
                        class="tb-user"
                        href="${pageContext.request.contextPath}/account"
                        title="Cài đặt tài khoản"
                    >

                        <c:choose>

                            <c:when test="${not empty accountSettings.avatar}">

                                <img
                                    class="avatar"
                                    src="${pageContext.request.contextPath}${accountSettings.avatar}"
                                    alt="Ảnh đại diện"
                                >

                            </c:when>

                            <c:otherwise>

                                <span class="avatar">
                                    <c:out
                                        value="${accountSettings.fullName.substring(0, 1)}"
                                    />
                                </span>

                            </c:otherwise>

                        </c:choose>

                        <span class="tb-user-text">

                            <b>

                                <c:out
                                    value="${accountSettings.fullName}"
                                />

                            </b>

                            <small>

                                <c:out
                                    value="${currentRole.displayName}"
                                />

                            </small>

                        </span>

                    </a>

                </div>

            </c:if>

        </header>


        <main class="content">

            <div class="page-intro">

                <p>
                    <c:choose>
                        <c:when test="${currentRole.dbValue eq 'Manager'}">
                            Hồ sơ đăng nhập của bạn. Đổi email hoặc SĐT phải xác thực bằng mã gửi tới email mới.
                        </c:when>
                        <c:otherwise>
                            Hồ sơ và mật khẩu của chính bạn.
                        </c:otherwise>
                    </c:choose>
                </p>

            </div>


            <%-- Lỗi khi không tải được Account Settings --%>

            <c:if test="${not empty pageError}">

                <div class="account-form-message">

                    <c:out value="${pageError}" />

                </div>

            </c:if>


            <c:if test="${empty pageError}">

                <%-- Manager: 2 cột Hồ sơ + Mật khẩu như prototype, không dùng tab --%>
                <c:if test="${currentRole.dbValue eq 'Manager'}">
                    <%@ include file="/WEB-INF/jsp/auth/account/profile-manager.jspf" %>
                </c:if>

                <c:if test="${currentRole.dbValue ne 'Manager'}">

                <%-- ================= TAB ================= --%>

                <nav
                    class="account-tabs"
                    aria-label="Cài đặt tài khoản"
                >

                    <a
                        href="${pageContext.request.contextPath}/account?tab=profile"
                        class="btn ${activeTab eq 'profile' ? 'btn-primary' : ''}"
                        aria-current="${activeTab eq 'profile' ? 'page' : 'false'}"
                    >
                        Hồ sơ
                    </a>


                    <a
                        href="${pageContext.request.contextPath}/account?tab=password"
                        class="btn ${activeTab eq 'password' ? 'btn-primary' : ''}"
                        aria-current="${activeTab eq 'password' ? 'page' : 'false'}"
                    >
                        Mật khẩu
                    </a>


                    <%-- Chỉ Customer có tab Hồ sơ phụ thuộc --%>

                    <c:if test="${hasDependentsTab}">

                        <a
                            href="${pageContext.request.contextPath}/account?tab=dependents"
                            class="btn ${activeTab eq 'dependents' ? 'btn-primary' : ''}"
                            aria-current="${activeTab eq 'dependents' ? 'page' : 'false'}"
                        >
                            Hồ sơ phụ thuộc
                        </a>

                    </c:if>

                </nav>


                <%-- ================= NỘI DUNG TAB ================= --%>

                <div class="account-tab-content">

                    <c:choose>


                        <%-- ===== PROFILE ===== --%>

                        <c:when test="${activeTab eq 'profile'}">

                            <c:choose>

                                <%-- Admin: phần Vũ phụ trách --%>

                                <c:when test="${currentRole.dbValue eq 'Admin'}">

                                    <%@ include file="/WEB-INF/jsp/auth/account/profile-admin.jspf" %>

                                </c:when>


                                <%-- Các role khác chờ merge profile JSPF --%>

                                <c:otherwise>

                                    <section class="account-card">

                                        <div class="account-card-body">

                                            <h2>
                                                Hồ sơ
                                            </h2>

                                            <p class="account-subtitle">
                                                Hồ sơ của vai trò này đang chờ tích hợp.
                                            </p>

                                        </div>

                                    </section>

                                </c:otherwise>

                            </c:choose>

                        </c:when>


                        <%-- ===== PASSWORD: dùng chung 5 role ===== --%>

                        <c:when test="${activeTab eq 'password'}">

                            <%@ include file="/WEB-INF/jsp/auth/account/tab-password.jspf" %>

                        </c:when>


                        <%-- ===== DEPENDENTS: chỉ Customer ===== --%>

                        <c:when test="${activeTab eq 'dependents' and hasDependentsTab}">

                            <section class="account-card">

                                <div class="account-card-body">

                                    <h2>
                                        Hồ sơ phụ thuộc
                                    </h2>

                                    <p class="account-subtitle">
                                        Nội dung hồ sơ phụ thuộc đang chờ tích hợp từ Customer Account Settings.
                                    </p>

                                </div>

                            </section>

                        </c:when>


                    </c:choose>

                </div>

                </c:if>

            </c:if>

        </main>

    </div>

</div>


<script
    src="${pageContext.request.contextPath}/js/pages/auth/account-settings.js?v=2"
    defer>
</script>

</body>

</html>