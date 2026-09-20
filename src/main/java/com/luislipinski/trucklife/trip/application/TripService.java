package com.luislipinski.trucklife.trip.application;

import com.luislipinski.trucklife.career.domain.CareerGame;
import com.luislipinski.trucklife.career.persistence.CareerEntity;
import com.luislipinski.trucklife.career.persistence.CareerRepository;
import com.luislipinski.trucklife.shared.error.ApiProblemException;
import com.luislipinski.trucklife.shared.error.ResourceNotFoundException;
import com.luislipinski.trucklife.trip.domain.TripPaymentCategory;
import com.luislipinski.trucklife.trip.domain.TripSource;
import com.luislipinski.trucklife.trip.domain.TripType;
import com.luislipinski.trucklife.trip.persistence.TripDraftEntity;
import com.luislipinski.trucklife.trip.persistence.TripDraftRepository;
import com.luislipinski.trucklife.trip.persistence.TripEntity;
import com.luislipinski.trucklife.trip.persistence.TripRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
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
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class TripService implements TripOperations {

    private static final int MAX_DRAFT_JSON_LENGTH = 32768;
    private static final TypeReference<Map<String, Object>> DRAFT_MAP_TYPE = new TypeReference<>() {};

    private final CareerRepository careerRepository;
    private final TripRepository tripRepository;
    private final TripDraftRepository tripDraftRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public TripService(CareerRepository careerRepository, TripRepository tripRepository,
                       TripDraftRepository tripDraftRepository, ObjectMapper objectMapper, Clock clock) {
        this.careerRepository = careerRepository;
        this.tripRepository = tripRepository;
        this.tripDraftRepository = tripDraftRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    @Transactional
    public TripEntity create(UUID userId, CareerGame game, UUID careerId, CreateTripCommand command) {
        CareerEntity career = lockedOwnedCareer(userId, game, careerId);
        DayOfWeek departureDay = day(command.departureDay(), "departureDay");
        DayOfWeek arrivalDay = day(command.arrivalDay(), "arrivalDay");
        long elapsedMinutes = elapsedMinutes(departureDay, command.departureTime(), arrivalDay, command.arrivalTime());

        TripType type = tripType(command.type());
        TripPaymentCategory category = paymentCategory(type, command.paymentCategory());
        String cargo = type == TripType.DEADHEAD ? null : optional(command.cargo());
        if (type == TripType.LOADED && category == TripPaymentCategory.DEADHEAD) {
            throw problem("TRIP_PAYMENT_CATEGORY_INVALID", "Trip payment category invalid",
                    "Loaded trips cannot use the deadhead payment category");
        }
        validatePaymentEligibility(career, type, category);

        Integer breakMinutes = command.breakMinutes();
        if (breakMinutes != null && breakMinutes >= elapsedMinutes) {
            throw problem("TRIP_BREAK_INVALID", "Trip break invalid", "Break minutes must be lower than the elapsed trip time");
        }
        validateOdometer(command.odometerStart(), command.odometerEnd());

        Instant now = clock.instant();
        TripEntity trip = new TripEntity(
                UUID.randomUUID(), career.getId(), career.getCurrentOperationalWeek(), departureDay, command.departureTime(),
                arrivalDay, command.arrivalTime(), required(command.originCity(), "originCity"),
                optional(command.originCompany()), required(command.destinationCity(), "destinationCity"),
                optional(command.destinationCompany()), cargo, type, category, command.officialDistance(), breakMinutes,
                optional(command.truckMake()), optional(command.truckModel()), command.odometerStart(), command.odometerEnd(),
                TripSource.MANUAL, json(Map.of("companyName", textOrEmpty(career.getCompanyName()))),
                json(baseSnapshot(career)), now, now
        );
        TripEntity saved = tripRepository.saveAndFlush(trip);
        tripDraftRepository.deleteById(career.getId());
        tripDraftRepository.flush();
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public Draft getDraft(UUID userId, CareerGame game, UUID careerId) {
        CareerEntity career = ownedCareer(userId, game, careerId);
        return tripDraftRepository.findById(career.getId())
                .filter(draft -> draft.getOperationalWeek() == career.getCurrentOperationalWeek())
                .map(this::draft)
                .orElseGet(() -> new Draft(career.getCurrentOperationalWeek(), Map.of(), null));
    }

    @Override
    @Transactional
    public Draft saveDraft(UUID userId, CareerGame game, UUID careerId, SaveTripDraftCommand command) {
        CareerEntity career = lockedOwnedCareer(userId, game, careerId);
        if (command.expectedOperationalWeek() != career.getCurrentOperationalWeek()) {
            throw conflict(
                    "TRIP_DRAFT_WEEK_STALE",
                    "Trip draft week is stale",
                    "Reload the career before saving a draft for a different operational week"
            );
        }

        String payload = draftJson(command.data());
        Instant now = clock.instant();
        TripDraftEntity entity = tripDraftRepository.findById(career.getId())
                .orElseGet(() -> new TripDraftEntity(career.getId(), career.getCurrentOperationalWeek(), payload, now));
        entity.update(career.getCurrentOperationalWeek(), payload, now);
        TripDraftEntity saved = tripDraftRepository.saveAndFlush(entity);
        return draft(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TripEntity> list(UUID userId, CareerGame game, UUID careerId, Integer operationalWeek) {
        CareerEntity career = ownedCareer(userId, game, careerId);
        if (operationalWeek == null) return tripRepository.findAllByCareerIdOrderByOperationalWeekAscCreatedAtAscIdAsc(career.getId());
        if (operationalWeek <= 0) throw problem("TRIP_WEEK_INVALID", "Trip week invalid", "operationalWeek must be greater than zero");
        return tripRepository.findAllByCareerIdAndOperationalWeekOrderByCreatedAtAscIdAsc(career.getId(), operationalWeek);
    }

    @Override
    @Transactional(readOnly = true)
    public TripEntity get(UUID userId, CareerGame game, UUID careerId, UUID tripId) {
        CareerEntity career = ownedCareer(userId, game, careerId);
        return ownedTrip(career.getId(), tripId);
    }

    @Override
    @Transactional
    public void delete(UUID userId, CareerGame game, UUID careerId, UUID tripId) {
        CareerEntity career = lockedOwnedCareer(userId, game, careerId);
        TripEntity trip = ownedTrip(career.getId(), tripId);
        if (trip.getOperationalWeek() != career.getCurrentOperationalWeek()) {
            throw conflict(
                    "TRIP_WEEK_LOCKED",
                    "Trip week locked",
                    "Trips from closed operational weeks cannot be deleted"
            );
        }
        tripRepository.delete(trip);
        tripRepository.flush();
    }

    private void validatePaymentEligibility(CareerEntity career, TripType type, TripPaymentCategory category) {
        if (type != TripType.LOADED) return;
        if (career.getCurrentLevel() <= 1 && category != TripPaymentCategory.NORMAL) {
            throw problem("TRIP_PAYMENT_CATEGORY_INVALID", "Trip payment category invalid",
                    "Level 1 careers can only use the normal loaded category");
        }
        boolean dangerousCategory = category == TripPaymentCategory.HAZMAT || category == TripPaymentCategory.HAZMAT_DOUBLES;
        boolean doublesCategory = category == TripPaymentCategory.DOUBLES || category == TripPaymentCategory.HAZMAT_DOUBLES;
        if (dangerousCategory && !career.isDangerousGoodsQualified()) {
            throw problem("TRIP_QUALIFICATION_REQUIRED", "Dangerous-goods qualification required",
                    career.getGame() == CareerGame.ATS ? "HazMat qualification is required for this payment category"
                            : "ADR qualification is required for this payment category");
        }
        if (doublesCategory && career.getCurrentLevel() < 3) {
            throw problem("TRIP_LEVEL_REQUIRED", "Level 3 required",
                    career.getGame() == CareerGame.ATS ? "Doubles categories are available only at Level 3"
                            : "Euro Combi categories are available only at Level 3");
        }
    }

    private CareerEntity lockedOwnedCareer(UUID userId, CareerGame game, UUID careerId) {
        return careerRepository.findForUpdateByIdAndUserIdAndGame(careerId, userId, game)
                .orElseThrow(() -> new ResourceNotFoundException("CAREER_NOT_FOUND", "The requested career does not exist"));
    }

    private CareerEntity ownedCareer(UUID userId, CareerGame game, UUID careerId) {
        return careerRepository.findByIdAndUserIdAndGame(careerId, userId, game)
                .orElseThrow(() -> new ResourceNotFoundException("CAREER_NOT_FOUND", "The requested career does not exist"));
    }

    private TripEntity ownedTrip(UUID careerId, UUID tripId) {
        return tripRepository.findByIdAndCareerId(tripId, careerId)
                .orElseThrow(() -> new ResourceNotFoundException("TRIP_NOT_FOUND", "The requested trip does not exist"));
    }

    private DayOfWeek day(String value, String field) {
        try { return DayOfWeek.valueOf(value.strip().toUpperCase(Locale.ROOT)); }
        catch (RuntimeException exception) {
            throw problem("TRIP_SCHEDULE_INVALID", "Trip schedule invalid", field + " must be a valid weekday");
        }
    }

    private long elapsedMinutes(DayOfWeek departureDay, LocalTime departureTime, DayOfWeek arrivalDay, LocalTime arrivalTime) {
        int departureOffset = departureDay.getValue() - 1;
        int arrivalOffset = arrivalDay.getValue() - 1;
        if (arrivalOffset < departureOffset) arrivalOffset += 7;
        long minutes = (long) (arrivalOffset - departureOffset) * 24 * 60
                + java.time.Duration.between(departureTime, arrivalTime).toMinutes();
        if (minutes <= 0) throw problem("TRIP_SCHEDULE_INVALID", "Trip schedule invalid",
                "Arrival must be later than departure; choose the next weekday when the trip crosses midnight");
        return minutes;
    }

    private TripType tripType(String value) {
        try { return TripType.valueOf(value.strip().toUpperCase(Locale.ROOT)); }
        catch (RuntimeException exception) {
            throw problem("TRIP_TYPE_INVALID", "Trip type invalid", "type must be LOADED or DEADHEAD");
        }
    }

    private TripPaymentCategory paymentCategory(TripType type, String value) {
        if (type == TripType.DEADHEAD) return TripPaymentCategory.DEADHEAD;
        if (value == null || value.isBlank()) return TripPaymentCategory.NORMAL;
        try { return TripPaymentCategory.valueOf(value.strip().toUpperCase(Locale.ROOT)); }
        catch (RuntimeException exception) {
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
        try { return objectMapper.writeValueAsString(value); }
        catch (JacksonException exception) { throw new IllegalStateException("Trip snapshot could not be serialized", exception); }
    }

    private String draftJson(Map<String, Object> value) {
        Map<String, Object> data = value == null ? Map.of() : value;
        String payload = json(data);
        if (payload.length() > MAX_DRAFT_JSON_LENGTH) {
            throw problem("TRIP_DRAFT_INVALID", "Trip draft invalid", "Trip draft is too large");
        }
        return payload;
    }

    private Draft draft(TripDraftEntity entity) {
        try {
            Map<String, Object> data = objectMapper.readValue(
                    entity.getPayloadJson().getBytes(StandardCharsets.UTF_8),
                    DRAFT_MAP_TYPE
            );
            return new Draft(entity.getOperationalWeek(), data, entity.getUpdatedAt());
        } catch (JacksonException exception) {
            throw new IllegalStateException("Trip draft could not be deserialized", exception);
        }
    }

    private String required(String value, String field) {
        if (value == null || value.isBlank()) throw problem("TRIP_DATA_INVALID", "Trip data invalid", field + " is required");
        return value.strip();
    }

    private String optional(String value) { return value == null || value.isBlank() ? null : value.strip(); }
    private String textOrEmpty(String value) { return value == null ? "" : value; }
    private ApiProblemException problem(String code, String title, String detail) {
        return new ApiProblemException(HttpStatus.BAD_REQUEST, code, title, detail);
    }
    private ApiProblemException conflict(String code, String title, String detail) {
        return new ApiProblemException(HttpStatus.CONFLICT, code, title, detail);
    }
}
