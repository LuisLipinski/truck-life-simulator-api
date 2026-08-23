package com.luislipinski.trucklife.identity.api;

import com.luislipinski.trucklife.identity.api.validation.ValidPassword;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record ResetPasswordRequest(
        @NotBlank
        @Pattern(regexp = "[A-Za-z0-9_-]{43}", message = "must be a valid reset token")
        @Schema(description = "Opaque URL-safe password reset token", accessMode = Schema.AccessMode.WRITE_ONLY)
        String token,

        @NotNull
        @ValidPassword
        @Schema(format = "password", minLength = 12, maxLength = 128, accessMode = Schema.AccessMode.WRITE_ONLY)
        String newPassword
) {
    public ResetPasswordRequest {
        token = token == null ? null : token.strip();
    }

    @Override
    public String toString() {
        return "ResetPasswordRequest[token=redacted, newPassword=redacted]";
    }
}
