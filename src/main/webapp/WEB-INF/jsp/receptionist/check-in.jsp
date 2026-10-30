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
              href="${pageContext.request.contextPath}/css/pages/receptionist/check-in.css?v=20261028a">
            <script src="${pageContext.request.contextPath}/js/pages/receptionist/check-in.js?v=20261030a" defer></script>
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
                      <%@ include file="/WEB-INF/jsp/common/topbar-user.jspf" %>
                    </div>
                  </header>

                  <main class="content" id="main-content" tabindex="-1">
                    <div class="split">
                      <div class="col-main">

                        <section class="panel" data-reveal>
                          <div class="panel-head">
                            <div>
                              <h2>Tìm khách</h2>
                            </div>
                          </div>

                          <%-- Form tìm kiếm hội viên theo mã khách, số điện thoại hoặc họ tên --%>
                          <%-- q: tham số gửi lên servlet --%>
                          <%-- <c:out value='${query}' /> giữ lại từ khóa khi tải lại trang,đồng thời dùng c:out để tránh XSS --%>
                          <%-- Bấm nút "Tìm" bằng phương thức GET, từ khóa sẽ đc gắn lên thanh địa chỉ nên mỗi lần bấm Reload trình duyệt
                           sẽ gửi đúng đường link này kèm tham số q lên servlet --%>
                          <%-- Action: là địa chỉ mà trình duyệt gửi dữ liệu tới khi bấm submit --%>
                            <form id="ciForm" action="${pageContext.request.contextPath}/receptionist/check-in"
                              method="GET" class="toolbar" novalidate>
                              <div class="search" style="flex:1;">
                                <i class="fa-solid fa-magnifying-glass"></i>
                                <input id="ciq" name="q" type="search" value="<c:out value='${query}' />"
                                  placeholder="Tên, mã khách hoặc số điện thoại" autocomplete="off"
                                  aria-label="Mã khách, số điện thoại hoặc tên" required <c:if
                                  test="${not lookupResult.canCheckIn}">autofocus</c:if>>
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

                          <%-- Danh sách kết quả tìm kiếm (dùng trực tiếp searchResultsPage.items để đồng bộ với phân trang) --%>
                            <c:if test="${not empty searchResultsPage.items}">
                              <section class="panel ci-search-results" data-reveal>
                                <div class="panel-head">
                                  <div>
                                    <h2>Kết quả tìm kiếm</h2>
                                    <p>Tìm thấy <b>
                                        <c:out
                                          value="${searchResultsPage.totalItems}" />
                                      </b> khách hàng · Chọn khách cần check-in.
                                    </p>
                                  </div>

                                </div>
                                <div class="search-results-list">
                                  <c:forEach var="item" items="${searchResultsPage.items}">
                                    <a href="${pageContext.request.contextPath}/receptionist/check-in?q=<c:out value='${item.customerCode}' />"
                                      class="search-result-row">
                                      <%-- Chọn khách hàng để chuyển thẳng sang màn hình xác nhận check-in --%>
                                        <%-- Khối hiển thị ảnh check-in ở danh sach tìm kiếm --%>
                                        <div class="sr-photo${empty item.checkInPhoto ? ' is-empty' : ''}">
                                          <c:choose>
                                            <c:when test="${not empty item.checkInPhoto}">
                                              <img src="<c:url value='${item.checkInPhoto}' />" alt="Ảnh khách" loading="lazy">
                                            </c:when>
                                            <c:otherwise>
                                              <i class="fa-solid fa-user"></i>
                                              <%-- Có ảnh hiện ảnh, chưa có ảnh hiện icon hình người --%>
                                            </c:otherwise>
                                          </c:choose>
                                        </div>
                                        <%-- Khối hiển thị thông tin khách hàng ở danh sach tìm kiếm --%>
                                        <div class="sr-info">
                                          <div class="sr-name">
                                            <c:out value="${item.fullName}" />
                                          </div>
                                          <div class="sr-meta">
                                            <span class="sr-code"><i class="fa-solid fa-id-card"></i>
                                              <c:out value="${item.customerCode}" />
                                            </span>
                                            <c:choose>
                                              <c:when test="${not empty item.phone}">
                                                <span class="sr-phone"><i class="fa-solid fa-phone"></i>
                                                  <c:out value="${item.phone}" />
                                                </span>
                                              </c:when>
                                              <c:otherwise>
                                                <span class="sr-phone is-muted"><i class="fa-solid fa-phone"></i> Chưa có SĐT</span>
                                              </c:otherwise>
                                            </c:choose>
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
                                            <c:otherwise> <%-- Trường hợp trạng thái khác nhánh default của switch case mặc dù k đc chạy đến --%>
                                              <span class="badge badge-muted">
                                                <c:out value="${item.customerStatus}" />
                                              </span>
                                            </c:otherwise>
                                          </c:choose>
                                        </div>
                                        <span class="sr-arrow" aria-hidden="true">
                                          <i class="fa-solid fa-chevron-right"></i> <%-- biểu tượng mũi tên --%>
                                        </span>
                                    </a>
                                  </c:forEach>
                                </div>

                                <%-- Thanh phân trang --%>
                                  <c:if test="${not empty searchResultsPage and searchResultsPage.totalPages > 1}">
                                  <%-- hiển thị thanh phân trang nếu có số page > 1, kiểm tra empty tránh nullpointex --%>
                                    <div class="ci-pagination">
                                      <c:choose>
                                        <c:when test="${searchResultsPage.hasPrevious}">
                                          <c:url var="prevUrl" value="/receptionist/check-in">
                                            <c:param name="q" value="${query}" />
                                            <c:param name="page" value="${searchResultsPage.pageNumber - 1}" />
                                          </c:url>
                                          <a class="btn btn-ghost btn-sm" href="${prevUrl}"><i
                                              class="fa-solid fa-chevron-left"></i> Trang trước</a>
                                        </c:when>
                                        <c:otherwise>
                                          <button class="btn btn-ghost btn-sm" type="button" disabled><i
                                              class="fa-solid fa-chevron-left"></i> Trang trước</button>
                                        </c:otherwise>
                                        <%-- ẩn nút Trang trước nếu pageNumber = 1 --%>
                                      </c:choose>

                                      <span class="ci-pagination-info">Trang <b>${searchResultsPage.pageNumber}</b> /
                                        <b>${searchResultsPage.totalPages}</b></span> <%-- hiển thị trang hiện tại / tổng số trang --%>

                                      <c:choose>
                                        <c:when test="${searchResultsPage.hasNext}">
                                          <c:url var="nextUrl" value="/receptionist/check-in">
                                            <c:param name="q" value="${query}" />
                                            <c:param name="page" value="${searchResultsPage.pageNumber + 1}" />
                                          </c:url>
                                          <a class="btn btn-ghost btn-sm" href="${nextUrl}">Trang sau <i
                                              class="fa-solid fa-chevron-right"></i></a>
                                        </c:when>
                                        <c:otherwise>
                                          <button class="btn btn-ghost btn-sm" type="button" disabled>Trang sau <i
                                              class="fa-solid fa-chevron-right"></i></button>
                                        </c:otherwise>
                                      </c:choose>
                                    </div>
                                  </c:if>
                              </section>
                            </c:if>

                            <%-- Kết quả tra cứu --%>
                              <c:if test="${not empty lookupResult}">
                              <%-- nếu tìm thấy danh sách thì chạy block 79--%>
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
                                              <div class="ci-photo${empty lookupResult.checkInPhoto ? ' is-empty' : ''}">
                                                <c:choose>
                                                  <c:when test="${not empty lookupResult.checkInPhoto}">
                                                    <img src="<c:url value='${lookupResult.checkInPhoto}' />" alt="Ảnh hội viên">
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
                                                        <c:if test="${not empty lookupResult.ptTotalSessions}"> <%-- kiểm tra null vì có thể chưa đc xếp lịch --%>
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
                                              <%-- alertType: ok, warn, danger, các class tương ứng màu xanh, vàng, đỏ --%>
                                              <c:choose>
                                                <c:when test="${lookupResult.decision.alertType eq 'ok'}"> <%-- đủ điều kiện vào tập, icon v --%>
                                                  <i class="fa-solid fa-circle-check"></i>
                                                </c:when>
                                                <c:when test="${lookupResult.decision.alertType eq 'warn'}"><%-- thiếu ảnh checkin, icon camera --%>
                                                  <i class="fa-solid fa-camera"></i>
                                                </c:when>
                                                <c:otherwise>
                                                  <i class="fa-solid fa-circle-xmark"></i><%-- không đủ điều kiện vào tập, icon x --%>
                                                </c:otherwise>
                                              </c:choose>
                                              <span>
                                                <b> <%-- decision: quyết định hệ thống, message: hướng dẫn lễ tân --%>
                                                  <c:out value="${lookupResult.decision.title}" />.
                                                </b>
                                                <c:out value="${lookupResult.message}" />
                                              </span>
                                            </div>

                                            <%-- Nút hành động --%>
                                              <div class="btn-row">
                                                <c:if test="${lookupResult.canCheckIn}"> <%-- đủ điều kiện vào tập, hiển thị nút xác nhận --%>
                                                  <form
                                                    action="${pageContext.request.contextPath}/receptionist/check-in"
                                                    method="POST" style="margin:0;">
                                                    <input type="hidden" name="action" value="confirmCheckIn"> <%-- type=hiden ẩn đi vì là 1 thẻ chứa dữ liệu, thay vì type=text gioongs ô tìm kiếm --%>
                                                    <input type="hidden" name="subscriptionId" value="${lookupResult.subscriptionId}">
                                                    <input type="hidden" name="customerName" value="<c:out value='${lookupResult.fullName}' />">
                                                    <input type="hidden" name="photoVerified" value="${not empty lookupResult.checkInPhoto}">
                                                    <%-- autofocus: gõ mã → Enter (tìm) → Enter (vào tập) --%>
                                                      <button class="btn btn-primary" type="submit" autofocus>
                                                        <i class="fa-solid fa-door-open"></i> Xác nhận vào tập
                                                      </button>
                                                  </form>
                                                </c:if>

                                                <c:choose>
                                                  <c:when test="${empty lookupResult.checkInPhoto}">
                                                    <button class="btn btn-outline" type="button" data-ci-photo>
                                                      <i class="fa-solid fa-camera"></i> Chụp ảnh tại quầy
                                                    </button>
                                                  </c:when>
                                                  <c:otherwise>
                                                    <button class="btn btn-outline" type="button" data-ci-photo
                                                      title="Chụp lại ảnh check-in nếu ảnh cũ bị mờ hoặc đổi nhận diện">
                                                      <i class="fa-solid fa-camera-rotate"></i> Chụp lại ảnh
                                                    </button>
                                                  </c:otherwise>
                                                </c:choose>
                                              </div>
                                        </section>
                                      </c:otherwise>
                                </c:choose>
                              </c:if>

                      </div>

                      <%-- Cột bên phải: Nhật ký ca --%>
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
                                        <c:out value="${gms:formatTime(item.checkedInAt.toLocalTime())}" /> <%-- hiển thị giờ phút dạng HH:mm --%>
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
                      <h3 id="modalTitle">
                        <c:choose>
                          <c:when test="${not empty lookupResult.checkInPhoto}">Chụp lại ảnh check-in tại quầy</c:when>
                          <c:otherwise>Chụp ảnh check-in tại quầy</c:otherwise>
                        </c:choose>
                      </h3>
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