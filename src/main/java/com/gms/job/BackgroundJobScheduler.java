package com.gms.job;

import com.gms.util.DateTimeUtil;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * BỘ HẸN GIỜ CHẠY JOB — bật / tắt theo vòng đời Tomcat
 * (ScheduledExecutorService của JDK, không cần Quartz).
 *
 * LỊCH CHẠY (giá trị hiện tại)
 * Hằng ngày 00:05 giờ VN, chạy NỐI TIẾP theo thứ tự trong DAILY_JOBS:
 * JOB-01 cộng ngày bảo lưu → JOB-02 hết hạn gói → JOB-03 hết hạn vé ngày →
 * JOB-04 chốt buổi PT → JOB-08 nhắc khách ngày mai
 * (JOB-01/02/03 hiện là khung trống TODO do thành viên khác làm; JOB-04, JOB-08
 * đã chạy thật.)
 * Mỗi phút, các job trong MINUTE_JOBS chạy độc lập (hiện đều là khung TODO).
 * Job hằng ngày lỗi DB → thử lại tối đa 3 lần, mỗi lần cách 5 phút.
 *
 * CÁC HÀM
 * contextInitialized Tomcat gọi khi app khởi động → lên lịch
 * contextDestroyed Tomcat gọi khi app dừng / redeploy → tắt luồng
 * runDailyJobs chạy chuỗi job hằng ngày → runDailyJobWithRetry
 * runMinuteJob chạy 1 job mỗi phút
 *
 * Thêm job: tạo class implements BackgroundJob rồi thêm vào DAILY_JOBS hoặc
 * MINUTE_JOBS.
 */
@WebListener
public class BackgroundJobScheduler implements ServletContextListener {

    // ===== Cấu hình lịch chạy (giá trị hiện tại ghi bên phải) =====
    private static final LocalTime DAILY_RUN_TIME = LocalTime.of(0, 5); // = 00:05 giờ VN mỗi ngày
    private static final int DAILY_MAX_RETRIES = 3; // = lỗi DB thì thử lại tối đa 3 lần
    private static final long DAILY_RETRY_DELAY_MINUTES = 5; // = mỗi lần thử lại cách 5 phút

    /** Job hằng ngày — THỨ TỰ TRONG LIST LÀ THỨ TỰ CHẠY, không đảo. */
    private static final List<BackgroundJob> DAILY_JOBS = List.of(
            new ResumeExpiredFreezesJob(), // JOB-01: hết bảo lưu (khung TODO)
            new ExpireMembershipPackagesJob(), // JOB-02: gói hết hạn → Expired (khung TODO)
            new ExpireUnusedDayPassesJob(), // JOB-03: vé ngày không dùng → Expired (khung TODO)
            new AutoCloseUnmarkedSessionsJob(), // JOB-04: chốt buổi PT đã qua ngày (AutoCloseSessionService)
            new RemindMissingGymAccessJob() // JOB-08: chạy SAU các job hết hạn để xét quyền vào gym mới nhất
    );

    /** Job chạy mỗi phút — độc lập nhau. */
    private static final List<BackgroundJob> MINUTE_JOBS = List.of(
            new CancelOverdueOnlineOrdersJob(),
            new ExpireTimedOutTransactionsJob(),
            new LeaveRequestReminderJob(),
            new ReconcileVnPayTransactionsJob());

    private ScheduledExecutorService dailyExecutor;
    private ScheduledExecutorService minuteExecutor;

    // =================================================================================================
    // 1. BẬT / TẮT THEO TOMCAT
    // =================================================================================================

    /** HÀM CHÍNH — Tomcat gọi 1 lần khi ứng dụng khởi động (nhờ @WebListener). */
    @Override
    public void contextInitialized(ServletContextEvent event) {
        // B1: tạo 2 luồng riêng — job hằng ngày có thể ngủ chờ thử lại 5 phút, không
        // được làm trễ job mỗi phút
        dailyExecutor = Executors.newSingleThreadScheduledExecutor();
        minuteExecutor = Executors.newScheduledThreadPool(MINUTE_JOBS.size());

        // B2: lên lịch chuỗi job hằng ngày lúc 00:05
        long initialDelaySeconds = calculateSecondsUntilNextDailyRun();
        long oneDaySeconds = TimeUnit.DAYS.toSeconds(1);
        dailyExecutor.scheduleAtFixedRate(this::runDailyJobs, initialDelaySeconds, oneDaySeconds, TimeUnit.SECONDS);

        // B3: lên lịch từng job mỗi phút
        for (BackgroundJob job : MINUTE_JOBS) {
            Runnable task = () -> runMinuteJob(job);
            minuteExecutor.scheduleWithFixedDelay(task, 1, 1, TimeUnit.MINUTES);
        }

        System.out
                .println("[JOB] Đã khởi động scheduler. Lượt hằng ngày kế tiếp sau " + initialDelaySeconds + " giây.");
    }

