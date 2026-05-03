package io.github.ninik.pcloudbackrest.model;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResultSummaryTest {
    @Test
    void tracksFailureStateAndCanLogSummary() {
        ResultSummary summary = new ResultSummary();
        Logger log = LoggerFactory.getLogger(ResultSummaryTest.class);

        summary.scannedFile();
        summary.uploaded(10);
        summary.downloaded(20);
        summary.skipped();
        summary.folderCreated();
        summary.foldersCreated(2);

        assertFalse(summary.hasFailures());

        summary.failed();

        assertTrue(summary.hasFailures());
        summary.log(log);
    }
}
