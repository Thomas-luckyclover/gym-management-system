# Nhiệm vụ luyện vấn đáp My PT Session

Cập nhật: 14/10/2026. Nguồn: yêu cầu trực tiếp của người dùng.

## Mục tiêu và vai trò

- Đóng vai hội đồng có kinh nghiệm khoảng 3 năm, vấn đáp bảo vệ đồ án 10 tuần.
- Phạm vi chính: màn Lịch tập của tôi (My PT Session) trong BF-08; gồm Sắp tới, Lịch sử, Gói PT của tôi, lọc, phân trang và hủy buổi. Đổi buổi chỉ hỏi trong phạm vi thực tế đã triển khai; không coi thiết kế hoặc SQL demo là chức năng hoàn thành.
- Bám nội dung người học trình bày, màn demo và code hiện tại. Trước khi chấm một chi tiết kỹ thuật, kiểm tra đúng phiên bản code; không đoán tên hàm, bảng hoặc field.

## Hình thức bài

- 10 câu, tổng 20 điểm. Mỗi câu có thể gồm các ý liên quan; câu dễ ít điểm, câu khó nhiều điểm. Công bố điểm trước từng câu, bảo đảm tổng đúng 20.
- Bắt đầu bằng 2 phút trình bày tự do, sau đó demo, rồi hỏi theo nội dung đã trình bày và demo.
- Hỏi từng câu, chờ trả lời; không đổ toàn bộ đề hoặc đáp án cùng lúc.
- Người học được mở code và phải chỉ vị trí cụ thể, không bắt học thuộc tên hàm.
- Nếu bỏ câu demo, ghi rõ chưa chấm và mẫu số còn lại; không tự cộng điểm hoặc coi là đã demo.

## Cách hỏi phải sát hội đồng

- Hỏi ngắn, trực tiếp: chỉ vào một nút, số liệu, field, thông báo, bộ lọc hoặc trang rồi yêu cầu tìm đoạn code thực hiện.
- Câu trả lời cần chỉ được file, hàm hoặc khối sự kiện và đoạn xử lý cụ thể. Chỉ nói “ở Service/DAO” chưa đủ.
- Theo câu trả lời để hỏi tiếp: hàm nào gọi hàm nào, tham số ở đâu ra, DTO nào giữ dữ liệu, query nào đọc/ghi, bảng và cột nào liên quan, kết quả đưa lên màn hình bằng cách nào.
- Hỏi cả cơ chế và luồng chạy, không giới hạn ở chức năng hủy hoặc câu hỏi nghiệp vụ.
- Không cho sẵn file/hàm/đáp án trong câu hỏi đầu tiên nếu chính việc tìm vị trí là nội dung cần đánh giá.
- Không dẫn người học tới đáp án bằng chuỗi câu hỏi quá đơn giản rồi chấm như trả lời độc lập. Ghi nhận rõ mức trợ giúp.
- Khi người học xin giảng hoặc chưa hiểu đề: tạm chuyển sang hướng dẫn ngắn, giải thích một điểm; sau đó yêu cầu tự chỉ code hoặc nói lại. Phân biệt phần học với phần thi.
- Chấm thẳng: đúng, sai, thiếu ý; nghiêm túc nhưng không xúc phạm. Không tự nhận đã đọc nội dung màn demo nếu chưa thấy.

## Các hướng hỏi xoáy

1. Mở trang: URL được mapping ở đâu, file nhận request, hàm điều phối, chỗ forward tới giao diện, đường dẫn JSP được xử lý thế nào.
2. Giao diện và sự kiện: file dựng nút/form/modal, selector nối HTML với JavaScript, khối đăng ký và xử lý sự kiện, điều kiện hiện/ẩn nút hoặc badge.
3. Nguồn số/field: giá trị trên màn thuộc attribute và DTO nào, ai gán, query nào lấy, bảng/cột hoặc phép tính nào tạo ra; phân biệt dữ liệu lưu với dữ liệu tính.
4. Bộ lọc: form gửi gì, GET hay POST, Servlet đọc ở đâu, Service kiểm tra thế nào, Filter DTO giữ gì, đoạn query áp dụng điều kiện ở đâu, PreparedStatement gán tham số ra sao.
5. Phân trang: count theo ngày hay theo buổi, offset/page size ở đâu, lọc trước hay sau phân trang, giới hạn trang không hợp lệ, giữ bộ lọc qua link như thế nào.
6. Hủy buổi: ID đi từ đâu đến đâu, thời điểm mở modal và gửi request, kiểm tra quyền/trạng thái/thời gian, query cập nhật, bảng/cột thay đổi, kiểm tra số dòng cập nhật.
7. Thông báo và lỗi: message nào, nguồn hằng số, ServiceResult, flash toast, forward/redirect, lý do hiện đúng thông báo đó, refresh/back có gửi lại thao tác không.
8. Quyền và dữ liệu: danh tính từ session khác tham số form, người đứng tên/người đi cùng, kiểm tra sở hữu, sửa request hoặc trạng thái thay đổi giữa lúc đọc và ghi.
9. Nghiệp vụ hiển thị: Sắp tới/Lịch sử/Gói PT phân nhóm thế nào, Scheduled/Cancelled/chờ job, gói Active/gói cũ trong dropdown, buổi sắp tới và quota/lượt đổi của từng lần mua.
10. Thực hành: yêu cầu sửa hoặc thêm một chức năng nhỏ dựa trên màn đang demo; yêu cầu người học xác định các file cần đổi, thực hiện rồi giải thích và demo kết quả. Không tự sửa code ứng dụng thay người học khi đang thi.

Các ví dụ trên là hướng hỏi, không phải đề cố định. Không dồn tất cả thành một câu hỏi dài.

## Trạng thái tiếp tục

- Bài trước đạt 13,5/18 điểm đã chấm; câu demo 2 điểm bỏ qua. Có nhiều gợi ý nên không coi điểm này là mức trả lời độc lập.
- Người dùng đánh giá cách hỏi trước chưa sát: quá thiên về giải thích/nghiệp vụ, gợi ý sớm; yêu cầu ưu tiên truy vết code trực tiếp và hỏi xoáy theo màn.
- Ở lượt bắt đầu lại, người học đã xác định `MyPtSessionServlet` và `RequestUtil.forward(request, response, VIEW)` là đoạn gọi giao diện.
- Câu tiếp theo đang chờ trả lời: `VIEW` trỏ tới đâu và đoạn code nào xử lý đường dẫn đó để mở JSP dù giá trị không có đuôi `.jsp`?
- Sau đó mở rộng sang đoạn áp dụng bộ lọc, luồng file gọi nhau, cơ chế phân trang và dữ liệu database; không chỉ tiếp tục hỏi hủy buổi.

## Cách sử dụng ghi chú

- Đọc ghi chú này và nhật ký học trước khi tiếp tục phiên vấn đáp.
- Dùng `learn-codebase` để theo dõi mức hiểu và `business-analyst-agent` để đối chiếu nghiệp vụ khi cần.
- Không thay đổi code ứng dụng trong phiên vấn đáp, trừ khi người dùng chuyển sang yêu cầu thực hiện sửa code.
- Lưu tiến độ và điểm sau các lượt đáng kể; không tự commit/push tài liệu nếu chưa được yêu cầu.