    /** HÀM CHÍNH — Tomcat gọi khi ứng dụng dừng / redeploy. */
    @Override
    public void contextDestroyed(ServletContextEvent event) {
        // Dừng hẳn các luồng, tránh rò rỉ thread khi redeploy nhiều lần trong lúc code
        if (dailyExecutor != null) {
            dailyExecutor.shutdownNow();
        }
        if (minuteExecutor != null) {
            minuteExecutor.shutdownNow();
        }
    }

    // =================================================================================================
    // 2. CHẠY JOB
    // =================================================================================================

    /**
     * HÀM CHÍNH — luồng dailyExecutor gọi mỗi ngày lúc 00:05 (lên lịch ở B2 của
     * contextInitialized).
     * Chạy nối tiếp các job hằng ngày; job trước xong mới tới job sau.
     */
    private void runDailyJobs() {
        for (BackgroundJob job : DAILY_JOBS) {
            runDailyJobWithRetry(job);
        }
    }

    /**
     * HÀM PHỤ — gọi trong vòng lặp của runDailyJobs, mỗi job một lần.
     * Lỗi DB (SQLException) → in lỗi, chờ 5 phút, thử lại; quá 3 lần thì bỏ, chờ
     * lượt ngày mai.
     * Lỗi khác (RuntimeException) → in lỗi và bỏ qua job này.
     */
    private void runDailyJobWithRetry(BackgroundJob job) {
        for (int attempt = 1; attempt <= DAILY_MAX_RETRIES + 1; attempt++) {
            try {
                job.run();
                return;
            } catch (SQLException e) {
                e.printStackTrace();
                boolean hasRetryLeft = attempt <= DAILY_MAX_RETRIES;
                if (!hasRetryLeft) {
                    System.err.println("[JOB][CRITICAL] " + job.getName() + " thất bại sau " + DAILY_MAX_RETRIES
                            + " lần thử lại, chờ lượt kế tiếp.");
                    return;
                }
                System.err.println("[JOB] " + job.getName() + " lỗi DB, thử lại sau " + DAILY_RETRY_DELAY_MINUTES
                        + " phút (lần " + attempt + ").");
                boolean isStillRunning = sleepMinutes(DAILY_RETRY_DELAY_MINUTES);
                if (!isStillRunning) {
                    return;
                }
            } catch (RuntimeException e) {
                // Bắt RuntimeException ở tầng scheduler (ngoại lệ so với quy tắc Service): nếu
                // để lọt,
                // ScheduledExecutorService sẽ lặng lẽ HỦY luôn các lượt chạy sau của cả chuỗi
                // job.
                e.printStackTrace();
                return;
            }
        }
    }

    /**
     * HÀM CHÍNH — luồng minuteExecutor gọi mỗi phút cho từng job (lên lịch ở B3 của
     * contextInitialized).
     * Lỗi thì chỉ in ra, không thử lại: 1 phút sau job tự chạy lượt mới.
     */
    private void runMinuteJob(BackgroundJob job) {
        try {
            job.run();
        } catch (SQLException e) {
            e.printStackTrace();
        } catch (RuntimeException e) {
            // Cùng lý do như runDailyJobWithRetry: lọt exception là job bị hủy vĩnh viễn
            e.printStackTrace();
        }
    }

    // =================================================================================================
    // 3. TÍNH GIỜ
    // =================================================================================================

    /**
     * HÀM PHỤ — gọi ở B2 của contextInitialized.
     * Số giây từ bây giờ tới 00:05 kế tiếp (giờ Việt Nam).
     * VD bây giờ 23:00 → còn 3.900 giây (1 giờ 5 phút); bây giờ 00:10 → chờ tới
     * 00:05 ngày mai.
     */
    private long calculateSecondsUntilNextDailyRun() {
        LocalDateTime now = DateTimeUtil.now();
        LocalDateTime nextRun = now.toLocalDate().atTime(DAILY_RUN_TIME);
        boolean hasPassedToday = !nextRun.isAfter(now);
        if (hasPassedToday) {
            nextRun = nextRun.plusDays(1);
        }
        return Duration.between(now, nextRun).getSeconds();
    }

    /**
     * HÀM PHỤ — gọi trong runDailyJobWithRetry trước mỗi lần thử lại.
     * Ngủ chờ; trả false nếu bị ngắt vì ứng dụng đang tắt (khi đó thôi thử lại).
     */
    private boolean sleepMinutes(long minutes) {
        try {
            Thread.sleep(TimeUnit.MINUTES.toMillis(minutes));
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
