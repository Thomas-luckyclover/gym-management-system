# GMS — Gym Management System (Super Gym)

**Môn học:** SWP391 — Software Development Project · **Nhóm:** G5 · **Trường Đại học FPT**

## Giới thiệu dự án (Project Introduction)

GMS là hệ thống quản lý phòng gym **Super Gym**, số hóa toàn bộ vận hành hằng ngày của một phòng tập:
bán gói tập online (thanh toán VNPay) và tại quầy, check-in hội viên, đặt và quản lý buổi tập với huấn luyện viên (PT),
bảo lưu gói, nghỉ phép nhân viên, bảo trì thiết bị và báo cáo doanh thu.

Hệ thống phục vụ 6 nhóm người dùng: **Khách vãng lai, Hội viên, Huấn luyện viên (PT), Lễ tân, Quản lý, Quản trị viên**.

**Công nghệ:** Java 17 · Jakarta Servlet 6 / JSP 3.1 / JSTL 3 · Apache Tomcat 10.1 · SQL Server · Maven · HTML / CSS / JavaScript thuần · VNPay (sandbox) · Gmail SMTP.

**Tài liệu dự án (SRS, thiết kế, báo cáo):** [Google Drive](https://drive.google.com/drive/folders/1FJVCnXGreeBdyfikNVIl7XMqAY3TFDfK)

## Chức năng chính

| # | Nhóm chức năng | Nội dung |
|---|---|---|
| 1 | Authentication | Đăng nhập, chọn vai trò, đăng xuất, đăng ký tài khoản, quên mật khẩu, cài đặt tài khoản, thông báo trong ứng dụng |
| 2 | Public Website | Trang chủ, giới thiệu, bảng giá, blog |
| 3 | System Account Management | Quản lý tài khoản hệ thống, nhật ký thao tác (audit log) |
| 4 | Configuration & Catalogue | Tham số vận hành, giờ mở cửa, danh mục gói tập / gói PT, voucher |
| 5 | Staff & Leave | Hồ sơ nhân viên, gửi và duyệt đơn nghỉ phép |
| 6 | Customer Management | Hồ sơ hội viên (kể cả hồ sơ phụ thuộc), duyệt xóa hội viên, check-in, bảo lưu gói |
| 7 | Sales & Payment | Mua gói online qua VNPay, bán hàng tại quầy, bán gói PT, ca làm việc & đối soát tiền, lịch sử giao dịch |
| 8 | Facilities & Maintenance | Danh mục cơ sở vật chất / thiết bị, báo hỏng và duyệt bảo trì |
| 9 | Customer Training | Trang cá nhân hội viên, gói của tôi, lịch tập, chỉ số cơ thể, đặt buổi PT lẻ, giáo án, thư viện bài tập |
| 10 | PT Teaching | Dashboard PT, lịch dạy (công bố khung giờ, dời / chuyển / hủy buổi), ghi chú khách, soạn giáo án |
| 11 | Feedback | Hội viên gửi góp ý, quản lý xem góp ý |
| 12 | Content & Notifications | Quản lý bài viết blog, gửi thông báo |
| 13 | Reporting | Báo cáo vận hành & doanh thu (Quản lý), báo cáo hệ thống (Quản trị viên) |

## Thành viên (Member List) — Trường Đại học FPT

| # | Họ tên | GitHub | Vai trò |
|---|---|---|---|
| 1 | Lê Công Minh | [@Thomas-luckyclover](https://github.com/Thomas-luckyclover) | **Leader** |
| 2 | Phan Khắc Huy Hoàng | [@MuscleCodder06](https://github.com/MuscleCodder06) | Member |
| 3 | Lê Thị Thanh Nhàn | [@lenhan24](https://github.com/lenhan24) | Member |
| 4 | Nguyễn Tuấn Minh | [@Penguin336](https://github.com/Penguin336) | Member |
| 5 | Nguyễn Đăng Vũ | [@godzetkaa](https://github.com/godzetkaa) | Member |

## Cách chạy (How to Run)

1. Cài **JDK 17**, **Apache Tomcat 10.1**, **SQL Server** (+ SSMS) và NetBeans (hoặc IDE có Maven).
2. Tạo database: trong SSMS chạy lần lượt `database/0_Reset_Database.sql` → `database/1_Init_Schema.sql` → `database/2_Seed_Data.sql`.
3. Copy `src/main/resources/application.properties.example` thành `application.properties` (cùng thư mục), điền tài khoản SQL Server.
   File này không được commit.
4. Mở project trong NetBeans → **Clean and Build** → **Run** (Tomcat 10.1).
5. Mở `http://localhost:<port>/gym-management-system/`. Tài khoản mẫu trong dữ liệu seed dùng mật khẩu `Gms@2026`
   (ví dụ khách `an.nguyen95@gmail.com`, lễ tân `tam.le@gms.vn`, quản lý `viet.tran@gms.vn`, PT `trang.nguyen@gms.vn`).

> Chưa điền App Password Gmail thì email (mã xác thực…) được in ra console Tomcat thay vì gửi thật.

## Lời cảm ơn

Nhóm G5 xin chân thành cảm ơn giảng viên môn SWP391 đã hướng dẫn và góp ý trong suốt quá trình thực hiện dự án,
cảm ơn Trường Đại học FPT đã tạo môi trường học tập và thực hành, và cảm ơn từng thành viên trong nhóm đã cùng nhau
hoàn thành Super Gym.
