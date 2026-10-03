package com.gms.service;

import com.gms.constant.Messages;
import com.gms.constant.Role;
import com.gms.dao.AccountDAO;
import com.gms.dao.EmployeeDAO;
import com.gms.dao.PersonDAO;
import com.gms.dao.SessionDAO;
import com.gms.dao.SubscriptionDAO;
import com.gms.dao.TimeSlotDAO;
import com.gms.dto.ChartBar;
import com.gms.dto.CommissionLine;
import com.gms.dto.PtClientView;
import com.gms.dto.PtDashboardView;
import com.gms.dto.PtSessionView;
import com.gms.dto.ServiceResult;
import com.gms.dto.SessionStatRow;
import com.gms.dto.WeatherView;
import com.gms.entity.Account;
import com.gms.entity.Employee;
import com.gms.entity.Person;
import com.gms.util.DateTimeUtil;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Gom dữ liệu cho màn đầu tiên PT thấy sau khi đăng nhập (dashboard — chỉ đọc, không sửa dữ liệu).
 *
 * <p>Bố cục theo prototype {@code pt/trang-chu.html}; nghiệp vụ theo SRS 9.1.2 và {@code 05_Dashboard_Rules.md}.</p>
 *
 * <p>Các phần dữ liệu cung cấp cho JSP:</p>
 * <ul>
 *   <li>Đầu trang — mã NV, họ tên, chữ viết tắt avatar, tỷ lệ hoa hồng, lời chào theo giờ, ngày hôm nay, thời tiết.</li>
 *   <li>Hoa hồng theo kỳ — chọn tháng/quý, bảng nguồn buổi, tổng tiền, số buổi, đếm trạng thái, biểu đồ cột.</li>
 *   <li>Buổi sắp tới — danh sách buổi trong khoảng "Từ ngày" đến "Từ ngày + rangeDays".</li>
 *   <li>Buổi cần điểm danh — buổi Scheduled đang nằm trong cửa sổ điểm danh (BR-54, P-07).</li>
 *   <li>Banner thiếu gói tập — đếm khách có buổi rơi vào ngày không có Membership (CBR-66).</li>
 *   <li>Tóm tắt hôm nay — số buổi hôm nay, số slot trống tới Chủ nhật (luôn là hôm nay).</li>
 *   <li>Cột phụ — khách phụ trách vào ngày đang lọc, ô Tuần và ô Tháng neo theo ngày đang lọc.</li>
 * </ul>
 *
 * <p>Ràng buộc nghiệp vụ chính:</p>
 * <ul>
 *   <li>BR-54 / P-07: buổi cần điểm danh = đã tới giờ bắt đầu và chưa quá {@code attendanceWindowHours} giờ sau khi kết thúc.</li>
 *   <li>DBR-58, CBR-55/57/80/81/82: tiền hoa hồng lấy trực tiếp từ {@code Sessions.CommissionAmount} của buổi ĐƯỢC TÍNH.</li>
 *   <li>GBR-01, GBR-10: lọc buổi theo PT đang làm việc, theo ngày diễn ra buổi.</li>
 *   <li>GBR-14: dashboard chỉ đọc — buổi quá cửa sổ là việc JOB-04, không tự đổi trạng thái ở đây.</li>
 *   <li>CBR-64/CBR-66: banner thiếu gói quét các buổi Scheduled trong 30 ngày tới.</li>
 * </ul>
 *
 * @author Le Cong Minh
 */
public class PtDashboardService {

    /** Các lựa chọn "Trong ..." của khối Buổi sắp tới (1 = chỉ ngày đã chọn); mặc định 7 như prototype. */
    private static final List<Integer> RANGE_OPTIONS = List.of(1, 3, 7, 30);
    private static final int DEFAULT_RANGE_DAYS = 7;

    /** Banner "khách thiếu gói tập" xét các buổi Scheduled trong 30 ngày tới (CBR-66). */
    private static final int MISSING_MEMBERSHIP_LOOKAHEAD_DAYS = 30;

    /** Gói PT hết hạn trong 7 ngày thì coi là "sắp hết hạn". */
    private static final int ENDING_SOON_DAYS = 7;

    /** Số tháng gần nhất cho chọn ở ô "Kỳ" (tháng này + 2 tháng trước), cộng thêm quý này và quý trước. */
    private static final int MONTH_OPTION_COUNT = 3;

