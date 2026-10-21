# BF-05 — Bản sửa và kiểm chứng, 21/10/2026

## Quyết định trong chat

- Ưu tiên checklist BF-05; SRS sẽ cập nhật theo các quyết định mới.
- Tạm dùng ca cố định: mọi PT/Lễ tân `Working` được giả định làm cả hai ca. Không thêm bảng phân ca. Số liệu không phải số người đã được phân công thực tế.
- Chia công việc thành nhiều commit nhỏ có nội dung thật.

## Ngày nghỉ và quỹ phép

FromDate/ToDate/Days là dữ liệu chuẩn. Một ca trong một ngày tính 0,5 ngày; FullDay tính cả hai ngày biên. Đơn tối đa 31 ngày trong cùng năm; nhiều ngày phải chọn FullDay. Form nhận ngày kết thúc, giữ giá trị khi lỗi; lịch sử/thông báo hiện đủ khoảng ngày. Dự báo cạnh form chỉ xét ngày bắt đầu và có chú thích.

Annual lấy quỹ từ P-23; thiếu cấu hình thì báo rõ và chặn duyệt, không tự cấp 14 ngày. Sick/Unpaid không dùng quỹ Annual. Các đơn Pending khác vẫn giữ chỗ như chính sách hiện tại. Khi xét một đơn Pending, loại chính đơn đó khỏi số giữ chỗ rồi so sánh số ngày xin nghỉ đúng một lần. Quỹ phép tính từ đơn; không trừ một số dư độc lập.

Đây vẫn là định mức chung P-23, chưa theo từng hợp đồng. UsedDays gồm mọi đơn Approved trong năm, kể cả tương lai; chưa tách đã nghỉ và đã duyệt trong tương lai.

## Quyền và transaction

Tài khoản Active có nhân viên Working ở vị trí Manager mới được xử lý. Đơn chưa phân công thuộc hàng chờ quản lý chung; Manager hợp lệ được xử lý. Đơn được giao chỉ người nhận được xử lý. AccountId được tra sang EmployeeId, không dùng thay thế hai mã.

Duyệt/từ chối/chuyển khóa trước khi đọc lại đơn, kiểm tra Pending, vị trí Manager hiện tại và người được giao. Chuyển đơn kiểm tra người nhận là Manager Working. Duyệt kiểm tra ngày/ca/số ngày/quỹ phép/lịch PT/định biên trên toàn khoảng ngày. Audit và thông báo cùng transaction; lỗi thì rollback. UPDATE vẫn có điều kiện Status = Pending.

TABLOCKX/HOLDLOCK trên LeaveRequests tuần tự hóa quyết định, kể cả đơn khác ngày bắt đầu nhưng giao khoảng nghỉ hoặc dùng chung quỹ phép. Nộp/rút dùng cùng thứ tự khóa. Lựa chọn đơn giản này phù hợp quy mô đồ án, nhưng các quyết định khác ngày cũng phải chờ nhau. Đọc lịch PT và số nhân viên giữ khóa đến hết transaction.

Hạn duyệt giữ quy tắc sẵn có: mốc sớm hơn giữa ngày nộp + 3 ngày và ngày bắt đầu nghỉ - 1 ngày, tại giờ P-20. Có nhãn quá hạn; chưa thêm trạng thái Expired hoặc đổi chính sách quá hạn.

## Hai màn Manager

- Danh sách chỉ có Xem chi tiết; bỏ modal xử lý nhanh và JS chèn tên vào handler. Ưu tiên hạn gần nhất, nhãn Sắp quá hạn/Quá hạn, snapshot kiểm tra kèm lý do.
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
