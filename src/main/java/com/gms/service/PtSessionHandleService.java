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
 * SERVICE HỘP "XỬ LÝ BUỔI" trên Lịch dạy PT (/pt/schedule?handle=120).
 *
 * Servlet gọi 4 hàm chính, mỗi lần đúng 1 hàm:
 *   getHandleForm  — mở hộp:   đọc buổi → tìm khung Dời buổi + PT nhận Chuyển → PtHandleView
 *   reschedule     — Dời buổi: buổi sang khung trống khác của CHÍNH PT (giữ PT, giữ hoa hồng)
 *   transfer       — Chuyển PT: giữ ngày giờ, đổi sang PT khác đang trống đúng giờ đó (hoa hồng theo PT mới)
 *   cancel         — Hủy buổi: PT nhập lý do + xác nhận khách đồng ý nhận lại tiền mặt
 *
 * Quyết định đã chốt (docs/notes.md, 25/10/2026):
 *   - Khách không còn tự đổi buổi → gộp "Dời buổi" và "Đổi hộ khách" làm một; KHÔNG tính lượt đổi (SwapsUsed).
 *   - Khung cũ sau khi dời / chuyển / hủy → về Còn trống (Free).
 *   - Mở hộp không giữ chỗ: bấm Lưu thì kiểm tra lại TOÀN BỘ trong 1 transaction có khóa dòng.
 *
 * MỤC LỤC
 *   PHẦN 1. MỞ HỘP        getHandleForm → findMoveDays, groupByDay, findTransferSlots
 *   PHẦN 2. GHI           reschedule, transfer, cancel → inTransaction, loadOpenSession, loadTarget, moveTo, notify
 *   PHẦN 3. LUẬT (static) whyNotAllowed, whySessionLocked, checkCancel, parseSlotId, transferCommission
 */
public class PtSessionHandleService {

    /** Dời buổi chỉ tìm khung trong 14 ngày tới (và không quá hạn gói) để danh sách không quá dài. */
    static final int MOVE_SEARCH_DAYS = 14;
    static final int REASON_MIN = 5;
    static final int REASON_MAX = 500;

    private final PtScheduleDAO scheduleDAO = new PtScheduleDAO();          // tìm mã PT, khóa PT
    private final PtSessionHandleDAO handleDAO = new PtSessionHandleDAO();
    private final NotificationDAO notificationDAO = new NotificationDAO();


    // ================================================================================================================
    // PHẦN 1. MỞ HỘP — chỉ ĐỌC, không giữ chỗ
    // ================================================================================================================

