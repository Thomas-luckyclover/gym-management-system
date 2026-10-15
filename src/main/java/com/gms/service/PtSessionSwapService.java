package com.gms.service;

import com.gms.constant.Messages;
import com.gms.dao.NotificationDAO;
import com.gms.dao.PtSessionSwapDAO;
import com.gms.dto.PtSessionSwapInfo;
import com.gms.dto.PtSwapSlotView;
import com.gms.dto.ServiceResult;
import com.gms.util.DBContext;
import com.gms.util.DateTimeUtil;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** UC-38: đọc slot hợp lệ, rồi kiểm tra lại và đổi nguyên tử; không đổi giá trị/hoa hồng buổi. */
public class PtSessionSwapService {
    private final PtSessionSwapDAO swapDAO = new PtSessionSwapDAO();
    private final NotificationDAO notificationDAO = new NotificationDAO();
    private final SystemConfigService configService = new SystemConfigService();

    /** CBR-39: tuần sau chỉ mở từ thứ bảy; không lấy toàn bộ thời hạn gói. */
    static LocalDate lastSelectableDate(LocalDate today) {
        LocalDate sunday = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        return today.getDayOfWeek().getValue()>=6 ? sunday.plusWeeks(1) : sunday;
    }

    /** Quyền sở hữu do DAO giới hạn; đây là chính sách thời gian, loại gói và quota. */
    static String sourceError(PtSessionSwapInfo info, LocalDateTime now) {
        if (info==null) return Messages.MSG07_NO_PERMISSION;
        if (!"PTSingle".equals(info.getSubscriptionType()) && !"PTCouple".equals(info.getSubscriptionType())) {
            return Messages.MSG111_SINGLE_SESSION_CANNOT_SWAP;
        }
        if (!"Scheduled".equals(info.getSessionStatus())
                || !now.isBefore(info.getSlotDate().atTime(info.getStartTime()))) {
            return Messages.MSG91_SESSION_CANNOT_CHANGE;
        }
        if (!"Active".equals(info.getSubscriptionStatus())) return Messages.MSG105_ACTION_OUTSIDE_WINDOW;
        if (info.getSwapsUsed()>=info.getMaxSwaps()) return Messages.MSG90_SWAP_LIMIT_REACHED.formatted(info.getMaxSwaps());
        return null;
    }

    static boolean isAllowedDate(PtSessionSwapInfo info, PtSwapSlotView slot, LocalDateTime now) {
        LocalDate date=slot.getSlotDate();
        return slot.getTimeSlotId()!=info.getTimeSlotId() && date.getDayOfWeek().getValue()>=6
                && !date.isBefore(info.getPackageStartDate()) && !date.isAfter(info.getPackageEndDate())
                && !date.isBefore(now.toLocalDate()) && !date.isAfter(lastSelectableDate(now.toLocalDate()))
                && now.isBefore(date.atTime(slot.getStartTime()))
                && Duration.between(info.getStartTime(),info.getEndTime())
                        .equals(Duration.between(slot.getStartTime(),slot.getEndTime()));
    }

    private boolean isEligible(Connection connection, PtSessionSwapInfo info, PtSwapSlotView slot,
            LocalDateTime now, LocalTime open, LocalTime close) throws SQLException {
        if (!isAllowedDate(info,slot,now) || !swapDAO.isOpen(connection,slot,open,close)
                || swapDAO.hasOverlap(connection,info,slot)) return false;
        return swapDAO.canUseGym(connection,info.getCustomerId(),slot.getSlotDate())
                || (info.getPartnerCustomerId()!=null
                    && swapDAO.canUseGym(connection,info.getPartnerCustomerId(),slot.getSlotDate()));
    }

