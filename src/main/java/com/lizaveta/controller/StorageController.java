package com.lizaveta.controller;

import com.lizaveta.model.fileDTO.FileInfoDto;
import com.lizaveta.service.storage.StorageService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/off-chance")
@RequiredArgsConstructor
public class StorageController {

    private static final Logger logger = LoggerFactory.getLogger(StorageController.class);

    private final StorageService storageService;

    @GetMapping("/storage")
    public ResponseEntity<List<FileInfoDto>> listFiles(
            @RequestParam(value = "relativePath", required = false, defaultValue = "") String relativePath)
            throws IOException {
        logger.info("Получение списка файлов по пути: '{}'", relativePath);
        List<FileInfoDto> files = storageService.listFilesAsDto(relativePath);
        logger.info("Найдено {} файлов", files.size());
        return ResponseEntity.ok(files);
    }

    @GetMapping("/search")
    public ResponseEntity<List<FileInfoDto>> searchFiles(@RequestParam String query) throws IOException {
        logger.info("Поиск файлов по имени: '{}'", query);
        List<FileInfoDto> results = storageService.searchFilesByNameAsDto(query);
        logger.info("Найдено {} совпадений", results.size());
        return ResponseEntity.ok(results);
    }

    @PostMapping("/upload-folder")
    public CompletableFuture<ResponseEntity<String>> uploadFolderFromClient(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam("relativePaths") List<String> relativePaths,
            @RequestParam(value = "parentFolderId", required = false, defaultValue = "") String parentFolderId
    ) throws IOException {
        logger.info("Загрузка папки: файлов = {}, родительская папка ID = '{}'", files.size(), parentFolderId);
        Path userRootPath = storageService.getUserStorageRoot();
        return storageService.uploadFolderAsync(files, relativePaths, parentFolderId, userRootPath)
                .thenApply(v -> {
                    logger.info("Папка успешно загружена");
                    return ResponseEntity.status(HttpStatus.CREATED).body("Папка загружена успешно");
                })
                .exceptionally(ex -> {
                    logger.error("Ошибка при загрузке папки: {}", ex.getMessage(), ex);
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body("Ошибка при загрузке папки");
                });
    }

    @PostMapping("/upload")
    public CompletableFuture<ResponseEntity<String>> uploadFileAsync(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "folderId", required = false, defaultValue = "") String folderId
    ) throws IOException {
        logger.info("Загрузка файла: '{}', в папку '{}'", file.getOriginalFilename(), folderId);
        Path userRootPath = storageService.getUserStorageRoot();
        return storageService.uploadMultipartFileAsync(file, folderId, userRootPath)
                .thenApply(v -> {
                    logger.info("Файл '{}' загружен успешно", file.getOriginalFilename());
                    return ResponseEntity.status(HttpStatus.CREATED).body("Загрузка начата");
                })
                .exceptionally(ex -> {
                    logger.error("Ошибка при загрузке файла '{}': {}", file.getOriginalFilename(), ex.getMessage(), ex);
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body("Ошибка при загрузке файла");
                });
    }

    @GetMapping("/download/{fileId}")
    public CompletableFuture<ResponseEntity<byte[]>> downloadFileAsync(@PathVariable String fileId) throws IOException {
        logger.info("Запрос на скачивание файла: '{}'", fileId);
        Path userRootPath = storageService.getUserStorageRoot();
        return storageService.downloadFileToByteArrayAsync(fileId, userRootPath)
                .thenApply(bytes -> {
                    logger.info("Файл '{}' успешно загружен в память", fileId);
                    return ResponseEntity.ok()
                            .header(HttpHeaders.CONTENT_DISPOSITION,
                                    "attachment; filename=\"" + Path.of(fileId).getFileName() + "\"")
                            .contentType(MediaType.APPLICATION_OCTET_STREAM)
                            .body(bytes);
                })
                .exceptionally(ex -> {
                    logger.error("Ошибка при скачивании файла '{}': {}", fileId, ex.getMessage(), ex);
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
                });
    }

    @GetMapping("/download-zip/{folderId}")
    public CompletableFuture<ResponseEntity<byte[]>> downloadFolderAsZipAsync(@PathVariable String folderId) throws IOException {
        logger.info("Запрос на скачивание архива папки: '{}'", folderId);
        Path userRootPath = storageService.getUserStorageRoot();

        return storageService.downloadFolderAsZipAsync(folderId, userRootPath)
                .thenApply(bytes -> {
                    String safeFolderName = folderId.replace("/", "_");
                    if (!safeFolderName.toLowerCase().endsWith(".zip")) {
                        safeFolderName += ".zip";
                    }

                    logger.info("Папка '{}' успешно заархивирована", folderId);
                    return ResponseEntity.ok()
                            .header(HttpHeaders.CONTENT_DISPOSITION,
                                    "attachment; filename=\"" + safeFolderName + "\";" +
                                            "filename*=UTF-8''" + UriUtils.encode(safeFolderName, StandardCharsets.UTF_8))
                            .contentType(MediaType.APPLICATION_OCTET_STREAM)
                            .body(bytes);
                })
                .exceptionally(ex -> {
                    logger.error("Ошибка при скачивании архива папки '{}': {}", folderId, ex.getMessage(), ex);
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
                });
    }

    @PostMapping("/create-folder")
    public ResponseEntity<String> createFolder(
            @RequestParam String name,
            @RequestParam(value = "parentFolderId", required = false, defaultValue = "") String parentFolderId)
            throws IOException {
        logger.info("Создание папки: '{}', родительская папка ID = '{}'", name, parentFolderId);
        String folderId = storageService.createFolder(name, parentFolderId);
        logger.info("Папка успешно создана: '{}'", folderId);
        return ResponseEntity.status(HttpStatus.CREATED).body(folderId);
    }

    @DeleteMapping("/delete-file")
    public ResponseEntity<Void> deleteFile(@RequestParam String filePath) throws IOException {
        logger.info("Удаление файла: '{}'", filePath);
        storageService.deleteFile(filePath);
        logger.info("Файл '{}' удалён", filePath);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/delete-folder/{folderId}")
    public ResponseEntity<Void> deleteFolder(@PathVariable String folderId) throws IOException {
        logger.info("Удаление папки: '{}'", folderId);
        storageService.deleteFolderRecursively(folderId);
        logger.info("Папка '{}' удалена", folderId);
        return ResponseEntity.noContent().build();
    }
}
