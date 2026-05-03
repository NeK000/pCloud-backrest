package io.github.ninik.pcloudbackrest.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DangerousPathValidationTest {
    @Test
    void rejectsRootAndSystemFolders() {
        assertThrows(IllegalArgumentException.class, () -> Config.validateLocalBackupFolder(Path.of("/"), Mode.BACKUP));
        assertThrows(IllegalArgumentException.class, () -> Config.validateLocalBackupFolder(Path.of("/etc"), Mode.RESTORE));
        assertThrows(IllegalArgumentException.class, () -> Config.validateLocalBackupFolder(Path.of("/tmp"), Mode.RESTORE));
    }

    @Test
    void acceptsSpecificNestedFolder() {
        assertDoesNotThrow(() -> Config.validateLocalBackupFolder(Path.of("/data/photos"), Mode.RESTORE));
    }
}
