package com.luislipinski.trucklife.identity.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record VerificationTokenRequest(
        @NotBlank
        @Pattern(regexp = "[A-Za-z0-9_-]{43}", message = "must be a valid verification token")
        @Schema(
                description = "Opaque URL-safe verification token",
                accessMode = Schema.AccessMode.WRITE_ONLY
        )
        String token
) {

    public VerificationTokenRequest {
        token = token == null ? null : token.strip();
    }

    @Override
    public String toString() {
        return "VerificationTokenRequest[token=redacted]";
    }
}
