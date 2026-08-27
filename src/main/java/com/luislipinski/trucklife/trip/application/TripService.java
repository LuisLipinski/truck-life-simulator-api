package com.luislipinski.trucklife.trip.application;

import com.luislipinski.trucklife.career.domain.CareerGame;
import com.luislipinski.trucklife.career.persistence.CareerEntity;
import com.luislipinski.trucklife.career.persistence.CareerRepository;
import com.luislipinski.trucklife.shared.error.ApiProblemException;
import com.luislipinski.trucklife.shared.error.ResourceNotFoundException;
import com.luislipinski.trucklife.trip.domain.TripPaymentCategory;
import com.luislipinski.trucklife.trip.domain.TripSource;
import com.luislipinski.trucklife.trip.domain.TripType;
import com.luislipinski.trucklife.trip.persistence.TripEntity;
import com.luislipinski.trucklife.trip.persistence.TripRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class TripService implements TripOperations {

    private final CareerRepository careerRepository;
    private final TripRepository tripRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public TripService(
            CareerRepository careerRepository,
            TripRepository tripRepository,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.careerRepository = careerRepository;
        this.tripRepository = tripRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    @Transactional
    public TripEntity create(UUID userId, CareerGame game, UUID careerId, CreateTripCommand command) {
        CareerEntity career = lockedOwnedCareer(userId, game, careerId);
        DayOfWeek departureDay = day(command.departureDay(), "departureDay");
        DayOfWeek arrivalDay = day(command.arrivalDay(), "arrivalDay");
        long elapsedMinutes = elapsedMinutes(
                departureDay,
                command.departureTime(),
                arrivalDay,
                command.arrivalTime()
        );

        TripType type = tripType(command.type());
        TripPaymentCategory category = paymentCategory(type, command.paymentCategory());
        String cargo = type == TripType.DEADHEAD ? null : optional(command.cargo());
        if (type == TripType.LOADED && category == TripPaymentCategory.DEADHEAD) {
            throw problem("TRIP_PAYMENT_CATEGORY_INVALID", "Trip payment category invalid", "Loaded trips cannot use the deadhead payment category");
        }
        if (career.getCurrentLevel() <= 1 && type == TripType.LOADED && category != TripPaymentCategory.NORMAL) {
            throw problem("TRIP_PAYMENT_CATEGORY_INVALID", "Trip payment category invalid", "Level 1 careers can only use the normal loaded category");
        }

        Integer breakMinutes = command.breakMinutes();
        if (breakMinutes != null && breakMinutes >= elapsedMinutes) {
            throw problem("TRIP_BREAK_INVALID", "Trip break invalid", "Break minutes must be lower than the elapsed trip time");
        }
        validateOdometer(command.odometerStart(), command.odometerEnd());

        Instant now = clock.instant();
        TripEntity trip = new TripEntity(
                UUID.randomUUID(),
                career.getId(),
                career.getCurrentOperationalWeek(),
                departureDay,
                command.departureTime(),
                arrivalDay,
                command.arrivalTime(),
                required(command.originCity(), "originCity"),
                optional(command.originCompany()),
                required(command.destinationCity(), "destinationCity"),
                optional(command.destinationCompany()),
                cargo,
                type,
                category,
                command.officialDistance(),
                breakMinutes,
                optional(command.truckMake()),
                optional(command.truckModel()),
                command.odometerStart(),
                command.odometerEnd(),
                TripSource.MANUAL,
                json(Map.of("companyName", textOrEmpty(career.getCompanyName()))),
                json(baseSnapshot(career)),
                now,
                now
        );
        return tripRepository.saveAndFlush(trip);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TripEntity> list(UUID userId, CareerGame game, UUID careerId, Integer operationalWeek) {
        CareerEntity career = ownedCareer(userId, game, careerId);
        if (operationalWeek == null) {
            return tripRepository.findAllByCareerIdOrderByOperationalWeekAscCreatedAtAscIdAsc(career.getId());
        }
        if (operationalWeek <= 0) {
            throw problem("TRIP_WEEK_INVALID", "Trip week invalid", "operationalWeek must be greater than zero");
        }
        return tripRepository.findAllByCareerIdAndOperationalWeekOrderByCreatedAtAscIdAsc(
                career.getId(),
                operationalWeek
        );
    }

    @Override
    @Transactional(readOnly = true)
    public TripEntity get(UUID userId, CareerGame game, UUID careerId, UUID tripId) {
        CareerEntity career = ownedCareer(userId, game, careerId);
        return tripRepository.findByIdAndCareerId(tripId, career.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "TRIP_NOT_FOUND",
                        "The requested trip does not exist"
                ));
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

    private DayOfWeek day(String value, String field) {
        try {
            return DayOfWeek.valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            throw problem("TRIP_SCHEDULE_INVALID", "Trip schedule invalid", field + " must be a valid weekday");
        }
    }

    private long elapsedMinutes(
            DayOfWeek departureDay,
            LocalTime departureTime,
            DayOfWeek arrivalDay,
            LocalTime arrivalTime
    ) {
        int departureOffset = departureDay.getValue() - 1;
        int arrivalOffset = arrivalDay.getValue() - 1;
        if (arrivalOffset < departureOffset) {
            arrivalOffset += 7;
        }
        long minutes = (long) (arrivalOffset - departureOffset) * 24 * 60
                + java.time.Duration.between(departureTime, arrivalTime).toMinutes();
        if (minutes <= 0) {
            throw problem(
                    "TRIP_SCHEDULE_INVALID",
                    "Trip schedule invalid",
                    "Arrival must be later than departure; choose the next weekday when the trip crosses midnight"
            );
        }
        return minutes;
    }

    private TripType tripType(String value) {
        try {
            return TripType.valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            throw problem("TRIP_TYPE_INVALID", "Trip type invalid", "type must be LOADED or DEADHEAD");
        }
    }

    private TripPaymentCategory paymentCategory(TripType type, String value) {
        if (type == TripType.DEADHEAD) {
            return TripPaymentCategory.DEADHEAD;
        }
        if (value == null || value.isBlank()) {
            return TripPaymentCategory.NORMAL;
        }
        try {
            return TripPaymentCategory.valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            throw problem("TRIP_PAYMENT_CATEGORY_INVALID", "Trip payment category invalid", "Unknown payment category");
        }
    }

    private void validateOdometer(BigDecimal start, BigDecimal end) {
        if ((start == null) != (end == null)) {
            throw problem("TRIP_ODOMETER_INVALID", "Trip odometer invalid", "Both odometer readings must be provided together");
        }
        if (start != null && (start.signum() < 0 || end.signum() < 0 || end.compareTo(start) < 0)) {
            throw problem("TRIP_ODOMETER_INVALID", "Trip odometer invalid", "Odometer end must be greater than or equal to odometer start");
        }
    }

    private Map<String, Object> baseSnapshot(CareerEntity career) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("city", textOrEmpty(career.getBaseCity()));
        snapshot.put("countryCode", textOrEmpty(career.getCountryCode()));
        snapshot.put("countryName", "");
        snapshot.put("stateCode", textOrEmpty(career.getStateCode()));
        snapshot.put("stateName", "");
        snapshot.put("currency", textOrEmpty(career.getDisplayCurrency()));
        snapshot.put("baseCurrency", textOrEmpty(career.getBaseCurrency()));
        snapshot.put("exchangeRate", career.getExchangeRate());
        snapshot.put("exchangeRateAsOf", career.getExchangeRateAsOf() == null ? "" : career.getExchangeRateAsOf().toString());
        snapshot.put("cityMarketVersion", career.getCityMarketVersion());
        snapshot.put("cityMarketLabel", career.getCityMarketLabel());
        snapshot.put("cityCostFactor", career.getCityCostFactor());
        snapshot.put("citySalaryFactor", career.getCitySalaryFactor());
        return snapshot;
    }

    private String json(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Trip snapshot could not be serialized", exception);
        }
    }

    private String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw problem("TRIP_DATA_INVALID", "Trip data invalid", field + " is required");
        }
        return value.strip();
    }

    private String optional(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private String textOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private ApiProblemException problem(String code, String title, String detail) {
        return new ApiProblemException(HttpStatus.BAD_REQUEST, code, title, detail);
    }
}
