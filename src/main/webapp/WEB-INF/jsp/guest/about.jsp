<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%--
  Về chúng tôi (/about, AboutServlet). Trang công khai, nội dung tĩnh — phong cách cyberpunk neon.
  Khối: 1 Hero neon · 2 Vòng card danh ngôn xoay 3D · 3 Giá trị cốt lõi · 4 Câu chuyện + cột mốc · 5 Minigame Pumpy né đồ ăn vặt · 6 Kêu gọi.
  Trang tĩnh: KHÔNG đặt số cấu hình (giờ mở cửa, số cơ sở, số hội viên…) để khỏi lệch khi Manager đổi tham số.
  JS: js/pages/guest/about.js · CSS: css/pages/guest/about.css
--%>
<!DOCTYPE html>
<html lang="vi">
<head>
  <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
  <title>Về chúng tôi · Super Gym</title>
  <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/guest/about.css?v=5">
  <script src="${pageContext.request.contextPath}/js/pages/guest/about.js?v=3" defer></script>
</head>
<body class="theme-guest-dark about-page">
  <%@ include file="/WEB-INF/jsp/common/flash-toast.jspf" %>
  <%@ include file="/WEB-INF/jsp/common/guest-header.jspf" %>
  <c:set var="ctx" value="${pageContext.request.contextPath}" />

  <%-- Nền chung: lưới neon chạy về chân trời + vạch quét --%>
  <div class="cy-backdrop" aria-hidden="true">
    <div class="cy-sun"></div>
    <div class="cy-grid"></div>
    <div class="cy-scan"></div>
  </div>

  <main class="cy-main">
    <%-- ============================ 1. HERO NEON ============================ --%>
    <section class="cy-hero" aria-labelledby="cy-title">
      <div class="cy-sign" data-cy-tilt>
        <svg class="cy-deco cy-deco--star" viewBox="0 0 24 24" aria-hidden="true"><path d="M12 2l2.6 6.9L22 9.3l-5.7 4.6 1.9 7.1L12 17l-6.2 4 1.9-7.1L2 9.3l7.4-.4z"/></svg>
        <svg class="cy-deco cy-deco--spark" viewBox="0 0 24 24" aria-hidden="true"><path d="M12 0l2.4 9.6L24 12l-9.6 2.4L12 24l-2.4-9.6L0 12l9.6-2.4z"/></svg>
        <svg class="cy-deco cy-deco--bolt" viewBox="0 0 24 24" aria-hidden="true"><path d="M14 2L4 14h7l-1 8 10-12h-7z"/></svg>
        <svg class="cy-deco cy-deco--arrows" viewBox="0 0 40 20" aria-hidden="true"><path d="M2 10l16-8-4 8 4 8zM38 10L22 2l4 8-4 8z"/></svg>
        <h1 id="cy-title" class="cy-title" data-text="SUPER  GYM" aria-label="Super Gym">SUPER<svg class="cy-title-star" viewBox="0 0 24 24" aria-hidden="true"><path d="M12 0l2.4 9.6L24 12l-9.6 2.4L12 24l-2.4-9.6L0 12l9.6-2.4z"/></svg>GYM</h1>
        <%-- Quả tạ neon: nâng lên hạ xuống như đang tập (trang trí) --%>
        <svg class="cy-dumbbell" viewBox="0 0 240 80" aria-hidden="true">
          <rect x="62" y="34" width="116" height="12" rx="6" class="cy-db-bar"/>
          <rect x="40" y="16" width="20" height="48" rx="6" class="cy-db-plate"/>
          <rect x="16" y="8" width="22" height="64" rx="7" class="cy-db-plate cy-db-plate--big"/>
          <rect x="180" y="16" width="20" height="48" rx="6" class="cy-db-plate"/>
          <rect x="202" y="8" width="22" height="64" rx="7" class="cy-db-plate cy-db-plate--big"/>
          <rect x="100" y="34" width="40" height="12" rx="3" class="cy-db-grip"/>
        </svg>
        <p class="cy-meta">EST. 2019 · HÀ NỘI</p>
      </div>
      <a class="cy-scroll" href="#quotes">Cuộn xuống <span aria-hidden="true">↓</span></a>
    </section>

    <%-- ============================ 2. VÒNG CARD DANH NGÔN XOAY 3D ============================ --%>
    <section class="cy-section" id="quotes" aria-labelledby="quotes-title">
      <h2 id="quotes-title" class="cy-h2">Mantra <em>phòng gym</em></h2>

      <div class="cy-ring-stage" id="ringStage">
        <div class="cy-ring" id="quoteRing" style="--n: 12">
          <article class="cy-card" style="--i: 0"><span class="cy-tag">#01</span><blockquote>Tôi ghét từng phút tập luyện, nhưng tôi tự nhủ: đừng bỏ cuộc. Chịu khổ bây giờ để sống như nhà vô địch cả đời.</blockquote><cite>Muhammad Ali</cite></article>
          <article class="cy-card" style="--i: 1"><span class="cy-tag">#02</span><blockquote>Sức mạnh không đến từ chiến thắng. Chính những lần vật lộn mới tạo nên sức mạnh.</blockquote><cite>Arnold Schwarzenegger</cite></article>
          <article class="cy-card" style="--i: 2"><span class="cy-tag">#03</span><blockquote>Tôi không sợ người luyện 10.000 cú đá một lần. Tôi sợ người luyện một cú đá 10.000 lần.</blockquote><cite>Lý Tiểu Long</cite></article>
          <article class="cy-card" style="--i: 3"><span class="cy-tag">#04</span><blockquote>Thể chất khỏe mạnh là nền tảng của một trí óc năng động và sáng tạo.</blockquote><cite>John F. Kennedy</cite></article>
          <article class="cy-card" style="--i: 4"><span class="cy-tag">#05</span><blockquote>Chúng ta là những gì ta lặp đi lặp lại. Vì thế, sự xuất sắc không phải một hành động mà là một thói quen.</blockquote><cite>Will Durant</cite></article>
          <article class="cy-card" style="--i: 5"><span class="cy-tag">#06</span><blockquote>Bạn không cần phải giỏi để bắt đầu, nhưng phải bắt đầu để trở nên giỏi.</blockquote><cite>Zig Ziglar</cite></article>
          <article class="cy-card" style="--i: 6"><span class="cy-tag">#07</span><blockquote>Chiến thắng không phải là tất cả, nhưng khát khao chiến thắng thì có.</blockquote><cite>Vince Lombardi</cite></article>
          <article class="cy-card" style="--i: 7"><span class="cy-tag">#08</span><blockquote>Thể thao không xây dựng tính cách. Nó bộc lộ tính cách.</blockquote><cite>Heywood Broun</cite></article>
          <article class="cy-card" style="--i: 8"><span class="cy-tag">#09</span><blockquote>Sức khỏe mới là tài sản thật sự, không phải vàng hay bạc.</blockquote><cite>Mahatma Gandhi</cite></article>
          <article class="cy-card" style="--i: 9"><span class="cy-tag">#10</span><blockquote>Hãy chăm sóc cơ thể. Đó là nơi duy nhất bạn phải sống.</blockquote><cite>Jim Rohn</cite></article>
          <article class="cy-card" style="--i: 10"><span class="cy-tag">#11</span><blockquote>Tôi đã thất bại hết lần này đến lần khác trong đời. Và đó chính là lý do tôi thành công.</blockquote><cite>Michael Jordan</cite></article>
          <article class="cy-card" style="--i: 11"><span class="cy-tag">#12</span><blockquote>Bất khả thi không phải là sự thật. Đó chỉ là một ý kiến.</blockquote><cite>Muhammad Ali</cite></article>
        </div>
      </div>
      <div class="cy-ring-controls">
        <button type="button" class="cy-btn-icon" id="ringPrev" aria-label="Thẻ trước">‹</button>
        <span class="cy-ring-count" id="ringCount" aria-live="polite">#01 / 12</span>
        <button type="button" class="cy-btn-icon" id="ringNext" aria-label="Thẻ sau">›</button>
      </div>
    </section>

    <%-- ============================ 3. GIÁ TRỊ CỐT LÕI (tĩnh, không chứa số cấu hình) ============================ --%>
    <section class="cy-section" aria-labelledby="values-title">
      <h2 id="values-title" class="cy-h2">Điều làm nên <em>Super Gym</em></h2>
      <div class="cy-values">
        <article class="cy-value" data-cy-tilt>
          <svg class="cy-value-ico" viewBox="0 0 24 24" aria-hidden="true"><path d="M3 20h18M6 16v-5M11 16V7M16 16v-8M21 16V4"/></svg>
          <h3>Tiến bộ đo được</h3>
          <p>Mỗi buổi tập, mỗi lần check-in đều được ghi lại để bạn thấy mình đã đi được bao xa.</p>
        </article>
        <article class="cy-value" data-cy-tilt>
          <svg class="cy-value-ico" viewBox="0 0 24 24" aria-hidden="true"><path d="M6 7v10M3 9.5v5M18 7v10M21 9.5v5M6 12h12"/></svg>
          <h3>Kỹ thuật trước mức tạ</h3>
          <p>Huấn luyện viên sửa từng tư thế trước khi tăng tạ, để bạn tập lâu dài mà không chấn thương.</p>
        </article>
        <article class="cy-value" data-cy-tilt>
          <svg class="cy-value-ico" viewBox="0 0 24 24" aria-hidden="true"><circle cx="9" cy="8" r="3.5"/><path d="M2.5 20c.6-3.6 3.3-5.5 6.5-5.5s5.9 1.9 6.5 5.5"/><path d="M16 4.6a3.5 3.5 0 0 1 0 6.8M18.5 14.8c1.6.7 2.7 2.4 3 5.2"/></svg>
          <h3>Có người đồng hành</h3>
          <p>Huấn luyện viên riêng theo sát lịch tập, ghi chú tiến độ và điều chỉnh giáo án cho bạn.</p>
        </article>
        <article class="cy-value" data-cy-tilt>
          <svg class="cy-value-ico" viewBox="0 0 24 24" aria-hidden="true"><rect x="3" y="5" width="18" height="16" rx="2"/><path d="M3 10h18M8 3v4M16 3v4M8 15l2.5 2.5L16 12"/></svg>
          <h3>Mọi thứ trong một tài khoản</h3>
          <p>Mua gói, đặt buổi PT, xem lịch tập và lịch sử giao dịch ngay trên điện thoại.</p>
        </article>
      </div>
    </section>

    <%-- ============================ 4. CÂU CHUYỆN + CỘT MỐC ============================ --%>
    <section class="cy-section cy-story" aria-labelledby="story-title">
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
        <figure class="cy-photo cy-photo--a" data-cy-tilt><img src="${ctx}/images/blog/gian-co.jpg" alt="Khu giàn tạ của Super Gym" loading="lazy"><figcaption>#STRENGTH</figcaption></figure>
        <figure class="cy-photo cy-photo--b" data-cy-tilt><img src="${ctx}/images/blog/cardio.jpg" alt="Khu cardio buổi tối" loading="lazy"><figcaption>#CARDIO</figcaption></figure>
        <figure class="cy-photo cy-photo--c" data-cy-tilt><img src="${ctx}/images/blog/khoi-dong.jpg" alt="Hội viên khởi động trước buổi tập" loading="lazy"><figcaption>#WARM-UP</figcaption></figure>
      </div>
    </section>

    <%-- ============================ 5. MINIGAME MOCHI ============================ --%>
    <section class="cy-section" id="game" aria-labelledby="game-title">
      <h2 id="game-title" class="cy-h2">Pumpy <em>né đồ ăn vặt</em></h2>

      <div class="cy-game" id="cyGame" tabindex="0" aria-label="Màn chơi Pumpy né đồ ăn vặt. Nhấn Space để nhảy.">
        <div class="cy-game-hud">
          <span>KCAL NÉ ĐƯỢC <b id="gameScore">0</b></span>
          <span>KỶ LỤC <b id="gameBest">0</b></span>
        </div>
        <div class="cy-game-floor" aria-hidden="true"></div>

        <%-- Pumpy phiên bản cyberpunk: kính visor cyan, băng đô hồng, viền neon --%>
        <svg class="cy-mochi" id="gamePumpy" viewBox="30 120 240 230" aria-hidden="true">
          <g class="cy-mochi-body">
            <path d="M66 178 L72 132 L108 152 Z" fill="#D6F55B" stroke="#141414" stroke-width="6"/>
            <path d="M234 178 L228 132 L192 152 Z" fill="#D6F55B" stroke="#141414" stroke-width="6"/>
            <path d="M40 252 C40 175 92 140 150 140 C208 140 260 175 260 252 C260 312 215 340 150 340 C85 340 40 312 40 252 Z" fill="#D6F55B" stroke="#141414" stroke-width="6"/>
            <path d="M46 180 Q150 150 254 180" fill="none" stroke="#FF3DCB" stroke-width="12"/>
            <rect x="88" y="190" width="124" height="34" rx="10" fill="#0B1030" stroke="#5CF6FF" stroke-width="4"/>
            <rect x="98" y="198" width="44" height="6" rx="3" fill="#5CF6FF" opacity=".8"/>
            <ellipse cx="150" cy="282" rx="50" ry="40" fill="#F1FFB8"/>
            <%-- Xoắn ốc bụng: đường gốc của Pumpy (giống pumpy-mail-hint.jspf) --%>
            <path transform="translate(150 286) scale(0.8) translate(-150 -280)" d="M150.0 278.0 L150.3 278.2 L150.4 278.5 L150.5 278.9 L150.5 279.3 L150.4 279.7 L150.2 280.1 L149.9 280.5 L149.4 280.8 L148.9 281.0 L148.3 281.1 L147.7 281.1 L147.0 281.0 L146.3 280.7 L145.7 280.3 L145.0 279.8 L144.5 279.1 L144.0 278.3 L143.7 277.4 L143.5 276.4 L143.5 275.3 L143.7 274.2 L144.0 273.1 L144.5 272.0 L145.3 271.0 L146.2 270.1 L147.3 269.3 L148.5 268.6 L149.9 268.1 L151.4 267.9 L153.0 267.9 L154.6 268.1 L156.2 268.6 L157.7 269.3 L159.2 270.3 L160.5 271.5 L161.6 273.0 L162.6 274.7 L163.3 276.5 L163.7 278.5 L163.8 280.5 L163.7 282.6 L163.1 284.7 L162.3 286.8 L161.1 288.8 L159.7 290.5 L157.9 292.1 L155.9 293.5 L153.6 294.5 L151.2 295.2 L148.7 295.5 L146.0 295.5 L143.4 295.1 L140.8 294.2 L138.3 293.0 L136.0 291.4 L133.9 289.4 L132.1 287.0 L130.6 284.4 L129.6 281.6 L128.9 278.6 L128.7 275.5 L128.9 272.3 L129.7 269.1 L130.9 266.1 L132.6 263.2 L134.7 260.5 L137.2 258.2 L140.1 256.2 L143.3 254.7 L146.7 253.6 L150.3 253.0 L154.0 253.0 L157.8 253.5 L161.4 254.6 L164.9 256.2 L168.2 258.4 L171.1 261.0 L173.7 264.1 L175.8 267.6 L177.4 271.4 L178.4 275.4 L178.8 279.6 L178.6 283.9 L177.8 288.1 L176.3 292.2 L174.2 296.1 L171.6 299.7 L168.4 302.9 L164.7 305.6 L160.6 307.8" fill="none" stroke="#141414" stroke-width="7"/>
            <path d="M132 244 L141 251 L150 244 L159 251 L168 244" fill="none" stroke="#141414" stroke-width="4"/>
            <g class="cy-mochi-feet"><circle cx="106" cy="334" r="22" fill="#D6F55B" stroke="#141414" stroke-width="6"/><circle cx="194" cy="334" r="22" fill="#D6F55B" stroke="#141414" stroke-width="6"/></g>
          </g>
        </svg>

        <div class="cy-obstacles" id="gameObstacles" aria-hidden="true"></div>

        <%-- Mẫu đồ ăn (JS nhân bản): burger, donut, trà sữa, pizza --%>
        <template id="foodTemplates">
          <svg class="cy-food" data-kcal="550" viewBox="0 0 64 64"><path d="M8 30 Q32 6 56 30 Z" fill="#F5A524" stroke="#141414" stroke-width="3"/><rect x="6" y="30" width="52" height="7" rx="3" fill="#22C55E" stroke="#141414" stroke-width="3"/><rect x="6" y="37" width="52" height="8" rx="3" fill="#7C2D12" stroke="#141414" stroke-width="3"/><path d="M8 45 H56 Q56 56 32 56 Q8 56 8 45 Z" fill="#F5A524" stroke="#141414" stroke-width="3"/></svg>
          <svg class="cy-food" data-kcal="300" viewBox="0 0 64 64"><circle cx="32" cy="34" r="24" fill="#F59E0B" stroke="#141414" stroke-width="3"/><path d="M10 30 Q32 10 54 30 Q50 44 32 40 Q14 44 10 30 Z" fill="#FF3DCB" stroke="#141414" stroke-width="2"/><circle cx="32" cy="34" r="8" fill="#0B1030" stroke="#141414" stroke-width="3"/></svg>
          <svg class="cy-food" data-kcal="450" viewBox="0 0 64 64"><path d="M16 18 H48 L44 58 H20 Z" fill="#E9D5B5" stroke="#141414" stroke-width="3"/><circle cx="26" cy="50" r="3" fill="#141414"/><circle cx="34" cy="52" r="3" fill="#141414"/><circle cx="40" cy="48" r="3" fill="#141414"/><path d="M36 18 L42 2" stroke="#5CF6FF" stroke-width="4"/><rect x="14" y="14" width="36" height="6" rx="3" fill="#FF3DCB" stroke="#141414" stroke-width="3"/></svg>
          <svg class="cy-food" data-kcal="280" viewBox="0 0 64 64"><path d="M8 12 L56 12 L32 58 Z" fill="#FBBF24" stroke="#141414" stroke-width="3"/><path d="M8 12 Q32 4 56 12" fill="none" stroke="#B45309" stroke-width="6"/><circle cx="26" cy="22" r="4" fill="#DC2626"/><circle cx="38" cy="26" r="4" fill="#DC2626"/><circle cx="32" cy="38" r="4" fill="#DC2626"/></svg>
        </template>

        <div class="cy-game-overlay" id="gameOverlay">
          <p class="cy-game-msg" id="gameMsg">Sẵn sàng?</p>
          <p class="cy-game-hint"><kbd>Space</kbd> / <kbd>↑</kbd> / chạm để nhảy qua đồ ăn vặt</p>
          <button type="button" class="btn btn-primary btn-lg" id="gameStart">Chơi ngay</button>
        </div>
      </div>
    </section>

    <%-- ============================ 6. KÊU GỌI ============================ --%>
    <section class="cy-section cy-cta">
      <h2 class="cy-h2">Đến lượt bạn <em>vào sân.</em></h2>
      <div class="cy-cta-row">
        <a class="btn btn-primary btn-lg" href="${ctx}/register">Đăng ký ngay</a>
        <a class="btn btn-ghost btn-lg" href="${ctx}/#pricing">Xem bảng giá</a>
      </div>
    </section>
  </main>

  <%@ include file="/WEB-INF/jsp/common/guest-footer.jspf" %>
</body>
</html>
