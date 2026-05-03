package com.example.pcloudbackrest;

import com.pcloud.sdk.ApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;

public final class App {
    private static final Logger log = LoggerFactory.getLogger(App.class);

    private App() {
    }

    public static void main(String[] args) {
        int exitCode = run();
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    static int run() {
        try {
            Config config = Config.fromEnv(System.getenv());
            String remoteFolder = config.effectiveRemoteFolder(Clock.systemUTC());
            log.info("Starting {}: local={}, remote={}, dryRun={}",
                    config.mode().name().toLowerCase(), config.localBackupFolder(), remoteFolder, config.dryRun());

            ApiClient apiClient = new PCloudClientFactory().create(config);
            try (PCloudGateway gateway = new SdkPCloudGateway(apiClient)) {
                ResultSummary summary = switch (config.mode()) {
                    case BACKUP -> new BackupService(gateway, new FileComparisonService()).run(config, remoteFolder);
                    case RESTORE -> new RestoreService(gateway, new FileComparisonService()).run(config, remoteFolder);
                };
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
}
