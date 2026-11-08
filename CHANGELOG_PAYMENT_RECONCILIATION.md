# TỔNG HỢP CHI TIẾT CÁC THAY ĐỔI TRONG HỆ THỐNG (CHANGELOG)

Tài liệu này tổng hợp toàn bộ các thay đổi mã nguồn, sửa lỗi nghiệp vụ thanh toán (CS-16 đến CS-19), tối ưu hóa luồng đối soát VNPay và xử lý QueryDR.

---

## 1. Tối ưu hóa Luồng Đối soát VNPay & Thay thế QueryDR Giả Lập

### 1.1. Bối cảnh & Lý do thay đổi
* **Thực trạng:** Tài khoản VNPay Sandbox (`PFUP9RS2`) chưa được VNPay mở quyền gọi WebAPI QueryDR (`merchant_webapi/api/transaction`) và yêu cầu Whitelist IP tĩnh.
* **Vấn đề cũ:** Khi không gọi được VNPay, mã nguồn cũ tự động "bịa" ra dữ liệu giả (mock data) `"Thành công (Đối soát hệ thống)"` và số tiền giả định. Điều này gây sai lệch thực tế khi đối soát dòng tiền và tiềm ẩn rủi ro hoàn tiền khống.
* **Giải pháp chuẩn hóa:**
  1. Xóa bỏ hoàn toàn mã giả lập trong hệ thống.
  2. Tắt gọi ngầm QueryDR bằng cấu hình an toàn.
  3. Bổ sung các liên kết trực tiếp mở cổng **VNPay Merchant Portal Sandbox** (`https://sandbox.vnpayment.vn/merchantv2/`) để Quản lý (Manager) tra cứu dữ liệu thật 100%.

### 1.2. Các tệp đã thay đổi

#### a. `src/main/resources/application.properties`
* Cấu hình cờ:
  ```properties
  vnpay.query-enabled=false
  ```
* **Mục đích:** Vô hiệu hóa việc gọi ngầm QueryDR tự động, giải phóng tài nguyên và triệt tiêu toàn bộ lỗi `[RESULT] Chưa hỏi được VNPay` trong Console log.

#### b. `src/main/java/com/gms/service/ManagerTransactionService.java`
* **Xóa bỏ:** Đoạn code gán dữ liệu giả lập `Thành công (Đối soát hệ thống)` khi Sandbox trả về lỗi hoặc timeout.
* **Thay thế:** Báo lỗi rõ ràng và có định hướng:
  ```java
  return ServiceResult.fail("Cổng API VNPay chưa mở tra cứu cho tài khoản Sandbox này. Vui lòng mở cổng VNPay Merchant Portal để tra cứu.");
  ```

#### c. `src/main/webapp/WEB-INF/jsp/manager/transaction-list.jsp`
* **Popup Hoàn tiền (`#refundConfirmModal`):**
  * Xóa bỏ dòng trạng thái giả `"VNPay thực thu: Chưa tra cứu"` và nút `"Tra cứu"` gọi API nội bộ.
  * Thay thế bằng mục **"Đối soát cổng VNPay"** với nút liên kết:
    ```html
    <a href="https://sandbox.vnpayment.vn/merchantv2/" target="_blank" rel="noopener noreferrer" 
       class="btn btn-outline btn-sm" title="Mở cổng VNPay Merchant Portal để kiểm tra thực tế">
        <i class="fa-solid fa-arrow-up-right-from-square"></i> Mở VNPay Merchant Portal
    </a>
    ```
  * Cập nhật thông báo hướng dẫn: *"Mặc định là số tiền đơn. Đối soát trên VNPay để xác nhận số tiền thực tế trước khi hoàn."*

#### d. `src/main/webapp/js/pages/manager/transaction-list.js`
* **Bảng lần thử giao dịch (Attempts):**
  * Thay nút `"Tra cứu"` cũ bằng nút liên kết trực tiếp:
    ```javascript
    <a href="https://sandbox.vnpayment.vn/merchantv2/" target="_blank" rel="noopener noreferrer" 
       class="btn btn-outline btn-sm" title="Mở cổng VNPay Merchant Portal để tra cứu">
        <i class="fa-solid fa-arrow-up-right-from-square"></i> Tra cứu trên VNPay
    </a>
    ```
  * Cập nhật chú thích chi tiết `txdNote`: Bấm nút mở cổng VNPay để kiểm tra số thực thu.

---

## 2. Sửa lỗi Nghiệp vụ Ca làm việc Lễ tân (CS-16 & CS-19)

### 2.1. Chi tiết lỗi & Giải pháp xử lý

| Test Case | Vấn đề ban đầu | Nguyên nhân mã nguồn | Giải pháp đã khắc phục |
| :--- | :--- | :--- | :--- |
| **CS-16** | Xác nhận chuyển khoản tại quầy không cấp thẻ tập | Hàm `confirmBankTransfer()` chỉ `UPDATE Orders` và `Transactions` nhưng **quên `INSERT INTO Subscriptions`**. | Bổ sung câu lệnh tự động tạo thẻ tập trong bảng `Subscriptions` và kích hoạt khách hàng sang `Active`. |
| **CS-19** | Hai Lễ tân cùng bấm xác nhận bị văng lỗi hệ thống (MSG04) | Cập nhật Transaction trước, khi người thứ 2 bấm bị vi phạm ràng buộc Unique Index `UX_Transactions_OneSuccessPerOrder`. | Đổi thứ tự: Cập nhật `Orders` trước. Nếu đơn không còn `Pending` thì dừng ngay và trả về **MSG269**. |

### 2.2. Mã nguồn đã cập nhật: `src/main/java/com/gms/dao/ShiftDAO.java`
Cập nhật lại toàn bộ hàm `confirmBankTransfer`:
* Khóa trạng thái `Orders` từ `Pending` sang `Paid` trước để chặn xung đột đồng thời.
* Cập nhật / tạo `Transactions` trạng thái `Success`.
* Tự động `INSERT INTO Subscriptions` cho hội viên và kích hoạt trạng thái khách hàng sang `Active`.

---

## 3. Nhật ký Kiểm toán Hoàn tiền (Audit Logs Verification)

Đã xác nhận quy trình ghi vết hoàn tiền ngoài hệ thống (BR-47, CBR-72):
1. **Bảng `Transactions`:** Lưu vết `RefundedAt` (thời điểm), `RefundedByEmployeeId` (Manager thực hiện) và `RefundNote` (lý do đối soát từ 5 đến 480 ký tự).
2. **Bảng `AuditLogs`:** Tự động ghi nhật ký bảo mật với hành động *"Xác nhận hoàn tiền ngoài hệ thống"*, gắn mã giao dịch và ghi chú chi tiết.
3. **Báo cáo & Đối soát:** Hỗ trợ xem trực tiếp trên Popup giao diện Manager và xuất đầy đủ 3 cột đối soát ra file Excel (`.xlsx`).

---

## 4. Tóm tắt Trạng thái Mã nguồn & Biên dịch

* **Công cụ biên dịch:** JDK 17 (Target bytecode version 61.0 tương thích 100% với môi trường chạy).
* **Kết quả build:** `0 errors`, toàn bộ class backend hoạt động đồng bộ.
* **Cam kết Git:** Đã commit vào nhánh làm việc với message `add vnpay dashboard` (commit `fa1490a`).

