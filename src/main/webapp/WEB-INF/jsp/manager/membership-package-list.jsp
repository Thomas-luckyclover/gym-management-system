<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="gms" uri="/WEB-INF/gms-functions.tld" %>

<!DOCTYPE html>
<html lang="vi">
<head>
  <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
  <title>${pageTitle}</title>
  <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/manager/package-catalogue.css?v=20261031v1">
</head>

<body class="dock theme-manager">
  <%@ include file="/WEB-INF/jsp/common/flash-toast.jspf" %>

  <div class="app">
    <%@ include file="/WEB-INF/jsp/common/layout-manager.jspf" %>

    <div class="main">
      <header class="topbar">
        <div class="tb-left">
          <h1 class="tb-title">Danh mục gói dịch vụ</h1>
          <span class="tb-subtitle">Quản lý bảng giá, thời hạn hiệu lực và trạng thái kinh doanh của gói hội viên và gói PT (#17 - UC-10).</span>
        </div>

        <div class="top-right">
          <%@ include file="/WEB-INF/jsp/common/notification-bell.jspf" %>
          <%@ include file="/WEB-INF/jsp/common/topbar-user.jspf" %>
        </div>
      </header>

      <main class="content" id="main-content" tabindex="-1">

        <%-- Tabs chuyển đổi giữa Gói Hội viên & Gói PT --%>
        <nav class="catalogue-nav-tabs" aria-label="Phân loại gói dịch vụ">
          <a href="${pageContext.request.contextPath}/manager/membership-packages" class="catalogue-tab-item active">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"></path>
              <circle cx="9" cy="7" r="4"></circle>
              <path d="M23 21v-2a4 4 0 0 0-3-3.87"></path>
              <path d="M16 3.13a4 4 0 0 1 0 7.75"></path>
            </svg>
            <span>Gói hội viên &amp; Vé ngày</span>
          </a>
          <a href="${pageContext.request.contextPath}/manager/pt-packages" class="catalogue-tab-item">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"></polygon>
            </svg>
            <span>Gói PT &amp; Huấn luyện viên</span>
          </a>
        </nav>

        <%-- Thanh công cụ tìm kiếm và lọc --%>
        <section class="catalogue-filter-bar" aria-label="Bộ lọc danh mục gói">
          <form method="get" action="${pageContext.request.contextPath}/manager/membership-packages" class="catalogue-filter-group">
            <div class="search-input-wrap">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="11" cy="11" r="8"></circle>
                <line x1="21" y1="21" x2="16.65" y2="16.65"></line>
              </svg>
              <input type="text" name="keyword" class="form-control" placeholder="Tìm kiếm theo tên gói..." value="<c:out value='${keyword}' />">
            </div>

            <div class="filter-item">
              <select name="status" class="form-select" onchange="this.form.submit()">
                <option value="all" <c:if test="${status == 'all'}">selected</c:if>>Tất cả trạng thái</option>
                <option value="active" <c:if test="${status == 'active'}">selected</c:if>>Đang mở bán</option>
                <option value="inactive" <c:if test="${status == 'inactive'}">selected</c:if>>Đã ngừng bán</option>
              </select>
            </div>

            <c:if test="${not empty keyword or status != 'all'}">
              <a href="${pageContext.request.contextPath}/manager/membership-packages" class="btn btn-sm btn-ghost" title="Xóa bộ lọc">
                ✕ Xóa lọc
              </a>
            </c:if>
          </form>

          <div class="catalogue-actions">
            <a href="${pageContext.request.contextPath}/manager/membership-packages/form" class="btn btn-primary">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="12" y1="5" x2="12" y2="19"></line>
                <line x1="5" y1="12" x2="19" y2="12"></line>
              </svg>
              <span>+ Thêm gói hội viên</span>
            </a>
          </div>
        </section>

        <%-- Bảng danh sách gói --%>
        <div class="catalogue-table-card">
          <c:choose>
            <c:when test="${empty packages}">
              <div class="empty-state p-5 text-center">
                <p class="text-muted">Không tìm thấy gói hội viên nào phù hợp với điều kiện tìm kiếm.</p>
              </div>
            </c:when>
            <c:otherwise>
              <table class="catalogue-table">
                <thead>
                  <tr>
                    <th>Tên gói dịch vụ</th>
                    <th>Phân loại</th>
                    <th>Thời hạn</th>
                    <th>Giá niêm yết</th>
                    <th>Thư viện</th>
                    <th>Đơn đang chạy</th>
                    <th>Trạng thái</th>
                    <th class="text-right">Thao tác</th>
                  </tr>
                </thead>
                <tbody>
                  <c:forEach var="pkg" items="${packages}">
                    <tr class="${!pkg.active ? 'row-inactive' : ''}">
                      <td>
                        <div class="package-name-cell"><c:out value="${pkg.name}" /></div>
                        <c:if test="${not empty pkg.description}">
                          <div class="package-sub-desc" title="<c:out value='${pkg.description}' />">
                            <c:out value="${pkg.description}" />
                          </div>
                        </c:if>
                      </td>
                      <td>
                        <span class="badge ${pkg.type == 'DayPass' ? 'badge-info' : 'badge-primary'}">
                          <c:out value="${pkg.typeDisplay}" />
                        </span>
                      </td>
                      <td>
                        <strong><c:out value="${pkg.durationDisplay}" /></strong>
                      </td>
                      <td>
                        <span class="package-price-cell"><c:out value="${pkg.formattedPrice}" /></span>
                      </td>
                      <td>
                        <c:choose>
                          <c:when test="${pkg.unlocksExerciseLibrary}">
                            <span class="badge badge-success" title="Được mở khóa xem Thư viện bài tập">Mở khóa</span>
                          </c:when>
                          <c:otherwise>
                            <span class="badge badge-subtle">Không</span>
                          </c:otherwise>
                        </c:choose>
                      </td>
                      <td>
                        <c:choose>
                          <c:when test="${pkg.runningOrdersCount > 0}">
                            <span class="badge-running has-running" title="Có ${pkg.runningOrdersCount} hợp đồng đang hiệu lực">
                              ${pkg.runningOrdersCount} hợp đồng
                            </span>
                          </c:when>
                          <c:otherwise>
                            <span class="badge-running">0 hợp đồng</span>
                          </c:otherwise>
                        </c:choose>
                      </td>
                      <td>
                        <c:choose>
                          <c:when test="${pkg.active}">
                            <span class="badge-status active">● Đang bán</span>
                          </c:when>
                          <c:otherwise>
                            <span class="badge-status inactive">○ Ngừng bán</span>
                          </c:otherwise>
                        </c:choose>
                      </td>
                      <td class="text-right">
                        <div class="table-actions justify-content-end">
                          <a href="${pageContext.request.contextPath}/manager/membership-packages/form?id=${pkg.packageId}" class="btn btn-sm btn-outline" title="Chỉnh sửa thông tin gói">
                            Sửa
                          </a>

                          <c:choose>
                            <c:when test="${pkg.active}">
                              <button type="button" class="btn btn-sm btn-ghost text-danger"
                                      data-toggle-package
                                      data-package-id="${pkg.packageId}"
                                      data-package-name="<c:out value='${pkg.name}' />"
                                      data-target-active="false"
                                      data-row-version="${pkg.rowVersionBase64}"
                                      data-running-count="${pkg.runningOrdersCount}"
                                      title="Ngừng bán gói này">
                                Ngừng bán
                              </button>
                            </c:when>
                            <c:otherwise>
                              <button type="button" class="btn btn-sm btn-ghost text-success"
                                      data-toggle-package
                                      data-package-id="${pkg.packageId}"
                                      data-package-name="<c:out value='${pkg.name}' />"
                                      data-target-active="true"
                                      data-row-version="${pkg.rowVersionBase64}"
                                      data-running-count="${pkg.runningOrdersCount}"
                                      title="Mở bán lại gói này">
                                Mở bán lại
                              </button>
                            </c:otherwise>
                          </c:choose>
                        </div>
                      </td>
                    </tr>
                  </c:forEach>
                </tbody>
              </table>
            </c:otherwise>
          </c:choose>
        </div>

      </main>
    </div>
  </div>

  <%-- Modal xác nhận thay đổi trạng thái bán (Mở bán lại / Ngừng bán) --%>
  <div class="modal fade" id="toggleStatusModal" tabindex="-1" role="dialog" aria-hidden="true" style="display:none;">
    <div class="modal-dialog modal-dialog-centered" role="document">
      <div class="modal-content">
        <form method="post" action="${pageContext.request.contextPath}/manager/membership-packages">
          <input type="hidden" name="csrfToken" value="${csrfToken}">
          <input type="hidden" name="action" value="toggleStatus">
          <input type="hidden" name="packageId" id="togglePackageId">
          <input type="hidden" name="targetActive" id="toggleTargetActive">
          <input type="hidden" name="rowVersion" id="toggleRowVersion">
          <input type="hidden" name="keyword" value="<c:out value='${keyword}' />">
          <input type="hidden" name="status" value="<c:out value='${status}' />">

          <div class="modal-header">
            <h5 class="modal-title" id="toggleModalTitle">Thay đổi trạng thái gói</h5>
            <button type="button" class="close" data-dismiss="modal" aria-label="Đóng" onclick="closeModal('toggleStatusModal')">
              <span aria-hidden="true">&times;</span>
            </button>
          </div>

          <div class="modal-body">
            <p id="toggleModalMessage"></p>

            <div id="toggleWarningRunningOrders" class="running-alert-box mb-3" style="display: none;">
              <svg width="20" height="20" viewBox="0 0 20 20" fill="currentColor">
                <path fill-rule="evenodd" d="M8.257 3.099c.765-1.36 2.722-1.36 3.486 0l5.58 9.92c.75 1.334-.213 2.98-1.742 2.98H4.42c-1.53 0-2.493-1.646-1.743-2.98l5.58-9.92zM11 13a1 1 0 11-2 0 1 1 0 012 0zm-1-8a1 1 0 00-1 1v3a1 1 0 002 0V6a1 1 0 00-1-1z" clip-rule="evenodd"/>
              </svg>
              <div>
                <strong>Lưu ý:</strong> Hiện có <span id="toggleRunningCount">0</span> hội viên đang sử dụng gói này. Các hội viên hiện tại vẫn tiếp tục tập đến ngày hết hạn bình thường.
              </div>
            </div>

            <div class="form-group">
              <label for="toggleReasonInput">Lý do thay đổi (ghi nhận AuditLog):</label>
              <textarea name="reason" id="toggleReasonInput" class="form-control" rows="2" placeholder="Ví dụ: Thay đổi chiến lược sản phẩm, cập nhật giá mới..."></textarea>
            </div>
          </div>

          <div class="modal-footer">
            <button type="button" class="btn btn-outline" data-dismiss="modal" onclick="closeModal('toggleStatusModal')">Hủy</button>
            <button type="submit" class="btn btn-primary" id="toggleSubmitBtn">Xác nhận</button>
          </div>
        </form>
      </div>
    </div>
  </div>

  <script src="${pageContext.request.contextPath}/js/pages/manager/package-catalogue.js?v=20261031v1"></script>
</body>
</html>
