/**
 * Khu vực Huấn luyện viên — URL /pt/*, được PtAreaFilter bảo vệ. Màn điểm danh phải dùng được trên điện thoại (GBR-35).
 *
 * <p>Servlet dự kiến (nguồn: SRS 5.1 Screen List — bảng đầy đủ ở docs/project-structure.md):</p>
 * <pre>
 * PtDashboardServlet               /pt/dashboard                                        #49  PT Dashboard                       TV5
 * TimeSlotListServlet              /pt/time-slots                                       #54  Available Time Slots               TV5
 * TimeSlotAddServlet               /pt/time-slots/add                                   #55  Add Time Slot                      TV5
 * SessionAttendanceServlet         /pt/sessions/attendance                              #56  Session Attendance                 TV5
 * ClientListServlet                /pt/clients                                          #57  Client List                        TV5
 * PrivateNoteServlet               /pt/clients/notes                                    #58  Private Client Notes               TV5
 * TrainingPlanEditorServlet        /pt/clients/plans                                    #59  Workout & Diet Plan Editor         TV5
 * </pre>
 *
 * <p>Mỗi Servlet: @WebServlet ngay trên class (cấm web.xml), doGet hiển thị / doPost xử lý,
 * chỉ điều phối — gọi Service, forward JSP bằng RequestUtil.forward hoặc trả JSON bằng JsonUtil.writeJson.</p>
 */
package com.gms.servlet.pt;
