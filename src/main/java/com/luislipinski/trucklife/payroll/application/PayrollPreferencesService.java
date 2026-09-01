package com.luislipinski.trucklife.payroll.application;

import com.luislipinski.trucklife.career.domain.CareerGame;
import com.luislipinski.trucklife.career.persistence.CareerEntity;
import com.luislipinski.trucklife.career.persistence.CareerRepository;
import com.luislipinski.trucklife.incident.domain.IncidentChargeMethod;
import com.luislipinski.trucklife.incident.persistence.IncidentRepository;
import com.luislipinski.trucklife.payroll.persistence.PayrollPeriodEntity;
import com.luislipinski.trucklife.payroll.persistence.PayrollPeriodRepository;
import com.luislipinski.trucklife.shared.error.ApiProblemException;
import com.luislipinski.trucklife.shared.error.ResourceNotFoundException;
import com.luislipinski.trucklife.trip.persistence.TripEntity;
import com.luislipinski.trucklife.trip.persistence.TripRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class PayrollPreferencesService {
    private static final int ETS2_MIN_WEEKS = 4;
    private static final int ETS2_MAX_WEEKS = 5;
    private static final TypeReference<Map<String,Object>> SNAPSHOT_TYPE = new TypeReference<>() {};

    private final CareerRepository careerRepository;
    private final PayrollPeriodRepository payrollPeriodRepository;
    private final TripRepository tripRepository;
    private final IncidentRepository incidentRepository;
    private final PayrollCalculator calculator;
    private final PayrollContextSnapshotFactory contextSnapshotFactory;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public PayrollPreferencesService(CareerRepository careerRepository,
                                     PayrollPeriodRepository payrollPeriodRepository,
                                     TripRepository tripRepository,
                                     IncidentRepository incidentRepository,
                                     PayrollCalculator calculator,
                                     PayrollContextSnapshotFactory contextSnapshotFactory,
                                     ObjectMapper objectMapper,
                                     Clock clock) {
        this.careerRepository = careerRepository;
        this.payrollPeriodRepository = payrollPeriodRepository;
        this.tripRepository = tripRepository;
        this.incidentRepository = incidentRepository;
        this.calculator = calculator;
        this.contextSnapshotFactory = contextSnapshotFactory;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public SettingsState getSettings(UUID userId, CareerGame game, UUID careerId) {
        CareerEntity career = ownedCareer(userId, game, careerId);
        return settingsState(career);
    }

    @Transactional
    public SettingsState updateSettings(UUID userId, CareerGame game, UUID careerId,
                                        int expectedOperationalWeek, Integer expectedPayrollMonth,
                                        BigDecimal level1Gross, BigDecimal routeOverrunRate,
                                        BigDecimal benefits, BigDecimal perDiemRate) {
        CareerEntity career = lockedCareer(userId, game, careerId);
        validateContext(career, expectedOperationalWeek, expectedPayrollMonth);
        if (game == CareerGame.ETS2 && !currentMonthPeriods(career).isEmpty()) {
            throw conflict("PAYROLL_SETTINGS_MONTH_STARTED", "Payroll month already started",
                    "ETS2 payroll preferences can be changed before closing the first week of the operational payroll month");
        }
        career.updatePayrollSettings(money(level1Gross), money(routeOverrunRate), money(benefits), money(perDiemRate), clock.instant());
        careerRepository.flush();
        return settingsState(career);
    }

    @Transactional(readOnly = true)
    public Preview preview(UUID userId, CareerGame game, UUID careerId) {
        CareerEntity career = ownedCareer(userId, game, careerId);
        return game == CareerGame.ATS ? previewAts(career) : previewEts2(career);
    }

    private Preview previewAts(CareerEntity career) {
        int week = career.getCurrentOperationalWeek();
        Map<String,Object> context = contextSnapshotFactory.from(career);
        List<TripEntity> trips = tripRepository.findAllByCareerIdAndOperationalWeekOrderByCreatedAtAscIdAsc(career.getId(), week);
        PayrollCalculator.Calculation calculation = calculator.calculate(CareerGame.ATS, contextFrom(context), trips);
        BigDecimal incidents = pendingIncidentDeduction(career.getId(), week, calculation.deposit());
        return Preview.from(true, week, null, List.of(week), calculation, incidents, context);
    }

    private Preview previewEts2(CareerEntity career) {
        Integer month = career.getCurrentPayrollMonth();
        if (month == null || month < 1) throw new IllegalStateException("ETS2 career must have a current payroll month");
        List<PayrollPeriodEntity> periods = currentMonthPeriods(career);
        boolean ready = periods.size() >= ETS2_MIN_WEEKS && periods.size() <= ETS2_MAX_WEEKS;
        List<Integer> weeks = periods.stream().map(PayrollPeriodEntity::getOperationalWeek).toList();
        Map<String,Object> context = periods.isEmpty()
                ? contextSnapshotFactory.from(career)
                : snapshot(periods.getLast().getContextSnapshotJson());
        List<TripEntity> trips = periods.isEmpty()
                ? List.of()
                : tripRepository.findAllByCareerIdOrderByOperationalWeekAscCreatedAtAscIdAsc(career.getId()).stream()
                    .filter(trip -> weeks.contains(trip.getOperationalWeek())).toList();
        PayrollCalculator.Calculation calculation = calculator.calculate(CareerGame.ETS2, contextFrom(context), trips);
        int eligibleWeek = weeks.isEmpty() ? Math.max(1, career.getCurrentOperationalWeek() - 1) : weeks.getLast();
        BigDecimal incidents = pendingIncidentDeduction(career.getId(), eligibleWeek, calculation.deposit());
        return Preview.from(ready, null, month, weeks, calculation, incidents, context);
    }

    private SettingsState settingsState(CareerEntity career) {
        PayrollCalculator.Context context = contextFrom(contextSnapshotFactory.from(career));
        PayrollCalculator.Settings settings = calculator.settings(career.getGame(), context);
        boolean editable = career.getGame() == CareerGame.ATS || currentMonthPeriods(career).isEmpty();
        return new SettingsState(career.getId(), career.getGame(), career.getCurrentOperationalWeek(),
                career.getCurrentPayrollMonth(), career.getCurrentLevel(), career.getDisplayCurrency(), editable, settings);
    }

    private List<PayrollPeriodEntity> currentMonthPeriods(CareerEntity career) {
        if (career.getGame() != CareerGame.ETS2 || career.getCurrentPayrollMonth() == null) return List.of();
        return payrollPeriodRepository.findAllByCareerIdOrderByOperationalWeekAsc(career.getId()).stream()
                .filter(period -> career.getCurrentPayrollMonth().equals(period.getPayrollMonth())).toList();
    }

    private BigDecimal pendingIncidentDeduction(UUID careerId, int eligibleThroughWeek, BigDecimal availableAmount) {
        BigDecimal available = availableAmount.max(BigDecimal.ZERO);
        BigDecimal total = BigDecimal.ZERO;
        var pending = incidentRepository.findAllByCareerIdAndChargeMethodAndRemainingAmountGreaterThanOrderByRecordedAtAscIdAsc(
                careerId, IncidentChargeMethod.PAYSLIP, BigDecimal.ZERO);
        for (var incident : pending) {
            if (incident.getOperationalWeek() > eligibleThroughWeek || available.signum() <= 0) continue;
            BigDecimal amount = incident.getRemainingAmount().min(available);
            total = total.add(amount);
            available = available.subtract(amount);
        }
        return money(total);
    }

    private void validateContext(CareerEntity career, int expectedWeek, Integer expectedMonth) {
        if (expectedWeek != career.getCurrentOperationalWeek()) {
            throw conflict("PAYROLL_SETTINGS_WEEK_CONFLICT", "Operational week changed",
                    "The requested operational week is no longer current");
        }
        if (career.getGame() == CareerGame.ETS2
                && (expectedMonth == null || !expectedMonth.equals(career.getCurrentPayrollMonth()))) {
            throw conflict("PAYROLL_SETTINGS_MONTH_CONFLICT", "Payroll month changed",
                    "The requested operational payroll month is no longer current");
        }
    }

    private PayrollCalculator.Context contextFrom(Map<String,Object> map) {
        return new PayrollCalculator.Context(
                (short) integer(map, "currentLevel", 1),
                string(map, "stateCode"), string(map, "countryCode"), string(map, "baseCurrency"),
                string(map, "displayCurrency"), decimal(map, "exchangeRate", BigDecimal.ONE),
                decimal(map, "citySalaryFactor", BigDecimal.ONE),
                decimalNullable(map, "payrollLevel1GrossOverride"),
                decimalNullable(map, "payrollRouteOverrunRateOverride"),
                decimalNullable(map, "payrollBenefitsOverride"),
                decimalNullable(map, "payrollPerDiemRateOverride")
        );
    }

    private Map<String,Object> snapshot(String json) {
        try { return objectMapper.readValue(json.getBytes(StandardCharsets.UTF_8), SNAPSHOT_TYPE); }
        catch (JacksonException ex) { throw new IllegalStateException("Payroll period snapshot could not be deserialized", ex); }
    }

    private CareerEntity ownedCareer(UUID userId, CareerGame game, UUID careerId) {
        return careerRepository.findByIdAndUserIdAndGame(careerId, userId, game)
                .orElseThrow(() -> new ResourceNotFoundException("CAREER_NOT_FOUND", "The requested career does not exist"));
    }
    private CareerEntity lockedCareer(UUID userId, CareerGame game, UUID careerId) {
        return careerRepository.findForUpdateByIdAndUserIdAndGame(careerId, userId, game)
                .orElseThrow(() -> new ResourceNotFoundException("CAREER_NOT_FOUND", "The requested career does not exist"));
    }
    private String string(Map<String,Object> map, String key) { Object v=map.get(key); return v==null?"":String.valueOf(v); }
    private int integer(Map<String,Object> map, String key, int fallback) { Object v=map.get(key); return v==null||String.valueOf(v).isBlank()?fallback:Integer.parseInt(String.valueOf(v)); }
    private BigDecimal decimal(Map<String,Object> map, String key, BigDecimal fallback) { BigDecimal v=decimalNullable(map,key); return v==null?fallback:v; }
    private BigDecimal decimalNullable(Map<String,Object> map, String key) { Object v=map.get(key); return v==null||String.valueOf(v).isBlank()?null:new BigDecimal(String.valueOf(v)); }
    private BigDecimal money(BigDecimal value) {
        if (value == null || value.signum() < 0) throw new IllegalArgumentException("Payroll preference must be non-negative");
        return value.setScale(2, RoundingMode.HALF_UP);
    }
    private ApiProblemException conflict(String code, String title, String detail) { return new ApiProblemException(HttpStatus.CONFLICT, code, title, detail); }

    public record SettingsState(UUID careerId, CareerGame game, int currentOperationalWeek, Integer currentPayrollMonth,
                                short currentLevel, String displayCurrency, boolean editable,
                                PayrollCalculator.Settings settings) {}

    public record Preview(boolean ready, Integer operationalWeek, Integer payrollMonth, List<Integer> weeks,
                          short level, String displayCurrency, BigDecimal grossAmount, BigDecimal taxAmount,
                          BigDecimal benefitsAmount, BigDecimal perDiemAmount, BigDecimal netSalaryAmount,
                          BigDecimal incidentDeductionAmount, BigDecimal depositAmount, BigDecimal totalDistance,
                          int elapsedMinutes, int breakMinutes, int workedMinutes, int overrunMinutes,
                          List<PayrollCalculator.DailyWorkBreakdown> dailyWorkBreakdown,
                          List<PayrollCalculator.Line> lines, Map<String,Object> contextSnapshot) {
        static Preview from(boolean ready, Integer week, Integer month, List<Integer> weeks,
                            PayrollCalculator.Calculation calculation, BigDecimal incidentDeduction,
                            Map<String,Object> context) {
            return new Preview(ready, week, month, List.copyOf(weeks), calculation.level(),
                    String.valueOf(context.getOrDefault("displayCurrency", "USD")), calculation.gross(),
                    calculation.taxTotal(), calculation.benefits(), calculation.perDiem(), calculation.netSalary(),
                    incidentDeduction, calculation.deposit().subtract(incidentDeduction).max(BigDecimal.ZERO),
                    calculation.totalDistance(), calculation.elapsedMinutes(), calculation.breakMinutes(),
                    calculation.workedMinutes(), calculation.overrunMinutes(), calculation.dailyWorkBreakdown(),
                    calculation.lines(), new LinkedHashMap<>(context));
        }
    }
}
