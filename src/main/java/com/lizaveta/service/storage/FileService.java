package com.lizaveta.service.storage;

import com.lizaveta.service.storage.util.FileUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import lombok.RequiredArgsConstructor;
import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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

    public byte[] downloadFileToByteArray(Path userRoot, String fileId) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Path filePath = FileUtils.resolveSecurePath(userRoot, fileId);
            if (!Files.exists(filePath) || Files.isDirectory(filePath)) {
                throw new FileNotFoundException("Файл не найден: " + fileId);
            }
            Files.copy(filePath, outputStream);
            return outputStream.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void deleteFile(Path userRoot, String filePath) throws IOException {
        Path path = FileUtils.resolveSecurePath(userRoot, filePath);
        if (!Files.exists(path) || Files.isDirectory(path)) {
            throw new FileNotFoundException("Файл не найден: " + filePath);
        }
        Files.delete(path);
    }
}