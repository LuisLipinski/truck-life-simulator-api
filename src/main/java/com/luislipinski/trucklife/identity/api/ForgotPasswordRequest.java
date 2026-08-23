package com.luislipinski.trucklife.identity.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ForgotPasswordRequest(
        @NotBlank
        @Email
        @Size(max = 320)
        @Schema(example = "driver@example.com", maxLength = 320)
        String email
) {
    public ForgotPasswordRequest {
        email = email == null ? null : email.strip();
    }

    @Override
    public String toString() {
        return "ForgotPasswordRequest[email=redacted]";
    }
}
