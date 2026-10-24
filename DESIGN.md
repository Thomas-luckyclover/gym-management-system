---
name: Super Gym · Aegean Ink
description: Tác vụ rõ trên nền mực đại dương, năng lượng cam và nét khắc thể thao.
colors:
  action: "#FF4F2B"
  action-hover: "#FF693F"
  action-soft: "#FF986B"
  action-ink: "#080F1B"
  ember: "#ED3845"
  navy-ground: "#0A1E31"
  navy-panel: "#102A40"
  navy-raised: "#18364F"
  text-main: "#F0F4F8"
  text-soft: "#B1C4D7"
  text-muted: "#94ADC5"
  line: "rgba(170, 195, 220, 0.2)"
  focus: "#AAC8E8"
  sidebar: "#153D4E"
  sidebar-hover: "#225267"
  sidebar-active: "#2A586D"
  sidebar-ink: "#FFF1E7"
  light-ground: "#F8E9D2"
  light-panel: "#FFF8EE"
  light-raised: "#F4E3C8"
  light-text: "#2A1E14"
  home-aqua: "#23ACB4"
  home-aqua-soft: "#67BABF"
  home-ocean: "#055762"
  home-deep: "#032731"
  auth-panel: "#0E1A2C"
  auth-input: "#080F1B"
  email-header: "#0C2337"
  pumpy-body: "#294F78"
  pumpy-swirl: "#24496E"
  pumpy-plate: "#203D60"
  pumpy-cream: "#F2FBF8"
  success-dark: "#8CE8B4"
  success-light: "#1F6B41"
  success-tint: "rgba(76, 214, 140, 0.14)"
typography:
  display:
    fontFamily: '"Inter Tight", "Inter", "Segoe UI", sans-serif'
    fontSize: "clamp(3.8rem, 7.2vw, 7.6rem)"
    fontWeight: 700
    lineHeight: 1
    letterSpacing: "-0.005em"
  headline:
    fontFamily: '"Inter Tight", "Inter", "Segoe UI", sans-serif'
    fontSize: "clamp(2.8rem, 5vw, 5.2rem)"
    fontWeight: 700
    lineHeight: 1.2
    letterSpacing: "-0.005em"
  title:
    fontFamily: '"Inter Tight", "Inter", "Segoe UI", sans-serif'
    fontSize: "1.5rem"
    fontWeight: 700
    lineHeight: 1.2
    letterSpacing: "-0.02em"
  body:
    fontFamily: '"Inter", "Segoe UI", system-ui, sans-serif'
    fontSize: "1rem"
    fontWeight: 400
    lineHeight: 1.55
    letterSpacing: "0"
  label:
    fontFamily: '"JetBrains Mono", ui-monospace, "Cascadia Mono", Consolas, monospace'
    fontSize: "0.8125rem"
    fontWeight: 500
    letterSpacing: "0.06em"
rounded:
  xs: "4px"
  sm: "8px"
  button-default: "calc(12px * 0.81)"
  internal-control: "2px"
  panel: "4px"
  pill: "999px"
  circle: "50%"
spacing:
  space-1: "4px"
  space-2: "8px"
  space-2-5: "10px"
  space-3: "12px"
  space-4: "16px"
  space-4-5: "20px"
  space-5: "24px"
  space-6: "32px"
  space-8: "48px"
  space-10: "64px"
  space-12: "80px"
components:
  button-primary:
    backgroundColor: "{colors.action}"
    textColor: "{colors.action-ink}"
    rounded: "{rounded.internal-control}"
    padding: "10px 20px"
  button-primary-hover:
    backgroundColor: "{colors.action-hover}"
  button-outline:
    backgroundColor: "{colors.navy-panel}"
    textColor: "{colors.action-soft}"
    rounded: "{rounded.internal-control}"
    padding: "10px 20px"
  button-ghost:
    backgroundColor: "transparent"
    textColor: "{colors.text-main}"
    rounded: "{rounded.internal-control}"
    padding: "10px 20px"
  input-field:
    backgroundColor: "{colors.navy-panel}"
    textColor: "{colors.text-main}"
    rounded: "{rounded.internal-control}"
    padding: "10px 12px"
  nav-item-active:
    backgroundColor: "{colors.sidebar-active}"
    textColor: "{colors.sidebar-ink}"
    rounded: "{rounded.xs}"
    padding: "10px 12px"
  badge-success:
    backgroundColor: "{colors.success-tint}"
    textColor: "{colors.success-dark}"
    rounded: "{rounded.pill}"
    padding: "4px 10px"
  session-day:
    backgroundColor: "{colors.navy-panel}"
    textColor: "{colors.text-main}"
    rounded: "{rounded.internal-control}"
    padding: "24px"
  hintbox:
    backgroundColor: "{colors.navy-raised}"
    textColor: "{colors.text-main}"
    rounded: "{rounded.internal-control}"
    padding: "12px 16px"
