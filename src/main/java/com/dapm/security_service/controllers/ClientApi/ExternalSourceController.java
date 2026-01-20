package com.dapm.security_service.controllers.ClientApi;

import com.dapm.security_service.models.dtos.ExternalSourceDto;
import com.dapm.security_service.models.dtos.CreateExternalSourceRequest;
import com.dapm.security_service.models.dtos.ConnectorPluginDto;
import com.dapm.security_service.models.dtos.ConnectorStatusDto;
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

    @GetMapping("/connectors/{name}/config")
    public ResponseEntity<Map<String, String>> getConnectorConfig(
            @PathVariable String name
    ) {
        return ResponseEntity.ok(
                externalSourceService.getExternalSourceConfig(name)
        );
    }

    @PutMapping("/connectors/{name}/config")
    public ResponseEntity<Map<String, String>> updateConnectorConfig(
            @PathVariable String name,
            @RequestBody Map<String, String> config
    ) {
        return ResponseEntity.ok(
                externalSourceService.updateExternalSourceConfig(name, config)
        );
    }

    @PutMapping("/connectors/{name}/pause")
    public ResponseEntity<Void> pauseConnector(@PathVariable String name) {
        externalSourceService.pauseConnector(name);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/connectors/{name}/resume")
    public ResponseEntity<Void> resumeConnector(@PathVariable String name) {
        externalSourceService.resumeConnector(name);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/connectors/{name}/status")
    public ResponseEntity<ConnectorStatusDto> getConnectorStatus(@PathVariable String name) {
        return ResponseEntity.ok(externalSourceService.getConnectorStatus(name));
    }

}
