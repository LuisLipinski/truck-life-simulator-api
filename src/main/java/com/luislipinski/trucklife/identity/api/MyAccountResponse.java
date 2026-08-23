package com.luislipinski.trucklife.identity.api;

import com.luislipinski.trucklife.identity.domain.UserRole;
import com.luislipinski.trucklife.identity.domain.UserStatus;
import java.time.Instant;
import java.util.UUID;

public record MyAccountResponse(
        UUID id,
        String email,
        String displayName,
        UserRole role,
        UserStatus status,
        boolean emailVerified,
        Instant createdAt,
        Instant lastLoginAt
) {
}
