package com.lizaveta.config;

import lombok.Getter;

import java.nio.file.Path;
import java.nio.file.Paths;

@Getter
public enum StoragePath {
    WINDOWS(Paths.get("D:\\off-chance\\storage")),
    LINUX(Paths.get("/home/lizza/off-chance/storage")),
    OTHER(Paths.get("storage"));

    private final Path path;

    StoragePath(Path path) {
        this.path = path;
    }

    public static StoragePath detect() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) return WINDOWS;
        if (os.contains("nix") || os.contains("nux")) return LINUX;
        return OTHER;
    }
}