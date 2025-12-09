package com.dapm.security_service.controllers.ClientApi;

import com.dapm.security_service.models.dtos.ExternalSourceDto;
import com.dapm.security_service.services.ExternalSourceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
}
