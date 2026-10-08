# codeImplementationFrontend.md — Quy Tắc Viết Code Frontend (Vibecode Rules)

File này quy định chi tiết cách viết **Frontend** (JSP hiển thị, HTML, CSS, JavaScript): comment, bảo mật XSS, thụt lề, code style, DOM, tối ưu, UX/UI, và thứ tự trình bày. Phần Backend (Java: Entity/DAO/Service/Servlet) nằm ở file riêng `codeImplementationBackend.md`. `agents.md` tham chiếu đến cả 2 file này — tách riêng, không gộp.

> ⚠️ **LƯU Ý CỰC KỲ QUAN TRỌNG:** Dù các thành viên khác trong nhóm có thể không dùng hết, không đọc hết, hoặc không hiểu vì sao có từng quy tắc trong file này, AI **vẫn bắt buộc áp dụng đầy đủ 100% nội dung dưới đây** mỗi khi hỗ trợ code (vibecode) cho user hiện tại — **không được tự ý bỏ qua, rút gọn, nới lỏng, hay coi nhẹ bất kỳ quy tắc nào** chỉ vì cho rằng người khác không theo hoặc không cần đến mức đó.

## 1. JSP — Rõ Ràng Tối Đa + Chống XSS

- **Viết rõ ràng hết mức có thể** — ưu tiên JSTL (`<c:if>`, `<c:forEach>`, `<c:choose>`), hạn chế tối đa scriptlet `<% %>`; không viết business logic/query trong JSP.
- **Comment khung** — mỗi khối lớn 1 dòng `<%-- ===== Khối … (biến request: x, y) ===== --%>` (Mục 2).

### Chống XSS — bắt buộc, không được bỏ qua

- **Trường hợp KHÔNG dùng AJAX** (JSP render trực tiếp dữ liệu ra HTML): **luôn dùng `<c:out>`** để in dữ liệu động ra trang, tuyệt đối không in thẳng `${...}` cho dữ liệu có nguồn gốc từ user nhập (tên, ghi chú, mô tả...).

  ```jsp
  <!-- Không an toàn — nếu student.name chứa <script>...</script> sẽ chạy luôn trên trình duyệt -->
  <p>Tên: ${student.name}</p>

  <!-- An toàn — c:out tự động escape ký tự đặc biệt (<, >, &...) -->
  <p>Tên: <c:out value="${student.name}" /></p>
  ```

- **Trường hợp CÓ dùng AJAX** (JS nhận dữ liệu JSON rồi tự chèn vào DOM): `c:out` không áp dụng được (vì không qua JSP nữa) — bắt buộc **escape dữ liệu trong JS** trước khi chèn vào HTML bằng hàm `escapeHtml()` (xem Mục 5 — Bảo Mật Trong JS).

## 2. Comment và dữ liệu phải khớp màn hình

- **JSP:** mỗi khối lớn có comment tên khối và request attribute/DTO được dùng. Không chỉ viết “hiển thị dữ liệu”; nêu “Lịch 7 ngày: scheduleDays do Service tạo”.
- **JS:** comment đầu nhóm hàm, mục đích hàm và các bước B1/B2; phần khó chia B1.1/B1.2. Ghi rõ DOM id/class/data attribute đang nối với JSP.
- **CSS:** comment theo khối màn hình; selector có JS dùng phải chỉ rõ điểm nối. Style riêng có phạm vi của trang, tránh ảnh hưởng màn khác.
- Cho phép giải thích ngắn hoặc ví dụ khi giúp hiểu luồng. Không viết comment dài kể lịch sử chat hoặc mô tả lại từng cú pháp.
- Giữ ghi chú người học đã viết; khi chưa hiểu, sửa lời giải thích bằng dữ liệu cụ thể của module.

### 2.1. Hợp đồng Servlet → JSP → JS

Trước khi render một khối, nêu rõ attribute, kiểu và ai tạo. Ví dụ:

