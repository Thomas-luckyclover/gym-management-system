package com.gms.service;

import com.gms.constant.Messages;
import com.gms.dao.NotificationDAO;
import com.gms.dao.PtScheduleDAO;
import com.gms.dao.PtSessionHandleDAO;
import com.gms.dto.PtHandleDay;
import com.gms.dto.PtHandleSlot;
import com.gms.dto.PtHandleView;
import com.gms.dto.PtSlotDraft;
import com.gms.dto.ServiceResult;
import com.gms.util.DBContext;
import com.gms.util.DateTimeUtil;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * SERVICE HỘP "XỬ LÝ BUỔI" (màn Lịch dạy PT) — /pt/schedule?handle=<SessionId> (UC-30): mở hộp + 3 nút Dời buổi · Chuyển PT · Hủy buổi.
 *
 * AI GỌI SERVICE NÀY?  Chỉ PtScheduleServlet (loadHandle, handleSession). dayLabel còn được PtSaleService dùng lại.
 * SERVICE GỌI AI?      PtSessionHandleDAO (buổi, khung trống, ghi buổi / khung / SessionMoveLogs) · PtScheduleDAO (khóa PT, tạo khung)
 *                      · NotificationDAO (báo khách / PT nhận) · SystemConfigService (P-06, P-17) · PtScheduleService (luật khung như Công bố).
 *
 * BỐ CỤC FILE — mỗi HÀM CHÍNH đứng trước, các hàm phụ của nó nằm ngay bên dưới:
 *   HÀM CHÍNH 1  getHandleForm   mở hộp: đầu hộp + khung dời được + PT nhận thay được  → whySessionLocked, findMoveDays, groupByDay, whyNotAllowed
 *   HÀM CHÍNH 2  reschedule      "Dời buổi" (khung có sẵn hoặc "Giờ khác")            → parseSlotId, parseDate, parseQuarterTime, createSlotForMove,
 *                                                                                       loadTarget, moveTo, slotText
 *   HÀM CHÍNH 3  transfer        "Chuyển PT": giữ giờ, PT khác dạy thay               → transferCommission
 *   HÀM CHÍNH 4  cancel          "Hủy buổi": vẫn tính buổi, PT tự hoàn tiền mặt       → checkCancel, withoutEndDot
 *   DÙNG CHUNG (3 nút)           inTransaction, lockPt, loadOpenSession, notify, oldText, trimToNull, dayLabel, max, min
 *
 * LUẬT QUAN TRỌNG
 *   - Mỗi nút chạy trong 1 transaction (inTransaction): khóa PT → khóa buổi → kiểm → ghi → gửi thông báo. Lỗi → rollback cả lượt.
 *   - Chỉ buổi Scheduled CHƯA bắt đầu mới xử lý được (whySessionLocked → MSG91).
 *   - Khung đích phải còn Free và qua whyNotAllowed (đã qua giờ, ngoài hạn gói, phòng nghỉ, PT nghỉ phép, khách trùng giờ).
 *   - Dời buổi không tính lượt đổi của khách; Chuyển PT tính lại hoa hồng theo tỷ lệ PT nhận; Hủy vẫn tính buổi đã dùng.
 */
public class PtSessionHandleService {
    // Dời buổi: tìm khung trong 14 ngày tới · lý do hủy 5–500 ký tự.
    static final int MOVE_SEARCH_DAYS = 14;
    static final int REASON_MIN = 5;
    static final int REASON_MAX = 500;
    // Giá trị ô radio "Giờ khác" ở tab Dời buổi: PT tự chọn ngày + giờ → hệ thống tạo khung mới rồi dời luôn.
    static final String NEW_SLOT_VALUE = "new";

    private final PtScheduleDAO scheduleDAO = new PtScheduleDAO();
    private final PtSessionHandleDAO handleDAO = new PtSessionHandleDAO();
    private final NotificationDAO notificationDAO = new NotificationDAO();
    private final SystemConfigService configService = new SystemConfigService();

    
    // Đây là mảng ghép để tách mở đóng transaction ra khỏi việc cần làm
    // Vấn đề : 3 nút lặp lại cùng 1 khuôn => dùng chung
    // Định nghĩa interface: TransactionWork là baats cứ thứ gì có hàm run: nhận 1 Connection, trả 1 ServiceResult<LocalDate> và có thể ném exception
    // Hàm khuôn inTransaction nhận interface đó
    @FunctionalInterface
    interface TransactionWork {
        ServiceResult<LocalDate> run(Connection connection) throws SQLException;
    }



