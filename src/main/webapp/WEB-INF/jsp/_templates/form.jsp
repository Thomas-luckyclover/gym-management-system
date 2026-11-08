<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="gms" uri="/WEB-INF/gms-functions.tld" %>
<%--
  TEMPLATE · MÀN FORM (thêm/sửa). POST về Servlet; lỗi trả lại cùng form + giữ giá trị đã nhập.
  Servlet đặt: form (giá trị), errors (Map tên ô → câu lỗi), formError (lỗi chung).
  data-validate: common.js kiểm tra trước khi gửi · data-unsaved-warning: cảnh báo rời trang khi chưa lưu.
  Nút submit tự hiện Pumpy đẩy tạ khi đang gửi (setButtonLoading).
--%>
<!DOCTYPE html>
<html lang="vi">
<head>
  <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
  <title>Biểu mẫu · Super Gym</title>
</head>
<body class="dock theme-manager">
<%@ include file="/WEB-INF/jsp/common/flash-toast.jspf" %>
<div class="app">
  <%@ include file="/WEB-INF/jsp/common/layout-manager.jspf" %>
  <div class="main">
    <header class="topbar">
      <div class="tb-left"><h1 class="tb-title">Thêm mới</h1></div>
    </header>

    <main class="content gms-page">
      <section class="gms-panel" data-reveal>
        <div class="gms-panel-body">
          <c:if test="${not empty formError}">
            <div class="gms-alert" role="alert"><c:out value="${formError}" /></div>
          </c:if>
          <form class="gms-form" method="post" data-validate data-unsaved-warning novalidate>
            <label class="field">
              <span class="field-label">Tên *</span>
              <input id="name" name="name" required maxlength="100" data-label="Tên" value="<c:out value='${form.name}' />">
              <span class="field-error" id="nameError" ${empty errors.name ? 'hidden' : ''}><c:out value="${errors.name}" /></span>
            </label>
            <label class="field">
              <span class="field-label">Số tiền *</span>
              <input id="amount" name="amount" required inputmode="numeric" data-label="Số tiền" value="<c:out value='${form.amount}' />">
              <span class="field-error" id="amountError" ${empty errors.amount ? 'hidden' : ''}><c:out value="${errors.amount}" /></span>
            </label>
            <label class="field is-full">
              <span class="field-label">Ghi chú</span>
              <textarea id="note" name="note" maxlength="500"><c:out value="${form.note}" /></textarea>
            </label>
            <div class="gms-form-actions">
              <a class="btn btn-secondary" href="javascript:history.back()">Hủy</a>
              <button class="btn btn-primary" type="submit">Lưu</button>
            </div>
          </form>
        </div>
      </section>
    </main>
  </div>
</div>
</body>
</html>
