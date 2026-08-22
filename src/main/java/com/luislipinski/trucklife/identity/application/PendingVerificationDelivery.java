package com.luislipinski.trucklife.identity.application;

import java.time.Instant;

final class PendingVerificationDelivery {

    private final String recipient;
    private final String displayName;
    private final String rawToken;
    private final Instant expiresAt;

    PendingVerificationDelivery(
            String recipient,
            String displayName,
            String rawToken,
            Instant expiresAt
    ) {
        this.recipient = recipient;
        this.displayName = displayName;
        this.rawToken = rawToken;
        this.expiresAt = expiresAt;
    }

    String recipient() {
        return recipient;
    }

    String displayName() {
        return displayName;
    }

    String rawToken() {
        return rawToken;
    }

    Instant expiresAt() {
        return expiresAt;
    }

    @Override
    public String toString() {
        return "PendingVerificationDelivery[redacted]";
    }
}