    /** Chỉ trả slot có thể chọn; rỗng là trạng thái hợp lệ, lỗi không giả thành danh sách rỗng. */
    public ServiceResult<Map<String,Object>> getOptions(int accountId, int sessionId) {
        try (Connection connection=DBContext.getConnection()) {
            LocalDateTime now=DateTimeUtil.now();
            PtSessionSwapInfo info=swapDAO.findOwnedSession(connection,accountId,sessionId,false);
            String error=sourceError(info,now);
            if (error!=null) return ServiceResult.fail(error);
            String[] hours=configService.getString(SystemConfigService.OPENING_HOURS).split("-");
            LocalTime open=LocalTime.parse(hours[0].trim()), close=LocalTime.parse(hours[1].trim());
            List<PtSwapSlotView> available=new ArrayList<>();
            for (PtSwapSlotView slot:swapDAO.findFreeSlots(connection,info.getPtEmployeeId(),
                    now.toLocalDate(),lastSelectableDate(now.toLocalDate()))) {
                if (isEligible(connection,info,slot,now,open,close)) available.add(slot);
            }
            return ServiceResult.ok(Map.of("slots",available,"remainingSwaps",info.getMaxSwaps()-info.getSwapsUsed(),
                    "ptName",info.getPtName(),"originalTimeSlotId",info.getTimeSlotId()));
        } catch (SQLException | IllegalArgumentException exception) {
            exception.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /** Service sở hữu transaction. Lỗi ở bất kỳ bước nào phải hoàn tác cả slot, buổi, quota và log. */
    public ServiceResult<Void> swapSession(int accountId, int sessionId, int targetId, int originalSlotId) {
        try (Connection connection=DBContext.getConnection()) {
            connection.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
            connection.setAutoCommit(false);
            try {
                ServiceResult<Void> result=swapInTransaction(connection,accountId,sessionId,targetId,originalSlotId,null);
                if (result.isSuccess()) connection.commit(); else connection.rollback();
                return result;
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException | RuntimeException exception) {
            exception.printStackTrace();
            return ServiceResult.fail(Messages.MSG22_CONCURRENT_EDIT);
        }
    }

    /** Tách bước ghi để kiểm thử transaction với rollback; caller không được commit khi fail. */
    ServiceResult<Void> swapInTransaction(Connection connection, int accountId, int sessionId,
            int targetId, int originalSlotId, LocalDateTime now) throws SQLException {
        PtSessionSwapInfo info=swapDAO.findOwnedSession(connection,accountId,sessionId,true);
        // Đọc giờ sau khi lấy được khóa, không dùng thời điểm request bắt đầu chờ khóa.
        boolean liveClock = now == null;
        if (liveClock) now = DateTimeUtil.now();
        String error=sourceError(info,now);
        if (error!=null) return ServiceResult.fail(error);
        if (info.getTimeSlotId()!=originalSlotId) return ServiceResult.fail(Messages.MSG22_CONCURRENT_EDIT);
        swapDAO.lockParticipants(connection,info);
        String[] hours=configService.getString(SystemConfigService.OPENING_HOURS).split("-");
        LocalTime open=LocalTime.parse(hours[0].trim()), close=LocalTime.parse(hours[1].trim());
        PtSwapSlotView target=null;
        for (PtSwapSlotView slot:swapDAO.findFreeSlots(connection,info.getPtEmployeeId(),
                now.toLocalDate(),lastSelectableDate(now.toLocalDate()))) {
            if (slot.getTimeSlotId()==targetId) { target=slot; break; }
        }
        if (target==null || !isEligible(connection,info,target,now,open,close)) {
            return ServiceResult.fail(Messages.MSG112_SWAP_SLOT_UNAVAILABLE);
        }
        if (!swapDAO.claimSlot(connection,targetId,info.getPtEmployeeId())) {
            return ServiceResult.fail(Messages.MSG103_SLOT_ALREADY_TAKEN);
        }
        if (liveClock && (!DateTimeUtil.now().isBefore(info.getSlotDate().atTime(info.getStartTime()))
                || !DateTimeUtil.now().isBefore(target.getSlotDate().atTime(target.getStartTime())))) {
            return ServiceResult.fail(Messages.MSG91_SESSION_CANNOT_CHANGE);
        }
        if (!swapDAO.moveSession(connection,info,targetId)
                || !swapDAO.increaseSwaps(connection,info.getSubscriptionId())) {
            return ServiceResult.fail(Messages.MSG103_SLOT_ALREADY_TAKEN);
        }
        swapDAO.releaseOldSlot(connection,info.getTimeSlotId());
        swapDAO.insertMoveLog(connection,info,targetId,accountId);
        String content="Buổi PT " + DateTimeUtil.formatDate(info.getSlotDate()) + " "
                + DateTimeUtil.formatTime(info.getStartTime()) + " đã đổi sang "
                + DateTimeUtil.formatDate(target.getSlotDate()) + " " + DateTimeUtil.formatTime(target.getStartTime())
                + " cùng PT " + info.getPtName() + ". Gói đã sử dụng thêm một lượt đổi.";
        int notificationId=notificationDAO.insertSystemNotification(connection,"Đã đổi lịch buổi PT",content);
        for (int recipient:swapDAO.findRecipients(connection,info)) {
            notificationDAO.insertRecipient(connection,notificationId,recipient);
        }
        return ServiceResult.ok(null,Messages.MSG113_SESSION_SWAPPED);
    }
}
