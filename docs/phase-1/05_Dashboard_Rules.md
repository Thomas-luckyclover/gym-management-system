# Dashboard: toàn bộ rule liên quan

> Nguồn: SRS (mục 9.1 Dashboards, 12.1 Reporting, bảng UC, ma trận quyền "Reports & Dashboards") + bộ rule v24 (GBR, CBR, DBR, BR, P-01…P-20) + schema `1_Init_Schema.sql`.
> Prototype chỉ để tham khảo giao diện. **Rule lấy theo SRS**.
> Chỗ nào SRS **chưa nói**, nhóm đã **chốt ngày 30/09/2026**, đánh dấu **[CHỐT-Dn]** (xem mục 6).

## 0. Có 4 dashboard

| # | Màn hình | Vai trò | URL | UC | SRS |
|---|---|---|---|---|---|
| 48 | Customer Dashboard | Khách | `/customer/dashboard` | UC-41 | 9.1.1 |
| 49 | PT Dashboard | PT | `/pt/dashboard` | UC-32 | 9.1.2 |
| 66 | Operational & Revenue Report | Manager | `/manager/dashboard` | UC-46 | 12.1.1 |
| 67 | System Operational Report | Admin | `/admin/dashboard` | UC-47 | 12.1.2 |

**Lễ tân không có dashboard.** Trang về sau khi đăng nhập của Lễ tân là màn **Check-in** (`/receptionist/check-in`, UC-19), đã định nghĩa trong `Role.getHomePath()`.

Cả 4 màn đều **chỉ xem** (không có form sửa dữ liệu). Mỗi màn là trang đích sau khi đăng nhập của vai trò đó.

---

## 1. Customer Dashboard (`/customer/dashboard`, UC-41)

### 1.1 Hiển thị gì

| Khối | Nội dung | Rule |
|---|---|---|
| Trạng thái hội viên | Trạng thái hồ sơ: **New / Active / Frozen / Expired** (Inactive không đăng nhập được) | DBR-34, DBR-35 |
| Được vào phòng tập không | `canUseService` = đang có **gói tập (Membership) hoặc vé ngày (Day Pass) còn hiệu lực**. Gói PT **không** cho vào phòng tập | DBR-47, DBR-48, CBR-58 |
| Gói tập + hạn | Gói Membership đang dùng, **ngày hết hạn là ngày cuối còn dùng được** (tính cả ngày đó) | GBR-03, CBR-03 |
| Gói nối tiếp | Gói mua khi gói cũ còn hạn bắt đầu **từ ngày sau** ngày hết hạn gói cũ. Hiện cả gói "chưa bắt đầu" | DBR-52 |
| Bảo lưu | Nếu Frozen: hiện khoảng bảo lưu. Hết bảo lưu, số ngày đã bảo lưu **được cộng vào hạn gói** | DBR-51, JOB-01 |
| Số buổi PT còn lại | **[CHỐT-D2]** Còn lại = số buổi `Scheduled` của gói, hiện kèm "Đã dùng X / Tổng `GeneratedSessionCount`". Theo từng gói PT. Gói PT bán **theo thời hạn**, số buổi chốt lúc đơn Paid (`Subscriptions.GeneratedSessionCount`). Gói đôi (couple) **dùng chung 1 quota** cho 2 người | DBR-53, DBR-54, BR-53 |
| Buổi tập sắp tới | **[CHỐT-D3]** 5 buổi `Scheduled` gần nhất từ thời điểm hiện tại + link "Xem tất cả". Các buổi `Scheduled` của khách (gồm cả buổi của gói couple mà khách là thành viên) | DBR-37 |
| Nhãn "Chưa có gói tập" | Buổi PT rơi vào ngày khách **không có Membership còn hiệu lực** thì gắn nhãn này **kèm nút gia hạn**. Mua hoặc gia hạn gói phủ ngày đó thì nhãn tự mất. Vé ngày **không** thay được Membership cho buổi PT | CBR-66, CBR-63, SRS 9.1.1 |
| Banner cảnh báo | 5 loại: Frozen, Expired, gói còn ≤ P-21 ngày, có buổi "Chưa có gói tập", đơn chờ thanh toán (chi tiết mục 6) | [CHỐT-D1] |

### 1.2 Luật hệ quả khách cần biết (hiện ở banner hoặc tooltip)

- **Frozen hoặc Expired thì không check-in được và không đặt buổi PT mới được** (UC-41).
- Buổi PT rơi vào ngày không có Membership **không diễn ra**: bị tính **Missed**, **vẫn trừ buổi**, không dời lịch, không gia hạn gói PT (CBR-64, BR-52).
- Bảo lưu gói tập: mọi buổi PT trong thời gian bảo lưu thành **Missed ngay**, bị trừ, không hoàn (CBR-62).
- Gói PT hết hạn: các buổi `Scheduled` còn lại thành **Cancelled**, không hoàn tiền, không gia hạn (CBR-55).
- Ngày phòng tập đóng cửa (khai báo sau khi đã thanh toán): buổi hôm đó **Cancelled, không trừ buổi** (CBR-57).

