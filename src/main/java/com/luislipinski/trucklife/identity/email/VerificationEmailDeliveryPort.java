package com.luislipinski.trucklife.identity.email;

import java.time.Instant;

public interface VerificationEmailDeliveryPort {
    void sendVerificationEmail(String recipient, String displayName, String rawToken, Instant expiresAt);

    default void sendPasswordResetEmail(
            String recipient,
            String displayName,
            String rawToken,
            Instant expiresAt
    ) {
        // Backward-compatible extension of the delivery port. Implementations that
        // support password recovery can override this method without exposing tokens.
    }
}
