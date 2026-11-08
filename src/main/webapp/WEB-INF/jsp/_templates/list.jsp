<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="gms" uri="/WEB-INF/gms-functions.tld" %>
<%--
  TEMPLATE · MÀN DANH SÁCH (lọc + bảng + phân trang). Sao chép sang WEB-INF/jsp/<vai-trò>/<màn>.jsp rồi:
  1) đổi theme-*, layout-*.jspf, activeNav, tiêu đề;  2) đổi cột bảng theo DTO;  3) xóa comment hướng dẫn.
  Servlet đặt: items (List), page (PageResult), q, status, listError (khi Service lỗi).
  Trạng thái: lỗi (listError) ≠ rỗng (empty items) — không gộp. Khung CSS: common.css mục 15. Dữ liệu người dùng luôn c:out.
--%>
<!DOCTYPE html>
<html lang="vi">
<head>
  <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
  <title>Danh sách · Super Gym</title>
</head>
<body class="dock theme-manager">
<%@ include file="/WEB-INF/jsp/common/flash-toast.jspf" %>
<div class="app">
  <%@ include file="/WEB-INF/jsp/common/layout-manager.jspf" %>
  <div class="main">
    <header class="topbar">
      <div class="tb-left"><h1 class="tb-title">Danh sách</h1></div>
      <div class="top-right"><a class="btn btn-primary" href="#">Thêm mới</a></div>
    </header>

    <main class="content gms-page">
      <%-- ===== Bộ lọc (GET để giữ trên URL, F5 không mất) ===== --%>
      <form class="gms-toolbar" method="get" data-reveal>
        <label class="field">
          <span class="field-label">Tìm kiếm</span>
          <input name="q" type="search" value="<c:out value='${q}' />" placeholder="Tên, mã, số điện thoại…">
        </label>
        <label class="field">
          <span class="field-label">Trạng thái</span>
          <select name="status">
            <option value="">Tất cả</option>
            <option value="Active" ${status eq 'Active' ? 'selected' : ''}>Đang hoạt động</option>
            <option value="Inactive" ${status eq 'Inactive' ? 'selected' : ''}>Ngừng</option>
          </select>
        </label>
        <button class="btn btn-secondary" type="submit">Lọc</button>
      </form>

      <section class="gms-panel" data-reveal>
        <c:choose>
          <c:when test="${not empty listError}">
            <p class="empty-state" role="alert"><c:out value="${listError}" /></p>
          </c:when>
          <c:when test="${empty items}">
            <p class="empty-state">Chưa có dữ liệu phù hợp với bộ lọc.</p>
          </c:when>
          <c:otherwise>
            <div class="gms-table-wrap">
              <table class="gms-table">
                <thead><tr><th>Mã</th><th>Tên</th><th>Trạng thái</th><th class="num">Số tiền</th><th class="actions">Thao tác</th></tr></thead>
                <tbody>
                  <c:forEach var="item" items="${items}">
                    <tr>
                      <td><c:out value="${item.code}" /></td>
                      <td><c:out value="${item.name}" /></td>
                      <td><span class="badge badge-success"><c:out value="${item.statusLabel}" /></span></td>
                      <td class="num">${gms:formatMoney(item.amount)}</td>
                      <td class="actions"><a class="btn btn-secondary btn-sm" href="#">Xem</a></td>
                    </tr>
                  </c:forEach>
                </tbody>
              </table>
            </div>
            <div class="gms-pagination">
              <span>Trang ${page.currentPage} / ${page.totalPages}</span>
              <%-- Dùng phân trang của common.js hoặc link ?page= theo page.currentPage --%>
            </div>
          </c:otherwise>
        </c:choose>
      </section>
    </main>
  </div>
</div>
</body>
</html>
