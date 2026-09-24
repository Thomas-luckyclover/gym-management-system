/**
 * Điểm nhận kết quả từ VNPay. Công khai (AuthFilter cho qua) vì VNPay gọi server-to-server và phiên của khách có thể đã hết hạn khi quay về.
 *
 * <p>Servlet dự kiến (nguồn: SRS 5.1 Screen List — bảng đầy đủ ở docs/project-structure.md):</p>
 * <pre>
 * VnPayIpnServlet                  /api/v1/commerce/payment/webhook/vnpay               #-   VNPay IPN webhook (API-02)         TV1
 * VnPayReturnServlet               /payment/vnpay-return                                #-   Trang kết quả sau VNPay            TV1
 * </pre>
 *
 * <p>Mỗi Servlet: @WebServlet ngay trên class (cấm web.xml), doGet hiển thị / doPost xử lý,
 * chỉ điều phối — gọi Service, forward JSP bằng RequestUtil.forward hoặc trả JSON bằng JsonUtil.writeJson.</p>
 */
package com.gms.servlet.payment;
