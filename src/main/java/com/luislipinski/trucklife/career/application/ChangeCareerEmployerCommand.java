package com.luislipinski.trucklife.career.application;

public record ChangeCareerEmployerCommand(
        long version,
        String companyName,
        String effectiveDay
) {
}
