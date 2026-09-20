package com.luislipinski.trucklife.trip.api;

import com.luislipinski.trucklife.trip.application.TripOperations;
import java.time.Instant;
import java.util.Map;

public record TripDraftResponse(
        int operationalWeek,
        Map<String, Object> data,
        Instant updatedAt
) {

    static TripDraftResponse from(TripOperations.Draft draft) {
        return new TripDraftResponse(
                draft.operationalWeek(),
                draft.data(),
                draft.updatedAt()
        );
    }
}
