package com.example.pcloudbackrest;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RemotePathServiceTest {
    @Test
    void normalizesRemotePaths() {
        assertEquals("/Backups/Immich", RemotePathService.normalizeRemoteFolder("Backups//Immich/"));
        assertEquals("/Backups/Immich/file.jpg", RemotePathService.join("/Backups/Immich", "file.jpg"));
        assertEquals("/Backups/Immich", RemotePathService.parent("/Backups/Immich/file.jpg"));
        assertEquals("file.jpg", RemotePathService.filename("/Backups/Immich/file.jpg"));
    }

    @Test
    void mapsLocalPathToRemoteRelativePath() {
        Path root = Path.of("/data/photos");
        Path file = Path.of("/data/photos/2026/05/image.jpg");

        assertEquals("2026/05/image.jpg", RemotePathService.relativeRemotePath(root, file));
    }

    @Test
    void safeResolveRejectsTraversal() {
        assertThrows(IllegalArgumentException.class,
                () -> RemotePathService.safeResolve(Path.of("/data/photos"), "../escape.txt"));
    }
}
