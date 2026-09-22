package com.luislipinski.trucklife.subscription.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionRepository extends JpaRepository<SubscriptionEntity, UUID> {
    List<SubscriptionEntity> findAllByUserIdOrderByUpdatedAtDescIdDesc(UUID userId);
}
