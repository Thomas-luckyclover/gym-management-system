<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="gms" uri="/WEB-INF/gms-functions.tld" %>
<!DOCTYPE html>
<html lang="vi">
<head>
  <%-- Include phần <head> dùng chung (tokens.css, common.css, fonts, Font Awesome) --%>
  <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
  <title>Blog — Super Gym</title>
  <meta name="description" content="Kiến thức tập luyện, dinh dưỡng và tin tức phòng gym, cập nhật mỗi tuần từ đội ngũ huấn luyện viên.">
  <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/guest/blog-list.css">
  <script src="${pageContext.request.contextPath}/js/pages/guest/blog-list.js" defer></script>
</head>
<body class="theme-guest-dark">
  <%-- Flash Toast thông báo redirect từ server nếu có --%>
  <%@ include file="/WEB-INF/jsp/common/flash-toast.jspf" %>

  <%-- Header dùng chung cho luồng khách --%>
  <c:set var="activeNav" value="blog" />
  <%@ include file="/WEB-INF/jsp/common/guest-header.jspf" %>

  <%-- ======================= 2.2.1 — BLOG LIST (SRS #6 / UC-06) ======================= --%>
  <main class="main-content">
    <div class="wrap">
      <%-- Tiêu đề trang danh sách blog --%>
      <div class="page-head">
        <h1>Blog Super Gym</h1>
        <p>Kiến thức tập luyện, dinh dưỡng và tin tức phòng gym, cập nhật mỗi tuần từ đội ngũ huấn luyện viên.</p>
      </div>

      <%-- Thanh công cụ tìm kiếm và lọc chuyên mục --%>
      <div class="blog-controls">
        <form class="search-input-wrap" id="blogSearchForm" action="${pageContext.request.contextPath}/blog" method="get">
          <c:if test="${not empty selectedCategory and selectedCategory ne 'Tất cả'}">
            <input type="hidden" name="category" value="<c:out value='${selectedCategory}' />">
          </c:if>
          <i class="fa-solid fa-magnifying-glass"></i>
          <input id="blogSearch" name="q" type="search" placeholder="Tìm bài viết theo tiêu đề..." aria-label="Tìm bài viết theo tiêu đề" value="<c:out value='${keyword}' />" maxlength="100">
        </form>

        <div class="chip-filter" id="chipFilter">
          <button type="button" class="${selectedCategory eq 'Tất cả' ? 'active' : ''}" data-category="Tất cả">Tất cả</button>
          <button type="button" class="${selectedCategory eq 'Tập luyện' ? 'active' : ''}" data-category="Tập luyện">Tập luyện</button>
          <button type="button" class="${selectedCategory eq 'Dinh dưỡng' ? 'active' : ''}" data-category="Dinh dưỡng">Dinh dưỡng</button>
          <button type="button" class="${selectedCategory eq 'Tin tức' ? 'active' : ''}" data-category="Tin tức">Tin tức</button>
        </div>

        <div class="result-count" id="resultCount" role="status">
          <c:if test="${totalCount > 0}">
            <c:out value="${totalCount}" /> bài viết
          </c:if>
        </div>
      </div>

      <%-- Lưới hiển thị danh sách bài viết (6 bài/trang) --%>
      <div class="post-grid" id="blogGrid">
        <c:forEach var="post" items="${posts}">
          <%-- Xác định màu nền, nhãn chuyên mục và icon đại diện --%>
          <c:set var="cardToneClass" value="tone-amber" />
          <c:set var="cardChipClass" value="chip-tap-luyen" />
          <c:set var="cardIconClass" value="fa-dumbbell" />
          <c:set var="cardCatName" value="Tập luyện" />

          <c:if test="${post.category eq 'Nutrition' or post.category eq 'Dinh dưỡng'}">
            <c:set var="cardToneClass" value="tone-green" />
            <c:set var="cardChipClass" value="chip-dinh-duong" />
            <c:set var="cardIconClass" value="fa-apple-whole" />
            <c:set var="cardCatName" value="Dinh dưỡng" />
          </c:if>
          <c:if test="${post.category eq 'News' or post.category eq 'Tin tức'}">
            <c:set var="cardToneClass" value="tone-blue" />
            <c:set var="cardChipClass" value="chip-tin-tuc" />
            <c:set var="cardIconClass" value="fa-bullhorn" />
            <c:set var="cardCatName" value="Tin tức" />
          </c:if>

          <a class="post-card" href="${pageContext.request.contextPath}/blog/detail?id=${post.blogPostId}"
             data-category="${cardCatName}" data-title="<c:out value='${post.title}' />">
            <div class="post-thumb ${cardToneClass}">
              <c:if test="${not empty post.coverImage and not post.coverImage.contains('default-')}">
                <img class="post-thumb-img"
                     src="${post.coverImage.startsWith('/') ? pageContext.request.contextPath : ''}${post.coverImage}"
                     alt="<c:out value='${post.title}' />"
                     onerror="this.style.display='none'; if(this.nextElementSibling){this.nextElementSibling.classList.remove('is-hidden');}">
              </c:if>
              <div class="post-thumb-fallback<c:if test="${not empty post.coverImage and not post.coverImage.contains('default-')}"> is-hidden</c:if>">
                <svg class="cover-pattern" viewBox="0 0 400 200" preserveAspectRatio="xMidYMid slice" aria-hidden="true">
                  <c:choose>
                    <c:when test="${post.category eq 'Nutrition' or post.category eq 'Dinh dưỡng'}">
                      <g fill="none" stroke="rgba(255,255,255,.28)" stroke-width="3">
                        <circle cx="368" cy="188" r="24"/><circle cx="368" cy="188" r="44"/><circle cx="368" cy="188" r="64"/>
                      </g>
                    </c:when>
                    <c:when test="${post.category eq 'News' or post.category eq 'Tin tức'}">
                      <circle cx="30" cy="6" r="90" fill="rgba(255,255,255,.16)"/>
                    </c:when>
                    <c:otherwise>
                      <g fill="rgba(255,255,255,.3)">
                        <circle cx="22" cy="150" r="4"/><circle cx="50" cy="122" r="4"/><circle cx="78" cy="160" r="4"/>
                        <circle cx="22" cy="180" r="4"/><circle cx="50" cy="180" r="4"/><circle cx="78" cy="190" r="4"/>
                        <circle cx="106" cy="142" r="4"/><circle cx="6" cy="120" r="4"/><circle cx="106" cy="180" r="4"/>
                      </g>
                    </c:otherwise>
                  </c:choose>
                </svg>
                <span class="post-thumb-icon"><i class="fa-solid ${cardIconClass}"></i></span>
              </div>
            </div>

            <div class="post-body">
              <span class="chip ${cardChipClass}"><c:out value="${cardCatName}" /></span>
              <h3><c:out value="${post.title}" /></h3>
              <p class="post-excerpt"><c:out value="${post.excerpt}" /></p>
              <div class="post-meta">
                <i class="fa-solid fa-calendar-days"></i>
                <c:if test="${not empty post.publishedAt}">
                  <c:out value="${gms:formatDate(post.publishedAt.toLocalDate())}" />
                </c:if>
              </div>
            </div>
          </a>
        </c:forEach>

        <%-- Trạng thái rỗng khi không có bài viết khớp bộ lọc (MSG01) --%>
        <div class="empty-state" id="blogEmpty" <c:if test="${not empty posts}">hidden</c:if>>
          <div class="empty-icon"><i class="fa-solid fa-box-open"></i></div>
          <p><b>Không tìm thấy kết quả phù hợp với bộ lọc.</b><br>Thử từ khoá khác hoặc chọn lại danh mục.</p>
        </div>
      </div>

      <%-- Phân trang danh sách bài viết --%>
      <c:if test="${totalCount > 0 and totalPages > 1}">
        <div class="pagination" id="blogPagination">
          <%-- Nút lùi về trang trước --%>
          <button type="button" class="page-btn" data-page="${currentPage - 1}" ${currentPage <= 1 ? 'disabled' : ''} aria-label="Trang trước">
            <i class="fa-solid fa-chevron-left"></i>
          </button>

          <%-- Danh sách các số trang --%>
          <c:forEach var="p" begin="1" end="${totalPages}">
            <button type="button" class="page-btn ${p == currentPage ? 'active' : ''}" data-page="${p}" ${p == currentPage ? 'aria-current="page"' : ''}>
              ${p}
            </button>
          </c:forEach>

          <%-- Nút tiến tới trang sau --%>
          <button type="button" class="page-btn" data-page="${currentPage + 1}" ${currentPage >= totalPages ? 'disabled' : ''} aria-label="Trang sau">
            <i class="fa-solid fa-chevron-right"></i>
          </button>
        </div>
      </c:if>
    </div>
  </main>

  <%-- Footer dùng chung cho luồng khách --%>
  <%@ include file="/WEB-INF/jsp/common/guest-footer.jspf" %>
</body>
</html>
