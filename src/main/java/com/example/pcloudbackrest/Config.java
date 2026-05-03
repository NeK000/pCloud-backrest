package com.example.pcloudbackrest;

import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Set;

record Config(
        String accessToken,
        String remoteFolder,
        Path localBackupFolder,
        Mode mode,
        boolean dryRun,
        boolean deleteExtraFilesOnRestore,
        String logLevel,
        boolean backupTimestamped,
        String apiHost
) {
    private static final Set<String> DANGEROUS_PATHS = Set.of(
            "/", "/bin", "/boot", "/dev", "/etc", "/home", "/lib", "/lib64",
            "/opt", "/proc", "/root", "/run", "/sbin", "/sys", "/tmp", "/usr", "/var"
    );
    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    static Config fromEnv(Map<String, String> env) {
        String token = required(env, "PCLOUD_ACCESS_TOKEN");
        String remoteFolder = RemotePathService.normalizeRemoteFolder(required(env, "PCLOUD_REMOTE_FOLDER"));
        Path localFolder = Path.of(required(env, "LOCAL_BACKUP_FOLDER")).toAbsolutePath().normalize();
        Mode mode = Mode.parse(required(env, "MODE"));

        validateLocalBackupFolder(localFolder, mode);

        return new Config(
                token,
                remoteFolder,
                localFolder,
                mode,
                parseBoolean(env.get("DRY_RUN"), false, "DRY_RUN"),
                parseBoolean(env.get("DELETE_EXTRA_FILES_ON_RESTORE"), false, "DELETE_EXTRA_FILES_ON_RESTORE"),
                env.getOrDefault("LOG_LEVEL", "INFO"),
                parseBoolean(env.get("BACKUP_TIMESTAMPED"), false, "BACKUP_TIMESTAMPED"),
                blankToNull(env.get("PCLOUD_API_HOST"))
        );
    }

    String effectiveRemoteFolder(Clock clock) {
        if (mode != Mode.BACKUP || !backupTimestamped) {
            return remoteFolder;
        }
        return RemotePathService.join(remoteFolder, TIMESTAMP_FORMAT.format(clock.instant()));
    }

    static void validateLocalBackupFolder(Path path, Mode mode) {
        if (path == null || path.toString().isBlank()) {
            throw new IllegalArgumentException("LOCAL_BACKUP_FOLDER must not be empty.");
        }
        Path normalized = path.toAbsolutePath().normalize();
        String asString = normalized.toString();
        if (normalized.getParent() == null || DANGEROUS_PATHS.contains(asString)) {
            throw new IllegalArgumentException("LOCAL_BACKUP_FOLDER points to an unsafe path: " + normalized);
        }
        if (mode == Mode.RESTORE && normalized.getNameCount() < 2) {
            throw new IllegalArgumentException("Refusing restore into a shallow filesystem path: " + normalized);
        }
    }

    private static String required(Map<String, String> env, String key) {
        String value = env.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(key + " is required.");
        }
        return value.trim();
    }

    private static boolean parseBoolean(String value, boolean defaultValue, String key) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return switch (value.trim().toLowerCase()) {
            case "true" -> true;
            case "false" -> false;
            default -> throw new IllegalArgumentException(key + " must be 'true' or 'false'.");
        };
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
