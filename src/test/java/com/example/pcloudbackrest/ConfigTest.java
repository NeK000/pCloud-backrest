package com.example.pcloudbackrest;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