| Attribute/field | Nguồn | Cách dùng |
|---|---|---|
| `scheduleDays` | Service tạo `List<PtScheduleDayView>`, Servlet đặt vào request | JSP lặp từng nhóm ngày |
| `day.sessions` | Service gom các `PtSessionView` do DAO tạo | JSP lặp các buổi trong ngày |
| `day.today` | Getter `isToday()` của DTO ngày | Chọn class để tô nổi bật, không in boolean |
| `session.slotDate` | DAO đọc ngày buổi | `gms:formatDate`, dạng dd/MM/yyyy |
| `session.startTime` | DAO đọc giờ bắt đầu | `gms:formatTime`, dạng HH:mm |
| `session.sessionId` | DAO đọc mã buổi | `data-session-id` trên nút Xử lý, không in thành chữ |
| `activeNav` | Servlet chọn mục menu | Layout include đọc để thêm class active |

```jsp
<%-- ===== Lịch 7 ngày (scheduleDays: List<PtScheduleDayView>) ===== --%>
<c:forEach var="day" items="${scheduleDays}">
    <h3><c:out value="${day.dayLabel}" /> · <c:out value="${gms:formatDate(day.slotDate)}" /></h3>
    <c:forEach var="session" items="${day.sessions}">
        <p><c:out value="${gms:formatTime(session.startTime)}" /> · <c:out value="${session.customerName}" /></p>
    </c:forEach>
</c:forEach>
```

Ví dụ trên chỉ minh họa điểm nối, chưa bao gồm các trạng thái rỗng/lỗi của khối hoàn chỉnh. Định dạng LocalDate/LocalTime bằng hàm gms; không truyền thẳng vào fmt:formatDate. Tên và mô tả dùng c:out; dữ liệu động trong thuộc tính HTML cũng phải escape đúng ngữ cảnh.

### 2.2. Khung, rỗng, lỗi là ba tình huống khác nhau

- Khung chưa nối Service: nói rõ trong báo cáo; không dùng số giả hoặc thông báo khẳng định không có lịch thật.
- Service đọc thành công nhưng danh sách ngày rỗng buổi: hiện “Không có buổi trong ngày”.
- Service thất bại: hiện thông báo lỗi thân thiện; không biến thành “Không có buổi”.
- JS gọi AJAX: có trạng thái đang tải, thành công, rỗng và thất bại; không báo thành công trước phản hồi backend.
- Thao tác mới chỉ dựng modal: khóa nút chưa có backend và báo rõ mức hoàn thiện trong báo cáo. Có dữ liệu quyền thì hiện lý do khóa cụ thể; backend vẫn kiểm tra lại quyền và trạng thái khi ghi.

### 2.3. Modal và bàn phím

Modal cần tiêu đề, nút đóng, hỗ trợ Escape và trả focus về nút mở. Không đặt hai nút “Bỏ qua”/“Đóng” nếu cùng tác dụng. Có thể dùng dialog native hoặc thành phần chung phù hợp; không ép modal nghiệp vụ nhiều lựa chọn vào hộp xác nhận đơn giản.

## 3. Thụt Lề & Trình Bày (Formatting)

| Loại file | Thụt lề | Dấu ngoặc `{}` |
|---|---|---|
| JavaScript | 4 spaces | K&R (mở ngoặc cùng dòng) |
| CSS | 2 spaces | mỗi thuộc tính 1 dòng, dấu `;` cuối mỗi dòng |
| JSP/HTML (tag thường) | 4 spaces mỗi cấp; tách thẻ lồng nhau xuống dòng | — |
| JSP (block `<% %>`, JSTL) | 4 spaces (theo cú pháp Java bên trong) | theo Java |

- Luôn có 1 dòng trống giữa các hàm JS, giữa các khối CSS lớn (theo section/component).
- File kết thúc bằng 1 dòng trống, không để nhiều hơn 1 dòng trống liên tiếp.
- HTML/JSP: mỗi thẻ con thụt lề thêm 2 spaces so với thẻ cha, thẻ đóng thẳng hàng với thẻ mở.

