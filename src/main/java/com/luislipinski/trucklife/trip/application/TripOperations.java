package com.luislipinski.trucklife.trip.application;

import com.luislipinski.trucklife.career.domain.CareerGame;
import com.luislipinski.trucklife.trip.persistence.TripEntity;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface TripOperations {

    TripEntity create(UUID userId, CareerGame game, UUID careerId, CreateTripCommand command);

    Draft getDraft(UUID userId, CareerGame game, UUID careerId);

    Draft saveDraft(UUID userId, CareerGame game, UUID careerId, SaveTripDraftCommand command);

    List<TripEntity> list(UUID userId, CareerGame game, UUID careerId, Integer operationalWeek);

    TripEntity get(UUID userId, CareerGame game, UUID careerId, UUID tripId);

    void delete(UUID userId, CareerGame game, UUID careerId, UUID tripId);

    record Draft(int operationalWeek, Map<String, Object> data, Instant updatedAt) {}
}
