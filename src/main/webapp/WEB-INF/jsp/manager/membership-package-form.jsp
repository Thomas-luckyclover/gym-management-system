<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>

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
          <div class="breadcrumb-trail mb-1">
            <a href="${pageContext.request.contextPath}/manager/membership-packages" class="text-muted">Danh mục gói</a>
            <span class="text-muted">/</span>
            <span class="text-muted">${isEdit ? 'Chỉnh sửa' : 'Thêm mới'}</span>
          </div>
          <h1 class="tb-title">${isEdit ? 'Chỉnh sửa gói hội viên' : 'Thêm mới gói hội viên'}</h1>
          <span class="tb-subtitle">Thiết lập thông số giá bán, thời hạn theo chuẩn P-04 và quyền lợi gói (#18 - UC-10).</span>
        </div>

        <div class="top-right">
          <%@ include file="/WEB-INF/jsp/common/notification-bell.jspf" %>
          <%@ include file="/WEB-INF/jsp/common/topbar-user.jspf" %>
        </div>
      </header>

      <main class="content" id="main-content" tabindex="-1">

        <c:if test="${not empty errorMessage}">
          <div class="alert alert-danger mb-4" role="alert">
            <svg width="20" height="20" viewBox="0 0 20 20" fill="currentColor" class="mr-2">
              <path fill-rule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7 4a1 1 0 11-2 0 1 1 0 012 0zm-1-9a1 1 0 00-1 1v4a1 1 0 102 0V6a1 1 0 00-1-1z" clip-rule="evenodd"/>
            </svg>
            <span><c:out value="${errorMessage}" /></span>
          </div>
        </c:if>

        <div class="package-form-grid">
          
          <%-- CỘT TRÁI: BIỂU MẪU NHẬP LIỆU --%>
          <section class="package-form-card">
            <form method="post" action="${pageContext.request.contextPath}/manager/membership-packages/form">
              <input type="hidden" name="csrfToken" value="${csrfToken}">
              <input type="hidden" name="packageId" value="${form.packageId}">
              <input type="hidden" name="rowVersion" value="${form.rowVersion}">

              <h2 class="form-section-title">Thông tin cơ bản</h2>

              <div class="form-group mb-3">
                <label for="packageNameInput" class="form-label required">Tên gói dịch vụ:</label>
                <input type="text" id="packageNameInput" name="name" class="form-control ${not empty fieldErrors['name'] ? 'is-invalid' : ''}"
                       placeholder="Ví dụ: Gói Hội Viên Tiêu Chuẩn 3 Tháng"
                       maxlength="50"
                       required
                       value="<c:out value='${form.name}' />">
                <c:choose>
                  <c:when test="${not empty fieldErrors['name']}">
                    <div class="invalid-feedback d-block">${fieldErrors['name']}</div>
                  </c:when>
                  <c:otherwise>
                    <small class="form-text text-muted">Từ 3 đến 50 ký tự, không được trùng với gói khác đang mở bán (DBR-06).</small>
                  </c:otherwise>
                </c:choose>
              </div>

              <div class="form-row-2col mb-3">
                <div class="form-group">
                  <label for="packageTypeSelect" class="form-label required">Phân loại gói:</label>
                  <select id="packageTypeSelect" name="type" class="form-select ${not empty fieldErrors['type'] ? 'is-invalid' : ''}">
                    <option value="Membership" <c:if test="${form.type == 'Membership'}">selected</c:if>>Gói hội viên (Theo tháng)</option>
                    <option value="DayPass" <c:if test="${form.type == 'DayPass'}">selected</c:if>>Vé ngày (Day Pass)</option>
                  </select>
                  <c:if test="${not empty fieldErrors['type']}">
                    <div class="invalid-feedback d-block">${fieldErrors['type']}</div>
                  </c:if>
                </div>

                <div class="form-group">
                  <label for="packagePriceInput" class="form-label required">Giá niêm yết (VNĐ):</label>
                  <input type="text" id="packagePriceInput" name="price" class="form-control ${not empty fieldErrors['price'] ? 'is-invalid' : ''}"
                         placeholder="Ví dụ: 1.500.000"
                         required
                         value="<c:out value='${form.price}' />">
                  <c:choose>
                    <c:when test="${not empty fieldErrors['price']}">
                      <div class="invalid-feedback d-block">${fieldErrors['price']}</div>
                    </c:when>
                    <c:otherwise>
                      <small class="form-text text-muted">Từ 10.000 ₫ đến 500.000.000 ₫, bội số của 1.000 ₫ (DBR-11).</small>
                    </c:otherwise>
                  </c:choose>
                </div>
              </div>

              <div class="form-row-2col mb-3">
                <div class="form-group">
                  <label for="packageDurationSelect" class="form-label required">Thời hạn gói:</label>
                  <select id="packageDurationSelect" name="durationValue" class="form-select ${not empty fieldErrors['durationValue'] ? 'is-invalid' : ''}">
                    <option value="1" <c:if test="${form.durationValue == '1'}">selected</c:if>>1 tháng / 1 ngày</option>
                    <option value="3" <c:if test="${form.durationValue == '3'}">selected</c:if>>3 tháng</option>
                    <option value="6" <c:if test="${form.durationValue == '6'}">selected</c:if>>6 tháng</option>
                    <option value="12" <c:if test="${form.durationValue == '12'}">selected</c:if>>12 tháng (1 năm)</option>
                    <option value="24" <c:if test="${form.durationValue == '24'}">selected</c:if>>24 tháng (2 năm)</option>
                    <option value="36" <c:if test="${form.durationValue == '36'}">selected</c:if>>36 tháng (3 năm)</option>
                  </select>
                  <c:if test="${not empty fieldErrors['durationValue']}">
                    <div class="invalid-feedback d-block">${fieldErrors['durationValue']}</div>
                  </c:if>
                </div>

                <div class="form-group">
                  <label for="packageDurationUnitSelect" class="form-label required">Đơn vị thời hạn:</label>
                  <select id="packageDurationUnitSelect" name="durationUnit" class="form-select ${not empty fieldErrors['durationUnit'] ? 'is-invalid' : ''}">
                    <option value="Month" <c:if test="${form.durationUnit == 'Month'}">selected</c:if>>Tháng (Gói hội viên)</option>
                    <option value="Day" <c:if test="${form.durationUnit == 'Day'}">selected</c:if>>Ngày (Vé ngày)</option>
                  </select>
                  <c:if test="${not empty fieldErrors['durationUnit']}">
                    <div class="invalid-feedback d-block">${fieldErrors['durationUnit']}</div>
                  </c:if>
                </div>
              </div>

              <h2 class="form-section-title mt-4">Nội dung &amp; Quyền lợi</h2>

              <div class="form-group mb-3">
                <label for="packageDescInput" class="form-label">Mô tả ngắn gọn:</label>
                <input type="text" id="packageDescInput" name="description" class="form-control"
                       placeholder="Ví dụ: Phù hợp cho người mới bắt đầu tập luyện thể hình"
                       maxlength="255"
                       value="<c:out value='${form.description}' />">
                <small class="form-text text-muted">Tối đa 255 ký tự.</small>
              </div>

              <div class="form-group mb-3">
                <label for="packageBenefitsInput" class="form-label">Danh sách quyền lợi đi kèm:</label>
                <textarea id="packageBenefitsInput" name="benefits" class="form-control ${not empty fieldErrors['benefits'] ? 'is-invalid' : ''}"
                          rows="5"
                          placeholder="Mỗi dòng là một quyền lợi, tối đa 10 dòng:&#10;Tập luyện không giới hạn khung giờ&#10;Miễn phí tủ đồ cá nhân thông minh&#10;Sử dụng phòng tắm nước nóng &amp; xông hơi"><c:out value="${form.benefits}" /></textarea>
                <c:choose>
                  <c:when test="${not empty fieldErrors['benefits']}">
                    <div class="invalid-feedback d-block">${fieldErrors['benefits']}</div>
                  </c:when>
                  <c:otherwise>
                    <small class="form-text text-muted">Tối đa 10 dòng, mỗi dòng từ 3 đến 100 ký tự (DBR-15).</small>
                  </c:otherwise>
                </c:choose>
              </div>

              <div class="form-check mb-3">
                <input type="checkbox" id="unlocksLibraryCheckbox" name="unlocksExerciseLibrary" class="form-check-input"
                       value="true" <c:if test="${form.unlocksExerciseLibrary}">checked</c:if>>
                <label for="unlocksLibraryCheckbox" class="form-check-label font-weight-bold">
                  Cho phép hội viên xem Thư viện bài tập video chuẩn
                </label>
                <small class="form-text text-muted d-block">
                  Hội viên mua gói này sẽ được cấp quyền truy cập toàn bộ kho bài tập và video hướng dẫn.
                </small>
              </div>

              <div class="form-check mb-4">
                <input type="checkbox" id="packageActiveCheckbox" name="active" class="form-check-input"
                       value="true" <c:if test="${form.active}">checked</c:if>>
                <label for="packageActiveCheckbox" class="form-check-label font-weight-bold">
                  Mở bán gói ngay trên hệ thống
                </label>
              </div>

              <div class="form-actions d-flex gap-2">
                <button type="submit" class="btn btn-primary">
                  ${isEdit ? 'Lưu thay đổi' : 'Tạo gói hội viên'}
                </button>
                <a href="${pageContext.request.contextPath}/manager/membership-packages" class="btn btn-outline">
                  Hủy bỏ
                </a>
              </div>
            </form>
          </section>

          <%-- CỘT PHẢI: XEM TRƯỚC (LIVE PREVIEW) & CẢNH BÁO HỢP ĐỒNG ĐANG CHẠY --%>
          <aside class="preview-sticky-wrap">
            <c:if test="${isEdit and runningOrdersCount > 0}">
              <div class="running-alert-box">
                <svg width="22" height="22" viewBox="0 0 20 20" fill="currentColor" class="flex-shrink-0">
                  <path fill-rule="evenodd" d="M8.257 3.099c.765-1.36 2.722-1.36 3.486 0l5.58 9.92c.75 1.334-.213 2.98-1.742 2.98H4.42c-1.53 0-2.493-1.646-1.743-2.98l5.58-9.92zM11 13a1 1 0 11-2 0 1 1 0 012 0zm-1-8a1 1 0 00-1 1v3a1 1 0 002 0V6a1 1 0 00-1-1z" clip-rule="evenodd"/>
                </svg>
                <div>
                  <strong>Cảnh báo đang có hợp đồng chạy:</strong> Hiện có <strong>${runningOrdersCount}</strong> hội viên đang sử dụng gói này. Nếu bạn đổi giá bán, hợp đồng hiện tại của hội viên không bị ảnh hưởng nhưng giá mới sẽ áp dụng ngay cho các lượt đăng ký tiếp theo.
                </div>
              </div>
            </c:if>

            <div class="preview-header">
              <span class="text-xs text-uppercase font-weight-bold text-muted">Xem trước thẻ gói tập</span>
            </div>

            <div class="package-preview-card preview-popular">
              <span class="preview-badge-pill">Gói phổ biến</span>
              <h3 class="preview-title" id="previewTitle"><c:out value="${form.name}" /></h3>
              <div class="preview-price" id="previewPrice">0 ₫ <span>/ gói</span></div>

              <div class="preview-specs" id="previewDuration">
                Thời hạn: 1 Tháng
              </div>

              <ul class="preview-benefit-list" id="previewBenefitsList">
                <%-- Được cập nhật trực tiếp qua package-catalogue.js --%>
              </ul>
            </div>
          </aside>

        </div>

      </main>
    </div>
  </div>

  <script src="${pageContext.request.contextPath}/js/pages/manager/package-catalogue.js?v=20261031v1"></script>
</body>
</html>
