package com.gms.service;

import com.gms.constant.Messages;
import com.gms.dao.PtScheduleDAO;
import com.gms.dto.HourException;
import com.gms.dto.LeaveInfo;
import com.gms.dto.PtScheduleDay;
import com.gms.dto.PtScheduleItem;
import com.gms.dto.PtScheduleWeek;
import com.gms.dto.PtSlotDraft;
import com.gms.dto.ServiceResult;
import com.gms.entity.Session;
import com.gms.util.DBContext;
import com.gms.util.DateTimeUtil;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Dựng lịch tuần của PT: DAO đọc dữ liệu gốc, Service tính ngày, trạng thái và quyền thao tác.
 * getWeek trả PtScheduleWeek → 7 PtScheduleDay → các PtScheduleItem cho Servlet/JSP.
 * publish kiểm tra toàn bộ nháp rồi công bố trong cùng transaction.
 */
public class PtScheduleService {

    private final PtScheduleDAO scheduleDAO = new PtScheduleDAO(); // Đọc lịch riêng của PT đăng nhập.
    private final SystemConfigService configService = new SystemConfigService(); // P-06 và P-19.

    // ================================================================================================================
    // 1. HÀM CHÍNH — getWeek kiểm tra PT, đọc DAO rồi dựng tuần; cấu hình được đọc ngay dưới
    // ================================================================================================================

