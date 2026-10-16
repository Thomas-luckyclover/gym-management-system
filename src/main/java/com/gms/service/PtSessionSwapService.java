package com.gms.service;

import com.gms.constant.Messages;
import com.gms.dao.AccountDAO;
import com.gms.dao.MyPtSessionDAO;
import com.gms.dao.NotificationDAO;
import com.gms.dao.PtSessionSwapDAO;
import com.gms.dto.PtSessionInfo;
import com.gms.dto.PtSwapSlotView;
import com.gms.dto.ServiceResult;
import com.gms.entity.Account;
import com.gms.util.DBContext;
import com.gms.util.DateTimeUtil;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * [DOI-BUOI] ĐỔI BUỔI PT (UC-38). Gồm 2 phần:
 *
 *   [DOI-BUOI-GET]  getOptions  : AJAX, chỉ ĐỌC → danh sách slot được chọn, hiện trong hộp thoại
 *   [DOI-BUOI-POST] swapSession : kiểm tra LẠI từ đầu rồi đổi trong 1 TRANSACTION
 *
 * Quy tắc (tìm theo mã luật):
 *   BR-74   buổi lẻ không được đổi
 *   BR-76   chỉ đổi buổi còn Scheduled, CHƯA BẮT ĐẦU
 *   BR-73   còn lượt đổi: SwapsUsed < MaxSwapsSnapshot (giới hạn chốt lúc mua — GBR-04)
 *   BR-75   slot mới: THỨ 7 / CHỦ NHẬT, trong hạn gói
 *   [CHOT 17/10] được chọn BẤT KỲ thứ 7 / CN nào từ hôm nay tới HẾT HẠN GÓI
 *               (bỏ giới hạn "chỉ tuần này" của CBR-39 cho đổi buổi — CBR-39 vẫn áp dụng cho đặt buổi lẻ)
 *   CBR-44  slot PT đã công bố trống (Free)
 *   CBR-51  cùng PT
 *   CBR-61  ngày mới khách vào được gym (gói đôi: ít nhất 1 người)
 *   BR-77   gói đôi: ai trong 2 người cũng được đổi
 *   notes   cùng thời lượng; gym mở cửa; PT và khách không trùng buổi khác
 */
public class PtSessionSwapService {

    private final PtSessionSwapDAO swapDAO = new PtSessionSwapDAO();
    private final MyPtSessionDAO sessionDAO = new MyPtSessionDAO();
    private final AccountDAO accountDAO = new AccountDAO();
    private final NotificationDAO notificationDAO = new NotificationDAO();
    private final SystemConfigService configService = new SystemConfigService();
    private final MyPtSessionService myPtSessionService = new MyPtSessionService();


    // =====================================================================
    // [DOI-BUOI-GET] Lấy danh sách slot cho hộp thoại "Đổi buổi"
    // =====================================================================

    /**
     * [DOI-BUOI-GET]
     * @return Map gửi về JS dạng JSON:
     *         slots, remainingSwaps, ptName, originalTimeSlotId (slot hiện tại của buổi)
     */
    public ServiceResult<Map<String, Object>> getOptions(int accountId, int sessionId) {
        try (Connection connection = DBContext.getConnection()) {
            LocalDateTime now = DateTimeUtil.now();

            // B1: đọc buổi (không khóa vì chỉ xem)
            PtSessionInfo info = sessionDAO.findSessionInfo(connection, sessionId, false);

            // B2: buổi có phải của mình và còn được đổi không?
            String error = checkCanSwap(info, accountId, now);
            if (error != null) {
                return ServiceResult.fail(error);
            }

            // B3: lọc ra các slot hợp lệ
            List<PtSwapSlotView> slots = findValidSlots(connection, info, now);

            // B4: đóng gói kết quả cho JS
            Map<String, Object> data = new HashMap<>();
            data.put("slots", slots);
            data.put("remainingSwaps", info.getMaxSwaps() - info.getSwapsUsed());
            data.put("ptName", info.getPtName());
            data.put("originalTimeSlotId", info.getTimeSlotId());
            return ServiceResult.ok(data);

        } catch (SQLException | RuntimeException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }


    // =====================================================================
    // [DOI-BUOI-POST] Xác nhận đổi buổi — 1 TRANSACTION: đủ hết hoặc không gì cả
    // =====================================================================

    /**
     * [DOI-BUOI-POST]
     * @param targetSlotId   slot mới khách chọn
     * @param originalSlotId slot cũ lúc khách mở hộp thoại (phát hiện buổi đã bị đổi ở tab khác)
     */
    public ServiceResult<Void> swapSession(int accountId, int sessionId, int targetSlotId, int originalSlotId) {
        try (Connection connection = DBContext.getConnection()) {

            // B1: mở transaction mức cao nhất (SERIALIZABLE) để 2 người không chen nhau
            connection.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
            connection.setAutoCommit(false);

            try {
                // B2: làm tất cả các bước đổi
                ServiceResult<Void> result = doSwap(connection, accountId, sessionId, targetSlotId, originalSlotId);

                // B3: thành công → COMMIT (lưu thật) ; thất bại → ROLLBACK (hoàn tác mọi bước)
                if (result.isSuccess()) {
                    connection.commit();
                } else {
                    connection.rollback();
                }
                return result;

            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }

        } catch (SQLException | RuntimeException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG22_CONCURRENT_EDIT);
        }
    }

