<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="gms" uri="/WEB-INF/gms-functions.tld" %>
<%--
  Trang chủ (#5 Home, UC-06, luật 2.6). Dữ liệu: HomeServlet → request "home" (HomePageView).
  Chỉ hiển thị, không có ô nhập. Mọi con số lấy từ DB, không gõ cứng theo prototype.
--%>
<!DOCTYPE html>
<html lang="vi">
<head>
  <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
  <title>Super Gym · Tập đúng cách, đo được tiến bộ</title>
  <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/guest/home.css?v=4">
  <script src="${pageContext.request.contextPath}/js/pages/guest/home.js?v=2" defer></script>
</head>
<body class="theme-guest-dark">
  <%@ include file="/WEB-INF/jsp/common/flash-toast.jspf" %>
  <c:set var="activeNav" value="home" />
  <%@ include file="/WEB-INF/jsp/common/guest-header.jspf" %>

  <main>
    <%-- ============================ HERO ============================ --%>
    <section class="hero">
      <div class="wrap hero-inner">
       <div>
        <h1>Tập đúng cách,<br><em>đo được</em> tiến bộ.</h1>
        <p class="lead">Một tài khoản cho tất cả: mua gói, check-in tại quầy, đặt lịch huấn luyện viên riêng và theo dõi từng buổi tập.</p>
        <div class="btn-row">
          <a class="btn btn-primary btn-lg" href="${ctx}/register">Đăng ký ngay <i class="fa-solid fa-arrow-right"></i></a>
          <a class="btn btn-ghost btn-lg" href="#pricing">Xem bảng giá</a>
        </div>
        <div class="hero-stats">
          <c:if test="${not empty home.openingHours}">
            <div class="hero-stat">
              <b class="tabular"><c:out value="${home.openingHours}" /></b>
              <span>mở cửa mỗi ngày</span>
            </div>
          </c:if>
        </div>
       </div>

        <%-- Minh hoạ tính năng (thẻ hội viên, check-in, buổi PT) — chỉ để trang trí, dữ liệu mẫu --%>
        <div class="hero-visual" aria-hidden="true">
          <div class="hv-card hv-member">
            <div class="hv-member-top"><span class="hv-label">Thẻ hội viên</span><span class="hv-pill">Active</span></div>
            <div class="hv-name">NGUYỄN MINH AN</div>
            <div class="hv-rows">
              <div class="hv-row"><span>Mã hội viên</span><b>SG-0241</b></div>
              <div class="hv-row"><span>Gói 6 tháng</span><b>đến 14/03/2027</b></div>
            </div>
            <div class="hv-code">
              <c:forTokens var="h" items="72,36,48,84,24,60,36,96,48,24,72,36,60,84,24,48,96,36,60,24,72,48,36,84,60,24,48,72,36,96,24,60,48,36" delims=",">
                <i style="height:${h}%"></i>
              </c:forTokens>
            </div>
          </div>

          <div class="hv-card hv-checkin">
            <span class="hv-ico"><i class="fa-solid fa-check"></i></span>
            <div><b>Đã check-in 06:42</b><span>Super Gym · Nguyễn Huệ</span></div>
          </div>

          <c:if test="${not empty home.pts}">
            <c:set var="heroPt" value="${home.pts[0]}" />
            <div class="hv-card hv-session">
              <div class="hv-session-head"><span>Buổi PT kế tiếp</span><b>T4 · 19:00</b></div>
              <div class="hv-session-row">
                <span class="hv-avatar"><c:out value="${heroPt.initials}" /></span>
                <div>
                  <b>PT <c:out value="${heroPt.fullName}" /></b>
                  <c:if test="${not empty heroPt.specialty}"><span><c:out value="${heroPt.specialty}" /></span></c:if>
                </div>
              </div>
              <div class="hv-progress">
                <div class="hv-progress-bar"><i></i></div>
                <div class="hv-progress-label"><span>Tiến độ gói PT</span><b class="tabular">8 / 10 buổi</b></div>
              </div>
            </div>
          </c:if>
        </div>
      </div>
    </section>

    <%-- ============================ DỊCH VỤ (nội dung tĩnh) ============================ --%>
    <section class="section" id="dich-vu">
      <div class="wrap">
        <div class="section-head">
          <span class="eyebrow">Dịch vụ</span>
          <h2>Mọi thứ bạn cần để bắt đầu tập luyện</h2>
        </div>
        <div class="card-grid">
          <div class="info-card">
            <i class="fa-solid fa-dumbbell"></i>
            <h3>Phòng tập tự do</h3>
            <p>Khu tạ, máy tập và cardio đầy đủ, vào tập bất cứ lúc nào trong giờ mở cửa.</p>
          </div>
          <div class="info-card">
            <i class="fa-solid fa-user-check"></i>
            <h3>Huấn luyện viên 1 kèm 1</h3>
            <p>Giáo án theo mục tiêu riêng, PT điểm danh và ghi nhận tiến độ sau từng buổi.</p>
          </div>
          <div class="info-card">
            <i class="fa-solid fa-user-group"></i>
            <h3>Gói Couple</h3>
            <p>Tập cùng bạn bè hoặc người thân với chung một huấn luyện viên.</p>
          </div>
          <div class="info-card">
            <i class="fa-solid fa-chart-line"></i>
            <h3>Theo dõi tiến độ</h3>
            <p>Lịch sử buổi tập, giáo án và ghi chú của PT nằm gọn trong tài khoản của bạn.</p>
          </div>
        </div>
      </div>
    </section>

    <%-- ============================ BẢNG GIÁ GÓI TẬP ============================ --%>
    <section class="section section-alt" id="pricing">
      <div class="wrap">
        <div class="section-head">
          <span class="eyebrow">Bảng giá</span>
          <h2>Chọn gói phù hợp với mục tiêu của bạn</h2>
          <p>Giá đã gồm VAT, không phí ẩn.</p>
        </div>

        <c:choose>
          <c:when test="${empty home.gymPackages}">
            <div class="empty-state">Hiện chưa có gói tập nào đang mở bán. Vui lòng quay lại sau.</div>
          </c:when>
          <c:otherwise>
            <div class="card-grid">
              <c:forEach var="card" items="${home.gymPackages}">
                <div class="price-card ${not empty card.badge ? 'is-highlight' : ''}">
                  <c:if test="${not empty card.badge}">
                    <span class="price-badge"><c:out value="${card.badge}" /></span>
                  </c:if>
                  <h3><c:out value="${card.pkg.packageName}" /></h3>
                  <div class="price-amount tabular">
                    ${gms:formatMoney(card.pkg.price)}
                    <small>/ <c:choose><c:when test="${card.months > 0}">${card.months} tháng</c:when><c:otherwise>ngày</c:otherwise></c:choose></small>
                  </div>
                  <c:if test="${not empty card.monthlyPrice}">
                    <div class="price-sub tabular">≈ ${gms:formatMoney(card.monthlyPrice)} mỗi tháng</div>
                  </c:if>
                  <c:if test="${not empty card.savingPercent}">
                    <span class="price-save">Tiết kiệm ${card.savingPercent}%</span>
                  </c:if>
                  <c:if test="${not empty card.pkg.description}">
                    <p class="price-desc"><c:out value="${card.pkg.description}" /></p>
                  </c:if>
                  <ul class="price-features">
                    <c:forEach var="benefit" items="${card.pkg.benefitLines}">
                      <li><i class="fa-solid fa-check"></i> <c:out value="${benefit}" /></li>
                    </c:forEach>
                  </ul>
                  <%-- Chưa đăng nhập: AuthFilter tự chuyển sang /login?returnUrl=... rồi quay lại đúng gói này --%>
                  <a class="btn ${not empty card.badge ? 'btn-primary' : 'btn-outline'} btn-block"
                     href="${ctx}/customer/checkout?packageId=${card.pkg.packageId}">Đăng ký gói này</a>
                </div>
              </c:forEach>
            </div>
          </c:otherwise>
        </c:choose>
      </div>
    </section>

    <%-- ============================ BẢNG GIÁ PT ============================ --%>
    <c:if test="${not empty home.ptPackages}">
      <section class="section" id="pt-pricing">
        <div class="wrap">
          <div class="section-head">
            <span class="eyebrow">Personal Trainer</span>
            <h2>Huấn luyện cùng chuyên gia</h2>
            <p>Gói PT dài hạn đăng ký tại quầy để được tư vấn lộ trình. Buổi lẻ đặt online sau khi đăng nhập.</p>
          </div>
          <div class="card-grid">
            <c:forEach var="card" items="${home.ptPackages}">
              <c:set var="isSingleSession" value="${card.pkg.packageType == 'SINGLE_SESSION'}" />
              <c:set var="isCouple" value="${card.pkg.packageType == 'PT_COUPLE'}" />
              <div class="price-card">
                <h3><c:out value="${card.pkg.packageName}" /></h3>
                <div class="price-amount tabular">
                  ${gms:formatMoney(card.pkg.price)}
                  <small>/ <c:choose><c:when test="${isSingleSession}">buổi</c:when><c:otherwise>${card.months} tháng</c:otherwise></c:choose></small>
                </div>
                <div class="price-sub">
                  <i class="fa-solid ${isCouple ? 'fa-user-group' : 'fa-user'}"></i>
                  ${isCouple ? '2 người' : '1 người'}
                </div>
                <ul class="price-features">
                  <c:forEach var="benefit" items="${card.pkg.benefitLines}">
                    <li><i class="fa-solid fa-check"></i> <c:out value="${benefit}" /></li>
                  </c:forEach>
                </ul>
                <c:choose>
                  <c:when test="${isSingleSession}">
                    <a class="btn btn-primary btn-block" href="${ctx}/customer/book-session">
                      <i class="fa-solid fa-globe"></i> Đặt online
                    </a>
                  </c:when>
                  <c:otherwise>
                    <span class="counter-only"><i class="fa-solid fa-shop"></i> Đăng ký tại quầy</span>
                  </c:otherwise>
                </c:choose>
              </div>
            </c:forEach>
          </div>
        </div>
      </section>
    </c:if>

    <%-- ============================ ĐỘI NGŨ PT ============================ --%>
    <c:if test="${not empty home.pts}">
      <section class="section section-alt" id="doi-ngu">
        <div class="wrap">
          <div class="section-head">
            <span class="eyebrow">Đội ngũ</span>
            <h2>${home.ptCount} huấn luyện viên của Super Gym</h2>
          </div>
          <div class="card-grid">
            <c:forEach var="pt" items="${home.pts}">
              <div class="coach-card">
                <%-- Luật 2.6: avatar là chữ cái đầu tên, không dùng ảnh thật; không hiện SĐT/email --%>
                <div class="coach-avatar"><c:out value="${pt.initials}" /></div>
                <h3><c:out value="${pt.fullName}" /></h3>
                <c:if test="${not empty pt.specialty}">
                  <div class="coach-role"><c:out value="${pt.specialty}" /></div>
                </c:if>
                <div class="coach-meta">
                  <c:if test="${not empty pt.yearsOfExperience}">${pt.yearsOfExperience} năm kinh nghiệm · </c:if>
                  ${pt.completedSessionCount} buổi đã dạy
                </div>
              </div>
            </c:forEach>
          </div>
        </div>
      </section>
    </c:if>

    <%-- ============================ BÀI VIẾT MỚI (không có bài → ẩn cả khối) ============================ --%>
    <c:if test="${not empty home.latestPosts}">
      <section class="section">
        <div class="wrap">
          <div class="section-head section-head-row">
            <div>
              <span class="eyebrow">Blog</span>
              <h2>Bài viết mới nhất</h2>
            </div>
            <a class="btn btn-ghost btn-sm" href="${ctx}/blog">Xem tất cả <i class="fa-solid fa-arrow-right"></i></a>
          </div>
          <div class="card-grid card-grid-3">
            <c:forEach var="post" items="${home.latestPosts}">
              <a class="post-card" href="${ctx}/blog/detail?id=${post.blogPostId}">
                <c:if test="${not empty post.coverImage}">
                  <img class="post-thumb" loading="lazy" alt=""
                       src="${post.coverImage.startsWith('/') ? ctx : ''}${post.coverImage}">
                </c:if>
                <div class="post-body">
                  <h3><c:out value="${post.title}" /></h3>
                  <p class="post-excerpt"><c:out value="${post.excerpt}" /></p>
                  <div class="post-meta">
                    <i class="fa-regular fa-calendar"></i>
                    <c:out value="${gms:formatDate(post.publishedAt.toLocalDate())}" />
                  </div>
                </div>
              </a>
            </c:forEach>
          </div>
        </div>
      </section>
    </c:if>

    <%-- ============================ HỎI ĐÁP (nội dung tĩnh — SRS không yêu cầu quản lý) ============================ --%>
    <section class="section section-alt" id="faq">
      <div class="wrap wrap-narrow">
        <div class="section-head">
          <span class="eyebrow">Hỏi đáp</span>
          <h2>Câu hỏi thường gặp</h2>
        </div>
        <details class="faq-item">
          <summary>Tôi đã có hồ sơ tại quầy nhưng chưa có tài khoản thì đăng ký thế nào?</summary>
          <p>Vào trang Đăng ký và nhập số điện thoại đã dùng tại quầy. Hệ thống nhận ra hồ sơ cũ và gửi mã xác thực tới email trên hồ sơ đó để kích hoạt tài khoản.</p>
        </details>
        <details class="faq-item">
          <summary>Gói PT có mua online được không?</summary>
          <p>Gói PT dài hạn chỉ đăng ký tại quầy để huấn luyện viên tư vấn lộ trình. Riêng buổi PT lẻ thì đặt online sau khi đăng nhập.</p>
        </details>
        <details class="faq-item">
          <summary>Quên mật khẩu thì lấy lại bằng cách nào?</summary>
          <p>Bấm "Quên mật khẩu" ở trang Đăng nhập, nhập email đã đăng ký và làm theo mã xác thực được gửi tới email.</p>
        </details>
        <details class="faq-item">
          <summary>Giá trên website đã gồm VAT chưa?</summary>
          <p>Rồi. Giá hiển thị là số tiền cuối cùng bạn trả, không phát sinh thêm phí.</p>
        </details>
      </div>
    </section>

    <%-- ============================ KÊU GỌI ĐĂNG KÝ ============================ --%>
    <section class="cta-band">
      <div class="wrap cta-inner">
        <div class="cta-copy">
          <h2>Sẵn sàng cho <em>buổi tập đầu tiên?</em></h2>
          <p class="cta-lead">Pumpy đã khởi động xong rồi. Tạo tài khoản để chọn gói và bắt đầu cùng Super Gym.</p>
          <a class="btn btn-primary btn-lg" href="${ctx}/register">Đăng ký ngay <i class="fa-solid fa-arrow-right"></i></a>
        </div>
        <%-- Linh vật Pumpy ngồi cạnh tạ — trang trí + tương tác vui (bấm để nhảy) --%>
        <%@ include file="/WEB-INF/jsp/guest/_mascot.jspf" %>
      </div>
    </section>
  </main>

  <c:set var="openingHours" value="${home.openingHours}" />
  <%@ include file="/WEB-INF/jsp/common/guest-footer.jspf" %>
</body>
</html>
