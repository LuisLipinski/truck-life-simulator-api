package com.luislipinski.trucklife.backup.api;

import com.luislipinski.trucklife.career.domain.CareerGame;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.UUID;

public record CareerImportValidationRequest(
        @NotNull UUID operationId,
        @NotBlank @Size(max = 200) String sourceCareerId,
        @NotNull CareerGame game,
        @Min(12) @Max(12) int sourceVersion,
        @NotNull Map<String, Object> career,
        @NotNull Map<String, Object> state
) {
}
