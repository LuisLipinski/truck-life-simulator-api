package com.luislipinski.trucklife.payroll.api;

import com.luislipinski.trucklife.career.domain.CareerGame;
import com.luislipinski.trucklife.payroll.application.PayrollPreferencesService;
import java.math.BigDecimal;
import java.util.UUID;

public record PayrollSettingsResponse(
        UUID careerId,
        CareerGame game,
        int currentOperationalWeek,
        Integer currentPayrollMonth,
        short currentLevel,
        String displayCurrency,
        boolean editable,
        BigDecimal defaultLevel1Gross,
        BigDecimal defaultRouteOverrunRate,
        BigDecimal defaultBenefits,
        BigDecimal defaultPerDiemRate,
        BigDecimal level1Gross,
        BigDecimal routeOverrunRate,
        BigDecimal benefits,
        BigDecimal perDiemRate
) {
    static PayrollSettingsResponse from(PayrollPreferencesService.SettingsState state) {
        var settings = state.settings();
        return new PayrollSettingsResponse(
                state.careerId(), state.game(), state.currentOperationalWeek(), state.currentPayrollMonth(),
                state.currentLevel(), state.displayCurrency(), state.editable(),
                settings.defaultLevel1Gross(), settings.defaultRouteOverrunRate(),
                settings.defaultBenefits(), settings.defaultPerDiemRate(),
                settings.level1Gross(), settings.routeOverrunRate(), settings.benefits(), settings.perDiemRate()
        );
    }
}
