# BF-05 — Bản sửa và kiểm chứng, 21/10/2026

## Quyết định trong chat

- Ưu tiên checklist BF-05; SRS sẽ cập nhật theo các quyết định mới.
- Tạm dùng ca cố định: mọi PT/Lễ tân `Working` được giả định làm cả hai ca. Không thêm bảng phân ca. Số liệu không phải số người đã được phân công thực tế.
- Đợt đầu chia commit nhỏ có nội dung thật. Đợt refactor dễ học, user yêu cầu gom theo nhóm lớn, không chia quá vụn.

## Ngày nghỉ và quỹ phép

FromDate/ToDate/Days là dữ liệu chuẩn. Một ca trong một ngày tính 0,5 ngày; FullDay tính cả hai ngày biên. Đơn tối đa 31 ngày trong cùng năm; nhiều ngày phải chọn FullDay. Form nhận ngày kết thúc, giữ giá trị khi lỗi; lịch sử/thông báo hiện đủ khoảng ngày. Dự báo cạnh form chỉ xét ngày bắt đầu và có chú thích.

Annual lấy quỹ từ P-23; thiếu cấu hình thì báo rõ và chặn duyệt, không tự cấp 14 ngày. Sick/Unpaid không dùng quỹ Annual. Các đơn Pending khác vẫn giữ chỗ như chính sách hiện tại. Khi xét một đơn Pending, loại chính đơn đó khỏi số giữ chỗ rồi so sánh số ngày xin nghỉ đúng một lần. Quỹ phép tính từ đơn; không trừ một số dư độc lập.

Đây vẫn là định mức chung P-23, chưa theo từng hợp đồng. UsedDays gồm mọi đơn Approved trong năm, kể cả tương lai; chưa tách đã nghỉ và đã duyệt trong tương lai.

## Quyền và transaction

Tài khoản Active có nhân viên Working ở vị trí Manager mới được xử lý. Đơn chưa phân công thuộc hàng chờ quản lý chung; Manager hợp lệ được xử lý. Đơn được giao chỉ người nhận được xử lý. AccountId được tra sang EmployeeId, không dùng thay thế hai mã.

Duyệt/từ chối/chuyển khóa trước khi đọc lại đơn, kiểm tra Pending, vị trí Manager hiện tại và người được giao. Chuyển đơn kiểm tra người nhận là Manager Working. Duyệt kiểm tra ngày/ca/số ngày/quỹ phép/lịch PT/định biên trên toàn khoảng ngày. Audit và thông báo cùng transaction; lỗi thì rollback. UPDATE vẫn có điều kiện Status = Pending.

TABLOCKX/HOLDLOCK trên LeaveRequests tuần tự hóa quyết định, kể cả đơn khác ngày bắt đầu nhưng giao khoảng nghỉ hoặc dùng chung quỹ phép. Nộp/rút dùng cùng thứ tự khóa. Lựa chọn đơn giản này phù hợp quy mô đồ án, nhưng các quyết định khác ngày cũng phải chờ nhau. Đọc lịch PT và số nhân viên giữ khóa đến hết transaction.

Quyết định mới 21/10: bỏ hạn duyệt, đếm ngược và mốc ngày nộp + 3. Pending nghỉ hôm nay/ngày mai hiện Sắp đến ngày nghỉ. Job nhắc một lần cho Pending nghỉ ngày mai, không phụ thuộc giờ P-20 và không tự duyệt/từ chối. Không xóa P-20 khỏi cấu hình chung hoặc thay schema.

## Hai màn Manager

- Danh sách chỉ có Xem chi tiết; bỏ modal xử lý nhanh và JS chèn tên vào handler. Ưu tiên Pending sắp nghỉ theo ngày bắt đầu; có nhãn Sắp đến ngày nghỉ và kết quả kiểm tra kèm lý do.
- Chi tiết có từng ngày/ca, số trước đơn đang xét, sau duyệt và mức tối thiểu; ghi rõ giả định ca cố định.
- Khóa xác nhận khi thiếu quyền, ngày đã qua hoặc kiểm tra bắt buộc chưa đạt. Backend kiểm tra lại khi POST.
- Hiện mã/ngày/giờ thực tế/trạng thái Scheduled của từng buổi PT trùng và hướng dẫn liên hệ PT xử lý. UC-35 chưa có nên không tạo liên kết đến URL chưa tồn tại.
- Đếm riêng đơn khác Approved/Pending giao khoảng ngày/ca, có link danh sách theo ngày. Pending chưa tính là nghỉ chính thức.
- Xác nhận gồm nhân viên, ngày, ca, loại nghỉ, số ngày, hệ quả quỹ phép/thông báo. Modal hỗ trợ Escape, giữ focus và trả focus về nút mở.
- Chưa có cấu hình giờ ca riêng nên không gắn giờ ví dụ 06:00–12:00 vào tên ca.

