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
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/off-chance")
@RequiredArgsConstructor
public class StorageController {

    private final StorageService fileManagerService;

    @GetMapping("/storage")
    public ResponseEntity<List<FileInfoDto>> listFiles(
            @RequestParam(value = "relativePath", required = false, defaultValue = "") String relativePath)
            throws IOException {
        return ResponseEntity.ok(fileManagerService.listFilesAsDto(relativePath));
    }

    @GetMapping("/search")
    public ResponseEntity<List<FileInfoDto>> searchFiles(@RequestParam String query) throws IOException {
        return ResponseEntity.ok(fileManagerService.searchFilesByNameAsDto(query));
    }

    @PostMapping("/upload-folder")
    public ResponseEntity<String> uploadFolderFromClient(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam("relativePaths") List<String> relativePaths,
            @RequestParam(value = "parentFolderId", required = false, defaultValue = "") String parentFolderId
    ) throws IOException {
        fileManagerService.uploadFolder(files, relativePaths, parentFolderId);
        return ResponseEntity.status(HttpStatus.CREATED).body("Папка загружена успешно");
    }

    @PostMapping("/upload")
    public ResponseEntity<String> uploadFileAsync(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "folderId", required = false, defaultValue = "") String folderId) throws IOException {
        fileManagerService.uploadMultipartFile(file, folderId);
        return ResponseEntity.status(HttpStatus.CREATED).body("Загрузка начата");
    }

    @GetMapping("/download/{fileId}")
    public CompletableFuture<ResponseEntity<byte[]>> downloadFileAsync(@PathVariable String fileId) throws IOException {
        return fileManagerService.downloadFileToByteArrayAsync(fileId)
                .thenApply(bytes -> ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                "attachment; filename=\"" + Path.of(fileId).getFileName() + "\"")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .body(bytes));
    }

    @GetMapping("/download-zip/{folderId}")
    public CompletableFuture<ResponseEntity<byte[]>> downloadFolderAsZipAsync(@PathVariable String folderId)
            throws IOException {
        return fileManagerService.downloadFolderAsZipAsync(folderId)
                .thenApply(bytes -> ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                "attachment; filename=\"" + folderId.replace("/", "_") + ".zip\"")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .body(bytes));
    }

    @PostMapping("/create-folder")
    public ResponseEntity<String> createFolder(
            @RequestParam String name,
            @RequestParam(value = "parentFolderId", required = false, defaultValue = "") String parentFolderId)
            throws IOException {
        String folderId = fileManagerService.createFolder(name, parentFolderId);
        return ResponseEntity.status(HttpStatus.CREATED).body(folderId);
    }

    @DeleteMapping("/delete-file")
    public ResponseEntity<Void> deleteFile(@RequestParam String filePath) throws IOException {
        fileManagerService.deleteFile(filePath);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/delete-folder/{folderId}")
    public ResponseEntity<Void> deleteFolder(@PathVariable String folderId) throws IOException {
        fileManagerService.deleteFolderRecursively(folderId);
        return ResponseEntity.noContent().build();
    }
}
