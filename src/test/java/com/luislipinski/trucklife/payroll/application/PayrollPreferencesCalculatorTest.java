package com.luislipinski.trucklife.payroll.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.luislipinski.trucklife.career.domain.CareerGame;
import com.luislipinski.trucklife.trip.domain.TripPaymentCategory;
import com.luislipinski.trucklife.trip.domain.TripSource;
import com.luislipinski.trucklife.trip.domain.TripType;
import com.luislipinski.trucklife.trip.persistence.TripEntity;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PayrollPreferencesCalculatorTest {
    private final PayrollCalculator calculator = new PayrollCalculator();

    @Test
    void appliesPersistedPreferencesAndStillCalculatesTaxesAndDepositServerSide() {
        PayrollCalculator.Context context = new PayrollCalculator.Context(
                (short) 1, "AZ", "", "USD", "USD", BigDecimal.ONE, BigDecimal.ONE,
                new BigDecimal("1000.00"), new BigDecimal("30.00"),
                new BigDecimal("50.00"), new BigDecimal("90.00")
        );
        TripEntity trip = trip();

        PayrollCalculator.Calculation calculation = calculator.calculate(CareerGame.ATS, context, List.of(trip));
        PayrollCalculator.Settings settings = calculator.settings(CareerGame.ATS, context);

        assertThat(settings.defaultLevel1Gross()).isEqualByComparingTo("920.00");
        assertThat(settings.level1Gross()).isEqualByComparingTo("1000.00");
        assertThat(settings.routeOverrunRate()).isEqualByComparingTo("30.00");
        assertThat(settings.benefits()).isEqualByComparingTo("50.00");
        assertThat(settings.perDiemRate()).isEqualByComparingTo("90.00");
        assertThat(calculation.gross()).isEqualByComparingTo("1240.00");
        assertThat(calculation.benefits()).isEqualByComparingTo("50.00");
        assertThat(calculation.perDiem()).isEqualByComparingTo("180.00");
        assertThat(calculation.taxTotal()).isPositive();
        assertThat(calculation.deposit()).isEqualByComparingTo(calculation.netSalary().add(calculation.perDiem()));
    }

    private TripEntity trip() {
        Instant now = Instant.parse("2026-08-31T10:00:00Z");
        return new TripEntity(UUID.randomUUID(), UUID.randomUUID(), 1,
                DayOfWeek.MONDAY, LocalTime.parse("08:00"), DayOfWeek.TUESDAY, LocalTime.parse("08:00"),
                "Phoenix, AZ", "Origin", "Tucson, AZ", "Destination", "Cargo", TripType.LOADED,
                TripPaymentCategory.NORMAL, new BigDecimal("100.00"), 0, null, null, null, null,
                TripSource.MANUAL, "{}", "{}", now, now);
    }
}
