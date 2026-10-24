# Gói của tôi và PT Dashboard — 23/10/2026

Gộp đầu gói với buổi tới, giữ vòng tròn thống kê và bỏ lịch sử vắng/hủy. Gộp cảnh báo vào đúng thẻ khách theo SubscriptionId, thêm số điện thoại. Phần giải thích Buổi khác giờ nằm chung trong nút i của Lịch cố định; bỏ nút i riêng.

Số dòng so với bản lưu trước đợt chỉnh giao diện này; các thay đổi có sẵn trước đó không tính vào mức giảm.

| Nhóm | File | Trước | Sau | Chênh lệch |
| --- | --- | ---: | ---: | ---: |
| Gói của tôi | [my-bookings.jsp](../src/main/webapp/WEB-INF/jsp/customer/my-bookings.jsp) | 443 | 370 | -73 |
| Gói của tôi | [_booking-package.jspf](../src/main/webapp/WEB-INF/jsp/customer/_booking-package.jspf) | 19 | 8 | -11 |
| Gói của tôi | [my-bookings.css](../src/main/webapp/css/pages/customer/my-bookings.css) | 377 | 303 | -74 |
| Gói của tôi | [MyBookingService.java](../src/main/java/com/gms/service/MyBookingService.java) | 492 | 464 | -28 |
| Gói của tôi | [MyBookingView.java](../src/main/java/com/gms/dto/MyBookingView.java) | 147 | 138 | -9 |
| Gói của tôi | [MyBookingServiceTest.java](../src/test/java/com/gms/service/MyBookingServiceTest.java) | 390 | 365 | -25 |
| PT Dashboard | [PtDashboardDAO.java](../src/main/java/com/gms/dao/PtDashboardDAO.java) | 563 | 569 | +6 |
| PT Dashboard | [PtAssignedPackageView.java](../src/main/java/com/gms/dto/PtAssignedPackageView.java) | 127 | 142 | +15 |
| PT Dashboard | [PtDashboardService.java](../src/main/java/com/gms/service/PtDashboardService.java) | 753 | 773 | +20 |
| PT Dashboard | [PtDashboardServlet.java](../src/main/java/com/gms/servlet/pt/PtDashboardServlet.java) | 189 | 189 | +0 |
| PT Dashboard | [dashboard.jsp](../src/main/webapp/WEB-INF/jsp/pt/dashboard.jsp) | 345 | 258 | -87 |
| PT Dashboard | [dashboard.css](../src/main/webapp/css/pages/pt/dashboard.css) | 687 | 631 | -56 |
| PT Dashboard | [PtDashboardServiceTest.java](../src/test/java/com/gms/service/PtDashboardServiceTest.java) | 110 | 159 | +49 |
| Hướng dẫn | [common.js](../src/main/webapp/js/common.js) | 2964 | 2962 | -2 |
| Hướng dẫn | [head.jspf](../src/main/webapp/WEB-INF/jsp/common/head.jspf) | 44 | 44 | +0 |
| Tổng | | 7650 | 7375 | -275 |

Kiểm tra: 243 test, 0 thất bại/lỗi/bỏ qua; Maven package thành công. Jasper biên dịch MyBookings và PT Dashboard: 0 lỗi. common.js qua node --check.

Đã kiểm tra dữ liệu seed Gia Hương (buổi hôm nay), Mai (thiếu Membership), PT Sơn (2 thẻ khách có số điện thoại); deep link mở đúng buổi. Desktop 1440px, mobile 390px và 320px không tràn ngang; hộp hướng dẫn mở bằng nút và đóng bằng Escape. Chưa kiểm tra RTL và zoom 200%.

[Năm sơ đồ draw.io theo màn](diagrams/README.md).

Ảnh kiểm tra lưu trong target/booking-compact-evidence/: huong-desktop.jpg, mai-mobile-320.jpg, pt-clients-desktop.jpg, hint-desktop.jpg, hint-mobile.jpg. Log kiểm tra lưu trong target/pt-clients-build.log và target/client-cards-jsp-final.log.
