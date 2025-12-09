package com.dapm.security_service.controllers.ClientApi;

import com.dapm.security_service.models.dtos.ExternalSourceDto;
import com.dapm.security_service.models.dtos.CreateExternalSourceRequest;
import com.dapm.security_service.services.ExternalSourceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/external-sources")
public class ExternalSourceController {
    private final ExternalSourceService externalSourceService;

    public ExternalSourceController(ExternalSourceService externalSourceService) {
        this.externalSourceService = externalSourceService;
    }

    @GetMapping
    public List<ExternalSourceDto> getExternalSources() {
        return externalSourceService.listExternalSources();
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> createExternalSource(
            @RequestBody CreateExternalSourceRequest request
    ) {
        externalSourceService.createExternalSource(request);
        return ResponseEntity.ok(
                Map.of("message", "External source connector created successfully")
        );
    }
}
