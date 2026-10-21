# Đọc code BF-05 theo từng luồng

BF-05 có hai người dùng: PT/Lễ tân nộp hoặc rút đơn của mình; Quản lý xem và xử lý đơn. Bắt đầu từ Servlet của màn đang học, theo lời gọi xuống Service rồi DAO. JSP chỉ in dữ liệu đã được chuẩn bị.

## 1. Xem danh sách đơn

1. `LeaveRequestListServlet.doGet()` đọc bộ lọc từ URL và AccountId từ session.
2. `LeaveRequestService.createManagerFilter()` đổi dữ liệu nhập thành `LeaveRequestFilter`. Bộ lọc này dùng cho cả bảng và số đếm trên tab.
3. `getManagerPage()` gọi DAO đếm và lấy một trang đơn. DAO trả `LeaveRequestRow`, gồm đơn và thông tin nhân viên.
4. `toItemView()` sao chép dữ liệu, thêm hạn xử lý và chữ đếm ngược. Hàm này không truy vấn database.
5. Với đơn Pending, `loadApprovalSummary()` đọc điều kiện qua `LeaveApprovalService.review()` để hiện Đạt/Cần xử lý.
6. Servlet đặt `leavePage`, `statusCounts` và bộ lọc vào request; `leave-request-list.jsp` hiển thị chúng.

`AccountId` là mã tài khoản đăng nhập. `EmployeeId` là mã nhân viên. Service phải tra từ tài khoản sang nhân viên; hai mã không thay thế cho nhau.

## 2. Mở chi tiết

`LeaveRequestReviewServlet.doGet()` gọi `showReview()`. Hàm này lấy `LeaveReviewDetail`, danh sách người có thể nhận chuyển và CSRF token, rồi mở JSP. Khi POST lỗi, Servlet giữ lại dữ liệu nhập và dùng lại `showReview()`.

`LeaveRequestService.getReviewDetail()` gọi cùng `LeaveApprovalService.review()` như danh sách. Kết quả gồm quỹ phép, quân số từng ngày/ca và các mục kiểm tra. Service bổ sung danh sách buổi PT bị trùng, lịch sử xử lý và người phụ trách.

## 3. Bấm duyệt

1. JavaScript chỉ mở hộp xác nhận. Người dùng xác nhận thì form gửi POST.
2. Servlet kiểm tra đăng nhập, CSRF token và hành động, rồi gọi `approveRequest()`.
3. Service tìm Manager đang làm việc, mở transaction và khóa phạm vi quyết định.
4. `validateDecision()` đọc lại đơn: còn Pending không, ai được giao, Manager có quyền không.
5. `validateApproval()` gọi `LeaveApprovalService.review()` bằng chính Connection của transaction. Nếu có mục không đạt, trả lý do và rollback.
6. Nếu đạt, DAO cập nhật Approved. Service ghi audit, gửi thông báo và commit. Lỗi ở một bước thì rollback toàn bộ.
7. Servlet thông báo kết quả và quay về danh sách; lỗi nghiệp vụ thì hiện lại chi tiết.

Transaction là một nhóm thay đổi cùng thành công hoặc cùng hủy. Nó cần thiết để tránh trường hợp đơn được duyệt nhưng nhật ký/thông báo chưa lưu, hoặc hai người cùng xử lý dữ liệu cũ.

Từ chối và chuyển người duyệt cũng đọc lại quyền/trạng thái trong transaction. Từ chối cần lý do; chuyển cần người nhận là Manager đang làm việc.

## 4. Kiểm tra đầu vào và ba điều kiện chung

`LeaveApprovalService` chỉ đọc và kiểm tra, không tự ghi dữ liệu hay mở transaction:

- `validateRequest()`: khoảng ngày, ca, số ngày lưu có khớp nhau không. Nhiều ngày phải nghỉ cả ngày; tối đa 31 ngày trong cùng năm.
- `checkAnnualBalance()`: chỉ áp dụng phép năm. Đơn Annual khác đang chờ vẫn giữ chỗ; loại đơn hiện tại khỏi phần giữ chỗ để không tính hai lần. Thiếu P-23 thì chặn và giải thích. `LeaveBalance` nhận định mức qua constructor, không có mặc định 14 ngày hoặc setter làm lệch số dư.
- `checkStaffing()`: kiểm tra từng ca của từng ngày. Cả ngày được xét thành sáng và chiều. Nhân viên đã nghỉ việc không được coi là đủ điều kiện.
- `checkSessions()`: PT có buổi Scheduled trùng khoảng nghỉ thì phải xử lý lịch trước.

