package com.dapm.security_service.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.dapm.security_service.models.dtos.ConnectorPluginDto;

@Service
public class KafkaConnectClient {

    private final RestTemplate restTemplate;
    private final WebClient webClient;
    private final String baseUrl;

    public KafkaConnectClient(
            @Value("${dapm.kafka-connect.base-url}") String baseUrl,
            WebClient kafkaConnectWebClient
    ) {
        this.baseUrl = baseUrl;
        this.restTemplate = new RestTemplate(); //  for GET/POST for now
        this.webClient = kafkaConnectWebClient; // for DELETE because of 415
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
        String encoded = UriUtils.encodePathSegment(name, StandardCharsets.UTF_8);

        webClient
                .delete()
                .uri(baseUrl + "/connectors/" + encoded)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .toBodilessEntity()
                .block();
    }

    public List<ConnectorPluginDto> getConnectorPlugins() {
        String url = baseUrl + "/connector-plugins";

        ResponseEntity<List<Map<String, Object>>> response =
                restTemplate.exchange(
                        url,
                        HttpMethod.GET,
                        null,
                        new ParameterizedTypeReference<>() {}
                );

        List<Map<String, Object>> body = response.getBody();
        if (body == null) return List.of();

        return body.stream()
                .filter(m -> {
                    Object clazzObj = m.get("class");
                    if (clazzObj == null) return false;
                    String clazz = String.valueOf(clazzObj);
                    return !clazz.startsWith("org.apache.kafka.connect.mirror.");
                })
                .map(m -> new ConnectorPluginDto(
                        String.valueOf(m.get("class")),
                        String.valueOf(m.get("type")),
                        m.get("version") == null ? null : String.valueOf(m.get("version"))
                ))
                .collect(Collectors.toList());
    }

    public List<Map<String, Object>> getConnectorPluginConfig(String connectorClass) {
        String url = baseUrl + "/connector-plugins/" + connectorClass + "/config";
        return restTemplate.exchange(
                url,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<Map<String, Object>>>() {}
        ).getBody();
    }
}
