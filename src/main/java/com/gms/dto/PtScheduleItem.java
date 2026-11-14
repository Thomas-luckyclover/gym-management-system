package com.gms.dto;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * DTO 1 Ô trên lịch dạy PT (1 khung giờ, có thể kèm 1 buổi của khách).
 *
 * AI TẠO / AI ĐIỀN — theo 3 bước:
 *   B1  DAO tạo ô (đọc DB):
 *         PtScheduleDAO.findOpenSlots → constructor 5 tham số  (khung Free / Held, CHƯA có buổi)
 *         PtScheduleDAO.findSessions  → constructor 11 tham số (khung CÓ buổi)
 *   B2  Service tính thêm (không đọc DB): PtScheduleService.classify → fillWeek → assignLanes
 *   B3  JSP đọc: pt/_schedule-slot.jspf (biến item), _schedule-day.jspf, schedule.jsp
 *   Dùng thêm ở: PtScheduleDAO.findSlotsForPublish (kiểm trùng khi Công bố), PtSaleDAO.findPtSlots (màn Bán gói PT).
 *
 * CÁCH ĐỌC COMMENT MỖI FIELD:
 *   NGUỒN   ← bảng.cột (DAO) hoặc hàm Service tính ra
 *   DÙNG    → field đi đâu trên màn, theo 1 trong 4 kiểu:
 *             [CHỮ]   in thành chữ người dùng đọc được
 *             [CSS]   không thấy chữ, chỉ quyết định vị trí / kích thước / màu của ô
 *             [NÚT]   quyết định ô có ô tích "Gỡ khung" / link "Bấm để xử lý" hay không
 *             [ẨN]    nằm trong HTML (id, data-*, value) cho JS / form dùng, người dùng không thấy
 *             [KHÔNG DÙNG] DAO có đọc nhưng màn này không dùng tới
 */
public class PtScheduleItem {

    // Ô KHUNG (chưa có buổi) — PtScheduleDAO.findOpenSlots / findSlotsForPublish gọi.
    // kind lúc này = TimeSlots.Status gốc ("Free" | "Held"); classify đổi lại thành "free" / "held" / "locked".
    public PtScheduleItem(int timeSlotId, LocalDate slotDate, LocalTime startTime, LocalTime endTime, String kind) {
        this.timeSlotId = timeSlotId;
        this.slotDate = slotDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.kind = kind;
    }

    // Ô BUỔI (khung có khách) — PtScheduleDAO.findSessions gọi (SESSIONS_SQL).
    // kind để null; classify gán "booked".
    public PtScheduleItem(Integer sessionId, String sessionStatus, int timeSlotId, LocalDate slotDate, LocalTime startTime,
            LocalTime endTime, String packageName, int sessionNo, int totalSessions, String customerNames,
            String customerPhone) {
        this.sessionId = sessionId;
        this.sessionStatus = sessionStatus;
        this.timeSlotId = timeSlotId;
        this.slotDate = slotDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.packageName = packageName;
        this.sessionNo = sessionNo;
        this.totalSessions = totalSessions;
        this.customerNames = customerNames;
        this.customerPhone = customerPhone;
    }

    // Ô RỖNG — dùng khi chỉ cần ngày + giờ để kiểm (VD validateDrafts kiểm khung nháp có trùng ca nghỉ không).
    public PtScheduleItem() {
    }


    // =================================================================================================
    // NHÓM 1. LẤY TỪ BẢNG TimeSlots — mọi ô đều có (cả ô khung lẫn ô buổi)
    // =================================================================================================

    // NGUỒN ← TimeSlots.TimeSlotId
    // DÙNG  → [ẨN] value của ô tích "Gỡ khung" (name=removeSlotId, gửi khi Công bố)
    //       → Service merge: gom ô cùng khung (buổi đã hủy + khung trống) để chỉ giữ 1 ô
    private int timeSlotId;

    // NGUỒN ← TimeSlots.SlotDate
    // DÙNG  → Service fillWeek: bỏ ô vào đúng cột ngày (JSP không in trực tiếp, cột ngày in day.date)
    private LocalDate slotDate;

    // NGUỒN ← TimeSlots.StartTime
    // DÙNG  → [CHỮ] "07:00 – …" trên ô
    //       → Service: tính startMinute, ca sáng / chiều (so 13:00 khi xét nghỉ phép), sắp xếp ô
    private LocalTime startTime;

    // NGUỒN ← TimeSlots.EndTime
    // DÙNG  → [CHỮ] "… – 08:30" trên ô
    //       → Service: tính lengthMinutes, past (đã kết thúc chưa)
    private LocalTime endTime;


    // =================================================================================================
    // NHÓM 2. LẤY TỪ BẢNG Sessions + bảng JOIN — CHỈ ô buổi có (ô khung để null / 0)
    //         SQL: SESSIONS_SQL trong PtScheduleDAO
    //         Sessions JOIN TimeSlots, Subscriptions, Orders, Customers, Persons
    // =================================================================================================

