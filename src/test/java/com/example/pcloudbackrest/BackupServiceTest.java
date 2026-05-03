package com.example.pcloudbackrest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BackupServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void uploadsMissingRemoteFileUsingGateway() throws Exception {
        Path localFile = tempDir.resolve("album").resolve("image.jpg");
        Files.createDirectories(localFile.getParent());
        Files.writeString(localFile, "image-data");

        Config config = Config.fromEnv(Map.of(
                "PCLOUD_ACCESS_TOKEN", "token",
                "PCLOUD_REMOTE_FOLDER", "/Backups/Immich",
                "LOCAL_BACKUP_FOLDER", tempDir.toString(),
                "MODE", "backup"
        ));
        FakePCloudGateway gateway = new FakePCloudGateway();

        new BackupService(gateway, new FileComparisonService()).run(config, "/Backups/Immich");

        assertEquals(localFile, gateway.uploadedLocalFile);
        assertEquals("/Backups/Immich/album", gateway.uploadedRemoteFolder);
        assertEquals("image.jpg", gateway.uploadedFilename);
    }

    private static final class FakePCloudGateway implements PCloudGateway {
        private Path uploadedLocalFile;
        private String uploadedRemoteFolder;
        private String uploadedFilename;

        @Override
        public int ensureFolder(String remoteFolder) {
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
            return Optional.empty();
        }

        @Override
        public Optional<String> sha1(RemoteFileEntry remoteFile) {
            return Optional.empty();
        }

        @Override
        public void upload(Path localFile, String remoteFolder, String filename, Instant modifiedTime) {
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
