package com.luislipinski.trucklife.identity.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserActionTokenRepository extends JpaRepository<UserActionTokenEntity, UUID> {

    Optional<UserActionTokenEntity> findByTokenHash(String tokenHash);
}
