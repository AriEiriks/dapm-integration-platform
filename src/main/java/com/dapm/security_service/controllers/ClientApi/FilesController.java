package com.dapm.security_service.controllers.ClientApi;

import com.dapm.security_service.models.dtos.FilesDto;
import com.dapm.security_service.services.FilesService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/files")
public class FilesController {

    private final FilesService filesService;

    public FilesController(FilesService filesService) {
        this.filesService = filesService;
    }

    @GetMapping
    public List<FilesDto> listFiles() {
        return filesService.listFiles();
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FilesDto> upload(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(filesService.upload(file));
    }
}
