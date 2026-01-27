package com.dapm.security_service.controllers.ClientApi;

import com.dapm.security_service.services.KafkaTopicService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/kafka")
public class KafkaTopicController {

    private final KafkaTopicService kafkaTopicService;

    public KafkaTopicController(KafkaTopicService kafkaTopicService) {
        this.kafkaTopicService = kafkaTopicService;
    }

    @GetMapping("/topics")
    public ResponseEntity<List<String>> getTopics(
            @RequestParam(name = "includeInternal", defaultValue = "false") boolean includeInternal
    ) {
        return ResponseEntity.ok(kafkaTopicService.listTopics(includeInternal));
    }
}