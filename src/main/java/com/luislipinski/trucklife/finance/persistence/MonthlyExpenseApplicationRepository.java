package com.luislipinski.trucklife.finance.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MonthlyExpenseApplicationRepository extends JpaRepository<MonthlyExpenseApplicationEntity, UUID> {}