## SQL và DB local

Sửa index/seed còn tham chiếu LeaveDate để khớp schema khoảng ngày đã có. Helper seed ánh xạ mẫu một ngày sang FromDate/ToDate/Days. Không reset, nạp seed hay đổi schema/data trên SQL Server đang chạy.

Metadata DB local: có FromDate/ToDate/Days/AssignedToEmployeeId, có LeaveDate cũ cho phép NULL, có P-13/P-14/P-20/P-23. DAO mới không cần LeaveDate. File seed trong repo chưa có P-23; môi trường mới cần cấu hình chính sách quỹ phép trước khi duyệt Annual.

## Kiểm tra

- Maven package: 174 test, không failure/error/skip; build WAR thành công.
- Sau chỉnh thông báo/lịch sử, chạy lại Leave*Test và package: 40 test BF-05 qua, gồm 18 test mới.
- Test mới xét ngày cuối thiếu người/trùng lịch, vượt quỹ/vừa đủ 0,5 ngày/thiếu P-23, Sick không dùng Annual, ngày/ca/số ngày sai, đổi người nhận, tab cũ, gửi duyệt lặp, rollback, API không bỏ qua quỹ phép, khoảng nộp sai và khóa xác nhận.
- Service/DAO thật đọc đủ danh sách/chi tiết 7 đơn trên SQL Server local; đơn 2 ngày FullDay trả 4 dòng ca. Không thay bản ghi.
- Hai kết nối SQL Server thật: B phải chờ khóa A; sau rollback A, B lấy khóa được. Không thay dữ liệu. Chưa thử hai Manager cùng commit trên DB thử nghiệm riêng.
- Helper và 7 fixture nghỉ phép của generator: khoảng/số ngày đúng. Chưa chạy toàn generator vì Python hiện thiếu python-dateutil.
- Chưa redeploy Tomcat, thử JSP trên trình duyệt hoặc chạy bộ schema/seed trên DB mới. Build WAR không thay thế các kiểm tra đó.

## Phần nâng cao còn lại

Rút Pending vẫn xóa vật lý và ghi audit, chưa đạt TC-09 theo hướng Withdrawn. Đơn có lịch sử chuyển có thể bị FK chặn xóa. Cần thay đổi schema/migration riêng cho Withdrawn và kiểm thử lịch sử; không dùng Rejected giả lập rút đơn.

Chưa có module PT đổi/hủy/bàn giao, hủy sau duyệt/hoàn quỹ hoặc báo vắng khẩn cấp trong hệ thống. Nghỉ đột xuất vẫn báo trực tiếp Manager. Không tuyên bố đã hoàn thiện các phần nâng cao này hay mô hình phân ca thực tế.

## Rà lại ảnh danh sách ngày 21/10/2026

Phát hiện đơn #7 của nhân viên Resigned vẫn hiện Đạt: snapshot danh sách dùng chức vụ từ dòng DAO mà chưa đối chiếu Working; trang chi tiết cũng chưa phản ánh điều kiện này. POST duyệt đã kiểm tra Working và chặn trường hợp đó.

Đã sửa danh sách tra lại vị trí Working giống bước duyệt; chi tiết hiện lý do nhân viên đã nghỉ việc và khóa xác nhận. Lý do Cần xử lý xuất hiện ngay trên danh sách. Bỏ thuật ngữ snapshot khỏi nội dung người dùng thấy, cập nhật phiên bản CSS để trình duyệt tải lại.

Thêm 2 test tái hiện lỗi danh sách/chi tiết. Clean package chạy 176 test, không lỗi; kiểm tra Service thật trên DB local xác nhận đơn #7 Cần xử lý và canApprove = false, không đổi bản ghi. Chưa redeploy WAR mới hoặc xác minh bản sửa này bằng trình duyệt.

## Đơn giản hóa frontend theo yêu cầu trong chat

