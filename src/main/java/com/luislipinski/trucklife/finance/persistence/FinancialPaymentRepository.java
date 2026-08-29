package com.luislipinski.trucklife.finance.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinancialPaymentRepository extends JpaRepository<FinancialPaymentEntity,UUID> {
    List<FinancialPaymentEntity> findAllByContractIdOrderByRecordedAtAscIdAsc(UUID contractId);
    Optional<FinancialPaymentEntity> findByOperationId(UUID operationId);
}
