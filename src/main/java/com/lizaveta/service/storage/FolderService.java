package com.lizaveta.service.storage;

import com.lizaveta.service.storage.util.FileUtils;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.*;
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

    public void zipFolder(Path folderPath, OutputStream outputStream) throws IOException {
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