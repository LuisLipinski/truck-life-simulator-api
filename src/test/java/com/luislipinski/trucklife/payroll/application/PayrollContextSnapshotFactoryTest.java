package com.luislipinski.trucklife.payroll.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.luislipinski.trucklife.career.domain.CareerGame;
import com.luislipinski.trucklife.career.persistence.CareerEntity;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PayrollContextSnapshotFactoryTest {

    private final PayrollContextSnapshotFactory factory = new PayrollContextSnapshotFactory();

    @Test
    void replacesPersistedClientFactorsWithServerCityPolicy() {
        CareerEntity career = career(
                CareerGame.ATS,
                "AZ",
                null,
                "Phoenix, AZ",
                "client-v999",
                "Client supplied market",
                "9.9000",
                "9.9000"
        );

        Map<String, Object> snapshot = factory.from(career);

        assertThat(snapshot)
                .containsEntry("cityMarketVersion", "1")
                .containsEntry("cityMarketKey", "major")
                .containsEntry("cityMarketLabel", "Metrópole principal")
                .containsEntry("cityMarketKnown", true);
        assertThat((BigDecimal) snapshot.get("cityCostFactor")).isEqualByComparingTo("1.10");
        assertThat((BigDecimal) snapshot.get("citySalaryFactor")).isEqualByComparingTo("1.05");
    }

    @Test
    void makesUnknownModCityNeutralRegardlessOfPersistedClientFactors() {
        CareerEntity career = career(
                CareerGame.ATS,
                "AZ",
                null,
                "Modville, AZ",
                "client-v999",
                "Client supplied market",
                "3.0000",
                "4.0000"
        );

        Map<String, Object> snapshot = factory.from(career);

        assertThat(snapshot)
                .containsEntry("cityMarketVersion", "1")
                .containsEntry("cityMarketKey", "reference")
                .containsEntry("cityMarketKnown", false);
        assertThat((BigDecimal) snapshot.get("cityCostFactor")).isEqualByComparingTo("1");
        assertThat((BigDecimal) snapshot.get("citySalaryFactor")).isEqualByComparingTo("1");
    }

    private CareerEntity career(
            CareerGame game,
            String stateCode,
            String countryCode,
            String baseCity,
            String cityMarketVersion,
            String cityMarketLabel,
            String cityCostFactor,
            String citySalaryFactor
    ) {
        Instant now = Instant.parse("2026-08-27T09:00:00Z");
        return new CareerEntity(
                UUID.randomUUID(),
                UUID.randomUUID(),
                game,
                "Payroll Driver",
                "Road Logistics",
                "Test career",
                (short) 1,
                new BigDecimal("5000.00"),
                game == CareerGame.ATS ? "USD" : "EUR",
                game == CareerGame.ATS ? "USD" : "EUR",
                new BigDecimal("1.00000000"),
                LocalDate.of(2026, 8, 26),
                stateCode,
                countryCode,
                baseCity,
                game == CareerGame.ATS ? "Kenworth" : "MAN",
                game == CareerGame.ATS ? "T680" : "TGX",
                cityMarketVersion,
                cityMarketLabel,
                new BigDecimal(cityCostFactor),
                new BigDecimal(citySalaryFactor),
                1,
                game == CareerGame.ETS2 ? 1 : null,
                now,
                now
        );
    }
}
