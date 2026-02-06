package com.dapm.security_service.services;

import com.dapm.security_service.models.dtos.FilesDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.file.*;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

@Service
public class FilesService {

    private final Path dataDir;

    public FilesService(@Value("${dapm.data-dir:/data}") String dataDir) {
        this.dataDir = Paths.get(dataDir).toAbsolutePath().normalize();
    }

    public List<FilesDto> listFiles() {
        if (!Files.exists(dataDir)) return List.of();

        try (Stream<Path> stream = Files.list(dataDir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(p -> !p.getFileName().toString().endsWith(".tmp"))
                    .map(p -> toDto(p, "/data/" + p.getFileName()))
                    .sorted(Comparator.comparing(FilesDto::getName))
                    .toList();
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to list files in /data: " + e.getMessage());
        }
    }


    public FilesDto upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }

        try {
            Files.createDirectories(dataDir);

            String originalName = file.getOriginalFilename() == null
                    ? "upload.bin"
                    : file.getOriginalFilename();

            String safeName = sanitizeFilename(originalName);

            Path finalPath = dataDir.resolve(safeName).normalize();
            if (!finalPath.startsWith(dataDir)) {
                throw new IllegalArgumentException("Invalid file name");
            }

            Path tmpPath = dataDir.resolve(safeName + ".tmp").normalize();
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, tmpPath, StandardCopyOption.REPLACE_EXISTING);
            }

            Files.move(
                    tmpPath,
                    finalPath,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
            );

            return new FilesDto(
                    safeName,
                    Files.size(finalPath),
                    "/data/" + safeName
            );

        } catch (Exception e) {
            throw new IllegalArgumentException("Upload failed: " + e.getMessage());
        }
    }

    private FilesDto toDto(Path path, String connectPath) {
        try {
            return new FilesDto(
                    path.getFileName().toString(),
                    Files.size(path),
                    connectPath
            );
        } catch (Exception e) {
            return new FilesDto(path.getFileName().toString(), -1, connectPath);
        }
    }

    private String sanitizeFilename(String name) {
        String n = name.replace("\\", "/");
        n = n.substring(n.lastIndexOf('/') + 1);

        n = n.replaceAll("[^a-zA-Z0-9._-]", "_");

        return n.isBlank() ? "upload.bin" : n;
    }
}
