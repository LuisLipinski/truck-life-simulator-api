package com.luislipinski.trucklife.finance.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinancialInstallmentRepository extends JpaRepository<FinancialInstallmentEntity,UUID> {
    List<FinancialInstallmentEntity> findAllByContractIdOrderByScheduleVersionAscInstallmentNumberAsc(UUID contractId);
    List<FinancialInstallmentEntity> findAllByContractIdAndScheduleVersionOrderByInstallmentNumberAsc(UUID contractId,int scheduleVersion);
}
