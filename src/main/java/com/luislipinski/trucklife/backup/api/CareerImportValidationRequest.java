package com.luislipinski.trucklife.backup.api;

import com.luislipinski.trucklife.career.domain.CareerGame;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

public record CareerImportValidationRequest(
        @NotNull UUID operationId,
        @NotBlank @Size(max = 200) String sourceCareerId,
        @NotNull CareerGame game,
        @Min(12) @Max(12) int sourceVersion,
        @NotNull Map<String, Object> career,
        @NotNull Map<String, Object> state
) {

    public CareerImportValidationRequest {
        state = normalizeLegacyState(state);
    }

    private static Map<String, Object> normalizeLegacyState(Map<String, Object> source) {
        if (source == null) {
            return null;
        }
        Map<String, Object> normalized = new LinkedHashMap<>(source);
        normalized.put("trips", normalizeList(source.get("trips"), CareerImportValidationRequest::normalizeLegacyTrip));
        normalized.put("closedWeeks", normalizeList(
                source.get("closedWeeks"),
                CareerImportValidationRequest::normalizeLegacyClosedPeriod
        ));
        normalized.put("incidents", normalizeList(
                source.get("incidents"),
                CareerImportValidationRequest::normalizeLegacyIncident
        ));
        return normalized;
    }

    private static Object normalizeLegacyTrip(Object value) {
        if (!(value instanceof Map<?, ?> source)) {
            return value;
        }
        Map<String, Object> trip = stringKeyMap(source);
        copyTextAlias(trip, "origin", "from");
        copyTextAlias(trip, "originCompany", "fromCompany");
        copyTextAlias(trip, "destination", "to");
        copyTextAlias(trip, "destinationCompany", "toCompany");
        deriveOperationalTime(trip);
        return trip;
    }

    private static Object normalizeLegacyClosedPeriod(Object value) {
        if (!(value instanceof Map<?, ?> source)) {
            return value;
        }
        Map<String, Object> period = stringKeyMap(source);
        copyAlias(period, "distance", "totalMiles");
        copyAlias(period, "deposit", "netDeposit");
        deriveLegacyPayrollTotals(period);
        return period;
    }

    private static Object normalizeLegacyIncident(Object value) {
        if (!(value instanceof Map<?, ?> source)) {
            return value;
        }
        Map<String, Object> incident = stringKeyMap(source);
        copyTextAlias(incident, "chargeMethod", "payment");
        return incident;
    }

    private static void deriveOperationalTime(Map<String, Object> trip) {
        if (hasText(trip.get("departureDay"))
                && hasText(trip.get("departureTime"))
                && hasText(trip.get("arrivalDay"))
                && hasText(trip.get("arrivalTime"))) {
            return;
        }
        LocalDateTime departure = legacyDateTime(trip.get("departureAt"));
        LocalDateTime arrival = legacyDateTime(trip.get("arrivalAt"));
        if (departure == null || arrival == null) {
            return;
        }
        putTextIfMissing(trip, "departureDay", departure.getDayOfWeek().name());
        putTextIfMissing(trip, "departureTime", operationalTime(departure));
        putTextIfMissing(trip, "arrivalDay", arrival.getDayOfWeek().name());
        putTextIfMissing(trip, "arrivalTime", operationalTime(arrival));
    }

    private static LocalDateTime legacyDateTime(Object value) {
        if (!hasText(value)) {
            return null;
        }
        String text = String.valueOf(value).strip();
        try {
            return OffsetDateTime.parse(text).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDateTime.parse(text);
            } catch (DateTimeParseException ignoredAgain) {
                return null;
            }
        }
    }

    private static String operationalTime(LocalDateTime value) {
        return value.toLocalTime().withSecond(0).withNano(0).toString();
    }

    private static void deriveLegacyPayrollTotals(Map<String, Object> period) {
        BigDecimal deposit = decimal(period.get("deposit"));
        BigDecimal perDiem = decimalOrZero(period.get("perDiem"));
        BigDecimal incidentDeduction = decimalOrZero(period.get("incidentDeduction"));

        if (period.get("netSalary") == null && deposit != null && perDiem != null && incidentDeduction != null) {
            BigDecimal netSalary = deposit.subtract(perDiem).add(incidentDeduction);
            if (netSalary.signum() >= 0) {
                period.put("netSalary", netSalary);
            }
        }

        if (period.get("taxes") != null) {
            return;
        }
        BigDecimal gross = decimal(period.get("gross"));
        BigDecimal netSalary = decimal(period.get("netSalary"));
        BigDecimal benefits = decimalOrZero(period.get("benefits"));
        if (gross == null || netSalary == null || benefits == null) {
            return;
        }
        BigDecimal unitemizedDeductions = gross.subtract(netSalary).subtract(benefits);
        if (unitemizedDeductions.signum() < 0) {
            return;
        }
        period.put("taxes", unitemizedDeductions);
        if (period.get("taxBreakdown") == null && unitemizedDeductions.signum() > 0) {
            period.put("taxBreakdown", Map.of(
                    "Legacy unitemized payroll deductions",
                    unitemizedDeductions
            ));
        }
    }

    private static Object normalizeList(Object value, Function<Object, Object> normalizer) {
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> list)) {
            return value;
        }
        return list.stream().map(normalizer).toList();
    }

    private static Map<String, Object> stringKeyMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private static void copyTextAlias(Map<String, Object> target, String destination, String source) {
        if (!hasText(target.get(destination)) && hasText(target.get(source))) {
            target.put(destination, String.valueOf(target.get(source)).strip());
        }
    }

    private static void copyAlias(Map<String, Object> target, String destination, String source) {
        if (target.get(destination) == null && target.get(source) != null) {
            target.put(destination, target.get(source));
        }
    }

    private static void putTextIfMissing(Map<String, Object> target, String key, String value) {
        if (!hasText(target.get(key))) {
            target.put(key, value);
        }
    }

    private static boolean hasText(Object value) {
        return value != null && !String.valueOf(value).isBlank();
    }

    private static BigDecimal decimalOrZero(Object value) {
        return value == null ? BigDecimal.ZERO : decimal(value);
    }

    private static BigDecimal decimal(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return value instanceof BigDecimal number ? number : new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
