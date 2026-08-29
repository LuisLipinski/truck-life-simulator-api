package com.luislipinski.trucklife.finance.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinancialContractEventRepository extends JpaRepository<FinancialContractEventEntity,UUID> {
    List<FinancialContractEventEntity> findAllByContractIdOrderByRecordedAtAscIdAsc(UUID contractId);
}