    /** [DOI-BUOI-POST] 10 bước đổi buổi, chạy TRONG transaction. Fail ở đâu thì swapSession rollback ở đó. */
    private ServiceResult<Void> doSwap(Connection connection, int accountId, int sessionId,
            int targetSlotId, int originalSlotId) throws SQLException {

        // Bước 1: đọc buổi + KHÓA dòng buổi và gói (người khác phải chờ mình xong)
        PtSessionInfo info = sessionDAO.findSessionInfo(connection, sessionId, true);
        LocalDateTime now = DateTimeUtil.now();   // lấy giờ SAU khi có khóa

        // Bước 2: kiểm tra LẠI (không tin kết quả lúc mở hộp thoại)
        String error = checkCanSwap(info, accountId, now);
        if (error != null) {
            return ServiceResult.fail(error);
        }

        // Bước 3: buổi vẫn ở slot cũ? (tab khác / người tập cùng có thể vừa đổi rồi)
        if (info.getTimeSlotId() != originalSlotId) {
            return ServiceResult.fail(Messages.MSG22_CONCURRENT_EDIT);
        }

        // Bước 4: khóa hồ sơ khách (chặn 2 lần đổi khác gói xếp trùng giờ cho cùng khách)
        swapDAO.lockCustomers(connection, info.getCustomerId(), info.getPartnerCustomerId());

        // Bước 5: slot mới có nằm trong danh sách hợp lệ không?
        PtSwapSlotView target = null;
        for (PtSwapSlotView slot : findValidSlots(connection, info, now)) {
            if (slot.getTimeSlotId() == targetSlotId) {
                target = slot;
                break;
            }
        }
        if (target == null) {
            return ServiceResult.fail(Messages.MSG112_SWAP_SLOT_UNAVAILABLE);
        }

        // Bước 6: giữ slot mới (Free → Booked). Người khác lấy trước → false
        if (!swapDAO.claimSlot(connection, targetSlotId, info.getPtEmployeeId())) {
            return ServiceResult.fail(Messages.MSG103_SLOT_ALREADY_TAKEN);
        }

        // Bước 7: chuyển buổi sang slot mới + trừ 1 lượt đổi
        boolean moved = swapDAO.moveSession(connection, sessionId, info.getTimeSlotId(), targetSlotId);
        boolean counted = moved && swapDAO.increaseSwapsUsed(connection, info.getSubscriptionId());
        if (!moved || !counted) {
            return ServiceResult.fail(Messages.MSG103_SLOT_ALREADY_TAKEN);
        }

        // Bước 8: trả slot cũ về Free
        swapDAO.releaseOldSlot(connection, info.getTimeSlotId());

        // Bước 9: [AUDIT] ghi nhật ký
        swapDAO.insertMoveLog(connection, info, targetSlotId, accountId);
        swapDAO.insertAuditLog(connection, info, targetSlotId, accountId);

        // Bước 10: [THONG-BAO] báo cho khách, người tập cùng và PT (CBR-46)
        notifySwap(connection, info, target);

        return ServiceResult.ok(null, Messages.MSG113_SESSION_SWAPPED);
    }


