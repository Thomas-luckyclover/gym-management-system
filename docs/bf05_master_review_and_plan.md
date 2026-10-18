# Tổng Kiểm Định Nghiệp Vụ & UI/UX Toàn Luồng `BF-05` (`UC-14`, `UC-15`, `JOB-07`)

> [!IMPORTANT]
> **LƯU Ý QUAN TRỌNG TRƯỚC KHI CHẠY THỬ TRÊN TRÌNH DUYỆT:**
> 1. Vì bạn có thêm thuộc tính mới vào `StaffLeaveFormView.java`, sau khi sửa code Java xong bạn **bắt buộc bấm `Clean and Build` (hoặc `Redeploy / Restart Tomcat`)** để Tomcat nạp file `.class` mới (nếu không Tomcat dùng `.class` cũ sẽ bị đứt ngang trang ở dấu `≥`).
> 2. Trong các file JSP bên dưới, đường dẫn `leave-requests.css?v=20261018` đã được gắn thêm `?v=20261018` để ép trình duyệt xóa bộ nhớ đệm (Cache) và nhận ngay khung giao diện 2 cột mới.

---

## PHẦN 1: Tổng Hợp Các Điểm Nâng Cấp Cho Màn #25 (`UC-14`) & Màn #26, #27 (`UC-15`)

