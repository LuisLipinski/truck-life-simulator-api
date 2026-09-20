package com.luislipinski.trucklife.trip.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TripDraftRepository extends JpaRepository<TripDraftEntity, UUID> {
}
