# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

Super Gym là một phòng gym giả lập, làm cho đồ án môn SWP391 (nhóm G5). Hệ thống phải được thiết kế như một sản phẩm thật dành cho phòng gym ở Việt Nam. Người đánh giá trực tiếp là giảng viên và hội đồng, xem bản demo trên laptop hoặc máy chiếu.

Khu nội bộ gồm mọi khu cần đăng nhập. Mỗi vai trò làm một việc riêng:

- **Customer (hội viên):** mua gói tập trực tuyến, đặt, đổi hoặc hủy buổi PT, theo dõi lịch tập, trạng thái gói và lịch sử giao dịch, xem giáo án và thực đơn, gửi góp ý.
- **PT (huấn luyện viên):** đăng lịch rảnh, theo dõi lịch dạy và khách được giao, đánh dấu khách vắng, đổi, chuyển hoặc hủy buổi, ghi chú riêng về khách, soạn giáo án và thực đơn, gửi đơn nghỉ phép.
- **Receptionist (lễ tân):** thao tác tại quầy. Quản lý hồ sơ khách và tạo tài khoản khách, bán gói tại quầy, thu tiền, check-in khách, bảo lưu gói, báo và đóng yêu cầu bảo trì, chốt két cuối ca.
- **Manager (quản lý):** cấu hình tham số và giờ mở cửa, quản lý danh mục gói và voucher, quản lý hồ sơ nhân sự, danh mục cơ sở vật chất và bài blog. Duyệt các yêu cầu xóa khách, nghỉ phép và bảo trì. Xem giao dịch toàn hệ thống, đọc góp ý, gửi thông báo, xem dashboard tổng hợp.
- **Admin (quản trị):** tạo và quản lý tài khoản nội bộ, xem nhật ký kiểm toán (audit log) và báo cáo vận hành.

Cập nhật phạm vi ngày 14/10/2026: gồm khu công khai (homepage, giới thiệu, blog, đăng nhập/đăng ký) và khu nội bộ. Homepage phục vụ lựa chọn dịch vụ và đăng ký; khu nội bộ ưu tiên hoàn thành tác vụ, giảm rối và trang trí. Đợt hiện tại tinh chỉnh nhận diện và các component chung, đồng thời làm rõ lịch theo ngày, bộ lọc và xác nhận hủy trong My PT Sessions; giữ route và nghiệp vụ hiện có.

## Product Purpose

Số hóa toàn bộ vận hành của một phòng gym: bán gói (online qua VNPay, hoặc tại quầy bằng tiền mặt/chuyển khoản), check-in, đặt và quản lý buổi PT, bảo lưu gói, nghỉ phép, bảo trì, voucher, góp ý và báo cáo. Đặc tả chuẩn nằm ở `docs/SRS - G5.docx.md`, với 67 màn hình được liệt kê ở `docs/project-structure.md`.

Thành công nghĩa là mỗi vai trò làm xong việc của mình nhanh, không nhầm lẫn, và bản demo thuyết phục người chấm rằng đây là một sản phẩm vận hành được thật.

## Operating Context

- Lễ tân thao tác liên tục tại quầy trong suốt ca làm: check-in, bán gói, thu tiền, chốt két cuối ca.
- PT xem lịch dạy theo ngày và tuần, xử lý từng buổi tập.
- Manager và Admin xem dashboard, duyệt yêu cầu, cấu hình hệ thống.
- Customer tự phục vụ: mua gói, đặt buổi PT, theo dõi lịch tập.
- Tích hợp ngoài: VNPay (thanh toán online), Email/OTP (mã xác minh khi đăng ký, khôi phục mật khẩu, nhận hồ sơ).
- Ngôn ngữ giao diện là tiếng Việt (GBR-01). Mã thông báo MSG01–61 hiển thị bằng bản dịch tiếng Việt.

## Capabilities and Constraints

- **Stack cố định:** Java 17, Jakarta Servlet 6, JSP 3.1/JSTL 3, Tomcat 10.1, SQL Server. Frontend là HTML/CSS/JS thuần, không framework và không có bước build cho frontend.
- **CSS ba lớp:** `tokens.css` → `common.css` → `brand-system.css` → `css/pages/<vai-trò>/<màn>.css`. Mọi CSS chỉ được dùng `var(--...)` từ `tokens.css`; cấm hard-code màu, px, font, radius hay shadow. Chỉ Leader sửa `tokens.css`.
- **Theme theo vai trò** gắn bằng class trên `<body>`: `theme-member`, `theme-staff`, `theme-manager`, `theme-admin` (cùng `theme-guest`/`theme-guest-dark` cho khu công khai). Bản redesign phải giữ được việc mỗi vai trò nhìn ra là một khu riêng.
- Thư viện dùng chung có sẵn: `common.js` (toast, hộp xác nhận, kiểm tra form, AJAX, đếm ngược, định dạng tiền và ngày, phân trang), `head.jspf`, `flash-toast.jspf`, và các layout `layout-<vai-trò>.jspf`.
- Quy tắc code frontend nằm ở `docs/codeImplementationFrontend.md`: chống XSS bằng `c:out`/`escapeHtml`, phân biệt rõ trạng thái khung, rỗng, lỗi và đang tải, modal hỗ trợ Escape và trả focus, JS viết rõ ràng, không dùng cú pháp "khôn".
- Màu badge trạng thái theo GBR-28: `badge-success`, `badge-warning`, `badge-danger`, `badge-neutral`.
- **Prototype cũ** (`Prototype/prototype`) và giao diện hiện tại chỉ là tài liệu tham khảo về luồng và nội dung, **không ràng buộc về hình thức**. Được phép thay toàn bộ diện mạo của khu nội bộ.

