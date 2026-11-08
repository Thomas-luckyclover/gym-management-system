# PROTOTYPE CHANGES LOG

## 📋 Màn có thay đổi Field/Component

### 1. Đăng nhập (`dang-nhap.html`) - **TẠO MỚI**
**Fields:**
- Phone number (10 chữ số, bắt đầu 0)
- Password (8-64 ký tự, phải có chữ và số)

**Components:**
- Login form với validation
- "Quên mật khẩu?" link
- MSG-005 banner: "Tài khoản chưa có mật khẩu" với CTA "Đặt mật khẩu ngay"
- Error states cho phone/password

---

### 2. Đăng ký (`dang-ky.html`)
**Fields thay đổi:**
- **Gộp vào 1 form**: Phone + Email + Password + Họ tên + Ngày sinh (thay vì tách phone resolution riêng)
- Phone validation: realtime check
- Password: 8-64 chars, alphanumeric required
- Ngày sinh: Thêm field-hint "Lưu ý: Thành viên dưới 10 tuổi không thể mua gói PT..."

**Components mới:**
- Link "Đã có tài khoản? Đăng nhập"
- Success screen: "Đăng nhập ngay" button thay vì "Về trang chủ"
- Blocked-account screen: Thêm "Đăng nhập" button

**Logic mới (dang-ky.js):**
- OTP throttling: 5 attempts/hour, 60s resend delay, 5-minute lock sau 5 lần
- Password validation: 8-64 characters

---

### 3. Quên mật khẩu (`quen-mat-khau.html`)
**Components thay đổi:**
- "Quay lại đăng ký" → "Quay lại đăng nhập"
- Success message: "Mật khẩu đã thay đổi. Mọi phiên đăng nhập khác đã bị đăng xuất để bảo mật."

**Logic mới (quen-mat-khau.js):**
- Reset code throttling
- Password validation: 8-64 chars, alphanumeric mix

---

### 4. Bán gói PT (`lt/ban-goi-pt.html`)
**Complete redesign:**

**Fields mới:**
- Date range navigation: "◄ Tuần trước" / "Tuần sau ►"
- "Về tuần đầu" button
- Dynamic week label: "14/09 – 20/09 · 3 buổi"
- Start date → Auto-calculate end date

**Components thay đổi:**
- **Calendar grid redesign**: 
  - Từ 5-week tab view → Infinite scroll weekly view
  - Grid layout: 7 columns (T2-CN) × time slots
  - Absolute-positioned slots với hover states
  - T7/CN: visible nhưng disabled (không chọn được)
  - T2-T6: Mỗi ngày chỉ chọn được 1 buổi
- **Bỏ requirement**: Không cần tick đủ N buổi, chỉ hiển thị "X buổi đã chọn"
- **Slot states**:
  - Ngoài package range: disabled + tooltip "Ngoài khoảng gói"
  - Weekend: disabled
  - Selected: highlighted

**Logic mới (ban-goi-pt.js):**
- `addMonths()` fix: Dùng `setMonth()` thay vì constructor (5/10 → 5/11 không phải 4/11)
- Dynamic grid rendering theo tuần hiện tại
- Selection tracking per day (max 1 slot/day T2-T6)

---

### 5. Tài khoản khách (`kh/tai-khoan.html`)
**Fields thay đổi:**
- Mã khách: SG-xxxx → **KH-xxxx**
- Ngày sinh: **readonly** + field-hint "Ngày sinh chỉ được cập nhật tại quầy Lễ tân"

**Validation mới (tai-khoan.js):**
- Chặn sửa DOB, báo lỗi MSG-012 nếu thay đổi

---

### 6. Thanh toán (`kh/thanh-toan.html`)
**Components mới:**
- Msg-banner info: "Link thanh toán có hiệu lực 5 phút. Quá thời gian này cần tạo đơn mới."
- Countdown: 15:00 → **20:00** (PAY_SECONDS = 1200)

---

### 7. Đặt buổi lẻ (`kh/dat-buoi-le.html`)
**Components mới:**
- Note-box: "Chỉ hiển thị các slot đã được PT publish. Slot chưa publish sẽ không xuất hiện trong lịch."

---

### 8. Đổi lịch (`kh/doi-lich.html`)
**Fix:**
- Xóa duplicate HTML code block (2 đoạn giống nhau)

---

### 9. Mua gói (`kh/mua-goi.html`)
**Components mới:**
- Note: "Gói ngày hết hạn lúc 23:59 ngày cuối cùng."

---

