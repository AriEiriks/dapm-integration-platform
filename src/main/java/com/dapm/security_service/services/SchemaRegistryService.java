package com.dapm.security_service.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class SchemaRegistryService {

    private final SchemaRegistryClient schemaRegistryClient;
    private final ObjectMapper objectMapper;

    public SchemaRegistryService(SchemaRegistryClient schemaRegistryClient, ObjectMapper objectMapper) {
        this.schemaRegistryClient = schemaRegistryClient;
        this.objectMapper = objectMapper;
    }

    public List<String> listSubjects() {
        List<String> subjects = schemaRegistryClient.listSubjects();
        return subjects == null ? List.of() : subjects;
    }

    public Map<String, Object> getLatest(String subject) {
        return schemaRegistryClient.getLatest(subject);
    }

    public Map<String, Object> registerJsonSchema(String subject, JsonNode schemaJson) {
        if (schemaJson == null || !schemaJson.isObject()) {
            throw new IllegalArgumentException("Schema must be a JSON object");
        }

        final String schemaAsString;
        try {
            schemaAsString = objectMapper.writeValueAsString(schemaJson);
        } catch (Exception e) {
            throw new IllegalArgumentException("Schema is not valid JSON");
        }

        Map<String, Object> payload = Map.of(
                "schemaType", "JSON",
                "schema", schemaAsString
        );

        return schemaRegistryClient.registerSchema(subject, payload);
    }
}
