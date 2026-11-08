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
| Package | chữ thường, không dấu gạch | `com.gms.dao`, `com.gms.service` |
| Boolean method/biến | tiền tố `is`/`has`/`can` | `isActive`, `hasPermission()` |
| File JSP | kebab-case hoặc camelCase, mô tả rõ chức năng | `login.jsp`, `order-detail.jsp` |

**Lưu ý riêng cho MVC:**
- Class Model (entity) đặt tên số ít, khớp tên bảng: bảng `Orders` → class `Order`.
- Service layer: hậu tố `Service` — `OrderService`.

## 1b. Đặt tên theo ý nghĩa trong luồng đang làm

Tên cần giúp người đọc biết biến chứa gì, thuộc đối tượng nào và dùng ở bước nào. Viết đúng camelCase chưa đủ: `data`, `item`, `id`, `list` vẫn khó hiểu nếu không có ngữ cảnh.

| Dữ liệu | Tên nên dùng | Ý nghĩa |
|---|---|---|
| Mã tài khoản trong session | `accountId` | Khác `personId`, `employeeId` và `customerId` |
| PT đang phụ trách buổi | `ptEmployeeId` | Đọc từ `Sessions.PtEmployeeId`, không mặc định là PT của gói |
| Ngày hiện tại theo giờ VN | `today` | Kiểu `LocalDate`, lấy một lần cho mỗi lần xử lý |
| Có phải ngày hôm nay không | `isToday` | Kiểu `boolean`, tính bằng `slotDate.equals(today)` |
| Danh sách buổi DAO trả về | `sessions` | `List<PtSessionView>`, mỗi phần tử là một buổi |
| Các buổi được chọn cho một ngày | `sessionsOfDay` | Vẫn là các `PtSessionView` do DAO tạo |
| Lịch Service đã gom theo ngày | `scheduleDays` | `List<PtScheduleDayView>`, mỗi phần tử là một ngày |
| Ngày bắt đầu/kết thúc phạm vi | `fromDate`, `toDate` | Comment nói rõ có tính cả ngày biên không |

**Boolean:** dùng tiền tố `is`, `has`, `can`, `should` theo ý nghĩa: `isToday`, `hasMembership`, `canReschedule`. Không dùng `today` cho boolean khi trong cùng luồng đã có `today` là một ngày.

**Getter và JSP:** tên thuộc tính JSP theo getter JavaBean, không theo tên field trực tiếp:

```java
private final boolean isToday;

public boolean isToday() {
    return isToday;
}
```

JSP đọc `${day.today}`. Getter `getSlotDate()` tương ứng `${day.slotDate}`. Đổi field thành `isToday` không có nghĩa phải đổi EL thành `${day.isToday}`.

**DTO:** tên nói rõ một object chứa gì; nơi tạo được ghi trong Javadoc, không suy đoán chỉ từ hậu tố `View`:

- `PtSessionView`: một buổi, DAO tạo từ kết quả truy vấn.
- `PtScheduleDayView`: một ngày và các buổi của ngày, Service tạo từ `List<PtSessionView>`.
- Chỉ tạo DTO khi có nhu cầu cụ thể; không tạo toàn bộ Entity/DTO của module trước.

**Hàm:** nêu hành động và kết quả: DAO `findSessions(...)`, Service `getSchedule(accountId)`, helper `buildDayLabel(...)`. Các tên này là ví dụ theo PTDashboard, không ép module khác dùng cùng tên.

**Điểm nối frontend:** request attribute `scheduleDays` ở Servlet phải khớp `${scheduleDays}` trong JSP. `activeNav` là mục menu đang chọn, layout dùng chung đọc nó. CSS `.pt-schedule` mô tả khối lịch; `data-session-id` mang mã buổi cho JS, không phải nội dung in cho người dùng.

Tên cột SQL giữ đúng schema; alias dùng theo ý nghĩa kết quả, ví dụ `CustomerName`, `PartnerName`. Có thể dùng alias SQL ngắn `s`, `t`, `sub` khi phần JOIN thể hiện rõ bảng tương ứng; biến Java nên viết rõ `statement`, `resultSet`, `session`.

Khi đổi tên, kiểm tra tất cả chỗ gọi, constructor, getter, JSP EL, request attribute và JS/CSS liên quan. Không đổi tên hàng loạt ở module khác chỉ để đồng bộ hình thức.

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

### Danh sách giá trị chuẩn (sinh từ CHECK trong `database/1_Init_Schema.sql` v26)

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
| `Subscriptions.Status` | `Active`, `Frozen`, `Used`, `Expired`, `Cancelled` |
| `CustomerDeletionRequests.Status` | `Pending`, `Approved`, `Rejected` |
| `TimeSlots.Status` | `Free`, `Held`, `Booked`, `Withdrawn` |
| `Sessions.Status` | `Scheduled`, `CheckedIn`, `Missed`, `Cancelled` |
| `Sessions.MissedReason` | `NoCheckIn`, `NoMembership`, `MembershipFrozen` |
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
Khi rà enum, đối chiếu code và CHECK của schema hiện tại; không dựa vào nhận xét về phiên bản cũ. Nếu cột bắt buộc mà `fromDbValue()` trả null, DAO báo lỗi để Service xử lý; cột được phép NULL phải được xét riêng.

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
