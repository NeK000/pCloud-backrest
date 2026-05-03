package io.github.ninik.pcloudbackrest.schedule;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SchedulerService {
    private static final Logger log = LoggerFactory.getLogger(SchedulerService.class);

    private final CronSchedule schedule;
    private final Clock clock;
    private final ScheduledJob job;
    private final WaitStrategy waitStrategy;
    private final ShutdownHookRegistrar shutdownHookRegistrar;
    private final AtomicBoolean running = new AtomicBoolean(true);

    public SchedulerService(CronSchedule schedule, Clock clock, ScheduledJob job) {
        this(schedule, clock, job, SchedulerService::sleepUntilElapsed, hook -> Runtime.getRuntime().addShutdownHook(hook));
    }

    SchedulerService(
            CronSchedule schedule,
            Clock clock,
            ScheduledJob job,
            WaitStrategy waitStrategy,
            ShutdownHookRegistrar shutdownHookRegistrar
    ) {
        this.schedule = schedule;
        this.clock = clock;
        this.job = job;
        this.waitStrategy = waitStrategy;
        this.shutdownHookRegistrar = shutdownHookRegistrar;
    }

    public int run(boolean runOnStart) {
        Thread schedulerThread = Thread.currentThread();
        shutdownHookRegistrar.register(new Thread(() -> {
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
            try {
                boolean waitCompleted = waitStrategy.waitFor(wait, running);
                if (waitCompleted && running.get()) {
                    runJob();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                running.set(false);
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

    static boolean sleepUntilElapsed(Duration wait, AtomicBoolean running) throws InterruptedException {
        long millis = Math.max(0L, wait.toMillis());
        long deadline = System.currentTimeMillis() + millis;
        while (running.get() && millis > 0L) {
            Thread.sleep(Math.min(millis, 60_000L));
            millis = deadline - System.currentTimeMillis();
        }
        return running.get();
    }

    @FunctionalInterface
    public interface ScheduledJob {
        int runOnce() throws Exception;
    }

    @FunctionalInterface
    interface WaitStrategy {
        boolean waitFor(Duration wait, AtomicBoolean running) throws InterruptedException;
    }

    @FunctionalInterface
    interface ShutdownHookRegistrar {
        void register(Thread hook);
    }
}
