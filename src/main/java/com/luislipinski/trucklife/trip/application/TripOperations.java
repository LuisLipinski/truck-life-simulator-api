package com.luislipinski.trucklife.trip.application;

import com.luislipinski.trucklife.career.domain.CareerGame;
import com.luislipinski.trucklife.trip.persistence.TripEntity;
import java.util.List;
import java.util.UUID;

public interface TripOperations {

    TripEntity create(UUID userId, CareerGame game, UUID careerId, CreateTripCommand command);

    List<TripEntity> list(UUID userId, CareerGame game, UUID careerId, Integer operationalWeek);

    TripEntity get(UUID userId, CareerGame game, UUID careerId, UUID tripId);
}
