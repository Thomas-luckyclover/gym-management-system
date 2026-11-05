<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="profile" value="${detail.profile}" />
<!DOCTYPE html>
<html lang="vi">
<head>
    <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
    <title>${editMode ? 'Sửa' : 'Tạo'} hồ sơ hội viên · Super Gym</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/receptionist/customer-list.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/receptionist/customer-profile-editor.css">
    <script src="${pageContext.request.contextPath}/js/pages/receptionist/customer-profile-editor.js" defer></script>
</head>
<body class="dock theme-staff">
<%@ include file="/WEB-INF/jsp/common/flash-toast.jspf" %>
<div class="app">
    <%@ include file="/WEB-INF/jsp/common/layout-receptionist.jspf" %>
    <div class="main">
        <header class="topbar">
            <h1 class="tb-title">Hồ sơ hội viên</h1>
            <div class="top-right">
                <%@ include file="/WEB-INF/jsp/common/notification-bell.jspf" %>
                <%@ include file="/WEB-INF/jsp/common/topbar-user.jspf" %>
            </div>
        </header>
        <main class="content bf07-page profile-editor" id="main-content" tabindex="-1">
            <nav class="bf07-breadcrumb" aria-label="Đường dẫn"><a href="${pageContext.request.contextPath}/receptionist/customers">← Danh sách hội viên</a></nav>
            <section class="bf07-panel editor-card">
                <div class="editor-heading">
                    <div><h2>${editMode ? 'Sửa hồ sơ hội viên' : 'Thêm hội viên'}</h2><p class="bf07-muted">Thông tin hội viên đăng ký tại quầy.</p></div>
                    <span class="bf07-muted">* Thông tin bắt buộc</span>
                </div>
                <c:if test="${not empty saveError}"><p class="bf07-notice bf07-notice-danger" role="alert"><c:out value="${saveError}" /></p></c:if>
                <form id="customerProfileEditor" method="post" enctype="multipart/form-data" action="${pageContext.request.contextPath}/receptionist/customers/form">
                    <input type="hidden" name="action" value="save">
                    <c:if test="${editMode}"><input type="hidden" name="customerType" value="<c:out value='${submitted ? param.customerType : profile.customerType}'/>"></c:if>
                    <input type="hidden" name="csrfToken" value="<c:out value='${sessionScope.csrfToken}'/>">
                    <input type="hidden" name="customerId" value="<c:out value='${submitted ? param.customerId : profile.customerId}'/>">
                    <fieldset>
                        <legend>Thông tin hồ sơ</legend>
                        <div class="editor-grid">
                            <div class="editor-field"><label for="customerType">Loại hồ sơ *</label>
                                <select id="customerType" name="customerType" ${editMode ? 'disabled' : ''}>
                                    <option value="Self" ${(submitted ? param.customerType : profile.customerType) == 'Dependent' ? '' : 'selected'}>Cá nhân (Self)</option>
                                    <option value="Dependent" ${(submitted ? param.customerType : profile.customerType) == 'Dependent' ? 'selected' : ''}>Người phụ thuộc (Dependent)</option>
                                </select>
                                <small class="bf07-muted">${editMode ? 'Không thay đổi loại hồ sơ sau khi tạo.' : 'Chọn Người phụ thuộc để nhập thông tin giám hộ.'}</small>
                            </div>
                            <c:if test="${editMode}"><div class="editor-field"><label for="customerCode">Mã hội viên</label><input id="customerCode" value="<c:out value='${profile.customerCode}'/>" readonly></div></c:if>
                            <div class="editor-field"><label for="fullName">Họ và tên *</label><input id="fullName" name="fullName" value="<c:out value='${submitted ? param.fullName : profile.fullName}'/>" maxlength="100" autocomplete="name" required></div>
                            <div class="editor-field"><label for="dateOfBirth">Ngày sinh *</label><input type="date" id="dateOfBirth" name="dateOfBirth" value="<c:out value='${submitted ? param.dateOfBirth : person.dateOfBirth}'/>" required></div>
                        </div>
                    </fieldset>
                    <fieldset id="contactSection">
                        <legend>Thông tin liên hệ</legend>
                        <div class="editor-grid">
                            <div class="editor-field"><label for="phone">Số điện thoại *</label><input type="tel" id="phone" name="phone" value="<c:out value='${submitted ? param.phone : profile.phone}'/>" autocomplete="tel" maxlength="15" required></div>
                            <div class="editor-field"><label for="email">Email *</label><input type="email" id="email" name="email" value="<c:out value='${submitted ? param.email : profile.email}'/>" autocomplete="email" maxlength="254" required></div>
                        </div>
                        <c:if test="${not editMode}"><p class="bf07-muted">Hồ sơ cá nhân mới sẽ dùng email để nhận thông tin tài khoản sau khi tạo thành công.</p></c:if>
                    </fieldset>
                    <c:if test="${editMode}">
                        <div id="identityVerification" class="bf07-notice">
                            <label><input type="checkbox" name="identityVerified" value="true" ${param.identityVerified == 'true' ? 'checked' : ''}> Đã xác minh khách tại quầy bằng ảnh check-in hoặc giấy tờ.</label>
                            <p>Chỉ cần xác nhận khi đổi điện thoại/email. Nếu chưa có ảnh check-in, hãy bổ sung ảnh bên dưới. Liên hệ dùng chung cho các vai trò của khách; đổi email sẽ đăng xuất tài khoản khách.</p>
                        </div>
                    </c:if>
                    <fieldset id="guardianSection" hidden disabled>
                        <legend>Thông tin người giám hộ</legend>
                        <div class="editor-grid">
                            <div class="editor-field"><label for="guardianName">Họ tên người giám hộ *</label><input id="guardianName" name="guardianName" value="<c:out value='${submitted ? param.guardianName : customer.guardianName}'/>" maxlength="50" required></div>
                            <div class="editor-field"><label for="guardianRelationship">Quan hệ với hội viên *</label><input id="guardianRelationship" name="guardianRelationship" value="<c:out value='${submitted ? param.guardianRelationship : customer.guardianRelationship}'/>" placeholder="Ví dụ: Bố, Mẹ" maxlength="30" required></div>
                            <div class="editor-field"><label for="guardianPhone">Số điện thoại chính *</label><input type="tel" id="guardianPhone" name="guardianPhone" value="<c:out value='${submitted ? param.guardianPhone : customer.guardianPhone}'/>" maxlength="15" required></div>
                            <div class="editor-field"><label for="guardianPhone2">Số điện thoại phụ</label><input type="tel" id="guardianPhone2" name="guardianPhone2" value="<c:out value='${submitted ? param.guardianPhone2 : customer.guardianPhone2}'/>" maxlength="15"></div>
                        </div>
                        <p class="bf07-muted">Hồ sơ người phụ thuộc không tạo tài khoản đăng nhập riêng.</p>
                    </fieldset>
                    <fieldset>
                        <legend>Ảnh check-in</legend>
                        <div class="editor-photo">
                            <c:if test="${not empty customer.checkInPhoto}"><img src="${pageContext.request.contextPath}<c:out value='${customer.checkInPhoto}'/>" alt="Ảnh check-in hiện tại"></c:if>
                            <img id="photoPreview" alt="Ảnh check-in đã chọn" hidden>
                            <div><label for="checkInPhoto">Chọn ảnh rõ khuôn mặt hội viên</label><p class="bf07-muted">Ảnh JPEG hoặc PNG, tối đa 5 MB.</p><input type="file" id="checkInPhoto" name="checkInPhoto" accept="image/jpeg,image/png"></div>
                        </div>
                        <p id="photoError" class="bf07-error" role="alert"></p>
                    </fieldset>
                    <div class="editor-footer">
                        <span class="bf07-muted">${editMode ? 'Mã, loại hồ sơ và trạng thái được giữ nguyên.' : 'Mã hội viên sẽ được tạo tự động khi lưu.'}</span>
                        <div class="bf07-row-actions"><a class="btn btn-outline" href="${pageContext.request.contextPath}/receptionist/customers">Hủy</a><button class="btn btn-primary" type="submit">${editMode ? 'Lưu thay đổi' : 'Thêm hội viên'}</button></div>
                    </div>
                    <p id="editorMessage" class="bf07-notice" role="status" hidden></p>
                </form>
            </section>
        </main>
    </div>
</div>
</body>
</html>
