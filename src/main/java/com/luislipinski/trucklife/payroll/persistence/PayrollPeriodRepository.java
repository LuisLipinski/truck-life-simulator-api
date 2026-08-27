package com.luislipinski.trucklife.payroll.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayrollPeriodRepository extends JpaRepository<PayrollPeriodEntity, UUID> {

    List<PayrollPeriodEntity> findAllByCareerIdOrderByOperationalWeekAsc(UUID careerId);

    long countByCareerIdAndPayrollMonth(UUID careerId, Integer payrollMonth);
}
