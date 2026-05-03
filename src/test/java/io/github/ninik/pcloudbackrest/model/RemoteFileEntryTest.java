package io.github.ninik.pcloudbackrest.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RemoteFileEntryTest {
    @Test
    void relativeToReturnsEmptyWhenPathEqualsRemoteRoot() {
        RemoteFileEntry entry = new RemoteFileEntry("/Backups/Immich", 0, Instant.EPOCH, null, null);

        assertEquals("", entry.relativeTo("/Backups/Immich"));
    }

    @Test
    void relativeToHandlesRootRemoteFolder() {
        RemoteFileEntry entry = new RemoteFileEntry("/Backups/Immich/file.jpg", 0, Instant.EPOCH, null, null);

        assertEquals("Backups/Immich/file.jpg", entry.relativeTo("/"));
    }

    @Test
    void relativeToRejectsPathOutsideRemoteRoot() {
        RemoteFileEntry entry = new RemoteFileEntry("/Other/file.jpg", 0, Instant.EPOCH, null, null);

        assertThrows(IllegalArgumentException.class, () -> entry.relativeTo("/Backups"));
    }

    @Test
    void hashValueReturnsPresentOnlyForNonBlankHash() {
        assertEquals("abc", new RemoteFileEntry("/file", 0, Instant.EPOCH, "abc", null).hashValue().orElseThrow());
        assertTrue(new RemoteFileEntry("/file", 0, Instant.EPOCH, "", null).hashValue().isEmpty());
        assertTrue(new RemoteFileEntry("/file", 0, Instant.EPOCH, "   ", null).hashValue().isEmpty());
        assertTrue(new RemoteFileEntry("/file", 0, Instant.EPOCH, null, null).hashValue().isEmpty());
    }
}
