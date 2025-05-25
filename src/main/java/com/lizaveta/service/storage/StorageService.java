package com.lizaveta.service.storage;

import com.lizaveta.config.StoragePath;
import com.lizaveta.model.fileDTO.FileInfoDto;
import com.lizaveta.model.fileDTO.FileType;
import com.lizaveta.service.auth.UserService;
import com.lizaveta.service.storage.util.FileUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import lombok.RequiredArgsConstructor;
import jakarta.servlet.http.HttpServletRequest;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class StorageService {

    private final UserService userService;
    private final HttpServletRequest request;
    private final FolderService folderService;
    private final FileService fileService;

    private static final Path GLOBAL_STORAGE_ROOT = StoragePath.detect().getPath();

    public List<FileInfoDto> listFilesAsDto(String relativePath) throws IOException {
        Path targetDir = FileUtils.resolveSecurePath(getUserStorageRoot(), relativePath);
        if (!Files.exists(targetDir) || !Files.isDirectory(targetDir)) return Collections.emptyList();

        List<FileInfoDto> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(targetDir)) {
            for (Path path : stream) {
                FileType type = Files.isDirectory(path) ? FileType.FOLDER : FileType.FILE;
                files.add(new FileInfoDto(
                        getUserStorageRoot().relativize(path).toString(),
                        path.getFileName().toString(),
                        type
                ));
            }
        }
        return files;
    }

    public void uploadMultipartFile(MultipartFile file, String folderId) throws IOException {
        fileService.uploadFile(getUserStorageRoot(), file, folderId);
    }

    public void uploadFolder(List<MultipartFile> files, List<String> relativePaths, String parentFolderId) throws IOException {
        fileService.uploadFolder(getUserStorageRoot(), files, relativePaths, parentFolderId);
    }

    @Async
    public CompletableFuture<byte[]> downloadFileToByteArrayAsync(String fileId) throws IOException {
        return fileService.downloadFileToByteArrayAsync(getUserStorageRoot(), fileId);
    }

    public String downloadFileToStream(String fileId, OutputStream outputStream) throws IOException {
        return fileService.downloadFileToStream(getUserStorageRoot(), fileId, outputStream);
    }

    @Async
    public CompletableFuture<byte[]> downloadFolderAsZipAsync(String folderId) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            folderService.zipFolder(FileUtils.resolveSecurePath(getUserStorageRoot(), folderId), outputStream);
            return CompletableFuture.completedFuture(outputStream.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void deleteFile(String filePath) throws IOException {
        fileService.deleteFile(getUserStorageRoot(), filePath);
    }

    public void deleteFolderRecursively(String folderId) throws IOException {
        Path folderPath = FileUtils.resolveSecurePath(getUserStorageRoot(), folderId);
        folderService.deleteFolder(folderPath);
    }

    public List<FileInfoDto> searchFilesByNameAsDto(String nameQuery) throws IOException {
        List<FileInfoDto> result = new ArrayList<>();
        Files.walk(getUserStorageRoot())
                .filter(path -> path.getFileName().toString().contains(nameQuery))
                .forEach(path -> {
                    FileType type = Files.isDirectory(path) ? FileType.FOLDER : FileType.FILE;
                    try {
                        result.add(new FileInfoDto(
                                getUserStorageRoot().relativize(path).toString(),
                                path.getFileName().toString(),
                                type
                        ));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
        return result;
    }

    public String createFolder(String name, String parentFolderId) throws IOException {
        Path parentPath = FileUtils.resolveSecurePath(getUserStorageRoot(), parentFolderId);
        Path newFolderPath = parentPath.resolve(name);
        folderService.createFolder(newFolderPath);
        return getUserStorageRoot().relativize(newFolderPath).toString();
    }

    public void createUserFolder(String folderId) throws IOException {
        Path newFolderPath = FileUtils.resolveSecurePath(GLOBAL_STORAGE_ROOT, folderId);
        folderService.createFolder(newFolderPath);
    }

    public void deleteUserFolder(String folderId) throws IOException {
        Path folderPath = FileUtils.resolveSecurePath(GLOBAL_STORAGE_ROOT, folderId);
        folderService.deleteFolder(folderPath);
    }

    private Path getUserStorageRoot() throws IOException {
        String token = userService.extractTokenFromCookies(request);
        return userService.getUserByAccessToken(token)
                .map(user -> GLOBAL_STORAGE_ROOT.resolve(user.getId()).normalize())
                .filter(path -> path.startsWith(GLOBAL_STORAGE_ROOT))
                .map(path -> {
                    try {
                        FileUtils.createDirectoriesIfNotExist(path);
                        return path;
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                })
                .orElseThrow(() -> new SecurityException("Недопустимый токен или пользователь не найден"));
    }
}
