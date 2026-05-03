package io.github.ninik.pcloudbackrest.gateway;

import com.pcloud.sdk.ApiClient;
import com.pcloud.sdk.ApiError;
import com.pcloud.sdk.Checksums;
import com.pcloud.sdk.DataSink;
import com.pcloud.sdk.DataSource;
import com.pcloud.sdk.RemoteEntry;
import com.pcloud.sdk.RemoteFile;
import com.pcloud.sdk.RemoteFolder;
import com.pcloud.sdk.UploadOptions;
import io.github.ninik.pcloudbackrest.model.RemoteFileEntry;
import io.github.ninik.pcloudbackrest.service.RemotePathService;
import okio.ByteString;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

public final class SdkPCloudGateway implements PCloudGateway {
    private static final int NOT_FOUND = 2005;

    private final ApiClient client;

    public SdkPCloudGateway(ApiClient client) {
        this.client = client;
    }

    @Override
    public int ensureFolder(String remoteFolder) throws Exception {
        String normalized = RemotePathService.normalize(remoteFolder);
        if ("/".equals(normalized)) {
            return 0;
        }
        if (folderExists(normalized)) {
            return 0;
        }
        int created = ensureFolder(RemotePathService.parent(normalized));
        try {
            client.createFolder(normalized).execute();
            return created + 1;
        } catch (ApiError e) {
            if (!isAlreadyExists(e) && !folderExists(normalized)) {
                throw e;
            }
            return created;
        }
    }

    @Override
    public boolean folderExists(String remoteFolder) throws Exception {
        try {
            client.loadFolder(RemotePathService.normalize(remoteFolder)).execute();
            return true;
        } catch (ApiError e) {
            if (isNotFound(e)) {
                return false;
            }
            throw e;
        }
    }

    @Override
    public List<RemoteFileEntry> listFilesRecursively(String remoteFolder) throws Exception {
        RemoteFolder root = client.listFolder(RemotePathService.normalize(remoteFolder), true).execute();
        List<RemoteFileEntry> result = new ArrayList<>();
        collectFiles(root, RemotePathService.normalize(remoteFolder), result);
        return result;
    }

    @Override
    public Optional<RemoteFileEntry> findFile(String remoteFilePath) throws Exception {
        String normalized = RemotePathService.normalize(remoteFilePath);
        try {
            return Optional.of(toEntry(normalized, client.loadFile(normalized).execute()));
        } catch (ApiError e) {
            if (isNotFound(e)) {
                return Optional.empty();
            }
            throw e;
        }
    }

    @Override
    public Optional<String> sha1(RemoteFileEntry remoteFile) throws Exception {
        if (!(remoteFile.sdkFile() instanceof RemoteFile sdkRemoteFile)) {
            return Optional.empty();
        }
        try {
            Checksums checksums = client.getChecksums(sdkRemoteFile.fileId()).execute();
            ByteString sha1 = checksums.getSha1();
            return sha1 == null ? Optional.empty() : Optional.of(sha1.hex());
        } catch (ApiError e) {
            return Optional.empty();
        }
    }

    @Override
    public void upload(Path localFile, String remoteFolder, String filename, Instant modifiedTime) throws Exception {
        Date modifiedDate = modifiedTime == null ? null : Date.from(modifiedTime);
        client.createFile(
                RemotePathService.normalize(remoteFolder),
                filename,
                DataSource.create(localFile.toFile()),
                modifiedDate,
                null,
                UploadOptions.OVERRIDE_FILE
        ).execute();
    }

    @Override
    public void download(RemoteFileEntry remoteFile, Path localFile) throws Exception {
        if (!(remoteFile.sdkFile() instanceof RemoteFile sdkRemoteFile)) {
            throw new IOException("Remote file metadata does not contain a pCloud SDK file handle: " + remoteFile.path());
        }
        Path parent = localFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        sdkRemoteFile.download(DataSink.create(localFile.toFile()));
    }

    @Override
    public void close() {
        client.shutdown();
    }

    private void collectFiles(RemoteFolder folder, String folderPath, List<RemoteFileEntry> result) {
        for (RemoteEntry child : folder.children()) {
            String childPath = RemotePathService.join(folderPath, child.name());
            if (child.isFile()) {
                result.add(toEntry(childPath, child.asFile()));
            } else if (child.isFolder()) {
                collectFiles(child.asFolder(), childPath, result);
            }
        }
    }

    private RemoteFileEntry toEntry(String remotePath, RemoteFile file) {
        Date lastModified = file.lastModified();
        return new RemoteFileEntry(
                RemotePathService.normalize(remotePath),
                file.size(),
                lastModified == null ? null : lastModified.toInstant(),
                file.hash(),
                file
        );
    }

    private boolean isNotFound(ApiError error) {
        return error.errorCode() == NOT_FOUND || error.errorMessage().toLowerCase().contains("not found");
    }

    private boolean isAlreadyExists(ApiError error) {
        String message = error.errorMessage().toLowerCase();
        return message.contains("already exists") || message.contains("exists");
    }
}
