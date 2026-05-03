package io.github.ninik.pcloudbackrest.gateway;

import com.pcloud.sdk.ApiClient;
import com.pcloud.sdk.ApiError;
import com.pcloud.sdk.Call;
import com.pcloud.sdk.Checksums;
import com.pcloud.sdk.RemoteEntry;
import com.pcloud.sdk.RemoteFile;
import com.pcloud.sdk.RemoteFolder;
import io.github.ninik.pcloudbackrest.model.RemoteFileEntry;
import okio.ByteString;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SdkPCloudGatewayTest {
    @TempDir
    Path tempDir;

    @Test
    void ensureFolderCreatesMissingParents() throws Exception {
        FakeApiClient fakeClient = new FakeApiClient();
        fakeClient.existingFolders.add("/");
        SdkPCloudGateway gateway = new SdkPCloudGateway(fakeClient.proxy());

        int created = gateway.ensureFolder("/Backups/Immich");

        assertEquals(2, created);
        assertEquals(List.of("/Backups", "/Backups/Immich"), fakeClient.createdFolders);
        assertTrue(fakeClient.existingFolders.contains("/Backups/Immich"));
    }

    @Test
    void ensureFolderDoesNothingForRoot() throws Exception {
        FakeApiClient fakeClient = new FakeApiClient();
        SdkPCloudGateway gateway = new SdkPCloudGateway(fakeClient.proxy());

        assertEquals(0, gateway.ensureFolder("/"));
        assertTrue(fakeClient.createdFolders.isEmpty());
    }

    @Test
    void ensureFolderTreatsAlreadyExistsAsSuccess() throws Exception {
        FakeApiClient fakeClient = new FakeApiClient();
        fakeClient.existingFolders.add("/");
        fakeClient.alreadyExistsFolders.add("/Backups");
        SdkPCloudGateway gateway = new SdkPCloudGateway(fakeClient.proxy());

        int created = gateway.ensureFolder("/Backups");

        assertEquals(0, created);
        assertEquals(List.of("/Backups"), fakeClient.createdFolders);
    }

    @Test
    void ensureFolderRethrowsCreateFolderApiErrorWhenFolderStillMissing() {
        FakeApiClient fakeClient = new FakeApiClient();
        fakeClient.existingFolders.add("/");
        fakeClient.failingCreateFolders.add("/Backups");
        SdkPCloudGateway gateway = new SdkPCloudGateway(fakeClient.proxy());

        assertThrows(ApiError.class, () -> gateway.ensureFolder("/Backups"));
    }

    @Test
    void folderExistsReturnsFalseForNotFound() throws Exception {
        FakeApiClient fakeClient = new FakeApiClient();
        SdkPCloudGateway gateway = new SdkPCloudGateway(fakeClient.proxy());

        assertFalse(gateway.folderExists("/missing"));
    }

    @Test
    void folderExistsRethrowsUnexpectedApiError() {
        FakeApiClient fakeClient = new FakeApiClient();
        fakeClient.failingLoadFolders.add("/broken");
        SdkPCloudGateway gateway = new SdkPCloudGateway(fakeClient.proxy());

        assertThrows(ApiError.class, () -> gateway.folderExists("/broken"));
    }

    @Test
    void listFilesRecursivelyMapsRemotePathsAndMetadata() throws Exception {
        Instant modified = Instant.parse("2026-05-03T10:15:30Z");
        RemoteFile file = remoteFile("image.jpg", 123, modified, "remote-hash", 42L);
        RemoteFolder album = remoteFolder("album", List.of(file));
        RemoteFolder root = remoteFolder("Backups", List.of(album));

        FakeApiClient fakeClient = new FakeApiClient();
        fakeClient.folderTree = root;
        SdkPCloudGateway gateway = new SdkPCloudGateway(fakeClient.proxy());

        List<RemoteFileEntry> files = gateway.listFilesRecursively("/Backups");

        assertEquals(1, files.size());
        assertEquals("/Backups/album/image.jpg", files.getFirst().path());
        assertEquals(123, files.getFirst().size());
        assertEquals(modified, files.getFirst().lastModified());
        assertEquals("remote-hash", files.getFirst().hash());
    }

    @Test
    void findFileReturnsEmptyForMissingFileAndEntryForExistingFile() throws Exception {
        Instant modified = Instant.parse("2026-05-03T10:15:30Z");
        FakeApiClient fakeClient = new FakeApiClient();
        fakeClient.filesByPath.put("/Backups/image.jpg", remoteFile("image.jpg", 7, modified, "hash", 99L));
        SdkPCloudGateway gateway = new SdkPCloudGateway(fakeClient.proxy());

        Optional<RemoteFileEntry> found = gateway.findFile("/Backups/image.jpg");
        Optional<RemoteFileEntry> missing = gateway.findFile("/Backups/missing.jpg");

        assertTrue(found.isPresent());
        assertEquals("/Backups/image.jpg", found.get().path());
        assertEquals(7, found.get().size());
        assertTrue(missing.isEmpty());
    }

    @Test
    void findFileRethrowsUnexpectedApiError() {
        FakeApiClient fakeClient = new FakeApiClient();
        fakeClient.failingLoadFiles.add("/Backups/broken.jpg");
        SdkPCloudGateway gateway = new SdkPCloudGateway(fakeClient.proxy());

        assertThrows(ApiError.class, () -> gateway.findFile("/Backups/broken.jpg"));
    }

    @Test
    void sha1ReturnsChecksumWhenAvailableAndEmptyWhenUnavailable() throws Exception {
        RemoteFile file = remoteFile("image.jpg", 7, Instant.EPOCH, "hash", 99L);
        FakeApiClient fakeClient = new FakeApiClient();
        fakeClient.sha1 = ByteString.decodeHex("0123456789abcdef0123456789abcdef01234567");
        SdkPCloudGateway gateway = new SdkPCloudGateway(fakeClient.proxy());

        assertEquals(Optional.of("0123456789abcdef0123456789abcdef01234567"),
                gateway.sha1(new RemoteFileEntry("/Backups/image.jpg", 7, Instant.EPOCH, null, file)));

        fakeClient.throwChecksumError = true;
        assertTrue(gateway.sha1(new RemoteFileEntry("/Backups/image.jpg", 7, Instant.EPOCH, null, file)).isEmpty());
        assertTrue(gateway.sha1(new RemoteFileEntry("/Backups/image.jpg", 7, Instant.EPOCH, null, null)).isEmpty());
    }

    @Test
    void uploadDelegatesToSdkCreateFileWithNormalizedFolderAndModifiedDate() throws Exception {
        Path localFile = tempDir.resolve("upload.txt");
        Files.writeString(localFile, "content");
        Instant modified = Instant.parse("2026-05-03T10:15:30Z");
        FakeApiClient fakeClient = new FakeApiClient();
        SdkPCloudGateway gateway = new SdkPCloudGateway(fakeClient.proxy());

        gateway.upload(localFile, "Backups//Immich", "upload.txt", modified);

        assertEquals("/Backups/Immich", fakeClient.uploadFolder);
        assertEquals("upload.txt", fakeClient.uploadFilename);
        assertEquals(Date.from(modified), fakeClient.uploadModifiedDate);
        assertEquals(1, fakeClient.createFileCalls);
    }

    @Test
    void downloadRequiresSdkRemoteFileHandle() {
        SdkPCloudGateway gateway = new SdkPCloudGateway(new FakeApiClient().proxy());
        RemoteFileEntry entry = new RemoteFileEntry("/Backups/image.jpg", 1, Instant.EPOCH, null, null);

        assertThrows(java.io.IOException.class, () -> gateway.download(entry, tempDir.resolve("image.jpg")));
    }

    @Test
    void downloadCreatesParentDirectoriesAndInvokesSdkFileDownload() throws Exception {
        AtomicBoolean downloadCalled = new AtomicBoolean(false);
        RemoteFile file = remoteFile("image.jpg", 1, Instant.EPOCH, "hash", 1L, downloadCalled);
        SdkPCloudGateway gateway = new SdkPCloudGateway(new FakeApiClient().proxy());
        Path localFile = tempDir.resolve("album/image.jpg");

        gateway.download(new RemoteFileEntry("/Backups/album/image.jpg", 1, Instant.EPOCH, null, file), localFile);

        assertTrue(Files.isDirectory(localFile.getParent()));
        assertTrue(downloadCalled.get());
    }

    @Test
    void closeShutsDownSdkClient() {
        FakeApiClient fakeClient = new FakeApiClient();

        new SdkPCloudGateway(fakeClient.proxy()).close();

        assertTrue(fakeClient.shutdownCalled);
    }

    private static RemoteFolder remoteFolder(String name, List<RemoteEntry> children) {
        InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
            case "name" -> name;
            case "children" -> children;
            case "isFile" -> false;
            case "isFolder" -> true;
            case "asFolder" -> proxy;
            case "asFile" -> throw new IllegalStateException("Not a file");
            case "folderId" -> 0L;
            case "lastModified", "created" -> new Date(0L);
            case "parentFolderId" -> 0L;
            case "canRead", "canModify", "canDelete", "isMine", "isShared", "canCreate" -> true;
            case "id" -> "folder-" + name;
            case "toString" -> name;
            default -> defaultValue(method.getReturnType());
        };
        return proxy(RemoteFolder.class, handler);
    }

    private static RemoteFile remoteFile(String name, long size, Instant modified, String hash, long fileId) {
        return remoteFile(name, size, modified, hash, fileId, new AtomicBoolean(false));
    }

    private static RemoteFile remoteFile(
            String name,
            long size,
            Instant modified,
            String hash,
            long fileId,
            AtomicBoolean downloadCalled
    ) {
        InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
            case "name" -> name;
            case "size" -> size;
            case "lastModified", "created" -> Date.from(modified);
            case "hash" -> hash;
            case "fileId" -> fileId;
            case "isFile" -> true;
            case "isFolder" -> false;
            case "asFile" -> proxy;
            case "asFolder" -> throw new IllegalStateException("Not a folder");
            case "parentFolderId" -> 0L;
            case "contentType" -> "application/octet-stream";
            case "hasThumbnail" -> false;
            case "canRead", "canModify", "canDelete", "isMine", "isShared" -> true;
            case "id" -> "file-" + name;
            case "download" -> {
                downloadCalled.set(true);
                yield null;
            }
            case "toString" -> name;
            default -> defaultValue(method.getReturnType());
        };
        return proxy(RemoteFile.class, handler);
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    @SuppressWarnings("unchecked")
    private static <T> Call<T> call(T value) {
        return (Call<T>) Proxy.newProxyInstance(
                Call.class.getClassLoader(),
                new Class<?>[]{Call.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "execute" -> value;
                    case "isExecuted", "isCanceled" -> false;
                    case "cancel" -> null;
                    case "clone" -> proxy;
                    default -> defaultValue(method.getReturnType());
                }
        );
    }

    @SuppressWarnings("unchecked")
    private static <T> Call<T> failingCall(Exception error) {
        return (Call<T>) Proxy.newProxyInstance(
                Call.class.getClassLoader(),
                new Class<?>[]{Call.class},
                (proxy, method, args) -> {
                    if ("execute".equals(method.getName())) {
                        throw error;
                    }
                    return defaultValue(method.getReturnType());
                }
        );
    }

    private static Object defaultValue(Class<?> returnType) {
        if (returnType == Boolean.TYPE) {
            return false;
        }
        if (returnType == Integer.TYPE) {
            return 0;
        }
        if (returnType == Long.TYPE) {
            return 0L;
        }
        if (returnType == Void.TYPE) {
            return null;
        }
        return null;
    }

    private static final class FakeApiClient {
        private final Set<String> existingFolders = new HashSet<>();
        private final Set<String> alreadyExistsFolders = new HashSet<>();
        private final Set<String> failingCreateFolders = new HashSet<>();
        private final Set<String> failingLoadFolders = new HashSet<>();
        private final Set<String> failingLoadFiles = new HashSet<>();
        private final List<String> createdFolders = new ArrayList<>();
        private final java.util.Map<String, RemoteFile> filesByPath = new java.util.HashMap<>();
        private RemoteFolder folderTree = remoteFolder("root", List.of());
        private ByteString sha1;
        private boolean throwChecksumError;
        private int createFileCalls;
        private String uploadFolder;
        private String uploadFilename;
        private Date uploadModifiedDate;
        private boolean shutdownCalled;

        private ApiClient proxy() {
            return SdkPCloudGatewayTest.proxy(ApiClient.class, (proxy, method, args) -> switch (method.getName()) {
                case "loadFolder" -> loadFolder((String) args[0]);
                case "createFolder" -> createFolder((String) args[0]);
                case "listFolder" -> call(folderTree);
                case "loadFile" -> loadFile((String) args[0]);
                case "getChecksums" -> checksums();
                case "createFile" -> createFile(args);
                case "shutdown" -> {
                    shutdownCalled = true;
                    yield null;
                }
                case "apiHost" -> "api.pcloud.com";
                default -> defaultValue(method.getReturnType());
            });
        }

        private Call<RemoteFolder> loadFolder(String path) {
            if (failingLoadFolders.contains(path)) {
                return failingCall(new ApiError(5000, "temporary failure"));
            }
            if (existingFolders.contains(path)) {
                return call(remoteFolder(path, List.of()));
            }
            return failingCall(new ApiError(2005, "not found"));
        }

        private Call<RemoteFolder> createFolder(String path) {
            createdFolders.add(path);
            if (alreadyExistsFolders.contains(path)) {
                return failingCall(new ApiError(2004, "already exists"));
            }
            if (failingCreateFolders.contains(path)) {
                return failingCall(new ApiError(5000, "create failed"));
            }
            existingFolders.add(path);
            return call(remoteFolder(path, List.of()));
        }

        private Call<RemoteFile> loadFile(String path) {
            if (failingLoadFiles.contains(path)) {
                return failingCall(new ApiError(5000, "temporary failure"));
            }
            RemoteFile file = filesByPath.get(path);
            if (file == null) {
                return failingCall(new ApiError(2005, "not found"));
            }
            return call(file);
        }

        private Call<Checksums> checksums() {
            if (throwChecksumError) {
                return failingCall(new ApiError(5000, "checksum unavailable"));
            }
            Checksums checksums = SdkPCloudGatewayTest.proxy(Checksums.class, (proxy, method, args) -> switch (method.getName()) {
                case "getSha1" -> sha1;
                case "getSha256", "getMd5", "getFile" -> null;
                default -> defaultValue(method.getReturnType());
            });
            return call(checksums);
        }

        private Call<RemoteFile> createFile(Object[] args) {
            createFileCalls++;
            uploadFolder = (String) args[0];
            uploadFilename = (String) args[1];
            uploadModifiedDate = (Date) args[3];
            return call(remoteFile(uploadFilename, 0, Instant.EPOCH, null, 1L));
        }
    }
}
