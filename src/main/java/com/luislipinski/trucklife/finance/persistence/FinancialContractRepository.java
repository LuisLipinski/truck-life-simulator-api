package com.luislipinski.trucklife.finance.persistence;

import com.luislipinski.trucklife.finance.domain.FinancialContractStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinancialContractRepository extends JpaRepository<FinancialContractEntity,UUID> {
    List<FinancialContractEntity> findAllByCareerIdOrderByCreatedAtDescIdDesc(UUID careerId);
    List<FinancialContractEntity> findAllByCareerIdAndStatusInOrderByCreatedAtAscIdAsc(UUID careerId,List<FinancialContractStatus> statuses);
    Optional<FinancialContractEntity> findByIdAndCareerId(UUID id,UUID careerId);
    Optional<FinancialContractEntity> findByOriginationOperationId(UUID operationId);
}
