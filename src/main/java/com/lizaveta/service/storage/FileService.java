package com.lizaveta.service.storage;

import com.lizaveta.service.storage.util.FileUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import lombok.RequiredArgsConstructor;
import java.io.*;
import java.nio.file.*;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class FileService {

    public String uploadFile(Path userRoot, MultipartFile file, String folderId) throws IOException {
        Path folderPath = FileUtils.resolveSecurePath(userRoot, folderId);
        FileUtils.createDirectoriesIfNotExist(folderPath);

        String fileName = FileUtils.generateTimestampedFileName(Objects.requireNonNull(file.getOriginalFilename()));
        Path targetPath = folderPath.resolve(fileName);

        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }
        return userRoot.relativize(targetPath).toString();
    }

    public void uploadFolder(Path userRoot, List<MultipartFile> files, List<String> relativePaths, String parentFolderId) throws IOException {
        if (files == null || files.isEmpty() || relativePaths == null || relativePaths.isEmpty() || files.size() != relativePaths.size()) {
            throw new IllegalArgumentException("Некорректные данные для загрузки папки");
        }

        Path relPathObj = Paths.get(relativePaths.get(0));
        Path targetRoot = FileUtils.resolveSecurePath(userRoot, parentFolderId).resolve(relPathObj.getName(0));
        String timestamp = FileUtils.generateTimestampedFileName("");

        for (int i = 0; i < files.size(); i++) {
            MultipartFile file = files.get(i);
            Path relPath = Paths.get(relativePaths.get(i));

            Path subPath = relPath.getNameCount() > 1
                    ? relPath.subpath(1, relPath.getNameCount())
                    : Paths.get(Objects.requireNonNull(file.getOriginalFilename()));

            String fileNameWithTimestamp = timestamp + "_" + subPath.getFileName();
            Path fullTargetPath = targetRoot.resolve(
                    subPath.getParent() != null
                            ? subPath.getParent().resolve(fileNameWithTimestamp)
                            : Paths.get(fileNameWithTimestamp)
            ).normalize();

            if (!fullTargetPath.startsWith(userRoot)) {
                throw new SecurityException("Выход за пределы директории пользователя");
            }

            FileUtils.createDirectoriesIfNotExist(fullTargetPath.getParent());
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, fullTargetPath, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    @Async
    public CompletableFuture<byte[]> downloadFileToByteArrayAsync(Path userRoot, String fileId) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            downloadFileToStream(userRoot, fileId, outputStream);
            return CompletableFuture.completedFuture(outputStream.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public String downloadFileToStream(Path userRoot, String fileId, OutputStream outputStream) throws IOException {
        Path filePath = FileUtils.resolveSecurePath(userRoot, fileId);
        if (!Files.exists(filePath) || Files.isDirectory(filePath)) {
            throw new FileNotFoundException("Файл не найден: " + fileId);
        }
        Files.copy(filePath, outputStream);
        return filePath.getFileName().toString();
    }

    public void deleteFile(Path userRoot, String filePath) throws IOException {
        Path path = FileUtils.resolveSecurePath(userRoot, filePath);
        if (!Files.exists(path) || Files.isDirectory(path)) {
            throw new FileNotFoundException("Файл не найден: " + filePath);
        }
        Files.delete(path);
    }
}