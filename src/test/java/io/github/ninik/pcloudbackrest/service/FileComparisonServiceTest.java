package io.github.ninik.pcloudbackrest.service;

import io.github.ninik.pcloudbackrest.model.RemoteFileEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileComparisonServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void skipsWhenSizeAndTimestampMatch() throws Exception {
        Path file = tempDir.resolve("same.txt");
        Files.writeString(file, "same");
        Instant modified = Files.getLastModifiedTime(file).toInstant();
        RemoteFileEntry remote = new RemoteFileEntry("/remote/same.txt", 4, modified, null, null);

        assertFalse(new FileComparisonService().shouldTransfer(file, remote, Optional.empty()));
    }

    @Test
    void transfersWhenSizeDiffers() throws Exception {
        Path file = tempDir.resolve("size.txt");
        Files.writeString(file, "local");
        RemoteFileEntry remote = new RemoteFileEntry("/remote/size.txt", 99, Instant.EPOCH, null, null);

        assertTrue(new FileComparisonService().shouldTransfer(file, remote, Optional.empty()));
    }

    @Test
    void skipsWhenSha1MatchesDespiteTimestampDifference() throws Exception {
        Path file = tempDir.resolve("hash.txt");
        Files.writeString(file, "hash-me");
        FileComparisonService service = new FileComparisonService();
        RemoteFileEntry remote = new RemoteFileEntry("/remote/hash.txt", Files.size(file), Instant.EPOCH, null, null);

        assertFalse(service.shouldTransfer(file, remote, Optional.of(service.sha1(file))));
    }
}
