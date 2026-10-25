# File notes — mâu thuẫn tài liệu & quyết định đã chốt

## Chỉ số cơ thể của khách — /customer/body-stats (25/10/2026, ĐÃ CODE) + làm mới seed
- **DB (đã báo người dùng)**: bảng mới `BodyMeasurements` (CustomerId, MeasuredOn, WeightKg, HeightCm, BodyFatPercent?, WaistCm?, Note?). UNIQUE (CustomerId, MeasuredOn): 1 lần đo / ngày, lưu lại cùng ngày = ghi đè (MERGE). BMI **không lưu**, Service tính. Ngoài SRS, chỉ khách xem; PT đo ở phòng theo dõi riêng (không nối dữ liệu).
- **Màn**: 4 ô số (cân nặng, BMI + nhóm BMI châu Á: <18,5 / 18,5–22,9 / 23–24,9 / ≥25, % mỡ, vòng eo; thay đổi so với lần đầu), biểu đồ đường SVG do Service tính toạ độ (trục ngang theo ngày thật, chọn chỉ số bằng `?metric=`, không JS), form ghi lần đo (chiều cao điền sẵn lần trước), bảng lịch sử + xóa (hộp xác nhận chung của common.js). Menu khách: "Chỉ số cơ thể".
- **Seed**: giữ mốc lịch sử 03/10 (SEED_CASES.md không đổi), thêm mục 8b "làm mới cho demo": gia hạn gói tập 21–23/10 (Bích, Quân, Mai, Hà + 12 khách thường) → Active; gói PT mới từ 26/10 (An PT Sơn 07:00, Quân PT Long 11:00 từ 02/11, Hưng & Hà gói đôi PT Trang 19:30, Mai gói PT 2 tháng nối tiếp từ 09/11); 4 buổi lẻ tuần 26/10; chỉ số cơ thể của Mai (6 lần) và An (5 lần). Khung PT công bố kéo tới 31/12. runtest / negtest / bizcheck đều qua.

## Merge develop vào dev_mb1_MinhLC (26/10/2026)
- `develop` đổi `LeaveRequests`: bỏ `LeaveDate`, thay bằng khoảng `FromDate`..`ToDate` (+ `Days`, `AssignedToEmployeeId`). 2 câu SQL của nhánh này còn đọc `LeaveDate` → màn Lịch dạy, Xử lý buổi, Bán gói PT lỗi "Hệ thống đang bận". Đã sửa:
  - `PtScheduleDAO.findLeaves`: lấy đơn có khoảng chạm [from, to], tách thành 1 `LeaveInfo` / ngày (`isOnLeave` giữ nguyên).
  - `PtSessionHandleDAO.SLOT_SQL`: `t.SlotDate BETWEEN lr.FromDate AND lr.ToDate`.
- `2_Seed_Data.sql` trên develop đã được sửa tay phần LeaveRequests / LeaveRequestTransfers, **chưa khớp `tools/seeddata.py`** → KHÔNG chạy lại `seeddata.py` cho tới khi người sửa phần nghỉ phép cập nhật generator (chạy lại sẽ xóa dữ liệu của họ).

## Chỉ số cơ thể: thêm Tỷ lệ cơ + hint box 2 màn + sửa lỗi ngày Bán gói PT (25/10/2026, ĐÃ CODE)
- **DB đổi**: `BodyMeasurements.MusclePercent DECIMAL(3,1) NULL` + `CK_BodyMeasurements_Muscle` (10–70). Seed: các lần đo InBody có % cơ, lần "Tự cân ở nhà" để trống. DB đang chạy chỉ cần thêm cột (không phải nạp lại seed):
  `IF COL_LENGTH('BodyMeasurements', 'MusclePercent') IS NULL ALTER TABLE BodyMeasurements ADD MusclePercent DECIMAL(3,1) NULL CONSTRAINT CK_BodyMeasurements_Muscle CHECK (MusclePercent IS NULL OR MusclePercent BETWEEN 10 AND 70);`
- **Code**: form thêm ô "Tỷ lệ cơ" (không bắt buộc) → `BodyStatsService.save` → `checkInput` (10–70%) → `BodyStatsDAO.save` (MERGE thêm cột). Thêm ô số thứ 5 "Tỷ lệ cơ" (bấm = biểu đồ `?metric=muscle`) và cột "Cơ" trong lịch sử. `checkInput` bỏ 2 tham số chữ không dùng.
- **Bảng lịch sử**: cột số căn phải cả tiêu đề lẫn giá trị (quy ước `th.num` của common.css; trang cần selector mạnh hơn `thead th` chung), Ngày + Ghi chú căn trái.
- **Hint box** (`.pt-info` dùng chung) cạnh tiêu đề màn Chỉ số cơ thể và Bán gói PT.
- **Lỗi Bán gói PT**: gõ ngày bắt đầu ngoài hôm nay → +30 ngày thì trình duyệt chặn gửi (`min`/`max`) nhưng JS đã làm mờ + khóa form (`is-busy`) → kẹt. Nay: ngày sai → câu lỗi đỏ dưới ô, không gửi, không khóa; ngày đúng → chờ 0,7 giây (gõ xong) mới gửi.

## Lễ tân bán gói PT — /receptionist/pt-sales (25/10/2026, bản 2 — ĐÃ CHỐT + ĐÃ CODE)
UC-21 bản **đơn giản hóa** (SRS cập nhật sau). Bản 2 thay bản 1 (bỏ "giờ cố định 1 giờ cho cả kỳ", bỏ "thu tiền ngay không giữ chỗ").

- **Luồng 4 bước** (thanh bước trên cùng, cột phải "Đơn bán" dính khi cuộn):
  1. **Khách** — ô tìm kiểu Google: gõ ≥ 2 ký tự → `pt-sales.js` gọi `GET /receptionist/pt-sales/customers?q=` → `PtSaleService.search` → `PtSaleDAO.searchCustomers` (tối đa 8). Khớp **tên không dấu theo thứ tự từ** ("do mai" → "Đỗ Thị Mai"), **SĐT / SĐT giám hộ**, **mã KH**. Dùng collation `Latin1_General_CI_AI` (bản `Vietnamese_CI_AI` coi ă, ê, đ là chữ riêng → "van" không khớp "Văn"). Gợi ý ghi luôn "Gói tập tới …" hoặc lý do không bán được. Chọn → `?customer=<CustomerId>`. Tắt JS: Enter → `?q=` → danh sách link.
  2. **Gói & PT** — ô gói, ô PT, ngày bắt đầu (mặc định ngày mai, tối đa +30 ngày). Gói đôi: ô tìm người tập cùng (cùng API, bỏ chính người đứng tên). Đổi lựa chọn → JS tự gửi form GET; đổi gói/PT/ngày thì bỏ giờ đã chọn.
  3. **Lịch tập** (bản 3, 26/10 — thay bảng "8/9 tuần"): làm theo kiểu sự kiện lặp lại của Google Calendar / đặt PT định kỳ của Mindbody, Glofox.
     - **Lịch mẫu hằng tuần**: mỗi thứ T2–T6 chọn **Nghỉ** hoặc 1 giờ (1–5 buổi/tuần). Mẫu nhanh: T2·T4·T6, T3·T5, T2→T6, Bỏ hết. Dưới mỗi giờ: "Còn 4/9 buổi" + lý do gặp nhiều nhất viết ngắn ("Khách trùng giờ", "PT có khách"…); đủ mọi tuần thì không ghi; hết buổi → "Không còn buổi" và khóa. Rê chuột: danh sách ngày không xếp được kèm lý do đầy đủ.
     - **Từng tuần**: tab Tuần 1…N (chấm đỏ = có ngày chưa xếp được, chấm xanh = có ngày sửa riêng), ‹ › đổi tuần. 5 ô ngày: xanh lá có buổi · vàng PT cần dời sau · đỏ chưa xếp được (kèm lý do) · xám Nghỉ.
     - **Sửa riêng 1 buổi**: bấm ô ngày → chọn giờ khác (gồm cả khung PT mở riêng ngày đó) hoặc Không tập, chỉ ngày đó; "Về lịch mẫu" để hoàn tác.
     - Kỹ thuật: server tính sẵn mọi ngày × giờ (`PtSaleService.buildPlan` → `PtSalePlan` JSON trong trang), `pt-sale-plan.js` ghép lịch ngay trong trình duyệt (không tải lại trang). Nút Giữ lịch gửi `sessions=2026-10-27@09:30,…`; `checkRequested` **kiểm tra lại từng buổi có khóa** bằng cùng luật `checkDay`, sai ngày nào báo đúng ngày đó. Lịch mẫu lưu trên URL (t1..t5, trống = Nghỉ); buổi sửa riêng mất khi đổi khách/gói/PT/ngày bắt đầu.
  4. **Giữ chỗ & thu tiền** — xem dưới.
- **Giữ chỗ 15 phút** (giống mua vé): nút "Giữ lịch 15 phút" (POST `action=hold`) → trong 1 transaction có khóa PT, làm lại đủ 3 bước rồi tạo đơn **`Pending`** tại quầy (`PaymentDueAt` NULL, ca tự mở như bản 1) và đặt mọi khung của kỳ sang **`Held`** (`HeldByOrderId`, `HoldExpiresAt` = +15 phút); ngày PT chưa có khung → tạo khung `Held`. Từ lúc này lễ tân khác / PT không lấy được các khung đó (bảng tuần của họ ghi "Đang được giữ cho đơn khác"). Trang chuyển sang `?order=<OrderId>`: đồng hồ đếm ngược (common.js `data-countdown-seconds`, hết giờ tự tải lại).
  - **Thu tiền** (`action=pay`): Cash / Transfer (ô mã giao dịch chỉ hiện khi Chuyển khoản, CSS `:has`) + tích "Đã nhận đủ". Kiểm tra đơn còn giữ, chưa quá hạn → đơn `Paid` + giao dịch `Success` + Subscription + khung `Held → Booked` + Sessions. `FixedStartTime` = giờ xuất hiện nhiều nhất. Báo khách + người tập cùng + PT như bản 1.
  - **Hủy giữ chỗ** (`action=release`, có hộp xác nhận) hoặc **quá 15 phút**: đơn `Cancelled` (lý do ghi sẵn), khung do lần giữ này tạo ra bị xóa, khung có sẵn trả về `Free`. Quá hạn được dọn **lười**: mỗi lần mở màn / bấm giữ, `releaseExpired` nhả mọi đơn giữ đã quá hạn (chưa có job nền).
- **Giữ nguyên từ bản 1**: điều kiện khách mua được (gói thành viên phủ hôm nay, không bảo lưu, đủ P-05 tuổi, không phải chính lễ tân); luật ngày không có buổi; ngày phòng nghỉ / PT có đơn nghỉ vẫn sinh buổi + tô vàng; công thức tiền (VAT, SessionValue, hoa hồng, MaxSwapsSnapshot); không voucher; bỏ nhánh bán buổi lẻ.
- Đã chạy thật (lễ tân `tam.le@gms.vn`, Chrome 1440 + 390): tìm "do mai", "0901234501", "KH-100"; gói 1 tháng giữ 24 buổi → đồng hồ 14:59; gói đôi KH-1001 + KH-1006 giữ 23 buổi → chuyển khoản → Paid; hủy giữ → Cancelled, 0 khung còn giữ; ép hết hạn → "Đơn này không còn giữ chỗ…". **DB local đã đổi** bởi các lần thử này.

## Bước 11 — PT xử lý buổi trên Lịch dạy (25/10/2026, ĐÃ CHỐT + ĐÃ CODE)
Đổi so với SRS (SRS cập nhật sau): khách KHÔNG còn tự đổi buổi → gộp "Dời buổi (PT bận)" và "Đổi hộ khách không có tài khoản" thành **một** phương án "Dời buổi". Không tính lượt đổi (`SwapsUsed` không đổi), không xác minh SĐT người yêu cầu. Còn 3 phương án: Dời buổi · Chuyển PT · Hủy buổi.