## 4. JavaScript — Rõ Ràng Tuyệt Đối, Không Cần Tối Ưu

**Đây là phần quan trọng và khó nhất — nguyên tắc bao trùm: miễn chạy đúng, không chậm, hoàn thành đúng mục đích là đạt — không cần tối ưu bất kỳ điều gì, không dùng logic nâng cao/lạ, không viết "khôn" để giảm số dòng.**

- **Không dùng:** arrow function lồng nhau phức tạp, destructuring nhiều tầng, spread/rest operator nếu không thật cần thiết, method chaining dài (`.filter().map().reduce()` nối liền nhau), regex phức tạp khó đọc, IIFE, closure nâng cao — nếu 1 đoạn code có cách viết "ngắn gọn nhưng khó hiểu" và cách viết "dài hơn nhưng rõ ràng", **luôn chọn cách dài hơn nhưng rõ ràng**.
- **Comment theo Mục 2:** nêu mục đích hàm gọi server, dữ liệu nhận/trả và các bước B1/B2. Khối khó có bước con; giải thích ngắn điểm nối với JSP khi cần.
  ```js
  // Gửi đăng nhập lên server, hiện lỗi hoặc chuyển trang
  function submitLogin(username, password) {
      // B1: kiểm tra 2 ô không trống
      ...
      // B2: gọi postData('/login')
      ...
      // B3: lỗi → hiện dưới ô; thành công → chuyển trang
      ...
  }
  ```

- **Mỗi hàm chỉ nên có 1 mục đích rõ ràng** — nếu hàm vừa gọi AJAX vừa xử lý hiển thị vừa validate, tách ra thành các hàm nhỏ hơn có tên rõ ràng, gọi lẫn nhau — không dồn hết vào 1 hàm dài khó theo dõi.

### Bảo mật trong JS — escape dữ liệu trước khi chèn vào DOM (AJAX)

Khi dữ liệu từ AJAX (JSON) được chèn vào HTML bằng `innerHTML`, **bắt buộc escape** trước để chống XSS:

```js
// Hàm dùng chung để escape HTML trước khi chèn dữ liệu động vào DOM, chống XSS
function escapeHtml(text) {
    const div = document.createElement("div");
    div.textContent = text;
    return div.innerHTML;
}
```

```js
// Dùng escapeHtml() khi chèn dữ liệu người dùng nhập vào HTML
studentNameCell.innerHTML = escapeHtml(student.name);
```

**Ưu tiên hơn nữa:** nếu chỉ cần chèn text thuần (không cần chèn thẻ HTML), dùng `textContent` thay vì `innerHTML` — an toàn tuyệt đối, không cần escape:
```js
studentNameCell.textContent = student.name;  // an toàn, không cần escapeHtml()
```

## 5. DOM — Không Làm Tắt, Phải Rõ Ràng

- **Không dùng quá nhiều phương thức nối liền nhau trên 1 dòng/1 hàm** (method chaining kiểu `document.querySelector(".a").parentElement.nextElementSibling.classList.add("b")`) — tách ra từng bước rõ ràng với biến trung gian.

  **Không làm (làm tắt, khó đọc):**
  ```js
  document.getElementById("student-table").querySelector("tbody").children[0].remove();
  ```

  **Nên làm (từng bước rõ ràng):**
  ```js
  const studentTable = document.getElementById("student-table");
  const tableBody = studentTable.querySelector("tbody");
  const firstRow = tableBody.children[0];
  firstRow.remove();
  ```

