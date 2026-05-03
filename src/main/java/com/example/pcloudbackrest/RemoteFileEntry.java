package com.example.pcloudbackrest;

import java.time.Instant;
import java.util.Optional;

record RemoteFileEntry(
        String path,
        long size,
        Instant lastModified,
        String hash,
        Object sdkFile
) {
    String relativeTo(String remoteRoot) {
        String normalizedRoot = RemotePathService.normalize(remoteRoot);
        String normalizedPath = RemotePathService.normalize(path);
        if (normalizedPath.equals(normalizedRoot)) {
            return "";
        }
        String prefix = "/".equals(normalizedRoot) ? "/" : normalizedRoot + "/";
        if (!normalizedPath.startsWith(prefix)) {
            throw new IllegalArgumentException(normalizedPath + " is not under " + normalizedRoot);
        }
        return normalizedPath.substring(prefix.length());
    }

    Optional<String> hashValue() {
        return Optional.ofNullable(hash).filter(value -> !value.isBlank());
    }
}
