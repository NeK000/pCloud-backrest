package io.github.ninik.pcloudbackrest;

import com.pcloud.sdk.ApiClient;
import io.github.ninik.pcloudbackrest.config.Config;
import io.github.ninik.pcloudbackrest.gateway.PCloudGateway;
import io.github.ninik.pcloudbackrest.model.RemoteFileEntry;
import io.github.ninik.pcloudbackrest.model.ResultSummary;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Constructor;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppTest {
    @TempDir
    Path tempDir;

    @AfterEach
    void resetMainHooks() {
        App.runFunction = App::run;
        App.exitFunction = System::exit;
        App.envFunction = System::getenv;
        App.oneShotRunnerFunction = App::runOnce;
        App.schedulerRunnerFunction = App.productionSchedulerRunner();
        Thread.interrupted();
    }

    @Test
    void privateConstructorCanBeCovered() throws Exception {
        Constructor<App> constructor = App.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        constructor.newInstance();
    }

    @Test
    void mainDoesNotExitForZeroCode() {
        AtomicInteger exits = new AtomicInteger();
        App.runFunction = () -> 0;
        App.exitFunction = exits::set;

        App.main(new String[0]);

        assertEquals(0, exits.get());
    }

    @Test
    void mainExitsForNonZeroCode() {
        AtomicInteger exits = new AtomicInteger();
        App.runFunction = () -> 2;
        App.exitFunction = exits::set;

        App.main(new String[0]);

        assertEquals(2, exits.get());
    }

    @Test
    void runReturnsOneForInvalidConfig() {
        assertEquals(1, App.run(Map.of(), config -> 0, (schedule, config, runner) -> 0));
    }

    @Test
    void defaultRunUsesConfiguredSuppliers() {
        App.envFunction = () -> baseEnv("backup", Map.of());
        App.oneShotRunnerFunction = config -> 3;
        App.schedulerRunnerFunction = (schedule, config, runner) -> 8;

        assertEquals(3, App.run());
    }

    @Test
    void runExecutesOneShotWhenNoScheduleIsConfigured() {
        AtomicInteger calls = new AtomicInteger();

        int exitCode = App.run(baseEnv("backup", Map.of()), config -> {
            calls.incrementAndGet();
            return 5;
        }, (schedule, config, runner) -> 0);

        assertEquals(5, exitCode);
        assertEquals(1, calls.get());
    }

    @Test
    void runExecutesSchedulerWhenScheduleIsConfigured() {
        Map<String, String> env = baseEnv("backup", Map.of(
                "SCHEDULE_CRON", "0 3 * * *",
                "SCHEDULE_TIMEZONE", "UTC",
                "SCHEDULE_RUN_ON_START", "true"
        ));

        int exitCode = App.run(env, config -> 9, (schedule, config, runner) -> {
            assertEquals("0 3 * * *", schedule.toString());
            assertTrue(config.scheduleRunOnStart());
            return runner.run(config);
        });

        assertEquals(9, exitCode);
    }

    @Test
    void productionSchedulerRunnerCanBeInvoked() {
        Config config = Config.fromEnv(baseEnv("backup", Map.of(
                "SCHEDULE_CRON", "0 3 * * *",
                "SCHEDULE_TIMEZONE", "UTC",
                "SCHEDULE_RUN_ON_START", "true"
        )));

        try {
            int exitCode = App.productionSchedulerRunner().run(config.schedule().orElseThrow(), config, ignored -> {
                Thread.currentThread().interrupt();
                return 0;
            });

            assertEquals(0, exitCode);
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void runReturnsOneForUnexpectedFailure() {
        int exitCode = App.run(baseEnv("backup", Map.of()), config -> {
            throw new RuntimeException("boom");
        }, (schedule, config, runner) -> 0);

        assertEquals(1, exitCode);
    }

    @Test
    void runOnceReturnsZeroAndClosesGatewayWhenSummaryHasNoFailures() {
        FakeGateway gateway = new FakeGateway();

        int exitCode = App.runOnce(
                config("backup"),
                ignored -> apiClient(),
                ignored -> gateway,
                (config, remoteFolder, pCloudGateway) -> new ResultSummary()
        );

        assertEquals(0, exitCode);
        assertTrue(gateway.closed);
    }

    @Test
    void defaultRunOnceCanCompleteDryRunBackupWithoutRemoteCalls() {
        Config config = Config.fromEnv(baseEnv("backup", Map.of("DRY_RUN", "true")));

        assertEquals(0, App.runOnce(config));
    }

    @Test
    void runOnceReturnsTwoWhenSummaryHasFailures() {
        ResultSummary summary = new ResultSummary();
        summary.failed();

        int exitCode = App.runOnce(
                config("backup"),
                ignored -> apiClient(),
                ignored -> new FakeGateway(),
                (config, remoteFolder, gateway) -> summary
        );

        assertEquals(2, exitCode);
    }

    @Test
    void runOnceReturnsOneForConfigurationException() {
        int exitCode = App.runOnce(
                config("backup"),
                ignored -> apiClient(),
                ignored -> new FakeGateway(),
                (config, remoteFolder, gateway) -> {
                    throw new IllegalArgumentException("bad");
                }
        );

        assertEquals(1, exitCode);
    }

    @Test
    void runOnceReturnsOneForFatalException() {
        int exitCode = App.runOnce(
                config("backup"),
                ignored -> {
                    throw new RuntimeException("client failed");
                },
                ignored -> new FakeGateway(),
                (config, remoteFolder, gateway) -> new ResultSummary()
        );

        assertEquals(1, exitCode);
    }

    @Test
    void executeTransferRunsBackupService() throws Exception {
        Files.writeString(tempDir.resolve("file.txt"), "content");
        FakeGateway gateway = new FakeGateway();

        ResultSummary summary = App.executeTransfer(config("backup"), "/Backups/Immich", gateway);

        assertFalse(summary.hasFailures());
        assertEquals(1, gateway.uploadCalls);
    }

    @Test
    void executeTransferRunsRestoreService() throws Exception {
        FakeGateway gateway = new FakeGateway();
        gateway.remoteFiles = List.of();

        ResultSummary summary = App.executeTransfer(config("restore"), "/Backups/Immich", gateway);

        assertFalse(summary.hasFailures());
    }

    private Config config(String mode) {
        return Config.fromEnv(baseEnv(mode, Map.of()));
    }

    private Map<String, String> baseEnv(String mode, Map<String, String> overrides) {
        Map<String, String> env = new HashMap<>();
        env.put("PCLOUD_ACCESS_TOKEN", "token");
        env.put("PCLOUD_REMOTE_FOLDER", "/Backups/Immich");
        env.put("LOCAL_BACKUP_FOLDER", tempDir.toString());
        env.put("MODE", mode);
        env.putAll(overrides);
        return env;
    }

    private ApiClient apiClient() {
        return (ApiClient) Proxy.newProxyInstance(
                ApiClient.class.getClassLoader(),
                new Class<?>[]{ApiClient.class},
                (proxy, method, args) -> null
        );
    }

    private static final class FakeGateway implements PCloudGateway {
        private boolean closed;
        private int uploadCalls;
        private List<RemoteFileEntry> remoteFiles = List.of();

        @Override
        public int ensureFolder(String remoteFolder) {
            return 0;
        }

        @Override
        public boolean folderExists(String remoteFolder) {
            return true;
        }

        @Override
        public List<RemoteFileEntry> listFilesRecursively(String remoteFolder) {
            return remoteFiles;
        }

        @Override
        public Optional<RemoteFileEntry> findFile(String remoteFilePath) {
            return Optional.empty();
        }

        @Override
        public Optional<String> sha1(RemoteFileEntry remoteFile) {
            return Optional.empty();
        }

        @Override
        public void upload(Path localFile, String remoteFolder, String filename, Instant modifiedTime) {
            uploadCalls++;
        }

        @Override
        public void download(RemoteFileEntry remoteFile, Path localFile) {
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
