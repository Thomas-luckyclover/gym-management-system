/**
 * Controller (Servlet) — chia package con theo VAI TRÒ / KHU VỰC URL, khớp với Filter phân quyền:
 * auth, guest, customer, pt, receptionist, staff (PT + Lễ tân dùng chung), manager, admin, payment.
 *
 * <p>Servlet chỉ điều phối: đọc tham số (RequestUtil) → gọi Service → forward JSP / redirect / trả JSON.
 * Không SQL, không nghiệp vụ, không try-catch riêng (Service đã xử lý).
 * Sau POST thành công luôn redirect (Post/Redirect/Get) kèm RequestUtil.setFlashToast(...) để F5 không gửi lại form.</p>
 */
package com.gms.servlet;
