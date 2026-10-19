package com.gms.service;

import com.gms.constant.Messages;
import com.gms.dao.MyPtSessionDAO;
import com.gms.dto.MyBookingView;
import com.gms.dto.MyPtPackageView;
import com.gms.dto.MyPtSessionRow;
import com.gms.dto.ServiceResult;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Màn "Gói của tôi" (/customer/bookings, màn #50) — CHỈ XEM.
 *   Cột trái : gói PT đang tập / đã kết thúc + nhóm "Các buổi lẻ"
 *   Cột phải : gói đang chọn + 5 ô đếm + bảng buổi #1 → #N
 * Dùng lại DAO và cách tính trạng thái của màn "Lịch tập" → 2 màn luôn hiện cùng 1 nhãn.
 */
public class MyBookingService {

    /** Giá trị ?id= khi chọn nhóm "Các buổi lẻ". */
    public static final String SINGLE_KEY = "single";

    private final MyPtSessionDAO sessionDAO = new MyPtSessionDAO();
    private final MyPtSessionService sessionService = new MyPtSessionService();

    /**
     * Dữ liệu cả màn.
     * @param selectedId ?id= trên URL: mã gói, "single", hoặc rỗng/sai → tự chọn gói đầu tiên
     */
    public ServiceResult<MyBookingView> getMyBookings(int accountId, String selectedId, LocalDateTime now) {
        try {
            // B1: tìm khách
            Integer customerId = sessionService.findCustomerId(accountId);
            if (customerId == null) {
                return ServiceResult.fail(Messages.MSG07_NO_PERMISSION);
            }
            MyBookingView view = new MyBookingView();

            // B2: cột trái — gói PT (1 câu SQL), chia Đang tập / Đã kết thúc
            for (MyPtPackageView p : sessionDAO.findMyPackages(customerId, now.toLocalDate())) {
                fillPackage(p, customerId);
                if (p.isActive()) {
                    view.getActivePackages().add(p);
                } else {
                    view.getEndedPackages().add(p);
                }
            }

            // B3: cột trái — các buổi lẻ
            List<MyPtSessionRow> singles = sessionDAO.findSingleSessions(customerId);
            view.setSingleSessionCount(singles.size());

            // B4: chọn cái đang xem (URL hợp lệ → theo URL; không thì gói đầu tiên)
            MyPtPackageView selected = pickPackage(view, selectedId);
            List<MyPtSessionRow> sessions;
            if (selected != null) {
                view.setSelectedPackage(selected);
                sessions = sessionDAO.findSessionsOfPackage(customerId, selected.getSubscriptionId());
            } else if (singles.isEmpty()) {
                return ServiceResult.ok(view);   // chưa mua gói PT / buổi lẻ nào → JSP hiện trạng thái rỗng
            } else {
                view.setSingleSelected(true);
                sessions = singles;
            }

            // B5: cột phải — nhãn, trạng thái (giống màn Lịch tập), số đếm, buổi để cuộn tới
            sessionService.fillDisplay(sessions, customerId, now);
            view.setSessions(sessions);
            countSessions(view, sessions, now);
            return ServiceResult.ok(view);

        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /** Điền chữ hiển thị cho 1 thẻ gói: loại gói, người tập cùng, link Zalo PT. */
    private void fillPackage(MyPtPackageView p, int myCustomerId) {
        p.setTypeLabel(MyPtSessionService.typeLabel(p.getType()));
        p.setZaloUrl(MyPtSessionService.zaloUrl(p.getPtPhone()));
        if (p.isCouple() && p.getPartnerCustomerId() != null) {
            boolean iAmHolder = p.getHolderCustomerId() == myCustomerId;
            p.setPartnerName(iAmHolder ? p.getPartnerFullName() : p.getHolderName());
        }
    }

    /**
     * Chọn gói theo ?id=. Trả null khi đang xem "Các buổi lẻ".
     *   id = "single" và có buổi lẻ → null (xem buổi lẻ)
     *   id = mã gói CỦA KHÁCH → gói đó (mã gói người khác → coi như không có, không lộ dữ liệu)
     *   còn lại → gói đang tập đầu tiên → gói đã kết thúc đầu tiên → null (buổi lẻ)
     */
    static MyPtPackageView pickPackage(MyBookingView view, String selectedId) {
        // B1: chọn nhóm buổi lẻ
        if (SINGLE_KEY.equals(selectedId) && view.getSingleSessionCount() > 0) {
            return null;
        }

        // B2: tìm đúng mã gói trong danh sách gói của khách
        if (selectedId != null) {
            for (MyPtPackageView p : view.getActivePackages()) {
                if (String.valueOf(p.getSubscriptionId()).equals(selectedId.trim())) {
                    return p;
                }
            }
            for (MyPtPackageView p : view.getEndedPackages()) {
                if (String.valueOf(p.getSubscriptionId()).equals(selectedId.trim())) {
                    return p;
                }
            }
        }

        // B3: mặc định
        if (!view.getActivePackages().isEmpty()) {
            return view.getActivePackages().get(0);
        }
        if (!view.getEndedPackages().isEmpty()) {
            return view.getEndedPackages().get(0);
        }
        return null;
    }

    /**
     * Đếm 5 ô + tìm buổi để tự cuộn tới.
     * Tổng = Đã check-in + Vắng + Đã hủy + Còn lại + Đang cập nhật (unit test kiểm tra).
     */
    static void countSessions(MyBookingView view, List<MyPtSessionRow> sessions, LocalDateTime now) {
        // B1: đếm theo mã trạng thái
        for (MyPtSessionRow s : sessions) {
            switch (s.getStatusKey()) {
                case MyPtSessionService.KEY_CHECKED_IN -> view.setCheckedInCount(view.getCheckedInCount() + 1);
                case MyPtSessionService.KEY_MISSED -> view.setMissedCount(view.getMissedCount() + 1);
                case MyPtSessionService.KEY_CANCELLED -> view.setCancelledCount(view.getCancelledCount() + 1);
                case MyPtSessionService.KEY_PENDING -> view.setPendingCount(view.getPendingCount() + 1);
                default -> view.setRemainingCount(view.getRemainingCount() + 1);   // today / upcoming / warn
            }
        }
        view.setTotalCount(sessions.size());

        // B2: buổi "còn lại" đầu tiên (hôm nay / sắp tới / cần chú ý); danh sách đã xếp theo ngày giờ
        for (MyPtSessionRow s : sessions) {
            if (s.getStatus().equals("Scheduled") && MyPtSessionService.isNotEnded(s, now)) {
                view.setFocusSessionId(s.getSessionId());
                break;
            }
        }
    }
}
