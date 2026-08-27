package com.luislipinski.trucklife.payroll.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayslipRepository extends JpaRepository<PayslipEntity, UUID> {
    List<PayslipEntity> findAllByCareerIdOrderByGeneratedAtDescIdDesc(UUID careerId);
    Optional<PayslipEntity> findByIdAndCareerId(UUID id, UUID careerId);
    boolean existsByCareerIdAndOperationalWeek(UUID careerId, Integer operationalWeek);
    boolean existsByCareerIdAndPayrollMonth(UUID careerId, Integer payrollMonth);
}
