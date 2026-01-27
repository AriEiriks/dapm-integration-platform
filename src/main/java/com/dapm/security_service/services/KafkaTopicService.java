package com.dapm.security_service.services;

import org.apache.kafka.clients.admin.AdminClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class KafkaTopicService {

    private final AdminClient adminClient;

    public KafkaTopicService(AdminClient adminClient) {
        this.adminClient = adminClient;
    }

    public List<String> listTopics(boolean includeInternal) {
        try {
            return adminClient
                    .listTopics()
                    .names()
                    .get(5, TimeUnit.SECONDS)
                    .stream()
                    .filter(name -> includeInternal || !isInternalTopic(name))
                    .sorted()
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException("Failed to list Kafka topics", e);
        }
    }

    private boolean isInternalTopic(String name) {
        // common internal topics; tweak to your environment
        return name.startsWith("__") || name.equals("_schemas");
    }
}
