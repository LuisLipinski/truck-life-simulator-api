package com.luislipinski.trucklife.phase2.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.luislipinski.trucklife.career.domain.CareerGame;
import com.luislipinski.trucklife.career.persistence.CareerEntity;
import com.luislipinski.trucklife.payroll.persistence.PayslipEntity;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class Phase2TerminationPolicyCatalogTest {
    private final Phase2TerminationPolicyCatalog catalog = new Phase2TerminationPolicyCatalog();

    @Test
    void atsVoluntaryTransitionDoesNotInventStateSeverance() {
        CareerEntity career = career(CareerGame.ATS, "US", "CA", "USD");

        var result = catalog.quote(career, 1, List.of(
                payslip(career.getId(), CareerGame.ATS, 1, null, "850.00", "USD")
        ));

        assertThat(result.amount()).isEqualByComparingTo("0.00");
        assertThat(result.policySource()).contains("dol.gov");
        assertThat(result.snapshot()).containsEntry("rule", "US_FEDERAL_VOLUNTARY_RESIGNATION");
    }

    @Test
    void italyPaysSimplifiedTfrFromGrossHistoryOfCurrentEmploymentOnly() {
        CareerEntity career = career(CareerGame.ETS2, "IT", null, "EUR");

        var result = catalog.quote(career, 3, List.of(
                payslip(career.getId(), CareerGame.ETS2, null, 1, "2700.00", "EUR", 1, 2),
                payslip(career.getId(), CareerGame.ETS2, null, 2, "2700.00", "EUR", 3, 5),
                payslip(career.getId(), CareerGame.ETS2, null, 3, "2700.00", "EUR", 6, 9)
        ));

        assertThat(result.amount()).isEqualByComparingTo("400.00");
        assertThat(result.policySource()).contains("lavoro.gov.it");
        assertThat(result.snapshot()).containsEntry("eligibleGrossPaid", new BigDecimal("5400.00"));
        assertThat(result.snapshot()).containsEntry("annualRevaluationApplied", false);
    }

    @Test
    void turkeyOrdinaryVoluntaryTransitionHasNoStatutorySeverance() {
        CareerEntity career = career(CareerGame.ETS2, "TR", null, "TRY");

        var result = catalog.quote(career, 1, List.of());

        assertThat(result.amount()).isEqualByComparingTo("0.00");
        assertThat(result.policySource()).contains("csgb.gov.tr");
        assertThat(result.snapshot()).containsEntry("rule", "TR_VOLUNTARY_RESIGNATION");
    }

    private CareerEntity career(CareerGame game, String country, String state, String currency) {
        Instant now = Instant.parse("2026-11-28T10:00:00Z");
        return new CareerEntity(
                UUID.randomUUID(),
                UUID.randomUUID(),
                game,
                "Driver",
                "Road Logistics",
                null,
                (short) 1,
                new BigDecimal("10000.00"),
                currency,
                currency,
                new BigDecimal("1.00000000"),
                LocalDate.of(2026, 11, 28),
                state,
                country,
                game == CareerGame.ATS ? "Los Angeles, CA" : "Milano",
                null,
                null,
                "test-v1",
                "Test",
                BigDecimal.ONE.setScale(4),
                BigDecimal.ONE.setScale(4),
                1,
                game == CareerGame.ETS2 ? 1 : null,
                now,
                now
        );
    }

    private PayslipEntity payslip(
            UUID careerId,
            CareerGame game,
            Integer week,
            Integer month,
            String gross,
            String currency
    ) {
        int endWeek = week == null ? 1 : week;
        return payslip(careerId, game, week, month, gross, currency, endWeek, endWeek);
    }

    private PayslipEntity payslip(
            UUID careerId,
            CareerGame game,
            Integer week,
            Integer month,
            String gross,
            String currency,
            int startWeek,
            int endWeek
    ) {
        BigDecimal grossAmount = new BigDecimal(gross);
        BigDecimal zero = BigDecimal.ZERO.setScale(2);
        return new PayslipEntity(
                UUID.randomUUID(),
                careerId,
                game,
                week,
                month,
                startWeek,
                endWeek,
                (short) 1,
                currency,
                grossAmount,
                zero,
                zero,
                zero,
                grossAmount,
                zero,
                grossAmount,
                zero,
                zero,
                grossAmount,
                zero,
                0,
                0,
                0,
                0,
                "{}",
                Instant.parse("2026-11-28T10:00:00Z")
        );
    }
}
