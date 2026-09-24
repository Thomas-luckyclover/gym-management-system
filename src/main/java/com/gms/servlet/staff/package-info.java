/**
 * Màn hình DÙNG CHUNG cho PT và Lễ tân. Một Servlet khai báo 2 URL (/pt/... và /receptionist/...) để mỗi URL vẫn được đúng Filter vai trò bảo vệ.
 *
 * <p>Servlet dự kiến (nguồn: SRS 5.1 Screen List — bảng đầy đủ ở docs/project-structure.md):</p>
 * <pre>
 * LeaveRequestFormServlet          /pt/leave-requests + /receptionist/leave-requests    #25  Leave Request Form                 TV3
 * </pre>
 *
 * <p>Mỗi Servlet: @WebServlet ngay trên class (cấm web.xml), doGet hiển thị / doPost xử lý,
 * chỉ điều phối — gọi Service, forward JSP bằng RequestUtil.forward hoặc trả JSON bằng JsonUtil.writeJson.</p>
 */
package com.gms.servlet.staff;
