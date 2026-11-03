package com.gms.service;

import com.gms.constant.Messages;
import com.gms.dao.NotificationDAO;
import com.gms.dao.PtScheduleDAO;
import com.gms.dao.PtSessionHandleDAO;
import com.gms.dto.PtHandleDay;
import com.gms.dto.PtHandleSlot;
import com.gms.dto.PtHandleView;
import com.gms.dto.ServiceResult;
import com.gms.util.DBContext;
import com.gms.util.DateTimeUtil;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * HỘP "XỬ LÝ BUỔI" (màn Lịch dạy PT) — mở hộp (getHandleForm) + 3 nút: Dời buổi · Chuyển PT · Hủy buổi.
 * Mỗi nút chạy trong 1 transaction (inTransaction): khóa PT → khóa buổi → kiểm → ghi → gửi thông báo. Lỗi → rollback cả lượt.
 */
public class PtSessionHandleService {
    // Dời buổi: tìm khung trong 14 ngày tới · lý do hủy 5–500 ký tự.
    static final int MOVE_SEARCH_DAYS = 14;
    static final int REASON_MIN = 5;
    static final int REASON_MAX = 500;

    private final PtScheduleDAO scheduleDAO = new PtScheduleDAO();
    private final PtSessionHandleDAO handleDAO = new PtSessionHandleDAO();
    private final NotificationDAO notificationDAO = new NotificationDAO();

    @FunctionalInterface
    interface TransactionWork {
        ServiceResult<LocalDate> run(Connection connection) throws SQLException;
    }

