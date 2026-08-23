package com.luislipinski.trucklife.identity.application;

import java.time.Instant;
import java.util.Objects;

public record PendingPasswordResetDelivery(
        String recipient,
        String displayName,
        String rawToken,
        Instant expiresAt
) {
    public PendingPasswordResetDelivery {
        Objects.requireNonNull(recipient, "recipient must not be null");
        Objects.requireNonNull(displayName, "displayName must not be null");
        Objects.requireNonNull(rawToken, "rawToken must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    }

    @Override
    public String toString() {
        return "PendingPasswordResetDelivery[recipient=redacted, displayName=redacted, rawToken=redacted, expiresAt=" + expiresAt + "]";
    }
}
