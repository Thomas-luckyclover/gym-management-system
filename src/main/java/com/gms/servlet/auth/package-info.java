/**
 * Xác thực & tài khoản cá nhân — đăng nhập, đăng xuất, đăng ký, quên mật khẩu, cài đặt tài khoản (mọi vai trò).
 *
 * <p>Servlet dự kiến (nguồn: SRS 5.1 Screen List — bảng đầy đủ ở docs/project-structure.md):</p>
 * <pre>
 * LoginServlet                     /login                                               #1   Sign In                            TV1
 * LogoutServlet                    /logout                                              #-   Logout (POST)                      TV1
 * RegisterServlet                  /register                                            #2   Register Account                   TV1
 * ForgotPasswordServlet            /forgot-password                                     #3   Forgot Password                    TV1
 * SetPasswordServlet               /set-password                                        #-   Đặt mật khẩu qua link email        TV1
 * AccountSettingsServlet           /account                                             #4   Account Settings                   TV1
 * </pre>
 *
 * <p>Mỗi Servlet: @WebServlet ngay trên class (cấm web.xml), doGet hiển thị / doPost xử lý,
 * chỉ điều phối — gọi Service, forward JSP bằng RequestUtil.forward hoặc trả JSON bằng JsonUtil.writeJson.</p>
 */
package com.gms.servlet.auth;
