package com.luislipinski.trucklife.career.application;

public record UpdateCareerProfileCommand(
        long version,
        String driverName,
        String biography
) {
}
