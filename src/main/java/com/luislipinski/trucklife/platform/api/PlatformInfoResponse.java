package com.luislipinski.trucklife.platform.api;

import java.time.Instant;

public record PlatformInfoResponse(
        String service,
        String status,
        String apiVersion,
        int moduleCount,
        Instant generatedAt
) {
}
