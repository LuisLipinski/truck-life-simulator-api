package com.luislipinski.trucklife.backup.application;

import com.luislipinski.trucklife.backup.api.CareerImportValidationRequest;
import com.luislipinski.trucklife.backup.persistence.CareerImportOperationEntity;
import com.luislipinski.trucklife.career.domain.CareerGame;
import com.luislipinski.trucklife.career.persistence.CareerEntity;
import com.luislipinski.trucklife.shared.error.ApiProblemException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Time;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class CareerImportAggregateMaterializer {

    private static final BigDecimal RESERVE_ANNUAL_YIELD = new BigDecimal("0.032500");
    private static final String IMPORT_POLICY = "local-v12-import";
    private static final Map<String, String> EXPENSE_CATEGORIES = Map.ofEntries(
            Map.entry("rent", "RENT"),
            Map.entry("electricity", "ELECTRICITY"),
            Map.entry("water", "WATER"),
            Map.entry("internet", "INTERNET"),
            Map.entry("phone", "PHONE"),
            Map.entry("groceries", "GROCERIES"),
            Map.entry("eatingOut", "EATING_OUT"),
            Map.entry("health", "HEALTH"),
            Map.entry("publicTransport", "PUBLIC_TRANSPORT"),
            Map.entry("household", "HOUSEHOLD"),
            Map.entry("leisure", "LEISURE")
    );

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public CareerImportAggregateMaterializer(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public void materialize(
            CareerImportOperationEntity operation,
            CareerEntity career,
            CareerImportValidationRequest request,
            Instant importedAt
    ) {
        archive(operation, career, request, importedAt);
        importTrips(career, request.state(), importedAt);
        Map<Integer, UUID> periodIds = importPayrollPeriods(career, request.state(), importedAt);
        importPayslips(career, request.state(), periodIds, importedAt);
        importIncidents(career, request.state(), importedAt);
        importCurrentFinance(career, request.state(), importedAt);
        importOpeningLedger(career, request.state(), importedAt);
    }

    private void archive(
            CareerImportOperationEntity operation,
            CareerEntity career,
            CareerImportValidationRequest request,
            Instant importedAt
    ) {
        Map<String, Object> archived = new LinkedHashMap<>();
        archived.put("sourceCareerId", request.sourceCareerId());
        archived.put("game", request.game().name());
        archived.put("sourceVersion", request.sourceVersion());
        archived.put("career", request.career());
        archived.put("state", request.state());
        jdbc.update("""
                INSERT INTO career_import_archives
                    (import_operation_id, career_id, source_version, snapshot_json, archived_at)
                VALUES (?, ?, ?, ?, ?)
                """,
                operation.getId(), career.getId(), request.sourceVersion(), json(archived), importedAt);
    }

    private void importTrips(CareerEntity career, Map<String, Object> state, Instant importedAt) {
        List<?> trips = list(state, "trips");
        for (int index = 0; index < trips.size(); index++) {
            Map<String, Object> trip = object(trips.get(index), "state.trips[" + index + "]");
            int week = positiveInt(trip.get("week"), "state.trips[" + index + "].week");
            TemporalParts temporal = temporalParts(trip, index);
            String type = tripType(trip.get("type"));
            String paymentCategory = paymentCategory(trip.get("payCategory"), type);
            BigDecimal distance = positiveMoney(firstNonNull(trip.get("distance"), trip.get("miles")),
                    "state.trips[" + index + "].distance");
            Integer breakMinutes = nullableNonNegativeInt(trip.get("breakMinutes"),
                    "state.trips[" + index + "].breakMinutes");
            BigDecimal odometerStart = nullableDecimal(trip.get("odometerStart"),
                    "state.trips[" + index + "].odometerStart");
            BigDecimal odometerEnd = nullableDecimal(trip.get("odometerEnd"),
                    "state.trips[" + index + "].odometerEnd");
            if ((odometerStart == null) != (odometerEnd == null)
                    || (odometerStart != null && (odometerStart.signum() < 0 || odometerEnd.compareTo(odometerStart) < 0))) {
                throw invalid("state.trips[" + index + "] odometer values must form a non-negative ordered pair");
            }
            String employerSnapshot = employerSnapshot(career, trip);
            String baseSnapshot = baseSnapshot(career, trip, temporal.derivedFromLegacyTimestamp());
            Instant recorded = technicalInstant(firstNonNull(trip.get("createdAt"), trip.get("recordedAt")), importedAt);
            jdbc.update("""
                    INSERT INTO trips (
                        id, career_id, operational_week, departure_day, departure_time, arrival_day, arrival_time,
                        origin_city, origin_company, destination_city, destination_company, cargo, trip_type,
                        payment_category, official_distance, break_minutes, truck_make, truck_model,
                        odometer_start, odometer_end, source, employer_snapshot_json, base_snapshot_json,
                        created_at, updated_at, version
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                    """,
                    UUID.randomUUID(), career.getId(), week,
                    temporal.departureDay().name(), Time.valueOf(temporal.departureTime()),
                    temporal.arrivalDay().name(), Time.valueOf(temporal.arrivalTime()),
                    requiredText(trip.get("origin"), "state.trips[" + index + "].origin"),
                    optionalText(trip.get("originCompany")),
                    requiredText(trip.get("destination"), "state.trips[" + index + "].destination"),
                    optionalText(trip.get("destinationCompany")), optionalText(trip.get("cargo")), type,
                    paymentCategory, distance, breakMinutes,
                    optionalText(trip.get("truckMake")), optionalText(trip.get("truckModel")),
                    odometerStart, odometerEnd, tripSource(trip.get("source")), employerSnapshot, baseSnapshot,
                    recorded, recorded);
        }
    }

    private Map<Integer, UUID> importPayrollPeriods(
            CareerEntity career,
            Map<String, Object> state,
            Instant importedAt
    ) {
        Map<Integer, Map<String, Object>> periodSnapshotByWeek = new HashMap<>();
        Map<Integer, Integer> monthByWeek = new HashMap<>();
        Set<Integer> closedWeeks = new LinkedHashSet<>();
        for (Object value : list(state, "closedOperationalWeeks")) {
            closedWeeks.add(positiveInt(value, "state.closedOperationalWeeks"));
        }
        for (int index = 0; index < list(state, "closedWeeks").size(); index++) {
            Map<String, Object> closed = object(list(state, "closedWeeks").get(index), "state.closedWeeks[" + index + "]");
            List<Integer> weeks = periodWeeks(closed, index);
            closedWeeks.addAll(weeks);
            Integer month = career.getGame() == CareerGame.ETS2
                    ? positiveInt(firstNonNull(closed.get("month"), career.getCurrentPayrollMonth()),
                            "state.closedWeeks[" + index + "].month")
                    : null;
            for (Integer week : weeks) {
                periodSnapshotByWeek.putIfAbsent(week, closed);
                if (month != null) {
                    monthByWeek.put(week, month);
                }
            }
        }
        Map<Integer, UUID> ids = new HashMap<>();
        for (Integer week : closedWeeks.stream().sorted().toList()) {
            UUID id = UUID.randomUUID();
            ids.put(week, id);
            Map<String, Object> raw = periodSnapshotByWeek.get(week);
            Integer payrollMonth = career.getGame() == CareerGame.ETS2
                    ? monthByWeek.getOrDefault(week, career.getCurrentPayrollMonth())
                    : null;
            Map<String, Object> context = new LinkedHashMap<>();
            context.put("imported", true);
            context.put("sourceVersion", 12);
            context.put("operationalWeek", week);
            context.put("legacyPeriod", raw == null ? Map.of() : raw);
            Instant closedAt = raw == null ? importedAt : technicalInstant(raw.get("closedAt"), importedAt);
            jdbc.update("""
                    INSERT INTO payroll_periods
                        (id, career_id, operational_week, payroll_month, context_snapshot_json, closed_at)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, id, career.getId(), week, payrollMonth, json(context), closedAt);
        }
        return ids;
    }

    private void importPayslips(
            CareerEntity career,
            Map<String, Object> state,
            Map<Integer, UUID> periodIds,
            Instant importedAt
    ) {
        List<?> closedPeriods = list(state, "closedWeeks");
        for (int index = 0; index < closedPeriods.size(); index++) {
            Map<String, Object> closed = object(closedPeriods.get(index), "state.closedWeeks[" + index + "]");
            List<Integer> weeks = periodWeeks(closed, index);
            int startWeek = positiveInt(firstNonNull(closed.get("startWeek"), weeks.getFirst()),
                    "state.closedWeeks[" + index + "].startWeek");
            int endWeek = positiveInt(firstNonNull(closed.get("endWeek"), weeks.getLast()),
                    "state.closedWeeks[" + index + "].endWeek");
            Integer operationalWeek;
            Integer payrollMonth;
            if (career.getGame() == CareerGame.ATS) {
                operationalWeek = positiveInt(firstNonNull(closed.get("week"), endWeek),
                        "state.closedWeeks[" + index + "].week");
                payrollMonth = null;
                startWeek = operationalWeek;
                endWeek = operationalWeek;
            } else {
                operationalWeek = null;
                payrollMonth = positiveInt(firstNonNull(closed.get("month"), career.getCurrentPayrollMonth()),
                        "state.closedWeeks[" + index + "].month");
            }
            short level = (short) rangedInt(firstNonNull(closed.get("level"), career.getCurrentLevel()), 1, 3,
                    "state.closedWeeks[" + index + "].level");
            String currency = currency(firstNonNull(closed.get("currency"), career.getDisplayCurrency()),
                    "state.closedWeeks[" + index + "].currency");
            BigDecimal gross = nonNegativeMoney(closed.get("gross"), "state.closedWeeks[" + index + "].gross");
            BigDecimal taxes = nonNegativeMoney(firstNonNull(closed.get("taxes"), BigDecimal.ZERO),
                    "state.closedWeeks[" + index + "].taxes");
            BigDecimal benefits = nonNegativeMoney(firstNonNull(closed.get("benefits"), BigDecimal.ZERO),
                    "state.closedWeeks[" + index + "].benefits");
            BigDecimal perDiem = nonNegativeMoney(firstNonNull(closed.get("perDiem"), BigDecimal.ZERO),
                    "state.closedWeeks[" + index + "].perDiem");
            BigDecimal netSalary = nonNegativeMoney(firstNonNull(closed.get("netSalary"), gross.subtract(taxes).subtract(benefits).max(BigDecimal.ZERO)),
                    "state.closedWeeks[" + index + "].netSalary");
            BigDecimal incidentDeduction = nonNegativeMoney(firstNonNull(closed.get("incidentDeduction"), BigDecimal.ZERO),
                    "state.closedWeeks[" + index + "].incidentDeduction");
            BigDecimal deposit = nonNegativeMoney(firstNonNull(closed.get("deposit"), netSalary.add(perDiem).subtract(incidentDeduction).max(BigDecimal.ZERO)),
                    "state.closedWeeks[" + index + "].deposit");
            BigDecimal reserveInterest = nonNegativeMoney(firstNonNull(closed.get("reserveInterest"), BigDecimal.ZERO),
                    "state.closedWeeks[" + index + "].reserveInterest");
            BigDecimal distance = nonNegativeMoney(firstNonNull(closed.get("distance"), closed.get("miles"), BigDecimal.ZERO),
                    "state.closedWeeks[" + index + "].distance");
            int elapsedMinutes = nonNegativeInt(firstNonNull(closed.get("routeElapsedMinutes"), 0),
                    "state.closedWeeks[" + index + "].routeElapsedMinutes");
            int breakMinutes = nonNegativeInt(firstNonNull(closed.get("routeBreakMinutes"), 0),
                    "state.closedWeeks[" + index + "].routeBreakMinutes");
            int workedMinutes = nonNegativeInt(firstNonNull(closed.get("routeWorkedMinutes"), Math.max(0, elapsedMinutes - breakMinutes)),
                    "state.closedWeeks[" + index + "].routeWorkedMinutes");
            int overrunMinutes = legacyOverrunMinutes(closed, index);
            if (breakMinutes > elapsedMinutes || workedMinutes > elapsedMinutes) {
                throw invalid("state.closedWeeks[" + index + "] route minute totals are inconsistent");
            }
            UUID payslipId = UUID.randomUUID();
            Map<String, Object> context = new LinkedHashMap<>();
            context.put("imported", true);
            context.put("sourceVersion", 12);
            context.put("reserveContributionRecoverable", false);
            context.put("legacySnapshot", closed);
            Instant generatedAt = technicalInstant(closed.get("closedAt"), importedAt);
            jdbc.update("""
                    INSERT INTO payslips (
                        id, career_id, game_id, operational_week, payroll_month, start_operational_week,
                        end_operational_week, level, display_currency, gross_amount, tax_amount, benefits_amount,
                        per_diem_amount, net_salary_amount, deposit_amount, total_distance, elapsed_minutes,
                        break_minutes, worked_minutes, overrun_minutes, context_snapshot_json, generated_at,
                        incident_deduction_amount, reserve_interest_amount, reserve_contribution_amount,
                        balance_credit_amount
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?)
                    """,
                    payslipId, career.getId(), career.getGame().name(), operationalWeek, payrollMonth,
                    startWeek, endWeek, level, currency, gross, taxes, benefits, perDiem, netSalary, deposit,
                    distance, elapsedMinutes, breakMinutes, workedMinutes, overrunMinutes, json(context), generatedAt,
                    incidentDeduction, reserveInterest, deposit);
            importPayslipLines(payslipId, closed, gross, taxes, benefits, perDiem, incidentDeduction);
            for (Integer week : weeks) {
                UUID periodId = periodIds.get(week);
                if (periodId != null) {
                    jdbc.update("UPDATE payroll_periods SET payslip_id=? WHERE id=?", payslipId, periodId);
                }
            }
        }
    }

    private void importPayslipLines(
            UUID payslipId,
            Map<String, Object> closed,
            BigDecimal gross,
            BigDecimal taxes,
            BigDecimal benefits,
            BigDecimal perDiem,
            BigDecimal incidentDeduction
    ) {
        int order = 1;
        order = insertLine(payslipId, order, "IMPORTED_GROSS", "Remuneração bruta importada", "EARNING", gross, Map.of("imported", true));
        if (perDiem.signum() > 0) {
            order = insertLine(payslipId, order, "PER_DIEM", "Per diem", "EARNING", perDiem, Map.of("imported", true));
        }
        Object breakdownValue = closed.get("taxBreakdown");
        if (breakdownValue instanceof Map<?, ?> rawBreakdown && !rawBreakdown.isEmpty()) {
            for (Map.Entry<?, ?> entry : rawBreakdown.entrySet()) {
                BigDecimal amount = nullableDecimal(entry.getValue(), "taxBreakdown." + entry.getKey());
                if (amount != null && amount.signum() > 0) {
                    order = insertLine(payslipId, order, "TAX_" + sanitizeCode(entry.getKey()),
                            String.valueOf(entry.getKey()), "DEDUCTION", amount.abs(), Map.of("imported", true));
                }
            }
        } else if (taxes.signum() > 0) {
            order = insertLine(payslipId, order, "TAXES", "Impostos", "DEDUCTION", taxes, Map.of("imported", true));
        }
        if (benefits.signum() > 0) {
            order = insertLine(payslipId, order, "BENEFITS", "Benefícios", "DEDUCTION", benefits, Map.of("imported", true));
        }
        if (incidentDeduction.signum() > 0) {
            insertLine(payslipId, order, "INCIDENTS", "Descontos de ocorrências", "DEDUCTION", incidentDeduction, Map.of("imported", true));
        }
    }

    private int insertLine(
            UUID payslipId,
            int order,
            String code,
            String label,
            String lineType,
            BigDecimal amount,
            Map<String, Object> metadata
    ) {
        jdbc.update("""
                INSERT INTO payslip_lines
                    (id, payslip_id, line_order, code, label, line_type, amount, quantity, rate, metadata_json)
                VALUES (?, ?, ?, ?, ?, ?, ?, NULL, NULL, ?)
                """, UUID.randomUUID(), payslipId, order, code, label, lineType, amount, json(metadata));
        return order + 1;
    }

    private void importIncidents(CareerEntity career, Map<String, Object> state, Instant importedAt) {
        List<?> incidents = list(state, "incidents");
        for (int index = 0; index < incidents.size(); index++) {
            Map<String, Object> incident = object(incidents.get(index), "state.incidents[" + index + "]");
            int week = positiveInt(firstNonNull(incident.get("week"), career.getCurrentOperationalWeek()),
                    "state.incidents[" + index + "].week");
            BigDecimal amount = positiveMoney(firstNonNull(incident.get("amount"), incident.get("value")),
                    "state.incidents[" + index + "].amount");
            String method = incidentChargeMethod(incident.get("chargeMethod"));
            BigDecimal remaining = method.equals("BALANCE")
                    ? BigDecimal.ZERO.setScale(2)
                    : nonNegativeMoney(firstNonNull(incident.get("remaining"), amount),
                            "state.incidents[" + index + "].remaining");
            if (remaining.compareTo(amount) > 0) {
                throw invalid("state.incidents[" + index + "].remaining cannot exceed amount");
            }
            String status = incidentStatus(method, amount, remaining, incident.get("status"));
            Instant recordedAt = technicalInstant(firstNonNull(incident.get("recordedAt"), incident.get("createdAt")), importedAt);
            jdbc.update("""
                    INSERT INTO incidents (
                        id, career_id, related_trip_id, operational_week, incident_type, amount, remaining_amount,
                        route_label, description, charge_method, status, recorded_at, updated_at, version
                    ) VALUES (?, ?, NULL, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                    """,
                    UUID.randomUUID(), career.getId(), week, incidentType(incident.get("type")), amount, remaining,
                    textOrFallback(incident.get("route"), "Rota não informada no legado"),
                    textOrFallback(incident.get("description"), "Ocorrência importada do snapshot local"),
                    method, status, recordedAt, recordedAt);
        }
    }

    private void importCurrentFinance(CareerEntity career, Map<String, Object> state, Instant importedAt) {
        Object expensesValue = state.get("expenses");
        if (expensesValue instanceof Map<?, ?> expenses) {
            for (Map.Entry<String, String> mapping : EXPENSE_CATEGORIES.entrySet()) {
                Object rawAmount = expenses.get(mapping.getKey());
                if (rawAmount == null) {
                    continue;
                }
                BigDecimal amount = nonNegativeMoney(rawAmount, "state.expenses." + mapping.getKey());
                insertExpense(career, "STANDARD", mapping.getValue(), mapping.getKey(), amount, true, importedAt,
                        Map.of("imported", true, "sourceVersion", 12));
            }
        }
        List<?> custom = list(state, "customExpenses");
        for (int index = 0; index < custom.size(); index++) {
            Map<String, Object> expense = object(custom.get(index), "state.customExpenses[" + index + "]");
            BigDecimal amount = nonNegativeMoney(firstNonNull(expense.get("value"), expense.get("amount"), BigDecimal.ZERO),
                    "state.customExpenses[" + index + "].value");
            boolean included = booleanValue(firstNonNull(expense.get("monthly"), Boolean.TRUE));
            insertExpense(career, "CUSTOM", null,
                    textOrFallback(firstNonNull(expense.get("name"), expense.get("label")), "Despesa importada"),
                    amount, included, importedAt, Map.of("imported", true, "legacyExpense", expense));
        }
        BigDecimal reserve = nonNegativeMoney(firstNonNull(state.get("emergencyReserve"), BigDecimal.ZERO),
                "state.emergencyReserve");
        boolean autoEnabled = false;
        BigDecimal autoAmount = BigDecimal.ZERO.setScale(2);
        if (state.get("autoReserveContribution") instanceof Map<?, ?> auto) {
            autoEnabled = booleanValue(auto.get("enabled"));
            autoAmount = nonNegativeMoney(firstNonNull(auto.get("amount"), BigDecimal.ZERO),
                    "state.autoReserveContribution.amount");
        }
        jdbc.update("""
                INSERT INTO emergency_reserve (
                    career_id, balance, annual_yield_rate, auto_contribution_enabled, auto_contribution_amount,
                    display_currency, policy_version, updated_at, version
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0)
                """, career.getId(), reserve, RESERVE_ANNUAL_YIELD, autoEnabled, autoAmount,
                career.getDisplayCurrency(), IMPORT_POLICY, importedAt);
    }

    private void insertExpense(
            CareerEntity career,
            String type,
            String category,
            String name,
            BigDecimal amount,
            boolean included,
            Instant importedAt,
            Map<String, Object> context
    ) {
        jdbc.update("""
                INSERT INTO monthly_expenses (
                    id, career_id, expense_type, category, name, amount, included, display_currency,
                    policy_version, context_snapshot_json, created_at, updated_at, version
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                """, UUID.randomUUID(), career.getId(), type, category, name, amount, included,
                career.getDisplayCurrency(), IMPORT_POLICY, json(context), importedAt, importedAt);
    }

    private void importOpeningLedger(CareerEntity career, Map<String, Object> state, Instant importedAt) {
        BigDecimal balance = career.getBalance().setScale(2, RoundingMode.HALF_UP);
        BigDecimal reserve = nonNegativeMoney(firstNonNull(state.get("emergencyReserve"), BigDecimal.ZERO),
                "state.emergencyReserve");
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("imported", true);
        metadata.put("sourceVersion", 12);
        metadata.put("migrationBoundary", true);
        metadata.put("legacyHistoryArchived", true);
        metadata.put("reserveOpeningIncluded", true);
        jdbc.update("""
                INSERT INTO ledger_entries (
                    id, career_id, entry_type, source_type, source_id, entry_order, operational_week,
                    payroll_month, amount, balance_delta, reserve_delta, balance_before, balance_after,
                    reserve_balance_before, reserve_balance_after, display_currency, description,
                    metadata_json, recorded_at
                ) VALUES (?, ?, 'OPENING_BALANCE', 'CAREER', ?, 0, ?, ?, ?, ?, ?, 0, ?, 0, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(), career.getId(), career.getId(), career.getCurrentOperationalWeek(),
                career.getGame() == CareerGame.ETS2 ? career.getCurrentPayrollMonth() : null,
                balance, balance, reserve, balance, reserve, career.getDisplayCurrency(),
                "Saldo de abertura importado da carreira local", json(metadata), importedAt);
    }

    private String employerSnapshot(CareerEntity career, Map<String, Object> trip) {
        Object value = trip.get("employer");
        if (value instanceof Map<?, ?> map) {
            return json(map);
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("companyName", value == null ? career.getCompanyName() : String.valueOf(value));
        snapshot.put("importedFallback", value == null);
        return json(snapshot);
    }

    private String baseSnapshot(CareerEntity career, Map<String, Object> trip, boolean derivedTimestamp) {
        Object value = trip.get("baseSnapshot");
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> snapshot = new LinkedHashMap<>();
            map.forEach((key, item) -> snapshot.put(String.valueOf(key), item));
            if (derivedTimestamp) {
                snapshot.put("tripWeekdayTimeDerivedFromLegacyTimestamp", true);
            }
            return json(snapshot);
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("city", career.getBaseCity());
        snapshot.put("stateCode", career.getStateCode());
        snapshot.put("countryCode", career.getCountryCode());
        snapshot.put("currency", career.getDisplayCurrency());
        snapshot.put("baseCurrency", career.getBaseCurrency());
        snapshot.put("exchangeRate", career.getExchangeRate());
        snapshot.put("exchangeRateAsOf", career.getExchangeRateAsOf());
        snapshot.put("cityMarketVersion", career.getCityMarketVersion());
        snapshot.put("cityMarketLabel", career.getCityMarketLabel());
        snapshot.put("cityCostFactor", career.getCityCostFactor());
        snapshot.put("citySalaryFactor", career.getCitySalaryFactor());
        snapshot.put("importedFallback", true);
        snapshot.put("tripWeekdayTimeDerivedFromLegacyTimestamp", derivedTimestamp);
        return json(snapshot);
    }

    private TemporalParts temporalParts(Map<String, Object> trip, int index) {
        String departureDay = optionalText(trip.get("departureDay"));
        String departureTime = optionalText(trip.get("departureTime"));
        String arrivalDay = optionalText(trip.get("arrivalDay"));
        String arrivalTime = optionalText(trip.get("arrivalTime"));
        if (departureDay != null && departureTime != null && arrivalDay != null && arrivalTime != null) {
            try {
                return new TemporalParts(day(departureDay), LocalTime.parse(departureTime), day(arrivalDay), LocalTime.parse(arrivalTime), false);
            } catch (DateTimeParseException | IllegalArgumentException exception) {
                throw invalid("state.trips[" + index + "] contains invalid weekday/time values");
            }
        }
        try {
            OffsetDateTime departure = OffsetDateTime.parse(requiredText(trip.get("departureAt"),
                    "state.trips[" + index + "].departureAt"));
            OffsetDateTime arrival = OffsetDateTime.parse(requiredText(trip.get("arrivalAt"),
                    "state.trips[" + index + "].arrivalAt"));
            return new TemporalParts(departure.getDayOfWeek(), departure.toLocalTime().withSecond(0).withNano(0),
                    arrival.getDayOfWeek(), arrival.toLocalTime().withSecond(0).withNano(0), true);
        } catch (DateTimeParseException exception) {
            throw invalid("state.trips[" + index + "] must provide operational weekday/time or parseable legacy timestamps");
        }
    }

    private List<Integer> periodWeeks(Map<String, Object> closed, int index) {
        List<Integer> weeks = new ArrayList<>();
        Object raw = closed.get("weeks");
        if (raw instanceof List<?> list) {
            for (Object value : list) {
                weeks.add(positiveInt(value, "state.closedWeeks[" + index + "].weeks"));
            }
        }
        if (weeks.isEmpty() && closed.get("week") != null) {
            weeks.add(positiveInt(closed.get("week"), "state.closedWeeks[" + index + "].week"));
        }
        if (weeks.isEmpty()) {
            throw invalid("state.closedWeeks[" + index + "] must identify at least one operational week");
        }
        return weeks.stream().distinct().sorted().toList();
    }

    private int legacyOverrunMinutes(Map<String, Object> closed, int index) {
        if (closed.get("routeOverrunMinutes") != null) {
            return nonNegativeInt(closed.get("routeOverrunMinutes"), "state.closedWeeks[" + index + "].routeOverrunMinutes");
        }
        BigDecimal hours = nullableDecimal(closed.get("routeOverrunHours"), "state.closedWeeks[" + index + "].routeOverrunHours");
        return hours == null ? 0 : hours.multiply(BigDecimal.valueOf(60)).setScale(0, RoundingMode.HALF_UP).intValueExact();
    }

    private String tripType(Object value) {
        String text = String.valueOf(value == null ? "Loaded" : value).strip().toUpperCase(Locale.ROOT);
        return text.equals("DEADHEAD") || text.equals("EMPTY") ? "DEADHEAD" : "LOADED";
    }

    private String paymentCategory(Object value, String tripType) {
        if (tripType.equals("DEADHEAD")) {
            return "DEADHEAD";
        }
        String text = String.valueOf(value == null ? "normal" : value).strip().toUpperCase(Locale.ROOT).replace('-', '_');
        return switch (text) {
            case "NORMAL", "STANDARD" -> "NORMAL";
            case "HAZMAT", "ADR" -> "HAZMAT";
            case "DOUBLES", "EURO_COMBI" -> "DOUBLES";
            case "HAZMAT_DOUBLES", "ADR_EURO_COMBI" -> "HAZMAT_DOUBLES";
            default -> throw invalid("Unsupported imported trip payment category: " + text);
        };
    }

    private String tripSource(Object value) {
        String text = String.valueOf(value == null ? "IMPORT" : value).strip().toUpperCase(Locale.ROOT);
        return switch (text) {
            case "MANUAL", "TELEMETRY", "IMPORT" -> text;
            default -> "IMPORT";
        };
    }

    private String incidentType(Object value) {
        String text = String.valueOf(value == null ? "Outra" : value).strip().toLowerCase(Locale.ROOT);
        if (text.contains("infra") || text.contains("fine")) return "INFRACTION";
        if (text.contains("acidente") || text.contains("accident")) return "ACCIDENT";
        if (text.contains("pedágio") || text.contains("pedagio") || text.contains("toll")) return "TOLL_CHARGE";
        return "OTHER";
    }

    private String incidentChargeMethod(Object value) {
        String text = String.valueOf(value == null ? "balance" : value).strip().toUpperCase(Locale.ROOT);
        return text.equals("PAYSLIP") ? "PAYSLIP" : "BALANCE";
    }

    private String incidentStatus(String method, BigDecimal amount, BigDecimal remaining, Object legacyStatus) {
        String legacy = String.valueOf(legacyStatus == null ? "" : legacyStatus).toLowerCase(Locale.ROOT);
        if (legacy.contains("cancel")) return "CANCELLED";
        if (method.equals("BALANCE")) return "PAID_BALANCE";
        if (remaining.signum() == 0) return "DEDUCTED_PAYSLIP";
        if (remaining.compareTo(amount) == 0) return "PENDING_PAYSLIP";
        return "PARTIALLY_DEDUCTED";
    }

    private String sanitizeCode(Object value) {
        String result = String.valueOf(value).toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_");
        return result.isBlank() ? "LEGACY" : result.substring(0, Math.min(48, result.length()));
    }

    private DayOfWeek day(String value) {
        return DayOfWeek.valueOf(value.strip().toUpperCase(Locale.ROOT));
    }

    private Instant technicalInstant(Object value, Instant fallback) {
        if (value == null || String.valueOf(value).isBlank()) {
            return fallback;
        }
        String text = String.valueOf(value).strip();
        try {
            return Instant.parse(text);
        } catch (DateTimeParseException ignored) {
            try {
                return OffsetDateTime.parse(text).toInstant();
            } catch (DateTimeParseException ignoredAgain) {
                return fallback;
            }
        }
    }

    private List<?> list(Map<String, Object> source, String key) {
        Object value = source.get(key);
        if (value == null) return List.of();
        if (!(value instanceof List<?> result)) throw invalid("state." + key + " must be an array");
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> object(Object value, String label) {
        if (!(value instanceof Map<?, ?> map)) throw invalid(label + " must be an object");
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private int positiveInt(Object value, String label) {
        int result = integer(value, label);
        if (result < 1) throw invalid(label + " must be greater than zero");
        return result;
    }

    private int nonNegativeInt(Object value, String label) {
        int result = integer(value, label);
        if (result < 0) throw invalid(label + " cannot be negative");
        return result;
    }

    private Integer nullableNonNegativeInt(Object value, String label) {
        if (value == null || String.valueOf(value).isBlank()) return null;
        return nonNegativeInt(value, label);
    }

    private int rangedInt(Object value, int min, int max, String label) {
        int result = integer(value, label);
        if (result < min || result > max) throw invalid(label + " must be between " + min + " and " + max);
        return result;
    }

    private int integer(Object value, String label) {
        try {
            return decimal(value, label).intValueExact();
        } catch (ArithmeticException exception) {
            throw invalid(label + " must be an integer");
        }
    }

    private BigDecimal positiveMoney(Object value, String label) {
        BigDecimal result = money(value, label);
        if (result.signum() <= 0) throw invalid(label + " must be greater than zero");
        return result;
    }

    private BigDecimal nonNegativeMoney(Object value, String label) {
        BigDecimal result = money(value, label);
        if (result.signum() < 0) throw invalid(label + " cannot be negative");
        return result;
    }

    private BigDecimal money(Object value, String label) {
        return decimal(value, label).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal nullableDecimal(Object value, String label) {
        if (value == null || String.valueOf(value).isBlank()) return null;
        return decimal(value, label);
    }

    private BigDecimal decimal(Object value, String label) {
        if (value == null) throw invalid(label + " is required");
        try {
            return value instanceof BigDecimal decimal ? decimal : new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException exception) {
            throw invalid(label + " must be numeric");
        }
    }

    private String currency(Object value, String label) {
        String result = requiredText(value, label).toUpperCase(Locale.ROOT);
        if (!result.matches("[A-Z]{3}")) throw invalid(label + " must be a 3-letter currency code");
        return result;
    }

    private String requiredText(Object value, String label) {
        String text = value == null ? "" : String.valueOf(value).strip();
        if (text.isEmpty()) throw invalid(label + " is required");
        return text;
    }

    private String optionalText(Object value) {
        if (value == null) return null;
        String text = String.valueOf(value).strip();
        return text.isEmpty() ? null : text;
    }

    private String textOrFallback(Object value, String fallback) {
        String text = optionalText(value);
        return text == null ? fallback : text;
    }

    private boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) return bool;
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }

    private Object firstNonNull(Object... values) {
        for (Object value : values) if (value != null) return value;
        return null;
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Imported career snapshot could not be serialized", exception);
        }
    }

    private ApiProblemException invalid(String detail) {
        return new ApiProblemException(
                HttpStatus.BAD_REQUEST,
                "CAREER_IMPORT_INVALID",
                "Career import snapshot is invalid",
                detail
        );
    }

    private record TemporalParts(
            DayOfWeek departureDay,
            LocalTime departureTime,
            DayOfWeek arrivalDay,
            LocalTime arrivalTime,
            boolean derivedFromLegacyTimestamp
    ) {}
}
