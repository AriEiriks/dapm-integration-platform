package com.dapm.security_service.controllers.Client2Api;

import com.dapm.security_service.controllers.PeerApi.PeerHandshakeController;
import com.dapm.security_service.models.PublisherOrganization;
import com.dapm.security_service.models.enums.SubscriptionTier;
import com.dapm.security_service.repositories.PublisherOrganizationRepository;
import com.dapm.security_service.security.CustomUserDetails;
import com.dapm.security_service.services.TokenService;
import com.dapm.security_service.services.TokenVerificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/handshake")
public class HandshakeClientController {

    private final RestTemplate restTemplate = new RestTemplate();

    @Autowired
    private TokenService tokenService;

    @Autowired
    private TokenVerificationService verificationService;

    @Autowired
    private PublisherOrganizationRepository publisherOrganizationRepository;

    @Value("${PEER_ORGA_URL:}")
    private String peerOrgaUrl;   // http://130.225.70.65:8081

    @Value("${PEER_ORGB_URL:}")
    private String peerOrgbUrl;   // http://192.168.8.132:8082

    private String getPeerBaseUrl(String orgName) {
        return switch (orgName.toLowerCase()) {
            case "orga" -> peerOrgaUrl;
            case "orgb" -> peerOrgbUrl;
            default -> throw new IllegalArgumentException("Unknown organization: " + orgName);
        };
    }

    @PostMapping("")
    @PreAuthorize("hasAuthority('CREATE_PROJECT')")
    public ResponseEntity<Map<String, Object>> createHandshake(
            @RequestBody HandshakeResult.OrgNameRequest orgName,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        Map<String, Object> body = new HashMap<>();

        if (orgName == null || orgName.orgName().isBlank()) {
            body.put("success", false);
            body.put("message", "Organization name is required.");
            return ResponseEntity.badRequest().body(body);
        }

        try {
            HandshakeResult result = sendHandshake(orgName.orgName().trim());

            if (result.success) {
                body.put("success", true);
                body.put("message", "Partnership established.");
                body.put("requestedOrg", orgName.orgName().trim());
                body.put("verifiedOrg", result.verifiedOrg);
                return ResponseEntity.ok(body);
            } else {
                body.put("success", false);
                body.put("requestedOrg", orgName.orgName().trim());
                body.put("error", result.errorMessage);
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(body);
            }

        } catch (Exception ex) {
            body.put("success", false);
            body.put("message", "Unexpected error during handshake.");
            body.put("error", ex.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
        }
    }

    private synchronized HandshakeResult sendHandshake(String orgName) {
        try {
            // 1) Create handshake JWT
            String token = tokenService.generateHandshakeToken(300);

            PeerHandshakeController.HandshakeRequest request =
                    new PeerHandshakeController.HandshakeRequest();
            request.setToken(token);

            // 2) Build correct handshake URL
            String url = getPeerBaseUrl(orgName) + "/api/peer/handshake";

            ResponseEntity<PeerHandshakeController.HandshakeResponse> response =
                    restTemplate.postForEntity(url, request, PeerHandshakeController.HandshakeResponse.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {

                Integer maxHours = response.getBody().getMaxHours();

                publisherOrganizationRepository.findByName(orgName)
                        .orElseGet(() ->
                                publisherOrganizationRepository.save(
                                        new PublisherOrganization(UUID.randomUUID(), orgName, SubscriptionTier.FREE, maxHours)
                                ));

                return HandshakeResult.ok(orgName + " (maxHours=" + maxHours + ")");
            }

            return HandshakeResult.fail("Peer responded with status: " + response.getStatusCode());

        } catch (RestClientException e) {
            return HandshakeResult.fail("HTTP error calling peer: " + e.getMessage());
        } catch (Exception ex) {
            return HandshakeResult.fail("Handshake failed: " + ex.getMessage());
        }
    }

    private static class HandshakeResult {
        final boolean success;
        final String verifiedOrg;
        final String errorMessage;

        private HandshakeResult(boolean success, String verifiedOrg, String errorMessage) {
            this.success = success;
            this.verifiedOrg = verifiedOrg;
            this.errorMessage = errorMessage;
        }

        static HandshakeResult ok(String verifiedOrg) {
            return new HandshakeResult(true, verifiedOrg, null);
        }

        static HandshakeResult fail(String errorMessage) {
            return new HandshakeResult(false, null, errorMessage);
        }

        public record OrgNameRequest(String orgName) {}
    }
}