### 10. Check-in lễ tân (`lt/check-in.html`)
**Trạng thái thay đổi:**
- Pending-delete status: "Vào tập được — có lưu ý" → **"Từ chối vào tập. Hồ sơ đang chờ Quản lý duyệt xoá — không cho vào tập trong thời gian chờ duyệt."**

---

### 11. Hồ sơ khách (`lt/ho-so-khach.html`)
**Field-hints mới:**
- Ngày sinh: "Dùng để kiểm tra mốc 10 tuổi khi bán gói PT và validate tuổi."
- Email: "Nếu để trống, hệ thống sẽ tạo account không mật khẩu (khách phải đặt qua email sau)."

---

### 12. Điểm danh PT (`pt/diem-danh.html`)
**Components thay đổi:**
- Tooltip nút "Không dạy được": "Chỉ hiện trong cửa sổ 24h trước buổi tập. Buổi này chưa tới giờ."
- Mô tả cửa sổ điểm danh: "Chỉ điểm danh được trong cửa sổ 24 giờ trước buổi tập. Quá 24 giờ sau khi buổi kết thúc, hệ thống tự chốt theo lịch sử check-in của khách."

---

### 13. Nghỉ phép PT (`pt/nghi-phep.html`)
**Field-hint mới:**
- Ngày nghỉ: "Phải nộp trước 23:59 ngày hôm trước. Nộp muộn sẽ bị từ chối."

---

### 14. Trang chủ (`index.html`)
**Components đơn giản hóa:**
- **Gói PT**: 3 loại (1/3/6 tháng) thay vì nhiều combo
- **Membership benefits**: Rút gọn lời hứa, bỏ các feature không có trong luật (bảo lưu 7/15/30 ngày, buổi InBody, ưu tiên xếp lịch)

---

## 📊 Tổng kết

| Loại thay đổi | Số màn |
|---------------|--------|
| **Tạo mới hoàn toàn** | 1 (dang-nhap) |
| **Redesign major** | 1 (lt/ban-goi-pt) |
| **Thêm/đổi fields** | 6 |
| **Thêm components/hints** | 7 |
| **Fix bugs** | 1 (doi-lich duplicate) |
| **Total** | **14 màn** |

---

**Last Updated**: 2026-09-23 12:24 PM

---

## Bổ sung (phần mua gói, UC-20 / UC-26) - bám theo các màn thật

### Bán hàng tại quầy (`lt/ban-hang.html`) - dựng lại thành 3 bước như hệ thống
- Bước 1 / 3: Chọn gói cho khách. Bước 2 / 3: Chọn khách (tìm theo tên, SĐT, mã KH; "+ Tạo hồ sơ"). Bước 3 / 3: Xác nhận đơn hàng & Thu tiền.
- Tạo hồ sơ: chọn loại hồ sơ "Tự sở hữu" (có email, tài khoản và mật khẩu tạm) hoặc "Phụ thuộc" (người giám hộ: tên, quan hệ, SĐT); loại hồ sơ không đổi sau khi tạo.
- Ngày bắt đầu (CBR-119): Gói Ngày luôn bắt đầu hôm nay. Gói tập: "Kích hoạt ngay hôm nay" hoặc, nếu khách còn gói đang dùng, "Kích hoạt nối tiếp sau gói hiện tại"; hoặc "Hẹn ngày kích hoạt khác" trong 7 ngày kể từ ngày sớm nhất.
- Thanh toán: "Tiền mặt" hoặc "Chuyển khoản / Quẹt thẻ POS". Chuyển khoản có ô mã tham chiếu (không bắt buộc, tối đa 50 ký tự) và ô "Xác nhận thu tiền tại quầy" bắt buộc tick (CBR-77, MSG326).

### Phiếu thu dịch vụ (`lt/bien-nhan.html`) - **TẠO MỚI**
- Hiện sau khi xác nhận thu tiền. Trường: Mã đơn hàng, Thời gian thu, Lễ tân thu tiền, Hình thức, Hội viên, Mã KH / SĐT, Gói đăng ký, Hiệu lực gói (từ → đến), Giảm giá, TỔNG ĐÃ THU.
- Nút "Đổi ngày" cạnh Hiệu lực gói: chỉ gói tập, một lần, trong 7 ngày; ngày hết hạn tính lại theo thời hạn gói. Gói Ngày và buổi PT lẻ không đổi được.
- Nút: In phiếu thu, Bán đơn mới.

### Giao dịch toàn hệ thống (`Manager/gms_manager_mockup_v24.html`)
- Hộp "Xác nhận hoàn tiền" của đơn online: thêm liên kết mở VNPay Merchant Portal để đối soát (hệ thống không còn gọi QueryDR).
