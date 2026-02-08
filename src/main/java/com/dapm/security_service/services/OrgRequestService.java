package com.dapm.security_service.services;

import com.dapm.security_service.models.dtos2.PipelineProcessingElementRequestOutboundDto;
import com.dapm.security_service.models.dtos.peer.RequestResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.beans.factory.annotation.Value;

@Service
public class OrgRequestService {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${PEER_ORGA_URL:}")
    private String peerOrgaUrl;   // e.g. http://130.225.70.65:8081

    @Value("${PEER_ORGB_URL:}")
    private String peerOrgbUrl;   // e.g. http://192.168.8.132:8082

    private String getPeerBaseUrl(String orgName) {
        return switch (orgName.toLowerCase()) {
            case "orga" -> peerOrgaUrl;
            case "orgb" -> peerOrgbUrl;
            default -> throw new IllegalArgumentException("Unknown organization: " + orgName);
        };
    }

    public RequestResponse sendRequestToOrg(
            PipelineProcessingElementRequestOutboundDto requestDto,
            String orgName) {

        // Build: http://IP:port/api/peer/request-access
        String targetUrl = getPeerBaseUrl(orgName) + "/api/peer/request-access";

        return restTemplate.postForObject(
                targetUrl,
                requestDto,
                RequestResponse.class
        );
    }
}
