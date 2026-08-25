package com.luislipinski.trucklife.identity.api;

import io.swagger.v3.oas.annotations.media.Schema;

public record LoginRequest(
        @Schema(example = "driver@example.com", maxLength = 320)
        String email,

        @Schema(format = "password", accessMode = Schema.AccessMode.WRITE_ONLY)
        String password
) {

    public LoginRequest {
        email = email == null ? null : email.strip();
    }

    @Override
    public String toString() {
        return "LoginRequest[email=redacted, password=redacted]";
    }
}
