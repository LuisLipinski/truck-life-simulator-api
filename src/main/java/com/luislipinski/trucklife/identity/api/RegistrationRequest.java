package com.luislipinski.trucklife.identity.api;

import com.luislipinski.trucklife.identity.api.validation.ValidPassword;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegistrationRequest(
        @NotBlank
        @Email
        @Size(max = 320)
        @Schema(example = "driver@example.com", maxLength = 320)
        String email,

        @NotBlank
        @Size(min = 2, max = 120)
        @Schema(example = "Road Driver", minLength = 2, maxLength = 120)
        String displayName,

        @NotNull
        @ValidPassword
        @Schema(
                format = "password",
                minLength = 12,
                maxLength = 128,
                accessMode = Schema.AccessMode.WRITE_ONLY
        )
        String password
) {

    public RegistrationRequest {
        email = email == null ? null : email.strip();
        displayName = displayName == null ? null : displayName.strip();
    }

    @Override
    public String toString() {
        return "RegistrationRequest[email=redacted, displayName=redacted, password=redacted]";
    }
}
