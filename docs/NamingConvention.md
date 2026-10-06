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
| Constraint / index | `PK_`, `FK_`, `UQ_`, `CK_`, `DF_`, `IX_`, `UX_` (unique filtered) + `<Bảng>_<Cột/ý nghĩa>` | `CK_Sessions_CancelType`, `UX_Persons_Phone_Active` |
| Giá trị liệt kê | Tiếng Anh PascalCase, xem mục 2b | `PtTransfer` |

## 2b. Enum / giá trị liệt kê: DB ↔ Java ↔ JSP/JS phải khớp 1–1 (BẮT BUỘC)

Lỗi hay gặp nhất của nhóm: DB lưu `Working`, Java so `"WORKING"`; enum này dùng `name()`, enum kia dùng `dbValue`; JSP gõ tay `'Active'`.
Từ v25 chỉ còn **1 cách duy nhất**:

| Tầng | Quy tắc | Ví dụ |
|---|---|---|
| **DB** | Lưu **tiếng Anh PascalCase**, đúng danh sách ở bảng dưới (cũng là CHECK constraint trong `1_Init_Schema.sql`). Hiển thị tiếng Việt là việc của View (GBR-01). | `'PtTransfer'`, `'NoCheckIn'` |
| **Java enum** | Hằng số `UPPER_SNAKE_CASE` + **chuỗi DB đi kèm**: `PT_TRANSFER("PtTransfer")`. Bắt buộc có `getDbValue()` và `static fromDbValue(String)` (không khớp → `null`, **không** tự trả giá trị mặc định). Có thêm `getDisplayName()` khi cần chữ tiếng Việt. | xem mẫu bên dưới |
| **Vị trí enum** | Lồng trong Entity của bảng (`Session.Status`, `Session.CancelType`). Dùng chung nhiều bảng thì đặt ở `constant/` (VD `Role`). | |
| **Đọc DB** | `X.fromDbValue(rs.getString("Status"))` | |
| **Ghi DB / tham số SQL** | `stmt.setString(i, x.getDbValue())`. **Cấm** `name()`, `toString()`, `ordinal()`. | |
| **Chuỗi trong câu SQL** | Ưu tiên tham số `?` + `getDbValue()`. Nếu buộc phải viết thẳng (`WHERE Status = 'Scheduled'`) thì chữ phải **trùng từng ký tự** với bảng dưới. | |
| **So sánh trong Java** | So enum với enum (`status == Session.Status.MISSED`), **cấm** so chuỗi `"Missed".equals(...)`. | |
| **JSP** | Không gõ tay giá trị DB. Dùng getter boolean (`${s.missed}`) hoặc `${s.status.dbValue}`; chữ tiếng Việt lấy từ `getDisplayName()`. | |
| **JS / JSON** | Gửi và nhận đúng `dbValue`. | `{"status":"Scheduled"}` |

```java
public enum CancelType {
    BY_PT("ByPt", "PT hủy"),
    BY_CUSTOMER("ByCustomer", "Khách hủy"),
    GYM_CLOSED("GymClosed", "Phòng tập nghỉ"),
    PACKAGE_EXPIRED("PackageExpired", "Gói PT hết hạn");

    private final String dbValue;
    private final String displayName;

    CancelType(String dbValue, String displayName) {
        this.dbValue = dbValue;
        this.displayName = displayName;
    }

    public String getDbValue() {
        return dbValue;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static CancelType fromDbValue(String value) {
        for (CancelType item : values()) {
            if (item.dbValue.equals(value)) {
                return item;
            }
        }
        return null;
    }
}
```

### Danh sách giá trị chuẩn (sinh từ CHECK trong `database/1_Init_Schema.sql` v25)

