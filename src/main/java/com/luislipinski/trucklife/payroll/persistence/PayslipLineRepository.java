package com.luislipinski.trucklife.payroll.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayslipLineRepository extends JpaRepository<PayslipLineEntity, UUID> {
    List<PayslipLineEntity> findAllByPayslipIdOrderByLineOrderAsc(UUID payslipId);
}
