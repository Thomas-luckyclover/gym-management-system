<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="gms" uri="/WEB-INF/gms-functions.tld" %>
<%--
================================================================================
 home.jsp — TRANG CHỦ CÔNG KHAI (GET /, màn #5, UC-06, luật 2.6). Chỉ hiển thị, không có ô nhập.

 AI GỬI DỮ LIỆU SANG?  HomeServlet.doGet gọi HomeService.getHomePage() MỘT lần → biến home (HomePageView).

   Biến JSP            Ai tạo                                         DAO đọc bảng nào
   ─────────────────── ────────────────────────────────────────────── ─────────────────────────────────────
   home.gymPackages    DAO đọc gói, Service tính giá/tháng,           PackageDAO.findActiveForHomepage:
                       % tiết kiệm, nhãn "Phổ biến nhất"/"Tiết kiệm"    Packages (IsActive = 1)
   home.ptPackages     cùng câu DAO trên, Service tách nhóm gói PT     (như trên)
   home.pts, ptCount   EmployeeDAO.findWorkingPts                      Employees + Persons + COUNT Sessions đã dạy
   home.openingHours   Service đọc P-19 "05:30-22:00" → "05:30 – 22:00" SystemConfigs
   home.latestPosts    BlogService.getLatestPosts(3)                   BlogPosts (Status = Active, 3 bài mới nhất)
   Mọi con số (giá, số buổi đã dạy…) lấy từ DB.

 BỐ CỤC (từ trên xuống)
   1. Hero: "Mạnh hơn mỗi ngày." + giờ mở cửa + số huấn luyện viên, dải chữ chạy
   2. Dải 4 ảnh "Đánh thức bản năng"   (ảnh trong images/brand đã tô sẵn màu xanh-cam)
   3. Dịch vụ                4. Bảng giá gói tập + gói PT      5. Đội ngũ (không ảnh thật, không liên hệ — luật 2.6)
   6. 3 bài viết mới nhất    7. Hỏi đáp (<details>)             8. Kêu gọi đăng ký + linh vật Pumpy

 JS: home-motion.js (hiệu ứng mở màn + cuộn, Web Animations API), home.js (Pumpy).
 Ảnh images/brand: Unsplash / Pexels (giấy phép tự do, nguồn trong file .json cạnh ảnh).
================================================================================ --%>
<!DOCTYPE html>
<html lang="vi">
<head>
  <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
  <title>Super Gym · Mạnh hơn mỗi ngày</title>
  <meta name="description" content="Super Gym — chọn gói tập, đặt lịch huấn luyện viên và theo dõi hành trình tập luyện của bạn.">
  <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/guest/home.css?v=20261025a">
  <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/guest/mascot.css?v=20261014f">
  <script src="${pageContext.request.contextPath}/js/pages/guest/home-motion.js?v=20261013c" defer></script>
  <script src="${pageContext.request.contextPath}/js/pages/guest/home.js?v=20261014f" defer></script>
</head>
<body class="theme-guest-dark home-cine">
  <%@ include file="/WEB-INF/jsp/common/flash-toast.jspf" %>
  <c:set var="activeNav" value="home" />
  <%@ include file="/WEB-INF/jsp/common/guest-header.jspf" %>

  <main id="main-content">
    <%-- ============================ 1. HERO ============================ --%>
    <section class="cine-hero" aria-labelledby="hero-title">
      <div class="cine-hero__media" aria-hidden="true">
        <img class="cine-hero__photo" src="${ctx}/images/brand/hero-barbell.jpg" alt="" width="1600" height="1067" fetchpriority="high">
        <span class="cine-hero__sun"></span>
      </div>
      <div class="wrap cine-hero__inner">
        <h1 id="hero-title" class="cine-title">
          <span class="cine-line"><span>Mạnh hơn</span></span>
          <span class="cine-line cine-line--hot"><span>mỗi ngày.</span></span>
        </h1>
        <p class="cine-lead" data-fade>Tập có mục tiêu. Tiến bộ có lộ trình.<br>Không gian để bạn vượt qua chính mình.</p>
        <div class="cine-actions" data-fade>
          <a class="cine-btn cine-btn--hot" href="${ctx}/register">Bắt đầu tập <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path d="M5 12h14m-7-7 7 7-7 7"/></svg></a>
          <a class="cine-btn cine-btn--ghost" href="#pricing">Xem bảng giá</a>
        </div>
        <%-- 2 con số dưới nút: "Mở cửa mỗi ngày 05:30 – 22:00" (home.openingHours, P-19)
             và "Huấn luyện viên 6" (home.ptCount = số PT đang làm). Thiếu dữ liệu thì ẩn ô đó. --%>
        <dl class="cine-meta" data-fade>
          <c:if test="${not empty home.openingHours}">
            <div><dt>Mở cửa mỗi ngày</dt><dd class="tabular"><c:out value="${home.openingHours}" /></dd></div>
          </c:if>
          <c:if test="${not empty home.pts}">
            <div><dt>Huấn luyện viên</dt><dd class="tabular"><c:out value="${home.ptCount}" /></dd></div>
          </c:if>
        </dl>
      </div>
      <div class="cine-hero__caption" aria-hidden="true"><span>SUPER GYM</span><span>Sức mạnh từ bên trong.</span></div>
      <%-- Dải chữ chạy cuối hero --%>
      <div class="cine-marquee" aria-hidden="true">
        <div class="cine-marquee__track">
          <span>Sức mạnh</span><i aria-hidden="true"></i><span>Sức bền</span><i aria-hidden="true"></i><span>Kỷ luật</span><i aria-hidden="true"></i><span>Tiến bộ</span><i aria-hidden="true"></i><span>Bền bỉ</span>
        </div>
      </div>
    </section>

    <%-- ============================ 2. DẢI 4 ẢNH ============================ --%>
    <section class="cine-strip" aria-labelledby="strip-title">
      <div class="wrap">
        <div class="cine-row-head"><h2 id="strip-title" class="cine-h2" data-reveal-title>Đánh thức<br><em>bản năng.</em></h2></div>
      </div>
      <div class="cine-strip__cards">
        <figure class="cine-card" data-card><img src="${ctx}/images/brand/strip-squat.jpg" alt="Học viên nữ squat với đòn tạ" loading="lazy" width="1600" height="1067"><figcaption>Sức mạnh</figcaption></figure>
        <figure class="cine-card" data-card><img src="${ctx}/images/brand/strip-curl.jpg" alt="Học viên nam tập tay với tạ" loading="lazy" width="1600" height="1067"><figcaption>Kiểm soát</figcaption></figure>
        <figure class="cine-card" data-card><img src="${ctx}/images/brand/strip-endurance.jpg?v=20261025" alt="Vận động viên nữ nâng đòn tạ trong phòng tập" loading="lazy" width="1200" height="1600"><figcaption>Bền bỉ</figcaption></figure>
        <figure class="cine-card" data-card><img src="${ctx}/images/brand/strip-ropes.jpg" alt="Học viên tập thừng chiến" loading="lazy" width="1600" height="1067"><figcaption>Bùng nổ</figcaption></figure>
      </div>
    </section>

    <%-- ============================ 3. DỊCH VỤ ============================ --%>
    <section class="cine-section cine-section--services" id="dich-vu" aria-labelledby="svc-title">
      <div class="wrap cine-services">
        <div class="cine-services__head">
          <h2 id="svc-title" class="cine-h2" data-reveal-title>Một nơi.<br><em>Nhiều cách<br>vượt giới hạn.</em></h2>
          <a class="cine-link" href="#pricing">Tìm gói tập phù hợp <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path d="M5 12h14m-7-7 7 7-7 7"/></svg></a>
        </div>
        <ul class="cine-services__list">
          <li class="cine-service" data-fade>
            <img src="${ctx}/images/brand/facility.jpg" alt="" loading="lazy" width="1600" height="1199">
            <div><h3>Phòng tập tự do</h3><p>Khu tạ, máy tập và cardio đầy đủ, vào tập bất cứ lúc nào trong giờ mở cửa.</p></div>
          </li>
          <li class="cine-service" data-fade>
            <img src="${ctx}/images/brand/svc-pt.jpg" alt="" loading="lazy" width="1600" height="1067">
            <div><h3>Huấn luyện viên kèm riêng</h3><p>Giáo án theo mục tiêu của bạn, huấn luyện viên điểm danh và ghi nhận tiến độ sau từng buổi.</p></div>
          </li>
          <li class="cine-service" data-fade>
            <img src="${ctx}/images/brand/svc-couple.jpg" alt="" loading="lazy" width="1600" height="1067">
            <div><h3>Tập đôi</h3><p>Tập cùng bạn bè hoặc người thân với chung một huấn luyện viên.</p></div>
          </li>
          <li class="cine-service" data-fade>
            <img src="${ctx}/images/brand/svc-track.jpg" alt="" loading="lazy" width="1600" height="1067">
            <div><h3>Theo dõi tiến độ</h3><p>Lịch sử buổi tập, giáo án và ghi chú của huấn luyện viên nằm gọn trong tài khoản của bạn.</p></div>
          </li>
        </ul>
      </div>
    </section>

    <%-- ============================ 4. BẢNG GIÁ (khối đại dương sâu) ============================ --%>
    <section class="cine-ocean" id="pricing" aria-labelledby="pricing-title">
      <div class="wrap">
        <div class="cine-ocean__head">
          <h2 id="pricing-title" class="cine-h2" data-reveal-title>Đầu tư cho<br><em>phiên bản tốt hơn.</em></h2>
          <p class="cine-sub" data-fade>Giá đã gồm VAT, không phát sinh thêm phí.</p>
        </div>
        <%-- ----- 4a. Thẻ gói tập (home.gymPackages, mỗi thẻ: card.pkg = Package do DAO đọc, còn lại Service tính) -----
               card.badge          nhãn trên đầu thẻ "Phổ biến nhất" / "Tiết kiệm nhất" → thẻ viền cam (is-hot)
               card.pkg.packageName  Packages.PackageName, VD "Gói 3 tháng"
               card.pkg.price      Packages.Price → gms:formatMoney "1.200.000đ", chữ to
               card.months         Service đổi thời hạn → "3 tháng" (0 = vé ngày → "1 ngày")
               card.monthlyPrice   Service: giá ÷ số tháng → " · khoảng 400.000đ/tháng"
               card.savingPercent  Service: so với gói 1 tháng → chip "Tiết kiệm 20%"
               card.pkg.benefitLines Packages.Benefits tách dòng → danh sách ✓ quyền lợi
             Nút "Chọn gói" → /customer/checkout?packageId=… --%>
        <c:choose>
          <c:when test="${empty home.gymPackages}">
            <p class="cine-empty">Hiện chưa có gói tập nào đang mở bán. Vui lòng quay lại sau.</p>
          </c:when>
          <c:otherwise>
            <div class="cine-prices">
              <c:forEach var="card" items="${home.gymPackages}">
                <article class="cine-price ${not empty card.badge ? 'is-hot' : ''}" aria-labelledby="plan-${card.pkg.packageId}" data-fade>
                  <c:if test="${not empty card.badge}"><span class="cine-price__badge"><c:out value="${card.badge}" /></span></c:if>
                  <h3 id="plan-${card.pkg.packageId}"><c:out value="${card.pkg.packageName}" /></h3>
                  <p class="cine-price__amount tabular">${gms:formatMoney(card.pkg.price)}</p>
                  <p class="cine-price__per">
                    <c:choose><c:when test="${card.months > 0}">${card.months} tháng</c:when><c:otherwise>1 ngày</c:otherwise></c:choose>
                    <c:if test="${not empty card.monthlyPrice}"> · khoảng ${gms:formatMoney(card.monthlyPrice)}/tháng</c:if>
                  </p>
                  <c:if test="${not empty card.savingPercent}"><span class="cine-price__save">Tiết kiệm ${card.savingPercent}%</span></c:if>
                  <ul>
                    <c:forEach var="benefit" items="${card.pkg.benefitLines}">
                      <li><c:out value="${benefit}" /></li>
                    </c:forEach>
                  </ul>
                  <%-- Chưa đăng nhập: AuthFilter tự chuyển sang /login?returnUrl=... rồi quay lại đúng gói này --%>
                  <a class="cine-btn ${not empty card.badge ? 'cine-btn--hot' : 'cine-btn--line'}" href="${ctx}/customer/checkout?packageId=${card.pkg.packageId}" aria-label="Chọn <c:out value='${card.pkg.packageName}' />">Chọn gói <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path d="M5 12h14m-7-7 7 7-7 7"/></svg></a>
                </article>
              </c:forEach>
            </div>
          </c:otherwise>
        </c:choose>

        <%-- ----- 4b. Gói PT (home.ptPackages) — mỗi gói 1 hàng: tên · "1 người / 2 người · 2 tháng / mỗi buổi" · giá.
             Buổi lẻ (SINGLE_SESSION) có nút cam "Đặt trực tuyến"; gói PT dài hạn chỉ ghi "Đăng ký tại quầy". ----- --%>
        <c:if test="${not empty home.ptPackages}">
          <div class="cine-pt" id="pt-pricing">
            <h3 class="cine-h3">Tập cùng chuyên gia</h3>
            <p class="cine-sub">Gói huấn luyện dài hạn đăng ký tại quầy để được tư vấn lộ trình. Buổi lẻ đặt trực tuyến sau khi đăng nhập.</p>
            <ul class="cine-pt__list">
              <c:forEach var="card" items="${home.ptPackages}">
                <c:set var="isSingleSession" value="${card.pkg.packageType == 'SINGLE_SESSION'}" />
                <c:set var="isCouple" value="${card.pkg.packageType == 'PT_COUPLE'}" />
                <li class="cine-pt__row ${isSingleSession ? 'is-bookable' : ''}" data-fade>
                  <div>
                    <h4><c:out value="${card.pkg.packageName}" /></h4>
                    <p>${isCouple ? '2 người' : '1 người'} · <c:choose><c:when test="${isSingleSession}">mỗi buổi</c:when><c:otherwise>${card.months} tháng</c:otherwise></c:choose></p>
                  </div>
                  <p class="cine-pt__price tabular">${gms:formatMoney(card.pkg.price)}</p>
                  <c:choose>
                    <c:when test="${isSingleSession}"><a class="cine-btn cine-btn--hot cine-btn--sm" href="${ctx}/customer/book-session">Đặt trực tuyến</a></c:when>
                    <c:otherwise><span class="cine-pt__counter">Đăng ký tại quầy</span></c:otherwise>
                  </c:choose>
                </li>
              </c:forEach>
            </ul>
          </div>
        </c:if>
      </div>
    </section>

    <%-- ============================ 5. ĐỘI NGŨ (luật 2.6: không ảnh thật, không hiện liên hệ) ============================ --%>
    <c:if test="${not empty home.pts}">
      <section class="cine-section" id="doi-ngu" aria-labelledby="team-title">
        <div class="wrap">
          <h2 id="team-title" class="cine-h2" data-reveal-title><span class="cine-num tabular">${home.ptCount}</span> huấn luyện viên</h2>
          <div class="cine-coaches">
            <%-- Mỗi PT 1 thẻ: số to "128 buổi đã dạy" (COUNT Sessions do EmployeeDAO đếm) / tên (Persons.FullName) /
                 chuyên môn (Employees.Specialty) / "5 năm kinh nghiệm" (Employees.YearsOfExperience). Không ảnh, không SĐT. --%>
            <c:forEach var="pt" items="${home.pts}">
              <article class="cine-coach" data-fade>
                <p class="cine-coach__sessions tabular">${pt.completedSessionCount}<span>buổi đã dạy</span></p>
                <h3><c:out value="${pt.fullName}" /></h3>
                <c:if test="${not empty pt.specialty}"><p class="cine-coach__role"><c:out value="${pt.specialty}" /></p></c:if>
                <c:if test="${not empty pt.yearsOfExperience}"><p class="cine-coach__exp">${pt.yearsOfExperience} năm kinh nghiệm</p></c:if>
              </article>
            </c:forEach>
          </div>
        </div>
      </section>
    </c:if>

    <%-- ============================ 6. BÀI VIẾT MỚI ============================ --%>
    <c:if test="${not empty home.latestPosts}">
      <section class="cine-section cine-section--tight" aria-labelledby="blog-title">
        <div class="wrap">
          <div class="cine-row-head">
            <h2 id="blog-title" class="cine-h2" data-reveal-title>Tập luyện.<br><em>Hiểu sâu hơn.</em></h2>
            <a class="cine-link" href="${ctx}/blog">Xem tất cả bài viết <i class="fa-solid fa-arrow-right" aria-hidden="true"></i></a>
          </div>
          <div class="cine-posts">
            <%-- Mỗi bài 1 thẻ: ảnh bìa (post.coverImage, lỗi ảnh → ảnh mặc định) / ngày đăng dd/MM/yyyy (post.publishedAt) /
                 tiêu đề (post.title) / đoạn tóm tắt (post.excerpt). Bấm → /blog/detail?id=… --%>
            <c:forEach var="post" items="${home.latestPosts}">
              <a class="cine-post" href="${ctx}/blog/detail?id=${post.blogPostId}" data-fade>
                <c:if test="${not empty post.coverImage}">
                  <span class="cine-post__media"><img loading="lazy" alt="" width="800" height="533" data-image-fallback="${pageContext.request.contextPath}/images/blog/default-blog.jpg" src="${post.coverImage.startsWith('/') ? ctx : ''}<c:out value='${post.coverImage}' />"></span>
                </c:if>
                <span class="cine-post__date"><c:out value="${gms:formatDate(post.publishedAt.toLocalDate())}" /></span>
                <h3><c:out value="${post.title}" /></h3>
                <p><c:out value="${post.excerpt}" /></p>
              </a>
            </c:forEach>
          </div>
        </div>
      </section>
    </c:if>

    <%-- ============================ 7. HỎI ĐÁP ============================ --%>
    <section class="cine-section cine-section--tight" id="faq" aria-labelledby="faq-title">
      <div class="wrap cine-faq">
        <h2 id="faq-title" class="cine-h2" data-reveal-title>Câu hỏi thường gặp</h2>
        <div>
          <details class="cine-faq__item">
            <summary>Tôi đã có hồ sơ tại quầy nhưng chưa có tài khoản thì đăng ký thế nào?</summary>
            <p>Vào trang Đăng ký và nhập số điện thoại đã dùng tại quầy. Hệ thống nhận ra hồ sơ cũ và gửi mã xác thực tới email trên hồ sơ đó để kích hoạt tài khoản.</p>
          </details>
          <details class="cine-faq__item">
            <summary>Gói huấn luyện viên có mua trực tuyến được không?</summary>
            <p>Gói dài hạn chỉ đăng ký tại quầy để huấn luyện viên tư vấn lộ trình. Riêng buổi lẻ thì đặt trực tuyến sau khi đăng nhập.</p>
          </details>
          <details class="cine-faq__item">
            <summary>Quên mật khẩu thì lấy lại bằng cách nào?</summary>
            <p>Bấm "Quên mật khẩu" ở trang Đăng nhập, nhập email đã đăng ký và làm theo mã xác thực được gửi tới email.</p>
          </details>
          <details class="cine-faq__item">
            <summary>Giá trên website đã gồm VAT chưa?</summary>
            <p>Rồi. Giá hiển thị là số tiền cuối cùng bạn trả, không phát sinh thêm phí.</p>
          </details>
        </div>
      </div>
    </section>

    <%-- ============================ 8. KÊU GỌI ĐĂNG KÝ + PUMPY ============================ --%>
    <section class="cta-band cine-cta">
      <div class="wrap cta-inner">
        <div class="cta-copy">
          <h2 class="cine-h2">Buổi tập đầu tiên <em>đang chờ bạn.</em></h2>
          <p class="cta-lead">Pumpy đã khởi động xong. Tạo tài khoản để chọn gói và bắt đầu cùng Super Gym.</p>
          <a class="cine-btn cine-btn--hot" href="${ctx}/register">Tạo tài khoản <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path d="M5 12h14m-7-7 7 7-7 7"/></svg></a>
        </div>
        <%-- Linh vật Pumpy (xanh chanh gốc) — trang trí + tương tác vui (bấm để nhảy) --%>
        <%@ include file="/WEB-INF/jsp/guest/_mascot.jspf" %>
      </div>
    </section>
  </main>

  <c:set var="openingHours" value="${home.openingHours}" />
  <%@ include file="/WEB-INF/jsp/common/guest-footer.jspf" %>
</body>
</html>
