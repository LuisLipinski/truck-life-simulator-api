package com.luislipinski.trucklife.subscription.persistence;

import com.luislipinski.trucklife.subscription.domain.PaymentProviderCode;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentEventRepository extends JpaRepository<PaymentEventEntity, UUID> {
    Optional<PaymentEventEntity> findByProviderAndProviderEventId(
            PaymentProviderCode provider,
            String providerEventId
    );
}
