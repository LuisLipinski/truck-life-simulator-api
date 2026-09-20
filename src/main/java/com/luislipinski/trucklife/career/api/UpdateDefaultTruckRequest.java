package com.luislipinski.trucklife.career.api;

import jakarta.validation.constraints.Size;

public record UpdateDefaultTruckRequest(
        @Size(max = 80) String defaultTruckMake,
        @Size(max = 120) String defaultTruckModel
) {
}
