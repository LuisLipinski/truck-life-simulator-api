package com.luislipinski.trucklife.subscription.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentOrderRepository extends JpaRepository<PaymentOrderEntity, UUID> {
    Optional<PaymentOrderEntity> findByIdAndUserId(UUID id, UUID userId);
    Optional<PaymentOrderEntity> findByUserIdAndCheckoutOperationId(UUID userId, UUID checkoutOperationId);
    List<PaymentOrderEntity> findAllByUserIdOrderByCreatedAtDescIdDesc(UUID userId);
}
