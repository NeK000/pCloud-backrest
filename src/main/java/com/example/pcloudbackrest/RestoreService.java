package com.example.pcloudbackrest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

final class RestoreService {
    private static final Logger log = LoggerFactory.getLogger(RestoreService.class);

    private final PCloudGateway pCloud;
    private final FileComparisonService comparisonService;

    RestoreService(PCloudGateway pCloud, FileComparisonService comparisonService) {
        this.pCloud = pCloud;
        this.comparisonService = comparisonService;
    }

    ResultSummary run(Config config, String remoteFolder) throws Exception {
        ResultSummary summary = new ResultSummary();
        if (!pCloud.folderExists(remoteFolder)) {
            throw new IllegalArgumentException("PCLOUD_REMOTE_FOLDER does not exist: " + remoteFolder);
        }
        if (config.dryRun()) {
            log.info("DRY_RUN=true; no local files will be changed.");
        } else {
            createDirectories(config.localBackupFolder(), summary);
        }

        List<RemoteFileEntry> remoteFiles = pCloud.listFilesRecursively(remoteFolder);
        Set<String> remoteRelativePaths = new HashSet<>();

        for (RemoteFileEntry remoteFile : remoteFiles) {
            summary.scannedFile();
            String relativePath = remoteFile.relativeTo(remoteFolder);
            remoteRelativePaths.add(relativePath);
            restoreOne(config, relativePath, remoteFile, summary);
        }

        if (config.deleteExtraFilesOnRestore()) {
            deleteExtraLocalFiles(config, remoteRelativePaths, summary);
        } else {
            log.info("DELETE_EXTRA_FILES_ON_RESTORE is not true; local-only files will be preserved.");
        }
        return summary;
    }

    private void restoreOne(Config config, String relativePath, RemoteFileEntry remoteFile, ResultSummary summary) {
        try {
            Path localFile = RemotePathService.safeResolve(config.localBackupFolder(), relativePath);
            Optional<String> remoteSha1 = pCloud.sha1(remoteFile);
            if (!comparisonService.shouldTransfer(localFile, remoteFile, remoteSha1)) {
                log.info("Skipped unchanged file: {} -> {}", remoteFile.path(), localFile);
                summary.skipped();
                return;
            }

            if (config.dryRun()) {
                log.info("DRY RUN download: {} -> {} ({} bytes)", remoteFile.path(), localFile, remoteFile.size());
            } else {
                Path parent = localFile.getParent();
                if (parent != null) {
                    createDirectories(parent, summary);
                }
                pCloud.download(remoteFile, localFile);
                if (remoteFile.lastModified() != null) {
                    Files.setLastModifiedTime(localFile, FileTime.from(remoteFile.lastModified()));
                }
                log.info("Downloaded file: {} -> {} ({} bytes)", remoteFile.path(), localFile, remoteFile.size());
                summary.downloaded(remoteFile.size());
            }
        } catch (Exception e) {
            summary.failed();
            log.error("Failed to restore file: {}", remoteFile.path(), e);
        }
    }

    private void createDirectories(Path directory, ResultSummary summary) throws IOException {
        if (Files.isDirectory(directory)) {
            return;
        }
        Files.createDirectories(directory);
        summary.folderCreated();
    }

    private void deleteExtraLocalFiles(Config config, Set<String> remoteRelativePaths, ResultSummary summary) throws IOException {
        if (!Files.exists(config.localBackupFolder())) {
            return;
        }
        try (Stream<Path> paths = Files.walk(config.localBackupFolder())) {
            List<Path> localPaths = paths.sorted(Comparator.reverseOrder()).toList();
            for (Path path : localPaths) {
                if (path.equals(config.localBackupFolder())) {
                    continue;
                }
                if (Files.isRegularFile(path)) {
                    String relative = RemotePathService.relativeRemotePath(config.localBackupFolder(), path);
                    if (!remoteRelativePaths.contains(relative)) {
                        if (config.dryRun()) {
                            log.info("DRY RUN delete extra local file: {}", path);
                        } else {
                            try {
                                Files.deleteIfExists(path);
                                log.info("Deleted extra local file: {}", path);
                            } catch (IOException e) {
                                summary.failed();
                                log.error("Failed to delete extra local file: {}", path, e);
                            }
                        }
                    }
                } else if (Files.isDirectory(path) && !config.dryRun()) {
                    try (Stream<Path> children = Files.list(path)) {
                        if (children.findAny().isEmpty()) {
                            Files.deleteIfExists(path);
                            log.info("Deleted empty local folder: {}", path);
                        }
                    } catch (IOException e) {
                        summary.failed();
                        log.error("Failed to delete empty local folder: {}", path, e);
                    }
                }
            }
        }
    }
}