![Ảnh chụp màn hình Màn #25 (Phần dưới bị đứt ở dấu ≥ do chưa Rebuild class mới & cache CSS cũ)](/C:/Users/Admin/.gemini/antigravity/brain/84af0ddb-d9ba-4baf-9592-67207eee971b/.user_uploaded/media_1792302545212_642eb7d2.png)

### 1.1. Bổ sung cho Màn #25 (`leave-request-form.jsp` — Nhân viên nộp đơn, `SRS 5.2.1`)
1. **Bổ sung `Staffing preview` chuẩn 100% theo `SRS 5.2.1` (dòng 2401):**
   - Trong SRS mục `5.2.1` quy định: *"Staffing preview: Shows, as of the moment of submission: **the number of PTs and receptionists that would remain on the chosen shift if this request is approved**; the submission cut-off time for that day; and the list of requests this staff member has already submitted, together with their status."*
   - Giải pháp: Tính sẵn số PT còn lại (`remainingPt`) và số Lễ tân còn lại (`remainingReceptionist`) nếu đơn nghỉ được duyệt bằng chính hàm `countOnDutyAfterApproval` đã có trong `LeaveRequestDAO`, truyền qua `StaffLeaveFormView` để hiển thị rõ ở cột bên phải:
     - **HLV (PT) còn trực:** `X PT` *(tối thiểu ≥ 2 PT / ca)*
     - **Lễ tân còn trực:** `Y Lễ tân` *(tối thiểu ≥ 1 Lễ tân / ca)*
2. **Hiển thị thời điểm gửi đơn (`Nộp lúc: dd/MM/yyyy HH:mm`) ở danh sách `"Đơn đã nộp gần đây"`:**
   - Giúp nhân viên đối chiếu chính xác thời gian mình đã bấm nộp đơn so với mốc chốt `15:00` (`P-12`).
3. **Tách class `.leave-form-grid` riêng trong `leave-requests.css` & Thêm `?v=20261018`:**
   - Giúp 2 ô chọn `Loại nghỉ` và `Ca nghỉ` thẳng hàng, đẹp mắt và không bị ăn nhầm khung viền của `.leave-info-grid > div`.

---

## PHẦN 2: Chi Tiết Các File Cần Cập Nhật Để Hoàn Thiện Màn #25, #26, #27

### 1. File [StaffLeaveFormView.java](file:///d:/GMS/gym-management-system/src/main/java/com/gms/dto/StaffLeaveFormView.java)
Thay thế toàn bộ nội dung file [StaffLeaveFormView.java](file:///d:/GMS/gym-management-system/src/main/java/com/gms/dto/StaffLeaveFormView.java) bằng bản dưới đây (đã bổ sung `minPtPerShift`, `minReceptionistPerShift`, `remainingPt`, `remainingReceptionist` cho `Staffing preview` của `SRS 5.2.1`):

```java
package com.gms.dto;

import com.gms.entity.LeaveRequest;

import java.time.LocalTime;
import java.util.Collections;
import java.util.List;

/**
 * DTO dữ liệu khởi tạo cho màn hình nộp đơn nghỉ phép của nhân viên (màn #25, UC-14).
 * Service tạo từ Employee, LeaveRequestDAO và SystemConfigService; Servlet chuyển sang JSP.
 */
public class StaffLeaveFormView {

    // DAO: Employees.EmployeeId của nhân viên đang đăng nhập.
    private final int employeeId;

    // DAO: Employees.EmployeeCode (ví dụ NV-0005).
    private final String employeeCode;

    // DAO: Persons.FullName của nhân viên.
    private final String fullName;

    // DAO: Employees.Position ("PT" hoặc "Receptionist").
    private final String position;

    // SystemConfigs: tham số P-12 (mặc định 15:00).
    private final LocalTime submissionCutoffTime;

    // SystemConfigs: tham số P-13 (số PT tối thiểu mỗi ca, mặc định 2).
    private final int minPtPerShift;

    // SystemConfigs: tham số P-14 (số Lễ tân tối thiểu mỗi ca, mặc định 1).
    private final int minReceptionistPerShift;

    // Dự báo số PT còn trực ca nếu đơn nghỉ được duyệt (SRS 5.2.1 Staffing preview).
    private final int remainingPt;

    // Dự báo số Lễ tân còn trực ca nếu đơn nghỉ được duyệt (SRS 5.2.1 Staffing preview).
    private final int remainingReceptionist;

    // DAO: danh sách các đơn xin nghỉ gần đây của nhân viên này.
    private final List<LeaveRequest> myRequests;

    public StaffLeaveFormView(
            int employeeId,
            String employeeCode,
            String fullName,
            String position,
            LocalTime submissionCutoffTime,
            List<LeaveRequest> myRequests
    ) {
        this(employeeId, employeeCode, fullName, position, submissionCutoffTime, 2, 1, 2, 1, myRequests);
    }

    public StaffLeaveFormView(
            int employeeId,
            String employeeCode,
            String fullName,
            String position,
            LocalTime submissionCutoffTime,
            int minPtPerShift,
            int minReceptionistPerShift,
            List<LeaveRequest> myRequests
    ) {
        this(employeeId, employeeCode, fullName, position, submissionCutoffTime,
                minPtPerShift, minReceptionistPerShift, minPtPerShift, minReceptionistPerShift, myRequests);
    }

    public StaffLeaveFormView(
            int employeeId,
            String employeeCode,
            String fullName,
            String position,
            LocalTime submissionCutoffTime,
            int minPtPerShift,
            int minReceptionistPerShift,
            int remainingPt,
            int remainingReceptionist,
            List<LeaveRequest> myRequests
    ) {
        this.employeeId = employeeId;
        this.employeeCode = employeeCode;
        this.fullName = fullName;
        this.position = position;
        this.submissionCutoffTime = submissionCutoffTime;
        this.minPtPerShift = minPtPerShift;
        this.minReceptionistPerShift = minReceptionistPerShift;
        this.remainingPt = remainingPt;
        this.remainingReceptionist = remainingReceptionist;
        this.myRequests = myRequests == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(myRequests);
    }

    public int getEmployeeId() {
        return employeeId;
    }

    public String getEmployeeCode() {
        return employeeCode;
    }

    public String getFullName() {
        return fullName;
    }

    public String getPosition() {
        return position;
    }

    public LocalTime getSubmissionCutoffTime() {
        return submissionCutoffTime;
    }

    public int getMinPtPerShift() {
        return minPtPerShift;
    }

    public int getMinReceptionistPerShift() {
        return minReceptionistPerShift;
    }

    public int getRemainingPt() {
        return remainingPt;
    }

    public int getRemainingReceptionist() {
        return remainingReceptionist;
    }

    public List<LeaveRequest> getMyRequests() {
        return myRequests;
    }
}
```

---

### 2. Hàm `getStaffLeaveForm` trong [LeaveRequestService.java](file:///d:/GMS/gym-management-system/src/main/java/com/gms/service/LeaveRequestService.java#L512-L549)
Tại [LeaveRequestService.java (dòng 512–549)](file:///d:/GMS/gym-management-system/src/main/java/com/gms/service/LeaveRequestService.java#L512-L549), cập nhật hàm `getStaffLeaveForm` để tính luôn `remainingPt` và `remainingReceptionist` cho ca ngày mai (`SRS 5.2.1`):

```java
    public ServiceResult<StaffLeaveFormView> getStaffLeaveForm(int accountId) {
        try {
            // B1: tìm nhân viên từ tài khoản đăng nhập
            Employee employee = findEmployeeForAccount(accountId);
            if (employee == null) {
                return ServiceResult.fail(
                        String.format(Messages.MSG08_NOT_FOUND, "nhân viên"));
            }

            // B2: lấy họ tên từ Persons để hiện trên form
            Person person = personDAO.findById(employee.getPersonId());
            String fullName = person == null ? "" : person.getFullName();

            // B3: đọc giờ chốt nộp đơn P-12 và định biên P-13, P-14 từ SystemConfigs
            LocalTime submissionCutoff = LocalTime.parse(
                    systemConfigService.getString(
                            SystemConfigService.LEAVE_SUBMIT_DEADLINE));
            int minPt = systemConfigService.getInt(SystemConfigService.MIN_PT_PER_SHIFT);
            int minReceptionist = systemConfigService.getInt(SystemConfigService.MIN_RECEPTIONIST_PER_SHIFT);

            // B4: tính dự báo quân số PT và Lễ tân còn trực vào ngày mai nếu đơn này được duyệt (SRS 5.2.1)
            LocalDate tomorrow = DateTimeUtil.today().plusDays(1);
            int remainingPt = leaveRequestDAO.countOnDutyAfterApproval(tomorrow, "FullDay", "PT", 0);
            int remainingReceptionist = leaveRequestDAO.countOnDutyAfterApproval(tomorrow, "FullDay", "Receptionist", 0);

            if (Role.PT == employee.getPosition()) {
                // Người nộp là PT nên số Lễ tân đang trực không bị trừ 1
                remainingReceptionist = remainingReceptionist + 1;
            } else {
                // Người nộp là Lễ tân nên số PT đang trực không bị trừ 1
                remainingPt = remainingPt + 1;
            }

            // B5: lấy 10 đơn gần nhất của chính nhân viên này
            List<LeaveRequest> myRequests = leaveRequestDAO.findByEmployeeId(employee.getEmployeeId(), 10);

            return ServiceResult.ok(new StaffLeaveFormView(
                    employee.getEmployeeId(),
                    employee.getEmployeeCode(),
                    fullName,
                    employee.getPosition().getDbValue(),
                    submissionCutoff,
                    minPt,
                    minReceptionist,
                    remainingPt,
                    remainingReceptionist,
                    myRequests));

        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }
```

---

### 3. Phần cuối file [leave-requests.css](file:///d:/GMS/gym-management-system/src/main/webapp/css/pages/manager/leave-requests.css#L354-L413)
Tại [leave-requests.css (từ dòng 354 đến hết file)](file:///d:/GMS/gym-management-system/src/main/webapp/css/pages/manager/leave-requests.css#L354-L413), thay đoạn CSS của màn #25 bằng đoạn sau (đã thêm `.leave-form-grid` cho 2 ô chọn `Loại nghỉ` / `Ca nghỉ`):

```css
/* ===== Trang nhân viên nộp đơn (màn #25, UC-14) ===== */

.leave-split {
    display: grid;
    grid-template-columns: minmax(0, 1fr) 340px;
    gap: var(--space-6);
    align-items: start;
}

.leave-form-grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: var(--space-4);
    margin-bottom: var(--space-4);
}

.leave-side-card {
    padding: var(--space-4) var(--space-5);
    margin-bottom: var(--space-4);
    border: var(--border-width) solid var(--color-border-strong);
    border-radius: var(--radius-lg);
    background: var(--color-surface);
    box-shadow: var(--shadow-card);
}

.leave-side-title {
    display: block;
    margin-bottom: var(--space-3);
    font-size: var(--font-size-xs);
    font-weight: 700;
    text-transform: uppercase;
    letter-spacing: 0.04em;
    color: var(--color-text-soft);
}

.leave-side-row {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: var(--space-2);
    margin-bottom: var(--space-2);
    font-size: var(--font-size-sm);
}

.leave-history-list {
    display: flex;
    flex-direction: column;
    gap: var(--space-3);
}

.leave-history-row {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;
    gap: var(--space-3);
    padding: var(--space-3) var(--space-4);
    border: var(--border-width) solid var(--color-border);
    border-radius: var(--radius-md);
    background: var(--color-bg);
}

@media (max-width: 960px) {
    .leave-split,
    .leave-form-grid {
        grid-template-columns: 1fr;
    }
}
```

---

### 4. File [leave-request-form.jsp](file:///d:/GMS/gym-management-system/src/main/webapp/WEB-INF/jsp/staff/leave-request-form.jsp) (Màn #25)
Thay thế toàn bộ nội dung [leave-request-form.jsp](file:///d:/GMS/gym-management-system/src/main/webapp/WEB-INF/jsp/staff/leave-request-form.jsp) bằng bản hoàn chỉnh dưới đây (đã có `?v=20261018` chống cache CSS, hiển thị `Staffing preview` chuẩn `SRS 5.2.1` và giờ nộp đơn `SubmittedAt`):

```jsp
<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="gms" uri="/WEB-INF/gms-functions.tld" %>

<c:set var="isReceptionist" value="${sessionScope.role eq 'Receptionist' or formView.position eq 'Receptionist'}" />

<!DOCTYPE html>
<html lang="vi">
<head>
  <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
  <title>Nghỉ phép · Super Gym</title>
  <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/auth/account-settings.css?v=20261018">
  <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/manager/leave-requests.css?v=20261018">
  <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/auth/notification-bell.css?v=20261018">
  <script src="${pageContext.request.contextPath}/js/pages/auth/notification-bell.js" defer></script>
</head>

<body class="dock ${isReceptionist ? 'theme-staff' : 'theme-pt'}">
  <%@ include file="/WEB-INF/jsp/common/flash-toast.jspf" %>

  <div class="app">
    <%-- ===== Sidebar dock theo role PT / Lễ tân ===== --%>
    <c:set var="activeNav" value="leaves" />
    <c:choose>
      <c:when test="${isReceptionist}">
        <%@ include file="/WEB-INF/jsp/common/layout-receptionist.jspf" %>
      </c:when>
      <c:otherwise>
        <%@ include file="/WEB-INF/jsp/common/layout-pt.jspf" %>
      </c:otherwise>
    </c:choose>

    <div class="main">
      <%-- ===== Topbar đồng bộ chuẩn thiết kế có chuông thông báo ===== --%>
      <header class="topbar">
        <div class="tb-left">
          <h1 class="tb-title">Đơn nghỉ phép</h1>
        </div>

        <div class="top-right">
          <%@ include file="/WEB-INF/jsp/common/notification-bell.jspf" %>

          <a class="tb-user" href="${pageContext.request.contextPath}/account">
            <span class="avatar">${isReceptionist ? 'LT' : 'PT'}</span>
            <span>
              <b><c:out value="${not empty formView.fullName ? formView.fullName : sessionScope.displayName}" default="Nhân viên" /></b>
              <small>
                ${isReceptionist ? 'Lễ tân' : 'Huấn luyện viên'}
                <c:if test="${not empty formView.employeeCode}">
                  · <c:out value="${formView.employeeCode}" />
                </c:if>
              </small>
            </span>
          </a>
        </div>
      </header>

      <main class="content" id="main-content" tabindex="-1">
        <%-- Thông báo lỗi nộp đơn từ server + Nút dẫn sang màn xử lý lịch dạy (BF-08 / CBR-83) --%>
        <c:if test="${not empty submitError}">
          <div class="alert-banner danger" style="margin-bottom:var(--space-4);display:flex;align-items:center;justify-content:space-between;gap:var(--space-3);flex-wrap:wrap">
            <div>
              <i class="fa-solid fa-circle-exclamation" aria-hidden="true"></i>
              <c:out value="${submitError}" />
            </div>
            <c:if test="${not isReceptionist and submitError.contains('buổi')}">
              <a class="btn btn-sm btn-outline" href="${pageContext.request.contextPath}/pt/dashboard">
                Mở lịch dạy để xử lý (BF-08) →
              </a>
            </c:if>
          </div>
        </c:if>

        <c:if test="${not empty formError}">
          <div class="alert-banner danger" style="margin-bottom:var(--space-4)">
            <i class="fa-solid fa-circle-exclamation" aria-hidden="true"></i>
            <c:out value="${formError}" />
          </div>
        </c:if>

        <%-- Layout 2 cột cho màn nộp đơn (formView: StaffLeaveFormView do Service tạo) --%>
        <div class="leave-split">
          <div class="leave-col-main">
            <%-- ===== Panel 1: Form nộp đơn xin nghỉ phép ===== --%>
            <section class="panel leave-panel">
              <div class="panel-head">
                <h2>Tạo đơn nghỉ phép</h2>
                <p>Điền thông tin ngày, ca nghỉ và lý do để gửi Quản lý phê duyệt.</p>
              </div>

              <c:set var="submitActionUrl" value="${pageContext.request.contextPath}${isReceptionist ? '/receptionist/leave-requests' : '/pt/leave-requests'}" />
              <form action="${submitActionUrl}" method="post" id="leaveForm" novalidate>
                <input type="hidden" name="csrfToken" value="${csrfToken}">

                <div class="leave-form-grid">
                  <div class="field">
                    <label class="field-label" for="leaveType">
                      Loại nghỉ <span aria-hidden="true">*</span>
                    </label>
                    <select id="leaveType" name="leaveType" required>
                      <option value="Annual" ${paramLeaveType eq 'Annual' ? 'selected' : ''}>Nghỉ phép năm</option>
                      <option value="Sick" ${paramLeaveType eq 'Sick' ? 'selected' : ''}>Nghỉ ốm</option>
                      <option value="Unpaid" ${paramLeaveType eq 'Unpaid' ? 'selected' : ''}>Nghỉ không lương</option>
                    </select>
                    <c:if test="${not empty fieldErrors.leaveType}">
                      <div class="field-error">
                        <c:out value="${fieldErrors.leaveType}" />
                      </div>
                    </c:if>
                  </div>

                  <div class="field">
                    <label class="field-label" for="shiftType">
                      Ca nghỉ <span aria-hidden="true">*</span>
                    </label>
                    <select id="shiftType" name="shiftType" required>
                      <option value="FullDay" ${paramShiftType eq 'FullDay' ? 'selected' : ''}>Cả ngày</option>
                      <option value="Morning" ${paramShiftType eq 'Morning' ? 'selected' : ''}>Ca sáng</option>
                      <option value="Afternoon" ${paramShiftType eq 'Afternoon' ? 'selected' : ''}>Ca chiều</option>
                    </select>
                    <c:if test="${not empty fieldErrors.shiftType}">
                      <div class="field-error">
                        <c:out value="${fieldErrors.shiftType}" />
                      </div>
                    </c:if>
                  </div>
                </div>

                <div class="field" style="margin-bottom:var(--space-4)">
                  <label class="field-label" for="leaveDate">
                    Ngày nghỉ <span aria-hidden="true">*</span>
                  </label>
                  <input id="leaveDate"
                         name="leaveDate"
                         type="date"
                         data-cutoff="<c:out value='${formView.submissionCutoffTime}' default='15:00' />"
                         value="<c:out value='${paramLeaveDate}' />"
                         required>
                  <span class="field-hint" style="display:block;margin-top:var(--space-1);color:var(--color-text-soft);font-size:var(--font-size-xs)">
                    <i class="fa-solid fa-circle-info" aria-hidden="true"></i>
                    Đơn phải nộp trước giờ chốt (<c:out value="${formView.submissionCutoffTime}" default="15:00" />) của ngày hôm trước. Nghỉ đột xuất cùng ngày vui lòng báo trực tiếp Quản lý.
                  </span>
                  <div class="field-error" id="leaveDateClientErr" hidden></div>
                  <c:if test="${not empty fieldErrors.leaveDate}">
                    <div class="field-error">
                      <c:out value="${fieldErrors.leaveDate}" />
                    </div>
                  </c:if>
                </div>

                <div class="field" style="margin-bottom:var(--space-5)">
                  <label class="field-label" for="leaveReason">
                    Lý do nghỉ <span aria-hidden="true">*</span>
                  </label>
                  <textarea id="leaveReason"
                            name="reason"
                            rows="4"
                            minlength="5"
                            maxlength="500"
                            placeholder="Nêu ngắn gọn lý do nghỉ (5–500 ký tự)…"
                            required><c:out value="${paramReason}" /></textarea>
                  <c:if test="${not empty fieldErrors.reason}">
                    <div class="field-error">
                      <c:out value="${fieldErrors.reason}" />
                    </div>
                  </c:if>
                </div>

                <button class="btn btn-primary" type="submit" id="leaveSubmit">
                  <i class="fa-solid fa-paper-plane" aria-hidden="true"></i> Nộp đơn nghỉ phép
                </button>
              </form>
            </section>

            <%-- ===== Panel 2: Danh sách đơn đã nộp gần đây (formView.myRequests) ===== --%>
            <section class="panel leave-panel">
              <div class="panel-head" style="display:flex;align-items:center;justify-content:space-between">
                <div>
                  <h2>Đơn đã nộp gần đây</h2>
                  <p>Hiển thị tối đa 10 đơn xin nghỉ phép mới nhất của bạn.</p>
                </div>
              </div>

              <c:choose>
                <c:when test="${empty formView.myRequests}">
                  <div class="leave-empty">Bạn chưa có đơn xin nghỉ phép nào được nộp.</div>
                </c:when>
                <c:otherwise>
                  <div class="leave-history-list">
                    <c:forEach var="req" items="${formView.myRequests}">
                      <div class="leave-history-row">
                        <div style="flex:1">
                          <div style="display:flex;align-items:center;gap:var(--space-2);margin-bottom:var(--space-1);flex-wrap:wrap">
                            <b>${gms:formatDate(req.leaveDate)}</b>
                            <span style="color:var(--color-text-soft);font-size:var(--font-size-xs)">
                              · <c:out value="${req.shiftType.displayName}" />
                            </span>
                            <c:if test="${req.submittedAt != null}">
                              <span style="color:var(--color-text-soft);font-size:var(--font-size-xs)">
                                · Nộp lúc ${gms:formatDateTime(req.submittedAt)}
                              </span>
                            </c:if>
                          </div>
                          <div style="font-size:var(--font-size-xs);color:var(--color-text-soft);display:flex;flex-wrap:wrap;gap:var(--space-3);margin-bottom:var(--space-1)">
                            <span><i class="fa-solid fa-tag" aria-hidden="true"></i> <c:out value="${req.leaveType.displayName}" /></span>
                            <span><i class="fa-solid fa-quote-left" aria-hidden="true"></i> <c:out value="${req.reason}" /></span>
                          </div>
                          <c:if test="${req.rejected and not empty req.rejectReason}">
                            <div style="font-size:var(--font-size-xs);color:var(--color-danger)">
                              <i class="fa-solid fa-circle-exclamation" aria-hidden="true"></i> Lý do từ chối: <c:out value="${req.rejectReason}" />
                            </div>
                          </c:if>
                        </div>

                        <div style="white-space:nowrap">
                          <span class="badge ${req.pending ? 'badge-warning' : req.approved ? 'badge-success' : 'badge-danger'}">
                            <c:out value="${req.status.displayName}" />
                          </span>
                        </div>
                      </div>
                    </c:forEach>
                  </div>
                </c:otherwise>
              </c:choose>
            </section>
          </div>

          <%-- ===== Cột phải: Staffing preview theo SRS 5.2.1 (Hạn nộp P-12 & Quân số trực ca dự kiến) ===== --%>
          <div class="leave-col-side">
            <div class="leave-side-card">
              <span class="leave-side-title">Hạn chốt nộp đơn (P-12)</span>
              <div class="leave-side-row">
                <span style="color:var(--color-text-soft)">Ngày nghỉ đang chọn:</span>
                <b id="infoDate">—</b>
              </div>
              <div class="leave-side-row" style="margin-bottom:var(--space-3)">
                <span style="color:var(--color-text-soft)">Hạn nộp trước:</span>
                <b id="infoDeadline"><c:out value="${formView.submissionCutoffTime}" default="15:00" /> · ngày hôm trước</b>
              </div>
              <p style="margin:0;font-size:var(--font-size-xs);color:var(--color-text-soft);line-height:1.5">
                Quá giờ chốt trên, nếu nghỉ đột xuất vui lòng báo trực tiếp Quản lý ngoài hệ thống (CBR-84).
              </p>
            </div>

            <div class="leave-side-card">
              <span class="leave-side-title">Dự báo quân số & Định biên ca trực</span>
              <div class="leave-side-row">
                <span style="color:var(--color-text-soft)">PT còn trực (nếu duyệt):</span>
                <b><c:out value="${formView.remainingPt}" default="2" /> PT <small style="font-weight:400;color:var(--color-text-soft)">(tối thiểu ≥ <c:out value="${formView.minPtPerShift}" default="2" />)</small></b>
              </div>
              <div class="leave-side-row" style="margin-bottom:var(--space-3)">
                <span style="color:var(--color-text-soft)">Lễ tân còn trực (nếu duyệt):</span>
                <b><c:out value="${formView.remainingReceptionist}" default="1" /> Lễ tân <small style="font-weight:400;color:var(--color-text-soft)">(tối thiểu ≥ <c:out value="${formView.minReceptionistPerShift}" default="1" />)</small></b>
              </div>
              <p style="margin:0;font-size:var(--font-size-xs);color:var(--color-text-soft);line-height:1.5">
                Đơn chỉ được Quản lý duyệt khi số nhân sự còn lại trong ca đảm bảo định biên tối thiểu (P-13, P-14).
              </p>
            </div>
          </div>
        </div>
      </main>
    </div>
  </div>

  <script src="${pageContext.request.contextPath}/js/pages/staff/leave-request-form.js?v=20261018"></script>
</body>
</html>
```
