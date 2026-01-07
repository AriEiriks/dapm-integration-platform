package com.dapm.security_service.services;

import com.dapm.security_service.models.dtos.ConnectorPluginDto;
import com.dapm.security_service.models.dtos.CreateExternalSourceRequest;
import com.dapm.security_service.models.dtos.ExternalSourceDto;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@Service
public class ExternalSourceService {

    private final KafkaConnectClient kafkaConnectClient;

    public ExternalSourceService(KafkaConnectClient kafkaConnectClient) {
        this.kafkaConnectClient = kafkaConnectClient;
    }

    /**
     * List external source connectors by querying Kafka Connect.
     * Currently this returns all connectors of type "source"
     * mapping needs work
     */
    public List<ExternalSourceDto> listExternalSources() {
        List<String> connectorNames = kafkaConnectClient.getConnectorNames();
        List<ExternalSourceDto> result = new ArrayList<>();

        if (connectorNames == null) {
            return result;
        }

        for (String name : connectorNames) {
            Map<String, Object> info = kafkaConnectClient.getConnectorInfo(name);
            if (info == null) {
                continue;
            }

            Object typeObj = info.get("type");
            String type = typeObj != null ? typeObj.toString() : "";

            if (!"source".equalsIgnoreCase(type)) {
                continue;
            }

            @SuppressWarnings("unchecked")
            Map<String, String> config = (Map<String, String>) info.get("config");

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

    public void createExternalSource(CreateExternalSourceRequest request) {
        kafkaConnectClient.createConnector(request.getName(), request.getConfig());
    }

    public void deleteConnector(String connectorName) {
        kafkaConnectClient.deleteConnector(connectorName);
    }

    public List<ConnectorPluginDto> getConnectorPlugins() {
        return kafkaConnectClient.getConnectorPlugins();
    }

    // Return ALL config definitions (required + optional).
    public List<Map<String, Object>> getConnectorPluginConfigDefs(String connectorClass) {
        return kafkaConnectClient.getConnectorPluginConfig(connectorClass);
    }
}
