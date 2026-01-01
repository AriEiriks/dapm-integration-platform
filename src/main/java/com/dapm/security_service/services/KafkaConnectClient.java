package com.dapm.security_service.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

@Service
public class KafkaConnectClient {
    private final RestTemplate restTemplate = new RestTemplate();
    private final String baseUrl;

    public KafkaConnectClient(@Value("${dapm.kafka-connect.base-url}") String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public List<String> getConnectorNames() {
        String url = baseUrl + "/connectors";
        ResponseEntity<List<String>> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<String>>() {}
        );
        return response.getBody();
    }

    public Map<String, Object> getConnectorInfo(String name) {
        String url = baseUrl + "/connectors/" + name;
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );
        return response.getBody();
    }

    public void createConnector(String name, Map<String, String> config) {
        String url = baseUrl + "/connectors";

        Map<String, Object> payload = new HashMap<>();
        payload.put("name", name);
        payload.put("config", config);

        restTemplate.postForEntity(url, payload, Map.class);
    }

    public void deleteConnector(String name) {
        String encoded = URLEncoder.encode(name, StandardCharsets.UTF_8);
        String url = baseUrl + "/connectors/" + name;
        restTemplate.delete(url);
    }
}
