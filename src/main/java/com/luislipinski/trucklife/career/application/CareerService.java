package com.luislipinski.trucklife.career.application;

import com.luislipinski.trucklife.career.domain.CareerEventType;
import com.luislipinski.trucklife.career.domain.CareerGame;
import com.luislipinski.trucklife.career.persistence.CareerEntity;
import com.luislipinski.trucklife.career.persistence.CareerEventEntity;
import com.luislipinski.trucklife.career.persistence.CareerEventRepository;
import com.luislipinski.trucklife.career.persistence.CareerOwnerLock;
import com.luislipinski.trucklife.career.persistence.CareerRepository;
import com.luislipinski.trucklife.shared.error.ApiProblemException;
import com.luislipinski.trucklife.shared.error.ResourceNotFoundException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class CareerService implements CareerOperations {

    private static final long FREE_CAREERS_PER_GAME = 2;

    private final CareerRepository careerRepository;
    private final CareerOwnerLock ownerLock;
    private final CareerEventRepository eventRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public CareerService(
            CareerRepository careerRepository,
            CareerOwnerLock ownerLock,
            CareerEventRepository eventRepository,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.careerRepository = careerRepository;
        this.ownerLock = ownerLock;
        this.eventRepository = eventRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    @Transactional
    public CareerEntity create(UUID userId, CreateCareerCommand command) {
        String stateCode = null;
        String countryCode = null;
        if (command.game() == CareerGame.ATS) {
            stateCode = requiredLocation(command.stateCode(), "stateCode", CareerGame.ATS);
        } else {
            countryCode = requiredLocation(command.countryCode(), "countryCode", CareerGame.ETS2);
        }

        ownerLock.lock(userId);
        if (careerRepository.countByUserIdAndGame(userId, command.game()) >= FREE_CAREERS_PER_GAME) {
            throw new ApiProblemException(
                    HttpStatus.CONFLICT,
                    "CAREER_LIMIT_REACHED",
                    "Career limit reached",
                    "The Free account limit is two careers per game"
            );
        }

        Instant now = clock.instant();
        CareerEntity career = new CareerEntity(
                UUID.randomUUID(),
                userId,
                command.game(),
                command.driverName().strip(),
                command.companyName().strip(),
                optional(command.biography()),
                (short) 1,
                command.initialBalance(),
                command.baseCurrency().strip(),
                command.displayCurrency().strip(),
                command.exchangeRate(),
                command.exchangeRateAsOf(),
                stateCode,
                countryCode,
                command.baseCity().strip(),
                optional(command.defaultTruckMake()),
                optional(command.defaultTruckModel()),
                optional(command.cityMarketVersion()),
                optional(command.cityMarketLabel()),
                command.cityCostFactor(),
                command.citySalaryFactor(),
                1,
                command.game() == CareerGame.ETS2 ? 1 : null,
                now,
                now
        );
        return careerRepository.saveAndFlush(career);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CareerEntity> list(UUID userId, CareerGame game) {
        return careerRepository.findAllByUserIdAndGameOrderByCreatedAtAscIdAsc(userId, game);
    }

    @Override
    @Transactional(readOnly = true)
    public CareerEntity get(UUID userId, CareerGame game, UUID careerId) {
        return ownedCareer(userId, game, careerId);
    }

    @Override
    @Transactional
    public CareerEntity updateProfile(
            UUID userId,
            CareerGame game,
            UUID careerId,
            UpdateCareerProfileCommand command
    ) {
        CareerEntity career = ownedCareer(userId, game, careerId);
        requireCurrentVersion(career, command.version());

        String driverName = command.driverName().strip();
        String biography = optional(command.biography());
        Map<String, Object> changes = new LinkedHashMap<>();
        addChange(changes, "driverName", career.getDriverName(), driverName);
        addChange(changes, "bio", textOrEmpty(career.getBiography()), textOrEmpty(biography));
        if (changes.isEmpty()) {
            return career;
        }

        Instant now = clock.instant();
        career.updateProfile(driverName, biography, now);
        flushCareer();
        recordEvent(
                career.getId(),
                CareerEventType.PROFILE_UPDATED,
                command.effectiveDate() == null ? LocalDate.now(clock) : command.effectiveDate(),
                now,
                changes
        );
        return career;
    }

    @Override
    @Transactional
    public CareerEntity changeEmployer(
            UUID userId,
            CareerGame game,
            UUID careerId,
            ChangeCareerEmployerCommand command
    ) {
        CareerEntity career = ownedCareer(userId, game, careerId);
        requireCurrentVersion(career, command.version());
        String companyName = command.companyName().strip();
        if (Objects.equals(career.getCompanyName(), companyName)) {
            return career;
        }

        Instant now = clock.instant();
        Map<String, Object> changes = Map.of(
                "company",
                change(textOrEmpty(career.getCompanyName()), companyName)
        );
        career.changeEmployer(companyName, now);
        flushCareer();
        recordEvent(
                career.getId(),
                CareerEventType.EMPLOYER_CHANGED,
                command.effectiveDate(),
                now,
                changes
        );
        return career;
    }

    @Override
    @Transactional
    public CareerEntity changeBase(
            UUID userId,
            CareerGame game,
            UUID careerId,
            ChangeCareerBaseCommand command
    ) {
        CareerEntity career = ownedCareer(userId, game, careerId);
        requireCurrentVersion(career, command.version());

        String stateCode = null;
        String countryCode = null;
        if (game == CareerGame.ATS) {
            stateCode = requiredLocation(command.stateCode(), "stateCode", CareerGame.ATS);
        } else {
            countryCode = requiredLocation(command.countryCode(), "countryCode", CareerGame.ETS2);
        }
        String baseCity = command.baseCity().strip();
        String baseCurrency = command.baseCurrency().strip().toUpperCase(Locale.ROOT);
        String cityMarketVersion = optional(command.cityMarketVersion());
        String cityMarketLabel = optional(command.cityMarketLabel());

        Map<String, Object> previousBase = baseSnapshot(career);
        Map<String, Object> nextBase = baseSnapshot(
                baseCity,
                stateCode,
                countryCode,
                career.getDisplayCurrency(),
                baseCurrency,
                command.exchangeRate(),
                command.exchangeRateAsOf(),
                cityMarketVersion,
                cityMarketLabel,
                command.cityCostFactor(),
                command.citySalaryFactor()
        );
        if (previousBase.equals(nextBase)) {
            return career;
        }

        Instant now = clock.instant();
        career.changeBase(
                stateCode,
                countryCode,
                baseCity,
                baseCurrency,
                command.exchangeRate(),
                command.exchangeRateAsOf(),
                cityMarketVersion,
                cityMarketLabel,
                command.cityCostFactor(),
                command.citySalaryFactor(),
                now
        );
        flushCareer();
        recordEvent(
                career.getId(),
                CareerEventType.BASE_CHANGED,
                command.effectiveDate(),
                now,
                Map.of("base", change(previousBase, nextBase))
        );
        return career;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CareerEventEntity> listEvents(UUID userId, CareerGame game, UUID careerId) {
        CareerEntity career = ownedCareer(userId, game, careerId);
        return eventRepository.findAllByCareerIdOrderByEffectiveDateAscRecordedAtAscIdAsc(career.getId());
    }

    private CareerEntity ownedCareer(UUID userId, CareerGame game, UUID careerId) {
        return careerRepository.findByIdAndUserIdAndGame(careerId, userId, game)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "CAREER_NOT_FOUND",
                        "The requested career does not exist"
                ));
    }

    private void requireCurrentVersion(CareerEntity career, long version) {
        if (career.getVersion() != version) {
            throw versionConflict();
        }
    }

    private void flushCareer() {
        try {
            careerRepository.flush();
        } catch (ObjectOptimisticLockingFailureException exception) {
            throw versionConflict();
        }
    }

    private void recordEvent(
            UUID careerId,
            CareerEventType type,
            LocalDate effectiveDate,
            Instant recordedAt,
            Map<String, Object> changes
    ) {
        try {
            eventRepository.save(new CareerEventEntity(
                    UUID.randomUUID(),
                    careerId,
                    type,
                    effectiveDate,
                    recordedAt,
                    objectMapper.writeValueAsString(changes)
            ));
        } catch (JacksonException exception) {
            throw new IllegalStateException("Career event changes could not be serialized", exception);
        }
    }

    private Map<String, Object> baseSnapshot(CareerEntity career) {
        return baseSnapshot(
                career.getBaseCity(),
                career.getStateCode(),
                career.getCountryCode(),
                career.getDisplayCurrency(),
                career.getBaseCurrency(),
                career.getExchangeRate(),
                career.getExchangeRateAsOf(),
                career.getCityMarketVersion(),
                career.getCityMarketLabel(),
                career.getCityCostFactor(),
                career.getCitySalaryFactor()
        );
    }

    private Map<String, Object> baseSnapshot(
            String city,
            String stateCode,
            String countryCode,
            String displayCurrency,
            String baseCurrency,
            BigDecimal exchangeRate,
            LocalDate exchangeRateAsOf,
            String cityMarketVersion,
            String cityMarketLabel,
            BigDecimal cityCostFactor,
            BigDecimal citySalaryFactor
    ) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("city", textOrEmpty(city));
        snapshot.put("countryCode", textOrEmpty(countryCode));
        snapshot.put("countryName", "");
        snapshot.put("stateCode", textOrEmpty(stateCode));
        snapshot.put("stateName", "");
        snapshot.put("currency", textOrEmpty(displayCurrency));
        snapshot.put("baseCurrency", textOrEmpty(baseCurrency));
        snapshot.put("exchangeRate", exchangeRate);
        snapshot.put("exchangeRateAsOf", exchangeRateAsOf == null ? "" : exchangeRateAsOf.toString());
        snapshot.put("cityMarketVersion", cityMarketVersion);
        snapshot.put("cityMarketLabel", cityMarketLabel);
        snapshot.put("cityCostFactor", cityCostFactor);
        snapshot.put("citySalaryFactor", citySalaryFactor);
        return snapshot;
    }

    private void addChange(Map<String, Object> changes, String key, Object previous, Object next) {
        if (!Objects.equals(previous, next)) {
            changes.put(key, change(previous, next));
        }
    }

    private Map<String, Object> change(Object previous, Object next) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("previous", previous);
        value.put("next", next);
        return value;
    }

    private String requiredLocation(String value, String field, CareerGame game) {
        if (value == null || value.isBlank()) {
            throw new ApiProblemException(
                    HttpStatus.BAD_REQUEST,
                    "CAREER_LOCATION_INVALID",
                    "Career location invalid",
                    field + " is required for " + game
            );
        }
        return value.strip().toUpperCase(Locale.ROOT);
    }

    private String optional(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private String textOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private ApiProblemException versionConflict() {
        return new ApiProblemException(
                HttpStatus.CONFLICT,
                "CAREER_VERSION_CONFLICT",
                "Career version conflict",
                "The career changed since it was loaded; reload it before retrying"
        );
    }
}