- **Cache phần tử DOM vào biến** nếu dùng lại nhiều lần trong cùng 1 hàm (đỡ query lại nhiều lần — đây là tối ưu duy nhất được khuyến khích vì giúp code rõ hơn, không phải vì hiệu năng).
- **Mỗi hàm xử lý DOM chỉ nên làm 1 việc rõ ràng** (VD: `renderStudentRow()` chỉ tạo 1 dòng bảng, `clearStudentTable()` chỉ xóa bảng) — không gộp nhiều thao tác DOM khác nhau vào 1 hàm.
- Luôn comment rõ hàm DOM đang thao tác với phần tử nào (id/class gì), mục đích thao tác đó để làm gì.
- **Đồng bộ 2 chiều với CSS/HTML:** khi viết 1 hàm JS thao tác DOM lên 1 id/class cụ thể, đảm bảo comment ở đúng id/class đó trong CSS/HTML (Mục 6, Mục 7) cũng ghi rõ tên hàm này — tránh trường hợp đổi 1 bên (CSS/HTML đổi id, hoặc JS đổi hàm) mà bên kia không cập nhật theo, gây lỗi khó phát hiện.

## 6. CSS — Comment, Đặt Tên Liên Kết JS, UX/UI

- **Comment ngắn gọn, cơ bản** — không cần giải thích dài dòng, chỉ cần đủ hiểu section CSS này style cho phần nào của trang:
  ```css
  /* Style cho khung đăng nhập */
  .login-box { ... }

  /* Style cho nút submit */
  .btn-login { ... }
  ```
- **Class/id có liên quan đến JS (JS sẽ query tới để xử lý DOM) phải đặt tên rõ ràng hơn bình thường, và ghi chú rõ: file JS nào + hàm nào đang dùng đến nó, hàm đó thao tác DOM ra sao (thêm/xóa/sửa nội dung, gắn sự kiện...):**
  ```css
  /* id này được dùng trong login.js — hàm validateLoginForm() đọc giá trị input để validate,
     hàm submitLogin() gắn sự kiện submit cho form này. Không đổi id nếu chưa sửa login.js */
  #login-form { ... }

  /* id này được dùng trong student-list.js — hàm renderStudentRow() thêm dòng <tr> vào đây,
     hàm clearStudentTable() xóa toàn bộ nội dung bên trong */
  #student-table-body { ... }
  ```
- **Đề xuất animation cho phần tĩnh:** với các phần tử tĩnh có thể hưởng lợi từ hiệu ứng chuyển động nhẹ (hover button, xuất hiện modal, chuyển trang, loading spinner...), AI nên **chủ động gợi ý** cho user biết chỗ này nên làm animation gì — nhưng **chỉ đề xuất, không tự ý code animation nếu user chưa yêu cầu**, tránh làm phát sinh việc ngoài phạm vi đang làm. Ngoài các chỗ có thể hưởng lợi rõ rệt từ animation, không cần đề xuất thêm gì khác.
- **CSS phải đẹp, đảm bảo UX/UI** — không chỉ style cho chạy được, mà cần: khoảng cách (spacing) hợp lý, màu sắc hài hòa, chữ dễ đọc, trạng thái hover/focus/disabled rõ ràng cho các phần tử tương tác (button, input), responsive cơ bản nếu SRS có yêu cầu đa thiết bị.
- Ưu tiên class hơn ID để style; đặt tên kebab-case theo `NamingConvention.md`; tránh `!important` trừ khi thật sự cần override.

## 7. HTML — Rõ Ràng, Có Comment

- Nếu có phần HTML thuần (ngoài JSP), viết rõ ràng, thẻ lồng nhau đúng chuẩn, không bỏ thẻ đóng.
- Comment các block quan trọng — section nào làm gì, đặc biệt các phần có JS/CSS gắn vào; nếu phần tử có JS thao tác DOM lên nó, ghi rõ file JS + hàm nào đang dùng:
  ```html
  <!-- Bảng danh sách sinh viên — dữ liệu đổ vào bằng student-list.js, hàm renderStudentRow() -->
  <table id="student-table">
    ...
  </table>
  ```
- Đặt `id`/`class` rõ nghĩa (theo `NamingConvention.md`), vì đây thường là điểm nối giữa HTML–CSS–JS.

## 8. Coding Style Cơ Bản

