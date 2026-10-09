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
 * Khởi động/dừng các job chạy ngầm theo vòng đời Tomcat (dùng ScheduledExecutorService của JDK, không cần Quartz).
 * Job hằng ngày chạy lúc 00:05 giờ VN (nối tiếp: cộng bảo lưu → hết hạn gói → hết hạn vé ngày → chốt buổi PT); job mỗi phút chạy độc lập.
 * Thêm job: tạo class implements BackgroundJob rồi thêm vào DAILY_JOBS hoặc MINUTE_JOBS.
 */
@WebListener
public class BackgroundJobScheduler implements ServletContextListener {

    private static final LocalTime DAILY_RUN_TIME = LocalTime.of(0, 5);
    private static final int DAILY_MAX_RETRIES = 3;
    private static final long DAILY_RETRY_DELAY_MINUTES = 5;

    /** Job hằng ngày — THỨ TỰ TRONG LIST LÀ THỨ TỰ CHẠY, không đảo. */
    private static final List<BackgroundJob> DAILY_JOBS = List.of(
            new ResumeExpiredFreezesJob(),
            new ExpireMembershipPackagesJob(),
            new ExpireUnusedDayPassesJob(),
            new AutoCloseUnmarkedSessionsJob()
    );

    /** Job chạy mỗi phút — độc lập nhau. */
    private static final List<BackgroundJob> MINUTE_JOBS = List.of(
            new CancelOverdueOnlineOrdersJob(),
            new ExpireTimedOutTransactionsJob(),
            new AutoRejectOverdueLeaveRequestsJob()
    );

    private ScheduledExecutorService dailyExecutor;
    private ScheduledExecutorService minuteExecutor;

    // ===== Tomcat gọi khi ứng dụng khởi động =====

    @Override
    public void contextInitialized(ServletContextEvent event) {
        // B1: tạo 2 luồng riêng — job hằng ngày có thể ngủ chờ thử lại 5 phút, không được làm trễ job mỗi phút
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

        System.out.println("[JOB] Đã khởi động scheduler. Lượt hằng ngày kế tiếp sau " + initialDelaySeconds + " giây.");
    }

    // ===== Tomcat gọi khi ứng dụng dừng/redeploy =====

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

    // ===== Chạy job =====

    /**
     * Chạy nối tiếp các job hằng ngày, mỗi job có cơ chế thử lại khi mất kết nối DB.
     */
    private void runDailyJobs() {
        for (BackgroundJob job : DAILY_JOBS) {
            runDailyJobWithRetry(job);
        }
    }

    private void runDailyJobWithRetry(BackgroundJob job) {
        for (int attempt = 1; attempt <= DAILY_MAX_RETRIES + 1; attempt++) {
            try {
                job.run();
                return;
            } catch (SQLException e) {
                e.printStackTrace();
                boolean hasRetryLeft = attempt <= DAILY_MAX_RETRIES;
                if (!hasRetryLeft) {
                    System.err.println("[JOB][CRITICAL] " + job.getName() + " thất bại sau " + DAILY_MAX_RETRIES + " lần thử lại, chờ lượt kế tiếp.");
                    return;
                }
                System.err.println("[JOB] " + job.getName() + " lỗi DB, thử lại sau " + DAILY_RETRY_DELAY_MINUTES + " phút (lần " + attempt + ").");
                boolean isStillRunning = sleepMinutes(DAILY_RETRY_DELAY_MINUTES);
                if (!isStillRunning) {
                    return;
                }
            } catch (RuntimeException e) {
                // Bắt RuntimeException ở tầng scheduler (ngoại lệ so với quy tắc Service): nếu để lọt,
                // ScheduledExecutorService sẽ lặng lẽ HỦY luôn các lượt chạy sau của cả chuỗi job.
                e.printStackTrace();
                return;
            }
        }
    }

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

    // ===== Tiện ích tính giờ =====

    /**
     * Số giây từ bây giờ tới 00:05 kế tiếp (giờ Việt Nam).
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
     * Ngủ chờ thử lại; trả false nếu bị ngắt do ứng dụng đang tắt.
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
