package com.dapm.security_service.models.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExternalSourceDto {
    private String name;
    private String type;
    private String connectorClass;
    private String topics;
    private List<String> usedByPipelines;
}
