package com.dapm.security_service.services;

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
        Map<String, String> connectorConfig = new HashMap<>();

        String kcql = null;
        if (request.getConfig() != null) {
            kcql = request.getConfig().get("connect.gcpstorage.kcql");
        }

        if (kcql == null || kcql.isBlank()) {
            throw new IllegalArgumentException("KCQL must be provided as config['connect.gcpstorage.kcql'].");
        }

        connectorConfig.put("connector.class", "io.lenses.streamreactor.connect.gcp.storage.source.GCPStorageSourceConnector");
        connectorConfig.put("tasks.max", "1");

        connectorConfig.put("connect.gcpstorage.kcql", kcql);

        connectorConfig.put("connect.gcpstorage.gcp.auth.mode", "File");
        connectorConfig.put("connect.gcpstorage.gcp.file", "/etc/secrets/dapm-bucketviewer.json");
        connectorConfig.put("connect.gcpstorage.gcp.project.id", "dapm-streams-data");

        connectorConfig.put("connect.gcpstorage.source.extension.includes", "Json");
        connectorConfig.put("connect.gcpstorage.source.partition.search.continuous", "false");

        kafkaConnectClient.createConnector(request.getName(), connectorConfig);
    }
}
