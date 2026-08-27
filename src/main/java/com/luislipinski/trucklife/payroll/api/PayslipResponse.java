package com.luislipinski.trucklife.payroll.api;

import com.luislipinski.trucklife.career.domain.CareerGame;
import com.luislipinski.trucklife.payroll.application.PayslipOperations;
import com.luislipinski.trucklife.payroll.domain.PayslipLineType;
import com.luislipinski.trucklife.payroll.persistence.PayslipEntity;
import com.luislipinski.trucklife.payroll.persistence.PayslipLineEntity;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

public record PayslipResponse(
        UUID id,
        UUID careerId,
        CareerGame game,
        Integer operationalWeek,
        Integer payrollMonth,
        int startOperationalWeek,
        int endOperationalWeek,
        short level,
        String displayCurrency,
        BigDecimal grossAmount,
        BigDecimal taxAmount,
        BigDecimal benefitsAmount,
        BigDecimal perDiemAmount,
        BigDecimal netSalaryAmount,
        BigDecimal incidentDeductionAmount,
        BigDecimal depositAmount,
        BigDecimal totalDistance,
        int elapsedMinutes,
        int breakMinutes,
        int workedMinutes,
        int overrunMinutes,
        Map<String, Object> contextSnapshot,
        Instant generatedAt,
        List<LineResponse> lines
) {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    static PayslipResponse from(PayslipOperations.Result result, ObjectMapper objectMapper) {
        PayslipEntity p = result.payslip();
        return new PayslipResponse(
                p.getId(),
                p.getCareerId(),
                p.getGame(),
                p.getOperationalWeek(),
                p.getPayrollMonth(),
                p.getStartOperationalWeek(),
                p.getEndOperationalWeek(),
                p.getLevel(),
                p.getDisplayCurrency(),
                p.getGrossAmount(),
                p.getTaxAmount(),
                p.getBenefitsAmount(),
                p.getPerDiemAmount(),
                p.getNetSalaryAmount(),
                p.getIncidentDeductionAmount(),
                p.getDepositAmount(),
                p.getTotalDistance(),
                p.getElapsedMinutes(),
                p.getBreakMinutes(),
                p.getWorkedMinutes(),
                p.getOverrunMinutes(),
                map(p.getContextSnapshotJson(), objectMapper),
                p.getGeneratedAt(),
                result.lines().stream()
                        .map(line -> LineResponse.from(line, objectMapper))
                        .toList()
        );
    }

    public record LineResponse(
            UUID id,
            int order,
            String code,
            String label,
            PayslipLineType type,
            BigDecimal amount,
            BigDecimal quantity,
            BigDecimal rate,
            Map<String, Object> metadata
    ) {
        static LineResponse from(PayslipLineEntity line, ObjectMapper objectMapper) {
            return new LineResponse(
                    line.getId(),
                    line.getLineOrder(),
                    line.getCode(),
                    line.getLabel(),
                    line.getLineType(),
                    line.getAmount(),
                    line.getQuantity(),
                    line.getRate(),
                    map(line.getMetadataJson(), objectMapper)
            );
        }
    }

    private static Map<String, Object> map(String json, ObjectMapper objectMapper) {
        try {
            return objectMapper.readValue(json.getBytes(StandardCharsets.UTF_8), MAP_TYPE);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Payslip JSON could not be deserialized", exception);
        }
    }
}
