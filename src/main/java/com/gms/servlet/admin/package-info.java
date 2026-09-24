/**
 * Khu vực Quản trị viên — URL /admin/*, được AdminAreaFilter bảo vệ.
 *
 * <p>Servlet dự kiến (nguồn: SRS 5.1 Screen List — bảng đầy đủ ở docs/project-structure.md):</p>
 * <pre>
 * AdminDashboardServlet            /admin/dashboard                                     #67  System Operational Report          TV2
 * UserListServlet                  /admin/users                                         #8   User List                          TV2
 * UserCreateServlet                /admin/users/create                                  #9   Add User Account                   TV2
 * UserDetailServlet                /admin/users/detail                                  #10  User Details                       TV2
 * LockedCustomerListServlet        /admin/locked-customers                              #11  Temporarily Locked Customers       TV2
 * LockedCustomerDetailServlet      /admin/locked-customers/detail                       #12  Unlock Customer Account            TV2
 * AuditLogServlet                  /admin/audit-log                                     #13  Audit Log                          TV2
 * </pre>
 *
 * <p>Mỗi Servlet: @WebServlet ngay trên class (cấm web.xml), doGet hiển thị / doPost xử lý,
 * chỉ điều phối — gọi Service, forward JSP bằng RequestUtil.forward hoặc trả JSON bằng JsonUtil.writeJson.</p>
 */
package com.gms.servlet.admin;
