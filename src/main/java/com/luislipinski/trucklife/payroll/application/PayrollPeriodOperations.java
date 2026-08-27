package com.luislipinski.trucklife.payroll.application;

import com.luislipinski.trucklife.career.domain.CareerGame;
import com.luislipinski.trucklife.payroll.persistence.PayrollPeriodEntity;
import java.util.List;
import java.util.UUID;

public interface PayrollPeriodOperations {

    PayrollPeriodEntity close(
            UUID userId,
            CareerGame game,
            UUID careerId,
            int expectedOperationalWeek
    );

    List<PayrollPeriodEntity> list(UUID userId, CareerGame game, UUID careerId);
}
