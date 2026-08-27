package com.luislipinski.trucklife.payroll.application;

import com.luislipinski.trucklife.career.domain.CareerGame;
import com.luislipinski.trucklife.career.persistence.CareerEntity;
import com.luislipinski.trucklife.career.persistence.CareerRepository;
import com.luislipinski.trucklife.payroll.persistence.PayrollPeriodEntity;
import com.luislipinski.trucklife.payroll.persistence.PayrollPeriodRepository;
import com.luislipinski.trucklife.payroll.persistence.PayslipEntity;
import com.luislipinski.trucklife.payroll.persistence.PayslipLineEntity;
import com.luislipinski.trucklife.payroll.persistence.PayslipLineRepository;
import com.luislipinski.trucklife.payroll.persistence.PayslipRepository;
import com.luislipinski.trucklife.shared.error.ApiProblemException;
import com.luislipinski.trucklife.shared.error.ResourceNotFoundException;
import com.luislipinski.trucklife.trip.persistence.TripEntity;
import com.luislipinski.trucklife.trip.persistence.TripRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
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
public class PayslipService implements PayslipOperations {
    private static final int ETS2_MIN_WEEKS_PER_PAYROLL_MONTH = 4;
    private static final int ETS2_MAX_WEEKS_PER_PAYROLL_MONTH = 5;
    private static final String POLICY_VERSION = "phase1-payroll-2026-v1";
    private static final TypeReference<Map<String, Object>> SNAPSHOT_TYPE = new TypeReference<>() {};