## Brand Commitments

- Giữ tên **Super Gym**. Nhận diện triển khai dùng `src/main/webapp/images/brand/super-gym-mark.svg` và `super-gym-wordmark.svg`: S cam đặc #FF4F2B, đầu cuộn Ionic, gợn biển và xoáy mực trời; không dấu mặt trời. SVG thay logo PNG ở các vị trí nhận diện và favicon.
- Các vai trò nhận ra được qua tên khu, menu, nội dung và tác vụ, cùng thuộc một hệ thống thị giác. HintBox dùng mẫu PT Dashboard chung; badge giữ nghĩa xanh thành công/vàng cảnh báo/đỏ lỗi/trung tính.
- Font hiện hành: **Inter Tight / Inter / JetBrains Mono** cho tiêu đề / nội dung / nhãn ngắn. Chừa đủ khoảng cho dấu tiếng Việt, không ép ngang chữ; số dùng tabular-nums khi cần đối chiếu.
- Homepage giữ hero `hero-barbell.jpg` và hiệu ứng gốc, aqua tươi đậm hơn khoảng 19%. Nội bộ dùng navy còn sắc đại dương, vân mực comic rất mờ (chấm tram, gạch cọ khô; thay ảnh gang gỉ từ 15/10) và ripple nhẹ phía sau panel đặc. Light mode trắng be #F5F2EB, không artwork/sóng/weather nền. Nút chính cam đặc, không gradient hoặc halo.
- Sidebar màu biển đặc #153D4E, trạng thái chọn màu đặc; tay nắm navy và mũi tên sáng. Ánh xanh theo con trỏ và gió nét SVG hiện khi hover; reduced motion/chế độ nhẹ giữ nét gió tĩnh. Avatar PT dùng viền mảnh như các zone, không vòng cam dày.
- Auth dùng nét SVG nguyên bản: nam vận động viên bên trái, nữ vận động viên trưởng thành đội nguyệt quế ngồi trên wordmark; form trung tâm yên, rõ. Nét khắc chạy một lần, sóng dừng khi nhập và tuân theo giảm chuyển động.
- Pumpy giữ silhouette mũm mĩm, tay ngắn tròn, xoáy bụng và **miệng zigzag nguyên bản**; chỉ đổi mắt để biểu cảm. Thân #294F78, xoáy #24496E, tạ/bong bóng #203D60, chữ kem #F2FBF8. Loading thiên hà đen trắng giữ Pumpy/tạ navy. Nội dung tương tác thân thiện, có 12 câu; dẫn nguồn khi kể từ nguyên gym/gymnasium, không suy rộng thành nguồn gốc mọi hoạt động tập luyện.
- Ảnh nội dung là ảnh chụp thật; ảnh stock chỉ minh họa, có sidecar nguồn cho asset mới. Giữ cảnh canvas mặt trời/tạ riêng trang About, giọt rơi tới thanh tạ. Giữ animation Pumpy, sân gym và thời tiết đã có, cùng cơ chế tạm dừng/giảm chuyển động; ảnh cùng trang không lặp thành nhiều khối nội dung.
- Email xác minh và thông báo chung dùng cùng palette navy/cam/kem; giữ nội dung, thời hạn OTP và cấu hình gửi hiện có.

## Evidence on Hand

- Ảnh blog thật: `src/main/webapp/images/blog/`.
- Dữ liệu mẫu: `database/2_Seed_Data.sql`.
- Chưa có lời chứng thực, số liệu khách hàng hay đối tác thật. Không được bịa ra.

## Product Principles

- **Tác vụ trước, trang trí sau:** mỗi màn nội bộ phục vụ một công việc cụ thể của một vai trò. Thông tin cần để ra quyết định phải nằm ở đầu màn.
- **Vai trò nhận ra ngay:** người dùng luôn biết mình đang ở khu nào, nhưng các khu vẫn thuộc cùng một hệ thống Super Gym.
- **Trung thực về trạng thái:** đang tải, rỗng, lỗi và chưa hoàn thiện là bốn trạng thái khác nhau, không đánh tráo cho nhau.
- **Lời lẽ theo góc nhìn người dùng:** chữ trên giao diện nói bằng ngôn ngữ của khách và nhân viên phòng gym, không dùng từ kỹ thuật của hệ thống.
- **Đủ chất để thuyết phục khi demo:** chăm chút như sản phẩm thật, đọc rõ trên máy chiếu.

Hướng thị giác hiện tại là **Aegean Ink** theo brief 14/10; chi tiết token và biến thể nằm ở `DESIGN.md`. Không thêm dấu cộng trang trí hoặc nét tốc độ gây nhiễu sân gym. Thời tiết mất kết nối dùng dữ liệu thật gần nhất trong ngày, có giờ lấy mẫu và nhãn rõ ràng. Kiểm chứng gồm preview tĩnh từ source trên desktop/mobile, JS/SVG/diff và biên dịch javac tập trung cho EmailGateway; chưa xác nhận Tomcat, DB, đăng nhập, POST hủy buổi hoặc gửi email thật.
