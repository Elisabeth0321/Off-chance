package com.lizaveta.service;

import com.lizaveta.model.fileDTO.FileInfoDto;
import com.lizaveta.model.fileDTO.FileType;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@RequiredArgsConstructor
public class FileManagerService {

    private final UserService userService;
    private final HttpServletRequest request;

    private static final Path GLOBAL_STORAGE_ROOT;

    static {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            GLOBAL_STORAGE_ROOT = Paths.get("D:\\off-chance\\storage"); // Windows
        } else if (os.contains("nix") || os.contains("nux")) {
            GLOBAL_STORAGE_ROOT = Paths.get("/home/lizza/off-chance/storage"); // Linux
        } else {
            GLOBAL_STORAGE_ROOT = Paths.get("storage"); // Для других ОС
        }
    }

    public List<FileInfoDto> listFilesAsDto(String relativePath) throws IOException {
        List<FileInfoDto> files = new ArrayList<>();
        Path targetDir = resolvePathSecurely(relativePath);
        if (!Files.exists(targetDir) || !Files.isDirectory(targetDir)) {
            return files;
        }
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

    public String uploadMultipartFile(MultipartFile file, String folderId) throws IOException {
        Path folderPath = resolvePathSecurely(folderId);
        if (!Files.exists(folderPath)) {
            Files.createDirectories(folderPath);
        }

        String originalFileName = file.getOriginalFilename();
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        String fileNameWithTimestamp = timestamp + "_" + originalFileName;
        Path targetPath = folderPath.resolve(fileNameWithTimestamp);

        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }
        return getUserStorageRoot().relativize(targetPath).toString();
    }

    public void uploadFolder(List<MultipartFile> files, List<String> relativePaths, String parentFolderId) throws IOException {
        if (files == null || files.isEmpty() || relativePaths == null || relativePaths.isEmpty() || files.size() != relativePaths.size()) {
            throw new IllegalArgumentException("Некорректные данные для загрузки папки");
        }

        Path relPathObj = Paths.get(relativePaths.get(0));
        String rootFolderName = relPathObj.getName(0).toString();
        Path targetRoot = resolvePathSecurely(parentFolderId).resolve(rootFolderName);

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));

        for (int i = 0; i < files.size(); i++) {
            MultipartFile file = files.get(i);
            Path relPath = Paths.get(relativePaths.get(i));

            Path subPath = relPath.getNameCount() > 1
                    ? relPath.subpath(1, relPath.getNameCount())
                    : Paths.get(Objects.requireNonNull(file.getOriginalFilename()));

            String originalFileName = subPath.getFileName().toString();
            String fileNameWithTimestamp = timestamp + "_" + originalFileName;

            Path subPathWithTimestamp = subPath.getParent() != null
                    ? subPath.getParent().resolve(fileNameWithTimestamp)
                    : Paths.get(fileNameWithTimestamp);

            Path fullTargetPath = targetRoot.resolve(subPathWithTimestamp).normalize();

            if (!fullTargetPath.startsWith(getUserStorageRoot())) {
                throw new SecurityException("Попытка выхода за пределы допустимой директории!");
            }

            Files.createDirectories(fullTargetPath.getParent());
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, fullTargetPath, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    @Async
    public CompletableFuture<byte[]> downloadFileToByteArrayAsync(String fileId) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            downloadFileToStream(fileId, outputStream);
            return CompletableFuture.completedFuture(outputStream.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public String downloadFileToStream(String fileId, OutputStream outputStream) throws IOException {
        Path filePath = resolvePathSecurely(fileId);
        if (!Files.exists(filePath) || Files.isDirectory(filePath)) {
            throw new FileNotFoundException("Файл не найден: " + fileId);
        }
        Files.copy(filePath, outputStream);
        return filePath.getFileName().toString();
    }

    @Async
    public CompletableFuture<byte[]> downloadFolderAsZipAsync(String folderId) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            downloadFolderAsZip(folderId, outputStream);
            return CompletableFuture.completedFuture(outputStream.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void downloadFolderAsZip(String folderId, OutputStream outputStream) throws IOException {
        Path folderPath = resolvePathSecurely(folderId);
        if (!Files.exists(folderPath) || !Files.isDirectory(folderPath)) {
            throw new FileNotFoundException("Папка не найдена: " + folderId);
        }
        try (ZipOutputStream zipOut = new ZipOutputStream(outputStream)) {
            Files.walk(folderPath)
                    .filter(Files::isRegularFile)
                    .forEach(path -> {
                        try {
                            ZipEntry zipEntry = new ZipEntry(folderPath.relativize(path).toString());
                            zipOut.putNextEntry(zipEntry);
                            Files.copy(path, zipOut);
                            zipOut.closeEntry();
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
        }
    }

    public void deleteFile(String filePath) throws IOException {
        Path path = resolvePathSecurely(filePath);
        if (!Files.exists(path) || Files.isDirectory(path)) {
            throw new FileNotFoundException("Файл не найден: " + filePath);
        }
        Files.delete(path);
    }

    public void deleteFolderRecursively(String folderId) throws IOException {
        Path folderPath = resolvePathSecurely(folderId);
        if (!Files.exists(folderPath) || !Files.isDirectory(folderPath)) {
            throw new FileNotFoundException("Папка не найдена: " + folderId);
        }
        Files.walk(folderPath)
                .sorted((o1, o2) -> o2.compareTo(o1))
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
    }

    public List<FileInfoDto> searchFilesByNameAsDto(String nameQuery) throws IOException {
        List<FileInfoDto> result = new ArrayList<>();
        Files.walk(getUserStorageRoot())
                .filter(path -> {
                    try {
                        return !path.equals(getUserStorageRoot());
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                })
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
        Path parentPath = resolvePathSecurely(parentFolderId);
        if (!Files.exists(parentPath)) {
            Files.createDirectories(parentPath);
        }
        Path newFolderPath = parentPath.resolve(name);
        Files.createDirectories(newFolderPath);
        return getUserStorageRoot().relativize(newFolderPath).toString();
    }

    public void createUserFolder(String folderId) throws IOException {
        Path newFolderPath = GLOBAL_STORAGE_ROOT.resolve(folderId).normalize();
        if (!newFolderPath.startsWith(GLOBAL_STORAGE_ROOT)) {
            throw new SecurityException("Попытка выхода за пределы допустимой директории!");
        }

        if (!Files.exists(newFolderPath)) {
            Files.createDirectories(newFolderPath);
        }
    }

    public void deleteUserFolder(String folderId) throws IOException {
        Path folderPath = GLOBAL_STORAGE_ROOT.resolve(folderId).normalize();
        if (!Files.exists(folderPath) || !Files.isDirectory(folderPath)) {
            throw new FileNotFoundException("Папка не найдена: " + folderId);
        }
        Files.walk(folderPath)
                .sorted((o1, o2) -> o2.compareTo(o1))
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
    }

    private Path resolvePathSecurely(String relativePath) throws IOException {
        Path resolved = getUserStorageRoot().resolve(relativePath).normalize();
        if (!resolved.startsWith(getUserStorageRoot())) {
            throw new SecurityException("Попытка выхода за пределы пользовательской директории!");
        }
        return resolved;
    }

    private Path getUserStorageRoot() throws IOException {
        String accessToken = userService.extractTokenFromCookies(request);
        return userService.getUserByAccessToken(accessToken)
                .map(user -> GLOBAL_STORAGE_ROOT.resolve(user.getId()).normalize())
                .filter(path -> path.startsWith(GLOBAL_STORAGE_ROOT))
                .map(path -> {
                    try {
                        if (!Files.exists(path)) {
                            Files.createDirectories(path);
                        }
                        return path;
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                })
                .orElseThrow(() -> new SecurityException("Недопустимый токен или пользователь не найден"));
    }
}