    // Dữ liệu mở hộp Xử lý buổi. Trả về: ok(PtHandleView) | fail(câu lỗi → Servlet đặt "handleError").
    public ServiceResult<PtHandleView> getHandleForm(int accountId, int sessionId, LocalDateTime now) {
        try (Connection connection = DBContext.getConnection()) {
            // B1: tài khoản → PT (không phải → MSG07)
            Integer ptId = scheduleDAO.findPtEmployeeId(accountId);
            if (ptId == null) {
                return ServiceResult.fail(Messages.MSG07_NO_PERMISSION);
            }

            // B2: PtSessionHandleDAO.findSession: buổi phải của PT này; không còn Scheduled / đã bắt đầu → MSG91 (whySessionLocked)
            PtHandleView session = handleDAO.findSession(connection, sessionId, ptId, false);
            String locked = whySessionLocked(session, now);
            if (locked != null) {
                return ServiceResult.fail(locked);
            }

            // B3: tiêu đề "Thứ 3 · 05/11"
            session.setDayLabel(dayLabel(session.getSlotDate()));

            // B4: tab Dời buổi: khung trống của chính PT trong 14 ngày tới (và trong hạn gói), lọc whyNotAllowed, gom theo ngày
            findMoveDays(connection, session, ptId, now);

            // B5: tab Chuyển PT: PT khác trống ĐÚNG ngày giờ này (findTransferSlots), lọc whyNotAllowed
            List<PtHandleSlot> transferSlots = new ArrayList<>();
            for (PtHandleSlot slot : handleDAO.findTransferSlots(connection, session, ptId)) {
                if (whyNotAllowed(session, slot, now) == null) {
                    transferSlots.add(slot);
                }
            }
            session.setTransferSlots(transferSlots);

            return ServiceResult.ok(session);

        } catch (SQLException exception) {
            exception.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /**
     * HÀM PHỤ — buổi còn xử lý được không: không tìm thấy → "Không tìm thấy…"; không Scheduled hoặc đã bắt đầu → MSG91. null = được.
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
     * → PtSessionHandleDAO.findMySlots → lọc → moveDays (groupByDay) + moveSlotCount + moveUntil.
     */
    private void findMoveDays(Connection connection, PtHandleView session, int ptId, LocalDateTime now)
            throws SQLException {
        LocalDate from = max(now.toLocalDate(), session.getTermStart());
        LocalDate to = min(now.toLocalDate().plusDays(MOVE_SEARCH_DAYS - 1), session.getTermEnd());
        session.setMoveUntil(to);
        if (from.isAfter(to)) {
            return;
        }

        List<PtHandleSlot> valid = new ArrayList<>();
        for (PtHandleSlot slot : handleDAO.findMySlots(connection, session, ptId, from, to)) {
            if (whyNotAllowed(session, slot, now) == null) {
                valid.add(slot);
            }
        }
        session.setMoveDays(groupByDay(valid));
        session.setMoveSlotCount(valid.size());
    }

    /**
     * HÀM PHỤ (dùng chung) — 1 khung có nhận buổi được không; null = được, không thì câu lý do:
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

    // Nút "Dời buổi": sang khung trống khác CỦA CHÍNH PT. Trả về: ok(ngày mới, MSG315) | fail(câu lỗi).
    public ServiceResult<LocalDate> reschedule(int accountId, int sessionId, String slotIdText, String reason,
            LocalDateTime now) {
        // B1: chưa chọn khung → MSG313 · ghi chú > 500 ký tự → MSG314
        Integer slotId = parseSlotId(slotIdText);
        if (slotId == null) {
            return ServiceResult.fail(Messages.MSG313_PT_NEW_SLOT_REQUIRED);
        }
        String note = trimToNull(reason);
        if (note != null && note.length() > REASON_MAX) {
            return ServiceResult.fail(String.format(Messages.MSG314_NOTE_MAX_LENGTH, REASON_MAX));
        }

        // Mở transaction
        return inTransaction(connection -> {
            // B2: khóa PT (lockPt) + khóa buổi, kiểm còn xử lý được (loadOpenSession)
            int ptId = lockPt(connection, accountId);
            PtHandleView session = loadOpenSession(connection, sessionId, ptId, now);

            // B3: khóa khung đích + kiểm (loadTarget); khung không phải của PT này → MSG103
            PtHandleSlot target = loadTarget(connection, session, slotId, now);
            if (target.getPtEmployeeId() != ptId) {
                throw new IllegalArgumentException(Messages.MSG103_SLOT_ALREADY_TAKEN);
            }

            // B4: dời (moveTo): TimeSlots khung mới Free → Booked · Sessions đổi khung · khung cũ Booked → Free · SessionMoveLogs thêm 1 dòng
            //     (hoa hồng giữ nguyên, không tính lượt đổi của khách)
            moveTo(connection, session, target, session.getCommissionAmount(), "PtReschedule", accountId, note, now);

            // B5: Notifications: báo khách (và người tập cùng) "PT dời buổi tập"
            String newTime = slotText(target);
            String content = "Buổi PT " + oldText(session) + " được " + session.getPtName() + " dời sang " + newTime + "."
                    + (note == null ? "" : " Ghi chú: " + note);
            notify(connection, handleDAO.findCustomerAccounts(connection, session), "PT dời buổi tập", content);

            return ServiceResult.ok(target.getSlotDate(), String.format(Messages.MSG315_PT_SESSION_MOVED, newTime));
        });
    }

    /**
     * HÀM PHỤ (3 nút B2) — PtSessionHandleDAO.findSession(lock = true) KHÓA dòng buổi; không còn xử lý được → lỗi MSG91.
     */
    private PtHandleView loadOpenSession(Connection connection, int sessionId, int ptId, LocalDateTime now)
            throws SQLException {
        PtHandleView session = handleDAO.findSession(connection, sessionId, ptId, true);
        String locked = whySessionLocked(session, now);
        if (locked != null) {
            throw new IllegalArgumentException(locked);
        }
        return session;
    }

    /**
     * HÀM PHỤ (Dời / Chuyển B3) — PtSessionHandleDAO.findSlotById KHÓA khung đích; không còn Free → MSG103; whyNotAllowed → câu lỗi.
     */
    private PtHandleSlot loadTarget(Connection connection, PtHandleView session, int slotId, LocalDateTime now)
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
    private void moveTo(Connection connection, PtHandleView session, PtHandleSlot target, int commission,
            String moveType, int accountId, String reason, LocalDateTime now) throws SQLException {
        // B1: TimeSlots khung đích: Free → Booked (không cập nhật được = vừa bị lấy → MSG103)
        if (!handleDAO.bookSlot(connection, target.getTimeSlotId())) {
            throw new IllegalArgumentException(Messages.MSG103_SLOT_ALREADY_TAKEN);
        }

        // B2: Sessions: đổi TimeSlotId, PtEmployeeId, CommissionAmount (buổi không còn Scheduled → MSG91)
        if (!handleDAO.moveSession(connection, session.getSessionId(), target.getTimeSlotId(),
                target.getPtEmployeeId(), commission)) {
            throw new IllegalArgumentException(Messages.MSG91_SESSION_CANNOT_CHANGE);
        }

        // B3: TimeSlots khung cũ: Booked → Free
        handleDAO.releaseSlot(connection, session.getTimeSlotId());

        // B4: SessionMoveLogs: thêm 1 dòng (loại dời, khung cũ → mới, PT cũ → mới, ai làm, lý do)
        handleDAO.insertMoveLog(connection, session, target, moveType, accountId, reason, now);
    }

    /**
     * HÀM PHỤ — Notifications thêm 1 dòng (nội dung cắt ≤ 500 ký tự) + NotificationRecipients cho từng tài khoản.
     */
    private void notify(Connection connection, List<Integer> accountIds, String title, String content)
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

    // Nút "Chuyển PT": giữ ngày giờ, PT khác dạy thay. Trả về: ok(ngày buổi, MSG317) | fail.
    public ServiceResult<LocalDate> transfer(int accountId, int sessionId, String slotIdText, LocalDateTime now) {
        // B1: chưa chọn PT → MSG316
        Integer slotId = parseSlotId(slotIdText);
        if (slotId == null) {
            return ServiceResult.fail(Messages.MSG316_PT_TARGET_REQUIRED);
        }

        return inTransaction(connection -> {
            // B2: khóa PT + khóa buổi (loadOpenSession)
            int ptId = lockPt(connection, accountId);
            PtHandleView session = loadOpenSession(connection, sessionId, ptId, now);

            // B3: khóa khung đích; phải là PT KHÁC và ĐÚNG ngày giờ của buổi → không thì MSG103
            PtHandleSlot target = loadTarget(connection, session, slotId, now);
            boolean samePt = target.getPtEmployeeId() == ptId;
            boolean sameTime = target.getSlotDate().equals(session.getSlotDate())
                    && target.getStartTime().equals(session.getStartTime())
                    && target.getEndTime().equals(session.getEndTime());
            if (samePt || !sameTime) {
                throw new IllegalArgumentException(Messages.MSG103_SLOT_ALREADY_TAKEN);
            }

            // B4: hoa hồng tính lại theo tỷ lệ của PT nhận → moveTo (ghi như Dời buổi, loại "PtTransfer")
            int commission = transferCommission(session.getSessionValue(), target.getCommissionRate());
            moveTo(connection, session, target, commission, "PtTransfer", accountId, null, now);

            // B5: Notifications: báo khách "Đổi PT dạy buổi tập" + báo PT nhận "Bạn nhận dạy thay 1 buổi"
            String when = oldText(session);
            notify(connection, handleDAO.findCustomerAccounts(connection, session), "Đổi PT dạy buổi tập",
                    "Buổi PT " + when + " sẽ do " + target.getPtName() + " dạy thay " + session.getPtName()
                            + ". Giờ tập giữ nguyên.");
            Integer newPtAccount = notificationDAO.findActiveAccountIdByEmployeeId(connection, target.getPtEmployeeId());
            if (newPtAccount != null) {
                notify(connection, List.of(newPtAccount), "Bạn nhận dạy thay 1 buổi",
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

    // Nút "Hủy buổi": PT hủy, buổi vẫn tính đã dùng, PT tự hoàn tiền mặt cho khách. Trả về: ok(ngày buổi, MSG318) | fail.
    public ServiceResult<LocalDate> cancel(int accountId, int sessionId, String reason, boolean refundAgreed,
            LocalDateTime now) {
        // B1: kiểm lý do 5–500 ký tự + đã tích "Khách đã đồng ý nhận lại tiền mặt" (checkCancel)
        String note = trimToNull(reason);
        String invalid = checkCancel(note, refundAgreed);
        if (invalid != null) {
            return ServiceResult.fail(invalid);
        }

        return inTransaction(connection -> {
            // B2: khóa PT + khóa buổi (loadOpenSession)
            int ptId = lockPt(connection, accountId);
            PtHandleView session = loadOpenSession(connection, sessionId, ptId, now);

            // B3: Sessions: Scheduled → Cancelled (CancelType = ByPt, lý do, thời điểm, người hủy) · TimeSlots khung: Booked → Free
            if (!handleDAO.cancelByPt(connection, sessionId, note, accountId, now)) {
                throw new IllegalArgumentException(Messages.MSG91_SESSION_CANNOT_CHANGE);
            }
            handleDAO.releaseSlot(connection, session.getTimeSlotId());

            // B4: Notifications: báo khách "PT hủy buổi tập" kèm lý do
            String when = oldText(session);
            notify(connection, handleDAO.findCustomerAccounts(connection, session), "PT hủy buổi tập",
                    "Buổi PT " + when + " đã bị " + session.getPtName() + " hủy. Lý do: " + withoutEndDot(note)
                            + ". Khoản hoàn tiền mặt thực hiện như bạn đã thỏa thuận với PT.");

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

    /**
     * HÀM PHỤ (3 nút) — chạy work trong 1 transaction: xong → commit · lỗi → rollback;
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

    /**
     * HÀM PHỤ (3 nút B2) — PtScheduleDAO.lockPtForPublish: KHÓA dòng PT (cùng khóa với Công bố lịch); không phải PT → MSG07.
     */
    private int lockPt(Connection connection, int accountId) throws SQLException {
        Integer ptId = scheduleDAO.lockPtForPublish(connection, accountId);
        if (ptId == null) {
            throw new IllegalArgumentException(Messages.MSG07_NO_PERMISSION);
        }
        return ptId;
    }

    /**
     * HÀM PHỤ — ô timeSlotId → số dương; sai → null.
     */
    static Integer parseSlotId(String text) {
        try {
            int id = Integer.parseInt(text == null ? "" : text.trim());
            return id > 0 ? id : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    /**
     * HÀM PHỤ — "Thứ 3 · 05/11" (PtSaleService cũng dùng).
     */
    static String dayLabel(LocalDate date) {
        String name = date.getDayOfWeek() == DayOfWeek.SUNDAY ? "Chủ nhật" : "Thứ " + (date.getDayOfWeek().getValue() + 1);
        return name + " · " + DateTimeUtil.formatDate(date).substring(0, 5);
    }

    /**
     * HÀM PHỤ — "Thứ 3 05/11/2026 18:00–19:30" cho câu thông báo / lỗi.
     */
    private static String slotText(PtHandleSlot slot) {
        return dayLabel(slot.getSlotDate()).replace(" · ", " ") + "/" + slot.getSlotDate().getYear() + " "
                + DateTimeUtil.formatTime(slot.getStartTime()) + "–" + DateTimeUtil.formatTime(slot.getEndTime());
    }

    /**
     * HÀM PHỤ — "05/11/2026 18:00–19:30" (giờ cũ của buổi).
     */
    private static String oldText(PtHandleView session) {
        return DateTimeUtil.formatDate(session.getSlotDate()) + " " + DateTimeUtil.formatTime(session.getStartTime())
                + "–" + DateTimeUtil.formatTime(session.getEndTime());
    }

    /**
     * HÀM PHỤ — bỏ dấu chấm cuối lý do (câu thông báo tự thêm dấu chấm).
     */
    private static String withoutEndDot(String text) {
        return text.endsWith(".") ? text.substring(0, text.length() - 1) : text;
    }

    /**
     * HÀM PHỤ — chuỗi trống → null, không thì trim.
     */
    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static LocalDate max(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }

    private static LocalDate min(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }
}