### 1.3 Quyền

- Chỉ vai trò **Customer**. Chỉ thấy dữ liệu **của chính mình** (GBR-10).
- Hồ sơ phụ thuộc (con) **không có tài khoản** (CBR-21), nên không có dashboard riêng. Người giám hộ xem hồ sơ con ở Account Settings, tab Hồ sơ phụ thuộc.

---

## 2. PT Dashboard (`/pt/dashboard`, UC-32)

### 2.1 Hiển thị gì

| Khối | Nội dung | Rule |
|---|---|---|
| Bộ lọc ngày | Kiểu ngày `dd/mm/yyyy`, **bắt buộc, mặc định hôm nay**. Đổi ngày thì tải lại các buổi của ngày đó | SRS 9.1.2, GBR-01 |
| Buổi trong ngày | Giờ, khách (gói đôi: 2 khách), trạng thái (Scheduled / Completed / Missed / Cancelled) | DBR-37 |
| Buổi cần điểm danh | Buổi `Scheduled` đã tới giờ bắt đầu và **còn trong cửa sổ điểm danh**: từ lúc bắt đầu đến **24 giờ sau khi kết thúc** (P-07) | BR-54, P-07 |
| Khách của tôi | **Chỉ khách được giao cho PT đang đăng nhập** | SRS 9.1.2, GBR-10 |
| Nhãn "Chưa có gói tập" | Buổi của khách không có Membership ngày đó được **tô nổi bật** để PT nhắc khách gia hạn | CBR-66 |

### 2.2 Luật liên quan

- Buổi được sinh **tự động khi đơn gói PT Paid**, PT **không cần xác nhận** và **không duyệt/từ chối từng buổi** (CBR-50, BR-57, UC-32).
- Buổi ở trạng thái cuối (Completed / Missed / Cancelled) **không ai sửa được** (DBR-38).
- Hết cửa sổ điểm danh mà buổi vẫn `Scheduled` thì **JOB-04 tự đóng**, xét theo thứ tự (CBR-42, CBR-64):
  1. Gói PT + khách không có Membership ngày đó (gói đôi: cả hai không có) → **Missed** (lý do: không có gói tập)
  2. Khách có check-in hôm đó (gói đôi: ít nhất 1 người) → **Completed**
  3. Còn lại → **Missed** (lý do: không check-in)
- Buổi Missed **vẫn tính hoa hồng** cho PT (CBR-80).
- Ghi chú riêng về khách: **chỉ PT viết ra mới xem được**, kể cả khi khách đã chuyển sang PT khác (CBR-92).
- Sau khi dời lịch, chuyển PT hoặc hủy, hệ thống báo cho khách ngay (gói đôi: cả 2 người). Nếu chuyển PT thì báo cả PT mới (CBR-46).
- Màn này **phải dùng được trên điện thoại** (GBR-35).

---

## 3. Manager: Operational & Revenue Report (`/manager/dashboard`, UC-46)

### 3.1 Bộ lọc

| Trường | Rule |
|---|---|
| Từ ngày / Đến ngày | Cặp ngày, **cả hai bắt buộc**, **không giới hạn khoảng cách** giữa 2 ngày (SRS 12.1.1, UC-46). Ngày cuối được tính trọn cả ngày (GBR-03). **Mặc định: ngày 1 tháng hiện tại → hôm nay [CHỐT-D6]**. **Khoảng ngược (Từ > Đến) bị từ chối, cả form lẫn server [CHỐT-D7]** |

### 3.2 Số liệu phải có

| Chỉ số | Cách tính | Rule |
|---|---|---|
| **Tổng doanh thu** | Tổng giao dịch **Success** của các đơn **không bị Cancelled**, **đã gồm VAT**. Hoàn tiền ngoài hệ thống **không trừ** | DBR-56, GBR-02, GBR-16 |
| Doanh thu **theo từng gói** | Tách riêng phần VAT: VAT = Số phải trả × VAT% ÷ (100 + VAT%), lấy số **đã lưu trên đơn** (`Orders`), không tính lại theo VAT hiện tại | DBR-57, GBR-02, P-10 |
| Doanh thu **vé ngày** + **số lượt vé ngày** | Như trên, lọc loại DayPass | SRS 12.1.1 |
| **Số lượt check-in** | Đếm `CheckIns` trong khoảng ngày | SRS 12.1.1 |
| **Hoa hồng theo từng PT** | Tổng `Sessions.CommissionAmount` (= tỷ lệ hoa hồng × giá trị buổi, đã chốt trên từng buổi). Tính cho buổi **Completed, Missed, PT hủy, khách hủy** | DBR-58, DBR-59, CBR-80, CBR-81, CBR-82 |
| — Giá trị 1 buổi | (Giá gói lúc mua − giảm giá) ÷ số buổi **thực sinh** của đơn | DBR-59, BR-40 |
| — Buổi bị Missed do bảo lưu | **Vẫn tính** hoa hồng | CBR-68 |
| — **Không tính** hoa hồng | Buổi Cancelled do **phòng đóng cửa** (CBR-57) và do **gói PT hết hạn** (CBR-55) | suy ra từ CBR-80…82 |
| — Chuyển PT | Hoa hồng tính cho **PT đang giữ buổi** (`Sessions.PtEmployeeId`) | schema, JOB-04 |