    // =====================================================================
    // [DOI-BUOI] KIỂM TRA BUỔI CŨ
    // =====================================================================

    /** [DOI-BUOI][QUYEN] Buổi này có phải của khách và còn được đổi không? null = được đổi. */
    private String checkCanSwap(PtSessionInfo info, int accountId, LocalDateTime now) throws SQLException {
        if (!isOwner(info, accountId)) {
            return Messages.MSG07_NO_PERMISSION;
        }
        return checkSourceSession(info, now);
    }

    /**
     * [QUYEN] Quyền sở hữu: buổi có thật, đơn đã Paid, PT còn làm việc,
     *         khách là người đứng tên / người đi kèm (BR-77),
     *         và khách không phải chính PT dạy buổi đó.
     */
    private boolean isOwner(PtSessionInfo info, int accountId) throws SQLException {
        if (info == null || !"Paid".equals(info.getOrderStatus()) || !"Working".equals(info.getPtStatus())) {
            return false;
        }

        Integer customerId = myPtSessionService.findCustomerId(accountId);
        if (customerId == null || !MyPtSessionService.isMySession(info, customerId)) {
            return false;
        }

        Account account = accountDAO.findById(accountId);
        return account.getPersonId() != info.getPtPersonId();
    }

    /**
     * [DOI-BUOI] 4 "CỔNG" của buổi cũ. Trả câu lỗi của cổng đầu tiên bị chặn; null = qua hết.
     *   Cổng 1 (BR-74): chỉ gói PT cá nhân / đôi
     *   Cổng 2 (BR-76): buổi còn Scheduled và CHƯA BẮT ĐẦU
     *   Cổng 3        : gói còn Active
     *   Cổng 4 (BR-73): còn lượt đổi
     */
    static String checkSourceSession(PtSessionInfo info, LocalDateTime now) {
        // Cổng 1
        String type = info.getSubscriptionType();
        if (!"PTSingle".equals(type) && !"PTCouple".equals(type)) {
            return Messages.MSG111_SINGLE_SESSION_CANNOT_SWAP;
        }

        // Cổng 2
        LocalDateTime startsAt = info.getSlotDate().atTime(info.getStartTime());
        if (!"Scheduled".equals(info.getSessionStatus()) || !now.isBefore(startsAt)) {
            return Messages.MSG91_SESSION_CANNOT_CHANGE;
        }

        // Cổng 3
        if (!"Active".equals(info.getSubscriptionStatus())) {
            return Messages.MSG105_ACTION_OUTSIDE_WINDOW;
        }

        // Cổng 4
        if (info.getSwapsUsed() >= info.getMaxSwaps()) {
            return Messages.MSG90_SWAP_LIMIT_REACHED.formatted(info.getMaxSwaps());
        }

        return null;
    }


    // =====================================================================
    // [DOI-BUOI] KIỂM TRA SLOT MỚI
    // =====================================================================

    /**
     * [DOI-BUOI] Lấy slot Free của PT từ HÔM NAY tới HẾT HẠN GÓI, rồi giữ lại slot qua được MỌI điều kiện.
     * [CHOT 17/10] Khách bận tuần này thì đổi sang tuần sau, tháng sau... miễn còn trong hạn gói.
     */
    private List<PtSwapSlotView> findValidSlots(Connection connection, PtSessionInfo info, LocalDateTime now)
            throws SQLException {

        LocalTime[] openingHours = readOpeningHours();
        LocalDate today = now.toLocalDate();

        // CBR-44, CBR-51: slot Free của CHÍNH PT đang dạy buổi này, từ hôm nay tới hết hạn gói
        List<PtSwapSlotView> freeSlots = swapDAO.findFreeSlots(connection, info.getPtEmployeeId(),
                today, info.getPackageEndDate());

        List<PtSwapSlotView> validSlots = new ArrayList<>();
        for (PtSwapSlotView slot : freeSlots) {
            boolean dateTimeOk = isAllowedSlot(info, slot, now);                                   // không cần DB
            boolean everyoneFree = dateTimeOk && isFreeForEveryone(connection, info, slot, openingHours); // cần DB
            if (everyoneFree) {
                validSlots.add(slot);
            }
        }
        return validSlots;
    }

