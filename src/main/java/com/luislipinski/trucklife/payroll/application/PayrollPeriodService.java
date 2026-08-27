package com.luislipinski.trucklife.payroll.application;

import com.luislipinski.trucklife.career.domain.CareerGame;
import com.luislipinski.trucklife.career.persistence.CareerEntity;
import com.luislipinski.trucklife.career.persistence.CareerRepository;
import com.luislipinski.trucklife.payroll.persistence.PayrollPeriodEntity;
import com.luislipinski.trucklife.payroll.persistence.PayrollPeriodRepository;
import com.luislipinski.trucklife.shared.error.ApiProblemException;
import com.luislipinski.trucklife.shared.error.ResourceNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class PayrollPeriodService implements PayrollPeriodOperations {

    private static final long ETS2_MAX_WEEKS_PER_PAYROLL_MONTH = 5;

    private final CareerRepository careerRepository;
    private final PayrollPeriodRepository payrollPeriodRepository;
    private final PayrollContextSnapshotFactory contextSnapshotFactory;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public PayrollPeriodService(
            CareerRepository careerRepository,
            PayrollPeriodRepository payrollPeriodRepository,
            PayrollContextSnapshotFactory contextSnapshotFactory,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.careerRepository = careerRepository;
        this.payrollPeriodRepository = payrollPeriodRepository;
        this.contextSnapshotFactory = contextSnapshotFactory;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    @Transactional
    public PayrollPeriodEntity close(
            UUID userId,
            CareerGame game,
            UUID careerId,
            int expectedOperationalWeek
    ) {
        CareerEntity career = lockedOwnedCareer(userId, game, careerId);
        int currentWeek = career.getCurrentOperationalWeek();

        if (expectedOperationalWeek != currentWeek) {
            throw conflict(
                    "PAYROLL_WEEK_CONFLICT",
                    "Operational week changed",
                    "The requested week is no longer the career current operational week"
            );
        }
        if (game == CareerGame.ATS) {
            throw conflict(
                    "PAYROLL_ATS_CLOSE_REQUIRES_PAYSLIP",
                    "ATS week requires payslip closing",
                    "ATS weekly closing is performed together with weekly payslip generation"
            );
        }

        Integer payrollMonth = career.getCurrentPayrollMonth();
        if (payrollMonth == null || payrollMonth < 1) {
            throw new IllegalStateException("ETS2 career must have a current payroll month");
        }
        if (payrollPeriodRepository.countByCareerIdAndPayrollMonth(career.getId(), payrollMonth)
                >= ETS2_MAX_WEEKS_PER_PAYROLL_MONTH) {
            throw conflict(
                    "PAYROLL_MONTH_WEEK_LIMIT_REACHED",
                    "Payroll month week limit reached",
                    "Generate the monthly payslip before closing another operational week"
            );
        }

        Map<String, Object> authoritativeContext;
        try {
            authoritativeContext = contextSnapshotFactory.from(career);
        } catch (IllegalArgumentException exception) {
            throw conflict(
                    "PAYROLL_POLICY_UNAVAILABLE",
                    "Payroll policy unavailable",
                    exception.getMessage()
            );
        }

        Instant now = clock.instant();
        PayrollPeriodEntity period = new PayrollPeriodEntity(
                UUID.randomUUID(),
                career.getId(),
                currentWeek,
                payrollMonth,
                json(authoritativeContext),
                now
        );

        payrollPeriodRepository.save(period);
        career.advanceOperationalWeek(now);
        careerRepository.flush();
        return period;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PayrollPeriodEntity> list(UUID userId, CareerGame game, UUID careerId) {
        CareerEntity career = ownedCareer(userId, game, careerId);
        return payrollPeriodRepository.findAllByCareerIdOrderByOperationalWeekAsc(career.getId());
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

    private String json(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Payroll period snapshot could not be serialized", exception);
        }
    }

    private ApiProblemException conflict(String code, String title, String detail) {
        return new ApiProblemException(HttpStatus.CONFLICT, code, title, detail);
    }
}