    private static final String[] ROMAN_QUARTERS = {"I", "II", "III", "IV"};

    private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter MONTH_YEAR = DateTimeFormatter.ofPattern("MM/yyyy");
    private static final DateTimeFormatter MONTH_ONLY = DateTimeFormatter.ofPattern("MM");

    private final AccountDAO accountDAO = new AccountDAO();
    private final EmployeeDAO employeeDAO = new EmployeeDAO();
    private final PersonDAO personDAO = new PersonDAO();
    private final SessionDAO sessionDAO = new SessionDAO();
    private final SubscriptionDAO subscriptionDAO = new SubscriptionDAO();
    private final TimeSlotDAO timeSlotDAO = new TimeSlotDAO();
    private final SystemConfigService systemConfigService = new SystemConfigService();
    private final WeatherService weatherService = new WeatherService();

    // ===== Entry point: lấy toàn bộ dashboard =====

    /**
     * Lấy toàn bộ dữ liệu dashboard của PT đang đăng nhập.
     * Tài khoản phải là PT đang làm việc (GBR-10); không hợp lệ thì trả MSG07. Lỗi DB trả MSG04.
     * Điền lần lượt: đầu trang → hoa hồng theo kỳ → buổi sắp tới theo bộ lọc ngày → điểm danh + banner thiếu gói → tóm tắt hôm nay → cột phụ (khách/tuần/tháng) neo theo "Từ ngày".
     */
    public ServiceResult<PtDashboardView> getDashboard(int accountId, String periodParam, String dateParam,
            int rangeDays, String weatherDemo) {
        try {
            // B1: tài khoản phải là PT đang làm việc (GBR-10). Lấy luôn hồ sơ Person để có họ tên
            Account account = accountDAO.findById(accountId);
            Employee employee = account == null ? null : employeeDAO.findByPersonId(account.getPersonId());
            boolean isWorkingPt = employee != null
                    && employee.getPosition() == Role.PT
                    && employee.getStatus() == Employee.Status.WORKING;
            if (!isWorkingPt) {
                return ServiceResult.fail(Messages.MSG07_NO_PERMISSION);
            }
            Person person = personDAO.findById(account.getPersonId());
            int ptId = employee.getEmployeeId();
            LocalDateTime now = DateTimeUtil.now();
            LocalDate today = now.toLocalDate();

            // B2: đầu trang — tên, lời chào, thời tiết
            PtDashboardView view = new PtDashboardView();
            fillHeader(view, employee, person, now);
            WeatherView demo = weatherService.demoWeather(weatherDemo);
            view.setWeather(demo != null ? demo : weatherService.getTodayWeather());

            // B3: hoa hồng theo kỳ đã chọn (bảng theo nguồn buổi, thống kê trạng thái, biểu đồ)
            fillCommission(view, ptId, periodParam, today);

            // B4: buổi sắp tới theo bộ lọc ngày (bắt buộc, mặc định hôm nay — server kiểm lại, GBR-13)
            LocalDate fromDate = DateTimeUtil.parseHtmlDate(dateParam);
            if (fromDate == null) {
                fromDate = today;
                if (dateParam != null && dateParam.isEmpty()) {
                    view.setDateError(String.format(Messages.MSG09_REQUIRED, "Ngày"));
                }
            }
            int days = RANGE_OPTIONS.contains(rangeDays) ? rangeDays : DEFAULT_RANGE_DAYS;
            view.setFromDateValue(fromDate.toString());
            view.setFromToday(fromDate.equals(today));
            view.setFromDateLabel((fromDate.equals(today) ? "Hôm nay" : dayOfWeekLabel(fromDate.getDayOfWeek()))
                    + ", " + DateTimeUtil.formatDate(fromDate));
            view.setRangeDays(days);
            List<PtSessionView> upcoming = sessionDAO.findByPt(ptId, fromDate, fromDate.plusDays(days - 1), false);
            for (PtSessionView session : upcoming) {
                labelSession(session, today);
            }
            view.setUpcomingSessions(upcoming);

            // B5: buổi cần điểm danh + banner thiếu gói tập — chung 1 lần đọc các buổi Scheduled quanh hôm nay
            int windowHours = systemConfigService.getInt(SystemConfigService.ATTENDANCE_WINDOW_HOURS);
            view.setAttendanceWindowHours(windowHours);
            List<PtSessionView> scheduled = sessionDAO.findByPt(ptId,
                    today.minusDays(windowHours / 24 + 1), today.plusDays(MISSING_MEMBERSHIP_LOOKAHEAD_DAYS - 1), true);
            fillAttendanceAndMissingMembership(view, scheduled, now, windowHours);

            // B6: tóm tắt HÔM NAY trên thẻ chào (luôn là hôm nay, không theo ô lọc)
            fillToday(view, ptId, today);

            // B7: cột phụ — TẤT CẢ theo NGÀY ĐANG LỌC (ô "Từ ngày") để cùng 1 mốc với khối Buổi sắp tới:
            //     khách phụ trách vào ngày đó, tuần chứa ngày đó, tháng chứa ngày đó
            fillClients(view, ptId, fromDate);
            fillWeekAndMonth(view, ptId, fromDate, today);

            return ServiceResult.ok(view);

        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    // ===== Đầu trang =====

    /** Điền mã NV, họ tên, chữ viết tắt avatar, tỷ lệ hoa hồng, lời chào, ngày hôm nay. */
    private void fillHeader(PtDashboardView view, Employee employee, Person person, LocalDateTime now) {
        String fullName = person == null ? "" : person.getFullName().trim();
        String[] words = fullName.split("\\s+");
        String firstWord = words[0];
        String lastWord = words[words.length - 1];

        view.setEmployeeCode(employee.getEmployeeCode());
        view.setCommissionRate(employee.getCommissionRate() == null ? 0 : employee.getCommissionRate());
        view.setFullName(fullName);
        // Avatar = chữ đầu của chữ đầu tiên + chữ đầu của chữ cuối: "Đinh Công Sơn" → "ĐS", "David Miller" → "DM"
        view.setInitials(fullName.isEmpty() ? "PT"
                : (firstWord.substring(0, 1) + (words.length > 1 ? lastWord.substring(0, 1) : "")).toUpperCase());
        view.setGreeting(buildGreeting(now.toLocalTime()));
        view.setTodayLabel(dayOfWeekLabel(now.getDayOfWeek()) + ", " + DateTimeUtil.formatDate(now.toLocalDate()));
    }

    // ===== Hoa hồng =====

    /**
     * Điền khối Hoa hồng cho kỳ đã chọn (1 tháng hoặc 1 quý).
     * Chỉ cộng buổi ĐƯỢC TÍNH hoa hồng (xem {@link #isCommissionable}); lọc theo NGÀY DIỄN RA BUỔI (CHỐT-D4).
     * Số tiền lấy từ {@code CommissionAmount} đã chốt trên từng buổi (DBR-58), không tính lại.
     */
    private void fillCommission(PtDashboardView view, int ptId, String periodParam, LocalDate today)
            throws SQLException {
        // B1: danh sách kỳ cho chọn + kỳ đang xem (không hợp lệ → tháng này)
        List<PtDashboardView.PeriodOption> options = buildPeriodOptions(today);
        view.setPeriodOptions(options);
        String periodValue = options.get(0).getValue();
        for (PtDashboardView.PeriodOption option : options) {
            if (option.getValue().equals(periodParam)) {
                periodValue = periodParam;
            }
        }
        view.setPeriodValue(periodValue);

        // B2: đổi kỳ ra khoảng ngày
        boolean isQuarter = periodValue.contains("Q");
        LocalDate fromDate;
        LocalDate toDate;
        if (isQuarter) {
            int year = Integer.parseInt(periodValue.substring(0, 4));
            int quarter = Integer.parseInt(periodValue.substring(6));
            fromDate = LocalDate.of(year, (quarter - 1) * 3 + 1, 1);
            toDate = YearMonth.from(fromDate.plusMonths(2)).atEndOfMonth();
        } else {
            YearMonth month = YearMonth.parse(periodValue);
            fromDate = month.atDay(1);
            toDate = month.atEndOfMonth();
        }

        // B3: cộng từng buổi. Bảng theo nguồn buổi giữ thứ tự cố định để dễ so giữa các kỳ
        Map<String, CommissionLine> lines = new LinkedHashMap<>();
        lines.put("PTSingle", new CommissionLine(sourceTypeLabel("PTSingle")));
        lines.put("PTCouple", new CommissionLine(sourceTypeLabel("PTCouple")));
        lines.put("SingleSession", new CommissionLine(sourceTypeLabel("SingleSession")));
        List<ChartBar> bars = buildChartBars(fromDate, toDate, isQuarter);

        long total = 0;
        int completed = 0;
        int missed = 0;
        int cancelledPaid = 0;
        int cancelledUnpaid = 0;
        for (SessionStatRow row : sessionDAO.findStatRows(ptId, fromDate, toDate)) {
            boolean isCommissionable = isCommissionable(row.getStatus(), row.getCancelType());
            if ("Completed".equals(row.getStatus())) {
                completed++;
            } else if ("Missed".equals(row.getStatus())) {
                missed++;
            } else if ("Cancelled".equals(row.getStatus())) {
                if (isCommissionable) {
                    cancelledPaid++;
                } else {
                    cancelledUnpaid++;
                }
            }
            if (!isCommissionable) {
                continue;
            }
            lines.get(row.getSourceType()).add(row.getCommissionAmount());
            bars.get(chartIndex(row.getSlotDate(), fromDate, isQuarter)).add(row.getCommissionAmount());
            total += row.getCommissionAmount();
        }

        // B4: chỉ giữ nguồn có buổi; tính chiều cao cột biểu đồ
        List<CommissionLine> nonEmptyLines = new ArrayList<>();
        int sessionCount = 0;
        for (CommissionLine line : lines.values()) {
            if (line.getSessionCount() > 0) {
                nonEmptyLines.add(line);
                sessionCount += line.getSessionCount();
            }
        }
        scaleChartBars(bars);

        view.setCommissionLines(nonEmptyLines);
        view.setCommissionTotal(total);
        view.setCommissionSessionCount(sessionCount);
        view.setCompletedCount(completed);
        view.setMissedCount(missed);
        view.setCancelledPaidCount(cancelledPaid);
        view.setCancelledUnpaidCount(cancelledUnpaid);
        view.setChartTitle(isQuarter ? "Theo tháng" : "Theo tuần");
        view.setChartBars(bars);

        // B5: dòng ghi chú dưới tiêu đề, giống prototype ("Tính tới hết 20/09 · 28 buổi đã dạy")
        boolean isCurrent = !today.isBefore(fromDate) && !today.isAfter(toDate);
        String prefix;
        if (isQuarter) {
            prefix = "Tháng " + fromDate.format(MONTH_ONLY) + " – " + toDate.format(MONTH_ONLY)
                    + (isCurrent ? ", tính tới hết " + today.format(DAY_MONTH) : "");
        } else {
            prefix = isCurrent ? "Tính tới hết " + today.format(DAY_MONTH) : "Đã chốt";
        }
        view.setPeriodNote(prefix + " · " + sessionCount + " buổi tính hoa hồng");
    }

    /**
     * Buổi có được tính hoa hồng cho PT không (theo "05_Dashboard_Rules.md" mục 3.2).
     * Completed / Missed → TÍNH (CBR-80). Cancelled với ByPt / ByCustomer → TÍNH (CBR-81, CBR-82).
     * Cancelled với GymClosed / PackageExpired → KHÔNG tính (CBR-57, CBR-55). Scheduled → chưa tính.
     */
    static boolean isCommissionable(String status, String cancelType) {
        if ("Completed".equals(status) || "Missed".equals(status)) {
            return true;
        }
        return "Cancelled".equals(status)
                && ("ByPt".equals(cancelType) || "ByCustomer".equals(cancelType));
    }

    /** Tháng này + 2 tháng trước + quý này + quý trước, VD: 09/2026, 08/2026, 07/2026, Quý III/2026, Quý II/2026. */
    private List<PtDashboardView.PeriodOption> buildPeriodOptions(LocalDate today) {
        List<PtDashboardView.PeriodOption> options = new ArrayList<>();
        YearMonth thisMonth = YearMonth.from(today);
        for (int i = 0; i < MONTH_OPTION_COUNT; i++) {
            YearMonth month = thisMonth.minusMonths(i);
            options.add(new PtDashboardView.PeriodOption(month.toString(), "Tháng " + month.format(MONTH_YEAR)));
        }
        LocalDate quarterStart = today.withDayOfMonth(1).withMonth((today.getMonthValue() - 1) / 3 * 3 + 1);
        for (int i = 0; i < 2; i++) {
            LocalDate start = quarterStart.minusMonths(3L * i);
            int quarter = (start.getMonthValue() - 1) / 3 + 1;
            options.add(new PtDashboardView.PeriodOption(start.getYear() + "-Q" + quarter,
                    "Quý " + ROMAN_QUARTERS[quarter - 1] + "/" + start.getYear()));
        }
        return options;
    }

    /** Các cột biểu đồ rỗng: kỳ tháng → cột theo tuần (01–07, 08–14, 15–21, 22–28, 29–cuối); kỳ quý → 3 cột tháng. */
    private List<ChartBar> buildChartBars(LocalDate fromDate, LocalDate toDate, boolean isQuarter) {
        List<ChartBar> bars = new ArrayList<>();
        if (isQuarter) {
            for (int i = 0; i < 3; i++) {
                bars.add(new ChartBar("T" + fromDate.plusMonths(i).format(MONTH_ONLY)));
            }
            return bars;
        }
        for (int startDay = 1; startDay <= toDate.getDayOfMonth(); startDay += 7) {
            int endDay = Math.min(startDay + 6, toDate.getDayOfMonth());
            bars.add(new ChartBar(String.format("%02d–%02d", startDay, endDay)));
        }
        return bars;
    }

    /** Buổi ngày date rơi vào cột thứ mấy của biểu đồ. */
    private int chartIndex(LocalDate date, LocalDate fromDate, boolean isQuarter) {
        return isQuarter
                ? date.getMonthValue() - fromDate.getMonthValue()
                : (date.getDayOfMonth() - 1) / 7;
    }

    /** Chiều cao cột = % so với cột cao nhất; cột có tiền mà quá thấp thì vẫn cao tối thiểu 6% để nhìn thấy. */
    private void scaleChartBars(List<ChartBar> bars) {
        long max = 0;
        for (ChartBar bar : bars) {
            max = Math.max(max, bar.getAmount());
        }
        for (ChartBar bar : bars) {
            int percent = max == 0 ? 0 : (int) Math.round(bar.getAmount() * 100.0 / max);
            bar.setHeightPercent(bar.getAmount() > 0 ? Math.max(percent, 6) : 0);
        }
    }

    // ===== Buổi cần điểm danh + banner thiếu gói tập =====

    /**
     * Tách từ danh sách buổi Scheduled quanh hôm nay ra: buổi cần điểm danh (BR-54, P-07) và banner thiếu gói (CBR-66).
     * Điểm danh = đã tới giờ bắt đầu và chưa quá windowHours giờ sau khi kết thúc.
     * Banner đếm số khách có buổi từ hôm nay trở đi rơi vào ngày không có Membership, kèm ngày gần nhất.
     */
    private void fillAttendanceAndMissingMembership(PtDashboardView view, List<PtSessionView> scheduled,
            LocalDateTime now, int windowHours) {
        LocalDate today = now.toLocalDate();
        List<PtSessionView> attendance = new ArrayList<>();
        Set<String> missingCustomers = new LinkedHashSet<>();
        LocalDate firstMissingDate = null;

        for (PtSessionView session : scheduled) {
            if (isAttendanceOpen(session.getSlotDate(), session.getStartTime(), session.getEndTime(), now, windowHours)) {
                labelSession(session, today);
                attendance.add(session);
            }
            boolean isUpcomingMissing = session.isMissingMembership() && !session.getSlotDate().isBefore(today);
            if (isUpcomingMissing) {
                missingCustomers.add(session.getCustomerName()
                        + (session.getPartnerName() == null ? "" : " & " + session.getPartnerName()));
                if (firstMissingDate == null) {
                    firstMissingDate = session.getSlotDate();
                }
            }
        }
        view.setAttendanceSessions(attendance);
        view.setMissingMembershipCustomerCount(missingCustomers.size());
        view.setMissingMembershipFirstDate(firstMissingDate == null ? null : firstMissingDate.format(DAY_MONTH));
    }

    /**
     * Buổi có đang trong cửa sổ điểm danh không: giờ bắt đầu ≤ bây giờ ≤ giờ kết thúc + windowHours (BR-54, P-07).
     * Tách riêng (không đụng DB) để unit test được các mốc biên.
     */
    static boolean isAttendanceOpen(LocalDate date, LocalTime startTime, LocalTime endTime,
            LocalDateTime now, int windowHours) {
        LocalDateTime startAt = LocalDateTime.of(date, startTime);
        LocalDateTime deadline = LocalDateTime.of(date, endTime).plusHours(windowHours);
        return !now.isBefore(startAt) && !now.isAfter(deadline);
    }

    // ===== Cột phụ =====

    /** Khách phụ trách vào ngày onDate + đánh dấu ai sắp hết gói (≤ 7 ngày). */
    private void fillClients(PtDashboardView view, int ptId, LocalDate onDate) throws SQLException {
        List<PtClientView> clients = subscriptionDAO.findClientsOfPt(ptId, onDate);
        int endingSoon = 0;
        for (PtClientView client : clients) {
            client.setPackageLabel(sourceTypeLabel(client.getSourceType()));
            client.setEndDateText(DateTimeUtil.formatDate(client.getEndDate()));
            client.setFrozen("Frozen".equals(client.getStatus()));
            boolean isEndingSoon = !client.getEndDate().isAfter(onDate.plusDays(ENDING_SOON_DAYS));
            client.setEndingSoon(isEndingSoon);
            if (isEndingSoon) {
                endingSoon++;
            }
        }
        view.setClients(clients);
        view.setClientsEndingSoon(endingSoon);
    }

    /**
     * Tóm tắt HÔM NAY cho thẻ chào: số buổi hôm nay (mọi trạng thái) và số slot trống từ hôm nay tới Chủ nhật.
     */
    private void fillToday(PtDashboardView view, int ptId, LocalDate today) throws SQLException {
        LocalDate thisSunday = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        view.setTodaySessionCount(sessionDAO.findStatRows(ptId, today, today).size());
        view.setTodayWeekFreeSlots(timeSlotDAO.countFreeSlots(ptId, today, thisSunday));
    }

    /**
     * Ô "Tuần" và ô "Tháng" của cột phụ — xét theo ngày anchor (ô "Từ ngày"), không theo hôm nay.
     * Tuần = thứ 2 → chủ nhật chứa anchor: đếm buổi theo trạng thái + slot còn trống.
     * Tháng = tháng chứa anchor, chia tiền hoa hồng làm 3 phần: đã chốt (isCommissionable), chờ chốt (Scheduled đã qua), dự kiến (Scheduled từ hôm nay trở đi).
     * VD David, anchor 23/09 (hôm nay 30/09) → Tháng 09/2026: đã chốt 0đ, chờ chốt 270.000đ (1 buổi), dự kiến 0đ.
     */
    private void fillWeekAndMonth(PtDashboardView view, int ptId, LocalDate anchor, LocalDate today)
            throws SQLException {
        // B1: khoảng ngày của tuần và tháng chứa anchor
        LocalDate weekStart = anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd = weekStart.plusDays(6);
        YearMonth month = YearMonth.from(anchor);
        LocalDate monthStart = month.atDay(1);
        LocalDate monthEnd = month.atEndOfMonth();
        LocalDate readFrom = weekStart.isBefore(monthStart) ? weekStart : monthStart;
        LocalDate readTo = weekEnd.isAfter(monthEnd) ? weekEnd : monthEnd;

        // B2: cộng / đếm từng buổi vào tuần và tháng
        int weekScheduled = 0;
        int weekTaught = 0;
        int weekMissed = 0;
        int weekCancelled = 0;
        int earnedCount = 0;
        long earnedAmount = 0;
        int pendingCount = 0;
        long pendingAmount = 0;
        int expectedCount = 0;
        long expectedAmount = 0;
        for (SessionStatRow row : sessionDAO.findStatRows(ptId, readFrom, readTo)) {
            LocalDate date = row.getSlotDate();
            String status = row.getStatus();

            boolean isInWeek = !date.isBefore(weekStart) && !date.isAfter(weekEnd);
            if (isInWeek) {
                switch (status) {
                    case "Completed":
                        weekTaught++;
                        break;
                    case "Missed":
                        weekMissed++;
                        break;
                    case "Cancelled":
                        weekCancelled++;
                        break;
                    default:
                        weekScheduled++;
                        break;
                }
            }

            boolean isInMonth = !date.isBefore(monthStart) && !date.isAfter(monthEnd);
            if (!isInMonth) {
                continue;
            }
            if (isCommissionable(status, row.getCancelType())) {
                earnedCount++;
                earnedAmount += row.getCommissionAmount();
            } else if ("Scheduled".equals(status) && date.isBefore(today)) {
                pendingCount++;
                pendingAmount += row.getCommissionAmount();
            } else if ("Scheduled".equals(status)) {
                expectedCount++;
                expectedAmount += row.getCommissionAmount();
            }
        }

        // B3: nhãn ghi rõ tuần nào, tháng nào — "Tuần này · 28/09 – 04/10" hoặc "Tuần 21/09 – 27/09"
        boolean isThisWeek = !today.isBefore(weekStart) && !today.isAfter(weekEnd);
        view.setWeekLabel((isThisWeek ? "Tuần này · " : "Tuần ")
                + weekStart.format(DAY_MONTH) + " – " + weekEnd.format(DAY_MONTH));
        view.setWeekScheduled(weekScheduled);
        view.setWeekTaught(weekTaught);
        view.setWeekMissed(weekMissed);
        view.setWeekCancelled(weekCancelled);
        // Slot trống chỉ tính từ hôm nay trở đi (slot ngày đã qua không còn đặt được); tuần đã qua → null, JSP ẩn dòng
        view.setWeekFreeSlots(weekEnd.isBefore(today) ? null
                : timeSlotDAO.countFreeSlots(ptId, weekStart.isAfter(today) ? weekStart : today, weekEnd));

        view.setMonthLabel((month.equals(YearMonth.from(today)) ? "Tháng này · " : "Tháng ") + month.format(MONTH_YEAR));
        view.setMonthEarnedCount(earnedCount);
        view.setMonthEarnedAmount(earnedAmount);
        view.setMonthPendingCount(pendingCount);
        view.setMonthPendingAmount(pendingAmount);
        view.setMonthExpectedCount(expectedCount);
        view.setMonthExpectedAmount(expectedAmount);
    }

    // ===== Hàm phụ =====

    /** Gắn nhãn ngày, giờ, tên gói, trạng thái tiếng Việt + màu badge cho 1 buổi. */
    private void labelSession(PtSessionView session, LocalDate today) {
        LocalDate date = session.getSlotDate();
        String dayLabel;
        if (date.equals(today)) {
            dayLabel = "Hôm nay";
        } else if (date.equals(today.plusDays(1))) {
            dayLabel = "Mai";
        } else if (date.equals(today.minusDays(1))) {
            dayLabel = "Hôm qua";
        } else {
            dayLabel = date.format(DAY_MONTH);
        }
        session.setDayLabel(dayLabel);
        session.setTimeRange(DateTimeUtil.formatTime(session.getStartTime())
                + " – " + DateTimeUtil.formatTime(session.getEndTime()));
        session.setPackageLabel(sourceTypeLabel(session.getSourceType()));

        // GBR-01: trạng thái lưu tiếng Anh, hiện tiếng Việt. GBR-28: vàng = chờ, xanh = thành công, đỏ = hủy / thất bại
        switch (session.getStatus()) {
            case "Completed":
                session.setStatusLabel("Hoàn thành");
                session.setStatusBadge("badge-success");
                break;
            case "Missed":
                session.setStatusLabel("Vắng");
                session.setStatusBadge("badge-danger");
                break;
            case "Cancelled":
                session.setStatusLabel("Đã hủy");
                session.setStatusBadge("badge-danger");
                break;
            default:
                session.setStatusLabel("Đã xếp");
                session.setStatusBadge("badge-warning");
                break;
        }
    }

    /** Đổi loại gói của buổi PT sang tên hiển thị (khớp cột "Nguồn buổi" của prototype). */
    private String sourceTypeLabel(String sourceType) {
        if ("PTSingle".equals(sourceType)) {
            return "Gói PT cá nhân";
        }
        if ("PTCouple".equals(sourceType)) {
            return "Gói PT Couple";
        }
        if ("SingleSession".equals(sourceType)) {
            return "Buổi lẻ khách tự đặt";
        }
        return sourceType;
    }

    /** Lời chào theo giờ: trước 11h sáng, trước 14h trưa, trước 18h chiều, còn lại tối. */
    private String buildGreeting(LocalTime now) {
        if (now.isBefore(LocalTime.of(11, 0))) {
            return "Chào buổi sáng";
        }
        if (now.isBefore(LocalTime.of(14, 0))) {
            return "Chào buổi trưa";
        }
        if (now.isBefore(LocalTime.of(18, 0))) {
            return "Chào buổi chiều";
        }
        return "Chào buổi tối";
    }

    /** VD MONDAY → "Thứ 2", SUNDAY → "Chủ nhật". */
    private String dayOfWeekLabel(DayOfWeek dayOfWeek) {
        return dayOfWeek == DayOfWeek.SUNDAY ? "Chủ nhật" : "Thứ " + (dayOfWeek.getValue() + 1);
    }
}
