package com.luislipinski.trucklife.identity.persistence;

import com.luislipinski.trucklife.identity.domain.UserActionTokenPurpose;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserActionTokenRepository extends JpaRepository<UserActionTokenEntity, UUID> {

    Optional<UserActionTokenEntity> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select token
            from UserActionTokenEntity token
            join fetch token.user
            where token.tokenHash = :tokenHash
            """)
    Optional<UserActionTokenEntity> findByTokenHashForUpdate(
            @Param("tokenHash") String tokenHash
    );

    @Modifying(flushAutomatically = true)
    @Query("""
            update UserActionTokenEntity token
            set token.usedAt = :usedAt
            where token.user.id = :userId
              and token.purpose = :purpose
              and token.usedAt is null
              and token.expiresAt > :usedAt
            """)
    int markActiveTokensAsUsed(
            @Param("userId") UUID userId,
            @Param("purpose") UserActionTokenPurpose purpose,
            @Param("usedAt") Instant usedAt
    );
}
