package com.dapm.security_service.controllers.ClientApi;

import com.dapm.security_service.services.SchemaRegistryService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/schema-registry")
public class SchemaRegistryController {

    private final SchemaRegistryService schemaRegistryService;

    public SchemaRegistryController(SchemaRegistryService schemaRegistryService) {
        this.schemaRegistryService = schemaRegistryService;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        schemaRegistryService.listSubjects();
        return Map.of("status", "UP");
    }

    @GetMapping("/subjects")
    public List<String> listSubjects() {
        return schemaRegistryService.listSubjects();
    }

    @GetMapping("/subjects/{subject}/versions/latest")
    public ResponseEntity<Map<String, Object>> latest(@PathVariable String subject) {
        return ResponseEntity.ok(schemaRegistryService.getLatest(subject));
    }

    @PostMapping("/subjects/{subject}/versions")
    public ResponseEntity<Map<String, Object>> register(
            @PathVariable String subject,
            @RequestBody JsonNode schema
    ) {
        return ResponseEntity.ok(schemaRegistryService.registerJsonSchema(subject, schema));
    }
}
