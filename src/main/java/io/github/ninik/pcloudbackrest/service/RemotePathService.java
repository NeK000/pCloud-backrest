package io.github.ninik.pcloudbackrest.service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class RemotePathService {
    private RemotePathService() {
    }

    public static String normalizeRemoteFolder(String path) {
        String normalized = normalize(path);
        if ("/".equals(normalized)) {
            throw new IllegalArgumentException("PCLOUD_REMOTE_FOLDER must not be '/'.");
        }
        return normalized;
    }

    public static String normalize(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("Remote path must not be empty.");
        }
        String candidate = path.trim().replace('\\', '/');
        if (!candidate.startsWith("/")) {
            candidate = "/" + candidate;
        }
        String[] parts = candidate.split("/");
        List<String> clean = new ArrayList<>();
        for (String part : parts) {
            if (part.isBlank() || ".".equals(part)) {
                continue;
            }
            if ("..".equals(part)) {
                throw new IllegalArgumentException("Remote path must not contain '..': " + path);
            }
            clean.add(part);
        }
        return clean.isEmpty() ? "/" : "/" + String.join("/", clean);
    }

    public static String join(String folder, String child) {
        String cleanFolder = normalize(folder);
        String cleanChild = child == null ? "" : child.trim().replace('\\', '/');
        if (cleanChild.isBlank()) {
            return cleanFolder;
        }
        if (cleanChild.startsWith("/")) {
            cleanChild = cleanChild.substring(1);
        }
        return normalize(("/".equals(cleanFolder) ? "" : cleanFolder) + "/" + cleanChild);
    }

    public static String parent(String remoteFilePath) {
        String normalized = normalize(remoteFilePath);
        int index = normalized.lastIndexOf('/');
        return index <= 0 ? "/" : normalized.substring(0, index);
    }

    public static String filename(String remoteFilePath) {
        String normalized = normalize(remoteFilePath);
        int index = normalized.lastIndexOf('/');
        return index < 0 ? normalized : normalized.substring(index + 1);
    }

    public static String relativeRemotePath(Path root, Path file) {
        Path relative = root.toAbsolutePath().normalize().relativize(file.toAbsolutePath().normalize());
        return relative.toString().replace('\\', '/');
    }

    public static Path safeResolve(Path root, String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("Relative path must not be empty.");
        }
        if (relativePath.startsWith("/") || relativePath.startsWith("\\")) {
            throw new IllegalArgumentException("Relative path must not be absolute: " + relativePath);
        }
        Path resolved = root.resolve(relativePath).normalize();
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path absoluteResolved = resolved.toAbsolutePath().normalize();
        if (!absoluteResolved.startsWith(normalizedRoot)) {
            throw new IllegalArgumentException("Resolved path escapes local folder: " + relativePath);
        }
        return absoluteResolved;
    }
}
