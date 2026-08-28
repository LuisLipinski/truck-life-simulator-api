package com.luislipinski.trucklife.finance.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MonthlyExpenseRepository extends JpaRepository<MonthlyExpenseEntity, UUID> {
    List<MonthlyExpenseEntity> findAllByCareerIdOrderByCreatedAtAscIdAsc(UUID careerId);
    Optional<MonthlyExpenseEntity> findByIdAndCareerId(UUID id, UUID careerId);
}
