package com.luislipinski.trucklife.identity.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luislipinski.trucklife.identity.config.IdentitySessionProperties;
import com.luislipinski.trucklife.identity.domain.UserRole;
import com.luislipinski.trucklife.identity.persistence.UserEntity;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class JwtAccessTokenIssuer {

    private static final int MAXIMUM_TOKEN_LENGTH = 8_192;
    private static final long CLOCK_SKEW_SECONDS = 30;
    private static final Base64.Encoder BASE64_URL_ENCODER = Base64.getUrlEncoder()
            .withoutPadding();
    private static final Base64.Decoder BASE64_URL_DECODER = Base64.getUrlDecoder();
    private static final TypeReference<Map<String, Object>> JSON_OBJECT = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;
    private final IdentitySessionProperties properties;
    private final Clock clock;
    private final SecretKeySpec signingKey;

    public JwtAccessTokenIssuer(
            ObjectMapper objectMapper,
            IdentitySessionProperties properties,
            Clock clock
    ) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.clock = clock;
        this.signingKey = new SecretKeySpec(
                Base64.getDecoder().decode(properties.jwtSecretBase64()),
                "HmacSHA256"
        );
    }

    public IssuedAccessToken issue(UserEntity user, UUID sessionId) {
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(properties.accessTokenTtl());
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", user.getId().toString());
        claims.put("sid", sessionId.toString());
        claims.put("role", user.getRole().name());
        claims.put("email_verified", user.isEmailVerified());
        claims.put("iss", properties.issuer());
        claims.put("aud", List.of(properties.audience()));
        claims.put("iat", issuedAt.getEpochSecond());
        claims.put("exp", expiresAt.getEpochSecond());
        claims.put("jti", UUID.randomUUID().toString());

        String encodedHeader = encodeJson(Map.of("alg", "HS256", "typ", "JWT"));
        String encodedClaims = encodeJson(claims);
        String signingInput = encodedHeader + '.' + encodedClaims;
        String signature = BASE64_URL_ENCODER.encodeToString(sign(signingInput));
        String token = signingInput + '.' + signature;
        return new IssuedAccessToken(token, expiresAt);
    }

    public DecodedAccessToken decodeAndValidate(String token) {
        if (token == null || token.isBlank() || token.length() > MAXIMUM_TOKEN_LENGTH) {
            throw invalidToken();
        }
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3) {
            throw invalidToken();
        }

        Map<String, Object> header = decodeJson(parts[0]);
        if (!"HS256".equals(header.get("alg")) || !"JWT".equals(header.get("typ"))) {
            throw invalidToken();
        }
        byte[] receivedSignature = decodeBase64(parts[2]);
        byte[] expectedSignature = sign(parts[0] + '.' + parts[1]);
        if (!MessageDigest.isEqual(expectedSignature, receivedSignature)) {
            throw invalidToken();
        }

        Map<String, Object> claims = decodeJson(parts[1]);
        String subject = stringClaim(claims, "sub");
        String sessionId = stringClaim(claims, "sid");
        String role = stringClaim(claims, "role");
        String issuer = stringClaim(claims, "iss");
        String tokenId = stringClaim(claims, "jti");
        long issuedAtSeconds = longClaim(claims, "iat");
        long expiresAtSeconds = longClaim(claims, "exp");
        Object emailVerified = claims.get("email_verified");
        Object audiences = claims.get("aud");

        if (!properties.issuer().equals(issuer)
                || !(audiences instanceof List<?> audienceList)
                || !audienceList.contains(properties.audience())
                || !(emailVerified instanceof Boolean verified)
                || tokenId.isBlank()) {
            throw invalidToken();
        }

        Instant issuedAt = Instant.ofEpochSecond(issuedAtSeconds);
        Instant expiresAt = Instant.ofEpochSecond(expiresAtSeconds);
        Instant now = clock.instant();
        if (!expiresAt.isAfter(issuedAt)
                || issuedAt.isAfter(now.plusSeconds(CLOCK_SKEW_SECONDS))
                || !now.isBefore(expiresAt.plusSeconds(CLOCK_SKEW_SECONDS))) {
            throw invalidToken();
        }

        try {
            return new DecodedAccessToken(
                    UUID.fromString(subject),
                    UUID.fromString(sessionId),
                    UserRole.valueOf(role),
                    verified,
                    issuer,
                    properties.audience(),
                    issuedAt,
                    expiresAt,
                    UUID.fromString(tokenId)
            );
        } catch (IllegalArgumentException exception) {
            throw invalidToken();
        }
    }

    private String encodeJson(Map<String, Object> value) {
        try {
            return BASE64_URL_ENCODER.encodeToString(objectMapper.writeValueAsBytes(value));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("JWT claims could not be serialized", exception);
        }
    }

    private Map<String, Object> decodeJson(String encoded) {
        try {
            return objectMapper.readValue(decodeBase64(encoded), JSON_OBJECT);
        } catch (IOException exception) {
            throw invalidToken();
        }
    }

    private byte[] decodeBase64(String value) {
        try {
            return BASE64_URL_DECODER.decode(value);
        } catch (IllegalArgumentException exception) {
            throw invalidToken();
        }
    }

    private byte[] sign(String signingInput) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(signingKey);
            return mac.doFinal(signingInput.getBytes(StandardCharsets.US_ASCII));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HmacSHA256 is unavailable", exception);
        }
    }

    private String stringClaim(Map<String, Object> claims, String name) {
        Object value = claims.get(name);
        if (value instanceof String text && !text.isBlank()) {
            return text;
        }
        throw invalidToken();
    }

    private long longClaim(Map<String, Object> claims, String name) {
        Object value = claims.get(name);
        if (value instanceof Number number) {
            return number.longValue();
        }
        throw invalidToken();
    }

    private InvalidAccessTokenException invalidToken() {
        return new InvalidAccessTokenException();
    }

    public record IssuedAccessToken(String token, Instant expiresAt) {

        @Override
        public String toString() {
            return "IssuedAccessToken[token=redacted, expiresAt=" + expiresAt + ']';
        }
    }

    public record DecodedAccessToken(
            UUID userId,
            UUID sessionId,
            UserRole role,
            boolean emailVerified,
            String issuer,
            String audience,
            Instant issuedAt,
            Instant expiresAt,
            UUID tokenId
    ) {
    }

    public static final class InvalidAccessTokenException extends RuntimeException {

        private InvalidAccessTokenException() {
            super("Access token is invalid");
        }
    }
}
