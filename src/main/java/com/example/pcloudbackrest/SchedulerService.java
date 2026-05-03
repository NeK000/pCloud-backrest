package com.example.pcloudbackrest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.concurrent.atomic.AtomicBoolean;

final class SchedulerService {
    private static final Logger log = LoggerFactory.getLogger(SchedulerService.class);

    private final CronSchedule schedule;
    private final Clock clock;
    private final ScheduledJob job;
    private final AtomicBoolean running = new AtomicBoolean(true);

    SchedulerService(CronSchedule schedule, Clock clock, ScheduledJob job) {
        this.schedule = schedule;
        this.clock = clock;
        this.job = job;
    }

    int run(boolean runOnStart) {
        Thread schedulerThread = Thread.currentThread();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            running.set(false);
            schedulerThread.interrupt();
        }, "shutdown"));
        log.info("Scheduler enabled: cron='{}', timezone='{}', runOnStart={}", schedule, schedule.zoneId(), runOnStart);

        if (runOnStart) {
            runJob();
        }

        while (running.get()) {
            ZonedDateTime now = ZonedDateTime.now(clock).withZoneSameInstant(schedule.zoneId());
            ZonedDateTime nextRun = schedule.nextAfter(now);
            Duration wait = Duration.between(now, nextRun);
            log.info("Next scheduled run at {}", nextRun);
            sleep(wait);
            if (running.get()) {
                runJob();
            }
        }
        return 0;
    }

    private void runJob() {
        try {
            int exitCode = job.runOnce();
            if (exitCode == 0) {
                log.info("Scheduled run completed successfully.");
            } else {
                log.warn("Scheduled run completed with exit code {}. Scheduler will continue.", exitCode);
            }
        } catch (Exception e) {
            log.error("Scheduled run failed. Scheduler will continue.", e);
        }
    }

    private void sleep(Duration wait) {
        long millis = Math.max(0L, wait.toMillis());
        long deadline = System.currentTimeMillis() + millis;
        while (running.get() && millis > 0L) {
            try {
                Thread.sleep(Math.min(millis, 60_000L));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                running.set(false);
                return;
            }
            millis = deadline - System.currentTimeMillis();
        }
    }

    @FunctionalInterface
    interface ScheduledJob {
        int runOnce() throws Exception;
    }
}
