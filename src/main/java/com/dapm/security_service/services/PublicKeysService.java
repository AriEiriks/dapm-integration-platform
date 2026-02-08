package com.dapm.security_service.services;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PublicKeysService {

    private final RestTemplate restTemplate = new RestTemplate();

    // cache: key is orgId#kid → PublicKey
    private final Map<String, PublicKey> cache = new ConcurrentHashMap<>();

    /**
     * Extracts the key for a given org + kid.
     * Caches once fetched from JWKS.
     */
    public PublicKey getKeyForOrg(String orgId, String kid) {
        String cacheKey = orgId + "#" + kid;
        return cache.computeIfAbsent(cacheKey, k -> fetchKey(orgId, kid));
    }

    private PublicKey fetchKey(String orgId, String kid) {
        try {
            String url = buildJwksUrl(orgId);
            JwksResponse response = restTemplate.getForObject(url, JwksResponse.class);

            if (response == null || response.keys == null || response.keys.isEmpty()) {
                throw new IllegalStateException("No keys found in JWKS for org " + orgId);
            }

            return response.keys.stream()
                    .filter(jwk -> kid.equals(jwk.kid))
                    .findFirst()
                    .map(this::toPublicKey)
                    .orElseThrow(() -> new IllegalStateException("No matching kid " + kid + " in JWKS for " + orgId));
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch JWKS for " + orgId, e);
        }
    }

    @Value("${PEER_ORGA_URL:}")
    private String peerOrgaUrl;

    @Value("${PEER_ORGB_URL:}")
    private String peerOrgbUrl;

    private String buildJwksUrl(String orgId) {
        String base = switch (orgId.toLowerCase()) {
            case "orga" -> peerOrgaUrl;   // e.g. http://130.225.70.65:8081
            case "orgb" -> peerOrgbUrl;   // e.g. http://192.168.8.132:8082
            default -> throw new IllegalArgumentException("Unknown orgId: " + orgId);
        };

        return base + "/.well-known/jwks.json";
    }


    private PublicKey toPublicKey(Jwk jwk) {
        try {
            BigInteger modulus = new BigInteger(1, Base64.getUrlDecoder().decode(jwk.n));
            BigInteger exponent = new BigInteger(1, Base64.getUrlDecoder().decode(jwk.e));
            RSAPublicKeySpec spec = new RSAPublicKeySpec(modulus, exponent);
            return KeyFactory.getInstance("RSA").generatePublic(spec);
        } catch (Exception ex) {
            throw new RuntimeException("Failed to convert JWK to RSA PublicKey", ex);
        }
    }

    /**
     * Helper class for JWKS JSON mapping
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class JwksResponse {
        @JsonProperty("keys")
        public List<Jwk> keys;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class Jwk {
        public String kty;
        public String alg;
        public String use;
        public String kid;
        public String n; // modulus
        public String e; // exponent
    }

    // --- Optional helper ---
    // Decode JWT header (just Base64) to read kid without verifying
    public static String extractKidFromToken(String token) {
        String[] parts = token.split("\\.");
        if (parts.length < 2) throw new IllegalArgumentException("Invalid JWT");
        String headerJson = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
        return headerJson.replaceAll(".*\"kid\"\\s*:\\s*\"([^\"]+)\".*", "$1");
    }

    // Decode JWT payload to read iss
    public static String extractIssuerFromToken(String token) {
        String[] parts = token.split("\\.");
        if (parts.length < 2) throw new IllegalArgumentException("Invalid JWT");
        String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        return payloadJson.replaceAll(".*\"iss\"\\s*:\\s*\"([^\"]+)\".*", "$1");
    }
}