---

# Design System: Super Gym · Aegean Ink

## Overview

**Creative North Star: "Aegean Ink"**

Tác vụ ở tiền cảnh. Navy giữ sắc biển, chất sắt mờ và gợn nước nhẹ; cam đặc dẫn mắt tới hành động. Nét khắc Hy Lạp nằm trong logo và auth, ảnh thể thao thật nằm trong nội dung. Light mode là nền trắng be yên, panel rõ và chữ tối.

Đây là bản ghi hệ thống đã triển khai theo brief 14/10/2026, thay các cam kết thị giác cũ ngày 13/10. Giá trị lấy từ cascade hiện tại của `tokens.css`, `common.css`, `brand-system.css` và CSS trang; frontmatter mô tả ngữ cảnh mặc định, các biến thể được nêu bên dưới. Route, dữ liệu và luồng nghiệp vụ giữ theo sản phẩm hiện có.

**Key Characteristics:**

- Panel đặc màu, dữ liệu và thao tác đọc rõ.
- Cam sống động, navy còn sắc đại dương, light mode trắng be.
- Logo S đầu cuộn Ionic, nét khắc auth và Pumpy navy nguyên tạo hình.
- Chuyển động nhỏ, có giới hạn và tôn trọng chế độ nhẹ.

## Colors

### Primary

Cam hành động dùng cho CTA và chữ S; chữ tối trên nền cam. Hover nội bộ/header/auth dùng `action-hover`; CTA homepage giữ hover `ember` trong CSS hiện hành. Không gradient hoặc halo trên nút chính.

### Secondary

Navy tạo nền, panel và lớp nổi nội bộ. Sidebar dùng màu biển đặc cùng các trạng thái hover/active đặc. Aqua riêng homepage giữ sắc tươi cũ nhưng đậm hơn khoảng 19%; không áp palette navy Pumpy cho nền homepage.

### Neutral

Chữ chính, chữ phụ và đường chia có vai trò riêng. Light mode đổi nền, panel, chữ và viền theo `html[data-theme="light"]`, giữ sidebar navy. Pumpy dùng thân navy, xoáy bụng xanh đậm, tạ/bong bóng navy và chữ kem trong frontmatter.

**The Semantic Color Rule.** Xanh thành công, vàng cảnh báo, đỏ lỗi và màu trung tính giữ nghĩa nghiệp vụ; không đổi badge thành aqua hoặc cam chỉ để khớp thương hiệu.

## Typography

Inter Tight cho tiêu đề và số nổi bật; Inter cho nội dung, form và điều khiển; JetBrains Mono cho ngày, mã và nhãn ngắn. Cỡ chữ phụ thấp nhất trong thang là 13px; nội dung chính 16px, phụ 14–15px. Số tiền và giờ dùng tabular-nums.

Display/headline trong frontmatter dành cho khu công khai. Tiêu đề vận hành gọn hơn; auth và tiêu đề khối My PT Sessions dùng Inter theo CSS trang. Tracking tiêu đề đã có trong source; không ép ngang chữ và không cắt dấu tiếng Việt. Chữ mono in hoa chỉ dành cho nhãn ngắn.

## Layout

CSS theo thứ tự `tokens.css` → `common.css` → `brand-system.css` → CSS trang. Thang khoảng cách dùng bước 4px làm nền, xen 10px/20px cho điều khiển. Nội dung homepage tối đa 1360px, gutter 24px; sidebar rộng 272px. Guest header có ba vùng logo/liên kết/tài khoản, chuyển menu ở 1080px và gọn thêm ở 640px.