    // NGUỒN ← Sessions.SessionId (null = ô khung, không có buổi)
    // DÙNG  → [ẨN] id="session-<id>" (sau khi Dời / Hủy, trang cuộn tới đúng ô)
    //       → [NÚT] link "Bấm để xử lý": ?handle=<sessionId>
    private Integer sessionId;

    // NGUỒN ← Sessions.Status ('Scheduled' | 'CheckedIn' | 'Missed' | 'Cancelled')
    // DÙNG  → [ẨN] data-status (CSS tô màu theo trạng thái)
    //       → Service sessionLabel: đổi thành chữ statusLabel (KHÔNG in thẳng chữ tiếng Anh)
    private String sessionStatus;

    // NGUỒN ← Persons.FullName của người đứng tên (Subscriptions.CustomerId → Customers → Persons)
    //         gói đôi: + " & " + tên người tập cùng (Orders.PartnerCustomerId → Customers → Persons)
    // DÙNG  → [CHỮ] tên in đậm trên ô, VD "Nguyễn An & Trần Bình"
    private String customerNames;

    // NGUỒN ← Orders.PackageNameSnapshot (tên gói chụp lúc mua, đổi catalogue không ảnh hưởng)
    // DÙNG  → [CHỮ] "Gói PT 3 tháng · Buổi n/N"
    private String packageName;

    // NGUỒN ← KHÔNG có cột: SQL tính ROW_NUMBER() OVER (PARTITION BY SubscriptionId ORDER BY SlotDate, StartTime)
    // DÙNG  → [CHỮ] số n trong "Buổi n/N"
    private int sessionNo;

    // NGUỒN ← Subscriptions.GeneratedSessionCount (số buổi sinh lúc bán, cố định — DBR-53)
    // DÙNG  → [CHỮ] số N trong "Buổi n/N"
    private int totalSessions;

    // NGUỒN ← Persons.Phone của người đứng tên
    // DÙNG  → [KHÔNG DÙNG] màn Lịch dạy không in SĐT; không class nào gọi getCustomerPhone() của DTO này
    //         (SĐT + Zalo khách PT xem ở Dashboard PT, qua DTO khác)
    private String customerPhone;


    // =================================================================================================
    // NHÓM 3. SERVICE TÍNH RA — không có trong DB
    //         PtScheduleService: classify (loại ô, nhãn, nút) · fillWeek (vị trí) · assignLanes (làn)
    // =================================================================================================

    // NGUỒN ← classify:
    //           có sessionId                      → "booked"
    //           TimeSlots.Status = 'Held'         → "held"
    //           khung trống trùng ca nghỉ (LeaveRequests Pending/Approved) → "locked"
    //           còn lại                           → "free"
    // DÙNG  → [CSS] màu ô (class is-booked / is-held / is-locked / is-free)
    //       → JSP chọn chữ phụ: held → "Đơn chờ thanh toán", locked → "Nghỉ phép", free → "Khung rảnh"
    private String kind;

    // NGUỒN ← classify:
    //           ô buổi  → sessionLabel: Scheduled → "Sắp tới" | "Đang diễn ra" | "Chưa chốt" (theo giờ hiện tại)
    //                                   CheckedIn → "Đã check-in" · Missed → "Vắng" · Cancelled → "Đã hủy"
    //           ô khung → "Còn trống" | "Đang giữ · đơn chờ thanh toán" | "Chờ duyệt nghỉ" | "Đã duyệt nghỉ"
    // DÙNG  → [CHỮ] chữ trạng thái góc ô (ô held JSP in gọn "Đang giữ") + tooltip (title)
    private String statusLabel;

    // NGUỒN ← classify: EndTime <= giờ hiện tại
    // DÙNG  → [CSS] ô mờ (class is-past)
    private boolean past;

    // NGUỒN ← classify: kind = "free" VÀ chưa tới giờ bắt đầu
    // DÙNG  → [NÚT] ô có ô tích "Gỡ khung" / "Giữ lại" (BR-95)
    //       → schedule.jsp: có ít nhất 1 ô canRemove → hiện nút "Gỡ hết khung trống"
    private boolean canRemove;

    // NGUỒN ← classify: có buổi VÀ Sessions.Status = 'Scheduled' VÀ chưa tới giờ bắt đầu (BR-70)
    // DÙNG  → [NÚT] ô là link "Bấm để xử lý" mở hộp Dời / Chuyển / Hủy
    private boolean canHandle;

    // NGUỒN ← fillWeek: phút của startTime − phút đầu trục giờ (week.axisStartHour × 60)
    //         (classify cũng gán 1 lần nhưng fillWeek ghi đè ngay sau → giá trị cuối là của fillWeek)
    //         VD trục bắt đầu 5:00, ô 07:00 → 120
    // DÙNG  → [CSS] --start: vị trí dọc của ô trên cột
    private int startMinute;