- Danh sách chỉ hiện Phép năm/Nghỉ ốm/Không lương trong cột loại nghỉ; bỏ lý do bên dưới. Lý do đầy đủ vẫn ở trang chi tiết.
- Người xử lý chỉ hiện một tên: Pending lấy người được giao, đơn đã xử lý lấy người thực hiện. Chưa giao ghi Chưa phân công; tự từ chối ghi Hệ thống. Bỏ Quản lý chung và các chú thích lặp dưới tên. Chi tiết phân biệt Người phụ trách với Người đã xử lý, không gắn Đã duyệt cho đơn bị từ chối.
- Giữ đúng hai bộ lọc: chức vụ PT/Lễ tân, loại nghỉ Annual/Sick/Unpaid. Ô tìm theo tên hoặc mã. Tab trạng thái giữ nguyên bộ lọc; số lượng tab dùng cùng khoảng ngày với bảng.
- Tách hạn xử lý thành cột riêng. Loại nghỉ, người xử lý và các tiêu đề dùng chữ gọn; số ngày hiển thị 0,5 / 1 / 2 thay vì 0.5 / 1.0 / 2.0.
- Không thêm JS. JSP dùng c:choose để hiển thị, CSS riêng leave-request-list.css chỉ áp dụng cho body.leave-list-page. Service giữ phần tính điều kiện; Servlet nhận bộ lọc; DAO truy vấn như trước. Các câu nhắc trên chi tiết đổi Working/Receptionist/SLA thành tiếng Việt dễ hiểu.
- Đã kiểm tra hai JSP bằng Jasper của Tomcat 10.1, không lỗi. Bản Tomcat xem thử chỉ đọc dữ liệu đã xác nhận bộ lọc Nghỉ ốm, Lễ tân + Không lương, xóa lọc, đếm tab theo ngày và chi tiết Chưa phân công. Không redeploy ứng dụng chính hoặc ghi quyết định nghỉ phép khi kiểm tra.

Để học luồng đơn giản: bắt đầu ở LeaveRequestListServlet.doGet, theo getManagerPage tới DAO rồi xem các field được JSP in ra. Tên hiển thị lấy từ dữ liệu thật; JSP không tự chọn hoặc gán một người duyệt mới.

## Refactor để dễ học (21/10/2026)

Đường dẫn đọc code mới: [BF-05-code-guide.md](BF-05-code-guide.md).

- Luồng nhân viên tách sang StaffLeaveService; LeaveRequestService giữ luồng quản lý và nhắc hạn. LeaveApprovalService gom các điều kiện dùng chung cho danh sách, chi tiết và POST, dùng Connection bên gọi truyền vào.
- Quyết định đọc lại đơn trong transaction, bỏ truy vấn chi tiết lặp trước transaction. Quyền, phân công, Pending, audit và thông báo vẫn được bảo vệ như trước.
- DTO ra khỏi DAO; Entity dùng FromDate/ToDate/Days, bỏ LeaveDate cũ trùng dữ liệu. DAO đọc đúng cột schema hiện tại và lưu Days do Service tính. Câu SELECT có khóa viết rõ, không thay chuỗi SQL bằng replace.
- Tham số AccountId đặt đúng tên. Bộ lọc chuẩn hóa một lần dùng cho bảng và tab. Servlet chi tiết dùng một hàm tải trang cho GET và POST lỗi, giữ lại ghi chú/người nhận khi chuyển lỗi.
- DTO danh sách khởi tạo từ một dòng dữ liệu, đặt riêng hạn xử lý; bỏ constructor 20 tham số và các fallback ngày chồng nhau. Bỏ ReviewCheck.details không dùng.
- Danh sách dùng CSS độc lập; bỏ khối CSS tab/lọc/nút nhanh cũ. JSP lặp tab một lần, JavaScript chi tiết ra file riêng. Số ngày và khoảng ngày trong xác nhận hiển thị gọn hơn.
- JOB-07 đổi tên thành LeaveRequestReminderJob/sendDueReminders để khớp hành vi nhắc hạn hiện có; không thêm tự từ chối.
- Không sửa schema, không triển khai ý tưởng accordion theo nhân viên, không đổi chính sách nghỉ phép.

