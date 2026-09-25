<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="gms" uri="/WEB-INF/gms-functions.tld" %>
<!DOCTYPE html>
<html lang="vi">
<head>
  <%-- Include phần <head> dùng chung (tokens.css, common.css, fonts, Font Awesome) --%>
  <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
  <title><c:out value="${post.title}" /> · Super Gym</title>
  <meta name="description" content="<c:out value='${post.excerpt}' />">
  <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/guest/blog-detail.css">
  <script src="${pageContext.request.contextPath}/js/pages/guest/blog-detail.js" defer></script>
</head>
<body class="theme-guest">
  <%-- Flash Toast thông báo redirect từ server nếu có --%>
  <%@ include file="/WEB-INF/jsp/common/flash-toast.jspf" %>

  <%-- Thanh chỉ báo tiến trình đọc bài viết (được tính toán bởi blog-detail.js) --%>
  <div class="reading-progress" id="readingProgressBar" aria-hidden="true"></div>

  <%-- ============================ HEADER KHÁCH ============================ --%>
  <header class="site-header">
    <div class="header-inner">
      <a class="brand" href="${pageContext.request.contextPath}/">
        <span class="brand-mark">Super Gym</span>
      </a>

      <%-- Menu điều hướng chính --%>
      <nav class="nav-links">
        <a href="${pageContext.request.contextPath}/">Trang chủ</a>
        <a href="${pageContext.request.contextPath}/#dich-vu">Dịch vụ</a>
        <a href="${pageContext.request.contextPath}/#pricing">Bảng giá</a>
        <a href="${pageContext.request.contextPath}/#co-so-vat-chat">Cơ sở vật chất</a>
        <a href="${pageContext.request.contextPath}/blog" aria-current="page">Blog</a>
        <a href="${pageContext.request.contextPath}/#faq">Hỏi đáp</a>
      </nav>

      <%-- Nút tương tác tài khoản --%>
      <div class="header-actions">
        <c:choose>
          <c:when test="${not empty sessionScope.accountId}">
            <a class="btn btn-ghost btn-sm" href="${pageContext.request.contextPath}/customer/dashboard">
              <i class="fa-regular fa-user"></i> <c:out value="${sessionScope.displayName}" />
            </a>
            <form action="${pageContext.request.contextPath}/logout" method="post" style="display:inline;">
              <button class="btn btn-ghost btn-sm" type="submit" aria-label="Đăng xuất">
                <i class="fa-solid fa-arrow-right-from-bracket"></i> Đăng xuất
              </button>
            </form>
          </c:when>
          <c:otherwise>
            <a class="btn btn-ghost btn-sm" href="${pageContext.request.contextPath}/login">Đăng nhập</a>
            <a class="btn btn-lime btn-sm" href="${pageContext.request.contextPath}/register">Đăng ký</a>
          </c:otherwise>
        </c:choose>
      </div>

      <%-- Nút menu mobile --%>
      <button class="hamburger" type="button" data-toggle-target="mobileNav" aria-label="Mở menu" aria-expanded="false" aria-controls="mobileNav">
        <i class="fa-solid fa-bars"></i>
      </button>
    </div>

    <%-- Menu mở rộng trên thiết bị di động --%>
    <div class="mobile-nav" id="mobileNav" hidden>
      <a href="${pageContext.request.contextPath}/">Trang chủ</a>
      <a href="${pageContext.request.contextPath}/#dich-vu">Dịch vụ</a>
      <a href="${pageContext.request.contextPath}/#pricing">Bảng giá</a>
      <a href="${pageContext.request.contextPath}/#co-so-vat-chat">Cơ sở vật chất</a>
      <a href="${pageContext.request.contextPath}/blog" aria-current="page">Blog</a>
      <a href="${pageContext.request.contextPath}/#faq">Hỏi đáp</a>
      <c:choose>
        <c:when test="${not empty sessionScope.accountId}">
          <a href="${pageContext.request.contextPath}/customer/dashboard">
            <i class="fa-regular fa-user"></i> <c:out value="${sessionScope.displayName}" />
          </a>
          <form action="${pageContext.request.contextPath}/logout" method="post" style="display:block; padding: 10px 0;">
            <button class="btn btn-ghost btn-sm" type="submit">
              <i class="fa-solid fa-arrow-right-from-bracket"></i> Đăng xuất
            </button>
          </form>
        </c:when>
        <c:otherwise>
          <a href="${pageContext.request.contextPath}/login">Đăng nhập</a>
          <a href="${pageContext.request.contextPath}/register">Đăng ký</a>
        </c:otherwise>
      </c:choose>
    </div>
  </header>

  <%-- ====================== CHI TIẾT BÀI VIẾT (SRS #7 / 2.2.2) ====================== --%>
  <main class="main-content">
    <div class="wrap">
      <article class="article-wrap">
        <%-- Nút quay lại danh sách blog --%>
        <a class="back-link" href="${pageContext.request.contextPath}/blog">
          <i class="fa-solid fa-arrow-left"></i> Quay lại danh sách
        </a>

        <%-- Xác định màu nền và icon đại diện theo chuyên mục --%>
        <c:set var="categoryCode" value="${post.category}" />
        <c:set var="toneClass" value="tone-amber" />
        <c:set var="chipClass" value="chip-tap-luyen" />
        <c:set var="iconClass" value="fa-dumbbell" />

        <c:if test="${categoryCode eq 'Nutrition'}">
          <c:set var="toneClass" value="tone-green" />
          <c:set var="chipClass" value="chip-dinh-duong" />
          <c:set var="iconClass" value="fa-utensils" />
        </c:if>
        <c:if test="${categoryCode eq 'News'}">
          <c:set var="toneClass" value="tone-blue" />
          <c:set var="chipClass" value="chip-tin-tuc" />
          <c:set var="iconClass" value="fa-newspaper" />
        </c:if>

        <%-- Khung ảnh bìa bài viết với fallback SVG nếu ảnh không tải được --%>
        <div class="article-cover ${toneClass}">
          <c:if test="${not empty post.coverImage}">
            <img class="article-cover-img"
                 src="${post.coverImage.startsWith('/') ? pageContext.request.contextPath : ''}${post.coverImage}"
                 alt="<c:out value='${post.title}' />"
                 onerror="this.style.display='none'; if(this.nextElementSibling){this.nextElementSibling.style.display='flex';}">
          </c:if>
          <div class="article-cover-fallback" style="${not empty post.coverImage ? 'display:none;' : 'display:flex;'}">
            <svg class="cover-pattern" viewBox="0 0 400 200" preserveAspectRatio="xMidYMid slice" aria-hidden="true">
              <g fill="rgba(255,255,255,.3)">
                <circle cx="22" cy="150" r="4"/><circle cx="50" cy="122" r="4"/><circle cx="78" cy="160" r="4"/>
                <circle cx="22" cy="180" r="4"/><circle cx="50" cy="180" r="4"/><circle cx="78" cy="190" r="4"/>
                <circle cx="106" cy="142" r="4"/><circle cx="6" cy="120" r="4"/><circle cx="106" cy="180" r="4"/>
              </g>
            </svg>
            <span class="article-cover-icon"><i class="fa-solid ${iconClass}"></i></span>
          </div>
        </div>

        <%-- Nhãn chuyên mục --%>
        <span class="chip ${chipClass}"><c:out value="${categoryDisplayName}" /></span>

        <%-- Tiêu đề chính của bài viết --%>
        <h1><c:out value="${post.title}" /></h1>

        <%-- Thông tin bổ sung: ngày đăng, thời gian đọc ước tính, nút chia sẻ --%>
        <div class="article-meta">
          <span class="meta-item">
            <i class="fa-solid fa-calendar-days"></i>
            <time datetime="${post.publishedAt}">
              <c:out value="${gms:formatDate(post.publishedAt.toLocalDate())}" />
            </time>
          </span>
          <span class="meta-item">
            <i class="fa-solid fa-clock"></i>
            <c:out value="${detailView.readingTimeFormatted}" />
          </span>
          <c:if test="${not empty post.authorName}">
            <span class="meta-item">
              <i class="fa-solid fa-user-pen"></i>
              <c:out value="${post.authorName}" />
            </span>
          </c:if>
          <button type="button" class="btn-share" id="btnShareArticle" title="Sao chép liên kết bài viết">
            <i class="fa-solid fa-share-nodes"></i> <span>Chia sẻ</span>
          </button>
        </div>

        <%-- Nội dung chi tiết bài viết (HTML đã được Manager soạn và kiểm duyệt, hiển thị có định dạng) --%>
        <div class="article-body" id="articleBody">
          ${post.content}
        </div>

        <%-- Thanh điều hướng cuối bài --%>
        <div class="article-nav">
          <a class="back-link back-link-flush" href="${pageContext.request.contextPath}/blog">
            <i class="fa-solid fa-arrow-left"></i> Quay lại danh sách
          </a>
          <button type="button" class="btn-top" id="btnScrollToTop" aria-label="Cuộn lên đầu trang">
            <i class="fa-solid fa-arrow-up"></i> <span>Lên đầu trang</span>
          </button>
        </div>
      </article>
    </div>

    <%-- ====================== CÙNG CHUYÊN MỤC / ĐỌC TIẾP ====================== --%>
    <c:if test="${not empty detailView.relatedPosts}">
      <section class="section section-alt related-section">
        <div class="wrap">
          <div class="section-head">
            <span class="eyebrow">Cùng chuyên mục</span>
            <h2>Đọc tiếp</h2>
          </div>

          <div class="post-grid">
            <c:forEach var="related" items="${detailView.relatedPosts}">
              <c:set var="relToneClass" value="tone-amber" />
              <c:set var="relChipClass" value="chip-tap-luyen" />
              <c:set var="relIconClass" value="fa-dumbbell" />
              <c:set var="relCatName" value="Tập luyện" />

              <c:if test="${related.category eq 'Nutrition'}">
                <c:set var="relToneClass" value="tone-green" />
                <c:set var="relChipClass" value="chip-dinh-duong" />
                <c:set var="relIconClass" value="fa-utensils" />
                <c:set var="relCatName" value="Dinh dưỡng" />
              </c:if>
              <c:if test="${related.category eq 'News'}">
                <c:set var="relToneClass" value="tone-blue" />
                <c:set var="relChipClass" value="chip-tin-tuc" />
                <c:set var="relIconClass" value="fa-newspaper" />
                <c:set var="relCatName" value="Tin tức" />
              </c:if>

              <a class="post-card" href="${pageContext.request.contextPath}/blog/detail?id=${related.blogPostId}">
                <div class="post-thumb ${relToneClass}">
                  <c:if test="${not empty related.coverImage}">
                    <img class="post-thumb-img"
                         src="${related.coverImage.startsWith('/') ? pageContext.request.contextPath : ''}${related.coverImage}"
                         alt="<c:out value='${related.title}' />"
                         onerror="this.style.display='none'; if(this.nextElementSibling){this.nextElementSibling.style.display='flex';}">
                  </c:if>
                  <div class="post-thumb-fallback" style="${not empty related.coverImage ? 'display:none;' : 'display:flex;'}">
                    <svg class="cover-pattern" viewBox="0 0 400 200" preserveAspectRatio="xMidYMid slice" aria-hidden="true">
                      <g fill="rgba(255,255,255,.3)">
                        <circle cx="22" cy="150" r="4"/><circle cx="50" cy="122" r="4"/><circle cx="78" cy="160" r="4"/>
                        <circle cx="22" cy="180" r="4"/><circle cx="50" cy="180" r="4"/><circle cx="78" cy="190" r="4"/>
                      </g>
                    </svg>
                    <span class="post-thumb-icon"><i class="fa-solid ${relIconClass}"></i></span>
                  </div>
                </div>

                <div class="post-body">
                  <span class="chip ${relChipClass}"><c:out value="${relCatName}" /></span>
                  <h3><c:out value="${related.title}" /></h3>
                  <p class="post-excerpt"><c:out value="${related.excerpt}" /></p>
                  <div class="post-meta">
                    <i class="fa-solid fa-calendar-days"></i>
                    <c:out value="${gms:formatDate(related.publishedAt.toLocalDate())}" />
                  </div>
                </div>
              </a>
            </c:forEach>
          </div>
        </div>
      </section>
    </c:if>
  </main>

  <%-- ============================ FOOTER KHÁCH ============================ --%>
  <footer class="site-footer">
    <div class="footer-inner">
      <div>
        <div class="footer-brand">Super Gym</div>
        <p class="footer-tag">Theo dõi tiến độ, đặt lịch PT và quản lý gói tập trong một tài khoản duy nhất.</p>
        <div class="footer-social">
          <a href="#" aria-label="Facebook"><i class="fa-brands fa-facebook-f"></i></a>
          <a href="#" aria-label="Instagram"><i class="fa-brands fa-instagram"></i></a>
          <a href="#" aria-label="YouTube"><i class="fa-brands fa-youtube"></i></a>
        </div>
      </div>
      <div class="footer-col">
        <h4>Khám phá</h4>
        <a href="${pageContext.request.contextPath}/">Trang chủ</a>
        <a href="${pageContext.request.contextPath}/#pricing">Bảng giá</a>
        <a href="${pageContext.request.contextPath}/#co-so-vat-chat">Cơ sở vật chất</a>
        <a href="${pageContext.request.contextPath}/blog">Blog</a>
      </div>
      <div class="footer-col">
        <h4>Tài khoản</h4>
        <a href="${pageContext.request.contextPath}/login">Đăng nhập</a>
        <a href="${pageContext.request.contextPath}/register">Đăng ký</a>
        <a href="${pageContext.request.contextPath}/forgot-password">Quên mật khẩu</a>
        <a href="${pageContext.request.contextPath}/#faq">Câu hỏi thường gặp</a>
      </div>
      <div class="footer-col">
        <h4>Liên hệ</h4>
        <p><i class="fa-solid fa-location-dot"></i> 12 Nguyễn Huệ, Q.1, TP.HCM</p>
        <p><i class="fa-solid fa-clock"></i> 05:30 – 22:00 mỗi ngày</p>
        <a href="tel:19006868"><i class="fa-solid fa-phone"></i> 1900 6868</a>
        <a href="mailto:hi@supergym.vn"><i class="fa-solid fa-envelope"></i> hi@supergym.vn</a>
      </div>
    </div>
    <div class="footer-bottom">
      <span>© 2026 Super Gym. All rights reserved.</span>
    </div>
  </footer>
</body>
</html>
