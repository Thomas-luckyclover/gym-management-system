package com.gms.service;

import com.gms.constant.AppConstants;
import com.gms.constant.Messages;
import com.gms.dao.MyBookingDAO;
import com.gms.dao.MyPtSessionDAO;
import com.gms.dto.MyBookingView;
import com.gms.dto.MyPtPackageView;
import com.gms.dto.MyPtSessionRow;
import com.gms.dto.ServiceResult;
import com.gms.dto.SessionGroup;
import com.gms.dto.SessionMoveInfo;
import com.gms.dto.WeekdaySlot;
import com.gms.util.ZaloUtil;
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
 * MÀN GÓI CỦA TÔI — dựng MyBookingView cho MyBookingServlet.
 * Đọc DB qua MyPtSessionDAO (gói, buổi — dùng chung với màn Lịch tập) và MyBookingDAO (lịch sử dời buổi).
 */
public class MyBookingService {
    // ?package=single → xem buổi lẻ thay vì 1 gói.
    public static final String SINGLE_KEY = "single";

    // 4 loại khung gợi ý (ctaKind) — JSP chọn câu + nút theo giá trị này.
    static final String CTA_BUY_MEMBERSHIP = "buyMembership";
    static final String CTA_RENEW_AT_COUNTER = "renewAtCounter";
    static final String CTA_REJOIN = "rejoin";
    static final String CTA_PT_PACKAGE = "ptPackage";

    // Gợi ý gia hạn khi còn ≤ 3 buổi hoặc ≤ 14 ngày · gợi ý mua gói PT khi đặt ≥ 3 buổi lẻ trong 30 ngày.
    static final int RENEW_SESSIONS_LEFT = 3;
    static final int RENEW_DAYS_LEFT = 14;
    static final int SINGLE_SUGGEST_COUNT = 3;
    static final int SINGLE_SUGGEST_DAYS = 30;

    private static final String[] WEEKDAY_SHORT = {"T2", "T3", "T4", "T5", "T6", "T7", "CN"};
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DD_MM = DateTimeFormatter.ofPattern("dd/MM");

    private final MyPtSessionDAO sessionDAO = new MyPtSessionDAO();
    private final MyBookingDAO bookingDAO = new MyBookingDAO();
    private final MyPtSessionService sessionService = new MyPtSessionService();
    private final SystemConfigService systemConfigService = new SystemConfigService();

