/**
 * Khu vực Quản lý — URL /manager/*, được ManagerAreaFilter bảo vệ.
 *
 * <p>Servlet dự kiến (nguồn: SRS 5.1 Screen List — bảng đầy đủ ở docs/project-structure.md):</p>
 * <pre>
 * ManagerDashboardServlet          /manager/dashboard                                   #66  Operational & Revenue Report       TV3
 * ParameterListServlet             /manager/parameters                                  #14  Operating Parameters               TV3
 * ParameterEditServlet             /manager/parameters/edit                             #15  Edit Parameter                     TV3
 * OperatingHoursServlet            /manager/operating-hours                             #16  Operating Hours                    TV3
 * MembershipPackageListServlet     /manager/membership-packages                         #17  Membership Package List            TV3
 * MembershipPackageFormServlet     /manager/membership-packages/form                    #18  Membership Package Form            TV3
 * PtPackageListServlet             /manager/pt-packages                                 #19  PT Package List                    TV3
 * PtPackageFormServlet             /manager/pt-packages/form                            #20  PT Package Form                    TV3
 * VoucherListServlet               /manager/vouchers                                    #21  Voucher List                       TV3
 * VoucherFormServlet               /manager/vouchers/form                               #22  Voucher Form                       TV3
 * StaffListServlet                 /manager/staff                                       #23  Staff List                         TV3
 * StaffDetailServlet               /manager/staff/detail                                #24  Staff Details                      TV3
 * LeaveRequestListServlet          /manager/leave-requests                              #26  Leave Request List                 TV3
 * LeaveRequestReviewServlet        /manager/leave-requests/review                       #27  Leave Request Approval             TV3
 * MemberListServlet                /manager/members                                     #30  Customer List (Manager)            TV4
 * MemberDetailServlet              /manager/members/detail                              #31  Customer Details (Manager)         TV4
 * MemberDeletionReviewServlet      /manager/member-deletions/review                     #32  Member Deletion Approval           TV4
 * SystemTransactionServlet         /manager/transactions                                #41  System-wide Transactions           TV1
 * FacilityListServlet              /manager/facilities                                  #43  Facility List                      TV4
 * FacilityFormServlet              /manager/facilities/form                             #44  Facility Form                      TV4
 * MaintenanceRequestListServlet    /manager/maintenance-requests                        #46  Maintenance Request List           TV4
 * MaintenanceRequestReviewServlet  /manager/maintenance-requests/review                 #47  Maintenance Request Approval       TV4
 * FeedbackListServlet              /manager/feedback                                    #62  Feedback List                      TV2
 * BlogPostListServlet              /manager/blog-posts                                  #63  Blog Management                    TV2
 * BlogPostEditorServlet            /manager/blog-posts/editor                           #64  Blog Post Editor                   TV2
 * SendNotificationServlet          /manager/notifications/send                          #65  Send Notification                  TV2
 * </pre>
 *
 * <p>Mỗi Servlet: @WebServlet ngay trên class (cấm web.xml), doGet hiển thị / doPost xử lý,
 * chỉ điều phối — gọi Service, forward JSP bằng RequestUtil.forward hoặc trả JSON bằng JsonUtil.writeJson.</p>
 */
package com.gms.servlet.manager;
