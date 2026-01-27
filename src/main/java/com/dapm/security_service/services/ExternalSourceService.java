package com.dapm.security_service.services;

import com.dapm.security_service.models.Pipeline;
import com.dapm.security_service.models.dtos.ConnectorPluginDto;
import com.dapm.security_service.models.dtos.ConnectorStatusDto;
import com.dapm.security_service.models.dtos.CreateExternalSourceRequest;
import com.dapm.security_service.models.dtos.ExternalSourceDto;
import com.dapm.security_service.models.enums.PipelinePhase;
import com.dapm.security_service.models.models2.ValidatedPipelineConfig;
import com.dapm.security_service.repositories.PipelineRepositoryy;
import com.dapm.security_service.repositories.ValidatePipelineRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ExternalSourceService {

    private final KafkaConnectClient kafkaConnectClient;

    private final PipelineRepositoryy pipelineRepository;
    private final ValidatePipelineRepository validatePipelineRepository;
    private final ObjectMapper objectMapper;

    private static final Pattern KCQL_INSERT_INTO =
            Pattern.compile("(?i)\\bINSERT\\s+INTO\\s+([^\\s]+)");

    public ExternalSourceService(
            KafkaConnectClient kafkaConnectClient,
            PipelineRepositoryy pipelineRepository,
            ValidatePipelineRepository validatePipelineRepository,
            ObjectMapper objectMapper
    ) {
        this.kafkaConnectClient = kafkaConnectClient;
        this.pipelineRepository = pipelineRepository;
        this.validatePipelineRepository = validatePipelineRepository;
        this.objectMapper = objectMapper;
    }

    public List<ExternalSourceDto> listExternalSources() {
        List<String> connectorNames = kafkaConnectClient.getConnectorNames();
        List<ExternalSourceDto> result = new ArrayList<>();

        // ✅ compute once
        Map<String, Set<String>> executingPipelineTopics = getExecutingPipelineTopics();

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

            // which executing pipelines reference these topics
            List<String> usedByPipelines = computeUsedByPipelines(publishedTopics, executingPipelineTopics);

            ExternalSourceDto dto = new ExternalSourceDto(
                    name,
                    type,
                    connectorClass,
                    publishedTopics,
                    usedByPipelines
            );
            result.add(dto);
        }

        return result;
    }

    private Map<String, Set<String>> getExecutingPipelineTopics() {
        List<Pipeline> pipelines = pipelineRepository.findAll();
        if (pipelines == null || pipelines.isEmpty()) return Collections.emptyMap();

        Map<String, Set<String>> out = new HashMap<>();

        for (Pipeline p : pipelines) {
            if (p == null) continue;
            if (p.getPipelinePhase() != PipelinePhase.EXECUTING) continue;

            String pipelineName = p.getName();
            if (pipelineName == null || pipelineName.isBlank()) continue;

            ValidatedPipelineConfig cfg = validatePipelineRepository.getPipeline(pipelineName);
            if (cfg == null) {
                out.put(pipelineName, Collections.emptySet());
                continue;
            }

            String json = cfg.getPipelineJson();
            if (json == null || json.isBlank()) {
                out.put(pipelineName, Collections.emptySet());
                continue;
            }

            out.put(pipelineName, extractTopicsFromJson(json));
        }

        return out;
    }

    private Set<String> extractTopicsFromJson(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            Set<String> topics = new HashSet<>();
            collectTopicValues(root, topics);
            return topics;
        } catch (Exception e) {
            return Collections.emptySet();
        }
    }

    private void collectTopicValues(JsonNode node, Set<String> out) {
        if (node == null || node.isNull()) return;

        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                String key = entry.getKey();
                JsonNode val = entry.getValue();

                if (isTopicKey(key)) addTopicValue(val, out);
                collectTopicValues(val, out); // recurse
            });
            return;
        }

        if (node.isArray()) {
            for (JsonNode child : node) collectTopicValues(child, out);
        }
    }

    private boolean isTopicKey(String key) {
        if (key == null) return false;
        String k = key.trim().toLowerCase();
        return k.equals("topic")
                || k.equals("topics")
                || k.endsWith("topic")
                || (k.contains("kafka") && k.contains("topic"));
    }

    private void addTopicValue(JsonNode val, Set<String> out) {
        if (val == null || val.isNull()) return;

        if (val.isTextual()) {
            splitAndAdd(val.asText(), out);
            return;
        }

        if (val.isArray()) {
            for (JsonNode c : val) {
                if (c != null && c.isTextual()) splitAndAdd(c.asText(), out);
            }
        }
    }

    private void splitAndAdd(String raw, Set<String> out) {
        if (raw == null) return;
        String trimmed = raw.trim();
        if (trimmed.isBlank()) return;

        for (String part : trimmed.split(",")) {
            String t = part.trim();
            if (!t.isBlank()) out.add(t);
        }
    }

    private List<String> computeUsedByPipelines(
            String connectorTopicsCsv,
            Map<String, Set<String>> executingPipelineTopics
    ) {
        if (connectorTopicsCsv == null || connectorTopicsCsv.isBlank()) return Collections.emptyList();
        if (executingPipelineTopics == null || executingPipelineTopics.isEmpty()) return Collections.emptyList();

        Set<String> connectorTopics = new HashSet<>();
        splitAndAdd(connectorTopicsCsv, connectorTopics);
        if (connectorTopics.isEmpty()) return Collections.emptyList();

        List<String> usedBy = new ArrayList<>();

        for (Map.Entry<String, Set<String>> e : executingPipelineTopics.entrySet()) {
            Set<String> pipelineTopics = e.getValue();
            if (pipelineTopics == null || pipelineTopics.isEmpty()) continue;

            for (String topic : connectorTopics) {
                if (pipelineTopics.contains(topic)) {
                    usedBy.add(e.getKey()); // pipeline name
                    break;
                }
            }
        }

        return usedBy;
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
