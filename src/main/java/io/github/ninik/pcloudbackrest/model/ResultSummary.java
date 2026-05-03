package io.github.ninik.pcloudbackrest.model;

import org.slf4j.Logger;

public final class ResultSummary {
    private long filesScanned;
    private long filesUploaded;
    private long filesDownloaded;
    private long filesSkipped;
    private long foldersCreated;
    private long failures;
    private long bytesTransferred;

    public void scannedFile() {
        filesScanned++;
    }

    public void uploaded(long bytes) {
        filesUploaded++;
        bytesTransferred += bytes;
    }

    public void downloaded(long bytes) {
        filesDownloaded++;
        bytesTransferred += bytes;
    }

    public void skipped() {
        filesSkipped++;
    }

    public void folderCreated() {
        foldersCreated++;
    }

    public void foldersCreated(long count) {
        foldersCreated += count;
    }

    public void failed() {
        failures++;
    }

    public boolean hasFailures() {
        return failures > 0;
    }

    public void log(Logger log) {
        log.info("Summary: files scanned={}, uploaded={}, downloaded={}, skipped={}, folders created={}, failures={}, bytes transferred={}",
                filesScanned, filesUploaded, filesDownloaded, filesSkipped, foldersCreated, failures, bytesTransferred);
    }
}