    /**
     * HÀM CHÍNH — PtScheduleServlet.doGet gọi (qua loadHandle) khi URL có ?handle=120.
     *
     * Vào:  accountId (session), sessionId (từ ?handle=), now (giờ Việt Nam).
     * Ra:   PtHandleView có đủ lựa chọn; lỗi (không phải buổi của mình, đã bắt đầu…) → câu báo lỗi.
     */
    public ServiceResult<PtHandleView> getHandleForm(int accountId, int sessionId, LocalDateTime now) {
        try (Connection connection = DBContext.getConnection()) {

            // B1: tài khoản → mã PT. Buổi phải là của PT này và còn xử lý được.
            Integer ptId = scheduleDAO.findPtEmployeeId(accountId);
            if (ptId == null) {
                return ServiceResult.fail(Messages.MSG07_NO_PERMISSION);
            }
            PtHandleView session = handleDAO.findSession(connection, sessionId, ptId, false);
            String locked = whySessionLocked(session, now);
            if (locked != null) {
                return ServiceResult.fail(locked);
            }

            // B2: tên ngày cho đầu hộp, ví dụ "Thứ 3 · 27/10".
            session.setDayLabel(dayLabel(session.getSlotDate()));

            // B3: Dời buổi — khung trống hợp lệ của chính PT, gom theo ngày.
            findMoveDays(connection, session, ptId, now);

            // B4: Chuyển PT — PT khác trống đúng giờ này.
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
     * HÀM PHỤ — gọi ở B3 của getHandleForm.
     * Tìm từ hôm nay (không sớm hơn ngày đầu gói) tới 14 ngày sau (không quá ngày cuối gói).
     * Buổi lẻ (SingleSession) có hạn gói đúng 1 ngày → tự nhiên chỉ dời được trong chính ngày đó.
     */
    private void findMoveDays(Connection connection, PtHandleView session, int ptId, LocalDateTime now)
            throws SQLException {
        LocalDate from = max(now.toLocalDate(), session.getTermStart());
        LocalDate to = min(now.toLocalDate().plusDays(MOVE_SEARCH_DAYS - 1), session.getTermEnd());
        session.setMoveUntil(to);
        if (from.isAfter(to)) {
            return;   // gói đã hết hạn: không còn ngày nào để dời
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
     * HÀM PHỤ — gọi ở findMoveDays. Danh sách khung (đã xếp theo ngày giờ) → mỗi ngày 1 PtHandleDay.
     * Ví dụ 3 khung 29/10 06:00, 29/10 18:00, 30/10 08:00 → 2 ngày.
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


    // ================================================================================================================
    // PHẦN 2. GHI — mỗi hàm chạy trong 1 transaction; lỗi ở đâu cũng rollback hết
    // ================================================================================================================

    /**
     * HÀM CHÍNH — PtScheduleServlet.doPost gọi khi action = "reschedule".
     *
     * Vào:  slotIdText = mã khung đích (radio trong hộp), reason = ghi chú cho khách (không bắt buộc).
     * Ra:   thành công → ngày mới của buổi (Servlet mở đúng tuần đó) + câu toast.
     */
    public ServiceResult<LocalDate> reschedule(int accountId, int sessionId, String slotIdText, String reason,
            LocalDateTime now) {
        Integer slotId = parseSlotId(slotIdText);
        if (slotId == null) {
            return ServiceResult.fail("Chọn một khung giờ mới cho buổi này.");
        }
        String note = trimToNull(reason);
        if (note != null && note.length() > REASON_MAX) {
            return ServiceResult.fail("Ghi chú tối đa " + REASON_MAX + " ký tự.");
        }

        return inTransaction(connection -> {
            // B1: khóa PT (xếp hàng với Công bố) + khóa buổi, kiểm tra buổi còn xử lý được.
            int ptId = lockPt(connection, accountId);
            PtHandleView session = loadOpenSession(connection, sessionId, ptId, now);

            // B2: khóa khung đích, kiểm tra lại đúng luật lúc mở hộp. Phải là khung của CHÍNH PT.
            PtHandleSlot target = loadTarget(connection, session, slotId, now);
            if (target.getPtEmployeeId() != ptId) {
                throw new IllegalArgumentException(Messages.MSG103_SLOT_ALREADY_TAKEN);
            }

            // B3: ghi: khung mới Booked, buổi sang khung mới (giữ hoa hồng), khung cũ Free, ghi log.
            moveTo(connection, session, target, session.getCommissionAmount(), "PtReschedule", accountId, note, now);

            // B4: báo khách.
            String newTime = slotText(target);
            String content = "Buổi PT " + oldText(session) + " được " + session.getPtName() + " dời sang " + newTime + "."
                    + (note == null ? "" : " Ghi chú: " + note);
            notify(connection, handleDAO.findCustomerAccounts(connection, session), "PT dời buổi tập", content);

            return ServiceResult.ok(target.getSlotDate(), "Đã dời buổi sang " + newTime + ".");
        });
    }

    /**
     * HÀM CHÍNH — PtScheduleServlet.doPost gọi khi action = "transfer".
     * slotIdText = khung của PT nhận (cùng ngày giờ). Hoa hồng tính lại theo tỷ lệ của PT nhận.
     */
    public ServiceResult<LocalDate> transfer(int accountId, int sessionId, String slotIdText, LocalDateTime now) {
        Integer slotId = parseSlotId(slotIdText);
        if (slotId == null) {
            return ServiceResult.fail("Chọn PT nhận buổi này.");
        }

        return inTransaction(connection -> {
            // B1: khóa PT + buổi.
            int ptId = lockPt(connection, accountId);
            PtHandleView session = loadOpenSession(connection, sessionId, ptId, now);

            // B2: khung đích phải của PT KHÁC và trùng đúng ngày + giờ của buổi.
            PtHandleSlot target = loadTarget(connection, session, slotId, now);
            boolean samePt = target.getPtEmployeeId() == ptId;
            boolean sameTime = target.getSlotDate().equals(session.getSlotDate())
                    && target.getStartTime().equals(session.getStartTime())
                    && target.getEndTime().equals(session.getEndTime());
            if (samePt || !sameTime) {
                throw new IllegalArgumentException(Messages.MSG103_SLOT_ALREADY_TAKEN);
            }

            // B3: ghi với hoa hồng mới (DBR-58: tỷ lệ của PT dạy × giá trị buổi).
            int commission = transferCommission(session.getSessionValue(), target.getCommissionRate());
            moveTo(connection, session, target, commission, "PtTransfer", accountId, null, now);

            // B4: báo khách + PT nhận.
            String when = oldText(session);
            notify(connection, handleDAO.findCustomerAccounts(connection, session), "Đổi PT dạy buổi tập",
                    "Buổi PT " + when + " sẽ do " + target.getPtName() + " dạy thay " + session.getPtName()
                            + ". Giờ tập giữ nguyên.");
            Integer newPtAccount = notificationDAO.findActiveAccountIdByEmployeeId(connection, target.getPtEmployeeId());
            if (newPtAccount != null) {
                notify(connection, List.of(newPtAccount), "Bạn nhận dạy thay 1 buổi",
                        session.getPtName() + " chuyển cho bạn buổi " + when + " với khách " + session.getCustomerNames() + ".");
            }

            return ServiceResult.ok(session.getSlotDate(), "Đã chuyển buổi " + when + " cho " + target.getPtName() + ".");
        });
    }

    /**
     * HÀM CHÍNH — PtScheduleServlet.doPost gọi khi action = "cancel".
     * reason 5–500 ký tự, refundAgreed phải được tích. Buổi vẫn tính là đã dùng, PT vẫn có hoa hồng.
     */
    public ServiceResult<LocalDate> cancel(int accountId, int sessionId, String reason, boolean refundAgreed,
            LocalDateTime now) {
        String note = trimToNull(reason);
        String invalid = checkCancel(note, refundAgreed);
        if (invalid != null) {
            return ServiceResult.fail(invalid);
        }

        return inTransaction(connection -> {
            // B1: khóa PT + buổi.
            int ptId = lockPt(connection, accountId);
            PtHandleView session = loadOpenSession(connection, sessionId, ptId, now);

            // B2: hủy buổi + trả khung về Còn trống.
            if (!handleDAO.cancelByPt(connection, sessionId, note, accountId, now)) {
                throw new IllegalArgumentException(Messages.MSG91_SESSION_CANNOT_CHANGE);
            }
            handleDAO.releaseSlot(connection, session.getTimeSlotId());

            // B3: báo khách kèm lý do.
            String when = oldText(session);
            notify(connection, handleDAO.findCustomerAccounts(connection, session), "PT hủy buổi tập",
                    "Buổi PT " + when + " đã bị " + session.getPtName() + " hủy. Lý do: " + withoutEndDot(note)
                            + ". Khoản hoàn tiền mặt thực hiện như bạn đã thỏa thuận với PT.");

            return ServiceResult.ok(session.getSlotDate(), "Đã hủy buổi " + when + ".");
        });
    }

    /** Một khối việc chạy trong transaction. Trả kết quả; ném IllegalArgumentException = từ chối có lý do. */
    @FunctionalInterface
    interface TransactionWork {
        ServiceResult<LocalDate> run(Connection connection) throws SQLException;
    }

    /**
     * HÀM PHỤ — 3 hàm ghi gọi. Mở transaction → chạy work → commit; lỗi → rollback.
     * IllegalArgumentException mang câu cho PT đọc (MSG103, MSG91…); lỗi khác → câu chung MSG04.
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

    /** HÀM PHỤ — B1 của 3 hàm ghi. Khóa hồ sơ PT như lúc Công bố: Lưu và Công bố của cùng PT phải xếp hàng. */
    private int lockPt(Connection connection, int accountId) throws SQLException {
        Integer ptId = scheduleDAO.lockPtForPublish(connection, accountId);
        if (ptId == null) {
            throw new IllegalArgumentException(Messages.MSG07_NO_PERMISSION);
        }
        return ptId;
    }

    /** HÀM PHỤ — B1 của 3 hàm ghi. Đọc buổi CÓ KHÓA rồi kiểm tra lại: đúng PT, còn Scheduled, chưa bắt đầu. */
    private PtHandleView loadOpenSession(Connection connection, int sessionId, int ptId, LocalDateTime now)
            throws SQLException {
        PtHandleView session = handleDAO.findSession(connection, sessionId, ptId, true);
        String locked = whySessionLocked(session, now);
        if (locked != null) {
            throw new IllegalArgumentException(locked);
        }
        return session;
    }

    /** HÀM PHỤ — B2 của Dời / Chuyển. Đọc khung đích CÓ KHÓA; không còn trống hoặc sai luật → từ chối. */
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
     * HÀM PHỤ — B3 của Dời / Chuyển. Thứ tự: lấy khung mới TRƯỚC (thất bại thì chưa đổi gì) → gắn buổi → nhả khung cũ → log.
     */
    private void moveTo(Connection connection, PtHandleView session, PtHandleSlot target, int commission,
            String moveType, int accountId, String reason, LocalDateTime now) throws SQLException {
        if (!handleDAO.bookSlot(connection, target.getTimeSlotId())) {
            throw new IllegalArgumentException(Messages.MSG103_SLOT_ALREADY_TAKEN);
        }
        if (!handleDAO.moveSession(connection, session.getSessionId(), target.getTimeSlotId(),
                target.getPtEmployeeId(), commission)) {
            throw new IllegalArgumentException(Messages.MSG91_SESSION_CANNOT_CHANGE);
        }
        handleDAO.releaseSlot(connection, session.getTimeSlotId());
        handleDAO.insertMoveLog(connection, session, target, moveType, accountId, reason, now);
    }

    /**
     * HÀM PHỤ — B4 của 3 hàm ghi. 1 thông báo hệ thống + 1 dòng người nhận cho mỗi tài khoản.
     * Không có ai có tài khoản (VD hồ sơ phụ thuộc) → không ghi gì. Nội dung dài quá 500 ký tự thì cắt.
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


    // ================================================================================================================
    // PHẦN 3. LUẬT — static, không đọc DB, test trực tiếp được (PtSessionHandleServiceTest)
    // ================================================================================================================

    /**
     * Buổi còn xử lý được không? null = được; còn lại là câu báo cho PT.
     *   không tìm thấy (không phải buổi của PT này) · không còn Scheduled · đã tới giờ bắt đầu → MSG91.
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
     * Khung đích có nhận được buổi không? null = được; còn lại là lý do (in ra khi bấm Lưu bị từ chối).
     * Mở hộp và bấm Lưu dùng CHUNG hàm này nên luật luôn giống nhau.
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
        // Khách thiếu Membership/DayPass ngày đó KHÔNG chặn: buổi đang có cũng có thể thiếu, chặn thì PT kẹt.
        // JSP tô vàng khung đó (slot.hasGymAccess = false); buổi sau khi dời hiện "Cần chú ý", JOB-08 tự nhắc khách.
        return null;
    }

    /** Hủy buổi: lý do 5–500 ký tự và phải tích "khách đồng ý nhận lại tiền mặt". null = hợp lệ. */
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

    /** Mã khung từ form ("812") → 812. Thiếu, chữ, số ≤ 0 → null. */
    static Integer parseSlotId(String text) {
        try {
            int id = Integer.parseInt(text == null ? "" : text.trim());
            return id > 0 ? id : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    /** Hoa hồng khi chuyển PT = giá trị buổi × tỷ lệ PT nhận, làm tròn. VD 223.000 × 70% = 156.100. */
    static int transferCommission(int sessionValue, int ratePercent) {
        return (int) Math.round(sessionValue * ratePercent / 100.0);
    }

    /** "Thứ 3 · 27/10". */
    static String dayLabel(LocalDate date) {
        String name = date.getDayOfWeek() == DayOfWeek.SUNDAY ? "Chủ nhật" : "Thứ " + (date.getDayOfWeek().getValue() + 1);
        return name + " · " + DateTimeUtil.formatDate(date).substring(0, 5);
    }

    /** "Thứ 5 29/10/2026 06:00–07:30" — giờ của khung đích. */
    private static String slotText(PtHandleSlot slot) {
        return dayLabel(slot.getSlotDate()).replace(" · ", " ") + "/" + slot.getSlotDate().getYear() + " "
                + DateTimeUtil.formatTime(slot.getStartTime()) + "–" + DateTimeUtil.formatTime(slot.getEndTime());
    }

    /** "27/10/2026 18:00–19:30" — giờ cũ của buổi. */
    private static String oldText(PtHandleView session) {
        return DateTimeUtil.formatDate(session.getSlotDate()) + " " + DateTimeUtil.formatTime(session.getStartTime())
                + "–" + DateTimeUtil.formatTime(session.getEndTime());
    }

    /** "Ốm." → "Ốm" để câu thông báo không có 2 dấu chấm liền nhau. */
    private static String withoutEndDot(String text) {
        return text.endsWith(".") ? text.substring(0, text.length() - 1) : text;
    }

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
