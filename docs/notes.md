# File notes — mâu thuẫn tài liệu & quyết định đã chốt

Theo agents.md Phần 3, 4, 7.3: mọi chỗ SRS/SDS/prototype/chat nói khác nhau được ghi ở đây, kèm trạng thái.
Khi nhóm chốt xong 1 mục → đổi trạng thái thành **ĐÃ CHỐT**, ghi ngày + phương án, và sửa lại tài liệu gốc cho khớp.

## Hạn mức đổi buổi gói PT theo tháng (10/10/2026, ĐÃ CHỐT trong chat)

- P-08 đổi nghĩa: **số lượt đổi buổi cho mỗi tháng của gói PT**, mặc định 3. Hạn mức một gói = P-08 × số tháng của gói → gói 1/2/3 tháng được 3/6/9 lượt. Buổi lẻ vẫn không được đổi (BR-74).
- Schema không đổi: `Subscriptions.MaxSwapsSnapshot` đã lưu hạn mức riêng từng gói, chốt lúc bán (GBR-04), TINYINT đủ chứa. Service bán gói PT (UC-21) phải ghi `MaxSwapsSnapshot = P-08 × Packages.DurationValue` (gói PT luôn tính theo tháng, P-21); màn đổi buổi so `SwapsUsed` với `MaxSwapsSnapshot`, không đọc thẳng P-08.
- Đã sửa: `seedgen.py` (hằng `SWAPS_PER_MONTH`), mô tả P-08 trong `seeddata.py`, comment cột trong `1_Init_Schema.sql`, comment `SystemConfigService.MAX_SWAP_COUNT` và `Subscription`. Sinh lại `2_Seed_Data.sql`; runtest, negtest, bizcheck đều qua.
- Cần sửa SRS: BR-73 ("at most P-08 swaps per package") và mô tả P-08 ở III.4.

## Seed bảo lưu theo quyết định 09/10 (10/10/2026)

- `seedgen.py` trước đây chốt sẵn mọi buổi trong kỳ bảo lưu thành `Missed/MembershipFrozen` và nhả slot, kể cả buổi tương lai. Đã sửa: buổi chưa tới ngày giữ `Scheduled`, slot `Booked`; chỉ buổi đã qua ngày mới là `Missed/MembershipFrozen` (như JOB-04 chốt). Cập nhật `docs/database/SEED_CASES.md`.
- Còn để ngỏ (chưa sửa): 9 buổi `Missed/NoCheckIn` mà cùng ngày khách có check-in vào gym (check-in ngẫu nhiên trùng ngày); seed vẫn nhả slot về `Free` cho mọi buổi `Missed`, trong khi mục JOB-04 ghi JOB-04 không giải phóng slot.

## BF-08 — kiểm tra và cập nhật Main Business Flow (09/10/2026)

- Căn cứ bản “Sửa SRS: Báo vắng & Đánh giá PT” ngày 06/10/2026 do user gửi lại, BF-08 đã mở rộng thành **PT Package Session**, không còn chỉ là PT xử lý buổi. Mục 2.8 và sơ đồ Mermaid hiện mô tả toàn bộ vòng đời: sinh Scheduled → check-in → JOB-04 chốt; xử lý buổi chỉ là A2.
- Main flow và A1 đi qua JOB-04; A3 là khách đổi lịch, A4 là khách hủy. Ngoại lệ gồm thiếu quyền vào gym, bảo lưu và gói PT hết hạn. Không có thao tác PT báo vắng/điểm danh bằng tay. CheckedIn không chứng minh đã tập với PT.
- Dời vì PT bận (`PtReschedule`) và chuyển PT (`PtTransfer`) không tăng lượt đổi. Đổi hộ khách không có tài khoản (`PtOnBehalfSwap`) tăng `Subscriptions.SwapsUsed`, so với `MaxSwapsSnapshot` của gói; không ghi cứng 3 lần và không cho SingleSession dùng nhánh đổi hộ.
- Hủy bởi PT: `Cancelled / ByPt`, có lý do và xác nhận hoàn tiền mặt; vẫn tính buổi/hoa hồng, không tạo giao dịch hoàn tiền trong hệ thống. Không thay đổi quy tắc tiền.
- Nguồn đối chiếu: BR-62–71, BR-73–74, CBR-39/45/46/61; schema `Sessions`, `TimeSlots`, `SessionMoveLogs`, `Subscriptions` trong `database/1_Init_Schema.sql`.
- Hiện trạng code: JOB-04 đã có Job/Service/DAO chốt buổi, là một phần của BF-08 mở rộng. Riêng A2 mới có 4 nút disabled trong `dashboard.jsp`; `js/pages/pt/dashboard.js` chỉ mở/đóng dialog, chưa có backend lưu các thao tác này. Chưa kết luận mọi nhánh của BF-08 đã hoàn thiện.
- Bản sửa ngày 06/10 ghi thiếu membership → NoMembership; quyết định chat sau đó cho phép DayPass được ưu tiên: JOB-04 công nhận Membership hoặc DayPass hợp lệ. Những đề xuất UI/rating khác trong tài liệu đính kèm không tự động thay thế các quyết định user đã chốt sau đó.
- Phạm vi lần này chỉ cập nhật tài liệu; chưa triển khai thao tác ghi dữ liệu. Sơ đồ ảnh cũ của BF-08 trong bản Markdown đã được thay bằng Mermaid để sửa và đối chiếu dễ hơn; chưa sửa bản Word gốc.

