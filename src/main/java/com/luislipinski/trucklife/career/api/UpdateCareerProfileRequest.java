package com.luislipinski.trucklife.career.api;

import com.luislipinski.trucklife.career.application.UpdateCareerProfileCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record UpdateCareerProfileRequest(
        @NotNull @PositiveOrZero Long version,
        @NotBlank @Size(max = 120) String driverName,
        @Size(max = 800) String biography
) {

    UpdateCareerProfileCommand toCommand() {
        return new UpdateCareerProfileCommand(version, driverName, biography);
    }
}
