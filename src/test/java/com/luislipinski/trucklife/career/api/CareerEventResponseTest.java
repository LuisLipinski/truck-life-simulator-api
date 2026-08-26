package com.luislipinski.trucklife.career.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.luislipinski.trucklife.career.domain.CareerEventType;
import com.luislipinski.trucklife.career.persistence.CareerEventEntity;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class CareerEventResponseTest {

    @Test
    void deserializesStructuredPreviousAndNextValues() {
        CareerEventEntity event = new CareerEventEntity(
                UUID.randomUUID(),
                UUID.randomUUID(),
                CareerEventType.PROFILE_UPDATED,
                LocalDate.of(2026, 8, 26),
                Instant.parse("2026-08-26T20:00:00Z"),
                "{\"driverName\":{\"previous\":\"Old Driver\",\"next\":\"New Driver\"}}"
        );

        CareerEventResponse response = CareerEventResponse.from(event, new ObjectMapper());

        assertThat(response.type()).isEqualTo(CareerEventType.PROFILE_UPDATED);
        assertThat(response.effectiveDate()).isEqualTo(LocalDate.of(2026, 8, 26));
        assertThat(response.changes()).containsEntry(
                "driverName",
                Map.of("previous", "Old Driver", "next", "New Driver")
        );
    }
}
