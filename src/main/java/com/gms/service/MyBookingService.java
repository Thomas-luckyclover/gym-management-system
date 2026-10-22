package com.gms.service;

import com.gms.constant.AppConstants;
import com.gms.constant.Messages;
import com.gms.dao.MyBookingDAO;
import com.gms.dao.MyPtSessionDAO;
import com.gms.dto.MyBookingView;
import com.gms.dto.MyPtPackageView;
import com.gms.dto.MyPtSessionRow;
import com.gms.dto.ServiceResult;
import com.gms.dto.SessionMoveInfo;
import com.gms.dto.WeekdaySlot;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Màn "Gói của tôi" (/customer/bookings, màn #50) — CHỈ XEM.
 *   Cột trái : gói PT đang tập / đã kết thúc + nhóm "Các buổi lẻ"
 *   Cột phải : CTA đúng lúc + buổi tới + lịch cố định (tuần mẫu T2–T6) + dải chấm các buổi + cần để ý
 * Không liệt kê hết các buổi ở đây: xem đủ từng buổi → màn "Lịch tập".
 * Dùng lại DAO và cách tính trạng thái của màn "Lịch tập" → 2 màn luôn hiện cùng 1 nhãn.
 */
public class MyBookingService {

    /** Giá trị ?id= khi chọn nhóm "Các buổi lẻ". */
    public static final String SINGLE_KEY = "single";

    // ----- Loại CTA (mỗi màn chỉ hiện tối đa 1) -----
    static final String CTA_BUY_MEMBERSHIP = "buyMembership";   // buổi sắp tới chưa có quyền vào gym → mua gói thành viên online
    static final String CTA_RENEW_AT_COUNTER = "renewAtCounter"; // gói PT sắp hết → gia hạn TẠI QUẦY (CBR-48)
    static final String CTA_REJOIN = "rejoin";                   // gói PT đã kết thúc → đăng ký lại tại quầy
    static final String CTA_PT_PACKAGE = "ptPackage";            // hay đặt buổi lẻ → gợi ý gói PT (tại quầy)

    // ----- Ngưỡng hiện CTA -----
    static final int RENEW_SESSIONS_LEFT = 3;     // còn ≤ 3 buổi
    static final int RENEW_DAYS_LEFT = 14;        // hoặc còn ≤ 14 ngày
    static final int SINGLE_SUGGEST_COUNT = 3;    // ≥ 3 buổi lẻ …
    static final int SINGLE_SUGGEST_DAYS = 30;    // … trong 30 ngày gần nhất

    private static final String[] WEEKDAY_SHORT = {"T2", "T3", "T4", "T5", "T6", "T7", "CN"};
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DD_MM = DateTimeFormatter.ofPattern("dd/MM");

    private final MyPtSessionDAO sessionDAO = new MyPtSessionDAO();
    private final MyBookingDAO bookingDAO = new MyBookingDAO();
    private final MyPtSessionService sessionService = new MyPtSessionService();
    private final SystemConfigService systemConfigService = new SystemConfigService();

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
            view.setTodayWeekday(now.getDayOfWeek().getValue());

            // B2: cột trái — gói PT (1 câu SQL), chia Đang tập / Đã kết thúc
            for (MyPtPackageView p : sessionDAO.findMyPackages(customerId, now.toLocalDate())) {
                fillPackage(p, customerId);
                if (p.isActive()) {
                    view.getActivePackages().add(p);
                } else {
                    view.getEndedPackages().add(p);
                }
            }

            // B3: cột trái — các buổi lẻ (+ đếm buổi lẻ 30 ngày gần nhất cho gợi ý gói PT)
            List<MyPtSessionRow> singles = sessionDAO.findSingleSessions(customerId);
            view.setSingleSessionCount(singles.size());
            view.setRecentSingleCount(countRecentSingles(singles, now.toLocalDate()));

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

            // B5: nhãn + trạng thái (giống màn Lịch tập), số đếm, buổi tới
            sessionService.fillDisplay(sessions, customerId, now);
            countSessions(view, sessions, now);
            view.setWatchList(buildWatchList(sessions));

            // B6: phần riêng của gói PT / của buổi lẻ
            if (selected != null) {
                fillPackageDetail(view, selected, sessions, now.toLocalDate());
                view.setSessions(sessions);
            } else {
                view.setSessions(orderSingles(sessions, now));
            }

            // B7: CTA (tối đa 1) + thông tin quầy khi CTA bảo khách ra quầy
            view.setCtaKind(chooseCta(view, sessions));
            fillCounterInfo(view);
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

    /** Gói PT: lịch cố định + lý do buổi lệch + vạch tuần cho dải chấm + số ngày còn lại. */
    private void fillPackageDetail(MyBookingView view, MyPtPackageView selected,
            List<MyPtSessionRow> sessions, LocalDate today) throws SQLException {
        // B1: tuần mẫu T2–T6 + các buổi lệch
        applyWeekTemplate(view, sessions);

        // B2: buổi lệch → hỏi SessionMoveLogs vì sao lệch (chỉ khi có buổi lệch)
        if (!view.getOffPattern().isEmpty()) {
            Map<Integer, SessionMoveInfo> moves = bookingDAO.findMoves(selected.getSubscriptionId());
            for (MyPtSessionRow row : view.getOffPattern()) {
                row.setMoveNote(moveNote(moves.get(row.getSessionId())));
            }
        }

        // B3: vạch ngăn tuần + số ngày tới hết hạn
        markWeeks(sessions);
        view.setDaysLeft(Math.max(0, ChronoUnit.DAYS.between(today, selected.getEndDate())));
    }

    /** Giờ mở cửa (P-19) + hotline — chỉ đọc khi CTA bảo khách ra quầy. */
    private void fillCounterInfo(MyBookingView view) throws SQLException {
        String cta = view.getCtaKind();
        boolean atCounter = CTA_RENEW_AT_COUNTER.equals(cta) || CTA_REJOIN.equals(cta) || CTA_PT_PACKAGE.equals(cta);
        if (!atCounter) {
            return;
        }
        view.setHotline(AppConstants.GYM_HOTLINE);
        view.setHotlineTel(AppConstants.GYM_HOTLINE_TEL);
        try {
            view.setOpeningHours(systemConfigService.getString(SystemConfigService.OPENING_HOURS).replace("-", " – "));
        } catch (IllegalStateException e) {
            view.setOpeningHours(null);   // thiếu P-19 thì chỉ không hiện giờ mở cửa, không làm hỏng màn
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

    /** Đếm các trạng thái và chọn buổi Scheduled đầu tiên chưa kết thúc. */
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

        // B2: lấy buổi Scheduled đầu tiên chưa hết giờ để hiển thị và mở đúng buổi trên lịch.
        for (MyPtSessionRow s : sessions) {
            if (!s.getStatus().equals("Scheduled") || !MyPtSessionService.isNotEnded(s, now)) {
                continue;
            }
            view.setFocusSessionId(s.getSessionId());
            view.setFocusSession(s);
            view.setDaysUntilFocus(ChronoUnit.DAYS.between(now.toLocalDate(), s.getSlotDate()));
            break;
        }
    }

    /**
     * Lịch cố định dạng "tuần mẫu" T2 → T6 (BR-35: buổi gói PT chỉ rơi vào T2–T6).
     *   Giờ cố định của 1 thứ = giờ bắt đầu có NHIỀU buổi nhất ở thứ đó, nếu chiếm ≥ 50% số buổi của thứ đó
     *   (hòa → giờ sớm hơn). Giờ chính = giờ cố định của nhiều thứ nhất; thứ cố định nhưng khác giờ chính → "odd".
     *   Buổi không khớp giờ cố định của thứ nó (kể cả T7/CN do đổi buổi) → "Buổi khác giờ".
     * Ví dụ: T2, T3, T6 lúc 18:00 + T4 lúc 07:00 → 4 ô cố định (T4 là odd), T5 none.
     */
    static void applyWeekTemplate(MyBookingView view, List<MyPtSessionRow> sessions) {
        // B1: đếm số buổi theo (thứ, giờ bắt đầu); nhớ giờ kết thúc của từng giờ bắt đầu
        List<TreeMap<LocalTime, Integer>> perDay = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            perDay.add(new TreeMap<>());
        }
        Map<LocalTime, LocalTime> endOf = new HashMap<>();
        for (MyPtSessionRow s : sessions) {
            int day = s.getSlotDate().getDayOfWeek().getValue();   // Thứ 2 = 1 … Chủ nhật = 7
            if (day <= 5) {
                perDay.get(day - 1).merge(s.getStartTime(), 1, Integer::sum);
                endOf.putIfAbsent(s.getStartTime(), s.getEndTime());
            }
        }

        // B2: giờ cố định của từng thứ
        LocalTime[] fixed = new LocalTime[5];
        int[] fixedCount = new int[5];
        boolean[] hasSessions = new boolean[5];
        for (int d = 0; d < 5; d++) {
            int total = 0;
            LocalTime best = null;
            int bestCount = 0;
            for (Map.Entry<LocalTime, Integer> e : perDay.get(d).entrySet()) {   // TreeMap: giờ tăng dần → hòa giữ giờ sớm
                total += e.getValue();
                if (e.getValue() > bestCount) {
                    best = e.getKey();
                    bestCount = e.getValue();
                }
            }
            hasSessions[d] = total > 0;
            boolean majority = bestCount * 2 >= total && (bestCount >= 2 || total == 1);
            if (hasSessions[d] && majority) {
                fixed[d] = best;
                fixedCount[d] = bestCount;
            }
        }

        // B3: giờ chính = giờ cố định của nhiều thứ nhất (hòa → giờ sớm hơn)
        TreeMap<LocalTime, Integer> daysPerTime = new TreeMap<>();
        for (LocalTime time : fixed) {
            if (time != null) {
                daysPerTime.merge(time, 1, Integer::sum);
            }
        }
        LocalTime main = null;
        int mainDays = 0;
        for (Map.Entry<LocalTime, Integer> e : daysPerTime.entrySet()) {
            if (e.getValue() > mainDays) {
                main = e.getKey();
                mainDays = e.getValue();
            }
        }

        // B4: 5 ô
        List<WeekdaySlot> template = new ArrayList<>();
        for (int d = 0; d < 5; d++) {
            String kind;
            if (fixed[d] != null) {
                kind = fixed[d].equals(main) ? WeekdaySlot.KIND_MAIN : WeekdaySlot.KIND_ODD;
            } else {
                kind = hasSessions[d] ? WeekdaySlot.KIND_MIXED : WeekdaySlot.KIND_NONE;
            }
            template.add(new WeekdaySlot(WEEKDAY_SHORT[d], kind, fixed[d],
                    fixed[d] == null ? null : endOf.get(fixed[d]), fixedCount[d]));
        }
        view.setWeekTemplate(template);
        view.setTemplateSummary(summarize(fixed));

        // B5: buổi khớp lịch → đếm; buổi lệch → "Buổi khác giờ"
        int onPattern = 0;
        List<MyPtSessionRow> off = new ArrayList<>();
        for (MyPtSessionRow s : sessions) {
            int day = s.getSlotDate().getDayOfWeek().getValue();
            boolean match = day <= 5 && s.getStartTime().equals(fixed[day - 1]);
            if (match) {
                onPattern++;
            } else {
                off.add(s);
            }
        }
        view.setTemplateCount(onPattern);
        view.setOffPattern(off);
    }

    /**
     * Câu tóm tắt lịch cố định: gom các thứ cùng giờ, theo thứ tự thứ xuất hiện.
     *   cả 5 thứ cùng giờ → "T2 – T6 lúc 18:00"
     *   khác nhau → "T2 · T3 · T6 lúc 18:00, T4 lúc 07:00"
     *   không thứ nào cố định → null
     */
    static String summarize(LocalTime[] fixed) {
        // B1: giờ → các thứ (giữ thứ tự xuất hiện)
        Map<LocalTime, List<String>> daysByTime = new LinkedHashMap<>();
        for (int d = 0; d < fixed.length; d++) {
            if (fixed[d] != null) {
                daysByTime.computeIfAbsent(fixed[d], k -> new ArrayList<>()).add(WEEKDAY_SHORT[d]);
            }
        }
        if (daysByTime.isEmpty()) {
            return null;
        }

        // B2: ghép từng cụm
        List<String> parts = new ArrayList<>();
        for (Map.Entry<LocalTime, List<String>> e : daysByTime.entrySet()) {
            String days = e.getValue().size() == 5 ? "T2 – T6" : String.join(" · ", e.getValue());
            parts.add(days + " lúc " + e.getKey().format(HH_MM));
        }
        return String.join(", ", parts);
    }

    /** Vì sao 1 buổi lệch lịch cố định. Không có log dời → giờ khác ngay từ lúc xếp lịch (BR-41). */
    static String moveNote(SessionMoveInfo move) {
        if (move == null) {
            return "Xếp giờ khác từ đầu vì khung cố định ngày đó đã kín";
        }
        String who = switch (move.getMoveType()) {
            case "CustomerSwap" -> "Bạn đổi buổi";
            case "PtOnBehalfSwap" -> "PT đổi giúp bạn";
            case "PtReschedule" -> "PT dời lịch";
            case "PtTransfer" -> "Chuyển PT dạy thay";
            default -> "Đã dời buổi";
        };
        int day = move.getOriginalDate().getDayOfWeek().getValue();
        return who + " · thay buổi " + WEEKDAY_SHORT[day - 1] + " " + move.getOriginalDate().format(DD_MM);
    }

    /** Đánh dấu buổi đầu tiên của mỗi tuần (trừ tuần đầu) → dải chấm vẽ vạch ngăn tuần. */
    static void markWeeks(List<MyPtSessionRow> sessions) {
        LocalDate previousWeek = null;
        for (MyPtSessionRow s : sessions) {
            LocalDate week = s.getSlotDate().with(DayOfWeek.MONDAY);
            s.setFirstOfWeek(previousWeek != null && !week.equals(previousWeek));
            previousWeek = week;
        }
    }

    /**
     * "Cần để ý": chỉ buổi SẮP TỚI mà bạn chưa có Membership/DayPass — còn kịp mua gói trước ngày tập.
     * Không tính ngày phòng nghỉ (PT tự dời) và kỳ bảo lưu (vẫn có gói).
     */
    static List<MyPtSessionRow> buildWatchList(List<MyPtSessionRow> sessions) {
        List<MyPtSessionRow> warn = new ArrayList<>();
        for (MyPtSessionRow s : sessions) {
            if (MyPtSessionService.KEY_WARN.equals(s.getStatusKey()) && s.isNoGymPass()) {
                warn.add(s);
            }
        }
        return warn;
    }

    /**
     * Buổi lẻ: buổi chưa kết thúc trước (gần nhất trước), rồi buổi đã qua (mới nhất trước).
     * Buổi đã qua gần nhất với mỗi PT (không bị hủy) → rebook = "Đặt lại với PT này".
     */
    static List<MyPtSessionRow> orderSingles(List<MyPtSessionRow> singles, LocalDateTime now) {
        // B1: tách sắp tới / đã qua
        List<MyPtSessionRow> upcoming = new ArrayList<>();
        List<MyPtSessionRow> past = new ArrayList<>();
        for (MyPtSessionRow s : singles) {
            if (MyPtSessionService.isNotEnded(s, now)) {
                upcoming.add(s);
            } else {
                past.add(0, s);
            }
        }

        // B2: mỗi PT chỉ 1 nút "Đặt lại" (ở buổi gần nhất)
        Set<Integer> rebookedPts = new HashSet<>();
        for (MyPtSessionRow s : past) {
            boolean usable = !"Cancelled".equals(s.getStatus());
            s.setRebook(usable && rebookedPts.add(s.getPtEmployeeId()));
        }
        upcoming.addAll(past);
        return upcoming;
    }

    /** Số buổi lẻ (không tính buổi bị hủy) trong 30 ngày gần nhất tính tới hôm nay. */
    static int countRecentSingles(List<MyPtSessionRow> singles, LocalDate today) {
        LocalDate from = today.minusDays(SINGLE_SUGGEST_DAYS);
        int count = 0;
        for (MyPtSessionRow s : singles) {
            boolean inWindow = !s.getSlotDate().isBefore(from) && !s.getSlotDate().isAfter(today);
            if (inWindow && !"Cancelled".equals(s.getStatus())) {
                count++;
            }
        }
        return count;
    }

    /**
     * Chọn 1 CTA theo độ ưu tiên (null = không hiện gì):
     *   1. buổi sắp tới chưa có Membership/DayPass (không tính bảo lưu, ngày phòng nghỉ) → mua gói thành viên online
     *   2. đang xem buổi lẻ: ≥ 3 buổi lẻ trong 30 ngày → gợi ý gói PT (tại quầy)
     *   3. gói PT đã kết thúc → đăng ký lại tại quầy
     *   4. gói PT đang tập còn ≤ 3 buổi hoặc ≤ 14 ngày → gia hạn tại quầy (CBR-48: gói PT không bán online)
     * Gọi sau countSessions và sau khi đã tính daysLeft.
     */
    static String chooseCta(MyBookingView view, List<MyPtSessionRow> sessions) {
        // B1: buổi sắp tới chưa có gói thành viên (cùng điều kiện với "Cần để ý")
        if (!buildWatchList(sessions).isEmpty()) {
            return CTA_BUY_MEMBERSHIP;
        }

        // B2: buổi lẻ
        if (view.isSingleSelected()) {
            return view.getRecentSingleCount() >= SINGLE_SUGGEST_COUNT ? CTA_PT_PACKAGE : null;
        }

        // B3 + B4: gói PT
        MyPtPackageView p = view.getSelectedPackage();
        if (p == null) {
            return null;
        }
        if (!p.isActive()) {
            return CTA_REJOIN;
        }
        boolean fewSessions = view.getRemainingCount() <= RENEW_SESSIONS_LEFT;
        boolean fewDays = view.getDaysLeft() <= RENEW_DAYS_LEFT;
        return fewSessions || fewDays ? CTA_RENEW_AT_COUNTER : null;
    }
}
