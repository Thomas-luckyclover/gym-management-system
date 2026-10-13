<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%--
  Về chúng tôi (/about, AboutServlet). Trang công khai, nội dung tĩnh — thương hiệu SUNSET × DEEP OCEAN.
  Khối: Hero hoàng hôn trên mặt nước aqua · Triết lý tập luyện · Giá trị cốt lõi
        · Câu chuyện và cột mốc · Kêu gọi đăng ký.
  Trang tĩnh: KHÔNG đặt số cấu hình (giờ mở cửa, số cơ sở, số hội viên…) để khỏi lệch khi Manager đổi tham số.
  JS: about-sun.js (nền hero) · CSS: css/pages/guest/about.css
--%>
<!DOCTYPE html>
<html lang="vi">
<head>
  <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
  <title>Về chúng tôi · Super Gym</title>
  <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/guest/about.css?v=20261013c">
  <script src="${pageContext.request.contextPath}/js/pages/guest/about-sun.js?v=2" defer></script>
</head>
<body class="theme-guest-dark about-page">
  <%@ include file="/WEB-INF/jsp/common/flash-toast.jspf" %>
  <c:set var="activeNav" value="about" />
  <%@ include file="/WEB-INF/jsp/common/guest-header.jspf" %>
  <c:set var="ctx" value="${pageContext.request.contextPath}" />

  <main class="cy-main" id="main-content">
    <%-- ===== 1. Hero: mặt trời cam cháy tan chảy, giọt nắng rơi xuống mặt nước aqua và phản chiếu.
         Canvas chỉ trang trí (aria-hidden); tắt JS vẫn còn nền gradient CSS cùng tông. ===== --%>
    <section class="cy-hero" aria-labelledby="cy-title">
      <canvas class="cy-hero__sun" id="aboutSun" aria-hidden="true"></canvas>
      <div class="cy-hero__inner">
        <h1 id="cy-title" class="cy-title">Super<br><em>Gym.</em></h1>
        <p class="cy-sub">Kỹ thuật trước mức tạ. Tiến bộ qua từng buổi tập.</p>
        <div class="cy-hero-actions">
          <a class="cy-btn cy-btn--hot" href="${ctx}/#pricing">Khám phá gói tập <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M5 12h14m-7-7 7 7-7 7"/></svg></a>
          <a class="cy-btn cy-btn--line" href="#story">Câu chuyện của chúng tôi</a>
        </div>
      </div>
    </section>

    <%-- ===== Triết lý tập luyện: nội dung tĩnh, không số liệu hoặc lời chứng thực ===== --%>
    <section class="cy-section cy-manifesto" aria-labelledby="manifesto-title">
      <h2 id="manifesto-title" class="cy-h2">Không chỉ nâng tạ.<br><em>Nâng giới hạn.</em></h2>
      <p>Chúng tôi tin vào những điều nhỏ được lặp lại mỗi ngày: đúng một tư thế, thêm một lần nâng, giữ một lời hẹn với chính mình. Đó là cách sức mạnh trở thành một phần cuộc sống.</p>
    </section>
    <%-- ============================ 3. GIÁ TRỊ CỐT LÕI (tĩnh, không chứa số cấu hình) ============================ --%>
    <section class="cy-section" aria-labelledby="values-title">
      <h2 id="values-title" class="cy-h2">Điều làm nên <em>Super Gym</em></h2>
      <div class="cy-values">
        <article class="cy-value">
          <svg class="cy-value-ico" viewBox="0 0 24 24" aria-hidden="true"><path d="M3 20h18M6 16v-5M11 16V7M16 16v-8M21 16V4"/></svg>
          <h3>Tiến bộ đo được</h3>
          <p>Mỗi buổi tập, mỗi lần check-in đều được ghi lại để bạn thấy mình đã đi được bao xa.</p>
        </article>
        <article class="cy-value">
          <svg class="cy-value-ico" viewBox="0 0 24 24" aria-hidden="true"><path d="M6 7v10M3 9.5v5M18 7v10M21 9.5v5M6 12h12"/></svg>
          <h3>Kỹ thuật trước mức tạ</h3>
          <p>Huấn luyện viên sửa từng tư thế trước khi tăng tạ, để bạn tập đúng kỹ thuật và bền bỉ hơn.</p>
        </article>
        <article class="cy-value">
          <svg class="cy-value-ico" viewBox="0 0 24 24" aria-hidden="true"><circle cx="9" cy="8" r="3.5"/><path d="M2.5 20c.6-3.6 3.3-5.5 6.5-5.5s5.9 1.9 6.5 5.5"/><path d="M16 4.6a3.5 3.5 0 0 1 0 6.8M18.5 14.8c1.6.7 2.7 2.4 3 5.2"/></svg>
          <h3>Có người đồng hành</h3>
          <p>Huấn luyện viên riêng theo sát lịch tập, ghi chú tiến độ và điều chỉnh giáo án cho bạn.</p>
        </article>
        <article class="cy-value">
          <svg class="cy-value-ico" viewBox="0 0 24 24" aria-hidden="true"><rect x="3" y="5" width="18" height="16" rx="2"/><path d="M3 10h18M8 3v4M16 3v4M8 15l2.5 2.5L16 12"/></svg>
          <h3>Mọi thứ trong một tài khoản</h3>
          <p>Mua gói, đặt buổi PT, xem lịch tập và lịch sử giao dịch ngay trên điện thoại.</p>
        </article>
      </div>
    </section>

    <%-- ============================ 4. CÂU CHUYỆN + CỘT MỐC ============================ --%>
    <section class="cy-section cy-story" id="story" aria-labelledby="story-title">
      <div class="cy-story-copy">
        <h2 id="story-title" class="cy-h2">Từ một tầng hầm <em>đến cộng đồng</em></h2>
        <p>Super Gym bắt đầu năm 2019 trong một tầng hầm nhỏ với vài giàn tạ cũ và một nhóm huấn luyện viên tâm huyết. Chúng tôi tin rằng tập luyện hiệu quả phải <b>đo được</b>: số buổi, mức tạ, cân nặng, giấc ngủ.</p>
        <p>Đến nay, mỗi hội viên có lịch PT, gói tập và lịch sử check-in ngay trên tài khoản của mình, để huấn luyện viên và hội viên cùng nhìn vào một bảng số liệu.</p>
        <ol class="cy-timeline">
          <li><b>2019</b><span>Mở cửa phòng tập đầu tiên.</span></li>
          <li><b>2021</b><span>Thêm khu Functional &amp; HIIT và lớp PT đôi.</span></li>
          <li><b>2026</b><span>Ra mắt đặt lịch PT và mua gói trực tuyến.</span></li>
        </ol>
      </div>
      <div class="cy-gallery">
        <%-- Ảnh minh họa Unsplash đã chỉnh duotone thương hiệu (images/brand), không phải ảnh cơ sở --%>
        <figure class="cy-photo cy-photo--a"><img src="${ctx}/images/brand/about-balance.jpg" alt="Người tập giãn cơ trước biển lúc hoàng hôn" loading="lazy" width="1800" height="1135"><figcaption>Cân bằng</figcaption></figure>
        <figure class="cy-photo cy-photo--b"><img src="${ctx}/images/brand/about-back.jpg" alt="Lưng vận động viên trước giàn tạ" loading="lazy" width="1100" height="1650"><figcaption>Sức mạnh</figcaption></figure>
        <figure class="cy-photo cy-photo--c"><img src="${ctx}/images/brand/about-swim.jpg" alt="Người bơi giữa mặt hồ" loading="lazy" width="1800" height="908"><figcaption>Bền bỉ</figcaption></figure>
      </div>
    </section>

    <%-- ============================ 5. KÊU GỌI ============================ --%>
    <section class="cy-section cy-cta">
      <h2 class="cy-h2">Đến lượt bạn <em>vào sân.</em></h2>
      <div class="cy-cta-row">
        <a class="cy-btn cy-btn--hot" href="${ctx}/register">Đăng ký ngay</a>
        <a class="cy-btn cy-btn--line" href="${ctx}/#pricing">Xem bảng giá</a>
      </div>
    </section>
  </main>

  <%@ include file="/WEB-INF/jsp/common/guest-footer.jspf" %>
</body>
</html>
