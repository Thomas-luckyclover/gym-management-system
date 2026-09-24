/**
 * Khu vực Lễ tân — URL /receptionist/*, được ReceptionistAreaFilter bảo vệ.
 *
 * <p>Servlet dự kiến (nguồn: SRS 5.1 Screen List — bảng đầy đủ ở docs/project-structure.md):</p>
 * <pre>
 * CheckInServlet                   /receptionist/check-in                               #33  Member Check-in                    TV4
 * CustomerProfileListServlet       /receptionist/customers                              #28  Customer Profile List              TV4
 * CustomerProfileFormServlet       /receptionist/customers/form                         #29  Customer Profile Form              TV4
 * FreezeMembershipServlet          /receptionist/freeze                                 #34  Freeze Membership                  TV4
 * CounterSaleServlet               /receptionist/sales                                  #37  In-person Package Sale             TV4
 * PtPackageSaleServlet             /receptionist/pt-package-sales                       #38  PT Package Sale                    TV5
 * ShiftServlet                     /receptionist/shift                                  #39  Shift & Cash Reconciliation        TV4
 * FacilityCatalogServlet           /receptionist/facilities                             #42  Facility Catalog View              TV4
 * MaintenanceRequestServlet        /receptionist/maintenance-requests                   #45  Maintenance Request Form & My Requests TV4
 * </pre>
 *
 * <p>Mỗi Servlet: @WebServlet ngay trên class (cấm web.xml), doGet hiển thị / doPost xử lý,
 * chỉ điều phối — gọi Service, forward JSP bằng RequestUtil.forward hoặc trả JSON bằng JsonUtil.writeJson.</p>
 */
package com.gms.servlet.receptionist;
