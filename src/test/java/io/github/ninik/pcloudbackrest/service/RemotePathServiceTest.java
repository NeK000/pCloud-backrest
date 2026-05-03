package io.github.ninik.pcloudbackrest.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RemotePathServiceTest {
    @Test
    void normalizesRemotePaths() {
        assertEquals("/Backups/Immich", RemotePathService.normalizeRemoteFolder("Backups//Immich/"));
        assertEquals("/Backups/Immich/file.jpg", RemotePathService.join("/Backups/Immich", "file.jpg"));
        assertEquals("/Backups/Immich/file.jpg", RemotePathService.join("/Backups/Immich", "/file.jpg"));
        assertEquals("/Backups/Immich", RemotePathService.join("/Backups/Immich", "   "));
        assertEquals("/file.jpg", RemotePathService.join("/", "/file.jpg"));
        assertEquals("/Backups/Immich", RemotePathService.parent("/Backups/Immich/file.jpg"));
        assertEquals("/", RemotePathService.parent("/file.jpg"));
        assertEquals("file.jpg", RemotePathService.filename("/Backups/Immich/file.jpg"));
    }

    @Test
    void rejectsInvalidRemotePaths() {
        assertThrows(IllegalArgumentException.class, () -> RemotePathService.normalize(null));
        assertThrows(IllegalArgumentException.class, () -> RemotePathService.normalize("   "));
        assertThrows(IllegalArgumentException.class, () -> RemotePathService.normalize("/Backups/../Secrets"));
        assertThrows(IllegalArgumentException.class, () -> RemotePathService.normalizeRemoteFolder("/"));
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
                () -> RemotePathService.safeResolve(Path.of("/data/photos"), null));
        assertThrows(IllegalArgumentException.class,
                () -> RemotePathService.safeResolve(Path.of("/data/photos"), "   "));
        assertThrows(IllegalArgumentException.class,
                () -> RemotePathService.safeResolve(Path.of("/data/photos"), "/absolute.txt"));
        assertThrows(IllegalArgumentException.class,
                () -> RemotePathService.safeResolve(Path.of("/data/photos"), "\\absolute.txt"));
        assertThrows(IllegalArgumentException.class,
                () -> RemotePathService.safeResolve(Path.of("/data/photos"), "../escape.txt"));
    }

    @Test
    void safeResolveAcceptsRelativePathInsideRoot() {
        assertEquals(
                Path.of("/data/photos/album/image.jpg"),
                RemotePathService.safeResolve(Path.of("/data/photos"), "album/image.jpg")
        );
    }
}
