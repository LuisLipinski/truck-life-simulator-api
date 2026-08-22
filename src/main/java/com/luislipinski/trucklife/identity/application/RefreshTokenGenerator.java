package com.luislipinski.trucklife.identity.application;

import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;

@Component
public class RefreshTokenGenerator {

    private static final int TOKEN_BYTES = 32;
    private final SecureRandom secureRandom;

    public RefreshTokenGenerator(SecureRandom secureRandom) {
        this.secureRandom = secureRandom;
    }

    public GeneratedRefreshToken generate() {
        byte[] tokenBytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(tokenBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        return new GeneratedRefreshToken(rawToken, TokenDigests.sha256(rawToken));
    }

    public static final class GeneratedRefreshToken {

        private final String rawToken;
        private final String tokenHash;

        GeneratedRefreshToken(String rawToken, String tokenHash) {
            this.rawToken = rawToken;
            this.tokenHash = tokenHash;
        }

        public String rawToken() {
            return rawToken;
        }

        public String tokenHash() {
            return tokenHash;
        }

        @Override
        public String toString() {
            return "GeneratedRefreshToken[redacted]";
        }
    }
}
