package com.luislipinski.trucklife.career.api;

import com.luislipinski.trucklife.career.application.ChangeCareerEmployerCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record ChangeCareerEmployerRequest(
        @NotNull @PositiveOrZero Long version,
        @NotBlank @Size(max = 160) String companyName,
        @NotNull LocalDate effectiveDate
) {

    ChangeCareerEmployerCommand toCommand() {
        return new ChangeCareerEmployerCommand(version, companyName, effectiveDate);
    }
}
