package com.luislipinski.trucklife.subscription.persistence;

import com.luislipinski.trucklife.subscription.domain.SubscriptionStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionRepository extends JpaRepository<SubscriptionEntity, UUID> {
    List<SubscriptionEntity> findAllByUserIdOrderByUpdatedAtDescIdDesc(UUID userId);

    Optional<SubscriptionEntity> findFirstByUserIdOrderByUpdatedAtDescIdDesc(UUID userId);

    Optional<SubscriptionEntity> findFirstByUserIdAndStatusInOrderByUpdatedAtDescIdDesc(
            UUID userId,
            Collection<SubscriptionStatus> statuses
    );
}
