package com.luislipinski.trucklife.identity.application;

import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;

@Component
public class ActionTokenGenerator {

    private static final int TOKEN_BYTES = 32;
    private final SecureRandom secureRandom;

    public ActionTokenGenerator(SecureRandom secureRandom) {
        this.secureRandom = secureRandom;
    }

    public GeneratedActionToken generate() {
        byte[] tokenBytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(tokenBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        return new GeneratedActionToken(rawToken, TokenDigests.sha256(rawToken));
    }

    public static final class GeneratedActionToken {

        private final String rawToken;
        private final String tokenHash;

        GeneratedActionToken(String rawToken, String tokenHash) {
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
            return "GeneratedActionToken[redacted]";
        }
    }
}