## PTDashboard — thời tiết và giao diện (09/10/2026, ĐÃ CHỐT trong chat)

- Dùng Open-Meteo hiện có cho dashboard, giữ cache thành công 30 phút và thử lại sau lỗi 5 phút. Khi API lỗi, nền dùng `sunny` và thẻ thời tiết ẩn; các khối khác vẫn mở bình thường.
- `?weather=sunny|cloudy|rain|storm|snow|wind|night` dùng dữ liệu xem trước cố định, không gọi API; Servlet chỉ gắn class từ bảy giá trị này.
- Để nhận biết gió mạnh, yêu cầu thêm `current.wind_speed_10m` (km/h) ngoài các field API-04 trong SRS; Service dùng ngưỡng 35 km/h. Tuyết đọc mã WMO 71/73/75/77/85/86. Nắng nóng không có mã WMO riêng: xét nhiệt độ cao nhất 35°C hoặc nhiệt độ cảm nhận 39°C; nắng nóng gay gắt từ 38°C hoặc cảm nhận 42°C.
- Giao diện thời tiết dùng bạt che CSS và hiệu ứng nền trên riêng PT Dashboard; giữ các khối nghiệp vụ, sidebar và tài khoản hiện tại. CSS hiệu ứng tắt chuyển động khi trình duyệt báo `prefers-reduced-motion`.

## My PT Sessions — kiến thức cần nắm trước khi code (09/10/2026)

