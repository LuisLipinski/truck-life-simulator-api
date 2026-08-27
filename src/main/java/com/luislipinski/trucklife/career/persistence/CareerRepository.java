package com.luislipinski.trucklife.career.persistence;

import com.luislipinski.trucklife.career.domain.CareerGame;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CareerRepository extends JpaRepository<CareerEntity, UUID> {

    List<CareerEntity> findAllByUserIdAndGameOrderByCreatedAtAscIdAsc(UUID userId, CareerGame game);

    Optional<CareerEntity> findByIdAndUserIdAndGame(UUID id, UUID userId, CareerGame game);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT career
            FROM CareerEntity career
            WHERE career.id = :id
              AND career.userId = :userId
              AND career.game = :game
            """)
    Optional<CareerEntity> findForUpdateByIdAndUserIdAndGame(
            @Param("id") UUID id,
            @Param("userId") UUID userId,
            @Param("game") CareerGame game
    );

    long countByUserIdAndGame(UUID userId, CareerGame game);
}
