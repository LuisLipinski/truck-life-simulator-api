package com.luislipinski.trucklife.trip.application;

import java.util.Map;

public record SaveTripDraftCommand(
        int expectedOperationalWeek,
        Map<String, Object> data
) {
}
