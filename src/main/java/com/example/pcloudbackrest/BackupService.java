package com.example.pcloudbackrest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;

final class BackupService {
    private static final Logger log = LoggerFactory.getLogger(BackupService.class);

    private final PCloudGateway pCloud;
    private final FileComparisonService comparisonService;

    BackupService(PCloudGateway pCloud, FileComparisonService comparisonService) {
        this.pCloud = pCloud;
        this.comparisonService = comparisonService;
    }

    ResultSummary run(Config config, String remoteFolder) throws Exception {
        if (!Files.isDirectory(config.localBackupFolder())) {
            throw new IllegalArgumentException("LOCAL_BACKUP_FOLDER must exist and be a directory for backup: " + config.localBackupFolder());
        }

        ResultSummary summary = new ResultSummary();
        if (config.dryRun()) {
            log.info("DRY_RUN=true; no remote folders or files will be changed.");
        } else {
            summary.foldersCreated(pCloud.ensureFolder(remoteFolder));
        }

        try (Stream<Path> paths = Files.walk(config.localBackupFolder())) {
            for (Path localFile : paths.filter(Files::isRegularFile).sorted(Comparator.naturalOrder()).toList()) {
                summary.scannedFile();
                backupOne(config, remoteFolder, localFile, summary);
            }
        }
        return summary;
    }

    private void backupOne(Config config, String remoteFolder, Path localFile, ResultSummary summary) {
        String relativePath = RemotePathService.relativeRemotePath(config.localBackupFolder(), localFile);
        String remoteFilePath = RemotePathService.join(remoteFolder, relativePath);
        String remoteParent = RemotePathService.parent(remoteFilePath);
        String filename = RemotePathService.filename(remoteFilePath);

        try {
            Optional<RemoteFileEntry> remoteFile = pCloud.findFile(remoteFilePath);
            if (remoteFile.isPresent()) {
                Optional<String> remoteSha1 = pCloud.sha1(remoteFile.get());
                if (!comparisonService.shouldTransfer(localFile, remoteFile.get(), remoteSha1)) {
                    log.info("Skipped unchanged file: {} -> {}", localFile, remoteFilePath);
                    summary.skipped();
                    return;
                }
            }

            long size = Files.size(localFile);
            Instant modified = Files.getLastModifiedTime(localFile).toInstant();
            if (config.dryRun()) {
                log.info("DRY RUN upload: {} -> {} ({} bytes)", localFile, remoteFilePath, size);
            } else {
                summary.foldersCreated(pCloud.ensureFolder(remoteParent));
                pCloud.upload(localFile, remoteParent, filename, modified);
                log.info("Uploaded file: {} -> {} ({} bytes)", localFile, remoteFilePath, size);
                summary.uploaded(size);
            }
        } catch (Exception e) {
            summary.failed();
            log.error("Failed to backup file: {} -> {}", localFile, remoteFilePath, e);
        }
    }
}