- Màn hình dành cho Customer xem toàn bộ buổi PT của chính mình, gồm buổi phát sinh từ `PTSingle`, `PTCouple` và `SingleSession`; customer không được xem hoặc sửa buổi của người khác.
- Route và tên file theo cấu trúc dự án: `customer.MyPtSessionServlet` → `/customer/pt-sessions` → `WEB-INF/jsp/customer/my-pt-sessions.jsp`. Luồng chuẩn là Servlet nhận account/customer hiện tại → Service xử lý nghiệp vụ → DAO đọc SQL → DTO trả dữ liệu → JSP chỉ hiển thị.
- Dữ liệu chính lấy từ `Sessions` và `TimeSlots`; nối `Subscriptions` để biết gói/loại buổi và khách sở hữu, nối `Employees`/`Persons` để hiện PT phụ trách. `SessionMoveLogs` chỉ dùng khi cần xem lịch sử đổi buổi, không dùng để tự suy ra trạng thái hiện tại.
- Trạng thái cần hiển thị: `Scheduled`, `CheckedIn`, `Missed`, `Cancelled`. Khi `Missed` phải phân biệt `NoCheckIn`, `NoMembership`, `MembershipFrozen`; khi `Cancelled` phải phân biệt `CancelType` nếu màn hình cần giải thích.
- Buổi tương lai đang nằm trong kỳ bảo lưu **không đổi thành `Missed` trước ngày tập**. Màn hình có thể hiện cảnh báo “Buổi nằm trong thời gian bảo lưu”; trạng thái vẫn là `Scheduled`. Nếu bảo lưu kết thúc sớm, buổi sau `ActualEndDate` vẫn giữ `Scheduled`.
- JOB-04 chỉ chốt các buổi `Scheduled` đã qua ngày. Màn hình không tự chốt buổi, không tự xác nhận khách đã tập và không thay thế check-in.
- Các thao tác customer cần tách rõ khỏi thao tác xem: chỉ buổi `Scheduled` chưa bắt đầu mới có thể đổi/huỷ theo điều kiện BF-08; buổi đã `CheckedIn`, `Missed` hoặc `Cancelled` là lịch sử, không cho sửa trực tiếp.
- Customer swap chỉ chọn slot được công bố và còn trống theo giới hạn thời gian/tuần của nghiệp vụ; đổi buổi phải giữ đúng gói, PT và điều kiện membership, đồng thời ghi `SessionMoveLogs` và tăng `SwapsUsed` khi áp dụng. Không được sửa thẳng `TimeSlots` mà bỏ qua transaction.
- Hủy bởi customer là `Cancelled / ByCustomer`, vẫn tiêu buổi và không hoàn tiền; cần lưu account thực hiện và thời điểm theo schema. Đây là khác với PT hủy (`ByPt`) và gym hủy (`GymClosed`).
- Với `PTCouple`, màn hình phải hiện đủ hai khách liên quan nếu dữ liệu có partner; các quy tắc đủ điều kiện/check-in của gói đôi không được suy ra chỉ từ tên người đứng tên.
- Không đưa logic SQL, tính trạng thái hoặc quy tắc đổi/hủy vào JSP. DTO nên chứa sẵn nhãn, thời gian, tên PT, loại gói, trạng thái và quyền thao tác để JSP chỉ render.

## JOB-04 — viết lại từng bước (08/10/2026, ĐÃ CHỐT trong chat)

- Đã gỡ DAO, Service, class job, test và khối SQL chốt buổi cũ để cùng người học viết lại từ đầu; scheduler tạm thời không đăng ký JOB-04.
- Trong thời gian viết lại, bản build mới chưa tự chốt buổi PT. Giữ các job khác, schema, dữ liệu và các thành phần dùng chung.
- Nghiệp vụ đã chốt: chạy trong chuỗi hằng ngày bắt đầu lúc 00:05 giờ VN, sau JOB-03; chọn buổi Scheduled có ngày tập trước hôm nay, kể cả buổi sót từ nhiều ngày trước.
- Xét theo ngày tập: không dùng được gym và có người bảo lưu → MembershipFrozen; không dùng được gym và không ai bảo lưu → NoMembership; tiếp theo có check-in cùng ngày → CheckedIn; còn lại → NoCheckIn.
- PTSingle/SingleSession xét một khách. PTCouple chỉ coi cả buổi không dùng được gym khi cả hai đều không dùng được; nếu đi tiếp bước check-in thì chỉ cần một trong hai có bản ghi cùng ngày.
- SingleSession mua trên web phải có membership trước khi mua. User đã bổ sung DayPass cũng có thể là quyền vào gym hợp lệ khi chốt; bản Service hiện áp dụng quy tắc Membership hoặc DayPass cho mọi loại buổi PT.
- Check-in cùng ngày chỉ phản ánh khách có đến gym, không xác nhận thực tế tập PT. Chưa chuyển sang xét trong khung giờ của buổi; tỷ lệ NoCheckIn và thiếu membership/bảo lưu được tách riêng khi thống kê.
- JOB-04 chỉ xử lý buổi đã qua ngày, nên không giải phóng TimeSlot khi chốt Missed; slot quá khứ không còn đặt lại được. Bảo lưu chỉ tạo cảnh báo cho buổi tương lai, không chốt trạng thái trước ngày tập.
- **Quyết định cập nhật 09/10/2026:** Không đánh dấu các buổi tương lai là `Missed/MembershipFrozen` ngay khi tạo bảo lưu. Buổi chưa diễn ra giữ `Scheduled` và dashboard chỉ hiện cảnh báo “Buổi nằm trong thời gian bảo lưu”. JOB-04 chốt sau khi ngày tập kết thúc, dùng khoảng bảo lưu thực tế (`ActualEndDate`, nếu có; nếu chưa thì `PlannedEndDate`). Nếu khách dừng bảo lưu sớm, các buổi tương lai sau `ActualEndDate` vẫn giữ `Scheduled`; buổi đã chốt `Missed/MembershipFrozen` không tự khôi phục để tránh làm sai lịch sử và hoa hồng.
- Khi viết lại: comment rõ nguồn dữ liệu, điều kiện nghiệp vụ, thứ tự ưu tiên, trường hợp gói đôi và bảo lưu kết thúc sớm; giải thích transaction riêng từng buổi, kiểm tra lại Scheduled và giữ nguyên CommissionAmount.
- Bước 1: SessionAutoCloseData trong package dto có 10 field dữ liệu của một buổi, thêm hai cờ DayPass riêng để Service có thể xét theo loại buổi sau này. Không có setter hay logic chốt; không có timeSlotId vì JOB-04 không cập nhật slot.
- Bước 2: AutoCloseSessionDAO có findOverdueSessionIds, findDataForClosing và closeScheduledSession. Hàm đọc tạo DTO từ ngày tập, gói PT, hai khách, membership, DayPass, bảo lưu và check-in. Hàm ghi kiểm tra lại Scheduled, giữ nguyên CommissionAmount/TimeSlots.
- Bước 3: AutoCloseSessionService đã có evaluate và runJob. Service coi Membership hoặc DayPass là quyền vào gym, ưu tiên bảo lưu, xử lý từng buổi bằng transaction riêng và ghi AuditLog cùng transaction.
- Bước 4: Đã tạo AutoCloseUnmarkedSessionsJob và đăng ký sau JOB-03 trong BackgroundJobScheduler. Job chỉ gọi Service; chưa viết lại test JOB-04.

