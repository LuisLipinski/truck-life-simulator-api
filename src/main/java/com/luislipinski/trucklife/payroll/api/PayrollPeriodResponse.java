package com.luislipinski.trucklife.payroll.api;

import com.luislipinski.trucklife.career.domain.CareerGame;
import com.luislipinski.trucklife.payroll.persistence.PayrollPeriodEntity;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

public record PayrollPeriodResponse(
        UUID id,
        UUID careerId,
        CareerGame game,
        int operationalWeek,
        Integer payrollMonth,
        Map<String, Object> contextSnapshot,
        Instant closedAt
) {

    private static final TypeReference<Map<String, Object>> SNAPSHOT_TYPE = new TypeReference<>() {
    };

    static PayrollPeriodResponse from(
            PayrollPeriodEntity period,
            CareerGame game,
            ObjectMapper objectMapper
    ) {
        return new PayrollPeriodResponse(
                period.getId(),
                period.getCareerId(),
                game,
                period.getOperationalWeek(),
                period.getPayrollMonth(),
                snapshot(period.getContextSnapshotJson(), objectMapper),
                period.getClosedAt()
        );
    }

    private static Map<String, Object> snapshot(String json, ObjectMapper objectMapper) {
        try {
            return objectMapper.readValue(
                    json.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    SNAPSHOT_TYPE
            );
        } catch (JacksonException exception) {
            throw new IllegalStateException("Payroll period snapshot could not be deserialized", exception);
        }
    }
}
