package io.github.ninik.pcloudbackrest.schedule;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchedulerServiceTest {
    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-05-03T10:00:00Z"), UTC);

    @Test
    void runOnStartExecutesSuccessfulJobAndThenStops() {
        AtomicInteger runs = new AtomicInteger();
        SchedulerService scheduler = scheduler(
                () -> {
                    runs.incrementAndGet();
                    return 0;
                },
                (wait, running) -> {
                    running.set(false);
                    return false;
                },
                hook -> {
                }
        );

        assertEquals(0, scheduler.run(true));
        assertEquals(1, runs.get());
    }

    @Test
    void runOnStartKeepsSchedulerAliveAfterNonZeroJobResult() {
        AtomicInteger runs = new AtomicInteger();
        SchedulerService scheduler = scheduler(
                () -> {
                    runs.incrementAndGet();
                    return 2;
                },
                (wait, running) -> {
                    running.set(false);
                    return false;
                },
                hook -> {
                }
        );

        assertEquals(0, scheduler.run(true));
        assertEquals(1, runs.get());
    }

    @Test
    void runOnStartCatchesJobExceptionAndContinues() {
        AtomicInteger runs = new AtomicInteger();
        SchedulerService scheduler = scheduler(
                () -> {
                    runs.incrementAndGet();
                    throw new IllegalStateException("boom");
                },
                (wait, running) -> {
                    running.set(false);
                    return false;
                },
                hook -> {
                }
        );

        assertEquals(0, scheduler.run(true));
        assertEquals(1, runs.get());
    }

    @Test
    void scheduledRunExecutesAfterWaitCompletes() {
        AtomicInteger runs = new AtomicInteger();
        AtomicInteger waits = new AtomicInteger();
        SchedulerService scheduler = scheduler(
                () -> {
                    runs.incrementAndGet();
                    return 0;
                },
                (wait, running) -> {
                    if (waits.incrementAndGet() == 1) {
                        return true;
                    }
                    running.set(false);
                    return false;
                },
                hook -> {
                }
        );

        assertEquals(0, scheduler.run(false));
        assertEquals(1, runs.get());
        assertEquals(2, waits.get());
    }

    @Test
    void interruptedWaitStopsSchedulerAndRestoresInterruptFlag() {
        SchedulerService scheduler = scheduler(
                () -> 0,
                (wait, running) -> {
                    throw new InterruptedException("stop");
                },
                hook -> {
                }
        );

        try {
            assertEquals(0, scheduler.run(false));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void shutdownHookStopsSchedulerBeforeLoopStarts() {
        SchedulerService scheduler = scheduler(
                () -> 0,
                (wait, running) -> {
                    throw new AssertionError("wait should not be called");
                },
                Thread::run
        );

        try {
            assertEquals(0, scheduler.run(false));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void defaultSleepReturnsImmediatelyForZeroOrStoppedState() throws Exception {
        AtomicBoolean running = new AtomicBoolean(true);

        assertTrue(SchedulerService.sleepUntilElapsed(Duration.ZERO, running));
        assertTrue(SchedulerService.sleepUntilElapsed(Duration.ofMillis(1), running));

        running.set(false);
        assertFalse(SchedulerService.sleepUntilElapsed(Duration.ofMillis(1), running));
    }

    @Test
    void productionConstructorCanBeCreated() {
        SchedulerService scheduler = new SchedulerService(CronSchedule.parse("0 3 * * *", UTC), CLOCK, () -> 0);

        assertEquals(SchedulerService.class, scheduler.getClass());
    }

    private SchedulerService scheduler(
            SchedulerService.ScheduledJob job,
            SchedulerService.WaitStrategy waitStrategy,
            SchedulerService.ShutdownHookRegistrar shutdownHookRegistrar
    ) {
        return new SchedulerService(
                CronSchedule.parse("1 10 * * *", UTC),
                CLOCK,
                job,
                waitStrategy,
                shutdownHookRegistrar
        );
    }
}
