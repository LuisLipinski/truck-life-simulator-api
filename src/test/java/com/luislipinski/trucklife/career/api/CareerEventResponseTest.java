package com.luislipinski.trucklife.career.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.luislipinski.trucklife.career.domain.CareerEventType;
import com.luislipinski.trucklife.career.persistence.CareerEventEntity;
import java.time.DayOfWeek;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class CareerEventResponseTest {

    @Test
    void deserializesStructuredPreviousAndNextValuesWithOperationalWeekday() {
        CareerEventEntity event = new CareerEventEntity(
                UUID.randomUUID(),
                UUID.randomUUID(),
                CareerEventType.EMPLOYER_CHANGED,
                3,
                DayOfWeek.WEDNESDAY,
                Instant.parse("2026-08-26T20:00:00Z"),
                "{\"company\":{\"previous\":\"Old Logistics\",\"next\":\"New Logistics\"}}"
        );

        CareerEventResponse response = CareerEventResponse.from(event, new ObjectMapper());

        assertThat(response.type()).isEqualTo(CareerEventType.EMPLOYER_CHANGED);
        assertThat(response.operationalWeek()).isEqualTo(3);
        assertThat(response.effectiveDay()).isEqualTo("wednesday");
        assertThat(response.changes()).containsEntry(
                "company",
                Map.of("previous", "Old Logistics", "next", "New Logistics")
        );
    }

    @Test
    void keepsProfileCorrectionsWithoutInventingAGameWeekday() {
        CareerEventEntity event = new CareerEventEntity(
                UUID.randomUUID(),
                UUID.randomUUID(),
                CareerEventType.PROFILE_UPDATED,
                2,
                null,
                Instant.parse("2026-08-26T20:00:00Z"),
                "{\"driverName\":{\"previous\":\"Old Driver\",\"next\":\"New Driver\"}}"
        );

        CareerEventResponse response = CareerEventResponse.from(event, new ObjectMapper());

        assertThat(response.operationalWeek()).isEqualTo(2);
        assertThat(response.effectiveDay()).isNull();
    }
}
