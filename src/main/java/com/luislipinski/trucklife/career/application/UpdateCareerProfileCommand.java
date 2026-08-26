package com.luislipinski.trucklife.career.application;

import java.time.LocalDate;

public record UpdateCareerProfileCommand(
        long version,
        String driverName,
        String biography,
        LocalDate effectiveDate
) {

    public UpdateCareerProfileCommand(long version, String driverName, String biography) {
        this(version, driverName, biography, null);
    }
}
