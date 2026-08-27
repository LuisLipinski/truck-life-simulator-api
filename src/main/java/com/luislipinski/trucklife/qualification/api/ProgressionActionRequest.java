package com.luislipinski.trucklife.qualification.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record ProgressionActionRequest(
        @Min(1) int expectedOperationalWeek,
        @Min(1) @Max(3) short expectedCurrentLevel
) {}
