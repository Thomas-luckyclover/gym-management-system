# Đọc code BF-05 theo từng luồng

BF-05 có hai người dùng: PT/Lễ tân nộp hoặc rút đơn của mình; Quản lý xem và xử lý đơn. Bắt đầu từ Servlet của màn đang học, theo lời gọi xuống Service rồi DAO. JSP chỉ in dữ liệu đã được chuẩn bị.

## 1. Xem danh sách đơn

1. `LeaveRequestListServlet.doGet()` đọc bộ lọc từ URL và AccountId từ session. Mặc định mở Tất cả để thấy đủ lịch sử khi mở nhân viên.
2. `LeaveRequestService.createManagerFilter()` đổi dữ liệu nhập thành `LeaveRequestFilter`. Hai ô Từ ngày/Đến ngày lọc theo thời gian nghỉ, được phép bỏ trống một hoặc cả hai. Service báo lỗi nếu Từ ngày lớn hơn Đến ngày.
3. `getEmployeePage()` gọi DAO `countEmployees()` và `findEmployeePage()`. Khi chưa chọn ngày, SQL LEFT JOIN giữ người chưa có đơn. Khi chọn ít nhất một ngày, EXISTS chỉ giữ nhân viên có đơn khớp toàn bộ bộ lọc (ngày, trạng thái, loại nghỉ, tên/mã và chức vụ). SQL vẫn đếm tổng/đơn chờ trên toàn bộ lịch sử, ưu tiên người sắp nghỉ và phân trang theo nhân viên.
4. Service lấy danh sách EmployeeId của trang, gọi `findEmployeeRequests()` một lần để lấy các đơn phù hợp. Map theo EmployeeId giúp gắn đơn về đúng người; không cắt trang giữa các đơn của cùng một người.
5. `toItemView()` sao chép mỗi đơn và đánh dấu sắp nghỉ. Với Pending, `loadApprovalSummary()` gọi bộ kiểm tra chung để hiện Đạt/Cần xử lý.
6. Servlet đặt `employeePage`, `statusCounts` và bộ lọc vào request. JSP lặp nhân viên; details/summary mở danh sách đơn mà không cần JS riêng.

Số tổng/đơn chờ trên hàng nhân viên luôn tính trên toàn bộ lịch sử của người đó. Số trên tab và danh sách bên trong theo bộ lọc đang chọn. Khi chưa chọn ngày, nếu có lịch sử nhưng không có đơn khớp trạng thái/loại, hàng vẫn mở được và giải thích rõ. Người chưa từng có đơn hiện Chưa có đơn, không có nút mở rộng. Nhân viên đã nghỉ việc vẫn có nhãn riêng để giữ lịch sử.

Điều kiện giao khoảng ngày (tính cả hai biên): `LeaveRequests.ToDate >= fromDate` và `LeaveRequests.FromDate <= toDate`. Ví dụ minh họa: chọn 24/10–24/10 vẫn thấy đơn nghỉ 23/10–24/10. Nếu bỏ trống một đầu thì không giới hạn đầu đó. Tab và phân trang giữ ngày đã chọn; Tìm quay về trang đầu; Xóa lọc bỏ cả hai ngày và giữ tab hiện tại.

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
- `leave-request-list.jsp`: tab, bộ lọc, danh sách nhân viên và bảng đơn trong mỗi nhóm; các tab dùng một vòng lặp JSTL.
- `leave-employee-header.jspf`: tên/chức vụ, tổng đơn, số đơn chờ và cảnh báo sắp nghỉ dùng chung cho hàng có đơn và chưa có đơn.
- `leave-request-list.css`: toàn bộ style riêng danh sách. Trang không còn nạp CSS chi tiết để rồi ghi đè.
- `leave-request-review.jsp`: dữ liệu chi tiết và các form xác nhận.
- `leave-request-review.js`: mở/đóng hộp thoại, Escape, giữ focus và thu gọn lý do; không gửi AJAX xử lý đơn.
- `leave-requests.css`: style form nhân viên và chi tiết; đã bỏ các khối lọc/tab/nút duyệt nhanh cũ không dùng.
- `LeaveRequestReminderJob` gọi `sendDueReminders()`: nhắc một lần cho đơn còn chờ bắt đầu nghỉ ngày mai. Tên job phản ánh hành vi hiện tại; không tự từ chối đơn.