- **1 dòng chỉ chứa 1 logic** — không gộp nhiều thao tác/điều kiện vào 1 dòng.

  **Không làm:**
  ```js
  if (input.value === "" || input.value.length < 3) showError("Tên quá ngắn");
  ```

  **Nên làm:**
  ```js
  const isEmpty = input.value === "";
  const isTooShort = input.value.length < 3;

  if (isEmpty || isTooShort) {
      showError("Tên quá ngắn");
  }
  ```

- Không dùng toán tử ba ngôi (`? :`) lồng nhau.
- Mỗi khai báo biến JS một dòng riêng, không gộp `let a = 1, b = 2;`.

## 9. Code Style Theo Từng Loại (Tóm Tắt)

| Loại code | Style áp dụng |
|---|---|
| JavaScript | `const`/`let`, tránh `var`; mỗi hàm 1 mục đích rõ ràng; đặt tên theo `NamingConvention.md`; ưu tiên rõ ràng tuyệt đối (Mục 4). |
| CSS | Ưu tiên class hơn ID; kebab-case; đẹp, UX/UI tốt, gợi ý animation cho phần tĩnh (Mục 6). |
| JSP (View) | Hạn chế scriptlet, ưu tiên JSTL; bắt buộc `<c:out>` chống XSS (Mục 1). |
| AJAX (fetch/XMLHttpRequest) | Luôn xử lý cả 2 nhánh thành công và lỗi; escape dữ liệu trước khi chèn vào DOM (Mục 4). |

**Màn hình dùng AJAX/gọi REST API:** vẫn áp dụng đầy đủ quy tắc trong file này. JS xử lý AJAX tách thành hàm riêng rõ mục đích (VD: `loadOrderList()`, `submitLoginForm()`), không viết logic gọi API xen kẽ trực tiếp trong event handler dài dòng.

## 10. Nguyên Tắc Tối Quan Trọng: Code Phải Viết Từ Trên Xuống Dưới

Áp dụng như Backend: code JS/CSS/JSP viết theo đúng thứ tự chạy logic từ trên xuống, đọc từ đầu file đến cuối là hiểu luồng — không nhảy qua nhảy lại. Constant/biến toàn cục JS đặt ở đầu file. Không gom các hàm "cùng loại" (VD: tất cả hàm validate) dồn lại 1 chỗ nếu phá vỡ thứ tự đọc theo luồng sử dụng thực tế của trang.

## 11. Lưu Ý Cuối

- **JSP:** comment đủ để biết DTO/attribute và khối màn hình; dùng `<c:out>` cho dữ liệu động từ người dùng; không đặt nghiệp vụ trong View.
- **JS (quan trọng nhất):** không cần tối ưu, không dùng logic nâng cao/lạ — miễn chạy đúng, không chậm là đạt; comment các bước `// Bn:`, chia bước con khi cần và giải thích ngắn điểm nối dữ liệu (Mục 2); escape dữ liệu (`escapeHtml()`/`textContent`) khi chèn vào DOM qua AJAX.
- **DOM:** không method chaining dài, không làm tắt, mỗi hàm 1 việc, comment rõ đang thao tác phần tử nào.
- **CSS:** comment ngắn gọn; id/class liên quan JS phải rõ ràng + ghi chú file JS **và tên hàm** đang dùng đến, hàm đó thao tác DOM ra sao; gợi ý animation cho phần tĩnh (chỉ đề xuất, không tự code); phải đẹp, đảm bảo UX/UI.
- **HTML:** rõ ràng, có comment, id/class đặt tên rõ nghĩa, ghi chú file JS + hàm nếu có JS thao tác DOM lên phần tử đó.
- Format: 4 spaces cho JS và HTML/JSP, 2 spaces cho CSS, K&R.
- Coding cơ bản: 1 dòng = 1 logic.
- Thứ tự code: từ trên xuống theo luồng chạy thực tế, chỉ constant ở đầu file.
