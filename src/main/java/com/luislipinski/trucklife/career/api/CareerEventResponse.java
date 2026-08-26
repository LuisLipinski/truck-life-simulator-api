package com.luislipinski.trucklife.career.api;

import com.luislipinski.trucklife.career.domain.CareerEventType;
import com.luislipinski.trucklife.career.persistence.CareerEventEntity;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

public record CareerEventResponse(
        UUID id,
        CareerEventType type,
        LocalDate effectiveDate,
        Instant recordedAt,
        Map<String, Object> changes
) {

    private static final TypeReference<Map<String, Object>> CHANGES_TYPE = new TypeReference<>() {
    };

    static CareerEventResponse from(CareerEventEntity event, ObjectMapper objectMapper) {
        try {
            return new CareerEventResponse(
                    event.getId(),
                    event.getType(),
                    event.getEffectiveDate(),
                    event.getRecordedAt(),
                    objectMapper.readValue(event.getChangesJson().getBytes(java.nio.charset.StandardCharsets.UTF_8), CHANGES_TYPE)
            );
        } catch (JacksonException exception) {
            throw new IllegalStateException("Career event changes could not be deserialized", exception);
        }
    }
}