Bổ sung luồng nhân viên: form và API dùng chung dự báo; parse ngày bằng util có sẵn, bỏ kiểm tra loại lỗi bằng cách cắt chuỗi thông báo. PT có đường dẫn xem lịch trên banner lỗi. JS dùng getJson/formatDate chung, có trạng thái tải/lỗi và bỏ phản hồi cũ khi đổi lựa chọn. LeaveBalance bỏ constructor tự cấp 14 ngày và các setter không dùng để số dư không bị lệch với định mức/đã duyệt/đang chờ.

Kiểm chứng cuối: 184 test qua; ba JSP biên dịch không lỗi. Trình duyệt bản xem thử chỉ đọc đã kiểm tra lại danh sách, chi tiết, hộp thoại, form PT, dự báo AJAX và khóa ngày quá hạn. Không ghi dữ liệu nghỉ phép, không redeploy Tomcat chính.

## Đơn giản hóa việc nhắc duyệt theo ngày nghỉ (21/10/2026)

Đã bỏ toàn bộ thuộc tính hạn/đếm ngược/quá hạn khỏi DTO và Service màn quản lý; bỏ SQL sắp theo ngày nộp + 3 và truy vấn nhắc SLA cũ. Danh sách còn bảy cột, cảnh báo sắp nghỉ nằm cùng trạng thái. Chi tiết/timeline dùng ngày bắt đầu nghỉ. Pending đã qua ngày vẫn bị chặn duyệt như cũ; chưa tự từ chối.

ReminderSentAt chỉ được đánh dấu nếu đơn còn Pending và chưa nhắc. Lưu dấu nhắc và thông báo cùng transaction, lỗi thì rollback để job có thể thử lại. Các dấu nhắc cũ được giữ nguyên; không reset database.

Kiểm chứng thay đổi nhắc theo ngày: clean package chạy 191 test, không lỗi; Jasper biên dịch hai JSP quản lý không lỗi. Bản Tomcat xem thử chỉ đọc xác nhận bảy cột, nhãn sắp nghỉ ngày mai, thứ tự tab Tất cả, timeline ngày bắt đầu nghỉ, lọc khoảng ngày và loại nghỉ. Không chạy job gửi nhắc hoặc ghi quyết định trên DB thật; ứng dụng chính chưa redeploy.

## Danh sách nhân viên mở rộng các đơn (21/10/2026, user yêu cầu thử triển khai)

- Toàn bộ PT/Lễ tân xuất hiện, kể cả người chưa có đơn hoặc đã nghỉ việc; người đã nghỉ việc có nhãn riêng. Manager không thêm vào vì nghiệp vụ nộp đơn hiện chỉ hỗ trợ PT/Lễ tân.
- Hàng cha hiện tên/mã/chức vụ, tổng đơn và tổng Pending của nhân viên. Pending gần ngày nghỉ được xếp trước và có badge đỏ.
- Bấm hàng cha để mở các đơn bằng details/summary native, hỗ trợ bàn phím không cần JS. Người chưa có đơn hiện Chưa có đơn và không mở rộng. Xem chi tiết và luồng quyết định giữ nguyên.
- Mặc định tab Tất cả. Bộ lọc trạng thái/loại/ngày chỉ lọc đơn bên trong; tên/mã/chức vụ lọc hàng nhân viên. Không khớp bộ lọc không bị hiểu nhầm là chưa từng có đơn.
- countEmployees/findEmployeePage phân trang nhân viên tại SQL; LEFT JOIN giữ người chưa có đơn. findEmployeeRequests lấy đơn của cả trang bằng danh sách EmployeeId có tham số, không cắt đôi nhóm và không đọc riêng từng nhân viên.
- Không đổi schema, chính sách nghỉ hoặc các kiểm tra khi duyệt. Hướng dẫn luồng mới ở BF-05-code-guide.md.

Kiểm chứng nhóm nhân viên: 197 test qua, hai JSP quản lý cùng header mới biên dịch không lỗi. Bản xem thử chỉ đọc trên DB local hiển thị 12 PT/Lễ tân qua hai trang; đã kiểm tra mở/đóng bằng chuột và Enter, đi tới chi tiết, người chưa có đơn, lịch sử người đã nghỉ việc, lọc tên/mã, loại nghỉ và ngày cuối của đơn nhiều ngày. Mã hóa UTF-8 được khai báo riêng ở fragment header. Không thay DB hoặc gửi quyết định; chưa redeploy Tomcat chính.
