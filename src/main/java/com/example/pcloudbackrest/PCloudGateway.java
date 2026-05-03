package com.example.pcloudbackrest;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

interface PCloudGateway extends AutoCloseable {
    int ensureFolder(String remoteFolder) throws Exception;

    boolean folderExists(String remoteFolder) throws Exception;

    List<RemoteFileEntry> listFilesRecursively(String remoteFolder) throws Exception;

    Optional<RemoteFileEntry> findFile(String remoteFilePath) throws Exception;

    Optional<String> sha1(RemoteFileEntry remoteFile) throws Exception;

    void upload(Path localFile, String remoteFolder, String filename, Instant modifiedTime) throws Exception;

    void download(RemoteFileEntry remoteFile, Path localFile) throws Exception;

    @Override
    void close();
}
