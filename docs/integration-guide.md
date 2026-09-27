# Tích hợp bên thứ 3 — VNPay & Email (giải thích dễ hiểu)

Bản giải thích bắt buộc theo agents.md Phần 7 mục 6.4. Code nằm ở `src/main/java/com/gms/integration/`.

---

## 1. Email Gateway (SRS API-03) — `EmailGateway.java`

### Nó làm gì
Gửi email từ 1 địa chỉ Gmail của nhóm tới khách: mã xác thực 6 số (đăng ký, kích hoạt hồ sơ, quên mật khẩu, đổi email)
và thông báo cho email cũ khi đổi email. Hệ thống không gửi SMS.

### Luồng dữ liệu
```
Service (VD RegistrationService)
   │ 1. sinh mã: SecureCodeUtil.generateVerificationCode()
   │ 2. gọi emailGateway.sendVerificationCode(email, mã, P-15 phút, "đăng ký tài khoản")
   ▼
EmailGateway ──SMTP 587 + STARTTLS──► smtp.gmail.com ──► hộp thư khách
   │ gửi lỗi → ném EmailSendException
   ▼
Service bắt lỗi → KHÔNG tạo tài khoản, trả ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY)
```

### Các trường hợp lỗi
| Tình huống | Hệ thống xử lý |
|---|---|
| Sai App Password / chưa bật | `EmailSendException`, log `AuthenticationFailedException` trong console Tomcat |
| Mất mạng, Gmail không phản hồi | Chờ tối đa 10 giây rồi báo lỗi, không treo trang |
| Email người nhận sai định dạng | Service đã chặn trước bằng `ValidationUtil.isValidEmail` |

### ⚠️ CẦN CHUẨN BỊ (chặn việc test gửi mail thật)
1. **Thiếu gì:** 1 tài khoản Gmail riêng của nhóm + **App Password** 16 ký tự.
2. **Vì sao cần:** Gmail không cho đăng nhập SMTP bằng mật khẩu thường; thiếu thì không gửi được mã → không đăng ký/quên mật khẩu được.
3. **Lấy ở đâu:** tạo Gmail mới → bật **Xác minh 2 bước** (Google Account → Bảo mật) → vào `myaccount.google.com/apppasswords`
   → tạo mật khẩu ứng dụng tên "GMS" → copy 16 ký tự (bỏ khoảng trắng) vào `mail.app-password` trong `application.properties`.
4. **Mức độ gấp:** CAO — TV1 cần trước khi làm màn Đăng ký / Quên mật khẩu. Trong lúc chờ vẫn code được, chỉ chưa gửi thật.

---

## 2. Cổng thanh toán VNPay (SRS API-01, API-02) — `VnPayClient.java`

### Nó làm gì
Cho khách tự mua gói tập / đặt buổi PT lẻ online mà không cần ra quầy. GMS **không bao giờ** chạm vào số thẻ;
khách nhập thẻ/quét QR trên trang của VNPay.

### Luồng dữ liệu (4 bước)
```
① Khách bấm "Thanh toán" trên /customer/checkout
      PaymentService tạo Transaction (Pending, số tiền lấy từ đơn — không lấy từ trình duyệt)
      VnPayClient.buildPaymentUrl(...) → URL có chữ ký HMAC-SHA512
      Servlet redirect trình duyệt sang VNPay
② Khách thanh toán trên trang VNPay (sandbox: dùng thẻ test bên dưới)
③ VNPay gọi NGẦM về server GMS (IPN — server nói chuyện với server):
      /api/v1/commerce/payment/webhook/vnpay?vnp_Amount=...&vnp_SecureHash=...
      VnPayIpnServlet: verifySignature → so số tiền → PaymentService đánh dấu Transaction Success, Order Paid, cấp gói
      Trả lời VNPay: {"RspCode":"00","Message":"Confirm Success"}
      ★ ĐÂY LÀ NƠI DUY NHẤT ĐƯỢC ĐÁNH DẤU "ĐÃ THANH TOÁN"
④ VNPay chuyển trình duyệt khách về /payment/vnpay-return
      VnPayReturnServlet chỉ HIỂN THỊ kết quả (khách có thể tự sửa URL này nên không tin để cập nhật đơn)
```

