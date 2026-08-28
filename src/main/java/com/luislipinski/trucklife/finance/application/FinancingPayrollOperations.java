package com.luislipinski.trucklife.finance.application;

import com.luislipinski.trucklife.career.persistence.CareerEntity;
import java.time.Instant;

public interface FinancingPayrollOperations {
    void processDuePayments(CareerEntity career,int operationalWeek,Integer payrollMonth,Instant now);
}
