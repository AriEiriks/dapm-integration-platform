package com.dapm.security_service.controllers.ClientApi;

import com.dapm.security_service.models.dtos.ExternalSourceDto;
import com.dapm.security_service.models.dtos.CreateExternalSourceRequest;
import com.dapm.security_service.models.dtos.ConnectorPluginDto;
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

    @GetMapping("/plugins/{connectorClass}/config-defs")
    public ResponseEntity<List<Map<String, Object>>> getPluginConfigDefs(
            @PathVariable String connectorClass
    ) {
        return ResponseEntity.ok(externalSourceService.getConnectorPluginConfigDefs(connectorClass));
    }

    @DeleteMapping("/connectors/{name}")
    public ResponseEntity<Void> deleteConnector(@PathVariable String name) {
        externalSourceService.deleteConnector(name);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/plugins")
    public ResponseEntity<List<ConnectorPluginDto>> listConnectorPlugins() {
        return ResponseEntity.ok(externalSourceService.getConnectorPlugins());
    }
}
