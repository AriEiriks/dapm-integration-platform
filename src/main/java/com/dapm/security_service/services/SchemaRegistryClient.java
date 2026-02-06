package com.dapm.security_service.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Service
public class SchemaRegistryClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public SchemaRegistryClient(@Value("${dapm.schema-registry.base-url}") String baseUrl) {
        this.baseUrl = baseUrl;
        this.restTemplate = new RestTemplate();
    }

    public List<String> listSubjects() {
        String url = baseUrl + "/subjects";
        ResponseEntity<List<String>> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<String>>() {}
        );
        return response.getBody();
    }

    public Map<String, Object> registerSchema(String subject, Map<String, Object> payload) {
        String encodedSubject = UriUtils.encodePathSegment(subject, StandardCharsets.UTF_8);
        String url = baseUrl + "/subjects/" + encodedSubject + "/versions";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/vnd.schemaregistry.v1+json"));

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                new HttpEntity<>(payload, headers),
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );

        return response.getBody();
    }

    public Map<String, Object> getLatest(String subject) {
        String encodedSubject = UriUtils.encodePathSegment(subject, StandardCharsets.UTF_8);
        String url = baseUrl + "/subjects/" + encodedSubject + "/versions/latest";

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );
        return response.getBody();
    }
}
