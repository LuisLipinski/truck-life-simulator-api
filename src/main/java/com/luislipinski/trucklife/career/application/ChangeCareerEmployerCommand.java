package com.luislipinski.trucklife.career.application;

import java.time.LocalDate;

public record ChangeCareerEmployerCommand(
        long version,
        String companyName,
        LocalDate effectiveDate
) {
}
