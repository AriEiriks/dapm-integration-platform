package com.dapm.security_service.models.models2;

import candidate_validation.ValidatedPipeline;
import lombok.Data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class ValidatedPipelineConfig {
    private String projectName;
    private String pipelineName;
    private ValidatedPipeline validatedPipeline;
    private String pipelineJson;
    private List<String> externalPEs;
    private final Map<String, String> externalPEsTokens = new HashMap<>();

}
