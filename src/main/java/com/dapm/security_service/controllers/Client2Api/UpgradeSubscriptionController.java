package com.dapm.security_service.controllers.Client2Api;

import com.dapm.security_service.models.PublisherOrganization;
import com.dapm.security_service.models.dtos2.SubscriptionRequestDto;
import com.dapm.security_service.models.enums.SubscriptionTier;
import com.dapm.security_service.repositories.PublisherOrganizationRepository;
import com.dapm.security_service.services.TokenService;
import com.dapm.security_service.services.TokenVerificationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;

@RestController
@RequestMapping("/api/upgrade-subscription")
public class UpgradeSubscriptionController {

    @Autowired private TokenService tokenService;
    @Autowired private TokenVerificationService verificationService;
    @Autowired private PublisherOrganizationRepository publisherRepository;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ✅ Use the same env variables as all other peer calls
    @Value("${PEER_ORGA_URL:}")
    private String peerOrgaUrl;

    @Value("${PEER_ORGB_URL:}")
    private String peerOrgbUrl;

    @Value("${peer.subscription.path:/api/peer/upgrade-subscription}")
    private String subscriptionPath;

    // ✅ Build correct URL based on orgName
    private String buildPeerUrl(String orgName) {
        String base = switch (orgName.toLowerCase()) {
            case "orga" -> peerOrgaUrl;
            case "orgb" -> peerOrgbUrl;
            default -> throw new IllegalArgumentException("Unknown organization: " + orgName);
        };
        return base + subscriptionPath;
    }

    @PostMapping
    public ResponseEntity<PeerUpgradeResponse> requestSubscriptionUpgrade(@RequestBody SubscriptionRequestDto dto) {
        try {
            // 1) Create handshake token
            String token = tokenService.generateHandshakeToken(300);
            dto.setToken(token);

            // 2) Build correct peer URL
            String url = buildPeerUrl(dto.getOrgName());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<SubscriptionRequestDto> entity = new HttpEntity<>(dto, headers);

            // 3) Call peer
            ResponseEntity<PeerUpgradeResponse> response =
                    restTemplate.postForEntity(url, entity, PeerUpgradeResponse.class);

            // 4) Update DB on success
            if (response.getStatusCode().is2xxSuccessful()
                    && response.getBody() != null
                    && response.getBody().success()) {

                System.out.println("Subscription upgrade successful: " +
                        response.getBody().message() +
                        " new tier=" + response.getBody().tier() +
                        " at " + Instant.now());

                PublisherOrganization publisherOrganization = publisherRepository
                        .findByName(dto.getOrgName())
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Partner Organization not found or handshake not completed."
                        ));

                publisherOrganization.setTier(response.getBody().tier());
                publisherOrganization.setMaxHours(response.getBody().maxHours());
                publisherRepository.save(publisherOrganization);
            }

            return ResponseEntity.status(response.getStatusCode()).body(response.getBody());

        } catch (HttpStatusCodeException ex) {
            // Handle peer error
            String body = ex.getResponseBodyAsString();
            try {
                PeerUpgradeResponse parsed = objectMapper.readValue(body, PeerUpgradeResponse.class);
                return ResponseEntity.status(ex.getStatusCode()).body(parsed);
            } catch (Exception ignored) {
                return ResponseEntity.status(ex.getStatusCode())
                        .body(new PeerUpgradeResponse(false, extractMessageField(body), null, null));
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new PeerUpgradeResponse(false, "Unexpected error while upgrading subscription", null, null));
        }
    }

    private String extractMessageField(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            JsonNode messageNode = node.get("message");
            if (messageNode != null && messageNode.isTextual()) {
                return messageNode.asText();
            }
        } catch (Exception ignored) {}
        return "Request failed";
    }

    static record PeerUpgradeResponse(
            boolean success,
            String message,
            SubscriptionTier tier,
            Integer maxHours
    ) {}
}