Gợi ý lọc buổi được tính hoa hồng theo schema hiện có:

```sql
Status IN ('Completed','Missed')
OR (Status = 'Cancelled' AND ResolutionType IN ('Cancel','CustomerRequest'))
```

### 3.3 Luật hiển thị

- **[CHỐT-D4] Lọc theo ngày nào:** doanh thu theo thời điểm giao dịch **Success**; hoa hồng theo **ngày diễn ra buổi**; check-in theo `CheckIns.CheckedInAt`.

- Số liệu là **ảnh chụp tại thời điểm tạo báo cáo**, không cập nhật trực tiếp (SRS 12.1.1, UC-46).
- **Không hiện số liệu audit** (CBR-103, ma trận quyền).
- Giá vốn hàng hóa **ngoài phạm vi** hệ thống (CBR-79).
- Tiền: số nguyên VND, căn phải, có dấu phân cách nghìn và "đ". Tổng tiền ghi "gồm VAT x%" (GBR-29, GBR-02).
- Không có dữ liệu trong khoảng đã chọn → hiện thông báo ngắn (GBR-21).
- **Xuất CSV** đúng những số đang hiện trên màn, theo đúng bộ lọc (SRS 12.1.1, GBR-33).

### 3.4 Quyền

- Chỉ **Manager** vào màn này. Admin xem cùng số liệu ở màn riêng của mình (mục 4). PT, Lễ tân, khách không xem được.

---

## 4. Admin: System Operational Report (`/admin/dashboard`, UC-47)

### 4.1 Bộ lọc

| Trường | Rule |
|---|---|
| Từ ngày / Đến ngày | Cả hai bắt buộc. **Khoảng ngược (Từ > Đến) bị từ chối**. Không có dữ liệu thì hiện rõ trên màn (SRS 12.1.2, GBR-21). **Mặc định: ngày 1 tháng hiện tại → hôm nay [CHỐT-D6]** |

### 4.2 Số liệu

- **Toàn bộ số liệu của báo cáo Manager** (mục 3.2), cùng cách tính.
- **Cộng thêm số liệu audit (chỉ Admin thấy)** (CBR-103, GBR-08):

| Chỉ số audit | Lấy từ |
|---|---|
| Hoạt động đăng nhập | `AuditLogs` với `Action` = `SignIn` / `SignInFailed` / `AccountLocked` **[CHỐT-D5]** |
| Tài khoản bị khóa | Tài khoản khóa tạm do sai mật khẩu (BR-01, P-17/P-18) + tài khoản Admin khóa (CBR-96) |
| Thao tác nhạy cảm **theo loại** và **theo người thực hiện** | `AuditLogs` nhóm theo `Action` và `AccountId` (`NULL` = hệ thống/job) |
| Thay đổi tài khoản | `AuditLogs` với `EntityName = 'Account'` |

### 4.3 Luật hiển thị

- Giống mục 3.3: ảnh chụp tại thời điểm tạo, hiển thị tiền, CSV theo bộ lọc, trạng thái rỗng.
- Log audit **chỉ đọc**, không sửa không xóa (ma trận quyền AuditLog).

---

## 5. Luật chung áp cho cả 4 dashboard