## 7. Những giả định vẫn giữ

PT/Lễ tân đang làm việc được tạm tính làm cả hai ca. Chưa có bảng phân ca thực tế. Rút đơn vẫn xóa Pending và ghi audit; chưa thêm trạng thái Withdrawn. Đã triển khai hàng mở rộng cho toàn bộ PT/Lễ tân; đây là hai chức vụ được phép nộp đơn trong BF-05 hiện tại.

## 8. Kiểm chứng

`LeaveApprovalSafetyTest` kiểm tra dữ liệu qua cả danh sách, chi tiết và POST: thiếu người ở ca chiều ngày cuối, Pending giữ quỹ phép, trùng lịch PT và đơn đủ điều kiện. Test cũng kiểm tra tab cũ, thay người duyệt, duyệt hai lần và rollback khi lưu thông báo lỗi.

Chạy `mvn package` để kiểm tra Java và test; JSP được kiểm tra riêng bằng Jasper của Tomcat 10.1. Kiểm tra trình duyệt dùng bản Tomcat xem thử chỉ đọc dữ liệu, không gửi quyết định thật.

Kết quả cuối đợt: `mvn package` chạy 184 test, không lỗi; Jasper biên dịch cả ba JSP không lỗi. Trình duyệt xác nhận lọc/đếm tab, trạng thái nhân viên đã nghỉ việc, chi tiết 0,5 ngày, hộp thoại và Escape, form PT, dự báo AJAX theo ngày/ca và khóa nộp khi đã quá hạn. Bản xem thử chặn POST; không ghi quyết định hoặc nộp đơn lên DB thật. WAR chưa được redeploy vào Tomcat chính.

## Cập nhật: ưu tiên ngày nghỉ, bỏ hạn duyệt (21/10/2026)

Theo quyết định mới trong chat, không còn cột Hạn xử lý, đếm ngược, nhãn Sắp quá hạn/Quá hạn hay mốc ngày nộp + 3. Đơn Pending bắt đầu nghỉ hôm nay hoặc ngày mai có nhãn Sắp đến ngày nghỉ. Thứ tự danh sách: Pending từ hôm nay trở đi theo ngày nghỉ tăng dần, rồi Pending đã qua ngày, cuối cùng đơn đã xử lý. Kiểm tra nhân sự/quỹ phép/quyền duyệt vẫn thực hiện như trước.

JOB-07 chọn Pending nghỉ ngày mai, chưa có ReminderSentAt. Job đánh dấu và lưu thông báo trong cùng transaction; không dùng giờ P-20 để đặt hạn hoặc chặn duyệt. P-20 vẫn còn trong cấu hình chung nhưng luồng này không đọc nữa. Đơn đã được nhắc theo chính sách cũ không tự xóa dấu để gửi lại.

## Cập nhật: gom đơn theo nhân viên (21/10/2026)

Luồng duyệt/từ chối/chuyển người duyệt giữ nguyên ở trang chi tiết. Chỉ màn danh sách đổi sang LeaveEmployeeView. Phân trang SQL theo nhân viên; lấy toàn bộ đơn phù hợp của những người trên trang trong một truy vấn, không truy vấn riêng từng người. Tab mặc định đổi sang Tất cả. Không thêm bảng, schema hoặc JavaScript cho accordion.

Kiểm chứng đợt nhóm nhân viên: 197 test qua; JSP/fragment biên dịch không lỗi. Trình duyệt xác nhận hai trang nhân viên, người chưa có đơn, mở/đóng bằng Enter, lọc tên/mã/loại/ngày và Xem chi tiết. Header fragment tự khai báo UTF-8 để chữ cố định không bị lỗi khi include. Chưa redeploy ứng dụng chính.
