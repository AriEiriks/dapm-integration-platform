package com.dapm.security_service.services;

import com.dapm.security_service.models.dtos.ExternalSourceDto;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class ExternalSourceService {
    private final KafkaConnectClient kafkaConnectClient;

    public ExternalSourceService(KafkaConnectClient kafkaConnectClient) {
        this.kafkaConnectClient = kafkaConnectClient;
    }

    public List<ExternalSourceDto> listExternalSources() {
        List<String> connectorNames = kafkaConnectClient.getConnectorNames();
        List<ExternalSourceDto> result = new ArrayList<>();

        if (connectorNames == null) {
            return result;
        }

        for (String name : connectorNames) {
            Map<String, Object> connectorInfo = kafkaConnectClient.getConnectorInfo(name);
            if (connectorInfo == null) continue;

            // Typical Kafka Connect response: { "name": "...", "config": {...}, "tasks": [...], "type": "source" }
            String type = (String) connectorInfo.get("type");
            if (type == null || !"source".equalsIgnoreCase(type)) {
                // For now, only treat source connectors as external sources
                continue;
            }

            @SuppressWarnings("unchecked")
            Map<String, String> config = (Map<String, String>) connectorInfo.get("config");

            String connectorClass = config != null ? config.getOrDefault("connector.class", "") : "";
            String topics = config != null ? config.getOrDefault("topics", "") : "";

            ExternalSourceDto dto = new ExternalSourceDto(
                    name,
                    type,
                    connectorClass,
                    topics
            );

            result.add(dto);
        }

        return result;
    }
}
