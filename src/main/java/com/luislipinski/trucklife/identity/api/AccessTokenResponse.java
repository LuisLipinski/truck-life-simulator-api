package com.luislipinski.trucklife.identity.api;

import io.swagger.v3.oas.annotations.media.Schema;

public record AccessTokenResponse(
        @Schema(description = "Signed short-lived JWT", accessMode = Schema.AccessMode.READ_ONLY)
        String accessToken,

        @Schema(example = "Bearer")
        String tokenType,

        @Schema(description = "Access token lifetime in seconds", example = "600")
        long expiresIn
) {

    @Override
    public String toString() {
        return "AccessTokenResponse[accessToken=redacted, tokenType="
                + tokenType
                + ", expiresIn="
                + expiresIn
                + ']';
    }
}
