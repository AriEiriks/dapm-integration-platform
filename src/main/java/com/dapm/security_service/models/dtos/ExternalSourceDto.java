package com.dapm.security_service.models.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExternalSourceDto {
    // Kafka Connect connector name
    private String name;

    // Kafka Connect "type": likely "source" (we'll filter for that)
    private String type;

    // e.g. org.apache.kafka.connect.file.FileStreamSourceConnector
    private String connectorClass;

    // topics this connector writes to (comma separated)
    private String topics;
}
