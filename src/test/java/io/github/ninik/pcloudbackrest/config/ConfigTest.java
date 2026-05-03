package io.github.ninik.pcloudbackrest.config;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConfigTest {
    @Test
    void parsesRequiredAndOptionalEnv() {
        Map<String, String> env = baseEnv();
        env.put("DRY_RUN", "true");
        env.put("DELETE_EXTRA_FILES_ON_RESTORE", "true");
        env.put("BACKUP_TIMESTAMPED", "true");
        env.put("LOG_LEVEL", "DEBUG");
        env.put("PCLOUD_API_HOST", "eapi.pcloud.com");

        Config config = Config.fromEnv(env);

        assertEquals("token-value", config.accessToken());
        assertEquals("/Backups/Immich", config.remoteFolder());
        assertEquals(Mode.BACKUP, config.mode());
        assertTrue(config.dryRun());
        assertTrue(config.deleteExtraFilesOnRestore());
        assertTrue(config.backupTimestamped());
        assertEquals("DEBUG", config.logLevel());
        assertEquals("eapi.pcloud.com", config.apiHost());
    }

    @Test
    void rejectsMissingRequiredEnv() {
        Map<String, String> env = baseEnv();
        env.remove("PCLOUD_ACCESS_TOKEN");

        assertThrows(IllegalArgumentException.class, () -> Config.fromEnv(env));
    }

    @Test
    void optionalBooleansDefaultToFalse() {
        Config config = Config.fromEnv(baseEnv());

        assertFalse(config.dryRun());
        assertFalse(config.deleteExtraFilesOnRestore());
        assertFalse(config.backupTimestamped());
        assertTrue(config.schedule().isEmpty());
    }

    @Test
    void parsesScheduleEnv() {
        Map<String, String> env = baseEnv();
        env.put("SCHEDULE_CRON", "0 3 * * SUN");
        env.put("SCHEDULE_TIMEZONE", "UTC");
        env.put("SCHEDULE_RUN_ON_START", "true");

        Config config = Config.fromEnv(env);

        assertTrue(config.schedule().isPresent());
        assertTrue(config.scheduleRunOnStart());
        assertEquals("UTC", config.schedule().get().zoneId().getId());
    }

    @Test
    void appendsTimestampOnlyForTimestampedBackups() {
        Map<String, String> env = baseEnv();
        env.put("BACKUP_TIMESTAMPED", "true");

        Config config = Config.fromEnv(env);

        assertEquals(
                "/Backups/Immich/20260503-101530",
                config.effectiveRemoteFolder(Clock.fixed(Instant.parse("2026-05-03T10:15:30Z"), ZoneOffset.UTC))
        );
    }

    @Test
    void rejectsInvalidModeAndBooleanAndRemoteRoot() {
        Map<String, String> invalidMode = baseEnv();
        invalidMode.put("MODE", "sync");
        assertThrows(IllegalArgumentException.class, () -> Config.fromEnv(invalidMode));

        Map<String, String> invalidBoolean = baseEnv();
        invalidBoolean.put("DRY_RUN", "yes");
        assertThrows(IllegalArgumentException.class, () -> Config.fromEnv(invalidBoolean));

        Map<String, String> remoteRoot = baseEnv();
        remoteRoot.put("PCLOUD_REMOTE_FOLDER", "/");
        assertThrows(IllegalArgumentException.class, () -> Config.fromEnv(remoteRoot));
    }

    @Test
    void rejectsInvalidScheduleTimezone() {
        Map<String, String> env = baseEnv();
        env.put("SCHEDULE_CRON", "0 3 * * *");
        env.put("SCHEDULE_TIMEZONE", "No/SuchZone");

        assertThrows(IllegalArgumentException.class, () -> Config.fromEnv(env));
    }

    private Map<String, String> baseEnv() {
        Map<String, String> env = new HashMap<>();
        env.put("PCLOUD_ACCESS_TOKEN", "token-value");
        env.put("PCLOUD_REMOTE_FOLDER", "/Backups/Immich/");
        env.put("LOCAL_BACKUP_FOLDER", "/data/photos");
        env.put("MODE", "backup");
        return env;
    }
}