### Chữ ký HMAC-SHA512 là gì
Hai bên (GMS và VNPay) cùng giữ 1 **khóa bí mật** (`hash-secret`). Trước khi gửi, bên gửi "trộn" toàn bộ tham số với khóa bí mật
thành 1 chuỗi 128 ký tự (chữ ký). Bên nhận tự tính lại; chỉ cần đổi 1 ký tự (VD sửa số tiền) là chữ ký không khớp → từ chối.
Đã test sẵn trong `VnPayClientTest` (ký → kiểm tra đúng; sửa số tiền → bị từ chối).

### Mã trả lời IPN (hằng số trong `VnPayClient`)
| RspCode | Khi nào | Hằng |
|---|---|---|
| 00 | Ghi nhận thành công (kể cả khi đơn đã hủy → vẫn ghi Transaction Success và đưa vào danh sách hoàn tiền thủ công — SRS 6.2) | `IPN_CONFIRM_SUCCESS` |
| 01 | Không tìm thấy giao dịch theo `vnp_TxnRef` | `IPN_ORDER_NOT_FOUND` |
| 02 | Giao dịch đã xử lý rồi (VNPay gọi lại lần 2) — không làm gì thêm | `IPN_ORDER_ALREADY_CONFIRMED` |
| 04 | Số tiền không khớp | `IPN_INVALID_AMOUNT` |
| 97 | Sai chữ ký | `IPN_INVALID_SIGNATURE` |
| 99 | Lỗi khác | `IPN_UNKNOWN_ERROR` |

### Các trường hợp lỗi SRS đã quy định
- Thanh toán lỗi / hết hạn link (5 phút) → Transaction Failed/Expired, **đơn vẫn Pending**, khách thanh toán lại với mã `TxnRef` mới cho tới hạn đơn P-09 (20 phút).
- Quá hạn đơn → JOB-05 hủy đơn, trả lượt voucher, nhả slot PT.
- VNPay báo thành công cho đơn đã hủy / lệch tiền / slot đã bị người khác lấy → vẫn ghi Transaction Success nhưng **không** chuyển đơn Paid, đưa vào danh sách hoàn tiền của Manager.

### ⚠️ CẦN CHUẨN BỊ (chặn việc test thanh toán thật)
1. **Thiếu gì:** tài khoản **VNPay Sandbox** → nhận `vnp_TmnCode` + `vnp_HashSecret`.
   - **Vì sao cần:** không có 2 giá trị này thì không tạo được link thanh toán hợp lệ.
   - **Lấy ở đâu:** đăng ký tại `https://sandbox.vnpayment.vn/devreg/`, VNPay gửi thông tin qua email → điền vào `vnpay.tmn-code`, `vnpay.hash-secret`.
   - **Mức độ gấp:** CAO — TV1 cần trước khi làm màn Checkout.
2. **Thiếu gì:** 1 **URL công khai** để VNPay gọi IPN về máy đang chạy Tomcat (localhost thì VNPay không gọi tới được).
   - **Vì sao cần:** bước ③ — không có IPN thì đơn không bao giờ chuyển Paid.
   - **Lấy ở đâu:** cài `ngrok` → chạy `ngrok http 8080` → lấy link `https://xxxx.ngrok-free.app` → khai báo IPN URL
     `https://xxxx.ngrok-free.app/gms/api/v1/commerce/payment/webhook/vnpay` trong trang quản trị merchant sandbox của VNPay
     (hoặc gửi cho hỗ trợ VNPay nếu trang không cho sửa).
   - **Mức độ gấp:** TRUNG BÌNH — chỉ cần khi test đầu-cuối; trong lúc chờ có thể test IPN bằng cách tự gọi URL có chữ ký tạo từ unit test.
3. **Thẻ test sandbox** (công khai trên tài liệu VNPay): ngân hàng NCB, số thẻ `9704198526191432198`,
   tên `NGUYEN VAN A`, ngày phát hành `07/15`, OTP `123456`. Nếu VNPay đổi, xem lại trang tài liệu sandbox.

---

## 3. An toàn chung
- `application.properties` chứa khóa bí mật → **không commit** (đã có trong `.gitignore`). Lộ khóa → vào VNPay/Google đổi ngay.
- Không in `hash-secret`, App Password, mã OTP ra log/console.
- Server luôn tự kiểm tra lại mọi thứ (GBR-13): số tiền lấy từ DB, không tin số tiền trình duyệt gửi lên.
