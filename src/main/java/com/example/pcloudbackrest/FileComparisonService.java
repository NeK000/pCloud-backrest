package com.example.pcloudbackrest;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

final class FileComparisonService {
    private static final long TIMESTAMP_TOLERANCE_MILLIS = 2_000L;

    boolean shouldTransfer(Path localFile, RemoteFileEntry remoteFile, Optional<String> remoteSha1) throws IOException {
        if (!Files.exists(localFile)) {
            return true;
        }
        if (Files.size(localFile) != remoteFile.size()) {
            return true;
        }

        Instant localModified = Files.getLastModifiedTime(localFile).toInstant();
        if (timestampsClose(localModified, remoteFile.lastModified())) {
            return false;
        }

        Optional<String> comparableHash = remoteSha1.or(remoteFile::hashValue);
        if (comparableHash.isPresent()) {
            String localSha1 = sha1(localFile);
            return !localSha1.equalsIgnoreCase(comparableHash.get());
        }
        return true;
    }

    String sha1(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] buffer = new byte[1024 * 1024];
            try (InputStream input = Files.newInputStream(file)) {
                int read;
                while ((read = input.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 digest is not available in this Java runtime.", e);
        }
    }

    private boolean timestampsClose(Instant localModified, Instant remoteModified) {
        if (remoteModified == null) {
            return false;
        }
        return Math.abs(localModified.toEpochMilli() - remoteModified.toEpochMilli()) <= TIMESTAMP_TOLERANCE_MILLIS;
    }
}