    /** Mã tài khoản + ngày xem + giờ VN → tuần lịch đã phân loại; lỗi DB không trả tuần trống. */
    public ServiceResult<PtScheduleWeek> getWeek(int accountId, LocalDate anyDay, LocalDateTime now) {
        try {
            // B1: tài khoản đăng nhập → PT đang làm việc; không nhận mã PT từ URL.
            Integer ptId = scheduleDAO.findPtEmployeeId(accountId);
            if (ptId == null) {
                // Không có hồ sơ PT đang Working thì dừng; không trả một tuần rỗng như thể có quyền xem.
                return ServiceResult.fail(Messages.MSG07_NO_PERMISSION);
            }

            // B2: đọc cấu hình và bốn nhóm dữ liệu; không gọi DAO trong vòng lặp ngày.
            // B2.1: anyDay có thể là bất kỳ ngày nào; truy vấn luôn lấy từ Thứ 2 đến Chủ nhật.
            LocalDate monday = weekStartOf(anyDay);
            LocalDate sunday = monday.plusDays(6);

            // B2.2: P-06 dùng cho khung mới; hours[0] là giờ mở, hours[1] là giờ đóng theo P-19.
            int minutes = configService.getInt(SystemConfigService.SESSION_DURATION_MINUTES);
            LocalTime[] hours = parseOpeningHours(configService.getString(SystemConfigService.OPENING_HOURS));

            // B2.3: lấy toàn bộ buổi, khung Free/Held, đơn nghỉ và giờ ngoại lệ của tuần một lần.
            List<PtScheduleItem> sessions = scheduleDAO.findSessions(ptId, monday, sunday);
            List<PtScheduleItem> openSlots = scheduleDAO.findOpenSlots(ptId, monday, sunday);
            List<LeaveInfo> leaves = scheduleDAO.findLeaves(ptId, monday, sunday);
            List<HourException> exceptions = scheduleDAO.findHourExceptions(monday, sunday);

            // B3: dựng đủ bảy ngày, rồi ghép các khối vào ngày tương ứng.
            PtScheduleWeek week = buildEmptyWeek(monday, now, exceptions, leaves, minutes, hours[0], hours[1]);
            // merge chỉ ghép/sắp xếp; fillWeek mới phân loại từng khối và thêm vào day.items.
            fillWeek(week, merge(sessions, openSlots), leaves, now);
            return ServiceResult.ok(week);
        } catch (SQLException | IllegalStateException | DateTimeException exception) {
            // B4: lỗi DB/cấu hình/ngày giờ → báo lỗi tải; không làm người dùng hiểu nhầm là không có lịch.
            exception.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /** P-19 dạng HH:mm-HH:mm → giờ mở/đóng; sai cấu hình thì báo lỗi, không dùng giờ giả. */
    public static LocalTime[] parseOpeningHours(String value) {
        // B1: tách "05:30-22:00" thành hai phần; -1 giữ cả phần rỗng để phát hiện cấu hình thiếu giờ.
        String[] parts = value.split("-", -1);
        if (parts.length != 2) {
            // Không đúng hai phần mở/đóng thì không thể xác định giới hạn tạo khung.
            throw new IllegalStateException("P-19 không đúng dạng HH:mm-HH:mm");
        }

        // B2: bỏ khoảng trắng rồi đổi sang LocalTime; giờ sai hoặc rỗng sẽ gây lỗi parse.
        LocalTime open = LocalTime.parse(parts[0].trim());
        LocalTime close = LocalTime.parse(parts[1].trim());
        if (!close.isAfter(open)) {
            // Đóng trước hoặc bằng giờ mở đều không hợp lệ; lịch này không dùng giờ mở qua đêm.
            throw new IllegalStateException("P-19 có giờ đóng cửa không sau giờ mở cửa");
        }
        // B3: trả đúng thứ tự để getWeek truyền giờ mặc định vào buildEmptyWeek.
        return new LocalTime[]{open, close};
    }

    // ================================================================================================================
    // 2. DỰNG KHUNG TUẦN — buildEmptyWeek gọi các hàm dựng ngày và nhãn nghỉ ngay dưới
    // ================================================================================================================

    /** Dựng bảy ngày rỗng từ Thứ 2, áp dụng P-19/ngoại lệ và nhãn nghỉ của từng ngày. */
    static PtScheduleWeek buildEmptyWeek(LocalDate monday, LocalDateTime now,
            List<HourException> exceptions, List<LeaveInfo> leaves, int sessionMinutes,
            LocalTime open, LocalTime close) {
        // B1: mốc tuần và cấu hình; DTO chỉ giữ dữ liệu, không tự tính.
        if (sessionMinutes <= 0 || !close.isAfter(open)) {
            // Chặn thời lượng không dương và khoảng mở cửa sai trước khi dựng các ngày.
            throw new IllegalStateException("Cấu hình lịch dạy không hợp lệ");
        }
        PtScheduleWeek week = new PtScheduleWeek();
        week.setWeekStart(monday);
        week.setWeekEnd(monday.plusDays(6));
        week.setPrevWeek(monday.minusWeeks(1));
        week.setNextWeek(monday.plusWeeks(1));
        // So hai ngày Thứ 2 để biết tuần đang xem có phải tuần chứa hôm nay hay không.
        week.setCurrentWeek(monday.equals(weekStartOf(now.toLocalDate())));
        week.setSessionMinutes(sessionMinutes);
        // Mở 05:30 → trục bắt đầu 05:00; đóng 22:30 → trục kết thúc 23:00 để không cắt khung.
        week.setAxisStartHour(open.getHour());
        week.setAxisEndHour(endHour(close));

        // B2: kể cả ngày không có khung vẫn có cột; items phải là list có thể thêm phần tử.
        List<PtScheduleDay> days = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            // B2.1: i = 0 là Thứ 2, i = 6 là Chủ nhật; buildDay áp dụng giờ ngoại lệ của ngày đó.
            PtScheduleDay day = buildDay(monday.plusDays(i), now.toLocalDate(), open, close, exceptions);
            day.setLeaveLabel(buildLeaveLabel(day.getDate(), leaves));
            days.add(day);
            if (!day.isClosedAllDay()) {
                // B2.2: chỉ ngày mở cửa mới có giờ để so; ngày nghỉ cả ngày có thể có giờ null.
                // Ngày mở sớm/đóng muộn hơn P-19 sẽ nới trục chung cho cả tuần.
                week.setAxisStartHour(Math.min(week.getAxisStartHour(), day.getOpenTime().getHour()));
                week.setAxisEndHour(Math.max(week.getAxisEndHour(), endHour(day.getCloseTime())));
            }
        }
        // B3: gắn cả bảy ngày vào tuần; các khung giờ sẽ được fillWeek thêm sau.
        week.setDays(days);
        return week;
    }

    /** Một ngày dùng giờ mặc định; bản ghi ngoại lệ thay giờ hoặc đánh dấu nghỉ cả ngày. */
    private static PtScheduleDay buildDay(LocalDate date, LocalDate today, LocalTime open,
            LocalTime close, List<HourException> exceptions) {
        // B1: ngày, nhãn và giờ mặc định từ P-19.
        PtScheduleDay day = new PtScheduleDay();
        day.setDate(date);
        // Java đánh số Thứ 2 = 1 nên cộng 1 để hiện T2; riêng Chủ nhật dùng nhãn CN.
        day.setDayName(date.getDayOfWeek() == DayOfWeek.SUNDAY ? "CN" : "T" + (date.getDayOfWeek().getValue() + 1));
        day.setToday(date.equals(today));
        day.setPast(date.isBefore(today));
        day.setOpenTime(open);
        day.setCloseTime(close);
        // Mỗi ngày có list riêng để thêm khung; không dùng chung list giữa các ngày.
        day.setItems(new ArrayList<>());

        // B2: ngày nghỉ nhận giờ null; buổi đã sinh vẫn được fillWeek ghép vào để PT xử lý.
        for (HourException exception : exceptions) {
            // Duyệt các ngoại lệ của tuần; chỉ bản ghi có ApplyDate trùng date mới thay giờ ngày này.
            if (date.equals(exception.getApplyDate())) {
                day.setClosedAllDay(exception.isClosedAllDay());
                day.setOpenTime(exception.getOpenTime());
                day.setCloseTime(exception.getCloseTime());
                if (!day.isClosedAllDay() && (day.getOpenTime() == null || day.getCloseTime() == null
                        || !day.getCloseTime().isAfter(day.getOpenTime()))) {
                    // Ngày có mở cửa phải đủ hai giờ đúng thứ tự; ngày nghỉ cả ngày được phép giờ null.
                    throw new IllegalStateException("Giờ ngoại lệ không hợp lệ: " + date);
                }
                // ApplyDate là duy nhất nên tìm đúng bản ghi là dừng, không cần xét ngoại lệ khác.
                break;
            }
        }
        // Không tìm thấy ngoại lệ thì giữ open/close mặc định đã gán ở B1.
        return day;
    }

    /** Ghép nhãn các ca nghỉ còn Pending/Approved; đơn Rejected không ảnh hưởng lịch. */
    static String buildLeaveLabel(LocalDate date, List<LeaveInfo> leaves) {
        // B1: một ngày có thể có đơn ca sáng và ca chiều, nên thu thập nhiều nhãn.
        List<String> labels = new ArrayList<>();
        for (LeaveInfo leave : leaves) {
            // Chỉ lấy đơn của đúng ngày và còn Pending/Approved; đơn khác ngày/Rejected bị bỏ qua.
            if (date.equals(leave.getLeaveDate()) && isActiveLeave(leave)) {
                // B2: đổi mã ca trong DB thành chữ; mã lạ phải báo lỗi, không đoán ca nghỉ.
                String shift = switch (leave.getShiftType()) {
                    case "Morning" -> "ca sáng";
                    case "Afternoon" -> "ca chiều";
                    case "FullDay" -> "cả ngày";
                    default -> throw new IllegalStateException("Ca nghỉ không hợp lệ");
                };
                // isActiveLeave đã lọc nên ở đây chỉ còn Pending hoặc Approved.
                String status = "Pending".equals(leave.getStatus()) ? "chờ duyệt" : "đã duyệt";
                labels.add("Nghỉ phép " + shift + " · " + status);
            }
        }
        // B3: nối hai ca bằng "; "; không có đơn phù hợp thì trả chuỗi rỗng.
        return String.join("; ", labels);
    }

    // ================================================================================================================
    // 3. GHÉP BUỔI VÀ KHUNG — merge sắp xếp; fillWeek đưa từng khối vào đúng ngày
    // ================================================================================================================

    /** Ghép hai list DAO, sắp theo ngày/giờ; không bỏ buổi cũ nếu khung đã được mở lại. */
    static List<PtScheduleItem> merge(List<PtScheduleItem> sessions, List<PtScheduleItem> openSlots) {
        // B1: tạo list mới chứa buổi có khách, rồi thêm Free/Held; không thêm/xóa trên hai list DAO.
        List<PtScheduleItem> items = new ArrayList<>(sessions);
        items.addAll(openSlots);
        // B2: xếp ngày trước, giờ bắt đầu sau; không gộp theo TimeSlotId làm mất lịch sử buổi đã hủy.
        items.sort(Comparator.comparing(PtScheduleItem::getSlotDate).thenComparing(PtScheduleItem::getStartTime));
        return items;
    }

    /** Phân loại từng khối rồi tính vị trí theo trục của tuần; không đổi trạng thái DB. */
    static void fillWeek(PtScheduleWeek week, List<PtScheduleItem> items, List<LeaveInfo> leaves, LocalDateTime now) {
        // B1: mở rộng trục nếu có buổi lịch sử nằm ngoài giờ mở cửa hiện tại.
        for (PtScheduleItem item : items) {
            // Xét hết các khối trước để mọi khối được tính top theo cùng một trục giờ cuối cùng.
            week.setAxisStartHour(Math.min(week.getAxisStartHour(), item.getStartTime().getHour()));
            week.setAxisEndHour(Math.max(week.getAxisEndHour(), endHour(item.getEndTime())));
        }

        // B2: phân loại và thêm vào đúng ngày; 05:00 → 08:30 tương ứng startMinute = 210.
        for (PtScheduleItem item : items) {
            // B2.1: tính nhãn/quyền; đổi vị trí tạm theo trục 05:00 thành trục thực tế của tuần.
            classify(item, leaves, now);
            // toSecondOfDay()/60 = phút từ 00:00; trừ phút đầu trục để JSP đặt khối đúng độ cao.
            item.setStartMinute(item.getStartTime().toSecondOfDay() / 60 - week.getAxisStartHour() * 60);
            for (PtScheduleDay day : week.getDays()) {
                // B2.2: tìm cột có ngày trùng slotDate; không đưa khối sang các ngày khác.
                if (day.getDate().equals(item.getSlotDate())) {
                    day.getItems().add(item);
                    // Mỗi ngày chỉ có một cột nên đã thêm đúng ngày thì dừng vòng tìm ngày.
                    break;
                }
            }
        }
    }

    // ================================================================================================================
    // 4. PHÂN LOẠI KHỐI — classify gọi sessionLabel / isOnLeave để đặt nhãn và quyền
    // ================================================================================================================

    /** Tính kind, past, nhãn và quyền từ dữ liệu gốc; classify không gọi DAO hoặc ghi DB. */
    static void classify(PtScheduleItem item, List<LeaveInfo> leaves, LocalDateTime now) {
        // B1: hết giờ mới là past; tới giờ bắt đầu đã không được gỡ/xử lý.
        LocalDateTime start = LocalDateTime.of(item.getSlotDate(), item.getStartTime());
        LocalDateTime end = LocalDateTime.of(item.getSlotDate(), item.getEndTime());
        // end <= now → đã qua; đặt lại hai quyền về false để không giữ kết quả từ lần phân loại trước.
        item.setPast(!end.isAfter(now));
        item.setCanRemove(false);
        item.setCanHandle(false);
        // Vị trí tạm từ 05:00; fillWeek sẽ tính lại nếu trục tuần mở rộng sang giờ khác.
        item.setStartMinute(item.getStartTime().toSecondOfDay() / 60 - 5 * 60);
        // Độ dài lấy chênh lệch giờ DB, không lấy P-06 vì khung lịch sử có thể khác thời lượng hiện tại.
        item.setLengthMinutes((int) Duration.between(item.getStartTime(), item.getEndTime()).toMinutes());

        // B2: buổi có khách luôn là booked, kể cả CheckedIn/Missed/Cancelled.
        if (item.getSessionId() != null) {
            // Có SessionId thì ưu tiên thông tin buổi, không đổi thành locked theo đơn nghỉ.
            item.setKind("booked");
            item.setStatusLabel(sessionLabel(item, start, end, now));
            // Chỉ buổi Scheduled chưa bắt đầu được mở xử lý; tới đúng giờ bắt đầu cũng không được.
            item.setCanHandle("Scheduled".equals(item.getSessionStatus()) && start.isAfter(now));
        } else if ("Held".equalsIgnoreCase(item.getKind())) {
            // B3: chưa có buổi nhưng khách đang giữ khung để thanh toán; hai quyền vẫn false.
            item.setKind("held");
            item.setStatusLabel("Đang giữ · đơn chờ thanh toán");
        } else if (isOnLeave(item, leaves)) {
            // B4: khung Free trùng ca nghỉ đang chờ/đã duyệt → tạm khóa trên màn, không sửa DB.
            item.setKind("locked");
            item.setStatusLabel("Tạm khóa · đơn nghỉ");
        } else {
            // B5: không có buổi, không Held, không trùng nghỉ → khung trống bình thường.
            item.setKind("free");
            item.setStatusLabel("Còn trống");
            // Khung vẫn là free khi đã qua, nhưng chỉ khung chưa bắt đầu mới hiện quyền Gỡ.
            item.setCanRemove(start.isAfter(now));
        }
    }

    /** Nhãn buổi Scheduled theo thời gian; các trạng thái đã chốt luôn giữ nhãn kết quả. */
    private static String sessionLabel(PtScheduleItem item, LocalDateTime start,
            LocalDateTime end, LocalDateTime now) {
        // B1: đổi trạng thái DB sang enum để phân nhánh; mã không nhận diện được thì báo lỗi tải.
        Session.Status status = Session.Status.fromDbValue(item.getSessionStatus());
        if (status == null) {
            throw new IllegalStateException("Trạng thái buổi không hợp lệ: " + item.getSessionId());
        }
        // B2: chỉ Scheduled cần xét thời gian vì hệ thống có thể chưa chốt kết quả buổi.
        if (status == Session.Status.SCHEDULED) {
            if (!end.isAfter(now)) {
                // Đã tới/qua giờ kết thúc nhưng DB còn Scheduled → đang chờ hệ thống cập nhật.
                return "Đang cập nhật";
            }
            if (!start.isAfter(now)) {
                // Nhánh trước đã loại buổi hết giờ; còn start <= now < end → đang diễn ra.
                return "Đang diễn ra";
            }
            // Còn lại là buổi chưa bắt đầu: cùng ngày hiện Hôm nay, ngày sau hiện Sắp tới.
            return item.getSlotDate().equals(now.toLocalDate()) ? "Hôm nay" : "Sắp tới";
        }
        // B3: kết quả đã chốt giữ đúng nhãn, không bị đổi theo đồng hồ hoặc đơn nghỉ.
        return switch (status) {
            case CHECKED_IN -> "Đã check-in";
            case MISSED -> "Vắng";
            case CANCELLED -> "Đã hủy";
            default -> throw new IllegalStateException("Trạng thái buổi không hợp lệ");
        };
    }

    /** Ca sáng xét giờ bắt đầu < 12:00, ca chiều >= 12:00; chỉ Pending/Approved khóa khung. */
    static boolean isOnLeave(PtScheduleItem item, List<LeaveInfo> leaves) {
        // B1: duyệt đơn nghỉ để tìm ít nhất một đơn còn hiệu lực trùng ngày và ca của khung.
        for (LeaveInfo leave : leaves) {
            if (!item.getSlotDate().equals(leave.getLeaveDate()) || !isActiveLeave(leave)) {
                // continue bỏ riêng đơn khác ngày hoặc đã bị từ chối, vẫn xét những đơn tiếp theo.
                continue;
            }
            // B2: chia ca bằng giờ BẮT ĐẦU; 11:45–13:15 thuộc ca sáng, 12:00–13:30 thuộc ca chiều.
            boolean morning = item.getStartTime().isBefore(LocalTime.NOON);
            if ("FullDay".equals(leave.getShiftType())
                    || ("Morning".equals(leave.getShiftType()) && morning)
                    || ("Afternoon".equals(leave.getShiftType()) && !morning)) {
                // Nghỉ cả ngày hoặc trùng đúng nửa ngày → tìm được đơn khóa khung, trả ngay true.
                return true;
            }
        }
        // B3: duyệt hết vẫn không có đơn phù hợp thì khung không bị khóa bởi nghỉ phép.
        return false;
    }

    /** Đơn chờ duyệt và đã duyệt cùng tạm khóa; trạng thái khác không được dùng. */
    private static boolean isActiveLeave(LeaveInfo leave) {
        // || nghĩa là chỉ cần một trong hai trạng thái; Rejected trả false.
        return "Pending".equals(leave.getStatus()) || "Approved".equals(leave.getStatus());
    }

    // ================================================================================================================
    // 5. CÔNG BỐ — publish giữ transaction, các hàm phụ kiểm tra dữ liệu trước khi DAO ghi
    // ================================================================================================================

    /** Tài khoản + các ngày/giờ nháp → số khung đã công bố; lỗi thì rollback cả lần gửi. */
    public ServiceResult<Integer> publish(int accountId, String[] dates, String[] starts,
            int expectedMinutes, LocalDateTime now) {
        if (dates == null || starts == null || dates.length == 0 || dates.length != starts.length) {
            return ServiceResult.fail("Chọn ít nhất một khung nháp trước khi công bố.");
        }
        try (Connection connection = DBContext.getConnection()) {
            // B1: tất cả kiểm tra và ghi dùng chung transaction, không lưu từng phần.
            connection.setAutoCommit(false);
            try {
                ServiceResult<Integer> result = publishInTransaction(connection, accountId, dates, starts, expectedMinutes, now);
                if (result.isSuccess()) {
                    connection.commit(); // Mọi khung hợp lệ và đã ghi xong thì chốt cả danh sách.
                } else {
                    connection.rollback(); // Lỗi quyền/cấu hình thì không giữ thay đổi nào.
                }
                return result;
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (IllegalArgumentException exception) {
            // B2: lỗi nhập liệu giữ nguyên nháp ở client, không lộ lỗi SQL ra màn hình.
            return ServiceResult.fail(exception.getMessage());
        } catch (SQLException | IllegalStateException | DateTimeException exception) {
            exception.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /** Connection đã tắt autoCommit; caller quyết định commit, test có thể rollback toàn bộ. */
    ServiceResult<Integer> publishInTransaction(Connection connection, int accountId, String[] dates,
            String[] starts, int expectedMinutes, LocalDateTime now) throws SQLException {
        // B1: khóa đúng PT và cấu hình để hai lần công bố không vượt qua kiểm tra trùng.
        Integer ptId = scheduleDAO.lockPtForPublish(connection, accountId);
        if (ptId == null) return ServiceResult.fail(Messages.MSG07_NO_PERMISSION);
        scheduleDAO.lockScheduleConfigs(connection);
        int minutes = configService.getInt(SystemConfigService.SESSION_DURATION_MINUTES);
        LocalTime[] hours = parseOpeningHours(configService.getString(SystemConfigService.OPENING_HOURS));
        if (minutes <= 0 || minutes >= 24 * 60) throw new IllegalStateException("P-06 không hợp lệ");
        if (minutes != expectedMinutes) {
            return ServiceResult.fail("Thời lượng buổi đã thay đổi. Tải lại lịch và kiểm tra nháp trước khi công bố.");
        }

        // B2: Service tính giờ kết thúc; đọc giờ ngoại lệ, nghỉ phép và khung đang có một lần.
        List<PtSlotDraft> drafts = buildDrafts(dates, starts, minutes);
        LocalDate fromDate = drafts.get(0).getSlotDate();
        LocalDate toDate = drafts.get(drafts.size() - 1).getSlotDate();
        List<HourException> exceptions = scheduleDAO.findHourExceptions(connection, fromDate, toDate);
        List<LeaveInfo> leaves = scheduleDAO.findLeaves(connection, ptId, fromDate, toDate);
        List<PtScheduleItem> existing = scheduleDAO.findSlotsForPublish(connection, ptId, fromDate, toDate);
        validateDrafts(drafts, existing, leaves, exceptions, hours, now);

        // B3: chỉ ghi sau khi mọi khung hợp lệ; mở lại Withdrawn đúng giờ hoặc tạo Free mới.
        for (PtSlotDraft draft : drafts) {
            scheduleDAO.insertOrReopenSlot(connection, ptId, draft);
        }
        return ServiceResult.ok(drafts.size(), "Đã công bố " + drafts.size() + " khung giờ.");
    }

    /** Ngày/giờ từ form → DTO nháp xếp theo ngày giờ; không nhận EndTime từ trình duyệt. */
    static List<PtSlotDraft> buildDrafts(String[] dates, String[] starts, int minutes) {
        if (dates == null || starts == null || dates.length == 0 || dates.length != starts.length) {
            throw new IllegalArgumentException("Danh sách ngày/giờ nháp không hợp lệ.");
        }
        List<PtSlotDraft> drafts = new ArrayList<>();
        for (int i = 0; i < dates.length; i++) {
            try {
                // B1: SQL DATE nhận năm 1..9999; giờ bắt đầu phải đúng mốc 15 phút, không có giây lẻ.
                LocalDate date = LocalDate.parse(dates[i] == null ? "" : dates[i]);
                LocalTime start = LocalTime.parse(starts[i] == null ? "" : starts[i]);
                LocalTime end = start.plusMinutes(minutes);
                if (date.getYear() < 1 || date.getYear() > 9999 || start.getMinute() % 15 != 0
                        || start.getSecond() != 0 || start.getNano() != 0 || !end.isAfter(start)) {
                    throw new IllegalArgumentException("Ngày/giờ nháp không hợp lệ; chọn mốc 15 phút, không qua ngày.");
                }
                drafts.add(new PtSlotDraft(date, start, end));
            } catch (DateTimeException exception) {
                throw new IllegalArgumentException("Ngày/giờ nháp không đúng định dạng.");
            }
        }
        // B2: xếp trước để chỉ cần so hai khung liền nhau khi kiểm tra trùng giữa các nháp.
        drafts.sort(Comparator.comparing(PtSlotDraft::getSlotDate).thenComparing(PtSlotDraft::getStartTime));
        return drafts;
    }

    /** Kiểm tra ngày/giờ, phòng nghỉ, đơn nghỉ và giao nhau; lỗi nêu đúng khung cần sửa. */
    static void validateDrafts(List<PtSlotDraft> drafts, List<PtScheduleItem> existing, List<LeaveInfo> leaves,
            List<HourException> exceptions, LocalTime[] hours, LocalDateTime now) {
        PtSlotDraft previous = null;
        for (PtSlotDraft draft : drafts) {
            String label = DateTimeUtil.formatDate(draft.getSlotDate()) + " " + DateTimeUtil.formatTime(draft.getStartTime());
            // B1: ngày hôm nay chỉ nhận giờ chưa bắt đầu; giờ ngoại lệ thay P-19 của đúng ngày.
            if (!draft.getSlotDate().atTime(draft.getStartTime()).isAfter(now)) {
                throw new IllegalArgumentException(label + ": giờ bắt đầu đã qua.");
            }
            PtScheduleDay day = buildDay(draft.getSlotDate(), now.toLocalDate(), hours[0], hours[1], exceptions);
            if (day.isClosedAllDay() || draft.getStartTime().isBefore(day.getOpenTime())
                    || draft.getEndTime().isAfter(day.getCloseTime())) {
                throw new IllegalArgumentException(label + ": phòng nghỉ hoặc khung nằm ngoài giờ mở cửa.");
            }
            PtScheduleItem candidate = new PtScheduleItem();
            candidate.setSlotDate(draft.getSlotDate());
            candidate.setStartTime(draft.getStartTime());
            if (isOnLeave(candidate, leaves)) throw new IllegalArgumentException(label + ": trùng ca nghỉ phép.");

            // B2: khung liền nhau được phép chạm mép, nhưng không giao nhau hoặc lặp lại.
            if (previous != null && previous.getSlotDate().equals(draft.getSlotDate())
                    && previous.getEndTime().isAfter(draft.getStartTime())) {
                throw new IllegalArgumentException(label + ": trùng một khung khác trong danh sách nháp.");
            }
            for (PtScheduleItem slot : existing) {
                if (!slot.getSlotDate().equals(draft.getSlotDate())) continue;
                if ("Withdrawn".equals(slot.getKind())) {
                    // Cùng giờ bắt đầu phải dùng lại bản ghi cũ; không đổi EndTime làm sai lịch sử buổi.
                    if (slot.getStartTime().equals(draft.getStartTime()) && !slot.getEndTime().equals(draft.getEndTime())) {
                        throw new IllegalArgumentException(label + ": khung cũ có thời lượng khác. Chọn giờ bắt đầu khác.");
                    }
                    continue;
                }
                if (draft.getStartTime().isBefore(slot.getEndTime()) && slot.getStartTime().isBefore(draft.getEndTime())) {
                    throw new IllegalArgumentException(label + ": trùng khung đã công bố "
                            + DateTimeUtil.formatTime(slot.getStartTime()) + "–" + DateTimeUtil.formatTime(slot.getEndTime()) + ".");
                }
            }
            previous = draft;
        }
    }

    // ================================================================================================================
    // 6. NGÀY VÀ TRỤC GIỜ DÙNG CHUNG — Servlet và các hàm dựng tuần gọi
    // ================================================================================================================

    /** Đọc ngày URL yyyy-MM-dd; thiếu/sai/tràn mốc tuần thì trả today do Servlet truyền vào. */
    public static LocalDate parseDate(String value, LocalDate today) {
        // B1: chưa chọn ngày thì mở tuần hiện tại.
        if (value == null || value.isBlank()) {
            return today;
        }

        // B2: parse chỉ nhận ngày ISO, ví dụ "2026-10-22"; "abc" hoặc "2026-02-30" gây exception.
        try {
            LocalDate date = LocalDate.parse(value);
            LocalDate weekStart = weekStartOf(date);

            // LocalDate không bị sửa khi cộng/trừ. Hai dòng này chỉ kiểm tra ngày có vượt biên Java không:
            // Thứ 2 tuần trước = -7 ngày; Chủ nhật tuần sau = +7 + 6 = +13 ngày.
            // Không cần giữ kết quả; vượt biên thì vào catch, tránh lỗi 500 ở các nút đổi tuần.
            weekStart.minusWeeks(1);
            weekStart.plusDays(13);

            // Trả ngày đã đọc, không trả weekStart; Servlet tự gọi weekStartOf để lấy Thứ 2.
            return date;
        } catch (DateTimeException exception) {
            // Sai định dạng, ngày không tồn tại hoặc phép tính vượt biên → mở tuần hôm nay.
            return today;
        }
    }

    /** Ngày bất kỳ → Thứ 2 cùng tuần; 22/10/2026 và Chủ nhật 25/10 đều trả 19/10/2026. */
    public static LocalDate weekStartOf(LocalDate date) {
        // with(MONDAY) đưa ngày về Thứ 2 cùng tuần, không thay đổi biến date ban đầu.
        return date.with(DayOfWeek.MONDAY);
    }

    /** Giờ cuối trục làm tròn lên để 22:30 vẫn vẽ được; 22:00 giữ nguyên 22. */
    private static int endHour(LocalTime time) {
        // Có phút/giây lẻ thì cộng thêm một giờ cho trục; đúng đầu giờ thì cộng 0.
        return time.getHour() + (time.getMinute() > 0 || time.getSecond() > 0 ? 1 : 0);
    }

}
