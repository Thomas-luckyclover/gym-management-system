# Sơ đồ học code GMS

Mở **[gms-5-man-flow-hoc-code.drawio](gms-5-man-flow-hoc-code.drawio)** bằng draw.io Desktop hoặc diagrams.net. File có 23 trang, gồm mục lục có thể bấm để chuyển trang và 22 trang nội dung. Trên mỗi trang có nút **Về mục lục**. Ảnh PNG chỉ là ảnh xem trước, không chứa các tab sơ đồ.

| Màn | Trang trong file chung | Nội dung |
| --- | --- | --- |
| Homepage | 01 | Luồng GET, các nguồn dữ liệu, HomePageView và JSP |
| Recovery pass | 02 | Gửi mã, xác minh, đặt mật khẩu, session và transaction |
| MyBookings | 03–06, 22 | Luồng, DTO, số buổi, lịch mẫu, cảnh báo, điều hướng và bố cục mới đúng màu GMS |
| MyPTSession | 07–16 | Quyền xem, tuần/tháng, SQL, số thứ tự, trạng thái, Membership/DayPass, JS và chi tiết |
| PT Dashboard | 17–21 | Các khối dữ liệu, lịch 7 ngày, khách phụ trách, cảnh báo 14 ngày, nhắc khách và hoa hồng |

**[my-pt-sessions-flow-hien-tai.drawio](my-pt-sessions-flow-hien-tai.drawio)** là bản riêng đã cập nhật, gồm mục lục và 10 trang MyPTSession. Nội dung tương ứng trang 07–16 của file chung; số trang của bản riêng là 01–10. ID trang cũ `gms-current-0` đến `gms-current-10` được giữ lại.

## Năm file riêng theo màn

| Màn | File mở trực tiếp | Số trang, có mục lục |
| --- | --- | --- |
| Homepage | [homepage-flow.drawio](homepage-flow.drawio) | 2 |
| Recovery pass | [recovery-password-flow.drawio](recovery-password-flow.drawio) | 2 |
| MyBookings | [my-bookings-flow.drawio](my-bookings-flow.drawio) | 6 |
| MyPTSession | [my-pt-sessions-flow-hien-tai.drawio](my-pt-sessions-flow-hien-tai.drawio) | 11 |
| PT Dashboard | [pt-dashboard-flow.drawio](pt-dashboard-flow.drawio) | 6 |

## Cách đọc

- Màu xanh dương: Servlet; tím: Service; vàng: DAO hoặc điều kiện; xanh lá: DTO/kết quả; xám: JSP/JS; đỏ: lỗi hoặc giới hạn chức năng. Mỗi ô có nhãn lớp để không phải phân biệt chỉ bằng màu.
- Đọc số bước và mũi tên: hàng trên từ trái sang phải, hàng dưới từ phải sang trái. Hai trang trạng thái và quyền vào gym có mũi tên chia nhánh; đọc nhãn mũi tên thay vì thực hiện mọi nhánh.
- Ô ghi nhớ có ví dụ hoặc điểm dễ nhầm. Dòng cuối trang chỉ tên file/hàm để đối chiếu code. Mũi tên thông thường là thứ tự học, không có nghĩa mỗi ô đều gọi DB hoặc là một request mới.
- Chữ và đường nối là phần tử draw.io có thể sửa. Chữ trong mỗi thẻ thuộc thẻ đó, nên di chuyển thẻ sẽ kéo cả nội dung đi cùng.

## Phạm vi cập nhật

Đối chiếu working tree ngày **23/10/2026**, bao gồm thay đổi chưa commit. Đây là sơ đồ giải thích code hiện tại, không mô tả các thao tác chưa triển khai như đã hoạt động.

- MyBookings: đầu gói và Buổi tới gộp thành một khối, chỉ một nút xem lịch đúng buổi. Giữ vòng tròn theo yêu cầu cuối; xóa nextAfterFocus, endedCount và các phần hiển thị trùng. Bỏ khối “N buổi vắng / hủy · xem lý do”, pastProblems và buildPastProblems(); giữ khối Cần chú ý, chú thích trạng thái và link ô chấm. Thẻ trái còn tên gói, PT và số buổi. Buổi lẻ ẩn vòng; gói kết thúc ẩn Zalo. Trang 22 minh họa desktop/mobile với màu GMS lấy từ computed style, gồm chip Membership. Các màu phân lớp Servlet/Service/DAO/DTO của trang luồng là chú giải kỹ thuật, không phải màu giao diện.
- MyPTSession: ưu tiên cảnh báo ngày phòng nghỉ; phân biệt trạng thái DB với nhãn `pending` chờ chốt trên UI; không có thao tác khách tự hủy mới. `ByCustomer` chỉ dùng khi hiển thị dữ liệu cũ.
- PT Dashboard: một khối Khách phụ trách, mỗi thẻ gói có tên/SĐT, số buổi và hạn gói; cảnh báo nằm ngay dưới gói, ghép bằng SubscriptionId. DAO đọc thêm gói cảnh báo của buổi dạy thay; gói đôi giữ SĐT và cảnh báo từng người. Thẻ có `closedDay` đứng đầu, không nhắc khách. Dời buổi mở đúng SessionId nhưng nghiệp vụ trong hộp thoại còn `disabled`.
- Màn lịch public của PT đang làm dở không thuộc năm màn trong bộ sơ đồ này.

Đã kiểm tra XML, ID, đầu nối, liên kết trang, khung trang và độ dài dòng chữ; đã xem ảnh kiểm tra bố cục từ cùng mô hình tạo file. **[Ảnh mục lục](gms-5-man-preview.png)** giúp xem nhanh cấu trúc mà không cần mở trình sửa draw.io.
