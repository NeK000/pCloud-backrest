package com.example.pcloudbackrest;

import org.slf4j.Logger;

final class ResultSummary {
    private long filesScanned;
    private long filesUploaded;
    private long filesDownloaded;
    private long filesSkipped;
    private long foldersCreated;
    private long failures;
    private long bytesTransferred;

    void scannedFile() {
        filesScanned++;
    }

    void uploaded(long bytes) {
        filesUploaded++;
        bytesTransferred += bytes;
    }

    void downloaded(long bytes) {
        filesDownloaded++;
        bytesTransferred += bytes;
    }

    void skipped() {
        filesSkipped++;
    }

    void folderCreated() {
        foldersCreated++;
    }

    void foldersCreated(long count) {
        foldersCreated += count;
    }

    void failed() {
        failures++;
    }

    boolean hasFailures() {
        return failures > 0;
    }

    void log(Logger log) {
        log.info("Summary: files scanned={}, uploaded={}, downloaded={}, skipped={}, folders created={}, failures={}, bytes transferred={}",
                filesScanned, filesUploaded, filesDownloaded, filesSkipped, foldersCreated, failures, bytesTransferred);
    }
}