    // Dựng dữ liệu cả màn. Trả về: ok(MyBookingView) | fail(MSG07 không phải khách, MSG04 lỗi DB).
    public ServiceResult<MyBookingView> getMyBookings(int accountId, String packageKey, LocalDateTime now) {
        try {
            // B1: tài khoản → CustomerId (không phải khách → MSG07); ghi thứ hôm nay để tô ô "hôm nay"
            Integer customerId = sessionService.findCustomerId(accountId);
            if (customerId == null) {
                return ServiceResult.fail(Messages.MSG07_NO_PERMISSION);
            }
            MyBookingView view = new MyBookingView();
            view.setTodayWeekday(now.getDayOfWeek().getValue());

            // B2: MyPtSessionDAO.findMyPackages → mọi gói PT của khách (đứng tên hoặc tập cùng)
            //     fillPackage điền nhãn / Zalo / người tập cùng → chia 2 tab: Đang tập | Đã kết thúc
            for (MyPtPackageView p : sessionDAO.findMyPackages(customerId, now.toLocalDate())) {
                fillPackage(p, customerId);
                if (p.isActive()) {
                    view.getActivePackages().add(p);
                } else {
                    view.getEndedPackages().add(p);
                }
            }

            // B3: MyPtSessionDAO.findSingleSessions → buổi lẻ: đếm tổng + đếm 30 ngày gần đây (cho gợi ý mua gói PT)
            List<MyPtSessionRow> singles = sessionDAO.findSingleSessions(customerId);
            view.setSingleSessionCount(singles.size());
            view.setRecentSingleCount(countRecentSingles(singles, now.toLocalDate()));

            // B4: chọn gói đang xem theo ?package= (pickPackage); không có gói thì xem buổi lẻ; không có gì → trả view rỗng
            MyPtPackageView selected = pickPackage(view, packageKey);
            List<MyPtSessionRow> sessions;
            if (selected != null) {
                view.setSelectedPackage(selected);
                sessions = sessionDAO.findSessionsOfPackage(customerId, selected.getSubscriptionId());
            } else if (singles.isEmpty()) {
                return ServiceResult.ok(view);
            } else {
                view.setSingleSelected(true);
                sessions = singles;
            }

            // B5: MyPtSessionService.fillDisplay → mỗi buổi: statusKey / statusLabel / lý do / cảnh báo thiếu gói gym
            sessionService.fillDisplay(sessions, customerId, now);
            // B6: đếm buổi theo trạng thái + tìm "Buổi tới" (countSessions) → danh sách "Cần để ý" (buildWatchList, groupByReason)
            countSessions(view, sessions, now);
            view.setWatchList(buildWatchList(sessions));
            view.setWatchGroups(groupByReason(view.getWatchList()));

            // B7: xem gói → Lịch cố định + buổi khác giờ + số ngày còn lại (fillPackageDetail); xem buổi lẻ → sắp tới trước, đã qua sau (orderSingles)
            if (selected != null) {
                fillPackageDetail(
                        view,
                        selected,
                        sessions,
                        now.toLocalDate());
                view.setSessions(sessions);
            } else {
                view.setSessions(orderSingles(sessions, now));
            }

            // B8: chọn khung gợi ý (chooseCta) + điền hotline / giờ mở cửa nếu gợi ý ra quầy (fillCounterInfo)
            view.setCtaKind(chooseCta(view, sessions));
            fillCounterInfo(view);
            return ServiceResult.ok(view);

        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /**
     * HÀM PHỤ (getMyBookings B2) — điền typeLabel, zaloUrl, partnerName (tên NGƯỜI KIA của gói đôi).
     */
    private void fillPackage(MyPtPackageView p, int myCustomerId) {
        p.setTypeLabel(MyPtSessionService.typeLabel(p.getType()));
        p.setZaloUrl(ZaloUtil.toUrl(p.getPtPhone()));

        if (p.isCouple() && p.getPartnerCustomerId() != null) {
            boolean iAmHolder = p.getHolderCustomerId() == myCustomerId;
            p.setPartnerName(iAmHolder ? p.getPartnerFullName() : p.getHolderName());
        }
    }

    /**
     * HÀM PHỤ (getMyBookings B3) — đếm buổi lẻ không bị hủy trong 30 ngày gần đây. Trả về: số buổi.
     */
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
     * HÀM PHỤ (getMyBookings B4) — gói đang xem: ?package=single → null (xem buổi lẻ) · đúng Id → gói đó
     * · không có / sai Id → gói đang tập đầu tiên → gói đã kết thúc đầu tiên → null.
     */
    static MyPtPackageView pickPackage(MyBookingView view, String packageKey) {
        if (SINGLE_KEY.equals(packageKey) && view.getSingleSessionCount() > 0) {
            return null;
        }

        if (packageKey != null) {
            String id = packageKey.trim();
            for (MyPtPackageView p : view.getActivePackages()) {
                if (String.valueOf(p.getSubscriptionId()).equals(id)) {
                    return p;
                }
            }
            for (MyPtPackageView p : view.getEndedPackages()) {
                if (String.valueOf(p.getSubscriptionId()).equals(id)) {
                    return p;
                }
            }
        }

        if (!view.getActivePackages().isEmpty()) {
            return view.getActivePackages().get(0);
        }
        if (!view.getEndedPackages().isEmpty()) {
            return view.getEndedPackages().get(0);
        }
        return null;
    }

    /**
     * HÀM PHỤ (getMyBookings B6) — đếm checkedIn / missed / cancelled / pending / còn lại;
     * "Buổi tới" = buổi Scheduled đầu tiên chưa kết thúc (focusSession, focusSessionId, daysUntilFocus).
     */
    static void countSessions(MyBookingView view, List<MyPtSessionRow> sessions, LocalDateTime now) {
        for (MyPtSessionRow s : sessions) {
            switch (s.getStatusKey()) {
                case MyPtSessionService.KEY_CHECKED_IN -> view.setCheckedInCount(view.getCheckedInCount() + 1);
                case MyPtSessionService.KEY_MISSED -> view.setMissedCount(view.getMissedCount() + 1);
                case MyPtSessionService.KEY_CANCELLED -> view.setCancelledCount(view.getCancelledCount() + 1);
                case MyPtSessionService.KEY_PENDING -> view.setPendingCount(view.getPendingCount() + 1);
                default -> view.setRemainingCount(view.getRemainingCount() + 1);
            }
        }
        view.setTotalCount(sessions.size());

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
     * HÀM PHỤ (B6, chooseCta) — buổi sắp tới bị cảnh báo VÌ chưa có gói gym / vé ngày. Trả về: danh sách buổi.
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
     * HÀM PHỤ (B6) — gom buổi cùng trạng thái + cùng lý do thành 1 nhóm (SessionGroup) cho khung "Cần để ý".
     */
    static List<SessionGroup> groupByReason(List<MyPtSessionRow> sessions) {
        Map<String, SessionGroup> groups = new LinkedHashMap<>();
        for (MyPtSessionRow s : sessions) {
            String key = s.getStatusKey() + "|" + s.getExplanation();
            groups.computeIfAbsent(key, k -> new SessionGroup(s.getStatusKey(), s.getStatusLabel(), s.getExplanation()))
                    .getSessions().add(s);
        }
        return new ArrayList<>(groups.values());
    }

    /**
     * HÀM PHỤ (getMyBookings B7, khi xem 1 gói) — lịch cố định, ghi chú buổi khác giờ, vạch ngăn tuần, số ngày còn lại.
     */
    private void fillPackageDetail(MyBookingView view, MyPtPackageView selected,
            List<MyPtSessionRow> sessions, LocalDate today) throws SQLException {
        // B1: tìm giờ cố định từng thứ (applyWeekTemplate)
        applyWeekTemplate(view, sessions);

        // B2: có buổi khác giờ → MyBookingDAO.findMoves (SessionMoveLogs) → ghi chú "PT dời lịch · thay buổi …"
        if (!view.getOffPattern().isEmpty()) {
            Map<Integer, SessionMoveInfo> moves = bookingDAO.findMoves(selected.getSubscriptionId());
            for (MyPtSessionRow row : view.getOffPattern()) {
                row.setMoveNote(moveNote(moves.get(row.getSessionId())));
            }
        }

        // B3: đánh dấu buổi đầu mỗi tuần (vạch ngăn trong dải chấm) + số ngày tới hết gói
        markWeeks(sessions);
        view.setDaysLeft(Math.max(0, ChronoUnit.DAYS.between(today, selected.getEndDate())));
    }

    /**
     * HÀM PHỤ (fillPackageDetail B1) — "Lịch cố định" T2–T6 từ chính các buổi của gói.
     */
    static void applyWeekTemplate(MyBookingView view, List<MyPtSessionRow> sessions) {
        // B1: đếm mỗi thứ (T2–T6) mỗi giờ có mấy buổi
        List<TreeMap<LocalTime, Integer>> perDay = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            perDay.add(new TreeMap<>());
        }
        Map<LocalTime, LocalTime> endOf = new HashMap<>();
        for (MyPtSessionRow s : sessions) {
            int day = s.getSlotDate().getDayOfWeek().getValue();
            if (day <= 5) {
                perDay.get(day - 1).merge(s.getStartTime(), 1, Integer::sum);
                endOf.putIfAbsent(s.getStartTime(), s.getEndTime());
            }
        }

        // B2: mỗi thứ: giờ chiếm ĐA SỐ (≥ 1/2 số buổi, ít nhất 2 buổi hoặc chỉ có 1 buổi) = giờ cố định
        LocalTime[] fixed = new LocalTime[5];
        int[] fixedCount = new int[5];
        boolean[] hasSessions = new boolean[5];
        for (int d = 0; d < 5; d++) {
            int total = 0;
            LocalTime best = null;
            int bestCount = 0;
            for (Map.Entry<LocalTime, Integer> e : perDay.get(d).entrySet()) {
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

        // B3: giờ chính = giờ cố định của nhiều thứ nhất
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

        // B4: tạo 5 ô WeekdaySlot: main (giờ chính) · odd (giờ cố định khác) · mixed (có buổi, không cố định) · none
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

        // B5: chia buổi: đúng giờ cố định (đếm templateCount) | khác giờ → offPattern
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
     * HÀM PHỤ (applyWeekTemplate B4) — "T2 · T4 lúc 18:00, T6 lúc 07:00" (5 thứ cùng giờ → "T2 – T6"). null = không có giờ cố định.
     */
    static String summarize(LocalTime[] fixed) {
        Map<LocalTime, List<String>> daysByTime = new LinkedHashMap<>();
        for (int d = 0; d < fixed.length; d++) {
            if (fixed[d] != null) {
                daysByTime.computeIfAbsent(fixed[d], k -> new ArrayList<>()).add(WEEKDAY_SHORT[d]);
            }
        }
        if (daysByTime.isEmpty()) {
            return null;
        }

        List<String> parts = new ArrayList<>();
        for (Map.Entry<LocalTime, List<String>> e : daysByTime.entrySet()) {
            String days = e.getValue().size() == 5 ? "T2 – T6" : String.join(" · ", e.getValue());
            parts.add(days + " lúc " + e.getKey().format(HH_MM));
        }
        return String.join(", ", parts);
    }

    /**
     * HÀM PHỤ (fillPackageDetail B2) — câu ghi chú cho 1 buổi khác giờ:
     * không có lịch sử dời → "Xếp giờ khác từ đầu…" · có → "<ai dời> · thay buổi T3 05/11".
     */
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

    /**
     * HÀM PHỤ (fillPackageDetail B3) — firstOfWeek = true ở buổi đầu tiên của mỗi tuần mới (trừ tuần đầu).
     */
    static void markWeeks(List<MyPtSessionRow> sessions) {
        LocalDate previousWeek = null;
        for (MyPtSessionRow s : sessions) {
            LocalDate week = s.getSlotDate().with(DayOfWeek.MONDAY);
            s.setFirstOfWeek(previousWeek != null && !week.equals(previousWeek));
            previousWeek = week;
        }
    }

    /**
     * HÀM PHỤ (getMyBookings B7, buổi lẻ) — buổi sắp tới (cũ → mới) rồi buổi đã qua (mới → cũ);
     * mỗi PT đánh rebook cho 1 buổi đã qua gần nhất (link "Đặt lại với PT này").
     */
    static List<MyPtSessionRow> orderSingles(List<MyPtSessionRow> singles, LocalDateTime now) {
        List<MyPtSessionRow> upcoming = new ArrayList<>();
        List<MyPtSessionRow> past = new ArrayList<>();
        for (MyPtSessionRow s : singles) {
            if (MyPtSessionService.isNotEnded(s, now)) {
                upcoming.add(s);
            } else {
                past.add(0, s);
            }
        }

        Set<Integer> rebookedPts = new HashSet<>();
        for (MyPtSessionRow s : past) {
            boolean usable = !"Cancelled".equals(s.getStatus());
            s.setRebook(usable && rebookedPts.add(s.getPtEmployeeId()));
        }

        upcoming.addAll(past);
        return upcoming;
    }

    /**
     * HÀM PHỤ (getMyBookings B8) — khung gợi ý nào:
     * có buổi thiếu gói gym → buyMembership · xem buổi lẻ, ≥ 3 buổi/30 ngày → ptPackage
     * · gói đã kết thúc → rejoin · còn ≤ 3 buổi hoặc ≤ 14 ngày → renewAtCounter · còn lại → null (không hiện).
     */
    static String chooseCta(MyBookingView view, List<MyPtSessionRow> sessions) {
        if (!buildWatchList(sessions).isEmpty()) {
            return CTA_BUY_MEMBERSHIP;
        }

        if (view.isSingleSelected()) {
            return view.getRecentSingleCount() >= SINGLE_SUGGEST_COUNT ? CTA_PT_PACKAGE : null;
        }

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

    /**
     * HÀM PHỤ (getMyBookings B8) — gợi ý ra quầy (renew / rejoin / ptPackage) → điền hotline + giờ mở cửa (P-19).
     */
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
            view.setOpeningHours(null);
        }
    }
}