| Bảng.Cột | Giá trị DB (đúng từng ký tự) |
|---|---|
| `Customers.CustomerType` | `Self`, `Dependent` |
| `Customers.Status` | `New`, `Active`, `Frozen`, `Expired`, `Inactive` |
| `Employees.Position` | `Admin`, `Manager`, `PT`, `Receptionist` |
| `Employees.Status` | `Working`, `Resigned` |
| `Accounts.Status` | `Active`, `Locked`, `Inactive` |
| `VerificationCodes.Purpose` | `SignUp`, `Claim`, `Recovery`, `EmailChange`, `PhoneChange` |
| `Packages.PackageType` | `Membership`, `DayPass`, `PTSingle`, `PTCouple`, `SingleSession` |
| `Vouchers.DiscountType` | `Percent`, `Amount` |
| `Vouchers.ApplicableScope` | `MembershipAndDayPass`, `MembershipOnly` |
| `Shifts.ShiftType` | `Morning`, `Afternoon` |
| `Shifts.Status` | `NotOpened`, `Open`, `Closed` |
| `Orders.Channel` | `Counter`, `Online` |
| `Orders.Status` | `Pending`, `Paid`, `Cancelled` |
| `Transactions.PaymentMethod` | `Cash`, `Transfer`, `OnlineGateway` |
| `Transactions.Status` | `Pending`, `Success`, `Failed`, `Expired` |
| `Subscriptions.SubscriptionType` | `Membership`, `DayPass`, `PTSingle`, `PTCouple`, `SingleSession` |
| `Subscriptions.Status` | `Active`, `Frozen`, `Used`, `Expired`, `Completed`, `Cancelled` |
| `CustomerDeletionRequests.Status` | `Pending`, `Approved`, `Rejected` |
| `TimeSlots.Status` | `Free`, `Held`, `Booked`, `Withdrawn` |
| `Sessions.Status` | `Scheduled`, `Completed`, `Missed`, `Cancelled` |
| `Sessions.MissedReason` | `NoCheckIn`, `NoMembership`, `MembershipFrozen`, `MarkedAbsent` |
| `Sessions.CancelType` | `ByPt`, `ByCustomer`, `GymClosed`, `PackageExpired` |
| `SessionMoveLogs.MoveType` | `CustomerSwap`, `PtOnBehalfSwap`, `PtReschedule`, `PtTransfer` |
| `LeaveRequests.LeaveType` | `Annual`, `Sick`, `Unpaid` |
| `LeaveRequests.ShiftType` | `Morning`, `Afternoon`, `FullDay` |
| `LeaveRequests.Status` | `Pending`, `Approved`, `Rejected` |
| `Facilities.Category` | `Cardio`, `FreeWeights`, `Machines`, `Amenities` |
| `Facilities.Status` | `Available`, `UnderMaintenance`, `Retired` |
| `MaintenanceRequests.Priority` | `Urgent`, `Normal`, `Low` |
| `MaintenanceRequests.Status` | `Open`, `InProgress`, `Rejected`, `Closed` |
| `StaffAttendances.ShiftType` | `Morning`, `Afternoon` |
| `StaffAttendances.Status` | `Present`, `Late`, `Absent`, `OnLeave` |
| `TrainingPlans.PlanType` | `Workout`, `Meal` |
| `BlogPosts.Category` | `Training`, `Nutrition`, `News` |
| `BlogPosts.Status` | `Active`, `Inactive` |
| `Notifications.Audience` | `AllCustomers`, `ActiveMembers`, `PTs`, `Receptionists`, `AllStaff`, `Individual` |
| `Orders.CancelType` | `PaymentTimeout`, `ReplacedByNewOrder`, `InvalidPayment`, `CancelledByReceptionist` |
| `Packages.DurationUnit` | `Day`, `Month` |
| `AuditLogs.ActingRole` | `Admin`, `Manager`, `PT`, `Receptionist`, `Customer` (= `Role.getDbValue()`) |

Thêm hoặc đổi giá trị = sửa schema (quy trình `agents.md` Phần 5) **và** cập nhật bảng này **và** enum Java trong cùng 1 commit.
Enum đang lệch cần sửa: `BlogPost.Status` đang khai báo `Active`/`Inactive` (dùng `name()`), phải đổi về mẫu ở trên (`ACTIVE("Active")`).

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
