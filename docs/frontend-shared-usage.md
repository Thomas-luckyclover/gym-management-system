# Dùng tokens.css · common.css · common.js

## 1. Khung 1 trang JSP

```jsp
<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="gms" uri="/WEB-INF/gms-functions.tld" %>
<!DOCTYPE html>
<html lang="vi">
<head>
  <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
  <title>Đặt buổi lẻ · Super Gym</title>
  <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/customer/book-session.css">
  <script src="${pageContext.request.contextPath}/js/pages/customer/book-session.js" defer></script>
</head>
<%-- Theme theo khu: theme-guest | theme-guest-dark | theme-member | theme-staff | theme-manager | theme-admin --%>
<body class="theme-member">
  <%@ include file="/WEB-INF/jsp/common/flash-toast.jspf" %>
  ...
</body>
</html>
```

## 2. Chuyển CSS prototype sang token

| Prototype | Token mới | | Prototype | Token mới |
|---|---|---|---|---|
| `var(--primary)` | `var(--color-primary)` | | `var(--bg)` | `var(--color-bg)` |
| `var(--primary-hover)` | `var(--color-primary-hover)` | | `var(--card)`, `#fff` | `var(--color-surface)` |
| `var(--primary-tint)` | `var(--color-primary-tint)` | | `var(--text)` | `var(--color-text)` |
| `var(--primary-border)` | `var(--color-primary-border)` | | `var(--text-soft)` / `--text2` | `var(--color-text-soft)` |
| `var(--success/-tint/-border)` | `var(--color-success…)` | | `#8F8B81` | `var(--color-text-muted)` |
| `var(--ink)` `#0D0D0D` | `var(--color-surface-inverse)` | | `#C9C5BC` (chữ trên nền đen) | `var(--color-text-inverse-soft)` |
| `var(--lime)` | `var(--color-lime)` | | `var(--border)` | `var(--color-border)` |
| `--role` (Manager) / `--admin` | `var(--color-primary)` + `theme-manager` / `theme-admin` | | `#D4D1C8` | `var(--color-border-strong)` |
| `--radius-sm` / `--r-sm` | `var(--radius-sm)` | | `--radius-md` / `--r-md` | `var(--radius-md)` (đã giảm 19%) |
| `--radius-lg` / `--r-lg`, `16px` | `var(--radius-lg)` (đã giảm 19%) | | `999px`, `99px` | `var(--radius-pill)` |
| `4px 8px 12px 16px 24px 32px 48px 64px` | `--space-1 2 3 4 5 6 8 10` | | `6px 10px 14px 20px` | `--space-1-5 2-5 3-5 4-5` |
| `.7rem` `.74rem` | `var(--font-size-2xs)` / `xs` | | `.8rem`–`.84rem` | `var(--font-size-sm)` |
| `.85rem`–`.88rem`, `14px` | `var(--font-size-md)` | | `15px`, `.92`–`.98rem` | `var(--font-size-base)` |
| `.15s` `.12s` | `var(--duration-base)` / `fast` | | `z-index:300` | `var(--z-modal)` |

Còn giá trị nào chưa có token → báo Leader thêm vào `tokens.css`, **không** tự hard-code.

## 3. Form gửi thường (POST về Servlet) — không cần viết JS

```jsp
<form action="${pageContext.request.contextPath}/receptionist/customers/form" method="post" data-validate data-unsaved-warning>
  <div class="field">
    <label class="field-label" for="fullName">Họ tên *</label>
    <input id="fullName" name="fullName" required maxlength="50" data-rule="fullName"
           value="<c:out value='${form.fullName}'/>">
    <%-- Lỗi server trả về (khi forward lại form) — cùng id "<idÔ>Error" với lỗi do JS hiện --%>
    <div class="field-error" id="fullNameError" ${empty errors.fullName ? 'hidden' : ''}><c:out value="${errors.fullName}"/></div>
  </div>
  <div class="field">
    <label class="field-label" for="phone">Số điện thoại *</label>
    <input id="phone" name="phone" required data-label="Số điện thoại" data-rule="phone">
  </div>
  <button class="btn btn-primary" type="submit">Lưu</button>
  <button class="btn btn-danger" type="submit" name="action" value="requestDelete"
          data-confirm-title="Yêu cầu xóa hồ sơ" data-confirm-message="Gửi yêu cầu xóa hồ sơ KH-0012?"
          data-confirm-reason-name="deleteReason">Yêu cầu xóa</button>
</form>
```
common.js tự: kiểm tra khi rời ô + khi bấm Lưu (lỗi tiếng Việt dưới từng ô), khóa nút khi đang gửi, hỏi xác nhận + bắt nhập lý do,
cảnh báo khi rời trang chưa lưu. Servlet đọc lý do bằng `request.getParameter("deleteReason")`.