    /** [DOI-BUOI] Điều kiện NGÀY / GIỜ của slot mới (không cần DB). */
    static boolean isAllowedSlot(PtSessionInfo info, PtSwapSlotView slot, LocalDateTime now) {
        LocalDate date = slot.getSlotDate();
        LocalDate today = now.toLocalDate();

        boolean notSameSlot    = slot.getTimeSlotId() != info.getTimeSlotId();
        boolean isWeekend      = date.getDayOfWeek() == DayOfWeek.SATURDAY
                              || date.getDayOfWeek() == DayOfWeek.SUNDAY;                       // BR-75
        boolean inPackageTerm  = !date.isBefore(info.getPackageStartDate())
                              && !date.isAfter(info.getPackageEndDate());                       // BR-75
        boolean notInPast      = !date.isBefore(today);                                         // [CHOT 17/10]
        boolean notStartedYet  = now.isBefore(date.atTime(slot.getStartTime()));
        boolean sameDuration   = Duration.between(info.getStartTime(), info.getEndTime())
                .equals(Duration.between(slot.getStartTime(), slot.getEndTime()));              // DBR-20

        return notSameSlot && isWeekend && inPackageTerm && notInPast && notStartedYet && sameDuration;
    }

    /** [DOI-BUOI] Điều kiện cần HỎI DB: gym mở cửa, PT rảnh, cả 2 khách rảnh, ít nhất 1 người vào được gym. */
    private boolean isFreeForEveryone(Connection connection, PtSessionInfo info, PtSwapSlotView slot,
            LocalTime[] openingHours) throws SQLException {

        int sessionId = info.getSessionId();
        Integer partnerId = info.getPartnerCustomerId();

        // Gym mở cửa giờ đó (P-19 + ngày ngoại lệ)
        if (!swapDAO.isGymOpen(connection, slot, openingHours[0], openingHours[1])) {
            return false;
        }

        // PT không trùng buổi khác
        if (swapDAO.isPtBusy(connection, info.getPtEmployeeId(), sessionId, slot)) {
            return false;
        }

        // Khách (và người đi kèm) không trùng buổi khác
        if (swapDAO.isCustomerBusy(connection, info.getCustomerId(), sessionId, slot)) {
            return false;
        }
        if (partnerId != null && swapDAO.isCustomerBusy(connection, partnerId, sessionId, slot)) {
            return false;
        }

        // CBR-61: gói đôi chỉ cần 1 trong 2 người vào được gym
        boolean holderCanEnter = swapDAO.canEnterGym(connection, info.getCustomerId(), slot.getSlotDate());
        boolean partnerCanEnter = partnerId != null
                && swapDAO.canEnterGym(connection, partnerId, slot.getSlotDate());
        return holderCanEnter || partnerCanEnter;
    }

    /** Giờ mở cửa mặc định từ cấu hình P-19, dạng "06:00-22:00" → [06:00, 22:00]. */
    private LocalTime[] readOpeningHours() throws SQLException {
        String[] parts = configService.getString(SystemConfigService.OPENING_HOURS).split("-");
        return new LocalTime[] {LocalTime.parse(parts[0].trim()), LocalTime.parse(parts[1].trim())};
    }


    // =====================================================================
    // [THONG-BAO]
    // =====================================================================

    /** [THONG-BAO][DOI-BUOI] Báo cho khách đứng tên, người đi kèm và PT (CBR-46). */
    private void notifySwap(Connection connection, PtSessionInfo info, PtSwapSlotView target) throws SQLException {
        String content = "Buổi PT " + DateTimeUtil.formatDate(info.getSlotDate()) + " "
                + DateTimeUtil.formatTime(info.getStartTime()) + " đã đổi sang "
                + DateTimeUtil.formatDate(target.getSlotDate()) + " "
                + DateTimeUtil.formatTime(target.getStartTime())
                + " cùng PT " + info.getPtName() + ". Gói đã sử dụng thêm một lượt đổi.";

        int notificationId = notificationDAO.insertSystemNotification(connection, "Đã đổi lịch buổi PT", content);

        List<Integer> recipients = sessionDAO.findRecipientAccounts(connection,
                info.getCustomerId(), info.getPartnerCustomerId(), info.getPtEmployeeId());
        for (int recipientAccountId : recipients) {
            notificationDAO.insertRecipient(connection, notificationId, recipientAccountId);
        }
    }
}