    private final CareerRepository careerRepository;
    private final PayrollPeriodRepository payrollPeriodRepository;
    private final PayslipRepository payslipRepository;
    private final PayslipLineRepository payslipLineRepository;
    private final TripRepository tripRepository;
    private final PayrollCalculator calculator;
    private final PayrollContextSnapshotFactory contextSnapshotFactory;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public PayslipService(
            CareerRepository careerRepository,
            PayrollPeriodRepository payrollPeriodRepository,
            PayslipRepository payslipRepository,
            PayslipLineRepository payslipLineRepository,
            TripRepository tripRepository,
            PayrollCalculator calculator,
            PayrollContextSnapshotFactory contextSnapshotFactory,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.careerRepository = careerRepository;
        this.payrollPeriodRepository = payrollPeriodRepository;
        this.payslipRepository = payslipRepository;
        this.payslipLineRepository = payslipLineRepository;
        this.tripRepository = tripRepository;
        this.calculator = calculator;
        this.contextSnapshotFactory = contextSnapshotFactory;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Result generate(
            UUID userId,
            CareerGame game,
            UUID careerId,
            Integer expectedOperationalWeek,
            Integer expectedPayrollMonth
    ) {
        CareerEntity career = lockedOwnedCareer(userId, game, careerId);
        try {
            return game == CareerGame.ATS
                    ? generateAts(career, expectedOperationalWeek)
                    : generateEts2(career, expectedPayrollMonth);
        } catch (IllegalArgumentException exception) {
            throw conflict("PAYSLIP_POLICY_UNAVAILABLE", "Payroll policy unavailable", exception.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<Result> list(UUID userId, CareerGame game, UUID careerId) {
        CareerEntity career = ownedCareer(userId, game, careerId);
        return payslipRepository.findAllByCareerIdOrderByGeneratedAtDescIdDesc(career.getId()).stream()
                .map(this::result)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Result get(UUID userId, CareerGame game, UUID careerId, UUID payslipId) {
        CareerEntity career = ownedCareer(userId, game, careerId);
        PayslipEntity payslip = payslipRepository.findByIdAndCareerId(payslipId, career.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "PAYSLIP_NOT_FOUND",
                        "The requested payslip does not exist"
                ));
        return result(payslip);
    }

    private Result generateAts(CareerEntity career, Integer expectedOperationalWeek) {
        if (expectedOperationalWeek == null) {
            throw badRequest(
                    "PAYSLIP_WEEK_REQUIRED",
                    "Operational week required",
                    "ATS payslip generation requires expectedOperationalWeek"
            );
        }
        int currentWeek = career.getCurrentOperationalWeek();
        if (expectedOperationalWeek != currentWeek) {
            throw conflict(
                    "PAYSLIP_WEEK_CONFLICT",
                    "Operational week changed",
                    "The requested week is no longer the career current operational week"
            );
        }
        if (payslipRepository.existsByCareerIdAndOperationalWeek(career.getId(), currentWeek)) {
            throw conflict(
                    "PAYSLIP_ALREADY_GENERATED",
                    "Payslip already generated",
                    "The ATS operational week already has a payslip"
            );
        }

        Map<String, Object> periodContext = contextSnapshotFactory.from(career);
        PayrollCalculator.Context calculationContext = contextFrom(periodContext);
        List<TripEntity> trips = tripRepository
                .findAllByCareerIdAndOperationalWeekOrderByCreatedAtAscIdAsc(career.getId(), currentWeek);
        PayrollCalculator.Calculation calculation = calculator.calculate(
                CareerGame.ATS,
                calculationContext,
                trips
        );
        Instant now = clock.instant();
        UUID payslipId = UUID.randomUUID();
        PayslipEntity payslip = new PayslipEntity(
                payslipId,
                career.getId(),
                CareerGame.ATS,
                currentWeek,
                null,
                currentWeek,
                currentWeek,
                calculation.level(),
                calculationContext.displayCurrency(),
                calculation.gross(),
                calculation.taxTotal(),
                calculation.benefits(),
                calculation.perDiem(),
                calculation.netSalary(),
                calculation.deposit(),
                calculation.totalDistance(),
                calculation.elapsedMinutes(),
                calculation.breakMinutes(),
                calculation.workedMinutes(),
                calculation.overrunMinutes(),
                json(payslipSnapshot(periodContext, List.of(currentWeek), List.of(), trips)),
                now
        );
        payslipRepository.saveAndFlush(payslip);

        PayrollPeriodEntity period = new PayrollPeriodEntity(
                UUID.randomUUID(),
                career.getId(),
                currentWeek,
                null,
                json(periodContext),
                now
        );
        period.assignPayslip(payslipId);
        payrollPeriodRepository.saveAndFlush(period);

        List<PayslipLineEntity> lines = saveLines(payslipId, calculation.lines());
        career.creditBalance(calculation.deposit(), now);
        career.advanceOperationalWeek(now);
        careerRepository.flush();
        return new Result(payslip, lines);
    }

    private Result generateEts2(CareerEntity career, Integer expectedPayrollMonth) {
        if (expectedPayrollMonth == null) {
            throw badRequest(
                    "PAYSLIP_MONTH_REQUIRED",
                    "Payroll month required",
                    "ETS2 payslip generation requires expectedPayrollMonth"
            );
        }
        Integer currentMonth = career.getCurrentPayrollMonth();
        if (currentMonth == null || currentMonth < 1) {
            throw new IllegalStateException("ETS2 career must have a current payroll month");
        }
        if (!currentMonth.equals(expectedPayrollMonth)) {
            throw conflict(
                    "PAYSLIP_MONTH_CONFLICT",
                    "Payroll month changed",
                    "The requested payroll month is no longer the career current payroll month"
            );
        }
        if (payslipRepository.existsByCareerIdAndPayrollMonth(career.getId(), currentMonth)) {
            throw conflict(
                    "PAYSLIP_ALREADY_GENERATED",
                    "Payslip already generated",
                    "The ETS2 operational payroll month already has a payslip"
            );
        }

        List<PayrollPeriodEntity> periods = payrollPeriodRepository
                .findAllByCareerIdOrderByOperationalWeekAsc(career.getId())
                .stream()
                .filter(period -> currentMonth.equals(period.getPayrollMonth()))
                .toList();
        if (periods.size() < ETS2_MIN_WEEKS_PER_PAYROLL_MONTH) {
            throw conflict(
                    "PAYSLIP_ETS2_PERIODS_INSUFFICIENT",
                    "Not enough closed weeks",
                    "Close at least four operational weeks before generating the ETS2 monthly payslip"
            );
        }
        if (periods.size() > ETS2_MAX_WEEKS_PER_PAYROLL_MONTH) {
            throw new IllegalStateException("ETS2 payroll month cannot contain more than five closed weeks");
        }
        if (periods.stream().anyMatch(period -> period.getPayslipId() != null)) {
            throw conflict(
                    "PAYSLIP_PERIOD_ALREADY_PAID",
                    "Payroll period already paid",
                    "One or more closed operational weeks are already linked to a payslip"
            );
        }

        Map<String, Object> authoritativeContext = snapshot(periods.getLast().getContextSnapshotJson());
        PayrollCalculator.Context calculationContext = contextFrom(authoritativeContext);
        List<Integer> weeks = periods.stream().map(PayrollPeriodEntity::getOperationalWeek).toList();
        List<TripEntity> trips = tripRepository
                .findAllByCareerIdOrderByOperationalWeekAscCreatedAtAscIdAsc(career.getId())
                .stream()
                .filter(trip -> weeks.contains(trip.getOperationalWeek()))
                .toList();
        PayrollCalculator.Calculation calculation = calculator.calculate(
                CareerGame.ETS2,
                calculationContext,
                trips
        );
        Instant now = clock.instant();
        UUID payslipId = UUID.randomUUID();
        PayslipEntity payslip = new PayslipEntity(
                payslipId,
                career.getId(),
                CareerGame.ETS2,
                null,
                currentMonth,
                weeks.getFirst(),
                weeks.getLast(),
                calculation.level(),
                calculationContext.displayCurrency(),
                calculation.gross(),
                calculation.taxTotal(),
                calculation.benefits(),
                calculation.perDiem(),
                calculation.netSalary(),
                calculation.deposit(),
                calculation.totalDistance(),
                calculation.elapsedMinutes(),
                calculation.breakMinutes(),
                calculation.workedMinutes(),
                calculation.overrunMinutes(),
                json(payslipSnapshot(authoritativeContext, weeks, periods, trips)),
                now
        );
        payslipRepository.saveAndFlush(payslip);

        periods.forEach(period -> period.assignPayslip(payslipId));
        payrollPeriodRepository.saveAllAndFlush(periods);
        List<PayslipLineEntity> lines = saveLines(payslipId, calculation.lines());
        career.creditBalance(calculation.deposit(), now);
        career.advancePayrollMonth(now);
        careerRepository.flush();
        return new Result(payslip, lines);
    }

    private Result result(PayslipEntity payslip) {
        return new Result(
                payslip,
                payslipLineRepository.findAllByPayslipIdOrderByLineOrderAsc(payslip.getId())
        );
    }

    private List<PayslipLineEntity> saveLines(
            UUID payslipId,
            List<PayrollCalculator.Line> calculatedLines
    ) {
        List<PayslipLineEntity> lines = new ArrayList<>();
        for (int index = 0; index < calculatedLines.size(); index++) {
            PayrollCalculator.Line line = calculatedLines.get(index);
            lines.add(new PayslipLineEntity(
                    UUID.randomUUID(),
                    payslipId,
                    index + 1,
                    line.code(),
                    line.label(),
                    line.type(),
                    line.amount(),
                    line.quantity(),
                    line.rate(),
                    "{}"
            ));
        }
        return payslipLineRepository.saveAllAndFlush(lines);
    }

    private PayrollCalculator.Context contextFrom(Map<String, Object> snapshot) {
        return new PayrollCalculator.Context(
                (short) integer(snapshot, "currentLevel", 1),
                string(snapshot, "stateCode"),
                string(snapshot, "countryCode"),
                string(snapshot, "baseCurrency"),
                string(snapshot, "displayCurrency"),
                decimal(snapshot, "exchangeRate", BigDecimal.ONE),
                decimal(snapshot, "citySalaryFactor", BigDecimal.ONE)
        );
    }

    private Map<String, Object> payslipSnapshot(
            Map<String, Object> authoritativeContext,
            List<Integer> weeks,
            List<PayrollPeriodEntity> periods,
            List<TripEntity> trips
    ) {
        Map<String, Object> snapshot = new LinkedHashMap<>(authoritativeContext);
        snapshot.put("policyVersion", POLICY_VERSION);
        snapshot.put("sourceOperationalWeeks", weeks);
        snapshot.put(
                "sourcePayrollPeriodIds",
                periods.stream().map(period -> period.getId().toString()).toList()
        );
        snapshot.put("sourceTripIds", trips.stream().map(trip -> trip.getId().toString()).toList());
        snapshot.put("incidentDeductionsIncluded", false);
        snapshot.put("emergencyReserveIncluded", false);
        return snapshot;
    }

    private Map<String, Object> snapshot(String json) {
        try {
            return objectMapper.readValue(json.getBytes(StandardCharsets.UTF_8), SNAPSHOT_TYPE);
        } catch (JacksonException exception) {
            throw new IllegalStateException(
                    "Payroll period snapshot could not be deserialized",
                    exception
            );
        }
    }

    private String json(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Payslip snapshot could not be serialized", exception);
        }
    }

    private CareerEntity lockedOwnedCareer(UUID userId, CareerGame game, UUID careerId) {
        return careerRepository.findForUpdateByIdAndUserIdAndGame(careerId, userId, game)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "CAREER_NOT_FOUND",
                        "The requested career does not exist"
                ));
    }

    private CareerEntity ownedCareer(UUID userId, CareerGame game, UUID careerId) {
        return careerRepository.findByIdAndUserIdAndGame(careerId, userId, game)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "CAREER_NOT_FOUND",
                        "The requested career does not exist"
                ));
    }

    private String string(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private int integer(Map<String, Object> map, String key, int fallback) {
        Object value = map.get(key);
        return value == null || String.valueOf(value).isBlank()
                ? fallback
                : Integer.parseInt(String.valueOf(value));
    }

    private BigDecimal decimal(Map<String, Object> map, String key, BigDecimal fallback) {
        Object value = map.get(key);
        return value == null || String.valueOf(value).isBlank()
                ? fallback
                : new BigDecimal(String.valueOf(value));
    }

    private ApiProblemException badRequest(String code, String title, String detail) {
        return new ApiProblemException(HttpStatus.BAD_REQUEST, code, title, detail);
    }

    private ApiProblemException conflict(String code, String title, String detail) {
        return new ApiProblemException(HttpStatus.CONFLICT, code, title, detail);
    }
}
