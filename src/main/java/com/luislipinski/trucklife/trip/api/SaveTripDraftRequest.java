package com.luislipinski.trucklife.trip.api;

import com.luislipinski.trucklife.trip.application.SaveTripDraftCommand;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

public record SaveTripDraftRequest(
        @Min(1) int expectedOperationalWeek,
        @NotNull Map<String, Object> data
) {

    SaveTripDraftCommand toCommand() {
        return new SaveTripDraftCommand(expectedOperationalWeek, data);
    }
}
