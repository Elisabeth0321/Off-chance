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
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@RequiredArgsConstructor
public class StorageService {

    private final UserService userService;
    private final HttpServletRequest request;
    private final FolderService folderService;
    private final FileService fileService;

    private final ExecutorService executorService = Executors.newFixedThreadPool(10);

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

    @Async("executorService")
    public CompletableFuture<String> uploadMultipartFileAsync(MultipartFile file, String folderId, Path userRootPath) throws IOException {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return fileService.uploadFile(userRootPath, file, folderId);
            } catch (IOException e) {
                throw new CompletionException(e);
            }
        }, executorService);
    }

    @Async("executorService")
    public CompletableFuture<String> uploadFolderAsync(List<MultipartFile> files, List<String> relativePaths, String parentFolderId, Path userRootPath) throws IOException {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return folderService.uploadFolder(userRootPath, files, relativePaths, parentFolderId);
            } catch (IOException e) {
                throw new CompletionException(e);
            }
        }, executorService);
    }

    @Async("executorService")
    public CompletableFuture<byte[]> downloadFileToByteArrayAsync(String fileId, Path userRootPath) throws IOException {
        return CompletableFuture.supplyAsync(() ->
                fileService.downloadFileToByteArray(userRootPath, fileId), executorService);
    }

    @Async("executorService")
    public CompletableFuture<byte[]> downloadFolderAsZipAsync(String folderId, Path userRootPath) {
        return CompletableFuture.supplyAsync(() ->
                folderService.downloadFolder(userRootPath, folderId), executorService);
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

    public Path getUserStorageRoot() throws IOException {
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
