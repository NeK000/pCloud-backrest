package io.github.ninik.pcloudbackrest.service;

import io.github.ninik.pcloudbackrest.config.Config;
import io.github.ninik.pcloudbackrest.gateway.PCloudGateway;
import io.github.ninik.pcloudbackrest.model.RemoteFileEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RestoreServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void downloadsMissingLocalFileAndPreservesTimestamp() throws Exception {
        Instant modified = Instant.parse("2026-05-03T10:15:30Z");
        FakePCloudGateway gateway = new FakePCloudGateway();
        gateway.remoteFiles.add(new RemoteFileEntry("/Backups/Immich/album/image.jpg", 10, modified, null, null));

        var summary = new RestoreService(gateway, new FileComparisonService()).run(restoreConfig(Map.of()), "/Backups/Immich");

        Path restored = tempDir.resolve("album/image.jpg");
        assertTrue(Files.exists(restored));
        assertEquals("downloaded", Files.readString(restored));
        assertEquals(modified, Files.getLastModifiedTime(restored).toInstant());
        assertEquals(1, gateway.downloadCalls);
        assertFalse(summary.hasFailures());
    }

    @Test
    void skipsUnchangedLocalFile() throws Exception {
        Path localFile = tempDir.resolve("same.txt");
        Files.writeString(localFile, "same");
        Instant modified = Files.getLastModifiedTime(localFile).toInstant();

        FakePCloudGateway gateway = new FakePCloudGateway();
        gateway.remoteFiles.add(new RemoteFileEntry("/Backups/Immich/same.txt", 4, modified, null, null));

        new RestoreService(gateway, new FileComparisonService()).run(restoreConfig(Map.of()), "/Backups/Immich");

        assertEquals(0, gateway.downloadCalls);
    }

    @Test
    void deletesExtraLocalFilesOnlyWhenEnabled() throws Exception {
        Path extra = tempDir.resolve("extra.txt");
        Files.writeString(extra, "extra");

        FakePCloudGateway gateway = new FakePCloudGateway();
        gateway.remoteFiles.add(new RemoteFileEntry("/Backups/Immich/remote.txt", 6, Instant.EPOCH, null, null));

        new RestoreService(gateway, new FileComparisonService())
                .run(restoreConfig(Map.of("DELETE_EXTRA_FILES_ON_RESTORE", "true")), "/Backups/Immich");

        assertFalse(Files.exists(extra));
        assertTrue(Files.exists(tempDir.resolve("remote.txt")));
    }

    @Test
    void preservesExtraLocalFilesByDefault() throws Exception {
        Path extra = tempDir.resolve("extra.txt");
        Files.writeString(extra, "extra");

        FakePCloudGateway gateway = new FakePCloudGateway();

        new RestoreService(gateway, new FileComparisonService()).run(restoreConfig(Map.of()), "/Backups/Immich");

        assertTrue(Files.exists(extra));
    }

    @Test
    void dryRunDoesNotDownloadOrDelete() throws Exception {
        Path extra = tempDir.resolve("extra.txt");
        Files.writeString(extra, "extra");

        FakePCloudGateway gateway = new FakePCloudGateway();
        gateway.remoteFiles.add(new RemoteFileEntry("/Backups/Immich/new.txt", 7, Instant.EPOCH, null, null));

        new RestoreService(gateway, new FileComparisonService()).run(restoreConfig(Map.of(
                "DRY_RUN", "true",
                "DELETE_EXTRA_FILES_ON_RESTORE", "true"
        )), "/Backups/Immich");

        assertEquals(0, gateway.downloadCalls);
        assertFalse(Files.exists(tempDir.resolve("new.txt")));
        assertTrue(Files.exists(extra));
    }

    @Test
    void rejectsMissingRemoteFolder() throws Exception {
        FakePCloudGateway gateway = new FakePCloudGateway();
        gateway.folderExists = false;

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new RestoreService(gateway, new FileComparisonService()).run(restoreConfig(Map.of()), "/Backups/Immich"));

        assertEquals("PCLOUD_REMOTE_FOLDER does not exist: /Backups/Immich", error.getMessage());
    }

    private Config restoreConfig(Map<String, String> overrides) {
        Map<String, String> env = new HashMap<>();
        env.put("PCLOUD_ACCESS_TOKEN", "token");
        env.put("PCLOUD_REMOTE_FOLDER", "/Backups/Immich");
        env.put("LOCAL_BACKUP_FOLDER", tempDir.toString());
        env.put("MODE", "restore");
        env.putAll(overrides);
        return Config.fromEnv(env);
    }

    private static final class FakePCloudGateway implements PCloudGateway {
        private final List<RemoteFileEntry> remoteFiles = new ArrayList<>();
        private boolean folderExists = true;
        private int downloadCalls;

        @Override
        public int ensureFolder(String remoteFolder) {
            return 0;
        }

        @Override
        public boolean folderExists(String remoteFolder) {
            return folderExists;
        }

        @Override
        public List<RemoteFileEntry> listFilesRecursively(String remoteFolder) {
            return remoteFiles;
        }

        @Override
        public Optional<RemoteFileEntry> findFile(String remoteFilePath) {
            return Optional.empty();
        }

        @Override
        public Optional<String> sha1(RemoteFileEntry remoteFile) {
            return Optional.empty();
        }

        @Override
        public void upload(Path localFile, String remoteFolder, String filename, Instant modifiedTime) {
        }

        @Override
        public void download(RemoteFileEntry remoteFile, Path localFile) throws Exception {
            downloadCalls++;
            Files.createDirectories(localFile.getParent());
            Files.writeString(localFile, "downloaded");
        }

        @Override
        public void close() {
        }
    }
}
