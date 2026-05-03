package io.github.ninik.pcloudbackrest;

import com.pcloud.sdk.ApiClient;
import io.github.ninik.pcloudbackrest.config.Config;
import io.github.ninik.pcloudbackrest.gateway.PCloudClientFactory;
import io.github.ninik.pcloudbackrest.gateway.PCloudGateway;
import io.github.ninik.pcloudbackrest.gateway.SdkPCloudGateway;
import io.github.ninik.pcloudbackrest.model.ResultSummary;
import io.github.ninik.pcloudbackrest.schedule.SchedulerService;
import io.github.ninik.pcloudbackrest.service.BackupService;
import io.github.ninik.pcloudbackrest.service.FileComparisonService;
import io.github.ninik.pcloudbackrest.service.RestoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.util.Map;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public final class App {
    private static final Logger log = LoggerFactory.getLogger(App.class);
    static IntSupplier runFunction = App::run;
    static IntConsumer exitFunction = System::exit;
    static Supplier<Map<String, String>> envFunction = System::getenv;
    static OneShotRunner oneShotRunnerFunction = App::runOnce;
    static SchedulerRunner schedulerRunnerFunction = productionSchedulerRunner();

    private App() {
    }

    public static void main(String[] args) {
        int exitCode = runFunction.getAsInt();
        if (exitCode != 0) {
            exitFunction.accept(exitCode);
        }
    }

    static int run() {
        return run(envFunction.get(), oneShotRunnerFunction, schedulerRunnerFunction);
    }

    static int run(Map<String, String> env, OneShotRunner oneShotRunner, SchedulerRunner schedulerRunner) {
        try {
            Config config = Config.fromEnv(env);
            return config.schedule()
                    .map(schedule -> schedulerRunner.run(schedule, config, oneShotRunner))
                    .orElseGet(() -> oneShotRunner.run(config));
        } catch (IllegalArgumentException e) {
            log.error("Configuration error: {}", e.getMessage());
            return 1;
        } catch (Exception e) {
            log.error("Fatal error", e);
            return 1;
        }
    }

    static SchedulerRunner productionSchedulerRunner() {
        return (schedule, config, runner) ->
                new SchedulerService(schedule, Clock.system(schedule.zoneId()), () -> runner.run(config))
                        .run(config.scheduleRunOnStart());
    }

    static int runOnce(Config config) {
        return runOnce(config, new PCloudClientFactory()::create, SdkPCloudGateway::new, App::executeTransfer);
    }

    static int runOnce(
            Config config,
            ClientProvider clientProvider,
            GatewayProvider gatewayProvider,
            TransferExecutor transferExecutor
    ) {
        try {
            String remoteFolder = config.effectiveRemoteFolder(Clock.systemUTC());
            log.info("Starting {}: local={}, remote={}, dryRun={}",
                    config.mode().name().toLowerCase(), config.localBackupFolder(), remoteFolder, config.dryRun());

            ApiClient apiClient = clientProvider.create(config);
            try (PCloudGateway gateway = gatewayProvider.create(apiClient)) {
                ResultSummary summary = transferExecutor.run(config, remoteFolder, gateway);
                summary.log(log);
                return summary.hasFailures() ? 2 : 0;
            }
        } catch (IllegalArgumentException e) {
            log.error("Configuration error: {}", e.getMessage());
            return 1;
        } catch (Exception e) {
            log.error("Fatal error", e);
            return 1;
        }
    }

    static ResultSummary executeTransfer(Config config, String remoteFolder, PCloudGateway gateway) throws Exception {
        return switch (config.mode()) {
            case BACKUP -> new BackupService(gateway, new FileComparisonService()).run(config, remoteFolder);
            case RESTORE -> new RestoreService(gateway, new FileComparisonService()).run(config, remoteFolder);
        };
    }

    @FunctionalInterface
    interface OneShotRunner {
        int run(Config config);
    }

    @FunctionalInterface
    interface SchedulerRunner {
        int run(io.github.ninik.pcloudbackrest.schedule.CronSchedule schedule, Config config, OneShotRunner runner);
    }

    @FunctionalInterface
    interface ClientProvider {
        ApiClient create(Config config);
    }

    @FunctionalInterface
    interface GatewayProvider {
        PCloudGateway create(ApiClient apiClient);
    }

    @FunctionalInterface
    interface TransferExecutor {
        ResultSummary run(Config config, String remoteFolder, PCloudGateway gateway) throws Exception;
    }
}
