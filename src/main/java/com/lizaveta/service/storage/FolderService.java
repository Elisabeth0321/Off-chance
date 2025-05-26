package com.lizaveta.service.storage;

import com.lizaveta.service.storage.util.FileUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.*;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class FolderService {

    public void deleteFolder(Path folderPath) throws IOException {
        FileUtils.deleteRecursively(folderPath);
    }

    public void createFolder(Path path) throws IOException {
        FileUtils.createDirectoriesIfNotExist(path);
    }

    public String uploadFolder(Path userRoot, List<MultipartFile> files, List<String> relativePaths, String parentFolderId) throws IOException {
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

        return userRoot.relativize(targetRoot).toString();
    }

    public byte[] downloadFolder(Path userRoot, String folderId) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            zipFolder(FileUtils.resolveSecurePath(userRoot, folderId), outputStream);
            return outputStream.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void zipFolder(Path folderPath, OutputStream outputStream) throws IOException {
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
}