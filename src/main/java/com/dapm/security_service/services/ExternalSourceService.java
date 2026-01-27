package com.dapm.security_service.services;

import com.dapm.security_service.models.dtos.ConnectorPluginDto;
import com.dapm.security_service.models.dtos.CreateExternalSourceRequest;
import com.dapm.security_service.models.dtos.ExternalSourceDto;
import com.dapm.security_service.models.dtos.ConnectorStatusDto;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class ExternalSourceService {

    private final KafkaConnectClient kafkaConnectClient;

    private static final Pattern KCQL_INSERT_INTO =
            Pattern.compile("(?i)\\bINSERT\\s+INTO\\s+([^\\s]+)");

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
            Map<String, Object> info = kafkaConnectClient.getConnectorInfo(name);
            if (info == null) {
                continue;
            }

            Object typeObj = info.get("type");
            String type = typeObj != null ? typeObj.toString() : "";

            @SuppressWarnings("unchecked")
            Map<String, String> config = (Map<String, String>) info.get("config");

            String connectorClass = config != null ? config.getOrDefault("connector.class", "") : "";
            String publishedTopics = resolvePublishedTopics(connectorClass, config);

            ExternalSourceDto dto = new ExternalSourceDto(
                    name,
                    type,
                    connectorClass,
                    publishedTopics
            );
            result.add(dto);
        }

        return result;
    }

    private String resolvePublishedTopics(String connectorClass, Map<String, String> config) {
        if (config == null) return "";

        // Specific case: GCPStorage
        if (connectorClass != null && connectorClass.toLowerCase().contains("gcpstorage")) {
            String kcql = config.getOrDefault("connect.gcpstorage.kcql",
                    config.getOrDefault("connect.gcpstorage.kcql".toLowerCase(), ""));
            if (kcql == null || kcql.isBlank()) return "";

            // Split by ';' (multiple statements possible)
            List<String> topics = new ArrayList<>();
            for (String stmt : kcql.split(";")) {
                String topic = extractInsertIntoTopic(stmt);
                if (topic != null && !topic.isBlank()) topics.add(topic);
            }

            // return comma-separated
            return String.join(",", topics);
        }

        // Default case: uses "topic"
        String topic = config.get("topic");
        if (topic != null && !topic.isBlank()) return topic;

        return "";
    }

    private String extractInsertIntoTopic(String kcqlStatement) {
        if (kcqlStatement == null) return null;
        Matcher m = KCQL_INSERT_INTO.matcher(kcqlStatement.trim());
        if (!m.find()) return null;

        String raw = m.group(1).trim();

        if ((raw.startsWith("`") && raw.endsWith("`")) || (raw.startsWith("\"") && raw.endsWith("\""))) {
            raw = raw.substring(1, raw.length() - 1);
        }

        return raw;
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

    public List<Map<String, Object>> getConnectorPluginConfigDefs(String connectorClass) {
        return kafkaConnectClient.getConnectorPluginConfig(connectorClass);
    }

    public Map<String, String> getExternalSourceConfig(String connectorName) {
        return kafkaConnectClient.getConnectorConfig(connectorName);
    }

    public Map<String, String> updateExternalSourceConfig(
            String connectorName,
            Map<String, String> config
    ) {
        return kafkaConnectClient.updateConnectorConfig(connectorName, config);
    }

    // pause/resume
    public void pauseConnector(String connectorName) {
        kafkaConnectClient.pauseConnector(connectorName);
    }

    public void resumeConnector(String connectorName) {
        kafkaConnectClient.resumeConnector(connectorName);
    }

    public ConnectorStatusDto getConnectorStatus(String connectorName) {
        Map<String, Object> raw = kafkaConnectClient.getConnectorStatus(connectorName);

        String state = "UNKNOWN";

        if (raw != null) {
            boolean anyTaskFailed = false;

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> tasks = (List<Map<String, Object>>) raw.get("tasks");
            if (tasks != null) {
                for (Map<String, Object> t : tasks) {
                    Object s = t.get("state");
                    if (s != null && "FAILED".equalsIgnoreCase(String.valueOf(s))) {
                        anyTaskFailed = true;
                        break;
                    }
                }
            }

            if (anyTaskFailed) {
                state = "FAILED";
            } else {
                @SuppressWarnings("unchecked")
                Map<String, Object> connector = (Map<String, Object>) raw.get("connector");
                Object connectorState = connector != null ? connector.get("state") : null;
                if (connectorState != null) {
                    state = String.valueOf(connectorState);
                }
            }
        }

        state = normalizeState(state);

        return new ConnectorStatusDto(connectorName, state);
    }

    private String normalizeState(String state) {
        if (state == null) return "UNKNOWN";
        String s = state.toUpperCase();

        if (s.equals("RUNNING")) return "RUNNING";
        if (s.equals("PAUSED")) return "PAUSED";
        if (s.equals("FAILED")) return "FAILED";

        return "UNKNOWN";
    }

}
