package io.github.ninik.pcloudbackrest.service;

import io.github.ninik.pcloudbackrest.config.Config;
import io.github.ninik.pcloudbackrest.gateway.PCloudGateway;
import io.github.ninik.pcloudbackrest.model.RemoteFileEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackupServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void uploadsMissingRemoteFileUsingGateway() throws Exception {
        Path localFile = tempDir.resolve("album").resolve("image.jpg");
        Files.createDirectories(localFile.getParent());
        Files.writeString(localFile, "image-data");

        Config config = backupConfig(Map.of());
        FakePCloudGateway gateway = new FakePCloudGateway();

        new BackupService(gateway, new FileComparisonService()).run(config, "/Backups/Immich");

        assertEquals(localFile, gateway.uploadedLocalFile);
        assertEquals("/Backups/Immich/album", gateway.uploadedRemoteFolder);
        assertEquals("image.jpg", gateway.uploadedFilename);
    }

    @Test
    void skipsUnchangedRemoteFile() throws Exception {
        Path localFile = tempDir.resolve("same.txt");
        Files.writeString(localFile, "same");
        Instant modified = Files.getLastModifiedTime(localFile).toInstant();

        Config config = backupConfig(Map.of());
        FakePCloudGateway gateway = new FakePCloudGateway();
        gateway.remoteFile = Optional.of(new RemoteFileEntry("/Backups/Immich/same.txt", 4, modified, null, null));

        var summary = new BackupService(gateway, new FileComparisonService()).run(config, "/Backups/Immich");

        assertEquals(0, gateway.uploadCalls);
        assertFalse(summary.hasFailures());
    }

    @Test
    void dryRunDoesNotCreateFoldersOrUpload() throws Exception {
        Path localFile = tempDir.resolve("dry-run.txt");
        Files.writeString(localFile, "content");

        Config config = backupConfig(Map.of("DRY_RUN", "true"));
        FakePCloudGateway gateway = new FakePCloudGateway();

        new BackupService(gateway, new FileComparisonService()).run(config, "/Backups/Immich");

        assertEquals(0, gateway.ensureFolderCalls);
        assertEquals(0, gateway.uploadCalls);
    }

    @Test
    void marksSummaryFailedWhenUploadFailsAndContinues() throws Exception {
        Files.writeString(tempDir.resolve("first.txt"), "first");
        Files.writeString(tempDir.resolve("second.txt"), "second");

        Config config = backupConfig(Map.of());
        FakePCloudGateway gateway = new FakePCloudGateway();
        gateway.failUploads = true;

        var summary = new BackupService(gateway, new FileComparisonService()).run(config, "/Backups/Immich");

        assertEquals(2, gateway.uploadCalls);
        assertTrue(summary.hasFailures());
    }

    private Config backupConfig(Map<String, String> overrides) {
        Map<String, String> env = new HashMap<>();
        env.put("PCLOUD_ACCESS_TOKEN", "token");
        env.put("PCLOUD_REMOTE_FOLDER", "/Backups/Immich");
        env.put("LOCAL_BACKUP_FOLDER", tempDir.toString());
        env.put("MODE", "backup");
        env.putAll(overrides);
        return Config.fromEnv(env);
    }

    private static final class FakePCloudGateway implements PCloudGateway {
        private Path uploadedLocalFile;
        private String uploadedRemoteFolder;
        private String uploadedFilename;
        private Optional<RemoteFileEntry> remoteFile = Optional.empty();
        private boolean failUploads;
        private int ensureFolderCalls;
        private int uploadCalls;

        @Override
        public int ensureFolder(String remoteFolder) {
            ensureFolderCalls++;
            return 0;
        }

        @Override
        public boolean folderExists(String remoteFolder) {
            return true;
        }

        @Override
        public java.util.List<RemoteFileEntry> listFilesRecursively(String remoteFolder) {
            return java.util.List.of();
        }

        @Override
        public Optional<RemoteFileEntry> findFile(String remoteFilePath) {
            return remoteFile;
        }

        @Override
        public Optional<String> sha1(RemoteFileEntry remoteFile) {
            return Optional.empty();
        }

        @Override
        public void upload(Path localFile, String remoteFolder, String filename, Instant modifiedTime) throws Exception {
            uploadCalls++;
            if (failUploads) {
                throw new Exception("upload failed");
            }
            this.uploadedLocalFile = localFile;
            this.uploadedRemoteFolder = remoteFolder;
            this.uploadedFilename = filename;
        }

        @Override
        public void download(RemoteFileEntry remoteFile, Path localFile) {
        }

        @Override
        public void close() {
        }
    }
}
