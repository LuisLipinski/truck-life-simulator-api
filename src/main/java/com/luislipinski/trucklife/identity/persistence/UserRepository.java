package com.luislipinski.trucklife.identity.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {

    Optional<UserEntity> findByNormalizedEmail(String normalizedEmail);

    boolean existsByNormalizedEmail(String normalizedEmail);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select account from UserEntity account where account.normalizedEmail = :normalizedEmail")
    Optional<UserEntity> findByNormalizedEmailForUpdate(
            @Param("normalizedEmail") String normalizedEmail
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select account from UserEntity account where account.id = :userId")
    Optional<UserEntity> findByIdForUpdate(@Param("userId") UUID userId);
}