## PTDashboard — phạm vi “Cần chú ý” (07/10/2026, ĐÃ CHỐT trong chat)

- Lịch dạy vẫn hiển thị 7 ngày, từ hôm nay đến hôm nay + 6 ngày.
- Cảnh báo membership/bảo lưu xét 14 ngày, từ hôm nay đến hôm nay + 13 ngày; tính cả hai ngày biên theo giờ VN.
- Kiểm tra membership theo ngày từng buổi, tính cả gói đã mua nối tiếp; không cảnh báo chỉ vì hạn membership khác hạn gói PT.
- Gom theo khách + gói PT + loại cảnh báo, chỉ ra buổi gần nhất có vấn đề trong phạm vi trên.
- Gói đôi xét từng người và ghi rõ khi người còn lại vẫn dùng được gym; không kết luận cả buổi không tập được chỉ vì một người thiếu membership/bảo lưu.
- Số buổi còn trong lịch là tổng buổi Scheduled chưa bắt đầu của gói, không giới hạn vào 14 ngày.
- Ngưỡng cảnh báo gói PT sắp hết theo ngày/số buổi chưa chốt; không tự lấy 14 ngày làm ngưỡng này.
- Đã nối hai DTO, PtDashboardDAO.findClientPackages(...) và PtDashboardService.getAttentionItems(...) qua Servlet/JSP; khối cảnh báo đọc attentionItems/attentionError, không cần JavaScript.
- Bố cục 08/10/2026: cảnh báo đưa lời nhắc lên ngay dưới tên khách, tách thông tin gói PT và membership; lịch tách giờ/tên khách, nhấn hôm nay. Dùng token theme-staff và CSS riêng của dashboard.
- PtClientPackageView là dữ liệu DAO trả cho Service. PtAttentionView là kết quả Service tạo, chứa field hiển thị trực tiếp; không chứa object PtClientPackageView bên trong.
- DAO lấy buổi Scheduled chưa bắt đầu để dashboard hiển thị lịch; cảnh báo bảo lưu được tính riêng theo `FreezeRecords`, không dựa vào việc chốt sẵn buổi tương lai.

## PTDashboard — Khách phụ trách (08/10/2026)

