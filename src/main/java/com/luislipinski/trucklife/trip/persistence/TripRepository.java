package com.luislipinski.trucklife.trip.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TripRepository extends JpaRepository<TripEntity, UUID> {

    List<TripEntity> findAllByCareerIdOrderByOperationalWeekAscCreatedAtAscIdAsc(UUID careerId);

    List<TripEntity> findAllByCareerIdAndOperationalWeekOrderByCreatedAtAscIdAsc(UUID careerId, int operationalWeek);

    Optional<TripEntity> findByIdAndCareerId(UUID id, UUID careerId);
}
