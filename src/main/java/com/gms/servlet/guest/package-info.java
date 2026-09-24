/**
 * Trang công khai cho khách chưa đăng nhập (Guest): trang chủ, blog. Không qua Filter phân quyền.
 *
 * <p>Servlet dự kiến (nguồn: SRS 5.1 Screen List — bảng đầy đủ ở docs/project-structure.md):</p>
 * <pre>
 * HomeServlet                      "" (trang gốc)                                       #5   Home                               TV2
 * BlogListServlet                  /blog                                                #6   Blog List                          TV2
 * BlogDetailServlet                /blog/detail                                         #7   Blog Details                       TV2
 * </pre>
 *
 * <p>Mỗi Servlet: @WebServlet ngay trên class (cấm web.xml), doGet hiển thị / doPost xử lý,
 * chỉ điều phối — gọi Service, forward JSP bằng RequestUtil.forward hoặc trả JSON bằng JsonUtil.writeJson.</p>
 */
package com.gms.servlet.guest;