    // NGUỒN ← classify: EndTime − StartTime (phút), VD 90
    // DÙNG  → [CSS] --length: chiều cao ô
    private int lengthMinutes;

    // NGUỒN ← assignLanes: ô này đứng làn thứ mấy trong CỤM ô chồng giờ (0 = sát trái)
    //         VD 19:00–20:30 (làn 0) chồng 20:00–21:30 (làn 1)
    // DÙNG  → [CSS] --lane: lệch ngang
    private int lane;

    // NGUỒN ← assignLanes: cụm chứa ô này có mấy làn (1 = không chồng ai)
    // DÙNG  → [CSS] --lanes: chia bề ngang; > 1 thì thêm class is-narrow (ô hẹp)
    private int laneCount = 1;


    // =================================================================================================
    // NHÓM 4. GETTER TỰ TÍNH — không có field, không có setter; tính lại mỗi lần JSP gọi
    // =================================================================================================

    // = startTime đổi ra phút từ 0 giờ (VD 07:00 → 420)
    // DÙNG → [ẨN] data-start (JS chặn kéo khung nháp đè lên ô này) · _schedule-slot.jspf:19
    public int getStartOfDay() {
        return startTime.toSecondOfDay() / 60;
    }

    // = endTime đổi ra phút từ 0 giờ (VD 08:30 → 510)
    // DÙNG → [ẨN] data-end · _schedule-slot.jspf:19
    public int getEndOfDay() {
        return endTime.toSecondOfDay() / 60;
    }

    // = kind == "booked" (ô có buổi)
    // DÙNG → [ẨN] chỉ ô buổi mới có id="session-<id>" · JSP chọn in tên khách + "Buổi n/N" · _schedule-slot.jspf:15, 62
    public boolean isBooked() {
        return "booked".equals(kind);
    }

    // = ô chiếm giờ thật: khung (không buổi) HOẶC buổi còn Scheduled
    //   (buổi CheckedIn / Missed / Cancelled không chiếm giờ → được thả khung mới đè lên)
    // DÙNG → [ẨN] chỉ ô blocking mới có data-start / data-end · _schedule-slot.jspf:19
    public boolean isBlocking() {
        return sessionId == null || "Scheduled".equals(sessionStatus);
    }


    // =================================================================================================
    // GETTER / SETTER
    // =================================================================================================

    public int getTimeSlotId() { return timeSlotId; }
    public void setTimeSlotId(int timeSlotId) { this.timeSlotId = timeSlotId; }

    public LocalDate getSlotDate() { return slotDate; }
    public void setSlotDate(LocalDate slotDate) { this.slotDate = slotDate; }

    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }

    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime endTime) { this.endTime = endTime; }

    public Integer getSessionId() { return sessionId; }
    public void setSessionId(Integer sessionId) { this.sessionId = sessionId; }

    public String getSessionStatus() { return sessionStatus; }
    public void setSessionStatus(String sessionStatus) { this.sessionStatus = sessionStatus; }

    public String getCustomerNames() { return customerNames; }
    public void setCustomerNames(String customerNames) { this.customerNames = customerNames; }

    public String getCustomerPhone() { return customerPhone; }
    public void setCustomerPhone(String customerPhone) { this.customerPhone = customerPhone; }

    public String getPackageName() { return packageName; }
    public void setPackageName(String packageName) { this.packageName = packageName; }

    public int getSessionNo() { return sessionNo; }
    public void setSessionNo(int sessionNo) { this.sessionNo = sessionNo; }

    public int getTotalSessions() { return totalSessions; }
    public void setTotalSessions(int totalSessions) { this.totalSessions = totalSessions; }

    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }

    public boolean isPast() { return past; }
    public void setPast(boolean past) { this.past = past; }

    public String getStatusLabel() { return statusLabel; }
    public void setStatusLabel(String statusLabel) { this.statusLabel = statusLabel; }

    public int getStartMinute() { return startMinute; }
    public void setStartMinute(int startMinute) { this.startMinute = startMinute; }

    public int getLengthMinutes() { return lengthMinutes; }
    public void setLengthMinutes(int lengthMinutes) { this.lengthMinutes = lengthMinutes; }

    public int getLane() { return lane; }
    public void setLane(int lane) { this.lane = lane; }

    public int getLaneCount() { return laneCount; }
    public void setLaneCount(int laneCount) { this.laneCount = laneCount; }

    public boolean isCanRemove() { return canRemove; }
    public void setCanRemove(boolean canRemove) { this.canRemove = canRemove; }

    public boolean isCanHandle() { return canHandle; }
    public void setCanHandle(boolean canHandle) { this.canHandle = canHandle; }
}