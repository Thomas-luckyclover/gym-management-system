package com.gms.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Một nhóm ngày trong khối Lịch 7 ngày.
 * PtDashboardService sẽ tạo DTO này từ danh sách PtSessionView do
 * PtDashboardDAO trả về.
 * Service chuẩn bị ngày, nhãn và gom buổi; Servlet chuyển kết quả cho JSP hiển
 * thị.
 * Ngày không có buổi vẫn có DTO, với danh sách sessions rỗng.
 */
public class PtScheduleDayView {

    // Service: lấy today = DateTimeUtil.today(), rồi tính today.plusDays(i), với i
    // từ 0 đến 6.
    // Không lấy ngày từ một buổi cụ thể, để ngày không có buổi vẫn xuất hiện trong
    // lịch: Thứ Ba: 2 buổi, Thứ Tư: Không có buổi

    // JSP dùng gms:formatDate để hiện dd/MM/yyyy, ví dụ 06/10/2026.
    private final LocalDate slotDate;

    // Service: tính từ slotDate của DTO này và today = DateTimeUtil.today().
    // Nếu slotDate.equals(today) thì chọn “Hôm nay”; còn lại chọn tên thứ từ
    // slotDate.getDayOfWeek().
    // JSP hiện cùng ngày, ví dụ “Hôm nay · 06/10/2026” hoặc “Thứ Tư · 07/10/2026”.
    private final String dayLabel;

    // Service: tính bằng slotDate.equals(today), với today = DateTimeUtil.today().
    // Dùng slotDate của DTO ngày này, không lấy trạng thái hay giờ của
    // PtSessionView.
    // JSP dùng để tô nổi bật nhóm hôm nay; không in true/false ra màn hình.
    private final boolean isToday;

    // Service: duyệt danh sách PtSessionView do PtDashboardDAO.findSessions(...)
    // trả về.
    // Chọn các buổi có session.getSlotDate().equals(slotDate), với slotDate là ngày
    // của DTO này.
    // Giữ thứ tự giờ DAO đã sắp; không tính lại startTime, endTime hay status của
    // buổi.
    // Các object buổi do DAO tạo; JSP lặp danh sách để hiện từng buổi.
    // Danh sách rỗng thì hiện “Không có buổi trong ngày”.
    private final List<PtSessionView> sessions;

    /**
     * Nhận thông tin ngày và danh sách buổi đã được Service gom nhóm.
     * 
     * @param slotDate ngày của nhóm trong lịch
     * @param dayLabel nhãn “Hôm nay” hoặc tên thứ do Service chọn
     * @param today    có phải ngày hiện tại theo giờ VN không
     * @param sessions các buổi của ngày, truyền danh sách rỗng nếu không có
     */
    public PtScheduleDayView(LocalDate slotDate, String dayLabel, boolean isToday,
            List<PtSessionView> sessions) {
        // B1: nhận ngày và nhãn hiển thị từ Service.
        this.slotDate = slotDate;
        this.dayLabel = dayLabel;
        this.isToday = isToday;

        // B2: giữ bản sao danh sách, tránh bị sửa sau khi gom.
        this.sessions = List.copyOf(sessions); // List.copyOf(sessions) tạo một danh sách chỉ đọc từ danh sách Service
                                               // truyền vào.
    }

    // ===== JSP đọc kết quả Service đã chuẩn bị; không có setter =====

    /** Ngày để JSP hiển thị bên cạnh nhãn của nhóm. */
    public LocalDate getSlotDate() {
        return slotDate;
    }

    /** Nhãn ngày đã được Service chuẩn bị bằng tiếng Việt. */
    public String getDayLabel() {
        return dayLabel;
    }

    /** JSP đọc thuộc tính today để chọn kiểu hiển thị nổi bật. */
    public boolean isToday() {
        return isToday;
    }

    /** Danh sách buổi để JSP lặp; rỗng nếu ngày không có lịch. */
    public List<PtSessionView> getSessions() {
        return sessions;
    }
}
