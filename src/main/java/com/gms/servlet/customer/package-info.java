/**
 * Khu vực Hội viên — URL /customer/*, được CustomerAreaFilter bảo vệ.
 *
 * <p>Servlet dự kiến (nguồn: SRS 5.1 Screen List — bảng đầy đủ ở docs/project-structure.md):</p>
 * <pre>
 * CustomerDashboardServlet         /customer/dashboard                                  #48  Customer Dashboard                 TV5
 * PackageSelectionServlet          /customer/packages                                   #35  Package Selection                  TV1
 * CheckoutServlet                  /customer/checkout                                   #36  Checkout / Payment                 TV1
 * TransactionHistoryServlet        /customer/transactions                               #40  Personal Transaction History       TV1
 * MyBookingServlet                 /customer/bookings                                   #50  My Bookings                        TV5
 * MyPtSessionServlet               /customer/pt-sessions                                #51  My PT Sessions                     TV5
 * BookSingleSessionServlet         /customer/book-session                               #52  Book Single Session                TV5
 * (#53 Swap Session đã bỏ 19/10 — khách không tự đổi buổi, xem docs/notes.md)
 * MyPlanServlet                    /customer/plans                                      #60  My Plan                            TV5
 * SubmitFeedbackServlet            /customer/feedback                                   #61  Submit Feedback                    TV2
 * </pre>
 *
 * <p>Mỗi Servlet: @WebServlet ngay trên class (cấm web.xml), doGet hiển thị / doPost xử lý,
 * chỉ điều phối — gọi Service, forward JSP bằng RequestUtil.forward hoặc trả JSON bằng JsonUtil.writeJson.</p>
 */
package com.gms.servlet.customer;