    // ============================================================================================================
    // HÀM CHÍNH 1. getHandleForm — mở hộp Xử lý buổi (GET /pt/schedule?handle=<SessionId>)
    //   Ai gọi: PtScheduleServlet.loadHandle  ·  Ra: PtHandleView → JSP biến "handle"
    //   Hàm phụ ngay dưới: whySessionLocked · findMoveDays → groupByDay · whyNotAllowed
    // ============================================================================================================

    /**
     * HÀM CHÍNH 1 — dữ liệu mở hộp Xử lý buổi.
     *   Ai gọi: PtScheduleServlet.loadHandle
     *   Trả về: ok(PtHandleView) | câu lỗi (buổi không còn xử lý được)
     */
    public ServiceResult<PtHandleView> getHandleForm(int accountId, int sessionId, LocalDateTime now) {
        try (Connection connection = DBContext.getConnection()) {
            // B1: tài khoản phải là PT
            Integer ptId = scheduleDAO.findPtEmployeeId(accountId);
            if (ptId == null) {
                return ServiceResult.fail(Messages.MSG07_NO_PERMISSION);
            }

            // B2: lấy buổi, kiểm còn xử lý được (của PT này, chưa bắt đầu)
            PtHandleView session = handleDAO.findSession(
                    connection,
                    sessionId,
                    ptId,
                    false);
            String locked = whySessionLocked(session, now);
            if (locked != null) {
                return ServiceResult.fail(locked);
            }

            // B3: tiêu đề hộp "Thứ 3 · 05/11"
            session.setDayLabel(dayLabel(session.getSlotDate()));

            // B4: tab Dời buổi: khung trống của chính PT trong 14 ngày tới
            findMoveDays(
                    connection,
                    session,
                    ptId,
                    now);

            // B5: tab Chuyển PT: PT khác trống đúng giờ này
            List<PtHandleSlot> transferSlots = new ArrayList<>();
            for (PtHandleSlot slot : handleDAO.findTransferSlots(connection, session, ptId)) {
                if (whyNotAllowed(session, slot, now) == null) {
                    transferSlots.add(slot);
                }
            }
            session.getTransfer().setSlots(transferSlots);

            return ServiceResult.ok(session);

        } catch (SQLException exception) {
            exception.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /**
     * HÀM PHỤ (getHandleForm B2, loadOpenSession) — buổi còn xử lý được không: không tìm thấy → "Không tìm thấy…"; không Scheduled hoặc đã bắt đầu → MSG91. null = được.
     */
    static String whySessionLocked(PtHandleView session, LocalDateTime now) {
        if (session == null) {
            return "Không tìm thấy buổi này trong lịch của bạn.";
        }
        boolean scheduled = "Scheduled".equals(session.getSessionStatus());
        boolean started = !session.getSlotDate().atTime(session.getStartTime()).isAfter(now);
        if (!scheduled || started) {
            return Messages.MSG91_SESSION_CANNOT_CHANGE;
        }
        return null;
    }

    /**
     * HÀM PHỤ (getHandleForm B4) — khoảng tìm = từ max(hôm nay, đầu gói) tới min(hôm nay + 13, hạn gói)
     * → PtSessionHandleDAO.findMySlots → lọc → handle.move: days (groupByDay) + slotCount + from + until.
     */
    private void findMoveDays(
            Connection connection,
            PtHandleView session,
            int ptId,
            LocalDateTime now)
            throws SQLException {
        LocalDate from = max(now.toLocalDate(), session.getTermStart());
        LocalDate to = min(now.toLocalDate().plusDays(MOVE_SEARCH_DAYS - 1), session.getTermEnd());
        session.getMove().setFrom(from);
        session.getMove().setUntil(to);
        if (from.isAfter(to)) {
            return;
        }

        List<PtHandleSlot> valid = new ArrayList<>();
        for (PtHandleSlot slot : handleDAO.findMySlots(
                connection,
                session,
                ptId,
                from,
                to)) {
            if (whyNotAllowed(session, slot, now) == null) {
                valid.add(slot);
            }
        }
        session.getMove().setDays(groupByDay(valid));
        session.getMove().setSlotCount(valid.size());
    }

    /**
     * HÀM PHỤ (findMoveDays) — danh sách khung (đã xếp theo ngày) → mỗi ngày 1 PtHandleDay.
     */
    static List<PtHandleDay> groupByDay(List<PtHandleSlot> slots) {
        List<PtHandleDay> days = new ArrayList<>();
        PtHandleDay current = null;

        for (PtHandleSlot slot : slots) {
            boolean newDay = current == null || !current.getDate().equals(slot.getSlotDate());
            if (newDay) {
                current = new PtHandleDay(slot.getSlotDate(), dayLabel(slot.getSlotDate()));
                days.add(current);
            }
            current.getSlots().add(slot);
        }
        return days;
    }

    /**
     * HÀM PHỤ (getHandleForm B5, findMoveDays, loadTarget) — 1 khung có nhận buổi được không; null = được, không thì câu lý do:
     * khung hiện tại · đã qua giờ · ngoài hạn gói · phòng nghỉ · PT có đơn nghỉ · khách có buổi khác trùng giờ.
     */
    static String whyNotAllowed(PtHandleView session, PtHandleSlot slot, LocalDateTime now) {
        if (slot.getTimeSlotId() == session.getTimeSlotId()) {
            return "đây là khung hiện tại của buổi.";
        }
        if (!slot.getSlotDate().atTime(slot.getStartTime()).isAfter(now)) {
            return "khung đã qua giờ bắt đầu.";
        }
        boolean outsideTerm = slot.getSlotDate().isBefore(session.getTermStart())
                || slot.getSlotDate().isAfter(session.getTermEnd());
        if (outsideTerm) {
            return "ngoài hạn gói (đến " + DateTimeUtil.formatDate(session.getTermEnd()) + ").";
        }
        if (slot.isGymClosed()) {
            return "phòng nghỉ ngày này.";
        }
        if (slot.isPtOnLeave()) {
            return "PT có đơn nghỉ trùng ca này.";
        }
        if (slot.isCustomerBusy()) {
            return "khách có buổi khác trùng giờ.";
        }

        return null;
    }



    // ============================================================================================================
    // HÀM CHÍNH 2. reschedule — nút "Dời buổi": sang khung trống khác CỦA CHÍNH PT (có sẵn, hoặc "Giờ khác" tạo khung mới)
    //   Ai gọi: PtScheduleServlet.handleSession (action=reschedule)  ·  Ra: ngày mới của buổi (để quay lại đúng tuần)
    //   Hàm phụ ngay dưới: parseSlotId · parseDate · parseQuarterTime · createSlotForMove · loadTarget · moveTo · slotText
    // ============================================================================================================

    /**
     * HÀM CHÍNH 2 — nút "Dời buổi": sang khung trống khác của chính PT (có sẵn, hoặc "Giờ khác" tạo khung mới).
     *   Ai gọi: PtScheduleServlet.handleSession (action=reschedule)
     *   Trả về: ok(ngày mới của buổi) | câu lỗi
     */
    public ServiceResult<LocalDate> reschedule(
            int accountId,
            int sessionId,
            String slotIdText,
            String newDateText,
            String newStartText,
            String reason,
            LocalDateTime now) {
        // B1: kiểm lựa chọn khung và ghi chú
        // createNew: slotIdText 2 trường hhopwj 1 là String "73" - buổi muốn đổi sang hoặc là String "new"
        boolean createNew = NEW_SLOT_VALUE.equals(slotIdText == null ? "" : slotIdText.trim());
        
        Integer slotId = createNew ? null : parseSlotId(slotIdText);
        if (!createNew && slotId == null) {
            return ServiceResult.fail(Messages.MSG313_PT_NEW_SLOT_REQUIRED);
        }
        
        
        // Nếu là "new" => parseDate(newDateText)
        // nếu là "new" => parseQuarterTime(newStartTime)
        LocalDate newDate = createNew ? parseDate(newDateText) : null;
        LocalTime newStart = createNew ? parseQuarterTime(newStartText) : null;
        if (createNew && (newDate == null || newStart == null)) {
            return ServiceResult.fail(Messages.MSG336_PT_NEW_SLOT_TIME_REQUIRED);
        }
        
        // Lí do: < max, ko cần < min do ko bắt buộc - nghiệp vụ: PT có thể ghi chú tối đa 500 kí tự
        String note = trimToNull(reason);
        if (note != null && note.length() > REASON_MAX) {
            return ServiceResult.fail(String.format(Messages.MSG314_NOTE_MAX_LENGTH, REASON_MAX));
        }

        
        // Mở Transaction
        return inTransaction(connection -> {
            // B2: khóa PT + khóa buổi
            int ptId = lockPt(connection, accountId);
            PtHandleView session = loadOpenSession(
                    connection,
                    sessionId,
                    ptId,
                    now);

            // B3: chọn "Giờ khác" → tạo khung mới làm khung đích
            // Nếu "new" => create slot mới, ko thì lấy slotId đã có sẵn
            int targetSlotId = createNew
                    ? createSlotForMove(
                            connection,
                            session,
                            ptId,
                            newDate,
                            newStart,
                            now)
                    : slotId;

            // B4: khóa khung đích, kiểm nhận được buổi không
            PtHandleSlot target = loadTarget(
                    connection,
                    session,
                    targetSlotId,
                    now);
            if (target.getPtEmployeeId() != ptId) {
                throw new IllegalArgumentException(Messages.MSG103_SLOT_ALREADY_TAKEN);
            }

            // B5: dời buổi sang khung mới. Sau đó trả khung cũ
            moveTo(
                    connection,
                    session,
                    target,
                    session.getCommissionAmount(),
                    "PtReschedule",
                    accountId,
                    note,
                    now);

            // B6: báo khách
            String newTime = slotText(target);
            String content = "Buổi PT " + oldText(session) + " được " + session.getPtName() + " dời sang " + newTime + "."
                    + (note == null ? "" : " Ghi chú: " + note);
            notify(
                    connection,
                    handleDAO.findCustomerAccounts(connection, session),
                    "PT dời buổi tập",
                    content);

            return ServiceResult.ok(target.getSlotDate(), String.format(Messages.MSG315_PT_SESSION_MOVED, newTime));
        });
    }


    /** HÀM PHỤ (reschedule B1, transfer B1) — ô timeSlotId → số dương; sai → null. */
    static Integer parseSlotId(String text) {
        try {
            int id = Integer.parseInt(text == null ? "" : text.trim());
            return id > 0 ? id : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    /** HÀM PHỤ (reschedule B1) — "2026-11-12" → LocalDate; trống / sai → null. */
    static LocalDate parseDate(String text) {
        try {
            return text == null || text.isBlank() ? null : LocalDate.parse(text.trim());
        } catch (DateTimeException exception) {
            return null;
        }
    }

    /** HÀM PHỤ (reschedule B1) — "17:30" → LocalTime đúng mốc 15 phút; trống / sai / lệch mốc → null. */
    static LocalTime parseQuarterTime(String text) {
        try {
            LocalTime time = text == null || text.isBlank() ? null : LocalTime.parse(text.trim());
            boolean onGrid = time != null && time.getMinute() % 15 == 0 && time.getSecond() == 0 && time.getNano() == 0;
            return onGrid ? time : null;
        } catch (DateTimeException exception) {
            return null;
        }
    }

    /**
     * HÀM PHỤ (reschedule B2b) — tạo khung Free mới cho chính PT để dời buổi sang ("Giờ khác").
     * Input: ngày + giờ bắt đầu PT chọn. Output: TimeSlotId của khung vừa tạo (hoặc khung đã gỡ cùng giờ được mở lại).
     *
     * B1: ngày phải trong khoảng dời được (giống danh sách khung có sẵn): từ max(hôm nay, đầu gói) tới min(hôm nay + 13, hạn gói).
     * B2: kiểm y như nút Công bố (PtScheduleService.validateDrafts): đã qua giờ · phòng nghỉ / ngoài giờ mở cửa
     *     · trùng ca nghỉ phép · CHỒNG GIỜ khung khác của PT (kể cả khung hiện tại của buổi này).
     * B3: ghi khung (PtScheduleDAO.insertOrReopenSlot — SQL tự chặn chồng giờ lần nữa) rồi đọc lại id.
     * Lỗi → IllegalArgumentException kèm câu báo → inTransaction rollback, trả câu lỗi cho PT.
     */
    
    // Hàm này chỉ tạo khung và trả về id. Việc dời là của moveTo sau đó.
    private int createSlotForMove(
            Connection connection,
            PtHandleView session,
            int ptId,
            LocalDate date,
            LocalTime start,
            LocalDateTime now) throws SQLException {
        // B1: ngày phải trong khoảng dời được
        // 1.1 Ngày sớm nhatas được chọn
        LocalDate from = max(now.toLocalDate(), session.getTermStart());
        // 1.2 Ngày muộn nhất được chọn
        LocalDate to = min(now.toLocalDate().plusDays(MOVE_SEARCH_DAYS - 1), session.getTermEnd());
        //nếu date trước from và sau to thì báo lỗi
        if (date.isBefore(from) || date.isAfter(to)) {
            throw new IllegalArgumentException(String.format(
                    Messages.MSG337_PT_NEW_SLOT_DATE_RANGE,
                    DateTimeUtil.formatDate(from),
                    DateTimeUtil.formatDate(to),
                    MOVE_SEARCH_DAYS));
        }

        // B2: kiểm như nút Công bố (giờ mở cửa, nghỉ phép, chồng giờ)
        int minutes = configService.getInt(SystemConfigService.SESSION_DURATION_MINUTES);
        LocalTime[] hours = PtScheduleService.parseOpeningHours(configService.getString(SystemConfigService.OPENING_HOURS));
        LocalTime end = start.plusMinutes(minutes);
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException(String.format(Messages.MSG338_PT_NEW_SLOT_PAST_MIDNIGHT, minutes));
        }
        
        
        // Tại PtSlotDraft
        PtSlotDraft draft = new PtSlotDraft(date, start, end);
        // validateDraft: cần 6 thứ để kiểm: List of nháp gồm 1 nháp - do hàm đòi List, các khung đã có của PT trong ngày đó, đơn nghỉ của PT trong ngày đó, ngày nghỉ/giờ riêng, giờ mở cửa mặc định, giờ hiện tại
        // ko hợp lệ thì ném lỗi, Ktra xem draft có thỏa mãn các yếu tố ko
        PtScheduleService.validateDrafts(
                List.of(draft),
                scheduleDAO.findSlotsForPublish(
                        connection,
                        ptId,
                        date,
                        date),
                scheduleDAO.findLeaves(
                        connection,
                        ptId,
                        date,
                        date),
                scheduleDAO.findHourExceptions(connection, date, date),
                hours,
                now);

        // B3: ghi khung rồi đọc lại mã khung 
        // 2 câu SQL: 1 câu Withdrawn về lại Free + 1 câu              
        scheduleDAO.insertOrReopenSlot(connection, ptId, draft);
        
        // Sau khi ghi xong, đọc lại rồi trả lại slotId 
        Integer slotId = scheduleDAO.findSlotIdByStart(
                connection,
                ptId,
                date,
                start);
        if (slotId == null) {
            throw new IllegalStateException("Không đọc lại được khung vừa tạo");
        }
        return slotId;
    }

    /**
     * HÀM PHỤ (Dời / Chuyển B3) — PtSessionHandleDAO.findSlotById KHÓA khung đích; không còn Free → MSG103; whyNotAllowed → câu lỗi.
     */
    private PtHandleSlot loadTarget(
            Connection connection,
            PtHandleView session,
            int slotId,
            LocalDateTime now)
            throws SQLException {
        PtHandleSlot target = handleDAO.findSlotById(connection, session, slotId);
        if (target == null) {
            throw new IllegalArgumentException(Messages.MSG103_SLOT_ALREADY_TAKEN);
        }
        String why = whyNotAllowed(session, target, now);
        if (why != null) {
            throw new IllegalArgumentException(slotText(target) + ": " + why);
        }
        return target;
    }

    /**
     * HÀM PHỤ (Dời / Chuyển B4) — 4 lệnh ghi:
     */
    private void moveTo(
            Connection connection,
            PtHandleView session,
            PtHandleSlot target,
            int commission,
            String moveType,
            int accountId,
            String reason,
            LocalDateTime now) throws SQLException {
        
        
        // B1: lấy khung đích (vừa bị người khác lấy → báo lỗi), đổi free về booked
        // Nếu ko lấy được
        if (!handleDAO.bookSlot(connection, target.getTimeSlotId())) {
            throw new IllegalArgumentException(Messages.MSG103_SLOT_ALREADY_TAKEN);
        }

        // B2: đổi khung, PT, hoa hồng của buổi
        // Nếu ko đổi được
        if (!handleDAO.moveSession(
                connection,
                session.getSessionId(),
                target.getTimeSlotId(),
                target.getPtEmployeeId(),
                commission)) {
            throw new IllegalArgumentException(Messages.MSG91_SESSION_CANNOT_CHANGE);
        }

        // B3: trả khung cũ về trống
        handleDAO.releaseSlot(connection, session.getTimeSlotId());

        // B4: ghi lịch sử dời
        handleDAO.insertMoveLog(
                connection,
                session,
                target,
                moveType,
                accountId,
                reason,
                now);
    }

    /** HÀM PHỤ (reschedule, loadTarget) — "Thứ 3 05/11/2026 18:00–19:30" cho câu thông báo / lỗi. */
    // PtHandleSlot { SlotDate = 2026-11-05, StartTime = 18:00, EndTime = 19:30 }
    //                ↓ slotText
    // "Thứ 5 05/11/2026 18:00–19:30"
    private static String slotText(PtHandleSlot slot) {
        return dayLabel(slot.getSlotDate()).replace(" · ", " ") + "/" + slot.getSlotDate().getYear() + " "
                + DateTimeUtil.formatTime(slot.getStartTime()) + "–" + DateTimeUtil.formatTime(slot.getEndTime());
    }



    // ============================================================================================================
    // HÀM CHÍNH 3. transfer — nút "Chuyển PT": giữ ngày giờ, PT khác dạy thay
    //   Ai gọi: PtScheduleServlet.handleSession (action=transfer)  ·  Dùng lại: loadTarget, moveTo (khối HÀM CHÍNH 2)
    //   Hàm phụ ngay dưới: transferCommission
    // ============================================================================================================

    /**
     * HÀM CHÍNH 3 — nút "Chuyển PT": giữ ngày giờ, PT khác dạy thay.
     *   Ai gọi: PtScheduleServlet.handleSession (action=transfer)
     *   Trả về: ok(ngày buổi) | câu lỗi
     */
    public ServiceResult<LocalDate> transfer(
            int accountId,
            int sessionId,
            String slotIdText,
            LocalDateTime now) {
        // B1: chưa chọn PT → báo lỗi
        Integer slotId = parseSlotId(slotIdText);
        if (slotId == null) {
            return ServiceResult.fail(Messages.MSG316_PT_TARGET_REQUIRED);
        }

        
        // return inTransaction(  connection -> { ...các bước B2, B3, B4, B5... }  ); => connection là tham số duy nhất
        // transfer không tự chạy khối { … }. Nó chỉ đưa khối đó cho inTransaction. Bên trong, inTransaction mới gọi work.run() để chạy khối
        return inTransaction(connection -> {
            // B2: khóa PT + khóa buổi
            int ptId = lockPt(connection, accountId);
            // hàm đọc và khóa buổi
            // Sau đó hàm ktra xem buổi đó còn xử lý đc ko
            // 
            PtHandleView session = loadOpenSession( // Nó nghĩa là buổi còn “mở”, tức còn xử lý được. Tên hàm đọc là: “lấy buổi, với điều kiện buổi còn mở để xử lý”.
                    connection,
                    sessionId,
                    ptId,
                    now);

            // B3: khung đích phải của PT khác, đúng ngày giờ buổi
            PtHandleSlot target = loadTarget(
                    connection,
                    session,
                    slotId,
                    now);
            
            
            boolean samePt = target.getPtEmployeeId() == ptId;
            boolean sameTime = target.getSlotDate().equals(session.getSlotDate())
                    && target.getStartTime().equals(session.getStartTime())
                    && target.getEndTime().equals(session.getEndTime());
            if (samePt || !sameTime) {
                throw new IllegalArgumentException(Messages.MSG103_SLOT_ALREADY_TAKEN);
            }

            // B4: tính lại hoa hồng cho PT nhận rồi chuyển buổi
            int commission = transferCommission(session.getSessionValue(), target.getCommissionRate());
            moveTo(
                    connection,
                    session,
                    target,
                    commission,
                    "PtTransfer",
                    accountId,
                    null,
                    now);

            // B5: báo khách + báo PT nhận
            String when = oldText(session);
            notify(
                    connection,
                    handleDAO.findCustomerAccounts(connection, session),
                    "Đổi PT dạy buổi tập",
                    "Buổi PT " + when + " sẽ do " + target.getPtName() + " dạy thay " + session.getPtName() + ". Giờ tập giữ nguyên.");
            Integer newPtAccount = notificationDAO.findActiveAccountIdByEmployeeId(connection, target.getPtEmployeeId());
            if (newPtAccount != null) {
                notify(
                        connection,
                        List.of(newPtAccount),
                        "Bạn nhận dạy thay 1 buổi",
                        session.getPtName() + " chuyển cho bạn buổi " + when + " với khách " + session.getCustomerNames() + ".");
            }

            return ServiceResult.ok(session.getSlotDate(), String.format(Messages.MSG317_PT_SESSION_TRANSFERRED, when, target.getPtName()));
        });
    }

    /**
     * HÀM PHỤ (transfer B4) — giá trị buổi × tỷ lệ % của PT nhận, làm tròn.
     */
    static int transferCommission(int sessionValue, int ratePercent) {
        return (int) Math.round(sessionValue * ratePercent / 100.0);
    }



    // ============================================================================================================
    // HÀM CHÍNH 4. cancel — nút "Hủy buổi": buổi vẫn tính đã dùng, PT tự hoàn tiền mặt cho khách
    //   Ai gọi: PtScheduleServlet.handleSession (action=cancel)
    //   Hàm phụ ngay dưới: checkCancel · withoutEndDot
    // ============================================================================================================

    /**
     * HÀM CHÍNH 4 — nút "Hủy buổi": buổi vẫn tính đã dùng, PT tự hoàn tiền mặt cho khách.
     *   Ai gọi: PtScheduleServlet.handleSession (action=cancel)
     *   Trả về: ok(ngày buổi) | câu lỗi
     */
    public ServiceResult<LocalDate> cancel(
            int accountId,
            int sessionId,
            String reason,
            boolean refundAgreed,
            LocalDateTime now) {
        // B1: kiểm lý do + ô tích đã thỏa thuận hoàn tiền
        String note = trimToNull(reason);
        String invalid = checkCancel(note, refundAgreed);
        if (invalid != null) {
            return ServiceResult.fail(invalid);
        }

        return inTransaction(connection -> {
            // B2: khóa PT + khóa buổi
            int ptId = lockPt(connection, accountId);
            PtHandleView session = loadOpenSession(
                    connection,
                    sessionId,
                    ptId,
                    now);

            // B3: hủy buổi, trả khung về trống
            if (!handleDAO.cancelByPt(
                    connection,
                    sessionId,
                    note,
                    accountId,
                    now)) {
                throw new IllegalArgumentException(Messages.MSG91_SESSION_CANNOT_CHANGE);
            }
            handleDAO.releaseSlot(connection, session.getTimeSlotId());

            // B4: báo khách kèm lý do
            String when = oldText(session);
            notify(
                    connection,
                    handleDAO.findCustomerAccounts(connection, session),
                    "PT hủy buổi tập",
                    "Buổi PT " + when + " đã bị " + session.getPtName() + " hủy. Lý do: " + withoutEndDot(note) + ". Khoản hoàn tiền mặt thực hiện như bạn đã thỏa thuận với PT.");

            return ServiceResult.ok(session.getSlotDate(), String.format(Messages.MSG318_PT_SESSION_CANCELLED, when));
        });
    }

    /**
     * HÀM PHỤ (cancel B1) — lý do < 5 / > 500 ký tự, chưa tích đồng ý hoàn tiền → câu lỗi; null = hợp lệ.
     */
    static String checkCancel(String reason, boolean refundAgreed) {
        if (reason == null || reason.length() < REASON_MIN) {
            return "Nhập lý do hủy ít nhất " + REASON_MIN + " ký tự.";
        }
        if (reason.length() > REASON_MAX) {
            return "Lý do hủy tối đa " + REASON_MAX + " ký tự.";
        }
        if (!refundAgreed) {
            return "Xác nhận khách đã đồng ý nhận lại tiền mặt trước khi hủy.";
        }
        return null;
    }

    /** HÀM PHỤ (cancel B4) — bỏ dấu chấm cuối lý do (câu thông báo tự thêm dấu chấm). */
    private static String withoutEndDot(String text) {
        return text.endsWith(".") ? text.substring(0, text.length() - 1) : text;
    }



    // ============================================================================================================
    // DÙNG CHUNG (3 nút) — transaction, khóa PT / buổi, thông báo, chữ ngày giờ, so ngày
    // ============================================================================================================

    /**
     * HÀM PHỤ (dùng chung: reschedule, transfer, cancel) — chạy work trong 1 transaction: xong → commit · lỗi → rollback;
     * IllegalArgumentException → trả đúng câu lỗi · lỗi DB → MSG04.
     */
    private ServiceResult<LocalDate> inTransaction(TransactionWork work) {
        try (Connection connection = DBContext.getConnection()) {
            connection.setAutoCommit(false);
            try {
                ServiceResult<LocalDate> result = work.run(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }

        } catch (IllegalArgumentException exception) {
            return ServiceResult.fail(exception.getMessage());

        } catch (SQLException | IllegalStateException | DateTimeException exception) {
            exception.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /** HÀM PHỤ (dùng chung: 3 nút B2) — PtScheduleDAO.lockPtForPublish: KHÓA dòng PT (cùng khóa với Công bố lịch); không phải PT → MSG07. */
    private int lockPt(Connection connection, int accountId) throws SQLException {
        Integer ptId = scheduleDAO.lockPtForPublish(connection, accountId);
        if (ptId == null) {
            throw new IllegalArgumentException(Messages.MSG07_NO_PERMISSION);
        }
        return ptId;
    }

    /** HÀM PHỤ (dùng chung: 3 nút B2) — PtSessionHandleDAO.findSession(lock = true) KHÓA dòng buổi; không còn xử lý được → lỗi MSG91. */
    private PtHandleView loadOpenSession(
            Connection connection,
            int sessionId,
            int ptId,
            LocalDateTime now)
            throws SQLException {
        PtHandleView session = handleDAO.findSession(
                connection,
                sessionId,
                ptId,
                true);
        String locked = whySessionLocked(session, now);
        if (locked != null) {
            throw new IllegalArgumentException(locked);
        }
        return session;
    }

    /** HÀM PHỤ (dùng chung: 3 nút, bước cuối) — Notifications thêm 1 dòng (nội dung cắt ≤ 500 ký tự) + NotificationRecipients cho từng tài khoản. */
    private void notify(
            Connection connection,
            List<Integer> accountIds,
            String title,
            String content)
            throws SQLException {
        if (accountIds.isEmpty()) {
            return;
        }
        String text = content.length() > 500 ? content.substring(0, 497) + "..." : content;
        int notificationId = notificationDAO.insertSystemNotification(connection, title, text);
        for (int accountId : accountIds) {
            notificationDAO.insertRecipient(connection, notificationId, accountId);
        }
    }

    /** HÀM PHỤ (dùng chung: 3 nút) — "05/11/2026 18:00–19:30" (giờ cũ của buổi) cho câu thông báo. */
    private static String oldText(PtHandleView session) {
        return DateTimeUtil.formatDate(session.getSlotDate()) + " " + DateTimeUtil.formatTime(session.getStartTime())
                + "–" + DateTimeUtil.formatTime(session.getEndTime());
    }

    /** HÀM PHỤ (dùng chung: reschedule, cancel) — chuỗi trống → null, không thì trim. */
    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    /** HÀM PHỤ (dùng chung: getHandleForm, groupByDay, PtSaleService) — "Thứ 3 · 05/11". */
    static String dayLabel(LocalDate date) {
        String name = date.getDayOfWeek() == DayOfWeek.SUNDAY ? "Chủ nhật" : "Thứ " + (date.getDayOfWeek().getValue() + 1);
        return name + " · " + DateTimeUtil.formatDate(date).substring(0, 5);
    }

    /** HÀM PHỤ (findMoveDays, createSlotForMove) — ngày muộn hơn trong 2 ngày. */
    private static LocalDate max(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }

    /** HÀM PHỤ (findMoveDays, createSlotForMove) — ngày sớm hơn trong 2 ngày. */
    private static LocalDate min(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }
}