Khu vận hành đặt topbar, bộ lọc, dữ liệu và hành động theo thứ tự ổn định; gutter mobile 16px. Sidebar có tay nắm mũi tên để thu gọn, giữ nền navy và mũi tên sáng ở cả hai theme. Drawer, skip link, focus và Escape dùng cơ chế chung hiện có. Các breakpoint là của từng component, không phải một lưới ép cho mọi trang.

## Elevation & Depth

Panel nội bộ là lớp màu đặc, viền mảnh và bóng thấp; bản đồ thành phố nét mực (images/brand/city-map-ink.webp, chỉ nét màu giấy, tan dần lên trên để không đè khối chào/thời tiết) cùng ripple nằm sau vùng tác vụ. Light mode tắt artwork, ripple và weather nền. Auth dùng form đặc màu; header khách vẫn giữ lớp kính hiện hành. Shadow, focus, motion và breakpoint chi tiết nằm trong `.impeccable/design.json`.

**The Foreground Rule.** Không để ảnh hoặc hiệu ứng xuyên qua bảng, form và chữ cần đọc.

## Shapes

Khung nội bộ chung dùng góc 4px. Cả năm theme nội bộ override radius điều khiển về 2px và radius-md về 4px; thẻ ngày My PT Sessions cùng HintBox kế thừa radius-sm 2px. Auth giữ radius-sm gốc 8px; header/auth/homepage dùng nút góc 4px. Radius nút gốc trong frontmatter chỉ áp dụng khi không có override. Badge và capsule loading bo viên thuốc; avatar tròn. Component frontmatter mô tả theme nội bộ tối. Không áp một radius cho mọi ngữ cảnh.

## Components