`LeaveApprovalReview.getBlockingMessage()` trả lý do đầu tiên chưa đạt; trả null nếu đạt. Danh sách dùng lý do này để tóm tắt, chi tiết hiện đủ các mục, lần duyệt thật kiểm tra lại dữ liệu mới nhất.

Ví dụ: đơn nghỉ hai ngày có ca chiều ngày thứ hai thiếu người. Cả danh sách, chi tiết và POST đều chặn vì cùng đi qua `checkStaffing()`.

## 5. Nhân viên nộp/rút đơn

`LeaveRequestFormServlet` và `LeaveRequestPreviewServlet` gọi `StaffLeaveService`. Service này giữ các bước xem form, dự báo quân số, nộp đơn và rút đơn của chính nhân viên. Luồng quản lý nằm riêng trong `LeaveRequestService`. Form và API dự báo dùng cùng `loadStaffingPreview()`; Servlet dùng `DateTimeUtil.parseHtmlDate()` thay vì tự bắt lỗi parse. JS dùng `getJson()` và `formatDate()` có sẵn trong `common.js`, có trạng thái đang tải/lỗi và bỏ phản hồi cũ khi người dùng đổi lựa chọn.

Service tính số ngày: một ca là 0,5; cả ngày là số ngày từ FromDate đến ToDate, tính cả hai biên. DAO lưu số ngày đó, không tự tính lại hay đoán ngày kết thúc. Entity chỉ giữ `fromDate`, `toDate`, `days`; tên trường form `leaveDate` là ngày bắt đầu gửi từ trình duyệt.

## 6. File giao diện và dữ liệu

- `dto/`: các lớp chỉ chứa dữ liệu truyền giữa DAO, Service và JSP. Các lớp này không còn nằm lẫn bên trong DAO.
- `leave-request-list.jsp`: tab, bộ lọc, bảng; các tab dùng một vòng lặp JSTL.
- `leave-request-list.css`: toàn bộ style riêng danh sách. Trang không còn nạp CSS chi tiết để rồi ghi đè.
- `leave-request-review.jsp`: dữ liệu chi tiết và các form xác nhận.
- `leave-request-review.js`: mở/đóng hộp thoại, Escape, giữ focus và thu gọn lý do; không gửi AJAX xử lý đơn.
- `leave-requests.css`: style form nhân viên và chi tiết; đã bỏ các khối lọc/tab/nút duyệt nhanh cũ không dùng.
- `LeaveRequestReminderJob` gọi `sendDueReminders()`: nhắc đơn đến hạn. Tên job phản ánh hành vi hiện tại; không tự từ chối đơn.

## 7. Những giả định vẫn giữ

PT/Lễ tân đang làm việc được tạm tính làm cả hai ca. Chưa có bảng phân ca thực tế. Rút đơn vẫn xóa Pending và ghi audit; chưa thêm trạng thái Withdrawn. Chưa triển khai màn gom toàn bộ nhân viên rồi mở rộng các đơn bên dưới vì ý tưởng đó chưa được chốt thực hiện.

## 8. Kiểm chứng

`LeaveApprovalSafetyTest` kiểm tra dữ liệu qua cả danh sách, chi tiết và POST: thiếu người ở ca chiều ngày cuối, Pending giữ quỹ phép, trùng lịch PT và đơn đủ điều kiện. Test cũng kiểm tra tab cũ, thay người duyệt, duyệt hai lần và rollback khi lưu thông báo lỗi.

Chạy `mvn package` để kiểm tra Java và test; JSP được kiểm tra riêng bằng Jasper của Tomcat 10.1. Kiểm tra trình duyệt dùng bản Tomcat xem thử chỉ đọc dữ liệu, không gửi quyết định thật.

Kết quả cuối đợt: `mvn package` chạy 184 test, không lỗi; Jasper biên dịch cả ba JSP không lỗi. Trình duyệt xác nhận lọc/đếm tab, trạng thái nhân viên đã nghỉ việc, chi tiết 0,5 ngày, hộp thoại và Escape, form PT, dự báo AJAX theo ngày/ca và khóa nộp khi đã quá hạn. Bản xem thử chặn POST; không ghi quyết định hoặc nộp đơn lên DB thật. WAR chưa được redeploy vào Tomcat chính.