- Hiện đầy đủ gói PT Active và buổi lẻ Scheduled chưa bắt đầu, không giới hạn 7/14 ngày hay số dòng.
- PTSingle/PTCouple lọc theo Subscriptions.PtEmployeeId; SingleSession lọc theo Sessions.PtEmployeeId thực tế của buổi sắp dạy.
- Chỉ lấy đơn Paid; gói đôi hiện hai tên trong một dòng. Một khách có nhiều gói phù hợp vẫn có nhiều dòng.
- Số buổi đếm theo toàn gói: Scheduled và ngày/giờ bắt đầu > thời điểm xét, kể cả buổi giao PT khác. Gói Active có 0 buổi tương lai vẫn hiện.
- Đã nối PtAssignedPackageView, PtDashboardDAO.findAssignedPackages và PtDashboardService.getAssignedPackages qua Servlet/JSP bằng assignedPackages/assignedPackagesError; không cần JavaScript.
- JSP in Subscriptions.EndDate dưới nhãn “Hạn sử dụng” cho cả gói PT/buổi lẻ, không gọi đây là ngày dạy thực tế sau khi dời buổi.

## PTDashboard — Kết quả tháng và hoa hồng (08/10/2026)

- Lọc theo Sessions.PtEmployeeId và TimeSlots.SlotDate trong tháng hiện tại giờ VN. Scheduled kết thúc đúng now thuộc nhóm chờ chốt; chưa kết thúc thuộc nhóm dự kiến.
- Đếm riêng CheckedIn, Missed/NoCheckIn, Missed/NoMembership, Missed/MembershipFrozen và tất cả Cancelled. Mẫu số tỷ lệ gồm cả năm loại, kể cả Cancelled; Pending/Projected chưa được tính tỷ lệ.
- Tiền đọc Sessions.CommissionAmount đã lưu: đã chốt gồm CheckedIn, Missed và Cancelled ByPt/ByCustomer; GymClosed/PackageExpired chỉ tính vào số buổi hủy, không cộng tiền. SUM dùng BIGINT/Java long.
- PtDashboardDAO tạo PtCommissionSummaryView với 10 số gốc; PtDashboardService bổ sung tổng buổi đã có kết quả và năm tỷ lệ, làm tròn hai chữ số. Servlet chuyển commissionSummary hoặc commissionSummaryError sang JSP; không cần JavaScript cho khối này.

## ✅ v25 (03/10/2026): đã chốt và xử lý

- **D1.2:** 1 Person = 1 tài khoản, chọn vai trò.
- **D1.3:** thời hạn gói theo `DurationUnit` / `DurationValue`.
- **D2:** thêm cột cho Orders, MaintenanceRequests, Notifications.
- **D3:** seed viết lại toàn bộ, tính theo mốc và giờ chạy.
- **D5:** đổi tên PK.
- **D6:** bỏ TokenHash / PasswordSetup.
- **A3:** link thanh toán 5 phút, không cấu hình.
- **A9:** múi giờ `Asia/Ho_Chi_Minh`.

Xem `docs/database/SCHEMA_CHANGES_v25.md`.
**Còn chờ nhóm chốt:** rule nghỉ phép của Manager (UC-15).

## A. Mâu thuẫn đang CHỜ NHÓM CHỐT (phát hiện 24/09/2026)

