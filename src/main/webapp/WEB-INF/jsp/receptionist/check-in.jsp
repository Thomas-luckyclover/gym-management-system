<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
  <%@ taglib prefix="c" uri="jakarta.tags.core" %>
    <%@ taglib prefix="gms" uri="/WEB-INF/gms-functions.tld" %>
      <%-- check-in.jsp — Màn hình Check-in hội viên tại quầy Lễ tân (#33, UC-19). Tuân thủ các luật: - CBR-37, CBR-38:
        Ảnh check-in thuộc hồ sơ hội viên do Lễ tân quản lý - DBR-47, DBR-48, CBR-58: Điều kiện canUseService (gói
        Membership hoặc DayPass) - GBR-01, GBR-03, GBR-28: Định dạng ngày, giờ, tiền và màu sắc trạng thái --%>
        <!DOCTYPE html>
        <html lang="vi">

        <head>
          <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
            <title>Check-in hội viên · Super Gym Lễ tân</title>
            <link rel="stylesheet"
              href="${pageContext.request.contextPath}/css/pages/receptionist/check-in.css?v=20261013c">
            <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/auth/notification-bell.css">
            <script src="${pageContext.request.contextPath}/js/pages/receptionist/check-in.js" defer></script>
            <script src="${pageContext.request.contextPath}/js/pages/auth/notification-bell.js" defer></script>
        </head>

        <body class="dock theme-staff">
          <%@ include file="/WEB-INF/jsp/common/flash-toast.jspf" %>

            <div class="app">
              <%@ include file="/WEB-INF/jsp/common/layout-receptionist.jspf" %>

                <div class="main">
                  <header class="topbar">
                    <div class="tb-left">
                      <h1 class="tb-title">Check-in hội viên</h1>
                    </div>
                    <div class="top-right">

                      <%@ include file="/WEB-INF/jsp/common/notification-bell.jspf" %>

                        <span class="badge badge-ok">
                          <i class="fa-solid fa-circle-check"></i> Ca làm việc đang mở
                        </span>
                        <a class="tb-user" href="${pageContext.request.contextPath}/account" title="Cài đặt tài khoản">
                          <span class="avatar">LT</span>
                          <span class="tb-user-text">
                            <b>
                              <c:out value="${sessionScope.displayName}" default="Lễ tân" />
                            </b>
                            <small>Lễ tân quầy</small>
                          </span>
                        </a>
                    </div>
                  </header>

                  <main class="content" id="main-content" tabindex="-1">
                    <div class="split">
                      <div class="col-main">

                        <section class="panel" data-reveal>
                          <div class="panel-head">
                            <div>
                              <h2>Tìm khách</h2>
                              <p>Nhập mã khách, số điện thoại hoặc tên để tìm hồ sơ. Hệ thống hỗ trợ tìm theo một phần tên hoặc 4 số cuối SĐT.</p>
                            </div>
                          </div>

                          <%-- Form tìm kiếm hội viên theo mã khách, số điện thoại hoặc họ tên --%>
                            <form id="ciForm" action="${pageContext.request.contextPath}/receptionist/check-in"
                              method="GET" class="toolbar" novalidate>
                              <div class="search" style="flex:1;">
                                <i class="fa-solid fa-magnifying-glass"></i>
                                <input id="ciq" name="q" type="search" value="<c:out value='${query}' />"
                                  placeholder="Nhập mã khách (KH-...), họ tên hoặc 4 số cuối SĐT..."
                                  autocomplete="off" aria-label="Mã khách, số điện thoại hoặc tên" required
                                  <c:if test="${not lookupResult.canCheckIn}">autofocus</c:if>>
                              </div>
                              <button class="btn btn-primary" type="submit">Tìm</button>
                              <c:if test="${not empty query}">
                                <a href="${pageContext.request.contextPath}/receptionist/check-in" class="btn btn-ghost"
                                  title="Xóa tìm kiếm">Xóa Lọc</a>
                              </c:if>
                            </form>
                        </section>

                        <%-- Thông báo lỗi tra cứu (lỗi hệ thống hoặc kết nối cơ sở dữ liệu) --%>
                          <c:if test="${not empty searchError}">
                            <div class="alert-banner danger" style="margin-top:var(--space-4);">
                              <i class="fa-solid fa-circle-xmark"></i>
                              <span><b>Lỗi tra cứu:</b>
                                <c:out value="${searchError}" />
                              </span>
                            </div>
                          </c:if>

                          <%-- Danh sách kết quả tìm kiếm (khi tìm theo tên, trả về nhiều khách có phân trang) --%>
                            <c:if test="${not empty searchResults}">
                              <section class="panel ci-search-results" data-reveal>
                                <div class="panel-head">
                                  <div>
                                    <h2>Kết quả tìm kiếm</h2>
                                    <p>Tìm thấy <b><c:out value="${not empty searchResultsPage ? searchResultsPage.totalItems : searchResults.size()}" /></b> khách hàng phù hợp với từ khóa "<c:out value='${query}' />".
                                      <c:if test="${not empty searchResultsPage and searchResultsPage.totalPages > 1}">
                                        (Trang <b>${searchResultsPage.pageNumber}</b> / <b>${searchResultsPage.totalPages}</b>)
                                      </c:if>
                                      Chọn khách cần check-in.
                                    </p>
                                  </div>
                                </div>
                                <div class="search-results-list">
                                  <c:forEach var="item" items="${searchResults}">
                                    <a href="${pageContext.request.contextPath}/receptionist/check-in?q=<c:out value='${item.customerCode}' />"
                                      class="search-result-row">
                                      <%-- Chọn khách hàng để chuyển thẳng sang màn hình xác nhận check-in --%>
                                      <div class="sr-photo">
                                        <c:choose>
                                          <c:when test="${not empty item.checkInPhoto}">
                                            <img src="<c:out value='${item.checkInPhoto}' />" loading="lazy">
                                          </c:when>
                                          <c:otherwise>
                                            <i class="fa-solid fa-user"></i>
                                          </c:otherwise>
                                        </c:choose>
                                      </div>
                                      <div class="sr-info">
                                        <div class="sr-name">
                                          <c:out value="${item.fullName}" />
                                        </div>
                                        <div class="sr-meta">
                                          <span><i class="fa-solid fa-id-card"></i>
                                            <c:out value="${item.customerCode}" />
                                          </span>
                                          <span><i class="fa-solid fa-phone"></i>
                                            <c:out value="${item.phone}" />
                                          </span>
                                        </div>
                                      </div>
                                      <div class="sr-status">
                                        <c:choose>
                                          <c:when test="${item.customerStatus eq 'Active'}">
                                            <span class="badge badge-ok">Đang hoạt động</span>
                                          </c:when>
                                          <c:when test="${item.customerStatus eq 'New'}">
                                            <span class="badge badge-info">Mới tạo</span>
                                          </c:when>
                                          <c:when test="${item.customerStatus eq 'Frozen'}">
                                            <span class="badge badge-warn">Đang bảo lưu</span>
                                          </c:when>
                                          <c:when test="${item.customerStatus eq 'Expired'}">
                                            <span class="badge badge-muted">Hết hạn</span>
                                          </c:when>
                                          <c:otherwise>
                                            <span class="badge badge-muted">
                                              <c:out value="${item.customerStatus}" />
                                            </span>
                                          </c:otherwise>
                                        </c:choose>
                                      </div>
                                      <i class="fa-solid fa-chevron-right sr-arrow"></i>
                                    </a>
                                  </c:forEach>
                                </div>

                                <%-- Thanh phân trang --%>
                                <c:if test="${not empty searchResultsPage and searchResultsPage.totalPages > 1}">
                                  <div class="ci-pagination">
                                    <c:choose>
                                      <c:when test="${searchResultsPage.hasPrevious}">
                                        <c:url var="prevUrl" value="/receptionist/check-in">
                                          <c:param name="q" value="${query}" />
                                          <c:param name="page" value="${searchResultsPage.pageNumber - 1}" />
                                        </c:url>
                                        <a class="btn btn-ghost btn-sm" href="${prevUrl}"><i class="fa-solid fa-chevron-left"></i> Trang trước</a>
                                      </c:when>
                                      <c:otherwise>
                                        <button class="btn btn-ghost btn-sm" type="button" disabled><i class="fa-solid fa-chevron-left"></i> Trang trước</button>
                                      </c:otherwise>
                                    </c:choose>

                                    <span class="ci-pagination-info">Trang <b>${searchResultsPage.pageNumber}</b> / <b>${searchResultsPage.totalPages}</b></span>

                                    <c:choose>
                                      <c:when test="${searchResultsPage.hasNext}">
                                        <c:url var="nextUrl" value="/receptionist/check-in">
                                          <c:param name="q" value="${query}" />
                                          <c:param name="page" value="${searchResultsPage.pageNumber + 1}" />
                                        </c:url>
                                        <a class="btn btn-ghost btn-sm" href="${nextUrl}">Trang sau <i class="fa-solid fa-chevron-right"></i></a>
                                      </c:when>
                                      <c:otherwise>
                                        <button class="btn btn-ghost btn-sm" type="button" disabled>Trang sau <i class="fa-solid fa-chevron-right"></i></button>
                                      </c:otherwise>
                                    </c:choose>
                                  </div>
                                </c:if>
                              </section>
                            </c:if>

                            <%-- Kết quả tra cứu --%>
                              <c:if test="${not empty lookupResult}">
                                <c:choose>
                                  <%-- Trường hợp không tìm thấy hồ sơ --%>
                                    <c:when test="${lookupResult.decision.name() eq 'NOT_FOUND'}">
                                      <section class="panel ci-card" data-reveal>
                                        <div class="alert-banner danger">
                                          <i class="fa-solid fa-circle-xmark"></i>
                                          <span><b>Không tìm thấy hồ sơ.</b>
                                            <c:out value="${lookupResult.message}" />
                                          </span>
                                        </div>
                                        <div class="btn-row">
                                          <button class="btn btn-primary" type="button"
                                            onclick="document.getElementById('ciq').focus();">
                                            <i class="fa-solid fa-rotate-left"></i> Nhập lại
                                          </button>
                                        </div>
                                      </section>
                                    </c:when>

                                    <%-- Trường hợp tìm thấy hội viên --%>
                                      <c:otherwise>
                                        <section class="panel ci-card" data-reveal>
                                          <div class="ci-top">
                                            <%-- Ảnh check-in --%>
                                              <div
                                                class="ci-photo<c:if test='${empty lookupResult.checkInPhoto}'> is-empty</c:if>"
                                                data-ci-face>
                                                <c:choose>
                                                  <c:when test="${not empty lookupResult.checkInPhoto}">
                                                    <img src="<c:out value='${lookupResult.checkInPhoto}' />"
                                                      alt="Ảnh check-in của <c:out value='${lookupResult.fullName}' />"
                                                      loading="lazy">
                                                  </c:when>
                                                  <c:otherwise>
                                                    <span>Chưa có<br>ảnh</span>
                                                  </c:otherwise>
                                                </c:choose>
                                              </div>

                                              <%-- Thông tin hội viên --%>
                                                <div class="ci-id">
                                                  <h2>
                                                    <c:out value="${lookupResult.fullName}" />
                                                    <c:choose>
                                                      <c:when test="${lookupResult.customerStatus eq 'Active'}">
                                                        <span class="badge badge-ok">Đang hoạt động</span>
                                                      </c:when>
                                                      <c:when test="${lookupResult.customerStatus eq 'New'}">
                                                        <span class="badge badge-info">Mới tạo</span>
                                                      </c:when>
                                                      <c:when test="${lookupResult.customerStatus eq 'Frozen'}">
                                                        <span class="badge badge-warn">Đang bảo lưu</span>
                                                      </c:when>
                                                      <c:when test="${lookupResult.customerStatus eq 'Expired'}">
                                                        <span class="badge badge-muted">Hết hạn</span>
                                                      </c:when>
                                                      <c:otherwise>
                                                        <span class="badge badge-muted">
                                                          <c:out value="${lookupResult.customerStatus}" />
                                                        </span>
                                                      </c:otherwise>
                                                    </c:choose>

                                                    <c:if test="${lookupResult.hasPendingDeletion}">
                                                      <span class="badge badge-danger">Chờ duyệt xoá</span>
                                                    </c:if>
                                                  </h2>

                                                  <div class="row-meta">
                                                    <span><i class="fa-solid fa-id-card"></i>
                                                      <c:out value="${lookupResult.customerCode}" />
                                                    </span>
                                                    <span><i class="fa-solid fa-phone"></i>
                                                      <c:out value="${lookupResult.phone}" />
                                                    </span>
                                                  </div>

                                                  <div class="row-meta">
                                                    <c:choose>
                                                      <c:when test="${not empty lookupResult.packageName}">
                                                        <span>
                                                          <i class="fa-solid fa-dumbbell"></i>
                                                          <c:out value="${lookupResult.packageName}" />
                                                          <c:if test="${not empty lookupResult.endDate}">
                                                            · tới
                                                            <c:out value="${gms:formatDate(lookupResult.endDate)}" />
                                                          </c:if>
                                                        </span>
                                                      </c:when>
                                                      <c:otherwise>
                                                        <span><i class="fa-solid fa-dumbbell"></i> Chưa có gói
                                                          tập</span>
                                                      </c:otherwise>
                                                    </c:choose>

                                                    <c:if test="${lookupResult.hasPtPackage}">
                                                      <span>
                                                        <i class="fa-solid fa-user-check"></i>
                                                        <c:out value="${lookupResult.ptPackageName}" />
                                                        <c:if test="${not empty lookupResult.ptTotalSessions}">
                                                          · tổng ${lookupResult.ptTotalSessions} buổi
                                                        </c:if>
                                                      </span>
                                                    </c:if>
                                                  </div>
                                                </div>
                                          </div>

                                          <%-- Alert Banner đánh giá điều kiện theo GBR-28 --%>
                                            <div
                                              class="alert-banner <c:out value='${lookupResult.decision.alertType}' />">
                                              <c:choose>
                                                <c:when test="${lookupResult.decision.alertType eq 'ok'}">
                                                  <i class="fa-solid fa-circle-check"></i>
                                                </c:when>
                                                <c:when test="${lookupResult.decision.alertType eq 'warn'}">
                                                  <i class="fa-solid fa-camera"></i>
                                                </c:when>
                                                <c:otherwise>
                                                  <i class="fa-solid fa-circle-xmark"></i>
                                                </c:otherwise>
                                              </c:choose>
                                              <span>
                                                <b>
                                                  <c:out value="${lookupResult.decision.title}" />.
                                                </b>
                                                <c:out value="${lookupResult.message}" />
                                              </span>
                                            </div>

                                            <%-- Nút hành động --%>
                                              <div class="btn-row">
                                                <c:if test="${lookupResult.canCheckIn}">
                                                  <form
                                                    action="${pageContext.request.contextPath}/receptionist/check-in"
                                                    method="POST" style="margin:0;">
                                                    <input type="hidden" name="action" value="confirmCheckIn">
                                                    <input type="hidden" name="subscriptionId"
                                                      value="${lookupResult.subscriptionId}">
                                                    <input type="hidden" name="customerName"
                                                      value="<c:out value='${lookupResult.fullName}' />">
                                                    <input type="hidden" name="photoVerified" value="true">
                                                    <%-- autofocus: gõ mã → Enter (tìm) → Enter (vào tập) --%>
                                                      <button class="btn btn-primary" type="submit" data-ci-go
                                                        autofocus>
                                                        <i class="fa-solid fa-door-open"></i> Xác nhận vào tập
                                                      </button>
                                                  </form>
                                                </c:if>

                                                <c:if
                                                  test="${lookupResult.requiresPhoto or empty lookupResult.checkInPhoto}">
                                                  <button class="btn btn-outline" type="button" data-ci-photo>
                                                    <i class="fa-solid fa-camera"></i> Chụp ảnh tại quầy
                                                  </button>
                                                </c:if>
                                              </div>
                                        </section>
                                      </c:otherwise>
                                </c:choose>
                              </c:if>

                      </div>

                      <%-- Cột bên phải: Nhật ký ca & Nút thử nhanh theo dữ liệu seed thật --%>
                        <aside class="col-side">
                          <div class="side-card">
                            <span class="section-label">Lượt vào ca này</span>
                            <div class="data-list plain" id="ciLog">
                              <c:choose>
                                <c:when test="${not empty todayLogs}">
                                  <c:forEach var="item" items="${todayLogs}">
                                    <div class="data-row">
                                      <div class="row-main">
                                        <div class="row-title">
                                          <c:out value="${item.fullName}" />
                                        </div>
                                        <div class="row-sub">
                                          <c:out value="${item.customerCode}" /> ·
                                          <c:out value="${item.packageName}" />
                                        </div>
                                      </div>
                                      <b class="tabular">
                                        <c:if test="${not empty item.checkedInAt}">
                                          <c:out value="${gms:formatTime(item.checkedInAt.toLocalTime())}" />
                                        </c:if>
                                      </b>
                                    </div>
                                  </c:forEach>
                                </c:when>
                                <c:otherwise>
                                  <div
                                    style="font-size:var(--font-size-md);color:var(--color-text-muted);padding:var(--space-2) 0;">
                                    Chưa có lượt vào tập nào trong ca hôm nay.
                                  </div>
                                </c:otherwise>
                              </c:choose>
                            </div>
                          </div>
                        </aside>
                    </div>
                  </main>
                </div>
            </div>

            <%-- Modal chụp ảnh tại quầy (CBR-38) --%>
              <c:if test="${not empty lookupResult}">
                <div class="modal-overlay" id="photoModal" role="dialog" aria-modal="true" aria-labelledby="modalTitle">
                  <div class="modal-box">
                    <div class="modal-head">
                      <h3 id="modalTitle">Chụp ảnh check-in tại quầy</h3>
                      <button class="btn btn-ghost btn-sm" type="button" data-close-modal
                        aria-label="Đóng">&times;</button>
                    </div>
                    <p style="font-size:var(--font-size-md);color:var(--color-text-soft);margin-bottom:var(--space-3);">
                      Camera quầy sẽ chụp và lưu vào hồ sơ của <b>
                        <c:out value="${lookupResult.fullName}" />
                      </b>. Ảnh chỉ dùng để đối chiếu khi khách vào tập.
                    </p>
                    <div class="camera-preview">
                      <video id="cameraVideo" autoplay playsinline></video>
                      <canvas id="cameraCanvas" style="display:none;"></canvas>
                    </div>
                    <form action="${pageContext.request.contextPath}/receptionist/check-in" method="POST"
                      enctype="multipart/form-data">
                      <input type="hidden" name="action" value="savePhoto">
                      <input type="hidden" name="customerId" value="${lookupResult.customerId}">
                      <input type="hidden" name="customerCode" value="<c:out value='${lookupResult.customerCode}' />">
                      <input type="file" id="photoFileInput" name="photoFile" accept="image/jpeg,image/png"
                        style="margin-bottom:var(--space-3);font-size:var(--font-size-md);">
                      <div class="btn-row" style="justify-content:flex-end;">
                        <button class="btn btn-outline" type="button" id="captureBtn">Chụp từ webcam</button>
                        <button class="btn btn-primary" type="submit">Lưu ảnh check-in</button>
                      </div>
                    </form>
                  </div>
                </div>
              </c:if>

        </body>

        </html>