`data-rule`: `phone` · `email` · `password` · `fullName` · `verificationCode` · `reason` · `positiveInteger` · `dateOfBirth`.
Ô xác nhận mật khẩu: `data-match="password"`. Ô ngày chỉ chọn từ ngày mai: `<input type="date" min="2026-09-25">`.

## 4. Form / thao tác AJAX

```js
// ===== Xử lý áp mã voucher =====

// Gửi mã voucher lên server, nhận về tổng tiền mới
// Input: form #voucherForm (ô voucherCode)
// Output: cập nhật tổng tiền trên trang nếu áp mã thành công
async function handleApplyVoucher(event) {
    event.preventDefault();
    const voucherForm = document.getElementById("voucherForm");
    const result = await submitFormAjax(voucherForm);
    if (!result.success) {
        return;
    }
    const grandTotal = document.getElementById("grandTotal");
    grandTotal.textContent = formatMoney(result.data.amountPayable);
}

document.getElementById("voucherForm").addEventListener("submit", handleApplyVoucher);
```
`submitFormAjax` đã lo: kiểm tra dữ liệu, khóa nút, lỗi mạng/hết phiên/không quyền/lỗi server, hiện lỗi dưới ô, toast.
Form AJAX **không** gắn `data-validate` (để không kiểm tra 2 lần). Servlet trả: `JsonUtil.writeJson(response, JsonResponse.from(result))`.

## 5. Danh sách phân trang ở server

```jsp
<form method="get">
  <input name="keyword" value="<c:out value='${param.keyword}'/>" placeholder="Tìm theo tên, SĐT">
  <select name="status" data-auto-submit> ... </select>
  <button class="btn btn-outline" type="submit">Lọc</button>
  <button class="btn btn-ghost" type="button" data-clear-filters>Xóa bộ lọc</button>
</form>

<c:forEach var="customer" items="${page.items}" varStatus="loop">
  <tr>
    <td class="tabular">${page.startIndex + loop.index + 1}</td>
    <td><c:out value="${customer.fullName}"/></td>
    <td class="money"><c:out value="${gms:formatMoney(customer.totalPaid)}"/></td>
    <td><span class="badge badge-success">Đang hoạt động</span></td>
  </tr>
</c:forEach>

<select data-page-size-select>
  <option ${page.pageSize == 10 ? 'selected' : ''}>10</option>
  <option ${page.pageSize == 20 ? 'selected' : ''}>20</option>
  <option ${page.pageSize == 50 ? 'selected' : ''}>50</option>
</select>
```

## 6. Thành phần khác

| Cần | Dùng |
|---|---|
| Toast sau khi lưu + redirect | Java: `RequestUtil.setFlashToast(request, "success", Messages.MSG23_SAVED)` |
| Toast từ JS | `showToast("success" \| "info" \| "warning" \| "error", "…")` |
| Hộp xác nhận tùy biến | `openConfirmDialog({ title, message, confirmText, isDanger, requireReason, onConfirm })` |
| Đếm ngược mm:ss | `<b data-countdown-seconds="${secondsLeft}" data-countdown-reload="true">20:00</b>` |
| Nút con mắt mật khẩu | `<button type="button" class="password-toggle" data-password-toggle="password" aria-label="Hiện mật khẩu"><i class="fa-solid fa-eye"></i></button>` |
| Tab | `[role="tablist"] > button[data-tab="x"]` + `[data-tab-panel="x"]` |
| Mở/đóng menu | `<button data-toggle-target="mobileNav" aria-expanded="false">` |
| Badge trạng thái (GBR-28) | `badge-success` hoạt động/thành công · `badge-warning` chờ · `badge-danger` hủy/lỗi · `badge-neutral` ngừng/hết hạn |
| Chèn dữ liệu AJAX vào HTML | ưu tiên `element.textContent = …`; bắt buộc dùng innerHTML thì bọc `escapeHtml(…)` |

## 7. Gợi ý animation (chỉ đề xuất — chưa code, làm khi nhóm muốn)
- Sidebar hội viên/nhân viên trượt ra: đã có token `--duration-slower` + `--ease-standard`.
- Hàng bảng mới thêm bằng AJAX: nền `--color-primary-tint` nhạt dần trong ~1 giây để mắt thấy dòng vừa thêm.
- Slot lịch PT được chọn: phóng nhẹ `scale(1.02)` khi hover.
- Skeleton loading cho dashboard thay vì màn trắng khi đang tải số liệu.
