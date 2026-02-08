package com.dapm.security_service.controllers.Client2Api;

import com.dapm.security_service.models.PublisherOrganization;
import com.dapm.security_service.models.dtos2.GetPeerRequest;
import com.dapm.security_service.models.enums.PeType;
import com.dapm.security_service.models.enums.Tier;
import com.dapm.security_service.repositories.ProcessingElementRepository;
import com.dapm.security_service.repositories.PublisherOrganizationRepository;
import com.dapm.security_service.repositories.TiersRepository;
import com.dapm.security_service.services.TokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.nio.file.*;
import java.util.*;

@RestController
@RequestMapping("/api/external-peer-configs")
public class RequestPEConfigFromPeersController {

    @Value("${runtime.configs.root:/runtime-configs}")
    private String rootDir;

    @Value("${PEER_ORGA_URL:}")
    private String peerOrgaUrl;   // e.g. http://130.225.70.65:8081

    @Value("${PEER_ORGB_URL:}")
    private String peerOrgbUrl;   // e.g. http://192.168.8.132:8082

    @Value("${peer.sync.path:/peer/availablePeConfigs}")
    private String syncPath;

    @Autowired private ProcessingElementRepository processingElementRepository;
    @Autowired private PublisherOrganizationRepository publisherOrganizationRepository;
    @Autowired private TiersRepository tiersRepository;
    @Autowired private TokenService tokenService;

    private final RestTemplate restTemplate = new RestTemplate();

    // ✅ Builds ONLY sync URL (NOT execution URL)
    private String buildPeerSyncUrl(String orgName) {
        String base = switch (orgName.toLowerCase()) {
            case "orga" -> peerOrgaUrl;
            case "orgb" -> peerOrgbUrl;
            default -> throw new IllegalArgumentException("Unknown organization: " + orgName);
        };

        return base + syncPath;  // e.g. http://130.225.70.65:8081/peer/availablePeConfigs
    }

    // ✅ Returns pure runtime base URL
    private String getRuntimeHost(String orgName) {
        return switch (orgName.toLowerCase()) {
            case "orga" -> peerOrgaUrl;   // http://130.225.70.65:8081
            case "orgb" -> peerOrgbUrl;   // http://192.168.8.132:8082
            default -> throw new IllegalArgumentException("Unknown organization: " + orgName);
        };
    }

    @PostMapping("/sync-peer-configs")
    public ResponseEntity<?> syncFromPeer(@RequestBody SyncRequest req) throws Exception {

        // ✅ Sync URL (NOT runtime)
        String syncUrl = buildPeerSyncUrl(req.orgName());

        String jwtA = tokenService.generateHandshakeToken(300);
        GetPeerRequest body = new GetPeerRequest();
        body.setToken(jwtA);

        // ✅ Fetch pe configs from peer
        PeerConfigsResponse response = restTemplate.postForObject(syncUrl, body, PeerConfigsResponse.class);

        if (response == null || response.items == null || response.items.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(new SyncResult(0, List.of(), "Peer returned no configs"));
        }

        Path root = Path.of(rootDir).toAbsolutePath().normalize();
        Files.createDirectories(root);

        List<String> savedFiles = new ArrayList<>();

        for (PeerConfigItem item : response.items) {

            if (!item.found || item.schema == null) continue;

            // ✅ Save schema file
            Path dest = root.resolve(item.fileName).normalize();
            if (!dest.startsWith(root)) continue;

            Files.createDirectories(dest.getParent());
            Files.writeString(dest, item.schema, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            savedFiles.add(item.fileName);

            PublisherOrganization org = publisherOrganizationRepository.findByName(req.orgName())
                    .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + req.orgName()));

            // ✅ This is the correct runtime host for pipeline execution
            String runtimeHost = getRuntimeHost(req.orgName());

            // ✅ Save PE metadata
            com.dapm.security_service.models.ProcessingElement pe =
                    com.dapm.security_service.models.ProcessingElement.builder()
                            .id(UUID.randomUUID())
                            .ownerPartnerOrganization(org)
                            .templateId(item.templateId)
                            .tier(item.tier)
                            .inputs(item.inputs)
                            .output(item.output)
                            .instanceNumber(0)
                            .processingElementType(item.processingElementType)
                            .configSchema(item.schema)
                            .hostURL(runtimeHost)   // ✅ Correct host URL (NO sync path)
                            .build();

            processingElementRepository.save(pe);
        }

        return ResponseEntity.ok(new SyncResult(savedFiles.size(), savedFiles, "ok"));
    }

    // === DTOs ===
    public record SyncRequest(String orgName) {}

    public record SyncResult(int savedCount, List<String> savedFiles, String status) {}

    public static class PeerConfigsResponse {
        public String organization;
        public int count;
        public List<PeerConfigItem> items;
    }

    public static class PeerConfigItem {
        public String peId;
        public String templateId;
        public String fileName;
        public boolean found;
        public String schema;
        public Tier tier;
        public Set<String> inputs;
        public String output;
        public PeType processingElementType;
    }
}