- **Nút và ô nhập:** CTA cam đặc, không halo; trạng thái phụ dùng viền hoặc nền trong suốt. Chiều cao nút cơ sở 38px, nút lớn/ô auth 46px, nav/header 44px. Input giữ label, focus, lỗi và trạng thái khóa rõ.
- **Sidebar:** nền `sidebar` đặc, active dùng `sidebar-active` đặc; ánh xanh theo con trỏ là lớp tương tác. SVG `sea-breeze.svg` hiện khi hover: full mode chuyển động, reduced motion/`gms-lite` giữ nét tĩnh. Bản cuối dùng nét xanh sáng 1.3px, opacity 0.78, quãng trôi 64px và chu kỳ 3.4s. Avatar PT dùng đúng border 2px và outline 1px của avatar các zone; không vòng cam dày.
- **HintBox:** mẫu PT Dashboard là chuẩn chung trong `common.css`: `.pt-info`, `.pt-info-trigger`, `.pt-info-popover`. Tooltip gói và biểu đồ dùng cùng surface, viền, padding và chữ; HintBox dùng chung xử lý chạm, Escape và bấm ngoài trong `common.js`. Ở màn dưới 700px, HintBox chuyển thành hộp cố định gần đáy.
- **My PT Sessions:** bỏ subtitle; ba tab Sắp tới/Lịch sử/Gói PT của tôi và một bộ lọc chung. Mỗi ngày là panel riêng với nhãn ngày; mỗi buổi là hàng có divider và hành động hủy riêng. Cột giờ 168px trên màn rộng, chuyển một cột dưới 700px. Dialog hủy nêu đúng ngày, giờ, PT đã chọn; giữ POST `action=cancel` + `sessionId` về route hiện tại. PT/gói/trạng thái và cảnh báo quyền tập của buổi sắp tới luôn hiện; thời hạn, mã gói, thời điểm hủy, giải thích và lý do nằm trong `details.my-pt-session-details` đóng mặc định, dùng `dl/dt/dd` với lưới metadata hai cột desktop/một cột mobile.
- **Logo:** `images/brand/super-gym-mark.svg` và `super-gym-wordmark.svg` thay PNG trong các vị trí triển khai. Chữ S cam đặc chiếm ưu thế, đầu cuộn Ionic, gợn biển và xoáy mực trời; không mặt trời. Wordmark vẽ bằng path, không phụ thuộc font tải ngoài.
- **Auth "Gymnasion Ink" (15/10):** nền mực `#080F1E` như homepage, bảng màu gọn: mực, kem, cam (CTA, liên kết, nét khắc), aqua chỉ cho focus. Tranh gốm Hy Lạp vẽ lối comic: trái là chiến binh Sparta mũ Corinthian nâng hai tạ đá trong lòng chén kylix viền hoa văn chìa khóa (chòm mũ dựng đứng và áo choàng màu cam); phải là cột Ionic khối mực viền kem, khăn tập đất nung vắt đỉnh, cặp tạ đá cạnh chân cột; chân trang dải meander mờ. Nữ hoàng đội nguyệt quế ngồi ở mép phải wordmark, chân buông ra ngoài chữ, không che logo. SVG sinh bằng script, viết thẳng vào `auth-artwork.jspf`/`auth-muse.jspf`. Hiện một lần (900ms, rise + fade), reduced motion/chế độ nhẹ tắt animation; dưới 900px tranh mờ còn 45%.
- **Pumpy và loading:** giữ silhouette mũm mĩm, bàn tay ngắn tròn, bụng xoáy và miệng zigzag nguyên bản ở mọi biểu cảm; chỉ mắt thể hiện cảm xúc. Homepage có 12 câu tương tác có nội dung. Bong bóng PT Coach dùng navy, chữ và gạch chân kem, giữ hình/copy cũ. Loading là capsule thiên hà đen trắng, Pumpy và tạ vẫn navy. Từ nguyên “gym”/“gymnasium” chỉ giải thích nguồn từ Hy Lạp; không khẳng định toàn bộ hoạt động tập luyện do Hy Lạp phát minh. Nguồn: [gym](https://www.etymonline.com/word/gym), [gymnasium](https://www.etymonline.com/word/gymnasium).
- **Email giao dịch:** mẫu xác minh và thông báo chung trong `EmailGateway.java` dùng header navy, brand/khối OTP cam, thân kem và nền ngoài trắng be. Email dùng Arial/Helvetica fallback theo template; giữ nội dung, hạn OTP và cấu hình SMTP hiện có.
- **Pumpy đi thuyền (About, 15/10):** góc phải mặt nước hero, Pumpy ngồi thuyền buồm mực có dải cam, sáng dịu (brightness 0.74) trong vùng tối như ngược sáng hoàng hôn. Thuyền trôi ra phía mặt trời 9s, dừng ngắm, trôi về 9s; về đúng chỗ cũ thì than kẹt vòng lặp thời gian và hiện nút "Giải cứu Pumpy · Đăng ký". Thoại tự chạy là trang trí (aria-hidden); bấm Pumpy thì câu thoại đọc qua aria-live. Reduced motion/chế độ nhẹ: thuyền đứng yên, chỉ nói khi bấm; hero khuất hoặc tab ẩn thì tạm dừng. File: `_pumpy-sailor.jspf`, `about-pumpy.js`, mục "Pumpy đi thuyền" trong `about.css`, token `--sailor-*`.
- **Ảnh và cảnh riêng trang:** giữ `hero-barbell.jpg`, `home.css` và `home-motion.js` như HEAD. Gallery thứ ba dùng `strip-endurance.jpg` ([Pexels 14623740](https://www.pexels.com/photo/a-woman-doing-a-deadlift-14623740/)), không đổi ảnh hero. About có bốn ảnh giá trị Pexels thật; giữ canvas mặt trời/tạ, giọt rơi chạm thanh tạ thực tế. Bìa khởi động dùng ảnh stretching thật. Ảnh mới có sidecar `.json` ghi nguồn/cách tạo và mục đích; stock là ảnh minh họa, không chứng minh cơ sở vật chất thật.

Motion gợn nền (48s), gió sidebar (3.4s) và thiên hà loading (12s) chỉ tạo không khí. Giữ điều khiển tạm dừng sân gym/thời tiết và fallback dữ liệu thật hiện có. Homepage giữ bộ hiệu ứng gốc đã khôi phục.

Phạm vi kiểm chứng của đợt này: static preview dựng từ source ở desktop/mobile, kiểm tra cú pháp JS, parse SVG và diff; kiểm tra biên dịch javac tập trung cho EmailGateway đã qua. Chưa xác nhận chạy Tomcat, kết nối DB, đăng nhập thật, thực thi POST hủy buổi hoặc gửi email thật; không coi preview là kiểm thử nghiệp vụ.

## Arena (24/10, 4 màn + xác thực)

PT Dashboard, Lịch dạy PT, My PT Sessions và My Bookings dùng bảng màu đĩa tạ thi đấu (token `--arena-*` trong `tokens.css`, lớp áp dụng ở mục "ARENA" cuối `common.css` và mục Arena cuối CSS từng trang). Các khu Quản lý, Lễ tân, Admin và Trang cá nhân khách không đổi.

- **Màu:** đĩa 20kg xanh `#24468F` là nền sân và topbar; đĩa 25kg đỏ `#FF4F2B` (cùng màu nút trang chủ và sidebar) chỉ cho một việc nặng nhất mỗi màn; chữ ember trên khối tối dùng `#FF7A5C`, trên nền sáng dùng `#C2361A` (nút chính, hôm nay, ô Cần chú ý); phấn magie `#EEF2F8` là khối đọc, ô con bên trong trắng; thép `#A7B3C9` là chữ phụ trên khối tối. Màu nghĩa xanh lá/vàng/đỏ giữ nguyên.
- **Khối tối có đèn:** khối chào, Kết quả tháng, dải đầu gói và thanh Chờ công bố dùng `--arena-900` + vùng sáng xanh ở góc trên + vạch sáng 1px. Chế độ nhẹ bỏ vùng sáng.
- **Điểm nhớ duy nhất:** vân nhám thanh đòn 8px trên đỉnh khối chào PT (hai lớp sọc chéo CSS). Không dùng ở chỗ khác.
- **Chữ:** tên người dùng trong khối chào to, đậm 800, kèm chấm vuông ember; "CHÀO," nhỏ màu thép. Tiêu đề khối Inter Tight 800.
- **Hình:** khối 6px, ô 4px, chip/nhãn 2px; avatar vẫn tròn. Khe ghép 12px. Khối sáng không viền, dùng bóng tông xanh.
- **Nhịp khoảng cách:** bước 4px; khe giữa khối 12px, khe giữa các ô 8px, lề trong khối 24px (điện thoại 16px). Tiêu đề topbar thẳng mép trái với khối nội dung ở mọi bề ngang.
- **Tiếp cận (WCAG 2.1 AA):** quét axe-core 0 lỗi trên 5 màn ở 1440px và 390px; zoom 200% không tràn ngang. Vòng focus 2 tông (vạch xanh + viền trắng, trên khối tối là xanh nhạt). Điều khiển trên điện thoại cao tối thiểu 44px; nút (i) và nút hiện mật khẩu giữ cỡ nhỏ nhưng có vùng chạm 44px. Viền nút phụ `#7D8FAE` ≥ 3:1 trên trắng. Ô tài khoản thu gọn trên điện thoại vẫn giữ tên cho trình đọc màn hình. Chip trạng thái có viền cùng tông.
- **Sidebar và chuyển động:** sidebar giữ nguyên (ember cũ `#FF4F2B` ghim ở `--arena-sidebar-ember`). Không thêm animation; hiệu ứng hiện dần, bấm lún và gió sidebar có sẵn giữ nguyên.
- **Xác thực:** nền, thẻ form, ô nhập và focus đổi qua token `--auth-*`; đất nung và áo choàng của tranh gốm đổi sang màu đặc bằng đúng màu cũ để tranh không ngả tím trên nền mới.

## Do's and Don'ts

### Do:

- Do giữ panel đặc và dữ liệu ở tiền cảnh.
- Do dùng token theo đúng theme và giữ nghĩa màu trạng thái.
- Do giữ logo S, silhouette Pumpy và miệng zigzag nguyên bản.
- Do ghi nguồn ảnh mới và tôn trọng reduced motion/chế độ nhẹ.

### Don't:

- Don't thêm gradient hoặc halo vào nút chính hay nền/active sidebar.
- Don't thêm artwork, sóng hoặc weather nền vào light mode.
- Don't dùng lại logo PNG, dấu mặt trời trong logo hoặc vòng avatar PT cam dày.
- Don't đổi hero/hiệu ứng homepage đã khôi phục hoặc biến ảnh minh họa thành bằng chứng sản phẩm.
