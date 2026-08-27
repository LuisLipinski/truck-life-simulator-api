package com.luislipinski.trucklife.trip.persistence;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TripRepository extends JpaRepository<TripEntity, UUID> {

    List<TripEntity> findAllByCareerIdOrderByOperationalWeekAscCreatedAtAscIdAsc(UUID careerId);

    List<TripEntity> findAllByCareerIdAndOperationalWeekOrderByCreatedAtAscIdAsc(UUID careerId, int operationalWeek);

    Optional<TripEntity> findByIdAndCareerId(UUID id, UUID careerId);

    @Query("select coalesce(sum(t.officialDistance), 0) from TripEntity t where t.careerId = :careerId")
    BigDecimal sumOfficialDistanceByCareerId(@Param("careerId") UUID careerId);
}
