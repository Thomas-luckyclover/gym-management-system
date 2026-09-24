# NamingConvention.md — Quy Tắc Đặt Tên (Java / JavaScript / CSS)

Áp dụng thống nhất cho toàn bộ dự án. Mọi code mới (kể cả code do AI sinh ra) phải tuân theo đúng quy tắc bên dưới.

> ⚠️ **LƯU Ý CỰC KỲ QUAN TRỌNG:** Dù các thành viên khác trong nhóm có thể không dùng hết, không đọc hết, hoặc không hiểu vì sao có từng quy tắc trong file này, AI **vẫn bắt buộc áp dụng đầy đủ 100% nội dung dưới đây** mỗi khi hỗ trợ code (vibecode) cho user hiện tại — **không được tự ý bỏ qua, rút gọn, nới lỏng, hay coi nhẹ bất kỳ quy tắc nào** chỉ vì cho rằng người khác không theo hoặc không cần đến mức đó.

## 1. Java — Backend (Servlet / Model / DAO)

| Đối tượng | Quy tắc | Ví dụ |
|---|---|---|
| Class, Interface | PascalCase | `UserService`, `OrderDAO`, `IPaymentHandler` |
| Servlet (Controller) | PascalCase, hậu tố `Servlet` hoặc `Controller` | `LoginServlet`, `OrderController` |
| DAO | PascalCase, hậu tố `DAO` | `UserDAO`, `ProductDAO` |
| Method | camelCase, động từ đầu tiên | `getUserById()`, `calculateTotal()` |
| Biến (variable) | camelCase | `userName`, `totalPrice` |
| Hằng số (constant) | UPPER_SNAKE_CASE | `MAX_LOGIN_ATTEMPT`, `DEFAULT_PAGE_SIZE` |
| Package | chữ thường, không dấu gạch | `com.project.dao`, `com.project.service` |
| Boolean method/biến | tiền tố `is`/`has`/`can` | `isActive`, `hasPermission()` |
| File JSP | kebab-case hoặc camelCase, mô tả rõ chức năng | `login.jsp`, `order-detail.jsp` |

**Lưu ý riêng cho MVC:**
- Class Model (entity) đặt tên số ít, khớp tên bảng: bảng `Orders` → class `Order`.
- Service layer: hậu tố `Service` — `OrderService`.

## 2. Database — SQL Server (tham chiếu nhanh)

| Đối tượng | Quy tắc | Ví dụ |
|---|---|---|
| Tên bảng | PascalCase, số nhiều | `Users`, `Orders`, `OrderDetails` |
| Tên cột | PascalCase | `UserId`, `CreatedDate`, `TotalAmount` |
| Khóa chính | `<TenBang(so it)>Id` | `UserId`, `OrderId` |
| Khóa ngoại | trùng tên cột khóa chính ở bảng gốc | `OrderDetails.OrderId` tham chiếu `Orders.OrderId` |
| Stored Procedure | tiền tố `sp_`, PascalCase | `sp_GetUserById` |

## 3. JavaScript — Frontend

| Đối tượng | Quy tắc | Ví dụ |
|---|---|---|
| Biến, function | camelCase | `userName`, `getTotalPrice()` |
| Hằng số toàn cục | UPPER_SNAKE_CASE | `const MAX_RETRY = 3;` |
| Class (nếu dùng ES6 class) | PascalCase | `class CartManager {}` |
| Tên file `.js` | kebab-case | `cart-handler.js`, `form-validate.js` |
| Boolean | tiền tố `is`/`has`/`should` | `isLoading`, `hasError` |
| Event handler function | tiền tố `handle` hoặc `on` | `handleSubmit()`, `onClickLogin()` |
| DOM id (nếu JS thao tác) | camelCase, khớp với HTML | `document.getElementById('userForm')` |

## 4. CSS

| Đối tượng | Quy tắc | Ví dụ |
|---|---|---|
| Class CSS | kebab-case | `.user-card`, `.btn-primary` |
| ID CSS | camelCase (hạn chế dùng ID để style) | `#mainHeader` |
| File CSS | kebab-case, theo module/trang | `login-page.css`, `order-detail.css` |
| Biến CSS (custom property) | kebab-case, tiền tố `--` | `--primary-color`, `--font-size-base` |
| BEM (nếu áp dụng cho component phức tạp) | `block__element--modifier` | `.card__title--highlight` |

## 5. Nguyên Tắc Chung (Áp Dụng Mọi Ngôn Ngữ)

- **Rõ nghĩa hơn ngắn gọn:** `userList` tốt hơn `ul`, `uL`.
- **Không viết tắt tùy tiện:** tránh `usrNm`, `ordDtl` — trừ các từ viết tắt phổ biến đã thống nhất trong team (VD: `id`, `url`).
- **Nhất quán số ít/số nhiều:** danh sách/collection dùng số nhiều (`orders`, `userList`), một đối tượng dùng số ít (`order`, `user`).
- **Không đặt tên trùng khái niệm khác nhau:** ví dụ không dùng `data` chung chung cho nhiều mục đích khác nhau trong cùng 1 file.
- **Tên file/class phải phản ánh đúng chức năng**, không đặt tên tạm kiểu `test1.js`, `abc.jsp`.

## 6. Khi Có Xung Đột Với Tài Liệu Khác

Nếu SRS/SDS dùng tên khác với quy tắc ở đây (VD: tên bảng/cột đã có sẵn từ trước), áp dụng theo đúng mức ưu tiên đã nêu trong `agents.md` (Chat > SRS > SDS/notes > khác) và báo rõ sự khác biệt trước khi đổi tên hàng loạt.
