# Super Gym — Ember / Deep Aqua

Cập nhật 13/10/2026 theo yêu cầu redesign cả khu công khai và nội bộ; thay quyết định cũ về font và logo. HTML/CSS/JS thuần, JSP/JSTL, giữ luồng dữ liệu và route hiện có.

## Nhận diện

Cam cháy `--brand-sunset` (#FF5D2E), đỏ than `--brand-ember-red` (#F13C2D), aqua sâu `--brand-ocean-deep` (#04303C), aqua sáng `--brand-aqua` (#2BD4DE). Sắc đỏ trong cam tăng theo yêu cầu mới; đại dương sâu hơn. Homepage hướng đến tỷ trọng 65% ember / 35% aqua trong các vùng nhận diện. Khu vận hành dùng nền aqua tối, surface rõ và ember cho hành động chính. Chế độ sáng vẫn hỗ trợ.

Logo: huy hiệu vòm góc bo tròn màu cyan với chữ SUPER GYM cam/đỏ và biểu tượng đòn tạ ở giữa, tách nền trong suốt (`images/logo.png` và `images/logo-mark.png`). Dùng đồng bộ cho Header/Footer khách, các màn Auth, Sidebar dock nội bộ và Favicon. Giữ tương thích với các fallback SVG trong `images/brand/`.

Pumpy giữ tạo hình: thân aqua đậm #18B8C0, mọi xoáy bụng xanh #087A80; tạ trắng lõi cam, tạ đen lõi aqua. Áp dụng cả auth, lỗi, coach, empty state và loading. Không dùng màu thương hiệu để thay đổi nghĩa badge trạng thái.

## Typography và khoảng thở

Be Vietnam Pro cho nội dung, menu, form và dữ liệu vận hành; Barlow Condensed 600–800 cho tiêu đề chiến dịch. Không dùng Anton, không tracking âm, không scale ngang chữ. Tiêu đề nội bộ không viết hoa dài hoặc dùng font condensed. Dấu tiếng Việt phải đủ khoảng trên/dưới. Số tài chính dùng tabular-nums và hiển thị ngay giá trị thật.

## Homepage — Persuade

Ảnh thể thao lớn, bố cục editorial, một tiêu đề và hai hành động rõ. Thanh nav theo ba vùng logo / liên kết / tài khoản; thu gọn trên tablet. Nhịp section luân phiên khoảng thở, ảnh, hàng dịch vụ và bảng giá aqua. Bảng giá tự xuống hàng tối đa 3 cột desktop / 2 tablet / 1 mobile, hàng cuối căn giữa: 5 gói = 3+2, 6 gói = 3+3. Giá, giờ hoạt động và PT lấy từ dữ liệu thật. Không tạo lời chứng thực, chỉ số hiệu quả hoặc đối tác giả.

Footer giữ Instagram/Facebook/TikTok dưới dạng biểu tượng không bấm vì chưa có URL chính thức. Bản đồ lazy-load và link chỉ đường cùng truy vấn 25 Cầu Giấy, Hà Nội.

Ảnh mỗi khối là một nội dung riêng; không nhân bản ảnh hero thành bóng mờ. Ánh ember/aqua từ lớp màu CSS thay vì ảnh lặp. Chữ blog chỉ underline khi tương tác, không gạch chân toàn card.

## Khu nội bộ — Operate

Sidebar thu gọn bằng mũi tên ở mọi kích thước, trả không gian cho màn chính. Trạng thái đang chọn màu bạc, hover ánh sáng nhẹ theo con trỏ. Drawer đóng bằng nền ngoài/Escape, giữ focus trong menu, `inert` vùng không hoạt động. Có skip link tới nội dung chính. Topbar, bộ lọc, dữ liệu và thao tác theo thứ tự ổn định; vùng bấm đủ lớn.

Header PT/Customer giữ lời chào, sân gym chuyển động và hiệu ứng thời tiết trong thẻ theo yêu cầu người dùng. Nhiệt độ và dữ liệu thật xuất hiện ngay; có điều khiển tạm dừng hiệu ứng. Motion không cản tác vụ, loops dừng khi offscreen/tab ẩn và theo prefers-reduced-motion. Khối 5 trạng thái hoa hồng PT căn giữa 2 ô hàng cuối. Mọi khối giữ phân biệt đang tải / rỗng / lỗi / chưa hoàn thiện.

## Motion và hiệu năng

Homepage dùng Web Animations API và IntersectionObserver: entrance tiêu đề, reveal ảnh và parallax hero tối đa 20px. Nội dung hiển thị ngay khi JavaScript hoặc animation không chạy. Motion tắt theo prefers-reduced-motion, loops dừng khi offscreen/tab ẩn. Blur chỉ ở lớp ảnh/lớp nền giới hạn, không trên nội dung cần đọc. Không scroll hijacking hoặc pin section dài. Ảnh dưới fold lazy-load và có kích thước dự trữ.

## Cấu trúc triển khai

`tokens.css` → `common.css` → `brand-system.css` → CSS riêng trang. Token là nguồn giá trị; brand-system quản lý nhận diện chung, guest header và auth. Common giữ component vận hành; mỗi trang chỉ sở hữu bố cục của mình. JS dùng lại API form/toast/confirmation đang có, render text động qua c:out/textContent/escapeHtml.

Nguồn tham khảo nghệ thuật: ảnh người dùng cung cấp; Adidas (ảnh thể thao và CTA), Equinox (nhịp editorial), Gymshark (phân cấp thương mại). Không dùng logo hoặc nhận diện độc quyền của các thương hiệu này.
