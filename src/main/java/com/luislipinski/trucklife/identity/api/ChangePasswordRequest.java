package com.luislipinski.trucklife.identity.api;

import com.luislipinski.trucklife.identity.api.validation.ValidPassword;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record ChangePasswordRequest(
        @NotNull
        @ValidPassword
        @Schema(format = "password", minLength = 12, maxLength = 128, accessMode = Schema.AccessMode.WRITE_ONLY)
        String currentPassword,

        @NotNull
        @ValidPassword
        @Schema(format = "password", minLength = 12, maxLength = 128, accessMode = Schema.AccessMode.WRITE_ONLY)
        String newPassword
) {
    @Override
    public String toString() {
        return "ChangePasswordRequest[currentPassword=redacted, newPassword=redacted]";
    }
}
