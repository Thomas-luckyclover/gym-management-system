<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="gms" uri="/WEB-INF/gms-functions.tld" %>

<!DOCTYPE html>
<html lang="vi">
<head>
  <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
  <title>Yêu cầu bảo trì · Super Gym Quản lý</title>
  <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/manager/maintenance-requests.css?v=20261026v1">

</head>

<body class="dock theme-manager">
  <%@ include file="/WEB-INF/jsp/common/flash-toast.jspf" %>

  <div class="app">
    <%@ include file="/WEB-INF/jsp/common/layout-manager.jspf" %>

    <div class="main">
      <header class="topbar">
        <div class="tb-left">
          <h1 class="tb-title">Yêu cầu bảo trì</h1>
          <%-- Câu giải thích chuyển vào hint box chung (.pt-info, common.css / common.js) thay cho dòng chữ phụ dưới tiêu đề. --%>
          <span class="pt-info"><button type="button" class="pt-info-trigger" aria-label="Hướng dẫn Yêu cầu bảo trì" aria-describedby="maintenance-help"><span aria-hidden="true">i</span></button><span id="maintenance-help" class="pt-info-popover" role="tooltip">Duyệt hoặc từ chối yêu cầu lễ tân gửi lên; theo dõi đến khi máy sửa xong.</span></span>
        </div>

        <div class="top-right">
          <%@ include file="/WEB-INF/jsp/common/notification-bell.jspf" %>

          <%@ include file="/WEB-INF/jsp/common/topbar-user.jspf" %>
        </div>
      </header>

      <main class="content maintenance-list-content" id="main-content" tabindex="-1">

        <%-- ===== 5 THẺ ĐẾM TRẠNG THÁI ===== --%>
        <div class="maintenance-stats-bar">
          <c:url var="allUrl" value="/manager/maintenance-requests">
            <c:if test="${not empty keyword}"><c:param name="keyword" value="${keyword}" /></c:if>
            <c:if test="${not empty priorityFilter}"><c:param name="priority" value="${priorityFilter}" /></c:if>
            <c:if test="${not empty reportedDateFilter}"><c:param name="reportedDate" value="${reportedDateFilter}" /></c:if>
          </c:url>
          <a href="${allUrl}" class="stat-card ${empty statusFilter ? 'active' : ''}">
            <span class="stat-name">Tất cả</span>
            <strong class="stat-count"><c:out value="${totalStatusCount}" default="0" /></strong>
          </a>

          <%-- Chờ duyệt (Open) --%>
          <c:url var="openUrl" value="/manager/maintenance-requests">
            <c:param name="status" value="Open" />
            <c:if test="${not empty keyword}"><c:param name="keyword" value="${keyword}" /></c:if>
            <c:if test="${not empty priorityFilter}"><c:param name="priority" value="${priorityFilter}" /></c:if>
            <c:if test="${not empty reportedDateFilter}"><c:param name="reportedDate" value="${reportedDateFilter}" /></c:if>
          </c:url>
          <a href="${openUrl}" class="stat-card ${statusFilter == 'Open' ? 'active' : ''}">
            <span class="stat-name"><span class="dot dot-open"></span> Chờ duyệt</span>
            <strong class="stat-count"><c:out value="${statusCounts['Open']}" default="0" /></strong>
          </a>

          <%-- Đang xử lý (InProgress) --%>
          <c:url var="progUrl" value="/manager/maintenance-requests">
            <c:param name="status" value="InProgress" />
            <c:if test="${not empty keyword}"><c:param name="keyword" value="${keyword}" /></c:if>
            <c:if test="${not empty priorityFilter}"><c:param name="priority" value="${priorityFilter}" /></c:if>
            <c:if test="${not empty reportedDateFilter}"><c:param name="reportedDate" value="${reportedDateFilter}" /></c:if>
          </c:url>
          <a href="${progUrl}" class="stat-card ${statusFilter == 'InProgress' ? 'active' : ''}">
            <span class="stat-name"><span class="dot dot-progress"></span> Đang xử lý</span>
            <strong class="stat-count"><c:out value="${statusCounts['InProgress']}" default="0" /></strong>
          </a>

          <%-- Từ chối (Rejected) --%>
          <c:url var="rejUrl" value="/manager/maintenance-requests">
            <c:param name="status" value="Rejected" />
            <c:if test="${not empty keyword}"><c:param name="keyword" value="${keyword}" /></c:if>
            <c:if test="${not empty priorityFilter}"><c:param name="priority" value="${priorityFilter}" /></c:if>
            <c:if test="${not empty reportedDateFilter}"><c:param name="reportedDate" value="${reportedDateFilter}" /></c:if>
          </c:url>
          <a href="${rejUrl}" class="stat-card ${statusFilter == 'Rejected' ? 'active' : ''}">
            <span class="stat-name"><span class="dot dot-rejected"></span> Từ chối</span>
            <strong class="stat-count"><c:out value="${statusCounts['Rejected']}" default="0" /></strong>
          </a>

          <%-- Đã đóng (Closed) --%>
          <c:url var="closeUrl" value="/manager/maintenance-requests">
            <c:param name="status" value="Closed" />
            <c:if test="${not empty keyword}"><c:param name="keyword" value="${keyword}" /></c:if>
            <c:if test="${not empty priorityFilter}"><c:param name="priority" value="${priorityFilter}" /></c:if>
            <c:if test="${not empty reportedDateFilter}"><c:param name="reportedDate" value="${reportedDateFilter}" /></c:if>
          </c:url>
          <a href="${closeUrl}" class="stat-card ${statusFilter == 'Closed' ? 'active' : ''}">
            <span class="stat-name"><span class="dot dot-closed"></span> Đã đóng</span>
            <strong class="stat-count"><c:out value="${statusCounts['Closed']}" default="0" /></strong>
          </a>
        </div>

        <%-- ===== BẢNG DANH SÁCH & BỘ LỌC ===== --%>
        <section class="maintenance-table-card">
          <div class="card-header-row">
            <h2 class="card-heading">Danh sách yêu cầu</h2>
          </div>

          <%-- Bộ lọc tìm kiếm --%>
          <form method="get" action="${pageContext.request.contextPath}/manager/maintenance-requests" class="filter-controls-row">
            <c:if test="${not empty statusFilter}">
              <input type="hidden" name="status" value="${statusFilter}">
            </c:if>

            <div class="filter-field filter-search">
              <label class="filter-label">Tìm yêu cầu</label>
              <input type="search"
                     name="keyword"
                     maxlength="100"
                     value="<c:out value='${keyword}' />"
                     placeholder="Mã yêu cầu, mã máy hoặc tên thiết bị">
            </div>

            <div class="filter-field filter-priority">
              <label class="filter-label">Mức ưu tiên</label>
              <select name="priority">
                <option value="">Tất cả</option>
                <c:forEach var="p" items="${priorityOptions}">
                  <option value="${p.dbValue}" <c:if test="${priorityFilter == p.dbValue}">selected</c:if>>
                    <c:out value="${p.displayName}" />
                  </option>
                </c:forEach>
              </select>
            </div>

            <div class="filter-field filter-date">
              <label class="filter-label">Ngày báo</label>
              <input type="date" name="reportedDate" value="<c:out value='${reportedDateFilter}' />">
            </div>

            <div class="filter-actions-col">
              <button class="btn btn-primary btn-filter-submit" type="submit">Lọc</button>
              <c:if test="${not empty keyword || not empty statusFilter || not empty priorityFilter || not empty reportedDateFilter}">
                <a class="btn btn-ghost" href="${pageContext.request.contextPath}/manager/maintenance-requests">Xóa lọc</a>
              </c:if>
            </div>
          </form>

          <%-- Bảng dữ liệu --%>
          <div class="table-responsive-wrapper">
            <table class="maintenance-data-table">
              <thead>
                <tr>
                  <th style="width: 110px;">MÃ</th>
                  <th style="width: 220px;">THIẾT BỊ · MÁY</th>
                  <th>SỰ CỐ</th>
                  <th style="width: 110px;">ƯU TIÊN</th>
                  <th style="width: 170px;">NGÀY BÁO · NGƯỜI BÁO</th>
                  <th style="width: 140px;">TRẠNG THÁI</th>
                  <th style="width: 130px; text-align: right;">THAO TÁC</th>
                </tr>
              </thead>
              <tbody>
                <c:choose>
                  <c:when test="${not empty requestPage.items}">
                    <c:forEach var="item" items="${requestPage.items}">
                      <tr>
                        <%-- Cột MÃ --%>
                        <td>
                          <strong class="request-code"><c:out value="${item.request.requestCode}" /></strong>
                        </td>

                        <%-- Cột THIẾT BỊ · MÁY --%>
                        <td>
                          <div class="cell-device-info">
                            <strong class="device-name"><c:out value="${item.facilityName}" /></strong>
                            <span class="device-item-code">
                              <c:choose>
                                <c:when test="${not empty item.request.affectedItemsNote}">
                                  <c:out value="${item.request.affectedItemsNote}" /> · <c:out value="${item.itemCode}" />
                                </c:when>
                                <c:otherwise>
                                  <c:out value="${item.itemLabel}" /><c:if test="${not empty item.placementNote}"> · <c:out value="${item.placementNote}" /></c:if> (<c:out value="${item.itemCode}" />)
                                </c:otherwise>
                              </c:choose>
                            </span>
                            <span class="device-location"><c:out value="${item.facilityLocation}" /></span>
                          </div>
                        </td>

                        <%-- Cột SỰ CỐ --%>
                        <td>
                          <div class="cell-incident-info">
                            <strong class="incident-title"><c:out value="${item.request.incidentType.displayName}" /></strong>
                            <p class="incident-desc"><c:out value="${item.request.description}" /></p>
                            <span class="incident-detected">Phát hiện: <c:out value="${item.request.detectedBy.displayName}" /></span>
                            <c:if test="${item.request.warningTagPlaced}">
                              <span class="warning-tag-pill">Đã treo biển ngừng dùng</span>
                            </c:if>
                          </div>
                        </td>

                        <%-- Cột ƯU TIÊN --%>
                        <td>
                          <span class="priority-label priority-${item.request.priority.dbValue.toLowerCase()}">
                            <c:out value="${item.request.priority.displayName}" />
                          </span>
                        </td>

                        <%-- Cột NGÀY BÁO · NGƯỜI BÁO --%>
                        <td>
                          <div class="cell-reporter-info">
                            <span class="report-date">${gms:formatDate(item.request.reportedAt.toLocalDate())}</span>
                            <span class="reporter-name"><c:out value="${item.reporterName}" /></span>
                          </div>
                        </td>

                        <%-- Cột TRẠNG THÁI --%>
                        <td>
                          <c:choose>
                            <c:when test="${item.request.open}">
                              <span class="status-badge badge-open">● Chờ duyệt</span>
                            </c:when>
                            <c:when test="${item.request.inProgress}">
                              <span class="status-badge badge-progress">● Đang xử lý</span>
                            </c:when>
                            <c:when test="${item.request.rejected}">
                              <span class="status-badge badge-rejected">● Từ chối</span>
                            </c:when>
                            <c:when test="${item.request.closed}">
                              <span class="status-badge badge-closed">● Đã đóng</span>
                            </c:when>
                          </c:choose>
                        </td>

                        <%-- Cột THAO TÁC --%>
                        <td style="text-align: right;">
                          <a class="btn ${item.request.open ? 'btn-primary' : 'btn-outline'} btn-sm"
                             href="${pageContext.request.contextPath}/manager/maintenance-requests/review?id=${item.request.maintenanceRequestId}">
                            ${item.request.open ? 'Xem & duyệt' : 'Xem chi tiết'}
                          </a>
                        </td>
                      </tr>
                    </c:forEach>
                  </c:when>
                  <c:otherwise>
                    <tr>
                      <td colspan="7" class="table-empty-message">
                        Không có yêu cầu bảo trì nào phù hợp với bộ lọc hiện tại.
                      </td>
                    </tr>
                  </c:otherwise>
                </c:choose>
              </tbody>
            </table>
          </div>

          <%-- Phân trang --%>
          <c:if test="${requestPage.totalPages > 1}">
            <div class="table-pagination-row">
              <span class="page-counter">Trang ${requestPage.pageNumber} / ${requestPage.totalPages}</span>
              <div class="pagination-buttons">
                <c:if test="${requestPage.hasPrevious}">
                  <a class="btn btn-outline btn-sm" href="?page=${requestPage.pageNumber - 1}&keyword=${keyword}&status=${statusFilter}">‹ Trang trước</a>
                </c:if>
                <c:if test="${requestPage.hasNext}">
                  <a class="btn btn-outline btn-sm" href="?page=${requestPage.pageNumber + 1}&keyword=${keyword}&status=${statusFilter}">Trang sau ›</a>
                </c:if>
              </div>
            </div>
          </c:if>
        </section>

      </main>
    </div>
  </div>
</body>
</html>