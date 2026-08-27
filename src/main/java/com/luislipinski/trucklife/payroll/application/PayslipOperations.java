package com.luislipinski.trucklife.payroll.application;

import com.luislipinski.trucklife.career.domain.CareerGame;
import com.luislipinski.trucklife.payroll.persistence.PayslipEntity;
import com.luislipinski.trucklife.payroll.persistence.PayslipLineEntity;
import java.util.List;
import java.util.UUID;

public interface PayslipOperations {
    Result generate(UUID userId, CareerGame game, UUID careerId, Integer expectedOperationalWeek, Integer expectedPayrollMonth);
    List<Result> list(UUID userId, CareerGame game, UUID careerId);
    Result get(UUID userId, CareerGame game, UUID careerId, UUID payslipId);
    record Result(PayslipEntity payslip, List<PayslipLineEntity> lines) {}
}