| Mã | Nội dung |
|---|---|
| GBR-01 | Tiếng Việt, ngày `dd/mm/yyyy`, giờ `HH:mm`, múi giờ UTC+7. Trạng thái lưu tiếng Anh, **hiển thị tiếng Việt** |
| GBR-03 | Ngày kết thúc (gói, bảo lưu, kỳ báo cáo) là **ngày cuối còn hiệu lực** |
| GBR-10 | Chỉ thấy dữ liệu vai trò mình được phép. Kiểm tra ở filter + service, không chỉ ẩn nút |
| GBR-13 | Mọi luật kiểm lại ở server (VD: bộ lọc ngày ngược vẫn phải chặn ở server) |
| GBR-14 | Trạng thái (hết hạn gói, hết bảo lưu, tự đóng buổi) do **job** cập nhật (JOB-01…04). Dashboard **chỉ đọc trạng thái trong DB**, không tự tính lại trạng thái |
| GBR-21 | Trạng thái rỗng có thông báo ngắn |
| GBR-28 | Màu trạng thái thống nhất: xanh = hoạt động / thành công, vàng = chờ / đang giữ, đỏ = hủy / thất bại, xám = hết hạn / ngừng |
| GBR-29 | Tiền căn phải, phân cách nghìn, "đ" |
| GBR-31 | Hết phiên đăng nhập → đăng nhập lại rồi **quay về đúng dashboard** |
| GBR-33 | Xuất file đúng theo bộ lọc hiện tại |
| GBR-35 | Màn của PT phải dùng tốt trên điện thoại |

Tham số liên quan (bảng `SystemConfigs`): **P-07** (cửa sổ điểm danh 24 giờ), **P-10** (VAT), **P-11** (tỷ lệ hoa hồng mặc định 70%).

---

## 6. Các điểm đã chốt (30/09/2026)

| Mã | Vấn đề | Đã chốt |
|---|---|---|
| **D1** | Banner cảnh báo của khách | Có 5 banner (bảng dưới). Mốc "sắp hết hạn" là tham số mới **P-21 = 7 ngày**, Manager chỉnh được |
| **D2** | Số buổi PT còn lại | = số buổi `Scheduled` của gói. Hiện kèm "Đã dùng X / Tổng Y" (Y = `GeneratedSessionCount`). Gói đôi: 2 người thấy cùng số |
| **D3** | Buổi sắp tới | 5 buổi `Scheduled` gần nhất từ bây giờ + link "Xem tất cả" sang Lịch tập của tôi |
| **D4** | Báo cáo lọc theo ngày nào | Doanh thu: thời điểm giao dịch Success. Hoa hồng: ngày diễn ra buổi. Check-in: `CheckedInAt` |
| **D5** | Log đăng nhập cho Admin | Ghi `AuditLogs` mỗi lần: `SignIn`, `SignInFailed`, `AccountLocked`. Không đổi schema. **Người làm Sign in (Nhàn) làm luôn** |
| **D6** | Khoảng ngày mặc định của báo cáo | Ngày 1 tháng hiện tại → hôm nay |
| **D7** | Chặn khoảng ngày ngược | Áp cho cả Manager và Admin, chặn ở form và server |
| **D8** | Tên UC-46, UC-47 lệch | Lấy tên theo SRS mục 12.1, sửa lại bảng UC |
| **D9** | Mã rule lệch (CBR-82 dẫn BR-78/BR-79) | Lấy theo bộ rule v24, người giữ SRS rà lại mã dẫn chéo |
| **D10** | 1 người vừa là khách vừa là nhân viên | Đăng nhập xong hiện **màn chọn vai trò** (Nhân viên / Khách hàng) rồi mới vào dashboard. Đổi vai trò = đăng xuất rồi đăng nhập lại. Không đổi schema |

**Chi tiết D1: 5 banner của khách**

| Khi nào | Nội dung | Màu (GBR-28) | Nút |
|---|---|---|---|
| Hồ sơ Frozen | "Gói tập đang bảo lưu đến dd/mm/yyyy" | Vàng | — |
| Hồ sơ Expired | "Gói tập đã hết hạn" | Đỏ | Mua gói |
| Gói tập còn ≤ P-21 ngày (mặc định 7) | "Gói tập hết hạn sau X ngày (dd/mm/yyyy)" | Vàng | Gia hạn |
| Có buổi PT mang nhãn "Chưa có gói tập" | "Có X buổi PT rơi vào ngày không có gói tập" | Đỏ | Gia hạn |
| Có đơn Pending chờ thanh toán | "Đơn #… hết hạn thanh toán sau mm:ss" (GBR-27) | Vàng | Thanh toán |

Không hiện banner "sắp hết hạn" nếu khách **đã mua gói nối tiếp** (DBR-52). Nhiều banner cùng lúc thì xếp theo thứ tự: đỏ trước, vàng sau.

**Việc phát sinh sau khi chốt**

| Việc | Ai |
|---|---|
| Thêm **P-21** vào `SystemConfigs`: 1 file migration mới + dòng seed + cập nhật bảng tham số | Leader |
| Ghi log đăng nhập (D5) | Nhàn (Sign in) |
| Màn chọn vai trò sau đăng nhập (D10) | Nhàn (Sign in) |
| Sửa SRS: bảng UC (D8), mã dẫn chéo (D9), thêm P-21, ghi rõ D1–D7 vào mục 9.1 và 12.1 | Người giữ SRS |
