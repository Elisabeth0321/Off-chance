package com.lizaveta.controller;

import com.lizaveta.model.fileDTO.FileInfoDto;
import com.lizaveta.service.storage.StorageService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import lombok.RequiredArgsConstructor;
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

    private final StorageService storageService;

    @GetMapping("/storage")
    public ResponseEntity<List<FileInfoDto>> listFiles(
            @RequestParam(value = "relativePath", required = false, defaultValue = "") String relativePath)
            throws IOException {
        return ResponseEntity.ok(storageService.listFilesAsDto(relativePath));
    }

    @GetMapping("/search")
    public ResponseEntity<List<FileInfoDto>> searchFiles(@RequestParam String query) throws IOException {
        return ResponseEntity.ok(storageService.searchFilesByNameAsDto(query));
    }

    @PostMapping("/upload-folder")
    public CompletableFuture<ResponseEntity<String>> uploadFolderFromClient(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam("relativePaths") List<String> relativePaths,
            @RequestParam(value = "parentFolderId", required = false, defaultValue = "") String parentFolderId
    ) throws IOException {
        Path userRootPath = storageService.getUserStorageRoot();
        return storageService.uploadFolderAsync(files, relativePaths, parentFolderId, userRootPath)
                .thenApply(v -> ResponseEntity.status(HttpStatus.CREATED).body("Папка загружена успешно"));
    }

    @PostMapping("/upload")
    public CompletableFuture<ResponseEntity<String>> uploadFileAsync(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "folderId", required = false, defaultValue = "") String folderId
    ) throws IOException {
        Path userRootPath = storageService.getUserStorageRoot();
        return storageService.uploadMultipartFileAsync(file, folderId, userRootPath)
                .thenApply(v -> ResponseEntity.status(HttpStatus.CREATED).body("Загрузка начата"));
    }

    @GetMapping("/download/{fileId}")
    public CompletableFuture<ResponseEntity<byte[]>> downloadFileAsync(@PathVariable String fileId) throws IOException {
        Path userRootPath = storageService.getUserStorageRoot();
        return storageService.downloadFileToByteArrayAsync(fileId, userRootPath)
                .thenApply(bytes -> ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                "attachment; filename=\"" + Path.of(fileId).getFileName() + "\"")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .body(bytes));
    }

    @GetMapping("/download-zip/{folderId}")
    public CompletableFuture<ResponseEntity<byte[]>> downloadFolderAsZipAsync(@PathVariable String folderId) throws IOException {
        Path userRootPath = storageService.getUserStorageRoot();

        return storageService.downloadFolderAsZipAsync(folderId, userRootPath)
                .thenApply(bytes -> {
                    String safeFolderName = folderId.replace("/", "_");
                    if (!safeFolderName.toLowerCase().endsWith(".zip")) {
                        safeFolderName += ".zip";
                    }

                    return ResponseEntity.ok()
                            .header(HttpHeaders.CONTENT_DISPOSITION,
                                    "attachment;" +
                                            "filename=\"" + safeFolderName + "\";" +
                                            "filename*=UTF-8''" + UriUtils.encode(safeFolderName, StandardCharsets.UTF_8))
                            .contentType(MediaType.APPLICATION_OCTET_STREAM)
                            .body(bytes);
                });
    }

    @PostMapping("/create-folder")
    public ResponseEntity<String> createFolder(
            @RequestParam String name,
            @RequestParam(value = "parentFolderId", required = false, defaultValue = "") String parentFolderId)
            throws IOException {
        String folderId = storageService.createFolder(name, parentFolderId);
        return ResponseEntity.status(HttpStatus.CREATED).body(folderId);
    }

    @DeleteMapping("/delete-file")
    public ResponseEntity<Void> deleteFile(@RequestParam String filePath) throws IOException {
        storageService.deleteFile(filePath);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/delete-folder/{folderId}")
    public ResponseEntity<Void> deleteFolder(@PathVariable String folderId) throws IOException {
        storageService.deleteFolderRecursively(folderId);
        return ResponseEntity.noContent().build();
    }
}
