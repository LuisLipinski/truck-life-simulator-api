package com.luislipinski.trucklife.identity.application;

import com.luislipinski.trucklife.identity.domain.UserRole;
import com.luislipinski.trucklife.identity.domain.UserStatus;
import java.time.Instant;
import java.util.UUID;

public record AuthenticatedAccount(
        UUID userId,
        UUID sessionId,
        String email,
        String displayName,
        UserRole role,
        UserStatus status,
        boolean emailVerified,
        Instant createdAt,
        Instant lastLoginAt
) {
}