- **Vào hộp**: thẻ buổi `canHandle` (Scheduled + chưa bắt đầu) trên `/pt/schedule` là link `?date=…&view=…&handle=<SessionId>`. PT Dashboard: nút "Xử lý buổi" / "Dời buổi" thành link tới đúng URL này; hộp 4 nút khóa + nhóm JS cũ của Dashboard đã bỏ.
- **Code**: `PtScheduleServlet` (GET đọc `handle` → `loadHandle`; POST có `action` → `handleSession`, POST thường + PRG, bị từ chối thì dựng lại trang, hộp mở đúng phương án, giữ chữ đã nhập) → `PtSessionHandleService` (`getHandleForm`, `reschedule`, `transfer`, `cancel`) → `PtSessionHandleDAO`. JSP `pt/_handle-dialog.jspf`; đổi phương án bằng radio + CSS `:has`, JS chỉ `showModal` + bỏ `?handle` khỏi URL khi đóng.
- **Khung đích hợp lệ** (một câu SQL `SLOT_SQL` dùng cho cả lúc mở hộp và lúc Lưu): Free, không bị đơn giữ, chưa có buổi Scheduled, PT đang làm việc **và tài khoản Active**; Service xét thêm: chưa tới giờ, trong hạn gói, không phải ngày phòng nghỉ, PT không có đơn nghỉ trùng ca, khách/người tập cùng không có buổi khác chồng giờ.
- **Khách thiếu Membership/DayPass ngày đích KHÔNG chặn** (chặn thì buổi đang "Cần chú ý" không dời được): khung đó tô vàng; sau khi dời buổi vẫn hiện "Cần chú ý", JOB-08 tự nhắc khách.
- **Dời buổi**: chỉ sang khung Free của chính PT, tìm từ hôm nay tới 14 ngày sau (không quá hạn gói). Giữ PT, giữ hoa hồng. Log `SessionMoveLogs.MoveType = PtReschedule`.
- **Chuyển PT**: giữ nguyên ngày + giờ, chỉ PT khác có khung Free đúng giờ đó. `Sessions.PtEmployeeId` + `TimeSlotId` đổi sang PT nhận; `CommissionAmount = round(SessionValue × CommissionRate PT nhận / 100)`. `Subscriptions.PtEmployeeId` (PT phụ trách gói) KHÔNG đổi. Log `PtTransfer`. Báo khách + PT nhận.
- **Hủy buổi**: lý do 5–500 ký tự + tích "khách đồng ý nhận lại tiền mặt" (CHECK `CK_Sessions_PtCancel`). `CancelType = ByPt`, vẫn tính buổi, PT vẫn có hoa hồng.
- **Khung cũ** sau cả 3 phương án → Free (hiện Còn trống trên lịch PT).
- **Chống tranh chấp**: Lưu chạy 1 transaction: `lockPtForPublish` (xếp hàng với Công bố) → khóa dòng buổi → khóa khung đích (UPDLOCK) → kiểm tra lại → `bookSlot` có điều kiện `Status = 'Free'`. Khung vừa bị lấy → MSG103; buổi đã bắt đầu / không còn Scheduled → MSG91.
- **Thông báo** (chuông, thông báo hệ thống): khách + người tập cùng có tài khoản Active; hồ sơ phụ thuộc không có tài khoản thì không nhận.
- Đã chạy thật trên DB seed (PT Sơn): dời buổi 275, thử khung vừa bị lấy (MSG103), chuyển buổi 277 cho PT 14 (hoa hồng 366.667 × 60% = 220.000), hủy buổi 276; `?handle=` của PT khác → "Không tìm thấy buổi này trong lịch của bạn." **DB local đã bị đổi bởi các lần thử này** (chạy lại seed nếu cần dữ liệu gốc). Phát hiện khi thử: PT 14 có tài khoản Locked nhưng vẫn Working → đã chặn nhận chuyển.
- Chưa làm: buổi đã hủy và khung Còn trống cùng giờ nằm cạnh nhau trên lưới tuần (giống mọi buổi Cancelled trước đây).

## Làm gọn 7 màn + JOB-08 tự nhắc khách (24/10/2026, ĐÃ CHỐT)
Mục tiêu: code dễ học, nội dung trên màn hình chỉ thêm, không mất. Quy ước comment: mỗi hàm mở đầu `HÀM CHÍNH` (ai gọi, lúc nào) hoặc `HÀM PHỤ` (gọi ở bước nào của hàm nào); trong hàm chỉ ghi B1, B2…; đầu file liệt kê nhóm + hàm chính, các nhóm ngăn bằng `// ====`.

**JOB-08 — tự nhắc khách thiếu quyền vào gym (thay nút "Nhắc khách" của PT)**
- `RemindMissingGymAccessJob` chạy trong lượt 00:05 (`BackgroundJobScheduler.DAILY_JOBS`), xét các buổi PT `Scheduled` của NGÀY MAI (giờ Việt Nam).
- Luật "vào được gym" giống JOB-04 (`AutoCloseSessionService.canUseGym`): có Membership hoặc DayPass phủ ngày tập VÀ không bảo lưu ngày đó. Không vào được → 1 thông báo hệ thống ở chuông (`Notifications.SentByEmployeeId` = NULL, Audience = Individual).
- Gói PT đôi xét cả người đứng tên lẫn người tập cùng (`Orders.PartnerCustomerId`). Chỉ gửi cho tài khoản Active.
- Bỏ qua ngày phòng nghỉ cả ngày (CBR-56: PT phải dời trước ngày nghỉ; SRS ghi buổi còn sót do JOB-04 hủy GymClosed nhưng phần này JOB-04 **chưa code** — xem mục "Cần sửa tiếp code runtime" bên dưới). Đã chạy thử trên DB: ngày nghỉ 20/10/2026, câu SQL cũ sẽ nhắc nhầm 4 khách mua gói; câu mới trả 0 dòng.
- Câu nhắc: thiếu gói → "mua gói thành viên hoặc vé ngày"; đang bảo lưu → "liên hệ lễ tân kiểm tra thời gian bảo lưu". KHÔNG khuyên mua vé ngày khi đang bảo lưu, vì theo JOB-04 có vé ngày mà đang bảo lưu vẫn không vào được.
- Chống gửi trùng: tiêu đề "Nhắc buổi PT ngày dd/MM/yyyy"; tài khoản đã có thông báo hệ thống cùng tiêu đề thì bỏ qua (job chạy lại không gửi lần 2). Khách có 2 buổi cùng ngày → 1 lời nhắc, nêu buổi sớm nhất. Bước kiểm tra + 2 câu INSERT nằm trong 1 transaction.
- PT Dashboard bỏ nút "Nhắc khách", bỏ `doPost` + CSRF; thẻ "Cần membership" ghi "Hệ thống tự nhắc khách trước buổi 1 ngày".

**PT Dashboard**
- Giữ khối chào + thời tiết; bố cục khối đầu trang tách ra `css/dashboard-hero.css`, dùng chung với Trang cá nhân của khách.
- Mỗi khách phụ trách (và người tập cùng của gói đôi) có số gọi (`tel:`) + nút **Zalo**. `ZaloUtil.toUrl` giữ lại chữ số, đổi `84xxxxxxxxx` → `0xxxxxxxxx`, trả `https://zalo.me/<số>`; không có số thì không hiện nút. `MyPtSessionService` dùng chung hàm này.

**Gói của tôi**
- Chỉ còn 1 link "Xem trên lịch tập" (thẻ Buổi tới); topbar màn này bỏ nút "Lịch tập".
- Cột trái hết trống: dưới danh sách gói có "PT buổi tới" (tên, gọi, Nhắn Zalo) và "Vào phòng tập" (hôm nay vào được không + link mua gói). Bỏ mục "Đi tới" cũ.
- Bỏ dòng thứ/ngày/buổi lặp trên-dưới. Màu + hình trạng thái buổi dùng chung `css/pages/customer/session-status.css`.

**Lịch tập (MyPTSession)**: viết lại JSP/CSS/JS, Java giữ nguyên. Mobile chọn ngày bằng link server dựng sẵn (không cần JS). Topbar chỉ còn "Đặt buổi lẻ"; màn ≤ 480px chỉ hiện icon (aria-label giữ tên nút) để tiêu đề không bị ép.

**Quên mật khẩu / Đăng nhập / Đăng ký**
- Bỏ sub-title dưới tiêu đề form; thông tin cần thiết chuyển thành gợi ý dưới ô (VD "Mã 6 số sẽ được gửi tới email này…"). Quên mật khẩu viết lại Servlet/Service/JSP/JS/CSS; luồng 3 bước giữ nguyên (bước suy ra từ session, không từ URL).
- Sửa lỗi có sẵn trong `common.js` (`bindFieldValidation`): ô có lỗi do server in sẵn trước đây chỉ mất lỗi khi rời ô → dòng lỗi biến mất đúng lúc nhấn chuột, nút gửi bị đẩy lên ~27px, cú bấm trượt, phải bấm lần 2. Nay ô đó được đánh dấu `invalid` ngay khi mở trang nên lỗi mất trong lúc gõ.

**Trang chủ**: dọn comment + mục lục CSS, giao diện giữ nguyên (đã so pixel). Ảnh `strip-endurance.jpg` được phủ duotone xanh–cam như các ảnh khác (`strip-endurance.jpg.json` ghi cách xử lý).

## PT Dashboard nhắc dời buổi ngày phòng nghỉ (22/10/2026)
- DAO lấy từng buổi Scheduled của PT rơi vào OperatingHourExceptions.IsClosedAllDay = 1 trong 14 ngày tới; Service tạo thẻ closedDay trước các cảnh báo membership/bảo lưu.
- Thẻ ngày nghỉ có nút Dời buổi mở đúng SessionId trong hộp UC-35, không gửi nhắc khách. PT phải dời trước ngày nghỉ; buổi còn Scheduled sau ngày nghỉ do JOB-04 hủy GymClosed, không trừ buổi, không tính hoa hồng, không dạy bù hoặc gia hạn.
- Seed của Mai ngày 22/09/2026 (Session 83) đổi từ khách tự hủy sang Missed/NoCheckIn, tự chốt lúc 00:05 ngày 23/09; giữ nguyên SessionValue/CommissionAmount. CBR-56 cập nhật theo quyết định 10/10, không sửa schema.

## Gói của tôi v2 + PT Dashboard nhắc khách (22/10/2026, ĐÃ CHỐT theo góp ý mentor)
Mentor chê: danh sách buổi quá dài, điều hướng cụt, thiếu CTA. Đã sửa:
- **Bỏ bảng #1 → N, chip lọc, dòng gập** (thay cho mục 21/10 bên dưới). Xem từng buổi → màn Lịch tập (nút "Xem trên lịch tập" mở đúng tháng có buổi tới).
- **Lịch cố định dạng tuần mẫu T2–T6** (BR-35, CBR-43, BR-41): mỗi ô = giờ có nhiều buổi nhất của thứ đó (≥ 50%, hòa → giờ sớm). Thứ cố định nhưng khác giờ chính (VD T4 07:00) → viền đứt. Buổi lệch (đổi buổi, PT dời, chuyển PT, xếp giờ khác từ đầu) → "Buổi khác giờ" kèm lý do đọc từ `SessionMoveLogs`. Hàm `MyBookingService.applyWeekTemplate` có unit test.
- **Dải chấm "Các buổi của gói"**: 1 ô = 1 buổi, vạch = sang tuần; mỗi trạng thái có hình riêng (đặc / × / gạch chéo / viền) để không chỉ dựa vào màu. **"Cần để ý"** chỉ liệt kê buổi cảnh báo sắp tới + vắng + hủy, kèm lý do.
- **CTA tối đa 1, chọn theo ưu tiên** (`chooseCta`): thiếu quyền vào gym → mua gói thành viên online; gói PT còn ≤ 3 buổi hoặc ≤ 14 ngày → **gia hạn TẠI QUẦY** (CBR-48: gói PT không bán online — nút Gọi lễ tân + Báo PT trước); gói đã kết thúc → đăng ký lại tại quầy; ≥ 3 buổi lẻ/30 ngày → gợi ý gói PT (tại quầy). Hotline lấy `AppConstants.GYM_HOTLINE` (giống chân trang), giờ mở cửa đọc P-19.
- **Điều hướng**: cột trái có mục "Đi tới" (Gói thành viên & vé ngày, Lịch tập, Đặt buổi lẻ); buổi lẻ có "Đặt lại với PT này" → `/customer/book-session?pt=<EmployeeId>` (**màn Đặt buổi lẻ cần đọc tham số `pt` để chọn sẵn PT**). **(24/10: bỏ mục "Đi tới", thay bằng "PT buổi tới" + "Vào phòng tập"; "Đặt lại với PT này" vẫn giữ.)**
- Chữ giải thích phụ chuyển vào nút (i) (hint box chung), màn chỉ giữ dữ liệu.
- **PT Dashboard**: bỏ khối "Đánh giá PT"; khối "Cần chú ý" có nút **Nhắc khách** → POST `/pt/dashboard` (CSRF) → 1 thông báo cá nhân (`Notifications` Audience = Individual, `SentByEmployeeId` = PT). Service tính lại danh sách Cần chú ý của chính PT đó (không tin form), mỗi khách 1 lần/ngày, khách chưa có tài khoản → báo PT nhắn Zalo (GBR-15). **(24/10: đã bỏ nút Nhắc khách, thay bằng JOB-08 tự nhắc — xem mục 24/10.)**
- Khách xem lời nhắc ở **chuông thông báo** đã thêm vào header Gói của tôi + Lịch tập; lời nhắc dẫn khách về Gói của tôi, nơi CTA "Mua gói thành viên" đang hiện → 3 màn nối thành 1 luồng. **(24/10: lời nhắc nay do JOB-08 gửi.)**

