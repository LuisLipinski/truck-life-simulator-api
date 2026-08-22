package com.luislipinski.trucklife.identity.email;

import java.time.Instant;

public interface VerificationEmailDeliveryPort {

    void sendVerificationEmail(
            String recipient,
            String displayName,
            String rawToken,
            Instant expiresAt
    );
}