| # | Chủ đề | Nguồn 1 | Nguồn 2 | Đề xuất | Tạm thời trong code |
|---|---|---|---|---|---|
| 1 | Độ dài mật khẩu | SRS 1.2.2: **6–64** ký tự | DBR-02 + prototype `dang-ky.js`: **8–64**, có chữ và số | Chốt 8–64 có chữ + số (an toàn hơn, 2/3 nguồn đồng ý) → sửa SRS 1.2.2 | `PASSWORD_MIN_LENGTH = 8` ở `AppConstants.java` và `common.js` |
| 2 | Hạn nộp đơn nghỉ phép | P-12: **15:00** ngày hôm trước | Prototype `pt/nghi-phep.html`: "trước **23:59** ngày hôm trước" | Theo SRS (15:00, Manager cấu hình được) → sửa câu gợi ý trên prototype | Đọc P-12 từ SystemConfig, không hard-code |
| 3 | Thời hạn link thanh toán VNPay | System Parameters: **5 phút** (không cấu hình) | JOB-06: "mặc định **10 phút** (DC-12)" | Chốt 1 con số, sửa lại bên còn lại | `AppConstants.PAYMENT_LINK_VALID_MINUTES = 5` |
| 4 | Đăng ký tài khoản | SRS 1.2: **2 bước** (kiểm tra SĐT → nhập thông tin) | Prototype (PROTOTYPE_CHANGES #2): **gộp 1 form** | Chọn 1 và cập nhật bên còn lại; nếu gộp form thì vẫn phải chạy UC-IDENT trước khi tạo tài khoản | Chưa code màn này |
| 5 | Bảng Person | DBR-22, 23, 27, 28, 30 nói SĐT/email "lưu trên **Person**" | SRS 3.2 không có entity Person | **Đã rõ theo DB:** `1_Init_Schema.sql` có bảng `Persons` (khớp DBR) → cần sửa SRS 3.2 cho khớp (thêm Person, bỏ CustomerPhone) | Code theo DB |
| 6 | Ngôn ngữ thông báo | SRS III.5: câu MSG viết **tiếng Anh** | GBR-01: giao diện **tiếng Việt** | Giữ mã MSG, hiển thị bản dịch tiếng Việt | Đã dịch trong `Messages.java` + `common.js` — nhờ nhóm duyệt câu chữ |
| 7 | Xuất file giao dịch | SRS 7.4.2: xuất **Excel** | SRS 12.1: xuất **CSV** | Giữ đúng từng màn: 7.4.2 xuất .xlsx (Apache POI đã có trong pom), 12.1 xuất CSV | — |
| 8 | Mã OTP khi đăng ký | Account giữ "verification code, code expiry" | Lúc đăng ký **chưa có Account** để lưu mã | **Đã giải quyết trong DB:** bảng `VerificationCodes` (AccountId cho phép NULL) → sửa SRS 3.2 mục Account | Code theo DB |
| 9 | Múi giờ | SRS 5.0 ghi "Asia/**Ha_Noi**" | ID này không tồn tại trong Java/IANA | Dùng `Asia/Ho_Chi_Minh` (cùng UTC+7) → sửa SRS | `DateTimeUtil.GYM_ZONE` |
| 10 | SRS còn thiếu mã thông báo | DBR-01, 02, 03, 04 có luật | III.5 không có câu báo lỗi tương ứng | Bổ sung mã MSG vào SRS | Tạm dùng `EXTRA_*` trong `Messages.java`/`common.js` |

## D. Rà soát database `1_Init_Schema.sql` + `2_Seed_Data.sql` (24/09/2026) — CHỜ NHÓM CHỐT

Theo agents.md Phần 5: **chưa ai sửa schema**. Trước khi sửa: copy schema hiện tại vào Notion → nhóm chốt phương án → Leader viết file `database/migrations/NNN_...sql`.

### D1. Ảnh hưởng ngay tới việc code (ưu tiên cao)

| # | Vấn đề | Căn cứ | Đề xuất |
|---|---|---|---|
| D1.1 | Hash mật khẩu trong seed là chuỗi giả → không đăng nhập được | codeImplementationBackend Mục 11, BCrypt | **Đã có** `database/3_Dev_Passwords.sql` (chỉ đổi dữ liệu mẫu, mật khẩu `Gms@2026`) |
| D1.2 | ✅ **ĐÃ CHỐT 03/10/2026 (rule v25)** — phương án (a): 1 Person = 1 SĐT, 1 email, 1 tài khoản cho mọi vai trò chưa Inactive (DBR-65). Có ≥ 2 vai trò → đăng nhập xong chọn vai trò, đổi vai trò không cần đăng nhập lại (CBR-113, `/select-role`). Nhân viên không tự xử lý hồ sơ khách của mình (CBR-115, `SelfServiceGuard`). Schema chỉ đổi UNIQUE(PersonId) của Customers/Employees thành filtered (migration 002) | SRS 3.2 sửa câu "never both" | Đã code |
| D1.3 | Thời hạn gói lưu bằng **ngày** (`DurationDays` 30/90/180/365) | GBR-03 + DBR-19 tính theo **tháng** (05/09 → 05/10; 31/01 → 28/02) | Đổi sang `DurationValue` + `DurationUnit` (Day/Month), hoặc `DurationMonths` + DayPass riêng |

### D2. Thiếu cột so với SRS/prototype

**24/09/2026 — Leader chốt và đã áp dụng 4 mục** (Employees ×2, VerificationCodes, LeaveRequests): sửa `1_Init_Schema.sql`, `2_Seed_Data.sql`, thêm `database/migrations/001_pt_profile_setup_token_leave_reminder.sql`. Còn lại MaintenanceRequests, Notifications, Orders — chờ chốt khi làm phase tương ứng.

| Bảng | Thiếu | Màn / luật cần |
|---|---|---|
| MaintenanceRequests | `RequestCode` (BT-xxxx), `Priority` (Urgent/Normal/Low), `WarningTagPlaced`, `AcceptanceNote` | SRS 8.2.1.1, 8.2.1.2; prototype `lt/bao-tri` |
| Notifications | `Audience` (All customers / Active members / PT / Receptionists / All staff), `ScheduledAt` | SRS 11.2.1 — gửi hẹn giờ còn cần thêm 1 job, SRS 5.3 chưa có |
| Orders | `PackageBenefitsSnapshot` | DBR-50 (giá + mô tả + **quyền lợi** copy vào đơn) |
| ~~Employees~~ ✅ | ~~`Specialty`, `YearsOfExperience`~~ — **đã thêm** (migration 001) | SRS 2.1.1 danh sách PT trang chủ; prototype `kh/dat-buoi-le` lọc theo chuyên môn |
| ~~Employees~~ ✅ | ~~`ResignationReason`~~ — **đã thêm**, bắt buộc khi Resigned (migration 001) | SRS 5.1.2 "Reason — required" |
| ~~VerificationCodes~~ ✅ | ~~Purpose `PasswordSetup` + `TokenHash`~~ — **đã thêm**, `Code` cho phép NULL (migration 001) | MSG38, P-15 — tài khoản Lễ tân tạo tại quầy. **Không còn dùng từ 27/09** (xem D6) |
| ~~LeaveRequests~~ ✅ | ~~`ReminderSentAt`~~ — **đã thêm** (migration 001) | JOB-07 nhắc Manager — không có cột này job chạy mỗi phút sẽ nhắc lặp liên tục |

### D3. Dữ liệu mẫu lệch luật (sửa trong `2_Seed_Data.sql`, không đụng schema)

- Khách 9, 10, 14 để `Active` nhưng chưa có gói nào Paid → phải là `New` (DBR-34, DBR-48).
- `Subscriptions.EndDate` tính lẫn 3 cách: theo tháng (Sub 1, 2, 3, 6, 7, 8, 12), theo ngày (Sub 4, 15), lệch 1 ngày (Sub 10, 11). Chốt D1.3 rồi tính lại.
- Sub 4 đang bảo lưu nhưng đã cộng sẵn ngày bảo lưu vào EndDate → khi JOB-01 mở lại sẽ cộng lần 2 (DBR-51 chỉ cộng khi kết thúc bảo lưu).
- `P-16 = '03,05,07,08,09'` (2 chữ số) trong khi code kiểm tra đầu số 3 chữ số; SRS còn ghi P-16 là cấu hình triển khai, không phải tham số Manager sửa → chốt 1 nơi lưu.
- `P-04 = '1,30,90,180,365'` (ngày, có gói 6 tháng) vs SRS P-04 "1 ngày, 1, 3, **9**, 12 tháng" vs form 4.2.2 ví dụ "6 tháng" vs prototype có "Gói 9 Tháng" → SRS tự mâu thuẫn, cần chốt danh sách.
- Đơn 17: buổi PT lẻ bán **tại quầy**, trong khi UC-37 ghi buổi lẻ chỉ đặt online.
- Gói PT sinh 22 buổi (`GeneratedSessionCount = 22`) nhưng seed chỉ tạo 6 buổi → số buổi còn lại hiển thị sẽ sai khi test.
- Seed cố định "hôm nay = 23/09/2026": chạy vào ngày khác thì đơn Pending, slot Held, khóa tạm đều đã quá hạn → JOB-05/06 sẽ hủy ngay khi app khởi động. Chấp nhận, hoặc viết script dời mốc ngày.

### D4. Ảnh (DBR-08)

- Cột đủ: `Persons.Avatar`, `Customers.CheckInPhoto`, `BlogPosts.CoverImage` (`StaffAttendances.CheckInPhoto` cho chấm công — chưa làm). PT ở trang chủ dùng chữ cái đầu tên, không cần ảnh (SRS 2.1.1).
- **Chưa có file ảnh thật:** seed trỏ `/uploads/checkin/kh-1001.jpg`, `/uploads/blog/squat.jpg`… nhưng không có file → cần: thư mục upload ngoài project (`app.upload-dir`), 1 Servlet trả ảnh có phân quyền (ảnh check-in chỉ nhân viên xem, ảnh blog công khai), ảnh bìa mặc định theo category (SRS 2.1.1) đặt trong `webapp/images/`, avatar mặc định = chữ cái đầu tên.

### D5. Nhỏ
- PK `CustomerDeletionRequests.DeletionRequestId` chưa đúng quy tắc `<TênBảngSốÍt>Id` (NamingConvention Mục 2) → `CustomerDeletionRequestId`.
- Tiền dùng `INT` (tối đa ~2,1 tỷ đ) — đủ cho gym; Java vẫn đọc bằng `long` (`rs.getLong`).
- `TimeSlots` chỉ chặn trùng giờ bắt đầu; chồng giờ (09:00 và 09:30) phải chặn ở Service (DBR-33).

### D6. Nợ kỹ thuật (giữ trong schema, code không dùng)

| Thành phần | Ghi chú |
|---|---|
| VerificationCodes.TokenHash, Purpose 'PasswordSetup', CK_VerificationCodes_CodeOrToken, UX_VerificationCodes_TokenHash | Không còn dùng từ 27/09 (bỏ luồng link đặt mật khẩu, CBR-24/31 chuyển sang Quên mật khẩu). Giữ nguyên để khỏi tạo lại DB; dọn khi chốt schema cuối. |

## B. Quyết định kỹ thuật đã áp dụng (Leader, 24/09/2026)

| Quyết định | Lý do |
|---|---|
| Package gốc `com.gms` (thay cho ví dụ `com.project` trong tài liệu quy tắc) | Tên thật của dự án |
| Tomcat **10.1** + Java **17** + Jakarta EE 10 | Tomcat 10 dùng `jakarta.*`; 10.1 là bản đang được hỗ trợ |
| Servlet chia package con theo vai trò (auth, guest, customer, pt, receptionist, staff, manager, admin, payment) | ~70 Servlet — đúng ngoại lệ "1 layer quá nhiều class" trong agents.md Phần 2; khớp luôn với Filter phân quyền theo URL |
| Mỗi Filter vai trò tự kiểm tra cả đăng nhập | `@WebFilter` không đảm bảo thứ tự chạy, không được dùng web.xml để xếp thứ tự |
| Đăng xuất mọi thiết bị bằng `SessionRegistryListener` (RAM) | Không phải sửa database |
| Job chạy ngầm bằng `ScheduledExecutorService` của JDK | Không thêm thư viện (Quartz) |
| Kết nối DB bằng `DriverManager` qua `DBContext` | Đơn giản, đủ cho quy mô đồ án; đổi sang connection pool sau chỉ sửa 1 file |
| Bo góc md/lg giảm 19% (`--radius-reduce-ratio: 0.81`) | Yêu cầu của Leader trong chat |
| Khu Manager/Admin giữ bo góc nhỏ 6px + font Work Sans như mockup | Giữ đúng prototype từng vai trò |

## C. File tài liệu được `agents.md` nhắc tới nhưng chưa có trong repo

`frontend-design-pipeline-SWP391-v4.md`, `shared-library-guide.md` — nếu nhóm còn dùng, thêm vào thư mục gốc.
Hướng dẫn dùng tokens/common hiện có: `docs/frontend-shared-usage.md`.