## Gói của tôi — làm lại UX cột trái và bảng buổi (21/10/2026, đã thay bằng v2 ở trên)
- Bỏ 2 dropdown Tình trạng + Loại gói: "Buổi lẻ" nằm ở cả 2 dropdown nên tạo tổ hợp vô nghĩa (VD Đã kết thúc + Buổi lẻ → rỗng), người dùng tưởng lọc bị lỗi.
- Thay bằng 3 tab: Đang tập / Đã kết thúc / Buổi lẻ (có số đếm). Dùng `bindTabs()` chung của `common.js` (`data-tab` ↔ `data-tab-panel`); JSP mở sẵn tab chứa gói đang xem. Loại gói (cá nhân/đôi) hiện thành nhãn trên thẻ, không lọc.
- Thẻ gói không hiện mã gói (#82) vì dễ nhầm với số thứ tự buổi #1, #2; tiến độ ghi "17/45 buổi đã qua · còn 28".
- Ô đếm → chip lọc có chấm màu trạng thái; đổi "Còn lại" thành "Sắp tới" (vẫn gồm Hôm nay + Sắp tới + Cần chú ý); chip = 0 thì khóa.
- Bảng: buổi lẻ bỏ cột #, thêm cột PT; lý do chỉ hiện với Cần chú ý / Vắng / Đã hủy, câu chung của các trạng thái khác nằm trong "Chi tiết"; dòng buổi sắp tới có nhãn "Buổi tới"; bảng co theo số dòng, không còn khoảng trống lớn.
- Đầu cột phải có thẻ **"Buổi tới"** (ngày giờ, còn mấy ngày, PT + Nhắn Zalo / Sao chép số / Google Calendar / Xem trên lịch tập; buổi Cần chú ý thì nói lý do ngay trên thẻ) + vòng tiến độ 17/45. Service thêm `focusSession`, `daysUntilFocus`, `endedCount` vào `MyBookingView` (có unit test).
- Buổi đã qua (gồm cả "Đang cập nhật") được gập thành 1 dòng đầu bảng "▸ 19 buổi đã qua · 16 đã check-in · 1 vắng…", bấm để mở; bỏ cách tự cuộn + vạch "Hôm nay" làm trước đó. Bấm chip lọc khác "Tất cả" thì hiện đủ các buổi khớp, không gập.
- Thêm `MyPtSessionRow.getDayName()` để hiện "Thứ 6, 23/10/2026".

## Lịch tập — gọn giao diện và sửa đóng chi tiết (19/10/2026, cập nhật theo phản hồi)

- Lịch tháng chỉ lấy/hiện buổi trong đúng tháng đã chọn. Ô đệm của tháng trước/sau để trống, không có số ngày hoặc link. Mỗi ngày hiện dạng d/M (6/10, 6/11), ngày có/không có buổi đều căn phía trên. Quyết định này thay cách hiện ngày ngoài tháng ở các mục cũ bên dưới.
- Ngày hôm nay và ngày người dùng chọn có hai dấu độc lập: hôm nay nền cam + nhãn, ngày chọn viền xanh + nhãn “Đang chọn”. Cùng ngày giữ cả hai; khác tuần/tháng chỉ đánh dấu các ngày có trong lưới. Áp dụng cả tuần, tháng và chọn ngày trên mobile.
- Servlet giữ `viewDate` là ngày hợp lệ thực sự đang xem; `selectedDate` là ngày được chọn qua URL. Ô chọn ngày và link Tuần/Tháng giữ đúng ngày đó, không đổi thành Thứ 2/ngày đầu tháng.
- `MyPtSessionRow.ended` do Service so sánh giờ kết thúc với `now` của request; nền buổi cũ trầm hơn nhưng không disabled, không giảm opacity của cả thẻ và không đổi nhãn kết quả đã lưu. Thời điểm đúng bằng giờ kết thúc được tính đã kết thúc.
- Màn Gói của tôi trên desktop từ 1080px và cao từ 480px dùng chiều cao cửa sổ: header và ô đếm cố định, danh sách gói/bảng buổi cuộn riêng. Không ẩn scrollbar bằng CSS để che phần tràn; mobile hoặc cửa sổ rất thấp vẫn cuộn trang bình thường. Đã kiểm tra trang cao đúng 720px ở viewport 720px, bàn phím End tới được hàng cuối trong bảng dài.
- Thay 5 khung trước đó bằng **Sáng 06:00–12:00 / Chiều 12:00–18:00 / Tối 18:00–22:00**, xếp theo giờ bắt đầu. Service và test cùng dùng 3 khung.
- Chỉ giữ tiêu đề trang ở topbar; bỏ “Lịch của bạn”, “Theo dõi hành trình tập” và các đoạn hướng dẫn lặp. Hướng dẫn sử dụng/cách đếm/trạng thái nằm trong HintBox `.pt-info` dùng chung.
- Thanh công cụ chỉ gồm điều hướng tuần/tháng, Hôm nay, Chọn ngày và Tuần/Tháng. Ngày trong tiêu đề cột rút gọn dd/MM; ô buổi không lặp “Xem chi tiết”.
- Nút Đóng là `type=button`, gọi `dialog.close()` trực tiếp. Không dùng form dialog vì bộ xử lý submit chung sẽ bật loading “Đang xử lý…” dù không có request server.
- Bấm ngoài khung, nút Đóng hoặc Escape đều đóng và trả focus về buổi vừa chọn. Khóa cuộn nền khi mở; chỉ thân chi tiết có thể cuộn, phần đầu và nút Đóng luôn nhìn thấy.
- Chi tiết gom PT/SĐT/liên hệ vào một nhóm; cảnh báo hiện ngay dưới ngày giờ, thông tin gói dùng nhãn–giá trị; Google Calendar là liên kết phụ.
- Kiểm tra: 32/32 test của hai Service qua; Jasper biên dịch hai JSP không lỗi. Đã kiểm thử thực tế trên fixture Tomcat: nút Đóng không loading sau mở lại, bấm ngoài/Escape đóng, focus và cuộn nền được trả lại, HintBox mở được, desktop và mobile 390px dùng 3 khung.

## Giao diện Lịch tập và Gói của tôi — triển khai tiếp (19/10/2026)

- Mẫu đối chiếu: artifact Claude `8B3PiEFhEu9LGgbx8jJLVq`, gồm lịch tuần, thanh chi tiết, lịch tháng, gói và mobile theo ngày. Giữ theme/token/layout hiện tại; dữ liệu minh họa của mẫu không thay cho DB.
- Phạm vi: Customer đang đăng nhập, chỉ đọc các buổi của người đứng tên hoặc người đi kèm gói đôi. Không mở thêm quyền cho vai trò khác; không có phân tầng tenant/plan trong chức năng này.
- Lịch tuần: 5 khung × 7 ngày; mobile chọn một ngày và vẫn đọc đủ 5 khung. Lịch tháng hiện giờ + nhãn ngắn; bấm ngày/buổi để mở tuần chứa ngày đó.
- Hai màn dùng chung nội dung chi tiết, link Zalo/Google Calendar và sao chép số. Clipboard bị chặn thì hiện ô số đã chọn để khách tự sao chép. Không có JS vẫn đọc được qua details.
- Hai dropdown chỉ lọc danh sách gói bên trái; không âm thầm thay gói đang xem bên phải. Ô trạng thái lọc bảng buổi; Còn lại gồm Hôm nay, Sắp tới, Cần chú ý. Đang cập nhật được tính riêng và chỉ hiện ô đếm khi có buổi.
- Số buổi đã qua trên thẻ gói là số buổi có ngày tập trước hôm nay, không phải số buổi hoàn thành. CheckedIn chỉ ghi nhận vào gym.
- Tiêu chí nghiệm thu (Given / When / Then):
  - Có lịch trong tuần → đổi tuần/tháng hoặc chọn ngày → URL giữ đúng cách xem và dữ liệu đúng khoảng; ngày sai/cực trị trở về hôm nay.
  - Có buổi cảnh báo → mở chi tiết → đọc được lý do, PT và gói; Escape đóng và trả focus về buổi đã chọn.
  - Có gói cá nhân/đôi/buổi lẻ → kết hợp hai bộ lọc → chỉ thẻ khớp cả hai xuất hiện; không khớp hiện thông báo rỗng.
  - Có nhiều trạng thái → chọn Còn lại → chỉ hiện today/upcoming/warn, không gồm buổi pending hoặc đã hủy.
  - Tải thành công nhưng không có dữ liệu → hiện trạng thái rỗng; lỗi Service → hiện lỗi và nút tải lại, không báo nhầm là không có buổi.
  - Tên/lý do chứa ký tự HTML → hiển thị dạng chữ, không thực thi mã; buổi thiếu số PT → hướng dẫn liên hệ lễ tân.
- Kiểm tra: build WAR và 151 test có sẵn đã qua; thêm test ngày URL sai/cực trị, bộ test hai Service qua 31/31. Jasper biên dịch hai JSP không lỗi. Đã chạy JSP trên Tomcat riêng với fixture minh họa trong target; chưa xác nhận truy vấn bằng SQL Server thật.

## My PT Sessions — đổi sang thời khóa biểu, khách chỉ xem (19/10/2026, ĐÃ CHỐT theo góp ý GVHD)

- **Bỏ khách tự hủy buổi (UC-39) và khách tự đổi buổi (UC-38, màn #53 Swap Session).** Lý do: vận hành thực tế khách nhắn PT qua Zalo; PT dời / đổi buổi giúp khách qua UC-35 (`PtReschedule`). Hủy phía khách ít giá trị. Hai mục 17/10 bên dưới về hủy / đổi buổi phía khách **hết hiệu lực**.
- Màn `/customer/pt-sessions` thành **Lịch tập** dạng thời khóa biểu như FAP: hàng = 5 khung giờ cố định (Sáng sớm 06–09, Sáng 09–12, Trưa 12–15, Chiều 15–18, Tối 18–22, xếp theo giờ bắt đầu), cột = Thứ 2 → Chủ nhật; có chế độ Tuần / Tháng. Bấm một buổi → thanh chi tiết dọc bên phải (PT, gói, trạng thái + lý do, Nhắn Zalo, Sao chép số).
- **Màn mới #50 `/customer/bookings` "Gói của tôi"**: cột trái danh sách gói PT + buổi lẻ (nhóm Đang tập / Đã kết thúc / Buổi lẻ, 2 dropdown Tình trạng + Loại gói); cột phải chi tiết gói + 5 ô đếm (Tất cả, Đã check-in, Vắng, Đã hủy, Còn lại — "Còn lại" gồm cả buổi cần chú ý) kiêm nút lọc + bảng buổi đánh số #1→N theo thứ tự thời gian.
- Bỏ bộ lọc 5 ô (gói, loại, buổi trong ngày, từ/đến ngày), tab trạng thái lịch sử và phân trang 8 ngày (phân trang bỏ ở giai đoạn thay bằng thời khóa biểu).
- Liên hệ PT: link `https://zalo.me/<SĐT>` + sao chép số (laptop bấm `tel:` không ra gì khi demo).
- Dữ liệu cũ vẫn có buổi `Cancelled / ByCustomer` → giữ câu giải thích khi hiển thị lịch sử; schema không đổi.
- Code giai đoạn 1 (19/10): đã xóa `PtSessionSwapService`, `PtSessionSwapDAO`, `PtSwapSlotView`, `PtSessionInfo`, `MyPtSessionFilter`, test đổi buổi, phần POST / hộp thoại / bộ lọc trên màn và các mã MSG108–113 chỉ dùng cho bộ lọc / hủy / đổi buổi.
- Code giai đoạn 2 (19/10, backend): `MyPtSessionDAO` dùng 1 câu SQL gốc (CTE + `ROW_NUMBER` đánh số buổi theo ngày giờ trong từng gói, JOIN sẵn tên/SĐT PT) cho 3 hàm `findSessionsInRange` / `findSessionsOfPackage` / `findSingleSessions`; `findMyPackages` 1 câu cho cột trái; `findClosedDays` cho cảnh báo phòng nghỉ. `MyPtSessionService.getWeek/getMonth` + `applyStatus` (7 trạng thái), `MyBookingService` + `MyBookingServlet` `/customer/bookings?id=`. Ô đếm: Tổng = Đã check-in + Vắng + Đã hủy + Còn lại + Đang cập nhật. Đã bỏ thẻ "Lượt đổi còn lại" (khách không tự đổi buổi). JSP hai màn đang là bảng thô, giao diện thật ở giai đoạn 3.
- **Cần sửa SRS:** bỏ UC-38, UC-39 phía khách (giữ đổi hộ qua PT), bỏ màn #53, cập nhật mô tả màn #50, #51, BF-08 A3/A4, các BR-73…79 / CBR liên quan tới thao tác của khách.

## Khách hủy buổi — lý do không bắt buộc (17/10/2026, HẾT HIỆU LỰC từ 19/10 — xem mục trên)

- Khách tự hủy buổi **không bắt buộc nhập lý do** (theo SRS 9.2.1: hủy không có ô nhập). Có ô "Lý do hủy (không bắt buộc)", tối đa 500 ký tự; bỏ trống thì `Sessions.CancelReason` = NULL.
- GBR-09 (bắt buộc lý do 5–500) chỉ áp cho nhân viên hủy / từ chối / void; cần sửa câu chữ GBR-09 cho rõ phạm vi.
- Vẫn giữ: ghi AuditLog, thông báo PT + người tập cùng (CBR-46), 1 transaction, vẫn trừ buổi không hoàn tiền (BR-79).

## Customer Swap UC-38 — mở rộng ngày được chọn (17/10/2026, HẾT HIỆU LỰC từ 19/10 — khách không tự đổi buổi)

- Khách đổi buổi được chọn **bất kỳ slot thứ 7 / Chủ nhật** của cùng PT, từ hôm nay tới **hết hạn gói** (tuần sau, tháng sau đều được). Bỏ giới hạn "chỉ tuần này, từ thứ 7 mở tuần sau" của CBR-39 cho riêng thao tác đổi buổi.
- CBR-39 vẫn giữ nguyên cho đặt buổi lẻ (UC-37).
- Các điều kiện khác giữ nguyên: cùng PT, slot Free, cùng thời lượng, chưa bắt đầu, trong hạn gói, gym mở cửa, PT/khách không trùng buổi, ít nhất 1 người vào được gym, còn lượt đổi.
- Code: `PtSessionSwapService.findValidSlots` lấy slot tới `PackageEndDate`; `isAllowedSlot` bỏ điều kiện cửa sổ tuần. Test `anyWeekendInsidePackageTermIsAllowed`.
- Cần sửa SRS: CBR-39 (tách riêng UC-37 / UC-38), mô tả UC-38 và mục 9.2.4 "Limited to the current week...".

## BF-05 — chỉ nhận hai loại nghỉ cho đơn mới (23/10/2026, ĐÃ CHỐT)

Theo yêu cầu user, form gửi đơn và bộ lọc quản lý chỉ còn Phép năm / Không lương. StaffLeaveService từ chối Sick ngay khi validate đầu vào, kể cả gửi trực tiếp POST. Giữ giá trị Sick trong entity và schema để đọc đúng lịch sử, không xóa hay đổi loại các đơn cũ. Tách đơn là cách xử lý phần vượt phép, không phải loại nghỉ thứ ba; chưa triển khai trong bước này. User yêu cầu chưa commit đến khi làm xong các phần còn lại.

## BF-05 — thu gọn thanh lọc danh sách (22/10/2026, ĐÃ CHỐT)

User xem ảnh đề xuất rồi yêu cầu áp dụng. Thanh trên gồm hàng tab trạng thái và hàng tìm nhân viên/khoảng ngày gửi/Bộ lọc/Tìm; chức vụ và loại nghỉ nằm trong details mở bằng Bộ lọc, số lựa chọn hiện trên nút. Ô ngày vẫn để trống khi chưa chọn, tab và phân trang tiếp tục giữ các điều kiện đã chọn. Xóa lọc trở về trạng thái không giới hạn ngày. Giữ form GET và bộ lọc Service/DAO hiện có; không thêm nghiệp vụ mới.

Kiểm chứng: Jasper biên dịch JSP không lỗi; package với test bỏ qua thành công vì thay đổi chỉ ở JSP/CSS. Trên GMS cổng 8080, ở 1280 px thanh lọc gọn hai hàng và bảng được đẩy lên. Mở Bộ lọc, chọn PT + Phép năm trả 3 đơn và hiện Bộ lọc 2; chọn ngày gửi 22/10–22/10 vẫn trả 3 đơn; đổi sang Tất cả giữ ngày/chức vụ/loại; Xóa lọc đưa các điều kiện và ngày về trống, tab Chờ duyệt hiện 7 đơn. Ở 640 px và 390 px không tràn ngang. Đã đăng xuất tài khoản test; không duyệt/từ chối đơn.

## BF-05 — chỉ giữ Duyệt đã chọn (22/10/2026, ĐÃ CHỐT)

User yêu cầu bỏ nút Duyệt tất cả trên trang vì trùng thao tác checkbox đầu bảng. Giữ checkbox chọn từng đơn/chọn tất cả đơn đủ điều kiện trên trang và một nút Duyệt đã chọn; bỏ JS riêng của nút thừa. Nút duyệt chỉ bật khi có lựa chọn, vẫn hỏi xác nhận số đơn và kiểm tra lại từng đơn ở backend.

Kiểm chứng: Jasper biên dịch JSP không lỗi, package thành công. Trình duyệt trên GMS cổng 8080 xác nhận nút thừa đã mất; tick đầu bảng chọn 6 đơn đủ điều kiện, bỏ qua đơn vướng lịch PT; hộp xác nhận ghi đúng 6 đơn; hủy rồi bỏ tick hết thì nút bị khóa. Không gửi xác nhận duyệt; đã đăng xuất tài khoản test.

## BF-05 — không tự ẩn đơn cũ bằng bộ lọc hôm nay (22/10/2026, ĐÃ CHỐT)

User xác nhận đơn cũ không được bị ẩn khi qua ngày. Bỏ mặc định ngày gửi hôm nay ở danh sách, thay thế quyết định mặc định hai ô ngày trước đó. Mở Chờ duyệt hiện tất cả đơn chưa xử lý qua các ngày; các tab khác cũng không tự giới hạn ngày gửi. Chỉ áp dụng khoảng ngày khi người dùng chủ động chọn; Xóa lọc trở về không giới hạn ngày. Chuyển tab và phân trang vẫn giữ bộ lọc đã chọn. Thứ tự ưu tiên sắp nghỉ/FIFO, trạng thái và điều kiện duyệt không đổi.

Kiểm chứng: package chạy 234 test không lỗi, gồm 8 trường hợp hồi quy mở tab không tự giới hạn ngày, chọn khoảng ngày và xóa ngày. Đã nạp lại riêng GMS trên Tomcat và đăng nhập tài khoản test: mặc định hai ô ngày trống, chọn 22–22 có 7 đơn, Xóa lọc trở về không giới hạn ngày. Đã đăng xuất; không ghi quyết định duyệt/từ chối.

User yêu cầu bỏ dòng tổng số đơn phù hợp và khoảng Ngày gửi ở đầu bảng danh sách; đã bỏ hai dòng, để thao tác duyệt nhiều gọn bên phải. Không tạo thanh đệm rỗng ở tab đã xử lý hoặc danh sách trống.

User yêu cầu bỏ Chuyển người duyệt ở chi tiết: bỏ nút, hộp chuyển giao và truy vấn danh sách người tiếp nhận không còn dùng. Thanh tác vụ chi tiết chỉ còn Duyệt/Từ chối. Lịch sử cũ vẫn hiển thị các mốc đã ghi trước đây.

Kiểm chứng cuối đợt: package chạy 226 test không lỗi; Jasper biên dịch hai JSP không lỗi. Đã nạp lại context GMS và kiểm tra trên cổng 8080: lọc ngày gửi 22–22 vẫn có 7 đơn, không còn hai dòng thống kê ở đầu bảng; chi tiết chỉ còn Duyệt/Từ chối và giữ bố cục gọn. Đã đăng xuất tài khoản test sau khi xem.

## BF-05 — chi tiết vừa màn hình và nạp lại mã Java (22/10/2026)

Thông tin đơn và lý do nằm cột trái, kết quả kiểm tra ở cột phải. Quân số từng ca, danh sách buổi PT trùng và cách kiểm tra dùng details/summary thu gọn; lý do chưa đạt vẫn hiện ngay. Lịch sử xử lý chuyển thành hàng thu gọn dưới hai cột. Không thêm JS hoặc đổi điều kiện duyệt. Bản xem thử ở 1280×720 đo chiều cao tài liệu bằng chiều cao viewport, cả đơn đủ điều kiện và đơn vướng lịch PT đều không phải cuộn ở trạng thái ban đầu. Đã kiểm tra mở/đóng quân số, buổi trùng, lịch sử bằng chuột/bàn phím và mở/hủy hộp xác nhận.

User báo lọc ngày gửi 22/10–22/10 trống: DB local có 7 Pending gửi ngày 22/10, code hiện tại trả đủ 7. Log Tomcat cho thấy ứng dụng /gym-management-system nạp lại lần cuối 15:31, trước bản sửa DAO sang SubmittedAt; request 22–22 vẫn trả trang rỗng còn mở rộng đến ngày nghỉ thì có đơn. Cần nạp lại riêng context GMS sau khi build để thay class Java cũ. Tomcat dùng docBase target/gms: tránh clean khi ứng dụng đang chạy vì xóa thư mục này sẽ làm Tomcat tự undeploy/redeploy giữa build. Package chỉ cập nhật file, không bảo đảm classloader đã đổi.

Đã package thành công và yêu cầu Tomcat nạp lại riêng context bằng cập nhật thời gian descriptor gym-management-system.xml (hash nội dung cấu hình không đổi). Log xác nhận triển khai thành công lúc 16:08. Đăng nhập tài khoản Manager test theo SEED_CASES trên cổng 8080: URL status=Pending&fromDate=2026-10-22&toDate=2026-10-22 hiển thị đủ 7 đơn; màn chi tiết mới cũng vừa 1280×720. Đã đăng xuất tài khoản test. Không duyệt hoặc từ chối đơn thật. JSP đã biên dịch và kiểm tra giao diện; không thêm/chạy lại test Java cho thay đổi bố cục.

## BF-05 — bỏ khung Người phụ trách ở chi tiết (22/10/2026, ĐÃ CHỐT)

Bỏ card Người phụ trách/Người đã xử lý riêng ở cột phải và CSS chỉ dùng cho card đó. Tiến trình xử lý cùng thông tin kết quả duyệt/từ chối vẫn có người thực hiện; thao tác chuyển người duyệt ở thanh đầu trang dùng luồng hiện có.

Khi rà màn này, bỏ liên kết Xem đơn trong khoảng ngày còn truyền ngày nghỉ vào bộ lọc đã đổi sang ngày gửi. Số đơn khác trùng ngày/ca vẫn được tính theo ngày nghỉ, không đổi kiểm tra điều kiện duyệt.

Kiểm chứng: package thành công, Jasper biên dịch hai JSP quản lý không lỗi. Trình duyệt mở chi tiết xác nhận cột phải chỉ còn Tiến trình xử lý, không còn card Người phụ trách hoặc liên kết lọc sai ngày. Không thêm/chạy lại test Java cho thay đổi hiển thị này và không thực hiện quyết định thật.

## BF-05 — bộ lọc ngày là ngày gửi đơn (22/10/2026, ĐÃ SỬA)

User sửa rõ hai ô ngày lọc theo SubmittedAt (thời gian gửi đơn), không theo FromDate/ToDate (thời gian nghỉ). Mặc định vẫn là hôm nay. DAO dùng cùng điều kiện cho bảng theo đơn, số đếm tab và nhóm/lịch sử nhân viên: SubmittedAt >= 00:00 Từ ngày và SubmittedAt < 00:00 ngày sau Đến ngày. Đơn gửi hôm nay nhưng nghỉ tháng sau vẫn hiện; đơn gửi hôm qua nhưng nghỉ hôm nay không thuộc bộ lọc hôm nay. Giao diện ghi Gửi từ ngày và Ngày gửi để phân biệt với cột Ngày nghỉ / Ca.

Quyết định này thay mô tả lọc giao khoảng ngày nghỉ trong các ghi chú trước. Thứ tự ưu tiên sắp nghỉ/FIFO vẫn dùng ngày nghỉ và giờ gửi như hiện tại.

Kiểm chứng: clean package chạy 226 test không lỗi, gồm 6 truy vấn DAO dùng chung điều kiện ngày gửi. SQL production chạy trên VALUES chỉ đọc xác nhận gửi hôm nay/nghỉ tương lai, gửi hôm khác/nghỉ hôm nay, đầu ngày/cuối ngày, số đếm tab/nhân viên/lịch sử, phân trang và khoảng một đầu/trống. Jasper biên dịch không lỗi; trình duyệt hiển thị 7 đơn gửi hôm nay dù ngày nghỉ từ 23–30/10. Không ghi dữ liệu hay quyết định duyệt thật.

## BF-05 — gọn thanh duyệt nhiều và mặc định ngày hôm nay (22/10/2026, ĐÃ CHỐT)

Gộp số đã chọn và hai nút duyệt nhiều vào bên phải thanh trên bảng, thay dòng hướng dẫn Bấm hàng/Ưu tiên sắp nghỉ; bỏ khung thao tác riêng. Giữ ID/form liên kết checkbox và hộp xác nhận hiện có.

Khi URL chưa có cả hai tham số ngày, Từ ngày và Đến ngày mặc định là hôm nay theo DateTimeUtil (giờ Việt Nam). Bộ lọc ngày gửi đơn tính trọn hai ngày biên theo quyết định sửa ở trên. Người dùng có thể xóa một hoặc cả hai ô để xem phạm vi mở; tab/phân trang gửi cả tham số rỗng để không tự điền lại hôm nay. Xóa lọc quay về ngày mặc định hôm nay. Quyết định này thay mặc định trống của bộ lọc ngày trước đó.

Kiểm chứng: clean package chạy 220 test không lỗi; Jasper biên dịch JSP/fragment không lỗi. Trình duyệt xác nhận ngày mặc định, chủ động xóa ngày, giữ ngày trống khi đổi tab/phân trang và thanh duyệt nhiều nằm trong đầu bảng. POST xem thử sau khi chuyển form vẫn gửi đúng các mã đã tick, CSRF và returnUrl, không ghi quyết định thật.

## BF-05 — duyệt nhiều đơn trên trang Chờ duyệt (22/10/2026, ĐÃ CHỐT)

Thêm checkbox từng đơn, chọn hết trang, Duyệt đã chọn và Duyệt tất cả trên trang. Phạm vi mặc định là trang đang xem (tối đa 50 đơn), không tự chọn đơn ở trang khác. Chỉ cho tick đơn có quyền xử lý và kiểm tra Đạt; cả hai nút đều hỏi xác nhận số lượng trước khi POST. Không lồng form: checkbox liên kết form duyệt nhiều bằng thuộc tính form.

Service giữ thứ tự của danh sách (sắp nghỉ/FIFO), bỏ mã trùng và gọi approveRequest hiện có lần lượt. Mỗi đơn có transaction riêng, đọc lại trạng thái/phân công/quỹ phép/nhân sự/lịch PT sau các đơn đã duyệt trước đó. Đơn không đủ điều kiện không bị thay đổi, các đơn thành công vẫn được lưu; kết quả sau redirect hiện số thành công và mã/lý do của từng đơn chưa duyệt. POST kiểm tra login/CSRF và giới hạn 50 mã; không đổi schema hoặc viết câu UPDATE duyệt hàng loạt bỏ qua nghiệp vụ.

Kiểm chứng: clean package chạy 220 test không lỗi, gồm kết quả thành công một phần, giữ thứ tự, bỏ mã trùng, dữ liệu sai, login/CSRF và giữ bộ lọc qua redirect. Jasper biên dịch JSP/fragment không lỗi. Trình duyệt kiểm tra chọn từng đơn/chọn hết, trạng thái checkbox chọn một phần, hủy bằng Escape, khóa đơn vướng lịch, khóa cả hai nút khi không có đơn đủ điều kiện. POST ở bản xem thử chỉ in form: nhận đúng action, các mã đã chọn và CSRF, không gọi Service ghi quyết định.

## BF-05 — mở mặc định tab Chờ duyệt (22/10/2026, ĐÃ CHỐT)

Khi mở `/manager/leave-requests` mà URL không có `status`, mặc định chọn Chờ duyệt để Quản lý xử lý các đơn đang chờ. Tab Tất cả vẫn mở bằng `status=All` và tiếp tục hiện nhóm nhân viên cùng lịch sử đơn.

## BF-05 — bấm hàng mở chi tiết, xử lý ngay trên danh sách (22/10/2026, ĐÃ CHỐT)

User chốt bấm phần thông tin/khoảng trống của một hàng đơn để mở chi tiết; bỏ nút Xem chi tiết. Pending có hai nút Duyệt/Từ chối cuối hàng: xác nhận trước khi duyệt, từ chối bắt buộc lý do 5–500 ký tự. Dùng data-confirm-message/data-confirm-reason-name có sẵn trong common.js; không viết thêm hệ thống hộp thoại. JS riêng chỉ mở hàng bằng click/Enter, bỏ qua nút/form và giữ focus bàn phím khi mở/đóng hộp.

Form gửi CSRF, mã đơn và hành động tới LeaveRequestReviewServlet hiện có. Service vẫn kiểm tra lại trạng thái, quyền/phân công, quỹ phép, nhân sự, lịch PT và ghi transaction/audit/thông báo. Duyệt bị khóa khi chưa đạt kiểm tra hoặc không có quyền; Từ chối cần quyền xử lý. Đơn đã xử lý chỉ mở được chi tiết. Thành công quay lại tab/bộ lọc/trang đang xem bằng returnUrl, chỉ chấp nhận đường dẫn danh sách nội bộ; lỗi giữ luồng hiển thị chi tiết hiện có.

Kiểm chứng: clean package chạy 213 test, không lỗi, gồm hợp đồng POST, CSRF, phiên đăng nhập, giữ lý do, quay lại bộ lọc và chặn returnUrl ngoài danh sách. Jasper biên dịch hai JSP quản lý/fragment không lỗi. Trình duyệt kiểm tra click/Enter mở hàng, hai nút không điều hướng nhầm, xác nhận, lý do bắt buộc, nút duyệt bị khóa, Tab/Escape/focus và thao tác trong nhóm nhân viên. POST trên bản xem thử chỉ in dữ liệu form, không gọi Service ghi DB; nhận đúng action/id/CSRF/lý do/returnUrl. Không duyệt/từ chối đơn thật hoặc redeploy Tomcat chính.

## BF-05 — chỉ nhóm nhân viên ở tab Tất cả (22/10/2026, ĐÃ CHỐT)

User chốt Tất cả mới hiện toàn bộ PT/Lễ tân và mở lịch sử đơn của từng người. Chờ duyệt/Đã duyệt/Từ chối chuyển về bảng từng đơn, phân trang theo đơn tại SQL; Tất cả vẫn phân trang theo nhân viên. Bộ lọc tên/chức vụ/loại/ngày và tab tiếp tục được giữ khi điều hướng. Mặc định Tất cả ở thời điểm này đã được thay bằng quyết định Chờ duyệt ngày 22/10/2026 ở trên; không thay schema hoặc chính sách duyệt.

Thứ tự Chờ duyệt: nhóm có FromDate hôm nay hoặc ngày mai lên trước (cùng điều kiện nhãn Sắp đến ngày nghỉ), rồi SubmittedAt tăng dần; cùng thời điểm nộp thì LeaveRequestId tăng dần để phân trang ổn định. Ngoài nhóm sắp nghỉ, đơn nộp trước đứng trước, không tiếp tục xếp theo ngày nghỉ. Đơn đã xử lý xếp theo ReviewedAt mới nhất.

Bảng trạng thái không có cột người duyệt. Chỉ bảng lịch sử bên trong nhân viên hiện Người xử lý từ ReviewedByEmployeeId; Pending hiện dấu —, không lấy tên người được giao làm người đã xử lý. Hai kiểu bảng dùng chung leave-request-table.jspf để giữ cách hiển thị ngày/ca, loại, kiểm tra, trạng thái và Xem chi tiết nhất quán.

Kiểm chứng: clean package chạy 205 test, không lỗi; mở rộng kiểm tra điều kiện duyệt để bảng từng đơn và nhóm nhân viên khớp với chi tiết/POST. Jasper biên dịch hai JSP quản lý và fragment mới không lỗi. SQL production chạy trên VALUES chỉ đọc xác nhận nhóm sắp nghỉ, FIFO, cùng thời điểm nộp, phân trang, tab và lọc ngày/loại; đối chiếu thêm các Pending hiện có trên DB local. Trình duyệt xác nhận Chờ duyệt không có nhóm/cột người xử lý, Tất cả có nhóm và lịch sử có cột người xử lý, trạng thái rỗng, lọc ngày/loại và mở chi tiết. Không ghi quyết định hoặc redeploy Tomcat chính.

## BF-05 — gọn màn gửi đơn nhân viên (22/10/2026, ĐÃ CHỐT)

User yêu cầu bỏ hai khung Hạn nộp đơn/Dự báo nhân sự ở bên phải và đặt một nút i tròn ở góc trên bên phải khung Tạo đơn nghỉ phép. Form/lịch sử về một cột; thông tin chuyển vào HintBox chung của common.css/common.js (hover/focus/chạm, Escape hoặc bấm ngoài để đóng). Giữ các ID để JS cập nhật hạn nộp và dự báo theo ngày/ca; bỏ dòng hạn nộp lặp dưới ô ngày. Không đổi hạn nộp P-12 hoặc nghiệp vụ gửi/rút/duyệt đơn. Dự báo vẫn chỉ xét ngày bắt đầu và ghi rõ giới hạn trong chú thích.

Kiểm chứng: package thành công; Jasper biên dịch JSP nhân viên không lỗi; trình duyệt xác nhận form PT/Lễ tân, chú thích ẩn ban đầu, mở bằng bàn phím/bấm nút, đóng bằng Escape và cập nhật khi đổi ngày/ca. Dùng bản xem thử chỉ đọc, không gửi/rút đơn hoặc redeploy Tomcat chính. Không thêm test cho thay đổi bố cục có thể đảo ngược này.

User yêu cầu ít chữ hơn: rút gọn chú thích còn hạn nộp, hướng dẫn báo Quản lý, ngày/ca dự báo, số PT/Lễ tân và một dòng giới hạn. Khi đổi lựa chọn, chỉ hiện ngày và tên ca.

## BF-05 — thêm hai ô lọc ngày nghỉ (21/10/2026, ĐÃ CHỐT)

User yêu cầu thêm Từ ngày/Đến ngày, mặc định trống; lọc theo thời gian nghỉ có giao với khoảng chọn, tính cả ngày biên, cho phép chỉ nhập một đầu. Khi chọn ngày, chỉ hiện nhân viên có đơn khớp toàn bộ bộ lọc; khi không chọn ngày, vẫn giữ hành vi hiện toàn bộ nhân viên như đợt trước. Service báo lỗi khoảng đảo chiều; tab, số lượng và phân trang dùng cùng khoảng ngày. Xóa lọc bỏ ngày và giữ tab. Không đổi schema hoặc luồng duyệt. User yêu cầu spam commit trở lại; đợt này chia hai commit backend và giao diện.

## BF-05 — nhóm đơn theo nhân viên (21/10/2026, ĐÃ CHỐT THỬ)

User yêu cầu hiển thị toàn bộ nhân viên và bấm mở các đơn. Triển khai toàn bộ PT/Lễ tân (hai chức vụ đang hỗ trợ nghỉ phép), giữ người chưa có đơn và lịch sử người đã nghỉ việc. Phân trang theo nhân viên tại SQL, đếm tổng/Pending trên hàng cha, badge đỏ khi sắp nghỉ; mặc định Tất cả. Tab/loại/ngày lọc đơn bên trong, tên/mã/chức vụ lọc nhân viên. Dùng details/summary native; quyết định vẫn ở trang chi tiết, không đổi schema hoặc chính sách nghỉ. Luồng đọc code: [BF-05-code-guide.md](BF-05-code-guide.md).


## BF-05 — bỏ hạn duyệt, nhắc theo ngày nghỉ (21/10/2026, ĐÃ CHỐT)

User đồng ý bỏ cột đếm ngược và mốc nộp + 3 ngày. Pending nghỉ hôm nay/ngày mai hiện Sắp đến ngày nghỉ; ưu tiên Pending sắp nghỉ theo ngày bắt đầu. Job nhắc một lần cho Pending nghỉ ngày mai, không dùng giờ P-20 làm hạn duyệt. Giữ kiểm tra quyền/quỹ phép/nhân sự/lịch PT và chặn duyệt ngày đã qua. Quyết định này thay các mô tả hạn P-20/SLA cũ trong SRS/checklist; không đổi schema hoặc xóa cấu hình dùng chung. Chi tiết: [BF-05-code-guide.md](BF-05-code-guide.md).


## BF-05 — refactor dễ học, gom commit theo nhóm (21/10/2026)

User chốt đợt này không chia commit quá nhỏ. Tách luồng nhân viên/Manager, dùng chung điều kiện xem và duyệt, tách DTO khỏi DAO, bỏ ngày lưu trùng và CSS/JS khó lần theo. Luồng và nơi bắt đầu học: [BF-05-code-guide.md](BF-05-code-guide.md). Không đổi schema hoặc chính sách ca; tiếp tục giả định nhân viên đang làm việc làm cả hai ca. JOB-07 đặt tên theo chức năng nhắc hạn hiện có.


## BF-05 — duyệt nghỉ phép và ca cố định (21/10/2026, chốt trong chat)

- Ưu tiên checklist BF-05; SRS sẽ cập nhật sau. Tạm giả định mọi PT/Lễ tân Working làm cả hai ca, chưa thêm bảng phân ca thực tế. Giao diện ghi rõ giới hạn này.
- Hỗ trợ đơn nhiều ngày bằng FromDate/ToDate/Days; kiểm tra đủ ngày/ca, quỹ phép và lịch PT trong transaction duyệt. Người được giao và trạng thái được đọc lại khi ghi quyết định.
- Quỹ Annual lấy P-23 hiện có, không tự mặc định 14 khi thiếu cấu hình; các đơn Pending khác vẫn giữ chỗ. Không tính giữ chỗ hai lần cho đơn đang xét. Sick/Unpaid không dùng quỹ Annual.
- Kết quả, bằng chứng kiểm tra và phần nâng cao chưa làm (Withdrawn, xử lý buổi PT, báo vắng khẩn cấp) được ghi tại [BF-05-implementation.md](BF-05-implementation.md). Chưa đổi schema/data của DB local hoặc redeploy Tomcat.

## Customer Swap UC-38 — runtime My PT Session (14/10/2026)

- Đã nối nút Đổi buổi trên buổi gói PT Scheduled chưa bắt đầu. Dialog tải slot qua GET AJAX `action=swapOptions`, xác nhận qua POST `action=swap` với CSRF token và mã slot nguồn để phát hiện lịch đã bị người khác đổi.
- Backend chỉ cho đổi gói PTSingle/PTCouple Active, đúng người đứng tên hoặc partner, còn lượt theo MaxSwapsSnapshot − SwapsUsed. Không dùng P-08 hiện tại hoặc ghi cứng 3 lượt; không đổi buổi lẻ/đang diễn ra/đã chốt.
- Slot mới phải Free của PT thực tế đang phụ trách buổi, cùng độ dài, chưa bắt đầu, T7/CN trong hạn gói và cửa sổ CBR-39 (tuần sau mở từ T7). Kiểm tra giờ mở cửa P-19/ngày ngoại lệ, Membership hoặc DayPass theo notes và kỳ bảo lưu; gói đôi cần ít nhất một thành viên đủ quyền vào gym.
- Chặn trùng lịch Scheduled của PT và bất kỳ thành viên gói với mọi gói khác. Service dùng transaction SERIALIZABLE, khóa buổi/gói/người tham gia, kiểm tra lại slot/quota lúc xác nhận. Bất kỳ bước nào lỗi đều rollback.
- Cập nhật TimeSlotId của chính buổi, slot đích Booked, nhả slot cũ khi không còn Scheduled, tăng SwapsUsed một lần; giữ nguyên trạng thái Scheduled, giá trị/hoa hồng, hạn gói và tổng buổi. Ghi SessionMoveLogs.CustomerSwap, AuditLogs và thông báo trong ứng dụng cho PT cùng các thành viên có tài khoản, trong cùng transaction.
- Rỗng/hết lượt/slot mất hiệu lực có thông báo riêng; chọn Giữ lịch cũ hoặc Escape không ghi DB. Chưa triển khai thao tác dời/chuyển PT UC-35 trong lần này.

Theo agents.md Phần 3, 4, 7.3: mọi chỗ SRS/SDS/prototype/chat nói khác nhau được ghi ở đây, kèm trạng thái.
Khi nhóm chốt xong 1 mục → đổi trạng thái thành **ĐÃ CHỐT**, ghi ngày + phương án, và sửa lại tài liệu gốc cho khớp.

## Ngày phòng nghỉ: PT dời buổi thay vì không sinh buổi (10/10/2026, ĐÃ CHỐT trong chat)

- Quyết định này thay thế hướng bù buổi/ngày nghỉ trước đó và CBR-56 cũ: bán gói PT vẫn sinh đủ buổi cho mọi ngày T2–T6 trong kỳ, kể cả ngày phòng nghỉ đã khai báo. `GeneratedSessionCount` không loại ngày nghỉ.
- Manager khai báo ngày phòng nghỉ ở `OperatingHourExceptions.IsClosedAllDay = 1`. Trước ngày nghỉ, PT và khách thống nhất rồi PT dời chính buổi đó qua UC-35 / `PtReschedule` (BR-64): không trừ buổi, không tăng `SwapsUsed`, giữ tổng quota. Chọn slot khác của chính PT trong hạn gói, kể cả T7/CN hoặc giờ khác trong ngày; slot đích phải hợp lệ và không rơi vào thời gian phòng đóng cửa. Không dùng CustomerSwap vì sẽ tiêu P-08.
- PT Dashboard, khối Cần chú ý: cần thêm cảnh báo “Buổi rơi vào ngày phòng nghỉ, cần dời”.
- Lưới an toàn: buổi còn Scheduled khi ngày phòng nghỉ đã qua được JOB-04 chốt `Cancelled / GymClosed`; xét TRƯỚC quyền vào gym và check-in. Không trừ buổi, không tính hoa hồng. Buổi ngày nghỉ tương lai vẫn Scheduled để PT dời, không hủy trước ngày.
- Buổi lẻ (SingleSession) không được đặt vào ngày phòng nghỉ.
- Không cần đổi cấu trúc DB: đã có OperatingHourExceptions, Sessions.CancelType = GymClosed và SessionMoveLogs.MoveType = PtReschedule. Giữ ràng buộc hủy hệ thống không có CancelledByAccountId, không RefundAgreed. Không tạo thêm buổi/quota tự động hoặc tự gia hạn.
- Đã sửa seed trong lần này: `seedgen.py` sinh đủ ngày T2–T6; ngày nghỉ quá khứ chưa dời mô phỏng JOB-04 với GymClosed lúc 00:05 ngày hôm sau; ngày nghỉ tương lai giữ Scheduled/Booked. Gói Mai 07/09–07/11 có 45 buổi, ngày 20/10 giữ Scheduled. Thêm kiểm tra ngày nghỉ cho hàm sinh buổi lẻ; cập nhật comment schema và sinh lại `2_Seed_Data.sql`.
- Cần sửa tiếp code runtime: JOB-04 (đọc lịch nghỉ và ưu tiên GymClosed), cảnh báo PT Dashboard, kiểm tra ngày nghỉ khi đặt buổi lẻ và rà luồng bán gói/dời buổi theo chính sách mới. Seed đã đổi không có nghĩa runtime đã hỗ trợ.
- Cần sửa SRS/SDS: BR-40, CBR-56, CBR-57, BF-08/UC liên quan và bổ sung rule đặt buổi lẻ; bỏ mô tả không sinh buổi ngày nghỉ. Rà ý nghĩa “không trừ buổi” trong số liệu/quota và cách xử lý quyền lợi nếu buổi bị lưới an toàn hủy khi chưa dời; chưa tự triển khai phục hồi hoặc bù/gia hạn sau hủy.
- Chưa chạy schema/seed lên SQL Server đang dùng. Muốn nạp bộ seed mới, nhóm chạy lại bộ 3 file SQL theo quy trình reset dữ liệu của dự án; thao tác này cần chủ động vì thay dữ liệu test hiện tại.

## My PT Sessions — Lịch sử buổi tập và bộ lọc (10/10/2026, ĐÃ CHỐT trong chat)

- Phân nhóm hiển thị (chỉ UI, không UPDATE DB), dùng **một `now` giờ VN cho cả request**: Scheduled kết thúc > now → Lịch tập sắp tới; Scheduled kết thúc ≤ now → Lịch sử "Kết quả đang cập nhật"; CheckedIn → "Đã check-in"; Missed → "Vắng" + lý do; Cancelled (kể cả buổi tương lai) → "Đã hủy" + lý do.
- Nhãn CheckedIn là "Đã check-in", không dùng "Đã tập"/"Hoàn thành". Giải thích nằm trong hint chung của khối Lịch sử (tạm bỏ hint từng buổi): cá nhân/buổi lẻ "Bạn đã check-in…", gói đôi "Bạn hoặc người tập cùng đã check-in…"; không xác nhận buổi PT đã hoàn thành.
- Lịch sử đọc `Sessions.Status/MissedReason/CancelType/CancelReason`, không tính lại từ CheckIns hay membership hiện tại; không lọc `Subscriptions.Status`, không lọc `Packages.Status` (CBR-55). `CancelReason` in nguyên văn. Thêm enum `Session.CancelType`.
- Cập nhật 11/10/2026: My PT Sessions bỏ rèm `<details>` và nút Mở tất cả/Thu gọn; cả Sắp tới và Lịch sử luôn hiện đầy đủ các buổi theo nhóm ngày. Phân trang theo 8 ngày có buổi/trang, không cắt cùng ngày sang hai trang; đang triển khai từng bước, chưa nối truy vấn phân trang. PT Dashboard giữ cơ chế thu gọn hiện có.
- **Bộ lọc chung** chỉ gồm Loại buổi, Từ ngày, Đến ngày, áp dụng cho cả Sắp tới và Lịch sử. **Không có ô Trạng thái** trong bộ lọc chung.
- **Trạng thái riêng của khối Lịch sử**: Tất cả · Kết quả đang cập nhật · Đã check-in · Vắng · Đã hủy. "Tất cả" gồm cả Scheduled đã kết thúc. Chọn trạng thái lịch sử không làm khối Sắp tới rỗng. Các điều kiện kết hợp AND.
- Rỗng: đang có điều kiện lọc → "Không có buổi phù hợp với bộ lọc"; không lọc → thông báo rỗng mặc định của từng khối (GBR-21).
- Giá trị không hợp lệ (ngày sai, Từ ngày > Đến ngày, lựa chọn lạ): giữ nguyên giá trị người dùng nhập, báo lỗi ngay ô tương ứng, **không áp dụng yêu cầu lọc đó** (không áp dụng một phần), giữ danh sách mặc định và ghi "Bộ lọc chưa được áp dụng". Không trả 500.
- Code: tách dữ liệu thô (tham số URL) khỏi filter đã hợp lệ; validate trả `ServiceResult` + `failFields` sẵn có. DAO được ghép đoạn SQL cố định, giá trị người dùng luôn truyền bằng `?`.
- Xóa lọc: link về URL gốc, xóa cả bộ lọc chung lẫn trạng thái lịch sử. Đổi bộ lọc chung thì giữ trạng thái lịch sử đang chọn nếu hợp lệ.
- Phân trang đang triển khai từng bước: đã có DTO trang, đọc số trang URL và DAO `countUpcomingSessionDays` / `countHistorySessionDays` đếm ngày sau lọc bằng `COUNT(DISTINCT SlotDate)`. Sắp tới xét Scheduled chưa kết thúc; lịch sử xét đúng nhóm hiện tại và trạng thái lọc riêng.
- Bước 4 đã có DAO `findUpcomingSessionsPage` / `findHistorySessionsPage`: CTE FilteredSessions giữ đúng quyền xem/nhóm/bộ lọc, PageDays chọn các ngày bằng DISTINCT + OFFSET/FETCH, rồi JOIN lấy đủ buổi thuộc ngày đã chọn. Service sẽ truyền offset và cỡ trang 8; chưa nối vào Service/Servlet/JSP nên màn hiện tại vẫn dùng luồng lấy danh sách cũ. Không tải toàn bộ kết quả vào Java để cắt trang; không tách buổi cùng ngày. Đã compile và đối chiếu 288 trang trên SQL Server local với danh sách cũ, có lọc/trạng thái/gói đôi/rỗng và ngày nhiều buổi. Chưa làm AJAX.
- Bước 5 đã thêm Service `getUpcomingSessionsPage` / `getHistorySessionsPage`: kiểm tra tài khoản/hồ sơ → gọi DAO đếm ngày sau lọc → tính tổng trang (tối thiểu 1), chặn số trang trong 1..totalPages → tính offset → gọi DAO lấy trang → bổ sung nhãn/cảnh báo hiện có → gom ngày và tạo `MyPtSessionPage`. Cỡ trang dùng hằng `DAYS_PER_PAGE = 8`. Không có ngày thì bỏ truy vấn lấy buổi, trả trang 1/1 với map rỗng; lỗi truy vấn trả fail, không trả rỗng. Chưa nối Servlet/JSP, nên màn hình vẫn dùng hai hàm Service cũ cho tới bước tiếp theo.
- Kiểm tra bước 5: compile Java 17 thành công; đối chiếu 124 trang Service trên SQL Server local, gồm rỗng và các mốc 8/9/16/17 ngày, số trang âm/vượt giới hạn, khách đứng tên/đi cùng gói đôi, nhãn/cảnh báo khớp luồng cũ; tài khoản không tồn tại bị từ chối. Kiểm tra chỉ đọc, không sửa dữ liệu DB.

## Hạn mức đổi buổi gói PT theo tháng (10/10/2026, ĐÃ CHỐT trong chat)

- P-08 đổi nghĩa: **số lượt đổi buổi cho mỗi tháng của gói PT**, mặc định 3. Hạn mức một gói = P-08 × số tháng của gói → gói 1/2/3 tháng được 3/6/9 lượt. Buổi lẻ vẫn không được đổi (BR-74).
- Schema không đổi: `Subscriptions.MaxSwapsSnapshot` đã lưu hạn mức riêng từng gói, chốt lúc bán (GBR-04), TINYINT đủ chứa. Service bán gói PT (UC-21) phải ghi `MaxSwapsSnapshot = P-08 × Packages.DurationValue` (gói PT luôn tính theo tháng, P-21); màn đổi buổi so `SwapsUsed` với `MaxSwapsSnapshot`, không đọc thẳng P-08.
- Đã sửa: `seedgen.py` (hằng `SWAPS_PER_MONTH`), mô tả P-08 trong `seeddata.py`, comment cột trong `1_Init_Schema.sql`, comment `SystemConfigService.MAX_SWAP_COUNT` và `Subscription`. Sinh lại `2_Seed_Data.sql`; runtest, negtest, bizcheck đều qua.
- Cần sửa SRS: BR-73 ("at most P-08 swaps per package") và mô tả P-08 ở III.4.

## Seed bảo lưu theo quyết định 09/10 (10/10/2026)

- `seedgen.py` trước đây chốt sẵn mọi buổi trong kỳ bảo lưu thành `Missed/MembershipFrozen` và nhả slot, kể cả buổi tương lai. Đã sửa: buổi chưa tới ngày giữ `Scheduled`, slot `Booked`; chỉ buổi đã qua ngày mới là `Missed/MembershipFrozen` (như JOB-04 chốt). Cập nhật `docs/database/SEED_CASES.md`.
- Còn để ngỏ (chưa sửa): 9 buổi `Missed/NoCheckIn` mà cùng ngày khách có check-in vào gym (check-in ngẫu nhiên trùng ngày); seed vẫn nhả slot về `Free` cho mọi buổi `Missed`, trong khi mục JOB-04 ghi JOB-04 không giải phóng slot.
## My PT Sessions — quyền vào gym thống nhất theo JOB-04 (10/10/2026, ĐÃ CHỐT trong chat)

- Quyết định có hiệu lực: xét quyền vào gym của buổi PT theo logic JOB-04 hiện tại, chấp nhận **Membership hoặc DayPass hợp lệ theo ngày buổi tập**, áp dụng cho cả `PTSingle`, `PTCouple` và `SingleSession`.
- Với gói đôi, ít nhất một thành viên có quyền vào gym hợp lệ thì không cảnh báo cả buổi là thiếu quyền vào gym. Có thể giải thích riêng tình trạng từng người khi cần.
- Áp dụng thống nhất cho cảnh báo trên My PT Sessions, PT/Customer Dashboard và kiểm tra quyền vào gym trong các thao tác liên quan buổi PT; không dùng CBR-63 cũ để phủ nhận DayPass đã được chấp nhận theo quyết định này.
- Nếu có DayPass hợp lệ cho ngày đó thì không hiện cảnh báo chỉ vì thiếu membership. Giữ nguyên điều kiện bảo lưu trong JOB-04 hiện tại: `canUseGym = (hasMembership || hasDayPass) && !isFrozen`; DayPass không tự bỏ qua kỳ bảo lưu. Gói đôi chỉ coi cả buổi thiếu quyền khi cả hai đều không dùng được gym.
- Đầu việc tài liệu: sửa CBR-63 và rà CBR-61, CBR-66, CBR-67 cùng mô tả BF-08/UC liên quan để thống nhất điều kiện Membership hoặc DayPass; cảnh báo thiếu quyền vào gym không được diễn đạt thành bắt buộc mua membership khi DayPass đã hợp lệ.
- Không suy rộng thành mọi điều kiện mua/đặt buổi đều được bỏ: các giới hạn slot, thời gian, hạn gói và lượt đổi vẫn giữ theo rule tương ứng.
- Đây là quyết định đã chốt, không hỏi lại khi triển khai. Lần này chỉ cập nhật notes, chưa sửa SRS/SDS hoặc code cảnh báo.

## Gói PT — lượt đổi theo thời hạn gói (10/10/2026, ĐÃ CHỐT; cập nhật triển khai xem mục đầu file)

- Thay giới hạn 3 lượt cho toàn bộ gói bằng tổng lượt theo số tháng thời hạn gói: 1 tháng → 3 lượt, 2 tháng → 6 lượt, 3 tháng → 9 lượt. Cấp tổng giới hạn ngay lúc mua; dùng linh hoạt trong hạn gói, không reset theo tháng hoặc chu kỳ 25 ngày.
- Giữ cách lưu hiện tại: lúc thanh toán tạo Subscription, tính `MaxSwapsSnapshot = số lượt mỗi tháng × số tháng của gói`; `SwapsUsed` vẫn là tổng lượt đã dùng của cả gói. Gói đôi dùng chung giới hạn, không nhân thêm theo số thành viên; SingleSession không được đổi theo BR-74.
- Theo GBR-04, chỉ áp dụng chính sách mới cho gói mua mới; gói cũ giữ `MaxSwapsSnapshot` đã lưu, không tự cập nhật hàng loạt.
- Đầu việc tài liệu: sửa BR-73 và mô tả P-08 để nói rõ số lượt đổi trên mỗi tháng thời hạn gói, thay cho số lượt tối đa của cả gói; rà các mô tả UC-38, đổi hộ khách và snapshot trong SRS/SDS để thống nhất.
- Đầu việc code: rà các luồng thanh toán/tạo Subscription để tính snapshot mới. Các luồng đổi lịch vẫn kiểm tra tổng `SwapsUsed` với `MaxSwapsSnapshot`; DAO/Service/JSP khối Gói PT của bạn vẫn tính lượt còn lại từ snapshot trừ lượt đã dùng.
- Không cần thêm bảng/cột cho phương án này: đã có `MaxSwapsSnapshot` và `SwapsUsed`. Có thể cập nhật seed data để minh họa chính sách mới khi test; không chạy seed/migration hoặc sửa dữ liệu đang dùng trong lần ghi notes này.
- Cập nhật sau khi pull develop ngày 10/10/2026: seed, comment schema/entity/config đã được sửa như mục đầu file. Các đầu việc SRS/SDS và rà luồng bán gói/tạo snapshot ở trên vẫn cần đối chiếu; không suy ra dữ liệu DB đang chạy đã được cập nhật.

## Danh mục gói — ngừng bán và gói khách đã mua (10/10/2026, ĐÃ CHỐT trong chat)

- Quyết định: cho phép chuyển `Packages.Status` sang `Inactive` để ngừng bán dù còn khách sử dụng. UI gọi thao tác này là **Ngừng bán**, không gọi Xóa; giữ bản ghi để tra cứu lịch sử, không xóa vật lý.
- Ngừng bán chỉ chặn bán mới, không thay đổi các đơn và subscription đã tạo. `Subscriptions.Status = Active` vẫn giữ hiệu lực, thời hạn, lịch tập và quyền lợi đã chốt; không đổi trạng thái theo `Packages.Status`.
- Mâu thuẫn cần sửa tài liệu: **GBR-06** hiện cấm deactivate khi còn bản ghi Active tham chiếu, trong khi ghi chú màn quản lý gói cho phép ngừng bán gói còn đơn đang chạy. Cần bổ sung ngoại lệ cho danh mục `Packages` vào GBR-06 và thống nhất thuật ngữ/nghiệp vụ ngừng bán ở SRS, SDS nếu có nội dung tương ứng. Không mở rộng ngoại lệ này sang hồ sơ khách hoặc nhân viên.
- Áp dụng ở My PT Sessions: `findActivePtPackages(customerId)` lọc `Subscriptions.Status = Active`, loại `PTSingle`/`PTCouple`, đơn `Paid` và quyền sở hữu/đi cùng; không lọc `Packages.Status`. Gói khách đã mua vẫn hiện khi danh mục đã ngừng bán.
- Trạng thái tài liệu: mới ghi quyết định và đầu việc tại notes; **chưa sửa SRS/SDS gốc**. DAO/Service hiện tại giữ nguyên.

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
- Các thao tác customer cần tách rõ khỏi thao tác xem: chỉ buổi `Scheduled` chưa bắt đầu mới được đổi; buổi `Scheduled` chưa kết thúc được hủy, kể cả khi đang diễn ra. Buổi đã `CheckedIn`, `Missed` hoặc `Cancelled` là lịch sử, không cho sửa trực tiếp.
- Customer swap chỉ chọn slot được công bố và còn trống theo giới hạn thời gian/tuần của nghiệp vụ; đổi buổi phải giữ đúng gói, PT và điều kiện membership, đồng thời ghi `SessionMoveLogs` và tăng `SwapsUsed` khi áp dụng. Không được sửa thẳng `TimeSlots` mà bỏ qua transaction.
- Hủy bởi customer là `Cancelled / ByCustomer`, vẫn tiêu buổi và không hoàn tiền; cần lưu account thực hiện và thời điểm theo schema. Đây là khác với PT hủy (`ByPt`) và gym hủy (`GymClosed`).
- Với `PTCouple`, màn hình phải hiện đủ hai khách liên quan nếu dữ liệu có partner; các quy tắc đủ điều kiện/check-in của gói đôi không được suy ra chỉ từ tên người đứng tên.
- Không đưa logic SQL, tính trạng thái hoặc quy tắc đổi/hủy vào JSP. DTO nên chứa sẵn nhãn, thời gian, tên PT, loại gói, trạng thái và quyền thao tác để JSP chỉ render.

## My PT Sessions — khách hủy buổi trước giờ kết thúc (13/10/2026, ĐÃ CHỐT)

- Gộp thao tác đổi buổi (UC-38) và hủy buổi (UC-39) vào từng buổi đủ điều kiện trên màn `Lịch tập của tôi` (`/customer/pt-sessions`); giữ `Đặt buổi lẻ` là trang riêng. Đây là quyết định về luồng UI, chưa có nghĩa backend đổi/hủy đã được triển khai.
- Khách được hủy khi buổi còn `Scheduled` **và thời điểm kết thúc của `TimeSlots` lớn hơn thời điểm hiện tại theo giờ VN**. Như vậy được hủy cả khi buổi đang diễn ra, nhưng không được hủy từ lúc buổi kết thúc trở đi, dù JOB-04 chưa chốt. Nút trên JSP chỉ phản ánh điều kiện để khách thấy; server phải kiểm tra lại tại lúc nhận yêu cầu.
- Mục đích của hủy khi đang diễn ra là báo ngay cho PT biết khách không đến để PT không phải tiếp tục chờ. Khi khách xác nhận, buổi chuyển thành `Cancelled / ByCustomer`, lưu `CancelledAt` và `CancelledByAccountId`; buổi vẫn bị trừ khỏi gói, không hoàn tiền, và PT được thông báo ngay. Modal phải nói rõ hậu quả trước khi xác nhận.
- Cần sửa SRS/BF-08: UC-39 đang viết “at any time”, mục My Bookings chỉ chặn theo `Scheduled`, còn mô tả BF-08 có thể bị hiểu là chỉ được hủy trước lúc bắt đầu. Tất cả phải ghi rõ điều kiện **trước giờ kết thúc** và cho phép hủy khi đang diễn ra; chuyển điểm vào thao tác từ My Bookings sang My PT Sessions cho khớp menu hiện tại.

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

## Quy tắc bổ sung: nhiều gói PT cùng thời hạn (chốt với người dùng, 13/10/2026)

- Khách được mua và sở hữu nhiều gói PT có thời hạn trùng nhau, với cùng PT hoặc các PT khác nhau. Không chặn mua chỉ vì khách đang có một gói PT Active.
- Mỗi lần mua là một Subscription riêng; thời hạn, quota buổi và lượt đổi của từng gói được quản lý độc lập, không cộng gộp hoặc chuyển qua lại.
- Không được xếp một buổi trùng giờ với buổi Scheduled khác của bất kỳ khách tham gia nào, kể cả thành viên gói đôi và buổi thuộc gói/PT khác. Kiểm tra này áp dụng khi bán gói, khách đổi buổi, PT đổi lịch hoặc chuyển PT; vẫn phải kiểm tra PT và slot đích đủ điều kiện.
- Ví dụ: khách có gói với PT Sơn lúc 08:00–09:30 có thể mua thêm gói với Sơn hoặc Trang lúc 15:00–16:30 trong cùng thời hạn; không được xếp thêm buổi 09:00–10:30 vào ngày đã có buổi Scheduled 08:00–09:30.
- Đây là quyết định nghiệp vụ bổ sung để đồng bộ vào SRS và triển khai; ghi chú này không xác nhận code hiện tại đã kiểm tra đầy đủ các điều kiện trên.

## C. File tài liệu được `agents.md` nhắc tới nhưng chưa có trong repo

`frontend-design-pipeline-SWP391-v4.md`, `shared-library-guide.md` — nếu nhóm còn dùng, thêm vào thư mục gốc.
Hướng dẫn dùng tokens/common hiện có: `docs/frontend-shared-usage.md`.
- 23/10/2026 — Gói của tôi: gộp đầu gói/Buổi tới, giữ vòng tròn, bỏ lịch sử vắng/hủy và field thừa; hint i nhỏ, chỉ mở khi bấm và kẹp trong màn hình; giải thích Buổi khác giờ gộp vào i Lịch cố định. PT Dashboard gộp cảnh báo vào thẻ gói theo SubscriptionId, thêm SĐT chủ gói/partner; giữ nhắc khách và dời buổi.
- Kiểm tra: 243 test pass, Jasper 0 lỗi, JS hợp lệ; seed Gia Hương/Mai và PT Sơn, desktop 1440 và mobile 390/320; draw.io cập nhật luồng/màu và tách 5 file theo màn.
- 23/10/2026 — MyPTSession, MyBookings và Lịch PT: xếp hàm chính trước, hàm phụ theo nhóm ngay dưới; ngăn nhóm bằng dòng dấu bằng dài. Đối chiếu AST giữ nguyên 121 định nghĩa Java, 15 thân hàm JS và thứ tự khởi tạo; 243 test pass, JS hợp lệ.
- 23/10/2026 — Lịch PT bước dựng khung: JSP/Servlet chuyển view=week|list bằng GET, giữ ngày và kiểu xem khi điều hướng tuần; cùng HTML đổi bố cục bằng CSS Grid. Chỉ có ô minh họa và nút khóa, chưa nối dữ liệu, kéo thả hoặc ghi DB; 243 test pass, Jasper 0 lỗi, kiểm tra desktop/mobile trên Tomcat QA.
- 23/10/2026 — Lịch PT bước 7: kéo vùng trống hoặc nhập ngày/giờ để xem trước một khung theo P-06/P-19, bám mốc 15 phút; CSS Grid đặt vị trí, link chuyển lịch tuần/danh sách giữ lựa chọn trong cùng tuần. Chọn lại thay khung cũ; Escape/thả ngoài cột hủy kéo, Bỏ xem trước chỉ xóa lựa chọn tạm.
- Kiểm tra bước 7: 243 test Java và 6 test JS pass, JSP biên dịch 0 lỗi; HTTP hai view 200, đọc cấu hình seed 90 phút và 05:30–22:00. Chưa ghi DB, chưa kiểm tra trùng buổi/đơn nghỉ/ngoại lệ; công bố vẫn khóa. Kiểm tra kéo thả thực tế trên Chrome chưa thực hiện do xét duyệt tự động chặn truy cập cửa sổ đang mở Google Docs.
- 23/10/2026 — Lịch PT bước 8: Servlet gọi getWeek(accountId, date, now), JSP lặp week.days/day.items thay ô mẫu; phân biệt lỗi tải với ngày trống, hiện nhãn nghỉ/phòng đóng cửa và các kết quả buổi. Giờ xem trước lấy theo từng ngày, gồm ngoại lệ; xử lý buổi, gỡ và công bố chưa nối, không ghi DB.
- Kiểm tra bước 8: 243 test Java, 9 test JS pass; WAR build thành công, Jasper 0 lỗi. Tomcat QA seed PT Sơn trả cùng 40 khối ở Tuần/Danh sách, tuần trống đủ 7 ngày, tuần phòng nghỉ vẫn có buổi, URL sai quay về tuần hiện tại; tài khoản không có hồ sơ PT hiện lỗi và không tải JS chọn khung. Kiểm tra trực quan trên trình duyệt chưa xác nhận.
- 23/10/2026 — Sửa UI Lịch PT: lịch chiếm đủ chiều ngang, form nhập tay mở/thu gọn bằng details, bỏ khối hướng dẫn lặp và chữ Khung giờ trên ô trống; thước giờ/đầu ngày giữ khi cuộn, màu theo token sáng/tối. Preview chuyển sang lớp Grid riêng để không chia đôi cột ngày; chặn chọn chữ khi kéo, giữ nút Bỏ xem trước ngoài form.
- Kiểm tra UI bằng Chrome headless với profile riêng, chỉ mở localhost QA: 12 trường hợp Tuần/Danh sách × 1440/390/320px × sáng/tối không tràn ngang trang; kéo không co thẻ hoặc bôi chữ, chuyển view/nhập giờ/bỏ xem trước pass, form mở ở 390/320px không tràn. 9 test JS pass, WAR build thành công, Jasper 0 lỗi; ảnh và báo cáo trong target/pt-schedule-ui-evidence.
- 23/10/2026 — Lịch PT bỏ sticky trong lịch và giới hạn chiều cao tạo thanh cuộn dọc phụ; giữ cuộn dọc mặc định của trang, chỉ cuộn ngang riêng khi màn hình hẹp. Nhãn phòng nghỉ có dòng riêng dưới ngày, tăng khoảng dòng và chừa khe giữa thẻ; kiểm tra 1440/390/320px ở Tuần/Danh sách: không có cuộn dọc trong lịch, chữ không tràn thẻ, wheel cuộn đúng trang. Đây là thay đổi thay thế cách cuộn riêng ghi ở mục UI trước.
- 23/10/2026 — Lịch PT bước 9: kéo/nhập thêm nhiều nháp, bỏ riêng/bỏ tất cả; sessionStorage giữ nháp theo tài khoản và tuần khi chuyển view hoặc tải lại. Công bố gửi một POST có CSRF; Service tính EndTime theo P-06, kiểm tra giờ/ngày nghỉ/phép/trùng lịch rồi ghi tất cả trong một transaction; mở lại Withdrawn đúng giờ bằng mã cũ, giữ nguyên giờ lịch sử.
- Kiểm tra bước 9: 259 test Java, 9 test JS pass; 4 test SQL chạy riêng xác nhận ghi danh sách, từ chối trùng, mở lại Withdrawn và khóa công bố đồng thời, toàn bộ dữ liệu thử rollback. Jasper 0 lỗi; QA headless kiểm tra kéo, giữ nháp, POST bị từ chối thật, quyền/CSRF và 12 trường hợp responsive không tràn trang/cuộn dọc phụ. Luồng thành công trên UI dùng phản hồi giả lập để không ghi dữ liệu QA; Chromium có cảnh báo ViewTransition khi điều hướng nhanh, không có lỗi JS ứng dụng. Ảnh/báo cáo tại target/pt-schedule-step9-evidence.
- 23/10/2026 — Đồng bộ dev_mb1_MinhLC trước khi chia 10 commit: nhận nhóm cảnh báo cùng lý do từ remote, giữ Cần để ý thu gọn và không khôi phục lịch sử vắng/hủy đã bỏ. Bản hợp nhất qua 260 test Java, 9 test JS; MyBookings, MyPTSession và Lịch PT biên dịch JSP 0 lỗi. Lịch PT hiện hoàn tất bước 9; bước 10 gỡ khung mới thống nhất cách chờ Công bố, chưa triển khai.
- 23/10/2026 — Lịch PT bước 10 (Gỡ khung): nút Gỡ khung trên khung Free chưa bắt đầu chỉ đánh dấu "chờ gỡ" (ptRemovals, lưu sessionStorage key riêng `.remove`); bấm lại = Giữ lại; Bỏ tất cả xóa cả nháp và chờ gỡ. Công bố gửi chung một POST (slotDate/startTime + removeSlotId). Service gỡ trước rồi thêm trong cùng transaction; DAO withdrawFreeSlot đổi Free → Withdrawn (DBR-36) chỉ khi đúng PT, đang Free, chưa tới giờ và không có buổi Scheduled; một khung không gỡ được thì rollback cả lần gửi. schedule.js sắp lại theo quy ước HÀM CHÍNH/HÀM PHỤ (hàm phụ ngay dưới, ghi rõ gọi ở bước nào).
- Kiểm tra bước 10: 266 test Java (5 test SQL bỏ qua vì cần DB local, đã thêm ca gỡ/gỡ lại/mở lại), 10 test JS pass. Chưa kiểm tra trên trình duyệt thật.
- 24/10/2026 — Lịch PT: Lịch tuần ẩn nút thao tác (thẻ nhỏ), nên bấm thẳng vào thẻ khung Còn trống (thẻ có nút Gỡ do Service cho canRemove) để đánh dấu/bỏ chờ gỡ; Danh sách vẫn có nút. Thẻ có khách/đang giữ/đã qua không phản hồi (Xử lý buổi là bước 11). Kiểm tra trên Tomcat + SQL Server thật bằng Chrome headless: gỡ + thêm, Công bố lỗi trùng buổi giữ nháp, Công bố lại → 773 Withdrawn, khung mới Free.
- 24/10/2026 — Lịch PT viết lại toàn màn cho dễ đọc và gọn (đã chốt với PT: bấm để đặt khung, điện thoại tự chuyển danh sách, thanh Công bố dính đáy, Java giữ nghiệp vụ).
  - UI: một panel giống màn Lịch tập của khách (‹ tuần ›, Thêm khung, Tuần | Danh sách gạch cam, chú thích màu). Bỏ kéo thả: rê chuột thấy bóng khung 90 phút (đỏ kèm lý do nếu trùng/ngoài giờ), bấm để thêm nháp; form "Thêm khung" (Ngày + Giờ bắt đầu) cho điện thoại và bàn phím.
  - Ít JS hơn: gỡ khung là checkbox trong thẻ (cả thẻ là label) → CSS :has() đổi kiểu "Chờ gỡ"; CSS counter đếm "x khung mới · y khung gỡ" và tự hiện thanh Công bố; kiểu danh sách là mặc định (mobile-first), lưới tuần chỉ ở ≥701px; thẻ tự xếp theo giờ bằng order. JS còn: thêm/bỏ nháp (template), lưu tạm sessionStorage, Công bố bằng FormData. Lỗi thêm khung dùng toast chung.
  - Service tính sẵn khoảng giờ được thêm khung mỗi ngày (firstStartMinute/lastStartMinute; hôm nay tính từ sau giờ hiện tại) thay cho JS; DTO thêm isBlocking/startOfDay/endOfDay. Tên ngày đổi T2/CN → Thứ 2/Chủ nhật cho giống màn khách.
  - Header PT tách ra css/pages/pt/header.css (trước nằm trong dashboard.css nên Lịch dạy lệch); _header.jspf có nút i khi Servlet đặt pageHelp; Lịch dạy hiện mã NV như Dashboard. Bỏ nút "Xử lý buổi" đang khóa (bước 11 sẽ làm thật).
  - Kiểm tra: 267 test Java + 5 test SQL thật (SQL Server, rollback) + 6 test JS pass; impeccable detect 0 lỗi. Chrome trên Tomcat + DB seed: thêm bằng bấm và form, gỡ, F5 giữ, Danh sách giữ, Công bố "2 khung, gỡ 1"; 1440 tối/sáng, 390/320 cảm ứng không tràn ngang; Tab + Space gỡ được.
- 25/10/2026 — Đặt tên tham số URL rõ nghĩa + Servlet ghi số dòng JSP:
  - Gói của tôi: `?id=` → `?package=` (Subscriptions.SubscriptionId hoặc "single"); Service đổi `selectedId` → `packageKey`.
  - Lịch tập: `?session=` → `?openSession=` (attribute `openSessionId`, `data-open-session`); `?from=` → `?backPackage=` (attribute `backPackageKey`). Bỏ attribute `dashboardError` vì không JSP nào đọc.
  - Quy ước mới: mỗi `getParameter` trong Servlet ghi JSP + dòng GỬI lên; mỗi `setAttribute` ghi JSP + dòng ĐỌC. Chỗ đọc nằm trong .jspf thì ghi thêm dòng của JSP cha nhúng jspf đó. Sửa JSP làm lệch số dòng thì phải sửa comment Servlet cùng commit.
  - Servlet 6 màn tách hàm phụ để doGet/doPost chỉ còn các bước (getAccountId, loadHeader, loadBookings, loadTimetable, loadBlocks, loadWeather, setPageInfo, isPtWithValidToken…). Hành vi giữ nguyên: 271 test pass; Tomcat + SQL Server: link gói, "Xem trên lịch tập", "Quay lại Gói của tôi", "Xem toàn bộ gói", lịch tháng mở đúng buổi; URL sai (`?package=abc`, `?openSession=-1`) vẫn 200.
