package com.luislipinski.trucklife.qualification.api;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record PromotionRequest(
        @Min(1) int expectedOperationalWeek,
        @Min(1) @Max(3) short expectedCurrentLevel,
        @Min(2) @Max(3) short targetLevel,
        @AssertTrue(message = "academyCompleted must be true") boolean academyCompleted
) {}
